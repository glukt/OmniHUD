package com.osrscopilot.combat.ui;

import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.model.CombatStyle;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SourceDetailsWindowTest
{
    private CombatEncounterManager mgr;
    private NPC boss;

    @Before
    public void setUp()
    {
        Client client = mock(Client.class);
        Player local = mock(Player.class);
        boss = mock(NPC.class);
        when(client.getLocalPlayer()).thenReturn(local);
        when(local.getName()).thenReturn("Me");
        when(boss.getName()).thenReturn("Vorkath");
        when(client.getTickCount()).thenReturn(50);
        mgr = new CombatEncounterManager(client);
    }

    @Test
    public void rendersSourceRowsFromLiveStatsWithoutThrowing()
    {
        mgr.notifyCombatAction(boss, 50);
        mgr.recordPlayerDamageDealt(CombatStyle.RANGED, 30, "Twisted bow", 50, "Vorkath", false);
        mgr.recordPlayerDamageDealt(CombatStyle.RANGED, 12, "Twisted bow", 52, "Vorkath", false);
        mgr.recordPlayerDamageDealt(CombatStyle.MAGIC, 40, "Fire Surge", 54, "Vorkath", false);

        SourceDetailsWindow w = new SourceDetailsWindow(mgr, null);
        try
        {
            w.renderOnceForTest();
            assertTrue("at least one source row rendered", w.sourceRowCountForTest() >= 2);
            // re-render is idempotent
            w.renderOnceForTest();
            assertTrue(w.sourceRowCountForTest() >= 2);
        }
        finally
        {
            w.dispose();
        }
    }

    @Test
    public void liveTickUpdatesRowsInPlaceInsteadOfRecreatingThem()
    {
        mgr.notifyCombatAction(boss, 50);
        mgr.recordPlayerDamageDealt(CombatStyle.RANGED, 20, "Twisted bow", 50, "Vorkath", false);
        mgr.recordPlayerDamageDealt(CombatStyle.MAGIC, 40, "Fire Surge", 52, "Vorkath", false);

        SourceDetailsWindow w = new SourceDetailsWindow(mgr, null);
        try
        {
            w.renderOnceForTest();
            java.awt.Component row0 = w.sourceRowComponentForTest(0);
            java.awt.Component row1 = w.sourceRowComponentForTest(1);

            // more damage to the SAME sources -> order unchanged -> rows must be reused, not rebuilt
            mgr.recordPlayerDamageDealt(CombatStyle.RANGED, 15, "Twisted bow", 54, "Vorkath", false);
            w.tickForTest();

            assertSame("row 0 reused on a live tick", row0, w.sourceRowComponentForTest(0));
            assertSame("row 1 reused on a live tick", row1, w.sourceRowComponentForTest(1));
        }
        finally
        {
            w.dispose();
        }
    }

    @Test
    public void handlesAnEmptyManagerGracefully()
    {
        SourceDetailsWindow w = new SourceDetailsWindow(mgr, null);
        try
        {
            w.renderOnceForTest();
            assertTrue(w.sourceRowCountForTest() >= 1); // the "no data" hint
        }
        finally
        {
            w.dispose();
        }
    }
}
