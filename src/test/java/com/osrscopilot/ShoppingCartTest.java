package com.osrscopilot;

import com.osrscopilot.data.ShoppingCartManager;
import com.osrscopilot.data.model.CartItem;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.ShopItem;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.coords.WorldPoint;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ShoppingCartTest
{
    private ShoppingCartManager cartManager;
    private Shop shop1;
    private Shop shop2;
    private ShopItem lawRune;
    private ShopItem cosmicRune;

    @Before
    public void setUp()
    {
        cartManager = new ShoppingCartManager();

        shop1 = Shop.builder()
            .id(1)
            .name("Betty's Magic Emporium")
            .town("Port Sarim")
            .worldX(3014)
            .worldY(3258)
            .worldPlane(0)
            .build();

        shop2 = Shop.builder()
            .id(2)
            .name("Aubury's Rune Shop")
            .town("Varrock")
            .worldX(3253)
            .worldY(3401)
            .worldPlane(0)
            .build();

        lawRune = ShopItem.builder()
            .itemId(563)
            .name("Law rune")
            .price(350)
            .defaultStock(250)
            .build();

        cosmicRune = ShopItem.builder()
            .itemId(564)
            .name("Cosmic rune")
            .price(50)
            .defaultStock(100)
            .build();
    }

    @Test
    public void testCartItemSubtotal()
    {
        CartItem item = CartItem.builder()
            .itemId(563)
            .itemName("Law rune")
            .quantity(100)
            .unitPrice(350)
            .shopName("Aubury's Rune Shop")
            .townName("Varrock")
            .worldPoint(new WorldPoint(3253, 3401, 0))
            .defaultStock(250)
            .build();

        Assert.assertEquals(35000, item.getSubtotal());
        Assert.assertEquals(35000L, item.getSubtotalLong());
    }

    @Test
    public void testAddItemAndIncrement()
    {
        cartManager.addItem(lawRune, shop1, 50);
        Assert.assertEquals(1, cartManager.getItemCount());
        Assert.assertEquals(50, cartManager.getTotalQuantity());
        Assert.assertEquals(50 * 350, cartManager.getTotalCost());

        // Adding same item to same shop should accumulate quantity
        cartManager.addItem(lawRune, shop1, 25);
        Assert.assertEquals(1, cartManager.getItemCount());
        Assert.assertEquals(75, cartManager.getTotalQuantity());
        Assert.assertEquals(75 * 350, cartManager.getTotalCost());
    }

    @Test
    public void testAddSameItemFromDifferentShops()
    {
        cartManager.addItem(lawRune, shop1, 50);
        cartManager.addItem(lawRune, shop2, 100);

        Assert.assertEquals(2, cartManager.getItemCount());
        Assert.assertEquals(150, cartManager.getTotalQuantity());
        Assert.assertEquals(150 * 350, cartManager.getTotalCost());
    }

    @Test
    public void testRemoveItem()
    {
        cartManager.addItem(lawRune, shop1, 50);
        cartManager.addItem(cosmicRune, shop1, 100);
        Assert.assertEquals(2, cartManager.getItemCount());

        cartManager.removeItem(lawRune.getItemId(), shop1.getName());
        Assert.assertEquals(1, cartManager.getItemCount());
        Assert.assertEquals("Cosmic rune", cartManager.getItems().get(0).getItemName());
        Assert.assertEquals(100 * 50, cartManager.getTotalCost());
    }

    @Test
    public void testSetQuantityAndRemoveOnZero()
    {
        cartManager.addItem(lawRune, shop1, 50);
        cartManager.setQuantity(lawRune.getItemId(), shop1.getName(), 200);
        Assert.assertEquals(200, cartManager.getItems().get(0).getQuantity());
        Assert.assertEquals(200 * 350, cartManager.getTotalCost());

        // Setting quantity to 0 removes the item
        cartManager.setQuantity(lawRune.getItemId(), shop1.getName(), 0);
        Assert.assertEquals(0, cartManager.getItemCount());
        Assert.assertEquals(0, cartManager.getTotalCost());
    }

    @Test
    public void testIncrementQuantity()
    {
        cartManager.addItem(cosmicRune, shop1, 20);
        cartManager.incrementQuantity(cosmicRune.getItemId(), shop1.getName(), 10);
        Assert.assertEquals(30, cartManager.getItems().get(0).getQuantity());

        cartManager.incrementQuantity(cosmicRune.getItemId(), shop1.getName(), -15);
        Assert.assertEquals(15, cartManager.getItems().get(0).getQuantity());

        // Decrementing past zero removes it
        cartManager.incrementQuantity(cosmicRune.getItemId(), shop1.getName(), -20);
        Assert.assertEquals(0, cartManager.getItemCount());
    }

    @Test
    public void testClear()
    {
        cartManager.addItem(lawRune, shop1, 50);
        cartManager.addItem(cosmicRune, shop2, 100);
        Assert.assertEquals(2, cartManager.getItemCount());

        cartManager.clear();
        Assert.assertEquals(0, cartManager.getItemCount());
        Assert.assertEquals(0, cartManager.getTotalCost());
    }

    @Test
    public void testCartListenerNotification()
    {
        AtomicInteger notificationCount = new AtomicInteger(0);
        cartManager.addCartListener(notificationCount::incrementAndGet);

        cartManager.addItem(lawRune, shop1, 10);
        cartManager.incrementQuantity(lawRune.getItemId(), shop1.getName(), 5);
        cartManager.setQuantity(lawRune.getItemId(), shop1.getName(), 25);
        cartManager.removeItem(lawRune.getItemId(), shop1.getName());

        Assert.assertEquals(4, notificationCount.get());
    }

    @Test
    public void testBankAndInventoryCoinsDefaults()
    {
        Assert.assertEquals(-1L, cartManager.getLastKnownBankCoins());
        Assert.assertEquals(0L, cartManager.getLastKnownInventoryCoins());
    }

    @Test
    public void testSetBankAndInventoryCoins()
    {
        cartManager.setBankCoins(25_000_000L);
        Assert.assertEquals(25_000_000L, cartManager.getLastKnownBankCoins());

        cartManager.setInventoryCoins(150_000L);
        Assert.assertEquals(150_000L, cartManager.getLastKnownInventoryCoins());

        cartManager.setBankCoins(0L);
        Assert.assertEquals(0L, cartManager.getLastKnownBankCoins());
    }

    @Test
    public void testBankAndInventoryCoinsListenerNotification()
    {
        AtomicInteger notificationCount = new AtomicInteger(0);
        cartManager.addCartListener(notificationCount::incrementAndGet);

        cartManager.setBankCoins(1_000_000L);
        cartManager.setInventoryCoins(50_000L);

        // Re-setting the exact same values shouldn't re-trigger notification unnecessarily
        cartManager.setBankCoins(1_000_000L);
        cartManager.setInventoryCoins(50_000L);

        Assert.assertEquals(2, notificationCount.get());
    }
}
