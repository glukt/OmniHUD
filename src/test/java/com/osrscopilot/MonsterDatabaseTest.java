package com.osrscopilot;

import com.google.gson.Gson;
import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterDrop;
import com.osrscopilot.data.model.MonsterSpawnZone;
import java.util.List;
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
    public void testMonsterDatabaseLoad()
    {
        Assert.assertTrue("Database should be loaded", database.isLoaded());
        List<Monster> monsters = database.getAllMonsters();
        Assert.assertNotNull("Monsters list should not be null", monsters);
        Assert.assertFalse("Should have loaded monsters from database", monsters.isEmpty());
        Assert.assertTrue("Should have loaded at least 1000 monsters, found: " + monsters.size(), monsters.size() >= 1000);

        Monster kbd = database.getMonsterByName("King Black Dragon");
        Assert.assertNotNull("KBD should be in database", kbd);
        Assert.assertEquals(276, kbd.getCombatLevel());
        Assert.assertTrue("KBD should have drops", kbd.hasDrops());
        Assert.assertTrue("KBD should have spawn zones", kbd.hasSpawnZones());

        Monster zulrah = database.getMonsterByName("Zulrah");
        Assert.assertNotNull("Zulrah should be in database", zulrah);
        Assert.assertEquals(725, zulrah.getCombatLevel());

        Monster gargoyle = database.getMonsterByName("Gargoyle");
        Assert.assertNotNull("Gargoyle should be in database", gargoyle);
        Assert.assertEquals(75, gargoyle.getSlayerLevel());
    }

    @Test
    public void testCategoryFiltering()
    {
        List<Monster> bosses = database.getMonstersByCategory("Bosses");
        Assert.assertFalse("Bosses list should not be empty", bosses.isEmpty());

        List<Monster> dragons = database.getMonstersByCategory("Dragons");
        Assert.assertFalse("Dragons list should not be empty", dragons.isEmpty());

        List<Monster> slayer = database.getMonstersByCategory("Slayer");
        Assert.assertFalse("Slayer monsters list should not be empty", slayer.isEmpty());

        List<Monster> f2p = database.getMonstersByCategory("F2P");
        Assert.assertFalse("F2P list should not be empty", f2p.isEmpty());
    }

    @Test
    public void testMonsterSearch()
    {
        List<Monster> results = database.searchMonsters("dragon", "All");
        Assert.assertFalse("Search results for dragon should not be empty", results.isEmpty());
        for (Monster m : results)
        {
            Assert.assertTrue("Monster name should contain dragon", m.getName().toLowerCase().contains("dragon"));
        }
    }

    @Test
    public void testWeaknessAndDefences()
    {
        Monster vorkath = database.getMonsterByName("Vorkath");
        Assert.assertNotNull("Vorkath should exist", vorkath);
        Assert.assertNotNull("Vorkath weakness should not be null", vorkath.getWeakness());
    }

    @Test
    public void testQuestRequirements()
    {
        Monster vorkath = database.getMonsterByName("Vorkath");
        Assert.assertNotNull("Vorkath should exist", vorkath);
        Assert.assertEquals("Dragon Slayer II", vorkath.getQuestRequirement());
        Assert.assertTrue(vorkath.hasQuestRequirement());

        Monster zulrah = database.getMonsterByName("Zulrah");
        Assert.assertNotNull("Zulrah should exist", zulrah);
        Assert.assertEquals("Regicide", zulrah.getQuestRequirement());

        Monster demonicGorilla = database.getMonsterByName("Demonic gorilla");
        Assert.assertNotNull("Demonic gorilla should exist", demonicGorilla);
        Assert.assertEquals("Monkey Madness II", demonicGorilla.getQuestRequirement());

        Monster muspah = database.getMonsterByName("Phantom Muspah");
        Assert.assertNotNull("Phantom Muspah should exist", muspah);
        Assert.assertEquals("Secrets of the North", muspah.getQuestRequirement());

        Monster dks = database.getMonsterByName("Dagannoth Rex");
        Assert.assertNotNull("Dagannoth Rex should exist", dks);
        Assert.assertEquals("The Fremennik Trials", dks.getQuestRequirement());

        Monster basilisk = database.getMonsterByName("Basilisk Knight");
        Assert.assertNotNull("Basilisk Knight should exist", basilisk);
        Assert.assertEquals("The Fremennik Exiles", basilisk.getQuestRequirement());

        Monster gargoyle = database.getMonsterByName("Gargoyle");
        Assert.assertNotNull("Gargoyle should exist", gargoyle);
        Assert.assertEquals("Priest in Peril", gargoyle.getQuestRequirement());

        Monster banshee = database.getMonsterByName("Banshee");
        Assert.assertNotNull("Banshee should exist", banshee);
        Assert.assertEquals("Priest in Peril", banshee.getQuestRequirement());

        Monster graardor = database.getMonsterByName("General Graardor");
        Assert.assertNotNull("General Graardor should exist", graardor);
        Assert.assertEquals("Death Plateau / Troll Stronghold", graardor.getQuestRequirement());

        Monster scurrius = database.getMonsterByName("Scurrius");
        Assert.assertNotNull("Scurrius should exist", scurrius);
        Assert.assertEquals("Varrock Sewers", scurrius.getQuestRequirement());
    }

    @Test
    public void testMonkeyZombieSpawns()
    {
        Monster mz = database.getMonsterByName("Monkey Zombie");
        Assert.assertNotNull("Monkey Zombie must exist in database", mz);
        Assert.assertTrue("Monkey Zombie must have spawn zones", mz.hasSpawnZones());
        Assert.assertEquals(3, mz.getSpawnZones().size());
        // Recentred on the real Ape Atoll dungeon spawn cluster (wiki + game-cache coords).
        Assert.assertEquals(2785, mz.getSpawnZones().get(0).getZoneCenter().getX());
        Assert.assertEquals(9199, mz.getSpawnZones().get(0).getZoneCenter().getY());
        Assert.assertEquals(2800, mz.getSpawnZones().get(1).getZoneCenter().getX());
        Assert.assertEquals(2785, mz.getSpawnZones().get(1).getZoneCenter().getY());
        Assert.assertEquals(2760, mz.getSpawnZones().get(2).getZoneCenter().getX());
        Assert.assertEquals(2750, mz.getSpawnZones().get(2).getZoneCenter().getY());
    }

    @Test
    public void testBossSpawns()
    {
        Monster zulrah = database.getMonsterByName("Zulrah");
        Assert.assertNotNull(zulrah);
        Assert.assertEquals(2268, zulrah.getSpawnZones().get(0).getZoneCenter().getX());
        Assert.assertEquals(3072, zulrah.getSpawnZones().get(0).getZoneCenter().getY());

        Monster vorkath = database.getMonsterByName("Vorkath");
        Assert.assertNotNull(vorkath);
        // (2269,4062) - the wiki locline and the game cache both put Vorkath's lair here; the old
        // hand-entered (2272,4048) was ~14 tiles off.
        Assert.assertEquals(2269, vorkath.getSpawnZones().get(0).getZoneCenter().getX());
        Assert.assertEquals(4062, vorkath.getSpawnZones().get(0).getZoneCenter().getY());

        Monster muspah = database.getMonsterByName("Phantom Muspah");
        Assert.assertNotNull(muspah);
        Assert.assertEquals(2909, muspah.getSpawnZones().get(0).getZoneCenter().getX()); // wiki locline (was ~18 off)
        Assert.assertEquals(10317, muspah.getSpawnZones().get(0).getZoneCenter().getY());

        Monster kbd = database.getMonsterByName("King Black Dragon");
        Assert.assertNotNull(kbd);
        Assert.assertEquals(2271, kbd.getSpawnZones().get(0).getZoneCenter().getX());
        Assert.assertEquals(4699, kbd.getSpawnZones().get(0).getZoneCenter().getY());
    }

    @Test
    public void testHellhoundSpawnsPrecise()
    {
        Monster hellhound = database.getMonsterByName("Hellhound");
        Assert.assertNotNull("Hellhound must exist in database", hellhound);
        Assert.assertTrue("Hellhound must have spawn zones", hellhound.hasSpawnZones());
        Assert.assertTrue("Hellhound must have all 10+ spawn zones loaded", hellhound.getSpawnZones().size() >= 10);

        boolean hasTaverley = hellhound.getSpawnZones().stream().anyMatch(z -> "Taverley Dungeon".equalsIgnoreCase(z.getDungeonName()));
        boolean hasCatacombs = hellhound.getSpawnZones().stream().anyMatch(z -> "Catacombs of Kourend".equalsIgnoreCase(z.getDungeonName()));
        boolean hasKaruulm = hellhound.getSpawnZones().stream().anyMatch(z -> "Karuulm Slayer Dungeon".equalsIgnoreCase(z.getDungeonName()));
        boolean hasWitchaven = hellhound.getSpawnZones().stream().anyMatch(z -> "Witchaven Dungeon".equalsIgnoreCase(z.getDungeonName()));
        boolean hasWildy = hellhound.getSpawnZones().stream().anyMatch(z -> z.getWildernessLevel() > 0);

        Assert.assertTrue("Hellhound must have Taverley Dungeon spawn", hasTaverley);
        Assert.assertTrue("Hellhound must have Catacombs of Kourend spawn", hasCatacombs);
        Assert.assertTrue("Hellhound must have Karuulm Slayer Dungeon spawn", hasKaruulm);
        Assert.assertTrue("Hellhound must have Witchaven Dungeon spawn", hasWitchaven);
        Assert.assertTrue("Hellhound must have Wilderness spawn", hasWildy);
    }

    @Test
    public void testGrizzlyBearSpawns()
    {
        Monster grizzlyBear = database.getMonsterByName("Grizzly bear");
        Assert.assertNotNull("Grizzly bear must exist in database", grizzlyBear);
        Assert.assertTrue("Grizzly bear must have spawn zones", grizzlyBear.hasSpawnZones());
        Assert.assertEquals("Grizzly bear must have all 17 spawn zones loaded", 17, grizzlyBear.getSpawnZones().size());
    }

    @Test
    public void testCaveHorrorCategorizedDrops()
    {
        Monster caveHorror = database.getMonsterByName("Cave horror");
        Assert.assertNotNull("Cave horror must exist in database", caveHorror);
        Assert.assertTrue("Cave horror must have drops", caveHorror.hasDrops());
        List<MonsterDrop> drops = caveHorror.getDrops();
        Assert.assertFalse("Cave horror drops must not be empty", drops.isEmpty());

        // Verify categories: 100%, Weapons and armour, Runes, Tertiary
        boolean has100 = drops.stream().anyMatch(d -> "100%".equalsIgnoreCase(d.getCategory()) && d.getName().equalsIgnoreCase("Big bones"));
        boolean hasWeaponsArmour = drops.stream().anyMatch(d -> "Weapons and armour".equalsIgnoreCase(d.getCategory()) && d.getName().equalsIgnoreCase("Black mask (10)"));
        boolean hasRunes = drops.stream().anyMatch(d -> "Runes".equalsIgnoreCase(d.getCategory()) && d.getName().equalsIgnoreCase("Nature rune"));
        boolean hasTertiary = drops.stream().anyMatch(d -> "Tertiary".equalsIgnoreCase(d.getCategory()) && d.getName().equalsIgnoreCase("Ensouled horror head"));

        Assert.assertTrue("Cave horror must have 100% category drops", has100);
        Assert.assertTrue("Cave horror must have Weapons and armour category drops", hasWeaponsArmour);
        Assert.assertTrue("Cave horror must have Runes category drops", hasRunes);
        Assert.assertTrue("Cave horror must have Tertiary category drops", hasTertiary);
    }

    @Test
    public void testHillGiantSpawnsPrecise()
    {
        Monster hillGiant = database.getMonsterByName("Hill Giant");
        Assert.assertNotNull("Hill Giant must exist in database", hillGiant);
        Assert.assertTrue("Hill Giant must have spawn zones", hillGiant.hasSpawnZones());

        // Check Shayzien Giant Pit has wildernessLevel = 0
        boolean foundShayzien = false;
        for (com.osrscopilot.data.model.MonsterSpawnZone z : hillGiant.getSpawnZones())
        {
            if (z.getZoneName().contains("Shayzien"))
            {
                foundShayzien = true;
                Assert.assertEquals("Shayzien Giant Pit must have wildy=0", 0, z.getWildernessLevel());
                Assert.assertEquals(1443, z.getZoneCenter().getX());
                Assert.assertEquals(3612, z.getZoneCenter().getY());
            }
            if (z.getZoneName().contains("Catacombs"))
            {
                Assert.assertEquals("Catacombs Hill Giants must have wildy=0", 0, z.getWildernessLevel());
            }
        }
        Assert.assertTrue("Must have found Shayzien Giant Pit zone", foundShayzien);
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
        // 1. Nightmare Zone / (hard) bosses must NEVER have a real open-world spawn
        // (an entrance-only marker zone pointing at the Nightmare Zone lobby is expected and fine)
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

        // 2. Scan entire database for (hard), (historical), (Nightmare Zone)
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
        Assert.assertTrue("Black Knight Titan must have spawn zones", bkt.hasSpawnZones());
        Assert.assertEquals("Fisher Realm", bkt.getSpawnZones().get(0).getDungeonName());

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
        Assert.assertNotNull("Tarn / Tarn Razorlor must exist", tarn);
        Assert.assertTrue("Tarn must have spawn zones", tarn.hasSpawnZones());
        Assert.assertEquals("Tarn's Lair", tarn.getSpawnZones().get(0).getDungeonName());

        // 14. Slash Bash -> Zogre Dungeon (NOT Gu'Tanoth surface!)
        Monster slashBash = database.getMonsterByName("Slash Bash");
        Assert.assertNotNull("Slash Bash must exist", slashBash);
        Assert.assertTrue("Slash Bash must have spawn zones", slashBash.hasSpawnZones());
        Assert.assertEquals("Zogre Dungeon", slashBash.getSpawnZones().get(0).getDungeonName());

        // 15. Chronozon -> Edgeville Dungeon (wildy >= 0)
        Monster chronozon = database.getMonsterByName("Chronozon");
        Assert.assertNotNull("Chronozon must exist", chronozon);
        Assert.assertTrue("Chronozon must have spawn zones", chronozon.hasSpawnZones());
        Assert.assertEquals("Edgeville Dungeon", chronozon.getSpawnZones().get(0).getDungeonName());
        Assert.assertTrue(chronozon.getSpawnZones().get(0).getWildernessLevel() >= 0);
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

    @Test
    public void testFakeTestNpcsPurged()
    {
        // 1. Check specific fake NPC IDs are null
        Assert.assertNull("NPC 6698 (Ghost guard 1337) must be purged", database.getMonsterById(6698));
        Assert.assertNull("NPC 6574 (Gnome guard 1337) must be purged", database.getMonsterById(6574));
        Assert.assertNull("NPC 3361 (Guard 1337) must be purged", database.getMonsterById(3361));

        // 2. Scan entire database for any fake 1337 stats or names
        for (Monster m : database.getAllMonsters())
        {
            Assert.assertNotEquals("No monster may have fake combat level 1337: " + m.getName(), 1337, m.getCombatLevel());
            Assert.assertFalse("No monster name may contain '(1337)': " + m.getName(), m.getName().contains("(1337)"));
            Assert.assertFalse("Ghost guard (1337) must not exist", "Ghost guard (1337)".equalsIgnoreCase(m.getName()));
            Assert.assertFalse("Gnome guard (1337) must not exist", "Gnome guard (1337)".equalsIgnoreCase(m.getName()));
            Assert.assertFalse("Guard (1337) must not exist", "Guard (1337)".equalsIgnoreCase(m.getName()));
        }
    }

    @Test
    public void testEncounterTypes()
    {
        // 1. Raid Bosses without open-world spawns
        Monster verzik = database.getMonsterByName("Verzik Vitur");
        Assert.assertNotNull("Verzik Vitur must exist", verzik);
        Assert.assertTrue("Verzik must not have an open-world spawn (entrance marker only is OK)", hasOnlyEntranceMarkerZones(verzik));
        Assert.assertTrue("Verzik must have encounterType", verzik.hasEncounterType());
        Assert.assertTrue("Verzik encounterType must mention Theatre of Blood",
            verzik.getEncounterType().contains("Theatre of Blood"));

        Monster sotetseg = database.getMonsterByName("Sotetseg");
        Assert.assertNotNull("Sotetseg must exist", sotetseg);
        Assert.assertTrue("Sotetseg encounterType must mention Theatre of Blood",
            sotetseg.getEncounterType().contains("Theatre of Blood"));

        Monster bloat = database.getMonsterByName("Pestilent Bloat");
        Assert.assertNotNull("Pestilent Bloat must exist", bloat);
        Assert.assertTrue("Bloat encounterType must mention Theatre of Blood",
            bloat.getEncounterType().contains("Theatre of Blood"));

        Monster maiden = database.getMonsterByName("The Maiden of Sugadinti");
        Assert.assertNotNull("Maiden must exist", maiden);
        Assert.assertTrue("Maiden encounterType must mention Theatre of Blood",
            maiden.getEncounterType().contains("Theatre of Blood"));

        Monster olm = database.getMonsterByName("Great Olm");
        Assert.assertNotNull("Great Olm must exist", olm);
        Assert.assertTrue("Olm must not have an open-world spawn (entrance marker only is OK)", hasOnlyEntranceMarkerZones(olm));
        Assert.assertTrue("Olm encounterType must mention Chambers of Xeric",
            olm.getEncounterType().contains("Chambers of Xeric"));

        // 2. Minigames
        Monster zuk = database.getMonsterByName("TzKal-Zuk");
        if (zuk != null && zuk.getEncounterType() != null)
        {
            Assert.assertTrue("TzKal-Zuk encounterType must mention Inferno, TzHaar, or Minigame",
                zuk.getEncounterType().contains("Inferno") || zuk.getEncounterType().contains("TzHaar") || zuk.getEncounterType().contains("Minigame"));
        }

        Monster penanceQueen = database.getMonsterByName("Penance Queen");
        Assert.assertNotNull("Penance Queen must exist", penanceQueen);
        Assert.assertTrue("Penance Queen encounterType must mention Barbarian Assault",
            penanceQueen.getEncounterType().contains("Barbarian Assault"));

        Monster brawler = database.getMonsterByName("Brawler");
        Assert.assertNotNull("Brawler must exist", brawler);
        Assert.assertTrue("Brawler encounterType must mention Pest Control",
            brawler.getEncounterType().contains("Pest Control"));

        Monster defiler = database.getMonsterByName("Defiler");
        Assert.assertNotNull("Defiler must exist", defiler);
        Assert.assertTrue("Defiler encounterType must mention Pest Control",
            defiler.getEncounterType().contains("Pest Control"));

        // 3. Boss Minions
        Monster strongstack = database.getMonsterByName("Sergeant Strongstack");
        if (strongstack != null && strongstack.getEncounterType() != null)
        {
            Assert.assertTrue("Sergeant Strongstack encounterType must mention Boss Minion",
                strongstack.getEncounterType().contains("Boss Minion"));
        }

        Monster snakeling = database.getMonsterByName("Snakeling");
        if (snakeling != null && snakeling.getEncounterType() != null)
        {
            Assert.assertTrue("Snakeling encounterType must mention Boss Minion",
                snakeling.getEncounterType().contains("Boss Minion"));
        }

        Monster zombifiedSpawn = database.getMonsterByName("Zombified Spawn");
        if (zombifiedSpawn != null && zombifiedSpawn.getEncounterType() != null)
        {
            Assert.assertTrue("Zombified Spawn encounterType must mention Boss Minion",
                zombifiedSpawn.getEncounterType().contains("Boss Minion"));
        }

        Monster deathSpawn = database.getMonsterByName("Death spawn");
        if (deathSpawn != null && deathSpawn.getEncounterType() != null)
        {
            Assert.assertTrue("Death spawn encounterType must mention Boss Minion",
                deathSpawn.getEncounterType().contains("Boss Minion"));
        }

        // 4. Superior Slayer
        Monster nightBeast = database.getMonsterByName("Night beast");
        Assert.assertNotNull("Night beast must exist", nightBeast);
        Assert.assertTrue("Night beast encounterType must mention Superior Slayer",
            nightBeast.getEncounterType().contains("Superior Slayer"));

        Monster nechryarch = database.getMonsterByName("Nechryarch");
        Assert.assertNotNull("Nechryarch must exist", nechryarch);
        Assert.assertTrue("Nechryarch encounterType must mention Superior Slayer",
            nechryarch.getEncounterType().contains("Superior Slayer"));

        Monster chokeDevil = database.getMonsterByName("Choke devil");
        Assert.assertNotNull("Choke devil must exist", chokeDevil);
        Assert.assertTrue("Choke devil encounterType must mention Superior Slayer",
            chokeDevil.getEncounterType().contains("Superior Slayer"));

        // 5. ALL monsters without spawn zones MUST have a non-empty encounterType
        int zeroZonesCount = 0;
        for (Monster m : database.getAllMonsters())
        {
            if (!m.hasSpawnZones())
            {
                zeroZonesCount++;
                Assert.assertTrue("Monster without spawn zones must have encounterType: " + m.getName() + " (id=" + m.getId() + ")",
                    m.hasEncounterType());
                Assert.assertNotNull(m.getEncounterType());
                Assert.assertFalse(m.getEncounterType().trim().isEmpty());
            }
        }
        Assert.assertTrue("Must have tested monsters without spawn zones", zeroZonesCount > 0);
    }

    @Test
    public void testAccurateSlayerLevels()
    {
        Monster chokeDevil = database.getMonsterByName("Choke devil");
        Assert.assertNotNull("Choke devil must exist", chokeDevil);
        // Superior of the Dust devil - shares the base 65 Slayer requirement (the old value 93 was
        // a bad hand-entered override; the wiki infobox says 65).
        Assert.assertEquals("Choke devil slayer level must be 65", 65, chokeDevil.getSlayerLevel());

        Monster gargoyle = database.getMonsterByName("Gargoyle");
        Assert.assertNotNull("Gargoyle must exist", gargoyle);
        Assert.assertEquals("Gargoyle slayer level must be 75", 75, gargoyle.getSlayerLevel());

        Monster cerberus = database.getMonsterByName("Cerberus");
        Assert.assertNotNull("Cerberus must exist", cerberus);
        Assert.assertEquals("Cerberus slayer level must be 91", 91, cerberus.getSlayerLevel());

        Monster hydra = database.getMonsterByName("Alchemical Hydra");
        Assert.assertNotNull("Alchemical Hydra must exist", hydra);
        Assert.assertEquals("Alchemical Hydra slayer level must be 95", 95, hydra.getSlayerLevel());

        Monster kraken = database.getMonsterByName("Kraken");
        Assert.assertNotNull("Kraken must exist", kraken);
        Assert.assertEquals("Kraken slayer level must be 87", 87, kraken.getSlayerLevel());

        Monster abyssalDemon = database.getMonsterByName("Abyssal demon");
        Assert.assertNotNull("Abyssal demon must exist", abyssalDemon);
        Assert.assertEquals("Abyssal demon slayer level must be 85", 85, abyssalDemon.getSlayerLevel());

        Monster darkBeast = database.getMonsterByName("Dark beast");
        Assert.assertNotNull("Dark beast must exist", darkBeast);
        Assert.assertEquals("Dark beast slayer level must be 90", 90, darkBeast.getSlayerLevel());
    }
}
