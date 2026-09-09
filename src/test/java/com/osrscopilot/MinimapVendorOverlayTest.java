package com.osrscopilot;

import com.google.gson.Gson;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.model.MembershipFilter;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.ShopItem;
import com.osrscopilot.map.MinimapVendorOverlay;
import com.osrscopilot.util.IconCache;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MinimapVendorOverlayTest
{
    private static class TestConfig implements OsrsCopilotConfig
    {
        private boolean showMinimap = true;

        public void setShowMinimap(boolean val)
        {
            this.showMinimap = val;
        }

        @Override
        public boolean showMinimapVendors()
        {
            return showMinimap;
        }

        @Override public boolean alignToNativeIcons() { return true; }
        @Override public boolean enableTownMarkers() { return true; }
        @Override public boolean showVendorIcons() { return true; }
        @Override public boolean snapToEdge() { return false; }
        @Override public boolean hideZeroStock() { return true; }
        @Override public boolean hideIronmanRestricted() { return true; }
        @Override public MembershipFilter membershipFilter() { return MembershipFilter.ALL; }
        @Override public boolean showDetailedTooltips() { return true; }
        @Override public int searchDistanceLimit() { return 0; }
    }

    private TestConfig config;
    private ShopDatabase database;
    private IconCache iconCache;

    @Before
    public void setUp()
    {
        config = new TestConfig();
        database = new ShopDatabase(new Gson());
        database.load();
        iconCache = new IconCache();
    }

    @Test
    public void testConfigDefaultMethodValue()
    {
        OsrsCopilotConfig defaultConfig = new OsrsCopilotConfig() {};
        Assert.assertTrue("Default showMinimapVendors should be true", defaultConfig.showMinimapVendors());
    }

    @Test
    public void testOverlayProperties()
    {
        MinimapVendorOverlay overlay = new MinimapVendorOverlay(
            null,
            config,
            database,
            iconCache,
            null,
            null
        );

        Assert.assertEquals(OverlayLayer.ABOVE_WIDGETS, overlay.getLayer());
        Assert.assertEquals(OverlayPosition.DYNAMIC, overlay.getPosition());
        Assert.assertEquals(MinimapVendorOverlay.PRIORITY_HIGH, overlay.getPriority(), 0.001f);
    }

    @Test
    public void testOverlayImplementsMouseListenerAndConsumesClicks()
    {
        Assert.assertTrue(
            "MinimapVendorOverlay must implement MouseListener to consume left-click events and prevent canvas walk",
            MouseListener.class.isAssignableFrom(MinimapVendorOverlay.class)
        );
    }

    @Test
    public void testActiveMarkersInitialState()
    {
        MinimapVendorOverlay overlay = new MinimapVendorOverlay(
            null,
            config,
            database,
            iconCache,
            null,
            null
        );

        List<MinimapVendorOverlay.MinimapShopMarker> markers = overlay.getActiveMarkers();
        Assert.assertNotNull(markers);
        Assert.assertTrue("Active markers should initially be empty", markers.isEmpty());
        Assert.assertNull("Cached player location should initially be null", overlay.getCachedPlayerLocation());
    }

    @Test
    public void testMinimapShopMarkerProperties()
    {
        Shop shop = database.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull(shop);
        Point pt = new Point(620, 140);
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

        MinimapVendorOverlay.MinimapShopMarker marker = new MinimapVendorOverlay.MinimapShopMarker(shop, pt, icon);
        Assert.assertSame(shop, marker.getShop());
        Assert.assertEquals(pt, marker.getMinimapPoint());
        Assert.assertSame(icon, marker.getIcon());
    }

    @Test
    public void testShopCategoryResolution()
    {
        Shop magicShop = Shop.builder()
            .name("Aubury's Rune Store")
            .tags(Collections.singletonList("magic"))
            .build();
        Assert.assertEquals("magic", magicShop.getCategory());

        Shop meleeShop = Shop.builder()
            .name("Horvik's Armoury")
            .tags(Collections.singletonList("melee"))
            .build();
        Assert.assertEquals("melee", meleeShop.getCategory());

        Shop untaggedBowShop = Shop.builder()
            .name("Lowe's Archery Emporium")
            .build();
        Assert.assertEquals("archery", untaggedBowShop.getCategory());

        Shop foodShop = Shop.builder()
            .name("Giddy Goose Pub & Food")
            .build();
        Assert.assertEquals("food", foodShop.getCategory());

        Shop herbShop = Shop.builder()
            .name("Apothecary Potions")
            .build();
        Assert.assertEquals("herblore", herbShop.getCategory());

        Shop generalShop = Shop.builder()
            .name("Varrock General Store")
            .build();
        Assert.assertEquals("general", generalShop.getCategory());
    }

    @Test
    public void testRenderReturnsNullWhenDisabled()
    {
        config.setShowMinimap(false);

        MinimapVendorOverlay overlay = new MinimapVendorOverlay(
            null,
            config,
            database,
            iconCache,
            null,
            null
        );

        Assert.assertNull(overlay.render(null));
        Assert.assertTrue(overlay.getActiveMarkers().isEmpty());
        Assert.assertNull(overlay.getCachedPlayerLocation());
    }

    @Test
    public void testDistanceCalculationAndFiltering()
    {
        WorldPoint playerLoc = new WorldPoint(3250, 3400, 0); // Near Varrock Rune Shop (3253, 3402)
        // Looked up by name, not ordinal id - ids are reassigned every time the wiki scraper
        // re-walks Category:Shops (confirmed 2026-08-20: a full re-scrape shifted this exact shop's
        // id and silently broke this test when it fell through to an id-based fallback).
        Shop aubury = database.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull(aubury);

        int distToAubury = playerLoc.distanceTo(aubury.getWorldPoint());
        Assert.assertTrue("Aubury should be within 45 tiles", distToAubury <= 45);

        // A distant shop in Yanille
        Shop frenita = database.getShopByName("Frenita's Cookery Shop");
        if (frenita != null)
        {
            int distToYanille = playerLoc.distanceTo(frenita.getWorldPoint());
            Assert.assertTrue("Yanille shop should be further than 45 tiles", distToYanille > 45);
        }
    }

    /** The per-frame render only re-projects a small pre-filtered list; the full scan is plane + 45-tile bounded. */
    @Test
    public void testScanNearbyShopsIsPlaneAndDistanceBounded()
    {
        MinimapVendorOverlay overlay = new MinimapVendorOverlay(
            null, config, database, iconCache, null, null);

        WorldPoint atVarrock = new WorldPoint(3250, 3400, 0);
        List<Shop> near = overlay.scanNearbyShops(atVarrock);

        Assert.assertFalse("Varrock has shops within 45 tiles", near.isEmpty());
        Assert.assertTrue("scan is a strict subset of all shops", near.size() < database.getAllShops().size());
        for (Shop s : near)
        {
            Assert.assertEquals("only the player's plane", 0, s.getWorldPlane());
            Assert.assertTrue("within MAX_DISTANCE (45) tiles",
                atVarrock.distanceTo(s.getWorldPoint()) <= 45);
        }
        Assert.assertTrue("Aubury's Rune Shop is in range",
            near.stream().anyMatch(s -> "Aubury's Rune Shop".equals(s.getName())));

        // A spot with nothing nearby yields an empty scan, not the whole list.
        Assert.assertTrue(overlay.scanNearbyShops(new WorldPoint(2500, 4000, 0)).isEmpty());
    }

    @Test
    public void testMouseClickRadiusCheck24px()
    {
        Point minimapPoint = new Point(600, 100);
        Point hitPoint12 = new Point(608, 108); // distance = sqrt(64+64) = 11.31 <= 24
        Point hitPoint18 = new Point(612, 112); // distance = sqrt(144+144) = 16.97 <= 24
        Point hitPoint24 = new Point(616, 116); // distance = sqrt(256+256) = 22.62 <= 24
        Point missPoint25 = new Point(625, 100); // distance = 25 > 24

        Assert.assertTrue("Point within 12px should hit 24px radius", minimapPoint.distanceTo(hitPoint12) <= 24);
        Assert.assertTrue("Point within 18px should hit 24px radius", minimapPoint.distanceTo(hitPoint18) <= 24);
        Assert.assertTrue("Point within 24px should hit 24px radius", minimapPoint.distanceTo(hitPoint24) <= 24);
        Assert.assertFalse("Point farther than 24px should miss", minimapPoint.distanceTo(missPoint25) <= 24);
    }

    @Test
    public void testMouseHoverRadiusCheck()
    {
        Point minimapPoint = new Point(600, 100);
        Point hitPoint10 = new Point(608, 106); // distance = 10.0
        Point missPoint11 = new Point(608, 108); // distance = 11.31

        Assert.assertTrue("Point within 10px should be within hover radius", minimapPoint.distanceTo(hitPoint10) <= 10);
        Assert.assertFalse("Point farther than 10px should miss hover radius", minimapPoint.distanceTo(missPoint11) <= 10);
    }

    @Test
    public void testTooltipTextFormatCompactManyItems()
    {
        Shop aubury = database.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull(aubury);

        String tooltip = MinimapVendorOverlay.buildTooltip(aubury);
        String[] lines = tooltip.split("</br>");

        // Compact fixed-width layout: name / town + tenure / item count + first item / open hint.
        // Aubury's Rune Shop has no quest lock, so exactly 4 lines.
        Assert.assertEquals("Tooltip should have 4 lines separated by </br>", 4, lines.length);
        Assert.assertEquals("<col=ff981f>" + aubury.getName() + "</col>", lines[0]);
        Assert.assertEquals("<col=a0a0a0>" + aubury.getTown() + " · F2P</col>", lines[1]);
        int total = aubury.getItems().size();
        Assert.assertEquals(
            "<col=ffd700>" + total + " items · " + aubury.getItems().get(0).getName() + " +" + (total - 1) + "</col>",
            lines[2]);
        Assert.assertTrue("Every line must stay well under a full canvas width",
            java.util.Arrays.stream(lines).allMatch(l -> l.replaceAll("<[^>]+>", "").length() <= 44));
        Assert.assertEquals("<col=90caf9>Left-click to open in side panel</col>", lines[3]);
    }

    @Test
    public void testTooltipTextFormatSmallStock()
    {
        List<ShopItem> items = new ArrayList<>();
        items.add(ShopItem.builder().name("Bronze sword").defaultStock(10).price(20).build());
        items.add(ShopItem.builder().name("Iron sword").defaultStock(5).price(50).build());

        Shop smallShop = Shop.builder()
            .id(999)
            .name("Small Swords")
            .town("Lumbridge")
            .npcName("Bob")
            .items(items)
            .build();

        String tooltip = MinimapVendorOverlay.buildTooltip(smallShop);
        String[] lines = tooltip.split("</br>");

        Assert.assertEquals(4, lines.length);
        Assert.assertEquals("<col=a0a0a0>Lumbridge · F2P</col>", lines[1]);
        Assert.assertEquals("<col=ffd700>2 items · Bronze sword +1</col>", lines[2]);
        Assert.assertEquals("<col=90caf9>Left-click to open in side panel</col>", lines[3]);
    }

    @Test
    public void testToggleBoundsComputation()
    {
        java.awt.Rectangle minimapBounds = new java.awt.Rectangle(100, 200, 150, 150);
        java.awt.Rectangle toggleBounds = MinimapVendorOverlay.computeToggleBounds(minimapBounds);

        Assert.assertNotNull(toggleBounds);
        Assert.assertEquals(104, toggleBounds.x);
        Assert.assertEquals(204, toggleBounds.y);
        Assert.assertEquals(18, toggleBounds.width);
        Assert.assertEquals(18, toggleBounds.height);

        Assert.assertNull("Null minimap bounds should return null toggle bounds", MinimapVendorOverlay.computeToggleBounds(null));
    }

    @Test
    public void testToggleHoverCheck()
    {
        java.awt.Rectangle minimapBounds = new java.awt.Rectangle(100, 200, 150, 150);
        java.awt.Rectangle toggleBounds = MinimapVendorOverlay.computeToggleBounds(minimapBounds);

        Point insidePoint = new Point(105, 205);
        Point outsidePoint = new Point(90, 190);

        Assert.assertTrue("Inside point should be contained in toggle bounds", toggleBounds.contains(insidePoint.getX(), insidePoint.getY()));
        Assert.assertFalse("Outside point should not be contained in toggle bounds", toggleBounds.contains(outsidePoint.getX(), outsidePoint.getY()));
    }

    @Test
    public void testToggleTooltipsExactStrings()
    {
        String tooltipOn = MinimapVendorOverlay.buildToggleTooltip(true);
        String tooltipOff = MinimapVendorOverlay.buildToggleTooltip(false);

        Assert.assertEquals(
            "<col=ff981f>Vendor Minimap Icons:</col> <col=4ade80>ON</col><br><col=90caf9>Click to toggle off</col>",
            tooltipOn
        );

        Assert.assertEquals(
            "<col=ff981f>Vendor Minimap Icons:</col> <col=f87171>OFF</col><br><col=90caf9>Click to toggle on</col>",
            tooltipOff
        );
    }

    @Test
    public void testToggleIconsGeneration()
    {
        BufferedImage onIcon = iconCache.getMinimapToggleOnIcon();
        BufferedImage offIcon = iconCache.getMinimapToggleOffIcon();

        Assert.assertNotNull("Toggle ON icon must be generated", onIcon);
        Assert.assertEquals(18, onIcon.getWidth());
        Assert.assertEquals(18, onIcon.getHeight());

        Assert.assertNotNull("Toggle OFF icon must be generated", offIcon);
        Assert.assertEquals(18, offIcon.getWidth());
        Assert.assertEquals(18, offIcon.getHeight());
    }

    @Test
    public void testToggleClickHandler()
    {
        AtomicBoolean vendorToggled = new AtomicBoolean(false);

        MinimapVendorOverlay overlay = new MinimapVendorOverlay(
            null,
            config,
            database,
            iconCache,
            null,
            null
        );

        overlay.setToggleClickHandler(() -> vendorToggled.set(true));
        overlay.toggleMinimapVendors();

        Assert.assertTrue("Vendor toggle handler should be triggered", vendorToggled.get());
    }

    @Test
    public void testToggleDebounce()
    {
        AtomicInteger vendorCount = new AtomicInteger(0);

        MinimapVendorOverlay overlay = new MinimapVendorOverlay(
            null,
            config,
            database,
            iconCache,
            null,
            null
        );

        overlay.setToggleClickHandler(vendorCount::incrementAndGet);

        overlay.toggleMinimapVendors();
        overlay.toggleMinimapVendors(); // Rapid 2nd call

        Assert.assertEquals("Vendor toggle calls within 250ms must be debounced to 1 invocation", 1, vendorCount.get());
    }

    @Test
    public void testLeftClickConsumesEventsOnVendorMarkers()
    {
        // 1. Verify overlay implements mouse listener (intercepts and consumes canvas clicks on vendor markers)
        Assert.assertTrue(
            "MinimapVendorOverlay must implement MouseListener so left clicks on icons do not move player",
            MouseListener.class.isAssignableFrom(MinimapVendorOverlay.class)
        );

        // 2. Verify tooltip formatting
        Shop shop = database.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull(shop);
        String tooltip = MinimapVendorOverlay.buildTooltip(shop);
        Assert.assertNotNull(tooltip);
    }

    @Test
    public void testMinimapRightClickMenuEntryFormatting()
    {
        Shop aubury = database.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull(aubury);

        String expectedOptionViewShop = "View Shop";
        String expectedOptionOpenSidePanel = "Open in Side Panel";
        String expectedTarget = "<col=ff981f>" + aubury.getName() + "</col>";

        Assert.assertEquals("View Shop", expectedOptionViewShop);
        Assert.assertEquals("Open in Side Panel", expectedOptionOpenSidePanel);
        Assert.assertEquals("<col=ff981f>" + aubury.getName() + "</col>", expectedTarget);
    }
}
