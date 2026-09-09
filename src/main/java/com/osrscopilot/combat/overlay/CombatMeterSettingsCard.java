package com.osrscopilot.combat.overlay;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.combat.CombatMeterColors;
import com.osrscopilot.combat.engine.CombatEncounterManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;

import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.FontManager;

@Singleton
public class CombatMeterSettingsCard
{
    public static final int CARD_WIDTH = 270;
    public static final int CARD_HEIGHT = 586;

    private final OsrsCopilotConfig config;
    private final ConfigManager configManager;
    private final CombatEncounterManager encounterManager;
    private final CombatStateBannerOverlay bannerOverlay;
    private final Map<String, Rectangle> hitboxes = new HashMap<>();

    private Runnable onClose;

    // Scroll state. The card can be taller than the canvas; the body scrolls under a fixed header.
    private int viewportH = CARD_HEIGHT;   // set by the overlay from the live canvas height
    private int scrollY = 0;
    private int contentHeight = 0;
    private int lastVisibleBodyH = CARD_HEIGHT - 32;
    private Rectangle visibleBodyScreen = null;

    public void setViewportHeight(int h)
    {
        this.viewportH = Math.max(200, h);
    }

    public synchronized void scrollBy(int dy)
    {
        setScrollY(scrollY + dy);
    }

    public synchronized void setScrollY(int v)
    {
        scrollY = Math.max(0, Math.min(v, Math.max(0, contentHeight - lastVisibleBodyH)));
    }

    public synchronized int getScrollY()
    {
        return scrollY;
    }

    /** Ratio to convert a thumb-drag delta (px on screen) into a content-scroll delta. */
    public synchronized double thumbDragRatio()
    {
        return lastVisibleBodyH > 0 ? (double) contentHeight / lastVisibleBodyH : 1.0;
    }

    public synchronized boolean isOnScrollThumb(Point p)
    {
        Rectangle t = hitboxes.get("SCROLL_THUMB");
        return t != null && p != null && t.contains(p);
    }

    @Inject
    public CombatMeterSettingsCard(
        OsrsCopilotConfig config,
        ConfigManager configManager,
        CombatEncounterManager encounterManager,
        CombatStateBannerOverlay bannerOverlay)
    {
        this.config = config;
        this.configManager = configManager;
        this.encounterManager = encounterManager;
        this.bannerOverlay = bannerOverlay;
    }

    public void setOnClose(Runnable onClose)
    {
        this.onClose = onClose;
    }

    public synchronized void render(Graphics2D g, Point anchorTopLeft, Point mousePos, CombatMeterOverlay.MetricMode currentMode)
    {
        render(g, anchorTopLeft, anchorTopLeft, mousePos, currentMode);
    }

    public synchronized void render(Graphics2D g, Point localPos, Point screenPos, Point mousePos, CombatMeterOverlay.MetricMode currentMode)
    {
        hitboxes.clear();
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (localPos == null) localPos = screenPos != null ? screenPos : new Point(0, 0);
        if (screenPos == null) screenPos = localPos;

        int x = localPos.x;
        int y = localPos.y;
        int deltaX = screenPos.x - localPos.x;
        int deltaY = screenPos.y - localPos.y;

        int cardH = Math.min(CARD_HEIGHT, viewportH);
        int bodyTop = 30;
        int visibleBodyH = Math.max(120, cardH - bodyTop - 4);
        lastVisibleBodyH = visibleBodyH;
        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, contentHeight - visibleBodyH)));

        // 1. Draw Card Background & Double-Border
        g2d.setColor(CombatMeterColors.POPUP_BG);
        g2d.fillRoundRect(x, y, CARD_WIDTH, cardH, 8, 8);

        g2d.setColor(CombatMeterColors.HUD_BORDER);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(x, y, CARD_WIDTH, cardH, 8, 8);

        g2d.setColor(new Color(255, 255, 255, 18));
        g2d.drawRoundRect(x + 2, y + 2, CARD_WIDTH - 4, cardH - 4, 6, 6);

        // 2. Header Bar
        g2d.setColor(CombatMeterColors.FOOTER_BG);
        g2d.fillRoundRect(x + 3, y + 3, CARD_WIDTH - 6, 24, 6, 6);
        g2d.setFont(FontManager.getRunescapeBoldFont());
        g2d.setColor(CombatMeterColors.TEXT_ACCENT_GOLD);
        HudGlyphs.settings(g2d, x + 8, y + 6, 10, CombatMeterColors.FOOTER_BG);
        g2d.drawString("Combat Meter Settings", x + 22, y + 18);

        // Close Button (drawn cross - the RS bitmap font has no glyph for it)
        Rectangle closeBtn = new Rectangle(x + deltaX + CARD_WIDTH - 22, y + deltaY + 5, 16, 16);
        hitboxes.put("CLOSE", closeBtn);
        boolean closeHover = mousePos != null && closeBtn.contains(mousePos);
        g2d.setColor(closeHover ? CombatMeterColors.SWATCH_DRAGON_RED : CombatMeterColors.TEXT_MUTED);
        HudGlyphs.cross(g2d, x + CARD_WIDTH - 19, y + 8, 9);

        // Body: clipped to the card under the fixed header, scrolled by scrollY.
        java.awt.Shape prevClip = g2d.getClip();
        g2d.clipRect(x, y + bodyTop, CARD_WIDTH - 1, visibleBodyH);
        int bodyOriginY = y + bodyTop + 2 - scrollY;
        int curY = bodyOriginY;

        // 3. Section: Display Mode Selector
        curY = drawSectionTitle(g2d, "DISPLAY MODE", x + 8, curY);
        curY = drawModePills(g2d, x + 8, curY, deltaX, deltaY, mousePos, currentMode);

        // 4. Section: Meter Dimensions - steppers, so any value in range is reachable and the
        //    meter always resizes itself to fit (no drag-resize).
        curY = drawSectionTitle(g2d, "SIZE", x + 8, curY);
        curY = drawStepper(g2d, "WIDTH", "Width", config.combatOverlayWidth(), "px", x + 8, curY, deltaX, deltaY, mousePos);
        curY = drawStepper(g2d, "BARHEIGHT", "Bar height", config.combatBarHeight(), "px", x + 8, curY, deltaX, deltaY, mousePos);
        curY = drawStepper(g2d, "OPACITY", "Opacity", config.combatOverlayOpacity(), "%", x + 8, curY, deltaX, deltaY, mousePos);
        curY = drawBarCountSelector(g2d, x + 8, curY, deltaX, deltaY, mousePos);

        // 5. Section: Bar Style & Accents
        curY = drawSectionTitle(g2d, "BAR STYLE & ACCENT", x + 8, curY);
        curY = drawStyleAndPalette(g2d, x + 8, curY, deltaX, deltaY, mousePos);
        curY = drawBarTexturePills(g2d, x + 8, curY, deltaX, deltaY, mousePos);
        curY = drawStepper(g2d, "CORNER", "Corner radius", config.combatBarCorner(), "px", x + 8, curY, deltaX, deltaY, mousePos);
        curY = drawStepper(g2d, "BARGAP", "Bar spacing", config.combatBarGap(), "px", x + 8, curY, deltaX, deltaY, mousePos);
        curY = drawCheckbox(g2d, "TOGGLE_RANK", "Show bar rank number", config.combatBarShowRank(), x + 8, curY, deltaX, deltaY, mousePos);
        curY = drawCheckbox(g2d, "TOGGLE_TEXTSHADOW", "Bar text shadow", config.combatBarTextShadow(), x + 8, curY, deltaX, deltaY, mousePos);
        curY = drawCheckbox(g2d, "TOGGLE_STYLEDOT", "Combat-style dot on bars", config.combatBarShowStyleIcon(), x + 8, curY, deltaX, deltaY, mousePos);
        curY = drawSectionTitle(g2d, "BAR LABEL FONT", x + 8, curY);
        curY = drawBarFontPills(g2d, x + 8, curY, deltaX, deltaY, mousePos);

        // 5c. Section: HUD header layout
        curY = drawSectionTitle(g2d, "HUD HEADER", x + 8, curY);
        curY = drawHeaderModePills(g2d, x + 8, curY, deltaX, deltaY, mousePos);

        // 5b. Section: Fight Graph
        curY = drawSectionTitle(g2d, "FIGHT GRAPH", x + 8, curY);
        curY = drawStepper(g2d, "GRAPHSMOOTH", "Smoothing", config.combatGraphSmoothing(), "s", x + 8, curY, deltaX, deltaY, mousePos);
        curY = drawGraphStylePills(g2d, x + 8, curY, deltaX, deltaY, mousePos);

        // 6. Section: Toggles
        curY = drawSectionTitle(g2d, "AUTOMATION & FOOTER", x + 8, curY);
        curY = drawCheckbox(g2d, "TOGGLE_AUTOHIDE", "Dim HUD when idle", config.combatAutoHide(), x + 8, curY, deltaX, deltaY, mousePos);
        curY = drawCheckbox(g2d, "TOGGLE_FOOTER", "Show summary mini-footer", config.combatShowMiniFooter(), x + 8, curY, deltaX, deltaY, mousePos);

        // 6b. Section: Combat State Banner (quick access - full options in the config panel)
        curY = drawSectionTitle(g2d, "COMBAT STATE BANNER", x + 8, curY);
        curY = drawCheckbox(g2d, "BANNER_ENABLE", "Enable 'Entering / Left Combat'", config.combatBannerEnabled(), x + 8, curY, deltaX, deltaY, mousePos);
        curY = drawCheckbox(g2d, "BANNER_EDIT", "Reposition banner (show sample)", bannerOverlay != null && bannerOverlay.isEditMode(), x + 8, curY, deltaX, deltaY, mousePos);
        drawActionButton(g2d, "BANNER_PREVIEW", "Preview banner", x + 8, curY, CARD_WIDTH - 24, 18, deltaX, deltaY, mousePos, new Color(255, 138, 101));
        curY += 22;

        // 7. Action Buttons: Reset & Copy Report
        curY += 2;
        drawActionButton(g2d, "ACTION_RESET_ENCOUNTER", "Reset Live", x + 8, curY, 80, 20, deltaX, deltaY, mousePos, new Color(239, 83, 80));
        drawActionButton(g2d, "ACTION_RESET_SESSION", "Reset Session", x + 92, curY, 78, 20, deltaX, deltaY, mousePos, new Color(255, 183, 77));
        drawActionButton(g2d, "ACTION_COPY_CHAT", "Copy summary", x + 174, curY, 88, 20, deltaX, deltaY, mousePos, new Color(100, 181, 246));
        curY += 24;

        // 8. Copy the one-line summary with a chat-channel prefix (clipboard only - you paste + send).
        curY = drawSectionTitle(g2d, "COPY SUMMARY FOR A CHAT CHANNEL", x + 8, curY);
        curY += 2;
        drawActionButton(g2d, "SHARE_PUBLIC", "Public", x + 8, curY, 78, 20, deltaX, deltaY, mousePos, new Color(120, 144, 156));
        drawActionButton(g2d, "SHARE_FRIENDS", "Friends", x + 92, curY, 78, 20, deltaX, deltaY, mousePos, new Color(120, 144, 156));
        drawActionButton(g2d, "SHARE_CLAN", "Clan", x + 174, curY, 88, 20, deltaX, deltaY, mousePos, new Color(120, 144, 156));
        curY += 24;

        contentHeight = curY - bodyOriginY;
        g2d.setClip(prevClip);

        // Scrollbar (only when the body overflows the visible band).
        if (contentHeight > visibleBodyH)
        {
            int trackX = x + CARD_WIDTH - 8;
            g2d.setColor(new Color(0, 0, 0, 90));
            g2d.fillRoundRect(trackX, y + bodyTop, 5, visibleBodyH, 3, 3);
            int span = contentHeight - visibleBodyH;
            int thumbH = Math.max(20, Math.round((float) visibleBodyH * visibleBodyH / contentHeight));
            int thumbY = y + bodyTop + Math.round((float) (visibleBodyH - thumbH) * scrollY / span);
            g2d.setColor(new Color(200, 200, 210, 200));
            g2d.fillRoundRect(trackX, thumbY, 5, thumbH, 3, 3);
            hitboxes.put("SCROLL_THUMB", new Rectangle(trackX - 3 + deltaX, thumbY + deltaY, 11, thumbH));
            hitboxes.put("SCROLL_TRACK", new Rectangle(trackX - 3 + deltaX, y + bodyTop + deltaY, 11, visibleBodyH));
        }

        // Only rows currently inside the visible band stay clickable (plus CLOSE / scrollbar).
        visibleBodyScreen = new Rectangle(x + deltaX, y + bodyTop + deltaY, CARD_WIDTH, visibleBodyH);
        hitboxes.entrySet().removeIf(e -> !"CLOSE".equals(e.getKey())
            && !e.getKey().startsWith("SCROLL")
            && !visibleBodyScreen.intersects(e.getValue()));

        g2d.dispose();
    }

    private int drawSectionTitle(Graphics2D g, String title, int x, int y)
    {
        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(CombatMeterColors.TEXT_MUTED);
        g.drawString(title, x, y + 9);
        return y + 12;
    }

    private int drawModePills(Graphics2D g, int x, int y, int deltaX, int deltaY, Point mousePos, CombatMeterOverlay.MetricMode currentMode)
    {
        // Labels come straight from the enum so the card, the HUD pill and the mode dropdown
        // never disagree.
        CombatMeterOverlay.MetricMode[] enumVals = CombatMeterOverlay.MetricMode.values();
        int btnW = 58;
        int btnH = 17;

        for (int i = 0; i < enumVals.length; i++)
        {
            String label = enumVals[i].getTitle();
            int bx = x + (i * 64);
            Rectangle rect = new Rectangle(bx + deltaX, y + deltaY, btnW, btnH);
            String key = "MODE_" + enumVals[i].name();
            hitboxes.put(key, rect);

            boolean isActive = currentMode == enumVals[i];
            boolean isHover = mousePos != null && rect.contains(mousePos);

            g.setColor(isActive ? CombatMeterColors.BTN_BG_ACTIVE : isHover ? CombatMeterColors.BTN_BG_HOVER : CombatMeterColors.BTN_BG_IDLE);
            g.fillRoundRect(bx, y, btnW, btnH, 3, 3);
            g.setColor(CombatMeterColors.HUD_BORDER);
            g.drawRoundRect(bx, y, btnW - 1, btnH - 1, 3, 3);

            g.setFont(FontManager.getRunescapeSmallFont());
            g.setColor(isActive ? CombatMeterColors.BTN_TEXT_ACTIVE : CombatMeterColors.TEXT_PRIMARY);
            FontMetrics fm = g.getFontMetrics();
            int tx = bx + (btnW - fm.stringWidth(label)) / 2;
            g.drawString(label, tx, y + 12);
        }
        return y + btnH + 6;
    }

    /** [-] Label: value unit [+] row. Registers {key}_DEC and {key}_INC hitboxes. */
    private int drawStepper(Graphics2D g, String key, String label, int value, String unit,
                            int x, int y, int deltaX, int deltaY, Point mousePos)
    {
        int btnH = 16;
        int stepW = 18;
        int rowW = CARD_WIDTH - 24;

        Rectangle dec = new Rectangle(x + deltaX, y + deltaY, stepW, btnH);
        Rectangle inc = new Rectangle(x + rowW - stepW + deltaX, y + deltaY, stepW, btnH);
        hitboxes.put(key + "_DEC", dec);
        hitboxes.put(key + "_INC", inc);

        boolean decHover = mousePos != null && dec.contains(mousePos);
        boolean incHover = mousePos != null && inc.contains(mousePos);

        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();

        // - button
        g.setColor(decHover ? CombatMeterColors.BTN_BG_HOVER : CombatMeterColors.BTN_BG_IDLE);
        g.fillRoundRect(x, y, stepW, btnH, 3, 3);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(x, y, stepW - 1, btnH - 1, 3, 3);
        g.setColor(CombatMeterColors.TEXT_PRIMARY);
        g.drawString("-", x + stepW / 2 - fm.stringWidth("-") / 2, y + 12);

        // + button
        g.setColor(incHover ? CombatMeterColors.BTN_BG_HOVER : CombatMeterColors.BTN_BG_IDLE);
        g.fillRoundRect(x + rowW - stepW, y, stepW, btnH, 3, 3);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(x + rowW - stepW, y, stepW - 1, btnH - 1, 3, 3);
        g.setColor(CombatMeterColors.TEXT_PRIMARY);
        g.drawString("+", x + rowW - stepW / 2 - fm.stringWidth("+") / 2, y + 12);

        // label + value between them
        g.setColor(CombatMeterColors.TEXT_SECONDARY);
        g.drawString(label, x + stepW + 6, y + 12);
        String vs = value + " " + unit;
        g.setColor(CombatMeterColors.TEXT_ACCENT_GOLD);
        g.drawString(vs, x + rowW - stepW - 6 - fm.stringWidth(vs), y + 12);

        return y + btnH + 6;
    }

    private int drawBarCountSelector(Graphics2D g, int x, int y, int deltaX, int deltaY, Point mousePos)
    {
        // 1-10, matching @Range(min = 1, max = 10) on maxCombatBars.
        int[] counts = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int step = 24;
        int btnW = 22;
        int btnH = 16;

        for (int i = 0; i < counts.length; i++)
        {
            int bx = x + (i * step);
            Rectangle rect = new Rectangle(bx + deltaX, y + deltaY, btnW, btnH);
            String key = "BARCOUNT_" + counts[i];
            hitboxes.put(key, rect);

            boolean isActive = config.maxCombatBars() == counts[i];
            boolean isHover = mousePos != null && rect.contains(mousePos);

            g.setColor(isActive ? CombatMeterColors.BTN_BG_ACTIVE : isHover ? CombatMeterColors.BTN_BG_HOVER : CombatMeterColors.BTN_BG_IDLE);
            g.fillRoundRect(bx, y, btnW, btnH, 3, 3);
            g.setColor(CombatMeterColors.HUD_BORDER);
            g.drawRoundRect(bx, y, btnW - 1, btnH - 1, 3, 3);

            g.setFont(FontManager.getRunescapeSmallFont());
            g.setColor(isActive ? CombatMeterColors.BTN_TEXT_ACTIVE : CombatMeterColors.TEXT_PRIMARY);
            FontMetrics fm = g.getFontMetrics();
            String txt = String.valueOf(counts[i]);
            int tx = bx + (btnW - fm.stringWidth(txt)) / 2;
            g.drawString(txt, tx, y + 12);
        }
        return y + btnH + 6;
    }

    private int drawBarTexturePills(Graphics2D g, int x, int y, int deltaX, int deltaY, Point mousePos)
    {
        OsrsCopilotConfig.BarTextureOption[] opts = OsrsCopilotConfig.BarTextureOption.values();
        String[] labels = {"Flat", "Gloss", "Striped"};
        int btnW = 58;
        int btnH = 16;
        for (int i = 0; i < opts.length && i < labels.length; i++)
        {
            int bx = x + (i * 64);
            Rectangle rect = new Rectangle(bx + deltaX, y + deltaY, btnW, btnH);
            hitboxes.put("BARTEX_" + opts[i].name(), rect);
            boolean isActive = config.combatBarTexture() == opts[i];
            boolean isHover = mousePos != null && rect.contains(mousePos);
            g.setColor(isActive ? CombatMeterColors.BTN_BG_ACTIVE : isHover ? CombatMeterColors.BTN_BG_HOVER : CombatMeterColors.BTN_BG_IDLE);
            g.fillRoundRect(bx, y, btnW, btnH, 3, 3);
            g.setColor(CombatMeterColors.HUD_BORDER);
            g.drawRoundRect(bx, y, btnW - 1, btnH - 1, 3, 3);
            g.setFont(FontManager.getRunescapeSmallFont());
            g.setColor(isActive ? CombatMeterColors.BTN_TEXT_ACTIVE : CombatMeterColors.TEXT_PRIMARY);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(labels[i], bx + (btnW - fm.stringWidth(labels[i])) / 2, y + 12);
        }
        return y + btnH + 6;
    }

    private int drawBarFontPills(Graphics2D g, int x, int y, int deltaX, int deltaY, Point mousePos)
    {
        OsrsCopilotConfig.BarFontOption[] opts = OsrsCopilotConfig.BarFontOption.values();
        String[] labels = {"Small", "Regular", "Bold"};
        int btnW = 58;
        int btnH = 16;
        for (int i = 0; i < opts.length && i < labels.length; i++)
        {
            int bx = x + (i * 64);
            Rectangle rect = new Rectangle(bx + deltaX, y + deltaY, btnW, btnH);
            hitboxes.put("BARFONT_" + opts[i].name(), rect);
            boolean isActive = config.combatBarFont() == opts[i];
            boolean isHover = mousePos != null && rect.contains(mousePos);
            g.setColor(isActive ? CombatMeterColors.BTN_BG_ACTIVE : isHover ? CombatMeterColors.BTN_BG_HOVER : CombatMeterColors.BTN_BG_IDLE);
            g.fillRoundRect(bx, y, btnW, btnH, 3, 3);
            g.setColor(CombatMeterColors.HUD_BORDER);
            g.drawRoundRect(bx, y, btnW - 1, btnH - 1, 3, 3);
            g.setFont(FontManager.getRunescapeSmallFont());
            g.setColor(isActive ? CombatMeterColors.BTN_TEXT_ACTIVE : CombatMeterColors.TEXT_PRIMARY);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(labels[i], bx + (btnW - fm.stringWidth(labels[i])) / 2, y + 12);
        }
        return y + btnH + 6;
    }

    private int drawGraphStylePills(Graphics2D g, int x, int y, int deltaX, int deltaY, Point mousePos)
    {
        OsrsCopilotConfig.GraphStyleOption[] opts = OsrsCopilotConfig.GraphStyleOption.values();
        String[] labels = {"Line", "Area", "Bars"};
        int btnW = 58;
        int btnH = 17;
        for (int i = 0; i < opts.length && i < labels.length; i++)
        {
            int bx = x + (i * 64);
            Rectangle rect = new Rectangle(bx + deltaX, y + deltaY, btnW, btnH);
            hitboxes.put("GRAPHSTYLE_" + opts[i].name(), rect);

            boolean isActive = config.combatGraphStyle() == opts[i];
            boolean isHover = mousePos != null && rect.contains(mousePos);
            g.setColor(isActive ? CombatMeterColors.BTN_BG_ACTIVE : isHover ? CombatMeterColors.BTN_BG_HOVER : CombatMeterColors.BTN_BG_IDLE);
            g.fillRoundRect(bx, y, btnW, btnH, 3, 3);
            g.setColor(CombatMeterColors.HUD_BORDER);
            g.drawRoundRect(bx, y, btnW - 1, btnH - 1, 3, 3);
            g.setFont(FontManager.getRunescapeSmallFont());
            g.setColor(isActive ? CombatMeterColors.BTN_TEXT_ACTIVE : CombatMeterColors.TEXT_PRIMARY);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(labels[i], bx + (btnW - fm.stringWidth(labels[i])) / 2, y + 12);
        }
        return y + btnH + 6;
    }

    private int drawHeaderModePills(Graphics2D g, int x, int y, int deltaX, int deltaY, Point mousePos)
    {
        OsrsCopilotConfig.HeaderModeOption[] opts = OsrsCopilotConfig.HeaderModeOption.values();
        String[] labels = {"Full", "Minimal", "Hidden"};
        int btnW = 58;
        int btnH = 17;
        for (int i = 0; i < opts.length && i < labels.length; i++)
        {
            int bx = x + (i * 64);
            Rectangle rect = new Rectangle(bx + deltaX, y + deltaY, btnW, btnH);
            hitboxes.put("HEADERMODE_" + opts[i].name(), rect);
            boolean isActive = config.combatHeaderMode() == opts[i];
            boolean isHover = mousePos != null && rect.contains(mousePos);
            g.setColor(isActive ? CombatMeterColors.BTN_BG_ACTIVE : isHover ? CombatMeterColors.BTN_BG_HOVER : CombatMeterColors.BTN_BG_IDLE);
            g.fillRoundRect(bx, y, btnW, btnH, 3, 3);
            g.setColor(CombatMeterColors.HUD_BORDER);
            g.drawRoundRect(bx, y, btnW - 1, btnH - 1, 3, 3);
            g.setFont(FontManager.getRunescapeSmallFont());
            g.setColor(isActive ? CombatMeterColors.BTN_TEXT_ACTIVE : CombatMeterColors.TEXT_PRIMARY);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(labels[i], bx + (btnW - fm.stringWidth(labels[i])) / 2, y + 12);
        }
        return y + btnH + 6;
    }

    private int drawStyleAndPalette(Graphics2D g, int x, int y, int deltaX, int deltaY, Point mousePos)
    {
        String[] styles = {"Gradient", "Solid", "Class Style"};
        OsrsCopilotConfig.BarStyleOption[] styleEnums = OsrsCopilotConfig.BarStyleOption.values();
        int btnW = 81;
        int btnH = 16;

        for (int i = 0; i < styles.length && i < styleEnums.length; i++)
        {
            int bx = x + (i * 85);
            Rectangle rect = new Rectangle(bx + deltaX, y + deltaY, btnW, btnH);
            String key = "STYLE_" + styleEnums[i].name();
            hitboxes.put(key, rect);

            boolean isActive = config.combatBarStyle() == styleEnums[i];
            boolean isHover = mousePos != null && rect.contains(mousePos);

            g.setColor(isActive ? CombatMeterColors.BTN_BG_ACTIVE : isHover ? CombatMeterColors.BTN_BG_HOVER : CombatMeterColors.BTN_BG_IDLE);
            g.fillRoundRect(bx, y, btnW, btnH, 3, 3);
            g.setColor(CombatMeterColors.HUD_BORDER);
            g.drawRoundRect(bx, y, btnW - 1, btnH - 1, 3, 3);

            g.setFont(FontManager.getRunescapeSmallFont());
            g.setColor(isActive ? CombatMeterColors.BTN_TEXT_ACTIVE : CombatMeterColors.TEXT_PRIMARY);
            FontMetrics fm = g.getFontMetrics();
            int tx = bx + (btnW - fm.stringWidth(styles[i])) / 2;
            g.drawString(styles[i], tx, y + 12);
        }

        // Swatches
        int swatchY = y + btnH + 5;
        OsrsCopilotConfig.PaletteAccentOption[] swatchOptions = OsrsCopilotConfig.PaletteAccentOption.values();
        int sSize = 14;

        for (int i = 0; i < swatchOptions.length; i++)
        {
            int sx = x + (i * 24);
            Rectangle sRect = new Rectangle(sx + deltaX, swatchY + deltaY, sSize, sSize);
            hitboxes.put("SWATCH_" + swatchOptions[i].name(), sRect);

            if (swatchOptions[i] == OsrsCopilotConfig.PaletteAccentOption.DYNAMIC_STYLE)
            {
                // Multi-color dynamic style swatch (Orange -> Green -> Blue)
                g.setColor(CombatMeterColors.MELEE_PRIMARY);
                g.fillRect(sx, swatchY, sSize / 3, sSize);
                g.setColor(CombatMeterColors.RANGED_PRIMARY);
                g.fillRect(sx + (sSize / 3), swatchY, sSize / 3, sSize);
                g.setColor(CombatMeterColors.MAGIC_PRIMARY);
                g.fillRect(sx + (2 * sSize / 3), swatchY, sSize - (2 * sSize / 3), sSize);
            }
            else if (swatchOptions[i] == OsrsCopilotConfig.PaletteAccentOption.CUSTOM)
            {
                Color cc = config.combatBarCustomColor();
                g.setColor(cc != null ? cc : CombatMeterColors.SWATCH_TOA_GOLD);
                g.fillRoundRect(sx, swatchY, sSize, sSize, 3, 3);
                // corner tick so it reads as "custom / editable in config"
                g.setColor(Color.WHITE);
                g.drawLine(sx + sSize - 4, swatchY + 2, sSize + sx - 2, swatchY + 4);
            }
            else
            {
                Color swColor = CombatMeterColors.getPalettePrimary(swatchOptions[i]);
                g.setColor(swColor != null ? swColor : CombatMeterColors.SWATCH_TOA_GOLD);
                g.fillRoundRect(sx, swatchY, sSize, sSize, 3, 3);
            }

            g.setColor(CombatMeterColors.HUD_BORDER);
            g.drawRoundRect(sx, swatchY, sSize, sSize, 3, 3);

            boolean isCurrent = config.combatPaletteAccent() == swatchOptions[i]
                || (swatchOptions[i] == OsrsCopilotConfig.PaletteAccentOption.DYNAMIC_STYLE && config.combatBarStyle() == OsrsCopilotConfig.BarStyleOption.CLASS_STYLE);

            if (isCurrent)
            {
                g.setColor(Color.WHITE);
                g.drawRoundRect(sx - 1, swatchY - 1, sSize + 2, sSize + 2, 4, 4);
            }
        }

        return swatchY + sSize + 6;
    }

    private int drawCheckbox(Graphics2D g, String key, String label, boolean checked, int x, int y, int deltaX, int deltaY, Point mousePos)
    {
        Rectangle hit = new Rectangle(x + deltaX, y + deltaY, 250, 15);
        hitboxes.put(key, hit);

        boolean isHover = mousePos != null && hit.contains(mousePos);
        int boxSize = 11;

        g.setColor(isHover ? CombatMeterColors.BTN_BG_HOVER : CombatMeterColors.BTN_BG_IDLE);
        g.fillRect(x, y + 1, boxSize, boxSize);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRect(x, y + 1, boxSize, boxSize);

        if (checked)
        {
            g.setColor(CombatMeterColors.TEXT_ACCENT_GOLD);
            HudGlyphs.check(g, x + 1, y + 2, 9);
        }

        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(isHover ? CombatMeterColors.TEXT_PRIMARY : CombatMeterColors.TEXT_SECONDARY);
        g.drawString(label, x + 16, y + 10);

        return y + 16;
    }

    private void drawActionButton(Graphics2D g, String key, String label, int x, int y, int w, int h, int deltaX, int deltaY, Point mousePos, Color accent)
    {
        Rectangle rect = new Rectangle(x + deltaX, y + deltaY, w, h);
        hitboxes.put(key, rect);

        boolean isHover = mousePos != null && rect.contains(mousePos);
        g.setColor(isHover ? CombatMeterColors.BTN_BG_HOVER : CombatMeterColors.BTN_BG_IDLE);
        g.fillRoundRect(x, y, w, h, 3, 3);

        g.setColor(isHover ? accent : CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(x, y, w - 1, h - 1, 3, 3);

        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(isHover ? accent : CombatMeterColors.TEXT_PRIMARY);
        FontMetrics fm = g.getFontMetrics();
        int tx = x + (w - fm.stringWidth(label)) / 2;
        g.drawString(label, tx, y + 14);
    }


    // synchronized on the same monitor as render() - render() rebuilds `hitboxes` on the client
    // thread, this runs on the AWT thread. Copy the matched key out before releasing the lock.
    public boolean handleClick(Point p, CombatMeterOverlay overlay)
    {
        String hit = null;
        synchronized (this)
        {
            for (Map.Entry<String, Rectangle> entry : hitboxes.entrySet())
            {
                if (entry.getValue().contains(p))
                {
                    hit = entry.getKey();
                    break;
                }
            }
        }
        if (hit == null)
        {
            return false;
        }
        if ("SCROLL_THUMB".equals(hit))
        {
            return true;   // drag is handled by the overlay's mouseDragged
        }
        if ("SCROLL_TRACK".equals(hit))
        {
            Rectangle track = hitboxes.get("SCROLL_TRACK");
            if (track != null && lastVisibleBodyH > 0)
            {
                double frac = (p.y - track.y) / (double) track.height;
                setScrollY((int) Math.round(frac * Math.max(0, contentHeight - lastVisibleBodyH)));
            }
            return true;
        }
        handleAction(hit, overlay);
        return true;
    }

    private void handleAction(String actionKey, CombatMeterOverlay overlay)
    {
        if ("CLOSE".equals(actionKey))
        {
            if (onClose != null) onClose.run();
        }
        else if (actionKey.startsWith("MODE_"))
        {
            CombatMeterOverlay.MetricMode mode = CombatMeterOverlay.MetricMode.valueOf(actionKey.substring(5));
            if (overlay != null) overlay.setMode(mode);
        }
        else if ("WIDTH_DEC".equals(actionKey) || "WIDTH_INC".equals(actionKey))
        {
            int w = clamp(config.combatOverlayWidth() + (actionKey.endsWith("INC") ? 10 : -10), 160, 420);
            configManager.setConfiguration("osrscopilot", "combatOverlayWidth", w);
        }
        else if ("BARHEIGHT_DEC".equals(actionKey) || "BARHEIGHT_INC".equals(actionKey))
        {
            int h = clamp(config.combatBarHeight() + (actionKey.endsWith("INC") ? 2 : -2), 10, 44);
            configManager.setConfiguration("osrscopilot", "combatBarHeight", h);
        }
        else if ("CORNER_DEC".equals(actionKey) || "CORNER_INC".equals(actionKey))
        {
            int c = clamp(config.combatBarCorner() + (actionKey.endsWith("INC") ? 1 : -1), 0, 8);
            configManager.setConfiguration("osrscopilot", "combatBarCorner", c);
        }
        else if ("BARGAP_DEC".equals(actionKey) || "BARGAP_INC".equals(actionKey))
        {
            int gg = clamp(config.combatBarGap() + (actionKey.endsWith("INC") ? 1 : -1), 0, 6);
            configManager.setConfiguration("osrscopilot", "combatBarGap", gg);
        }
        else if ("TOGGLE_TEXTSHADOW".equals(actionKey))
        {
            configManager.setConfiguration("osrscopilot", "combatBarTextShadow", !config.combatBarTextShadow());
        }
        else if ("TOGGLE_STYLEDOT".equals(actionKey))
        {
            configManager.setConfiguration("osrscopilot", "combatBarShowStyleIcon", !config.combatBarShowStyleIcon());
        }
        else if (actionKey.startsWith("HEADERMODE_"))
        {
            configManager.setConfiguration("osrscopilot", "combatHeaderMode",
                OsrsCopilotConfig.HeaderModeOption.valueOf(actionKey.substring("HEADERMODE_".length())));
        }
        else if ("OPACITY_DEC".equals(actionKey) || "OPACITY_INC".equals(actionKey))
        {
            int o = clamp(config.combatOverlayOpacity() + (actionKey.endsWith("INC") ? 10 : -10), 20, 100);
            configManager.setConfiguration("osrscopilot", "combatOverlayOpacity", o);
        }
        else if ("GRAPHSMOOTH_DEC".equals(actionKey) || "GRAPHSMOOTH_INC".equals(actionKey))
        {
            int s = clamp(config.combatGraphSmoothing() + (actionKey.endsWith("INC") ? 1 : -1), 1, 30);
            configManager.setConfiguration("osrscopilot", "combatGraphSmoothing", s);
        }
        else if (actionKey.startsWith("GRAPHSTYLE_"))
        {
            configManager.setConfiguration("osrscopilot", "combatGraphStyle",
                OsrsCopilotConfig.GraphStyleOption.valueOf(actionKey.substring("GRAPHSTYLE_".length())));
        }
        else if (actionKey.startsWith("BARTEX_"))
        {
            configManager.setConfiguration("osrscopilot", "combatBarTexture",
                OsrsCopilotConfig.BarTextureOption.valueOf(actionKey.substring("BARTEX_".length())));
        }
        else if (actionKey.startsWith("BARFONT_"))
        {
            configManager.setConfiguration("osrscopilot", "combatBarFont",
                OsrsCopilotConfig.BarFontOption.valueOf(actionKey.substring("BARFONT_".length())));
        }
        else if ("TOGGLE_RANK".equals(actionKey))
        {
            configManager.setConfiguration("osrscopilot", "combatBarShowRank", !config.combatBarShowRank());
        }
        else if (actionKey.startsWith("BARCOUNT_"))
        {
            int count = Integer.parseInt(actionKey.substring(9));
            configManager.setConfiguration("osrscopilot", "maxCombatBars", count);
        }
        else if (actionKey.startsWith("STYLE_"))
        {
            OsrsCopilotConfig.BarStyleOption style = OsrsCopilotConfig.BarStyleOption.valueOf(actionKey.substring(6));
            configManager.setConfiguration("osrscopilot", "combatBarStyle", style);
            if (style == OsrsCopilotConfig.BarStyleOption.CLASS_STYLE)
            {
                configManager.setConfiguration("osrscopilot", "combatPaletteAccent", OsrsCopilotConfig.PaletteAccentOption.DYNAMIC_STYLE);
            }
        }
        else if (actionKey.startsWith("SWATCH_"))
        {
            OsrsCopilotConfig.PaletteAccentOption swatch = OsrsCopilotConfig.PaletteAccentOption.valueOf(actionKey.substring(7));
            configManager.setConfiguration("osrscopilot", "combatPaletteAccent", swatch);
            if (swatch == OsrsCopilotConfig.PaletteAccentOption.DYNAMIC_STYLE)
            {
                configManager.setConfiguration("osrscopilot", "combatBarStyle", OsrsCopilotConfig.BarStyleOption.CLASS_STYLE);
            }
            else if (config.combatBarStyle() == OsrsCopilotConfig.BarStyleOption.CLASS_STYLE)
            {
                configManager.setConfiguration("osrscopilot", "combatBarStyle", OsrsCopilotConfig.BarStyleOption.GRADIENT);
            }
        }
        else if ("TOGGLE_AUTOHIDE".equals(actionKey))
        {
            configManager.setConfiguration("osrscopilot", "combatAutoHide", !config.combatAutoHide());
        }
        else if ("TOGGLE_FOOTER".equals(actionKey))
        {
            configManager.setConfiguration("osrscopilot", "combatShowMiniFooter", !config.combatShowMiniFooter());
        }
        else if ("BANNER_ENABLE".equals(actionKey))
        {
            configManager.setConfiguration("osrscopilot", "combatBannerEnabled", !config.combatBannerEnabled());
        }
        else if ("BANNER_EDIT".equals(actionKey))
        {
            if (bannerOverlay != null)
            {
                bannerOverlay.toggleEditMode();
            }
        }
        else if ("BANNER_PREVIEW".equals(actionKey))
        {
            if (bannerOverlay != null)
            {
                bannerOverlay.previewBanner();
            }
        }
        else if ("ACTION_RESET_ENCOUNTER".equals(actionKey))
        {
            encounterManager.resetCurrentEncounter();
        }
        else if ("ACTION_RESET_SESSION".equals(actionKey))
        {
            encounterManager.resetSessionAndHistory();
        }
        else if ("ACTION_COPY_CHAT".equals(actionKey))
        {
            copyCombatReportToClipboard();
        }
        else if ("SHARE_PUBLIC".equals(actionKey))
        {
            encounterManager.shareToChat(com.osrscopilot.combat.ShareChannel.PUBLIC);
        }
        else if ("SHARE_FRIENDS".equals(actionKey))
        {
            encounterManager.shareToChat(com.osrscopilot.combat.ShareChannel.FRIENDS);
        }
        else if ("SHARE_CLAN".equals(actionKey))
        {
            encounterManager.shareToChat(com.osrscopilot.combat.ShareChannel.CLAN);
        }
    }

    public void copyCombatReportToClipboard()
    {
        encounterManager.shareCopy();
    }

    private static int clamp(int v, int lo, int hi)
    {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public synchronized Map<String, Rectangle> getHitboxes()
    {
        return new HashMap<>(hitboxes);
    }
}
