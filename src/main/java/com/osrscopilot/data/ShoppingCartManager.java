package com.osrscopilot.data;

import com.osrscopilot.data.model.CartItem;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.ShopItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ShoppingCartManager
{
    private static ShoppingCartManager instance;

    private final List<CartItem> items = new CopyOnWriteArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private long lastKnownBankCoins = -1;
    private long lastKnownInventoryCoins = 0;

    @Inject
    public ShoppingCartManager()
    {
        instance = this;
    }

    public static synchronized ShoppingCartManager getInstance()
    {
        if (instance == null)
        {
            instance = new ShoppingCartManager();
        }
        return instance;
    }

    /**
     * Adds an item to the shopping cart.
     * If the item is already present for the specified shop, increments its quantity.
     * Otherwise, creates a new cart entry.
     */
    public synchronized void addItem(ShopItem item, Shop shop, int qty)
    {
        if (item == null || shop == null || qty <= 0)
        {
            return;
        }

        for (CartItem existing : items)
        {
            if (existing.getItemId() == item.getItemId() &&
                existing.getShopName().equalsIgnoreCase(shop.getName()))
            {
                existing.setQuantity(existing.getQuantity() + qty);
                notifyListeners();
                return;
            }
        }

        CartItem newCartItem = CartItem.builder()
            .itemId(item.getItemId())
            .itemName(item.getName())
            .quantity(qty)
            .unitPrice(item.getPrice())
            .shopName(shop.getName())
            .townName(shop.getTown())
            .worldPoint(shop.getWorldPoint())
            .defaultStock(item.getDefaultStock())
            .build();

        items.add(newCartItem);
        notifyListeners();
    }

    /**
     * Adds a pre-constructed CartItem directly.
     */
    public synchronized void addItem(CartItem cartItem)
    {
        if (cartItem == null || cartItem.getQuantity() <= 0)
        {
            return;
        }

        for (CartItem existing : items)
        {
            if (existing.getItemId() == cartItem.getItemId() &&
                existing.getShopName().equalsIgnoreCase(cartItem.getShopName()))
            {
                existing.setQuantity(existing.getQuantity() + cartItem.getQuantity());
                notifyListeners();
                return;
            }
        }

        items.add(cartItem);
        notifyListeners();
    }

    /**
     * Removes an item matching the itemId and shopName from the cart.
     */
    public synchronized void removeItem(int itemId, String shopName)
    {
        boolean removed = items.removeIf(item ->
            item.getItemId() == itemId &&
            (shopName == null || item.getShopName().equalsIgnoreCase(shopName))
        );

        if (removed)
        {
            notifyListeners();
        }
    }

    /**
     * Removes a specific CartItem instance from the cart.
     */
    public synchronized void removeItem(CartItem cartItem)
    {
        if (cartItem != null && items.remove(cartItem))
        {
            notifyListeners();
        }
    }

    /**
     * Sets the quantity of a specific item from a shop.
     * If quantity is <= 0, the item is removed from the cart.
     */
    public synchronized void setQuantity(int itemId, String shopName, int qty)
    {
        if (qty <= 0)
        {
            removeItem(itemId, shopName);
            return;
        }

        for (CartItem item : items)
        {
            if (item.getItemId() == itemId &&
                (shopName == null || item.getShopName().equalsIgnoreCase(shopName)))
            {
                item.setQuantity(qty);
                notifyListeners();
                return;
            }
        }
    }

    /**
     * Increments or decrements the quantity of an item by a delta amount.
     * If result is <= 0, removes the item.
     */
    public synchronized void incrementQuantity(int itemId, String shopName, int delta)
    {
        for (CartItem item : items)
        {
            if (item.getItemId() == itemId &&
                (shopName == null || item.getShopName().equalsIgnoreCase(shopName)))
            {
                int newQty = item.getQuantity() + delta;
                if (newQty <= 0)
                {
                    items.remove(item);
                }
                else
                {
                    item.setQuantity(newQty);
                }
                notifyListeners();
                return;
            }
        }
    }

    /**
     * Calculates total cost in gp across all items in the cart (as int).
     */
    public int getTotalCost()
    {
        long total = getTotalCostLong();
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    /**
     * Calculates total cost in gp across all items in the cart (as 64-bit long).
     */
    public long getTotalCostLong()
    {
        long total = 0;
        for (CartItem item : items)
        {
            total += item.getSubtotalLong();
        }
        return total;
    }

    /**
     * Calculates total estimated dynamic cost in gp accounting for OSRS shop stock depletion price inflation.
     */
    public long getTotalDynamicCostLong()
    {
        long total = 0;
        for (CartItem item : items)
        {
            total += item.getDynamicSubtotalLong();
        }
        return total;
    }

    /**
     * Returns total number of distinct cart entries.
     */
    public int getItemCount()
    {
        return items.size();
    }

    /**
     * Returns total sum of item quantities.
     */
    public int getTotalQuantity()
    {
        int total = 0;
        for (CartItem item : items)
        {
            total += item.getQuantity();
        }
        return total;
    }

    /**
     * Returns an unmodifiable view of current items in the cart.
     */
    public List<CartItem> getItems()
    {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    /**
     * Clears all items from the cart.
     */
    public synchronized void clear()
    {
        if (!items.isEmpty())
        {
            items.clear();
            notifyListeners();
        }
    }

    /**
     * Registers a listener callback invoked whenever cart contents or quantities change.
     */
    public void addCartListener(Runnable listener)
    {
        if (listener != null && !listeners.contains(listener))
        {
            listeners.add(listener);
        }
    }

    /**
     * Unregisters a listener callback.
     */
    public void removeCartListener(Runnable listener)
    {
        if (listener != null)
        {
            listeners.remove(listener);
        }
    }

    /**
     * Updates the last known bank coins and notifies listeners.
     */
    public synchronized void setBankCoins(long coins)
    {
        if (this.lastKnownBankCoins != coins)
        {
            this.lastKnownBankCoins = coins;
            notifyListeners();
        }
    }

    /**
     * Updates the last known inventory coins and notifies listeners.
     */
    public synchronized void setInventoryCoins(long coins)
    {
        if (this.lastKnownInventoryCoins != coins)
        {
            this.lastKnownInventoryCoins = coins;
            notifyListeners();
        }
    }

    /**
     * Gets the last known bank coin balance (-1 if not yet opened/synced).
     */
    public long getLastKnownBankCoins()
    {
        return lastKnownBankCoins;
    }

    /**
     * Gets the last known inventory coin balance.
     */
    public long getLastKnownInventoryCoins()
    {
        return lastKnownInventoryCoins;
    }

    /**
     * Notifies all registered listeners of a cart state change.
     */
    public void notifyListeners()
    {
        for (Runnable listener : listeners)
        {
            try
            {
                listener.run();
            }
            catch (Exception ex)
            {
                log.error("Error invoking cart listener", ex);
            }
        }
    }
}
