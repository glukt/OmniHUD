package com.osrscopilot.map;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.data.DungeonEntranceDatabase;
import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterDrop;
import com.osrscopilot.data.model.MonsterSpawnZone;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.ShopItem;
import com.osrscopilot.data.model.TownNode;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Compact, non-intrusive Enhanced Map Key overlay on the World Map offering:
 * - Sleek Quick Search bar with Top-6 Multi-Result Floating Dropdown list:
 *   Instant typing & Enter / Up / Down arrow navigation to find and snap to shops, items, or monsters with a beacon!
 * - Complete keyboard lock while searching to prevent accidental client hotkey triggers.
 * - Towns: ON / OFF
 * - Shops: ON / OFF
 * - Monsters: ON / OFF
 * - Clear Highlights
 * - Master Spreadsheet Popout
 */
@Slf4j
@Singleton
public class WorldMapLegendOverlay extends Overlay implements MouseListener, KeyListener
{
    public static final Color BG_COLOR = new Color(18, 18, 18, 252);
    public static final Color DROPDOWN_BG = new Color(15, 15, 15, 255);
    private static final Color BORDER_COLOR = new Color(255, 185, 45, 200);
    private static final Color TITLE_COLOR = new Color(255, 152, 31);

    private static final Color SEARCH_BG = new Color(12, 12, 15, 252);
    private static final Color SEARCH_BORDER_FOCUSED = new Color(255, 185, 45, 240);
    private static final Color SEARCH_BORDER_UNFOCUSED = new Color(75, 75, 85, 200);
    private static final Color SEARCH_TEXT_COLOR = new Color(255, 255, 255);
    private static final Color SEARCH_HINT_COLOR = new Color(150, 150, 155);
    private static final Color SEARCH_PREVIEW_COLOR = new Color(253, 224, 71);

    private static final Color BTN_ON_BG = new Color(20, 80, 45, 230);
    private static final Color BTN_ON_BORDER = new Color(16, 185, 129);
    private static final Color BTN_OFF_BG = new Color(55, 25, 25, 230);
    private static final Color BTN_OFF_BORDER = new Color(239, 68, 68);

    private static final Color BTN_CLEAR_BG = new Color(45, 20, 20, 230);
    private static final Color BTN_CLEAR_BORDER = new Color(220, 38, 38, 200);

    private static final Color BTN_GOLD_BG = new Color(48, 36, 16, 235);
    private static final Color BTN_GOLD_BORDER = new Color(245, 158, 11);

    public static class SearchResult
    {
        public enum MatchType { SHOP, MONSTER, ITEM_SHOP, DROP_MONSTER, TOWN }

        private final MatchType type;
        private final String title;
        private final String subtitle;
        private final WorldPoint targetPoint;
        private final Shop shop;
        private final Monster monster;
        private final MonsterSpawnZone zone;
        private final TownNode town;
        private final int score;

        public SearchResult(MatchType type, String title, String subtitle, WorldPoint targetPoint, Shop shop, Monster monster, MonsterSpawnZone zone, TownNode town)
        {
            this(type, title, subtitle, targetPoint, shop, monster, zone, town, 0);
        }

        public SearchResult(MatchType type, String title, String subtitle, WorldPoint targetPoint, Shop shop, Monster monster, MonsterSpawnZone zone, TownNode town, int score)
        {
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.targetPoint = targetPoint;
            this.shop = shop;
            this.monster = monster;
            this.zone = zone;
            this.town = town;
            this.score = score;
        }

        public MatchType getType() { return type; }
        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }
        public WorldPoint getTargetPoint() { return targetPoint; }
        public Shop getShop() { return shop; }
        public Monster getMonster() { return monster; }
        public MonsterSpawnZone getZone() { return zone; }
        public TownNode getTown() { return town; }
        public int getScore() { return score; }
    }

    private final Client client;
    private final OsrsCopilotConfig config;
    private final ConfigManager configManager;
    private final WorldMapMarkerManager markerManager;
    private final WorldMapMonsterZoneOverlay monsterZoneOverlay;
    private final WorldMapFocusBeaconOverlay beaconOverlay;
    private final ShopDatabase shopDatabase;
    private final MonsterDatabase monsterDatabase;
    private final DungeonEntranceDatabase dungeonEntranceDatabase;
    private final ClientThread clientThread;

    private Consumer<SearchResult> onSearchResultSelected = null;

    private final Rectangle legendPanelBounds = new Rectangle();
    private final Rectangle searchBoxBounds = new Rectangle();
    private final Rectangle searchClearBounds = new Rectangle();
    private final Rectangle townBtnBounds = new Rectangle();
    private final Rectangle shopBtnBounds = new Rectangle();
    private final Rectangle monsterBtnBounds = new Rectangle();
    private final Rectangle clearBtnBounds = new Rectangle();

    private final Rectangle dropdownBounds = new Rectangle();
    private final List<Rectangle> dropdownRowBounds = new ArrayList<>();
    private List<SearchResult> currentSearchResults = new ArrayList<>();

    @Getter
    @Setter
    private int selectedIndex = -1;

    @Getter
    private String searchQuery = "";

    @Getter
    private boolean searchFocused = false;

    private String statusMessage = null;
    private long statusMessageTime = 0L;

    private java.awt.Point mousePressPoint = null;
    private volatile boolean lastWorldMapOpen = false;

    // Registered on the shared KeyboardFocusManager only while the search box is focused - see
    // handleGlobalKeyEvent() below for why this is necessary in addition to the KeyManager registration.
    private boolean globalKeyDispatcherRegistered = false;
    private final KeyEventDispatcher globalKeyEventDispatcher = this::handleGlobalKeyEvent;

    @Inject
    public WorldMapLegendOverlay(
        Client client,
        OsrsCopilotConfig config,
        ConfigManager configManager,
        WorldMapMarkerManager markerManager,
        WorldMapMonsterZoneOverlay monsterZoneOverlay,
        WorldMapFocusBeaconOverlay beaconOverlay,
        ShopDatabase shopDatabase,
        MonsterDatabase monsterDatabase,
        DungeonEntranceDatabase dungeonEntranceDatabase,
        ClientThread clientThread)
    {
        this.client = client;
        this.config = config;
        this.configManager = configManager;
        this.markerManager = markerManager;
        this.monsterZoneOverlay = monsterZoneOverlay;
        this.beaconOverlay = beaconOverlay;
        this.shopDatabase = shopDatabase;
        this.monsterDatabase = monsterDatabase;
        this.dungeonEntranceDatabase = dungeonEntranceDatabase;
        this.clientThread = clientThread;

        setPosition(OverlayPosition.DYNAMIC);
        setPriority(PRIORITY_HIGHEST);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
    }

    public WorldMapLegendOverlay(
        Client client,
        OsrsCopilotConfig config,
        ConfigManager configManager,
        WorldMapMarkerManager markerManager,
        WorldMapMonsterZoneOverlay monsterZoneOverlay,
        WorldMapFocusBeaconOverlay beaconOverlay)
    {
        this(client, config, configManager, markerManager, monsterZoneOverlay, beaconOverlay, null, null, null, null);
    }

    public WorldMapLegendOverlay(
        Client client,
        OsrsCopilotConfig config,
        ConfigManager configManager,
        WorldMapMarkerManager markerManager)
    {
        this(client, config, configManager, markerManager, null, null, null, null, null, null);
    }

    public void setSearchQuery(String query)
    {
        this.searchQuery = query != null ? query : "";
        updateSearchResults();
    }

    public void setOnSearchResultSelected(Consumer<SearchResult> onSearchResultSelected)
    {
        this.onSearchResultSelected = onSearchResultSelected;
    }

    /**
     * Sets whether the quick search box is focused. In addition to the plain flag flip that Lombok's
     * generated setter used to do, this registers/unregisters {@link #globalKeyEventDispatcher} with
     * the shared {@link KeyboardFocusManager} so the search box reliably wins every keystroke while
     * focused - see {@link #handleGlobalKeyEvent(KeyEvent)} for the full rationale. Every internal
     * assignment to searchFocused goes through this method instead of touching the field directly, so
     * the dispatcher lifecycle always stays in sync with the actual focus state.
     */
    public void setSearchFocused(boolean searchFocused)
    {
        boolean wasFocused = this.searchFocused;
        this.searchFocused = searchFocused;

        if (searchFocused && !wasFocused)
        {
            registerGlobalKeyDispatcher();
        }
        else if (!searchFocused && wasFocused)
        {
            unregisterGlobalKeyDispatcher();
        }
    }

    private void registerGlobalKeyDispatcher()
    {
        if (globalKeyDispatcherRegistered)
        {
            return;
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(globalKeyEventDispatcher);
        globalKeyDispatcherRegistered = true;
    }

    private void unregisterGlobalKeyDispatcher()
    {
        if (!globalKeyDispatcherRegistered)
        {
            return;
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(globalKeyEventDispatcher);
        globalKeyDispatcherRegistered = false;
    }

    public Rectangle getLegendPanelBounds()
    {
        return legendPanelBounds;
    }

    public Rectangle getSearchBoxBounds()
    {
        return searchBoxBounds;
    }

    public Rectangle getSearchClearBounds()
    {
        return searchClearBounds;
    }

    public Rectangle getTownBtnBounds()
    {
        return townBtnBounds;
    }

    public Rectangle getShopBtnBounds()
    {
        return shopBtnBounds;
    }

    public Rectangle getMonsterBtnBounds()
    {
        return monsterBtnBounds;
    }

    public Rectangle getClearBtnBounds()
    {
        return clearBtnBounds;
    }

    public Rectangle getDropdownBounds()
    {
        return dropdownBounds;
    }

    public List<Rectangle> getDropdownRowBounds()
    {
        return Collections.unmodifiableList(dropdownRowBounds);
    }

    public List<SearchResult> getCurrentSearchResults()
    {
        return Collections.unmodifiableList(currentSearchResults);
    }

    private void updateSearchResults()
    {
        if (searchQuery == null || searchQuery.trim().isEmpty())
        {
            currentSearchResults = Collections.emptyList();
            selectedIndex = -1;
        }
        else
        {
            currentSearchResults = findMatches(searchQuery, 6);
            selectedIndex = -1;
        }
    }

    /**
     * Finds and ranks the top matching results (up to maxResults) across:
     * 1. Direct monster spawns / monster names
     * 2. Direct shops / vendor names
     * 3. Towns
     * 4. Items sold in shops
     * 5. Items dropped by monsters
     */
    public List<SearchResult> findMatches(String query, int maxResults)
    {
        if (query == null || query.trim().isEmpty())
        {
            return Collections.emptyList();
        }

        String q = query.trim().toLowerCase(Locale.ROOT);
        List<SearchResult> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1. Direct monster spawns / name search
        if (monsterDatabase != null && monsterDatabase.isLoaded())
        {
            List<Monster> monsters = monsterDatabase.searchMonstersByName(q);
            for (Monster m : monsters)
            {
                if (m.hasSpawnZones())
                {
                    for (MonsterSpawnZone zone : m.getSpawnZones())
                    {
                        String key = "mob:" + m.getId() + ":" + (zone.getLocationName() != null ? zone.getLocationName() : zone.getEffectiveFocusPoint());
                        if (seen.add(key))
                        {
                            int score = scoreMonsterMatch(m, q, zone);
                            if (score > 0)
                            {
                                WorldPoint targetPt = resolveMonsterZoneTarget(zone);
                                String subtitle = formatMonsterSubtitle(m, zone);
                                candidates.add(new SearchResult(SearchResult.MatchType.MONSTER, m.getName(), subtitle, targetPt, null, m, zone, null, score));
                            }
                        }
                    }
                }
                else
                {
                    String key = "mob:" + m.getId();
                    if (seen.add(key))
                    {
                        int score = scoreMonsterMatch(m, q, null);
                        if (score > 0)
                        {
                            String subtitle = m.getCombatLevel() > 0 ? "Combat Lv." + m.getCombatLevel() : (m.getCategory() != null ? m.getCategory() : "Monster");
                            candidates.add(new SearchResult(SearchResult.MatchType.MONSTER, m.getName(), subtitle, null, null, m, null, null, score));
                        }
                    }
                }
            }
        }

        // 2. Direct shop name search
        if (shopDatabase != null && shopDatabase.isLoaded())
        {
            for (Shop s : shopDatabase.getAllShops())
            {
                int score = scoreShopMatch(s, q);
                if (score > 0)
                {
                    String key = "shop:" + s.getId();
                    if (seen.add(key))
                    {
                        WorldPoint pt = shopDatabase.getAlignedShopLocation(s);
                        String subtitle = "Shop • " + s.getTown();
                        candidates.add(new SearchResult(SearchResult.MatchType.SHOP, s.getName(), subtitle, pt, s, null, null, null, score));
                    }
                }
            }

            // 3. Direct town name search
            for (TownNode town : shopDatabase.getAllTowns())
            {
                int score = scoreTownMatch(town, q);
                if (score > 0)
                {
                    String key = "town:" + town.getId();
                    if (seen.add(key))
                    {
                        String subtitle = "Town Hub" + (town.getDescription() != null && !town.getDescription().isEmpty() ? " • " + town.getDescription() : "");
                        candidates.add(new SearchResult(SearchResult.MatchType.TOWN, town.getName(), subtitle, town.getWorldPoint(), null, null, null, town, score));
                    }
                }
            }

            // 4. Item sold in shop search
            Set<Shop> shopsSelling = shopDatabase.searchShopsByItemName(q);
            for (Shop s : shopsSelling)
            {
                ShopItem matchingItem = findMatchingShopItem(s, q);
                if (matchingItem != null)
                {
                    int score = scoreItemShopMatch(matchingItem, s, q);
                    if (score > 0)
                    {
                        String key = "item_shop:" + s.getId() + ":" + matchingItem.getItemId();
                        if (seen.add(key))
                        {
                            WorldPoint pt = shopDatabase.getAlignedShopLocation(s);
                            String subtitle = formatItemShopSubtitle(matchingItem, s);
                            candidates.add(new SearchResult(SearchResult.MatchType.ITEM_SHOP, s.getName(), subtitle, pt, s, null, null, null, score));
                        }
                    }
                }
            }
        }

        // 5. Monster drops search
        if (monsterDatabase != null && monsterDatabase.isLoaded())
        {
            List<Monster> dropMonsters = monsterDatabase.searchMonstersByDropName(q);
            for (Monster m : dropMonsters)
            {
                MonsterDrop matchingDrop = findMatchingDrop(m, q);
                if (matchingDrop != null)
                {
                    int score = scoreDropMatch(matchingDrop, m, q);
                    if (score > 0)
                    {
                        if (m.hasSpawnZones())
                        {
                            for (MonsterSpawnZone zone : m.getSpawnZones())
                            {
                                String key = "drop:" + m.getId() + ":" + matchingDrop.getName().toLowerCase(Locale.ROOT) + ":" + (zone.getLocationName() != null ? zone.getLocationName() : zone.getEffectiveFocusPoint());
                                if (seen.add(key))
                                {
                                    WorldPoint targetPt = resolveMonsterZoneTarget(zone);
                                    String subtitle = formatDropSubtitle(matchingDrop, m, zone);
                                    candidates.add(new SearchResult(SearchResult.MatchType.DROP_MONSTER, m.getName(), subtitle, targetPt, null, m, zone, null, score));
                                }
                            }
                        }
                        else
                        {
                            String key = "drop:" + m.getId() + ":" + matchingDrop.getName().toLowerCase(Locale.ROOT);
                            if (seen.add(key))
                            {
                                String subtitle = formatDropSubtitle(matchingDrop, m, null);
                                candidates.add(new SearchResult(SearchResult.MatchType.DROP_MONSTER, m.getName(), subtitle, null, null, m, null, null, score));
                            }
                        }
                    }
                }
            }
        }

        // Sort by score descending, then prefer shorter title, then alphabetical
        candidates.sort((a, b) -> {
            int cmp = Integer.compare(b.getScore(), a.getScore());
            if (cmp != 0) return cmp;

            int lenCmp = Integer.compare(a.getTitle().length(), b.getTitle().length());
            if (lenCmp != 0) return lenCmp;

            return a.getTitle().compareToIgnoreCase(b.getTitle());
        });

        if (candidates.size() <= maxResults)
        {
            return candidates;
        }
        return new ArrayList<>(candidates.subList(0, maxResults));
    }

    /**
     * Finds the best matching Shop, Monster, Item in Shop, Monster Drop, or Town Hub.
     */
    public SearchResult findBestMatch(String query)
    {
        List<SearchResult> matches = findMatches(query, 1);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private ShopItem findMatchingShopItem(Shop shop, String query)
    {
        if (shop.getItems() == null) return null;
        ShopItem best = null;
        int bestScore = 0;
        for (ShopItem item : shop.getItems())
        {
            if (item.getName() == null) continue;
            int score = scoreText(item.getName(), query);
            if (score > bestScore)
            {
                bestScore = score;
                best = item;
            }
        }
        return best;
    }

    private MonsterDrop findMatchingDrop(Monster monster, String query)
    {
        if (monster.getDrops() == null) return null;
        MonsterDrop best = null;
        int bestScore = 0;
        for (MonsterDrop drop : monster.getDrops())
        {
            if (drop.getName() == null) continue;
            int score = scoreText(drop.getName(), query);
            if (score > bestScore)
            {
                bestScore = score;
                best = drop;
            }
        }
        return best;
    }

    private int scoreText(String text, String query)
    {
        if (text == null || query == null) return 0;
        String t = text.toLowerCase(Locale.ROOT).trim();
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (t.equals(q)) return 1000;
        if (t.startsWith(q)) return 700;
        if (t.contains(q)) return 400;

        String[] qTokens = q.split("[\\s\\-_/(),.]+");
        int matchCount = 0;
        boolean allMatched = true;
        for (String qt : qTokens)
        {
            if (qt.isEmpty()) continue;
            if (t.contains(qt))
            {
                matchCount += 100;
            }
            else
            {
                allMatched = false;
            }
        }
        if (allMatched && matchCount > 0)
        {
            return 300 + matchCount;
        }
        return matchCount;
    }

    private int scoreMonsterMatch(Monster m, String query, MonsterSpawnZone zone)
    {
        int textScore = scoreText(m.getName(), query);
        if (textScore == 0) return 0;
        int score = textScore + 300;
        if (zone != null && zone.getSpawnCount() > 0) score += Math.min(20, zone.getSpawnCount());
        return score;
    }

    private int scoreShopMatch(Shop s, String query)
    {
        int textScore = scoreText(s.getName(), query);
        if (textScore == 0) return 0;
        return textScore + 300;
    }

    private int scoreTownMatch(TownNode town, String query)
    {
        int textScore = scoreText(town.getName(), query);
        if (textScore == 0) return 0;
        return textScore + 300;
    }

    private int scoreItemShopMatch(ShopItem item, Shop shop, String query)
    {
        int textScore = scoreText(item.getName(), query);
        if (textScore == 0) return 0;
        int score = textScore + 250;
        if (item.getPrice() > 0) score += 5;
        return score;
    }

    private int scoreDropMatch(MonsterDrop drop, Monster m, String query)
    {
        int textScore = scoreText(drop.getName(), query);
        if (textScore == 0) return 0;
        return textScore + 200;
    }

    private String formatMonsterSubtitle(Monster m, MonsterSpawnZone zone)
    {
        if (zone != null && zone.getLocationName() != null && !zone.getLocationName().isEmpty())
        {
            return "Combat Lv." + m.getCombatLevel() + " • " + zone.getLocationName() + (zone.getSpawnCount() > 0 ? " (x" + zone.getSpawnCount() + ")" : "");
        }
        if (m.getCombatLevel() > 0)
        {
            return "Combat Lv." + m.getCombatLevel() + (m.getCategory() != null ? " • " + m.getCategory() : "");
        }
        return m.getCategory() != null ? m.getCategory() : "Monster";
    }

    private String formatItemShopSubtitle(ShopItem item, Shop shop)
    {
        String pricePart = (item != null && item.getPrice() > 0) ? "Sells for " + item.getPrice() + "gp • " : (item != null ? "Sells \"" + item.getName() + "\" • " : "Sells item • ");
        String townPart = (shop.getTown() != null && !shop.getTown().isEmpty()) ? shop.getTown() : "Shop";
        String shopPart = " (" + shop.getName() + ")";
        return pricePart + townPart + shopPart;
    }

    private String formatDropSubtitle(MonsterDrop drop, Monster m, MonsterSpawnZone zone)
    {
        String dropName = drop != null ? drop.getName() : "Item";
        String rarity = (drop != null && drop.getRarityFraction() != null && !drop.getRarityFraction().isEmpty()) ? " (" + drop.getRarityFraction() + ")" : "";
        String zonePart = (zone != null && zone.getLocationName() != null && !zone.getLocationName().isEmpty()) ? " • " + zone.getLocationName() + (zone.getSpawnCount() > 0 ? " (x" + zone.getSpawnCount() + ")" : "") : "";
        return "Drops " + dropName + rarity + " • Lv." + m.getCombatLevel() + zonePart;
    }

    private WorldPoint resolveMonsterZoneTarget(MonsterSpawnZone zone)
    {
        if (zone == null)
        {
            return null;
        }

        WorldPoint center = zone.getZoneCenter() != null ? zone.getZoneCenter() : zone.getEffectiveFocusPoint();
        if (zone.getSurfaceEntrance() != null)
        {
            return zone.getSurfaceEntrance();
        }

        if (dungeonEntranceDatabase != null && center != null && dungeonEntranceDatabase.isUndergroundOrDungeon(center))
        {
            DungeonEntranceDatabase.DungeonMapping mapping = dungeonEntranceDatabase.findDungeon(center);
            if (mapping != null && mapping.getSurfaceEntrance() != null)
            {
                return mapping.getSurfaceEntrance();
            }
        }

        return center;
    }

    /**
     * Executes quick search: finds best match and pans the World Map to that shop or monster spawn zone with a beacon!
     */
    public boolean executeSearch(String query)
    {
        if (query == null || query.trim().isEmpty())
        {
            return false;
        }

        SearchResult match = findBestMatch(query);
        if (match == null)
        {
            statusMessage = "No match found for \"" + query + "\"";
            statusMessageTime = System.currentTimeMillis();
            return false;
        }

        return executeSearchResult(match);
    }

    /**
     * Executes a specific SearchResult: pans map, focuses overlay, triggers beacon.
     */
    public boolean executeSearchResult(SearchResult match)
    {
        if (match == null)
        {
            return false;
        }

        statusMessage = "Snapped to: " + match.getTitle();
        statusMessageTime = System.currentTimeMillis();

        WorldPoint targetPt = match.getTargetPoint();
        String beaconLabel;

        if (match.getType() == SearchResult.MatchType.MONSTER || match.getType() == SearchResult.MatchType.DROP_MONSTER)
        {
            Monster monster = match.getMonster();
            MonsterSpawnZone zone = match.getZone();

            if (monsterZoneOverlay != null)
            {
                monsterZoneOverlay.setZonesVisible(true);
                if (zone != null)
                {
                    monsterZoneOverlay.setFocusedZone(monster, zone);
                }
                else if (monster != null)
                {
                    monsterZoneOverlay.setFocusedMonster(monster);
                }
            }

            beaconLabel = (monster != null ? monster.getName() : match.getTitle())
                + (zone != null && zone.getSpawnCount() > 0 ? " (x" + zone.getSpawnCount() + ")" : " Zone");
        }
        else if (match.getType() == SearchResult.MatchType.ITEM_SHOP)
        {
            Shop shop = match.getShop();
            beaconLabel = (shop != null ? shop.getName() : match.getTitle()) + " (" + match.getTitle() + ")";
        }
        else if (match.getType() == SearchResult.MatchType.SHOP)
        {
            Shop shop = match.getShop();
            beaconLabel = (shop != null ? shop.getName() + " (" + shop.getTown() + ")" : match.getTitle());
        }
        else if (match.getType() == SearchResult.MatchType.TOWN)
        {
            TownNode town = match.getTown();
            beaconLabel = (town != null ? town.getName() + " Hub" : match.getTitle());
        }
        else
        {
            beaconLabel = match.getTitle();
        }

        panMapAndTriggerBeacon(targetPt, beaconLabel);

        if (onSearchResultSelected != null)
        {
            onSearchResultSelected.accept(match);
        }

        return true;
    }

    private void panMapAndTriggerBeacon(WorldPoint targetPt, String label)
    {
        if (targetPt == null)
        {
            return;
        }

        Runnable action = () -> {
            if (client != null && client.getWorldMap() != null)
            {
                client.getWorldMap().setWorldMapPositionTarget(targetPt);
            }
            if (beaconOverlay != null)
            {
                beaconOverlay.triggerBeacon(targetPt, label);
            }
        };

        if (clientThread != null)
        {
            clientThread.invokeLater(action);
        }
        else
        {
            action.run();
        }
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        Widget worldMap = client != null ? client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER) : null;
        if (worldMap == null || worldMap.isHidden())
        {
            lastWorldMapOpen = false;
            legendPanelBounds.setBounds(0, 0, 0, 0);
            searchBoxBounds.setBounds(0, 0, 0, 0);
            searchClearBounds.setBounds(0, 0, 0, 0);
            townBtnBounds.setBounds(0, 0, 0, 0);
            shopBtnBounds.setBounds(0, 0, 0, 0);
            monsterBtnBounds.setBounds(0, 0, 0, 0);
            clearBtnBounds.setBounds(0, 0, 0, 0);
            dropdownBounds.setBounds(0, 0, 0, 0);
            dropdownRowBounds.clear();
            return null;
        }

        lastWorldMapOpen = true;
        Rectangle mapBounds = worldMap.getBounds();
        int panelW = 274;
        int panelH = 62;

        // Position at bottom-left, accounting for the native left map overview/key if open
        int startX = mapBounds.x + 12;
        Widget overviewWidget = client.getWidget(InterfaceID.Worldmap.OVERVIEW_CONTAINER);
        if (overviewWidget != null && !overviewWidget.isHidden())
        {
            startX = overviewWidget.getBounds().x + overviewWidget.getBounds().width + 12;
        }

        int startY = mapBounds.y + mapBounds.height - panelH - 42;
        Widget bottomBar = client.getWidget(InterfaceID.Worldmap.BOTTOM_GRAPHIC0);
        if (bottomBar != null && !bottomBar.isHidden())
        {
            startY = bottomBar.getBounds().y - panelH - 8;
        }
        if (startY < mapBounds.y + 10)
        {
            startY = mapBounds.y + 10;
        }

        legendPanelBounds.setBounds(startX, startY, panelW, panelH);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Panel Background
        g.setColor(BG_COLOR);
        g.fillRoundRect(startX, startY, panelW, panelH, 6, 6);
        g.setColor(BORDER_COLOR);
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(startX, startY, panelW, panelH, 6, 6);

        // =========================================================================
        // ROW 1: Quick Search Bar
        // =========================================================================
        int searchX = startX + 6;
        int searchY = startY + 6;
        int searchW = panelW - 12;
        int searchH = 22;
        searchBoxBounds.setBounds(searchX, searchY, searchW, searchH);

        // Search Box Background & Glowing Focus Border
        g.setColor(SEARCH_BG);
        g.fillRoundRect(searchX, searchY, searchW, searchH, 4, 4);

        g.setColor(searchFocused ? SEARCH_BORDER_FOCUSED : SEARCH_BORDER_UNFOCUSED);
        g.setStroke(new BasicStroke(searchFocused ? 1.4f : 1.0f));
        g.drawRoundRect(searchX, searchY, searchW, searchH, 4, 4);

        // Search Magnifying Icon
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();
        g.setColor(searchFocused ? TITLE_COLOR : SEARCH_HINT_COLOR);
        String searchIcon = "[o]";
        g.drawString(searchIcon, searchX + 6, searchY + 15);
        int textOffset = searchX + 22;

        // Search Text or Placeholder
        if (searchQuery.isEmpty())
        {
            if (statusMessage != null && (System.currentTimeMillis() - statusMessageTime < 3500))
            {
                g.setColor(SEARCH_PREVIEW_COLOR);
                g.drawString(statusMessage, textOffset, searchY + 15);
            }
            else
            {
                g.setColor(SEARCH_HINT_COLOR);
                g.drawString("Quick Search: Item, Shop, Monster... [Enter]", textOffset, searchY + 15);
            }
            searchClearBounds.setBounds(0, 0, 0, 0);
        }
        else
        {
            g.setColor(SEARCH_TEXT_COLOR);
            g.drawString(searchQuery, textOffset, searchY + 15);
            int typedW = fm.stringWidth(searchQuery);

            // Blinking cursor when focused
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0)
            {
                g.setColor(TITLE_COLOR);
                g.fillRect(textOffset + typedW + 1, searchY + 4, 2, 14);
            }

            // Live Match Preview hint if dropdown is not open or focused. currentSearchResults is
            // already kept in sync with searchQuery by updateSearchResults() on every keystroke, so
            // reuse its top hit instead of re-running the whole matcher every frame.
            if (!searchFocused)
            {
                SearchResult preview = currentSearchResults.isEmpty() ? null : currentSearchResults.get(0);
                if (preview != null)
                {
                    String previewHint = " [↵ " + preview.getTitle() + "]";
                    int maxAvailable = searchW - typedW - 55;
                    if (maxAvailable > 40)
                    {
                        g.setColor(SEARCH_PREVIEW_COLOR);
                        g.drawString(previewHint, textOffset + typedW + 6, searchY + 15);
                    }
                }
            }

            // Clear '✕' button on right of search box
            int clearW = 16;
            int clearX = searchX + searchW - clearW - 4;
            int clearY = searchY + 3;
            searchClearBounds.setBounds(clearX, clearY, clearW, 16);

            g.setColor(new Color(239, 68, 68, 180));
            g.drawString("✕", clearX + 4, searchY + 15);
        }

        // =========================================================================
        // ROW 2: Toggle Buttons (Towns, Shops, Bestiary, Clear)
        // =========================================================================
        int btnY = startY + 34;
        int btnH = 22;
        int curX = startX + 6;

        boolean townsOn = config != null && config.enableTownMarkers();
        townBtnBounds.setBounds(curX, btnY, 60, btnH);
        drawToggleButton(g, townBtnBounds, "Towns", townsOn);
        curX += 60 + 4;

        boolean shopsOn = config != null && config.showVendorIcons();
        shopBtnBounds.setBounds(curX, btnY, 60, btnH);
        drawToggleButton(g, shopBtnBounds, "Shops", shopsOn);
        curX += 60 + 4;

        boolean monstersOn = monsterZoneOverlay == null || monsterZoneOverlay.isZonesVisible();
        monsterBtnBounds.setBounds(curX, btnY, 74, btnH);
        drawToggleButton(g, monsterBtnBounds, "Bestiary", monstersOn);
        curX += 74 + 4;

        clearBtnBounds.setBounds(curX, btnY, 56, btnH);
        drawClearButton(g, clearBtnBounds, "Clear");

        // =========================================================================
        // FLOATING DROPDOWN LIST: Top 6 Matches (Rendered on top)
        // =========================================================================
        drawFloatingDropdown(g, startX, startY, panelW);

        return null;
    }

    private void drawFloatingDropdown(Graphics2D g, int startX, int startY, int panelW)
    {
        if (!searchFocused || searchQuery.trim().isEmpty() || currentSearchResults.isEmpty())
        {
            dropdownBounds.setBounds(0, 0, 0, 0);
            dropdownRowBounds.clear();
            return;
        }

        int searchX = startX + 6;
        int searchY = startY + 6;
        int searchW = panelW - 12;
        int searchH = 22;

        int ddX = searchX;
        int ddY = searchY + searchH + 4;
        int ddW = searchW;
        int rowH = 34;
        int ddH = currentSearchResults.size() * rowH + 6;

        dropdownBounds.setBounds(ddX, ddY, ddW, ddH);
        dropdownRowBounds.clear();

        // Outer Dropdown Background & Glowing Gold Border
        g.setColor(DROPDOWN_BG);
        g.fillRoundRect(ddX, ddY, ddW, ddH, 6, 6);
        g.setColor(new Color(255, 185, 45, 220));
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(ddX, ddY, ddW, ddH, 6, 6);

        for (int i = 0; i < currentSearchResults.size(); i++)
        {
            SearchResult result = currentSearchResults.get(i);
            Rectangle rowRect = new Rectangle(ddX + 3, ddY + 3 + i * rowH, ddW - 6, rowH);
            dropdownRowBounds.add(rowRect);

            boolean isSelected = (i == selectedIndex);

            // Highlight selected row
            if (isSelected)
            {
                g.setColor(new Color(55, 42, 16, 240));
                g.fillRoundRect(rowRect.x, rowRect.y, rowRect.width, rowRect.height, 4, 4);

                g.setColor(new Color(255, 185, 45, 200));
                g.setStroke(new BasicStroke(1.0f));
                g.drawRoundRect(rowRect.x, rowRect.y, rowRect.width, rowRect.height, 4, 4);

                // Left gold accent indicator bar
                g.setColor(new Color(255, 185, 45));
                g.fillRect(rowRect.x + 2, rowRect.y + 4, 3, rowRect.height - 8);
            }
            else
            {
                if (i > 0)
                {
                    // Row separator
                    g.setColor(new Color(45, 45, 55, 150));
                    g.drawLine(rowRect.x + 6, rowRect.y, rowRect.x + rowRect.width - 6, rowRect.y);
                }
            }

            // Draw Type Badge Pill
            drawCategoryBadge(g, rowRect.x + 8, rowRect.y + 8, result.getType());

            // Draw Title and Subtitle
            int textX = rowRect.x + 50;
            int maxTextW = rowRect.width - 105;

            g.setFont(FontManager.getRunescapeBoldFont());
            FontMetrics fmb = g.getFontMetrics();
            g.setColor(isSelected ? new Color(255, 230, 140) : Color.WHITE);
            String title = truncateText(result.getTitle(), fmb, maxTextW);
            g.drawString(title, textX, rowRect.y + 14);

            g.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics fms = g.getFontMetrics();
            g.setColor(isSelected ? new Color(240, 240, 200) : new Color(165, 165, 175));
            String subtitle = truncateText(result.getSubtitle(), fms, maxTextW);
            g.drawString(subtitle, textX, rowRect.y + 27);

            // Action cue on selected row
            if (isSelected)
            {
                g.setFont(FontManager.getRunescapeSmallFont());
                g.setColor(new Color(253, 224, 71));
                String enterCue = "[↵ Snap]";
                FontMetrics fmCue = g.getFontMetrics();
                g.drawString(enterCue, rowRect.x + rowRect.width - fmCue.stringWidth(enterCue) - 6, rowRect.y + 21);
            }
        }
    }

    private void drawCategoryBadge(Graphics2D g, int x, int y, SearchResult.MatchType type)
    {
        int badgeW = 36;
        int badgeH = 18;

        Color badgeBg;
        Color badgeBorder;
        String badgeText;

        switch (type)
        {
            case SHOP:
                badgeBg = new Color(70, 45, 15, 220);
                badgeBorder = new Color(245, 158, 11);
                badgeText = "SHOP";
                break;
            case ITEM_SHOP:
                badgeBg = new Color(20, 45, 75, 220);
                badgeBorder = new Color(59, 130, 246);
                badgeText = "ITEM";
                break;
            case MONSTER:
                badgeBg = new Color(75, 20, 20, 220);
                badgeBorder = new Color(239, 68, 68);
                badgeText = "MOB";
                break;
            case DROP_MONSTER:
                badgeBg = new Color(65, 20, 60, 220);
                badgeBorder = new Color(236, 72, 153);
                badgeText = "DROP";
                break;
            case TOWN:
                badgeBg = new Color(15, 60, 35, 220);
                badgeBorder = new Color(16, 185, 129);
                badgeText = "TOWN";
                break;
            default:
                badgeBg = new Color(40, 40, 50, 220);
                badgeBorder = new Color(150, 150, 160);
                badgeText = "INFO";
                break;
        }

        g.setColor(badgeBg);
        g.fillRoundRect(x, y, badgeW, badgeH, 4, 4);
        g.setColor(badgeBorder);
        g.setStroke(new BasicStroke(1.0f));
        g.drawRoundRect(x, y, badgeW, badgeH, 4, 4);

        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        int tx = x + (badgeW - fm.stringWidth(badgeText)) / 2;
        int ty = y + ((badgeH - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(badgeText, tx, ty);
    }

    private String truncateText(String text, FontMetrics fm, int maxW)
    {
        if (text == null || fm.stringWidth(text) <= maxW)
        {
            return text != null ? text : "";
        }
        String ellipsis = "...";
        int ellipsisW = fm.stringWidth(ellipsis);
        int avail = maxW - ellipsisW;
        if (avail <= 0) return ellipsis;

        int len = text.length();
        while (len > 0 && fm.stringWidth(text.substring(0, len)) > avail)
        {
            len--;
        }
        return text.substring(0, Math.max(0, len)) + ellipsis;
    }

    private void drawToggleButton(Graphics2D g, Rectangle rect, String label, boolean active)
    {
        g.setColor(active ? BTN_ON_BG : BTN_OFF_BG);
        g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 4, 4);

        g.setColor(active ? BTN_ON_BORDER : BTN_OFF_BORDER);
        g.setStroke(new BasicStroke(1.0f));
        g.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 4, 4);

        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(Color.WHITE);

        String text = label + ": " + (active ? "ON" : "OFF");
        FontMetrics fm = g.getFontMetrics();
        int tx = rect.x + (rect.width - fm.stringWidth(text)) / 2;
        int ty = rect.y + ((rect.height - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(text, tx, ty);
    }

    private void drawClearButton(Graphics2D g, Rectangle rect, String text)
    {
        g.setColor(BTN_CLEAR_BG);
        g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 4, 4);

        g.setColor(BTN_CLEAR_BORDER);
        g.setStroke(new BasicStroke(1.0f));
        g.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 4, 4);

        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(new Color(254, 202, 202));

        FontMetrics fm = g.getFontMetrics();
        int tx = rect.x + (rect.width - fm.stringWidth(text)) / 2;
        int ty = rect.y + ((rect.height - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(text, tx, ty);
    }

    private boolean handlePanelClick(java.awt.Point p, MouseEvent e)
    {
        if (e.isConsumed())
        {
            return true;
        }

        // 1. Search Clear '✕' Click
        if (!searchQuery.isEmpty() && searchClearBounds.contains(p))
        {
            setSearchQuery("");
            setSearchFocused(true);
            if (client != null && client.getCanvas() != null)
            {
                client.getCanvas().requestFocusInWindow();
            }
            e.consume();
            return true;
        }

        // 2. Search Box Click
        if (searchBoxBounds.contains(p))
        {
            setSearchFocused(true);
            if (client != null && client.getCanvas() != null)
            {
                client.getCanvas().requestFocusInWindow();
            }
            e.consume();
            return true;
        }

        // Clicked outside search box within panel
        setSearchFocused(false);
        selectedIndex = -1;

        // 3. Towns Toggle
        if (townBtnBounds.contains(p))
        {
            if (config != null && configManager != null)
            {
                boolean nextVal = !config.enableTownMarkers();
                configManager.setConfiguration("osrscopilot", "enableTownMarkers", nextVal);
            }
            if (markerManager != null)
            {
                markerManager.rebuildMarkers();
            }
            e.consume();
            return true;
        }

        // 4. Shops Toggle
        if (shopBtnBounds.contains(p))
        {
            if (config != null && configManager != null)
            {
                boolean nextVal = !config.showVendorIcons();
                configManager.setConfiguration("osrscopilot", "showVendorIcons", nextVal);
            }
            if (markerManager != null)
            {
                markerManager.rebuildMarkers();
            }
            e.consume();
            return true;
        }

        // 5. Bestiary (monster spawn-zone) Toggle
        if (monsterBtnBounds.contains(p))
        {
            if (monsterZoneOverlay != null)
            {
                boolean nextVal = !monsterZoneOverlay.isZonesVisible();
                monsterZoneOverlay.setZonesVisible(nextVal);
                if (configManager != null)
                {
                    configManager.setConfiguration("osrscopilot", "showMonsterZones", nextVal);
                }
            }
            e.consume();
            return true;
        }

        // 6. Clear Highlights
        if (clearBtnBounds.contains(p))
        {
            if (monsterZoneOverlay != null)
            {
                monsterZoneOverlay.clearActiveZones();
            }
            if (beaconOverlay != null)
            {
                beaconOverlay.clearBeacon();
            }
            setSearchQuery("");
            statusMessage = null;
            e.consume();
            return true;
        }

        // Clicked inside panel area, consume so it does not bleed into the map canvas
        e.consume();
        return true;
    }

    public void setLastWorldMapOpen(boolean open)
    {
        this.lastWorldMapOpen = open;
    }

    @Override
    public MouseEvent mousePressed(MouseEvent e)
    {
        if (!lastWorldMapOpen)
        {
            if (searchFocused)
            {
                setSearchFocused(false);
                selectedIndex = -1;
            }
            mousePressPoint = null;
            return e;
        }

        if (e.getButton() == MouseEvent.BUTTON1)
        {
            mousePressPoint = e.getPoint();

            // Check Dropdown click first
            if (searchFocused && dropdownBounds.contains(mousePressPoint))
            {
                e.consume();
                return e;
            }

            // Check Legend Panel click
            if (legendPanelBounds.contains(mousePressPoint))
            {
                if (searchBoxBounds.contains(mousePressPoint) || searchClearBounds.contains(mousePressPoint))
                {
                    setSearchFocused(true);
                    if (client != null && client.getCanvas() != null)
                    {
                        client.getCanvas().requestFocusInWindow();
                    }
                }
                e.consume();
                return e;
            }
            else
            {
                setSearchFocused(false);
                selectedIndex = -1;
            }
        }
        return e;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent e)
    {
        if (!lastWorldMapOpen)
        {
            if (searchFocused)
            {
                setSearchFocused(false);
                selectedIndex = -1;
            }
            mousePressPoint = null;
            return e;
        }

        if (e.getButton() == MouseEvent.BUTTON1 && mousePressPoint != null)
        {
            java.awt.Point p = e.getPoint();
            if (mousePressPoint.distanceSq(p) <= 36)
            {
                // 1. Check Dropdown row click
                if (searchFocused && (dropdownBounds.contains(p) || dropdownBounds.contains(mousePressPoint)))
                {
                    for (int i = 0; i < dropdownRowBounds.size(); i++)
                    {
                        if (dropdownRowBounds.get(i).contains(p) || dropdownRowBounds.get(i).contains(mousePressPoint))
                        {
                            if (i < currentSearchResults.size())
                            {
                                executeSearchResult(currentSearchResults.get(i));
                                setSearchFocused(false);
                                selectedIndex = -1;
                            }
                            e.consume();
                            mousePressPoint = null;
                            return e;
                        }
                    }
                    e.consume();
                    mousePressPoint = null;
                    return e;
                }

                // 2. Check Legend Panel click
                if (legendPanelBounds.contains(p) || legendPanelBounds.contains(mousePressPoint))
                {
                    handlePanelClick(p, e);
                    e.consume();
                    mousePressPoint = null;
                    return e;
                }
            }
            mousePressPoint = null;
        }
        return e;
    }

    @Override public MouseEvent mouseClicked(MouseEvent e) { return e; }
    @Override public MouseEvent mouseEntered(MouseEvent e) { return e; }
    @Override public MouseEvent mouseExited(MouseEvent e) { return e; }

    @Override
    public MouseEvent mouseDragged(MouseEvent e)
    {
        if (!lastWorldMapOpen)
        {
            mousePressPoint = null;
            return e;
        }

        if (mousePressPoint != null && (legendPanelBounds.contains(mousePressPoint) || (searchFocused && dropdownBounds.contains(mousePressPoint))))
        {
            e.consume();
            return e;
        }
        return e;
    }

    @Override
    public MouseEvent mouseMoved(MouseEvent e)
    {
        if (!lastWorldMapOpen)
        {
            return e;
        }

        if (searchFocused && !dropdownBounds.isEmpty() && dropdownBounds.contains(e.getPoint()))
        {
            for (int i = 0; i < dropdownRowBounds.size(); i++)
            {
                if (dropdownRowBounds.get(i).contains(e.getPoint()))
                {
                    selectedIndex = i;
                    return e;
                }
            }
        }
        return e;
    }

    // =========================================================================
    // KeyListener implementation for Quick Search & Hotkey Lock
    // =========================================================================

    /**
     * Global AWT-level key interception, registered on the shared {@link KeyboardFocusManager} only
     * while {@link #searchFocused} is true (see {@link #setSearchFocused(boolean)}).
     * <p>
     * RuneLite's own {@code KeyManager} dispatches key events to every plugin-registered
     * {@code net.runelite.client.input.KeyListener} in plain registration order (a
     * {@code CopyOnWriteArrayList}, appended-to as plugins start up) with no priority mechanism, and
     * stops at the first listener that calls {@link KeyEvent#consume()}. That means if any other
     * enabled plugin - unrelated to this one, and outside this codebase - happens to register its own
     * hotkey {@code KeyListener} earlier and unconditionally consumes a given key (its own hotkey,
     * regardless of what has focus), this overlay's {@link #keyPressed(KeyEvent)}/
     * {@link #keyTyped(KeyEvent)} below would never even be invoked for that key: consuming it here
     * does nothing to stop a plugin that already ran first. This is exactly what explained live
     * "sometimes I can't type certain letters" reports - which letters got eaten depended on which
     * other plugins happened to be enabled, and in what order they registered, not on anything wrong
     * with this class's own keyPressed/keyTyped consumption logic (which was, and remains, correct).
     * <p>
     * A {@link KeyEventDispatcher} added directly to the {@link KeyboardFocusManager} runs before ANY
     * component-level listener chain - the game canvas's own KeyListener chain, and therefore
     * RuneLite's KeyManager, included - so this guarantees the search box always gets first look at
     * every keystroke while it is focused, regardless of what else is installed or how it is ordered.
     * <p>
     * This is deliberately scoped as narrowly as possible: it only acts while {@code searchFocused} is
     * true, and even then only when the game canvas is the actual current AWT focus owner. If some
     * other real Swing component has taken focus instead (the sidebar's own item-search field, the
     * Master Spreadsheet popout dialog, etc.), {@code searchFocused} is stale - this lets that
     * component receive its own input normally instead of globally swallowing it, and resyncs the
     * stale flag back to false so the map's own search box stops rendering as focused too.
     * <p>
     * Public so it can double as the {@link KeyEventDispatcher} lambda target and be exercised
     * directly from unit tests without needing a real focused window.
     */
    public boolean handleGlobalKeyEvent(KeyEvent e)
    {
        if (!searchFocused || e.isConsumed())
        {
            return false;
        }

        if (client == null || client.getCanvas() == null)
        {
            return false;
        }

        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner != client.getCanvas())
        {
            setSearchFocused(false);
            return false;
        }

        switch (e.getID())
        {
            case KeyEvent.KEY_PRESSED:
                keyPressed(e);
                break;
            case KeyEvent.KEY_TYPED:
                keyTyped(e);
                break;
            case KeyEvent.KEY_RELEASED:
                keyReleased(e);
                break;
            default:
                break;
        }

        return e.isConsumed();
    }

    @Override
    public void keyTyped(KeyEvent e)
    {
        if (!searchFocused)
        {
            return;
        }

        char c = e.getKeyChar();
        if (c >= 32 && c != 127 && c != KeyEvent.CHAR_UNDEFINED)
        {
            if (searchQuery.length() < 60)
            {
                searchQuery += c;
                selectedIndex = -1;
                updateSearchResults();
            }
        }

        // ALWAYS consume typed events when searchFocused is true so typing letters/numbers/space/punctuation
        // never triggers client hotkeys, spellbook tab switching, camera movement, or chat toggles
        e.consume();
    }

    @Override
    public void keyPressed(KeyEvent e)
    {
        if (!searchFocused)
        {
            return;
        }

        int code = e.getKeyCode();
        if (code == KeyEvent.VK_ENTER)
        {
            if (selectedIndex >= 0 && selectedIndex < currentSearchResults.size())
            {
                executeSearchResult(currentSearchResults.get(selectedIndex));
                setSearchFocused(false);
                selectedIndex = -1;
            }
            else
            {
                if (executeSearch(searchQuery))
                {
                    setSearchFocused(false);
                    selectedIndex = -1;
                }
            }
            e.consume();
        }
        else if (code == KeyEvent.VK_ESCAPE)
        {
            setSearchFocused(false);
            selectedIndex = -1;
            e.consume();
        }
        else if (code == KeyEvent.VK_BACK_SPACE)
        {
            if (!searchQuery.isEmpty())
            {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                selectedIndex = -1;
                updateSearchResults();
            }
            e.consume();
        }
        else if (code == KeyEvent.VK_DELETE)
        {
            searchQuery = "";
            selectedIndex = -1;
            updateSearchResults();
            e.consume();
        }
        else if (code == KeyEvent.VK_UP)
        {
            if (!currentSearchResults.isEmpty())
            {
                if (selectedIndex <= 0)
                {
                    selectedIndex = currentSearchResults.size() - 1;
                }
                else
                {
                    selectedIndex--;
                }
            }
            e.consume();
        }
        else if (code == KeyEvent.VK_DOWN)
        {
            if (!currentSearchResults.isEmpty())
            {
                if (selectedIndex < 0 || selectedIndex >= currentSearchResults.size() - 1)
                {
                    selectedIndex = 0;
                }
                else
                {
                    selectedIndex++;
                }
            }
            e.consume();
        }
        else
        {
            // ALWAYS consume all other key press events (WASD camera, spellbook F-keys, chat, tab, space, letters)
            // so client canvas hotkeys NEVER trigger or steal focus while search is focused
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e)
    {
        if (searchFocused)
        {
            e.consume();
        }
    }

    @Override
    public void focusLost()
    {
        setSearchFocused(false);
        selectedIndex = -1;
    }
}
