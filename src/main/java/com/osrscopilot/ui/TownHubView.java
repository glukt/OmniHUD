package com.osrscopilot.ui;

import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.TownNode;
import com.osrscopilot.util.NpcPortraitManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

public class TownHubView extends JPanel
{
    private static final Color TITLE_COLOR = new Color(255, 152, 31);
    private static final Color QUEST_PURPLE = new Color(187, 134, 252);
    private static final Color ACCENT_BLUE = new Color(90, 200, 250);

    private final ShopDatabase shopDatabase;
    private final NpcPortraitManager npcPortraitManager;
    private final Consumer<Shop> onInspectShop;
    private final Consumer<Shop> onFocusShopOnMap;
    private final Runnable onOpenSpreadsheet;

    private final JComboBox<String> townDropdown = new JComboBox<>();
    private final JComboBox<String> categoryDropdown = new JComboBox<>();
    private final JTextField searchField = new JTextField();
    private final JPanel shopListContainer = new JPanel();
    private final JLabel townDescLabel = new JLabel();

    // Interactive tutorial: the first shop card of the current list + its Map button, and the
    // highlighter that rings whichever the walkthrough is pointing at.
    private final com.osrscopilot.combat.tutorial.TourHighlight tourHighlight =
        new com.osrscopilot.combat.tutorial.TourHighlight();
    private javax.swing.JComponent tourFirstCard;
    private javax.swing.JComponent tourFirstMapBtn;

    public TownHubView(
        ShopDatabase shopDatabase,
        NpcPortraitManager npcPortraitManager,
        Consumer<Shop> onInspectShop,
        Consumer<Shop> onFocusShopOnMap,
        Runnable onOpenSpreadsheet)
    {
        this.shopDatabase = shopDatabase;
        this.npcPortraitManager = npcPortraitManager;
        this.onInspectShop = onInspectShop;
        this.onFocusShopOnMap = onFocusShopOnMap;
        this.onOpenSpreadsheet = onOpenSpreadsheet;

        setLayout(new BorderLayout(0, 0));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(6, 6, 6, 6));

        JLabel titleLabel = new JLabel("Vendor Map: Town Hubs");
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setForeground(TITLE_COLOR);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // Town Dropdown
        townDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        townDropdown.setForeground(Color.WHITE);
        townDropdown.setFont(FontManager.getRunescapeFont());
        townDropdown.setAlignmentX(LEFT_ALIGNMENT);
        townDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        townDropdown.setToolTipText("Select a town or regional settlement");
        townDropdown.addActionListener(e -> onTownSelected());
        headerPanel.add(townDropdown);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // Town Description
        townDescLabel.setFont(FontManager.getRunescapeSmallFont());
        townDescLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        townDescLabel.setAlignmentX(LEFT_ALIGNMENT);
        headerPanel.add(townDescLabel);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // Category Filter
        categoryDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        categoryDropdown.setForeground(Color.WHITE);
        categoryDropdown.setFont(FontManager.getRunescapeSmallFont());
        categoryDropdown.setAlignmentX(LEFT_ALIGNMENT);
        categoryDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        categoryDropdown.setToolTipText("Filter local shops by inventory type (e.g. Magic, Food, Armour)");
        categoryDropdown.addItem("All Categories");
        categoryDropdown.addItem("Magic & Runes");
        categoryDropdown.addItem("Archery & Ranged");
        categoryDropdown.addItem("Melee & Armour");
        categoryDropdown.addItem("General Stores");
        categoryDropdown.addItem("Food & Fish");
        categoryDropdown.addItem("Herblore & Farming");
        categoryDropdown.addItem("Crafting & Clothes");
        categoryDropdown.addActionListener(e -> rebuildShopList());
        headerPanel.add(categoryDropdown);

        add(headerPanel, BorderLayout.NORTH);

        // Shop Cards List
        shopListContainer.setLayout(new BoxLayout(shopListContainer, BoxLayout.Y_AXIS));
        shopListContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel listWrapper = new ScrollableContentPanel();
        listWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listWrapper.add(shopListContainer, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(listWrapper);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);
    }

    public void initialize()
    {
        townDropdown.removeAllItems();
        List<TownNode> towns = new ArrayList<>(shopDatabase.getAllTowns());
        Collections.sort(towns, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (TownNode town : towns)
        {
            townDropdown.addItem(town.getName());
        }

        if (townDropdown.getItemCount() > 0)
        {
            townDropdown.setSelectedIndex(0);
        }
    }

    public void selectTownByName(String townName)
    {
        if (townDropdown.getItemCount() == 0)
        {
            initialize();
        }

        if (categoryDropdown.getSelectedIndex() != 0)
        {
            categoryDropdown.setSelectedIndex(0);
        }

        for (int i = 0; i < townDropdown.getItemCount(); i++)
        {
            if (townDropdown.getItemAt(i).equalsIgnoreCase(townName))
            {
                if (townDropdown.getSelectedIndex() == i)
                {
                    onTownSelected();
                }
                else
                {
                    townDropdown.setSelectedIndex(i);
                }
                break;
            }
        }
    }

    /**
     * Interactive tutorial: ring one part of this tab so the walkthrough can point at it. Keys:
     * {@code town} (the town picker), {@code firstShop} / {@code mapButton} (the first shop card and
     * its Map button). {@code null} clears the highlight. EDT only.
     */
    public void tourFocus(String key)
    {
        switch (key == null ? "" : key)
        {
            case "town":
                tourHighlight.set(townDropdown);
                break;
            case "firstShop":
                tourHighlight.set(tourFirstCard);
                com.osrscopilot.combat.tutorial.TourHighlight.scrollIntoView(tourFirstCard);
                break;
            case "mapButton":
                tourHighlight.set(tourFirstMapBtn);
                com.osrscopilot.combat.tutorial.TourHighlight.scrollIntoView(tourFirstMapBtn);
                break;
            default:
                tourHighlight.clear();
                break;
        }
    }

    private void onTownSelected()
    {
        String selectedTown = (String) townDropdown.getSelectedItem();
        if (selectedTown == null) return;

        TownNode town = shopDatabase.getTownByName(selectedTown);
        if (town != null && town.getDescription() != null && !town.getDescription().isEmpty())
        {
            // TODO SidebarMetrics: fold this 175px wrap width into ui.theme.SidebarMetrics.WRAP_WIDTH
            townDescLabel.setText("<html><body style='width: 175px; color: #9c9c9c;'>" + town.getDescription() + "</body></html>");
            townDescLabel.setVisible(true);
        }
        else
        {
            townDescLabel.setVisible(false);
        }

        rebuildShopList();
    }

    private void rebuildShopList()
    {
        shopListContainer.removeAll();
        tourHighlight.clear();
        tourFirstCard = null;
        tourFirstMapBtn = null;
        String selectedTown = (String) townDropdown.getSelectedItem();
        if (selectedTown == null) return;

        List<Shop> shops = shopDatabase.getShopsByTown(selectedTown);
        String category = (String) categoryDropdown.getSelectedItem();

        for (Shop shop : shops)
        {
            if (!matchesCategory(shop, category)) continue;

            JPanel card = buildShopCard(shop);
            if (tourFirstCard == null)
            {
                tourFirstCard = card;
            }
            shopListContainer.add(card);
            shopListContainer.add(Box.createRigidArea(new Dimension(0, 3)));
        }

        if (shopListContainer.getComponentCount() == 0)
        {
            JLabel emptyLabel = new JLabel("No shops found in this category.");
            emptyLabel.setFont(FontManager.getRunescapeSmallFont());
            emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            emptyLabel.setBorder(new EmptyBorder(10, 6, 6, 6));
            shopListContainer.add(emptyLabel);
        }

        shopListContainer.revalidate();
        shopListContainer.repaint();
    }

    private JPanel buildShopCard(Shop shop)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(3, 4, 3, 4)
        ));
        card.setAlignmentX(LEFT_ALIGNMENT);

        // Top Row: Portrait (24x24) + Title/Keeper
        JPanel topRow = new JPanel(new BorderLayout(5, 0));
        topRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        topRow.setAlignmentX(LEFT_ALIGNMENT);

        JLabel portraitLabel = new JLabel();
        portraitLabel.setPreferredSize(new Dimension(24, 24));
        portraitLabel.setMinimumSize(new Dimension(24, 24));
        portraitLabel.setMaximumSize(new Dimension(24, 24));
        portraitLabel.setHorizontalAlignment(JLabel.CENTER);
        portraitLabel.setVerticalAlignment(JLabel.CENTER);
        npcPortraitManager.loadNpcPortrait(shop.getNpcName(), 24, portraitLabel);
        topRow.add(portraitLabel, BorderLayout.WEST);

        JPanel infoCol = new JPanel();
        infoCol.setLayout(new BoxLayout(infoCol, BoxLayout.Y_AXIS));
        infoCol.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Name capped to 165px (HTML width, matching the stock-preview label convention below) so a long
        // wiki-sourced shop name (e.g. "Justine's stuff for the Last Shopper Standing", 45 chars) wraps
        // within the card instead of inflating the card's preferred width and pushing the button row
        // out of the (HORIZONTAL_SCROLLBAR_NEVER) viewport.
        JLabel shopNameLabel = new JLabel("<html><body style='width: 165px;'>" + shop.getName() + "</body></html>");
        shopNameLabel.setFont(FontManager.getRunescapeBoldFont());
        shopNameLabel.setForeground(TITLE_COLOR);
        shopNameLabel.setAlignmentX(LEFT_ALIGNMENT);
        shopNameLabel.setToolTipText(shop.getName());
        infoCol.add(shopNameLabel);

        String cleanKeeper = shop.getNpcName().replace("[", "").replace("]", "").trim();
        String keeperText = cleanKeeper + " (" + (shop.isMembersOnly() ? "P2P" : "F2P") + ")";
        JLabel keeperLabel = new JLabel("<html><body style='width: 165px;'>" + keeperText + "</body></html>");
        keeperLabel.setFont(FontManager.getRunescapeSmallFont());
        keeperLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        keeperLabel.setAlignmentX(LEFT_ALIGNMENT);
        keeperLabel.setToolTipText(keeperText);
        infoCol.add(keeperLabel);

        topRow.add(infoCol, BorderLayout.CENTER);
        card.add(topRow);
        card.add(Box.createRigidArea(new Dimension(0, 2)));

        // Quest requirement line
        if (shop.getQuestRequirement() != null && !shop.getQuestRequirement().isEmpty())
        {
            JLabel questLbl = new JLabel("[Req: " + shop.getQuestRequirement() + "]");
            questLbl.setFont(FontManager.getRunescapeSmallFont());
            questLbl.setForeground(QUEST_PURPLE);
            questLbl.setAlignmentX(LEFT_ALIGNMENT);
            questLbl.setToolTipText("Requires: " + shop.getQuestRequirement());
            card.add(questLbl);
            card.add(Box.createRigidArea(new Dimension(0, 1)));
        }

        // Stock preview: ONE truncated line so the card height stays predictable. The full item
        // list is on the tooltip and one click away in the Stock view. (A wrapping HTML label here
        // let long shops push their own button row out of the card's clipped bounds.)
        if (shop.getItems() != null && !shop.getItems().isEmpty())
        {
            StringBuilder full = new StringBuilder();
            for (int i = 0; i < shop.getItems().size(); i++)
            {
                if (i > 0) full.append(", ");
                full.append(shop.getItems().get(i).getName());
            }

            int total = shop.getItems().size();
            String head = shop.getItems().get(0).getName();
            if (total > 1)
            {
                head += ", " + shop.getItems().get(1).getName();
            }
            if (head.length() > 32)
            {
                head = head.substring(0, 31).trim() + "…";
            }
            String suffix = total > 2 ? "  +" + (total - 2) + " more" : "";

            JLabel stockPreview = new JLabel("Stock: " + head + suffix);
            stockPreview.setFont(FontManager.getRunescapeSmallFont());
            stockPreview.setForeground(new Color(160, 160, 160));
            stockPreview.setAlignmentX(LEFT_ALIGNMENT);
            stockPreview.setToolTipText("Stock: " + full);
            card.add(stockPreview);
        }

        card.add(Box.createRigidArea(new Dimension(0, 3)));

        // Action Buttons: 2-column compact grid (fits cleanly inside 205-225px)
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 3, 0));
        btnRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        btnRow.setPreferredSize(new Dimension(0, 20));
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JButton stockBtn = new JButton("Stock");
        stockBtn.setFont(FontManager.getRunescapeSmallFont());
        stockBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        stockBtn.setForeground(Color.WHITE);
        stockBtn.setMargin(new Insets(0, 2, 0, 2));
        stockBtn.setFocusPainted(false);
        stockBtn.setToolTipText("View shop inventory");
        stockBtn.addActionListener(e -> onInspectShop.accept(shop));
        btnRow.add(stockBtn);

        JButton focusBtn = new JButton("Map");
        focusBtn.setFont(FontManager.getRunescapeSmallFont());
        focusBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        focusBtn.setForeground(ACCENT_BLUE);
        focusBtn.setMargin(new Insets(0, 2, 0, 2));
        focusBtn.setFocusPainted(false);
        focusBtn.setToolTipText("Show this shop on the World Map");
        focusBtn.addActionListener(e -> onFocusShopOnMap.accept(shop));
        btnRow.add(focusBtn);
        if (tourFirstMapBtn == null)
        {
            tourFirstMapBtn = focusBtn;
        }

        card.add(btnRow);

        // Pin the card to its natural height so BoxLayout doesn't stretch it, without the old
        // fixed 90px cap that clipped taller cards (wrapped name + quest line + buttons).
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private boolean matchesCategory(Shop shop, String category)
    {
        if (category == null || "All Categories".equals(category)) return true;

        List<String> tags = shop.getTags();
        if (tags == null) return false;

        switch (category)
        {
            case "Magic & Runes": return tags.contains("magic");
            case "Archery & Ranged": return tags.contains("archery");
            case "Melee & Armour": return tags.contains("melee");
            case "General Stores": return tags.contains("general");
            case "Food & Fish": return tags.contains("food");
            case "Herblore & Farming": return tags.contains("herblore");
            case "Crafting & Clothes": return tags.contains("crafting");
            default: return true;
        }
    }

    /**
     * A JPanel that reports {@code getScrollableTracksViewportWidth() == true} so that, when placed
     * inside a JScrollPane with HORIZONTAL_SCROLLBAR_NEVER, the viewport forces this panel (and thus
     * its BoxLayout children) to the viewport's actual width instead of letting an oversized child
     * (e.g. an unconstrained-width label) silently inflate the preferred width and clip trailing
     * content out of view. Height still tracks the natural preferred size so vertical scrolling works.
     */
    private static class ScrollableContentPanel extends JPanel implements Scrollable
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
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
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
