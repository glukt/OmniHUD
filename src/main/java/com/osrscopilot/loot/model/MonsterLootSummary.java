package com.osrscopilot.loot.model;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

/**
 * Rolled-up loot for one source (a monster / boss / chest), keyed by {@link LootRecord#sourceKey()}.
 * Rebuilt from the record list on load and maintained incrementally as new records land.
 */
@Data
public class MonsterLootSummary
{
    private final String sourceKey;
    private SourceKind sourceKind;
    private String sourceName;
    private int npcId = -1;

    private int totalKills;      // includes dry kills fed via recordKill()
    private int lootedKills;     // kills that dropped at least one tracked item
    private long totalBestValue;
    private long firstSeenMs;
    private long lastSeenMs;
    // Monotonic "last touched" counter bumped on every kill / drop for this source. The "Recent"
    // sort orders by this (not a wall-clock timestamp) so the source just acted on is always #1,
    // with no clock-skew / equal-millisecond ties.
    private long lastTouchSeq;

    private final Map<Integer, ItemStat> itemStats = new HashMap<>();

    public MonsterLootSummary(String sourceKey)
    {
        this.sourceKey = sourceKey;
    }

    public ItemStat itemStat(int itemId)
    {
        return itemStats.computeIfAbsent(itemId, ItemStat::new);
    }

    /** GP/hr given elapsed tracked seconds (wall-clock), 0 when there's no elapsed time. */
    public long gpPerHour(long elapsedSeconds)
    {
        if (elapsedSeconds <= 0)
        {
            return 0;
        }
        return Math.round(totalBestValue / (elapsedSeconds / 3600.0));
    }
}
