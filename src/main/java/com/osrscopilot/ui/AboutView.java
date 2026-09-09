package com.osrscopilot.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import com.osrscopilot.ui.theme.CopilotPalette;
import com.osrscopilot.ui.theme.SidebarMetrics;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

public class AboutView extends JPanel
{
    private static final Color TITLE_COLOR = CopilotPalette.ACCENT;
    private static final Color SECTION_GOLD = new Color(255, 215, 0);
    private static final Color ACCENT_BLUE = new Color(90, 200, 250);
    private static final String VERSION = "0.1.0";
    private static final String REPO_URL = "https://github.com/glukt/OmniHUD";

    /**
     * Usable width (px) of the RuneLite side-panel content area. After the panel's own
     * insets and the always-present vertical scrollbar, actual child width is a little
     * under this - nothing here is allowed to want more than this. Shared value, see
     * {@link SidebarMetrics#PANEL_WIDTH}; wrapped-label body width is
     * {@link SidebarMetrics#WRAP_WIDTH} via {@link SidebarMetrics#htmlWrap}.
     */
    private static final int PANEL_WIDTH = SidebarMetrics.PANEL_WIDTH;

    private final Consumer<String> onNavigateTab;
    private Runnable onStartCombatTour;
    private Runnable onStartTownsMapTour;
    private Runnable onStartSlayerTour;

    /** Wired by the plugin to launch the interactive Combat HUD walkthrough. */
    public void setOnStartCombatTour(Runnable r) { this.onStartCombatTour = r; }

    /** Wired by the plugin to launch the Towns &amp; Map walkthrough. */
    public void setOnStartTownsMapTour(Runnable r) { this.onStartTownsMapTour = r; }

    /** Wired by the plugin to launch the Slayer walkthrough. */
    public void setOnStartSlayerTour(Runnable r) { this.onStartSlayerTour = r; }

    public AboutView(Consumer<String> onNavigateTab)
    {
        this.onNavigateTab = onNavigateTab;

        setLayout(new BorderLayout(0, 0));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(8, 8, 10, 8));

        // ---- Header -----------------------------------------------------------------------
        JLabel title = new JLabel("OmniHUD");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(TITLE_COLOR);
        title.setAlignmentX(LEFT_ALIGNMENT);
        title.setMaximumSize(new Dimension(PANEL_WIDTH, title.getPreferredSize().height));
        content.add(title);

        JLabel tagline = wrappedLabel(
            "Map guide · bestiary · combat & loot analytics · Slayer · shops",
            ColorScheme.LIGHT_GRAY_COLOR);
        content.add(Box.createRigidArea(new Dimension(0, 2)));
        content.add(tagline);

        JLabel status = wrappedLabel(
            "v" + VERSION + " · community build, not on the Plugin Hub",
            ColorScheme.MEDIUM_GRAY_COLOR);
        content.add(Box.createRigidArea(new Dimension(0, 1)));
        content.add(status);
        content.add(Box.createRigidArea(new Dimension(0, 12)));

        // ---- Guided tours ---------------------------------------------------------------
        content.add(sectionHeader("New here? Take a tour"));
        content.add(Box.createRigidArea(new Dimension(0, 4)));
        content.add(tourButton("Combat HUD tour",
            "Guided walkthrough of the on-canvas Combat HUD", () -> onStartCombatTour));
        content.add(Box.createRigidArea(new Dimension(0, 3)));
        content.add(tourButton("Towns & Map tour",
            "Towns, shops, search, and the World Map snap", () -> onStartTownsMapTour));
        content.add(Box.createRigidArea(new Dimension(0, 3)));
        content.add(tourButton("Slayer tour",
            "Tasks, spawn locations, and drops", () -> onStartSlayerTour));
        content.add(Box.createRigidArea(new Dimension(0, 14)));

        // ---- Jump to a tab -------------------------------------------------------------
        content.add(sectionHeader("Open a tab"));
        content.add(Box.createRigidArea(new Dimension(0, 4)));

        JPanel grid = new JPanel(new GridLayout(0, 2, 4, 4));
        grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        grid.add(featureButton("Combat", "Live DPS / DTPS / HPS, past fights, action ledger, fight graph",
            OsrsCopilotPanel.VIEW_COMBAT));
        grid.add(featureButton("Loot", "Drops per source, kill counts, GP/hr, and dryness vs the wiki rate",
            OsrsCopilotPanel.VIEW_LOOT));
        grid.add(featureButton("Bestiary", "~1,800 monsters: drop tables, rates, weaknesses, spawn zones",
            OsrsCopilotPanel.VIEW_MONSTERS));
        grid.add(featureButton("Slayer", "Active task, XP & GP, master requirements, spawn locations",
            OsrsCopilotPanel.VIEW_SLAYER));
        grid.add(featureButton("Towns", "Every settlement and its vendors, on the World Map",
            OsrsCopilotPanel.VIEW_TOWNS));
        grid.add(featureButton("Shops", "~495 shops: live stock, restock timers, buy/sell prices",
            OsrsCopilotPanel.VIEW_STOCK));
        grid.add(featureButton("Cart", "Plan a buying run across shops and check it against your GP",
            OsrsCopilotPanel.VIEW_CART));
        grid.add(featureButton("Search", "One box across every drop table and every shop",
            OsrsCopilotPanel.VIEW_SEARCH));
        grid.setMaximumSize(new Dimension(PANEL_WIDTH, grid.getPreferredSize().height));
        content.add(grid);
        content.add(Box.createRigidArea(new Dimension(0, 14)));

        // ---- Tips ---------------------------------------------------------------------
        content.add(sectionHeader("Good to know"));
        content.add(Box.createRigidArea(new Dimension(0, 4)));
        JLabel tips = wrappedLabel(
            "• <b>Map</b> on any shop, monster or town snaps the World Map to it.<br>"
            + "• Right-click a minimap vendor or an NPC in-game to jump to its shop.<br>"
            + "• Search shows shop sellers and monster droppers side by side.",
            ColorScheme.LIGHT_GRAY_COLOR);
        content.add(tips);
        content.add(Box.createRigidArea(new Dimension(0, 14)));

        // ---- Known issues -----------------------------------------------------------------
        content.add(sectionHeader("Known issues"));
        content.add(Box.createRigidArea(new Dimension(0, 4)));
        JLabel known = wrappedLabel(
            "• A handful of dungeon entrances aren't pinned exactly yet - the map opens the "
            + "<i>area</i> with a soft marker instead of a precise beacon. Mostly instanced / "
            + "quest dungeons: Lassar Undercity &amp; Catacombs, Abyssal Nexus, Puro-Puro, "
            + "Tarn's Lair, Tolna's Rift, Meiyerditch Laboratories, Ape Atoll Temple, and the "
            + "Wilderness singles-boss caves (KBD, Artio, Calvar'ion, Spindel).<br>"
            + "• A few spawn zones are still tagged to the wrong dungeon, so their entrance "
            + "beacon can be off.<br>"
            + "• Combat: the \"Current Session\" timer keeps ticking on the login screen.",
            ColorScheme.LIGHT_GRAY_COLOR);
        content.add(known);
        content.add(Box.createRigidArea(new Dimension(0, 4)));
        JLabel knownLink = wrappedLabel(
            "Reported / fixed issues are tracked on GitHub - see <b>Report a bug</b> below.",
            ColorScheme.MEDIUM_GRAY_COLOR);
        content.add(knownLink);
        content.add(Box.createRigidArea(new Dimension(0, 14)));

        // ---- Footer ----------------------------------------------------------------------
        JLabel testers = wrappedLabel(
            "Testing? Bugs are expected - tell me what broke and what you were doing.",
            ColorScheme.MEDIUM_GRAY_COLOR);
        content.add(testers);
        content.add(Box.createRigidArea(new Dimension(0, 5)));

        JPanel links = new JPanel(new GridLayout(1, 2, 4, 0));
        links.setBackground(ColorScheme.DARK_GRAY_COLOR);
        links.setMaximumSize(new Dimension(PANEL_WIDTH, 22));
        links.setAlignmentX(LEFT_ALIGNMENT);
        links.add(linkButton("GitHub", REPO_URL));
        links.add(linkButton("Report a bug", REPO_URL + "/issues"));
        content.add(links);
        content.add(Box.createRigidArea(new Dimension(0, 8)));

        JLabel license = wrappedLabel("BSD 2-Clause · not affiliated with Jagex",
            ColorScheme.MEDIUM_GRAY_COLOR);
        content.add(license);

        JPanel listWrapper = new ScrollableContentPanel();
        listWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listWrapper.add(content, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(listWrapper);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * A left-aligned small-font label whose text wraps (instead of clipping) inside the
     * side panel. Uses the same fixed {@code <html>} body width as every other wrapped
     * label in the plugin and caps its maximum width at {@link #PANEL_WIDTH} so BoxLayout
     * can never stretch it past the panel.
     */
    private JLabel wrappedLabel(String bodyHtml, Color fg)
    {
        JLabel l = new JLabel(SidebarMetrics.htmlWrap(bodyHtml));
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(fg);
        l.setAlignmentX(LEFT_ALIGNMENT);
        // Cap the width only: BoxLayout can never stretch the label past the panel, but its
        // height stays free so the wrapped text is never vertically clipped.
        l.setMaximumSize(new Dimension(PANEL_WIDTH, Integer.MAX_VALUE));
        return l;
    }

    private JLabel sectionHeader(String text)
    {
        JLabel h = new JLabel(text.toUpperCase());
        h.setFont(FontManager.getRunescapeSmallFont());
        h.setForeground(SECTION_GOLD);
        h.setAlignmentX(LEFT_ALIGNMENT);
        h.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR));
        h.setMaximumSize(new Dimension(PANEL_WIDTH, 16));
        return h;
    }

    private JButton tourButton(String label, String tooltip, java.util.function.Supplier<Runnable> action)
    {
        JButton b = new JButton(label);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        b.setForeground(SECTION_GOLD);
        b.setFocusPainted(false);
        b.setHorizontalAlignment(JButton.LEFT);
        b.setMargin(new Insets(2, 6, 2, 6));
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setToolTipText(tooltip);
        b.setPreferredSize(new Dimension(0, 22));
        b.setMaximumSize(new Dimension(PANEL_WIDTH, 22));
        b.addActionListener(e -> {
            Runnable r = action.get();
            if (r != null)
            {
                r.run();
            }
        });
        return b;
    }

    private JButton featureButton(String name, String tooltip, String targetView)
    {
        JButton b = new JButton(name);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        b.setForeground(ACCENT_BLUE);
        b.setFocusPainted(false);
        b.setMargin(new Insets(2, 4, 2, 4));
        b.setToolTipText(tooltip);
        b.setPreferredSize(new Dimension(0, 22));
        b.setMaximumSize(new Dimension(PANEL_WIDTH / 2, 22));
        b.addActionListener(e -> {
            if (onNavigateTab != null)
            {
                onNavigateTab.accept(targetView);
            }
        });
        return b;
    }

    private JButton linkButton(String text, String url)
    {
        JButton b = new JButton(text);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setBackground(ColorScheme.DARK_GRAY_COLOR);
        b.setForeground(ACCENT_BLUE);
        b.setFocusPainted(false);
        b.setMargin(new Insets(1, 4, 1, 4));
        b.setToolTipText(url);
        b.setMaximumSize(new Dimension(PANEL_WIDTH / 2, 22));
        b.addActionListener(e -> LinkBrowser.browse(url));
        return b;
    }

    private static class ScrollableContentPanel extends JPanel implements javax.swing.Scrollable
    {
        ScrollableContentPanel()
        {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
        {
            return 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }
}
