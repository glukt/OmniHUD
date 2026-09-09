package com.osrscopilot.map;

import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.ShopItem;
import com.osrscopilot.data.model.TownNode;

public final class TooltipFormatter
{
    private TooltipFormatter() {}

    public static String formatTownTooltip(TownNode town)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(town.getName());
        if (town.getDescription() != null && !town.getDescription().isEmpty())
        {
            sb.append(" - ").append(town.getDescription());
        }
        return sb.toString();
    }

    public static String formatShopBriefTooltip(Shop shop)
    {
        return shop.getName() + " (" + shop.getTown() + ") - " + shop.getNpcName() + " [" + (shop.isMembersOnly() ? "Members" : "F2P") + "]";
    }

    public static String formatShopDetailedTooltip(Shop shop)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(shop.getName()).append(" (").append(shop.getTown()).append(")\n");
        sb.append("Keeper: ").append(shop.getNpcName())
          .append(" | ").append(shop.isMembersOnly() ? "Members" : "F2P")
          .append(" | Currency: ").append(shop.getCurrency().getFullName());

        if (shop.getItems() != null && !shop.getItems().isEmpty())
        {
            sb.append("\n\nStock:");
            int displayCount = Math.min(shop.getItems().size(), 6);
            for (int i = 0; i < displayCount; i++)
            {
                ShopItem item = shop.getItems().get(i);
                sb.append("\n• ")
                  .append(item.getName())
                  .append(" x").append(item.getDefaultStock())
                  .append(" (").append(item.getPrice()).append(" ").append(shop.getCurrency().getShortName()).append(")");
            }
            if (shop.getItems().size() > displayCount)
            {
                sb.append("\n...and ").append(shop.getItems().size() - displayCount).append(" more");
            }
        }

        return sb.toString();
    }

    /** Hard cap on a single tooltip line - RuneLite's tooltip box does not wrap, so an unbounded
     *  line runs off the canvas edge. Trims on a word boundary where it can. */
    private static String clip(String s, int max)
    {
        if (s == null)
        {
            return "";
        }
        s = s.trim();
        if (s.length() <= max)
        {
            return s;
        }
        String head = s.substring(0, max - 2);
        int lastSpace = head.lastIndexOf(' ');
        if (lastSpace >= max - 9)
        {
            head = head.substring(0, lastSpace);
        }
        return head + "..";
    }

    public static String formatShopMinimapTooltip(Shop shop)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<col=ff981f>").append(clip(shop.getName() != null ? shop.getName() : "Shop", 34)).append("</col></br>");

        String town = shop.getTown() != null ? shop.getTown() : "";
        String tenure = shop.isMembersOnly() ? "Members" : "F2P";
        sb.append("<col=a0a0a0>").append(town.isEmpty() ? tenure : clip(town, 24) + " · " + tenure).append("</col></br>");

        int total = shop.getItems() != null ? shop.getItems().size() : 0;
        if (total > 0)
        {
            String first = clip(shop.getItems().get(0).getName(), 18);
            sb.append("<col=ffd700>").append(total).append(total == 1 ? " item" : " items");
            sb.append(" · ").append(first);
            if (total > 1)
            {
                sb.append(" +").append(total - 1);
            }
            sb.append("</col></br>");
        }

        if (shop.getQuestRequirement() != null && !shop.getQuestRequirement().isEmpty())
        {
            sb.append("<col=c38cff>Locked: ").append(clip(shop.getQuestRequirement(), 26)).append("</col></br>");
        }

        sb.append("<col=90caf9>Left-click to open in side panel</col>");
        return sb.toString();
    }

    public static String formatMinimapToggleTooltip(boolean enabled)
    {
        if (enabled)
        {
            return "<col=ff981f>Vendor Minimap Icons:</col> <col=4ade80>ON</col><br><col=90caf9>Click to toggle off</col>";
        }
        else
        {
            return "<col=ff981f>Vendor Minimap Icons:</col> <col=f87171>OFF</col><br><col=90caf9>Click to toggle on</col>";
        }
    }

    public static String formatMonsterMinimapTooltip(com.osrscopilot.data.model.Monster monster, com.osrscopilot.data.model.MonsterSpawnZone zone)
    {
        StringBuilder sb = new StringBuilder();
        String name = monster != null ? monster.getName() : "Monster";
        int combat = monster != null ? monster.getCombatLevel() : 0;
        sb.append("<col=ff981f>").append(name).append(combat > 0 ? " (Level " + combat + ")" : "").append("</col></br>");

        sb.append("<col=a0a0a0>Spawn: ");
        if (zone != null && zone.getLocationName() != null && !zone.getLocationName().isEmpty())
        {
            sb.append(zone.getLocationName());
        }
        else if (zone != null && zone.getZoneName() != null && !zone.getZoneName().isEmpty())
        {
            sb.append(zone.getZoneName());
        }
        else
        {
            sb.append(monster != null && monster.getCategory() != null ? monster.getCategory() : "Nearby");
        }
        if (zone != null && zone.getSpawnCount() > 0)
        {
            sb.append(" (x").append(zone.getSpawnCount()).append(")");
        }
        sb.append("</col></br>");

        sb.append("<col=ffd700>Combat: ");
        if (zone != null && zone.isMultiCombat())
        {
            sb.append("⚔ Multi-Combat");
        }
        else
        {
            sb.append("Single-Combat");
        }
        if (zone != null && zone.getWildernessLevel() > 0)
        {
            sb.append(" | ☠ Wildy Lv.").append(zone.getWildernessLevel());
        }
        sb.append("</col></br>");

        sb.append("<col=90caf9>Right-click to open in side panel</col>");
        return sb.toString();
    }

    public static String formatMonsterMinimapToggleTooltip(boolean enabled)
    {
        if (enabled)
        {
            return "<col=ff981f>Monster Minimap Icons:</col> <col=4ade80>ON</col><br><col=90caf9>Click to toggle off</col>";
        }
        else
        {
            return "<col=ff981f>Monster Minimap Icons:</col> <col=f87171>OFF</col><br><col=90caf9>Click to toggle on</col>";
        }
    }
}
