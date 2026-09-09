package com.osrscopilot.combat;

import com.osrscopilot.OsrsCopilotConfig;
import java.awt.Color;

public final class CombatMeterColors
{
    private CombatMeterColors() {}

    // Damage style & entity colours
    public static final Color MELEE_PRIMARY      = new Color(230, 81, 0);     // Deep Orange
    public static final Color MELEE_GRADIENT     = new Color(255, 138, 101);  // Soft Orange Accent

    public static final Color RANGED_PRIMARY     = new Color(46, 125, 50);    // Hunter Green
    public static final Color RANGED_GRADIENT    = new Color(129, 199, 132);  // Soft Green Accent

    public static final Color MAGIC_PRIMARY      = new Color(21, 101, 192);   // Arcane Blue
    public static final Color MAGIC_GRADIENT     = new Color(100, 181, 246);  // Soft Cyan Accent

    public static final Color THRALL_PRIMARY     = new Color(123, 31, 162);   // Shadow Purple
    public static final Color THRALL_GRADIENT    = new Color(186, 104, 200);  // Soft Violet Accent

    public static final Color STATUS_PRIMARY     = new Color(245, 127, 23);   // Gold / Venom / Poison
    public static final Color STATUS_GRADIENT    = new Color(255, 213, 79);   // Yellow Accent

    public static final Color RECOIL_VENGEANCE   = new Color(198, 40, 40);    // Crimson
    public static final Color CANNON_PRIMARY     = new Color(109, 76, 65);    // Cannon Brown / Steel

    // Semantic metric colours - one hue per concept, shared by the HUD mode pill, the bars,
    // the fight graph curves and the side-panel cards (these used to be 3-4 near-duplicates each).
    public static final Color DAMAGE_TAKEN_PRIMARY  = new Color(239, 83, 80);   // Red
    public static final Color DAMAGE_TAKEN_GRADIENT = new Color(255, 138, 128);
    public static final Color HEALING_PRIMARY       = new Color(129, 199, 132); // Green
    public static final Color HEALING_GRADIENT      = new Color(165, 214, 167);
    public static final Color SUPPLY_PRIMARY        = new Color(255, 202, 40);  // Amber
    public static final Color SUPPLY_GRADIENT       = new Color(255, 224, 130);

    // Container & Background Chrome
    public static final Color HUD_BACKGROUND     = new Color(18, 18, 22, 225); // Dark semi-transparent
    public static final Color HUD_BORDER         = new Color(45, 45, 52, 255);
    public static final Color BAR_TRACK_BG       = new Color(30, 30, 36, 180);
    public static final Color FOOTER_BG          = new Color(12, 12, 16, 240);
    public static final Color POPUP_BG           = new Color(20, 20, 26, 250);
    public static final Color TEXT_PRIMARY       = new Color(245, 245, 245);
    public static final Color TEXT_SECONDARY     = new Color(175, 175, 185);
    public static final Color TEXT_MUTED         = new Color(120, 120, 135);
    public static final Color TEXT_ACCENT_GOLD   = new Color(255, 193, 7);

    // Button states
    public static final Color BTN_BG_IDLE        = new Color(34, 34, 42, 220);
    public static final Color BTN_BG_HOVER       = new Color(55, 55, 68, 240);
    public static final Color BTN_BG_ACTIVE      = new Color(201, 158, 55, 230);
    public static final Color BTN_TEXT_ACTIVE    = new Color(15, 15, 20);

    // Accent Palette Swatches
    public static final Color SWATCH_DRAGON_RED      = new Color(220, 50, 45);
    public static final Color SWATCH_SARADOMIN_BLUE  = new Color(45, 120, 225);
    public static final Color SWATCH_ZULRAH_TEAL     = new Color(30, 180, 160);
    public static final Color SWATCH_VOID_PURPLE     = new Color(145, 65, 220);
    public static final Color SWATCH_TOA_GOLD        = new Color(255, 193, 7);

    public static Color getPalettePrimary(OsrsCopilotConfig.PaletteAccentOption opt)
    {
        if (opt == null) return SWATCH_TOA_GOLD;
        switch (opt)
        {
            case DRAGON_RED: return SWATCH_DRAGON_RED;
            case SARADOMIN_BLUE: return SWATCH_SARADOMIN_BLUE;
            case ZULRAH_TEAL: return SWATCH_ZULRAH_TEAL;
            case VOID_PURPLE: return SWATCH_VOID_PURPLE;
            case TOA_GOLD: return SWATCH_TOA_GOLD;
            case DYNAMIC_STYLE: return null;
            default: return SWATCH_TOA_GOLD;
        }
    }

    public static Color getPaletteGradient(OsrsCopilotConfig.PaletteAccentOption opt)
    {
        if (opt == null) return new Color(255, 224, 130);
        switch (opt)
        {
            case DRAGON_RED: return new Color(255, 120, 115);
            case SARADOMIN_BLUE: return new Color(115, 175, 255);
            case ZULRAH_TEAL: return new Color(100, 230, 215);
            case VOID_PURPLE: return new Color(195, 130, 255);
            case TOA_GOLD: return new Color(255, 224, 130);
            case DYNAMIC_STYLE: return null;
            default: return new Color(255, 224, 130);
        }
    }
}

