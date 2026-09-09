package com.osrscopilot.loot;

import com.google.gson.Gson;
import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterDrop;
import com.osrscopilot.loot.model.LootItem;
import com.osrscopilot.loot.model.MonsterLootSummary;
import com.osrscopilot.loot.model.SourceKind;
import com.osrscopilot.loot.ui.LootTabView;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JLabel;
import org.junit.Assert;
import org.junit.Test;

public class LootTabViewTest
{
    private static void collect(Component c, List<JLabel> out)
    {
        if (c instanceof JLabel)
        {
            out.add((JLabel) c);
        }
        if (c instanceof Container)
        {
            for (Component child : ((Container) c).getComponents())
            {
                collect(child, out);
            }
        }
    }

    private static String allText(Component root)
    {
        List<JLabel> labels = new ArrayList<>();
        collect(root, labels);
        StringBuilder sb = new StringBuilder();
        for (JLabel l : labels)
        {
            if (l.getText() != null)
            {
                sb.append(l.getText()).append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    public void testNullManagerRendersGracefully()
    {
        LootTabView v = new LootTabView(null, null, null, null);
        v.refresh();
        Assert.assertTrue(allText(v).toLowerCase().contains("unavailable"));
    }

    @Test
    public void testStaysWithinSidePanelWidth()
    {
        MonsterDatabase mdb = new MonsterDatabase(new Gson());
        mdb.load();

        LootTrackerManager loot = new LootTrackerManager(new Gson(), null,
            new java.io.File(System.getProperty("java.io.tmpdir"), "wmd-loot-w-" + System.nanoTime()));
        loot.setActiveCharacter(11L, "W");
        // A source with a long name + a long-named unique.
        for (int i = 0; i < 40; i++)
        {
            loot.recordKill("Vardorvis");
        }
        loot.recordLoot(SourceKind.NPC_KILL, 12223, "Vardorvis", 784, 301, 12850, "Vardorvis", true,
            Arrays.asList(LootItem.builder().itemId(28313).quantity(1).gePriceEach(120_000_000L).build()));

        LootTabView v = new LootTabView(loot, mdb, null, m -> {});
        v.setSize(net.runelite.client.ui.PluginPanel.PANEL_WIDTH, 2000);
        v.refresh();
        v.doLayout();
        forceLayout(v);

        int limit = net.runelite.client.ui.PluginPanel.PANEL_WIDTH + 4;
        java.util.List<JLabel> labels = new ArrayList<>();
        collect(v, labels);
        for (JLabel l : labels)
        {
            int pw = l.getPreferredSize().width;
            boolean ok = pw <= limit || l.getToolTipText() != null;
            Assert.assertTrue("label '" + l.getText() + "' pref width " + pw + " exceeds panel " + limit
                + " and has no tooltip", ok);
        }
    }

    private static void forceLayout(Component c)
    {
        c.setSize(c.getPreferredSize().width > 0
            ? Math.min(c.getPreferredSize().width, net.runelite.client.ui.PluginPanel.PANEL_WIDTH)
            : net.runelite.client.ui.PluginPanel.PANEL_WIDTH, Math.max(1, c.getPreferredSize().height));
        if (c instanceof Container)
        {
            ((Container) c).doLayout();
            for (Component ch : ((Container) c).getComponents())
            {
                forceLayout(ch);
            }
        }
    }

    @Test
    public void testItemGridWrapsInsteadOfSpillingOffTheRight()
    {
        MonsterDatabase mdb = new MonsterDatabase(new Gson());
        mdb.load();

        LootTrackerManager loot = new LootTrackerManager(new Gson(), null,
            new java.io.File(System.getProperty("java.io.tmpdir"), "wmd-loot-grid-" + System.nanoTime()));
        loot.setActiveCharacter(21L, "Grid");
        // A dozen distinct items from one source - more than one grid row.
        int[] ids = {1149, 1215, 1231, 1305, 1333, 1373, 4587, 6739, 11840, 11785, 11235, 892};
        for (int i = 0; i < ids.length; i++)
        {
            loot.recordKill("Iron dragon");
            loot.recordLoot(SourceKind.NPC_KILL, 1591, "Iron dragon", i + 1, 301, 12850, "Iron dragons", false,
                java.util.Collections.singletonList(LootItem.builder().itemId(ids[i]).quantity(1).gePriceEach(1000).build()));
        }

        LootTabView v = new LootTabView(loot, mdb, null, m -> {});
        v.setSize(net.runelite.client.ui.PluginPanel.PANEL_WIDTH, 2000);
        v.refresh();
        forceLayout(v);

        int limit = net.runelite.client.ui.PluginPanel.PANEL_WIDTH + 4;
        boolean sawGrid = false;
        for (Component c : deepChildren(v))
        {
            if (!(c instanceof Container))
            {
                continue;
            }
            java.awt.LayoutManager lm = ((Container) c).getLayout();
            if (lm instanceof java.awt.GridLayout && ((Container) c).getComponentCount() >= 10)
            {
                sawGrid = true;
                Assert.assertTrue("item grid pref width " + c.getPreferredSize().width + " > panel " + limit,
                    c.getPreferredSize().width <= limit);
                Assert.assertTrue("item grid wrapped to >1 row",
                    ((java.awt.GridLayout) lm).getRows() >= 2);
            }
        }
        Assert.assertTrue("found the item grid", sawGrid);
    }

    @Test
    public void testSourceWithNoObtainedLootIsHiddenFromMainList()
    {
        MonsterDatabase mdb = new MonsterDatabase(new Gson());
        mdb.load();

        LootTrackerManager loot = new LootTrackerManager(new Gson(), null,
            new java.io.File(System.getProperty("java.io.tmpdir"), "wmd-loot-dry-" + System.nanoTime()));
        loot.setActiveCharacter(7L, "Dry");
        for (int i = 0; i < 4000; i++)
        {
            loot.recordKill("Gargoyle"); // KC but never a tracked drop
        }

        LootTabView v = new LootTabView(loot, mdb, null, m -> {});
        v.refresh();

        // Stock-Loot-Tracker behaviour: a source you've had no drop from isn't listed at all,
        // and nothing "still dry" / greyed leaks onto the main list.
        String text = allText(v).toLowerCase();
        Assert.assertFalse(text.contains("gargoyle"));
        Assert.assertFalse(text.contains("still dry"));
        java.util.List<JLabel> labels = new ArrayList<>();
        collect(v, labels);
        Assert.assertEquals("no greyed 'missing' icons on the main list",
            0, labels.stream().filter(l -> !l.isEnabled()).count());
    }

    @Test
    public void testSourceDetailListsEveryPotentialDrop() throws Exception
    {
        MonsterDatabase mdb = new MonsterDatabase(new Gson());
        mdb.load();

        LootTrackerManager loot = new LootTrackerManager(new Gson(), null,
            new java.io.File(System.getProperty("java.io.tmpdir"), "wmd-loot-detail-" + System.nanoTime()));
        loot.setActiveCharacter(9L, "Detail");
        for (int i = 0; i < 50; i++)
        {
            loot.recordKill("Gargoyle");
        }
        loot.recordLoot(SourceKind.NPC_KILL, 412, "Gargoyle", 25, 301, 12850, "Gargoyles", false,
            Arrays.asList(LootItem.builder().itemId(4153).quantity(1).gePriceEach(28_000).build())); // granite maul

        LootTabView v = new LootTabView(loot, mdb, null, m -> {});
        v.refresh();

        // Click the source name to open the detail view.
        JLabel nameLbl = null;
        for (Component c : deepChildren(v))
        {
            if (c instanceof JLabel && "Gargoyle".equals(((JLabel) c).getText())
                && ((JLabel) c).getToolTipText() != null
                && ((JLabel) c).getToolTipText().toLowerCase().contains("drop"))
            {
                nameLbl = (JLabel) c;
            }
        }
        Assert.assertNotNull("source name is a clickable link", nameLbl);
        for (java.awt.event.MouseListener ml : nameLbl.getMouseListeners())
        {
            ml.mouseClicked(new MouseEvent(nameLbl, MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, 1, 1, 1, false));
        }

        String detail = allText(v).toLowerCase();
        // Fraction-first readout: your observed rate as "1/N", the wiki rate labelled below it.
        Assert.assertTrue("detail shows your rate as a 1/N fraction", detail.contains("1/50"));
        Assert.assertTrue("detail labels your rate", detail.contains("your rate"));
        Assert.assertTrue("detail labels the wiki rate", detail.contains("wiki"));
        Assert.assertFalse("no wordy '1 every N kills' phrasing", detail.contains("1 every"));
        Assert.assertTrue("detail shows a not-obtained drop", detail.contains("none in 50 kills"));

        boolean hasBack = deepChildren(v).stream()
            .anyMatch(c -> c instanceof javax.swing.JButton && "< Back".equals(((javax.swing.JButton) c).getText()));
        Assert.assertTrue("detail keeps a back button", hasBack);
    }

    private static java.util.List<Component> deepChildren(Component c)
    {
        java.util.List<Component> out = new ArrayList<>();
        if (c instanceof Container)
        {
            for (Component ch : ((Container) c).getComponents())
            {
                out.add(ch);
                out.addAll(deepChildren(ch));
            }
        }
        return out;
    }

    @Test
    public void testRendersRecordedLoot()
    {
        MonsterDatabase mdb = new MonsterDatabase(new Gson());
        mdb.load();

        LootTrackerManager loot = new LootTrackerManager(new Gson(), null,
            new java.io.File(System.getProperty("java.io.tmpdir"), "wmd-loot-test-" + System.nanoTime()));
        loot.setActiveCharacter(4242L, "Tester");
        loot.recordKill("Gargoyle");
        loot.recordKill("Gargoyle");
        loot.recordLoot(SourceKind.NPC_KILL, 412, "Gargoyle", 111, 301, 12850, "Gargoyles", false,
            Arrays.asList(LootItem.builder().itemId(4153).quantity(1).gePriceEach(28_000).build()));

        LootTabView v = new LootTabView(loot, mdb, null, m -> {});
        v.refresh();

        String text = allText(v);
        Assert.assertTrue("shows the source name", text.contains("Gargoyle"));
        Assert.assertTrue("shows kill count", text.contains("KC 2"));
        Assert.assertTrue("summary line present", text.toLowerCase().contains("total:"));
    }

    // LootTabView.DRY_RED / LUCKY_GREEN (private) - a guaranteed drop must never be painted either.
    private static final Color DRY_RED = new Color(239, 110, 110);
    private static final Color LUCKY_GREEN = new Color(120, 210, 140);

    /**
     * FIX 3 (T7c): a guaranteed (1/1) drop never produces a "dry" / "lucky" phrase or a red/green
     * colour in the Loot tab, even once the kill count has outrun the KC stamped on the last drop.
     */
    @Test
    public void testGuaranteedDropNeverPaintsDryOrLuckyInLootTab()
    {
        MonsterDatabase mdb = new MonsterDatabase(new Gson());
        mdb.load();

        String monName = "Nex";
        int guaranteedItemId = 532; // Big bones, a 1/1 Nex drop
        if (mdb.getMonsterByName(monName) == null)
        {
            outer:
            for (Monster m : mdb.getAllMonsters())
            {
                if (m.getDrops() == null)
                {
                    continue;
                }
                for (MonsterDrop d : m.getDrops())
                {
                    if (d.getItemId() > 0 && d.getPerKillDenominator() == 1)
                    {
                        monName = m.getName();
                        guaranteedItemId = d.getItemId();
                        break outer;
                    }
                }
            }
        }
        Assert.assertNotNull("need a monster with a guaranteed drop", mdb.getMonsterByName(monName));

        LootTrackerManager loot = new LootTrackerManager(new Gson(), null,
            new java.io.File(System.getProperty("java.io.tmpdir"), "wmd-loot-guar-" + System.nanoTime()));
        loot.setActiveCharacter(70L, "Guar");
        // One guaranteed drop at KC 1, then a long streak of dry kills so kc >> killCountAtLast.
        loot.recordKill(monName);
        loot.recordLoot(SourceKind.NPC_KILL, 11278, monName, 1001, 301, 12850, null, false,
            java.util.Collections.singletonList(LootItem.builder()
                .itemId(guaranteedItemId).quantity(1).name("Big bones").gePriceEach(300).build()));
        for (int i = 0; i < 60; i++)
        {
            loot.recordKill(monName);
        }

        LootTabView v = new LootTabView(loot, mdb, null, m -> {});
        v.refresh();

        // Main list: the only obtained item is the guaranteed drop, so nothing is dry-red /
        // lucky-green anywhere on the list.
        for (JLabel l : labelsOf(v))
        {
            Assert.assertFalse("main-list label '" + l.getText() + "' is dry-red",
                DRY_RED.equals(l.getForeground()));
            Assert.assertFalse("main-list label '" + l.getText() + "' is lucky-green",
                LUCKY_GREEN.equals(l.getForeground()));
        }

        // Source detail: scope to the guaranteed-drop row itself (other, non-guaranteed drops on
        // this table may legitimately read dry) and assert no dry/lucky verdict there.
        clickSourceName(v, monName);
        JLabel bones = null;
        for (JLabel l : labelsOf(v))
        {
            if (l.getText() != null && l.getText().startsWith("Big bones"))
            {
                bones = l;
            }
        }
        Assert.assertNotNull("guaranteed-drop row is rendered in the detail view", bones);
        Container row = bones.getParent().getParent(); // name label -> text panel -> row panel
        for (JLabel l : labelsOf(row))
        {
            Assert.assertFalse("detail-row label '" + l.getText() + "' is dry-red",
                DRY_RED.equals(l.getForeground()));
            Assert.assertFalse("detail-row label '" + l.getText() + "' is lucky-green",
                LUCKY_GREEN.equals(l.getForeground()));
            String t = l.getText() == null ? "" : l.getText().toLowerCase();
            Assert.assertFalse("guaranteed row reads dry: " + t, t.contains(" dry"));
            Assert.assertFalse("guaranteed row reads unluckier: " + t, t.contains("unluckier"));
            Assert.assertFalse("guaranteed row reads 'top %': " + t, t.startsWith("top "));
        }
    }

    /**
     * FIX 5 (T3 loot slice): the source-detail GP/hr uses the SAME &gt;60s / not-an-idle-period
     * guard as the source list, so a source killed across weeks shows no "/hr" in either view, and
     * a source killed in one sitting shows it in both - with matching "/hr" wording.
     */
    @Test
    public void testGpPerHourGuardMatchesBetweenListAndDetailViews()
    {
        MonsterDatabase mdb = new MonsterDatabase(new Gson());
        mdb.load();

        LootTrackerManager loot = new LootTrackerManager(new Gson(), null,
            new java.io.File(System.getProperty("java.io.tmpdir"), "wmd-loot-gphr-" + System.nanoTime()));
        loot.setActiveCharacter(50L, "Hr");
        loot.recordKill("Cow");
        loot.recordLoot(SourceKind.NPC_KILL, 2, "Cow", 2, 301, 1, null, false,
            java.util.Collections.singletonList(LootItem.builder()
                .itemId(1739).quantity(1).name("Cowhide").gePriceEach(500).build()));

        MonsterLootSummary s = loot.getSummary("NPC_KILL:cow");
        long now = System.currentTimeMillis();

        // Weeks-long span -> the lifetime average is meaningless -> hidden in BOTH views.
        s.setFirstSeenMs(now - 21L * 24 * 60 * 60 * 1000);
        s.setLastSeenMs(now);
        LootTabView idle = new LootTabView(loot, mdb, null, m -> {});
        idle.refresh();
        Assert.assertFalse("list hides an idle-span /hr", allText(idle).contains("/hr"));
        clickSourceName(idle, "Cow");
        String idleDetail = allText(idle);
        Assert.assertFalse("detail hides an idle-span /hr (same guard as the list)", idleDetail.contains("/hr"));
        Assert.assertFalse("no legacy bare '/h' wording in the detail view",
            idleDetail.replace("/hr", "").contains("/h"));

        // ~40 min span -> a real sitting -> shown in BOTH views.
        s.setFirstSeenMs(now - 40L * 60 * 1000);
        s.setLastSeenMs(now);
        LootTabView live = new LootTabView(loot, mdb, null, m -> {});
        live.refresh();
        Assert.assertTrue("list shows a session-length /hr", allText(live).contains("/hr"));
        clickSourceName(live, "Cow");
        String liveDetail = allText(live);
        Assert.assertTrue("detail shows the same session-length /hr", liveDetail.contains("/hr"));
        Assert.assertFalse("detail uses '/hr', not the old '/h'",
            liveDetail.replace("/hr", "").contains("/h"));
    }

    /** Audit one-liner: shortQty's middle band must not print 250,000 as "0M". */
    @Test
    public void testShortQtyMiddleBand() throws Exception
    {
        Method m = LootTabView.class.getDeclaredMethod("shortQty", long.class);
        m.setAccessible(true);
        Assert.assertEquals("999", m.invoke(null, 999L));
        Assert.assertEquals("1k", m.invoke(null, 1_000L));
        Assert.assertEquals("99k", m.invoke(null, 99_999L));
        Assert.assertEquals("250k", m.invoke(null, 250_000L));   // was "0M"
        Assert.assertEquals("999k", m.invoke(null, 999_999L));   // was "0M"
        Assert.assertEquals("1M", m.invoke(null, 1_000_000L));
        Assert.assertEquals("12M", m.invoke(null, 12_500_000L));
    }

    private static List<JLabel> labelsOf(Component root)
    {
        List<JLabel> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void clickSourceName(Component v, String name)
    {
        for (Component c : deepChildren(v))
        {
            if (c instanceof JLabel && name.equals(((JLabel) c).getText())
                && ((JLabel) c).getToolTipText() != null
                && ((JLabel) c).getToolTipText().toLowerCase().contains("drop"))
            {
                for (MouseListener ml : c.getMouseListeners())
                {
                    ml.mouseClicked(new MouseEvent(c, MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(), 0, 1, 1, 1, false));
                }
                return;
            }
        }
        throw new AssertionError("source-name link not found: " + name);
    }
}
