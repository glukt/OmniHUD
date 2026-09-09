package com.osrscopilot.combat.engine;

import com.osrscopilot.OsrsCopilotConfig;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.StatChanged;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CombatTickLedgerTest
{
    private Client client;
    private Player me;
    private CombatTickLedger ledger;
    private int tick;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        me = mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(me);
        when(client.getTickCount()).thenAnswer(i -> tick);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.HITPOINTS)).thenReturn(1_000_000);
        when(client.getSkillExperience(Skill.ATTACK)).thenReturn(2_000_000);
        when(client.getSkillExperience(Skill.STRENGTH)).thenReturn(3_000_000);
        when(client.getSkillExperience(Skill.DEFENCE)).thenReturn(4_000_000);
        when(client.getSkillExperience(Skill.RANGED)).thenReturn(5_000_000);
        when(client.getSkillExperience(Skill.MAGIC)).thenReturn(6_000_000);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(70);
        when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(70);
        ledger = new CombatTickLedger(client);
    }

    private HitsplatApplied npcHit(int amount, int type)
    {
        NPC npc = mock(NPC.class);
        when(npc.getName()).thenReturn("Hill Giant");
        when(npc.getIndex()).thenReturn(3);
        Hitsplat hs = mock(Hitsplat.class);
        when(hs.getAmount()).thenReturn(amount);
        when(hs.getHitsplatType()).thenReturn(type);
        when(hs.isMine()).thenReturn(true);
        HitsplatApplied e = new HitsplatApplied();
        e.setActor(npc);
        e.setHitsplat(hs);
        return e;
    }

    private HitsplatApplied incoming(int amount)
    {
        Hitsplat hs = mock(Hitsplat.class);
        when(hs.getAmount()).thenReturn(amount);
        when(hs.getHitsplatType()).thenReturn(amount == 0 ? HitsplatID.BLOCK_ME : HitsplatID.DAMAGE_ME);
        when(hs.isMine()).thenReturn(true);
        HitsplatApplied e = new HitsplatApplied();
        e.setActor(me);
        e.setHitsplat(hs);
        return e;
    }

    private StatChanged xp(Skill sk, int total)
    {
        return new StatChanged(sk, total, 1, 1);
    }

    private AnimationChanged meAnim(int id)
    {
        when(me.getAnimation()).thenReturn(id);
        AnimationChanged e = new AnimationChanged();
        e.setActor(me);
        return e;
    }

    @Test
    public void assemblesOneMeleeSwingAcrossTwoTicks()
    {
        // tick 100: swing + Hitpoints/Strength XP for the coming 12-damage hit
        tick = 100;
        ledger.onAnimationChanged(meAnim(390));
        ledger.onStatChanged(xp(Skill.HITPOINTS, 1_000_016)); // +16
        ledger.onStatChanged(xp(Skill.STRENGTH, 3_000_048));  // +48 (aggressive)
        ledger.onGameTick();

        // tick 101: the hitsplat lands
        tick = 101;
        ledger.onHitsplatApplied(npcHit(12, HitsplatID.DAMAGE_ME));
        ledger.onGameTick();

        List<CombatTick> ticks = ledger.recent(5);
        assertEquals(2, ticks.size());

        CombatTick swing = ticks.get(0);
        assertEquals(100, swing.getTick());
        assertTrue("attack animation registered", swing.wasAttack());
        assertEquals(390, swing.playerAnim);
        assertEquals(16, swing.getDHpXp());
        assertEquals(48, swing.getDStyleXp());
        assertEquals(0, swing.myWeaponDamage());

        CombatTick land = ticks.get(1);
        assertEquals(101, land.getTick());
        assertEquals(12, land.myWeaponDamage());
        assertEquals(0, land.getDHpXp());
        assertEquals("Hill Giant#3", land.getMyHits().get(0).getTarget());
    }

    @Test
    public void seedsXpBaselineSoTheFirstDropIsNotLost()
    {
        tick = 200;
        // first stat event of the session -> baseline seeded from getSkillExperience, real delta kept
        ledger.onStatChanged(xp(Skill.HITPOINTS, 1_000_020));
        ledger.onGameTick();
        assertEquals(20, ledger.recent(1).get(0).getDHpXp());
    }

    @Test
    public void incomingHitsAndZeroesAreSeparated()
    {
        tick = 300;
        ledger.onHitsplatApplied(npcHit(0, HitsplatID.DAMAGE_ME)); // your splash / 0 on the NPC
        ledger.onHitsplatApplied(incoming(8));                     // the NPC hits you for 8
        ledger.onHitsplatApplied(incoming(0));                     // and a blocked one
        ledger.onGameTick();

        CombatTick t = ledger.recent(1).get(0);
        assertEquals("no self weapon damage (the NPC splat was 0)", 0, t.myWeaponDamage());
        assertEquals(1, t.getMyHits().size());
        assertEquals(8, t.incomingDamage());
        assertEquals(2, t.getIncoming().size());
    }

    @Test
    public void maxHitFlagIsCarried()
    {
        tick = 400;
        ledger.onHitsplatApplied(npcHit(19, HitsplatID.DAMAGE_MAX_ME));
        ledger.onGameTick();
        CombatTick.Splat s = ledger.recent(1).get(0).getMyHits().get(0);
        assertTrue(s.isMax());
        assertTrue(s.isSelfDamage());
        assertEquals(19, s.getAmount());
    }

    @Test
    public void disabledByConfigIsANoOp()
    {
        OsrsCopilotConfig cfg = mock(OsrsCopilotConfig.class);
        when(cfg.combatDebugCapture()).thenReturn(false);
        CombatTickLedger off = new CombatTickLedger(client, null, cfg);

        tick = 500;
        off.onAnimationChanged(meAnim(390));
        off.onStatChanged(xp(Skill.HITPOINTS, 1_000_010));
        off.onHitsplatApplied(npcHit(12, HitsplatID.DAMAGE_ME));
        off.onGameTick();

        assertEquals("nothing recorded while disabled", 0, off.size());
    }

    @Test
    public void ringIsBoundedAndClearWorks()
    {
        for (int i = 0; i < 30; i++)
        {
            tick = 1000 + i;
            ledger.onAnimationChanged(meAnim(390));
            ledger.onGameTick();
        }
        assertEquals(30, ledger.size());
        ledger.clear();
        assertEquals(0, ledger.size());
        assertFalse(ledger.recent(5).iterator().hasNext());
    }
}
