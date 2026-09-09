package com.osrscopilot.combat.tutorial;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.model.EncounterSegment;
import com.osrscopilot.combat.overlay.CombatGraphCard;
import com.osrscopilot.combat.overlay.CombatGraphOverlay;
import com.osrscopilot.combat.overlay.CombatMeterOverlay;
import com.osrscopilot.combat.overlay.CombatMeterSettingsCard;
import com.osrscopilot.combat.overlay.CombatStateBannerOverlay;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P3: the anchored Combat HUD walkthrough. Verifies every step's spotlight resolves against a live
 * headless render of the real HUD, the state machine + gated hand-off behave, and the whole run
 * leaves the encounter manager's live state untouched.
 */
public class CombatTourTest
{
    private Client client;
    private OsrsCopilotConfig config;
    private ConfigManager configManager;
    private CombatEncounterManager mgr;
    private CombatMeterOverlay hud;
    private Graphics2D hg;
    private Graphics2D tg;

    private final int[] openPanel = {0};
    private final int[] openSource = {0};
    private final int[] closeSource = {0};

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        Canvas canvas = mock(Canvas.class);
        when(client.getCanvas()).thenReturn(canvas);
        when(canvas.getWidth()).thenReturn(800);
        when(canvas.getHeight()).thenReturn(600);

        config = mock(OsrsCopilotConfig.class);
        when(config.showCombatOverlay()).thenReturn(true);
        when(config.maxCombatBars()).thenReturn(5);
        when(config.combatAutoHide()).thenReturn(false);
        when(config.combatOverlayOpacity()).thenReturn(88);
        when(config.combatOverlayWidth()).thenReturn(240);
        when(config.combatShowMiniFooter()).thenReturn(true);
        when(config.combatShowBossProgress()).thenReturn(true);
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

        configManager = mock(ConfigManager.class);
        mgr = new CombatEncounterManager(client);
        CombatStateBannerOverlay bannerOverlay = new CombatStateBannerOverlay(client, config, mgr);
        CombatMeterSettingsCard settingsCard = new CombatMeterSettingsCard(config, configManager, mgr, bannerOverlay);
        CombatGraphOverlay graphOverlay = new CombatGraphOverlay(client, config, mgr, new CombatGraphCard());
        hud = new CombatMeterOverlay(client, config, configManager, mgr, mock(TooltipManager.class), settingsCard, graphOverlay);
        hud.setOnOpenSourceDetails((scope, entity) -> openSource[0]++);
        // Stand in for OverlayManager: place the HUD roughly where the tour centres it, so anchors
        // (which are overlayBounds-relative) resolve to realistic on-canvas screen coordinates.
        hud.getBounds().setBounds(250, 190, 240, 200);

        hg = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB).createGraphics();
        tg = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB).createGraphics();
    }

    private final java.util.List<String> panelScrolls = new java.util.ArrayList<>();

    private Tour tour()
    {
        return CombatHudTour.build(hud, mgr,
            () -> openPanel[0]++, () -> openSource[0]++, () -> closeSource[0]++,
            key -> panelScrolls.add(key));
    }

    private static MouseEvent press(int x, int y)
    {
        return new MouseEvent(new Canvas(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
            0, x, y, 1, false, MouseEvent.BUTTON1);
    }

    @Test
    public void stepListIsWellFormed()
    {
        Tour t = tour();
        assertEquals(15, t.getStepCount());
        for (int i = 0; i < t.getStepCount(); i++)
        {
            TutorialStep s = t.getStep(i);
            assertFalse("step " + i + " title", s.getTitle().trim().isEmpty());
            assertFalse("step " + i + " body", s.getBody().trim().isEmpty());
        }
        assertEquals(TutorialStep.Mode.GATED, t.getStep(9).getMode());
        assertEquals(TutorialStep.Mode.DEMO, t.getStep(2).getMode());
        assertEquals(TutorialStep.Mode.DEMO, t.getStep(8).getMode());
        // The four panel steps pin their caption beside the side panel.
        assertEquals(TutorialStep.Placement.SIDE_PANEL, t.getStep(10).getPlacement());
        assertEquals(TutorialStep.Placement.SIDE_PANEL, t.getStep(13).getPlacement());
    }

    @Test
    public void everyAnchoredStepResolvesAnOnCanvasRectAcrossTheRun()
    {
        Tour t = tour();
        TutorialOverlay ov = new TutorialOverlay(client);
        ov.start(t);
        assertTrue(mgr.isDemoMode());

        for (int i = 0; i < t.getStepCount(); i++)
        {
            assertEquals(i, ov.currentIndex());
            hud.render(hg); // real HUD layout for this step's state (dropdown/card open, etc.)

            TutorialStep s = t.getStep(i);
            Rectangle r = s.resolveTarget();
            // Spotlit steps (bars/metric/scope/footer/graph/settings/panel) must resolve on canvas;
            // the intro, outro and the four panel steps have no target (caption only).
            boolean spotlit = i >= 1 && i <= 9;
            if (spotlit)
            {
                assertNotNull("step " + i + " (" + s.getTitle() + ") has a spotlight", r);
                assertTrue("step " + i + " spotlight on canvas",
                    r.x >= 0 && r.y >= -16 && r.x + r.width <= 800 && r.y + r.height <= 600
                        && r.width > 0 && r.height > 0);
            }
            else
            {
                assertNull("step " + i + " (" + s.getTitle() + ") is caption-only", r);
            }
            if (i < t.getStepCount() - 1)
            {
                ov.next();
            }
        }

        ov.next(); // past the last step -> end
        assertFalse(ov.isActive());
        assertFalse("demo mode cleared", mgr.isDemoMode());
        assertTrue("source window opened at least once", openSource[0] >= 1);
        assertTrue("source window closed on teardown", closeSource[0] >= 1);
        assertTrue("panel walk-through drove all four sections",
            panelScrolls.contains("top") && panelScrolls.contains("damage")
                && panelScrolls.contains("ledger") && panelScrolls.contains("share"));
        assertNull("teardown fires tourScrollTo(null) to clear every panel highlight",
            panelScrolls.get(panelScrolls.size() - 1));
    }

    @Test
    public void demoForcesTheFullHeaderEvenWhenThePlayerHidIt()
    {
        when(config.combatHeaderMode()).thenReturn(OsrsCopilotConfig.HeaderModeOption.HIDDEN);

        hud.render(hg);
        assertTrue("header hidden without a tour", hud.getTutorialAnchors().get("metric").isEmpty());

        TutorialOverlay ov = new TutorialOverlay(client);
        ov.start(tour());
        hud.render(hg);
        assertFalse("tour forces the metric pill back on screen",
            hud.getTutorialAnchors().get("metric").isEmpty());

        ov.end();
        hud.render(hg);
        assertTrue("header hidden again after the tour", hud.getTutorialAnchors().get("metric").isEmpty());
    }

    @Test
    public void gatedPanelStepAdvancesOnAClickInThePanelButton()
    {
        Tour t = tour();
        TutorialOverlay ov = new TutorialOverlay(client);
        ov.start(t);
        for (int i = 0; i < 9; i++)
        {
            ov.next();
        }
        assertEquals(9, ov.currentIndex());

        hud.render(hg);
        ov.render(tg);
        Rectangle panel = hud.getTutorialAnchors().get("panel");
        assertFalse("panel anchor present", panel.isEmpty());

        ov.mousePressed(press(panel.x + panel.width / 2, panel.y + panel.height / 2));
        assertEquals("gated step advanced on the real click", 10, ov.currentIndex());
        assertTrue("and opened the panel", openPanel[0] >= 1);
    }

    @Test
    public void theWholeRunNeverMutatesLiveEncounterState()
    {
        mgr.getCurrentSessionEncounter().getLocalPlayerStats().setTotalDamage(50_000);
        mgr.getOverallSessionEncounter().getLocalPlayerStats().setTotalDamage(900_000);
        EncounterSegment liveBefore = mgr.getSelectedOrCurrentEncounter();

        TutorialOverlay ov = new TutorialOverlay(client);
        ov.start(tour());
        for (int i = 0; i < 20; i++)
        {
            hud.render(hg);
            ov.next(); // walks to the end and then no-ops
        }
        ov.end();

        assertFalse(ov.isActive());
        assertFalse(mgr.isDemoMode());
        assertSame(liveBefore, mgr.getSelectedOrCurrentEncounter());
        assertEquals(50_000, mgr.getCurrentSessionEncounter().getLocalPlayerStats().getTotalDamage());
        assertEquals(900_000, mgr.getOverallSessionEncounter().getLocalPlayerStats().getTotalDamage());
    }

    @Test
    public void backFromTheFirstStepIsANoOp()
    {
        TutorialOverlay ov = new TutorialOverlay(client);
        ov.start(tour());
        ov.back();
        assertEquals(0, ov.currentIndex());
        assertTrue(ov.isActive());
    }
}
