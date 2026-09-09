package com.osrscopilot.data;

import com.osrscopilot.data.model.Shop;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

@Slf4j
@Singleton
public class ShopLiveStockManager
{
    private final Map<Integer, Map<Integer, Integer>> liveShopStocks = new ConcurrentHashMap<>();
    private final Map<Integer, Long> liveShopSyncTimes = new ConcurrentHashMap<>();

    private int activeShopId = -1;

    @Inject
    public ShopLiveStockManager()
    {
    }

    public void setActiveShop(Shop shop)
    {
        if (shop != null)
        {
            this.activeShopId = shop.getId();
        }
    }

    public void updateFromContainer(int shopId, ItemContainer container)
    {
        if (container == null)
        {
            return;
        }

        Map<Integer, Integer> stockMap = new HashMap<>();
        for (Item item : container.getItems())
        {
            if (item.getId() > 0 && item.getQuantity() >= 0)
            {
                stockMap.put(item.getId(), item.getQuantity());
            }
        }

        liveShopStocks.put(shopId, stockMap);
        liveShopSyncTimes.put(shopId, System.currentTimeMillis());
        log.debug("Updated live stock for shop ID {}: {} items", shopId, stockMap.size());
    }

    public Integer getLiveStock(Shop shop, int itemId)
    {
        if (shop == null) return null;
        Map<Integer, Integer> stock = liveShopStocks.get(shop.getId());
        if (stock != null)
        {
            return stock.get(itemId);
        }
        return null;
    }

    public boolean hasLiveStock(Shop shop)
    {
        return shop != null && liveShopStocks.containsKey(shop.getId());
    }

    public Long getLastSyncTime(Shop shop)
    {
        return shop != null ? liveShopSyncTimes.get(shop.getId()) : null;
    }

    public int getActiveShopId()
    {
        return activeShopId;
    }
}
