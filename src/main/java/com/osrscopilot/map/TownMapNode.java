package com.osrscopilot.map;

import com.osrscopilot.data.model.TownNode;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

public class TownMapNode extends WorldMapPoint
{
    private final TownNode town;

    public TownMapNode(TownNode town, BufferedImage icon, boolean snapToEdge)
    {
        super(town.getWorldPoint(), icon);
        this.town = town;
        setSnapToEdge(snapToEdge);
        setJumpOnClick(false);
        setName(town.getName());
        setTooltip(null);
    }

    public TownNode getTown()
    {
        return town;
    }
}
