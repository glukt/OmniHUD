package com.osrscopilot.combat.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class EntityCombatStats
{
    private String name;
    private CombatStyle dominantStyle = CombatStyle.MELEE;
    private long totalDamage = 0;
    private long damageTaken = 0;
    private int hpHealed = 0;
    private int hpOverhealed = 0;
    private int potionsDrunkCount = 0;
    private int foodEatenCount = 0;
    private int attackAttempts = 0;
    private int successfulHits = 0;
    private int prayerPointsRestored = 0;
    private int attackUptimeTicks = 0;
    private int lostCombatTicks = 0;
    private int specialAttacksCount = 0;
    private long specialAttackDamage = 0;
    private long totalGpCost = 0;
    private double durationSeconds = 1.0;


    private HitRecord maxHitDealt;
    private HitRecord maxHitTaken;

    private final Map<CombatStyle, Long> damageByStyle = new EnumMap<>(CombatStyle.class);
    private final Map<CombatStyle, Long> damageTakenByStyle = new EnumMap<>(CombatStyle.class);
    private final Map<String, Long> damageTakenBySource = new java.util.LinkedHashMap<>();
    private final Map<String, Integer> damageTakenHitsBySource = new java.util.LinkedHashMap<>();
    private final Map<String, WeaponDamageAccumulator> weaponMap = new HashMap<>();
    private final Map<Integer, ConsumableAccumulator> consumableMap = new HashMap<>();
    private final List<CombatTimelineEvent> timelineEvents = new ArrayList<>();
    private final List<CombatTimeSeriesPoint> timeSeries = new ArrayList<>();

    public synchronized void reset()
    {
        this.totalDamage = 0;
        this.damageTaken = 0;
        this.hpHealed = 0;
        this.hpOverhealed = 0;
        this.potionsDrunkCount = 0;
        this.foodEatenCount = 0;
        this.attackAttempts = 0;
        this.successfulHits = 0;
        this.prayerPointsRestored = 0;
        this.attackUptimeTicks = 0;
        this.lostCombatTicks = 0;
        this.specialAttacksCount = 0;
        this.specialAttackDamage = 0;
        this.totalGpCost = 0;
        this.durationSeconds = 1.0;
        this.maxHitDealt = null;
        this.maxHitTaken = null;
        this.damageByStyle.clear();
        this.damageTakenByStyle.clear();
        this.damageTakenBySource.clear();
        this.damageTakenHitsBySource.clear();
        this.weaponMap.clear();
        this.consumableMap.clear();
        this.timelineEvents.clear();
        this.timeSeries.clear();
        this.seriesStride = 1;
        this.droppedEventCount = 0;
        this.dominantStyle = CombatStyle.MELEE;
    }

    // The lists/maps below are mutated on the client thread (@Subscribe / onGameTick) and read on the
    // Swing EDT (side-panel refresh). Every accessor returns a snapshot copy taken under this
    // instance lock; every mutator that touches them is synchronized on the same lock.
    public synchronized List<CombatTimelineEvent> getTimelineEvents()
    {
        return new ArrayList<>(timelineEvents);
    }

    public synchronized List<CombatTimeSeriesPoint> getTimeSeries()
    {
        return new ArrayList<>(timeSeries);
    }

    public synchronized Map<CombatStyle, Long> getDamageByStyle()
    {
        return new EnumMap<>(damageByStyle);
    }

    public synchronized Map<CombatStyle, Long> getDamageTakenByStyleRaw()
    {
        return new EnumMap<>(damageTakenByStyle);
    }

    /**
     * Bulk-load persisted "Total" aggregates on startup (only the headline fields the Total-scope
     * cards read). Additive so a restore + subsequent live fights sum correctly.
     */
    public synchronized void applyPersistedTotals(long totalDamage, long damageTaken,
        int hpHealed, int hpOverhealed, int potions, int food, int attackAttempts, int successfulHits,
        long totalGpCost, Map<CombatStyle, Long> byStyle, Map<CombatStyle, Long> takenByStyle)
    {
        this.totalDamage += totalDamage;
        this.damageTaken += damageTaken;
        this.hpHealed += hpHealed;
        this.hpOverhealed += hpOverhealed;
        this.potionsDrunkCount += potions;
        this.foodEatenCount += food;
        this.attackAttempts += attackAttempts;
        this.successfulHits += successfulHits;
        this.totalGpCost += totalGpCost;
        if (byStyle != null) byStyle.forEach((k, v) -> damageByStyle.merge(k, v, Long::sum));
        if (takenByStyle != null) takenByStyle.forEach((k, v) -> damageTakenByStyle.merge(k, v, Long::sum));
        updateDominantStyle();
    }

    /**
     * Hydrate this stats object from a party member's broadcast snapshot (headline totals only).
     * Used to build the GROUP scope's per-member rows from remote data.
     */
    public synchronized void applyRemoteSnapshot(long totalDamage, long melee, long ranged, long magic,
        long damageTaken, int hpHealed, int attackAttempts, int successfulHits, int specCount, int maxHit)
    {
        this.totalDamage = totalDamage;
        this.damageTaken = damageTaken;
        this.hpHealed = hpHealed;
        this.attackAttempts = attackAttempts;
        this.successfulHits = successfulHits;
        this.specialAttacksCount = specCount;
        this.damageByStyle.clear();
        if (melee > 0) damageByStyle.put(CombatStyle.MELEE, melee);
        if (ranged > 0) damageByStyle.put(CombatStyle.RANGED, ranged);
        if (magic > 0) damageByStyle.put(CombatStyle.MAGIC, magic);
        if (maxHit > 0)
        {
            this.maxHitDealt = new HitRecord(maxHit, dominantStyle, name, 0);
        }
        updateDominantStyle();
    }

    /** Add one weapon/spell row from a remote member's snapshot (for their drill-down view). */
    public synchronized void recordRemoteWeapon(String weaponName, long damage, int hitCount, int maxHit)
    {
        if (weaponName == null || weaponName.isEmpty())
        {
            return;
        }
        WeaponDamageAccumulator w = weaponMap.computeIfAbsent(weaponName, WeaponDamageAccumulator::new);
        w.totalDamage += damage;
        w.hitCount += hitCount;
        w.maxHit = Math.max(w.maxHit, maxHit);
    }

    public Map<String, WeaponDamageAccumulator> getWeaponMap()
    {
        return weaponMap;
    }

    public Map<Integer, ConsumableAccumulator> getConsumableMap()
    {
        return consumableMap;
    }

    public long getTotalDamage()
    {
        return totalDamage;
    }

    public void setTotalDamage(long totalDamage)
    {
        this.totalDamage = totalDamage;
    }

    public long getDamageTaken()
    {
        return damageTaken;
    }

    public void setDamageTaken(long damageTaken)
    {
        this.damageTaken = damageTaken;
    }

    public int getHpHealed()
    {
        return hpHealed;
    }

    public void setHpHealed(int hpHealed)
    {
        this.hpHealed = hpHealed;
    }

    public int getHpOverhealed()
    {
        return hpOverhealed;
    }

    public void setHpOverhealed(int hpOverhealed)
    {
        this.hpOverhealed = hpOverhealed;
    }

    public int getPotionsDrunkCount()
    {
        return potionsDrunkCount;
    }

    public void setPotionsDrunkCount(int potionsDrunkCount)
    {
        this.potionsDrunkCount = potionsDrunkCount;
    }

    public int getFoodEatenCount()
    {
        return foodEatenCount;
    }

    public void setFoodEatenCount(int foodEatenCount)
    {
        this.foodEatenCount = foodEatenCount;
    }

    public int getAttackAttempts()
    {
        return attackAttempts;
    }

    public void setAttackAttempts(int attackAttempts)
    {
        this.attackAttempts = attackAttempts;
    }

    public int getSuccessfulHits()
    {
        return successfulHits;
    }

    public void setSuccessfulHits(int successfulHits)
    {
        this.successfulHits = successfulHits;
    }

    public int getPrayerPointsRestored()
    {
        return prayerPointsRestored;
    }

    public void setPrayerPointsRestored(int prayerPointsRestored)
    {
        this.prayerPointsRestored = prayerPointsRestored;
    }

    public int getAttackUptimeTicks()
    {
        return attackUptimeTicks;
    }

    public void setAttackUptimeTicks(int attackUptimeTicks)
    {
        this.attackUptimeTicks = attackUptimeTicks;
    }

    public int getLostCombatTicks()
    {
        return lostCombatTicks;
    }

    public void setLostCombatTicks(int lostCombatTicks)
    {
        this.lostCombatTicks = lostCombatTicks;
    }

    public int getSpecialAttacksCount()
    {
        return specialAttacksCount;
    }

    public void setSpecialAttacksCount(int specialAttacksCount)
    {
        this.specialAttacksCount = specialAttacksCount;
    }

    public long getSpecialAttackDamage()
    {
        return specialAttackDamage;
    }

    public void setSpecialAttackDamage(long specialAttackDamage)
    {
        this.specialAttackDamage = specialAttackDamage;
    }

    public long getTotalGpCost()
    {
        return totalGpCost;
    }

    public void setTotalGpCost(long totalGpCost)
    {
        this.totalGpCost = totalGpCost;
    }

    public double getDurationSeconds()
    {
        return durationSeconds;
    }

    public void setDurationSeconds(double durationSeconds)
    {
        this.durationSeconds = durationSeconds;
    }

    public HitRecord getMaxHitDealt()
    {
        return maxHitDealt;
    }

    public void setMaxHitDealt(HitRecord maxHitDealt)
    {
        this.maxHitDealt = maxHitDealt;
    }

    public HitRecord getMaxHitTaken()
    {
        return maxHitTaken;
    }

    public void setMaxHitTaken(HitRecord maxHitTaken)
    {
        this.maxHitTaken = maxHitTaken;
    }

    public CombatStyle getDominantStyle()
    {
        return dominantStyle;
    }

    public void setDominantStyle(CombatStyle dominantStyle)
    {
        this.dominantStyle = dominantStyle;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public EntityCombatStats(String name)
    {
        this.name = name;
    }

    public double getDps()
    {
        return durationSeconds > 0 ? (double) totalDamage / durationSeconds : 0.0;
    }

    /** DPS measured against time the player was actually attacking (DPS excluding idle time). */
    public double getActiveDps()
    {
        double activeSeconds = getAttackUptimeTicks() * 0.6;
        return activeSeconds > 0 ? (double) totalDamage / activeSeconds : getDps();
    }

    public double getDtps()
    {
        return durationSeconds > 0 ? (double) damageTaken / durationSeconds : 0.0;
    }

    public double getHps()
    {
        return durationSeconds > 0 ? (double) hpHealed / durationSeconds : 0.0;
    }

    public int getTotalHealing()
    {
        return hpHealed + hpOverhealed;
    }

    public double getHitAccuracy()
    {
        return attackAttempts > 0 ? ((double) successfulHits / attackAttempts) * 100.0 : 0.0;
    }

    public double getAttackUptimePercent()
    {
        int totalTicks = attackUptimeTicks + lostCombatTicks;
        return totalTicks > 0 ? ((double) attackUptimeTicks / totalTicks) * 100.0 : 100.0;
    }

    public double getGpPerHour()
    {
        return durationSeconds > 0 ? ((double) totalGpCost / durationSeconds) * 3600.0 : 0.0;
    }

    // Ring-buffer cap for the action ledger. Deep enough for a long boss fight; the long-lived
    // session / total scopes are set lower by CombatEncounterManager.
    private int maxTimelineEvents = 4000;
    // How many events have been evicted from the front of the ring (so the UI can say so honestly
    // instead of claiming "N of N").
    private int droppedEventCount = 0;

    public synchronized void setMaxTimelineEvents(int max)
    {
        this.maxTimelineEvents = Math.max(10, max);
        while (timelineEvents.size() > maxTimelineEvents)
        {
            evictOldest();
        }
    }

    public synchronized int getMaxTimelineEvents()
    {
        return maxTimelineEvents;
    }

    public synchronized int getDroppedEventCount()
    {
        return droppedEventCount;
    }

    public synchronized void addTimelineEvent(CombatTimelineEvent event)
    {
        if (event != null)
        {
            timelineEvents.add(event);
            while (timelineEvents.size() > maxTimelineEvents)
            {
                evictOldest();
            }
        }
    }

    /** Drop the oldest event, but keep the fight's opening "START" line pinned. */
    private void evictOldest()
    {
        if (timelineEvents.isEmpty())
        {
            return;
        }
        int idx = (timelineEvents.size() > 1 && "START".equals(timelineEvents.get(0).getEventType())) ? 1 : 0;
        timelineEvents.remove(idx);
        droppedEventCount++;
    }

    public synchronized void recordTimeSeriesSecond(int sec, int dmg, int tkn, int heal, String note)
    {
        recordTimeSeriesSecond(sec, dmg, tkn, heal, null, note);
    }

    // Adaptive time-series resolution: 1s buckets until the series fills, then it halves resolution
    // (2s, 4s, ...) so the WHOLE fight stays on the graph instead of the first N minutes being
    // evicted and the x-axis stretching off the end.
    private static final int SERIES_CAP = 600;
    private int seriesStride = 1;
    // Rolling-average window for the fight graph, in seconds. 1 = raw per-second (spiky), higher
    // = smoother (a smoothed line, not raw hits).
    private int rollingWindowSeconds = 5;

    public synchronized void setRollingWindowSeconds(int seconds)
    {
        this.rollingWindowSeconds = Math.max(1, Math.min(60, seconds));
    }

    public synchronized void recordTimeSeriesSecond(int sec, int dmg, int tkn, int heal, CombatStyle style, String note)
    {
        CombatTimeSeriesPoint point;
        if (timeSeries.isEmpty() || sec - timeSeries.get(timeSeries.size() - 1).getSecond() >= seriesStride)
        {
            point = new CombatTimeSeriesPoint(sec);
            point.setDamageDealt(dmg);
            if (style == CombatStyle.MELEE) point.setMeleeDamage(dmg);
            else if (style == CombatStyle.RANGED) point.setRangedDamage(dmg);
            else if (style == CombatStyle.MAGIC) point.setMagicDamage(dmg);
            else if (style != null && style.isStatusEffect()) point.setStatusDamage(dmg);
            else if (style == CombatStyle.THRALL) point.setThrallDamage(dmg);
            else if (style == CombatStyle.CANNON) point.setCannonDamage(dmg);

            point.setDamageTaken(tkn);
            point.setHpHealed(heal);
            point.setCumulativeDamage(totalDamage);
            point.setCumulativeDamageTaken(damageTaken);
            point.setCumulativeHealing(hpHealed);
            point.addEventNote(note);
            timeSeries.add(point);

            if (timeSeries.size() > SERIES_CAP)
            {
                compactSeries();
            }
        }
        else
        {
            point = timeSeries.get(timeSeries.size() - 1);
            point.setDamageDealt(point.getDamageDealt() + dmg);
            if (style == CombatStyle.MELEE) point.setMeleeDamage(point.getMeleeDamage() + dmg);
            else if (style == CombatStyle.RANGED) point.setRangedDamage(point.getRangedDamage() + dmg);
            else if (style == CombatStyle.MAGIC) point.setMagicDamage(point.getMagicDamage() + dmg);
            else if (style != null && style.isStatusEffect()) point.setStatusDamage(point.getStatusDamage() + dmg);
            else if (style == CombatStyle.THRALL) point.setThrallDamage(point.getThrallDamage() + dmg);
            else if (style == CombatStyle.CANNON) point.setCannonDamage(point.getCannonDamage() + dmg);

            point.setDamageTaken(point.getDamageTaken() + tkn);
            point.setHpHealed(point.getHpHealed() + heal);
            point.setCumulativeDamage(totalDamage);
            point.setCumulativeDamageTaken(damageTaken);
            point.setCumulativeHealing(hpHealed);
            point.addEventNote(note);
        }

        // Rolling average over the configured window. The window is in seconds; convert to
        // points using the current bucket stride, and normalise by seconds (not point count) so
        // the value is a true per-second rate regardless of bucket width.
        int windowPoints = Math.max(1, rollingWindowSeconds / Math.max(1, seriesStride));
        int window = Math.min(windowPoints, timeSeries.size());
        long sumDmg = 0;
        long sumMelee = 0;
        long sumRanged = 0;
        long sumMagic = 0;
        long sumStatus = 0;
        long sumTkn = 0;
        long sumHeal = 0;
        for (int i = timeSeries.size() - window; i < timeSeries.size(); i++)
        {
            CombatTimeSeriesPoint p = timeSeries.get(i);
            sumDmg += p.getDamageDealt();
            sumMelee += p.getMeleeDamage();
            sumRanged += p.getRangedDamage();
            sumMagic += p.getMagicDamage();
            sumStatus += p.getStatusDamage();
            sumTkn += p.getDamageTaken();
            sumHeal += p.getHpHealed();
        }
        // Divide by the FULL configured window (not just the points seen so far) so a big first
        // hit early in the fight reads as a low 6-s-averaged rate, not a spike, and the line
        // ramps up. This is how a smoothed DPS curve behaves.
        double perSec = 1.0 / (windowPoints * (double) Math.max(1, seriesStride));
        point.setRollingDps(sumDmg * perSec);
        point.setRollingMeleeDps(sumMelee * perSec);
        point.setRollingRangedDps(sumRanged * perSec);
        point.setRollingMagicDps(sumMagic * perSec);
        point.setRollingStatusDps(sumStatus * perSec);
        point.setRollingDtps(sumTkn * perSec);
        point.setRollingHps(sumHeal * perSec);
    }

    /** Halve the time-series resolution in place: merge consecutive pairs, keep the later second. */
    private void compactSeries()
    {
        List<CombatTimeSeriesPoint> merged = new ArrayList<>(timeSeries.size() / 2 + 1);
        for (int i = 0; i < timeSeries.size(); i += 2)
        {
            CombatTimeSeriesPoint a = timeSeries.get(i);
            CombatTimeSeriesPoint b = (i + 1 < timeSeries.size()) ? timeSeries.get(i + 1) : null;
            if (b == null)
            {
                merged.add(a);
                break;
            }
            b.setDamageDealt(a.getDamageDealt() + b.getDamageDealt());
            b.setMeleeDamage(a.getMeleeDamage() + b.getMeleeDamage());
            b.setRangedDamage(a.getRangedDamage() + b.getRangedDamage());
            b.setMagicDamage(a.getMagicDamage() + b.getMagicDamage());
            b.setStatusDamage(a.getStatusDamage() + b.getStatusDamage());
            b.setThrallDamage(a.getThrallDamage() + b.getThrallDamage());
            b.setCannonDamage(a.getCannonDamage() + b.getCannonDamage());
            b.setDamageTaken(a.getDamageTaken() + b.getDamageTaken());
            b.setHpHealed(a.getHpHealed() + b.getHpHealed());
            b.setRollingDps(Math.max(a.getRollingDps(), b.getRollingDps()));
            b.setRollingMeleeDps(Math.max(a.getRollingMeleeDps(), b.getRollingMeleeDps()));
            b.setRollingRangedDps(Math.max(a.getRollingRangedDps(), b.getRollingRangedDps()));
            b.setRollingMagicDps(Math.max(a.getRollingMagicDps(), b.getRollingMagicDps()));
            b.setRollingStatusDps(Math.max(a.getRollingStatusDps(), b.getRollingStatusDps()));
            b.setRollingDtps(Math.max(a.getRollingDtps(), b.getRollingDtps()));
            b.setRollingHps(Math.max(a.getRollingHps(), b.getRollingHps()));
            // cumulative* and second are already the later point's - keep b as-is for those.
            merged.add(b);
        }
        timeSeries.clear();
        timeSeries.addAll(merged);
        seriesStride *= 2;
    }

    // Peak = the highest point of the *smoothed* line. A single big hit is a max hit, not a
    // DPS peak, so raw per-bucket damage is deliberately not considered here.
    public synchronized double getPeakDps()
    {
        double peak = 0.0;
        for (CombatTimeSeriesPoint p : timeSeries)
        {
            if (p.getRollingDps() > peak) peak = p.getRollingDps();
        }
        return peak;
    }

    public synchronized double getPeakDtps()
    {
        double peak = 0.0;
        for (CombatTimeSeriesPoint p : timeSeries)
        {
            if (p.getRollingDtps() > peak) peak = p.getRollingDtps();
        }
        return peak;
    }

    public synchronized double getPeakHps()
    {
        double peak = 0.0;
        for (CombatTimeSeriesPoint p : timeSeries)
        {
            if (p.getRollingHps() > peak) peak = p.getRollingHps();
        }
        return peak;
    }




    public synchronized void recordDamageDealt(CombatStyle style, int amount, String weaponName, int tick)
    {
        recordDamageDealt(style, amount, weaponName, tick, false, null);
    }

    public synchronized void recordDamageDealt(CombatStyle style, int amount, String weaponName, int tick,
        boolean special, String target)
    {
        // One hitsplat == one attack (the common case). Multi-hitsplat weapons come in via
        // recordExtraSplat so they don't each count as a separate attack.
        recordAttempt(amount > 0);
        accrueDamage(style, amount, weaponName, tick, true, special, target);
    }

    /** Book one attack attempt; {@code landed} true iff it dealt damage. */
    public synchronized void recordAttempt(boolean landed)
    {
        attackAttempts++;
        if (landed)
        {
            successfulHits++;
        }
    }

    /**
     * A further hitsplat of an attack already counted by {@link #recordAttempt} (Scythe, claws,
     * blowpipe, Karil's, ...). Adds damage without a new attempt; {@code creditsHit} upgrades a
     * so-far-missed attack to a hit when this splat is the first one to land.
     */
    public synchronized void recordExtraSplat(CombatStyle style, int amount, String weaponName, int tick, boolean creditsHit)
    {
        recordExtraSplat(style, amount, weaponName, tick, creditsHit, false, null);
    }

    public synchronized void recordExtraSplat(CombatStyle style, int amount, String weaponName, int tick,
        boolean creditsHit, boolean special, String target)
    {
        if (creditsHit && amount > 0)
        {
            successfulHits++;
        }
        accrueDamage(style, amount, weaponName, tick, false, special, target);
    }

    private void accrueDamage(CombatStyle style, int amount, String weaponName, int tick,
        boolean freshAttack, boolean special, String target)
    {
        totalDamage += amount;

        WeaponDamageAccumulator acc = (weaponName != null && !weaponName.isEmpty())
            ? weaponMap.computeIfAbsent(weaponName, k -> new WeaponDamageAccumulator(weaponName))
            : null;
        if (acc != null && freshAttack)
        {
            acc.recordAttack(amount > 0, special);
        }

        if (amount > 0)
        {
            damageByStyle.merge(style, (long) amount, Long::sum);

            if (acc != null)
            {
                acc.recordSplat(amount, special, target);
            }
            if (special)
            {
                // Every splat of a spec counts toward the headline spec-damage total, matching the
                // per-weapon accumulator above. (recordSpecialAttack() only bumps the count, once.)
                specialAttackDamage += amount;
            }

            if (maxHitDealt == null || amount > maxHitDealt.getAmount())
            {
                maxHitDealt = new HitRecord(amount, style, weaponName, tick);
            }
        }
        updateDominantStyle();
    }

    public synchronized void recordMiss(CombatStyle style)
    {
        attackAttempts++;
    }

    /** A miss attributable to a specific source, so per-source accuracy stays honest. */
    public synchronized void recordMiss(CombatStyle style, String weaponName, boolean special)
    {
        attackAttempts++;
        if (weaponName != null && !weaponName.isEmpty())
        {
            weaponMap.computeIfAbsent(weaponName, k -> new WeaponDamageAccumulator(weaponName))
                .recordAttack(false, special);
        }
    }

    /**
     * Book the ticks between two consecutive attacks: {@code activeTicks} counts as time spent
     * attacking (capped at one weapon cycle), {@code lostTicks} is the pause beyond that. Feeds
     * {@link #getAttackUptimePercent()} and {@link #getActiveDps()}.
     */
    public synchronized void recordAttackCycle(int activeTicks, int lostTicks)
    {
        if (activeTicks > 0)
        {
            attackUptimeTicks += activeTicks;
        }
        if (lostTicks > 0)
        {
            lostCombatTicks += lostTicks;
        }
    }

    /**
     * Count the spec just recorded (via {@link #recordDamageDealt}/{@link #recordExtraSplat} with
     * {@code special=true}, or {@link #recordMiss}). Call once per spec, on the first splat. The
     * spec's damage - every splat of it - is accrued in {@code accrueDamage}. A missed spec still
     * counts.
     */
    public synchronized void recordSpecialAttack()
    {
        specialAttacksCount++;
    }

    public synchronized void recordStatusDamage(CombatStyle style, int amount, String sourceName, int tick)
    {
        recordStatusDamage(style, amount, sourceName, tick, null);
    }

    /**
     * Damage-over-time tick (poison / venom / burn / bleed). Rolls into the unified total, the
     * per-style breakdown, and its own "source" row so it shows in the damage breakdown - but it is
     * NOT an attack attempt, so it never moves hit accuracy.
     */
    public synchronized void recordStatusDamage(CombatStyle style, int amount, String sourceName, int tick, String target)
    {
        if (amount <= 0)
        {
            return;
        }
        totalDamage += amount;
        damageByStyle.merge(style, (long) amount, Long::sum);
        if (sourceName != null && !sourceName.isEmpty())
        {
            weaponMap.computeIfAbsent(sourceName, k -> new WeaponDamageAccumulator(sourceName))
                .recordSplat(amount, false, target);
        }
        updateDominantStyle();
    }

    public synchronized void recordDamageTaken(CombatStyle style, int amount, String sourceName, int tick)
    {
        damageTaken += amount;
        if (amount > 0)
        {
            if (style != null)
            {
                damageTakenByStyle.merge(style, (long) amount, Long::sum);
            }
            String src = (sourceName != null && !sourceName.isEmpty()) ? sourceName : "Enemy";
            if (damageTakenBySource.containsKey(src) || damageTakenBySource.size() < 40)
            {
                damageTakenBySource.merge(src, (long) amount, Long::sum);
                damageTakenHitsBySource.merge(src, 1, Integer::sum);
            }

            // Only real hits set the "biggest hit taken" record - a blocked / 0 hit must not
            // stick a HitRecord(0, ...) in there as the first entry.
            if (maxHitTaken == null || amount > maxHitTaken.getAmount())
            {
                maxHitTaken = new HitRecord(amount, style, sourceName, tick);
            }
        }
    }

    public synchronized List<StyleDamageEntry> getDamageTakenByStyle()
    {
        List<StyleDamageEntry> entries = new ArrayList<>();
        damageTakenByStyle.forEach((style, dmg) -> {
            if (dmg > 0)
            {
                entries.add(new StyleDamageEntry(style, style.getDisplayName(), dmg, style.getPrimaryColor()));
            }
        });
        entries.sort((a, b) -> Long.compare(b.getDamage(), a.getDamage()));
        return entries;
    }

    public synchronized Map<String, Long> getDamageTakenBySource()
    {
        return new java.util.LinkedHashMap<>(damageTakenBySource);
    }

    /** Incoming-hit count per source name, paired with {@link #getDamageTakenBySource()}. */
    public synchronized Map<String, Integer> getDamageTakenHitsBySource()
    {
        return new java.util.LinkedHashMap<>(damageTakenHitsBySource);
    }

    public synchronized void recordConsumable(int itemId, String itemName, int quantity, long cost)
    {
        totalGpCost += cost;
        consumableMap.computeIfAbsent(itemId, k -> new ConsumableAccumulator(itemId, itemName))
            .add(quantity, cost);
    }


    public synchronized long getStyleDamage(CombatStyle style)
    {
        if (style == null) return 0;
        return damageByStyle.getOrDefault(style, 0L);
    }

    public synchronized List<StyleDamageEntry> getStyleBreakdown()
    {
        List<StyleDamageEntry> entries = new ArrayList<>();
        damageByStyle.forEach((style, dmg) -> {
            if (dmg > 0)
            {
                entries.add(new StyleDamageEntry(style, style.getDisplayName(), dmg, style.getPrimaryColor()));
            }
        });
        entries.sort((a, b) -> Long.compare(b.getDamage(), a.getDamage()));
        return entries;
    }

    public synchronized List<WeaponAbilityEntry> getWeaponBreakdown()
    {
        List<WeaponAbilityEntry> entries = new ArrayList<>();
        weaponMap.values().forEach(w ->
        {
            LinkedHashMap<String, long[]> targets = new LinkedHashMap<>();
            w.byTarget.forEach((k, v) -> targets.put(k, new long[]{v[0], v[1]}));
            entries.add(new WeaponAbilityEntry(w.weaponName, w.totalDamage, w.hitCount, w.maxHit, w.minHit,
                w.attempts, w.landedAttempts, w.specDamage, w.specHits, w.specAttempts, w.specMax, w.specMin,
                w.hist.clone(), targets));
        });
        entries.sort((a, b) -> Long.compare(b.getTotalDamage(), a.getTotalDamage()));
        return entries;
    }

    public synchronized List<ConsumableUsageEntry> getConsumablesUsed()
    {
        List<ConsumableUsageEntry> entries = new ArrayList<>();
        consumableMap.values().forEach(c -> entries.add(new ConsumableUsageEntry(c.itemId, c.itemName, c.quantity, c.totalCost)));
        entries.sort((a, b) -> Long.compare(b.getTotalCost(), a.getTotalCost()));
        return entries;
    }

    /**
     * Fold another bucket's totals into this one - used to merge consecutive same-target kills into
     * one "Gargoyle x47" segment. Every scalar sums, the biggest-hit
     * records take the max, and the fight graph is replaced with the newer pull's (a concatenated
     * grind graph isn't meaningful; the latest kill is the representative shape).
     */
    public synchronized void absorb(EntityCombatStats o)
    {
        if (o == null || o == this)
        {
            return;
        }
        absorbScalarsOnly(o);
        synchronized (o)
        {
            if (!o.timeSeries.isEmpty())
            {
                timeSeries.clear();
                timeSeries.addAll(o.timeSeries);
                seriesStride = o.seriesStride;
            }
            timelineEvents.addAll(o.timelineEvents);
            while (timelineEvents.size() > maxTimelineEvents)
            {
                timelineEvents.remove(0);
            }
        }
    }

    /**
     * Fold another bucket's totals into this one WITHOUT copying its action-ledger timeline or its
     * fight time-series. For the per-frame party GROUP assembly, which is rebuilt many times a
     * second and keeps no merged event log of its own - the full {@link #absorb} lays the
     * timeline / series on top for the one-shot trash-merge.
     */
    public synchronized void absorbScalarsOnly(EntityCombatStats o)
    {
        if (o == null || o == this)
        {
            return;
        }
        synchronized (o)
        {
            totalDamage += o.totalDamage;
            damageTaken += o.damageTaken;
            hpHealed += o.hpHealed;
            hpOverhealed += o.hpOverhealed;
            potionsDrunkCount += o.potionsDrunkCount;
            foodEatenCount += o.foodEatenCount;
            attackAttempts += o.attackAttempts;
            successfulHits += o.successfulHits;
            prayerPointsRestored += o.prayerPointsRestored;
            attackUptimeTicks += o.attackUptimeTicks;
            lostCombatTicks += o.lostCombatTicks;
            specialAttacksCount += o.specialAttacksCount;
            specialAttackDamage += o.specialAttackDamage;
            totalGpCost += o.totalGpCost;

            o.damageByStyle.forEach((k, v) -> damageByStyle.merge(k, v, Long::sum));
            o.damageTakenByStyle.forEach((k, v) -> damageTakenByStyle.merge(k, v, Long::sum));
            o.damageTakenBySource.forEach((k, v) ->
            {
                if (damageTakenBySource.containsKey(k) || damageTakenBySource.size() < 40)
                {
                    damageTakenBySource.merge(k, v, Long::sum);
                    damageTakenHitsBySource.merge(k, o.damageTakenHitsBySource.getOrDefault(k, 0), Integer::sum);
                }
            });
            o.weaponMap.forEach((name, ow) ->
                weaponMap.computeIfAbsent(name, WeaponDamageAccumulator::new).absorb(ow));
            o.consumableMap.forEach((id, oc) ->
            {
                ConsumableAccumulator c = consumableMap.get(id);
                if (c == null)
                {
                    c = new ConsumableAccumulator(id, oc.itemName);
                    consumableMap.put(id, c);
                }
                c.quantity += oc.quantity;
                c.totalCost += oc.totalCost;
            });

            if (o.maxHitDealt != null && (maxHitDealt == null || o.maxHitDealt.getAmount() > maxHitDealt.getAmount()))
            {
                maxHitDealt = o.maxHitDealt;
            }
            if (o.maxHitTaken != null && (maxHitTaken == null || o.maxHitTaken.getAmount() > maxHitTaken.getAmount()))
            {
                maxHitTaken = o.maxHitTaken;
            }
        }
        updateDominantStyle();
    }

    private void updateDominantStyle()
    {
        CombatStyle best = CombatStyle.MELEE;
        long max = -1;
        for (Map.Entry<CombatStyle, Long> entry : damageByStyle.entrySet())
        {
            if (entry.getValue() > max)
            {
                max = entry.getValue();
                best = entry.getKey();
            }
        }
        this.dominantStyle = best;
    }

    /** Hit-size histogram bins; the last bin is "that size and above". */
    static final int HIST_BINS = 128;
    private static final int WEAPON_TARGET_CAP = 24;

    private static class WeaponDamageAccumulator
    {
        final String weaponName;
        long totalDamage = 0;
        int hitCount = 0;
        int maxHit = 0;
        int minHit = 0;               // smallest landed hit (>0); 0 == none yet
        int attempts = 0;             // swings made (a multi-splat swing counts once)
        int landedAttempts = 0;       // swings that landed >=1 splat

        long specDamage = 0;
        int specHits = 0;
        int specAttempts = 0;
        int specMax = 0;
        int specMin = 0;

        final int[] hist = new int[HIST_BINS];
        final Map<String, long[]> byTarget = new LinkedHashMap<>();   // name -> {damage, hits}

        WeaponDamageAccumulator(String weaponName)
        {
            this.weaponName = weaponName;
        }

        /** One swing with this source. {@code landed} iff it dealt >=1 damage. */
        void recordAttack(boolean landed, boolean special)
        {
            attempts++;
            if (landed)
            {
                landedAttempts++;
            }
            if (special)
            {
                specAttempts++;
            }
        }

        /** One landed splat (amount > 0). {@code target} may be null. */
        void recordSplat(int amount, boolean special, String target)
        {
            totalDamage += amount;
            hitCount++;
            if (amount > maxHit)
            {
                maxHit = amount;
            }
            if (minHit == 0 || amount < minHit)
            {
                minHit = amount;
            }
            hist[Math.min(amount, HIST_BINS - 1)]++;
            if (special)
            {
                specDamage += amount;
                specHits++;
                if (amount > specMax)
                {
                    specMax = amount;
                }
                if (specMin == 0 || amount < specMin)
                {
                    specMin = amount;
                }
            }
            if (target != null && !target.isEmpty()
                && (byTarget.containsKey(target) || byTarget.size() < WEAPON_TARGET_CAP))
            {
                long[] t = byTarget.computeIfAbsent(target, k -> new long[2]);
                t[0] += amount;
                t[1]++;
            }
        }

        /** Legacy single-arg splat (thrall / cannon / remote), no attempt or spec bookkeeping. */
        void recordHit(int amount)
        {
            recordSplat(amount, false, null);
        }

        void absorb(WeaponDamageAccumulator o)
        {
            totalDamage += o.totalDamage;
            hitCount += o.hitCount;
            attempts += o.attempts;
            landedAttempts += o.landedAttempts;
            maxHit = Math.max(maxHit, o.maxHit);
            if (o.minHit > 0 && (minHit == 0 || o.minHit < minHit))
            {
                minHit = o.minHit;
            }
            specDamage += o.specDamage;
            specHits += o.specHits;
            specAttempts += o.specAttempts;
            specMax = Math.max(specMax, o.specMax);
            if (o.specMin > 0 && (specMin == 0 || o.specMin < specMin))
            {
                specMin = o.specMin;
            }
            for (int i = 0; i < HIST_BINS; i++)
            {
                hist[i] += o.hist[i];
            }
            o.byTarget.forEach((k, v) ->
            {
                if (byTarget.containsKey(k) || byTarget.size() < WEAPON_TARGET_CAP)
                {
                    long[] t = byTarget.computeIfAbsent(k, x -> new long[2]);
                    t[0] += v[0];
                    t[1] += v[1];
                }
            });
        }
    }

    private static class ConsumableAccumulator
    {
        final int itemId;
        final String itemName;
        int quantity = 0;
        long totalCost = 0;

        ConsumableAccumulator(int itemId, String itemName)
        {
            this.itemId = itemId;
            this.itemName = itemName;
        }

        void add(int qty, long cost)
        {
            this.quantity += qty;
            this.totalCost += cost;
        }
    }
}
