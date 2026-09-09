package com.osrscopilot.ui;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.RarityFormat;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.ShoppingCartManager;
import com.osrscopilot.data.SlayerTaskManager;
import com.osrscopilot.data.model.CartItem;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterDrop;
import com.osrscopilot.data.model.MonsterSpawnZone;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.ShopItem;
import com.osrscopilot.data.model.SlayerMaster;
import com.osrscopilot.data.model.SlayerTaskAssignment;
import com.osrscopilot.util.NpcPortraitManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;

/**
 * OmniHUD — Directory:
 * An authentic OSRS Skill Guide interface with dual-mode (In-Client Modal Overlay &amp; External Window) architecture.
 * Features left-side vertical category tabs, sub-filters, yellow level/price badges, crisp 32x32 sprites,
 * orange member text, red requirement warnings, stone action buttons, and full sortable spreadsheet grid.
 */
@Singleton
@Slf4j
public class ShopDirectorySpreadsheetDialog extends JFrame
{
    public static final String TAB_SHOPS = "TAB_SHOPS";
    public static final String TAB_MONSTERS = "TAB_MONSTERS";
    public static final String TAB_SLAYER = "TAB_SLAYER";
    public static final String TAB_SEARCH = "TAB_SEARCH";

    // Authentic OSRS Skill Guide Brown & Stone Color Palette
    public static final Color BG_STONE_DARK = new Color(36, 29, 21);       // #241D15 Outer dark stone
    public static final Color BG_STONE_PANEL = new Color(46, 38, 29);      // #2E261D Main brown background
    public static final Color BG_STONE_HEADER = new Color(59, 48, 36);     // #3B3024 Header & toolbar brown
    public static final Color BG_STONE_CARD = new Color(50, 41, 31);       // #32291F Guide row card background
    public static final Color BG_STONE_CARD_ALT = new Color(42, 34, 25);   // #2A2219 Alternating card
    public static final Color BG_STONE_BADGE = new Color(30, 24, 17);      // #1E1811 Level/Price badge
    public static final Color BG_STONE_BTN = new Color(56, 46, 34);        // #382E22 Stone button
    public static final Color BG_STONE_BTN_HOVER = new Color(75, 62, 47);  // #4B3E2F Button hover
    public static final Color BG_STONE_BTN_PRESS = new Color(32, 26, 19);  // #201A13 Button pressed

    public static final Color ROW_EVEN = new Color(46, 38, 29);
    public static final Color ROW_ODD = new Color(38, 31, 23);
    public static final Color ROW_SELECTED = new Color(75, 58, 38);

    public static final Color BORDER_STONE_OUTER = new Color(24, 18, 12);
    public static final Color BORDER_STONE_HIGHLIGHT = new Color(98, 80, 60);
    public static final Color BORDER_STONE_SHADOW = new Color(30, 24, 17);
    public static final Color BORDER_GOLD = new Color(184, 134, 11, 220);
    public static final Color BORDER_GOLD_BRIGHT = new Color(245, 180, 40);

    // Text & Accent Colors
    public static final Color TITLE_GOLD = new Color(255, 184, 38);
    public static final Color PRICE_GOLD = new Color(255, 232, 56);
    public static final Color NAME_ORANGE = new Color(255, 152, 31);
    public static final Color TEXT_PARCHMENT = new Color(237, 229, 216);
    public static final Color TEXT_MUTED = new Color(195, 185, 170);
    public static final Color REQ_RED = new Color(239, 68, 68);
    public static final Color MAP_CYAN = new Color(100, 200, 250);
    public static final Color QUEST_PURPLE = new Color(192, 132, 252);
    public static final Color SLAYER_PURPLE = new Color(192, 132, 252);
    public static final Color STOCK_GREEN = new Color(74, 222, 128);
    public static final Color STOCK_RED = new Color(248, 113, 113);
    public static final Color COMBAT_LVL_GOLD = new Color(255, 215, 0);
    public static final Color HP_RED = new Color(248, 113, 113);
    public static final Color MAX_HIT_ORANGE = new Color(251, 146, 60);
    public static final Color WEAKNESS_MINT = new Color(52, 211, 153);
    public static final Color WILDY_RED = new Color(239, 68, 68);
    public static final Color DROP_PINK = new Color(236, 72, 153);

    // Compatibility aliases
    public static final Color DARK_BG = BG_STONE_DARK;
    public static final Color PANEL_BG = BG_STONE_PANEL;
    public static final Color HEADER_BG = BG_STONE_HEADER;
    public static final Color TAB_ACTIVE_BG = BG_STONE_HEADER;
    public static final Color TAB_ACTIVE_BORDER = BORDER_GOLD_BRIGHT;
    public static final Color TAB_INACTIVE_BG = BG_STONE_DARK;
    public static final Color TAB_INACTIVE_BORDER = BORDER_STONE_OUTER;

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    public static final String[] SHOP_COLUMN_NAMES = {
        "Town", "Shop Name", "NPC", "Item Name", "Stock", "Buy Price", "Sell Price", "Restock", "Currency", "Memb"
    };
    public static final String[] COLUMN_NAMES = SHOP_COLUMN_NAMES;

    public static final String[] MONSTER_COLUMN_NAMES = {
        "Monster", "Category", "Lvl", "HP", "Max Hit", "Style", "Weakness", "Slayer Req", "Quest Req", "Primary Spawn", "Top Drops"
    };

    public static final String[] SLAYER_COLUMN_NAMES = {
        "Slayer Master", "Location", "Cmb Req", "Slayer Req", "Task Monster", "Kills", "Weight", "Unlock Requirement"
    };

    public static final String[] SEARCH_COLUMN_NAMES = {
        "Item", "Type (Shop / Monster)", "Source Name", "Location / Zone", "Stock / Rarity", "Price / Drop Rate", "Reqs"
    };

    // Databases & Services
    private final ShopDatabase shopDatabase;
    private final MonsterDatabase monsterDatabase;
    private final SlayerTaskManager slayerTaskManager;
    private final ItemManager itemManager;
    private final OsrsCopilotConfig config;
    private final ShoppingCartManager cartManager;

    // Navigation & Map Callbacks
    private Consumer<Shop> onFocusShopOnMap;
    private Consumer<Shop> onInspectShop;
    private Consumer<Monster> onFocusMonsterOnMap;
    private Consumer<Monster> onInspectMonster;
    private BiConsumer<Monster, MonsterSpawnZone> onFocusMonsterZoneOnMap;
    private BiConsumer<WorldPoint, String> onFocusPointOnMap;
    private Consumer<String> onOpenSlayerTab;

    private net.runelite.client.ui.ClientToolbar clientToolbar;
    private net.runelite.client.ui.NavigationButton navButton;
    private java.awt.Point dragInitialClick;

    // Dual-Mode State (In-Client Modal Overlay vs Undocked External JFrame)
    private boolean undocked = false;
    private JDialog inClientModalDialog;

    // When true, no real window is ever realised/shown. Used by tests (which run on a
    // non-headless JVM) so exercising the dialog doesn't flash frames on the user's screen.
    private boolean windowRealizationSuppressed = false;
    private final JPanel mainGuidePanel = new JPanel(new BorderLayout(0, 0));
    private final JButton undockDockButton = new JButton("Undock");
    private final JButton viewModeToggleBtn = new JButton("Guide View");
    private boolean guideViewMode = true;

    // Card Layout Container for Main Content (Center/Right)
    private final CardLayout tabCardLayout = new CardLayout();
    private final JPanel tabCardsPanel = new JPanel(tabCardLayout);
    private String activeTab = TAB_SHOPS;

    // Left-Side Vertical Category Strip & Sub-Filter Panel
    private final JPanel leftNavPanel = new JPanel();
    private final JButton navShopsBtn = new JButton("Shops & Markets");
    private final JButton navMonstersBtn = new JButton("Bestiary");
    private final JButton navSlayerBtn = new JButton("Slayer Masters");
    private final JButton navSearchBtn = new JButton("Global Item Search");
    private final JPanel subFiltersContainer = new JPanel();
    private String activeSubFilter = "All";

    // Dynamic Result Count & Footer Feedback
    private final JLabel footerStatusLabel = new JLabel("Click Map to pan & beacon location • Undock for multi-monitor display");
    private final JButton addCartBtn = new JButton("Add to Cart");

    // --- TAB 1: SHOPS COMPONENTS ---
    private final JTextField shopSearchField = new JTextField(14);
    private final JComboBox<String> shopFilterTownDropdown = new JComboBox<>();
    private final JComboBox<String> shopFilterMembDropdown = new JComboBox<>();
    private final JCheckBox shopHideZeroStockCheckbox = new JCheckBox("Hide 0-Stock");
    private final JLabel shopRowCountLabel = new JLabel();
    private final JTable shopTable = new JTable();
    private DefaultTableModel shopTableModel;
    private TableRowSorter<DefaultTableModel> shopSorter;
    private final List<TableColumn> allShopTableColumns = new ArrayList<>();
    private final boolean[] shopColumnVisible = new boolean[SHOP_COLUMN_NAMES.length];
    private final List<ShopTableRowData> currentShopRows = new ArrayList<>();
    private final JPanel shopCardsListPanel = new JPanel();
    private final CardLayout shopViewCardLayout = new CardLayout();
    private final JPanel shopViewContainer = new JPanel(shopViewCardLayout);

    // --- TAB 2: BESTIARY COMPONENTS ---
    private final JTextField monsterSearchField = new JTextField(14);
    private final JComboBox<String> monsterCategoryDropdown = new JComboBox<>();
    private final JComboBox<String> monsterWildyDropdown = new JComboBox<>();
    private final JComboBox<String> monsterSlayerDropdown = new JComboBox<>();
    private final JLabel monsterRowCountLabel = new JLabel();
    private final JTable monsterTable = new JTable();
    private DefaultTableModel monsterTableModel;
    private TableRowSorter<DefaultTableModel> monsterSorter;
    private final List<TableColumn> allMonsterTableColumns = new ArrayList<>();
    private final boolean[] monsterColumnVisible = new boolean[MONSTER_COLUMN_NAMES.length];
    private final List<MonsterTableRowData> currentMonsterRows = new ArrayList<>();
    // formatTopDrops() is a pure function of a monster's (immutable) drop list but is the one
    // non-trivial cost in the Bestiary filter loop - build the HTML string once per monster and
    // reuse it on every subsequent search instead of re-formatting ~1,800 of them per keystroke.
    private final java.util.Map<Integer, String> topDropsCache = new java.util.HashMap<>();
    private final JPanel monsterCardsListPanel = new JPanel();
    private final JPanel combatAchievementsInspectorPanel = new JPanel();
    private Monster selectedBestiaryMonster = null;
    private NpcPortraitManager npcPortraitManager;
    private final CardLayout monsterViewCardLayout = new CardLayout();
    private final JPanel monsterViewContainer = new JPanel(monsterViewCardLayout);

    // --- TAB 3: SLAYER COMPONENTS ---
    private final JTextField slayerSearchField = new JTextField(14);
    private final JComboBox<String> slayerMasterDropdown = new JComboBox<>();
    private final JComboBox<String> slayerFilterReqDropdown = new JComboBox<>();
    private final JLabel slayerRowCountLabel = new JLabel();
    private final JTable slayerTable = new JTable();
    private DefaultTableModel slayerTableModel;
    private TableRowSorter<DefaultTableModel> slayerSorter;
    private final List<TableColumn> allSlayerTableColumns = new ArrayList<>();
    private final boolean[] slayerColumnVisible = new boolean[SLAYER_COLUMN_NAMES.length];
    private final List<SlayerTableRowData> currentSlayerRows = new ArrayList<>();
    private final JPanel slayerCardsListPanel = new JPanel();
    private final CardLayout slayerViewCardLayout = new CardLayout();
    private final JPanel slayerViewContainer = new JPanel(slayerViewCardLayout);

    // --- TAB 4: GLOBAL SEARCH COMPONENTS ---
    private final JTextField searchUniversalField = new JTextField(16);
    private final JComboBox<String> searchSourceTypeDropdown = new JComboBox<>();
    private final JComboBox<String> searchMembDropdown = new JComboBox<>();
    private final JLabel searchRowCountLabel = new JLabel();
    private final JTable searchTable = new JTable();
    private DefaultTableModel searchTableModel;
    private TableRowSorter<DefaultTableModel> searchSorter;
    private final List<TableColumn> allSearchTableColumns = new ArrayList<>();
    private final boolean[] searchColumnVisible = new boolean[SEARCH_COLUMN_NAMES.length];
    private final List<GlobalSearchRowData> currentSearchRows = new ArrayList<>();
    private final JPanel searchCardsListPanel = new JPanel();
    private final CardLayout searchViewCardLayout = new CardLayout();
    private final JPanel searchViewContainer = new JPanel(searchViewCardLayout);

    public ShopDirectorySpreadsheetDialog(
        ShopDatabase shopDatabase,
        MonsterDatabase monsterDatabase,
        OsrsCopilotConfig config,
        ShoppingCartManager cartManager)
    {
        this(shopDatabase, monsterDatabase, new SlayerTaskManager(null, monsterDatabase), null, null, config, cartManager);
    }

    public ShopDirectorySpreadsheetDialog(
        ShopDatabase shopDatabase,
        MonsterDatabase monsterDatabase,
        SlayerTaskManager slayerTaskManager,
        OsrsCopilotConfig config,
        ShoppingCartManager cartManager)
    {
        this(shopDatabase, monsterDatabase, slayerTaskManager, null, null, config, cartManager);
    }

    public ShopDirectorySpreadsheetDialog(
        ShopDatabase shopDatabase,
        MonsterDatabase monsterDatabase,
        SlayerTaskManager slayerTaskManager,
        ItemManager itemManager,
        OsrsCopilotConfig config,
        ShoppingCartManager cartManager)
    {
        this(shopDatabase, monsterDatabase, slayerTaskManager, itemManager, null, config, cartManager);
    }

    @Inject
    public ShopDirectorySpreadsheetDialog(
        ShopDatabase shopDatabase,
        MonsterDatabase monsterDatabase,
        SlayerTaskManager slayerTaskManager,
        ItemManager itemManager,
        NpcPortraitManager npcPortraitManager,
        OsrsCopilotConfig config,
        ShoppingCartManager cartManager)
    {
        super("OmniHUD — Directory");
        this.shopDatabase = shopDatabase;
        this.monsterDatabase = java.util.Objects.requireNonNull(monsterDatabase, "monsterDatabase");
        this.slayerTaskManager = slayerTaskManager != null ? slayerTaskManager : new SlayerTaskManager(null, this.monsterDatabase);
        this.itemManager = itemManager;
        this.npcPortraitManager = npcPortraitManager;
        this.config = config;
        this.cartManager = cartManager != null ? cartManager : ShoppingCartManager.getInstance();

        for (int i = 0; i < shopColumnVisible.length; i++) shopColumnVisible[i] = true;
        for (int i = 0; i < monsterColumnVisible.length; i++) monsterColumnVisible[i] = true;
        for (int i = 0; i < slayerColumnVisible.length; i++) slayerColumnVisible[i] = true;
        for (int i = 0; i < searchColumnVisible.length; i++) searchColumnVisible[i] = true;

        setPreferredSize(new Dimension(1260, 760));
        setMinimumSize(new Dimension(920, 560));
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setLocationRelativeTo(null);

        // Build Full Skill Guide UI
        buildMainGuideUi();

        // Attach UI to external window content pane by default
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(mainGuidePanel, BorderLayout.CENTER);
        pack();
    }

    public void setClientToolbar(net.runelite.client.ui.ClientToolbar clientToolbar, net.runelite.client.ui.NavigationButton navButton)
    {
        this.clientToolbar = clientToolbar;
        this.navButton = navButton;
    }

    public void setNpcPortraitManager(NpcPortraitManager npcPortraitManager)
    {
        this.npcPortraitManager = npcPortraitManager;
    }

    private void enableWindowDragging(JComponent component)
    {
        MouseAdapter dragListener = new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e))
                {
                    dragInitialClick = e.getPoint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e) && dragInitialClick != null)
                {
                    java.awt.Window window = inClientModalDialog != null && inClientModalDialog.isVisible()
                        ? inClientModalDialog
                        : ShopDirectorySpreadsheetDialog.this;
                    if (window != null)
                    {
                        int thisX = window.getLocation().x;
                        int thisY = window.getLocation().y;
                        int xMoved = e.getX() - dragInitialClick.x;
                        int yMoved = e.getY() - dragInitialClick.y;
                        window.setLocation(thisX + xMoved, thisY + yMoved);
                    }
                }
            }
        };

        component.addMouseListener(dragListener);
        component.addMouseMotionListener(dragListener);
    }

    private void enableShiftDrag(JComponent component)
    {
        MouseAdapter shiftDragListener = new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (e.isShiftDown() && SwingUtilities.isLeftMouseButton(e))
                {
                    dragInitialClick = e.getPoint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e)
            {
                if (e.isShiftDown() && SwingUtilities.isLeftMouseButton(e) && dragInitialClick != null)
                {
                    java.awt.Window window = inClientModalDialog != null && inClientModalDialog.isVisible()
                        ? inClientModalDialog
                        : ShopDirectorySpreadsheetDialog.this;
                    if (window != null)
                    {
                        int thisX = window.getLocation().x;
                        int thisY = window.getLocation().y;
                        int xMoved = e.getX() - dragInitialClick.x;
                        int yMoved = e.getY() - dragInitialClick.y;
                        window.setLocation(thisX + xMoved, thisY + yMoved);
                    }
                }
            }
        };

        component.addMouseListener(shiftDragListener);
        component.addMouseMotionListener(shiftDragListener);
    }

    private final java.util.List<javax.swing.Timer> debounceTimers = new java.util.ArrayList<>();
    private boolean debounceSuppressed;

    private void setupDebouncedSearch(JTextField textField, Runnable searchAction)
    {
        javax.swing.Timer debounceTimer = new javax.swing.Timer(150, e -> searchAction.run());
        debounceTimer.setRepeats(false);
        debounceTimers.add(debounceTimer);

        textField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            private void bump() { if (!debounceSuppressed) debounceTimer.restart(); }
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { bump(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { bump(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { bump(); }
        });
    }

    /**
     * Test hook: when suppressed, typing into a search field no longer arms the ~150ms debounce
     * timer, and any pending timer is stopped. Tests drive {@code applyXFilters()} explicitly and
     * read the table models off the EDT, so a deferred re-filter would otherwise race that read
     * (the historical flake). No effect in production, where reads also happen on the EDT.
     */
    public void setDebounceSuppressed(boolean suppressed)
    {
        this.debounceSuppressed = suppressed;
        if (suppressed)
        {
            cancelPendingDebounce();
        }
    }

    /** Stop any pending search-debounce timers. */
    public void cancelPendingDebounce()
    {
        for (javax.swing.Timer t : debounceTimers)
        {
            t.stop();
        }
    }

    private void buildMainGuideUi()
    {
        mainGuidePanel.setBackground(BG_STONE_DARK);
        mainGuidePanel.setBorder(buildDoubleBeveledStoneBorder());
        enableShiftDrag(mainGuidePanel);

        // 1. Top Decorative Header Bar (with Title, View Toggle, Undock/Dock, Close)
        JPanel headerBar = buildHeaderBar();
        mainGuidePanel.add(headerBar, BorderLayout.NORTH);

        // 2. Center Workspace: Left Vertical Category Nav + Center/Right Guide Content
        JPanel workspace = new JPanel(new BorderLayout(0, 0));
        workspace.setBackground(BG_STONE_DARK);
        enableShiftDrag(workspace);

        // 2a. Left Vertical Navigation Strip
        buildLeftVerticalNav();
        workspace.add(leftNavPanel, BorderLayout.WEST);

        // 2b. Center/Right Guide Content Panel
        JPanel shopsTab = buildShopsTabPanel();
        JPanel monstersTab = buildMonstersTabPanel();
        JPanel slayerTab = buildSlayerTabPanel();
        JPanel searchTab = buildSearchTabPanel();

        tabCardsPanel.setBackground(BG_STONE_DARK);
        tabCardsPanel.add(shopsTab, TAB_SHOPS);
        tabCardsPanel.add(monstersTab, TAB_MONSTERS);
        tabCardsPanel.add(slayerTab, TAB_SLAYER);
        tabCardsPanel.add(searchTab, TAB_SEARCH);

        workspace.add(tabCardsPanel, BorderLayout.CENTER);
        mainGuidePanel.add(workspace, BorderLayout.CENTER);

        // 3. Bottom Action Footer Bar
        JPanel footerBar = buildActionFooterBar();
        mainGuidePanel.add(footerBar, BorderLayout.SOUTH);
    }

    // ==========================================
    // TOP HEADER BAR & DOCKING CONTROLS
    // ==========================================
    private JPanel buildHeaderBar()
    {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(BG_STONE_HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, BORDER_GOLD),
            new EmptyBorder(8, 14, 8, 14)
        ));
        enableWindowDragging(header);

        // Left Title Banner
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titlePanel.setBackground(BG_STONE_HEADER);
        enableWindowDragging(titlePanel);

        JLabel titleLabel = new JLabel("OmniHUD — Directory");
        titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(TITLE_GOLD);
        titlePanel.add(titleLabel);

        JLabel subtitleLabel = new JLabel("• In-Game Reference Guide");
        subtitleLabel.setFont(FontManager.getRunescapeSmallFont());
        subtitleLabel.setForeground(TEXT_MUTED);
        titlePanel.add(subtitleLabel);

        header.add(titlePanel, BorderLayout.WEST);

        // Right Controls: View Toggle, Undock/Dock, Close 'X'
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        controlsPanel.setBackground(BG_STONE_HEADER);

        styleStoneButton(viewModeToggleBtn, TITLE_GOLD);
        viewModeToggleBtn.setToolTipText("Switch between OSRS Skill Guide Row Cards and Sortable Spreadsheet Grid");
        viewModeToggleBtn.addActionListener(e -> toggleViewMode());
        controlsPanel.add(viewModeToggleBtn);

        styleStoneButton(undockDockButton, MAP_CYAN);
        undockDockButton.setToolTipText("Dock or undock the guide between in-client overlay and standalone window");
        undockDockButton.addActionListener(e -> toggleDockMode());
        controlsPanel.add(undockDockButton);

        JButton closeBtn = new JButton("✕");
        styleStoneButton(closeBtn, Color.WHITE);
        closeBtn.setPreferredSize(new Dimension(30, 26));
        closeBtn.setToolTipText("Close Guide");
        closeBtn.addActionListener(e -> closeGuide());
        controlsPanel.add(closeBtn);

        header.add(controlsPanel, BorderLayout.EAST);
        return header;
    }

    public void toggleViewMode()
    {
        guideViewMode = !guideViewMode;
        viewModeToggleBtn.setText(guideViewMode ? "Guide View" : "Table Grid");
        String cardKey = guideViewMode ? "CARDS" : "TABLE";

        shopViewCardLayout.show(shopViewContainer, cardKey);
        monsterViewCardLayout.show(monsterViewContainer, cardKey);
        slayerViewCardLayout.show(slayerViewContainer, cardKey);
        searchViewCardLayout.show(searchViewContainer, cardKey);

        if (guideViewMode)
        {
            refreshActiveCardsView();
        }
    }

    public void setGuideViewMode(boolean enable)
    {
        if (this.guideViewMode != enable)
        {
            toggleViewMode();
        }
    }

    public boolean isGuideViewMode()
    {
        return guideViewMode;
    }

    // ==========================================
    // DUAL-MODE WINDOW / IN-CLIENT MODAL DOCKING
    // ==========================================

    /** Test/embedding hook: when set, the dialog never realises a real window. */
    public void setWindowRealizationSuppressed(boolean suppressed)
    {
        this.windowRealizationSuppressed = suppressed;
    }

    public void toggleDockMode()
    {
        if (undocked)
        {
            dockInGame();
        }
        else
        {
            undockToWindow();
        }
    }

    public void undockToWindow()
    {
        this.undocked = true;
        undockDockButton.setText("Dock In-Game");

        // Hide in-client modal if active
        if (inClientModalDialog != null && inClientModalDialog.isVisible())
        {
            inClientModalDialog.setVisible(false);
            inClientModalDialog.getContentPane().removeAll();
        }

        // Attach guide to JFrame
        getContentPane().removeAll();
        getContentPane().add(mainGuidePanel, BorderLayout.CENTER);
        revalidate();
        repaint();

        if (!windowRealizationSuppressed)
        {
            super.setVisible(true);
            toFront();
        }
        setFeedback("Guide undocked to standalone window for multi-monitor use.");
    }

    public void dockInGame()
    {
        this.undocked = false;
        undockDockButton.setText("Undock");

        // Hide external JFrame
        super.setVisible(false);
        getContentPane().removeAll();

        // Check if there is an active client parent frame
        Frame clientFrame = findClientFrame();
        if (clientFrame != null)
        {
            if (inClientModalDialog == null || inClientModalDialog.getOwner() != clientFrame)
            {
                inClientModalDialog = new JDialog(clientFrame, false);
                inClientModalDialog.setUndecorated(true);
                inClientModalDialog.setBackground(new Color(0, 0, 0, 0));
            }

            inClientModalDialog.getContentPane().removeAll();
            inClientModalDialog.getContentPane().setLayout(new BorderLayout());
            inClientModalDialog.getContentPane().add(mainGuidePanel, BorderLayout.CENTER);

            // Size and center over client frame
            Dimension clientSize = clientFrame.getSize();
            int w = Math.min(1240, Math.max(860, clientSize.width - 40));
            int h = Math.min(740, Math.max(520, clientSize.height - 40));
            inClientModalDialog.setSize(w, h);
            inClientModalDialog.setLocationRelativeTo(clientFrame);

            if (!windowRealizationSuppressed)
            {
                inClientModalDialog.setVisible(true);
                inClientModalDialog.toFront();
            }
            setFeedback("Guide docked inside RuneLite client viewport.");
        }
        else
        {
            // Fallback for standalone/testing environment
            getContentPane().add(mainGuidePanel, BorderLayout.CENTER);
            if (!windowRealizationSuppressed)
            {
                super.setVisible(true);
                toFront();
            }
        }
    }

    public boolean isUndocked()
    {
        return undocked;
    }

    public void setUndocked(boolean undocked)
    {
        if (undocked) undockToWindow();
        else dockInGame();
    }

    private Frame findClientFrame()
    {
        Frame[] frames = Frame.getFrames();
        if (frames != null)
        {
            for (Frame f : frames)
            {
                if (f != this && f.isVisible() && (f.getTitle().contains("RuneLite") || f.getTitle().contains("Old School")))
                {
                    return f;
                }
            }
            for (Frame f : frames)
            {
                if (f != this && f.isVisible())
                {
                    return f;
                }
            }
        }
        return null;
    }

    public void closeGuide()
    {
        if (inClientModalDialog != null)
        {
            inClientModalDialog.setVisible(false);
        }
        super.setVisible(false);
    }

    @Override
    public void setVisible(boolean visible)
    {
        if (windowRealizationSuppressed)
        {
            return;
        }
        if (visible)
        {
            if (undocked)
            {
                super.setVisible(true);
                toFront();
            }
            else
            {
                Frame clientFrame = findClientFrame();
                if (clientFrame != null)
                {
                    dockInGame();
                }
                else
                {
                    super.setVisible(true);
                    toFront();
                }
            }
        }
        else
        {
            closeGuide();
        }
    }

    // ==========================================
    // LEFT VERTICAL NAVIGATION & SUB-FILTERS
    // ==========================================
    private void buildLeftVerticalNav()
    {
        leftNavPanel.setLayout(new BorderLayout(0, 0));
        leftNavPanel.setBackground(BG_STONE_DARK);
        leftNavPanel.setPreferredSize(new Dimension(210, 0));
        leftNavPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 2, BORDER_GOLD),
            new EmptyBorder(6, 6, 6, 6)
        ));

        // Top: 4 Main Vertical Category Tabs
        JPanel navButtonsPanel = new JPanel(new GridLayout(4, 1, 0, 4));
        navButtonsPanel.setBackground(BG_STONE_DARK);

        styleVerticalNavButton(navShopsBtn, true);
        navShopsBtn.addActionListener(e -> selectTab(TAB_SHOPS));
        navButtonsPanel.add(navShopsBtn);

        styleVerticalNavButton(navMonstersBtn, false);
        navMonstersBtn.addActionListener(e -> selectTab(TAB_MONSTERS));
        navButtonsPanel.add(navMonstersBtn);

        styleVerticalNavButton(navSlayerBtn, false);
        navSlayerBtn.addActionListener(e -> selectTab(TAB_SLAYER));
        navButtonsPanel.add(navSlayerBtn);

        styleVerticalNavButton(navSearchBtn, false);
        navSearchBtn.addActionListener(e -> selectTab(TAB_SEARCH));
        navButtonsPanel.add(navSearchBtn);

        leftNavPanel.add(navButtonsPanel, BorderLayout.NORTH);

        // Center: Sub-Filters Section with Stone Bevel
        subFiltersContainer.setLayout(new BoxLayout(subFiltersContainer, BoxLayout.Y_AXIS));
        subFiltersContainer.setBackground(BG_STONE_PANEL);
        subFiltersContainer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_STONE_OUTER, 1),
            new EmptyBorder(6, 4, 6, 4)
        ));

        JScrollPane subFilterScroll = new JScrollPane(subFiltersContainer);
        styleScrollPane(subFilterScroll);
        subFilterScroll.setBorder(BorderFactory.createEmptyBorder());
        leftNavPanel.add(subFilterScroll, BorderLayout.CENTER);

        rebuildSubFiltersForActiveTab();
    }

    private void styleVerticalNavButton(JButton btn, boolean active)
    {
        btn.setFont(FontManager.getRunescapeBoldFont());
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(198, 36));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        if (active)
        {
            btn.setBackground(BG_STONE_HEADER);
            btn.setForeground(TITLE_GOLD);
            btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_GOLD_BRIGHT, 2),
                new EmptyBorder(4, 10, 4, 10)
            ));
        }
        else
        {
            btn.setBackground(BG_STONE_DARK);
            btn.setForeground(TEXT_MUTED);
            btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_STONE_HIGHLIGHT, 1),
                new EmptyBorder(4, 10, 4, 10)
            ));
        }
    }

    private void rebuildSubFiltersForActiveTab()
    {
        subFiltersContainer.removeAll();

        JLabel subHeader = new JLabel("  CATEGORIES & FILTERS");
        subHeader.setFont(FontManager.getRunescapeSmallFont());
        subHeader.setForeground(TITLE_GOLD);
        subHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        subFiltersContainer.add(subHeader);
        subFiltersContainer.add(Box.createVerticalStrut(4));

        String[] filters;
        if (TAB_SHOPS.equals(activeTab))
        {
            filters = new String[]{
                "All Categories", "Runes & Magic", "Armour & Shields", "Weapons & Ammo", "Food & Potions", "General Stores", "Charter Ships"
            };
        }
        else if (TAB_MONSTERS.equals(activeTab))
        {
            filters = new String[]{
                "All Monsters", "Bosses", "Slayer Mobs", "Wilderness", "Dragons", "Demons", "Undead"
            };
        }
        else if (TAB_SLAYER.equals(activeTab))
        {
            filters = new String[]{
                "All Masters", "Turael / Spria", "Krystilia", "Mazchna / Achates", "Vannaka", "Chaeldar", "Konar", "Nieve / Steve", "Duradel", "Kuradal"
            };
        }
        else
        {
            filters = new String[]{
                "All Sources", "Shops Only", "Monster Drops Only", "Members Only", "Free to Play"
            };
        }

        for (String filter : filters)
        {
            JButton btn = new JButton(filter);
            boolean isSelected = activeSubFilter.equalsIgnoreCase(filter) || (activeSubFilter.startsWith("All") && filter.startsWith("All"));
            styleSubFilterButton(btn, isSelected);
            btn.setAlignmentX(Component.LEFT_ALIGNMENT);
            btn.setMaximumSize(new Dimension(190, 26));
            btn.addActionListener(e -> {
                this.activeSubFilter = filter;
                rebuildSubFiltersForActiveTab();
                handleSubFilterSelected(filter);
            });
            subFiltersContainer.add(btn);
            subFiltersContainer.add(Box.createVerticalStrut(2));
        }

        subFiltersContainer.revalidate();
        subFiltersContainer.repaint();
    }

    private void styleSubFilterButton(JButton btn, boolean isSelected)
    {
        btn.setFont(FontManager.getRunescapeFont());
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        if (isSelected)
        {
            btn.setBackground(BG_STONE_HEADER);
            btn.setForeground(PRICE_GOLD);
            btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_GOLD, 1),
                new EmptyBorder(2, 8, 2, 8)
            ));
        }
        else
        {
            btn.setBackground(BG_STONE_PANEL);
            btn.setForeground(TEXT_PARCHMENT);
            btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_STONE_SHADOW, 1),
                new EmptyBorder(2, 8, 2, 8)
            ));
        }
    }

    private void handleSubFilterSelected(String subFilter)
    {
        if (TAB_SHOPS.equals(activeTab))
        {
            if (subFilter.startsWith("All")) shopSearchField.setText("");
            else if (subFilter.contains("Rune") || subFilter.contains("Magic")) shopSearchField.setText("magic");
            else if (subFilter.contains("Armour")) shopSearchField.setText("armour");
            else if (subFilter.contains("Weapon")) shopSearchField.setText("sword");
            else if (subFilter.contains("Food") || subFilter.contains("Potion")) shopSearchField.setText("food");
            else if (subFilter.contains("General")) shopSearchField.setText("general store");
            else if (subFilter.contains("Charter")) shopSearchField.setText("charter");
            applyShopFilters();
        }
        else if (TAB_MONSTERS.equals(activeTab))
        {
            if (subFilter.startsWith("All")) monsterCategoryDropdown.setSelectedItem("All Categories");
            else if (subFilter.contains("Boss")) monsterCategoryDropdown.setSelectedItem("Bosses");
            else if (subFilter.contains("Slayer")) monsterCategoryDropdown.setSelectedItem("Slayer");
            else if (subFilter.contains("Wilderness")) monsterCategoryDropdown.setSelectedItem("Wilderness");
            else if (subFilter.contains("Dragon")) monsterCategoryDropdown.setSelectedItem("Dragons");
            else if (subFilter.contains("Demon")) monsterCategoryDropdown.setSelectedItem("Demons");
            else if (subFilter.contains("Undead")) monsterCategoryDropdown.setSelectedItem("Undead");
            applyMonsterFilters();
        }
        else if (TAB_SLAYER.equals(activeTab))
        {
            if (subFilter.startsWith("All")) slayerMasterDropdown.setSelectedItem("All Slayer Masters");
            else
            {
                String name = subFilter.split("/")[0].trim();
                for (int i = 0; i < slayerMasterDropdown.getItemCount(); i++)
                {
                    String item = slayerMasterDropdown.getItemAt(i);
                    if (item.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
                    {
                        slayerMasterDropdown.setSelectedIndex(i);
                        break;
                    }
                }
            }
            applySlayerFilters();
        }
        else if (TAB_SEARCH.equals(activeTab))
        {
            if (subFilter.contains("Shop")) searchSourceTypeDropdown.setSelectedItem("Shops & Markets Only");
            else if (subFilter.contains("Drop")) searchSourceTypeDropdown.setSelectedItem("Monster Drops Only");
            else if (subFilter.contains("Members")) searchMembDropdown.setSelectedItem("Members Only");
            else if (subFilter.contains("Free")) searchMembDropdown.setSelectedItem("F2P Only");
            else
            {
                searchSourceTypeDropdown.setSelectedItem("All Sources (Shops & Drops)");
                searchMembDropdown.setSelectedItem("All Access");
            }
            applySearchFilters();
        }
    }

    public void selectTab(String tabKey)
    {
        this.activeTab = tabKey;
        tabCardLayout.show(tabCardsPanel, tabKey);

        styleVerticalNavButton(navShopsBtn, TAB_SHOPS.equals(tabKey));
        styleVerticalNavButton(navMonstersBtn, TAB_MONSTERS.equals(tabKey));
        styleVerticalNavButton(navSlayerBtn, TAB_SLAYER.equals(tabKey));
        styleVerticalNavButton(navSearchBtn, TAB_SEARCH.equals(tabKey));

        this.activeSubFilter = "All";
        rebuildSubFiltersForActiveTab();

        if (TAB_SHOPS.equals(tabKey))
        {
            if (shopDatabase != null && !shopDatabase.isLoaded()) shopDatabase.load();
            populateTownDropdown();
            applyShopFilters();
        }
        else if (TAB_MONSTERS.equals(tabKey))
        {
            if (monsterDatabase != null && !monsterDatabase.isLoaded()) monsterDatabase.load();
            applyMonsterFilters();
        }
        else if (TAB_SLAYER.equals(tabKey))
        {
            if (slayerTaskManager != null && monsterDatabase != null)
            {
                if (!monsterDatabase.isLoaded()) monsterDatabase.load();
                slayerTaskManager.setMonsterDatabase(monsterDatabase);
            }
            populateSlayerMasterDropdown();
            applySlayerFilters();
        }
        else if (TAB_SEARCH.equals(tabKey))
        {
            if (shopDatabase != null && !shopDatabase.isLoaded()) shopDatabase.load();
            if (monsterDatabase != null && !monsterDatabase.isLoaded()) monsterDatabase.load();
            applySearchFilters();
        }

        if (addCartBtn != null)
        {
            addCartBtn.setVisible(TAB_SHOPS.equals(tabKey) || TAB_SEARCH.equals(tabKey));
        }

        refreshActiveCardsView();
    }

    private void refreshActiveCardsView()
    {
        if (!guideViewMode) return;

        if (TAB_SHOPS.equals(activeTab)) rebuildShopGuideCards();
        else if (TAB_MONSTERS.equals(activeTab)) rebuildMonsterGuideCards();
        else if (TAB_SLAYER.equals(activeTab)) rebuildSlayerGuideCards();
        else if (TAB_SEARCH.equals(activeTab)) rebuildSearchGuideCards();
    }

    // ==========================================
    // TAB 1: SHOPS & MARKETS
    // ==========================================
    private JPanel buildShopsTabPanel()
    {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(BG_STONE_PANEL);
        panel.setBorder(new EmptyBorder(6, 8, 6, 8));

        // Filter Header
        JPanel topBar = new JPanel(new BorderLayout(8, 4));
        topBar.setBackground(BG_STONE_PANEL);

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        searchRow.setBackground(BG_STONE_PANEL);

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(FontManager.getRunescapeBoldFont());
        searchLabel.setForeground(TITLE_GOLD);
        searchRow.add(searchLabel);

        styleTextField(shopSearchField);
        shopSearchField.setToolTipText("Filter by item name, shop name, town, or NPC keeper");
        setupDebouncedSearch(shopSearchField, this::applyShopFilters);
        searchRow.add(shopSearchField);

        JButton clearBtn = new JButton("✖");
        styleMiniButton(clearBtn);
        clearBtn.setToolTipText("Clear search text");
        clearBtn.addActionListener(e -> {
            shopSearchField.setText("");
            applyShopFilters();
        });
        searchRow.add(clearBtn);

        JLabel townLabel = new JLabel("Town:");
        townLabel.setFont(FontManager.getRunescapeBoldFont());
        townLabel.setForeground(TITLE_GOLD);
        searchRow.add(townLabel);

        styleDropdown(shopFilterTownDropdown);
        shopFilterTownDropdown.addItem("All Towns / Settlements");
        shopFilterTownDropdown.addActionListener(e -> applyShopFilters());
        searchRow.add(shopFilterTownDropdown);

        JLabel membLabel = new JLabel("Type:");
        membLabel.setFont(FontManager.getRunescapeBoldFont());
        membLabel.setForeground(TITLE_GOLD);
        searchRow.add(membLabel);

        styleDropdown(shopFilterMembDropdown);
        shopFilterMembDropdown.addItem("All Items");
        shopFilterMembDropdown.addItem("Members Only");
        shopFilterMembDropdown.addItem("F2P Only");
        shopFilterMembDropdown.addActionListener(e -> applyShopFilters());
        searchRow.add(shopFilterMembDropdown);

        shopHideZeroStockCheckbox.setBackground(BG_STONE_PANEL);
        shopHideZeroStockCheckbox.setForeground(TEXT_PARCHMENT);
        shopHideZeroStockCheckbox.setFont(FontManager.getRunescapeFont());
        shopHideZeroStockCheckbox.setSelected(true);
        shopHideZeroStockCheckbox.addActionListener(e -> applyShopFilters());
        searchRow.add(shopHideZeroStockCheckbox);

        JButton columnsBtn = new JButton("Columns ▾");
        styleStoneButton(columnsBtn, TITLE_GOLD);
        columnsBtn.setToolTipText("Show or hide shop directory columns");
        columnsBtn.addActionListener(e -> showShopColumnSelectorPopup(columnsBtn, 0, columnsBtn.getHeight()));
        searchRow.add(columnsBtn);

        topBar.add(searchRow, BorderLayout.NORTH);

        shopRowCountLabel.setFont(FontManager.getRunescapeSmallFont());
        shopRowCountLabel.setForeground(TEXT_MUTED);
        shopRowCountLabel.setBorder(new EmptyBorder(0, 10, 2, 0));
        topBar.add(shopRowCountLabel, BorderLayout.SOUTH);

        panel.add(topBar, BorderLayout.NORTH);

        // Shop Table Setup (Grid View)
        shopTableModel = new DefaultTableModel(SHOP_COLUMN_NAMES, 0)
        {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }

            @Override
            public Class<?> getColumnClass(int columnIndex)
            {
                if (columnIndex == 4 || columnIndex == 5 || columnIndex == 6) return Integer.class;
                return String.class;
            }
        };

        shopTable.setModel(shopTableModel);
        shopSorter = new TableRowSorter<>(shopTableModel);
        shopTable.setRowSorter(shopSorter);

        styleTable(shopTable);
        configureTableHeader(shopTable, (header, x, y) -> showShopColumnSelectorPopup(header, x, y));

        DefaultTableCellRenderer shopRenderer = new DefaultTableCellRenderer()
        {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col)
            {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                applyRowColors(c, row, isSelected);
                setBorder(new EmptyBorder(0, 8, 0, 8));
                int modelCol = table.convertColumnIndexToModel(col);

                switch (modelCol)
                {
                    case 0:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        if (!isSelected) setForeground(MAP_CYAN);
                        break;
                    case 1:
                        setFont(FontManager.getRunescapeFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        if (!isSelected) setForeground(TEXT_PARCHMENT);
                        break;
                    case 2:
                        setFont(FontManager.getRunescapeSmallFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        if (!isSelected) setForeground(TEXT_MUTED);
                        break;
                    case 3:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        if (!isSelected) setForeground(PRICE_GOLD);
                        break;
                    case 4:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.CENTER);
                        if (value instanceof Number)
                        {
                            int stock = ((Number) value).intValue();
                            if (!isSelected) setForeground(stock > 0 ? STOCK_GREEN : STOCK_RED);
                        }
                        break;
                    case 5:
                    case 6:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.RIGHT);
                        if (value instanceof Number)
                        {
                            setText(NUMBER_FORMAT.format(((Number) value).intValue()) + " gp");
                            if (!isSelected) setForeground(PRICE_GOLD);
                        }
                        break;
                    case 7:
                    case 8:
                        setFont(FontManager.getRunescapeSmallFont());
                        setHorizontalAlignment(JLabel.CENTER);
                        if (!isSelected) setForeground(TEXT_MUTED);
                        break;
                    case 9:
                        setFont(FontManager.getRunescapeSmallFont());
                        setHorizontalAlignment(JLabel.CENTER);
                        String memb = value != null ? value.toString() : "";
                        if (!isSelected) setForeground("Members".equalsIgnoreCase(memb) ? NAME_ORANGE : TEXT_MUTED);
                        break;
                }
                return c;
            }
        };

        int[] preferredWidths = {120, 160, 110, 150, 65, 85, 85, 75, 75, 70};
        for (int i = 0; i < shopTable.getColumnCount(); i++)
        {
            TableColumn col = shopTable.getColumnModel().getColumn(i);
            col.setCellRenderer(shopRenderer);
            if (i < preferredWidths.length) col.setPreferredWidth(preferredWidths[i]);
            allShopTableColumns.add(col);
        }

        shopTable.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e) { handleShopTableMouse(e); }
            @Override
            public void mouseReleased(MouseEvent e) { handleShopTableMouse(e); }
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2)
                {
                    int viewRow = shopTable.getSelectedRow();
                    if (viewRow >= 0)
                    {
                        int modelRow = shopTable.convertRowIndexToModel(viewRow);
                        if (modelRow >= 0 && modelRow < currentShopRows.size())
                        {
                            ShopTableRowData data = currentShopRows.get(modelRow);
                            if (onFocusShopOnMap != null) onFocusShopOnMap.accept(data.shop);
                            if (onInspectShop != null) onInspectShop.accept(data.shop);
                            setFeedback("Focused map & opened " + data.shop.getName() + " in sidebar.");
                        }
                    }
                }
            }

            private void handleShopTableMouse(MouseEvent e)
            {
                if (e.isPopupTrigger())
                {
                    int viewRow = shopTable.rowAtPoint(e.getPoint());
                    if (viewRow >= 0)
                    {
                        shopTable.setRowSelectionInterval(viewRow, viewRow);
                        int modelRow = shopTable.convertRowIndexToModel(viewRow);
                        if (modelRow >= 0 && modelRow < currentShopRows.size())
                        {
                            ShopTableRowData data = currentShopRows.get(modelRow);
                            JPopupMenu popup = createShopRowContextMenu(data);
                            popup.show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(shopTable);
        styleScrollPane(tableScroll);

        // Guide Cards Setup
        shopCardsListPanel.setLayout(new BoxLayout(shopCardsListPanel, BoxLayout.Y_AXIS));
        shopCardsListPanel.setBackground(BG_STONE_DARK);
        JScrollPane cardsScroll = new JScrollPane(shopCardsListPanel);
        styleScrollPane(cardsScroll);

        shopViewContainer.setLayout(shopViewCardLayout);
        shopViewContainer.add(cardsScroll, "CARDS");
        shopViewContainer.add(tableScroll, "TABLE");

        panel.add(shopViewContainer, BorderLayout.CENTER);
        return panel;
    }

    private void rebuildShopGuideCards()
    {
        shopCardsListPanel.removeAll();
        int count = 0;
        int maxCards = 150; // Performance cap for instant smooth rendering

        List<ShopTableRowData> rows;
        synchronized (this)
        {
            rows = new ArrayList<>(currentShopRows);
        }
        for (ShopTableRowData row : rows)
        {
            JPanel card = buildShopGuideCard(row, count % 2 == 0);
            shopCardsListPanel.add(card);
            shopCardsListPanel.add(Box.createVerticalStrut(3));
            count++;
            if (count >= maxCards)
            {
                if (rows.size() > maxCards)
                {
                    JLabel moreLabel = new JLabel("... Showing first " + maxCards + " of " + NUMBER_FORMAT.format(rows.size()) + " shop items. Use search filters or switch to Table Grid for complete list.");
                    moreLabel.setFont(FontManager.getRunescapeSmallFont());
                    moreLabel.setForeground(TITLE_GOLD);
                    moreLabel.setBorder(new EmptyBorder(8, 14, 8, 14));
                    shopCardsListPanel.add(moreLabel);
                }
                break;
            }
        }

        shopCardsListPanel.revalidate();
        shopCardsListPanel.repaint();
    }

    private JPanel buildShopGuideCard(ShopTableRowData row, boolean even)
    {
        JPanel card = new JPanel(new BorderLayout(8, 0));
        card.setBackground(even ? BG_STONE_CARD : BG_STONE_CARD_ALT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_STONE_OUTER, 1),
            new EmptyBorder(6, 8, 6, 8)
        ));
        card.setMaximumSize(new Dimension(3000, 56));
        card.setPreferredSize(new Dimension(0, 56));

        // 1. Left Requirement / Price Badge
        JPanel badgePanel = new JPanel(new BorderLayout());
        badgePanel.setBackground(BG_STONE_BADGE);
        badgePanel.setPreferredSize(new Dimension(64, 44));
        badgePanel.setBorder(BorderFactory.createLineBorder(BORDER_STONE_HIGHLIGHT, 1));

        JLabel priceBadgeLabel = new JLabel(formatPriceBadge(row.item.getPrice()), SwingConstants.CENTER);
        priceBadgeLabel.setFont(FontManager.getRunescapeBoldFont());
        priceBadgeLabel.setForeground(PRICE_GOLD);
        badgePanel.add(priceBadgeLabel, BorderLayout.CENTER);

        JLabel stockSub = new JLabel(row.item.getDefaultStock() + "x stock", SwingConstants.CENTER);
        stockSub.setFont(FontManager.getRunescapeSmallFont());
        stockSub.setForeground(row.item.getDefaultStock() > 0 ? STOCK_GREEN : STOCK_RED);
        badgePanel.add(stockSub, BorderLayout.SOUTH);

        card.add(badgePanel, BorderLayout.WEST);

        // 2. Sprite & Text Details (Center)
        JPanel centerPanel = new JPanel(new BorderLayout(8, 0));
        centerPanel.setBackground(card.getBackground());

        JLabel spriteLabel = new JLabel();
        spriteLabel.setPreferredSize(new Dimension(32, 32));
        loadItemSprite(row.item.getItemId(), spriteLabel);
        centerPanel.add(spriteLabel, BorderLayout.WEST);

        JPanel details = new JPanel(new GridLayout(2, 1, 0, 2));
        details.setBackground(card.getBackground());

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        titleRow.setBackground(card.getBackground());

        JLabel nameLabel = new JLabel(row.item.getName());
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(row.shop.isMembersOnly() ? NAME_ORANGE : TITLE_GOLD);
        titleRow.add(nameLabel);

        if (row.shop.isMembersOnly())
        {
            JLabel membBadge = new JLabel("[Members]");
            membBadge.setFont(FontManager.getRunescapeSmallFont());
            membBadge.setForeground(NAME_ORANGE);
            titleRow.add(membBadge);
        }

        JLabel currencyLabel = new JLabel("(" + row.shop.getCurrency().getShortName() + ")");
        currencyLabel.setFont(FontManager.getRunescapeSmallFont());
        currencyLabel.setForeground(TEXT_MUTED);
        titleRow.add(currencyLabel);

        details.add(titleRow);

        // Subtitle / Location / Requirements
        StringBuilder subDesc = new StringBuilder("<html>");
        subDesc.append("<span style='color: #64C8FA;'>").append(row.shop.getTown()).append("</span>");
        subDesc.append(" • <span style='color: #EDE5D8;'>").append(escapeHtml(row.shop.getName())).append("</span>");
        if (row.shop.getQuestRequirement() != null && !row.shop.getQuestRequirement().isEmpty())
        {
            subDesc.append(" • <span style='color: #EF4444; font-weight: bold;'>Requires: ").append(escapeHtml(row.shop.getQuestRequirement())).append("</span>");
        }
        subDesc.append("</html>");

        JLabel descLabel = new JLabel(subDesc.toString());
        descLabel.setFont(FontManager.getRunescapeFont());
        details.add(descLabel);

        centerPanel.add(details, BorderLayout.CENTER);
        card.add(centerPanel, BorderLayout.CENTER);

        // 3. Right Action Buttons Strip
        JPanel actionStrip = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        actionStrip.setBackground(card.getBackground());

        JButton mapBtn = new JButton("Map");
        styleStoneButton(mapBtn, MAP_CYAN);
        mapBtn.addActionListener(e -> {
            if (onFocusShopOnMap != null) onFocusShopOnMap.accept(row.shop);
            setFeedback("Centered map on " + row.shop.getName() + " (" + row.shop.getTown() + ").");
        });
        actionStrip.add(mapBtn);

        JButton panelBtn = new JButton("Side Panel");
        styleStoneButton(panelBtn, Color.WHITE);
        panelBtn.addActionListener(e -> {
            if (onInspectShop != null) onInspectShop.accept(row.shop);
            if (clientToolbar != null && navButton != null) SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
            setFeedback("Opened " + row.shop.getName() + " in sidebar stock view.");
        });
        actionStrip.add(panelBtn);

        JButton cartBtn = new JButton("Cart");
        styleStoneButton(cartBtn, PRICE_GOLD);
        cartBtn.addActionListener(e -> {
            cartManager.addItem(row.item, row.shop, 1);
            setFeedback("✓ Added 1x " + row.item.getName() + " to Shopping Cart!");
        });
        actionStrip.add(cartBtn);

        JButton wikiBtn = new JButton("Wiki");
        styleStoneButton(wikiBtn, new Color(147, 197, 253));
        wikiBtn.addActionListener(e -> openWikiPage(row.item.getName()));
        actionStrip.add(wikiBtn);

        card.add(actionStrip, BorderLayout.EAST);
        return card;
    }

    // ==========================================
    // TAB 2: BESTIARY
    // ==========================================
    private JPanel buildMonstersTabPanel()
    {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(BG_STONE_PANEL);
        panel.setBorder(new EmptyBorder(6, 8, 6, 8));

        // Filter Header
        JPanel topBar = new JPanel(new BorderLayout(8, 4));
        topBar.setBackground(BG_STONE_PANEL);

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        searchRow.setBackground(BG_STONE_PANEL);

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(FontManager.getRunescapeBoldFont());
        searchLabel.setForeground(TITLE_GOLD);
        searchRow.add(searchLabel);

        styleTextField(monsterSearchField);
        monsterSearchField.setToolTipText("Filter by monster name, spawn area/dungeon, drops, or weakness");
        setupDebouncedSearch(monsterSearchField, this::applyMonsterFilters);
        searchRow.add(monsterSearchField);

        JButton clearBtn = new JButton("✖");
        styleMiniButton(clearBtn);
        clearBtn.setToolTipText("Clear search text");
        clearBtn.addActionListener(e -> {
            monsterSearchField.setText("");
            applyMonsterFilters();
        });
        searchRow.add(clearBtn);

        JLabel catLabel = new JLabel("Category:");
        catLabel.setFont(FontManager.getRunescapeBoldFont());
        catLabel.setForeground(TITLE_GOLD);
        searchRow.add(catLabel);

        styleDropdown(monsterCategoryDropdown);
        monsterCategoryDropdown.addItem("All Categories");
        monsterCategoryDropdown.addItem("Bosses");
        monsterCategoryDropdown.addItem("Slayer");
        monsterCategoryDropdown.addItem("Dragons");
        monsterCategoryDropdown.addItem("Demons");
        monsterCategoryDropdown.addItem("Undead");
        monsterCategoryDropdown.addItem("Wilderness");
        monsterCategoryDropdown.addItem("F2P");
        monsterCategoryDropdown.addItem("Members");
        monsterCategoryDropdown.addActionListener(e -> applyMonsterFilters());
        searchRow.add(monsterCategoryDropdown);

        JLabel wildyLabel = new JLabel("Zone:");
        wildyLabel.setFont(FontManager.getRunescapeBoldFont());
        wildyLabel.setForeground(TITLE_GOLD);
        searchRow.add(wildyLabel);

        styleDropdown(monsterWildyDropdown);
        monsterWildyDropdown.addItem("All Zones");
        monsterWildyDropdown.addItem("Wilderness Only");
        monsterWildyDropdown.addItem("Safe Zones Only");
        monsterWildyDropdown.addActionListener(e -> applyMonsterFilters());
        searchRow.add(monsterWildyDropdown);

        JLabel slayerLabel = new JLabel("Slayer:");
        slayerLabel.setFont(FontManager.getRunescapeBoldFont());
        slayerLabel.setForeground(TITLE_GOLD);
        searchRow.add(slayerLabel);

        styleDropdown(monsterSlayerDropdown);
        monsterSlayerDropdown.addItem("All Monsters");
        monsterSlayerDropdown.addItem("Slayer Req Only (>1)");
        monsterSlayerDropdown.addItem("No Slayer Req");
        monsterSlayerDropdown.addActionListener(e -> applyMonsterFilters());
        searchRow.add(monsterSlayerDropdown);

        JButton columnsBtn = new JButton("Columns ▾");
        styleStoneButton(columnsBtn, TITLE_GOLD);
        columnsBtn.setToolTipText("Show or hide bestiary columns");
        columnsBtn.addActionListener(e -> showMonsterColumnSelectorPopup(columnsBtn, 0, columnsBtn.getHeight()));
        searchRow.add(columnsBtn);

        topBar.add(searchRow, BorderLayout.NORTH);

        monsterRowCountLabel.setFont(FontManager.getRunescapeSmallFont());
        monsterRowCountLabel.setForeground(TEXT_MUTED);
        monsterRowCountLabel.setBorder(new EmptyBorder(0, 10, 2, 0));
        topBar.add(monsterRowCountLabel, BorderLayout.SOUTH);

        panel.add(topBar, BorderLayout.NORTH);

        // Monster Table Setup
        monsterTableModel = new DefaultTableModel(MONSTER_COLUMN_NAMES, 0)
        {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }

            @Override
            public Class<?> getColumnClass(int columnIndex)
            {
                if (columnIndex == 2 || columnIndex == 3 || columnIndex == 4 || columnIndex == 7)
                {
                    return Integer.class;
                }
                return String.class;
            }
        };

        monsterTable.setModel(monsterTableModel);
        monsterSorter = new TableRowSorter<>(monsterTableModel);
        monsterTable.setRowSorter(monsterSorter);

        styleTable(monsterTable);
        configureTableHeader(monsterTable, (header, x, y) -> showMonsterColumnSelectorPopup(header, x, y));

        DefaultTableCellRenderer monsterRenderer = new DefaultTableCellRenderer()
        {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col)
            {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                applyRowColors(c, row, isSelected);
                setBorder(new EmptyBorder(0, 8, 0, 8));
                int modelCol = table.convertColumnIndexToModel(col);

                switch (modelCol)
                {
                    case 0:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        if (!isSelected) setForeground(PRICE_GOLD);
                        break;
                    case 1:
                        setFont(FontManager.getRunescapeFont());
                        setHorizontalAlignment(JLabel.CENTER);
                        if (!isSelected) setForeground(TEXT_PARCHMENT);
                        break;
                    case 2:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.CENTER);
                        if (!isSelected) setForeground(COMBAT_LVL_GOLD);
                        break;
                    case 3:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.RIGHT);
                        if (value instanceof Number)
                        {
                            setText(NUMBER_FORMAT.format(((Number) value).intValue()));
                        }
                        if (!isSelected) setForeground(HP_RED);
                        break;
                    case 4:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.CENTER);
                        if (!isSelected) setForeground(MAX_HIT_ORANGE);
                        break;
                    case 5:
                        setFont(FontManager.getRunescapeFont());
                        setHorizontalAlignment(JLabel.CENTER);
                        if (!isSelected) setForeground(TEXT_PARCHMENT);
                        break;
                    case 6:
                        setFont(FontManager.getRunescapeFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        String weak = value != null ? value.toString() : "";
                        if (!isSelected) setForeground("None".equalsIgnoreCase(weak) ? TEXT_MUTED : WEAKNESS_MINT);
                        break;
                    case 7:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.CENTER);
                        if (value instanceof Number)
                        {
                            int req = ((Number) value).intValue();
                            setText(req > 1 ? String.valueOf(req) : "None");
                            if (!isSelected) setForeground(req > 1 ? REQ_RED : TEXT_MUTED);
                        }
                        break;
                    case 8:
                        setFont(FontManager.getRunescapeSmallFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        String quest = value != null ? value.toString() : "";
                        if (!isSelected) setForeground("None".equalsIgnoreCase(quest) || quest.isEmpty() ? TEXT_MUTED : REQ_RED);
                        break;
                    case 9:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        if (!isSelected) setForeground(MAP_CYAN);
                        break;
                    case 10:
                        setFont(FontManager.getRunescapeSmallFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        if (!isSelected) setForeground(new Color(186, 230, 253));
                        break;
                }
                return c;
            }
        };

        int[] preferredWidths = {150, 85, 55, 55, 60, 75, 95, 75, 110, 160, 200};
        for (int i = 0; i < monsterTable.getColumnCount(); i++)
        {
            TableColumn col = monsterTable.getColumnModel().getColumn(i);
            col.setCellRenderer(monsterRenderer);
            if (i < preferredWidths.length) col.setPreferredWidth(preferredWidths[i]);
            allMonsterTableColumns.add(col);
        }

        ToolTipManager.sharedInstance().registerComponent(monsterTable);
        monsterTable.addMouseMotionListener(new MouseAdapter()
        {
            @Override
            public void mouseMoved(MouseEvent e)
            {
                int viewCol = monsterTable.columnAtPoint(e.getPoint());
                int viewRow = monsterTable.rowAtPoint(e.getPoint());
                int modelCol = viewCol >= 0 ? monsterTable.convertColumnIndexToModel(viewCol) : -1;
                int modelRow = viewRow >= 0 ? monsterTable.convertRowIndexToModel(viewRow) : -1;

                if (modelCol == 0 || modelCol == 9)
                {
                    monsterTable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
                else
                {
                    monsterTable.setCursor(Cursor.getDefaultCursor());
                }

                if (modelCol == 10 && modelRow >= 0 && modelRow < currentMonsterRows.size())
                {
                    monsterTable.setToolTipText(buildDropsTooltip(currentMonsterRows.get(modelRow)));
                }
                else
                {
                    monsterTable.setToolTipText(null);
                }
            }
        });

        monsterTable.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e) { handleMonsterTableMouse(e); }
            @Override
            public void mouseReleased(MouseEvent e) { handleMonsterTableMouse(e); }
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getButton() == MouseEvent.BUTTON1)
                {
                    int viewRow = monsterTable.getSelectedRow();
                    int viewCol = monsterTable.getSelectedColumn();
                    if (viewRow < 0) return;
                    int modelRow = monsterTable.convertRowIndexToModel(viewRow);
                    int modelCol = viewCol >= 0 ? monsterTable.convertColumnIndexToModel(viewCol) : -1;

                    if (modelRow >= 0 && modelRow < currentMonsterRows.size())
                    {
                        MonsterTableRowData data = currentMonsterRows.get(modelRow);
                        if (e.getClickCount() == 2)
                        {
                            panMapToMonsterRow(data);
                            if (onInspectMonster != null) onInspectMonster.accept(data.monster);
                            setFeedback("Focused map & opened " + data.monster.getName() + " in sidebar.");
                        }
                        else if (modelCol == 0 || modelCol == 9)
                        {
                            panMapToMonsterRow(data);
                            setFeedback("Centered map on " + data.monster.getName() + " spawn zone.");
                        }
                    }
                }
            }

            private void handleMonsterTableMouse(MouseEvent e)
            {
                if (e.isPopupTrigger())
                {
                    int viewRow = monsterTable.rowAtPoint(e.getPoint());
                    if (viewRow >= 0)
                    {
                        monsterTable.setRowSelectionInterval(viewRow, viewRow);
                        int modelRow = monsterTable.convertRowIndexToModel(viewRow);
                        if (modelRow >= 0 && modelRow < currentMonsterRows.size())
                        {
                            MonsterTableRowData data = currentMonsterRows.get(modelRow);
                            JPopupMenu popup = createMonsterRowContextMenu(data);
                            popup.show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(monsterTable);
        styleScrollPane(tableScroll);

        // Guide Cards Split View Setup (Combat Achievements & Collection Log Parity)
        JPanel bestiarySplitPanel = new JPanel(new BorderLayout(6, 0));
        bestiarySplitPanel.setBackground(BG_STONE_DARK);

        monsterCardsListPanel.setLayout(new BoxLayout(monsterCardsListPanel, BoxLayout.Y_AXIS));
        monsterCardsListPanel.setBackground(BG_STONE_DARK);
        JScrollPane cardsScroll = new JScrollPane(monsterCardsListPanel);
        styleScrollPane(cardsScroll);
        cardsScroll.setPreferredSize(new Dimension(310, 0));
        cardsScroll.setMinimumSize(new Dimension(240, 0));
        bestiarySplitPanel.add(cardsScroll, BorderLayout.WEST);

        combatAchievementsInspectorPanel.setLayout(new BoxLayout(combatAchievementsInspectorPanel, BoxLayout.Y_AXIS));
        combatAchievementsInspectorPanel.setBackground(BG_STONE_DARK);
        combatAchievementsInspectorPanel.setBorder(new EmptyBorder(0, 4, 0, 4));
        JScrollPane inspectorScroll = new JScrollPane(combatAchievementsInspectorPanel);
        styleScrollPane(inspectorScroll);
        inspectorScroll.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 2, 0, 0, BORDER_GOLD),
            new EmptyBorder(0, 0, 0, 0)
        ));
        bestiarySplitPanel.add(inspectorScroll, BorderLayout.CENTER);

        monsterViewContainer.setLayout(monsterViewCardLayout);
        monsterViewContainer.add(bestiarySplitPanel, "CARDS");
        monsterViewContainer.add(tableScroll, "TABLE");

        panel.add(monsterViewContainer, BorderLayout.CENTER);
        return panel;
    }

    private void rebuildMonsterGuideCards()
    {
        monsterCardsListPanel.removeAll();
        int count = 0;
        int maxCards = 400;

        Set<String> seenMonsters = new HashSet<>();
        List<MonsterTableRowData> rows;
        synchronized (this)
        {
            rows = new ArrayList<>(currentMonsterRows);
        }

        Monster firstMonster = null;
        boolean selectedStillPresent = false;

        for (MonsterTableRowData row : rows)
        {
            String key = row.monster.getName() + "-" + row.monster.getCombatLevel();
            if (!seenMonsters.add(key))
            {
                continue;
            }

            if (firstMonster == null)
            {
                firstMonster = row.monster;
            }
            if (selectedBestiaryMonster != null && selectedBestiaryMonster.getName().equalsIgnoreCase(row.monster.getName())
                && selectedBestiaryMonster.getCombatLevel() == row.monster.getCombatLevel())
            {
                selectedStillPresent = true;
            }

            JPanel card = buildMonsterIndexCard(row, count % 2 == 0);
            monsterCardsListPanel.add(card);
            monsterCardsListPanel.add(Box.createVerticalStrut(3));
            count++;
            if (count >= maxCards)
            {
                if (rows.size() > maxCards)
                {
                    JLabel moreLabel = new JLabel("... Showing first " + maxCards + " monsters. Use search or Table Grid view.");
                    moreLabel.setFont(FontManager.getRunescapeSmallFont());
                    moreLabel.setForeground(TITLE_GOLD);
                    moreLabel.setBorder(new EmptyBorder(6, 10, 6, 10));
                    monsterCardsListPanel.add(moreLabel);
                }
                break;
            }
        }

        if (!selectedStillPresent)
        {
            selectedBestiaryMonster = firstMonster;
        }

        updateCombatAchievementsInspector(selectedBestiaryMonster);

        monsterCardsListPanel.revalidate();
        monsterCardsListPanel.repaint();
    }

    private JPanel buildMonsterIndexCard(MonsterTableRowData row, boolean even)
    {
        boolean isSelected = selectedBestiaryMonster != null
            && selectedBestiaryMonster.getName().equalsIgnoreCase(row.monster.getName())
            && selectedBestiaryMonster.getCombatLevel() == row.monster.getCombatLevel();

        JPanel card = new JPanel(new BorderLayout(6, 0));
        card.setBackground(isSelected ? BG_STONE_HEADER : (even ? BG_STONE_CARD : BG_STONE_CARD_ALT));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(isSelected ? BORDER_GOLD_BRIGHT : BORDER_STONE_OUTER, isSelected ? 2 : 1),
            new EmptyBorder(4, 6, 4, 6)
        ));
        card.setMaximumSize(new Dimension(3000, 54));
        card.setPreferredSize(new Dimension(0, 54));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        card.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2)
                {
                    panMapToMonsterRow(row);
                    if (onInspectMonster != null) onInspectMonster.accept(row.monster);
                    if (clientToolbar != null && navButton != null) SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
                    setFeedback("Opened " + row.monster.getName() + " in sidebar bestiary view.");
                }
                else
                {
                    selectedBestiaryMonster = row.monster;
                    rebuildMonsterGuideCards();
                    setFeedback("Selected " + row.monster.getName() + " (Lvl " + row.monster.getCombatLevel() + "). Click Map to locate.");
                }
            }
        });

        // 1. Combat Level Badge Box
        JPanel badgePanel = new JPanel(new BorderLayout());
        badgePanel.setBackground(BG_STONE_BADGE);
        badgePanel.setPreferredSize(new Dimension(52, 44));
        badgePanel.setBorder(BorderFactory.createLineBorder(BORDER_STONE_HIGHLIGHT, 1));

        JLabel lvlBadge = new JLabel("Lvl " + row.monster.getCombatLevel(), SwingConstants.CENTER);
        lvlBadge.setFont(FontManager.getRunescapeBoldFont());
        lvlBadge.setForeground(COMBAT_LVL_GOLD);
        badgePanel.add(lvlBadge, BorderLayout.CENTER);

        JLabel hpSub = new JLabel(row.monster.getHitpoints() + " HP", SwingConstants.CENTER);
        hpSub.setFont(FontManager.getRunescapeSmallFont());
        hpSub.setForeground(HP_RED);
        badgePanel.add(hpSub, BorderLayout.SOUTH);

        card.add(badgePanel, BorderLayout.WEST);

        // 2. Center Info
        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 1));
        centerPanel.setBackground(card.getBackground());

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        titleRow.setBackground(card.getBackground());

        JLabel nameLabel = new JLabel(row.monster.getName());
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(row.monster.isMembers() ? NAME_ORANGE : TITLE_GOLD);
        titleRow.add(nameLabel);

        JLabel catBadge = new JLabel("[" + (row.monster.getCategory() != null ? row.monster.getCategory() : "Monster") + "]");
        catBadge.setFont(FontManager.getRunescapeSmallFont());
        catBadge.setForeground(TEXT_MUTED);
        titleRow.add(catBadge);

        centerPanel.add(titleRow);

        JPanel subRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        subRow.setBackground(card.getBackground());

        JLabel locLabel = new JLabel("📍 " + buildLocationDisplay(row.zone));
        locLabel.setFont(FontManager.getRunescapeSmallFont());
        locLabel.setForeground(MAP_CYAN);
        subRow.add(locLabel);

        if (row.monster.getWeakness() != null && !row.monster.getWeakness().isEmpty() && !"None".equalsIgnoreCase(row.monster.getWeakness()))
        {
            JLabel weakLabel = new JLabel("• " + row.monster.getWeakness());
            weakLabel.setFont(FontManager.getRunescapeSmallFont());
            weakLabel.setForeground(WEAKNESS_MINT);
            subRow.add(weakLabel);
        }

        centerPanel.add(subRow);
        card.add(centerPanel, BorderLayout.CENTER);

        // 3. Action Buttons Strip: [ Side Panel ] and [ Map ]
        JPanel actionStrip = new JPanel(new GridLayout(2, 1, 0, 2));
        actionStrip.setBackground(card.getBackground());
        actionStrip.setPreferredSize(new Dimension(74, 44));

        JButton panelBtn = new JButton("Side Panel");
        styleStoneButton(panelBtn, Color.WHITE);
        panelBtn.setFont(FontManager.getRunescapeSmallFont());
        panelBtn.setMargin(new Insets(1, 2, 1, 2));
        panelBtn.setToolTipText("Open " + row.monster.getName() + " in sidebar bestiary view");
        panelBtn.addActionListener(e -> {
            if (onInspectMonster != null) onInspectMonster.accept(row.monster);
            if (clientToolbar != null && navButton != null) SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
            setFeedback("Opened " + row.monster.getName() + " in sidebar bestiary view.");
        });
        actionStrip.add(panelBtn);

        JButton mapBtn = new JButton("Map");
        styleStoneButton(mapBtn, MAP_CYAN);
        mapBtn.setFont(FontManager.getRunescapeSmallFont());
        mapBtn.setMargin(new Insets(1, 2, 1, 2));
        mapBtn.setToolTipText("Locate " + row.monster.getName() + " on World Map");
        mapBtn.addActionListener(e -> {
            panMapToMonsterRow(row);
            setFeedback("Centered map on " + row.monster.getName() + " spawn zone.");
        });
        actionStrip.add(mapBtn);

        card.add(actionStrip, BorderLayout.EAST);

        return card;
    }

    private void updateCombatAchievementsInspector(Monster monster)
    {
        combatAchievementsInspectorPanel.removeAll();
        if (monster == null)
        {
            JLabel emptyLabel = new JLabel("Select a monster from the list to view its Combat Achievements profile.", SwingConstants.CENTER);
            emptyLabel.setFont(FontManager.getRunescapeBoldFont());
            emptyLabel.setForeground(TEXT_MUTED);
            emptyLabel.setBorder(new EmptyBorder(40, 20, 40, 20));
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            combatAchievementsInspectorPanel.add(emptyLabel);
            combatAchievementsInspectorPanel.revalidate();
            combatAchievementsInspectorPanel.repaint();
            return;
        }

        // 1. TOP HEADER BANNER & ACTION STRIP
        JPanel headerBar = new JPanel(new BorderLayout(8, 0));
        headerBar.setBackground(BG_STONE_HEADER);
        headerBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, BORDER_GOLD),
            new EmptyBorder(8, 12, 8, 12)
        ));
        headerBar.setMaximumSize(new Dimension(3000, 48));

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titlePanel.setBackground(BG_STONE_HEADER);

        JLabel titleLbl = new JLabel(monster.getName());
        titleLbl.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        titleLbl.setForeground(TITLE_GOLD);
        titlePanel.add(titleLbl);

        JLabel catTag = new JLabel("[" + (monster.getCategory() != null ? monster.getCategory() : "Monster") + "]");
        catTag.setFont(FontManager.getRunescapeSmallFont());
        catTag.setForeground(TEXT_MUTED);
        titlePanel.add(catTag);

        JLabel membTag = new JLabel("[" + (monster.isMembers() ? "Members" : "Free-to-Play") + "]");
        membTag.setFont(FontManager.getRunescapeSmallFont());
        membTag.setForeground(monster.isMembers() ? NAME_ORANGE : PRICE_GOLD);
        titlePanel.add(membTag);

        headerBar.add(titlePanel, BorderLayout.WEST);

        // Top Right Action Row: [ 🌐 Locate ] [ ⚔ WIKI ] [ 📋 Side Panel ]
        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actionsRow.setBackground(BG_STONE_HEADER);

        JButton locateBtn = new JButton("🌐 Locate");
        styleStoneButton(locateBtn, MAP_CYAN);
        locateBtn.setToolTipText("Locate " + monster.getName() + " on World Map & drop beacon");
        locateBtn.addActionListener(e -> {
            panMapToMonster(monster);
            setFeedback("Centered World Map on " + monster.getName() + ".");
        });
        actionsRow.add(locateBtn);

        JButton wikiBtn = new JButton("⚔ Wiki");
        styleStoneButton(wikiBtn, new Color(147, 197, 253));
        wikiBtn.setToolTipText("Open official OSRS Wiki page for " + monster.getName());
        wikiBtn.addActionListener(e -> openMonsterWiki(monster));
        actionsRow.add(wikiBtn);

        JButton panelBtn = new JButton("Side Panel");
        styleStoneButton(panelBtn, Color.WHITE);
        panelBtn.setToolTipText("Open " + monster.getName() + " in sidebar bestiary");
        panelBtn.addActionListener(e -> {
            if (onInspectMonster != null) onInspectMonster.accept(monster);
            if (clientToolbar != null && navButton != null) SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
            setFeedback("Opened " + monster.getName() + " in sidebar bestiary view.");
        });
        actionsRow.add(panelBtn);

        headerBar.add(actionsRow, BorderLayout.EAST);
        combatAchievementsInspectorPanel.add(headerBar);
        combatAchievementsInspectorPanel.add(Box.createVerticalStrut(6));

        // 2. UPPER COMBAT OVERVIEW & SUNKEN PORTRAIT CHAMBER
        JPanel topGrid = new JPanel(new BorderLayout(8, 0));
        topGrid.setBackground(BG_STONE_DARK);
        topGrid.setMaximumSize(new Dimension(3000, 210));
        topGrid.setPreferredSize(new Dimension(0, 210));

        // Left Overview Box
        JPanel overviewBox = new JPanel();
        overviewBox.setLayout(new BoxLayout(overviewBox, BoxLayout.Y_AXIS));
        overviewBox.setBackground(BG_STONE_PANEL);
        overviewBox.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_STONE_OUTER, 1),
            new EmptyBorder(8, 10, 8, 10)
        ));

        JLabel overviewHeader = new JLabel("⚔ Combat Overview & Requirements");
        overviewHeader.setFont(FontManager.getRunescapeBoldFont());
        overviewHeader.setForeground(TITLE_GOLD);
        overviewHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        overviewBox.add(overviewHeader);
        overviewBox.add(Box.createVerticalStrut(4));

        if (monster.getQuestRequirement() != null && !monster.getQuestRequirement().isEmpty() && !"None".equalsIgnoreCase(monster.getQuestRequirement()))
        {
            JLabel questReq = new JLabel("• Requires Quest: " + monster.getQuestRequirement());
            questReq.setFont(FontManager.getRunescapeSmallFont());
            questReq.setForeground(REQ_RED);
            questReq.setAlignmentX(Component.LEFT_ALIGNMENT);
            overviewBox.add(questReq);
            overviewBox.add(Box.createVerticalStrut(2));
        }

        if (monster.getSlayerLevel() > 1)
        {
            JLabel slayerReq = new JLabel("• Requires Slayer: Level " + monster.getSlayerLevel() + " Slayer");
            slayerReq.setFont(FontManager.getRunescapeSmallFont());
            slayerReq.setForeground(new Color(192, 132, 252));
            slayerReq.setAlignmentX(Component.LEFT_ALIGNMENT);
            overviewBox.add(slayerReq);
            overviewBox.add(Box.createVerticalStrut(2));
        }

        JLabel styleLbl = new JLabel("• Attack Style: " + (monster.getAttackType() != null && !monster.getAttackType().isEmpty() ? monster.getAttackType() : "Melee")
            + " • Weakness: " + (monster.getWeakness() != null && !monster.getWeakness().isEmpty() ? monster.getWeakness() : "None"));
        styleLbl.setFont(FontManager.getRunescapeSmallFont());
        styleLbl.setForeground(TEXT_PARCHMENT);
        styleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        overviewBox.add(styleLbl);
        overviewBox.add(Box.createVerticalStrut(2));

        JLabel statsLbl = new JLabel("• Hitpoints: " + monster.getHitpoints() + " HP • Max Hit: " + (monster.getMaxHit() > 0 ? monster.getMaxHit() : "N/A"));
        statsLbl.setFont(FontManager.getRunescapeSmallFont());
        statsLbl.setForeground(PRICE_GOLD);
        statsLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        overviewBox.add(statsLbl);
        overviewBox.add(Box.createVerticalStrut(6));

        // Spawns & Dungeons Strip
        JLabel spawnsHeader = new JLabel("📍 Spawn Locations & Entrances:");
        spawnsHeader.setFont(FontManager.getRunescapeBoldFont());
        spawnsHeader.setForeground(MAP_CYAN);
        spawnsHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        overviewBox.add(spawnsHeader);
        overviewBox.add(Box.createVerticalStrut(2));

        List<MonsterSpawnZone> spawns = monster.getSpawnZones();
        if (spawns != null && !spawns.isEmpty())
        {
            JPanel spawnsList = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            spawnsList.setBackground(BG_STONE_PANEL);
            spawnsList.setAlignmentX(Component.LEFT_ALIGNMENT);

            int shown = 0;
            for (MonsterSpawnZone zone : spawns)
            {
                String locName = buildLocationDisplay(zone);
                JButton locBtn = new JButton(locName + " 🌐");
                styleStoneButton(locBtn, MAP_CYAN);
                locBtn.setFont(FontManager.getRunescapeSmallFont());
                locBtn.setMargin(new Insets(1, 4, 1, 4));
                locBtn.setToolTipText("Show " + locName + " on the World Map");
                locBtn.addActionListener(e -> {
                    if (onFocusMonsterZoneOnMap != null) onFocusMonsterZoneOnMap.accept(monster, zone);
                    else if (onFocusPointOnMap != null && zone.getEffectiveFocusPoint() != null) onFocusPointOnMap.accept(zone.getEffectiveFocusPoint(), monster.getName() + " (" + locName + ")");
                    setFeedback("Centered World Map on " + locName + ".");
                });
                spawnsList.add(locBtn);
                shown++;
                if (shown >= 6 && spawns.size() > 6)
                {
                    JLabel more = new JLabel("+" + (spawns.size() - 6) + " more");
                    more.setFont(FontManager.getRunescapeSmallFont());
                    more.setForeground(TEXT_MUTED);
                    spawnsList.add(more);
                    break;
                }
            }
            overviewBox.add(spawnsList);
        }
        else
        {
            JLabel noSpawns = new JLabel("• Instance / Quest Encounter");
            noSpawns.setFont(FontManager.getRunescapeSmallFont());
            noSpawns.setForeground(TEXT_MUTED);
            noSpawns.setAlignmentX(Component.LEFT_ALIGNMENT);
            overviewBox.add(noSpawns);
        }

        topGrid.add(overviewBox, BorderLayout.CENTER);

        // Right Sunken Portrait Chamber
        JPanel portraitBox = new JPanel();
        portraitBox.setLayout(new BoxLayout(portraitBox, BoxLayout.Y_AXIS));
        portraitBox.setBackground(BG_STONE_DARK);
        portraitBox.setPreferredSize(new Dimension(190, 0));
        portraitBox.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_STONE_SHADOW, 2),
            new EmptyBorder(6, 6, 6, 6)
        ));

        JLabel portraitLabel = new JLabel();
        portraitLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        portraitLabel.setHorizontalAlignment(SwingConstants.CENTER);
        portraitLabel.setPreferredSize(new Dimension(90, 90));
        portraitLabel.setMaximumSize(new Dimension(90, 90));

        if (npcPortraitManager != null)
        {
            npcPortraitManager.loadNpcHeroPortrait(monster.getName(), 90, portraitLabel);
        }
        portraitBox.add(portraitLabel);
        portraitBox.add(Box.createVerticalStrut(4));

        JLabel lvlLabel = new JLabel("Combat Level: " + monster.getCombatLevel(), SwingConstants.CENTER);
        lvlLabel.setFont(FontManager.getRunescapeBoldFont());
        lvlLabel.setForeground(COMBAT_LVL_GOLD);
        lvlLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        portraitBox.add(lvlLabel);

        JLabel hpLabel = new JLabel(monster.getHitpoints() + " Hitpoints", SwingConstants.CENTER);
        hpLabel.setFont(FontManager.getRunescapeSmallFont());
        hpLabel.setForeground(HP_RED);
        hpLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        portraitBox.add(hpLabel);

        topGrid.add(portraitBox, BorderLayout.EAST);
        combatAchievementsInspectorPanel.add(topGrid);
        combatAchievementsInspectorPanel.add(Box.createVerticalStrut(8));

        // 3. CATEGORIZED DROP TABLE & COLLECTION LOG
        List<MonsterDrop> drops = monster.getDrops();
        int dropCount = drops != null ? drops.size() : 0;

        JPanel dropsHeader = new JPanel(new BorderLayout());
        dropsHeader.setBackground(BG_STONE_HEADER);
        dropsHeader.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_GOLD),
            new EmptyBorder(4, 8, 4, 8)
        ));
        dropsHeader.setMaximumSize(new Dimension(3000, 26));

        JLabel dropsTitle = new JLabel("🏆 Drop Table & Loot Log (" + dropCount + " items)");
        dropsTitle.setFont(FontManager.getRunescapeBoldFont());
        dropsTitle.setForeground(TITLE_GOLD);
        dropsHeader.add(dropsTitle, BorderLayout.WEST);

        combatAchievementsInspectorPanel.add(dropsHeader);
        combatAchievementsInspectorPanel.add(Box.createVerticalStrut(4));

        if (drops == null || drops.isEmpty())
        {
            JLabel noDrops = new JLabel("No item drops recorded for this monster.");
            noDrops.setFont(FontManager.getRunescapeSmallFont());
            noDrops.setForeground(TEXT_MUTED);
            noDrops.setBorder(new EmptyBorder(10, 12, 10, 12));
            noDrops.setAlignmentX(Component.LEFT_ALIGNMENT);
            combatAchievementsInspectorPanel.add(noDrops);
        }
        else
        {
            java.util.Map<String, List<MonsterDrop>> categoryMap = new java.util.LinkedHashMap<>();
            for (String cat : MonsterDetailView.DROP_CATEGORIES)
            {
                categoryMap.put(cat, new ArrayList<>());
            }

            for (MonsterDrop drop : drops)
            {
                if (drop.getName() == null || drop.getName().trim().isEmpty()) continue;
                String cat = MonsterDetailView.categorizeDrop(drop);
                categoryMap.computeIfAbsent(cat, k -> new ArrayList<>()).add(drop);
            }

            for (java.util.Map.Entry<String, List<MonsterDrop>> entry : categoryMap.entrySet())
            {
                String catName = entry.getKey();
                List<MonsterDrop> catDrops = entry.getValue();
                if (catDrops.isEmpty()) continue;

                JPanel catBar = createCategoryHeaderBar(catName, catDrops.size());
                combatAchievementsInspectorPanel.add(catBar);
                combatAchievementsInspectorPanel.add(Box.createVerticalStrut(2));

                JPanel dropsGrid = new JPanel(new GridLayout(0, 2, 4, 3));
                dropsGrid.setBackground(BG_STONE_DARK);
                dropsGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

                for (MonsterDrop drop : catDrops)
                {
                    dropsGrid.add(buildDropInspectorCard(monster, drop));
                }

                combatAchievementsInspectorPanel.add(dropsGrid);
                combatAchievementsInspectorPanel.add(Box.createVerticalStrut(6));
            }
        }

        combatAchievementsInspectorPanel.revalidate();
        combatAchievementsInspectorPanel.repaint();
    }

    private JPanel createCategoryHeaderBar(String title, int count)
    {
        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setBackground(new Color(36, 36, 42));
        header.setMaximumSize(new Dimension(3000, 22));
        header.setPreferredSize(new Dimension(0, 22));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, TITLE_GOLD),
            new EmptyBorder(2, 6, 2, 6)
        ));

        JLabel label = new JLabel(title + " (" + count + ")");
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(TITLE_GOLD);
        header.add(label, BorderLayout.WEST);
        return header;
    }

    private JPanel buildDropInspectorCard(Monster monster, MonsterDrop drop)
    {
        JPanel card = new JPanel(new BorderLayout(6, 0));
        card.setBackground(BG_STONE_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_STONE_OUTER, 1),
            new EmptyBorder(3, 4, 3, 4)
        ));
        card.setPreferredSize(new Dimension(0, 38));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Color rarityColor = RarityFormat.perKillColor(drop);
        String formattedRarity = RarityFormat.perKill(drop) + RarityFormat.perRollNote(drop);

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(32, 32));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        if (itemManager != null && drop.getItemId() > 0)
        {
            AsyncBufferedImage img = itemManager.getImage(drop.getItemId());
            if (img != null) img.addTo(iconLabel);
        }
        card.add(iconLabel, BorderLayout.WEST);

        JPanel center = new JPanel(new GridLayout(2, 1, 0, 0));
        center.setBackground(BG_STONE_CARD);

        JLabel nameLbl = new JLabel(drop.getName());
        nameLbl.setFont(FontManager.getRunescapeBoldFont());
        nameLbl.setForeground(Color.WHITE);
        center.add(nameLbl);

        JLabel qtyLbl = new JLabel("Qty: " + drop.getQuantity() + " • " + formattedRarity);
        qtyLbl.setFont(FontManager.getRunescapeSmallFont());
        qtyLbl.setForeground(rarityColor);
        center.add(qtyLbl);

        card.add(center, BorderLayout.CENTER);

        // Cart button
        if (cartManager != null && drop.getItemId() > 0)
        {
            JButton cartBtn = new JButton("+🛒");
            styleStoneButton(cartBtn, STOCK_GREEN);
            cartBtn.setMargin(new Insets(0, 2, 0, 2));
            cartBtn.setFont(FontManager.getRunescapeSmallFont());
            cartBtn.setToolTipText("Add " + drop.getName() + " to shopping cart");
            cartBtn.addActionListener(e -> {
                cartManager.addItem(CartItem.builder()
                    .itemId(drop.getItemId())
                    .itemName(drop.getName())
                    .quantity(1)
                    .unitPrice(0)
                    .shopName(monster.getName())
                    .townName("Monster Drop")
                    .build());
                setFeedback("Added " + drop.getName() + " to Shopping Cart.");
            });
            card.add(cartBtn, BorderLayout.EAST);
        }

        card.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2 || SwingUtilities.isRightMouseButton(e))
                {
                    openWikiPage(drop.getName());
                }
            }
        });

        return card;
    }

    private void panMapToMonster(Monster monster)
    {
        if (monster == null) return;
        List<MonsterSpawnZone> zones = monster.getSpawnZones();
        if (zones != null && !zones.isEmpty())
        {
            MonsterSpawnZone zone = zones.get(0);
            if (onFocusMonsterZoneOnMap != null)
            {
                onFocusMonsterZoneOnMap.accept(monster, zone);
            }
            else if (onFocusPointOnMap != null && zone.getEffectiveFocusPoint() != null)
            {
                onFocusPointOnMap.accept(zone.getEffectiveFocusPoint(), monster.getName() + " (" + buildLocationDisplay(zone) + ")");
            }
        }
        else if (onFocusMonsterOnMap != null)
        {
            onFocusMonsterOnMap.accept(monster);
        }
    }

    // ==========================================
    // TAB 3: SLAYER MASTERS & TASKS
    // ==========================================
    private JPanel buildSlayerTabPanel()
    {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(BG_STONE_PANEL);
        panel.setBorder(new EmptyBorder(6, 8, 6, 8));

        // Filter Header
        JPanel topBar = new JPanel(new BorderLayout(8, 4));
        topBar.setBackground(BG_STONE_PANEL);

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        searchRow.setBackground(BG_STONE_PANEL);

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(FontManager.getRunescapeBoldFont());
        searchLabel.setForeground(TITLE_GOLD);
        searchRow.add(searchLabel);

        styleTextField(slayerSearchField);
        slayerSearchField.setToolTipText("Filter by task monster, master, location, or unlock requirement");
        setupDebouncedSearch(slayerSearchField, this::applySlayerFilters);
        searchRow.add(slayerSearchField);

        JButton clearBtn = new JButton("✖");
        styleMiniButton(clearBtn);
        clearBtn.setToolTipText("Clear search text");
        clearBtn.addActionListener(e -> {
            slayerSearchField.setText("");
            applySlayerFilters();
        });
        searchRow.add(clearBtn);

        JLabel masterLabel = new JLabel("Master:");
        masterLabel.setFont(FontManager.getRunescapeBoldFont());
        masterLabel.setForeground(TITLE_GOLD);
        searchRow.add(masterLabel);

        styleDropdown(slayerMasterDropdown);
        slayerMasterDropdown.addItem("All Slayer Masters");
        slayerMasterDropdown.addActionListener(e -> applySlayerFilters());
        searchRow.add(slayerMasterDropdown);

        JLabel reqLabel = new JLabel("Requirement:");
        reqLabel.setFont(FontManager.getRunescapeBoldFont());
        reqLabel.setForeground(TITLE_GOLD);
        searchRow.add(reqLabel);

        styleDropdown(slayerFilterReqDropdown);
        slayerFilterReqDropdown.addItem("All Tasks");
        slayerFilterReqDropdown.addItem("Slayer Req > 1");
        slayerFilterReqDropdown.addItem("Quest / Unlock Req Only");
        slayerFilterReqDropdown.addItem("No Requirements");
        slayerFilterReqDropdown.addActionListener(e -> applySlayerFilters());
        searchRow.add(slayerFilterReqDropdown);

        JButton columnsBtn = new JButton("Columns ▾");
        styleStoneButton(columnsBtn, TITLE_GOLD);
        columnsBtn.setToolTipText("Show or hide slayer columns");
        columnsBtn.addActionListener(e -> showSlayerColumnSelectorPopup(columnsBtn, 0, columnsBtn.getHeight()));
        searchRow.add(columnsBtn);

        topBar.add(searchRow, BorderLayout.NORTH);

        slayerRowCountLabel.setFont(FontManager.getRunescapeSmallFont());
        slayerRowCountLabel.setForeground(TEXT_MUTED);
        slayerRowCountLabel.setBorder(new EmptyBorder(0, 10, 2, 0));
        topBar.add(slayerRowCountLabel, BorderLayout.SOUTH);

        panel.add(topBar, BorderLayout.NORTH);

        // Slayer Table Setup
        slayerTableModel = new DefaultTableModel(SLAYER_COLUMN_NAMES, 0)
        {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }

            @Override
            public Class<?> getColumnClass(int columnIndex)
            {
                if (columnIndex == 2 || columnIndex == 3 || columnIndex == 6)
                {
                    return Integer.class;
                }
                return String.class;
            }
        };

        slayerTable.setModel(slayerTableModel);
        slayerSorter = new TableRowSorter<>(slayerTableModel);
        slayerTable.setRowSorter(slayerSorter);

        styleTable(slayerTable);
        configureTableHeader(slayerTable, (header, x, y) -> showSlayerColumnSelectorPopup(header, x, y));

        DefaultTableCellRenderer slayerRenderer = new DefaultTableCellRenderer()
        {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col)
            {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                applyRowColors(c, row, isSelected);
                setBorder(new EmptyBorder(0, 8, 0, 8));
                int modelCol = table.convertColumnIndexToModel(col);

                switch (modelCol)
                {
                    case 0:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        if (!isSelected) setForeground(TITLE_GOLD);
                        break;
                    case 1:
                        setFont(FontManager.getRunescapeFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        if (!isSelected) setForeground(MAP_CYAN);
                        break;
                    case 2:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.CENTER);
                        if (!isSelected) setForeground(COMBAT_LVL_GOLD);
                        break;
                    case 3:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.CENTER);
                        if (value instanceof Number)
                        {
                            int req = ((Number) value).intValue();
                            setText(req > 1 ? String.valueOf(req) : "None");
                            if (!isSelected) setForeground(req > 1 ? REQ_RED : TEXT_MUTED);
                        }
                        break;
                    case 4:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        if (!isSelected) setForeground(PRICE_GOLD);
                        break;
                    case 5:
                        setFont(FontManager.getRunescapeFont());
                        setHorizontalAlignment(JLabel.CENTER);
                        if (!isSelected) setForeground(TEXT_PARCHMENT);
                        break;
                    case 6:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.CENTER);
                        if (!isSelected) setForeground(new Color(134, 239, 172));
                        break;
                    case 7:
                        setFont(FontManager.getRunescapeSmallFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        String req = value != null ? value.toString() : "";
                        if (!isSelected) setForeground("None".equalsIgnoreCase(req) || req.isEmpty() ? TEXT_MUTED : REQ_RED);
                        break;
                }
                return c;
            }
        };

        int[] preferredWidths = {130, 140, 65, 75, 160, 85, 65, 220};
        for (int i = 0; i < slayerTable.getColumnCount(); i++)
        {
            TableColumn col = slayerTable.getColumnModel().getColumn(i);
            col.setCellRenderer(slayerRenderer);
            if (i < preferredWidths.length) col.setPreferredWidth(preferredWidths[i]);
            allSlayerTableColumns.add(col);
        }

        slayerTable.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e) { handleSlayerTableMouse(e); }
            @Override
            public void mouseReleased(MouseEvent e) { handleSlayerTableMouse(e); }
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2)
                {
                    int viewRow = slayerTable.getSelectedRow();
                    if (viewRow >= 0)
                    {
                        int modelRow = slayerTable.convertRowIndexToModel(viewRow);
                        if (modelRow >= 0 && modelRow < currentSlayerRows.size())
                        {
                            SlayerTableRowData data = currentSlayerRows.get(modelRow);
                            if (data.monster != null)
                            {
                                if (onFocusMonsterOnMap != null) onFocusMonsterOnMap.accept(data.monster);
                                if (onInspectMonster != null) onInspectMonster.accept(data.monster);
                                setFeedback("Focused task monster " + data.monster.getName() + " on World Map.");
                            }
                            else if (data.master != null && data.master.getLocationPoint() != null && onFocusPointOnMap != null)
                            {
                                onFocusPointOnMap.accept(data.master.getLocationPoint(), data.master.getName());
                                setFeedback("Focused Slayer Master " + data.master.getName() + " on World Map.");
                            }
                        }
                    }
                }
            }

            private void handleSlayerTableMouse(MouseEvent e)
            {
                if (e.isPopupTrigger())
                {
                    int viewRow = slayerTable.rowAtPoint(e.getPoint());
                    if (viewRow >= 0)
                    {
                        slayerTable.setRowSelectionInterval(viewRow, viewRow);
                        int modelRow = slayerTable.convertRowIndexToModel(viewRow);
                        if (modelRow >= 0 && modelRow < currentSlayerRows.size())
                        {
                            SlayerTableRowData data = currentSlayerRows.get(modelRow);
                            JPopupMenu popup = createSlayerRowContextMenu(data);
                            popup.show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(slayerTable);
        styleScrollPane(tableScroll);

        // Guide Cards Setup
        slayerCardsListPanel.setLayout(new BoxLayout(slayerCardsListPanel, BoxLayout.Y_AXIS));
        slayerCardsListPanel.setBackground(BG_STONE_DARK);
        JScrollPane cardsScroll = new JScrollPane(slayerCardsListPanel);
        styleScrollPane(cardsScroll);

        slayerViewContainer.setLayout(slayerViewCardLayout);
        slayerViewContainer.add(cardsScroll, "CARDS");
        slayerViewContainer.add(tableScroll, "TABLE");

        panel.add(slayerViewContainer, BorderLayout.CENTER);
        return panel;
    }

    private void rebuildSlayerGuideCards()
    {
        slayerCardsListPanel.removeAll();
        int count = 0;
        int maxCards = 150;

        List<SlayerTableRowData> rows;
        synchronized (this)
        {
            rows = new ArrayList<>(currentSlayerRows);
        }
        for (SlayerTableRowData row : rows)
        {
            JPanel card = buildSlayerGuideCard(row, count % 2 == 0);
            slayerCardsListPanel.add(card);
            slayerCardsListPanel.add(Box.createVerticalStrut(3));
            count++;
            if (count >= maxCards)
            {
                if (rows.size() > maxCards)
                {
                    JLabel moreLabel = new JLabel("... Showing first " + maxCards + " of " + NUMBER_FORMAT.format(rows.size()) + " slayer task assignments.");
                    moreLabel.setFont(FontManager.getRunescapeSmallFont());
                    moreLabel.setForeground(TITLE_GOLD);
                    moreLabel.setBorder(new EmptyBorder(8, 14, 8, 14));
                    slayerCardsListPanel.add(moreLabel);
                }
                break;
            }
        }

        slayerCardsListPanel.revalidate();
        slayerCardsListPanel.repaint();
    }

    private JPanel buildSlayerGuideCard(SlayerTableRowData row, boolean even)
    {
        JPanel card = new JPanel(new BorderLayout(8, 0));
        card.setBackground(even ? BG_STONE_CARD : BG_STONE_CARD_ALT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_STONE_OUTER, 1),
            new EmptyBorder(6, 8, 6, 8)
        ));
        card.setMaximumSize(new Dimension(3000, 56));
        card.setPreferredSize(new Dimension(0, 56));

        int slayerReq = row.monster != null ? row.monster.getSlayerLevel() : row.master.getSlayerRequirement();

        // 1. Requirement Badge
        JPanel badgePanel = new JPanel(new BorderLayout());
        badgePanel.setBackground(BG_STONE_BADGE);
        badgePanel.setPreferredSize(new Dimension(64, 44));
        badgePanel.setBorder(BorderFactory.createLineBorder(BORDER_STONE_HIGHLIGHT, 1));

        JLabel reqBadge = new JLabel(slayerReq > 1 ? ("Req " + slayerReq) : "Lvl 1+", SwingConstants.CENTER);
        reqBadge.setFont(FontManager.getRunescapeBoldFont());
        reqBadge.setForeground(slayerReq > 1 ? REQ_RED : PRICE_GOLD);
        badgePanel.add(reqBadge, BorderLayout.CENTER);

        JLabel wtSub = new JLabel("Weight: " + row.assignment.getWeight(), SwingConstants.CENTER);
        wtSub.setFont(FontManager.getRunescapeSmallFont());
        wtSub.setForeground(new Color(134, 239, 172));
        badgePanel.add(wtSub, BorderLayout.SOUTH);

        card.add(badgePanel, BorderLayout.WEST);

        // 2. Sprite & Details
        JPanel centerPanel = new JPanel(new BorderLayout(8, 0));
        centerPanel.setBackground(card.getBackground());

        JPanel details = new JPanel(new GridLayout(2, 1, 0, 2));
        details.setBackground(card.getBackground());

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        titleRow.setBackground(card.getBackground());

        JLabel nameLabel = new JLabel(row.assignment.getMonster());
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(PRICE_GOLD);
        titleRow.add(nameLabel);

        JLabel countLabel = new JLabel("(" + row.assignment.getAmountDisplay() + " kills)");
        countLabel.setFont(FontManager.getRunescapeSmallFont());
        countLabel.setForeground(TEXT_PARCHMENT);
        titleRow.add(countLabel);

        details.add(titleRow);

        // Subtitle / Master & Requirements
        StringBuilder subDesc = new StringBuilder("<html>");
        subDesc.append("<span style='color: #FFB826;'>Master: ").append(escapeHtml(row.master.getName())).append("</span>");
        subDesc.append(" • <span style='color: #64C8FA;'>").append(escapeHtml(row.master.getLocationName())).append("</span>");
        if (row.master.getCombatRequirement() > 0)
        {
            subDesc.append(" • <span style='color: #FFDF88;'>Cmb Req: ").append(row.master.getCombatRequirement()).append("</span>");
        }
        if (row.assignment.getRequirement() != null && !row.assignment.getRequirement().isEmpty())
        {
            subDesc.append(" • <span style='color: #EF4444; font-weight: bold;'>Requires: ").append(escapeHtml(row.assignment.getRequirement())).append("</span>");
        }
        subDesc.append("</html>");

        JLabel descLabel = new JLabel(subDesc.toString());
        descLabel.setFont(FontManager.getRunescapeFont());
        details.add(descLabel);

        centerPanel.add(details, BorderLayout.CENTER);
        card.add(centerPanel, BorderLayout.CENTER);

        // 3. Action Buttons
        JPanel actionStrip = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        actionStrip.setBackground(card.getBackground());

        JButton mapBtn = new JButton("Map");
        styleStoneButton(mapBtn, MAP_CYAN);
        mapBtn.addActionListener(e -> {
            if (row.monster != null && row.monster.hasSpawnZones() && onFocusMonsterZoneOnMap != null)
            {
                onFocusMonsterZoneOnMap.accept(row.monster, row.monster.getSpawnZones().get(0));
            }
            else if (row.monster != null && onFocusMonsterOnMap != null)
            {
                onFocusMonsterOnMap.accept(row.monster);
            }
            else if (row.master.getLocationPoint() != null && onFocusPointOnMap != null)
            {
                onFocusPointOnMap.accept(row.master.getLocationPoint(), row.master.getName());
            }
            setFeedback("Centered map on " + row.assignment.getMonster() + ".");
        });
        actionStrip.add(mapBtn);

        JButton panelBtn = new JButton("Side Panel");
        styleStoneButton(panelBtn, Color.WHITE);
        panelBtn.addActionListener(e -> {
            if (row.monster != null && onInspectMonster != null)
            {
                onInspectMonster.accept(row.monster);
            }
            else if (onOpenSlayerTab != null)
            {
                onOpenSlayerTab.accept(row.master.getName());
            }
            if (clientToolbar != null && navButton != null) SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
            setFeedback("Opened " + row.assignment.getMonster() + " in sidebar.");
        });
        actionStrip.add(panelBtn);

        JButton wikiBtn = new JButton("Wiki");
        styleStoneButton(wikiBtn, new Color(147, 197, 253));
        wikiBtn.addActionListener(e -> openWikiPage(row.assignment.getMonster()));
        actionStrip.add(wikiBtn);

        card.add(actionStrip, BorderLayout.EAST);
        return card;
    }

    // ==========================================
    // TAB 4: GLOBAL ITEM & DROP SEARCH
    // ==========================================
    private JPanel buildSearchTabPanel()
    {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(BG_STONE_PANEL);
        panel.setBorder(new EmptyBorder(6, 8, 6, 8));

        // Filter Header
        JPanel topBar = new JPanel(new BorderLayout(8, 4));
        topBar.setBackground(BG_STONE_PANEL);

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        searchRow.setBackground(BG_STONE_PANEL);

        JLabel searchLabel = new JLabel("Item / Drop Search:");
        searchLabel.setFont(FontManager.getRunescapeBoldFont());
        searchLabel.setForeground(TITLE_GOLD);
        searchRow.add(searchLabel);

        styleTextField(searchUniversalField);
        searchUniversalField.setToolTipText("Universal item & drop search across all shops and monster drop tables");
        setupDebouncedSearch(searchUniversalField, this::applySearchFilters);
        searchRow.add(searchUniversalField);

        JButton clearBtn = new JButton("✖");
        styleMiniButton(clearBtn);
        clearBtn.setToolTipText("Clear search text");
        clearBtn.addActionListener(e -> {
            searchUniversalField.setText("");
            applySearchFilters();
        });
        searchRow.add(clearBtn);

        JLabel srcLabel = new JLabel("Source:");
        srcLabel.setFont(FontManager.getRunescapeBoldFont());
        srcLabel.setForeground(TITLE_GOLD);
        searchRow.add(srcLabel);

        styleDropdown(searchSourceTypeDropdown);
        searchSourceTypeDropdown.addItem("All Sources (Shops & Drops)");
        searchSourceTypeDropdown.addItem("Shops & Markets Only");
        searchSourceTypeDropdown.addItem("Monster Drops Only");
        searchSourceTypeDropdown.addActionListener(e -> applySearchFilters());
        searchRow.add(searchSourceTypeDropdown);

        JLabel membLabel = new JLabel("Access:");
        membLabel.setFont(FontManager.getRunescapeBoldFont());
        membLabel.setForeground(TITLE_GOLD);
        searchRow.add(membLabel);

        styleDropdown(searchMembDropdown);
        searchMembDropdown.addItem("All Access");
        searchMembDropdown.addItem("Members Only");
        searchMembDropdown.addItem("F2P Only");
        searchMembDropdown.addActionListener(e -> applySearchFilters());
        searchRow.add(searchMembDropdown);

        JButton columnsBtn = new JButton("Columns ▾");
        styleStoneButton(columnsBtn, TITLE_GOLD);
        columnsBtn.setToolTipText("Show or hide search columns");
        columnsBtn.addActionListener(e -> showSearchColumnSelectorPopup(columnsBtn, 0, columnsBtn.getHeight()));
        searchRow.add(columnsBtn);

        topBar.add(searchRow, BorderLayout.NORTH);

        searchRowCountLabel.setFont(FontManager.getRunescapeSmallFont());
        searchRowCountLabel.setForeground(TEXT_MUTED);
        searchRowCountLabel.setBorder(new EmptyBorder(0, 10, 2, 0));
        topBar.add(searchRowCountLabel, BorderLayout.SOUTH);

        panel.add(topBar, BorderLayout.NORTH);

        // Search Table Setup
        searchTableModel = new DefaultTableModel(SEARCH_COLUMN_NAMES, 0)
        {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }

            @Override
            public Class<?> getColumnClass(int columnIndex) { return String.class; }
        };

        searchTable.setModel(searchTableModel);
        searchSorter = new TableRowSorter<>(searchTableModel);
        searchTable.setRowSorter(searchSorter);

        styleTable(searchTable);
        configureTableHeader(searchTable, (header, x, y) -> showSearchColumnSelectorPopup(header, x, y));

        DefaultTableCellRenderer searchRenderer = new DefaultTableCellRenderer()
        {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col)
            {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                applyRowColors(c, row, isSelected);
                setBorder(new EmptyBorder(0, 8, 0, 8));
                int modelCol = table.convertColumnIndexToModel(col);

                switch (modelCol)
                {
                    case 0:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        if (!isSelected) setForeground(PRICE_GOLD);
                        break;
                    case 1:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.CENTER);
                        String type = value != null ? value.toString() : "";
                        if (!isSelected) setForeground(type.contains("Shop") ? TITLE_GOLD : DROP_PINK);
                        break;
                    case 2:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        if (!isSelected) setForeground(TEXT_PARCHMENT);
                        break;
                    case 3:
                        setFont(FontManager.getRunescapeFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        if (!isSelected) setForeground(MAP_CYAN);
                        break;
                    case 4:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.CENTER);
                        String sr = value != null ? value.toString() : "";
                        if (!isSelected) setForeground(sr.startsWith("Stock:") ? STOCK_GREEN : PRICE_GOLD);
                        break;
                    case 5:
                        setFont(FontManager.getRunescapeBoldFont());
                        setHorizontalAlignment(JLabel.RIGHT);
                        String p = value != null ? value.toString() : "";
                        if (!isSelected) setForeground(p.contains("gp") ? PRICE_GOLD : new Color(147, 197, 253));
                        break;
                    case 6:
                        setFont(FontManager.getRunescapeSmallFont());
                        setHorizontalAlignment(JLabel.LEFT);
                        String req = value != null ? value.toString() : "";
                        if (!isSelected) setForeground("None".equalsIgnoreCase(req) || req.isEmpty() ? TEXT_MUTED : REQ_RED);
                        break;
                }
                return c;
            }
        };

        int[] preferredWidths = {160, 100, 150, 160, 100, 110, 160};
        for (int i = 0; i < searchTable.getColumnCount(); i++)
        {
            TableColumn col = searchTable.getColumnModel().getColumn(i);
            col.setCellRenderer(searchRenderer);
            if (i < preferredWidths.length) col.setPreferredWidth(preferredWidths[i]);
            allSearchTableColumns.add(col);
        }

        searchTable.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e) { handleSearchTableMouse(e); }
            @Override
            public void mouseReleased(MouseEvent e) { handleSearchTableMouse(e); }
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2)
                {
                    int viewRow = searchTable.getSelectedRow();
                    if (viewRow >= 0)
                    {
                        int modelRow = searchTable.convertRowIndexToModel(viewRow);
                        if (modelRow >= 0 && modelRow < currentSearchRows.size())
                        {
                            GlobalSearchRowData data = currentSearchRows.get(modelRow);
                            if (data.type == GlobalSearchRowData.SourceType.SHOP)
                            {
                                if (onFocusShopOnMap != null) onFocusShopOnMap.accept(data.shop);
                                if (onInspectShop != null) onInspectShop.accept(data.shop);
                                setFeedback("Focused shop " + data.shop.getName() + " on World Map.");
                            }
                            else if (data.monster != null)
                            {
                                if (data.monsterZone != null && onFocusMonsterZoneOnMap != null)
                                {
                                    onFocusMonsterZoneOnMap.accept(data.monster, data.monsterZone);
                                }
                                else if (onFocusMonsterOnMap != null)
                                {
                                    onFocusMonsterOnMap.accept(data.monster);
                                }
                                if (onInspectMonster != null) onInspectMonster.accept(data.monster);
                                setFeedback("Focused monster " + data.monster.getName() + " on World Map.");
                            }
                        }
                    }
                }
            }

            private void handleSearchTableMouse(MouseEvent e)
            {
                if (e.isPopupTrigger())
                {
                    int viewRow = searchTable.rowAtPoint(e.getPoint());
                    if (viewRow >= 0)
                    {
                        searchTable.setRowSelectionInterval(viewRow, viewRow);
                        int modelRow = searchTable.convertRowIndexToModel(viewRow);
                        if (modelRow >= 0 && modelRow < currentSearchRows.size())
                        {
                            GlobalSearchRowData data = currentSearchRows.get(modelRow);
                            JPopupMenu popup = createSearchRowContextMenu(data);
                            popup.show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(searchTable);
        styleScrollPane(tableScroll);

        // Guide Cards Setup
        searchCardsListPanel.setLayout(new BoxLayout(searchCardsListPanel, BoxLayout.Y_AXIS));
        searchCardsListPanel.setBackground(BG_STONE_DARK);
        JScrollPane cardsScroll = new JScrollPane(searchCardsListPanel);
        styleScrollPane(cardsScroll);

        searchViewContainer.setLayout(searchViewCardLayout);
        searchViewContainer.add(cardsScroll, "CARDS");
        searchViewContainer.add(tableScroll, "TABLE");

        panel.add(searchViewContainer, BorderLayout.CENTER);
        return panel;
    }

    private void rebuildSearchGuideCards()
    {
        searchCardsListPanel.removeAll();
        int count = 0;
        int maxCards = 150;

        List<GlobalSearchRowData> rows;
        synchronized (this)
        {
            rows = new ArrayList<>(currentSearchRows);
        }
        for (GlobalSearchRowData row : rows)
        {
            JPanel card = buildSearchGuideCard(row, count % 2 == 0);
            searchCardsListPanel.add(card);
            searchCardsListPanel.add(Box.createVerticalStrut(3));
            count++;
            if (count >= maxCards)
            {
                if (rows.size() > maxCards)
                {
                    JLabel moreLabel = new JLabel("... Showing first " + maxCards + " of " + NUMBER_FORMAT.format(rows.size()) + " search results.");
                    moreLabel.setFont(FontManager.getRunescapeSmallFont());
                    moreLabel.setForeground(TITLE_GOLD);
                    moreLabel.setBorder(new EmptyBorder(8, 14, 8, 14));
                    searchCardsListPanel.add(moreLabel);
                }
                break;
            }
        }

        searchCardsListPanel.revalidate();
        searchCardsListPanel.repaint();
    }

    private JPanel buildSearchGuideCard(GlobalSearchRowData row, boolean even)
    {
        JPanel card = new JPanel(new BorderLayout(8, 0));
        card.setBackground(even ? BG_STONE_CARD : BG_STONE_CARD_ALT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_STONE_OUTER, 1),
            new EmptyBorder(6, 8, 6, 8)
        ));
        card.setMaximumSize(new Dimension(3000, 56));
        card.setPreferredSize(new Dimension(0, 56));

        boolean isShop = row.type == GlobalSearchRowData.SourceType.SHOP;

        // 1. Badge
        JPanel badgePanel = new JPanel(new BorderLayout());
        badgePanel.setBackground(BG_STONE_BADGE);
        badgePanel.setPreferredSize(new Dimension(64, 44));
        badgePanel.setBorder(BorderFactory.createLineBorder(BORDER_STONE_HIGHLIGHT, 1));

        JLabel typeBadge = new JLabel(isShop ? "Shop" : "Drop", SwingConstants.CENTER);
        typeBadge.setFont(FontManager.getRunescapeBoldFont());
        typeBadge.setForeground(isShop ? TITLE_GOLD : DROP_PINK);
        badgePanel.add(typeBadge, BorderLayout.CENTER);

        JLabel priceSub = new JLabel(isShop ? (NUMBER_FORMAT.format(row.shopItem.getPrice()) + " gp") : RarityFormat.perKill(row.monsterDrop), SwingConstants.CENTER);
        priceSub.setFont(FontManager.getRunescapeSmallFont());
        priceSub.setForeground(isShop ? PRICE_GOLD : new Color(147, 197, 253));
        badgePanel.add(priceSub, BorderLayout.SOUTH);

        card.add(badgePanel, BorderLayout.WEST);

        // 2. Sprite & Details
        JPanel centerPanel = new JPanel(new BorderLayout(8, 0));
        centerPanel.setBackground(card.getBackground());

        JLabel spriteLabel = new JLabel();
        spriteLabel.setPreferredSize(new Dimension(32, 32));
        if (isShop && row.shopItem != null)
        {
            loadItemSprite(row.shopItem.getItemId(), spriteLabel);
        }
        else if (!isShop && row.monsterDrop != null)
        {
            loadItemSprite(row.monsterDrop.getItemId(), spriteLabel);
        }
        centerPanel.add(spriteLabel, BorderLayout.WEST);

        JPanel details = new JPanel(new GridLayout(2, 1, 0, 2));
        details.setBackground(card.getBackground());

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        titleRow.setBackground(card.getBackground());

        String itemName = isShop ? row.shopItem.getName() : row.monsterDrop.getName();
        JLabel nameLabel = new JLabel(itemName);
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(PRICE_GOLD);
        titleRow.add(nameLabel);

        JLabel sourceTag = new JLabel("from " + (isShop ? row.shop.getName() : row.monster.getName()));
        sourceTag.setFont(FontManager.getRunescapeSmallFont());
        sourceTag.setForeground(TEXT_MUTED);
        titleRow.add(sourceTag);

        details.add(titleRow);

        // Subtitle / Location & Requirements
        StringBuilder subDesc = new StringBuilder("<html>");
        if (isShop)
        {
            subDesc.append("<span style='color: #64C8FA;'>").append(row.shop.getTown()).append("</span>");
            subDesc.append(" • <span style='color: #4ADE80;'>Stock: ").append(row.shopItem.getDefaultStock()).append("</span>");
            if (row.shop.getQuestRequirement() != null && !row.shop.getQuestRequirement().isEmpty())
            {
                subDesc.append(" • <span style='color: #EF4444; font-weight: bold;'>Requires: ").append(escapeHtml(row.shop.getQuestRequirement())).append("</span>");
            }
        }
        else
        {
            String loc = row.monsterZone != null ? row.monsterZone.getLocationName() : "Instance";
            subDesc.append("<span style='color: #64C8FA;'>").append(escapeHtml(loc)).append("</span>");
            String dropRate = row.monsterDrop.getRarity() > 0 ? String.format(Locale.US, "%.2f%%", row.monsterDrop.getRarity() * 100) : "Varies";
            subDesc.append(" • <span style='color: #FDE047;'>Rate: ").append(dropRate).append("</span>");
            if (row.monster.getSlayerLevel() > 1)
            {
                subDesc.append(" • <span style='color: #EF4444; font-weight: bold;'>Requires: Level ").append(row.monster.getSlayerLevel()).append(" Slayer</span>");
            }
        }
        subDesc.append("</html>");

        JLabel descLabel = new JLabel(subDesc.toString());
        descLabel.setFont(FontManager.getRunescapeFont());
        details.add(descLabel);

        centerPanel.add(details, BorderLayout.CENTER);
        card.add(centerPanel, BorderLayout.CENTER);

        // 3. Action Buttons
        JPanel actionStrip = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        actionStrip.setBackground(card.getBackground());

        JButton mapBtn = new JButton("Map");
        styleStoneButton(mapBtn, MAP_CYAN);
        mapBtn.addActionListener(e -> {
            if (isShop && onFocusShopOnMap != null)
            {
                onFocusShopOnMap.accept(row.shop);
                setFeedback("Centered map on " + row.shop.getName() + ".");
            }
            else if (!isShop && row.monsterZone != null && onFocusMonsterZoneOnMap != null)
            {
                onFocusMonsterZoneOnMap.accept(row.monster, row.monsterZone);
                setFeedback("Centered map on " + row.monster.getName() + " spawn.");
            }
            else if (!isShop && onFocusMonsterOnMap != null)
            {
                onFocusMonsterOnMap.accept(row.monster);
                setFeedback("Centered map on " + row.monster.getName() + ".");
            }
        });
        actionStrip.add(mapBtn);

        JButton panelBtn = new JButton("Side Panel");
        styleStoneButton(panelBtn, Color.WHITE);
        panelBtn.addActionListener(e -> {
            if (isShop && onInspectShop != null)
            {
                onInspectShop.accept(row.shop);
                setFeedback("Opened " + row.shop.getName() + " in sidebar stock view.");
            }
            else if (!isShop && onInspectMonster != null)
            {
                onInspectMonster.accept(row.monster);
                setFeedback("Opened " + row.monster.getName() + " in sidebar bestiary view.");
            }
            if (clientToolbar != null && navButton != null) SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
        });
        actionStrip.add(panelBtn);

        if (isShop)
        {
            JButton cartBtn = new JButton("Cart");
            styleStoneButton(cartBtn, PRICE_GOLD);
            cartBtn.addActionListener(e -> {
                cartManager.addItem(row.shopItem, row.shop, 1);
                setFeedback("✓ Added 1x " + row.shopItem.getName() + " to cart!");
            });
            actionStrip.add(cartBtn);
        }

        JButton wikiBtn = new JButton("Wiki");
        styleStoneButton(wikiBtn, new Color(147, 197, 253));
        wikiBtn.addActionListener(e -> openWikiPage(itemName));
        actionStrip.add(wikiBtn);

        card.add(actionStrip, BorderLayout.EAST);
        return card;
    }

    // ==========================================
    // ACTION FOOTER BAR
    // ==========================================
    private JPanel buildActionFooterBar()
    {
        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setBackground(BG_STONE_PANEL);
        footer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_STONE_OUTER),
            new EmptyBorder(6, 12, 6, 12)
        ));

        footerStatusLabel.setFont(FontManager.getRunescapeSmallFont());
        footerStatusLabel.setForeground(MAP_CYAN);
        footer.add(footerStatusLabel, BorderLayout.WEST);

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnGroup.setBackground(BG_STONE_PANEL);

        JButton focusMapBtn = new JButton("Map");
        styleStoneButton(focusMapBtn, MAP_CYAN);
        focusMapBtn.setToolTipText("Show the selected item / shop / monster on the World Map");
        focusMapBtn.addActionListener(e -> executeFocusMap());
        btnGroup.add(focusMapBtn);

        JButton sidePanelBtn = new JButton("Side Panel");
        styleStoneButton(sidePanelBtn, Color.WHITE);
        sidePanelBtn.setToolTipText("Open side panel and load selected shop/monster/slayer view");
        sidePanelBtn.addActionListener(e -> executeViewInSidePanel());
        btnGroup.add(sidePanelBtn);

        styleStoneButton(addCartBtn, PRICE_GOLD);
        addCartBtn.setVisible(TAB_SHOPS.equals(activeTab) || TAB_SEARCH.equals(activeTab));
        addCartBtn.setToolTipText("Add selected shop item to Shopping Cart");
        addCartBtn.addActionListener(e -> executeAddToCart());
        btnGroup.add(addCartBtn);

        JButton wikiBtn = new JButton("Wiki");
        styleStoneButton(wikiBtn, new Color(147, 197, 253));
        wikiBtn.setToolTipText("Open official OSRS Wiki page");
        wikiBtn.addActionListener(e -> executeOpenWiki());
        btnGroup.add(wikiBtn);

        JButton exportCsvBtn = new JButton("Export CSV");
        styleStoneButton(exportCsvBtn, new Color(134, 239, 172));
        exportCsvBtn.setToolTipText("Export active table to a CSV file");
        exportCsvBtn.addActionListener(e -> executeExportCsv());
        btnGroup.add(exportCsvBtn);

        JButton closeBtn = new JButton("Close");
        styleStoneButton(closeBtn, TEXT_MUTED);
        closeBtn.addActionListener(e -> closeGuide());
        btnGroup.add(closeBtn);

        footer.add(btnGroup, BorderLayout.EAST);
        return footer;
    }

    public void executeFocusMap()
    {
        switch (activeTab)
        {
            case TAB_SHOPS:
            {
                int viewRow = shopTable.getSelectedRow();
                if (viewRow >= 0)
                {
                    int modelRow = shopTable.convertRowIndexToModel(viewRow);
                    if (modelRow < currentShopRows.size() && onFocusShopOnMap != null)
                    {
                        Shop s = currentShopRows.get(modelRow).shop;
                        onFocusShopOnMap.accept(s);
                        setFeedback("Centered map on " + s.getName() + ".");
                    }
                }
                break;
            }
            case TAB_MONSTERS:
            {
                int viewRow = monsterTable.getSelectedRow();
                if (viewRow >= 0)
                {
                    int modelRow = monsterTable.convertRowIndexToModel(viewRow);
                    if (modelRow < currentMonsterRows.size())
                    {
                        MonsterTableRowData data = currentMonsterRows.get(modelRow);
                        panMapToMonsterRow(data);
                        setFeedback("Centered map on " + data.monster.getName() + " spawn.");
                    }
                }
                break;
            }
            case TAB_SLAYER:
            {
                int viewRow = slayerTable.getSelectedRow();
                if (viewRow >= 0)
                {
                    int modelRow = slayerTable.convertRowIndexToModel(viewRow);
                    if (modelRow < currentSlayerRows.size())
                    {
                        SlayerTableRowData data = currentSlayerRows.get(modelRow);
                        if (data.monster != null)
                        {
                            if (onFocusMonsterOnMap != null) onFocusMonsterOnMap.accept(data.monster);
                            setFeedback("Centered map on task monster " + data.monster.getName() + ".");
                        }
                        else if (data.master != null && data.master.getLocationPoint() != null && onFocusPointOnMap != null)
                        {
                            onFocusPointOnMap.accept(data.master.getLocationPoint(), data.master.getName());
                            setFeedback("Centered map on Slayer Master " + data.master.getName() + ".");
                        }
                    }
                }
                break;
            }
            case TAB_SEARCH:
            {
                int viewRow = searchTable.getSelectedRow();
                if (viewRow >= 0)
                {
                    int modelRow = searchTable.convertRowIndexToModel(viewRow);
                    if (modelRow < currentSearchRows.size())
                    {
                        GlobalSearchRowData data = currentSearchRows.get(modelRow);
                        if (data.type == GlobalSearchRowData.SourceType.SHOP && data.shop != null && onFocusShopOnMap != null)
                        {
                            onFocusShopOnMap.accept(data.shop);
                            setFeedback("Centered map on " + data.shop.getName() + ".");
                        }
                        else if (data.monster != null)
                        {
                            if (data.monsterZone != null && onFocusMonsterZoneOnMap != null)
                            {
                                onFocusMonsterZoneOnMap.accept(data.monster, data.monsterZone);
                            }
                            else if (onFocusMonsterOnMap != null)
                            {
                                onFocusMonsterOnMap.accept(data.monster);
                            }
                            setFeedback("Centered map on " + data.monster.getName() + " spawn.");
                        }
                    }
                }
                break;
            }
        }
    }

    public void executeViewInSidePanel()
    {
        switch (activeTab)
        {
            case TAB_SHOPS:
            {
                int viewRow = shopTable.getSelectedRow();
                if (viewRow >= 0)
                {
                    int modelRow = shopTable.convertRowIndexToModel(viewRow);
                    if (modelRow < currentShopRows.size() && onInspectShop != null)
                    {
                        Shop s = currentShopRows.get(modelRow).shop;
                        onInspectShop.accept(s);
                        setFeedback("Opened " + s.getName() + " in sidebar stock view.");
                    }
                }
                break;
            }
            case TAB_MONSTERS:
            {
                int viewRow = monsterTable.getSelectedRow();
                if (viewRow >= 0)
                {
                    int modelRow = monsterTable.convertRowIndexToModel(viewRow);
                    if (modelRow < currentMonsterRows.size() && onInspectMonster != null)
                    {
                        Monster m = currentMonsterRows.get(modelRow).monster;
                        onInspectMonster.accept(m);
                        setFeedback("Opened " + m.getName() + " in sidebar bestiary view.");
                    }
                }
                break;
            }
            case TAB_SLAYER:
            {
                int viewRow = slayerTable.getSelectedRow();
                if (viewRow >= 0)
                {
                    int modelRow = slayerTable.convertRowIndexToModel(viewRow);
                    if (modelRow < currentSlayerRows.size())
                    {
                        SlayerTableRowData data = currentSlayerRows.get(modelRow);
                        if (data.monster != null && onInspectMonster != null)
                        {
                            onInspectMonster.accept(data.monster);
                            setFeedback("Opened " + data.monster.getName() + " in sidebar.");
                        }
                        else if (onOpenSlayerTab != null)
                        {
                            onOpenSlayerTab.accept(data.master.getName());
                            setFeedback("Opened Slayer tab for " + data.master.getName() + ".");
                        }
                    }
                }
                break;
            }
            case TAB_SEARCH:
            {
                int viewRow = searchTable.getSelectedRow();
                if (viewRow >= 0)
                {
                    int modelRow = searchTable.convertRowIndexToModel(viewRow);
                    if (modelRow < currentSearchRows.size())
                    {
                        GlobalSearchRowData data = currentSearchRows.get(modelRow);
                        if (data.type == GlobalSearchRowData.SourceType.SHOP && data.shop != null && onInspectShop != null)
                        {
                            onInspectShop.accept(data.shop);
                            setFeedback("Opened " + data.shop.getName() + " in sidebar stock view.");
                        }
                        else if (data.monster != null && onInspectMonster != null)
                        {
                            onInspectMonster.accept(data.monster);
                            setFeedback("Opened " + data.monster.getName() + " in sidebar bestiary view.");
                        }
                    }
                }
                break;
            }
        }

        if (clientToolbar != null && navButton != null)
        {
            SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
        }
    }

    private void executeAddToCart()
    {
        if (TAB_SHOPS.equals(activeTab))
        {
            int viewRow = shopTable.getSelectedRow();
            if (viewRow >= 0)
            {
                int modelRow = shopTable.convertRowIndexToModel(viewRow);
                if (modelRow < currentShopRows.size())
                {
                    ShopTableRowData data = currentShopRows.get(modelRow);
                    cartManager.addItem(data.item, data.shop, 1);
                    setFeedback("✓ Added 1x " + data.item.getName() + " (" + data.shop.getName() + ") to cart!");
                    return;
                }
            }
        }
        else if (TAB_SEARCH.equals(activeTab))
        {
            int viewRow = searchTable.getSelectedRow();
            if (viewRow >= 0)
            {
                int modelRow = searchTable.convertRowIndexToModel(viewRow);
                if (modelRow < currentSearchRows.size())
                {
                    GlobalSearchRowData data = currentSearchRows.get(modelRow);
                    if (data.type == GlobalSearchRowData.SourceType.SHOP && data.shopItem != null && data.shop != null)
                    {
                        cartManager.addItem(data.shopItem, data.shop, 1);
                        setFeedback("✓ Added 1x " + data.shopItem.getName() + " (" + data.shop.getName() + ") to cart!");
                        return;
                    }
                }
            }
        }
        setFeedback("Select an item in Shops or Global Search to add to cart.");
    }

    private void executeOpenWiki()
    {
        switch (activeTab)
        {
            case TAB_SHOPS:
            {
                int viewRow = shopTable.getSelectedRow();
                if (viewRow >= 0)
                {
                    int modelRow = shopTable.convertRowIndexToModel(viewRow);
                    if (modelRow < currentShopRows.size())
                    {
                        openWikiPage(currentShopRows.get(modelRow).item.getName());
                    }
                }
                break;
            }
            case TAB_MONSTERS:
            {
                int viewRow = monsterTable.getSelectedRow();
                if (viewRow >= 0)
                {
                    int modelRow = monsterTable.convertRowIndexToModel(viewRow);
                    if (modelRow < currentMonsterRows.size())
                    {
                        openMonsterWiki(currentMonsterRows.get(modelRow).monster);
                    }
                }
                break;
            }
            case TAB_SLAYER:
            {
                int viewRow = slayerTable.getSelectedRow();
                if (viewRow >= 0)
                {
                    int modelRow = slayerTable.convertRowIndexToModel(viewRow);
                    if (modelRow < currentSlayerRows.size())
                    {
                        openWikiPage(currentSlayerRows.get(modelRow).assignment.getMonster());
                    }
                }
                break;
            }
            case TAB_SEARCH:
            {
                int viewRow = searchTable.getSelectedRow();
                if (viewRow >= 0)
                {
                    int modelRow = searchTable.convertRowIndexToModel(viewRow);
                    if (modelRow < currentSearchRows.size())
                    {
                        GlobalSearchRowData data = currentSearchRows.get(modelRow);
                        if (data.type == GlobalSearchRowData.SourceType.SHOP && data.shopItem != null)
                        {
                            openWikiPage(data.shopItem.getName());
                        }
                        else if (data.monsterDrop != null)
                        {
                            openWikiPage(data.monsterDrop.getName());
                        }
                    }
                }
                break;
            }
        }
    }

    private void executeExportCsv()
    {
        JTable table = getActiveTable();
        if (table == null || table.getRowCount() == 0)
        {
            setFeedback("No rows available to export in current table.");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Table as CSV");
        fileChooser.setSelectedFile(new File("osrs_field_guide_" + activeTab.toLowerCase(Locale.ROOT) + ".csv"));
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION)
        {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase(Locale.ROOT).endsWith(".csv"))
            {
                fileToSave = new File(fileToSave.getAbsolutePath() + ".csv");
            }

            try (PrintWriter pw = new PrintWriter(new FileWriter(fileToSave)))
            {
                TableColumnModel cm = table.getColumnModel();
                StringBuilder headerLine = new StringBuilder();
                for (int c = 0; c < cm.getColumnCount(); c++)
                {
                    if (c > 0) headerLine.append(",");
                    headerLine.append(escapeCsv(cm.getColumn(c).getHeaderValue().toString()));
                }
                pw.println(headerLine);

                for (int r = 0; r < table.getRowCount(); r++)
                {
                    StringBuilder rowLine = new StringBuilder();
                    for (int c = 0; c < cm.getColumnCount(); c++)
                    {
                        if (c > 0) rowLine.append(",");
                        Object val = table.getValueAt(r, c);
                        rowLine.append(escapeCsv(val != null ? val.toString() : ""));
                    }
                    pw.println(rowLine);
                }

                setFeedback("✓ Successfully exported " + table.getRowCount() + " rows to " + fileToSave.getName() + "!");
            }
            catch (Exception ex)
            {
                setFeedback("Error exporting CSV: " + ex.getMessage());
            }
        }
    }

    private String escapeCsv(String val)
    {
        if (val == null) return "\"\"";
        String clean = val.replace("\"", "\"\"");
        return "\"" + clean + "\"";
    }

    public void setFeedback(String msg)
    {
        footerStatusLabel.setText(msg);
    }

    // ==========================================
    // FILTER LOGIC
    // ==========================================

    /**
     * Runs {@code r} on the Swing EDT, blocking the caller if it is on another thread. The
     * applyXFilters() methods mutate the table models + guide-card panels directly, so they must
     * run on the EDT - a worker thread calling them (a test, a background rebuild) otherwise races
     * the JTable / RowSorter internals and leaves {@code invokeLater} rebuilds queued past teardown.
     */
    private static void runOnEdt(Runnable r)
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            r.run();
            return;
        }
        try
        {
            SwingUtilities.invokeAndWait(r);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        catch (java.lang.reflect.InvocationTargetException e)
        {
            throw new IllegalStateException("filter marshal failed", e.getCause());
        }
    }

    public void applyShopFilters()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            runOnEdt(this::applyShopFilters);
            return;
        }
        if (shopDatabase == null) return;
        if (!shopDatabase.isLoaded()) shopDatabase.load();

        String query = shopSearchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedTown = (String) shopFilterTownDropdown.getSelectedItem();
        String selectedMemb = (String) shopFilterMembDropdown.getSelectedItem();
        boolean hideZero = shopHideZeroStockCheckbox.isSelected();

        currentShopRows.clear();
        shopTableModel.setRowCount(0);

        for (Shop shop : shopDatabase.getAllShops())
        {
            if (selectedTown != null && !selectedTown.startsWith("All") && !shop.getTown().equalsIgnoreCase(selectedTown))
            {
                continue;
            }

            if (shop.getItems() == null) continue;

            for (ShopItem item : shop.getItems())
            {
                if (hideZero && (item.isZeroDefaultStock() || item.getDefaultStock() <= 0))
                {
                    continue;
                }

                if (selectedMemb != null && !selectedMemb.startsWith("All"))
                {
                    if ("Members Only".equalsIgnoreCase(selectedMemb) && !shop.isMembersOnly()) continue;
                    if ("F2P Only".equalsIgnoreCase(selectedMemb) && shop.isMembersOnly()) continue;
                }

                if (!query.isEmpty())
                {
                    boolean match = item.getName().toLowerCase(Locale.ROOT).contains(query) ||
                        shop.getName().toLowerCase(Locale.ROOT).contains(query) ||
                        shop.getTown().toLowerCase(Locale.ROOT).contains(query) ||
                        shop.getNpcName().toLowerCase(Locale.ROOT).contains(query);
                    if (!match) continue;
                }

                ShopTableRowData rowData = new ShopTableRowData(shop, item);
                currentShopRows.add(rowData);

                shopTableModel.addRow(new Object[]{
                    shop.getTown(),
                    shop.getName(),
                    shop.getNpcName(),
                    item.getName(),
                    item.getDefaultStock(),
                    item.getPrice(),
                    item.getEffectiveBuyPrice(),
                    item.getRestockTimeSeconds() + "s",
                    shop.getCurrency().getShortName(),
                    shop.isMembersOnly() ? "Members" : "F2P"
                });
            }
        }

        shopRowCountLabel.setText("Showing " + NUMBER_FORMAT.format(currentShopRows.size()) + " shop items across " + shopDatabase.getAllShops().size() + " shops");
        if (guideViewMode)
        {
            rebuildShopGuideCards();
        }
    }

    public void applyMonsterFilters()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            runOnEdt(this::applyMonsterFilters);
            return;
        }
        if (monsterDatabase == null) return;
        if (!monsterDatabase.isLoaded()) monsterDatabase.load();

        String rawQuery = monsterSearchField.getText().trim().toLowerCase(Locale.ROOT);
        String[] queryTokens = rawQuery.isEmpty() ? new String[0] : rawQuery.split("[\\s\\-_/(),.]+");

        String selectedCat = (String) monsterCategoryDropdown.getSelectedItem();
        String selectedWildy = (String) monsterWildyDropdown.getSelectedItem();
        String selectedSlayer = (String) monsterSlayerDropdown.getSelectedItem();

        currentMonsterRows.clear();
        monsterTableModel.setRowCount(0);

        Set<Integer> uniqueMonsterIds = new HashSet<>();

        for (Monster monster : monsterDatabase.getAllMonsters())
        {
            // Category Filter
            if (selectedCat != null && !selectedCat.startsWith("All"))
            {
                if ("Bosses".equalsIgnoreCase(selectedCat) && !"Boss".equalsIgnoreCase(monster.getCategory())) continue;
                if ("Slayer".equalsIgnoreCase(selectedCat) && monster.getSlayerLevel() <= 1 && !"Slayer".equalsIgnoreCase(monster.getCategory())) continue;
                if ("Dragons".equalsIgnoreCase(selectedCat) && !"Dragon".equalsIgnoreCase(monster.getCategory()) && !monster.getName().toLowerCase(Locale.ROOT).contains("dragon") && !monster.getName().toLowerCase(Locale.ROOT).contains("wyvern") && !monster.getName().toLowerCase(Locale.ROOT).contains("drake") && !monster.getName().toLowerCase(Locale.ROOT).contains("hydra")) continue;
                if ("Demons".equalsIgnoreCase(selectedCat) && !"Demon".equalsIgnoreCase(monster.getCategory()) && !monster.getName().toLowerCase(Locale.ROOT).contains("demon") && !monster.getName().toLowerCase(Locale.ROOT).contains("fiend")) continue;
                if ("Undead".equalsIgnoreCase(selectedCat) && !"Undead".equalsIgnoreCase(monster.getCategory()) && !monster.getName().toLowerCase(Locale.ROOT).contains("skeleton") && !monster.getName().toLowerCase(Locale.ROOT).contains("zombie") && !monster.getName().toLowerCase(Locale.ROOT).contains("ghost") && !monster.getName().toLowerCase(Locale.ROOT).contains("ankou") && !monster.getName().toLowerCase(Locale.ROOT).contains("vampyre")) continue;
                if ("Wilderness".equalsIgnoreCase(selectedCat) && !"Wilderness".equalsIgnoreCase(monster.getCategory()) && (monster.getSpawnZones() == null || monster.getSpawnZones().stream().noneMatch(z -> z.getWildernessLevel() > 0))) continue;
                if ("F2P".equalsIgnoreCase(selectedCat) && monster.isMembers()) continue;
                if ("Members".equalsIgnoreCase(selectedCat) && !monster.isMembers()) continue;
            }

            // Slayer Filter
            if (selectedSlayer != null && !selectedSlayer.startsWith("All"))
            {
                if (selectedSlayer.contains("Req Only") && monster.getSlayerLevel() <= 1) continue;
                if (selectedSlayer.contains("No Slayer") && monster.getSlayerLevel() > 1) continue;
            }

            String formattedDrops = topDropsCache.computeIfAbsent(monster.getId(), k -> formatTopDrops(monster));

            List<MonsterSpawnZone> zones = monster.getSpawnZones();
            if (zones != null && !zones.isEmpty())
            {
                for (MonsterSpawnZone zone : zones)
                {
                    if (selectedWildy != null && !selectedWildy.startsWith("All"))
                    {
                        if ("Wilderness Only".equalsIgnoreCase(selectedWildy) && zone.getWildernessLevel() <= 0) continue;
                        if ("Safe Zones Only".equalsIgnoreCase(selectedWildy) && zone.getWildernessLevel() > 0) continue;
                    }

                    List<MonsterDrop> matchedDrops = new ArrayList<>();
                    if (!matchesMonsterSearch(monster, zone, queryTokens, matchedDrops))
                    {
                        continue;
                    }

                    MonsterTableRowData rowData = new MonsterTableRowData(monster, zone, formattedDrops, matchedDrops);
                    currentMonsterRows.add(rowData);
                    uniqueMonsterIds.add(monster.getId());

                    String locDisplay = buildLocationDisplay(zone);

                    monsterTableModel.addRow(new Object[]{
                        monster.getName(),
                        monster.getCategory() != null ? monster.getCategory() : "Monster",
                        monster.getCombatLevel(),
                        monster.getHitpoints(),
                        monster.getMaxHit(),
                        monster.getAttackType() != null ? monster.getAttackType() : "Melee",
                        monster.getWeakness() != null && !monster.getWeakness().isEmpty() ? monster.getWeakness() : "None",
                        monster.getSlayerLevel(),
                        monster.getQuestRequirement() != null ? monster.getQuestRequirement() : "None",
                        locDisplay,
                        formattedDrops
                    });
                }
            }
            else
            {
                if (selectedWildy != null && "Wilderness Only".equalsIgnoreCase(selectedWildy))
                {
                    continue;
                }

                List<MonsterDrop> matchedDrops = new ArrayList<>();
                if (!matchesMonsterSearch(monster, null, queryTokens, matchedDrops))
                {
                    continue;
                }

                MonsterTableRowData rowData = new MonsterTableRowData(monster, null, formattedDrops, matchedDrops);
                currentMonsterRows.add(rowData);
                uniqueMonsterIds.add(monster.getId());

                monsterTableModel.addRow(new Object[]{
                    monster.getName(),
                    monster.getCategory() != null ? monster.getCategory() : "Monster",
                    monster.getCombatLevel(),
                    monster.getHitpoints(),
                    monster.getMaxHit(),
                    monster.getAttackType() != null ? monster.getAttackType() : "Melee",
                    monster.getWeakness() != null && !monster.getWeakness().isEmpty() ? monster.getWeakness() : "None",
                    monster.getSlayerLevel(),
                    monster.getQuestRequirement() != null ? monster.getQuestRequirement() : "None",
                    "📍 Instance / Quest",
                    formattedDrops
                });
            }
        }

        monsterRowCountLabel.setText("Showing " + NUMBER_FORMAT.format(currentMonsterRows.size()) + " monster spawns across " + uniqueMonsterIds.size() + " unique monsters");
        if (guideViewMode)
        {
            rebuildMonsterGuideCards();
        }
    }

    public void applySlayerFilters()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            runOnEdt(this::applySlayerFilters);
            return;
        }
        if (slayerTaskManager == null) return;

        String query = slayerSearchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedMaster = (String) slayerMasterDropdown.getSelectedItem();
        String selectedReq = (String) slayerFilterReqDropdown.getSelectedItem();

        currentSlayerRows.clear();
        slayerTableModel.setRowCount(0);

        int activeMasterCount = 0;

        for (SlayerMaster master : SlayerMaster.ALL_MASTERS)
        {
            if (selectedMaster != null && !selectedMaster.startsWith("All") && !master.getName().equalsIgnoreCase(selectedMaster))
            {
                continue;
            }

            activeMasterCount++;
            List<SlayerTaskAssignment> assignments = slayerTaskManager.getAssignmentsForMaster(master.getName());

            for (SlayerTaskAssignment assignment : assignments)
            {
                Monster monster = monsterDatabase != null ? SlayerTaskManager.findMonsterForTask(assignment.getMonster(), monsterDatabase) : null;
                int slayerReq = monster != null ? monster.getSlayerLevel() : master.getSlayerRequirement();

                if (selectedReq != null && !selectedReq.startsWith("All"))
                {
                    if (selectedReq.contains("Slayer Req") && slayerReq <= 1) continue;
                    if (selectedReq.contains("Quest") && (assignment.getRequirement() == null || assignment.getRequirement().isEmpty())) continue;
                    if (selectedReq.contains("No Req") && (slayerReq > 1 || (assignment.getRequirement() != null && !assignment.getRequirement().isEmpty()))) continue;
                }

                if (!query.isEmpty())
                {
                    boolean match = assignment.getMonster().toLowerCase(Locale.ROOT).contains(query) ||
                        master.getName().toLowerCase(Locale.ROOT).contains(query) ||
                        master.getLocationName().toLowerCase(Locale.ROOT).contains(query) ||
                        (assignment.getRequirement() != null && assignment.getRequirement().toLowerCase(Locale.ROOT).contains(query)) ||
                        (assignment.getLocations() != null && assignment.getLocations().toLowerCase(Locale.ROOT).contains(query));
                    if (!match) continue;
                }

                SlayerTableRowData rowData = new SlayerTableRowData(master, assignment, monster);
                currentSlayerRows.add(rowData);

                String unlockReq = assignment.getRequirement() != null && !assignment.getRequirement().isEmpty()
                    ? assignment.getRequirement()
                    : (assignment.getExtended() != null ? ("Unlock: " + assignment.getExtended()) : "None");

                slayerTableModel.addRow(new Object[]{
                    master.getName(),
                    master.getLocationName(),
                    master.getCombatRequirement(),
                    slayerReq,
                    assignment.getMonster(),
                    assignment.getAmountDisplay(),
                    assignment.getWeight(),
                    unlockReq
                });
            }
        }

        slayerRowCountLabel.setText("Showing " + NUMBER_FORMAT.format(currentSlayerRows.size()) + " slayer task assignments across " + activeMasterCount + " slayer masters");
        if (guideViewMode)
        {
            rebuildSlayerGuideCards();
        }
    }

    public void applySearchFilters()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            runOnEdt(this::applySearchFilters);
            return;
        }
        String query = searchUniversalField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedSource = (String) searchSourceTypeDropdown.getSelectedItem();
        String selectedAccess = (String) searchMembDropdown.getSelectedItem();

        currentSearchRows.clear();
        searchTableModel.setRowCount(0);

        boolean includeShops = selectedSource == null || selectedSource.startsWith("All") || selectedSource.contains("Shops");
        boolean includeDrops = selectedSource == null || selectedSource.startsWith("All") || selectedSource.contains("Drops");

        // 1. Search Shop Items
        if (includeShops && shopDatabase != null && shopDatabase.isLoaded())
        {
            for (Shop shop : shopDatabase.getAllShops())
            {
                if (shop.getItems() == null) continue;
                for (ShopItem item : shop.getItems())
                {
                    if (selectedAccess != null && !selectedAccess.startsWith("All"))
                    {
                        if ("Members Only".equalsIgnoreCase(selectedAccess) && !shop.isMembersOnly()) continue;
                        if ("F2P Only".equalsIgnoreCase(selectedAccess) && shop.isMembersOnly()) continue;
                    }

                    if (!query.isEmpty())
                    {
                        boolean match = item.getName().toLowerCase(Locale.ROOT).contains(query) ||
                            shop.getName().toLowerCase(Locale.ROOT).contains(query) ||
                            shop.getTown().toLowerCase(Locale.ROOT).contains(query);
                        if (!match) continue;
                    }

                    GlobalSearchRowData rowData = new GlobalSearchRowData(shop, item);
                    currentSearchRows.add(rowData);

                    searchTableModel.addRow(new Object[]{
                        item.getName(),
                        "Shop",
                        shop.getName(),
                        shop.getTown(),
                        "Stock: " + item.getDefaultStock(),
                        NUMBER_FORMAT.format(item.getPrice()) + " " + shop.getCurrency().getShortName(),
                        shop.getQuestRequirement() != null ? shop.getQuestRequirement() : (shop.isMembersOnly() ? "Members" : "None")
                    });
                }
            }
        }

        // 2. Search Monster Drops
        if (includeDrops && monsterDatabase != null && monsterDatabase.isLoaded())
        {
            for (Monster monster : monsterDatabase.getAllMonsters())
            {
                if (monster.getDrops() == null) continue;

                if (selectedAccess != null && !selectedAccess.startsWith("All"))
                {
                    if ("Members Only".equalsIgnoreCase(selectedAccess) && !monster.isMembers()) continue;
                    if ("F2P Only".equalsIgnoreCase(selectedAccess) && monster.isMembers()) continue;
                }

                MonsterSpawnZone firstZone = monster.hasSpawnZones() ? monster.getSpawnZones().get(0) : null;
                String loc = firstZone != null ? firstZone.getLocationName() : "Instance / Dungeon";

                for (MonsterDrop drop : monster.getDrops())
                {
                    if (drop.getName() == null || drop.getName().isEmpty()) continue;

                    if (!query.isEmpty())
                    {
                        boolean match = drop.getName().toLowerCase(Locale.ROOT).contains(query) ||
                            monster.getName().toLowerCase(Locale.ROOT).contains(query) ||
                            loc.toLowerCase(Locale.ROOT).contains(query);
                        if (!match) continue;
                    }

                    GlobalSearchRowData rowData = new GlobalSearchRowData(monster, drop, firstZone);
                    currentSearchRows.add(rowData);

                    // "Price / Drop Rate" column keeps the wiki per-roll percent as a secondary rate;
                    // the "Stock / Rarity" column carries the per-kill "1/N" headline.
                    String dropRate = drop.getRarity() > 0 ? String.format(Locale.US, "%.2f%%", drop.getRarity() * 100) : "Varies";
                    String reqs = monster.getSlayerLevel() > 1 ? ("Slayer " + monster.getSlayerLevel()) : (monster.getQuestRequirement() != null ? monster.getQuestRequirement() : (monster.isMembers() ? "Members" : "None"));

                    searchTableModel.addRow(new Object[]{
                        drop.getName(),
                        "Monster Drop",
                        monster.getName(),
                        loc,
                        RarityFormat.perKill(drop),
                        dropRate,
                        reqs
                    });
                }
            }
        }

        searchRowCountLabel.setText("Showing " + NUMBER_FORMAT.format(currentSearchRows.size()) + " items and drops across shops and bestiary");
        if (guideViewMode)
        {
            rebuildSearchGuideCards();
        }
    }

    private boolean matchesMonsterSearch(Monster monster, MonsterSpawnZone zone, String[] tokens, List<MonsterDrop> matchedDropsOut)
    {
        if (tokens.length == 0) return true;

        String name = monster.getName().toLowerCase(Locale.ROOT);
        String category = monster.getCategory() != null ? monster.getCategory().toLowerCase(Locale.ROOT) : "";
        String weakness = monster.getWeakness() != null ? monster.getWeakness().toLowerCase(Locale.ROOT) : "";
        String attributes = monster.getAttributes() != null ? monster.getAttributes().toLowerCase(Locale.ROOT) : "";
        String attackType = monster.getAttackType() != null ? monster.getAttackType().toLowerCase(Locale.ROOT) : "";
        String loc = zone != null ? (zone.getLocationName() + " " + zone.getZoneName() + " " + (zone.getDungeonName() != null ? zone.getDungeonName() : "")).toLowerCase(Locale.ROOT) : "";
        String combatLvl = String.valueOf(monster.getCombatLevel());
        String questReq = monster.getQuestRequirement() != null ? monster.getQuestRequirement().toLowerCase(Locale.ROOT) : "";

        List<String> dropTokens = null;
        for (String token : tokens)
        {
            if (token.isEmpty()) continue;

            boolean foundInFields = name.contains(token) ||
                loc.contains(token) ||
                weakness.contains(token) ||
                attributes.contains(token) ||
                attackType.contains(token) ||
                category.contains(token) ||
                combatLvl.equals(token) ||
                questReq.contains(token);

            if (!foundInFields)
            {
                if (dropTokens == null) dropTokens = new ArrayList<>();
                dropTokens.add(token);
            }
        }

        if (dropTokens == null)
        {
            return true;
        }

        if (monster.getDrops() == null) return false;

        boolean anyDropMatched = false;
        for (MonsterDrop drop : monster.getDrops())
        {
            if (drop.getName() == null || drop.getName().isEmpty()) continue;
            String dropName = drop.getName().toLowerCase(Locale.ROOT);

            boolean allRemainingTokensInThisDrop = true;
            for (String dropToken : dropTokens)
            {
                if (!dropName.contains(dropToken))
                {
                    allRemainingTokensInThisDrop = false;
                    break;
                }
            }

            if (allRemainingTokensInThisDrop)
            {
                anyDropMatched = true;
                if (matchedDropsOut != null && !containsDropByName(matchedDropsOut, drop))
                {
                    matchedDropsOut.add(drop);
                }
            }
        }

        return anyDropMatched;
    }

    private boolean containsDropByName(List<MonsterDrop> drops, MonsterDrop candidate)
    {
        if (candidate.getName() == null) return false;
        for (MonsterDrop d : drops)
        {
            if (candidate.getName().equalsIgnoreCase(d.getName())) return true;
        }
        return false;
    }

    private String buildDropsTooltip(MonsterTableRowData data)
    {
        if (data == null || data.monster == null) return null;
        List<MonsterDrop> allDrops = data.monster.getDrops();
        if (allDrops == null || allDrops.isEmpty()) return null;

        StringBuilder sb = new StringBuilder("<html>");
        sb.append("<b>All Drops for ").append(data.monster.getName()).append(" (").append(allDrops.size()).append("):</b><br>");
        int shown = 0;
        for (MonsterDrop d : allDrops)
        {
            if (d.getName() == null || dropNameBlank(d)) continue;
            sb.append("&#8226; ").append(escapeHtml(d.getName())).append(" (").append(escapeHtml(RarityFormat.perKill(d))).append(")<br>");
            shown++;
            if (shown >= 25 && allDrops.size() > shown)
            {
                sb.append("&hellip; +").append(allDrops.size() - shown).append(" more");
                break;
            }
        }
        sb.append("</html>");
        return sb.toString();
    }

    private boolean dropNameBlank(MonsterDrop drop)
    {
        return drop.getName() == null || drop.getName().trim().isEmpty();
    }

    private String escapeHtml(String s)
    {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String buildLocationDisplay(MonsterSpawnZone zone)
    {
        if (zone == null) return "Instance / Quest";
        String loc = zone.getLocationName();
        if (loc == null || loc.trim().isEmpty()) loc = zone.getZoneName();
        if (loc == null || loc.trim().isEmpty()) loc = zone.getDungeonName();
        if (loc == null || loc.trim().isEmpty()) loc = "Surface";
        return loc;
    }

    private String formatTopDrops(Monster monster)
    {
        if (monster.getDrops() == null || monster.getDrops().isEmpty())
        {
            return "<span style='color: #94A3B8;'>None</span>";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (MonsterDrop drop : monster.getDrops())
        {
            if (drop.getName() == null || drop.getName().isEmpty()) continue;
            if (count > 0) sb.append(", ");
            String name = drop.getName();
            // Per-kill headline + its shared tier colour (neutral grey for an unknown "Varies" rate).
            String rarity = RarityFormat.perKill(drop);
            Color rc = RarityFormat.perKillColor(drop);
            String color = String.format("#%02x%02x%02x", rc.getRed(), rc.getGreen(), rc.getBlue());
            sb.append("<span style='color: ").append(color).append(";'>").append(escapeHtml(name));
            if (drop.getQuantity() != null && !drop.getQuantity().equals("1") && !drop.getQuantity().isEmpty())
            {
                sb.append(" x").append(escapeHtml(drop.getQuantity()));
            }
            if (rarity != null && !rarity.isEmpty() && !"Varies".equalsIgnoreCase(rarity))
            {
                sb.append(" (").append(escapeHtml(rarity)).append(")");
            }
            sb.append("</span>");
            count++;
            if (count >= 3)
            {
                if (monster.getDrops().size() > 3)
                {
                    sb.append(" <span style='color: #94A3B8;'>(+").append(monster.getDrops().size() - 3).append(" more)</span>");
                }
                break;
            }
        }
        return sb.length() > 0 ? sb.toString() : "<span style='color: #94A3B8;'>None</span>";
    }

    private void panMapToMonsterRow(MonsterTableRowData data)
    {
        if (data == null || data.monster == null) return;

        if (data.zone != null && onFocusMonsterZoneOnMap != null)
        {
            onFocusMonsterZoneOnMap.accept(data.monster, data.zone);
        }
        else if (onFocusMonsterOnMap != null)
        {
            onFocusMonsterOnMap.accept(data.monster);
        }
    }

    private String formatPriceBadge(int price)
    {
        if (price >= 1_000_000) return String.format(Locale.US, "%.1fM", price / 1_000_000.0);
        if (price >= 1_000) return (price / 1000) + "k";
        return price + " gp";
    }

    private void loadItemSprite(int itemId, JLabel targetLabel)
    {
        if (itemId > 0 && itemManager != null)
        {
            AsyncBufferedImage img = itemManager.getImage(itemId);
            img.addTo(targetLabel);
        }
        else
        {
            targetLabel.setText("");
            targetLabel.setIcon(null);
        }
    }

    // ==========================================
    // POPULATORS & NAVIGATION HELPERS
    // ==========================================
    private void populateTownDropdown()
    {
        if (shopDatabase != null && shopDatabase.isLoaded() && shopFilterTownDropdown.getItemCount() <= 1)
        {
            for (var town : shopDatabase.getAllTowns())
            {
                shopFilterTownDropdown.addItem(town.getName());
            }
        }
    }

    private void populateSlayerMasterDropdown()
    {
        if (slayerMasterDropdown.getItemCount() <= 1)
        {
            for (SlayerMaster master : SlayerMaster.ALL_MASTERS)
            {
                slayerMasterDropdown.addItem(master.getName());
            }
        }
    }

    public void openSpreadsheet(String initialQuery)
    {
        if (initialQuery != null && !initialQuery.isEmpty())
        {
            if (TAB_MONSTERS.equals(activeTab)) openMonsterTab(initialQuery);
            else if (TAB_SLAYER.equals(activeTab)) openSlayerTab(initialQuery);
            else if (TAB_SEARCH.equals(activeTab)) openSearchTab(initialQuery);
            else openShopTab(initialQuery);
            return;
        }
        selectTab(activeTab);
        setVisible(true);
    }

    public void openShopTab(String initialQuery)
    {
        selectTab(TAB_SHOPS);
        populateTownDropdown();
        if (initialQuery != null && !initialQuery.isEmpty())
        {
            shopSearchField.setText(initialQuery);
        }
        applyShopFilters();
        setVisible(true);
    }

    public void openMonsterTab(String initialQuery)
    {
        selectTab(TAB_MONSTERS);
        if (initialQuery != null && !initialQuery.isEmpty())
        {
            monsterSearchField.setText(initialQuery);
        }
        applyMonsterFilters();
        setVisible(true);
    }

    public void openSlayerTab(String initialQuery)
    {
        selectTab(TAB_SLAYER);
        if (initialQuery != null && !initialQuery.isEmpty())
        {
            slayerSearchField.setText(initialQuery);
        }
        applySlayerFilters();
        setVisible(true);
    }

    public void openSearchTab(String initialQuery)
    {
        selectTab(TAB_SEARCH);
        if (initialQuery != null && !initialQuery.isEmpty())
        {
            searchUniversalField.setText(initialQuery);
        }
        applySearchFilters();
        setVisible(true);
    }

    // ==========================================
    // ROW CONTEXT MENUS
    // ==========================================
    private JPopupMenu createShopRowContextMenu(ShopTableRowData data)
    {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG_STONE_PANEL);
        menu.setBorder(BorderFactory.createLineBorder(BORDER_GOLD, 1));

        JMenuItem add1 = new JMenuItem("Add to Cart (1)");
        styleMenuItem(add1);
        add1.addActionListener(e -> {
            cartManager.addItem(data.item, data.shop, 1);
            setFeedback("✓ Added 1x " + data.item.getName() + " to cart!");
        });
        menu.add(add1);

        JMenuItem add5 = new JMenuItem("Add to Cart (5)");
        styleMenuItem(add5);
        add5.addActionListener(e -> {
            cartManager.addItem(data.item, data.shop, 5);
            setFeedback("✓ Added 5x " + data.item.getName() + " to cart!");
        });
        menu.add(add5);

        JMenuItem add10 = new JMenuItem("Add to Cart (10)");
        styleMenuItem(add10);
        add10.addActionListener(e -> {
            cartManager.addItem(data.item, data.shop, 10);
            setFeedback("✓ Added 10x " + data.item.getName() + " to cart!");
        });
        menu.add(add10);

        JMenuItem addAll = new JMenuItem("Add All (Stock: " + data.item.getDefaultStock() + ")");
        styleMenuItem(addAll);
        addAll.addActionListener(e -> {
            int qty = data.item.getDefaultStock() > 0 ? data.item.getDefaultStock() : 1;
            cartManager.addItem(data.item, data.shop, qty);
            setFeedback("✓ Added " + qty + "x " + data.item.getName() + " to cart!");
        });
        menu.add(addAll);

        JMenuItem addX = new JMenuItem("Add X...");
        styleMenuItem(addX);
        addX.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(
                this,
                "Enter quantity to add to cart for " + data.item.getName() + ":",
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
                        cartManager.addItem(data.item, data.shop, qty);
                        setFeedback("✓ Added " + qty + "x " + data.item.getName() + " to cart!");
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

        JMenuItem focus = new JMenuItem("Pan Map to Shop");
        styleMenuItem(focus);
        focus.addActionListener(e -> {
            if (onFocusShopOnMap != null) onFocusShopOnMap.accept(data.shop);
            setFeedback("Centered map on " + data.shop.getName() + ".");
        });
        menu.add(focus);

        JMenuItem viewStock = new JMenuItem("View in Sidebar Stock");
        styleMenuItem(viewStock);
        viewStock.addActionListener(e -> {
            if (onInspectShop != null) onInspectShop.accept(data.shop);
            if (clientToolbar != null && navButton != null) SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
            setFeedback("Opened " + data.shop.getName() + " in sidebar.");
        });
        menu.add(viewStock);

        JMenuItem wiki = new JMenuItem("Open OSRS Wiki");
        styleMenuItem(wiki);
        wiki.addActionListener(e -> openWikiPage(data.item.getName()));
        menu.add(wiki);

        return menu;
    }

    private JPopupMenu createMonsterRowContextMenu(MonsterTableRowData data)
    {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG_STONE_PANEL);
        menu.setBorder(BorderFactory.createLineBorder(BORDER_GOLD, 1));

        JMenuItem focus = new JMenuItem("Pan Map to Spawn");
        styleMenuItem(focus);
        focus.addActionListener(e -> {
            panMapToMonsterRow(data);
            setFeedback("Centered map on " + data.monster.getName() + " spawn.");
        });
        menu.add(focus);

        JMenuItem viewSidebar = new JMenuItem("View in Sidebar");
        styleMenuItem(viewSidebar);
        viewSidebar.addActionListener(e -> {
            if (onInspectMonster != null) onInspectMonster.accept(data.monster);
            if (clientToolbar != null && navButton != null) SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
            setFeedback("Opened " + data.monster.getName() + " in sidebar.");
        });
        menu.add(viewSidebar);

        JMenuItem wiki = new JMenuItem("Open OSRS Wiki");
        styleMenuItem(wiki);
        wiki.addActionListener(e -> openMonsterWiki(data.monster));
        menu.add(wiki);

        menu.addSeparator();

        if (data.zone != null && data.zone.getLocationName() != null && !data.zone.getLocationName().isEmpty())
        {
            JMenuItem filterLoc = new JMenuItem("Filter by Location: " + data.zone.getLocationName());
            styleMenuItem(filterLoc);
            filterLoc.addActionListener(e -> {
                monsterSearchField.setText(data.zone.getLocationName());
                applyMonsterFilters();
            });
            menu.add(filterLoc);
        }

        if (data.monster.getWeakness() != null && !data.monster.getWeakness().isEmpty())
        {
            JMenuItem filterWeak = new JMenuItem("Filter by Weakness: " + data.monster.getWeakness());
            styleMenuItem(filterWeak);
            filterWeak.addActionListener(e -> {
                monsterSearchField.setText(data.monster.getWeakness());
                applyMonsterFilters();
            });
            menu.add(filterWeak);
        }

        return menu;
    }

    private JPopupMenu createSlayerRowContextMenu(SlayerTableRowData data)
    {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG_STONE_PANEL);
        menu.setBorder(BorderFactory.createLineBorder(BORDER_GOLD, 1));

        if (data.monster != null)
        {
            JMenuItem focusMonster = new JMenuItem("Pan Map to Task Monster Spawn");
            styleMenuItem(focusMonster);
            focusMonster.addActionListener(e -> {
                if (onFocusMonsterOnMap != null) onFocusMonsterOnMap.accept(data.monster);
                setFeedback("Centered map on " + data.monster.getName() + ".");
            });
            menu.add(focusMonster);

            JMenuItem viewMonster = new JMenuItem("View Monster in Sidebar");
            styleMenuItem(viewMonster);
            viewMonster.addActionListener(e -> {
                if (onInspectMonster != null) onInspectMonster.accept(data.monster);
                if (clientToolbar != null && navButton != null) SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
                setFeedback("Opened " + data.monster.getName() + " in sidebar.");
            });
            menu.add(viewMonster);
        }

        if (data.master != null && data.master.getLocationPoint() != null)
        {
            JMenuItem focusMaster = new JMenuItem("Pan Map to " + data.master.getName());
            styleMenuItem(focusMaster);
            focusMaster.addActionListener(e -> {
                if (onFocusPointOnMap != null) onFocusPointOnMap.accept(data.master.getLocationPoint(), data.master.getName());
                setFeedback("Centered map on " + data.master.getName() + ".");
            });
            menu.add(focusMaster);
        }

        JMenuItem wiki = new JMenuItem("Open OSRS Wiki");
        styleMenuItem(wiki);
        wiki.addActionListener(e -> openWikiPage(data.assignment.getMonster()));
        menu.add(wiki);

        return menu;
    }

    private JPopupMenu createSearchRowContextMenu(GlobalSearchRowData data)
    {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG_STONE_PANEL);
        menu.setBorder(BorderFactory.createLineBorder(BORDER_GOLD, 1));

        if (data.type == GlobalSearchRowData.SourceType.SHOP)
        {
            JMenuItem addCart = new JMenuItem("Add to Cart");
            styleMenuItem(addCart);
            addCart.addActionListener(e -> {
                cartManager.addItem(data.shopItem, data.shop, 1);
                setFeedback("✓ Added 1x " + data.shopItem.getName() + " to cart!");
            });
            menu.add(addCart);

            JMenuItem focus = new JMenuItem("Pan Map to Shop");
            styleMenuItem(focus);
            focus.addActionListener(e -> {
                if (onFocusShopOnMap != null) onFocusShopOnMap.accept(data.shop);
                setFeedback("Centered map on " + data.shop.getName() + ".");
            });
            menu.add(focus);

            JMenuItem viewSidebar = new JMenuItem("View Shop in Sidebar");
            styleMenuItem(viewSidebar);
            viewSidebar.addActionListener(e -> {
                if (onInspectShop != null) onInspectShop.accept(data.shop);
                if (clientToolbar != null && navButton != null) SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
                setFeedback("Opened " + data.shop.getName() + " in sidebar.");
            });
            menu.add(viewSidebar);

            JMenuItem wiki = new JMenuItem("Open OSRS Wiki");
            styleMenuItem(wiki);
            wiki.addActionListener(e -> openWikiPage(data.shopItem.getName()));
            menu.add(wiki);
        }
        else
        {
            JMenuItem focus = new JMenuItem("Pan Map to Monster Spawn");
            styleMenuItem(focus);
            focus.addActionListener(e -> {
                if (data.monsterZone != null && onFocusMonsterZoneOnMap != null)
                {
                    onFocusMonsterZoneOnMap.accept(data.monster, data.monsterZone);
                }
                else if (onFocusMonsterOnMap != null)
                {
                    onFocusMonsterOnMap.accept(data.monster);
                }
                setFeedback("Centered map on " + data.monster.getName() + ".");
            });
            menu.add(focus);

            JMenuItem viewSidebar = new JMenuItem("View Monster in Sidebar");
            styleMenuItem(viewSidebar);
            viewSidebar.addActionListener(e -> {
                if (onInspectMonster != null) onInspectMonster.accept(data.monster);
                if (clientToolbar != null && navButton != null) SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
                setFeedback("Opened " + data.monster.getName() + " in sidebar.");
            });
            menu.add(viewSidebar);

            JMenuItem wiki = new JMenuItem("Open OSRS Wiki");
            styleMenuItem(wiki);
            wiki.addActionListener(e -> openWikiPage(data.monsterDrop.getName()));
            menu.add(wiki);
        }

        return menu;
    }

    // ==========================================
    // COLUMN CHOOSER POPUPS & VISIBILITY
    // ==========================================
    private void showShopColumnSelectorPopup(Component invoker, int x, int y)
    {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG_STONE_PANEL);
        menu.setBorder(BorderFactory.createLineBorder(BORDER_GOLD, 1));

        JLabel title = new JLabel("  Shop Columns:");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(TITLE_GOLD);
        title.setBorder(new EmptyBorder(4, 8, 4, 8));
        menu.add(title);
        menu.addSeparator();

        for (int i = 0; i < SHOP_COLUMN_NAMES.length; i++)
        {
            final int colIndex = i;
            javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(SHOP_COLUMN_NAMES[i], shopColumnVisible[i]);
            item.setFont(FontManager.getRunescapeFont());
            item.setBackground(BG_STONE_PANEL);
            item.setForeground(Color.WHITE);
            item.addActionListener(e -> setShopColumnVisible(colIndex, item.isSelected()));
            menu.add(item);
        }
        menu.show(invoker, x, y);
    }

    public void setShopColumnVisible(int modelIndex, boolean visible)
    {
        if (modelIndex < 0 || modelIndex >= shopColumnVisible.length) return;
        if (shopColumnVisible[modelIndex] == visible) return;

        int visibleCount = 0;
        for (boolean v : shopColumnVisible) if (v) visibleCount++;
        if (!visible && visibleCount <= 1) return;

        shopColumnVisible[modelIndex] = visible;
        rebuildShopTableColumns();
    }

    private void rebuildShopTableColumns()
    {
        TableColumnModel colModel = shopTable.getColumnModel();
        while (colModel.getColumnCount() > 0) colModel.removeColumn(colModel.getColumn(0));
        for (int i = 0; i < allShopTableColumns.size(); i++)
        {
            if (shopColumnVisible[i]) colModel.addColumn(allShopTableColumns.get(i));
        }
        shopTable.revalidate();
        shopTable.repaint();
    }

    private void showMonsterColumnSelectorPopup(Component invoker, int x, int y)
    {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG_STONE_PANEL);
        menu.setBorder(BorderFactory.createLineBorder(BORDER_GOLD, 1));

        JLabel title = new JLabel("  Bestiary Columns:");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(TITLE_GOLD);
        title.setBorder(new EmptyBorder(4, 8, 4, 8));
        menu.add(title);
        menu.addSeparator();

        for (int i = 0; i < MONSTER_COLUMN_NAMES.length; i++)
        {
            final int colIndex = i;
            javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(MONSTER_COLUMN_NAMES[i], monsterColumnVisible[i]);
            item.setFont(FontManager.getRunescapeFont());
            item.setBackground(BG_STONE_PANEL);
            item.setForeground(Color.WHITE);
            item.addActionListener(e -> setMonsterColumnVisible(colIndex, item.isSelected()));
            menu.add(item);
        }
        menu.show(invoker, x, y);
    }

    public void setMonsterColumnVisible(int modelIndex, boolean visible)
    {
        if (modelIndex < 0 || modelIndex >= monsterColumnVisible.length) return;
        if (monsterColumnVisible[modelIndex] == visible) return;

        int visibleCount = 0;
        for (boolean v : monsterColumnVisible) if (v) visibleCount++;
        if (!visible && visibleCount <= 1) return;

        monsterColumnVisible[modelIndex] = visible;
        rebuildMonsterTableColumns();
    }

    private void rebuildMonsterTableColumns()
    {
        TableColumnModel colModel = monsterTable.getColumnModel();
        while (colModel.getColumnCount() > 0) colModel.removeColumn(colModel.getColumn(0));
        for (int i = 0; i < allMonsterTableColumns.size(); i++)
        {
            if (monsterColumnVisible[i]) colModel.addColumn(allMonsterTableColumns.get(i));
        }
        monsterTable.revalidate();
        monsterTable.repaint();
    }

    private void showSlayerColumnSelectorPopup(Component invoker, int x, int y)
    {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG_STONE_PANEL);
        menu.setBorder(BorderFactory.createLineBorder(BORDER_GOLD, 1));

        JLabel title = new JLabel("  Slayer Columns:");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(TITLE_GOLD);
        title.setBorder(new EmptyBorder(4, 8, 4, 8));
        menu.add(title);
        menu.addSeparator();

        for (int i = 0; i < SLAYER_COLUMN_NAMES.length; i++)
        {
            final int colIndex = i;
            javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(SLAYER_COLUMN_NAMES[i], slayerColumnVisible[i]);
            item.setFont(FontManager.getRunescapeFont());
            item.setBackground(BG_STONE_PANEL);
            item.setForeground(Color.WHITE);
            item.addActionListener(e -> {
                slayerColumnVisible[colIndex] = item.isSelected();
                rebuildSlayerTableColumns();
            });
            menu.add(item);
        }
        menu.show(invoker, x, y);
    }

    private void rebuildSlayerTableColumns()
    {
        TableColumnModel colModel = slayerTable.getColumnModel();
        while (colModel.getColumnCount() > 0) colModel.removeColumn(colModel.getColumn(0));
        for (int i = 0; i < allSlayerTableColumns.size(); i++)
        {
            if (slayerColumnVisible[i]) colModel.addColumn(allSlayerTableColumns.get(i));
        }
        slayerTable.revalidate();
        slayerTable.repaint();
    }

    private void showSearchColumnSelectorPopup(Component invoker, int x, int y)
    {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG_STONE_PANEL);
        menu.setBorder(BorderFactory.createLineBorder(BORDER_GOLD, 1));

        JLabel title = new JLabel("  Search Columns:");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(TITLE_GOLD);
        title.setBorder(new EmptyBorder(4, 8, 4, 8));
        menu.add(title);
        menu.addSeparator();

        for (int i = 0; i < SEARCH_COLUMN_NAMES.length; i++)
        {
            final int colIndex = i;
            javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(SEARCH_COLUMN_NAMES[i], searchColumnVisible[i]);
            item.setFont(FontManager.getRunescapeFont());
            item.setBackground(BG_STONE_PANEL);
            item.setForeground(Color.WHITE);
            item.addActionListener(e -> {
                searchColumnVisible[colIndex] = item.isSelected();
                rebuildSearchTableColumns();
            });
            menu.add(item);
        }
        menu.show(invoker, x, y);
    }

    private void rebuildSearchTableColumns()
    {
        TableColumnModel colModel = searchTable.getColumnModel();
        while (colModel.getColumnCount() > 0) colModel.removeColumn(colModel.getColumn(0));
        for (int i = 0; i < allSearchTableColumns.size(); i++)
        {
            if (searchColumnVisible[i]) colModel.addColumn(allSearchTableColumns.get(i));
        }
        searchTable.revalidate();
        searchTable.repaint();
    }

    // ==========================================
    // STYLING & BORDER UTILITIES
    // ==========================================
    private Border buildDoubleBeveledStoneBorder()
    {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_GOLD, 2),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_STONE_OUTER, 1),
                BorderFactory.createLineBorder(BORDER_STONE_HIGHLIGHT, 1)
            )
        );
    }

    private void styleTextField(JTextField tf)
    {
        tf.setBackground(BG_STONE_DARK);
        tf.setForeground(PRICE_GOLD);
        tf.setCaretColor(PRICE_GOLD);
        tf.setFont(FontManager.getRunescapeFont());
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_STONE_OUTER, 1),
            new EmptyBorder(4, 8, 4, 8)
        ));
    }

    private void styleDropdown(JComboBox<String> dropdown)
    {
        dropdown.setBackground(BG_STONE_DARK);
        dropdown.setForeground(TEXT_PARCHMENT);
        dropdown.setFont(FontManager.getRunescapeFont());
        dropdown.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                c.setFont(FontManager.getRunescapeFont());
                if (isSelected)
                {
                    c.setBackground(ROW_SELECTED);
                    c.setForeground(TITLE_GOLD);
                }
                else
                {
                    c.setBackground(BG_STONE_DARK);
                    c.setForeground(TEXT_PARCHMENT);
                }
                return c;
            }
        });
    }

    private void styleStoneButton(JButton btn, Color textColor)
    {
        btn.setFont(FontManager.getRunescapeBoldFont());
        btn.setBackground(BG_STONE_BTN);
        btn.setForeground(textColor);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_STONE_OUTER, 1),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_STONE_HIGHLIGHT, 1),
                new EmptyBorder(4, 10, 4, 10)
            )
        ));

        btn.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                btn.setBackground(BG_STONE_BTN_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                btn.setBackground(BG_STONE_BTN);
            }

            @Override
            public void mousePressed(MouseEvent e)
            {
                btn.setBackground(BG_STONE_BTN_PRESS);
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                btn.setBackground(BG_STONE_BTN_HOVER);
            }
        });
    }

    private void styleMiniButton(JButton btn)
    {
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setBackground(BG_STONE_BTN);
        btn.setForeground(TEXT_PARCHMENT);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(24, 24));
        btn.setBorder(BorderFactory.createLineBorder(BORDER_STONE_OUTER, 1));
    }

    private void styleTable(JTable table)
    {
        table.setBackground(BG_STONE_DARK);
        table.setForeground(TEXT_PARCHMENT);
        table.setFont(FontManager.getRunescapeFont());
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
    }

    private interface HeaderPopupInvoker
    {
        void show(Component header, int x, int y);
    }

    private void configureTableHeader(JTable table, HeaderPopupInvoker popupInvoker)
    {
        JTableHeader header = table.getTableHeader();
        header.setBackground(BG_STONE_HEADER);
        header.setForeground(TITLE_GOLD);
        header.setFont(FontManager.getRunescapeBoldFont());
        header.setPreferredSize(new Dimension(0, 32));
        header.setReorderingAllowed(true);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, BORDER_GOLD));

        header.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (e.isPopupTrigger()) popupInvoker.show(header, e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (e.isPopupTrigger()) popupInvoker.show(header, e.getX(), e.getY());
            }
        });
    }

    private void styleScrollPane(JScrollPane scrollPane)
    {
        scrollPane.setBackground(BG_STONE_DARK);
        scrollPane.getViewport().setBackground(BG_STONE_DARK);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_STONE_OUTER, 1));
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
    }

    private void styleMenuItem(JMenuItem item)
    {
        item.setFont(FontManager.getRunescapeFont());
        item.setBackground(BG_STONE_PANEL);
        item.setForeground(TEXT_PARCHMENT);
    }

    private void applyRowColors(Component c, int row, boolean isSelected)
    {
        if (isSelected)
        {
            c.setBackground(ROW_SELECTED);
            c.setForeground(PRICE_GOLD);
        }
        else
        {
            c.setBackground(row % 2 == 0 ? ROW_EVEN : ROW_ODD);
            c.setForeground(TEXT_PARCHMENT);
        }
    }

    private void openWikiPage(String name)
    {
        if (name == null || name.trim().isEmpty()) return;
        try
        {
            String url = "https://oldschool.runescape.wiki/w/Special:Search?search=" +
                URLEncoder.encode(name, StandardCharsets.UTF_8.name());
            LinkBrowser.browse(url);
        }
        catch (Exception ex)
        {
            LinkBrowser.browse("https://oldschool.runescape.wiki/w/" + name.replace(' ', '_'));
        }
    }

    private void openMonsterWiki(Monster monster)
    {
        if (monster == null) return;
        if (monster.getWikiUrl() != null && !monster.getWikiUrl().isEmpty())
        {
            LinkBrowser.browse(monster.getWikiUrl());
        }
        else
        {
            openWikiPage(monster.getName());
        }
    }

    // ==========================================
    // GETTERS, SETTERS & CALLBACK WIRING
    // ==========================================
    public void setCallbacks(Consumer<Shop> onFocusShopOnMap, Consumer<Shop> onInspectShop)
    {
        this.onFocusShopOnMap = onFocusShopOnMap;
        this.onInspectShop = onInspectShop;
    }

    public void setCallbacks(
        Consumer<Shop> onFocusShopOnMap,
        Consumer<Shop> onInspectShop,
        Consumer<Monster> onFocusMonsterOnMap,
        Consumer<Monster> onInspectMonster,
        BiConsumer<Monster, MonsterSpawnZone> onFocusMonsterZoneOnMap)
    {
        this.onFocusShopOnMap = onFocusShopOnMap;
        this.onInspectShop = onInspectShop;
        this.onFocusMonsterOnMap = onFocusMonsterOnMap;
        this.onInspectMonster = onInspectMonster;
        this.onFocusMonsterZoneOnMap = onFocusMonsterZoneOnMap;
    }

    public void setCallbacks(
        Consumer<Shop> onFocusShopOnMap,
        Consumer<Shop> onInspectShop,
        Consumer<Monster> onFocusMonsterOnMap,
        Consumer<Monster> onInspectMonster,
        BiConsumer<Monster, MonsterSpawnZone> onFocusMonsterZoneOnMap,
        BiConsumer<WorldPoint, String> onFocusPointOnMap,
        Consumer<String> onOpenSlayerTab)
    {
        this.onFocusShopOnMap = onFocusShopOnMap;
        this.onInspectShop = onInspectShop;
        this.onFocusMonsterOnMap = onFocusMonsterOnMap;
        this.onInspectMonster = onInspectMonster;
        this.onFocusMonsterZoneOnMap = onFocusMonsterZoneOnMap;
        this.onFocusPointOnMap = onFocusPointOnMap;
        this.onOpenSlayerTab = onOpenSlayerTab;
    }

    public void setMonsterCallbacks(
        Consumer<Monster> onFocusMonsterOnMap,
        Consumer<Monster> onInspectMonster,
        BiConsumer<Monster, MonsterSpawnZone> onFocusMonsterZoneOnMap)
    {
        this.onFocusMonsterOnMap = onFocusMonsterOnMap;
        this.onInspectMonster = onInspectMonster;
        this.onFocusMonsterZoneOnMap = onFocusMonsterZoneOnMap;
    }

    public void setOnFocusShopOnMap(Consumer<Shop> callback) { this.onFocusShopOnMap = callback; }
    public void setOnInspectShop(Consumer<Shop> callback) { this.onInspectShop = callback; }
    public void setOnOpenShopDetail(Consumer<Shop> callback) { this.onInspectShop = callback; }
    public void setOnFocusMonsterOnMap(Consumer<Monster> callback) { this.onFocusMonsterOnMap = callback; }
    public void setOnInspectMonster(Consumer<Monster> callback) { this.onInspectMonster = callback; }
    public void setOnOpenMonsterDetail(Consumer<Monster> callback) { this.onInspectMonster = callback; }
    public void setOnFocusMonsterZoneOnMap(BiConsumer<Monster, MonsterSpawnZone> callback) { this.onFocusMonsterZoneOnMap = callback; }
    public void setOnFocusPointOnMap(BiConsumer<WorldPoint, String> callback) { this.onFocusPointOnMap = callback; }
    public void setOnOpenSlayerTab(Consumer<String> callback) { this.onOpenSlayerTab = callback; }

    public JTable getShopTable() { return shopTable; }
    public JTable getMonsterTable() { return monsterTable; }
    public JTable getSlayerTable() { return slayerTable; }
    public JTable getSearchTable() { return searchTable; }

    public DefaultTableModel getShopTableModel() { return shopTableModel; }
    public DefaultTableModel getMonsterTableModel() { return monsterTableModel; }
    public DefaultTableModel getSlayerTableModel() { return slayerTableModel; }
    public DefaultTableModel getSearchTableModel() { return searchTableModel; }

    public String getActiveTab() { return activeTab; }
    public String getActiveSubFilter() { return activeSubFilter; }

    public JTable getActiveTable()
    {
        switch (activeTab)
        {
            case TAB_MONSTERS: return monsterTable;
            case TAB_SLAYER: return slayerTable;
            case TAB_SEARCH: return searchTable;
            case TAB_SHOPS:
            default:
                return shopTable;
        }
    }

    public javax.swing.JButton getAddCartButton() { return addCartBtn; }
    public String getFooterStatusText() { return footerStatusLabel.getText(); }
    public javax.swing.JTextField getShopSearchField() { return shopSearchField; }
    public javax.swing.JTextField getMonsterSearchField() { return monsterSearchField; }
    public javax.swing.JTextField getSlayerSearchField() { return slayerSearchField; }
    public javax.swing.JTextField getSearchUniversalField() { return searchUniversalField; }
    public javax.swing.JPanel getShopCardsListPanel() { return shopCardsListPanel; }
    public javax.swing.JPanel getMonsterCardsListPanel() { return monsterCardsListPanel; }
    public javax.swing.JPanel getSlayerCardsListPanel() { return slayerCardsListPanel; }
    public javax.swing.JPanel getSearchCardsListPanel() { return searchCardsListPanel; }

    // ==========================================
    // DATA MODEL STRUCTURES
    // ==========================================
    public static class ShopTableRowData
    {
        public final Shop shop;
        public final ShopItem item;

        public ShopTableRowData(Shop shop, ShopItem item)
        {
            this.shop = shop;
            this.item = item;
        }
    }

    public static class TableRowData extends ShopTableRowData
    {
        public TableRowData(Shop shop, ShopItem item)
        {
            super(shop, item);
        }
    }

    public static class MonsterTableRowData
    {
        public final Monster monster;
        public final MonsterSpawnZone zone;
        public final String dropsSummary;
        public final List<MonsterDrop> matchedDrops;

        public MonsterTableRowData(Monster monster, MonsterSpawnZone zone, String dropsSummary, List<MonsterDrop> matchedDrops)
        {
            this.monster = monster;
            this.zone = zone;
            this.dropsSummary = dropsSummary;
            this.matchedDrops = matchedDrops;
        }
    }

    public static class SlayerTableRowData
    {
        public final SlayerMaster master;
        public final SlayerTaskAssignment assignment;
        public final Monster monster;

        public SlayerTableRowData(SlayerMaster master, SlayerTaskAssignment assignment, Monster monster)
        {
            this.master = master;
            this.assignment = assignment;
            this.monster = monster;
        }
    }

    public static class GlobalSearchRowData
    {
        public enum SourceType { SHOP, MONSTER_DROP }
        public final SourceType type;
        public final Shop shop;
        public final ShopItem shopItem;
        public final Monster monster;
        public final MonsterDrop monsterDrop;
        public final MonsterSpawnZone monsterZone;

        public GlobalSearchRowData(Shop shop, ShopItem item)
        {
            this.type = SourceType.SHOP;
            this.shop = shop;
            this.shopItem = item;
            this.monster = null;
            this.monsterDrop = null;
            this.monsterZone = null;
        }

        public GlobalSearchRowData(Monster monster, MonsterDrop drop, MonsterSpawnZone zone)
        {
            this.type = SourceType.MONSTER_DROP;
            this.shop = null;
            this.shopItem = null;
            this.monster = monster;
            this.monsterDrop = drop;
            this.monsterZone = zone;
        }
    }
}
