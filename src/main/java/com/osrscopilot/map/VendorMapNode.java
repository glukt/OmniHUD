package com.osrscopilot.map;

import com.osrscopilot.data.model.Shop;
import java.awt.image.BufferedImage;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

public class VendorMapNode extends WorldMapPoint
{
    private final Shop shop;
    private final WorldPoint npcLocation;
    private final boolean nativeIconAligned;

    /**
     * @param nativeIconAligned true if this node's displayPoint is known (via the curated
     * ShopDatabase.KNOWN_NATIVE_SHOP_ALIGNMENTS table, see NativeIconDetector) to sit exactly on a
     * pre-existing native OSRS world map icon. Rendering code uses this to draw a halo highlight
     * around the existing icon instead of a competing plugin marker - the click/hover hit-zone
     * (this node's WorldPoint) is unaffected either way.
     */
    public VendorMapNode(Shop shop, WorldPoint displayPoint, BufferedImage icon, boolean snapToEdge, boolean nativeIconAligned)
    {
        super(displayPoint != null ? displayPoint : shop.getWorldPoint(), icon);
        this.shop = shop;
        this.npcLocation = shop.getWorldPoint();
        this.nativeIconAligned = nativeIconAligned;
        setSnapToEdge(snapToEdge);
        setJumpOnClick(false);
        setName(shop.getName());
        setTooltip(null);
    }

    public VendorMapNode(Shop shop, WorldPoint displayPoint, BufferedImage icon, boolean snapToEdge)
    {
        this(shop, displayPoint, icon, snapToEdge, false);
    }

    public VendorMapNode(Shop shop, BufferedImage icon, boolean snapToEdge)
    {
        this(shop, shop.getWorldPoint(), icon, snapToEdge, false);
    }

    public Shop getShop()
    {
        return shop;
    }

    public WorldPoint getNpcLocation()
    {
        return npcLocation;
    }

    public boolean isNativeIconAligned()
    {
        return nativeIconAligned;
    }
}
