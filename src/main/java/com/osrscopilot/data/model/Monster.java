package com.osrscopilot.data.model;

import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Equality is <strong>id-only</strong> ({@link EqualsAndHashCode} below overrides the structural
 * equals/hashCode {@code @Value} would otherwise generate over the whole {@code drops} +
 * {@code spawnZones} lists). {@code MonsterDatabase} holds monsters in {@code HashSet}s and does
 * {@code contains()} checks on every debounced search keystroke; hashing ~44k drop rows per record
 * per keystroke was the measured cost. Two records only ever share an id when they are the same
 * logical monster, so id-only equality is safe as well as fast.
 */
@Value
@Builder
@EqualsAndHashCode(of = {"id"})
public class Monster
{
    int id;

    /** Every real in-game NPC id this monster answers to (aggressive / idle / HP-tier variants);
     *  empty for records that only have a synthetic key. {@code id} is always the representative. */
    java.util.List<Integer> npcIds;

    String name;
    int combatLevel;
    int hitpoints;
    int maxHit;
    String attackType;
    int attackSpeed;
    int slayerLevel;
    String questRequirement;
    boolean aggressive;
    boolean poisonous;
    boolean immuneToPoison;
    boolean members;
    String category; // Boss, Slayer, Dragon, Demon, Undead, Wilderness, F2P, Standard
    String wikiUrl;

    // Defensive bonuses
    int defenceStab;
    int defenceSlash;
    int defenceCrush;
    int defenceMagic;
    int defenceRanged;

    // Elemental weakness straight from the OSRS Wiki: "" when the monster has none (most don't),
    // else "<Element> (<pct>%)" e.g. "Fire (50%)". No longer a synthesised recommendation string.
    String weakness;

    // Comma-separated OSRS Wiki monster attributes ("demon", "undead,dragon", "leafy", ...).
    // Drives the bane-weapon hints (demonbane / Salve / dragonbane / keris / leaf-bladed).
    String attributes;

    // Direct OSRS Wiki image filename (e.g. "Cave horror (1).png" or "Vorkath.png")
    String wikiImage;

    // Encounter Explanation for non-world roaming / instanced / minigame / raid creatures
    String encounterType;

    // Spawn Locations / Zones
    List<MonsterSpawnZone> spawnZones;

    // Drops
    List<MonsterDrop> drops;

    /** Never null - an empty list when this record has no real NPC id. */
    public java.util.List<Integer> getNpcIds()
    {
        return npcIds != null ? npcIds : java.util.Collections.emptyList();
    }

    /** Typed view of the free-text {@link #attackType} ("Melee, Magic" -> MIXED, "N/a" -> NONE). */
    public CombatStyleClass getAttackStyleClass()
    {
        return CombatStyleClass.classify(attackType);
    }

    /** An elemental weakness is exploited with Magic; no recorded weakness -> NONE. */
    public CombatStyleClass getWeaknessStyleClass()
    {
        if (weakness == null || weakness.trim().isEmpty())
        {
            return CombatStyleClass.NONE;
        }
        String w = weakness.toLowerCase(java.util.Locale.ROOT);
        if (w.startsWith("air") || w.startsWith("water") || w.startsWith("earth") || w.startsWith("fire"))
        {
            return CombatStyleClass.MAGIC;
        }
        return CombatStyleClass.classify(weakness);
    }

    /** True if this monster carries the given OSRS Wiki attribute (e.g. "demon", "dragon"). */
    public boolean hasAttribute(String attr)
    {
        if (attributes == null || attr == null)
        {
            return false;
        }
        for (String a : attributes.toLowerCase(java.util.Locale.ROOT).split(","))
        {
            if (a.trim().equals(attr.toLowerCase(java.util.Locale.ROOT)))
            {
                return true;
            }
        }
        return false;
    }

    public boolean hasDrops()
    {
        return drops != null && !drops.isEmpty();
    }

    public boolean hasSpawnZones()
    {
        return spawnZones != null && !spawnZones.isEmpty();
    }

    public boolean hasQuestRequirement()
    {
        return questRequirement != null && !questRequirement.trim().isEmpty();
    }

    public boolean hasEncounterType()
    {
        return encounterType != null && !encounterType.trim().isEmpty();
    }
}
