package com.osrscopilot.combat.party;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.model.CombatStyle;
import com.osrscopilot.combat.model.EncounterSegment;
import com.osrscopilot.combat.model.EntityCombatStats;
import com.osrscopilot.combat.model.SegmentType;
import com.osrscopilot.combat.model.WeaponAbilityEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.party.PartyPlugin;

/**
 * The "GROUP" combat scope: a shared damage meter for a party who all run OmniHUD.
 *
 * <p>OSRS gives a client no attribution for other players' hits, so this can't be reconstructed
 * from one client - instead every client is the sole authority on its own {@code isMine()} stats
 * and broadcasts a compact idempotent {@link RemoteCombatSnapshot} through RuneLite's
 * {@link PartyService} (E2E encrypted by the party passphrase). Each client renders the group
 * meter from [its own live stats] + [everyone else's latest snapshot].
 */
@Singleton
@Slf4j
public class CombatPartyService
{
    private static final long STALE_MS = 30_000;
    private static final int SEND_EVERY_TICKS = 5; // ~3 s
    static final int MAX_WEAPONS = 4;
    static final String VERSION = "0.1";

    // A teammate this many tiles away or closer, fighting, counts as "in my fight" even before I
    // have thrown a hit - close enough that their pull is the start of a fight I'm about to join.
    static final int GROUP_PROXIMITY_TILES = 50;

    // A fixed id so the "Group" scope row keeps its identity across dropdown refreshes - the view
    // compares rows by reference, and the segment is mutated in place each tick, never replaced.
    private static final UUID GROUP_SCOPE_ID = new UUID(0x0057_C0_11_6E_00_0001L, 0x6E_75_70_6E_75_70_6E_75L);

    private final PartyService party;
    private final WSClient wsClient;
    private final Client client;
    private final OsrsCopilotConfig config;
    private final CombatEncounterManager encounters;
    private final PluginManager pluginManager;

    private final Map<Long, RemoteCombatSnapshot> remote = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastSeenMs = new ConcurrentHashMap<>();

    private volatile String myShared = "";
    private volatile String myBoss = "";
    // My last known world tile, sampled on the game tick so the read-side proximity checks (which
    // also run on the Swing EDT) never touch the client. 0/0 until the first tick in the world.
    private volatile int myX;
    private volatile int myY;
    private volatile int myPlane;
    // Tick cursors for the per-N-ticks throttles. NOT seeded to Integer.MIN_VALUE - client tick
    // counts are small positive ints, so (tick - MIN_VALUE) overflows negative and the ">= N"
    // gate would never fire. Flags below make the first call always pass.
    private int lastSendTick;
    private int lastIdleLogTick;
    private boolean sentOnce;
    private boolean idleLoggedOnce;
    private boolean registered;

    // The single, reference-stable "Group" scope segment. Mutated in place by rebuildGroupScope().
    private final EncounterSegment groupScope =
        new EncounterSegment(GROUP_SCOPE_ID, "Group", SegmentType.GROUP, 0, "You");

    @Inject
    public CombatPartyService(PartyService party, WSClient wsClient, Client client,
        OsrsCopilotConfig config, CombatEncounterManager encounters, PluginManager pluginManager)
    {
        this.party = party;
        this.wsClient = wsClient;
        this.client = client;
        this.config = config;
        this.encounters = encounters;
        this.pluginManager = pluginManager;
    }

    /** Test seam - no RuneLite services. */
    CombatPartyService(CombatEncounterManager encounters)
    {
        this(null, null, null, null, encounters, null);
    }

    // ---------------------------------------------------------------- lifecycle

    public void register()
    {
        if (!registered && wsClient != null)
        {
            wsClient.registerMessage(RemoteCombatSnapshot.class);
            registered = true;
        }
    }

    public void unregister()
    {
        if (registered && wsClient != null)
        {
            wsClient.unregisterMessage(RemoteCombatSnapshot.class);
        }
        registered = false;
        remote.clear();
        lastSeenMs.clear();
        myShared = "";
        myBoss = "";
    }

    // ---------------------------------------------------------------- inbound

    /** Called by the plugin's {@code @Subscribe} for {@link RemoteCombatSnapshot}. */
    public void ingest(RemoteCombatSnapshot m)
    {
        if (m == null)
        {
            return;
        }
        if (m.getMemberId() == localMemberId())
        {
            return; // ignore our own echo
        }
        remote.put(m.getMemberId(), m);
        lastSeenMs.put(m.getMemberId(), System.currentTimeMillis());

        // Simultaneous-start race: if we both minted an id for the same boss, the lower id wins.
        if (!myShared.isEmpty() && !m.getSharedId().isEmpty()
            && sameBoss(m.getBoss(), myBoss) && m.getSharedId().compareTo(myShared) < 0)
        {
            myShared = m.getSharedId();
        }
    }

    /** Called by the plugin's {@code @Subscribe} for party {@code UserPart}. */
    public void onMemberLeft(long memberId)
    {
        remote.remove(memberId);
        lastSeenMs.remove(memberId);
    }

    // ---------------------------------------------------------------- per-tick

    public void onGameTick(int tick)
    {
        if (!enabledAndInParty())
        {
            if (!myShared.isEmpty())
            {
                myShared = "";
                myBoss = "";
            }
            if (!remote.isEmpty())
            {
                remote.clear();
                lastSeenMs.clear();
            }
            // One line every ~30s so a "no group members" report has a cause in the log instead
            // of silence: which of the two gates is closed.
            if (!idleLoggedOnce || tick < lastIdleLogTick || tick - lastIdleLogTick >= 50)
            {
                idleLoggedOnce = true;
                lastIdleLogTick = tick;
                log.debug("group meter idle - not sharing: combatPartyEnabled={} inParty={} partyPlugin={}",
                    partyEnabled(), inParty(), runelitePartyAvailable());
            }
            return;
        }
        evictStale();
        cacheMyLocation();
        refreshMyShared();
        // First tick after enable always sends; then every SEND_EVERY_TICKS; and re-sends if the
        // tick counter went backwards (world hop / relog).
        if (!sentOnce || tick < lastSendTick || tick - lastSendTick >= SEND_EVERY_TICKS)
        {
            sentOnce = true;
            lastSendTick = tick;
            sendSnapshot();
        }
    }

    private void evictStale()
    {
        long now = System.currentTimeMillis();
        lastSeenMs.entrySet().removeIf(e ->
        {
            if (now - e.getValue() > STALE_MS)
            {
                remote.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    private void refreshMyShared()
    {
        EncounterSegment live = encounters.getCurrentEncounter();
        boolean fighting = live != null && live.isInCombat();
        if (fighting)
        {
            myBoss = live.getTargetName() == null ? "" : live.getTargetName();
            if (myShared.isEmpty())
            {
                // Adopt an in-flight shared id for the same boss, else become the anchor.
                for (RemoteCombatSnapshot s : remote.values())
                {
                    if (!s.getSharedId().isEmpty() && s.isInCombat() && sameBoss(s.getBoss(), myBoss))
                    {
                        myShared = s.getSharedId();
                        break;
                    }
                }
                if (myShared.isEmpty())
                {
                    myShared = UUID.randomUUID().toString();
                }
            }
            return;
        }
        // Not swinging myself: stand in on a nearby teammate's fight so the combined meter shows
        // their damage against a target I haven't touched yet, and the "Entering Combat" banner
        // fires with them. Drops as soon as nobody within GROUP_PROXIMITY_TILES is still fighting.
        RemoteCombatSnapshot near = nearbyRemoteFight();
        if (near != null)
        {
            myBoss = near.getBoss() == null ? "" : near.getBoss();
            if (!near.getSharedId().isEmpty())
            {
                myShared = near.getSharedId();
            }
            else if (myShared.isEmpty())
            {
                myShared = UUID.randomUUID().toString();
            }
        }
        else
        {
            myShared = "";
            myBoss = "";
        }
    }

    private void sendSnapshot()
    {
        if (party == null)
        {
            return;
        }
        RemoteCombatSnapshot s = buildLocalSnapshot();
        try
        {
            party.send(s);
        }
        catch (RuntimeException e)
        {
            // party not ready / rate limited - the next tick retries. Logged so a persistent
            // failure (unregistered message type, null local member, serialization) is visible.
            log.debug("party combat snapshot send failed", e);
        }
    }

    RemoteCombatSnapshot buildLocalSnapshot()
    {
        RemoteCombatSnapshot s = new RemoteCombatSnapshot();
        s.setSharedId(myShared);
        s.setBoss(myBoss);
        s.setVer(VERSION);
        s.setRsn(localName());
        s.setWorldX(myX);
        s.setWorldY(myY);
        s.setWorldPlane(myPlane);

        EncounterSegment scope = encounters.getEncounterScope();
        EncounterSegment live = encounters.getCurrentEncounter();
        s.setInCombat(live != null && live.isInCombat());
        if (myBoss.isEmpty() && live != null && live.getTargetName() != null)
        {
            s.setBoss(live.getTargetName());
        }
        if (scope == null)
        {
            s.setWeapons(new ArrayList<>());
            return s;
        }
        EntityCombatStats st = scope.getLocalPlayerStats();
        s.setCombatSeconds(scope.getDurationSeconds());
        s.setTotalDamage(st.getTotalDamage());
        s.setDmgMelee(st.getStyleDamage(CombatStyle.MELEE));
        s.setDmgRanged(st.getStyleDamage(CombatStyle.RANGED));
        s.setDmgMagic(st.getStyleDamage(CombatStyle.MAGIC));
        s.setDmgTaken(st.getDamageTaken());
        s.setHealed(st.getHpHealed());
        s.setMaxHit(st.getMaxHitDealt() != null ? st.getMaxHitDealt().getAmount() : 0);
        s.setAttempts(st.getAttackAttempts());
        s.setHits(st.getSuccessfulHits());
        s.setSpecs(st.getSpecialAttacksCount());

        List<RemoteCombatSnapshot.WeaponSlice> weapons = new ArrayList<>();
        List<WeaponAbilityEntry> wb = st.getWeaponBreakdown();
        for (int i = 0; i < wb.size() && i < MAX_WEAPONS; i++)
        {
            WeaponAbilityEntry w = wb.get(i);
            RemoteCombatSnapshot.WeaponSlice ws = new RemoteCombatSnapshot.WeaponSlice();
            ws.setName(w.getWeaponName());
            ws.setDmg(w.getTotalDamage());
            ws.setHits(w.getHitCount());
            ws.setMax(w.getMaxHit());
            weapons.add(ws);
        }
        s.setWeapons(weapons);
        return s;
    }

    // ---------------------------------------------------------------- read side

    /** A shared fight is in progress right now (someone in the party is swinging). */
    public boolean isActive()
    {
        return enabledAndInParty() && (!myShared.isEmpty() || anyRemoteInCombat());
    }

    /**
     * Whether the HUD/panel should auto-swap from the solo fight to the combined group meter:
     * the feature is on, you're in a party, at least one other member is broadcasting, and a
     * fight (yours or a member's) is live. Between fights or when solo, this is false and the
     * normal solo view shows - the "Group" row still stays in the scope dropdown for inspection.
     */
    public boolean shouldAutoShowGroup()
    {
        if (!enabledAndInParty())
        {
            return false;
        }
        if (!myShared.isEmpty())
        {
            for (RemoteCombatSnapshot s : remote.values())
            {
                if (s.isInCombat()
                    && (myShared.equals(s.getSharedId()) || sameBoss(s.getBoss(), myBoss)))
                {
                    return true;
                }
            }
            return false;
        }
        // Not in a shared fight yet - a teammate within GROUP_PROXIMITY_TILES who is fighting
        // pulls the combined meter up; their target becomes "our" fight on the next tick.
        return anyNearbyRemoteInCombat();
    }

    /**
     * The "Group" scope row for the encounter dropdown / HUD: the shared meter is offered whenever
     * the feature is on and you are in a party (idle or not), so join / leave and the member list
     * stay visible between fights. Null otherwise. Always the same instance - mutated in place.
     */
    public EncounterSegment getGroupScopeOrNull()
    {
        if (!enabledAndInParty())
        {
            return null;
        }
        return getGroupSegment();
    }

    /**
     * Refresh the stable GROUP segment: my live contribution + every member's latest snapshot.
     * Snapshot-then-publish - the whole aggregate is built into local objects here (this runs on
     * both the client thread and the Swing EDT), then swapped into the shared segment in one
     * locked step by {@link EncounterSegment#publishGroupAggregate}.
     */
    EncounterSegment getGroupSegment()
    {
        String bossLabel = !myBoss.isEmpty() ? myBoss : firstRemoteBoss();

        // My aggregated contribution - scalars only, no timeline / time-series copy (the Group
        // meter has no merged event log and this is rebuilt many times a second).
        EntityCombatStats mine = new EntityCombatStats(localName());
        EncounterSegment liveScope = encounters.getEncounterScope();
        int myDur = 1;
        // Fold my own numbers in only when they belong to THIS shared fight. When I'm just standing
        // in on a teammate's pull (myShared adopted, but I haven't swung), my last unrelated fight
        // must not inflate the group total - my row stays at 0 until I engage. The idle overview
        // (myShared empty) keeps showing my most recent fight as before.
        boolean mineCountsHere = liveScope != null
            && (myShared.isEmpty()
                || liveScope.isInCombat()
                || sameBoss(liveScope.getTargetName(), myBoss));
        if (mineCountsHere)
        {
            mine.absorbScalarsOnly(liveScope.getLocalPlayerStats());
            myDur = Math.max(1, liveScope.getDurationSeconds());
        }

        boolean showOffline = config == null || config.combatPartyShowOffline();
        // While you are in a fight, the live group meter is scoped to THAT fight - only members
        // who have converged onto the same sharedId, or who are in combat with the same-named
        // target (bridges the ~1 tick before ids converge). A teammate off doing something else
        // is not folded in. Idle (no myShared), show the whole roster so the "Group" scope stays a
        // useful party overview between fights.
        boolean sharedFightOnly = !myShared.isEmpty();
        long now = System.currentTimeMillis();
        Map<String, EntityCombatStats> members = new LinkedHashMap<>();
        for (Map.Entry<Long, RemoteCombatSnapshot> e : remote.entrySet())
        {
            RemoteCombatSnapshot s = e.getValue();
            if (sharedFightOnly
                && !myShared.equals(s.getSharedId())
                && !(s.isInCombat() && sameBoss(s.getBoss(), myBoss) && withinProximity(s)))
            {
                continue;
            }
            boolean stale = now - lastSeenMs.getOrDefault(e.getKey(), 0L) > STALE_MS;
            if (stale && !showOffline)
            {
                continue;
            }
            String label = s.getRsn() == null || s.getRsn().isEmpty() ? "Member " + e.getKey() : s.getRsn();
            if (stale)
            {
                label = label + " (offline)";
            }
            EntityCombatStats es = new EntityCombatStats(label);
            es.applyRemoteSnapshot(s.getTotalDamage(), s.getDmgMelee(), s.getDmgRanged(), s.getDmgMagic(),
                s.getDmgTaken(), s.getHealed(), s.getAttempts(), s.getHits(), s.getSpecs(), s.getMaxHit());
            es.setDurationSeconds(Math.max(1, s.getCombatSeconds()));
            if (s.getWeapons() != null)
            {
                for (RemoteCombatSnapshot.WeaponSlice w : s.getWeapons())
                {
                    es.recordRemoteWeapon(w.getName(), w.getDmg(), w.getHits(), w.getMax());
                }
            }
            members.put(label, es);
        }

        groupScope.publishGroupAggregate(bossLabel, mine, myDur, members);
        return groupScope;
    }

    public int memberCount()
    {
        return remote.size() + 1;
    }

    // ------------------------------------------------------------ party controls (UI)

    public boolean partyEnabled()
    {
        return config != null && config.combatPartyEnabled();
    }

    /**
     * Whether RuneLite's stock "Party" plugin is present and enabled. It owns the websocket
     * session this feature rides on; we don't hard-depend on it (the group meter is opt-in), so
     * the Party control checks this and tells the user to turn it on if it's off. Returns true
     * when we can't tell (test seam) so we never nag without cause.
     */
    public boolean runelitePartyAvailable()
    {
        if (pluginManager == null)
        {
            return true;
        }
        for (Plugin p : pluginManager.getPlugins())
        {
            if (p instanceof PartyPlugin)
            {
                return pluginManager.isPluginEnabled(p);
            }
        }
        return false;
    }

    public boolean inParty()
    {
        return party != null && party.isInParty();
    }

    /** The current party passphrase (empty when not in a party). */
    public String passphrase()
    {
        String p = party != null ? party.getPartyPassphrase() : null;
        return p == null ? "" : p;
    }

    // Words for a self-generated join code. We deliberately do NOT call
    // PartyService.generatePassphrase() - that one asserts it is on the client thread and reads
    // the item cache, so it blows up when called from a Swing menu handler.
    private static final String[] PASS_WORDS = {
        "copper", "willow", "rune", "dragon", "goblin", "wizard", "yak", "moss", "coal", "gnome",
        "spider", "amber", "onyx", "ferret", "otter", "raven", "cedar", "flint", "quartz", "badger",
        "maple", "cobalt", "heron", "lynx", "opal", "pike", "sable", "thorn", "vole", "wren",
    };

    /** A fresh word-based passphrase (no client-thread / item-cache dependency). */
    public String newPassphrase()
    {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++)
        {
            if (i > 0)
            {
                sb.append('-');
            }
            sb.append(PASS_WORDS[r.nextInt(PASS_WORDS.length)]);
        }
        return sb.toString();
    }

    public void joinParty(String passphrase)
    {
        if (party != null && passphrase != null && !passphrase.trim().isEmpty())
        {
            party.changeParty(passphrase.trim());
        }
    }

    public void leaveParty()
    {
        if (party != null && party.isInParty())
        {
            party.changeParty(null);
        }
        remote.clear();
        lastSeenMs.clear();
    }

    // ---------------------------------------------------------------- helpers

    private boolean enabledAndInParty()
    {
        return config != null && config.combatPartyEnabled()
            && party != null && party.isInParty();
    }

    private long localMemberId()
    {
        return party != null && party.getLocalMember() != null ? party.getLocalMember().getMemberId() : -1L;
    }

    private String localName()
    {
        if (client != null && client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
        {
            return client.getLocalPlayer().getName();
        }
        // The encounter manager already resolved the RSN from the client - reuse it.
        EncounterSegment scope = encounters.getEncounterScope();
        if (scope != null && scope.getLocalPlayerStats() != null)
        {
            String n = scope.getLocalPlayerStats().getName();
            if (n != null && !n.isEmpty() && !"You".equals(n))
            {
                return n;
            }
        }
        return party != null && party.getLocalMember() != null && party.getLocalMember().getDisplayName() != null
            ? party.getLocalMember().getDisplayName() : "You";
    }

    private boolean anyRemoteInCombat()
    {
        for (RemoteCombatSnapshot s : remote.values())
        {
            if (s.isInCombat())
            {
                return true;
            }
        }
        return false;
    }

    /** Sample my own tile on the game tick so the EDT-side proximity checks never touch the client. */
    private void cacheMyLocation()
    {
        if (client == null || client.getLocalPlayer() == null)
        {
            return;
        }
        WorldPoint wp = client.getLocalPlayer().getWorldLocation();
        if (wp != null)
        {
            myX = wp.getX();
            myY = wp.getY();
            myPlane = wp.getPlane();
        }
    }

    /** First party member who is fighting and within GROUP_PROXIMITY_TILES of me, or null. */
    private RemoteCombatSnapshot nearbyRemoteFight()
    {
        for (RemoteCombatSnapshot s : remote.values())
        {
            if (s.isInCombat() && withinProximity(s))
            {
                return s;
            }
        }
        return null;
    }

    private boolean anyNearbyRemoteInCombat()
    {
        return nearbyRemoteFight() != null;
    }

    /**
     * Whether a member's snapshot is close enough to count as the same fight. True when either side
     * has no location yet (older peer, or I haven't ticked in-world) so mixed versions fall back to
     * matching on target name alone.
     */
    private boolean withinProximity(RemoteCombatSnapshot s)
    {
        if (!hasLocation(s) || (myX == 0 && myY == 0))
        {
            return true;
        }
        if (s.getWorldPlane() != myPlane)
        {
            return false;
        }
        int dx = Math.abs(s.getWorldX() - myX);
        int dy = Math.abs(s.getWorldY() - myY);
        return Math.max(dx, dy) <= GROUP_PROXIMITY_TILES;
    }

    private static boolean hasLocation(RemoteCombatSnapshot s)
    {
        return s.getWorldX() != 0 || s.getWorldY() != 0;
    }

    private String firstRemoteBoss()
    {
        for (RemoteCombatSnapshot s : remote.values())
        {
            if (s.getBoss() != null && !s.getBoss().isEmpty())
            {
                return s.getBoss();
            }
        }
        return "";
    }

    private static boolean sameBoss(String a, String b)
    {
        return a != null && b != null && !a.isEmpty() && a.equalsIgnoreCase(b);
    }
}
