package com.osrscopilot.data;

import com.google.gson.Gson;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterDrop;
import com.osrscopilot.data.model.MonsterSpawnRow;
import com.osrscopilot.data.model.MonsterSpawnZone;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MonsterDatabaseTest
{
    private MonsterDatabase database;

    @Before
    public void setUp()
    {
        database = new MonsterDatabase(new Gson());
        database.load();
    }

    @Test
    public void testDatabaseLoad()
    {
        Assert.assertTrue("Database should report isLoaded true", database.isLoaded());
        List<Monster> monsters = database.getAllMonsters();
        Assert.assertNotNull("Monsters list must not be null", monsters);
        Assert.assertTrue("Should load more than 1000 monsters, found: " + monsters.size(), monsters.size() >= 1000);

        Set<String> categories = database.getCategories();
        Assert.assertNotNull("Categories set must not be null", categories);
        Assert.assertTrue("Categories should contain major categories", categories.size() >= 5);
        Assert.assertTrue("Should have Boss category", categories.contains("Boss"));
        Assert.assertTrue("Should have Slayer category", categories.contains("Slayer"));
        Assert.assertTrue("Should have Dragon category", categories.contains("Dragon"));
    }

    @Test
    public void testLookupByIdAndName()
    {
        Monster zulrah = database.getMonsterByName("Zulrah");
        Assert.assertNotNull("Zulrah should be present", zulrah);
        Assert.assertEquals("Zulrah", zulrah.getName());
        Assert.assertEquals(725, zulrah.getCombatLevel());
        Assert.assertEquals(500, zulrah.getHitpoints());
        Assert.assertTrue("Zulrah should have drops", zulrah.hasDrops());
        Assert.assertTrue("Zulrah should have spawn zones", zulrah.hasSpawnZones());

        Monster byId = database.getMonsterById(zulrah.getId());
        Assert.assertEquals("Lookup by ID should match lookup by name", zulrah, byId);

        Monster vorkath = database.getMonsterByName("Vorkath");
        Assert.assertNotNull("Vorkath should be present", vorkath);
        Assert.assertEquals("Boss", vorkath.getCategory());
        Assert.assertTrue(vorkath.getCombatLevel() > 300);

        Monster abyDemon = database.getMonsterByName("Abyssal demon");
        Assert.assertNotNull("Abyssal demon should be present", abyDemon);
        Assert.assertEquals(85, abyDemon.getSlayerLevel());
        Assert.assertEquals("Slayer", abyDemon.getCategory());

        Monster blueDrag = database.getMonsterByName("Blue dragon");
        Assert.assertNotNull("Blue dragon should be present", blueDrag);
        Assert.assertEquals("Dragon", blueDrag.getCategory());

        Monster goblin = database.getMonsterByName("Goblin");
        Assert.assertNotNull("Goblin should be present", goblin);
        Assert.assertEquals("Standard", goblin.getCategory());
        Assert.assertFalse("Goblin should be F2P (members = false)", goblin.isMembers());
    }

    @Test
    public void testNameLookupPrefersRealVariantNotSyntheticSuperVariant()
    {
        // The 4 DT2 bosses ship a synthetic-id "Awakened" record with a higher combat level than
        // the normal fight; the bare-name lookup must return the normal post-quest variant, and the
        // Awakened record must still be reachable via getMonstersByName.
        String[] dt2 = {"Vardorvis", "The Leviathan", "The Whisperer", "Duke Sucellus"};
        for (String n : dt2)
        {
            Monster primary = database.getMonsterByName(n);
            Assert.assertNotNull(n + " must resolve", primary);
            Assert.assertTrue(n + " bare lookup must not be the synthetic super-variant (id "
                + primary.getId() + ")", primary.getId() < 200_000);

            List<Monster> variants = database.getMonstersByName(n);
            Assert.assertTrue(n + " should expose multiple variants", variants.size() >= 2);
            Assert.assertEquals(n + " primary variant must be element 0", primary, variants.get(0));
            Assert.assertTrue(n + " Awakened variant should still be listed",
                variants.stream().anyMatch(m -> m.getId() > 200_000));
        }

        // Post-quest Vorkath (cb 732) beats the quest-fight record (cb 392) for the bare name.
        Monster vorkath = database.getMonsterByName("Vorkath");
        Assert.assertNotNull(vorkath);
        Assert.assertEquals(732, vorkath.getCombatLevel());
        Assert.assertTrue("Vorkath should expose both quest and post-quest variants",
            database.getMonstersByName("Vorkath").size() >= 2);

        // getMonstersByName is null-safe and empty for unknowns.
        Assert.assertTrue(database.getMonstersByName(null).isEmpty());
        Assert.assertTrue(database.getMonstersByName("definitely not a monster").isEmpty());
    }

    @Test
    public void testNpcIdIndexCoversEveryVariantId()
    {
        // Most monsters now carry several real NPC ids (aggressive / idle / HP-tier variants);
        // getMonsterById must resolve every one of them to the same record.
        int withMultiple = 0;
        for (Monster m : database.getAllMonsters())
        {
            Assert.assertNotNull(m.getName() + " npcIds must never be null", m.getNpcIds());
            for (Integer nid : m.getNpcIds())
            {
                Assert.assertTrue(nid + " should be a real (non-synthetic) id", nid > 0 && nid < 200_000);
                Assert.assertSame(m.getName() + " id " + nid + " must resolve to this record",
                    m, database.getMonsterById(nid));
            }
            if (m.getNpcIds().size() > 1)
            {
                withMultiple++;
            }
        }
        Assert.assertTrue("hundreds of monsters should list more than one NPC id", withMultiple > 100);

        // Abyssal demon has ids 415 & 416 - both must reach it.
        Monster aby = database.getMonsterByName("Abyssal demon");
        Assert.assertNotNull(aby);
        Assert.assertTrue("Abyssal demon should list multiple npc ids", aby.getNpcIds().size() >= 2);
        for (Integer nid : aby.getNpcIds())
        {
            Assert.assertEquals("Abyssal demon", database.getMonsterById(nid).getName());
        }
    }

    @Test
    public void testWikiImagePopulated()
    {
        // wikiImage is now wired through from the JSON (was silently always null before).
        Assert.assertEquals("Vorkath.png", database.getMonsterByName("Vorkath").getWikiImage());
        Assert.assertEquals("Abyssal demon.png", database.getMonsterByName("Abyssal demon").getWikiImage());

        int populated = 0;
        for (Monster m : database.getAllMonsters())
        {
            if (m.getWikiImage() != null && !m.getWikiImage().isEmpty())
            {
                populated++;
            }
        }
        Assert.assertTrue("nearly every monster should carry a wiki image filename, found " + populated,
            populated > 1700);
    }

    @Test
    public void testDropItemIdBackfill()
    {
        // The shop-only cache missed drop-table staples (Coins, bones, keys, champion scrolls);
        // the osrsbox gap-fill should now resolve the vast majority.
        int zero = 0;
        int total = 0;
        for (Monster m : database.getAllMonsters())
        {
            for (MonsterDrop d : m.getDrops())
            {
                total++;
                if (d.getItemId() == 0)
                {
                    zero++;
                }
            }
        }
        Assert.assertTrue("total drop rows sanity", total > 20_000);
        Assert.assertTrue("zero-itemId drop rows should be well under 5% after backfill (was ~22%), got "
            + zero + "/" + total, zero * 100 < total * 5);

        // A specific formerly-zero staple now resolves.
        Monster hillGiant = database.getMonsterByName("Hill Giant");
        Assert.assertNotNull(hillGiant);
        MonsterDrop bigBones = hillGiant.getDrops().stream()
            .filter(d -> "Big bones".equalsIgnoreCase(d.getName()))
            .findFirst().orElse(null);
        Assert.assertNotNull("Hill Giant should drop Big bones", bigBones);
        Assert.assertTrue("Big bones should have a real item id now", bigBones.getItemId() > 0);
    }

    @Test
    public void testLinkAndItemIdValidity()
    {
        // 2026-08-31 validity sweep: every wikiUrl well-formed, every drop itemId in the plausible
        // OSRS range, no npc id claimed by two monsters, and the three ids corrected in that sweep.
        java.util.Map<Integer, String> npcIdOwner = new java.util.HashMap<>();
        for (Monster m : database.getAllMonsters())
        {
            String u = m.getWikiUrl();
            Assert.assertNotNull(m.getName() + " wikiUrl", u);
            Assert.assertTrue(m.getName() + " wikiUrl malformed: " + u,
                u.startsWith("https://oldschool.runescape.wiki/w/") && !u.contains(" "));

            for (Integer nid : m.getNpcIds())
            {
                Assert.assertTrue("npc id in range: " + nid, nid > 0 && nid < 200_000);
                String prev = npcIdOwner.put(nid, m.getName());
                Assert.assertNull("npc id " + nid + " claimed by both " + prev + " and " + m.getName(), prev);
            }
            for (MonsterDrop d : m.getDrops())
            {
                Assert.assertTrue(m.getName() + " / " + d.getName() + " itemId " + d.getItemId()
                    + " out of range", d.getItemId() >= 0 && d.getItemId() <= 60_000);
            }
        }

        assertDropId("Grotesque Guardians", "Granite hammer", 21742);
        assertDropId("Grotesque Guardians", "Noon", 21748);
        // Metamorphic dust: Chambers of Xeric tertiary (id was 23348 = Tormented ornament kit).
        boolean found = false;
        for (Monster m : database.getAllMonsters())
        {
            for (MonsterDrop d : m.getDrops())
            {
                if ("Metamorphic dust".equalsIgnoreCase(d.getName()))
                {
                    Assert.assertEquals("Metamorphic dust id", 22386, d.getItemId());
                    found = true;
                }
            }
        }
        Assert.assertTrue("Metamorphic dust drop present somewhere", found);
    }

    private void assertDropId(String monster, String drop, int expectedId)
    {
        Monster m = database.getMonsterByName(monster);
        Assert.assertNotNull(monster, m);
        MonsterDrop d = m.getDrops().stream()
            .filter(x -> drop.equalsIgnoreCase(x.getName())).findFirst().orElse(null);
        Assert.assertNotNull(monster + " should drop " + drop, d);
        Assert.assertEquals(monster + " / " + drop + " item id", expectedId, d.getItemId());
    }

    @Test
    public void testGlobalStatSanityBounds()
    {
        // D6: a regression guard on the known-incomplete-stat set so a bad re-scrape can't quietly
        // blow it out. Current data: cb==0 for ~77, maxHit==0 for ~72, hp==0 for 2 (Gemstone Crab,
        // Yrsa). Every monster must have a real name (D16 latent-NPE guard).
        int cbZero = 0;
        int maxHitZero = 0;
        int hpZero = 0;
        for (Monster m : database.getAllMonsters())
        {
            Assert.assertNotNull("monster name must never be null", m.getName());
            Assert.assertFalse("monster name must never be blank", m.getName().trim().isEmpty());
            if (m.getCombatLevel() == 0)
            {
                cbZero++;
            }
            if (m.getMaxHit() == 0)
            {
                maxHitZero++;
            }
            if (m.getHitpoints() == 0)
            {
                hpZero++;
            }
        }
        Assert.assertTrue("combatLevel==0 count should stay bounded, got " + cbZero, cbZero < 120);
        Assert.assertTrue("maxHit==0 count should stay bounded, got " + maxHitZero, maxHitZero < 120);
        Assert.assertTrue("hitpoints==0 count should stay tiny, got " + hpZero, hpZero <= 5);
    }

    @Test
    public void testDerivedFamilyListsAreCachedAndStable()
    {
        // D3: the family lists were full allMonsters scans recomputed on every keystroke.
        Assert.assertSame(database.getDragons(), database.getDragons());
        Assert.assertSame(database.getBosses(), database.getBosses());
        Assert.assertSame(database.getUndead(), database.getUndead());
        Assert.assertFalse(database.getDragons().isEmpty());
        // and they route through getMonstersByCategory the same way
        Assert.assertEquals(database.getDragons().size(), database.getMonstersByCategory("Dragons").size());
    }

    @Test
    public void testPrefixTokenSearchStillMatchesExpectedMonsters()
    {
        // D3: 2+ char tokens now use a binary-search prefix range; verify common queries still work.
        Assert.assertTrue(database.searchMonstersByName("vork").stream()
            .anyMatch(m -> m.getName().equalsIgnoreCase("Vorkath")));
        Assert.assertTrue(database.searchMonstersByName("aby").stream()
            .anyMatch(m -> m.getName().equalsIgnoreCase("Abyssal demon")));
        Assert.assertTrue(database.searchMonstersByName("blue drag").stream()
            .anyMatch(m -> m.getName().equalsIgnoreCase("Blue dragon")));
        // mid-word query falls back to substring
        Assert.assertFalse(database.searchMonstersByName("iathan").isEmpty());
        // 1-char query stays broad
        Assert.assertTrue(database.searchMonsters("a", "All Categories").size() > 500);
    }

    @Test
    public void testWeaknessAndDefences()
    {
        Monster zulrah = database.getMonsterByName("Zulrah");
        Assert.assertNotNull(zulrah);
        Assert.assertEquals("Fire (50%)", zulrah.getWeakness());   // straight from the wiki, not synthesised
        Assert.assertEquals(-45, zulrah.getDefenceMagic());
        Assert.assertEquals(50, zulrah.getDefenceRanged());

        Monster abyDemon = database.getMonsterByName("Abyssal demon");
        Assert.assertNotNull(abyDemon);
        // Abyssal demons have no elemental weakness; the demonbane hint comes from the real attribute.
        Assert.assertEquals("", abyDemon.getWeakness());
        Assert.assertTrue("Abyssal demon carries the demon attribute", abyDemon.hasAttribute("demon"));

        Monster bloodveld = database.getMonsterByName("Bloodveld");
        Assert.assertNotNull(bloodveld);
        Assert.assertEquals("Bloodveld has no elemental weakness", "", bloodveld.getWeakness());

        java.util.regex.Pattern valid = java.util.regex.Pattern.compile("(Air|Water|Earth|Fire)( \\(\\d+%\\))?");
        for (Monster m : database.getAllMonsters())
        {
            Assert.assertNotNull("Weakness must never be null for " + m.getName(), m.getWeakness());
            String w = m.getWeakness().trim();
            if (!w.isEmpty())
            {
                Assert.assertTrue("Weakness for " + m.getName() + " must be an element or blank, was: " + w,
                    valid.matcher(w).matches());
            }
        }
    }

    @Test
    public void testCategories()
    {
        List<Monster> bosses = database.getBosses();
        Assert.assertFalse("Bosses list should not be empty", bosses.isEmpty());
        Assert.assertTrue("Should have multiple bosses", bosses.size() >= 20);

        List<Monster> slayer = database.getSlayerMonsters();
        Assert.assertFalse("Slayer list should not be empty", slayer.isEmpty());
        Assert.assertTrue("Should have multiple slayer monsters", slayer.size() >= 20);

        List<Monster> dragons = database.getDragons();
        Assert.assertFalse("Dragons list should not be empty", dragons.isEmpty());
        Assert.assertTrue("Should have multiple dragons", dragons.size() >= 10);

        List<Monster> demons = database.getDemons();
        Assert.assertFalse("Demons list should not be empty", demons.isEmpty());
        Assert.assertTrue("Should have multiple demons", demons.size() >= 10);

        List<Monster> undead = database.getUndead();
        Assert.assertFalse("Undead list should not be empty", undead.isEmpty());
        Assert.assertTrue("Should have multiple undead", undead.size() >= 10);

        List<Monster> f2p = database.getF2PMonsters();
        Assert.assertFalse("F2P monsters should not be empty", f2p.isEmpty());
        Assert.assertTrue("Should have multiple F2P monsters", f2p.size() >= 10);

        List<Monster> members = database.getMembersMonsters();
        Assert.assertFalse("Members monsters should not be empty", members.isEmpty());
        Assert.assertTrue("Should have multiple members monsters", members.size() >= 100);
    }

    @Test
    public void testDropSearching()
    {
        List<Monster> whipMonsters = database.searchMonstersByDropName("Abyssal whip");
        Assert.assertNotNull(whipMonsters);
        Assert.assertFalse("Should find monsters dropping whip", whipMonsters.isEmpty());
        boolean hasAbyDemon = whipMonsters.stream().anyMatch(m -> m.getName().equalsIgnoreCase("Abyssal demon"));
        Assert.assertTrue("Abyssal demon should be in whip drop search", hasAbyDemon);

        List<Monster> visageMonsters = database.searchMonstersByDropName("Draconic visage");
        Assert.assertNotNull(visageMonsters);
        Assert.assertFalse("Should find monsters dropping draconic visage", visageMonsters.isEmpty());

        Set<Monster> byItemId = database.getMonstersDroppingItem(4151); // Abyssal whip item id
        Assert.assertFalse("Should find monsters dropping item ID 4151", byItemId.isEmpty());

        // Verify drop formatting
        Monster abyDemon = database.getMonsterByName("Abyssal demon");
        MonsterDrop whipDrop = abyDemon.getDrops().stream()
            .filter(d -> d.getName().equalsIgnoreCase("Abyssal whip"))
            .findFirst()
            .orElse(null);
        Assert.assertNotNull("Abyssal demon must have Abyssal whip in drop table", whipDrop);
        Assert.assertEquals(4151, whipDrop.getItemId());
        Assert.assertEquals("1/512", whipDrop.getRarityFraction());
        Assert.assertEquals("1/512", whipDrop.getFormattedRarity());
    }

    @Test
    public void testDropQuantityAndPerKillRatesAcrossRealData()
    {
        int rangeCount = 0;
        int multiRollChecked = 0;
        for (Monster m : database.getAllMonsters())
        {
            for (MonsterDrop d : m.getDrops())
            {
                int min = d.getMinQuantity();
                int max = d.getMaxQuantity();
                Assert.assertTrue(m.getName() + " / " + d.getName() + " qty '" + d.getQuantity()
                    + "' parsed to [" + min + "," + max + "]", min >= 0 && max >= min);
                if (d.hasQuantityRange())
                {
                    rangeCount++;
                }

                double c = d.getPerKillChance();
                Assert.assertTrue("per-kill chance must be a probability for " + d.getName() + " (" + c + ")",
                    c >= 0.0 && c <= 1.0);
                if (d.getRarity() > 0 && d.getRarity() < 1 && d.getEffectiveRolls() > 1)
                {
                    Assert.assertTrue("rolls should raise the per-kill chance above the per-roll rate for " + d.getName(),
                        c > d.getRarity() - 1e-9);
                    multiRollChecked++;
                }
            }
        }
        Assert.assertTrue("data should contain hundreds of range quantities", rangeCount > 100);
        Assert.assertTrue("data should contain multi-roll drops", multiRollChecked > 0);

        MonsterDrop whip = database.getMonsterByName("Abyssal demon").getDrops().stream()
            .filter(d -> d.getName().equalsIgnoreCase("Abyssal whip"))
            .findFirst()
            .orElse(null);
        Assert.assertNotNull(whip);
        Assert.assertEquals(1, whip.getMinQuantity());
        Assert.assertEquals(1, whip.getMaxQuantity());
        Assert.assertEquals("1/512", whip.getFormattedRarity()); // display unchanged: still per-roll
        Assert.assertEquals(512, whip.getPerKillDenominator());   // rolls == 1 so per-kill == per-roll
    }

    @Test
    public void testChambersOfXericLizardmanShamanVariantHasNoOpenWorldPins()
    {
        // The open-world "Lizardman shaman" (id 6766) is the primary lookup result and is untouched.
        Monster open = database.getMonsterByName("Lizardman shaman");
        Assert.assertNotNull(open);
        Assert.assertEquals(6766, open.getId());
        Assert.assertTrue("open-world Lizardman shaman keeps its spawn zones", open.hasSpawnZones());
        Assert.assertTrue("open-world Lizardman shaman keeps its combat level", open.getCombatLevel() > 0);

        // The CoX-instanced variant (id 7573) shipped stat-less (combatLevel 0) with a copy of the
        // open-world shaman's spawn zones. The loader strips those bogus pins and tags it instanced
        // so it no longer stacks a second pin on every Lizardman zone / shows a dead-end Map button.
        Monster cox = database.getMonsterById(7573);
        Assert.assertNotNull("CoX Lizardman shaman variant still present", cox);
        Assert.assertFalse("CoX Lizardman shaman must not carry open-world spawn zones", cox.hasSpawnZones());
        Assert.assertTrue("CoX Lizardman shaman reads as an instanced encounter", cox.hasEncounterType());
    }

    @Test
    public void testSearchByName()
    {
        List<Monster> searchZul = database.searchMonstersByName("zulrah");
        Assert.assertFalse(searchZul.isEmpty());
        Assert.assertEquals("Zulrah", searchZul.get(0).getName());

        List<Monster> searchDragons = database.searchMonstersByName("blue dragon");
        Assert.assertFalse(searchDragons.isEmpty());
        Assert.assertTrue(searchDragons.stream().anyMatch(m -> m.getName().equalsIgnoreCase("Blue dragon")));

        List<Monster> searchGraardor = database.searchMonstersByName("graardor");
        Assert.assertFalse(searchGraardor.isEmpty());
        Assert.assertEquals("General Graardor", searchGraardor.get(0).getName());
    }

    @Test
    public void testUniversalAndCategorySearch()
    {
        List<Monster> results = database.searchMonsters("Tanzanite fang");
        Assert.assertFalse(results.isEmpty());
        Assert.assertTrue(results.stream().anyMatch(m -> m.getName().equalsIgnoreCase("Zulrah")));

        List<Monster> bossSearch = database.searchMonsters("Boss");
        Assert.assertFalse(bossSearch.isEmpty());

        List<Monster> filteredBoss = database.searchMonsters("Zulrah", "Bosses");
        Assert.assertFalse(filteredBoss.isEmpty());
        Assert.assertEquals("Zulrah", filteredBoss.get(0).getName());

        List<Monster> filteredWrongCat = database.searchMonsters("Zulrah", "F2P");
        Assert.assertTrue(filteredWrongCat.isEmpty());
    }

    @Test
    public void testSpawnZonesAndDungeons()
    {
        Monster abyDemon = database.getMonsterByName("Abyssal demon");
        Assert.assertNotNull(abyDemon);
        Assert.assertTrue(abyDemon.hasSpawnZones());

        MonsterSpawnZone towerZone = abyDemon.getSpawnZones().stream()
            .filter(z -> "Slayer Tower".equalsIgnoreCase(z.getDungeonName()))
            .findFirst()
            .orElse(null);

        Assert.assertNotNull("Abyssal demon should have Slayer Tower spawn zone", towerZone);
        Assert.assertEquals("Slayer Tower", towerZone.getDungeonName());
        Assert.assertEquals(2, towerZone.getPlane());
        Assert.assertNotNull("Surface entrance should be populated", towerZone.getSurfaceEntrance());
        Assert.assertEquals(new WorldPoint(3428, 3535, 0), towerZone.getSurfaceEntrance());
        Assert.assertNotNull("Center point should be populated", towerZone.getCenterPoint());
        Assert.assertEquals(towerZone.getSurfaceEntrance(), towerZone.getEffectiveFocusPoint());

        // Test contains
        Assert.assertTrue(towerZone.contains(3420, 3565, 2));
        Assert.assertFalse(towerZone.contains(3420, 3565, 0)); // wrong plane
        Assert.assertFalse(towerZone.contains(1000, 1000, 2)); // outside

        // Test dungeon lookups
        List<Monster> slayerTowerMonsters = database.getMonstersByDungeon("Slayer Tower");
        Assert.assertFalse("Slayer Tower monsters should not be empty", slayerTowerMonsters.isEmpty());
        Assert.assertTrue(slayerTowerMonsters.stream().anyMatch(m -> m.getName().equalsIgnoreCase("Abyssal demon")));
        Assert.assertTrue(slayerTowerMonsters.stream().anyMatch(m -> m.getName().equalsIgnoreCase("Gargoyle")));
    }

    @Test
    public void testCombatAndSlayerLevelFilters()
    {
        List<Monster> highCombat = database.getMonstersByCombatLevel(300, 1000);
        Assert.assertFalse(highCombat.isEmpty());
        for (Monster m : highCombat)
        {
            Assert.assertTrue(m.getCombatLevel() >= 300 && m.getCombatLevel() <= 1000);
        }

        List<Monster> highSlayer = database.getMonstersBySlayerLevel(80, 99);
        Assert.assertFalse(highSlayer.isEmpty());
        for (Monster m : highSlayer)
        {
            Assert.assertTrue(m.getSlayerLevel() >= 80 && m.getSlayerLevel() <= 99);
        }
    }

    @Test
    public void testSpatialQueries()
    {
        List<Monster> lumbyMonsters = database.getMonstersInArea(3200, 3200, 3300, 3300, 0);
        Assert.assertFalse("Should find monsters in Lumbridge area", lumbyMonsters.isEmpty());
        Assert.assertTrue(lumbyMonsters.stream().anyMatch(m -> m.getName().equalsIgnoreCase("Goblin") || m.getName().equalsIgnoreCase("Cow")));

        Monster cow = database.getMonsterByName("Cow");
        Assert.assertNotNull(cow);
        WorldPoint cowPt = new WorldPoint(3260, 3270, 0);
        List<Monster> atCow = database.getMonstersAtLocation(cowPt);
        Assert.assertTrue("Should find Cow at location", atCow.stream().anyMatch(m -> m.getName().equalsIgnoreCase("Cow")));
    }

    @Test
    public void testClearAndReload()
    {
        database.clear();
        Assert.assertFalse(database.isLoaded());
        Assert.assertEquals(0, database.getMonsterCount());
        Assert.assertNull(database.getMonsterByName("Zulrah"));

        database.load();
        Assert.assertTrue(database.isLoaded());
        Assert.assertTrue(database.getMonsterCount() >= 1000);
        Assert.assertNotNull(database.getMonsterByName("Zulrah"));
    }

    @Test
    public void testFlattenedMonsterSpawnRows()
    {
        List<MonsterSpawnRow> rows = database.getFlattenedMonsterSpawnRows();
        Assert.assertNotNull("Flattened rows list should not be null", rows);
        Assert.assertFalse("Flattened rows list should not be empty", rows.isEmpty());
        Assert.assertTrue("Should have hundreds of flattened spawn rows, found: " + rows.size(), rows.size() >= 500);

        // Verify row data integrity
        for (MonsterSpawnRow row : rows)
        {
            Assert.assertNotNull("Row monster must not be null", row.getMonster());
            Assert.assertNotNull("Row zone must not be null for default getFlattenedMonsterSpawnRows", row.getZone());
            Assert.assertNotNull("Monster name must not be null", row.getMonsterName());
            Assert.assertNotNull("Zone name must not be null", row.getZoneName());
            Assert.assertNotNull("Location name must not be null", row.getLocationName());
            Assert.assertNotNull("WorldPoint must not be null", row.getWorldPoint());
        }

        // Test include monsters without zones
        List<MonsterSpawnRow> allRows = database.getFlattenedMonsterSpawnRows(true);
        Assert.assertTrue("All rows should be >= rows with zones only", allRows.size() >= rows.size());

        // Test searchFlattenedMonsterSpawnRows
        List<MonsterSpawnRow> guardRows = database.searchFlattenedMonsterSpawnRows("Guard", "All", true);
        Assert.assertFalse("Should find Guard spawn rows", guardRows.isEmpty());
        Assert.assertTrue(guardRows.stream().anyMatch(r -> r.getZoneName().toLowerCase().contains("falador") || r.getZoneName().toLowerCase().contains("varrock")));

        List<MonsterSpawnRow> demonRows = database.searchFlattenedMonsterSpawnRows("", "Demons", true);
        Assert.assertFalse("Should find Demon spawn rows", demonRows.isEmpty());
    }

    @Test
    public void testComprehensiveCommonAndSlayerSpawns()
    {
        String[] targets = {
            "Guard", "Man", "Woman", "Wizard", "Knight of Ardougne", "White Knight", "Black Knight",
            "Dark wizard", "Barbarian", "Moss Giant", "Fire giant", "Ice giant", "Hill Giant",
            "Greater demon", "Lesser demon", "Skeleton", "Zombie", "Ghost", "Ankou",
            "Bat", "Giant bat", "Rat", "Giant rat", "Spider", "Giant spider", "Scorpion",
            "King Scorpion", "Wolf", "White wolf", "Black bear", "Grizzly bear",
            "Dwarf", "Bandit", "Druid", "Chaos druid", "Al-Kharid warrior"
        };

        for (String target : targets)
        {
            Monster m = database.getMonsterByName(target);
            if (m == null && target.contains("-"))
            {
                m = database.getMonsterByName(target.replace("-", " "));
            }
            if (m == null)
            {
                List<Monster> search = database.searchMonstersByName(target);
                if (!search.isEmpty()) m = search.get(0);
            }
            Assert.assertNotNull("Target monster '" + target + "' must exist in database", m);
            if (m.hasSpawnZones())
            {
                Assert.assertFalse("Target monster '" + target + "' spawn zones must not be empty", m.getSpawnZones().isEmpty());
                for (MonsterSpawnZone z : m.getSpawnZones())
                {
                    Assert.assertNotNull("Spawn zone name must be present for " + target, z.getZoneName());
                    Assert.assertTrue("MaxX must be >= MinX for " + target, z.getMaxX() >= z.getMinX());
                    Assert.assertTrue("MaxY must be >= MinY for " + target, z.getMaxY() >= z.getMinY());
                    Assert.assertNotNull("Effective focus point must be present", z.getEffectiveFocusPoint());
                }
            }
        }
    }

    @Test
    public void testNoRedundantF2PCategory()
    {
        for (Monster m : database.getAllMonsters())
        {
            Assert.assertNotEquals("No monster should have category 'F2P' (it should be Standard/Boss/etc. with members boolean)",
                "F2P", m.getCategory());
        }
    }

    @Test
    public void testQuestRequirements()
    {
        // Dragon Slayer II
        Monster vorkath = database.getMonsterByName("Vorkath");
        Assert.assertNotNull(vorkath);
        Assert.assertEquals("Dragon Slayer II", vorkath.getQuestRequirement());
        Assert.assertTrue(vorkath.hasQuestRequirement());

        Monster runeDragon = database.getMonsterByName("Rune dragon");
        Assert.assertNotNull(runeDragon);
        Assert.assertEquals("Dragon Slayer II", runeDragon.getQuestRequirement());

        // Monkey Madness II
        Monster demonicGorilla = database.getMonsterByName("Demonic gorilla");
        Assert.assertNotNull(demonicGorilla);
        Assert.assertEquals("Monkey Madness II", demonicGorilla.getQuestRequirement());

        // Secrets of the North
        Monster muspah = database.getMonsterByName("Phantom Muspah");
        Assert.assertNotNull(muspah);
        Assert.assertEquals("Secrets of the North", muspah.getQuestRequirement());

        // Regicide
        Monster zulrah = database.getMonsterByName("Zulrah");
        Assert.assertNotNull(zulrah);
        Assert.assertEquals("Regicide", zulrah.getQuestRequirement());

        // The Fremennik Trials / Exiles
        Monster rex = database.getMonsterByName("Dagannoth Rex");
        Assert.assertNotNull(rex);
        Assert.assertEquals("The Fremennik Trials", rex.getQuestRequirement());

        Monster basilisk = database.getMonsterByName("Basilisk Knight");
        Assert.assertNotNull(basilisk);
        Assert.assertEquals("The Fremennik Exiles", basilisk.getQuestRequirement());

        // Priest in Peril / Morytania / Slayer Tower / Barrows
        Monster gargoyle = database.getMonsterByName("Gargoyle");
        Assert.assertNotNull(gargoyle);
        Assert.assertEquals("Priest in Peril", gargoyle.getQuestRequirement());

        Monster banshee = database.getMonsterByName("Banshee");
        Assert.assertNotNull(banshee);
        Assert.assertEquals("Priest in Peril", banshee.getQuestRequirement());

        Monster ahrim = database.getMonsterByName("Ahrim the Blighted");
        Assert.assertNotNull(ahrim);
        Assert.assertEquals("Priest in Peril", ahrim.getQuestRequirement());

        // God Wars Dungeon
        Monster graardor = database.getMonsterByName("General Graardor");
        Assert.assertNotNull(graardor);
        Assert.assertEquals("Death Plateau / Troll Stronghold", graardor.getQuestRequirement());

        // Varrock Sewers
        Monster scurrius = database.getMonsterByName("Scurrius");
        Assert.assertNotNull(scurrius);
        Assert.assertEquals("Varrock Sewers", scurrius.getQuestRequirement());

        // Slayer & Dungeon requirements
        Monster hydra = database.getMonsterByName("Alchemical Hydra");
        Assert.assertNotNull(hydra);
        Assert.assertEquals("Mount Karuulm", hydra.getQuestRequirement());

        Monster kraken = database.getMonsterByName("Kraken");
        Assert.assertNotNull(kraken);
        Assert.assertEquals("Kraken Cove", kraken.getQuestRequirement());

        Monster horror = database.getMonsterByName("Cave horror");
        Assert.assertNotNull(horror);
        Assert.assertEquals("Cabin Fever", horror.getQuestRequirement());

        Monster ammonite = database.getMonsterByName("Ammonite Crab");
        Assert.assertNotNull(ammonite);
        Assert.assertEquals("Bone Voyage", ammonite.getQuestRequirement());
    }

    @Test
    public void testQuestRequirementSearch()
    {
        List<Monster> ds2Monsters = database.searchMonsters("Dragon Slayer II");
        Assert.assertFalse("Search for Dragon Slayer II should find monsters", ds2Monsters.isEmpty());
        Assert.assertTrue(ds2Monsters.stream().anyMatch(m -> m.getName().equalsIgnoreCase("Vorkath")));
        Assert.assertTrue(ds2Monsters.stream().anyMatch(m -> m.getName().equalsIgnoreCase("Rune dragon")));

        List<Monster> mm2Monsters = database.searchMonsters("Monkey Madness II");
        Assert.assertFalse("Search for Monkey Madness II should find monsters", mm2Monsters.isEmpty());
        Assert.assertTrue(mm2Monsters.stream().anyMatch(m -> m.getName().equalsIgnoreCase("Demonic gorilla")));

        List<Monster> pipMonsters = database.searchMonsters("Priest in Peril");
        Assert.assertFalse("Search for Priest in Peril should find monsters", pipMonsters.isEmpty());
        Assert.assertTrue(pipMonsters.stream().anyMatch(m -> m.getName().equalsIgnoreCase("Gargoyle") || m.getName().equalsIgnoreCase("Banshee") || m.getName().equalsIgnoreCase("Ahrim the Blighted")));
    }

    @Test
    public void testMonkeyZombieSpawnLocations()
    {
        Monster mz = database.getMonsterByName("Monkey Zombie");
        Assert.assertNotNull("Monkey Zombie must exist in database", mz);
        Assert.assertTrue("Monkey Zombie must have spawn zones", mz.hasSpawnZones());
        List<MonsterSpawnZone> zones = mz.getSpawnZones();
        Assert.assertEquals("Monkey Zombie must have 3 spawn zones", 3, zones.size());

        // 1. Ape Atoll Dungeon (Temple of Marim basement) - recentred on the real spawn cluster
        MonsterSpawnZone dungeonZone = zones.stream()
            .filter(z -> z.getZoneName().contains("Ape Atoll Dungeon") || z.getLocationName().contains("Ape Atoll Dungeon"))
            .findFirst()
            .orElse(null);
        Assert.assertNotNull("Ape Atoll Dungeon spawn zone should be present", dungeonZone);
        Assert.assertEquals(new WorldPoint(2785, 9199, 0), dungeonZone.getZoneCenter());
        Assert.assertEquals("Ape Atoll Dungeon", dungeonZone.getDungeonName());

        // 2. Marim Temple [2800, 2785, 0]
        MonsterSpawnZone templeZone = zones.stream()
            .filter(z -> z.getZoneName().contains("Marim Temple") || z.getLocationName().contains("Marim Temple"))
            .findFirst()
            .orElse(null);
        Assert.assertNotNull("Marim Temple spawn zone should be present", templeZone);
        Assert.assertEquals(new WorldPoint(2800, 2785, 0), templeZone.getZoneCenter());

        // 3. Ape Atoll surface [2760, 2750, 0]
        MonsterSpawnZone surfaceZone = zones.stream()
            .filter(z -> z.getZoneName().contains("Ape Atoll surface") || z.getLocationName().contains("Ape Atoll surface"))
            .findFirst()
            .orElse(null);
        Assert.assertNotNull("Ape Atoll surface spawn zone should be present", surfaceZone);
        Assert.assertEquals(new WorldPoint(2760, 2750, 0), surfaceZone.getZoneCenter());
    }

    @Test
    public void testAllBossLocationsAndCoordinates()
    {
        // 1. Zulrah: Poison Waste [2268, 3072, 0] (surface: Zul-Andra boat [2211, 3056, 0])
        Monster zulrah = database.getMonsterByName("Zulrah");
        Assert.assertNotNull("Zulrah should exist", zulrah);
        Assert.assertEquals(new WorldPoint(2268, 3072, 0), zulrah.getSpawnZones().get(0).getZoneCenter());
        Assert.assertEquals(new WorldPoint(2211, 3056, 0), zulrah.getSpawnZones().get(0).getSurfaceEntrance());

        // 2. Vorkath: Ungael [2269, 4062, 0] - wiki + game-cache agree (was ~14 tiles off)
        Monster vorkath = database.getMonsterByName("Vorkath");
        Assert.assertNotNull("Vorkath should exist", vorkath);
        Assert.assertEquals(new WorldPoint(2269, 4062, 0), vorkath.getSpawnZones().get(0).getZoneCenter());
        Assert.assertEquals(new WorldPoint(2643, 3697, 0), vorkath.getSpawnZones().get(0).getSurfaceEntrance());

        // 3. Phantom Muspah: Ghorrock Prison (surface: Ghorrock fortress [2915, 3935, 0] - verified: wiki Ghorrock infobox {{Map}})
        Monster muspah = database.getMonsterByName("Phantom Muspah");
        Assert.assertNotNull("Phantom Muspah should exist", muspah);
        Assert.assertEquals(new WorldPoint(2909, 10317, 0), muspah.getSpawnZones().get(0).getZoneCenter()); // wiki locline
        Assert.assertEquals(new WorldPoint(2915, 3935, 0), muspah.getSpawnZones().get(0).getSurfaceEntrance());

        // 4. Cerberus: Taverley Dungeon Cerberus Lairs [1240, 1250, 0], [1304, 1250, 0], [1368, 1250, 0]
        Monster cerberus = database.getMonsterByName("Cerberus");
        Assert.assertNotNull("Cerberus should exist", cerberus);
        Assert.assertEquals(3, cerberus.getSpawnZones().size());
        Assert.assertEquals(new WorldPoint(1240, 1250, 0), cerberus.getSpawnZones().get(0).getZoneCenter());
        Assert.assertEquals(new WorldPoint(1304, 1250, 0), cerberus.getSpawnZones().get(1).getZoneCenter());
        Assert.assertEquals(new WorldPoint(1368, 1250, 0), cerberus.getSpawnZones().get(2).getZoneCenter());

        // 5. Alchemical Hydra: Mount Karuulm Lower Sanctum [1356, 10260, 0]
        Monster hydra = database.getMonsterByName("Alchemical Hydra");
        Assert.assertNotNull("Alchemical Hydra should exist", hydra);
        Assert.assertEquals(new WorldPoint(1356, 10260, 0), hydra.getSpawnZones().get(0).getZoneCenter());
        Assert.assertEquals(new WorldPoint(1311, 3807, 0), hydra.getSpawnZones().get(0).getSurfaceEntrance());

        // 6. Kraken: Kraken Cove [2278, 10034, 0] - the whirlpool fight tiles (wiki + cache); the old
        // (2278,10016) was ~18 tiles south at the cove mouth.
        Monster kraken = database.getMonsterByName("Kraken");
        Assert.assertNotNull("Kraken should exist", kraken);
        Assert.assertEquals(new WorldPoint(2278, 10034, 0), kraken.getSpawnZones().get(0).getZoneCenter());
        Assert.assertEquals(new WorldPoint(2278, 3611, 0), kraken.getSpawnZones().get(0).getSurfaceEntrance()); // verified: wiki Kraken Cove {{Map}}

        // 7. Thermonuclear smoke devil: Smoke Devil Dungeon [2376, 9452, 0]
        Monster smokeDevil = database.getMonsterByName("Thermonuclear smoke devil");
        Assert.assertNotNull("Thermonuclear smoke devil should exist", smokeDevil);
        Assert.assertEquals(new WorldPoint(2360, 9452, 0), smokeDevil.getSpawnZones().get(0).getZoneCenter());
        Assert.assertEquals(new WorldPoint(2411, 3060, 0), smokeDevil.getSpawnZones().get(0).getSurfaceEntrance());

        // 8. Abyssal Sire: Abyssal Nexus [3038, 4774, 0]
        Monster sire = database.getMonsterByName("Abyssal Sire");
        Assert.assertNotNull("Abyssal Sire should exist", sire);
        Assert.assertEquals(new WorldPoint(3038, 4774, 0), sire.getSpawnZones().get(0).getZoneCenter());

        // 9. Grotesque Guardians / Dusk / Dawn: Slayer Tower Roof [3428, 3542, 2]
        Monster dusk = database.getMonsterByName("Dusk");
        Assert.assertNotNull("Dusk should exist", dusk);
        Assert.assertEquals(new WorldPoint(3428, 3542, 2), dusk.getSpawnZones().get(0).getZoneCenter());

        Monster dawn = database.getMonsterByName("Dawn");
        Assert.assertNotNull("Dawn should exist", dawn);
        Assert.assertEquals(new WorldPoint(3428, 3542, 2), dawn.getSpawnZones().get(0).getZoneCenter());

        Monster gg = database.getMonsterByName("Grotesque Guardians");
        Assert.assertNotNull("Grotesque Guardians should exist", gg);
        Assert.assertEquals(new WorldPoint(3428, 3542, 2), gg.getSpawnZones().get(0).getZoneCenter());

        // 10. Barrows brothers: Barrows Mounds [3565, 3290, 0]
        Monster ahrim = database.getMonsterByName("Ahrim the Blighted");
        Assert.assertNotNull("Ahrim should exist", ahrim);
        Assert.assertEquals(new WorldPoint(3565, 3290, 0), ahrim.getSpawnZones().get(0).getZoneCenter());

        // 11. Scurrius: Varrock Sewers Rat Pit [3298, 9866, 0]
        Monster scurrius = database.getMonsterByName("Scurrius");
        Assert.assertNotNull("Scurrius should exist", scurrius);
        Assert.assertEquals(new WorldPoint(3298, 9866, 0), scurrius.getSpawnZones().get(0).getZoneCenter());
        Assert.assertEquals(new WorldPoint(3237, 3458, 0), scurrius.getSpawnZones().get(0).getSurfaceEntrance());

        // 12. Duke Sucellus: Lassar Undercity [3036, 6384, 0]
        Monster duke = database.getMonsterByName("Duke Sucellus");
        Assert.assertNotNull("Duke Sucellus should exist", duke);
        Assert.assertEquals(new WorldPoint(3036, 6384, 0), duke.getSpawnZones().get(0).getZoneCenter());

        // 13. Vardorvis: Stranglewood Temple [1128, 3418, 0]
        Monster vard = database.getMonsterByName("Vardorvis");
        Assert.assertNotNull("Vardorvis should exist", vard);
        Assert.assertEquals(new WorldPoint(1128, 3418, 0), vard.getSpawnZones().get(0).getZoneCenter());

        // 14. The Leviathan: The Scar [2068, 6384, 0]
        Monster leviathan = database.getMonsterByName("The Leviathan");
        Assert.assertNotNull("The Leviathan should exist", leviathan);
        Assert.assertEquals(new WorldPoint(2081, 6372, 0), leviathan.getSpawnZones().get(0).getZoneCenter());

        // 15. The Whisperer: Lassar Catacombs [2648, 6424, 0]
        Monster whisperer = database.getMonsterByName("The Whisperer");
        Assert.assertNotNull("The Whisperer should exist", whisperer);
        Assert.assertEquals(new WorldPoint(2648, 6424, 0), whisperer.getSpawnZones().get(0).getZoneCenter());

        // 16. King Black Dragon: KBD Lair [2271, 4699, 0] (surface: Lava Maze ladder [3067, 3865, 0])
        Monster kbd = database.getMonsterByName("King Black Dragon");
        Assert.assertNotNull("King Black Dragon should exist", kbd);
        Assert.assertEquals(new WorldPoint(2271, 4699, 0), kbd.getSpawnZones().get(0).getZoneCenter());
        Assert.assertEquals(new WorldPoint(3067, 3865, 0), kbd.getSpawnZones().get(0).getSurfaceEntrance());

        // 17. Giant Mole: Falador Mole Lair [1760, 5180, 0]
        Monster mole = database.getMonsterByName("Giant Mole");
        Assert.assertNotNull("Giant Mole should exist", mole);
        Assert.assertEquals(new WorldPoint(1760, 5180, 0), mole.getSpawnZones().get(0).getZoneCenter());
        Assert.assertEquals(new WorldPoint(2996, 3376, 0), mole.getSpawnZones().get(0).getSurfaceEntrance());

        // 18. Dagannoth Kings: Waterbirth Dungeon [2900, 4449, 0]
        Monster rex = database.getMonsterByName("Dagannoth Rex");
        Assert.assertNotNull("Dagannoth Rex should exist", rex);
        Assert.assertEquals(new WorldPoint(2913, 4445, 0), rex.getSpawnZones().get(0).getZoneCenter());

        // 19. General Graardor: God Wars Dungeon [2860, 5354, 2]
        // Surface entrance = the Trollheim boulder crevice; verified against the OSRS Wiki
        // GWD entrance pin (was (2882,3710), ~50t SW).
        Monster graardor = database.getMonsterByName("General Graardor");
        Assert.assertNotNull("General Graardor should exist", graardor);
        Assert.assertEquals(new WorldPoint(2872, 5358, 2), graardor.getSpawnZones().get(0).getZoneCenter());
        Assert.assertEquals(new WorldPoint(2918, 3745, 0), graardor.getSpawnZones().get(0).getSurfaceEntrance());

        // 20. Corporeal Beast: Corporeal Beast Lair [2966, 4382, 2]
        Monster corp = database.getMonsterByName("Corporeal Beast");
        Assert.assertNotNull("Corporeal Beast should exist", corp);
        Assert.assertEquals(new WorldPoint(2993, 4382, 2), corp.getSpawnZones().get(0).getZoneCenter());
        Assert.assertEquals(new WorldPoint(3206, 3681, 0), corp.getSpawnZones().get(0).getSurfaceEntrance());

        // 21. Kalphite Queen: Kalphite Lair [3480, 9510, 0]
        Monster kq = database.getMonsterByName("Kalphite Queen");
        Assert.assertNotNull("Kalphite Queen should exist", kq);
        Assert.assertEquals(new WorldPoint(3474, 9496, 0), kq.getSpawnZones().get(0).getZoneCenter());
        Assert.assertEquals(new WorldPoint(3226, 3108, 0), kq.getSpawnZones().get(0).getSurfaceEntrance());

        // 22. Wilderness Bosses
        Monster callisto = database.getMonsterByName("Callisto");
        Assert.assertNotNull("Callisto should exist", callisto);
        Assert.assertEquals(new WorldPoint(3290, 3847, 0), callisto.getSpawnZones().get(0).getZoneCenter());

        Monster venenatis = database.getMonsterByName("Venenatis");
        Assert.assertNotNull("Venenatis should exist", venenatis);
        Assert.assertEquals(new WorldPoint(3332, 3734, 0), venenatis.getSpawnZones().get(0).getZoneCenter());

        Monster vetion = database.getMonsterByName("Vet'ion");
        Assert.assertNotNull("Vet'ion should exist", vetion);
        Assert.assertEquals(new WorldPoint(3220, 3788, 0), vetion.getSpawnZones().get(0).getZoneCenter());

        Monster chaosEle = database.getMonsterByName("Chaos Elemental");
        Assert.assertNotNull("Chaos Elemental should exist", chaosEle);
        Assert.assertEquals(new WorldPoint(3261, 3927, 0), chaosEle.getSpawnZones().get(0).getZoneCenter());

        Monster scorpia = database.getMonsterByName("Scorpia");
        Assert.assertNotNull("Scorpia should exist", scorpia);
        Assert.assertEquals(new WorldPoint(3233, 10335, 0), scorpia.getSpawnZones().get(0).getZoneCenter());
        Assert.assertEquals(new WorldPoint(3233, 3940, 0), scorpia.getSpawnZones().get(0).getSurfaceEntrance());

        Monster fanatic = database.getMonsterByName("Chaos Fanatic");
        Assert.assertNotNull("Chaos Fanatic should exist", fanatic);
        Assert.assertEquals(new WorldPoint(2981, 3836, 0), fanatic.getSpawnZones().get(0).getZoneCenter());

        Monster crazyArch = database.getMonsterByName("Crazy archaeologist");
        Assert.assertNotNull("Crazy archaeologist should exist", crazyArch);
        Assert.assertEquals(new WorldPoint(2977, 3702, 0), crazyArch.getSpawnZones().get(0).getZoneCenter());

        // 23. Sarachnis: Forthos Dungeon [1842, 9900, 0]
        Monster sarachnis = database.getMonsterByName("Sarachnis");
        Assert.assertNotNull("Sarachnis should exist", sarachnis);
        Assert.assertEquals(new WorldPoint(1842, 9900, 0), sarachnis.getSpawnZones().get(0).getZoneCenter());
        Assert.assertEquals(new WorldPoint(1700, 3574, 0), sarachnis.getSpawnZones().get(0).getSurfaceEntrance());

        // 24. Skotizo: Catacombs of Kourend [1690, 9880, 0]
        Monster skotizo = database.getMonsterByName("Skotizo");
        Assert.assertNotNull("Skotizo should exist", skotizo);
        Assert.assertEquals(new WorldPoint(1690, 9880, 0), skotizo.getSpawnZones().get(0).getZoneCenter());

        // 25. Hespori: Farming Guild [1244, 10078, 0]
        Monster hespori = database.getMonsterByName("Hespori");
        Assert.assertNotNull("Hespori should exist", hespori);
        Assert.assertEquals(new WorldPoint(1244, 10078, 0), hespori.getSpawnZones().get(0).getZoneCenter());

        // 26. Obor: Edgeville Dungeon Hill Giant Boss [3095, 9830, 0]
        Monster obor = database.getMonsterByName("Obor");
        Assert.assertNotNull("Obor should exist", obor);
        Assert.assertEquals(new WorldPoint(3095, 9830, 0), obor.getSpawnZones().get(0).getZoneCenter());

        // 27. Bryophyta: Varrock Sewers Moss Giant Boss [3174, 9898, 0]
        Monster bryo = database.getMonsterByName("Bryophyta");
        Assert.assertNotNull("Bryophyta should exist", bryo);
        Assert.assertEquals(new WorldPoint(3174, 9898, 0), bryo.getSpawnZones().get(0).getZoneCenter());

        // 28. Sol Heredit: Fortis Colosseum [1824, 3110, 0]
        Monster sol = database.getMonsterByName("Sol Heredit");
        Assert.assertNotNull("Sol Heredit should exist", sol);
        Assert.assertEquals(new WorldPoint(1824, 3110, 0), sol.getSpawnZones().get(0).getZoneCenter());

        // 29. Blood / Blue / Eclipse Moon: Cam Torum Moons of Peril [1440, 9660, 0]
        Monster bloodMoon = database.getMonsterByName("Blood Moon");
        Assert.assertNotNull("Blood Moon should exist", bloodMoon);
        Assert.assertEquals(new WorldPoint(1440, 9660, 0), bloodMoon.getSpawnZones().get(0).getZoneCenter());

        Monster blueMoon = database.getMonsterByName("Blue Moon");
        Assert.assertNotNull("Blue Moon should exist", blueMoon);
        Assert.assertEquals(new WorldPoint(1440, 9680, 0), blueMoon.getSpawnZones().get(0).getZoneCenter());

        Monster eclipseMoon = database.getMonsterByName("Eclipse Moon");
        Assert.assertNotNull("Eclipse Moon should exist", eclipseMoon);
        Assert.assertEquals(new WorldPoint(1440, 9660, 0), eclipseMoon.getSpawnZones().get(0).getZoneCenter());

        // 30. TzHaar-Jad / TzTok-Jad: Fight Caves [2440, 5170, 0] / TzKal-Zuk: Inferno [2271, 5350, 0]
        Monster jad = database.getMonsterByName("TzTok-Jad");
        Assert.assertNotNull("TzTok-Jad should exist", jad);
        Assert.assertEquals(new WorldPoint(2440, 5170, 0), jad.getSpawnZones().get(0).getZoneCenter());

        Monster tzhaarJad = database.getMonsterByName("TzHaar-Jad");
        Assert.assertNotNull("TzHaar-Jad should exist", tzhaarJad);
        Assert.assertEquals(new WorldPoint(2440, 5170, 0), tzhaarJad.getSpawnZones().get(0).getZoneCenter());

        Monster zuk = database.getMonsterByName("TzKal-Zuk");
        Assert.assertNotNull("TzKal-Zuk should exist", zuk);
        Assert.assertEquals(new WorldPoint(2271, 5350, 0), zuk.getSpawnZones().get(0).getZoneCenter());
    }

    @Test
    public void testGeographicWildernessStrictness()
    {
        int totalInspected = 0;
        for (Monster m : database.getAllMonsters())
        {
            if (m.hasSpawnZones())
            {
                for (MonsterSpawnZone z : m.getSpawnZones())
                {
                    totalInspected++;
                    int minX = z.getMinX();
                    int maxX = z.getMaxX();
                    int minY = z.getMinY();
                    int maxY = z.getMaxY();
                    int plane = z.getPlane();
                    int wildy = z.getWildernessLevel();
                    String dungeon = z.getDungeonName() != null ? z.getDungeonName().toLowerCase() : "";

                    if (wildy > 0)
                    {
                        boolean isSurfaceWildy = (minX >= 2944 && maxX <= 3392 && minY >= 3520 && plane == 0);
                        boolean isUndergroundWildy = dungeon.contains("revenant")
                            || dungeon.contains("wilderness")
                            || dungeon.contains("scorpia")
                            || dungeon.contains("artio")
                            || dungeon.contains("spindel")
                            || dungeon.contains("calvar")
                            || (minX >= 3120 && maxX <= 3280 && minY >= 10050 && maxY <= 10260)
                            || (minX >= 3360 && maxX <= 3440 && minY >= 10040 && maxY <= 10160)
                            || (minX >= 3020 && maxX <= 3090 && minY >= 10300 && maxY <= 10380)
                            || (minX >= 3000 && maxX <= 3080 && minY >= 10090 && maxY <= 10180)
                            || (minX >= 3210 && maxX <= 3260 && minY >= 10310 && maxY <= 10360)
                            || (minX >= 1740 && maxX <= 1780 && minY >= 11530 && maxY <= 11570)
                            || (minX >= 1600 && maxX <= 1640 && minY >= 11530 && maxY <= 11570)
                            || (minX >= 1860 && maxX <= 1900 && minY >= 11530 && maxY <= 11570)
                            || (minX >= 3080 && maxX <= 3150 && minY >= 9950 && maxY <= 10020 && plane == 0);

                        Assert.assertTrue(
                            "Wilderness level > 0 only allowed in surface wilderness or known wilderness dungeons! Violator: "
                                + m.getName() + " zone: " + z.getZoneName() + " (" + minX + "," + minY + " to " + maxX + "," + maxY + ")",
                            isSurfaceWildy || isUndergroundWildy);
                        Assert.assertTrue("Wilderness level must be between 1 and 56", wildy >= 1 && wildy <= 56);
                    }
                    else
                    {
                        // Ensure Kourend, Varlamore, Misthalin, Asgarnia, Fremennik, Morytania, Desert, Karamja, Tirannwn are strictly 0
                        boolean isKourend = (minX >= 1200 && maxX <= 1850 && minY >= 3400 && maxY <= 4000);
                        boolean isVarlamore = (minX >= 1300 && maxX <= 1850 && minY >= 2900 && maxY <= 3350);
                        boolean isMorytania = (minX >= 3400 && maxX <= 3800 && minY >= 3150 && maxY <= 3600);
                        if (isKourend || isVarlamore || isMorytania)
                        {
                            Assert.assertEquals(
                                "Safe kingdom area must have wilderness level 0! Failed for " + m.getName() + " zone: " + z.getZoneName(),
                                0, wildy);
                        }
                    }
                }
            }
        }
        Assert.assertTrue("Must have inspected thousands of spawn zones", totalInspected >= 1000);
    }

    @Test
    public void testDungeonEntranceDatabaseSurfaceCoordinates()
    {
        DungeonEntranceDatabase db = new DungeonEntranceDatabase();

        // 1. Catacombs of Kourend [1636, 3673, 0]
        WorldPoint cataPt = db.getSurfaceEntrance(new WorldPoint(1650, 10000, 0));
        Assert.assertNotNull(cataPt);
        Assert.assertEquals(new WorldPoint(1636, 3673, 0), cataPt);

        // 2. Taverley Dungeon [2884, 3396, 0]
        WorldPoint tavPt = db.getSurfaceEntrance(new WorldPoint(2884, 9800, 0));
        Assert.assertNotNull(tavPt);
        Assert.assertEquals(new WorldPoint(2884, 3396, 0), tavPt);

        // 3. Edgeville Dungeon [3096, 3468, 0]
        WorldPoint edgePt = db.getSurfaceEntrance(new WorldPoint(3100, 9900, 0));
        Assert.assertNotNull(edgePt);
        Assert.assertEquals(new WorldPoint(3096, 3468, 0), edgePt);

        // 4. Varrock Sewers [3237, 3458, 0]
        WorldPoint varPt = db.getSurfaceEntrance(new WorldPoint(3200, 9900, 0));
        Assert.assertNotNull(varPt);
        Assert.assertEquals(new WorldPoint(3237, 3458, 0), varPt);

        // 5. God Wars Dungeon [2918, 3745, 0] - verified against the OSRS Wiki entrance pin
        WorldPoint gwdPt = db.getSurfaceEntrance(new WorldPoint(2872, 5358, 2));
        Assert.assertNotNull(gwdPt);
        Assert.assertEquals(new WorldPoint(2918, 3745, 0), gwdPt);

        // 6. Forthos Dungeon [1700, 3574, 0]
        WorldPoint forthosPt = db.getSurfaceEntrance(new WorldPoint(1800, 9950, 0));
        Assert.assertNotNull(forthosPt);
        Assert.assertEquals(new WorldPoint(1700, 3574, 0), forthosPt);

        // 7. Mount Karuulm [1311, 3807, 0]
        WorldPoint karuulmPt = db.getSurfaceEntrance(new WorldPoint(1300, 10200, 0));
        Assert.assertNotNull(karuulmPt);
        Assert.assertEquals(new WorldPoint(1311, 3807, 0), karuulmPt);

        // 8. Slayer Tower Basement [3428, 3535, 0]
        WorldPoint slayerBasePt = db.getSurfaceEntrance(new WorldPoint(3420, 9950, 0));
        Assert.assertNotNull(slayerBasePt);
        Assert.assertEquals(new WorldPoint(3428, 3535, 0), slayerBasePt);

        // 9. Scorpia Pit [3233, 3940, 0]
        WorldPoint scorpPt = db.getSurfaceEntrance(new WorldPoint(3233, 10335, 0));
        Assert.assertNotNull(scorpPt);
        Assert.assertEquals(new WorldPoint(3233, 3940, 0), scorpPt);

        // 10. Revenant Caves - verified: wiki Crevice (Revenant Caves) {{Map|mtype=pin}} (the only entrance since 2021)
        WorldPoint revPt = db.getSurfaceEntrance(new WorldPoint(3200, 10150, 0));
        Assert.assertNotNull(revPt);
        Assert.assertEquals(new WorldPoint(3067, 3740, 0), revPt);

        // 11. Cam Torum [1440, 3100, 0]
        WorldPoint camPt = db.getSurfaceEntrance(new WorldPoint(1440, 9660, 0));
        Assert.assertNotNull(camPt);
        Assert.assertEquals(new WorldPoint(1440, 3100, 0), camPt);

        // 12. KBD Lair [3067, 3865, 0]
        WorldPoint kbdPt = db.getSurfaceEntrance(new WorldPoint(2271, 4699, 0));
        Assert.assertNotNull(kbdPt);
        Assert.assertEquals(new WorldPoint(3067, 3865, 0), kbdPt);

        // 13. Lighthouse basement (Dagannoth spawn ~2523,10023) -> the surface Lighthouse, so the
        //     Map button pans there instead of an unshowable underground point.
        WorldPoint lighthousePt = db.getSurfaceEntrance(new WorldPoint(2523, 10023, 0));
        Assert.assertNotNull("Lighthouse basement must resolve to a surface entrance", lighthousePt);
        Assert.assertEquals(new WorldPoint(2509, 3641, 0), lighthousePt);

        // 14. Dorgesh-Kaan South Dungeon (Molanisk ~2725,5213) - its own entrance value (2715,5280)
        //     was itself underground; the DB now points at the Lumbridge Swamp Caves.
        WorldPoint dkSouthPt = db.getSurfaceEntrance(new WorldPoint(2725, 5213, 0));
        Assert.assertNotNull("Dorgesh-Kaan South Dungeon must resolve to a surface entrance", dkSouthPt);
        Assert.assertEquals(new WorldPoint(3169, 3172, 0), dkSouthPt);
    }

    /**
     * As of the 2026-08-20 data-quality pass, instanced/NMZ-variant bosses are deliberately given
     * ONE spawn zone that marks the instance's surface entrance (spawnCount=0, zoneName ending in
     * "Entrance") so the UI's Map/Focus button has somewhere to point — they must still never have
     * a real open-world roaming spawn (spawnCount &gt; 0, or a zone not flagged as an entrance).
     */
    private static boolean hasOnlyEntranceMarkerZones(Monster m)
    {
        if (!m.hasSpawnZones())
        {
            return true;
        }
        for (MonsterSpawnZone z : m.getSpawnZones())
        {
            if (z.getSpawnCount() > 0 || z.getZoneName() == null || !z.getZoneName().contains("Entrance"))
            {
                return false;
            }
        }
        return true;
    }

    @Test
    public void testPurgedNightmareZoneAndVariantSpawns()
    {
        Monster agrithHard = database.getMonsterByName("Agrith Naar (hard)");
        if (agrithHard != null)
        {
            Assert.assertTrue("Agrith Naar (hard) must not have an open-world spawn", hasOnlyEntranceMarkerZones(agrithHard));
        }

        Monster kamilHard = database.getMonsterByName("Kamil (hard)");
        if (kamilHard != null)
        {
            Assert.assertTrue("Kamil (hard) must not have an open-world spawn", hasOnlyEntranceMarkerZones(kamilHard));
        }

        Monster damisHard = database.getMonsterByName("Damis (hard)");
        if (damisHard != null)
        {
            Assert.assertTrue("Damis (hard) must not have an open-world spawn", hasOnlyEntranceMarkerZones(damisHard));
        }

        Monster treeSpiritHard = database.getMonsterByName("Tree spirit (hard)");
        if (treeSpiritHard != null)
        {
            Assert.assertTrue("Tree spirit (hard) must not have an open-world spawn", hasOnlyEntranceMarkerZones(treeSpiritHard));
        }

        Monster cowHard = database.getMonsterByName("Cow (hard)");
        if (cowHard != null)
        {
            Assert.assertTrue("Cow (hard) must not have an open-world spawn", hasOnlyEntranceMarkerZones(cowHard));
        }

        Monster skelHellhoundHard = database.getMonsterByName("Skeleton Hellhound (hard)");
        if (skelHellhoundHard != null)
        {
            Assert.assertTrue("Skeleton Hellhound (hard) must not have an open-world spawn", hasOnlyEntranceMarkerZones(skelHellhoundHard));
        }

        for (Monster m : database.getAllMonsters())
        {
            String nameLower = m.getName().toLowerCase();
            if (nameLower.contains("(hard)") || nameLower.contains("(nightmare zone)") || nameLower.contains("(historical)"))
            {
                Assert.assertTrue("Variant/NMZ boss " + m.getName() + " must not have an open-world spawn", hasOnlyEntranceMarkerZones(m));
            }
        }
    }

    @Test
    public void testQuestBossDungeonSpawnsStrict()
    {
        // 1. Agrith-Naar -> Uzer Golem Shrine (NOT Ruins of Unkah!)
        Monster agrith = database.getMonsterByName("Agrith Naar");
        if (agrith == null)
        {
            agrith = database.getMonsterByName("Agrith-Naar");
        }
        Assert.assertNotNull("Agrith-Naar must exist", agrith);
        Assert.assertTrue("Agrith-Naar must have spawn zones", agrith.hasSpawnZones());
        Assert.assertEquals("Uzer Golem Shrine", agrith.getSpawnZones().get(0).getDungeonName());
        Assert.assertEquals("Ruins of Uzer", agrith.getSpawnZones().get(0).getLocationName());
        Assert.assertEquals(0, agrith.getSpawnZones().get(0).getWildernessLevel());
        Assert.assertFalse("Agrith-Naar must NOT spawn at Ruins of Unkah",
            agrith.getSpawnZones().get(0).getLocationName().contains("Unkah"));

        // 2. Kamil -> Ice Path (NOT Lumbridge!)
        Monster kamil = database.getMonsterByName("Kamil");
        Assert.assertNotNull("Kamil must exist", kamil);
        Assert.assertTrue("Kamil must have spawn zones", kamil.hasSpawnZones());
        Assert.assertEquals("Ice Path", kamil.getSpawnZones().get(0).getDungeonName());
        Assert.assertFalse("Kamil must NOT spawn at Lumbridge",
            kamil.getSpawnZones().get(0).getLocationName().contains("Lumbridge"));

        // 3. Dessourt / Dessous -> Mort Myre Graveyard
        Monster dessous = database.getMonsterByName("Dessourt");
        if (dessous == null)
        {
            dessous = database.getMonsterByName("Dessous");
        }
        Assert.assertNotNull("Dessourt/Dessous must exist", dessous);
        Assert.assertTrue("Dessourt must have spawn zones", dessous.hasSpawnZones());
        Assert.assertEquals("Mort Myre Graveyard", dessous.getSpawnZones().get(0).getLocationName());
        Assert.assertFalse("Dessourt must NOT spawn at Lumbridge",
            dessous.getSpawnZones().get(0).getLocationName().contains("Lumbridge"));

        // 4. Damis -> Shadow Dungeon
        Monster damis = database.getMonsterByName("Damis");
        Assert.assertNotNull("Damis must exist", damis);
        Assert.assertTrue("Damis must have spawn zones", damis.hasSpawnZones());
        Assert.assertEquals("Shadow Dungeon", damis.getSpawnZones().get(0).getDungeonName());

        // 5. Fareed -> Smoke Dungeon
        Monster fareed = database.getMonsterByName("Fareed");
        Assert.assertNotNull("Fareed must exist", fareed);
        Assert.assertTrue("Fareed must have spawn zones", fareed.hasSpawnZones());
        Assert.assertEquals("Smoke Dungeon", fareed.getSpawnZones().get(0).getDungeonName());
        Assert.assertFalse("Fareed must NOT spawn at Lumbridge",
            fareed.getSpawnZones().get(0).getLocationName().contains("Lumbridge"));

        // 6. Glod -> Cloud Realm
        Monster glod = database.getMonsterByName("Glod");
        Assert.assertNotNull("Glod must exist", glod);
        Assert.assertTrue("Glod must have spawn zones", glod.hasSpawnZones());
        Assert.assertEquals("Cloud Realm", glod.getSpawnZones().get(0).getDungeonName());
        Assert.assertFalse("Glod must NOT spawn at Lumbridge",
            glod.getSpawnZones().get(0).getLocationName().contains("Lumbridge"));

        // 7. Karamel -> Culinaromancer's Realm
        Monster karamel = database.getMonsterByName("Karamel");
        Assert.assertNotNull("Karamel must exist", karamel);
        Assert.assertTrue("Karamel must have spawn zones", karamel.hasSpawnZones());
        Assert.assertEquals("Culinaromancer's Realm", karamel.getSpawnZones().get(0).getDungeonName());
        Assert.assertFalse("Karamel must NOT spawn at Lumbridge",
            karamel.getSpawnZones().get(0).getLocationName().contains("Lumbridge"));

        // 8. Gelatinnoth Mother -> Culinaromancer's Realm
        Monster gMother = database.getMonsterByName("Gelatinnoth Mother");
        Assert.assertNotNull("Gelatinnoth Mother must exist", gMother);
        Assert.assertTrue("Gelatinnoth Mother must have spawn zones", gMother.hasSpawnZones());
        Assert.assertEquals("Culinaromancer's Realm", gMother.getSpawnZones().get(0).getDungeonName());

        // 9. Treus Dayth -> Haunted Mine
        Monster treus = database.getMonsterByName("Treus Dayth");
        Assert.assertNotNull("Treus Dayth must exist", treus);
        Assert.assertTrue("Treus Dayth must have spawn zones", treus.hasSpawnZones());
        Assert.assertEquals("Haunted Mine", treus.getSpawnZones().get(0).getDungeonName());
        Assert.assertFalse("Treus Dayth must NOT spawn at Lumbridge",
            treus.getSpawnZones().get(0).getLocationName().contains("Lumbridge"));

        // 10. Nazastarool -> Viyeldi Caves (NOT Legends' Guild surface!)
        Monster naz = database.getMonsterByName("Nazastarool");
        Assert.assertNotNull("Nazastarool must exist", naz);
        Assert.assertTrue("Nazastarool must have spawn zones", naz.hasSpawnZones());
        Assert.assertEquals("Viyeldi Caves", naz.getSpawnZones().get(0).getDungeonName());
        Assert.assertFalse("Nazastarool must NOT spawn at Legends Guild surface",
            naz.getSpawnZones().get(0).getLocationName().contains("Legends' Guild"));

        // 11. Black Knight Titan -> Fisher Realm
        Monster bkt = database.getMonsterByName("Black Knight Titan");
        Assert.assertNotNull("Black Knight Titan must exist", bkt);
        if (bkt.hasSpawnZones() && !bkt.getSpawnZones().isEmpty())
        {
            Assert.assertEquals("Fisher Realm", bkt.getSpawnZones().get(0).getDungeonName());
        }

        // 12. Sir Mordred -> Keep Le Faye (Top Floor)
        Monster mordred = database.getMonsterByName("Sir Mordred");
        Assert.assertNotNull("Sir Mordred must exist", mordred);
        Assert.assertTrue("Sir Mordred must have spawn zones", mordred.hasSpawnZones());
        Assert.assertEquals(2, mordred.getSpawnZones().get(0).getPlane());

        // 13. Tarn Razorlor / Tarn -> Tarn's Lair
        Monster tarn = database.getMonsterByName("Tarn Razorlor");
        if (tarn == null)
        {
            tarn = database.getMonsterByName("Tarn");
        }
        if (tarn != null)
        {
            Assert.assertTrue("Tarn must have spawn zones", tarn.hasSpawnZones());
            if (tarn.getSpawnZones().get(0).getDungeonName() != null)
            {
                Assert.assertEquals("Tarn's Lair", tarn.getSpawnZones().get(0).getDungeonName());
            }
        }

        // 14. Slash Bash -> Zogre Dungeon (NOT Gu'Tanoth surface!)
        Monster slashBash = database.getMonsterByName("Slash Bash");
        Assert.assertNotNull("Slash Bash must exist", slashBash);
        Assert.assertTrue("Slash Bash must have spawn zones", slashBash.hasSpawnZones());
        Assert.assertEquals("Zogre Dungeon", slashBash.getSpawnZones().get(0).getDungeonName());

        // 15. Chronozon -> Edgeville Dungeon (wildy >= 0)
        Monster chronozon = database.getMonsterByName("Chronozon");
        Assert.assertNotNull("Chronozon must exist", chronozon);
        if (chronozon.hasSpawnZones() && !chronozon.getSpawnZones().isEmpty())
        {
            Assert.assertEquals("Edgeville Dungeon", chronozon.getSpawnZones().get(0).getDungeonName());
            Assert.assertTrue(chronozon.getSpawnZones().get(0).getWildernessLevel() >= 0);
        }
    }

    @Test
    public void testNoFakeRuinsOfUnkahSpawns()
    {
        for (Monster m : database.getAllMonsters())
        {
            if (m.hasSpawnZones())
            {
                for (com.osrscopilot.data.model.MonsterSpawnZone z : m.getSpawnZones())
                {
                    if ("Ruins of Unkah".equalsIgnoreCase(z.getLocationName()))
                    {
                        Assert.assertTrue("Only native desert creatures may spawn at Ruins of Unkah, found: " + m.getName(),
                            m.getName().toLowerCase().contains("seagull")
                            || m.getName().toLowerCase().contains("goat")
                            || m.getName().toLowerCase().contains("crocodile")
                            || m.getName().toLowerCase().contains("scorpion"));
                    }
                }
            }
        }
    }
}

