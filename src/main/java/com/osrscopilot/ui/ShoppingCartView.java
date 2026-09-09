package com.osrscopilot.ui;

import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.ShoppingCartManager;
import com.osrscopilot.data.model.CartItem;
import com.osrscopilot.data.model.Shop;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
public class ShoppingCartView extends JPanel
{
    private static final Color TITLE_COLOR = new Color(255, 152, 31);
    private static final Color GOLD_COLOR = new Color(255, 215, 0);
    private static final Color BUDGET_GREEN = new Color(74, 222, 128);
    private static final Color BUDGET_RED = new Color(248, 113, 113);
    private static final Color SHOP_CYAN = new Color(90, 200, 250);
    private static final Color DELETE_RED = new Color(252, 128, 128);

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private final ShoppingCartManager cartManager;
    private final ShopDatabase shopDatabase;
    private final ItemManager itemManager;
    private final Consumer<Shop> onFocusShopOnMap;
    private final Consumer<Shop> onInspectShop;

    private final JTextField budgetField = new JTextField("100,000", 8);
    private final JLabel bankGpLabel = new JLabel("Bank GP: Not Synced");
    private final JButton useBankGpBtn = new JButton("Use Bank GP");
    private final JCheckBox dynamicDepletionCheckbox = new JCheckBox("Dynamic price rise (stock depletion)", true);
    private final JLabel totalCostLabel = new JLabel("Total: 0 gp");
    private final JLabel budgetLeftLabel = new JLabel("Budget Left: +100,000 gp");
    private final JLabel itemCountLabel = new JLabel("0 items in cart");
    private final JPanel itemsContainer = new JPanel();
    private final JPanel emptyPanel = new JPanel();
    private boolean userModifiedBudget = false;

    public ShoppingCartView(
        ShoppingCartManager cartManager,
        ShopDatabase shopDatabase,
        ItemManager itemManager,
        Consumer<Shop> onFocusShopOnMap,
        Consumer<Shop> onInspectShop)
    {
        this.cartManager = cartManager;
        this.shopDatabase = shopDatabase;
        this.itemManager = itemManager;
        this.onFocusShopOnMap = onFocusShopOnMap;
        this.onInspectShop = onInspectShop;

        if (cartManager.getLastKnownBankCoins() > 0)
        {
            budgetField.setText(NUMBER_FORMAT.format(cartManager.getLastKnownBankCoins()));
        }

        setLayout(new BorderLayout(0, 0));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // 1. Top Header: Budget Tracker & Grand Total Cost
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Header Title Row
        JPanel titleRow = new JPanel(new BorderLayout(4, 0));
        titleRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        titleRow.setAlignmentX(LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel("Shopping Cart & Planner");
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setForeground(TITLE_COLOR);
        titleRow.add(titleLabel, BorderLayout.WEST);

        JButton clearAllBtn = new JButton("Clear All");
        clearAllBtn.setFont(FontManager.getRunescapeSmallFont());
        clearAllBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        clearAllBtn.setForeground(DELETE_RED);
        clearAllBtn.setFocusPainted(false);
        clearAllBtn.setMargin(new Insets(1, 4, 1, 4));
        clearAllBtn.setToolTipText("Remove all items from your shopping cart");
        clearAllBtn.addActionListener(e -> {
            if (cartManager.getItemCount() > 0)
            {
                int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to clear all items from your shopping cart?",
                    "Clear Shopping Cart",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                );
                if (confirm == JOptionPane.YES_OPTION)
                {
                    cartManager.clear();
                }
            }
        });
        titleRow.add(clearAllBtn, BorderLayout.EAST);
        headerPanel.add(titleRow);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        // Bank GP Row
        JPanel bankGpRow = new JPanel(new BorderLayout(4, 0));
        bankGpRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bankGpRow.setAlignmentX(LEFT_ALIGNMENT);

        bankGpLabel.setFont(FontManager.getRunescapeBoldFont());
        bankGpLabel.setForeground(GOLD_COLOR);
        bankGpLabel.setToolTipText("Auto-extracted from your Bank. Open your bank in-game to sync.");
        bankGpRow.add(bankGpLabel, BorderLayout.WEST);

        useBankGpBtn.setFont(FontManager.getRunescapeSmallFont());
        useBankGpBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        useBankGpBtn.setForeground(GOLD_COLOR);
        useBankGpBtn.setFocusPainted(false);
        useBankGpBtn.setMargin(new Insets(1, 4, 1, 4));
        useBankGpBtn.setToolTipText("Set your budget to your current Bank GP");
        useBankGpBtn.setEnabled(false);
        useBankGpBtn.addActionListener(e -> {
            if (cartManager.getLastKnownBankCoins() >= 0)
            {
                userModifiedBudget = true;
                budgetField.setText(NUMBER_FORMAT.format(cartManager.getLastKnownBankCoins()));
                updateBudgetCalculations();
            }
        });
        bankGpRow.add(useBankGpBtn, BorderLayout.EAST);

        headerPanel.add(bankGpRow);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        // Budget Input Box (Clean 2-Line Layout to prevent truncation)
        JPanel budgetContainer = new JPanel(new GridLayout(2, 1, 0, 4));
        budgetContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        budgetContainer.setAlignmentX(LEFT_ALIGNMENT);

        JPanel budgetInputRow = new JPanel(new BorderLayout(6, 0));
        budgetInputRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel budgetPrefixLabel = new JLabel("Budget:");
        budgetPrefixLabel.setFont(FontManager.getRunescapeBoldFont());
        budgetPrefixLabel.setForeground(Color.WHITE);
        budgetInputRow.add(budgetPrefixLabel, BorderLayout.WEST);

        budgetField.setBackground(ColorScheme.DARK_GRAY_COLOR);
        budgetField.setForeground(GOLD_COLOR);
        budgetField.setCaretColor(Color.WHITE);
        budgetField.setFont(FontManager.getRunescapeFont());
        budgetField.setHorizontalAlignment(JTextField.LEFT);
        budgetField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 65), 1),
            new EmptyBorder(3, 6, 3, 6)
        ));
        budgetField.setToolTipText("Enter target budget (e.g. '100k', '500k', '1.5m', '500000')");
        budgetField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyReleased(KeyEvent e)
            {
                userModifiedBudget = true;
                updateBudgetCalculations();
            }
        });
        budgetInputRow.add(budgetField, BorderLayout.CENTER);
        budgetContainer.add(budgetInputRow);

        // Quick Budget Presets on dedicated full-width subrow
        JPanel presetGroup = new JPanel(new GridLayout(1, 4, 3, 0));
        presetGroup.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JButton b50k = createPresetButton("50k", 50_000);
        JButton b100k = createPresetButton("100k", 100_000);
        JButton b1m = createPresetButton("1M", 1_000_000);
        JButton bBank = createPresetButton("Bank", -1);
        presetGroup.add(b50k);
        presetGroup.add(b100k);
        presetGroup.add(b1m);
        presetGroup.add(bBank);
        budgetContainer.add(presetGroup);

        headerPanel.add(budgetContainer);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        // Grand Total & Budget Left summary card
        JPanel statsCard = new JPanel(new GridLayout(2, 1, 0, 2));
        statsCard.setBackground(ColorScheme.DARK_GRAY_COLOR);
        statsCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 65), 1),
            new EmptyBorder(5, 7, 5, 7)
        ));
        statsCard.setAlignmentX(LEFT_ALIGNMENT);

        totalCostLabel.setFont(FontManager.getRunescapeBoldFont());
        totalCostLabel.setForeground(GOLD_COLOR);
        statsCard.add(totalCostLabel);

        budgetLeftLabel.setFont(FontManager.getRunescapeBoldFont());
        budgetLeftLabel.setForeground(BUDGET_GREEN);
        statsCard.add(budgetLeftLabel);

        headerPanel.add(statsCard);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        dynamicDepletionCheckbox.setFont(FontManager.getRunescapeSmallFont());
        dynamicDepletionCheckbox.setForeground(new Color(200, 200, 200));
        dynamicDepletionCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        dynamicDepletionCheckbox.setFocusPainted(false);
        dynamicDepletionCheckbox.setAlignmentX(LEFT_ALIGNMENT);
        dynamicDepletionCheckbox.setToolTipText("Accounts for OSRS shop price inflation as items (like runes) are bought out of stock.");
        dynamicDepletionCheckbox.addActionListener(e -> {
            updateBudgetCalculations();
            rebuildCart();
        });
        headerPanel.add(dynamicDepletionCheckbox);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        itemCountLabel.setFont(FontManager.getRunescapeSmallFont());
        itemCountLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        itemCountLabel.setAlignmentX(LEFT_ALIGNMENT);
        headerPanel.add(itemCountLabel);

        add(headerPanel, BorderLayout.NORTH);

        // 2. Empty State Placeholder Panel
        emptyPanel.setLayout(new BoxLayout(emptyPanel, BoxLayout.Y_AXIS));
        emptyPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        emptyPanel.setBorder(new EmptyBorder(30, 16, 20, 16));

        JLabel emptyTitle = new JLabel("Shopping Cart Empty");
        emptyTitle.setFont(FontManager.getRunescapeBoldFont());
        emptyTitle.setForeground(TITLE_COLOR);
        emptyTitle.setAlignmentX(CENTER_ALIGNMENT);
        emptyPanel.add(emptyTitle);
        emptyPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        JLabel emptyDesc = new JLabel("<html><body style='text-align: center; color: #a0a0a5; font-size: 11px;'>" +
            "Right-click any shop item or click <b>[+Cart]</b> in the Vendor Stock view " +
            "or Global Item Search to plan your shopping list!" +
            "</body></html>");
        emptyDesc.setAlignmentX(CENTER_ALIGNMENT);
        emptyPanel.add(emptyDesc);

        // 3. Items List View Container
        itemsContainer.setLayout(new BoxLayout(itemsContainer, BoxLayout.Y_AXIS));
        itemsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel listWrapper = new ScrollableContentPanel();
        listWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listWrapper.add(itemsContainer, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(listWrapper);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);

        // 4. Register Manager Change Listener
        cartManager.addCartListener(() -> {
            if (SwingUtilities.isEventDispatchThread())
            {
                rebuildCart();
            }
            else
            {
                SwingUtilities.invokeLater(this::rebuildCart);
            }
        });

        // Initial Build
        rebuildCart();
    }

    private JButton createPresetButton(String text, long amount)
    {
        JButton btn = new JButton(text);
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        btn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(0, 1, 0, 1));
        btn.setPreferredSize(new Dimension(28, 20));
        btn.addActionListener(e -> {
            userModifiedBudget = true;
            if (amount >= 0)
            {
                budgetField.setText(NUMBER_FORMAT.format(amount));
            }
            else if (cartManager.getLastKnownBankCoins() >= 0)
            {
                budgetField.setText(NUMBER_FORMAT.format(cartManager.getLastKnownBankCoins()));
            }
            updateBudgetCalculations();
        });
        return btn;
    }

    private void updateBankGpDisplay()
    {
        long bankCoins = cartManager.getLastKnownBankCoins();
        long invCoins = cartManager.getLastKnownInventoryCoins();

        if (bankCoins < 0)
        {
            bankGpLabel.setText("Bank GP: Not Synced");
            bankGpLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            useBankGpBtn.setEnabled(false);
            bankGpLabel.setToolTipText("Open your bank in-game to auto-sync your gold balance.");
        }
        else
        {
            bankGpLabel.setText("Bank GP: " + NUMBER_FORMAT.format(bankCoins) + " gp");
            bankGpLabel.setForeground(GOLD_COLOR);
            useBankGpBtn.setEnabled(true);
            bankGpLabel.setToolTipText("Bank: " + NUMBER_FORMAT.format(bankCoins) + " gp | Inventory: " + NUMBER_FORMAT.format(invCoins) + " gp");

            if (!userModifiedBudget && bankCoins > 0)
            {
                budgetField.setText(NUMBER_FORMAT.format(bankCoins));
            }
        }
    }

    private long parseBudgetAmount(String text)
    {
        if (text == null) return 0;
        String clean = text.trim().toLowerCase().replace(",", "").replace("gp", "").trim();
        if (clean.isEmpty()) return 0;

        try
        {
            if (clean.endsWith("m"))
            {
                double val = Double.parseDouble(clean.substring(0, clean.length() - 1).trim());
                return (long) (val * 1_000_000);
            }
            if (clean.endsWith("k"))
            {
                double val = Double.parseDouble(clean.substring(0, clean.length() - 1).trim());
                return (long) (val * 1_000);
            }
            if (clean.endsWith("b"))
            {
                double val = Double.parseDouble(clean.substring(0, clean.length() - 1).trim());
                return (long) (val * 1_000_000_000);
            }
            return Long.parseLong(clean);
        }
        catch (NumberFormatException ex)
        {
            return 0;
        }
    }

    private void updateBudgetCalculations()
    {
        long budget = parseBudgetAmount(budgetField.getText());
        long flatCost = cartManager.getTotalCostLong();
        long dynamicCost = cartManager.getTotalDynamicCostLong();
        long effectiveCost = dynamicDepletionCheckbox.isSelected() ? dynamicCost : flatCost;
        long diff = budget - effectiveCost;

        if (dynamicDepletionCheckbox.isSelected() && dynamicCost != flatCost)
        {
            totalCostLabel.setText("Total: " + NUMBER_FORMAT.format(dynamicCost) + " gp (" + NUMBER_FORMAT.format(flatCost) + " flat)");
            totalCostLabel.setToolTipText("Dynamic cost includes shop price increase as stock runs out (base cost: " + NUMBER_FORMAT.format(flatCost) + " gp).");
        }
        else
        {
            totalCostLabel.setText("Total: " + NUMBER_FORMAT.format(effectiveCost) + " gp");
            totalCostLabel.setToolTipText(null);
        }

        if (diff >= 0)
        {
            budgetLeftLabel.setText("Budget Left: +" + NUMBER_FORMAT.format(diff) + " gp");
            budgetLeftLabel.setForeground(BUDGET_GREEN);
        }
        else
        {
            budgetLeftLabel.setText("Over Budget: " + NUMBER_FORMAT.format(diff) + " gp");
            budgetLeftLabel.setForeground(BUDGET_RED);
        }
    }

    public void rebuildCart()
    {
        doRebuildCart();
    }

    private void doRebuildCart()
    {
        itemsContainer.removeAll();
        List<CartItem> items = cartManager.getItems();

        updateBankGpDisplay();
        updateBudgetCalculations();

        int totalEntries = items.size();
        int totalQty = cartManager.getTotalQuantity();
        itemCountLabel.setText(totalEntries + " unique item" + (totalEntries == 1 ? "" : "s") +
            " (" + NUMBER_FORMAT.format(totalQty) + " total qty)");

        if (items.isEmpty())
        {
            itemsContainer.add(emptyPanel);
            itemsContainer.revalidate();
            itemsContainer.repaint();
            return;
        }

        for (CartItem cartItem : items)
        {
            JPanel card = buildCartItemCard(cartItem);
            itemsContainer.add(card);
            itemsContainer.add(Box.createRigidArea(new Dimension(0, 4)));
        }

        itemsContainer.revalidate();
        itemsContainer.repaint();
    }

    private JPanel buildCartItemCard(CartItem item)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(55, 55, 60), 1),
            new EmptyBorder(4, 5, 4, 5)
        ));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        // Row 1: Item Icon + Name & Unit Price + Remove (X) Button
        JPanel topRow = new JPanel(new BorderLayout(5, 0));
        topRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        topRow.setAlignmentX(LEFT_ALIGNMENT);

        // 32x32 Icon Container
        JPanel iconContainer = new JPanel(new BorderLayout());
        iconContainer.setOpaque(false);
        iconContainer.setPreferredSize(new Dimension(32, 32));
        iconContainer.setMinimumSize(new Dimension(32, 32));
        iconContainer.setMaximumSize(new Dimension(32, 32));

        JLabel iconLabel = new JLabel();
        iconLabel.setHorizontalAlignment(JLabel.CENTER);
        iconLabel.setVerticalAlignment(JLabel.CENTER);
        if (itemManager != null)
        {
            AsyncBufferedImage img = itemManager.getImage(item.getItemId(), item.getQuantity(), item.getQuantity() > 1);
            if (img != null)
            {
                img.addTo(iconLabel);
            }
        }
        iconContainer.add(iconLabel, BorderLayout.CENTER);
        topRow.add(iconContainer, BorderLayout.WEST);

        // Name & Unit Price
        JPanel titleCol = new JPanel(new GridLayout(2, 1, 0, 1));
        titleCol.setOpaque(false);

        // Name capped to 130px (HTML width) -- topRow's titleCol budget at the default ~225px sidebar
        // is roughly 139px once the 32px icon container, 18px remove ("X") button, and row gaps are
        // subtracted, so 130px leaves a small margin.
        JLabel nameLabel = new JLabel("<html><body style='width: 130px;'>" + item.getItemName() + "</body></html>");
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setToolTipText(item.getItemName());
        titleCol.add(nameLabel);

        JLabel unitPriceLabel = new JLabel("@ " + NUMBER_FORMAT.format(item.getUnitPrice()) + " gp each");
        unitPriceLabel.setFont(FontManager.getRunescapeSmallFont());
        unitPriceLabel.setForeground(GOLD_COLOR);
        titleCol.add(unitPriceLabel);

        topRow.add(titleCol, BorderLayout.CENTER);

        // Remove Button (X)
        JButton removeBtn = new JButton("✕");
        removeBtn.setFont(FontManager.getRunescapeBoldFont());
        removeBtn.setForeground(DELETE_RED);
        removeBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        removeBtn.setBorder(null);
        removeBtn.setFocusPainted(false);
        removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeBtn.setToolTipText("Remove from Cart");
        removeBtn.setPreferredSize(new Dimension(18, 18));
        removeBtn.addActionListener(e -> cartManager.removeItem(item));
        topRow.add(removeBtn, BorderLayout.EAST);

        card.add(topRow);
        card.add(Box.createRigidArea(new Dimension(0, 3)));

        // Row 2: Shop & Town Name + Map Focus Button + Sidebar View Button
        JPanel shopRow = new JPanel(new BorderLayout(3, 0));
        shopRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        shopRow.setAlignmentX(LEFT_ALIGNMENT);

        // Name capped to 90px (HTML width) -- shopRow's remaining budget at the default ~225px sidebar
        // is roughly 96px once the Focus/Stock action buttons and row gaps are subtracted. Combined
        // shop+town strings can run past 60 chars for wiki-scraped names, so this reliably wraps
        // instead of pushing the action buttons out of the viewport.
        String shopText = item.getShopName() + " (" + item.getTownName() + ")";
        JLabel shopLabel = new JLabel("<html><body style='width: 90px;'>" + shopText + "</body></html>");
        shopLabel.setFont(FontManager.getRunescapeSmallFont());
        shopLabel.setForeground(SHOP_CYAN);
        shopLabel.setToolTipText("Sold at: " + item.getShopName() + " in " + item.getTownName());
        shopRow.add(shopLabel, BorderLayout.CENTER);

        JPanel shopActionGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        shopActionGroup.setOpaque(false);

        JButton focusShopBtn = new JButton("Map");
        focusShopBtn.setFont(FontManager.getRunescapeSmallFont());
        focusShopBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        focusShopBtn.setForeground(SHOP_CYAN);
        focusShopBtn.setMargin(new Insets(1, 2, 1, 2));
        focusShopBtn.setFocusPainted(false);
        focusShopBtn.setToolTipText("Show " + item.getShopName() + " on the World Map");
        focusShopBtn.addActionListener(e -> handleFocusShop(item));
        shopActionGroup.add(focusShopBtn);

        JButton inspectShopBtn = new JButton("Stock");
        inspectShopBtn.setFont(FontManager.getRunescapeSmallFont());
        inspectShopBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        inspectShopBtn.setForeground(Color.WHITE);
        inspectShopBtn.setMargin(new Insets(1, 2, 1, 2));
        inspectShopBtn.setFocusPainted(false);
        inspectShopBtn.setToolTipText("View shop inventory in sidebar");
        inspectShopBtn.addActionListener(e -> handleInspectShop(item));
        shopActionGroup.add(inspectShopBtn);

        shopRow.add(shopActionGroup, BorderLayout.EAST);

        card.add(shopRow);
        card.add(Box.createRigidArea(new Dimension(0, 3)));

        // Row 3: Interactive Quantity Controls & Subtotal Cost
        JPanel bottomRow = new JPanel(new BorderLayout(3, 0));
        bottomRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bottomRow.setAlignmentX(LEFT_ALIGNMENT);

        // Quantity Editor Controls: [-10] [-] [ qty field ] [+] [+10]
        JPanel qtyControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 0));
        qtyControls.setOpaque(false);

        JButton minus10Btn = new JButton("-10");
        styleStepButton(minus10Btn, 20);
        minus10Btn.setToolTipText("Subtract 10");
        minus10Btn.addActionListener(e -> cartManager.incrementQuantity(item.getItemId(), item.getShopName(), -10));
        qtyControls.add(minus10Btn);

        JButton minus1Btn = new JButton("-");
        styleStepButton(minus1Btn, 16);
        minus1Btn.setToolTipText("Subtract 1");
        minus1Btn.addActionListener(e -> cartManager.incrementQuantity(item.getItemId(), item.getShopName(), -1));
        qtyControls.add(minus1Btn);

        // Direct editable quantity field
        JTextField qtyField = new JTextField(String.valueOf(item.getQuantity()), 4);
        qtyField.setFont(FontManager.getRunescapeSmallFont());
        qtyField.setBackground(ColorScheme.DARK_GRAY_COLOR);
        qtyField.setForeground(Color.WHITE);
        qtyField.setCaretColor(Color.WHITE);
        qtyField.setHorizontalAlignment(JTextField.CENTER);
        qtyField.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 75), 1));
        qtyField.setPreferredSize(new Dimension(32, 20));
        qtyField.setToolTipText("Click to edit exact quantity (Press Enter to apply)");

        Runnable applyQtyEdit = () -> {
            try
            {
                String text = qtyField.getText().trim().replace(",", "");
                int parsed = Integer.parseInt(text);
                if (parsed != item.getQuantity())
                {
                    cartManager.setQuantity(item.getItemId(), item.getShopName(), parsed);
                }
            }
            catch (NumberFormatException ex)
            {
                qtyField.setText(String.valueOf(item.getQuantity()));
            }
        };

        qtyField.addActionListener(e -> applyQtyEdit.run());
        qtyField.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(FocusEvent e)
            {
                applyQtyEdit.run();
            }
        });
        qtyControls.add(qtyField);

        JButton plus1Btn = new JButton("+");
        styleStepButton(plus1Btn, 16);
        plus1Btn.setToolTipText("Add 1");
        plus1Btn.addActionListener(e -> cartManager.incrementQuantity(item.getItemId(), item.getShopName(), 1));
        qtyControls.add(plus1Btn);

        JButton plus10Btn = new JButton("+10");
        styleStepButton(plus10Btn, 20);
        plus10Btn.setToolTipText("Add 10");
        plus10Btn.addActionListener(e -> cartManager.incrementQuantity(item.getItemId(), item.getShopName(), 10));
        qtyControls.add(plus10Btn);

        bottomRow.add(qtyControls, BorderLayout.WEST);

        // Subtotal Label on the Right (supports flat vs dynamic cost)
        long flatSubtotal = item.getSubtotalLong();
        long dynamicSubtotal = item.getDynamicSubtotalLong();
        JLabel subtotalLabel;
        if (dynamicDepletionCheckbox.isSelected() && dynamicSubtotal != flatSubtotal)
        {
            subtotalLabel = new JLabel(NUMBER_FORMAT.format(dynamicSubtotal) + " gp");
            subtotalLabel.setToolTipText("Estimated cost: " + NUMBER_FORMAT.format(dynamicSubtotal) + " gp (Base flat: " + NUMBER_FORMAT.format(flatSubtotal) + " gp, +" + (dynamicSubtotal - flatSubtotal) + " gp inflation)");
        }
        else
        {
            subtotalLabel = new JLabel(NUMBER_FORMAT.format(flatSubtotal) + " gp");
            subtotalLabel.setToolTipText("Subtotal = " + item.getQuantity() + " × " + NUMBER_FORMAT.format(item.getUnitPrice()) + " gp");
        }
        subtotalLabel.setFont(FontManager.getRunescapeBoldFont());
        subtotalLabel.setForeground(GOLD_COLOR);
        subtotalLabel.setHorizontalAlignment(JLabel.RIGHT);
        bottomRow.add(subtotalLabel, BorderLayout.CENTER);

        card.add(bottomRow);

        // Right-Click Context Menu on Card
        JPopupMenu contextMenu = createCardContextMenu(item);
        card.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (e.isPopupTrigger())
                {
                    contextMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (e.isPopupTrigger())
                {
                    contextMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        return card;
    }

    private void styleStepButton(JButton btn)
    {
        styleStepButton(btn, 24);
    }

    private void styleStepButton(JButton btn, int width)
    {
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(0, 1, 0, 1));
        btn.setPreferredSize(new Dimension(width, 20));
    }

    private JPopupMenu createCardContextMenu(CartItem item)
    {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem setQty = new JMenuItem("Set Exact Quantity...");
        setQty.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(
                this,
                "Enter new quantity for " + item.getItemName() + ":",
                String.valueOf(item.getQuantity())
            );
            if (input != null && !input.trim().isEmpty())
            {
                try
                {
                    int val = Integer.parseInt(input.trim().replace(",", ""));
                    cartManager.setQuantity(item.getItemId(), item.getShopName(), val);
                }
                catch (NumberFormatException nfe)
                {
                    log.debug("Ignoring non-numeric quantity input", nfe);
                }
            }
        });
        menu.add(setQty);

        menu.addSeparator();

        JMenuItem add50 = new JMenuItem("Add +50");
        add50.addActionListener(e -> cartManager.incrementQuantity(item.getItemId(), item.getShopName(), 50));
        menu.add(add50);

        JMenuItem add100 = new JMenuItem("Add +100");
        add100.addActionListener(e -> cartManager.incrementQuantity(item.getItemId(), item.getShopName(), 100));
        menu.add(add100);

        JMenuItem add500 = new JMenuItem("Add +500");
        add500.addActionListener(e -> cartManager.incrementQuantity(item.getItemId(), item.getShopName(), 500));
        menu.add(add500);

        menu.addSeparator();

        JMenuItem focusMap = new JMenuItem("Show on World Map");
        focusMap.addActionListener(e -> handleFocusShop(item));
        menu.add(focusMap);

        JMenuItem inspect = new JMenuItem("View Shop Stock");
        inspect.addActionListener(e -> handleInspectShop(item));
        menu.add(inspect);

        menu.addSeparator();

        JMenuItem remove = new JMenuItem("Remove from Cart");
        remove.addActionListener(e -> cartManager.removeItem(item));
        menu.add(remove);

        return menu;
    }

    private void handleFocusShop(CartItem item)
    {
        if (onFocusShopOnMap == null) return;

        Shop targetShop = findShop(item);
        if (targetShop != null)
        {
            onFocusShopOnMap.accept(targetShop);
        }
    }

    private void handleInspectShop(CartItem item)
    {
        if (onInspectShop == null) return;

        Shop targetShop = findShop(item);
        if (targetShop != null)
        {
            onInspectShop.accept(targetShop);
        }
    }

    private Shop findShop(CartItem item)
    {
        if (shopDatabase == null) return null;

        for (Shop shop : shopDatabase.getAllShops())
        {
            if (shop.getName().equalsIgnoreCase(item.getShopName()) &&
                (item.getTownName() == null || shop.getTown().equalsIgnoreCase(item.getTownName())))
            {
                return shop;
            }
        }

        for (Shop shop : shopDatabase.getAllShops())
        {
            if (shop.getName().equalsIgnoreCase(item.getShopName()))
            {
                return shop;
            }
        }

        // Fallback shop object if not found in cache
        if (item.getWorldPoint() != null)
        {
            return Shop.builder()
                .name(item.getShopName())
                .town(item.getTownName() != null ? item.getTownName() : "Gielinor")
                .npcName(item.getShopName())
                .worldX(item.getWorldPoint().getX())
                .worldY(item.getWorldPoint().getY())
                .worldPlane(item.getWorldPoint().getPlane())
                .build();
        }

        return null;
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
