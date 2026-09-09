package com.osrscopilot.map;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.data.DungeonEntranceDatabase;
import com.osrscopilot.data.RarityFormat;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterDrop;
import com.osrscopilot.data.model.MonsterSpawnZone;
import net.runelite.api.GameState;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

/**
 * WorldMapMonsterZoneOverlay renders active and focused monster spawn zones
 * on the RuneLite World Map canvas.
 *
 * Features:
 * - Glowing rounded bounding polygons with multi-layer luminous bloom when inside dungeon sub-maps or on surface.
 * - Authentic Dungeon Entrance Monster Portal marker rendered at surface entrances when viewing the surface map
 *   for underground/dungeon zones (e.g. at Edgeville Trapdoor: '[Dungeon Entrance] Edgeville Dungeon (Hill Giant Spawn)').
 * - Pulsing, semi-transparent heat gradient centers indicating monster density.
 * - Spawn count badges (e.g. "x8"), multi-combat indicators ("Multi"), and wilderness badges.
 * - Rich hover tooltip with monster stats, weaknesses, slayer requirements, and top rare drops.
 * - Focused single-zone rendering & highlighting with interactive click navigation.
 */
@Slf4j
@Singleton
public class WorldMapMonsterZoneOverlay extends Overlay implements MouseListener
{
    // Color Palette
    private static final Color BG_DARK = new Color(15, 15, 20, 252);
    private static final Color BORDER_GOLD = new Color(255, 185, 45, 230);
    private static final Color TITLE_GOLD = new Color(255, 152, 31);
    private static final Color MULTI_AMBER = new Color(245, 158, 11);
    private static final Color WILDY_RED = new Color(239, 68, 68);
    private static final Color BOSS_PURPLE = new Color(168, 85, 247);
    private static final Color SLAYER_GREEN = new Color(16, 185, 129);
    private static final Color STANDARD_CYAN = new Color(6, 182, 212);
    private static final Color TEXT_WHITE = new Color(240, 240, 240);
    private static final Color TEXT_MUTED = new Color(170, 170, 170);
    private static final Color DROP_GOLD = new Color(255, 215, 0);
    private static final Color DROP_PURPLE = new Color(192, 132, 252);
    private static final Color BADGE_BG = new Color(12, 12, 12, 225);
    private static final Color CLUSTER_PURPLE = new Color(167, 139, 250);

    // --- Low-zoom spawn zone clustering ---
    // getWorldMapZoom() returns pixels-per-tile (see WorldMapOverlay#mapWorldPointToGraphicsPoint), so a
    // smaller value means more of the world is visible per pixel (zoomed out). Below this threshold zone
    // boxes are small/dense enough on screen that grouping nearby ones into a single "xN" marker reduces
    // clutter; at/above it every zone still renders individually as before.
    private static final float CLUSTER_ZOOM_THRESHOLD = 4.0f;
    // Screen-pixel proximity radius used to greedily group zone centers into one cluster marker.
    private static final float CLUSTER_PIXEL_RADIUS = 48f;
    // Don't bother clustering unless there are enough open-world zones in view to plausibly clutter the map.
    private static final int CLUSTER_MIN_ELIGIBLE_ZONES = 4;
    // Above this open-world entry count, cluster regardless of zoom - a safety bound on how many
    // full glowing boxes + radial-gradient fills a single frame can be asked to rasterize (e.g. "Rat"
    // ships 88 spawn zones). Overlapping-on-screen zones merge; the rest still render individually.
    private static final int CLUSTER_FORCE_ENTRY_COUNT = 50;

    // Interned glow colors (getGlowColor used to allocate a fresh Color every call, per box per frame).
    private static final Color GLOW_WILDY = new Color(255, 99, 71);
    private static final Color GLOW_MULTI = new Color(251, 191, 36);
    private static final Color GLOW_BOSS = new Color(192, 132, 252);
    private static final Color GLOW_SLAYER = new Color(52, 211, 153);
    private static final Color GLOW_DEFAULT = new Color(56, 189, 248);

    // Interned strokes for renderGlowingBorder (3 BasicStroke allocations per box per frame -> 0).
    private static final BasicStroke STROKE_BLOOM_HOVER = new BasicStroke(6.0f);
    private static final BasicStroke STROKE_BLOOM_FOCUS = new BasicStroke(5.0f);
    private static final BasicStroke STROKE_BLOOM = new BasicStroke(4.5f);
    private static final BasicStroke STROKE_AURA_HOVER = new BasicStroke(3.5f);
    private static final BasicStroke STROKE_AURA_FOCUS = new BasicStroke(3.0f);
    private static final BasicStroke STROKE_AURA = new BasicStroke(2.5f);
    private static final BasicStroke STROKE_CORE_WIDE = new BasicStroke(2.0f);
    private static final BasicStroke STROKE_CORE = new BasicStroke(1.5f);

    private static final float[] HEAT_FRACTIONS = {0.0f, 0.5f, 1.0f};

    public static class MonsterZoneEntry
    {
        private final Monster monster;
        private final MonsterSpawnZone zone;
        private final Color customColor;

        // Per-entry derived data, filled once by the overlay (ensureDerived). The inputs (monster,
        // zone, dungeon DB) are immutable after construction, so this is computed one time instead of
        // re-scanning the DungeonEntranceDatabase 3x per entry per frame in the render loop.
        boolean derivedReady = false;
        boolean underground = false;
        WorldPoint surfaceEntrance = null;
        // True when surfaceEntrance is an estimate (unverified zone entrance or a DungeonEntranceDatabase
        // fallback) - the marker/beacon then read as an approximate area, not a pinpoint.
        boolean entranceApproximate = false;
        String dungeonName = null;
        Color themeColor = null;
        Color glowColor = null;

        public MonsterZoneEntry(Monster monster, MonsterSpawnZone zone, Color customColor)
        {
            this.monster = monster;
            this.zone = zone;
            this.customColor = customColor;
        }

        public Monster getMonster() { return monster; }
        public MonsterSpawnZone getZone() { return zone; }
        public Color getCustomColor() { return customColor; }

        public static MonsterZoneEntryBuilder builder() { return new MonsterZoneEntryBuilder(); }

        public static class MonsterZoneEntryBuilder
        {
            private Monster monster;
            private MonsterSpawnZone zone;
            private Color customColor;

            public MonsterZoneEntryBuilder monster(Monster monster) { this.monster = monster; return this; }
            public MonsterZoneEntryBuilder zone(MonsterSpawnZone zone) { this.zone = zone; return this; }
            public MonsterZoneEntryBuilder customColor(Color customColor) { this.customColor = customColor; return this; }
            public MonsterZoneEntry build() { return new MonsterZoneEntry(monster, zone, customColor); }
        }
    }

    private static class RenderedZoneBox
    {
        private final MonsterZoneEntry entry;
        private final Rectangle screenBounds;
        private final Rectangle spawnBadgeBounds;
        private final Rectangle multiBadgeBounds;
        private final Rectangle wildyBadgeBounds;
        private final Rectangle bossBadgeBounds;
        private final Rectangle entranceBadgeBounds;
        private final Color themeColor;
        private final Color glowColor;
        private final boolean isDungeonEntrance;
        private final WorldPoint entrancePoint;
        // Screen-space projection of entrancePoint, captured at render time so the mouse handler can
        // reuse it instead of re-projecting off the client thread (D21). Render-thread write,
        // published to the mouse thread via renderedBoxesSnapshot.
        private java.awt.Point entranceScreen;
        private final String dungeonName;
        private final boolean isFocused;
        private final boolean isCluster;
        private final List<MonsterZoneEntry> clusterEntries;
        private final WorldPoint clusterCentroid;

        RenderedZoneBox(MonsterZoneEntry entry, Rectangle screenBounds, Rectangle spawnBadgeBounds,
                               Rectangle multiBadgeBounds, Rectangle wildyBadgeBounds, Rectangle bossBadgeBounds,
                               Rectangle entranceBadgeBounds, Color themeColor, Color glowColor,
                               boolean isDungeonEntrance, WorldPoint entrancePoint, String dungeonName,
                               boolean isFocused, boolean isCluster, List<MonsterZoneEntry> clusterEntries,
                               WorldPoint clusterCentroid)
        {
            this.entry = entry;
            this.screenBounds = screenBounds;
            this.spawnBadgeBounds = spawnBadgeBounds;
            this.multiBadgeBounds = multiBadgeBounds;
            this.wildyBadgeBounds = wildyBadgeBounds;
            this.bossBadgeBounds = bossBadgeBounds;
            this.entranceBadgeBounds = entranceBadgeBounds;
            this.themeColor = themeColor;
            this.glowColor = glowColor;
            this.isDungeonEntrance = isDungeonEntrance;
            this.entrancePoint = entrancePoint;
            this.dungeonName = dungeonName;
            this.isFocused = isFocused;
            this.isCluster = isCluster;
            this.clusterEntries = clusterEntries;
            this.clusterCentroid = clusterCentroid;
        }

        public MonsterZoneEntry getEntry() { return entry; }
        public Rectangle getScreenBounds() { return screenBounds; }
        public Rectangle getSpawnBadgeBounds() { return spawnBadgeBounds; }
        public Rectangle getMultiBadgeBounds() { return multiBadgeBounds; }
        public Rectangle getWildyBadgeBounds() { return wildyBadgeBounds; }
        public Rectangle getBossBadgeBounds() { return bossBadgeBounds; }
        public Rectangle getEntranceBadgeBounds() { return entranceBadgeBounds; }
        public Color getThemeColor() { return themeColor; }
        public Color getGlowColor() { return glowColor; }
        public boolean isDungeonEntrance() { return isDungeonEntrance; }
        public WorldPoint getEntrancePoint() { return entrancePoint; }
        public java.awt.Point getEntranceScreen() { return entranceScreen; }
        void setEntranceScreen(java.awt.Point p) { this.entranceScreen = p; }
        public String getDungeonName() { return dungeonName; }
        public boolean isFocused() { return isFocused; }
        public boolean isCluster() { return isCluster; }
        public List<MonsterZoneEntry> getClusterEntries() { return clusterEntries; }
        public WorldPoint getClusterCentroid() { return clusterCentroid; }

        public static RenderedZoneBoxBuilder builder() { return new RenderedZoneBoxBuilder(); }

        public static class RenderedZoneBoxBuilder
        {
            private MonsterZoneEntry entry;
            private Rectangle screenBounds;
            private Rectangle spawnBadgeBounds;
            private Rectangle multiBadgeBounds;
            private Rectangle wildyBadgeBounds;
            private Rectangle bossBadgeBounds;
            private Rectangle entranceBadgeBounds;
            private Color themeColor;
            private Color glowColor;
            private boolean isDungeonEntrance;
            private WorldPoint entrancePoint;
            private String dungeonName;
            private boolean isFocused;
            private boolean isCluster = false;
            private List<MonsterZoneEntry> clusterEntries = null;
            private WorldPoint clusterCentroid = null;

            public RenderedZoneBoxBuilder entry(MonsterZoneEntry entry) { this.entry = entry; return this; }
            public RenderedZoneBoxBuilder screenBounds(Rectangle screenBounds) { this.screenBounds = screenBounds; return this; }
            public RenderedZoneBoxBuilder spawnBadgeBounds(Rectangle spawnBadgeBounds) { this.spawnBadgeBounds = spawnBadgeBounds; return this; }
            public RenderedZoneBoxBuilder multiBadgeBounds(Rectangle multiBadgeBounds) { this.multiBadgeBounds = multiBadgeBounds; return this; }
            public RenderedZoneBoxBuilder wildyBadgeBounds(Rectangle wildyBadgeBounds) { this.wildyBadgeBounds = wildyBadgeBounds; return this; }
            public RenderedZoneBoxBuilder bossBadgeBounds(Rectangle bossBadgeBounds) { this.bossBadgeBounds = bossBadgeBounds; return this; }
            public RenderedZoneBoxBuilder entranceBadgeBounds(Rectangle entranceBadgeBounds) { this.entranceBadgeBounds = entranceBadgeBounds; return this; }
            public RenderedZoneBoxBuilder themeColor(Color themeColor) { this.themeColor = themeColor; return this; }
            public RenderedZoneBoxBuilder glowColor(Color glowColor) { this.glowColor = glowColor; return this; }
            public RenderedZoneBoxBuilder isDungeonEntrance(boolean isDungeonEntrance) { this.isDungeonEntrance = isDungeonEntrance; return this; }
            public RenderedZoneBoxBuilder entrancePoint(WorldPoint entrancePoint) { this.entrancePoint = entrancePoint; return this; }
            public RenderedZoneBoxBuilder dungeonName(String dungeonName) { this.dungeonName = dungeonName; return this; }
            public RenderedZoneBoxBuilder isFocused(boolean isFocused) { this.isFocused = isFocused; return this; }
            public RenderedZoneBoxBuilder isCluster(boolean isCluster) { this.isCluster = isCluster; return this; }
            public RenderedZoneBoxBuilder clusterEntries(List<MonsterZoneEntry> clusterEntries) { this.clusterEntries = clusterEntries; return this; }
            public RenderedZoneBoxBuilder clusterCentroid(WorldPoint clusterCentroid) { this.clusterCentroid = clusterCentroid; return this; }

            public RenderedZoneBox build()
            {
                return new RenderedZoneBox(entry, screenBounds, spawnBadgeBounds, multiBadgeBounds,
                    wildyBadgeBounds, bossBadgeBounds, entranceBadgeBounds, themeColor, glowColor,
                    isDungeonEntrance, entrancePoint, dungeonName, isFocused, isCluster,
                    clusterEntries, clusterCentroid);
            }
        }
    }

    private final Client client;
    private final WorldMapOverlay worldMapOverlay;
    private final OsrsCopilotConfig config;
    private final WorldMapFocusBeaconOverlay beaconOverlay;
    private final DungeonEntranceDatabase dungeonEntranceDatabase;

    // Active zone entries to render
    private final List<MonsterZoneEntry> activeEntries = new CopyOnWriteArrayList<>();
    // Render-thread-only working list. At the end of each render() an immutable copy is published to
    // renderedBoxesSnapshot, which is the ONLY thing the AWT/mouse thread reads - so a click never
    // iterates a list the render thread is mid-mutation on.
    private final List<RenderedZoneBox> renderedBoxes = new ArrayList<>();
    private volatile List<RenderedZoneBox> renderedBoxesSnapshot = Collections.emptyList();

    @Getter
    private volatile Monster focusedMonster = null;
    @Getter
    private volatile MonsterSpawnZone focusedZone = null;
    private volatile boolean zonesVisible = true;

    /** Effective visibility: the in-memory toggle AND the persistent config item. */
    public boolean isZonesVisible()
    {
        return zonesVisible && (config == null || config.showMonsterZones());
    }

    public void setZonesVisible(boolean zonesVisible)
    {
        this.zonesVisible = zonesVisible;
    }

    private BiConsumer<Monster, MonsterSpawnZone> onZoneClickListener = null;
    private Consumer<MonsterSpawnZone> onZoneSelectionCallback = null;
    private Consumer<Monster> onMonsterSelectionCallback = null;

    private java.awt.Point mousePressPoint = null;
    private RenderedZoneBox hoveredBox = null;
    private volatile boolean lastWorldMapOpen = false;

    // Incremented on every mutation of activeEntries; used to invalidate the cached cluster grouping
    // below without needing to re-hash/re-scan the (potentially large) entry list every frame.
    private volatile int entriesVersion = 0;

    // Cached clustering result - the O(n^2) proximity grouping only re-runs when the zoom level moves
    // materially or the active zone set changes, never unconditionally in the render loop.
    private List<List<MonsterZoneEntry>> cachedClusters = null;
    private int cachedClustersEntriesVersion = -1;
    private float cachedClustersZoom = Float.NaN;

    @Inject
    public WorldMapMonsterZoneOverlay(
        Client client,
        WorldMapOverlay worldMapOverlay,
        OsrsCopilotConfig config,
        WorldMapFocusBeaconOverlay beaconOverlay,
        DungeonEntranceDatabase dungeonEntranceDatabase)
    {
        this.client = client;
        this.worldMapOverlay = worldMapOverlay;
        this.config = config;
        this.beaconOverlay = beaconOverlay;
        this.dungeonEntranceDatabase = dungeonEntranceDatabase;

        setPosition(OverlayPosition.DYNAMIC);
        setPriority(PRIORITY_HIGHEST);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
    }

    public WorldMapMonsterZoneOverlay(
        Client client,
        WorldMapOverlay worldMapOverlay,
        OsrsCopilotConfig config,
        WorldMapFocusBeaconOverlay beaconOverlay)
    {
        this(client, worldMapOverlay, config, beaconOverlay, new DungeonEntranceDatabase());
    }

    /**
     * Sets a monster as the single focused monster and loads all its spawn zones.
     */
    public void setFocusedMonster(Monster monster)
    {
        this.focusedMonster = monster;
        this.focusedZone = null;
        activeEntries.clear();

        if (monster != null && monster.getSpawnZones() != null)
        {
            for (MonsterSpawnZone zone : monster.getSpawnZones())
            {
                activeEntries.add(MonsterZoneEntry.builder()
                    .monster(monster)
                    .zone(zone)
                    .build());
            }
        }
        if (onMonsterSelectionCallback != null && monster != null)
        {
            onMonsterSelectionCallback.accept(monster);
        }
        entriesVersion++;
    }

    /**
     * Sets a specific zone as focused for a given monster and renders that single zone in focused mode.
     */
    public void setFocusedZone(Monster monster, MonsterSpawnZone zone)
    {
        this.focusedMonster = monster;
        this.focusedZone = zone;
        activeEntries.clear();

        if (zone != null)
        {
            activeEntries.add(MonsterZoneEntry.builder()
                .monster(monster)
                .zone(zone)
                .build());
            if (onZoneSelectionCallback != null)
            {
                onZoneSelectionCallback.accept(zone);
            }
        }
        entriesVersion++;
    }

    /**
     * Highlights a specific zone without replacing all active entries.
     */
    public void highlightZone(MonsterSpawnZone zone)
    {
        this.focusedZone = zone;
    }

    /**
     * Adds a monster spawn zone to the active rendering list.
     */
    public void addMonsterZone(Monster monster, MonsterSpawnZone zone)
    {
        if (zone == null)
        {
            return;
        }
        activeEntries.add(MonsterZoneEntry.builder()
            .monster(monster)
            .zone(zone)
            .build());
        entriesVersion++;
    }

    /**
     * Adds all spawn zones for the given monster to the rendering list.
     */
    public void addMonster(Monster monster)
    {
        if (monster == null || monster.getSpawnZones() == null)
        {
            return;
        }
        for (MonsterSpawnZone zone : monster.getSpawnZones())
        {
            activeEntries.add(MonsterZoneEntry.builder()
                .monster(monster)
                .zone(zone)
                .build());
        }
        entriesVersion++;
    }

    /**
     * Clears all active monster zones from the overlay.
     */
    public void clearActiveZones()
    {
        activeEntries.clear();
        focusedMonster = null;
        focusedZone = null;
        hoveredBox = null;
        entriesVersion++;
    }

    public void clear()
    {
        clearActiveZones();
    }

    public List<MonsterZoneEntry> getActiveEntries()
    {
        return Collections.unmodifiableList(activeEntries);
    }

    public void setOnZoneClickListener(BiConsumer<Monster, MonsterSpawnZone> listener)
    {
        this.onZoneClickListener = listener;
    }

    public void setOnZoneSelectionCallback(Consumer<MonsterSpawnZone> callback)
    {
        this.onZoneSelectionCallback = callback;
    }

    public void setOnMonsterSelectionCallback(Consumer<Monster> callback)
    {
        this.onMonsterSelectionCallback = callback;
    }

    /** Clears the render-thread working list and publishes an empty snapshot for the mouse thread. */
    private void resetRendered()
    {
        renderedBoxes.clear();
        renderedBoxesSnapshot = Collections.emptyList();
        hoveredBox = null;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (client == null || client.getGameState() != GameState.LOGGED_IN
            || !isZonesVisible() || activeEntries.isEmpty())
        {
            lastWorldMapOpen = false;
            resetRendered();
            return null;
        }

        Widget worldMap = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        if (worldMap == null || worldMap.isHidden())
        {
            lastWorldMapOpen = false;
            resetRendered();
            return null;
        }

        Rectangle mapBounds = worldMap.getBounds();
        if (mapBounds == null)
        {
            lastWorldMapOpen = false;
            resetRendered();
            return null;
        }

        lastWorldMapOpen = true;
        Point mouseCanvasPos = client.getMouseCanvasPosition();
        boolean mouseInMap = mouseCanvasPos != null && mapBounds.contains(mouseCanvasPos.getX(), mouseCanvasPos.getY());
        int mx = mouseCanvasPos != null ? mouseCanvasPos.getX() : -1;
        int my = mouseCanvasPos != null ? mouseCanvasPos.getY() : -1;

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Shape originalClip = graphics.getClip();
        graphics.setClip(mapBounds);

        renderedBoxes.clear();
        hoveredBox = null;

        long now = System.currentTimeMillis();
        double pulse = (Math.sin(now / 350.0) + 1.0) / 2.0; // 0.0 to 1.0 smooth sine wave

        // 1. Render all active zones or dungeon entrance portal markers.
        // Dungeon/underground entries always render individually (portal marker on the surface map, or a
        // full box if actually viewing the dungeon sub-map) - clustering only ever applies to open-world
        // zones, which is where the "many nearby spawn boxes" clutter this feature targets actually occurs.
        List<MonsterZoneEntry> openWorldEntries = new ArrayList<>();
        for (MonsterZoneEntry entry : activeEntries)
        {
            MonsterSpawnZone zone = entry.getZone();
            if (zone == null)
            {
                continue;
            }

            ensureDerived(entry);
            if (entry.underground)
            {
                renderSingleZoneEntry(graphics, entry, mapBounds, pulse, mx, my, mouseInMap);
            }
            else
            {
                openWorldEntries.add(entry);
            }
        }

        float currentZoom = getCurrentZoomSafe();
        boolean shouldCluster = openWorldEntries.size() > CLUSTER_MIN_ELIGIBLE_ZONES
            && (currentZoom < CLUSTER_ZOOM_THRESHOLD || openWorldEntries.size() >= CLUSTER_FORCE_ENTRY_COUNT);

        if (!shouldCluster)
        {
            for (MonsterZoneEntry entry : openWorldEntries)
            {
                renderSingleZoneEntry(graphics, entry, mapBounds, pulse, mx, my, mouseInMap);
            }
        }
        else
        {
            for (List<MonsterZoneEntry> cluster : getOrComputeClusters(openWorldEntries, currentZoom))
            {
                if (cluster.size() == 1)
                {
                    renderSingleZoneEntry(graphics, cluster.get(0), mapBounds, pulse, mx, my, mouseInMap);
                }
                else
                {
                    renderClusterEntry(graphics, cluster, mapBounds, pulse, mx, my, mouseInMap);
                }
            }
        }

        // Publish an immutable copy for the mouse thread (see renderedBoxesSnapshot).
        renderedBoxesSnapshot = Collections.unmodifiableList(new ArrayList<>(renderedBoxes));

        // Restore clip before rendering hover tooltip so tooltip is never clipped
        graphics.setClip(originalClip);

        // 2. Render rich monster tooltip if hovering over any zone, badge, or dungeon entrance portal
        if (hoveredBox != null && mouseInMap)
        {
            if (hoveredBox.isCluster())
            {
                renderClusterHoverTooltip(graphics, hoveredBox, mx + 16, my + 24);
            }
            else
            {
                renderMonsterHoverTooltip(graphics, hoveredBox.getEntry().getMonster(), hoveredBox.getEntry().getZone(), hoveredBox, mx + 16, my + 24);
            }
        }

        return null;
    }

    /**
     * Renders one zone entry using the pre-existing per-entry logic (dungeon entrance portal marker or
     * full glowing bounding box + badges), unchanged from before clustering was introduced. Adds the
     * resulting box to {@link #renderedBoxes} and updates {@link #hoveredBox} exactly as the old inline
     * loop body did.
     */
    private void renderSingleZoneEntry(
        Graphics2D graphics,
        MonsterZoneEntry entry,
        Rectangle mapBounds,
        double pulse,
        int mx,
        int my,
        boolean mouseInMap)
    {
        Monster monster = entry.getMonster();
        MonsterSpawnZone zone = entry.getZone();
        if (zone == null)
        {
            return;
        }

        ensureDerived(entry);
        boolean isFocused = (focusedZone != null && zone.equals(focusedZone));
        Color themeColor = entry.themeColor;
        Color glowColor = entry.glowColor;

        boolean isUnderground = entry.underground;
        WorldPoint surfaceEntrance = entry.surfaceEntrance;
        String dungeonName = entry.dungeonName;

        // Check if user is on the Surface map vs Dungeon sub-map
        if (isUnderground && surfaceEntrance != null && isViewingSurfaceMap(zone, surfaceEntrance))
        {
            // Render authentic Dungeon Entrance Monster Portal marker on surface
            Point entranceScreen = worldMapOverlay.mapWorldPointToGraphicsPoint(surfaceEntrance);
            if (entranceScreen == null)
            {
                return;
            }

            if (!mapBounds.contains(entranceScreen.getX(), entranceScreen.getY()))
            {
                return;
            }

            RenderedZoneBox portalBox = renderDungeonEntranceMarker(
                graphics,
                entry,
                entranceScreen,
                dungeonName,
                surfaceEntrance,
                themeColor,
                glowColor,
                pulse,
                isFocused,
                mx,
                my,
                mouseInMap
            );

            portalBox.setEntranceScreen(new java.awt.Point(entranceScreen.getX(), entranceScreen.getY()));
            renderedBoxes.add(portalBox);
            if (mouseInMap && (portalBox.getScreenBounds().contains(mx, my) || (Math.pow(mx - entranceScreen.getX(), 2) + Math.pow(my - entranceScreen.getY(), 2) <= 576)))
            {
                hoveredBox = portalBox;
            }
        }
        else
        {
            // Inside dungeon sub-map or on surface overworld: render full bounding box polygon
            Rectangle screenRect = projectZoneToScreen(zone);
            if (screenRect == null || !mapBounds.intersects(screenRect))
            {
                return;
            }

            boolean isHovered = mouseInMap && screenRect.contains(mx, my);

            // Render Zone Heat Gradient Center
            renderHeatGradientCenter(graphics, screenRect, themeColor, pulse, isHovered, isFocused);

            // Render Glowing Rounded Bounding Polygons
            renderGlowingBorder(graphics, screenRect, themeColor, glowColor, pulse, isHovered, isFocused);

            // Render Badges (Spawn count, Multi-combat, Wilderness, Boss)
            RenderedZoneBox box = renderZoneBadges(graphics, entry, screenRect, themeColor, glowColor, isHovered, isFocused);
            renderedBoxes.add(box);

            if (isHovered || (mouseInMap && isMouseOverBadges(box, mx, my)))
            {
                hoveredBox = box;
            }
        }
    }

    /**
     * Resolves the world map's current zoom level (pixels-per-tile) defensively. Fails open to a large
     * value (i.e. "treat as fully zoomed in, never cluster") if the world map isn't available, matching
     * this overlay's existing fail-open convention for other WorldMap-derived state.
     */
    private float getCurrentZoomSafe()
    {
        try
        {
            WorldMap worldMap = client.getWorldMap();
            if (worldMap != null)
            {
                float zoom = worldMap.getWorldMapZoom();
                if (zoom > 0f)
                {
                    return zoom;
                }
            }
        }
        catch (Exception ignored)
        {
            // fall through to fail-open default below
        }
        return Float.MAX_VALUE;
    }

    /**
     * Returns the cached cluster grouping for the current open-world entries, recomputing it only when
     * the zoom level has moved materially or the active zone set has changed since the last computation.
     * This keeps the O(n^2) proximity grouping (see {@link #clusterEntries}) out of the steady-state
     * render loop - most frames just reuse the cached result.
     */
    private List<List<MonsterZoneEntry>> getOrComputeClusters(List<MonsterZoneEntry> openWorldEntries, float zoom)
    {
        boolean zoomChanged = Float.isNaN(cachedClustersZoom) || Math.abs(cachedClustersZoom - zoom) > 0.15f;
        boolean entriesChanged = cachedClustersEntriesVersion != entriesVersion;

        if (cachedClusters == null || zoomChanged || entriesChanged)
        {
            cachedClusters = clusterEntries(openWorldEntries, zoom, CLUSTER_PIXEL_RADIUS);
            cachedClustersZoom = zoom;
            cachedClustersEntriesVersion = entriesVersion;
        }

        return cachedClusters;
    }

    /**
     * Greedily groups zone entries whose world-space centers project to within {@code pixelRadius} screen
     * pixels of each other at the given zoom level (getWorldMapZoom() is pixels-per-tile, so the pixel
     * radius is converted to a tile radius via division by zoom). Entries on different planes, or with no
     * resolvable center point, are never grouped together.
     * <p>
     * This is intentionally a single-pass greedy clustering (each entry either joins the first
     * still-open cluster whose seed point is within range, or starts a new cluster) rather than an exact
     * nearest-neighbor solution - it is O(n^2) in the worst case, but n is the number of spawn zones for
     * the entries currently loaded into the overlay (typically at most a few dozen), and the result is
     * cached (see {@link #getOrComputeClusters}) rather than recomputed every frame, so the worst case
     * never actually runs on the render/hot path.
     * <p>
     * Package-private static so it can be unit tested directly with synthetic zone data, independent of
     * any live RuneLite client/widget state.
     */
    static List<List<MonsterZoneEntry>> clusterEntries(List<MonsterZoneEntry> entries, float zoom, float pixelRadius)
    {
        List<List<MonsterZoneEntry>> clusters = new ArrayList<>();
        if (entries == null || entries.isEmpty())
        {
            return clusters;
        }

        float safeZoom = zoom > 0f && !Float.isInfinite(zoom) ? zoom : 1f;
        double tileRadius = pixelRadius / safeZoom;
        double tileRadiusSq = tileRadius * tileRadius;

        boolean[] assigned = new boolean[entries.size()];
        for (int i = 0; i < entries.size(); i++)
        {
            if (assigned[i])
            {
                continue;
            }
            assigned[i] = true;

            List<MonsterZoneEntry> cluster = new ArrayList<>();
            cluster.add(entries.get(i));

            WorldPoint centerI = worldCenterOf(entries.get(i).getZone());
            if (centerI != null)
            {
                for (int j = i + 1; j < entries.size(); j++)
                {
                    if (assigned[j])
                    {
                        continue;
                    }
                    WorldPoint centerJ = worldCenterOf(entries.get(j).getZone());
                    if (centerJ == null || centerJ.getPlane() != centerI.getPlane())
                    {
                        continue;
                    }
                    double dx = centerJ.getX() - centerI.getX();
                    double dy = centerJ.getY() - centerI.getY();
                    if (dx * dx + dy * dy <= tileRadiusSq)
                    {
                        cluster.add(entries.get(j));
                        assigned[j] = true;
                    }
                }
            }

            clusters.add(cluster);
        }

        return clusters;
    }

    private static WorldPoint worldCenterOf(MonsterSpawnZone zone)
    {
        if (zone == null)
        {
            return null;
        }
        WorldPoint center = zone.getZoneCenter();
        return center != null ? center : zone.getEffectiveFocusPoint();
    }

    private static WorldPoint clusterCentroid(List<MonsterZoneEntry> cluster)
    {
        long sumX = 0;
        long sumY = 0;
        int plane = 0;
        int count = 0;
        for (MonsterZoneEntry entry : cluster)
        {
            WorldPoint center = worldCenterOf(entry.getZone());
            if (center == null)
            {
                continue;
            }
            sumX += center.getX();
            sumY += center.getY();
            plane = center.getPlane();
            count++;
        }
        if (count == 0)
        {
            return null;
        }
        return new WorldPoint((int) (sumX / count), (int) (sumY / count), plane);
    }

    /**
     * Renders a compact combined marker for a cluster of >= 2 nearby spawn zones: a pulsing badge circle
     * with an "xN" count, in the same visual language (glow/pulse/badge-pill) as the individual zone
     * badges, but far cheaper than rendering N full glowing boxes on top of each other.
     */
    private void renderClusterEntry(
        Graphics2D g,
        List<MonsterZoneEntry> cluster,
        Rectangle mapBounds,
        double pulse,
        int mx,
        int my,
        boolean mouseInMap)
    {
        WorldPoint centroid = clusterCentroid(cluster);
        if (centroid == null)
        {
            return;
        }

        Point centerScreen = worldMapOverlay.mapWorldPointToGraphicsPoint(centroid);
        if (centerScreen == null || !mapBounds.contains(centerScreen.getX(), centerScreen.getY()))
        {
            return;
        }

        int cx = centerScreen.getX();
        int cy = centerScreen.getY();

        // Theme: escalate to wilderness/boss coloring if any member of the cluster warrants it, otherwise
        // a neutral cluster-purple so clustered markers read as visually distinct from single zone boxes.
        Color clusterColor = CLUSTER_PURPLE;
        for (MonsterZoneEntry entry : cluster)
        {
            MonsterSpawnZone z = entry.getZone();
            if (z != null && z.getWildernessLevel() > 0)
            {
                clusterColor = WILDY_RED;
                break;
            }
            if (entry.getMonster() != null && "Boss".equalsIgnoreCase(entry.getMonster().getCategory()))
            {
                clusterColor = BOSS_PURPLE;
            }
        }
        Color glow = getGlowColor(clusterColor);

        int radius = 15;
        double distSq = Math.pow(mx - cx, 2) + Math.pow(my - cy, 2);
        boolean isHovered = mouseInMap && distSq <= (radius + 6) * (radius + 6);

        // Outer bloom
        int bloomAlpha = (int) (55 + pulse * 35) + (isHovered ? 40 : 0);
        g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), Math.max(0, Math.min(255, bloomAlpha))));
        g.setStroke(new BasicStroke(isHovered ? 5.0f : 3.5f));
        g.drawOval(cx - radius - 4, cy - radius - 4, (radius + 4) * 2, (radius + 4) * 2);

        // Fill disc
        g.setColor(new Color(15, 12, 24, isHovered ? 245 : 225));
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);

        // Core ring
        g.setColor(clusterColor);
        g.setStroke(new BasicStroke(isHovered ? 2.5f : 1.75f));
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);

        // Count label
        Font boldFont = FontManager.getRunescapeBoldFont();
        g.setFont(boldFont);
        FontMetrics fm = g.getFontMetrics();
        String countText = "x" + cluster.size();
        int tw = fm.stringWidth(countText);
        g.setColor(TEXT_WHITE);
        g.drawString(countText, cx - tw / 2, cy + fm.getAscent() / 2 - 2);

        Rectangle bounds = new Rectangle(cx - radius - 4, cy - radius - 4, (radius + 4) * 2, (radius + 4) * 2);

        RenderedZoneBox box = RenderedZoneBox.builder()
            .entry(cluster.get(0))
            .screenBounds(bounds)
            .themeColor(clusterColor)
            .glowColor(glow)
            .isDungeonEntrance(false)
            .isFocused(false)
            .isCluster(true)
            .clusterEntries(cluster)
            .clusterCentroid(centroid)
            .build();

        renderedBoxes.add(box);
        if (isHovered)
        {
            hoveredBox = box;
        }
    }

    /**
     * Renders a lightweight hover tooltip for a cluster marker: how many zones are grouped, and either
     * the shared monster name (if the cluster is all one monster) or how many distinct monsters it spans.
     */
    private void renderClusterHoverTooltip(Graphics2D g, RenderedZoneBox clusterBox, int startX, int startY)
    {
        List<MonsterZoneEntry> cluster = clusterBox.getClusterEntries();
        if (cluster == null || cluster.isEmpty())
        {
            return;
        }

        Set<String> monsterNames = new LinkedHashSet<>();
        for (MonsterZoneEntry entry : cluster)
        {
            if (entry.getMonster() != null && entry.getMonster().getName() != null)
            {
                monsterNames.add(entry.getMonster().getName());
            }
        }

        Font boldFont = FontManager.getRunescapeBoldFont();
        Font smallFont = FontManager.getRunescapeSmallFont();
        FontMetrics fmBold = g.getFontMetrics(boldFont);
        FontMetrics fmSmall = g.getFontMetrics(smallFont);

        String titleStr = cluster.size() + " spawn zones nearby";
        String subtitleStr = monsterNames.size() == 1
            ? monsterNames.iterator().next()
            : monsterNames.size() + " different monsters";
        String hintStr = "Zoom in or click to see individually";

        int cardWidth = Math.max(200, Math.max(fmBold.stringWidth(titleStr), Math.max(fmSmall.stringWidth(subtitleStr), fmSmall.stringWidth(hintStr))) + 24);
        int cardHeight = 60;

        if (startY + cardHeight > client.getCanvasHeight() - 10)
        {
            startY = Math.max(10, startY - cardHeight - 44);
        }
        if (startX + cardWidth > client.getCanvasWidth() - 10)
        {
            startX = Math.max(10, client.getCanvasWidth() - cardWidth - 10);
        }

        g.setColor(BG_DARK);
        g.fillRoundRect(startX, startY, cardWidth, cardHeight, 8, 8);
        g.setColor(BORDER_GOLD);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(startX, startY, cardWidth, cardHeight, 8, 8);

        int curY = startY + 18;
        g.setFont(boldFont);
        g.setColor(TITLE_GOLD);
        g.drawString(titleStr, startX + 12, curY);

        curY += 16;
        g.setFont(smallFont);
        g.setColor(TEXT_MUTED);
        g.drawString(subtitleStr, startX + 12, curY);

        curY += 18;
        g.setColor(new Color(130, 130, 130));
        g.drawString(hintStr, startX + 12, curY);
    }

    /**
     * Checks whether a monster spawn zone is underground or located inside a dungeon.
     */
    public boolean isDungeonOrUnderground(MonsterSpawnZone zone)
    {
        if (zone == null)
        {
            return false;
        }
        if (zone.getSurfaceEntrance() != null || (zone.getDungeonName() != null && !zone.getDungeonName().isEmpty()))
        {
            return true;
        }

        WorldPoint center = zone.getZoneCenter();
        if (center != null)
        {
            if (dungeonEntranceDatabase != null && dungeonEntranceDatabase.isUndergroundOrDungeon(center))
            {
                return true;
            }
            if (center.getY() >= 6400 || (center.getY() >= 4000 && center.getY() <= 5500))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Resolves the overworld surface entrance coordinate for a zone.
     */
    public WorldPoint resolveSurfaceEntrance(MonsterSpawnZone zone)
    {
        if (zone == null)
        {
            return null;
        }
        if (zone.getSurfaceEntrance() != null)
        {
            return zone.getSurfaceEntrance();
        }
        if (dungeonEntranceDatabase != null)
        {
            WorldPoint center = zone.getZoneCenter();
            if (center != null)
            {
                DungeonEntranceDatabase.DungeonMapping mapping = dungeonEntranceDatabase.findDungeon(center);
                if (mapping != null)
                {
                    return mapping.getSurfaceEntrance();
                }
            }
        }
        return null;
    }

    /**
     * True when a zone's surface entrance is an estimate rather than a verified pin: an unverified
     * zone entrance, or one only known via the {@link DungeonEntranceDatabase} fallback (none of
     * whose coordinates are verified). Callers soften the marker / beacon so it does not claim a
     * precision the data does not have.
     */
    public boolean resolveEntranceApproximate(MonsterSpawnZone zone)
    {
        if (zone == null)
        {
            return false;
        }
        if (zone.getSurfaceEntrance() != null)
        {
            return zone.isApproximateFocusPoint();
        }
        if (dungeonEntranceDatabase != null)
        {
            WorldPoint center = zone.getZoneCenter();
            if (center != null)
            {
                DungeonEntranceDatabase.DungeonMapping mapping = dungeonEntranceDatabase.findDungeon(center);
                if (mapping != null && mapping.getSurfaceEntrance() != null)
                {
                    return !mapping.isEntranceVerified();
                }
            }
        }
        return false;
    }

    /**
     * Resolves the dungeon name for a zone.
     */
    public String resolveDungeonName(MonsterSpawnZone zone)
    {
        if (zone == null)
        {
            return "Dungeon";
        }
        if (zone.getDungeonName() != null && !zone.getDungeonName().isEmpty())
        {
            return zone.getDungeonName();
        }
        if (dungeonEntranceDatabase != null)
        {
            WorldPoint center = zone.getZoneCenter();
            if (center != null)
            {
                DungeonEntranceDatabase.DungeonMapping mapping = dungeonEntranceDatabase.findDungeon(center);
                if (mapping != null && mapping.getDungeonName() != null)
                {
                    return mapping.getDungeonName();
                }
            }
        }
        if (zone.getLocationName() != null && !zone.getLocationName().isEmpty())
        {
            return zone.getLocationName();
        }
        return zone.getZoneName() != null ? zone.getZoneName() : "Dungeon";
    }

    /**
     * Checks if the active World Map view is the Surface map (vs that specific dungeon sub-map).
     */
    public boolean isViewingSurfaceMap(MonsterSpawnZone zone, WorldPoint surfaceEntrance)
    {
        if (zone == null || surfaceEntrance == null)
        {
            return true;
        }

        WorldMap worldMap = client.getWorldMap();
        if (worldMap == null || worldMap.getWorldMapData() == null)
        {
            return true;
        }

        WorldPoint center = zone.getZoneCenter();
        boolean mapContainsZone = center != null && worldMap.getWorldMapData().surfaceContainsPosition(center.getX(), center.getY());
        boolean mapContainsEntrance = worldMap.getWorldMapData().surfaceContainsPosition(surfaceEntrance.getX(), surfaceEntrance.getY());

        // If the active map surface contains the entrance, or doesn't contain the dungeon zone, we are on the surface
        return mapContainsEntrance || !mapContainsZone;
    }

    /**
     * Projects a monster spawn zone bounding box (minX, minY to maxX, maxY) into screen coordinates.
     */
    private Rectangle projectZoneToScreen(MonsterSpawnZone zone)
    {
        int minX = zone.getMinX();
        int maxX = zone.getMaxX();
        int minY = zone.getMinY();
        int maxY = zone.getMaxY();
        int plane = zone.getPlane();

        // Fallback for single point or invalid bounding box
        if (minX <= 0 || maxX <= 0 || minY <= 0 || maxY <= 0 || maxX < minX || maxY < minY)
        {
            WorldPoint center = zone.getZoneCenter() != null ? zone.getZoneCenter() : zone.getEffectiveFocusPoint();
            if (center == null)
            {
                return null;
            }
            Point pt = worldMapOverlay.mapWorldPointToGraphicsPoint(center);
            if (pt == null)
            {
                return null;
            }
            int r = 18;
            return new Rectangle(pt.getX() - r, pt.getY() - r, r * 2, r * 2);
        }

        // Project corners
        Point pNW = worldMapOverlay.mapWorldPointToGraphicsPoint(new WorldPoint(minX, maxY + 1, plane));
        Point pSE = worldMapOverlay.mapWorldPointToGraphicsPoint(new WorldPoint(maxX + 1, minY, plane));
        Point pNE = worldMapOverlay.mapWorldPointToGraphicsPoint(new WorldPoint(maxX + 1, maxY + 1, plane));
        Point pSW = worldMapOverlay.mapWorldPointToGraphicsPoint(new WorldPoint(minX, minY, plane));

        if (pNW != null && pSE != null)
        {
            int screenMinX = Math.min(Math.min(pNW.getX(), pSE.getX()), Math.min(pNE != null ? pNE.getX() : pNW.getX(), pSW != null ? pSW.getX() : pNW.getX()));
            int screenMaxX = Math.max(Math.max(pNW.getX(), pSE.getX()), Math.max(pNE != null ? pNE.getX() : pSE.getX(), pSW != null ? pSW.getX() : pSE.getX()));
            int screenMinY = Math.min(Math.min(pNW.getY(), pSE.getY()), Math.min(pNE != null ? pNE.getY() : pNW.getY(), pSW != null ? pSW.getY() : pNW.getY()));
            int screenMaxY = Math.max(Math.max(pNW.getY(), pSE.getY()), Math.max(pNE != null ? pNE.getY() : pSE.getY(), pSW != null ? pSW.getY() : pSE.getY()));

            int w = Math.max(22, screenMaxX - screenMinX);
            int h = Math.max(22, screenMaxY - screenMinY);
            return new Rectangle(screenMinX, screenMinY, w, h);
        }

        // Center fallback if corner projection partially failed
        WorldPoint centerPt = zone.getZoneCenter() != null ? zone.getZoneCenter() : zone.getEffectiveFocusPoint();
        if (centerPt != null)
        {
            Point centerScreen = worldMapOverlay.mapWorldPointToGraphicsPoint(centerPt);
            if (centerScreen != null)
            {
                int r = 24;
                return new Rectangle(centerScreen.getX() - r, centerScreen.getY() - r, r * 2, r * 2);
            }
        }

        return null;
    }

    /**
     * Renders an authentic Dungeon Entrance Monster Portal marker at the surfaceEntrance coordinate.
     */
    private RenderedZoneBox renderDungeonEntranceMarker(
        Graphics2D g,
        MonsterZoneEntry entry,
        Point entranceScreen,
        String dungeonName,
        WorldPoint surfaceEntrance,
        Color themeColor,
        Color glowColor,
        double pulse,
        boolean isFocused,
        int mx,
        int my,
        boolean mouseInMap)
    {
        Monster monster = entry.getMonster();
        MonsterSpawnZone zone = entry.getZone();
        String monsterName = monster != null ? monster.getName() : "Monster";

        int ex = entranceScreen.getX();
        int ey = entranceScreen.getY();

        int portalRadius = isFocused ? 16 : 13;

        // 1. Concentric Luminous Portal Rings / Bloom
        // Outer wide bloom ring
        int bloomAlpha = (int) (45 + pulse * 40);
        if (isFocused) bloomAlpha = Math.min(255, bloomAlpha + 50);
        g.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), Math.max(0, Math.min(255, bloomAlpha))));
        g.setStroke(new BasicStroke(isFocused ? 5.5f : 4.0f));
        g.drawOval(ex - portalRadius - 4, ey - portalRadius - 4, (portalRadius + 4) * 2, (portalRadius + 4) * 2);

        // Mid aura ring
        int auraAlpha = (int) (120 + pulse * 55);
        if (isFocused) auraAlpha = Math.min(255, auraAlpha + 45);
        g.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), Math.max(0, Math.min(255, auraAlpha))));
        g.setStroke(new BasicStroke(isFocused ? 3.0f : 2.0f));
        g.drawOval(ex - portalRadius, ey - portalRadius, portalRadius * 2, portalRadius * 2);

        // Inner portal disc fill
        int innerAlpha = (int) (130 + pulse * 45);
        g.setColor(new Color(15, 23, 42, Math.max(0, Math.min(255, innerAlpha))));
        g.fillOval(ex - portalRadius, ey - portalRadius, portalRadius * 2, portalRadius * 2);

        // Center emblem / vortex swirl (ASCII: the RS bitmap font has no star/vortex glyph)
        Font smallFont = FontManager.getRunescapeSmallFont();
        g.setFont(smallFont);
        FontMetrics fmSmall = g.getFontMetrics();
        String emblem = isFocused ? "*" : "+";
        int ew = fmSmall.stringWidth(emblem);
        int eh = fmSmall.getAscent();
        g.setColor(isFocused ? TITLE_GOLD : TEXT_WHITE);
        g.drawString(emblem, ex - ew / 2, ey + eh / 2 - 2);

        Rectangle portalBounds = new Rectangle(ex - 24, ey - 24, 48, 48);

        // 2. Dungeon Entrance Label Pill: '[Dungeon Entrance] {dungeonName} ({monsterName} Spawn)'
        Font boldFont = FontManager.getRunescapeBoldFont();
        g.setFont(boldFont);
        FontMetrics fmBold = g.getFontMetrics(boldFont);

        String entrancePrefix = entry.entranceApproximate ? "[Dungeon Entrance ~] " : "[Dungeon Entrance] ";
        String entranceBody = dungeonName + " (" + monsterName + " Spawn)";

        int prefixW = fmBold.stringWidth(entrancePrefix);
        int bodyW = fmBold.stringWidth(entranceBody);
        int totalLabelW = prefixW + bodyW + 16;
        int labelH = 20;

        int labelX = ex - totalLabelW / 2;
        int labelY = ey - portalRadius - labelH - 6;

        Rectangle entranceBadgeBounds = new Rectangle(labelX, labelY, totalLabelW, labelH);

        double mouseDistSq = (Math.pow(mx - ex, 2) + Math.pow(my - ey, 2));
        boolean isHovered = mouseInMap && (portalBounds.contains(mx, my) || entranceBadgeBounds.contains(mx, my) || mouseDistSq <= 576);

        // Draw Pill Background
        g.setColor(BADGE_BG);
        g.fillRoundRect(labelX, labelY, totalLabelW, labelH, 8, 8);

        // Pill Glowing Border
        Color pillBorderColor = isFocused ? TITLE_GOLD : (isHovered ? BORDER_GOLD : themeColor);
        g.setColor(new Color(pillBorderColor.getRed(), pillBorderColor.getGreen(), pillBorderColor.getBlue(), isHovered ? 255 : 210));
        g.setStroke(new BasicStroke(isHovered ? 2.0f : 1.25f));
        g.drawRoundRect(labelX, labelY, totalLabelW, labelH, 8, 8);

        // Text inside Pill
        int ty = labelY + ((labelH - fmBold.getHeight()) / 2) + fmBold.getAscent();
        g.setColor(TITLE_GOLD);
        g.drawString(entrancePrefix, labelX + 8, ty);

        g.setColor(TEXT_WHITE);
        g.drawString(entranceBody, labelX + 8 + prefixW, ty);

        // 3. Badges (Spawn count, Multi-combat, Wilderness, Boss)
        int badgeX = labelX + totalLabelW + 4;
        int badgeY = labelY;
        int badgeH = labelH;

        Rectangle spawnBadgeBounds = null;
        Rectangle multiBadgeBounds = null;
        Rectangle wildyBadgeBounds = null;
        Rectangle bossBadgeBounds = null;

        g.setFont(smallFont);

        // Spawn Count Badge
        int spawnCount = zone.getSpawnCount();
        if (spawnCount > 0)
        {
            String spawnText = "x" + spawnCount;
            int spawnW = fmSmall.stringWidth(spawnText) + 10;
            spawnBadgeBounds = new Rectangle(badgeX, badgeY, spawnW, badgeH);
            drawBadgePill(g, spawnBadgeBounds, spawnText, themeColor, TEXT_WHITE, isHovered);
            badgeX += spawnW + 3;
        }

        // Boss Badge
        if (monster != null && "Boss".equalsIgnoreCase(monster.getCategory()))
        {
            String bossText = "Boss";
            int bossW = fmSmall.stringWidth(bossText) + 10;
            bossBadgeBounds = new Rectangle(badgeX, badgeY, bossW, badgeH);
            drawBadgePill(g, bossBadgeBounds, bossText, BOSS_PURPLE, new Color(243, 232, 255), isHovered);
            badgeX += bossW + 3;
        }

        // Multi-combat Badge
        if (zone.isMultiCombat())
        {
            String multiText = "Multi";
            int multiW = fmSmall.stringWidth(multiText) + 10;
            multiBadgeBounds = new Rectangle(badgeX, badgeY, multiW, badgeH);
            drawBadgePill(g, multiBadgeBounds, multiText, MULTI_AMBER, new Color(254, 243, 199), isHovered);
            badgeX += multiW + 3;
        }

        // Wilderness Badge
        if (zone.getWildernessLevel() > 0)
        {
            String wildyText = "Wildy Lv." + zone.getWildernessLevel();
            int wildyW = fmSmall.stringWidth(wildyText) + 10;
            wildyBadgeBounds = new Rectangle(badgeX, badgeY, wildyW, badgeH);
            drawBadgePill(g, wildyBadgeBounds, wildyText, WILDY_RED, new Color(254, 226, 226), isHovered);
            badgeX += wildyW + 3;
        }

        // Combined Screen Bounds
        Rectangle screenBounds = portalBounds.union(entranceBadgeBounds);
        if (spawnBadgeBounds != null) screenBounds = screenBounds.union(spawnBadgeBounds);
        if (multiBadgeBounds != null) screenBounds = screenBounds.union(multiBadgeBounds);
        if (wildyBadgeBounds != null) screenBounds = screenBounds.union(wildyBadgeBounds);
        if (bossBadgeBounds != null) screenBounds = screenBounds.union(bossBadgeBounds);

        return RenderedZoneBox.builder()
            .entry(entry)
            .screenBounds(screenBounds)
            .spawnBadgeBounds(spawnBadgeBounds)
            .multiBadgeBounds(multiBadgeBounds)
            .wildyBadgeBounds(wildyBadgeBounds)
            .bossBadgeBounds(bossBadgeBounds)
            .entranceBadgeBounds(entranceBadgeBounds)
            .themeColor(themeColor)
            .glowColor(glowColor)
            .isDungeonEntrance(true)
            .entrancePoint(surfaceEntrance)
            .dungeonName(dungeonName)
            .isFocused(isFocused)
            .build();
    }

    /**
     * Renders a luminous, pulsing radial heat gradient center fill.
     */
    private void renderHeatGradientCenter(Graphics2D g, Rectangle rect, Color theme, double pulse, boolean hovered, boolean focused)
    {
        int cx = rect.x + rect.width / 2;
        int cy = rect.y + rect.height / 2;
        float radius = Math.max(rect.width, rect.height) * 0.75f;
        if (radius <= 0) radius = 16.0f;

        int centerAlpha = (int) ((hovered ? 95 : (focused ? 80 : 65)) + pulse * 25);
        int edgeAlpha = (int) ((hovered ? 30 : (focused ? 20 : 15)) + pulse * 10);

        centerAlpha = Math.max(0, Math.min(255, centerAlpha));
        edgeAlpha = Math.max(0, Math.min(255, edgeAlpha));

        Color[] colors = {
            new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), centerAlpha),
            new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), (centerAlpha + edgeAlpha) / 2),
            new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), edgeAlpha)
        };

        RadialGradientPaint gradient = new RadialGradientPaint(cx, cy, radius, HEAT_FRACTIONS, colors);
        g.setPaint(gradient);
        g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 14, 14);
    }

    /**
     * Draws multi-layer glowing rounded bounding border lines.
     */
    private void renderGlowingBorder(Graphics2D g, Rectangle rect, Color theme, Color glow, double pulse, boolean hovered, boolean focused)
    {
        int arc = 14;

        // Layer 1: Outer wide bloom
        int bloomAlpha = (int) ((hovered ? 65 : (focused ? 50 : 35)) + pulse * 25);
        g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), Math.max(0, Math.min(255, bloomAlpha))));
        g.setStroke(hovered ? STROKE_BLOOM_HOVER : (focused ? STROKE_BLOOM_FOCUS : STROKE_BLOOM));
        g.drawRoundRect(rect.x - 1, rect.y - 1, rect.width + 2, rect.height + 2, arc + 2, arc + 2);

        // Layer 2: Mid aura
        int auraAlpha = (int) ((hovered ? 140 : (focused ? 110 : 85)) + pulse * 35);
        g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), Math.max(0, Math.min(255, auraAlpha))));
        g.setStroke(hovered ? STROKE_AURA_HOVER : (focused ? STROKE_AURA_FOCUS : STROKE_AURA));
        g.drawRoundRect(rect.x, rect.y, rect.width, rect.height, arc, arc);

        // Layer 3: Inner sharp neon core
        int coreAlpha = (int) ((hovered ? 255 : (focused ? 240 : 210)) + pulse * 45);
        g.setColor(new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), Math.max(0, Math.min(255, coreAlpha))));
        g.setStroke((hovered || focused) ? STROKE_CORE_WIDE : STROKE_CORE);
        g.drawRoundRect(rect.x, rect.y, rect.width, rect.height, arc, arc);
    }

    /**
     * Renders density, multi-combat, wilderness, and boss badges on the monster zone.
     */
    private RenderedZoneBox renderZoneBadges(
        Graphics2D g,
        MonsterZoneEntry entry,
        Rectangle rect,
        Color theme,
        Color glow,
        boolean hovered,
        boolean focused)
    {
        Monster monster = entry.getMonster();
        MonsterSpawnZone zone = entry.getZone();

        Font font = FontManager.getRunescapeSmallFont();
        FontMetrics fm = g.getFontMetrics(font);
        g.setFont(font);

        int badgeY = rect.y + 4;
        int badgeH = 18;

        // If zone is very small, position badges directly above the box
        if (rect.height < 36)
        {
            badgeY = rect.y - 20;
        }

        int curX = rect.x + 6;

        Rectangle spawnBadgeBounds = null;
        Rectangle multiBadgeBounds = null;
        Rectangle wildyBadgeBounds = null;
        Rectangle bossBadgeBounds = null;

        // 1. Spawn Count Badge (e.g. "x8")
        int spawnCount = zone.getSpawnCount();
        String spawnText = "x" + (spawnCount > 0 ? spawnCount : 1);
        int spawnW = fm.stringWidth(spawnText) + 12;
        spawnBadgeBounds = new Rectangle(curX, badgeY, spawnW, badgeH);
        drawBadgePill(g, spawnBadgeBounds, spawnText, theme, TEXT_WHITE, hovered);
        curX += spawnW + 4;

        // 2. Boss Badge (if Boss)
        if (monster != null && "Boss".equalsIgnoreCase(monster.getCategory()))
        {
            String bossText = "Boss";
            int bossW = fm.stringWidth(bossText) + 12;
            bossBadgeBounds = new Rectangle(curX, badgeY, bossW, badgeH);
            drawBadgePill(g, bossBadgeBounds, bossText, BOSS_PURPLE, new Color(243, 232, 255), hovered);
            curX += bossW + 4;
        }

        // 3. Multi-Combat Badge ("Multi")
        if (zone.isMultiCombat())
        {
            String multiText = "Multi";
            int multiW = fm.stringWidth(multiText) + 12;
            multiBadgeBounds = new Rectangle(curX, badgeY, multiW, badgeH);
            drawBadgePill(g, multiBadgeBounds, multiText, MULTI_AMBER, new Color(254, 243, 199), hovered);
            curX += multiW + 4;
        }

        // 4. Wilderness Badge ("Wildy Lv.X")
        if (zone.getWildernessLevel() > 0)
        {
            String wildyText = "Wildy Lv." + zone.getWildernessLevel();
            int wildyW = fm.stringWidth(wildyText) + 12;
            wildyBadgeBounds = new Rectangle(curX, badgeY, wildyW, badgeH);
            drawBadgePill(g, wildyBadgeBounds, wildyText, WILDY_RED, new Color(254, 226, 226), hovered);
            curX += wildyW + 4;
        }

        return RenderedZoneBox.builder()
            .entry(entry)
            .screenBounds(rect)
            .spawnBadgeBounds(spawnBadgeBounds)
            .multiBadgeBounds(multiBadgeBounds)
            .wildyBadgeBounds(wildyBadgeBounds)
            .bossBadgeBounds(bossBadgeBounds)
            .entranceBadgeBounds(null)
            .themeColor(theme)
            .glowColor(glow)
            .isDungeonEntrance(false)
            .entrancePoint(null)
            .dungeonName(null)
            .isFocused(focused)
            .build();
    }

    private void drawBadgePill(Graphics2D g, Rectangle bounds, String text, Color borderColor, Color textColor, boolean hovered)
    {
        // Background
        g.setColor(BADGE_BG);
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);

        // Border
        g.setColor(new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), hovered ? 255 : 210));
        g.setStroke(new BasicStroke(hovered ? 1.5f : 1.0f));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);

        // Text
        FontMetrics fm = g.getFontMetrics();
        int tx = bounds.x + (bounds.width - fm.stringWidth(text)) / 2;
        int ty = bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();

        g.setColor(textColor);
        g.drawString(text, tx, ty);
    }

    /**
     * Renders the rich monster tooltip with combat info, stats, weaknesses, slayer requirements,
     * and top rare drops.
     */
    private void renderMonsterHoverTooltip(Graphics2D g, Monster monster, MonsterSpawnZone zone, RenderedZoneBox box, int startX, int startY)
    {
        if (monster == null && zone == null)
        {
            return;
        }

        Font boldFont = FontManager.getRunescapeBoldFont();
        Font smallFont = FontManager.getRunescapeSmallFont();
        FontMetrics fmBold = g.getFontMetrics(boldFont);
        FontMetrics fmSmall = g.getFontMetrics(smallFont);

        String monsterName = monster != null ? monster.getName() : "Monster Spawn";
        int combatLevel = monster != null ? monster.getCombatLevel() : 0;
        String titleStr = monsterName + (combatLevel > 0 ? " (Level " + combatLevel + ")" : "");

        String locName;
        if (box != null && box.isDungeonEntrance())
        {
            boolean approx = box.getEntry() != null && box.getEntry().entranceApproximate;
            locName = (approx ? "[Dungeon Entrance ~ approx] " : "[Dungeon Entrance] ")
                + (box.getDungeonName() != null ? box.getDungeonName() : "Dungeon");
        }
        else
        {
            locName = zone.getLocationName() != null ? zone.getLocationName() : (zone.getZoneName() != null ? zone.getZoneName() : "Spawn Location");
        }

        String categoryStr = (monster != null && monster.getCategory() != null) ? monster.getCategory() : "Standard";
        String memberStr = (monster != null && monster.isMembers()) ? "P2P" : "F2P";
        String subtitleStr = locName + " | " + categoryStr + " (" + memberStr + ")";

        // Calculate card dimensions
        int maxTextWidth = Math.max(fmBold.stringWidth(titleStr), fmSmall.stringWidth(subtitleStr));

        // Stats rows
        String hpStr = (monster != null && monster.getHitpoints() > 0) ? "HP: " + monster.getHitpoints() : null;
        String atkStr = (monster != null && monster.getAttackType() != null)
            ? "Style: " + monster.getAttackType() + (monster.getMaxHit() > 0 ? " (Max: " + monster.getMaxHit() + ")" : "")
            : null;
        String weakStr = (monster != null && monster.getWeakness() != null && !monster.getWeakness().isEmpty())
            ? "Weakness: " + monster.getWeakness()
            : null;
        String slayerStr = (monster != null && monster.getSlayerLevel() > 1)
            ? "Slayer Req: Lv." + monster.getSlayerLevel()
            : null;
        String questStr = (monster != null && monster.hasQuestRequirement())
            ? "Quest Req: " + monster.getQuestRequirement().trim()
            : null;

        if (hpStr != null && atkStr != null)
        {
            maxTextWidth = Math.max(maxTextWidth, fmSmall.stringWidth(hpStr) + fmSmall.stringWidth(atkStr) + 16);
        }
        else if (hpStr != null)
        {
            maxTextWidth = Math.max(maxTextWidth, fmSmall.stringWidth(hpStr));
        }
        else if (atkStr != null)
        {
            maxTextWidth = Math.max(maxTextWidth, fmSmall.stringWidth(atkStr));
        }

        if (weakStr != null) maxTextWidth = Math.max(maxTextWidth, fmSmall.stringWidth(weakStr));
        if (slayerStr != null) maxTextWidth = Math.max(maxTextWidth, fmSmall.stringWidth(slayerStr));
        if (questStr != null) maxTextWidth = Math.max(maxTextWidth, fmSmall.stringWidth(questStr));

        // Zone stats
        String zoneStatsStr = null;
        if (zone != null)
        {
            zoneStatsStr = "Spawns: " + (zone.getSpawnCount() > 0 ? zone.getSpawnCount() : 1) + "  |  " + (zone.isMultiCombat() ? "Multi-Combat" : "Single-Combat");
            if (zone.getWildernessLevel() > 0)
            {
                zoneStatsStr += "  |  Wildy Lv." + zone.getWildernessLevel();
            }
            maxTextWidth = Math.max(maxTextWidth, fmSmall.stringWidth(zoneStatsStr));
        }

        // Drops list (top 4 rare / notable drops sorted from rarest to least rare)
        List<MonsterDrop> displayDrops = new ArrayList<>();
        if (monster != null && monster.getDrops() != null && !monster.getDrops().isEmpty())
        {
            List<MonsterDrop> candidates = new ArrayList<>();
            for (MonsterDrop drop : monster.getDrops())
            {
                if (drop.getName() == null) continue;
                String name = drop.getName().trim().toLowerCase(java.util.Locale.ROOT);
                if (name.equalsIgnoreCase("bones") || name.equalsIgnoreCase("big bones") || name.equalsIgnoreCase("coins")
                    || name.equalsIgnoreCase("ashes") || name.equalsIgnoreCase("vial of water") || name.equalsIgnoreCase("feather"))
                {
                    continue;
                }
                // Exclude 100% / always drops unless monster has no other drops
                if (drop.getRarity() >= 1.0)
                {
                    continue;
                }
                candidates.add(drop);
            }

            if (candidates.isEmpty())
            {
                for (MonsterDrop drop : monster.getDrops())
                {
                    if (drop.getName() != null && !drop.getName().equalsIgnoreCase("Coins"))
                    {
                        candidates.add(drop);
                    }
                }
            }

            // Sort by rarity ASCENDING (smallest probability first = Rarest first!)
            candidates.sort(Comparator.comparingDouble(MonsterDrop::getRarity));

            for (int i = 0; i < Math.min(candidates.size(), 4); i++)
            {
                displayDrops.add(candidates.get(i));
            }
        }

        if (!displayDrops.isEmpty())
        {
            maxTextWidth = Math.max(maxTextWidth, fmBold.stringWidth("Top Rare Drops:"));
            for (MonsterDrop drop : displayDrops)
            {
                String rarityStr = (drop.getQuantity() != null && !drop.getQuantity().equals("1") ? "x" + drop.getQuantity() + " " : "")
                    + "(" + RarityFormat.perKill(drop) + ")";
                int dropLineWidth = fmSmall.stringWidth("- " + drop.getName()) + fmSmall.stringWidth(rarityStr) + 28;
                maxTextWidth = Math.max(maxTextWidth, dropLineWidth);
            }
        }

        String footerHint = (box != null && box.isDungeonEntrance())
            ? "Left-Click: Focus & Center on Dungeon Entrance"
            : "Left-Click: Focus & Center on Spawn Zone";
        int clickHintWidth = fmSmall.stringWidth(footerHint);
        maxTextWidth = Math.max(maxTextWidth, clickHintWidth);

        int cardWidth = Math.max(260, maxTextWidth + 24);

        // Height calculation based on active content lines
        int cardHeight = 44; // Header title + subtitle + divider
        if (hpStr != null || atkStr != null) cardHeight += 15;
        if (weakStr != null) cardHeight += 15;
        if (slayerStr != null) cardHeight += 15;
        if (questStr != null) cardHeight += 15;
        if (zoneStatsStr != null) cardHeight += 16;
        if (!displayDrops.isEmpty())
        {
            cardHeight += 24 + (displayDrops.size() * 14); // Drops header + rows
        }
        cardHeight += 22; // Footer hint

        // Clamp inside canvas bounds
        if (startY + cardHeight > client.getCanvasHeight() - 10)
        {
            startY = Math.max(10, startY - cardHeight - 44);
        }
        if (startX + cardWidth > client.getCanvasWidth() - 10)
        {
            startX = Math.max(10, client.getCanvasWidth() - cardWidth - 10);
        }

        // 1. Tooltip Background & Golden Glass Border
        g.setColor(BG_DARK);
        g.fillRoundRect(startX, startY, cardWidth, cardHeight, 8, 8);
        g.setColor(BORDER_GOLD);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(startX, startY, cardWidth, cardHeight, 8, 8);

        int curY = startY + 18;

        // 2. Monster Title & Subtitle
        g.setFont(boldFont);
        g.setColor(TITLE_GOLD);
        g.drawString(titleStr, startX + 12, curY);

        curY += 15;
        g.setFont(smallFont);
        g.setColor(TEXT_MUTED);
        g.drawString(subtitleStr, startX + 12, curY);

        curY += 6;
        g.setColor(new Color(60, 60, 60));
        g.drawLine(startX + 10, curY, startX + cardWidth - 10, curY);
        curY += 14;

        // 3. Combat Stats Grid (HP & Attack on row 1)
        if (hpStr != null || atkStr != null)
        {
            int colX = startX + 12;
            if (hpStr != null)
            {
                g.setColor(new Color(248, 113, 113));
                g.drawString(hpStr, colX, curY);
                colX += fmSmall.stringWidth(hpStr) + 16;
            }
            if (atkStr != null)
            {
                g.setColor(new Color(251, 191, 36));
                g.drawString(atkStr, colX, curY);
            }
            curY += 15;
        }

        // 4. Weakness Row (Clean own line)
        if (weakStr != null)
        {
            g.setColor(new Color(56, 189, 248));
            g.drawString(weakStr, startX + 12, curY);
            curY += 15;
        }

        // 5. Slayer Requirement Row (Clean own line below Weakness)
        if (slayerStr != null)
        {
            g.setColor(new Color(52, 211, 153));
            g.drawString(slayerStr, startX + 12, curY);
            curY += 15;
        }

        // 6. Quest Requirement Row (Clean own line if present)
        if (questStr != null)
        {
            g.setColor(new Color(195, 140, 255));
            g.drawString(questStr, startX + 12, curY);
            curY += 15;
        }

        // 7. Zone Info Line
        if (zoneStatsStr != null)
        {
            g.setColor(new Color(209, 213, 219));
            g.drawString(zoneStatsStr, startX + 12, curY);
            curY += 16;
        }

        // 8. Drops Section
        if (!displayDrops.isEmpty())
        {
            g.setColor(new Color(60, 60, 60));
            g.drawLine(startX + 10, curY - 4, startX + cardWidth - 10, curY - 4);
            curY += 10;

            g.setFont(boldFont);
            g.setColor(new Color(253, 224, 71));
            g.drawString("Top Rare Drops:", startX + 12, curY);
            curY += 14;

            g.setFont(smallFont);
            for (MonsterDrop drop : displayDrops)
            {
                g.setColor(DROP_PURPLE);
                g.drawString("- " + drop.getName(), startX + 14, curY);

                String rarityStr = (drop.getQuantity() != null && !drop.getQuantity().equals("1") ? "x" + drop.getQuantity() + " " : "")
                    + "(" + RarityFormat.perKill(drop) + ")";
                g.setColor(DROP_GOLD);
                int rW = fmSmall.stringWidth(rarityStr);
                g.drawString(rarityStr, startX + cardWidth - 12 - rW, curY);

                curY += 14;
            }
            curY += 2;
        }

        // 9. Footer Hint
        curY = startY + cardHeight - 6;
        g.setFont(smallFont);
        g.setColor(new Color(130, 130, 130));
        g.drawString(footerHint, startX + 12, curY);
    }

    private boolean isMouseOverBadges(RenderedZoneBox box, int mx, int my)
    {
        if (box == null) return false;
        if (box.getEntranceBadgeBounds() != null && box.getEntranceBadgeBounds().contains(mx, my)) return true;
        if (box.getSpawnBadgeBounds() != null && box.getSpawnBadgeBounds().contains(mx, my)) return true;
        if (box.getMultiBadgeBounds() != null && box.getMultiBadgeBounds().contains(mx, my)) return true;
        if (box.getWildyBadgeBounds() != null && box.getWildyBadgeBounds().contains(mx, my)) return true;
        if (box.getBossBadgeBounds() != null && box.getBossBadgeBounds().contains(mx, my)) return true;
        return false;
    }

    /**
     * Fills {@link MonsterZoneEntry}'s per-entry derived cache once. Safe to call every frame - it is
     * O(1) once ready. Inputs are immutable after entry construction, so the cache never needs
     * invalidating. Callers must have already null-checked {@code e.zone}.
     */
    private void ensureDerived(MonsterZoneEntry e)
    {
        if (e == null || e.derivedReady || e.zone == null)
        {
            return;
        }
        MonsterSpawnZone zone = e.zone;
        e.underground = isDungeonOrUnderground(zone);
        e.surfaceEntrance = resolveSurfaceEntrance(zone);
        e.entranceApproximate = resolveEntranceApproximate(zone);
        e.dungeonName = resolveDungeonName(zone);
        Color theme = e.customColor != null ? e.customColor : getThemeColor(e.monster, zone);
        e.themeColor = theme;
        e.glowColor = getGlowColor(theme);
        e.derivedReady = true;
    }

    private Color getThemeColor(Monster monster, MonsterSpawnZone zone)
    {
        if (zone.getWildernessLevel() > 0)
        {
            return WILDY_RED;
        }
        if (zone.isMultiCombat())
        {
            return MULTI_AMBER;
        }
        if (monster != null)
        {
            if ("Boss".equalsIgnoreCase(monster.getCategory()))
            {
                return BOSS_PURPLE;
            }
            if (monster.getSlayerLevel() > 1 || "Slayer".equalsIgnoreCase(monster.getCategory()))
            {
                return SLAYER_GREEN;
            }
        }
        return STANDARD_CYAN;
    }

    private Color getGlowColor(Color theme)
    {
        if (theme == WILDY_RED) return GLOW_WILDY;
        if (theme == MULTI_AMBER) return GLOW_MULTI;
        if (theme == BOSS_PURPLE) return GLOW_BOSS;
        if (theme == SLAYER_GREEN) return GLOW_SLAYER;
        return GLOW_DEFAULT;
    }

    public void setLastWorldMapOpen(boolean open)
    {
        this.lastWorldMapOpen = open;
    }

    @Override
    public MouseEvent mousePressed(MouseEvent e)
    {
        if (!lastWorldMapOpen || !isZonesVisible() || renderedBoxesSnapshot.isEmpty())
        {
            mousePressPoint = null;
            return e;
        }

        if (e.getButton() == MouseEvent.BUTTON1)
        {
            mousePressPoint = e.getPoint();
        }
        return e;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent e)
    {
        List<RenderedZoneBox> boxes = renderedBoxesSnapshot;
        if (!lastWorldMapOpen || !isZonesVisible() || boxes.isEmpty())
        {
            mousePressPoint = null;
            return e;
        }

        if (e.getButton() == MouseEvent.BUTTON1 && mousePressPoint != null)
        {
            java.awt.Point releasePoint = e.getPoint();
            // Verify click did not drag significantly (within 6px radius)
            if (mousePressPoint.distanceSq(releasePoint) <= 36)
            {
                for (RenderedZoneBox box : boxes)
                {
                    boolean isEntranceHit = false;
                    // Reuse the screen point captured at render time - don't re-project off the
                    // client thread (D21).
                    java.awt.Point screenPt = box.getEntranceScreen();
                    if (box.isDungeonEntrance() && screenPt != null)
                    {
                        double distSq = releasePoint.distanceSq(screenPt.getX(), screenPt.getY());
                        if (distSq <= 576) // Generous 24px radius portal hit-box
                        {
                            isEntranceHit = true;
                        }
                    }

                    if (isEntranceHit || box.getScreenBounds().contains(releasePoint) || isMouseOverBadges(box, releasePoint.x, releasePoint.y))
                    {
                        Monster monster = box.getEntry().getMonster();
                        MonsterSpawnZone zone = box.getEntry().getZone();

                        // Invoke callbacks to open the monster/dungeon in the side panel smoothly
                        if (onZoneClickListener != null)
                        {
                            onZoneClickListener.accept(monster, zone);
                        }
                        if (onZoneSelectionCallback != null)
                        {
                            onZoneSelectionCallback.accept(zone);
                        }
                        if (onMonsterSelectionCallback != null && monster != null)
                        {
                            onMonsterSelectionCallback.accept(monster);
                        }

                        // Center map on zone or trigger beacon
                        if (box.isDungeonEntrance() && box.getEntrancePoint() != null)
                        {
                            if (client.getWorldMap() != null)
                            {
                                client.getWorldMap().setWorldMapPositionTarget(box.getEntrancePoint());
                            }
                            if (beaconOverlay != null)
                            {
                                String dungeonName = box.getDungeonName() != null ? box.getDungeonName() : "Dungeon";
                                String monsterName = monster != null ? monster.getName() : "Monster";
                                // A leading "~ " tells WorldMapFocusBeaconOverlay to draw the soft
                                // "approximate area" pulse instead of a pinpoint core - our dungeon
                                // entrance coords for these zones are estimates, not verified pins.
                                String approxPrefix = box.getEntry().entranceApproximate ? "~ " : "";
                                beaconOverlay.triggerBeacon(box.getEntrancePoint(),
                                    approxPrefix + "[Dungeon Entrance] " + dungeonName + " (" + monsterName + " inside)");
                            }
                            // Do NOT consume click on dungeon entrance so the underlying dungeon entrance map element is clicked!
                        }
                        else if (zone != null)
                        {
                            WorldPoint focusPt = zone.getZoneCenter() != null ? zone.getZoneCenter() : zone.getEffectiveFocusPoint();
                            if (focusPt != null)
                            {
                                if (client.getWorldMap() != null)
                                {
                                    client.getWorldMap().setWorldMapPositionTarget(focusPt);
                                }
                                if (beaconOverlay != null)
                                {
                                    String label = (monster != null ? monster.getName() : "Monster") + " Zone (" + zone.getSpawnCount() + "x)";
                                    beaconOverlay.triggerBeacon(focusPt, label);
                                }
                            }
                            // We already recentred the map above - consume so the native world map
                            // doesn't also pan to the raw clicked pixel in the same gesture (D22).
                            e.consume();
                        }

                        mousePressPoint = null;
                        return e;
                    }
                }
            }
            mousePressPoint = null;
        }
        return e;
    }

    @Override public MouseEvent mouseClicked(MouseEvent e) { return e; }
    @Override public MouseEvent mouseEntered(MouseEvent e) { return e; }
    @Override public MouseEvent mouseExited(MouseEvent e) { return e; }
    @Override public MouseEvent mouseDragged(MouseEvent e) { return e; }
    @Override public MouseEvent mouseMoved(MouseEvent e) { return e; }
}
