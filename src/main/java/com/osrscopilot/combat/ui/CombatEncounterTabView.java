package com.osrscopilot.combat.ui;

import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.model.ConsumableUsageEntry;
import com.osrscopilot.combat.model.EncounterSegment;
import com.osrscopilot.combat.model.EntityCombatStats;
import com.osrscopilot.combat.model.SegmentStatus;
import com.osrscopilot.combat.model.SegmentType;
import com.osrscopilot.combat.model.StyleDamageEntry;
import com.osrscopilot.combat.model.WeaponAbilityEntry;
import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.ui.theme.CopilotPalette;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

public class CombatEncounterTabView extends JPanel
{
    private final CombatEncounterManager encounterManager;
    private com.osrscopilot.combat.party.CombatPartyService combatPartyService;
    private Runnable combatListener;

    // Which party member row (in the GROUP scope) is expanded to its weapon/spell drill-down.
    private String expandedMember = null;
    // Coalesces the burst of combat events in a single tick into one Swing rebuild.
    private final javax.swing.Timer refreshDebounce;
    private final MonsterDatabase monsterDatabase;
    private final ItemManager itemManager;
    private final Consumer<Monster> onOpenMonsterDetail;

    private Runnable onOpenGraph;
    private Runnable onToggleHud;
    private Runnable onOpenLog;

    public void setOnOpenGraph(Runnable onOpenGraph)
    {
        this.onOpenGraph = onOpenGraph;
    }

    public void setOnOpenLog(Runnable onOpenLog)
    {
        this.onOpenLog = onOpenLog;
    }

    public void setOnToggleHud(Runnable onToggleHud)
    {
        this.onToggleHud = onToggleHud;
    }

    /** Optional - enables the "Group" damage-meter scope and its Party join/leave control. */
    public void setCombatPartyService(com.osrscopilot.combat.party.CombatPartyService svc)
    {
        this.combatPartyService = svc;
    }

    private final JComboBox<EncounterSegment> segmentDropdown = new JComboBox<>();

    private final JButton resetBtn = new JButton("Reset");
    private final JButton partyBtn = new JButton("Party");
    private final JPanel heroBanner = new JPanel();
    private final JLabel targetNameLabel = new JLabel("No Active Fight");
    private final JLabel dpsLabel = new JLabel("DPS 0.0");
    private final JButton viewBestiaryBtn = new JButton("Bestiary >");

    // Shown only when the selected scope is a merged "Target x N" trash row.
    private final CollapsibleSectionCard perKillCard;
    private final JPanel perKillContentPanel = new JPanel();

    // Refs the interactive tutorial scrolls to when it walks the panel.
    private JScrollPane scrollPane;
    private JPanel shareRow;

    // 6 Deep-Dive Analytics Cards
    private final CollapsibleSectionCard styleBreakdownCard;
    private final CollapsibleSectionCard defensiveEfficiencyCard;
    private final CollapsibleSectionCard healingSustenanceCard;
    private final CollapsibleSectionCard buffUptimeCard;
    private final CollapsibleSectionCard consumablesLedgerCard;
    private final CollapsibleSectionCard timelineLedgerCard;

    // Action-ledger: how many rows to show. Collapsed shows the most recent few; the
    // "Show all" toggle expands to the full retained buffer for the selected scope.
    private static final int TIMELINE_COLLAPSED_ROWS = 8;
    private boolean timelineExpanded = false;

    // Which "BY WEAPON / SPELL" row is currently expanded to its per-source detail (null = none).
    private String expandedWeapon = null;

    // Which per-kill breakdown row is drilled into (KillSummary.getIndex(), -1 = none).
    private int expandedKillIndex = -1;
    private static final java.time.format.DateTimeFormatter KILL_END_FMT =
        java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault());

    private final JPanel styleContentPanel = new JPanel();
    private final JPanel defensiveContentPanel = new JPanel();
    private final JPanel healingContentPanel = new JPanel();
    private final JPanel buffContentPanel = new JPanel();
    private final JPanel consumablesContentPanel = new JPanel();
    private final JPanel timelineContentPanel = new JPanel();

    private final JLabel dtpsLabel = new JLabel("DTPS 0.0");
    private final JLabel hpsLabel = new JLabel("HPS 0.0");
    private final JLabel costLabel = new JLabel("Cost 0");

    private boolean updatingDropdown = false;

    public CombatEncounterTabView(
        CombatEncounterManager encounterManager,
        MonsterDatabase monsterDatabase,
        ItemManager itemManager,
        Consumer<Monster> onOpenMonsterDetail)
    {
        this.encounterManager = encounterManager;
        this.monsterDatabase = monsterDatabase;
        this.itemManager = itemManager;
        this.onOpenMonsterDetail = onOpenMonsterDetail;

        setLayout(new BorderLayout(0, 0));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel mainList = new JPanel();
        mainList.setLayout(new BoxLayout(mainList, BoxLayout.Y_AXIS));
        mainList.setBackground(ColorScheme.DARK_GRAY_COLOR);
        mainList.setBorder(new EmptyBorder(4, 4, 4, 4));
        mainList.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 1. Segment Dropdown & Reset Row
        JPanel segmentRow = new JPanel(new BorderLayout(4, 0));
        segmentRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        segmentRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        segmentRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        segmentDropdown.setFont(FontManager.getRunescapeSmallFont());
        segmentDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        segmentDropdown.setForeground(Color.WHITE);
        segmentDropdown.setRenderer(new javax.swing.DefaultListCellRenderer()
        {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                int index, boolean sel, boolean focus)
            {
                super.getListCellRendererComponent(list, value, index, sel, focus);
                if (value instanceof EncounterSegment)
                {
                    // Row 0 is the auto-following "Live" scope; EncounterSegment.toString()
                    // labels every row itself (Live / Current Session / Total / "#N ...").
                    setText(((EncounterSegment) value).toString());
                }
                setFont(FontManager.getRunescapeSmallFont());
                return this;
            }
        });
        segmentDropdown.addActionListener(e -> {
            if (updatingDropdown) return;
            EncounterSegment selected = (EncounterSegment) segmentDropdown.getSelectedItem();
            if (selected != null)
            {
                encounterManager.selectEncounter(selected);
                refreshUI();
            }
        });
        segmentRow.add(segmentDropdown, BorderLayout.CENTER);

        resetBtn.setText("Reset");
        resetBtn.setFont(FontManager.getRunescapeSmallFont());
        resetBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        resetBtn.setForeground(CopilotPalette.NEGATIVE);
        resetBtn.setFocusPainted(false);
        resetBtn.setMargin(new Insets(1, 4, 1, 4));
        resetBtn.setPreferredSize(new Dimension(58, 22));
        resetBtn.setToolTipText("Reset Live / Session / Total");
        resetBtn.addActionListener(e -> showResetMenu(resetBtn));
        segmentRow.add(resetBtn, BorderLayout.EAST);

        mainList.add(segmentRow);
        mainList.add(Box.createRigidArea(new Dimension(0, 4)));

        // 2. Hero Summary Banner
        setupHeroBanner();
        mainList.add(heroBanner);
        mainList.add(Box.createRigidArea(new Dimension(0, 5)));

        // Per-kill breakdown (only visible for a merged "Target x N" scope).
        perKillContentPanel.setLayout(new BoxLayout(perKillContentPanel, BoxLayout.Y_AXIS));
        perKillContentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        perKillCard = new CollapsibleSectionCard("Per-kill breakdown", CopilotPalette.ACCENT_MUTED, perKillContentPanel);
        perKillCard.setToolTipText("Every individual kill folded into this merged row - click a row to drill in.");
        perKillCard.setVisible(false);
        mainList.add(perKillCard);
        mainList.add(Box.createRigidArea(new Dimension(0, 4)));

        // 3. Section 1: Damage Dealt Card
        styleContentPanel.setLayout(new BoxLayout(styleContentPanel, BoxLayout.Y_AXIS));
        styleContentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        styleBreakdownCard = new CollapsibleSectionCard("Damage Dealt", CopilotPalette.ACCENT, styleContentPanel);
        mainList.add(styleBreakdownCard);
        mainList.add(Box.createRigidArea(new Dimension(0, 4)));

        // Section 2: Defensive Card
        defensiveContentPanel.setLayout(new BoxLayout(defensiveContentPanel, BoxLayout.Y_AXIS));
        defensiveContentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        defensiveEfficiencyCard = new CollapsibleSectionCard("Defensive", CopilotPalette.TAKEN, defensiveContentPanel);
        mainList.add(defensiveEfficiencyCard);
        mainList.add(Box.createRigidArea(new Dimension(0, 4)));

        // Section 3: Healing & Sustenance Card
        healingContentPanel.setLayout(new BoxLayout(healingContentPanel, BoxLayout.Y_AXIS));
        healingContentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        healingSustenanceCard = new CollapsibleSectionCard("Healing", CopilotPalette.POSITIVE, healingContentPanel);
        mainList.add(healingSustenanceCard);
        mainList.add(Box.createRigidArea(new Dimension(0, 4)));

        // Section 4: Buff & Aura Uptime Card
        // Semantic roles in this view resolve to CopilotPalette; the raw new Color(...) values that
        // remain are intentional local one-offs (buff violet here, thrall/party violet, cannon
        // brown, the deep-orange "big hit" highlight, the dim drill-in greys) - decorative, not
        // roles, so not promoted to the shared palette.
        buffContentPanel.setLayout(new BoxLayout(buffContentPanel, BoxLayout.Y_AXIS));
        buffContentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buffUptimeCard = new CollapsibleSectionCard("Buffs & Auras", new Color(159, 122, 234), buffContentPanel);
        mainList.add(buffUptimeCard);
        mainList.add(Box.createRigidArea(new Dimension(0, 4)));

        // Section 5: Consumables & Resource Cost Card
        consumablesContentPanel.setLayout(new BoxLayout(consumablesContentPanel, BoxLayout.Y_AXIS));
        consumablesContentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        consumablesLedgerCard = new CollapsibleSectionCard("Consumables", CopilotPalette.GOLD, consumablesContentPanel);
        mainList.add(consumablesLedgerCard);
        mainList.add(Box.createRigidArea(new Dimension(0, 4)));

        // Section 6: Combat Action Timeline Ledger Card
        timelineContentPanel.setLayout(new BoxLayout(timelineContentPanel, BoxLayout.Y_AXIS));
        timelineContentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        timelineLedgerCard = new CollapsibleSectionCard("Action Ledger", CopilotPalette.RATE, timelineContentPanel);
        timelineLedgerCard.setToolTipText("<html>Recent combat actions. 0-damage, blocked and splashed"
            + " hits are hidden here to keep it readable - open <b>Full Log</b> for the complete stream.</html>");
        mainList.add(timelineLedgerCard);
        mainList.add(Box.createRigidArea(new Dimension(0, 3)));
        shareRow = buildShareRow();
        mainList.add(shareRow);
        mainList.add(Box.createRigidArea(new Dimension(0, 5)));

        // Open on the two cards people actually watch; the deep-dive cards start collapsed so the
        // panel isn't a long scroll on first open. Each card's summary line still shows its
        // headline number while collapsed, and the state sticks for the session once toggled.
        defensiveEfficiencyCard.setExpanded(false);
        healingSustenanceCard.setExpanded(false);
        buffUptimeCard.setExpanded(false);
        consumablesLedgerCard.setExpanded(false);

        // Section 6: Action Toolbar
        JPanel actionToolbar = setupActionToolbar();
        mainList.add(actionToolbar);

        JPanel contentWrapper = new ScrollableContentPanel();
        contentWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        contentWrapper.add(mainList, BorderLayout.NORTH);

        scrollPane = new JScrollPane(contentWrapper);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(scrollPane, BorderLayout.CENTER);

        // Listen for combat updates. Many events fire per tick during a fight; debounce them into
        // one rebuild instead of tearing down six cards per hitsplat.
        this.refreshDebounce = new javax.swing.Timer(120, e -> {
            refreshDropdown();
            refreshUI();
        });
        this.refreshDebounce.setRepeats(false);
        this.combatListener = () -> SwingUtilities.invokeLater(refreshDebounce::restart);
        encounterManager.addCombatListener(this.combatListener);

        refreshDropdown();
        refreshUI();
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

    private void setupHeroBanner()
    {
        heroBanner.setLayout(new BoxLayout(heroBanner, BoxLayout.Y_AXIS));
        heroBanner.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        heroBanner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CopilotPalette.CARD_BORDER, 1),
            new EmptyBorder(5, 6, 5, 6)
        ));
        heroBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));
        heroBanner.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel topRow = new JPanel(new BorderLayout(2, 0));
        topRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        targetNameLabel.setFont(FontManager.getRunescapeBoldFont());
        targetNameLabel.setForeground(CopilotPalette.ACCENT);
        topRow.add(targetNameLabel, BorderLayout.CENTER);

        viewBestiaryBtn.setFont(FontManager.getRunescapeSmallFont());
        viewBestiaryBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        viewBestiaryBtn.setForeground(CopilotPalette.LINK);
        viewBestiaryBtn.setFocusPainted(false);
        viewBestiaryBtn.setMargin(new Insets(1, 4, 1, 4));
        viewBestiaryBtn.setPreferredSize(new Dimension(72, 18));
        viewBestiaryBtn.addActionListener(e -> {
            EncounterSegment current = encounterManager.getSelectedOrCurrentEncounter();
            if (current != null && current.getTargetName() != null && onOpenMonsterDetail != null)
            {
                Monster m = monsterDatabase.getMonsterByName(current.getTargetName());
                if (m != null)
                {
                    onOpenMonsterDetail.accept(m);
                }
            }
        });
        topRow.add(viewBestiaryBtn, BorderLayout.EAST);
        heroBanner.add(topRow);

        heroBanner.add(Box.createRigidArea(new Dimension(0, 3)));

        // 2x2 Metric Grid
        JPanel statsGrid = new JPanel(new GridLayout(2, 2, 2, 2));
        statsGrid.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        dpsLabel.setFont(FontManager.getRunescapeSmallFont());
        dpsLabel.setForeground(Color.WHITE);
        statsGrid.add(dpsLabel);

        dtpsLabel.setFont(FontManager.getRunescapeSmallFont());
        dtpsLabel.setForeground(CopilotPalette.NEGATIVE);
        statsGrid.add(dtpsLabel);

        hpsLabel.setFont(FontManager.getRunescapeSmallFont());
        hpsLabel.setForeground(CopilotPalette.POSITIVE);
        statsGrid.add(hpsLabel);

        costLabel.setFont(FontManager.getRunescapeSmallFont());
        costLabel.setForeground(CopilotPalette.GOLD);
        statsGrid.add(costLabel);

        heroBanner.add(statsGrid);
    }

    private JPanel setupActionToolbar()
    {
        JPanel bar = new JPanel(new GridLayout(2, 3, 2, 2));
        bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton hudBtn = new JButton("HUD");
        hudBtn.setFont(FontManager.getRunescapeSmallFont());
        hudBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        hudBtn.setForeground(CopilotPalette.ACCENT);
        hudBtn.setToolTipText("Toggle In-Game Combat Meter Overlay");
        hudBtn.setFocusPainted(false);
        hudBtn.setMargin(new Insets(1, 2, 1, 2));
        hudBtn.addActionListener(e -> {
            if (onToggleHud != null) onToggleHud.run();
        });
        bar.add(hudBtn);

        JButton graphBtn = new JButton("Graph");
        graphBtn.setFont(FontManager.getRunescapeSmallFont());
        graphBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        graphBtn.setForeground(CopilotPalette.POSITIVE);
        graphBtn.setToolTipText("Toggle Floating Fight Time-Series Graph");
        graphBtn.setFocusPainted(false);
        graphBtn.setMargin(new Insets(1, 2, 1, 2));
        graphBtn.addActionListener(e -> {
            if (onOpenGraph != null) onOpenGraph.run();
        });
        bar.add(graphBtn);

        JButton copyBtn = new JButton("Copy summary");
        copyBtn.setFont(FontManager.getRunescapeSmallFont());
        copyBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        copyBtn.setForeground(new Color(77, 208, 225));
        copyBtn.setToolTipText("Copy the one-line DPS/damage summary to the clipboard (Full Log has the whole log)");
        copyBtn.setFocusPainted(false);
        copyBtn.setMargin(new Insets(1, 2, 1, 2));
        copyBtn.addActionListener(e -> copyCombatReport());
        bar.add(copyBtn);

        // Reset Live / Session / Total all live in the "Reset" menu next to the scope dropdown -
        // no duplicate toolbar buttons here (F10).

        JButton logBtn = new JButton("Full Log");
        logBtn.setFont(FontManager.getRunescapeSmallFont());
        logBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        logBtn.setForeground(CopilotPalette.RATE);
        logBtn.setToolTipText("Open the full scrollable combat log");
        logBtn.setFocusPainted(false);
        logBtn.setMargin(new Insets(1, 2, 1, 2));
        logBtn.addActionListener(e -> { if (onOpenLog != null) onOpenLog.run(); });
        bar.add(logBtn);

        partyBtn.setFont(FontManager.getRunescapeSmallFont());
        partyBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        partyBtn.setForeground(new Color(186, 104, 200));
        partyBtn.setToolTipText("Join / leave a party for the shared \"Group\" damage meter");
        partyBtn.setFocusPainted(false);
        partyBtn.setMargin(new Insets(1, 2, 1, 2));
        partyBtn.addActionListener(e -> showPartyMenu(partyBtn));
        bar.add(partyBtn);

        return bar;
    }

    /** Keep the toolbar "Party" button showing live state (member count / not connected). */
    private void refreshPartyButton()
    {
        if (combatPartyService == null)
        {
            partyBtn.setVisible(false);
            return;
        }
        partyBtn.setVisible(true);
        if (!combatPartyService.partyEnabled())
        {
            partyBtn.setText("Party");
            partyBtn.setToolTipText("Turn on \"Group damage meter\" in settings to use this");
        }
        else if (!combatPartyService.runelitePartyAvailable())
        {
            partyBtn.setText("Party");
            partyBtn.setToolTipText("Needs RuneLite's \"Party\" plugin - enable it in the plugin list");
        }
        else if (combatPartyService.inParty())
        {
            partyBtn.setText("Party ● " + combatPartyService.memberCount());
            partyBtn.setToolTipText("In party \"" + combatPartyService.passphrase()
                + "\" - click to copy the passphrase or leave");
        }
        else
        {
            partyBtn.setText("Party");
            partyBtn.setToolTipText("Not in a party - click to join or start one");
        }
    }

    /** Small popup: shows the party passphrase and offers join / generate / leave. */
    private void showPartyMenu(java.awt.Component anchor)
    {
        if (combatPartyService == null)
        {
            return;
        }
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        if (!combatPartyService.partyEnabled())
        {
            javax.swing.JMenuItem off = new javax.swing.JMenuItem("Enable \"Group damage meter\" in settings first");
            off.setEnabled(false);
            menu.add(off);
            menu.show(anchor, 0, -menu.getPreferredSize().height);
            return;
        }

        if (!combatPartyService.runelitePartyAvailable())
        {
            javax.swing.JMenuItem needParty = new javax.swing.JMenuItem(
                "<html>RuneLite's <b>Party</b> plugin is turned off.<br>"
                + "Enable it in the plugin list, then click Party again.</html>");
            needParty.setEnabled(false);
            menu.add(needParty);
            menu.show(anchor, 0, -menu.getPreferredSize().height);
            return;
        }

        if (combatPartyService.inParty())
        {
            String pass = combatPartyService.passphrase();
            javax.swing.JMenuItem cur = new javax.swing.JMenuItem("In party: " + pass + "  (copy)");
            cur.setToolTipText("Copy the passphrase so a friend can join the same party");
            cur.addActionListener(a -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(pass), null));
            menu.add(cur);
            menu.addSeparator();
            javax.swing.JMenuItem leave = new javax.swing.JMenuItem("Leave party");
            leave.addActionListener(a -> { combatPartyService.leaveParty(); refreshDropdown(); refreshUI(); });
            menu.add(leave);
        }
        else
        {
            javax.swing.JMenuItem join = new javax.swing.JMenuItem("Join a party...");
            join.addActionListener(a ->
            {
                String p = javax.swing.JOptionPane.showInputDialog(this,
                    "Party passphrase (everyone in the group enters the same words):",
                    "Join party", javax.swing.JOptionPane.PLAIN_MESSAGE);
                if (p != null && !p.trim().isEmpty())
                {
                    combatPartyService.joinParty(p);
                    refreshDropdown();
                    refreshUI();
                }
            });
            menu.add(join);

            javax.swing.JMenuItem gen = new javax.swing.JMenuItem("Start a new party (random passphrase)");
            gen.addActionListener(a ->
            {
                String p = combatPartyService.newPassphrase();
                combatPartyService.joinParty(p);
                javax.swing.JOptionPane.showMessageDialog(this,
                    "Party started. Share this passphrase:\n\n" + p,
                    "New party", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                refreshDropdown();
                refreshUI();
            });
            menu.add(gen);
        }

        menu.show(anchor, 0, -menu.getPreferredSize().height);
    }

    private void refreshDropdown()
    {
        if (segmentDropdown.isPopupVisible())
        {
            return;
        }

        List<EncounterSegment> segments = encounterManager.getAllSegmentsForDropdown();
        EncounterSegment selected = encounterManager.getSelectedOrCurrentEncounter();

        boolean needsNewModel = false;
        if (segmentDropdown.getItemCount() != segments.size())
        {
            needsNewModel = true;
        }
        else
        {
            for (int i = 0; i < segments.size(); i++)
            {
                if (segmentDropdown.getItemAt(i) != segments.get(i))
                {
                    needsNewModel = true;
                    break;
                }
            }
        }

        updatingDropdown = true;
        if (needsNewModel)
        {
            DefaultComboBoxModel<EncounterSegment> model = new DefaultComboBoxModel<>();
            for (EncounterSegment seg : segments)
            {
                model.addElement(seg);
            }
            segmentDropdown.setModel(model);
        }

        if (selected != null && segmentDropdown.getSelectedItem() != selected)
        {
            segmentDropdown.setSelectedItem(selected);
        }
        segmentDropdown.repaint();
        updatingDropdown = false;
    }

    private void showResetMenu(java.awt.Component anchor)
    {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        javax.swing.JMenuItem fight = new javax.swing.JMenuItem("Reset Live");
        fight.setToolTipText("Clear the current / most-recent fight");
        fight.addActionListener(a -> { encounterManager.resetCurrentEncounter(); refreshDropdown(); refreshUI(); });
        menu.add(fight);

        javax.swing.JMenuItem session = new javax.swing.JMenuItem("Reset Session");
        session.setToolTipText("Clear Current Session + past-fights list (keeps all-time Total)");
        session.addActionListener(a -> { encounterManager.resetSessionAndHistory(); refreshDropdown(); refreshUI(); });
        menu.add(session);

        menu.addSeparator();

        javax.swing.JMenuItem total = new javax.swing.JMenuItem("Reset Total (all-time)...");
        total.setToolTipText("Wipe the saved all-time totals");
        total.addActionListener(a ->
        {
            int r = javax.swing.JOptionPane.showConfirmDialog(this,
                "Wipe your saved all-time Total? This can't be undone.",
                "Reset Total", javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
            if (r == javax.swing.JOptionPane.YES_OPTION)
            {
                encounterManager.resetTotal();
                refreshDropdown();
                refreshUI();
            }
        });
        menu.add(total);

        menu.show(anchor, 0, anchor.getHeight());
    }

    public void refreshUI()
    {
        if (!isShowing())
        {
            return;
        }

        refreshPartyButton();

        EncounterSegment enc = encounterManager.getSelectedOrCurrentEncounter();
        if (enc == null)
        {
            targetNameLabel.setText("No Active Fight");
            dpsLabel.setText("DPS 0.0");
            dtpsLabel.setText("DTPS 0.0");
            hpsLabel.setText("HPS 0.0");
            costLabel.setText("Cost 0");
            viewBestiaryBtn.setVisible(false);
            return;
        }

        EntityCombatStats local = enc.getLocalPlayerStats();
        targetNameLabel.setText(scopeTitle(enc));

        if (enc.getSegmentType() == SegmentType.GROUP)
        {
            long groupTotal = 0;
            long groupTaken = 0;
            double groupDur = 1;
            for (EntityCombatStats m : enc.getRankedParticipants(64))
            {
                groupTotal += m.getTotalDamage();
                groupTaken += m.getDamageTaken();
                groupDur = Math.max(groupDur, m.getDurationSeconds());
            }
            dpsLabel.setText(String.format("Grp DPS %.1f", groupTotal / groupDur));
            dtpsLabel.setText(String.format("Grp DTPS %.1f", groupTaken / groupDur));
            hpsLabel.setText("Grp Dmg " + formatAmount(groupTotal));
            costLabel.setText(String.format("You %.1f dps", local.getDps()));
            viewBestiaryBtn.setVisible(false);
        }
        else
        {
            dpsLabel.setText(String.format("DPS %.1f", local.getDps()));
            dtpsLabel.setText(String.format("DTPS %.1f", local.getDtps()));
            hpsLabel.setText(String.format("HPS %.1f", local.getHps()));
            costLabel.setText(String.format("Cost %s", formatAmount(local.getTotalGpCost())));

            // Cross-link check
            Monster monster = monsterDatabase.getMonsterByName(enc.getTargetName());
            viewBestiaryBtn.setVisible(monster != null);
        }

        // Repopulate Cards
        updatePerKillBreakdown(enc);
        updateStyleBreakdown(enc);
        updateDefensiveEfficiency(enc);
        updateHealingSustenance(enc);
        updateBuffUptime(enc);
        updateConsumablesLedger(enc);
        updateTimelineLedger(enc);
    }

    private void addSubHeading(JPanel container, String text)
    {
        JLabel h = new JLabel(text);
        h.setFont(FontManager.getRunescapeSmallFont());
        h.setForeground(CopilotPalette.TEXT_MUTED);
        h.setBorder(new EmptyBorder(4, 0, 1, 0));
        h.setAlignmentX(LEFT_ALIGNMENT);
        container.add(h);
    }

    private void updateStyleBreakdown(EncounterSegment enc)
    {
        styleContentPanel.removeAll();

        if (enc.getSegmentType() == SegmentType.GROUP)
        {
            updateGroupMeter(enc);
            return;
        }

        EntityCombatStats stats = enc.getLocalPlayerStats();
        long total = stats.getTotalDamage();
        List<StyleDamageEntry> entries = stats.getStyleBreakdown();

        if (entries.isEmpty() && total == 0)
        {
            JLabel empty = new JLabel("<html><i>No damage recorded yet</i></html>");
            empty.setFont(FontManager.getRunescapeSmallFont());
            empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            styleContentPanel.add(empty);
        }
        else
        {
            // ---- Fight recap headline -------------------------------------------------------
            // NOTE: prayer accuracy / damage mitigated are still not shown - nothing feeds them
            // yet (see docs/COMBAT_HUD_POLISH.md B8 / F5). Only metrics with a real source show.
            addStatRow(styleContentPanel, "DPS:", String.format("%.1f", stats.getDps()), CopilotPalette.GOLD);
            addStatRow(styleContentPanel, "Total Damage:", formatAmount(total), Color.WHITE);
            if (stats.getPeakDps() > 0)
            {
                addStatRow(styleContentPanel, "Peak DPS:", String.format("%.1f", stats.getPeakDps()), CopilotPalette.ACCENT_MUTED);
            }
            if (stats.getAttackUptimeTicks() + stats.getLostCombatTicks() > 0)
            {
                addStatRow(styleContentPanel, "Attack Uptime:",
                    String.format("%.0f%%", stats.getAttackUptimePercent()), CopilotPalette.POSITIVE);
                addStatRow(styleContentPanel, "Active DPS:",
                    String.format("%.1f", stats.getActiveDps()), CopilotPalette.ACCENT_MUTED);
            }
            addStatRow(styleContentPanel, "Hit Accuracy:", String.format("%.1f%%", stats.getHitAccuracy()), CopilotPalette.GOLD);
            if (stats.getMaxHitDealt() != null)
            {
                addStatRow(styleContentPanel, "Max Hit:",
                    stats.getMaxHitDealt().getAmount() + " (" + stats.getMaxHitDealt().getSourceName() + ")", new Color(255, 112, 67));
            }
            if (stats.getSpecialAttacksCount() > 0)
            {
                addStatRow(styleContentPanel, "Specials:",
                    stats.getSpecialAttacksCount() + " (" + formatAmount(stats.getSpecialAttackDamage()) + " dmg)", new Color(255, 202, 40));
            }

            // ---- By combat style (incl. poison / venom / burn / bleed) --------------------
            if (!entries.isEmpty())
            {
                addSubHeading(styleContentPanel, "BY STYLE");
                long topStyle = entries.get(0).getDamage();
                for (StyleDamageEntry e : entries)
                {
                    double pct = total > 0 ? (e.getDamage() * 100.0) / total : 0.0;
                    styleContentPanel.add(new MeterRowPanel(
                        e.getStyleName(),
                        String.format("%s  %.0f%%", formatAmount(e.getDamage()), pct),
                        null,
                        topStyle > 0 ? (double) e.getDamage() / topStyle : 0.0,
                        e.getColor()));
                }
            }

            // ---- By weapon / spell / special --------------------------------------------
            List<WeaponAbilityEntry> weapons = stats.getWeaponBreakdown();
            if (!weapons.isEmpty())
            {
                addSubHeading(styleContentPanel, "BY WEAPON / SPELL  (click a row for detail)");
                long topW = weapons.get(0).getTotalDamage();
                double dur = Math.max(1, stats.getDurationSeconds());
                for (WeaponAbilityEntry w : weapons)
                {
                    MeterRowPanel row = new MeterRowPanel(
                        w.getWeaponName(),
                        formatAmount(w.getTotalDamage()),
                        w.getHitCount() + " hits  |  avg " + w.getAvgHit() + "  |  max " + w.getMaxHit(),
                        topW > 0 ? (double) w.getTotalDamage() / topW : 0.0,
                        CopilotPalette.TEXT_MUTED);
                    String wn = w.getWeaponName();
                    row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                    row.addMouseListener(new java.awt.event.MouseAdapter()
                    {
                        @Override
                        public void mousePressed(java.awt.event.MouseEvent e)
                        {
                            expandedWeapon = wn.equals(expandedWeapon) ? null : wn;
                            updateStyleBreakdown(encounterManager.getSelectedOrCurrentEncounter());
                        }
                    });
                    styleContentPanel.add(row);

                    if (wn.equals(expandedWeapon))
                    {
                        double wDps = w.getTotalDamage() / dur;
                        addStatRow(styleContentPanel, "   DPS:", String.format("%.1f", wDps), new Color(200, 200, 210));
                        addStatRow(styleContentPanel, "   Share of total:", pctStr(w.getTotalDamage(), Math.max(1, total)), new Color(200, 200, 210));
                        addStatRow(styleContentPanel, "   Hits / avg / max:",
                            w.getHitCount() + " / " + w.getAvgHit() + " / " + w.getMaxHit(), new Color(200, 200, 210));
                    }
                }
            }

            // ---- Targets: your damage split by which enemy it landed on (AoE / multi-mob) ----
            List<EntityCombatStats> enemies = enc.getEnemyBreakdown();
            if (enemies.size() > 1)
            {
                double dur = Math.max(1, stats.getDurationSeconds());
                long sumE = 0;
                for (EntityCombatStats e : enemies)
                {
                    sumE += e.getTotalDamage();
                }
                addSubHeading(styleContentPanel, "TARGETS  (" + enemies.size() + " enemies)");
                long topE = enemies.get(0).getTotalDamage();
                for (EntityCombatStats e : enemies)
                {
                    long hits = e.getSuccessfulHits();
                    String sub = e.getTotalDamage() > 0
                        ? String.format("%.1f DPS%s", e.getTotalDamage() / dur, hits > 0 ? "  |  " + hits + " hits" : "")
                        : null;
                    styleContentPanel.add(new MeterRowPanel(
                        e.getName(),
                        formatAmount(e.getTotalDamage()) + "  " + pctStr(e.getTotalDamage(), Math.max(1, sumE)),
                        sub,
                        topE > 0 ? (double) e.getTotalDamage() / topE : 0.0,
                        CopilotPalette.TEXT_MUTED));
                }
            }

            // ---- Other contributors (thrall / cannon) ----------------------------------
            long thrall = enc.getThrallStats().getTotalDamage();
            long cannon = enc.getCannonStats().getTotalDamage();
            if (thrall > 0 || cannon > 0)
            {
                addSubHeading(styleContentPanel, "CONTRIBUTORS");
                long refTotal = Math.max(1, total + thrall + cannon);
                addStatRow(styleContentPanel, "You:", formatAmount(total) + "  " + pctStr(total, refTotal), Color.WHITE);
                if (thrall > 0)
                {
                    addStatRow(styleContentPanel, "Thrall:", formatAmount(thrall) + "  " + pctStr(thrall, refTotal), new Color(186, 104, 200));
                }
                if (cannon > 0)
                {
                    addStatRow(styleContentPanel, "Cannon:", formatAmount(cannon) + "  " + pctStr(cannon, refTotal), new Color(255, 167, 38));
                }
            }
        }

        styleBreakdownCard.setSummaryText(formatAmount(total) + " Dmg");
        styleContentPanel.revalidate();
        styleContentPanel.repaint();
    }

    /**
     * The GROUP scope: a ranked, click-to-drill party damage meter. Each client is authoritative
     * for its own row; teammate rows are rebuilt from their broadcast snapshots. Bars rank by total
     * damage; click a member to expand their weapon / spell and style breakdown.
     */
    private void updateGroupMeter(EncounterSegment enc)
    {
        boolean inParty = combatPartyService != null && combatPartyService.inParty();
        if (!inParty)
        {
            JLabel hint = new JLabel("<html><i>Not in a party. Use the <b>Party</b> button below to"
                + " join or start one - every member runs this plugin and enters the same"
                + " passphrase.</i></html>");
            hint.setFont(FontManager.getRunescapeSmallFont());
            hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            styleContentPanel.add(hint);
            styleBreakdownCard.setSummaryText("No party");
            styleContentPanel.revalidate();
            styleContentPanel.repaint();
            return;
        }

        List<EntityCombatStats> members = enc.getRankedParticipants(64);
        long groupTotal = 0;
        double groupDur = 1;
        for (EntityCombatStats m : members)
        {
            groupTotal += m.getTotalDamage();
            groupDur = Math.max(groupDur, m.getDurationSeconds());
        }

        // Group DPS and total already show in the metric grid at the top of this panel; the party
        // size is in the "Group (N)" scope title, and the passphrase is on the Party button. Keep
        // the card body to just the ranked member bars so nothing is said twice.
        addSubHeading(styleContentPanel, "MEMBERS (" + members.size() + ")  tap a row to expand");

        long topDmg = members.isEmpty() ? 1 : Math.max(1, members.get(0).getTotalDamage());
        EntityCombatStats mineStats = enc.getLocalPlayerStats();
        for (EntityCombatStats m : members)
        {
            boolean isMe = m == mineStats;
            double dur = Math.max(1, m.getDurationSeconds());
            long hits = m.getSuccessfulHits();
            String sub = String.format("%.1f DPS%s", m.getTotalDamage() / dur,
                hits > 0 ? "  |  " + hits + " hits" : "");
            MeterRowPanel row = new MeterRowPanel(
                m.getName() + (isMe ? "  (you)" : ""),
                formatAmount(m.getTotalDamage()) + "  " + pctStr(m.getTotalDamage(), Math.max(1, groupTotal)),
                sub,
                topDmg > 0 ? (double) m.getTotalDamage() / topDmg : 0.0,
                isMe ? new Color(255, 202, 40) : CopilotPalette.TEXT_MUTED);

            final String key = m.getName();
            row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            row.addMouseListener(new java.awt.event.MouseAdapter()
            {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e)
                {
                    expandedMember = key.equals(expandedMember) ? null : key;
                    updateStyleBreakdown(encounterManager.getSelectedOrCurrentEncounter());
                }
            });
            styleContentPanel.add(row);

            if (key.equals(expandedMember))
            {
                List<WeaponAbilityEntry> wb = m.getWeaponBreakdown();
                if (wb.isEmpty())
                {
                    addStatRow(styleContentPanel, "   (no weapon detail sent)", "", new Color(150, 150, 160));
                }
                else
                {
                    long topW = Math.max(1, wb.get(0).getTotalDamage());
                    for (WeaponAbilityEntry w : wb)
                    {
                        styleContentPanel.add(new MeterRowPanel(
                            "   " + w.getWeaponName(),
                            formatAmount(w.getTotalDamage()) + "  " + pctStr(w.getTotalDamage(), Math.max(1, m.getTotalDamage())),
                            w.getHitCount() + " hits  |  avg " + w.getAvgHit() + "  |  max " + w.getMaxHit(),
                            (double) w.getTotalDamage() / topW,
                            new Color(150, 150, 160)));
                    }
                }
                for (StyleDamageEntry s : m.getStyleBreakdown())
                {
                    addStatRow(styleContentPanel, "   " + s.getStyleName() + ":",
                        formatAmount(s.getDamage()) + "  " + pctStr(s.getDamage(), Math.max(1, m.getTotalDamage())),
                        s.getColor());
                }
            }
        }

        styleBreakdownCard.setSummaryText(formatAmount(groupTotal) + " Grp");
        styleContentPanel.revalidate();
        styleContentPanel.repaint();
    }

    private static String pctStr(long part, long whole)
    {
        return String.format("%.0f%%", whole > 0 ? (part * 100.0) / whole : 0.0);
    }

    private void updateDefensiveEfficiency(EncounterSegment enc)
    {
        defensiveContentPanel.removeAll();
        EntityCombatStats stats = enc.getLocalPlayerStats();

        long taken = stats.getDamageTaken();
        addStatRow(defensiveContentPanel, "Damage Taken:", formatAmount(taken) + " HP", CopilotPalette.NEGATIVE);
        addStatRow(defensiveContentPanel, "DTPS:", String.format("%.1f", stats.getDtps()), CopilotPalette.NEGATIVE);
        // No prayer-accuracy / damage-mitigated readout: an OSRS client gets no reliable signal
        // for "an incoming attack the right overhead was up for", so that dead path was removed.
        if (stats.getMaxHitTaken() != null)
        {
            String src = stats.getMaxHitTaken().getSourceName();
            String tag = (src != null && !src.isEmpty() && !src.equals("Enemy"))
                ? src : stats.getMaxHitTaken().getStyle().getDisplayName();
            addStatRow(defensiveContentPanel, "Biggest Hit Taken:",
                stats.getMaxHitTaken().getAmount() + " (" + tag + ")", CopilotPalette.NEGATIVE);
        }

        List<StyleDamageEntry> takenStyles = stats.getDamageTakenByStyle();
        if (!takenStyles.isEmpty())
        {
            addSubHeading(defensiveContentPanel, "DAMAGE TAKEN BY STYLE");
            long topT = takenStyles.get(0).getDamage();
            for (StyleDamageEntry e : takenStyles)
            {
                defensiveContentPanel.add(new MeterRowPanel(
                    e.getStyleName(),
                    formatAmount(e.getDamage()) + "  " + pctStr(e.getDamage(), Math.max(1, taken)),
                    null,
                    topT > 0 ? (double) e.getDamage() / topT : 0.0,
                    e.getColor()));
            }
        }

        java.util.Map<String, Long> bySource = stats.getDamageTakenBySource();
        if (!bySource.isEmpty())
        {
            java.util.Map<String, Integer> hitsBySource = stats.getDamageTakenHitsBySource();
            addSubHeading(defensiveContentPanel, bySource.size() > 1
                ? "BY SOURCE  (" + bySource.size() + " attackers)" : "BY SOURCE");
            long topS = bySource.values().stream().mapToLong(Long::longValue).max().orElse(1);
            bySource.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(8)
                .forEach(en ->
                {
                    int hits = hitsBySource.getOrDefault(en.getKey(), 0);
                    long avg = hits > 0 ? Math.round(en.getValue() / (double) hits) : 0;
                    defensiveContentPanel.add(new MeterRowPanel(
                        en.getKey(),
                        formatAmount(en.getValue()) + "  " + pctStr(en.getValue(), Math.max(1, taken)),
                        hits > 0 ? hits + " hits  |  avg " + avg : null,
                        topS > 0 ? (double) en.getValue() / topS : 0.0,
                        new Color(239, 120, 120)));
                });
        }

        defensiveEfficiencyCard.setSummaryText("Tkn: " + formatAmount(taken));
        defensiveContentPanel.revalidate();
        defensiveContentPanel.repaint();
    }

    /**
     * When the selected scope is a merged "Target x N" trash row, list its individual kills; the
     * on-canvas HUD dropdown does the same via its [+] caret. Hidden for every other scope.
     */
    private void updatePerKillBreakdown(EncounterSegment enc)
    {
        perKillContentPanel.removeAll();
        boolean show = enc != null && enc.isMerged() && enc.hasMergedChildren();
        perKillCard.setVisible(show);
        if (!show)
        {
            return;
        }

        List<EncounterSegment.KillSummary> kills = enc.getMergedChildren();
        for (EncounterSegment.KillSummary k : kills)
        {
            boolean wiped = k.getStatus() == SegmentStatus.WIPED;
            boolean open = k.getIndex() == expandedKillIndex;
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            row.setBorder(new EmptyBorder(1, 2, 1, 2));
            row.setAlignmentX(LEFT_ALIGNMENT);

            JLabel left = new JLabel((open ? "[-] " : "[+] ") + "#" + k.getIndex() + "   "
                + com.osrscopilot.combat.CombatFormat.duration(k.getDurationSeconds()));
            left.setFont(FontManager.getRunescapeSmallFont());
            left.setForeground(Color.WHITE);

            JLabel right = new JLabel(formatAmount(Math.round(k.getDps())) + " dps   "
                + formatAmount(k.getDamageTaken()) + " tkn   " + (wiped ? "wipe" : "kill"));
            right.setFont(FontManager.getRunescapeSmallFont());
            right.setForeground(wiped ? CopilotPalette.NEGATIVE : ColorScheme.LIGHT_GRAY_COLOR);

            row.add(left, BorderLayout.WEST);
            row.add(right, BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            row.setToolTipText("Click to " + (open ? "collapse" : "show") + " this kill's breakdown");
            final int idx = k.getIndex();
            row.addMouseListener(new java.awt.event.MouseAdapter()
            {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e)
                {
                    // Toggle the in-place drill-in; the child EncounterSegments aren't retained past
                    // the merge, so this expands the KillSummary snapshot rather than re-selecting a
                    // scope. The top accordion (perKillCard's own header) is untouched.
                    expandedKillIndex = (idx == expandedKillIndex) ? -1 : idx;
                    updatePerKillBreakdown(encounterManager.getSelectedOrCurrentEncounter());
                }
            });
            perKillContentPanel.add(row);

            if (open)
            {
                perKillContentPanel.add(buildKillDetail(k, wiped));
            }
            perKillContentPanel.add(Box.createRigidArea(new Dimension(0, 1)));
        }
        perKillCard.setSummaryText(kills.size() + " kills");
        perKillContentPanel.revalidate();
        perKillContentPanel.repaint();
    }

    /** The in-place drill-in for one per-kill row: the full {@link EncounterSegment.KillSummary}. */
    private JPanel buildKillDetail(EncounterSegment.KillSummary k, boolean wiped)
    {
        JPanel detail = new JPanel();
        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.setBackground(ColorScheme.DARK_GRAY_COLOR);
        detail.setAlignmentX(LEFT_ALIGNMENT);
        detail.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 2, 0, 0, CopilotPalette.ACCENT_MUTED),
            new EmptyBorder(2, 6, 3, 2)));

        addStatRow(detail, "DPS:", String.format("%.1f", k.getDps()), CopilotPalette.GOLD);
        addStatRow(detail, "DTPS:", String.format("%.1f", k.getDtps()), CopilotPalette.NEGATIVE);
        addStatRow(detail, "Damage:", formatAmount(k.getTotalDamage()), Color.WHITE);
        addStatRow(detail, "Taken:", formatAmount(k.getDamageTaken()) + " HP", CopilotPalette.TAKEN);
        addStatRow(detail, "Duration:",
            com.osrscopilot.combat.CombatFormat.duration(k.getDurationSeconds()), ColorScheme.LIGHT_GRAY_COLOR);
        addStatRow(detail, "Result:", wiped ? "Wipe" : "Kill",
            wiped ? CopilotPalette.NEGATIVE : CopilotPalette.POSITIVE);
        if (k.getEndTimestamp() != null)
        {
            addStatRow(detail, "Ended:", KILL_END_FMT.format(k.getEndTimestamp()), ColorScheme.LIGHT_GRAY_COLOR);
        }
        detail.setMaximumSize(new Dimension(Integer.MAX_VALUE, detail.getPreferredSize().height));
        return detail;
    }

    /**
     * Render the per-kill breakdown for {@code enc} with kill {@code drillIndex} drilled in
     * ({@code -1} = none) and return every row / detail label, top to bottom. Test hook - mirrors
     * what {@link #updatePerKillBreakdown} paints without needing the panel on screen.
     */
    public List<String> perKillLinesFor(EncounterSegment enc, int drillIndex)
    {
        expandedKillIndex = drillIndex;
        updatePerKillBreakdown(enc);
        List<String> out = new java.util.ArrayList<>();
        collectLabelText(perKillContentPanel, out);
        return out;
    }

    private static void collectLabelText(java.awt.Container c, List<String> out)
    {
        for (Component comp : c.getComponents())
        {
            if (comp instanceof JLabel)
            {
                String t = ((JLabel) comp).getText();
                if (t != null && !t.isEmpty())
                {
                    out.add(t);
                }
            }
            if (comp instanceof java.awt.Container)
            {
                collectLabelText((java.awt.Container) comp, out);
            }
        }
    }

    /** Whether the per-kill breakdown card is currently visible (merged scope only). Test hook. */
    public boolean isPerKillCardVisible()
    {
        return perKillCard.isVisible();
    }

    /** The "share &amp; party" row's current border - null once the tour clears its highlight. Test hook. */
    public javax.swing.border.Border shareRowBorderForTest()
    {
        return shareRow.getBorder();
    }

    /** The hero-banner's current border - restored to its compound border once the tour ends. Test hook. */
    public javax.swing.border.Border heroBannerBorderForTest()
    {
        return heroBanner.getBorder();
    }

    private final java.util.Map<javax.swing.JComponent, javax.swing.border.Border> tourSavedBorders =
        new java.util.HashMap<>();

    /**
     * The interactive tutorial's panel walk-through: scroll to a named section, expand it, and ring
     * it in the tour accent so exactly one section is lit at a time. {@code null} clears every
     * highlight (call it when the tour ends). Keys: "top", "damage", "ledger", "share". EDT only.
     */
    public void tourScrollTo(String key)
    {
        setTourLit(styleBreakdownCard, false);
        setTourLit(timelineLedgerCard, false);
        setPanelLit(heroBanner, false);
        setPanelLit(shareRow, false);
        if (key == null)
        {
            return;
        }
        switch (key)
        {
            case "top":
                if (scrollPane != null)
                {
                    scrollPane.getVerticalScrollBar().setValue(0);
                }
                setPanelLit(heroBanner, true);
                break;
            case "damage":
                styleBreakdownCard.setExpanded(true);
                setTourLit(styleBreakdownCard, true);
                scrollIntoView(styleBreakdownCard);
                break;
            case "ledger":
                timelineLedgerCard.setExpanded(true);
                setTourLit(timelineLedgerCard, true);
                scrollIntoView(timelineLedgerCard);
                break;
            case "share":
                setPanelLit(shareRow, true);
                scrollIntoView(shareRow);
                break;
            default:
                break;
        }
    }

    private void setTourLit(CollapsibleSectionCard card, boolean on)
    {
        if (card != null)
        {
            card.setTourHighlight(on);
        }
    }

    private void setPanelLit(javax.swing.JComponent c, boolean on)
    {
        if (c == null)
        {
            return;
        }
        if (on)
        {
            // shareRow has no border of its own, so the saved value is legitimately null. Track
            // membership with containsKey, not "saved != null", or the null never gets restored and
            // the ring sticks after the tour ends (tourScrollTo(null)).
            if (!tourSavedBorders.containsKey(c))
            {
                tourSavedBorders.put(c, c.getBorder());
            }
            c.setBorder(BorderFactory.createLineBorder(CopilotPalette.ACCENT, 2));
        }
        else if (tourSavedBorders.containsKey(c))
        {
            c.setBorder(tourSavedBorders.remove(c));
        }
        c.revalidate();
        c.repaint();
    }

    private void scrollIntoView(javax.swing.JComponent c)
    {
        if (c == null)
        {
            return;
        }
        SwingUtilities.invokeLater(() ->
            c.scrollRectToVisible(new java.awt.Rectangle(0, 0, c.getWidth(), Math.max(1, c.getHeight()))));
    }

    private void updateHealingSustenance(EncounterSegment enc)
    {
        healingContentPanel.removeAll();
        EntityCombatStats stats = enc.getLocalPlayerStats();

        addStatRow(healingContentPanel, "Effective Healing:", formatAmount(stats.getHpHealed()) + " HP", CopilotPalette.POSITIVE);
        if (stats.getHpOverhealed() > 0)
        {
            addStatRow(healingContentPanel, "Overhealing (Wasted):", formatAmount(stats.getHpOverhealed()) + " HP", CopilotPalette.ACCENT_MUTED);
        }
        addStatRow(healingContentPanel, "Food Eaten:", stats.getFoodEatenCount() + " items", Color.WHITE);
        addStatRow(healingContentPanel, "Potions Drank:", stats.getPotionsDrunkCount() + " doses", Color.WHITE);
        if (stats.getPrayerPointsRestored() > 0)
        {
            addStatRow(healingContentPanel, "Prayer Restored:", stats.getPrayerPointsRestored() + " pts", new Color(100, 181, 246));
        }

        healingSustenanceCard.setSummaryText("Heal: " + formatAmount(stats.getHpHealed()));
        healingContentPanel.revalidate();
        healingContentPanel.repaint();
    }

    private void updateBuffUptime(EncounterSegment enc)
    {
        buffContentPanel.removeAll();
        if (encounterManager.getBuffTracker() == null) return;

        EntityCombatStats stats = enc.getLocalPlayerStats();
        int totalTicks = (int) (stats.getDurationSeconds() / 0.6);
        // Window end = this scope's end tick (kept ~= now for the live fight and the session
        // scopes by onGameTick.updateDuration; frozen at the kill for a finished fight). Passing
        // the raw live tick made a finished fight's open ranges keep accruing "uptime" after it.
        int windowEnd = enc.getEndTick() > 0 ? enc.getEndTick() : encounterManager.getCurrentTick();
        List<com.osrscopilot.combat.engine.BuffTrackingEngine.BuffUptimeSnapshot> snapshots =
            encounterManager.getBuffTracker().getActiveAndRecentSnapshots(
                windowEnd, Math.max(1, totalTicks));

        if (snapshots.isEmpty())
        {
            JLabel empty = new JLabel("<html><i>No active buffs recorded</i></html>");
            empty.setFont(FontManager.getRunescapeSmallFont());
            empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            buffContentPanel.add(empty);
            buffUptimeCard.setSummaryText("0 Active");
        }
        else
        {
            int activeCount = 0;
            for (com.osrscopilot.combat.engine.BuffTrackingEngine.BuffUptimeSnapshot snap : snapshots)
            {
                if (snap.isCurrentlyActive()) activeCount++;
                buffContentPanel.add(new BuffUptimeRowPanel(snap));
                buffContentPanel.add(Box.createRigidArea(new Dimension(0, 2)));
            }
            buffUptimeCard.setSummaryText(activeCount + " Active");
        }

        buffContentPanel.revalidate();
        buffContentPanel.repaint();
    }

    private void updateConsumablesLedger(EncounterSegment enc)
    {
        consumablesContentPanel.removeAll();
        EntityCombatStats stats = enc.getLocalPlayerStats();
        List<ConsumableUsageEntry> entries = stats.getConsumablesUsed();

        if (entries.isEmpty() && stats.getTotalGpCost() == 0)
        {
            JLabel empty = new JLabel("<html><i>No resource costs recorded</i></html>");
            empty.setFont(FontManager.getRunescapeSmallFont());
            empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            consumablesContentPanel.add(empty);
        }
        else
        {
            for (ConsumableUsageEntry entry : entries)
            {
                JPanel row = new JPanel(new BorderLayout(4, 0));
                row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                row.setBorder(new EmptyBorder(1, 0, 1, 0));

                JLabel name = new JLabel(entry.getQuantity() + "x " + entry.getItemName());
                name.setFont(FontManager.getRunescapeSmallFont());
                name.setForeground(Color.WHITE);

                JLabel priceLbl = new JLabel(formatAmount(entry.getTotalCost()) + " GP");
                priceLbl.setFont(FontManager.getRunescapeSmallFont());
                priceLbl.setForeground(CopilotPalette.GOLD);

                row.add(name, BorderLayout.WEST);
                row.add(priceLbl, BorderLayout.EAST);
                consumablesContentPanel.add(row);
            }

            addStatRow(consumablesContentPanel, "Total Supply Cost:", formatAmount(stats.getTotalGpCost()) + " GP", CopilotPalette.GOLD);
            addStatRow(consumablesContentPanel, "Supply Burn/Hr:", formatAmount((long) stats.getGpPerHour()) + " GP/hr", CopilotPalette.ACCENT_MUTED);
        }

        consumablesLedgerCard.setSummaryText(formatAmount(stats.getTotalGpCost()) + " GP");
        consumablesContentPanel.revalidate();
        consumablesContentPanel.repaint();
    }

    /**
     * Blocked / 0-damage hits-taken are restored to the Full Log for completeness, but they would
     * spam this HUD-adjacent card - keep it a signal feed and drop them from both the collapsed and
     * expanded views here.
     */
    private static boolean shownInLedger(com.osrscopilot.combat.model.CombatTimelineEvent ev)
    {
        // The HUD ledger is a signal feed: drop 0-damage hits/blocks/splashes (kept in the Full
        // Log). Buff toggles, CC, heals and fight boundaries are always shown.
        String t = ev.getEventType();
        return ev.getAmount() != 0 || (!"TAKEN".equals(t) && !"HIT".equals(t));
    }

    /** Event descriptions the action-ledger card shows for {@code enc}, oldest first. Test hook. */
    public List<String> ledgerDescriptionsFor(EncounterSegment enc)
    {
        List<String> out = new java.util.ArrayList<>();
        for (com.osrscopilot.combat.model.CombatTimelineEvent ev : enc.getLocalPlayerStats().getTimelineEvents())
        {
            if (shownInLedger(ev))
            {
                out.add(ev.getDescription());
            }
        }
        return out;
    }

    private String lastLedgerKey = "";

    private void updateTimelineLedger(EncounterSegment enc)
    {
        EntityCombatStats stats = enc.getLocalPlayerStats();
        List<com.osrscopilot.combat.model.CombatTimelineEvent> events = new java.util.ArrayList<>();
        for (com.osrscopilot.combat.model.CombatTimelineEvent ev : stats.getTimelineEvents())
        {
            if (shownInLedger(ev))
            {
                events.add(ev);
            }
        }

        // Skip the removeAll() + rebuild-N-panels churn (every 120 ms during a fight) when the
        // visible ledger hasn't actually changed.
        String key = System.identityHashCode(enc) + "|" + events.size() + "|" + timelineExpanded
            + "|" + (events.isEmpty() ? 0 : System.identityHashCode(events.get(events.size() - 1)));
        if (key.equals(lastLedgerKey))
        {
            return;
        }
        lastLedgerKey = key;

        timelineContentPanel.removeAll();

        if (events.isEmpty())
        {
            JLabel empty = new JLabel("<html><i>Combat action stream will appear here</i></html>");
            empty.setFont(FontManager.getRunescapeSmallFont());
            empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            timelineContentPanel.add(empty);
        }
        else
        {
            int shown = timelineExpanded ? events.size() : Math.min(TIMELINE_COLLAPSED_ROWS, events.size());
            int start = events.size() - shown;
            for (int i = events.size() - 1; i >= start; i--)
            {
                com.osrscopilot.combat.model.CombatTimelineEvent ev = events.get(i);
                JPanel row = new JPanel(new BorderLayout(4, 0));
                row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                row.setBorder(new EmptyBorder(1, 0, 1, 0));

                JLabel tag = new JLabel("[" + ev.getTimeFormatted() + "] " + ev.getIcon());
                tag.setFont(FontManager.getRunescapeSmallFont());
                tag.setForeground(ev.getColor());

                JLabel desc = new JLabel(ev.getDescription());
                desc.setFont(FontManager.getRunescapeSmallFont());
                desc.setForeground(Color.WHITE);

                row.add(tag, BorderLayout.WEST);
                row.add(desc, BorderLayout.CENTER);
                timelineContentPanel.add(row);
            }

            if (events.size() > TIMELINE_COLLAPSED_ROWS)
            {
                JButton toggle = new JButton(timelineExpanded
                    ? "Show less"
                    : "Show all " + events.size());
                toggle.setFont(FontManager.getRunescapeSmallFont());
                toggle.setForeground(CopilotPalette.RATE);
                toggle.setFocusPainted(false);
                toggle.setBorder(new EmptyBorder(2, 0, 2, 0));
                toggle.setContentAreaFilled(false);
                toggle.addActionListener(e ->
                {
                    timelineExpanded = !timelineExpanded;
                    updateTimelineLedger(enc);
                });
                timelineContentPanel.add(toggle);
            }
        }

        timelineLedgerCard.setSummaryText(events.size() + " Events");
        timelineContentPanel.revalidate();
        timelineContentPanel.repaint();
    }

    private void copyCombatReport()
    {
        encounterManager.shareCopy();
    }

    /** Row under the Action Ledger: copy the one-line summary (optionally with a chat-channel prefix). */
    private JPanel buildShareRow()
    {
        JPanel bar = new JPanel(new GridLayout(1, 4, 2, 0));
        bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.add(shareBtn("Copy", "Copy the one-line combat summary to the clipboard",
            new Color(77, 208, 225), e -> encounterManager.shareCopy()));
        bar.add(shareBtn("Public", "Copy the summary to the clipboard - paste it into Public chat yourself",
            ColorScheme.LIGHT_GRAY_COLOR, e -> encounterManager.shareToChat(com.osrscopilot.combat.ShareChannel.PUBLIC)));
        bar.add(shareBtn("FC", "Copy the summary with a '/' prefix - paste it into Friends Chat yourself",
            ColorScheme.LIGHT_GRAY_COLOR, e -> encounterManager.shareToChat(com.osrscopilot.combat.ShareChannel.FRIENDS)));
        bar.add(shareBtn("Clan", "Copy the summary with a '//' prefix - paste it into Clan chat yourself",
            ColorScheme.LIGHT_GRAY_COLOR, e -> encounterManager.shareToChat(com.osrscopilot.combat.ShareChannel.CLAN)));
        return bar;
    }

    private JButton shareBtn(String text, String tip, Color fg, java.awt.event.ActionListener onClick)
    {
        JButton b = new JButton(text);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        b.setForeground(fg);
        b.setToolTipText(tip);
        b.setFocusPainted(false);
        b.setMargin(new Insets(1, 2, 1, 2));
        b.addActionListener(onClick);
        return b;
    }

    private void addStatRow(JPanel container, String label, String value, Color valueColor)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(1, 0, 1, 0));

        JLabel lbl = new JLabel(label);
        lbl.setFont(FontManager.getRunescapeSmallFont());
        lbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JLabel val = new JLabel(value);
        val.setFont(FontManager.getRunescapeSmallFont());
        val.setForeground(valueColor);

        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.EAST);
        container.add(row);
    }

    private String formatAmount(long amt)
    {
        return com.osrscopilot.combat.CombatFormat.amount(amt);
    }

    /**
     * Hero-banner title for the selected scope. The two Overall scopes show wall-clock elapsed in
     * parentheses (their DPS divides by combat time, so the paren is the only place
     * "how long you've been at it" appears).
     */
    private String scopeTitle(EncounterSegment enc)
    {
        if (enc.getSegmentType() == SegmentType.SESSION_CURRENT)
        {
            return "Current Session  (" + com.osrscopilot.combat.CombatFormat.duration(enc.getWallClockSeconds()) + " in)";
        }
        if (enc.getSegmentType() == SegmentType.SESSION_TOTAL)
        {
            return "Total (all-time)";
        }
        if (enc.getSegmentType() == SegmentType.GROUP)
        {
            int n = combatPartyService != null ? combatPartyService.memberCount() : enc.getRankedParticipants(64).size();
            String t = enc.getTargetName();
            boolean named = t != null && !t.isEmpty() && !"Group".equals(t);
            return "Group (" + n + ")" + (named ? " - " + t : "");
        }
        if (enc.isMerged())
        {
            return enc.getTargetName() + " x" + enc.getMergedKills();
        }
        return enc.getTargetNameWithLevel();
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


