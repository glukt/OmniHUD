package com.osrscopilot.data.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

@Value
@Builder
public class TownNode
{
    int id;
    String name;
    int regionId;
    int worldX;
    int worldY;
    int worldPlane;
    String description;
    List<Integer> shopIds;

    public WorldPoint getWorldPoint()
    {
        return new WorldPoint(worldX, worldY, worldPlane);
    }
}
