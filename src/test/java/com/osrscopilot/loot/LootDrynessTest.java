package com.osrscopilot.loot;

import org.junit.Assert;
import org.junit.Test;

public class LootDrynessTest
{
    @Test
    public void testPStillDry()
    {
        Assert.assertEquals(1.0, LootDryness.pStillDry(0.01, 0), 1e-9);
        Assert.assertEquals(1.0, LootDryness.pStillDry(0.0, 500), 1e-9);
        // 1/128, 128 kills -> (127/128)^128 ~= 0.367
        Assert.assertEquals(Math.pow(127.0 / 128.0, 128), LootDryness.pStillDry(1.0 / 128, 128), 1e-9);
        // stable for huge kill counts (no underflow to a broken value)
        double v = LootDryness.pStillDry(1.0 / 5000, 50_000);
        Assert.assertTrue(v > 0 && v < 0.0001);
    }

    @Test
    public void testDryPercentile()
    {
        Assert.assertEquals(0.0, LootDryness.dryPercentile(0.01, 0), 1e-9);
        // at the expected KC ~63% of players would have it
        double atExpected = LootDryness.dryPercentile(1.0 / 128, 128);
        Assert.assertTrue(atExpected > 60 && atExpected < 66);
        // way dry -> approaches 100
        Assert.assertTrue(LootDryness.dryPercentile(1.0 / 128, 1000) > 99.9);
    }

    @Test
    public void testLuckFactorAndPhrase()
    {
        // 2 drops in 100 kills at 1/128 expected -> observed 1/50 -> ~2.56x lucky
        double f = LootDryness.luckFactor(2, 100, 1.0 / 128);
        Assert.assertEquals(2.56, f, 0.01);
        Assert.assertTrue(LootDryness.luckPhrase(f).contains("lucky"));
        Assert.assertEquals("on rate", LootDryness.luckPhrase(1.0));
        Assert.assertTrue(LootDryness.luckPhrase(0.4).contains("dry"));
        Assert.assertEquals("", LootDryness.luckPhrase(0));
        Assert.assertEquals(0.0, LootDryness.luckFactor(1, 0, 0.1), 0);
    }

    @Test
    public void testExpectedKills()
    {
        Assert.assertEquals(128, LootDryness.expectedKills(1.0 / 128));
        Assert.assertEquals(0, LootDryness.expectedKills(0));
    }

    @Test
    public void testLuckiestPercentile()
    {
        double p = 1.0 / 512;
        // 2 drops in 10 kills at 1/512 -> vanishingly unlikely -> ~0%
        double wild = LootDryness.luckiestPercentile(2, 10, p);
        Assert.assertTrue("two 1/512 in 10 kills is < 0.1%: " + wild, wild < 0.1);
        // 1 drop in exactly the expected KC -> ~63% would have >= 1 by now
        double onPace = LootDryness.luckiestPercentile(1, 512, p);
        Assert.assertTrue(onPace > 55 && onPace < 68);
        // 2 drops in ~1000 kills (expected ~2) -> not remarkable, well above 20%
        Assert.assertTrue(LootDryness.luckiestPercentile(2, 1000, p) > 25);
        // guards
        Assert.assertEquals(100.0, LootDryness.luckiestPercentile(0, 10, p), 0);
        Assert.assertEquals(0.0, LootDryness.luckiestPercentile(5, 3, p), 0);
    }

    @Test
    public void testDryOrLuckyPhrase()
    {
        double p = 1.0 / 512;
        // Two black masks in 10 KC -> the "back to back" case
        Assert.assertTrue(LootDryness.dryOrLuckyPhrase(2, 10, 3, p).startsWith("top "));
        // Zero in 800 KC -> unlucky
        Assert.assertTrue(LootDryness.dryOrLuckyPhrase(0, 800, 800, p).startsWith("unluckier than"));
        // One around the expected mark -> on rate
        Assert.assertEquals("on rate", LootDryness.dryOrLuckyPhrase(1, 500, 5, p));
        Assert.assertEquals("", LootDryness.dryOrLuckyPhrase(0, 0, 0, p));
    }

    /**
     * FIX 3 (T7c): a guaranteed drop ({@code p >= 1}) is never phrased as dry or lucky - no
     * "unluckier than 100%" and no mirror "top &lt;0.1%" - at ANY kill count, drop count or dry
     * streak. An unknown rate ({@code p <= 0}) stays silent too, never a percentile.
     */
    @Test
    public void testGuaranteedDropNeverReadsDryOrLucky()
    {
        for (int kc : new int[]{1, 5, 50, 500, 5000, 50_000})
        {
            for (int drops : new int[]{0, 1, kc, kc + 25})
            {
                for (int dry : new int[]{0, kc})
                {
                    String phrase = LootDryness.dryOrLuckyPhrase(drops, kc, dry, 1.0);
                    Assert.assertEquals("guaranteed -> ALWAYS_DROPS (kc=" + kc + " drops=" + drops
                        + " dry=" + dry + ")", LootDryness.ALWAYS_DROPS, phrase);
                    Assert.assertFalse("no 'top X%' verdict: " + phrase, phrase.startsWith("top "));
                    Assert.assertFalse("no 'unluckier' verdict: " + phrase, phrase.contains("unluckier"));
                    Assert.assertFalse("no 'dry' verdict: " + phrase, phrase.contains("dry"));
                }
            }
        }
        // rarity above 1 (data noise) is still "guaranteed", not a percentile.
        Assert.assertEquals(LootDryness.ALWAYS_DROPS, LootDryness.dryOrLuckyPhrase(0, 300, 300, 1.5));
        // unknown rate -> silent, no percentile.
        Assert.assertEquals("", LootDryness.dryOrLuckyPhrase(0, 900, 900, 0.0));
        Assert.assertEquals("", LootDryness.dryOrLuckyPhrase(0, 900, 900, -1.0));
    }

    /** A non-guaranteed drop you are extremely dry on never rounds up to "unluckier than 100%". */
    @Test
    public void testDryPercentileNeverDisplaysAsOneHundred()
    {
        // 1/128 rate, 2000 dry kills -> dryPercentile rounds to 100.
        String noneYet = LootDryness.dryOrLuckyPhrase(0, 2000, 2000, 1.0 / 128);
        Assert.assertTrue(noneYet, noneYet.startsWith("unluckier than "));
        Assert.assertFalse("no 100% for a non-guaranteed drop: " + noneYet, noneYet.contains("100%"));
        Assert.assertTrue(noneYet, noneYet.contains("99%"));

        String someButDry = LootDryness.dryOrLuckyPhrase(1, 5000, 4000, 1.0 / 512);
        Assert.assertFalse("no 100% in the streak phrase: " + someButDry, someButDry.contains("100%"));
    }
}
