package com.osrscopilot.map;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.overlay.CombatGraphOverlay;
import com.osrscopilot.combat.overlay.CombatMeterOverlay;
import com.osrscopilot.ui.OsrsCopilotPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Minimap Combat Meter shortcut button.
 * Draws a small OSRS-styled circular button with pixel-crafted crossed swords on the minimap
 * rim, next to the native Wiki banner. Left-click toggles the in-game Combat Meter HUD overlay;
 * right-click opens the fight graph, the combat side panel, or toggles the HUD. Hover shows a
 * tooltip with the current [Active] / [Hidden] state.
 */
@Singleton
public class MinimapCombatButtonOverlay extends Overlay implements MouseListener
{
    public static final int BUTTON_SIZE = 20;
    public static final int BUTTON_SPACING = 4;
    public static final String TOOLTIP_TEXT = "Toggle Combat Meter";

    private final Client client;
    private final OsrsCopilotConfig config;
    private final ConfigManager configManager;
    private final TooltipManager tooltipManager;
    private final CombatMeterOverlay combatMeterOverlay;
    private final CombatGraphOverlay combatGraphOverlay;
    private final CombatEncounterManager encounterManager;
    private final ClientToolbar clientToolbar;

    private NavigationButton navButton;
    private OsrsCopilotPanel panel;
    private Runnable onToggleHud;
    private long lastClickTime = 0;
    private java.awt.Point mousePressPoint = null;
    private volatile Rectangle lastCalculatedBounds = null;

    private static BufferedImage cachedIcon = null;

    @Inject
    public MinimapCombatButtonOverlay(
        Client client,
        OsrsCopilotConfig config,
        ConfigManager configManager,
        TooltipManager tooltipManager,
        CombatMeterOverlay combatMeterOverlay,
        CombatGraphOverlay combatGraphOverlay,
        CombatEncounterManager encounterManager,
        ClientToolbar clientToolbar)
    {
        this.client = client;
        this.config = config;
        this.configManager = configManager;
        this.tooltipManager = tooltipManager;
        this.combatMeterOverlay = combatMeterOverlay;
        this.combatGraphOverlay = combatGraphOverlay;
        this.encounterManager = encounterManager;
        this.clientToolbar = clientToolbar;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);

        // Left-click toggles the HUD; right-click offers the graph / side panel / reset.
        addMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY, "Fight graph", "Combat Meter",
            e -> toggleFightGraph());
        addMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY, "Combat panel", "Combat Meter",
            e -> openCombatSidebarTab());
        addMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY, "Toggle HUD", "Combat Meter",
            e -> toggleCombatMeter());
    }

    public void setNavButton(NavigationButton navButton)
    {
        this.navButton = navButton;
    }

    public void setOnToggleHud(Runnable onToggleHud)
    {
        this.onToggleHud = onToggleHud;
    }

    public void setPanel(OsrsCopilotPanel panel)
    {
        this.panel = panel;
    }

    public Rectangle getMinimapBounds()
    {
        if (client == null)
        {
            return null;
        }

        Widget minimap = client.getWidget(InterfaceID.Orbs.UNIVERSE);
        if (isValidWidget(minimap))
        {
            return minimap.getBounds();
        }

        minimap = client.getWidget(InterfaceID.Toplevel.MINIMAP);
        if (isValidWidget(minimap))
        {
            return minimap.getBounds();
        }

        minimap = client.getWidget(InterfaceID.ToplevelOsrsStretch.MINIMAP);
        if (isValidWidget(minimap))
        {
            return minimap.getBounds();
        }

        minimap = client.getWidget(InterfaceID.ToplevelPreEoc.MINIMAP);
        if (isValidWidget(minimap))
        {
            return minimap.getBounds();
        }

        minimap = client.getWidget(InterfaceID.Toplevel.MAPCONTAINER);
        if (isValidWidget(minimap))
        {
            return minimap.getBounds();
        }

        minimap = client.getWidget(InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER);
        if (isValidWidget(minimap))
        {
            return minimap.getBounds();
        }

        minimap = client.getWidget(InterfaceID.ToplevelPreEoc.MAP_CONTAINER);
        if (isValidWidget(minimap))
        {
            return minimap.getBounds();
        }

        return null;
    }

    public Rectangle getWikiBannerBounds()
    {
        if (client == null)
        {
            return null;
        }

        Widget wikiBanner = client.getWidget(InterfaceID.Orbs.WIKI_ICON);
        if (isValidWidget(wikiBanner))
        {
            return wikiBanner.getBounds();
        }

        wikiBanner = client.getWidget(InterfaceID.Orbs.WIKI); // the banner's parent
        if (isValidWidget(wikiBanner))
        {
            return wikiBanner.getBounds();
        }

        return null;
    }

    private static boolean isValidWidget(Widget widget)
    {
        if (widget == null || widget.isHidden())
        {
            return false;
        }
        Rectangle bounds = widget.getBounds();
        return bounds != null && bounds.width > 0 && bounds.height > 0;
    }

    public Rectangle getButtonBounds()
    {
        Rectangle wikiBounds = getWikiBannerBounds();
        Rectangle minimapBounds = getMinimapBounds();

        // Note: no space is reserved for a "guide button" - there is no such overlay in the
        // plugin, and reserving a slot for one only produced a phantom gap.
        if (wikiBounds != null && wikiBounds.width > 0 && wikiBounds.height > 0)
        {
            int x = wikiBounds.x - BUTTON_SIZE - BUTTON_SPACING;
            int y = wikiBounds.y + (wikiBounds.height - BUTTON_SIZE) / 2;
            return new Rectangle(x, y, BUTTON_SIZE, BUTTON_SIZE);
        }

        if (minimapBounds != null && minimapBounds.width > 0 && minimapBounds.height > 0)
        {
            int x = minimapBounds.x + minimapBounds.width - BUTTON_SIZE - 46;
            int y = minimapBounds.y + minimapBounds.height - BUTTON_SIZE - 18;
            return new Rectangle(x, y, BUTTON_SIZE, BUTTON_SIZE);
        }

        return null;
    }

    public boolean isButtonHovered(Point mousePos)
    {
        if (mousePos == null)
        {
            return false;
        }
        Rectangle bounds = this.lastCalculatedBounds;
        return bounds != null && bounds.contains(mousePos.getX(), mousePos.getY());
    }

    public void toggleCombatMeter()
    {
        long now = System.currentTimeMillis();
        if (now - lastClickTime < 250)
        {
            return;
        }
        lastClickTime = now;

        if (onToggleHud != null)
        {
            onToggleHud.run();
        }
        else if (configManager != null && config != null)
        {
            // Fallback for standalone construction (tests): flip the config directly.
            configManager.setConfiguration("osrscopilot", "showCombatOverlay", !config.showCombatOverlay());
        }
    }

    public void toggleFightGraph()
    {
        if (combatGraphOverlay != null)
        {
            combatGraphOverlay.toggle();
        }
    }

    public void openCombatSidebarTab()
    {
        SwingUtilities.invokeLater(() -> {
            if (clientToolbar != null && navButton != null)
            {
                clientToolbar.openPanel(navButton);
            }
            if (panel != null)
            {
                panel.showTab(OsrsCopilotPanel.VIEW_COMBAT);
            }
        });
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (client == null || client.getGameState() != GameState.LOGGED_IN)
        {
            this.lastCalculatedBounds = null;
            return null;
        }

        if (config != null && !config.showMinimapCombatButton())
        {
            this.lastCalculatedBounds = null;
            return null;
        }

        Rectangle bounds = getButtonBounds();
        this.lastCalculatedBounds = bounds;
        if (bounds == null || g == null)
        {
            return null;
        }

        Point mousePoint = client.getMouseCanvasPosition();
        boolean hovered = isButtonHovered(mousePoint);

        if (cachedIcon == null)
        {
            cachedIcon = createCombatButtonIcon(bounds.width);
        }

        boolean isActive = config != null && config.showCombatOverlay();

        if (hovered)
        {
            // Red/Gold combat glow highlight
            g.setColor(new Color(255, 87, 34, 110));
            g.fillOval(bounds.x - 2, bounds.y - 2, bounds.width + 4, bounds.height + 4);

            g.setColor(new Color(255, 193, 7, 240));
            g.setStroke(new BasicStroke(1.8f));
            g.drawOval(bounds.x - 2, bounds.y - 2, bounds.width + 4, bounds.height + 4);

            if (tooltipManager != null)
            {
                String status = isActive ? " [Active]" : " [Hidden]";
                tooltipManager.add(new Tooltip(TOOLTIP_TEXT + status));
            }
        }
        else
        {
            // Subtle dark stone backing
            g.setColor(new Color(20, 20, 24, 175));
            g.fillOval(bounds.x, bounds.y, bounds.width, bounds.height);

            if (isActive)
            {
                g.setColor(new Color(255, 193, 7, 140));
                g.setStroke(new BasicStroke(1.0f));
                g.drawOval(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        }

        if (cachedIcon != null)
        {
            g.drawImage(cachedIcon, bounds.x, bounds.y, bounds.width, bounds.height, null);
        }

        return null;
    }

    // =========================================================================
    // MouseListener implementation for Click Consumption & Walk Prevention
    // =========================================================================

    @Override
    public java.awt.event.MouseEvent mousePressed(java.awt.event.MouseEvent e)
    {
        Rectangle b = this.lastCalculatedBounds;
        if (b != null && b.contains(e.getPoint()))
        {
            if (e.getButton() == java.awt.event.MouseEvent.BUTTON1)
            {
                mousePressPoint = e.getPoint();
                e.consume(); // Prevents canvas click and character walking
                return e;
            }
        }
        mousePressPoint = null;
        return e;
    }

    @Override
    public java.awt.event.MouseEvent mouseReleased(java.awt.event.MouseEvent e)
    {
        Rectangle b = this.lastCalculatedBounds;
        if (b != null && mousePressPoint != null && e.getButton() == java.awt.event.MouseEvent.BUTTON1)
        {
            java.awt.Point p = e.getPoint();
            if (b.contains(p) || (b.contains(mousePressPoint) && mousePressPoint.distanceSq(p) <= 36))
            {
                toggleCombatMeter();
                e.consume(); // Consumes release event so game engine never executes walk
            }
            mousePressPoint = null;
            return e;
        }
        mousePressPoint = null;
        return e;
    }

    @Override
    public java.awt.event.MouseEvent mouseDragged(java.awt.event.MouseEvent e)
    {
        Rectangle b = this.lastCalculatedBounds;
        if (b != null && mousePressPoint != null && b.contains(mousePressPoint))
        {
            e.consume();
            return e;
        }
        return e;
    }

    @Override public java.awt.event.MouseEvent mouseClicked(java.awt.event.MouseEvent e) { return e; }
    @Override public java.awt.event.MouseEvent mouseEntered(java.awt.event.MouseEvent e) { return e; }
    @Override public java.awt.event.MouseEvent mouseExited(java.awt.event.MouseEvent e) { return e; }
    @Override public java.awt.event.MouseEvent mouseMoved(java.awt.event.MouseEvent e) { return e; }

    /**
     * Fallback generator for the pixel-crafted OSRS Crossed Swords Combat Icon.
     */
    public static BufferedImage createCombatButtonIcon(int size)
    {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) img.getGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 1. Dark circular backing shadow
        g.setColor(new Color(15, 12, 14, 240));
        g.fill(new Ellipse2D.Float(0.5f, 0.5f, size - 1.0f, size - 1.0f));

        // 2. Base Dark Steel / Ruby Shield Fill
        GradientPaint bgPaint = new GradientPaint(
            2.0f, 2.0f, new Color(42, 28, 30),
            size - 2.0f, size - 2.0f, new Color(20, 18, 22)
        );
        g.setPaint(bgPaint);
        g.fill(new Ellipse2D.Float(1.5f, 1.5f, size - 3.0f, size - 3.0f));

        // 3. Ornate Gold Bezel Rim
        GradientPaint goldRim = new GradientPaint(
            2.0f, 2.0f, new Color(245, 185, 45),
            size - 2.0f, size - 2.0f, new Color(130, 85, 15)
        );
        g.setPaint(goldRim);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Float(1.0f, 1.0f, size - 2.0f, size - 2.0f));

        // Specular glint arc on top-left rim
        g.setColor(new Color(255, 240, 180, 220));
        g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Float(1.5f, 1.5f, size - 3.0f, size - 3.0f, 105, 75, Arc2D.OPEN));

        // 4. Crossed Swords Icon (⚔)
        // Sword 1 (Top-Left to Bottom-Right)
        g.setColor(new Color(220, 225, 235)); // Steel blade
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(5, 5, 14, 14);

        // Sword 2 (Top-Right to Bottom-Left)
        g.drawLine(14, 5, 5, 14);

        // Crossguards (Gold / Bronze)
        g.setColor(new Color(255, 193, 7));
        g.setStroke(new BasicStroke(1.2f));
        // Crossguard for sword 1
        g.drawLine(12, 14, 14, 12);
        // Crossguard for sword 2
        g.drawLine(7, 14, 5, 12);

        // Pommels
        g.setColor(new Color(230, 81, 0));
        g.fillOval(14, 14, 2, 2);
        g.fillOval(4, 14, 2, 2);

        // Center Jewel / Clashing Point Sparkle
        g.setColor(new Color(255, 255, 255));
        g.fillRect(9, 9, 2, 2);

        g.dispose();
        return img;
    }
}
