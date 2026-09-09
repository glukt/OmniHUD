package com.osrscopilot.loot.model;

import java.util.List;
import java.util.Locale;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * One loot event - append-only. A single kill/chest that dropped several items is ONE record with
 * several {@link LootItem}s, never several records (keeps kill-count and event counts honest).
 */
@Value
@Builder
public class LootRecord
{
    public static final int SCHEMA_VERSION = 1;

    long id;                 // monotonic per-character, assigned on insert
    long timestampEpochMs;
    String characterName;    // display only
    long accountHash;        // stable per-character key; -1 if unavailable (logged out / no account)
    SourceKind sourceKind;
    int npcId;               // -1 if not an NPC source
    String sourceName;       // "Vorkath" / "Barrows" / "Master Farmer" / "Grubby Chest"
    int combatLevel;         // -1 if n/a
    int killCountAtDrop;     // KC for this source at drop time; -1 if unknown
    int worldId;
    int regionId;            // player region id at drop time; -1 if n/a
    String slayerTaskName;   // task active at drop time, else null
    boolean instanced;
    boolean imported;        // synthesized from RuneLite's stock Loot Tracker (aggregate, no real timeline)
    @Singular("item") List<LootItem> items;
    int schemaVersion;

    /** Stable grouping key - name-based so a boss's multiple NPC ids fold together. */
    public String sourceKey()
    {
        String n = sourceName == null ? "?" : sourceName.trim().toLowerCase(Locale.ROOT);
        return (sourceKind == null ? "UNKNOWN" : sourceKind.name()) + ":" + n;
    }

    public long totalGe()
    {
        long t = 0;
        if (items != null)
        {
            for (LootItem i : items)
            {
                t += i.totalGe();
            }
        }
        return t;
    }

    public long totalHa()
    {
        long t = 0;
        if (items != null)
        {
            for (LootItem i : items)
            {
                t += i.totalHa();
            }
        }
        return t;
    }

    public long totalBestValue()
    {
        long t = 0;
        if (items != null)
        {
            for (LootItem i : items)
            {
                t += i.bestValueTotal();
            }
        }
        return t;
    }
}
