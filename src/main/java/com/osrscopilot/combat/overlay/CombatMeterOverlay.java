package com.osrscopilot.combat.overlay;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.combat.CombatMeterColors;
import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.model.ConsumableUsageEntry;
import com.osrscopilot.combat.model.EncounterSegment;
import com.osrscopilot.combat.model.EntityCombatStats;
import com.osrscopilot.combat.model.StyleDamageEntry;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

@Singleton
@Slf4j
public class CombatMeterOverlay extends Overlay implements MouseListener, net.runelite.client.input.MouseWheelListener
{
    public enum MetricMode
    {
        // One short name per mode, used identically on the HUD pill, the mode dropdown and the
        // settings-card pills (they used to disagree: "Damage Done" / "Damage" / "Damage").
        DAMAGE_DONE("Damage", "DPS", new Color(230, 74, 25), new Color(255, 112, 67)),
        DAMAGE_TAKEN("Taken", "DTPS", CombatMeterColors.DAMAGE_TAKEN_PRIMARY, CombatMeterColors.DAMAGE_TAKEN_GRADIENT),
        HEALING_DONE("Healing", "HPS", CombatMeterColors.HEALING_PRIMARY, CombatMeterColors.HEALING_GRADIENT),
        CONSUMABLES("Supplies", "GP", CombatMeterColors.SUPPLY_PRIMARY, CombatMeterColors.SUPPLY_GRADIENT);

        private final String title;
        private final String rateUnit;
        private final Color primaryColor;
        private final Color gradientColor;

        MetricMode(String title, String rateUnit, Color primaryColor, Color gradientColor)
        {
            this.title = title;
            this.rateUnit = rateUnit;
            this.primaryColor = primaryColor;
            this.gradientColor = gradientColor;
        }

        public String getTitle()
        {
            return title;
        }

        public String getRateUnit()
        {
            return rateUnit;
        }

        public Color getPrimaryColor()
        {
            return primaryColor;
        }

        public Color getGradientColor()
        {
            return gradientColor;
        }
    }

    private final Client client;
    private final OsrsCopilotConfig config;
    private final ConfigManager configManager;

    private final CombatEncounterManager encounterManager;
    private final TooltipManager tooltipManager;
    @Getter
    private final CombatMeterSettingsCard settingsCard;
    private final CombatGraphOverlay graphOverlay;

    private static final int HEADER_HEIGHT = 18;
    private static final int FOOTER_HEIGHT = 16;
    private static final int BAR_GAP = 2;   // default; overridable via config.combatBarGap()
    private static final int PADDING = 4;

    // Hoisted out of render() - these are fixed and were being allocated ~50x/second.
    private static final Color INNER_HIGHLIGHT = new Color(255, 255, 255, 22);
    private static final Color BTN_IDLE_BG = new Color(24, 24, 30, 220);
    private static final Color MUTED_TXT = new Color(150, 150, 160);
    private static final Color HEADER_LABEL = new Color(120, 144, 156);
    private static final Color DIVIDER = new Color(55, 55, 65);
    private static final Color GRAPH_ICON = new Color(129, 199, 132);
    private static final Color PANEL_ICON = new Color(100, 181, 246);
    private static final Color FOOTER_TKN = new Color(239, 83, 80);
    private static final Color FOOTER_HEAL = new Color(102, 187, 106);
    private static final Color FOOTER_POTS = new Color(77, 208, 225);
    private static final Color FOOTER_FOOD = new Color(255, 215, 0);
    private static final Color BAR_OUTLINE = new Color(0, 0, 0, 90);
    private static final Color BAR_HOVER_SHEEN = new Color(255, 255, 255, 45);
    private static final BasicStroke STROKE_1 = new BasicStroke(1.0f);
    private static final BasicStroke STROKE_1_2 = new BasicStroke(1.2f);
    private static final Color GRIP_IDLE = new Color(255, 255, 255, 70);
    private static final Color DEMO_CHIP_BG = new Color(123, 63, 178, 240); // quest purple, tutorial marker
    private static final Color HELP_RED = new Color(229, 83, 75); // the "?" tour button
    private static final Color GRIP_ACTIVE = new Color(255, 255, 255, 150);
    private static final Color DROPDOWN_INNER_HL = new Color(255, 255, 255, 20);

    // Cache the opacity-derived background colour; recompute only when the alpha % changes.
    private int cachedBgAlphaPct = -1;
    private Color cachedBg;

    @Getter
    private MetricMode currentMode = MetricMode.DAMAGE_DONE;

    private Runnable onOpenCombatTab;
    private Runnable onStartTour;
    // Right-click on the HUD (a bar, or the meter body) -> open the "Damage Sources" window,
    // scoped to the live encounter and (if a bar was hit) that participant.
    private java.util.function.BiConsumer<EncounterSegment, EntityCombatStats> onOpenSourceDetails;
    @Getter
    private boolean settingsOpen = false;

    public void setOnOpenCombatTab(Runnable onOpenCombatTab)
    {
        this.onOpenCombatTab = onOpenCombatTab;
    }

    /** Wired by the plugin to launch the interactive HUD walkthrough from the header "?" button. */
    public void setOnStartTour(Runnable onStartTour)
    {
        this.onStartTour = onStartTour;
    }

    public void setOnOpenSourceDetails(java.util.function.BiConsumer<EncounterSegment, EntityCombatStats> cb)
    {
        this.onOpenSourceDetails = cb;
    }

    private Rectangle modeBounds = new Rectangle();
    private Rectangle segmentBounds = new Rectangle();
    private Rectangle settingsBounds = new Rectangle();
    private Rectangle settingsCardBounds = new Rectangle();
    private Rectangle graphBounds = new Rectangle();
    private Rectangle panelBounds = new Rectangle();
    private Rectangle helpBounds = new Rectangle();
    private Rectangle overlayBounds = new Rectangle();
    private Rectangle resizeGripBounds = new Rectangle();
    // Screen-space footer bands, captured in render() for the interactive tutorial's spotlights.
    private volatile Rectangle footerBossBounds = new Rectangle();
    private volatile Rectangle footerMiniBounds = new Rectangle();
    private volatile Rectangle segmentDropdownBounds = new Rectangle();

    // Interactive tutorial: suppress the hover inspection card + header tooltips (they clutter the
    // walkthrough), and centre the HUD for the tour, restoring the player's own placement after.
    private boolean suppressHoverChrome = false;
    private boolean tutorialLayoutActive = false;
    private Point savedPreferredLocation;
    private OverlayPosition savedPreferredPosition;

    private boolean isResizing = false;
    private boolean isScrollDragging = false;
    private int scrollDragStartY = 0;
    private int scrollDragStartScroll = 0;
    private Point dragStartPoint = null;
    private int dragStartWidth = 220;
    private int dragStartBarHeight = 20;
    private int lastAppliedWidth = -1;
    private int lastAppliedBarHeight = -1;

    @Getter
    private boolean modeDropdownOpen = false;
    private boolean segmentDropdownOpen = false;
    // Written on the client thread in render(), read on the AWT thread in mousePressed(). Each
    // render builds a fresh collection and publishes it via these volatile refs, so a click can
    // never see a half-cleared map (was an intermittent ConcurrentModificationException).
    private volatile Map<Integer, Rectangle> segmentDropdownHitboxes = java.util.Collections.emptyMap();
    private volatile Map<MetricMode, Rectangle> modeDropdownHitboxes = java.util.Collections.emptyMap();
    private volatile List<BarHitbox> barHitboxes = java.util.Collections.emptyList();

    public Map<MetricMode, Rectangle> getModeDropdownHitboxes()
    {
        return modeDropdownHitboxes;
    }

    @Inject
    public CombatMeterOverlay(
        Client client,
        OsrsCopilotConfig config,
        ConfigManager configManager,
        CombatEncounterManager encounterManager,
        TooltipManager tooltipManager,
        CombatMeterSettingsCard settingsCard,
        CombatGraphOverlay graphOverlay)
    {
        this.client = client;
        this.config = config;
        this.configManager = configManager;
        this.encounterManager = encounterManager;
        this.tooltipManager = tooltipManager;
        this.settingsCard = settingsCard;
        this.graphOverlay = graphOverlay;

        if (this.settingsCard != null)
        {
            this.settingsCard.setOnClose(() -> this.settingsOpen = false);
        }

        setPosition(OverlayPosition.CANVAS_TOP_RIGHT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
        setMovable(true);
        // Bar height x count still drives the natural size, but the bottom-right grip lets you
        // drag it: horizontal drag -> "Overlay Width", vertical drag -> "Bar Height" (both
        // written straight to config, so it persists and stays in sync with the steppers).
        setMinimumSize(160);
        // Right-click the HUD -> quick actions + "Configure".
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Combat panel", "Combat Meter",
            e -> { if (onOpenCombatTab != null) onOpenCombatTab.run(); });
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Meter settings", "Combat Meter",
            e -> { settingsOpen = !settingsOpen; modeDropdownOpen = false; segmentDropdownOpen = false; });
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Fight graph", "Combat Meter", e -> toggleGraph());
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset fight", "Combat Meter",
            e -> { if (encounterManager != null) encounterManager.resetCurrentEncounter(); });
        addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OverlayManager.OPTION_CONFIGURE, "Combat Meter overlay");
    }

    public void cycleMode()
    {
        MetricMode[] values = MetricMode.values();
        int next = (currentMode.ordinal() + 1) % values.length;
        currentMode = values[next];
    }

    public void setMode(MetricMode mode)
    {
        if (mode != null)
        {
            this.currentMode = mode;
        }
    }

    public void toggleGraph()
    {
        if (graphOverlay != null)
        {
            graphOverlay.toggle();
        }
    }

    public boolean isGraphOpen()
    {
        return graphOverlay != null && graphOverlay.isOpen();
    }

    public void setGraphOpen(boolean graphOpen)
    {
        if (graphOverlay != null)
        {
            graphOverlay.setOpen(graphOpen);
        }
    }

    public void setSettingsOpen(boolean settingsOpen)
    {
        this.settingsOpen = settingsOpen;
    }

    public CombatGraphOverlay getGraphOverlay()
    {
        return graphOverlay;
    }

    public CombatGraphCard getGraphCard()
    {
        return graphOverlay != null ? graphOverlay.getGraphCard() : null;
    }

    // ---- Interactive tutorial hooks -------------------------------------------------------------

    /**
     * Screen-space rectangles of the HUD controls the walkthrough spotlights, keyed by role:
     * {@code metric}, {@code scope}, {@code graph}, {@code settings}, {@code panel}, {@code grip},
     * {@code bars} (the union of all bar rows), {@code bossline}, {@code minifooter} and
     * {@code footer} (both bands together). Read fresh each frame from the last {@link #render}; a
     * role maps to an empty rectangle when that control is not currently on screen.
     */
    public Map<String, Rectangle> getTutorialAnchors()
    {
        Map<String, Rectangle> a = new HashMap<>();
        a.put("metric", copy(modeBounds));
        a.put("scope", copy(segmentBounds));
        a.put("graph", copy(graphBounds));
        a.put("settings", copy(settingsBounds));
        a.put("panel", copy(panelBounds));
        a.put("grip", copy(resizeGripBounds));
        a.put("bars", barsUnion());
        a.put("bossline", copy(footerBossBounds));
        a.put("minifooter", copy(footerMiniBounds));
        a.put("footer", union(footerBossBounds, footerMiniBounds));
        // The opened-popup variants, so a demo step can spotlight the thing it just opened (which
        // sits below the HUD) instead of the button that opened it.
        a.put("scopemenu", copy(segmentDropdownBounds));
        a.put("settingscard", copy(settingsCardBounds));
        a.put("graphcard", (graphOverlay != null && graphOverlay.isOpen()) ? copy(graphOverlay.getBounds()) : new Rectangle());
        return a;
    }

    /** Open / close the scope (segment) dropdown - used by the tutorial's scope step. */
    public void setScopeDropdownOpen(boolean open)
    {
        this.segmentDropdownOpen = open;
        if (open)
        {
            this.modeDropdownOpen = false;
            this.settingsOpen = false;
        }
    }

    /**
     * Move the HUD to a centred, comfortable spot for the walkthrough and remember where the player
     * had it. {@link #endTutorialLayout()} puts it back exactly. Also mutes the hover inspection
     * card and header tooltips for the tour's duration.
     */
    public void beginTutorialLayout()
    {
        this.suppressHoverChrome = true;
        if (tutorialLayoutActive)
        {
            return;
        }
        tutorialLayoutActive = true;
        // The actual re-centre happens in render() on the client thread, where the canvas size and
        // this frame's HUD height are known; this is only called off-thread (a button click).
        savedPreferredLocation = getPreferredLocation();
        savedPreferredPosition = getPreferredPosition();
    }

    /** Restore the player's own HUD placement and re-enable the hover chrome. */
    public void endTutorialLayout()
    {
        this.suppressHoverChrome = false;
        if (!tutorialLayoutActive)
        {
            return;
        }
        tutorialLayoutActive = false;
        setPreferredPosition(savedPreferredPosition);
        setPreferredLocation(savedPreferredLocation);
    }

    /** Open the "Damage Sources" window on the top-ranked participant of the shown scope. */
    public void openSourceDetailsForTopParticipant()
    {
        if (onOpenSourceDetails == null || encounterManager == null)
        {
            return;
        }
        EncounterSegment scope = encounterManager.getSelectedOrCurrentEncounter();
        EntityCombatStats top = null;
        if (scope != null)
        {
            List<EntityCombatStats> ranked = scope.getRankedParticipants(1);
            if (!ranked.isEmpty())
            {
                top = ranked.get(0);
            }
        }
        onOpenSourceDetails.accept(scope, top);
    }

    private static Rectangle copy(Rectangle r)
    {
        return (r != null && r.width > 0 && r.height > 0) ? new Rectangle(r) : new Rectangle();
    }

    private Rectangle barsUnion()
    {
        Rectangle u = null;
        for (BarHitbox bh : barHitboxes)
        {
            if (bh.bounds != null && bh.bounds.width > 0)
            {
                u = (u == null) ? new Rectangle(bh.bounds) : u.union(bh.bounds);
            }
        }
        return u != null ? u : new Rectangle();
    }

    private static Rectangle union(Rectangle a, Rectangle b)
    {
        boolean va = a != null && a.width > 0 && a.height > 0;
        boolean vb = b != null && b.width > 0 && b.height > 0;
        if (va && vb)
        {
            return a.union(b);
        }
        if (va)
        {
            return new Rectangle(a);
        }
        return vb ? new Rectangle(b) : new Rectangle();
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        // Build hitboxes into a fresh list this frame; publish at the end. If render bails below,
        // publish the empty list so a stale bar rect doesn't stay clickable over empty canvas.
        List<BarHitbox> bars = new ArrayList<>();

        boolean demo = encounterManager != null && encounterManager.isDemoMode();

        if (encounterManager == null || config == null || (!config.showCombatOverlay() && !demo))
        {
            barHitboxes = bars;
            return null;
        }

        EncounterSegment currentEncounter = encounterManager.getSelectedOrCurrentEncounter();
        if (currentEncounter == null)
        {
            currentEncounter = encounterManager.getOverallSessionEncounter();
        }
        if (currentEncounter == null)
        {
            barHitboxes = bars;
            return null;
        }

        List<EntityCombatStats> rankedEntities = currentEncounter.getRankedParticipants(config.maxCombatBars());

        // The HUD keeps showing the last fight after combat (classic combat-meter behaviour). It never
        // collapses or vanishes while showCombatOverlay is on - "Auto hide" only dims it while idle
        // so it stays fully readable and every control stays clickable.
        boolean idleDim = !settingsOpen && !modeDropdownOpen && !segmentDropdownOpen
            && config.combatAutoHide() && !currentEncounter.isInCombat();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // "Dim HUD when idle": fade the WHOLE overlay (bars + text, not just the background) to
        // 40% while out of combat. Restored right before we return.
        java.awt.Composite originalComposite = g.getComposite();
        if (idleDim)
        {
            g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.4f));
        }

        // Size is fully derived: width from the "Overlay Width" setting, height from
        // "Bar Height" x number of bars + chrome. No getPreferredSize() / drag-resize involved.
        int width = Math.max(160, Math.min(420, config.combatOverlayWidth()));
        int barHeight = Math.max(10, Math.min(44, config.combatBarHeight()));
        int barCount = Math.max(1, rankedEntities.size());
        int barGap = Math.max(0, Math.min(6, config.combatBarGap()));
        // During the interactive tutorial, force the full header + both footer rows so every
        // walkthrough step has its control on screen regardless of the player's own HUD settings.
        OsrsCopilotConfig.HeaderModeOption headerMode =
            demo ? OsrsCopilotConfig.HeaderModeOption.FULL : config.combatHeaderMode();
        int headerH = headerMode == OsrsCopilotConfig.HeaderModeOption.HIDDEN ? 2 : HEADER_HEIGHT;

        CombatEncounterManager.LiveBossProgress bossProg =
            (demo || config.combatShowBossProgress()) ? encounterManager.getLiveBossProgress() : null;
        int bossLineH = bossProg != null ? 13 : 0;
        boolean miniFooter = demo || config.combatShowMiniFooter();
        int footerContentH = bossLineH + (miniFooter ? FOOTER_HEIGHT : 0);
        int footerBlockH = footerContentH > 0 ? footerContentH + 2 : 0;

        int headerAndFooter = PADDING * 2 + headerH + footerBlockH;
        int totalHeight = headerAndFooter + (barCount * (barHeight + barGap));

        // Interactive tutorial: park the HUD centred for the walkthrough. Uses the game-logical
        // canvas size (getCanvasWidth/Height) - the AWT Canvas can report scaled/stretched pixels
        // that don't match the coordinate space overlays are placed in. Restored by endTutorialLayout().
        if (demo && tutorialLayoutActive && client != null)
        {
            int cw = client.getCanvasWidth();
            int ch = client.getCanvasHeight();
            if (cw > 0 && ch > 0)
            {
                // Horizontally centred, and lifted above the exact vertical centre so it sits in
                // the upper-middle with room for popups and the caption below.
                int lift = Math.max(60, Math.min(170, ch / 5));
                setPreferredPosition(OverlayPosition.DETACHED);
                setPreferredLocation(new Point(
                    Math.max(8, cw / 2 - width / 2),
                    Math.max(8, Math.min(ch - totalHeight - 8, ch / 2 - totalHeight / 2 - lift))));
            }
        }

        overlayBounds = getBounds();
        if (overlayBounds == null)
        {
            overlayBounds = new Rectangle(0, 0, width, totalHeight);
        }
        // Self-heal: if the HUD has been dragged fully off the visible canvas, snap it back.
        try
        {
            if (client != null && client.getCanvas() != null)
            {
                int cw = client.getCanvas().getWidth();
                int ch = client.getCanvas().getHeight();
                boolean offScreen = overlayBounds.x > cw - 20 || overlayBounds.y > ch - 20
                    || overlayBounds.x + overlayBounds.width < 20 || overlayBounds.y + overlayBounds.height < 20;
                if (offScreen && cw > 0 && ch > 0)
                {
                    setPreferredLocation(null);
                    setPreferredPosition(OverlayPosition.CANVAS_TOP_RIGHT);
                }
            }
        }
        catch (RuntimeException e)
        {
            log.trace("Overlay off-screen self-heal check failed", e);
        }

        int alphaPct = Math.max(20, config.combatOverlayOpacity()); // matches the config @Range floor
        if (idleDim)
        {
            alphaPct = Math.max(20, alphaPct / 2); // dim (never hide) between fights when auto-hide is on
        }
        if (alphaPct != cachedBgAlphaPct || cachedBg == null)
        {
            cachedBg = new Color(14, 14, 18, (int) (255 * (alphaPct / 100.0f)));
            cachedBgAlphaPct = alphaPct;
        }

        // 1. Main Background, Outer Border, and Inner 1px Highlight
        g.setColor(cachedBg);
        g.fillRoundRect(0, 0, width, totalHeight, 6, 6);

        g.setColor(CombatMeterColors.HUD_BORDER);
        g.setStroke(STROKE_1);
        g.drawRoundRect(0, 0, width - 1, totalHeight - 1, 6, 6);

        g.setColor(INNER_HIGHLIGHT);
        g.drawRoundRect(1, 1, width - 3, totalHeight - 3, 5, 5);

        Point headerMouse = (client != null && client.getMouseCanvasPosition() != null)
            ? new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY())
            : null;

        // 2. Header Bar with Segment Title, Buttons, and Mode Dropdown Trigger
        if (headerMode == OsrsCopilotConfig.HeaderModeOption.HIDDEN)
        {
            // Bars only. Zero the header hitboxes so stale rects can't stay clickable, and make
            // sure a dropdown opened before the header was hidden doesn't linger.
            modeBounds = new Rectangle();
            segmentBounds = new Rectangle();
            graphBounds = new Rectangle();
            settingsBounds = new Rectangle();
            panelBounds = new Rectangle();
            helpBounds = new Rectangle();
            modeDropdownOpen = false;
            segmentDropdownOpen = false;
        }
        else
        {
            // During the tutorial a "DEMO" chip takes the far-left of the header (pushing the metric
            // pill right) so the staged numbers can never be mistaken for a real fight.
            int demoInset = 0;
            if (demo)
            {
                g.setFont(FontManager.getRunescapeSmallFont());
                int chipW = g.getFontMetrics().stringWidth("DEMO") + 10;
                int chipH = HEADER_HEIGHT - 3;
                g.setColor(DEMO_CHIP_BG);
                g.fillRoundRect(PADDING, PADDING + 1, chipW, chipH, 4, 4);
                g.setColor(CombatMeterColors.HUD_BORDER);
                g.drawRoundRect(PADDING, PADDING + 1, chipW - 1, chipH - 1, 4, 4);
                g.setColor(Color.WHITE);
                g.drawString("DEMO", PADDING + 5, PADDING + chipH - 3);
                demoInset = chipW + 3;
            }
            renderHeaderControls(g, width, currentEncounter, headerMouse, headerMode, demoInset);
        }

        int curY = PADDING + headerH + 2;

        // 3. Progress Bars

        long maxMetricVal = 0;
        long totalMetricVal = 0;
        for (EntityCombatStats entity : rankedEntities)
        {
            long val = getEntityMetricValue(entity, currentMode);
            if (val > maxMetricVal) maxMetricVal = val;
            totalMetricVal += val;
        }

        Point mousePos = (client != null && client.getMouseCanvasPosition() != null)
            ? new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY())
            : null;

        EntityCombatStats hoveredEntity = null;
        int hoveredRank = 1;

        for (int i = 0; i < rankedEntities.size(); i++)
        {
            EntityCombatStats entity = rankedEntities.get(i);
            Rectangle barRect = new Rectangle(PADDING, curY, width - (PADDING * 2), barHeight);
            Rectangle screenBarRect = new Rectangle(overlayBounds.x + barRect.x, overlayBounds.y + barRect.y, barRect.width, barRect.height);
            bars.add(new BarHitbox(screenBarRect, entity));

            boolean isHovered = (mousePos != null && screenBarRect.contains(mousePos));
            if (isHovered)
            {
                hoveredEntity = entity;
                hoveredRank = i + 1;
            }

            renderProgressBar(g, barRect.x, barRect.y, barRect.width, barRect.height, i + 1, entity, maxMetricVal, totalMetricVal, isHovered);
            curY += barHeight + barGap;
        }

        if (rankedEntities.isEmpty())
        {
            g.setFont(FontManager.getRunescapeSmallFont());
            g.setColor(MUTED_TXT);
            String hint = currentEncounter.isInCombat() ? "Waiting for the first hit..." : "No combat data - attack something";
            g.drawString(hint, PADDING + 4, curY + barHeight - 6);
            curY += barHeight + barGap;
        }

        // 4. Footer: optional live boss-progress line, then the optional mini-stat footer.
        Rectangle bossBand = new Rectangle();
        Rectangle miniBand = new Rectangle();
        if (bossLineH > 0 || miniFooter)
        {
            curY += 2;
            if (bossLineH > 0)
            {
                renderBossProgressLine(g, PADDING, curY, width - (PADDING * 2), bossProg,
                    currentEncounter.getDurationSeconds());
                bossBand = new Rectangle(overlayBounds.x + PADDING, overlayBounds.y + curY,
                    width - (PADDING * 2), bossLineH);
                curY += bossLineH;
            }
            if (miniFooter)
            {
                renderMiniFooter(g, PADDING, curY, width - (PADDING * 2), FOOTER_HEIGHT, currentEncounter);
                miniBand = new Rectangle(overlayBounds.x + PADDING, overlayBounds.y + curY,
                    width - (PADDING * 2), FOOTER_HEIGHT);
            }
        }
        footerBossBounds = bossBand;
        footerMiniBounds = miniBand;

        // 5. Draw floating inspection card on hover (if settings not open)
        if (hoveredEntity != null && !settingsOpen && !suppressHoverChrome)
        {
            renderFloatingInspectionCard(g, width, totalHeight, hoveredEntity, hoveredRank, currentEncounter);
        }

        // 6. Draw Canvas Settings Card when open
        if (settingsOpen && settingsCard != null)
        {
            int canvasH = 0;
            try
            {
                canvasH = (client != null && client.getCanvas() != null) ? client.getCanvas().getHeight() : 0;
            }
            catch (RuntimeException e)
            {
                log.trace("Canvas height read failed", e);
            }
            int cardVisibleH = canvasH > 240
                ? Math.min(CombatMeterSettingsCard.CARD_HEIGHT, canvasH - 16)
                : CombatMeterSettingsCard.CARD_HEIGHT;

            int localX = Math.min(0, width - CombatMeterSettingsCard.CARD_WIDTH);
            // Prefer directly below the HUD. If that would run off the bottom, anchor the card to
            // the TOP of the HUD instead (its bottom edge at the HUD's top edge), capping the
            // viewport to the room available above - the card scrolls, so that's fine.
            int localY;
            if (canvasH > 0)
            {
                int belowScreenTop = overlayBounds.y + totalHeight + 3;
                if (belowScreenTop + cardVisibleH <= canvasH - 8)
                {
                    localY = totalHeight + 3;
                }
                else
                {
                    // Anchor to the HUD's top edge; if the card is taller than the room above, let
                    // its top sit at the canvas edge and shrink the viewport so it stays on screen.
                    int screenTop = overlayBounds.y - 3 - cardVisibleH;
                    if (screenTop < 8)
                    {
                        screenTop = 8;
                        cardVisibleH = Math.min(cardVisibleH, canvasH - 16);
                        cardVisibleH = Math.min(cardVisibleH, canvasH - 8 - screenTop);
                    }
                    localY = screenTop - overlayBounds.y;
                }
            }
            else
            {
                localY = popupY(totalHeight + 3, cardVisibleH);
            }
            settingsCard.setViewportHeight(cardVisibleH);
            Point localPos = new Point(localX, localY);
            Point screenPos = new Point(overlayBounds.x + localX, overlayBounds.y + localY);
            settingsCard.render(g, localPos, screenPos, mousePos, currentMode);
            settingsCardBounds = new Rectangle(screenPos.x, screenPos.y,
                CombatMeterSettingsCard.CARD_WIDTH, cardVisibleH);
        }
        else
        {
            settingsCardBounds = new Rectangle();
        }

        // 7. Resize grip (bottom-right). Drag: horizontal -> width, vertical -> bar height.
        int gs = 10;
        int gx = width - gs - 2;
        int gy = totalHeight - gs - 2;
        g.setColor(isResizing ? GRIP_ACTIVE : GRIP_IDLE);
        for (int i = 0; i < 3; i++)
        {
            int off = i * 3;
            g.drawLine(gx + gs - off, gy + gs, gx + gs, gy + gs - off);
        }
        resizeGripBounds = new Rectangle(overlayBounds.x + gx - 3, overlayBounds.y + gy - 3, gs + 6, gs + 6);
        headerTip(headerMouse, resizeGripBounds, "Drag: width + bar height");

        barHitboxes = bars;

        // 8. Draw Canvas Mode / Segment Dropdown Menus when open
        if (modeDropdownOpen)
        {
            renderModeDropdown(g, overlayBounds, mousePos);
        }
        else
        {
            modeDropdownHitboxes = java.util.Collections.emptyMap();
        }
        if (segmentDropdownOpen)
        {
            renderSegmentDropdown(g, overlayBounds, mousePos);
        }
        else
        {
            segmentDropdownHitboxes = java.util.Collections.emptyMap();
            segmentDropdownBounds = new Rectangle();
        }

        g.setComposite(originalComposite);
        return new Dimension(width, totalHeight);
    }

    // Special hitbox keys for the scope dropdown. AUTOFOLLOW is the toggle row; a merged row's
    // expand caret is keyed SEG_HITBOX_EXPAND_BASE - rowIndex.
    private static final int SEG_HITBOX_AUTOFOLLOW = -100;
    private static final int SEG_HITBOX_EXPAND_BASE = -200;

    // Which merged "x N" row (by segment id) is expanded to show its per-kill rows, if any.
    private java.util.UUID expandedSegId = null;

    /**
     * Local Y (relative to the overlay's top-left) at which to draw a popup of height
     * {@code popupHeight}: its natural spot {@code naturalBelowY} if that fits on the canvas,
     * otherwise flipped to sit entirely above the overlay so it can't be clipped off the bottom
     * of the screen. Mirrors the flip the hover inspection card already does.
     */
    private int popupY(int naturalBelowY, int popupHeight)
    {
        try
        {
            int canvasH = (client != null && client.getCanvas() != null) ? client.getCanvas().getHeight() : 0;
            int topY = overlayBounds != null ? overlayBounds.y : 0;
            if (canvasH > 0 && topY + naturalBelowY + popupHeight > canvasH - 6)
            {
                return -popupHeight - 3;
            }
        }
        catch (RuntimeException e)
        {
            log.trace("Canvas height read failed", e);
        }
        return naturalBelowY;
    }

    private void renderSegmentDropdown(Graphics2D g, Rectangle bounds, Point mousePos)
    {
        Map<Integer, Rectangle> hb = new HashMap<>();
        java.util.List<EncounterSegment> segments = encounterManager.getAllSegmentsForDropdown();
        int histCount = Math.max(0, segments.size() - 3);

        int dropX = segmentBounds.x - bounds.x;
        int dropW = Math.max(190, segmentBounds.width + 20);
        int rowH = 17;
        int childH = 14;
        int headH = 13;

        // Extra rows if a merged "x N" row is expanded.
        int expandedChildRows = 0;
        for (EncounterSegment s : segments)
        {
            if (s.getSegmentId().equals(expandedSegId) && s.hasMergedChildren())
            {
                expandedChildRows = s.getMergedChildren().size();
                break;
            }
        }

        // 1 "SCOPES" header + N rows + (history ? 1 header : 0) + expanded kids + divider + autofollow row
        int dropH = headH + (segments.size() * rowH) + (expandedChildRows * childH)
            + (histCount > 0 ? headH : 0) + rowH + 8;
        // Below the header normally; flipped above the overlay when it would run off the bottom.
        int dropY = popupY(PADDING + HEADER_HEIGHT + 2, dropH);
        segmentDropdownBounds = new Rectangle(bounds.x + dropX, bounds.y + dropY, dropW, dropH);

        g.setColor(CombatMeterColors.POPUP_BG);
        g.fillRoundRect(dropX, dropY, dropW, dropH, 6, 6);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(dropX, dropY, dropW - 1, dropH - 1, 6, 6);

        EncounterSegment selected = encounterManager.getSelectedOrCurrentEncounter();
        int curY = dropY + 4;
        g.setFont(FontManager.getRunescapeSmallFont());

        g.setColor(HEADER_LABEL);
        g.drawString("SCOPES", dropX + 6, curY + 9);
        curY += headH;

        for (int i = 0; i < segments.size(); i++)
        {
            if (i == 3 && histCount > 0)
            {
                g.setColor(HEADER_LABEL);
                g.drawString("PAST FIGHTS", dropX + 6, curY + 9);
                curY += headH;
            }

            EncounterSegment seg = segments.get(i);
            Rectangle screenRow = new Rectangle(bounds.x + dropX + 2, bounds.y + curY, dropW - 4, rowH);
            hb.put(i, screenRow);

            boolean isHover = mousePos != null && screenRow.contains(mousePos);
            boolean isActive = seg == selected;
            if (isHover || isActive)
            {
                g.setColor(isActive ? CombatMeterColors.BTN_BG_ACTIVE : CombatMeterColors.BTN_BG_HOVER);
                g.fillRoundRect(dropX + 2, curY, dropW - 4, rowH, 3, 3);
            }
            g.setColor(isActive ? CombatMeterColors.TEXT_ACCENT_GOLD : Color.WHITE);
            // Row 0 is the auto-following "Live" scope; EncounterSegment.toString() labels it
            // itself ("Live - <target>" / "Live - waiting for combat"), so no extra prefix here.
            String scopeName = seg.toString();
            String label = (isActive ? "> " : "  ") + scopeName;
            boolean expandable = seg.isMerged() && seg.hasMergedChildren();
            boolean expanded = expandable && seg.getSegmentId().equals(expandedSegId);
            g.drawString(truncateString(label, dropW - (expandable ? 24 : 12), g), dropX + 6, curY + 12);
            if (expandable)
            {
                g.setColor(MUTED_TXT);
                g.drawString(expanded ? "[-]" : "[+]", dropX + dropW - 20, curY + 12);
                hb.put(SEG_HITBOX_EXPAND_BASE - i,
                    new Rectangle(bounds.x + dropX + dropW - 24, bounds.y + curY, 22, rowH));
            }
            curY += rowH;

            if (expanded)
            {
                java.util.List<EncounterSegment.KillSummary> kids = seg.getMergedChildren();
                g.setFont(FontManager.getRunescapeSmallFont());
                for (EncounterSegment.KillSummary k : kids)
                {
                    g.setColor(k.getStatus() == com.osrscopilot.combat.model.SegmentStatus.WIPED
                        ? FOOTER_TKN : MUTED_TXT);
                    String kl = "   #" + k.getIndex() + "  " + formatDuration(k.getDurationSeconds())
                        + "   " + String.format("%.0f", k.getDps()) + " dps"
                        + "   " + formatAmount(k.getDamageTaken()) + " tkn";
                    g.drawString(truncateString(kl, dropW - 12, g), dropX + 6, curY + 11);
                    curY += childH;
                }
                g.setFont(FontManager.getRunescapeSmallFont());
            }
        }

        // divider + auto-follow toggle
        curY += 3;
        g.setColor(DIVIDER);
        g.drawLine(dropX + 4, curY, dropX + dropW - 4, curY);
        curY += 3;

        Rectangle afRow = new Rectangle(bounds.x + dropX + 2, bounds.y + curY, dropW - 4, rowH);
        hb.put(SEG_HITBOX_AUTOFOLLOW, afRow);
        boolean afHover = mousePos != null && afRow.contains(mousePos);
        if (afHover)
        {
            g.setColor(CombatMeterColors.BTN_BG_HOVER);
            g.fillRoundRect(dropX + 2, curY, dropW - 4, rowH, 3, 3);
        }
        boolean af = encounterManager.isAutoFollow();
        g.setColor(af ? CombatMeterColors.TEXT_ACCENT_GOLD : MUTED_TXT);
        g.drawString((af ? "[x] " : "[ ] ") + "Auto-follow current fight", dropX + 6, curY + 12);
        segmentDropdownHitboxes = hb;
    }

    /** Adds a RuneLite tooltip for a header affordance when the mouse is over it. */
    private void headerTip(Point mousePos, Rectangle screenBounds, String text)
    {
        if (suppressHoverChrome)
        {
            return;
        }
        if (tooltipManager != null && mousePos != null && screenBounds != null && screenBounds.contains(mousePos))
        {
            tooltipManager.add(new Tooltip(text));
        }
    }

    private void renderHeaderControls(Graphics2D g, int width, EncounterSegment currentEncounter, Point mousePos,
                                     OsrsCopilotConfig.HeaderModeOption headerMode, int leftInset)
    {
        boolean minimal = headerMode == OsrsCopilotConfig.HeaderModeOption.MINIMAL;
        int headerY = PADDING + 1;
        int btnHeight = HEADER_HEIGHT - 3;
        int modeX = PADDING + leftInset;

        // Button A: Mode Dropdown Trigger Button (Left: ~62px)
        int modeW = 62;
        g.setColor(modeDropdownOpen ? CombatMeterColors.BTN_BG_ACTIVE : BTN_IDLE_BG);
        g.fillRoundRect(modeX, headerY, modeW, btnHeight, 4, 4);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(modeX, headerY, modeW - 1, btnHeight - 1, 4, 4);

        g.setFont(FontManager.getRunescapeSmallFont());
        Color modeTextColor = modeDropdownOpen ? CombatMeterColors.BTN_TEXT_ACTIVE :
                              currentMode == MetricMode.DAMAGE_DONE ? CombatMeterColors.TEXT_ACCENT_GOLD :
                              currentMode == MetricMode.DAMAGE_TAKEN ? CombatMeterColors.SWATCH_DRAGON_RED :
                              currentMode == MetricMode.HEALING_DONE ? CombatMeterColors.SWATCH_ZULRAH_TEAL :
                              new Color(255, 183, 77);
        g.setColor(modeTextColor);
        g.drawString(currentMode.getTitle(), modeX + 4, headerY + btnHeight - 3);
        HudGlyphs.downCaret(g, modeX + modeW - 8, headerY + 6, 3, 4);
        modeBounds = new Rectangle(overlayBounds.x + modeX, overlayBounds.y + headerY, modeW, btnHeight);
        if (!modeDropdownOpen)
        {
            headerTip(mousePos, modeBounds, "Metric: " + currentMode.getTitle() + "  —  click to change");
        }

        // Button B: right-side icons - Graph, Settings, "?" tour, Side Panel.
        // Reset + Copy Report live in the settings card (rare / destructive, and they were one
        // stray pixel apart from each other in the header).
        int panelX = width - PADDING - 16;
        int helpX = panelX - 18;
        int settingsX = helpX - 18;
        int graphX = settingsX - 18;

        if (minimal)
        {
            // Metric + scope only. Zero the icon hitboxes; the scope pill takes the freed width.
            graphBounds = new Rectangle();
            settingsBounds = new Rectangle();
            panelBounds = new Rectangle();
            helpBounds = new Rectangle();
        }
        else
        {

        // Graph Button [Vector Mini Line Chart]
        boolean isGraphActive = isGraphOpen();
        g.setColor(isGraphActive ? CombatMeterColors.BTN_BG_ACTIVE : BTN_IDLE_BG);
        g.fillRoundRect(graphX, headerY, 16, btnHeight, 3, 3);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(graphX, headerY, 15, btnHeight - 1, 3, 3);
        g.setColor(isGraphActive ? CombatMeterColors.BTN_TEXT_ACTIVE : GRAPH_ICON);
        g.setStroke(STROKE_1_2);
        g.drawLine(graphX + 3, headerY + 10, graphX + 6, headerY + 6);
        g.drawLine(graphX + 6, headerY + 6, graphX + 9, headerY + 8);
        g.drawLine(graphX + 9, headerY + 8, graphX + 12, headerY + 3);
        g.fillOval(graphX + 11, headerY + 2, 3, 3);
        g.setStroke(STROKE_1);
        graphBounds = new Rectangle(overlayBounds.x + graphX, overlayBounds.y + headerY, 16, btnHeight);
        headerTip(mousePos, graphBounds, isGraphActive ? "Fight graph — hide" : "Fight graph — show");

        // Settings Button [cog]
        Color settingsBg = settingsOpen ? CombatMeterColors.BTN_BG_ACTIVE : BTN_IDLE_BG;
        g.setColor(settingsBg);
        g.fillRoundRect(settingsX, headerY, 16, btnHeight, 3, 3);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(settingsX, headerY, 15, btnHeight - 1, 3, 3);
        g.setColor(settingsOpen ? CombatMeterColors.BTN_TEXT_ACTIVE : CombatMeterColors.TEXT_ACCENT_GOLD);
        HudGlyphs.settings(g, settingsX + 3, headerY + 3, 10, settingsBg);
        settingsBounds = new Rectangle(overlayBounds.x + settingsX, overlayBounds.y + headerY, 16, btnHeight);
        if (!settingsOpen)
        {
            headerTip(mousePos, settingsBounds, "Meter settings");
        }

        // "?" Tour Button - small font so the glyph matches the weight of the vector icons.
        g.setColor(BTN_IDLE_BG);
        g.fillRoundRect(helpX, headerY, 16, btnHeight, 3, 3);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(helpX, headerY, 15, btnHeight - 1, 3, 3);
        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(HELP_RED);
        FontMetrics qfm = g.getFontMetrics();
        g.drawString("?", helpX + 8 - qfm.stringWidth("?") / 2, headerY + btnHeight - 4);
        helpBounds = new Rectangle(overlayBounds.x + helpX, overlayBounds.y + headerY, 16, btnHeight);
        headerTip(mousePos, helpBounds, "Take the HUD tour");

        // Side Panel Button [≡]
        g.setColor(BTN_IDLE_BG);
        g.fillRoundRect(panelX, headerY, 16, btnHeight, 3, 3);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(panelX, headerY, 15, btnHeight - 1, 3, 3);
        g.setColor(PANEL_ICON);
        HudGlyphs.panel(g, panelX + 4, headerY + 4, 8);
        panelBounds = new Rectangle(overlayBounds.x + panelX, overlayBounds.y + headerY, 16, btnHeight);
        headerTip(mousePos, panelBounds, "Open the Combat panel");

        } // end !minimal icon block

        // Button C: Segment / scope button - fills the header between the mode pill and the icons
        // (or the right edge in Minimal mode).
        int segX = modeX + modeW + 3;
        int segW = (minimal ? width - PADDING : graphX - 4) - segX;
        // Match the mode pill: an open dropdown lights the pill so its (dark) active text stays
        // readable, instead of dark-on-dark.
        g.setColor(segmentDropdownOpen ? CombatMeterColors.BTN_BG_ACTIVE : BTN_IDLE_BG);
        g.fillRoundRect(segX, headerY, segW, btnHeight, 4, 4);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(segX, headerY, segW - 1, btnHeight - 1, 4, 4);

        g.setColor(segmentDropdownOpen ? CombatMeterColors.BTN_TEXT_ACTIVE : Color.WHITE);
        String segText = currentEncounter.getTargetNameWithLevel() + " " + formatDuration(currentEncounter.getDurationSeconds());
        g.drawString(truncateString(segText, segW - 16, g), segX + 3, headerY + btnHeight - 3);
        g.setColor(MUTED_TXT);
        HudGlyphs.downCaret(g, segX + segW - 8, headerY + 6, 3, 4);
        segmentBounds = new Rectangle(overlayBounds.x + segX, overlayBounds.y + headerY, segW, btnHeight);
        if (!segmentDropdownOpen)
        {
            headerTip(mousePos, segmentBounds, "Scope: " + currentEncounter.getTargetNameWithLevel()
                + "  —  click to change   ·   right-click meter for source breakdown");
        }
    }


    private void renderModeDropdown(Graphics2D g, Rectangle bounds, Point mousePos)
    {
        Map<MetricMode, Rectangle> hb = new HashMap<>();
        int dropX = PADDING;
        int dropW = 120;
        int rowH = 18;
        MetricMode[] modes = MetricMode.values();
        int dropH = (modes.length * rowH) + 6;
        int dropY = popupY(PADDING + HEADER_HEIGHT + 2, dropH);

        // Background & Border
        g.setColor(CombatMeterColors.POPUP_BG);
        g.fillRoundRect(dropX, dropY, dropW, dropH, 6, 6);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(dropX, dropY, dropW - 1, dropH - 1, 6, 6);
        g.setColor(DROPDOWN_INNER_HL);
        g.drawRoundRect(dropX + 1, dropY + 1, dropW - 3, dropH - 3, 5, 5);

        int curY = dropY + 3;
        g.setFont(FontManager.getRunescapeSmallFont());

        for (MetricMode mode : modes)
        {
            Rectangle rowRect = new Rectangle(dropX + 2, curY, dropW - 4, rowH);
            Rectangle screenRect = new Rectangle(bounds.x + rowRect.x, bounds.y + rowRect.y, rowRect.width, rowRect.height);
            hb.put(mode, screenRect);

            boolean isHover = mousePos != null && screenRect.contains(mousePos);
            boolean isActive = mode == currentMode;

            if (isHover || isActive)
            {
                g.setColor(isActive ? CombatMeterColors.BTN_BG_ACTIVE : CombatMeterColors.BTN_BG_HOVER);
                g.fillRoundRect(rowRect.x, rowRect.y, rowRect.width, rowRect.height, 3, 3);
            }

            Color textColor = isActive ? CombatMeterColors.TEXT_ACCENT_GOLD : mode.getPrimaryColor();
            g.setColor(textColor);
            g.drawString(mode.getTitle(), rowRect.x + 6, curY + 12);

            if (isActive)
            {
                g.setColor(CombatMeterColors.TEXT_ACCENT_GOLD);
                HudGlyphs.check(g, rowRect.x + dropW - 14, curY + 2, 8);
            }

            curY += rowH;
        }
        modeDropdownHitboxes = hb;
    }

    private void renderFloatingInspectionCard(
        Graphics2D g, int width, int totalHeight,
        EntityCombatStats stats, int rank, EncounterSegment encounter)
    {
        List<StyleDamageEntry> styles = stats.getStyleBreakdown();
        int styleRows = Math.max(1, Math.min(5, styles.size()));

        int cardW = Math.max(width, 210);
        int cardH = 108 + 13 * styleRows;
        int cardX = 0;
        // Natural spot is just below the HUD; popupY() flips it above when the HUD sits low.
        int cardY = popupY(totalHeight + 3, cardH);

        // Background & Borders
        g.setColor(new Color(15, 15, 20, 245));
        g.fillRoundRect(cardX, cardY, cardW, cardH, 5, 5);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(cardX, cardY, cardW - 1, cardH - 1, 5, 5);

        int lineY = cardY + 14;
        g.setFont(FontManager.getRunescapeBoldFont());
        g.setColor(CombatMeterColors.TEXT_ACCENT_GOLD);
        g.drawString(stats.getName() + " (#" + rank + ")", cardX + 6, lineY);

        g.setFont(FontManager.getRunescapeSmallFont());
        lineY += 14;
        g.setColor(Color.WHITE);
        g.drawString("Damage: " + formatAmount(stats.getTotalDamage()) + " (" + String.format("%.1f DPS", stats.getDps()) + ")", cardX + 6, lineY);

        lineY += 13;
        g.setColor(new Color(239, 83, 80));
        g.drawString("Damage Taken: " + formatAmount(stats.getDamageTaken()) + " HP", cardX + 6, lineY);

        lineY += 13;
        g.setColor(new Color(129, 199, 132));
        String healStr = "Healing: " + formatAmount(stats.getHpHealed()) + " HP";
        if (stats.getHpOverhealed() > 0)
        {
            healStr += " (+" + formatAmount(stats.getHpOverhealed()) + " over)";
        }
        g.drawString(healStr, cardX + 6, lineY);

        lineY += 13;
        g.setColor(PANEL_ICON);
        String accStr = String.format("Accuracy: %.1f%%", stats.getHitAccuracy());
        if (stats.getMaxHitDealt() != null)
        {
            accStr += " | Max Hit: " + stats.getMaxHitDealt().getAmount();
        }
        g.drawString(accStr, cardX + 6, lineY);

        lineY += 8;
        g.setColor(DIVIDER);
        g.drawLine(cardX + 6, lineY, cardX + cardW - 6, lineY);

        lineY += 13;
        if (!styles.isEmpty())
        {
            long styleTotal = Math.max(1, stats.getTotalDamage());
            for (int s = 0; s < styleRows; s++)
            {
                StyleDamageEntry entry = styles.get(s);
                g.setColor(entry.getColor());
                double share = (entry.getDamage() * 100.0) / styleTotal;
                g.drawString("- " + entry.getStyleName() + ": " + formatAmount(entry.getDamage())
                    + " (" + String.format("%.0f%%", share) + ")", cardX + 6, lineY);
                lineY += 13;
            }
            lineY -= 13; // the trailing "lineY += 13" below advances to the next block
        }
        else
        {
            g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
            g.drawString("- No combat hits recorded", cardX + 6, lineY);
        }

        lineY += 13;
        List<ConsumableUsageEntry> consumables = stats.getConsumablesUsed();
        if (!consumables.isEmpty())
        {
            ConsumableUsageEntry topCons = consumables.get(0);
            g.setColor(new Color(255, 215, 0));
            g.drawString("- " + topCons.getQuantity() + "x " + topCons.getItemName() + " (" + formatAmount(topCons.getTotalCost()) + " GP)", cardX + 6, lineY);
        }
        else
        {
            g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
            g.drawString("- Consumables: 0 used", cardX + 6, lineY);
        }

        lineY += 14;
        g.setColor(new Color(180, 180, 190));
        g.drawString("Click: Combat panel    Right-click: source breakdown", cardX + 6, lineY);
    }



    private long getEntityMetricValue(EntityCombatStats stats, MetricMode mode)
    {
        switch (mode)
        {
            case DAMAGE_DONE: return stats.getTotalDamage();
            case DAMAGE_TAKEN: return stats.getDamageTaken();
            case HEALING_DONE: return stats.getHpHealed();
            case CONSUMABLES: return stats.getPotionsDrunkCount() + stats.getFoodEatenCount();
            default: return stats.getTotalDamage();
        }
    }

    private static Color darken(Color c, float f)
    {
        return new Color(Math.round(c.getRed() * f), Math.round(c.getGreen() * f), Math.round(c.getBlue() * f), c.getAlpha());
    }

    private void renderProgressBar(
        Graphics2D g, int x, int y, int w, int h, int rank,
        EntityCombatStats stats, long maxVal, long totalVal, boolean isHovered)
    {
        int cr = Math.max(0, Math.min(8, config.combatBarCorner()));

        g.setColor(CombatMeterColors.BAR_TRACK_BG);
        g.fillRoundRect(x, y, w, h, cr, cr);

        long val = getEntityMetricValue(stats, currentMode);
        float fillRatio = maxVal > 0 ? (float) val / maxVal : 0f;
        int fillWidth = Math.max(2, (int) (w * Math.min(1.0f, fillRatio)));

        OsrsCopilotConfig.BarStyleOption styleOpt = config.combatBarStyle();
        OsrsCopilotConfig.PaletteAccentOption paletteOpt = config.combatPaletteAccent();

        Color primary;
        Color accent;

        if (styleOpt == OsrsCopilotConfig.BarStyleOption.CLASS_STYLE || paletteOpt == OsrsCopilotConfig.PaletteAccentOption.DYNAMIC_STYLE)
        {
            primary = currentMode == MetricMode.DAMAGE_DONE
                ? stats.getDominantStyle().getPrimaryColor()
                : currentMode.getPrimaryColor();
            accent = currentMode == MetricMode.DAMAGE_DONE
                ? stats.getDominantStyle().getGradientColor()
                : currentMode.getGradientColor();
        }
        else if (paletteOpt == OsrsCopilotConfig.PaletteAccentOption.CUSTOM)
        {
            primary = config.combatBarCustomColor();
            if (primary == null)
            {
                primary = new Color(255, 183, 77);
            }
            accent = darken(primary, 0.72f);
        }
        else
        {
            primary = CombatMeterColors.getPalettePrimary(paletteOpt);
            accent = CombatMeterColors.getPaletteGradient(paletteOpt);
            if (primary == null)
            {
                primary = currentMode == MetricMode.DAMAGE_DONE
                    ? stats.getDominantStyle().getPrimaryColor()
                    : currentMode.getPrimaryColor();
                accent = currentMode == MetricMode.DAMAGE_DONE
                    ? stats.getDominantStyle().getGradientColor()
                    : currentMode.getGradientColor();
            }
        }

        if (styleOpt == OsrsCopilotConfig.BarStyleOption.SOLID)
        {
            g.setColor(primary);
            g.fillRoundRect(x, y, fillWidth, h, cr, cr);
        }
        else
        {
            GradientPaint gp = new GradientPaint(x, y, primary, x + fillWidth, y, accent);
            g.setPaint(gp);
            g.fillRoundRect(x, y, fillWidth, h, cr, cr);
        }

        applyBarTexture(g, x, y, fillWidth, h);

        if (isHovered)
        {
            g.setColor(BAR_HOVER_SHEEN);
            g.fillRoundRect(x, y, w, h, cr, cr);
        }

        g.setColor(BAR_OUTLINE);
        g.drawRoundRect(x, y, w - 1, h - 1, cr, cr);

        g.setFont(barFont());
        FontMetrics fm = g.getFontMetrics();
        boolean shadow = config.combatBarTextShadow();

        // Right text is right-aligned to the bar edge; the left "rank. name" is clipped so it
        // can never overrun into it on a narrow bar.
        String rightText = formatRightText(stats, val, totalVal);
        int rightTextWidth = fm.stringWidth(rightText);
        drawBarLabel(g, rightText, x + w - rightTextWidth - 4, y + h - 4, Color.WHITE, shadow);

        // Optional style dot at the bar's left edge.
        int leftX = x + 4;
        if (config.combatBarShowStyleIcon())
        {
            int d = Math.min(7, h - 6);
            g.setColor(stats.getDominantStyle().getPrimaryColor());
            g.fillOval(x + 3, y + (h - d) / 2, d, d);
            leftX = x + 6 + d;
        }

        String leftText = (config.combatBarShowRank() ? rank + ". " : "") + stats.getName();
        int leftMaxWidth = Math.max(24, (x + w) - leftX - rightTextWidth - 10);
        drawBarLabel(g, truncateString(leftText, leftMaxWidth, g), leftX, y + h - 4,
            CombatMeterColors.TEXT_PRIMARY, shadow);
    }

    private static final Color BAR_TEXT_SHADOW = new Color(0, 0, 0, 160);

    private void drawBarLabel(Graphics2D g, String text, int x, int y, Color color, boolean shadow)
    {
        if (shadow)
        {
            g.setColor(BAR_TEXT_SHADOW);
            g.drawString(text, x + 1, y + 1);
        }
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private java.awt.Font barFont()
    {
        switch (config.combatBarFont())
        {
            case REGULAR: return FontManager.getRunescapeFont();
            case BOLD: return FontManager.getRunescapeBoldFont();
            default: return FontManager.getRunescapeSmallFont();
        }
    }

    /** Draw the configured texture overlay clipped to the filled portion of a bar. */
    private void applyBarTexture(Graphics2D g, int x, int y, int fillWidth, int h)
    {
        OsrsCopilotConfig.BarTextureOption tex = config.combatBarTexture();
        if (tex == OsrsCopilotConfig.BarTextureOption.NONE || fillWidth <= 2)
        {
            return;
        }
        int cr = Math.max(0, Math.min(8, config.combatBarCorner()));
        java.awt.Shape oldClip = g.getClip();
        g.setClip(new java.awt.geom.RoundRectangle2D.Float(x, y, fillWidth, h, cr, cr));
        if (tex == OsrsCopilotConfig.BarTextureOption.GLOSS)
        {
            g.setPaint(new GradientPaint(x, y, new Color(255, 255, 255, 65),
                x, y + h / 2f, new Color(255, 255, 255, 0)));
            g.fillRect(x, y, fillWidth, Math.max(1, h / 2));
        }
        else // STRIPED
        {
            g.setColor(new Color(0, 0, 0, 38));
            g.setStroke(STROKE_1);
            for (int i = -h; i < fillWidth; i += 6)
            {
                g.drawLine(x + i, y + h, x + i + h, y);
            }
        }
        g.setClip(oldClip);
    }

    private String formatRightText(EntityCombatStats stats, long val, long totalVal)
    {
        if (currentMode == MetricMode.CONSUMABLES)
        {
            return String.format("%d pots / %d food", stats.getPotionsDrunkCount(), stats.getFoodEatenCount());
        }

        double sharePct = totalVal > 0 ? ((double) val / totalVal) * 100.0 : 0.0;
        String pct = String.format("%.0f%%", sharePct);
        String amount = formatAmount(val);
        if (currentMode == MetricMode.HEALING_DONE && stats.getHpOverhealed() > 0)
        {
            amount += " (+" + formatAmount(stats.getHpOverhealed()) + "o)";
        }
        String rate;
        switch (currentMode)
        {
            case DAMAGE_TAKEN: rate = String.format("%.1f DTPS", stats.getDtps()); break;
            case HEALING_DONE: rate = String.format("%.1f HPS", stats.getHps()); break;
            default:           rate = String.format("%.1f DPS", stats.getDps()); break;
        }

        switch (config.combatBarValueFormat())
        {
            case AMOUNT_PERCENT: return amount + " (" + pct + ")";
            case AMOUNT:         return amount;
            case PERCENT:        return pct;
            case RATE:           return rate;
            default:             return rate + " | " + amount + " (" + pct + ")";
        }
    }

    private void renderBossProgressLine(Graphics2D g, int x, int y, int w,
        CombatEncounterManager.LiveBossProgress bp, int elapsedSec)
    {
        g.setColor(CombatMeterColors.FOOTER_BG);
        g.fillRoundRect(x, y, w, 13, 3, 3);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(x, y, w - 1, 12, 3, 3);

        g.setFont(FontManager.getRunescapeSmallFont());
        int baseline = y + 10;
        int tx = x + 4;

        String nm = truncateString(bp.getName() != null ? bp.getName() : "Target", Math.max(40, w / 3), g);
        g.setColor(MUTED_TXT);
        g.drawString(nm, tx, baseline);
        tx += g.getFontMetrics().stringWidth(nm) + 6;

        String pc = Math.round(bp.getHpFraction() * 100) + "%";
        g.setColor(CombatMeterColors.TEXT_ACCENT_GOLD);
        g.drawString(pc, tx, baseline);
        tx += g.getFontMetrics().stringWidth(pc) + 8;

        if (bp.getEtaSeconds() >= 0)
        {
            String eta = "~" + formatDuration(bp.getEtaSeconds());
            g.setColor(FOOTER_HEAL);
            g.drawString(eta, tx, baseline);
            tx += g.getFontMetrics().stringWidth(eta) + 8;
            if (bp.getProjectedKph() > 0)
            {
                g.setColor(PANEL_ICON);
                g.drawString("~" + Math.round(bp.getProjectedKph()) + "/hr", tx, baseline);
            }
        }
        else
        {
            g.setColor(MUTED_TXT);
            g.drawString(formatDuration(elapsedSec), tx, baseline);
        }
    }

    private void renderMiniFooter(Graphics2D g, int x, int y, int w, int h, EncounterSegment encounter)
    {
        g.setColor(CombatMeterColors.FOOTER_BG);
        g.fillRoundRect(x, y, w, h, 3, 3);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(x, y, w - 1, h - 1, 3, 3);

        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();
        int baseY = y + h - 4;
        int gap = 6;
        int leftEdge = x + 3;
        int rightEdge = x + w - 3;

        // Right group first: Food hard against the right edge, Pots measured to its left. Fixed
        // x-offsets used to be used here, which collided once either count hit two digits
        // ("Pots: 15Food: 40") and pushed Food past the panel edge.
        String pots = "Pots: " + encounter.getLocalPlayerStats().getPotionsDrunkCount();
        String food = "Food: " + encounter.getLocalPlayerStats().getFoodEatenCount();
        int foodX = rightEdge - fm.stringWidth(food);
        int potsX = foodX - gap - fm.stringWidth(pots);

        // Left group: Tkn, then Heal. Drop the "(+No)" overheal note, then ellipsize, if it would
        // run into the right group.
        String taken = "Tkn: " + formatAmount(encounter.getLocalPlayerStats().getDamageTaken());
        int healX = leftEdge + fm.stringWidth(taken) + gap;
        int healBudget = potsX - gap - healX;

        String healed = "Heal: " + formatAmount(encounter.getLocalPlayerStats().getHpHealed());
        String healedFull = healed;
        if (encounter.getLocalPlayerStats().getHpOverhealed() > 0)
        {
            healedFull = healed + " (+" + formatAmount(encounter.getLocalPlayerStats().getHpOverhealed()) + "o)";
        }
        if (fm.stringWidth(healedFull) <= healBudget)
        {
            healed = healedFull;
        }
        else if (fm.stringWidth(healed) > healBudget)
        {
            healed = truncateString(healed, Math.max(0, healBudget), g);
        }

        g.setColor(FOOTER_TKN);
        g.drawString(taken, leftEdge, baseY);
        if (healBudget > 0)
        {
            g.setColor(FOOTER_HEAL);
            g.drawString(healed, healX, baseY);
        }
        g.setColor(FOOTER_POTS);
        g.drawString(pots, potsX, baseY);
        g.setColor(FOOTER_FOOD);
        g.drawString(food, foodX, baseY);
    }

    public void cycleSegment()
    {
        List<EncounterSegment> segments = encounterManager.getAllSegmentsForDropdown();
        if (segments.isEmpty()) return;
        EncounterSegment current = encounterManager.getSelectedOrCurrentEncounter();
        int idx = segments.indexOf(current);
        int next = (idx + 1) % segments.size();
        encounterManager.selectEncounter(segments.get(next));
    }

    private String formatAmount(long amt)
    {
        return com.osrscopilot.combat.CombatFormat.amount(amt);
    }

    private String formatDuration(int totalSeconds)
    {
        return com.osrscopilot.combat.CombatFormat.duration(totalSeconds);
    }

    private String truncateString(String text, int maxWidth, Graphics2D g)
    {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) return text;
        while (text.length() > 3 && fm.stringWidth(text + "...") > maxWidth)
        {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    @Override
    public MouseEvent mousePressed(MouseEvent mouseEvent)
    {
        if (mouseEvent == null || !config.showCombatOverlay())
        {
            return mouseEvent;
        }

        // Right-click on the meter opens the "Damage Sources" window (on a bar -> that participant).
        // A right-click anywhere else passes straight through.
        if (mouseEvent.getButton() == MouseEvent.BUTTON3)
        {
            Point rp = mouseEvent.getPoint();
            EntityCombatStats picked = null;
            for (BarHitbox bh : barHitboxes)
            {
                if (bh.bounds != null && bh.bounds.contains(rp))
                {
                    picked = bh.stats;
                    break;
                }
            }
            boolean onOverlay = picked != null
                || (overlayBounds != null && overlayBounds.width > 0 && overlayBounds.contains(rp))
                || contains(modeBounds, rp) || contains(segmentBounds, rp) || contains(panelBounds, rp)
                || contains(graphBounds, rp) || contains(settingsBounds, rp);
            if (onOverlay && onOpenSourceDetails != null && encounterManager != null)
            {
                onOpenSourceDetails.accept(encounterManager.getSelectedOrCurrentEncounter(), picked);
                mouseEvent.consume();
            }
            return mouseEvent;
        }

        // Middle click passes straight through so it can't cycle segments or reset the session.
        if (mouseEvent.getButton() == MouseEvent.BUTTON2)
        {
            return mouseEvent;
        }

        // Alt key held -> let RuneLite's OverlayRenderer handle dragging / resizing freely!
        if (client != null && client.isKeyPressed(net.runelite.api.KeyCode.KC_ALT))
        {
            return mouseEvent;
        }

        Point p = mouseEvent.getPoint();

        // 0a. Settings-card scrollbar thumb - start a drag.
        if (settingsOpen && settingsCard != null && settingsCard.isOnScrollThumb(p))
        {
            isScrollDragging = true;
            scrollDragStartY = p.y;
            scrollDragStartScroll = settingsCard.getScrollY();
            mouseEvent.consume();
            return mouseEvent;
        }

        // 0. Resize grip - start a drag that rewrites width / bar-height config.
        if (resizeGripBounds != null && resizeGripBounds.contains(p))
        {
            isResizing = true;
            dragStartPoint = p;
            dragStartWidth = config.combatOverlayWidth();
            dragStartBarHeight = config.combatBarHeight();
            lastAppliedWidth = dragStartWidth;
            lastAppliedBarHeight = dragStartBarHeight;
            mouseEvent.consume();
            return mouseEvent;
        }

        // 1. Settings Card Clicks. While the card is open, clicks on the card OR anywhere on the
        // meter itself (bars, header, dragging it) must NOT close it - only a click clean outside
        // both surfaces (or the cog / the card's X) closes it.
        if (settingsOpen && settingsCard != null)
        {
            if (settingsCard.handleClick(p, this))
            {
                mouseEvent.consume();
                return mouseEvent;
            }

            boolean onCard = settingsCardBounds != null && settingsCardBounds.contains(p);
            boolean onMeter = overlayBounds != null && overlayBounds.contains(p);
            if (!onCard && !onMeter)
            {
                settingsOpen = false;
                mouseEvent.consume();
                return mouseEvent;
            }
            // click landed on the meter with the card open: fall through so bar/drag logic runs,
            // but keep the card open.
        }

        // 3. Settings Cog Button [⚙]
        if (settingsBounds != null && settingsBounds.contains(p))
        {
            settingsOpen = !settingsOpen;
            modeDropdownOpen = false;
            segmentDropdownOpen = false;
            mouseEvent.consume();
            return mouseEvent;
        }

        // 4. Graph Button [📈]
        if (graphBounds != null && graphBounds.contains(p))
        {
            toggleGraph();
            mouseEvent.consume();
            return mouseEvent;
        }

        // 4b. "?" Tour Button
        if (helpBounds != null && helpBounds.contains(p))
        {
            if (onStartTour != null)
            {
                onStartTour.run();
            }
            mouseEvent.consume();
            return mouseEvent;
        }

        // 6. Mode Dropdown Interactivity
        if (modeDropdownOpen)
        {
            for (Map.Entry<MetricMode, Rectangle> entry : modeDropdownHitboxes.entrySet())
            {
                if (entry.getValue().contains(p))
                {
                    currentMode = entry.getKey();
                    modeDropdownOpen = false;
                    mouseEvent.consume();
                    return mouseEvent;
                }
            }
            modeDropdownOpen = false;
            if (modeBounds != null && modeBounds.contains(p))
            {
                mouseEvent.consume();
                return mouseEvent;
            }
        }

        if (modeBounds != null && modeBounds.contains(p))
        {
            modeDropdownOpen = !modeDropdownOpen;
            segmentDropdownOpen = false;
            mouseEvent.consume();
            return mouseEvent;
        }

        // 6b. Segment Dropdown Interactivity (classic scope picker)
        if (segmentDropdownOpen)
        {
            for (Map.Entry<Integer, Rectangle> entry : segmentDropdownHitboxes.entrySet())
            {
                if (entry.getValue().contains(p))
                {
                    int idx = entry.getKey();
                    java.util.List<EncounterSegment> segs = encounterManager.getAllSegmentsForDropdown();
                    if (idx == SEG_HITBOX_AUTOFOLLOW)
                    {
                        encounterManager.setAutoFollow(!encounterManager.isAutoFollow());
                    }
                    else if (idx <= SEG_HITBOX_EXPAND_BASE)
                    {
                        // Merged "x N" row expand/collapse caret - keeps the dropdown open.
                        int rowIdx = SEG_HITBOX_EXPAND_BASE - idx;
                        if (rowIdx >= 0 && rowIdx < segs.size())
                        {
                            java.util.UUID id = segs.get(rowIdx).getSegmentId();
                            expandedSegId = id.equals(expandedSegId) ? null : id;
                        }
                    }
                    else
                    {
                        if (idx >= 0 && idx < segs.size())
                        {
                            encounterManager.selectEncounter(segs.get(idx));
                        }
                        segmentDropdownOpen = false;
                    }
                    mouseEvent.consume();
                    return mouseEvent;
                }
            }
            segmentDropdownOpen = false;
            if (segmentBounds != null && segmentBounds.contains(p))
            {
                mouseEvent.consume();
                return mouseEvent;
            }
        }

        // 7. Segment Button -> open the scope dropdown
        if (segmentBounds != null && segmentBounds.contains(p))
        {
            segmentDropdownOpen = !segmentDropdownOpen;
            modeDropdownOpen = false;
            mouseEvent.consume();
            return mouseEvent;
        }

        // 9. Side Panel Button
        if (panelBounds != null && panelBounds.contains(p))
        {
            if (onOpenCombatTab != null)
            {
                onOpenCombatTab.run();
            }
            mouseEvent.consume();
            return mouseEvent;
        }

        // 10. Bar Drilldown
        for (BarHitbox bh : barHitboxes)
        {
            if (bh.bounds != null && bh.bounds.contains(p))
            {
                if (onOpenCombatTab != null)
                {
                    onOpenCombatTab.run();
                }
                mouseEvent.consume();
                return mouseEvent;
            }
        }

        return mouseEvent;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent mouseEvent)
    {
        if (isScrollDragging)
        {
            isScrollDragging = false;
            mouseEvent.consume();
            return mouseEvent;
        }
        if (isResizing)
        {
            isResizing = false;
            dragStartPoint = null;
            mouseEvent.consume();
            return mouseEvent;
        }
        return mouseEvent;
    }

    @Override
    public java.awt.event.MouseWheelEvent mouseWheelMoved(java.awt.event.MouseWheelEvent e)
    {
        if (settingsOpen && settingsCard != null && settingsCardBounds != null
            && settingsCardBounds.width > 0 && settingsCardBounds.contains(e.getPoint()))
        {
            settingsCard.scrollBy(e.getWheelRotation() * 22);
            e.consume();
        }
        return e;
    }

    @Override
    public MouseEvent mouseClicked(MouseEvent mouseEvent)
    {
        return mouseEvent;
    }

    @Override
    public MouseEvent mouseEntered(MouseEvent mouseEvent)
    {
        return mouseEvent;
    }

    @Override
    public MouseEvent mouseExited(MouseEvent mouseEvent)
    {
        return mouseEvent;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent mouseEvent)
    {
        if (isScrollDragging && settingsCard != null)
        {
            int delta = (int) Math.round((mouseEvent.getY() - scrollDragStartY) * settingsCard.thumbDragRatio());
            settingsCard.setScrollY(scrollDragStartScroll + delta);
            mouseEvent.consume();
            return mouseEvent;
        }
        if (isResizing && dragStartPoint != null && configManager != null)
        {
            int dx = mouseEvent.getX() - dragStartPoint.x;
            int dy = mouseEvent.getY() - dragStartPoint.y;

            int barCount = Math.max(1, config.maxCombatBars());
            int newWidth = clamp(dragStartWidth + dx, 160, 420);
            int newBarHeight = clamp(dragStartBarHeight + Math.round(dy / (float) barCount), 10, 44);

            if (newWidth != lastAppliedWidth)
            {
                configManager.setConfiguration("osrscopilot", "combatOverlayWidth", newWidth);
                lastAppliedWidth = newWidth;
            }
            if (newBarHeight != lastAppliedBarHeight)
            {
                configManager.setConfiguration("osrscopilot", "combatBarHeight", newBarHeight);
                lastAppliedBarHeight = newBarHeight;
            }
            mouseEvent.consume();
            return mouseEvent;
        }
        return mouseEvent;
    }

    private static int clamp(int v, int lo, int hi)
    {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @Override
    public MouseEvent mouseMoved(MouseEvent mouseEvent)
    {
        return mouseEvent;
    }

    private static boolean contains(Rectangle r, Point p)
    {
        return r != null && r.width > 0 && r.contains(p);
    }

    private static class BarHitbox
    {
        final Rectangle bounds;
        final EntityCombatStats stats;

        BarHitbox(Rectangle bounds, EntityCombatStats stats)
        {
            this.bounds = bounds;
            this.stats = stats;
        }
    }
}

