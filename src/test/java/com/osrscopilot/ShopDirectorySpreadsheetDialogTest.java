package com.osrscopilot;

import com.google.gson.Gson;
import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.ShoppingCartManager;
import com.osrscopilot.data.SlayerTaskManager;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterSpawnZone;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.ui.ShopDirectorySpreadsheetDialog;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class ShopDirectorySpreadsheetDialogTest
{
    private ShopDatabase shopDatabase;
    private MonsterDatabase monsterDatabase;
    private SlayerTaskManager slayerTaskManager;
    private ShoppingCartManager cartManager;
    private ShopDirectorySpreadsheetDialog dialog;

    @Before
    public void setUp()
    {
        shopDatabase = new ShopDatabase(new Gson());
        shopDatabase.load();

        monsterDatabase = new MonsterDatabase(new Gson());
        monsterDatabase.load();

        slayerTaskManager = new SlayerTaskManager(null, monsterDatabase);
        slayerTaskManager.loadAssignments();

        cartManager = ShoppingCartManager.getInstance();
        cartManager.clear();

        dialog = new ShopDirectorySpreadsheetDialog(shopDatabase, monsterDatabase, slayerTaskManager, null, cartManager);
        // Tests run on a non-headless JVM (Swing layout assertions need a real toolkit);
        // keep the dialog from flashing a real window when open*Tab()/docking are exercised.
        dialog.setWindowRealizationSuppressed(true);
        // Tests drive applyXFilters() explicitly and read the models off the EDT; stop the ~150ms
        // search debounce from firing a re-filter mid-read (the historical cross-test flake).
        dialog.setDebounceSuppressed(true);
    }

    @After
    public void tearDown() throws Exception
    {
        if (dialog != null)
        {
            // Flush any queued EDT work (guide-card rebuilds, openPanel calls) so it can't run
            // against a disposed dialog and corrupt the next test in this JVM.
            dialog.cancelPendingDebounce();
            SwingUtilities.invokeAndWait(() -> { });
            dialog.setVisible(false);
            dialog.dispose();
            SwingUtilities.invokeAndWait(() -> { });
            dialog = null;
        }
    }

    @Test
    public void testColumnDefinitions()
    {
        Assert.assertEquals(10, ShopDirectorySpreadsheetDialog.SHOP_COLUMN_NAMES.length);
        Assert.assertEquals(11, ShopDirectorySpreadsheetDialog.MONSTER_COLUMN_NAMES.length);
        Assert.assertEquals(8, ShopDirectorySpreadsheetDialog.SLAYER_COLUMN_NAMES.length);
        Assert.assertEquals(7, ShopDirectorySpreadsheetDialog.SEARCH_COLUMN_NAMES.length);

        String[] expectedMonsterCols = {
            "Monster", "Category", "Lvl", "HP", "Max Hit", "Style", "Weakness", "Slayer Req", "Quest Req", "Primary Spawn", "Top Drops"
        };

        for (int i = 0; i < expectedMonsterCols.length; i++)
        {
            Assert.assertEquals(expectedMonsterCols[i], ShopDirectorySpreadsheetDialog.MONSTER_COLUMN_NAMES[i]);
        }
    }

    @Test
    public void testShopTabPopulationAndFiltering()
    {
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_SHOPS);
        dialog.applyShopFilters();

        DefaultTableModel model = dialog.getShopTableModel();
        Assert.assertTrue("Shop table should have rows loaded", model.getRowCount() > 0);
        Assert.assertTrue("Shop table should have over 1000 items", model.getRowCount() >= 1000);

        // Verify column count in model
        Assert.assertEquals(10, model.getColumnCount());
    }

    @Test
    public void testMonsterTabPopulationAndFiltering()
    {
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_MONSTERS);
        dialog.applyMonsterFilters();

        DefaultTableModel model = dialog.getMonsterTableModel();
        Assert.assertTrue("Monster table should have rows loaded", model.getRowCount() > 0);
        Assert.assertTrue("Monster table should have over 500 monster spawn entries", model.getRowCount() >= 500);

        // Verify column count in model
        Assert.assertEquals(11, model.getColumnCount());

        // Verify row contents
        boolean foundAbyssalDemon = false;
        boolean foundVorkath = false;
        boolean foundKbd = false;

        for (int r = 0; r < model.getRowCount(); r++)
        {
            String name = (String) model.getValueAt(r, 0);
            if ("Abyssal demon".equalsIgnoreCase(name))
            {
                foundAbyssalDemon = true;
                int slayerReq = (int) model.getValueAt(r, 7);
                Assert.assertEquals(85, slayerReq);
                String topDrops = (String) model.getValueAt(r, 10);
                Assert.assertNotNull("Abyssal demon top drops should not be null", topDrops);
                Assert.assertFalse("Abyssal demon top drops should not be empty", topDrops.isEmpty());
            }
            else if ("Vorkath".equalsIgnoreCase(name))
            {
                foundVorkath = true;
                int combatLvl = (int) model.getValueAt(r, 2);
                Assert.assertTrue("Vorkath combat level should be 732 or 392", combatLvl == 732 || combatLvl == 392);
            }
            else if ("King Black Dragon".equalsIgnoreCase(name))
            {
                foundKbd = true;
                int combatLvl = (int) model.getValueAt(r, 2);
                Assert.assertEquals(276, combatLvl);
            }
        }

        Assert.assertTrue("Should have found Abyssal demon in monster table", foundAbyssalDemon);
        Assert.assertTrue("Should have found Vorkath in monster table", foundVorkath);
        Assert.assertTrue("Should have found King Black Dragon in monster table", foundKbd);
    }

    @Test
    public void testMonsterTabSearchFiltering()
    {
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_MONSTERS);

        // Search by Drop Name
        dialog.openMonsterTab("visage");
        DefaultTableModel model = dialog.getMonsterTableModel();
        Assert.assertTrue("Search for 'visage' should return drop results", model.getRowCount() > 0);

        boolean hasDragonOrWyvern = false;
        for (int r = 0; r < model.getRowCount(); r++)
        {
            String name = (String) model.getValueAt(r, 0);
            String drops = (String) model.getValueAt(r, 10);
            if (drops.toLowerCase().contains("visage") || name.toLowerCase().contains("dragon") || name.toLowerCase().contains("wyvern") || name.toLowerCase().contains("vorkath"))
            {
                hasDragonOrWyvern = true;
                break;
            }
        }
        Assert.assertTrue("Search for 'visage' should match monsters dropping it", hasDragonOrWyvern);

        // Search by wiki attribute (weakness is now the real elemental value, "dragon" is an attribute)
        dialog.openMonsterTab("dragon");
        Assert.assertTrue("Search for 'dragon' should return dragon monsters", dialog.getMonsterTableModel().getRowCount() > 0);

        // Search by Location
        dialog.openMonsterTab("Catacombs");
        Assert.assertTrue("Search for 'Catacombs' should return spawn locations in Catacombs", dialog.getMonsterTableModel().getRowCount() > 0);
    }

    @Test
    public void testColumnVisibilityToggle()
    {
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_SHOPS);
        int initialShopCols = dialog.getShopTable().getColumnModel().getColumnCount();
        Assert.assertEquals(10, initialShopCols);

        dialog.setShopColumnVisible(9, false); // Hide Memb column
        Assert.assertEquals(9, dialog.getShopTable().getColumnModel().getColumnCount());

        dialog.setShopColumnVisible(9, true); // Restore Memb column
        Assert.assertEquals(10, dialog.getShopTable().getColumnModel().getColumnCount());

        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_MONSTERS);
        int initialMonsterCols = dialog.getMonsterTable().getColumnModel().getColumnCount();
        Assert.assertEquals(11, initialMonsterCols);

        dialog.setMonsterColumnVisible(10, false); // Hide Top Drops
        Assert.assertEquals(10, dialog.getMonsterTable().getColumnModel().getColumnCount());

        dialog.setMonsterColumnVisible(10, true); // Restore Top Drops
        Assert.assertEquals(11, dialog.getMonsterTable().getColumnModel().getColumnCount());
    }

    @Test
    public void testCallbacksIntegration()
    {
        AtomicReference<Shop> focusedShop = new AtomicReference<>();
        AtomicReference<Shop> inspectedShop = new AtomicReference<>();
        AtomicReference<Monster> focusedMonster = new AtomicReference<>();
        AtomicReference<Monster> inspectedMonster = new AtomicReference<>();
        AtomicReference<MonsterSpawnZone> focusedZone = new AtomicReference<>();

        dialog.setCallbacks(
            focusedShop::set,
            inspectedShop::set,
            focusedMonster::set,
            inspectedMonster::set,
            (m, z) -> {
                focusedMonster.set(m);
                focusedZone.set(z);
            }
        );

        Shop sampleShop = shopDatabase.getAllShops().get(0);
        Assert.assertNotNull(sampleShop);
    }

    @Test
    public void testClickingMonsterNameColumnPansMapToRow()
    {
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_MONSTERS);
        dialog.openMonsterTab("Vorkath");

        AtomicReference<Monster> focusedMonster = new AtomicReference<>();
        dialog.setMonsterCallbacks(focusedMonster::set, m -> {}, (m, z) -> focusedMonster.set(m));

        JTable table = dialog.getMonsterTable();
        Assert.assertTrue("Search for 'Vorkath' should return at least one row", table.getRowCount() > 0);

        table.getSelectionModel().setSelectionInterval(0, 0);
        table.getColumnModel().getSelectionModel().setSelectionInterval(0, 0);

        MouseEvent click = new MouseEvent(
            table, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 5, 5, 1, false, MouseEvent.BUTTON1);
        table.dispatchEvent(click);

        Assert.assertNotNull("Clicking the Monster Name cell should trigger the pan-map callback", focusedMonster.get());
        Assert.assertEquals("Vorkath", focusedMonster.get().getName());
    }

    @Test
    public void testAddToCartButtonVisibilityAcrossTabs()
    {
        // 1. Shops Tab: Add to Cart MUST be visible
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_SHOPS);
        Assert.assertTrue("Add to Cart should be visible on Shops tab", dialog.getAddCartButton().isVisible());

        // 2. Monsters Tab: Add to Cart MUST be hidden
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_MONSTERS);
        Assert.assertFalse("Add to Cart should be hidden on Bestiary tab", dialog.getAddCartButton().isVisible());

        // 3. Slayer Tab: Add to Cart MUST be hidden
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_SLAYER);
        Assert.assertFalse("Add to Cart should be hidden on Slayer tab", dialog.getAddCartButton().isVisible());

        // 4. Global Search Tab: Add to Cart MUST be visible
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_SEARCH);
        Assert.assertTrue("Add to Cart should be visible on Global Search tab", dialog.getAddCartButton().isVisible());
    }

    @Test
    public void testMonsterCardSingleClickAndDoubleClick() throws Exception
    {
        AtomicReference<Monster> inspectedMonster = new AtomicReference<>();
        ClientToolbar mockToolbar = Mockito.mock(ClientToolbar.class);
        NavigationButton mockNavButton = NavigationButton.builder()
            .icon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
            .tooltip("Test")
            .build();

        dialog.setClientToolbar(mockToolbar, mockNavButton);
        dialog.setCallbacks(s -> {}, s -> {}, m -> {}, inspectedMonster::set, (m, z) -> {});

        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_MONSTERS);
        dialog.setGuideViewMode(true);
        dialog.applyMonsterFilters();

        JPanel cardsPanel = dialog.getMonsterCardsListPanel();
        Assert.assertTrue("Should have monster cards", cardsPanel.getComponentCount() > 0);

        Component firstCardComp = cardsPanel.getComponent(0);
        Assert.assertTrue("First component should be a JPanel card", firstCardComp instanceof JPanel);
        JPanel card = (JPanel) firstCardComp;

        // Test Single-Click: sets selection feedback
        MouseEvent singleClick = new MouseEvent(
            card, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 10, 10, 1, false, MouseEvent.BUTTON1);
        for (java.awt.event.MouseListener ml : card.getMouseListeners())
        {
            ml.mouseClicked(singleClick);
        }
        Assert.assertTrue("Feedback text should mention Selected", dialog.getFooterStatusText().contains("Selected"));
        Assert.assertTrue("Feedback text should mention Click Map to locate", dialog.getFooterStatusText().contains("Click Map to locate"));

        // Test Double-Click: sets feedback, inspects monster, and opens sidebar on EDT
        MouseEvent doubleClick = new MouseEvent(
            card, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 10, 10, 2, false, MouseEvent.BUTTON1);
        for (java.awt.event.MouseListener ml : card.getMouseListeners())
        {
            ml.mouseClicked(doubleClick);
        }

        Assert.assertNotNull("Inspected monster should be set on double click", inspectedMonster.get());
        Assert.assertTrue("Feedback text should confirm opened in sidebar", dialog.getFooterStatusText().contains("sidebar bestiary view"));

        // Process EDT events
        SwingUtilities.invokeAndWait(() -> {});
        Mockito.verify(mockToolbar, Mockito.atLeastOnce()).openPanel(mockNavButton);
    }

    @Test
    public void testSidePanelButtonsOnCardsAndFooterAutoOpenToolbar() throws Exception
    {
        ClientToolbar mockToolbar = Mockito.mock(ClientToolbar.class);
        NavigationButton mockNavButton = NavigationButton.builder()
            .icon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
            .tooltip("Test")
            .build();
        dialog.setClientToolbar(mockToolbar, mockNavButton);

        AtomicReference<Shop> inspectedShop = new AtomicReference<>();
        AtomicReference<Monster> inspectedMonster = new AtomicReference<>();
        AtomicReference<String> openedSlayerTab = new AtomicReference<>();

        dialog.setCallbacks(
            s -> {},
            inspectedShop::set,
            m -> {},
            inspectedMonster::set,
            (m, z) -> {},
            (pt, label) -> {},
            openedSlayerTab::set
        );

        dialog.setGuideViewMode(true);

        // 1. Shop Card Side Panel Button
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_SHOPS);
        dialog.applyShopFilters();
        JPanel shopCard = (JPanel) dialog.getShopCardsListPanel().getComponent(0);
        JButton shopSidePanelBtn = findButtonByText(shopCard, "Side Panel");
        Assert.assertNotNull("Shop card should contain a 'Side Panel' button", shopSidePanelBtn);
        shopSidePanelBtn.doClick();
        Assert.assertNotNull("Should have inspected shop", inspectedShop.get());

        // 2. Monster Card Side Panel Button
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_MONSTERS);
        dialog.applyMonsterFilters();
        JPanel monsterCard = (JPanel) dialog.getMonsterCardsListPanel().getComponent(0);
        JButton monsterSidePanelBtn = findButtonByText(monsterCard, "Side Panel");
        Assert.assertNotNull("Monster card should contain a 'Side Panel' button", monsterSidePanelBtn);
        monsterSidePanelBtn.doClick();
        Assert.assertNotNull("Should have inspected monster", inspectedMonster.get());

        // 3. Slayer Card Side Panel Button
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_SLAYER);
        dialog.applySlayerFilters();
        JPanel slayerCard = (JPanel) dialog.getSlayerCardsListPanel().getComponent(0);
        JButton slayerSidePanelBtn = findButtonByText(slayerCard, "Side Panel");
        Assert.assertNotNull("Slayer card should contain a 'Side Panel' button", slayerSidePanelBtn);
        slayerSidePanelBtn.doClick();

        // 4. Search Card Side Panel Button
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_SEARCH);
        dialog.openSearchTab("rune");
        JPanel searchCard = (JPanel) dialog.getSearchCardsListPanel().getComponent(0);
        JButton searchSidePanelBtn = findButtonByText(searchCard, "Side Panel");
        Assert.assertNotNull("Search card should contain a 'Side Panel' button", searchSidePanelBtn);
        searchSidePanelBtn.doClick();

        // 5. Footer Bar Side Panel Button
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_SHOPS);
        dialog.applyShopFilters();
        Assert.assertTrue("Shop table should have rows", dialog.getShopTable().getRowCount() > 0);
        dialog.getShopTable().setRowSelectionInterval(0, 0);
        dialog.executeViewInSidePanel();

        SwingUtilities.invokeAndWait(() -> {});
        Mockito.verify(mockToolbar, Mockito.atLeast(5)).openPanel(mockNavButton);
    }

    @Test
    public void testDebouncedSearchFieldsOnAllTabs()
    {
        Assert.assertNotNull(dialog.getShopSearchField());
        Assert.assertNotNull(dialog.getMonsterSearchField());
        Assert.assertNotNull(dialog.getSlayerSearchField());
        Assert.assertNotNull(dialog.getSearchUniversalField());

        // Typing into each search field triggers debounce document listeners without UI freezing
        dialog.getShopSearchField().setText("sword");
        dialog.getMonsterSearchField().setText("dragon");
        dialog.getSlayerSearchField().setText("demon");
        dialog.getSearchUniversalField().setText("plate");

        Assert.assertEquals("sword", dialog.getShopSearchField().getText());
        Assert.assertEquals("dragon", dialog.getMonsterSearchField().getText());
        Assert.assertEquals("demon", dialog.getSlayerSearchField().getText());
        Assert.assertEquals("plate", dialog.getSearchUniversalField().getText());
    }

    @Test
    public void testConcurrentFilteringAndCardRebuildingSafety() throws Exception
    {
        dialog.setGuideViewMode(true);
        final int THREADS = 4;
        // applyXFilters() now marshals its whole body (table-model mutation + a 150-card guide
        // rebuild) onto the EDT, so N worker threads calling it are serialised there. A few
        // iterations each is enough to prove no ConcurrentModificationException / torn state; the
        // old count (10) x that serialised cost just made the run slow, not more thorough.
        final int ITERATIONS = 3;
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(THREADS);
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();

        for (int i = 0; i < THREADS; i++)
        {
            final int threadId = i;
            new Thread(() -> {
                try
                {
                    for (int iter = 0; iter < ITERATIONS; iter++)
                    {
                        if (threadId % 4 == 0)
                        {
                            dialog.applyShopFilters();
                        }
                        else if (threadId % 4 == 1)
                        {
                            dialog.applyMonsterFilters();
                        }
                        else if (threadId % 4 == 2)
                        {
                            dialog.applySlayerFilters();
                        }
                        else
                        {
                            dialog.applySearchFilters();
                        }
                    }
                }
                catch (Throwable t)
                {
                    errorRef.compareAndSet(null, t);
                }
                finally
                {
                    latch.countDown();
                }
            }).start();
        }

        boolean finished = latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        Assert.assertTrue("Concurrent filter operations timed out", finished);
        Assert.assertNull("Concurrent modification exception occurred: " + errorRef.get(), errorRef.get());

        // Drain the EDT and confirm the filters actually ran (models populated), not just "no throw".
        SwingUtilities.invokeAndWait(() -> { });
        Assert.assertTrue(dialog.getShopTableModel().getRowCount() > 0);
        Assert.assertTrue(dialog.getMonsterTableModel().getRowCount() > 0);
    }

    private static JButton findButtonByText(java.awt.Container container, String text)
    {
        for (Component c : container.getComponents())
        {
            if (c instanceof JButton && text.equals(((JButton) c).getText()))
            {
                return (JButton) c;
            }
            if (c instanceof java.awt.Container)
            {
                JButton found = findButtonByText((java.awt.Container) c, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
