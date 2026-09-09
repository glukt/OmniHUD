package com.osrscopilot.combat.tutorial;

import com.osrscopilot.combat.CombatFormat;
import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.model.CombatStyle;
import com.osrscopilot.combat.model.CombatTimelineEvent;
import com.osrscopilot.combat.model.EncounterSegment;
import com.osrscopilot.combat.model.EntityCombatStats;
import com.osrscopilot.combat.model.SegmentStatus;
import com.osrscopilot.combat.model.SegmentType;
import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Staged combat data for the interactive HUD tutorial. Everything here is a plain, standalone
 * {@link EncounterSegment} built with the same {@code record*} calls the live path uses - it never
 * reads or writes {@link CombatEncounterManager} state, config, or the loot tracker. Build it, hand
 * it to {@link CombatEncounterManager#setDemoOverride}, throw it away when the tour ends.
 *
 * <p>The scenario: a small God Wars Dungeon team (you + two others) ~48 seconds into a General
 * Graardor kill at 57% HP. You are meleeing with an Osmumten's fang and speccing a Dragon
 * warhammer, with a cannon and a Bloodfiend thrall adding to your total, two brews and a prayer
 * potion drunk.
 */
public final class CombatTutorialFixture
{
    public static final String BOSS_NAME = "General Graardor";
    private static final int GRAARDOR_NPC_ID = 2215;
    private static final int GRAARDOR_COMBAT_LEVEL = 624;
    private static final String MAIN_WEAPON = "Osmumten's fang";

    private CombatTutorialFixture() {}

    /** The live "current fight" the whole HUD renders during the tour. A fresh instance each call. */
    public static EncounterSegment sampleEncounter()
    {
        EncounterSegment seg = new EncounterSegment(UUID.randomUUID(), BOSS_NAME, SegmentType.ENCOUNTER, 0, "You");
        seg.setNpcInfo(GRAARDOR_NPC_ID, GRAARDOR_COMBAT_LEVEL);
        seg.setStartTimestamp(Instant.now().minusSeconds(48));
        seg.setStatus(SegmentStatus.IN_PROGRESS);

        // --- You: fang swings, a few misses, three Dragon warhammer specs, some hits taken --------
        EntityCombatStats you = seg.getLocalPlayerStats();
        you.setDominantStyle(CombatStyle.MELEE);
        int[] rolls = {
            41, 0, 38, 44, 40, 0, 43, 39, 45, 42, 37, 0, 44, 41, 40, 43, 38, 45, 0, 42,
            39, 44, 41, 0, 43, 40, 38, 45, 42, 0, 44, 39, 41, 43, 40, 0, 45, 38, 42, 44,
        };
        int tick = 0;
        for (int i = 0; i < rolls.length; i++)
        {
            tick += 3;
            int dmg = rolls[i];
            if (dmg == 0)
            {
                you.recordMiss(CombatStyle.MELEE, MAIN_WEAPON, false);
            }
            else
            {
                you.recordDamageDealt(CombatStyle.MELEE, dmg, MAIN_WEAPON, tick, false, BOSS_NAME);
            }
            you.recordTimeSeriesSecond(i + 1, Math.max(0, dmg),
                (i % 9 == 4) ? 12 : 0, (i % 13 == 6) ? 22 : 0, CombatStyle.MELEE, null);
        }
        for (int spec : new int[]{31, 27, 34})
        {
            tick += 3;
            you.recordDamageDealt(CombatStyle.MELEE, spec, "Dragon warhammer", tick, true, BOSS_NAME);
            you.recordSpecialAttack(); // damage accrues via recordDamageDealt(special=true)
        }
        you.recordDamageTaken(CombatStyle.MELEE, 26, BOSS_NAME, 20);
        you.recordDamageTaken(CombatStyle.RANGED, 14, BOSS_NAME, 44);
        you.recordDamageTaken(CombatStyle.RANGED, 9, "Sergeant Grimspike", 30);
        you.recordAttackCycle(70, 10); // ~88% attack uptime
        you.setHpHealed(60);
        you.setPotionsDrunkCount(2);
        you.setFoodEatenCount(1);
        you.recordConsumable(6685, "Saradomin brew(4)", 2, 22_000);
        you.recordConsumable(2434, "Prayer potion(4)", 1, 9_000);

        // Your cannon and thrall are your own output - fold them into your total as their own source
        // rows (damage + a breakdown row, but never an attack attempt, so they don't move your hit
        // accuracy), not separate bars.
        for (int i = 0; i < 16; i++)
        {
            you.recordStatusDamage(CombatStyle.CANNON, 12 + (i % 5), "Dwarf multicannon", i * 6, BOSS_NAME);
        }
        for (int i = 0; i < 10; i++)
        {
            you.recordStatusDamage(CombatStyle.THRALL, 18 + (i % 4), "Bloodfiend thrall", i * 9, BOSS_NAME);
        }

        // Two team-mates, so the meter shows a real multi-combatant ranking.
        addPartyMember(seg, "Zaphira", CombatStyle.MELEE, "Scythe of vitur", 22, 33);
        addPartyMember(seg, "Kandarin", CombatStyle.RANGED, "Zaryte crossbow", 30, 21);

        // The Action Ledger renders EntityCombatStats#getTimelineEvents(); the record* calls above
        // never write those (only the live CombatEncounterManager path does), so stage a hand-picked
        // ~28-line stream for the same fight. Every number below is drawn from the arrays already
        // used above (fang rolls, the 31/27/34 specs, the 26/14/9 hits taken, the 12-16 cannon /
        // 18-21 thrall ticks) so the ledger reconciles with the totals - and addTimelineEvent is
        // display-only, so no total, accuracy or rate shifts.
        buildActionLedger(you);

        seg.updateDuration(80); // ~48s wall-clock -> durationSeconds on every sub-stat
        return seg;
    }

    /** Colours copied from the live ledger writers so the demo stream is visually identical. */
    private static final Color L_TAKEN = new Color(239, 83, 80);
    private static final Color L_SPEC = new Color(255, 202, 40);
    private static final Color L_DEBUFF = new Color(140, 190, 255);
    private static final Color L_CANNON = new Color(255, 167, 38);
    private static final Color L_THRALL = new Color(186, 104, 200);
    private static final Color L_POTION = new Color(77, 208, 225);
    private static final Color L_BUFF = new Color(150, 180, 210);
    private static final Color L_START = new Color(255, 183, 77);
    private static final Color L_HIT = CombatStyle.MELEE.getPrimaryColor();

    private static void buildActionLedger(EntityCombatStats you)
    {
        ledger(you, 0, "START", ">", "Engaged " + BOSS_NAME, L_START);
        ledger(you, 0, "BUFF", "~", "Engaged with: Piety, Protect from Melee, Overload", L_BUFF);
        hit(you, 3, 41);
        spec(you, 5, 31, "-30%");
        hit(you, 6, 38);
        taken(you, 8, 26, BOSS_NAME);
        cannon(you, 9, 14);
        hit(you, 11, 44);
        spec(you, 12, 27, "-51% total");
        thrall(you, 14, 20);
        hit(you, 15, 40);
        taken(you, 17, 9, "Sergeant Grimspike");
        ledger(you, 19, "POTION", "~", "Drank Saradomin brew(4)", L_POTION);
        hit(you, 21, 43);
        spec(you, 24, 34, "-66% total");
        hit(you, 26, 45);
        taken(you, 28, 14, BOSS_NAME);
        cannon(you, 30, 12);
        hit(you, 33, 39);
        ledger(you, 35, "POTION", "~", "Drank Prayer potion(4)", L_POTION);
        hit(you, 37, 42);
        thrall(you, 40, 18);
        hit(you, 43, 44);
        ledger(you, 46, "POTION", "~", "Drank Saradomin brew(4)", L_POTION);
        hit(you, 47, 37);
    }

    private static void hit(EntityCombatStats s, int sec, int dmg)
    {
        ledger(s, sec, "HIT", "+", "Dealt " + dmg + " (" + MAIN_WEAPON + ")", L_HIT, dmg);
    }

    private static void spec(EntityCombatStats s, int sec, int dmg, String drain)
    {
        ledger(s, sec, "SPEC", "!", "Special: " + dmg + " (Dragon warhammer)", L_SPEC, dmg);
        ledger(s, sec, "DEBUFF", "*", BOSS_NAME + " Defence " + drain + " (Dragon warhammer)", L_DEBUFF);
    }

    private static void taken(EntityCombatStats s, int sec, int dmg, String from)
    {
        ledger(s, sec, "TAKEN", "v", "Took " + dmg + " (" + from + ")", L_TAKEN, dmg);
    }

    private static void cannon(EntityCombatStats s, int sec, int dmg)
    {
        ledger(s, sec, "DOT", "C", "Dwarf multicannon " + dmg, L_CANNON, dmg);
    }

    private static void thrall(EntityCombatStats s, int sec, int dmg)
    {
        ledger(s, sec, "DOT", "T", "Bloodfiend thrall " + dmg, L_THRALL, dmg);
    }

    private static void ledger(EntityCombatStats s, int sec, String type, String icon, String desc, Color color)
    {
        ledger(s, sec, type, icon, desc, color, CombatTimelineEvent.NO_AMOUNT);
    }

    private static void ledger(EntityCombatStats s, int sec, String type, String icon, String desc, Color color, int amount)
    {
        s.addTimelineEvent(CombatTimelineEvent.builder()
            .clientTick((int) Math.round(sec / 0.6))
            .timeFormatted(CombatFormat.duration(sec))
            .eventType(type).icon(icon).description(desc).color(color).amount(amount)
            .source("You").build());
    }

    /** HP 57%, ~72s to kill, ~31 kills/hr on current pace - the footer boss-progress line. */
    public static CombatEncounterManager.LiveBossProgress sampleBossProgress()
    {
        return new CombatEncounterManager.LiveBossProgress(BOSS_NAME, 0.57, 72, 31.0, true);
    }

    /**
     * The scope dropdown for the tour: the given live fight, faux Current Session / Total rows, a
     * merged "Bloodveld x21" trash row (expandable into per-kill children), and two past Graardor
     * kills. {@code live} is placed as row 0 by identity so "select this row" is a no-op.
     */
    public static List<EncounterSegment> dropdownFor(EncounterSegment live)
    {
        List<EncounterSegment> list = new ArrayList<>();
        list.add(live);
        list.add(sessionRow(SegmentType.SESSION_CURRENT, "Current Session", 214_000, 15_800, 1_230));
        list.add(sessionRow(SegmentType.SESSION_TOTAL, "Total", 4_820_000, 261_000, 33_400));
        list.add(mergedTrashRow());
        list.add(pastKill(3, 512));
        list.add(pastKill(2, 548));
        return list;
    }

    // --- helpers --------------------------------------------------------------------------------

    private static void addPartyMember(EncounterSegment seg, String name, CombatStyle style,
        String weapon, int hits, int hitDmg)
    {
        EntityCombatStats m = seg.getOtherParticipants().computeIfAbsent(name, EntityCombatStats::new);
        m.setDominantStyle(style);
        for (int i = 0; i < hits; i++)
        {
            if (i % 7 == 3)
            {
                m.recordMiss(style, weapon, false);
            }
            else
            {
                m.recordDamageDealt(style, hitDmg + (i % 5), weapon, i * 3, false, BOSS_NAME);
            }
        }
    }

    private static EncounterSegment sessionRow(SegmentType type, String title, int dmg, int taken, int combatSeconds)
    {
        EncounterSegment s = new EncounterSegment(UUID.randomUUID(), title, type, 0, "You");
        s.setStatus(SegmentStatus.IN_PROGRESS);
        EntityCombatStats p = s.getLocalPlayerStats();
        p.setTotalDamage(dmg);
        p.setDamageTaken(taken);
        p.setDurationSeconds(combatSeconds);
        s.addCombatSeconds(combatSeconds);
        return s;
    }

    private static EncounterSegment mergedTrashRow()
    {
        EncounterSegment row = bloodveldKill();
        for (int i = 0; i < 20; i++)
        {
            row.absorb(bloodveldKill());
        }
        row.setHistoryIndex(4);
        row.setStatus(SegmentStatus.COMPLETED);
        row.setEndTimestamp(Instant.now().minusSeconds(300));
        return row;
    }

    private static EncounterSegment bloodveldKill()
    {
        EncounterSegment k = new EncounterSegment(UUID.randomUUID(), "Bloodveld", SegmentType.ENCOUNTER, 0, "You");
        k.setStartTimestamp(Instant.now().minusSeconds(600));
        k.setEndTimestamp(Instant.now().minusSeconds(585));
        EntityCombatStats p = k.getLocalPlayerStats();
        for (int i = 0; i < 6; i++)
        {
            p.recordDamageDealt(CombatStyle.MELEE, 22 + (i % 4), "Blade of saeldor", i * 3, false, "Bloodveld");
        }
        p.recordDamageTaken(CombatStyle.MELEE, 4, "Bloodveld", 5);
        k.setStatus(SegmentStatus.COMPLETED);
        k.updateDuration(25);
        return k;
    }

    private static EncounterSegment pastKill(int historyIndex, int durationSeconds)
    {
        EncounterSegment k = new EncounterSegment(UUID.randomUUID(), BOSS_NAME, SegmentType.ENCOUNTER, 0, "You");
        k.setNpcInfo(GRAARDOR_NPC_ID, GRAARDOR_COMBAT_LEVEL);
        k.setStartTimestamp(Instant.now().minusSeconds(3_600));
        k.setEndTimestamp(Instant.now().minusSeconds(3_600 - durationSeconds));
        k.setStatus(SegmentStatus.COMPLETED);
        k.setHistoryIndex(historyIndex);
        EntityCombatStats p = k.getLocalPlayerStats();
        for (int i = 0; i < durationSeconds / 3; i++)
        {
            p.recordDamageDealt(CombatStyle.RANGED, 40, MAIN_WEAPON, i * 3, false, BOSS_NAME);
        }
        k.updateDuration(durationSeconds * 5 / 3);
        return k;
    }
}
