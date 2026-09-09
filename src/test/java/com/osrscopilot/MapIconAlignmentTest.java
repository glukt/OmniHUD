package com.osrscopilot;

import com.google.gson.Gson;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.map.NativeIconDetector;
import com.osrscopilot.map.VendorMapNode;
import com.osrscopilot.map.WorldMapMarkerManager;
import com.osrscopilot.map.WorldMapShopTooltipOverlay;
import com.osrscopilot.util.IconCache;
import com.osrscopilot.util.NpcPortraitManager;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MapIconAlignmentTest
{
    private ShopDatabase database;
    private IconCache iconCache;

    @Before
    public void setUp()
    {
        database = new ShopDatabase(new Gson());
        database.load();
        iconCache = new IconCache();
    }

    @Test
    public void testCategoryIconsAndDimensions()
    {
        String[] categories = {"magic", "melee", "archery", "food", "herblore", "general"};

        for (String cat : categories)
        {
            BufferedImage orb = iconCache.getIconForCategory(cat);
            Assert.assertNotNull("Orb icon should not be null for category: " + cat, orb);
            Assert.assertEquals("Category orb width should be 12 for " + cat, IconCache.ORB_SIZE, orb.getWidth());
            Assert.assertEquals("Category orb height should be 12 for " + cat, IconCache.ORB_SIZE, orb.getHeight());
        }

        // Town Hub Orb (28x28)
        BufferedImage townOrb = iconCache.getTownIcon();
        Assert.assertNotNull("Town orb should not be null", townOrb);
        Assert.assertEquals("Town orb width should be 28", 28, townOrb.getWidth());
        Assert.assertEquals("Town orb height should be 28", 28, townOrb.getHeight());
        Assert.assertEquals("Town orb width should match IconCache.TOWN_ORB_SIZE", IconCache.TOWN_ORB_SIZE, townOrb.getWidth());
        Assert.assertEquals("Town orb height should match IconCache.TOWN_ORB_SIZE", IconCache.TOWN_ORB_SIZE, townOrb.getHeight());

        // Test category convenience getters
        Assert.assertEquals(IconCache.ORB_SIZE, iconCache.getMagicIcon().getWidth());
        Assert.assertEquals(IconCache.ORB_SIZE, iconCache.getMeleeIcon().getWidth());
        Assert.assertEquals(IconCache.ORB_SIZE, iconCache.getArcheryIcon().getWidth());
        Assert.assertEquals(IconCache.ORB_SIZE, iconCache.getFoodIcon().getWidth());
        Assert.assertEquals(IconCache.ORB_SIZE, iconCache.getHerbloreIcon().getWidth());
        Assert.assertEquals(IconCache.ORB_SIZE, iconCache.getGeneralIcon().getWidth());
        Assert.assertEquals(IconCache.TOWN_ORB_SIZE, iconCache.getTownIcon().getWidth());

        // Test OSRS Mini Globe Icon (20x20)
        BufferedImage pluginIcon = iconCache.getPluginIcon();
        Assert.assertNotNull("Plugin icon should not be null", pluginIcon);
        Assert.assertEquals(IconCache.GLOBE_SIZE, pluginIcon.getWidth());
        Assert.assertEquals(IconCache.GLOBE_SIZE, pluginIcon.getHeight());

        BufferedImage shopIcon = iconCache.getShopIcon();
        Assert.assertNotNull("Shop icon should not be null", shopIcon);
        Assert.assertEquals(IconCache.GLOBE_SIZE, shopIcon.getWidth());
        Assert.assertEquals(IconCache.GLOBE_SIZE, shopIcon.getHeight());
    }

    @Test
    public void testKnownShopAlignments()
    {
        // Aubury's Rune Shop
        Shop aubury = database.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull("Aubury's shop should exist", aubury);
        WorldPoint auburyAligned = database.getAlignedShopLocation(aubury);
        Assert.assertEquals(new WorldPoint(3253, 3402, 0), auburyAligned);

        // Lowe's Archery Emporium
        Shop lowe = database.getShopByName("Lowe's Archery Emporium");
        Assert.assertNotNull("Lowe's shop should exist", lowe);
        WorldPoint loweAligned = database.getAlignedShopLocation(lowe);
        Assert.assertEquals(new WorldPoint(3233, 3424, 0), loweAligned);

        // Horvik's Armour Shop
        Shop horvik = database.getShopByName("Horvik's Armour Shop");
        Assert.assertNotNull("Horvik's shop should exist", horvik);
        WorldPoint horvikAligned = database.getAlignedShopLocation(horvik);
        Assert.assertEquals(new WorldPoint(3230, 3436, 0), horvikAligned);

        // Zeke's Superior Scimitars
        Shop zeke = database.getShopByName("Zeke's Superior Scimitars");
        Assert.assertNotNull("Zeke's shop should exist", zeke);
        WorldPoint zekeAligned = database.getAlignedShopLocation(zeke);
        Assert.assertEquals(new WorldPoint(3288, 3190, 0), zekeAligned);

        // Lumbridge General Store
        Shop lumbGen = database.getShopByName("Lumbridge General Store");
        Assert.assertNotNull("Lumbridge General store should exist", lumbGen);
        WorldPoint lumbGenAligned = database.getAlignedShopLocation(lumbGen);
        Assert.assertEquals(new WorldPoint(3212, 3247, 0), lumbGenAligned);
    }


    @Test
    public void testShopModelMapLocations()
    {
        Shop explicitShop = Shop.builder()
            .id(9999)
            .name("Test Shop")
            .worldX(3000)
            .worldY(3000)
            .worldPlane(0)
            .mapX(3005)
            .mapY(3005)
            .build();

        Assert.assertTrue(explicitShop.hasExplicitMapLocation());
        Assert.assertEquals(new WorldPoint(3000, 3000, 0), explicitShop.getWorldPoint());
        Assert.assertEquals(new WorldPoint(3005, 3005, 0), explicitShop.getMapLocation());

        Shop defaultShop = Shop.builder()
            .id(9998)
            .name("Default Shop")
            .worldX(3100)
            .worldY(3100)
            .worldPlane(0)
            .build();

        Assert.assertFalse(defaultShop.hasExplicitMapLocation());
        Assert.assertEquals(new WorldPoint(3100, 3100, 0), defaultShop.getMapLocation());
    }

    // =========================================================================
    // Native-icon halo highlight feature
    // =========================================================================

    @Test
    public void testNativeIconDetectorFlagsCuratedShops()
    {
        // Aubury's Rune Shop is in ShopDatabase.KNOWN_NATIVE_SHOP_ALIGNMENTS
        Shop aubury = database.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull(aubury);
        Assert.assertTrue(NativeIconDetector.isNativeIconAligned(database, aubury));

        // Lumbridge General Store is also curated
        Shop lumbGen = database.getShopByName("Lumbridge General Store");
        Assert.assertNotNull(lumbGen);
        Assert.assertTrue(NativeIconDetector.isNativeIconAligned(database, lumbGen));
    }

    @Test
    public void testNativeIconDetectorIgnoresUncuratedShop()
    {
        // Synthetic shop with an id that cannot possibly be in the curated table and no explicit
        // map-location override - aligned point falls back to the raw NPC point, so detection
        // must be false.
        Shop uncurated = Shop.builder()
            .id(9_999_999)
            .name("Some Uncurated Shop")
            .worldX(1500)
            .worldY(1500)
            .worldPlane(0)
            .build();

        Assert.assertFalse(NativeIconDetector.isNativeIconAligned(database, uncurated));
    }

    @Test
    public void testNativeIconDetectorIgnoresExplicitMapLocationOverride()
    {
        // An explicit per-shop map-location override is a different curation mechanism (arbitrary
        // building-center repositioning), not a claim that a native map icon exists there - even
        // though its aligned point differs from its raw point, detection must be false.
        Shop explicitOverride = Shop.builder()
            .id(9_999_998)
            .name("Explicit Override Shop")
            .worldX(100)
            .worldY(200)
            .worldPlane(0)
            .mapX(150)
            .mapY(250)
            .build();

        Assert.assertTrue(explicitOverride.hasExplicitMapLocation());
        Assert.assertNotEquals(explicitOverride.getWorldPoint(), database.getAlignedShopLocation(explicitOverride));
        Assert.assertFalse(NativeIconDetector.isNativeIconAligned(database, explicitOverride));
    }

    @Test
    public void testNativeIconDetectorNullSafety()
    {
        Shop shop = database.getShopByName("Aubury's Rune Shop");
        Assert.assertFalse(NativeIconDetector.isNativeIconAligned(null, shop));
        Assert.assertFalse(NativeIconDetector.isNativeIconAligned(database, null));
        Assert.assertFalse(NativeIconDetector.isNativeIconAligned(null, null));
    }

    @Test
    public void testCuratedNativeIconShopCountIsReasonable()
    {
        // Sanity guard: the curated native-icon table currently has 42 entries. This asserts the
        // detector actually flags a plausible, non-zero, non-runaway number of loaded shops so a
        // future data or logic regression (e.g. detector always returning true/false) is caught.
        int flagged = 0;
        for (Shop shop : database.getAllShops())
        {
            if (NativeIconDetector.isNativeIconAligned(database, shop))
            {
                flagged++;
            }
        }
        Assert.assertTrue("Expected at least one curated native-icon shop to be flagged", flagged > 0);
        Assert.assertTrue("Flagged count should not exceed the curated table size (42)", flagged <= 42);
    }

    @Test
    public void testIconCacheInvisibleIconIsFullyTransparentAndCorrectlySized()
    {
        BufferedImage invisible = iconCache.getInvisibleIcon();
        Assert.assertNotNull(invisible);
        Assert.assertEquals(IconCache.ORB_SIZE, invisible.getWidth());
        Assert.assertEquals(IconCache.ORB_SIZE, invisible.getHeight());

        for (int x = 0; x < invisible.getWidth(); x++)
        {
            for (int y = 0; y < invisible.getHeight(); y++)
            {
                int alpha = (invisible.getRGB(x, y) >>> 24) & 0xFF;
                Assert.assertEquals("Invisible icon must be fully transparent at (" + x + "," + y + ")", 0, alpha);
            }
        }
    }

    @Test
    public void testMarkerOpacityTweakStaysSubtle()
    {
        // The default-marker opacity tweak (IconCache.MARKER_OPACITY) should be a small, tasteful
        // softening - not enough to make the marker hard to see. Assert the orb still has a
        // strongly-opaque core pixel.
        BufferedImage magicOrb = iconCache.getMagicIcon();
        int centerAlpha = (magicOrb.getRGB(magicOrb.getWidth() / 2, magicOrb.getHeight() / 2) >>> 24) & 0xFF;
        Assert.assertTrue("Marker core should remain clearly visible after the opacity tweak", centerAlpha > 200);
    }

    @Test
    public void testVendorMapNodeNativeIconAlignedFlag()
    {
        Shop aubury = database.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull(aubury);

        VendorMapNode haloNode = new VendorMapNode(
            aubury, database.getAlignedShopLocation(aubury), iconCache.getInvisibleIcon(), false, true);
        Assert.assertTrue(haloNode.isNativeIconAligned());

        VendorMapNode normalNode = new VendorMapNode(aubury, iconCache.getMagicIcon(), false);
        Assert.assertFalse(normalNode.isNativeIconAligned());
    }

    @Test
    public void testShopTooltipOverlayRendersHaloWithoutErrorAndPaintsPixels()
    {
        Shop aubury = database.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull(aubury);
        WorldPoint alignedPoint = database.getAlignedShopLocation(aubury);

        VendorMapNode haloNode = new VendorMapNode(aubury, alignedPoint, iconCache.getInvisibleIcon(), false, true);

        Client client = mock(Client.class);
        WorldMapOverlay worldMapOverlay = mock(WorldMapOverlay.class);
        WorldMapMarkerManager markerManager = mock(WorldMapMarkerManager.class);
        NpcPortraitManager npcPortraitManager = mock(NpcPortraitManager.class);
        OsrsCopilotConfig config = mock(OsrsCopilotConfig.class);

        when(config.highlightNativeIcons()).thenReturn(true);
        when(config.showDetailedTooltips()).thenReturn(false);

        Widget worldMapWidget = mock(Widget.class);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(worldMapWidget);
        when(worldMapWidget.isHidden()).thenReturn(false);
        when(worldMapWidget.getBounds()).thenReturn(new Rectangle(0, 0, 800, 600));
        when(client.getMouseCanvasPosition()).thenReturn(null);

        when(markerManager.getActiveVendorNodes()).thenReturn(List.of(haloNode));
        when(markerManager.getActiveTownNodes()).thenReturn(Collections.emptyList());

        when(worldMapOverlay.mapWorldPointToGraphicsPoint(eq(alignedPoint))).thenReturn(new Point(400, 300));

        WorldMapShopTooltipOverlay overlay = new WorldMapShopTooltipOverlay(
            client, worldMapOverlay, markerManager, database, npcPortraitManager, config);

        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();

        overlay.render(g2d);
        g2d.dispose();

        // The halo's inner glow fill covers the exact center point, so it should no longer be
        // fully transparent after rendering.
        int alpha = (img.getRGB(400, 300) >>> 24) & 0xFF;
        Assert.assertTrue("Halo should have painted a visible pixel at the shop's aligned point", alpha > 0);
    }

    @Test
    public void testShopTooltipOverlaySkipsHaloWhenConfigDisabled()
    {
        Shop aubury = database.getShopByName("Aubury's Rune Shop");
        WorldPoint alignedPoint = database.getAlignedShopLocation(aubury);
        VendorMapNode haloNode = new VendorMapNode(aubury, alignedPoint, iconCache.getInvisibleIcon(), false, true);

        Client client = mock(Client.class);
        WorldMapOverlay worldMapOverlay = mock(WorldMapOverlay.class);
        WorldMapMarkerManager markerManager = mock(WorldMapMarkerManager.class);
        NpcPortraitManager npcPortraitManager = mock(NpcPortraitManager.class);
        OsrsCopilotConfig config = mock(OsrsCopilotConfig.class);

        when(config.highlightNativeIcons()).thenReturn(false);
        when(config.showDetailedTooltips()).thenReturn(false);

        Widget worldMapWidget = mock(Widget.class);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(worldMapWidget);
        when(worldMapWidget.isHidden()).thenReturn(false);
        when(worldMapWidget.getBounds()).thenReturn(new Rectangle(0, 0, 800, 600));
        when(client.getMouseCanvasPosition()).thenReturn(null);

        when(markerManager.getActiveVendorNodes()).thenReturn(List.of(haloNode));
        when(markerManager.getActiveTownNodes()).thenReturn(Collections.emptyList());

        when(worldMapOverlay.mapWorldPointToGraphicsPoint(eq(alignedPoint))).thenReturn(new Point(400, 300));

        WorldMapShopTooltipOverlay overlay = new WorldMapShopTooltipOverlay(
            client, worldMapOverlay, markerManager, database, npcPortraitManager, config);

        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();

        overlay.render(g2d);
        g2d.dispose();

        int alpha = (img.getRGB(400, 300) >>> 24) & 0xFF;
        Assert.assertEquals("No halo should be painted when highlightNativeIcons is disabled", 0, alpha);
    }
}
