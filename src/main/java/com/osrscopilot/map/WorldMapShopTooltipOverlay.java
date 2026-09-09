package com.osrscopilot.map;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.ShopItem;
import com.osrscopilot.data.model.TownNode;
import com.osrscopilot.util.NpcPortraitManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

@Singleton
public class WorldMapShopTooltipOverlay extends Overlay
{
    private static final int HOVER_RADIUS_SQ = 16 * 16;
    private static final Color BG_COLOR = new Color(15, 15, 20, 252);
    private static final Color BORDER_GOLD = new Color(255, 185, 45, 230);
    private static final Color TITLE_GOLD = new Color(255, 152, 31);
    private static final Color QUEST_PURPLE = new Color(195, 140, 255);
    private static final Color QUEST_BG = new Color(55, 18, 75, 220);
    private static final Color STOCK_GREEN = new Color(52, 211, 153);
    private static final Color PRICE_GOLD = new Color(255, 215, 0);

    // Native-icon halo highlight (see renderNativeIconHalos javadoc)
    private static final Color HALO_COLOR = new Color(255, 205, 60);
    private static final float HALO_BASE_RADIUS_PX = 9.0f;
    private static final float HALO_PULSE_RADIUS_PX = 2.0f;
    private static final float HALO_BASE_ALPHA = 0.22f;
    private static final float HALO_PULSE_ALPHA = 0.12f;
    private static final float HALO_HOVER_ALPHA = 0.75f;
    private static final float HALO_IDLE_FILL_ALPHA = 0.08f;
    private static final float HALO_HOVER_FILL_ALPHA = 0.22f;
    private static final long HALO_PULSE_PERIOD_MS = 1800L;

    private static final Stroke STROKE_HALO_HOVER = new BasicStroke(2.2f);
    private static final Stroke STROKE_HALO_NORMAL = new BasicStroke(1.5f);
    private static final Stroke STROKE_CARD_BORDER = new BasicStroke(1.5f);

    private final Client client;
    private final WorldMapOverlay worldMapOverlay;
    private final WorldMapMarkerManager markerManager;
    private final ShopDatabase shopDatabase;
    private final NpcPortraitManager npcPortraitManager;
    private final OsrsCopilotConfig config;

    // Per-cursor-position memo for the hover scan. render() runs every frame, but the ~500-node
    // world->screen projection only needs redoing when something that moves the nodes on screen
    // changes: the cursor, or the map's pan/zoom. A cursor parked over the open map costs nothing.
    private int lastHoverMx = Integer.MIN_VALUE;
    private int lastHoverMy = Integer.MIN_VALUE;
    private int lastMapX = Integer.MIN_VALUE;
    private int lastMapY = Integer.MIN_VALUE;
    private float lastMapZoom = Float.NaN;
    private VendorMapNode hoverVendor;
    private TownMapNode hoverTown;

    @Inject
    public WorldMapShopTooltipOverlay(
        Client client,
        WorldMapOverlay worldMapOverlay,
        WorldMapMarkerManager markerManager,
        ShopDatabase shopDatabase,
        NpcPortraitManager npcPortraitManager,
        OsrsCopilotConfig config)
    {
        this.client = client;
        this.worldMapOverlay = worldMapOverlay;
        this.markerManager = markerManager;
        this.shopDatabase = shopDatabase;
        this.npcPortraitManager = npcPortraitManager;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setPriority(PRIORITY_HIGHEST);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        Widget worldMap = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        if (worldMap == null || worldMap.isHidden())
        {
            return null;
        }

        Point mousePos = client.getMouseCanvasPosition();
        Rectangle mapBounds = worldMap.getBounds();
        boolean mouseOnMap = mousePos != null && mapBounds.contains(mousePos.getX(), mousePos.getY());
        int mx = mouseOnMap ? mousePos.getX() : Integer.MIN_VALUE;
        int my = mouseOnMap ? mousePos.getY() : Integer.MIN_VALUE;

        // Native-icon halo pass runs independently of the detailed-tooltip toggle below - it's a
        // cheap per-frame ring draw around the handful of curated shops known to sit exactly on a
        // pre-existing native OSRS world map icon (ShopDatabase.KNOWN_NATIVE_SHOP_ALIGNMENTS via
        // NativeIconDetector). The click/hover hit-zone and tooltip logic underneath are unchanged.
        if (config.highlightNativeIcons())
        {
            renderNativeIconHalos(graphics, mx, my);
        }

        if (!config.showDetailedTooltips() || !mouseOnMap)
        {
            return null;
        }

        resolveHover(mx, my);
        if (hoverVendor != null)
        {
            renderShopMiniMenu(graphics, hoverVendor.getShop(), mx + 20, my + 32);
        }
        else if (hoverTown != null)
        {
            renderTownMiniMenu(graphics, hoverTown.getTown(), mx + 20, my + 32);
        }

        return null;
    }

    /**
     * Resolves which vendor / town node (if any) the cursor is over, memoised so a stationary cursor
     * on the open map does no projection work at all. Vendor nodes win over town nodes, matching the
     * old scan order.
     */
    private void resolveHover(int mx, int my)
    {
        WorldMap wm = client.getWorldMap();
        Point mapPos = wm != null ? wm.getWorldMapPosition() : null;
        float zoom = wm != null ? wm.getWorldMapZoom() : Float.NaN;
        boolean viewSame = mapPos != null
            && mapPos.getX() == lastMapX && mapPos.getY() == lastMapY
            && Float.compare(zoom, lastMapZoom) == 0;

        if (mx == lastHoverMx && my == lastHoverMy && viewSame)
        {
            return; // nothing that affects the projection has changed since the last scan
        }

        lastHoverMx = mx;
        lastHoverMy = my;
        if (mapPos != null)
        {
            lastMapX = mapPos.getX();
            lastMapY = mapPos.getY();
            lastMapZoom = zoom;
        }

        // Fast path: is the node we were on last frame still under the cursor? (1-2 projections)
        if (hoverVendor != null && isUnderCursor(hoverVendor.getWorldPoint(), mx, my))
        {
            hoverTown = null;
            return;
        }
        if (hoverTown != null && isUnderCursor(hoverTown.getWorldPoint(), mx, my))
        {
            hoverVendor = null;
            return;
        }

        hoverVendor = null;
        hoverTown = null;
        for (VendorMapNode node : markerManager.getActiveVendorNodes())
        {
            if (isUnderCursor(node.getWorldPoint(), mx, my))
            {
                hoverVendor = node;
                return;
            }
        }
        for (TownMapNode node : markerManager.getActiveTownNodes())
        {
            if (isUnderCursor(node.getWorldPoint(), mx, my))
            {
                hoverTown = node;
                return;
            }
        }
    }

    private boolean isUnderCursor(WorldPoint wp, int mx, int my)
    {
        Point screenPt = worldMapOverlay.mapWorldPointToGraphicsPoint(wp);
        if (screenPt == null)
        {
            return false;
        }
        int dx = screenPt.getX() - mx;
        int dy = screenPt.getY() - my;
        return (dx * dx + dy * dy) <= HOVER_RADIUS_SQ;
    }

    /**
     * Draws a cheap, alpha-blended pulsing ring ("halo") over shops known to coincide exactly with a
     * pre-existing native OSRS world map icon, instead of this plugin drawing a second, competing
     * pin on top of the game's own icon. WorldMapMarkerManager swaps those shops' WorldMapPoint icon
     * for a fully transparent placeholder - this method supplies the actual visual treatment. The
     * underlying node (and therefore the existing click/hover hit-zone and menu behavior) is
     * completely unchanged; only what gets painted at that point differs.
     *
     * Cost: O(active vendor shops) per frame, but the vast majority are skipped in one boolean check
     * (node.isNativeIconAligned()) - only the curated-table subset (a few dozen shops at most) reach
     * the actual draw calls, each of which is two simple alpha-blended oval ops with cached static strokes.
     */
    private void renderNativeIconHalos(Graphics2D g, int mx, int my)
    {
        List<VendorMapNode> vendorNodes = markerManager.getActiveVendorNodes();
        if (vendorNodes.isEmpty())
        {
            return;
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double phase = (System.currentTimeMillis() % HALO_PULSE_PERIOD_MS) / (double) HALO_PULSE_PERIOD_MS;
        float pulse = (float) (0.5 + 0.5 * Math.sin(phase * 2.0 * Math.PI)); // smooth 0..1 sine wave

        Composite originalComposite = g.getComposite();
        Stroke originalStroke = g.getStroke();

        for (VendorMapNode node : vendorNodes)
        {
            if (!node.isNativeIconAligned())
            {
                continue;
            }

            Point screenPt = worldMapOverlay.mapWorldPointToGraphicsPoint(node.getWorldPoint());
            if (screenPt == null)
            {
                continue;
            }

            int sx = screenPt.getX();
            int sy = screenPt.getY();

            boolean hovered = mx != Integer.MIN_VALUE
                && (((long) (sx - mx) * (sx - mx)) + ((long) (sy - my) * (sy - my))) <= HOVER_RADIUS_SQ;

            float ringAlpha = hovered ? HALO_HOVER_ALPHA : (HALO_BASE_ALPHA + pulse * HALO_PULSE_ALPHA);
            float fillAlpha = hovered ? HALO_HOVER_FILL_ALPHA : HALO_IDLE_FILL_ALPHA;
            float radius = HALO_BASE_RADIUS_PX + (hovered ? 1.5f : pulse * HALO_PULSE_RADIUS_PX);

            int innerR = Math.round(radius * 0.75f);
            int outerR = Math.round(radius);

            // Soft inner glow fill
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fillAlpha));
            g.setColor(HALO_COLOR);
            g.fillOval(sx - innerR, sy - innerR, innerR * 2, innerR * 2);

            // Outer glowing ring
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ringAlpha));
            g.setColor(HALO_COLOR);
            g.setStroke(hovered ? STROKE_HALO_HOVER : STROKE_HALO_NORMAL);
            g.drawOval(sx - outerR, sy - outerR, outerR * 2, outerR * 2);
        }

        g.setComposite(originalComposite);
        g.setStroke(originalStroke);
    }

    private void renderShopMiniMenu(Graphics2D g, Shop shop, int startX, int startY)
    {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        FontMetrics fmBold = g.getFontMetrics(FontManager.getRunescapeBoldFont());
        FontMetrics fmSmall = g.getFontMetrics(FontManager.getRunescapeSmallFont());

        String metaStr = shop.getTown() + " | Keeper: " + shop.getNpcName() + " (" + (shop.isMembersOnly() ? "P2P" : "F2P") + ")";
        String questStr = (shop.getQuestRequirement() != null && !shop.getQuestRequirement().isEmpty())
            ? "🔒 Unlock: " + shop.getQuestRequirement()
            : null;

        // Calculate dynamic width to contain ALL text perfectly
        int maxTextWidth = Math.max(fmBold.stringWidth(shop.getName()), fmSmall.stringWidth(metaStr));
        if (questStr != null)
        {
            maxTextWidth = Math.max(maxTextWidth, fmSmall.stringWidth(questStr) + 16);
        }

        // Check stock item line widths
        int stockCount = (shop.getItems() != null) ? Math.min(shop.getItems().size(), 4) : 0;
        if (stockCount > 0)
        {
            for (int i = 0; i < stockCount; i++)
            {
                ShopItem item = shop.getItems().get(i);
                String priceStr = "x" + item.getDefaultStock() + " (" + item.getPrice() + " " + shop.getCurrency().getShortName() + ")";
                int lineW = fmSmall.stringWidth("• " + item.getName()) + fmSmall.stringWidth(priceStr) + 24;
                if (lineW > maxTextWidth) maxTextWidth = lineW;
            }
        }

        int footerW = fmSmall.stringWidth("Right-Click: View Stock in SidePanel");
        if (footerW > maxTextWidth) maxTextWidth = footerW;

        int cardWidth = Math.max(250, maxTextWidth + 24);

        // Calculate dynamic height
        int cardHeight = 36 + (stockCount * 14) + 26;
        if (questStr != null)
        {
            cardHeight += 24;
        }

        // Flip or clamp to screen bounds
        if (startY + cardHeight > client.getCanvasHeight() - 10)
        {
            startY = Math.max(10, startY - cardHeight - 44);
        }
        if (startX + cardWidth > client.getCanvasWidth() - 10)
        {
            startX = Math.max(10, client.getCanvasWidth() - cardWidth - 10);
        }

        // Card background
        g.setColor(BG_COLOR);
        g.fillRoundRect(startX, startY, cardWidth, cardHeight, 8, 8);
        g.setColor(BORDER_GOLD);
        g.setStroke(STROKE_CARD_BORDER);
        g.drawRoundRect(startX, startY, cardWidth, cardHeight, 8, 8);

        int curY = startY + 16;

        // Title
        g.setFont(FontManager.getRunescapeBoldFont());
        g.setColor(TITLE_GOLD);
        g.drawString(shop.getName(), startX + 10, curY);

        curY += 14;
        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
        g.drawString(metaStr, startX + 10, curY);

        curY += 6;

        // Quest Lock Banner (Fully contained within cardWidth)
        if (questStr != null)
        {
            curY += 2;
            int bannerW = cardWidth - 20;
            g.setColor(QUEST_BG);
            g.fillRoundRect(startX + 10, curY, bannerW, 18, 4, 4);
            g.setColor(QUEST_PURPLE);
            g.drawRoundRect(startX + 10, curY, bannerW, 18, 4, 4);
            g.drawString(questStr, startX + 16, curY + 13);
            curY += 22;
        }

        // Divider
        g.setColor(new Color(60, 60, 60));
        g.drawLine(startX + 10, curY, startX + cardWidth - 10, curY);
        curY += 12;

        // Stock preview
        if (stockCount > 0)
        {
            g.setFont(FontManager.getRunescapeSmallFont());
            for (int i = 0; i < stockCount; i++)
            {
                ShopItem item = shop.getItems().get(i);
                g.setColor(STOCK_GREEN);
                g.drawString("• " + item.getName(), startX + 10, curY);

                String priceStr = "x" + item.getDefaultStock() + " (" + item.getPrice() + " " + shop.getCurrency().getShortName() + ")";
                g.setColor(PRICE_GOLD);
                int strW = fmSmall.stringWidth(priceStr);
                g.drawString(priceStr, startX + cardWidth - 10 - strW, curY);

                curY += 13;
            }
        }

        // Footer hint
        curY = startY + cardHeight - 6;
        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(new Color(140, 140, 140));
        g.drawString("Left-Click: View Stock in SidePanel", startX + 10, curY);
    }

    private void renderTownMiniMenu(Graphics2D g, TownNode town, int startX, int startY)
    {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        FontMetrics fmBold = g.getFontMetrics(FontManager.getRunescapeBoldFont());
        FontMetrics fmSmall = g.getFontMetrics(FontManager.getRunescapeSmallFont());

        int textW = Math.max(fmBold.stringWidth(town.getName() + " (Town Hub)"), fmSmall.stringWidth("Click: Open Town in SidePanel"));
        int cardWidth = Math.max(220, textW + 24);
        int cardHeight = 70;

        if (startY + cardHeight > client.getCanvasHeight() - 10)
        {
            startY = Math.max(10, startY - cardHeight - 44);
        }
        if (startX + cardWidth > client.getCanvasWidth() - 10)
        {
            startX = Math.max(10, client.getCanvasWidth() - cardWidth - 10);
        }

        g.setColor(BG_COLOR);
        g.fillRoundRect(startX, startY, cardWidth, cardHeight, 8, 8);
        g.setColor(BORDER_GOLD);
        g.setStroke(STROKE_CARD_BORDER);
        g.drawRoundRect(startX, startY, cardWidth, cardHeight, 8, 8);

        g.setFont(FontManager.getRunescapeBoldFont());
        g.setColor(TITLE_GOLD);
        g.drawString(town.getName() + " (Town Hub)", startX + 10, startY + 18);

        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
        List<Shop> shops = shopDatabase.getShopsByTown(town.getName());
        g.drawString(shops.size() + " Vendor Shop(s) Available", startX + 10, startY + 34);

        g.setColor(new Color(90, 200, 250));
        g.drawString("Click: Open Town in SidePanel", startX + 10, startY + 52);
    }
}
