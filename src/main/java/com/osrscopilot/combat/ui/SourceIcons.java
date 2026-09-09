package com.osrscopilot.combat.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Small painted glyphs for damage sources that aren't items - the status effects (poison / venom /
 * burn / bleed) and the "no weapon" cases (punch / kick). Everything else resolves to a real item
 * icon via {@link net.runelite.client.game.ItemManager}.
 */
final class SourceIcons
{
    static final int SIZE = 14;

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    private SourceIcons()
    {
    }

    /** A glyph for {@code name}, or null if this source should use a real item icon instead. */
    static BufferedImage forName(String name)
    {
        if (name == null || name.isEmpty())
        {
            return null;
        }
        String n = name.toLowerCase(Locale.ROOT);
        String key;
        if (n.contains("venom"))
        {
            key = "venom";
        }
        else if (n.contains("poison"))
        {
            key = "poison";
        }
        else if (n.contains("burn"))
        {
            key = "burn";
        }
        else if (n.contains("bleed"))
        {
            key = "bleed";
        }
        else if (n.contains("kick") || n.contains("stomp"))
        {
            key = "kick";
        }
        else if (n.equals("unarmed") || n.contains("punch") || n.contains("fist"))
        {
            key = "punch";
        }
        else
        {
            return null;
        }
        return CACHE.computeIfAbsent(key, SourceIcons::paint);
    }

    private static BufferedImage paint(String key)
    {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        switch (key)
        {
            case "poison":
                splat(g, new Color(88, 190, 60));
                break;
            case "venom":
                splat(g, new Color(40, 120, 30));
                break;
            case "burn":
                splat(g, new Color(240, 140, 40));
                break;
            case "bleed":
                splat(g, new Color(200, 40, 40));
                break;
            case "punch":
                fist(g);
                break;
            case "kick":
                boot(g);
                break;
            default:
                break;
        }
        g.dispose();
        return img;
    }

    /** A rounded hitsplat blob with a couple of flecks - the OSRS poison/venom/burn splat shape. */
    private static void splat(Graphics2D g, Color c)
    {
        g.setColor(c);
        g.fill(new Ellipse2D.Double(2, 3, 10, 8));
        g.fill(new Ellipse2D.Double(1, 6, 4, 4));
        g.fill(new Ellipse2D.Double(9, 2, 4, 4));
        g.setColor(new Color(255, 255, 255, 150));
        g.fill(new Ellipse2D.Double(4, 5, 3, 2));
    }

    private static void fist(Graphics2D g)
    {
        g.setColor(new Color(225, 190, 150));
        g.fillRoundRect(3, 5, 8, 6, 3, 3);          // fist mass
        g.fillRoundRect(3, 4, 7, 3, 2, 2);          // knuckles
        g.setColor(new Color(150, 110, 80));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(5, 4, 5, 7);
        g.drawLine(7, 4, 7, 7);
        g.drawLine(9, 4, 9, 7);
    }

    private static void boot(Graphics2D g)
    {
        g.setColor(new Color(120, 90, 60));
        Path2D.Double p = new Path2D.Double();
        p.moveTo(4, 2);
        p.lineTo(7, 2);
        p.lineTo(7, 8);
        p.lineTo(12, 8);
        p.lineTo(12, 11);
        p.lineTo(4, 11);
        p.closePath();
        g.fill(p);
        g.setColor(new Color(60, 45, 30));
        g.fillRect(4, 10, 9, 2);                    // sole
    }
}
