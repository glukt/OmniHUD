package com.osrscopilot.combat;

import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.model.CombatTimelineEvent;
import com.osrscopilot.combat.model.EncounterSegment;
import com.osrscopilot.combat.tutorial.CombatTutorialFixture;
import java.util.List;
import net.runelite.api.Client;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * P1 of the Combat HUD tutorial: the staged fixture, and the read-time demo override on
 * {@link CombatEncounterManager} that every combat view resolves its scope through. The override
 * must be a pure redirect - no live encounter, history or session/total state may change while it
 * is active, and clearing it must restore the exact live view.
 */
public class CombatTutorialFixtureTest
{
    private CombatEncounterManager newManager()
    {
        return new CombatEncounterManager(mock(Client.class));
    }

    @Test
    public void fixtureEncounterIsFullyPopulated()
    {
        EncounterSegment seg = CombatTutorialFixture.sampleEncounter();

        assertEquals(CombatTutorialFixture.BOSS_NAME, seg.getTargetName());
        assertTrue("player dealt damage", seg.getLocalPlayerStats().getTotalDamage() > 0);
        assertTrue("player took damage", seg.getLocalPlayerStats().getDamageTaken() > 0);
        assertTrue("specs recorded", seg.getLocalPlayerStats().getSpecialAttacksCount() == 3);
        assertFalse("weapon breakdown", seg.getLocalPlayerStats().getWeaponBreakdown().isEmpty());
        assertFalse("fight graph series", seg.getLocalPlayerStats().getTimeSeries().isEmpty());
        assertFalse("consumable ledger", seg.getLocalPlayerStats().getConsumablesUsed().isEmpty());

        // You + two party members show as bars; the cannon and thrall are folded into your total.
        assertEquals(3, seg.getRankedParticipants(6).size());
        assertEquals(2, seg.getOtherParticipants().size());
        assertEquals("cannon is not its own bar", 0, seg.getCannonStats().getTotalDamage());
        assertEquals("thrall is not its own bar", 0, seg.getThrallStats().getTotalDamage());

        // The folded-in cannon / thrall each get their own source row but are never counted as an
        // attack, so hit accuracy stays measured against your real swings only.
        java.util.Set<String> sources = new java.util.HashSet<>();
        seg.getLocalPlayerStats().getWeaponBreakdown().forEach(w -> sources.add(w.getWeaponName()));
        assertTrue("cannon folded into your sources", sources.contains("Dwarf multicannon"));
        assertTrue("thrall folded into your sources", sources.contains("Bloodfiend thrall"));
        assertTrue("spec weapon has its own row", sources.contains("Dragon warhammer"));
        double mainAccuracy = seg.getLocalPlayerStats().getWeaponBreakdown().stream()
            .filter(w -> "Osmumten's fang".equals(w.getWeaponName()))
            .findFirst().orElseThrow(AssertionError::new).getAccuracyPct();
        assertTrue("main-weapon accuracy is a real hit rate", mainAccuracy > 0 && mainAccuracy < 100);
    }

    @Test
    public void fixtureStagesAnActionLedgerConsistentWithItsTotals()
    {
        EncounterSegment seg = CombatTutorialFixture.sampleEncounter();
        List<CombatTimelineEvent> evs = seg.getLocalPlayerStats().getTimelineEvents();

        assertTrue("the Action Ledger card has a fight stream to render", evs.size() >= 20);
        assertTrue("opens with the engage line",
            evs.stream().anyMatch(e -> "START".equals(e.getEventType()) && e.getDescription().contains("General Graardor")));
        assertTrue("fang swings are logged",
            evs.stream().anyMatch(e -> e.getDescription().contains("Dealt") && e.getDescription().contains("Osmumten's fang")));
        assertEquals("all three Dragon warhammer specs are in the ledger", 3,
            evs.stream().filter(e -> "SPEC".equals(e.getEventType()) && e.getDescription().contains("Dragon warhammer")).count());
        assertEquals("each spec logs its Defence drain", 3,
            evs.stream().filter(e -> "DEBUFF".equals(e.getEventType()) && e.getDescription().contains("Defence")).count());
        assertTrue("damage taken from Graardor is logged",
            evs.stream().anyMatch(e -> e.getDescription().equals("Took 26 (General Graardor)")));
        assertTrue("a minion hit is logged",
            evs.stream().anyMatch(e -> e.getDescription().contains("Sergeant Grimspike")));
        assertTrue("cannon ticks fold into the stream",
            evs.stream().anyMatch(e -> e.getDescription().contains("Dwarf multicannon")));
        assertTrue("thrall ticks fold into the stream",
            evs.stream().anyMatch(e -> e.getDescription().contains("Bloodfiend thrall")));
        assertTrue("a Saradomin brew sip is logged",
            evs.stream().anyMatch(e -> e.getDescription().contains("Saradomin brew")));

        // Every numeric ledger line reuses a value the fixture already baked in - nothing invented.
        java.util.Set<Integer> fangRolls = new java.util.HashSet<>(java.util.Arrays.asList(37, 38, 39, 40, 41, 42, 43, 44, 45));
        for (CombatTimelineEvent e : evs)
        {
            if ("HIT".equals(e.getEventType()) && e.getDescription().contains("Osmumten's fang"))
            {
                assertTrue("fang hit " + e.getAmount() + " is a real roll", fangRolls.contains(e.getAmount()));
            }
        }

        // The ledger is display-only: the headline totals / accuracy are exactly as before.
        assertEquals(3, seg.getLocalPlayerStats().getSpecialAttacksCount());
        double mainAccuracy = seg.getLocalPlayerStats().getWeaponBreakdown().stream()
            .filter(w -> "Osmumten's fang".equals(w.getWeaponName()))
            .findFirst().orElseThrow(AssertionError::new).getAccuracyPct();
        assertTrue("main-weapon accuracy still a real hit rate", mainAccuracy > 0 && mainAccuracy < 100);
    }

    @Test
    public void dropdownHasLiveMergedAndPastRows()
    {
        EncounterSegment live = CombatTutorialFixture.sampleEncounter();
        List<EncounterSegment> dd = CombatTutorialFixture.dropdownFor(live);

        assertSame("row 0 is the live fight by identity", live, dd.get(0));

        EncounterSegment merged = dd.stream().filter(EncounterSegment::isMerged).findFirst()
            .orElseThrow(AssertionError::new);
        assertEquals("Bloodveld x21", 21, merged.getMergedKills());
        assertTrue("merged row expands into per-kill children", merged.hasMergedChildren());

        long pastKills = dd.stream().filter(s -> s.getHistoryIndex() == 2 || s.getHistoryIndex() == 3).count();
        assertEquals("two past boss kills", 2, pastKills);
    }

    @Test
    public void overrideRedirectsEveryScopeRead()
    {
        CombatEncounterManager mgr = newManager();
        EncounterSegment live = CombatTutorialFixture.sampleEncounter();

        assertFalse(mgr.isDemoMode());
        mgr.setDemoOverride(live, CombatTutorialFixture.dropdownFor(live), CombatTutorialFixture.sampleBossProgress());

        assertTrue(mgr.isDemoMode());
        assertSame(live, mgr.getSelectedOrCurrentEncounter());
        assertSame(live, mgr.getAllSegmentsForDropdown().get(0));
        assertEquals(6, mgr.getAllSegmentsForDropdown().size());
        assertEquals(CombatTutorialFixture.BOSS_NAME, mgr.getLiveBossProgress().getName());
    }

    @Test
    public void overrideNeverMutatesLiveStateAndClearRestoresIt()
    {
        CombatEncounterManager mgr = newManager();

        // Stand in for real accumulated data on the always-present session / total scopes.
        mgr.getCurrentSessionEncounter().getLocalPlayerStats().setTotalDamage(50_000);
        mgr.getOverallSessionEncounter().getLocalPlayerStats().setTotalDamage(900_000);
        EncounterSegment liveViewBefore = mgr.getSelectedOrCurrentEncounter();
        boolean autoFollowBefore = mgr.isAutoFollow();

        EncounterSegment demo = CombatTutorialFixture.sampleEncounter();
        mgr.setDemoOverride(demo, CombatTutorialFixture.dropdownFor(demo), CombatTutorialFixture.sampleBossProgress());

        // Every user-triggered entry point a tutorial click could reach.
        mgr.selectEncounter(mgr.getAllSegmentsForDropdown().get(3));
        mgr.setAutoFollow(false);
        mgr.resetCurrentEncounter();
        mgr.resetSessionAndHistory();
        mgr.resetTotal();

        mgr.clearDemoOverride();

        assertFalse(mgr.isDemoMode());
        assertSame("live view identity restored", liveViewBefore, mgr.getSelectedOrCurrentEncounter());
        assertEquals("current session untouched", 50_000,
            mgr.getCurrentSessionEncounter().getLocalPlayerStats().getTotalDamage());
        assertEquals("total untouched", 900_000,
            mgr.getOverallSessionEncounter().getLocalPlayerStats().getTotalDamage());
        assertEquals("auto-follow untouched", autoFollowBefore, mgr.isAutoFollow());
        assertNull("real boss progress needs a live NPC health bar", mgr.getLiveBossProgress());
    }
}
