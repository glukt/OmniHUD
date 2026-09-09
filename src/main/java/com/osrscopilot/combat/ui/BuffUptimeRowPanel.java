package com.osrscopilot.combat.ui;

import com.osrscopilot.combat.engine.BuffTrackingEngine.BuffUptimeSnapshot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

public class BuffUptimeRowPanel extends JPanel
{
    private final BuffUptimeSnapshot snapshot;

    public BuffUptimeRowPanel(BuffUptimeSnapshot snapshot)
    {
        this.snapshot = snapshot;
        setLayout(new BorderLayout(6, 0));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        setPreferredSize(new Dimension(200, 24));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        // Left Label: Icon + Name
        String leftText = (snapshot.getIconSymbol() != null ? snapshot.getIconSymbol() + " " : "") + snapshot.getName();
        JLabel nameLabel = new JLabel(leftText);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());
        nameLabel.setForeground(snapshot.isCurrentlyActive() ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);

        // Right Label: Uptime % and Duration
        int totalSec = (int) snapshot.getTotalActiveSeconds();
        int mins = totalSec / 60;
        int secs = totalSec % 60;
        String durStr = mins > 0 ? String.format("%dm %02ds", mins, secs) : String.format("%ds", secs);
        String rightText = String.format("%.1f%% (%s)", snapshot.getUptimePercent(), durStr);

        JLabel valLabel = new JLabel(rightText);
        valLabel.setFont(FontManager.getRunescapeSmallFont());
        valLabel.setForeground(getUptimeColor(snapshot.getUptimePercent()));

        add(nameLabel, BorderLayout.WEST);
        add(valLabel, BorderLayout.EAST);

        // Detailed Tooltip
        setToolTipText(String.format(
            "<html><b>%s</b> (%s)<br>" +
            "Total Uptime: <b>%.1f%%</b> (%.1fs)<br>" +
            "Applications: <b>%d</b><br>" +
            "Status: <font color='%s'><b>%s</b></font></html>",
            snapshot.getName(),
            snapshot.getCategory() != null ? snapshot.getCategory().getDisplayName() : "Buff",
            snapshot.getUptimePercent(),
            snapshot.getTotalActiveSeconds(),
            snapshot.getCountApplied(),
            snapshot.isCurrentlyActive() ? "#48BB78" : "#A0AEC0",
            snapshot.isCurrentlyActive() ? "ACTIVE" : "INACTIVE"
        ));
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        if (snapshot == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw progress bar proportional to uptime percentage behind text
        int barWidth = (int) ((getWidth() - 2) * (Math.min(100.0, snapshot.getUptimePercent()) / 100.0));
        if (barWidth > 0)
        {
            Color catColor = snapshot.getCategory() != null ? snapshot.getCategory().getColor() : new Color(72, 187, 120);
            g2.setColor(new Color(catColor.getRed(), catColor.getGreen(), catColor.getBlue(), 45));
            g2.fillRoundRect(1, 1, barWidth, getHeight() - 2, 4, 4);

            if (snapshot.isCurrentlyActive())
            {
                g2.setColor(new Color(catColor.getRed(), catColor.getGreen(), catColor.getBlue(), 120));
                g2.drawRoundRect(1, 1, barWidth, getHeight() - 2, 4, 4);
            }
        }

        g2.dispose();
    }

    private Color getUptimeColor(double pct)
    {
        if (pct >= 80.0) return new Color(104, 211, 145); // Emerald Green
        if (pct >= 40.0) return new Color(246, 224, 94);  // Amber Gold
        return new Color(239, 154, 154);                  // Coral Muted Red
    }
}
