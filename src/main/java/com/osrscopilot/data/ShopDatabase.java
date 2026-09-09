package com.osrscopilot.data;

import com.google.gson.Gson;
import com.osrscopilot.data.model.CurrencyType;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.ShopItem;
import com.osrscopilot.data.model.TownNode;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

@Slf4j
@Singleton
public class ShopDatabase
{
    private static final String DATA_RESOURCE = "/com/osrscopilot/shops_data.json.gz";

    private final Gson gson;

    private final Map<Integer, Shop> shopById = new HashMap<>();
    // One NPC can run several shops (Gabooty x3, Razmire x3, ...) - keep them all, but
    // getShopByNpcId returns the "primary" (most stock, quest-open preferred).
    private final Map<Integer, List<Shop>> shopsByNpcId = new HashMap<>();

    // Dead / artifact shop pages that the wiki Category:Shops walk drags in.
    private static boolean isExcludedShop(String name)
    {
        if (name == null)
        {
            return false;
        }
        String n = name.trim();
        return n.contains("(historical)")
            || n.contains("(Deadman Mode)")
            || n.endsWith("/Entire stock")
            || n.startsWith("Leagues Reward Shop")     // seasonal Leagues content, not the live game
            || "Bunbridge General Store".equals(n);   // Bunbridge is an RS3-only location
    }
    private final Map<String, List<Shop>> shopsByTown = new HashMap<>();
    private final Map<Integer, List<Shop>> shopsByRegion = new HashMap<>();
    private final Map<WorldPoint, Shop> shopByLocation = new HashMap<>();

    private final Map<Integer, Set<Shop>> itemToShopsMap = new HashMap<>();
    private final Map<String, Set<Integer>> itemNameToShopIds = new HashMap<>();

    private final Map<Integer, TownNode> townsById = new HashMap<>();
    private final List<TownNode> allTowns = new ArrayList<>();
    private final List<Shop> allShops = new ArrayList<>();

    // Keyed by exact shop NAME (not ordinal id) - ids are reassigned by the wiki scraper every time
    // Category:Shops is re-walked (confirmed 2026-08-20: a full re-scrape shifted Aubury's id 32->33,
    // silently breaking this table when it was id-keyed and desyncing two tests along with it). Names
    // are far more stable across rescrapes than positional ids, though not perfectly immutable if a
    // shop is ever renamed on the wiki - if this table ever silently stops matching after a future
    // rescrape, that's the first thing to check.
    private static final Map<String, WorldPoint> KNOWN_NATIVE_SHOP_ALIGNMENTS = new HashMap<>();

    static
    {
        // Curated world map icon / building center alignment coordinates for prominent shops across Gielinor
        // Varrock
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Aubury's Rune Shop", new WorldPoint(3253, 3402, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Lowe's Archery Emporium", new WorldPoint(3233, 3424, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Horvik's Armour Shop", new WorldPoint(3230, 3436, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Varrock Swordshop", new WorldPoint(3214, 3425, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Thessalia's Fine Clothes", new WorldPoint(3207, 3418, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Varrock General Store", new WorldPoint(3217, 3415, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Zaff's Superior Staffs!", new WorldPoint(3201, 3434, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Apothecary's Potions", new WorldPoint(3195, 3404, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Fancy Clothes Store", new WorldPoint(3280, 3397, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Blue Moon Inn", new WorldPoint(3224, 3398, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Jolly Boar Inn", new WorldPoint(3282, 3497, 0));

        // Falador
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Cassie's Shield Shop", new WorldPoint(2976, 3385, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Flynn's Mace Market", new WorldPoint(2950, 3388, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Wayne's Chains! - Chainmail specialist", new WorldPoint(2972, 3312, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Falador General Store", new WorldPoint(2956, 3386, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Herquin's Gems", new WorldPoint(2947, 3336, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Rising Sun Inn", new WorldPoint(2958, 3372, 0));

        // Al Kharid
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Zeke's Superior Scimitars", new WorldPoint(3288, 3190, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Louie's Armoured Legs Bazaar", new WorldPoint(3316, 3176, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Ranael's Super Skirt Store", new WorldPoint(3315, 3176, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Al Kharid General Store", new WorldPoint(3317, 3186, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Ali's Discount Wares", new WorldPoint(3303, 3211, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Gem Trader", new WorldPoint(3315, 3209, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Dommik's Crafting Store", new WorldPoint(3324, 3197, 0));

        // Lumbridge
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Lumbridge General Store", new WorldPoint(3212, 3247, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Bob's Brilliant Axes", new WorldPoint(3230, 3203, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("The Sheared Ram", new WorldPoint(3229, 3239, 0));

        // Port Sarim
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Gerrant's Fishy Business", new WorldPoint(3014, 3225, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Brian's Battleaxe Bazaar", new WorldPoint(3028, 3250, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Food Store", new WorldPoint(3013, 3207, 0)); // Port Sarim Food Store
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Betty's Magic Emporium", new WorldPoint(3014, 3259, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Grum's Gold Exchange", new WorldPoint(3015, 3250, 0));

        // Catherby & Seers
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Hickton's Archery Emporium", new WorldPoint(2823, 3441, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Harry's Fishing Shop", new WorldPoint(2834, 3433, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Arhein's General Goods", new WorldPoint(2804, 3432, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Vanessa's Farming shop", new WorldPoint(2813, 3463, 0));

        // East Ardougne
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Aemad's Adventuring Supplies", new WorldPoint(2617, 3295, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Zenesha's Plate Mail Body Shop", new WorldPoint(2654, 3302, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Ardougne Baker's Stall", new WorldPoint(2656, 3310, 0));

        // Yanille
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Frenita's Cookery Shop", new WorldPoint(2567, 3099, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Aleck's Hunter Emporium", new WorldPoint(2567, 3082, 0));
        KNOWN_NATIVE_SHOP_ALIGNMENTS.put("Dragon Inn", new WorldPoint(2558, 3083, 0));
    }

    /**
     * Names of shops whose native OSRS world-map icon coordinate is hand-verified, so a caller can
     * halo the existing icon instead of drawing a competing pin (see {@code NativeIconDetector}).
     */
    public static java.util.Set<String> knownNativeAlignedShopNames()
    {
        return java.util.Collections.unmodifiableSet(KNOWN_NATIVE_SHOP_ALIGNMENTS.keySet());
    }

    // volatile: load() runs on a background thread; a reader seeing true has a happens-before
    // edge to the index maps populated before the write (safe publication).
    private volatile boolean loaded = false;

    // Set when a load attempt finished without producing data (missing/corrupt archive). Lets the
    // UI say "failed to load" instead of an indefinite "still loading".
    private volatile boolean loadFailed = false;

    @Inject
    public ShopDatabase(Gson gson)
    {
        this.gson = gson;
    }

    public synchronized void load()
    {
        if (loaded)
        {
            return;
        }

        long start = System.currentTimeMillis();
        loadFailed = false;
        try (InputStream is = getClass().getResourceAsStream(DATA_RESOURCE))
        {
            if (is == null)
            {
                log.error("Resource not found: {}", DATA_RESOURCE);
                loadFailed = true;
                return;
            }

            try (GZIPInputStream gzis = new GZIPInputStream(is);
                 InputStreamReader isr = new InputStreamReader(gzis, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(isr))
            {
                DatabasePayload payload = gson.fromJson(reader, DatabasePayload.class);
                if (payload != null)
                {
                    buildIndexes(payload);
                    loaded = true;
                    log.debug("Loaded {} shops, {} towns, {} indexed items in {} ms",
                        allShops.size(), allTowns.size(), itemToShopsMap.size(),
                        (System.currentTimeMillis() - start));
                }
                else
                {
                    log.error("Shop database archive parsed to no records");
                    loadFailed = true;
                }
            }
        }
        catch (Exception e)
        {
            log.error("Failed to load shop database from gzip archive", e);
            loadFailed = true;
        }
    }

    private void buildIndexes(DatabasePayload payload)
    {
        clear();

        if (payload.towns != null)
        {
            for (TownNode town : payload.towns)
            {
                townsById.put(town.getId(), town);
                allTowns.add(town);
            }
        }

        if (payload.shops != null)
        {
            for (RawShop raw : payload.shops)
            {
                if (isExcludedShop(raw.name))
                {
                    continue;
                }
                List<ShopItem> shopItems = new ArrayList<>();
                if (raw.items != null)
                {
                    for (RawShopItem rawItem : raw.items)
                    {
                        shopItems.add(ShopItem.builder()
                            .itemId(rawItem.itemId)
                            .name(rawItem.name)
                            .price(rawItem.price)
                            .buyPrice(rawItem.buyPrice)
                            .defaultStock(rawItem.defaultStock)
                            .restockTimeSeconds(rawItem.restockTimeSeconds)
                            .zeroDefaultStock(rawItem.zeroDefaultStock)
                            .ironmanBlocked(rawItem.ironmanBlocked)
                            .essentialTags(rawItem.essentialTags != null ? rawItem.essentialTags : Collections.emptyList())
                            .build());
                    }
                }

                Shop shop = Shop.builder()
                    .id(raw.id)
                    .name(raw.name)
                    .npcId(raw.npcId)
                    .npcName(raw.npcName)
                    .town(raw.town)
                    .description(raw.description)
                    .questRequirement(raw.questRequirement)
                    .regionId(raw.regionId)
                    .worldX(raw.worldX)
                    .worldY(raw.worldY)
                    .worldPlane(raw.worldPlane)
                    .mapX(raw.mapX)
                    .mapY(raw.mapY)
                    .membersOnly(raw.membersOnly)
                    .currency(CurrencyType.fromCode(raw.currency))
                    .items(shopItems)
                    .tags(raw.tags != null ? raw.tags : Collections.emptyList())
                    .build();

                allShops.add(shop);
                shopById.put(shop.getId(), shop);

                if (shop.getNpcId() > 0)
                {
                    shopsByNpcId.computeIfAbsent(shop.getNpcId(), k -> new ArrayList<>()).add(shop);
                }

                if (shop.getWorldX() > 0 && shop.getWorldY() > 0)
                {
                    shopByLocation.put(shop.getWorldPoint(), shop);
                }

                if (shop.getTown() != null)
                {
                    shopsByTown.computeIfAbsent(shop.getTown().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(shop);
                }

                shopsByRegion.computeIfAbsent(shop.getRegionId(), k -> new ArrayList<>()).add(shop);

                for (ShopItem item : shopItems)
                {
                    itemToShopsMap.computeIfAbsent(item.getItemId(), k -> new HashSet<>()).add(shop);

                    // Guard a null item name so one bad row can't NPE the whole vendor-directory
                    // load (matches MonsterDatabase.buildIndexes).
                    if (item.getName() == null)
                    {
                        continue;
                    }
                    String[] tokens = item.getName().toLowerCase(Locale.ROOT).split("\\s+");
                    for (String token : tokens)
                    {
                        if (!token.isEmpty())
                        {
                            itemNameToShopIds.computeIfAbsent(token, k -> new HashSet<>()).add(shop.getId());
                        }
                    }
                }
            }
        }
    }

    public synchronized void clear()
    {
        shopById.clear();
        shopsByNpcId.clear();
        shopsByTown.clear();
        shopsByRegion.clear();
        shopByLocation.clear();
        itemToShopsMap.clear();
        itemNameToShopIds.clear();
        townsById.clear();
        allTowns.clear();
        allShops.clear();
        loaded = false;
        loadFailed = false;
    }

    public boolean isLoaded()
    {
        return loaded;
    }

    /** True when a load attempt ran but produced no data (missing or corrupt archive). */
    public boolean isLoadFailed()
    {
        return loadFailed;
    }

    public Shop getShopById(int id) { return shopById.get(id); }
    /** The primary shop for an NPC (most stock; a quest-open one preferred over a quest-locked one). */
    public Shop getShopByNpcId(int npcId)
    {
        List<Shop> list = shopsByNpcId.get(npcId);
        if (list == null || list.isEmpty())
        {
            return null;
        }
        Shop best = list.get(0);
        for (Shop s : list)
        {
            boolean sOpen = s.getQuestRequirement() == null || s.getQuestRequirement().isEmpty();
            boolean bOpen = best.getQuestRequirement() == null || best.getQuestRequirement().isEmpty();
            int sN = s.getItems() == null ? 0 : s.getItems().size();
            int bN = best.getItems() == null ? 0 : best.getItems().size();
            if ((sOpen && !bOpen) || (sOpen == bOpen && sN > bN))
            {
                best = s;
            }
        }
        return best;
    }

    /** Every shop an NPC runs, in load order. Empty when the NPC has none. */
    public List<Shop> getShopsByNpcId(int npcId)
    {
        return Collections.unmodifiableList(shopsByNpcId.getOrDefault(npcId, Collections.emptyList()));
    }
    public Shop getShopByWorldPoint(WorldPoint point) { return shopByLocation.get(point); }
    public List<Shop> getShopsByTown(String town) { return shopsByTown.getOrDefault(town.toLowerCase(Locale.ROOT), Collections.emptyList()); }
    public List<Shop> getShopsByRegion(int regionId) { return shopsByRegion.getOrDefault(regionId, Collections.emptyList()); }
    public Set<Shop> getShopsSellingItem(int itemId) { return itemToShopsMap.getOrDefault(itemId, Collections.emptySet()); }
    public List<Shop> getAllShops() { return Collections.unmodifiableList(allShops); }
    public List<TownNode> getAllTowns() { return Collections.unmodifiableList(allTowns); }

    public TownNode getTownByName(String name)
    {
        if (name == null) return null;
        for (TownNode town : allTowns)
        {
            if (town.getName().equalsIgnoreCase(name))
            {
                return town;
            }
        }
        return null;
    }

    public Shop getShopByName(String name)
    {
        if (name == null) return null;
        for (Shop shop : allShops)
        {
            if (shop.getName().equalsIgnoreCase(name))
            {
                return shop;
            }
        }
        return null;
    }

    public Set<Shop> searchShopsByItemName(String query)
    {
        if (query == null || query.trim().isEmpty())
        {
            return Collections.emptySet();
        }

        String[] queryTokens = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        Set<Integer> matchingShopIds = null;

        for (String token : queryTokens)
        {
            Set<Integer> tokenMatches = new HashSet<>();
            for (Map.Entry<String, Set<Integer>> entry : itemNameToShopIds.entrySet())
            {
                if (entry.getKey().contains(token))
                {
                    tokenMatches.addAll(entry.getValue());
                }
            }

            if (matchingShopIds == null)
            {
                matchingShopIds = tokenMatches;
            }
            else
            {
                matchingShopIds.retainAll(tokenMatches);
            }

            if (matchingShopIds.isEmpty())
            {
                break;
            }
        }

        if (matchingShopIds == null || matchingShopIds.isEmpty())
        {
            return Collections.emptySet();
        }

        Set<Shop> results = new HashSet<>();
        for (int id : matchingShopIds)
        {
            Shop shop = shopById.get(id);
            if (shop != null)
            {
                results.add(shop);
            }
        }
        return results;
    }

    /**
     * Resolves the best-aligned WorldPoint for a shop marker:
     * 1. Returns the curated native-icon alignment coordinate if this shop is one of the ~42
     *    hand-verified entries in {@link #KNOWN_NATIVE_SHOP_ALIGNMENTS} - these were specifically
     *    checked against the real in-game native map icon, which is a more precise fit for that
     *    purpose than a generic wiki {@code {{Map}}} polygon center.
     * 2. Returns the explicitly-scraped mapX/mapY coordinate if available (as of the 2026-08-20 full
     *    shop rescrape, this now covers ~98% of shops - it used to be checked first, but that
     *    silently made the curated table dead code for every shop it covers once mapX/mapY started
     *    being populated, since a generic-but-present coordinate always beat a purpose-built one).
     * 3. Falls back to the NPC's raw world-standing coordinate.
     */
    public WorldPoint getAlignedShopLocation(Shop shop)
    {
        if (shop == null)
        {
            return null;
        }

        WorldPoint known = KNOWN_NATIVE_SHOP_ALIGNMENTS.get(shop.getName());
        if (known != null)
        {
            return known;
        }

        if (shop.hasExplicitMapLocation())
        {
            return shop.getMapLocation();
        }

        return shop.getWorldPoint();
    }

    private static class DatabasePayload
    {
        List<TownNode> towns;
        List<RawShop> shops;
    }

    private static class RawShop
    {
        int id;
        String name;
        int npcId;
        String npcName;
        String town;
        String description;
        String questRequirement;
        int regionId;
        int worldX;
        int worldY;
        int worldPlane;
        int mapX;
        int mapY;
        boolean membersOnly;
        String currency;
        List<RawShopItem> items;
        List<String> tags;
    }

    private static class RawShopItem
    {
        int itemId;
        String name;
        int price;
        int buyPrice;
        int defaultStock;
        int restockTimeSeconds;
        boolean zeroDefaultStock;
        boolean ironmanBlocked;
        List<String> essentialTags;
    }
}
