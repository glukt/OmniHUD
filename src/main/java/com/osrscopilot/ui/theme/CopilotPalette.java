package com.osrscopilot.ui.theme;

import java.awt.Color;
import net.runelite.client.ui.ColorScheme;

/**
 * Single source of truth for panel colours. Every side-panel view (Bestiary, Slayer, Combat, Loot,
 * About, ...) should paint from these tokens so a theme tweak lands in one place instead of a
 * hundred scattered {@code new Color(...)} literals.
 *
 * <p>Overlays that paint on the game canvas ({@code map/*Overlay}, {@code combat/overlay/*},
 * {@code combat/tutorial/TutorialOverlay}'s scrim / ring / card chrome) keep their own values -
 * they are tuned for legibility over live game pixels, not for the dark-grey panel, and are out of
 * scope here. The one exception is the tour caption's title colour, which is deliberately unified
 * with {@link #ACCENT} so the walkthrough reads as part of the plugin.
 *
 * <p>Bespoke procedural tints (portrait vignettes, gradient stops, the {@code RarityFormat} rarity
 * ramp, Konar's badge palette, the nested-table chrome shades) are intentionally NOT folded in
 * here; they are one-offs, not roles.
 */
public final class CopilotPalette
{
    private CopilotPalette() {}

    /**
     * The plugin accent - the most-used of the four rival "gold" literals that used to be copied
     * into every view ({@code 255,152,31}). Retitling, active tabs, focus rings and section
     * headings all resolve here.
     */
    public static final Color ACCENT = new Color(0xFF, 0x98, 0x1F); // == new Color(255, 152, 31)

    /** Softer amber accent for secondary rails / peak markers (peak DPS, per-kill spine, supply burn). */
    public static final Color ACCENT_MUTED = new Color(255, 183, 77);

    /** Panel and section headings. Currently the same hue as {@link #ACCENT}; kept separate so a
     *  future theme can split "brand accent" from "heading text" without another literal hunt. */
    public static final Color TITLE = ACCENT;

    /** GP, per-hour rates, combat level, reward-point and weight figures - the ubiquitous pure gold
     *  ({@code 255,215,0}). Distinct on purpose from the more orange {@link #ACCENT}. */
    public static final Color GOLD = new Color(255, 215, 0);

    /** Gains / healing / "ahead" / unlocked. Value-equal to {@code CombatMeterColors.HEALING_PRIMARY}
     *  so the HUD, fight graph and panel cards read the same green. */
    public static final Color POSITIVE = new Color(129, 199, 132);

    /** Losses / DTPS / wipes / "dry" / destructive actions (Reset). Value-equal to
     *  {@code CombatMeterColors.DAMAGE_TAKEN_PRIMARY}. */
    public static final Color NEGATIVE = new Color(239, 83, 80);

    /** The softer damage-taken tint used on defensive breakdown rows (a lighter sibling of
     *  {@link #NEGATIVE}). */
    public static final Color TAKEN = new Color(239, 154, 154);

    /** Wilderness / danger red - wildy level badges, "LOCKED" requirement lines. */
    public static final Color WILDY = new Color(248, 113, 113);

    /** DPS / GP-per-hour / log &amp; ledger blue - the "a rate or a stream" colour. */
    public static final Color RATE = new Color(144, 202, 249);

    /** Clickable "Map" / "Wiki" / focus affordances inside a card. */
    public static final Color LINK = new Color(90, 200, 250);

    /** Elemental-weakness green (shared by the Bestiary card list, the detail view and Slayer). */
    public static final Color WEAKNESS = new Color(52, 211, 153);

    /** Slayer-level requirement purple. */
    public static final Color SLAYER = new Color(187, 134, 252);

    /** Quest-requirement purple (a hair pinker than {@link #SLAYER}). */
    public static final Color QUEST = new Color(192, 132, 252);

    /** Multi-combat zone orange. */
    public static final Color MULTI = new Color(251, 146, 60);

    /** Primary value text on a card. */
    public static final Color TEXT = Color.WHITE;

    /** Sub-headings and neutral (un-ranked) meter bars - a desaturated blue-grey. */
    public static final Color TEXT_MUTED = new Color(120, 144, 156);

    /** Card interior. Alias of {@link ColorScheme#DARKER_GRAY_COLOR}, the RuneLite panel standard. */
    public static final Color CARD_BG = ColorScheme.DARKER_GRAY_COLOR;

    /** The 1px outline around a card / hero banner (the {@code 55..60} grey family). */
    public static final Color CARD_BORDER = new Color(60, 60, 65);

    /** Hover background for a clickable row (spawn-zone cards, per-kill rows). */
    public static final Color ROW_HOVER = new Color(48, 48, 54);
}
