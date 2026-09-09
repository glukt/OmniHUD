package com.osrscopilot.combat.model;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EncounterSegment
{
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault());

    private final UUID segmentId;
    private String targetName;
    private int npcId = -1;
    private int npcCombatLevel = -1; // snapshot of the primary target's combat level, -1 if unknown
    private SegmentType segmentType;
    private SegmentStatus status;
    private int startTick;
    private int endTick;
    private Instant startTimestamp;
    private Instant endTimestamp;

    // For the long-lived session / total scopes: seconds of *actual combat*, accumulated only while
    // a fight is live. A single ENCOUNTER keeps using wall-clock (it is naturally bounded and the
    // graph x-axis is wall-clock), but session/total must not decay while the player stands idle.
    private double combatSeconds = 0.0;

    private final EntityCombatStats localPlayerStats;
    private final EntityCombatStats thrallStats;
    private final EntityCombatStats cannonStats;
    private final Map<String, EntityCombatStats> otherParticipants = new HashMap<>();

    // Per-enemy damage split for a multi-target pull (the per-target recap list). Keyed by the
    // resolved display name so "(1)/(2)" tags flow through. Not shown as HUD bars.
    private final Map<String, EntityCombatStats> enemyDamage = new java.util.LinkedHashMap<>();

    // Crowd control applied to enemies this fight (bind / snare / entangle / freeze).
    private final List<DebuffApplication> debuffs = new ArrayList<>();

    public EncounterSegment(UUID segmentId, String targetName, SegmentType segmentType, int startTick, String playerName)
    {
        this.segmentId = segmentId != null ? segmentId : UUID.randomUUID();
        this.targetName = targetName != null ? targetName : "Combat Encounter";
        this.segmentType = segmentType != null ? segmentType : SegmentType.ENCOUNTER;
        this.status = SegmentStatus.IN_PROGRESS;
        this.startTick = startTick;
        this.endTick = startTick;
        this.startTimestamp = Instant.now();

        this.localPlayerStats = new EntityCombatStats(playerName != null ? playerName : "You");
        this.thrallStats = new EntityCombatStats("Thrall");
        this.thrallStats.setDominantStyle(CombatStyle.THRALL);
        this.cannonStats = new EntityCombatStats("Cannon");
        this.cannonStats.setDominantStyle(CombatStyle.CANNON);
    }

    public UUID getSegmentId()
    {
        return segmentId;
    }

    public String getTargetName()
    {
        return targetName;
    }

    public void setTargetName(String targetName)
    {
        this.targetName = targetName;
    }

    public int getNpcId()
    {
        return npcId;
    }

    public int getNpcCombatLevel()
    {
        return npcCombatLevel;
    }

    public void setNpcInfo(int npcId, int npcCombatLevel)
    {
        this.npcId = npcId;
        this.npcCombatLevel = npcCombatLevel;
    }

    /** "Hill Giant (lvl 28)" when the level is known, else just the name. */
    public String getTargetNameWithLevel()
    {
        return npcCombatLevel > 0 ? targetName + " (lvl " + npcCombatLevel + ")" : targetName;
    }

    public SegmentType getSegmentType()
    {
        return segmentType;
    }

    public void setSegmentType(SegmentType segmentType)
    {
        this.segmentType = segmentType;
    }

    public SegmentStatus getStatus()
    {
        return status;
    }

    public void setStatus(SegmentStatus status)
    {
        this.status = status;
    }

    public int getStartTick()
    {
        return startTick;
    }

    public void setStartTick(int startTick)
    {
        this.startTick = startTick;
    }

    public int getEndTick()
    {
        return endTick;
    }

    public void setEndTick(int endTick)
    {
        this.endTick = endTick;
    }

    public Instant getStartTimestamp()
    {
        return startTimestamp;
    }

    public void setStartTimestamp(Instant startTimestamp)
    {
        this.startTimestamp = startTimestamp;
    }

    public Instant getEndTimestamp()
    {
        return endTimestamp;
    }

    public void setEndTimestamp(Instant endTimestamp)
    {
        this.endTimestamp = endTimestamp;
    }

    public EntityCombatStats getLocalPlayerStats()
    {
        return localPlayerStats;
    }

    public EntityCombatStats getThrallStats()
    {
        return thrallStats;
    }

    public EntityCombatStats getCannonStats()
    {
        return cannonStats;
    }

    public Map<String, EntityCombatStats> getOtherParticipants()
    {
        return otherParticipants;
    }

    /**
     * Atomically republish this GROUP segment from a snapshot the party service built off to the
     * side. The shared meter is read on the Swing EDT and the client thread at the same time, so
     * the whole swap - boss label, my aggregated contribution, and the participant map - happens
     * under this segment's monitor; a reader (also on the monitor) never sees a half-cleared map.
     * Scalars only: the Group meter keeps no merged action-ledger of its own.
     */
    public synchronized void publishGroupAggregate(String bossLabel, EntityCombatStats mineSnapshot,
        int myDurationSeconds, Map<String, EntityCombatStats> members)
    {
        this.targetName = (bossLabel == null || bossLabel.isEmpty()) ? "Group" : bossLabel;
        this.status = SegmentStatus.IN_PROGRESS;

        localPlayerStats.reset();
        if (mineSnapshot != null)
        {
            localPlayerStats.absorbScalarsOnly(mineSnapshot);
            localPlayerStats.setName(mineSnapshot.getName());
        }
        localPlayerStats.setDurationSeconds(Math.max(1, myDurationSeconds));

        otherParticipants.clear();
        if (members != null)
        {
            otherParticipants.putAll(members);
        }
    }

    // enemyDamage is written on the client thread and read on the Swing EDT (recap "Targets"
    // section) - guard it with the segment lock, same discipline as EntityCombatStats.
    public synchronized void recordDamageToEnemy(String enemyName, CombatStyle style, int amount, String weapon, int tick)
    {
        if (enemyName == null || enemyName.isEmpty())
        {
            return;
        }
        enemyDamage.computeIfAbsent(enemyName, EntityCombatStats::new).recordDamageDealt(style, amount, weapon, tick);
    }

    /** Per-enemy damage, highest first — for the recap's "Targets" section. */
    public synchronized List<EntityCombatStats> getEnemyBreakdown()
    {
        List<EntityCombatStats> list = new ArrayList<>(enemyDamage.values());
        list.sort((a, b) -> Long.compare(b.getTotalDamage(), a.getTotalDamage()));
        return list;
    }

    public synchronized Map<String, EntityCombatStats> getEnemyDamage()
    {
        return new java.util.LinkedHashMap<>(enemyDamage);
    }

    public synchronized void clearEnemyDamage()
    {
        enemyDamage.clear();
    }

    public synchronized void addDebuff(DebuffApplication d)
    {
        debuffs.add(d);
    }

    public synchronized List<DebuffApplication> getDebuffs()
    {
        return new ArrayList<>(debuffs);
    }

    /** Debuffs whose timer hasn't run out yet (for the per-tick expiry sweep). */
    public synchronized List<DebuffApplication> getOpenDebuffs()
    {
        List<DebuffApplication> open = new ArrayList<>();
        for (DebuffApplication d : debuffs)
        {
            if (d.isOpen())
            {
                open.add(d);
            }
        }
        return open;
    }

    public synchronized void clearDebuffs()
    {
        debuffs.clear();
    }

    // Snapshot of the last few incoming hits, captured when the player is downed (status WIPED),
    // so the log can show a "what killed you" recap even after the ring buffer has moved on.
    private final List<CombatTimelineEvent> deathRecap = new ArrayList<>();

    public synchronized void setDeathRecap(List<CombatTimelineEvent> hits)
    {
        deathRecap.clear();
        if (hits != null)
        {
            deathRecap.addAll(hits);
        }
    }

    public synchronized List<CombatTimelineEvent> getDeathRecap()
    {
        return new ArrayList<>(deathRecap);
    }

    public boolean isSessionScope()
    {
        return segmentType == SegmentType.SESSION_CURRENT || segmentType == SegmentType.SESSION_TOTAL;
    }

    public double getCombatSeconds()
    {
        return combatSeconds;
    }

    public void addCombatSeconds(double seconds)
    {
        this.combatSeconds += seconds;
    }

    public void resetCombatSeconds()
    {
        this.combatSeconds = 0.0;
    }

    public int getDurationSeconds()
    {
        // One rate denominator for every scope: the summed *combat* time of the fight(s) - time
        // hits were actually landing - never wall-clock. So a single kill reads an identical
        // DPS/DTPS/HPS whether viewed as the Live fight, the Current Session or the Total, and the
        // displayed rate freezes at the last hit rather than decaying while the encounter timeout
        // runs out (combatSeconds stops accruing a few ticks after the last combat action - see
        // CombatEncounterManager.onGameTick). A live ENCOUNTER with no accrued combat time yet
        // (before its first tick) falls back to wall-clock so it isn't dividing by ~0.
        if (isSessionScope() || mergedKills > 1 || combatSeconds > 0)
        {
            // A live single fight floors the denominator at 2s: (int)-truncating a sub-2s combat
            // time otherwise prints a wild transient rate in the opening ~1.7s. Finished fights
            // and the Overall scopes keep the true value.
            double floor = (isInCombat() && !isSessionScope() && mergedKills <= 1) ? 2.0 : 1.0;
            return (int) Math.max(floor, Math.round(combatSeconds));
        }
        if (startTimestamp == null) return 0;
        Instant end = (endTimestamp != null) ? endTimestamp : Instant.now();
        long secs = Duration.between(startTimestamp, end).getSeconds();
        return (int) Math.max(1, secs);
    }

    /**
     * Wall-clock seconds since this scope was last (re)anchored - for a "logged in / at it for
     * 2h 14m" readout. Distinct from {@link #getDurationSeconds()}, which for the session scopes
     * is combat time only.
     */
    public int getWallClockSeconds()
    {
        if (startTimestamp == null) return 0;
        Instant end = (endTimestamp != null) ? endTimestamp : Instant.now();
        return (int) Math.max(0, Duration.between(startTimestamp, end).getSeconds());
    }

    public synchronized void updateDuration(int currentTick)
    {
        this.endTick = currentTick;
        double durationSec = getDurationSeconds();
        localPlayerStats.setDurationSeconds(durationSec);
        thrallStats.setDurationSeconds(durationSec);
        cannonStats.setDurationSeconds(durationSec);
        otherParticipants.values().forEach(e -> e.setDurationSeconds(durationSec));
        enemyDamage.values().forEach(e -> e.setDurationSeconds(durationSec));
    }

    // getTotalDamage / getRankedParticipants iterate otherParticipants, which the party service
    // rebuilds in place for the GROUP scope from both the EDT and the client thread - guard them
    // with the same monitor publishGroupAggregate() / updateDuration() hold.
    public synchronized long getTotalDamage()
    {
        long total = localPlayerStats.getTotalDamage() + thrallStats.getTotalDamage() + cannonStats.getTotalDamage();
        for (EntityCombatStats other : otherParticipants.values())
        {
            total += other.getTotalDamage();
        }
        return Math.max(1, total);
    }

    public synchronized List<EntityCombatStats> getRankedParticipants(int max)
    {
        List<EntityCombatStats> list = new ArrayList<>();
        if (localPlayerStats.getTotalDamage() > 0 || localPlayerStats.getDamageTaken() > 0 || isInCombat())
        {
            list.add(localPlayerStats);
        }
        if (thrallStats.getTotalDamage() > 0)
        {
            list.add(thrallStats);
        }
        if (cannonStats.getTotalDamage() > 0)
        {
            list.add(cannonStats);
        }
        otherParticipants.values().forEach(e -> {
            if (e.getTotalDamage() > 0) list.add(e);
        });

        list.sort((a, b) -> Long.compare(b.getTotalDamage(), a.getTotalDamage()));
        if (list.size() > max)
        {
            return list.subList(0, max);
        }
        return list;
    }

    public boolean isInCombat()
    {
        return status == SegmentStatus.IN_PROGRESS || status == SegmentStatus.PHASE_TRANSITION;
    }

    // A monotonic index assigned when the fight is filed into history, so the dropdown can show
    // "#3 Zulrah ..." labels. 0 while the fight is still live / for the scope rows.
    private int historyIndex = 0;

    // Consecutive same-target kills folded into this one row. 1 = a
    // single fight; >1 renders as "Gargoyle x47".
    private int mergedKills = 1;

    // Latched true while a shared party fight was live at any point during this encounter, so the
    // history dropdown can mark it "[grp]". Sticky - set on a tick, kept through finalize/merge.
    private boolean groupFight = false;

    // 0..1 - how well the meter's attributed self-damage matched the Hitpoints-XP-derived damage
    // for this fight. Written by XpReconciler (observe-only); 1.0 until reconciled. Diagnostic.
    private volatile double attributionConfidence = 1.0;

    // Per-kill snapshots for a merged row, so the scope dropdown can expand "Gargoyle x47" into
    // its individual kills. Capped; oldest dropped (its numbers are already summed into the row).
    private static final int MERGED_CHILD_CAP = 40;
    private final List<KillSummary> mergedChildren = new ArrayList<>();

    /** A read-only snapshot of one kill inside a merged trash row. */
    public static final class KillSummary
    {
        private final int index;
        private final String targetName;
        private final long totalDamage;
        private final long damageTaken;
        private final int durationSeconds;
        private final double dps;
        private final double dtps;
        private final SegmentStatus status;
        private final Instant endTimestamp;

        KillSummary(int index, String targetName, long totalDamage, long damageTaken,
                    int durationSeconds, double dps, double dtps, SegmentStatus status, Instant endTimestamp)
        {
            this.index = index;
            this.targetName = targetName;
            this.totalDamage = totalDamage;
            this.damageTaken = damageTaken;
            this.durationSeconds = durationSeconds;
            this.dps = dps;
            this.dtps = dtps;
            this.status = status;
            this.endTimestamp = endTimestamp;
        }

        public int getIndex() { return index; }
        public String getTargetName() { return targetName; }
        public long getTotalDamage() { return totalDamage; }
        public long getDamageTaken() { return damageTaken; }
        public int getDurationSeconds() { return durationSeconds; }
        public double getDps() { return dps; }
        public double getDtps() { return dtps; }
        public SegmentStatus getStatus() { return status; }
        /** When this kill ended (wall-clock), or null if it was never stamped. */
        public Instant getEndTimestamp() { return endTimestamp; }
    }

    private KillSummary snapshotAsKill(int index)
    {
        EntityCombatStats s = localPlayerStats;
        return new KillSummary(index, targetName, s.getTotalDamage(), s.getDamageTaken(),
            getDurationSeconds(), s.getDps(), s.getDtps(), status, endTimestamp);
    }

    public synchronized List<KillSummary> getMergedChildren()
    {
        return new ArrayList<>(mergedChildren);
    }

    public synchronized boolean hasMergedChildren()
    {
        return !mergedChildren.isEmpty();
    }

    public int getHistoryIndex()
    {
        return historyIndex;
    }

    public void setHistoryIndex(int historyIndex)
    {
        this.historyIndex = historyIndex;
    }

    public int getMergedKills()
    {
        return mergedKills;
    }

    public boolean isMerged()
    {
        return mergedKills > 1;
    }

    /** Mark that a shared party fight was live during this encounter (idempotent, sticky). */
    public synchronized void markGroupFight()
    {
        this.groupFight = true;
    }

    public synchronized boolean isGroupFight()
    {
        return groupFight;
    }

    /** Diagnostic: 0..1 agreement between attributed self-damage and Hitpoints-XP-derived damage. */
    public double getAttributionConfidence()
    {
        return attributionConfidence;
    }

    public void setAttributionConfidence(double c)
    {
        this.attributionConfidence = c < 0 ? 0 : (c > 1 ? 1 : c);
    }

    /**
     * Fold a just-finished fight of the same target into this history row (trash merge). Sums every
     * participant's stats, extends the end, bumps the kill count, and surfaces a wipe if one
     * happened anywhere in the run.
     */
    public synchronized void absorb(EncounterSegment other)
    {
        if (other == null || other == this)
        {
            return;
        }

        // Keep a per-kill snapshot so "Gargoyle x47" can be expanded. On the first merge, snapshot
        // this row's own state (which was kill #1) before it gets summed into.
        if (mergedChildren.isEmpty() && mergedKills == 1)
        {
            mergedChildren.add(snapshotAsKill(1));
        }
        mergedChildren.add(other.snapshotAsKill(mergedChildren.size() + 1));
        while (mergedChildren.size() > MERGED_CHILD_CAP)
        {
            mergedChildren.remove(0);
        }

        localPlayerStats.absorb(other.getLocalPlayerStats());
        thrallStats.absorb(other.getThrallStats());
        cannonStats.absorb(other.getCannonStats());
        other.getEnemyDamage().forEach((k, v) ->
            enemyDamage.computeIfAbsent(k, EntityCombatStats::new).absorb(v));

        combatSeconds += other.combatSeconds;
        mergedKills += Math.max(1, other.mergedKills);
        if (other.groupFight)
        {
            groupFight = true;
        }
        endTick = Math.max(endTick, other.endTick);
        if (other.endTimestamp != null)
        {
            endTimestamp = other.endTimestamp;
        }
        if (other.status == SegmentStatus.WIPED)
        {
            status = SegmentStatus.WIPED;
            if (!other.getDeathRecap().isEmpty())
            {
                setDeathRecap(other.getDeathRecap());
            }
        }
        else if (status != SegmentStatus.WIPED)
        {
            status = SegmentStatus.COMPLETED;
        }
    }

    /**
     * Label shown in the scope dropdown. Uses this vocabulary:
     * "Live" = the current/most-recent segment, "Current Session" / "Total" = the two Overall
     * aggregates, and past fights read "#N Target (mm:ss) - kill/wipe @ HH:MM".
     */
    @Override
    public synchronized String toString()
    {
        String timeStr = startTimestamp != null ? TIME_FMT.format(startTimestamp) : "--:--";
        int totalSec = getDurationSeconds();
        int mins = totalSec / 60;
        int secs = totalSec % 60;
        String durStr = String.format("%02d:%02d", mins, secs);

        if (segmentType == SegmentType.SESSION_CURRENT)
        {
            return "Current Session (" + durStr + ")";
        }
        if (segmentType == SegmentType.SESSION_TOTAL)
        {
            return "Total (all-time)";
        }
        if (segmentType == SegmentType.GROUP)
        {
            int members = otherParticipants.size() + 1;
            boolean named = targetName != null && !targetName.isEmpty() && !"Group".equals(targetName);
            return "Group (" + members + ")" + (named ? " - " + targetName : "");
        }
        if (targetName != null && targetName.startsWith("No fights"))
        {
            return "Live - waiting for combat";
        }
        if (isInCombat())
        {
            return "Live - " + getTargetNameWithLevel() + " (" + durStr + ")";
        }
        String tag = status == SegmentStatus.WIPED ? "wipe" : "kill";
        String num = historyIndex > 0 ? "#" + historyIndex + " " : "";
        String grp = groupFight ? "[grp] " : "";
        String who = mergedKills > 1 ? targetName + " x" + mergedKills : getTargetNameWithLevel();
        return num + grp + who + " (" + durStr + ") - " + tag + " @ " + timeStr;
    }
}
