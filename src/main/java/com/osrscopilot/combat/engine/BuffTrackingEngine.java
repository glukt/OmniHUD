package com.osrscopilot.combat.engine;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;

/**
 * Tracks real-time active durations, ticks, and % uptime for Combat Stat Boosts,
 * Divine Potions, Raid Overloads/Salts, Combat Prayers, and Arceuus/Lunar Spells.
 */
@Slf4j
@Singleton
public class BuffTrackingEngine
{
    public enum BuffCategory
    {
        POTION_BOOST("Potions & Stats", new Color(72, 187, 120)),
        DIVINE_POTION("Divine Potions", new Color(56, 178, 172)),
        RAID_BUFF("Raid Salts & Overloads", new Color(237, 137, 54)),
        PRAYER_OFFENSIVE("Offensive Prayers", new Color(236, 201, 75)),
        PRAYER_DEFENSIVE("Defensive & Overheads", new Color(99, 179, 237)),
        SPELL_BUFF("Combat Spells & Thralls", new Color(159, 122, 234));

        private final String displayName;
        private final Color color;

        BuffCategory(String displayName, Color color)
        {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName()
        {
            return displayName;
        }

        public Color getColor()
        {
            return color;
        }
    }

    @Getter
    public static class TimeRange
    {
        private final int startTick;
        private int endTick;

        public TimeRange(int startTick)
        {
            this.startTick = startTick;
            this.endTick = -1;
        }

        public void close(int endTick)
        {
            this.endTick = endTick;
        }

        public boolean isOpen()
        {
            return endTick < 0;
        }

        public int getDurationTicks(int currentTick)
        {
            if (isOpen())
            {
                return Math.max(0, currentTick - startTick);
            }
            return Math.max(0, endTick - startTick);
        }
    }

    public static class BuffTracker
    {
        @Getter private final String id;
        @Getter private final String name;
        @Getter private final String iconSymbol;
        @Getter private final BuffCategory category;
        private final List<TimeRange> timeRanges = new ArrayList<>();
        private TimeRange currentRange = null;
        @Getter private int countApplied = 0;

        public BuffTracker(String id, String name, String iconSymbol, BuffCategory category)
        {
            this.id = id;
            this.name = name;
            this.iconSymbol = iconSymbol;
            this.category = category;
        }

        public synchronized void setActive(boolean active, int currentTick)
        {
            if (active)
            {
                if (currentRange == null)
                {
                    currentRange = new TimeRange(currentTick);
                    timeRanges.add(currentRange);
                    countApplied++;
                }
            }
            else
            {
                if (currentRange != null)
                {
                    currentRange.close(currentTick);
                    currentRange = null;
                }
            }
        }

        public synchronized boolean isActive()
        {
            return currentRange != null;
        }

        public synchronized int getTotalActiveTicks(int currentTick)
        {
            int total = 0;
            for (TimeRange range : timeRanges)
            {
                total += range.getDurationTicks(currentTick);
            }
            return total;
        }

        public synchronized List<TimeRange> getTimeRangesSnapshot()
        {
            return Collections.unmodifiableList(new ArrayList<>(timeRanges));
        }

        public synchronized BuffUptimeSnapshot snapshot(int currentTick, int encounterTotalTicks)
        {
            int activeTicks = getTotalActiveTicks(currentTick);
            double activeSeconds = activeTicks * 0.6;
            double uptimePercent = encounterTotalTicks > 0
                ? Math.min(100.0, (double) activeTicks / encounterTotalTicks * 100.0)
                : (activeTicks > 0 ? 100.0 : 0.0);

            return new BuffUptimeSnapshot(
                id,
                name,
                iconSymbol,
                category,
                activeTicks,
                activeSeconds,
                uptimePercent,
                countApplied,
                isActive(),
                getTimeRangesSnapshot()
            );
        }

        public synchronized void reset()
        {
            timeRanges.clear();
            currentRange = null;
            countApplied = 0;
        }

        /**
         * Start a fresh uptime history for a new encounter, discarding the previous fight's
         * ranges. A buff that is still on carries over as an open range from {@code startTick} so
         * its "on since before the fight" state isn't lost (fixes stale carried-over uptime %).
         */
        public synchronized void rebaseForNewEncounter(int startTick)
        {
            boolean wasActive = currentRange != null;
            timeRanges.clear();
            currentRange = null;
            countApplied = 0;
            if (wasActive)
            {
                currentRange = new TimeRange(startTick);
                timeRanges.add(currentRange);
                countApplied = 1;
            }
        }
    }

    public static class BuffUptimeSnapshot
    {
        private final String id;
        private final String name;
        private final String iconSymbol;
        private final BuffCategory category;
        private final int totalActiveTicks;
        private final double totalActiveSeconds;
        private final double uptimePercent;
        private final int countApplied;
        private final boolean currentlyActive;
        private final List<TimeRange> timeRanges;

        public BuffUptimeSnapshot(String id, String name, String iconSymbol, BuffCategory category,
                                  int totalActiveTicks, double totalActiveSeconds, double uptimePercent,
                                  int countApplied, boolean currentlyActive, List<TimeRange> timeRanges)
        {
            this.id = id;
            this.name = name;
            this.iconSymbol = iconSymbol;
            this.category = category;
            this.totalActiveTicks = totalActiveTicks;
            this.totalActiveSeconds = totalActiveSeconds;
            this.uptimePercent = uptimePercent;
            this.countApplied = countApplied;
            this.currentlyActive = currentlyActive;
            this.timeRanges = timeRanges;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getIconSymbol() { return iconSymbol; }
        public BuffCategory getCategory() { return category; }
        public int getTotalActiveTicks() { return totalActiveTicks; }
        public double getTotalActiveSeconds() { return totalActiveSeconds; }
        public double getUptimePercent() { return uptimePercent; }
        public int getCountApplied() { return countApplied; }
        public boolean isCurrentlyActive() { return currentlyActive; }
        public List<TimeRange> getTimeRanges() { return timeRanges; }
    }

    private final Client client;
    private final Map<String, BuffTracker> trackers = new LinkedHashMap<>();
    private boolean encounterActive = false;
    private int encounterStartTick = 0;

    @Inject
    public BuffTrackingEngine(Client client)
    {
        this.client = client;
        initializeDefinitions();
    }

    // ASCII category tags - the RuneScape bitmap font has no emoji glyphs, so the old icons
    // rendered as tofu boxes. "+" offensive, "#" defensive, "^" potion/boost, "~" spell/aura.
    private static final String TAG_OFF = "+";
    private static final String TAG_DEF = "#";
    private static final String TAG_POT = "^";
    private static final String TAG_SPELL = "~";

    private void initializeDefinitions()
    {
        // 1. Combat Prayers
        register("PRAYER_PIETY", "Piety", TAG_OFF, BuffCategory.PRAYER_OFFENSIVE);
        register("PRAYER_RIGOUR", "Rigour", TAG_OFF, BuffCategory.PRAYER_OFFENSIVE);
        register("PRAYER_AUGURY", "Augury", TAG_OFF, BuffCategory.PRAYER_OFFENSIVE);
        register("PRAYER_CHIVALRY", "Chivalry", TAG_OFF, BuffCategory.PRAYER_OFFENSIVE);
        register("PRAYER_ULTIMATE_STRENGTH", "Ultimate Strength", TAG_OFF, BuffCategory.PRAYER_OFFENSIVE);
        register("PRAYER_INCREDIBLE_REFLEXES", "Incredible Reflexes", TAG_OFF, BuffCategory.PRAYER_OFFENSIVE);
        register("PRAYER_EAGLE_EYE", "Eagle Eye", TAG_OFF, BuffCategory.PRAYER_OFFENSIVE);
        register("PRAYER_MYSTIC_MIGHT", "Mystic Might", TAG_OFF, BuffCategory.PRAYER_OFFENSIVE);
        register("PRAYER_MELEE", "Protect from Melee", TAG_DEF, BuffCategory.PRAYER_DEFENSIVE);
        register("PRAYER_MISSILES", "Protect from Missiles", TAG_DEF, BuffCategory.PRAYER_DEFENSIVE);
        register("PRAYER_MAGIC", "Protect from Magic", TAG_DEF, BuffCategory.PRAYER_DEFENSIVE);
        register("PRAYER_THICK_SKIN", "Thick Skin", TAG_DEF, BuffCategory.PRAYER_DEFENSIVE);
        register("PRAYER_ROCK_SKIN", "Rock Skin", TAG_DEF, BuffCategory.PRAYER_DEFENSIVE);
        register("PRAYER_STEEL_SKIN", "Steel Skin", TAG_DEF, BuffCategory.PRAYER_DEFENSIVE);

        // 2. Divine & Potion Boosts
        register("BOOST_STRENGTH", "Strength Boosted", TAG_POT, BuffCategory.POTION_BOOST);
        register("BOOST_ATTACK", "Attack Boosted", TAG_POT, BuffCategory.POTION_BOOST);
        register("BOOST_RANGED", "Ranged Boosted", TAG_POT, BuffCategory.POTION_BOOST);
        register("BOOST_MAGIC", "Magic Boosted", TAG_POT, BuffCategory.POTION_BOOST);
        register("DIVINE_POTION", "Divine Combat / Range", TAG_POT, BuffCategory.DIVINE_POTION);
        register("RAID_SALT_OVERLOAD", "Smelling Salts / Overload", TAG_POT, BuffCategory.RAID_BUFF);
        register("STAMINA", "Stamina Active", TAG_POT, BuffCategory.POTION_BOOST);

        // 3. Spells & Auras
        register("SPELL_THRALL", "Resurrect Thrall", TAG_SPELL, BuffCategory.SPELL_BUFF);
        register("SPELL_DEATH_CHARGE", "Death Charge", TAG_SPELL, BuffCategory.SPELL_BUFF);
        register("SPELL_VENGEANCE", "Vengeance Armed", TAG_SPELL, BuffCategory.SPELL_BUFF);
        register("SPELL_SHADOW_VEIL", "Shadow Veil", TAG_SPELL, BuffCategory.SPELL_BUFF);
        register("SPELL_WARD", "Ward of Arceuus", TAG_SPELL, BuffCategory.SPELL_BUFF);
    }

    private void register(String id, String name, String icon, BuffCategory category)
    {
        trackers.put(id, new BuffTracker(id, name, icon, category));
    }

    public synchronized void startEncounter(int startTick)
    {
        this.encounterActive = true;
        this.encounterStartTick = startTick;
        // Drop the previous fight's uptime history so a buff you're no longer running doesn't
        // still show a % in the new encounter.
        for (BuffTracker tracker : trackers.values())
        {
            tracker.rebaseForNewEncounter(startTick);
        }
    }

    private java.util.function.BiConsumer<String, Boolean> buffChangeSink;

    /** Fired (buffName, nowActive) whenever a tracked buff/prayer flips state. */
    public void setBuffChangeSink(java.util.function.BiConsumer<String, Boolean> sink)
    {
        this.buffChangeSink = sink;
    }

    public synchronized void stopEncounter(int endTick)
    {
        this.encounterActive = false;
        for (BuffTracker tracker : trackers.values())
        {
            if (tracker.isActive())
            {
                tracker.setActive(false, endTick);
            }
        }
    }

    public synchronized void reset()
    {
        this.encounterActive = false;
        this.encounterStartTick = 0;
        for (BuffTracker tracker : trackers.values())
        {
            tracker.reset();
        }
    }

    /** Display names of every buff / prayer / boost active right now - for the "engaged with" line. */
    public synchronized List<String> getActiveBuffNames()
    {
        List<String> names = new ArrayList<>();
        for (BuffTracker tracker : trackers.values())
        {
            if (tracker.isActive())
            {
                names.add(tracker.getName());
            }
        }
        return names;
    }

    public void onGameTick(GameTick event)
    {
        if (client == null || (client.getGameState() != null && client.getGameState() != GameState.LOGGED_IN)) return;
        int currentTick = client.getTickCount();

        // 1. Prayers
        setTrackerActive("PRAYER_PIETY", client.isPrayerActive(Prayer.PIETY), currentTick);
        setTrackerActive("PRAYER_RIGOUR", client.isPrayerActive(Prayer.RIGOUR), currentTick);
        setTrackerActive("PRAYER_AUGURY", client.isPrayerActive(Prayer.AUGURY), currentTick);
        setTrackerActive("PRAYER_CHIVALRY", client.isPrayerActive(Prayer.CHIVALRY), currentTick);
        setTrackerActive("PRAYER_ULTIMATE_STRENGTH", client.isPrayerActive(Prayer.ULTIMATE_STRENGTH), currentTick);
        setTrackerActive("PRAYER_INCREDIBLE_REFLEXES", client.isPrayerActive(Prayer.INCREDIBLE_REFLEXES), currentTick);
        setTrackerActive("PRAYER_EAGLE_EYE", client.isPrayerActive(Prayer.EAGLE_EYE), currentTick);
        setTrackerActive("PRAYER_MYSTIC_MIGHT", client.isPrayerActive(Prayer.MYSTIC_MIGHT), currentTick);
        setTrackerActive("PRAYER_MELEE", client.isPrayerActive(Prayer.PROTECT_FROM_MELEE), currentTick);
        setTrackerActive("PRAYER_MISSILES", client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES), currentTick);
        setTrackerActive("PRAYER_MAGIC", client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC), currentTick);
        setTrackerActive("PRAYER_THICK_SKIN", client.isPrayerActive(Prayer.THICK_SKIN), currentTick);
        setTrackerActive("PRAYER_ROCK_SKIN", client.isPrayerActive(Prayer.ROCK_SKIN), currentTick);
        setTrackerActive("PRAYER_STEEL_SKIN", client.isPrayerActive(Prayer.STEEL_SKIN), currentTick);

        // 2. Skill Boosts
        setTrackerActive("BOOST_STRENGTH", client.getBoostedSkillLevel(Skill.STRENGTH) > client.getRealSkillLevel(Skill.STRENGTH), currentTick);
        setTrackerActive("BOOST_ATTACK", client.getBoostedSkillLevel(Skill.ATTACK) > client.getRealSkillLevel(Skill.ATTACK), currentTick);
        setTrackerActive("BOOST_RANGED", client.getBoostedSkillLevel(Skill.RANGED) > client.getRealSkillLevel(Skill.RANGED), currentTick);
        setTrackerActive("BOOST_MAGIC", client.getBoostedSkillLevel(Skill.MAGIC) > client.getRealSkillLevel(Skill.MAGIC), currentTick);

        // 3. Stamina
        try
        {
            setTrackerActive("STAMINA", client.getVarbitValue(25) > 0, currentTick);
        }
        catch (RuntimeException e)
        {
            log.debug("Stamina varbit read failed", e);
        }

        // 4. Thralls (Arceuus Thrall active varbit 12411)
        try
        {
            setTrackerActive("SPELL_THRALL", client.getVarbitValue(12411) > 0, currentTick);
        }
        catch (RuntimeException e)
        {
            log.debug("Thrall varbit read failed", e);
        }
    }

    public void onVarbitChanged(VarbitChanged event)
    {
        if (client == null) return;
        int currentTick = client.getTickCount();

        try
        {
            // Divines (8993 = Divine Super Combat, 8995 = Divine Range, 8996 = Divine Bastion, 8997 = Divine Magic)
            boolean divineActive = client.getVarbitValue(8993) > 0 || client.getVarbitValue(8995) > 0
                || client.getVarbitValue(8996) > 0 || client.getVarbitValue(8997) > 0;
            setTrackerActive("DIVINE_POTION", divineActive, currentTick);

            // Raid Salts / Overloads (14353 = ToA Salts, 5418 = CoX Overload)
            boolean raidBuff = client.getVarbitValue(14353) > 0 || client.getVarbitValue(5418) > 0;
            setTrackerActive("RAID_SALT_OVERLOAD", raidBuff, currentTick);

            // Death Charge (12413)
            setTrackerActive("SPELL_DEATH_CHARGE", client.getVarbitValue(12413) > 0, currentTick);

            // Shadow Veil (12414)
            setTrackerActive("SPELL_SHADOW_VEIL", client.getVarbitValue(12414) > 0, currentTick);

            // Ward of Arceuus (12415)
            setTrackerActive("SPELL_WARD", client.getVarbitValue(12415) > 0, currentTick);

            // Vengeance (2450 / 2451)
            setTrackerActive("SPELL_VENGEANCE", client.getVarbitValue(2450) == 1, currentTick);
        }
        catch (RuntimeException e)
        {
            log.debug("Buff varbit scan failed", e);
        }
    }

    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.GAMEMESSAGE) return;
        String msg = event.getMessage();
        if (msg == null) return;

        int currentTick = client != null ? client.getTickCount() : 0;
        if (msg.contains("You resurrect a ") && msg.contains("thrall"))
        {
            setTrackerActive("SPELL_THRALL", true, currentTick);
        }
        else if (msg.contains("Taste Vengeance!"))
        {
            setTrackerActive("SPELL_VENGEANCE", false, currentTick);
        }
    }

    private synchronized void setTrackerActive(String id, boolean active, int tick)
    {
        BuffTracker tracker = trackers.get(id);
        if (tracker == null)
        {
            return;
        }
        boolean was = tracker.isActive();
        tracker.setActive(active, tick);
        if (was != active && buffChangeSink != null)
        {
            buffChangeSink.accept(tracker.getName(), active);
        }
    }

    public synchronized List<BuffUptimeSnapshot> getActiveAndRecentSnapshots(int currentTick, int totalEncounterTicks)
    {
        List<BuffUptimeSnapshot> list = new ArrayList<>();
        for (BuffTracker tracker : trackers.values())
        {
            BuffUptimeSnapshot snap = tracker.snapshot(currentTick, totalEncounterTicks);
            if (snap.getTotalActiveTicks() > 0 || snap.isCurrentlyActive())
            {
                list.add(snap);
            }
        }
        return list;
    }
}
