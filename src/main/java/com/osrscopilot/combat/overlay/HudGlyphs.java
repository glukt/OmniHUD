package com.osrscopilot.combat.overlay;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;

/**
 * Tiny vector glyphs painted straight onto the canvas with {@link Graphics2D}. The on-canvas HUD
 * draws text in the RuneScape bitmap font, which has no gear / hamburger / caret / check / cross
 * code point, so printing those characters yields an empty "tofu" box. Draw the shape instead.
 *
 * <p>Every method takes its colour and antialiasing from the incoming graphics context - set the
 * colour before calling. Strokes are static so nothing is allocated per frame.
 */
final class HudGlyphs
{
    private HudGlyphs()
    {
    }

    private static final Stroke THIN = new BasicStroke(1f);
    private static final Stroke BOLD = new BasicStroke(1.6f);

    /**
     * Filled downward triangle - the "open this dropdown" affordance. Horizontal centre at
     * {@code cx}, top edge at {@code y}, {@code 2*halfW} wide and {@code h} tall.
     */
    static void downCaret(Graphics2D g, int cx, int y, int halfW, int h)
    {
        g.fillPolygon(new int[]{cx - halfW, cx + halfW, cx}, new int[]{y, y, y + h}, 3);
    }

    /**
     * A settings cog centred in an {@code s x s} box at (x, y): a filled 8-tooth gear with a
     * punched-out centre. {@code hub} is painted into the axle hole (pass the button's background
     * so the hole reads as empty); a fully-transparent colour just leaves the solid gear body.
     */
    static void settings(Graphics2D g, int x, int y, int s, java.awt.Color hub)
    {
        double cx = x + s / 2.0;
        double cy = y + s / 2.0;
        double rOut = s / 2.0;              // tooth tips
        double rBody = rOut * 0.72;         // solid gear body
        double rRoot = rBody * 0.86;        // where each tooth meets the body
        double rHole = Math.max(1.4, s * 0.20);
        double half = Math.toRadians(13);   // half a (rectangular) tooth

        g.fill(new java.awt.geom.Ellipse2D.Double(cx - rBody, cy - rBody, rBody * 2, rBody * 2));

        for (int i = 0; i < 8; i++)
        {
            double a = (Math.PI * 2 * i) / 8;
            double[][] pts = {
                {a - half, rRoot}, {a - half, rOut}, {a + half, rOut}, {a + half, rRoot},
            };
            java.awt.geom.Path2D.Double tooth = new java.awt.geom.Path2D.Double();
            for (int k = 0; k < pts.length; k++)
            {
                double px = cx + Math.cos(pts[k][0]) * pts[k][1];
                double py = cy + Math.sin(pts[k][0]) * pts[k][1];
                if (k == 0)
                {
                    tooth.moveTo(px, py);
                }
                else
                {
                    tooth.lineTo(px, py);
                }
            }
            tooth.closePath();
            g.fill(tooth);
        }

        if (hub != null)
        {
            java.awt.Color prev = g.getColor();
            g.setColor(hub);
            g.fill(new java.awt.geom.Ellipse2D.Double(cx - rHole, cy - rHole, rHole * 2, rHole * 2));
            g.setColor(prev);
        }
    }

    /** "Side panel" icon: a bordered box with a divider near the left edge, in an {@code s x s} box at (x, y). */
    static void panel(Graphics2D g, int x, int y, int s)
    {
        Stroke original = g.getStroke();
        g.setStroke(THIN);
        g.drawRect(x, y, s, s);
        g.drawLine(x + s / 3, y, x + s / 3, y + s);
        g.setStroke(original);
    }

    /** Check mark, in an {@code s x s} box at (x, y). */
    static void check(Graphics2D g, int x, int y, int s)
    {
        Stroke original = g.getStroke();
        g.setStroke(BOLD);
        g.drawLine(x, y + s / 2, x + s / 3, y + s);
        g.drawLine(x + s / 3, y + s, x + s, y);
        g.setStroke(original);
    }

    /** Close / dismiss cross, in an {@code s x s} box at (x, y). */
    static void cross(Graphics2D g, int x, int y, int s)
    {
        Stroke original = g.getStroke();
        g.setStroke(BOLD);
        g.drawLine(x, y, x + s, y + s);
        g.drawLine(x + s, y, x, y + s);
        g.setStroke(original);
    }
}
