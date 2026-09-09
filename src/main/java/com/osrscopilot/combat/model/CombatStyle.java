package com.osrscopilot.combat.model;

import java.awt.Color;
import com.osrscopilot.combat.CombatMeterColors;

public enum CombatStyle
{
    MELEE("Melee", CombatMeterColors.MELEE_PRIMARY, CombatMeterColors.MELEE_GRADIENT),
    RANGED("Ranged", CombatMeterColors.RANGED_PRIMARY, CombatMeterColors.RANGED_GRADIENT),
    MAGIC("Magic", CombatMeterColors.MAGIC_PRIMARY, CombatMeterColors.MAGIC_GRADIENT),
    THRALL("Thrall", CombatMeterColors.THRALL_PRIMARY, CombatMeterColors.THRALL_GRADIENT),
    CANNON("Cannon", CombatMeterColors.CANNON_PRIMARY, new Color(141, 110, 99)),
    POISON("Poison", new Color(46, 125, 50), new Color(129, 199, 132)),
    VENOM("Venom", new Color(0, 150, 136), new Color(100, 230, 215)),
    BURN("Burn", new Color(255, 87, 34), new Color(255, 171, 145)),
    BLEED("Bleed", new Color(183, 28, 28), new Color(239, 154, 154)),
    POISON_VENOM("Poison/Venom", CombatMeterColors.STATUS_PRIMARY, CombatMeterColors.STATUS_GRADIENT),
    BURN_BLEED("Burn/Bleed", new Color(255, 87, 34), new Color(255, 171, 145)),
    RECOIL_RETALIATION("Recoil", CombatMeterColors.RECOIL_VENGEANCE, new Color(239, 154, 154)),
    OTHER("Other", new Color(158, 158, 158), new Color(224, 224, 224));

    private final String displayName;
    private final Color primaryColor;
    private final Color gradientColor;

    CombatStyle(String displayName, Color primaryColor, Color gradientColor)
    {
        this.displayName = displayName;
        this.primaryColor = primaryColor;
        this.gradientColor = gradientColor;
    }

    public boolean isStatusEffect()
    {
        return this == POISON || this == VENOM || this == BURN || this == BLEED || this == POISON_VENOM || this == BURN_BLEED;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public Color getPrimaryColor()
    {
        return primaryColor;
    }

    public Color getGradientColor()
    {
        return gradientColor;
    }
}
