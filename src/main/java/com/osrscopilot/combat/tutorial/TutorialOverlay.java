package com.osrscopilot.combat.tutorial;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import com.osrscopilot.ui.theme.CopilotPalette;
import net.runelite.api.Client;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * The coach-mark engine for the interactive tutorials. Renders a full-canvas dim with one control
 * spotlit, a caption card next to it, and Back / Next / Skip controls; it drives an ordered
 * {@link Tour} and its per-step {@code onEnter} / {@code onExit} hooks.
 *
 * <p>Surface-agnostic: it points at whatever screen-space {@link Rectangle} a step's target supplier
 * returns. It is completely inert while no tour is running - {@link #render} returns immediately and
 * the input handlers no-op - so it is safe to register at plugin start-up with nothing launching a
 * tour yet.
 *
 * <p>Draws in absolute canvas coordinates and returns {@code null} (the RuneLite screen-overlay
 * pattern), so its hitboxes and incoming mouse points share one coordinate space.
 */
@Singleton
public class TutorialOverlay extends Overlay implements MouseListener, KeyListener
{
    private static final Color SCRIM        = new Color(8, 6, 3, 168);
    private static final Color RING         = new Color(187, 134, 252);   // quest purple
    private static final Color CARD_BG      = new Color(15, 15, 20, 246);
    private static final Color CARD_BORDER  = new Color(45, 45, 52, 255);
    // Unified with the panel accent so the walkthrough reads as part of the plugin (the rest of
    // this overlay's chrome stays canvas-tuned).
    private static final Color TITLE_GOLD   = CopilotPalette.ACCENT;
    private static final Color BODY_TEXT    = new Color(221, 221, 229);
    private static final Color DOT_ON       = new Color(187, 134, 252);
    private static final Color DOT_OFF      = new Color(90, 90, 104);
    private static final Color BTN_BG       = new Color(34, 34, 42, 235);
    private static final Color BTN_BG_HOVER = new Color(58, 58, 72, 245);
    private static final Color BTN_TEXT     = new Color(232, 232, 238);
    private static final Stroke RING_STROKE = new BasicStroke(2f);

    private static final int CARD_MAX_W = 324;
    private static final int PAD = 12;
    private static final int SPOT_PAD = 6;
    private static final Rectangle EMPTY = new Rectangle();

    private final Client client;

    // Written on the client thread in render(), read on the AWT thread in the input handlers
    // (and by TutorialOverlayTest). Package-private on purpose.
    volatile Rectangle skipHit = EMPTY;
    volatile Rectangle backHit = EMPTY;
    volatile Rectangle nextHit = EMPTY;
    volatile Rectangle spotlight = EMPTY;
    volatile Rectangle captionBounds = EMPTY;

    private Tour tour;
    private int index = -1;
    private Point mouse;

    // Runs before any component-level key listener, so Esc / arrows work regardless of what has
    // canvas focus. Added to the shared KeyboardFocusManager only while a tour is live.
    private final KeyEventDispatcher keyDispatcher = this::dispatchTourKey;
    private boolean keyDispatcherAdded = false;

    @Inject
    public TutorialOverlay(Client client)
    {
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(OverlayPriority.HIGHEST);
    }

    // ---- lifecycle --------------------------------------------------------------------------

    public boolean isActive()
    {
        return tour != null;
    }

    public String activeTourId()
    {
        return tour != null ? tour.getId() : null;
    }

    /** For tests / step dots. -1 when no tour is running. */
    public int currentIndex()
    {
        return index;
    }

    public synchronized void start(Tour t)
    {
        if (t == null || t.getStepCount() == 0)
        {
            return;
        }
        end();
        this.tour = t;
        this.index = 0;
        addKeyDispatcher();
        t.start();
        t.getStep(0).enter();
    }

    public synchronized void next()
    {
        if (tour == null)
        {
            return;
        }
        if (index >= tour.getStepCount() - 1)
        {
            end();
            return;
        }
        tour.getStep(index).exit();
        index++;
        tour.getStep(index).enter();
    }

    public synchronized void back()
    {
        if (tour == null || index <= 0)
        {
            return;
        }
        tour.getStep(index).exit();
        index--;
        tour.getStep(index).enter();
    }

    public synchronized void end()
    {
        if (tour == null)
        {
            return;
        }
        Tour finished = tour;
        int at = index;
        tour = null;
        index = -1;
        skipHit = backHit = nextHit = spotlight = captionBounds = EMPTY;
        removeKeyDispatcher();
        if (at >= 0 && at < finished.getStepCount())
        {
            finished.getStep(at).exit();
        }
        finished.finish();
    }

    private void addKeyDispatcher()
    {
        if (!keyDispatcherAdded)
        {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
            keyDispatcherAdded = true;
        }
    }

    private void removeKeyDispatcher()
    {
        if (keyDispatcherAdded)
        {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
            keyDispatcherAdded = false;
        }
    }

    private boolean dispatchTourKey(KeyEvent e)
    {
        if (tour == null || e.getID() != KeyEvent.KEY_PRESSED)
        {
            return false;
        }
        switch (e.getKeyCode())
        {
            case KeyEvent.VK_ESCAPE:
                end();
                return true;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_ENTER:
                next();
                return true;
            case KeyEvent.VK_LEFT:
                back();
                return true;
            default:
                return false;
        }
    }

    // ---- render ----------------------------------------------------------------------------

    @Override
    public Dimension render(Graphics2D g)
    {
        Tour t = tour;
        if (t == null || client == null || client.getCanvas() == null)
        {
            return null;
        }
        int cw = client.getCanvas().getWidth();
        int ch = client.getCanvas().getHeight();
        if (cw <= 0 || ch <= 0)
        {
            return null;
        }
        int i = Math.max(0, Math.min(index, t.getStepCount() - 1));
        TutorialStep step = t.getStep(i);

        Rectangle target = step.resolveTarget();
        Rectangle hole = null;
        if (target != null && target.width > 3 && target.height > 3)
        {
            Rectangle clipped = target.intersection(new Rectangle(0, 0, cw, ch));
            if (!clipped.isEmpty())
            {
                hole = new Rectangle(clipped.x - SPOT_PAD, clipped.y - SPOT_PAD,
                    clipped.width + 2 * SPOT_PAD, clipped.height + 2 * SPOT_PAD);
            }
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(SCRIM);
        if (hole == null)
        {
            g.fillRect(0, 0, cw, ch);
            spotlight = EMPTY;
        }
        else
        {
            int hx = Math.max(0, hole.x);
            int hy = Math.max(0, hole.y);
            int hr = Math.min(cw, hole.x + hole.width);
            int hb = Math.min(ch, hole.y + hole.height);
            g.fillRect(0, 0, cw, hy);                     // above
            g.fillRect(0, hb, cw, ch - hb);               // below
            g.fillRect(0, hy, hx, hb - hy);               // left
            g.fillRect(hr, hy, cw - hr, hb - hy);         // right

            float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 320.0));
            g.setStroke(RING_STROKE);
            g.setColor(new Color(RING.getRed(), RING.getGreen(), RING.getBlue(), 150 + Math.round(80 * pulse)));
            g.drawRoundRect(hole.x, hole.y, hole.width, hole.height, 8, 8);
            spotlight = new Rectangle(hole);
        }

        drawCard(g, cw, ch, t, i, step, hole);
        return null;
    }

    private void drawCard(Graphics2D g, int cw, int ch, Tour t, int i, TutorialStep step, Rectangle spot)
    {
        int cardW = Math.min(CARD_MAX_W, cw - 24);
        if (cardW < 160)
        {
            cardW = Math.max(120, cw - 8);
        }

        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics bodyFm = g.getFontMetrics();
        List<String> lines = wrap(bodyFm, step.getBody(), cardW - 2 * PAD);
        int lineH = bodyFm.getHeight();

        g.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics titleFm = g.getFontMetrics();
        int titleH = titleFm.getHeight();
        int btnH = 18;

        int cardH = PAD + titleH + 4 + lines.size() * lineH + 8 + 1 + 10 + btnH + PAD;

        int cardX;
        int cardY;
        if (spot != null)
        {
            cardX = clamp(spot.x + spot.width / 2 - cardW / 2, 8, cw - cardW - 8);
            int below = spot.y + spot.height + 12;
            int above = spot.y - cardH - 12;
            if (below + cardH <= ch - 8)
            {
                cardY = below;
            }
            else if (above >= 8)
            {
                cardY = above;
            }
            else
            {
                cardY = clamp(spot.y + spot.height / 2 - cardH / 2, 8, ch - cardH - 8);
            }
        }
        else if (step.getPlacement() == TutorialStep.Placement.SIDE_PANEL)
        {
            // Panel steps: caption hugs the right edge, right beside the side panel it describes.
            cardX = Math.max(8, cw - cardW - 12);
            cardY = clamp(ch / 2 - cardH / 2, 12, ch - cardH - 8);
        }
        else
        {
            // No spotlight (intro / outro): sit in the upper area so the centred HUD below it
            // stays clear.
            cardX = (cw - cardW) / 2;
            cardY = clamp(ch / 5, 12, ch - cardH - 8);
        }
        captionBounds = new Rectangle(cardX, cardY, cardW, cardH);

        g.setColor(CARD_BG);
        g.fillRoundRect(cardX, cardY, cardW, cardH, 8, 8);
        g.setColor(CARD_BORDER);
        g.drawRoundRect(cardX, cardY, cardW - 1, cardH - 1, 8, 8);

        int x = cardX + PAD;
        int y = cardY + PAD + titleFm.getAscent();
        g.setFont(FontManager.getRunescapeBoldFont());
        g.setColor(TITLE_GOLD);
        g.drawString(clip(titleFm, step.getTitle(), cardW - 2 * PAD), x, y);

        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(BODY_TEXT);
        y += 4 + bodyFm.getAscent();
        for (String line : lines)
        {
            g.drawString(line, x, y);
            y += lineH;
        }

        int dividerY = cardY + cardH - PAD - btnH - 10;
        g.setColor(CARD_BORDER);
        g.drawLine(cardX + PAD, dividerY, cardX + cardW - PAD, dividerY);

        int rowY = cardY + cardH - PAD - btnH;

        // step dots, left-aligned on the button row
        int dotY = rowY + btnH / 2;
        int dx = cardX + PAD + 3;
        for (int d = 0; d < t.getStepCount(); d++)
        {
            g.setColor(d == i ? DOT_ON : DOT_OFF);
            g.fillOval(dx, dotY - 3, 6, 6);
            dx += 10;
        }

        // buttons, right-aligned: [Skip] [Back] [Next/Done]
        boolean last = i >= t.getStepCount() - 1;
        int rightEdge = cardX + cardW - PAD;
        rightEdge = drawButton(g, last ? "Done" : "Next >", rightEdge, rowY, btnH, true, r -> nextHit = r);
        if (i > 0)
        {
            rightEdge = drawButton(g, "< Back", rightEdge - 6, rowY, btnH, true, r -> backHit = r);
        }
        else
        {
            backHit = EMPTY;
        }
        drawButton(g, "Skip", rightEdge - 6, rowY, btnH, false, r -> skipHit = r);
    }

    /** Draws a right-aligned button whose right edge is at {@code rightX}; returns its left edge. */
    private int drawButton(Graphics2D g, String label, int rightX, int y, int h, boolean accent,
        java.util.function.Consumer<Rectangle> sink)
    {
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(label) + 16;
        int x = rightX - w;
        Rectangle r = new Rectangle(x, y, w, h);
        boolean hover = mouse != null && r.contains(mouse);
        g.setColor(hover ? BTN_BG_HOVER : BTN_BG);
        g.fillRoundRect(x, y, w, h, 6, 6);
        if (accent)
        {
            g.setColor(new Color(RING.getRed(), RING.getGreen(), RING.getBlue(), hover ? 235 : 170));
            g.drawRoundRect(x, y, w - 1, h - 1, 6, 6);
        }
        g.setColor(BTN_TEXT);
        g.drawString(label, x + 8, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        sink.accept(r);
        return x;
    }

    private static List<String> wrap(FontMetrics fm, String text, int maxW)
    {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty())
        {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+"))
        {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) > maxW && line.length() > 0)
            {
                out.add(line.toString());
                line = new StringBuilder(word);
            }
            else
            {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0)
        {
            out.add(line.toString());
        }
        return out;
    }

    private static String clip(FontMetrics fm, String s, int maxW)
    {
        if (s == null)
        {
            return "";
        }
        if (fm.stringWidth(s) <= maxW)
        {
            return s;
        }
        String ell = "..";
        int end = s.length();
        while (end > 0 && fm.stringWidth(s.substring(0, end) + ell) > maxW)
        {
            end--;
        }
        return s.substring(0, end) + ell;
    }

    private static int clamp(int v, int lo, int hi)
    {
        return v < lo ? lo : (Math.min(v, hi));
    }

    // ---- input -----------------------------------------------------------------------------

    @Override
    public MouseEvent mousePressed(MouseEvent e)
    {
        if (tour == null || e == null)
        {
            return e;
        }
        Point p = e.getPoint();
        this.mouse = p;
        if (nextHit.contains(p))
        {
            next();
            e.consume();
            return e;
        }
        if (backHit.contains(p))
        {
            back();
            e.consume();
            return e;
        }
        if (skipHit.contains(p))
        {
            end();
            e.consume();
            return e;
        }
        TutorialStep step = currentStep();
        if (step != null && step.advancesOn(e))
        {
            next();
            return e; // don't consume - the real control must see this same press
        }
        e.consume(); // block every other click so the dimmed-out game/UI can't be operated
        return e;
    }

    @Override
    public MouseEvent mouseMoved(MouseEvent e)
    {
        if (tour != null && e != null)
        {
            this.mouse = e.getPoint();
        }
        return e; // never consume moves - the cursor must keep working
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent e)
    {
        if (tour != null && e != null)
        {
            this.mouse = e.getPoint();
            e.consume();
        }
        return e;
    }

    @Override
    public MouseEvent mouseClicked(MouseEvent e)
    {
        if (tour != null && e != null)
        {
            e.consume();
        }
        return e;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent e)
    {
        if (tour != null && e != null)
        {
            e.consume();
        }
        return e;
    }

    @Override
    public MouseEvent mouseEntered(MouseEvent e)
    {
        return e;
    }

    @Override
    public MouseEvent mouseExited(MouseEvent e)
    {
        return e;
    }

    @Override
    public void keyPressed(KeyEvent e)
    {
        // Fallback path. The KeyEventDispatcher installed in start() is primary and consumes the
        // event before it can reach here, so this only runs if that path is unavailable; end() /
        // next() / back() are all safe to call twice.
        if (dispatchTourKey(e))
        {
            e.consume();
        }
    }

    @Override
    public void keyTyped(KeyEvent e)
    {
    }

    @Override
    public void keyReleased(KeyEvent e)
    {
    }

    private synchronized TutorialStep currentStep()
    {
        if (tour == null || index < 0 || index >= tour.getStepCount())
        {
            return null;
        }
        return tour.getStep(index);
    }
}
