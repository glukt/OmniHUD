package com.osrscopilot.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.runelite.api.coords.WorldPoint;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem
{
    private int itemId;
    private String itemName;
    private int quantity;
    private int unitPrice;
    private String shopName;
    private String townName;
    private WorldPoint worldPoint;
    private int defaultStock;

    /**
     * Calculates the subtotal cost (quantity * unit price).
     */
    public int getSubtotal()
    {
        long total = (long) quantity * (long) unitPrice;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    /**
     * Calculates 64-bit subtotal cost to prevent integer overflow for high budget items.
     */
    public long getSubtotalLong()
    {
        return (long) quantity * (long) unitPrice;
    }

    /**
     * Calculates dynamic stock depletion cost for runes/supplies where price scales up
     * as stock is bought out from the shop.
     */
    public long getDynamicSubtotalLong()
    {
        if (quantity <= 0)
        {
            return 0;
        }

        long total = 0;
        for (int i = 0; i < quantity; i++)
        {
            // OSRS price delta: ~0.1% increase per unit below default stock
            double priceRise = Math.max(0.1, (double) unitPrice * 0.001);
            long itemPrice = Math.max((long) unitPrice, Math.round(unitPrice + (i * priceRise)));
            // OSRS caps price rise at 3x base price
            itemPrice = Math.min((long) unitPrice * 3L, itemPrice);
            total += itemPrice;
        }
        return total;
    }
}
