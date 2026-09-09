package com.osrscopilot.combat.engine;

import com.osrscopilot.combat.CombatMeterColors;

import com.osrscopilot.combat.model.CombatStyle;
import com.osrscopilot.combat.model.CombatTimelineEvent;
import com.osrscopilot.combat.model.EncounterSegment;
import com.osrscopilot.combat.model.EntityCombatStats;
import com.osrscopilot.combat.model.SegmentStatus;
import com.osrscopilot.combat.model.SegmentType;
import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ObjIntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;

@Singleton
@Slf4j
public class CombatEncounterManager
{
    // How long to hold ghost multi-target entries whose NPC left render distance with no despawn.
    private static final int STALE_TARGET_TICKS = 15;
    // A fight ends this many ticks after the last combat action. Configurable (default ~20s) - the
    // old 9s split phase bosses (Vorkath acid, Zulrah dive, banking-then-back) into several rows.
    private int encounterTimeoutTicks = 34;
    // Longest common weapon cycle (godsword / heavy 2h). Ticks between attacks up to this count as
    // "attacking"; anything beyond is a pause (lost combat time). Feeds attack-uptime % / active DPS.
    private static final int MAX_ATTACK_CYCLE_TICKS = 7;
    // ~5 minutes; how often "Total" is flushed to disk during a long ongoing fight.
    private static final int TOTAL_PERSIST_INTERVAL_TICKS = 500;
    private int lastTotalPersistTick = 0;
    private int graphSmoothingSeconds = 6;

    // Crowd-control spell -> nominal freeze/bind length in game ticks (OSRS known values).
    private static final java.util.Map<String, Integer> CC_DURATIONS = new java.util.HashMap<>();
    static
    {
        CC_DURATIONS.put("bind", 5);
        CC_DURATIONS.put("snare", 10);
        CC_DURATIONS.put("entangle", 15);
        CC_DURATIONS.put("ice rush", 8);
        CC_DURATIONS.put("ice burst", 16);
        CC_DURATIONS.put("ice blitz", 24);
        CC_DURATIONS.put("ice barrage", 32);
    }
    private int sessionInactivityTimeoutTicks = 50; // 30.0 seconds (for current session)

    private final Client client;

    private EncounterSegment currentEncounter = null;
    private EncounterSegment lastEncounter = null;
    private final EncounterSegment currentSessionEncounter;
    private final EncounterSegment overallSessionEncounter;
    private final EncounterSegment emptyEncounterPlaceholder;
    private EncounterSegment selectedEncounter = null;

    // Supplies the optional "Group" scope row (the shared party damage meter). Set by the plugin
    // from CombatPartyService; kept as a supplier to avoid a compile-time dependency on the
    // party subsystem (which itself depends on this manager). Returns null when not in a party.
    private java.util.function.Supplier<EncounterSegment> groupScopeSupplier;
    // True while a shared party fight is live AND another member is broadcasting - when set, the
    // auto-follow view swaps from the solo fight to the combined group meter and swaps back when
    // the fight ends. A pinned scope still wins.
    private java.util.function.BooleanSupplier groupAutoFollowSupplier;

    private final List<EncounterSegment> historicalEncounters = new ArrayList<>();
    // Segment-history depth: how many finished fights the "Past fights" list keeps
    // browsable. Configurable (default 25). Older ones are evicted
    // from the list but their damage still lives on in the Current Session / Total aggregates.
    private int segmentHistoryDepth = 25;
    private int nextHistoryIndex = 1;
    // Trash merge: a kill of the same NPC as the previous history row, within this
    // window of it, folds into that row as "Gargoyle xN" instead of adding another entry.
    private boolean mergeTrash = true;
    private static final long MERGE_WINDOW_MS = 600_000L;
    private final List<Runnable> combatListeners = new CopyOnWriteArrayList<>();

    // "Auto-follow current fight" (auto-follow the newest fight). On until the user manually
    // pins Session / Total / a past fight; re-picking the Live scope turns it back on.
    private boolean autoFollow = true;

    private int lastCombatTick = 0;
    private int lastSessionCombatTick = 0;
    // Tick up to which combat time has been credited to the rate denominator. Only ever advanced
    // as far as the last real combat action (lastCombatTick), so the denominator - and the shown
    // rate - freezes exactly at the last hit instead of drifting through the encounter timeout.
    private int lastAccruedCombatTick = 0;
    private Actor activeTargetActor = null;

    // Fired (name, npcId) once when the player's current target dies - a confirmed player kill,
    // drop or no drop. The loot tracker uses this to advance kill-count / dryness on every kill.
    private ObjIntConsumer<String> killSink;

    // Fired (name, atLeast) with a kill-count number parsed from the game's own chat lines
    // ("... kill count is: N"). Fed into the per-source counter via Math.max, so it can only ever
    // raise a count, never lower one. Wired by the plugin to LootTrackerManager.raiseKillCount.
    private ObjIntConsumer<String> killCountSink;

    // NPC instances the player (or their cannon / thrall) has attacked this encounter. Identity
    // set, cleared per fight - the "did I deal damage to this actor" half of the confirmed-kill
    // test, so a cannon / thrall / AoE / tab-target kill still advances kill count even though the
    // dead actor was never the tracked activeTargetActor.
    private final Set<Actor> engagedActors = Collections.newSetFromMap(new IdentityHashMap<>());

    // The most recent "kill count is: N" chat line: the dead monster's base name (lower-case) and
    // the tick it arrived. Lets a death with no ActorDeath / despawn still count as a confirmed
    // kill when the game itself just told us we killed that monster.
    private String recentKcName;
    private int recentKcTick = Integer.MIN_VALUE;
    private static final int KC_MESSAGE_WINDOW_TICKS = 5;

    // "Your <name> kill count is: 1,234." / "Your <name> killcount is: N" (bosses, slayer helper).
    private static final Pattern KC_LINE = Pattern.compile(
        "your (.+?) kill ?count is:\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE);
    // "Your completed Chambers of Xeric count is: 40." / "Your Tombs of Amascut count is: 3." (raids).
    private static final Pattern RAID_LINE = Pattern.compile(
        "your (?:completed )?(.+?) count is:\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE);
    // Collection-log style "You have killed the Chaos Elemental 57 times."
    private static final Pattern KILLED_LINE = Pattern.compile(
        "you have killed (?:the )?(.+?) ([\\d,]+) times", Pattern.CASE_INSENSITIVE);

    // Monster-name -> max HP, wired by the plugin from the Bestiary so the live boss-progress
    // readout can turn an HP fraction into a time-to-kill. Null in unit tests (-> hpKnown=false).
    private java.util.function.ToIntFunction<String> bossHpLookup;

    // --- Tutorial demo mode --------------------------------------------------------------------
    // While these are set, every scope-resolving READ returns the staged fixture instead of live
    // data, and the user-triggered scope-select / reset entry points are no-ops. Nothing else is
    // touched - the live encounter, history, session/total aggregates, the tick loop, listeners
    // and persistence all keep running - so clearDemoOverride() restores the exact live view.
    // Set only by the interactive tutorial overlay.
    private EncounterSegment demoSegment;
    private List<EncounterSegment> demoDropdown;
    private LiveBossProgress demoBossProgress;

    /** Live single-target fight progress for the HUD: HP fraction + estimated time-to-kill + pace. */
    public static final class LiveBossProgress
    {
        private final String name;
        private final double hpFraction;
        private final int etaSeconds;      // -1 when max HP is unknown
        private final double projectedKph; // 0 when eta is unknown
        private final boolean hpKnown;

        public LiveBossProgress(String name, double hpFraction, int etaSeconds, double projectedKph, boolean hpKnown)
        {
            this.name = name;
            this.hpFraction = hpFraction;
            this.etaSeconds = etaSeconds;
            this.projectedKph = projectedKph;
            this.hpKnown = hpKnown;
        }

        public String getName() { return name; }
        public double getHpFraction() { return hpFraction; }
        public int getEtaSeconds() { return etaSeconds; }
        public double getProjectedKph() { return projectedKph; }
        public boolean isHpKnown() { return hpKnown; }
    }

    // Per-attack dedup for hit accuracy. A multi-hitsplat weapon (Scythe, claws, blowpipe, ...)
    // lands every splat inside one game tick; only the first counts as an attack attempt. These
    // sets are cleared every onGameTick so a stale value can never collapse separate attacks.
    private final java.util.Set<String> weaponsAttackedThisTick = new java.util.HashSet<>();
    private final java.util.Set<String> weaponsLandedThisTick = new java.util.HashSet<>();
    // Kept only for attack-uptime cadence (creditAttackCycle).
    private int lastPlayerAttackTick = Integer.MIN_VALUE;
    private String lastPlayerAttackWeapon = null;

    private final BuffTrackingEngine buffTracker;
    private final MultiTargetTracker multiTargetTracker;

    // Set by the plugin so the "Total" scope survives logout. Null in unit tests.
    private ConfigManager configManager;
    private static final String TOTAL_KEY = "combatTotalStats";

    @Inject
    public CombatEncounterManager(Client client, BuffTrackingEngine buffTracker, MultiTargetTracker multiTargetTracker)
    {
        this.client = client;
        this.buffTracker = buffTracker;
        this.multiTargetTracker = multiTargetTracker;
        if (this.buffTracker != null)
        {
            this.buffTracker.setBuffChangeSink(this::onBuffChange);
        }
        this.currentSessionEncounter = createSessionSegment("Current Session", SegmentType.SESSION_CURRENT);
        this.overallSessionEncounter = createSessionSegment("Total", SegmentType.SESSION_TOTAL);
        String playerName = (client != null && client.getLocalPlayer() != null) ? client.getLocalPlayer().getName() : "You";
        this.emptyEncounterPlaceholder = new EncounterSegment(UUID.randomUUID(), "No fights yet", SegmentType.ENCOUNTER, 0, playerName);
        this.emptyEncounterPlaceholder.setStatus(SegmentStatus.COMPLETED);
    }

    public CombatEncounterManager(Client client)
    {
        this(client, new BuffTrackingEngine(client), new MultiTargetTracker(null));
    }

    public EncounterSegment getCurrentEncounter()
    {
        return currentEncounter;
    }

    /** The most recently finalized fight, or null before the first fight of the run. */
    public EncounterSegment getLastEncounter()
    {
        return lastEncounter;
    }

    /** The "Live" scope (the current segment): the live fight if any, else the last finished one. */
    public EncounterSegment getEncounterScope()
    {
        if (currentEncounter != null)
        {
            return currentEncounter;
        }
        return lastEncounter != null ? lastEncounter : emptyEncounterPlaceholder;
    }

    public EncounterSegment getCurrentSessionEncounter()
    {
        return currentSessionEncounter;
    }

    public EncounterSegment getOverallSessionEncounter()
    {
        return overallSessionEncounter;
    }

    public EncounterSegment getSelectedEncounter()
    {
        return selectedEncounter;
    }

    public BuffTrackingEngine getBuffTracker()
    {
        return buffTracker;
    }

    public MultiTargetTracker getMultiTargetTracker()
    {
        return multiTargetTracker;
    }

    public Actor getActiveTargetActor()
    {
        return activeTargetActor;
    }

    public int getLastCombatTick()
    {
        return lastCombatTick;
    }

    /** The live game tick (for live buff-uptime math), or the last combat tick in a test/no-client context. */
    public int getCurrentTick()
    {
        return (client != null) ? client.getTickCount() : lastCombatTick;
    }

    public void setSessionTimeoutSeconds(int seconds)
    {
        if (seconds <= 0)
        {
            this.sessionInactivityTimeoutTicks = Integer.MAX_VALUE; // Never reset automatically
        }
        else
        {
            this.sessionInactivityTimeoutTicks = Math.max(10, (seconds * 10) / 6);
        }
    }

    /** How many finished fights the "Past fights" browser keeps. Min 5. */
    public synchronized void setSegmentHistoryDepth(int depth)
    {
        this.segmentHistoryDepth = Math.max(5, Math.min(100, depth));
        while (historicalEncounters.size() > segmentHistoryDepth)
        {
            historicalEncounters.remove(0);
        }
        notifyListeners();
    }

    public int getSegmentHistoryDepth()
    {
        return segmentHistoryDepth;
    }

    /** Trash merge: fold consecutive same-target kills into one "Gargoyle xN" row. */
    public synchronized void setMergeTrash(boolean merge)
    {
        this.mergeTrash = merge;
    }

    public boolean isMergeTrash()
    {
        return mergeTrash;
    }

    /** Seconds out of combat before a fight is considered over (default ~20). Min 6s. */
    public void setEncounterTimeoutSeconds(int seconds)
    {
        this.encounterTimeoutTicks = Math.max(10, (Math.max(6, seconds) * 10) / 6);
    }

    /** Rolling-average window (seconds) for the fight graph. Applied to every scope + new fights. */
    public synchronized void setGraphSmoothingSeconds(int seconds)
    {
        this.graphSmoothingSeconds = Math.max(1, seconds);
        currentSessionEncounter.getLocalPlayerStats().setRollingWindowSeconds(graphSmoothingSeconds);
        overallSessionEncounter.getLocalPlayerStats().setRollingWindowSeconds(graphSmoothingSeconds);
        if (currentEncounter != null)
        {
            currentEncounter.getLocalPlayerStats().setRollingWindowSeconds(graphSmoothingSeconds);
        }
    }

    /** Exposed for tests. */
    public int getEncounterTimeoutTicks()
    {
        return encounterTimeoutTicks;
    }

    public void addCombatListener(Runnable listener)
    {
        combatListeners.add(listener);
    }

    public void removeCombatListener(Runnable listener)
    {
        combatListeners.remove(listener);
    }

    private void notifyListeners()
    {
        for (Runnable listener : combatListeners)
        {
            try
            {
                listener.run();
            }
            catch (RuntimeException e)
            {
                log.trace("Combat listener threw", e);
            }
        }
    }

    public synchronized EncounterSegment getSelectedOrCurrentEncounter()
    {
        if (demoSegment != null)
        {
            return demoSegment;
        }
        if (selectedEncounter != null)
        {
            // The "Group" scope disappears when you leave the party - don't stay pinned to a ghost.
            if (selectedEncounter.getSegmentType() == SegmentType.GROUP
                && (groupScopeSupplier == null || groupScopeSupplier.get() == null))
            {
                selectedEncounter = null;
                autoFollow = true;
            }
            else
            {
                return selectedEncounter;
            }
        }
        // Auto-follow into the shared party meter: while a group fight is live and the user hasn't
        // pinned a scope, the HUD and panel show the combined group automatically, then fall back
        // to the solo fight when it ends. No "Group" scope pick required.
        if (autoFollow && groupScopeSupplier != null && groupAutoFollowSupplier != null
            && groupAutoFollowSupplier.getAsBoolean())
        {
            EncounterSegment group = groupScopeSupplier.get();
            if (group != null)
            {
                return group;
            }
        }
        if (currentEncounter != null)
        {
            return currentEncounter;
        }
        // At the kill the HUD holds the just-finished fight instead of snapping to the session.
        if (lastEncounter != null)
        {
            return lastEncounter;
        }
        return currentSessionEncounter;
    }

    public synchronized void selectEncounter(EncounterSegment segment)
    {
        if (demoSegment != null)
        {
            return; // a tutorial click on a scope row must not repin the live view
        }
        // Picking the "Live" scope (live fight, last fight, or the empty placeholder)
        // re-arms auto-follow; picking anything else pins that scope and stops auto-follow.
        if (segment == null || segment == currentEncounter || segment == lastEncounter || segment == emptyEncounterPlaceholder)
        {
            this.autoFollow = true;
            this.selectedEncounter = null;
        }
        else
        {
            this.autoFollow = false;
            this.selectedEncounter = segment;
        }
        notifyListeners();
    }

    public synchronized boolean isAutoFollow()
    {
        return autoFollow;
    }

    public synchronized void setAutoFollow(boolean follow)
    {
        if (demoSegment != null)
        {
            return;
        }
        this.autoFollow = follow;
        if (follow)
        {
            this.selectedEncounter = null;
        }
        notifyListeners();
    }

    public synchronized void setGroupScopeSupplier(java.util.function.Supplier<EncounterSegment> supplier)
    {
        this.groupScopeSupplier = supplier;
    }

    public synchronized void setGroupAutoFollowSupplier(java.util.function.BooleanSupplier supplier)
    {
        this.groupAutoFollowSupplier = supplier;
    }

    /**
     * True while a shared party fight is live near the local player - either they are in it, or a
     * teammate within range has pulled and they haven't swung yet. Lets the combat-state banner
     * fire "Entering Combat" for a fight a teammate started. Always false with no party wired.
     */
    public synchronized boolean isGroupCombatActive()
    {
        return groupAutoFollowSupplier != null && groupAutoFollowSupplier.getAsBoolean();
    }

    public synchronized List<EncounterSegment> getAllSegmentsForDropdown()
    {
        if (demoSegment != null)
        {
            return demoDropdown != null
                ? new ArrayList<>(demoDropdown)
                : new ArrayList<>(java.util.Collections.singletonList(demoSegment));
        }
        List<EncounterSegment> list = new ArrayList<>();
        // Always three fixed scope rows: Live, Current Session, Total.
        list.add(getEncounterScope());
        list.add(currentSessionEncounter);
        list.add(overallSessionEncounter);
        // Optional "Group" scope: the shared party meter, when a party is active.
        if (groupScopeSupplier != null)
        {
            EncounterSegment group = groupScopeSupplier.get();
            if (group != null)
            {
                list.add(group);
            }
        }
        // Then historical fights, newest first, minus whatever is already shown as row 1.
        EncounterSegment row1 = list.get(0);
        for (int i = historicalEncounters.size() - 1; i >= 0; i--)
        {
            EncounterSegment seg = historicalEncounters.get(i);
            if (seg != row1)
            {
                list.add(seg);
            }
        }
        return list;
    }

    /** Refreshes the combat-activity tick markers and rolls the Current Session if it went idle. */
    private void touchCombatTick(int currentTick)
    {
        if (sessionInactivityTimeoutTicks > 0 && sessionInactivityTimeoutTicks != Integer.MAX_VALUE
            && lastSessionCombatTick > 0 && currentTick - lastSessionCombatTick > sessionInactivityTimeoutTicks)
        {
            resetCurrentSession(currentTick);
        }
        lastSessionCombatTick = currentTick;
        lastCombatTick = currentTick;
    }

    private String localPlayerName()
    {
        return (client != null && client.getLocalPlayer() != null) ? client.getLocalPlayer().getName() : null;
    }

    /** Name to show for an NPC target, honouring multi-target "(1)/(2)" tagging when enabled. */
    private String resolveTargetName(NPC npc)
    {
        if (npc == null)
        {
            return "Combat Encounter";
        }
        String name = (multiTargetTracker != null) ? multiTargetTracker.getTargetDisplayName(npc) : npc.getName();
        return (name == null || name.isEmpty()) ? "Combat Encounter" : name;
    }

    private boolean isPlaceholderName(String name)
    {
        if (name == null || name.isEmpty() || "Combat Encounter".equals(name))
        {
            return true;
        }
        String player = localPlayerName();
        return player != null && player.equals(name);
    }

    /**
     * A player / thrall / cannon hit landed on an NPC. Opens or refreshes the Current Encounter.
     * Encounters strictly represent NPC targets — never the local player.
     */
    public synchronized void notifyCombatAction(Actor target, int currentTick)
    {
        if (isLocalFollower(target))
        {
            return; // a pet is never a combat participant, even if something upstream passed it
        }
        touchCombatTick(currentTick);

        NPC npcTarget = (target instanceof NPC) ? (NPC) target : null;

        if (currentEncounter == null)
        {
            String name = (npcTarget != null) ? resolveTargetName(npcTarget) : "Combat Encounter";
            startNewSegment(name, SegmentType.ENCOUNTER, currentTick, npcTarget);
        }
        else if (npcTarget != null)
        {
            // Log a mid-fight target switch (multi-mob pull): only when the focus actually moves
            // to a different NPC index and there is more than one tracked target.
            if (activeTargetActor instanceof NPC && npcTarget != activeTargetActor
                && ((NPC) activeTargetActor).getIndex() != npcTarget.getIndex()
                && multiTargetTracker != null && multiTargetTracker.getActiveTargetCount() > 1)
            {
                String to = resolveTargetName(npcTarget);
                currentEncounter.getLocalPlayerStats().addTimelineEvent(CombatTimelineEvent.builder()
                    .clientTick(currentTick).timeFormatted(ledgerTimeStr()).eventType("SWITCH").icon("-")
                    .description("Switched to " + to).color(new Color(180, 180, 190))
                    .source("You").target(to).build());
            }
            activeTargetActor = npcTarget;
            if (isPlaceholderName(currentEncounter.getTargetName()))
            {
                currentEncounter.setTargetName(resolveTargetName(npcTarget));
            }
            if (currentEncounter.getNpcCombatLevel() <= 0)
            {
                currentEncounter.setNpcInfo(npcTarget.getId(), npcTarget.getCombatLevel());
            }
        }
        // Remember every NPC we swing at (this path is also hit for thrall / cannon splats and for
        // AoE / off-primary targets) so its death can be credited as our kill even if it was never
        // the tracked activeTargetActor.
        if (npcTarget != null)
        {
            engagedActors.add(npcTarget);
        }
        notifyListeners();
    }

    /**
     * The player took a hitsplat. Never names an encounter after the player; only opens one when the
     * player is actually interacting with an NPC ("an NPC the player is fighting hit back").
     */
    public synchronized void notifyDamageTakenInCombat(Actor interactingWith, int currentTick)
    {
        if (isLocalFollower(interactingWith))
        {
            return;
        }
        touchCombatTick(currentTick);
        if (currentEncounter == null && interactingWith instanceof NPC)
        {
            NPC npc = (NPC) interactingWith;
            startNewSegment(resolveTargetName(npc), SegmentType.ENCOUNTER, currentTick, npc);
        }
        notifyListeners();
    }

    /**
     * True when this actor is the local player's follower / pet (Border Collie puppy, boss pets,
     * skilling pets, ...). Such an NPC permanently {@code getInteracting()}s its owner to face them,
     * so the "who is attacking me" resolver can otherwise pick it up and name a fight after it.
     */
    private boolean isLocalFollower(Actor a)
    {
        if (!(a instanceof NPC) || client == null)
        {
            return false;
        }
        if (a == client.getFollower())
        {
            return true;
        }
        NPCComposition c = ((NPC) a).getComposition();
        return c != null && c.isFollower();
    }

    /** Lifesteal / passive heal on the player — keeps a live encounter warm, never starts one. */
    public synchronized void notifyHealInCombat(int currentTick)
    {
        if (currentEncounter != null)
        {
            touchCombatTick(currentTick);
            notifyListeners();
        }
    }

    public synchronized void onGameTick(int currentTick)
    {
        // Fresh per game tick: a weapon's first splat this tick is one attack attempt, the rest
        // are extra hitsplats of it.
        weaponsAttackedThisTick.clear();
        weaponsLandedThisTick.clear();
        flushPendingBuffs(currentTick);

        // Roll the Current Session over after a configurable idle gap. Checked here so it fires
        // promptly on an idle tick instead of only on the next hitsplat. sessionInactivityTimeout
        // is Integer.MAX_VALUE when the "extra idle reset" is off (the default) - never fires then.
        if (sessionInactivityTimeoutTicks > 0 && sessionInactivityTimeoutTicks != Integer.MAX_VALUE
            && lastSessionCombatTick > 0 && currentTick - lastSessionCombatTick > sessionInactivityTimeoutTicks)
        {
            resetCurrentSession(currentTick);
        }

        // Age out multi-target entries whose NPC silently left render distance (no despawn event),
        // so one ghost target can't keep the "pull ongoing" guard true forever.
        if (multiTargetTracker != null)
        {
            multiTargetTracker.pruneStale(currentTick, STALE_TARGET_TICKS);
        }
        expireDebuffs(currentTick);

        boolean fightActive = currentEncounter != null && currentEncounter.isInCombat()
            && currentTick - lastCombatTick <= encounterTimeoutTicks;

        // combatSeconds is the one rate denominator for the Live fight AND both Overall scopes, so
        // a kill reads the same DPS/DTPS/HPS in each. Advance it only as far as the last real
        // combat action (lastCombatTick), never to "now": an inter-attack gap up to one weapon
        // cycle counts as engaged time, a longer pause is lost, and once hits stop the denominator
        // - and the shown rate - freezes exactly at the last hit instead of decaying through the
        // ~20s encounter timeout (which also stops the Overall scopes banking that idle time).
        // Done before every updateDuration() below so all three scopes publish the same value.
        if (currentEncounter != null && currentEncounter.isInCombat()
            && lastCombatTick > lastAccruedCombatTick)
        {
            int upTo = Math.min(currentTick, lastCombatTick);
            int gap = upTo - lastAccruedCombatTick;
            if (gap > 0)
            {
                double engaged = 0.6 * Math.min(gap, MAX_ATTACK_CYCLE_TICKS);
                currentEncounter.addCombatSeconds(engaged);
                currentSessionEncounter.addCombatSeconds(engaged);
                overallSessionEncounter.addCombatSeconds(engaged);
                lastAccruedCombatTick = upTo;
            }
        }

        if (currentEncounter != null && currentEncounter.isInCombat())
        {
            currentEncounter.updateDuration(currentTick);
            currentEncounter.getLocalPlayerStats().recordTimeSeriesSecond((int) currentEncounter.getDurationSeconds(), 0, 0, 0, null);
            if (isGroupCombatActive())
            {
                // Latch it for the history badge - a teammate was in this fight with you.
                currentEncounter.markGroupFight();
            }
            if (currentTick - lastCombatTick > encounterTimeoutTicks)
            {
                // Timed out with the target still alive -> disengaged, not a kill.
                finalizeCurrentSegment(SegmentStatus.ABANDONED, currentTick);
            }
        }

        currentSessionEncounter.updateDuration(currentTick);
        currentSessionEncounter.getLocalPlayerStats().recordTimeSeriesSecond((int) currentSessionEncounter.getDurationSeconds(), 0, 0, 0, null);
        overallSessionEncounter.updateDuration(currentTick);
        overallSessionEncounter.getLocalPlayerStats().recordTimeSeriesSecond((int) overallSessionEncounter.getDurationSeconds(), 0, 0, 0, null);

        // Flush "Total" to disk periodically so a client crash mid-raid doesn't lose the last
        // stretch of a very long fight (persistTotal otherwise only runs at fight end / logout).
        if (fightActive && currentTick - lastTotalPersistTick > TOTAL_PERSIST_INTERVAL_TICKS)
        {
            lastTotalPersistTick = currentTick;
            persistTotal();
        }
        notifyListeners();
    }

    /** True while more than one tracked NPC is still alive — one death shouldn't end the pull. */
    private boolean multiTargetPullOngoing()
    {
        return multiTargetTracker != null && multiTargetTracker.getActiveTargetCount() > 1;
    }

    public void setKillSink(ObjIntConsumer<String> sink)
    {
        this.killSink = sink;
    }

    /** Sink for a kill-count number parsed from a game chat line; applied to the counter via Math.max. */
    public void setKillCountSink(ObjIntConsumer<String> sink)
    {
        this.killCountSink = sink;
    }

    /**
     * Read the game's own kill-count chat lines ("Your Vorkath kill count is: N", raid "completed
     * ... count is: N", collection-log "you have killed ... N times") and raise the per-source
     * counter to that number. Never lowers a count. GAMEMESSAGE / SPAM only; never writes chat.
     */
    public synchronized void onChatMessage(ChatMessage event)
    {
        if (event == null)
        {
            return;
        }
        ChatMessageType type = event.getType();
        if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM)
        {
            return;
        }
        String msg = event.getMessage();
        if (msg == null || msg.isEmpty())
        {
            return;
        }
        String plain = msg.replaceAll("<[^>]+>", "").trim();

        String name = null;
        long count = -1;
        Matcher m = KC_LINE.matcher(plain);
        if (m.find())
        {
            name = m.group(1);
            count = parseCount(m.group(2));
        }
        if (name == null)
        {
            m = RAID_LINE.matcher(plain);
            if (m.find())
            {
                name = m.group(1);
                count = parseCount(m.group(2));
            }
        }
        if (name == null)
        {
            m = KILLED_LINE.matcher(plain);
            if (m.find())
            {
                name = m.group(1);
                count = parseCount(m.group(2));
            }
        }
        if (name == null || count <= 0 || count > Integer.MAX_VALUE)
        {
            return;
        }
        name = name.trim();
        recentKcName = stripMultiTargetTag(name).toLowerCase(Locale.ROOT);
        recentKcTick = getCurrentTick();
        if (killCountSink != null)
        {
            try
            {
                killCountSink.accept(name, (int) count);
            }
            catch (RuntimeException ignored)
            {
                // a sink failure must never break chat handling
            }
        }
    }

    private static long parseCount(String raw)
    {
        try
        {
            return Long.parseLong(raw.replace(",", "").trim());
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
    }

    /** Strip a trailing multi-target tag " (2)" so "Dagannoth (2)" compares equal to "Dagannoth". */
    private static String stripMultiTargetTag(String name)
    {
        if (name == null)
        {
            return null;
        }
        int i = name.lastIndexOf(" (");
        if (i > 0 && name.endsWith(")"))
        {
            String inner = name.substring(i + 2, name.length() - 1);
            if (!inner.isEmpty() && inner.chars().allMatch(Character::isDigit))
            {
                return name.substring(0, i);
            }
        }
        return name;
    }

    /** Case-insensitive name match that ignores a trailing multi-target " (n)" tag on either side. */
    private static boolean baseNameMatches(String a, String b)
    {
        return a != null && b != null
            && stripMultiTargetTag(a).equalsIgnoreCase(stripMultiTargetTag(b));
    }

    public synchronized void setBossHpLookup(java.util.function.ToIntFunction<String> lookup)
    {
        this.bossHpLookup = lookup;
    }

    public synchronized boolean isDemoMode()
    {
        return demoSegment != null;
    }

    /**
     * Point every combat view at a staged fixture for the interactive tutorial. Purely a read-time
     * redirect: the live encounter, fight history, session/total aggregates, tick loop, listeners
     * and persistence are all left running and untouched. {@link #clearDemoOverride()} restores the
     * live view exactly. {@code dropdown} may be null (the scope list then holds only {@code segment}).
     */
    public synchronized void setDemoOverride(EncounterSegment segment, List<EncounterSegment> dropdown,
        LiveBossProgress bossProgress)
    {
        this.demoSegment = segment;
        this.demoDropdown = (dropdown != null) ? new ArrayList<>(dropdown) : null;
        this.demoBossProgress = bossProgress;
        notifyListeners();
    }

    public synchronized void clearDemoOverride()
    {
        this.demoSegment = null;
        this.demoDropdown = null;
        this.demoBossProgress = null;
        notifyListeners();
    }

    /**
     * Live progress for a single-target fight against an NPC with a health bar, or null. HP% is
     * always real; the time-to-kill and kills/hour are only filled when the Bestiary knows the
     * monster's max HP (else {@code etaSeconds == -1}).
     */
    public synchronized LiveBossProgress getLiveBossProgress()
    {
        if (demoSegment != null)
        {
            return demoBossProgress;
        }
        EncounterSegment enc = currentEncounter;
        if (enc == null || !enc.isInCombat() || multiTargetPullOngoing()
            || !(activeTargetActor instanceof NPC))
        {
            return null;
        }
        NPC npc = (NPC) activeTargetActor;
        int ratio = npc.getHealthRatio();
        int scale = npc.getHealthScale();
        if (ratio < 0 || scale <= 0)
        {
            return null; // no health bar shown for this NPC
        }
        double frac = Math.max(0.0, Math.min(1.0, ratio / (double) scale));
        String name = enc.getTargetName();
        int maxHp = (bossHpLookup != null && name != null) ? Math.max(0, bossHpLookup.applyAsInt(name)) : 0;
        double dps = enc.getLocalPlayerStats().getDps();

        int eta = -1;
        double kph = 0;
        if (maxHp > 0 && dps > 0.5)
        {
            eta = (int) Math.round(frac * maxHp / dps);
            int total = enc.getDurationSeconds() + eta;
            if (total > 0)
            {
                kph = 3600.0 / total;
            }
        }
        return new LiveBossProgress(name, frac, eta, kph, maxHp > 0);
    }

    /** Notify the kill sink that the player's current target (an NPC) has died. */
    private void fireKill(Actor actor)
    {
        if (killSink != null && actor instanceof NPC)
        {
            NPC n = (NPC) actor;
            if (n.getName() != null)
            {
                try
                {
                    killSink.accept(n.getName(), n.getId());
                }
                catch (RuntimeException ignored)
                {
                    // a sink failure must never break encounter finalization
                }
            }
        }
    }

    public synchronized void handleActorDeath(Actor actor, int currentTick)
    {
        if (client == null || actor == null) return;
        Player localPlayer = client.getLocalPlayer();

        if (actor == localPlayer)
        {
            finalizeCurrentSegment(SegmentStatus.WIPED, currentTick);
            return;
        }
        if (currentEncounter == null)
        {
            return;
        }

        String deadName = actor.getName();
        boolean isActiveTarget = actor == activeTargetActor;
        boolean engaged = engagedActors.contains(actor);
        boolean nameMatchesEncounter = baseNameMatches(deadName, currentEncounter.getTargetName());
        boolean kcJustConfirmed = recentKcName != null && deadName != null
            && stripMultiTargetTag(deadName).equalsIgnoreCase(recentKcName)
            && currentTick - recentKcTick <= KC_MESSAGE_WINDOW_TICKS;

        // Does this death end the encounter? The tracked actor itself, or - when that ref has
        // desynced (null, or a differently multi-target-tagged instance we were also hitting) - a
        // same-base-name mob we actually engaged. A stray same-named mob we never touched is
        // neither, so it can't finalize your fight (D5).
        boolean endsEncounter = isActiveTarget
            || (activeTargetActor == null && nameMatchesEncounter)
            || (engaged && nameMatchesEncounter);

        // Does the kill count for us? Anything we (or our cannon / thrall) attacked, the tracked
        // target, or a mob the game itself just told us we killed.
        boolean creditable = isActiveTarget || engaged || kcJustConfirmed;

        if (!endsEncounter && !creditable)
        {
            return;
        }

        if (creditable)
        {
            fireKill(actor);
        }

        if (multiTargetPullOngoing())
        {
            // One mob of a multi-mob pull died - keep the encounter open for the rest.
            engagedActors.remove(actor);
            if (actor == activeTargetActor)
            {
                activeTargetActor = null;
            }
            notifyListeners();
        }
        else if (endsEncounter)
        {
            finalizeCurrentSegment(SegmentStatus.COMPLETED, currentTick);
        }
        else
        {
            // Credited a side kill (cannon / thrall / AoE add) while the primary fight continues.
            engagedActors.remove(actor);
            notifyListeners();
        }
    }

    public synchronized void handleNpcDespawned(NPC npc, int currentTick)
    {
        if (currentEncounter != null && npc == activeTargetActor && currentEncounter.isInCombat())
        {
            // Despawn + dead = a kill with no ActorDeath (animation-less NPC, off-screen). Use
            // isDead() as well as the health ratio, which reads -1 for NPCs that never showed a
            // health bar (D6). A despawn while still alive (left render / phase dive) is left for
            // the inactivity timeout to close as ABANDONED.
            if ((npc.isDead() || npc.getHealthRatio() == 0) && !multiTargetPullOngoing())
            {
                fireKill(npc);
                finalizeCurrentSegment(SegmentStatus.COMPLETED, currentTick);
            }
        }
    }

    public synchronized void recordPlayerDamageDealt(CombatStyle style, int amount, String weaponName, int tick)
    {
        recordPlayerDamageDealt(style, amount, weaponName, tick, null, false);
    }

    public synchronized void recordPlayerDamageDealt(CombatStyle style, int amount, String weaponName, int tick, String enemyName)
    {
        recordPlayerDamageDealt(style, amount, weaponName, tick, enemyName, false);
    }

    public synchronized void recordPlayerDamageDealt(CombatStyle style, int amount, String weaponName, int tick, String enemyName, boolean isSpecial)
    {
        // Multi-hitsplat weapons (Scythe x3, claws x4, blowpipe, Karil's, ...) land every splat
        // inside one game tick. The first splat of a weapon this tick is the attack attempt; the
        // rest only add damage (never a second attempt). Damage always accrues; only the
        // attempt / hit bookkeeping is de-duplicated, so a splat can never double-count damage.
        String wk = ((weaponName == null || weaponName.isEmpty()) ? "?" : weaponName) + "@" + tick;
        boolean newAttack = weaponsAttackedThisTick.add(wk);
        boolean alreadyLanded = weaponsLandedThisTick.contains(wk);
        if (amount > 0)
        {
            weaponsLandedThisTick.add(wk);
        }
        boolean creditLateHit = !newAttack && amount > 0 && !alreadyLanded;
        if (newAttack)
        {
            creditAttackCycle(tick);
            lastPlayerAttackTick = tick;
            lastPlayerAttackWeapon = weaponName;
        }

        // The enemy this splat landed on: the named off-primary target in an AoE / multi-mob pull,
        // else the fight's primary target. Feeds the per-source x target breakdown.
        String hitTarget = (enemyName != null && !enemyName.isEmpty()) ? enemyName
            : (currentEncounter != null ? currentEncounter.getTargetName() : null);

        if (currentEncounter != null)
        {
            accruePlayerDamage(currentEncounter.getLocalPlayerStats(), style, amount, weaponName, tick, !newAttack, creditLateHit, isSpecial, hitTarget);
            currentEncounter.getLocalPlayerStats().recordTimeSeriesSecond((int) currentEncounter.getDurationSeconds(), amount, 0, 0, style, amount > 0 ? "Dealt " + amount + " (" + weaponName + ")" : null);
            if (enemyName != null && !enemyName.isEmpty())
            {
                currentEncounter.recordDamageToEnemy(enemyName, style, amount, weaponName, tick);
            }
        }
        accruePlayerDamage(currentSessionEncounter.getLocalPlayerStats(), style, amount, weaponName, tick, !newAttack, creditLateHit, isSpecial, hitTarget);
        currentSessionEncounter.getLocalPlayerStats().recordTimeSeriesSecond((int) currentSessionEncounter.getDurationSeconds(), amount, 0, 0, style, amount > 0 ? "Dealt " + amount + " (" + weaponName + ")" : null);

        accruePlayerDamage(overallSessionEncounter.getLocalPlayerStats(), style, amount, weaponName, tick, !newAttack, creditLateHit, isSpecial, hitTarget);
        overallSessionEncounter.getLocalPlayerStats().recordTimeSeriesSecond((int) overallSessionEncounter.getDurationSeconds(), amount, 0, 0, style, amount > 0 ? "Dealt " + amount + " (" + weaponName + ")" : null);

        String timeStr = ledgerTimeStr();
        String styleIcon = resolveStyleIcon(style);
        Color eventColor = style != null && style.getPrimaryColor() != null ? style.getPrimaryColor() : CombatMeterColors.TEXT_ACCENT_GOLD;
        // Name the target on the ledger line only when it isn't the fight's primary target - i.e.
        // in a multi-mob pull / AoE, so "Dealt 12 (Ice Barrage -> Maniacal monkey (3))" is legible
        // without cluttering every single-target line.
        boolean offPrimary = enemyName != null && !enemyName.isEmpty()
            && currentEncounter != null && !enemyName.equals(currentEncounter.getTargetName());
        String desc = amount > 0
            ? ("Dealt " + amount + " (" + weaponName + (offPrimary ? " -> " + enemyName : "") + ")")
            : ("0 Splash/Miss (" + weaponName + ")");
        String targetName = (enemyName != null && !enemyName.isEmpty()) ? enemyName
            : (currentEncounter != null ? currentEncounter.getTargetName() : null);
        CombatTimelineEvent event = CombatTimelineEvent.builder()
            .clientTick(tick).timeFormatted(timeStr).eventType("HIT").icon(styleIcon)
            .description(desc).color(eventColor).amount(amount)
            .source("You").target(targetName).style(style).weaponOrSpell(weaponName)
            .miss(amount <= 0).special(isSpecial)
            .build();

        if (currentEncounter != null) currentEncounter.getLocalPlayerStats().addTimelineEvent(event);
        currentSessionEncounter.getLocalPlayerStats().addTimelineEvent(event);
        overallSessionEncounter.getLocalPlayerStats().addTimelineEvent(event);

        if (currentEncounter != null && amount > 0)
        {
            maybeRecordDebuff(weaponName, enemyName, tick);
        }

        if (isSpecial && newAttack)
        {
            if (currentEncounter != null) currentEncounter.getLocalPlayerStats().recordSpecialAttack();
            currentSessionEncounter.getLocalPlayerStats().recordSpecialAttack();
            overallSessionEncounter.getLocalPlayerStats().recordSpecialAttack();

            CombatTimelineEvent specEvent = CombatTimelineEvent.builder()
                .clientTick(tick).timeFormatted(timeStr).eventType("SPEC").icon("!")
                .description((amount > 0 ? "Special: " + amount : "Special: missed") + " (" + weaponName + ")")
                .color(new Color(255, 202, 40)).amount(amount)
                .source("You").target(targetName).style(style).weaponOrSpell(weaponName)
                .miss(amount <= 0).special(true)
                .build();
            if (currentEncounter != null) currentEncounter.getLocalPlayerStats().addTimelineEvent(specEvent);
            currentSessionEncounter.getLocalPlayerStats().addTimelineEvent(specEvent);
            overallSessionEncounter.getLocalPlayerStats().addTimelineEvent(specEvent);
        }

        notifyListeners();
    }

    /**
     * Book the gap between the previous attack and this one as active + lost ticks, on all three
     * scopes. Call from a new-attack path BEFORE lastPlayerAttackTick is moved forward.
     */
    private void creditAttackCycle(int newAttackTick)
    {
        if (lastPlayerAttackTick == Integer.MIN_VALUE || newAttackTick <= lastPlayerAttackTick)
        {
            return;
        }
        int gap = newAttackTick - lastPlayerAttackTick;
        int active = Math.min(gap, MAX_ATTACK_CYCLE_TICKS);
        int lost = gap - active;
        if (currentEncounter != null) currentEncounter.getLocalPlayerStats().recordAttackCycle(active, lost);
        currentSessionEncounter.getLocalPlayerStats().recordAttackCycle(active, lost);
        overallSessionEncounter.getLocalPlayerStats().recordAttackCycle(active, lost);
    }

    /** Route one player hitsplat into a stats bucket: a new attack, or an extra splat of one. */
    private static void accruePlayerDamage(EntityCombatStats stats, CombatStyle style, int amount,
        String weaponName, int tick, boolean extraSplat, boolean creditLateHit, boolean special, String target)
    {
        if (extraSplat)
        {
            stats.recordExtraSplat(style, amount, weaponName, tick, creditLateHit, special, target);
        }
        else
        {
            stats.recordDamageDealt(style, amount, weaponName, tick, special, target);
        }
    }

    /**
     * If this landed hit was a bind / snare / freeze spell, log the CC and its nominal duration.
     * CC is per-fight state, so - unlike HIT / TAKEN / BUFF - DEBUFF ledger lines are written to
     * currentEncounter only, not the Session / Total scopes (by design).
     */
    private void maybeRecordDebuff(String attackName, String enemyName, int tick)
    {
        if (attackName == null || currentEncounter == null)
        {
            return;
        }
        Integer ticks = CC_DURATIONS.get(attackName.trim().toLowerCase());
        if (ticks == null)
        {
            return;
        }
        String target = (enemyName != null && !enemyName.isEmpty())
            ? enemyName : currentEncounter.getTargetName();
        currentEncounter.addDebuff(new com.osrscopilot.combat.model.DebuffApplication(attackName, target, tick, ticks));
        // Log the cast itself (like the game's own "You cast..." line) - not a duration or a
        // remaining-time countdown, which the RuneLite rules class as an opponent freeze timer.
        currentEncounter.getLocalPlayerStats().addTimelineEvent(CombatTimelineEvent.builder()
            .clientTick(tick).timeFormatted(ledgerTimeStr()).eventType("DEBUFF").icon("*")
            .description("Cast " + attackName + " on " + target).color(new Color(140, 190, 255))
            .source("You").target(target).weaponOrSpell(attackName)
            .build());
    }

    // A buff change is only logged once it has held its new state for this many ticks. A prayer
    // flick (on/off every tick) keeps resetting the timer and is never logged; a real activation
    // - or a genuine swap between prayers - settles and is logged.
    private static final int BUFF_SETTLE_TICKS = 2;
    private final java.util.Map<String, boolean[]> pendingBuff = new java.util.HashMap<>();   // name -> {state}
    private final java.util.Map<String, Integer> pendingBuffSince = new java.util.HashMap<>();
    private final java.util.Map<String, Boolean> lastLoggedBuffState = new java.util.HashMap<>();

    /** A buff / prayer toggled. Debounced; the actual ledger line is emitted by flushPendingBuffs. */
    private synchronized void onBuffChange(String buffName, boolean active)
    {
        if (buffName == null)
        {
            return;
        }
        boolean[] cur = pendingBuff.get(buffName);
        if (cur == null || cur[0] != active)
        {
            pendingBuff.put(buffName, new boolean[]{active});
            pendingBuffSince.put(buffName, getCurrentTick());
        }
    }

    /** Emit ledger lines for buff changes that have settled (held their new state a couple ticks). */
    private void flushPendingBuffs(int currentTick)
    {
        if (pendingBuff.isEmpty())
        {
            return;
        }
        boolean any = false;
        java.util.Iterator<java.util.Map.Entry<String, boolean[]>> it = pendingBuff.entrySet().iterator();
        while (it.hasNext())
        {
            java.util.Map.Entry<String, boolean[]> e = it.next();
            String name = e.getKey();
            boolean active = e.getValue()[0];
            Integer since = pendingBuffSince.get(name);
            if (since == null || currentTick - since < BUFF_SETTLE_TICKS)
            {
                continue;
            }
            it.remove();
            pendingBuffSince.remove(name);

            Boolean logged = lastLoggedBuffState.get(name);
            if (logged != null && logged == active)
            {
                continue; // net no change since we last logged - a flick that came back
            }
            lastLoggedBuffState.put(name, active);

            if (currentEncounter == null)
            {
                continue; // track state, but only write ledger lines during a live fight
            }
            CombatTimelineEvent ev = CombatTimelineEvent.builder()
                .clientTick(currentTick).timeFormatted(ledgerTimeStr()).eventType("BUFF").icon("~")
                .description(name + (active ? " activated" : " ended"))
                .color(active ? new Color(160, 200, 120) : new Color(140, 140, 150))
                .source("You").target("You").weaponOrSpell(name)
                .build();
            currentEncounter.getLocalPlayerStats().addTimelineEvent(ev);
            currentSessionEncounter.getLocalPlayerStats().addTimelineEvent(ev);
            overallSessionEncounter.getLocalPlayerStats().addTimelineEvent(ev);
            any = true;
        }
        if (any)
        {
            notifyListeners();
        }
    }

    /**
     * Close out CC entries whose nominal window has run its course, for the internal fight model
     * only. Deliberately emits no ledger event and no on-screen countdown - see the RuneLite rules
     * on opponent freeze/debuff timers.
     */
    private void expireDebuffs(int currentTick)
    {
        if (currentEncounter == null)
        {
            return;
        }
        // Close out the nominal window internally (used by the fight model / tests). No ledger
        // event and no on-screen "X frozen for Ns" - see the RuneLite rules on freeze timers.
        for (com.osrscopilot.combat.model.DebuffApplication d : currentEncounter.getOpenDebuffs())
        {
            if (currentTick >= d.getExpectedEndTick())
            {
                d.close(currentTick);
            }
        }
    }

    // ASCII-only tags for the action ledger - the RuneScape bitmap font has no dingbat/emoji glyphs.
    private String resolveStyleIcon(CombatStyle style)
    {
        if (style == null) return "+";
        switch (style)
        {
            case MAGIC: return "*";
            case RANGED: return ">";
            case POISON:
            case VENOM:
            case POISON_VENOM: return "o";
            case BURN:
            case BURN_BLEED:
            case BLEED: return "=";
            case THRALL: return "T";
            case CANNON: return "C";
            default: return "+";
        }
    }

    public synchronized void recordPlayerMiss(CombatStyle style)
    {
        recordPlayerMiss(style, null, getCurrentTick(), null, false);
    }

    public synchronized void recordPlayerMiss(CombatStyle style, String weaponName, int tick, String enemyName)
    {
        recordPlayerMiss(style, weaponName, tick, enemyName, false);
    }

    public synchronized void recordPlayerMiss(CombatStyle style, String weaponName, int tick, String enemyName, boolean isSpecial)
    {
        // A 0-damage splat of a weapon that already registered a splat this tick (Karil's,
        // blowpipe, or a splash next to a land) is the same attack - don't book a second attempt.
        String wk = ((weaponName == null || weaponName.isEmpty()) ? "?" : weaponName) + "@" + tick;
        boolean newAttack = weaponsAttackedThisTick.add(wk);
        if (newAttack)
        {
            creditAttackCycle(tick);
            lastPlayerAttackTick = tick;
            lastPlayerAttackWeapon = weaponName;

            if (currentEncounter != null)
            {
                currentEncounter.getLocalPlayerStats().recordMiss(style, weaponName, isSpecial);
            }
            currentSessionEncounter.getLocalPlayerStats().recordMiss(style, weaponName, isSpecial);
            overallSessionEncounter.getLocalPlayerStats().recordMiss(style, weaponName, isSpecial);

            if (isSpecial)
            {
                if (currentEncounter != null) currentEncounter.getLocalPlayerStats().recordSpecialAttack();
                currentSessionEncounter.getLocalPlayerStats().recordSpecialAttack();
                overallSessionEncounter.getLocalPlayerStats().recordSpecialAttack();
            }
        }

        // A missed / splashed attack is still a full-log entry (amount 0). Magic misses read
        // "Splashed", everything else "Missed".
        String what = (weaponName != null && !weaponName.isEmpty()) ? weaponName : "attack";
        String verb = style == CombatStyle.MAGIC ? "Splashed" : "Missed";
        String desc = verb + " (" + what + ")";
        CombatTimelineEvent ev = CombatTimelineEvent.builder()
            .clientTick(tick).timeFormatted(ledgerTimeStr()).eventType("HIT").icon(resolveStyleIcon(style))
            .description(desc).color(new Color(150, 150, 160)).amount(0)
            .source("You")
            .target((enemyName != null && !enemyName.isEmpty()) ? enemyName
                : (currentEncounter != null ? currentEncounter.getTargetName() : null))
            .style(style).weaponOrSpell(weaponName).miss(true).special(isSpecial)
            .build();
        if (currentEncounter != null) currentEncounter.getLocalPlayerStats().addTimelineEvent(ev);
        currentSessionEncounter.getLocalPlayerStats().addTimelineEvent(ev);
        overallSessionEncounter.getLocalPlayerStats().addTimelineEvent(ev);

        // Standard Bind (and a 0-roll Snare / Entangle) lands for 0 damage and arrives here, not
        // via recordPlayerDamageDealt. If the resolved attack is a known CC spell, still log the
        // debuff so it shows in the full log. A genuine magic splash can look identical at this
        // point; the CC_DURATIONS name gate keeps false positives rare (proper splash / freeze-
        // immunity detection needs target getGraphic() reads - tracked as the CC follow-up).
        if (currentEncounter != null)
        {
            maybeRecordDebuff(weaponName, enemyName, tick);
        }

        notifyListeners();
    }

    /**
     * A damage-over-time tick (poison / venom / burn / bleed) on an NPC. Only counted when it lands
     * on the encounter's actual target, and it never counts toward hit-accuracy (it isn't an attack).
     */
    public synchronized void recordPlayerStatusDamage(Actor source, CombatStyle style, int amount, String sourceName, int tick)
    {
        // TODO(combat-accuracy): known limitation, in progress. Poison / venom / burn is credited
        // only while the victim is the current activeTargetActor, so a damage-over-time tick on a
        // multi-pull add, a switched-off target, or after the mob dies is dropped. Planned fix:
        // track which weapon/helm applied the stack and credit the whole stack to the applier.
        if (amount <= 0 || activeTargetActor == null || source != activeTargetActor)
        {
            return;
        }
        touchCombatTick(tick);

        String label = (sourceName != null && !sourceName.isEmpty())
            ? sourceName : (style != null ? style.getDisplayName() : "Status");
        String desc = label + " " + amount;
        Color color = (style != null && style.getPrimaryColor() != null)
            ? style.getPrimaryColor() : CombatMeterColors.TEXT_ACCENT_GOLD;
        String timeStr = ledgerTimeStr();
        String dotTarget = currentEncounter != null ? currentEncounter.getTargetName() : null;
        CombatTimelineEvent event = CombatTimelineEvent.builder()
            .clientTick(tick).timeFormatted(timeStr).eventType("DOT").icon(resolveStyleIcon(style))
            .description(desc).color(color).amount(amount)
            .source("You").target(dotTarget).style(style).weaponOrSpell(label)
            .build();

        for (EncounterSegment seg : new EncounterSegment[]{ currentEncounter, currentSessionEncounter, overallSessionEncounter })
        {
            if (seg == null)
            {
                continue;
            }
            EntityCombatStats s = seg.getLocalPlayerStats();
            s.recordStatusDamage(style, amount, label, tick, dotTarget);
            s.recordTimeSeriesSecond((int) seg.getDurationSeconds(), amount, 0, 0, style, desc);
            s.addTimelineEvent(event);
        }
        if (currentEncounter != null && dotTarget != null)
        {
            currentEncounter.recordDamageToEnemy(dotTarget, style, amount, label, tick);
        }
        notifyListeners();
    }

    // TODO(combat-accuracy): known limitation, in progress. Reached only from the currently-dead
    // DamageAttributionEngine thrall branch, so thrall damage is not attributed yet. Planned fix
    // tracks the thrall NPC directly and reconciles self-damage against the Hitpoints XP drop;
    // thrall (and cannon) damage will then fold into the local player's stats as a source row
    // unless combatSplitOwnedSources is set.
    public synchronized void recordThrallDamage(CombatStyle style, int amount, String thrallName, int tick)
    {
        if (currentEncounter != null)
        {
            currentEncounter.getThrallStats().recordDamageDealt(style, amount, thrallName, tick);
            if (amount > 0 && currentEncounter.getTargetName() != null)
            {
                currentEncounter.recordDamageToEnemy(currentEncounter.getTargetName(), style, amount, "Thrall", tick);
            }
        }
        currentSessionEncounter.getThrallStats().recordDamageDealt(style, amount, thrallName, tick);
        overallSessionEncounter.getThrallStats().recordDamageDealt(style, amount, thrallName, tick);
        notifyListeners();
    }

    public synchronized void recordCannonDamage(int amount, int tick)
    {
        if (currentEncounter != null)
        {
            currentEncounter.getCannonStats().recordDamageDealt(CombatStyle.CANNON, amount, "Dwarf Multicannon", tick);
            if (amount > 0 && currentEncounter.getTargetName() != null)
            {
                currentEncounter.recordDamageToEnemy(currentEncounter.getTargetName(), CombatStyle.CANNON, amount, "Cannon", tick);
            }
        }
        currentSessionEncounter.getCannonStats().recordDamageDealt(CombatStyle.CANNON, amount, "Dwarf Multicannon", tick);
        overallSessionEncounter.getCannonStats().recordDamageDealt(CombatStyle.CANNON, amount, "Dwarf Multicannon", tick);
        notifyListeners();
    }

    public synchronized void recordPlayerDamageTaken(CombatStyle style, int amount, String sourceName, int tick)
    {
        String note = amount > 0 ? "Took " + amount : null;
        if (currentEncounter != null)
        {
            currentEncounter.getLocalPlayerStats().recordDamageTaken(style, amount, sourceName, tick);
            currentEncounter.getLocalPlayerStats().recordTimeSeriesSecond((int) currentEncounter.getDurationSeconds(), 0, amount, 0, note);
        }
        currentSessionEncounter.getLocalPlayerStats().recordDamageTaken(style, amount, sourceName, tick);
        currentSessionEncounter.getLocalPlayerStats().recordTimeSeriesSecond((int) currentSessionEncounter.getDurationSeconds(), 0, amount, 0, note);

        overallSessionEncounter.getLocalPlayerStats().recordDamageTaken(style, amount, sourceName, tick);
        overallSessionEncounter.getLocalPlayerStats().recordTimeSeriesSecond((int) overallSessionEncounter.getDurationSeconds(), 0, amount, 0, note);

        // Every incoming hit is logged for a *complete* Full Log - including blocked / 0-damage
        // ones. The HUD-adjacent action ledger filters the 0s back out so it isn't spammed
        // (see CombatEncounterTabView#updateTimelineLedger).
        String timeStr = ledgerTimeStr();
        String src = (sourceName != null && !sourceName.isEmpty()) ? sourceName
            : (style != null ? style.getDisplayName() : "Enemy");
        CombatTimelineEvent event = CombatTimelineEvent.builder()
            .clientTick(tick).timeFormatted(timeStr).eventType("TAKEN").icon("v")
            .description(amount > 0 ? "Took " + amount + " (" + src + ")" : "Blocked hit (" + src + ")")
            .color(amount > 0 ? new Color(239, 83, 80) : new Color(120, 130, 150))
            .amount(Math.max(0, amount))
            .source(src).target("You").style(style).miss(amount <= 0)
            .build();
        if (currentEncounter != null) currentEncounter.getLocalPlayerStats().addTimelineEvent(event);
        currentSessionEncounter.getLocalPlayerStats().addTimelineEvent(event);
        overallSessionEncounter.getLocalPlayerStats().addTimelineEvent(event);

        notifyListeners();
    }

    // TODO(combat-accuracy): known limitation, in progress. Healing can be double-counted - a HEAL
    // hitsplat lands here AND the resulting StatChanged(HITPOINTS) is added again by
    // ConsumableAuditor.onStatChanged; passive regen also counts as "healing done". Planned fix:
    // one source of truth (the HP-points delta) classified by tick context (lifesteal / food /
    // brew / regen / redemption).
    public synchronized void recordHpHealed(int amount)
    {
        if (currentEncounter != null)
        {
            EntityCombatStats stats = currentEncounter.getLocalPlayerStats();
            stats.setHpHealed(stats.getHpHealed() + amount);
            stats.recordTimeSeriesSecond((int) currentEncounter.getDurationSeconds(), 0, 0, amount, "Healed " + amount);
        }
        EntityCombatStats csStats = currentSessionEncounter.getLocalPlayerStats();
        csStats.setHpHealed(csStats.getHpHealed() + amount);
        csStats.recordTimeSeriesSecond((int) currentSessionEncounter.getDurationSeconds(), 0, 0, amount, "Healed " + amount);

        EntityCombatStats sStats = overallSessionEncounter.getLocalPlayerStats();
        sStats.setHpHealed(sStats.getHpHealed() + amount);
        sStats.recordTimeSeriesSecond((int) overallSessionEncounter.getDurationSeconds(), 0, 0, amount, "Healed " + amount);

        String timeStr = ledgerTimeStr();
        CombatTimelineEvent event = CombatTimelineEvent.builder()
            .clientTick(getCurrentTick()).timeFormatted(timeStr).eventType("HEAL").icon("^")
            .description("Healed " + amount + " HP").color(new Color(129, 199, 132)).amount(amount)
            .source("You").target("You")
            .build();

        if (currentEncounter != null) currentEncounter.getLocalPlayerStats().addTimelineEvent(event);
        currentSessionEncounter.getLocalPlayerStats().addTimelineEvent(event);
        overallSessionEncounter.getLocalPlayerStats().addTimelineEvent(event);

        notifyListeners();
    }


    private String formatDuration(double durationSeconds)
    {
        return com.osrscopilot.combat.CombatFormat.duration(durationSeconds);
    }

    /** One-line shareable summary of the current scope, for the "Copy summary" buttons. */
    public synchronized String buildShareableSummary()
    {
        EncounterSegment scope = getSelectedOrCurrentEncounter();
        if (scope == null)
        {
            return "[Combat Meter] no fight data yet";
        }
        EntityCombatStats s = scope.getLocalPlayerStats();
        return String.format("[Combat Meter] %s -> %.1f DPS | %s dmg | %s taken | heals %s | cost %s gp",
            scope.getTargetName(), s.getDps(),
            shortNum(s.getTotalDamage()), shortNum(s.getDamageTaken()),
            shortNum(s.getHpHealed()), shortNum(s.getTotalGpCost()));
    }

    private com.osrscopilot.combat.CombatChatShare chatShare;

    /** Wired by the plugin so the HUD / ledger "Share" buttons can reach the clipboard + chat box. */
    public void setChatShare(com.osrscopilot.combat.CombatChatShare chatShare)
    {
        this.chatShare = chatShare;
    }

    /**
     * A compact one-line summary that fits a single OSRS chat message (~80 chars). Names the scope
     * (target / "Session" / "Total" / "Gargoyle x47"), then DPS, damage dealt / taken and time.
     */
    public synchronized String buildShareLine()
    {
        EncounterSegment scope = getSelectedOrCurrentEncounter();
        if (scope == null || scope.getLocalPlayerStats().getTotalDamage() <= 1)
        {
            return "Combat: no fight data yet";
        }
        EntityCombatStats s = scope.getLocalPlayerStats();
        String who;
        if (scope.getSegmentType() == SegmentType.SESSION_CURRENT)
        {
            who = "Session";
        }
        else if (scope.getSegmentType() == SegmentType.SESSION_TOTAL)
        {
            who = "Total";
        }
        else if (scope.isMerged())
        {
            who = scope.getTargetName() + " x" + scope.getMergedKills();
        }
        else
        {
            who = scope.getTargetName();
        }
        if (who != null && who.length() > 24)
        {
            who = who.substring(0, 22) + "..";
        }
        return String.format("%s | DPS %s | dealt %s | taken %s | %s",
            who, shortNum(Math.round(s.getDps())), shortNum(s.getTotalDamage()),
            shortNum(s.getDamageTaken()), formatDuration(scope.getDurationSeconds()));
    }

    /** Copy the compact summary to the clipboard (retried past a clipboard lock, with game-chat feedback). */
    public void shareCopy()
    {
        if (chatShare != null)
        {
            chatShare.copy(buildShareLine());
        }
    }

    /** Copy the compact summary with the OSRS channel prefix already on it; the player pastes + sends. */
    public void shareToChat(com.osrscopilot.combat.ShareChannel channel)
    {
        if (chatShare != null)
        {
            chatShare.copyForChannel(channel, buildShareLine());
        }
    }

    private static String shortNum(long v)
    {
        return com.osrscopilot.combat.CombatFormat.amount(v);
    }

    /** All action-ledger rows use one consistent time base: fight time if a fight is live, else session. */
    private String ledgerTimeStr()
    {
        return formatDuration(currentEncounter != null
            ? currentEncounter.getDurationSeconds()
            : overallSessionEncounter.getDurationSeconds());
    }

    private void startNewSegment(String name, SegmentType type, int startTick, Actor target)
    {
        this.activeTargetActor = target;
        this.lastPlayerAttackTick = Integer.MIN_VALUE;
        this.lastPlayerAttackWeapon = null;
        this.lastAccruedCombatTick = startTick;
        this.engagedActors.clear();
        this.weaponsAttackedThisTick.clear();
        this.weaponsLandedThisTick.clear();
        this.pendingBuff.clear();
        this.pendingBuffSince.clear();
        this.lastLoggedBuffState.clear();
        String playerName = (client != null && client.getLocalPlayer() != null) ? client.getLocalPlayer().getName() : "You";
        // An encounter is never named after the player.
        if (name == null || name.isEmpty() || name.equals(playerName))
        {
            name = "Combat Encounter";
        }
        this.currentEncounter = new EncounterSegment(UUID.randomUUID(), name, type, startTick, playerName);
        currentEncounter.getLocalPlayerStats().setRollingWindowSeconds(graphSmoothingSeconds);
        if (target instanceof NPC)
        {
            NPC npc = (NPC) target;
            currentEncounter.setNpcInfo(npc.getId(), npc.getCombatLevel());
        }
        if (autoFollow)
        {
            this.selectedEncounter = null; // snap the view to the new fight
        }

        if (buffTracker != null) buffTracker.startEncounter(startTick);

        CombatTimelineEvent startEvent = CombatTimelineEvent.builder()
            .clientTick(startTick).timeFormatted("00:00").eventType("START").icon(">")
            .description("Engaged " + name).color(CombatMeterColors.TEXT_ACCENT_GOLD)
            .source("You").target(name)
            .build();
        currentEncounter.getLocalPlayerStats().addTimelineEvent(startEvent);
        currentSessionEncounter.getLocalPlayerStats().addTimelineEvent(startEvent);
        overallSessionEncounter.getLocalPlayerStats().addTimelineEvent(startEvent);

        // Record what was already up when the fight began (prayers, overloads, boosts, ...) - the
        // "pre-pull" state, MMO-raid-style. Also seeds lastLoggedBuffState so a mid-fight toggle of one
        // of these logs an "ended".
        if (buffTracker != null)
        {
            java.util.List<String> prePull = buffTracker.getActiveBuffNames();
            for (String n : prePull)
            {
                lastLoggedBuffState.put(n, true);
            }
            if (!prePull.isEmpty())
            {
                currentEncounter.getLocalPlayerStats().addTimelineEvent(CombatTimelineEvent.builder()
                    .clientTick(startTick).timeFormatted("00:00").eventType("BUFF").icon("~")
                    .description("Engaged with: " + String.join(", ", prePull))
                    .color(new Color(150, 180, 210)).source("You").target("You")
                    .build());
            }
        }
    }

    /** The last ~10 incoming hits (TAKEN / incoming DOT) before a death - "what killed you". */
    private static java.util.List<CombatTimelineEvent> captureDeathRecap(EncounterSegment enc)
    {
        java.util.List<CombatTimelineEvent> all = enc.getLocalPlayerStats().getTimelineEvents();
        java.util.List<CombatTimelineEvent> incoming = new java.util.ArrayList<>();
        for (CombatTimelineEvent ev : all)
        {
            String t = ev.getEventType();
            if ("TAKEN".equals(t) || ("DOT".equals(t) && "You".equals(ev.getTargetName())))
            {
                incoming.add(ev);
            }
        }
        int from = Math.max(0, incoming.size() - 10);
        return new java.util.ArrayList<>(incoming.subList(from, incoming.size()));
    }

    public synchronized void finalizeCurrentSegment(SegmentStatus finalStatus, int endTick)
    {
        finalizeCurrentSegment(finalStatus, endTick, false);
    }

    /**
     * @param clampToCombat when true, stamp the end time from {@code endTick} rather than "now" so a
     *   fight finalized late (logout / world-hop, processed on relogin) doesn't record a duration
     *   spanning the whole offline period.
     */
    public synchronized void finalizeCurrentSegment(SegmentStatus finalStatus, int endTick, boolean clampToCombat)
    {
        if (currentEncounter == null) return;

        if (buffTracker != null) buffTracker.stopEncounter(endTick);

        currentEncounter.setStatus(finalStatus);
        currentEncounter.setEndTick(endTick);
        if (clampToCombat && currentEncounter.getStartTimestamp() != null)
        {
            long combatMillis = Math.max(0L, (long) ((endTick - currentEncounter.getStartTick()) * 600L));
            Instant clamped = currentEncounter.getStartTimestamp().plusMillis(combatMillis);
            currentEncounter.setEndTimestamp(clamped.isBefore(Instant.now()) ? clamped : Instant.now());
        }
        else
        {
            currentEncounter.setEndTimestamp(Instant.now());
        }
        currentEncounter.updateDuration(endTick);

        if (finalStatus == SegmentStatus.WIPED)
        {
            currentEncounter.setDeathRecap(captureDeathRecap(currentEncounter));
        }

        boolean win = finalStatus == SegmentStatus.COMPLETED;
        String durStr = formatDuration(currentEncounter.getDurationSeconds());
        String tName = isPlaceholderName(currentEncounter.getTargetName()) ? "target" : currentEncounter.getTargetName();
        String desc;
        if (finalStatus == SegmentStatus.COMPLETED)      desc = "Defeated " + tName + " (" + durStr + ")";
        else if (finalStatus == SegmentStatus.WIPED)     desc = "Wiped to " + tName + " (" + durStr + ")";
        else                                             desc = "Left combat with " + tName + " (" + durStr + ")";
        CombatTimelineEvent endEvent = CombatTimelineEvent.builder()
            .clientTick(endTick).timeFormatted(durStr).eventType("END").icon(win ? "*" : "x")
            .description(desc).color(win ? new Color(129, 199, 132) : new Color(239, 83, 80))
            .source("You").target(tName)
            .build();
        currentEncounter.getLocalPlayerStats().addTimelineEvent(endEvent);
        currentSessionEncounter.getLocalPlayerStats().addTimelineEvent(endEvent);
        overallSessionEncounter.getLocalPlayerStats().addTimelineEvent(endEvent);

        // Don't clutter the history list - or the "last fight" scope row - with sub-5-second
        // nothing-fights (a stray aggro, a single splash). Wipes and anything with real damage
        // are always kept.
        boolean trivial = finalStatus == SegmentStatus.COMPLETED
            && currentEncounter.getDurationSeconds() < 5
            && currentEncounter.getLocalPlayerStats().getTotalDamage() < 50;
        if (!trivial)
        {
            // Seed this fight's own combat time from its wall-clock span (a single uninterrupted
            // fight is ~all combat) so a merged "Gargoyle xN" row can sum true fighting time and
            // not the banking pauses between kills.
            if (currentEncounter.getCombatSeconds() <= 0)
            {
                currentEncounter.addCombatSeconds(currentEncounter.getDurationSeconds());
            }

            EncounterSegment mergeInto = (mergeTrash && finalStatus == SegmentStatus.COMPLETED)
                ? findTrashMergeTarget(currentEncounter) : null;
            if (mergeInto != null)
            {
                mergeInto.absorb(currentEncounter);
                mergeInto.updateDuration(mergeInto.getEndTick());
                lastEncounter = mergeInto;
            }
            else
            {
                currentEncounter.setHistoryIndex(nextHistoryIndex++);
                lastEncounter = currentEncounter;
                historicalEncounters.add(currentEncounter);
                while (historicalEncounters.size() > segmentHistoryDepth)
                {
                    historicalEncounters.remove(0); // Evict oldest — its damage stays in Session / Total
                }
            }
        }

        currentEncounter = null;
        activeTargetActor = null;
        engagedActors.clear();
        lastAccruedCombatTick = 0;
        // Each fight gets fresh multi-target tags ("(1)/(2)" restart) and no carried-over ghosts.
        if (multiTargetTracker != null) multiTargetTracker.reset();
        persistTotal(); // save the all-time bucket after every fight
        notifyListeners();
    }

    /**
     * The most recent history row this just-finished kill should fold into (trash merge),
     * or null to file it as a new row. Only merges into the newest entry, only for the same NPC
     * name, only a real kill (never into a wipe / disengage), and only within {@link #MERGE_WINDOW_MS}
     * so a fresh session doesn't glue onto yesterday's grind.
     */
    private EncounterSegment findTrashMergeTarget(EncounterSegment fresh)
    {
        if (historicalEncounters.isEmpty() || fresh.getTargetName() == null)
        {
            return null;
        }
        EncounterSegment last = historicalEncounters.get(historicalEncounters.size() - 1);
        if (last.getSegmentType() != SegmentType.ENCOUNTER
            || last.getStatus() == SegmentStatus.WIPED
            || last.getStatus() == SegmentStatus.ABANDONED
            || isPlaceholderName(last.getTargetName())
            || !fresh.getTargetName().equalsIgnoreCase(last.getTargetName()))
        {
            return null;
        }
        if (last.getEndTimestamp() != null && fresh.getStartTimestamp() != null
            && fresh.getStartTimestamp().toEpochMilli() - last.getEndTimestamp().toEpochMilli() > MERGE_WINDOW_MS)
        {
            return null;
        }
        return last;
    }

    public synchronized void resetCurrentSession(int currentTick)
    {
        clearSegmentStats(currentSessionEncounter);
        currentSessionEncounter.resetCombatSeconds();
        currentSessionEncounter.setStartTick(currentTick);
        currentSessionEncounter.setStartTimestamp(Instant.now());
        currentSessionEncounter.setEndTimestamp(null);
        lastSessionCombatTick = currentTick; // restart the idle timer from now
        notifyListeners();
    }

    /** Clears only the Total scope, leaving the fight history intact (the all-time bucket only). */
    /** "Reset Total" — wipes the persisted all-time bucket (and its saved config). */
    public synchronized void resetTotal()
    {
        if (demoSegment != null)
        {
            return;
        }
        clearSegmentStats(overallSessionEncounter);
        overallSessionEncounter.resetCombatSeconds();
        overallSessionEncounter.setStartTimestamp(Instant.now());
        overallSessionEncounter.setEndTimestamp(null);
        if (configManager != null)
        {
            configManager.unsetConfiguration("osrscopilot", TOTAL_KEY);
        }
        notifyListeners();
    }

    // ---- "Total" scope persistence (survives logout) -------------------------------------------

    public void setPersistence(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    /** Serialise the all-time bucket's headline stats to config. Cheap; safe to call per fight end. */
    public synchronized void persistTotal()
    {
        if (configManager == null)
        {
            return;
        }
        EntityCombatStats s = overallSessionEncounter.getLocalPlayerStats();
        String blob = "v1"
            + "|" + s.getTotalDamage()
            + "|" + s.getDamageTaken()
            + "|" + s.getHpHealed()
            + "|" + s.getHpOverhealed()
            + "|" + s.getPotionsDrunkCount()
            + "|" + s.getFoodEatenCount()
            + "|" + s.getAttackAttempts()
            + "|" + s.getSuccessfulHits()
            + "|" + s.getTotalGpCost()
            + "|" + (long) overallSessionEncounter.getCombatSeconds()
            + "|" + styleMapToString(s.getDamageByStyle())
            + "|" + styleMapToString(s.getDamageTakenByStyleRaw());
        configManager.setConfiguration("osrscopilot", TOTAL_KEY, blob);
    }

    private boolean totalRestored = false;

    /** Restore the all-time bucket from config on startup. No-op if nothing saved / bad format. */
    public synchronized void restoreTotal()
    {
        if (configManager == null || totalRestored)
        {
            return; // applyPersistedTotals is additive - never let it run twice (D4)
        }
        totalRestored = true;
        String blob = configManager.getConfiguration("osrscopilot", TOTAL_KEY);
        if (blob == null || !blob.startsWith("v1|"))
        {
            return;
        }
        try
        {
            String[] p = blob.split("\\|", -1);
            EntityCombatStats s = overallSessionEncounter.getLocalPlayerStats();
            s.reset();
            overallSessionEncounter.resetCombatSeconds();
            s.applyPersistedTotals(
                Long.parseLong(p[1]), Long.parseLong(p[2]),
                Integer.parseInt(p[3]), Integer.parseInt(p[4]),
                Integer.parseInt(p[5]), Integer.parseInt(p[6]),
                Integer.parseInt(p[7]), Integer.parseInt(p[8]),
                Long.parseLong(p[9]),
                styleMapFromString(p[11]), styleMapFromString(p[12]));
            overallSessionEncounter.addCombatSeconds(Double.parseDouble(p[10]));
        }
        catch (Exception ignored)
        {
            // corrupt blob — leave Total empty
        }
    }

    /** Finalize an in-flight fight without spanning the offline period. Used on logout / world-hop. */
    private void finalizeInFlightFight()
    {
        if (currentEncounter != null)
        {
            int endTick = Math.max(currentEncounter.getStartTick(), lastCombatTick);
            finalizeCurrentSegment(SegmentStatus.ABANDONED, endTick, true);
        }
        activeTargetActor = null;
        weaponsAttackedThisTick.clear();
        weaponsLandedThisTick.clear();
    }

    /** Real logout: close the fight, roll "Current Session" over, flush "Total" to disk. */
    public synchronized void onLogout()
    {
        finalizeInFlightFight();
        clearSegmentStats(currentSessionEncounter);
        currentSessionEncounter.resetCombatSeconds();
        currentSessionEncounter.setStartTimestamp(Instant.now());
        currentSessionEncounter.setEndTimestamp(null);
        lastSessionCombatTick = 0;
        persistTotal();
        notifyListeners();
    }

    /** World-hop: close the in-flight fight (so it doesn't merge with the post-hop one) but keep
     *  the Current Session / Total scopes running - a hop isn't a logout. */
    public synchronized void onWorldHop()
    {
        finalizeInFlightFight();
        notifyListeners();
    }

    /** A real login (not a world-hop): anchor the "Current Session" wall-clock to now. */
    public synchronized void onLogin()
    {
        currentSessionEncounter.setStartTimestamp(Instant.now());
        currentSessionEncounter.setEndTimestamp(null);
        notifyListeners();
    }

    private static String styleMapToString(Map<CombatStyle, Long> m)
    {
        if (m == null || m.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (Map.Entry<CombatStyle, Long> e : m.entrySet())
        {
            if (b.length() > 0) b.append(';');
            b.append(e.getKey().name()).append('=').append(e.getValue());
        }
        return b.toString();
    }

    private static Map<CombatStyle, Long> styleMapFromString(String s)
    {
        Map<CombatStyle, Long> m = new EnumMap<>(CombatStyle.class);
        if (s == null || s.isEmpty()) return m;
        for (String pair : s.split(";"))
        {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try
            {
                m.put(CombatStyle.valueOf(pair.substring(0, eq)), Long.parseLong(pair.substring(eq + 1)));
            }
            catch (IllegalArgumentException e)
            {
                log.debug("Skipping unparseable persisted style entry '{}'", pair, e);
            }
        }
        return m;
    }

    public synchronized void resetCurrentEncounter()
    {
        if (demoSegment != null)
        {
            return;
        }
        if (currentEncounter != null)
        {
            clearSegmentStats(currentEncounter);
            currentEncounter.resetCombatSeconds();
            currentEncounter.setStartTick(getCurrentTick());
            currentEncounter.setStartTimestamp(Instant.now());
            currentEncounter.setEndTimestamp(null);
            lastAccruedCombatTick = getCurrentTick();
            // Rebase buff/aura uptime to now, or every buff still running reads 100% for a long
            // time (its active ticks are measured from the original fight start, the denominator
            // just dropped to ~1).
            if (buffTracker != null) buffTracker.startEncounter(getCurrentTick());
        }
        weaponsAttackedThisTick.clear();
        weaponsLandedThisTick.clear();
        pendingBuff.clear();
        pendingBuffSince.clear();
        lastLoggedBuffState.clear();
        lastPlayerAttackTick = Integer.MIN_VALUE;
        if (multiTargetTracker != null) multiTargetTracker.reset();
        notifyListeners();
    }

    /** "Reset All" — wipes history, both session scopes, and the last-fight pointer. */
    /** "Reset Session" — clears Current Session + the fight-history list. Total is left intact. */
    public synchronized void resetSessionAndHistory()
    {
        if (demoSegment != null)
        {
            return;
        }
        historicalEncounters.clear();
        nextHistoryIndex = 1;
        selectedEncounter = null;
        autoFollow = true;
        lastEncounter = null;
        if (multiTargetTracker != null) multiTargetTracker.reset();
        if (currentEncounter == null && buffTracker != null) buffTracker.reset();
        clearSegmentStats(currentSessionEncounter);
        currentSessionEncounter.resetCombatSeconds();
        currentSessionEncounter.setStartTimestamp(Instant.now());
        currentSessionEncounter.setEndTimestamp(null);
        lastSessionCombatTick = getCurrentTick(); // restart the session idle timer from now (D7)
        notifyListeners();
    }

    private void clearSegmentStats(EncounterSegment segment)
    {
        if (segment == null) return;
        segment.clearEnemyDamage();
        segment.clearDebuffs();
        if (segment.getLocalPlayerStats() != null)
        {
            segment.getLocalPlayerStats().reset();
        }
        if (segment.getThrallStats() != null)
        {
            segment.getThrallStats().reset();
        }
        if (segment.getCannonStats() != null)
        {
            segment.getCannonStats().reset();
        }
    }



    private EncounterSegment createSessionSegment(String title, SegmentType type)
    {
        String playerName = (client != null && client.getLocalPlayer() != null) ? client.getLocalPlayer().getName() : "You";
        EncounterSegment seg = new EncounterSegment(UUID.randomUUID(), title, type, 0, playerName);
        seg.setStatus(SegmentStatus.IN_PROGRESS);
        // The session / total scopes run for hours - keep their action ledgers shorter than a
        // single fight's, but still deep enough to be a usable log (was 50 / 100 = the last
        // minute or two of a whole session).
        seg.getLocalPlayerStats().setMaxTimelineEvents(type == SegmentType.SESSION_TOTAL ? 1000 : 2000);
        return seg;
    }
}

