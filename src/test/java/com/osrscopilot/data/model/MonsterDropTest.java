package com.osrscopilot.data.model;

import org.junit.Assert;
import org.junit.Test;

public class MonsterDropTest
{
    private static int[] qty(String q)
    {
        MonsterDrop d = MonsterDrop.builder().quantity(q).build();
        return new int[]{d.getMinQuantity(), d.getMaxQuantity()};
    }

    @Test
    public void testQuantityParsingEdgeCases()
    {
        Assert.assertArrayEquals(new int[]{1, 1}, qty(null));
        Assert.assertArrayEquals(new int[]{1, 1}, qty(""));
        Assert.assertArrayEquals(new int[]{5, 5}, qty("5"));
        Assert.assertArrayEquals(new int[]{1, 3}, qty("1-3"));
        Assert.assertArrayEquals(new int[]{1, 3}, qty("1–3"));       // en-dash
        Assert.assertArrayEquals(new int[]{280, 420}, qty("280,420"));
        Assert.assertArrayEquals(new int[]{280, 420}, qty("280; 420"));
        Assert.assertArrayEquals(new int[]{1, 2}, qty("1 ,2"));
        Assert.assertArrayEquals(new int[]{8, 12}, qty("8;10;12"));
        Assert.assertArrayEquals(new int[]{4, 11}, qty("4-8,7-11"));
        Assert.assertArrayEquals(new int[]{1, 1}, qty("unknown"));
        Assert.assertArrayEquals(new int[]{100, 100}, qty("100 (noted)"));
    }

    @Test
    public void testHasQuantityRange()
    {
        Assert.assertFalse(MonsterDrop.builder().quantity("5").build().hasQuantityRange());
        Assert.assertTrue(MonsterDrop.builder().quantity("1-3").build().hasQuantityRange());
        Assert.assertFalse(MonsterDrop.builder().quantity(null).build().hasQuantityRange());
    }

    @Test
    public void testPerKillChanceFoldsInRolls()
    {
        // rolls = 1 -> per-kill == per-roll
        MonsterDrop single = MonsterDrop.builder().rarity(1.0 / 512).rolls(1).build();
        Assert.assertEquals(1.0 / 512, single.getPerKillChance(), 1e-12);
        Assert.assertEquals(512, single.getPerKillDenominator());

        // rolls = 2 at 1/128 per roll -> 1 - (127/128)^2 ~= 1/64.25
        MonsterDrop twoRolls = MonsterDrop.builder().rarity(1.0 / 128).rolls(2).build();
        double expected = 1.0 - Math.pow(1.0 - 1.0 / 128, 2);
        Assert.assertEquals(expected, twoRolls.getPerKillChance(), 1e-12);
        Assert.assertTrue("2 rolls must beat the per-roll rate", twoRolls.getPerKillChance() > 1.0 / 128);
        Assert.assertEquals(64, twoRolls.getPerKillDenominator());

        // rolls unset is treated as 1
        Assert.assertEquals(1, MonsterDrop.builder().rarity(0.5).build().getEffectiveRolls());
    }

    @Test
    public void testPerKillChanceUnknownRate()
    {
        Assert.assertEquals(0.0, MonsterDrop.builder().rarity(0).build().getPerKillChance(), 0);
        Assert.assertEquals(0.0, MonsterDrop.builder().rarity(-1).rarityFraction("Varies").build().getPerKillChance(), 0);
        Assert.assertEquals(0, MonsterDrop.builder().rarity(0).build().getPerKillDenominator());
    }

    @Test
    public void testPerKillChanceRecoversRateFromFractionString()
    {
        // rarity missing, but rarityFraction usable (comma-tolerant)
        MonsterDrop d = MonsterDrop.builder().rarity(0).rarityFraction("1/5,000").rolls(1).build();
        Assert.assertEquals(1.0 / 5000, d.getPerKillChance(), 1e-12);
    }

    @Test
    public void testFormattedRarityUnchangedAndPerRoll()
    {
        Assert.assertEquals("1/512", MonsterDrop.builder().rarityFraction("1/512").rarity(1.0 / 512).rolls(2).build().getFormattedRarity());
        Assert.assertEquals("Varies", MonsterDrop.builder().rarity(0).build().getFormattedRarity());
        Assert.assertEquals("Always (1/1)", MonsterDrop.builder().rarity(1.0).build().getFormattedRarity());
        Assert.assertEquals("1/128", MonsterDrop.builder().rarity(1.0 / 128).build().getFormattedRarity());
    }
}
