package com.osrscopilot.loot.model;

import lombok.Builder;
import lombok.Value;

/**
 * One item stack within a {@link LootRecord}. Prices are a snapshot taken at drop time so the loot
 * history shows what a drop was worth then, and totals don't drift as GE prices move.
 */
@Value
@Builder
public class LootItem
{
    int itemId;          // canonicalized (unnoted, non-placeholder)
    int quantity;
    String name;         // itemComposition.getName() at drop time (may be null on old records)
    long gePriceEach;    // itemManager.getItemPrice(itemId) at drop time, 0 if none
    long haPriceEach;    // itemComposition.getHaPrice() at drop time, 0 if none
    boolean noted;

    public long totalGe()
    {
        return (long) gePriceEach * quantity;
    }

    public long totalHa()
    {
        return (long) haPriceEach * quantity;
    }

    /** Per-item value for display: GE when it has one, else the high-alch value. */
    public long bestValueEach()
    {
        return gePriceEach > 0 ? gePriceEach : haPriceEach;
    }

    public long bestValueTotal()
    {
        return (long) bestValueEach() * quantity;
    }
}
