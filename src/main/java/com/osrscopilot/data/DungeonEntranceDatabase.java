package com.osrscopilot.data;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

@Singleton
public class DungeonEntranceDatabase
{
    @Value
    public static class DungeonMapping
    {
        String dungeonName;
        int minX, minY, maxX, maxY;
        int minPlane, maxPlane;
        WorldPoint surfaceEntrance;
        String entranceDescription;
        /**
         * True only when {@link #surfaceEntrance} is a hand-verified coordinate (checked in-game or
         * against an OSRS Wiki entrance map pin). Every entry here is currently an unverified
         * estimate, so callers should treat the entrance as an approximate area, not a pinpoint.
         */
        boolean entranceVerified;
    }

    private final List<DungeonMapping> mappings = new ArrayList<>();

    public DungeonEntranceDatabase()
    {
        initMappings();
    }

    private void initMappings()
    {
        // TzHaar City / Mor Ul Rek (Volcano on Karamja)
        addMapping("TzHaar City", 2360, 5050, 2560, 5200, 0, 3, new WorldPoint(2857, 3169, 0), "Karamja Volcano Entrance");
        addMapping("Mor Ul Rek", 2430, 5050, 2560, 5180, 0, 3, new WorldPoint(2857, 3169, 0), "Karamja Volcano Entrance");
        addMapping("Karamja Volcano", 2820, 9540, 2870, 9600, 0, 3, new WorldPoint(2857, 3169, 0), "Karamja Volcano Rocks");

        // Keldagrim (Underground Dwarven City)
        addMapping("Keldagrim", 2816, 10112, 2944, 10240, 0, 3, new WorldPoint(3140, 3504, 0), "Rellekka Mountain Cart Tunnel");
        addMapping("Keldagrim Entrance Cave", 2750, 10100, 2815, 10240, 0, 3, new WorldPoint(3140, 3504, 0), "Rellekka Mountain Cart Tunnel");

        // Zanaris (Lost City / Fairy Realm)
        addMapping("Zanaris", 2368, 4352, 2496, 4480, 0, 3, new WorldPoint(3204, 3169, 0), "Lumbridge Swamp Shed");

        // Catacombs of Kourend
        addMapping("Catacombs of Kourend", 1580, 9960, 1740, 10120, 0, 3, new WorldPoint(1636, 3673, 0), "Kourend Castle Statue");

        // Dorgesh-Kaan & Lumbridge Caves
        addMapping("Dorgesh-Kaan", 2688, 5248, 2752, 5376, 0, 3, new WorldPoint(3222, 3218, 0), "Lumbridge Castle Cellar / Swamp Caves");
        // The old surface entrance (2715,5280) was itself an underground coord - the map can't show
        // it. Reached from the Lumbridge Swamp Caves via the swamp dark hole.
        addMapping("Dorgesh-Kaan South Dungeon", 2680, 5185, 2760, 5320, 0, 3, new WorldPoint(3169, 3172, 0), "Lumbridge Swamp Caves (Dorgesh-Kaan South)");
        addMapping("Lumbridge Swamp Caves", 3130, 9530, 3240, 9590, 0, 3, new WorldPoint(3169, 3173, 0), "Lumbridge Swamp Dark Hole");
        addMapping("Lumbridge Catacombs", 3220, 9595, 3260, 9640, 0, 3, new WorldPoint(3244, 3198, 0), "Lumbridge Graveyard Entrance");

        // Taverley Dungeon
        addMapping("Taverley Dungeon", 2816, 9664, 2944, 9860, 0, 3, new WorldPoint(2884, 3396, 0), "Taverley Ladder");

        // Edgeville Dungeon & Varrock Sewers
        addMapping("Edgeville Dungeon", 3072, 9820, 3140, 10020, 0, 3, new WorldPoint(3096, 3468, 0), "Edgeville Trapdoor");
        addMapping("Varrock Sewers", 3141, 9856, 3280, 9930, 0, 3, new WorldPoint(3237, 3458, 0), "Varrock Castle Manhole");

        // Brimhaven Dungeon
        addMapping("Brimhaven Dungeon", 2624, 9408, 2760, 9600, 0, 3, new WorldPoint(2744, 3155, 0), "Brimhaven Cave Entrance");

        // Fremennik Slayer Dungeon & Caves
        addMapping("Fremennik Slayer Dungeon", 2680, 9980, 2830, 10070, 0, 3, new WorldPoint(2797, 3616, 0), "Rellekka Slayer Cave");
        addMapping("Brine Rat Cavern", 2680, 10100, 2740, 10160, 0, 3, new WorldPoint(2748, 3732, 0), "Rellekka Cave Entrance");
        addMapping("Jormungand's Prison", 2410, 10370, 2490, 10440, 0, 3, new WorldPoint(2463, 4008, 0), "Island of Stone (centre)");
        // Lighthouse basement (Horror from the Deep / Dagannoth spawn) - the surface Lighthouse
        // where Jossik and the trapdoor down are.
        addMapping("Lighthouse", 2500, 10000, 2550, 10045, 0, 3, new WorldPoint(2509, 3641, 0), "Lighthouse trapdoor (Jossik)");

        // Slayer Tower (Basement & Upper Floors)
        addMapping("Slayer Tower", 3400, 3520, 3450, 3580, 1, 3, new WorldPoint(3428, 3535, 0), "Canifis Slayer Tower Entrance");
        addMapping("Slayer Tower Basement", 3400, 9920, 3460, 9990, 0, 3, new WorldPoint(3428, 3535, 0), "Slayer Tower Trapdoor");

        // Waterfall Dungeon & Ancient Cavern
        addMapping("Waterfall Dungeon", 2550, 9861, 2624, 9920, 0, 3, new WorldPoint(2511, 3464, 0), "Baxtorian Falls Door");
        addMapping("Ancient Cavern", 1730, 5300, 1790, 5360, 0, 3, new WorldPoint(2511, 3508, 0), "Baxtorian Whirlpool");
        addMapping("Glarial's Tomb", 2520, 9810, 2560, 9850, 0, 3, new WorldPoint(2556, 3445, 0), "Baxtorian Glarial's Tombstone");

        // Miscellania Underground
        addMapping("Miscellania Dungeon", 2560, 10048, 2624, 10112, 0, 3, new WorldPoint(2511, 3864, 0), "Miscellania Castle Trapdoor");

        // Mage Training Arena (Underground Sub-map)
        addMapping("Mage Training Arena", 3330, 9600, 3400, 9700, 0, 3, new WorldPoint(3363, 3298, 0), "Al Kharid MTA Entrance");

        // Great Kourend Dungeons
        addMapping("Chasm of Fire", 1408, 10048, 1472, 10112, 0, 3, new WorldPoint(1435, 3671, 0), "Shayzien Chasm Elevator");
        addMapping("Forthos Dungeon", 1780, 9880, 1870, 9990, 0, 3, new WorldPoint(1700, 3574, 0), "Hosidius Forthos Ruin");
        addMapping("Karuulm Slayer Dungeon", 1240, 10140, 1380, 10290, 0, 3, new WorldPoint(1311, 3807, 0), "Mount Karuulm Elevator");
        addMapping("Farming Guild", 1220, 10060, 1260, 10100, 0, 3, new WorldPoint(1249, 3737, 0), "Farming Guild Entrance");
        addMapping("Ancient Vault", 1720, 9620, 1760, 9660, 0, 3, new WorldPoint(3508, 2971, 0), "Hosidius Crypt Entrance");

        // Kalphite Lair & Kalphite Hive
        addMapping("Kalphite Lair", 3270, 9470, 3520, 9540, 0, 3, new WorldPoint(3226, 3108, 0), "Shantay Pass Kalphite Lair");

        // God Wars Dungeon (Trollheim)
        addMapping("God Wars Dungeon", 2816, 5248, 2944, 5380, 0, 3, new WorldPoint(2918, 3745, 0), "Trollheim Boulder Crevice");

        // Wyvern Cave / Fossil Island Underground
        addMapping("Wyvern Cave", 3580, 10210, 3670, 10310, 0, 3, new WorldPoint(3746, 3779, 0), "Fossil Island Wyvern Hole");
        addMapping("Lithkren Vault", 1540, 5035, 1600, 5100, 0, 3, new WorldPoint(3554, 3998, 0), "Lithkren Trapdoor");

        // Smoke Dungeon & Smoke Devil Dungeon
        addMapping("Smoke Dungeon", 3136, 9340, 3330, 9410, 0, 3, new WorldPoint(3310, 2962, 0), "Pollnivneach Well");
        addMapping("Smoke Devil Dungeon", 2340, 9410, 2440, 9480, 0, 3, new WorldPoint(2411, 3060, 0), "Feldip Hills Smoky Well");

        // Mos Le'Harmless Caves & Morytania Underground
        addMapping("Mos Le'Harmless Caves", 3648, 9340, 3840, 9480, 0, 3, new WorldPoint(3748, 2974, 0), "Mos Le'Harmless Cave Entrance");
        addMapping("Haunted Mine", 3400, 9600, 3460, 9660, 0, 3, new WorldPoint(3440, 3232, 0), "Mort'ton Mine Cart");
        addMapping("Tarn's Lair", 3120, 4540, 3200, 4620, 0, 3, new WorldPoint(3150, 4580, 0), "Haunted Woods Trapdoor");
        addMapping("Sisterhood Sanctuary", 3770, 9700, 3850, 9860, 0, 3, new WorldPoint(3724, 3335, 0), "Slepe Sanctuary Trapdoor");
        addMapping("Shade Catacombs", 3480, 9680, 3540, 9740, 0, 3, new WorldPoint(3485, 3321, 0), "Mort'ton Crypt Entrance");
        addMapping("Barrows", 3550, 3275, 3580, 3305, 0, 3, new WorldPoint(3565, 3290, 0), "Barrows Mounds");

        // Asgarnia & Misthalin Underground
        addMapping("Asgarnia Ice Dungeon", 2944, 9530, 3080, 9610, 0, 3, new WorldPoint(3009, 3150, 0), "Port Sarim Trapdoor");
        addMapping("Stronghold of Security", 1850, 5210, 2390, 5310, 0, 3, new WorldPoint(3081, 3421, 0), "Barbarian Village Hole");
        addMapping("Dwarven Mines", 3010, 9810, 3055, 9860, 0, 3, new WorldPoint(3019, 3450, 0), "Falador / Ice Mountain Ladder");
        addMapping("Draynor Sewers", 3080, 9650, 3120, 9700, 0, 3, new WorldPoint(3080, 3270, 0), "Draynor Village Trapdoor");
        addMapping("Giant Mole Lair", 1700, 5140, 1800, 5220, 0, 3, new WorldPoint(2996, 3376, 0), "Falador Park Mole Hills");
        addMapping("Tolna's Rift", 3281, 9810, 3330, 9850, 0, 3, new WorldPoint(3310, 3450, 0), "Tolna's Rift Chasm");
        addMapping("Digsite Dungeon", 3340, 9760, 3380, 9800, 0, 3, new WorldPoint(3370, 3428, 0), "Digsite Winch");

        // Wilderness Dungeons & Boss Chambers
        addMapping("Ghorrock Prison", 2860, 10300, 2940, 10380, 0, 3, new WorldPoint(2915, 3935, 0), "Ghorrock Dungeon Entrance");
        addMapping("Corporeal Beast Lair", 2940, 4350, 3000, 4420, 0, 3, new WorldPoint(3206, 3681, 0), "Wilderness Cave");
        addMapping("KBD Lair", 2250, 4680, 2290, 4720, 0, 3, new WorldPoint(3067, 3865, 0), "Lava Maze Ladder");
        addMapping("Revenant Caves", 3120, 10050, 3280, 10260, 0, 3, new WorldPoint(3067, 3740, 0), "Wilderness Revenant Cave (North)");
        addMapping("Wilderness Slayer Cave", 3360, 10040, 3440, 10160, 0, 3, new WorldPoint(3260, 3664, 0), "Wilderness Slayer Cave (south entrance)");
        addMapping("Deep Wilderness Dungeon", 3020, 10300, 3090, 10380, 0, 3, new WorldPoint(3045, 3925, 0), "Wilderness Lvl 52 Gate");
        addMapping("Wilderness God Wars Dungeon", 3000, 10090, 3080, 10180, 0, 3, new WorldPoint(3018, 3740, 0), "Wilderness Lvl 28 Crevice");
        addMapping("Scorpia Pit", 3210, 10310, 3260, 10360, 0, 3, new WorldPoint(3233, 3940, 0), "Wilderness Lvl 54 Cave Entrance");
        addMapping("Artio's Cave", 1740, 11530, 1780, 11570, 0, 3, new WorldPoint(3130, 3680, 0), "Wilderness Lvl 21 Hunter Cave");
        addMapping("Spindel's Cave", 1600, 11530, 1640, 11570, 0, 3, new WorldPoint(3300, 3740, 0), "Wilderness Lvl 29 Web Cave");
        addMapping("Calvar'ion's Cave", 1860, 11530, 1900, 11570, 0, 3, new WorldPoint(3170, 3670, 0), "Wilderness Lvl 21 Graveyard Cave");

        // Kandarin / Western Province Dungeons
        addMapping("Corsair Cove Dungeon", 1980, 8960, 2050, 9050, 0, 3, new WorldPoint(2523, 2861, 0), "Corsair Cove Rock Entrance");
        addMapping("Iorwerth Dungeon", 3130, 12350, 3330, 12550, 0, 3, new WorldPoint(3225, 6046, 0), "Prifddinas Well");
        addMapping("Crash Site Cavern", 2080, 5600, 2160, 5680, 0, 3, new WorldPoint(2436, 3520, 0), "Gnome Stronghold Crash Site");
        addMapping("Underground Pass", 2300, 4670, 2490, 4740, 0, 3, new WorldPoint(2449, 3312, 0), "West Ardougne Well");
        addMapping("Underground Pass (West)", 2120, 4670, 2240, 4740, 0, 3, new WorldPoint(2449, 3312, 0), "West Ardougne Well");
        addMapping("Kraken Cove", 2240, 9980, 2320, 10060, 0, 3, new WorldPoint(2278, 3611, 0), "Piscatoris Kraken Cove Entrance");
        addMapping("Stronghold Slayer Cave", 2410, 9760, 2480, 9820, 0, 3, new WorldPoint(2430, 3425, 0), "Nieve's Cave Entrance");
        addMapping("Witchaven Dungeon", 2690, 9670, 2740, 9720, 0, 3, new WorldPoint(2696, 3283, 0), "Witchaven Trapdoor");
        addMapping("Ardougne Sewers", 2570, 9670, 2630, 9720, 0, 3, new WorldPoint(2632, 3294, 0), "East Ardougne Manhole");
        addMapping("Tree Gnome Village Dungeon", 2520, 9540, 2560, 9580, 0, 3, new WorldPoint(2530, 3160, 0), "Tree Gnome Maze Ladder");
        addMapping("Ogre Enclave", 2570, 9420, 2620, 9470, 0, 3, new WorldPoint(2524, 3035, 0), "Gu'Tanoth Cave Entrance");
        addMapping("Temple of Ikov", 2630, 9800, 2680, 9850, 0, 3, new WorldPoint(2677, 3405, 0), "McGrubor's Wood Ladder");
        addMapping("Isle of Souls Dungeon", 2120, 9280, 2160, 9320, 0, 3, new WorldPoint(2309, 2919, 0), "Isle of Souls Hole");
        addMapping("Ourania Cave", 3010, 5560, 3060, 5620, 0, 3, new WorldPoint(2450, 3230, 0), "Ourania Altar Crack");
        addMapping("Goblin Cave", 2570, 9810, 2620, 9860, 0, 3, new WorldPoint(2625, 3390, 0), "Fishing Guild Goblin Cave");
        addMapping("Shadow Dungeon", 2700, 4880, 2760, 4940, 0, 3, new WorldPoint(2547, 3421, 0), "Baxtorian Shadow Dungeon Ladder");
        addMapping("Myths' Guild Basement", 2425, 6675, 2495, 6785, 0, 3, new WorldPoint(2457, 2846, 0), "Myths' Guild Entrance");

        // Varlamore
        addMapping("Cam Torum", 1250, 9350, 1550, 9700, 0, 3, new WorldPoint(1440, 3100, 0), "Civitas illa Fortis Tunnel");
        addMapping("Ruins of Tapoyauik", 1350, 4500, 1660, 9650, 0, 3, new WorldPoint(1693, 3232, 0), "Twilight Temple Tapoyauik Entrance");
        addMapping("Lizardman Caves", 1250, 9900, 1350, 9980, 0, 3, new WorldPoint(1310, 3545, 0), "Lizardman Settlement Cave Entrance");
        addMapping("Meiyerditch Laboratories", 3540, 9700, 3600, 9760, 0, 3, new WorldPoint(3625, 3218, 0), "Meiyerditch Laboratories Trapdoor");
        addMapping("Kurask Lair", 1150, 9150, 1200, 9250, 0, 3, new WorldPoint(1191, 2786, 0), "Kurask Lair Entrance (far south-west isle)");
        addMapping("Aldarin Caverns", 3050, 8800, 3300, 8900, 0, 3, new WorldPoint(1650, 3050, 0), "Aldarin Gryphon Cavern Entrance");

        // Desert Treasure II Boss Chambers
        addMapping("Lassar Undercity", 3000, 6350, 3070, 6420, 0, 3, new WorldPoint(2996, 3440, 0), "Ice Mountain Camdozaal Entrance");
        addMapping("Stranglewood Temple", 1100, 3380, 1160, 3450, 0, 3, new WorldPoint(1215, 3415, 0), "The Stranglewood Boat");
        addMapping("The Scar", 2040, 6350, 2100, 6420, 0, 3, new WorldPoint(3310, 3150, 0), "Shantay Pass Scar Entrance");
        addMapping("Lassar Catacombs", 2620, 6390, 2680, 6460, 0, 3, new WorldPoint(2996, 3440, 0), "Camdozaal Catacombs Entrance");

        // Ape Atoll & Island Dungeons
        addMapping("Ape Atoll Dungeon", 2750, 9100, 2830, 9220, 0, 3, new WorldPoint(2763, 2703, 0), "Marim Temple Trapdoor");
        addMapping("Waterbirth Dungeon", 2860, 4420, 2940, 4480, 0, 3, new WorldPoint(2525, 3743, 0), "Waterbirth Island Cave");
        addMapping("Waterbirth Island Dungeon", 2400, 10100, 2560, 10280, 0, 3, new WorldPoint(2525, 3743, 0), "Waterbirth Island Cave");
        addMapping("Ungael", 2250, 4030, 2290, 4070, 0, 3, new WorldPoint(2643, 3697, 0), "Rellekka Boat");
        addMapping("Zulrah's Shrine", 2250, 3050, 2290, 3090, 0, 3, new WorldPoint(2211, 3056, 0), "Zul-Andra Boat");
    }

    private void addMapping(String name, int minX, int minY, int maxX, int maxY, int minPlane, int maxPlane, WorldPoint entrance, String desc)
    {
        // entranceVerified is false for every entry - these surface coordinates are hand-entered
        // estimates, not verified pins. See DungeonMapping#isEntranceVerified().
        mappings.add(new DungeonMapping(name, minX, minY, maxX, maxY, minPlane, maxPlane, entrance, desc, false));
    }

    public boolean isUndergroundOrDungeon(WorldPoint pt)
    {
        if (pt == null) return false;
        if (pt.getY() >= 6400) return true; // Standard OSRS underground coordinate band (Y >= 6400)
        if (pt.getY() <= 5500 && pt.getY() >= 4000) return true; // TzHaar / Zanaris coordinate band

        for (DungeonMapping mapping : mappings)
        {
            if (pt.getX() >= mapping.minX && pt.getX() <= mapping.maxX &&
                pt.getY() >= mapping.minY && pt.getY() <= mapping.maxY &&
                pt.getPlane() >= mapping.minPlane && pt.getPlane() <= mapping.maxPlane)
            {
                return true;
            }
        }
        return false;
    }

    public DungeonMapping findDungeon(WorldPoint pt)
    {
        if (pt == null) return null;
        for (DungeonMapping mapping : mappings)
        {
            if (pt.getX() >= mapping.minX && pt.getX() <= mapping.maxX &&
                pt.getY() >= mapping.minY && pt.getY() <= mapping.maxY &&
                pt.getPlane() >= mapping.minPlane && pt.getPlane() <= mapping.maxPlane)
            {
                return mapping;
            }
        }
        return null;
    }

    public WorldPoint getSurfaceEntrance(WorldPoint pt)
    {
        DungeonMapping mapping = findDungeon(pt);
        if (mapping != null)
        {
            return mapping.getSurfaceEntrance();
        }
        return null;
    }
}
