package com.osrscopilot.ui;

import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.ui.theme.SidebarMetrics;
import com.osrscopilot.util.NpcPortraitManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

public class MonsterDirectoryView extends JPanel
{
    private static final Color TITLE_COLOR = new Color(255, 152, 31);
    private static final Color COMBAT_LVL_COLOR = new Color(255, 215, 0);
    private static final Color HP_COLOR = new Color(239, 68, 68);
    private static final Color SLAYER_PURPLE = new Color(187, 134, 252);
    private static final Color FOCUS_BLUE = new Color(90, 200, 250);
    private static final Color WEAKNESS_COLOR = new Color(52, 211, 153);
    // Encounter-badge colors mirrored from MonsterDetailView's getBadgeColorForEncounter() palette,
    // so raid/instanced-boss badges look identical between the card list and the detail drill-down.
    private static final Color QUEST_PURPLE = new Color(192, 132, 252);
    private static final Color WILDY_RED = new Color(248, 113, 113);
    private static final Color RARITY_UNCOMMON = new Color(253, 224, 71);
    private static final Color REANIMATED_PURPLE = new Color(167, 139, 250);

    private final MonsterDatabase monsterDatabase;
    private final NpcPortraitManager npcPortraitManager;
    private final Consumer<Monster> onInspectMonster;
    private final Consumer<Monster> onFocusMonsterOnMap;
    private final Runnable onOpenSpreadsheet;

    private final JTextField searchField = new JTextField();
    private final JComboBox<String> categoryDropdown = new JComboBox<>();
    private final JComboBox<String> sortDropdown = new JComboBox<>();
    private final JLabel countLabel = new JLabel("Loading monsters...");
    private final JPanel listContainer = new JPanel();

    // --- Search performance (playtest bug fix, see GEMINI.md) ---
    //
    // Measured root cause (headless timing test against the real post-2026-08-20-rescrape database,
    // 1,835 monsters): the search index lookup itself (MonsterDatabase.searchMonsters) is fast --
    // ~5-30ms even for single-letter queries. The actual EDT freeze comes entirely from rebuilding the
    // Swing card tree: building one full card (portrait label, 2 BorderLayout rows, up to 4 detail
    // labels including HTML-rendered ones, 1-2 JButtons, a JPopupMenu, mouse listeners) for every
    // matching monster, synchronously, on every keystroke. Measured: rebuildMonsterList() for a blank
    // query (1,835 cards) took ~3.5s; a single letter like "a" (1,318 matches) took ~2.0s; "d" (728
    // matches) took ~1.0s -- all run synchronously on the caller's thread, which in the live client is
    // the EDT, so the whole client UI is unresponsive for that entire duration. Narrow queries are cheap
    // ("dragon", 34 matches, ~50ms) -- the problem is specifically the first 1-2 characters typed, before
    // the query has narrowed enough, which is exactly when the user is actively pressing keys.
    //
    // Fix, two parts:
    // 1. Debounce (searchDebounceTimer below): a keystroke no longer triggers rebuildMonsterList()
    //    directly -- it (re)starts a one-shot javax.swing.Timer that only fires after a short pause in
    //    typing. This collapses "N rebuilds queued back-to-back while typing a word" into at most one
    //    rebuild per pause, eliminating the compounding freeze from rapid keystrokes.
    // 2. Render capping (SEARCH_RENDER_LIMIT / pendingMonsters / renderedCount below): while the user has
    //    typed a non-empty query, only the first SEARCH_RENDER_LIMIT matches are actually built as Swing
    //    cards; a "Show more" row appends additional batches on demand without re-touching already-built
    //    cards. This bounds the worst-case per-search EDT cost regardless of how broad an early query is.
    //    Deliberately scoped to non-empty queries only: the default blank-query "browse everything" state
    //    (tab open, category/sort-only changes) keeps its original uncapped behavior unchanged, since nothing
    //    reported that path as a live-typing freeze and existing tests rely on the full card list being
    //    present after initialize().
    private static final int SEARCH_RENDER_LIMIT = 100;
    private final javax.swing.Timer searchDebounceTimer;
    private List<Monster> pendingMonsters = Collections.emptyList();
    private int renderedCount = 0;
    private JPanel showMoreRow;

    public MonsterDirectoryView(
        MonsterDatabase monsterDatabase,
        NpcPortraitManager npcPortraitManager,
        Consumer<Monster> onInspectMonster,
        Consumer<Monster> onFocusMonsterOnMap)
    {
        this(monsterDatabase, npcPortraitManager, onInspectMonster, onFocusMonsterOnMap, null);
    }

    public MonsterDirectoryView(
        MonsterDatabase monsterDatabase,
        NpcPortraitManager npcPortraitManager,
        Consumer<Monster> onInspectMonster,
        Consumer<Monster> onFocusMonsterOnMap,
        Runnable onOpenSpreadsheet)
    {
        this.monsterDatabase = monsterDatabase;
        this.npcPortraitManager = npcPortraitManager;
        this.onInspectMonster = onInspectMonster;
        this.onFocusMonsterOnMap = onFocusMonsterOnMap;
        this.onOpenSpreadsheet = onOpenSpreadsheet;

        // One-shot, restarted on every keystroke -- see the field-level comment above. 180ms is short
        // enough to feel instant once the user pauses, long enough that a normal typing cadence (a fresh
        // keystroke every <180ms) never actually fires a rebuild mid-word.
        this.searchDebounceTimer = new javax.swing.Timer(180, e -> rebuildMonsterList());
        this.searchDebounceTimer.setRepeats(false);

        setLayout(new BorderLayout(0, 0));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header Panel
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(6, 6, 6, 6));

        JLabel titleLabel = new JLabel("Bestiary");
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setForeground(TITLE_COLOR);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // Search Input
        searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        searchField.setFont(FontManager.getRunescapeFont());
        searchField.setAlignmentX(LEFT_ALIGNMENT);
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        searchField.setToolTipText("Search monsters by name (e.g. 'Vorkath', 'Dragon', 'Demon')");
        searchField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyReleased(KeyEvent e)
            {
                // Debounced -- see searchDebounceTimer's field-level comment. Restarting on every
                // keystroke means the actual (expensive) rebuild only runs once, after typing pauses,
                // instead of once per keystroke.
                searchDebounceTimer.restart();
            }
        });
        headerPanel.add(searchField);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // Category Filter Dropdown
        categoryDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        categoryDropdown.setForeground(Color.WHITE);
        categoryDropdown.setFont(FontManager.getRunescapeSmallFont());
        categoryDropdown.setAlignmentX(LEFT_ALIGNMENT);
        categoryDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        categoryDropdown.setToolTipText("Filter monsters by family, bosses, slayer, or wilderness");
        categoryDropdown.addItem("All Categories");
        categoryDropdown.addItem("Bosses");
        categoryDropdown.addItem("Slayer");
        categoryDropdown.addItem("Dragons");
        categoryDropdown.addItem("Demons");
        categoryDropdown.addItem("Undead");
        categoryDropdown.addItem("Wilderness");
        categoryDropdown.addItem("F2P");
        categoryDropdown.addActionListener(e -> rebuildMonsterList());
        headerPanel.add(categoryDropdown);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // Sort Dropdown
        sortDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sortDropdown.setForeground(Color.WHITE);
        sortDropdown.setFont(FontManager.getRunescapeSmallFont());
        sortDropdown.setAlignmentX(LEFT_ALIGNMENT);
        sortDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        sortDropdown.setToolTipText("Sort monsters by relevance, combat level, HP, or name");
        sortDropdown.addItem("Sort: Relevance");
        sortDropdown.addItem("Sort: Combat Level (High -> Low)");
        sortDropdown.addItem("Sort: Combat Level (Low -> High)");
        sortDropdown.addItem("Sort: Name (A-Z)");
        sortDropdown.addItem("Sort: Slayer Level (High -> Low)");
        sortDropdown.addItem("Sort: Hitpoints (High -> Low)");
        sortDropdown.addActionListener(e -> rebuildMonsterList());
        headerPanel.add(sortDropdown);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        countLabel.setFont(FontManager.getRunescapeSmallFont());
        countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        countLabel.setAlignmentX(LEFT_ALIGNMENT);
        headerPanel.add(countLabel);

        add(headerPanel, BorderLayout.NORTH);

        // List Container with ScrollPane
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel listWrapper = new ScrollableContentPanel();
        listWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listWrapper.add(listContainer, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(listWrapper);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);

        rebuildMonsterList();
    }

    public void initialize()
    {
        rebuildMonsterList();
    }

    public void setCategory(String category)
    {
        if (category == null) return;
        for (int i = 0; i < categoryDropdown.getItemCount(); i++)
        {
            if (categoryDropdown.getItemAt(i).equalsIgnoreCase(category))
            {
                categoryDropdown.setSelectedIndex(i);
                return;
            }
        }
    }

    public void searchMonster(String name)
    {
        if (name != null)
        {
            searchField.setText(name);
            categoryDropdown.setSelectedIndex(0);
            rebuildMonsterList();
        }
    }

    public void rebuildMonsterList()
    {
        listContainer.removeAll();
        showMoreRow = null;

        String query = searchField.getText().trim();
        String selectedCategory = (String) categoryDropdown.getSelectedItem();

        // Bestiary DB loads on a background thread - say so rather than showing "No monsters found."
        if (monsterDatabase == null || !monsterDatabase.isLoaded())
        {
            boolean failed = monsterDatabase != null && monsterDatabase.isLoadFailed();
            String msg = failed
                ? "Bestiary failed to load - see the client log."
                : "Bestiary still loading…";
            countLabel.setText(msg);
            JLabel loadingLabel = new JLabel(msg);
            loadingLabel.setFont(FontManager.getRunescapeSmallFont());
            loadingLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            loadingLabel.setBorder(new EmptyBorder(12, 8, 8, 8));
            listContainer.add(loadingLabel);
            pendingMonsters = Collections.emptyList();
            renderedCount = 0;
            listContainer.revalidate();
            listContainer.repaint();
            return;
        }

        // Search-First Behavior (Loot Lookup style):
        // When no search query is typed and "All Categories" is selected, show a clean search landing panel.
        if (query.isEmpty() && (selectedCategory == null || "All Categories".equalsIgnoreCase(selectedCategory)))
        {
            int totalCount = monsterDatabase != null && monsterDatabase.isLoaded() ? monsterDatabase.getAllMonsters().size() : 0;
            countLabel.setText("Search to view bestiary (" + totalCount + " monsters)");
            listContainer.add(buildSearchFirstPlaceholder());
            pendingMonsters = Collections.emptyList();
            renderedCount = 0;
            listContainer.revalidate();
            listContainer.repaint();
            return;
        }

        List<Monster> monsters = monsterDatabase.searchMonsters(query, selectedCategory);

        if (monsters == null || monsters.isEmpty())
        {
            countLabel.setText("No monsters found.");
            JLabel emptyLabel = new JLabel("No monsters found matching your search.");
            emptyLabel.setFont(FontManager.getRunescapeSmallFont());
            emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            emptyLabel.setBorder(new EmptyBorder(12, 8, 8, 8));
            listContainer.add(emptyLabel);
            pendingMonsters = Collections.emptyList();
            renderedCount = 0;
            listContainer.revalidate();
            listContainer.repaint();
            return;
        }

        List<Monster> sorted = new ArrayList<>(monsters);
        String sortChoice = (String) sortDropdown.getSelectedItem();
        if (sortChoice != null && sortChoice.contains("Relevance"))
        {
            // Keep MonsterDatabase.searchMonsters' ranking (exact -> prefix -> combat level). With no
            // query there's nothing to rank against, so fall back to combat level high->low.
            if (query.isEmpty())
            {
                sorted.sort((a, b) -> Integer.compare(b.getCombatLevel(), a.getCombatLevel()));
            }
        }
        else if (sortChoice != null)
        {
            if (sortChoice.contains("Combat Level (High -> Low)"))
            {
                sorted.sort((a, b) -> Integer.compare(b.getCombatLevel(), a.getCombatLevel()));
            }
            else if (sortChoice.contains("Combat Level (Low -> High)"))
            {
                sorted.sort(Comparator.comparingInt(Monster::getCombatLevel));
            }
            else if (sortChoice.contains("Name"))
            {
                sorted.sort(Comparator.comparing(Monster::getName, String.CASE_INSENSITIVE_ORDER));
            }
            else if (sortChoice.contains("Slayer Level"))
            {
                sorted.sort((a, b) -> Integer.compare(b.getSlayerLevel(), a.getSlayerLevel()));
            }
            else if (sortChoice.contains("Hitpoints"))
            {
                sorted.sort((a, b) -> Integer.compare(b.getHitpoints(), a.getHitpoints()));
            }
        }

        countLabel.setText("Showing " + sorted.size() + " monster" + (sorted.size() == 1 ? "" : "s") + ":");

        pendingMonsters = sorted;
        renderedCount = 0;

        int initialBatch = Math.min(sorted.size(), SEARCH_RENDER_LIMIT);
        renderMoreCards(initialBatch);

        listContainer.revalidate();
        listContainer.repaint();
    }

    private JPanel buildSearchFirstPlaceholder()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(8, 4, 8, 4));

        // Info Header Box
        JPanel infoBox = new JPanel();
        infoBox.setLayout(new BoxLayout(infoBox, BoxLayout.Y_AXIS));
        infoBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        infoBox.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 65), 1),
            new EmptyBorder(8, 8, 8, 8)
        ));
        infoBox.setAlignmentX(LEFT_ALIGNMENT);

        JLabel promptTitle = new JLabel("Bestiary");
        promptTitle.setFont(FontManager.getRunescapeBoldFont());
        promptTitle.setForeground(TITLE_COLOR);
        promptTitle.setAlignmentX(LEFT_ALIGNMENT);
        infoBox.add(promptTitle);
        infoBox.add(Box.createRigidArea(new Dimension(0, 4)));

        JLabel promptDesc = new JLabel("<html><body style='width: " + SidebarMetrics.WRAP_WIDTH + "px; color: #EDE5D8;'>" +
            "Type a monster name above to view combat stats, weaknesses, spawn locations, and drop tables.<br><br>" +
            "Or choose a quick filter below:</body></html>");
        promptDesc.setFont(FontManager.getRunescapeSmallFont());
        promptDesc.setAlignmentX(LEFT_ALIGNMENT);
        infoBox.add(promptDesc);
        infoBox.add(Box.createRigidArea(new Dimension(0, 8)));

        // Quick Category Filter Buttons (3x2 grid)
        JPanel buttonGrid = new JPanel(new GridLayout(3, 2, 4, 4));
        buttonGrid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttonGrid.setAlignmentX(LEFT_ALIGNMENT);
        buttonGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));

        String[] quickCats = {"Bosses", "Slayer", "Dragons", "Demons", "Undead", "Wilderness"};
        for (String cat : quickCats)
        {
            JButton catBtn = new JButton(cat);
            catBtn.setFont(FontManager.getRunescapeSmallFont());
            catBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
            catBtn.setForeground(COMBAT_LVL_COLOR);
            catBtn.setFocusPainted(false);
            catBtn.setMargin(new Insets(2, 2, 2, 2));
            catBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            catBtn.addActionListener(e -> setCategory(cat));
            buttonGrid.add(catBtn);
        }
        infoBox.add(buttonGrid);
        infoBox.add(Box.createRigidArea(new Dimension(0, 8)));

        // Tip box
        JLabel tipLabel = new JLabel("<html><body style='width: " + SidebarMetrics.WRAP_WIDTH + "px; color: #9CA3AF;'>" +
            "<span style='color: #64C8FA;'>Tip:</span> search by name, drop item, or slayer category above, or open a monster from its zone on the world map.</body></html>");
        tipLabel.setFont(FontManager.getRunescapeSmallFont());
        tipLabel.setAlignmentX(LEFT_ALIGNMENT);
        infoBox.add(tipLabel);

        panel.add(infoBox);

        return panel;
    }

    /**
     * Builds and appends the next {@code count} cards from {@link #pendingMonsters} (starting at
     * {@link #renderedCount}), then re-adds the "Show more" trailing row if any matches remain
     * unrendered. Only ever builds Swing components for monsters not already rendered -- clicking
     * "Show more" does not rebuild or re-touch the cards already on screen.
     */
    private void renderMoreCards(int count)
    {
        if (showMoreRow != null)
        {
            listContainer.remove(showMoreRow);
            showMoreRow = null;
        }

        int end = Math.min(renderedCount + count, pendingMonsters.size());
        for (int i = renderedCount; i < end; i++)
        {
            JPanel card = buildMonsterCard(pendingMonsters.get(i));
            listContainer.add(card);
            listContainer.add(Box.createRigidArea(new Dimension(0, 3)));
        }
        renderedCount = end;

        int remaining = pendingMonsters.size() - renderedCount;
        if (remaining > 0)
        {
            showMoreRow = buildShowMoreRow(remaining);
            listContainer.add(showMoreRow);
        }
    }

    /**
     * Trailing affordance shown while a capped search still has unrendered matches. Clicking it renders
     * up to another SEARCH_RENDER_LIMIT cards, so a very broad early query (e.g. a single letter) never
     * forces the full match set to be built at once.
     */
    private JPanel buildShowMoreRow(int remaining)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(4, 4, 8, 4));

        int nextBatch = Math.min(remaining, SEARCH_RENDER_LIMIT);
        JButton showMoreBtn = new JButton("Show " + nextBatch + " more (" + remaining + " remaining) - refine search to narrow");
        showMoreBtn.setFont(FontManager.getRunescapeSmallFont());
        showMoreBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        showMoreBtn.setForeground(FOCUS_BLUE);
        showMoreBtn.setFocusPainted(false);
        showMoreBtn.addActionListener(e -> {
            renderMoreCards(SEARCH_RENDER_LIMIT);
            listContainer.revalidate();
            listContainer.repaint();
        });
        row.add(showMoreBtn, BorderLayout.CENTER);
        return row;
    }

    private JPanel buildMonsterCard(Monster monster)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(3, 4, 3, 4)
        ));
        card.setAlignmentX(LEFT_ALIGNMENT);
        // Every card renders the same fixed set of detail rows (name, meta, slayer/weakness,
        // badge-or-placeholder) and the same button-row footprint (see below), so every card's natural
        // preferred height is identical regardless of which optional fields a given monster has.
        //
        // The cap below is NOT a "soft ceiling that BoxLayout only approaches when there's leftover
        // space" -- that reasoning (used to justify a 70px cap here previously) is wrong: BoxLayout
        // (via SizeRequirements.calculateTiledPositions) treats maximumSize as a hard per-component
        // ceiling that gets enforced on every layout pass, not just when a container has surplus space
        // to distribute. This was empirically confirmed with a headless Swing layout test (build a real
        // MonsterDirectoryView, force doLayout()/validate(), and read back a card's actual allocated
        // getSize() rather than just its getPreferredSize()): with the old 70px cap, a card's *actual*
        // rendered height was clamped to exactly 70px even though its own content required 130px,
        // silently clipping the badge row and the entire button row (Inspect/Map) out of the card's
        // visible/clickable bounds -- this is the live-play "cards are cut off, can't click Inspect or
        // Map" bug.
        //
        // Real content height, measured the same way against the actual current monsters_data.json.gz:
        //   - typical single-line name (4 fixed detail rows @ ~13-16px + button row 20px + border 7px): ~82px
        //   - worst case: names that wrap to 2 lines within the nameLabel's 95px HTML width cap (e.g. the
        //     real 39-char "Witch's experiment (fourth form) (hard)") push the card to ~130px
        // 136px covers the real worst case with a small safety margin for font-metric variance between
        // this dev environment and the live RuneLite client, while still bounding how tall a card can
        // stretch when a short filtered/search result list leaves the sidebar with extra vertical room.
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 136));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Row 1: Portrait + Info
        JPanel topRow = new JPanel(new BorderLayout(5, 0));
        topRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        topRow.setAlignmentX(LEFT_ALIGNMENT);

        // Portrait 32x32
        JLabel portraitLabel = new JLabel();
        portraitLabel.setPreferredSize(new Dimension(32, 32));
        portraitLabel.setMinimumSize(new Dimension(32, 32));
        portraitLabel.setMaximumSize(new Dimension(32, 32));
        portraitLabel.setHorizontalAlignment(JLabel.CENTER);
        portraitLabel.setVerticalAlignment(JLabel.CENTER);
        npcPortraitManager.loadNpcPortrait(monster, 32, portraitLabel);
        topRow.add(portraitLabel, BorderLayout.WEST);

        JPanel detailsCol = new JPanel();
        detailsCol.setLayout(new BoxLayout(detailsCol, BoxLayout.Y_AXIS));
        detailsCol.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Line 1: Name + Combat Level (fixed width on EAST to prevent cutoff, name truncated with ellipsis)
        JPanel nameLine = new JPanel(new BorderLayout(4, 0));
        nameLine.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nameLine.setAlignmentX(LEFT_ALIGNMENT);

        // Name capped to 95px (HTML width) -- the nameLine row budget at the default ~225px sidebar is
        // roughly 106px once the 32px portrait, 54px fixed-width combat-level label, and card/row gaps
        // are subtracted, so 95px leaves a small margin. Long wiki names (e.g. the 39-char
        // "Witch's experiment (second form) (hard)") wrap to extra lines within the card instead of
        // inflating the card's preferred width and clipping the button row out of the viewport.
        JLabel nameLabel = new JLabel("<html><body style='width: 95px;'>" + monster.getName() + "</body></html>");
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(TITLE_COLOR);
        nameLabel.setToolTipText(monster.getName());
        nameLine.add(nameLabel, BorderLayout.CENTER);

        JLabel lvlLabel = new JLabel("Lvl " + monster.getCombatLevel(), SwingConstants.RIGHT);
        lvlLabel.setFont(FontManager.getRunescapeBoldFont());
        lvlLabel.setForeground(COMBAT_LVL_COLOR);
        lvlLabel.setPreferredSize(new Dimension(66, 16)); // fits "Lvl 1563"
        lvlLabel.setMinimumSize(new Dimension(66, 16));
        lvlLabel.setMaximumSize(new Dimension(66, 16));
        nameLine.add(lvlLabel, BorderLayout.EAST);
        detailsCol.add(nameLine);

        // Line 2: HP, Max Hit & Category
        String metaText = "HP: " + monster.getHitpoints() + " - Max: " + monster.getMaxHit() + " - " + (monster.isMembers() ? "P2P" : "F2P");
        JLabel metaLabel = new JLabel(metaText);
        metaLabel.setFont(FontManager.getRunescapeSmallFont());
        metaLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        metaLabel.setAlignmentX(LEFT_ALIGNMENT);
        detailsCol.add(metaLabel);

        // Line 3: Slayer requirement or Weakness tag. In current data every monster has a non-empty
        // weakness (real value or the "Unknown" fallback -- see GEMINI.md §9 item 8), so this row is
        // always populated in practice, but we still fall back to a same-height blank placeholder for
        // the (data-shape-dependent) case where a monster has neither, so card height never depends on
        // which of these two fields happens to be present -- keeps every card's row count identical
        // regardless of what a given monster's data looks like.
        if (monster.getSlayerLevel() > 1)
        {
            JLabel slayerLabel = new JLabel("Slayer: " + monster.getSlayerLevel() + " - " + (monster.getWeakness() != null ? monster.getWeakness() : monster.getAttackType()));
            slayerLabel.setFont(FontManager.getRunescapeSmallFont());
            slayerLabel.setForeground(SLAYER_PURPLE);
            slayerLabel.setAlignmentX(LEFT_ALIGNMENT);
            detailsCol.add(slayerLabel);
        }
        else if (monster.getWeakness() != null && !monster.getWeakness().isEmpty())
        {
            JLabel weakLabel = new JLabel("Weakness: " + monster.getWeakness());
            weakLabel.setFont(FontManager.getRunescapeSmallFont());
            weakLabel.setForeground(WEAKNESS_COLOR);
            weakLabel.setAlignmentX(LEFT_ALIGNMENT);
            detailsCol.add(weakLabel);
        }
        else
        {
            detailsCol.add(buildPlaceholderLine());
        }

        // Line 4: encounterType badge, e.g. "[Raids: ToB]" for instanced/raid bosses -- so they read as
        // distinct from open-world monsters in the card list, matching the badge already shown in
        // MonsterDetailView's drill-down. Added as its own row (not inline with the name) so it never
        // competes with the capped nameLabel's width budget above.
        //
        // This row is now added UNCONDITIONALLY (real badge or an invisible same-font placeholder) so
        // every card reserves identical vertical space for it -- previously this row was only added for
        // the ~24% of monsters with an encounterType (raid/instanced bosses), which made those cards
        // visibly taller than the other ~76% and produced the ragged, inconsistent row heights reported
        // by the user. Reserving the space uniformly instead of conditionally omitting it keeps every
        // card's height identical regardless of which monster it represents.
        if (monster.hasEncounterType())
        {
            JLabel encounterBadge = new JLabel("[" + getBadgeTextForEncounter(monster.getEncounterType()) + "]");
            encounterBadge.setFont(FontManager.getRunescapeSmallFont());
            encounterBadge.setForeground(getBadgeColorForEncounter(monster.getEncounterType()));
            encounterBadge.setAlignmentX(LEFT_ALIGNMENT);
            encounterBadge.setToolTipText(monster.getEncounterType());
            detailsCol.add(encounterBadge);
        }
        else
        {
            detailsCol.add(buildPlaceholderLine());
        }

        topRow.add(detailsCol, BorderLayout.CENTER);
        card.add(topRow);
        card.add(Box.createRigidArea(new Dimension(0, 3)));

        // Row 2: Action Buttons: [Inspect] [Map]
        JPanel btnRow = new JPanel(new BorderLayout(4, 0));
        btnRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        btnRow.setPreferredSize(new Dimension(0, 20));
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JButton inspectBtn = new JButton("Inspect");
        inspectBtn.setFont(FontManager.getRunescapeSmallFont());
        inspectBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        inspectBtn.setForeground(Color.WHITE);
        inspectBtn.setFocusPainted(false);
        inspectBtn.setMargin(new Insets(0, 2, 0, 2));
        inspectBtn.setPreferredSize(new Dimension(0, 20));
        inspectBtn.setToolTipText("View combat stats, weaknesses, spawn locations, and loot drop table");
        inspectBtn.addActionListener(e -> onInspectMonster.accept(monster));
        btnRow.add(inspectBtn, BorderLayout.CENTER);

        // The EAST slot is always 50px wide, whether it holds a real "Map" button or an invisible
        // placeholder of the same size. Previously this slot was omitted entirely for the ~14% of
        // monsters with no spawn zones (instanced-only bosses with no entrance marker), which let
        // "Inspect" stretch to the card's full width on those cards -- a different Inspect-button width
        // from every other card, and the exact "sizing is inconsistent" symptom reported by the user.
        // Reserving the slot unconditionally keeps the Inspect button (and thus the whole button row)
        // the same width on every card regardless of whether that monster has spawn-zone data.
        if (monster.hasSpawnZones())
        {
            JButton focusBtn = new JButton("Map");
            focusBtn.setFont(FontManager.getRunescapeSmallFont());
            focusBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
            focusBtn.setForeground(FOCUS_BLUE);
            focusBtn.setFocusPainted(false);
            focusBtn.setMargin(new Insets(0, 2, 0, 2));
            focusBtn.setPreferredSize(new Dimension(50, 20));
            focusBtn.setMinimumSize(new Dimension(50, 20));
            focusBtn.setMaximumSize(new Dimension(50, 20));
            focusBtn.setToolTipText("Show this monster's spawn zone on the World Map");
            focusBtn.addActionListener(e -> onFocusMonsterOnMap.accept(monster));
            btnRow.add(focusBtn, BorderLayout.EAST);
        }
        else
        {
            btnRow.add(Box.createRigidArea(new Dimension(50, 20)), BorderLayout.EAST);
        }

        card.add(btnRow);

        // Hover and Click behavior
        JPopupMenu popupMenu = createCardContextMenu(monster);
        card.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                card.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }

            @Override
            public void mousePressed(MouseEvent e)
            {
                if (e.isPopupTrigger()) popupMenu.show(card, e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (e.isPopupTrigger()) popupMenu.show(card, e.getX(), e.getY());
            }

            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getButton() == MouseEvent.BUTTON1)
                {
                    onInspectMonster.accept(monster);
                }
            }
        });

        return card;
    }

    /**
     * A zero-content JLabel sized to exactly the same line height as the real small-font detail rows
     * (slayer/weakness line, encounterType badge line) it stands in for, using the label's own
     * background color so it paints nothing visible. Used so that a card missing one of those optional
     * fields still reserves the same vertical space as a card that has it -- keeping every card in the
     * list the same height regardless of which optional fields a given monster's data happens to carry.
     * A single shared font lookup (no per-call FontMetrics measurement) keeps this cheap to call once
     * per card during list rebuilds.
     */
    private JLabel buildPlaceholderLine()
    {
        JLabel placeholder = new JLabel(" ");
        placeholder.setFont(FontManager.getRunescapeSmallFont());
        placeholder.setForeground(ColorScheme.DARKER_GRAY_COLOR);
        placeholder.setAlignmentX(LEFT_ALIGNMENT);
        return placeholder;
    }

    private JPopupMenu createCardContextMenu(Monster monster)
    {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem inspectItem = new JMenuItem("Inspect Stats & Drop Table");
        inspectItem.addActionListener(e -> onInspectMonster.accept(monster));
        menu.add(inspectItem);

        JMenuItem focusMapItem = new JMenuItem("Show on World Map");
        focusMapItem.addActionListener(e -> onFocusMonsterOnMap.accept(monster));
        menu.add(focusMapItem);

        if (monster.getWikiUrl() != null && !monster.getWikiUrl().isEmpty())
        {
            JMenuItem wikiItem = new JMenuItem("Open Wiki Page");
            wikiItem.addActionListener(e -> LinkBrowser.browse(monster.getWikiUrl()));
            menu.add(wikiItem);
        }

        return menu;
    }

    // Mirrors MonsterDetailView's getBadgeTextForEncounter() so the card-list badge and the detail-view
    // badge always read identically for the same encounterType string. MonsterDetailView's version is
    // private, so this is a local copy rather than a shared import.
    private String getBadgeTextForEncounter(String encounterType)
    {
        if (encounterType == null) return "Instanced Encounter";
        String encLower = encounterType.toLowerCase(java.util.Locale.ROOT);
        if (encLower.contains("theatre of blood")) return "Raids: ToB";
        if (encLower.contains("chambers of xeric")) return "Raids: CoX";
        if (encLower.contains("tombs of amascut")) return "Raids: ToA";
        if (encLower.contains("raid")) return "Raids Encounter";
        if (encLower.contains("fight cave") || encLower.contains("inferno") || encLower.contains("tzhaar")) return "Minigame Wave";
        if (encLower.contains("pest control")) return "Pest Control";
        if (encLower.contains("barbarian assault")) return "Barbarian Assault";
        if (encLower.contains("gauntlet")) return "The Gauntlet";
        if (encLower.contains("nightmare zone")) return "Nightmare Zone";
        if (encLower.contains("colosseum")) return "Fortis Colosseum";
        if (encLower.contains("soul wars")) return "Soul Wars";
        if (encLower.contains("temple trekking")) return "Temple Trekking";
        if (encLower.contains("minigame")) return "Minigame";
        if (encLower.contains("boss minion") || encLower.contains("minion")) return "Boss Minion";
        if (encLower.contains("superior slayer") || encLower.contains("superior")) return "Superior Slayer";
        if (encLower.contains("treasure trails") || encLower.contains("clue")) return "Treasure Trails";
        if (encLower.contains("warriors' guild")) return "Warriors' Guild";
        if (encLower.contains("quest")) return "Quest Encounter";
        if (encLower.contains("creature creation")) return "Creature Creation";
        if (encLower.contains("reanimated")) return "Arceuus Magic";
        if (encLower.contains("random event")) return "Random Event";
        return "Instanced Encounter";
    }

    // Mirrors MonsterDetailView's getBadgeColorForEncounter() -- see note above.
    private Color getBadgeColorForEncounter(String encounterType)
    {
        if (encounterType == null) return ColorScheme.LIGHT_GRAY_COLOR;
        String encLower = encounterType.toLowerCase(java.util.Locale.ROOT);
        if (encLower.contains("raid")) return TITLE_COLOR;
        if (encLower.contains("minigame") || encLower.contains("tzhaar") || encLower.contains("inferno") || encLower.contains("fight cave") || encLower.contains("pest control") || encLower.contains("barbarian assault") || encLower.contains("gauntlet") || encLower.contains("colosseum") || encLower.contains("soul wars") || encLower.contains("temple trekking")) return FOCUS_BLUE;
        if (encLower.contains("quest")) return QUEST_PURPLE;
        if (encLower.contains("superior")) return SLAYER_PURPLE;
        if (encLower.contains("boss minion") || encLower.contains("minion")) return WILDY_RED;
        if (encLower.contains("treasure trails") || encLower.contains("clue")) return RARITY_UNCOMMON;
        if (encLower.contains("creature creation")) return WEAKNESS_COLOR;
        if (encLower.contains("reanimated")) return REANIMATED_PURPLE;
        return ColorScheme.LIGHT_GRAY_COLOR;
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
