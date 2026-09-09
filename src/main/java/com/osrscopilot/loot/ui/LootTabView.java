package com.osrscopilot.loot.ui;

import com.osrscopilot.combat.CombatFormat;
import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.RarityFormat;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterDrop;
import com.osrscopilot.loot.LootDryness;
import com.osrscopilot.loot.LootTrackerManager;
import com.osrscopilot.loot.model.ItemStat;
import com.osrscopilot.loot.model.LootItem;
import com.osrscopilot.loot.model.LootRecord;
import com.osrscopilot.loot.model.MonsterLootSummary;
import com.osrscopilot.loot.model.SourceKind;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * "Loot" side-panel tab, modelled on the stock Loot Tracker: one collapsible card per source, body
 * is a pure wrapping icon grid of items you've <em>obtained</em> only (name + KC + count kept).
 * Mousing an icon shows condensed drop math; clicking the source opens a detail view with every
 * potential drop and the full dryness / your-rate-vs-wiki analysis.
 *
 * <p>Deliberately narrow: the side panel gives ~190px usable, so nothing wide is laid out on a
 * single row - the analysis lives in the tooltip, the source detail and the per-item history.
 */
public class LootTabView extends JPanel
{
    private static final Color TITLE_COLOR = new Color(255, 152, 31);
    private static final Color SUBTLE = ColorScheme.LIGHT_GRAY_COLOR;
    // Match the stock RuneLite Loot Tracker: near-black per-source header strip, and let the
    // 1px GridLayout seams show the lighter DARK_GRAY ground so the item grid reads as a grid.
    private static final Color CARD_HEADER_BG = ColorScheme.DARKER_GRAY_COLOR.darker(); // ~(21,21,21)
    private static final Color GRID_LINE = ColorScheme.DARK_GRAY_COLOR;                 // (40,40,40)
    private static final Color DRY_RED = new Color(239, 110, 110);
    private static final Color LUCKY_GREEN = new Color(120, 210, 140);
    private static final Color LINK_BLUE = new Color(120, 170, 230);
    private static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault());
    private static final int MAX_SOURCE_CARDS = 80;
    private static final int CELL_W = 36;
    private static final int CELL_H = 42;
    private static final int PER_ROW = 5;

    // GP/hr here is a lifetime wall-clock average: totalValue / (lastSeen - firstSeen). It only
    // means something when that span is a single sitting - under a minute it's noise, and past the
    // upper cap the span is dominated by idle time / multiple sessions and the "/hr" is meaningless
    // (a source killed across weeks would show a tiny fictitious rate). Deferred: a real
    // engaged-time session GP/hr timer that measures time actually spent on the source.
    private static final long GPHR_MIN_SPAN_MS = 60_000L;
    private static final long GPHR_MAX_SPAN_MS = 12L * 60 * 60 * 1000;

    private static final String[] DATE_PRESETS = {"Any time", "Today", "7 days", "30 days"};
    private static final long[] DATE_MS = {0L, 86_400_000L, 604_800_000L, 2_592_000_000L};
    private static final String[] KIND_LABELS = {"All sources", "NPC", "PvP", "Pickpocket", "Chest / clue"};
    private static final SourceKind[] KIND_VALUES = {null, SourceKind.NPC_KILL, SourceKind.PVP, SourceKind.PICKPOCKET, SourceKind.CHEST_CLUE};
    private static final String[] MINVAL_LABELS = {"Any value", "10k+", "100k+", "1M+"};
    private static final long[] MINVAL = {0L, 10_000L, 100_000L, 1_000_000L};
    private static final String[] SORT_LABELS = {"Recent", "Value", "A-Z", "Kills"};

    private final LootTrackerManager loot;
    private final MonsterDatabase monsterDatabase;
    private final ItemManager itemManager;
    private final Consumer<Monster> onOpenMonster;

    private final JTextField searchField = new JTextField();
    private final JComboBox<String> sortCombo = new JComboBox<>(SORT_LABELS);
    private final JComboBox<String> dateCombo = new JComboBox<>(DATE_PRESETS);
    private final JComboBox<String> kindCombo = new JComboBox<>(KIND_LABELS);
    private final JComboBox<String> minValCombo = new JComboBox<>(MINVAL_LABELS);
    private final JButton collapseAllBtn = new JButton("Collapse all");
    private final JButton scopeToggle = new JButton("All time");
    private final JButton exportBtn = new JButton("Export CSV");
    private final JButton clearBtn = new JButton("Clear all");
    private final JLabel summaryLabel = new JLabel();

    private final JPanel listContainer = new JPanel();
    private final JPanel body = new JPanel(new BorderLayout());
    private boolean sessionOnly = false;
    private boolean allCollapsed = false;
    private boolean onListView = true;
    private final Set<String> collapsed = new HashSet<>();

    // Coalesces a burst of loot/kill events into a single EDT rebuild.
    private volatile boolean rebuildQueued = false;
    // A live loot/kill update arrived while the tab was hidden - rebuild when it's shown again
    // (showTab() also calls refresh() on open, so this is just the panel-re-expand path).
    private boolean liveRebuildPending = false;

    public LootTabView(LootTrackerManager loot, MonsterDatabase monsterDatabase, ItemManager itemManager,
                       Consumer<Monster> onOpenMonster)
    {
        this.loot = loot;
        this.monsterDatabase = monsterDatabase;
        this.itemManager = itemManager;
        this.onOpenMonster = onOpenMonster;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        add(buildHeader(), BorderLayout.NORTH);

        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        body.setBackground(ColorScheme.DARK_GRAY_COLOR);
        add(body, BorderLayout.CENTER);

        if (loot != null)
        {
            loot.addChangeListener(this::onLootChanged);
        }

        addHierarchyListener(e ->
        {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                && isShowing() && liveRebuildPending)
            {
                refreshLive();
            }
        });
    }

    /** Loot manager fired a change (any thread). Coalesce to one rebuild on the EDT. */
    private void onLootChanged()
    {
        if (rebuildQueued)
        {
            return;
        }
        rebuildQueued = true;
        javax.swing.SwingUtilities.invokeLater(() ->
        {
            rebuildQueued = false;
            refreshLive();
        });
    }

    /** Rebuild the source list in place if it's the current view (called live on kills / drops). */
    public void refreshLive()
    {
        if (!onListView)
        {
            return;
        }
        if (!isShowing())
        {
            // showTab() runs refresh() on open, so the tab is never stale when the user returns.
            liveRebuildPending = true;
            return;
        }
        liveRebuildPending = false;
        rebuildList();
    }

    private JPanel buildHeader()
    {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setBorder(new EmptyBorder(6, 6, 6, 6));

        JLabel title = new JLabel("Loot Tracker");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(TITLE_COLOR);
        title.setAlignmentX(LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createRigidArea(new Dimension(0, 4)));

        // Partial-capability notice: without RuneLite's stock Loot Tracker the pickpocket / clue /
        // chest LootReceived stream is gone, but NPC-kill loot (onNpcLootReceived) still lands.
        if (loot != null && !loot.stockLootTrackerAvailable())
        {
            JLabel trackerHint = new JLabel("<html><div style='width:168px'>Pickpocket, clue and "
                + "chest loot need RuneLite's <b>Loot Tracker</b> plugin enabled. NPC-kill loot "
                + "still tracks without it.</div></html>");
            trackerHint.setFont(FontManager.getRunescapeSmallFont());
            trackerHint.setForeground(TITLE_COLOR);
            trackerHint.setAlignmentX(LEFT_ALIGNMENT);
            trackerHint.setBorder(new EmptyBorder(0, 0, 4, 0));
            header.add(trackerHint);
            header.add(Box.createRigidArea(new Dimension(0, 3)));
        }

        styleField(searchField);
        searchField.setToolTipText("Search by monster / source name, or by an item you've looted");
        searchField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyReleased(KeyEvent e)
            {
                rebuildList();
            }
        });
        header.add(searchField);
        header.add(Box.createRigidArea(new Dimension(0, 3)));

        sortCombo.setToolTipText("Order sources: most recent kill / total value / name / kill count");
        header.add(filterRow(compactCombo(sortCombo), compactCombo(dateCombo)));
        header.add(Box.createRigidArea(new Dimension(0, 3)));
        header.add(filterRow(compactCombo(kindCombo), compactCombo(minValCombo)));
        header.add(Box.createRigidArea(new Dimension(0, 3)));

        styleSmallButton(collapseAllBtn);
        collapseAllBtn.setToolTipText("Collapse / expand every source");
        collapseAllBtn.addActionListener(e -> toggleCollapseAll());
        styleSmallButton(scopeToggle);
        scopeToggle.setToolTipText("All-time loot vs just this session");
        scopeToggle.addActionListener(e ->
        {
            sessionOnly = !sessionOnly;
            scopeToggle.setText(sessionOnly ? "This session" : "All time");
            rebuildList();
        });
        styleSmallButton(exportBtn);
        exportBtn.setToolTipText("Write the loot history to a CSV file");
        exportBtn.addActionListener(e -> doExport());
        styleSmallButton(clearBtn);
        clearBtn.setForeground(DRY_RED);
        clearBtn.setToolTipText("Wipe all loot history for this character and start fresh");
        clearBtn.addActionListener(e -> confirmClearAll());
        header.add(filterRow(scopeToggle, collapseAllBtn));
        header.add(Box.createRigidArea(new Dimension(0, 3)));
        header.add(filterRow(exportBtn, clearBtn));
        header.add(Box.createRigidArea(new Dimension(0, 4)));

        sortCombo.addActionListener(e -> rebuildList());
        dateCombo.addActionListener(e -> rebuildList());
        kindCombo.addActionListener(e -> rebuildList());
        minValCombo.addActionListener(e -> rebuildList());

        summaryLabel.setFont(FontManager.getRunescapeSmallFont());
        summaryLabel.setForeground(SUBTLE);
        summaryLabel.setAlignmentX(LEFT_ALIGNMENT);
        header.add(summaryLabel);
        return header;
    }

    public void refresh()
    {
        rebuildList();
    }

    private void confirmClearAll()
    {
        if (loot == null)
        {
            return;
        }
        int r = javax.swing.JOptionPane.showConfirmDialog(this,
            "Wipe ALL loot history for this character and start fresh?\nThis can't be undone.",
            "Clear loot history", javax.swing.JOptionPane.YES_NO_OPTION,
            javax.swing.JOptionPane.WARNING_MESSAGE);
        if (r == javax.swing.JOptionPane.YES_OPTION)
        {
            loot.clearAll();
            collapsed.clear();
            rebuildList();
        }
    }

    private void toggleCollapsed(String key)
    {
        if (!collapsed.remove(key))
        {
            collapsed.add(key);
        }
        rebuildList();
    }

    private void toggleCollapseAll()
    {
        allCollapsed = !allCollapsed;
        collapseAllBtn.setText(allCollapsed ? "Expand all" : "Collapse all");
        collapsed.clear();
        if (allCollapsed && loot != null)
        {
            for (MonsterLootSummary s : loot.getSummaries())
            {
                collapsed.add(s.getSourceKey());
            }
        }
        rebuildList();
    }

    // ------------------------------------------------------------------ filters

    private long dateFloorMs()
    {
        int i = dateCombo.getSelectedIndex();
        long window = (i >= 0 && i < DATE_MS.length) ? DATE_MS[i] : 0L;
        return window == 0 ? 0L : System.currentTimeMillis() - window;
    }

    private SourceKind kindFilter()
    {
        int i = kindCombo.getSelectedIndex();
        return (i > 0 && i < KIND_VALUES.length) ? KIND_VALUES[i] : null;
    }

    private long minValueFilter()
    {
        int i = minValCombo.getSelectedIndex();
        return (i >= 0 && i < MINVAL.length) ? MINVAL[i] : 0L;
    }

    private boolean anyRecordFilterActive()
    {
        return sessionOnly || dateCombo.getSelectedIndex() > 0 || kindCombo.getSelectedIndex() > 0;
    }

    /**
     * Whether a lifetime-span "/hr" readout is meaningful for a source right now. The source list
     * and the source-detail view share this one guard so the "/hr" appears (and is worded) the
     * same in both: only all-time scope, only a value &gt; 0, and only a span that plausibly is a
     * single sitting ({@link #GPHR_MIN_SPAN_MS}..{@link #GPHR_MAX_SPAN_MS}).
     */
    private boolean showGpPerHour(long spanMs, long value)
    {
        return !sessionOnly && value > 0 && spanMs > GPHR_MIN_SPAN_MS && spanMs <= GPHR_MAX_SPAN_MS;
    }

    /** Search matches a source you've looted an item from whose name contains the query. */
    private boolean matchesObtainedItem(MonsterLootSummary s, String q)
    {
        Monster m = monsterDatabase != null ? monsterDatabase.getMonsterByName(s.getSourceName()) : null;
        if (m == null || m.getDrops() == null || s.getItemStats().isEmpty())
        {
            return false;
        }
        Set<Integer> got = s.getItemStats().keySet();
        for (MonsterDrop d : m.getDrops())
        {
            if (d.getItemId() > 0 && got.contains(d.getItemId())
                && d.getName() != null && d.getName().toLowerCase(Locale.ROOT).contains(q))
            {
                return true;
            }
        }
        return false;
    }

    /** Source ordering for the list. Default "Recent" = the source you just acted on at the top. */
    private Comparator<MonsterLootSummary> sourceOrder()
    {
        switch (sortCombo.getSelectedIndex())
        {
            case 1:  return Comparator.comparingLong(MonsterLootSummary::getTotalBestValue).reversed();
            case 2:  return Comparator.comparing(s -> s.getSourceName() == null ? "" : s.getSourceName().toLowerCase(Locale.ROOT));
            case 3:  return Comparator.comparingInt(MonsterLootSummary::getTotalKills).reversed();
            default: return Comparator.comparingLong(MonsterLootSummary::getLastTouchSeq)
                .thenComparingLong(MonsterLootSummary::getLastSeenMs).reversed();
        }
    }

    // ------------------------------------------------------------------ source list

    private void rebuildList()
    {
        listContainer.removeAll();

        if (loot == null)
        {
            addPlain(listContainer, "Loot tracking unavailable.");
            showList();
            return;
        }

        String q = searchField.getText().trim().toLowerCase(Locale.ROOT);
        long floor = Math.max(dateFloorMs(), sessionOnly ? loot.getSessionStartMs() : 0L);
        SourceKind kindOnly = kindFilter();
        long minVal = minValueFilter();
        boolean filtered = anyRecordFilterActive();

        List<MonsterLootSummary> sources = new ArrayList<>(loot.getSummaries());
        sources.sort(sourceOrder());

        int shown = 0;
        int hidden = 0;
        long grandTotal = 0;
        int grandDrops = 0;
        for (MonsterLootSummary s : sources)
        {
            String name = s.getSourceName() == null ? "?" : s.getSourceName();
            boolean nameHit = q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q);
            boolean itemHit = !nameHit && matchesObtainedItem(s, q);
            if (!nameHit && !itemHit)
            {
                continue;
            }
            if (kindOnly != null && s.getSourceKind() != kindOnly)
            {
                continue;
            }

            List<LootRecord> recs = filtered ? recordsFor(s.getSourceKey(), floor) : null;
            long value = filtered ? sumValue(recs) : s.getTotalBestValue();
            boolean anyLoot = filtered ? !recs.isEmpty() : s.getLootedKills() > 0;
            // Stock-Loot-Tracker behaviour: only list sources you've actually got a drop from.
            if (!anyLoot || (minVal > 0 && value < minVal))
            {
                continue;
            }

            grandTotal += value;
            grandDrops += (recs != null) ? recs.size() : s.getLootedKills();
            if (shown >= MAX_SOURCE_CARDS)
            {
                hidden++;
                continue;
            }
            listContainer.add(buildSourceCard(s, recs, value, !q.isEmpty()));
            listContainer.add(Box.createRigidArea(new Dimension(0, 3)));
            shown++;
        }

        if (shown == 0)
        {
            addPlain(listContainer, loot.getRecordCount() == 0
                ? "No loot recorded yet - go kill something."
                : "Nothing matches these filters.");
        }
        else if (hidden > 0)
        {
            addPlain(listContainer, "+" + hidden + " more (refine filters)");
        }

        summaryLabel.setText((filtered ? "Filtered: " : "Total: ") + CombatFormat.amount(grandTotal)
            + "  " + CombatFormat.amount(grandDrops) + " drops  " + shown
            + (shown == 1 ? " source" : " sources"));

        showList();
    }

    private JPanel buildSourceCard(MonsterLootSummary s, List<LootRecord> filteredRecs, long value, boolean forceExpand)
    {
        String key = s.getSourceKey();
        String name = s.getSourceName() == null ? "?" : s.getSourceName();
        boolean isCollapsed = !forceExpand && collapsed.contains(key);
        int kc = s.getTotalKills();
        Monster monster = monsterDatabase != null ? monsterDatabase.getMonsterByName(name) : null;

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setAlignmentX(LEFT_ALIGNMENT);
        // Stock Loot Tracker separates its boxes with a 5px gap of the panel ground, no drawn line.
        card.setBorder(new EmptyBorder(5, 0, 0, 0));

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setBackground(CARD_HEADER_BG);
        head.setAlignmentX(LEFT_ALIGNMENT);
        head.setBorder(new EmptyBorder(5, 6, 4, 6));
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        // Row 1: [caret]  Name  (name -> drop table; caret -> collapse)
        JPanel r1 = new JPanel(new BorderLayout(3, 0));
        r1.setBackground(CARD_HEADER_BG);
        r1.setAlignmentX(LEFT_ALIGNMENT);
        r1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        JLabel caret = new JLabel(isCollapsed ? "[+]" : "[-]");
        caret.setFont(FontManager.getRunescapeSmallFont());
        caret.setForeground(SUBTLE);
        caret.setPreferredSize(new Dimension(15, 16));
        caret.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        caret.addMouseListener(click(() -> toggleCollapsed(key)));
        r1.add(caret, BorderLayout.WEST);

        JLabel nameL = new JLabel(ellipsize(name, 24));
        nameL.setFont(FontManager.getRunescapeBoldFont());
        nameL.setForeground(Color.WHITE);
        nameL.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nameL.setToolTipText(name + " - click for every drop & your odds");
        nameL.addMouseListener(click(() -> showSourceDetail(key)));
        r1.add(nameL, BorderLayout.CENTER);
        head.add(r1);

        // Row 2: KC / total value / gp-per-hr - click to collapse
        StringBuilder sub = new StringBuilder();
        if (kc > 0)
        {
            sub.append("KC ").append(kc);
        }
        if (value > 0)
        {
            sub.append(sub.length() > 0 ? "   " : "").append(CombatFormat.amount(value));
        }
        long span = s.getLastSeenMs() - s.getFirstSeenMs();
        if (showGpPerHour(span, value))
        {
            sub.append("   ").append(CombatFormat.amount(s.gpPerHour(span / 1000))).append("/hr");
        }
        JLabel subL = subLabel(sub.length() > 0 ? sub.toString() : "no kills yet", SUBTLE);
        subL.setBorder(new EmptyBorder(1, 17, 0, 0));
        subL.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        subL.addMouseListener(click(() -> toggleCollapsed(key)));
        head.add(subL);
        card.add(head);

        if (isCollapsed)
        {
            return card;
        }

        List<ItemRow> obtained = new ArrayList<>();
        for (ItemRow r : buildItemRows(s, monster, filteredRecs))
        {
            if (r.count > 0)
            {
                obtained.add(r);
            }
        }
        card.add(obtained.isEmpty() ? subLabel("  no items yet", SUBTLE) : iconGrid(key, kc, obtained));
        // Pin the card to the viewport width and its natural height so a BoxLayout parent can't
        // stretch it, and so nothing spills off the right edge as the list grows.
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private JPanel iconGrid(String sourceKey, int kc, List<ItemRow> obtained)
    {
        // Fixed 5-column grid (like the stock Loot Tracker) - GridLayout wraps into as many rows
        // as needed and never overflows horizontally, unlike a FlowLayout whose preferred width
        // is one long line.
        int rows = Math.max(1, (int) Math.ceil(obtained.size() / (double) PER_ROW));
        JPanel grid = new JPanel(new GridLayout(rows, PER_ROW, 1, 1));
        // The grid's own colour shows through the 1px GridLayout seams, so make it the lighter
        // ground - the (30,30,30) cells then read as a grid, matching the stock Loot Tracker.
        grid.setBackground(GRID_LINE);
        grid.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, GRID_LINE)); // seam under the header
        grid.setAlignmentX(LEFT_ALIGNMENT);
        for (ItemRow ir : obtained)
        {
            grid.add(iconCell(sourceKey, kc, ir));
        }
        // Fill the trailing cells of the last row with empty (30,30,30) slots (like the stock tracker).
        for (int i = obtained.size(); i < rows * PER_ROW; i++)
        {
            JPanel filler = new JPanel();
            filler.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            grid.add(filler);
        }
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows * (CELL_H + 1) + 3));
        return grid;
    }

    private JPanel iconCell(String sourceKey, int kc, ItemRow ir)
    {
        JPanel cell = new JPanel(new BorderLayout());
        cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        cell.setPreferredSize(new Dimension(CELL_W, CELL_H));
        cell.setMaximumSize(new Dimension(CELL_W, CELL_H));

        JLabel icon = new JLabel();
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        icon.setVerticalAlignment(SwingConstants.CENTER);
        if (itemManager != null && ir.itemId > 0)
        {
            AsyncBufferedImage img = itemManager.getImage(ir.itemId);
            if (img != null)
            {
                icon.setIcon(new ImageIcon(img));
                img.onLoaded(() -> { icon.setIcon(new ImageIcon(img)); icon.repaint(); });
            }
        }
        cell.add(icon, BorderLayout.CENTER);

        int dry = dryKills(ir, kc);
        String phrase = hasObservedRate(ir, kc)
            ? LootDryness.dryOrLuckyPhrase(ir.count, kc, dry, wikiP(ir))
            : "";
        JLabel tag = new JLabel(shortQty(ir.totalQty), SwingConstants.CENTER);
        tag.setFont(FontManager.getRunescapeSmallFont());
        tag.setForeground(phrase.startsWith("top") ? LUCKY_GREEN
            : (phrase.startsWith("unluckier") || phrase.contains("dry")) ? DRY_RED : SUBTLE);
        cell.add(tag, BorderLayout.SOUTH);

        cell.setToolTipText(condensedTooltip(ir, kc));
        cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Click an item -> the source's full drop table + your odds (per-item history is one more click in).
        cell.addMouseListener(click(() -> showSourceDetail(sourceKey)));
        return cell;
    }

    /**
     * Condensed drop math for the main-list hover. Same fraction-first shape as the source-detail
     * row: your rate over the wiki rate, then the raw count and luck percentile on one line.
     */
    private String condensedTooltip(ItemRow ir, int kc)
    {
        StringBuilder sb = new StringBuilder("<html>").append(esc(ir.name == null ? "?" : ir.name));
        if (ir.totalQty > 1)
        {
            sb.append(" ×").append(ir.totalQty);
        }
        if (hasObservedRate(ir, kc))
        {
            sb.append("<br><b>").append(yourRateFraction(ir, kc)).append("</b> your rate");
        }
        else if (ir.importedOnly())
        {
            sb.append("<br>×").append(ir.totalQty).append(" obtained (imported)");
        }
        else if (kc > 0)
        {
            sb.append("<br>none in ").append(kc).append(" kills");
        }
        if (ir.wikiDenominator > 0)
        {
            sb.append("<br>").append(wikiFraction(ir)).append(" wiki");
            if (ir.wikiDrop != null)
            {
                // per-roll footnote, multi-roll rows only (empty otherwise)
                sb.append(RarityFormat.perRollNote(ir.wikiDrop));
            }
        }
        else
        {
            sb.append("<br>no wiki rate");
        }

        String tail = luckTail(ir, kc);
        StringBuilder math = new StringBuilder();
        if (hasObservedRate(ir, kc))
        {
            math.append(ir.count).append(" in ").append(kc).append(" kills");
        }
        if (!tail.isEmpty())
        {
            math.append(math.length() > 0 ? " · " : "").append(tail);
        }
        if (math.length() > 0)
        {
            sb.append("<br>").append(esc(math.toString()));
        }
        return sb.append("</html>").toString();
    }

    /**
     * True when a "1/N" observed rate and a dry/lucky verdict actually mean something: we have a
     * kill count and at least one real (non stock-import) drop event to divide by. Stock-import
     * rows collapse every drop of an item into one aggregate record, so their count is fiction.
     */
    private static boolean hasObservedRate(ItemRow ir, int kc)
    {
        return kc > 0 && ir.count > 0 && !ir.importedOnly();
    }

    /** Your observed rate as a "1/N" fraction, matching the monster drop-table lookup style. */
    private static String yourRateFraction(ItemRow ir, int kc)
    {
        return "1/" + Math.max(1, kc / Math.max(1, ir.count));
    }

    /**
     * Wiki drop rate as the shared per-kill headline ({@link RarityFormat#perKill}) - the exact
     * string the Bestiary / spreadsheet / map surfaces quote, so both sides of the loot comparison
     * are per-kill and formatted by one place.
     */
    private String wikiFraction(ItemRow ir)
    {
        if (ir.wikiDrop != null)
        {
            return RarityFormat.perKill(ir.wikiDrop);
        }
        return ir.wikiDenominator > 0 ? "1/" + ir.wikiDenominator : "no wiki rate";
    }

    /** Tier colour for the wiki rate - the same ramp the Bestiary uses; neutral grey when unknown. */
    private static Color wikiColour(ItemRow ir)
    {
        return ir.wikiDrop != null
            ? RarityFormat.perKillColor(ir.wikiDrop)
            : RarityFormat.colorForChance(wikiP(ir));
    }

    /**
     * One-line luck read for the bottom of the readout: "luckier than 99.9%" when you're ahead of
     * rate, "unluckier than 82%" when behind, "on rate" when unremarkable, "" when rate unknown.
     */
    private String luckTail(ItemRow ir, int kc)
    {
        if (kc <= 0 || ir.wikiDenominator <= 0 || ir.importedOnly())
        {
            return "";
        }
        double p = wikiP(ir);
        if (p >= 1.0)
        {
            // Guaranteed drop - no dry/lucky read (a 1/1 drop is never "unluckier than 100%").
            return "";
        }
        if (ir.count == 0)
        {
            long pct = Math.round(LootDryness.dryPercentile(p, kc));
            return pct >= 50 ? "unluckier than " + pct + "%" : "";
        }
        String phrase = LootDryness.dryOrLuckyPhrase(ir.count, kc, dryKills(ir, kc), p);
        if (phrase.startsWith("top "))
        {
            return "luckier than " + invertTopPct(phrase.substring(4));
        }
        int worse = phrase.indexOf("unluckier than ");
        if (worse >= 0)
        {
            int end = phrase.indexOf('%', worse);
            return end > worse ? phrase.substring(worse, end + 1) : "unlucky";
        }
        return "on rate";
    }

    /** "&lt;0.1%" -&gt; "99.9%", "5%" -&gt; "95%": the complement of a "top X%" luck percentile. */
    private static String invertTopPct(String topPct)
    {
        String s = topPct.trim();
        if (s.startsWith("<"))
        {
            s = s.substring(1).trim();
        }
        if (s.endsWith("%"))
        {
            s = s.substring(0, s.length() - 1);
        }
        try
        {
            double inv = 100.0 - Double.parseDouble(s.trim());
            return (Math.abs(inv - Math.rint(inv)) < 0.05)
                ? Math.round(inv) + "%"
                : String.format(Locale.ROOT, "%.1f%%", inv);
        }
        catch (NumberFormatException notANumber)
        {
            return "average";
        }
    }

    private int dryKills(ItemRow ir, int kc)
    {
        if (kc <= 0)
        {
            return 0;
        }
        if (ir.count == 0)
        {
            return ir.wikiDenominator > 0 ? kc : 0;
        }
        return ir.killCountAtLast >= 0 ? Math.max(0, kc - ir.killCountAtLast) : 0;
    }

    private static double wikiP(ItemRow ir)
    {
        return ir.wikiDenominator > 0 ? 1.0 / ir.wikiDenominator : 0.0;
    }

    // ------------------------------------------------------------------ source detail

    /** Click-through for a source: every potential drop (obtained or not) with the full drop math. */
    private void showSourceDetail(String sourceKey)
    {
        onListView = false;
        MonsterLootSummary s = null;
        for (MonsterLootSummary cand : loot.getSummaries())
        {
            if (cand.getSourceKey().equals(sourceKey))
            {
                s = cand;
                break;
            }
        }
        if (s == null)
        {
            showList();
            return;
        }

        String name = s.getSourceName() == null ? "?" : s.getSourceName();
        int kc = s.getTotalKills();
        Monster monster = monsterDatabase != null ? monsterDatabase.getMonsterByName(name) : null;

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(6, 6, 6, 6));

        JButton back = new JButton("< Back");
        styleSmallButton(back);
        back.setAlignmentX(LEFT_ALIGNMENT);
        back.addActionListener(e -> showList());
        panel.add(back);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));

        JLabel h = new JLabel(ellipsize(name, 24));
        h.setFont(FontManager.getRunescapeBoldFont());
        h.setForeground(TITLE_COLOR);
        h.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(h);

        StringBuilder meta = new StringBuilder();
        if (kc > 0)
        {
            meta.append("KC ").append(kc).append("   ");
        }
        meta.append(CombatFormat.amount(s.getTotalBestValue()));
        long span = s.getLastSeenMs() - s.getFirstSeenMs();
        if (showGpPerHour(span, s.getTotalBestValue()))
        {
            meta.append("   ").append(CombatFormat.amount(s.gpPerHour(span / 1000))).append("/hr");
        }
        panel.add(subLabel(meta.toString(), SUBTLE));

        if (monster != null && onOpenMonster != null)
        {
            final Monster m = monster;
            JLabel bst = new JLabel("View in Bestiary >");
            bst.setFont(FontManager.getRunescapeSmallFont());
            bst.setForeground(LINK_BLUE);
            bst.setAlignmentX(LEFT_ALIGNMENT);
            bst.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            bst.addMouseListener(click(() -> onOpenMonster.accept(m)));
            panel.add(bst);
        }
        panel.add(Box.createRigidArea(new Dimension(0, 6)));

        List<ItemRow> rows = buildItemRows(s, monster, null);
        final int kcSafe = Math.max(1, kc);
        rows.sort(Comparator.comparingInt((ItemRow r) -> r.count > 0 ? 0 : 1)
            .thenComparingDouble(r -> r.count > 0
                ? -r.count
                : (r.wikiDenominator > 0 ? -LootDryness.dryPercentile(wikiP(r), kcSafe) : 1e9)));

        if (rows.isEmpty())
        {
            panel.add(subLabel("No drop table on record for this source.", SUBTLE));
        }
        for (ItemRow r : rows)
        {
            panel.add(dropDetailRow(sourceKey, r, kc));
            panel.add(Box.createRigidArea(new Dimension(0, 2)));
        }

        setBody(panel);
    }

    /**
     * One drop in the source-detail view. Fraction-first readout: your rate (bold, luck-coloured)
     * over the wiki rate, then a hairline and one small line with the raw count and luck percentile.
     */
    private JPanel dropDetailRow(String sourceKey, ItemRow ir, int kc)
    {
        JPanel rowP = new JPanel(new BorderLayout(4, 0));
        rowP.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        rowP.setAlignmentX(LEFT_ALIGNMENT);
        rowP.setBorder(new EmptyBorder(3, 3, 3, 3));
        rowP.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));

        JLabel icon = new JLabel();
        icon.setPreferredSize(new Dimension(32, 32));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        if (itemManager != null && ir.itemId > 0)
        {
            AsyncBufferedImage img = itemManager.getImage(ir.itemId);
            if (img != null)
            {
                icon.setIcon(new ImageIcon(img));
                img.onLoaded(() -> { icon.setIcon(new ImageIcon(img)); icon.repaint(); });
            }
        }
        icon.setEnabled(ir.count > 0);
        rowP.add(icon, BorderLayout.WEST);

        JPanel txt = new JPanel();
        txt.setLayout(new BoxLayout(txt, BoxLayout.Y_AXIS));
        txt.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Line 1: item name + quantity obtained ("Blood rune ×42").
        String rawName = ir.name == null ? "Item " + ir.itemId : ir.name;
        JLabel nm = new JLabel(ir.count > 0
            ? ellipsize(rawName, 17) + " ×" + shortQty(ir.totalQty)
            : ellipsize(rawName, 22));
        nm.setFont(FontManager.getRunescapeSmallFont());
        nm.setForeground(ir.count > 0 ? Color.WHITE : SUBTLE);
        nm.setAlignmentX(LEFT_ALIGNMENT);
        txt.add(nm);

        Color luckColor = luckColour(ir, kc);

        // Line 2 (primary): your rate as a fraction - bold, luck-coloured.
        if (hasObservedRate(ir, kc))
        {
            txt.add(fracLine(yourRateFraction(ir, kc), "your rate",
                FontManager.getRunescapeBoldFont(), luckColor));
        }
        else if (ir.importedOnly())
        {
            JLabel imp = new JLabel("×" + shortQty(ir.totalQty) + " obtained (imported)");
            imp.setFont(FontManager.getRunescapeSmallFont());
            imp.setForeground(SUBTLE);
            imp.setAlignmentX(LEFT_ALIGNMENT);
            txt.add(imp);
        }
        else if (kc > 0)
        {
            JLabel none = new JLabel("none in " + kc + " kills");
            none.setFont(FontManager.getRunescapeSmallFont());
            none.setForeground(luckColor);
            none.setAlignmentX(LEFT_ALIGNMENT);
            txt.add(none);
        }

        // Line 3: wiki rate as the Bestiary drop-table fraction, in the Bestiary's tier colour.
        if (ir.wikiDenominator > 0)
        {
            txt.add(fracLine(wikiFraction(ir), "wiki", FontManager.getRunescapeSmallFont(), wikiColour(ir)));
        }
        else
        {
            JLabel wna = new JLabel("no wiki rate");
            wna.setFont(FontManager.getRunescapeSmallFont());
            wna.setForeground(SUBTLE);
            wna.setAlignmentX(LEFT_ALIGNMENT);
            txt.add(wna);
        }
        rowP.add(txt, BorderLayout.CENTER);

        // Footer (full row width): hairline + raw count and luck percentile, small and subdued.
        String tail = luckTail(ir, kc);
        StringBuilder math = new StringBuilder();
        if (hasObservedRate(ir, kc))
        {
            math.append(ir.count).append(" in ").append(kc).append(" kills");
        }
        if (!tail.isEmpty())
        {
            math.append(math.length() > 0 ? " · " : "").append(tail);
        }
        if (math.length() > 0)
        {
            JPanel foot = new JPanel();
            foot.setLayout(new BoxLayout(foot, BoxLayout.Y_AXIS));
            foot.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            foot.add(hairline());
            foot.add(Box.createRigidArea(new Dimension(0, 1)));
            JLabel ml = new JLabel(math.toString());
            ml.setFont(FontManager.getRunescapeSmallFont());
            ml.setForeground(SUBTLE);
            ml.setAlignmentX(LEFT_ALIGNMENT);
            foot.add(ml);
            rowP.add(foot, BorderLayout.SOUTH);
        }

        if (ir.count > 0)
        {
            rowP.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            rowP.addMouseListener(click(() -> showHistory(sourceKey, ir.itemId, ir.name, kc, ir.wikiDenominator,
                () -> showSourceDetail(sourceKey))));
        }
        return rowP;
    }

    /** A "1/N  label" line: the fraction in {@code fracFont}/{@code fracColor}, the label small + grey. */
    private static JPanel fracLine(String frac, String label, Font fracFont, Color fracColor)
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 15));

        JLabel f = new JLabel(frac);
        f.setFont(fracFont);
        f.setForeground(fracColor);

        JLabel l = new JLabel("  " + label);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(SUBTLE);

        p.add(f);
        p.add(l);
        p.add(Box.createHorizontalGlue());
        return p;
    }

    /** Full-width 1px separator for the source-detail readout. */
    private static JPanel hairline()
    {
        JPanel p = new JPanel();
        p.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setPreferredSize(new Dimension(120, 1));
        p.setMinimumSize(new Dimension(16, 1));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return p;
    }

    /** Luck-signal colour for the primary line: green ahead of rate, red when dry, white otherwise. */
    private Color luckColour(ItemRow ir, int kc)
    {
        if (ir.importedOnly())
        {
            // Aggregate import - the drop-event count is always 1, so no dry/lucky verdict.
            return Color.WHITE;
        }
        if (kc <= 0 || ir.wikiDenominator <= 0 || wikiP(ir) >= 1.0)
        {
            // No rate, or a guaranteed 1/1 drop - never paint a dry/lucky verdict.
            return ir.count > 0 ? Color.WHITE : SUBTLE;
        }
        double p = wikiP(ir);
        if (ir.count == 0)
        {
            return LootDryness.dryPercentile(p, kc) >= 60 ? DRY_RED : SUBTLE;
        }
        String phrase = LootDryness.dryOrLuckyPhrase(ir.count, kc, dryKills(ir, kc), p);
        if (phrase.startsWith("top "))
        {
            return LUCKY_GREEN;
        }
        if (phrase.startsWith("unluckier") || phrase.contains("dry"))
        {
            return DRY_RED;
        }
        return Color.WHITE;
    }

    // ------------------------------------------------------------------ item history

    private void showHistory(String sourceKey, int itemId, String itemName, int kc, int wikiDenom, Runnable onBack)
    {
        onListView = false;
        List<LootRecord> recs = loot.getRecordsForItem(sourceKey, itemId);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(6, 6, 6, 6));

        JButton back = new JButton("< Back");
        styleSmallButton(back);
        back.setAlignmentX(LEFT_ALIGNMENT);
        back.addActionListener(e -> onBack.run());
        panel.add(back);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));

        JLabel h = new JLabel(ellipsize(itemName == null ? "?" : itemName, 26));
        h.setFont(FontManager.getRunescapeBoldFont());
        h.setForeground(TITLE_COLOR);
        h.setAlignmentX(LEFT_ALIGNMENT);
        if (itemName != null && !itemName.equals(h.getText()))
        {
            h.setToolTipText(itemName);
        }
        panel.add(h);

        int count = recs.size();
        int lastKc = recs.isEmpty() ? -1 : recs.get(recs.size() - 1).getKillCountAtDrop();
        // Stock-import rows fold every drop of an item into one aggregate record - no real per-drop
        // timeline, so no "you 1/N" and no dry/lucky verdict from them.
        boolean importedOnly = !recs.isEmpty() && recs.stream().allMatch(LootRecord::isImported);

        StringBuilder l1 = new StringBuilder();
        if (importedOnly)
        {
            long qty = 0;
            for (LootRecord r : recs)
            {
                for (LootItem it : r.getItems())
                {
                    if (it.getItemId() == itemId)
                    {
                        qty += it.getQuantity();
                    }
                }
            }
            l1.append("×").append(qty).append(" obtained (imported)");
            if (kc > 0)
            {
                l1.append(" in ").append(kc).append(" KC");
            }
        }
        else
        {
            l1.append(count).append(count == 1 ? " drop" : " drops");
            if (kc > 0)
            {
                l1.append(" in ").append(kc).append(" KC");
                if (count > 0)
                {
                    l1.append("  you 1/").append(Math.max(1, kc / count));
                }
            }
        }
        panel.add(subLabel(l1.toString(), SUBTLE));

        if (wikiDenom > 0)
        {
            double p = 1.0 / wikiDenom;
            panel.add(subLabel("wiki 1/" + wikiDenom + "  expect every ~" + LootDryness.expectedKills(p),
                RarityFormat.colorForChance(p)));
            if (kc > 0 && !importedOnly)
            {
                int dk = (count > 0 && lastKc >= 0) ? Math.max(0, kc - lastKc) : kc;
                String phr = LootDryness.dryOrLuckyPhrase(count, kc, dk, p);
                if (!phr.isEmpty() && !LootDryness.ALWAYS_DROPS.equals(phr))
                {
                    Color c = phr.startsWith("top") ? LUCKY_GREEN
                        : (phr.startsWith("unluckier") || phr.contains("dry")) ? DRY_RED : SUBTLE;
                    panel.add(subLabel(phr, c));
                }
                if (count > 1)
                {
                    panel.add(subLabel(count + " drops here vs ~" + oneDp(kc * p) + " expected", SUBTLE));
                }
            }
        }
        if (kc > 0 && lastKc >= 0 && count > 0 && !importedOnly)
        {
            panel.add(subLabel(Math.max(0, kc - lastKc) + " kills since the last one", SUBTLE));
        }
        panel.add(Box.createRigidArea(new Dimension(0, 6)));

        for (int i = recs.size() - 1; i >= 0; i--)
        {
            LootRecord rec = recs.get(i);
            int qty = 0;
            long val = 0;
            for (LootItem it : rec.getItems())
            {
                if (it.getItemId() == itemId)
                {
                    qty += it.getQuantity();
                    val += it.bestValueTotal();
                }
            }
            StringBuilder top = new StringBuilder(STAMP.format(Instant.ofEpochMilli(rec.getTimestampEpochMs())));
            if (rec.getKillCountAtDrop() >= 0)
            {
                top.append("  KC ").append(rec.getKillCountAtDrop());
            }
            top.append("  x").append(qty);
            JLabel t = subLabel(top.toString(), Color.WHITE);
            t.setBorder(new EmptyBorder(2, 2, 0, 2));
            panel.add(t);

            StringBuilder bot = new StringBuilder(CombatFormat.amount(val));
            if (rec.getSlayerTaskName() != null)
            {
                bot.append("  task: ").append(rec.getSlayerTaskName());
            }
            if (rec.isInstanced())
            {
                bot.append("  instance");
            }
            if (rec.isImported())
            {
                bot.append("  (imported)");
            }
            JLabel b = subLabel(bot.toString(), SUBTLE);
            b.setBorder(new EmptyBorder(0, 2, 2, 2));
            panel.add(b);
        }

        setBody(panel);
    }

    // ------------------------------------------------------------------ export

    private void doExport()
    {
        if (loot == null || loot.getRecordCount() == 0)
        {
            summaryLabel.setText("Nothing to export yet.");
            return;
        }
        try
        {
            java.io.File dir = com.osrscopilot.util.CopilotPaths.dataSubDir("exports");
            if (!dir.exists() && !dir.mkdirs())
            {
                summaryLabel.setText("Could not create the exports folder.");
                return;
            }
            String who = loot.getActiveCharacterName() == null || loot.getActiveCharacterName().isEmpty()
                ? "loot" : loot.getActiveCharacterName().replaceAll("[^A-Za-z0-9_-]", "_");
            java.io.File out = new java.io.File(dir, "loot-" + who + "-"
                + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())
                    .format(Instant.now()) + ".csv");
            try (java.io.Writer w = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(out), java.nio.charset.StandardCharsets.UTF_8))
            {
                loot.writeCsv(w);
            }
            summaryLabel.setText("Exported to " + out.getName());
        }
        catch (Exception ex)
        {
            summaryLabel.setText("Export failed.");
        }
    }

    // ------------------------------------------------------------------ view swap

    private void showList()
    {
        onListView = true;
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrap.add(listContainer, BorderLayout.NORTH);
        setBody(wrap);
    }

    private void setBody(JPanel content)
    {
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        body.removeAll();
        body.add(sp, BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
    }

    // ------------------------------------------------------------------ item-row model

    private List<ItemRow> buildItemRows(MonsterLootSummary s, Monster monster, List<LootRecord> filteredRecs)
    {
        java.util.LinkedHashMap<Integer, ItemRow> rows = new java.util.LinkedHashMap<>();

        if (monster != null && monster.getDrops() != null)
        {
            for (MonsterDrop d : monster.getDrops())
            {
                if (d.getItemId() <= 0)
                {
                    continue;
                }
                ItemRow row = rows.computeIfAbsent(d.getItemId(), k -> new ItemRow());
                row.itemId = d.getItemId();
                row.name = d.getName();
                row.wikiDenominator = d.getPerKillDenominator();
                row.wikiDrop = d;
            }
        }

        if (filteredRecs != null)
        {
            for (LootRecord rec : filteredRecs)
            {
                for (LootItem it : rec.getItems())
                {
                    ItemRow row = rows.computeIfAbsent(it.getItemId(), k -> new ItemRow());
                    row.itemId = it.getItemId();
                    if (row.name == null && it.getName() != null && !it.getName().isEmpty())
                    {
                        row.name = it.getName();
                    }
                    row.count++;
                    if (!rec.isImported())
                    {
                        row.realCount++;
                    }
                    row.totalQty += it.getQuantity();
                    long ts = rec.getTimestampEpochMs();
                    if (row.firstSeenMs == 0 || ts < row.firstSeenMs)
                    {
                        row.firstSeenMs = ts;
                        row.killCountAtFirst = rec.getKillCountAtDrop();
                    }
                    if (ts >= row.lastSeenMs)
                    {
                        row.lastSeenMs = ts;
                        row.killCountAtLast = rec.getKillCountAtDrop();
                    }
                }
            }
        }
        else
        {
            for (java.util.Map.Entry<Integer, ItemStat> e : s.getItemStats().entrySet())
            {
                ItemStat st = e.getValue();
                ItemRow row = rows.computeIfAbsent(e.getKey(), k -> new ItemRow());
                row.itemId = e.getKey();
                if (row.name == null && st.getItemName() != null && !st.getItemName().isEmpty())
                {
                    row.name = st.getItemName();
                }
                row.count = st.getCount();
                row.realCount = st.getRealCount();
                row.totalQty = st.getTotalQty();
                row.killCountAtFirst = st.getKillCountAtFirst();
                row.killCountAtLast = st.getKillCountAtLast();
                row.firstSeenMs = st.getFirstSeenMs();
                row.lastSeenMs = st.getLastSeenMs();
            }
        }

        for (ItemRow r : rows.values())
        {
            if (r.name == null || r.name.isEmpty())
            {
                // Real name comes from LootItem.name / ItemStat.itemName, backfilled on the client
                // thread (LootTrackerManager.resolveMissingNames) - never resolve it here on the EDT
                // (ItemManager.getItemComposition asserts client-thread-only).
                r.name = "Item " + r.itemId;
            }
        }

        List<ItemRow> list = new ArrayList<>(rows.values());
        list.sort(Comparator
            .comparingInt((ItemRow r) -> r.count > 0 ? 0 : 1)
            .thenComparingInt(r -> r.wikiDenominator > 0 ? r.wikiDenominator : Integer.MAX_VALUE)
            .thenComparing(r -> r.name == null ? "" : r.name));
        return list;
    }

    private List<LootRecord> recordsFor(String sourceKey, long sinceMs)
    {
        List<LootRecord> out = new ArrayList<>();
        for (LootRecord r : loot.getAllRecords())
        {
            if (r.sourceKey().equals(sourceKey) && r.getTimestampEpochMs() >= sinceMs)
            {
                out.add(r);
            }
        }
        return out;
    }

    private static long sumValue(List<LootRecord> recs)
    {
        long v = 0;
        for (LootRecord r : recs)
        {
            v += r.totalBestValue();
        }
        return v;
    }

    // ------------------------------------------------------------------ tiny UI helpers

    private static void styleField(JTextField f)
    {
        f.setFont(FontManager.getRunescapeFont());
        f.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        f.setForeground(Color.WHITE);
        f.setCaretColor(Color.WHITE);
        f.setAlignmentX(LEFT_ALIGNMENT);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
    }

    private static void styleSmallButton(JButton b)
    {
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setMargin(new java.awt.Insets(1, 4, 1, 4));
    }

    private static JComboBox<String> compactCombo(JComboBox<String> c)
    {
        c.setFont(FontManager.getRunescapeSmallFont());
        c.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        c.setForeground(Color.WHITE);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        c.setPreferredSize(new Dimension(90, 22));
        return c;
    }

    private static JPanel filterRow(Component a, Component b)
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBackground(ColorScheme.DARK_GRAY_COLOR);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        p.add(a);
        p.add(Box.createRigidArea(new Dimension(4, 0)));
        p.add(b);
        p.add(Box.createHorizontalGlue());
        return p;
    }

    private static JLabel subLabel(String text, Color fg)
    {
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(fg);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 15));
        return l;
    }

    private static void addPlain(JPanel c, String text)
    {
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(SUBTLE);
        l.setBorder(new EmptyBorder(10, 6, 6, 6));
        l.setAlignmentX(LEFT_ALIGNMENT);
        c.add(l);
    }

    private static String ellipsize(String s, int max)
    {
        if (s == null || s.length() <= max)
        {
            return s;
        }
        return s.substring(0, Math.max(1, max - 2)).trim() + "..";
    }

    private static String shortQty(long q)
    {
        if (q < 1000)
        {
            return Long.toString(q);
        }
        if (q < 1_000_000)
        {
            return (q / 1000) + "k";
        }
        return (q / 1_000_000) + "M";
    }

    private static String oneDp(double v)
    {
        return String.format("%.1f", v);
    }

    private static String esc(String s)
    {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static MouseAdapter click(Runnable r)
    {
        return new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                r.run();
            }
        };
    }

    private static final class ItemRow
    {
        int itemId;
        String name;
        int count;
        int realCount;      // drop events from live capture; < count means stock-import rows are folded in
        long totalQty;
        int wikiDenominator;
        MonsterDrop wikiDrop;
        int killCountAtFirst = -1;
        int killCountAtLast = -1;
        long firstSeenMs;
        long lastSeenMs;

        /** Only present via the stock Loot Tracker import - no real per-drop timeline, so no "1/N" or luck read. */
        boolean importedOnly()
        {
            return count > 0 && realCount == 0;
        }
    }
}
