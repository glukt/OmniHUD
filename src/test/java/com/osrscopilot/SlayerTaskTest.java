package com.osrscopilot;

import com.google.gson.Gson;
import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.SlayerTaskManager;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterSpawnZone;
import com.osrscopilot.data.model.SlayerMaster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public class SlayerTaskTest
{
    private MonsterDatabase monsterDatabase;
    private ConfigManager configManager;
    private SlayerTaskManager taskManager;
    private Map<String, String> configStorage;

    @Before
    public void setUp()
    {
        monsterDatabase = new MonsterDatabase(new Gson());
        monsterDatabase.load();

        configStorage = new HashMap<>();
        configManager = Mockito.mock(ConfigManager.class);

        Answer<Void> setAnswer = invocation -> {
            String group = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            Object value = invocation.getArgument(2);
            configStorage.put(group + "." + key, value != null ? value.toString() : null);
            return null;
        };

        Mockito.doAnswer(setAnswer).when(configManager).setConfiguration(Mockito.anyString(), Mockito.anyString(), Mockito.any());
        Mockito.doAnswer(setAnswer).when(configManager).setConfiguration(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        Mockito.doAnswer(setAnswer).when(configManager).setConfiguration(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt());
        Mockito.doAnswer(setAnswer).when(configManager).setConfiguration(Mockito.anyString(), Mockito.anyString(), Mockito.anyLong());

        Mockito.doAnswer((Answer<String>) invocation -> {
            String group = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            return configStorage.get(group + "." + key);
        }).when(configManager).getConfiguration(Mockito.anyString(), Mockito.anyString());

        Mockito.doAnswer((Answer<Integer>) invocation -> {
            String group = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            String val = configStorage.get(group + "." + key);
            return val != null ? Integer.parseInt(val) : null;
        }).when(configManager).getConfiguration(Mockito.anyString(), Mockito.anyString(), Mockito.eq(Integer.class));

        Mockito.doAnswer((Answer<Long>) invocation -> {
            String group = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            String val = configStorage.get(group + "." + key);
            return val != null ? Long.parseLong(val) : null;
        }).when(configManager).getConfiguration(Mockito.anyString(), Mockito.anyString(), Mockito.eq(Long.class));

        Mockito.doAnswer((Answer<Void>) invocation -> {
            String group = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            configStorage.remove(group + "." + key);
            return null;
        }).when(configManager).unsetConfiguration(Mockito.anyString(), Mockito.anyString());

        taskManager = new SlayerTaskManager(configManager, monsterDatabase);
    }

    @Test
    public void testAssignmentPatterns()
    {
        // 1. Standard assignment: "You have been assigned to kill hellhounds (x150)."
        boolean parsed1 = taskManager.parseChatMessage("You have been assigned to kill hellhounds (x150).");
        Assert.assertTrue(parsed1);
        Assert.assertEquals("hellhounds", taskManager.getMonsterName());
        Assert.assertEquals(150, taskManager.getInitialAmount());
        Assert.assertEquals(150, taskManager.getAmountRemaining());
        Assert.assertNull(taskManager.getLocation());
        Assert.assertTrue(taskManager.hasActiveTask());

        // 2. "Your new task is to kill 165 Gargoyles."
        boolean parsed2 = taskManager.parseChatMessage("Your new task is to kill 165 Gargoyles.");
        Assert.assertTrue(parsed2);
        Assert.assertEquals("Gargoyles", taskManager.getMonsterName());
        Assert.assertEquals(165, taskManager.getInitialAmount());
        Assert.assertEquals(165, taskManager.getAmountRemaining());

        // 3. "You are assigned to kill 140 Bloodvelds."
        boolean parsed3 = taskManager.parseChatMessage("You are assigned to kill 140 Bloodvelds.");
        Assert.assertTrue(parsed3);
        Assert.assertEquals("Bloodvelds", taskManager.getMonsterName());
        Assert.assertEquals(140, taskManager.getInitialAmount());
        Assert.assertEquals(140, taskManager.getAmountRemaining());
    }

    @Test
    public void testKonarAssignmentAndLocationPattern()
    {
        // Konar: "You are to bring balance to 142 Aberrant spectres in Catacombs of Kourend."
        boolean parsed = taskManager.parseChatMessage("You are to bring balance to 142 Aberrant spectres in Catacombs of Kourend.");
        Assert.assertTrue(parsed);
        Assert.assertEquals("Aberrant spectres", taskManager.getMonsterName());
        Assert.assertEquals(142, taskManager.getInitialAmount());
        Assert.assertEquals(142, taskManager.getAmountRemaining());
        Assert.assertEquals("Catacombs of Kourend", taskManager.getLocation());
        Assert.assertEquals("Konar quo Maten", taskManager.getSlayerMaster());

        // Konar check: "You are to bring balance to Aberrant spectres in Catacombs of Kourend; only 40 more to go."
        boolean parsedCheck = taskManager.parseChatMessage("You are to bring balance to Aberrant spectres in Catacombs of Kourend; only 40 more to go.");
        Assert.assertTrue(parsedCheck);
        Assert.assertEquals("Aberrant spectres", taskManager.getMonsterName());
        Assert.assertEquals(142, taskManager.getInitialAmount());
        Assert.assertEquals(40, taskManager.getAmountRemaining());
        Assert.assertEquals("Catacombs of Kourend", taskManager.getLocation());
    }

    @Test
    public void testStatusAndCheckPatterns()
    {
        // "You're assigned to kill Hellhounds; only 45 more to go."
        taskManager.parseChatMessage("You have been assigned to kill Hellhounds (x100).");
        boolean parsed = taskManager.parseChatMessage("You're assigned to kill Hellhounds; only 45 more to go.");
        Assert.assertTrue(parsed);
        Assert.assertEquals("Hellhounds", taskManager.getMonsterName());
        Assert.assertEquals(100, taskManager.getInitialAmount());
        Assert.assertEquals(45, taskManager.getAmountRemaining());

        // "You need to kill 87 Gargoyles to complete your current Slayer assignment."
        boolean parsedNeed = taskManager.parseChatMessage("You need to kill 87 Gargoyles to complete your current Slayer assignment.");
        Assert.assertTrue(parsedNeed);
        Assert.assertEquals("Gargoyles", taskManager.getMonsterName());
        Assert.assertEquals(87, taskManager.getAmountRemaining());

        // "You need 32 more kills to complete your task."
        boolean parsedKills = taskManager.parseChatMessage("You need 32 more kills to complete your task.");
        Assert.assertTrue(parsedKills);
        Assert.assertEquals(32, taskManager.getAmountRemaining());

        // "You still need to kill 25 more Dagannoth."
        boolean parsedStill = taskManager.parseChatMessage("You still need to kill 25 more Dagannoth.");
        Assert.assertTrue(parsedStill);
        Assert.assertEquals("Dagannoth", taskManager.getMonsterName());
        Assert.assertEquals(25, taskManager.getAmountRemaining());
    }

    @Test
    public void testTaskCompletionAndReset()
    {
        taskManager.parseChatMessage("Your new task is to kill 50 Gargoyles.");
        Assert.assertTrue(taskManager.hasActiveTask());
        Assert.assertEquals(50, taskManager.getAmountRemaining());

        // Complete task
        boolean completed = taskManager.parseChatMessage("You've completed your task! You killed 50 Gargoyles.");
        Assert.assertTrue(completed);
        Assert.assertEquals(0, taskManager.getAmountRemaining());
        Assert.assertFalse(taskManager.hasActiveTask());

        // Reset task
        taskManager.parseChatMessage("Your new task is to kill 100 Hellhounds.");
        Assert.assertTrue(taskManager.hasActiveTask());
        boolean reset = taskManager.parseChatMessage("Your Slayer task has been reset.");
        Assert.assertTrue(reset);
        Assert.assertFalse(taskManager.hasActiveTask());
        Assert.assertNull(taskManager.getMonsterName());
    }

    @Test
    public void testKillDecrementAndProgress()
    {
        taskManager.setTaskDetails("Hellhounds", 100, 100, null, "Duradel");
        Assert.assertEquals(0.0, taskManager.getProgressPercentage(), 0.01);
        Assert.assertEquals(0, taskManager.getKillsCompleted());

        taskManager.decrementKill();
        Assert.assertEquals(99, taskManager.getAmountRemaining());
        Assert.assertEquals(1, taskManager.getKillsCompleted());
        Assert.assertEquals(1.0, taskManager.getProgressPercentage(), 0.01);

        taskManager.decrementKill(49);
        Assert.assertEquals(50, taskManager.getAmountRemaining());
        Assert.assertEquals(50, taskManager.getKillsCompleted());
        Assert.assertEquals(50.0, taskManager.getProgressPercentage(), 0.01);
    }

    @Test
    public void testMonsterDatabaseMatching()
    {
        // 1. Plural "hellhounds" -> "Hellhound"
        Monster hellhound = SlayerTaskManager.findMonsterForTask("hellhounds", monsterDatabase);
        Assert.assertNotNull("Hellhounds should resolve to Monster object", hellhound);
        Assert.assertEquals("Hellhound", hellhound.getName());
        Assert.assertTrue("Hellhound should have spawn zones", hellhound.hasSpawnZones());

        // 2. Plural "gargoyles" -> "Gargoyle"
        Monster gargoyle = SlayerTaskManager.findMonsterForTask("gargoyles", monsterDatabase);
        Assert.assertNotNull("Gargoyles should resolve to Monster object", gargoyle);
        Assert.assertEquals("Gargoyle", gargoyle.getName());
        Assert.assertEquals(75, gargoyle.getSlayerLevel());

        // 3. Plural "bloodvelds" -> "Bloodveld"
        Monster bloodveld = SlayerTaskManager.findMonsterForTask("bloodvelds", monsterDatabase);
        Assert.assertNotNull("Bloodvelds should resolve to Monster object", bloodveld);
        Assert.assertEquals("Bloodveld", bloodveld.getName());
        Assert.assertEquals(50, bloodveld.getSlayerLevel());

        // 4. "abyssal demons" -> "Abyssal demon"
        Monster abyssalDemon = SlayerTaskManager.findMonsterForTask("abyssal demons", monsterDatabase);
        Assert.assertNotNull("Abyssal demons should resolve", abyssalDemon);
        Assert.assertEquals("Abyssal demon", abyssalDemon.getName());
        Assert.assertEquals(85, abyssalDemon.getSlayerLevel());

        // 5. "aberrant spectres" -> "Aberrant spectre"
        Monster spectre = SlayerTaskManager.findMonsterForTask("aberrant spectres", monsterDatabase);
        Assert.assertNotNull("Aberrant spectres should resolve", spectre);
        Assert.assertEquals("Aberrant spectre", spectre.getName());
        Assert.assertEquals(60, spectre.getSlayerLevel());

        // 6. "cave horrors" -> "Cave horror"
        Monster caveHorror = SlayerTaskManager.findMonsterForTask("cave horrors", monsterDatabase);
        Assert.assertNotNull("Cave horrors should resolve", caveHorror);
        Assert.assertEquals("Cave horror", caveHorror.getName());
        Assert.assertEquals(58, caveHorror.getSlayerLevel());

        // 7. "blue dragons" -> "Blue dragon"
        Monster blueDragon = SlayerTaskManager.findMonsterForTask("blue dragons", monsterDatabase);
        Assert.assertNotNull("Blue dragons should resolve", blueDragon);
        Assert.assertEquals("Blue dragon", blueDragon.getName());

        // 8. "dagannoth" -> "Dagannoth"
        Monster dagannoth = SlayerTaskManager.findMonsterForTask("dagannoth", monsterDatabase);
        Assert.assertNotNull("Dagannoth should resolve", dagannoth);
        Assert.assertEquals("Dagannoth", dagannoth.getName());

        // 9. Active monster on taskManager
        taskManager.parseChatMessage("You have been assigned to kill Hellhounds (x150).");
        Monster active = taskManager.getActiveMonster();
        Assert.assertNotNull(active);
        Assert.assertEquals("Hellhound", active.getName());
    }

    @Test
    public void testSlayerMastersDirectory()
    {
        List<SlayerMaster> masters = SlayerMaster.ALL_MASTERS;
        Assert.assertEquals("Should have exactly 10 Slayer Masters (incl. Mortimer / Wyrmscraig)", 10, masters.size());

        // Verify each master details
        SlayerMaster turael = SlayerMaster.TURAEL;
        Assert.assertEquals("Turael / Aya", turael.getName());
        Assert.assertEquals("Burthorpe", turael.getLocationName());
        Assert.assertEquals(2931, turael.getLocationPoint().getX());
        Assert.assertEquals(3536, turael.getLocationPoint().getY());
        Assert.assertEquals(0, turael.getLocationPoint().getPlane());

        SlayerMaster konar = SlayerMaster.KONAR;
        Assert.assertEquals("Konar quo Maten", konar.getName());
        Assert.assertEquals("Mount Karuulm", konar.getLocationName());
        Assert.assertEquals(75, konar.getCombatRequirement());
        Assert.assertEquals(1310, konar.getLocationPoint().getX());
        Assert.assertEquals(3810, konar.getLocationPoint().getY());
        Assert.assertEquals(0, konar.getLocationPoint().getPlane());

        SlayerMaster duradel = SlayerMaster.DURADEL;
        Assert.assertEquals("Duradel", duradel.getName());
        Assert.assertEquals("Shilo Village", duradel.getLocationName());
        Assert.assertEquals(100, duradel.getCombatRequirement());
        Assert.assertEquals(50, duradel.getSlayerRequirement());
        Assert.assertEquals(2869, duradel.getLocationPoint().getX());
        Assert.assertEquals(2982, duradel.getLocationPoint().getY());
        Assert.assertEquals(1, duradel.getLocationPoint().getPlane());

        SlayerMaster vannaka = SlayerMaster.VANNAKA;
        Assert.assertEquals("Vannaka", vannaka.getName());
        Assert.assertEquals("Edgeville Dungeon", vannaka.getLocationName());
        Assert.assertEquals(40, vannaka.getCombatRequirement());
        Assert.assertEquals(3145, vannaka.getLocationPoint().getX());
        Assert.assertEquals(9914, vannaka.getLocationPoint().getY());
        Assert.assertEquals(0, vannaka.getLocationPoint().getPlane());
        Assert.assertNotNull(vannaka.getSurfaceEntrance());
        Assert.assertEquals(3096, vannaka.getSurfaceEntrance().getX());
        Assert.assertEquals(3468, vannaka.getSurfaceEntrance().getY());
        Assert.assertEquals(0, vannaka.getSurfaceEntrance().getPlane());

        SlayerMaster chaeldar = SlayerMaster.CHAELDAR;
        Assert.assertEquals("Chaeldar", chaeldar.getName());
        Assert.assertEquals("Zanaris", chaeldar.getLocationName());
        Assert.assertEquals(70, chaeldar.getCombatRequirement());
        Assert.assertEquals(2446, chaeldar.getLocationPoint().getX());
        Assert.assertEquals(4431, chaeldar.getLocationPoint().getY());
        Assert.assertEquals(0, chaeldar.getLocationPoint().getPlane());
        Assert.assertNotNull(chaeldar.getSurfaceEntrance());
        Assert.assertEquals(3202, chaeldar.getSurfaceEntrance().getX());
        Assert.assertEquals(3169, chaeldar.getSurfaceEntrance().getY());
        Assert.assertEquals(0, chaeldar.getSurfaceEntrance().getPlane());

        SlayerMaster nieve = SlayerMaster.NIEVE;
        Assert.assertEquals("Nieve / Steve", nieve.getName());
        Assert.assertEquals("Tree Gnome Stronghold", nieve.getLocationName());
        Assert.assertEquals(85, nieve.getCombatRequirement());
        Assert.assertEquals(2432, nieve.getLocationPoint().getX());
        Assert.assertEquals(3423, nieve.getLocationPoint().getY());
        Assert.assertEquals(0, nieve.getLocationPoint().getPlane());

        SlayerMaster krystilia = SlayerMaster.KRYSTILIA;
        Assert.assertEquals("Krystilia", krystilia.getName());
        Assert.assertEquals("Edgeville (Jailhouse)", krystilia.getLocationName());
        Assert.assertEquals(3092, krystilia.getLocationPoint().getX());
        Assert.assertEquals(3506, krystilia.getLocationPoint().getY());
        Assert.assertEquals(0, krystilia.getLocationPoint().getPlane());

        SlayerMaster spria = SlayerMaster.SPRIA;
        Assert.assertEquals("Spria", spria.getName());
        Assert.assertEquals("Draynor Village", spria.getLocationName());
        Assert.assertEquals(3091, spria.getLocationPoint().getX());
        Assert.assertEquals(3267, spria.getLocationPoint().getY());
        Assert.assertEquals(0, spria.getLocationPoint().getPlane());

        SlayerMaster mazchna = SlayerMaster.MAZCHNA;
        Assert.assertEquals("Mazchna", mazchna.getName());
        Assert.assertEquals("Canifis", mazchna.getLocationName());
        Assert.assertEquals(20, mazchna.getCombatRequirement());
        Assert.assertEquals(3510, mazchna.getLocationPoint().getX());
        Assert.assertEquals(3508, mazchna.getLocationPoint().getY());
        Assert.assertEquals(0, mazchna.getLocationPoint().getPlane());

        // findByName helper
        Assert.assertSame(konar, SlayerMaster.findByName("Konar"));
        Assert.assertSame(duradel, SlayerMaster.findByName("Duradel"));
        Assert.assertSame(nieve, SlayerMaster.findByName("Nieve"));
    }

    @Test
    public void testKonarLocationZoneMatching()
    {
        Monster hellhound = monsterDatabase.getMonsterByName("Hellhound");
        Assert.assertNotNull(hellhound);
        Assert.assertTrue(hellhound.hasSpawnZones());

        boolean foundCatacombs = false;
        boolean foundKaruulm = false;
        boolean foundTaverley = false;

        for (MonsterSpawnZone zone : hellhound.getSpawnZones())
        {
            String name = zone.getZoneName() != null ? zone.getZoneName().toLowerCase() : "";
            String loc = zone.getLocationName() != null ? zone.getLocationName().toLowerCase() : "";
            String dung = zone.getDungeonName() != null ? zone.getDungeonName().toLowerCase() : "";

            if (name.contains("catacombs") || loc.contains("catacombs") || dung.contains("catacombs"))
            {
                foundCatacombs = true;
            }
            if (name.contains("karuulm") || loc.contains("karuulm") || dung.contains("karuulm"))
            {
                foundKaruulm = true;
            }
            if (name.contains("taverley") || loc.contains("taverley") || dung.contains("taverley"))
            {
                foundTaverley = true;
            }
        }

        Assert.assertTrue("Hellhounds must have Catacombs of Kourend spawn zone", foundCatacombs);
        Assert.assertTrue("Hellhounds must have Karuulm spawn zone", foundKaruulm);
        Assert.assertTrue("Hellhounds must have Taverley Dungeon spawn zone", foundTaverley);
    }

    @Test
    public void testPersistenceAcrossSessions()
    {
        taskManager.parseChatMessage("You are to bring balance to 140 Aberrant spectres in Catacombs of Kourend.");
        Assert.assertEquals("Aberrant spectres", configStorage.get("osrscopilot.slayerMonster"));
        Assert.assertEquals("140", configStorage.get("osrscopilot.slayerInitialAmount"));
        Assert.assertEquals("140", configStorage.get("osrscopilot.slayerAmountRemaining"));
        Assert.assertEquals("Catacombs of Kourend", configStorage.get("osrscopilot.slayerLocation"));
        Assert.assertEquals("Konar quo Maten", configStorage.get("osrscopilot.slayerMaster"));

        // Create new manager and load from config
        SlayerTaskManager newManager = new SlayerTaskManager(configManager, monsterDatabase);
        newManager.loadFromConfig();

        Assert.assertEquals("Aberrant spectres", newManager.getMonsterName());
        Assert.assertEquals(140, newManager.getInitialAmount());
        Assert.assertEquals(140, newManager.getAmountRemaining());
        Assert.assertEquals("Catacombs of Kourend", newManager.getLocation());
        Assert.assertEquals("Konar quo Maten", newManager.getSlayerMaster());
        Assert.assertTrue(newManager.hasActiveTask());
    }

    @Test
    public void testSlayerMasterAssignmentsLoaded()
    {
        List<com.osrscopilot.data.model.SlayerTaskAssignment> duradelTasks = taskManager.getAssignmentsForMaster("Duradel");
        Assert.assertNotNull("Duradel tasks must not be null", duradelTasks);
        Assert.assertTrue("Duradel must have at least 40 task assignments", duradelTasks.size() >= 40);

        boolean foundAbyssal = duradelTasks.stream().anyMatch(t -> t.getMonster().equalsIgnoreCase("Abyssal demons"));
        Assert.assertTrue("Duradel must assign Abyssal demons", foundAbyssal);

        List<com.osrscopilot.data.model.SlayerTaskAssignment> konarTasks = taskManager.getAssignmentsForMaster("Konar quo Maten");
        Assert.assertNotNull("Konar tasks must not be null", konarTasks);
        Assert.assertTrue("Konar must have at least 35 task assignments", konarTasks.size() >= 35);

        List<com.osrscopilot.data.model.SlayerTaskAssignment> turaelTasks = taskManager.getAssignmentsForMaster("Turael / Aya");
        Assert.assertNotNull("Turael tasks must not be null", turaelTasks);
        Assert.assertTrue("Turael must have at least 20 task assignments", turaelTasks.size() >= 20);

        List<com.osrscopilot.data.model.SlayerTaskAssignment> krystiliaTasks = taskManager.getAssignmentsForMaster("Krystilia");
        Assert.assertNotNull("Krystilia tasks must not be null", krystiliaTasks);
        Assert.assertTrue("Krystilia must have at least 35 task assignments", krystiliaTasks.size() >= 35);
    }

    @Test
    public void testSlayerMasterAccurateCoordinates()
    {
        Assert.assertEquals(new net.runelite.api.coords.WorldPoint(2931, 3536, 0), SlayerMaster.TURAEL.getLocationPoint());
        Assert.assertEquals(new net.runelite.api.coords.WorldPoint(3091, 3267, 0), SlayerMaster.SPRIA.getLocationPoint());
        Assert.assertEquals(new net.runelite.api.coords.WorldPoint(3510, 3508, 0), SlayerMaster.MAZCHNA.getLocationPoint());
        Assert.assertEquals(new net.runelite.api.coords.WorldPoint(3145, 9914, 0), SlayerMaster.VANNAKA.getLocationPoint());
        Assert.assertEquals(new net.runelite.api.coords.WorldPoint(2446, 4431, 0), SlayerMaster.CHAELDAR.getLocationPoint());
        Assert.assertEquals(new net.runelite.api.coords.WorldPoint(1310, 3810, 0), SlayerMaster.KONAR.getLocationPoint());
        Assert.assertEquals(new net.runelite.api.coords.WorldPoint(2432, 3423, 0), SlayerMaster.NIEVE.getLocationPoint());
        Assert.assertEquals(new net.runelite.api.coords.WorldPoint(2869, 2982, 1), SlayerMaster.DURADEL.getLocationPoint());
        Assert.assertEquals(new net.runelite.api.coords.WorldPoint(3092, 3506, 0), SlayerMaster.KRYSTILIA.getLocationPoint());
    }

    @Test
    public void testLiveClientVarPlayerSlayerTaskTracking()
    {
        net.runelite.api.Client client = Mockito.mock(net.runelite.api.Client.class);
        Mockito.when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);

        // Assign task
        taskManager.setTaskDetails("Cave horrors", 130, 130, "Mos Le'Harmless", "Duradel");
        Assert.assertEquals(130, taskManager.getAmountRemaining());

        // Player kills 5 Cave horrors -> VarPlayer.SLAYER_TASK_SIZE updates to 125
        Mockito.when(client.getVarpValue(net.runelite.api.VarPlayer.SLAYER_TASK_SIZE)).thenReturn(125);
        Mockito.when(client.getVarbitValue(net.runelite.api.Varbits.SLAYER_POINTS)).thenReturn(450);
        Mockito.when(client.getVarbitValue(net.runelite.api.Varbits.SLAYER_TASK_STREAK)).thenReturn(18);

        taskManager.updateFromClient(client);

        Assert.assertEquals(125, taskManager.getAmountRemaining());
        Assert.assertEquals(130, taskManager.getInitialAmount());
        Assert.assertEquals(450, taskManager.getSlayerPoints());
        Assert.assertEquals(18, taskManager.getTaskStreak());
        Assert.assertEquals(5, taskManager.getKillsCompleted());

        // Task complete -> VarPlayer drops to 0. A single 0 is ignored (transient login value);
        // a sustained 0 completes the task.
        Mockito.when(client.getVarpValue(net.runelite.api.VarPlayer.SLAYER_TASK_SIZE)).thenReturn(0);
        taskManager.updateFromClient(client);
        Assert.assertEquals("one transient 0 must not complete the task", 125, taskManager.getAmountRemaining());
        taskManager.updateFromClient(client);
        Assert.assertEquals(0, taskManager.getAmountRemaining());
    }

    @Test
    public void testSameMonsterFullReassignmentResetsTotals()
    {
        taskManager.setTaskDetails("Abyssal demons", 150, 150, null, "Duradel");
        taskManager.addTaskLoot(800_000L);
        taskManager.addTaskSupplyCost(200_000L);
        // progress on the same task keeps the tally
        taskManager.setTaskDetails("Abyssal demons", 150, 40, null, "Duradel");
        Assert.assertEquals(600_000L, taskManager.getTaskNetGp());
        // block/prefer list hands out the same monster again at a full count -> fresh task
        taskManager.setTaskDetails("Abyssal demons", 170, 170, null, "Duradel");
        Assert.assertEquals(0L, taskManager.getTaskLootValueGp());
        Assert.assertEquals(0L, taskManager.getTaskSupplyCostGp());
        Assert.assertEquals(0L, taskManager.getTaskNetGp());
    }

    @Test
    public void testTaskTotalsPersistAcrossRelog()
    {
        taskManager.setTaskDetails("Gargoyles", 185, 185, null, "Duradel");
        taskManager.addTaskLoot(1_250_000L);
        taskManager.addTaskSupplyCost(300_000L);
        taskManager.setTaskDetails("Gargoyles", 185, 120, null, "Duradel"); // progress persisted

        SlayerTaskManager reloaded = new SlayerTaskManager(configManager, monsterDatabase);
        reloaded.loadFromConfig();

        Assert.assertEquals("Gargoyles", reloaded.getMonsterName());
        Assert.assertEquals(1_250_000L, reloaded.getTaskLootValueGp());
        Assert.assertEquals(300_000L, reloaded.getTaskSupplyCostGp());
        Assert.assertEquals(950_000L, reloaded.getTaskNetGp());
    }

    @Test
    public void testSyncFromSlayerPluginConfig()
    {
        configStorage.put("slayer.taskName", "Gargoyles");
        configStorage.put("slayer.amount", "74");
        configStorage.put("slayer.initialAmount", "165");
        configStorage.put("slayer.slayerLocation", "Slayer Tower");
        configStorage.put("slayer.slayerMaster", "Duradel");

        taskManager.syncFromSlayerPluginConfig(configManager);

        Assert.assertEquals("Gargoyles", taskManager.getMonsterName());
        Assert.assertEquals(74, taskManager.getAmountRemaining());
        Assert.assertEquals(165, taskManager.getInitialAmount());
        Assert.assertEquals("Slayer Tower", taskManager.getLocation());
        Assert.assertEquals("Duradel", taskManager.getSlayerMaster());
        Assert.assertTrue(taskManager.hasActiveTask());
    }

    @Test
    public void testDuradelDialogueDetection()
    {
        taskManager.parseChatMessage("Duradel: Your new task is to kill 132 Cave horrors.");
        Assert.assertEquals("Cave horrors", taskManager.getMonsterName());
        Assert.assertEquals(132, taskManager.getAmountRemaining());
        Assert.assertEquals("Duradel", taskManager.getSlayerMaster());
    }


    @Test
    public void testSlayerRewardCatalogUnlocks()
    {
        com.osrscopilot.data.SlayerRewardCatalog catalog = taskManager.getRewardCatalog();
        Assert.assertNotNull(catalog);
        Assert.assertTrue("Catalog must contain at least 35 rewards", catalog.getAllRewards().size() >= 35);

        net.runelite.api.Client client = Mockito.mock(net.runelite.api.Client.class);
        Mockito.when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
        Mockito.when(client.getVarbitValue(5358)).thenReturn(1); // Verified Bigger and Badder varbit 5358

        catalog.updateUnlockStatus(client);

        com.osrscopilot.data.model.SlayerReward bnb = catalog.getAllRewards().stream()
            .filter(r -> r.getName().equalsIgnoreCase("Bigger and Badder"))
            .findFirst().orElse(null);

        Assert.assertNotNull(bnb);
        Assert.assertTrue("Bigger and Badder must be unlocked", bnb.isUnlocked());
        Assert.assertEquals("Bigger and Badder is 50 pts per the OSRS Wiki", 50, bnb.getCost());
        Assert.assertEquals(com.osrscopilot.data.model.SlayerReward.RewardType.UNLOCK, bnb.getType());

        // The helmet recolours are cosmetics, not gameplay unlocks.
        com.osrscopilot.data.model.SlayerReward kbb = catalog.getAllRewards().stream()
            .filter(r -> r.getName().equalsIgnoreCase("King Black Bonnet"))
            .findFirst().orElse(null);
        Assert.assertNotNull(kbb);
        Assert.assertEquals(com.osrscopilot.data.model.SlayerReward.RewardType.COSMETIC, kbb.getType());
    }

    @Test
    public void testSlayerRewardCatalogHasNoDuplicateVarbitIds()
    {
        // Every reward with a known varbit (> 0) must map to a DISTINCT unlock - a shared varbit
        // makes one reward mirror another's unlocked state in the UI. "Augment my Abbies" and
        // "Smell Ya Later" previously both carried 4090.
        com.osrscopilot.data.SlayerRewardCatalog catalog = taskManager.getRewardCatalog();
        java.util.Map<Integer, String> seen = new java.util.HashMap<>();
        for (com.osrscopilot.data.model.SlayerReward r : catalog.getAllRewards())
        {
            int v = r.getVarbitId();
            if (v <= 0)
            {
                continue;
            }
            String prev = seen.put(v, r.getName());
            Assert.assertNull("Varbit " + v + " is shared by '" + prev + "' and '" + r.getName() + "'", prev);
        }

        // The specific former collision: exactly one of the pair keeps a real varbit, the other is
        // marked unknown (-1) rather than duplicating.
        com.osrscopilot.data.model.SlayerReward smell = catalog.getAllRewards().stream()
            .filter(r -> r.getName().equalsIgnoreCase("Smell Ya Later")).findFirst().orElse(null);
        com.osrscopilot.data.model.SlayerReward abbies = catalog.getAllRewards().stream()
            .filter(r -> r.getName().equalsIgnoreCase("Augment my Abbies")).findFirst().orElse(null);
        Assert.assertNotNull(smell);
        Assert.assertNotNull(abbies);
        Assert.assertEquals(4090, smell.getVarbitId());
        Assert.assertEquals(-1, abbies.getVarbitId());
    }

    @Test
    public void testMasterMilestonePointsCalculation()
    {
        com.osrscopilot.data.model.SlayerMaster konar = com.osrscopilot.data.model.SlayerMaster.KONAR;
        Assert.assertEquals(18, konar.getBasePoints());
        Assert.assertEquals(18, konar.getPointsForStreak(1));
        Assert.assertEquals(90, konar.getPointsForStreak(10));
        Assert.assertEquals(270, konar.getPointsForStreak(50));
        Assert.assertEquals(450, konar.getPointsForStreak(100));
        Assert.assertEquals(630, konar.getPointsForStreak(250));
        Assert.assertEquals(900, konar.getPointsForStreak(1000));

        com.osrscopilot.data.model.SlayerMaster vannaka = com.osrscopilot.data.model.SlayerMaster.VANNAKA;
        Assert.assertEquals(4, vannaka.getBasePoints());
        Assert.assertEquals(20, vannaka.getPointsForStreak(10));
        Assert.assertEquals(60, vannaka.getPointsForStreak(50));
        Assert.assertEquals(100, vannaka.getPointsForStreak(100));

        com.osrscopilot.data.model.SlayerMaster turael = com.osrscopilot.data.model.SlayerMaster.TURAEL;
        Assert.assertEquals(0, turael.getBasePoints());
        Assert.assertEquals(0, turael.getPointsForStreak(50));
    }

    @Test
    public void testTaskXpAndLootTracking()
    {
        taskManager.setTaskDetails("Cave horrors", 130, 130, "Mos Le'Harmless Caves", "Konar");
        Assert.assertEquals(0, taskManager.getTaskSlayerXpEarned());
        Assert.assertEquals(0L, taskManager.getTaskLootValueGp());

        // Simulate XP gain
        net.runelite.api.Client client = Mockito.mock(net.runelite.api.Client.class);
        Mockito.when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);

        net.runelite.api.events.StatChanged event1 = new net.runelite.api.events.StatChanged(net.runelite.api.Skill.SLAYER, 100000, 70, 70);
        taskManager.onStatChanged(client, event1);

        net.runelite.api.events.StatChanged event2 = new net.runelite.api.events.StatChanged(net.runelite.api.Skill.SLAYER, 100130, 70, 70);
        taskManager.onStatChanged(client, event2);

        Assert.assertEquals(130, taskManager.getTaskSlayerXpEarned());

        // Simulate Loot gain
        taskManager.addTaskLoot(450000L);
        Assert.assertEquals(450000L, taskManager.getTaskLootValueGp());
    }

    @Test
    public void testMetalDragonsGroupTaskAndSubtypeToggle()
    {
        taskManager.setTaskDetails("Metal dragons", 35, 35, null, "Duradel");

        Assert.assertTrue(SlayerTaskManager.isGroupTaskName("Metal dragons"));
        Assert.assertEquals("Metal dragons", taskManager.getTaskName());
        Assert.assertEquals("all six metal dragons", 6, taskManager.getGroupMembers().size());
        Assert.assertTrue(taskManager.getGroupMembers().contains("Rune dragon"));
        Assert.assertTrue("starts on Auto", taskManager.isAutoSubtype());
        Assert.assertEquals("Bronze dragon", taskManager.getEffectiveMonsterName()); // Auto, no kills yet -> first member

        // Auto mode follows what you actually kill.
        taskManager.noteKilledNpc("Rune dragon");
        Assert.assertEquals("Rune dragon", taskManager.getEffectiveMonsterName());
        Assert.assertTrue(taskManager.isAutoSubtype());

        // Pinning a member overrides Auto.
        taskManager.setTaskSubtype("Iron dragon");
        Assert.assertFalse(taskManager.isAutoSubtype());
        Assert.assertEquals("Iron dragon", taskManager.getEffectiveMonsterName());
        Assert.assertEquals("Metal dragons", taskManager.getTaskName()); // title stays the group

        // Back to Auto - and it still remembers the last-seen kill.
        taskManager.setTaskSubtype("Auto - follows your kills");
        Assert.assertTrue(taskManager.isAutoSubtype());
        Assert.assertEquals("Rune dragon", taskManager.getEffectiveMonsterName());

        // A genuinely new assignment forgets both the pin and the auto-detected member.
        taskManager.setTaskDetails("Bandits", 120, 120, null, "Duradel");
        Assert.assertNull(taskManager.getTaskSubtype());
        Assert.assertNull(taskManager.getAutoDetectedMember());
        Assert.assertFalse(SlayerTaskManager.isGroupTaskName("Bandits"));
    }

    @Test
    public void testDemoTaskOverrideIsTransientAndRestoresTheRealTask()
    {
        // A real, persisted task.
        taskManager.setTaskDetails("Gargoyles", 165, 120, null, "Duradel");
        Assert.assertEquals("Gargoyles", configStorage.get("osrscopilot.slayerMonster"));

        // Install the tour's demo task.
        taskManager.setDemoTask("Metal dragons", 35, 35, null, "Duradel");
        Assert.assertTrue(taskManager.isDemoTask());
        Assert.assertEquals("Metal dragons", taskManager.getTaskName());
        Assert.assertEquals(35, taskManager.getAmountRemaining());

        // Live-sync paths are ignored while the demo task is up...
        Assert.assertFalse(taskManager.parseChatMessage("You have been assigned to kill Trolls (x150)."));
        taskManager.decrementKill();
        Assert.assertEquals("Metal dragons", taskManager.getTaskName());
        Assert.assertEquals(35, taskManager.getAmountRemaining());

        // ...and nothing demo touches disk - config still holds the real task.
        Assert.assertEquals("Gargoyles", configStorage.get("osrscopilot.slayerMonster"));
        Assert.assertEquals("120", configStorage.get("osrscopilot.slayerAmountRemaining"));

        // The subtype picker still works during the demo (the tour drives it).
        taskManager.noteKilledNpc("Rune dragon");
        Assert.assertEquals("Rune dragon", taskManager.getEffectiveMonsterName());

        // Tearing it down restores the real task exactly.
        taskManager.clearDemoTask();
        Assert.assertFalse(taskManager.isDemoTask());
        Assert.assertEquals("Gargoyles", taskManager.getTaskName());
        Assert.assertEquals(120, taskManager.getAmountRemaining());

        // A fresh manager reads the real task back - the demo never persisted.
        SlayerTaskManager reloaded = new SlayerTaskManager(configManager, monsterDatabase);
        reloaded.loadFromConfig();
        Assert.assertEquals("Gargoyles", reloaded.getTaskName());
        Assert.assertEquals(120, reloaded.getAmountRemaining());
    }

    @Test
    public void testCatacombsVariantTasksAreGroupsAndAutoFollow()
    {
        // "Bloodveld" done in the Catacombs of Kourend = Mutated Bloodveld, and it counts.
        taskManager.setTaskDetails("Bloodveld", 140, 140, null, "Duradel");
        Assert.assertTrue(SlayerTaskManager.isGroupTaskName("Bloodveld"));
        Assert.assertTrue(taskManager.isAutoSubtype());
        Assert.assertEquals("Bloodveld", taskManager.getEffectiveMonsterName()); // Auto, first member

        taskManager.noteKilledNpc("Mutated Bloodveld");
        Assert.assertEquals("Mutated Bloodveld", taskManager.getEffectiveMonsterName());

        // The slayer bosses count for their base task too.
        taskManager.setTaskDetails("Hellhounds", 150, 150, null, "Duradel");
        taskManager.noteKilledNpc("Cerberus");
        Assert.assertEquals("Cerberus", taskManager.getEffectiveMonsterName());

        // A non-umbrella task is left alone.
        taskManager.setTaskDetails("Fire giants", 130, 130, null, "Duradel");
        Assert.assertFalse(SlayerTaskManager.isGroupTaskName("Fire giants"));
        Assert.assertEquals("Fire giants", taskManager.getEffectiveMonsterName());
    }

    @Test
    public void testTaskBlockListTogglesAndReports()
    {
        Assert.assertFalse(taskManager.isTaskBlocked("Gargoyles"));
        Assert.assertNull(taskManager.getTaskUnavailableReason("Gargoyles"));

        taskManager.setTaskBlocked("Gargoyles", true);
        Assert.assertTrue(taskManager.isTaskBlocked("gargoyles")); // case-insensitive
        Assert.assertTrue(taskManager.getTaskUnavailableReason("Gargoyles").startsWith("Blocked"));
        Assert.assertTrue("block list persisted to config",
            configStorage.getOrDefault("osrscopilot.slayerBlockedTasks", "").contains("gargoyles"));
        Assert.assertTrue(taskManager.getBlockedTasks().contains("gargoyles"));

        taskManager.setTaskBlocked("Gargoyles", false);
        Assert.assertFalse(taskManager.isTaskBlocked("Gargoyles"));
        Assert.assertNull(taskManager.getTaskUnavailableReason("Gargoyles"));
    }

    @Test
    public void testUnlockGatedTasksReportLocked()
    {
        // Fresh manager -> SlayerRewardCatalog has nothing unlocked yet.
        Assert.assertEquals("Seeing Red", taskManager.getUnlockGateFor("Red dragons"));
        Assert.assertFalse(taskManager.isRewardUnlocked("Seeing Red"));
        String reason = taskManager.getTaskUnavailableReason("Red dragons");
        Assert.assertNotNull(reason);
        Assert.assertTrue(reason, reason.startsWith("Locked") && reason.contains("Seeing Red"));

        // A task with no unlock gate is always available.
        Assert.assertNull(taskManager.getUnlockGateFor("Bloodveld"));
        Assert.assertNull(taskManager.getTaskUnavailableReason("Bloodveld"));
    }

    @Test
    public void testMortimerMasterExists()
    {
        SlayerMaster mortimer = SlayerMaster.findByName("Mortimer");
        Assert.assertNotNull("Mortimer / Wyrmscraig master must exist", mortimer);
        Assert.assertEquals("Wyrmscraig Cavern", mortimer.getLocationName());
        Assert.assertEquals(2589, mortimer.getLocationPoint().getX());
        Assert.assertEquals(8614, mortimer.getLocationPoint().getY());
        Assert.assertEquals(100, mortimer.getCombatRequirement());
        Assert.assertEquals(70, mortimer.getSlayerRequirement());
        Assert.assertTrue(SlayerMaster.ALL_MASTERS.contains(mortimer));

        // Task list is populated from the wiki (29 assignments).
        java.util.List<com.osrscopilot.data.model.SlayerTaskAssignment> tasks =
            taskManager.getAssignmentsForMaster("Mortimer");
        Assert.assertEquals(29, tasks.size());
        Assert.assertTrue(tasks.stream().anyMatch(t -> "Abyssal demons".equals(t.getMonster()) && t.getWeight() == 8));
        Assert.assertTrue(tasks.stream().anyMatch(t -> "Hydras".equals(t.getMonster())));
    }

    @Test
    public void testDisplayTaskNameSentenceCases()
    {
        Assert.assertEquals("Metal dragons", SlayerTaskManager.displayTaskName("metal dragons"));
        Assert.assertEquals("Aberrant spectres", SlayerTaskManager.displayTaskName("Aberrant spectres"));
        Assert.assertNull(SlayerTaskManager.displayTaskName(null));
    }

    @Test
    public void testMasterResolutionIsExactNotLooseSubstring()
    {
        Assert.assertEquals("Duradel", SlayerMaster.findByName("Duradel").getName());
        Assert.assertSame(SlayerMaster.NIEVE, SlayerMaster.findByName("assigned by Nieve"));
        Assert.assertSame(SlayerMaster.KONAR, SlayerMaster.findByName("Konar"));
        Assert.assertNull(SlayerMaster.findByName("Bob"));
        Assert.assertNull(SlayerMaster.findByName(""));
    }

    /** Either alias of a compound master name resolves - "Steve" is Nieve post-MM2, "Aya" is Turael. */
    @Test
    public void testMasterResolutionAcceptsCompoundNameAliases()
    {
        Assert.assertSame(SlayerMaster.NIEVE, SlayerMaster.findByName("Steve"));
        Assert.assertSame(SlayerMaster.NIEVE, SlayerMaster.findByName("Nieve"));
        Assert.assertSame(SlayerMaster.TURAEL, SlayerMaster.findByName("Aya"));
        Assert.assertSame(SlayerMaster.TURAEL, SlayerMaster.findByName("Turael"));
        // a short alias only matches exactly, never as a stray substring
        Assert.assertNull(SlayerMaster.findByName("Ayabahamut"));
    }

    /** A dialogue header ("Steve:") sets the master without needing the stock Slayer plugin. */
    @Test
    public void testMasterDetectedFromDialogueHeaderAlias()
    {
        taskManager.parseChatMessage("Steve: Excellent, you're back. I need you to kill 122 aberrant spectres.");
        Assert.assertEquals("Nieve / Steve", taskManager.getSlayerMaster());

        taskManager.parseChatMessage("Aya: Here's a nice safe task for you.");
        Assert.assertEquals("Turael / Aya", taskManager.getSlayerMaster());
    }

    @Test
    public void testUnknownMasterClearedOnNewAssignment()
    {
        taskManager.setTaskDetails("Gargoyles", 90, 90, null, "Duradel");
        Assert.assertEquals("Duradel", taskManager.getSlayerMaster());

        // New task, no resolvable master supplied -> don't carry the stale one.
        taskManager.setTaskDetails("Nechryael", 130, 130, null, null);
        Assert.assertNull(taskManager.getSlayerMaster());
    }

    @Test
    public void testTaskSupplyCostAndNetGp()
    {
        taskManager.setTaskDetails("Abyssal demons", 150, 150, null, "Duradel");
        Assert.assertEquals(0L, taskManager.getTaskSupplyCostGp());
        Assert.assertEquals(0L, taskManager.getTaskNetGp());

        taskManager.addTaskLoot(1_000_000L);
        taskManager.addTaskSupplyCost(250_000L);
        taskManager.addTaskSupplyCost(50_000L);

        Assert.assertEquals(300_000L, taskManager.getTaskSupplyCostGp());
        Assert.assertEquals(700_000L, taskManager.getTaskNetGp());

        // A progress update for the SAME monster keeps the running tally.
        taskManager.setTaskDetails("Abyssal demons", 150, 90, null, "Duradel");
        Assert.assertEquals(700_000L, taskManager.getTaskNetGp());

        // A genuinely new assignment resets it.
        taskManager.setTaskDetails("Gargoyles", 180, 180, null, "Duradel");
        Assert.assertEquals(0L, taskManager.getTaskLootValueGp());
        Assert.assertEquals(0L, taskManager.getTaskSupplyCostGp());
        Assert.assertEquals(0L, taskManager.getTaskNetGp());
    }
}

