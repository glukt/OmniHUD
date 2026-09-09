package com.osrscopilot.data;

import com.osrscopilot.data.model.MonsterDrop;
import java.awt.Color;
import java.util.Locale;

/**
 * The single place the UI turns a {@link MonsterDrop} into the "1 in N" headline a player reads.
 *
 * <p>Every Bestiary / spreadsheet / world-map surface quotes the SAME number from here: the
 * <em>per-kill</em> probability, i.e. the wiki's per-roll rate folded through the row's
 * rolls-per-kill ({@link MonsterDrop#getPerKillChance()} / {@link MonsterDrop#getPerKillDenominator()}).
 * A drop rolled twice per kill at a wiki-quoted 1/1,024 lands ~1/512 per kill, and that 1/512 is
 * what the card, the table, the directory line, the spreadsheet column and the map tooltip all show.
 *
 * <p>The wiki's untouched per-roll string still lives on {@link MonsterDrop#getFormattedRarity()}
 * for non-UI / diagnostic use, and can be surfaced as an optional parenthetical note next to a
 * multi-roll headline via {@link #perRollNote(MonsterDrop)} - never as the headline, never driving
 * the colour.
 *
 * <p>Formatting rules match the long-standing detail-view idiom: {@code "Always (1/1)"} for a
 * guaranteed drop, a digit-grouped {@code "1/5,000"} otherwise, and {@code "Varies"} when the rate
 * is unknown. An unknown / "Varies" rate renders a NEUTRAL colour, not the rarest tier.
 */
public final class RarityFormat
{
    private RarityFormat()
    {
    }

    // One tier ramp, keyed off the per-kill probability. Values match the historical Bestiary
    // drop-card palette; UNKNOWN is a neutral grey so an unknown / "Varies" rate never masquerades
    // as the rarest tier.
    public static final Color ALWAYS = new Color(74, 222, 128);
    public static final Color COMMON = new Color(243, 244, 246);
    public static final Color UNCOMMON = new Color(253, 224, 71);
    public static final Color RARE = new Color(251, 146, 60);
    public static final Color VERY_RARE = new Color(192, 132, 252);
    public static final Color UNKNOWN = new Color(156, 163, 175);

    /**
     * Headline per-kill rarity for a drop: {@code "Always (1/1)"}, a digit-grouped {@code "1/5,000"},
     * or {@code "Varies"} when the rate is unknown. Null-safe.
     */
    public static String perKill(MonsterDrop drop)
    {
        if (drop == null)
        {
            return "Varies";
        }
        if (drop.getRarity() >= 1.0)
        {
            return "Always (1/1)";
        }
        int denom = drop.getPerKillDenominator();
        if (denom <= 0)
        {
            return "Varies";
        }
        if (denom <= 1)
        {
            return "1/1";
        }
        return String.format(Locale.US, "1/%,d", denom);
    }

    /**
     * The wiki per-roll figure as a small parenthetical note ({@code " (per roll 1/1,024)"}), or
     * {@code ""} when the row is single-roll, its per-roll rate is unknown, or it is not
     * meaningfully different from the per-kill headline. Purely a secondary annotation.
     */
    public static String perRollNote(MonsterDrop drop)
    {
        if (drop == null || drop.getEffectiveRolls() <= 1)
        {
            return "";
        }
        String perRoll = drop.getFormattedRarity();
        if (perRoll == null || perRoll.isEmpty()
            || "Varies".equalsIgnoreCase(perRoll)
            || perRoll.equals(perKill(drop)))
        {
            return "";
        }
        return " (per roll " + perRoll + ")";
    }

    /**
     * Tier colour for the per-kill headline of a drop; neutral grey when the rate is unknown /
     * "Varies". Null-safe.
     */
    public static Color perKillColor(MonsterDrop drop)
    {
        if (drop == null)
        {
            return UNKNOWN;
        }
        if (drop.getRarity() >= 1.0)
        {
            return ALWAYS;
        }
        return colorForChance(drop.getPerKillChance());
    }

    /**
     * Tier colour for a raw probability in {@code [0, 1]}. A value {@code <= 0} is treated as an
     * unknown rate and rendered NEUTRAL grey - not the rarest tier.
     */
    public static Color colorForChance(double chance)
    {
        if (chance <= 0.0)
        {
            return UNKNOWN;
        }
        if (chance >= 1.0)
        {
            return ALWAYS;
        }
        if (chance >= 1.0 / 20.0)
        {
            return COMMON;
        }
        if (chance >= 1.0 / 128.0)
        {
            return UNCOMMON;
        }
        if (chance >= 1.0 / 1000.0)
        {
            return RARE;
        }
        return VERY_RARE;
    }
}
