package com.osrscopilot.combat.model;

public enum SegmentStatus
{
    IN_PROGRESS("In Progress"),
    PHASE_TRANSITION("Phase Transition"),
    COMPLETED("Victory / Completed"),
    WIPED("Wiped / Defeated"),
    ABANDONED("Reset / Timeout");

    private final String displayName;

    SegmentStatus(String displayName)
    {
        this.displayName = displayName;
    }

    public String getDisplayName()
    {
        return displayName;
    }
}
