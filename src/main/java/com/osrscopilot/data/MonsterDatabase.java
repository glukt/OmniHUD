package com.osrscopilot.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterDrop;
import com.osrscopilot.data.model.MonsterSpawnRow;
import com.osrscopilot.data.model.MonsterSpawnZone;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

@Slf4j
@Singleton
public class MonsterDatabase
{
    private static final String DATA_RESOURCE = "/com/osrscopilot/monsters_data.json.gz";

    /**
     * Records whose id is above this are synthetic CRC32 hashes the build script assigns to variant
     * monsters it can't map to a real NPC id (the "Awakened" DT2 bosses, delve-scaled Doom of
     * Mokhaiotl tiers, ...). Real OSRS NPC ids are 5 digits today. Used to keep a synthetic
     * super-variant from winning the bare-name lookup over the normal fight.
     */
    private static final int SYNTHETIC_ID_THRESHOLD = 200_000;

    /**
     * Orders same-name variants best-first for {@link #getMonsterByName} / {@link #getMonstersByName}:
     * a real NPC id before a synthetic hash, then higher combat level (the repeatable post-quest
     * grind version — e.g. post-quest Vorkath cb 732 over the quest fight cb 392), then more spawn
     * zones, then the lower id for stability.
     */
    private static final Comparator<Monster> PRIMARY_VARIANT_ORDER =
        Comparator.<Monster>comparingInt(m -> isSyntheticId(m.getId()) ? 1 : 0)
            .thenComparing(Comparator.comparingInt(Monster::getCombatLevel).reversed())
            .thenComparing(Comparator.comparingInt((Monster m) -> m.getSpawnZones() == null ? 0 : m.getSpawnZones().size()).reversed())
            .thenComparingInt(Monster::getId);

    private final Gson gson;

    private final Map<Integer, Monster> monsterById = new HashMap<>();
    private final Map<String, Monster> monsterByName = new HashMap<>();
    private final Map<String, List<Monster>> monsterVariantsByName = new HashMap<>();
    private final Map<String, List<Monster>> monstersByCategory = new HashMap<>();
    private final Map<String, List<Monster>> monstersByDungeon = new HashMap<>();
    private final Map<String, List<Monster>> monstersByLocationName = new HashMap<>();

    private final Map<Integer, Set<Monster>> itemToMonstersMap = new HashMap<>();
    private final Map<String, Set<Integer>> itemNameToMonsterIds = new HashMap<>();
    private final Map<String, Set<Integer>> monsterNameToMonsterIds = new HashMap<>();

    private final List<Monster> allMonsters = new ArrayList<>();
    private final Set<String> allCategories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    // Derived family lists (bosses / dragons / demons / undead / slayer / wilderness / f2p / members)
    // are full allMonsters scans; the data is immutable after load, so compute them once.
    private final Map<String, List<Monster>> derivedCategoryCache = new HashMap<>();

    // Sorted name / drop-name token arrays for a binary-search prefix lookup, so "vork" / "drag"
    // don't scan the whole ~3k-entry token vocabulary (the old comments claimed O(1); it was O(vocab)).
    private volatile String[] nameTokensSorted = new String[0];
    private volatile String[] itemTokensSorted = new String[0];

    // volatile: load() runs on a background thread; a reader seeing true has a happens-before
    // edge to the index maps populated before the write (safe publication).
    private volatile boolean loaded = false;

    // Set when a load attempt finished without producing data (missing/corrupt archive). Lets the
    // UI say "failed to load" instead of an indefinite "still loading".
    private volatile boolean loadFailed = false;

    // The parsed raw list is immutable-after-parse and identical every run, so cache it across
    // plugin disable/enable cycles (Guice makes a fresh @Singleton each time). This skips the
    // ~150-400ms GZIP inflate + reflective Gson bind of ~32k objects on every re-enable and on the
    // lazy load() calls from ShopDirectorySpreadsheetDialog's Swing handlers; only buildIndexes
    // (~10-30ms) re-runs to repopulate this instance's maps.
    private static volatile List<RawMonster> cachedRawList;

    @Inject
    public MonsterDatabase(Gson gson)
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

        List<RawMonster> rawList = cachedRawList;
        if (rawList != null)
        {
            buildIndexes(rawList);
            loaded = true;
            log.debug("Loaded {} monsters from the parsed-DB cache in {} ms",
                allMonsters.size(), System.currentTimeMillis() - start);
            return;
        }

        try (InputStream is = MonsterDatabase.class.getResourceAsStream(DATA_RESOURCE))
        {
            if (is == null)
            {
                log.error("Monsters resource not found at {}", DATA_RESOURCE);
                loadFailed = true;
                return;
            }

            try (GZIPInputStream gzis = new GZIPInputStream(is);
                 InputStreamReader isr = new InputStreamReader(gzis, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(isr))
            {
                Type listType = new TypeToken<List<RawMonster>>() {}.getType();
                rawList = gson.fromJson(reader, listType);

                if (rawList != null)
                {
                    internRawStrings(rawList);
                    cachedRawList = rawList;
                    buildIndexes(rawList);
                    loaded = true;
                    log.debug("Loaded {} monsters, {} categories, {} indexed drop items in {} ms",
                        allMonsters.size(), allCategories.size(), itemToMonstersMap.size(),
                        (System.currentTimeMillis() - start));
                }
                else
                {
                    log.error("Monster database archive parsed to no records");
                    loadFailed = true;
                }
            }
        }
        catch (Exception e)
        {
            log.error("Failed to load monster database from gzip archive", e);
            loadFailed = true;
        }
    }

    /**
     * D4: Gson allocates a fresh String per field per record, but the repeated values
     * (drop name / quantity / rarityFraction / category, zone names) have only a few hundred
     * distinct values across 24k drops + 6k zones - a shared pool drops ~90k redundant Strings
     * (~3-5 MB). Runs once, before {@link #cachedRawList} is published.
     */
    private static void internRawStrings(List<RawMonster> rawList)
    {
        Map<String, String> pool = new HashMap<>();
        for (RawMonster m : rawList)
        {
            if (m == null)
            {
                continue;
            }
            m.category = intern(pool, m.category);
            m.attackType = intern(pool, m.attackType);
            m.weakness = intern(pool, m.weakness);
            m.questRequirement = intern(pool, m.questRequirement);
            m.encounterType = intern(pool, m.encounterType);
            if (m.drops != null)
            {
                for (RawDrop d : m.drops)
                {
                    d.name = intern(pool, d.name);
                    d.quantity = intern(pool, d.quantity);
                    d.rarityFraction = intern(pool, d.rarityFraction);
                    d.category = intern(pool, d.category);
                }
            }
            if (m.spawnZones != null)
            {
                for (RawSpawnZone z : m.spawnZones)
                {
                    z.zoneName = intern(pool, z.zoneName);
                    z.locationName = intern(pool, z.locationName);
                    z.dungeonName = intern(pool, z.dungeonName);
                }
            }
        }
    }

    private static String intern(Map<String, String> pool, String s)
    {
        if (s == null)
        {
            return null;
        }
        String hit = pool.get(s);
        if (hit != null)
        {
            return hit;
        }
        pool.put(s, s);
        return s;
    }

    private void buildIndexes(List<RawMonster> rawList)
    {
        clear();

        for (RawMonster raw : rawList)
        {
            List<MonsterDrop> drops = new ArrayList<>();
            if (raw.drops != null)
            {
                for (RawDrop rawDrop : raw.drops)
                {
                    drops.add(MonsterDrop.builder()
                        .itemId(rawDrop.itemId)
                        .name(rawDrop.name)
                        .quantity(rawDrop.quantity)
                        .rarity(rawDrop.rarity)
                        .rarityFraction(rawDrop.rarityFraction)
                        .rolls(rawDrop.rolls)
                        .noted(rawDrop.noted)
                        .members(rawDrop.members)
                        .category(rawDrop.category != null && !rawDrop.category.trim().isEmpty() ? rawDrop.category.trim() : "Other")
                        .build());
                }
            }

            List<MonsterSpawnZone> spawnZones = new ArrayList<>();
            if (raw.spawnZones != null)
            {
                for (RawSpawnZone rawZone : raw.spawnZones)
                {
                    WorldPoint center = null;
                    if (rawZone.centerPoint != null)
                    {
                        center = new WorldPoint(rawZone.centerPoint.x, rawZone.centerPoint.y, rawZone.centerPoint.plane);
                    }

                    WorldPoint surface = null;
                    if (rawZone.surfaceEntrance != null)
                    {
                        surface = new WorldPoint(rawZone.surfaceEntrance.x, rawZone.surfaceEntrance.y, rawZone.surfaceEntrance.plane);
                    }

                    spawnZones.add(MonsterSpawnZone.builder()
                        .zoneName(rawZone.zoneName)
                        .locationName(rawZone.locationName)
                        .minX(rawZone.minX)
                        .minY(rawZone.minY)
                        .maxX(rawZone.maxX)
                        .maxY(rawZone.maxY)
                        .plane(rawZone.plane)
                        .spawnCount(rawZone.spawnCount)
                        .multiCombat(rawZone.multiCombat)
                        .wildernessLevel(rawZone.wildernessLevel)
                        .centerPoint(center)
                        .surfaceEntrance(surface)
                        .dungeonName(rawZone.dungeonName)
                        .entranceVerified(rawZone.entranceVerified)
                        .build());
                }
            }

            // One-off loader fixup: the Chambers of Xeric "Lizardman shaman" (id 7573) shipped as a
            // stat-less record (combatLevel 0) carrying a copy of the open-world shaman's three
            // spawn zones, so it rendered as a duplicate roaming monster on the Bestiary list and
            // stacked a second pin on every Lizardman zone on the world map. It is a raid-instanced
            // encounter with no open-world spawns - drop the bogus zones and tag it as instanced.
            // Keyed on id + name + the tell-tale combatLevel 0 so a future rescrape that fixes the
            // record silently no-ops this. (Its real combat stats still want a DB rebuild.)
            String effectiveEncounterType = raw.encounterType;
            if (raw.id == 7573 && "Lizardman shaman".equals(raw.name) && raw.combatLevel == 0)
            {
                spawnZones.clear();
                if (effectiveEncounterType == null || effectiveEncounterType.trim().isEmpty())
                {
                    effectiveEncounterType = "Chambers of Xeric";
                }
            }

            Monster monster = Monster.builder()
                .id(raw.id)
                .npcIds(raw.npcIds != null ? Collections.unmodifiableList(new ArrayList<>(raw.npcIds)) : Collections.emptyList())
                .name(raw.name)
                .combatLevel(raw.combatLevel)
                .hitpoints(raw.hitpoints)
                .maxHit(raw.maxHit)
                .attackType(raw.attackType)
                .attackSpeed(raw.attackSpeed)
                .slayerLevel(raw.slayerLevel)
                .questRequirement(raw.questRequirement)
                .encounterType(effectiveEncounterType)
                .aggressive(raw.aggressive)
                .poisonous(raw.poisonous)
                .immuneToPoison(raw.immuneToPoison)
                .members(raw.members)
                .category(raw.category != null ? raw.category : "Standard")
                .wikiUrl(raw.wikiUrl)
                .defenceStab(raw.defenceStab)
                .defenceSlash(raw.defenceSlash)
                .defenceCrush(raw.defenceCrush)
                .defenceMagic(raw.defenceMagic)
                .defenceRanged(raw.defenceRanged)
                .weakness(raw.weakness)
                .attributes(raw.attributes)
                .wikiImage(raw.wikiImage)
                .spawnZones(Collections.unmodifiableList(spawnZones))
                .drops(Collections.unmodifiableList(drops))
                .build();

            allMonsters.add(monster);
            monsterById.put(monster.getId(), monster);
            // Also index every real in-game NPC id this monster answers to. putIfAbsent so the
            // representative record wins any (rare) contested id.
            for (Integer npcId : monster.getNpcIds())
            {
                if (npcId != null)
                {
                    monsterById.putIfAbsent(npcId, monster);
                }
            }

            if (monster.getName() != null)
            {
                monsterVariantsByName.computeIfAbsent(monster.getName().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(monster);

                String[] tokens = monster.getName().toLowerCase(Locale.ROOT).split("[\\s\\-_/(),.]+");
                for (String token : tokens)
                {
                    if (!token.isEmpty())
                    {
                        monsterNameToMonsterIds.computeIfAbsent(token, k -> new HashSet<>()).add(monster.getId());
                    }
                }
            }

            if (monster.getCategory() != null && !monster.getCategory().isEmpty())
            {
                allCategories.add(monster.getCategory());
                monstersByCategory.computeIfAbsent(monster.getCategory().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(monster);
            }

            for (MonsterDrop drop : drops)
            {
                if (drop.getItemId() > 0)
                {
                    itemToMonstersMap.computeIfAbsent(drop.getItemId(), k -> new HashSet<>()).add(monster);
                }

                if (drop.getName() != null)
                {
                    String[] tokens = drop.getName().toLowerCase(Locale.ROOT).split("[\\s\\-_/(),.]+");
                    for (String token : tokens)
                    {
                        if (!token.isEmpty())
                        {
                            itemNameToMonsterIds.computeIfAbsent(token, k -> new HashSet<>()).add(monster.getId());
                        }
                    }
                }
            }

            for (MonsterSpawnZone zone : spawnZones)
            {
                if (zone.getDungeonName() != null && !zone.getDungeonName().isEmpty())
                {
                    monstersByDungeon.computeIfAbsent(zone.getDungeonName().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(monster);
                }
                if (zone.getLocationName() != null && !zone.getLocationName().isEmpty())
                {
                    monstersByLocationName.computeIfAbsent(zone.getLocationName().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(monster);
                }
            }
        }

        // ~28% of records share a display name with another (level / mode / quest-stage variants).
        // Collapse each name to one "primary" variant for the fast-path lookup, and keep every
        // variant list sorted primary-first for getMonstersByName.
        for (List<Monster> variants : monsterVariantsByName.values())
        {
            variants.sort(PRIMARY_VARIANT_ORDER);
            monsterByName.put(variants.get(0).getName().toLowerCase(Locale.ROOT), variants.get(0));
        }

        buildDerivedCategoryCache();

        nameTokensSorted = monsterNameToMonsterIds.keySet().toArray(new String[0]);
        java.util.Arrays.sort(nameTokensSorted);
        itemTokensSorted = itemNameToMonsterIds.keySet().toArray(new String[0]);
        java.util.Arrays.sort(itemTokensSorted);
    }

    /** One-time compute of the family lists that getMonstersByCategory / the family getters return. */
    private void buildDerivedCategoryCache()
    {
        derivedCategoryCache.put("boss", monstersByCategory.getOrDefault("boss", Collections.emptyList()));

        List<Monster> dragons = new ArrayList<>();
        List<Monster> demons = new ArrayList<>();
        List<Monster> undead = new ArrayList<>();
        List<Monster> slayer = new ArrayList<>();
        List<Monster> wildy = new ArrayList<>();
        List<Monster> f2p = new ArrayList<>();
        List<Monster> members = new ArrayList<>();
        for (Monster m : allMonsters)
        {
            String n = lc(m.getName());
            String cat = m.getCategory();
            if ("Dragon".equalsIgnoreCase(cat) || n.contains("dragon") || n.contains("wyvern") || n.contains("drake") || n.contains("hydra"))
            {
                dragons.add(m);
            }
            if ("Demon".equalsIgnoreCase(cat) || n.contains("demon") || n.contains("fiend"))
            {
                demons.add(m);
            }
            if ("Undead".equalsIgnoreCase(cat) || n.contains("skeleton") || n.contains("zombie") || n.contains("ghost") || n.contains("ankou") || n.contains("vampyre"))
            {
                undead.add(m);
            }
            if (m.getSlayerLevel() > 1 || "Slayer".equalsIgnoreCase(cat))
            {
                slayer.add(m);
            }
            if ("Wilderness".equalsIgnoreCase(cat) || (m.getSpawnZones() != null && m.getSpawnZones().stream().anyMatch(z -> z.getWildernessLevel() > 0)))
            {
                wildy.add(m);
            }
            if (!m.isMembers() || "F2P".equalsIgnoreCase(cat))
            {
                f2p.add(m);
            }
            if (m.isMembers())
            {
                members.add(m);
            }
        }
        derivedCategoryCache.put("dragon", Collections.unmodifiableList(dragons));
        derivedCategoryCache.put("demon", Collections.unmodifiableList(demons));
        derivedCategoryCache.put("undead", Collections.unmodifiableList(undead));
        derivedCategoryCache.put("slayer", Collections.unmodifiableList(slayer));
        derivedCategoryCache.put("wilderness", Collections.unmodifiableList(wildy));
        derivedCategoryCache.put("f2p", Collections.unmodifiableList(f2p));
        derivedCategoryCache.put("members", Collections.unmodifiableList(members));
    }

    private static boolean isSyntheticId(int id)
    {
        return id > SYNTHETIC_ID_THRESHOLD;
    }

    /** Null-safe lower-case of a monster name, so a single null name can't NPE a whole filter/sort. */
    private static String lc(String s)
    {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /**
     * Monster ids whose name/drop token matches {@code token}: a binary-search prefix range over the
     * sorted token array first ("vork", "drag", "aby" - the normal case, O(log n + hits)), then a
     * full substring scan only if the prefix found nothing (mid-word queries like "iathan").
     */
    private static Set<Integer> idsForToken(String[] sortedKeys, Map<String, Set<Integer>> index, String token)
    {
        Set<Integer> out = new HashSet<>();
        // A 1-char query is inherently the "match everything" broad case - keep it a substring scan.
        if (token.length() >= 2)
        {
            int lo = java.util.Arrays.binarySearch(sortedKeys, token);
            if (lo < 0)
            {
                lo = -lo - 1;
            }
            for (int i = lo; i < sortedKeys.length && sortedKeys[i].startsWith(token); i++)
            {
                out.addAll(index.get(sortedKeys[i]));
            }
        }
        if (out.isEmpty())
        {
            for (Map.Entry<String, Set<Integer>> e : index.entrySet())
            {
                if (e.getKey().contains(token))
                {
                    out.addAll(e.getValue());
                }
            }
        }
        return out;
    }

    public synchronized void clear()
    {
        monsterById.clear();
        monsterByName.clear();
        monsterVariantsByName.clear();
        monstersByCategory.clear();
        monstersByDungeon.clear();
        monstersByLocationName.clear();
        itemToMonstersMap.clear();
        itemNameToMonsterIds.clear();
        monsterNameToMonsterIds.clear();
        derivedCategoryCache.clear();
        nameTokensSorted = new String[0];
        itemTokensSorted = new String[0];
        allMonsters.clear();
        allCategories.clear();
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

    public int getMonsterCount()
    {
        return allMonsters.size();
    }

    public List<Monster> getAllMonsters()
    {
        return Collections.unmodifiableList(allMonsters);
    }

    public Set<String> getCategories()
    {
        return Collections.unmodifiableSet(allCategories);
    }

    public Monster getMonsterById(int id)
    {
        return monsterById.get(id);
    }

    public Monster getMonsterByName(String name)
    {
        if (name == null)
        {
            return null;
        }
        return monsterByName.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Every monster sharing a display name (case-insensitive), primary variant first (same order
     * {@link #getMonsterByName} picks its single result from). Use this when one representative
     * isn't enough — e.g. a loot tracker that must tell a normal drop table from the "Awakened" one,
     * or a caller that has an NPC id and wants to match the exact variant.
     */
    public List<Monster> getMonstersByName(String name)
    {
        if (name == null)
        {
            return Collections.emptyList();
        }
        List<Monster> variants = monsterVariantsByName.get(name.trim().toLowerCase(Locale.ROOT));
        return variants == null ? Collections.emptyList() : Collections.unmodifiableList(variants);
    }

    public List<Monster> getMonstersByCategory(String category)
    {
        if (category == null || category.trim().isEmpty() || "All".equalsIgnoreCase(category.trim()) || "All Categories".equalsIgnoreCase(category.trim()))
        {
            return getAllMonsters();
        }

        String cat = category.trim().toLowerCase(Locale.ROOT);
        if ("bosses".equals(cat) || "boss".equals(cat))
        {
            return getBosses();
        }
        if ("dragons".equals(cat) || "dragon".equals(cat))
        {
            return getDragons();
        }
        if ("demons".equals(cat) || "demon".equals(cat))
        {
            return getDemons();
        }
        if ("undead".equals(cat) || "undeads".equals(cat))
        {
            return getUndead();
        }
        if ("slayer".equals(cat) || "slayers".equals(cat))
        {
            return getSlayerMonsters();
        }
        if ("wilderness".equals(cat))
        {
            return getWildernessMonsters();
        }
        if ("f2p".equals(cat))
        {
            return getF2PMonsters();
        }
        if ("p2p".equals(cat) || "members".equals(cat))
        {
            return getMembersMonsters();
        }

        return monstersByCategory.getOrDefault(cat, Collections.emptyList());
    }

    private List<Monster> cachedFamily(String key)
    {
        return derivedCategoryCache.getOrDefault(key, Collections.emptyList());
    }

    public List<Monster> getBosses()
    {
        return cachedFamily("boss");
    }

    public List<Monster> getSlayerMonsters()
    {
        return cachedFamily("slayer");
    }

    public List<Monster> getDragons()
    {
        return cachedFamily("dragon");
    }

    public List<Monster> getDemons()
    {
        return cachedFamily("demon");
    }

    public List<Monster> getUndead()
    {
        return cachedFamily("undead");
    }

    public List<Monster> getWildernessMonsters()
    {
        return cachedFamily("wilderness");
    }

    public List<Monster> getF2PMonsters()
    {
        return cachedFamily("f2p");
    }

    public List<Monster> getMembersMonsters()
    {
        return cachedFamily("members");
    }

    public List<Monster> getMonstersByCombatLevel(int minLevel, int maxLevel)
    {
        List<Monster> results = new ArrayList<>();
        for (Monster m : allMonsters)
        {
            if (m.getCombatLevel() >= minLevel && m.getCombatLevel() <= maxLevel)
            {
                results.add(m);
            }
        }
        return results;
    }

    public List<Monster> getMonstersBySlayerLevel(int minLevel, int maxLevel)
    {
        List<Monster> results = new ArrayList<>();
        for (Monster m : allMonsters)
        {
            if (m.getSlayerLevel() >= minLevel && m.getSlayerLevel() <= maxLevel)
            {
                results.add(m);
            }
        }
        return results;
    }

    public Set<Monster> getMonstersDroppingItem(int itemId)
    {
        return itemToMonstersMap.getOrDefault(itemId, Collections.emptySet());
    }

    public List<Monster> searchMonstersByName(String query)
    {
        if (query == null || query.trim().isEmpty())
        {
            return Collections.emptyList();
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT).trim();
        String[] queryTokens = lowerQuery.split("[\\s\\-_/(),.]+");

        Set<Integer> matchingIds = null;

        for (String token : queryTokens)
        {
            if (token.isEmpty())
            {
                continue;
            }

            Set<Integer> tokenMatches = idsForToken(nameTokensSorted, monsterNameToMonsterIds, token);

            if (matchingIds == null)
            {
                matchingIds = tokenMatches;
            }
            else
            {
                matchingIds.retainAll(tokenMatches);
            }

            if (matchingIds.isEmpty())
            {
                break;
            }
        }

        if (matchingIds == null || matchingIds.isEmpty())
        {
            return Collections.emptyList();
        }

        List<Monster> results = new ArrayList<>();
        for (int id : matchingIds)
        {
            Monster monster = monsterById.get(id);
            if (monster != null)
            {
                results.add(monster);
            }
        }

        results.sort((a, b) -> {
            String an = lc(a.getName());
            String bn = lc(b.getName());
            boolean aExact = an.equals(lowerQuery);
            boolean bExact = bn.equals(lowerQuery);
            if (aExact != bExact) return aExact ? -1 : 1;

            boolean aStarts = an.startsWith(lowerQuery);
            boolean bStarts = bn.startsWith(lowerQuery);
            if (aStarts != bStarts) return aStarts ? -1 : 1;

            return Integer.compare(b.getCombatLevel(), a.getCombatLevel());
        });

        return results;
    }

    public List<Monster> searchMonstersByDropName(String query)
    {
        if (query == null || query.trim().isEmpty())
        {
            return Collections.emptyList();
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT).trim();
        String[] queryTokens = lowerQuery.split("[\\s\\-_/(),.]+");

        Set<Integer> matchingIds = null;

        for (String token : queryTokens)
        {
            if (token.isEmpty())
            {
                continue;
            }

            Set<Integer> tokenMatches = idsForToken(itemTokensSorted, itemNameToMonsterIds, token);

            if (matchingIds == null)
            {
                matchingIds = tokenMatches;
            }
            else
            {
                matchingIds.retainAll(tokenMatches);
            }

            if (matchingIds.isEmpty())
            {
                break;
            }
        }

        if (matchingIds == null || matchingIds.isEmpty())
        {
            return Collections.emptyList();
        }

        List<Monster> results = new ArrayList<>();
        for (int id : matchingIds)
        {
            Monster monster = monsterById.get(id);
            if (monster != null)
            {
                results.add(monster);
            }
        }

        results.sort((a, b) -> {
            String an = lc(a.getName());
            String bn = lc(b.getName());
            boolean aExact = an.equals(lowerQuery);
            boolean bExact = bn.equals(lowerQuery);
            if (aExact != bExact) return aExact ? -1 : 1;

            if (an.length() != bn.length()) return Integer.compare(an.length(), bn.length());

            return Integer.compare(b.getCombatLevel(), a.getCombatLevel());
        });
        return results;
    }

    public List<Monster> searchMonsters(String query)
    {
        if (query == null || query.trim().isEmpty())
        {
            return getAllMonsters();
        }

        String lower = query.trim().toLowerCase(Locale.ROOT);
        List<Monster> byName = searchMonstersByName(lower);
        if (!byName.isEmpty())
        {
            return byName;
        }

        Set<Monster> combined = new LinkedHashSet<>();
        combined.addAll(searchMonstersByDropName(lower));

        for (Monster m : allMonsters)
        {
            if (m.getCategory() != null && m.getCategory().toLowerCase(Locale.ROOT).contains(lower))
            {
                combined.add(m);
            }
            else if (m.getWeakness() != null && m.getWeakness().toLowerCase(Locale.ROOT).contains(lower))
            {
                combined.add(m);
            }
            else if (m.getAttributes() != null && m.getAttributes().toLowerCase(Locale.ROOT).contains(lower))
            {
                combined.add(m);
            }
            else if (m.getQuestRequirement() != null && m.getQuestRequirement().toLowerCase(Locale.ROOT).contains(lower))
            {
                combined.add(m);
            }
        }

        return new ArrayList<>(combined);
    }

    public List<Monster> searchMonsters(String query, String category)
    {
        List<Monster> base = getMonstersByCategory(category);

        if (query == null || query.trim().isEmpty())
        {
            return new ArrayList<>(base);
        }

        List<Monster> queryMatches = searchMonsters(query);
        Set<Monster> baseSet = new HashSet<>(base);
        List<Monster> filtered = new ArrayList<>();
        for (Monster m : queryMatches)
        {
            if (baseSet.contains(m))
            {
                filtered.add(m);
            }
        }
        return filtered;
    }

    public List<Monster> getMonstersInArea(int minX, int minY, int maxX, int maxY, int plane)
    {
        List<Monster> results = new ArrayList<>();
        for (Monster monster : allMonsters)
        {
            if (monster.getSpawnZones() != null)
            {
                for (MonsterSpawnZone zone : monster.getSpawnZones())
                {
                    if (zone.getPlane() == plane &&
                        !(zone.getMaxX() < minX || zone.getMinX() > maxX ||
                          zone.getMaxY() < minY || zone.getMinY() > maxY))
                    {
                        results.add(monster);
                        break;
                    }
                }
            }
        }
        return results;
    }

    public List<Monster> getMonstersAtLocation(WorldPoint point)
    {
        if (point == null)
        {
            return Collections.emptyList();
        }
        List<Monster> results = new ArrayList<>();
        for (Monster monster : allMonsters)
        {
            if (monster.getSpawnZones() != null)
            {
                for (MonsterSpawnZone zone : monster.getSpawnZones())
                {
                    if (zone.contains(point.getX(), point.getY(), point.getPlane()))
                    {
                        results.add(monster);
                        break;
                    }
                }
            }
        }
        return results;
    }

    public List<Monster> getMonstersByDungeon(String dungeonName)
    {
        if (dungeonName == null)
        {
            return Collections.emptyList();
        }
        return monstersByDungeon.getOrDefault(dungeonName.trim().toLowerCase(Locale.ROOT), Collections.emptyList());
    }

    public List<Monster> getMonstersByLocationName(String locationName)
    {
        if (locationName == null)
        {
            return Collections.emptyList();
        }
        return monstersByLocationName.getOrDefault(locationName.trim().toLowerCase(Locale.ROOT), Collections.emptyList());
    }

    /**
     * Returns a flattened list of all monster spawn zones as spreadsheet-ready row items.
     * Only includes monsters that have at least one spawn zone registered.
     */
    public List<MonsterSpawnRow> getFlattenedMonsterSpawnRows()
    {
        return getFlattenedMonsterSpawnRows(false);
    }

    /**
     * Returns a flattened list of monster spawn zones. If includeMonstersWithoutZones is true,
     * monsters without a registered spawn zone will be emitted once with a null zone.
     */
    public List<MonsterSpawnRow> getFlattenedMonsterSpawnRows(boolean includeMonstersWithoutZones)
    {
        List<MonsterSpawnRow> rows = new ArrayList<>();
        for (Monster monster : allMonsters)
        {
            if (monster.hasSpawnZones())
            {
                for (MonsterSpawnZone zone : monster.getSpawnZones())
                {
                    rows.add(new MonsterSpawnRow(monster, zone));
                }
            }
            else if (includeMonstersWithoutZones)
            {
                rows.add(new MonsterSpawnRow(monster, null));
            }
        }
        return Collections.unmodifiableList(rows);
    }

    /**
     * Filters flattened monster spawn rows by text query, category, and optional zone inclusion.
     */
    public List<MonsterSpawnRow> searchFlattenedMonsterSpawnRows(String query, String category, boolean onlyWithZones)
    {
        List<MonsterSpawnRow> base = getFlattenedMonsterSpawnRows(!onlyWithZones);
        if ((query == null || query.trim().isEmpty()) && (category == null || category.trim().isEmpty() || "All".equalsIgnoreCase(category.trim()) || "All Categories".equalsIgnoreCase(category.trim())))
        {
            return base;
        }

        String lowerQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        String cat = category != null ? category.trim().toLowerCase(Locale.ROOT) : "all";

        List<MonsterSpawnRow> filtered = new ArrayList<>();
        for (MonsterSpawnRow row : base)
        {
            Monster m = row.getMonster();
            MonsterSpawnZone z = row.getZone();
            String mn = lc(m.getName());

            // Category filter
            if (!cat.isEmpty() && !"all".equals(cat) && !"all categories".equals(cat))
            {
                if ("bosses".equals(cat) || "boss".equals(cat))
                {
                    if (!"boss".equalsIgnoreCase(m.getCategory())) continue;
                }
                else if ("dragons".equals(cat) || "dragon".equals(cat))
                {
                    if (!"dragon".equalsIgnoreCase(m.getCategory()) && !mn.contains("dragon")) continue;
                }
                else if ("demons".equals(cat) || "demon".equals(cat))
                {
                    if (!"demon".equalsIgnoreCase(m.getCategory()) && !mn.contains("demon")) continue;
                }
                else if ("undead".equals(cat))
                {
                    if (!"undead".equalsIgnoreCase(m.getCategory()) && !mn.contains("skeleton") && !mn.contains("zombie") && !mn.contains("ghost") && !mn.contains("ankou")) continue;
                }
                else if ("slayer".equals(cat))
                {
                    if (m.getSlayerLevel() <= 1 && !"slayer".equalsIgnoreCase(m.getCategory())) continue;
                }
                else if ("wilderness".equals(cat))
                {
                    if (!"wilderness".equalsIgnoreCase(m.getCategory()) && (z == null || z.getWildernessLevel() <= 0)) continue;
                }
                else if ("f2p".equals(cat))
                {
                    if (m.isMembers()) continue;
                }
                else if ("p2p".equals(cat) || "members".equals(cat))
                {
                    if (!m.isMembers()) continue;
                }
                else if (!cat.equalsIgnoreCase(m.getCategory()))
                {
                    continue;
                }
            }

            // Text query filter
            if (!lowerQuery.isEmpty())
            {
                boolean match = mn.contains(lowerQuery) ||
                    (z != null && z.getZoneName() != null && z.getZoneName().toLowerCase(Locale.ROOT).contains(lowerQuery)) ||
                    (z != null && z.getLocationName() != null && z.getLocationName().toLowerCase(Locale.ROOT).contains(lowerQuery)) ||
                    (z != null && z.getDungeonName() != null && z.getDungeonName().toLowerCase(Locale.ROOT).contains(lowerQuery)) ||
                    (m.getCategory() != null && m.getCategory().toLowerCase(Locale.ROOT).contains(lowerQuery)) ||
                    (m.getWeakness() != null && m.getWeakness().toLowerCase(Locale.ROOT).contains(lowerQuery)) ||
                    (m.getAttributes() != null && m.getAttributes().toLowerCase(Locale.ROOT).contains(lowerQuery)) ||
                    (m.getQuestRequirement() != null && m.getQuestRequirement().toLowerCase(Locale.ROOT).contains(lowerQuery));
                if (!match) continue;
            }

            filtered.add(row);
        }
        return filtered;
    }

    private static class RawMonster
    {
        int id;
        List<Integer> npcIds;
        String name;
        int combatLevel;
        int hitpoints;
        int maxHit;
        String attackType;
        int attackSpeed;
        int slayerLevel;
        String questRequirement;
        boolean aggressive;
        boolean poisonous;
        boolean immuneToPoison;
        boolean members;
        String category;
        String wikiUrl;
        int defenceStab;
        int defenceSlash;
        int defenceCrush;
        int defenceMagic;
        int defenceRanged;
        String weakness;
        String attributes;
        String wikiImage;
        String encounterType;
        // NOTE: the JSON also carries a "slayerCategory" per record. It is deliberately left
        // unbound / unconsumed here - a reserved hook for a future slayer-task -> bestiary filter,
        // not dead weight. Do not remove it from the data.
        List<RawSpawnZone> spawnZones;
        List<RawDrop> drops;
    }

    private static class RawSpawnZone
    {
        String zoneName;
        String locationName;
        int minX, minY, maxX, maxY;
        int plane;
        int spawnCount;
        boolean multiCombat;
        int wildernessLevel;
        RawPoint centerPoint;
        RawPoint surfaceEntrance;
        String dungeonName;
        boolean entranceVerified;
    }

    private static class RawPoint
    {
        int x;
        int y;
        int plane;
    }

    private static class RawDrop
    {
        int itemId;
        String name;
        String quantity;
        double rarity;
        String rarityFraction;
        int rolls;
        boolean noted;
        boolean members;
        String category;
    }
}
