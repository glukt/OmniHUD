package com.osrscopilot.map;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.util.IconCache;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

@Singleton
public class MinimapVendorOverlay extends Overlay implements MouseListener
{
    public static class MinimapShopMarker
    {
        private final Shop shop;
        private final Point minimapPoint;
        private final BufferedImage icon;

        public MinimapShopMarker(Shop shop, Point minimapPoint, BufferedImage icon)
        {
            this.shop = shop;
            this.minimapPoint = minimapPoint;
            this.icon = icon;
        }

        public Shop getShop()
        {
            return shop;
        }

        public Point getMinimapPoint()
        {
            return minimapPoint;
        }

        public BufferedImage getIcon()
        {
            return icon;
        }
    }

    private static class CachedMarkerClickZone
    {
        private final Rectangle bounds;
        private final Shop shop;

        private CachedMarkerClickZone(Rectangle bounds, Shop shop)
        {
            this.bounds = bounds;
            this.shop = shop;
        }
    }

    public static final int TOGGLE_SIZE = 18;
    public static final int TOGGLE_OFFSET_X = 4;
    public static final int TOGGLE_OFFSET_Y = 4;

    private static final int MAX_DISTANCE = 45;
    private static final int HOVER_RADIUS = 10;
    private static final int ORB_SIZE = 10;
    private static final int ORB_OFFSET = 5;

    private final Client client;
    private final OsrsCopilotConfig config;
    private final ShopDatabase shopDatabase;
    private final IconCache iconCache;
    private final TooltipManager tooltipManager;
    private final ConfigManager configManager;

    private WorldPoint cachedPlayerLocation;
    // Shops that passed the plane + MAX_DISTANCE filter for cachedPlayerLocation. The full ~500-shop
    // scan (each iteration allocating a WorldPoint) only re-runs when the player changes tile; the
    // per-frame render then just re-projects this short list onto the rotating minimap.
    private List<Shop> nearbyShops = Collections.emptyList();
    private final List<MinimapShopMarker> activeMarkers = new ArrayList<>();
    private volatile Rectangle lastToggleBounds;
    private volatile List<CachedMarkerClickZone> cachedClickZones = Collections.emptyList();

    private Consumer<Shop> onShopClicked;
    private Runnable onToggleClicked;
    private long lastToggleTime = 0;
    private java.awt.Point mousePressPoint = null;

    public MinimapVendorOverlay(
        Client client,
        OsrsCopilotConfig config,
        ShopDatabase shopDatabase,
        IconCache iconCache,
        TooltipManager tooltipManager)
    {
        this(client, config, shopDatabase, iconCache, tooltipManager, null);
    }

    @Inject
    public MinimapVendorOverlay(
        Client client,
        OsrsCopilotConfig config,
        ShopDatabase shopDatabase,
        IconCache iconCache,
        TooltipManager tooltipManager,
        ConfigManager configManager)
    {
        this.client = client;
        this.config = config;
        this.shopDatabase = shopDatabase;
        this.iconCache = iconCache;
        this.tooltipManager = tooltipManager;
        this.configManager = configManager;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
    }

    public void setShopClickHandler(Consumer<Shop> onShopClicked)
    {
        this.onShopClicked = onShopClicked;
    }

    public void setToggleClickHandler(Runnable onToggleClicked)
    {
        this.onToggleClicked = onToggleClicked;
    }

    public static String buildTooltip(Shop shop)
    {
        return TooltipFormatter.formatShopMinimapTooltip(shop);
    }

    public static String buildToggleTooltip(boolean enabled)
    {
        return TooltipFormatter.formatMinimapToggleTooltip(enabled);
    }

    public WorldPoint getCachedPlayerLocation()
    {
        return cachedPlayerLocation;
    }

    public List<MinimapShopMarker> getActiveMarkers()
    {
        return Collections.unmodifiableList(activeMarkers);
    }

    public static Rectangle computeToggleBounds(Rectangle minimapBounds)
    {
        if (minimapBounds == null)
        {
            return null;
        }
        return new Rectangle(minimapBounds.x + TOGGLE_OFFSET_X, minimapBounds.y + TOGGLE_OFFSET_Y, TOGGLE_SIZE, TOGGLE_SIZE);
    }

    public Rectangle getMinimapBounds()
    {
        if (client == null)
        {
            return null;
        }

        Widget minimap = client.getWidget(InterfaceID.Orbs.UNIVERSE);
        if (isValidWidget(minimap))
        {
            return minimap.getBounds();
        }

        minimap = client.getWidget(InterfaceID.Toplevel.MINIMAP);
        if (isValidWidget(minimap))
        {
            return minimap.getBounds();
        }

        minimap = client.getWidget(InterfaceID.ToplevelOsrsStretch.MINIMAP);
        if (isValidWidget(minimap))
        {
            return minimap.getBounds();
        }

        minimap = client.getWidget(InterfaceID.ToplevelPreEoc.MINIMAP);
        if (isValidWidget(minimap))
        {
            return minimap.getBounds();
        }

        minimap = client.getWidget(InterfaceID.Toplevel.MAPCONTAINER);
        if (isValidWidget(minimap))
        {
            return minimap.getBounds();
        }

        minimap = client.getWidget(InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER);
        if (isValidWidget(minimap))
        {
            return minimap.getBounds();
        }

        minimap = client.getWidget(InterfaceID.ToplevelPreEoc.MAP_CONTAINER);
        if (isValidWidget(minimap))
        {
            return minimap.getBounds();
        }

        return null;
    }

    private static boolean isValidWidget(Widget widget)
    {
        if (widget == null || widget.isHidden())
        {
            return false;
        }
        Rectangle bounds = widget.getBounds();
        return bounds != null && bounds.width > 0 && bounds.height > 0;
    }

    public Rectangle getVendorToggleBounds()
    {
        if (client != null)
        {
            Widget worldMapOrb = client.getWidget(InterfaceID.Orbs.ORB_WORLDMAP);
            if (isValidWidget(worldMapOrb))
            {
                Rectangle orbBounds = worldMapOrb.getBounds();
                int toggleX = orbBounds.x + orbBounds.width - 8;
                int toggleY = orbBounds.y - TOGGLE_SIZE - 4;
                return new Rectangle(toggleX, toggleY, TOGGLE_SIZE, TOGGLE_SIZE);
            }
        }

        Rectangle minimapBounds = getMinimapBounds();
        if (minimapBounds != null)
        {
            return new Rectangle(minimapBounds.x + minimapBounds.width - (TOGGLE_SIZE * 2 + 8), minimapBounds.y + minimapBounds.height - TOGGLE_SIZE - 38, TOGGLE_SIZE, TOGGLE_SIZE);
        }
        return null;
    }

    public Rectangle getToggleBounds()
    {
        return getVendorToggleBounds();
    }

    public boolean isVendorToggleHovered(Point mousePos)
    {
        if (mousePos == null)
        {
            return false;
        }
        Rectangle bounds = this.lastToggleBounds != null ? this.lastToggleBounds : getVendorToggleBounds();
        return bounds != null && bounds.contains(mousePos.getX(), mousePos.getY());
    }

    public boolean isVendorToggleHovered(java.awt.Point p)
    {
        if (p == null)
        {
            return false;
        }
        Rectangle bounds = this.lastToggleBounds != null ? this.lastToggleBounds : getVendorToggleBounds();
        return bounds != null && bounds.contains(p);
    }

    public boolean isToggleHovered(Point mousePos)
    {
        return isVendorToggleHovered(mousePos);
    }

    public boolean isToggleHovered(java.awt.Point mousePos)
    {
        return isVendorToggleHovered(mousePos);
    }

    public void toggleMinimapVendors()
    {
        long now = System.currentTimeMillis();
        if (now - lastToggleTime < 250)
        {
            return;
        }
        lastToggleTime = now;

        if (onToggleClicked != null)
        {
            onToggleClicked.run();
            return;
        }

        if (configManager != null)
        {
            boolean current = config != null && config.showMinimapVendors();
            configManager.setConfiguration("osrscopilot", "showMinimapVendors", !current);
        }
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (client == null || client.getGameState() != GameState.LOGGED_IN)
        {
            activeMarkers.clear();
            cachedClickZones = Collections.emptyList();
            this.lastToggleBounds = null;
            cachedPlayerLocation = null;
            nearbyShops = Collections.emptyList();
            return null;
        }

        activeMarkers.clear();

        WorldPoint playerLoc = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getWorldLocation()
            : null;

        net.runelite.api.Point mousePoint = client.getMouseCanvasPosition();
        java.awt.Point mouseAwtPoint = mousePoint != null ? new java.awt.Point(mousePoint.getX(), mousePoint.getY()) : null;

        boolean showVendors = config == null || config.showMinimapVendors();

        // 1. Render Vendor Minimap Toggle Icon (Top-Left of Minimap)
        Rectangle vendorToggleBounds = getVendorToggleBounds();
        this.lastToggleBounds = vendorToggleBounds;
        boolean vendorToggleHovered = false;

        if (vendorToggleBounds != null && g != null)
        {
            vendorToggleHovered = mouseAwtPoint != null && vendorToggleBounds.contains(mouseAwtPoint);

            BufferedImage toggleImg = iconCache != null
                ? (showVendors ? iconCache.getMinimapToggleOnIcon() : iconCache.getMinimapToggleOffIcon())
                : null;

            if (vendorToggleHovered)
            {
                g.setColor(new Color(255, 215, 0, 90));
                g.fillOval(vendorToggleBounds.x - 2, vendorToggleBounds.y - 2, vendorToggleBounds.width + 4, vendorToggleBounds.height + 4);

                g.setColor(new Color(255, 215, 0, 230));
                g.setStroke(new BasicStroke(1.8f));
                g.drawOval(vendorToggleBounds.x - 2, vendorToggleBounds.y - 2, vendorToggleBounds.width + 4, vendorToggleBounds.height + 4);

                if (tooltipManager != null)
                {
                    tooltipManager.add(new Tooltip(buildToggleTooltip(showVendors)));
                }
            }
            else
            {
                g.setColor(new Color(20, 20, 20, 160));
                g.fillOval(vendorToggleBounds.x, vendorToggleBounds.y, vendorToggleBounds.width, vendorToggleBounds.height);
            }

            if (toggleImg != null)
            {
                g.drawImage(toggleImg, vendorToggleBounds.x, vendorToggleBounds.y, vendorToggleBounds.width, vendorToggleBounds.height, null);
            }
        }

        // 2. Render vendor orbs on minimap
        List<CachedMarkerClickZone> newZones = new ArrayList<>();
        if (showVendors && shopDatabase != null && playerLoc != null && g != null)
        {
            // Rescan the full shop list only when the player has moved to a new tile.
            if (!playerLoc.equals(cachedPlayerLocation))
            {
                cachedPlayerLocation = playerLoc;
                nearbyShops = scanNearbyShops(playerLoc);
            }

            for (Shop shop : nearbyShops)
            {
                LocalPoint lp = LocalPoint.fromWorld(client, shop.getWorldPoint());
                if (lp == null)
                {
                    continue;
                }

                Point minimapPoint = Perspective.localToMinimap(client, lp);
                if (minimapPoint != null)
                {
                    BufferedImage icon = iconCache != null ? iconCache.getIconForCategory(shop.getCategory()) : null;
                    activeMarkers.add(new MinimapShopMarker(shop, minimapPoint, icon));
                    newZones.add(new CachedMarkerClickZone(new Rectangle(minimapPoint.getX() - 8, minimapPoint.getY() - 8, 16, 16), shop));

                    if (icon != null)
                    {
                        g.drawImage(icon, minimapPoint.getX() - ORB_OFFSET, minimapPoint.getY() - ORB_OFFSET, ORB_SIZE, ORB_SIZE, null);
                    }

                    if (!vendorToggleHovered && mousePoint != null && tooltipManager != null && minimapPoint.distanceTo(mousePoint) <= HOVER_RADIUS)
                    {
                        tooltipManager.add(new Tooltip(buildTooltip(shop)));
                    }
                }
            }
        }
        this.cachedClickZones = Collections.unmodifiableList(newZones);

        return null;
    }

    /** Shops on the player's plane within {@link #MAX_DISTANCE} tiles - recomputed only on a tile change. */
    public List<Shop> scanNearbyShops(WorldPoint playerLoc)
    {
        List<Shop> near = new ArrayList<>();
        for (Shop shop : shopDatabase.getAllShops())
        {
            if (shop.getWorldX() == 0 || shop.getWorldY() == 0
                || shop.getWorldPlane() != playerLoc.getPlane())
            {
                continue;
            }
            if (playerLoc.distanceTo(shop.getWorldPoint()) <= MAX_DISTANCE)
            {
                near.add(shop);
            }
        }
        return near;
    }

    // =========================================================================
    // MouseListener implementation for Click Consumption & Walk Prevention
    // =========================================================================

    @Override
    public java.awt.event.MouseEvent mousePressed(java.awt.event.MouseEvent e)
    {
        if (e.getButton() == java.awt.event.MouseEvent.BUTTON1)
        {
            java.awt.Point p = e.getPoint();

            // 1. Vendor Toggle Button Press
            Rectangle toggle = this.lastToggleBounds;
            if (toggle != null && toggle.contains(p))
            {
                mousePressPoint = p;
                e.consume(); // Block canvas walk
                return e;
            }

            // 2. Vendor Shop Marker Press
            List<CachedMarkerClickZone> zones = this.cachedClickZones;
            if (zones != null && !zones.isEmpty())
            {
                for (CachedMarkerClickZone zone : zones)
                {
                    if (zone != null && zone.bounds != null && zone.bounds.contains(p))
                    {
                        mousePressPoint = p;
                        e.consume(); // Block canvas walk
                        return e;
                    }
                }
            }
        }
        mousePressPoint = null;
        return e;
    }

    @Override
    public java.awt.event.MouseEvent mouseReleased(java.awt.event.MouseEvent e)
    {
        if (e.getButton() == java.awt.event.MouseEvent.BUTTON1 && mousePressPoint != null)
        {
            java.awt.Point p = e.getPoint();

            // 1. Vendor Toggle Button Click
            Rectangle toggle = this.lastToggleBounds;
            if (toggle != null && (toggle.contains(p) || (toggle.contains(mousePressPoint) && mousePressPoint.distanceSq(p) <= 36)))
            {
                toggleMinimapVendors();
                e.consume();
                mousePressPoint = null;
                return e;
            }

            // 2. Vendor Shop Marker Click
            List<CachedMarkerClickZone> zones = this.cachedClickZones;
            if (zones != null && !zones.isEmpty())
            {
                for (CachedMarkerClickZone zone : zones)
                {
                    if (zone != null && zone.bounds != null && (zone.bounds.contains(p) || (zone.bounds.contains(mousePressPoint) && mousePressPoint.distanceSq(p) <= 36)))
                    {
                        if (onShopClicked != null)
                        {
                            onShopClicked.accept(zone.shop);
                        }
                        e.consume(); // Consumes click so character never walks to the shop tile
                        mousePressPoint = null;
                        return e;
                    }
                }
            }

            mousePressPoint = null;
        }
        mousePressPoint = null;
        return e;
    }

    @Override
    public java.awt.event.MouseEvent mouseDragged(java.awt.event.MouseEvent e)
    {
        if (mousePressPoint != null)
        {
            Rectangle toggle = this.lastToggleBounds;
            if (toggle != null && toggle.contains(mousePressPoint))
            {
                e.consume();
                return e;
            }
            List<CachedMarkerClickZone> zones = this.cachedClickZones;
            if (zones != null && !zones.isEmpty())
            {
                for (CachedMarkerClickZone zone : zones)
                {
                    if (zone != null && zone.bounds != null && zone.bounds.contains(mousePressPoint))
                    {
                        e.consume();
                        return e;
                    }
                }
            }
        }
        return e;
    }

    @Override public java.awt.event.MouseEvent mouseClicked(java.awt.event.MouseEvent e) { return e; }
    @Override public java.awt.event.MouseEvent mouseEntered(java.awt.event.MouseEvent e) { return e; }
    @Override public java.awt.event.MouseEvent mouseExited(java.awt.event.MouseEvent e) { return e; }
    @Override public java.awt.event.MouseEvent mouseMoved(java.awt.event.MouseEvent e) { return e; }
}
