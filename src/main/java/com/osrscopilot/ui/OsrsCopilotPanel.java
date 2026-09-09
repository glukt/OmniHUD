package com.osrscopilot.ui;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.ShopLiveStockManager;
import com.osrscopilot.data.ShoppingCartManager;
import com.osrscopilot.data.SlayerTaskManager;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterSpawnZone;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.util.NpcPortraitManager;
import com.osrscopilot.ui.theme.CopilotPalette;
import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.ui.CombatEncounterTabView;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class OsrsCopilotPanel extends PluginPanel
{
    public static final String VIEW_TOWNS = "TOWNS";
    public static final String VIEW_STOCK = "STOCK";
    public static final String VIEW_MONSTERS = "MONSTERS";
    public static final String VIEW_MONSTER_DETAIL = "MONSTER_DETAIL";
    public static final String VIEW_COMBAT = "COMBAT";
    public static final String VIEW_COMBAT_LOG = "COMBAT_LOG";
    public static final String VIEW_SLAYER = "SLAYER";
    public static final String VIEW_SEARCH = "SEARCH";
    public static final String VIEW_LOOT = "LOOT";
    public static final String VIEW_CART = "CART";
    public static final String VIEW_ABOUT = "ABOUT";
    private static final int RESIZE_HANDLE_WIDTH = 6;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentContainer = new JPanel(cardLayout);

    // The currently shown card, and the tab "< Back" from the monster detail view returns to -
    // the monster detail view is opened from the Bestiary, Slayer, Combat, Search and a map click,
    // so a hard-wired return to the Bestiary strands the user (D9).
    private String currentTab = VIEW_COMBAT;
    private String monsterDetailReturnTab = VIEW_MONSTERS;

    private final TownHubView townHubView;
    private final VendorStockView vendorStockView;
    private final MonsterDirectoryView monsterDirectoryView;
    private final MonsterDetailView monsterDetailView;
    private final CombatEncounterTabView combatEncounterTabView;
    private final com.osrscopilot.combat.ui.CombatLogView combatLogView;
    private final SlayerTabView slayerTabView;


    private final GlobalItemSearchView globalItemSearchView;
    private final com.osrscopilot.loot.ui.LootTabView lootTabView;
    private final ShoppingCartView shoppingCartView;
    private final AboutView aboutView;
    private final ShopDirectorySpreadsheetDialog spreadsheetDialog;
    private final ShoppingCartManager cartManager;
    private Runnable cartListener;

    private final JButton townsBtn = new JButton("Towns");
    private final JButton stockBtn = new JButton("Shops");
    private final JButton monstersBtn = new JButton("Bestiary");
    private final JButton combatBtn = new JButton("Combat");
    private final JButton slayerBtn = new JButton("Slayer");
    private final JButton searchBtn = new JButton("Search");
    private final JButton lootBtn = new JButton("Loot");
    private final JButton cartBtn = new JButton("Cart");
    private final JButton aboutBtn = new JButton("About");


    private boolean isDraggingLeftEdge = false;
    private int dragStartX = 0;
    private int initialWidth = 225;

    // -1 == "no drag has occurred yet" -> defer entirely to PluginPanel's hardcoded RuneLite default
    // (PANEL_WIDTH + SCROLLBAR_WIDTH = 242px). Once the user drags the left edge, this holds the
    // clamped [205,850] target width and getPreferredSize()/getMinimumSize() below return it instead.
    private int draggedWidth = -1;

    public OsrsCopilotPanel(
        ShopDatabase shopDatabase,
        MonsterDatabase monsterDatabase,
        ItemManager itemManager,
        NpcPortraitManager npcPortraitManager,
        ShopLiveStockManager liveStockManager,
        ShopDirectorySpreadsheetDialog spreadsheetDialog,
        OsrsCopilotConfig config,
        ShoppingCartManager cartManager,
        Consumer<Shop> onFocusShopOnMap,
        Consumer<Monster> onFocusMonsterOnMap,
        BiConsumer<Monster, MonsterSpawnZone> onFocusMonsterZoneOnMap)
    {
        this(
            shopDatabase,
            monsterDatabase,
            new SlayerTaskManager(null, monsterDatabase),
            itemManager,
            npcPortraitManager,
            liveStockManager,
            spreadsheetDialog,
            config,
            cartManager,
            onFocusShopOnMap,
            onFocusMonsterOnMap,
            onFocusMonsterZoneOnMap,
            null
        );
    }

    public OsrsCopilotPanel(
        ShopDatabase shopDatabase,
        MonsterDatabase monsterDatabase,
        SlayerTaskManager slayerTaskManager,
        ItemManager itemManager,
        NpcPortraitManager npcPortraitManager,
        ShopLiveStockManager liveStockManager,
        ShopDirectorySpreadsheetDialog spreadsheetDialog,
        OsrsCopilotConfig config,
        ShoppingCartManager cartManager,
        Consumer<Shop> onFocusShopOnMap,
        Consumer<Monster> onFocusMonsterOnMap,
        BiConsumer<Monster, MonsterSpawnZone> onFocusMonsterZoneOnMap,
        BiConsumer<WorldPoint, String> onFocusPointOnMap)
    {
        this(
            shopDatabase,
            monsterDatabase,
            slayerTaskManager,
            itemManager,
            npcPortraitManager,
            liveStockManager,
            spreadsheetDialog,
            config,
            cartManager,
            onFocusShopOnMap,
            onFocusMonsterOnMap,
            onFocusMonsterZoneOnMap,
            onFocusPointOnMap,
            null,
            null
        );
    }

    public OsrsCopilotPanel(
        ShopDatabase shopDatabase,
        MonsterDatabase monsterDatabase,
        SlayerTaskManager slayerTaskManager,
        ItemManager itemManager,
        NpcPortraitManager npcPortraitManager,
        ShopLiveStockManager liveStockManager,
        ShopDirectorySpreadsheetDialog spreadsheetDialog,
        OsrsCopilotConfig config,
        ShoppingCartManager cartManager,
        Consumer<Shop> onFocusShopOnMap,
        Consumer<Monster> onFocusMonsterOnMap,
        BiConsumer<Monster, MonsterSpawnZone> onFocusMonsterZoneOnMap,
        BiConsumer<WorldPoint, String> onFocusPointOnMap,
        CombatEncounterManager combatEncounterManager,
        com.osrscopilot.loot.LootTrackerManager lootTrackerManager)
    {
        super(false);
        this.spreadsheetDialog = spreadsheetDialog;
        this.cartManager = cartManager != null ? cartManager : ShoppingCartManager.getInstance();

        setLayout(new BorderLayout(0, 0));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // 1. Initialize Subviews
        this.vendorStockView = new VendorStockView(
            itemManager,
            npcPortraitManager,
            liveStockManager,
            config,
            this.cartManager,
            onFocusShopOnMap,
            () -> showTab(VIEW_TOWNS)
        );

        this.townHubView = new TownHubView(
            shopDatabase,
            npcPortraitManager,
            shop -> {
                vendorStockView.setShop(shop);
                showTab(VIEW_STOCK);
            },
            onFocusShopOnMap,
            () -> spreadsheetDialog.openSpreadsheet(null)
        );

        this.monsterDetailView = new MonsterDetailView(
            itemManager,
            npcPortraitManager,
            this.cartManager,
            (monster, zone) -> {
                if (onFocusMonsterZoneOnMap != null)
                {
                    onFocusMonsterZoneOnMap.accept(monster, zone);
                }
                else if (onFocusMonsterOnMap != null)
                {
                    onFocusMonsterOnMap.accept(monster);
                }
            },
            () -> showTab(monsterDetailReturnTab)
        );

        this.monsterDirectoryView = new MonsterDirectoryView(
            monsterDatabase,
            npcPortraitManager,
            monster -> {
                monsterDetailView.setMonster(monster);
                showTab(VIEW_MONSTER_DETAIL);
            },
            monster -> {
                if (onFocusMonsterOnMap != null)
                {
                    onFocusMonsterOnMap.accept(monster);
                }
            },
            () -> spreadsheetDialog.openMonsterTab(null)
        );

        CombatEncounterManager cem = combatEncounterManager != null ? combatEncounterManager : new CombatEncounterManager(null);
        this.combatEncounterTabView = new CombatEncounterTabView(
            cem,
            monsterDatabase,
            itemManager,
            this::openMonster
        );
        this.combatLogView = new com.osrscopilot.combat.ui.CombatLogView(cem, () -> showTab(VIEW_COMBAT));
        this.combatEncounterTabView.setOnOpenLog(() -> {
            showTab(VIEW_COMBAT_LOG);
            combatLogView.refresh();
        });

        this.slayerTabView = new SlayerTabView(
            slayerTaskManager,
            monsterDatabase,
            npcPortraitManager,
            (monster, zone) -> {
                if (onFocusMonsterZoneOnMap != null)
                {
                    onFocusMonsterZoneOnMap.accept(monster, zone);
                }
                else if (onFocusMonsterOnMap != null)
                {
                    onFocusMonsterOnMap.accept(monster);
                }
            },
            onFocusPointOnMap,
            this::openMonster
        );

        this.globalItemSearchView = new GlobalItemSearchView(
            shopDatabase,
            monsterDatabase,
            npcPortraitManager,
            config,
            this.cartManager,
            shop -> {
                vendorStockView.setShop(shop);
                showTab(VIEW_STOCK);
            },
            onFocusShopOnMap,
            (monster, zone) -> {
                if (onFocusMonsterZoneOnMap != null)
                {
                    onFocusMonsterZoneOnMap.accept(monster, zone);
                }
                else if (onFocusMonsterOnMap != null)
                {
                    onFocusMonsterOnMap.accept(monster);
                }
            },
            this::openMonster,
            () -> spreadsheetDialog.openSpreadsheet(null)
        );

        this.lootTabView = new com.osrscopilot.loot.ui.LootTabView(
            lootTrackerManager, monsterDatabase, itemManager, this::openMonster);

        this.shoppingCartView = new ShoppingCartView(
            this.cartManager,
            shopDatabase,
            itemManager,
            onFocusShopOnMap,
            shop -> {
                vendorStockView.setShop(shop);
                showTab(VIEW_STOCK);
            }
        );

        // 2. Wire spreadsheet dialog callbacks
        spreadsheetDialog.setCallbacks(
            onFocusShopOnMap,
            shop -> {
                vendorStockView.setShop(shop);
                showTab(VIEW_STOCK);
            },
            onFocusMonsterOnMap,
            monster -> {
                monsterDetailView.setMonster(monster);
                showTab(VIEW_MONSTER_DETAIL);
            },
            onFocusMonsterZoneOnMap,
            onFocusPointOnMap,
            masterName -> {
                if (slayerTabView != null)
                {
                    slayerTabView.selectMasterByName(masterName);
                }
                showTab(VIEW_SLAYER);
            }
        );

        this.aboutView = new AboutView(this::showTab);

        // 3. Top Navigation Bar - Title Bar + 3-Row 3-Col OSRS Grid
        JPanel topHeader = new JPanel(new BorderLayout(0, 0));
        topHeader.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        topHeader.setBorder(new EmptyBorder(4, 3, 4, 3));

        JPanel headerCol = new JPanel();
        headerCol.setLayout(new BoxLayout(headerCol, BoxLayout.Y_AXIS));
        headerCol.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Title Bar with plugin title
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        titleBar.setBorder(new EmptyBorder(0, 2, 3, 2));

        JLabel titleLabel = new JLabel("OmniHUD");
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setForeground(CopilotPalette.ACCENT);
        titleBar.add(titleLabel, BorderLayout.WEST);

        headerCol.add(titleBar);

        // 9 tabs on a 3x3 grid. "Bestiary" is too wide for a 5-column strip (the shape a 9th tab
        // gave the old GridLayout(2, 4)); 3x3 keeps every RS-font label inside its button down to
        // the 205px drag-floor and drops the ragged empty cell.
        // Row 1: Combat, Towns, Shops | Row 2: Bestiary, Slayer, Search | Row 3: Loot, Cart, About
        JPanel tabGroup = new JPanel(new GridLayout(3, 3, 2, 2));
        tabGroup.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Combat leads - it is the most-used tab and the plugin's landing view.
        styleNavButton(combatBtn);
        combatBtn.setToolTipText("Combat: Live fight DPS, encounter logs, damage styles, and consumable ledger");
        combatBtn.addActionListener(e -> showTab(VIEW_COMBAT));
        tabGroup.add(combatBtn);

        styleNavButton(townsBtn);
        townsBtn.setToolTipText("Town Hubs: Browse settlements, regional services, and local vendors");
        townsBtn.addActionListener(e -> showTab(VIEW_TOWNS));
        tabGroup.add(townsBtn);

        styleNavButton(stockBtn);
        stockBtn.setToolTipText("Vendor Stock: Browse shops, inventories, stock counts, and prices");
        stockBtn.addActionListener(e -> showTab(VIEW_STOCK));
        tabGroup.add(stockBtn);

        styleNavButton(monstersBtn);
        monstersBtn.setToolTipText("Bestiary: Search monsters, combat stats, drop tables, and spawn zones");
        monstersBtn.addActionListener(e -> showTab(VIEW_MONSTERS));
        tabGroup.add(monstersBtn);

        styleNavButton(slayerBtn);
        slayerBtn.setToolTipText("Slayer: Track active task, kill counts, masters, and assigned monster locations");
        slayerBtn.addActionListener(e -> showTab(VIEW_SLAYER));
        tabGroup.add(slayerBtn);

        styleNavButton(searchBtn);
        searchBtn.setToolTipText("Item & Drop Search: Search items across all vendor shops and monster drops");
        searchBtn.addActionListener(e -> showTab(VIEW_SEARCH));
        tabGroup.add(searchBtn);

        styleNavButton(lootBtn);
        lootBtn.setToolTipText("Loot Tracker: your recorded drops per monster, your rate vs the wiki rate, and per-item history");
        lootBtn.addActionListener(e -> showTab(VIEW_LOOT));
        tabGroup.add(lootBtn);

        styleNavButton(cartBtn);
        cartBtn.setToolTipText("Shopping Cart: Calculate total cost, budget checks, and plan vendor trips");
        cartBtn.addActionListener(e -> showTab(VIEW_CART));
        tabGroup.add(cartBtn);

        styleNavButton(aboutBtn);
        aboutBtn.setToolTipText("About: Feature overview, guides, and capabilities");
        aboutBtn.addActionListener(e -> showTab(VIEW_ABOUT));
        tabGroup.add(aboutBtn);

        headerCol.add(tabGroup);
        topHeader.add(headerCol, BorderLayout.CENTER);

        // Content
        contentContainer.add(townHubView, VIEW_TOWNS);
        contentContainer.add(vendorStockView, VIEW_STOCK);
        contentContainer.add(monsterDirectoryView, VIEW_MONSTERS);
        contentContainer.add(monsterDetailView, VIEW_MONSTER_DETAIL);
        contentContainer.add(combatEncounterTabView, VIEW_COMBAT);
        contentContainer.add(combatLogView, VIEW_COMBAT_LOG);
        contentContainer.add(slayerTabView, VIEW_SLAYER);
        contentContainer.add(globalItemSearchView, VIEW_SEARCH);
        contentContainer.add(lootTabView, VIEW_LOOT);
        contentContainer.add(shoppingCartView, VIEW_CART);
        contentContainer.add(aboutView, VIEW_ABOUT);


        // 4. Drag Left-Edge Resize Handle
        //
        // BUG FIX (playtest report, 2026-08-20): the previous implementation attached the resize
        // MouseListener/MouseMotionListener directly to `this` (the outermost panel) and tried to
        // detect the left-edge zone with an `e.getX() <= RESIZE_HANDLE_WIDTH` check inside
        // mouseMoved/mousePressed. That structurally cannot work: `this` is constructed with
        // super(false) (see PluginPanel), so it has zero border/insets, and `topHeader` +
        // `contentContainer` (added NORTH/CENTER via a zero-gap BorderLayout) together tile 100% of
        // `this`'s bounds -- there is no pixel of `this`'s own area left exposed anywhere,
        // including at x=0. Swing/AWT dispatches every MouseEvent to the single deepest component
        // under the pointer and does NOT bubble it to ancestors, so `this`'s own listener never
        // received a single real event -- confirmed empirically (a scripted desktop UI test firing
        // native mouse-move/press/drag events at a reproduction of this exact layout: the outer panel's
        // listener fired 0 times at every x from 0-150, while the deeply nested content label
        // received all of them, even at x=0..6). Whatever caused the live "resize cursor whenever
        // trying to use a side panel" report, it could not have been this listener's mouseMoved
        // setting a cursor that then bled down via AWT's ancestor cursor-resolution walk, because
        // `this.setCursor(...)` was never actually being invoked in the first place. Net effect: the
        // feature was both unreliable to trigger *and* impossible to reason about/fix by tweaking
        // the coordinate threshold, since the component holding the listener never saw the mouse.
        //
        // Fix: give the resize zone a real, dedicated component instead of trying to detect it via
        // coordinates on a panel that never receives events. `resizeHandle` is exactly
        // RESIZE_HANDLE_WIDTH px wide and added at BorderLayout.WEST of `this`, so it spans the
        // full sidebar height and is a genuine sibling -- never an ancestor -- of `topHeader` and
        // `contentContainer` (both moved into a plain `innerContent` wrapper added at CENTER). Its
        // cursor is set once, statically, to W_RESIZE: since resizeHandle only ever shows that
        // cursor for its own real bounds, and no descendant of innerContent has resizeHandle in its
        // ancestor chain, AWT's cursor-resolution walk can never bleed it into card content -- there
        // is nothing dynamic left to get stuck. Re-verified with the same scripted desktop UI test
        // against this exact structure: resizeHandle's mouseMoved/mousePressed/mouseDragged
        // all fire correctly for its own 6px column, content-area mouseMoved/cursor are completely
        // unaffected, and a full press-drag-release gesture starting in the handle works end-to-end.
        JPanel resizeHandle = new JPanel();
        resizeHandle.setPreferredSize(new Dimension(RESIZE_HANDLE_WIDTH, 0));
        resizeHandle.setBackground(ColorScheme.DARK_GRAY_COLOR);
        resizeHandle.setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));

        MouseAdapter resizeListener = new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                isDraggingLeftEdge = true;
                dragStartX = e.getXOnScreen();
                initialWidth = getWidth();
            }

            @Override
            public void mouseDragged(MouseEvent e)
            {
                if (isDraggingLeftEdge)
                {
                    int delta = dragStartX - e.getXOnScreen();
                    int newWidth = Math.max(205, Math.min(850, initialWidth + delta));
                    draggedWidth = newWidth;
                    setSize(new Dimension(newWidth, getHeight()));
                    revalidateContainer(getParent());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                isDraggingLeftEdge = false;
            }
        };

        resizeHandle.addMouseListener(resizeListener);
        resizeHandle.addMouseMotionListener(resizeListener);

        JPanel innerContent = new JPanel(new BorderLayout(0, 0));
        innerContent.setBackground(ColorScheme.DARK_GRAY_COLOR);
        innerContent.add(topHeader, BorderLayout.NORTH);
        innerContent.add(contentContainer, BorderLayout.CENTER);

        add(resizeHandle, BorderLayout.WEST);
        add(innerContent, BorderLayout.CENTER);

        showTab(VIEW_COMBAT);

        // Cart Badge Listener: updates Cart tab title with item count. Kept in a field so
        // dispose() can unregister it - the ShoppingCartManager is a @Singleton that outlives
        // this panel, so a fresh lambda per plugin enable would leak the whole panel tree.
        this.cartListener = () -> {
            Runnable update = () -> {
                int count = this.cartManager.getItemCount();
                cartBtn.setText(count > 0 ? "Cart(" + count + ")" : "Cart");
            };
            if (SwingUtilities.isEventDispatchThread())
            {
                update.run();
            }
            else
            {
                SwingUtilities.invokeLater(update);
            }
        };
        this.cartManager.addCartListener(this.cartListener);
    }

    /** Unregisters this panel's listeners (cart + combat views). Call from the plugin's shutDown(). */
    public void dispose()
    {
        if (cartManager != null && cartListener != null)
        {
            cartManager.removeCartListener(cartListener);
        }
        if (combatEncounterTabView != null)
        {
            combatEncounterTabView.dispose();
        }
        if (combatLogView != null)
        {
            combatLogView.dispose();
        }
    }

    /**
     * Overrides PluginPanel's hardcoded {@code getPreferredSize()} (which always returns
     * PANEL_WIDTH + SCROLLBAR_WIDTH == 242px regardless of what a subclass wants, since this
     * constructor uses {@code super(false)} so {@code getWrappedPanel() == this}) so the drag-to-resize
     * handler below actually changes the rendered width instead of being dead code. RuneLite's
     * sidebar JTabbedPane (ClientUI) hosts {@code getWrappedPanel()} -- i.e. this exact instance --
     * directly as a tab component and sizes the content area from its getPreferredSize()/
     * getMinimumSize(). Because Java method dispatch always calls the most-derived override, this
     * subclass override is what ClientUI actually observes; PluginPanel's version is never consulted
     * once this one exists. Falls back to the inherited RuneLite default until the user drags.
     */
    @Override
    public Dimension getPreferredSize()
    {
        Dimension base = super.getPreferredSize();
        return draggedWidth > 0 ? new Dimension(draggedWidth, base.height) : base;
    }

    /**
     * Paired with {@link #getPreferredSize()} above -- without also overriding getMinimumSize(),
     * PluginPanel's hardcoded 242px minimum would still stop the panel from ever being dragged
     * narrower than that, even though the drag handler clamps down to 205px.
     */
    @Override
    public Dimension getMinimumSize()
    {
        Dimension base = super.getMinimumSize();
        return draggedWidth > 0 ? new Dimension(draggedWidth, base.height) : base;
    }

    private void revalidateContainer(Container parent)
    {
        Container c = parent;
        while (c != null)
        {
            c.revalidate();
            c.repaint();
            c = c.getParent();
        }
        revalidate();
        repaint();
    }

    private void styleNavButton(JButton btn)
    {
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(0, 0, 0, 0));
        btn.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        btn.setPreferredSize(new Dimension(0, 22));
    }

    public void showTab(String tabName)
    {
        // Remember where we came from so "< Back" from the monster detail view returns there.
        if (VIEW_MONSTER_DETAIL.equals(tabName))
        {
            if (!VIEW_MONSTER_DETAIL.equals(currentTab))
            {
                // Only return to a tab that actually makes sense as a "back" target.
                monsterDetailReturnTab =
                    (VIEW_SLAYER.equals(currentTab) || VIEW_COMBAT.equals(currentTab) || VIEW_SEARCH.equals(currentTab))
                        ? currentTab : VIEW_MONSTERS;
            }
        }
        else if (tabName != null)
        {
            currentTab = tabName;
        }

        cardLayout.show(contentContainer, tabName);

        boolean isTowns = VIEW_TOWNS.equals(tabName);
        boolean isStock = VIEW_STOCK.equals(tabName);
        boolean isMonsters = VIEW_MONSTERS.equals(tabName) || VIEW_MONSTER_DETAIL.equals(tabName);
        boolean isCombat = VIEW_COMBAT.equals(tabName);
        boolean isSlayer = VIEW_SLAYER.equals(tabName);
        boolean isSearch = VIEW_SEARCH.equals(tabName);
        boolean isLoot = VIEW_LOOT.equals(tabName);
        boolean isCart = VIEW_CART.equals(tabName);
        boolean isAbout = VIEW_ABOUT.equals(tabName);

        townsBtn.setBackground(isTowns ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARK_GRAY_COLOR);
        stockBtn.setBackground(isStock ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARK_GRAY_COLOR);
        monstersBtn.setBackground(isMonsters ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARK_GRAY_COLOR);
        combatBtn.setBackground(isCombat ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARK_GRAY_COLOR);
        slayerBtn.setBackground(isSlayer ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARK_GRAY_COLOR);
        searchBtn.setBackground(isSearch ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARK_GRAY_COLOR);
        lootBtn.setBackground(isLoot ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARK_GRAY_COLOR);
        cartBtn.setBackground(isCart ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARK_GRAY_COLOR);
        aboutBtn.setBackground(isAbout ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARK_GRAY_COLOR);

        townsBtn.setForeground(isTowns ? CopilotPalette.ACCENT : Color.WHITE);
        stockBtn.setForeground(isStock ? CopilotPalette.ACCENT : Color.WHITE);
        monstersBtn.setForeground(isMonsters ? CopilotPalette.ACCENT : Color.WHITE);
        combatBtn.setForeground(isCombat ? CopilotPalette.ACCENT : Color.WHITE);
        slayerBtn.setForeground(isSlayer ? CopilotPalette.ACCENT : Color.WHITE);
        searchBtn.setForeground(isSearch ? CopilotPalette.ACCENT : Color.WHITE);
        lootBtn.setForeground(isLoot ? CopilotPalette.ACCENT : Color.WHITE);
        cartBtn.setForeground(isCart ? CopilotPalette.ACCENT : Color.WHITE);
        aboutBtn.setForeground(isAbout ? CopilotPalette.ACCENT : Color.WHITE);

        if (isSlayer && slayerTabView != null)
        {
            slayerTabView.refresh();
        }
        if (isLoot && lootTabView != null)
        {
            lootTabView.refresh();
        }
        if (isCombat && combatEncounterTabView != null)
        {
            combatEncounterTabView.refreshUI();
        }
        if (VIEW_COMBAT_LOG.equals(tabName) && combatLogView != null)
        {
            combatBtn.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
            combatBtn.setForeground(CopilotPalette.ACCENT);
            combatLogView.refresh();
        }
    }

    public void initialize()
    {
        townHubView.initialize();
        monsterDirectoryView.initialize();
        if (slayerTabView != null)
        {
            slayerTabView.refresh();
        }
        if (combatEncounterTabView != null)
        {
            combatEncounterTabView.refreshUI();
        }
    }

    public void openShop(Shop shop)
    {
        vendorStockView.setShop(shop);
        showTab(VIEW_STOCK);
    }

    public void openTown(String townName)
    {
        townHubView.selectTownByName(townName);
        showTab(VIEW_TOWNS);
    }

    public void openMonster(Monster monster)
    {
        monsterDetailView.setMonster(monster);
        showTab(VIEW_MONSTER_DETAIL);
    }

    public void openMonsterDirectory()
    {
        showTab(VIEW_MONSTERS);
    }

    public void openCombat()
    {
        showTab(VIEW_COMBAT);
    }

    public void openSlayer()
    {
        showTab(VIEW_SLAYER);
    }

    public void openCart()
    {
        showTab(VIEW_CART);
    }

    public void openLootTab()
    {
        showTab(VIEW_LOOT);
    }

    public TownHubView getTownHubView() { return townHubView; }
    public VendorStockView getVendorStockView() { return vendorStockView; }
    public MonsterDirectoryView getMonsterDirectoryView() { return monsterDirectoryView; }
    public MonsterDetailView getMonsterDetailView() { return monsterDetailView; }
    public CombatEncounterTabView getCombatEncounterTabView() { return combatEncounterTabView; }

    public AboutView getAboutView() { return aboutView; }
    public com.osrscopilot.loot.ui.LootTabView getLootTabView() { return lootTabView; }
    public SlayerTabView getSlayerTabView() { return slayerTabView; }
    public GlobalItemSearchView getGlobalItemSearchView() { return globalItemSearchView; }

    public String getCurrentTab() { return currentTab; }
    public ShoppingCartView getShoppingCartView() { return shoppingCartView; }
}

