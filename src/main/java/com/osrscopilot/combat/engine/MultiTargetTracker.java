package com.osrscopilot.combat.engine;

import com.osrscopilot.OsrsCopilotConfig;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.NPC;

/**
 * Tracks individual NPC target instances in multi-combat encounters using npc.getIndex()
 * to assign ordinal tags like "Cave horror (1)", "Cave horror (2)".
 */
@Singleton
public class MultiTargetTracker
{
    public static class TrackedTargetInstance
    {
        private final int index;
        private final int npcId;
        private final String baseName;
        private final int ordinal;
        private final String formattedTag;
        private int lastActiveTick;
        private boolean despawned;

        public TrackedTargetInstance(int index, int npcId, String baseName, int ordinal, String formattedTag, int startTick)
        {
            this.index = index;
            this.npcId = npcId;
            this.baseName = baseName;
            this.ordinal = ordinal;
            this.formattedTag = formattedTag;
            this.lastActiveTick = startTick;
            this.despawned = false;
        }

        public int getIndex()
        {
            return index;
        }

        public int getNpcId()
        {
            return npcId;
        }

        public String getBaseName()
        {
            return baseName;
        }

        public int getOrdinal()
        {
            return ordinal;
        }

        public String getFormattedTag()
        {
            return formattedTag;
        }

        public int getLastActiveTick()
        {
            return lastActiveTick;
        }

        public void setLastActiveTick(int lastActiveTick)
        {
            this.lastActiveTick = lastActiveTick;
        }

        public boolean isDespawned()
        {
            return despawned;
        }

        public void setDespawned(boolean despawned)
        {
            this.despawned = despawned;
        }
    }

    private final OsrsCopilotConfig config;

    // Key: NPC Index -> Target Instance Data
    private final Map<Integer, TrackedTargetInstance> activeTargets = new LinkedHashMap<>();

    // Key: NPC Base Name -> Next available ordinal counter (1, 2, 3...)
    private final Map<String, Integer> nameOrdinalCounters = new HashMap<>();

    // Key: NPC index -> the ordinal that index was first assigned. Survives a prune/recreate so a
    // single long-lived NPC keeps ordinal 1 (tag stays "Iron dragon", not "Iron dragon (3)").
    private final Map<Integer, Integer> indexOrdinalMemo = new HashMap<>();

    // Highest tick trackTarget() has been called with. getTargetDisplayName() has no tick of its
    // own, so it uses this rather than 0 - passing 0 would instantly make an entry look stale.
    private int lastSeenTick = 0;

    @Inject
    public MultiTargetTracker(OsrsCopilotConfig config)
    {
        this.config = config;
    }

    public synchronized TrackedTargetInstance trackTarget(NPC npc, int currentTick, boolean multiTargetEnabled)
    {
        if (npc == null) return null;
        if (currentTick > lastSeenTick) lastSeenTick = currentTick;

        int index = npc.getIndex();
        TrackedTargetInstance existing = activeTargets.get(index);
        if (existing != null)
        {
            // Only ever advance the activity stamp - a stray call with an older/zero tick must
            // not make a live target look idle and get pruned out from under itself.
            if (currentTick > existing.getLastActiveTick())
            {
                existing.setLastActiveTick(currentTick);
            }
            return existing;
        }

        String baseName = npc.getName() != null ? npc.getName() : "NPC";
        Integer remembered = indexOrdinalMemo.get(index);
        int ordinal = remembered != null
            ? remembered
            : nameOrdinalCounters.compute(baseName, (k, v) -> v == null ? 1 : v + 1);
        indexOrdinalMemo.put(index, ordinal);

        // Only suffix from the 2nd concurrent instance on, so a lone Hill Giant is just
        // "Hill Giant", not "Hill Giant (1)".
        String formattedTag = (multiTargetEnabled && ordinal > 1)
            ? baseName + " (" + ordinal + ")"
            : baseName;

        TrackedTargetInstance target = new TrackedTargetInstance(
            index, npc.getId(), baseName, ordinal, formattedTag, Math.max(currentTick, lastSeenTick));
        activeTargets.put(index, target);
        return target;
    }

    public synchronized String getFormattedTargetName(NPC npc, int currentTick, boolean multiTargetEnabled)
    {
        if (npc == null) return "Unknown Target";
        TrackedTargetInstance instance = trackTarget(npc, currentTick, multiTargetEnabled);
        return instance != null ? instance.getFormattedTag() : (npc.getName() != null ? npc.getName() : "NPC");
    }

    public synchronized String getTargetDisplayName(NPC npc)
    {
        if (npc == null) return "Unknown Target";
        // Pure read for an already-tracked target - never mutate its activity stamp here.
        TrackedTargetInstance existing = activeTargets.get(npc.getIndex());
        if (existing != null)
        {
            return existing.getFormattedTag();
        }
        boolean enabled = config != null && config.combatMultiTargetTagging();
        return getFormattedTargetName(npc, lastSeenTick, enabled);
    }

    public synchronized void handleNpcDespawn(NPC npc)
    {
        if (npc == null) return;
        TrackedTargetInstance target = activeTargets.get(npc.getIndex());
        if (target != null)
        {
            target.setDespawned(true);
            activeTargets.remove(npc.getIndex());
        }
    }

    public synchronized int getActiveTargetCount()
    {
        return (int) activeTargets.values().stream().filter(t -> !t.isDespawned()).count();
    }

    /**
     * Drop tracked targets that haven't been active for {@code maxIdleTicks}. NPCs that leave render
     * distance never fire {@code NpcDespawned}, so without this a stale entry can keep
     * {@code getActiveTargetCount() > 1} forever and stop the next fight from ever finalizing.
     */
    public synchronized void pruneStale(int currentTick, int maxIdleTicks)
    {
        activeTargets.values().removeIf(
            t -> t.getLastActiveTick() > 0 && currentTick - t.getLastActiveTick() > maxIdleTicks);
    }

    public synchronized void reset()
    {
        activeTargets.clear();
        nameOrdinalCounters.clear();
        indexOrdinalMemo.clear();
    }
}
