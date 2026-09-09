package com.osrscopilot.loot;

import com.google.gson.Gson;
import com.osrscopilot.loot.model.ItemStat;
import com.osrscopilot.loot.model.LootItem;
import com.osrscopilot.loot.model.LootRecord;
import com.osrscopilot.loot.model.MonsterLootSummary;
import com.osrscopilot.loot.model.SourceKind;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import com.osrscopilot.util.CopilotPaths;

/**
 * Owns the loot history for the currently-logged-in character: an append-only list of
 * {@link LootRecord}s, a plugin-side kill counter per source, and the rolled-up
 * {@link MonsterLootSummary} the UI reads. Persists one gzipped JSON file per character under
 * {@code RUNELITE_DIR/osrscopilot/loot/}.
 *
 * <p>Deliberately decoupled from RuneLite events - the plugin translates
 * {@code NpcLootReceived} / {@code PlayerLootReceived} / {@code LootReceived} / kill signals and
 * calls {@link #recordLoot} / {@link #recordKill} here, which keeps this class unit-testable.
 */
@Slf4j
@Singleton
public class LootTrackerManager
{
    private static final long FLUSH_DELAY_SECONDS = 5;

    private final Gson gson;
    private final ScheduledExecutorService executor;
    private final File lootDir;
    // Used only by stockLootTrackerAvailable(). Null under the test seam - the probe then
    // fails open (returns true) so tests never see the "enable Loot Tracker" hint.
    private final PluginManager pluginManager;

    private final Object lock = new Object();

    private final List<LootRecord> records = new ArrayList<>();
    private final Map<String, Integer> killCountBySource = new HashMap<>();
    private final Map<String, MonsterLootSummary> summaries = new LinkedHashMap<>();

    @Getter
    private long activeAccountHash = -1;
    @Getter
    private String activeCharacterName = "";
    private long nextId = 1;
    @Getter
    private long sessionStartMs = System.currentTimeMillis();

    private boolean dirty;
    private boolean imported;
    private ScheduledFuture<?> pendingFlush;

    // Fired after any capture (loot / kill / import / character switch) so the panel can refresh
    // live instead of only on tab switch. Listeners must be cheap and must not block.
    private final List<Runnable> changeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    // Set on any capture; the plugin's onGameTick polls + clears it to refresh the panel - a
    // guaranteed path that doesn't depend on Swing timer / listener registration timing.
    private volatile boolean changedSincePoll;
    private long touchSeq;
    // Count of (source,item) stats with no resolved name yet. Maintained incrementally so the
    // plugin's per-tick name-resolve pass can skip the full summaries scan once everything resolves.
    private volatile int namelessCount;

    @Inject
    public LootTrackerManager(Gson gson, ScheduledExecutorService executor, PluginManager pluginManager)
    {
        this(gson, executor, CopilotPaths.dataSubDir("loot"), pluginManager);
    }

    /** Test seam - lets a test point the store at a temp dir (and skip the stock-plugin probe). */
    LootTrackerManager(Gson gson, ScheduledExecutorService executor, File lootDir)
    {
        this(gson, executor, lootDir, null);
    }

    private LootTrackerManager(Gson gson, ScheduledExecutorService executor, File lootDir, PluginManager pluginManager)
    {
        this.gson = gson;
        this.executor = executor;
        this.lootDir = lootDir;
        this.pluginManager = pluginManager;
    }

    /**
     * Whether RuneLite's stock <b>Loot Tracker</b> plugin is present and enabled. It supplies the
     * {@code LootReceived} events this tab's pickpocket / clue / chest rows are built from - NPC
     * kill loot arrives separately via {@code onNpcLootReceived} and is unaffected. We no longer
     * hard-depend on it (the {@code @PluginDependency} was dropped so users can opt out), so
     * {@link com.osrscopilot.loot.ui.LootTabView} calls this and shows a partial-capability hint
     * when it's off. Returns {@code true} when we can't tell (no PluginManager / test seam) so we
     * never nag without cause.
     */
    public boolean stockLootTrackerAvailable()
    {
        if (pluginManager == null)
        {
            return true;
        }
        for (Plugin p : pluginManager.getPlugins())
        {
            if (p instanceof LootTrackerPlugin)
            {
                return pluginManager.isPluginEnabled(p);
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ character lifecycle

    /**
     * Switch to the loot store for a character. Flushes the previous character, loads this one's
     * file, and rebuilds the aggregates. No-op if it's the same account.
     */
    public void setActiveCharacter(long accountHash, String name)
    {
        if (demoLoot)
        {
            return;
        }
        synchronized (lock)
        {
            if (accountHash == activeAccountHash)
            {
                if (name != null && !name.isEmpty())
                {
                    activeCharacterName = name;
                }
                return;
            }

            if (activeAccountHash != -1 && dirty)
            {
                writeToDisk();
            }

            activeAccountHash = accountHash;
            activeCharacterName = name == null ? "" : name;
            records.clear();
            killCountBySource.clear();
            summaries.clear();
            nextId = 1;
            dirty = false;
            imported = false;
            // "This session" is per-character - a switch starts a fresh session clock, otherwise
            // the new character's session-scope GP/hr is measured from the old one's login.
            sessionStartMs = System.currentTimeMillis();

            readFromDisk();
            rebuildAggregates();
        }
        fireChanged();
    }

    public void resetSession()
    {
        if (demoLoot)
        {
            return;
        }
        synchronized (lock)
        {
            sessionStartMs = System.currentTimeMillis();
        }
    }

    // ------------------------------------------------------------------ guided-tour demo loot

    // volatile: the capture path (recordLoot / recordKill / setActiveCharacter / importFromStock)
    // reads this before entering synchronized(lock), so writes from the tour thread must publish.
    private volatile boolean demoLoot = false;
    private List<LootRecord> savedRecords;
    private Map<String, Integer> savedKillCounts;
    private Map<String, MonsterLootSummary> savedSummaries;
    private long savedNextId;
    private long savedSessionStartMs;

    /**
     * Install a canned "~90 Rune dragons on a Metal dragons task" loot history for the Slayer
     * guided tour. Nothing is persisted (writeToDisk() is a no-op while active) and the real
     * history is restored by {@link #clearDemoLoot()}.
     */
    public void installSlayerTourDemoLoot()
    {
        synchronized (lock)
        {
            if (!demoLoot)
            {
                savedRecords = new ArrayList<>(records);
                savedKillCounts = new HashMap<>(killCountBySource);
                savedSummaries = new LinkedHashMap<>(summaries);
                savedNextId = nextId;
                savedSessionStartMs = sessionStartMs;
                demoLoot = true;
            }
            records.clear();
            killCountBySource.clear();
            summaries.clear();
            nextId = 1;
            sessionStartMs = System.currentTimeMillis() - 42L * 60 * 1000; // ~42 min elapsed for GP/hr

            final String src = "Rune dragon";
            killCountBySource.put(src.toLowerCase(Locale.ROOT), 90);
            long now = System.currentTimeMillis();
            for (int k = 1; k <= 90; k++)
            {
                List<LootItem> its = new ArrayList<>();
                its.add(demoItem(536, 1, "Dragon bones", 2800));      // 100%
                its.add(demoItem(2364, 1, "Runite bar", 12800));      // 100%
                if (k % 15 == 0) its.add(demoItem(1127, 1, "Rune platebody", 38000));
                if (k % 18 == 0) its.add(demoItem(1303, 1, "Rune longsword", 19000));
                if (k % 30 == 0) its.add(demoItem(451, 2, "Runite ore", 11000));
                if (k % 9 == 0)  its.add(demoItem(995, 8000 + k * 41, "Coins", 1));
                if (k == 44)     its.add(demoItem(4087, 1, "Dragon platelegs", 160000));
                LootRecord rec = LootRecord.builder()
                    .id(nextId++)
                    .timestampEpochMs(now - (90 - k) * 28_000L)
                    .characterName(activeCharacterName)
                    .accountHash(activeAccountHash)
                    .sourceKind(SourceKind.NPC_KILL)
                    .npcId(8031)
                    .sourceName(src)
                    .combatLevel(380)
                    .killCountAtDrop(k)
                    .worldId(0)
                    .regionId(-1)
                    .slayerTaskName("Metal dragons")
                    .instanced(false)
                    .schemaVersion(LootRecord.SCHEMA_VERSION)
                    .items(its)
                    .build();
                records.add(rec);
                applyToSummary(rec);
            }
            MonsterLootSummary s = summaries.get(SourceKind.NPC_KILL.name() + ":" + src.toLowerCase(Locale.ROOT));
            if (s != null)
            {
                s.setTotalKills(90);
            }
        }
        changedSincePoll = true;
        fireChanged();
    }

    /** Restore the real loot history after the Slayer tour. */
    public void clearDemoLoot()
    {
        synchronized (lock)
        {
            if (!demoLoot)
            {
                return;
            }
            demoLoot = false;
            records.clear();
            records.addAll(savedRecords);
            killCountBySource.clear();
            killCountBySource.putAll(savedKillCounts);
            summaries.clear();
            summaries.putAll(savedSummaries);
            nextId = savedNextId;
            sessionStartMs = savedSessionStartMs;
            savedRecords = null;
            savedKillCounts = null;
            savedSummaries = null;
            if (dirty)
            {
                scheduleFlush(); // persist any real work that was pending before the demo
            }
        }
        changedSincePoll = true;
        fireChanged();
    }

    public boolean isDemoLoot()
    {
        synchronized (lock)
        {
            return demoLoot;
        }
    }

    private static LootItem demoItem(int id, int qty, String name, long ge)
    {
        return LootItem.builder().itemId(id).quantity(qty).name(name)
            .gePriceEach(ge).haPriceEach(ge * 3 / 5).noted(false).build();
    }

    /** Wipe this character's loot history (records, kill counts, aggregates) and persist the empty store. */
    public void clearAll()
    {
        if (demoLoot)
        {
            return;
        }
        synchronized (lock)
        {
            records.clear();
            killCountBySource.clear();
            summaries.clear();
            nextId = 1;
            touchSeq = 0;
            imported = true; // a deliberate wipe - don't auto-refill from the stock tracker
            dirty = true;
            writeToDisk();
        }
        changedSincePoll = true;
        fireChanged();
    }

    public void addChangeListener(Runnable r)
    {
        if (r != null)
        {
            changeListeners.add(r);
        }
    }

    public void removeChangeListener(Runnable r)
    {
        changeListeners.remove(r);
    }

    private void fireChanged()
    {
        for (Runnable r : changeListeners)
        {
            try
            {
                r.run();
            }
            catch (Exception ignored)
            {
                // a bad listener must not break capture
            }
        }
    }

    // ------------------------------------------------------------------ capture

    /**
     * Record one loot event. A kill/chest that dropped several items is ONE call with all the items.
     * Returns the stored record, or null when {@code items} is empty (use {@link #recordKill} to note
     * a dry kill).
     */
    public LootRecord recordLoot(
        SourceKind kind,
        int npcId,
        String sourceName,
        int combatLevel,
        int worldId,
        int regionId,
        String slayerTaskName,
        boolean instanced,
        List<LootItem> items)
    {
        if (items == null || items.isEmpty() || demoLoot)
        {
            return null;
        }

        synchronized (lock)
        {
            int kc;
            if (kind == SourceKind.PICKPOCKET && sourceName != null && !sourceName.trim().isEmpty())
            {
                // No separate pickpocket signal (combat's kill-sink is NPC deaths only), so each
                // loot event IS one successful pickpocket. Keyed "pp:<name>" so it can't collide
                // with the same NPC's combat kill count.
                kc = killCountBySource.merge("pp:" + sourceName.trim().toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
            else if (kind == SourceKind.NPC_KILL)
            {
                kc = currentKillCountInternal(sourceName); // advanced by the combat kill-sink
            }
            else
            {
                kc = -1;
            }

            LootRecord.LootRecordBuilder b = LootRecord.builder()
                .id(nextId++)
                .timestampEpochMs(System.currentTimeMillis())
                .characterName(activeCharacterName)
                .accountHash(activeAccountHash)
                .sourceKind(kind == null ? SourceKind.UNKNOWN : kind)
                .npcId(npcId)
                .sourceName(sourceName)
                .combatLevel(combatLevel)
                .killCountAtDrop(kc)
                .worldId(worldId)
                .regionId(regionId)
                .slayerTaskName(slayerTaskName)
                .instanced(instanced)
                .schemaVersion(LootRecord.SCHEMA_VERSION);

            for (LootItem i : items)
            {
                b.item(i);
            }
            LootRecord rec = b.build();

            records.add(rec);
            applyToSummary(rec);
            markDirty();
            return rec;
        }
    }

    /**
     * Note a kill on a source even when it dropped nothing tracked, so kill-count / dryness advance
     * on every kill rather than only on kills that produced a drop.
     */
    public void recordKill(String sourceName)
    {
        if (sourceName == null || sourceName.trim().isEmpty() || demoLoot)
        {
            return;
        }
        synchronized (lock)
        {
            String k = sourceName.trim().toLowerCase(Locale.ROOT);
            int kc = killCountBySource.merge(k, 1, Integer::sum);

            MonsterLootSummary s = summaries.computeIfAbsent(
                SourceKind.NPC_KILL.name() + ":" + k, MonsterLootSummary::new);
            if (s.getSourceName() == null)
            {
                s.setSourceName(sourceName.trim());
                s.setSourceKind(SourceKind.NPC_KILL);
            }
            s.setTotalKills(kc);
            // A kill - even a dry one - is activity on this source, so it moves to the top under
            // the "Recent" sort, matching the stock Loot Tracker.
            long now = System.currentTimeMillis();
            if (s.getFirstSeenMs() == 0)
            {
                s.setFirstSeenMs(now);
            }
            s.setLastSeenMs(now);
            s.setLastTouchSeq(++touchSeq);
            markDirty();
        }
    }

    /**
     * Raise a source's kill count to at least {@code atLeast} (Math.max), for the game's own
     * "kill count is: N" chat lines. Never lowers an existing count and never appends a record - a
     * pure correction so dryness maths uses the true KC without overwriting captured progress.
     */
    public void raiseKillCount(String sourceName, int atLeast)
    {
        if (sourceName == null || sourceName.trim().isEmpty() || atLeast <= 0 || demoLoot)
        {
            return;
        }
        synchronized (lock)
        {
            String k = sourceName.trim().toLowerCase(Locale.ROOT);
            int current = killCountBySource.getOrDefault(k, 0);
            if (atLeast <= current)
            {
                return;
            }
            killCountBySource.put(k, atLeast);

            MonsterLootSummary s = summaries.computeIfAbsent(
                SourceKind.NPC_KILL.name() + ":" + k, MonsterLootSummary::new);
            if (s.getSourceName() == null)
            {
                s.setSourceName(sourceName.trim());
                s.setSourceKind(SourceKind.NPC_KILL);
            }
            s.setTotalKills(atLeast);
            long now = System.currentTimeMillis();
            if (s.getFirstSeenMs() == 0)
            {
                s.setFirstSeenMs(now);
            }
            s.setLastSeenMs(now);
            s.setLastTouchSeq(++touchSeq);
            markDirty();
        }
    }

    public int currentKillCount(String sourceName)
    {
        synchronized (lock)
        {
            return currentKillCountInternal(sourceName);
        }
    }

    /** True once {@link #importFromStock} has run for the active character. */
    public boolean hasImported()
    {
        synchronized (lock)
        {
            return imported;
        }
    }

    /**
     * One-time backfill from RuneLite's stock Loot Tracker. The stock data is aggregate (per-item
     * total quantity + kill count + first/last, no per-drop timeline), so each entry becomes ONE
     * {@code imported} record plus a kill-count seed. Real capture builds the true history from here on.
     */
    public void importFromStock(List<StockEntry> entries)
    {
        if (demoLoot)
        {
            return;
        }
        if (entries == null || entries.isEmpty())
        {
            synchronized (lock)
            {
                imported = true;
                markDirty();
            }
            return;
        }
        synchronized (lock)
        {
            if (imported)
            {
                return;
            }
            for (StockEntry e : entries)
            {
                if (e == null || e.name == null || e.name.trim().isEmpty())
                {
                    continue;
                }
                if (e.kind == SourceKind.NPC_KILL && e.kills > 0)
                {
                    String k = e.name.trim().toLowerCase(Locale.ROOT);
                    killCountBySource.merge(k, e.kills, Math::max);
                }
                if (e.itemQty == null || e.itemQty.isEmpty())
                {
                    continue;
                }
                LootRecord.LootRecordBuilder b = LootRecord.builder()
                    .id(nextId++)
                    .timestampEpochMs(e.lastMs > 0 ? e.lastMs : System.currentTimeMillis())
                    .characterName(activeCharacterName)
                    .accountHash(activeAccountHash)
                    .sourceKind(e.kind == null ? SourceKind.UNKNOWN : e.kind)
                    .npcId(-1)
                    .sourceName(e.name.trim())
                    .combatLevel(-1)
                    .killCountAtDrop(e.kind == SourceKind.NPC_KILL ? e.kills : -1)
                    .worldId(0)
                    .regionId(-1)
                    .slayerTaskName(null)
                    .instanced(false)
                    .imported(true)
                    .schemaVersion(LootRecord.SCHEMA_VERSION);
                for (Map.Entry<Integer, Integer> iq : e.itemQty.entrySet())
                {
                    if (iq.getKey() != null && iq.getKey() > 0 && iq.getValue() != null && iq.getValue() > 0)
                    {
                        b.item(LootItem.builder().itemId(iq.getKey()).quantity(iq.getValue()).build());
                    }
                }
                LootRecord rec = b.build();
                if (!rec.getItems().isEmpty())
                {
                    records.add(rec);
                }
            }
            rebuildAggregates();
            imported = true;
            markDirty();
        }
    }

    /** Parsed shape of one RuneLite stock-Loot-Tracker entry, built by the plugin from config. */
    public static final class StockEntry
    {
        public SourceKind kind;
        public String name;
        public int kills;
        public long firstMs;
        public long lastMs;
        public Map<Integer, Integer> itemQty;
    }

    private int currentKillCountInternal(String sourceName)
    {
        if (sourceName == null || sourceName.trim().isEmpty())
        {
            return -1;
        }
        return killCountBySource.getOrDefault(sourceName.trim().toLowerCase(Locale.ROOT), 0);
    }

    // ------------------------------------------------------------------ read side (UI)

    public List<MonsterLootSummary> getSummaries()
    {
        synchronized (lock)
        {
            return new ArrayList<>(summaries.values());
        }
    }

    public MonsterLootSummary getSummary(String sourceKey)
    {
        synchronized (lock)
        {
            return summaries.get(sourceKey);
        }
    }

    public List<LootRecord> getAllRecords()
    {
        synchronized (lock)
        {
            return new ArrayList<>(records);
        }
    }

    /** Every record for a (source, item) pair, oldest first. */
    public List<LootRecord> getRecordsForItem(String sourceKey, int itemId)
    {
        synchronized (lock)
        {
            List<LootRecord> out = new ArrayList<>();
            for (LootRecord r : records)
            {
                if (!r.sourceKey().equals(sourceKey))
                {
                    continue;
                }
                for (LootItem i : r.getItems())
                {
                    if (i.getItemId() == itemId)
                    {
                        out.add(r);
                        break;
                    }
                }
            }
            return out;
        }
    }

    public int getRecordCount()
    {
        synchronized (lock)
        {
            return records.size();
        }
    }

    /** Write the whole loot history as CSV - one row per item stack. Never closes {@code w}. */
    public void writeCsv(java.io.Writer w) throws java.io.IOException
    {
        java.time.format.DateTimeFormatter iso = java.time.format.DateTimeFormatter.ISO_INSTANT;
        w.write("timestamp,character,source_kind,source_name,npc_id,kc_at_drop,world,region,"
            + "item_id,quantity,ge_each,ha_each,noted,slayer_task,instanced,imported\r\n");
        synchronized (lock)
        {
            for (LootRecord r : records)
            {
                String base = csv(iso.format(java.time.Instant.ofEpochMilli(r.getTimestampEpochMs())))
                    + "," + csv(r.getCharacterName())
                    + "," + (r.getSourceKind() == null ? "" : r.getSourceKind().name())
                    + "," + csv(r.getSourceName())
                    + "," + r.getNpcId()
                    + "," + r.getKillCountAtDrop()
                    + "," + r.getWorldId()
                    + "," + r.getRegionId();
                for (LootItem it : r.getItems())
                {
                    w.write(base
                        + "," + it.getItemId()
                        + "," + it.getQuantity()
                        + "," + it.getGePriceEach()
                        + "," + it.getHaPriceEach()
                        + "," + it.isNoted()
                        + "," + csv(r.getSlayerTaskName())
                        + "," + r.isInstanced()
                        + "," + r.isImported()
                        + "\r\n");
                }
            }
        }
    }

    private static String csv(String s)
    {
        if (s == null)
        {
            return "";
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0)
        {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    // ------------------------------------------------------------------ aggregation

    private void applyToSummary(LootRecord r)
    {
        MonsterLootSummary s = summaries.computeIfAbsent(r.sourceKey(), MonsterLootSummary::new);
        if (s.getSourceName() == null)
        {
            s.setSourceName(r.getSourceName());
        }
        if (s.getSourceKind() == null)
        {
            s.setSourceKind(r.getSourceKind());
        }
        if (s.getNpcId() < 0 && r.getNpcId() > 0)
        {
            s.setNpcId(r.getNpcId());
        }

        s.setLootedKills(s.getLootedKills() + 1);
        s.setLastTouchSeq(++touchSeq);
        s.setTotalBestValue(s.getTotalBestValue() + r.totalBestValue());
        if (s.getFirstSeenMs() == 0 || r.getTimestampEpochMs() < s.getFirstSeenMs())
        {
            s.setFirstSeenMs(r.getTimestampEpochMs());
        }
        if (r.getTimestampEpochMs() > s.getLastSeenMs())
        {
            s.setLastSeenMs(r.getTimestampEpochMs());
        }
        if (s.getTotalKills() < s.getLootedKills())
        {
            s.setTotalKills(s.getLootedKills());
        }

        for (LootItem it : r.getItems())
        {
            boolean freshStat = !s.getItemStats().containsKey(it.getItemId());
            ItemStat st = s.itemStat(it.getItemId());
            if (st.getItemName() == null && it.getName() != null && !it.getName().isEmpty())
            {
                st.setItemName(it.getName());
            }
            if (freshStat && (st.getItemName() == null || st.getItemName().isEmpty()))
            {
                namelessCount++;
            }
            st.setCount(st.getCount() + 1);
            if (!r.isImported())
            {
                st.setRealCount(st.getRealCount() + 1);
            }
            st.setTotalQty(st.getTotalQty() + it.getQuantity());
            st.setTotalBestValue(st.getTotalBestValue() + it.bestValueTotal());
            st.getRecordIds().add(r.getId());
            if (st.getFirstSeenMs() == 0 || r.getTimestampEpochMs() < st.getFirstSeenMs())
            {
                st.setFirstSeenMs(r.getTimestampEpochMs());
                st.setKillCountAtFirst(r.getKillCountAtDrop());
            }
            if (r.getTimestampEpochMs() >= st.getLastSeenMs())
            {
                st.setLastSeenMs(r.getTimestampEpochMs());
                st.setKillCountAtLast(r.getKillCountAtDrop());
            }
        }
    }

    private void rebuildAggregates()
    {
        summaries.clear();
        namelessCount = 0;
        for (LootRecord r : records)
        {
            applyToSummary(r);
        }
        // Overlay the persisted plugin-side kill counts (a running total; the per-record
        // killCountAtDrop is only a point sample).
        for (Map.Entry<String, Integer> e : killCountBySource.entrySet())
        {
            if (e.getKey().startsWith("pp:"))
            {
                continue; // pickpocket counters attach to their PICKPOCKET summary via applyToSummary
            }
            String key = SourceKind.NPC_KILL.name() + ":" + e.getKey();
            MonsterLootSummary s = summaries.computeIfAbsent(key, MonsterLootSummary::new);
            if (s.getSourceKind() == null)
            {
                s.setSourceKind(SourceKind.NPC_KILL);
            }
            s.setTotalKills(Math.max(s.getTotalKills(), e.getValue()));
        }
    }

    // ------------------------------------------------------------------ persistence

    private void markDirty()
    {
        dirty = true;
        changedSincePoll = true;
        scheduleFlush();
        fireChanged();
    }

    /** Polled by the plugin's onGameTick; true (once) after any kill / drop since the last poll. */
    public boolean pollChanged()
    {
        boolean c = changedSincePoll;
        changedSincePoll = false;
        return c;
    }

    /**
     * Cheap gate for the plugin's per-tick name-resolve pass: true only while some drop still has an
     * unresolved name. Maintained incrementally so a fully-resolved store costs nothing per tick.
     */
    public boolean hasUnresolvedItemNames()
    {
        return namelessCount > 0;
    }

    /** Item ids across all summaries that still have no resolved name (old / non-bestiary drops). */
    public java.util.Set<Integer> itemIdsMissingNames()
    {
        synchronized (lock)
        {
            java.util.Set<Integer> out = new java.util.HashSet<>();
            for (MonsterLootSummary s : summaries.values())
            {
                for (Map.Entry<Integer, ItemStat> e : s.getItemStats().entrySet())
                {
                    String n = e.getValue().getItemName();
                    if (n == null || n.isEmpty())
                    {
                        out.add(e.getKey());
                    }
                }
            }
            return out;
        }
    }

    /** Fill in a resolved item name (call from the client thread with an ItemComposition name). */
    public void applyItemName(int itemId, String name)
    {
        if (name == null || name.isEmpty())
        {
            return;
        }
        synchronized (lock)
        {
            for (MonsterLootSummary s : summaries.values())
            {
                ItemStat st = s.getItemStats().get(itemId);
                if (st != null && (st.getItemName() == null || st.getItemName().isEmpty()))
                {
                    st.setItemName(name);
                    if (namelessCount > 0)
                    {
                        namelessCount--;
                    }
                }
            }
        }
        changedSincePoll = true;
    }

    private void scheduleFlush()
    {
        if (executor == null)
        {
            return;
        }
        if (pendingFlush != null && !pendingFlush.isDone())
        {
            pendingFlush.cancel(false);
        }
        pendingFlush = executor.schedule(this::flush, FLUSH_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /** Force an immediate write (logout / shutdown). */
    public void flushNow()
    {
        synchronized (lock)
        {
            if (dirty)
            {
                writeToDisk();
            }
        }
    }

    private void flush()
    {
        synchronized (lock)
        {
            if (dirty)
            {
                writeToDisk();
            }
        }
    }

    private String storeKey()
    {
        return activeAccountHash == -1 ? "default" : Long.toUnsignedString(activeAccountHash);
    }

    private File storeFile()
    {
        return new File(lootDir, storeKey() + ".json.gz");
    }

    private void writeToDisk()
    {
        if (demoLoot)
        {
            return; // tour demo loot must never touch the player's file
        }
        try
        {
            if (!lootDir.exists() && !lootDir.mkdirs())
            {
                log.warn("Could not create loot store dir {}", lootDir);
                return;
            }
            LootFile envelope = new LootFile();
            envelope.schemaVersion = LootRecord.SCHEMA_VERSION;
            envelope.records = new ArrayList<>(records);
            envelope.killCounts = new HashMap<>(killCountBySource);
            envelope.imported = imported;

            File tmp = new File(lootDir, storeKey() + ".json.gz.tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp);
                 GZIPOutputStream gz = new GZIPOutputStream(fos);
                 Writer w = new OutputStreamWriter(gz, StandardCharsets.UTF_8))
            {
                gson.toJson(envelope, w);
            }
            try
            {
                Files.move(tmp.toPath(), storeFile().toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (Exception atomicFailed)
            {
                Files.move(tmp.toPath(), storeFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        }
        catch (Exception e)
        {
            log.warn("Failed to persist loot store", e);
        }
    }

    private void readFromDisk()
    {
        File f = storeFile();
        if (!f.exists())
        {
            return;
        }
        try (InputStream is = new FileInputStream(f);
             GZIPInputStream gz = new GZIPInputStream(is);
             BufferedReader r = new BufferedReader(new InputStreamReader(gz, StandardCharsets.UTF_8)))
        {
            LootFile env = gson.fromJson(r, LootFile.class);
            if (env == null)
            {
                return;
            }
            if (env.records != null)
            {
                records.addAll(env.records);
                long maxId = 0;
                for (LootRecord rec : env.records)
                {
                    maxId = Math.max(maxId, rec.getId());
                }
                nextId = maxId + 1;
            }
            if (env.killCounts != null)
            {
                killCountBySource.putAll(env.killCounts);
            }
            imported = env.imported;
        }
        catch (Exception e)
        {
            log.warn("Failed to read loot store {} - starting empty", f, e);
            records.clear();
            killCountBySource.clear();
            nextId = 1;
        }
    }

    private static class LootFile
    {
        int schemaVersion;
        boolean imported;
        List<LootRecord> records;
        Map<String, Integer> killCounts;
    }
}
