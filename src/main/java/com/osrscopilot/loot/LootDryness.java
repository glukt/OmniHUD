package com.osrscopilot.loot;

/**
 * "How dry am I" maths for the loot tab. Everything takes a per-kill drop probability {@code p}
 * (already roll-adjusted - see {@link com.osrscopilot.data.model.MonsterDrop#getPerKillChance()}).
 */
public final class LootDryness
{
    /**
     * Read for a guaranteed drop ({@code p >= 1}). A 1/1 drop is never "dry" or "lucky", so no
     * percentile applies - phrasing it as "unluckier than 100%" (or the mirror "top &lt;0.1%")
     * is nonsense. Callers treat this like "on rate": neutral colour, no luck verdict.
     */
    public static final String ALWAYS_DROPS = "always drops";

    private LootDryness()
    {
    }

    /** Probability of still having had zero of a {@code p}-per-kill drop after {@code dryKills} kills. */
    public static double pStillDry(double p, int dryKills)
    {
        if (p <= 0 || dryKills <= 0)
        {
            return 1.0;
        }
        if (p >= 1.0)
        {
            return 0.0;
        }
        // (1-p)^dryKills via log-space for stability with large kill counts.
        return Math.exp(dryKills * Math.log1p(-p));
    }

    /**
     * "Drier than X% of players" - the fraction of players who would already have the drop by this
     * point, i.e. the CDF {@code 1 - (1-p)^dryKills}, as a 0..100 percentage.
     */
    public static double dryPercentile(double p, int dryKills)
    {
        return clampPct(100.0 * (1.0 - pStillDry(p, dryKills)));
    }

    /**
     * Observed rate over expected rate: {@code (drops/kills) / p}. &gt; 1 luckier than average,
     * &lt; 1 drier. Returns 0 when it can't be computed.
     */
    public static double luckFactor(int drops, int kills, double p)
    {
        if (kills <= 0 || p <= 0)
        {
            return 0.0;
        }
        return (drops / (double) kills) / p;
    }

    /** Expected kills per drop = {@code round(1/p)}; 0 when the rate is unknown. */
    public static int expectedKills(double p)
    {
        return p > 0 ? (int) Math.round(1.0 / p) : 0;
    }

    /**
     * Binomial upper tail: the percentage of players who, after {@code kills} kills at
     * {@code p}-per-kill, would have obtained a {@code drops}-drop unique <em>at least this many
     * times</em>. Small = you got lucky (e.g. two 1/512 drops in 10 kills → "top ~0.02%").
     * Answers "how notable is it that I got N of this by now". Returns 100 when not computable.
     */
    public static double luckiestPercentile(int drops, int kills, double p)
    {
        if (drops <= 0 || kills <= 0 || p <= 0)
        {
            return 100.0;
        }
        if (p >= 1.0 || drops > kills)
        {
            return 0.0;
        }
        // P(X >= drops) = 1 - sum_{i=0}^{drops-1} C(kills, i) p^i (1-p)^(kills-i)
        // term_i built incrementally from term_0 = (1-p)^kills.
        double q = 1.0 - p;
        double term = Math.exp(kills * Math.log1p(-p)); // (1-p)^kills, stable
        double cdf = term;
        int cap = Math.min(drops - 1, 50);
        for (int i = 1; i <= cap; i++)
        {
            term *= ((double) (kills - i + 1) / i) * (p / q);
            cdf += term;
        }
        return clampPct(100.0 * (1.0 - cdf));
    }

    /** Short phrase for a per-item luck read: dry streak vs over-rate, whichever applies. */
    public static String dryOrLuckyPhrase(int drops, int kills, int dryKills, double p)
    {
        if (p <= 0 || kills <= 0)
        {
            return "";
        }
        if (p >= 1.0)
        {
            // Guaranteed drop - dryness / luck percentiles are meaningless (see ALWAYS_DROPS).
            return ALWAYS_DROPS;
        }
        if (drops == 0)
        {
            double pct = dryPercentile(p, dryKills);
            return pct >= 50 ? "unluckier than " + cappedPct(pct) + "%" : "";
        }
        double expected = kills * p;
        if (drops > expected + 1e-9)
        {
            double top = luckiestPercentile(drops, kills, p);
            if (top <= 35)
            {
                return "top " + fmtPct(top);
            }
        }
        else if (dryKills > 0)
        {
            double pct = dryPercentile(p, dryKills);
            if (pct >= 60)
            {
                return dryKills + " dry (unluckier than " + cappedPct(pct) + "%)";
            }
        }
        return "on rate";
    }

    /** Never show a whole-number percentile of 100 for a non-guaranteed drop - cap at 99. */
    private static long cappedPct(double pct)
    {
        return Math.min(99L, Math.round(pct));
    }

    private static String fmtPct(double v)
    {
        if (v >= 1)
        {
            return Math.round(v) + "%";
        }
        if (v >= 0.1)
        {
            return String.format("%.1f%%", v);
        }
        return "<0.1%";
    }

    /** Short human phrase for a luck factor, e.g. "3.1x lucky" / "on rate" / "0.4x (dry)". */
    public static String luckPhrase(double factor)
    {
        if (factor <= 0)
        {
            return "";
        }
        if (factor >= 1.15)
        {
            return trim(factor) + "x lucky";
        }
        if (factor <= 0.85)
        {
            return trim(factor) + "x (dry)";
        }
        return "on rate";
    }

    private static String trim(double v)
    {
        return (Math.abs(v - Math.rint(v)) < 0.05)
            ? Long.toString(Math.round(v))
            : String.format("%.1f", v);
    }

    private static double clampPct(double v)
    {
        return v < 0 ? 0 : (v > 100 ? 100 : v);
    }
}
