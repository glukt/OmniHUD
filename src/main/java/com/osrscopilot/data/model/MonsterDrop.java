package com.osrscopilot.data.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MonsterDrop
{
    private static final Pattern INT_RUN = Pattern.compile("\\d+");
    private static final Pattern FRACTION = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)");

    int itemId;
    String name;
    String quantity;
    double rarity;
    String rarityFraction;
    int rolls;
    boolean noted;
    boolean members;
    String category;

    /**
     * Raw wiki <em>per-roll</em> drop rate string, exactly the way the wiki quotes it (wiki rates
     * are per roll, not per kill). Prefers the pre-formatted {@link #rarityFraction} when present,
     * else derives from {@link #rarity}.
     *
     * <p>NON-UI: no player-facing surface should show this as the headline rarity - it ignores
     * {@link #rolls}, so a multi-roll drop reads rarer here than it actually is per kill. UI code
     * formats the per-kill headline through {@code com.osrscopilot.data.RarityFormat}; this
     * getter is for diagnostics and as the source of an optional "(per roll 1/N)" secondary note.
     */
    public String getFormattedRarity()
    {
        if (rarityFraction != null && !rarityFraction.isEmpty())
        {
            return rarityFraction;
        }
        if (rarity >= 1.0)
        {
            return "Always (1/1)";
        }
        if (rarity <= 0)
        {
            return "Varies";
        }
        int denom = (int) Math.round(1.0 / rarity);
        return "1/" + denom;
    }

    /** Smallest quantity this drop can roll (1 when the wiki omits a quantity). */
    public int getMinQuantity()
    {
        return quantityRange()[0];
    }

    /** Largest quantity this drop can roll (equal to the min for a fixed quantity). */
    public int getMaxQuantity()
    {
        return quantityRange()[1];
    }

    public boolean hasQuantityRange()
    {
        int[] r = quantityRange();
        return r[1] > r[0];
    }

    /**
     * Parses the free-text {@link #quantity} defensively: pulls every integer run out of the string
     * and returns {@code [min, max]}. Handles "5", "1-3", "1&ndash;3", "200,300", "1 ,2",
     * "8;10;12", "4-8,7-11". Falls back to {@code [1, 1]} for null / empty / non-numeric.
     */
    private int[] quantityRange()
    {
        if (quantity == null || quantity.isEmpty())
        {
            return new int[]{1, 1};
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        Matcher m = INT_RUN.matcher(quantity);
        while (m.find())
        {
            try
            {
                int v = Integer.parseInt(m.group());
                if (v < min)
                {
                    min = v;
                }
                if (v > max)
                {
                    max = v;
                }
            }
            catch (NumberFormatException overflow)
            {
                // an absurdly long digit run - ignore this token
            }
        }
        return (max == Integer.MIN_VALUE) ? new int[]{1, 1} : new int[]{min, max};
    }

    /** Times this row is rolled per kill (at least 1; the wiki default when unspecified is 1). */
    public int getEffectiveRolls()
    {
        return rolls > 0 ? rolls : 1;
    }

    /**
     * Probability this drop lands <em>at least once</em> in a single kill, folding in
     * {@link #getEffectiveRolls()}. Returns 0 when the rate is unknown ({@link #rarity} &lt;= 0 and
     * no usable {@link #rarityFraction}).
     */
    public double getPerKillChance()
    {
        double perRoll = perRollProbability();
        if (perRoll <= 0)
        {
            return 0.0;
        }
        if (perRoll >= 1.0)
        {
            return 1.0;
        }
        int r = getEffectiveRolls();
        return (r <= 1) ? perRoll : 1.0 - Math.pow(1.0 - perRoll, r);
    }

    /**
     * Integer "1 in N" denominator for {@link #getPerKillChance()}, or 0 when the rate is unknown.
     * Intended for a per-kill / dryness display in the loot tracker.
     */
    public int getPerKillDenominator()
    {
        double c = getPerKillChance();
        return (c <= 0) ? 0 : (int) Math.round(1.0 / c);
    }

    private double perRollProbability()
    {
        if (rarity > 0)
        {
            return rarity;
        }
        if (rarityFraction != null)
        {
            Matcher m = FRACTION.matcher(rarityFraction.replace(",", ""));
            if (m.find())
            {
                double num = Double.parseDouble(m.group(1));
                double den = Double.parseDouble(m.group(2));
                if (den > 0)
                {
                    return num / den;
                }
            }
        }
        return 0.0;
    }
}
