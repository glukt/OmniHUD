package com.osrscopilot.combat.tutorial;

import com.osrscopilot.ui.theme.CopilotPalette;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

/**
 * Rings one Swing component in the tour accent so an onboarding walkthrough can say "follow along
 * here" on a panel it can't draw a spotlight over. One component lit at a time; the previous one's
 * border is restored. Border geometry stays constant (a 2px line replacing whatever was there), so
 * the layout doesn't jump - fine for a transient highlight.
 */
public final class TourHighlight
{
    private static final Color ACCENT = CopilotPalette.ACCENT;

    private final Map<JComponent, Border> saved = new HashMap<>();
    private JComponent lit;

    /** Ring {@code c} (clearing any previous highlight). {@code null} just clears. */
    public void set(JComponent c)
    {
        if (c == lit)
        {
            return;
        }
        clear();
        lit = c;
        if (c != null)
        {
            saved.putIfAbsent(c, c.getBorder());
            c.setBorder(BorderFactory.createLineBorder(ACCENT, 2));
            repaint(c);
        }
    }

    public void clear()
    {
        if (lit == null)
        {
            return;
        }
        // Restore whatever the component had before - which may legitimately be null (no border).
        // Guarding on "original != null" left the accent ring in place on borderless containers,
        // so a moved-on highlight would visibly persist.
        if (saved.containsKey(lit))
        {
            lit.setBorder(saved.remove(lit));
        }
        repaint(lit);
        lit = null;
    }

    /** Scroll a highlighted component into view within its enclosing scroll pane. */
    public static void scrollIntoView(JComponent c)
    {
        if (c == null)
        {
            return;
        }
        SwingUtilities.invokeLater(() ->
            c.scrollRectToVisible(new java.awt.Rectangle(0, 0, c.getWidth(), Math.max(1, c.getHeight()))));
    }

    private static void repaint(JComponent c)
    {
        c.revalidate();
        c.repaint();
    }
}
