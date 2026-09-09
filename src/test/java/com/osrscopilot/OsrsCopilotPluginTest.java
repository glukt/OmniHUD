package com.osrscopilot;

import com.google.gson.Gson;
import com.osrscopilot.data.DungeonEntranceDatabase;
import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.ShopLiveStockManager;
import com.osrscopilot.data.ShoppingCartManager;
import com.osrscopilot.data.SlayerTaskManager;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterSpawnZone;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.TownNode;
import com.osrscopilot.loot.model.LootItem;
import com.osrscopilot.map.MinimapVendorOverlay;
import com.osrscopilot.map.WorldMapFocusBeaconOverlay;
import com.osrscopilot.map.WorldMapMonsterZoneOverlay;
import com.osrscopilot.ui.OsrsCopilotPanel;
import com.osrscopilot.util.IconCache;
import java.awt.Rectangle;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.api.worldmap.WorldMapData;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OsrsCopilotPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(OsrsCopilotPlugin.class);
        RuneLite.main(args);
    }

    private OsrsCopilotPlugin plugin;
    private Client client;
    private ClientThread clientThread;
    private ClientToolbar clientToolbar;
    private OsrsCopilotConfig config;
    private ConfigManager configManager;
    private ShopDatabase shopDatabase;
    private MonsterDatabase monsterDatabase;
    private DungeonEntranceDatabase dungeonEntranceDatabase;
    private WorldMapFocusBeaconOverlay beaconOverlay;
    private WorldMapMonsterZoneOverlay monsterZoneOverlay;
    private MinimapVendorOverlay minimapOverlay;
    private OsrsCopilotPanel panel;
    private NavigationButton navButton;
    private WorldMap worldMap;
    private WorldMapData worldMapData;

    @Before
    public void setUp()
    {
        plugin = new OsrsCopilotPlugin();
        client = mock(Client.class);
        clientThread = mock(ClientThread.class);
        clientToolbar = mock(ClientToolbar.class);
        config = mock(OsrsCopilotConfig.class);
        configManager = mock(ConfigManager.class);
        beaconOverlay = mock(WorldMapFocusBeaconOverlay.class);
        monsterZoneOverlay = mock(WorldMapMonsterZoneOverlay.class);
        minimapOverlay = mock(MinimapVendorOverlay.class);
        panel = mock(OsrsCopilotPanel.class);
        navButton = NavigationButton.builder().tooltip("Enhanced World Map").build();
        worldMap = mock(WorldMap.class);
        worldMapData = mock(WorldMapData.class);

        // Make clientThread run invokeLater tasks immediately
        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            if (r != null)
            {
                r.run();
            }
            return null;
        }).when(clientThread).invokeLater(any(Runnable.class));

        when(client.getWorldMap()).thenReturn(worldMap);
        when(worldMap.getWorldMapData()).thenReturn(worldMapData);

        // runConfigMigrations() sweeps the legacy config group; default to "nothing to copy".
        when(configManager.getConfigurationKeys(anyString())).thenReturn(Collections.emptyList());

        Gson gson = new Gson();
        shopDatabase = new ShopDatabase(gson);
        shopDatabase.load();
        monsterDatabase = new MonsterDatabase(gson);
        monsterDatabase.load();
        dungeonEntranceDatabase = new DungeonEntranceDatabase();

        setField(plugin, "client", client);
        setField(plugin, "clientThread", clientThread);
        setField(plugin, "clientToolbar", clientToolbar);
        setField(plugin, "config", config);
        setField(plugin, "configManager", configManager);
        setField(plugin, "shopDatabase", shopDatabase);
        setField(plugin, "monsterDatabase", monsterDatabase);
        setField(plugin, "dungeonEntranceDatabase", dungeonEntranceDatabase);
        setField(plugin, "beaconOverlay", beaconOverlay);
        setField(plugin, "monsterZoneOverlay", monsterZoneOverlay);
        setField(plugin, "minimapOverlay", minimapOverlay);
        setField(plugin, "panel", panel);
        setField(plugin, "navButton", navButton);
    }

    private static void setField(Object target, String fieldName, Object value)
    {
        try
        {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed setting field " + fieldName, e);
        }
    }

    @Test
    public void testMinimapRightClickWithin24pxCreatesMenuEntry()
    {
        when(config.showMinimapVendors()).thenReturn(true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);

        Shop aubury = shopDatabase.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull(aubury);

        Point minimapPoint = new Point(600, 100);
        Point mousePos = new Point(615, 115); // dist = sqrt(15^2+15^2) = 21.21 <= 24px
        when(client.getMouseCanvasPosition()).thenReturn(mousePos);

        MinimapVendorOverlay.MinimapShopMarker marker = new MinimapVendorOverlay.MinimapShopMarker(aubury, minimapPoint, null);
        when(minimapOverlay.getActiveMarkers()).thenReturn(Collections.singletonList(marker));

        MenuEntry mockEntry = mock(MenuEntry.class);
        when(client.createMenuEntry(-1)).thenReturn(mockEntry);
        when(mockEntry.setOption(anyString())).thenReturn(mockEntry);
        when(mockEntry.setTarget(anyString())).thenReturn(mockEntry);
        when(mockEntry.setType(any(MenuAction.class))).thenReturn(mockEntry);
        AtomicReference<java.util.function.Consumer<MenuEntry>> clickConsumer = new AtomicReference<>();
        when(mockEntry.onClick(any())).thenAnswer(inv -> {
            clickConsumer.set(inv.getArgument(0));
            return mockEntry;
        });

        MenuEntry preExistingEntry = mock(MenuEntry.class);
        MenuOpened menuOpened = new MenuOpened();
        menuOpened.setMenuEntries(new MenuEntry[]{preExistingEntry});

        plugin.onMenuOpened(menuOpened);

        verify(client, times(1)).createMenuEntry(-1);
        verify(mockEntry, times(1)).setOption("View Shop");
        verify(mockEntry, times(1)).setTarget("<col=ff981f>" + aubury.getName() + "</col>");
        verify(mockEntry, times(1)).setType(MenuAction.RUNELITE);
        Assert.assertNotNull("onClick handler must be registered", clickConsumer.get());

        // This is the real regression check for the "right-click does nothing" bug: a MenuEntry
        // built via client.createMenuEntry(-1) inside onMenuOpened is NOT automatically shown in
        // the popup - it must be committed back via event.setMenuEntries(...). Assert against the
        // actual committed array rather than invoking the captured onClick consumer directly,
        // which would bypass the real commit path and pass even if setMenuEntries was never called.
        MenuEntry[] committedEntries = menuOpened.getMenuEntries();
        Assert.assertEquals("pre-existing entries must be preserved", 2, committedEntries.length);
        Assert.assertSame("original entry must remain in place", preExistingEntry, committedEntries[0]);
        Assert.assertSame("new entry must be appended and committed via setMenuEntries", mockEntry, committedEntries[1]);

        // Test the onClick handler invokes openShopInSidePanel
        clickConsumer.get().accept(mockEntry);
        flushEdt();
        verify(panel, times(1)).openShop(aubury);
        verify(clientToolbar, times(1)).openPanel(navButton);
    }

    @Test
    public void testMinimapRightClickOutside24pxDoesNotCreateMenuEntry()
    {
        when(config.showMinimapVendors()).thenReturn(true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);

        Shop aubury = shopDatabase.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull(aubury);

        Point minimapPoint = new Point(600, 100);
        Point mousePos = new Point(630, 100); // dist = 30px > 24px
        when(client.getMouseCanvasPosition()).thenReturn(mousePos);

        MinimapVendorOverlay.MinimapShopMarker marker = new MinimapVendorOverlay.MinimapShopMarker(aubury, minimapPoint, null);
        when(minimapOverlay.getActiveMarkers()).thenReturn(Collections.singletonList(marker));

        MenuOpened menuOpened = new MenuOpened();
        menuOpened.setMenuEntries(new MenuEntry[0]);

        plugin.onMenuOpened(menuOpened);

        verify(client, never()).createMenuEntry(anyInt());
    }

    @Test
    public void testNpcRightClickCommitsViewShopEntryToMenuEntries()
    {
        // Covers the other onMenuOpened block with the same "created but never committed" flaw:
        // the NPC-right-click "View Shop" entry. Uses a spy so getShopByNpcId resolves regardless
        // of live ETL data (npcId is 0 for wiki-sourced shops, see GEMINI.md section 5).
        ShopDatabase shopDbSpy = org.mockito.Mockito.spy(shopDatabase);
        Shop aubury = shopDatabase.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull(aubury);
        org.mockito.Mockito.doReturn(aubury).when(shopDbSpy).getShopByNpcId(9999);
        setField(plugin, "shopDatabase", shopDbSpy);
        setField(plugin, "liveStockManager", mock(com.osrscopilot.data.ShopLiveStockManager.class));

        MenuEntry mockEntry = mock(MenuEntry.class);
        when(client.createMenuEntry(-1)).thenReturn(mockEntry);
        when(mockEntry.setOption(anyString())).thenReturn(mockEntry);
        when(mockEntry.setTarget(anyString())).thenReturn(mockEntry);
        when(mockEntry.setType(any(MenuAction.class))).thenReturn(mockEntry);
        when(mockEntry.onClick(any())).thenReturn(mockEntry);

        MenuEntry npcExamineEntry = mock(MenuEntry.class);
        when(npcExamineEntry.getType()).thenReturn(MenuAction.NPC_FIRST_OPTION);
        when(npcExamineEntry.getIdentifier()).thenReturn(9999);

        MenuOpened menuOpened = new MenuOpened();
        menuOpened.setMenuEntries(new MenuEntry[]{npcExamineEntry});

        plugin.onMenuOpened(menuOpened);

        MenuEntry[] committedEntries = menuOpened.getMenuEntries();
        Assert.assertEquals("new entry must be appended and committed via setMenuEntries", 2, committedEntries.length);
        Assert.assertSame(npcExamineEntry, committedEntries[0]);
        Assert.assertSame(mockEntry, committedEntries[1]);
    }

    @Test
    public void testOpenShopInSidePanelRunsOnEdtAndOpensPanel()
    {
        Shop aubury = shopDatabase.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull(aubury);

        plugin.openShopInSidePanel(aubury);
        flushEdt();

        verify(clientToolbar, times(1)).openPanel(navButton);
        verify(panel, times(1)).openShop(aubury);
    }

    @Test
    public void testIsWorldMapHidden()
    {
        // When widget is null
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(null);
        when(client.getWidget(InterfaceID.Worldmap.OVERVIEW_CONTAINER)).thenReturn(null);
        Assert.assertTrue(plugin.isWorldMapHidden());

        // When widget is hidden
        Widget hiddenWidget = mock(Widget.class);
        when(hiddenWidget.isHidden()).thenReturn(true);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(hiddenWidget);
        Assert.assertTrue(plugin.isWorldMapHidden());

        // When widget is visible
        Widget visibleWidget = mock(Widget.class);
        when(visibleWidget.isHidden()).thenReturn(false);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(visibleWidget);
        Assert.assertFalse(plugin.isWorldMapHidden());
    }

    @Test
    public void testTriggerOpenWorldMapLinksTheWorldMapInterface()
    {
        // Map is closed (FRAME widget absent / hidden).
        plugin.triggerOpenWorldMap();

        // The map opens by linking interface 595 into the client's floating-window layer -
        // not by faking a click on the minimap orb (whose op is just a sound).
        verify(client, times(1)).openInterface(anyInt(), eq(InterfaceID.WORLDMAP), eq(WidgetModalMode.NON_MODAL));
        verify(client, never()).menuAction(anyInt(), anyInt(), any(MenuAction.class), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    public void testClickingTheMapCrossClosesTheInterfaceWeOpened()
    {
        net.runelite.api.WidgetNode node = mock(net.runelite.api.WidgetNode.class);
        when(client.openInterface(anyInt(), eq(InterfaceID.WORLDMAP), eq(WidgetModalMode.NON_MODAL))).thenReturn(node);

        plugin.triggerOpenWorldMap();

        Widget cross = mock(Widget.class);
        when(cross.getId()).thenReturn((InterfaceID.WORLDMAP << 16) | 38);
        MenuOptionClicked close = mock(MenuOptionClicked.class);
        when(close.getWidget()).thenReturn(cross);
        when(close.getMenuOption()).thenReturn("Close");

        plugin.onMenuOptionClicked(close);

        verify(client, times(1)).closeInterface(node, true);
        verify(close, times(1)).consume();
    }

    @Test
    public void testRequestPendingFocusWhenMapHiddenTriggersOpenMap()
    {
        Widget hiddenMap = mock(Widget.class);
        when(hiddenMap.isHidden()).thenReturn(true);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(hiddenMap);

        WorldPoint targetPt = new WorldPoint(3200, 3200, 0);
        plugin.requestPendingFocus(targetPt, "Target Label");

        Assert.assertEquals(targetPt, plugin.getPendingFocusTarget());
        Assert.assertEquals("Target Label", plugin.getPendingFocusLabel());
        Assert.assertEquals(0, plugin.getPendingFocusAttempts());

        verify(client, times(1)).openInterface(anyInt(), eq(InterfaceID.WORLDMAP), eq(WidgetModalMode.NON_MODAL));
        verify(worldMap, never()).setWorldMapPositionTarget(any(WorldPoint.class));
        verify(beaconOverlay, never()).triggerBeacon(any(WorldPoint.class), anyString());
    }

    @Test
    public void testRequestPendingFocusWhenMapVisibleDoesNotTriggerOpenMap()
    {
        Widget visibleMap = mock(Widget.class);
        when(visibleMap.isHidden()).thenReturn(false);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(visibleMap);

        WorldPoint targetPt = new WorldPoint(3200, 3200, 0);
        plugin.requestPendingFocus(targetPt, "Target Label");

        Assert.assertEquals(targetPt, plugin.getPendingFocusTarget());
        Assert.assertEquals("Target Label", plugin.getPendingFocusLabel());
        Assert.assertEquals(0, plugin.getPendingFocusAttempts());

        verify(client, never()).openInterface(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void testOnGameTickSnapsPositionWhenMapOpens()
    {
        Widget mapWidget = mock(Widget.class);
        when(mapWidget.isHidden()).thenReturn(true);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(mapWidget);

        WorldPoint targetPt = new WorldPoint(3200, 3200, 0);
        plugin.requestPendingFocus(targetPt, "Target Label");

        // Map is now opened
        when(mapWidget.isHidden()).thenReturn(false);
        plugin.onGameTick(new GameTick());

        verify(worldMap, times(1)).setWorldMapPositionTarget(targetPt);
        verify(beaconOverlay, times(1)).triggerBeacon(targetPt, "Target Label");
        Assert.assertNull(plugin.getPendingFocusTarget());
        Assert.assertNull(plugin.getPendingFocusLabel());
    }

    @Test
    public void testOnGameTickOpensMapOnceThenWaitsAndGivesUpAt40()
    {
        Widget hiddenMap = mock(Widget.class);
        when(hiddenMap.isHidden()).thenReturn(true);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(hiddenMap);

        WorldPoint targetPt = new WorldPoint(3200, 3200, 0);
        plugin.requestPendingFocus(targetPt, "Target Label");

        // requestPendingFocus opened it once.
        verify(client, times(1)).openInterface(anyInt(), eq(InterfaceID.WORLDMAP), eq(WidgetModalMode.NON_MODAL));

        // It never opens again on subsequent ticks - just waits for the map to load.
        for (int i = 1; i < 40; i++)
        {
            plugin.onGameTick(new GameTick());
            Assert.assertEquals(i, plugin.getPendingFocusAttempts());
            Assert.assertEquals(targetPt, plugin.getPendingFocusTarget());
        }
        verify(client, times(1)).openInterface(anyInt(), eq(InterfaceID.WORLDMAP), eq(WidgetModalMode.NON_MODAL));

        // Tick 40: attempts reaches 40 -> give up and clear.
        plugin.onGameTick(new GameTick());
        Assert.assertEquals(0, plugin.getPendingFocusAttempts());
        Assert.assertNull(plugin.getPendingFocusTarget());
        Assert.assertNull(plugin.getPendingFocusLabel());

        verify(worldMap, never()).setWorldMapPositionTarget(any(WorldPoint.class));
        verify(beaconOverlay, never()).triggerBeacon(any(WorldPoint.class), anyString());
    }

    @Test
    public void testFocusShopOnMapAutoOpensWorldMapWhenHidden()
    {
        Widget mapWidget = mock(Widget.class);
        when(mapWidget.isHidden()).thenReturn(true);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(mapWidget);

        Shop aubury = shopDatabase.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull(aubury);

        plugin.focusShopOnMap(aubury);

        // Verifies trigger was called to open the map
        verify(client, times(1)).openInterface(anyInt(), eq(InterfaceID.WORLDMAP), eq(WidgetModalMode.NON_MODAL));
        verify(worldMap, never()).setWorldMapPositionTarget(any(WorldPoint.class));

        // When map becomes visible on game tick, it snaps
        when(mapWidget.isHidden()).thenReturn(false);
        plugin.onGameTick(new GameTick());

        verify(worldMap, times(1)).setWorldMapPositionTarget(aubury.getWorldPoint());
        verify(beaconOverlay, times(1)).triggerBeacon(aubury.getWorldPoint(), aubury.getName());
    }

    @Test
    public void testFocusTownOnMapAutoOpensWorldMapWhenHidden()
    {
        Widget mapWidget = mock(Widget.class);
        when(mapWidget.isHidden()).thenReturn(true);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(mapWidget);

        TownNode town = TownNode.builder()
            .name("Varrock")
            .worldX(3210)
            .worldY(3424)
            .worldPlane(0)
            .build();

        plugin.focusTownOnMap(town);

        verify(client, times(1)).openInterface(anyInt(), eq(InterfaceID.WORLDMAP), eq(WidgetModalMode.NON_MODAL));
        verify(worldMap, never()).setWorldMapPositionTarget(any(WorldPoint.class));

        when(mapWidget.isHidden()).thenReturn(false);
        plugin.onGameTick(new GameTick());

        verify(worldMap, times(1)).setWorldMapPositionTarget(town.getWorldPoint());
        verify(beaconOverlay, times(1)).triggerBeacon(town.getWorldPoint(), "Varrock");
    }

    @Test
    public void testFocusMonsterOnMapAutoOpensWorldMapWhenHidden()
    {
        Widget mapWidget = mock(Widget.class);
        when(mapWidget.isHidden()).thenReturn(true);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(mapWidget);

        Monster hillGiant = monsterDatabase.getMonsterByName("Hill Giant");
        if (hillGiant == null)
        {
            hillGiant = monsterDatabase.getAllMonsters().get(0);
        }
        Assert.assertNotNull(hillGiant);

        plugin.focusMonsterOnMap(hillGiant);

        verify(monsterZoneOverlay, times(1)).setFocusedMonster(hillGiant);
        verify(client, times(1)).openInterface(anyInt(), eq(InterfaceID.WORLDMAP), eq(WidgetModalMode.NON_MODAL));
        verify(worldMap, never()).setWorldMapPositionTarget(any(WorldPoint.class));

        when(mapWidget.isHidden()).thenReturn(false);
        plugin.onGameTick(new GameTick());

        verify(worldMap, times(1)).setWorldMapPositionTarget(any(WorldPoint.class));
        verify(beaconOverlay, times(1)).triggerBeacon(any(WorldPoint.class), anyString());
    }

    @Test
    public void testFocusMonsterZoneUndergroundUsesSurfaceEntrance()
    {
        Widget mapWidget = mock(Widget.class);
        when(mapWidget.isHidden()).thenReturn(true);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(mapWidget);

        WorldPoint underPoint = new WorldPoint(3117, 9852, 0); // Edgeville Dungeon
        WorldPoint surfacePoint = new WorldPoint(3097, 3468, 0);

        Monster monster = Monster.builder().name("Chaos Druid").build();
        MonsterSpawnZone zone = MonsterSpawnZone.builder()
            .centerPoint(underPoint)
            .surfaceEntrance(surfacePoint)
            .dungeonName("Edgeville Dungeon")
            .entranceVerified(true)
            .spawnCount(4)
            .build();

        plugin.focusMonsterZoneOnMap(monster, zone);

        verify(client, times(1)).openInterface(anyInt(), eq(InterfaceID.WORLDMAP), eq(WidgetModalMode.NON_MODAL));
        verify(worldMap, never()).setWorldMapPositionTarget(any(WorldPoint.class));

        when(mapWidget.isHidden()).thenReturn(false);
        plugin.onGameTick(new GameTick());

        verify(worldMap, times(1)).setWorldMapPositionTarget(surfacePoint);
        verify(beaconOverlay, times(1)).triggerBeacon(eq(surfacePoint), eq("[Dungeon Entrance] Edgeville Dungeon (Chaos Druid Spawn)"));
    }

    @Test
    public void testUnverifiedDungeonEntranceGetsApproximateBeacon()
    {
        Widget mapWidget = mock(Widget.class);
        when(mapWidget.isHidden()).thenReturn(true);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(mapWidget);

        WorldPoint underPoint = new WorldPoint(2400, 9800, 0);
        WorldPoint surfacePoint = new WorldPoint(2580, 3030, 0);

        Monster monster = Monster.builder().name("Ogre").build();
        MonsterSpawnZone zone = MonsterSpawnZone.builder()
            .centerPoint(underPoint)
            .surfaceEntrance(surfacePoint)
            .dungeonName("Ogre Enclave")
            .entranceVerified(false)   // entrance is only an estimate
            .spawnCount(6)
            .build();

        plugin.focusMonsterZoneOnMap(monster, zone);
        when(mapWidget.isHidden()).thenReturn(false);
        plugin.onGameTick(new GameTick());

        // Still pans to the area, but the beacon label marks it approximate ("~ " prefix ->
        // WorldMapFocusBeaconOverlay draws a soft area pulse, not a pinpoint).
        verify(worldMap, times(1)).setWorldMapPositionTarget(surfacePoint);
        verify(beaconOverlay, times(1)).triggerBeacon(eq(surfacePoint),
            eq("~ Ogre Enclave area (Ogre) - exact entrance unconfirmed"));
    }

    @Test
    public void testFocusPointOnMapRunsOnClientThread()
    {
        ArrayDeque<Runnable> pendingTasks = new ArrayDeque<>();
        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            if (r != null)
            {
                pendingTasks.add(r);
            }
            return null;
        }).when(clientThread).invokeLater(any(Runnable.class));

        Widget mapWidget = mock(Widget.class);
        when(mapWidget.isHidden()).thenReturn(true);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(mapWidget);

        WorldPoint target = new WorldPoint(3200, 3200, 0);
        plugin.focusPointOnMap(target, "Test Target");

        // Outer focusPointOnMap runnable is queued to clientThread
        Assert.assertEquals(1, pendingTasks.size());
        pendingTasks.poll().run(); // runs focusPointOnMap lambda, calling requestPendingFocus

        Assert.assertEquals(target, plugin.getPendingFocusTarget());
        Assert.assertEquals("Test Target", plugin.getPendingFocusLabel());

        // triggerOpenWorldMap was queued via clientThread
        Assert.assertEquals(1, pendingTasks.size());
        pendingTasks.poll().run();
        verify(client, times(1)).openInterface(anyInt(), eq(InterfaceID.WORLDMAP), eq(WidgetModalMode.NON_MODAL));
    }

    /**
     * FIX 2 (T7b): coins (995) and platinum tokens (13204) have no GE listing and no HA value, so
     * without a special-case a coin drop is valued at 0 gp and never reaches the GP totals /
     * GP-hr / value sort. Match the stock Loot Tracker: coins are 1 gp each, platinum 1,000.
     */
    @Test
    public void testBuildLootItemsValuesStackableCurrency()
    {
        ItemManager im = mock(ItemManager.class);
        when(im.canonicalize(anyInt())).thenAnswer(inv -> inv.getArgument(0));
        when(im.getItemPrice(anyInt())).thenReturn(0); // neither coins nor plat have a GE price
        ItemComposition comp = mock(ItemComposition.class);
        when(comp.getHaPrice()).thenReturn(0);
        when(comp.getName()).thenReturn("Coins");
        when(im.getItemComposition(anyInt())).thenReturn(comp);
        setField(plugin, "itemManager", im);

        java.util.List<LootItem> items = plugin.buildLootItems(Arrays.asList(
            new ItemStack(ItemID.COINS_995, 15_000),
            new ItemStack(ItemID.PLATINUM_TOKEN, 3),
            new ItemStack(526, 1))); // a plain item stays at its (here zero) price

        LootItem coins = items.get(0);
        Assert.assertEquals("coins are 1 gp each", 1L, coins.getGePriceEach());
        // bestValueTotal() feeds LootRecord.totalBestValue() -> MonsterLootSummary.totalBestValue,
        // i.e. the GP totals / GP-hr / value sort. A coin stack now contributes its quantity.
        Assert.assertEquals("a coin stack contributes its quantity", 15_000L, coins.bestValueTotal());

        LootItem plat = items.get(1);
        Assert.assertEquals("platinum tokens are 1,000 gp each", 1000L, plat.getGePriceEach());
        Assert.assertEquals(3_000L, plat.bestValueTotal());

        Assert.assertEquals("a non-currency item is untouched", 0L, items.get(2).getGePriceEach());
    }

    /**
     * The legacy marker-style / highlightNativeIcons reset now runs once, gated by a stored schema
     * version, instead of on every launch - so a later deliberate re-enable is left alone.
     */
    @Test
    public void testConfigMigrationRunsOnceThenRespectsUserChoice()
    {
        when(config.highlightNativeIcons()).thenReturn(true);

        // First launch: nothing stored -> the legacy values are cleared and the version stamped.
        when(configManager.getConfiguration("osrscopilot", "migrationVersion")).thenReturn(null);
        plugin.runConfigMigrations();
        verify(configManager).unsetConfiguration("osrscopilot", "iconStyle");
        verify(configManager).setConfiguration("osrscopilot", "highlightNativeIcons", false);
        // v3: the group damage meter default flipped on - clear any persisted value once.
        verify(configManager).unsetConfiguration("osrscopilot", "combatPartyEnabled");
        verify(configManager).setConfiguration("osrscopilot", "migrationVersion", 3);

        // Second launch: the stored version is current now, so nothing runs again even
        // though the user has deliberately switched options back on.
        org.mockito.Mockito.clearInvocations(configManager);
        when(configManager.getConfiguration("osrscopilot", "migrationVersion")).thenReturn("3");
        plugin.runConfigMigrations();
        verify(configManager, never()).setConfiguration(anyString(), anyString(), any(Object.class));
        verify(configManager, never()).unsetConfiguration(anyString(), anyString());
    }

    /**
     * v2 (package/identity rename): every key persisted under the legacy config group is copied
     * verbatim into the new "osrscopilot" group, the legacy group is left intact as a backup, and
     * the copied legacy migrationVersion gates the v1 reset so it is not re-applied on top of
     * settings the user already had.
     */
    @Test
    public void testRenameMigrationCopiesLegacyGroupThenGatesV1()
    {
        final String legacy = OsrsCopilotPlugin.LEGACY_CONFIG_GROUP;
        when(config.highlightNativeIcons()).thenReturn(true);

        // New group unstamped on the first read; after the v2 copy it carries the legacy v1 value.
        when(configManager.getConfiguration("osrscopilot", "migrationVersion")).thenReturn(null, "1");
        when(configManager.getConfigurationKeys(legacy + ".")).thenReturn(Arrays.asList(
            legacy + ".combatOverlayWidth",
            legacy + ".slayerMonster",
            legacy + ".migrationVersion"));
        when(configManager.getConfiguration(legacy, "combatOverlayWidth")).thenReturn("230");
        when(configManager.getConfiguration(legacy, "slayerMonster")).thenReturn("Aberrant spectres");
        when(configManager.getConfiguration(legacy, "migrationVersion")).thenReturn("1");

        plugin.runConfigMigrations();

        // every legacy key carried across, unchanged
        verify(configManager).setConfiguration("osrscopilot", "combatOverlayWidth", "230");
        verify(configManager).setConfiguration("osrscopilot", "slayerMonster", "Aberrant spectres");
        verify(configManager).setConfiguration("osrscopilot", "migrationVersion", "1");
        // the copied v1 version gates the legacy marker-style reset - it must NOT run again
        verify(configManager, never()).unsetConfiguration("osrscopilot", "iconStyle");
        verify(configManager, never()).setConfiguration("osrscopilot", "highlightNativeIcons", false);
        // legacy group preserved as a fallback - never cleared
        verify(configManager, never()).unsetConfiguration(eq(legacy), anyString());
        // v3 still runs on top of the copied v1 stamp (group-meter default flip)
        verify(configManager).unsetConfiguration("osrscopilot", "combatPartyEnabled");
        // schema version stamped forward to the current version
        verify(configManager).setConfiguration("osrscopilot", "migrationVersion", 3);
    }

    @Test
    public void testMigrateLegacyDataDirMovesWhenOnlyLegacyExists() throws Exception
    {
        java.nio.file.Path root = Files.createTempDirectory("osrscopilot-datadir");
        File legacy = new File(root.toFile(), OsrsCopilotPlugin.LEGACY_CONFIG_GROUP);
        File fresh = new File(root.toFile(), "osrscopilot");
        File loot = new File(legacy, "loot");
        Assert.assertTrue(loot.mkdirs());
        Files.write(new File(loot, "123.json.gz").toPath(), new byte[]{1, 2, 3});

        OsrsCopilotPlugin.migrateLegacyDataDir(legacy, fresh);

        Assert.assertFalse("legacy dir is gone after the move", legacy.exists());
        Assert.assertTrue("payload moved under the new dir", new File(fresh, "loot/123.json.gz").isFile());
    }

    @Test
    public void testMigrateLegacyDataDirNoOpsWhenBothExist() throws Exception
    {
        java.nio.file.Path root = Files.createTempDirectory("osrscopilot-datadir");
        File legacy = new File(root.toFile(), OsrsCopilotPlugin.LEGACY_CONFIG_GROUP);
        File fresh = new File(root.toFile(), "osrscopilot");
        Assert.assertTrue(new File(legacy, "loot").mkdirs());
        Assert.assertTrue(fresh.mkdirs());

        OsrsCopilotPlugin.migrateLegacyDataDir(legacy, fresh);

        Assert.assertTrue("legacy dir left untouched as a backup", new File(legacy, "loot").isDirectory());
    }

    @Test
    public void testMigrateLegacyDataDirNoOpsWhenNothingToMove() throws Exception
    {
        java.nio.file.Path root = Files.createTempDirectory("osrscopilot-datadir");
        File legacy = new File(root.toFile(), OsrsCopilotPlugin.LEGACY_CONFIG_GROUP);
        File fresh = new File(root.toFile(), "osrscopilot");

        OsrsCopilotPlugin.migrateLegacyDataDir(legacy, fresh);

        Assert.assertFalse(legacy.exists());
        Assert.assertFalse(fresh.exists());
    }

    private static void flushEdt()
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            return;
        }
        try
        {
            SwingUtilities.invokeAndWait(() -> {});
        }
        catch (Exception ignored)
        {
        }
    }
}
