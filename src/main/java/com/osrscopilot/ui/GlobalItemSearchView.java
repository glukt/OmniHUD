package com.osrscopilot.ui;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.ShoppingCartManager;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterDrop;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.ShopItem;
import com.osrscopilot.util.NpcPortraitManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

@Slf4j
public class GlobalItemSearchView extends JPanel
{
    private static final Color TITLE_COLOR = new Color(255, 152, 31);
    private static final Color ACCENT_BLUE = new Color(90, 200, 250);
    private static final Color STOCK_COLOR = new Color(16, 185, 129);
    private static final Color CHEAPEST_GOLD = new Color(255, 215, 0);
    private static final Color ZERO_STOCK_COLOR = new Color(156, 163, 175);
    private static final Color DROP_SOURCE_COLOR = new Color(236, 72, 153);
    private static final Color SECTION_HEADER_COLOR = new Color(255, 185, 45);

    public enum SearchFilterMode
    {
        ALL,
        DROPS_ONLY,
        SHOPS_ONLY
    }

    private final ShopDatabase shopDatabase;
    private final MonsterDatabase monsterDatabase;
    private final NpcPortraitManager npcPortraitManager;
    private final ShoppingCartManager cartManager;
    private final OsrsCopilotConfig config;
    private final Consumer<Shop> onInspectShop;
    private final Consumer<Shop> onFocusShopOnMap;
    private final java.util.function.BiConsumer<Monster, com.osrscopilot.data.model.MonsterSpawnZone> onFocusMonsterZoneOnMap;
    private final Consumer<Monster> onViewMonsterDetail;
    private final Runnable onOpenSpreadsheet;

    private SearchFilterMode currentFilterMode = SearchFilterMode.ALL;
    private final JButton filterAllBtn = new JButton("All");
    private final JButton filterDropsBtn = new JButton("Drops");
    private final JButton filterShopsBtn = new JButton("Shops");

    private final JTextField searchField = new JTextField();
    private final JComboBox<String> sortDropdown = new JComboBox<>();
    private final JPanel resultsContainer = new JPanel();
    private final JLabel statusLabel = new JLabel("Type item name (e.g. 'dragon harpoon', 'hammer')");

    private final com.osrscopilot.combat.tutorial.TourHighlight tourHighlight =
        new com.osrscopilot.combat.tutorial.TourHighlight();

    private static final int SEARCH_RENDER_LIMIT = 60;
    private final javax.swing.Timer searchDebounceTimer;
    private List<SearchResultEntry> pendingShopEntries = Collections.emptyList();
    private List<MonsterDropResultEntry> pendingMonsterEntries = Collections.emptyList();
    private int renderedShopCount = 0;
    private int renderedMonsterCount = 0;
    private JPanel showMoreRow;

    public GlobalItemSearchView(
        ShopDatabase shopDatabase,
        NpcPortraitManager npcPortraitManager,
        OsrsCopilotConfig config,
        Consumer<Shop> onInspectShop,
        Consumer<Shop> onFocusShopOnMap,
        Runnable onOpenSpreadsheet)
    {
        this(shopDatabase, null, npcPortraitManager, config, ShoppingCartManager.getInstance(), onInspectShop, onFocusShopOnMap, null, null, onOpenSpreadsheet);
    }

    public GlobalItemSearchView(
        ShopDatabase shopDatabase,
        NpcPortraitManager npcPortraitManager,
        OsrsCopilotConfig config,
        ShoppingCartManager cartManager,
        Consumer<Shop> onInspectShop,
        Consumer<Shop> onFocusShopOnMap,
        Runnable onOpenSpreadsheet)
    {
        this(shopDatabase, null, npcPortraitManager, config, cartManager, onInspectShop, onFocusShopOnMap, null, null, onOpenSpreadsheet);
    }

    public GlobalItemSearchView(
        ShopDatabase shopDatabase,
        MonsterDatabase monsterDatabase,
        NpcPortraitManager npcPortraitManager,
        OsrsCopilotConfig config,
        ShoppingCartManager cartManager,
        Consumer<Shop> onInspectShop,
        Consumer<Shop> onFocusShopOnMap,
        Consumer<Monster> onViewMonsterDetail,
        Runnable onOpenSpreadsheet)
    {
        this(shopDatabase, monsterDatabase, npcPortraitManager, config, cartManager, onInspectShop, onFocusShopOnMap, null, onViewMonsterDetail, onOpenSpreadsheet);
    }

    public GlobalItemSearchView(
        ShopDatabase shopDatabase,
        MonsterDatabase monsterDatabase,
        NpcPortraitManager npcPortraitManager,
        OsrsCopilotConfig config,
        ShoppingCartManager cartManager,
        Consumer<Shop> onInspectShop,
        Consumer<Shop> onFocusShopOnMap,
        java.util.function.BiConsumer<Monster, com.osrscopilot.data.model.MonsterSpawnZone> onFocusMonsterZoneOnMap,
        Consumer<Monster> onViewMonsterDetail,
        Runnable onOpenSpreadsheet)
    {
        this.shopDatabase = shopDatabase;
        this.monsterDatabase = monsterDatabase;
        this.npcPortraitManager = npcPortraitManager;
        this.config = config;
        this.cartManager = cartManager != null ? cartManager : ShoppingCartManager.getInstance();
        this.onInspectShop = onInspectShop;
        this.onFocusShopOnMap = onFocusShopOnMap;
        this.onFocusMonsterZoneOnMap = onFocusMonsterZoneOnMap;
        this.onViewMonsterDetail = onViewMonsterDetail;
        this.onOpenSpreadsheet = onOpenSpreadsheet;

        this.searchDebounceTimer = new javax.swing.Timer(180, e -> performSearch(searchField.getText().trim()));
        this.searchDebounceTimer.setRepeats(false);

        setLayout(new BorderLayout(0, 0));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(6, 6, 6, 6));

        JLabel searchHeader = new JLabel("Global Item & Drop Search");
        searchHeader.setFont(FontManager.getRunescapeBoldFont());
        searchHeader.setForeground(TITLE_COLOR);
        searchHeader.setAlignmentX(LEFT_ALIGNMENT);
        headerPanel.add(searchHeader);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        JPanel filterRow = new JPanel(new GridLayout(1, 3, 3, 0));
        filterRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        filterRow.setAlignmentX(LEFT_ALIGNMENT);
        filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        filterAllBtn.setToolTipText("Search both monster drops and shop vendors");
        filterDropsBtn.setToolTipText("Show only monsters that drop matching items");
        filterShopsBtn.setToolTipText("Show only vendor shops stocking matching items");

        styleFilterButton(filterAllBtn, SearchFilterMode.ALL);
        styleFilterButton(filterDropsBtn, SearchFilterMode.DROPS_ONLY);
        styleFilterButton(filterShopsBtn, SearchFilterMode.SHOPS_ONLY);

        filterRow.add(filterAllBtn);
        filterRow.add(filterDropsBtn);
        filterRow.add(filterShopsBtn);
        headerPanel.add(filterRow);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        searchField.setFont(FontManager.getRunescapeFont());
        searchField.setAlignmentX(LEFT_ALIGNMENT);
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        searchField.setToolTipText("Search by item name (e.g. 'Dragon harpoon', 'Rune scimitar', 'Lobster')");
        searchField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyReleased(KeyEvent e)
            {
                searchDebounceTimer.restart();
            }
        });
        headerPanel.add(searchField);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        sortDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sortDropdown.setForeground(Color.WHITE);
        sortDropdown.setFont(FontManager.getRunescapeSmallFont());
        sortDropdown.setAlignmentX(LEFT_ALIGNMENT);
        sortDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        sortDropdown.setToolTipText("Sort results by shop price, stock, or monster combat level");
        sortDropdown.addItem("Sort: Lowest Price First");
        sortDropdown.addItem("Sort: Best Sell Price");
        sortDropdown.addItem("Sort: Highest Stock First");
        sortDropdown.addItem("Sort: Monster Combat Lv.");
        sortDropdown.addItem("Sort: Name / Town");
        sortDropdown.addActionListener(e -> performSearch(searchField.getText().trim()));
        headerPanel.add(sortDropdown);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        headerPanel.add(statusLabel);

        add(headerPanel, BorderLayout.NORTH);

        resultsContainer.setLayout(new BoxLayout(resultsContainer, BoxLayout.Y_AXIS));
        resultsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        resultsContainer.setBorder(new EmptyBorder(4, 4, 6, 4));

        JPanel listWrapper = new ScrollableContentPanel();
        listWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listWrapper.add(resultsContainer, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(listWrapper);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);

        updateFilterButtons();
    }

    private void styleFilterButton(JButton btn, SearchFilterMode mode)
    {
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(2, 4, 2, 4));
        btn.addActionListener(e -> {
            currentFilterMode = mode;
            updateFilterButtons();
            performSearch(searchField.getText().trim());
        });
    }

    private void updateFilterButtons()
    {
        filterAllBtn.setBackground(currentFilterMode == SearchFilterMode.ALL ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
        filterAllBtn.setForeground(currentFilterMode == SearchFilterMode.ALL ? SECTION_HEADER_COLOR : Color.WHITE);

        filterDropsBtn.setBackground(currentFilterMode == SearchFilterMode.DROPS_ONLY ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
        filterDropsBtn.setForeground(currentFilterMode == SearchFilterMode.DROPS_ONLY ? SECTION_HEADER_COLOR : Color.WHITE);

        filterShopsBtn.setBackground(currentFilterMode == SearchFilterMode.SHOPS_ONLY ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
        filterShopsBtn.setForeground(currentFilterMode == SearchFilterMode.SHOPS_ONLY ? SECTION_HEADER_COLOR : Color.WHITE);
    }

    /** Interactive tutorial: ring the search box ({@code "box"}) or the results ({@code "results"}); {@code null} clears. */
    public void tourFocus(String key)
    {
        switch (key == null ? "" : key)
        {
            case "box":     tourHighlight.set(searchField); break;
            case "results":
                tourHighlight.set(resultsContainer);
                com.osrscopilot.combat.tutorial.TourHighlight.scrollIntoView(resultsContainer);
                break;
            default:        tourHighlight.clear(); break;
        }
    }

    public void performSearch(String query)
    {
        resultsContainer.removeAll();
        showMoreRow = null;

        if (query.length() < 2)
        {
            statusLabel.setText("Type at least 2 characters (e.g. 'dragon harpoon', 'hammer')");
            pendingShopEntries = Collections.emptyList();
            pendingMonsterEntries = Collections.emptyList();
            renderedShopCount = 0;
            renderedMonsterCount = 0;
            resultsContainer.revalidate();
            resultsContainer.repaint();
            return;
        }

        String qLower = query.toLowerCase(Locale.ROOT).trim();

        List<MonsterDropResultEntry> monsterResults = new ArrayList<>();
        if (currentFilterMode != SearchFilterMode.SHOPS_ONLY && monsterDatabase != null && monsterDatabase.isLoaded())
        {
            List<Monster> matchedMonsters = monsterDatabase.searchMonstersByDropName(query);
            for (Monster m : matchedMonsters)
            {
                List<MonsterDrop> matchingDrops = new ArrayList<>();
                if (m.getDrops() != null)
                {
                    for (MonsterDrop drop : m.getDrops())
                    {
                        if (drop.getName() != null && drop.getName().toLowerCase(Locale.ROOT).contains(qLower))
                        {
                            matchingDrops.add(drop);
                        }
                    }
                }
                if (!matchingDrops.isEmpty())
                {
                    monsterResults.add(new MonsterDropResultEntry(m, matchingDrops));
                }
            }
        }

        List<SearchResultEntry> shopResults = new ArrayList<>();
        int minPrice = Integer.MAX_VALUE;
        int maxPrice = Integer.MIN_VALUE;
        int minStock = Integer.MAX_VALUE;
        int maxStock = Integer.MIN_VALUE;

        if (currentFilterMode != SearchFilterMode.DROPS_ONLY && shopDatabase != null && shopDatabase.isLoaded())
        {
            Set<Shop> matchedShops = shopDatabase.searchShopsByItemName(query);
            for (Shop shop : matchedShops)
            {
                List<ShopItem> matchedItems = new ArrayList<>();
                for (ShopItem item : shop.getItems())
                {
                    if (item.getName().toLowerCase(Locale.ROOT).contains(qLower))
                    {
                        if (config.hideZeroStock() && (item.isZeroDefaultStock() || item.getDefaultStock() <= 0))
                        {
                            continue;
                        }
                        if (config.hideIronmanRestricted() && item.isIronmanBlocked())
                        {
                            continue;
                        }
                        matchedItems.add(item);

                        if (item.getPrice() < minPrice) minPrice = item.getPrice();
                        if (item.getPrice() > maxPrice) maxPrice = item.getPrice();

                        if (item.getDefaultStock() < minStock) minStock = item.getDefaultStock();
                        if (item.getDefaultStock() > maxStock) maxStock = item.getDefaultStock();
                    }
                }

                if (!matchedItems.isEmpty())
                {
                    shopResults.add(new SearchResultEntry(shop, matchedItems));
                }
            }
        }

        String sortChoice = (String) sortDropdown.getSelectedItem();
        if (sortChoice != null)
        {
            if (sortChoice.contains("Lowest Price"))
            {
                shopResults.sort(Comparator.comparingInt(e -> e.items.get(0).getPrice()));
                monsterResults.sort((a, b) -> Integer.compare(b.monster.getCombatLevel(), a.monster.getCombatLevel()));
            }
            else if (sortChoice.contains("Best Sell Price"))
            {
                shopResults.sort((a, b) -> Integer.compare(b.items.get(0).getEffectiveBuyPrice(), a.items.get(0).getEffectiveBuyPrice()));
                monsterResults.sort((a, b) -> Integer.compare(b.monster.getCombatLevel(), a.monster.getCombatLevel()));
            }
            else if (sortChoice.contains("Highest Stock"))
            {
                shopResults.sort((a, b) -> Integer.compare(b.items.get(0).getDefaultStock(), a.items.get(0).getDefaultStock()));
                monsterResults.sort((a, b) -> Integer.compare(b.monster.getCombatLevel(), a.monster.getCombatLevel()));
            }
            else if (sortChoice.contains("Combat Lv"))
            {
                monsterResults.sort((a, b) -> Integer.compare(b.monster.getCombatLevel(), a.monster.getCombatLevel()));
                shopResults.sort(Comparator.comparing(e -> e.shop.getName(), String.CASE_INSENSITIVE_ORDER));
            }
            else
            {
                shopResults.sort(Comparator.comparing((SearchResultEntry e) -> e.shop.getTown()).thenComparing(e -> e.shop.getName()));
                monsterResults.sort(Comparator.comparing((MonsterDropResultEntry e) -> e.monster.getName(), String.CASE_INSENSITIVE_ORDER));
            }
        }

        boolean hasPriceVariance = (minPrice < maxPrice && minPrice != Integer.MAX_VALUE);
        boolean hasStockVariance = (minStock < maxStock && maxStock > 0);
        for (SearchResultEntry entry : shopResults)
        {
            ShopItem primaryItem = entry.items.get(0);
            entry.isCheapest = hasPriceVariance && (primaryItem.getPrice() == minPrice);
            entry.isMaxStock = hasStockVariance && (primaryItem.getDefaultStock() == maxStock);
        }

        int totalCount = monsterResults.size() + shopResults.size();
        if (totalCount == 0)
        {
            boolean dbFailed = (shopDatabase != null && shopDatabase.isLoadFailed())
                || (monsterDatabase != null && monsterDatabase.isLoadFailed());
            boolean dbLoading = (shopDatabase != null && !shopDatabase.isLoaded() && !shopDatabase.isLoadFailed())
                || (monsterDatabase != null && !monsterDatabase.isLoaded() && !monsterDatabase.isLoadFailed());
            if (dbFailed)
            {
                statusLabel.setText("Search data failed to load - see the client log.");
            }
            else if (dbLoading)
            {
                statusLabel.setText("Search data still loading - try again in a moment.");
            }
            else
            {
                statusLabel.setText("No drops or shops found for '" + query + "'");
            }
            pendingShopEntries = Collections.emptyList();
            pendingMonsterEntries = Collections.emptyList();
            renderedShopCount = 0;
            renderedMonsterCount = 0;
            resultsContainer.revalidate();
            resultsContainer.repaint();
            return;
        }

        statusLabel.setText("Found " + monsterResults.size() + " drop(s), " + shopResults.size() + " shop(s):");

        pendingMonsterEntries = monsterResults;
        pendingShopEntries = shopResults;
        renderedMonsterCount = 0;
        renderedShopCount = 0;

        if (!monsterResults.isEmpty())
        {
            if (currentFilterMode == SearchFilterMode.ALL)
            {
                resultsContainer.add(createSectionHeader("Monster Drops (" + monsterResults.size() + ")"));
                resultsContainer.add(Box.createRigidArea(new Dimension(0, 3)));
            }

            int monsterLimit = (monsterResults.size() > SEARCH_RENDER_LIMIT) ? SEARCH_RENDER_LIMIT : monsterResults.size();
            for (int i = 0; i < monsterLimit; i++)
            {
                resultsContainer.add(buildMonsterDropCard(monsterResults.get(i)));
                resultsContainer.add(Box.createRigidArea(new Dimension(0, 3)));
            }
            renderedMonsterCount = monsterLimit;
        }

        if (!shopResults.isEmpty())
        {
            if (currentFilterMode == SearchFilterMode.ALL)
            {
                resultsContainer.add(createSectionHeader("Available in Shops (" + shopResults.size() + ")"));
                resultsContainer.add(Box.createRigidArea(new Dimension(0, 3)));
            }

            int shopLimit = (shopResults.size() > SEARCH_RENDER_LIMIT) ? SEARCH_RENDER_LIMIT : shopResults.size();
            for (int i = 0; i < shopLimit; i++)
            {
                resultsContainer.add(buildShopCard(shopResults.get(i)));
                resultsContainer.add(Box.createRigidArea(new Dimension(0, 3)));
            }
            renderedShopCount = shopLimit;
        }

        int remainingShops = shopResults.size() - renderedShopCount;
        if (remainingShops > 0)
        {
            showMoreRow = buildShowMoreRow(remainingShops);
            resultsContainer.add(showMoreRow);
        }

        resultsContainer.revalidate();
        resultsContainer.repaint();
    }

    private JPanel createSectionHeader(String title)
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(6, 2, 3, 2));
        panel.setAlignmentX(LEFT_ALIGNMENT);

        JLabel label = new JLabel(title);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(SECTION_HEADER_COLOR);
        panel.add(label, BorderLayout.WEST);
        return panel;
    }

    private JPanel buildMonsterDropCard(MonsterDropResultEntry entry)
    {
        Monster monster = entry.monster;
        List<MonsterDrop> drops = entry.matchingDrops;

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(3, 4, 3, 4)
        ));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JPanel topRow = new JPanel(new BorderLayout(5, 0));
        topRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        topRow.setAlignmentX(LEFT_ALIGNMENT);

        JLabel portraitLabel = new JLabel();
        portraitLabel.setPreferredSize(new Dimension(24, 24));
        portraitLabel.setMinimumSize(new Dimension(24, 24));
        portraitLabel.setMaximumSize(new Dimension(24, 24));
        portraitLabel.setHorizontalAlignment(JLabel.CENTER);
        portraitLabel.setVerticalAlignment(JLabel.CENTER);
        if (npcPortraitManager != null)
        {
            npcPortraitManager.loadNpcPortrait(monster.getName(), 24, portraitLabel);
        }
        topRow.add(portraitLabel, BorderLayout.WEST);

        String combatText = monster.getCombatLevel() > 0 ? " <span style='color: #ffb92d;'>(Lv. " + monster.getCombatLevel() + ")</span>" : "";
        JLabel nameLabel = new JLabel("<html><body><b>" + monster.getName() + "</b>" + combatText + "</body></html>");
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setToolTipText(monster.getName() + (monster.getCombatLevel() > 0 ? " (Combat Level: " + monster.getCombatLevel() + ")" : ""));
        topRow.add(nameLabel, BorderLayout.CENTER);

        if (monster.getCategory() != null && !monster.getCategory().isEmpty())
        {
            JLabel catLabel = new JLabel(monster.getCategory());
            catLabel.setFont(FontManager.getRunescapeSmallFont());
            catLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            topRow.add(catLabel, BorderLayout.EAST);
        }

        card.add(topRow);
        card.add(Box.createRigidArea(new Dimension(0, 2)));

        for (MonsterDrop drop : drops)
        {
            String qtyStr = drop.getQuantity() != null && !drop.getQuantity().isEmpty() ? drop.getQuantity() : "1";
            String rateStr = drop.getFormattedRarity();
            JLabel dropLabel = new JLabel("<html><body>• <b>" + drop.getName() + "</b> <span style='color: #a0a0a5;'>(Qty: " + qtyStr + " • </span><span style='color: #5ac8fa;'>" + rateStr + "</span><span style='color: #a0a0a5;'>)</span></body></html>");
            dropLabel.setFont(FontManager.getRunescapeSmallFont());
            dropLabel.setForeground(Color.WHITE);
            dropLabel.setAlignmentX(LEFT_ALIGNMENT);
            card.add(dropLabel);
        }

        String locationName = null;
        com.osrscopilot.data.model.MonsterSpawnZone primaryZone = null;
        if (monster.getSpawnZones() != null && !monster.getSpawnZones().isEmpty())
        {
            primaryZone = monster.getSpawnZones().get(0);
            locationName = primaryZone.getLocationName() != null ? primaryZone.getLocationName() : primaryZone.getZoneName();
        }

        if (locationName != null && !locationName.trim().isEmpty())
        {
            JLabel locLabel = new JLabel("Location: " + locationName);
            locLabel.setFont(FontManager.getRunescapeSmallFont());
            locLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            locLabel.setAlignmentX(LEFT_ALIGNMENT);
            card.add(locLabel);
        }

        card.add(Box.createRigidArea(new Dimension(0, 3)));

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 4, 0));
        btnRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        btnRow.setPreferredSize(new Dimension(0, 20));
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JButton viewBtn = new JButton("View");
        viewBtn.setFont(FontManager.getRunescapeSmallFont());
        viewBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        viewBtn.setForeground(Color.WHITE);
        viewBtn.setFocusPainted(false);
        viewBtn.setMargin(new Insets(1, 2, 1, 2));
        viewBtn.setToolTipText("View monster drop table and details");
        viewBtn.addActionListener(e -> {
            if (onViewMonsterDetail != null)
            {
                onViewMonsterDetail.accept(monster);
            }
        });
        btnRow.add(viewBtn);

        final com.osrscopilot.data.model.MonsterSpawnZone targetZone = primaryZone;
        JButton mapBtn = new JButton("Map");
        mapBtn.setFont(FontManager.getRunescapeSmallFont());
        mapBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        mapBtn.setForeground(ACCENT_BLUE);
        mapBtn.setFocusPainted(false);
        mapBtn.setMargin(new Insets(1, 2, 1, 2));
        mapBtn.setToolTipText("Show " + monster.getName() + " on the World Map");
        mapBtn.addActionListener(e -> {
            if (onFocusMonsterZoneOnMap != null && targetZone != null)
            {
                onFocusMonsterZoneOnMap.accept(monster, targetZone);
            }
            else if (onViewMonsterDetail != null)
            {
                onViewMonsterDetail.accept(monster);
            }
        });
        btnRow.add(mapBtn);

        card.add(btnRow);
        return card;
    }

    private JPanel buildShopCard(SearchResultEntry entry)
    {
        Shop shop = entry.shop;
        ShopItem primaryItem = entry.items.get(0);
        boolean isCheapest = entry.isCheapest;
        boolean isMaxStock = entry.isMaxStock;

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(3, 4, 3, 4)
        ));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JPanel topRow = new JPanel(new BorderLayout(5, 0));
        topRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        topRow.setAlignmentX(LEFT_ALIGNMENT);

        JLabel portraitLabel = new JLabel();
        portraitLabel.setPreferredSize(new Dimension(24, 24));
        portraitLabel.setMinimumSize(new Dimension(24, 24));
        portraitLabel.setMaximumSize(new Dimension(24, 24));
        portraitLabel.setHorizontalAlignment(JLabel.CENTER);
        portraitLabel.setVerticalAlignment(JLabel.CENTER);
        if (npcPortraitManager != null)
        {
            npcPortraitManager.loadNpcPortrait(shop.getNpcName(), 24, portraitLabel);
        }
        topRow.add(portraitLabel, BorderLayout.WEST);

        String badgeHtml = isCheapest ? "<span style='color: #ffd700;'>[Best Price] </span>" : (isMaxStock ? "<span style='color: #10b981;'>[Best Stock] </span>" : "");
        JLabel nameLabel = new JLabel("<html><body>" + badgeHtml + "<b>" + shop.getName() + "</b></body></html>");
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setToolTipText(shop.getName() + " (" + shop.getTown() + ")");
        topRow.add(nameLabel, BorderLayout.CENTER);

        JLabel townLabel = new JLabel(shop.getTown());
        townLabel.setFont(FontManager.getRunescapeSmallFont());
        townLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        topRow.add(townLabel, BorderLayout.EAST);

        card.add(topRow);
        card.add(Box.createRigidArea(new Dimension(0, 2)));

        for (ShopItem item : entry.items)
        {
            boolean isZero = item.getDefaultStock() <= 0;
            String currName = (shop.getCurrency() != null) ? shop.getCurrency().getShortName() : "gp";
            String priceText = "Buy: " + item.getPrice() + " " + currName;
            if (item.getEffectiveBuyPrice() > 0)
            {
                priceText += " | Sell: " + item.getEffectiveBuyPrice() + " " + currName;
            }

            JLabel itemStats = new JLabel("<html><body>• <b>" + item.getName() + "</b> <span style='color: #a0a0a5;'>(Stock: " + item.getDefaultStock() + " • " + item.getRestockTimeSeconds() + "s)</span><br><span style='color: " + (isZero ? "#9ca3af" : "#ffd700") + ";'>" + priceText + "</span></body></html>");
            itemStats.setFont(FontManager.getRunescapeSmallFont());
            itemStats.setForeground(Color.WHITE);
            itemStats.setAlignmentX(LEFT_ALIGNMENT);
            itemStats.setToolTipText("Right-click to Add to Shopping Cart");

            JPopupMenu itemMenu = createCartPopupMenu(item, shop);
            itemStats.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    if (e.isPopupTrigger()) itemMenu.show(itemStats, e.getX(), e.getY());
                }

                @Override
                public void mouseReleased(MouseEvent e)
                {
                    if (e.isPopupTrigger()) itemMenu.show(itemStats, e.getX(), e.getY());
                }
            });

            card.add(itemStats);
        }

        card.add(Box.createRigidArea(new Dimension(0, 3)));

        JPanel btnRow = new JPanel(new GridLayout(1, 3, 4, 0));
        btnRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        btnRow.setPreferredSize(new Dimension(0, 20));
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JButton inspectBtn = new JButton("View");
        inspectBtn.setFont(FontManager.getRunescapeSmallFont());
        inspectBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        inspectBtn.setForeground(Color.WHITE);
        inspectBtn.setFocusPainted(false);
        inspectBtn.setMargin(new Insets(1, 2, 1, 2));
        inspectBtn.setToolTipText("View shop in sidebar");
        inspectBtn.addActionListener(e -> onInspectShop.accept(shop));
        btnRow.add(inspectBtn);

        JButton focusBtn = new JButton("Map");
        focusBtn.setFont(FontManager.getRunescapeSmallFont());
        focusBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        focusBtn.setForeground(ACCENT_BLUE);
        focusBtn.setFocusPainted(false);
        focusBtn.setMargin(new Insets(1, 2, 1, 2));
        focusBtn.setToolTipText("Show this shop on the World Map");
        focusBtn.addActionListener(e -> onFocusShopOnMap.accept(shop));
        btnRow.add(focusBtn);

        JButton addCartBtn = new JButton("+ Cart");
        addCartBtn.setFont(FontManager.getRunescapeSmallFont());
        addCartBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        addCartBtn.setForeground(CHEAPEST_GOLD);
        addCartBtn.setFocusPainted(false);
        addCartBtn.setMargin(new Insets(1, 2, 1, 2));
        addCartBtn.setToolTipText("Add to Shopping Cart (Right-click for options)");
        addCartBtn.addActionListener(e -> {
            if (primaryItem != null)
            {
                cartManager.addItem(primaryItem, shop, 1);
            }
        });

        JPopupMenu primaryMenu = createCartPopupMenu(primaryItem, shop);
        addCartBtn.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (e.isPopupTrigger()) primaryMenu.show(addCartBtn, e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (e.isPopupTrigger()) primaryMenu.show(addCartBtn, e.getX(), e.getY());
            }
        });
        btnRow.add(addCartBtn);

        card.add(btnRow);
        return card;
    }

    private JPanel buildShowMoreRow(int remaining)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(4, 4, 8, 4));

        int nextBatch = Math.min(remaining, SEARCH_RENDER_LIMIT);
        JButton showMoreBtn = new JButton("Show " + nextBatch + " more (" + remaining + " remaining)");
        showMoreBtn.setFont(FontManager.getRunescapeSmallFont());
        showMoreBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        showMoreBtn.setForeground(ACCENT_BLUE);
        showMoreBtn.setFocusPainted(false);
        showMoreBtn.setPreferredSize(new Dimension(0, 22));
        showMoreBtn.addActionListener(e -> {
            int end = Math.min(renderedShopCount + SEARCH_RENDER_LIMIT, pendingShopEntries.size());
            for (int i = renderedShopCount; i < end; i++)
            {
                resultsContainer.add(buildShopCard(pendingShopEntries.get(i)));
                resultsContainer.add(Box.createRigidArea(new Dimension(0, 3)));
            }
            renderedShopCount = end;
            resultsContainer.revalidate();
            resultsContainer.repaint();
        });
        row.add(showMoreBtn, BorderLayout.CENTER);
        return row;
    }

    private JPopupMenu createCartPopupMenu(ShopItem item, Shop shop)
    {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem add1 = new JMenuItem("Add to Cart (1)");
        add1.addActionListener(e -> cartManager.addItem(item, shop, 1));
        menu.add(add1);

        JMenuItem add5 = new JMenuItem("Add to Cart (5)");
        add5.addActionListener(e -> cartManager.addItem(item, shop, 5));
        menu.add(add5);

        JMenuItem add10 = new JMenuItem("Add to Cart (10)");
        add10.addActionListener(e -> cartManager.addItem(item, shop, 10));
        menu.add(add10);

        JMenuItem add50 = new JMenuItem("Add to Cart (50)");
        add50.addActionListener(e -> cartManager.addItem(item, shop, 50));
        menu.add(add50);

        JMenuItem addAll = new JMenuItem("Add All (Stock: " + item.getDefaultStock() + ")");
        addAll.addActionListener(e -> {
            int stock = item.getDefaultStock() > 0 ? item.getDefaultStock() : 1;
            cartManager.addItem(item, shop, stock);
        });
        menu.add(addAll);

        JMenuItem addX = new JMenuItem("Add X...");
        addX.addActionListener(e -> {
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
                        cartManager.addItem(item, shop, qty);
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

        JMenuItem viewShop = new JMenuItem("View Shop in Sidebar");
        viewShop.addActionListener(e -> onInspectShop.accept(shop));
        menu.add(viewShop);

        JMenuItem focusMap = new JMenuItem("Show on World Map");
        focusMap.addActionListener(e -> onFocusShopOnMap.accept(shop));
        menu.add(focusMap);

        return menu;
    }

    private static class SearchResultEntry
    {
        Shop shop;
        List<ShopItem> items;
        boolean isCheapest;
        boolean isMaxStock;

        SearchResultEntry(Shop shop, List<ShopItem> items)
        {
            this.shop = shop;
            this.items = items;
        }
    }

    private static class MonsterDropResultEntry
    {
        Monster monster;
        List<MonsterDrop> matchingDrops;

        MonsterDropResultEntry(Monster monster, List<MonsterDrop> matchingDrops)
        {
            this.monster = monster;
            this.matchingDrops = matchingDrops;
        }
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
