package com.osrscopilot.combat.party;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.model.CombatStyle;
import com.osrscopilot.combat.model.EncounterSegment;
import com.osrscopilot.combat.model.EntityCombatStats;
import com.osrscopilot.combat.model.SegmentType;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.PartyService;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CombatPartyServiceTest
{
    private Client client;
    private CombatEncounterManager encounters;
    private CombatPartyService party;
    private NPC boss;
    private Player local;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        local = mock(Player.class);
        boss = mock(NPC.class);
        when(client.getLocalPlayer()).thenReturn(local);
        when(local.getName()).thenReturn("Me");
        when(boss.getName()).thenReturn("Vorkath");
        when(client.getTickCount()).thenReturn(100);

        encounters = new CombatEncounterManager(client);
        party = new CombatPartyService(encounters);
    }

    private static RemoteCombatSnapshot at(RemoteCombatSnapshot s, int x, int y)
    {
        s.setWorldX(x);
        s.setWorldY(y);
        s.setWorldPlane(0);
        return s;
    }

    private static RemoteCombatSnapshot remote(long memberId, String rsn, long dmg, boolean inCombat, String... weapons)
    {
        RemoteCombatSnapshot s = new RemoteCombatSnapshot();
        s.setMemberId(memberId);
        s.setRsn(rsn);
        s.setBoss("Vorkath");
        s.setSharedId("shared-1");
        s.setTotalDamage(dmg);
        s.setDmgRanged(dmg);
        s.setCombatSeconds(30);
        s.setInCombat(inCombat);
        s.setAttempts(20);
        s.setHits(18);
        List<RemoteCombatSnapshot.WeaponSlice> w = new ArrayList<>();
        for (String wn : weapons)
        {
            RemoteCombatSnapshot.WeaponSlice ws = new RemoteCombatSnapshot.WeaponSlice();
            ws.setName(wn);
            ws.setDmg(dmg / weapons.length);
            ws.setHits(9);
            ws.setMax(40);
            w.add(ws);
        }
        s.setWeapons(w);
        return s;
    }

    @Test
    public void testLocalSnapshotCapturesOwnDamageAndWeapons()
    {
        encounters.notifyCombatAction(boss, 100);
        encounters.recordPlayerDamageDealt(CombatStyle.RANGED, 300, "Twisted bow", 100);
        encounters.recordPlayerDamageDealt(CombatStyle.RANGED, 250, "Twisted bow", 102);

        RemoteCombatSnapshot s = party.buildLocalSnapshot();
        assertEquals(550, s.getTotalDamage());
        assertEquals(550, s.getDmgRanged());
        assertTrue("marked in combat", s.isInCombat());
        assertEquals("Vorkath", s.getBoss());
        assertNotNull(s.getWeapons());
        assertEquals("Twisted bow", s.getWeapons().get(0).getName());
        assertEquals(550, s.getWeapons().get(0).getDmg());
    }

    @Test
    public void testGroupSegmentMergesSelfAndRemotes()
    {
        // my contribution
        encounters.notifyCombatAction(boss, 100);
        encounters.recordPlayerDamageDealt(CombatStyle.MAGIC, 400, "Sanguinesti staff", 100);

        // two teammates
        party.ingest(remote(11L, "Zezima", 900, true, "Twisted bow"));
        party.ingest(remote(22L, "Woox", 250, true, "Dragon dagger"));

        EncounterSegment g = party.getGroupSegment();
        assertEquals("Vorkath", g.getTargetName());

        // ranked participants: Zezima (900) > me (400) > Woox (250)
        List<EntityCombatStats> ranked = g.getRankedParticipants(10);
        assertEquals(3, ranked.size());
        assertEquals("Zezima", ranked.get(0).getName());
        assertEquals(900, ranked.get(0).getTotalDamage());
        assertEquals("Me", ranked.get(1).getName());
        assertEquals(400, ranked.get(1).getTotalDamage());

        // teammate weapon drill-down survives the round trip
        EntityCombatStats zez = g.getOtherParticipants().get("Zezima");
        assertNotNull(zez);
        assertEquals("Twisted bow", zez.getWeaponBreakdown().get(0).getWeaponName());
    }

    /**
     * FIX 4: the shared GROUP segment is rebuilt from both the EDT and the client thread while it
     * is being read. Snapshot-then-publish + one monitor on every read/write means a reader never
     * trips over a half-cleared participant map.
     */
    @Test
    public void testGroupSegmentSurvivesConcurrentRebuildAndRead() throws Exception
    {
        for (long id = 1; id <= 4; id++)
        {
            party.ingest(remote(id, "M" + id, 100 * id, true, "Twisted bow", "Whip"));
        }
        encounters.notifyCombatAction(boss, 100);
        encounters.recordPlayerDamageDealt(CombatStyle.RANGED, 500, "Twisted bow", 100);

        final int iterations = 3000;
        final java.util.concurrent.atomic.AtomicReference<Throwable> err =
            new java.util.concurrent.atomic.AtomicReference<>();

        Runnable rebuildAndRead = () ->
        {
            try
            {
                for (int i = 0; i < iterations; i++)
                {
                    EncounterSegment g = party.getGroupSegment();
                    long total = 0;
                    for (EntityCombatStats s : g.getRankedParticipants(64))
                    {
                        total += s.getTotalDamage();
                    }
                    g.getTotalDamage();
                    g.toString();
                    if (total < 0)
                    {
                        throw new IllegalStateException("impossible negative total");
                    }
                }
            }
            catch (Throwable t)
            {
                err.compareAndSet(null, t);
            }
        };

        Thread a = new Thread(rebuildAndRead, "group-a");
        Thread b = new Thread(rebuildAndRead, "group-b");
        a.start();
        b.start();
        a.join(20_000);
        b.join(20_000);

        assertNull("a reader saw a half-built group meter: " + err.get(), err.get());
        EncounterSegment g = party.getGroupSegment();
        assertEquals("4 teammates in the map", 4, g.getOtherParticipants().size());
        assertEquals("4 teammates + me", 5, g.getRankedParticipants(64).size());
    }

    @Test
    public void testOwnEchoIsIgnored()
    {
        // localMemberId() is -1 with no PartyService; a snapshot from -1 is our echo.
        RemoteCombatSnapshot echo = remote(-1L, "Me", 999, true, "Whip");
        party.ingest(echo);
        assertEquals("only self in the group, no phantom remote", 1, party.memberCount());
    }

    @Test
    public void testMemberLeaveDropsTheRow()
    {
        party.ingest(remote(33L, "Gone", 500, true, "Whip"));
        assertEquals(2, party.memberCount());
        party.onMemberLeft(33L);
        assertEquals(1, party.memberCount());
    }

    @Test
    public void testNewPassphraseIsSelfContained()
    {
        // Must NOT call PartyService.generatePassphrase() (that asserts client-thread + reads the
        // item cache). party is null here; a real code still comes back.
        String p = party.newPassphrase();
        assertNotNull(p);
        assertTrue("multi-word join code", p.contains("-"));
        assertTrue("not blank", p.trim().length() > 3);
    }

    @Test
    public void testGroupScopeSupplierFeedsTheDropdownAndUnpinsOnLeave()
    {
        OsrsCopilotConfig cfg = mock(OsrsCopilotConfig.class);
        when(cfg.combatPartyEnabled()).thenReturn(true);
        when(cfg.combatPartyShowOffline()).thenReturn(true);
        PartyService ps = mock(PartyService.class);
        when(ps.isInParty()).thenReturn(true);

        CombatPartyService svc = new CombatPartyService(ps, null, client, cfg, encounters, null);
        encounters.setGroupScopeSupplier(svc::getGroupScopeOrNull);

        encounters.notifyCombatAction(boss, 100);
        encounters.recordPlayerDamageDealt(CombatStyle.MAGIC, 400, "Sanguinesti staff", 100);
        svc.ingest(remote(11L, "Zezima", 900, true, "Twisted bow"));

        EncounterSegment group = encounters.getAllSegmentsForDropdown().stream()
            .filter(s -> s.getSegmentType() == SegmentType.GROUP)
            .findFirst().orElse(null);
        assertNotNull("Group scope row is offered while in a party", group);

        encounters.selectEncounter(group);
        assertSame(group, encounters.getSelectedOrCurrentEncounter());
        assertEquals("me + one teammate", 2, group.getRankedParticipants(10).size());

        // The dropdown row is the same instance every refresh (mutated in place, never replaced).
        assertSame(group, svc.getGroupScopeOrNull());

        // Leaving the party pulls the row and unpins the selection.
        when(ps.isInParty()).thenReturn(false);
        assertNull(svc.getGroupScopeOrNull());
        assertNotSame(group, encounters.getSelectedOrCurrentEncounter());
    }

    /**
     * Recount-style auto-follow, scoped to the shared fight: the live view swaps to the combined
     * group meter only when a teammate is in YOUR fight (same target / converged sharedId), shows
     * just that fight's participants, and falls back to the solo fight when they stop. A teammate
     * off fighting something else never appears. The user never picks a "Group" scope.
     */
    @Test
    public void testAutoFollowScopesToTheSharedFight()
    {
        OsrsCopilotConfig cfg = mock(OsrsCopilotConfig.class);
        when(cfg.combatPartyEnabled()).thenReturn(true);
        when(cfg.combatPartyShowOffline()).thenReturn(true);
        PartyService ps = mock(PartyService.class);
        when(ps.isInParty()).thenReturn(true);

        CombatPartyService svc = new CombatPartyService(ps, null, client, cfg, encounters, null);
        encounters.setGroupScopeSupplier(svc::getGroupScopeOrNull);
        encounters.setGroupAutoFollowSupplier(svc::shouldAutoShowGroup);

        // Solo fight live (boss.getName() == "Vorkath"); no teammate yet -> stay on the solo view.
        encounters.notifyCombatAction(boss, 100);
        encounters.recordPlayerDamageDealt(CombatStyle.MELEE, 300, "Whip", 100);
        svc.onGameTick(101); // refreshMyShared(): mint/adopt a sharedId for "Vorkath"
        assertFalse(svc.shouldAutoShowGroup());
        assertNotEquals(SegmentType.GROUP, encounters.getSelectedOrCurrentEncounter().getSegmentType());

        // A teammate on an UNRELATED target -> does not pull you in, not in the segment.
        RemoteCombatSnapshot elsewhere = remote(22L, "Woox", 400, true, "Tbow");
        elsewhere.setBoss("Zulrah");
        elsewhere.setSharedId("other-99");
        svc.ingest(elsewhere);
        assertFalse(svc.shouldAutoShowGroup());

        // A teammate fighting YOUR target -> auto-swap; only shared-fight participants show.
        svc.ingest(remote(11L, "Zezima", 900, true, "Twisted bow")); // boss "Vorkath"
        svc.onGameTick(106);
        assertTrue(svc.shouldAutoShowGroup());
        EncounterSegment g = encounters.getSelectedOrCurrentEncounter();
        assertEquals(SegmentType.GROUP, g.getSegmentType());
        assertEquals("me + the one teammate in my fight (Woox excluded)", 2, g.getRankedParticipants(10).size());

        // That teammate stops fighting -> fall back to the solo fight, nothing pinned.
        svc.ingest(remote(11L, "Zezima", 900, false, "Twisted bow"));
        assertFalse(svc.shouldAutoShowGroup());
        assertNotEquals(SegmentType.GROUP, encounters.getSelectedOrCurrentEncounter().getSegmentType());
    }

    /**
     * Proximity pull: a teammate within GROUP_PROXIMITY_TILES who starts a fight promotes the
     * combined meter before the local player has thrown a hit. The local row is present but zero,
     * the segment is scoped to the teammate's target, and shouldAutoShowGroup() (which also drives
     * the "Entering Combat" banner) flips true.
     */
    @Test
    public void testNearbyTeammatePullShowsGroupMeterBeforeIEngage()
    {
        OsrsCopilotConfig cfg = mock(OsrsCopilotConfig.class);
        when(cfg.combatPartyEnabled()).thenReturn(true);
        when(cfg.combatPartyShowOffline()).thenReturn(true);
        PartyService ps = mock(PartyService.class);
        when(ps.isInParty()).thenReturn(true);
        when(local.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));

        CombatPartyService svc = new CombatPartyService(ps, null, client, cfg, encounters, null);
        encounters.setGroupScopeSupplier(svc::getGroupScopeOrNull);
        encounters.setGroupAutoFollowSupplier(svc::shouldAutoShowGroup);

        // I have attacked nothing. A teammate ~18 tiles away is fighting Vorkath.
        svc.ingest(at(remote(11L, "Zezima", 800, true, "Twisted bow"), 3212, 3214));
        svc.onGameTick(150);

        assertTrue("a nearby teammate's pull promotes the combined meter", svc.shouldAutoShowGroup());
        EncounterSegment g = encounters.getSelectedOrCurrentEncounter();
        assertEquals(SegmentType.GROUP, g.getSegmentType());
        assertEquals("Vorkath", g.getTargetName());

        List<EntityCombatStats> ranked = g.getRankedParticipants(10);
        assertEquals("me (0) + the teammate", 2, ranked.size());
        assertEquals("Zezima", ranked.get(0).getName());
        assertEquals(800, ranked.get(0).getTotalDamage());
        assertEquals("Me", ranked.get(1).getName());
        assertEquals("my row is present but zero until I swing", 0, ranked.get(1).getTotalDamage());
    }

    /** A teammate fighting far away (outside GROUP_PROXIMITY_TILES) never pulls the meter up. */
    @Test
    public void testFarTeammatePullDoesNotShowGroupMeter()
    {
        OsrsCopilotConfig cfg = mock(OsrsCopilotConfig.class);
        when(cfg.combatPartyEnabled()).thenReturn(true);
        when(cfg.combatPartyShowOffline()).thenReturn(true);
        PartyService ps = mock(PartyService.class);
        when(ps.isInParty()).thenReturn(true);
        when(local.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));

        CombatPartyService svc = new CombatPartyService(ps, null, client, cfg, encounters, null);
        encounters.setGroupScopeSupplier(svc::getGroupScopeOrNull);
        encounters.setGroupAutoFollowSupplier(svc::shouldAutoShowGroup);

        // ~280 tiles away - a different part of the map.
        svc.ingest(at(remote(11L, "Zezima", 800, true, "Twisted bow"), 3480, 3200));
        svc.onGameTick(150);

        assertFalse("a far teammate does not promote the meter", svc.shouldAutoShowGroup());
        assertNotEquals(SegmentType.GROUP,
            encounters.getSelectedOrCurrentEncounter().getSegmentType());
    }

    /**
     * Regression: the send throttle must actually broadcast. It was seeded to Integer.MIN_VALUE,
     * so (tick - lastSendTick) overflowed negative and the ">= SEND_EVERY_TICKS" gate never
     * fired - the snapshot was never sent even while enabled and in a party.
     */
    @Test
    public void testOnGameTickBroadcastsTheSnapshot()
    {
        OsrsCopilotConfig cfg = mock(OsrsCopilotConfig.class);
        when(cfg.combatPartyEnabled()).thenReturn(true);
        PartyService ps = mock(PartyService.class);
        when(ps.isInParty()).thenReturn(true);

        CombatPartyService svc = new CombatPartyService(ps, null, client, cfg, encounters, null);

        // client.getTickCount() is a small positive int - the old MIN_VALUE seed broke this.
        svc.onGameTick(120);
        org.mockito.Mockito.verify(ps, org.mockito.Mockito.atLeastOnce())
            .send(org.mockito.ArgumentMatchers.any(RemoteCombatSnapshot.class));
    }
}
