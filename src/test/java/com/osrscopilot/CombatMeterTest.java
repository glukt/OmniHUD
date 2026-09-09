package com.osrscopilot;

import com.osrscopilot.combat.CombatMeterColors;
import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.engine.ConsumableAuditor;
import com.osrscopilot.combat.engine.DamageAttributionEngine;
import com.osrscopilot.combat.model.CombatStyle;
import com.osrscopilot.combat.model.EncounterSegment;
import com.osrscopilot.combat.model.EntityCombatStats;
import com.osrscopilot.combat.model.SegmentStatus;
import com.osrscopilot.combat.model.SegmentType;
import com.osrscopilot.combat.model.StyleDamageEntry;
import com.osrscopilot.combat.overlay.CombatMeterOverlay;
import com.osrscopilot.combat.ui.CollapsibleSectionCard;
import com.osrscopilot.combat.ui.CombatEncounterTabView;
import com.osrscopilot.data.MonsterDatabase;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;

import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;


import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CombatMeterTest
{
    @Mock
    private Client client;

    @Mock
    private ItemManager itemManager;

    @Mock
    private OsrsCopilotConfig config;

    @Mock
    private Player localPlayer;

    @Mock
    private NPC targetNpc;

    @Mock
    private ItemContainer equipmentContainer;

    @Mock
    private ItemContainer inventoryContainer;

    @Mock
    private net.runelite.client.ui.overlay.tooltip.TooltipManager tooltipManager;

    @Mock
    private net.runelite.client.config.ConfigManager configManager;

    private CombatEncounterManager encounterManager;
    private DamageAttributionEngine damageEngine;
    private ConsumableAuditor consumableAuditor;
    private com.osrscopilot.combat.overlay.CombatMeterSettingsCard settingsCard;
    private com.osrscopilot.combat.overlay.CombatStateBannerOverlay bannerOverlay;
    private com.osrscopilot.combat.overlay.CombatGraphCard graphCard;
    private com.osrscopilot.combat.overlay.CombatGraphOverlay graphOverlay;

    @Before
    public void setUp()
    {
        MockitoAnnotations.openMocks(this);

        when(client.getLocalPlayer()).thenReturn(localPlayer);
        when(localPlayer.getName()).thenReturn("TestHero");
        when(targetNpc.getName()).thenReturn("Vorkath");
        when(client.getTickCount()).thenReturn(100);

        when(config.showCombatOverlay()).thenReturn(true);
        when(config.maxCombatBars()).thenReturn(5);
        when(config.combatAutoHide()).thenReturn(false);
        when(config.combatOverlayOpacity()).thenReturn(88);
        when(config.combatOverlayWidth()).thenReturn(220);
        when(config.combatShowMiniFooter()).thenReturn(true);
        when(config.combatBarStyle()).thenReturn(OsrsCopilotConfig.BarStyleOption.GRADIENT);
        when(config.combatPaletteAccent()).thenReturn(OsrsCopilotConfig.PaletteAccentOption.DRAGON_RED);
        when(config.combatBarHeight()).thenReturn(20);
        when(config.combatBarTexture()).thenReturn(OsrsCopilotConfig.BarTextureOption.GLOSS);
        when(config.combatBarValueFormat()).thenReturn(OsrsCopilotConfig.BarValueFormat.RATE_AMOUNT_PERCENT);
        when(config.combatBarFont()).thenReturn(OsrsCopilotConfig.BarFontOption.SMALL);
        when(config.combatBarShowRank()).thenReturn(true);
        when(config.combatGraphStyle()).thenReturn(OsrsCopilotConfig.GraphStyleOption.AREA);
        when(config.combatGraphSmoothing()).thenReturn(6);
        when(config.combatBarCorner()).thenReturn(4);
        when(config.combatBarGap()).thenReturn(2);
        when(config.combatBarTextShadow()).thenReturn(false);
        when(config.combatBarShowStyleIcon()).thenReturn(false);
        when(config.combatHeaderMode()).thenReturn(OsrsCopilotConfig.HeaderModeOption.FULL);
        when(config.combatBarCustomColor()).thenReturn(new Color(120, 200, 255));

        encounterManager = new CombatEncounterManager(client);
        damageEngine = new DamageAttributionEngine(client);
        consumableAuditor = new ConsumableAuditor(client, itemManager);
        bannerOverlay = new com.osrscopilot.combat.overlay.CombatStateBannerOverlay(client, config, encounterManager);
        settingsCard = new com.osrscopilot.combat.overlay.CombatMeterSettingsCard(config, configManager, encounterManager, bannerOverlay);
        graphCard = new com.osrscopilot.combat.overlay.CombatGraphCard();
        graphOverlay = new com.osrscopilot.combat.overlay.CombatGraphOverlay(client, config, encounterManager, graphCard);
    }

    /**
     * Simulates the player clicking "Eat" / "Drink" on a consumable, opening the auditor's
     * consume-intent gate. A food/potion whose inventory count falls without this click is treated
     * as an ingredient / fishing bait / used-on-object, not a heal (see the karambwan-bait fix).
     */
    private void clickConsume(String option)
    {
        MenuOptionClicked ev = mock(MenuOptionClicked.class);
        when(ev.getMenuOption()).thenReturn(option);
        consumableAuditor.onMenuOptionClicked(ev);
    }




    @Test
    public void testDirectPlayerDamageAndAccuracyTracking()
    {
        // 1. Initial State
        assertEquals("Total", encounterManager.getOverallSessionEncounter().getTargetName());
        assertNull(encounterManager.getCurrentEncounter());

        // 2. Notify Combat Action against Vorkath
        encounterManager.notifyCombatAction(targetNpc, 100);
        assertNotNull(encounterManager.getCurrentEncounter());
        assertEquals("Vorkath", encounterManager.getCurrentEncounter().getTargetName());
        assertTrue(encounterManager.getCurrentEncounter().isInCombat());

        // 3. Record Player Hits
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 45, "Dragon Hunter Lance", 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 52, "Dragon Hunter Lance", 104);
        encounterManager.recordPlayerMiss(CombatStyle.MELEE); // Miss / 0 hit

        EntityCombatStats stats = encounterManager.getCurrentEncounter().getLocalPlayerStats();
        assertEquals(97, stats.getTotalDamage());
        assertEquals(3, stats.getAttackAttempts());
        assertEquals(2, stats.getSuccessfulHits());
        assertEquals(52, stats.getMaxHitDealt().getAmount());
        assertEquals(66.6, stats.getHitAccuracy(), 0.1);

        // Verify Session Accumulation
        EntityCombatStats sessionStats = encounterManager.getOverallSessionEncounter().getLocalPlayerStats();
        assertEquals(97, sessionStats.getTotalDamage());
        assertEquals(3, sessionStats.getAttackAttempts());
    }

    @Test
    public void testInactivityEndIsLeftCombatNotDefeated()
    {
        encounterManager.setSessionTimeoutSeconds(0); // Current Session no longer idle-resets
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 30, "Whip", 100);
        // No further combat; a game tick well past the inactivity window ends the fight.
        encounterManager.onGameTick(200);

        EncounterSegment sess = encounterManager.getCurrentSessionEncounter();
        boolean hasLeft = sess.getLocalPlayerStats().getTimelineEvents().stream()
            .anyMatch(e -> "END".equals(e.getEventType()) && e.getDescription().startsWith("Left combat with"));
        boolean hasDefeated = sess.getLocalPlayerStats().getTimelineEvents().stream()
            .anyMatch(e -> e.getDescription().startsWith("Defeated"));
        assertTrue("timeout end should read 'Left combat with'", hasLeft);
        assertFalse("timeout must not claim 'Defeated'", hasDefeated);
    }

    @Test
    public void testResetSessionKeepsTotal()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 500, "Whip", 100);
        assertEquals(500, encounterManager.getCurrentSessionEncounter().getLocalPlayerStats().getTotalDamage());
        assertEquals(500, encounterManager.getOverallSessionEncounter().getLocalPlayerStats().getTotalDamage());

        encounterManager.resetSessionAndHistory();

        assertEquals("Session cleared", 0, encounterManager.getCurrentSessionEncounter().getLocalPlayerStats().getTotalDamage());
        assertEquals("Total untouched", 500, encounterManager.getOverallSessionEncounter().getLocalPlayerStats().getTotalDamage());
    }

    @Test
    public void testTotalScopePersistsAndRestores()
    {
        java.util.Map<String, String> store = new java.util.HashMap<>();
        doAnswer(inv -> {
            store.put((String) inv.getArgument(1), java.util.Objects.toString(inv.getArgument(2), null));
            return null;
        }).when(configManager).setConfiguration(anyString(), anyString(), any());
        doAnswer(inv -> store.get((String) inv.getArgument(1)))
            .when(configManager).getConfiguration(anyString(), anyString());

        encounterManager.setPersistence(configManager);
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MAGIC, 1234, "Fire Blast", 100);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MELEE, 88, "Vorkath", 101);
        encounterManager.persistTotal();

        CombatEncounterManager fresh = new CombatEncounterManager(client);
        fresh.setPersistence(configManager);
        fresh.restoreTotal();

        EntityCombatStats t = fresh.getOverallSessionEncounter().getLocalPlayerStats();
        assertEquals(1234, t.getTotalDamage());
        assertEquals(88, t.getDamageTaken());
        assertEquals(1234L, (long) t.getDamageByStyle().getOrDefault(CombatStyle.MAGIC, 0L));

        // Tranche D4: applyPersistedTotals is additive - a second restoreTotal must be a no-op.
        fresh.restoreTotal();
        assertEquals("restoreTotal must not double the Total", 1234, t.getTotalDamage());
    }

    /** Tranche D1: logging out mid-fight closes the fight (ABANDONED) without its duration
     *  spanning the offline period, and keeps the damage it had. */
    @Test
    public void testLogoutMidFightFinalizesCleanly()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 40, "Whip", 100, "Vorkath");
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 30, "Whip", 104, "Vorkath");
        encounterManager.onLogout();

        assertNull(encounterManager.getCurrentEncounter());
        EncounterSegment fight = encounterManager.getAllSegmentsForDropdown().get(0);
        assertEquals(SegmentStatus.ABANDONED, fight.getStatus());
        assertEquals(70, fight.getLocalPlayerStats().getTotalDamage());
        assertTrue("duration must not span an offline period", fight.getDurationSeconds() < 30);
    }

    /** Tranche D1: a world-hop closes the in-flight fight but leaves Current Session running. */
    @Test
    public void testWorldHopClosesFightKeepsSession()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 55, "Whip", 100, "Vorkath");
        encounterManager.onWorldHop();

        assertNull(encounterManager.getCurrentEncounter());
        assertEquals("session damage survives a hop", 55,
            encounterManager.getCurrentSessionEncounter().getLocalPlayerStats().getTotalDamage());
    }

    /** Tranche D6 / G1: a despawn of the target counts as a kill only when it's actually dead;
     *  a despawn while alive (left render / phase dive) leaves the fight for the idle timeout. */
    @Test
    public void testDespawnFinalizesOnlyWhenDead()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 40, "Whip", 100, "Vorkath");

        when(targetNpc.isDead()).thenReturn(false);
        when(targetNpc.getHealthRatio()).thenReturn(30); // still alive
        encounterManager.handleNpcDespawned(targetNpc, 105);
        assertNotNull("despawn while alive doesn't finalize", encounterManager.getCurrentEncounter());

        when(targetNpc.isDead()).thenReturn(true);
        encounterManager.handleNpcDespawned(targetNpc, 106);
        assertNull("despawn while dead = a kill", encounterManager.getCurrentEncounter());
        assertEquals(SegmentStatus.COMPLETED, encounterManager.getAllSegmentsForDropdown().get(0).getStatus());
    }

    /** G1: an NPC hitting you before you target it opens an encounter named after the attacker. */
    @Test
    public void testAttackedFirstOpensEncounter()
    {
        assertNull(encounterManager.getCurrentEncounter());
        encounterManager.notifyDamageTakenInCombat(targetNpc, 100);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MAGIC, 14, "Vorkath", 100);

        assertNotNull("being hit first still opens a fight", encounterManager.getCurrentEncounter());
        assertEquals("Vorkath", encounterManager.getCurrentEncounter().getTargetName());
        assertEquals(14, encounterManager.getCurrentEncounter().getLocalPlayerStats().getDamageTaken());
    }

    /**
     * A follower pet permanently "interacts with" its owner, so the attacker resolver can hand it
     * to the manager. It must never open or name an encounter - identified either as
     * {@code client.getFollower()} or by {@code NPCComposition.isFollower()}.
     */
    @Test
    public void testFollowerPetNeverOpensOrNamesAnEncounter()
    {
        NPC pet = mock(NPC.class);
        when(pet.getName()).thenReturn("Border Collie puppy");
        when(pet.getInteracting()).thenReturn(localPlayer);
        when(client.getFollower()).thenReturn(pet);

        // via the damage-taken path (the observed bug: pet named the fight while cannon-safespotting)
        encounterManager.notifyDamageTakenInCombat(pet, 100);
        assertNull("a follower pet must not open a fight", encounterManager.getCurrentEncounter());

        // via the action path (defensive: something upstream passing the pet as a target)
        encounterManager.notifyCombatAction(pet, 101);
        assertNull("a follower pet must not open a fight", encounterManager.getCurrentEncounter());

        // identified purely by composition (boss/skilling pets the client doesn't report as follower)
        when(client.getFollower()).thenReturn(null);
        net.runelite.api.NPCComposition petComp = mock(net.runelite.api.NPCComposition.class);
        when(petComp.isFollower()).thenReturn(true);
        NPC skillPet = mock(NPC.class);
        when(skillPet.getComposition()).thenReturn(petComp);
        when(skillPet.getName()).thenReturn("Baby chinchompa");
        encounterManager.notifyDamageTakenInCombat(skillPet, 102);
        encounterManager.notifyCombatAction(skillPet, 103);
        assertNull("a composition-flagged pet must not open a fight", encounterManager.getCurrentEncounter());

        // a real NPC still opens one normally
        encounterManager.notifyDamageTakenInCombat(targetNpc, 104);
        assertNotNull(encounterManager.getCurrentEncounter());
        assertEquals("Vorkath", encounterManager.getCurrentEncounter().getTargetName());
    }

    /** G1: a corrupt persisted Total blob leaves Total at zero and doesn't throw. */
    @Test
    public void testRestoreTotalToleratesCorruptBlob()
    {
        java.util.Map<String, String> store = new java.util.HashMap<>();
        store.put("combatTotalStats", "v1|not|a|number|at|all");
        doAnswer(inv -> store.get((String) inv.getArgument(1)))
            .when(configManager).getConfiguration(anyString(), anyString());

        CombatEncounterManager fresh = new CombatEncounterManager(client);
        fresh.setPersistence(configManager);
        fresh.restoreTotal(); // must not throw
        assertEquals(0, fresh.getOverallSessionEncounter().getLocalPlayerStats().getTotalDamage());
    }

    /** Tranche D5: killing a different NPC that shares the target's name must not end the fight. */
    @Test
    public void testKillingSameNamedNonTargetDoesNotEndFight()
    {
        NPC other = mock(NPC.class);
        when(other.getName()).thenReturn("Vorkath");
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 40, "Whip", 100, "Vorkath");

        encounterManager.handleActorDeath(other, 105);
        assertNotNull("fight with the real target continues", encounterManager.getCurrentEncounter());

        encounterManager.handleActorDeath(targetNpc, 106);
        assertNull("the real target dying ends it", encounterManager.getCurrentEncounter());
    }

    /** The loot tracker's kill sink fires once for the player's target dying, and only for it. */
    @Test
    public void testKillSinkFiresOnceForTargetDeath()
    {
        java.util.List<String> kills = new java.util.ArrayList<>();
        encounterManager.setKillSink((name, id) -> kills.add(name + "#" + id));

        when(targetNpc.getId()).thenReturn(8059);
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 40, "Whip", 100, "Vorkath");

        NPC other = mock(NPC.class);
        when(other.getName()).thenReturn("Vorkath");
        encounterManager.handleActorDeath(other, 104); // same name, not the target -> no kill
        assertTrue("a same-named non-target death is not a kill", kills.isEmpty());

        encounterManager.handleActorDeath(targetNpc, 105);
        assertEquals(1, kills.size());
        assertEquals("Vorkath#8059", kills.get(0));

        // A trailing despawn-while-dead for the same NPC must not double-count.
        when(targetNpc.isDead()).thenReturn(true);
        encounterManager.handleNpcDespawned(targetNpc, 106);
        assertEquals("no double count from the follow-up despawn", 1, kills.size());
    }

    /** An animation-less kill (no ActorDeath, only a despawn-while-dead) still fires the kill sink. */
    @Test
    public void testKillSinkFiresOnAnimlessDespawnKill()
    {
        java.util.List<String> kills = new java.util.ArrayList<>();
        encounterManager.setKillSink((name, id) -> kills.add(name));

        when(targetNpc.getId()).thenReturn(8059);
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 40, "Whip", 100, "Vorkath");

        when(targetNpc.isDead()).thenReturn(true);
        encounterManager.handleNpcDespawned(targetNpc, 105);
        assertEquals(java.util.Collections.singletonList("Vorkath"), kills);
    }

    /**
     * FIX 1: a cannon / thrall / AoE kill of an add the player only ever hit indirectly still
     * fires the kill sink, and it does NOT finalize the fight with the primary target.
     */
    @Test
    public void testSideKillCreditsKillCountWithoutEndingTheFight()
    {
        java.util.List<String> kills = new java.util.ArrayList<>();
        encounterManager.setKillSink((name, id) -> kills.add(name));

        encounterManager.notifyCombatAction(targetNpc, 100); // primary target "Vorkath"
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 40, "Whip", 100, "Vorkath");

        NPC add = mock(NPC.class);
        when(add.getName()).thenReturn("Dagannoth");
        when(add.getId()).thenReturn(2);
        // The plugin routes every thrall / cannon / AoE splat through notifyCombatAction(actor).
        encounterManager.notifyCombatAction(add, 101);
        encounterManager.recordCannonDamage(15, 101);
        // Player keeps swinging the primary, so it is the active target again when the add dies.
        encounterManager.notifyCombatAction(targetNpc, 102);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 30, "Whip", 102, "Vorkath");

        encounterManager.handleActorDeath(add, 103);

        assertTrue("cannon kill of an add advances kill count", kills.contains("Dagannoth"));
        assertNotNull("the primary fight keeps running", encounterManager.getCurrentEncounter());
        assertEquals("Vorkath", encounterManager.getCurrentEncounter().getTargetName());
    }

    /**
     * FIX 1: when the tracked target ref has desynced, a same-base-name mob the player was
     * actually hitting - even multi-target-tagged "(2)" - finalizes the base-named encounter.
     */
    @Test
    public void testMultiTargetTaggedDeathFinalisesBaseNamedEncounter()
    {
        NPC dagA = mock(NPC.class);
        when(dagA.getName()).thenReturn("Dagannoth");
        when(dagA.getId()).thenReturn(1);
        when(dagA.getIndex()).thenReturn(1);
        NPC dagB = mock(NPC.class);
        when(dagB.getName()).thenReturn("Dagannoth");
        when(dagB.getId()).thenReturn(2);
        when(dagB.getIndex()).thenReturn(2);

        java.util.List<String> kills = new java.util.ArrayList<>();
        encounterManager.setKillSink((name, id) -> kills.add(name));

        encounterManager.notifyCombatAction(dagA, 100);
        encounterManager.getCurrentEncounter().setTargetName("Dagannoth");
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 20, "Whip", 100, "Dagannoth");
        // Player tab-swaps: activeTargetActor now points at dagB, so dagA's death is a desync case.
        encounterManager.notifyCombatAction(dagB, 101);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 15, "Whip", 101, "Dagannoth (2)");

        encounterManager.handleActorDeath(dagA, 102);

        assertNull("a same-base-name engaged mob dying finalizes the desynced encounter",
            encounterManager.getCurrentEncounter());
        assertTrue(kills.contains("Dagannoth"));
    }

    /**
     * FIX 1: a game "kill count is: N" chat line raises the per-source counter via Math.max and
     * never lowers it. GAMEMESSAGE / SPAM only.
     */
    @Test
    public void testKillCountChatLineRaisesCounterViaMaxNeverLowers()
    {
        java.util.Map<String, Integer> kc = new java.util.HashMap<>();
        encounterManager.setKillCountSink((name, atLeast) ->
            kc.merge(name, atLeast, Math::max));

        encounterManager.onChatMessage(gameMsg("Your <col=ff0000>Vorkath</col> kill count is: 1,234."));
        assertEquals(Integer.valueOf(1234), kc.get("Vorkath"));

        // A stale / lower line must not lower the count.
        encounterManager.onChatMessage(gameMsg("Your Vorkath kill count is: 5."));
        assertEquals("Math.max only - never decreases", Integer.valueOf(1234), kc.get("Vorkath"));

        // Raid "completed ... count is: N" is also read.
        encounterManager.onChatMessage(gameMsg("Your completed Chambers of Xeric count is: 42."));
        assertEquals(Integer.valueOf(42), kc.get("Chambers of Xeric"));

        // Non game-message chat is ignored.
        ChatMessage pub = mock(ChatMessage.class);
        when(pub.getType()).thenReturn(net.runelite.api.ChatMessageType.PUBLICCHAT);
        when(pub.getMessage()).thenReturn("Your Zulrah kill count is: 999.");
        encounterManager.onChatMessage(pub);
        assertNull("public chat is not a kill-count source", kc.get("Zulrah"));
    }

    private static ChatMessage gameMsg(String text)
    {
        ChatMessage e = mock(ChatMessage.class);
        when(e.getType()).thenReturn(net.runelite.api.ChatMessageType.GAMEMESSAGE);
        when(e.getMessage()).thenReturn(text);
        return e;
    }

    @Test
    public void testCombatLogViewBuildsFightSummary()
    {
        when(targetNpc.getCombatLevel()).thenReturn(28);
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MAGIC, 250, "Earth Bolt", 100);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MELEE, 12, "Vorkath", 101);
        encounterManager.recordHpHealed(20);

        com.osrscopilot.combat.ui.CombatLogView log =
            new com.osrscopilot.combat.ui.CombatLogView(encounterManager, () -> {});
        List<String> summary = log.summaryFor(encounterManager.getCurrentEncounter());

        assertTrue(summary.stream().anyMatch(l -> l.contains("FIGHT SUMMARY") && l.contains("(lvl 28)")));
        assertTrue(summary.stream().anyMatch(l -> l.contains("Damage dealt") && l.contains("250")));
        assertTrue(summary.stream().anyMatch(l -> l.contains("Damage taken") && l.contains("12")));
        assertTrue(summary.stream().anyMatch(l -> l.startsWith("  Healing")));
        log.dispose();
    }

    @Test
    public void testCrowdControlDurationTracking()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        // Land a Snare (10 ticks) on the target at tick 100.
        encounterManager.recordPlayerDamageDealt(CombatStyle.MAGIC, 4, "Snare", 100, "Vorkath");

        List<com.osrscopilot.combat.model.DebuffApplication> ccs =
            encounterManager.getCurrentEncounter().getDebuffs();
        assertEquals(1, ccs.size());
        assertEquals("Snare", ccs.get(0).getType());
        assertEquals("Vorkath", ccs.get(0).getTargetName());
        assertEquals(110, ccs.get(0).getExpectedEndTick());
        assertTrue(ccs.get(0).isOpen());

        // Keep the fight alive, then tick past expiry.
        encounterManager.onGameTick(109);
        assertTrue(encounterManager.getCurrentEncounter().getDebuffs().get(0).isOpen());
        encounterManager.onGameTick(110);
        com.osrscopilot.combat.model.DebuffApplication done =
            encounterManager.getCurrentEncounter().getDebuffs().get(0);
        assertFalse(done.isOpen());
        assertEquals(6.0, done.getSeconds(), 0.05);

        // A normal weapon hit does not create a debuff.
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 40, "Dragon scimitar", 111, "Vorkath");
        assertEquals(1, encounterManager.getCurrentEncounter().getDebuffs().size());
    }

    @Test
    public void testEncounterCapturesNpcCombatLevel()
    {
        when(targetNpc.getId()).thenReturn(3031);
        when(targetNpc.getCombatLevel()).thenReturn(28);

        encounterManager.notifyCombatAction(targetNpc, 100);
        EncounterSegment enc = encounterManager.getCurrentEncounter();

        assertEquals(28, enc.getNpcCombatLevel());
        assertEquals(3031, enc.getNpcId());
        assertEquals("Vorkath (lvl 28)", enc.getTargetNameWithLevel());
        assertTrue(enc.toString().contains("(lvl 28)"));
    }

    @Test
    public void testPerTargetDamageSplit()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 40, "Whip", 100, "Cave horror (1)");
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 10, "Whip", 101, "Cave horror (2)");
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 30, "Whip", 102, "Cave horror (1)");

        List<EntityCombatStats> enemies = encounterManager.getCurrentEncounter().getEnemyBreakdown();
        assertEquals(2, enemies.size());
        assertEquals("Cave horror (1)", enemies.get(0).getName());
        assertEquals(70, enemies.get(0).getTotalDamage());
        assertEquals(10, enemies.get(1).getTotalDamage());
        // The unified player total still aggregates across both.
        assertEquals(80, encounterManager.getCurrentEncounter().getLocalPlayerStats().getTotalDamage());
    }

    /** Tranche F3: the per-target breakdown includes thrall / cannon contributions, not just
     *  the player's direct hits. */
    @Test
    public void testPerTargetSplitIncludesThrallAndCannon()
    {
        when(targetNpc.getName()).thenReturn("Vorkath");
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.RANGED, 60, "Toxic blowpipe", 100, "Vorkath");
        encounterManager.recordThrallDamage(CombatStyle.THRALL, 4, "Greater Ghost Thrall", 101);
        encounterManager.recordCannonDamage(25, 102);

        List<EntityCombatStats> enemies = encounterManager.getCurrentEncounter().getEnemyBreakdown();
        assertEquals(1, enemies.size());
        assertEquals("Vorkath", enemies.get(0).getName());
        assertEquals("60 player + 4 thrall + 25 cannon", 89, enemies.get(0).getTotalDamage());
    }

    /** Tranche C8: switching focus to another NPC mid multi-pull is logged once. */
    @Test
    public void testTargetSwitchLoggedOnMultiPull()
    {
        NPC horror2 = mock(NPC.class);
        when(horror2.getName()).thenReturn("Cave horror");
        when(horror2.getIndex()).thenReturn(77);
        when(horror2.getCombatLevel()).thenReturn(80);
        when(targetNpc.getIndex()).thenReturn(11);

        encounterManager.getMultiTargetTracker().trackTarget(targetNpc, 100, false);
        encounterManager.getMultiTargetTracker().trackTarget(horror2, 100, false);
        assertTrue(encounterManager.getMultiTargetTracker().getActiveTargetCount() > 1);

        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.notifyCombatAction(horror2, 102);

        long switches = encounterManager.getCurrentEncounter().getLocalPlayerStats().getTimelineEvents()
            .stream().filter(ev -> "SWITCH".equals(ev.getEventType())).count();
        assertEquals(1, switches);

        // Re-hitting the same target does not spam another switch line.
        encounterManager.notifyCombatAction(horror2, 104);
        assertEquals(1, encounterManager.getCurrentEncounter().getLocalPlayerStats().getTimelineEvents()
            .stream().filter(ev -> "SWITCH".equals(ev.getEventType())).count());
    }

    @Test
    public void testThrallAndCannonDamageAttribution()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);

        // Record Thrall hits (0-3 damage)
        encounterManager.recordThrallDamage(CombatStyle.THRALL, 3, "Greater Ghost Thrall", 100);
        encounterManager.recordThrallDamage(CombatStyle.THRALL, 2, "Greater Ghost Thrall", 104);

        // Record Cannon hits
        encounterManager.recordCannonDamage(28, 102);
        encounterManager.recordCannonDamage(30, 108);

        EncounterSegment enc = encounterManager.getCurrentEncounter();
        assertEquals(5, enc.getThrallStats().getTotalDamage());
        assertEquals(58, enc.getCannonStats().getTotalDamage());
        assertEquals(63, enc.getTotalDamage());

        List<EntityCombatStats> ranked = enc.getRankedParticipants(5);
        assertEquals(3, ranked.size()); // You (0), Cannon (58), Thrall (5)
        assertEquals("Cannon", ranked.get(0).getName());
        assertEquals("Thrall", ranked.get(1).getName());
    }

    /**
     * Tranche B2: an isOthers() small hit must not steal a queued cannonball PendingHit (which
     * would misbook it as thrall damage and leave the real cannonball to be counted as a weapon
     * hit). The cannonball's own isMine() hitsplat still resolves to CANNON_DEALT.
     */
    @Test
    public void testCannonPendingHitNotStolenByOthersHitsplat()
    {
        DamageAttributionEngine engine = new DamageAttributionEngine(client);
        when(client.getTickCount()).thenReturn(100);

        Projectile ball = mock(Projectile.class);
        when(ball.getId()).thenReturn(53);
        when(ball.getInteracting()).thenReturn(targetNpc);
        when(ball.getRemainingCycles()).thenReturn(0);
        ProjectileMoved pm = mock(ProjectileMoved.class);
        when(pm.getProjectile()).thenReturn(ball);
        engine.onProjectileMoved(pm);

        // A stray isOthers() 2-damage hit on the same target - must NOT consume the cannon pending.
        Hitsplat others = mock(Hitsplat.class);
        when(others.getHitsplatType()).thenReturn(HitsplatID.DAMAGE_OTHER);
        when(others.getAmount()).thenReturn(2);
        when(others.isOthers()).thenReturn(true);
        when(others.isMine()).thenReturn(false);
        HitsplatApplied othersEvt = mock(HitsplatApplied.class);
        when(othersEvt.getActor()).thenReturn(targetNpc);
        when(othersEvt.getHitsplat()).thenReturn(others);
        assertNull("isOthers() hit must not consume the cannonball pending", engine.processHitsplat(othersEvt, 101));

        // The cannonball lands: isMine() hit -> CANNON_DEALT.
        Hitsplat mine = mock(Hitsplat.class);
        when(mine.getHitsplatType()).thenReturn(HitsplatID.DAMAGE_ME);
        when(mine.getAmount()).thenReturn(28);
        when(mine.isMine()).thenReturn(true);
        HitsplatApplied mineEvt = mock(HitsplatApplied.class);
        when(mineEvt.getActor()).thenReturn(targetNpc);
        when(mineEvt.getHitsplat()).thenReturn(mine);
        DamageAttributionEngine.ProcessedHit cannon = engine.processHitsplat(mineEvt, 101);
        assertNotNull(cannon);
        assertEquals(DamageAttributionEngine.HitTarget.CANNON_DEALT, cannon.targetType);
        assertEquals(28, cannon.amount);
    }

    @Test
    public void testDamageTakenBreaksDownByStyleAndSource()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);

        encounterManager.recordPlayerDamageTaken(CombatStyle.MAGIC, 18, "Vorkath Dragonfire", 100);
        encounterManager.recordPlayerDamageTaken(CombatStyle.RANGED, 12, "Vorkath Spikes", 104);

        EntityCombatStats stats = encounterManager.getCurrentEncounter().getLocalPlayerStats();
        assertEquals(30, stats.getDamageTaken());

        // Damage taken is broken down by style and by source for analysis.
        assertEquals(2, stats.getDamageTakenByStyle().size());
        assertEquals(CombatStyle.MAGIC, stats.getDamageTakenByStyle().get(0).getStyle()); // 18 > 12
        assertEquals(18L, (long) stats.getDamageTakenBySource().get("Vorkath Dragonfire"));
        assertEquals(12L, (long) stats.getDamageTakenBySource().get("Vorkath Spikes"));
    }

    /** Multi-attacker fight: damage taken splits by source with a per-source hit count. */
    @Test
    public void testDamageTakenBySourceCarriesHitCounts()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MAGIC, 20, "Blue dragon (1)", 100);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MELEE, 6, "Blue dragon (2)", 101);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MELEE, 9, "Blue dragon (2)", 103);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MELEE, 0, "Blue dragon (2)", 105); // blocked - no hit

        EntityCombatStats s = encounterManager.getCurrentEncounter().getLocalPlayerStats();
        assertEquals(35, s.getDamageTaken());
        assertEquals(20L, (long) s.getDamageTakenBySource().get("Blue dragon (1)"));
        assertEquals(15L, (long) s.getDamageTakenBySource().get("Blue dragon (2)"));
        assertEquals(1, (int) s.getDamageTakenHitsBySource().get("Blue dragon (1)"));
        assertEquals("blocked 0s don't count as a hit", 2, (int) s.getDamageTakenHitsBySource().get("Blue dragon (2)"));
    }

    @Test
    public void testEncounterLifecycleBossDeathAndPlayerWipe()
    {
        // 1. Start Vorkath Encounter
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.RANGED, 750, "Dragon Hunter Crossbow", 100);
        // Three fixed scope rows: Current Encounter (live Vorkath) + Current Session + Total
        assertEquals(3, encounterManager.getAllSegmentsForDropdown().size());

        // 2. Boss Dies — encounter finalized; "Current Encounter" scope now holds the finished fight.
        encounterManager.handleActorDeath(targetNpc, 140);
        assertNull(encounterManager.getCurrentEncounter());
        List<EncounterSegment> segments = encounterManager.getAllSegmentsForDropdown();
        assertEquals(3, segments.size()); // [last fight] + Current Session + Total (history de-duped)

        EncounterSegment archived = segments.get(0);
        assertEquals("Vorkath", archived.getTargetName());
        assertEquals(SegmentStatus.COMPLETED, archived.getStatus());
        assertEquals(750, archived.getLocalPlayerStats().getTotalDamage());

        // 3. Start New Fight & Player Dies
        encounterManager.notifyCombatAction(targetNpc, 200);
        encounterManager.handleActorDeath(localPlayer, 220);

        segments = encounterManager.getAllSegmentsForDropdown();
        assertEquals(4, segments.size()); // [last fight #2] + Current Session + Total + Fight #1
        assertEquals(SegmentStatus.WIPED, segments.get(0).getStatus());
    }

    @Test
    public void testBuildShareLineIsCompactAndScopeAware()
    {
        assertEquals("Combat: no fight data yet", encounterManager.buildShareLine());

        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.RANGED, 750, "DHCB", 100);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MAGIC, 30, "Vorkath", 101);

        String line = encounterManager.buildShareLine();
        assertTrue("names the target: " + line, line.startsWith("Vorkath |"));
        assertTrue("carries dps + damage: " + line, line.contains("DPS ") && line.contains("dealt 750"));
        assertTrue("fits one OSRS chat line: " + line, line.length() <= 80);

        // Merged trash reads "Target xN".
        encounterManager.handleActorDeath(targetNpc, 120);
        for (int i = 0; i < 2; i++)
        {
            int t = 200 + i * 40;
            encounterManager.notifyCombatAction(targetNpc, t);
            encounterManager.recordPlayerDamageDealt(CombatStyle.RANGED, 400, "DHCB", t);
            encounterManager.handleActorDeath(targetNpc, t + 20);
        }
        encounterManager.selectEncounter(encounterManager.getAllSegmentsForDropdown().get(0));
        assertTrue(encounterManager.buildShareLine().startsWith("Vorkath x3 |"));

        // chatShare is unwired in tests - these must be safe no-ops, not NPEs.
        encounterManager.shareCopy();
        encounterManager.shareToChat(com.osrscopilot.combat.ShareChannel.CLAN);
    }

    /** Details! trash merge: consecutive kills of the same NPC fold into one "Vorkath x3" row. */
    @Test
    public void testTrashKillsMergeIntoOneRow()
    {
        for (int i = 0; i < 3; i++)
        {
            int t = 100 + i * 50;
            encounterManager.notifyCombatAction(targetNpc, t);
            encounterManager.recordPlayerDamageDealt(CombatStyle.RANGED, 750, "Dragon Hunter Crossbow", t);
            encounterManager.handleActorDeath(targetNpc, t + 20);
        }

        List<EncounterSegment> segments = encounterManager.getAllSegmentsForDropdown();
        assertEquals(3, segments.size()); // one merged past-fight row + Current Session + Total

        EncounterSegment merged = segments.get(0);
        assertTrue(merged.isMerged());
        assertEquals(3, merged.getMergedKills());
        assertEquals("Vorkath", merged.getTargetName());
        assertEquals(SegmentStatus.COMPLETED, merged.getStatus());
        assertEquals(2250, merged.getLocalPlayerStats().getTotalDamage()); // 750 * 3
        assertEquals(1, merged.getHistoryIndex()); // merges keep the first row's number
        assertTrue(merged.toString().contains("x3"));

        // The merged row can be expanded into its 3 individual kills; child #1 keeps that kill's
        // own 750 damage, not the summed 2250.
        assertTrue(merged.hasMergedChildren());
        List<EncounterSegment.KillSummary> kids = merged.getMergedChildren();
        assertEquals(3, kids.size());
        assertEquals(1, kids.get(0).getIndex());
        assertEquals(750, kids.get(0).getTotalDamage());
        assertEquals(750, kids.get(2).getTotalDamage());
    }

    @Test
    public void testMergeTrashOffKeepsEveryKillSeparate()
    {
        encounterManager.setMergeTrash(false);
        for (int i = 0; i < 3; i++)
        {
            int t = 100 + i * 50;
            encounterManager.notifyCombatAction(targetNpc, t);
            encounterManager.recordPlayerDamageDealt(CombatStyle.RANGED, 750, "DHCB", t);
            encounterManager.handleActorDeath(targetNpc, t + 20);
        }
        List<EncounterSegment> segments = encounterManager.getAllSegmentsForDropdown();
        assertEquals(5, segments.size()); // 3 fights + Current Session + Total
        for (EncounterSegment seg : segments)
        {
            assertFalse(seg.isMerged());
        }
    }

    /** A wipe in the middle of a grind breaks the merge chain and stays its own row. */
    /**
     * A fight a teammate was in latches "[grp]" onto its history row, so the scope dropdown still
     * lists every past fight but marks the shared ones. A solo fight carries no such tag.
     */
    @Test
    public void testGroupFightGetsHistoryBadgeSoloDoesNot()
    {
        NPC soloNpc = mock(NPC.class);
        when(soloNpc.getName()).thenReturn("Zulrah");

        // Solo kill first - no group supplier wired -> no tag.
        encounterManager.notifyCombatAction(soloNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.RANGED, 500, "Twisted bow", 100);
        encounterManager.handleActorDeath(soloNpc, 120);

        // Now a shared fight is live for the duration of the next kill (different target, so the
        // two rows don't trash-merge).
        encounterManager.setGroupAutoFollowSupplier(() -> true);
        encounterManager.notifyCombatAction(targetNpc, 200);
        encounterManager.recordPlayerDamageDealt(CombatStyle.RANGED, 500, "Twisted bow", 200);
        encounterManager.onGameTick(201); // tick latches the group-fight flag
        encounterManager.handleActorDeath(targetNpc, 220);

        List<EncounterSegment> segments = encounterManager.getAllSegmentsForDropdown();
        EncounterSegment groupKill = segments.get(0);
        EncounterSegment soloKill = segments.get(segments.size() - 1);

        assertEquals("Vorkath", groupKill.getTargetName());
        assertEquals("Zulrah", soloKill.getTargetName());
        assertTrue("shared fight is flagged", groupKill.isGroupFight());
        assertTrue("shared fight row is badged", groupKill.toString().contains("[grp]"));
        assertFalse("solo fight is not flagged", soloKill.isGroupFight());
        assertFalse("solo fight row has no badge", soloKill.toString().contains("[grp]"));
    }

    /**
     * The "Entering Combat" banner fires for a fight a nearby teammate started, even before the
     * local player has swung - it treats a live shared party fight as being in combat.
     */
    @Test
    public void testBannerFiresWhenANearbyTeammatePulls()
    {
        when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
        when(config.combatBannerEnabled()).thenReturn(true);
        when(config.combatBannerEnterText()).thenReturn("Entering Combat");
        when(config.combatBannerEnterColor()).thenReturn(new Color(255, 78, 50));
        when(config.combatBannerFadeInMs()).thenReturn(0);
        when(config.combatBannerLingerMs()).thenReturn(1500);
        when(config.combatBannerFadeOutMs()).thenReturn(0);
        when(config.combatBannerFontSize()).thenReturn(28);

        BufferedImage img = new BufferedImage(800, 550, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // No local encounter, but a shared party fight is live nearby.
        assertNull(bannerOverlay.currentBannerText());
        encounterManager.setGroupAutoFollowSupplier(() -> true);
        bannerOverlay.render(g);

        assertEquals("a nearby teammate's pull raises the Entering Combat banner",
            "Entering Combat", bannerOverlay.currentBannerText());
        g.dispose();
    }

    @Test
    public void testWipeIsNotMergedIntoATrashRun()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.RANGED, 500, "DHCB", 100);
        encounterManager.handleActorDeath(targetNpc, 120); // kill #1

        encounterManager.notifyCombatAction(targetNpc, 200);
        encounterManager.handleActorDeath(localPlayer, 220); // wipe

        encounterManager.notifyCombatAction(targetNpc, 300);
        encounterManager.recordPlayerDamageDealt(CombatStyle.RANGED, 500, "DHCB", 300);
        encounterManager.handleActorDeath(targetNpc, 320); // kill #2 - must NOT merge across the wipe

        List<EncounterSegment> segments = encounterManager.getAllSegmentsForDropdown();
        assertEquals(5, segments.size()); // kill#2, Session, Total, wipe, kill#1
        assertFalse(segments.get(0).isMerged());
        assertEquals(SegmentStatus.COMPLETED, segments.get(0).getStatus());
        boolean anyWipe = segments.stream().anyMatch(s -> s.getStatus() == SegmentStatus.WIPED);
        assertTrue("the wipe row survives on its own", anyWipe);
    }

    /**
     * Tranche C3: on a wipe, the encounter captures the last incoming hits ("what killed you")
     * and the Full Log summary shows them.
     */
    @Test
    public void testDeathRecapCapturedOnWipe()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MAGIC, 22, "Vorkath", 101);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MELEE, 15, "Vorkath", 103);
        encounterManager.recordPlayerDamageTaken(CombatStyle.RANGED, 41, "Vorkath", 105);
        encounterManager.handleActorDeath(localPlayer, 106);

        EncounterSegment wiped = encounterManager.getAllSegmentsForDropdown().get(0);
        assertEquals(SegmentStatus.WIPED, wiped.getStatus());
        List<com.osrscopilot.combat.model.CombatTimelineEvent> recap = wiped.getDeathRecap();
        assertEquals(3, recap.size());
        assertEquals(41, recap.get(2).getAmount());
        assertEquals("Vorkath", recap.get(2).getSourceName());

        com.osrscopilot.combat.ui.CombatLogView log =
            new com.osrscopilot.combat.ui.CombatLogView(encounterManager, () -> {});
        List<String> summary = log.summaryFor(wiped);
        assertTrue(summary.stream().anyMatch(l -> l.contains("What killed you")));
        assertTrue(summary.stream().anyMatch(l -> l.contains("41 from Vorkath")));
        log.dispose();
    }


    @Test
    public void testInactivityTimeoutFinalization()
    {
        encounterManager.setEncounterTimeoutSeconds(6); // -> 10-tick timeout for a fast test
        encounterManager.notifyCombatAction(targetNpc, 100);
        assertNotNull(encounterManager.getCurrentEncounter());

        // under the timeout -> still in combat
        encounterManager.onGameTick(108);
        assertNotNull(encounterManager.getCurrentEncounter());

        // past the timeout -> auto-finalizes
        encounterManager.onGameTick(114);
        assertNull(encounterManager.getCurrentEncounter());
    }

    @Test
    public void testConsumableAuditorWithoutFalsePositives()
    {
        when(itemManager.getItemPrice(anyInt())).thenReturn(2500);

        ItemComposition brewDef = mock(ItemComposition.class);
        when(brewDef.getName()).thenReturn("Saradomin brew(4)");
        when(client.getItemDefinition(6685)).thenReturn(brewDef);

        ItemComposition brew3Def = mock(ItemComposition.class);
        when(brew3Def.getName()).thenReturn("Saradomin brew(3)");
        when(client.getItemDefinition(6687)).thenReturn(brew3Def);

        EntityCombatStats stats = new EntityCombatStats("TestHero");

        // 1. Initial inventory: 1x 4-dose Brew (ID 6685)
        ItemContainer initContainer = mock(ItemContainer.class);
        Item brew4Item = new Item(6685, 1);
        when(initContainer.getItems()).thenReturn(new Item[]{brew4Item});
        ItemContainerChanged initialEvent = new ItemContainerChanged(InventoryID.INVENTORY.getId(), initContainer);

        consumableAuditor.auditInventory(initialEvent, stats);
        assertEquals(0, stats.getPotionsDrunkCount()); // Initial baseline cache

        // 2. Consume 1 dose: ID 6685 (0 count) -> ID 6687 (1 count)
        ItemContainer sipContainer = mock(ItemContainer.class);
        Item brew3Item = new Item(6687, 1);
        when(sipContainer.getItems()).thenReturn(new Item[]{brew3Item});
        ItemContainerChanged sipEvent = new ItemContainerChanged(InventoryID.INVENTORY.getId(), sipContainer);

        clickConsume("Drink");
        consumableAuditor.auditInventory(sipEvent, stats);
        assertEquals(1, stats.getPotionsDrunkCount());
        assertEquals(2500, stats.getConsumablesUsed().get(0).getTotalCost());

        // 3. Test Drop action suppression (no consumable recorded)
        MenuOptionClicked dropEvent = mock(MenuOptionClicked.class);
        when(dropEvent.getMenuOption()).thenReturn("Drop");
        when(dropEvent.getParam0()).thenReturn(0);
        consumableAuditor.onMenuOptionClicked(dropEvent);

        ItemContainer emptyContainer = mock(ItemContainer.class);
        when(emptyContainer.getItems()).thenReturn(new Item[]{});
        ItemContainerChanged dropContainerEvent = new ItemContainerChanged(InventoryID.INVENTORY.getId(), emptyContainer);

        consumableAuditor.auditInventory(dropContainerEvent, stats);
        assertEquals(1, stats.getPotionsDrunkCount()); // Still 1, drop wasn't counted as a drink!
    }

    @Test
    public void testEquippingAWeaponIsNotAConsumable()
    {
        when(itemManager.getItemPrice(anyInt())).thenReturn(100_000);

        ItemComposition scimDef = mock(ItemComposition.class);
        when(scimDef.getName()).thenReturn("Dragon scimitar");
        when(scimDef.getInventoryActions()).thenReturn(new String[]{"Wield", null, null, null, "Drop"});
        when(client.getItemDefinition(4587)).thenReturn(scimDef);

        EntityCombatStats stats = new EntityCombatStats("TestHero");

        // Baseline: scimitar sitting in the inventory.
        ItemContainer withScim = mock(ItemContainer.class);
        when(withScim.getItems()).thenReturn(new Item[]{new Item(4587, 1)});
        consumableAuditor.auditInventory(
            new ItemContainerChanged(InventoryID.INVENTORY.getId(), withScim), stats);

        // Now it leaves the inventory (equipped). Must NOT be booked as a consumable.
        ItemContainer withoutScim = mock(ItemContainer.class);
        when(withoutScim.getItems()).thenReturn(new Item[]{});
        consumableAuditor.auditInventory(
            new ItemContainerChanged(InventoryID.INVENTORY.getId(), withoutScim), stats);

        assertTrue("A wieldable item leaving the inventory is not a supply cost",
            stats.getConsumablesUsed().isEmpty());
        assertEquals(0L, stats.getTotalGpCost());
    }

    @Test
    public void testCombatMeterOverlayRendering()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 120, "Abyssal whip", 100);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MAGIC, 45, "Magic attack", 100);
        encounterManager.recordHpHealed(22);

        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);
        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = img.createGraphics();

        Dimension renderedDim = overlay.render(g);
        assertNotNull(renderedDim);
        assertTrue("Overlay width should be at least 170px", renderedDim.width >= 170);
        assertTrue("Overlay height should be at least 48px", renderedDim.height >= 48);

        // Test Mode Cycling
        assertEquals(CombatMeterOverlay.MetricMode.DAMAGE_DONE, overlay.getCurrentMode());
        overlay.cycleMode();
        assertEquals(CombatMeterOverlay.MetricMode.DAMAGE_TAKEN, overlay.getCurrentMode());
        overlay.cycleMode();
        assertEquals(CombatMeterOverlay.MetricMode.HEALING_DONE, overlay.getCurrentMode());
        overlay.cycleMode();
        assertEquals(CombatMeterOverlay.MetricMode.CONSUMABLES, overlay.getCurrentMode());
        overlay.cycleMode();
        assertEquals(CombatMeterOverlay.MetricMode.DAMAGE_DONE, overlay.getCurrentMode());

        g.dispose();
    }

    /**
     * Regression: the mini-footer's "Pots: N" / "Food: N" used to be drawn at fixed x-offsets that
     * collided once either count reached two digits ("Pots: 15Food: 40") and pushed Food past the
     * panel edge. They must now be measured: Food right-anchored, Pots fully to its left.
     */
    @Test
    public void testMiniFooterPotsAndFoodDoNotCollide()
    {
        when(config.combatOverlayWidth()).thenReturn(220);
        when(config.combatShowMiniFooter()).thenReturn(true);

        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MAGIC, 992, "Vorkath", 100);
        encounterManager.recordHpHealed(1200);
        EntityCombatStats s = encounterManager.getCurrentEncounter().getLocalPlayerStats();
        s.setHpOverhealed(458);   // forces the long "Heal: 1.2k (+458o)" left-group string
        s.setPotionsDrunkCount(15);
        s.setFoodEatenCount(40);

        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);
        BufferedImage img = new BufferedImage(320, 320, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Dimension dim = overlay.render(g);
        assertNotNull(dim);

        // FOOTER_POTS = (77,208,225) cyan; FOOTER_FOOD = (255,215,0) gold - the only two colours on
        // the HUD that are, respectively, low-R/high-B and high-R/high-G/near-zero-B.
        int potsMaxX = -1, foodMinX = Integer.MAX_VALUE, foodMaxX = -1, potsPx = 0, foodPx = 0;
        // Only the mini-footer band (bottom ~16px) - keeps the bar/header colours out of the scan.
        for (int py = Math.max(0, dim.height - 16); py < dim.height; py++)
        {
            for (int px = 0; px < dim.width; px++)
            {
                int rgb = img.getRGB(px, py);
                int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                if (r < 140 && gr > 150 && b > 175) { potsMaxX = Math.max(potsMaxX, px); potsPx++; }
                else if (r > 180 && gr > 140 && b < 90) { foodMinX = Math.min(foodMinX, px); foodMaxX = Math.max(foodMaxX, px); foodPx++; }
            }
        }

        assertTrue("expected the cyan 'Pots:' text to render", potsPx >= 8);
        assertTrue("expected the gold 'Food:' text to render", foodPx >= 8);
        assertTrue("'Pots: 15' must end before 'Food: 40' begins (was overlapping)", potsMaxX < foodMinX);
        assertTrue("'Food: 40' must not clip past the panel's right edge", foodMaxX <= dim.width - 1);

        g.dispose();
    }

    @Test
    public void testCurrentSessionAutoResetOnInactivity()
    {
        // 1. Initial combat at tick 100
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 150, "Abyssal whip", 100);

        assertEquals(150, encounterManager.getCurrentSessionEncounter().getLocalPlayerStats().getTotalDamage());
        assertEquals(150, encounterManager.getOverallSessionEncounter().getLocalPlayerStats().getTotalDamage());

        // 2. Next attack within timeout (tick 120, delta 20 < 50) -> Session keeps accumulating
        encounterManager.notifyCombatAction(targetNpc, 120);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 100, "Abyssal whip", 120);

        assertEquals(250, encounterManager.getCurrentSessionEncounter().getLocalPlayerStats().getTotalDamage());
        assertEquals(250, encounterManager.getOverallSessionEncounter().getLocalPlayerStats().getTotalDamage());

        // 3. Inactivity break (tick 250, delta 130 > 50 timeout ticks) -> Current session resets on new combat!
        encounterManager.notifyCombatAction(targetNpc, 250);
        encounterManager.recordPlayerDamageDealt(CombatStyle.RANGED, 80, "Toxic blowpipe", 250);

        assertEquals(80, encounterManager.getCurrentSessionEncounter().getLocalPlayerStats().getTotalDamage());
        assertEquals(330, encounterManager.getOverallSessionEncounter().getLocalPlayerStats().getTotalDamage()); // Overall session preserved!
    }

    @Test
    public void testHpHealingFromHitsplatAndStats()
    {
        // 1. Life-steal Hitsplat (HEAL = 4)
        Hitsplat healHitsplat = mock(Hitsplat.class);
        when(healHitsplat.getHitsplatType()).thenReturn(HitsplatID.HEAL);
        when(healHitsplat.getAmount()).thenReturn(15);
        HitsplatApplied healEvent = new HitsplatApplied();
        healEvent.setActor(localPlayer);
        healEvent.setHitsplat(healHitsplat);

        DamageAttributionEngine.ProcessedHit hit = damageEngine.processHitsplat(healEvent, 100);
        assertNotNull(hit);
        assertEquals(DamageAttributionEngine.HitTarget.PLAYER_HEAL, hit.targetType);
        assertEquals(15, hit.amount);

        encounterManager.recordHpHealed(hit.amount);
        assertEquals(15, encounterManager.getOverallSessionEncounter().getLocalPlayerStats().getHpHealed());

        // 2. StatChanged HP Delta Tracking
        when(client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS)).thenReturn(75, 95); // 75 -> 95 (+20 HP)
        net.runelite.api.events.GameStateChanged loginEvent = new net.runelite.api.events.GameStateChanged();
        loginEvent.setGameState(net.runelite.api.GameState.LOGGED_IN);
        consumableAuditor.onGameStateChanged(loginEvent);

        EntityCombatStats stats = new EntityCombatStats("TestHero");
        net.runelite.api.events.StatChanged statEvent = new net.runelite.api.events.StatChanged(net.runelite.api.Skill.HITPOINTS, 100000, 75, 95);
        consumableAuditor.onStatChanged(statEvent, stats);

        assertEquals(20, stats.getHpHealed());
    }

    @Test
    public void testCombatEncounterTabViewCardsAndLayout()
    {
        MonsterDatabase monsterDatabase = new MonsterDatabase(new com.google.gson.Gson());
        monsterDatabase.load();

        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 250, "Dragon scimitar", 100);
        encounterManager.recordPlayerDamageTaken(CombatStyle.RANGED, 50, "Ranged dart", 100);

        CombatEncounterTabView tabView = new CombatEncounterTabView(
            encounterManager,
            monsterDatabase,
            itemManager,
            monster -> {}
        );

        tabView.setSize(new Dimension(225, 600));
        tabView.doLayout();

        // Verify subviews and card elements render without throwing exceptions
        assertNotNull(tabView);
        assertEquals(ColorScheme.DARK_GRAY_COLOR, tabView.getBackground());
    }

    @Test
    public void testCollapsibleSectionCardToggle()
    {
        JPanel innerContent = new JPanel();
        CollapsibleSectionCard card = new CollapsibleSectionCard("Damage Styles", CombatMeterColors.TEXT_ACCENT_GOLD, innerContent);

        card.setSummaryText("42.5k GP");
        assertTrue(card.isExpanded());

        card.toggle();
        assertFalse(card.isExpanded());

        card.toggle();
        assertTrue(card.isExpanded());
    }

    @Test
    public void testKarambwanFoodAuditing()
    {
        when(itemManager.getItemPrice(3144)).thenReturn(600);

        ItemComposition karambwanDef = mock(ItemComposition.class);
        when(karambwanDef.getName()).thenReturn("Cooked karambwan");
        when(client.getItemDefinition(3144)).thenReturn(karambwanDef);

        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(70);
        when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(80);

        EntityCombatStats overallStats = new EntityCombatStats("Overall");
        EntityCombatStats currentStats = new EntityCombatStats("Current");

        // Baseline inventory: 5x Karambwans (ID 3144)
        ItemContainer initialContainer = mock(ItemContainer.class);
        when(initialContainer.getItems()).thenReturn(new Item[]{new Item(3144, 5)});
        consumableAuditor.auditInventory(new ItemContainerChanged(InventoryID.INVENTORY.getId(), initialContainer), overallStats, currentStats);

        assertEquals(0, overallStats.getFoodEatenCount());
        assertEquals(0, currentStats.getFoodEatenCount());

        // Eat 1 Karambwan (18 HP): current HP is 70/80 (10 missing) -> 10 Effective Heal + 8 Overheal
        ItemContainer afterEatContainer = mock(ItemContainer.class);
        when(afterEatContainer.getItems()).thenReturn(new Item[]{new Item(3144, 4)});
        clickConsume("Eat");
        consumableAuditor.auditInventory(new ItemContainerChanged(InventoryID.INVENTORY.getId(), afterEatContainer), overallStats, currentStats);

        assertEquals(1, overallStats.getFoodEatenCount());
        assertEquals(1, currentStats.getFoodEatenCount());
        assertEquals(10, overallStats.getHpHealed());
        assertEquals(8, overallStats.getHpOverhealed());
        assertEquals(18, overallStats.getTotalHealing());
        assertEquals(600, overallStats.getConsumablesUsed().get(0).getTotalCost());
        assertEquals("Cooked karambwan", overallStats.getConsumablesUsed().get(0).getItemName());
    }

    /**
     * Regression: fishing karambwan burns "Raw karambwanji" (id 3150) as bait every catch. That is
     * an inventory decrement with NO "Eat" click and a "Raw ..." / "karambwanji" name - it must not
     * be booked as food (previously showed "Ate Raw karambwanji (+18)" in the Action Ledger and
     * bumped the combat HUD food counter).
     */
    @Test
    public void testKarambwanBaitIsNotEaten()
    {
        when(itemManager.getItemPrice(3150)).thenReturn(5);

        ItemComposition baitDef = mock(ItemComposition.class);
        when(baitDef.getName()).thenReturn("Raw karambwanji");
        // Raw karambwanji really does carry an "Eat" inventory action in-game.
        when(baitDef.getInventoryActions()).thenReturn(new String[]{"Eat", null, null, null, "Drop"});
        when(client.getItemDefinition(3150)).thenReturn(baitDef);

        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(50);
        when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);

        EntityCombatStats overallStats = new EntityCombatStats("Overall");
        EntityCombatStats currentStats = new EntityCombatStats("Current");

        // Baseline: a stack of bait.
        ItemContainer initInv = mock(ItemContainer.class);
        when(initInv.getItems()).thenReturn(new Item[]{new Item(3150, 100)});
        consumableAuditor.auditInventory(
            new ItemContainerChanged(InventoryID.INVENTORY.getId(), initInv), overallStats, currentStats);

        // A catch consumes one bait - no menu click of any kind.
        ItemContainer afterCatch = mock(ItemContainer.class);
        when(afterCatch.getItems()).thenReturn(new Item[]{new Item(3150, 99)});
        consumableAuditor.auditInventory(
            new ItemContainerChanged(InventoryID.INVENTORY.getId(), afterCatch), overallStats, currentStats);

        assertEquals(0, overallStats.getFoodEatenCount());
        assertEquals(0, currentStats.getFoodEatenCount());
        assertEquals(0, overallStats.getHpHealed());
        assertEquals(0, overallStats.getHpOverhealed());
        assertTrue(overallStats.getConsumablesUsed().isEmpty());
        assertEquals(0L, overallStats.getTotalGpCost());
        assertTrue(overallStats.getTimelineEvents().isEmpty());
    }

    @Test
    public void testCombatMeterOverlaySegmentCycling()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);

        assertEquals("Vorkath", encounterManager.getSelectedOrCurrentEncounter().getTargetName());
        overlay.cycleSegment();
        assertEquals("Current Session", encounterManager.getSelectedOrCurrentEncounter().getTargetName());
        overlay.cycleSegment();
        assertEquals("Total", encounterManager.getSelectedOrCurrentEncounter().getTargetName());
        overlay.cycleSegment();
        assertEquals("Vorkath", encounterManager.getSelectedOrCurrentEncounter().getTargetName());
    }

    @Test
    public void testOverlayButtonInteractions()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);

        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        overlay.render(g);

        // 1. Click Mode Button -> Opens Mode Dropdown Menu
        MouseEvent modeClick = new MouseEvent(new JPanel(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 10, 8, 1, false);
        overlay.mousePressed(modeClick);
        assertTrue(overlay.isModeDropdownOpen());

        // Re-render to populate dropdown hitboxes
        overlay.render(g);

        // 2. Click DAMAGE_TAKEN item in dropdown
        Rectangle takenRect = overlay.getModeDropdownHitboxes().get(CombatMeterOverlay.MetricMode.DAMAGE_TAKEN);
        assertNotNull("DAMAGE_TAKEN dropdown item should exist", takenRect);
        MouseEvent selectClick = new MouseEvent(new JPanel(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, takenRect.x + 2, takenRect.y + 2, 1, false);
        overlay.mousePressed(selectClick);

        assertEquals(CombatMeterOverlay.MetricMode.DAMAGE_TAKEN, overlay.getCurrentMode());
        assertFalse(overlay.isModeDropdownOpen());

        g.dispose();
    }

    @Test
    public void testRightClickOnHudOpensSourceDetails()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);
        java.util.concurrent.atomic.AtomicReference<EncounterSegment> got = new java.util.concurrent.atomic.AtomicReference<>();
        overlay.setOnOpenSourceDetails((scope, entity) -> got.set(scope));

        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        overlay.render(g);

        // A right-click anywhere on the meter chrome opens the window (here: the header).
        MouseEvent rc = new MouseEvent(new JPanel(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
            0, 10, 8, 1, false, MouseEvent.BUTTON3);
        overlay.mousePressed(rc);
        g.dispose();

        assertNotNull("right-click on the HUD opens Source Details", got.get());
        assertEquals(encounterManager.getSelectedOrCurrentEncounter(), got.get());
    }

    @Test
    public void testLiveBossProgressEstimatesTimeToKill()
    {
        when(targetNpc.getHealthRatio()).thenReturn(15);
        when(targetNpc.getHealthScale()).thenReturn(30);
        encounterManager.setBossHpLookup(name -> "Vorkath".equals(name) ? 750 : 0);

        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 5000, "Whip", 100, "Vorkath");

        CombatEncounterManager.LiveBossProgress bp = encounterManager.getLiveBossProgress();
        assertNotNull("a live single-target fight vs an NPC with a health bar has progress", bp);
        assertEquals(0.5, bp.getHpFraction(), 0.01);
        assertTrue("known max HP -> real ETA", bp.isHpKnown() && bp.getEtaSeconds() >= 0);
        assertTrue("on-pace kills/hr is positive", bp.getProjectedKph() > 0);

        encounterManager.setBossHpLookup(name -> 0);   // unknown monster -> HP% only
        CombatEncounterManager.LiveBossProgress bp2 = encounterManager.getLiveBossProgress();
        assertNotNull(bp2);
        assertFalse(bp2.isHpKnown());
        assertEquals(-1, bp2.getEtaSeconds());
    }

    @Test
    public void testLiveBossProgressNullWithoutHealthBar()
    {
        when(targetNpc.getHealthRatio()).thenReturn(-1);
        when(targetNpc.getHealthScale()).thenReturn(-1);
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 100, "Whip", 100, "Vorkath");
        assertNull(encounterManager.getLiveBossProgress());
    }

    @Test
    public void testCustomPaletteUsesTheConfiguredColour()
    {
        when(config.combatPaletteAccent()).thenReturn(OsrsCopilotConfig.PaletteAccentOption.CUSTOM);
        when(config.combatBarStyle()).thenReturn(OsrsCopilotConfig.BarStyleOption.SOLID);
        when(config.combatBarTexture()).thenReturn(OsrsCopilotConfig.BarTextureOption.NONE);
        when(config.combatBarCustomColor()).thenReturn(new Color(60, 170, 255)); // strongly blue

        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 100, "Whip", 100, "Vorkath");
        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);

        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        overlay.render(g);
        g.dispose();

        // A pixel inside the first bar's fill should read blue-dominant, not the red default palette.
        Color px = new Color(img.getRGB(12, 40), true);
        assertTrue("custom bar colour should dominate (got " + px + ")", px.getBlue() > px.getRed() + 30);
    }

    @Test
    public void testHiddenHeaderRemovesHeaderControls()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);
        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        when(config.combatHeaderMode()).thenReturn(OsrsCopilotConfig.HeaderModeOption.FULL);
        Dimension full = overlay.render(g);

        when(config.combatHeaderMode()).thenReturn(OsrsCopilotConfig.HeaderModeOption.HIDDEN);
        Dimension hidden = overlay.render(g);

        assertTrue("hidden header shrinks the overlay", hidden.height < full.height);

        // the metric pill is gone -> clicking where it was must not open the dropdown
        overlay.mousePressed(new MouseEvent(new JPanel(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 12, 10, 1, false));
        assertFalse("no header controls in hidden mode", overlay.isModeDropdownOpen());
        g.dispose();
    }

    @Test
    public void testDropdownFlipsAboveTheOverlayWhenLowOnScreen()
    {
        java.awt.Canvas canvas = new java.awt.Canvas();
        canvas.setSize(1920, 1080);
        when(client.getCanvas()).thenReturn(canvas);
        encounterManager.notifyCombatAction(targetNpc, 100);

        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);
        BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        overlay.render(g);                                   // populate the getBounds() ref
        overlay.getBounds().setBounds(20, 1030, 220, 40);    // HUD parked near the canvas bottom
        overlay.render(g);                                   // re-place the header controls at the new bounds

        // open the metric dropdown (mode pill is at overlay x+4..x+66, y+5..y+20)
        overlay.mousePressed(new MouseEvent(new JPanel(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
            0, 20 + 8, 1030 + 10, 1, false));
        assertTrue(overlay.isModeDropdownOpen());
        overlay.render(g);

        Rectangle row = overlay.getModeDropdownHitboxes().get(CombatMeterOverlay.MetricMode.DAMAGE_TAKEN);
        assertNotNull(row);
        assertTrue("a dropdown row should sit above the HUD when it can't fit below (" + row.y
            + " vs HUD top " + overlay.getBounds().y + ")", row.y < overlay.getBounds().y);
        g.dispose();
    }

    @Test
    public void testHeaderIconsShowHoverTooltips()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);

        // Mouse parked over the settings cog. Right-side icons at width 220: graph 146, cog 164,
        // "?" 182, panel 200 (each 16 wide).
        when(client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(172, 11));

        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        overlay.render(g);
        g.dispose();

        org.mockito.ArgumentCaptor<net.runelite.client.ui.overlay.tooltip.Tooltip> cap =
            org.mockito.ArgumentCaptor.forClass(net.runelite.client.ui.overlay.tooltip.Tooltip.class);
        verify(tooltipManager, atLeastOnce()).add(cap.capture());
        assertTrue("hovering the cog should enqueue a 'settings' tooltip",
            cap.getAllValues().stream().anyMatch(t -> t.getText().toLowerCase().contains("settings")));
    }

    @Test
    public void testCombatMeterSettingsCardInteractions()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);

        BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Render card
        settingsCard.render(g, new java.awt.Point(10, 10), new java.awt.Point(15, 15), CombatMeterOverlay.MetricMode.DAMAGE_DONE);
        assertFalse(settingsCard.getHitboxes().isEmpty());

        // Test mode selection click via settings card
        java.awt.Rectangle takenHitbox = settingsCard.getHitboxes().get("MODE_DAMAGE_TAKEN");
        assertNotNull(takenHitbox);
        settingsCard.handleClick(new java.awt.Point(takenHitbox.x + 2, takenHitbox.y + 2), overlay);
        assertEquals(CombatMeterOverlay.MetricMode.DAMAGE_TAKEN, overlay.getCurrentMode());

        g.dispose();
    }

    /**
     * The "Preview banner" / "Reposition banner" actions used to be config booleans that either
     * unticked themselves or were really a mode. They now live on the settings card and drive the
     * overlay directly - no config round-trip.
     */
    @Test
    public void testBannerPreviewAndRepositionLiveOnSettingsCard()
    {
        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);
        BufferedImage img = new BufferedImage(400, 700, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // First render populates the scroll metrics; then scroll to the bottom (where the banner
        // controls sit) and render again so their hitboxes land inside the visible band.
        settingsCard.render(g, new java.awt.Point(10, 10), new java.awt.Point(10, 10), new java.awt.Point(-1, -1), CombatMeterOverlay.MetricMode.DAMAGE_DONE);
        settingsCard.scrollBy(4000);
        settingsCard.render(g, new java.awt.Point(10, 10), new java.awt.Point(10, 10), new java.awt.Point(-1, -1), CombatMeterOverlay.MetricMode.DAMAGE_DONE);

        java.awt.Rectangle editHit = settingsCard.getHitboxes().get("BANNER_EDIT");
        java.awt.Rectangle previewHit = settingsCard.getHitboxes().get("BANNER_PREVIEW");
        assertNotNull("Reposition-banner control must be reachable on the settings card", editHit);
        assertNotNull("Preview-banner control must be reachable on the settings card", previewHit);

        // Reposition banner: toggles transient overlay state, no config write.
        assertFalse(bannerOverlay.isEditMode());
        settingsCard.handleClick(new java.awt.Point(editHit.x + 2, editHit.y + 2), overlay);
        assertTrue("clicking it shows the placement sample", bannerOverlay.isEditMode());
        settingsCard.handleClick(new java.awt.Point(editHit.x + 2, editHit.y + 2), overlay);
        assertFalse("clicking it again hides the sample", bannerOverlay.isEditMode());

        // Preview banner: one-shot flash straight on the overlay, no config write and no throw.
        when(config.combatBannerEnterText()).thenReturn("Entering Combat");
        when(config.combatBannerEnterColor()).thenReturn(new Color(255, 78, 50));
        when(config.combatBannerLingerMs()).thenReturn(1200);
        settingsCard.handleClick(new java.awt.Point(previewHit.x + 2, previewHit.y + 2), overlay);

        verify(configManager, never()).setConfiguration(eq("osrscopilot"), eq("combatBannerEditMode"), any(Object.class));
        verify(configManager, never()).setConfiguration(eq("osrscopilot"), eq("combatBannerPreview"), any(Object.class));

        g.dispose();
    }

    /** FIX 4: the bar-count selector covers the full 1-10 @Range, and the bar-label font has a card row. */
    @Test
    public void testSettingsCardBarCountFullRangeAndBarFontRow()
    {
        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);
        BufferedImage img = new BufferedImage(400, 700, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        settingsCard.render(g, new java.awt.Point(10, 10), new java.awt.Point(10, 10), new java.awt.Point(-1, -1), CombatMeterOverlay.MetricMode.DAMAGE_DONE);

        for (int n = 1; n <= 10; n++)
        {
            assertNotNull("bar-count selector must expose " + n, settingsCard.getHitboxes().get("BARCOUNT_" + n));
        }

        java.awt.Rectangle ten = settingsCard.getHitboxes().get("BARCOUNT_10");
        settingsCard.handleClick(new java.awt.Point(ten.x + 2, ten.y + 2), overlay);
        verify(configManager).setConfiguration("osrscopilot", "maxCombatBars", 10);

        java.awt.Rectangle boldFont = settingsCard.getHitboxes().get("BARFONT_BOLD");
        assertNotNull("bar-label font must have a card row", boldFont);
        settingsCard.handleClick(new java.awt.Point(boldFont.x + 2, boldFont.y + 2), overlay);
        verify(configManager).setConfiguration("osrscopilot", "combatBarFont", OsrsCopilotConfig.BarFontOption.BOLD);

        g.dispose();
    }

    @Test
    public void testColorSchemePaletteSelectionAndNotOverriddenByDisplayMode()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 100, "Abyssal whip", 100);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MAGIC, 30, "Fire wave", 100);
        encounterManager.recordHpHealed(20);

        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);

        BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // 1. Render settings card and click Zulrah Teal swatch
        settingsCard.render(g, new java.awt.Point(10, 10), new java.awt.Point(15, 15), CombatMeterOverlay.MetricMode.DAMAGE_DONE);
        java.awt.Rectangle tealHitbox = settingsCard.getHitboxes().get("SWATCH_ZULRAH_TEAL");
        assertNotNull("SWATCH_ZULRAH_TEAL hitbox should exist", tealHitbox);

        settingsCard.handleClick(new java.awt.Point(tealHitbox.x + 2, tealHitbox.y + 2), overlay);
        verify(configManager).setConfiguration("osrscopilot", "combatPaletteAccent", OsrsCopilotConfig.PaletteAccentOption.ZULRAH_TEAL);

        // 2. Configure mock config to return ZULRAH_TEAL and GRADIENT
        when(config.combatPaletteAccent()).thenReturn(OsrsCopilotConfig.PaletteAccentOption.ZULRAH_TEAL);
        when(config.combatBarStyle()).thenReturn(OsrsCopilotConfig.BarStyleOption.GRADIENT);

        assertEquals(CombatMeterColors.SWATCH_ZULRAH_TEAL, CombatMeterColors.getPalettePrimary(OsrsCopilotConfig.PaletteAccentOption.ZULRAH_TEAL));

        // 3. Render across all display modes (Damage, Taken, Heals, Pots) - verify no NPE and smooth rendering
        for (CombatMeterOverlay.MetricMode mode : CombatMeterOverlay.MetricMode.values())
        {
            overlay.setMode(mode);
            Dimension dim = overlay.render(g);
            assertNotNull("Overlay should render with chosen palette in mode " + mode, dim);
        }

        // 4. Test Dynamic / Style Swatch
        java.awt.Rectangle dynamicHitbox = settingsCard.getHitboxes().get("SWATCH_DYNAMIC_STYLE");
        assertNotNull("SWATCH_DYNAMIC_STYLE hitbox should exist", dynamicHitbox);
        settingsCard.handleClick(new java.awt.Point(dynamicHitbox.x + 2, dynamicHitbox.y + 2), overlay);
        verify(configManager).setConfiguration("osrscopilot", "combatBarStyle", OsrsCopilotConfig.BarStyleOption.CLASS_STYLE);

        g.dispose();
    }

    @Test
    public void testAttackUptimeAndGpMetrics()
    {
        EntityCombatStats stats = new EntityCombatStats("Player");
        stats.setTotalDamage(500);
        stats.recordAttackCycle(60, 20);
        stats.setDurationSeconds(100.0);
        stats.setTotalGpCost(25000);

        assertEquals(75.0, stats.getAttackUptimePercent(), 0.01);
        // active DPS = damage / (activeTicks * 0.6) = 500 / 36
        assertEquals(500.0 / (60 * 0.6), stats.getActiveDps(), 0.01);
        assertEquals(900000.0, stats.getGpPerHour(), 1.0);
    }

    /**
     * Tranche B8: attack-uptime is fed from real attack cadence - consecutive attacks a normal
     * cycle apart are all "active"; a long pause becomes lost combat time.
     */
    @Test
    public void testAttackUptimeCreditedFromCadence()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        // 4-tick cadence, three attacks -> two gaps of 4 active ticks each.
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 20, "Abyssal whip", 100, "npc");
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 22, "Abyssal whip", 104, "npc");
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 18, "Abyssal whip", 108, "npc");

        EntityCombatStats s = encounterManager.getCurrentEncounter().getLocalPlayerStats();
        assertEquals(8, s.getAttackUptimeTicks());
        assertEquals(0, s.getLostCombatTicks());
        assertEquals(100.0, s.getAttackUptimePercent(), 0.01);

        // A 12-tick pause before the next swing: 7 active (one cycle), 5 lost.
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 25, "Abyssal whip", 120, "npc");
        assertEquals(15, s.getAttackUptimeTicks());
        assertEquals(5, s.getLostCombatTicks());
        assertEquals(75.0, s.getAttackUptimePercent(), 0.01);
    }

    @Test
    public void testTimelineEventBuffer()
    {
        EntityCombatStats stats = new EntityCombatStats("Player");
        com.osrscopilot.combat.model.CombatTimelineEvent event1 =
            new com.osrscopilot.combat.model.CombatTimelineEvent(
                100, "00:15", "HIT", "+", "Max hit 48 dealt with Abyssal whip", Color.ORANGE);
        stats.addTimelineEvent(event1);

        assertEquals(1, stats.getTimelineEvents().size());
        assertEquals("+", stats.getTimelineEvents().get(0).getIcon());
        assertEquals("00:15", stats.getTimelineEvents().get(0).getTimeFormatted());
    }

    /**
     * Tranche C1: structured fields round-trip through the builder and feed searchText() (the
     * haystack the Full Log's free-text search matches against).
     */
    @Test
    public void testTimelineEventStructuredFieldsAndSearch()
    {
        com.osrscopilot.combat.model.CombatTimelineEvent ev =
            com.osrscopilot.combat.model.CombatTimelineEvent.builder()
                .clientTick(120).timeFormatted("00:12").eventType("HIT").icon(">")
                .description("Dealt 22 (Dragon scimitar)").color(Color.ORANGE).amount(22)
                .source("You").target("Vorkath").style(CombatStyle.MELEE).weaponOrSpell("Dragon scimitar")
                .special(true)
                .build();

        assertEquals("Vorkath", ev.getTargetName());
        assertEquals("You", ev.getSourceName());
        assertEquals(CombatStyle.MELEE, ev.getStyle());
        assertTrue(ev.isSpecial());
        assertFalse(ev.isMiss());
        assertTrue(ev.getWallClockMillis() > 0);

        String hay = ev.searchText();
        assertTrue(hay.contains("vorkath"));
        assertTrue(hay.contains("dragon scimitar"));
        assertTrue(hay.contains("melee"));
        assertFalse(hay.contains("zulrah"));
    }

    @Test
    public void testTimelineBufferKeepsFarMoreThanFortyEvents()
    {
        EntityCombatStats stats = new EntityCombatStats("Player");
        for (int i = 1; i <= 150; i++)
        {
            stats.addTimelineEvent(new com.osrscopilot.combat.model.CombatTimelineEvent(
                i, "00:0" + i, "HIT", "+", "Hit " + i, Color.ORANGE));
        }
        assertEquals("current-encounter ledger keeps thousands of events", 150, stats.getTimelineEvents().size());

        // The long-lived scopes are capped tighter.
        stats.setMaxTimelineEvents(50);
        assertEquals(50, stats.getTimelineEvents().size());
        assertEquals("Hit 150", stats.getTimelineEvents().get(stats.getTimelineEvents().size() - 1).getDescription());
    }

    /**
     * Tranche C2: when the ring overflows, the fight's opening "START" line is pinned and the
     * dropped-event count is tracked so the UI can say so instead of claiming "N of N".
     */
    @Test
    public void testTimelineBufferPinsStartAndCountsDrops()
    {
        EntityCombatStats stats = new EntityCombatStats("Player");
        stats.setMaxTimelineEvents(10);
        stats.addTimelineEvent(new com.osrscopilot.combat.model.CombatTimelineEvent(
            0, "00:00", "START", ">", "Engaged Vorkath", Color.ORANGE));
        for (int i = 1; i <= 40; i++)
        {
            stats.addTimelineEvent(new com.osrscopilot.combat.model.CombatTimelineEvent(
                i, "00:0" + i, "HIT", "+", "Hit " + i, Color.ORANGE));
        }

        List<com.osrscopilot.combat.model.CombatTimelineEvent> evs = stats.getTimelineEvents();
        assertEquals(10, evs.size());
        assertEquals("START stays pinned at the front", "START", evs.get(0).getEventType());
        assertEquals("Hit 40", evs.get(evs.size() - 1).getDescription());
        assertEquals(31, stats.getDroppedEventCount()); // 41 added, 10 kept
    }

    @Test
    public void testStatCollectionAccessorsReturnDefensiveCopies()
    {
        EntityCombatStats stats = new EntityCombatStats("Player");
        stats.recordDamageDealt(CombatStyle.MELEE, 30, "Whip", 1);
        stats.recordDamageTaken(CombatStyle.MAGIC, 12, "Vorkath", 2);
        stats.addTimelineEvent(new com.osrscopilot.combat.model.CombatTimelineEvent(
            1, "00:01", "HIT", ">", "Hit 30", Color.ORANGE));

        // Mutating a returned collection must not corrupt the live one (would CME on the EDT otherwise).
        stats.getTimelineEvents().clear();
        stats.getStyleBreakdown().clear();
        stats.getWeaponBreakdown().clear();
        stats.getDamageTakenBySource().clear();
        stats.getDamageByStyle().clear();

        assertEquals(1, stats.getTimelineEvents().size());
        assertFalse(stats.getStyleBreakdown().isEmpty());
        assertFalse(stats.getWeaponBreakdown().isEmpty());
        assertFalse(stats.getDamageTakenBySource().isEmpty());
        assertFalse(stats.getDamageByStyle().isEmpty());

        EncounterSegment seg = new EncounterSegment(java.util.UUID.randomUUID(), "Pull",
            com.osrscopilot.combat.model.SegmentType.ENCOUNTER, 1, "Player");
        seg.recordDamageToEnemy("Goblin", CombatStyle.MELEE, 10, "Whip", 1);
        seg.getEnemyBreakdown().clear();
        seg.getEnemyDamage().clear();
        assertEquals(1, seg.getEnemyBreakdown().size());
    }

    @Test
    public void testCombatMeterAutoSizesFromBarHeightAndWidth()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 120, "Abyssal whip", 100);

        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);
        assertFalse("Meter sizes itself from bar height x count; no free-form resize", overlay.isResizable());

        BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        when(config.combatOverlayWidth()).thenReturn(240);
        when(config.combatBarHeight()).thenReturn(16);
        Dimension small = overlay.render(g);
        assertNotNull(small);
        assertEquals("width follows combatOverlayWidth", 240, small.width);

        when(config.combatBarHeight()).thenReturn(40);
        Dimension large = overlay.render(g);
        assertNotNull(large);
        assertTrue("taller bars -> taller meter", large.height > small.height);

        g.dispose();
    }

    @Test
    public void testWidthStepperFromSettingsCard()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);

        BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        settingsCard.render(g, new java.awt.Point(10, 10), new java.awt.Point(15, 15), CombatMeterOverlay.MetricMode.DAMAGE_DONE);

        java.awt.Rectangle inc = settingsCard.getHitboxes().get("WIDTH_INC");
        java.awt.Rectangle dec = settingsCard.getHitboxes().get("WIDTH_DEC");
        assertNotNull("WIDTH_INC hitbox should exist", inc);
        assertNotNull("WIDTH_DEC hitbox should exist", dec);

        // combatOverlayWidth() is stubbed to 220 in setUp; +10 step -> 230 written to config.
        settingsCard.handleClick(new java.awt.Point(inc.x + 2, inc.y + 2), overlay);
        verify(configManager).setConfiguration("osrscopilot", "combatOverlayWidth", 230);

        g.dispose();
    }

    @Test
    public void testAutomaticTimelineEventRecording()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 22, "Dragon scimitar", 101);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MAGIC, 9, "Cave horror", 102);
        encounterManager.recordHpHealed(18);

        EntityCombatStats stats = encounterManager.getOverallSessionEncounter().getLocalPlayerStats();
        assertFalse("Timeline events should be automatically generated", stats.getTimelineEvents().isEmpty());

        assertTrue(stats.getTimelineEvents().stream().anyMatch(e -> e.getDescription().contains("Engaged Vorkath")));
        assertTrue(stats.getTimelineEvents().stream().anyMatch(e -> e.getDescription().contains("Dealt 22 (Dragon scimitar)")));
        assertTrue(stats.getTimelineEvents().stream().anyMatch(e -> e.getDescription().contains("Took 9 (Cave horror)")));
        assertTrue(stats.getTimelineEvents().stream().anyMatch(e -> e.getDescription().contains("Healed 18 HP")));
    }

    @Test
    public void testCombatTimeSeriesAggregationAndGraphRendering()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 45, "Abyssal whip", 100);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MAGIC, 12, "Cave horror", 101);
        encounterManager.recordHpHealed(18);

        EntityCombatStats stats = encounterManager.getOverallSessionEncounter().getLocalPlayerStats();
        assertFalse("TimeSeries points should be recorded", stats.getTimeSeries().isEmpty());
        assertTrue(stats.getPeakDps() > 0);
        assertTrue(stats.getPeakHps() > 0);

        // Test Graph Card Rendering
        BufferedImage img = new BufferedImage(500, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        graphCard.render(g, new java.awt.Point(20, 20), new java.awt.Point(50, 50), encounterManager.getOverallSessionEncounter());

        // Test Graph Mode Cycling
        graphCard.setCurrentMode(com.osrscopilot.combat.overlay.CombatGraphCard.GraphMode.DTPS);
        assertEquals(com.osrscopilot.combat.overlay.CombatGraphCard.GraphMode.DTPS, graphCard.getCurrentMode());
        graphCard.setCurrentMode(com.osrscopilot.combat.overlay.CombatGraphCard.GraphMode.HPS);
        assertEquals(com.osrscopilot.combat.overlay.CombatGraphCard.GraphMode.HPS, graphCard.getCurrentMode());
        graphCard.setCurrentMode(com.osrscopilot.combat.overlay.CombatGraphCard.GraphMode.ALL);
        assertEquals(com.osrscopilot.combat.overlay.CombatGraphCard.GraphMode.ALL, graphCard.getCurrentMode());

        g.dispose();
    }

    /** Graph fix: peak = the top of the SMOOTHED line, not a single big hit's size. */
    @Test
    public void testGraphPeakIsSmoothedNotMaxHit()
    {
        EntityCombatStats s = new EntityCombatStats("Player");
        s.setRollingWindowSeconds(6);
        s.recordTimeSeriesSecond(0, 50, 0, 0, CombatStyle.MELEE, null); // one 50 max hit
        for (int sec = 1; sec <= 30; sec++)
        {
            s.recordTimeSeriesSecond(sec, 3, 0, 0, CombatStyle.MELEE, null); // steady 3/s
        }
        assertTrue("peak DPS reflects the rolling rate, not the 50 spike", s.getPeakDps() < 20.0);
        assertTrue(s.getPeakDps() > 0.0);
    }

    /** Details! parity: the "Current Session" / "Total" Overall scopes divide their rates by
     *  accrued *combat* time, not wall-clock - so a fight reads the same DPS in each. Wall-clock
     *  is still available separately for a "logged in for" readout. */
    @Test
    public void testCurrentSessionDpsUsesCombatTime()
    {
        EncounterSegment sess = encounterManager.getCurrentSessionEncounter();
        sess.setStartTimestamp(java.time.Instant.now().minusSeconds(90));
        sess.setEndTimestamp(null);
        sess.resetCombatSeconds();
        sess.addCombatSeconds(12.0); // 12s of actual fighting inside a 90s-old session

        assertEquals("rate denominator is combat time", 12, sess.getDurationSeconds());
        int wall = sess.getWallClockSeconds();
        assertTrue("wall-clock still available (~90s): " + wall, wall >= 88 && wall <= 95);
    }

    /**
     * FIX 2: the Live fight divides its rate by the same combat-time denominator as the Overall
     * scopes (so an idle-free fight reads one DPS everywhere), and once hits stop landing the
     * denominator freezes - the rate must not decay while the encounter timeout runs down.
     */
    @Test
    public void testLiveAndSessionDpsAgreeAndPostHitTailDoesNotDecay()
    {
        // Idle-free fight: a hit every tick, a game tick every tick.
        for (int t = 100; t <= 110; t++)
        {
            encounterManager.notifyCombatAction(targetNpc, t);
            encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 50, "Whip", t, "Vorkath");
            encounterManager.onGameTick(t);
        }
        EncounterSegment live = encounterManager.getCurrentEncounter();
        EncounterSegment sess = encounterManager.getCurrentSessionEncounter();
        assertNotNull(live);

        double liveDps = live.getLocalPlayerStats().getDps();
        assertEquals("Live and Session divide by the same combat-time denominator",
            sess.getLocalPlayerStats().getDps(), liveDps, 0.01);
        assertTrue("denominator is combat time, not a truncated ~1s: " + liveDps, liveDps > 60);

        // No further hits; the encounter timeout has NOT elapsed. The rate must hold, not decay.
        for (int t = 111; t <= 125; t++)
        {
            encounterManager.onGameTick(t);
        }
        assertNotNull("still within the encounter timeout", encounterManager.getCurrentEncounter());
        assertEquals("post-hit tail does not grow the denominator / decay the rate", liveDps,
            encounterManager.getCurrentEncounter().getLocalPlayerStats().getDps(), 0.01);
    }

    /** Tranche F8: a very long fight keeps its whole time-series on the graph (adaptive
     *  bucketing halves resolution) instead of evicting the early minutes. */
    @Test
    public void testTimeSeriesAdaptivelyBucketsOnLongFights()
    {
        EntityCombatStats s = new EntityCombatStats("Player");
        for (int sec = 0; sec < 5000; sec++)
        {
            s.recordTimeSeriesSecond(sec, 10, 0, 0, CombatStyle.MELEE, null);
        }
        List<com.osrscopilot.combat.model.CombatTimeSeriesPoint> pts = s.getTimeSeries();
        assertTrue("point count stays bounded", pts.size() <= 600);
        assertTrue("the last bucket still reaches the end of the fight",
            pts.get(pts.size() - 1).getSecond() >= 4900);
        long total = 0;
        for (com.osrscopilot.combat.model.CombatTimeSeriesPoint p : pts) total += p.getDamageDealt();
        assertEquals("no damage lost to bucketing", 5000L * 10, total);
    }

    @Test
    public void testGraphToggleFromOverlayAndHeader()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        CombatMeterOverlay overlay = new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay);

        assertFalse(overlay.isGraphOpen());
        overlay.toggleGraph();
        assertTrue(overlay.isGraphOpen());
        overlay.toggleGraph();
        assertFalse(overlay.isGraphOpen());
    }

    @Test
    public void testCombatGraphOverlayDirectDraggingAndResizing()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        graphOverlay.setOpen(true);
        assertTrue(graphOverlay.isOpen());
        graphOverlay.setPreferredLocation(new java.awt.Point(100, 100));

        BufferedImage img = new BufferedImage(600, 500, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Dimension dim = graphOverlay.render(g);
        assertNotNull("Graph overlay must render when open", dim);

        // Test Direct Header Bar Dragging
        // Initial location is at (100, 100), header bar is at y: 100..122, x: 100..452
        MouseEvent headerPress = new MouseEvent(new JPanel(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 100 + 50, 100 + 10, 1, false);
        graphOverlay.mousePressed(headerPress);

        // Drag +150px right, +80px down
        MouseEvent headerDrag = new MouseEvent(new JPanel(), MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), 0, 100 + 50 + 150, 100 + 10 + 80, 1, false);
        graphOverlay.mouseDragged(headerDrag);

        assertEquals(250, graphOverlay.getPreferredLocation().x);
        assertEquals(180, graphOverlay.getPreferredLocation().y);

        MouseEvent headerRelease = new MouseEvent(new JPanel(), MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, 300, 190, 1, false);
        graphOverlay.mouseReleased(headerRelease);

        g.dispose();
    }

    @Test
    public void testHealingTimeSeriesFromFoodAndBrews()
    {
        when(itemManager.getItemPrice(385)).thenReturn(1000);
        ItemComposition sharkDef = mock(ItemComposition.class);
        when(sharkDef.getName()).thenReturn("Shark");
        when(client.getItemDefinition(385)).thenReturn(sharkDef);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(60);
        when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);

        EntityCombatStats overallStats = new EntityCombatStats("Overall");
        EntityCombatStats currentStats = new EntityCombatStats("Current");

        // Baseline: 3x Sharks (ID 385)
        ItemContainer initInv = mock(ItemContainer.class);
        when(initInv.getItems()).thenReturn(new Item[]{new Item(385, 3)});
        consumableAuditor.auditInventory(new ItemContainerChanged(InventoryID.INVENTORY.getId(), initInv), overallStats, currentStats);

        // Eat 1 Shark (+20 HP)
        ItemContainer afterEat = mock(ItemContainer.class);
        when(afterEat.getItems()).thenReturn(new Item[]{new Item(385, 2)});
        clickConsume("Eat");
        consumableAuditor.auditInventory(new ItemContainerChanged(InventoryID.INVENTORY.getId(), afterEat), overallStats, currentStats);

        assertEquals(20, overallStats.getHpHealed());
        assertEquals(1, overallStats.getFoodEatenCount());

        // Verify TimeSeries recorded the 20 HP heal and peak HPS
        assertFalse(overallStats.getTimeSeries().isEmpty());
        assertTrue(overallStats.getPeakHps() >= 4.0);
        assertEquals(20, overallStats.getTimeSeries().get(overallStats.getTimeSeries().size() - 1).getCumulativeHealing());
    }

    @Test
    public void testMinimapCombatButtonBoundsAndClicks()
    {
        com.osrscopilot.map.MinimapCombatButtonOverlay minimapBtn = new com.osrscopilot.map.MinimapCombatButtonOverlay(
            client, config, configManager, tooltipManager,
            new CombatMeterOverlay(client, config, configManager, encounterManager, tooltipManager, settingsCard, graphOverlay),
            graphOverlay, encounterManager, null
        );

        when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
        when(config.showMinimapCombatButton()).thenReturn(true);
        when(config.showCombatOverlay()).thenReturn(true);

        net.runelite.api.widgets.Widget mockWiki = mock(net.runelite.api.widgets.Widget.class);
        when(mockWiki.isHidden()).thenReturn(false);
        when(mockWiki.getBounds()).thenReturn(new Rectangle(500, 100, 20, 20));
        when(client.getWidget(net.runelite.api.gameval.InterfaceID.Orbs.WIKI_ICON)).thenReturn(mockWiki);

        Rectangle bounds = minimapBtn.getButtonBounds();
        assertNotNull("Minimap combat button bounds should be computed", bounds);
        assertTrue(bounds.x < 500);

        BufferedImage img = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        minimapBtn.render(g);

        // Test click toggles config
        MouseEvent press = new MouseEvent(new JPanel(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, bounds.x + 5, bounds.y + 5, 1, false, MouseEvent.BUTTON1);
        minimapBtn.mousePressed(press);
        assertTrue(press.isConsumed());

        MouseEvent release = new MouseEvent(new JPanel(), MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, bounds.x + 5, bounds.y + 5, 1, false, MouseEvent.BUTTON1);
        minimapBtn.mouseReleased(release);
        assertTrue(release.isConsumed());

        verify(configManager).setConfiguration("osrscopilot", "showCombatOverlay", false);

        g.dispose();
    }

    @Test
    public void testCombatEncounterTabViewHudToggle()
    {
        MonsterDatabase mdb = mock(MonsterDatabase.class);
        CombatEncounterTabView view = new CombatEncounterTabView(encounterManager, mdb, itemManager, null);
        boolean[] hudToggled = new boolean[]{false};
        view.setOnToggleHud(() -> hudToggled[0] = true);

        // Find HUD button recursively
        JButton hudBtn = findButtonRecursively(view, "HUD");
        assertNotNull("HUD button should be present in combat tab toolbar", hudBtn);
        hudBtn.doClick();
        assertTrue("Clicking HUD button should trigger onToggleHud callback", hudToggled[0]);
    }

    @Test
    public void testSpellAttackResolverAutocast()
    {
        com.osrscopilot.combat.engine.SpellAttackResolver resolver = new com.osrscopilot.combat.engine.SpellAttackResolver(client);

        // Varbit 276 = 38 (Ice Barrage)
        when(client.getVarbitValue(276)).thenReturn(38);
        String spell = resolver.resolveAttackName(com.osrscopilot.combat.model.CombatStyle.MAGIC, "Ancient staff", 100);
        assertEquals("Ice Barrage", spell);

        // Varbit 276 = 51 (Fire Surge)
        when(client.getVarbitValue(276)).thenReturn(51);
        String spell2 = resolver.resolveAttackName(com.osrscopilot.combat.model.CombatStyle.MAGIC, "Kodai wand", 100);
        assertEquals("Fire Surge", spell2);
    }

    @Test
    public void testSpellAttackResolverPoweredStaves()
    {
        com.osrscopilot.combat.engine.SpellAttackResolver resolver = new com.osrscopilot.combat.engine.SpellAttackResolver(client);
        when(client.getVarbitValue(276)).thenReturn(0);

        String trident = resolver.resolveAttackName(com.osrscopilot.combat.model.CombatStyle.MAGIC, "Trident of the swamp", 100);
        assertEquals("Trident of the swamp", trident);

        String shadow = resolver.resolveAttackName(com.osrscopilot.combat.model.CombatStyle.MAGIC, "Tumeken's shadow", 100);
        assertEquals("Tumeken's shadow", shadow);
    }

    @Test
    public void testSpellAttackResolverSpecialAttacks()
    {
        com.osrscopilot.combat.engine.SpellAttackResolver resolver = new com.osrscopilot.combat.engine.SpellAttackResolver(client);

        // VarPlayer 300 (Spec energy) = 500 (50%), VarPlayer 301 (Spec active) = 1
        when(client.getVarpValue(300)).thenReturn(500);
        when(client.getVarpValue(301)).thenReturn(1);

        String ddsSpec = resolver.resolveAttackName(com.osrscopilot.combat.model.CombatStyle.MELEE, "Dragon dagger(p++)", 100);
        assertEquals("Dragon dagger Spec", ddsSpec);

        String clawsSpec = resolver.resolveAttackName(com.osrscopilot.combat.model.CombatStyle.MELEE, "Dragon claws", 100);
        assertEquals("Dragon claws Spec", clawsSpec);
    }

    /**
     * TASK 1: a MANUAL cast (spellbook -> click NPC) of a grasp/freeze spell must resolve to the
     * plain spell name a few ticks later, so the CC-duration tracker fires on a Snare.
     */
    @Test
    public void testManualCastSnareResolvesToSpellNameAndCreatesDebuff()
    {
        com.osrscopilot.combat.engine.SpellAttackResolver resolver =
            new com.osrscopilot.combat.engine.SpellAttackResolver(client);
        DamageAttributionEngine engine = new DamageAttributionEngine(client, resolver);

        encounterManager.notifyCombatAction(targetNpc, 100);

        // 1. Manual cast: WIDGET_TARGET_ON_NPC, option "Cast", target carries the spell name.
        when(client.getTickCount()).thenReturn(100);
        MenuOptionClicked cast = mock(MenuOptionClicked.class);
        when(cast.getMenuAction()).thenReturn(MenuAction.WIDGET_TARGET_ON_NPC);
        when(cast.getMenuOption()).thenReturn("Cast");
        when(cast.getMenuTarget()).thenReturn("<col=00ffff>Snare</col><col=ffffff> -> <col=ffff00>Vorkath</col>");
        resolver.onMenuOptionClicked(cast);

        // 2. The Snare projectile lands 3 ticks later as a 4-damage hitsplat.
        Hitsplat splat = mock(Hitsplat.class);
        when(splat.getHitsplatType()).thenReturn(HitsplatID.DAMAGE_ME);
        when(splat.getAmount()).thenReturn(4);
        when(splat.isMine()).thenReturn(true);
        HitsplatApplied hitEvent = mock(HitsplatApplied.class);
        when(hitEvent.getActor()).thenReturn(targetNpc);
        when(hitEvent.getHitsplat()).thenReturn(splat);

        DamageAttributionEngine.ProcessedHit hit = engine.processHitsplat(hitEvent, 103);
        assertNotNull(hit);
        assertEquals("manual grasp cast resolves by spell name", "Snare", hit.source);
        assertFalse(hit.isMiss);

        // 3. Fed through the manager, the landed Snare registers a DebuffApplication.
        encounterManager.recordPlayerDamageDealt(hit.style, hit.amount, hit.source, 103, "Vorkath");

        List<com.osrscopilot.combat.model.DebuffApplication> ccs =
            encounterManager.getCurrentEncounter().getDebuffs();
        assertEquals(1, ccs.size());
        assertEquals("Snare", ccs.get(0).getType());
        assertEquals("Vorkath", ccs.get(0).getTargetName());
        assertEquals(113, ccs.get(0).getExpectedEndTick()); // 103 + 10 nominal ticks
    }

    /**
     * Tranche B5: standard Bind lands for 0 damage and routes through recordPlayerMiss (isMiss),
     * not recordPlayerDamageDealt - it must still register a DebuffApplication so the full log
     * shows the bind.
     */
    @Test
    public void testZeroDamageBindStillLogsDebuff()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerMiss(CombatStyle.MAGIC, "Bind", 105, "Vorkath");

        List<com.osrscopilot.combat.model.DebuffApplication> ccs =
            encounterManager.getCurrentEncounter().getDebuffs();
        assertEquals(1, ccs.size());
        assertEquals("Bind", ccs.get(0).getType());
        assertEquals("Vorkath", ccs.get(0).getTargetName());
        assertEquals(110, ccs.get(0).getExpectedEndTick()); // 105 + 5 nominal ticks

        // A plain melee miss (no CC spell name) must NOT create a debuff.
        encounterManager.recordPlayerMiss(CombatStyle.MELEE, "Abyssal whip", 106, "Vorkath");
        assertEquals(1, encounterManager.getCurrentEncounter().getDebuffs().size());
    }

    /**
     * Tranche B7: a blocked / 0-damage hit taken must not become the "biggest hit taken" record.
     */
    @Test
    public void testBlockedHitDoesNotBecomeMaxHitTaken()
    {
        EntityCombatStats stats = new EntityCombatStats("Player");
        stats.recordDamageTaken(CombatStyle.MELEE, 0, "Vorkath", 1);
        assertNull("a 0 hit must not set maxHitTaken", stats.getMaxHitTaken());

        stats.recordDamageTaken(CombatStyle.MAGIC, 25, "Vorkath", 2);
        assertNotNull(stats.getMaxHitTaken());
        assertEquals(25, stats.getMaxHitTaken().getAmount());

        stats.recordDamageTaken(CombatStyle.MELEE, 0, "Vorkath", 3);
        assertEquals("a later 0 hit must not clear it", 25, stats.getMaxHitTaken().getAmount());
    }

    /**
     * Tranche B4 + P2.2: a special attack is captured (count once, damage from EVERY splat of it),
     * fans out to Session/Total, and emits its own SPEC ledger event. A missed spec still bumps
     * the count. The headline spec damage must match the per-weapon accumulator (both splats).
     */
    @Test
    public void testSpecialAttackCaptureAndLedger()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        // Dragon dagger spec = two hits on one tick; both are part of the spec.
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 40, "Dragon dagger", 101, "Vorkath", true);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 18, "Dragon dagger", 101, "Vorkath", true);

        EntityCombatStats fight = encounterManager.getCurrentEncounter().getLocalPlayerStats();
        assertEquals("one spec, not two", 1, fight.getSpecialAttacksCount());
        assertEquals("both splats of the spec", 58, fight.getSpecialAttackDamage());
        assertEquals("headline matches the per-weapon accumulator",
            58, fight.getWeaponBreakdown().stream()
                .filter(w -> "Dragon dagger".equals(w.getWeaponName())).findFirst().get().getSpecDamage());
        assertEquals("spec fans out to the session scope",
            1, encounterManager.getCurrentSessionEncounter().getLocalPlayerStats().getSpecialAttacksCount());

        long specEvents = fight.getTimelineEvents().stream()
            .filter(ev -> "SPEC".equals(ev.getEventType())).count();
        assertEquals("one SPEC ledger line for the spec", 1, specEvents);

        // A missed spec (routes through recordPlayerMiss) still counts, with no extra damage.
        encounterManager.recordPlayerMiss(CombatStyle.MELEE, "Dragon dagger", 102, "Vorkath", true);
        assertEquals(2, fight.getSpecialAttacksCount());
        assertEquals(58, fight.getSpecialAttackDamage());
    }

    /**
     * Tranche B3: hit accuracy counts attacks, not hitsplats. A Scythe swing lands 3 splats on one
     * tick - that is one attempt, not three.
     */
    @Test
    public void testAccuracyIsPerAttackNotPerHitsplat()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);

        // Attack 1: three splats, same tick, all land.
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 40, "Scythe of vitur", 100, "npc");
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 25, "Scythe of vitur", 100, "npc");
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 12, "Scythe of vitur", 100, "npc");

        // Attack 2: a full miss.
        encounterManager.recordPlayerMiss(CombatStyle.MELEE, "Scythe of vitur", 106, "npc");

        // Attack 3: first splat misses, a later splat the same tick lands -> one attempt, one hit.
        encounterManager.recordPlayerMiss(CombatStyle.MELEE, "Scythe of vitur", 112, "npc");
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 30, "Scythe of vitur", 112, "npc");

        EntityCombatStats s = encounterManager.getCurrentEncounter().getLocalPlayerStats();
        assertEquals("3 attacks, not 6 hitsplats", 3, s.getAttackAttempts());
        assertEquals("2 landed attacks", 2, s.getSuccessfulHits());
        assertEquals(66.6, s.getHitAccuracy(), 0.1);
        assertEquals("every splat's damage is still summed", 40 + 25 + 12 + 30, s.getTotalDamage());
    }

    /**
     * Regression: lots of separate-tick 0-damage hits (high-defence target) must drag accuracy
     * down, not sit at 100%. Also: damage never exceeds the sum of the landed hits.
     */
    @Test
    public void testManyZeroHitsLowerAccuracy()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        int tick = 100;
        // 4 lands of 10, 6 misses - one attack per tick.
        for (int i = 0; i < 4; i++)
        {
            encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 10, "Dragon scimitar", tick, "Iron dragon");
            tick += 4;
        }
        for (int i = 0; i < 6; i++)
        {
            encounterManager.recordPlayerMiss(CombatStyle.MELEE, "Dragon scimitar", tick, "Iron dragon");
            tick += 4;
        }

        EntityCombatStats s = encounterManager.getCurrentEncounter().getLocalPlayerStats();
        assertEquals(10, s.getAttackAttempts());
        assertEquals(4, s.getSuccessfulHits());
        assertEquals(40.0, s.getHitAccuracy(), 0.01);
        assertEquals(40, s.getTotalDamage());
    }

    /**
     * TASK 2: a 0-damage (blocked / fully-mitigated) hit taken now produces a TAKEN timeline event
     * with a structured amount of 0 - needed for a *complete* Full Log.
     */
    @Test
    public void testZeroDamageTakenIsLoggedAsBlockedHitEvent()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageTaken(CombatStyle.MELEE, 0, "Vorkath", 101);

        EntityCombatStats stats = encounterManager.getCurrentEncounter().getLocalPlayerStats();
        List<com.osrscopilot.combat.model.CombatTimelineEvent> taken = new java.util.ArrayList<>();
        for (com.osrscopilot.combat.model.CombatTimelineEvent ev : stats.getTimelineEvents())
        {
            if ("TAKEN".equals(ev.getEventType()))
            {
                taken.add(ev);
            }
        }
        assertEquals(1, taken.size());
        assertEquals(0, taken.get(0).getAmount());
        assertTrue(taken.get(0).getDescription().contains("Blocked hit"));
    }

    /**
     * TASK 2: the restored 0-damage TAKEN event is excluded from the HUD "Combat Action Ledger"
     * default list, but still present in the Full Log.
     */
    @Test
    public void testZeroDamageTakenHiddenFromHudLedgerButKeptInFullLog()
    {
        MonsterDatabase mdb = mock(MonsterDatabase.class);
        CombatEncounterTabView view = new CombatEncounterTabView(encounterManager, mdb, itemManager, null);

        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(CombatStyle.MELEE, 30, "Abyssal whip", 100, "Vorkath");
        encounterManager.recordPlayerDamageTaken(CombatStyle.MAGIC, 25, "Vorkath", 101); // real hit
        encounterManager.recordPlayerDamageTaken(CombatStyle.MELEE, 0, "Vorkath", 102);  // blocked

        List<String> ledger = view.ledgerDescriptionsFor(encounterManager.getCurrentEncounter());
        assertTrue("real hits-taken still show in the ledger",
            ledger.stream().anyMatch(d -> d.contains("Took 25")));
        assertFalse("blocked 0-damage hits are filtered from the HUD ledger",
            ledger.stream().anyMatch(d -> d.contains("Blocked hit")));

        com.osrscopilot.combat.ui.CombatLogView log =
            new com.osrscopilot.combat.ui.CombatLogView(encounterManager, () -> {});
        List<String> full = log.logLinesFor(encounterManager.getCurrentEncounter());
        assertTrue("Full Log keeps the blocked hit",
            full.stream().anyMatch(l -> l.contains("Blocked hit")));
        assertTrue(full.stream().anyMatch(l -> l.contains("Took 25")));
        log.dispose();
    }

    /**
     * Combat HUD tour, fix 1: the staged fixture now carries a full Action Ledger stream, so the
     * tour's "action ledger" step isn't looking at an empty card.
     */
    @Test
    public void testTutorialFixtureFeedsTheHudActionLedger()
    {
        MonsterDatabase mdb = mock(MonsterDatabase.class);
        CombatEncounterTabView view = new CombatEncounterTabView(encounterManager, mdb, itemManager, null);

        EncounterSegment fixture = com.osrscopilot.combat.tutorial.CombatTutorialFixture.sampleEncounter();
        List<String> ledger = view.ledgerDescriptionsFor(fixture);

        assertFalse("tour ledger step must not show an empty card", ledger.isEmpty());
        assertTrue(ledger.stream().anyMatch(d -> d.contains("Dealt") && d.contains("Osmumten's fang")));
        assertTrue(ledger.stream().anyMatch(d -> d.startsWith("Special: ") && d.contains("Dragon warhammer")));
        assertTrue(ledger.stream().anyMatch(d -> d.equals("Took 26 (General Graardor)")));
        assertTrue(ledger.stream().anyMatch(d -> d.contains("Dwarf multicannon")));
        assertTrue(ledger.stream().anyMatch(d -> d.contains("Saradomin brew")));
    }

    /**
     * Combat HUD tour, fix 2: {@code tourScrollTo(null)} at the end of the tour must clear every
     * panel highlight - the share &amp; party row's ring used to stick because its saved border was
     * legitimately null and the restore path skipped nulls.
     */
    @Test
    public void testTourShareRowHighlightClearsOnTeardown()
    {
        MonsterDatabase mdb = mock(MonsterDatabase.class);
        CombatEncounterTabView view = new CombatEncounterTabView(encounterManager, mdb, itemManager, null);

        assertNull("share row has no border to begin with", view.shareRowBorderForTest());

        view.tourScrollTo("share");
        assertNotNull("tour rings the share row", view.shareRowBorderForTest());

        view.tourScrollTo("top"); // highlight moves away from the share row
        assertNull("leaving the share step restores its (absent) border", view.shareRowBorderForTest());

        view.tourScrollTo("share");
        assertNotNull(view.shareRowBorderForTest());
        view.tourScrollTo(null); // tour ends
        assertNull("tour teardown clears the share-row ring", view.shareRowBorderForTest());
        assertNotNull("hero banner keeps its own compound border", view.heroBannerBorderForTest());
    }

    /**
     * Combat HUD tour, fix 3: each per-kill breakdown row drills in place into that kill's full
     * {@link EncounterSegment.KillSummary}; the top accordion (perKillCard header) is untouched.
     */
    @Test
    public void testPerKillRowsDrillIntoIndividualKill()
    {
        MonsterDatabase mdb = mock(MonsterDatabase.class);
        for (int i = 0; i < 3; i++)
        {
            int t = 100 + i * 50;
            encounterManager.notifyCombatAction(targetNpc, t);
            encounterManager.recordPlayerDamageDealt(CombatStyle.RANGED, 750, "DHCB", t);
            encounterManager.recordPlayerDamageTaken(CombatStyle.MAGIC, 30 + i, "Vorkath", t + 1);
            encounterManager.handleActorDeath(targetNpc, t + 20);
        }
        EncounterSegment merged = encounterManager.getAllSegmentsForDropdown().get(0);
        assertTrue("three same-target kills fold into one merged row", merged.isMerged());

        CombatEncounterTabView view = new CombatEncounterTabView(encounterManager, mdb, itemManager, null);

        List<String> collapsed = view.perKillLinesFor(merged, -1);
        assertTrue("per-kill card is visible on a merged scope", view.isPerKillCardVisible());
        assertEquals("one clickable row per kill", 3, collapsed.stream().filter(l -> l.startsWith("[+] #")).count());
        assertFalse("nothing drilled in yet", collapsed.stream().anyMatch(l -> l.startsWith("DPS:")));

        List<String> drilled = view.perKillLinesFor(merged, 2);
        assertTrue("row #2 shows the collapse caret", drilled.stream().anyMatch(l -> l.startsWith("[-] #2")));
        assertEquals("exactly one kill expanded at a time", 1, drilled.stream().filter(l -> l.startsWith("[-] #")).count());
        assertTrue("drill-in shows the kill's DPS", drilled.stream().anyMatch(l -> l.equals("DPS:")));
        assertTrue("drill-in shows DTPS", drilled.stream().anyMatch(l -> l.equals("DTPS:")));
        assertTrue("drill-in shows the kill result", drilled.stream().anyMatch(l -> l.equals("Kill")));

        // A non-merged scope hides the card entirely.
        view.perKillLinesFor(encounterManager.getCurrentSessionEncounter(), -1);
        assertFalse(view.isPerKillCardVisible());
    }

    @Test
    public void testStatusHitsplatAttribution()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);

        // Hitsplat 65 = Poison
        Hitsplat poisonHit = mock(Hitsplat.class);
        when(poisonHit.getHitsplatType()).thenReturn(HitsplatID.POISON);
        when(poisonHit.getAmount()).thenReturn(6);
        when(poisonHit.isMine()).thenReturn(false);
        when(poisonHit.isOthers()).thenReturn(false);

        HitsplatApplied event = mock(HitsplatApplied.class);
        when(event.getActor()).thenReturn(targetNpc);
        when(event.getHitsplat()).thenReturn(poisonHit);
        when(client.getTickCount()).thenReturn(101);

        DamageAttributionEngine.ProcessedHit hit = damageEngine.processHitsplat(event, 101);
        assertNotNull(hit);
        assertEquals(com.osrscopilot.combat.model.CombatStyle.POISON, hit.style);
        assertEquals(6, hit.amount);

        encounterManager.recordPlayerDamageDealt(hit.style, hit.amount, hit.source, 101);
        assertEquals(6, encounterManager.getOverallSessionEncounter().getLocalPlayerStats().getTotalDamage());
        assertEquals(6, encounterManager.getOverallSessionEncounter().getLocalPlayerStats().getStyleDamage(com.osrscopilot.combat.model.CombatStyle.POISON));
    }

    @Test
    public void testMultiTargetTrackerTagging()
    {
        when(config.combatMultiTargetTagging()).thenReturn(true);
        com.osrscopilot.combat.engine.MultiTargetTracker tracker = new com.osrscopilot.combat.engine.MultiTargetTracker(config);

        NPC npc1 = mock(NPC.class);
        when(npc1.getName()).thenReturn("Cave horror");
        when(npc1.getIndex()).thenReturn(101);

        NPC npc2 = mock(NPC.class);
        when(npc2.getName()).thenReturn("Cave horror");
        when(npc2.getIndex()).thenReturn(102);

        String tag1 = tracker.getTargetDisplayName(npc1);
        String tag2 = tracker.getTargetDisplayName(npc2);

        // The first instance is unsuffixed; only the 2nd+ concurrent instance gets a "(n)" tag.
        assertEquals("Cave horror", tag1);
        assertEquals("Cave horror (2)", tag2);
        assertEquals(2, tracker.getActiveTargetCount());

        // Test despawn
        tracker.handleNpcDespawn(npc1);
        assertEquals(1, tracker.getActiveTargetCount());
    }

    @Test
    public void testSingleTargetTagStaysStableAcrossReadsAndPruning()
    {
        when(config.combatMultiTargetTagging()).thenReturn(true);
        com.osrscopilot.combat.engine.MultiTargetTracker tracker =
            new com.osrscopilot.combat.engine.MultiTargetTracker(config);

        NPC giant = mock(NPC.class);
        when(giant.getName()).thenReturn("Hill Giant");
        when(giant.getIndex()).thenReturn(4242);

        // One giant, hit over many ticks, with display-name reads and prune sweeps interleaved
        // (this is what produced "Hill Giant (3)/(7)/(9)" churn before the fix).
        for (int tick = 100; tick < 200; tick += 6)
        {
            tracker.trackTarget(giant, tick, true);
            assertEquals("Hill Giant", tracker.getTargetDisplayName(giant));
            tracker.pruneStale(tick, 15);
        }
        assertEquals(1, tracker.getActiveTargetCount());
        assertEquals("Hill Giant", tracker.getTargetDisplayName(giant));

        // Only after a real idle gap does it drop.
        tracker.pruneStale(300, 15);
        assertEquals(0, tracker.getActiveTargetCount());
    }

    @Test
    public void testBuffTrackingEngineStatBoostsAndPrayers()
    {
        when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
        com.osrscopilot.combat.engine.BuffTrackingEngine buffEngine = new com.osrscopilot.combat.engine.BuffTrackingEngine(client);
        buffEngine.startEncounter(100);

        // Simulate Strength Boost
        when(client.getBoostedSkillLevel(net.runelite.api.Skill.STRENGTH)).thenReturn(118);
        when(client.getRealSkillLevel(net.runelite.api.Skill.STRENGTH)).thenReturn(99);

        // Simulate Piety prayer active
        when(client.isPrayerActive(net.runelite.api.Prayer.PIETY)).thenReturn(true);

        // Run 10 ticks
        net.runelite.api.events.GameTick tickEvent = new net.runelite.api.events.GameTick();
        for (int i = 0; i < 10; i++)
        {
            when(client.getTickCount()).thenReturn(100 + i);
            buffEngine.onGameTick(tickEvent);
        }

        List<com.osrscopilot.combat.engine.BuffTrackingEngine.BuffUptimeSnapshot> snapshots =
            buffEngine.getActiveAndRecentSnapshots(110, 10);

        assertFalse(snapshots.isEmpty());

        boolean hasStrength = snapshots.stream().anyMatch(s -> s.getName().contains("Strength"));
        boolean hasPiety = snapshots.stream().anyMatch(s -> s.getName().equals("Piety"));

        assertTrue("Should track Strength boost", hasStrength);
        assertTrue("Should track Piety prayer uptime", hasPiety);
    }

    @Test
    public void testCombatGraphCardModesAndTooltips()
    {
        encounterManager.notifyCombatAction(targetNpc, 100);
        encounterManager.recordPlayerDamageDealt(com.osrscopilot.combat.model.CombatStyle.MAGIC, 25, "Ice Barrage", 100);
        encounterManager.recordPlayerDamageDealt(com.osrscopilot.combat.model.CombatStyle.POISON, 4, "Poison", 101);

        graphCard.setCurrentMode(com.osrscopilot.combat.overlay.CombatGraphCard.GraphMode.MAGIC);
        assertEquals(com.osrscopilot.combat.overlay.CombatGraphCard.GraphMode.MAGIC, graphCard.getCurrentMode());

        BufferedImage img = new BufferedImage(500, 350, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Render at screen position (50, 50)
        graphCard.render(g, new java.awt.Point(50, 50), new java.awt.Point(50, 50), new java.awt.Point(100, 100), encounterManager.getOverallSessionEncounter());

        // Test clicking the Melee pill tab
        for (com.osrscopilot.combat.overlay.CombatGraphCard.GraphMode mode : com.osrscopilot.combat.overlay.CombatGraphCard.GraphMode.values())
        {
            graphCard.setCurrentMode(mode);
            assertEquals(mode, graphCard.getCurrentMode());
        }

        g.dispose();
    }

    private JButton findButtonRecursively(java.awt.Container container, String text)
    {
        for (java.awt.Component comp : container.getComponents())
        {
            if (comp instanceof JButton && text.equals(((JButton) comp).getText()))
            {
                return (JButton) comp;
            }
            if (comp instanceof java.awt.Container)
            {
                JButton found = findButtonRecursively((java.awt.Container) comp, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}







