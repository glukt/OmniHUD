package com.osrscopilot.combat.engine;

import com.osrscopilot.combat.model.CombatStyle;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;

/**
 * High-precision resolver for OSRS active spells, manual casts, powered staves,
 * and special attack names.
 */
@Slf4j
@Singleton
public class SpellAttackResolver
{
    private final Client client;

    // Pattern to extract targeted spell names from a menu target. The spell name is the first
    // colour-tagged run, e.g. "<col=00ffff>Ice Barrage</col><col=ffffff> -> <col=ffff00>Goblin".
    // Some clients omit the closing </col>, so stop at the next '<' rather than requiring it.
    private static final Pattern TARGET_SPELL_PATTERN = Pattern.compile("^<col=[0-9a-fA-F]+>([^<]+)");

    // A manual spellcast projectile can land a few ticks after the "Cast" click (travel time), so
    // the remembered cast stays valid for a short window rather than only the click tick.
    private static final int MANUAL_CAST_WINDOW_TICKS = 5;

    private String lastManualCastSpell = null;
    private int manualCastTick = -1;
    private int lastSpecialAttackEnergy = 1000;
    private boolean specialAttackArmed = false;
    private int lastSpecialAttackTick = -1;

    // Map Varbits.AUTO_CAST_SPELL values to real spell names
    private static final Map<Integer, String> AUTOCAST_SPELLS = new HashMap<>();
    static
    {
        // Standard Spellbook
        AUTOCAST_SPELLS.put(1, "Wind Strike");
        AUTOCAST_SPELLS.put(2, "Water Strike");
        AUTOCAST_SPELLS.put(3, "Earth Strike");
        AUTOCAST_SPELLS.put(4, "Fire Strike");
        AUTOCAST_SPELLS.put(5, "Wind Bolt");
        AUTOCAST_SPELLS.put(6, "Water Bolt");
        AUTOCAST_SPELLS.put(7, "Earth Bolt");
        AUTOCAST_SPELLS.put(8, "Fire Bolt");
        AUTOCAST_SPELLS.put(9, "Wind Blast");
        AUTOCAST_SPELLS.put(10, "Water Blast");
        AUTOCAST_SPELLS.put(11, "Earth Blast");
        AUTOCAST_SPELLS.put(12, "Fire Blast");
        AUTOCAST_SPELLS.put(13, "Wind Wave");
        AUTOCAST_SPELLS.put(14, "Water Wave");
        AUTOCAST_SPELLS.put(15, "Earth Wave");
        AUTOCAST_SPELLS.put(16, "Fire Wave");
        AUTOCAST_SPELLS.put(17, "Crumble Undead");
        AUTOCAST_SPELLS.put(18, "Iban Blast");
        AUTOCAST_SPELLS.put(19, "Magic Dart");
        AUTOCAST_SPELLS.put(20, "Claws of Guthix");
        AUTOCAST_SPELLS.put(21, "Flames of Zamorak");
        AUTOCAST_SPELLS.put(22, "Saradomin Strike");
        AUTOCAST_SPELLS.put(48, "Wind Surge");
        AUTOCAST_SPELLS.put(49, "Water Surge");
        AUTOCAST_SPELLS.put(50, "Earth Surge");
        AUTOCAST_SPELLS.put(51, "Fire Surge");

        // Ancient Magicks
        AUTOCAST_SPELLS.put(23, "Smoke Rush");
        AUTOCAST_SPELLS.put(24, "Shadow Rush");
        AUTOCAST_SPELLS.put(25, "Blood Rush");
        AUTOCAST_SPELLS.put(26, "Ice Rush");
        AUTOCAST_SPELLS.put(27, "Smoke Burst");
        AUTOCAST_SPELLS.put(28, "Shadow Burst");
        AUTOCAST_SPELLS.put(29, "Blood Burst");
        AUTOCAST_SPELLS.put(30, "Ice Burst");
        AUTOCAST_SPELLS.put(31, "Smoke Blitz");
        AUTOCAST_SPELLS.put(32, "Shadow Blitz");
        AUTOCAST_SPELLS.put(33, "Blood Blitz");
        AUTOCAST_SPELLS.put(34, "Ice Blitz");
        AUTOCAST_SPELLS.put(35, "Smoke Barrage");
        AUTOCAST_SPELLS.put(36, "Shadow Barrage");
        AUTOCAST_SPELLS.put(37, "Blood Barrage");
        AUTOCAST_SPELLS.put(38, "Ice Barrage");

        // Arceuus Spells
        AUTOCAST_SPELLS.put(52, "Ghostly Grasp");
        AUTOCAST_SPELLS.put(53, "Skeletal Grasp");
        AUTOCAST_SPELLS.put(54, "Undead Grasp");
        AUTOCAST_SPELLS.put(55, "Inferior Demonbane");
        AUTOCAST_SPELLS.put(56, "Superior Demonbane");
        AUTOCAST_SPELLS.put(57, "Dark Demonbane");
    }

    // Known powered staves by item ID
    private static final Map<Integer, String> POWERED_STAVES = new HashMap<>();
    static
    {
        POWERED_STAVES.put(11905, "Trident of the seas");
        POWERED_STAVES.put(11907, "Trident of the seas");
        POWERED_STAVES.put(22288, "Trident of the seas (e)");
        POWERED_STAVES.put(12899, "Trident of the swamp");
        POWERED_STAVES.put(22292, "Trident of the swamp (e)");
        POWERED_STAVES.put(22323, "Sanguinesti staff");
        POWERED_STAVES.put(25731, "Holy sanguinesti staff");
        POWERED_STAVES.put(27275, "Tumeken's shadow");
        POWERED_STAVES.put(28583, "Warped sceptre");
        POWERED_STAVES.put(22552, "Thammaron's sceptre");
        POWERED_STAVES.put(27662, "Accursed sceptre (a)");
        POWERED_STAVES.put(27665, "Accursed sceptre");
        POWERED_STAVES.put(22516, "Dawnbringer");
        POWERED_STAVES.put(23852, "Crystal staff (basic)");
        POWERED_STAVES.put(23853, "Crystal staff (attuned)");
        POWERED_STAVES.put(23854, "Crystal staff (perfected)");
        POWERED_STAVES.put(23855, "Corrupted staff (perfected)");
    }

    @Inject
    public SpellAttackResolver(Client client)
    {
        this.client = client;
    }

    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (event == null) return;
        MenuAction action = event.getMenuAction();
        String option = event.getMenuOption();

        // 1. Targeted spellcast on an NPC / player: a manual "Cast" of a combat OR utility spell
        //    (Bind / Snare / Entangle, Ice Rush/Burst/Blitz/Barrage, the Arceuus grasps, ...).
        //    The spell name is the first colour-tagged run of the menu target.
        if ((action == MenuAction.WIDGET_TARGET_ON_NPC || action == MenuAction.WIDGET_TARGET_ON_PLAYER)
            && isCast(option))
        {
            String spell = extractSpellName(event.getMenuTarget());
            if (spell == null)
            {
                // Colour tags absent - fall back to the bare target text ("Snare -> Goblin").
                spell = stripTags(event.getMenuTarget());
            }
            rememberManualCast(spell);
        }
        // 2. "Cast" straight off the spellbook widget (no entity in the menu target yet).
        else if (action == MenuAction.CC_OP && isCast(option))
        {
            rememberManualCast(stripTags(event.getMenuTarget()));
        }
    }

    private static boolean isCast(String option)
    {
        return option != null && option.toLowerCase().contains("cast");
    }

    private void rememberManualCast(String spellName)
    {
        if (spellName == null || spellName.isEmpty())
        {
            return;
        }
        lastManualCastSpell = spellName;
        manualCastTick = client != null ? client.getTickCount() : 0;
    }

    /** First colour-tagged run of a menu target, e.g. "<col=00ffff>Snare</col> -> ..." -> "Snare". */
    private static String extractSpellName(String menuTarget)
    {
        if (menuTarget == null)
        {
            return null;
        }
        Matcher matcher = TARGET_SPELL_PATTERN.matcher(menuTarget);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /** Drop OSRS colour/format tags and any " -> target" suffix, leaving the bare spell name. */
    private static String stripTags(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        String s = raw.replaceAll("<[^>]*>", "");
        int arrow = s.indexOf("->");
        if (arrow >= 0)
        {
            s = s.substring(0, arrow);
        }
        return s.trim();
    }

    public void onVarbitChanged(VarbitChanged event)
    {
        if (event == null || client == null) return;
        if (event.getVarpId() == 300) // SA_ENERGY
        {
            int currentEnergy = event.getValue();
            if (currentEnergy < lastSpecialAttackEnergy)
            {
                specialAttackArmed = true;
                lastSpecialAttackTick = client.getTickCount();
            }
            lastSpecialAttackEnergy = currentEnergy;
        }
    }

    public String resolveAttackName(CombatStyle style, String fallbackWeaponName, int currentTick)
    {
        if (client != null)
        {
            // Check special attack varp (301 = Special attack active)
            try
            {
                if (client.getVarpValue(301) == 1 || specialAttackArmed)
                {
                    if (fallbackWeaponName != null)
                    {
                        if (fallbackWeaponName.toLowerCase().contains("dragon dagger")) return "Dragon dagger Spec";
                        if (fallbackWeaponName.toLowerCase().contains("dragon claws")) return "Dragon claws Spec";
                        if (fallbackWeaponName.toLowerCase().contains("voidwaker")) return "Voidwaker Spec";
                        return fallbackWeaponName + " Spec";
                    }
                }
            }
            catch (RuntimeException e)
            {
                log.debug("Special-attack varp read failed", e);
            }

            // Check powered staves
            if (style == CombatStyle.MAGIC && fallbackWeaponName != null)
            {
                String lower = fallbackWeaponName.toLowerCase();
                if (lower.contains("trident of the swamp")) return "Trident of the swamp";
                if (lower.contains("trident of the seas")) return "Trident of the seas";
                if (lower.contains("tumeken's shadow")) return "Tumeken's shadow";
                if (lower.contains("sanguinesti")) return "Sanguinesti staff";
                if (lower.contains("warped sceptre")) return "Warped sceptre";
            }

            // Check autocast varbit 276
            try
            {
                int autocast = client.getVarbitValue(276);
                if (autocast > 0 && AUTOCAST_SPELLS.containsKey(autocast))
                {
                    return AUTOCAST_SPELLS.get(autocast);
                }
            }
            catch (RuntimeException e)
            {
                log.debug("Autocast varbit read failed", e);
            }
        }

        return fallbackWeaponName != null ? fallbackWeaponName : "Attack";
    }

    /**
     * Resolves the real attack/spell/weapon name and combat style for the player's attack.
     */
    public AttackResolution resolveCurrentAttack(int currentTick)
    {
        int weaponId = getEquippedWeaponId();
        String defaultWeaponName = getEquippedWeaponName(weaponId);

        // 1. Special Attack
        if (specialAttackArmed || (lastSpecialAttackTick > 0 && currentTick - lastSpecialAttackTick <= 2))
        {
            specialAttackArmed = false;
            String specName = resolveSpecialAttackName(weaponId, defaultWeaponName);
            CombatStyle style = resolveWeaponStyle(weaponId);
            return new AttackResolution(specName, style, true);
        }

        // 2. Manual Spellcast - valid for a short window after the click so the spell's own
        //    projectile-travel delay doesn't drop it back to the equipped-weapon name.
        if (lastManualCastSpell != null
            && currentTick >= manualCastTick
            && currentTick - manualCastTick <= MANUAL_CAST_WINDOW_TICKS)
        {
            String spellName = lastManualCastSpell;
            lastManualCastSpell = null;
            return new AttackResolution(spellName, CombatStyle.MAGIC, false);
        }

        // 3. Powered Staff
        if (weaponId > 0 && POWERED_STAVES.containsKey(weaponId))
        {
            return new AttackResolution(POWERED_STAVES.get(weaponId), CombatStyle.MAGIC, false);
        }

        // 4. Autocast Spell
        if (client != null)
        {
            try
            {
                int autocastVal = client.getVarbitValue(276);
                if (autocastVal > 0 && AUTOCAST_SPELLS.containsKey(autocastVal))
                {
                    String spell = AUTOCAST_SPELLS.get(autocastVal);
                    int defMode = client.getVarbitValue(2668);
                    if (defMode == 1)
                    {
                        spell += " (Defensive)";
                    }
                    return new AttackResolution(spell, CombatStyle.MAGIC, false);
                }
            }
            catch (RuntimeException e)
            {
                log.debug("Autocast varbit read failed", e);
            }
        }

        // 5. Default Weapon Attack
        CombatStyle style = resolveWeaponStyle(weaponId);
        return new AttackResolution(defaultWeaponName, style, false);
    }

    private String resolveSpecialAttackName(int weaponId, String fallback)
    {
        if (fallback != null)
        {
            String lower = fallback.toLowerCase();
            if (lower.contains("dragon dagger")) return "Dragon dagger (Special)";
            if (lower.contains("dragon claws")) return "Dragon claws (Special)";
            if (lower.contains("voidwaker")) return "Voidwaker (Special)";
            if (lower.contains("osmumten's fang") || lower.contains("fang")) return "Osmumten's fang (Special)";
            if (lower.contains("armadyl godsword")) return "Armadyl godsword (Special)";
            if (lower.contains("bandos godsword")) return "Bandos godsword (Special)";
            if (lower.contains("dragon warhammer")) return "Dragon warhammer (Special)";
            if (lower.contains("zaryte crossbow")) return "Zaryte crossbow (Special)";
            if (lower.contains("dark bow")) return "Dark bow (Special)";
            if (lower.contains("elder maul")) return "Elder maul (Special)";
            if (lower.contains("tonalztics")) return "Tonalztics of Ralos (Special)";
            if (lower.contains("heavy ballista") || lower.contains("light ballista")) return fallback + " (Special)";
            if (lower.contains("magic shortbow")) return fallback + " (Special)";
            if (lower.contains("blowpipe")) return "Toxic blowpipe (Special)";
            return fallback + " (Special)";
        }
        return "Special Attack";
    }

    private CombatStyle resolveWeaponStyle(int weaponId)
    {
        if (weaponId <= 0 || client == null) return CombatStyle.MELEE;
        try
        {
            ItemComposition comp = client.getItemDefinition(weaponId);
            if (comp == null || comp.getName() == null) return CombatStyle.MELEE;
            String name = comp.getName().toLowerCase();

            if (name.contains("bow") || name.contains("blowpipe") || name.contains("chinchompa")
                || name.contains("ballista") || name.contains("knife") || name.contains("dart")
                || name.contains("crossbow") || name.contains("javelin"))
            {
                return CombatStyle.RANGED;
            }
            if (name.contains("staff") || name.contains("wand") || name.contains("shadow")
                || name.contains("sceptre") || name.contains("trident"))
            {
                return CombatStyle.MAGIC;
            }
            return CombatStyle.MELEE;
        }
        catch (Exception e)
        {
            return CombatStyle.MELEE;
        }
    }

    private int getEquippedWeaponId()
    {
        if (client == null) return -1;
        try
        {
            ItemContainer eq = client.getItemContainer(InventoryID.EQUIPMENT);
            if (eq != null)
            {
                Item weapon = eq.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
                if (weapon != null) return weapon.getId();
            }
        }
        catch (RuntimeException e)
        {
            log.debug("Equipped-weapon lookup failed", e);
        }
        return -1;
    }

    private String getEquippedWeaponName(int weaponId)
    {
        if (weaponId <= 0 || client == null) return "Unarmed";
        try
        {
            ItemComposition comp = client.getItemDefinition(weaponId);
            if (comp != null && comp.getName() != null)
            {
                return comp.getName();
            }
        }
        catch (RuntimeException e)
        {
            log.debug("Weapon-name lookup failed", e);
        }
        return "Weapon (" + weaponId + ")";
    }

    public static class AttackResolution
    {
        private final String attackName;
        private final CombatStyle style;
        private final boolean isSpecial;

        public AttackResolution(String attackName, CombatStyle style, boolean isSpecial)
        {
            this.attackName = attackName;
            this.style = style;
            this.isSpecial = isSpecial;
        }

        public String getAttackName()
        {
            return attackName;
        }

        public CombatStyle getStyle()
        {
            return style;
        }

        public boolean isSpecial()
        {
            return isSpecial;
        }
    }
}
