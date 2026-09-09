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
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import net.runelite.api.coords.WorldPoint;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SpreadsheetDialogTest
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

        dialog = new ShopDirectorySpreadsheetDialog(
            shopDatabase,
            monsterDatabase,
            slayerTaskManager,
            null,
            null,
            cartManager
        );
        // Tests run on a non-headless JVM; keep the dialog from flashing a real
        // window when open*Tab()/docking are exercised.
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
            dialog.cancelPendingDebounce();
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            dialog.setVisible(false);
            dialog.dispose();
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            dialog = null;
        }
    }

    @Test
    public void testDialogInitializesWithAll4Tabs()
    {
        Assert.assertNotNull(dialog);
        Assert.assertNotNull(dialog.getShopTable());
        Assert.assertNotNull(dialog.getMonsterTable());
        Assert.assertNotNull(dialog.getSlayerTable());
        Assert.assertNotNull(dialog.getSearchTable());

        Assert.assertNotNull(dialog.getShopTableModel());
        Assert.assertNotNull(dialog.getMonsterTableModel());
        Assert.assertNotNull(dialog.getSlayerTableModel());
        Assert.assertNotNull(dialog.getSearchTableModel());

        Assert.assertEquals(10, ShopDirectorySpreadsheetDialog.SHOP_COLUMN_NAMES.length);
        Assert.assertEquals(11, ShopDirectorySpreadsheetDialog.MONSTER_COLUMN_NAMES.length);
        Assert.assertEquals(8, ShopDirectorySpreadsheetDialog.SLAYER_COLUMN_NAMES.length);
        Assert.assertEquals(7, ShopDirectorySpreadsheetDialog.SEARCH_COLUMN_NAMES.length);
    }

    @Test
    public void testTabSwitchingLoadsData()
    {
        // 1. Tab Shops
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_SHOPS);
        Assert.assertEquals(ShopDirectorySpreadsheetDialog.TAB_SHOPS, dialog.getActiveTab());
        DefaultTableModel shopModel = dialog.getShopTableModel();
        Assert.assertTrue("Shop table should have rows loaded", shopModel.getRowCount() >= 1000);
        Assert.assertEquals(10, shopModel.getColumnCount());

        // 2. Tab Bestiary
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_MONSTERS);
        Assert.assertEquals(ShopDirectorySpreadsheetDialog.TAB_MONSTERS, dialog.getActiveTab());
        DefaultTableModel monsterModel = dialog.getMonsterTableModel();
        Assert.assertTrue("Monster table should have rows loaded", monsterModel.getRowCount() >= 500);
        Assert.assertEquals(11, monsterModel.getColumnCount());

        // 3. Tab Slayer Masters & Tasks
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_SLAYER);
        Assert.assertEquals(ShopDirectorySpreadsheetDialog.TAB_SLAYER, dialog.getActiveTab());
        DefaultTableModel slayerModel = dialog.getSlayerTableModel();
        Assert.assertTrue("Slayer table should have rows loaded", slayerModel.getRowCount() >= 100);
        Assert.assertEquals(8, slayerModel.getColumnCount());

        // 4. Tab Global Search
        dialog.selectTab(ShopDirectorySpreadsheetDialog.TAB_SEARCH);
        Assert.assertEquals(ShopDirectorySpreadsheetDialog.TAB_SEARCH, dialog.getActiveTab());
        DefaultTableModel searchModel = dialog.getSearchTableModel();
        Assert.assertTrue("Search table should have rows loaded", searchModel.getRowCount() >= 1000);
        Assert.assertEquals(7, searchModel.getColumnCount());
    }

    @Test
    public void testFilterSearchesReturnMatchingItems()
    {
        // 1. Shop tab search
        dialog.openShopTab("potion");
        DefaultTableModel shopModel = dialog.getShopTableModel();
        Assert.assertTrue("Search for 'potion' should return shop items", shopModel.getRowCount() > 0);
        for (int r = 0; r < Math.min(shopModel.getRowCount(), 20); r++)
        {
            String itemName = (String) shopModel.getValueAt(r, 3);
            String shopName = (String) shopModel.getValueAt(r, 1);
            boolean matched = itemName.toLowerCase().contains("potion") || shopName.toLowerCase().contains("potion");
            Assert.assertTrue("Item or shop should contain query", matched);
        }

        // 2. Bestiary tab search
        dialog.openMonsterTab("dragon");
        DefaultTableModel monsterModel = dialog.getMonsterTableModel();
        Assert.assertTrue("Search for 'dragon' should return dragon monsters", monsterModel.getRowCount() > 0);

        // 3. Slayer tab search
        dialog.openSlayerTab("Gargoyles");
        DefaultTableModel slayerModel = dialog.getSlayerTableModel();
        Assert.assertTrue("Search for 'Gargoyles' should return assignments", slayerModel.getRowCount() > 0);
        boolean foundGargoyles = false;
        for (int r = 0; r < slayerModel.getRowCount(); r++)
        {
            String task = (String) slayerModel.getValueAt(r, 4);
            if (task.toLowerCase().contains("gargoyle"))
            {
                foundGargoyles = true;
                break;
            }
        }
        Assert.assertTrue("Should find Gargoyles task", foundGargoyles);

        // 4. Global search
        dialog.openSearchTab("whip");
        DefaultTableModel searchModel = dialog.getSearchTableModel();
        Assert.assertTrue("Global search for 'whip' should return results", searchModel.getRowCount() > 0);
    }

    @Test
    public void testDoubleClickAndActionButtonsInvokeCallbacks()
    {
        AtomicReference<Shop> focusedShop = new AtomicReference<>();
        AtomicReference<Shop> inspectedShop = new AtomicReference<>();
        AtomicReference<Monster> focusedMonster = new AtomicReference<>();
        AtomicReference<Monster> inspectedMonster = new AtomicReference<>();
        AtomicReference<MonsterSpawnZone> focusedZone = new AtomicReference<>();
        AtomicReference<WorldPoint> focusedPoint = new AtomicReference<>();
        AtomicReference<String> openedSlayerTab = new AtomicReference<>();

        dialog.setCallbacks(
            focusedShop::set,
            inspectedShop::set,
            focusedMonster::set,
            inspectedMonster::set,
            (m, z) -> {
                focusedMonster.set(m);
                focusedZone.set(z);
            },
            (pt, label) -> focusedPoint.set(pt),
            openedSlayerTab::set
        );

        // Double-click Shop table row
        dialog.openShopTab(null);
        JTable shopTable = dialog.getShopTable();
        Assert.assertTrue(shopTable.getRowCount() > 0);
        shopTable.setRowSelectionInterval(0, 0);

        MouseEvent doubleClickShop = new MouseEvent(
            shopTable, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 5, 5, 2, false, MouseEvent.BUTTON1
        );
        shopTable.dispatchEvent(doubleClickShop);

        Assert.assertNotNull("Focused shop should not be null", focusedShop.get());
        Assert.assertNotNull("Inspected shop should not be null", inspectedShop.get());

        // Double-click Monster table row
        dialog.openMonsterTab("Vorkath");
        JTable monsterTable = dialog.getMonsterTable();
        Assert.assertTrue(monsterTable.getRowCount() > 0);
        monsterTable.setRowSelectionInterval(0, 0);

        MouseEvent doubleClickMonster = new MouseEvent(
            monsterTable, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 5, 5, 2, false, MouseEvent.BUTTON1
        );
        monsterTable.dispatchEvent(doubleClickMonster);

        Assert.assertNotNull("Focused monster should not be null", focusedMonster.get());
        Assert.assertEquals("Vorkath", focusedMonster.get().getName());

        // Add to cart test
        dialog.openShopTab(null);
        shopTable.setRowSelectionInterval(0, 0);
        int initialCartCount = cartManager.getItemCount();
        cartManager.addItem(shopDatabase.getAllShops().get(0).getItems().get(0), shopDatabase.getAllShops().get(0), 1);
        Assert.assertEquals(initialCartCount + 1, cartManager.getItemCount());
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
    public void testDualModeDockingAndUndocking()
    {
        Assert.assertFalse("Should start in docked in-game mode", dialog.isUndocked());

        dialog.undockToWindow();
        Assert.assertTrue("Should now be in undocked window mode", dialog.isUndocked());

        dialog.dockInGame();
        Assert.assertFalse("Should be back in docked in-game mode", dialog.isUndocked());

        dialog.toggleDockMode();
        Assert.assertTrue("Should toggle to undocked", dialog.isUndocked());

        dialog.toggleDockMode();
        Assert.assertFalse("Should toggle back to docked", dialog.isUndocked());
    }

    @Test
    public void testGuideViewModeToggle()
    {
        Assert.assertTrue("Should default to guide cards view mode", dialog.isGuideViewMode());

        dialog.toggleViewMode();
        Assert.assertFalse("Should toggle to table grid mode", dialog.isGuideViewMode());

        dialog.setGuideViewMode(true);
        Assert.assertTrue("Should set back to guide cards mode", dialog.isGuideViewMode());
    }
}
