package com.osrscopilot.combat.model;

import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The per-source detail that feeds the "Damage Sources" window: min/max/accuracy, the
 * special-attack split, the hit-size histogram, and the per-target breakdown.
 */
public class WeaponSourceDetailTest
{
    private static WeaponAbilityEntry source(EntityCombatStats s, String name)
    {
        return s.getWeaponBreakdown().stream()
            .filter(w -> w.getWeaponName().equals(name))
            .findFirst().orElseThrow(() -> new AssertionError("no source " + name));
    }

    @Test
    public void capturesAttemptsHitsMinMaxAndAccuracy()
    {
        EntityCombatStats s = new EntityCombatStats("Me");
        s.recordDamageDealt(CombatStyle.MELEE, 12, "Abyssal whip", 1, false, "Nechryael");
        s.recordDamageDealt(CombatStyle.MELEE, 4, "Abyssal whip", 2, false, "Nechryael");
        s.recordDamageDealt(CombatStyle.MELEE, 20, "Abyssal whip", 3, false, "Nechryael");
        s.recordMiss(CombatStyle.MELEE, "Abyssal whip", false); // a 4th swing, no damage

        WeaponAbilityEntry w = source(s, "Abyssal whip");
        assertEquals(4, w.getAttempts());
        assertEquals(3, w.getLandedAttempts());
        assertEquals(3, w.getHitCount());
        assertEquals(4, w.getMinHit());
        assertEquals(20, w.getMaxHit());
        assertEquals(12, w.getAvgHit());          // (12+4+20)/3
        assertEquals(75.0, w.getAccuracyPct(), 0.01);
    }

    @Test
    public void splitsSpecialFromNormal()
    {
        EntityCombatStats s = new EntityCombatStats("Me");
        s.recordDamageDealt(CombatStyle.MELEE, 10, "Dragon dagger", 1, false, "Zulrah");
        s.recordDamageDealt(CombatStyle.MELEE, 8, "Dragon dagger", 2, false, "Zulrah");
        // a spec = two splats of one swing: first via recordDamageDealt, extra via recordExtraSplat
        s.recordDamageDealt(CombatStyle.MELEE, 25, "Dragon dagger", 3, true, "Zulrah");
        s.recordExtraSplat(CombatStyle.MELEE, 22, "Dragon dagger", 3, false, true, "Zulrah");

        WeaponAbilityEntry w = source(s, "Dragon dagger");
        assertTrue(w.hasSpecial());
        assertEquals(2, w.getSpecHits());
        assertEquals(47, w.getSpecDamage());
        assertEquals(25, w.getSpecMax());
        assertEquals(2, w.getNormalHits());
        assertEquals(18, w.getNormalDamage());
        assertEquals(1, w.getSpecAttempts());     // one spec swing
    }

    @Test
    public void binsHitsBySizeInTheHistogram()
    {
        EntityCombatStats s = new EntityCombatStats("Me");
        for (int i = 0; i < 3; i++) s.recordDamageDealt(CombatStyle.RANGED, 5, "Twisted bow", i, false, "Olm");
        s.recordDamageDealt(CombatStyle.RANGED, 11, "Twisted bow", 10, false, "Olm");
        s.recordDamageDealt(CombatStyle.RANGED, 44, "Twisted bow", 11, false, "Olm");

        int[] h = source(s, "Twisted bow").getHistogram();
        assertEquals(3, h[5]);
        assertEquals(1, h[11]);
        assertEquals(1, h[44]);
        assertEquals(0, h[6]);
    }

    @Test
    public void splitsDamageAcrossTargets()
    {
        EntityCombatStats s = new EntityCombatStats("Me");
        s.recordDamageDealt(CombatStyle.MAGIC, 15, "Ice Barrage", 1, false, "Monkey (1)");
        s.recordDamageDealt(CombatStyle.MAGIC, 9, "Ice Barrage", 1, false, "Monkey (2)");
        s.recordDamageDealt(CombatStyle.MAGIC, 6, "Ice Barrage", 1, false, "Monkey (2)");

        Map<String, long[]> byTarget = source(s, "Ice Barrage").getTargetBreakdown();
        assertEquals(2, byTarget.size());
        assertEquals(15, byTarget.get("Monkey (1)")[0]);
        assertEquals(1, byTarget.get("Monkey (1)")[1]);
        assertEquals(15, byTarget.get("Monkey (2)")[0]);
        assertEquals(2, byTarget.get("Monkey (2)")[1]);
    }

    @Test
    public void statusDamageShowsAsItsOwnSourceWithoutMovingAccuracy()
    {
        EntityCombatStats s = new EntityCombatStats("Me");
        s.recordDamageDealt(CombatStyle.MELEE, 10, "Abyssal whip", 1, false, "Nechryael");
        int attemptsBefore = s.getAttackAttempts();
        s.recordStatusDamage(CombatStyle.MELEE, 4, "Venom", 2, "Nechryael");
        s.recordStatusDamage(CombatStyle.MELEE, 4, "Venom", 4, "Nechryael");

        WeaponAbilityEntry venom = source(s, "Venom");
        assertEquals(8, venom.getTotalDamage());
        assertEquals(2, venom.getHitCount());
        assertEquals(0, venom.getAttempts());                 // DoT is not a swing
        assertEquals(attemptsBefore, s.getAttackAttempts());  // and never touches accuracy
    }

    @Test
    public void absorbMergesRichWeaponStats()
    {
        EntityCombatStats a = new EntityCombatStats("A");
        a.recordDamageDealt(CombatStyle.MELEE, 20, "Abyssal whip", 1, false, "Dragon (1)");
        a.recordDamageDealt(CombatStyle.MELEE, 8, "Abyssal whip", 2, true, "Dragon (1)");

        EntityCombatStats b = new EntityCombatStats("B");
        b.recordDamageDealt(CombatStyle.MELEE, 3, "Abyssal whip", 1, false, "Dragon (2)");
        b.recordMiss(CombatStyle.MELEE, "Abyssal whip", false);

        a.absorb(b);
        WeaponAbilityEntry w = source(a, "Abyssal whip");
        assertEquals(4, w.getAttempts());          // a: 2 swings; b: 1 swing + 1 miss
        assertEquals(3, w.getLandedAttempts());    // a: 2 landed; b: 1 landed
        assertEquals(3, w.getMinHit());            // b's smaller hit wins
        assertEquals(20, w.getMaxHit());
        assertEquals(1, w.getSpecHits());
        assertEquals(2, w.getTargetBreakdown().size());
        int[] h = w.getHistogram();
        assertEquals(1, h[3]);
        assertEquals(1, h[8]);
        assertEquals(1, h[20]);
    }
}
