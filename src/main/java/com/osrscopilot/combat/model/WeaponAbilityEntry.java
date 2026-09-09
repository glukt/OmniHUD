package com.osrscopilot.combat.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One "source" of your damage - a weapon, spell, or damage-over-time - with the full breakdown the
 * Source Details window needs: hit/attempt counts, min/max/avg, the special-attack subset, a
 * hit-size histogram, and how the damage split across the enemies it landed on.
 */
public class WeaponAbilityEntry
{
    private final String weaponName;
    private final long totalDamage;
    private final int hitCount;
    private final int maxHit;
    private final int minHit;
    private final int attempts;
    private final int landedAttempts;

    private final long specDamage;
    private final int specHits;
    private final int specAttempts;
    private final int specMax;
    private final int specMin;

    // hist[i] = number of landed hits of size i; the last bin is "i and above".
    private final int[] histogram;
    // target name -> {damage, hits}, in first-seen order.
    private final Map<String, long[]> targetBreakdown;

    public WeaponAbilityEntry(String weaponName, long totalDamage, int hitCount, int maxHit)
    {
        this(weaponName, totalDamage, hitCount, maxHit, 0, hitCount, hitCount,
            0, 0, 0, 0, 0, null, null);
    }

    public WeaponAbilityEntry(String weaponName, long totalDamage, int hitCount, int maxHit, int minHit,
        int attempts, int landedAttempts, long specDamage, int specHits, int specAttempts, int specMax,
        int specMin, int[] histogram, Map<String, long[]> targetBreakdown)
    {
        this.weaponName = weaponName;
        this.totalDamage = totalDamage;
        this.hitCount = hitCount;
        this.maxHit = maxHit;
        this.minHit = minHit;
        this.attempts = attempts;
        this.landedAttempts = landedAttempts;
        this.specDamage = specDamage;
        this.specHits = specHits;
        this.specAttempts = specAttempts;
        this.specMax = specMax;
        this.specMin = specMin;
        this.histogram = histogram;
        this.targetBreakdown = targetBreakdown == null ? new LinkedHashMap<>() : targetBreakdown;
    }

    public String getWeaponName()
    {
        return weaponName;
    }

    public long getTotalDamage()
    {
        return totalDamage;
    }

    public int getHitCount()
    {
        return hitCount;
    }

    public int getMaxHit()
    {
        return maxHit;
    }

    public int getMinHit()
    {
        return minHit;
    }

    public int getAvgHit()
    {
        return hitCount > 0 ? (int) (totalDamage / hitCount) : 0;
    }

    /** Attacks made with this source (a multi-hitsplat swing is one attempt). */
    public int getAttempts()
    {
        return attempts;
    }

    /** Attempts that landed at least one splat. */
    public int getLandedAttempts()
    {
        return landedAttempts;
    }

    public double getAccuracyPct()
    {
        return attempts > 0 ? (landedAttempts * 100.0) / attempts : 0.0;
    }

    public long getSpecDamage()
    {
        return specDamage;
    }

    public int getSpecHits()
    {
        return specHits;
    }

    public int getSpecAttempts()
    {
        return specAttempts;
    }

    public int getSpecMax()
    {
        return specMax;
    }

    public int getSpecMin()
    {
        return specMin;
    }

    public int getSpecAvg()
    {
        return specHits > 0 ? (int) (specDamage / specHits) : 0;
    }

    public boolean hasSpecial()
    {
        return specAttempts > 0 || specHits > 0;
    }

    // ---- "normal" = everything that isn't a special attack --------------------------------------

    public long getNormalDamage()
    {
        return Math.max(0, totalDamage - specDamage);
    }

    public int getNormalHits()
    {
        return Math.max(0, hitCount - specHits);
    }

    public int getNormalAvg()
    {
        int n = getNormalHits();
        return n > 0 ? (int) (getNormalDamage() / n) : 0;
    }

    /** hist[i] = landed hits of size i; last bin is "that size and above". Never null. */
    public int[] getHistogram()
    {
        return histogram == null ? new int[0] : histogram;
    }

    /** Damage + hit count keyed by enemy name, in first-seen order. Never null. */
    public Map<String, long[]> getTargetBreakdown()
    {
        return Collections.unmodifiableMap(targetBreakdown);
    }
}
