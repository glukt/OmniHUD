package com.osrscopilot.combat.model;

/**
 * One crowd-control / debuff applied to an enemy during a fight (bind, snare, entangle, freeze).
 * Duration is the spell's nominal length in game ticks; {@code actualEndTick} is filled when the
 * timer runs out (OSRS gives no live "debuff cleared" signal, so re-freezes / early breaks aren't
 * detected in this first version).
 */
public class DebuffApplication
{
    private final String type;        // "Snare", "Ice Barrage", ...
    private final String targetName;  // resolved enemy display name
    private final int startTick;
    private final int nominalTicks;
    private final int expectedEndTick;
    private int actualEndTick = -1;

    public DebuffApplication(String type, String targetName, int startTick, int nominalTicks)
    {
        this.type = type;
        this.targetName = targetName;
        this.startTick = startTick;
        this.nominalTicks = nominalTicks;
        this.expectedEndTick = startTick + nominalTicks;
    }

    public String getType() { return type; }
    public String getTargetName() { return targetName; }
    public int getStartTick() { return startTick; }
    public int getNominalTicks() { return nominalTicks; }
    public int getExpectedEndTick() { return expectedEndTick; }
    public int getActualEndTick() { return actualEndTick; }
    public boolean isOpen() { return actualEndTick < 0; }

    public void close(int tick) { this.actualEndTick = tick; }

    /** Held duration in seconds (nominal until closed, then actual). */
    public double getSeconds()
    {
        int end = actualEndTick >= 0 ? actualEndTick : expectedEndTick;
        return Math.max(0, (end - startTick)) * 0.6;
    }
}
