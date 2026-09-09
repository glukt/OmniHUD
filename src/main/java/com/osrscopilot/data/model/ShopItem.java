package com.osrscopilot.data.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ShopItem
{
    int itemId;
    String name;
    int price;
    int buyPrice;
    int defaultStock;
    int restockTimeSeconds;
    boolean zeroDefaultStock;
    boolean ironmanBlocked;
    List<String> essentialTags;

    /**
     * Returns the price the shop buys this item from players for.
     */
    public int getEffectiveBuyPrice()
    {
        if (buyPrice > 0)
        {
            return buyPrice;
        }
        return Math.max(1, (int) (price * 0.50));
    }
}
