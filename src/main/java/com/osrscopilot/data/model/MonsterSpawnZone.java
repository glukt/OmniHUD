package com.osrscopilot.data.model;

import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

@Value
@Builder
public class MonsterSpawnZone
{
    String zoneName;
    String locationName;
    int minX, minY, maxX, maxY;
    int plane;
    int spawnCount;
    boolean multiCombat;
    int wildernessLevel;
    WorldPoint centerPoint;
    WorldPoint surfaceEntrance;
    String dungeonName;

    /**
     * True only when {@link #surfaceEntrance} traces to a cited source (an OSRS Wiki entrance map
     * pin). When false, the surface entrance is a rough estimate and the map should not drop a
     * pinpoint beacon on it - see {@link #hasVerifiedSurfaceEntrance()}.
     */
    boolean entranceVerified;

    public WorldPoint getEffectiveFocusPoint()
    {
        if (surfaceEntrance != null)
        {
            return surfaceEntrance;
        }
        if (centerPoint != null)
        {
            return centerPoint;
        }
        return new WorldPoint((minX + maxX) / 2, (minY + maxY) / 2, plane);
    }

    /** A surface entrance we can stand behind - safe to drop a precise beacon on. */
    public boolean hasVerifiedSurfaceEntrance()
    {
        return surfaceEntrance != null && entranceVerified;
    }

    /** True when the focus point is an <em>estimated</em> dungeon entrance (open the area, no pin). */
    public boolean isApproximateFocusPoint()
    {
        return surfaceEntrance != null && !entranceVerified;
    }

    public WorldPoint getZoneCenter()
    {
        if (centerPoint != null)
        {
            return centerPoint;
        }
        if (minX > 0 && maxX > 0 && minY > 0 && maxY > 0)
        {
            return new WorldPoint((minX + maxX) / 2, (minY + maxY) / 2, plane);
        }
        if (surfaceEntrance != null)
        {
            return surfaceEntrance;
        }
        return null;
    }

    public boolean contains(int worldX, int worldY, int p)
    {
        return p == plane && worldX >= minX && worldX <= maxX && worldY >= minY && worldY <= maxY;
    }
}
