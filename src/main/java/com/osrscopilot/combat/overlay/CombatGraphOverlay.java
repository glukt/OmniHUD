package com.osrscopilot.combat.overlay;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.model.EncounterSegment;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

@Singleton
public class CombatGraphOverlay extends Overlay implements MouseListener
{
    private final Client client;
    private final OsrsCopilotConfig config;
    private final CombatEncounterManager encounterManager;
    private final CombatGraphCard graphCard;
    private boolean open = false;

    public CombatGraphCard getGraphCard()
    {
        return graphCard;
    }

    public boolean isOpen()
    {
        return open;
    }

    public void setOpen(boolean open)
    {
        this.open = open;
    }

    private boolean isDragging = false;
    private boolean isResizing = false;
    private Point dragStartPoint = null;
    private Point initialLocation = null;
    private Dimension initialSize = null;
    private Rectangle overlayBounds = null;

    @Inject
    public CombatGraphOverlay(
        Client client,
        OsrsCopilotConfig config,
        CombatEncounterManager encounterManager,
        CombatGraphCard graphCard)
    {
        this.client = client;
        this.config = config;
        this.encounterManager = encounterManager;
        this.graphCard = graphCard;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
        setMovable(true);
        setResizable(true);
        setMinimumSize(CombatGraphCard.MIN_WIDTH);
        setPreferredSize(new Dimension(CombatGraphCard.DEFAULT_WIDTH, CombatGraphCard.DEFAULT_HEIGHT));

        if (this.graphCard != null)
        {
            this.graphCard.setOnClose(() -> this.open = false);
        }
    }

    private boolean tutorialLayoutActive = false;
    private Point savedPreferredLocation;
    private OverlayPosition savedPreferredPosition;
    private java.util.function.Supplier<Rectangle> tutorialHudBounds;

    /** Park the graph card just off the HUD's right edge for the walkthrough; restored on end. The
     *  actual move happens in render() (client thread); this may be called from a button click.
     *  {@code hudBounds} supplies the live screen rect of the Combat HUD to anchor against. */
    public void beginTutorialLayout(java.util.function.Supplier<Rectangle> hudBounds)
    {
        this.tutorialHudBounds = hudBounds;
        if (tutorialLayoutActive)
        {
            return;
        }
        tutorialLayoutActive = true;
        savedPreferredLocation = getPreferredLocation();
        savedPreferredPosition = getPreferredPosition();
    }

    public void endTutorialLayout()
    {
        this.tutorialHudBounds = null;
        if (!tutorialLayoutActive)
        {
            return;
        }
        tutorialLayoutActive = false;
        setPreferredPosition(savedPreferredPosition);
        setPreferredLocation(savedPreferredLocation);
    }

    public void toggle()
    {
        this.open = !this.open;
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (!open || encounterManager == null)
        {
            return null;
        }

        EncounterSegment encounter = encounterManager.getSelectedOrCurrentEncounter();
        if (encounter == null)
        {
            encounter = encounterManager.getOverallSessionEncounter();
        }
        if (encounter == null)
        {
            return null;
        }

        Dimension size = getPreferredSize();
        if (size == null || size.width <= 0 || size.height <= 0)
        {
            size = new Dimension(CombatGraphCard.DEFAULT_WIDTH, CombatGraphCard.DEFAULT_HEIGHT);
        }

        // Interactive tutorial: park the graph card just off the HUD's right edge (client thread).
        // Game-logical canvas size, not the AWT Canvas (which can be scaled/stretched).
        if (tutorialLayoutActive && client != null)
        {
            int cw = client.getCanvasWidth();
            int ch = client.getCanvasHeight();
            Rectangle hud = tutorialHudBounds != null ? tutorialHudBounds.get() : null;
            if (cw > 0 && ch > 0 && hud != null && hud.width > 0)
            {
                int gx = hud.x + hud.width + 10;
                if (gx + size.width > cw - 8)
                {
                    gx = hud.x - size.width - 10;          // no room on the right - go left of the HUD
                }
                gx = Math.max(8, Math.min(cw - size.width - 8, gx));
                int gy = Math.max(8, Math.min(ch - size.height - 8, hud.y));
                setPreferredPosition(OverlayPosition.DETACHED);
                setPreferredLocation(new Point(gx, gy));
            }
            else if (cw > 0 && ch > 0)
            {
                setPreferredPosition(OverlayPosition.DETACHED);
                setPreferredLocation(new Point(
                    Math.max(8, Math.min(cw - size.width - 8, cw / 2 - size.width / 2)),
                    Math.max(8, Math.min(ch - size.height - 8, ch - size.height - 34))));
            }
        }

        graphCard.setSize(size);
        if (config != null)
        {
            graphCard.setStyle(config.combatGraphStyle());
        }

        Rectangle b = getBounds();
        if (b != null && b.width > 0 && b.height > 0)
        {
            overlayBounds = b;
        }
        else
        {
            Point prefLoc = getPreferredLocation();
            int bx = prefLoc != null ? prefLoc.x : 0;
            int by = prefLoc != null ? prefLoc.y : 0;
            overlayBounds = new Rectangle(bx, by, size.width, size.height);
        }

        Point mousePos = (client != null && client.getMouseCanvasPosition() != null)
            ? new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY())
            : null;

        Point localPos = new Point(0, 0);
        Point screenPos = new Point(overlayBounds.x, overlayBounds.y);

        graphCard.render(g, localPos, screenPos, mousePos, encounter);

        return size;
    }

    @Override
    public MouseEvent mousePressed(MouseEvent mouseEvent)
    {
        if (!open || overlayBounds == null)
        {
            return mouseEvent;
        }

        // Left button only; right / middle clicks pass through un-consumed.
        if (mouseEvent.getButton() == MouseEvent.BUTTON2 || mouseEvent.getButton() == MouseEvent.BUTTON3)
        {
            return mouseEvent;
        }

        Point p = mouseEvent.getPoint();
        if (!overlayBounds.contains(p))
        {
            return mouseEvent;
        }

        // 1. Resize Grip
        if (graphCard.isResizeGrip(p))
        {
            isResizing = true;
            dragStartPoint = p;
            Dimension cur = getPreferredSize();
            initialSize = new Dimension(cur != null ? cur : graphCard.getSize());
            mouseEvent.consume();
            return mouseEvent;
        }

        // 2. Buttons & Tabs inside GraphCard
        if (graphCard.handleClick(p, null))
        {
            mouseEvent.consume();
            return mouseEvent;
        }

        // 3. Direct Header Bar Dragging
        if (graphCard.isHeaderBar(p))
        {
            isDragging = true;
            dragStartPoint = p;
            Point loc = getPreferredLocation();
            initialLocation = loc != null ? new Point(loc.x, loc.y) : new Point(overlayBounds.x, overlayBounds.y);
            mouseEvent.consume();
            return mouseEvent;
        }

        return mouseEvent;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent mouseEvent)
    {
        if (isDragging)
        {
            isDragging = false;
            dragStartPoint = null;
            initialLocation = null;
            mouseEvent.consume();
            return mouseEvent;
        }
        if (isResizing)
        {
            isResizing = false;
            dragStartPoint = null;
            initialSize = null;
            mouseEvent.consume();
            return mouseEvent;
        }
        return mouseEvent;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent mouseEvent)
    {
        if (!open)
        {
            return mouseEvent;
        }

        if (isDragging && dragStartPoint != null && initialLocation != null)
        {
            int dx = mouseEvent.getX() - dragStartPoint.x;
            int dy = mouseEvent.getY() - dragStartPoint.y;
            Point newLoc = new Point(initialLocation.x + dx, initialLocation.y + dy);
            setPreferredLocation(newLoc);
            if (overlayBounds != null)
            {
                overlayBounds.setLocation(newLoc);
            }
            mouseEvent.consume();
            return mouseEvent;
        }

        if (isResizing && dragStartPoint != null && initialSize != null)
        {
            int dx = mouseEvent.getX() - dragStartPoint.x;
            int dy = mouseEvent.getY() - dragStartPoint.y;
            int newW = Math.max(CombatGraphCard.MIN_WIDTH, Math.min(CombatGraphCard.MAX_WIDTH, initialSize.width + dx));
            int newH = Math.max(CombatGraphCard.MIN_HEIGHT, Math.min(CombatGraphCard.MAX_HEIGHT, initialSize.height + dy));
            Dimension newDim = new Dimension(newW, newH);
            setPreferredSize(newDim);
            graphCard.setSize(newDim);
            mouseEvent.consume();
            return mouseEvent;
        }

        return mouseEvent;
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
    public MouseEvent mouseMoved(MouseEvent mouseEvent)
    {
        return mouseEvent;
    }
}
