package com.osrscopilot.combat.ui;

import com.osrscopilot.combat.CombatFormat;
import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.model.EncounterSegment;
import com.osrscopilot.combat.model.EntityCombatStats;
import com.osrscopilot.combat.model.SegmentType;
import com.osrscopilot.combat.model.WeaponAbilityEntry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * A floating, resizable per-source damage-detail window. Left: the scope's damage sources ranked
 * with bars. Right: the selected source's full breakdown - hits / attempts / accuracy,
 * min-avg-max, the special-attack split, a hit-size distribution, and how its damage divided
 * across each enemy it hit.
 */
public class SourceDetailsWindow extends JFrame
{
    private static final Color BG = ColorScheme.DARK_GRAY_COLOR;
    private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color GOLD = new Color(255, 202, 40);
    private static final Color STEEL = new Color(120, 144, 156);
    private static final Color DIM = ColorScheme.LIGHT_GRAY_COLOR;

    private final transient CombatEncounterManager mgr;
    private final transient ItemManager itemManager;
    // source name -> resolved item id (>0), or 0 = "no item, use a painted glyph / nothing".
    private final transient Map<String, Integer> iconIds = new HashMap<>();

    private final JComboBox<EncounterSegment> scopeBox = new JComboBox<>();
    private final JComboBox<String> memberBox = new JComboBox<>();
    private final JPanel memberRow = new JPanel(new BorderLayout());
    private final JPanel sourceList = new JPanel();
    private final JPanel detail = new JPanel();
    private JScrollPane leftScroll;
    private JScrollPane rightScroll;

    private String selectedSource;
    private boolean updatingBoxes;
    private final transient javax.swing.Timer live;

    // In-place refresh state: the left list and the detail pane are only torn down when their
    // structure changes, so a live tick just updates numbers and doesn't flicker.
    private final java.util.LinkedHashMap<String, JPanel> rowWraps = new java.util.LinkedHashMap<>();
    private final Map<String, MeterRowPanel> rowMeters = new HashMap<>();
    private List<String> lastOrder;
    private String lastEntityKey = "";
    private String lastDetailSig = "";
    // name -> a finished, scaled icon (so we resolve + scale once, not every tick).
    private final transient Map<String, javax.swing.Icon> iconReady = new HashMap<>();

    public SourceDetailsWindow(CombatEncounterManager mgr, ItemManager itemManager)
    {
        super("Damage Sources");
        this.mgr = mgr;
        this.itemManager = itemManager;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(560, 420);
        setMinimumSize(new Dimension(440, 320));

        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(6, 6, 6, 6));

        root.add(buildTopBar(), BorderLayout.NORTH);

        sourceList.setLayout(new BoxLayout(sourceList, BoxLayout.Y_AXIS));
        sourceList.setBackground(BG);
        leftScroll = new JScrollPane(sourceList,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        leftScroll.setBorder(BorderFactory.createLineBorder(CARD));
        leftScroll.getVerticalScrollBar().setUnitIncrement(16);
        leftScroll.setPreferredSize(new Dimension(230, 10));

        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.setBackground(BG);
        rightScroll = new JScrollPane(detail,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        rightScroll.setBorder(BorderFactory.createLineBorder(CARD));
        rightScroll.getVerticalScrollBar().setUnitIncrement(16);

        javax.swing.JSplitPane split = new javax.swing.JSplitPane(
            javax.swing.JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
        split.setResizeWeight(0.42);
        split.setBorder(null);
        root.add(split, BorderLayout.CENTER);

        setContentPane(root);

        // Refresh from live stats while the window is open. rebuild() updates in place, so this
        // is cheap and doesn't flicker unless the source list or the selected source changes.
        this.live = new javax.swing.Timer(750, e -> rebuild());
        this.live.setRepeats(true);
    }

    private JPanel buildTopBar()
    {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(BG);

        JPanel scopeRow = new JPanel(new BorderLayout(4, 0));
        scopeRow.setBackground(BG);
        JLabel l = new JLabel("Scope ");
        l.setForeground(DIM);
        l.setFont(FontManager.getRunescapeSmallFont());
        scopeRow.add(l, BorderLayout.WEST);
        scopeBox.setFont(FontManager.getRunescapeSmallFont());
        scopeBox.setRenderer(new javax.swing.DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object v,
                int i, boolean s, boolean f)
            {
                super.getListCellRendererComponent(list, v, i, s, f);
                if (v instanceof EncounterSegment)
                {
                    setText(((EncounterSegment) v).toString());
                }
                setFont(FontManager.getRunescapeSmallFont());
                return this;
            }
        });
        scopeBox.addActionListener(e -> { if (!updatingBoxes) forceRebuild(); });
        scopeRow.add(scopeBox, BorderLayout.CENTER);
        bar.add(scopeRow);

        memberRow.setBackground(BG);
        JLabel ml = new JLabel("Player ");
        ml.setForeground(DIM);
        ml.setFont(FontManager.getRunescapeSmallFont());
        memberRow.add(ml, BorderLayout.WEST);
        memberBox.setFont(FontManager.getRunescapeSmallFont());
        memberBox.addActionListener(e -> { if (!updatingBoxes) forceRebuild(); });
        memberRow.add(memberBox, BorderLayout.CENTER);
        memberRow.setVisible(false);
        bar.add(Box.createVerticalStrut(3));
        bar.add(memberRow);

        return bar;
    }

    /**
     * Show the window and point it at {@code preferredScope} (falling back to the first scope) and,
     * when that scope is a GROUP, at {@code preferredEntity} if it is one of its participants.
     */
    public void showFor(EncounterSegment preferredScope, EntityCombatStats preferredEntity)
    {
        SwingUtilities.invokeLater(() ->
        {
            refreshScopeBox(preferredScope);
            EncounterSegment scope = currentScope();
            if (preferredEntity != null && scope != null && scope.getSegmentType() == SegmentType.GROUP
                && preferredEntity.getName() != null)
            {
                syncMemberBox(scope);
                if (((DefaultComboBoxModel<String>) memberBox.getModel()).getIndexOf(preferredEntity.getName()) >= 0)
                {
                    updatingBoxes = true;
                    memberBox.setSelectedItem(preferredEntity.getName());
                    updatingBoxes = false;
                }
            }
            selectedSource = null; // let rebuild pick the top source for the (possibly new) entity
            forceRebuild();
            if (!isVisible())
            {
                setLocationRelativeTo(null);
                setVisible(true);
            }
            live.start();
            toFront();
            requestFocus();
        });
    }

    @Override
    public void dispose()
    {
        if (live != null)
        {
            live.stop();
        }
        super.dispose();
    }

    /** Test hook: populate the scope box and render once, without showing the window. */
    void renderOnceForTest()
    {
        refreshScopeBox(null);
        forceRebuild();
    }

    /** Test hook: a live tick (the in-place refresh path, no forced teardown). */
    void tickForTest()
    {
        rebuild();
    }

    java.awt.Component sourceRowComponentForTest(int i)
    {
        return sourceList.getComponent(i);
    }

    int sourceRowCountForTest()
    {
        return sourceList.getComponentCount();
    }

    private void refreshScopeBox(EncounterSegment preferred)
    {
        updatingBoxes = true;
        List<EncounterSegment> segs = mgr.getAllSegmentsForDropdown();
        DefaultComboBoxModel<EncounterSegment> m = new DefaultComboBoxModel<>();
        for (EncounterSegment s : segs)
        {
            m.addElement(s);
        }
        scopeBox.setModel(m);
        EncounterSegment pick = (preferred != null && segs.contains(preferred)) ? preferred
            : (segs.isEmpty() ? null : segs.get(0));
        if (pick != null)
        {
            scopeBox.setSelectedItem(pick);
        }
        updatingBoxes = false;
    }

    private EncounterSegment currentScope()
    {
        Object s = scopeBox.getSelectedItem();
        return s instanceof EncounterSegment ? (EncounterSegment) s : null;
    }

    private EntityCombatStats currentEntity(EncounterSegment scope)
    {
        if (scope == null)
        {
            return null;
        }
        if (scope.getSegmentType() == SegmentType.GROUP)
        {
            String who = (String) memberBox.getSelectedItem();
            if (who != null)
            {
                for (EntityCombatStats p : scope.getRankedParticipants(64))
                {
                    if (who.equals(p.getName()))
                    {
                        return p;
                    }
                }
            }
        }
        return scope.getLocalPlayerStats();
    }

    private void syncMemberBox(EncounterSegment scope)
    {
        boolean group = scope != null && scope.getSegmentType() == SegmentType.GROUP;
        memberRow.setVisible(group);
        if (!group)
        {
            return;
        }
        updatingBoxes = true;
        String prev = (String) memberBox.getSelectedItem();
        DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
        for (EntityCombatStats p : scope.getRankedParticipants(64))
        {
            m.addElement(p.getName());
        }
        memberBox.setModel(m);
        if (prev != null && ((DefaultComboBoxModel<String>) memberBox.getModel()).getIndexOf(prev) >= 0)
        {
            memberBox.setSelectedItem(prev);
        }
        updatingBoxes = false;
    }

    /** A user action changed the structure - force a full teardown on the next rebuild. */
    private void forceRebuild()
    {
        lastOrder = null;
        lastDetailSig = "";
        rebuild();
    }

    private void rebuild()
    {
        EncounterSegment scope = currentScope();
        syncMemberBox(scope);
        EntityCombatStats ent = currentEntity(scope);

        String entityKey = (scope == null ? "-" : scope.getSegmentType() + "/" + System.identityHashCode(scope))
            + "|" + (ent == null ? "-" : ent.getName());
        if (!entityKey.equals(lastEntityKey))
        {
            lastOrder = null;             // different fight / player -> full rebuild
            lastDetailSig = "";
            lastEntityKey = entityKey;
        }

        if (ent == null)
        {
            if (lastOrder != null || sourceList.getComponentCount() == 0)
            {
                sourceList.removeAll();
                rowWraps.clear();
                rowMeters.clear();
                sourceList.add(hint("No fight data yet."));
                detail.removeAll();
                lastOrder = new java.util.ArrayList<>();
                lastDetailSig = "empty";
                revalidateBoth();
            }
            return;
        }

        List<WeaponAbilityEntry> sources = ent.getWeaponBreakdown();
        long scopeTotal = Math.max(1, ent.getTotalDamage());
        double dur = Math.max(1, ent.getDurationSeconds());
        long top = sources.isEmpty() ? 1 : Math.max(1, sources.get(0).getTotalDamage());

        if (selectedSource == null || sources.stream().noneMatch(s -> s.getWeaponName().equals(selectedSource)))
        {
            selectedSource = sources.isEmpty() ? null : sources.get(0).getWeaponName();
            lastDetailSig = "";
        }

        List<String> order = new java.util.ArrayList<>();
        for (WeaponAbilityEntry s : sources)
        {
            order.add(s.getWeaponName());
        }

        if (!order.equals(lastOrder))
        {
            // Structure changed: rebuild the left list once.
            sourceList.removeAll();
            rowWraps.clear();
            rowMeters.clear();
            if (sources.isEmpty())
            {
                sourceList.add(hint("No per-source damage recorded for this scope."));
            }
            else
            {
                for (WeaponAbilityEntry s : sources)
                {
                    sourceList.add(buildSourceRow(s, scopeTotal, top));
                }
            }
            lastOrder = order;
            sourceList.revalidate();
            sourceList.repaint();
        }
        else
        {
            // Same rows: just refresh numbers + selection highlight in place.
            for (WeaponAbilityEntry s : sources)
            {
                MeterRowPanel m = rowMeters.get(s.getWeaponName());
                if (m != null)
                {
                    boolean sel = s.getWeaponName().equals(selectedSource);
                    m.update(s.getWeaponName(),
                        CombatFormat.amount(s.getTotalDamage()) + "  " + pct(s.getTotalDamage(), scopeTotal),
                        s.getHitCount() + " hits  |  avg " + s.getAvgHit(),
                        top > 0 ? (double) s.getTotalDamage() / top : 0,
                        sel ? GOLD : STEEL);
                }
                JPanel wrap = rowWraps.get(s.getWeaponName());
                if (wrap != null)
                {
                    boolean sel = s.getWeaponName().equals(selectedSource);
                    wrap.setBorder(sel ? BorderFactory.createMatteBorder(0, 2, 0, 0, GOLD) : new EmptyBorder(0, 2, 0, 0));
                }
            }
            sourceList.repaint();
        }

        // Detail pane: rebuild only when the selected source's numbers actually changed.
        WeaponAbilityEntry sel = sources.stream()
            .filter(s -> s.getWeaponName().equals(selectedSource)).findFirst().orElse(null);
        String detailSig = sel == null ? "none"
            : sel.getWeaponName() + "|" + sel.getTotalDamage() + "|" + sel.getHitCount() + "|"
              + sel.getAttempts() + "|" + sel.getSpecHits() + "|" + sel.getMaxHit() + "|"
              + sel.getMinHit() + "|" + sel.getTargetBreakdown().size() + "|" + ((int) dur);
        if (!detailSig.equals(lastDetailSig))
        {
            lastDetailSig = detailSig;
            int scrollY = rightScroll.getVerticalScrollBar().getValue();
            detail.removeAll();
            if (sel != null)
            {
                buildDetail(sel, scopeTotal, dur);
            }
            detail.revalidate();
            detail.repaint();
            SwingUtilities.invokeLater(() -> rightScroll.getVerticalScrollBar().setValue(scrollY));
        }
    }

    private void revalidateBoth()
    {
        sourceList.revalidate();
        sourceList.repaint();
        detail.revalidate();
        detail.repaint();
    }

    private JPanel buildSourceRow(WeaponAbilityEntry s, long scopeTotal, long top)
    {
        boolean sel = s.getWeaponName().equals(selectedSource);
        MeterRowPanel row = new MeterRowPanel(
            s.getWeaponName(),
            CombatFormat.amount(s.getTotalDamage()) + "  " + pct(s.getTotalDamage(), scopeTotal),
            s.getHitCount() + " hits  |  avg " + s.getAvgHit(),
            top > 0 ? (double) s.getTotalDamage() / top : 0,
            sel ? GOLD : STEEL);

        JPanel wrap = new JPanel(new BorderLayout(2, 0));
        wrap.setOpaque(false);
        wrap.setBorder(sel ? BorderFactory.createMatteBorder(0, 2, 0, 0, GOLD) : new EmptyBorder(0, 2, 0, 0));
        JLabel icon = iconLabel(s.getWeaponName());
        icon.setPreferredSize(new Dimension(SourceIcons.SIZE + 4, SourceIcons.SIZE));
        wrap.add(icon, BorderLayout.WEST);
        wrap.add(row, BorderLayout.CENTER);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 4));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        final String key = s.getWeaponName();
        java.awt.event.MouseAdapter pick = new java.awt.event.MouseAdapter()
        {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e)
            {
                selectedSource = key;
                lastDetailSig = "";
                rebuild();
            }
        };
        row.addMouseListener(pick);
        wrap.addMouseListener(pick);

        rowWraps.put(key, wrap);
        rowMeters.put(key, row);
        return wrap;
    }

    /**
     * An icon for a source: a painted glyph for status / unarmed, else the resolved item image.
     * Each name's finished icon is cached, so a live tick reuses it rather than re-fetching (which
     * is what made the icons pop / shift).
     */
    private JLabel iconLabel(String name)
    {
        JLabel l = new JLabel();
        l.setHorizontalAlignment(JLabel.CENTER);

        javax.swing.Icon ready = iconReady.get(name);
        if (ready != null)
        {
            l.setIcon(ready);
            return l;
        }

        BufferedImage glyph = SourceIcons.forName(name);
        if (glyph != null)
        {
            javax.swing.Icon ic = new ImageIcon(glyph);
            iconReady.put(name, ic);
            l.setIcon(ic);
            return l;
        }

        int id = resolveItemId(name);
        if (id > 0 && itemManager != null)
        {
            AsyncBufferedImage img = itemManager.getImage(id);
            img.onLoaded(() ->
            {
                javax.swing.Icon ic = new ImageIcon(
                    img.getScaledInstance(SourceIcons.SIZE + 2, SourceIcons.SIZE + 2, Image.SCALE_SMOOTH));
                iconReady.put(name, ic);
                l.setIcon(ic);
            });
        }
        return l;
    }

    private int resolveItemId(String name)
    {
        if (itemManager == null || name == null || name.isEmpty())
        {
            return 0;
        }
        Integer cached = iconIds.get(name);
        if (cached != null)
        {
            return cached;
        }
        int id = 0;
        try
        {
            java.util.List<net.runelite.http.api.item.ItemPrice> hits = itemManager.search(name);
            for (net.runelite.http.api.item.ItemPrice p : hits)
            {
                if (p.getName() != null && p.getName().equalsIgnoreCase(name))
                {
                    id = p.getId();
                    break;
                }
            }
            if (id == 0 && !hits.isEmpty())
            {
                id = hits.get(0).getId();
            }
        }
        catch (RuntimeException ignored)
        {
            // no price data / not an item - fall through to "no icon"
        }
        iconIds.put(name, id);
        return id;
    }

    private void buildDetail(WeaponAbilityEntry s, long scopeTotal, double dur)
    {
        double srcDps = s.getTotalDamage() / dur;

        JLabel head = new JLabel(s.getWeaponName());
        head.setFont(FontManager.getRunescapeBoldFont());
        head.setForeground(GOLD);
        JPanel headRow = new JPanel(new BorderLayout(4, 0));
        headRow.setBackground(BG);
        headRow.setBorder(new EmptyBorder(2, 2, 4, 2));
        headRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        JLabel hi = iconLabel(s.getWeaponName());
        hi.setPreferredSize(new Dimension(SourceIcons.SIZE + 4, SourceIcons.SIZE + 2));
        headRow.add(hi, BorderLayout.WEST);
        headRow.add(head, BorderLayout.CENTER);
        detail.add(headRow);

        stat("Damage", CombatFormat.amount(s.getTotalDamage()) + "   (" + pct(s.getTotalDamage(), scopeTotal) + " of scope)");
        stat("DPS", String.format("%.1f", srcDps));
        stat("Attempts / Hits", s.getAttempts() + " / " + s.getHitCount());
        if (s.getAttempts() > 0)
        {
            stat("Accuracy", String.format("%.1f%%  (%d landed)", s.getAccuracyPct(), s.getLandedAttempts()));
        }
        stat("Min / Avg / Max", s.getMinHit() + " / " + s.getAvgHit() + " / " + s.getMaxHit());

        if (s.hasSpecial())
        {
            sub("SPECIAL vs NORMAL");
            detail.add(splitTable(s, dur));
        }

        int[] hist = s.getHistogram();
        if (s.getHitCount() > 0 && s.getMaxHit() > 0)
        {
            sub("HIT SIZES");
            detail.add(histogram(hist, s.getMaxHit(), s.getHitCount()));
        }

        Map<String, long[]> byTarget = s.getTargetBreakdown();
        if (!byTarget.isEmpty())
        {
            sub("TARGETS");
            long tMax = byTarget.values().stream().mapToLong(v -> v[0]).max().orElse(1);
            for (Map.Entry<String, long[]> e : byTarget.entrySet())
            {
                long dmg = e.getValue()[0];
                long hits = e.getValue()[1];
                detail.add(new MeterRowPanel(
                    e.getKey(),
                    CombatFormat.amount(dmg) + "  " + pct(dmg, Math.max(1, s.getTotalDamage())),
                    hits + " hits",
                    tMax > 0 ? (double) dmg / tMax : 0,
                    STEEL));
            }
        }
    }

    private JPanel splitTable(WeaponAbilityEntry s, double dur)
    {
        JPanel p = new JPanel(new GridLayout(3, 5, 2, 1));
        p.setBackground(CARD);
        p.setBorder(new EmptyBorder(2, 2, 2, 2));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));

        cell(p, "", DIM);
        cell(p, "hits", DIM);
        cell(p, "avg", DIM);
        cell(p, "max", DIM);
        cell(p, "dmg", DIM);

        cell(p, "Special", GOLD);
        cell(p, String.valueOf(s.getSpecHits()), Color.WHITE);
        cell(p, String.valueOf(s.getSpecAvg()), Color.WHITE);
        cell(p, String.valueOf(s.getSpecMax()), Color.WHITE);
        cell(p, CombatFormat.amount(s.getSpecDamage()), Color.WHITE);

        cell(p, "Normal", STEEL);
        cell(p, String.valueOf(s.getNormalHits()), Color.WHITE);
        cell(p, String.valueOf(s.getNormalAvg()), Color.WHITE);
        cell(p, "-", Color.WHITE);
        cell(p, CombatFormat.amount(s.getNormalDamage()), Color.WHITE);
        return p;
    }

    /** Fold the 0..127 per-damage counts into ~10 even bins spanning 1..maxHit. */
    private JPanel histogram(int[] hist, int maxHit, int totalHits)
    {
        int bins = Math.max(1, Math.min(10, maxHit));
        int[] binCount = new int[bins];
        String[] binLabel = new String[bins];
        double width = maxHit / (double) bins;
        for (int b = 0; b < bins; b++)
        {
            int lo = (int) Math.floor(b * width) + 1;
            int hi = (b == bins - 1) ? Math.max(lo, hist.length - 1) : (int) Math.floor((b + 1) * width);
            binLabel[b] = (lo == hi) ? String.valueOf(lo) : lo + "-" + hi;
            for (int v = lo; v <= hi && v < hist.length; v++)
            {
                binCount[b] += hist[v];
            }
        }
        int peak = 1;
        for (int c : binCount)
        {
            peak = Math.max(peak, c);
        }

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int b = 0; b < bins; b++)
        {
            p.add(new MeterRowPanel(
                binLabel[b],
                binCount[b] + "  " + pct(binCount[b], Math.max(1, totalHits)),
                null,
                (double) binCount[b] / peak,
                new Color(100, 170, 210)));
        }
        return p;
    }

    private void stat(String k, String v)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(CARD);
        row.setBorder(new EmptyBorder(2, 3, 2, 3));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        JLabel a = new JLabel(k);
        a.setForeground(DIM);
        a.setFont(FontManager.getRunescapeSmallFont());
        JLabel b = new JLabel(v);
        b.setForeground(Color.WHITE);
        b.setFont(FontManager.getRunescapeSmallFont());
        row.add(a, BorderLayout.WEST);
        row.add(b, BorderLayout.EAST);
        detail.add(row);
        detail.add(Box.createVerticalStrut(2));
    }

    private void sub(String text)
    {
        JLabel h = new JLabel(text);
        h.setFont(FontManager.getRunescapeSmallFont());
        h.setForeground(new Color(120, 144, 156));
        h.setBorder(new EmptyBorder(6, 2, 1, 2));
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        detail.add(h);
    }

    private static void cell(JPanel p, String text, Color fg)
    {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setFont(FontManager.getRunescapeSmallFont());
        p.add(l);
    }

    private JLabel hint(String text)
    {
        JLabel l = new JLabel("<html><i>" + text + "</i></html>");
        l.setForeground(DIM);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setBorder(new EmptyBorder(6, 6, 6, 6));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static String pct(long part, long whole)
    {
        return String.format("%.0f%%", whole > 0 ? (part * 100.0) / whole : 0.0);
    }
}
