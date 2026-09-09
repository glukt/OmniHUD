package com.osrscopilot.combat.model;

public class HitRecord
{
    private final int amount;
    private final CombatStyle style;
    private final String sourceName;
    private final int tick;

    public HitRecord(int amount, CombatStyle style, String sourceName, int tick)
    {
        this.amount = amount;
        this.style = style;
        this.sourceName = sourceName;
        this.tick = tick;
    }

    public int getAmount()
    {
        return amount;
    }

    public CombatStyle getStyle()
    {
        return style;
    }

    public String getSourceName()
    {
        return sourceName;
    }

    public int getTick()
    {
        return tick;
    }
}
