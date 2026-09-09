package com.osrscopilot.combat.ui;

import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.model.CombatTimelineEvent;
import com.osrscopilot.combat.model.EncounterSegment;
import com.osrscopilot.combat.model.EntityCombatStats;
import com.osrscopilot.combat.model.SegmentStatus;
import com.osrscopilot.combat.model.SegmentType;
import com.osrscopilot.combat.model.StyleDamageEntry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * Full, scrollable, filterable, timestamped combat log for post-fight review. OSRS has no native
 * combat log, so this is built from the plugin's own {@link CombatTimelineEvent} stream.
 */
@Slf4j
public class CombatLogView extends JPanel
{
    private static final int MAX_RENDERED_LINES = 2000;
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final CombatEncounterManager encounterManager;
    private final Runnable onBack;

    private final JComboBox<String> scopeSelector = new JComboBox<>();
    private final JTextPane logPane = new JTextPane();
    private final JLabel statusLabel = new JLabel(" ");
    private JScrollPane scroll;

    // Which event types are shown. Empty selection == show everything.
    private final Set<String> activeFilters = new LinkedHashSet<>();
    // Free-text search over each event's source / target / weapon / style / description.
    private final javax.swing.JTextField searchField = new javax.swing.JTextField();
    private String searchQuery = "";
    private boolean updatingScope = false;
    private final Runnable combatListener;
    // Combat events fire many times per tick; coalesce them, and never rebuild while hidden.
    private final javax.swing.Timer refreshDebounce;
    private boolean pendingWhileHidden = false;
    private int lastDropdownSize = -1;
    private long lastDropdownSig = 0;
    private List<String> lastRenderedLines = java.util.Collections.emptyList();

    public CombatLogView(CombatEncounterManager encounterManager, Runnable onBack)
    {
        this.encounterManager = encounterManager;
        this.onBack = onBack;

        setLayout(new BorderLayout(0, 4));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(6, 6, 6, 6));

        add(buildHeader(), BorderLayout.NORTH);

        logPane.setEditable(false);
        logPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        logPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logPane.setBorder(new EmptyBorder(4, 4, 4, 4));

        scroll = new JScrollPane(logPane,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        add(statusLabel, BorderLayout.SOUTH);

        this.refreshDebounce = new javax.swing.Timer(150, e -> refresh());
        this.refreshDebounce.setRepeats(false);
        this.combatListener = () -> SwingUtilities.invokeLater(this::scheduleRefresh);
        encounterManager.addCombatListener(this.combatListener);
    }

    private void scheduleRefresh()
    {
        if (!isShowing())
        {
            pendingWhileHidden = true;
            return;
        }
        refreshDebounce.restart();
    }

    @Override
    public void addNotify()
    {
        super.addNotify();
        if (pendingWhileHidden)
        {
            pendingWhileHidden = false;
            refresh();
        }
    }

    /** Unregister the combat listener. Called from OsrsCopilotPanel.dispose(). */
    public void dispose()
    {
        if (refreshDebounce != null)
        {
            refreshDebounce.stop();
        }
        if (encounterManager != null && combatListener != null)
        {
            encounterManager.removeCombatListener(combatListener);
        }
    }

    private JPanel buildHeader()
    {
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel top = new JPanel(new BorderLayout(4, 0));
        top.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton back = small("< Back");
        back.addActionListener(e -> { if (onBack != null) onBack.run(); });
        top.add(back, BorderLayout.WEST);

        scopeSelector.setFont(FontManager.getRunescapeSmallFont());
        scopeSelector.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scopeSelector.addActionListener(e -> { if (!updatingScope) refresh(); });
        top.add(scopeSelector, BorderLayout.CENTER);
        header.add(top, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel filters = new JPanel(new GridLayout(1, 5, 3, 0));
        filters.setBackground(ColorScheme.DARK_GRAY_COLOR);
        addFilter(filters, "Dealt", "HIT", "DOT", "SPEC");
        addFilter(filters, "Taken", "TAKEN");
        addFilter(filters, "Support", "HEAL", "CONSUME", "POTION", "BUFF");
        addFilter(filters, "CC", "DEBUFF");
        addFilter(filters, "Fight", "START", "END", "SWITCH");
        center.add(filters);

        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel findLbl = new JLabel("find:");
        findLbl.setFont(FontManager.getRunescapeSmallFont());
        findLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        searchRow.add(findLbl, BorderLayout.WEST);
        searchField.setFont(FontManager.getRunescapeSmallFont());
        searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchField.setForeground(Color.WHITE);
        searchField.setToolTipText("Filter the log by source / target / weapon / spell / text (e.g. a boss name, \"whip\", \"venom\")");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            private void changed() { searchQuery = searchField.getText(); scheduleRefresh(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
        });
        searchRow.add(searchField, BorderLayout.CENTER);
        JButton clearSearch = small("x");
        clearSearch.addActionListener(e -> searchField.setText(""));
        searchRow.add(clearSearch, BorderLayout.EAST);
        center.add(searchRow);
        header.add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(1, 4, 3, 0));
        actions.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JButton copy = small("Copy log");
        copy.addActionListener(e -> copyToClipboard());
        JButton save = small("Save");
        save.addActionListener(e -> saveToFile());
        JButton toEnd = small("To end");
        toEnd.setToolTipText("Jump to the fight's end / death line");
        toEnd.addActionListener(e -> scrollToFightEnd());
        JButton clear = small("Reset Live");
        clear.addActionListener(e -> confirmReset());
        actions.add(copy);
        actions.add(save);
        actions.add(toEnd);
        actions.add(clear);
        header.add(actions, BorderLayout.SOUTH);

        return header;
    }

    private void addFilter(JPanel parent, String label, String... types)
    {
        JToggleButton b = new JToggleButton(label);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        b.setFocusPainted(false);
        b.setMargin(new java.awt.Insets(1, 2, 1, 2));
        b.addActionListener(e ->
        {
            for (String t : types)
            {
                if (b.isSelected())
                {
                    activeFilters.add(t);
                }
                else
                {
                    activeFilters.remove(t);
                }
            }
            refresh();
        });
        parent.add(b);
    }

    private JButton small(String text)
    {
        JButton b = new JButton(text);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        b.setFocusPainted(false);
        b.setMargin(new java.awt.Insets(1, 4, 1, 4));
        return b;
    }

    /** Rebuilds the scope dropdown and the log body. Safe to call off the constructor / listener. */
    public void refresh()
    {
        List<EncounterSegment> segments = encounterManager.getAllSegmentsForDropdown();

        // Only rebuild the dropdown when the segment list actually changed - the toString()s
        // update in place (durations tick) but recreating the model every event steals focus and
        // churns Swing for nothing. "Changed" includes row 0's segment being swapped
        // (live -> last -> placeholder) without the count moving, hence the identity signature.
        long sig = 0;
        for (EncounterSegment s : segments)
        {
            sig = sig * 31 + System.identityHashCode(s);
        }
        if (segments.size() != lastDropdownSize || sig != lastDropdownSig
            || scopeSelector.getItemCount() != segments.size())
        {
            int keep = scopeSelector.getSelectedIndex();
            updatingScope = true;
            scopeSelector.removeAllItems();
            for (EncounterSegment s : segments)
            {
                scopeSelector.addItem(s.toString());
            }
            if (keep >= 0 && keep < scopeSelector.getItemCount())
            {
                scopeSelector.setSelectedIndex(keep);
            }
            updatingScope = false;
            lastDropdownSize = segments.size();
            lastDropdownSig = sig;
        }

        int idx = Math.max(0, scopeSelector.getSelectedIndex());
        EncounterSegment scope = idx < segments.size() ? segments.get(idx) : encounterManager.getSelectedOrCurrentEncounter();

        List<String> lines = buildLines(scope);

        // Nothing rendered changed (e.g. a notify between events, or a hidden refresh) - don't
        // wipe + rebuild the document, which is what made the view jump around during a fight.
        if (lines.equals(lastRenderedLines))
        {
            return;
        }
        lastRenderedLines = new ArrayList<>(lines);

        // Preserve the user's scroll position if they've scrolled up to read; only auto-follow
        // when they're already parked at the bottom (chat-frame behaviour).
        javax.swing.JScrollBar vbar = scroll.getVerticalScrollBar();
        boolean atBottom = vbar.getValue() + vbar.getVisibleAmount() >= vbar.getMaximum() - 4;
        int prevScroll = vbar.getValue();

        StyledDocument doc = logPane.getStyledDocument();
        try
        {
            doc.remove(0, doc.getLength());
            int start = Math.max(0, lines.size() - MAX_RENDERED_LINES);
            for (int i = start; i < lines.size(); i++)
            {
                appendLine(doc, lines.get(i));
            }
        }
        catch (BadLocationException e)
        {
            log.debug("Combat log re-render failed", e);
        }
        if (atBottom)
        {
            logPane.setCaretPosition(doc.getLength());
        }
        else
        {
            // Hold the reader's position across the rebuild instead of snapping to the top.
            SwingUtilities.invokeLater(() ->
                vbar.setValue(Math.max(0, Math.min(prevScroll, vbar.getMaximum() - vbar.getVisibleAmount()))));
        }

        int shown = Math.min(lines.size(), MAX_RENDERED_LINES);
        int dropped = scope != null ? scope.getLocalPlayerStats().getDroppedEventCount() : 0;
        statusLabel.setText(lines.isEmpty()
            ? "No combat events for this scope yet."
            : "Showing " + shown + " of " + lines.size() + " events"
                + (dropped > 0 ? "  (+" + dropped + " older dropped)" : ""));
    }

    private static String sigil(String eventType)
    {
        if (eventType == null) return " ";
        switch (eventType)
        {
            case "HIT":
            case "DOT": return ">";
            case "SPEC": return "!";
            case "TAKEN": return "<";
            case "HEAL": return "+";
            case "CONSUME":
            case "POTION":
            case "BUFF": return "~";
            case "DEBUFF": return "*";
            case "SWITCH": return "-";
            case "START":
            case "END": return "=";
            default: return " ";
        }
    }

    /** A full post-fight stat block pinned at the top of the log (post-fight stat block). */
    private List<String> buildSummary(EncounterSegment scope)
    {
        List<String> s = new ArrayList<>();
        EntityCombatStats p = scope.getLocalPlayerStats();
        if (p.getTotalDamage() <= 1 && p.getDamageTaken() <= 0)
        {
            return s;
        }
        int dur = scope.getDurationSeconds();
        String title = scope.getSegmentType() == SegmentType.ENCOUNTER
            ? scope.getTargetNameWithLevel() : scope.toString();
        s.add("= FIGHT SUMMARY  " + title);
        s.add(String.format("  Duration      %d:%02d", dur / 60, dur % 60));
        s.add(String.format("  Damage dealt  %s  (%.1f DPS%s)", fmtNum(p.getTotalDamage()), p.getDps(),
            p.getPeakDps() > 0 ? String.format(", peak %.1f", p.getPeakDps()) : ""));
        String maxHit = p.getMaxHitDealt() != null
            ? "  |  max hit " + p.getMaxHitDealt().getAmount() + " (" + p.getMaxHitDealt().getSourceName() + ")"
            : "";
        s.add(String.format("  Accuracy      %.1f%%%s", p.getHitAccuracy(), maxHit));

        List<StyleDamageEntry> styles = p.getStyleBreakdown();
        if (!styles.isEmpty())
        {
            StringBuilder b = new StringBuilder("  By style      ");
            for (int i = 0; i < styles.size(); i++)
            {
                if (i > 0) b.append(" | ");
                b.append(styles.get(i).getStyleName()).append(' ').append(fmtNum(styles.get(i).getDamage()));
            }
            s.add(b.toString());
        }

        List<EntityCombatStats> enemies = scope.getEnemyBreakdown();
        if (enemies.size() > 1)
        {
            StringBuilder b = new StringBuilder("  Targets       ");
            for (int i = 0; i < enemies.size(); i++)
            {
                if (i > 0) b.append(" | ");
                b.append(enemies.get(i).getName()).append(' ').append(fmtNum(enemies.get(i).getTotalDamage()));
            }
            s.add(b.toString());
        }

        if (p.getDamageTaken() > 0)
        {
            String big = p.getMaxHitTaken() != null ? String.format("  |  biggest %d", p.getMaxHitTaken().getAmount()) : "";
            s.add(String.format("  Damage taken  %s  (%.1f DTPS)%s", fmtNum(p.getDamageTaken()), p.getDtps(), big));
            java.util.Map<String, Long> bySrc = p.getDamageTakenBySource();
            if (!bySrc.isEmpty())
            {
                StringBuilder b = new StringBuilder("  Taken from    ");
                int i = 0;
                for (java.util.Map.Entry<String, Long> e : bySrc.entrySet())
                {
                    if (i++ > 0) b.append(" | ");
                    b.append(e.getKey()).append(' ').append(fmtNum(e.getValue()));
                }
                s.add(b.toString());
            }
        }
        if (p.getHpHealed() > 0) s.add("  Healing       " + fmtNum(p.getHpHealed()));
        if (p.getTotalGpCost() > 0) s.add("  Supplies      " + fmtNum(p.getTotalGpCost()) + " GP");

        java.util.List<com.osrscopilot.combat.model.DebuffApplication> ccs = scope.getDebuffs();
        if (!ccs.isEmpty())
        {
            s.add("  Bind / freeze casts");
            for (com.osrscopilot.combat.model.DebuffApplication d : ccs)
            {
                s.add(String.format("    %-13s %s", d.getType(), d.getTargetName()));
            }
        }

        java.util.List<CombatTimelineEvent> death = scope.getDeathRecap();
        if (scope.getStatus() == SegmentStatus.WIPED && !death.isEmpty())
        {
            s.add("  What killed you  (last " + death.size() + " incoming hits)");
            for (CombatTimelineEvent ev : death)
            {
                String amt = ev.getAmount() > 0 ? String.valueOf(ev.getAmount()) : "0";
                s.add(String.format("    +%-6s %s  %s from %s", ev.getTimeFormatted(), ev.getWallClock(),
                    amt, ev.getSourceName() != null ? ev.getSourceName() : "?"));
            }
        }

        s.add("= ---------------");
        return s;
    }

    private static String fmtNum(long v)
    {
        return com.osrscopilot.combat.CombatFormat.amount(v);
    }

    /** The full post-fight stat block for a scope (also used by Copy / Save). */
    public java.util.List<String> summaryFor(EncounterSegment scope)
    {
        return buildSummary(scope);
    }

    /**
     * Every rendered line for a scope (summary block + the full, unfiltered event stream). This is
     * the complete Full Log - 0-damage HIT / TAKEN events included.
     */
    public java.util.List<String> logLinesFor(EncounterSegment scope)
    {
        return buildLines(scope);
    }

    private List<String> buildLines(EncounterSegment scope)
    {
        return buildLines(scope, true);
    }

    private List<String> buildLines(EncounterSegment scope, boolean applyFilters)
    {
        List<String> out = new ArrayList<>();
        if (scope == null)
        {
            return out;
        }
        out.add("legend:  > dealt   ! spec   < taken   + heal   ~ supply   * cc   = fight start/end");
        out.add("(the Full Log keeps 0-damage / blocked / splashed hits; the HUD ledger card hides them)");
        out.addAll(buildSummary(scope));
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
        for (CombatTimelineEvent ev : scope.getLocalPlayerStats().getTimelineEvents())
        {
            if (applyFilters && !activeFilters.isEmpty() && !activeFilters.contains(ev.getEventType()))
            {
                continue;
            }
            if (applyFilters && !q.isEmpty() && !ev.searchText().contains(q))
            {
                continue;
            }
            // Every line carries the real-world clock time as well as fight-relative MM:SS.
            String s = sigil(ev.getEventType());
            if ("=".equals(s))
            {
                out.add(ev.getWallClock() + "  = " + ev.getDescription());
            }
            else
            {
                out.add(String.format("%s  +%-6s %s %s", ev.getWallClock(), ev.getTimeFormatted(), s, ev.getDescription()));
            }
        }
        return out;
    }

    private void appendLine(StyledDocument doc, String line) throws BadLocationException
    {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        Color c = ColorScheme.TEXT_COLOR;
        String upper = line.toUpperCase();
        if (upper.contains("DEFEATED") || upper.contains("ENGAGED"))
        {
            c = new Color(255, 215, 0);
        }
        else if (upper.contains("TOOK "))
        {
            c = new Color(239, 120, 120);
        }
        else if (upper.contains("HEALED") || upper.contains("ATE ") || upper.contains("DRANK "))
        {
            c = new Color(129, 199, 132);
        }
        StyleConstants.setForeground(attr, c);
        doc.insertString(doc.getLength(), line + "\n", attr);
    }

    /** Scroll the log to the fight's end / death line (the last "= " boundary), else to the bottom. */
    private void scrollToFightEnd()
    {
        try
        {
            String text = logPane.getStyledDocument().getText(0, logPane.getStyledDocument().getLength());
            int at = text.lastIndexOf("  = ");
            if (at < 0)
            {
                at = text.lastIndexOf("\n= ");
            }
            logPane.setCaretPosition(at >= 0 ? at : logPane.getStyledDocument().getLength());
            statusLabel.setText(at >= 0 ? "Jumped to the fight's end." : "No end line yet - fight still going.");
        }
        catch (BadLocationException e)
        {
            log.debug("Scroll-to-fight-end failed", e);
        }
    }

    private String plainText()
    {
        int idx = Math.max(0, scopeSelector.getSelectedIndex());
        List<EncounterSegment> segs = encounterManager.getAllSegmentsForDropdown();
        EncounterSegment scope = idx < segs.size() ? segs.get(idx) : encounterManager.getSelectedOrCurrentEncounter();
        StringBuilder sb = new StringBuilder();
        sb.append("Combat log - ").append(scope != null ? scope.toString() : "current").append('\n');
        // Export is always the full stream + summary, regardless of which filter toggles are on.
        for (String l : buildLines(scope, false))
        {
            sb.append(l).append('\n');
        }
        return sb.toString();
    }

    private void copyToClipboard()
    {
        try
        {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(plainText()), null);
            statusLabel.setText("Combat log copied to clipboard.");
        }
        catch (Exception e)
        {
            statusLabel.setText("Could not access the clipboard.");
        }
    }

    private void saveToFile()
    {
        try
        {
            File dir = new File(RuneLite.RUNELITE_DIR, "combat-logs");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File out = new File(dir, "combat-log_" + LocalDateTime.now().format(FILE_STAMP) + ".txt");
            try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8.name()))
            {
                w.print(plainText());
            }
            statusLabel.setText("Saved to " + out.getName());
            LinkBrowser.open(dir.toString());
        }
        catch (Exception e)
        {
            statusLabel.setText("Save failed: " + e.getMessage());
        }
    }

    private void confirmReset()
    {
        int r = JOptionPane.showConfirmDialog(this,
            "Reset the current fight's stats and log?", "Reset Live",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION)
        {
            encounterManager.resetCurrentEncounter();
            refresh();
        }
    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(225, super.getPreferredSize().height);
    }
}
