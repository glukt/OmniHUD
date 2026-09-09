package com.osrscopilot.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.SlayerMaster;
import com.osrscopilot.data.model.SlayerTaskAssignment;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class SlayerTaskManager
{
    public static final String CONFIG_GROUP = "osrscopilot";
    public static final String KEY_MONSTER = "slayerMonster";
    public static final String KEY_INITIAL_AMOUNT = "slayerInitialAmount";
    public static final String KEY_AMOUNT_REMAINING = "slayerAmountRemaining";
    public static final String KEY_LOCATION = "slayerLocation";
    public static final String KEY_SLAYER_MASTER = "slayerMaster";
    public static final String KEY_LAST_UPDATED = "slayerLastUpdated";
    public static final String KEY_TASK_LOOT_GP = "slayerTaskLootGp";
    public static final String KEY_TASK_SUPPLY_GP = "slayerTaskSupplyGp";
    public static final String KEY_TASK_SLAYER_XP = "slayerTaskSlayerXp";
    public static final String KEY_TASK_SUBTYPE = "slayerTaskSubtype";

    /**
     * Slayer tasks that are an umbrella over several killable NPCs (the game lets you kill any of
     * them for task credit). Key = lowercase task name (matched against RuneLite's stored task
     * name); value = the members in the order the subtype picker should list them, most-common
     * first. The picker defaults to "Auto" and follows whatever you're actually killing.
     */
    public static final Map<String, List<String>> SLAYER_GROUP_MEMBERS = new HashMap<>();

    private static void group(String taskName, String... members)
    {
        SLAYER_GROUP_MEMBERS.put(taskName, Arrays.asList(members));
    }

    static
    {
        // --- multi-location groups -----------------------------------------------------------
        group("metal dragons",
            "Bronze dragon", "Iron dragon", "Steel dragon", "Mithril dragon", "Adamant dragon", "Rune dragon");
        group("lizards", "Desert Lizard", "Small Lizard", "Sulphur Lizard");
        group("fossil island wyverns",
            "Spitting Wyvern", "Taloned Wyvern", "Long-tailed Wyvern", "Ancient Wyvern");
        group("spiritual creatures", "Spiritual warrior", "Spiritual ranger", "Spiritual mage");
        group("tzhaar", "TzHaar-Ket", "TzHaar-Xil", "TzHaar-Mej", "TzHaar-Hur");
        group("revenants",
            "Revenant imp", "Revenant goblin", "Revenant pyrefiend", "Revenant hobgoblin", "Revenant cyclops",
            "Revenant hellhound", "Revenant demon", "Revenant ork", "Revenant dark beast",
            "Revenant knight", "Revenant dragon");
        group("dogs", "Guard dog", "Jackal", "Wild dog");
        group("trolls", "Mountain troll", "Ice troll", "River troll", "Troll general");
        group("crabs", "Rock Crab", "Sand Crab", "Ammonite Crab", "Swamp Crab");
        group("kalphite", "Kalphite Worker", "Kalphite Soldier", "Kalphite Guardian", "Kalphite Queen");
        group("kalphites", "Kalphite Worker", "Kalphite Soldier", "Kalphite Guardian", "Kalphite Queen");
        group("dagannoth", "Dagannoth", "Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme");

        // --- "a stronger variant / boss counts for the task" groups -------------------------
        // (the Catacombs of Kourend and Iorwerth Dungeon variants, and the slayer bosses)
        group("aberrant spectres", "Aberrant spectre", "Deviant spectre");
        group("bloodveld", "Bloodveld", "Mutated Bloodveld");
        group("nechryael", "Nechryael", "Greater Nechryael");
        group("abyssal demons", "Abyssal demon", "Greater abyssal demon", "Abyssal Sire");
        group("black demons", "Black demon", "Demonic gorilla");
        group("dark beasts", "Dark beast", "Night beast");
        group("hellhounds", "Hellhound", "Cerberus");
        group("smoke devils", "Smoke devil", "Thermonuclear smoke devil");
        group("cave kraken", "Cave kraken", "Kraken");
        group("kraken", "Cave kraken", "Kraken");
        group("kurask", "Kurask", "King kurask");
        group("jellies", "Jelly", "Warped Jelly");
        group("gargoyles", "Gargoyle", "Marble gargoyle");
        group("basilisks", "Basilisk", "Monstrous Basilisk", "Basilisk Knight");
        group("hydras", "Hydra", "Alchemical Hydra");
    }

    /** True if the given task name is one of the umbrella tasks in {@link #SLAYER_GROUP_MEMBERS}. */
    public static boolean isGroupTaskName(String taskName)
    {
        return taskName != null && SLAYER_GROUP_MEMBERS.containsKey(taskName.toLowerCase(Locale.ROOT).trim());
    }

    // 1. Assignment Patterns
    // "You have been assigned to kill Hellhounds (x150)." / "You are assigned to kill Hellhounds (x150)."
    private static final Pattern PATTERN_ASSIGN_X = Pattern.compile(
        "(?i)You (?:have been|are) assigned to kill\\s+(.+?)\\s+\\(x(\\d+)\\)"
    );

    // "Your new task is to kill 165 Gargoyles." / "Your new task is to kill 165 Gargoyles"
    private static final Pattern PATTERN_ASSIGN_NEW = Pattern.compile(
        "(?i)Your new task is to kill\\s+(\\d+)\\s+(.+?)(?:\\.|;|$)"
    );

    // "You are assigned to kill 140 Bloodvelds."
    private static final Pattern PATTERN_ASSIGN_COUNT_FIRST = Pattern.compile(
        "(?i)You are assigned to kill\\s+(\\d+)\\s+(.+?)(?:\\.|;|$)"
    );

    // Konar: "You are to bring balance to 140 Aberrant spectres in Catacombs of Kourend." / "in the Catacombs of Kourend"
    private static final Pattern PATTERN_KONAR_ASSIGN = Pattern.compile(
        "(?i)You are to bring balance to\\s+(\\d+)\\s+(.+?)\\s+in\\s+(?:the\\s+)?(.+?)(?:\\.|;|$)"
    );

    // "Your task is to kill 130 Cave horrors in the Witchaven Dungeon."
    private static final Pattern PATTERN_TASK_WITH_LOCATION = Pattern.compile(
        "(?i)Your task is to kill\\s+(\\d+)\\s+(.+?)\\s+in\\s+(?:the\\s+)?(.+?)(?:\\.|;|$)"
    );

    // 2. Check / Status / Progress Patterns
    // "You're assigned to kill Hellhounds; only 45 more to go." / "You're assigned to kill Hellhounds in Karuulm; only 45 more to go."
    private static final Pattern PATTERN_CHECK_ASSIGNED = Pattern.compile(
        "(?i)You're assigned to kill\\s+(.+?)(?:\\s+in\\s+(?:the\\s+)?(.+?))?;\\s+only\\s+(\\d+)\\s+more to go"
    );

    // "Your task is to kill Hellhounds; only 45 more to go." / "Your task is to kill Hellhounds in Karuulm; only 45 more to go."
    private static final Pattern PATTERN_CHECK_TASK = Pattern.compile(
        "(?i)Your task is to kill\\s+(.+?)(?:\\s+in\\s+(?:the\\s+)?(.+?))?;\\s+only\\s+(\\d+)\\s+more to go"
    );

    // Konar check: "You are to bring balance to Aberrant spectres in Catacombs of Kourend; only 40 more to go."
    private static final Pattern PATTERN_KONAR_CHECK = Pattern.compile(
        "(?i)You are to bring balance to\\s+(.+?)\\s+in\\s+(?:the\\s+)?(.+?);\\s+only\\s+(\\d+)\\s+more to go"
    );

    // "You need to kill 87 Gargoyles to complete your current Slayer assignment."
    private static final Pattern PATTERN_NEED_TO_KILL_COUNT = Pattern.compile(
        "(?i)You need to kill\\s+(\\d+)\\s+(.+?)\\s+to complete your (?:current )?Slayer assignment"
    );

    // "You need to kill Gargoyles to complete your current Slayer assignment."
    private static final Pattern PATTERN_NEED_TO_KILL_NAME = Pattern.compile(
        "(?i)You need to kill\\s+(.+?)\\s+to complete your (?:current )?Slayer assignment"
    );

    // "You need 32 more kills to complete your task." / "You need 1 more kill to complete your task."
    private static final Pattern PATTERN_NEED_KILLS = Pattern.compile(
        "(?i)You need\\s+(\\d+)\\s+more kills?\\s+to complete your task"
    );

    // "You still need to kill 25 more Dagannoth." / "You still need to kill 25 Dagannoth."
    private static final Pattern PATTERN_STILL_NEED = Pattern.compile(
        "(?i)You still need to kill\\s+(\\d+)\\s+(?:more\\s+)?(.+?)(?:\\.|;|$)"
    );

    // 3. Task Completion Patterns
    private static final Pattern PATTERN_COMPLETED = Pattern.compile(
        "(?i)You(?:'ve| have) completed (?:your|current) (?:Slayer )?task|You have completed your assignment|You completed your task"
    );

    // 4. Task Reset / Cancel Patterns
    private static final Pattern PATTERN_RESET = Pattern.compile(
        "(?i)Your (?:Slayer )?task has been reset|You no longer have a Slayer assignment|Your assignment has been cancelled"
    );

    @Getter
    private String monsterName = null;

    @Getter
    private int initialAmount = 0;

    @Getter
    private int amountRemaining = 0;

    @Getter
    private String location = null;

    @Getter
    private String slayerMaster = null;

    // For an umbrella task (e.g. "Metal dragons"): which member the user pinned in the picker, so
    // the tab shows that NPC's spawns / drops. null = "Auto" - follow whatever the player is
    // actually killing (see autoDetectedMember).
    @Getter
    private String taskSubtype = null;

    // In "Auto" mode, the last group member seen dying near the player this task. Not persisted -
    // it re-detects from kills. Used only when taskSubtype is null.
    @Getter
    private transient String autoDetectedMember = null;

    // --- guided-tour demo task ----------------------------------------------------------------
    // While a demo task is installed (the Slayer tour) nothing is persisted and every live-sync
    // path (chat, varps, the RuneLite Slayer plugin) is ignored so it can't be overwritten. The
    // real task is stashed here and restored by clearDemoTask().
    private boolean demoActive = false;
    private String demoSavedMonsterName;
    private int demoSavedInitialAmount;
    private int demoSavedAmountRemaining;
    private String demoSavedLocation;
    private String demoSavedSlayerMaster;
    private String demoSavedSubtype;
    private String demoSavedAutoDetectedMember;

    @Getter
    private int slayerPoints = 0;

    @Getter
    private int taskStreak = 0;

    @Getter
    private int wildernessStreak = 0;

    @Getter
    private int taskSlayerXpEarned = 0;

    @Getter
    private long taskLootValueGp = 0L;

    // Supply cost (food / potions / runes / ammo) spent during this task, fed from the combat
    // ConsumableAuditor. The Slayer tab is the single owner of net-gain = loot - supplies.
    @Getter
    private long taskSupplyCostGp = 0L;

    // Consecutive updateFromClient() calls that saw SLAYER_TASK_SIZE == 0 (debounces the
    // transient 0 that the varp reports for a tick or two right after login).
    private int zeroSizeStreak = 0;

    @Getter
    private long lastUpdatedTime = 0;

    @Getter
    private final SlayerRewardCatalog rewardCatalog;

    private int lastSlayerXp = -1;

    // Cached from the last updateFromClient() - used to grey out masters the player can't use yet.
    @Getter
    private int playerCombatLevel = 0;
    @Getter
    private int playerSlayerLevel = 0;

    public static final String KEY_BLOCKED_TASKS = "slayerBlockedTasks";
    // The player's own block list, kept by us (the game exposes no readable block list). Lowercase
    // task names.
    private final java.util.Set<String> blockedTasks = new java.util.HashSet<>();

    /**
     * Tasks that a Slayer master will only assign once a points reward is unlocked. Key = lowercase
     * task name (as stored), value = the {@link SlayerRewardCatalog} reward name that gates it.
     */
    public static final Map<String, String> SLAYER_UNLOCK_GATES = new HashMap<>();

    static
    {
        // reward names must match SlayerRewardCatalog exactly (that's how isRewardUnlocked resolves).
        SLAYER_UNLOCK_GATES.put("red dragons", "Seeing Red");
        SLAYER_UNLOCK_GATES.put("aviansies", "Watch the Birdie");
        SLAYER_UNLOCK_GATES.put("tzhaar", "Hot Stuff");
        SLAYER_UNLOCK_GATES.put("lizardmen", "Reptile Got Ripped");
        SLAYER_UNLOCK_GATES.put("vampyres", "Actual Vampyre Slayer");
        SLAYER_UNLOCK_GATES.put("feral vampyres", "Actual Vampyre Slayer");
        SLAYER_UNLOCK_GATES.put("basilisks", "Basilocked");
        SLAYER_UNLOCK_GATES.put("warped creatures", "Warped Reality");
        // Slayer-boss tasks all sit behind "Like a Boss".
        for (String boss : new String[]{"boss", "cerberus", "abyssal sire", "kraken",
            "thermonuclear smoke devil", "alchemical hydra", "the grotesque guardians"})
        {
            SLAYER_UNLOCK_GATES.putIfAbsent(boss, "Like a Boss");
        }
    }

    @Setter
    private MonsterDatabase monsterDatabase;

    private final ConfigManager configManager;
    private final List<Runnable> changeListeners = new ArrayList<>();
    private final Map<String, List<SlayerTaskAssignment>> masterAssignments = new HashMap<>();

    @Inject
    public SlayerTaskManager(ConfigManager configManager, MonsterDatabase monsterDatabase, SlayerRewardCatalog rewardCatalog)
    {
        this.configManager = configManager;
        this.monsterDatabase = monsterDatabase;
        this.rewardCatalog = rewardCatalog != null ? rewardCatalog : new SlayerRewardCatalog();
        loadAssignments();
        loadBlockedTasks();
    }

    public SlayerTaskManager(ConfigManager configManager, MonsterDatabase monsterDatabase)
    {
        this(configManager, monsterDatabase, new SlayerRewardCatalog());
    }

    public SlayerTaskManager(ConfigManager configManager)
    {
        this(configManager, null, new SlayerRewardCatalog());
    }

    public void loadAssignments()
    {
        masterAssignments.clear();
        try (InputStream in = SlayerTaskManager.class.getResourceAsStream("/com/osrscopilot/slayer_assignments.json"))
        {
            if (in == null)
            {
                log.warn("slayer_assignments.json not found on classpath");
                return;
            }
            JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet())
            {
                String master = entry.getKey();
                JsonArray arr = entry.getValue().getAsJsonArray();
                List<SlayerTaskAssignment> list = new ArrayList<>();
                for (JsonElement el : arr)
                {
                    JsonObject obj = el.getAsJsonObject();
                    list.add(SlayerTaskAssignment.builder()
                        .monster(obj.get("monster").getAsString())
                        .minAmount(obj.has("minAmount") ? obj.get("minAmount").getAsInt() : 0)
                        .maxAmount(obj.has("maxAmount") ? obj.get("maxAmount").getAsInt() : 0)
                        .extended(obj.has("extended") && !obj.get("extended").isJsonNull() ? obj.get("extended").getAsString() : null)
                        .weight(obj.has("weight") ? obj.get("weight").getAsInt() : 0)
                        .requirement(obj.has("requirement") && !obj.get("requirement").isJsonNull() ? obj.get("requirement").getAsString() : null)
                        .alternatives(obj.has("alternatives") && !obj.get("alternatives").isJsonNull() ? obj.get("alternatives").getAsString() : null)
                        .locations(obj.has("locations") && !obj.get("locations").isJsonNull() ? obj.get("locations").getAsString() : null)
                        .build()
                    );
                }
                masterAssignments.put(master.toLowerCase(Locale.ROOT), list);
            }
            log.debug("Loaded {} slayer master assignment lists", masterAssignments.size());
        }
        catch (Exception e)
        {
            log.error("Failed to load slayer_assignments.json", e);
        }
    }

    public List<SlayerTaskAssignment> getAssignmentsForMaster(String masterName)
    {
        if (masterName == null)
        {
            return Collections.emptyList();
        }
        String key = masterName.toLowerCase(Locale.ROOT).trim();
        List<SlayerTaskAssignment> list = masterAssignments.get(key);
        if (list != null)
        {
            return list;
        }
        for (Map.Entry<String, List<SlayerTaskAssignment>> entry : masterAssignments.entrySet())
        {
            if (entry.getKey().contains(key) || key.contains(entry.getKey()))
            {
                return entry.getValue();
            }
        }
        return Collections.emptyList();
    }

    public boolean hasActiveTask()
    {
        return monsterName != null && !monsterName.trim().isEmpty() && amountRemaining > 0;
    }

    public double getProgressPercentage()
    {
        if (initialAmount <= 0)
        {
            return 0.0;
        }
        int completed = Math.max(0, initialAmount - amountRemaining);
        return Math.min(100.0, Math.max(0.0, (completed * 100.0) / initialAmount));
    }

    public int getKillsCompleted()
    {
        if (initialAmount <= 0)
        {
            return 0;
        }
        return Math.max(0, initialAmount - amountRemaining);
    }

    /**
     * Resolves the active Slayer Monster object from the MonsterDatabase.
     */
    public Monster getActiveMonster()
    {
        if (monsterDatabase == null || monsterName == null || monsterName.trim().isEmpty())
        {
            return null;
        }
        return findMonsterForTask(monsterName, monsterDatabase);
    }

    public void onChatMessage(ChatMessage event)
    {
        if (event == null || event.getMessage() == null)
        {
            return;
        }
        parseChatMessage(event.getMessage());
    }

    public synchronized boolean parseChatMessage(String rawMessage)
    {
        if (rawMessage == null || rawMessage.trim().isEmpty() || demoActive)
        {
            return false;
        }

        String message = Text.removeTags(rawMessage).trim();
        boolean stateChanged = false;

        // 0. Slayer Master detection from a dialogue header ("Duradel:", "Konar:", "Steve:", "Aya:").
        // Match on the speaker name before the colon via SlayerMaster.findByName so the compound
        // display names ("Nieve / Steve", "Turael / Aya") resolve from either alias without needing
        // the stock Slayer plugin. Only look at a colon near the start so a mid-sentence ":" (a
        // time, a ratio) can't be read as a speaker.
        int headerColon = message.indexOf(':');
        if (headerColon > 0 && headerColon <= 24)
        {
            SlayerMaster spoken = SlayerMaster.findByName(message.substring(0, headerColon).trim());
            if (spoken != null && (this.slayerMaster == null || !this.slayerMaster.equalsIgnoreCase(spoken.getName())))
            {
                this.slayerMaster = spoken.getName();
                stateChanged = true;
            }
        }

        // 1. Check Task Completion
        Matcher compMatcher = PATTERN_COMPLETED.matcher(message);

        if (compMatcher.find())
        {
            log.debug("Slayer task completed: {}", monsterName);
            this.amountRemaining = 0;
            this.lastUpdatedTime = System.currentTimeMillis();
            saveToConfig();
            notifyListeners();
            return true;
        }

        // 2. Check Task Reset / Cancellation
        Matcher resetMatcher = PATTERN_RESET.matcher(message);
        if (resetMatcher.find())
        {
            log.debug("Slayer task reset/cancelled: {}", monsterName);
            clearTask();
            return true;
        }

        // 3. Konar Assignment: "You are to bring balance to <count> <monster> in <location>."
        Matcher konarAssignMatcher = PATTERN_KONAR_ASSIGN.matcher(message);
        if (konarAssignMatcher.find())
        {
            int count = Integer.parseInt(konarAssignMatcher.group(1));
            String mob = cleanMonsterName(konarAssignMatcher.group(2));
            String loc = cleanLocationName(konarAssignMatcher.group(3));
            setTaskDetails(mob, count, count, loc, "Konar quo Maten");
            return true;
        }

        // 4. Konar Check: "You are to bring balance to <monster> in <location>; only <count> more to go."
        Matcher konarCheckMatcher = PATTERN_KONAR_CHECK.matcher(message);
        if (konarCheckMatcher.find())
        {
            String mob = cleanMonsterName(konarCheckMatcher.group(1));
            String loc = cleanLocationName(konarCheckMatcher.group(2));
            int remaining = Integer.parseInt(konarCheckMatcher.group(3));
            int init = (this.initialAmount > 0 && mob.equalsIgnoreCase(this.monsterName))
                ? Math.max(this.initialAmount, remaining)
                : remaining;
            setTaskDetails(mob, init, remaining, loc, "Konar quo Maten");
            return true;
        }

        // 5. Assignment with location: "Your task is to kill <count> <monster> in <location>."
        Matcher taskLocMatcher = PATTERN_TASK_WITH_LOCATION.matcher(message);
        if (taskLocMatcher.find())
        {
            int count = Integer.parseInt(taskLocMatcher.group(1));
            String mob = cleanMonsterName(taskLocMatcher.group(2));
            String loc = cleanLocationName(taskLocMatcher.group(3));
            setTaskDetails(mob, count, count, loc, this.slayerMaster);
            return true;
        }

        // 6. Assignment (x<count>): "You have been assigned to kill <monster> (x<count>)."
        Matcher assignXMatcher = PATTERN_ASSIGN_X.matcher(message);
        if (assignXMatcher.find())
        {
            String mob = cleanMonsterName(assignXMatcher.group(1));
            int count = Integer.parseInt(assignXMatcher.group(2));
            setTaskDetails(mob, count, count, null, this.slayerMaster);
            return true;
        }

        // 7. Assignment: "Your new task is to kill <count> <monster>."
        Matcher assignNewMatcher = PATTERN_ASSIGN_NEW.matcher(message);
        if (assignNewMatcher.find())
        {
            int count = Integer.parseInt(assignNewMatcher.group(1));
            String mob = cleanMonsterName(assignNewMatcher.group(2));
            setTaskDetails(mob, count, count, null, this.slayerMaster);
            return true;
        }

        // 8. Assignment: "You are assigned to kill <count> <monster>."
        Matcher assignCountFirstMatcher = PATTERN_ASSIGN_COUNT_FIRST.matcher(message);
        if (assignCountFirstMatcher.find())
        {
            int count = Integer.parseInt(assignCountFirstMatcher.group(1));
            String mob = cleanMonsterName(assignCountFirstMatcher.group(2));
            setTaskDetails(mob, count, count, null, this.slayerMaster);
            return true;
        }

        // 9. Check: "You're assigned to kill <monster>; only <count> more to go."
        Matcher checkAssignedMatcher = PATTERN_CHECK_ASSIGNED.matcher(message);
        if (checkAssignedMatcher.find())
        {
            String mob = cleanMonsterName(checkAssignedMatcher.group(1));
            String loc = checkAssignedMatcher.group(2) != null ? cleanLocationName(checkAssignedMatcher.group(2)) : this.location;
            int remaining = Integer.parseInt(checkAssignedMatcher.group(3));
            int init = (this.initialAmount > 0 && mob.equalsIgnoreCase(this.monsterName))
                ? Math.max(this.initialAmount, remaining)
                : remaining;
            setTaskDetails(mob, init, remaining, loc, this.slayerMaster);
            return true;
        }

        // 10. Check: "Your task is to kill <monster>; only <count> more to go."
        Matcher checkTaskMatcher = PATTERN_CHECK_TASK.matcher(message);
        if (checkTaskMatcher.find())
        {
            String mob = cleanMonsterName(checkTaskMatcher.group(1));
            String loc = checkTaskMatcher.group(2) != null ? cleanLocationName(checkTaskMatcher.group(2)) : this.location;
            int remaining = Integer.parseInt(checkTaskMatcher.group(3));
            int init = (this.initialAmount > 0 && mob.equalsIgnoreCase(this.monsterName))
                ? Math.max(this.initialAmount, remaining)
                : remaining;
            setTaskDetails(mob, init, remaining, loc, this.slayerMaster);
            return true;
        }

        // 11. "You need to kill <count> <monster> to complete your current Slayer assignment."
        Matcher needCountMatcher = PATTERN_NEED_TO_KILL_COUNT.matcher(message);
        if (needCountMatcher.find())
        {
            int count = Integer.parseInt(needCountMatcher.group(1));
            String mob = cleanMonsterName(needCountMatcher.group(2));
            int init = (this.initialAmount > 0 && mob.equalsIgnoreCase(this.monsterName))
                ? Math.max(this.initialAmount, count)
                : count;
            setTaskDetails(mob, init, count, this.location, this.slayerMaster);
            return true;
        }

        // 12. "You need to kill <monster> to complete your current Slayer assignment."
        Matcher needNameMatcher = PATTERN_NEED_TO_KILL_NAME.matcher(message);
        if (needNameMatcher.find())
        {
            String mob = cleanMonsterName(needNameMatcher.group(1));
            if (this.monsterName == null || !this.monsterName.equalsIgnoreCase(mob))
            {
                setTaskDetails(mob, this.initialAmount > 0 ? this.initialAmount : 1, this.amountRemaining > 0 ? this.amountRemaining : 1, this.location, this.slayerMaster);
                return true;
            }
        }

        // 13. "You still need to kill <count> [more] <monster>."
        Matcher stillNeedMatcher = PATTERN_STILL_NEED.matcher(message);
        if (stillNeedMatcher.find())
        {
            int remaining = Integer.parseInt(stillNeedMatcher.group(1));
            String mob = cleanMonsterName(stillNeedMatcher.group(2));
            int init = (this.initialAmount > 0 && mob.equalsIgnoreCase(this.monsterName))
                ? Math.max(this.initialAmount, remaining)
                : remaining;
            setTaskDetails(mob, init, remaining, this.location, this.slayerMaster);
            return true;
        }

        // 14. "You need <count> more kills to complete your task."
        Matcher needKillsMatcher = PATTERN_NEED_KILLS.matcher(message);
        if (needKillsMatcher.find())
        {
            int remaining = Integer.parseInt(needKillsMatcher.group(1));
            this.amountRemaining = remaining;
            if (this.initialAmount < remaining)
            {
                this.initialAmount = remaining;
            }
            this.lastUpdatedTime = System.currentTimeMillis();
            saveToConfig();
            notifyListeners();
            return true;
        }

        if (stateChanged)
        {
            saveToConfig();
            notifyListeners();
        }

        return stateChanged;
    }


    public synchronized void setTaskDetails(String monster, int initial, int remaining, String loc, String master)
    {
        if (demoActive)
        {
            return;
        }
        // Only a genuinely new assignment resets the loot / XP / supply totals — a progress or
        // "check task" message reuses the same monster and must keep the running tally.
        boolean nameChanged = this.monsterName == null
            || monster == null
            || !this.monsterName.equalsIgnoreCase(monster);
        // Same monster can be re-assigned (block/prefer lists narrowing the pool, Turael-skip).
        // A fresh full assignment has the count at its max AND higher than what we last held
        // (kills only ever decrease it); a "check task" mid-task has remaining < initial.
        boolean fullReassign = initial > 0 && initial == remaining && remaining > this.amountRemaining;
        boolean newAssignment = nameChanged || fullReassign;

        this.monsterName = monster;
        this.initialAmount = initial;
        this.amountRemaining = remaining;
        this.location = (loc != null && !loc.trim().isEmpty()) ? loc.trim() : null;

        // Only accept a master string that resolves to one of the 9 real masters (canonicalised).
        // On a genuinely new assignment with no resolvable master, clear it rather than carry a
        // stale one from a previous task.
        com.osrscopilot.data.model.SlayerMaster m =
            com.osrscopilot.data.model.SlayerMaster.findByName(master);
        if (m != null)
        {
            this.slayerMaster = m.getName();
        }
        else if (newAssignment)
        {
            this.slayerMaster = null;
        }

        this.lastUpdatedTime = System.currentTimeMillis();
        if (newAssignment)
        {
            resetTaskTotals();
            this.taskSubtype = null; // fresh task -> forget the old umbrella subtype pick
            this.autoDetectedMember = null;
        }

        log.debug("Updated Slayer task: monster='{}', initial={}, remaining={}, location='{}', master='{}'",
            monsterName, initialAmount, amountRemaining, location, slayerMaster);

        saveToConfig();
        notifyListeners();
    }

    /** The task title as shown ("Metal dragons"). */
    public synchronized String getTaskName()
    {
        return monsterName;
    }

    /**
     * The specific NPC to resolve for spawns/drops. For an umbrella task: the pinned subtype if
     * set, else the member auto-detected from the player's kills, else the first member.
     */
    public synchronized String getEffectiveMonsterName()
    {
        if (isGroupTaskName(monsterName))
        {
            List<String> members = SLAYER_GROUP_MEMBERS.get(monsterName.toLowerCase(Locale.ROOT).trim());
            if (taskSubtype != null && members.contains(taskSubtype))
            {
                return taskSubtype;
            }
            if (autoDetectedMember != null && members.contains(autoDetectedMember))
            {
                return autoDetectedMember;
            }
            return members.get(0);
        }
        return monsterName;
    }

    /** True while the subtype picker is on "Auto" (no member pinned). */
    public synchronized boolean isAutoSubtype()
    {
        return taskSubtype == null;
    }

    /**
     * A group member of the current umbrella task just died near the player - in Auto mode, follow
     * it so the tab shows that dragon's spawns / drops. Matched case-insensitively; no-op otherwise.
     */
    public synchronized void noteKilledNpc(String npcName)
    {
        if (npcName == null || !isGroupTaskName(monsterName))
        {
            return;
        }
        String trimmed = npcName.trim();
        for (String member : SLAYER_GROUP_MEMBERS.get(monsterName.toLowerCase(Locale.ROOT).trim()))
        {
            if (member.equalsIgnoreCase(trimmed))
            {
                if (!member.equals(autoDetectedMember))
                {
                    autoDetectedMember = member;
                    if (taskSubtype == null)
                    {
                        notifyListeners();
                    }
                }
                return;
            }
        }
    }

    /** Sentence-case a task name for display ("metal dragons" -&gt; "Metal dragons"). */
    public static String displayTaskName(String name)
    {
        if (name == null || name.isEmpty())
        {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Members of the current umbrella task, or empty if this isn't a group task. */
    public synchronized List<String> getGroupMembers()
    {
        return isGroupTaskName(monsterName)
            ? SLAYER_GROUP_MEMBERS.get(monsterName.toLowerCase(Locale.ROOT).trim())
            : Collections.emptyList();
    }

    public synchronized void setTaskSubtype(String subtype)
    {
        // null / blank / anything starting "Auto" -> Auto mode (no pin, follow kills).
        String s = subtype != null ? subtype.trim() : "";
        String pinned = (s.isEmpty() || s.toLowerCase(Locale.ROOT).startsWith("auto")) ? null : s;
        if (java.util.Objects.equals(pinned, this.taskSubtype))
        {
            return;
        }
        this.taskSubtype = pinned;
        saveToConfig();
        notifyListeners();
    }

    /**
     * Manually reduce the remaining-kill count. In normal play the count is driven by the game's
     * own VarPlayer 394 plus the "N more to go" chat lines; this is the programmatic entry point
     * (used by tests and reserved for a future manual-adjust control).
     */
    public synchronized void decrementKill()
    {
        decrementKill(1);
    }

    public synchronized void decrementKill(int count)
    {
        if (demoActive)
        {
            return;
        }
        if (this.amountRemaining > 0)
        {
            this.amountRemaining = Math.max(0, this.amountRemaining - count);
            this.lastUpdatedTime = System.currentTimeMillis();
            saveToConfig();
            notifyListeners();
        }
    }

    /**
     * Install a fake, non-persisted Slayer task for the guided Slayer tour. The real task is
     * stashed and restored by {@link #clearDemoTask()}. Umbrella task names (e.g. "Metal dragons")
     * light up the subtype picker just like a real one.
     */
    public synchronized void setDemoTask(String taskName, int initial, int remaining, String loc, String master)
    {
        if (!demoActive)
        {
            demoSavedMonsterName = monsterName;
            demoSavedInitialAmount = initialAmount;
            demoSavedAmountRemaining = amountRemaining;
            demoSavedLocation = location;
            demoSavedSlayerMaster = slayerMaster;
            demoSavedSubtype = taskSubtype;
            demoSavedAutoDetectedMember = autoDetectedMember;
            demoActive = true;
        }
        this.monsterName = taskName;
        this.amountRemaining = Math.max(0, remaining);
        this.initialAmount = Math.max(initial, this.amountRemaining);
        this.location = (loc != null && !loc.trim().isEmpty()) ? loc.trim() : null;
        com.osrscopilot.data.model.SlayerMaster m =
            com.osrscopilot.data.model.SlayerMaster.findByName(master);
        this.slayerMaster = m != null ? m.getName() : ((master != null && !master.trim().isEmpty()) ? master.trim() : null);
        this.taskSubtype = null;
        this.autoDetectedMember = null;
        this.lastUpdatedTime = System.currentTimeMillis();
        notifyListeners();
    }

    /** Remove the demo task and restore whatever real task was there before {@link #setDemoTask}. */
    public synchronized void clearDemoTask()
    {
        if (!demoActive)
        {
            return;
        }
        demoActive = false;
        this.monsterName = demoSavedMonsterName;
        this.initialAmount = demoSavedInitialAmount;
        this.amountRemaining = demoSavedAmountRemaining;
        this.location = demoSavedLocation;
        this.slayerMaster = demoSavedSlayerMaster;
        this.taskSubtype = demoSavedSubtype;
        this.autoDetectedMember = demoSavedAutoDetectedMember;
        this.lastUpdatedTime = System.currentTimeMillis();
        notifyListeners();
    }

    /** True while a guided-tour demo task is installed. */
    public synchronized boolean isDemoTask()
    {
        return demoActive;
    }

    public synchronized void clearTask()
    {
        if (demoActive)
        {
            return;
        }
        this.monsterName = null;
        this.initialAmount = 0;
        this.amountRemaining = 0;
        this.location = null;
        this.slayerMaster = null;
        this.lastUpdatedTime = System.currentTimeMillis();
        resetTaskTotals();

        saveToConfig();
        notifyListeners();
    }

    private void resetTaskTotals()
    {
        this.taskLootValueGp = 0L;
        this.taskSupplyCostGp = 0L;
        this.taskSlayerXpEarned = 0;
    }

    private static long parseLongOr(String s, long fallback)
    {
        try
        {
            return (s != null && !s.trim().isEmpty()) ? Long.parseLong(s.trim()) : fallback;
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    public synchronized void loadFromConfig()
    {
        if (configManager == null || demoActive)
        {
            return;
        }

        try
        {
            String monster = configManager.getConfiguration(CONFIG_GROUP, KEY_MONSTER);
            String initialStr = configManager.getConfiguration(CONFIG_GROUP, KEY_INITIAL_AMOUNT);
            String remainingStr = configManager.getConfiguration(CONFIG_GROUP, KEY_AMOUNT_REMAINING);
            String loc = configManager.getConfiguration(CONFIG_GROUP, KEY_LOCATION);
            String master = configManager.getConfiguration(CONFIG_GROUP, KEY_SLAYER_MASTER);
            String updatedStr = configManager.getConfiguration(CONFIG_GROUP, KEY_LAST_UPDATED);

            Integer initial = (initialStr != null && !initialStr.trim().isEmpty()) ? Integer.parseInt(initialStr.trim()) : null;
            Integer remaining = (remainingStr != null && !remainingStr.trim().isEmpty()) ? Integer.parseInt(remainingStr.trim()) : null;
            Long updated = (updatedStr != null && !updatedStr.trim().isEmpty()) ? Long.parseLong(updatedStr.trim()) : null;

            if (monster != null && !monster.trim().isEmpty() && remaining != null && remaining > 0)
            {
                this.monsterName = monster;
                this.initialAmount = initial != null ? initial : remaining;
                this.amountRemaining = remaining;
                this.location = (loc != null && !loc.trim().isEmpty()) ? loc.trim() : null;
                com.osrscopilot.data.model.SlayerMaster savedMaster =
                    com.osrscopilot.data.model.SlayerMaster.findByName(master);
                this.slayerMaster = savedMaster != null ? savedMaster.getName()
                    : ((master != null && !master.trim().isEmpty()) ? master.trim() : null);
                String savedSubtype = configManager.getConfiguration(CONFIG_GROUP, KEY_TASK_SUBTYPE);
                this.taskSubtype = (savedSubtype != null && !savedSubtype.trim().isEmpty()) ? savedSubtype.trim() : null;
                this.lastUpdatedTime = updated != null ? updated : System.currentTimeMillis();
                this.taskLootValueGp = parseLongOr(configManager.getConfiguration(CONFIG_GROUP, KEY_TASK_LOOT_GP), 0L);
                this.taskSupplyCostGp = parseLongOr(configManager.getConfiguration(CONFIG_GROUP, KEY_TASK_SUPPLY_GP), 0L);
                this.taskSlayerXpEarned = (int) parseLongOr(configManager.getConfiguration(CONFIG_GROUP, KEY_TASK_SLAYER_XP), 0L);

                log.debug("Loaded persisted Slayer task: {} ({} / {})", monsterName, amountRemaining, initialAmount);
                notifyListeners();
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load Slayer task from configuration", e);
        }
    }

    public synchronized void saveToConfig()
    {
        if (configManager == null || demoActive)
        {
            return;
        }

        try
        {
            if (monsterName != null && !monsterName.trim().isEmpty() && amountRemaining > 0)
            {
                configManager.setConfiguration(CONFIG_GROUP, KEY_MONSTER, monsterName);
                configManager.setConfiguration(CONFIG_GROUP, KEY_INITIAL_AMOUNT, initialAmount);
                configManager.setConfiguration(CONFIG_GROUP, KEY_AMOUNT_REMAINING, amountRemaining);
                if (location != null)
                {
                    configManager.setConfiguration(CONFIG_GROUP, KEY_LOCATION, location);
                }
                else
                {
                    configManager.unsetConfiguration(CONFIG_GROUP, KEY_LOCATION);
                }

                if (slayerMaster != null)
                {
                    configManager.setConfiguration(CONFIG_GROUP, KEY_SLAYER_MASTER, slayerMaster);
                }
                else
                {
                    configManager.unsetConfiguration(CONFIG_GROUP, KEY_SLAYER_MASTER);
                }
                configManager.setConfiguration(CONFIG_GROUP, KEY_LAST_UPDATED, lastUpdatedTime);
                // Running loot / supply / XP tally survives a relog mid-task.
                configManager.setConfiguration(CONFIG_GROUP, KEY_TASK_LOOT_GP, taskLootValueGp);
                configManager.setConfiguration(CONFIG_GROUP, KEY_TASK_SUPPLY_GP, taskSupplyCostGp);
                configManager.setConfiguration(CONFIG_GROUP, KEY_TASK_SLAYER_XP, taskSlayerXpEarned);
                if (taskSubtype != null)
                {
                    configManager.setConfiguration(CONFIG_GROUP, KEY_TASK_SUBTYPE, taskSubtype);
                }
                else
                {
                    configManager.unsetConfiguration(CONFIG_GROUP, KEY_TASK_SUBTYPE);
                }
            }
            else
            {
                configManager.unsetConfiguration(CONFIG_GROUP, KEY_TASK_SUBTYPE);
                configManager.unsetConfiguration(CONFIG_GROUP, KEY_MONSTER);
                configManager.unsetConfiguration(CONFIG_GROUP, KEY_INITIAL_AMOUNT);
                configManager.unsetConfiguration(CONFIG_GROUP, KEY_AMOUNT_REMAINING);
                configManager.unsetConfiguration(CONFIG_GROUP, KEY_LOCATION);
                configManager.unsetConfiguration(CONFIG_GROUP, KEY_SLAYER_MASTER);
                configManager.unsetConfiguration(CONFIG_GROUP, KEY_LAST_UPDATED);
                configManager.unsetConfiguration(CONFIG_GROUP, KEY_TASK_LOOT_GP);
                configManager.unsetConfiguration(CONFIG_GROUP, KEY_TASK_SUPPLY_GP);
                configManager.unsetConfiguration(CONFIG_GROUP, KEY_TASK_SLAYER_XP);
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to save Slayer task to configuration", e);
        }
    }

    public synchronized void onVarbitChanged(Client client, VarbitChanged event)
    {
        updateFromClient(client);
    }

    public synchronized void onStatChanged(Client client, StatChanged event)
    {
        if (demoActive)
        {
            return;
        }
        if (event.getSkill() == Skill.SLAYER)
        {
            int currentXp = event.getXp();
            if (lastSlayerXp > 0 && currentXp > lastSlayerXp)
            {
                int delta = currentXp - lastSlayerXp;
                if (hasActiveTask())
                {
                    this.taskSlayerXpEarned += delta;
                }
                updateFromClient(client);
            }
            lastSlayerXp = currentXp;
        }
    }

    public synchronized void onGameTick(Client client)
    {
        updateFromClient(client);
    }


    public synchronized void addTaskLoot(long valueGp)
    {
        if (demoActive)
        {
            return;
        }
        if (valueGp > 0)
        {
            this.taskLootValueGp += valueGp;
            saveToConfig();
            notifyListeners();
        }
    }

    public synchronized void addTaskSupplyCost(long valueGp)
    {
        if (demoActive)
        {
            return;
        }
        if (valueGp > 0)
        {
            this.taskSupplyCostGp += valueGp;
            saveToConfig();
            notifyListeners();
        }
    }

    /** Net gain for the current task: loot value minus supplies spent. May be negative. */
    public synchronized long getTaskNetGp()
    {
        return taskLootValueGp - taskSupplyCostGp;
    }

    public synchronized void updateFromClient(Client client)
    {
        if (client == null || client.getGameState() != GameState.LOGGED_IN || demoActive)
        {
            return;
        }

        boolean stateChanged = false;

        // 1. Live Task Remaining Count via VarPlayer.SLAYER_TASK_SIZE (Varp 394)
        try
        {
            int varpRemaining = client.getVarpValue(VarPlayer.SLAYER_TASK_SIZE);
            if (varpRemaining > 0 && this.monsterName != null)
            {
                zeroSizeStreak = 0;
                if (this.amountRemaining != varpRemaining)
                {
                    log.debug("Slayer task size updated via VarPlayer: {} -> {}", this.amountRemaining, varpRemaining);
                    this.amountRemaining = varpRemaining;
                    if (this.initialAmount < varpRemaining)
                    {
                        this.initialAmount = varpRemaining;
                    }
                    this.lastUpdatedTime = System.currentTimeMillis();
                    stateChanged = true;
                }
            }
            else if (varpRemaining == 0 && this.amountRemaining > 0 && this.monsterName != null)
            {
                // On login the varp is briefly 0 before the real value loads. Only treat a
                // sustained 0 as a real completion so a relog can't wipe the persisted task.
                if (++zeroSizeStreak >= 2)
                {
                    log.debug("Slayer task completed via VarPlayer (size 0)");
                    this.amountRemaining = 0;
                    this.lastUpdatedTime = System.currentTimeMillis();
                    stateChanged = true;
                }
            }
            else
            {
                zeroSizeStreak = 0;
            }
        }
        catch (Exception e)
        {
            // Ignore varp read exceptions
        }

        // 2. Live Slayer Points (Varbits.SLAYER_POINTS = 4068)
        try
        {
            int pts = client.getVarbitValue(Varbits.SLAYER_POINTS);
            if (pts >= 0 && pts != this.slayerPoints)
            {
                this.slayerPoints = pts;
                stateChanged = true;
            }
        }
        catch (Exception e)
        {
            // Fallback or ignore
        }

        // 3. Live Task Streak (Varbits.SLAYER_TASK_STREAK = 4069)
        try
        {
            int streak = client.getVarbitValue(Varbits.SLAYER_TASK_STREAK);
            if (streak >= 0 && streak != this.taskStreak)
            {
                this.taskStreak = streak;
                stateChanged = true;
            }
        }
        catch (Exception e)
        {
            // Fallback or ignore
        }

        // 4. Live Wilderness Task Streak (Varbit 5617)
        try
        {
            int wildyStreak = client.getVarbitValue(5617);
            if (wildyStreak >= 0 && wildyStreak != this.wildernessStreak)
            {
                this.wildernessStreak = wildyStreak;
                stateChanged = true;
            }
        }
        catch (Exception e)
        {
            // Fallback or ignore
        }

        // 5. Update Unlock Statuses in catalog
        if (rewardCatalog != null)
        {
            rewardCatalog.updateUnlockStatus(client);
        }

        // 6. Cache player levels for master-lock display
        try
        {
            net.runelite.api.Player lp = client.getLocalPlayer();
            if (lp != null && lp.getCombatLevel() > 0)
            {
                this.playerCombatLevel = lp.getCombatLevel();
            }
            int sl = client.getRealSkillLevel(Skill.SLAYER);
            if (sl > 0)
            {
                this.playerSlayerLevel = sl;
            }
        }
        catch (Exception e)
        {
            // ignore
        }

        if (stateChanged)
        {
            saveToConfig();
            notifyListeners();
        }
    }

    // ---- task availability: locked (points unlock) / blocked (player's own list) --------------

    /** The reward name that must be unlocked before a master will assign this task, else null. */
    public String getUnlockGateFor(String taskName)
    {
        if (taskName == null)
        {
            return null;
        }
        return SLAYER_UNLOCK_GATES.get(taskName.toLowerCase(Locale.ROOT).trim());
    }

    /** True if the named {@link SlayerRewardCatalog} reward is unlocked (or unknown - assume yes). */
    public boolean isRewardUnlocked(String rewardName)
    {
        if (rewardName == null || rewardCatalog == null)
        {
            return true;
        }
        for (com.osrscopilot.data.model.SlayerReward r : rewardCatalog.getAllRewards())
        {
            if (rewardName.equalsIgnoreCase(r.getName()))
            {
                return r.isUnlocked();
            }
        }
        return true;
    }

    public synchronized boolean isTaskBlocked(String taskName)
    {
        return taskName != null && blockedTasks.contains(taskName.toLowerCase(Locale.ROOT).trim());
    }

    public synchronized java.util.Set<String> getBlockedTasks()
    {
        return new java.util.HashSet<>(blockedTasks);
    }

    public synchronized void setTaskBlocked(String taskName, boolean blocked)
    {
        if (taskName == null || taskName.trim().isEmpty())
        {
            return;
        }
        String k = taskName.toLowerCase(Locale.ROOT).trim();
        boolean changed = blocked ? blockedTasks.add(k) : blockedTasks.remove(k);
        if (changed)
        {
            persistBlockedTasks();
            notifyListeners();
        }
    }

    /**
     * Why a master can't currently give you this task, or null if nothing stops it. Does not
     * consider your Slayer level for the task itself (that's on the row's own requirement text).
     */
    public String getTaskUnavailableReason(String taskName)
    {
        if (isTaskBlocked(taskName))
        {
            return "Blocked - on your block list";
        }
        String gate = getUnlockGateFor(taskName);
        if (gate != null && !isRewardUnlocked(gate))
        {
            return "Locked - unlock \"" + gate + "\" from a Slayer master";
        }
        return null;
    }

    private void loadBlockedTasks()
    {
        blockedTasks.clear();
        if (configManager == null)
        {
            return;
        }
        try
        {
            String raw = configManager.getConfiguration(CONFIG_GROUP, KEY_BLOCKED_TASKS);
            if (raw != null && !raw.trim().isEmpty())
            {
                for (String s : raw.split(","))
                {
                    String t = s.trim().toLowerCase(Locale.ROOT);
                    if (!t.isEmpty())
                    {
                        blockedTasks.add(t);
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.debug("Failed to load blocked tasks", e);
        }
    }

    private void persistBlockedTasks()
    {
        if (configManager == null)
        {
            return;
        }
        try
        {
            if (blockedTasks.isEmpty())
            {
                configManager.unsetConfiguration(CONFIG_GROUP, KEY_BLOCKED_TASKS);
            }
            else
            {
                // Sorted so the persisted string is stable regardless of HashSet iteration order
                // (avoids spurious config-diff churn on every save).
                configManager.setConfiguration(CONFIG_GROUP, KEY_BLOCKED_TASKS,
                    String.join(",", new java.util.TreeSet<>(blockedTasks)));
            }
        }
        catch (Exception e)
        {
            log.debug("Failed to persist blocked tasks", e);
        }
    }

    public synchronized void setSlayerMaster(String master)
    {
        if (demoActive)
        {
            return;
        }
        this.slayerMaster = (master != null && !master.trim().isEmpty()) ? master.trim() : null;
        saveToConfig();
        notifyListeners();
    }

    public synchronized void syncFromSlayerPluginConfig(ConfigManager configManager)
    {
        if (configManager == null || demoActive)
        {
            return;
        }

        try
        {
            String taskName = configManager.getConfiguration("slayer", "taskName");
            String amountStr = configManager.getConfiguration("slayer", "amount");
            String initialStr = configManager.getConfiguration("slayer", "initialAmount");
            String loc = configManager.getConfiguration("slayer", "slayerLocation");
            String rlMasterStr = configManager.getConfiguration("slayer", "slayerMaster");

            if (taskName != null && !taskName.trim().isEmpty() && amountStr != null)
            {
                int remaining = Integer.parseInt(amountStr.trim());
                int initial = initialStr != null ? Integer.parseInt(initialStr.trim()) : remaining;
                // Prefer the master we picked up from the assignment dialogue - RuneLite's own
                // slayer config can lag / hold a stale master when a task is set another way.
                String master = (this.slayerMaster != null && !this.slayerMaster.trim().isEmpty())
                    ? this.slayerMaster
                    : ((rlMasterStr != null && !rlMasterStr.trim().isEmpty()) ? rlMasterStr.trim() : null);

                boolean masterUnknownButRlHasOne = (this.slayerMaster == null || this.slayerMaster.isEmpty())
                    && rlMasterStr != null && !rlMasterStr.trim().isEmpty();
                if (!taskName.equalsIgnoreCase(this.monsterName) || this.amountRemaining != remaining || masterUnknownButRlHasOne)
                {
                    log.debug("Synced Slayer task from RuneLite Slayer plugin: {} ({} / {}) in {}, master: {}", taskName, remaining, initial, loc, master);
                    setTaskDetails(taskName, initial, remaining, loc, master);
                }
            }
        }
        catch (Exception e)
        {
            log.debug("Error syncing from slayer plugin config", e);
        }
    }


    public void addChangeListener(Runnable listener)
    {
        if (listener != null && !changeListeners.contains(listener))
        {
            changeListeners.add(listener);
        }
    }

    public void removeChangeListener(Runnable listener)
    {
        changeListeners.remove(listener);
    }

    private void notifyListeners()
    {
        Runnable dispatch = () -> {
            for (Runnable listener : new ArrayList<>(changeListeners))
            {
                try
                {
                    listener.run();
                }
                catch (Exception e)
                {
                    log.error("Error executing SlayerTaskManager change listener", e);
                }
            }
        };

        if (SwingUtilities.isEventDispatchThread())
        {
            dispatch.run();
        }
        else
        {
            SwingUtilities.invokeLater(dispatch);
        }
    }

    private String cleanMonsterName(String name)
    {
        if (name == null)
        {
            return "";
        }
        String cleaned = name.trim();
        cleaned = cleaned.replaceAll("[.;,!]+$", "").trim();
        return cleaned;
    }

    private String cleanLocationName(String loc)
    {
        if (loc == null)
        {
            return null;
        }
        String cleaned = loc.trim();
        cleaned = cleaned.replaceAll("[.;,!]+$", "").trim();
        if (cleaned.toLowerCase(Locale.ROOT).startsWith("the "))
        {
            cleaned = cleaned.substring(4).trim();
        }
        return cleaned;
    }

    private static final Map<String, String> SLAYER_CATEGORY_MAP = new HashMap<>();
    static
    {
        SLAYER_CATEGORY_MAP.put("metal dragons", "Bronze dragon");
        SLAYER_CATEGORY_MAP.put("metal dragon", "Bronze dragon");
        SLAYER_CATEGORY_MAP.put("basilisks", "Basilisk");
        SLAYER_CATEGORY_MAP.put("basilisk", "Basilisk");
        SLAYER_CATEGORY_MAP.put("bears", "Grizzly bear");
        SLAYER_CATEGORY_MAP.put("bear", "Grizzly bear");
        SLAYER_CATEGORY_MAP.put("spiders", "Spider");
        SLAYER_CATEGORY_MAP.put("spider", "Spider");
        SLAYER_CATEGORY_MAP.put("gryphons", "Gryphon");
        SLAYER_CATEGORY_MAP.put("gryphon", "Gryphon");
        SLAYER_CATEGORY_MAP.put("kalphite", "Kalphite Worker");
        SLAYER_CATEGORY_MAP.put("kalphites", "Kalphite Worker");
        SLAYER_CATEGORY_MAP.put("tzhaar", "TzHaar-Ket");
        SLAYER_CATEGORY_MAP.put("spiritual creatures", "Spiritual warrior");
        SLAYER_CATEGORY_MAP.put("spiritual creature", "Spiritual warrior");
        SLAYER_CATEGORY_MAP.put("black demons", "Black demon");
        SLAYER_CATEGORY_MAP.put("greater demons", "Greater demon");
        SLAYER_CATEGORY_MAP.put("lesser demons", "Lesser demon");
        SLAYER_CATEGORY_MAP.put("abyssal demons", "Abyssal demon");
        SLAYER_CATEGORY_MAP.put("bloodveld", "Bloodveld");
        SLAYER_CATEGORY_MAP.put("nechryael", "Nechryael");
        SLAYER_CATEGORY_MAP.put("dust devils", "Dust devil");
        SLAYER_CATEGORY_MAP.put("smoke devils", "Smoke devil");
        SLAYER_CATEGORY_MAP.put("cave horrors", "Cave horror");
        SLAYER_CATEGORY_MAP.put("cave crawlers", "Cave crawler");
        SLAYER_CATEGORY_MAP.put("crawling hands", "Crawling hand");
        SLAYER_CATEGORY_MAP.put("cave bugs", "Cave bug");
        SLAYER_CATEGORY_MAP.put("cave slimes", "Cave slime");
        SLAYER_CATEGORY_MAP.put("fire giants", "Fire giant");
        SLAYER_CATEGORY_MAP.put("ice giants", "Ice giant");
        SLAYER_CATEGORY_MAP.put("moss giants", "Moss giant");
        SLAYER_CATEGORY_MAP.put("hill giants", "Hill giant");
        SLAYER_CATEGORY_MAP.put("green dragons", "Green dragon");
        SLAYER_CATEGORY_MAP.put("blue dragons", "Blue dragon");
        SLAYER_CATEGORY_MAP.put("red dragons", "Red dragon");
        SLAYER_CATEGORY_MAP.put("black dragons", "Black dragon");
        SLAYER_CATEGORY_MAP.put("fossil island wyverns", "Spitting Wyvern");
        SLAYER_CATEGORY_MAP.put("wyverns", "Skeletal Wyvern");
        SLAYER_CATEGORY_MAP.put("wyrms", "Wyrm");
        SLAYER_CATEGORY_MAP.put("drakes", "Drake");
        SLAYER_CATEGORY_MAP.put("hydras", "Hydra");
        SLAYER_CATEGORY_MAP.put("gargoyles", "Gargoyle");
        SLAYER_CATEGORY_MAP.put("dark beasts", "Dark beast");
        SLAYER_CATEGORY_MAP.put("suqahs", "Suqah");
        SLAYER_CATEGORY_MAP.put("trolls", "Mountain troll");
        SLAYER_CATEGORY_MAP.put("aviansies", "Aviansie");
        SLAYER_CATEGORY_MAP.put("dagannoth", "Dagannoth");
        SLAYER_CATEGORY_MAP.put("hellhounds", "Hellhound");
        SLAYER_CATEGORY_MAP.put("lizardmen", "Lizardman");
        SLAYER_CATEGORY_MAP.put("sourhogs", "Sourhog");
        SLAYER_CATEGORY_MAP.put("dogs", "Guard dog");
        SLAYER_CATEGORY_MAP.put("dwarves", "Dwarf");
        SLAYER_CATEGORY_MAP.put("ghosts", "Ghost");
        SLAYER_CATEGORY_MAP.put("goblins", "Goblin");
        SLAYER_CATEGORY_MAP.put("minotaurs", "Minotaur");
        SLAYER_CATEGORY_MAP.put("monkeys", "Monkey");
        SLAYER_CATEGORY_MAP.put("rats", "Giant rat");
        SLAYER_CATEGORY_MAP.put("scorpions", "Scorpion");
        SLAYER_CATEGORY_MAP.put("skeletons", "Skeleton");
        SLAYER_CATEGORY_MAP.put("wolves", "Wolf");
        SLAYER_CATEGORY_MAP.put("zombies", "Zombie");
        SLAYER_CATEGORY_MAP.put("birds", "Chicken");
        SLAYER_CATEGORY_MAP.put("bats", "Giant bat");
        SLAYER_CATEGORY_MAP.put("cows", "Cow");
        SLAYER_CATEGORY_MAP.put("ankou", "Ankou");
        SLAYER_CATEGORY_MAP.put("jellies", "Jelly");
        SLAYER_CATEGORY_MAP.put("elves", "Elf warrior");
        SLAYER_CATEGORY_MAP.put("kurask", "Kurask");
        SLAYER_CATEGORY_MAP.put("turoth", "Turoth");
        SLAYER_CATEGORY_MAP.put("mutated bloodveld", "Mutated Bloodveld");
        SLAYER_CATEGORY_MAP.put("abyssal demons / sire", "Abyssal demon");
        SLAYER_CATEGORY_MAP.put("callisto & artio", "Callisto");
        SLAYER_CATEGORY_MAP.put("vet'ion & calvar'ion", "Vet'ion");
        SLAYER_CATEGORY_MAP.put("venenatis & spindel", "Venenatis");
    }

    /**
     * Resolves task string against MonsterDatabase to find the best match Monster.
     * Handles singular, plural, case-insensitivity, category mappings, and token matching.
     */
    public static Monster findMonsterForTask(String taskMonsterName, MonsterDatabase db)
    {
        if (taskMonsterName == null || taskMonsterName.trim().isEmpty() || db == null)
        {
            return null;
        }

        String raw = taskMonsterName.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        // 1. Direct Category Mapping
        if (SLAYER_CATEGORY_MAP.containsKey(lower))
        {
            Monster catMonster = db.getMonsterByName(SLAYER_CATEGORY_MAP.get(lower));
            if (catMonster != null)
            {
                return catMonster;
            }
        }

        // 2. Direct exact match
        Monster direct = db.getMonsterByName(raw);
        if (direct != null)
        {
            return direct;
        }
        direct = db.getMonsterByName(lower);
        if (direct != null)
        {
            return direct;
        }

        // 3. Plural-to-singular transformations
        List<String> candidates = new ArrayList<>();

        if (lower.endsWith("ies") && lower.length() > 3)
        {
            candidates.add(lower.substring(0, lower.length() - 3) + "y"); // Aviansies -> Aviansie, Jellies -> Jelly
            candidates.add(lower.substring(0, lower.length() - 1)); // Aviansie
        }
        if (lower.endsWith("ves") && lower.length() > 3)
        {
            candidates.add(lower.substring(0, lower.length() - 3) + "f"); // Elves -> Elf, Wolves -> Wolf
            candidates.add(lower.substring(0, lower.length() - 3) + "fe");
        }
        if (lower.endsWith("men") && lower.length() > 3)
        {
            candidates.add(lower.substring(0, lower.length() - 3) + "man"); // Lizardmen -> Lizardman
        }
        if (lower.endsWith("es") && lower.length() > 2)
        {
            candidates.add(lower.substring(0, lower.length() - 2)); // Cockatrices -> Cockatrice, Banshees -> Banshee
            candidates.add(lower.substring(0, lower.length() - 1));
        }
        if (lower.endsWith("s") && lower.length() > 1)
        {
            candidates.add(lower.substring(0, lower.length() - 1)); // Hellhounds -> Hellhound, Gargoyles -> Gargoyle
        }

        for (String candidate : candidates)
        {
            if (SLAYER_CATEGORY_MAP.containsKey(candidate))
            {
                Monster catM = db.getMonsterByName(SLAYER_CATEGORY_MAP.get(candidate));
                if (catM != null)
                {
                    return catM;
                }
            }
            Monster m = db.getMonsterByName(candidate);
            if (m != null)
            {
                return m;
            }
        }

        // 4. Search by name tokens (e.g. "Kalphite", "TzHaar", "Trolls", "Dagannoth", "Wyverns")
        List<Monster> matches = db.searchMonstersByName(lower);
        if (!matches.isEmpty())
        {
            Monster best = null;
            for (Monster m : matches)
            {
                if (m.getName().equalsIgnoreCase(raw) || m.getName().equalsIgnoreCase(lower))
                {
                    return m;
                }
                for (String c : candidates)
                {
                    if (m.getName().equalsIgnoreCase(c))
                    {
                        return m;
                    }
                }
                if (best == null)
                {
                    best = m;
                }
                else if (m.getSlayerLevel() > best.getSlayerLevel())
                {
                    best = m;
                }
                else if (m.hasSpawnZones() && !best.hasSpawnZones())
                {
                    best = m;
                }
            }
            return best != null ? best : matches.get(0);
        }

        // 5. Fallback search across all monsters for safe substring contains (never match short < 4 char names like "Me")
        for (Monster m : db.getAllMonsters())
        {
            String mName = m.getName().toLowerCase(Locale.ROOT);
            if (mName.equals(lower))
            {
                return m;
            }
            // Disallow short monster names from matching arbitrary prefixes
            if (mName.length() >= 4 && (mName.startsWith(lower) || lower.startsWith(mName)))
            {
                return m;
            }
            for (String c : candidates)
            {
                if (c.length() >= 4 && (mName.startsWith(c) || c.startsWith(mName)))
                {
                    return m;
                }
            }
        }

        return null;
    }
}
