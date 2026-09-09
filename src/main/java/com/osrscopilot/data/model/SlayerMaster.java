package com.osrscopilot.data.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

@Value
@Builder
public class SlayerMaster
{
    String name;
    String locationName;
    WorldPoint locationPoint;
    WorldPoint surfaceEntrance;
    int combatRequirement;
    int slayerRequirement;
    String questRequirement;
    String description;
    String pointsInfo;

    public static final SlayerMaster TURAEL = SlayerMaster.builder()
        .name("Turael / Aya")
        .locationName("Burthorpe")
        .locationPoint(new WorldPoint(2931, 3536, 0))
        .combatRequirement(1)
        .slayerRequirement(1)
        .questRequirement(null)
        .description("Entry-level master in Burthorpe. Can cancel/reset tasks from other masters for 0 pts (breaks streak).")
        .pointsInfo("0 pts / task")
        .build();

    public static final SlayerMaster SPRIA = SlayerMaster.builder()
        .name("Spria")
        .locationName("Draynor Village")
        .locationPoint(new WorldPoint(3091, 3267, 0))
        .combatRequirement(1)
        .slayerRequirement(1)
        .questRequirement(null)
        .description("Entry-level master in Draynor Village. Can cancel/reset tasks like Turael.")
        .pointsInfo("0 pts / task")
        .build();

    public static final SlayerMaster MAZCHNA = SlayerMaster.builder()
        .name("Mazchna")
        .locationName("Canifis")
        .locationPoint(new WorldPoint(3510, 3508, 0))
        .combatRequirement(20)
        .slayerRequirement(1)
        .questRequirement("Priest in Peril")
        .description("Low-level master in Canifis near the hair salon.")
        .pointsInfo("2 pts (10th: 10, 50th: 30, 100th: 50)")
        .build();

    public static final SlayerMaster VANNAKA = SlayerMaster.builder()
        .name("Vannaka")
        .locationName("Edgeville Dungeon")
        .locationPoint(new WorldPoint(3145, 9914, 0))
        .surfaceEntrance(new WorldPoint(3096, 3468, 0))
        .combatRequirement(40)
        .slayerRequirement(1)
        .questRequirement(null)
        .description("Mid-level master wielding a 2h sword in Edgeville Dungeon.")
        .pointsInfo("4 pts (10th: 20, 50th: 60, 100th: 100)")
        .build();

    public static final SlayerMaster CHAELDAR = SlayerMaster.builder()
        .name("Chaeldar")
        .locationName("Zanaris")
        .locationPoint(new WorldPoint(2446, 4431, 0))
        .surfaceEntrance(new WorldPoint(3202, 3169, 0))
        .combatRequirement(70)
        .slayerRequirement(1)
        .questRequirement("Lost City")
        .description("Fairy Queen in Zanaris throne room for mid-high level combatants.")
        .pointsInfo("10 pts (10th: 50, 50th: 150, 100th: 250)")
        .build();

    public static final SlayerMaster KONAR = SlayerMaster.builder()
        .name("Konar quo Maten")
        .locationName("Mount Karuulm")
        .locationPoint(new WorldPoint(1310, 3810, 0))
        .combatRequirement(75)
        .slayerRequirement(1)
        .questRequirement(null)
        .description("Assigns location-locked tasks across Gielinor; monsters drop Brimstone Keys for chest loot.")
        .pointsInfo("18 pts (10th: 90, 50th: 270, 100th: 450)")
        .build();

    public static final SlayerMaster NIEVE = SlayerMaster.builder()
        .name("Nieve / Steve")
        .locationName("Tree Gnome Stronghold")
        .locationPoint(new WorldPoint(2432, 3423, 0))
        .combatRequirement(85)
        .slayerRequirement(1)
        .questRequirement(null)
        .description("High-level master stationed near the Stronghold Slayer Cave.")
        .pointsInfo("12 pts (10th: 60, 50th: 180, 100th: 300)")
        .build();

    public static final SlayerMaster DURADEL = SlayerMaster.builder()
        .name("Duradel")
        .locationName("Shilo Village")
        .locationPoint(new WorldPoint(2869, 2982, 1))
        .combatRequirement(100)
        .slayerRequirement(50)
        .questRequirement("Shilo Village")
        .description("Master in Shilo Village hut (2nd floor) offering highest points and dangerous tasks.")
        .pointsInfo("15 pts (10th: 75, 50th: 225, 100th: 375)")
        .build();

    public static final SlayerMaster KRYSTILIA = SlayerMaster.builder()
        .name("Krystilia")
        .locationName("Edgeville (Jailhouse)")
        .locationPoint(new WorldPoint(3092, 3506, 0))
        .combatRequirement(1)
        .slayerRequirement(1)
        .questRequirement(null)
        .description("Wilderness Slayer master in Edgeville jail. Monsters drop Larran's Keys.")
        .pointsInfo("25 pts (10th: 125, 50th: 375, 100th: 625)")
        .build();

    // Mortimer - Slayer master in Wyrmscraig Cavern (Wyrmscraig, released 29 Jul 2026). Data from
    // the OSRS Wiki: location pin (2589,8614), Cmb 100 / Slayer 70, partial "Fallen From Grace".
    // He uses per-monster "Mortifier" point modifiers (5-15) rather than a flat points rate.
    public static final SlayerMaster MORTIMER = SlayerMaster.builder()
        .name("Mortimer")
        .locationName("Wyrmscraig Cavern")
        .locationPoint(new WorldPoint(2589, 8614, 0))
        .surfaceEntrance(new WorldPoint(2566, 2255, 0))
        .combatRequirement(100)
        .slayerRequirement(70)
        .questRequirement("Fallen From Grace (partial)")
        .description("Skeletal Slayer master in Wyrmscraig Cavern, off the Varlamore coast. Uses "
            + "per-monster \"Mortifier\" point modifiers (5-15) instead of a flat points rate.")
        .pointsInfo("Mortifiers: 5-15 pts/task (varies by monster)")
        .build();

    public static final List<SlayerMaster> ALL_MASTERS = Collections.unmodifiableList(Arrays.asList(
        TURAEL,
        SPRIA,
        MAZCHNA,
        VANNAKA,
        CHAELDAR,
        KONAR,
        NIEVE,
        DURADEL,
        KRYSTILIA,
        MORTIMER
    ));

    public int getBasePoints()
    {
        if (this == TURAEL || this == SPRIA) return 0;
        if (this == MAZCHNA) return 2;
        if (this == VANNAKA) return 4;
        if (this == CHAELDAR) return 10;
        if (this == NIEVE) return 12;
        if (this == DURADEL) return 15;
        if (this == KONAR) return 18;
        if (this == KRYSTILIA) return 25;
        // Mortimer has no flat rate (per-monster "Mortifier" modifiers 5-15); 10 is a stand-in for
        // any code that expects a single number - the real value is per-task, see pointsInfo.
        if (this == MORTIMER) return 10;
        return 0;
    }

    public int getPointsForStreak(int streak)
    {
        int base = getBasePoints();
        if (base == 0 || streak <= 0) return base;
        if (streak % 1000 == 0) return base * 50;
        if (streak % 250 == 0) return base * 35;
        if (streak % 100 == 0) return base * 25;
        if (streak % 50 == 0) return base * 15;
        if (streak % 10 == 0) return base * 5;
        return base;
    }

    public static SlayerMaster findByName(String name)
    {
        if (name == null || name.trim().isEmpty())
        {
            return null;
        }
        String lower = name.toLowerCase().trim();

        // 1. Exact match wins (so "Chaeldar" can't be picked for "Duradel" by a stray substring).
        for (SlayerMaster master : ALL_MASTERS)
        {
            if (master.getName().toLowerCase().equals(lower))
            {
                return master;
            }
        }
        // 2. The input is, or contains as a distinct word, a master's full name or the first word
        //    of any of its slash-separated aliases: "Nieve" OR "Steve" of "Nieve / Steve",
        //    "Turael" OR "Aya" of "Turael / Aya", "Konar" of "Konar quo Maten".
        for (SlayerMaster master : ALL_MASTERS)
        {
            String full = master.getName().toLowerCase();
            java.util.List<String> tokens = new java.util.ArrayList<>();
            tokens.add(full);
            for (String alias : full.split("/"))
            {
                String firstWord = alias.trim().split(" ")[0];
                if (!firstWord.isEmpty())
                {
                    tokens.add(firstWord);
                }
            }
            for (String token : tokens)
            {
                // An exact match is safe at any length ("Aya"); the fuzzy word-boundary matches
                // need >= 4 chars so a stray short substring can't pick a master.
                if (lower.equals(token))
                {
                    return master;
                }
                if (token.length() >= 4 && (lower.startsWith(token + " ")
                    || lower.endsWith(" " + token) || lower.contains(" " + token + " ")))
                {
                    return master;
                }
            }
        }
        // 3. A master's name starts with the input (partial / abbreviation), min 4 chars.
        if (lower.length() >= 4)
        {
            for (SlayerMaster master : ALL_MASTERS)
            {
                if (master.getName().toLowerCase().startsWith(lower))
                {
                    return master;
                }
            }
        }
        return null;
    }
}
