package com.osrscopilot.combat.model;

import java.awt.Color;

public class StyleDamageEntry
{
    private final CombatStyle style;
    private final String styleName;
    private final long damage;
    private final Color color;

    public StyleDamageEntry(CombatStyle style, String styleName, long damage, Color color)
    {
        this.style = style;
        this.styleName = styleName;
        this.damage = damage;
        this.color = color;
    }

    public CombatStyle getStyle()
    {
        return style;
    }

    public String getStyleName()
    {
        return styleName;
    }

    public long getDamage()
    {
        return damage;
    }

    public Color color()
    {
        return color;
    }

    public Color getColor()
    {
        return color;
    }
}
