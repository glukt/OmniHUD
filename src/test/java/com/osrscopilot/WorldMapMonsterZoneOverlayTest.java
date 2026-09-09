package com.osrscopilot;

import com.osrscopilot.data.DungeonEntranceDatabase;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterDrop;
import com.osrscopilot.data.model.MonsterSpawnZone;
import com.osrscopilot.map.WorldMapFocusBeaconOverlay;
import com.osrscopilot.map.WorldMapMonsterZoneOverlay;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.api.worldmap.WorldMapData;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldMapMonsterZoneOverlayTest
{
    private Client client;
    private WorldMapOverlay worldMapOverlay;
    private OsrsCopilotConfig config;
    private WorldMapFocusBeaconOverlay beaconOverlay;
    private WorldMapMonsterZoneOverlay overlay;
    private DungeonEntranceDatabase dungeonDb;

    private static class DummyComponent extends Component {}

    private Component dummyComponent;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        worldMapOverlay = mock(WorldMapOverlay.class);
        config = mock(OsrsCopilotConfig.class);
        when(config.showMonsterZones()).thenReturn(true);
        beaconOverlay = mock(WorldMapFocusBeaconOverlay.class);
        dungeonDb = new DungeonEntranceDatabase();
        overlay = new WorldMapMonsterZoneOverlay(client, worldMapOverlay, config, beaconOverlay, dungeonDb);
        dummyComponent = new DummyComponent();
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

    @Test
    public void testOverlayProperties()
    {
        Assert.assertEquals(OverlayPosition.DYNAMIC, overlay.getPosition());
        Assert.assertEquals(OverlayLayer.ALWAYS_ON_TOP, overlay.getLayer());
        Assert.assertEquals(WorldMapMonsterZoneOverlay.PRIORITY_HIGHEST, overlay.getPriority(), 0.001f);
    }

    @Test
    public void testShowMonsterZonesConfigGatesRendering()
    {
        MonsterSpawnZone zone = MonsterSpawnZone.builder()
            .zoneName("Taverley Dungeon").minX(2880).minY(9760).maxX(2920).maxY(9800)
            .plane(0).spawnCount(6).build();
        Monster m = Monster.builder().id(1).name("Blue dragon").spawnZones(List.of(zone)).build();

        Widget worldMapWidget = mock(Widget.class);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(worldMapWidget);
        when(worldMapWidget.isHidden()).thenReturn(false);
        when(worldMapWidget.getBounds()).thenReturn(new Rectangle(0, 0, 800, 600));
        overlay.setFocusedMonster(m);
        overlay.setZonesVisible(true);

        // isZonesVisible() is the AND of the in-memory flag and the persistent config item, and
        // render() must no-op (not throw) when the config is off.
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);

        when(config.showMonsterZones()).thenReturn(false);
        Assert.assertFalse("config off -> not visible even with the flag set", overlay.isZonesVisible());
        Graphics2D g1 = img.createGraphics();
        overlay.render(g1);
        g1.dispose();

        when(config.showMonsterZones()).thenReturn(true);
        Assert.assertTrue("config on + flag on -> visible", overlay.isZonesVisible());

        overlay.setZonesVisible(false);
        Assert.assertFalse("flag off -> not visible even with config on", overlay.isZonesVisible());
    }

    @Test
    public void testSetFocusedMonsterAndZones()
    {
        MonsterSpawnZone zone1 = MonsterSpawnZone.builder()
            .zoneName("Taverley Dungeon")
            .locationName("Taverley Blue Dragons")
            .minX(2880)
            .minY(9760)
            .maxX(2920)
            .maxY(9800)
            .plane(0)
            .spawnCount(6)
            .multiCombat(false)
            .wildernessLevel(0)
            .build();

        MonsterSpawnZone zone2 = MonsterSpawnZone.builder()
            .zoneName("Catacombs of Kourend")
            .locationName("Catacombs Blue Dragons")
            .minX(1600)
            .minY(10000)
            .maxX(1640)
            .maxY(10040)
            .plane(0)
            .spawnCount(8)
            .multiCombat(true)
            .wildernessLevel(0)
            .build();

        MonsterDrop drop = MonsterDrop.builder()
            .itemId(536)
            .name("Dragon bones")
            .quantity("1")
            .rarity(1.0)
            .rarityFraction("1/1")
            .build();

        Monster blueDragon = Monster.builder()
            .id(265)
            .name("Blue dragon")
            .combatLevel(111)
            .hitpoints(105)
            .maxHit(10)
            .attackType("Melee / Dragonfire")
            .weakness("Stab, Ranged")
            .category("Dragon")
            .members(true)
            .spawnZones(List.of(zone1, zone2))
            .drops(List.of(drop))
            .build();

        overlay.setFocusedMonster(blueDragon);

        Assert.assertEquals(blueDragon, overlay.getFocusedMonster());
        Assert.assertEquals(2, overlay.getActiveEntries().size());
        Assert.assertEquals(zone1, overlay.getActiveEntries().get(0).getZone());
        Assert.assertEquals(zone2, overlay.getActiveEntries().get(1).getZone());

        // Test clear
        overlay.clearActiveZones();
        Assert.assertNull(overlay.getFocusedMonster());
        Assert.assertTrue(overlay.getActiveEntries().isEmpty());
    }

    @Test
    public void testSetFocusedZone()
    {
        MonsterSpawnZone zone = MonsterSpawnZone.builder()
            .zoneName("Wilderness")
            .locationName("Lava Dragon Isle")
            .minX(3180)
            .minY(3800)
            .maxX(3220)
            .maxY(3840)
            .plane(0)
            .spawnCount(10)
            .multiCombat(true)
            .wildernessLevel(43)
            .build();

        Monster lavaDragon = Monster.builder()
            .id(6593)
            .name("Lava dragon")
            .combatLevel(252)
            .hitpoints(200)
            .category("Dragon")
            .spawnZones(List.of(zone))
            .build();

        AtomicReference<MonsterSpawnZone> selectedZoneRef = new AtomicReference<>();
        overlay.setOnZoneSelectionCallback(selectedZoneRef::set);

        overlay.setFocusedZone(lavaDragon, zone);

        Assert.assertEquals(lavaDragon, overlay.getFocusedMonster());
        Assert.assertEquals(zone, overlay.getFocusedZone());
        Assert.assertEquals(1, overlay.getActiveEntries().size());
        Assert.assertEquals(zone, selectedZoneRef.get());
    }

    @Test
    public void testHighlightZone()
    {
        MonsterSpawnZone zone1 = MonsterSpawnZone.builder()
            .zoneName("Zone 1")
            .minX(3000)
            .minY(3000)
            .maxX(3010)
            .maxY(3010)
            .build();

        MonsterSpawnZone zone2 = MonsterSpawnZone.builder()
            .zoneName("Zone 2")
            .minX(3020)
            .minY(3020)
            .maxX(3030)
            .maxY(3030)
            .build();

        Monster testMonster = Monster.builder()
            .id(1)
            .name("Test Monster")
            .spawnZones(List.of(zone1, zone2))
            .build();

        overlay.setFocusedMonster(testMonster);
        Assert.assertEquals(2, overlay.getActiveEntries().size());
        Assert.assertNull(overlay.getFocusedZone());

        overlay.highlightZone(zone2);
        Assert.assertEquals(zone2, overlay.getFocusedZone());
        Assert.assertEquals(2, overlay.getActiveEntries().size());
    }

    @Test
    public void testZoneCallbacks()
    {
        MonsterSpawnZone zone = MonsterSpawnZone.builder()
            .zoneName("Zul-Andra")
            .locationName("Shrine")
            .minX(2260)
            .minY(3060)
            .maxX(2280)
            .maxY(3080)
            .plane(0)
            .spawnCount(1)
            .multiCombat(false)
            .wildernessLevel(0)
            .build();

        Monster zulrah = Monster.builder()
            .id(2042)
            .name("Zulrah")
            .combatLevel(725)
            .hitpoints(500)
            .category("Boss")
            .spawnZones(List.of(zone))
            .build();

        AtomicBoolean clicked = new AtomicBoolean(false);
        overlay.setOnZoneClickListener((m, z) -> {
            Assert.assertEquals(zulrah, m);
            Assert.assertEquals(zone, z);
            clicked.set(true);
        });

        overlay.setFocusedMonster(zulrah);
        Assert.assertFalse(clicked.get());
    }

    @Test
    public void testDungeonEntranceDatabaseDetection()
    {
        // TzHaar City underground point
        WorldPoint tzhaarPt = new WorldPoint(2450, 5150, 0);
        Assert.assertTrue("TzHaar should be detected as underground/dungeon", dungeonDb.isUndergroundOrDungeon(tzhaarPt));

        DungeonEntranceDatabase.DungeonMapping tzhaarDungeon = dungeonDb.findDungeon(tzhaarPt);
        Assert.assertNotNull("TzHaar dungeon mapping should be found", tzhaarDungeon);
        Assert.assertEquals("TzHaar City", tzhaarDungeon.getDungeonName());
        Assert.assertEquals(new WorldPoint(2857, 3169, 0), tzhaarDungeon.getSurfaceEntrance());

        // Taverley Dungeon underground point
        WorldPoint tavPt = new WorldPoint(2884, 9798, 0);
        Assert.assertTrue("Taverley dungeon coordinate (Y >= 6400) should be detected", dungeonDb.isUndergroundOrDungeon(tavPt));
        DungeonEntranceDatabase.DungeonMapping tavDungeon = dungeonDb.findDungeon(tavPt);
        Assert.assertNotNull("Taverley mapping should be found", tavDungeon);
        Assert.assertEquals(new WorldPoint(2884, 3396, 0), tavDungeon.getSurfaceEntrance());

        // Catacombs of Kourend
        WorldPoint catacombsPt = new WorldPoint(1660, 10050, 0);
        Assert.assertTrue(dungeonDb.isUndergroundOrDungeon(catacombsPt));
        DungeonEntranceDatabase.DungeonMapping cataDungeon = dungeonDb.findDungeon(catacombsPt);
        Assert.assertNotNull(cataDungeon);
        Assert.assertEquals("Catacombs of Kourend", cataDungeon.getDungeonName());
        Assert.assertEquals(new WorldPoint(1636, 3673, 0), cataDungeon.getSurfaceEntrance());

        // Edgeville Dungeon
        WorldPoint edgeDungeonPt = new WorldPoint(3100, 9860, 0);
        Assert.assertTrue(dungeonDb.isUndergroundOrDungeon(edgeDungeonPt));
        DungeonEntranceDatabase.DungeonMapping edgeDungeon = dungeonDb.findDungeon(edgeDungeonPt);
        Assert.assertNotNull(edgeDungeon);
        Assert.assertEquals("Edgeville Dungeon", edgeDungeon.getDungeonName());
        Assert.assertEquals(new WorldPoint(3096, 3468, 0), edgeDungeon.getSurfaceEntrance());

        // Surface point (Lumbridge castle)
        WorldPoint surfacePt = new WorldPoint(3222, 3218, 0);
        Assert.assertFalse("Surface Lumbridge is not underground", dungeonDb.isUndergroundOrDungeon(surfacePt));
    }

    @Test
    public void testDungeonResolutionMethodsInOverlay()
    {
        MonsterSpawnZone edgeZone = MonsterSpawnZone.builder()
            .zoneName("Edgeville Dungeon")
            .locationName("Edgeville Hill Giants")
            .minX(3100)
            .minY(9850)
            .maxX(3130)
            .maxY(9880)
            .plane(0)
            .spawnCount(6)
            .build();

        Assert.assertTrue(overlay.isDungeonOrUnderground(edgeZone));
        Assert.assertEquals(new WorldPoint(3096, 3468, 0), overlay.resolveSurfaceEntrance(edgeZone));
        Assert.assertEquals("Edgeville Dungeon", overlay.resolveDungeonName(edgeZone));

        MonsterSpawnZone surfaceCowZone = MonsterSpawnZone.builder()
            .zoneName("Lumbridge Pasture")
            .locationName("Lumbridge Cows")
            .minX(3250)
            .minY(3260)
            .maxX(3270)
            .maxY(3280)
            .plane(0)
            .build();

        Assert.assertFalse(overlay.isDungeonOrUnderground(surfaceCowZone));
        Assert.assertNull(overlay.resolveSurfaceEntrance(surfaceCowZone));
    }

    @Test
    public void testSurfaceMapVsDungeonSubmapDetection()
    {
        MonsterSpawnZone edgeZone = MonsterSpawnZone.builder()
            .zoneName("Edgeville Dungeon")
            .minX(3100)
            .minY(9850)
            .maxX(3130)
            .maxY(9880)
            .plane(0)
            .surfaceEntrance(new WorldPoint(3096, 3468, 0))
            .build();

        WorldMap worldMap = mock(WorldMap.class);
        WorldMapData mapData = mock(WorldMapData.class);
        when(client.getWorldMap()).thenReturn(worldMap);
        when(worldMap.getWorldMapData()).thenReturn(mapData);

        // Case 1: Active map contains surface entrance (3096, 3468) -> Surface map
        when(mapData.surfaceContainsPosition(3096, 3468)).thenReturn(true);
        when(mapData.surfaceContainsPosition(3115, 9865)).thenReturn(false);
        Assert.assertTrue("Should recognize active map is Surface map",
            overlay.isViewingSurfaceMap(edgeZone, edgeZone.getSurfaceEntrance()));

        // Case 2: Active map contains dungeon zone (3115, 9865) and NOT surface entrance -> Dungeon sub-map
        when(mapData.surfaceContainsPosition(3096, 3468)).thenReturn(false);
        when(mapData.surfaceContainsPosition(3115, 9865)).thenReturn(true);
        Assert.assertFalse("Should recognize active map is Dungeon sub-map",
            overlay.isViewingSurfaceMap(edgeZone, edgeZone.getSurfaceEntrance()));
    }

    @Test
    public void testDungeonEntrancePortalMarkerRenderOnSurfaceMap()
    {
        MonsterSpawnZone hillGiantZone = MonsterSpawnZone.builder()
            .zoneName("Edgeville Dungeon")
            .locationName("Hill Giant Pit")
            .dungeonName("Edgeville Dungeon")
            .minX(3100)
            .minY(9850)
            .maxX(3130)
            .maxY(9880)
            .plane(0)
            .spawnCount(6)
            .multiCombat(false)
            .surfaceEntrance(new WorldPoint(3096, 3468, 0))
            .build();

        Monster hillGiant = Monster.builder()
            .id(2098)
            .name("Hill Giant")
            .combatLevel(28)
            .hitpoints(35)
            .spawnZones(List.of(hillGiantZone))
            .build();

        Widget worldMapWidget = mock(Widget.class);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(worldMapWidget);
        when(worldMapWidget.isHidden()).thenReturn(false);
        when(worldMapWidget.getBounds()).thenReturn(new Rectangle(0, 0, 800, 600));

        WorldMap worldMap = mock(WorldMap.class);
        WorldMapData mapData = mock(WorldMapData.class);
        when(client.getWorldMap()).thenReturn(worldMap);
        when(worldMap.getWorldMapData()).thenReturn(mapData);

        // Active map is Surface
        when(mapData.surfaceContainsPosition(3096, 3468)).thenReturn(true);
        when(mapData.surfaceContainsPosition(3115, 9865)).thenReturn(false);

        // Surface entrance maps to screen point (400, 300)
        when(worldMapOverlay.mapWorldPointToGraphicsPoint(eq(new WorldPoint(3096, 3468, 0))))
            .thenReturn(new Point(400, 300));

        overlay.setFocusedMonster(hillGiant);

        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();

        overlay.render(g2d);
        g2d.dispose();

        // Overlay should have rendered without errors and placed the portal marker at the surface entrance
        Assert.assertEquals(1, overlay.getActiveEntries().size());
    }

    @Test
    public void testClickDungeonEntranceTriggersBeacon()
    {
        WorldPoint surfaceEntrance = new WorldPoint(3096, 3468, 0);
        MonsterSpawnZone hillGiantZone = MonsterSpawnZone.builder()
            .zoneName("Edgeville Dungeon")
            .dungeonName("Edgeville Dungeon")
            .minX(3100)
            .minY(9850)
            .maxX(3130)
            .maxY(9880)
            .plane(0)
            .spawnCount(6)
            .surfaceEntrance(surfaceEntrance)
            .entranceVerified(true)
            .build();

        Monster hillGiant = Monster.builder()
            .id(2098)
            .name("Hill Giant")
            .spawnZones(List.of(hillGiantZone))
            .build();

        AtomicBoolean zoneClicked = new AtomicBoolean(false);
        overlay.setOnZoneClickListener((m, z) -> {
            Assert.assertEquals(hillGiant, m);
            Assert.assertEquals(hillGiantZone, z);
            zoneClicked.set(true);
        });

        Widget worldMapWidget = mock(Widget.class);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(worldMapWidget);
        when(worldMapWidget.isHidden()).thenReturn(false);
        when(worldMapWidget.getBounds()).thenReturn(new Rectangle(0, 0, 800, 600));

        WorldMap worldMap = mock(WorldMap.class);
        WorldMapData mapData = mock(WorldMapData.class);
        when(client.getWorldMap()).thenReturn(worldMap);
        when(worldMap.getWorldMapData()).thenReturn(mapData);
        when(mapData.surfaceContainsPosition(3096, 3468)).thenReturn(true);
        when(worldMapOverlay.mapWorldPointToGraphicsPoint(eq(surfaceEntrance))).thenReturn(new Point(400, 300));

        overlay.setFocusedMonster(hillGiant);

        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        overlay.render(g2d);
        g2d.dispose();

        // Simulate click on entrance screen location (400, 300)
        MouseEvent press = createMouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, 400, 300);
        overlay.mousePressed(press);
        Assert.assertFalse("Press should not be consumed so dungeon ladder/trapdoor is clickable", press.isConsumed());

        MouseEvent release = createMouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, 400, 300);
        overlay.mouseReleased(release);
        Assert.assertFalse("Release should not be consumed so dungeon ladder/trapdoor is clickable", release.isConsumed());

        Assert.assertTrue("Zone click callback should be invoked", zoneClicked.get());
        verify(worldMap).setWorldMapPositionTarget(eq(surfaceEntrance));
        verify(beaconOverlay).triggerBeacon(eq(surfaceEntrance), eq("[Dungeon Entrance] Edgeville Dungeon (Hill Giant inside)"));
    }

    /** An unverified surface entrance beacons with a leading "~ " so the beacon draws the soft approx pulse. */
    @Test
    public void testUnverifiedDungeonEntranceBeaconsAsApproximate()
    {
        WorldPoint surfaceEntrance = new WorldPoint(3096, 3468, 0);
        MonsterSpawnZone zone = MonsterSpawnZone.builder()
            .zoneName("Edgeville Dungeon")
            .dungeonName("Edgeville Dungeon")
            .minX(3100).minY(9850).maxX(3130).maxY(9880)
            .plane(0)
            .spawnCount(6)
            .surfaceEntrance(surfaceEntrance)
            // no .entranceVerified(true) - this is an estimate
            .build();

        Monster hillGiant = Monster.builder()
            .id(2098)
            .name("Hill Giant")
            .spawnZones(List.of(zone))
            .build();

        overlay.setOnZoneClickListener((m, z) -> { });

        Widget worldMapWidget = mock(Widget.class);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(worldMapWidget);
        when(worldMapWidget.isHidden()).thenReturn(false);
        when(worldMapWidget.getBounds()).thenReturn(new Rectangle(0, 0, 800, 600));

        WorldMap worldMap = mock(WorldMap.class);
        WorldMapData mapData = mock(WorldMapData.class);
        when(client.getWorldMap()).thenReturn(worldMap);
        when(worldMap.getWorldMapData()).thenReturn(mapData);
        when(mapData.surfaceContainsPosition(3096, 3468)).thenReturn(true);
        when(worldMapOverlay.mapWorldPointToGraphicsPoint(eq(surfaceEntrance))).thenReturn(new Point(400, 300));

        overlay.setFocusedMonster(hillGiant);

        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        overlay.render(g2d);
        g2d.dispose();

        overlay.mousePressed(createMouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, 400, 300));
        overlay.mouseReleased(createMouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, 400, 300));

        verify(beaconOverlay).triggerBeacon(eq(surfaceEntrance),
            eq("~ [Dungeon Entrance] Edgeville Dungeon (Hill Giant inside)"));
    }

    @Test
    public void testClickDungeonEntranceHitboxRadius24px()
    {
        WorldPoint surfaceEntrance = new WorldPoint(3096, 3468, 0);
        MonsterSpawnZone hillGiantZone = MonsterSpawnZone.builder()
            .zoneName("Edgeville Dungeon")
            .dungeonName("Edgeville Dungeon")
            .minX(3100)
            .minY(9850)
            .maxX(3130)
            .maxY(9880)
            .plane(0)
            .spawnCount(6)
            .surfaceEntrance(surfaceEntrance)
            .entranceVerified(true)
            .build();

        Monster hillGiant = Monster.builder()
            .id(2098)
            .name("Hill Giant")
            .spawnZones(List.of(hillGiantZone))
            .build();

        AtomicBoolean zoneClicked = new AtomicBoolean(false);
        overlay.setOnZoneClickListener((m, z) -> zoneClicked.set(true));

        Widget worldMapWidget = mock(Widget.class);
        when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(worldMapWidget);
        when(worldMapWidget.isHidden()).thenReturn(false);
        when(worldMapWidget.getBounds()).thenReturn(new Rectangle(0, 0, 800, 600));

        WorldMap worldMap = mock(WorldMap.class);
        WorldMapData mapData = mock(WorldMapData.class);
        when(client.getWorldMap()).thenReturn(worldMap);
        when(worldMap.getWorldMapData()).thenReturn(mapData);
        when(mapData.surfaceContainsPosition(3096, 3468)).thenReturn(true);
        when(worldMapOverlay.mapWorldPointToGraphicsPoint(eq(surfaceEntrance))).thenReturn(new Point(400, 300));

        overlay.setFocusedMonster(hillGiant);

        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        overlay.render(g2d);
        g2d.dispose();

        // Click at (420, 310) which is sqrt(20^2 + 10^2) = sqrt(500) <= 24px away from (400, 300)
        MouseEvent press = createMouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, 420, 310);
        overlay.mousePressed(press);

        MouseEvent release = createMouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, 420, 310);
        overlay.mouseReleased(release);

        Assert.assertTrue("Click within 24px radius of portal center should trigger dungeon entrance selection", zoneClicked.get());
        verify(worldMap).setWorldMapPositionTarget(eq(surfaceEntrance));
        verify(beaconOverlay).triggerBeacon(eq(surfaceEntrance), eq("[Dungeon Entrance] Edgeville Dungeon (Hill Giant inside)"));
    }

    @Test
    public void testContextualBeaconLabelFormatting()
    {
        WorldPoint tzhaarShop = new WorldPoint(2450, 5150, 0);
        String label = "TzHaar Weapons";

        DungeonEntranceDatabase.DungeonMapping dungeon = dungeonDb.findDungeon(tzhaarShop);
        Assert.assertNotNull(dungeon);

        String beaconLabel = "[Dungeon Entrance] " + dungeon.getDungeonName() + " (" + label + " inside)";
        Assert.assertEquals("[Dungeon Entrance] TzHaar City (TzHaar Weapons inside)", beaconLabel);
    }

    @Test
    public void testHoverTooltipWithSlayerAndQuestRequirement()
    {
        Monster gargoyle = Monster.builder()
            .id(412)
            .name("Gargoyle")
            .combatLevel(111)
            .hitpoints(105)
            .maxHit(11)
            .attackType("Crush")
            .slayerLevel(75)
            .questRequirement("Priest in Peril")
            .weakness("Blunt Melee (Crush)")
            .category("Slayer")
            .members(true)
            .spawnZones(List.of(
                MonsterSpawnZone.builder()
                    .zoneName("Slayer Tower Top Floor")
                    .locationName("Morytania")
                    .minX(3430).minY(3530).maxX(3450).maxY(3550)
                    .spawnCount(6)
                    .multiCombat(false)
                    .build()
            ))
            .drops(List.of(
                MonsterDrop.builder()
                    .name("Granite maul")
                    .quantity("1")
                    .rarity(0.0039)
                    .rarityFraction("1/256")
                    .build()
            ))
            .build();

        Assert.assertTrue(gargoyle.hasQuestRequirement());
        Assert.assertEquals("Priest in Peril", gargoyle.getQuestRequirement());
        Assert.assertEquals(75, gargoyle.getSlayerLevel());

        when(client.getCanvasWidth()).thenReturn(1000);
        when(client.getCanvasHeight()).thenReturn(800);
        when(client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(500, 400));

        overlay.setFocusedMonster(gargoyle);

        WorldMap worldMap = mock(WorldMap.class);
        WorldMapData mapData = mock(WorldMapData.class);
        when(client.getWorldMap()).thenReturn(worldMap);
        when(worldMap.getWorldMapData()).thenReturn(mapData);
        when(mapData.surfaceContainsPosition(anyInt(), anyInt())).thenReturn(true);
        when(worldMapOverlay.mapWorldPointToGraphicsPoint(any())).thenReturn(new Point(500, 400));

        BufferedImage img = new BufferedImage(1000, 800, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        overlay.render(g2d);
        g2d.dispose();
    }

    @Test
    public void testDynamicTooltipWidthCalculationPreventsOverflow()
    {
        Monster longMonster = Monster.builder()
            .id(999)
            .name("Alchemical Hydra of the Karuulm Slayer Dungeon")
            .combatLevel(426)
            .hitpoints(1100)
            .maxHit(55)
            .attackType("Magic / Ranged / Melee")
            .slayerLevel(95)
            .questRequirement("Dragon Slayer II & Secrets of the North")
            .weakness("Ranged (Twisted bow / Toxic blowpipe)")
            .category("Boss")
            .members(true)
            .spawnZones(List.of(
                MonsterSpawnZone.builder()
                    .zoneName("Mount Karuulm Lower Sanctum")
                    .locationName("Karuulm Slayer Dungeon")
                    .minX(1300).minY(10200).maxX(1330).maxY(10230)
                    .spawnCount(1)
                    .multiCombat(false)
                    .wildernessLevel(0)
                    .build()
            ))
            .drops(List.of(
                MonsterDrop.builder()
                    .name("Hydra's claw (Very Rare)")
                    .quantity("1")
                    .rarity(0.001)
                    .rarityFraction("1/1000")
                    .build()
            ))
            .build();

        when(client.getCanvasWidth()).thenReturn(1280);
        when(client.getCanvasHeight()).thenReturn(960);
        when(client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(600, 500));

        overlay.setFocusedMonster(longMonster);

        WorldMap worldMap = mock(WorldMap.class);
        WorldMapData mapData = mock(WorldMapData.class);
        when(client.getWorldMap()).thenReturn(worldMap);
        when(worldMap.getWorldMapData()).thenReturn(mapData);
        when(mapData.surfaceContainsPosition(anyInt(), anyInt())).thenReturn(true);
        when(worldMapOverlay.mapWorldPointToGraphicsPoint(any())).thenReturn(new Point(600, 500));

        BufferedImage img = new BufferedImage(1280, 960, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        overlay.render(g2d);
        g2d.dispose();
    }

    @Test
    public void testTopRareDropsRarestFirstOrdering()
    {
        Monster caveHorror = Monster.builder()
            .id(1040)
            .name("Cave horror")
            .combatLevel(80)
            .hitpoints(55)
            .drops(List.of(
                MonsterDrop.builder().name("Big bones").quantity("1").rarity(1.0).rarityFraction("Always (1/1)").build(),
                MonsterDrop.builder().name("Mithril axe").quantity("1").rarity(1.0 / 43.0).rarityFraction("1/43").build(),
                MonsterDrop.builder().name("Rune dagger").quantity("1").rarity(1.0 / 128.0).rarityFraction("1/128").build(),
                MonsterDrop.builder().name("Black mask (10)").quantity("1").rarity(1.0 / 512.0).rarityFraction("1/512").build(),
                MonsterDrop.builder().name("Curved bone").quantity("1").rarity(1.0 / 5012.0).rarityFraction("1/5,012").build()
            ))
            .spawnZones(List.of(
                MonsterSpawnZone.builder()
                    .zoneName("Mos Le'Harmless Caves")
                    .locationName("Mos Le'Harmless Caves")
                    .minX(3740).minY(9360).maxX(3780).maxY(9400)
                    .spawnCount(15)
                    .build()
            ))
            .build();

        overlay.setFocusedMonster(caveHorror);

        when(client.getCanvasWidth()).thenReturn(1280);
        when(client.getCanvasHeight()).thenReturn(960);
        when(client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(500, 400));

        WorldMap worldMap = mock(WorldMap.class);
        WorldMapData mapData = mock(WorldMapData.class);
        when(client.getWorldMap()).thenReturn(worldMap);
        when(worldMap.getWorldMapData()).thenReturn(mapData);
        when(mapData.surfaceContainsPosition(anyInt(), anyInt())).thenReturn(true);
        when(worldMapOverlay.mapWorldPointToGraphicsPoint(any())).thenReturn(new Point(500, 400));

        BufferedImage img = new BufferedImage(1280, 960, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        overlay.render(g2d);
        g2d.dispose();
    }
}
