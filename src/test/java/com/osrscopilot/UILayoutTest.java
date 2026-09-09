package com.osrscopilot;

import com.google.gson.Gson;
import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.ShopLiveStockManager;
import com.osrscopilot.data.ShoppingCartManager;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.ui.AboutView;
import com.osrscopilot.ui.GlobalItemSearchView;
import com.osrscopilot.ui.MonsterDetailView;
import com.osrscopilot.ui.MonsterDirectoryView;
import com.osrscopilot.ui.ShoppingCartView;
import com.osrscopilot.ui.SlayerTabView;
import com.osrscopilot.ui.TownHubView;
import com.osrscopilot.ui.VendorStockView;
import com.osrscopilot.util.NpcPortraitManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import okhttp3.OkHttpClient;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UILayoutTest
{
    private ShopDatabase shopDatabase;
    private MonsterDatabase monsterDatabase;
    private NpcPortraitManager npcPortraitManager;
    private ScheduledExecutorService executor;
    private ShopLiveStockManager liveStockManager;
    private ShoppingCartManager cartManager;
    private OsrsCopilotConfig config;
    private ItemManager itemManager;

    @Before
    public void setUp()
    {
        shopDatabase = new ShopDatabase(new Gson());
        shopDatabase.load();

        monsterDatabase = new MonsterDatabase(new Gson());
        monsterDatabase.load();

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        npcPortraitManager = new NpcPortraitManager(OfflineHttp.client(), executor);
        liveStockManager = new ShopLiveStockManager();
        cartManager = new ShoppingCartManager();
        config = Mockito.mock(OsrsCopilotConfig.class);
        itemManager = Mockito.mock(ItemManager.class);
    }

    @After
    public void tearDown()
    {
        if (executor != null)
        {
            executor.shutdownNow();
        }
    }

    @Test
    public void testTopNavButtonsFitInsidePanel()
    {
        int panelWidth = 205; // Test strict 205px minimum
        int horizontalPadding = 6; // 3px on left, 3px on right of header
        int availableWidth = panelWidth - horizontalPadding;
        // The nav strip is a GridLayout(3, 3, 2, 2) holding all 9 tab buttons - 3 rows x 3 columns.
        int buttonCount = 3;
        int gap = 2;
        int buttonWidth = (availableWidth - (buttonCount - 1) * gap) / buttonCount;

        String[] tabNames = {"Combat", "Towns", "Shops", "Bestiary", "Slayer", "Search", "Loot", "Cart", "About"};

        JButton sampleBtn = new JButton();
        sampleBtn.setFont(FontManager.getRunescapeSmallFont());
        sampleBtn.setMargin(new Insets(0, 0, 0, 0));
        FontMetrics fm = sampleBtn.getFontMetrics(sampleBtn.getFont());

        for (String name : tabNames)
        {
            int textWidth = fm.stringWidth(name);
            assertTrue("Button text '" + name + "' (width=" + textWidth + ") should fit within button width (" + buttonWidth + ") at panelWidth=" + panelWidth,
                textWidth <= buttonWidth);
        }
    }


    @Test
    public void testTownHubActionButtonsFitCardWidth()
    {
        int[] testWidths = {205, 215, 225};

        for (int panelWidth : testWidths)
        {
            int cardPadding = 6; // 3px each side
            int availableCardWidth = panelWidth - cardPadding;
            int buttonCount = 2;
            int gap = 3;
            int buttonWidth = (availableCardWidth - gap) / buttonCount;

            String[] actionNames = {"Stock", "Map"};

            JButton sampleBtn = new JButton();
            sampleBtn.setFont(FontManager.getRunescapeSmallFont());
            sampleBtn.setMargin(new Insets(1, 2, 1, 2));
            FontMetrics fm = sampleBtn.getFontMetrics(sampleBtn.getFont());

            for (String name : actionNames)
            {
                int textWidth = fm.stringWidth(name);
                assertTrue("Action button '" + name + "' (width=" + textWidth + ") should easily fit inside half card width (" + buttonWidth + ") at panelWidth=" + panelWidth,
                    textWidth + 8 <= buttonWidth);
            }
        }
    }

    @Test
    public void testGlobalSearchActionButtonsFitCardWidth()
    {
        int[] testWidths = {205, 215, 225};

        for (int panelWidth : testWidths)
        {
            int cardPadding = 6;
            int availableCardWidth = panelWidth - cardPadding;
            int buttonCount = 3;
            int gap = 3;
            int buttonWidth = (availableCardWidth - (buttonCount - 1) * gap) / buttonCount;

            String[] actionNames = {"View", "Map", "+"};

            JButton sampleBtn = new JButton();
            sampleBtn.setFont(FontManager.getRunescapeSmallFont());
            sampleBtn.setMargin(new Insets(1, 2, 1, 2));
            FontMetrics fm = sampleBtn.getFontMetrics(sampleBtn.getFont());

            for (String name : actionNames)
            {
                int textWidth = fm.stringWidth(name);
                assertTrue("Search button '" + name + "' (width=" + textWidth + ") should fit inside 3-column card (" + buttonWidth + "px) at panelWidth=" + panelWidth,
                    textWidth + 6 <= buttonWidth);
            }
        }
    }

    @Test
    public void testShoppingCartQuantityControlsFitCardWidth()
    {
        int panelWidth = 205;
        int cardPadding = 10; // 5px each side
        int availableCardWidth = panelWidth - cardPadding; // 195px

        // [-10] (20) + [-] (16) + [qty] (32) + [+] (16) + [+10] (20) + 4 gaps (4px) = 108px
        int qtyControlsWidth = 20 + 16 + 32 + 16 + 20 + 4;
        int remainingForSubtotal = availableCardWidth - qtyControlsWidth - 3; // ~84px

        JLabel sampleSubtotal = new JLabel();
        sampleSubtotal.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics fm = sampleSubtotal.getFontMetrics(sampleSubtotal.getFont());

        String[] subtotalTexts = {"150 gp", "25,000 gp", "1,250,000 gp"};
        for (String costText : subtotalTexts)
        {
            int textWidth = fm.stringWidth(costText);
            assertTrue("Subtotal text '" + costText + "' (" + textWidth + "px) should fit within remaining " + remainingForSubtotal + "px on 205px sidebar",
                textWidth <= remainingForSubtotal + 10);
        }
    }

    @Test
    public void testVendorStockTableColumnsFitViewport()
    {
        int col0 = 95;  // Item Name
        int col1 = 40;  // Stock
        int col2 = 55;  // Price
        int totalTableWidth = col0 + col1 + col2;

        assertTrue("Vendor stock table columns total (" + totalTableWidth + "px) must fit within 200px viewport",
            totalTableWidth <= 200);
    }

    @Test
    public void testMonsterDetailDropsTableColumnsFitViewport()
    {
        int col0 = 95;  // Item Name
        int col1 = 35;  // Qty
        int col2 = 60;  // Rarity
        int totalDropsTableWidth = col0 + col1 + col2;

        assertTrue("Drops table columns total (" + totalDropsTableWidth + "px) must fit within 200px viewport",
            totalDropsTableWidth <= 200);
    }

    /**
     * 2026-08-20 fix (GEMINI.md Task 1): MonsterDetailView's hero-card combatLevelLabel used to
     * have a hard-coded 54px maximum/preferred/minimum width, which clipped anything past "Lvl
     * 12.." for real 4-digit combat levels in the current database (Yama at 1238, Sol Heredit at
     * 1563). This old test only sampled 1-3 digit levels ("Lvl 1".."Lvl 725"), which never
     * exceeded 54px, so it never actually caught the bug. The fix removed the fixed-width cap
     * entirely (a JLabel's own preferred width already exactly fits its text); this test checks
     * the real worst case in the current database directly instead of a synthetic sample list.
     */
    @Test
    public void testMonsterCardCombatLevelAllocation()
    {
        JLabel sampleLvlLabel = new JLabel();
        sampleLvlLabel.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics fm = sampleLvlLabel.getFontMetrics(sampleLvlLabel.getFont());

        Monster highestLevelMonster = null;
        for (Monster m : monsterDatabase.getAllMonsters())
        {
            if (highestLevelMonster == null || m.getCombatLevel() > highestLevelMonster.getCombatLevel())
            {
                highestLevelMonster = m;
            }
        }
        assertNotNull("Database should contain at least one monster with a real combat level", highestLevelMonster);

        String worstCaseText = "Lvl " + highestLevelMonster.getCombatLevel();
        int textWidth = fm.stringWidth(worstCaseText);

        MonsterDetailView monsterDetailView = new MonsterDetailView(
            itemManager, npcPortraitManager, cartManager, (monster, zone) -> {}, () -> {});
        monsterDetailView.setMonster(highestLevelMonster);
        List<JLabel> labels = findAllComponents(monsterDetailView, JLabel.class);
        JLabel combatLevelLabel = null;
        for (JLabel l : labels)
        {
            if (l.getText() != null && l.getText().startsWith("Lvl "))
            {
                combatLevelLabel = l;
                break;
            }
        }
        assertNotNull("Should find the hero card's combat level JLabel", combatLevelLabel);

        Dimension maxSize = combatLevelLabel.getMaximumSize();
        assertTrue("Combat level label must not have a fixed maximum width smaller than the real "
                + "worst-case text '" + worstCaseText + "' (" + textWidth + "px) needs - maximum width was "
                + (maxSize != null ? maxSize.width : -1) + "px",
            maxSize == null || maxSize.width >= textWidth);
    }

    /**
     * 2026-08-20 fix (GEMINI.md Task 1, user-reported live bug on Yama's card): the hero
     * card's combat level ("Lvl 12..."), HP/Max/attack-style line (cut off mid-string), and
     * badge row were all getting visually clipped by hard maximumSize height caps (heroCard
     * 76px, heroDetails 66px, combatStatsLabel/badgeRow each locked to a single 16-18px row)
     * - BoxLayout enforces maximumSize as a hard per-component ceiling on every layout pass
     * regardless of how much real content wants to render (same root cause class as the
     * MonsterDirectoryView card-height fix above, and exactly why the old
     * "must have a 76px maximum height" assertion this test used to make was itself encoding
     * the bug as a requirement). The fix removed those caps so the card grows to fit real
     * content (the user explicitly said a taller card is fine). This test renders Yama - the
     * literal monster from the bug report, whose real data has a 4-digit combat level (1238)
     * and a long multi-style attack-type string ("Melee (slash), Ranged, Magic") - forces a
     * real layout pass (not just getPreferredSize()), and verifies nothing ends up allocated
     * smaller than its own preferred size.
     */
    @Test
    public void testMonsterDetailHeroCardNotClippedForRealWorstCaseData()
    {
        MonsterDetailView monsterDetailView = new MonsterDetailView(
            itemManager,
            npcPortraitManager,
            cartManager,
            (monster, zone) -> {},
            () -> {}
        );

        Monster monster = monsterDatabase.getMonsterByName("Yama");
        if (monster == null && !monsterDatabase.getAllMonsters().isEmpty())
        {
            monster = monsterDatabase.getAllMonsters().get(0);
        }
        assertNotNull("Should find a monster to test against", monster);
        monsterDetailView.setMonster(monster);

        // Force a real layout pass against an off-screen Frame at the sidebar's default width
        // (not just getPreferredSize()) so BoxLayout's actual size allocation is exercised.
        java.awt.Frame frame = new java.awt.Frame();
        frame.setLayout(new java.awt.BorderLayout());
        frame.add(monsterDetailView, java.awt.BorderLayout.CENTER);
        frame.setSize(242, 2000);
        frame.addNotify();
        try
        {
            monsterDetailView.setSize(242, 2000);
            monsterDetailView.doLayout();
            monsterDetailView.validate();

            List<JLabel> labels = findAllComponents(monsterDetailView, JLabel.class);
            JLabel combatLevelLabel = null;
            JLabel attackStyleLabel = null;
            for (JLabel l : labels)
            {
                String text = l.getText();
                if (text == null) continue;
                if (text.startsWith("Lvl "))
                {
                    combatLevelLabel = l;
                }
                else if (text.contains("Style:"))
                {
                    attackStyleLabel = l;
                }
            }
            assertNotNull("Should find the combat level label", combatLevelLabel);
            assertNotNull("Should find the attack style label", attackStyleLabel);

            assertEquals("Combat level label text must be the real, untruncated value",
                "Lvl " + monster.getCombatLevel(), combatLevelLabel.getText());

            // The actual allocated height after layout must not be smaller than what the label
            // itself says it needs to render fully - if some ancestor still had a stale small
            // maximumSize cap, BoxLayout would clamp the actual size down below preferred here.
            assertTrue("Combat level label's actual height (" + combatLevelLabel.getHeight()
                    + "px) after layout must be >= its own preferred height (" + combatLevelLabel.getPreferredSize().height + "px)",
                combatLevelLabel.getHeight() >= combatLevelLabel.getPreferredSize().height);
            assertTrue("Attack style label's actual height (" + attackStyleLabel.getHeight()
                    + "px) after layout must be >= its own preferred height (" + attackStyleLabel.getPreferredSize().height + "px) "
                    + "- this is the exact line that was reported cut off ('HP: 2500 * Max: 46 * Melee (...')",
                attackStyleLabel.getHeight() >= attackStyleLabel.getPreferredSize().height);
        }
        finally
        {
            frame.removeNotify();
            frame.dispose();
        }
    }

    /**
     * A monster whose defence bonuses were never scraped (all five exactly 0) must read
     * "not recorded", not a wall of "+0"s presented as fact. A monster with any real bonus
     * still shows the Stab/Slash/Crush line.
     */
    @Test
    public void testMonsterDetailDefenceNotRecordedWhenAllZero()
    {
        MonsterDetailView view = new MonsterDetailView(
            itemManager, npcPortraitManager, cartManager, (monster, zone) -> {}, () -> {});

        Monster unscraped = Monster.builder()
            .id(90001).name("Test Unscraped").combatLevel(50)
            .defenceStab(0).defenceSlash(0).defenceCrush(0).defenceMagic(0).defenceRanged(0)
            .build();
        view.setMonster(unscraped);
        java.util.List<String> texts = labelTexts(view);
        assertTrue("all-zero defence should read 'not recorded': " + texts,
            texts.stream().anyMatch(t -> t.contains("not recorded")));
        assertFalse("no fabricated Stab +0 line: " + texts,
            texts.stream().anyMatch(t -> t.contains("Stab:")));

        Monster scraped = Monster.builder()
            .id(90002).name("Test Scraped").combatLevel(50)
            .defenceStab(28).defenceSlash(0).defenceCrush(-5).defenceMagic(0).defenceRanged(12)
            .build();
        view.setMonster(scraped);
        java.util.List<String> texts2 = labelTexts(view);
        assertTrue("a real bonus shows the defence line: " + texts2,
            texts2.stream().anyMatch(t -> t.contains("Stab:")));
    }

    private java.util.List<String> labelTexts(Component root)
    {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (JLabel l : findAllComponents(root, JLabel.class))
        {
            String t = l.getText();
            if (t != null)
            {
                out.add(t.replaceAll("<[^>]+>", " "));
            }
        }
        return out;
    }

    @Test
    public void testMonsterDetailDropControlsFit205pxSidebar()
    {
        MonsterDetailView monsterDetailView = new MonsterDetailView(
            itemManager,
            npcPortraitManager,
            cartManager,
            (monster, zone) -> {},
            () -> {}
        );

        // Find dropSortDropdown and viewToggleBtn
        List<javax.swing.JComboBox> dropdowns = findAllComponents(monsterDetailView, javax.swing.JComboBox.class);
        assertFalse("Should find sort dropdown in MonsterDetailView", dropdowns.isEmpty());
        javax.swing.JComboBox sortDropdown = dropdowns.get(0);

        assertEquals("Rarity (Common)", sortDropdown.getItemAt(0));
        assertEquals("Rarity (Rare)", sortDropdown.getItemAt(1));
        assertEquals("Name (A-Z)", sortDropdown.getItemAt(2));

        List<JButton> buttons = findAllComponents(monsterDetailView, JButton.class);
        JButton toggleBtn = null;
        for (JButton btn : buttons)
        {
            if ("Table".equals(btn.getText()) || "Cards".equals(btn.getText()))
            {
                toggleBtn = btn;
                break;
            }
        }

        assertNotNull("View toggle button must exist", toggleBtn);
        assertEquals("Toggle button width must be 50px", 50, toggleBtn.getPreferredSize().width);
        assertEquals("Toggle button height must be 22px", 22, toggleBtn.getPreferredSize().height);

        // Verify total width fits within 205px sidebar (205px - 8px border padding = 197px)
        int availableRowWidth = 205 - 8;
        int toggleWidth = toggleBtn.getPreferredSize().width;
        int gap = 4;
        int dropdownWidth = availableRowWidth - toggleWidth - gap; // 143px

        JButton dummyBtn = new JButton();
        dummyBtn.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = dummyBtn.getFontMetrics(dummyBtn.getFont());

        for (int i = 0; i < sortDropdown.getItemCount(); i++)
        {
            String label = (String) sortDropdown.getItemAt(i);
            int textWidth = fm.stringWidth(label);
            assertTrue("Dropdown label '" + label + "' (" + textWidth + "px) must fit within " + dropdownWidth + "px on 205px sidebar",
                textWidth + 24 <= dropdownWidth);
        }
    }

    @Test
    public void testAllSidebarViewsSetHorizontalScrollBarPolicyNever()
    {
        TownHubView townHubView = new TownHubView(
            shopDatabase,
            npcPortraitManager,
            shop -> {},
            shop -> {},
            () -> {}
        );

        VendorStockView vendorStockView = new VendorStockView(
            itemManager,
            npcPortraitManager,
            liveStockManager,
            config,
            cartManager,
            shop -> {},
            () -> {}
        );

        MonsterDirectoryView monsterDirectoryView = new MonsterDirectoryView(
            monsterDatabase,
            npcPortraitManager,
            monster -> {},
            monster -> {},
            () -> {}
        );

        MonsterDetailView monsterDetailView = new MonsterDetailView(
            itemManager,
            npcPortraitManager,
            cartManager,
            (monster, zone) -> {},
            () -> {}
        );

        GlobalItemSearchView globalItemSearchView = new GlobalItemSearchView(
            shopDatabase,
            npcPortraitManager,
            config,
            cartManager,
            shop -> {},
            shop -> {},
            () -> {}
        );

        ShoppingCartView shoppingCartView = new ShoppingCartView(
            cartManager,
            shopDatabase,
            itemManager,
            shop -> {},
            shop -> {}
        );

        SlayerTabView slayerTabView = new SlayerTabView(
            new com.osrscopilot.data.SlayerTaskManager(null, monsterDatabase),
            monsterDatabase,
            npcPortraitManager,
            (monster, zone) -> {},
            (pt, label) -> {},
            monster -> {}
        );

        Component[] views = {
            townHubView,
            vendorStockView,
            monsterDirectoryView,
            monsterDetailView,
            slayerTabView,
            globalItemSearchView,
            shoppingCartView
        };

        for (Component view : views)
        {
            List<JScrollPane> scrollPanes = findAllScrollPanes(view);
            assertFalse("View " + view.getClass().getSimpleName() + " should contain at least one JScrollPane",
                scrollPanes.isEmpty());

            for (JScrollPane sp : scrollPanes)
            {
                assertEquals("JScrollPane in " + view.getClass().getSimpleName() + " MUST have HORIZONTAL_SCROLLBAR_NEVER",
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
                    sp.getHorizontalScrollBarPolicy());
            }
        }
    }

    @Test
    public void testMonsterModelQuestRequirementAndSlayerDeduplication()
    {
        Monster gargoyle = Monster.builder()
            .id(412)
            .name("Gargoyle")
            .combatLevel(111)
            .slayerLevel(75)
            .questRequirement("Priest in Peril")
            .category("Slayer")
            .members(true)
            .build();

        assertTrue(gargoyle.hasQuestRequirement());
        assertEquals("Priest in Peril", gargoyle.getQuestRequirement());
        assertEquals(75, gargoyle.getSlayerLevel());
        assertTrue("Slayer level > 1 should be present", gargoyle.getSlayerLevel() > 1);
    }

    @Test
    public void testViewCardMaximumSizesPreventVerticalBloat()
    {
        MonsterDirectoryView monsterDirectoryView = new MonsterDirectoryView(
            monsterDatabase,
            npcPortraitManager,
            monster -> {},
            monster -> {},
            () -> {}
        );
        monsterDirectoryView.initialize();
        monsterDirectoryView.setCategory("Bosses");

        // 136px (not the old 70px) -- raised so the cap actually accommodates real card content
        // instead of clipping it; see the comment on card.setMaximumSize(...) in
        // MonsterDirectoryView.buildMonsterCard() for the measured worst-case height math.
        List<JPanel> dirCards = findAllPanels(monsterDirectoryView);
        boolean foundDirCard = false;
        for (JPanel panel : dirCards)
        {
            if (panel.getMaximumSize() != null && panel.getMaximumSize().height == 136)
            {
                foundDirCard = true;
                break;
            }
        }
        assertTrue("MonsterDirectoryView cards must have a maximumSize cap that fits real content (136px)", foundDirCard);

        TownHubView townHubView = new TownHubView(
            shopDatabase,
            npcPortraitManager,
            shop -> {},
            shop -> {},
            () -> {}
        );
        townHubView.initialize();

        List<JPanel> townCards = findAllPanels(townHubView);
        boolean foundTownCard = false;
        for (JPanel panel : townCards)
        {
            Dimension max = panel.getMaximumSize();
            // Card is pinned to its OWN natural height (full-width, height == preferred) so a wrapped
            // name / quest line / button row is never clipped -- replaces the old hard 90px cap.
            if (max != null && max.width == Integer.MAX_VALUE
                && max.height == panel.getPreferredSize().height && max.height > 30)
            {
                foundTownCard = true;
                break;
            }
        }
        assertTrue("TownHubView shop cards must be pinned to their natural (unclipped) height", foundTownCard);

        ShoppingCartView shoppingCartView = new ShoppingCartView(
            cartManager,
            shopDatabase,
            itemManager,
            shop -> {},
            shop -> {}
        );
        if (!shopDatabase.getAllShops().isEmpty() && !shopDatabase.getAllShops().get(0).getItems().isEmpty())
        {
            cartManager.addItem(shopDatabase.getAllShops().get(0).getItems().get(0), shopDatabase.getAllShops().get(0), 1);
            shoppingCartView.rebuildCart();
            List<JPanel> cartCards = findAllPanels(shoppingCartView);
            boolean foundCartCard = false;
            for (JPanel panel : cartCards)
            {
                if (panel.getMaximumSize() != null && panel.getMaximumSize().height <= 130 && panel.getMaximumSize().height >= 80)
                {
                    foundCartCard = true;
                    break;
                }
            }
            assertTrue("ShoppingCartView item cards must have compact maximumSize (<= 130px)", foundCartCard);
        }
    }

    @Test
    public void testMonsterDirectoryCardButtonRowBorderLayoutAndSizing()
    {
        MonsterDirectoryView monsterDirectoryView = new MonsterDirectoryView(
            monsterDatabase,
            npcPortraitManager,
            monster -> {},
            monster -> {},
            () -> {}
        );
        monsterDirectoryView.initialize();
        monsterDirectoryView.setCategory("Bosses");

        List<JPanel> panels = findAllPanels(monsterDirectoryView);
        JPanel cardBtnRow = null;
        JButton inspectBtn = null;
        JButton mapBtn = null;

        for (JPanel panel : panels)
        {
            if (panel.getLayout() instanceof java.awt.BorderLayout && panel.getMaximumSize() != null && panel.getMaximumSize().height == 20)
            {
                List<JButton> btns = findAllComponents(panel, JButton.class);
                if (btns.size() == 2)
                {
                    for (JButton b : btns)
                    {
                        if ("Inspect".equals(b.getText())) inspectBtn = b;
                        if ("Map".equals(b.getText())) mapBtn = b;
                    }
                    if (inspectBtn != null && mapBtn != null)
                    {
                        cardBtnRow = panel;
                        break;
                    }
                }
            }
        }

        assertNotNull("Monster card button row with BorderLayout must exist", cardBtnRow);
        assertTrue("cardBtnRow layout must be BorderLayout", cardBtnRow.getLayout() instanceof java.awt.BorderLayout);
        assertNotNull("Inspect button must exist", inspectBtn);
        assertNotNull("Map button must exist", mapBtn);

        assertEquals("Map button width must be 50px", 50, mapBtn.getPreferredSize().width);
        assertEquals("Map button height must be 20px", 20, mapBtn.getPreferredSize().height);
        assertEquals("Show this monster's spawn zone on the World Map", mapBtn.getToolTipText());

        // Verify button text fits in 205px sidebar with scrollbar
        int availableCardWidth = 205 - 16 - 8; // 181px (205 - 16px scrollbar - 8px card border padding)
        int mapBtnWidth = mapBtn.getPreferredSize().width;
        int gap = 4;
        int inspectBtnWidth = availableCardWidth - mapBtnWidth - gap; // 127px

        FontMetrics fm = inspectBtn.getFontMetrics(inspectBtn.getFont());
        int inspectTextWidth = fm.stringWidth(inspectBtn.getText());
        int mapTextWidth = fm.stringWidth(mapBtn.getText());

        assertTrue("Inspect button text (" + inspectTextWidth + "px) must fit within " + inspectBtnWidth + "px",
            inspectTextWidth + 8 <= inspectBtnWidth);
        assertTrue("Map button text (" + mapTextWidth + "px) must fit within " + mapBtnWidth + "px",
            mapTextWidth + 8 <= mapBtnWidth);
    }

    @Test
    public void testMonsterDirectoryCardWithoutSpawnZonesRendersFullWidthInspect()
    {
        MonsterDirectoryView monsterDirectoryView = new MonsterDirectoryView(
            monsterDatabase,
            npcPortraitManager,
            monster -> {},
            monster -> {},
            () -> {}
        );
        monsterDirectoryView.initialize();
        monsterDirectoryView.setCategory("Bosses");

        List<JPanel> panels = findAllPanels(monsterDirectoryView);
        JButton fullInspectBtn = null;

        for (JPanel panel : panels)
        {
            if (panel.getLayout() instanceof java.awt.BorderLayout && panel.getMaximumSize() != null && panel.getMaximumSize().height == 20)
            {
                List<JButton> btns = findAllComponents(panel, JButton.class);
                if (btns.size() == 1 && "Inspect".equals(btns.get(0).getText()))
                {
                    fullInspectBtn = btns.get(0);
                    break;
                }
            }
        }

        assertNotNull("Monster card without spawn zones must render full-width 'Inspect' button", fullInspectBtn);
        assertEquals("View combat stats, weaknesses, spawn locations, and loot drop table", fullInspectBtn.getToolTipText());
        assertEquals(new Insets(0, 2, 0, 2), fullInspectBtn.getMargin());
    }

    @Test
    public void testSlayerTabActiveTaskCardDimensions() throws Exception
    {
        com.osrscopilot.data.SlayerTaskManager taskManager = new com.osrscopilot.data.SlayerTaskManager(null, monsterDatabase);
        taskManager.setTaskDetails("Gargoyle", 150, 150, null, null);

        final SlayerTabView[] viewHolder = new SlayerTabView[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            viewHolder[0] = new SlayerTabView(
                taskManager,
                monsterDatabase,
                npcPortraitManager,
                (monster, zone) -> {},
                (pt, label) -> {},
                monster -> {}
            );
        });

        SlayerTabView slayerTabView = viewHolder[0];
        List<JPanel> panels = findAllPanels(slayerTabView);

        JPanel activeCard = null;
        for (JPanel p : panels)
        {
            if (p.getMaximumSize() != null && p.getMaximumSize().height == 175)
            {
                activeCard = p;
                break;
            }
        }
        assertNotNull("Active task card with 175px max height must exist", activeCard);
        assertEquals(175, activeCard.getMaximumSize().height);

        JPanel taskContainer = null;
        for (JPanel p : panels)
        {
            if (p.getMaximumSize() != null && p.getMaximumSize().height == 185)
            {
                taskContainer = p;
                break;
            }
        }
        assertNotNull("Task container with 185px max height must exist", taskContainer);
        assertEquals(185, taskContainer.getMaximumSize().height);
    }

    @Test
    public void testTownHubQuestRequirementFormattingWithoutLockEmoji()
    {
        TownHubView townHubView = new TownHubView(
            shopDatabase,
            npcPortraitManager,
            shop -> {},
            shop -> {},
            () -> {}
        );
        townHubView.initialize();

        List<JLabel> labels = findAllComponents(townHubView, JLabel.class);
        boolean foundQuestReq = false;

        for (JLabel lbl : labels)
        {
            String text = lbl.getText();
            if (text != null && text.startsWith("[Req:"))
            {
                foundQuestReq = true;
                assertFalse("Quest requirement label must not contain raw lock emoji", text.contains("🔒"));
            }
        }

        assertTrue("Should find at least one shop with quest requirement formatted as [Req: ...]", foundQuestReq);
    }

    @Test
    public void testSlayerTabLabelsAndButtonsAreEmojiFree() throws Exception
    {
        com.osrscopilot.data.SlayerTaskManager taskManager = new com.osrscopilot.data.SlayerTaskManager(null, monsterDatabase);
        taskManager.setTaskDetails("Gargoyle", 150, 150, null, null);

        final SlayerTabView[] viewHolder = new SlayerTabView[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> viewHolder[0] = new SlayerTabView(
            taskManager, monsterDatabase, npcPortraitManager,
            (monster, zone) -> {}, (pt, label) -> {}, monster -> {}));

        java.util.List<java.awt.Component> texts = new java.util.ArrayList<>();
        texts.addAll(findAllComponents(viewHolder[0], JLabel.class));
        texts.addAll(findAllComponents(viewHolder[0], JButton.class));
        for (java.awt.Component c : texts)
        {
            String t = c instanceof JLabel ? ((JLabel) c).getText() : ((JButton) c).getText();
            if (t == null)
            {
                continue;
            }
            // The RuneScape bitmap font has no emoji glyphs - astral-plane chars render as tofu.
            assertFalse("Slayer UI text must be emoji-free: \"" + t + "\"",
                t.codePoints().anyMatch(cp -> cp > 0xFFFF));
        }
    }

    @Test
    public void testSlayerTabTaskButtonRowBorderLayoutAndSizing() throws Exception
    {
        com.osrscopilot.data.SlayerTaskManager taskManager = new com.osrscopilot.data.SlayerTaskManager(null, monsterDatabase);
        taskManager.setTaskDetails("Gargoyle", 150, 150, null, null);

        final SlayerTabView[] viewHolder = new SlayerTabView[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            viewHolder[0] = new SlayerTabView(
                taskManager,
                monsterDatabase,
                npcPortraitManager,
                (monster, zone) -> {},
                (pt, label) -> {},
                monster -> {}
            );
        });

        SlayerTabView slayerTabView = viewHolder[0];

        List<JButton> buttons = findAllComponents(slayerTabView, JButton.class);
        JButton detailsBtn = null;
        JButton resetBtn = null;

        for (JButton btn : buttons)
        {
            if ("Mob Details".equals(btn.getText())) detailsBtn = btn;
            if ("Reset Task".equals(btn.getText())) resetBtn = btn;
        }

        assertNotNull("Mob Details button must exist in active task card", detailsBtn);
        assertNotNull("Reset Task button must exist in active task card", resetBtn);

        assertEquals("Mob Details button height must be 24px", 24, detailsBtn.getPreferredSize().height);
        assertEquals("Reset Task button height must be 24px", 24, resetBtn.getPreferredSize().height);

        int availableCardWidth = 205 - 16 - 10 - 8; // ~171px
        int gap = 4;
        int eachBtnWidth = (availableCardWidth - gap) / 2; // ~83px each

        FontMetrics detailsFm = detailsBtn.getFontMetrics(detailsBtn.getFont());
        FontMetrics resetFm = resetBtn.getFontMetrics(resetBtn.getFont());
        int detailsTextWidth = detailsFm.stringWidth(detailsBtn.getText());
        int resetTextWidth = resetFm.stringWidth(resetBtn.getText());

        assertTrue("Mob Details text (" + detailsTextWidth + "px) must fit within " + eachBtnWidth + "px",
            detailsTextWidth + 6 <= eachBtnWidth);
        assertTrue("Reset Task text (" + resetTextWidth + "px) must fit within " + eachBtnWidth + "px",
            resetTextWidth + 6 <= eachBtnWidth);
    }


    @Test
    public void testAllViewsButtonRowsAndLabelsFit205pxSidebar()
    {
        // The map-focus control is labelled "Map" everywhere now (was a mix of "Focus" / "Focus Map").
        // 1. TownHubView: Stock + Map
        int townCardWidth = 205 - 16 - 6; // 183px
        int townBtnWidth = (townCardWidth - 3) / 2; // 90px
        JButton dummyBtn = new JButton();
        dummyBtn.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = dummyBtn.getFontMetrics(dummyBtn.getFont());
        assertTrue(fm.stringWidth("Stock") + 8 <= townBtnWidth);
        assertTrue(fm.stringWidth("Map") + 8 <= townBtnWidth);

        // 2. VendorStockView: Map + Shop Wiki
        int vendorHeaderWidth = 205 - 12; // 193px
        int vendorActionBtnWidth = (vendorHeaderWidth - 4) / 2; // 94px
        assertTrue(fm.stringWidth("Map") + 8 <= vendorActionBtnWidth);
        assertTrue(fm.stringWidth("Shop Wiki") + 8 <= vendorActionBtnWidth);

        // 3. MonsterDetailView: Map + Wiki
        int detailActionWidth = 205 - 8 - 16; // 181px
        int detailBtnWidth = (detailActionWidth - 3) / 2; // 89px
        assertTrue(fm.stringWidth("Map") + 8 <= detailBtnWidth);
        assertTrue(fm.stringWidth("Wiki") + 8 <= detailBtnWidth);

        // 4. GlobalItemSearchView: View + Map + +
        int searchActionWidth = 205 - 16 - 6; // 183px
        int searchBtnWidth = (searchActionWidth - 6) / 3; // 59px
        assertTrue(fm.stringWidth("View") + 6 <= searchBtnWidth);
        assertTrue(fm.stringWidth("Map") + 6 <= searchBtnWidth);
        assertTrue(fm.stringWidth("+") + 6 <= searchBtnWidth);

        // 5. ShoppingCartView: Map + Stock
        assertTrue(fm.stringWidth("Map") + 6 <= 50);
        assertTrue(fm.stringWidth("Stock") + 6 <= 50);
    }

    /**
     * Regression test for the live-play bug where bestiary cards were vertically clipped,
     * hiding the Inspect/Map button row. Root cause: the card's maximumSize height (formerly 70px) was
     * smaller than the card's own real content height, and BoxLayout enforces maximumSize as a hard
     * per-component ceiling on every layout pass (not just when a container has leftover space to
     * distribute) -- so the card's actual allocated size was clamped down from its true preferred
     * height, clipping the badge and button rows out of the card's visible/clickable bounds.
     *
     * This test builds the real longest-name card from the current database, forces a real layout pass
     * (setSize/doLayout/validate against an off-screen java.awt.Frame, not just getPreferredSize()), and
     * asserts the card's ACTUAL allocated height after layout matches its true preferred height instead
     * of being clamped -- i.e. nothing is silently clipped anymore.
     */
    @Test
    public void testMonsterDirectoryCardHeightIsNotClippedByMaximumSizeCap()
    {
        MonsterDirectoryView monsterDirectoryView = new MonsterDirectoryView(
            monsterDatabase,
            npcPortraitManager,
            monster -> {},
            monster -> {},
            () -> {}
        );
        // Find the longest-name monster actually in the current database (worst-case 2-line wrap).
        Monster longest = null;
        for (Monster m : monsterDatabase.getAllMonsters())
        {
            if (longest == null || m.getName().length() > longest.getName().length())
            {
                longest = m;
            }
        }
        assertNotNull("Monster database should not be empty", longest);

        monsterDirectoryView.initialize();
        monsterDirectoryView.searchMonster(longest.getName());

        List<JLabel> labels = findAllComponents(monsterDirectoryView, JLabel.class);
        JLabel targetLabel = null;
        for (JLabel l : labels)
        {
            if (longest.getName().equals(l.getToolTipText()))
            {
                targetLabel = l;
                break;
            }
        }
        assertNotNull("Should find nameLabel for longest-name monster: " + longest.getName(), targetLabel);

        // Walk up: nameLabel -> nameLine -> detailsCol -> topRow -> card (the ancestor that actually
        // carries the explicit maximumSize cap set in MonsterDirectoryView.buildMonsterCard()).
        Component c = targetLabel;
        JPanel card = null;
        for (int i = 0; i < 8 && c != null; i++)
        {
            c = c.getParent();
            if (c instanceof JPanel && ((JPanel) c).getMaximumSize() != null
                && ((JPanel) c).getMaximumSize().height < Integer.MAX_VALUE)
            {
                card = (JPanel) c;
                break;
            }
        }
        assertNotNull("Should find the card panel ancestor with an explicit height cap", card);

        Dimension cardPref = card.getPreferredSize();
        Dimension cardMax = card.getMaximumSize();

        assertTrue("Card's real preferred height (" + cardPref.height + "px) must fit within its own "
            + "maximumSize cap (" + cardMax.height + "px) -- if preferred exceeds max, BoxLayout will "
            + "clip the card down to max on every layout pass, hiding rows",
            cardPref.height <= cardMax.height);

        // Force a real layout pass against an off-screen Frame (not just getPreferredSize()) and verify
        // the card's ACTUAL allocated size after layout equals its preferred size -- i.e. nothing got
        // silently clamped down to the cap.
        java.awt.Frame frame = new java.awt.Frame();
        frame.setLayout(new java.awt.BorderLayout());
        frame.add(monsterDirectoryView, java.awt.BorderLayout.CENTER);
        frame.setSize(225, 2000);
        frame.addNotify();
        try
        {
            monsterDirectoryView.setSize(225, 2000);
            monsterDirectoryView.doLayout();
            monsterDirectoryView.validate();

            Dimension cardActual = card.getSize();
            assertEquals("Card's actual allocated height after a real layout pass must equal its "
                + "preferred height (not be clamped down to the maximumSize cap), or the button row "
                + "gets clipped out of the card's clickable bounds",
                cardPref.height, cardActual.height);
        }
        finally
        {
            frame.removeNotify();
            frame.dispose();
        }
    }

    @Test
    public void testMonsterDetailHeroCardCompactHeaderAndActionButtonsLayout()
    {
        MonsterDetailView monsterDetailView = new MonsterDetailView(
            itemManager,
            npcPortraitManager,
            cartManager,
            (monster, zone) -> {},
            () -> {}
        );

        Monster monster = monsterDatabase.getMonsterByName("Gargoyle");
        if (monster == null && !monsterDatabase.getAllMonsters().isEmpty())
        {
            monster = monsterDatabase.getAllMonsters().get(0);
        }
        assertNotNull("Should find a monster to test against", monster);
        monsterDetailView.setMonster(monster);

        // Verify action buttons [Map] and [Wiki] exist and have fixed 20px height and Insets(0, 1, 0, 1).
        // MonsterDetailView also has per-spawn-zone "Map" buttons, so pin to the one that sits next
        // to "Wiki" in the same action row.
        List<JButton> buttons = findAllComponents(monsterDetailView, JButton.class);
        JButton wikiBtn = null;
        for (JButton b : buttons)
        {
            if ("Wiki".equals(b.getText())) wikiBtn = b;
        }
        assertNotNull("Wiki button must exist", wikiBtn);

        JButton focusBtn = null;
        for (java.awt.Component sib : wikiBtn.getParent().getComponents())
        {
            if (sib instanceof JButton && "Map".equals(((JButton) sib).getText())) focusBtn = (JButton) sib;
        }
        assertNotNull("Map button must exist alongside Wiki in the action row", focusBtn);

        assertEquals("Map button margin must be Insets(0, 1, 0, 1)", new Insets(0, 1, 0, 1), focusBtn.getMargin());
        assertEquals("Wiki button margin must be Insets(0, 1, 0, 1)", new Insets(0, 1, 0, 1), wikiBtn.getMargin());

        // Verify actionRow panel has fixed 20px height and GridLayout(1, 2, 2, 0)
        Container actionRow = focusBtn.getParent();
        assertNotNull("Action buttons must be in an actionRow container", actionRow);
        assertTrue("actionRow must use GridLayout", actionRow.getLayout() instanceof java.awt.GridLayout);
        java.awt.GridLayout gridLayout = (java.awt.GridLayout) actionRow.getLayout();
        assertEquals(1, gridLayout.getRows());
        assertEquals(2, gridLayout.getColumns());
        assertEquals(2, gridLayout.getHgap());
        assertEquals(0, gridLayout.getVgap());
        assertEquals(20, actionRow.getPreferredSize().height);
        assertEquals(20, actionRow.getMaximumSize().height);

        // Verify heroCard wrapper has BorderLayout and contains heroCard in NORTH
        List<JPanel> panels = findAllPanels(monsterDetailView);
        boolean foundHeroNorthWrapper = false;
        for (JPanel panel : panels)
        {
            if (panel.getLayout() instanceof java.awt.BorderLayout)
            {
                java.awt.BorderLayout bl = (java.awt.BorderLayout) panel.getLayout();
                Component northComp = bl.getLayoutComponent(java.awt.BorderLayout.NORTH);
                if (northComp instanceof JPanel)
                {
                    List<JLabel> labels = findAllComponents(northComp, JLabel.class);
                    boolean hasCombatLvl = labels.stream().anyMatch(l -> l.getText() != null && l.getText().startsWith("Lvl "));
                    if (hasCombatLvl)
                    {
                        foundHeroNorthWrapper = true;
                        break;
                    }
                }
            }
        }
        assertTrue("Hero card must be wrapped inside a BorderLayout.NORTH container to prevent vertical void stretching",
            foundHeroNorthWrapper);
    }

    @Test
    public void testMonsterDetailDropCategorizationAndRarityFormatting()
    {
        // 1. 100% Drops
        com.osrscopilot.data.model.MonsterDrop bones = com.osrscopilot.data.model.MonsterDrop.builder()
            .name("Bones")
            .quantity("1")
            .rarity(1.0)
            .build();
        assertEquals("100% Drops", MonsterDetailView.categorizeDrop(bones));
        assertEquals("Always (1/1)", MonsterDetailView.formatMathematicalRarity(bones));

        // 2. Weapons and Armour
        com.osrscopilot.data.model.MonsterDrop rune2h = com.osrscopilot.data.model.MonsterDrop.builder()
            .name("Rune 2h sword")
            .quantity("1")
            .rarity(1.0 / 128.0)
            .build();
        assertEquals("Weapons and Armour", MonsterDetailView.categorizeDrop(rune2h));
        assertEquals("1/128", MonsterDetailView.formatMathematicalRarity(rune2h));

        com.osrscopilot.data.model.MonsterDrop adamantPlate = com.osrscopilot.data.model.MonsterDrop.builder()
            .name("Adamant platebody")
            .quantity("1")
            .rarity(1.0 / 64.0)
            .build();
        assertEquals("Weapons and Armour", MonsterDetailView.categorizeDrop(adamantPlate));
        assertEquals("1/64", MonsterDetailView.formatMathematicalRarity(adamantPlate));

        // 3. Runes and Ammunition
        com.osrscopilot.data.model.MonsterDrop natureRune = com.osrscopilot.data.model.MonsterDrop.builder()
            .name("Nature rune")
            .quantity("67")
            .rarity(1.0 / 15.0)
            .build();
        assertEquals("Runes and Ammunition", MonsterDetailView.categorizeDrop(natureRune));

        com.osrscopilot.data.model.MonsterDrop runeArrow = com.osrscopilot.data.model.MonsterDrop.builder()
            .name("Rune arrow")
            .quantity("42")
            .rarity(1.0 / 25.0)
            .build();
        assertEquals("Runes and Ammunition", MonsterDetailView.categorizeDrop(runeArrow));

        // 4. Herbs
        com.osrscopilot.data.model.MonsterDrop grimyRanarr = com.osrscopilot.data.model.MonsterDrop.builder()
            .name("Grimy ranarr weed")
            .quantity("1")
            .rarity(1.0 / 512.0)
            .build();
        assertEquals("Herbs", MonsterDetailView.categorizeDrop(grimyRanarr));
        assertEquals("1/512", MonsterDetailView.formatMathematicalRarity(grimyRanarr));

        // 5. Seeds
        com.osrscopilot.data.model.MonsterDrop snapdragonSeed = com.osrscopilot.data.model.MonsterDrop.builder()
            .name("Snapdragon seed")
            .quantity("1")
            .rarity(1.0 / 843.0)
            .build();
        assertEquals("Seeds", MonsterDetailView.categorizeDrop(snapdragonSeed));

        // 6. Tertiary / Uniques
        com.osrscopilot.data.model.MonsterDrop clueHard = com.osrscopilot.data.model.MonsterDrop.builder()
            .name("Clue scroll (hard)")
            .quantity("1")
            .rarity(1.0 / 128.0)
            .build();
        assertEquals("Tertiary / Uniques", MonsterDetailView.categorizeDrop(clueHard));

        com.osrscopilot.data.model.MonsterDrop dwh = com.osrscopilot.data.model.MonsterDrop.builder()
            .name("Dragon warhammer")
            .quantity("1")
            .rarity(1.0 / 5000.0)
            .build();
        assertEquals("Tertiary / Uniques", MonsterDetailView.categorizeDrop(dwh));
        assertEquals("1/5,000", MonsterDetailView.formatMathematicalRarity(dwh));

        // 7. Other
        com.osrscopilot.data.model.MonsterDrop coins = com.osrscopilot.data.model.MonsterDrop.builder()
            .name("Coins")
            .quantity("10,000")
            .rarity(1.0 / 10.0)
            .build();
        assertEquals("Other", MonsterDetailView.categorizeDrop(coins));
    }

    @Test
    public void testMonsterDetailCategorizedDropTableCardAndTableView()
    {
        MonsterDetailView monsterDetailView = new MonsterDetailView(
            itemManager,
            npcPortraitManager,
            cartManager,
            (monster, zone) -> {},
            () -> {}
        );

        Monster gargoyle = monsterDatabase.getMonsterByName("Gargoyle");
        if (gargoyle == null && !monsterDatabase.getAllMonsters().isEmpty())
        {
            gargoyle = monsterDatabase.getAllMonsters().get(0);
        }
        assertNotNull("Should find monster in database", gargoyle);
        monsterDetailView.setMonster(gargoyle);

        // Verify category headers exist in Card view
        List<JLabel> labels = findAllComponents(monsterDetailView, JLabel.class);
        boolean foundCategoryHeader = false;
        for (JLabel l : labels)
        {
            String text = l.getText();
            if (text != null && (text.contains("100% Drops (") || text.contains("Weapons and Armour (") || text.contains("Runes and Ammunition (") || text.contains("Other (")))
            {
                foundCategoryHeader = true;
                break;
            }
        }
        assertTrue("MonsterDetailView must render distinct category header bars with item counts", foundCategoryHeader);

        // Toggle to Table view and verify table rendering
        List<JButton> buttons = findAllComponents(monsterDetailView, JButton.class);
        JButton toggleBtn = null;
        for (JButton b : buttons)
        {
            if ("Table".equals(b.getText()))
            {
                toggleBtn = b;
                break;
            }
        }
        assertNotNull("Table toggle button must exist", toggleBtn);
        toggleBtn.doClick();

        assertEquals("Button text after toggle should be 'Cards'", "Cards", toggleBtn.getText());
        List<javax.swing.JTable> tables = findAllComponents(monsterDetailView, javax.swing.JTable.class);
        assertFalse("Table view should render category tables", tables.isEmpty());
    }

    @Test
    public void testAboutViewNavigationAndContent()
    {
        java.util.concurrent.atomic.AtomicReference<String> navigatedTab = new java.util.concurrent.atomic.AtomicReference<>();
        com.osrscopilot.ui.AboutView aboutView = new com.osrscopilot.ui.AboutView(navigatedTab::set);

        List<JButton> buttons = findAllComponents(aboutView, JButton.class);
        assertFalse("About view should have feature navigation buttons", buttons.isEmpty());

        JButton townsJumpBtn = buttons.stream()
            .filter(b -> "Towns".equals(b.getText()))
            .findFirst()
            .orElse(null);
        assertNotNull("Towns jump button must exist", townsJumpBtn);
        townsJumpBtn.doClick();
        assertEquals("Clicking Towns should navigate to TOWNS tab", com.osrscopilot.ui.OsrsCopilotPanel.VIEW_TOWNS, navigatedTab.get());

        // The "New here?" strip carries the interactive tour launchers.
        JButton tourBtn = buttons.stream()
            .filter(b -> "Combat HUD tour".equals(b.getText()))
            .findFirst()
            .orElse(null);
        assertNotNull("About view must expose a Combat HUD tour button", tourBtn);
        boolean[] tourStarted = {false};
        aboutView.setOnStartCombatTour(() -> tourStarted[0] = true);
        tourBtn.doClick();
        assertTrue("Clicking it must invoke the tour callback", tourStarted[0]);

        JButton slayerTourBtn = buttons.stream()
            .filter(b -> "Slayer tour".equals(b.getText()))
            .findFirst()
            .orElse(null);
        assertNotNull("About view must expose a Slayer tour button", slayerTourBtn);
        boolean[] slayerTourStarted = {false};
        aboutView.setOnStartSlayerTour(() -> slayerTourStarted[0] = true);
        slayerTourBtn.doClick();
        assertTrue("Clicking it must invoke the Slayer tour callback", slayerTourStarted[0]);
    }

    @Test
    public void testSidePanelNavigationTooltips()
    {
        com.osrscopilot.ui.OsrsCopilotPanel panel = new com.osrscopilot.ui.OsrsCopilotPanel(
            shopDatabase,
            monsterDatabase,
            new com.osrscopilot.data.SlayerTaskManager(null),
            itemManager,
            npcPortraitManager,
            liveStockManager,
            new com.osrscopilot.ui.ShopDirectorySpreadsheetDialog(shopDatabase, monsterDatabase, new com.osrscopilot.data.SlayerTaskManager(null), itemManager, npcPortraitManager, config, cartManager),
            config,
            cartManager,
            null,
            null,
            null,
            null
        );

        List<JButton> buttons = findAllComponents(panel, JButton.class);
        JButton towns = buttons.stream().filter(b -> "Towns".equals(b.getText())).findFirst().orElse(null);
        JButton shops = buttons.stream().filter(b -> "Shops".equals(b.getText())).findFirst().orElse(null);
        JButton monsters = buttons.stream().filter(b -> "Bestiary".equals(b.getText()) || "Mobs".equals(b.getText()) || "Monsters".equals(b.getText())).findFirst().orElse(null);
        JButton combat = buttons.stream().filter(b -> "Combat".equals(b.getText())).findFirst().orElse(null);
        JButton slayer = buttons.stream().filter(b -> "Slayer".equals(b.getText())).findFirst().orElse(null);
        JButton search = buttons.stream().filter(b -> "Search".equals(b.getText())).findFirst().orElse(null);
        JButton cart = buttons.stream().filter(b -> "Cart".equals(b.getText())).findFirst().orElse(null);
        JButton about = buttons.stream().filter(b -> "About".equals(b.getText())).findFirst().orElse(null);

        assertNotNull("Towns button must exist", towns);
        assertNotNull("Shops button must exist", shops);
        assertNotNull("Monsters button must exist", monsters);
        assertNotNull("Combat button must exist", combat);
        assertNotNull("Slayer button must exist", slayer);
        assertNotNull("Search button must exist", search);
        assertNotNull("Cart button must exist", cart);
        assertNotNull("About button must exist", about);

        assertNotNull("Towns button must have tooltip", towns.getToolTipText());
        assertNotNull("Shops button must have tooltip", shops.getToolTipText());
        assertNotNull("Monsters button must have tooltip", monsters.getToolTipText());
        assertNotNull("Combat button must have tooltip", combat.getToolTipText());
        assertNotNull("Slayer button must have tooltip", slayer.getToolTipText());
        assertNotNull("Search button must have tooltip", search.getToolTipText());
        assertNotNull("Cart button must have tooltip", cart.getToolTipText());
        assertNotNull("About button must have tooltip", about.getToolTipText());

        panel.showTab(com.osrscopilot.ui.OsrsCopilotPanel.VIEW_ABOUT);

    }

    @Test
    public void testGlobalItemSearchAndAboutViewViewportAndCardFitting()
    {
        GlobalItemSearchView searchView = new GlobalItemSearchView(
            shopDatabase, monsterDatabase, npcPortraitManager, config, cartManager,
            shop -> {}, shop -> {}, (monster, zone) -> {}, monster -> {}, () -> {}
        );
        searchView.setSize(new Dimension(225, 600));
        searchView.doLayout();

        List<JScrollPane> searchScrolls = findAllScrollPanes(searchView);
        assertEquals("GlobalItemSearchView must have 1 JScrollPane", 1, searchScrolls.size());
        assertEquals("GlobalItemSearchView horizontal scroll policy must be NEVER",
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
            searchScrolls.get(0).getHorizontalScrollBarPolicy());

        Component searchViewportView = searchScrolls.get(0).getViewport().getView();
        assertTrue("GlobalItemSearchView viewport view must implement Scrollable",
            searchViewportView instanceof Scrollable);
        assertTrue("GlobalItemSearchView viewport view must track viewport width",
            ((Scrollable) searchViewportView).getScrollableTracksViewportWidth());

        // Perform search for 'hammer'
        searchView.performSearch("hammer");
        searchView.doLayout();

        // Check AboutView
        AboutView aboutView = new AboutView(tab -> {});
        aboutView.setSize(new Dimension(225, 600));
        aboutView.doLayout();

        List<JScrollPane> aboutScrolls = findAllScrollPanes(aboutView);
        assertEquals("AboutView must have 1 JScrollPane", 1, aboutScrolls.size());
        assertEquals("AboutView horizontal scroll policy must be NEVER",
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
            aboutScrolls.get(0).getHorizontalScrollBarPolicy());

        Component aboutViewportView = aboutScrolls.get(0).getViewport().getView();
        assertTrue("AboutView viewport view must implement Scrollable",
            aboutViewportView instanceof Scrollable);
        assertTrue("AboutView viewport view must track viewport width",
            ((Scrollable) aboutViewportView).getScrollableTracksViewportWidth());

        // Regression guard for the in-client clipping bug: the tagline / status / "Good to
        // know" / tester lines used to be plain non-wrapping JLabels that clipped mid-word in
        // the ~215px side panel. Any run of body text long enough to overflow the panel MUST
        // be an <html> label with a fixed body width so it wraps instead of clipping. (Short
        // labels -- the title and the section headers -- are allowed to stay plain.)
        for (JLabel lbl : findAllComponents(aboutView, JLabel.class))
        {
            String text = lbl.getText();
            if (text == null)
            {
                continue;
            }
            if (text.startsWith("<html"))
            {
                assertTrue("AboutView wrapped label must pin a body width so it wraps: " + text,
                    text.contains("width:") || text.contains("width :"));
            }
            else
            {
                assertTrue("AboutView label \"" + text + "\" is long enough to clip in the ~215px "
                        + "side panel and must be an <html> width-pinned label so it wraps",
                    text.length() <= 28);
            }
        }

        // Check CombatEncounterTabView
        net.runelite.api.Client mockClient = Mockito.mock(net.runelite.api.Client.class);
        com.osrscopilot.combat.engine.CombatEncounterManager encMgr = new com.osrscopilot.combat.engine.CombatEncounterManager(mockClient);
        com.osrscopilot.combat.ui.CombatEncounterTabView combatView = new com.osrscopilot.combat.ui.CombatEncounterTabView(
            encMgr, monsterDatabase, itemManager, monster -> {}
        );
        combatView.setSize(new Dimension(225, 600));
        combatView.doLayout();

        // The interactive tutorial's panel walk-through drives this - it must not throw for any key.
        combatView.tourScrollTo("top");
        combatView.tourScrollTo("damage");
        combatView.tourScrollTo("ledger");
        combatView.tourScrollTo("share");
        assertNotNull("share row is ringed while the tour talks about it", combatView.shareRowBorderForTest());
        combatView.tourScrollTo("bogus");
        combatView.tourScrollTo(null);
        assertNull("tour teardown clears the share-row highlight", combatView.shareRowBorderForTest());

        List<JScrollPane> combatScrolls = findAllScrollPanes(combatView);
        assertEquals("CombatEncounterTabView must have 1 JScrollPane", 1, combatScrolls.size());
        assertEquals("CombatEncounterTabView horizontal scroll policy must be NEVER",
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
            combatScrolls.get(0).getHorizontalScrollBarPolicy());

        Component combatViewportView = combatScrolls.get(0).getViewport().getView();
        assertTrue("CombatEncounterTabView viewport view must implement Scrollable",
            combatViewportView instanceof Scrollable);
        assertTrue("CombatEncounterTabView viewport view must track viewport width",
            ((Scrollable) combatViewportView).getScrollableTracksViewportWidth());
    }


    private List<JPanel> findAllPanels(Component component)
    {
        return findAllComponents(component, JPanel.class);
    }

    @SuppressWarnings("unchecked")
    private <T extends Component> List<T> findAllComponents(Component component, Class<T> clazz)
    {
        List<T> list = new ArrayList<>();
        if (clazz.isInstance(component))
        {
            list.add((T) component);
        }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                list.addAll(findAllComponents(child, clazz));
            }
        }
        return list;
    }

    private List<JScrollPane> findAllScrollPanes(Component component)
    {
        return findAllComponents(component, JScrollPane.class);
    }
}
