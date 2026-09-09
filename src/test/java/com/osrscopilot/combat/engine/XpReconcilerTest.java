package com.osrscopilot.combat.engine;

import com.osrscopilot.combat.model.EncounterSegment;
import com.osrscopilot.combat.model.SegmentType;
import java.util.UUID;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.StatChanged;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class XpReconcilerTest
{
    private Client client;
    private Player me;
    private CombatEncounterManager manager;
    private CombatTickLedger ledger;
    private XpReconciler recon;
    private EncounterSegment fight;
    private int tick;
    private int hpXpTotal = 1_000_000;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        me = mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(me);
        when(client.getTickCount()).thenAnswer(i -> tick);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.HITPOINTS)).thenReturn(hpXpTotal);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(70);
        when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(70);

        manager = mock(CombatEncounterManager.class);
        ledger = new CombatTickLedger(client);
        recon = new XpReconciler(manager, ledger);
        fight = new EncounterSegment(UUID.randomUUID(), "Hill Giant", SegmentType.ENCOUNTER, 0, "me");
        when(manager.getCurrentEncounter()).thenReturn(fight);
    }

    private void step()
    {
        ledger.onGameTick();
        recon.onGameTick();
    }

    private void hpXp(int delta)
    {
        hpXpTotal += delta;
        ledger.onStatChanged(new StatChanged(Skill.HITPOINTS, hpXpTotal, 1, 1));
    }

    private void npcHit(int amount)
    {
        NPC npc = mock(NPC.class);
        when(npc.getName()).thenReturn("Hill Giant");
        when(npc.getIndex()).thenReturn(3);
        Hitsplat hs = mock(Hitsplat.class);
        when(hs.getAmount()).thenReturn(amount);
        when(hs.getHitsplatType()).thenReturn(HitsplatID.DAMAGE_ME);
        when(hs.isMine()).thenReturn(true);
        HitsplatApplied e = new HitsplatApplied();
        e.setActor(npc);
        e.setHitsplat(hs);
        ledger.onHitsplatApplied(e);
    }

    /** One melee swing: XP on the swing tick, hitsplat one tick later (as the captures showed). */
    private void swing(int dmg)
    {
        hpXp((int) Math.round(dmg * 4.0 / 3.0));
        step();
        tick++;
        npcHit(dmg);
        step();
        tick++;
    }

    private void endFight()
    {
        when(manager.getCurrentEncounter()).thenReturn(null);
        step();
    }

    @Test
    public void cleanMeleeFightReconcilesToHighConfidenceOk()
    {
        tick = 100;
        for (int d : new int[]{9, 11, 15, 10, 12, 8, 14, 19, 7, 13})
        {
            swing(d);
        }
        endFight();

        XpReconciler.Report r = recon.getLastReport();
        assertNotNull(r);
        assertEquals("all damage attributed", 118, r.observed);
        assertEquals("XP-implied damage matches", 118, r.expected);
        assertEquals("OK", r.verdict);
        assertTrue("confidence ~1.0: " + r.confidence, r.confidence > 0.98);
        assertEquals(1.0, fight.getAttributionConfidence(), 0.02);
    }

    @Test
    public void unattributedExtraDamageReadsAsOver()
    {
        tick = 100;
        for (int d : new int[]{10, 10, 10, 10, 10})
        {
            swing(d);
        }
        // a hit that landed but gave us no XP (a thrall / cannon splat mislabelled as ours)
        npcHit(25);
        step();
        endFight();

        XpReconciler.Report r = recon.getLastReport();
        assertEquals(75, r.observed);      // 5x10 + a bogus 25
        assertEquals(49, r.expected);      // round(5 * round(10*4/3=13) * 0.75) = round(48.75)
        assertEquals("OVER", r.verdict);
        assertTrue("confidence dropped: " + r.confidence, r.confidence < 0.7);
    }

    @Test
    public void missingDamageReadsAsUnder()
    {
        tick = 100;
        for (int d : new int[]{10, 10, 10, 10, 10})
        {
            swing(d);
        }
        // Hitpoints XP for ~24 more damage, but no hitsplat captured (a dropped splat).
        hpXp(32);
        step();
        endFight();

        XpReconciler.Report r = recon.getLastReport();
        assertEquals(50, r.observed);
        assertEquals(73, r.expected);      // round((5*13 + 32) * 0.75) = round(72.75)
        assertEquals("UNDER", r.verdict);
        assertTrue("confidence dropped: " + r.confidence, r.confidence < 0.8);
    }

    @Test
    public void noCombatMeansFullConfidence()
    {
        tick = 100;
        step();
        step();
        endFight();
        XpReconciler.Report r = recon.getLastReport();
        assertEquals("OK", r.verdict);
        assertEquals(1.0, r.confidence, 0.001);
    }
}
