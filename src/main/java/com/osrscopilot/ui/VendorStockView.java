package com.osrscopilot.ui;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.data.ShopLiveStockManager;
import com.osrscopilot.data.ShoppingCartManager;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.ShopItem;
import com.osrscopilot.util.NpcPortraitManager;
import com.osrscopilot.util.OsrsTeleportData;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;

@Slf4j
public class VendorStockView extends JPanel
{
    private static final Color TITLE_COLOR = new Color(255, 152, 31);
    private static final Color RESTRICTED_COLOR = new Color(239, 68, 68);
    private static final Color ZERO_STOCK_COLOR = new Color(156, 163, 175);
    private static final Color PRICE_COLOR = new Color(255, 215, 0);
    private static final Color QUEST_PURPLE = new Color(187, 134, 252);
    private static final Color QUEST_BG = new Color(45, 15, 60);
    private static final Color LIVE_GREEN = new Color(52, 211, 153);
    private static final Color TELEPORT_CYAN = new Color(90, 200, 250);
    private static final Color CART_GOLD = new Color(255, 215, 0);

    private final ItemManager itemManager;
    private final NpcPortraitManager npcPortraitManager;
    private final ShopLiveStockManager liveStockManager;
    private final ShoppingCartManager cartManager;
    private final OsrsCopilotConfig config;
    private final Consumer<Shop> onFocusShopOnMap;
    private final Runnable onBackToTownHub;

    private Shop currentShop;
    private boolean ironmanFilterActive = false;
    private boolean isTableView = false;
    private final List<ShopItem> displayedItems = new ArrayList<>();

    private final JLabel titleLabel = new JLabel();
    private final JLabel metaLabel = new JLabel();
    private final JLabel teleportLabel = new JLabel();
    private final JLabel questLabel = new JLabel();
    private final JPanel descPanel = new JPanel();
    private final JButton descToggleBtn = new JButton("Info");
    private final JLabel descLabel = new JLabel();
    private boolean isDescExpanded = false;
    private final JLabel liveSyncLabel = new JLabel();
    private final JLabel keeperPortrait = new JLabel();
    private final JPanel heroPanel = new JPanel(new BorderLayout(8, 0));
    private final JPanel questPanel = new JPanel(new BorderLayout());
    private final JPanel actionRow = new JPanel(new GridLayout(1, 2, 4, 0));
    private final JPanel filterControls = new JPanel(new BorderLayout(4, 0));
    private final JPanel contentHolder = new JPanel(new BorderLayout());
    private final JPanel itemsContainer = new JPanel();
    private final JTable stockTable = new JTable();
    private final JScrollPane tableScrollPane;
    private final JScrollPane cardScrollPane;

    private final JCheckBox ironmanToggle = new JCheckBox("Hide 0-Stock (Ironman Unbuyable)");
    private final JComboBox<String> sortDropdown = new JComboBox<>();
    private final JButton viewToggleBtn = new JButton("Table");

    // Interactive tutorial.
    private final com.osrscopilot.combat.tutorial.TourHighlight tourHighlight =
        new com.osrscopilot.combat.tutorial.TourHighlight();
    private JButton tourMapBtn;

    public VendorStockView(
        ItemManager itemManager,
        NpcPortraitManager npcPortraitManager,
        ShopLiveStockManager liveStockManager,
        OsrsCopilotConfig config,
        Consumer<Shop> onFocusShopOnMap,
        Runnable onBackToTownHub)
    {
        this(itemManager, npcPortraitManager, liveStockManager, config, ShoppingCartManager.getInstance(), onFocusShopOnMap, onBackToTownHub);
    }

    public VendorStockView(
        ItemManager itemManager,
        NpcPortraitManager npcPortraitManager,
        ShopLiveStockManager liveStockManager,
        OsrsCopilotConfig config,
        ShoppingCartManager cartManager,
        Consumer<Shop> onFocusShopOnMap,
        Runnable onBackToTownHub)
    {
        this.itemManager = itemManager;
        this.npcPortraitManager = npcPortraitManager;
        this.liveStockManager = liveStockManager;
        this.config = config;
        this.cartManager = cartManager != null ? cartManager : ShoppingCartManager.getInstance();
        this.onFocusShopOnMap = onFocusShopOnMap;
        this.onBackToTownHub = onBackToTownHub;

        setLayout(new BorderLayout(0, 0));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(6, 6, 6, 6));

        JButton backButton = new JButton("< Back to Towns");
        backButton.setAlignmentX(LEFT_ALIGNMENT);
        backButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        backButton.setForeground(Color.WHITE);
        backButton.setFont(FontManager.getRunescapeSmallFont());
        backButton.setFocusPainted(false);
        backButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        backButton.addActionListener(e -> onBackToTownHub.run());
        headerPanel.add(backButton);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        // Hero Card (44x44 portrait + titles)
        heroPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        heroPanel.setAlignmentX(LEFT_ALIGNMENT);

        keeperPortrait.setPreferredSize(new Dimension(44, 44));
        keeperPortrait.setMinimumSize(new Dimension(44, 44));
        keeperPortrait.setMaximumSize(new Dimension(44, 44));
        keeperPortrait.setHorizontalAlignment(JLabel.CENTER);
        keeperPortrait.setVerticalAlignment(JLabel.CENTER);
        heroPanel.add(keeperPortrait, BorderLayout.WEST);

        JPanel heroDetails = new JPanel();
        heroDetails.setLayout(new BoxLayout(heroDetails, BoxLayout.Y_AXIS));
        heroDetails.setBackground(ColorScheme.DARK_GRAY_COLOR);

        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setForeground(TITLE_COLOR);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        heroDetails.add(titleLabel);

        metaLabel.setFont(FontManager.getRunescapeSmallFont());
        metaLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        metaLabel.setAlignmentX(LEFT_ALIGNMENT);
        heroDetails.add(metaLabel);

        teleportLabel.setFont(FontManager.getRunescapeSmallFont());
        teleportLabel.setForeground(TELEPORT_CYAN);
        teleportLabel.setAlignmentX(LEFT_ALIGNMENT);
        heroDetails.add(teleportLabel);

        heroPanel.add(heroDetails, BorderLayout.CENTER);
        headerPanel.add(heroPanel);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // Quest Lock Banner
        questPanel.setBackground(QUEST_BG);
        questPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(QUEST_PURPLE, 1),
            new EmptyBorder(3, 6, 3, 6)
        ));
        questPanel.setAlignmentX(LEFT_ALIGNMENT);
        questPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        questLabel.setFont(FontManager.getRunescapeSmallFont());
        questLabel.setForeground(QUEST_PURPLE);
        questPanel.add(questLabel, BorderLayout.CENTER);
        headerPanel.add(questPanel);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // Expandable / Collapsible Description & Stock Status Panel
        descPanel.setLayout(new BoxLayout(descPanel, BoxLayout.Y_AXIS));
        descPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        descPanel.setAlignmentX(LEFT_ALIGNMENT);
        descPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        descToggleBtn.setFont(FontManager.getRunescapeSmallFont());
        descToggleBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        descToggleBtn.setForeground(new Color(170, 190, 220));
        descToggleBtn.setMargin(new Insets(1, 4, 1, 4));
        descToggleBtn.setFocusPainted(false);
        descToggleBtn.setAlignmentX(LEFT_ALIGNMENT);
        descToggleBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        descToggleBtn.setText("Info");
        descToggleBtn.setToolTipText("Expand or collapse shop description and details");
        descToggleBtn.addActionListener(e -> {
            isDescExpanded = !isDescExpanded;
            descLabel.setVisible(isDescExpanded && descLabel.getText() != null && !descLabel.getText().isEmpty());
            liveSyncLabel.setVisible(isDescExpanded);
            descToggleBtn.setText(isDescExpanded ? "Hide Info" : "Info");
            descPanel.revalidate();
            descPanel.repaint();
        });

        descLabel.setFont(FontManager.getRunescapeSmallFont());
        descLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descLabel.setAlignmentX(LEFT_ALIGNMENT);
        descLabel.setBorder(new EmptyBorder(3, 4, 1, 4));
        descLabel.setVisible(false);

        liveSyncLabel.setFont(FontManager.getRunescapeSmallFont());
        liveSyncLabel.setForeground(LIVE_GREEN);
        liveSyncLabel.setAlignmentX(LEFT_ALIGNMENT);
        liveSyncLabel.setBorder(new EmptyBorder(2, 4, 3, 4));
        liveSyncLabel.setVisible(false);

        descPanel.add(descToggleBtn);
        descPanel.add(descLabel);
        descPanel.add(liveSyncLabel);
        descPanel.setVisible(true);

        headerPanel.add(descPanel);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // Action Buttons Row: Map + Shop Wiki Link (Clean Text, No Emojis)
        actionRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        actionRow.setAlignmentX(LEFT_ALIGNMENT);
        actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JButton focusMapBtn = new JButton("Map");
        focusMapBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        focusMapBtn.setForeground(new Color(90, 200, 250));
        focusMapBtn.setFont(FontManager.getRunescapeSmallFont());
        focusMapBtn.setFocusPainted(false);
        focusMapBtn.setMargin(new Insets(1, 2, 1, 2));
        focusMapBtn.setToolTipText("Show this shop on the World Map");
        focusMapBtn.addActionListener(e -> {
            if (currentShop != null) onFocusShopOnMap.accept(currentShop);
        });
        actionRow.add(focusMapBtn);
        this.tourMapBtn = focusMapBtn;

        JButton shopWikiBtn = new JButton("Shop Wiki");
        shopWikiBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        shopWikiBtn.setForeground(new Color(255, 185, 45));
        shopWikiBtn.setFont(FontManager.getRunescapeSmallFont());
        shopWikiBtn.setFocusPainted(false);
        shopWikiBtn.setMargin(new Insets(1, 2, 1, 2));
        shopWikiBtn.addActionListener(e -> {
            if (currentShop != null) openShopWiki(currentShop.getName());
        });
        actionRow.add(shopWikiBtn);

        headerPanel.add(actionRow);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        // Sort & View controls
        filterControls.setBackground(ColorScheme.DARK_GRAY_COLOR);
        filterControls.setAlignmentX(LEFT_ALIGNMENT);
        filterControls.setPreferredSize(new Dimension(0, 22));
        filterControls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        sortDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sortDropdown.setForeground(Color.WHITE);
        sortDropdown.setFont(FontManager.getRunescapeSmallFont());
        sortDropdown.setToolTipText("Sort items by price, stock, or restock speed");
        sortDropdown.addItem("Sort: Default");
        sortDropdown.addItem("Price (Low → High)");
        sortDropdown.addItem("Price (High → Low)");
        sortDropdown.addItem("Stock (High → Low)");
        sortDropdown.addItem("Stock (Low → High)");
        sortDropdown.addItem("Name (A → Z)");
        sortDropdown.addItem("Restock Speed");
        sortDropdown.addActionListener(e -> rebuildItems());
        filterControls.add(sortDropdown, BorderLayout.CENTER);

        viewToggleBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        viewToggleBtn.setForeground(Color.WHITE);
        viewToggleBtn.setFont(FontManager.getRunescapeSmallFont());
        viewToggleBtn.setFocusPainted(false);
        viewToggleBtn.setMargin(new Insets(1, 2, 1, 2));
        viewToggleBtn.setPreferredSize(new Dimension(50, 22));
        viewToggleBtn.setMinimumSize(new Dimension(50, 22));
        viewToggleBtn.setMaximumSize(new Dimension(50, 22));
        viewToggleBtn.setText("Table");
        viewToggleBtn.setToolTipText("Toggle between item cards and spreadsheet table view");
        viewToggleBtn.addActionListener(e -> {
            isTableView = !isTableView;
            viewToggleBtn.setText(isTableView ? "Cards" : "Table");
            rebuildItems();
        });
        filterControls.add(viewToggleBtn, BorderLayout.EAST);

        headerPanel.add(filterControls);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        ironmanToggle.setBackground(ColorScheme.DARK_GRAY_COLOR);
        ironmanToggle.setForeground(Color.WHITE);
        ironmanToggle.setFont(FontManager.getRunescapeSmallFont());
        ironmanToggle.setAlignmentX(LEFT_ALIGNMENT);
        ironmanToggle.setFocusPainted(false);
        ironmanToggle.setToolTipText("Hides items with 0 default stock which Ironmen cannot buy in OSRS, and minigame-blocked items.");
        ironmanToggle.addActionListener(e -> {
            ironmanFilterActive = ironmanToggle.isSelected();
            rebuildItems();
        });
        headerPanel.add(ironmanToggle);

        add(headerPanel, BorderLayout.NORTH);

        // Content Area (Cards or Table)
        itemsContainer.setLayout(new BoxLayout(itemsContainer, BoxLayout.Y_AXIS));
        itemsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel listWrapper = new ScrollableContentPanel();
        listWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listWrapper.add(itemsContainer, BorderLayout.NORTH);

        cardScrollPane = new JScrollPane(listWrapper);
        cardScrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        cardScrollPane.setBorder(null);
        cardScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        cardScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // Setup Table
        stockTable.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        stockTable.setForeground(Color.WHITE);
        stockTable.setFont(FontManager.getRunescapeSmallFont());
        stockTable.setRowHeight(24);
        stockTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stockTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        stockTable.getTableHeader().setBackground(ColorScheme.DARK_GRAY_COLOR);
        stockTable.getTableHeader().setForeground(TITLE_COLOR);
        stockTable.getTableHeader().setFont(FontManager.getRunescapeBoldFont());
        stockTable.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                handleTableClick(e);
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                handleTableClick(e);
            }

            private void handleTableClick(MouseEvent e)
            {
                int row = stockTable.rowAtPoint(e.getPoint());
                if (row >= 0 && row < displayedItems.size())
                {
                    stockTable.setRowSelectionInterval(row, row);
                    ShopItem item = displayedItems.get(row);

                    if (e.isPopupTrigger())
                    {
                        JPopupMenu popup = createItemContextMenu(item);
                        popup.show(e.getComponent(), e.getX(), e.getY());
                    }
                    else if (e.getID() == MouseEvent.MOUSE_CLICKED && e.getClickCount() == 1 && !e.isConsumed())
                    {
                        // Single click on table opens wiki
                        openWikiPage(item.getName());
                    }
                }
            }
        });

        tableScrollPane = new JScrollPane(stockTable);
        tableScrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tableScrollPane.setBorder(null);
        tableScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        contentHolder.add(cardScrollPane, BorderLayout.CENTER);
        add(contentHolder, BorderLayout.CENTER);

        // Start on the "pick a shop" placeholder so the first visit to the Shops tab isn't a
        // header of dead controls over an empty stripe.
        setShop(null);
    }

    /**
     * Fills the item list area with a "pick a shop" placeholder - tells a first-time user the Shops
     * tab is driven from elsewhere (Towns / Search / the map) rather than leaving them staring at an
     * empty pane with an inert filter row. Rendered inside the normal card scroll pane.
     */
    private void showEmptyState()
    {
        itemsContainer.removeAll();
        itemsContainer.add(Box.createRigidArea(new Dimension(0, 44)));

        JLabel heading = new JLabel("No shop selected");
        heading.setFont(FontManager.getRunescapeBoldFont());
        heading.setForeground(TITLE_COLOR);
        heading.setAlignmentX(CENTER_ALIGNMENT);
        itemsContainer.add(heading);
        itemsContainer.add(Box.createRigidArea(new Dimension(0, 8)));

        JLabel body = new JLabel("<html><body style='width: 180px; text-align: center; color: #b8b8b8;'>"
            + "Open a shop from the <b>Towns</b> tab, the item <b>Search</b> tab, or a vendor pin on the "
            + "world map to see its stock, prices and restock timers here.</body></html>");
        body.setFont(FontManager.getRunescapeSmallFont());
        body.setAlignmentX(CENTER_ALIGNMENT);
        itemsContainer.add(body);
        itemsContainer.add(Box.createRigidArea(new Dimension(0, 12)));

        JButton toTowns = new JButton("Browse Towns");
        toTowns.setFont(FontManager.getRunescapeSmallFont());
        toTowns.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        toTowns.setForeground(new Color(90, 200, 250));
        toTowns.setFocusPainted(false);
        toTowns.setAlignmentX(CENTER_ALIGNMENT);
        toTowns.setMaximumSize(new Dimension(130, 24));
        toTowns.addActionListener(e -> onBackToTownHub.run());
        itemsContainer.add(toTowns);
    }

    /** Shows/hides the whole shop-specific header stack in one call. */
    private void setShopChromeVisible(boolean visible)
    {
        heroPanel.setVisible(visible);
        descPanel.setVisible(visible);
        actionRow.setVisible(visible);
        filterControls.setVisible(visible);
        ironmanToggle.setVisible(visible);
        if (!visible)
        {
            questPanel.setVisible(false);
        }
    }

    /**
     * Interactive tutorial: ring one part of this tab. Keys: {@code hero}, {@code map} (the Map
     * button), {@code filters}, {@code items}. {@code null} clears. EDT only.
     */
    public void tourFocus(String key)
    {
        switch (key == null ? "" : key)
        {
            case "hero":    tourHighlight.set(heroPanel); break;
            case "map":     tourHighlight.set(tourMapBtn); break;
            case "filters": tourHighlight.set(filterControls); break;
            case "items":
                tourHighlight.set(itemsContainer);
                com.osrscopilot.combat.tutorial.TourHighlight.scrollIntoView(itemsContainer);
                break;
            default:        tourHighlight.clear(); break;
        }
    }

    public void setShop(Shop shop)
    {
        this.currentShop = shop;
        tourHighlight.clear();
        if (shop == null)
        {
            titleLabel.setText("No Shop Selected");
            metaLabel.setText("");
            teleportLabel.setText("");
            teleportLabel.setToolTipText(null);
            descLabel.setText("");
            liveSyncLabel.setText("");
            keeperPortrait.setIcon(null);
            displayedItems.clear();
            setShopChromeVisible(false);
            contentHolder.removeAll();
            contentHolder.add(cardScrollPane, BorderLayout.CENTER);
            showEmptyState();
            contentHolder.revalidate();
            contentHolder.repaint();
            itemsContainer.revalidate();
            itemsContainer.repaint();
            return;
        }

        setShopChromeVisible(true);
        titleLabel.setText(shop.getName());
        metaLabel.setText("Keeper: " + shop.getNpcName() + " (" + shop.getTown() + ") | " + (shop.isMembersOnly() ? "Members" : "F2P"));

        String primaryTp = OsrsTeleportData.getPrimaryTeleport(shop.getTown());
        if (primaryTp != null)
        {
            teleportLabel.setText("Travel: " + primaryTp);
            String fullTp = OsrsTeleportData.getNearestTeleport(shop.getTown());
            if (fullTp != null)
            {
                teleportLabel.setToolTipText("Fast travel options: " + fullTp);
            }
            teleportLabel.setVisible(true);
        }
        else
        {
            teleportLabel.setVisible(false);
            teleportLabel.setToolTipText(null);
        }

        if (liveStockManager.hasLiveStock(shop))
        {
            long elapsedSec = (System.currentTimeMillis() - liveStockManager.getLastSyncTime(shop)) / 1000;
            liveSyncLabel.setText("• Live in-game stock synced (" + (elapsedSec < 60 ? elapsedSec + "s ago" : (elapsedSec / 60) + "m ago") + ")");
        }
        else
        {
            liveSyncLabel.setText("• Base default stock (syncs live when opened in-game)");
        }
        liveSyncLabel.setVisible(isDescExpanded);

        if (shop.getQuestRequirement() != null && !shop.getQuestRequirement().isEmpty())
        {
            questLabel.setText("Unlock: " + shop.getQuestRequirement());
            questPanel.setVisible(true);
        }
        else
        {
            questPanel.setVisible(false);
        }

        if (shop.getDescription() != null && !shop.getDescription().trim().isEmpty())
        {
            descLabel.setText("<html><body style='width: 175px; color: #a8a8a8;'>" + shop.getDescription() + "</body></html>");
            descLabel.setVisible(isDescExpanded);
        }
        else
        {
            descLabel.setText("");
            descLabel.setVisible(false);
        }

        descToggleBtn.setText(isDescExpanded ? "Hide Info" : "Info");
        descPanel.setVisible(true);

        npcPortraitManager.loadNpcPortrait(shop.getNpcName(), 44, keeperPortrait);
        rebuildItems();
    }

    private void rebuildItems()
    {
        itemsContainer.removeAll();
        displayedItems.clear();

        if (currentShop == null || currentShop.getItems() == null)
        {
            itemsContainer.revalidate();
            itemsContainer.repaint();
            return;
        }

        List<ShopItem> filtered = new ArrayList<>();
        for (ShopItem item : currentShop.getItems())
        {
            boolean isZeroStock = item.isZeroDefaultStock() || item.getDefaultStock() <= 0;
            boolean isBlocked = item.isIronmanBlocked();

            if (ironmanFilterActive || config.hideZeroStock())
            {
                if (isZeroStock) continue;
            }
            if (ironmanFilterActive || config.hideIronmanRestricted())
            {
                if (isBlocked) continue;
            }
            filtered.add(item);
        }

        // Apply Sorting
        String sortChoice = (String) sortDropdown.getSelectedItem();
        if (sortChoice != null)
        {
            if (sortChoice.contains("Price (Low → High)"))
            {
                filtered.sort(Comparator.comparingInt(ShopItem::getPrice));
            }
            else if (sortChoice.contains("Price (High → Low)"))
            {
                filtered.sort((a, b) -> Integer.compare(b.getPrice(), a.getPrice()));
            }
            else if (sortChoice.contains("Stock (High → Low)"))
            {
                filtered.sort((a, b) -> Integer.compare(b.getDefaultStock(), a.getDefaultStock()));
            }
            else if (sortChoice.contains("Stock (Low → High)"))
            {
                filtered.sort(Comparator.comparingInt(ShopItem::getDefaultStock));
            }
            else if (sortChoice.contains("Name"))
            {
                filtered.sort(Comparator.comparing(ShopItem::getName));
            }
            else if (sortChoice.contains("Restock Speed"))
            {
                filtered.sort(Comparator.comparingInt(ShopItem::getRestockTimeSeconds));
            }
        }

        displayedItems.addAll(filtered);
        contentHolder.removeAll();

        if (isTableView)
        {
            // 3-Column Compact Table for 225px width
            String currName = currentShop.getCurrency().getShortName();
            String[] colNames = {"Item Name", "Stock", "Price (" + currName + ")"};
            DefaultTableModel model = new DefaultTableModel(colNames, 0)
            {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };

            for (ShopItem item : filtered)
            {
                Integer liveCount = liveStockManager.getLiveStock(currentShop, item.getItemId());
                String stockStr = (liveCount != null) ? liveCount + " (live)" : String.valueOf(item.getDefaultStock());
                model.addRow(new Object[]{
                    item.getName(),
                    stockStr,
                    item.getPrice()
                });
            }

            stockTable.setModel(model);
            stockTable.getColumnModel().getColumn(0).setPreferredWidth(95);
            stockTable.getColumnModel().getColumn(1).setPreferredWidth(40);
            stockTable.getColumnModel().getColumn(2).setPreferredWidth(55);

            DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
            centerRenderer.setHorizontalAlignment(JLabel.CENTER);
            stockTable.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
            stockTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);

            contentHolder.add(tableScrollPane, BorderLayout.CENTER);
        }
        else
        {
            // Clean 2-Line Card View (no text cutoffs, robust 32x32 icon & stack count rendering)
            for (ShopItem item : filtered)
            {
                boolean isZeroStock = item.isZeroDefaultStock() || item.getDefaultStock() <= 0;
                boolean isBlocked = item.isIronmanBlocked();

                JPanel itemRow = new JPanel(new BorderLayout(5, 0));
                itemRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                itemRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
                itemRow.setPreferredSize(new Dimension(0, 54));
                itemRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                Integer liveStock = liveStockManager.getLiveStock(currentShop, item.getItemId());
                String stockDisplay = (liveStock != null) ? "Live: " + liveStock : "Stock: " + item.getDefaultStock();
                String currName = currentShop.getCurrency().getShortName();
                String priceFormatted = String.format("%,d", item.getPrice()) + " " + currName;

                itemRow.setToolTipText("<html><b>" + item.getName() + "</b><br>" + stockDisplay + " | Price: " + priceFormatted + " | Restock: " + item.getRestockTimeSeconds() + "s<br><span style='color: #ffd700;'>Right-click to Add to Shopping Cart</span> • <span style='color: #90caf9;'>Click for Wiki ↗</span></html>");
                itemRow.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                    new EmptyBorder(3, 4, 3, 4)
                ));

                JPopupMenu itemPopup = createItemContextMenu(item);

                itemRow.addMouseListener(new MouseAdapter()
                {
                    @Override
                    public void mouseEntered(MouseEvent e)
                    {
                        itemRow.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
                    }

                    @Override
                    public void mouseExited(MouseEvent e)
                    {
                        itemRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    }

                    @Override
                    public void mousePressed(MouseEvent e)
                    {
                        if (e.isPopupTrigger())
                        {
                            itemPopup.show(itemRow, e.getX(), e.getY());
                        }
                    }

                    @Override
                    public void mouseReleased(MouseEvent e)
                    {
                        if (e.isPopupTrigger())
                        {
                            itemPopup.show(itemRow, e.getX(), e.getY());
                        }
                    }

                    @Override
                    public void mouseClicked(MouseEvent e)
                    {
                        if (e.getButton() == MouseEvent.BUTTON1)
                        {
                            openWikiPage(item.getName());
                        }
                    }
                });

                // 36x36 Icon Container to prevent quantity clipping on left/bottom
                JPanel iconContainer = new JPanel(new BorderLayout());
                iconContainer.setOpaque(false);
                iconContainer.setPreferredSize(new Dimension(36, 36));
                iconContainer.setMinimumSize(new Dimension(36, 36));
                iconContainer.setMaximumSize(new Dimension(36, 36));

                JLabel iconLabel = new JLabel();
                iconLabel.setHorizontalAlignment(JLabel.CENTER);
                iconLabel.setVerticalAlignment(JLabel.CENTER);
                if (itemManager != null)
                {
                    AsyncBufferedImage img = itemManager.getImage(item.getItemId(), item.getDefaultStock(), item.getDefaultStock() > 1);
                    img.addTo(iconLabel);
                }
                iconContainer.add(iconLabel, BorderLayout.CENTER);
                itemRow.add(iconContainer, BorderLayout.WEST);

                // 3-Line Details Panel (Clean, unclipped stock, buy, and sell prices)
                JPanel detailsPanel = new JPanel(new GridLayout(3, 1, 0, 0));
                detailsPanel.setOpaque(false);

                // Line 1: Item Name + Status Badge
                JPanel nameRow = new JPanel(new BorderLayout(4, 0));
                nameRow.setOpaque(false);

                // Single line, Swing-ellipsised to whatever width the row gives it (full name on the
                // tooltip). The old HTML width:125px made long wiki names (up to ~45 chars) WRAP to
                // two lines, which the fixed-height 3-row GridLayout below then clipped -- the name
                // visibly overran the "Stock / Restock" line.
                JLabel nameLabel = new JLabel(item.getName());
                nameLabel.setFont(FontManager.getRunescapeBoldFont());
                nameLabel.setForeground(isZeroStock ? ZERO_STOCK_COLOR : Color.WHITE);
                nameLabel.setToolTipText(item.getName());
                nameLabel.setMinimumSize(new Dimension(10, nameLabel.getPreferredSize().height));
                nameRow.add(nameLabel, BorderLayout.CENTER);

                if (isBlocked)
                {
                    JLabel badge = new JLabel("[Restricted]");
                    badge.setFont(FontManager.getRunescapeSmallFont());
                    badge.setForeground(RESTRICTED_COLOR);
                    nameRow.add(badge, BorderLayout.EAST);
                }
                else if (isZeroStock)
                {
                    JLabel badge = new JLabel("[0 Stock]");
                    badge.setFont(FontManager.getRunescapeSmallFont());
                    badge.setForeground(ZERO_STOCK_COLOR);
                    nameRow.add(badge, BorderLayout.EAST);
                }
                detailsPanel.add(nameRow);

                // Line 2: Stock • Restock
                JLabel stockLabel = new JLabel(stockDisplay + " • Restock " + item.getRestockTimeSeconds() + "s");
                stockLabel.setFont(FontManager.getRunescapeSmallFont());
                stockLabel.setForeground(isZeroStock ? ZERO_STOCK_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
                detailsPanel.add(stockLabel);

                // Line 3: Buy: X gp | Sell: Y gp
                String buySellText = "Buy: " + priceFormatted + (item.getEffectiveBuyPrice() > 0 ? " | Sell: " + String.format("%,d", item.getEffectiveBuyPrice()) + " " + currName : "");
                JLabel priceLabel = new JLabel(buySellText);
                priceLabel.setFont(FontManager.getRunescapeSmallFont());
                priceLabel.setForeground(PRICE_COLOR);
                detailsPanel.add(priceLabel);

                itemRow.add(detailsPanel, BorderLayout.CENTER);

                // Fast "+" Action Button on Row End (never truncates)
                JButton addCartBtn = new JButton("+");
                addCartBtn.setFont(FontManager.getRunescapeBoldFont());
                addCartBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
                addCartBtn.setForeground(CART_GOLD);
                addCartBtn.setFocusPainted(false);
                addCartBtn.setMargin(new Insets(0, 0, 0, 0));
                addCartBtn.setPreferredSize(new Dimension(22, 26));
                addCartBtn.setToolTipText("Add to Shopping Cart (Right-click for quantities)");
                addCartBtn.addActionListener(e -> {
                    if (currentShop != null)
                    {
                        cartManager.addItem(item, currentShop, 1);
                    }
                });
                addCartBtn.addMouseListener(new MouseAdapter()
                {
                    @Override
                    public void mousePressed(MouseEvent e)
                    {
                        if (e.isPopupTrigger())
                        {
                            itemPopup.show(addCartBtn, e.getX(), e.getY());
                        }
                    }

                    @Override
                    public void mouseReleased(MouseEvent e)
                    {
                        if (e.isPopupTrigger())
                        {
                            itemPopup.show(addCartBtn, e.getX(), e.getY());
                        }
                    }
                });
                itemRow.add(addCartBtn, BorderLayout.EAST);

                itemsContainer.add(itemRow);
            }

            if (itemsContainer.getComponentCount() == 0)
            {
                JLabel emptyLabel = new JLabel("No matching items found with current filters.");
                emptyLabel.setFont(FontManager.getRunescapeSmallFont());
                emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                emptyLabel.setBorder(new EmptyBorder(10, 6, 6, 6));
                itemsContainer.add(emptyLabel);
            }

            contentHolder.add(cardScrollPane, BorderLayout.CENTER);
        }

        contentHolder.revalidate();
        contentHolder.repaint();
    }

    private JPopupMenu createItemContextMenu(ShopItem item)
    {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem add1 = new JMenuItem("Add to Cart (1)");
        add1.addActionListener(e -> {
            if (currentShop != null) cartManager.addItem(item, currentShop, 1);
        });
        menu.add(add1);

        JMenuItem add5 = new JMenuItem("Add to Cart (5)");
        add5.addActionListener(e -> {
            if (currentShop != null) cartManager.addItem(item, currentShop, 5);
        });
        menu.add(add5);

        JMenuItem add10 = new JMenuItem("Add to Cart (10)");
        add10.addActionListener(e -> {
            if (currentShop != null) cartManager.addItem(item, currentShop, 10);
        });
        menu.add(add10);

        JMenuItem add50 = new JMenuItem("Add to Cart (50)");
        add50.addActionListener(e -> {
            if (currentShop != null) cartManager.addItem(item, currentShop, 50);
        });
        menu.add(add50);

        JMenuItem addAll = new JMenuItem("Add All (Stock)");
        addAll.addActionListener(e -> {
            if (currentShop != null)
            {
                Integer live = liveStockManager.getLiveStock(currentShop, item.getItemId());
                int stock = (live != null && live > 0) ? live : (item.getDefaultStock() > 0 ? item.getDefaultStock() : 1);
                cartManager.addItem(item, currentShop, stock);
            }
        });
        menu.add(addAll);

        JMenuItem addX = new JMenuItem("Add X...");
        addX.addActionListener(e -> {
            if (currentShop == null) return;
            String input = JOptionPane.showInputDialog(
                this,
                "Enter quantity to add to cart for " + item.getName() + ":",
                "Add to Cart",
                JOptionPane.PLAIN_MESSAGE
            );
            if (input != null && !input.trim().isEmpty())
            {
                try
                {
                    int qty = Integer.parseInt(input.trim().replace(",", ""));
                    if (qty > 0)
                    {
                        cartManager.addItem(item, currentShop, qty);
                    }
                }
                catch (NumberFormatException nfe)
                {
                    log.debug("Ignoring non-numeric cart quantity input", nfe);
                }
            }
        });
        menu.add(addX);

        menu.addSeparator();

        JMenuItem wiki = new JMenuItem("Open OSRS Wiki ↗");
        wiki.addActionListener(e -> openWikiPage(item.getName()));
        menu.add(wiki);

        return menu;
    }

    private void openWikiPage(String itemName)
    {
        try
        {
            String url = "https://oldschool.runescape.wiki/w/Special:Search?search=" +
                URLEncoder.encode(itemName, StandardCharsets.UTF_8.name());
            LinkBrowser.browse(url);
        }
        catch (Exception ex)
        {
            LinkBrowser.browse("https://oldschool.runescape.wiki/w/" + itemName.replace(' ', '_'));
        }
    }

    private void openShopWiki(String shopName)
    {
        try
        {
            String url = "https://oldschool.runescape.wiki/w/" +
                URLEncoder.encode(shopName.replace(' ', '_'), StandardCharsets.UTF_8.name()) + ".";
            LinkBrowser.browse(url);
        }
        catch (Exception ex)
        {
            LinkBrowser.browse("https://oldschool.runescape.wiki/w/" + shopName.replace(' ', '_'));
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
