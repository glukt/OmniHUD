package com.osrscopilot.loot;

import com.google.gson.Gson;
import com.osrscopilot.loot.model.LootItem;
import com.osrscopilot.loot.model.LootRecord;
import com.osrscopilot.loot.model.MonsterLootSummary;
import com.osrscopilot.loot.model.SourceKind;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LootTrackerManagerTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File lootDir;
    private final Gson gson = new Gson();

    @Before
    public void setUp()
    {
        lootDir = new File(tmp.getRoot(), "loot");
    }

    private LootTrackerManager newManager()
    {
        return new LootTrackerManager(gson, null, lootDir);
    }

    private static LootItem item(int id, int qty, long ge)
    {
        return LootItem.builder().itemId(id).quantity(qty).gePriceEach(ge).haPriceEach(ge / 3).build();
    }

    /** hasUnresolvedItemNames() is an incremental gate: false when every drop has a name. */
    @Test
    public void testUnresolvedItemNamesGate()
    {
        LootTrackerManager m = newManager();
        m.setActiveCharacter(700L, "Namer");
        Assert.assertFalse("empty store has nothing to resolve", m.hasUnresolvedItemNames());

        // item(...) builds a LootItem with no name -> the stat lands nameless.
        m.recordKill("Goblin");
        m.recordLoot(SourceKind.NPC_KILL, 3029, "Goblin", 5, 301, 1, null, false,
            Arrays.asList(item(526, 1, 120)));
        Assert.assertTrue("a nameless drop needs resolving", m.hasUnresolvedItemNames());

        m.applyItemName(526, "Bones");
        Assert.assertFalse("all names resolved -> gate closes", m.hasUnresolvedItemNames());

        // A second nameless drop reopens the gate; resolving it closes it again.
        m.recordKill("Goblin");
        m.recordLoot(SourceKind.NPC_KILL, 3029, "Goblin", 6, 302, 1, null, false,
            Arrays.asList(item(882, 5, 3)));
        Assert.assertTrue(m.hasUnresolvedItemNames());
        m.applyItemName(882, "Bronze arrow");
        Assert.assertFalse(m.hasUnresolvedItemNames());
    }

    @Test
    public void testRecordLootAndKillCount()
    {
        LootTrackerManager m = newManager();
        m.setActiveCharacter(123L, "Zezima");

        m.recordKill("Goblin");
        m.recordKill("Goblin");
        m.recordKill("Goblin");
        Assert.assertEquals(3, m.currentKillCount("Goblin"));

        LootRecord rec = m.recordLoot(SourceKind.NPC_KILL, 3029, "Goblin", 5, 301, 12850, null, false,
            Arrays.asList(item(526, 1, 120), item(995, 15, 1)));
        Assert.assertNotNull(rec);
        Assert.assertEquals(3, rec.getKillCountAtDrop());
        Assert.assertEquals("NPC_KILL:goblin", rec.sourceKey());
        Assert.assertEquals(120 + 15, rec.totalBestValue());

        MonsterLootSummary s = m.getSummary("NPC_KILL:goblin");
        Assert.assertNotNull(s);
        Assert.assertEquals(3, s.getTotalKills());
        Assert.assertEquals(1, s.getLootedKills());
        Assert.assertTrue(s.getItemStats().containsKey(526));
        Assert.assertEquals(1, s.getItemStats().get(526).getCount());
        Assert.assertEquals(3, s.getItemStats().get(526).getKillCountAtLast());
    }

    /** FIX 1: raiseKillCount is Math.max into the counter - it lifts a low count, never lowers one. */
    @Test
    public void testRaiseKillCountIsMaxAndNeverLowers()
    {
        LootTrackerManager m = newManager();
        m.setActiveCharacter(1L, "KcTester");

        m.recordKill("Vorkath"); // captured progress: 1
        Assert.assertEquals(1, m.currentKillCount("Vorkath"));

        m.raiseKillCount("Vorkath", 250); // the game says we're actually at 250
        Assert.assertEquals(250, m.currentKillCount("Vorkath"));
        Assert.assertEquals(250, m.getSummary("NPC_KILL:vorkath").getTotalKills());

        m.raiseKillCount("Vorkath", 40); // a stale / lower line must not roll it back
        Assert.assertEquals("Math.max only", 250, m.currentKillCount("Vorkath"));

        m.recordKill("Vorkath"); // real kills still advance from the corrected base
        Assert.assertEquals(251, m.currentKillCount("Vorkath"));
    }

    @Test
    public void testRecentTouchOrderingAndClearAll()
    {
        LootTrackerManager m = newManager();
        m.setActiveCharacter(88L, "Order");

        m.recordKill("Cow");
        m.recordLoot(SourceKind.NPC_KILL, 1, "Chicken", 1, 301, 1, null, false,
            Collections.singletonList(item(2138, 1, 3)));
        m.recordKill("Cow"); // Cow acted on last -> highest touch seq

        MonsterLootSummary cow = m.getSummary("NPC_KILL:cow");
        MonsterLootSummary chicken = m.getSummary("NPC_KILL:chicken");
        Assert.assertTrue("most-recent source has the highest touch seq",
            cow.getLastTouchSeq() > chicken.getLastTouchSeq());

        Assert.assertTrue("pollChanged() reports activity then clears", m.pollChanged());
        Assert.assertFalse(m.pollChanged());

        m.clearAll();
        Assert.assertEquals(0, m.getRecordCount());
        Assert.assertTrue(m.getSummaries().isEmpty());
        Assert.assertTrue("wipe marks imported so the stock backfill won't refill it", m.hasImported());
    }

    @Test
    public void testPickpocketAdvancesKcAndKeepsItemNamesAndFiresListener()
    {
        LootTrackerManager m = newManager();
        m.setActiveCharacter(55L, "Thiever");

        int[] fired = {0};
        m.addChangeListener(() -> fired[0]++);

        for (int i = 1; i <= 3; i++)
        {
            m.recordLoot(SourceKind.PICKPOCKET, -1, "Guard", 21, 301, 1, null, false,
                Collections.singletonList(LootItem.builder().itemId(22588).quantity(1).name("Coin pouch").build()));
        }

        Assert.assertTrue("change listener fires on capture", fired[0] >= 3);

        MonsterLootSummary s = m.getSummary("PICKPOCKET:guard");
        Assert.assertNotNull("pickpocket source is tracked", s);
        Assert.assertEquals("KC advances per pickpocket", 3, s.getTotalKills());
        Assert.assertEquals("Coin pouch", s.getItemStats().get(22588).getItemName());
        Assert.assertTrue("last-seen is set so 'Recent' sort works", s.getLastSeenMs() > 0);

        // No phantom NPC_KILL:guard leaks in from the pp: counter.
        Assert.assertNull(m.getSummary("NPC_KILL:guard"));
        Assert.assertNull(m.getSummary("NPC_KILL:pp:guard"));
    }

    @Test
    public void testSlayerTourDemoLootIsTransientAndRestoresReal()
    {
        LootTrackerManager m = newManager();
        m.setActiveCharacter(999L, "Real");
        m.recordKill("Goblin");
        m.recordLoot(SourceKind.NPC_KILL, 3029, "Goblin", 5, 301, 1, null, false,
            java.util.Arrays.asList(item(526, 1, 120)));
        int realRecords = m.getAllRecords().size();
        Assert.assertEquals(1, realRecords);

        m.installSlayerTourDemoLoot();
        Assert.assertTrue(m.isDemoLoot());
        Assert.assertEquals(90, m.currentKillCount("Rune dragon"));
        MonsterLootSummary rd = m.getSummaries().stream()
            .filter(s -> "Rune dragon".equalsIgnoreCase(s.getSourceName())).findFirst().orElse(null);
        Assert.assertNotNull("demo loot shows a Rune dragon source", rd);
        Assert.assertEquals(90, rd.getTotalKills());
        Assert.assertTrue("demo has the always-drops + rares", m.getAllRecords().size() >= 90);

        // Real captures are ignored while the demo is installed - nothing persists.
        Assert.assertNull(m.recordLoot(SourceKind.NPC_KILL, 1, "Goblin", 1, 1, 1, null, false,
            java.util.Arrays.asList(item(526, 1, 1))));
        m.recordKill("Goblin");
        Assert.assertFalse("no loot file written during the demo", new File(lootDir, "999.json.gz").exists());

        m.clearDemoLoot();
        Assert.assertFalse(m.isDemoLoot());
        Assert.assertEquals(realRecords, m.getAllRecords().size());
        Assert.assertEquals(0, m.currentKillCount("Rune dragon"));
        Assert.assertEquals("real Goblin KC restored, demo-time recordKill ignored", 1, m.currentKillCount("Goblin"));
    }

    @Test
    public void testEmptyLootReturnsNull()
    {
        LootTrackerManager m = newManager();
        m.setActiveCharacter(1L, "A");
        Assert.assertNull(m.recordLoot(SourceKind.NPC_KILL, 1, "X", 1, 1, 1, null, false, Collections.emptyList()));
        Assert.assertNull(m.recordLoot(SourceKind.NPC_KILL, 1, "X", 1, 1, 1, null, false, null));
        Assert.assertEquals(0, m.getRecordCount());
    }

    @Test
    public void testPersistenceRoundTrip()
    {
        LootTrackerManager m1 = newManager();
        m1.setActiveCharacter(555L, "Iron");
        m1.recordKill("Vorkath");
        m1.recordKill("Vorkath");
        m1.recordLoot(SourceKind.NPC_KILL, 8059, "Vorkath", 732, 302, 8_1_92, "Blue dragons", true,
            Arrays.asList(item(11286, 1, 5_000_000L)));  // Draconic visage-ish
        m1.flushNow();

        Assert.assertTrue("store file written", new File(lootDir, Long.toUnsignedString(555L) + ".json.gz").exists());

        LootTrackerManager m2 = newManager();
        m2.setActiveCharacter(555L, "Iron");
        Assert.assertEquals(1, m2.getRecordCount());
        Assert.assertEquals(2, m2.currentKillCount("Vorkath"));

        LootRecord r = m2.getAllRecords().get(0);
        Assert.assertEquals("Vorkath", r.getSourceName());
        Assert.assertEquals(8059, r.getNpcId());
        Assert.assertTrue(r.isInstanced());
        Assert.assertEquals("Blue dragons", r.getSlayerTaskName());
        Assert.assertEquals(1, r.getItems().size());
        Assert.assertEquals(11286, r.getItems().get(0).getItemId());

        MonsterLootSummary s = m2.getSummary("NPC_KILL:vorkath");
        Assert.assertNotNull(s);
        Assert.assertEquals(2, s.getTotalKills());
        Assert.assertEquals(5_000_000L, s.getTotalBestValue());

        // A fresh drop after reload keeps incrementing ids (no collision with the restored one).
        LootRecord r2 = m2.recordLoot(SourceKind.NPC_KILL, 8059, "Vorkath", 732, 302, 8192, null, false,
            Arrays.asList(item(526, 1, 100)));
        Assert.assertTrue(r2.getId() > r.getId());
    }

    @Test
    public void testMultiCharacterIsolation()
    {
        LootTrackerManager m = newManager();
        m.setActiveCharacter(1L, "Main");
        m.recordLoot(SourceKind.NPC_KILL, 1, "Cow", 2, 301, 1, null, false, Arrays.asList(item(1739, 1, 2)));
        m.flushNow();

        m.setActiveCharacter(2L, "Alt");
        Assert.assertEquals("switching character starts empty", 0, m.getRecordCount());
        m.recordLoot(SourceKind.NPC_KILL, 2, "Chicken", 1, 301, 1, null, false, Arrays.asList(item(2138, 1, 3)));
        m.flushNow();

        m.setActiveCharacter(1L, "Main");
        Assert.assertEquals(1, m.getRecordCount());
        Assert.assertEquals("Cow", m.getAllRecords().get(0).getSourceName());
    }

    @Test
    public void testWriteCsv() throws Exception
    {
        LootTrackerManager m = newManager();
        m.setActiveCharacter(3L, "Csv, Tester");
        m.recordKill("Gargoyle");
        m.recordLoot(SourceKind.NPC_KILL, 412, "Gargoyle", 111, 301, 12850, "Gargoyles", false,
            Arrays.asList(item(4153, 1, 28_000), item(536, 5, 100)));

        java.io.StringWriter sw = new java.io.StringWriter();
        m.writeCsv(sw);
        String csv = sw.toString();
        String[] lines = csv.split("\r\n");
        Assert.assertTrue(lines[0].startsWith("timestamp,character,source_kind"));
        Assert.assertEquals("header + one row per item stack", 3, lines.length);
        Assert.assertTrue("comma in a field is quoted", csv.contains("\"Csv, Tester\""));
        Assert.assertTrue(csv.contains(",4153,1,28000,"));
        Assert.assertTrue(csv.contains(",Gargoyles,false,false"));
    }

    @Test
    public void testImportFromStock()
    {
        LootTrackerManager m = newManager();
        m.setActiveCharacter(77L, "Importer");
        Assert.assertFalse(m.hasImported());

        LootTrackerManager.StockEntry e = new LootTrackerManager.StockEntry();
        e.kind = SourceKind.NPC_KILL;
        e.name = "Vorkath";
        e.kills = 350;
        e.lastMs = 1_700_000_000_000L;
        e.itemQty = new java.util.LinkedHashMap<>();
        e.itemQty.put(11286, 2);   // visage
        e.itemQty.put(526, 700);   // bones

        m.importFromStock(java.util.Collections.singletonList(e));

        Assert.assertTrue(m.hasImported());
        Assert.assertEquals(350, m.currentKillCount("Vorkath"));
        MonsterLootSummary s = m.getSummary("NPC_KILL:vorkath");
        Assert.assertNotNull(s);
        Assert.assertEquals(350, s.getTotalKills());
        Assert.assertTrue(s.getItemStats().containsKey(11286));
        Assert.assertEquals(2, s.getItemStats().get(11286).getTotalQty());
        Assert.assertTrue(m.getAllRecords().get(0).isImported());

        // A second import is a no-op (guarded), and the flag survives a reload.
        m.importFromStock(java.util.Collections.singletonList(e));
        Assert.assertEquals(1, m.getRecordCount());
        m.flushNow();
        LootTrackerManager m2 = newManager();
        m2.setActiveCharacter(77L, "Importer");
        Assert.assertTrue(m2.hasImported());
    }

    /** Imported item stats are flagged as import-only (no real per-drop count) until real capture lands. */
    @Test
    public void testImportedItemStatsAreMarkedImportedOnly()
    {
        LootTrackerManager m = newManager();
        m.setActiveCharacter(78L, "Mixed");

        LootTrackerManager.StockEntry e = new LootTrackerManager.StockEntry();
        e.kind = SourceKind.NPC_KILL;
        e.name = "Gargoyle";
        e.kills = 500;
        e.lastMs = 1_700_000_000_000L;
        e.itemQty = new java.util.LinkedHashMap<>();
        e.itemQty.put(4153, 20);   // granite maul, obtained 20x per the aggregate - but ONE import record
        m.importFromStock(java.util.Collections.singletonList(e));

        com.osrscopilot.loot.model.ItemStat imported = m.getSummary("NPC_KILL:gargoyle").getItemStats().get(4153);
        Assert.assertEquals("one aggregate drop event", 1, imported.getCount());
        Assert.assertEquals("no real drop events yet", 0, imported.getRealCount());
        Assert.assertEquals(20, imported.getTotalQty());
        Assert.assertTrue("import-only until real capture", imported.isImportedOnly());

        // A real kill+drop of the same item flips it off import-only.
        m.recordKill("Gargoyle");
        m.recordLoot(SourceKind.NPC_KILL, 1543, "Gargoyle", 111, 501, 1, null, false,
            Arrays.asList(item(4153, 1, 30_000)));
        com.osrscopilot.loot.model.ItemStat mixed = m.getSummary("NPC_KILL:gargoyle").getItemStats().get(4153);
        Assert.assertEquals(2, mixed.getCount());
        Assert.assertEquals(1, mixed.getRealCount());
        Assert.assertFalse(mixed.isImportedOnly());
    }

    /** Switching characters starts a fresh loot "This session" clock. */
    @Test
    public void testCharacterSwitchRestartsSessionClock()
    {
        LootTrackerManager m = newManager();
        m.setActiveCharacter(1L, "Main");
        long firstStart = m.getSessionStartMs();

        // let wall-clock advance a hair so the restart is observable
        try { Thread.sleep(5); } catch (InterruptedException ignored) { }

        m.setActiveCharacter(2L, "Alt");
        Assert.assertTrue("switch bumps the session clock forward", m.getSessionStartMs() > firstStart);

        long altStart = m.getSessionStartMs();
        m.setActiveCharacter(2L, "Alt"); // same account - no-op, clock unchanged
        Assert.assertEquals(altStart, m.getSessionStartMs());
    }

    @Test
    public void testItemHistoryQuery()
    {
        LootTrackerManager m = newManager();
        m.setActiveCharacter(9L, "H");
        m.recordKill("Gargoyle");
        m.recordLoot(SourceKind.NPC_KILL, 412, "Gargoyle", 111, 301, 1, null, false, Arrays.asList(item(4153, 1, 30_000)));
        m.recordKill("Gargoyle");
        m.recordLoot(SourceKind.NPC_KILL, 412, "Gargoyle", 111, 301, 1, null, false, Arrays.asList(item(536, 5, 100)));
        m.recordKill("Gargoyle");
        m.recordLoot(SourceKind.NPC_KILL, 412, "Gargoyle", 111, 301, 1, null, false, Arrays.asList(item(4153, 1, 30_000)));

        List<LootRecord> maulHistory = m.getRecordsForItem("NPC_KILL:gargoyle", 4153);
        Assert.assertEquals(2, maulHistory.size());
        Assert.assertEquals(1, maulHistory.get(0).getKillCountAtDrop());
        Assert.assertEquals(3, maulHistory.get(1).getKillCountAtDrop());
    }
}
