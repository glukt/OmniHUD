package com.osrscopilot.combat.model;

public enum SegmentType
{
    ENCOUNTER("Boss / Monster Fight"),
    SLAYER_TASK("Slayer Task"),
    RAID_ROOM("Raid Room"),
    SESSION_CURRENT("Current Session"),
    SESSION_TOTAL("Total"),
    GROUP("Group");

    private final String displayName;

    SegmentType(String displayName)
    {
        this.displayName = displayName;
    }

    public String getDisplayName()
    {
        return displayName;
    }
}
