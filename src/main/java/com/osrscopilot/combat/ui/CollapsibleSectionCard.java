package com.osrscopilot.combat.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

public class CollapsibleSectionCard extends JPanel
{
    private final JPanel headerPanel = new JPanel(new BorderLayout());
    private final JPanel bodyContainer = new JPanel();
    private final JLabel titleLabel = new JLabel();
    private final JLabel chevronLabel = new JLabel("[-]");
    private final JLabel summaryLabel = new JLabel();
    private boolean isExpanded = true;

    private static final Color TOUR_ACCENT = new Color(255, 152, 31);
    private static final Color IDLE_BORDER = new Color(55, 55, 62);

    public CollapsibleSectionCard(String title, Color accentColor, JPanel content)
    {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(55, 55, 62), 1),
            new EmptyBorder(3, 5, 3, 5)
        ));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));


        // Header
        headerPanel.setLayout(new BorderLayout(8, 0));
        headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headerPanel.setBorder(new EmptyBorder(2, 2, 2, 6));

        titleLabel.setText(title);
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setForeground(accentColor);

        chevronLabel.setFont(FontManager.getRunescapeBoldFont());
        chevronLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        summaryLabel.setFont(FontManager.getRunescapeSmallFont());
        summaryLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JPanel leftHeader = new JPanel();
        leftHeader.setLayout(new BoxLayout(leftHeader, BoxLayout.X_AXIS));
        leftHeader.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        leftHeader.add(chevronLabel);
        leftHeader.add(Box.createRigidArea(new Dimension(5, 0)));
        leftHeader.add(titleLabel);

        headerPanel.add(leftHeader, BorderLayout.WEST);
        headerPanel.add(summaryLabel, BorderLayout.EAST);


        // Body
        bodyContainer.setLayout(new BorderLayout());
        bodyContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bodyContainer.setBorder(new EmptyBorder(4, 2, 2, 2));
        if (content != null)
        {
            bodyContainer.add(content, BorderLayout.CENTER);
        }

        headerPanel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                toggle();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                headerPanel.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }
        });


        add(headerPanel, BorderLayout.NORTH);
        add(bodyContainer, BorderLayout.CENTER);
    }

    public void setSummaryText(String text)
    {
        summaryLabel.setText(text);
    }

    public void setContent(JPanel content)
    {
        bodyContainer.removeAll();
        if (content != null)
        {
            bodyContainer.add(content, BorderLayout.CENTER);
        }
        bodyContainer.revalidate();
        bodyContainer.repaint();
    }

    public void toggle()
    {
        setExpanded(!isExpanded);
    }

    public boolean isExpanded()
    {
        return isExpanded;
    }

    public JPanel getBodyContainer()
    {
        return bodyContainer;
    }

    public void setExpanded(boolean expanded)
    {
        this.isExpanded = expanded;
        chevronLabel.setText(expanded ? "[-]" : "[+]");
        bodyContainer.setVisible(expanded);
        revalidate();
        repaint();
    }

    /** Ring this card in the tour accent while the walkthrough is talking about it. Border geometry
     *  is kept constant (thicker line, thinner inner pad) so nothing jumps. */
    public void setTourHighlight(boolean on)
    {
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(on ? TOUR_ACCENT : IDLE_BORDER, on ? 2 : 1),
            new EmptyBorder(on ? 2 : 3, on ? 4 : 5, on ? 2 : 3, on ? 4 : 5)));
        Color bg = on ? new Color(38, 33, 22) : ColorScheme.DARKER_GRAY_COLOR;
        setBackground(bg);
        headerPanel.setBackground(bg);
        revalidate();
        repaint();
    }
}

