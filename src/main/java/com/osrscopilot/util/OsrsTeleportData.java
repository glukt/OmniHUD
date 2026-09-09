package com.osrscopilot.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class OsrsTeleportData
{
    private static final Map<String, String> TELEPORT_MAP;
    private static final Map<String, String> PRIMARY_TELEPORT_MAP;

    static
    {
        Map<String, String> map = new HashMap<>();

        // Misthalin & Lumbridge / Varrock
        map.put("Lumbridge", "Lumbridge Teleport / Fairy Ring (DJP)");
        map.put("Varrock", "Varrock Teleport / Ring of Wealth (GE)");
        map.put("Grand Exchange", "Ring of Wealth (GE) / Varrock Teleport (GE toggle)");
        map.put("Edgeville", "Amulet of Glory (Edgeville) / Fairy Ring (DKR)");
        map.put("Draynor Village", "Amulet of Glory (Draynor) / Draynor Manor Tablet / Fairy Ring (DIS)");
        map.put("Barbarian Village", "Skull Sceptre / Combat Bracelet (Monastery)");
        map.put("Dorgesh-Kaan", "Dorgesh-Kaan Sphere / Fairy Ring (AJQ)");
        map.put("Digsite", "Digsite Pendant / Senntisten Teleport");
        map.put("Exam Centre", "Digsite Pendant (Exam Centre)");
        map.put("Paterdomus", "Salve Graveyard Tablet / Fairy Ring (CKS)");
        map.put("Fossil Island", "Digsite Pendant (House on the Hill) / Mushtrees");
        map.put("Cooks' Guild", "Skills Necklace (Cooking Guild)");
        map.put("Champions' Guild", "Combat Bracelet / Chronicle (Champions' Guild)");

        // Asgarnia
        map.put("Falador", "Falador Teleport / Ring of Wealth (Park)");
        map.put("Port Sarim", "Explorer's Ring 3+ (Cabbage Patch) / Spirit Tree / Fairy Ring (AIQ)");
        map.put("Rimmington", "House Teleport (Rimmington Portal / Tab)");
        map.put("Taverley", "Taverley Teleport Tablet / Balloon (Taverley)");
        map.put("Burthorpe", "Games Necklace (Burthorpe) / Combat Bracelet (Warriors' Guild)");
        map.put("Warriors' Guild", "Combat Bracelet (Warriors' Guild) / Games Necklace");
        map.put("Crafting Guild", "Skills Necklace (Crafting Guild) / Crafting Cape");
        map.put("Mining Guild", "Skills Necklace (Mining Guild) / Falador Teleport");
        map.put("Monastery (Asgarnia)", "Combat Bracelet (Monastery) / Ardougne Cloak");

        // Kharidian Desert
        map.put("Al Kharid", "Amulet of Glory (Al Kharid) / Ring of Dueling (PvP Arena)");
        map.put("Shantay Pass", "Ring of Dueling (PvP Arena) / Fairy Ring (BIQ)");
        map.put("Pollnivneach", "Pollnivneach Tablet / Carpet / Fairy Ring (BIQ)");
        map.put("Nardah", "Desert Amulet 4 / Nardah Scroll / Fairy Ring (DLQ)");
        map.put("Sophanem", "Pharaoh's Sceptre (Jalsavrah) / Fairy Ring (CKR)");
        map.put("Menaphos", "Pharaoh's Sceptre (Jalsavrah) / Camulet");
        map.put("Bedabin Camp", "Camulet (Enakhra) / Fairy Ring (BIQ)");
        map.put("Bandit Camp (Desert)", "Bandit Camp Scroll / Camulet / Fairy Ring (BIQ)");
        map.put("Giants' Foundry", "Minigame Teleport (Giants' Foundry) / Ring of Dueling");
        map.put("Necropolis", "Pharaoh's Sceptre (Jal-Niz-Klar) / Fairy Ring (AKP)");

        // Kandarin & Western Provinces
        map.put("East Ardougne", "Ardougne Teleport / Ardougne Cloak / Spirit Tree");
        map.put("West Ardougne", "West Ardougne Tablet / Ardougne Teleport");
        map.put("Catherby", "Camelot Teleport / Catherby Portal / Fairy Ring (CJR)");
        map.put("Seers' Village", "Camelot Teleport (Bank toggle) / Fairy Ring (CJR)");
        map.put("Yanille", "Watchtower Teleport (Yanille toggle) / Fairy Ring (CIQ)");
        map.put("Wizards' Guild", "Watchtower Teleport / Minigame (NMZ) / Fairy Ring (CIQ)");
        map.put("Ranging Guild", "Skills Necklace (Ranging Guild)");
        map.put("Fishing Guild", "Skills Necklace (Fishing Guild) / Fairy Ring (ALA)");
        map.put("Grand Tree / Gnome Stronghold", "Royal Seed Pod / Spirit Tree (Grand Tree)");
        map.put("Tree Gnome Village", "Spirit Tree (Tree Gnome Village) / Royal Seed Pod");
        map.put("Port Khazard", "Minigame Teleport (Trawler) / Lunar Teleport (Khazard)");
        map.put("Hemenster", "Skills Necklace (Fishing / Ranging Guild)");
        map.put("Witchaven", "Ardougne Cloak (Monastery) / Fairy Ring (BLR)");
        map.put("Baxtorian Falls", "Games Necklace (Barbarian Outpost) / Fairy Ring (BJS)");
        map.put("Corsair Cove", "Corsair Cove Scroll / Mythical Cape / Charter Ship");
        map.put("Myth's Guild", "Mythical Cape / Spirit Tree (Feldip Hills)");

        // Fremennik Province & Islands
        map.put("Rellekka", "Fremennik Sea Boots / Waterbirth Teleport / Fairy Ring (DKS)");
        map.put("Jatizso", "Enchanted Lyre (Jatizso) / Boat from Rellekka");
        map.put("Neitiznot", "Enchanted Lyre (Neitiznot) / Boat from Rellekka");
        map.put("Miscellania", "Ring of Wealth (Miscellania) / Fairy Ring (CIP)");
        map.put("Lunar Isle", "Moonclan Teleport / Portal Nexus / Fairy Ring (CKS)");
        map.put("Weiss", "Icy Basalt (Weiss Salt Portal) / Fairy Ring (DKS)");
        map.put("Keldagrim", "Fairy Ring (DKS) / Minecart Network (GE)");
        map.put("Blast Furnace", "Minigame Teleport (Blast Furnace) / Minecart (GE)");

        // Tirannwn (Elven Lands)
        map.put("Prifddinas", "Enhanced Crystal Teleport Seed / Spirit Tree");
        map.put("Lletya", "Teleport Crystal (Lletya)");
        map.put("Zul-Andra", "Zul-Andra Teleport Scroll / Fairy Ring (BJS)");
        map.put("Tyras Camp", "Charter Ship (Port Tyras) / Fairy Ring (BKP)");

        // Fairy / Rift / Other Realms
        map.put("Zanaris", "Fairy Ring (Any code) / Dramen Staff in Lumbridge Shed");
        map.put("Guardians of the Rift", "Minigame Teleport (GotR) / Glory (Al Kharid -> Zamorak)");

        // Morytania & Harmony Island
        map.put("Canifis", "Kharyrll Teleport (Canifis) / Fairy Ring (CKS / BKR)");
        map.put("Port Phasmatys", "Ectophial (Ectofuntus Altar) / Fairy Ring (ALQ)");
        map.put("Burgh de Rott", "Drakkan's Medallion (Burgh de Rott) / Fairy Ring (BKR)");
        map.put("Mort'ton", "Minigame Teleport (Shades of Mort'ton) / Drakkan's Medallion");
        map.put("Slepe", "Drakkan's Medallion (Slepe) / Slepe Tablet");
        map.put("Darkmeyer", "Drakkan's Medallion (Darkmeyer)");
        map.put("Mos Le'Harmless", "Mos Le'Harmless Scroll / Fairy Ring (DIP)");

        // Karamja & TzHaar
        map.put("Brimhaven", "House Teleport (Brimhaven Portal) / Fairy Ring (BJR)");
        map.put("Tai Bwo Wannai", "Tai Bwo Wannai Scroll / Fairy Ring (CKR)");
        map.put("Shilo Village", "Karamja Gloves 3+ (Gem Mine) / Fairy Ring (CKR)");
        map.put("Mor Ul Rek / TzHaar", "Minigame Teleport (Fight Caves) / Fairy Ring (BLP)");
        map.put("Ape Atoll", "Ape Atoll Teleport / Royal Seed Pod");

        // Great Kourend & Kebos Lowlands
        map.put("Hosidius", "Xeric's Talisman (Glade) / Hosidius Tablet / Spirit Tree");
        map.put("Shayzien", "Xeric's Talisman (Lookout) / Shayzien Tablet / Fairy Ring (DJR)");
        map.put("Lovakengj", "Xeric's Talisman (Inferno) / Lovakengj Tablet");
        map.put("Arceuus", "Arceuus Library Tablet / Xeric's Talisman (Heart) / Fairy Ring (CIS)");
        map.put("Port Piscarilius", "Port Piscarilius Tablet / Book of the Dead");
        map.put("Farming Guild", "Skills Necklace (Farming Guild) / Fairy Ring (CIR)");
        map.put("Woodcutting Guild", "Skills Necklace (Woodcutting Guild) / Xeric's Talisman");
        map.put("Civitas illa Fortis", "Civitas illa Fortis Teleport / Quetzal Transport");
        map.put("Hunters' Guild", "Quetzal Whistle / Hunter Cape / Quetzal Transport");
        map.put("Aldarin", "Quetzal Transport / Boat from Sunset Coast");
        map.put("Cam Torum", "Quetzal / Cam Torum Teleport Tablet / Colosseum");
        map.put("Sunset Coast", "Quetzal Transport (Sunset Coast) / Boat from Aldarin");

        // Wilderness & Outposts
        map.put("Ferox Enclave", "Ring of Dueling (Ferox Enclave) / Portal Nexus");
        map.put("Mage Arena", "Edgeville / Ardougne Lever (Deserted Keep)");
        map.put("Bandit Camp (Wilderness)", "Ghorrock Teleport Tablet / Wilderness Obelisk");
        map.put("Void Knights' Outpost", "Minigame Teleport (Pest Control) / Boat from Port Sarim");

        TELEPORT_MAP = Collections.unmodifiableMap(map);

        Map<String, String> primary = new HashMap<>();

        // Misthalin & Lumbridge / Varrock
        primary.put("Lumbridge", "Lumbridge Teleport");
        primary.put("Varrock", "Varrock Teleport");
        primary.put("Grand Exchange", "Ring of Wealth (GE)");
        primary.put("Edgeville", "Glory (Edgeville)");
        primary.put("Draynor Village", "Glory (Draynor)");
        primary.put("Barbarian Village", "Skull Sceptre");
        primary.put("Dorgesh-Kaan", "Dorgesh-Kaan Sphere");
        primary.put("Digsite", "Digsite Pendant");
        primary.put("Exam Centre", "Digsite (Exam Centre)");
        primary.put("Paterdomus", "Salve Graveyard Tab");
        primary.put("Fossil Island", "Digsite (House on Hill)");
        primary.put("Cooks' Guild", "Skills (Cooking Guild)");
        primary.put("Champions' Guild", "Combat Bracelet");

        // Asgarnia
        primary.put("Falador", "Falador Teleport");
        primary.put("Port Sarim", "Explorer's Ring 3+");
        primary.put("Rimmington", "House (Rimmington)");
        primary.put("Taverley", "Taverley Tab");
        primary.put("Burthorpe", "Games (Burthorpe)");
        primary.put("Warriors' Guild", "Combat (Warriors' Gld)");
        primary.put("Crafting Guild", "Skills (Crafting Guild)");
        primary.put("Mining Guild", "Skills (Mining Guild)");
        primary.put("Monastery (Asgarnia)", "Combat (Monastery)");

        // Kharidian Desert
        primary.put("Al Kharid", "Glory (Al Kharid)");
        primary.put("Shantay Pass", "Dueling (PvP Arena)");
        primary.put("Pollnivneach", "Pollnivneach Tab");
        primary.put("Nardah", "Desert Amulet 4");
        primary.put("Sophanem", "Sceptre (Jalsavrah)");
        primary.put("Menaphos", "Sceptre (Jalsavrah)");
        primary.put("Bedabin Camp", "Camulet (Enakhra)");
        primary.put("Bandit Camp (Desert)", "Bandit Camp Scroll");
        primary.put("Giants' Foundry", "Minigame (Foundry)");
        primary.put("Necropolis", "Sceptre (Jal-Niz)");

        // Kandarin & Western Provinces
        primary.put("East Ardougne", "Ardougne Teleport");
        primary.put("West Ardougne", "West Ardougne Tab");
        primary.put("Catherby", "Camelot Teleport");
        primary.put("Seers' Village", "Camelot (Seers')");
        primary.put("Yanille", "Watchtower (Yanille)");
        primary.put("Wizards' Guild", "Watchtower Teleport");
        primary.put("Ranging Guild", "Skills (Ranging Guild)");
        primary.put("Fishing Guild", "Skills (Fishing Guild)");
        primary.put("Grand Tree / Gnome Stronghold", "Royal Seed Pod");
        primary.put("Tree Gnome Village", "Spirit Tree (Village)");
        primary.put("Port Khazard", "Minigame (Trawler)");
        primary.put("Hemenster", "Skills (Fishing)");
        primary.put("Witchaven", "Ardougne Cloak (Mon)");
        primary.put("Baxtorian Falls", "Games (Barbarian)");
        primary.put("Corsair Cove", "Corsair Cove Scroll");
        primary.put("Myth's Guild", "Mythical Cape");

        // Fremennik Province & Islands
        primary.put("Rellekka", "Fremennik Sea Boots");
        primary.put("Jatizso", "Lyre (Jatizso)");
        primary.put("Neitiznot", "Lyre (Neitiznot)");
        primary.put("Miscellania", "Wealth (Miscellania)");
        primary.put("Lunar Isle", "Moonclan Teleport");
        primary.put("Weiss", "Icy Basalt (Weiss)");
        primary.put("Keldagrim", "Fairy Ring (DKS)");
        primary.put("Blast Furnace", "Minigame (Blast Fdry)");

        // Tirannwn (Elven Lands)
        primary.put("Prifddinas", "Crystal Seed (Prif)");
        primary.put("Lletya", "Crystal Seed (Lletya)");
        primary.put("Zul-Andra", "Zul-Andra Scroll");
        primary.put("Tyras Camp", "Fairy Ring (BKP)");

        // Fairy / Rift / Other Realms
        primary.put("Zanaris", "Fairy Ring");
        primary.put("Guardians of the Rift", "Minigame (GotR)");

        // Morytania & Harmony Island
        primary.put("Canifis", "Kharyrll (Canifis)");
        primary.put("Port Phasmatys", "Ectophial");
        primary.put("Burgh de Rott", "Drakkan's (Burgh)");
        primary.put("Mort'ton", "Drakkan's (Burgh)");
        primary.put("Slepe", "Drakkan's (Slepe)");
        primary.put("Darkmeyer", "Drakkan's (Darkmeyer)");
        primary.put("Mos Le'Harmless", "Mos Le'Harmless Scroll");

        // Karamja & TzHaar
        primary.put("Brimhaven", "House (Brimhaven)");
        primary.put("Tai Bwo Wannai", "Tai Bwo Wannai Scroll");
        primary.put("Shilo Village", "Karamja Gloves 3+");
        primary.put("Mor Ul Rek / TzHaar", "Minigame (Fight Caves)");
        primary.put("Ape Atoll", "Ape Atoll Teleport");

        // Great Kourend & Kebos Lowlands
        primary.put("Hosidius", "Xeric's (Glade)");
        primary.put("Shayzien", "Xeric's (Lookout)");
        primary.put("Lovakengj", "Xeric's (Inferno)");
        primary.put("Arceuus", "Arceuus Library Tab");
        primary.put("Port Piscarilius", "Piscarilius Tab");
        primary.put("Farming Guild", "Skills (Farming Guild)");
        primary.put("Woodcutting Guild", "Skills (Woodcutting)");
        primary.put("Civitas illa Fortis", "Fortis Teleport");
        primary.put("Hunters' Guild", "Quetzal Whistle");
        primary.put("Aldarin", "Quetzal Transport");
        primary.put("Cam Torum", "Cam Torum Tab");
        primary.put("Sunset Coast", "Quetzal (Sunset)");

        // Wilderness & Outposts
        primary.put("Ferox Enclave", "Dueling (Ferox)");
        primary.put("Mage Arena", "Lever (Deserted Keep)");
        primary.put("Bandit Camp (Wilderness)", "Ghorrock Tab");
        primary.put("Void Knights' Outpost", "Minigame (Pest Cntrl)");

        PRIMARY_TELEPORT_MAP = Collections.unmodifiableMap(primary);
    }

    public static String getNearestTeleport(String settlement)
    {
        if (settlement == null) return null;
        for (Map.Entry<String, String> entry : TELEPORT_MAP.entrySet())
        {
            if (entry.getKey().equalsIgnoreCase(settlement) || settlement.toLowerCase().contains(entry.getKey().toLowerCase()))
            {
                return entry.getValue();
            }
        }
        return null;
    }

    public static String getPrimaryTeleport(String settlement)
    {
        if (settlement == null) return null;
        for (Map.Entry<String, String> entry : PRIMARY_TELEPORT_MAP.entrySet())
        {
            if (entry.getKey().equalsIgnoreCase(settlement) || settlement.toLowerCase().contains(entry.getKey().toLowerCase()))
            {
                return entry.getValue();
            }
        }

        String nearest = getNearestTeleport(settlement);
        if (nearest != null)
        {
            String[] parts = nearest.split("/");
            String first = parts[0].trim();
            if (first.length() > 23)
            {
                first = first.substring(0, 20) + "...";
            }
            return first;
        }
        return null;
    }

    private OsrsTeleportData() {}
}
