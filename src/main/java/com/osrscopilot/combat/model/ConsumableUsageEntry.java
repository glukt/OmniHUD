package com.osrscopilot.combat.model;

public class ConsumableUsageEntry
{
    private final int itemId;
    private final String itemName;
    private final int quantity;
    private final long totalCost;

    public ConsumableUsageEntry(int itemId, String itemName, int quantity, long totalCost)
    {
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.totalCost = totalCost;
    }

    public int getItemId()
    {
        return itemId;
    }

    public String getItemName()
    {
        return itemName;
    }

    public int getQuantity()
    {
        return quantity;
    }

    public long getTotalCost()
    {
        return totalCost;
    }
}
