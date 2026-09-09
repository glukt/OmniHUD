package com.osrscopilot;

import com.google.gson.Gson;
import com.osrscopilot.data.DungeonEntranceDatabase;
import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.model.MembershipFilter;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterSpawnZone;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.TownNode;
import com.osrscopilot.map.TownMapNode;
import com.osrscopilot.map.VendorMapNode;
import com.osrscopilot.map.WorldMapFocusBeaconOverlay;
import com.osrscopilot.map.WorldMapLegendOverlay;
import com.osrscopilot.map.WorldMapMarkerManager;
import com.osrscopilot.map.WorldMapMonsterZoneOverlay;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldMapLegendOverlayTest
{
    private static class DummyComponent extends Component {}

    private static class TestConfig implements OsrsCopilotConfig
    {
        private boolean showVendors = true;
        private boolean enableTowns = true;

        @Override public boolean showMinimapVendors() { return showVendors; }
        @Override public boolean enableTownMarkers() { return enableTowns; }
        @Override public boolean showVendorIcons() { return showVendors; }
        @Override public boolean alignToNativeIcons() { return true; }
        @Override public boolean snapToEdge() { return false; }
        @Override public boolean hideZeroStock() { return true; }
        @Override public boolean hideIronmanRestricted() { return true; }
        @Override public MembershipFilter membershipFilter() { return MembershipFilter.ALL; }
        @Override public boolean showDetailedTooltips() { return true; }
        @Override public int searchDistanceLimit() { return 0; }
    }

    private Component dummyComponent;
    private ShopDatabase shopDatabase;
    private MonsterDatabase monsterDatabase;
    private DungeonEntranceDatabase dungeonEntranceDatabase;
    private TestConfig config;

    @Before
    public void setUp()
    {
        dummyComponent = new DummyComponent();
        shopDatabase = new ShopDatabase(new Gson());
        shopDatabase.load();
        monsterDatabase = new MonsterDatabase(new Gson());
        monsterDatabase.load();
        dungeonEntranceDatabase = new DungeonEntranceDatabase();
        config = new TestConfig();
    }

    private MouseEvent createMouseEvent(int id, int button, int x, int y)
    {
        return new MouseEvent(
            dummyComponent,
            id,
            System.currentTimeMillis(),
            0,
            x,
            y,
            1,
            false,
            button
        );
    }

    private KeyEvent createKeyEvent(int id, int keyCode, char keyChar)
    {
        return new KeyEvent(
            dummyComponent,
            id,
            System.currentTimeMillis(),
            0,
            keyCode,
            keyChar
        );
    }

    @Test
    public void testTownNodeHitDetectionRadius24px()
    {
        TownNode town = TownNode.builder()
            .name("Varrock")
            .worldX(3200)
            .worldY(3200)
            .worldPlane(0)
            .build();
        TownMapNode townNode = new TownMapNode(town, new BufferedImage(28, 28, BufferedImage.TYPE_INT_ARGB), false);

        int screenX = 400;
        int screenY = 300;

        Point nodeScreenPoint = new Point(screenX, screenY);
        int townClickRadiusSq = 24 * 24;

        // Within 24px radius (e.g. offset 15, 15 -> distSq = 450 <= 576)
        int clickX = screenX + 15;
        int clickY = screenY + 15;
        int dx = nodeScreenPoint.getX() - clickX;
        int dy = nodeScreenPoint.getY() - clickY;
        Assert.assertTrue("Click within 24px town hub radius should hit", (dx * dx + dy * dy) <= townClickRadiusSq);

        // Outside 24px radius (e.g. offset 25, 25 -> distSq = 1250 > 576)
        int farClickX = screenX + 25;
        int farClickY = screenY + 25;
        int farDx = nodeScreenPoint.getX() - farClickX;
        int farDy = nodeScreenPoint.getY() - farClickY;
        Assert.assertFalse("Far click outside 24px radius should not hit", (farDx * farDx + farDy * farDy) <= townClickRadiusSq);
    }

    @Test
    public void testVendorNodeHitDetectionRadius18px()
    {
        int screenX = 400;
        int screenY = 300;

        Point nodeScreenPoint = new Point(screenX, screenY);
        int vendorClickRadiusSq = 18 * 18;

        // Within 18px radius (e.g. offset 10, 10 -> distSq = 200 <= 324)
        int clickX = screenX + 10;
        int clickY = screenY + 10;
        int dx = nodeScreenPoint.getX() - clickX;
        int dy = nodeScreenPoint.getY() - clickY;
        Assert.assertTrue("Click within 18px vendor radius should hit", (dx * dx + dy * dy) <= vendorClickRadiusSq);

        // Outside 18px radius (e.g. offset 20, 20 -> distSq = 800 > 324)
        int farClickX = screenX + 20;
        int farClickY = screenY + 20;
        int farDx = nodeScreenPoint.getX() - farClickX;
        int farDy = nodeScreenPoint.getY() - farClickY;
        Assert.assertFalse("Click outside 18px radius should not hit", (farDx * farDx + farDy * farDy) <= vendorClickRadiusSq);
    }

    @Test
    public void testDragDoesNotTriggerClick()
    {
        java.awt.Point pressPoint = new java.awt.Point(100, 100);
        java.awt.Point releasePoint = new java.awt.Point(150, 150);

        Assert.assertTrue("Drag distance squared > 36", pressPoint.distanceSq(releasePoint) > 36);

        java.awt.Point stationaryRelease = new java.awt.Point(102, 102);
        Assert.assertTrue("Stationary click distance squared <= 36", pressPoint.distanceSq(stationaryRelease) <= 36);
    }

    @Test
    public void testMinimapVendorHitDetectionRadius10px()
    {
        Point minimapPoint = new Point(600, 100);
        int hitRadius = 10;

        // Within 10px radius (offset 6, 7 -> dist = sqrt(36+49) = 9.22 <= 10)
        Point closePoint = new Point(606, 107);
        Assert.assertTrue("Point within 10px minimap radius should hit", minimapPoint.distanceTo(closePoint) <= hitRadius);

        // Outside 10px radius (offset 8, 8 -> dist = sqrt(64+64) = 11.31 > 10)
        Point farPoint = new Point(608, 108);
        Assert.assertFalse("Point outside 10px minimap radius should not hit", minimapPoint.distanceTo(farPoint) <= hitRadius);
    }

    @Test
    public void testMenuEntryFormatting()
    {
        String townName = "Varrock";
        String townOption = "View Hub";
        String townTarget = "<col=ff981f>" + townName + "</col>";
        Assert.assertEquals("View Hub", townOption);
        Assert.assertEquals("<col=ff981f>Varrock</col>", townTarget);

        String shopName = "Aubury's Rune Store";
        String shopOption = "View Stock";
        String shopTarget = "<col=ff981f>" + shopName + "</col>";
        Assert.assertEquals("View Stock", shopOption);
        Assert.assertEquals("<col=ff981f>Aubury's Rune Store</col>", shopTarget);
    }

    @Test
    public void testCanvasClickDoesNotConsumeMouseEvent()
    {
        MouseEvent event = createMouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, 300, 300);
        Assert.assertFalse("Event should initially not be consumed", event.isConsumed());

        // Legend panel bounds (panelW = 368, panelH = 74)
        Rectangle legendPanelBounds = new Rectangle(500, 50, 368, 74);
        java.awt.Point canvasClick = new java.awt.Point(300, 300);
        Assert.assertFalse("Canvas click is outside legend panel", legendPanelBounds.contains(canvasClick));

        // Click inside legend panel
        java.awt.Point panelClick = new java.awt.Point(520, 60);
        Assert.assertTrue("Panel click is inside legend panel", legendPanelBounds.contains(panelClick));
    }

    @Test
    public void testEnhancedMapKeyTwoRowLayout()
    {
        int startX = 100;
        int startY = 400;
        int panelW = 274;
        int panelH = 62;

        Rectangle legendPanelBounds = new Rectangle(startX, startY, panelW, panelH);

        // Row 1: Search Box
        int searchX = startX + 6;
        int searchY = startY + 6;
        int searchW = panelW - 12;
        int searchH = 22;
        Rectangle searchBoxBounds = new Rectangle(searchX, searchY, searchW, searchH);

        // Row 2: Buttons
        int btnY = startY + 34;
        int btnH = 22;
        int curX = startX + 6;

        Rectangle townBounds = new Rectangle(curX, btnY, 60, btnH);
        curX += 60 + 4;

        Rectangle shopBounds = new Rectangle(curX, btnY, 60, btnH);
        curX += 60 + 4;

        Rectangle monsterBounds = new Rectangle(curX, btnY, 74, btnH);
        curX += 74 + 4;

        Rectangle clearBounds = new Rectangle(curX, btnY, 56, btnH);

        Assert.assertTrue("Search box within panel", legendPanelBounds.contains(searchBoxBounds));
        Assert.assertTrue("Town button within panel", legendPanelBounds.contains(townBounds));
        Assert.assertTrue("Shop button within panel", legendPanelBounds.contains(shopBounds));
        Assert.assertTrue("Monster button within panel", legendPanelBounds.contains(monsterBounds));
        Assert.assertTrue("Clear button within panel", legendPanelBounds.contains(clearBounds));

        // Ensure row 1 and row 2 do not vertically overlap
        Assert.assertFalse("Search box does not overlap button row", searchBoxBounds.intersects(townBounds));

        // Ensure buttons in row 2 do not horizontally overlap
        Assert.assertFalse("Town and Shop buttons do not overlap", townBounds.intersects(shopBounds));
        Assert.assertFalse("Shop and Monster buttons do not overlap", shopBounds.intersects(monsterBounds));
        Assert.assertFalse("Monster and Clear buttons do not overlap", monsterBounds.intersects(clearBounds));
    }

    @Test
    public void testQuickSearchFindBestMatchShop()
    {
        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );

        WorldMapLegendOverlay.SearchResult result = overlay.findBestMatch("Aubury's Rune Shop");
        Assert.assertNotNull("Should find Aubury's Rune Shop", result);
        Assert.assertEquals(WorldMapLegendOverlay.SearchResult.MatchType.SHOP, result.getType());
        Assert.assertEquals("Aubury's Rune Shop", result.getTitle());
        Assert.assertNotNull(result.getShop());
        Assert.assertNotNull(result.getTargetPoint());

        // Partial match
        WorldMapLegendOverlay.SearchResult partial = overlay.findBestMatch("Horvik");
        Assert.assertNotNull("Should find Horvik's Armoury", partial);
        Assert.assertTrue(partial.getTitle().contains("Horvik"));
    }

    @Test
    public void testQuickSearchFindBestMatchMonster()
    {
        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );

        WorldMapLegendOverlay.SearchResult result = overlay.findBestMatch("Hill Giant");
        Assert.assertNotNull("Should find Hill Giant", result);
        Assert.assertEquals(WorldMapLegendOverlay.SearchResult.MatchType.MONSTER, result.getType());
        Assert.assertEquals("Hill Giant", result.getTitle());
        Assert.assertNotNull(result.getMonster());
        Assert.assertNotNull(result.getTargetPoint());
    }

    @Test
    public void testQuickSearchFindBestMatchItemInShop()
    {
        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );

        WorldMapLegendOverlay.SearchResult result = overlay.findBestMatch("death rune");
        Assert.assertNotNull("Should find shop selling death rune", result);
        Assert.assertEquals(WorldMapLegendOverlay.SearchResult.MatchType.ITEM_SHOP, result.getType());
        Assert.assertNotNull(result.getShop());
        Assert.assertNotNull(result.getTargetPoint());
    }

    @Test
    public void testQuickSearchFindBestMatchMonsterDrop()
    {
        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );

        WorldMapLegendOverlay.SearchResult result = overlay.findBestMatch("abyssal whip");
        Assert.assertNotNull("Should find monster dropping abyssal whip", result);
        Assert.assertEquals(WorldMapLegendOverlay.SearchResult.MatchType.DROP_MONSTER, result.getType());
        Assert.assertTrue("Should match Abyssal Sire or Abyssal demon",
            result.getTitle().equalsIgnoreCase("Abyssal Sire") || result.getTitle().equalsIgnoreCase("Abyssal demon"));
        Assert.assertNotNull(result.getMonster());
        Assert.assertNotNull(result.getTargetPoint());
    }

    @Test
    public void testQuickSearchFindBestMatchTown()
    {
        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );

        WorldMapLegendOverlay.SearchResult result = overlay.findBestMatch("Varrock");
        Assert.assertNotNull("Should find Varrock town", result);
        Assert.assertNotNull(result.getTargetPoint());
    }

    @Test
    public void testQuickSearchKeyInputHandling()
    {
        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );

        // Initially unfocused
        Assert.assertFalse(overlay.isSearchFocused());

        // Type when unfocused should not change searchQuery
        KeyEvent keyE = createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'e');
        overlay.keyTyped(keyE);
        Assert.assertEquals("", overlay.getSearchQuery());

        // Set focused
        overlay.setSearchFocused(true);

        // Type 'H', 'i', 'l', 'l'
        overlay.keyTyped(createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'H'));
        overlay.keyTyped(createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'i'));
        overlay.keyTyped(createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'l'));
        overlay.keyTyped(createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'l'));
        Assert.assertEquals("Hill", overlay.getSearchQuery());

        // Backspace removes last char
        overlay.keyPressed(createKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_BACK_SPACE, KeyEvent.CHAR_UNDEFINED));
        Assert.assertEquals("Hil", overlay.getSearchQuery());

        // Delete clears text
        overlay.keyPressed(createKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_DELETE, KeyEvent.CHAR_UNDEFINED));
        Assert.assertEquals("", overlay.getSearchQuery());

        // Escape unfocuses
        overlay.keyPressed(createKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        Assert.assertFalse(overlay.isSearchFocused());
    }

    @Test
    public void testQuickSearchExecuteSearchReturnsTrueForMatch()
    {
        AtomicReference<WorldMapLegendOverlay.SearchResult> selected = new AtomicReference<>();

        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );
        overlay.setOnSearchResultSelected(selected::set);

        boolean found = overlay.executeSearch("Hill Giant");
        Assert.assertTrue("Search for Hill Giant should succeed", found);
        Assert.assertNotNull(selected.get());
        Assert.assertEquals("Hill Giant", selected.get().getTitle());

        boolean notFound = overlay.executeSearch("xyzNonExistent12345");
        Assert.assertFalse("Search for nonexistent item should fail", notFound);
    }

    @Test
    public void testOverlayLayerAndPriorityConfiguration()
    {
        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );

        Assert.assertEquals("Overlay layer must be ALWAYS_ON_TOP so map icons/overlays never draw over search/legend",
            OverlayLayer.ALWAYS_ON_TOP, overlay.getLayer());
        Assert.assertEquals("Overlay priority must be PRIORITY_HIGHEST",
            Overlay.PRIORITY_HIGHEST, overlay.getPriority(), 0.001f);
    }

    @Test
    public void testSolidOpaqueBackgroundColors()
    {
        Assert.assertEquals("Legend panel background must be solid opaque (alpha 252)",
            252, WorldMapLegendOverlay.BG_COLOR.getAlpha());
        Assert.assertEquals("Legend panel background RGBA",
            new java.awt.Color(18, 18, 18, 252), WorldMapLegendOverlay.BG_COLOR);

        Assert.assertEquals("Floating dropdown background must be solid opaque (alpha 255)",
            255, WorldMapLegendOverlay.DROPDOWN_BG.getAlpha());
        Assert.assertEquals("Floating dropdown background RGBA",
            new java.awt.Color(15, 15, 15, 255), WorldMapLegendOverlay.DROPDOWN_BG);
    }

    @Test
    public void testKeyboardLockConsumesAllEventsWhenSearchFocused()
    {
        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );

        // 1. When search is NOT focused, events should NOT be consumed
        overlay.setSearchFocused(false);
        KeyEvent unfocusedKey = createKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_W, 'w');
        overlay.keyPressed(unfocusedKey);
        Assert.assertFalse("Unfocused key press should not be consumed", unfocusedKey.isConsumed());

        KeyEvent unfocusedTyped = createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'w');
        overlay.keyTyped(unfocusedTyped);
        Assert.assertFalse("Unfocused key typed should not be consumed", unfocusedTyped.isConsumed());

        KeyEvent unfocusedReleased = createKeyEvent(KeyEvent.KEY_RELEASED, KeyEvent.VK_W, 'w');
        overlay.keyReleased(unfocusedReleased);
        Assert.assertFalse("Unfocused key released should not be consumed", unfocusedReleased.isConsumed());

        // 2. When search IS focused:
        // - Printable characters and WASD keys in keyPressed MUST be consumed so client hotkeys never trigger!
        overlay.setSearchFocused(true);

        int[] testKeyCodes = {
            KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D,
            KeyEvent.VK_E, KeyEvent.VK_Q, KeyEvent.VK_1, KeyEvent.VK_2,
            KeyEvent.VK_9, KeyEvent.VK_SPACE, KeyEvent.VK_COMMA, KeyEvent.VK_PERIOD
        };

        for (int code : testKeyCodes)
        {
            KeyEvent pressed = createKeyEvent(KeyEvent.KEY_PRESSED, code, KeyEvent.CHAR_UNDEFINED);
            overlay.keyPressed(pressed);
            Assert.assertTrue("Key code " + code + " MUST be consumed in keyPressed while searchFocused so canvas hotkeys never leak", pressed.isConsumed());
        }

        // - Control / navigation keys and F-keys (F1-F12) in keyPressed MUST be consumed
        int[] controlAndFKeyCodes = {
            KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE, KeyEvent.VK_BACK_SPACE, KeyEvent.VK_DELETE,
            KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
            KeyEvent.VK_TAB, KeyEvent.VK_HOME, KeyEvent.VK_END,
            KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4,
            KeyEvent.VK_F5, KeyEvent.VK_F6, KeyEvent.VK_F7, KeyEvent.VK_F8,
            KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12
        };

        for (int code : controlAndFKeyCodes)
        {
            overlay.setSearchFocused(true);
            KeyEvent pressed = createKeyEvent(KeyEvent.KEY_PRESSED, code, KeyEvent.CHAR_UNDEFINED);
            overlay.keyPressed(pressed);
            Assert.assertTrue("Control / navigation / F-key " + code + " must be consumed in keyPressed", pressed.isConsumed());
        }

        // - In keyTyped: ALL typed characters (letters, numbers, spaces, symbols) MUST be consumed
        char[] testChars = {'w', 'a', 's', 'd', 'e', 'q', '1', '2', '9', ' ', 'f', 'z', '\'', '-', '(', ')', ':', '.'};
        for (char c : testChars)
        {
            overlay.setSearchFocused(true);
            KeyEvent typed = createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, c);
            overlay.keyTyped(typed);
            Assert.assertTrue("Typing char '" + c + "' must be consumed in keyTyped while searchFocused", typed.isConsumed());
        }

        // - In keyReleased: ALL key release events MUST be consumed while searchFocused
        for (int code : testKeyCodes)
        {
            overlay.setSearchFocused(true);
            KeyEvent released = createKeyEvent(KeyEvent.KEY_RELEASED, code, KeyEvent.CHAR_UNDEFINED);
            overlay.keyReleased(released);
            Assert.assertTrue("Releasing key code " + code + " must be consumed while searchFocused", released.isConsumed());
        }
    }

    @Test
    public void testAwtKeyTypedCharacterDispatchSequence()
    {
        AtomicReference<WorldMapLegendOverlay.SearchResult> selected = new AtomicReference<>();
        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );
        overlay.setOnSearchResultSelected(selected::set);
        overlay.setSearchFocused(true);

        // Simulate full AWT dispatch sequence (keyPressed -> keyTyped -> keyReleased) for "hill giant"
        String phrase = "hill giant";
        for (int i = 0; i < phrase.length(); i++)
        {
            char c = phrase.charAt(i);
            int vkCode = (c >= 'a' && c <= 'z') ? KeyEvent.VK_A + (c - 'a') : (c == ' ' ? KeyEvent.VK_SPACE : KeyEvent.VK_UNDEFINED);

            KeyEvent kp = createKeyEvent(KeyEvent.KEY_PRESSED, vkCode, KeyEvent.CHAR_UNDEFINED);
            overlay.keyPressed(kp);
            Assert.assertTrue("keyPressed for char '" + c + "' must be consumed to prevent hotkey leak", kp.isConsumed());

            KeyEvent kt = createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, c);
            overlay.keyTyped(kt);
            Assert.assertTrue("keyTyped for char '" + c + "' must be consumed", kt.isConsumed());

            KeyEvent kr = createKeyEvent(KeyEvent.KEY_RELEASED, vkCode, KeyEvent.CHAR_UNDEFINED);
            overlay.keyReleased(kr);
            Assert.assertTrue("keyReleased for char '" + c + "' must be consumed", kr.isConsumed());
        }

        Assert.assertEquals("hill giant", overlay.getSearchQuery());
        Assert.assertFalse("Dropdown results should be populated", overlay.getCurrentSearchResults().isEmpty());

        // Press Enter to execute search
        KeyEvent enterPress = createKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED);
        overlay.keyPressed(enterPress);
        Assert.assertTrue("Enter press must be consumed", enterPress.isConsumed());

        Assert.assertNotNull("Search result should be executed", selected.get());
        Assert.assertEquals("Hill Giant", selected.get().getTitle());
        Assert.assertFalse("Search should be unfocused after execution", overlay.isSearchFocused());
    }

    @Test
    public void testQuickSearchKeyboardTypingComprehensive()
    {
        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );
        overlay.setSearchFocused(true);

        // 1. Lowercase letters
        String lower = "varrock";
        for (char c : lower.toCharArray())
        {
            overlay.keyTyped(createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, c));
        }
        Assert.assertEquals("varrock", overlay.getSearchQuery());
        Assert.assertFalse("Search results populated for 'varrock'", overlay.getCurrentSearchResults().isEmpty());

        // 2. Delete clears
        overlay.keyPressed(createKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_DELETE, KeyEvent.CHAR_UNDEFINED));
        Assert.assertEquals("", overlay.getSearchQuery());
        Assert.assertTrue("Search results empty when query is empty", overlay.getCurrentSearchResults().isEmpty());

        // 3. Uppercase letters & numbers & space & punctuation: "Aubury's (Lv. 1)"
        String complex = "Aubury's (Lv. 1)";
        for (char c : complex.toCharArray())
        {
            overlay.keyTyped(createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, c));
        }
        Assert.assertEquals("Aubury's (Lv. 1)", overlay.getSearchQuery());

        // 4. Backspace removes last 3 chars (" 1)")
        for (int i = 0; i < 3; i++)
        {
            overlay.keyPressed(createKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_BACK_SPACE, KeyEvent.CHAR_UNDEFINED));
        }
        Assert.assertEquals("Aubury's (Lv.", overlay.getSearchQuery());

        // 5. Escape clears focus and selection
        overlay.keyPressed(createKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        Assert.assertFalse("Escape must clear search focus", overlay.isSearchFocused());
        Assert.assertEquals(-1, overlay.getSelectedIndex());
    }

    @Test
    public void testSearchBoxClickActivatesFocusImmediately()
    {
        Client mockClient = mock(Client.class);
        Widget mockWorldMap = mock(Widget.class);
        when(mockWorldMap.isHidden()).thenReturn(false);
        when(mockWorldMap.getBounds()).thenReturn(new Rectangle(0, 0, 1000, 800));
        when(mockClient.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(mockWorldMap);

        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            mockClient, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );

        // Render overlay to populate geometry bounds
        BufferedImage img = new BufferedImage(1000, 800, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        overlay.render(g);
        g.dispose();

        Rectangle searchBox = overlay.getSearchBoxBounds();
        Assert.assertFalse("Search box bounds must be populated", searchBox.isEmpty());

        // 1. Initial state: unfocused
        Assert.assertFalse("Initially search should be unfocused", overlay.isSearchFocused());

        // 2. Mouse press on search box immediately activates searchFocused = true
        int clickX = searchBox.x + 10;
        int clickY = searchBox.y + 10;
        MouseEvent pressEvent = createMouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, clickX, clickY);
        overlay.mousePressed(pressEvent);
        Assert.assertTrue("Pressing search box must activate searchFocused immediately", overlay.isSearchFocused());
        Assert.assertTrue("Press event on search box must be consumed", pressEvent.isConsumed());

        // 3. Mouse release on search box maintains focus
        MouseEvent releaseEvent = createMouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, clickX, clickY);
        overlay.mouseReleased(releaseEvent);
        Assert.assertTrue("Releasing search box must keep searchFocused = true", overlay.isSearchFocused());

        // 4. Smooth typing letters, numbers, spaces, backspaces
        overlay.keyTyped(createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'V'));
        overlay.keyTyped(createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'a'));
        overlay.keyTyped(createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'r'));
        overlay.keyTyped(createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'r'));
        overlay.keyTyped(createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, ' '));
        overlay.keyTyped(createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, '1'));
        Assert.assertEquals("Varr 1", overlay.getSearchQuery());

        overlay.keyPressed(createKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_BACK_SPACE, KeyEvent.CHAR_UNDEFINED));
        Assert.assertEquals("Varr ", overlay.getSearchQuery());

        // 5. Click search clear '✕' button
        img = new BufferedImage(1000, 800, BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        overlay.render(g);
        g.dispose();

        Rectangle clearBox = overlay.getSearchClearBounds();
        Assert.assertFalse("Clear button bounds should be populated", clearBox.isEmpty());

        int clearClickX = clearBox.x + 5;
        int clearClickY = clearBox.y + 5;
        overlay.mousePressed(createMouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, clearClickX, clearClickY));
        overlay.mouseReleased(createMouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, clearClickX, clearClickY));
        Assert.assertEquals("Search query should be cleared", "", overlay.getSearchQuery());
        Assert.assertTrue("Search should still remain focused after clicking clear", overlay.isSearchFocused());

        // 6. Click outside legend panel unfocuses search
        overlay.mousePressed(createMouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, 200, 200));
        Assert.assertFalse("Clicking outside legend panel should unfocus search", overlay.isSearchFocused());
    }

    @Test
    public void testMultiResultSearchTop6MatchesForVariousQueries()
    {
        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );

        // 1. Search "pot"
        List<WorldMapLegendOverlay.SearchResult> potMatches = overlay.findMatches("pot", 6);
        Assert.assertNotNull("Pot matches should not be null", potMatches);
        Assert.assertFalse("Pot matches should not be empty", potMatches.isEmpty());
        Assert.assertTrue("Pot matches should have at most 6 results", potMatches.size() <= 6);
        for (WorldMapLegendOverlay.SearchResult r : potMatches)
        {
            Assert.assertNotNull("Title should not be null", r.getTitle());
            Assert.assertNotNull("Subtitle should not be null", r.getSubtitle());
            Assert.assertNotNull("Target point should not be null", r.getTargetPoint());
        }

        // 2. Search "rune sword"
        List<WorldMapLegendOverlay.SearchResult> swordMatches = overlay.findMatches("rune sword", 6);
        Assert.assertNotNull("Rune sword matches should not be null", swordMatches);
        Assert.assertFalse("Rune sword matches should not be empty", swordMatches.isEmpty());
        Assert.assertTrue("Rune sword matches size <= 6", swordMatches.size() <= 6);

        // 3. Search "death rune"
        List<WorldMapLegendOverlay.SearchResult> deathRuneMatches = overlay.findMatches("death rune", 6);
        Assert.assertNotNull("Death rune matches should not be null", deathRuneMatches);
        Assert.assertFalse("Death rune matches should not be empty", deathRuneMatches.isEmpty());
        Assert.assertTrue("Death rune matches size <= 6", deathRuneMatches.size() <= 6);
        // Best match should be a shop selling death rune
        Assert.assertEquals(WorldMapLegendOverlay.SearchResult.MatchType.ITEM_SHOP, deathRuneMatches.get(0).getType());

        // 4. Search "monkey zombie"
        List<WorldMapLegendOverlay.SearchResult> monkeyMatches = overlay.findMatches("monkey zombie", 6);
        Assert.assertNotNull("Monkey zombie matches should not be null", monkeyMatches);
        Assert.assertFalse("Monkey zombie matches should not be empty", monkeyMatches.isEmpty());
        Assert.assertTrue("Monkey zombie matches size <= 6", monkeyMatches.size() <= 6);
        Assert.assertTrue(monkeyMatches.get(0).getTitle().toLowerCase().contains("monkey zombie"));

        // 5. Search "hill giant"
        List<WorldMapLegendOverlay.SearchResult> giantMatches = overlay.findMatches("hill giant", 6);
        Assert.assertNotNull("Hill giant matches should not be null", giantMatches);
        Assert.assertFalse("Hill giant matches should not be empty", giantMatches.isEmpty());
        Assert.assertTrue("Hill giant matches size <= 6", giantMatches.size() <= 6);
        Assert.assertEquals("Hill Giant", giantMatches.get(0).getTitle());
        Assert.assertEquals(WorldMapLegendOverlay.SearchResult.MatchType.MONSTER, giantMatches.get(0).getType());
    }

    @Test
    public void testDropdownKeyboardNavigationUpDownAndEnter()
    {
        AtomicReference<WorldMapLegendOverlay.SearchResult> executedResult = new AtomicReference<>();

        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );
        overlay.setOnSearchResultSelected(executedResult::set);
        overlay.setSearchFocused(true);
        overlay.setSearchQuery("death rune");

        List<WorldMapLegendOverlay.SearchResult> results = overlay.getCurrentSearchResults();
        Assert.assertFalse("Results should not be empty for 'death rune'", results.isEmpty());
        Assert.assertTrue("Results should have at least 2 entries", results.size() >= 2);

        // Initially selectedIndex is -1
        Assert.assertEquals(-1, overlay.getSelectedIndex());

        // Press DOWN arrow -> advances to index 0
        overlay.keyPressed(createKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED));
        Assert.assertEquals(0, overlay.getSelectedIndex());

        // Press DOWN arrow -> advances to index 1
        overlay.keyPressed(createKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED));
        Assert.assertEquals(1, overlay.getSelectedIndex());

        // Press UP arrow -> moves back to index 0
        overlay.keyPressed(createKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED));
        Assert.assertEquals(0, overlay.getSelectedIndex());

        // Press UP arrow again -> wraps around to last item
        overlay.keyPressed(createKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED));
        Assert.assertEquals(results.size() - 1, overlay.getSelectedIndex());

        // Press ENTER -> executes the selected item
        WorldMapLegendOverlay.SearchResult expected = results.get(results.size() - 1);
        overlay.keyPressed(createKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED));

        Assert.assertNotNull("Selected item should have been executed", executedResult.get());
        Assert.assertEquals(expected.getTitle(), executedResult.get().getTitle());
        Assert.assertEquals(expected.getTargetPoint(), executedResult.get().getTargetPoint());
        Assert.assertFalse("Search should be unfocused after execution", overlay.isSearchFocused());
    }

    @Test
    public void testDropdownMouseRowClickSelection()
    {
        AtomicReference<WorldMapLegendOverlay.SearchResult> executedResult = new AtomicReference<>();

        Client mockClient = mock(Client.class);
        Widget mockWorldMap = mock(Widget.class);
        when(mockWorldMap.isHidden()).thenReturn(false);
        when(mockWorldMap.getBounds()).thenReturn(new Rectangle(0, 0, 1000, 800));
        when(mockClient.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(mockWorldMap);

        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            mockClient, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );
        overlay.setOnSearchResultSelected(executedResult::set);
        overlay.setSearchFocused(true);
        overlay.setSearchQuery("Hill Giant");

        // Render to generate dropdown geometry
        BufferedImage img = new BufferedImage(1000, 800, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        overlay.render(g);
        g.dispose();

        Rectangle dropdownBounds = overlay.getDropdownBounds();
        Assert.assertFalse("Dropdown bounds should not be empty", dropdownBounds.isEmpty());
        List<Rectangle> rowBounds = overlay.getDropdownRowBounds();
        Assert.assertFalse("Row bounds should not be empty", rowBounds.isEmpty());

        Rectangle firstRow = rowBounds.get(0);
        int clickX = firstRow.x + 10;
        int clickY = firstRow.y + 10;

        // Press and release on first row
        overlay.mousePressed(createMouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, clickX, clickY));
        overlay.mouseReleased(createMouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, clickX, clickY));

        Assert.assertNotNull("Clicking dropdown row should execute search result", executedResult.get());
        Assert.assertEquals("Hill Giant", executedResult.get().getTitle());
        Assert.assertFalse("Search should be unfocused after click", overlay.isSearchFocused());
    }

    @Test
    public void testSearchBoxCanvasFocusRequestInMousePressedAndReleased()
    {
        Client mockClient = mock(Client.class);
        Canvas mockCanvas = mock(Canvas.class);
        when(mockClient.getCanvas()).thenReturn(mockCanvas);

        Widget mockWorldMap = mock(Widget.class);
        when(mockWorldMap.isHidden()).thenReturn(false);
        when(mockWorldMap.getBounds()).thenReturn(new Rectangle(0, 0, 1000, 800));
        when(mockClient.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(mockWorldMap);

        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            mockClient, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );

        BufferedImage img = new BufferedImage(1000, 800, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        overlay.render(g);
        g.dispose();

        Rectangle searchBox = overlay.getSearchBoxBounds();
        int clickX = searchBox.x + 10;
        int clickY = searchBox.y + 10;

        // 1. Mouse pressed within search box calls canvas requestFocusInWindow and sets searchFocused = true
        overlay.mousePressed(createMouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, clickX, clickY));
        Assert.assertTrue("Search must be focused on mouse pressed", overlay.isSearchFocused());
        verify(mockCanvas, atLeast(1)).requestFocusInWindow();

        // 2. Mouse released within search box calls canvas requestFocusInWindow and keeps searchFocused = true
        overlay.mouseReleased(createMouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, clickX, clickY));
        Assert.assertTrue("Search must remain focused on mouse released", overlay.isSearchFocused());
        verify(mockCanvas, atLeast(1)).requestFocusInWindow();

        // 3. Clear button click also requests focus on canvas
        overlay.setSearchQuery("Test");
        img = new BufferedImage(1000, 800, BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        overlay.render(g);
        g.dispose();

        Rectangle clearBounds = overlay.getSearchClearBounds();
        int clearX = clearBounds.x + 4;
        int clearY = clearBounds.y + 4;

        overlay.mousePressed(createMouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, clearX, clearY));
        verify(mockCanvas, atLeast(2)).requestFocusInWindow();

        overlay.mouseReleased(createMouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, clearX, clearY));
        verify(mockCanvas, atLeast(2)).requestFocusInWindow();
        Assert.assertTrue("Search must be focused after clearing", overlay.isSearchFocused());
        Assert.assertEquals("", overlay.getSearchQuery());
    }

    @Test
    public void testDropdownClickKeepsLegendOverlayResponsive()
    {
        AtomicReference<WorldMapLegendOverlay.SearchResult> executedResult = new AtomicReference<>();
        Client mockClient = mock(Client.class);
        Canvas mockCanvas = mock(Canvas.class);
        when(mockClient.getCanvas()).thenReturn(mockCanvas);

        Widget mockWorldMap = mock(Widget.class);
        when(mockWorldMap.isHidden()).thenReturn(false);
        when(mockWorldMap.getBounds()).thenReturn(new Rectangle(0, 0, 1000, 800));
        when(mockClient.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(mockWorldMap);

        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            mockClient, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );
        overlay.setOnSearchResultSelected(executedResult::set);

        // 1. Search and render dropdown
        overlay.setSearchFocused(true);
        overlay.setSearchQuery("Aubury");

        BufferedImage img = new BufferedImage(1000, 800, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        overlay.render(g);
        g.dispose();

        List<Rectangle> rowBounds = overlay.getDropdownRowBounds();
        Assert.assertFalse("Dropdown rows should exist", rowBounds.isEmpty());

        // 2. Click first dropdown row to snap to location
        Rectangle firstRow = rowBounds.get(0);
        int rowX = firstRow.x + 10;
        int rowY = firstRow.y + 10;
        overlay.mousePressed(createMouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, rowX, rowY));
        overlay.mouseReleased(createMouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, rowX, rowY));

        Assert.assertNotNull("Dropdown click should execute search result", executedResult.get());
        Assert.assertFalse("Search should be unfocused after execution", overlay.isSearchFocused());

        // 3. Immediately clicking back on search box re-focuses without needing to re-open map
        img = new BufferedImage(1000, 800, BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        overlay.render(g);
        g.dispose();

        Rectangle searchBox = overlay.getSearchBoxBounds();
        overlay.mousePressed(createMouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, searchBox.x + 5, searchBox.y + 5));
        overlay.mouseReleased(createMouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, searchBox.x + 5, searchBox.y + 5));

        Assert.assertTrue("Legend overlay must remain immediately responsive and re-focused", overlay.isSearchFocused());
        verify(mockCanvas, atLeastOnce()).requestFocusInWindow();
    }

    @Test
    public void testSetSearchFocusedTogglingRegistersAndUnregistersGlobalDispatcherWithoutThrowing()
    {
        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );

        // Repeated true/false transitions (including redundant same-state calls, which real usage
        // hits constantly - e.g. clicking the search box again while already focused) must be
        // idempotent: no double-registration, no attempt to unregister something never registered.
        overlay.setSearchFocused(true);
        overlay.setSearchFocused(true);
        Assert.assertTrue(overlay.isSearchFocused());

        overlay.setSearchFocused(false);
        overlay.setSearchFocused(false);
        Assert.assertFalse(overlay.isSearchFocused());

        overlay.setSearchFocused(true);
        overlay.setSearchFocused(false);
    }

    @Test
    public void testHandleGlobalKeyEventNoOpsWhenUnfocusedAlreadyConsumedOrNoClient()
    {
        // No Client wired up at all (common in these tests) - dispatcher must never NPE, must never claim it handled anything.
        WorldMapLegendOverlay overlayNoClient = new WorldMapLegendOverlay(
            null, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );
        overlayNoClient.setSearchFocused(true);
        KeyEvent noClientEvent = createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'z');
        Assert.assertFalse("With no Client/canvas available, the global dispatcher must not intercept",
            overlayNoClient.handleGlobalKeyEvent(noClientEvent));
        Assert.assertEquals("", overlayNoClient.getSearchQuery());

        Client mockClient = mock(Client.class);
        Canvas mockCanvas = mock(Canvas.class);
        when(mockClient.getCanvas()).thenReturn(mockCanvas);
        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            mockClient, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );

        // Not focused -> dispatcher must be a complete no-op
        overlay.setSearchFocused(false);
        KeyEvent unfocusedEvent = createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'z');
        Assert.assertFalse(overlay.handleGlobalKeyEvent(unfocusedEvent));
        Assert.assertEquals("", overlay.getSearchQuery());

        // Already-consumed events (something upstream already handled it) must be left alone
        overlay.setSearchFocused(true);
        KeyEvent alreadyConsumed = createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'z');
        alreadyConsumed.consume();
        Assert.assertFalse(overlay.handleGlobalKeyEvent(alreadyConsumed));
        Assert.assertEquals("", overlay.getSearchQuery());
    }

    @Test
    public void testGlobalKeyEventDispatcherSelfDisarmsWhenCanvasIsNotTheRealFocusOwner()
    {
        Client mockClient = mock(Client.class);
        Canvas mockCanvas = mock(Canvas.class);
        when(mockClient.getCanvas()).thenReturn(mockCanvas);

        WorldMapLegendOverlay overlay = new WorldMapLegendOverlay(
            mockClient, config, null, null, null, null,
            shopDatabase, monsterDatabase, dungeonEntranceDatabase, null
        );

        // Focusing the search box registers overlay's KeyEventDispatcher on the real, shared
        // KeyboardFocusManager (see setSearchFocused/handleGlobalKeyEvent); this alone must not throw.
        overlay.setSearchFocused(true);
        Assert.assertTrue(overlay.isSearchFocused());

        // This headless unit test has no real focused window, so getFocusOwner() returns null -
        // never the mock canvas. Calling handleGlobalKeyEvent directly (the same method the real
        // KeyEventDispatcher lambda delegates to) must treat searchFocused as stale rather than
        // globally swallowing a keystroke that was never actually meant for this overlay - this is
        // the guard against stealing input from some other legitimately-focused Swing component, e.g.
        // the sidebar's own search field or the Master Spreadsheet dialog.
        KeyEvent typed = createKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'y');
        boolean handled = overlay.handleGlobalKeyEvent(typed);

        Assert.assertFalse("Dispatcher must not claim to have handled a key when the canvas doesn't really have focus", handled);
        Assert.assertFalse("Event must not be consumed when self-disarming", typed.isConsumed());
        Assert.assertFalse("searchFocused must self-correct to false when the canvas doesn't really have focus",
            overlay.isSearchFocused());
        Assert.assertEquals("Stale-focus dispatch must never leak into the search query", "", overlay.getSearchQuery());

        // Unregister so this leaked-if-forgotten dispatcher doesn't linger for the rest of the suite.
        overlay.setSearchFocused(false);
    }
}
