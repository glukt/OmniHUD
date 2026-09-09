package com.osrscopilot.data.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

@Value
@Builder
public class Shop
{
    int id;
    String name;
    int npcId;
    String npcName;
    String town;
    String description;
    String questRequirement;
    int regionId;
    int worldX;
    int worldY;
    int worldPlane;
    int mapX;
    int mapY;
    boolean membersOnly;
    CurrencyType currency;
    List<ShopItem> items;
    List<String> tags;

    /**
     * Exact WorldPoint where the NPC stands in-game.
     */
    public WorldPoint getWorldPoint()
    {
        return new WorldPoint(worldX, worldY, worldPlane);
    }

    /**
     * Map-aligned WorldPoint directly centered over the shop building or native OSRS map symbol.
     * Falls back to the NPC location if no map coordinate override is defined.
     */
    public WorldPoint getMapLocation()
    {
        if (mapX > 0 && mapY > 0)
        {
            return new WorldPoint(mapX, mapY, worldPlane);
        }
        return getWorldPoint();
    }

    /**
     * Returns true if an explicit world map building/symbol coordinate is defined.
     */
    public boolean hasExplicitMapLocation()
    {
        return mapX > 0 && mapY > 0;
    }

    /**
     * Resolves the primary visual/filtering category of this shop.
     */
    public String getCategory()
    {
        if (tags != null && !tags.isEmpty())
        {
            for (String tag : tags)
            {
                if ("magic".equalsIgnoreCase(tag)) return "magic";
                if ("melee".equalsIgnoreCase(tag)) return "melee";
                if ("archery".equalsIgnoreCase(tag)) return "archery";
                if ("food".equalsIgnoreCase(tag)) return "food";
                if ("herblore".equalsIgnoreCase(tag)) return "herblore";
                if ("crafting".equalsIgnoreCase(tag)) return "crafting";
                if ("general".equalsIgnoreCase(tag)) return "general";
            }
            return tags.get(0);
        }

        if (name != null)
        {
            String lower = name.toLowerCase();
            if (lower.contains("rune") || lower.contains("magic") || lower.contains("staff") || lower.contains("wizard")) return "magic";
            if (lower.contains("sword") || lower.contains("armour") || lower.contains("armor") || lower.contains("shield") || lower.contains("scimitar") || lower.contains("axe") || lower.contains("mace")) return "melee";
            if (lower.contains("archery") || lower.contains("bow") || lower.contains("arrow") || lower.contains("range")) return "archery";
            if (lower.contains("food") || lower.contains("fish") || lower.contains("bake") || lower.contains("pub") || lower.contains("inn")) return "food";
            if (lower.contains("herb") || lower.contains("farm") || lower.contains("seed") || lower.contains("apothecary")) return "herblore";
            if (lower.contains("craft") || lower.contains("gem") || lower.contains("clothes") || lower.contains("silk")) return "crafting";
        }
        return "general";
    }
}
