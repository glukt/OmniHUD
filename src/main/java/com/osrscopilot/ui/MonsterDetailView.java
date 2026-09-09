package com.osrscopilot.ui;

import com.osrscopilot.data.RarityFormat;
import com.osrscopilot.data.ShoppingCartManager;
import com.osrscopilot.data.model.CartItem;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterDrop;
import com.osrscopilot.data.model.MonsterSpawnZone;
import com.osrscopilot.ui.theme.CopilotPalette;
import com.osrscopilot.ui.theme.SidebarMetrics;
import com.osrscopilot.util.NpcPortraitManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;

public class MonsterDetailView extends JPanel
{
    // Semantic colours resolve to CopilotPalette; the remaining new Color(...) values (the amber
    // guidance note, the three trait-badge pastels, the category-header strip) are one-offs.
    private static final Color TITLE_COLOR = CopilotPalette.ACCENT;
    private static final Color COMBAT_LVL_COLOR = CopilotPalette.GOLD;
    private static final Color SLAYER_PURPLE = CopilotPalette.SLAYER;
    private static final Color QUEST_PURPLE = CopilotPalette.QUEST;
    private static final Color FOCUS_BLUE = CopilotPalette.LINK;
    private static final Color WEAKNESS_GREEN = CopilotPalette.WEAKNESS;
    private static final Color WILDY_RED = CopilotPalette.WILDY;
    private static final Color MULTI_ORANGE = CopilotPalette.MULTI;

    // Kept as public constants for existing callers; the values now live in the shared
    // RarityFormat tier ramp so the Bestiary, spreadsheet and world map can never diverge.
    public static final Color RARITY_ALWAYS = RarityFormat.ALWAYS;
    public static final Color RARITY_COMMON = RarityFormat.COMMON;
    public static final Color RARITY_UNCOMMON = RarityFormat.UNCOMMON;
    public static final Color RARITY_RARE = RarityFormat.RARE;
    public static final Color RARITY_VERY_RARE = RarityFormat.VERY_RARE;

    public static final List<String> DROP_CATEGORIES = List.of(
        "100% Drops",
        "Weapons and Armour",
        "Runes and Ammunition",
        "Herbs",
        "Seeds",
        "Tertiary / Uniques",
        "Other"
    );

    private final ItemManager itemManager;
    private final NpcPortraitManager npcPortraitManager;
    private final ShoppingCartManager cartManager;
    private final BiConsumer<Monster, MonsterSpawnZone> onFocusSpawnZone;
    private final Runnable onBackToDirectory;

    private Monster currentMonster;
    private boolean isTableView = false;
    private final List<MonsterDrop> displayedDrops = new ArrayList<>();

    // Drop-filter perf: the same freeze pattern MonsterDirectoryView fixed one layer up -- an
    // unbounded synchronous card/table rebuild (per drop: panel + icon + tooltip + JPopupMenu + 4
    // listeners) on every keystroke. Debounce the filter, and cap how many rows are built per pass
    // with a "Show more" row for the rest. Cap resets whenever the monster / query / sort / view
    // changes.
    private static final int DROP_RENDER_LIMIT = 80;
    private final javax.swing.Timer dropFilterDebounce = new javax.swing.Timer(160, e -> rebuildDrops());
    private int dropRenderCap = DROP_RENDER_LIMIT;

    // Hero Header UI components
    private final JLabel titleLabel = new JLabel();
    private final JLabel combatLevelLabel = new JLabel();
    private final JLabel combatStatsLabel = new JLabel();
    private final JPanel badgeContainer = new JPanel();
    private final JPanel badgeRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
    private final JPanel badgeRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
    private final JPanel badgeRow3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));

    // Weaknesses UI card
    private final JPanel weaknessPanel = new JPanel();
    private final JLabel weaknessTextLabel = new JLabel();
    private final JLabel weaknessExtraLabel = new JLabel();
    private final JLabel defenceStatsLabel = new JLabel();
    private final JLabel defenceStatsLine2 = new JLabel();

    // Spawns UI card
    private final JPanel spawnsContainer = new JPanel();

    // The single scroll pane wrapping the whole view - kept as a field so setMonster() can reset
    // it to the top when switching monsters.
    private JScrollPane masterScrollPane;

    // Drop table UI components
    private final JTextField dropSearchField = new JTextField();
    private final JComboBox<String> dropSortDropdown = new JComboBox<>();
    private final JButton viewToggleBtn = new JButton("Table");
    private final JLabel dropCountLabel = new JLabel();
    private final JPanel dropsCardsContainer = new JPanel();
    private final JPanel dropsTableContainer = new JPanel();
    private final JPanel dropsContentHolder = new JPanel(new BorderLayout());

    public MonsterDetailView(
        ItemManager itemManager,
        NpcPortraitManager npcPortraitManager,
        Consumer<MonsterSpawnZone> onFocusSpawnZone,
        Runnable onBackToDirectory)
    {
        this(itemManager, npcPortraitManager, (ShoppingCartManager) null, (m, z) -> {
            if (onFocusSpawnZone != null) onFocusSpawnZone.accept(z);
        }, onBackToDirectory);
    }

    public MonsterDetailView(
        ItemManager itemManager,
        NpcPortraitManager npcPortraitManager,
        BiConsumer<Monster, MonsterSpawnZone> onFocusSpawnZone,
        Runnable onBackToDirectory)
    {
        this(itemManager, npcPortraitManager, (ShoppingCartManager) null, onFocusSpawnZone, onBackToDirectory);
    }

    public MonsterDetailView(
        ItemManager itemManager,
        NpcPortraitManager npcPortraitManager,
        ShoppingCartManager cartManager,
        Consumer<MonsterSpawnZone> onFocusSpawnZone,
        Runnable onBackToDirectory)
    {
        this(itemManager, npcPortraitManager, cartManager, (m, z) -> {
            if (onFocusSpawnZone != null) onFocusSpawnZone.accept(z);
        }, onBackToDirectory);
    }

    public MonsterDetailView(
        ItemManager itemManager,
        NpcPortraitManager npcPortraitManager,
        ShoppingCartManager cartManager,
        BiConsumer<Monster, MonsterSpawnZone> onFocusSpawnZone,
        Runnable onBackToDirectory)
    {
        this.itemManager = itemManager;
        this.npcPortraitManager = npcPortraitManager;
        this.cartManager = cartManager;
        this.onFocusSpawnZone = onFocusSpawnZone;
        this.onBackToDirectory = onBackToDirectory;

        dropFilterDebounce.setRepeats(false);

        setLayout(new BorderLayout(0, 0));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Master Scrollable Container
        JPanel masterContent = new JPanel();
        masterContent.setLayout(new BoxLayout(masterContent, BoxLayout.Y_AXIS));
        masterContent.setBackground(ColorScheme.DARK_GRAY_COLOR);
        masterContent.setBorder(new EmptyBorder(4, 4, 4, 4));

        // 1. Back Button
        JButton backBtn = new JButton("< Back");
        backBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        backBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        backBtn.setForeground(Color.WHITE);
        backBtn.setFont(FontManager.getRunescapeSmallFont());
        backBtn.setFocusPainted(false);
        backBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        backBtn.setMargin(new Insets(1, 4, 1, 4));
        backBtn.setToolTipText("Back to Bestiary");
        backBtn.addActionListener(e -> {
            if (onBackToDirectory != null) onBackToDirectory.run();
        });
        masterContent.add(backBtn);
        masterContent.add(Box.createRigidArea(new Dimension(0, 3)));

        // 2. Compact Native Hero Header Card (Responsive, zero clipping)
        JPanel heroWrapper = new JPanel(new BorderLayout());
        heroWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        heroWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel heroCard = new JPanel();
        heroCard.setLayout(new BoxLayout(heroCard, BoxLayout.Y_AXIS));
        heroCard.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        heroCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CopilotPalette.CARD_BORDER, 1),
            new EmptyBorder(4, 6, 4, 6)
        ));
        heroCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Top title row: Monster Name (CENTER) + Combat Level (EAST, compact width, zero clipping)
        JPanel heroTitleRow = new JPanel(new BorderLayout(3, 0));
        heroTitleRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        heroTitleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setForeground(TITLE_COLOR);
        heroTitleRow.add(titleLabel, BorderLayout.CENTER);

        combatLevelLabel.setFont(FontManager.getRunescapeBoldFont());
        combatLevelLabel.setForeground(COMBAT_LVL_COLOR);
        combatLevelLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        // Wide enough for "Lvl 1563" (Sol Heredit) in the bold RS font.
        combatLevelLabel.setPreferredSize(new Dimension(70, 18));
        combatLevelLabel.setMinimumSize(new Dimension(70, 18));
        heroTitleRow.add(combatLevelLabel, BorderLayout.EAST);
        heroCard.add(heroTitleRow);
        heroCard.add(Box.createRigidArea(new Dimension(0, 1)));

        // Stats row: HP: X • Max Hit: Y • Style: Z
        combatStatsLabel.setFont(FontManager.getRunescapeSmallFont());
        combatStatsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        combatStatsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        heroCard.add(combatStatsLabel);
        heroCard.add(Box.createRigidArea(new Dimension(0, 1)));

        // Badges: Crisp native JLabels across 3 clean, non-overflowing rows
        badgeContainer.setLayout(new BoxLayout(badgeContainer, BoxLayout.Y_AXIS));
        badgeContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        badgeContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        badgeRow1.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        badgeRow1.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeContainer.add(badgeRow1);

        badgeRow2.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        badgeRow2.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeContainer.add(badgeRow2);

        badgeRow3.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        badgeRow3.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeContainer.add(badgeRow3);

        heroCard.add(badgeContainer);

        heroWrapper.add(heroCard, BorderLayout.NORTH);
        masterContent.add(heroWrapper);
        masterContent.add(Box.createRigidArea(new Dimension(0, 3)));

        // 3. Clean Action Button Row: [Map] [Wiki] (Responsive grid, zero right clipping)
        JPanel actionRow = new JPanel(new GridLayout(1, 2, 2, 0));
        actionRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionRow.setPreferredSize(new Dimension(0, 20));
        actionRow.setMinimumSize(new Dimension(0, 20));
        actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JButton focusPrimaryBtn = new JButton("Map");
        focusPrimaryBtn.setFont(FontManager.getRunescapeSmallFont());
        focusPrimaryBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        focusPrimaryBtn.setForeground(FOCUS_BLUE);
        focusPrimaryBtn.setFocusPainted(false);
        focusPrimaryBtn.setMargin(new Insets(0, 1, 0, 1));
        focusPrimaryBtn.setToolTipText("Show this monster's primary spawn location on the World Map");
        focusPrimaryBtn.addActionListener(e -> {
            if (currentMonster != null && currentMonster.hasSpawnZones() && onFocusSpawnZone != null)
            {
                onFocusSpawnZone.accept(currentMonster, currentMonster.getSpawnZones().get(0));
            }
        });
        actionRow.add(focusPrimaryBtn);

        JButton wikiBtn = new JButton("Wiki");
        wikiBtn.setFont(FontManager.getRunescapeSmallFont());
        wikiBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        wikiBtn.setForeground(TITLE_COLOR);
        wikiBtn.setFocusPainted(false);
        wikiBtn.setMargin(new Insets(0, 1, 0, 1));
        wikiBtn.setToolTipText("Open the OSRS Wiki page for this monster in browser");
        wikiBtn.addActionListener(e -> {
            if (currentMonster != null)
            {
                String url = currentMonster.getWikiUrl();
                if (url != null && !url.isEmpty())
                {
                    LinkBrowser.browse(url);
                }
                else
                {
                    LinkBrowser.browse("https://oldschool.runescape.wiki/w/" + urlEncode(currentMonster.getName()));
                }
            }
        });
        actionRow.add(wikiBtn);

        masterContent.add(actionRow);
        masterContent.add(Box.createRigidArea(new Dimension(0, 4)));

        // 4. Combat & Defensive Weaknesses Card
        weaknessPanel.setLayout(new BoxLayout(weaknessPanel, BoxLayout.Y_AXIS));
        weaknessPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        weaknessPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CopilotPalette.CARD_BORDER, 1),
            new EmptyBorder(4, 6, 4, 6)
        ));
        weaknessPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        weaknessTextLabel.setFont(FontManager.getRunescapeSmallFont());
        weaknessTextLabel.setForeground(WEAKNESS_GREEN);
        weaknessTextLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        weaknessPanel.add(weaknessTextLabel);

        defenceStatsLabel.setFont(FontManager.getRunescapeSmallFont());
        defenceStatsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        defenceStatsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        weaknessPanel.add(defenceStatsLabel);

        defenceStatsLine2.setFont(FontManager.getRunescapeSmallFont());
        defenceStatsLine2.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        defenceStatsLine2.setAlignmentX(Component.LEFT_ALIGNMENT);
        weaknessPanel.add(defenceStatsLine2);

        weaknessExtraLabel.setFont(FontManager.getRunescapeSmallFont());
        weaknessExtraLabel.setForeground(new Color(255, 202, 40));
        weaknessExtraLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        weaknessPanel.add(weaknessExtraLabel);

        masterContent.add(weaknessPanel);
        masterContent.add(Box.createRigidArea(new Dimension(0, 5)));

        // 5. Spawn Locations & Zones Section
        JLabel spawnsTitle = new JLabel("Spawn Locations & Zones");
        spawnsTitle.setFont(FontManager.getRunescapeBoldFont());
        spawnsTitle.setForeground(FOCUS_BLUE);
        spawnsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        masterContent.add(spawnsTitle);
        masterContent.add(Box.createRigidArea(new Dimension(0, 3)));

        spawnsContainer.setLayout(new BoxLayout(spawnsContainer, BoxLayout.Y_AXIS));
        spawnsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        spawnsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        masterContent.add(spawnsContainer);
        masterContent.add(Box.createRigidArea(new Dimension(0, 5)));

        // 6. Drop Table / Loot Lookup Section
        JLabel dropsTitle = new JLabel("Drop Table & Loot Lookup");
        dropsTitle.setFont(FontManager.getRunescapeBoldFont());
        dropsTitle.setForeground(TITLE_COLOR);
        dropsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        masterContent.add(dropsTitle);
        masterContent.add(Box.createRigidArea(new Dimension(0, 3)));

        // Drop filter search bar + sort + table toggle
        dropSearchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        dropSearchField.setForeground(Color.WHITE);
        dropSearchField.setCaretColor(Color.WHITE);
        dropSearchField.setFont(FontManager.getRunescapeFont());
        dropSearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        dropSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        dropSearchField.setToolTipText("Filter drops by item name...");
        dropSearchField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyReleased(KeyEvent e)
            {
                // Debounced: a keystroke restarts a one-shot timer instead of rebuilding the whole
                // drop tree synchronously. A fresh filter always starts from the first page.
                dropRenderCap = DROP_RENDER_LIMIT;
                dropFilterDebounce.restart();
            }
        });
        masterContent.add(dropSearchField);
        masterContent.add(Box.createRigidArea(new Dimension(0, 3)));

        JPanel dropControlsRow = new JPanel(new BorderLayout(4, 0));
        dropControlsRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        dropControlsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        dropControlsRow.setPreferredSize(new Dimension(0, 22));
        dropControlsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        dropSortDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        dropSortDropdown.setForeground(Color.WHITE);
        dropSortDropdown.setFont(FontManager.getRunescapeSmallFont());
        dropSortDropdown.addItem("Rarity (Common)");
        dropSortDropdown.addItem("Rarity (Rare)");
        dropSortDropdown.addItem("Name (A-Z)");
        dropSortDropdown.addActionListener(e -> {
            dropRenderCap = DROP_RENDER_LIMIT;
            rebuildDrops();
        });
        dropControlsRow.add(dropSortDropdown, BorderLayout.CENTER);

        viewToggleBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        viewToggleBtn.setForeground(Color.WHITE);
        viewToggleBtn.setFont(FontManager.getRunescapeSmallFont());
        viewToggleBtn.setFocusPainted(false);
        viewToggleBtn.setPreferredSize(new Dimension(50, 22));
        viewToggleBtn.setMinimumSize(new Dimension(50, 22));
        viewToggleBtn.setMaximumSize(new Dimension(50, 22));
        viewToggleBtn.setMargin(new Insets(1, 2, 1, 2));
        viewToggleBtn.addActionListener(e -> {
            isTableView = !isTableView;
            viewToggleBtn.setText(isTableView ? "Cards" : "Table");
            dropRenderCap = DROP_RENDER_LIMIT;
            rebuildDrops();
        });
        dropControlsRow.add(viewToggleBtn, BorderLayout.EAST);
        masterContent.add(dropControlsRow);
        masterContent.add(Box.createRigidArea(new Dimension(0, 3)));

        dropCountLabel.setFont(FontManager.getRunescapeSmallFont());
        dropCountLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        dropCountLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        masterContent.add(dropCountLabel);
        masterContent.add(Box.createRigidArea(new Dimension(0, 3)));

        // Setup Drop Table and Drop Cards containers directly inside masterContent (no nested scrollpanes!)
        dropsTableContainer.setLayout(new BoxLayout(dropsTableContainer, BoxLayout.Y_AXIS));
        dropsTableContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        dropsTableContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        dropsCardsContainer.setLayout(new BoxLayout(dropsCardsContainer, BoxLayout.Y_AXIS));
        dropsCardsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        dropsCardsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        dropsContentHolder.setLayout(new BorderLayout());
        dropsContentHolder.setBackground(ColorScheme.DARK_GRAY_COLOR);
        dropsContentHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
        dropsContentHolder.add(dropsCardsContainer, BorderLayout.CENTER);
        masterContent.add(dropsContentHolder);

        // Single Unified Master Scroll Pane wrapper for the ENTIRE view
        JPanel listWrapper = new ScrollableContentPanel();
        listWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listWrapper.add(masterContent, BorderLayout.NORTH);

        masterScrollPane = new JScrollPane(listWrapper);
        masterScrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        masterScrollPane.setBorder(null);
        masterScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        masterScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        masterScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        add(masterScrollPane, BorderLayout.CENTER);
    }

    public void setMonster(Monster monster)
    {
        this.currentMonster = monster;
        if (monster == null)
        {
            return;
        }

        // Title and Combat Level
        titleLabel.setText(monster.getName());
        int lvl = monster.getCombatLevel();
        combatLevelLabel.setText(lvl > 0 ? "Lvl " + lvl : "");

        // Stats: HP: X • Max Hit: Y • Style: Z
        int hp = monster.getHitpoints();
        int maxHit = monster.getMaxHit();
        String style = monster.getAttackType();

        StringBuilder statsSb = new StringBuilder();
        if (hp > 0) statsSb.append("HP: ").append(hp);
        if (maxHit > 0)
        {
            if (statsSb.length() > 0) statsSb.append(" • ");
            statsSb.append("Max Hit: ").append(maxHit);
        }
        if (style != null && !style.isEmpty())
        {
            if (statsSb.length() > 0) statsSb.append(" • ");
            statsSb.append("Style: ").append(style);
        }
        combatStatsLabel.setText(statsSb.length() > 0 ? statsSb.toString() : "Stats: not recorded");

        // Native Badges (Crisp, anti-aliased, zero clipping across 3 neat rows)
        badgeRow1.removeAll();
        badgeRow2.removeAll();
        badgeRow3.removeAll();
        Set<String> addedBadges = new HashSet<>();

        // Row 1: Membership & Slayer Level / Category
        if (monster.isMembers())
        {
            badgeRow1.add(createBadgeLabel("[Members]", FOCUS_BLUE));
            addedBadges.add("members");
        }
        else
        {
            badgeRow1.add(createBadgeLabel("[F2P]", ColorScheme.LIGHT_GRAY_COLOR));
            addedBadges.add("f2p");
        }

        if (monster.getSlayerLevel() > 1)
        {
            badgeRow1.add(createBadgeLabel("[Slayer " + monster.getSlayerLevel() + "]", SLAYER_PURPLE));
            addedBadges.add("slayer");
        }
        else if (monster.getCategory() != null && !monster.getCategory().isEmpty())
        {
            String cat = monster.getCategory().trim();
            if (!addedBadges.contains(cat.toLowerCase(Locale.ROOT)) && !"standard".equalsIgnoreCase(cat))
            {
                badgeRow1.add(createBadgeLabel("[" + cat + "]", TITLE_COLOR));
                addedBadges.add(cat.toLowerCase(Locale.ROOT));
            }
        }

        // Row 2: Combat Traits
        if (monster.isAggressive())
        {
            badgeRow2.add(createBadgeLabel("[Aggressive]", new Color(252, 165, 165)));
        }
        if (monster.isPoisonous())
        {
            badgeRow2.add(createBadgeLabel("[Poisonous]", new Color(134, 239, 172)));
        }
        if (monster.isImmuneToPoison())
        {
            badgeRow2.add(createBadgeLabel("[Poison Immune]", new Color(216, 180, 254)));
        }

        // Row 3 for Quest / Encounter Requirements
        if (monster.hasQuestRequirement())
        {
            String q = monster.getQuestRequirement().trim();
            String questText = q.toLowerCase(Locale.ROOT).startsWith("quest:") ? q : "Quest: " + q;
            badgeRow3.add(createBadgeLabel("[" + questText + "]", QUEST_PURPLE));
        }
        if (!monster.hasSpawnZones() && monster.hasEncounterType())
        {
            String badgeText = getBadgeTextForEncounter(monster.getEncounterType());
            if (!addedBadges.contains(badgeText.toLowerCase(Locale.ROOT)))
            {
                Color encColor = getBadgeColorForEncounter(monster.getEncounterType());
                badgeRow3.add(createBadgeLabel("[" + badgeText + "]", encColor));
                addedBadges.add(badgeText.toLowerCase(Locale.ROOT));
            }
        }

        badgeRow1.setVisible(badgeRow1.getComponentCount() > 0);
        badgeRow2.setVisible(badgeRow2.getComponentCount() > 0);
        badgeRow3.setVisible(badgeRow3.getComponentCount() > 0);

        // Weakness & Defence
        String weakness = monster.getWeakness();
        weaknessTextLabel.setText(wrapHtml("Elemental weakness: "
            + (weakness != null && !weakness.isEmpty() ? weakness : "none")));

        // All five bonuses at exactly 0 is, in this dataset, a not-yet-scraped monster rather than a
        // genuine no-bonus one - show "not recorded" instead of asserting a wall of +0s as fact.
        boolean defenceRecorded = (monster.getDefenceStab() | monster.getDefenceSlash()
            | monster.getDefenceCrush() | monster.getDefenceMagic() | monster.getDefenceRanged()) != 0;
        if (defenceRecorded)
        {
            String defText1 = String.format("Stab: %+d  Slash: %+d  Crush: %+d",
                monster.getDefenceStab(), monster.getDefenceSlash(), monster.getDefenceCrush());
            String defText2 = String.format("Magic: %+d  Ranged: %+d",
                monster.getDefenceMagic(), monster.getDefenceRanged());
            defenceStatsLabel.setText(wrapHtml(defText1));
            defenceStatsLine2.setText(wrapHtml(defText2));
            defenceStatsLine2.setVisible(true);
        }
        else
        {
            defenceStatsLabel.setText(wrapHtml("Defence bonuses: not recorded"));
            defenceStatsLine2.setText("");
            defenceStatsLine2.setVisible(false);
        }

        String extra = weaknessGuidance(monster);
        weaknessExtraLabel.setText(extra.isEmpty() ? "" : wrapHtml(extra));
        weaknessExtraLabel.setVisible(!extra.isEmpty());

        // Rebuild Spawns
        rebuildSpawns();

        // Fresh monster -> clear any leftover drop-view state from the previous one.
        dropRenderCap = DROP_RENDER_LIMIT;
        if (!dropSearchField.getText().isEmpty())
        {
            dropSearchField.setText("");
        }
        if (isTableView)
        {
            isTableView = false;
            viewToggleBtn.setText("Table");
        }
        if (dropSortDropdown.getSelectedIndex() != 0)
        {
            dropSortDropdown.setSelectedIndex(0); // fires its listener -> one rebuildDrops()
        }
        rebuildDrops();

        // Scroll the whole view back to the top.
        if (masterScrollPane != null)
        {
            SwingUtilities.invokeLater(() -> {
                masterScrollPane.getVerticalScrollBar().setValue(0);
                masterScrollPane.getHorizontalScrollBar().setValue(0);
            });
        }

        revalidate();
        repaint();
    }

    private JLabel createBadgeLabel(String text, Color color)
    {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FontManager.getRunescapeSmallFont());
        lbl.setForeground(color);
        return lbl;
    }

    private static String wrapHtml(String text)
    {
        return SidebarMetrics.htmlWrap(text);
    }

    /**
     * The practical "what should I hit it with" line, built from real data: the style its defence
     * bonuses are lowest against (when there's a clear one), plus any bane-weapon family that
     * applies from the monster's OSRS Wiki attributes.
     */
    private static String weaknessGuidance(Monster m)
    {
        java.util.List<String> parts = new java.util.ArrayList<>();

        int[] def = {m.getDefenceStab(), m.getDefenceSlash(), m.getDefenceCrush(),
            m.getDefenceMagic(), m.getDefenceRanged()};
        String[] label = {"Stab", "Slash", "Crush", "Magic", "Ranged"};
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int v : def)
        {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        // Only call it a "weakness" when there's a real spread - all-equal (often all-zero) means
        // no style is favoured and you should just use your best DPS.
        if (max - min >= 15)
        {
            StringBuilder low = new StringBuilder();
            for (int i = 0; i < def.length; i++)
            {
                if (def[i] == min)
                {
                    low.append(low.length() > 0 ? " / " : "").append(label[i]);
                }
            }
            parts.add("Lowest defence: " + low);
        }

        String bane = baneWeaponHint(m);
        if (bane != null)
        {
            parts.add(bane);
        }
        return String.join("  ·  ", parts);
    }

    private static String baneWeaponHint(Monster m)
    {
        if (m.hasAttribute("demon"))
        {
            return "Demonbane: Arclight / Emberlight";
        }
        if (m.hasAttribute("dragon"))
        {
            return "Dragonbane: Dragon hunter lance / crossbow";
        }
        if (m.hasAttribute("undead"))
        {
            return "Salve amulet applies";
        }
        if (m.hasAttribute("kalphite"))
        {
            return "Keris applies";
        }
        if (m.hasAttribute("leafy"))
        {
            return "Requires leaf-bladed / broad weapons";
        }
        if (m.hasAttribute("golem"))
        {
            return "Rock hammer / rock thrownhammer finishes it";
        }
        for (String v : new String[]{"vampyre", "vampyre2", "vampyre3", "vampyrelesser"})
        {
            if (m.hasAttribute(v))
            {
                return "Vampyrebane: Blisterwood / Ivandis flail";
            }
        }
        return null;
    }

    private String getBadgeTextForEncounter(String encounterType)
    {
        if (encounterType == null) return "Instanced Encounter";
        String encLower = encounterType.toLowerCase(Locale.ROOT);
        if (encLower.contains("theatre of blood")) return "Raids: ToB";
        if (encLower.contains("chambers of xeric")) return "Raids: CoX";
        if (encLower.contains("tombs of amascut")) return "Raids: ToA";
        if (encLower.contains("inferno")) return "Minigame: Inferno";
        if (encLower.contains("fight cave")) return "Minigame: Fight Caves";
        if (encLower.contains("pest control")) return "Minigame: Pest Control";
        if (encLower.contains("gauntlet")) return "Minigame: Gauntlet";
        if (encLower.contains("colosseum")) return "Minigame: Colosseum";
        if (encLower.contains("quest")) return "Quest Encounter";
        if (encLower.contains("minion") || encLower.contains("summon")) return "Boss Minion";
        return "Instanced Encounter";
    }

    private Color getBadgeColorForEncounter(String encounterType)
    {
        if (encounterType == null) return ColorScheme.LIGHT_GRAY_COLOR;
        String encLower = encounterType.toLowerCase(Locale.ROOT);
        if (encLower.contains("theatre of blood") || encLower.contains("chambers of xeric") || encLower.contains("tombs of amascut"))
        {
            return WILDY_RED; // soft red for raids
        }
        if (encLower.contains("inferno") || encLower.contains("fight cave") || encLower.contains("gauntlet") || encLower.contains("colosseum"))
        {
            return MULTI_ORANGE; // orange for combat minigames
        }
        if (encLower.contains("quest"))
        {
            return QUEST_PURPLE;
        }
        return TITLE_COLOR;
    }

    private void rebuildSpawns()
    {
        spawnsContainer.removeAll();
        if (currentMonster == null)
        {
            return;
        }

        List<MonsterSpawnZone> zones = currentMonster.getSpawnZones();
        if (zones != null && !zones.isEmpty())
        {
            for (MonsterSpawnZone zone : zones)
            {
                JPanel zoneCard = buildSpawnZoneCard(currentMonster, zone);
                spawnsContainer.add(zoneCard);
                spawnsContainer.add(Box.createRigidArea(new Dimension(0, 3)));
            }
        }
        else if (currentMonster.hasEncounterType())
        {
            JPanel encCard = new JPanel(new BorderLayout(4, 2));
            encCard.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            encCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CopilotPalette.CARD_BORDER, 1),
                new EmptyBorder(5, 6, 5, 6)
            ));
            encCard.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel encTypeLabel = new JLabel(wrapHtml(currentMonster.getEncounterType()));
            encTypeLabel.setFont(FontManager.getRunescapeBoldFont());
            encTypeLabel.setForeground(TITLE_COLOR);
            encCard.add(encTypeLabel, BorderLayout.NORTH);

            JLabel encNoteLabel = new JLabel(wrapHtml("This encounter is dynamically instanced or spawned during combat, so it does not have fixed open-world roaming pins."));
            encNoteLabel.setFont(FontManager.getRunescapeSmallFont());
            encNoteLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            encCard.add(encNoteLabel, BorderLayout.CENTER);

            spawnsContainer.add(encCard);
        }
        else
        {
            JLabel emptyLabel = new JLabel("No spawn zones registered");
            emptyLabel.setFont(FontManager.getRunescapeSmallFont());
            emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            spawnsContainer.add(emptyLabel);
        }
    }

    private JPanel buildSpawnZoneCard(Monster monster, MonsterSpawnZone zone)
    {
        JPanel card = new JPanel(new BorderLayout(4, 0));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CopilotPalette.CARD_BORDER, 1),
            new EmptyBorder(3, 5, 3, 5)
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        card.setPreferredSize(new Dimension(0, 36));

        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        textCol.setAlignmentX(Component.LEFT_ALIGNMENT);

        String zoneName = zone.getZoneName();
        if (zoneName == null || zoneName.isEmpty())
        {
            zoneName = zone.getLocationName() != null ? zone.getLocationName() : "Spawn Zone";
        }
        JLabel nameLabel = new JLabel(zoneName);
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(FOCUS_BLUE);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameLabel.setToolTipText(zoneName);
        textCol.add(nameLabel);

        JPanel detailsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        detailsRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        detailsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (zone.getWildernessLevel() > 0)
        {
            JLabel wildyLabel = new JLabel("Wildy " + zone.getWildernessLevel());
            wildyLabel.setFont(FontManager.getRunescapeSmallFont());
            wildyLabel.setForeground(WILDY_RED);
            detailsRow.add(wildyLabel);
        }
        if (zone.isMultiCombat())
        {
            JLabel multiLabel = new JLabel("Multi");
            multiLabel.setFont(FontManager.getRunescapeSmallFont());
            multiLabel.setForeground(MULTI_ORANGE);
            detailsRow.add(multiLabel);
        }
        if (zone.getSpawnCount() > 0)
        {
            JLabel countLabel = new JLabel(zone.getSpawnCount() + " spawns");
            countLabel.setFont(FontManager.getRunescapeSmallFont());
            countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            detailsRow.add(countLabel);
        }
        textCol.add(detailsRow);

        card.add(textCol, BorderLayout.CENTER);

        JButton focusBtn = new JButton("Map");
        focusBtn.setFont(FontManager.getRunescapeSmallFont());
        focusBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        focusBtn.setForeground(FOCUS_BLUE);
        focusBtn.setFocusPainted(false);
        focusBtn.setMargin(new Insets(0, 1, 0, 1));
        focusBtn.setPreferredSize(new Dimension(38, 20));
        focusBtn.setMinimumSize(new Dimension(38, 20));
        focusBtn.setMaximumSize(new Dimension(38, 20));
        focusBtn.setToolTipText("Center map on " + zoneName);
        focusBtn.addActionListener(e -> {
            if (onFocusSpawnZone != null)
            {
                onFocusSpawnZone.accept(monster, zone);
            }
        });
        card.add(focusBtn, BorderLayout.EAST);

        // Entire card click triggers map centering
        MouseAdapter clickAdapter = new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                card.setBackground(CopilotPalette.ROW_HOVER);
                textCol.setBackground(CopilotPalette.ROW_HOVER);
                detailsRow.setBackground(CopilotPalette.ROW_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                textCol.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                detailsRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }

            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (onFocusSpawnZone != null)
                {
                    onFocusSpawnZone.accept(monster, zone);
                }
            }
        };
        card.addMouseListener(clickAdapter);
        textCol.addMouseListener(clickAdapter);
        nameLabel.addMouseListener(clickAdapter);
        detailsRow.addMouseListener(clickAdapter);

        return card;
    }

    private void rebuildDrops()
    {
        dropsCardsContainer.removeAll();
        dropsTableContainer.removeAll();
        displayedDrops.clear();

        if (currentMonster == null || currentMonster.getDrops() == null || currentMonster.getDrops().isEmpty())
        {
            dropCountLabel.setText("No drops listed.");
            dropsContentHolder.removeAll();
            dropsContentHolder.revalidate();
            dropsContentHolder.repaint();
            return;
        }

        String query = dropSearchField.getText().trim().toLowerCase(Locale.ROOT);
        List<MonsterDrop> filtered = new ArrayList<>();
        for (MonsterDrop d : currentMonster.getDrops())
        {
            if (query.isEmpty() || (d.getName() != null && d.getName().toLowerCase(Locale.ROOT).contains(query)))
            {
                filtered.add(d);
            }
        }

        int sortIndex = dropSortDropdown.getSelectedIndex();
        Comparator<MonsterDrop> comparator = null;
        if (sortIndex == 0) // Common -> Rare
        {
            comparator = Comparator.comparingDouble(MonsterDrop::getRarity).reversed();
        }
        else if (sortIndex == 1) // Rare -> Common
        {
            comparator = Comparator.comparingDouble(MonsterDrop::getRarity);
        }
        else if (sortIndex == 2) // Name A->Z
        {
            comparator = Comparator.comparing(MonsterDrop::getName, String.CASE_INSENSITIVE_ORDER);
        }

        // Group drops by category
        Map<String, List<MonsterDrop>> categoryMap = new LinkedHashMap<>();
        for (String catName : DROP_CATEGORIES)
        {
            categoryMap.put(catName, new ArrayList<>());
        }

        for (MonsterDrop drop : filtered)
        {
            String cat = categorizeDrop(drop);
            categoryMap.computeIfAbsent(cat, k -> new ArrayList<>()).add(drop);
        }

        // Sort items within each category
        for (List<MonsterDrop> catDrops : categoryMap.values())
        {
            if (comparator != null)
            {
                catDrops.sort(comparator);
            }
        }

        int totalMatched = 0;
        for (String catName : DROP_CATEGORIES)
        {
            List<MonsterDrop> c = categoryMap.get(catName);
            if (c != null)
            {
                totalMatched += c.size();
            }
        }

        // Cap how many rows we build this pass (dropRenderCap). Trim per-category in
        // DROP_CATEGORIES order so the shown set stays grouped; a "Show more" row appends the rest.
        int budget = dropRenderCap;
        for (String catName : DROP_CATEGORIES)
        {
            List<MonsterDrop> catDrops = categoryMap.get(catName);
            if (catDrops == null || catDrops.isEmpty())
            {
                continue;
            }
            if (budget <= 0)
            {
                catDrops.clear();
                continue;
            }
            if (catDrops.size() > budget)
            {
                catDrops.subList(budget, catDrops.size()).clear();
            }
            budget -= catDrops.size();
            displayedDrops.addAll(catDrops);
        }
        int hiddenCount = Math.max(0, totalMatched - displayedDrops.size());

        dropCountLabel.setText(hiddenCount > 0
            ? "Showing " + displayedDrops.size() + " of " + totalMatched + " drops:"
            : "Showing " + displayedDrops.size() + " drop" + (displayedDrops.size() == 1 ? "" : "s") + ":");
        dropsContentHolder.removeAll();

        if (isTableView)
        {
            for (String catName : DROP_CATEGORIES)
            {
                List<MonsterDrop> dropsInCat = categoryMap.get(catName);
                if (dropsInCat == null || dropsInCat.isEmpty())
                {
                    continue;
                }

                dropsTableContainer.add(createCategoryHeaderBar(catName, dropsInCat.size()));
                dropsTableContainer.add(Box.createRigidArea(new Dimension(0, 2)));
                dropsTableContainer.add(buildCategoryTable(dropsInCat));
                dropsTableContainer.add(Box.createRigidArea(new Dimension(0, 4)));
            }

            if (hiddenCount > 0)
            {
                dropsTableContainer.add(buildShowMoreDropsRow(hiddenCount));
            }

            if (displayedDrops.isEmpty())
            {
                JLabel emptyLabel = new JLabel("No drops match '" + query + "'");
                emptyLabel.setFont(FontManager.getRunescapeSmallFont());
                emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                emptyLabel.setBorder(new EmptyBorder(6, 6, 6, 6));
                emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                dropsTableContainer.add(emptyLabel);
            }

            dropsContentHolder.add(dropsTableContainer, BorderLayout.CENTER);
        }
        else
        {
            for (String catName : DROP_CATEGORIES)
            {
                List<MonsterDrop> dropsInCat = categoryMap.get(catName);
                if (dropsInCat == null || dropsInCat.isEmpty())
                {
                    continue;
                }

                dropsCardsContainer.add(createCategoryHeaderBar(catName, dropsInCat.size()));
                dropsCardsContainer.add(Box.createRigidArea(new Dimension(0, 2)));

                for (MonsterDrop drop : dropsInCat)
                {
                    JPanel dropRow = buildDropCard(drop);
                    dropsCardsContainer.add(dropRow);
                    dropsCardsContainer.add(Box.createRigidArea(new Dimension(0, 2)));
                }

                dropsCardsContainer.add(Box.createRigidArea(new Dimension(0, 4)));
            }

            if (hiddenCount > 0)
            {
                dropsCardsContainer.add(buildShowMoreDropsRow(hiddenCount));
            }

            if (displayedDrops.isEmpty())
            {
                JLabel emptyLabel = new JLabel("No drops match '" + query + "'");
                emptyLabel.setFont(FontManager.getRunescapeSmallFont());
                emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                emptyLabel.setBorder(new EmptyBorder(6, 6, 6, 6));
                emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                dropsCardsContainer.add(emptyLabel);
            }

            dropsContentHolder.add(dropsCardsContainer, BorderLayout.CENTER);
        }

        dropsContentHolder.revalidate();
        dropsContentHolder.repaint();
    }

    private JPanel buildShowMoreDropsRow(int hiddenCount)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setBorder(new EmptyBorder(3, 6, 3, 6));

        int next = Math.min(hiddenCount, DROP_RENDER_LIMIT * 2);
        JButton more = new JButton("Show " + next + " more  (" + hiddenCount + " hidden)");
        more.setFont(FontManager.getRunescapeSmallFont());
        more.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        more.setForeground(Color.WHITE);
        more.setFocusPainted(false);
        more.addActionListener(e -> {
            dropRenderCap += DROP_RENDER_LIMIT * 2;
            rebuildDrops();
        });
        row.add(more, BorderLayout.CENTER);
        return row;
    }

    private JPanel createCategoryHeaderBar(String title, int count)
    {
        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setBackground(new Color(36, 36, 42));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        header.setPreferredSize(new Dimension(0, 22));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, TITLE_COLOR),
            new EmptyBorder(2, 6, 2, 6)
        ));

        JLabel label = new JLabel(title + " (" + count + ")");
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(TITLE_COLOR);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        header.add(label, BorderLayout.WEST);

        return header;
    }

    private JPanel buildCategoryTable(List<MonsterDrop> dropsInCat)
    {
        String[] colNames = {"Item", "Qty", "Rarity"};
        DefaultTableModel model = new DefaultTableModel(colNames, 0)
        {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        for (MonsterDrop drop : dropsInCat)
        {
            model.addRow(new Object[]{
                drop.getName(),
                drop.getQuantity(),
                formatMathematicalRarity(drop)
            });
        }

        JTable table = new JTable(model);
        table.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        table.setForeground(Color.WHITE);
        table.setFont(FontManager.getRunescapeSmallFont());
        table.setRowHeight(22);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.getColumnModel().getColumn(0).setPreferredWidth(95);
        table.getColumnModel().getColumn(1).setPreferredWidth(35);
        table.getColumnModel().getColumn(2).setPreferredWidth(60);

        // Resolve each row's item icon ONCE here, not inside the cell renderer (which runs on every
        // repaint / scroll / selection and would re-register an async listener each time).
        final javax.swing.Icon[] rowIcons = new javax.swing.Icon[dropsInCat.size()];
        for (int i = 0; i < dropsInCat.size(); i++)
        {
            MonsterDrop d = dropsInCat.get(i);
            if (itemManager != null && d.getItemId() > 0)
            {
                AsyncBufferedImage img = itemManager.getImage(d.getItemId());
                if (img != null)
                {
                    rowIcons[i] = new javax.swing.ImageIcon(img);
                    img.onLoaded(table::repaint);
                }
            }
        }

        DefaultTableCellRenderer itemRenderer = new DefaultTableCellRenderer()
        {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int col)
            {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                lbl.setForeground(Color.WHITE);
                lbl.setFont(FontManager.getRunescapeSmallFont());
                lbl.setBorder(new EmptyBorder(0, 4, 0, 2));
                lbl.setIcon(row >= 0 && row < rowIcons.length ? rowIcons[row] : null);
                return lbl;
            }
        };
        table.getColumnModel().getColumn(0).setCellRenderer(itemRenderer);

        DefaultTableCellRenderer qtyRenderer = new DefaultTableCellRenderer();
        qtyRenderer.setHorizontalAlignment(JLabel.CENTER);
        qtyRenderer.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        table.getColumnModel().getColumn(1).setCellRenderer(qtyRenderer);

        DefaultTableCellRenderer rarityRenderer = new DefaultTableCellRenderer()
        {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int col)
            {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                if (row >= 0 && row < dropsInCat.size())
                {
                    MonsterDrop drop = dropsInCat.get(row);
                    c.setForeground(isSelected ? Color.WHITE : RarityFormat.perKillColor(drop));
                }
                setHorizontalAlignment(JLabel.CENTER);
                return c;
            }
        };
        table.getColumnModel().getColumn(2).setCellRenderer(rarityRenderer);

        table.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                handleCategoryTableClick(e, table, dropsInCat);
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                handleCategoryTableClick(e, table, dropsInCat);
            }

            @Override
            public void mouseClicked(MouseEvent e)
            {
                handleCategoryTableClick(e, table, dropsInCat);
            }
        });

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        tablePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // The table sits in a BoxLayout with no scroll pane of its own, so its JTableHeader is
        // never shown unless we add it explicitly - without this the three columns are unlabeled.
        javax.swing.table.JTableHeader tableHeader = table.getTableHeader();
        tableHeader.setReorderingAllowed(false);
        tableHeader.setResizingAllowed(false);
        tableHeader.setBackground(new Color(36, 36, 42));
        tableHeader.setForeground(TITLE_COLOR);
        tableHeader.setFont(FontManager.getRunescapeSmallFont());
        tablePanel.add(tableHeader, BorderLayout.NORTH);
        tablePanel.add(table, BorderLayout.CENTER);

        int headerH = Math.max(16, tableHeader.getPreferredSize().height);
        int panelH = headerH + dropsInCat.size() * table.getRowHeight() + 2;
        tablePanel.setPreferredSize(new Dimension(0, panelH));
        tablePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panelH));
        return tablePanel;
    }

    private void handleCategoryTableClick(MouseEvent e, JTable table, List<MonsterDrop> drops)
    {
        int row = table.rowAtPoint(e.getPoint());
        if (row >= 0 && row < drops.size())
        {
            table.setRowSelectionInterval(row, row);
            MonsterDrop drop = drops.get(row);
            if (e.isPopupTrigger())
            {
                JPopupMenu popup = createDropContextMenu(drop);
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
            else if (e.getID() == MouseEvent.MOUSE_CLICKED && e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1)
            {
                openWikiPage(drop.getName());
            }
        }
    }

    private JPanel buildDropCard(MonsterDrop drop)
    {
        JPanel card = new JPanel(new BorderLayout(6, 0));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        card.setPreferredSize(new Dimension(0, 38));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
            new EmptyBorder(2, 4, 2, 4)
        ));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Color rarityColor = RarityFormat.perKillColor(drop);
        String formattedRarity = RarityFormat.perKill(drop);
        // Multi-roll drops also show the wiki per-roll figure as a small secondary note - never the
        // headline, never driving the colour.
        String perRollNote = RarityFormat.perRollNote(drop);
        card.setToolTipText("<html><b>" + drop.getName() + "</b><br>Qty: " + drop.getQuantity() + " | Rarity: <span style='color: " + toHex(rarityColor) + ";'>" + formattedRarity + "</span>" + perRollNote + "<br><span style='color: #90caf9;'>Click for Wiki</span></html>");

        // Icon 32x32
        JPanel iconContainer = new JPanel(new BorderLayout());
        iconContainer.setOpaque(false);
        iconContainer.setPreferredSize(new Dimension(32, 32));
        iconContainer.setMinimumSize(new Dimension(32, 32));
        iconContainer.setMaximumSize(new Dimension(32, 32));

        JLabel iconLabel = new JLabel();
        iconLabel.setHorizontalAlignment(JLabel.CENTER);
        iconLabel.setVerticalAlignment(JLabel.CENTER);
        if (itemManager != null && drop.getItemId() > 0)
        {
            AsyncBufferedImage img = itemManager.getImage(drop.getItemId());
            if (img != null)
            {
                img.addTo(iconLabel);
            }
        }
        iconContainer.add(iconLabel, BorderLayout.CENTER);
        card.add(iconContainer, BorderLayout.WEST);

        // Details Column (strictly left-aligned)
        JPanel detailsCol = new JPanel();
        detailsCol.setLayout(new BoxLayout(detailsCol, BoxLayout.Y_AXIS));
        detailsCol.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        detailsCol.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(drop.getName());
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailsCol.add(nameLabel);

        JLabel statsLabel = new JLabel("Qty: " + drop.getQuantity() + " • " + formattedRarity + perRollNote);
        statsLabel.setFont(FontManager.getRunescapeSmallFont());
        statsLabel.setForeground(rarityColor);
        statsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailsCol.add(statsLabel);

        card.add(detailsCol, BorderLayout.CENTER);

        JPopupMenu dropPopup = createDropContextMenu(drop);

        MouseAdapter dropCardListener = new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                card.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
                detailsCol.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                detailsCol.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }

            @Override
            public void mousePressed(MouseEvent e)
            {
                if (e.isPopupTrigger()) dropPopup.show(card, e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (e.isPopupTrigger()) dropPopup.show(card, e.getX(), e.getY());
            }

            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getButton() == MouseEvent.BUTTON1)
                {
                    openWikiPage(drop.getName());
                }
            }
        };

        card.addMouseListener(dropCardListener);
        detailsCol.addMouseListener(dropCardListener);
        nameLabel.addMouseListener(dropCardListener);
        statsLabel.addMouseListener(dropCardListener);

        return card;
    }

    private JPopupMenu createDropContextMenu(MonsterDrop drop)
    {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem wikiItem = new JMenuItem("Open Wiki for '" + drop.getName() + "'");
        wikiItem.addActionListener(e -> openWikiPage(drop.getName()));
        popup.add(wikiItem);

        JMenuItem copyNameItem = new JMenuItem("Copy Name");
        copyNameItem.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(drop.getName()), null);
        });
        popup.add(copyNameItem);

        if (cartManager != null && drop.getItemId() > 0)
        {
            JMenuItem addToCartItem = new JMenuItem("Add to Shopping Cart");
            addToCartItem.addActionListener(e -> {
                cartManager.addItem(CartItem.builder()
                    .itemId(drop.getItemId())
                    .itemName(drop.getName())
                    .quantity(1)
                    .unitPrice(0)
                    .shopName(currentMonster != null ? currentMonster.getName() : "Monster Drop")
                    .townName("Monster Drop")
                    .build());
            });
            popup.add(addToCartItem);
        }

        return popup;
    }

    public static String categorizeDrop(MonsterDrop drop)
    {
        if (drop == null)
        {
            return "Other";
        }

        if (drop.getRarity() >= 1.0)
        {
            return "100% Drops";
        }

        String rawCat = drop.getCategory();
        if (rawCat != null && !rawCat.isEmpty())
        {
            String catL = rawCat.toLowerCase(Locale.ROOT);
            if (catL.contains("100%")) return "100% Drops";
            if (catL.contains("weapon") || catL.contains("armour") || catL.contains("armor") || catL.contains("equipment")) return "Weapons and Armour";
            if (catL.contains("rune") || catL.contains("ammunition") || catL.contains("arrow") || catL.contains("bolt")) return "Runes and Ammunition";
            if (catL.contains("seed")) return "Seeds";
            if (catL.contains("herb")) return "Herbs";
            if (catL.contains("tertiary") || catL.contains("unique") || catL.contains("special") || catL.contains("pet")) return "Tertiary / Uniques";
        }

        String name = drop.getName() != null ? drop.getName().toLowerCase(Locale.ROOT) : "";

        if (name.contains("clue scroll") || name.contains("casket") || name.contains("curved bone")
            || name.contains("long bone") || name.contains("ensouled") || name.contains("head")
            || name.contains("pet") || name.contains("jar of") || name.contains("champion scroll")
            || name.contains("visage") || name.contains("scythe of") || name.contains("twisted bow")
            || name.contains("tumeken") || name.contains("hilt") || name.contains("vestige")
            || name.contains("jaw") || name.contains("black mask") || name.contains("warhammer")
            || name.contains("fang") || name.contains("whip") || name.contains("trident"))
        {
            return "Tertiary / Uniques";
        }

        if (name.contains("seed"))
        {
            return "Seeds";
        }

        if (name.contains("sword") || name.contains("dagger") || name.contains("scimitar")
            || name.contains("axe") || name.contains("mace") || name.contains("spear")
            || name.contains("battleaxe") || name.contains("warhammer") || name.contains("halberd")
            || name.contains("bow") || name.contains("crossbow") || name.contains("staff")
            || name.contains("shield") || name.contains("helm") || name.contains("platebody")
            || name.contains("platelegs") || name.contains("plateskirt") || name.contains("chainbody")
            || name.contains("boots") || name.contains("gloves") || name.contains("coif")
            || name.contains("chaps") || name.contains("body") || name.contains("legs")
            || name.contains("robe") || name.contains("hat") || name.contains("top")
            || name.contains("bottom") || name.contains("kiteshield") || name.contains("sq shield"))
        {
            return "Weapons and Armour";
        }

        if (name.contains("rune") || name.contains("arrow") || name.contains("bolt")
            || name.contains("dart") || name.contains("javelin") || name.contains("knife"))
        {
            return "Runes and Ammunition";
        }

        if (name.contains("herb") || name.contains("ranarr") || name.contains("snapdragon")
            || name.contains("torstol") || name.contains("avantoe") || name.contains("kwuarm")
            || name.contains("cadantine") || name.contains("lantadyme") || name.contains("irit")
            || name.contains("toadflax") || name.contains("tarromin") || name.contains("harralander")
            || name.contains("marrentill") || name.contains("guam") || name.contains("grimy")
            || name.contains("clean "))
        {
            return "Herbs";
        }

        return "Other";
    }

    /**
     * The headline drop rarity string. Delegates to the shared {@link RarityFormat#perKill} so it
     * quotes the per-kill figure (rolls folded in), digit-grouped, "Varies" for an unknown rate.
     *
     * <p>Retained as a public static method: {@code loot/ui/LootTabView} still calls it and is
     * migrated in a later wave. New code should call {@link RarityFormat#perKill} directly.
     */
    public static String formatMathematicalRarity(MonsterDrop drop)
    {
        return RarityFormat.perKill(drop);
    }

    /**
     * Tier colour for a raw probability. Delegates to {@link RarityFormat#colorForChance}; an
     * unknown rate ({@code <= 0}) is NEUTRAL, not the rarest tier. Prefer
     * {@link RarityFormat#perKillColor(MonsterDrop)} when you have the drop, so multi-roll rows
     * colour by their per-kill chance.
     */
    public static Color getRarityColor(double rarity)
    {
        return RarityFormat.colorForChance(rarity);
    }

    private String toHex(Color c)
    {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private void openWikiPage(String name)
    {
        if (name != null && !name.isEmpty())
        {
            LinkBrowser.browse("https://oldschool.runescape.wiki/w/" + urlEncode(name));
        }
    }

    private String urlEncode(String text)
    {
        try
        {
            return URLEncoder.encode(text.replace(" ", "_"), StandardCharsets.UTF_8.toString());
        }
        catch (Exception e)
        {
            return text.replace(" ", "_");
        }
    }

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
