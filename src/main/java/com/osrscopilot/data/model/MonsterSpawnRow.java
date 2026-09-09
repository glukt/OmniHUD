package com.osrscopilot.data.model;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

@Value
public class MonsterSpawnRow
{
    Monster monster;
    MonsterSpawnZone zone;

    public MonsterSpawnRow(Monster monster, MonsterSpawnZone zone)
    {
        this.monster = monster;
        this.zone = zone;
    }

    public boolean hasZone()
    {
        return zone != null;
    }

    public String getMonsterName()
    {
        return monster != null ? monster.getName() : "Unknown Monster";
    }

    public int getCombatLevel()
    {
        return monster != null ? monster.getCombatLevel() : 0;
    }

    public int getHitpoints()
    {
        return monster != null ? monster.getHitpoints() : 0;
    }

    public int getMaxHit()
    {
        return monster != null ? monster.getMaxHit() : 0;
    }

    public String getAttackType()
    {
        return monster != null ? monster.getAttackType() : "Melee";
    }

    public int getAttackSpeed()
    {
        return monster != null ? monster.getAttackSpeed() : 4;
    }

    public int getSlayerLevel()
    {
        return monster != null ? monster.getSlayerLevel() : 0;
    }

    public boolean isMembers()
    {
        return monster != null && monster.isMembers();
    }

    public String getCategory()
    {
        return monster != null ? monster.getCategory() : "Standard";
    }

    public String getWeakness()
    {
        return monster != null ? monster.getWeakness() : "None";
    }

    public String getZoneName()
    {
        return zone != null ? zone.getZoneName() : "Unknown / Unspecified";
    }

    public String getLocationName()
    {
        if (zone == null)
        {
            return "Gielinor";
        }
        if (zone.getLocationName() != null && !zone.getLocationName().isEmpty())
        {
            return zone.getLocationName();
        }
        if (zone.getDungeonName() != null && !zone.getDungeonName().isEmpty())
        {
            return zone.getDungeonName();
        }
        return "Gielinor";
    }

    public String getDungeonName()
    {
        return zone != null ? zone.getDungeonName() : null;
    }

    public int getSpawnCount()
    {
        return zone != null ? zone.getSpawnCount() : 0;
    }

    public boolean isMultiCombat()
    {
        return zone != null && zone.isMultiCombat();
    }

    public int getWildernessLevel()
    {
        return zone != null ? zone.getWildernessLevel() : 0;
    }

    public int getPlane()
    {
        return zone != null ? zone.getPlane() : 0;
    }

    public WorldPoint getWorldPoint()
    {
        return zone != null ? zone.getEffectiveFocusPoint() : null;
    }

    public boolean isAggressive()
    {
        return monster != null && monster.isAggressive();
    }

    public boolean isPoisonous()
    {
        return monster != null && monster.isPoisonous();
    }

    public boolean isImmuneToPoison()
    {
        return monster != null && monster.isImmuneToPoison();
    }

    public String getWikiUrl()
    {
        return monster != null ? monster.getWikiUrl() : null;
    }
}
