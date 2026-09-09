package com.osrscopilot.combat.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One meter row: a proportional coloured bar painted behind a left label
 * and a right value, optionally with a dim sub-line for extra detail.
 *
 * <p>{@link #update} lets a caller refresh the numbers in place instead of tearing the row down
 * and rebuilding it, so a live view can tick without flickering.
 */
public class MeterRowPanel extends JPanel
{
    private double fillRatio;
    private Color barColor;
    private final JLabel left;
    private final JLabel right;

    public MeterRowPanel(String label, String value, String subLabel, double fillRatio, Color barColor)
    {
        super(new BorderLayout(4, 0));
        this.fillRatio = clamp(fillRatio);
        this.barColor = barColor != null ? barColor : ColorScheme.BRAND_ORANGE;

        setOpaque(false);
        setBorder(new EmptyBorder(2, 4, 2, 4));

        left = new JLabel(labelHtml(label, subLabel));
        left.setFont(FontManager.getRunescapeSmallFont());
        left.setForeground(Color.WHITE);

        right = new JLabel(value);
        right.setFont(FontManager.getRunescapeSmallFont());
        right.setForeground(Color.WHITE);

        add(left, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    /** Refresh this row's numbers and bar without recreating it. */
    public void update(String label, String value, String subLabel, double fillRatio, Color barColor)
    {
        this.fillRatio = clamp(fillRatio);
        if (barColor != null)
        {
            this.barColor = barColor;
        }
        left.setText(labelHtml(label, subLabel));
        right.setText(value);
        repaint();
    }

    private static double clamp(double v)
    {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static String labelHtml(String label, String subLabel)
    {
        return subLabel != null && !subLabel.isEmpty()
            ? "<html>" + escape(label) + "<br><span style='color:#c9ccd2;font-size:9px'>" + escape(subLabel) + "</span></html>"
            : label;
    }

    private static String escape(String s)
    {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public Dimension getMaximumSize()
    {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
        g2.fillRoundRect(0, 0, w, h, 4, 4);

        int fillW = (int) Math.round(w * fillRatio);
        if (fillW > 0)
        {
            g2.setColor(new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 70));
            g2.fillRoundRect(0, 0, Math.max(fillW, 4), h, 4, 4);
            g2.setColor(new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 160));
            g2.fillRect(0, h - 2, Math.max(fillW, 4), 2);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
