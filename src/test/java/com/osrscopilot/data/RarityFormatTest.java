package com.osrscopilot.data;

import com.osrscopilot.data.model.MonsterDrop;
import org.junit.Assert;
import org.junit.Test;

public class RarityFormatTest
{
    @Test
    public void testPerKillFoldsInRollsForMultiRollDrops()
    {
        // Zulrah's tanzanite fang shape: wiki quotes 1/1,024 per roll, rolled twice a kill.
        MonsterDrop fang = MonsterDrop.builder()
            .name("Tanzanite fang")
            .rarity(1.0 / 1024.0)
            .rarityFraction("1/1,024")
            .rolls(2)
            .build();

        // Per-kill denominator ~= 1 / (1 - (1023/1024)^2) ~= 512.
        Assert.assertEquals(512, fang.getPerKillDenominator());
        Assert.assertEquals("1/512", RarityFormat.perKill(fang));

        // The raw per-roll string is still available, and shows up only as the secondary note.
        Assert.assertEquals("1/1,024", fang.getFormattedRarity());
        Assert.assertEquals(" (per roll 1/1,024)", RarityFormat.perRollNote(fang));
    }

    @Test
    public void testPerKillSingleRollMatchesPerRoll()
    {
        MonsterDrop whip = MonsterDrop.builder()
            .name("Abyssal whip")
            .rarity(1.0 / 512.0)
            .rarityFraction("1/512")
            .rolls(1)
            .build();

        Assert.assertEquals("1/512", RarityFormat.perKill(whip));
        // No per-roll note for a single-roll drop - it would just repeat the headline.
        Assert.assertEquals("", RarityFormat.perRollNote(whip));
    }

    @Test
    public void testDigitGrouping()
    {
        MonsterDrop dwh = MonsterDrop.builder().name("Dragon warhammer").rarity(1.0 / 5000.0).build();
        Assert.assertEquals("1/5,000", RarityFormat.perKill(dwh));

        MonsterDrop jar = MonsterDrop.builder().name("Jar of swamp").rarity(1.0 / 12500.0).build();
        Assert.assertEquals("1/12,500", RarityFormat.perKill(jar));
    }

    @Test
    public void testVariesAndUnknownPassthrough()
    {
        Assert.assertEquals("Varies", RarityFormat.perKill(null));
        Assert.assertEquals("Varies", RarityFormat.perKill(MonsterDrop.builder().rarity(0).build()));
        Assert.assertEquals("Varies",
            RarityFormat.perKill(MonsterDrop.builder().rarity(-1).rarityFraction("Varies").build()));
        Assert.assertEquals("", RarityFormat.perRollNote(MonsterDrop.builder().rarity(0).build()));
    }

    @Test
    public void testAlwaysDrop()
    {
        Assert.assertEquals("Always (1/1)", RarityFormat.perKill(MonsterDrop.builder().rarity(1.0).build()));
    }

    @Test
    public void testColourIsNeutralForUnknownRateNotRarestTier()
    {
        // The old getRarityColor(0.0) fell through to the rarest-tier purple - regression guard.
        Assert.assertEquals(RarityFormat.UNKNOWN, RarityFormat.colorForChance(0.0));
        Assert.assertEquals(RarityFormat.UNKNOWN, RarityFormat.colorForChance(-1.0));
        Assert.assertEquals(RarityFormat.UNKNOWN,
            RarityFormat.perKillColor(MonsterDrop.builder().rarity(0).build()));
        Assert.assertNotEquals(RarityFormat.VERY_RARE, RarityFormat.colorForChance(0.0));
    }

    @Test
    public void testColourTiersByPerKillChance()
    {
        Assert.assertEquals(RarityFormat.ALWAYS, RarityFormat.colorForChance(1.0));
        Assert.assertEquals(RarityFormat.COMMON, RarityFormat.colorForChance(1.0 / 10.0));
        Assert.assertEquals(RarityFormat.UNCOMMON, RarityFormat.colorForChance(1.0 / 64.0));
        Assert.assertEquals(RarityFormat.RARE, RarityFormat.colorForChance(1.0 / 512.0));
        Assert.assertEquals(RarityFormat.VERY_RARE, RarityFormat.colorForChance(1.0 / 5000.0));

        // A multi-roll drop colours by its per-kill chance, not its rarer per-roll figure:
        // 1/128 per roll x2 rolls ~= 1/64 per kill -> UNCOMMON, not UNCOMMON->RARE boundary.
        MonsterDrop twoRoll = MonsterDrop.builder().rarity(1.0 / 128.0).rolls(2).build();
        Assert.assertEquals(RarityFormat.UNCOMMON, RarityFormat.perKillColor(twoRoll));
    }
}
