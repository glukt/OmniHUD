package com.osrscopilot.loot.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Rolled-up stats for one (source, item) pair. Rebuilt from {@link LootRecord}s on load; the UI reads
 * this rather than re-scanning the whole history per row.
 */
@Data
public class ItemStat
{
    private final int itemId;

    private String itemName;         // resolved from the first LootItem that carried a name
    private int count;               // number of drop events that included this item
    private int realCount;           // of those, events from live capture (not the one-time stock import)
    private long totalQty;
    private long firstSeenMs;
    private long lastSeenMs;
    private int killCountAtFirst = -1;
    private int killCountAtLast = -1;
    private long totalBestValue;
    private final List<Long> recordIds = new ArrayList<>();

    public ItemStat(int itemId)
    {
        this.itemId = itemId;
    }

    /** KC elapsed on this source since this item last dropped (needs the source's current KC). */
    public int killsSinceLast(int currentKillCount)
    {
        if (killCountAtLast < 0 || currentKillCount < 0)
        {
            return -1;
        }
        return Math.max(0, currentKillCount - killCountAtLast);
    }

    public boolean isObtained()
    {
        return count > 0;
    }

    /**
     * True when this item is only present via RuneLite's stock Loot Tracker import. That data is
     * aggregate (one row per item, no per-drop timeline), so a "1/N" rate or dry/lucky verdict
     * derived from {@link #count} would be fiction - callers should show the raw quantity instead.
     */
    public boolean isImportedOnly()
    {
        return count > 0 && realCount == 0;
    }
}
