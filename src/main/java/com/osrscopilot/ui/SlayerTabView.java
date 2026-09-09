package com.osrscopilot.ui;

import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.SlayerRewardCatalog;
import com.osrscopilot.data.SlayerTaskManager;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterSpawnZone;
import com.osrscopilot.data.model.SlayerMaster;
import com.osrscopilot.data.model.SlayerReward;
import com.osrscopilot.data.model.SlayerReward.RewardType;
import com.osrscopilot.data.model.SlayerTaskAssignment;
import com.osrscopilot.combat.tutorial.TourHighlight;
import com.osrscopilot.ui.theme.CopilotPalette;
import com.osrscopilot.ui.theme.SidebarMetrics;
import com.osrscopilot.util.NpcPortraitManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

@Slf4j
public class SlayerTabView extends JPanel
{
    // Shared semantic colours come from CopilotPalette. The raw new Color(...) values still inline
    // below (nested task-table chrome greys, the Slayer-local loot/weight yellows and finance
    // greens) are intentional one-offs, not roles.
    private static final Color TITLE_COLOR = CopilotPalette.ACCENT;
    private static final Color FOCUS_BLUE = CopilotPalette.LINK;
    private static final Color SLAYER_PURPLE = CopilotPalette.SLAYER;
    private static final Color QUEST_PURPLE = CopilotPalette.QUEST;
    private static final Color WEAKNESS_GREEN = CopilotPalette.WEAKNESS;
    // Konar's location-task badge keeps its own amber-on-brown pair - intentional one-off.
    private static final Color KONAR_GOLD = new Color(245, 158, 11);
    private static final Color KONAR_BADGE_BG = new Color(58, 42, 16);
    private static final Color WILDY_RED = CopilotPalette.WILDY;
    private static final Color MULTI_ORANGE = CopilotPalette.MULTI;
    // Vivid "unlocked / progress" green, deliberately brighter than CopilotPalette.POSITIVE.
    private static final Color UNLOCKED_GREEN = new Color(34, 197, 94);
    private static final Color REWARD_GOLD = CopilotPalette.GOLD;

    private final SlayerTaskManager slayerTaskManager;
    private final MonsterDatabase monsterDatabase;
    private final NpcPortraitManager npcPortraitManager;
    private final BiConsumer<Monster, MonsterSpawnZone> onFocusMonsterZoneOnMap;
    private final BiConsumer<WorldPoint, String> onFocusPointOnMap;
    private final Consumer<Monster> onOpenMonsterDetail;

    // Master container components
    private final JPanel taskContainer = new JPanel();
    private final JPanel spawnsContainer = new JPanel();
    private final JPanel rewardsContainer = new JPanel();
    private final JPanel mastersContainer = new JPanel();

    private boolean rewardsExpanded = false;
    private String selectedRewardFilter = "All";
    // A task change arrived while the tab was hidden - rebuild once it is shown again.
    private boolean refreshPending = false;
    private boolean builtOnce = false;

    // Guided Slayer tour: rings one section of the tab. tourSubtypePicker is re-captured on every
    // rebuild because the task card (and its "Killing:" dropdown) is thrown away and rebuilt.
    private final TourHighlight tourHighlight = new TourHighlight();
    private String tourFocusKey = null;
    private JComponent tourSubtypePicker = null;

    public SlayerTabView(
        SlayerTaskManager slayerTaskManager,
        MonsterDatabase monsterDatabase,
        NpcPortraitManager npcPortraitManager,
        BiConsumer<Monster, MonsterSpawnZone> onFocusMonsterZoneOnMap,
        BiConsumer<WorldPoint, String> onFocusPointOnMap,
        Consumer<Monster> onOpenMonsterDetail)
    {
        this.slayerTaskManager = slayerTaskManager;
        this.monsterDatabase = monsterDatabase;
        this.npcPortraitManager = npcPortraitManager;
        this.onFocusMonsterZoneOnMap = onFocusMonsterZoneOnMap;
        this.onFocusPointOnMap = onFocusPointOnMap;
        this.onOpenMonsterDetail = onOpenMonsterDetail;

        setLayout(new BorderLayout(0, 0));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setBackground(ColorScheme.DARK_GRAY_COLOR);
        mainContent.setBorder(new EmptyBorder(4, 4, 4, 4));

        // 1. Task Area
        taskContainer.setLayout(new BoxLayout(taskContainer, BoxLayout.Y_AXIS));
        taskContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        taskContainer.setAlignmentX(LEFT_ALIGNMENT);
        mainContent.add(taskContainer);
        mainContent.add(Box.createRigidArea(new Dimension(0, 5)));

        // 2. Spawns Area
        spawnsContainer.setLayout(new BoxLayout(spawnsContainer, BoxLayout.Y_AXIS));
        spawnsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        spawnsContainer.setAlignmentX(LEFT_ALIGNMENT);
        mainContent.add(spawnsContainer);
        mainContent.add(Box.createRigidArea(new Dimension(0, 6)));

        // 3. Slayer Rewards & Unlocks Area
        rewardsContainer.setLayout(new BoxLayout(rewardsContainer, BoxLayout.Y_AXIS));
        rewardsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        rewardsContainer.setAlignmentX(LEFT_ALIGNMENT);
        mainContent.add(rewardsContainer);
        mainContent.add(Box.createRigidArea(new Dimension(0, 6)));

        // 4. Slayer Masters Directory Header
        JPanel mastersHeaderRow = new JPanel(new BorderLayout(4, 0));
        mastersHeaderRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        mastersHeaderRow.setAlignmentX(LEFT_ALIGNMENT);
        mastersHeaderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel mastersTitle = new JLabel("Slayer Masters Directory (" + SlayerMaster.ALL_MASTERS.size() + ")");
        mastersTitle.setFont(FontManager.getRunescapeBoldFont());
        mastersTitle.setForeground(TITLE_COLOR);
        mastersHeaderRow.add(mastersTitle, BorderLayout.WEST);

        mainContent.add(mastersHeaderRow);
        mainContent.add(Box.createRigidArea(new Dimension(0, 3)));

        // 5. Masters List
        mastersContainer.setLayout(new BoxLayout(mastersContainer, BoxLayout.Y_AXIS));
        mastersContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        mastersContainer.setAlignmentX(LEFT_ALIGNMENT);
        buildMastersList();
        mainContent.add(mastersContainer);

        JPanel contentWrapper = new ScrollableContentPanel();
        contentWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        contentWrapper.add(mainContent, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(contentWrapper);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);

        // Register listener for real-time task updates
        if (slayerTaskManager != null)
        {
            slayerTaskManager.addChangeListener(this::refresh);
        }

        // Every on-task kill fires the change listener; a full 3-container rebuild while the tab is
        // hidden is wasted. Defer it and catch up the next time the tab is actually shown.
        addHierarchyListener(e ->
        {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                && isShowing() && refreshPending)
            {
                refresh();
            }
        });

        refresh();
    }

    public void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        // Once we've painted at least one full build, defer hidden-tab rebuilds until it's shown.
        if (builtOnce && !isShowing())
        {
            refreshPending = true;
            return;
        }
        refreshPending = false;

        rebuildTaskView();
        rebuildSpawnsView();
        rebuildRewardsView();
        applyTourFocus();
        revalidate();
        repaint();
        builtOnce = true;
    }

    /**
     * Ring one part of the Slayer tab for the guided tour. Keys: {@code task} / {@code picker} /
     * {@code spawns} / {@code rewards} / {@code masters}; {@code null} clears the highlight.
     */
    public void tourFocus(String key)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            final String k = key;
            SwingUtilities.invokeLater(() -> tourFocus(k));
            return;
        }
        this.tourFocusKey = key;
        applyTourFocus();
    }

    private void applyTourFocus()
    {
        JComponent target = null;
        if (tourFocusKey != null)
        {
            switch (tourFocusKey)
            {
                case "task":    target = taskContainer; break;
                case "picker":  target = tourSubtypePicker != null ? tourSubtypePicker : taskContainer; break;
                case "spawns":  target = spawnsContainer; break;
                case "rewards": target = rewardsContainer; break;
                case "masters": target = mastersContainer; break;
                default:        target = null; break;
            }
        }
        tourHighlight.set(target);
        if (target != null)
        {
            TourHighlight.scrollIntoView(target);
        }
    }

    private static String formatGp(long gp)
    {
        if (gp >= 1_000_000)
        {
            return String.format("%.1fM", gp / 1_000_000.0);
        }
        return gp >= 1_000 ? (gp / 1000) + "k" : String.valueOf(gp);
    }

    private void rebuildTaskView()
    {
        taskContainer.removeAll();
        tourSubtypePicker = null;

        // Points & Streak Header Bar
        int pts = slayerTaskManager != null ? slayerTaskManager.getSlayerPoints() : 0;
        int streak = slayerTaskManager != null ? slayerTaskManager.getTaskStreak() : 0;
        int wildyStreak = slayerTaskManager != null ? slayerTaskManager.getWildernessStreak() : 0;

        JPanel pointsRow = new JPanel(new BorderLayout(4, 0));
        pointsRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pointsRow.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(3, 5, 3, 5)
        ));
        pointsRow.setAlignmentX(LEFT_ALIGNMENT);
        pointsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        String streakText = "Streak: <span style='color: #5ac8fa; font-weight: bold;'>" + streak + "</span>";
        if (wildyStreak > 0)
        {
            streakText += " <span style='color: #f87171;'>(Wildy: " + wildyStreak + ")</span>";
        }
        JLabel pointsLabel = new JLabel("<html><body>Points: <span style='color: #ffd700; font-weight: bold;'>" + pts + "</span> • " + streakText + "</body></html>");
        pointsLabel.setFont(FontManager.getRunescapeSmallFont());
        pointsLabel.setForeground(Color.WHITE);
        pointsRow.add(pointsLabel, BorderLayout.WEST);

        taskContainer.add(pointsRow);
        taskContainer.add(Box.createRigidArea(new Dimension(0, 4)));

        if (slayerTaskManager == null || !slayerTaskManager.hasActiveTask())
        {
            // Empty State
            JPanel emptyCard = new JPanel();
            emptyCard.setLayout(new BoxLayout(emptyCard, BoxLayout.Y_AXIS));
            emptyCard.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            emptyCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CopilotPalette.CARD_BORDER, 1),
                new EmptyBorder(8, 8, 8, 8)
            ));
            emptyCard.setAlignmentX(LEFT_ALIGNMENT);
            emptyCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 115));

            JLabel title = new JLabel("No Active Slayer Task");
            title.setFont(FontManager.getRunescapeBoldFont());
            title.setForeground(TITLE_COLOR);
            title.setAlignmentX(LEFT_ALIGNMENT);
            emptyCard.add(title);
            emptyCard.add(Box.createRigidArea(new Dimension(0, 4)));

            JLabel desc = new JLabel(SidebarMetrics.htmlWrap(
                "Visit a Slayer Master or check your Gem / Helm. Tasks are auto-tracked here!"));
            desc.setFont(FontManager.getRunescapeSmallFont());
            desc.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            desc.setAlignmentX(LEFT_ALIGNMENT);
            emptyCard.add(desc);
            emptyCard.add(Box.createRigidArea(new Dimension(0, 6)));

            JButton manualBtn = new JButton("Set Task Manually");
            manualBtn.setFont(FontManager.getRunescapeSmallFont());
            manualBtn.setBackground(new Color(45, 45, 50));
            manualBtn.setForeground(FOCUS_BLUE);
            manualBtn.setFocusPainted(false);
            manualBtn.setAlignmentX(LEFT_ALIGNMENT);
            manualBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
            manualBtn.setMargin(new Insets(1, 4, 1, 4));
            manualBtn.setToolTipText("Manually enter your current assignment if not auto-detected");
            manualBtn.addActionListener(e -> promptManualTaskEntry());
            emptyCard.add(manualBtn);

            taskContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
            taskContainer.add(emptyCard);
            return;
        }

        // Active Task Card
        String taskMonsterName = slayerTaskManager.getMonsterName();
        int remaining = slayerTaskManager.getAmountRemaining();
        int initial = Math.max(slayerTaskManager.getInitialAmount(), remaining);
        int completed = Math.max(0, initial - remaining);
        int pct = (int) Math.round(slayerTaskManager.getProgressPercentage());
        String locRequirement = slayerTaskManager.getLocation();
        String master = slayerTaskManager.getSlayerMaster();

        boolean isGroup = SlayerTaskManager.isGroupTaskName(taskMonsterName);
        String effectiveName = slayerTaskManager.getEffectiveMonsterName();
        Monster monster = slayerTaskManager.getActiveMonster();
        if ((monster == null || isGroup) && monsterDatabase != null)
        {
            Monster resolved = SlayerTaskManager.findMonsterForTask(effectiveName, monsterDatabase);
            if (resolved != null)
            {
                monster = resolved;
            }
        }

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(locRequirement != null ? KONAR_GOLD : CopilotPalette.CARD_BORDER, 1),
            new EmptyBorder(5, 5, 5, 5)
        ));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 175));

        // Header Row: Portrait + Details
        JPanel headerRow = new JPanel(new BorderLayout(6, 0));
        headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerRow.setAlignmentX(LEFT_ALIGNMENT);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JLabel portrait = new JLabel();
        portrait.setPreferredSize(new Dimension(42, 42));
        portrait.setMinimumSize(new Dimension(42, 42));
        portrait.setMaximumSize(new Dimension(42, 42));
        portrait.setHorizontalAlignment(SwingConstants.CENTER);
        portrait.setVerticalAlignment(SwingConstants.CENTER);
        if (npcPortraitManager != null)
        {
            if (monster != null)
            {
                npcPortraitManager.loadNpcPortrait(monster, 42, portrait);
            }
            else
            {
                npcPortraitManager.loadNpcPortrait(taskMonsterName, 42, portrait);
            }
        }
        headerRow.add(portrait, BorderLayout.WEST);

        JPanel nameCol = new JPanel();
        nameCol.setLayout(new BoxLayout(nameCol, BoxLayout.Y_AXIS));
        nameCol.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Umbrella tasks keep their group name as the title; a specific NPC only shows its own name.
        String activeTaskName = isGroup
            ? SlayerTaskManager.displayTaskName(taskMonsterName)
            : (monster != null ? monster.getName() : SlayerTaskManager.displayTaskName(taskMonsterName));
        JLabel nameLabel = new JLabel("<html><body style='width: 140px;'>" + activeTaskName + "</body></html>");
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(TITLE_COLOR);
        nameLabel.setToolTipText(activeTaskName);
        nameCol.add(nameLabel);

        JLabel subLabel = new JLabel(remaining + " kills remaining" + (initial > 0 ? " (of " + initial + ")" : ""));
        subLabel.setFont(FontManager.getRunescapeSmallFont());
        subLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        nameCol.add(subLabel);

        headerRow.add(nameCol, BorderLayout.CENTER);
        card.add(headerRow);
        card.add(Box.createRigidArea(new Dimension(0, 4)));

        // Umbrella-task subtype picker: "Killing: [Iron dragon v]" so the spawns / drops shown
        // below match what you're actually fighting (task credit is group-wide either way).
        if (isGroup)
        {
            JPanel killRow = new JPanel(new BorderLayout(4, 0));
            killRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            killRow.setAlignmentX(LEFT_ALIGNMENT);
            killRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

            boolean auto = slayerTaskManager.isAutoSubtype();
            String autoSeen = slayerTaskManager.getAutoDetectedMember();
            String autoLabel = "Auto" + (auto && autoSeen != null ? " - seeing " + autoSeen : " - follows your kills");

            JLabel killLbl = new JLabel("Killing:");
            killLbl.setFont(FontManager.getRunescapeSmallFont());
            killLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            killRow.add(killLbl, BorderLayout.WEST);

            java.util.List<String> items = new ArrayList<>();
            items.add(autoLabel);
            items.addAll(slayerTaskManager.getGroupMembers());
            JComboBox<String> subCombo = new JComboBox<>(items.toArray(new String[0]));
            subCombo.setFont(FontManager.getRunescapeSmallFont());
            subCombo.setBackground(ColorScheme.DARK_GRAY_COLOR);
            subCombo.setSelectedItem(auto ? autoLabel : effectiveName);
            subCombo.setToolTipText("Task credit counts for the whole group either way - this just "
                + "picks which one's spawns and drops the tab shows.");
            subCombo.addActionListener(ev -> {
                Object sel = subCombo.getSelectedItem();
                if (sel != null)
                {
                    slayerTaskManager.setTaskSubtype(sel.toString());
                    refresh(); // task card + spawns view both follow the chosen subtype
                }
            });
            killRow.add(subCombo, BorderLayout.CENTER);
            card.add(killRow);
            card.add(Box.createRigidArea(new Dimension(0, 4)));
            tourSubtypePicker = subCombo;
        }

        // Progress Bar
        JProgressBar progressBar = new JProgressBar(0, initial);
        progressBar.setValue(completed);
        progressBar.setStringPainted(true);
        progressBar.setString(completed + " / " + initial + " (" + pct + "%)");
        progressBar.setFont(FontManager.getRunescapeSmallFont());
        progressBar.setForeground(new Color(34, 197, 94)); // Vibrant green
        progressBar.setBackground(new Color(40, 40, 45));
        progressBar.setBorder(BorderFactory.createLineBorder(CopilotPalette.CARD_BORDER, 1));
        progressBar.setAlignmentX(LEFT_ALIGNMENT);
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        progressBar.setPreferredSize(new Dimension(0, 16));
        card.add(progressBar);
        card.add(Box.createRigidArea(new Dimension(0, 3)));

        // Passive Tracker Row: Task XP & Live Loot
        int xpEarned = slayerTaskManager.getTaskSlayerXpEarned();
        if (xpEarned <= 0 && completed > 0 && monster != null && monster.getHitpoints() > 0)
        {
            xpEarned = completed * monster.getHitpoints();
        }
        long lootGp = slayerTaskManager.getTaskLootValueGp();

        JPanel trackerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        trackerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        trackerRow.setAlignmentX(LEFT_ALIGNMENT);

        JLabel xpLabel = new JLabel("XP +" + String.format("%,d", xpEarned) + " XP");
        xpLabel.setFont(FontManager.getRunescapeSmallFont());
        xpLabel.setForeground(new Color(147, 197, 253));
        xpLabel.setToolTipText("Slayer experience gained during this assignment");
        trackerRow.add(xpLabel);

        long supplyGp = slayerTaskManager.getTaskSupplyCostGp();
        long netGp = slayerTaskManager.getTaskNetGp();

        if (lootGp > 0)
        {
            JLabel lootLabel = new JLabel("Loot " + formatGp(lootGp) + " GP");
            lootLabel.setFont(FontManager.getRunescapeSmallFont());
            lootLabel.setForeground(new Color(253, 224, 71));
            lootLabel.setToolTipText("Estimated value of loot acquired on task");
            trackerRow.add(lootLabel);
        }

        if (supplyGp > 0)
        {
            JLabel supplyLabel = new JLabel("Supplies -" + formatGp(supplyGp) + " GP");
            supplyLabel.setFont(FontManager.getRunescapeSmallFont());
            supplyLabel.setForeground(CopilotPalette.ACCENT_MUTED);
            supplyLabel.setToolTipText("Food / potions / runes / ammo spent on task");
            trackerRow.add(supplyLabel);
        }

        if (lootGp > 0 || supplyGp > 0)
        {
            JLabel netLabel = new JLabel("• Net " + (netGp < 0 ? "-" : "") + formatGp(Math.abs(netGp)) + " GP");
            netLabel.setFont(FontManager.getRunescapeSmallFont());
            netLabel.setForeground(netGp >= 0 ? new Color(74, 222, 128) : CopilotPalette.NEGATIVE);
            netLabel.setToolTipText("Loot value minus supplies spent");
            trackerRow.add(netLabel);
        }

        card.add(trackerRow);
        card.add(Box.createRigidArea(new Dimension(0, 3)));

        // Badges Row (Slayer Level, Weakness, Quest, Konar, Master)
        JPanel badgesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
        badgesRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        badgesRow.setAlignmentX(LEFT_ALIGNMENT);

        if (locRequirement != null && !locRequirement.isEmpty())
        {
            JLabel konarBadge = new JLabel("[Konar: " + locRequirement + "]");
            konarBadge.setFont(FontManager.getRunescapeSmallFont());
            konarBadge.setForeground(KONAR_GOLD);
            badgesRow.add(konarBadge);
        }

        if (monster != null && monster.getSlayerLevel() > 1)
        {
            JLabel lvlBadge = new JLabel("[Slayer " + monster.getSlayerLevel() + "]");
            lvlBadge.setFont(FontManager.getRunescapeSmallFont());
            lvlBadge.setForeground(SLAYER_PURPLE);
            badgesRow.add(lvlBadge);
        }

        if (monster != null && monster.getWeakness() != null && !monster.getWeakness().isEmpty())
        {
            JLabel weakBadge = new JLabel("[Weak: " + monster.getWeakness() + "]");
            weakBadge.setFont(FontManager.getRunescapeSmallFont());
            weakBadge.setForeground(WEAKNESS_GREEN);
            badgesRow.add(weakBadge);
        }

        if (monster != null && monster.hasQuestRequirement())
        {
            JLabel qBadge = new JLabel("[" + monster.getQuestRequirement() + "]");
            qBadge.setFont(FontManager.getRunescapeSmallFont());
            qBadge.setForeground(QUEST_PURPLE);
            badgesRow.add(qBadge);
        }

        if (master != null && !master.isEmpty())
        {
            JLabel mBadge = new JLabel("[" + master + "]");
            mBadge.setFont(FontManager.getRunescapeSmallFont());
            mBadge.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            badgesRow.add(mBadge);
        }

        card.add(badgesRow);
        card.add(Box.createRigidArea(new Dimension(0, 3)));

        // Master Snap Row (if assigned from a master)
        SlayerMaster assignedMaster = SlayerMaster.findByName(master);
        if (assignedMaster != null)
        {
            JPanel masterRow = new JPanel(new BorderLayout(4, 0));
            masterRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            masterRow.setAlignmentX(LEFT_ALIGNMENT);
            masterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

            JLabel masterLbl = new JLabel("Master: " + assignedMaster.getName() + " (" + assignedMaster.getLocationName() + ")");
            masterLbl.setFont(FontManager.getRunescapeSmallFont());
            masterLbl.setForeground(new Color(254, 240, 138));
            masterRow.add(masterLbl, BorderLayout.CENTER);

            JButton snapMasterBtn = new JButton("Snap Map");
            snapMasterBtn.setFont(FontManager.getRunescapeSmallFont());
            snapMasterBtn.setBackground(new Color(40, 40, 45));
            snapMasterBtn.setForeground(FOCUS_BLUE);
            snapMasterBtn.setFocusPainted(false);
            snapMasterBtn.setMargin(new Insets(0, 2, 0, 2));
            snapMasterBtn.setToolTipText("Center map on " + assignedMaster.getName() + " in " + assignedMaster.getLocationName());
            snapMasterBtn.addActionListener(e -> {
                if (onFocusPointOnMap != null)
                {
                    onFocusPointOnMap.accept(assignedMaster.getLocationPoint(), assignedMaster.getName() + " (" + assignedMaster.getLocationName() + ")");
                }
            });
            masterRow.add(snapMasterBtn, BorderLayout.EAST);
            card.add(masterRow);
            card.add(Box.createRigidArea(new Dimension(0, 3)));
        }

        // Action Buttons Row: [Mob Details] [Reset Task] - Equal 50/50 split
        JPanel actionRow = new JPanel(new GridLayout(1, 2, 4, 0));
        actionRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        actionRow.setAlignmentX(LEFT_ALIGNMENT);
        actionRow.setPreferredSize(new Dimension(0, 24));
        actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        final Monster resolvedMonster = monster;
        JButton detailsBtn = new JButton("Mob Details");
        detailsBtn.setFont(FontManager.getRunescapeSmallFont());
        detailsBtn.setBackground(new Color(45, 45, 50));
        detailsBtn.setForeground(FOCUS_BLUE);
        detailsBtn.setFocusPainted(false);
        detailsBtn.setMargin(new Insets(1, 2, 1, 2));
        detailsBtn.setPreferredSize(new Dimension(0, 24));
        detailsBtn.setToolTipText("Open full monster info, weakness, stats, and drop tables");
        detailsBtn.addActionListener(e -> {
            if (resolvedMonster != null && onOpenMonsterDetail != null)
            {
                onOpenMonsterDetail.accept(resolvedMonster);
            }
            else if (resolvedMonster == null)
            {
                JOptionPane.showMessageDialog(this, "Monster data not found for: " + taskMonsterName, "Monster Info", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        actionRow.add(detailsBtn);

        JButton clearBtn = new JButton("Reset Task");
        clearBtn.setFont(FontManager.getRunescapeSmallFont());
        clearBtn.setBackground(new Color(45, 45, 50));
        clearBtn.setForeground(WILDY_RED);
        clearBtn.setFocusPainted(false);
        clearBtn.setMargin(new Insets(1, 2, 1, 2));
        clearBtn.setPreferredSize(new Dimension(0, 24));
        clearBtn.setToolTipText("Clear and reset current Slayer task");

        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to clear your current Slayer task?", "Reset Slayer Task", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION)
            {
                slayerTaskManager.clearTask();
            }
        });
        actionRow.add(clearBtn);

        card.add(actionRow);
        taskContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 185));
        taskContainer.add(card);
    }

    private void rebuildSpawnsView()
    {
        spawnsContainer.removeAll();

        if (slayerTaskManager == null || !slayerTaskManager.hasActiveTask())
        {
            return;
        }

        Monster monster = slayerTaskManager.getActiveMonster();
        boolean group = SlayerTaskManager.isGroupTaskName(slayerTaskManager.getMonsterName());
        if ((monster == null || group) && monsterDatabase != null)
        {
            Monster r = SlayerTaskManager.findMonsterForTask(slayerTaskManager.getEffectiveMonsterName(), monsterDatabase);
            if (r != null)
            {
                monster = r;
            }
        }

        if (monster == null || !monster.hasSpawnZones())
        {
            JPanel noSpawnsCard = new JPanel(new BorderLayout());
            noSpawnsCard.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            noSpawnsCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CopilotPalette.CARD_BORDER, 1),
                new EmptyBorder(4, 5, 4, 5)
            ));
            noSpawnsCard.setAlignmentX(LEFT_ALIGNMENT);
            noSpawnsCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            JLabel lbl = new JLabel("No specific spawn zones registered.");
            lbl.setFont(FontManager.getRunescapeSmallFont());
            lbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            noSpawnsCard.add(lbl, BorderLayout.CENTER);

            spawnsContainer.add(noSpawnsCard);
            return;
        }

        String konarLoc = slayerTaskManager.getLocation();
        List<MonsterSpawnZone> matchedZones = new ArrayList<>();
        List<MonsterSpawnZone> otherZones = new ArrayList<>();

        for (MonsterSpawnZone zone : monster.getSpawnZones())
        {
            if (konarLoc != null && matchesKonarLocation(zone, konarLoc))
            {
                matchedZones.add(zone);
            }
            else
            {
                otherZones.add(zone);
            }
        }

        List<MonsterSpawnZone> orderedZones = new ArrayList<>(matchedZones);
        orderedZones.addAll(otherZones);

        // Section Title
        JLabel titleLbl = new JLabel("Spawn Locations (" + orderedZones.size() + ")");
        titleLbl.setFont(FontManager.getRunescapeBoldFont());
        titleLbl.setForeground(FOCUS_BLUE);
        titleLbl.setAlignmentX(LEFT_ALIGNMENT);
        spawnsContainer.add(titleLbl);
        spawnsContainer.add(Box.createRigidArea(new Dimension(0, 3)));

        final Monster targetMonster = monster;

        for (MonsterSpawnZone zone : orderedZones)
        {
            boolean isKonarMatch = matchedZones.contains(zone);
            JPanel spawnCard = buildSpawnZoneCard(targetMonster, zone, isKonarMatch);
            spawnsContainer.add(spawnCard);
            spawnsContainer.add(Box.createRigidArea(new Dimension(0, 3)));
        }
    }

    private JPanel buildSpawnZoneCard(Monster monster, MonsterSpawnZone zone, boolean isKonarTarget)
    {
        JPanel spawnCard = new JPanel(new BorderLayout(4, 0));
        spawnCard.setBackground(isKonarTarget ? KONAR_BADGE_BG : ColorScheme.DARKER_GRAY_COLOR);
        spawnCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(isKonarTarget ? KONAR_GOLD : CopilotPalette.CARD_BORDER, isKonarTarget ? 2 : 1),
            new EmptyBorder(3, 4, 3, 4)
        ));
        spawnCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, isKonarTarget ? 48 : 40));
        spawnCard.setPreferredSize(new Dimension(0, isKonarTarget ? 46 : 38));
        spawnCard.setAlignmentX(LEFT_ALIGNMENT);
        spawnCard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        spawnCard.setToolTipText("Click card to snap World Map & beacon to " + zone.getZoneName());

        JPanel infoCol = new JPanel();
        infoCol.setLayout(new BoxLayout(infoCol, BoxLayout.Y_AXIS));
        infoCol.setBackground(isKonarTarget ? KONAR_BADGE_BG : ColorScheme.DARKER_GRAY_COLOR);

        if (isKonarTarget)
        {
            JLabel konarTopBadge = new JLabel("* KONAR TARGET LOCATION *");
            konarTopBadge.setFont(FontManager.getRunescapeSmallFont());
            konarTopBadge.setForeground(KONAR_GOLD);
            infoCol.add(konarTopBadge);
        }

        JPanel nameLine = new JPanel(new BorderLayout(2, 0));
        nameLine.setBackground(isKonarTarget ? KONAR_BADGE_BG : ColorScheme.DARKER_GRAY_COLOR);
        nameLine.setAlignmentX(LEFT_ALIGNMENT);

        // Name capped to 115px (HTML width) -- nameLine's remaining budget after the optional
        // spawn-count/wilderness/multi badge cluster on EAST (up to ~80px) is roughly 119px at the
        // default ~225px sidebar. Some wiki-scraped zone names run past 70 characters, so this
        // reliably wraps instead of inflating spawnCard's preferred width.
        JLabel nameLbl = new JLabel("<html><body style='width: 115px;'>" + zone.getZoneName() + "</body></html>");
        nameLbl.setFont(FontManager.getRunescapeBoldFont());
        nameLbl.setForeground(isKonarTarget ? new Color(255, 220, 100) : FOCUS_BLUE);
        nameLbl.setToolTipText(zone.getZoneName());
        nameLine.add(nameLbl, BorderLayout.CENTER);

        JPanel badgesPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        badgesPanel.setBackground(isKonarTarget ? KONAR_BADGE_BG : ColorScheme.DARKER_GRAY_COLOR);

        if (zone.getSpawnCount() > 0)
        {
            JLabel countBadge = new JLabel("x" + zone.getSpawnCount());
            countBadge.setFont(FontManager.getRunescapeSmallFont());
            countBadge.setForeground(new Color(254, 240, 138));
            badgesPanel.add(countBadge);
        }
        if (zone.getWildernessLevel() > 0)
        {
            JLabel wildyBadge = new JLabel("W" + zone.getWildernessLevel());
            wildyBadge.setFont(FontManager.getRunescapeSmallFont());
            wildyBadge.setForeground(WILDY_RED);
            badgesPanel.add(wildyBadge);
        }
        if (zone.isMultiCombat())
        {
            JLabel multiBadge = new JLabel("Multi");
            multiBadge.setFont(FontManager.getRunescapeSmallFont());
            multiBadge.setForeground(MULTI_ORANGE);
            badgesPanel.add(multiBadge);
        }

        if (badgesPanel.getComponentCount() > 0)
        {
            nameLine.add(badgesPanel, BorderLayout.EAST);
        }
        infoCol.add(nameLine);

        String locDesc = zone.getLocationName() != null ? zone.getLocationName() : (zone.getDungeonName() != null ? zone.getDungeonName() : "Gielinor");
        JLabel locLbl = new JLabel(locDesc);
        locLbl.setFont(FontManager.getRunescapeSmallFont());
        locLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        infoCol.add(locLbl);

        spawnCard.add(infoCol, BorderLayout.CENTER);

        MouseAdapter clickAdapter = new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                spawnCard.setBackground(CopilotPalette.ROW_HOVER);
                infoCol.setBackground(CopilotPalette.ROW_HOVER);
                nameLine.setBackground(CopilotPalette.ROW_HOVER);
                badgesPanel.setBackground(CopilotPalette.ROW_HOVER);
                spawnCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isKonarTarget ? KONAR_GOLD : FOCUS_BLUE, isKonarTarget ? 2 : 1),
                    new EmptyBorder(3, 4, 3, 4)
                ));
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                spawnCard.setBackground(isKonarTarget ? KONAR_BADGE_BG : ColorScheme.DARKER_GRAY_COLOR);
                infoCol.setBackground(isKonarTarget ? KONAR_BADGE_BG : ColorScheme.DARKER_GRAY_COLOR);
                nameLine.setBackground(isKonarTarget ? KONAR_BADGE_BG : ColorScheme.DARKER_GRAY_COLOR);
                badgesPanel.setBackground(isKonarTarget ? KONAR_BADGE_BG : ColorScheme.DARKER_GRAY_COLOR);
                spawnCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isKonarTarget ? KONAR_GOLD : CopilotPalette.CARD_BORDER, isKonarTarget ? 2 : 1),
                    new EmptyBorder(3, 4, 3, 4)
                ));
            }

            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (onFocusMonsterZoneOnMap != null && monster != null)
                {
                    onFocusMonsterZoneOnMap.accept(monster, zone);
                }
            }
        };

        spawnCard.addMouseListener(clickAdapter);
        infoCol.addMouseListener(clickAdapter);
        nameLine.addMouseListener(clickAdapter);
        nameLbl.addMouseListener(clickAdapter);
        badgesPanel.addMouseListener(clickAdapter);
        locLbl.addMouseListener(clickAdapter);
        for (java.awt.Component c : badgesPanel.getComponents())
        {
            c.addMouseListener(clickAdapter);
        }

        return spawnCard;
    }

    private final Set<String> expandedMasters = new HashSet<>();

    public void selectMasterByName(String masterName)
    {
        if (masterName != null && !masterName.isEmpty())
        {
            expandedMasters.add(masterName);
            buildMastersList();
            revalidate();
            repaint();
        }
    }

    /** Right-click a task row to add/remove it from the player's block list. */
    private void maybeShowBlockMenu(MouseEvent e, String taskMonster, boolean currentlyBlocked)
    {
        if (!e.isPopupTrigger() && !javax.swing.SwingUtilities.isRightMouseButton(e))
        {
            return;
        }
        if (slayerTaskManager == null)
        {
            return;
        }
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(
            currentlyBlocked ? "Unblock \"" + taskMonster + "\"" : "Block \"" + taskMonster + "\"");
        item.addActionListener(a -> {
            slayerTaskManager.setTaskBlocked(taskMonster, !currentlyBlocked);
            buildMastersList();
            revalidate();
            repaint();
        });
        menu.add(item);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void buildMastersList()
    {
        mastersContainer.removeAll();

        for (SlayerMaster master : SlayerMaster.ALL_MASTERS)
        {
            List<SlayerTaskAssignment> assignments = slayerTaskManager != null
                ? slayerTaskManager.getAssignmentsForMaster(master.getName())
                : Collections.emptyList();
            boolean isExpanded = expandedMasters.contains(master.getName());

            // Master lock: player doesn't meet the combat / Slayer-level requirement yet.
            int pCmb = slayerTaskManager != null ? slayerTaskManager.getPlayerCombatLevel() : 0;
            int pSlay = slayerTaskManager != null ? slayerTaskManager.getPlayerSlayerLevel() : 0;
            String masterLockReason = null;
            if (pCmb > 0 && master.getCombatRequirement() > 1 && pCmb < master.getCombatRequirement())
            {
                masterLockReason = "Requires Combat " + master.getCombatRequirement() + " (you have " + pCmb + ")";
            }
            if (pSlay > 0 && master.getSlayerRequirement() > 1 && pSlay < master.getSlayerRequirement())
            {
                String s = "Requires Slayer " + master.getSlayerRequirement() + " (you have " + pSlay + ")";
                masterLockReason = masterLockReason == null ? s : masterLockReason + "; " + s;
            }
            final boolean masterLocked = masterLockReason != null;

            JPanel cardContainer = new JPanel();
            cardContainer.setLayout(new BoxLayout(cardContainer, BoxLayout.Y_AXIS));
            cardContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            cardContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(masterLocked ? new Color(70, 45, 45) : CopilotPalette.CARD_BORDER, 1),
                new EmptyBorder(4, 5, 4, 5)
            ));
            cardContainer.setAlignmentX(LEFT_ALIGNMENT);

            // Master Info Row
            JPanel masterHeader = new JPanel(new BorderLayout(5, 0));
            masterHeader.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            masterHeader.setAlignmentX(LEFT_ALIGNMENT);

            JLabel portrait = new JLabel();
            portrait.setPreferredSize(new Dimension(32, 32));
            portrait.setMinimumSize(new Dimension(32, 32));
            portrait.setMaximumSize(new Dimension(32, 32));
            portrait.setHorizontalAlignment(SwingConstants.CENTER);
            portrait.setVerticalAlignment(SwingConstants.CENTER);
            if (npcPortraitManager != null)
            {
                npcPortraitManager.loadNpcPortrait(master.getName(), 32, portrait);
            }
            masterHeader.add(portrait, BorderLayout.WEST);

            JPanel infoCol = new JPanel();
            infoCol.setLayout(new BoxLayout(infoCol, BoxLayout.Y_AXIS));
            infoCol.setBackground(ColorScheme.DARKER_GRAY_COLOR);

            JPanel topTitleRow = new JPanel(new BorderLayout(2, 0));
            topTitleRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);

            JLabel nameLbl = new JLabel((masterLocked ? "[locked] " : "") + master.getName());
            nameLbl.setFont(FontManager.getRunescapeBoldFont());
            nameLbl.setForeground(masterLocked ? ColorScheme.MEDIUM_GRAY_COLOR : CopilotPalette.ACCENT);
            nameLbl.setToolTipText(masterLocked
                ? "<html><b>" + master.getName() + " - LOCKED</b><br>" + masterLockReason + "</html>"
                : master.getName() + " - " + master.getDescription());
            topTitleRow.add(nameLbl, BorderLayout.CENTER);

            JButton focusBtn = new JButton("Map");
            focusBtn.setFont(FontManager.getRunescapeSmallFont());
            focusBtn.setBackground(new Color(40, 40, 45));
            focusBtn.setForeground(FOCUS_BLUE);
            focusBtn.setFocusPainted(false);
            focusBtn.setPreferredSize(new Dimension(36, 18));
            focusBtn.setMinimumSize(new Dimension(36, 18));
            focusBtn.setMaximumSize(new Dimension(36, 18));
            focusBtn.setMargin(new Insets(0, 1, 0, 1));
            focusBtn.setToolTipText("Show " + master.getName() + " (" + master.getLocationName() + ") on the World Map");
            focusBtn.addActionListener(e -> {
                if (onFocusPointOnMap != null)
                {
                    onFocusPointOnMap.accept(master.getLocationPoint(), master.getName() + " (" + master.getLocationName() + ")");
                }
            });
            topTitleRow.add(focusBtn, BorderLayout.EAST);
            infoCol.add(topTitleRow);

            StringBuilder reqsSb = new StringBuilder(master.getLocationName());
            if (master.getCombatRequirement() > 1)
            {
                reqsSb.append(" • Cmb ").append(master.getCombatRequirement());
            }
            if (master.getSlayerRequirement() > 1)
            {
                reqsSb.append(" • Slay ").append(master.getSlayerRequirement());
            }
            JLabel reqLbl = new JLabel((masterLocked ? "LOCKED — " : "") + reqsSb);
            reqLbl.setFont(FontManager.getRunescapeSmallFont());
            reqLbl.setForeground(masterLocked ? WILDY_RED : ColorScheme.LIGHT_GRAY_COLOR);
            if (masterLocked)
            {
                reqLbl.setToolTipText(masterLockReason);
            }
            infoCol.add(reqLbl);

            JLabel ptsLbl = new JLabel(master.getPointsInfo());
            ptsLbl.setFont(FontManager.getRunescapeSmallFont());
            ptsLbl.setForeground(SLAYER_PURPLE);
            infoCol.add(ptsLbl);

            masterHeader.add(infoCol, BorderLayout.CENTER);
            cardContainer.add(masterHeader);

            // Expand / Collapse Task List Button
            if (!assignments.isEmpty())
            {
                cardContainer.add(Box.createRigidArea(new Dimension(0, 3)));
                JPanel taskToggleRow = new JPanel(new BorderLayout(4, 0));
                taskToggleRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                taskToggleRow.setAlignmentX(LEFT_ALIGNMENT);

                JButton toggleBtn = new JButton((isExpanded ? "▲ Hide Tasks (" : "▼ View Tasks (") + assignments.size() + ")");
                toggleBtn.setFont(FontManager.getRunescapeSmallFont());
                toggleBtn.setBackground(new Color(36, 36, 40));
                toggleBtn.setForeground(new Color(254, 240, 138));
                toggleBtn.setFocusPainted(false);
                toggleBtn.setMargin(new Insets(1, 2, 1, 2));
                toggleBtn.setPreferredSize(new Dimension(0, 18));
                toggleBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
                toggleBtn.addActionListener(e -> {
                    if (isExpanded)
                    {
                        expandedMasters.remove(master.getName());
                    }
                    else
                    {
                        expandedMasters.add(master.getName());
                    }
                    buildMastersList();
                    revalidate();
                    repaint();
                });
                taskToggleRow.add(toggleBtn, BorderLayout.CENTER);
                cardContainer.add(taskToggleRow);

                if (isExpanded)
                {
                    cardContainer.add(Box.createRigidArea(new Dimension(0, 4)));
                    JPanel taskTablePanel = new JPanel();
                    taskTablePanel.setLayout(new BoxLayout(taskTablePanel, BoxLayout.Y_AXIS));
                    taskTablePanel.setBackground(new Color(25, 25, 28));
                    taskTablePanel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(50, 50, 55), 1),
                        new EmptyBorder(3, 4, 3, 4)
                    ));
                    taskTablePanel.setAlignmentX(LEFT_ALIGNMENT);

                    // Table Header Bar
                    JPanel thRow = new JPanel(new BorderLayout(4, 0));
                    thRow.setBackground(new Color(35, 35, 40));
                    thRow.setPreferredSize(new Dimension(0, 16));
                    thRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

                    JLabel thMonster = new JLabel("Task Monster");
                    thMonster.setFont(FontManager.getRunescapeBoldFont());
                    thMonster.setForeground(TITLE_COLOR);
                    thRow.add(thMonster, BorderLayout.CENTER);

                    JPanel thRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
                    thRight.setBackground(new Color(35, 35, 40));

                    JLabel thKills = new JLabel("Kills");
                    thKills.setFont(FontManager.getRunescapeBoldFont());
                    thKills.setForeground(new Color(200, 200, 200));
                    thRight.add(thKills);

                    JLabel thWeight = new JLabel("Wt");
                    thWeight.setFont(FontManager.getRunescapeBoldFont());
                    thWeight.setForeground(CopilotPalette.GOLD);
                    thRight.add(thWeight);

                    thRow.add(thRight, BorderLayout.EAST);
                    taskTablePanel.add(thRow);
                    taskTablePanel.add(Box.createRigidArea(new Dimension(0, 2)));

                    for (SlayerTaskAssignment task : assignments)
                    {
                        JPanel tr = new JPanel(new BorderLayout(4, 0));
                        tr.setBackground(new Color(25, 25, 28));
                        tr.setPreferredSize(new Dimension(0, 18));
                        tr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

                        final boolean blocked = slayerTaskManager != null && slayerTaskManager.isTaskBlocked(task.getMonster());
                        final String unavail = slayerTaskManager != null
                            ? slayerTaskManager.getTaskUnavailableReason(task.getMonster()) : null;
                        final boolean unavailable = unavail != null;
                        final Color idleColor = blocked ? WILDY_RED
                            : (unavailable ? ColorScheme.MEDIUM_GRAY_COLOR : FOCUS_BLUE);

                        JLabel taskNameLbl = new JLabel((blocked ? "[blocked] " : unavailable ? "[locked] " : "") + task.getMonster());
                        taskNameLbl.setFont(FontManager.getRunescapeSmallFont());
                        taskNameLbl.setForeground(idleColor);
                        taskNameLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                        StringBuilder tt = new StringBuilder("<html><body><b>").append(task.getMonster()).append("</b>");
                        if (unavailable) tt.append("<br><span style='color:#f87171;'>").append(unavail).append("</span>");
                        tt.append("<br>Amount: ").append(task.getAmountDisplay());
                        if (task.getExtended() != null) tt.append("<br>Extended: ").append(task.getExtended());
                        tt.append("<br>Weight: ").append(task.getWeight());
                        if (task.getRequirement() != null) tt.append("<br>Req: ").append(task.getRequirement());
                        if (task.getAlternatives() != null) tt.append("<br>Alternatives: ").append(task.getAlternatives());
                        if (task.getLocations() != null) tt.append("<br>Locations: ").append(task.getLocations());
                        tt.append("<br><i>Click: monster details & drops · Right-click: ")
                            .append(blocked ? "unblock" : "block").append("</i></body></html>");
                        taskNameLbl.setToolTipText(tt.toString());

                        taskNameLbl.addMouseListener(new MouseAdapter()
                        {
                            @Override
                            public void mouseEntered(MouseEvent e)
                            {
                                taskNameLbl.setForeground(unavailable && !blocked ? ColorScheme.LIGHT_GRAY_COLOR : new Color(147, 197, 253));
                            }

                            @Override
                            public void mouseExited(MouseEvent e)
                            {
                                taskNameLbl.setForeground(idleColor);
                            }

                            @Override
                            public void mousePressed(MouseEvent e)
                            {
                                maybeShowBlockMenu(e, task.getMonster(), blocked);
                            }

                            @Override
                            public void mouseReleased(MouseEvent e)
                            {
                                maybeShowBlockMenu(e, task.getMonster(), blocked);
                            }

                            @Override
                            public void mouseClicked(MouseEvent e)
                            {
                                if (e.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(e))
                                {
                                    return;
                                }
                                if (monsterDatabase != null && onOpenMonsterDetail != null)
                                {
                                    Monster m = SlayerTaskManager.findMonsterForTask(task.getMonster(), monsterDatabase);
                                    if (m != null)
                                    {
                                        onOpenMonsterDetail.accept(m);
                                    }
                                }
                            }
                        });
                        tr.add(taskNameLbl, BorderLayout.CENTER);

                        JPanel trRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
                        trRight.setBackground(new Color(25, 25, 28));

                        JLabel amtLbl = new JLabel(task.getAmountDisplay());
                        amtLbl.setFont(FontManager.getRunescapeSmallFont());
                        amtLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                        trRight.add(amtLbl);

                        JLabel wtLbl = new JLabel(String.valueOf(task.getWeight()));
                        wtLbl.setFont(FontManager.getRunescapeBoldFont());
                        wtLbl.setForeground(unavailable ? ColorScheme.MEDIUM_GRAY_COLOR : new Color(253, 224, 71));
                        trRight.add(wtLbl);

                        tr.add(trRight, BorderLayout.EAST);
                        taskTablePanel.add(tr);
                    }

                    cardContainer.add(taskTablePanel);
                }
            }

            mastersContainer.add(cardContainer);
            mastersContainer.add(Box.createRigidArea(new Dimension(0, 4)));
        }
    }

    private boolean matchesKonarLocation(MonsterSpawnZone zone, String konarLocation)
    {
        if (zone == null || konarLocation == null || konarLocation.trim().isEmpty())
        {
            return false;
        }

        String locLower = konarLocation.toLowerCase(Locale.ROOT).trim();
        if (zone.getZoneName() != null && zone.getZoneName().toLowerCase(Locale.ROOT).contains(locLower))
        {
            return true;
        }
        if (zone.getLocationName() != null && zone.getLocationName().toLowerCase(Locale.ROOT).contains(locLower))
        {
            return true;
        }
        if (zone.getDungeonName() != null && zone.getDungeonName().toLowerCase(Locale.ROOT).contains(locLower))
        {
            return true;
        }

        // Token match for multi-word location descriptions (e.g. "Catacombs of Kourend" matching "Catacombs")
        String[] tokens = locLower.split("\\s+");
        for (String token : tokens)
        {
            if (token.length() >= 4 && !"dungeon".equals(token) && !"caves".equals(token) && !"slayer".equals(token))
            {
                if (zone.getZoneName() != null && zone.getZoneName().toLowerCase(Locale.ROOT).contains(token))
                {
                    return true;
                }
                if (zone.getLocationName() != null && zone.getLocationName().toLowerCase(Locale.ROOT).contains(token))
                {
                    return true;
                }
                if (zone.getDungeonName() != null && zone.getDungeonName().toLowerCase(Locale.ROOT).contains(token))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private void rebuildRewardsView()
    {
        rewardsContainer.removeAll();

        SlayerRewardCatalog catalog = slayerTaskManager != null ? slayerTaskManager.getRewardCatalog() : null;
        if (catalog == null)
        {
            return;
        }

        List<SlayerReward> allRewards = catalog.getAllRewards();
        int unlockedCount = 0;
        for (SlayerReward r : allRewards)
        {
            if (r.isUnlocked())
            {
                unlockedCount++;
            }
        }

        // 1. Header Bar with Expand / Collapse Toggle
        JPanel headerPanel = new JPanel(new BorderLayout(4, 0));
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerPanel.setAlignmentX(LEFT_ALIGNMENT);
        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String expandArrow = rewardsExpanded ? "▼ " : "▶ ";
        JLabel title = new JLabel(expandArrow + "Slayer Rewards (" + unlockedCount + "/" + allRewards.size() + ")");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(REWARD_GOLD);
        headerPanel.add(title, BorderLayout.WEST);

        headerPanel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                rewardsExpanded = !rewardsExpanded;
                rebuildRewardsView();
                rewardsContainer.revalidate();
                rewardsContainer.repaint();
            }
        });

        rewardsContainer.add(headerPanel);
        rewardsContainer.add(Box.createRigidArea(new Dimension(0, 3)));

        if (!rewardsExpanded)
        {
            return;
        }

        // 2. Filter Buttons (2 rows on a narrow panel): All / Unlocks / Extends / Cosmetic / Buys
        JPanel filterRow = new JPanel(new GridLayout(0, 3, 3, 3));
        filterRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        filterRow.setAlignmentX(LEFT_ALIGNMENT);
        filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        String[] filters = {"All", "Unlocks", "Extends", "Cosmetic", "Buys"};
        for (String filter : filters)
        {
            boolean active = filter.equalsIgnoreCase(selectedRewardFilter);
            JButton btn = new JButton(filter);
            btn.setFont(FontManager.getRunescapeSmallFont());
            btn.setBackground(active ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
            btn.setForeground(active ? REWARD_GOLD : ColorScheme.LIGHT_GRAY_COLOR);
            btn.setFocusPainted(false);
            btn.setMargin(new Insets(1, 2, 1, 2));
            btn.addActionListener(e -> {
                selectedRewardFilter = filter;
                rebuildRewardsView();
                rewardsContainer.revalidate();
                rewardsContainer.repaint();
            });
            filterRow.add(btn);
        }

        rewardsContainer.add(filterRow);
        rewardsContainer.add(Box.createRigidArea(new Dimension(0, 4)));

        // 3. Render reward cards, grouped by category with a light sub-header per group.
        boolean showSubHeaders = "All".equalsIgnoreCase(selectedRewardFilter);
        for (RewardType group : new RewardType[]{RewardType.UNLOCK, RewardType.EXTEND,
            RewardType.COSMETIC, RewardType.BUY, RewardType.MANAGEMENT})
        {
            java.util.List<SlayerReward> inGroup = new ArrayList<>();
            for (SlayerReward r : allRewards)
            {
                if (r.getType() == group && matchesRewardFilter(r))
                {
                    inGroup.add(r);
                }
            }
            if (inGroup.isEmpty())
            {
                continue;
            }

            if (showSubHeaders)
            {
                int haveInGroup = 0;
                for (SlayerReward r : inGroup)
                {
                    if (r.isUnlocked())
                    {
                        haveInGroup++;
                    }
                }
                JLabel sub = new JLabel(group.getDisplayName().toUpperCase() + "  ("
                    + haveInGroup + "/" + inGroup.size() + ")");
                sub.setFont(FontManager.getRunescapeSmallFont());
                sub.setForeground(REWARD_GOLD);
                sub.setAlignmentX(LEFT_ALIGNMENT);
                sub.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR));
                sub.setMaximumSize(new Dimension(Integer.MAX_VALUE, 15));
                rewardsContainer.add(Box.createRigidArea(new Dimension(0, 3)));
                rewardsContainer.add(sub);
                rewardsContainer.add(Box.createRigidArea(new Dimension(0, 2)));
            }

            for (SlayerReward reward : inGroup)
            {
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                new EmptyBorder(3, 4, 3, 4)
            ));
            card.setAlignmentX(LEFT_ALIGNMENT);

            // Top row: Name + Cost / Status Badge
            JPanel topRow = new JPanel(new BorderLayout(4, 0));
            topRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            topRow.setAlignmentX(LEFT_ALIGNMENT);

            JLabel nameLabel = new JLabel(reward.getName());
            nameLabel.setFont(FontManager.getRunescapeBoldFont());
            nameLabel.setForeground(reward.isUnlocked() ? UNLOCKED_GREEN : Color.WHITE);
            topRow.add(nameLabel, BorderLayout.WEST);

            String statusText = reward.isUnlocked()
                ? "<span style='color: #22c55e;'>[Unlocked]</span>"
                : "<span style='color: #ffd700;'>" + reward.getCost() + " pts</span>";
            JLabel statusLabel = new JLabel("<html><body>" + statusText + "</body></html>");
            statusLabel.setFont(FontManager.getRunescapeSmallFont());
            topRow.add(statusLabel, BorderLayout.EAST);

            card.add(topRow);
            card.add(Box.createRigidArea(new Dimension(0, 1)));

            // Description - wrapped cleanly to fit 205-225px side panel
            JLabel descLabel = new JLabel("<html><body style='width: 165px; color: #94a3b8; font-size: 10px;'>" + reward.getDescription() + "</body></html>");
            descLabel.setFont(FontManager.getRunescapeSmallFont());
            descLabel.setAlignmentX(LEFT_ALIGNMENT);
            card.add(descLabel);

            rewardsContainer.add(card);
            rewardsContainer.add(Box.createRigidArea(new Dimension(0, 2)));
            } // end: for reward in group
        } // end: for group in category order
    }

    private boolean matchesRewardFilter(SlayerReward reward)
    {
        if ("All".equalsIgnoreCase(selectedRewardFilter))
        {
            return true;
        }
        if ("Unlocks".equalsIgnoreCase(selectedRewardFilter) && reward.getType() == RewardType.UNLOCK)
        {
            return true;
        }
        if ("Extends".equalsIgnoreCase(selectedRewardFilter) && reward.getType() == RewardType.EXTEND)
        {
            return true;
        }
        if ("Cosmetic".equalsIgnoreCase(selectedRewardFilter) && reward.getType() == RewardType.COSMETIC)
        {
            return true;
        }
        if ("Buys".equalsIgnoreCase(selectedRewardFilter) && (reward.getType() == RewardType.BUY || reward.getType() == RewardType.MANAGEMENT))
        {
            return true;
        }
        return false;
    }

    private void promptManualTaskEntry()
    {
        String monster = JOptionPane.showInputDialog(this, "Enter Monster Name (e.g. Hellhounds, Gargoyles):", "Set Slayer Task", JOptionPane.PLAIN_MESSAGE);
        if (monster == null || monster.trim().isEmpty())
        {
            return;
        }

        String amountStr = JOptionPane.showInputDialog(this, "Enter Number to Kill:", "150");
        int amount = 100;
        try
        {
            if (amountStr != null)
            {
                amount = Integer.parseInt(amountStr.trim());
            }
        }
        catch (NumberFormatException e)
        {
            log.debug("Ignoring non-numeric Slayer task amount", e);
        }

        String loc = JOptionPane.showInputDialog(this, "Location Requirement (Optional, for Konar):", "");

        List<String> masterNames = new ArrayList<>();
        masterNames.add("Auto-Detect / None");
        for (SlayerMaster m : SlayerMaster.ALL_MASTERS)
        {
            masterNames.add(m.getName());
        }
        String selectedMaster = (String) JOptionPane.showInputDialog(
            this,
            "Select Slayer Master:",
            "Slayer Master",
            JOptionPane.PLAIN_MESSAGE,
            null,
            masterNames.toArray(),
            "Auto-Detect / None"
        );

        String finalMaster = (selectedMaster == null || selectedMaster.startsWith("Auto-Detect")) ? null : selectedMaster;

        slayerTaskManager.setTaskDetails(monster.trim(), amount, amount, loc != null && !loc.trim().isEmpty() ? loc.trim() : null, finalMaster);
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
