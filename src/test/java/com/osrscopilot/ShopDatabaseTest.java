package com.osrscopilot;

import com.google.gson.Gson;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.TownNode;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ShopDatabaseTest
{
    private ShopDatabase database;

    @Before
    public void setUp()
    {
        database = new ShopDatabase(new Gson());
        database.load();
    }

    @Test
    public void testDatabaseLoad()
    {
        List<Shop> shops = database.getAllShops();
        List<TownNode> towns = database.getAllTowns();

        Assert.assertNotNull("Shops list should not be null", shops);
        Assert.assertFalse("Should have loaded shops from database", shops.isEmpty());
        Assert.assertTrue("Should have at least 100 shops", shops.size() >= 100);

        Assert.assertNotNull("Towns list should not be null", towns);
        Assert.assertFalse("Should have loaded town hubs", towns.isEmpty());
        Assert.assertTrue("Should have major towns", towns.size() >= 10);
    }

    @Test
    public void testItemSearch()
    {
        Set<Shop> runeShops = database.searchShopsByItemName("death rune");
        Assert.assertNotNull(runeShops);
        Assert.assertTrue("Should find shops selling death runes", runeShops.size() > 0);

        Set<Shop> axeShops = database.searchShopsByItemName("bronze axe");
        Assert.assertNotNull(axeShops);
        Assert.assertTrue("Should find shops selling bronze axes", axeShops.size() > 0);
    }

    @Test
    public void testTownLookup()
    {
        List<Shop> varrockShops = database.getShopsByTown("Varrock");
        Assert.assertNotNull(varrockShops);
        Assert.assertTrue("Varrock should have multiple shops", varrockShops.size() >= 5);
    }

    @Test
    public void testPrimaryTeleportData()
    {
        for (TownNode town : database.getAllTowns())
        {
            String primaryTp = com.osrscopilot.util.OsrsTeleportData.getPrimaryTeleport(town.getName());
            if (primaryTp != null)
            {
                Assert.assertTrue("Primary teleport for " + town.getName() + " ('" + primaryTp + "') should be under 24 chars", primaryTp.length() < 24);
                Assert.assertFalse("Primary teleport should not contain multi-slash", primaryTp.contains("/"));
            }
        }

        Assert.assertEquals("Lumbridge Teleport", com.osrscopilot.util.OsrsTeleportData.getPrimaryTeleport("Lumbridge"));
        Assert.assertEquals("Desert Amulet 4", com.osrscopilot.util.OsrsTeleportData.getPrimaryTeleport("Nardah"));
    }

    @Test
    public void testBuyPriceAndSellRates()
    {
        boolean foundItemWithBuyPrice = false;
        for (Shop shop : database.getAllShops())
        {
            if (shop.getItems() != null)
            {
                for (com.osrscopilot.data.model.ShopItem item : shop.getItems())
                {
                    Assert.assertTrue("Effective buy price should be positive", item.getEffectiveBuyPrice() > 0);
                    if (item.getBuyPrice() > 0)
                    {
                        foundItemWithBuyPrice = true;
                    }
                }
            }
        }
        Assert.assertTrue("Should have items with explicit buy prices in database", foundItemWithBuyPrice);
    }

    @Test
    public void testShopValiditySweep()
    {
        // 2026-08-31 sweep: shop count, no dead pages, item/npc id ranges, town coverage.
        List<Shop> shops = database.getAllShops();
        Assert.assertTrue("shop count should be ~470", shops.size() >= 460 && shops.size() <= 500);

        int noNpc = 0;
        int noLoc = 0;
        for (Shop s : shops)
        {
            Assert.assertNotNull("shop name", s.getName());
            Assert.assertFalse("no historical/deadman shop entries: " + s.getName(),
                s.getName().contains("(historical)") || s.getName().contains("(Deadman Mode)")
                    || s.getName().endsWith("/Entire stock"));
            Assert.assertNotNull("every shop has a town: " + s.getName(), s.getTown());
            if (s.getNpcId() <= 0)
            {
                noNpc++;
            }
            if (s.getWorldX() <= 0 || s.getWorldY() <= 0)
            {
                noLoc++;
            }
            if (s.getItems() != null)
            {
                for (com.osrscopilot.data.model.ShopItem it : s.getItems())
                {
                    Assert.assertTrue(s.getName() + " / " + it.getName() + " itemId " + it.getItemId(),
                        it.getItemId() > 0 && it.getItemId() <= 60_000);
                }
            }
        }
        // Bounded, not zero - object shops / bartenders / reward shops legitimately have no NPC id.
        Assert.assertTrue("unlinked shop count stays bounded, got " + noNpc, noNpc <= 25);
        Assert.assertTrue("coordless shop count stays tiny, got " + noLoc, noLoc <= 6);
    }

    @Test
    public void testIslandAndSubLocationShopsGroupUnderTheirHub()
    {
        // Regression: "Where Wyrmscraig's Wear Wares Were" (in Auchrie) used to be filed under a
        // standalone "Auchrie" town, so it never showed in the Wyrmscraig hub. Both Wyrmscraig
        // shops must now resolve to the same town.
        List<Shop> wyrmscraig = database.getShopsByTown("Wyrmscraig");
        Assert.assertEquals("Wyrmscraig hub should list both island shops", 2, wyrmscraig.size());
        Assert.assertTrue(wyrmscraig.stream().anyMatch(s -> s.getName().equals("Supplies's Supplies")));
        Assert.assertTrue(wyrmscraig.stream().anyMatch(s -> s.getName().equals("Where Wyrmscraig's Wear Wares Were")));
        Assert.assertTrue("standalone Auchrie town should be gone", database.getShopsByTown("Auchrie").isEmpty());

        // The two "Dwarven Mine(s)" spellings collapsed to one town; the underwater area folds into
        // Fossil Island.
        Assert.assertTrue("Dwarven Mines spelling merged away", database.getShopsByTown("Dwarven Mines").isEmpty());
        Assert.assertTrue("Dwarven Mine hub keeps its shops", database.getShopsByTown("Dwarven Mine").size() >= 3);
        Assert.assertTrue("Underwater sub-area folded into Fossil Island", database.getShopsByTown("Underwater").isEmpty());
    }

    @Test
    public void testUntradeableStockResolved()
    {
        // The {{StoreLine}} parser used to resolve item ids only via the tradeable GE mapping, so
        // shops that stock untradeables lost those rows (or vanished entirely). The
        // bucket('infobox_item') fallback fills them.
        Shop aubury = database.getShopByName("Aubury's Rune Shop");
        Assert.assertNotNull(aubury);
        Assert.assertTrue("Aubury should now list its untradeable rune packs, got "
                + aubury.getItems().size(), aubury.getItems().size() >= 12);
        Assert.assertTrue("Aubury stocks a rune pack",
            aubury.getItems().stream().anyMatch(i -> i.getName().toLowerCase().contains("rune pack")));

        // A shop that used to be dropped because 100% of its stock is untradeable.
        Shop grace = database.getShopByName("Grace's Graceful Clothing");
        Assert.assertNotNull("Grace's Graceful Clothing should now exist", grace);
        Assert.assertTrue(grace.getItems().stream().anyMatch(i -> i.getName().equalsIgnoreCase("Graceful top")));
        Assert.assertEquals("bought with marks of grace", "MARK_OF_GRACE", grace.getCurrency().name());
        for (com.osrscopilot.data.model.ShopItem it : grace.getItems())
        {
            Assert.assertTrue(it.getName() + " price should be positive", it.getPrice() > 0);
        }
    }

    @Test
    public void testNoSeasonalOrPlaceholderShops()
    {
        for (Shop s : database.getAllShops())
        {
            String n = s.getName();
            Assert.assertFalse("no Leagues seasonal shop: " + n, n.startsWith("Leagues Reward Shop"));
            Assert.assertFalse("no broken owner-lookup placeholder: " + n,
                "Farming Supplies".equals(n) && (s.getNpcName() == null || s.getNpcName().equalsIgnoreCase("No")));
        }
    }

    @Test
    public void testMultiShopNpcResolution()
    {
        // Gabooty (npc 6424) runs 3 shops; getShopByNpcId returns one, getShopsByNpcId returns all.
        List<Shop> gabooty = database.getShopsByNpcId(6424);
        if (!gabooty.isEmpty())
        {
            Assert.assertTrue("Gabooty runs more than one shop", gabooty.size() >= 2);
            Shop primary = database.getShopByNpcId(6424);
            Assert.assertNotNull(primary);
            for (Shop s : gabooty)
            {
                Assert.assertTrue("primary has the most stock",
                    (primary.getItems() == null ? 0 : primary.getItems().size())
                        >= (s.getItems() == null ? 0 : s.getItems().size()));
            }
        }
    }
}
