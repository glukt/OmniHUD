package com.osrscopilot.combat.overlay;

import com.osrscopilot.combat.CombatMeterColors;
import com.osrscopilot.combat.model.CombatTimeSeriesPoint;
import com.osrscopilot.combat.model.EncounterSegment;
import com.osrscopilot.combat.model.EntityCombatStats;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import net.runelite.client.ui.FontManager;

@Singleton
public class CombatGraphCard
{
    public enum GraphMode
    {
        DPS("DPS", CombatMeterColors.TEXT_ACCENT_GOLD),
        MELEE("Melee", CombatMeterColors.MELEE_PRIMARY),
        RANGED("Range", CombatMeterColors.RANGED_PRIMARY),
        MAGIC("Magic", CombatMeterColors.MAGIC_PRIMARY),
        STATUS("Status", CombatMeterColors.STATUS_PRIMARY),
        DTPS("Taken", CombatMeterColors.DAMAGE_TAKEN_PRIMARY),
        HPS("Heals", CombatMeterColors.HEALING_PRIMARY),
        ALL("All", new Color(171, 71, 188));

        private final String title;
        private final Color color;

        GraphMode(String title, Color color)
        {
            this.title = title;
            this.color = color;
        }

        public String getTitle()
        {
            return title;
        }

        public Color getColor()
        {
            return color;
        }
    }

    public static final int DEFAULT_WIDTH = 420;
    public static final int DEFAULT_HEIGHT = 240;
    public static final int MIN_WIDTH = 300;
    public static final int MIN_HEIGHT = 180;
    public static final int MAX_WIDTH = 800;
    public static final int MAX_HEIGHT = 600;

    private GraphMode currentMode = GraphMode.DPS;
    private com.osrscopilot.OsrsCopilotConfig.GraphStyleOption style =
        com.osrscopilot.OsrsCopilotConfig.GraphStyleOption.AREA;
    private Dimension size = new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    private Runnable onClose;

    public void setStyle(com.osrscopilot.OsrsCopilotConfig.GraphStyleOption s)
    {
        if (s != null) this.style = s;
    }

    public GraphMode getCurrentMode()
    {
        return currentMode;
    }

    public void setCurrentMode(GraphMode currentMode)
    {
        this.currentMode = currentMode;
    }

    public Dimension getSize()
    {
        return size;
    }

    public void setSize(Dimension size)
    {
        this.size = size;
    }

    public void setOnClose(Runnable onClose)
    {
        this.onClose = onClose;
    }

    private Rectangle bounds = new Rectangle();
    private Rectangle closeBounds = new Rectangle();
    private Rectangle resizeGripBounds = new Rectangle();
    private Rectangle headerBounds = new Rectangle();
    // Published from render() (client thread), read in handleClick() (AWT thread) - copy-on-write.
    private volatile Map<GraphMode, Rectangle> modeButtonHitboxes = java.util.Collections.emptyMap();

    public void render(Graphics2D g, Point screenPos, Point mousePos, EncounterSegment encounter)
    {
        render(g, screenPos, screenPos, mousePos, encounter);
    }

    public void render(Graphics2D g, Point localPos, Point screenPos, Point mousePos, EncounterSegment encounter)
    {
        if (encounter == null) return;
        EntityCombatStats stats = encounter.getLocalPlayerStats();
        if (stats == null) return;

        if (localPos == null) localPos = screenPos != null ? screenPos : new Point(0, 0);
        if (screenPos == null) screenPos = localPos;

        int x = localPos.x;
        int y = localPos.y;
        int deltaX = screenPos.x - localPos.x;
        int deltaY = screenPos.y - localPos.y;
        int w = size.width;
        int h = size.height;

        bounds = new Rectangle(x + deltaX, y + deltaY, w, h);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 1. Background & Outer Border
        g.setColor(CombatMeterColors.POPUP_BG);
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(x, y, w - 1, h - 1, 8, 8);
        g.setColor(new Color(255, 255, 255, 20));
        g.drawRoundRect(x + 1, y + 1, w - 3, h - 3, 7, 7);

        // 2. Header Bar
        int headerH = 24;
        g.setColor(new Color(30, 30, 38, 240));
        g.fillRoundRect(x + 2, y + 2, w - 4, headerH, 6, 6);
        headerBounds = new Rectangle(x + deltaX, y + deltaY, w - 28, headerH);

        g.setFont(FontManager.getRunescapeBoldFont());
        g.setColor(CombatMeterColors.TEXT_ACCENT_GOLD);
        String headerTitle = "Fight Graph: " + encounter.getTargetName() + " (" + formatDuration(stats.getDurationSeconds()) + ")";
        g.drawString(truncateString(headerTitle, w - 60, g), x + 8, y + 16);

        // Close Button (drawn cross - the RS bitmap font has no glyph for it)
        int closeSize = 16;
        int closeX = x + w - closeSize - 6;
        int closeY = y + 4;
        closeBounds = new Rectangle(closeX + deltaX, closeY + deltaY, closeSize, closeSize);
        boolean closeHover = mousePos != null && closeBounds.contains(mousePos);
        g.setColor(closeHover ? new Color(239, 83, 80, 220) : new Color(40, 40, 50, 180));
        g.fillRoundRect(closeX, closeY, closeSize, closeSize, 4, 4);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(closeX, closeY, closeSize - 1, closeSize - 1, 4, 4);
        g.setColor(Color.WHITE);
        HudGlyphs.cross(g, closeX + 4, closeY + 4, 8);

        // 3. Mode Switcher Tabs / Pills
        int tabY = y + headerH + 6;
        int tabH = 18;
        int tabSpacing = 4;
        int totalTabs = GraphMode.values().length;
        int tabW = Math.max(38, (w - 16 - (totalTabs - 1) * tabSpacing) / totalTabs);
        int curTabX = x + 8;
        Map<GraphMode, Rectangle> hb = new HashMap<>();

        for (GraphMode mode : GraphMode.values())
        {
            Rectangle screenTabRect = new Rectangle(curTabX + deltaX, tabY + deltaY, tabW, tabH);
            hb.put(mode, screenTabRect);

            boolean isHover = mousePos != null && screenTabRect.contains(mousePos);
            boolean isActive = mode == currentMode;

            g.setColor(isActive ? CombatMeterColors.BTN_BG_ACTIVE : (isHover ? CombatMeterColors.BTN_BG_HOVER : new Color(30, 30, 38, 200)));
            g.fillRoundRect(curTabX, tabY, tabW, tabH, 4, 4);
            g.setColor(isActive ? mode.getColor() : CombatMeterColors.HUD_BORDER);
            g.drawRoundRect(curTabX, tabY, tabW - 1, tabH - 1, 4, 4);

            g.setFont(FontManager.getRunescapeSmallFont());
            g.setColor(isActive ? Color.WHITE : (isHover ? Color.WHITE : mode.getColor()));
            String title = mode.getTitle();
            int strW = g.getFontMetrics().stringWidth(title);
            int textX = curTabX + Math.max(2, (tabW - strW) / 2);
            g.drawString(title, textX, tabY + 13);

            curTabX += tabW + tabSpacing;
        }
        modeButtonHitboxes = hb;

        // 4. Plot Area
        int plotX = x + 44;
        int plotY = tabY + tabH + 10;
        int plotW = w - 56;
        int plotH = h - (plotY - y) - 28;

        if (plotW > 50 && plotH > 40)
        {
            renderPlot(g, plotX, plotY, plotW, plotH, deltaX, deltaY, stats, mousePos);
        }

        // 5. Resize Grip (◢)
        int gripSize = 8;
        int gx = x + w - gripSize - 3;
        int gy = y + h - gripSize - 3;
        g.setColor(new Color(255, 255, 255, 70));
        g.drawLine(gx + 5, gy + 7, gx + 7, gy + 5);
        g.drawLine(gx + 2, gy + 7, gx + 7, gy + 2);
        g.drawLine(gx - 1, gy + 7, gx + 7, gy - 1);
        resizeGripBounds = new Rectangle(gx + deltaX - 3, gy + deltaY - 3, gripSize + 6, gripSize + 6);
    }

    private void renderPlot(Graphics2D g, int px, int py, int pw, int ph, int deltaX, int deltaY, EntityCombatStats stats, Point mousePos)
    {
        List<CombatTimeSeriesPoint> pts = stats.getTimeSeries();
        int maxSec = Math.max(10, (int) Math.ceil(stats.getDurationSeconds()));
        if (!pts.isEmpty() && pts.get(pts.size() - 1).getSecond() > maxSec)
        {
            maxSec = pts.get(pts.size() - 1).getSecond();
        }

        // Calculate max Y value based on mode
        double maxY = 1.0;
        double avgVal = 0.0;
        double peakVal = 0.0;
        int peakSec = 0;

        if (currentMode == GraphMode.DPS)
        {
            peakVal = stats.getPeakDps();
            avgVal = stats.getDps();
            maxY = Math.max(5.0, peakVal * 1.2);
        }
        else if (currentMode == GraphMode.MELEE)
        {
            peakVal = getPeakStyleDps(pts, GraphMode.MELEE);
            avgVal = stats.getDurationSeconds() > 0 ? (double) stats.getStyleDamage(com.osrscopilot.combat.model.CombatStyle.MELEE) / stats.getDurationSeconds() : 0;
            maxY = Math.max(5.0, peakVal * 1.2);
        }
        else if (currentMode == GraphMode.RANGED)
        {
            peakVal = getPeakStyleDps(pts, GraphMode.RANGED);
            avgVal = stats.getDurationSeconds() > 0 ? (double) stats.getStyleDamage(com.osrscopilot.combat.model.CombatStyle.RANGED) / stats.getDurationSeconds() : 0;
            maxY = Math.max(5.0, peakVal * 1.2);
        }
        else if (currentMode == GraphMode.MAGIC)
        {
            peakVal = getPeakStyleDps(pts, GraphMode.MAGIC);
            avgVal = stats.getDurationSeconds() > 0 ? (double) stats.getStyleDamage(com.osrscopilot.combat.model.CombatStyle.MAGIC) / stats.getDurationSeconds() : 0;
            maxY = Math.max(5.0, peakVal * 1.2);
        }
        else if (currentMode == GraphMode.STATUS)
        {
            peakVal = getPeakStyleDps(pts, GraphMode.STATUS);
            long stDmg = stats.getStyleDamage(com.osrscopilot.combat.model.CombatStyle.POISON)
                + stats.getStyleDamage(com.osrscopilot.combat.model.CombatStyle.VENOM)
                + stats.getStyleDamage(com.osrscopilot.combat.model.CombatStyle.BURN)
                + stats.getStyleDamage(com.osrscopilot.combat.model.CombatStyle.BLEED)
                + stats.getStyleDamage(com.osrscopilot.combat.model.CombatStyle.POISON_VENOM)
                + stats.getStyleDamage(com.osrscopilot.combat.model.CombatStyle.BURN_BLEED);
            avgVal = stats.getDurationSeconds() > 0 ? (double) stDmg / stats.getDurationSeconds() : 0;
            maxY = Math.max(5.0, peakVal * 1.2);
        }
        else if (currentMode == GraphMode.DTPS)
        {
            peakVal = stats.getPeakDtps();
            avgVal = stats.getDtps();
            maxY = Math.max(5.0, peakVal * 1.2);
        }
        else if (currentMode == GraphMode.HPS)
        {
            peakVal = stats.getPeakHps();
            avgVal = stats.getHps();
            maxY = Math.max(5.0, peakVal * 1.2);
        }
        else
        {
            peakVal = Math.max(stats.getPeakDps(), Math.max(stats.getPeakDtps(), stats.getPeakHps()));
            maxY = Math.max(5.0, peakVal * 1.2);
        }

        // Plot Box Background
        g.setColor(new Color(15, 15, 20, 200));
        g.fillRect(px, py, pw, ph);
        g.setColor(new Color(60, 60, 75, 120));
        g.drawRect(px, py, pw, ph);

        // Horizontal Grid Lines & Y-Axis Labels
        int gridDivs = 4;
        g.setFont(FontManager.getRunescapeSmallFont());
        for (int i = 0; i <= gridDivs; i++)
        {
            int gy = py + ph - (i * ph / gridDivs);
            double val = (maxY * i) / gridDivs;

            g.setColor(new Color(255, 255, 255, 20));
            g.drawLine(px, gy, px + pw, gy);

            g.setColor(new Color(180, 180, 195));
            String lbl = String.format("%.1f", val);
            g.drawString(lbl, px - 36, gy + 4);
        }

        // Vertical Grid Lines & X-Axis Time Labels
        int timeDivs = Math.min(6, Math.max(2, pw / 60));
        for (int i = 0; i <= timeDivs; i++)
        {
            int sec = (maxSec * i) / timeDivs;
            int gx = px + (i * pw / timeDivs);

            g.setColor(new Color(255, 255, 255, 15));
            g.drawLine(gx, py, gx, py + ph);

            g.setColor(new Color(180, 180, 195));
            String timeLbl = formatDuration(sec);
            g.drawString(timeLbl, gx - 14, py + ph + 14);
        }

        // Average Reference Line (Dashed)
        if (currentMode != GraphMode.ALL && avgVal > 0)
        {
            int avgY = py + ph - (int) Math.round((avgVal / maxY) * ph);
            if (avgY >= py && avgY <= py + ph)
            {
                Stroke orig = g.getStroke();
                g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0));
                g.setColor(new Color(255, 255, 255, 80));
                g.drawLine(px, avgY, px + pw, avgY);
                g.drawString(String.format("Avg: %.1f", avgVal), px + pw - 50, avgY - 3);
                g.setStroke(orig);
            }
        }

        // Draw Curves
        if (!pts.isEmpty())
        {
            if (currentMode == GraphMode.ALL)
            {
                drawMetricCurve(g, px, py, pw, ph, pts, maxSec, maxY, GraphMode.DPS);
                drawMetricCurve(g, px, py, pw, ph, pts, maxSec, maxY, GraphMode.DTPS);
                drawMetricCurve(g, px, py, pw, ph, pts, maxSec, maxY, GraphMode.HPS);
            }
            else
            {
                drawMetricCurve(g, px, py, pw, ph, pts, maxSec, maxY, currentMode);
            }

            // Find Peak Timestamp
            for (CombatTimeSeriesPoint p : pts)
            {
                double v = getPointValueForMode(p, currentMode);
                if (v >= peakVal && v > 0)
                {
                    peakSec = p.getSecond();
                }
            }

            // Peak Callout Badge
            if (peakVal > 0 && currentMode != GraphMode.ALL)
            {
                int peakX = px + (int) Math.round(((double) peakSec / maxSec) * pw);
                int peakY = py + ph - (int) Math.round((peakVal / maxY) * ph);
                g.setColor(currentMode.getColor());
                g.fillOval(peakX - 3, peakY - 3, 7, 7);
                g.setColor(Color.WHITE);
                g.drawOval(peakX - 3, peakY - 3, 6, 6);

                String peakText = String.format("Peak: %.1f @ %s", peakVal, formatDuration(peakSec));
                int bw = g.getFontMetrics().stringWidth(peakText) + 8;
                int bx = Math.min(px + pw - bw, Math.max(px, peakX - (bw / 2)));
                int by = Math.max(py + 14, peakY - 8);

                g.setColor(new Color(24, 24, 30, 230));
                g.fillRoundRect(bx, by - 10, bw, 14, 4, 4);
                g.setColor(currentMode.getColor());
                g.drawRoundRect(bx, by - 10, bw - 1, 13, 4, 4);
                g.drawString(peakText, bx + 4, by + 1);
            }
        }
        else
        {
            g.setColor(new Color(150, 150, 160));
            g.drawString("No time-series data recorded yet. Deal or take damage to populate graph!", px + 15, py + (ph / 2));
        }

        // Interactive Mouse Crosshair & Floating Inspection Tooltip
        int screenPlotX = px + deltaX;
        int screenPlotY = py + deltaY;
        if (mousePos != null && mousePos.x >= screenPlotX && mousePos.x <= screenPlotX + pw && mousePos.y >= screenPlotY && mousePos.y <= screenPlotY + ph)
        {
            renderCrosshairAndTooltip(g, px, py, pw, ph, deltaX, deltaY, pts, maxSec, mousePos);
        }
    }

    private double getPeakStyleDps(List<CombatTimeSeriesPoint> pts, GraphMode mode)
    {
        double peak = 0.0;
        for (CombatTimeSeriesPoint p : pts)
        {
            double v = getPointValueForMode(p, mode);
            if (v > peak) peak = v;
        }
        return peak;
    }

    // Always the SMOOTHED rolling rate - plotting raw per-bucket damage made the line spike to
    // the max-hit size and read a max hit as the "peak DPS".
    private double getPointValueForMode(CombatTimeSeriesPoint pt, GraphMode mode)
    {
        if (pt == null) return 0.0;
        switch (mode)
        {
            case DPS: return pt.getRollingDps();
            case MELEE: return pt.getRollingMeleeDps();
            case RANGED: return pt.getRollingRangedDps();
            case MAGIC: return pt.getRollingMagicDps();
            case STATUS: return pt.getRollingStatusDps();
            case DTPS: return pt.getRollingDtps();
            case HPS: return pt.getRollingHps();
            case ALL: return Math.max(pt.getRollingDps(), Math.max(pt.getRollingDtps(), pt.getRollingHps()));
            default: return 0.0;
        }
    }

    private void drawMetricCurve(Graphics2D g, int px, int py, int pw, int ph, List<CombatTimeSeriesPoint> pts, int maxSec, double maxY, GraphMode mode)
    {
        Color primaryColor = mode.getColor();
        Color fillColor = new Color(primaryColor.getRed(), primaryColor.getGreen(), primaryColor.getBlue(), 45);

        if (style == com.osrscopilot.OsrsCopilotConfig.GraphStyleOption.BARS)
        {
            g.setColor(fillColor);
            int bw = Math.max(1, pw / Math.max(1, pts.size()) - 1);
            for (CombatTimeSeriesPoint pt : pts)
            {
                int cx = px + (int) Math.round(((double) pt.getSecond() / maxSec) * pw);
                double val = getPointValueForMode(pt, mode);
                int h = (int) Math.round((val / maxY) * ph);
                h = Math.max(0, Math.min(ph, h));
                g.fillRect(cx - bw / 2, py + ph - h, bw, h);
            }
            return;
        }

        if (style == com.osrscopilot.OsrsCopilotConfig.GraphStyleOption.AREA)
        {
            Polygon fillPoly = new Polygon();
            fillPoly.addPoint(px, py + ph);
            int prevX = px;
            for (CombatTimeSeriesPoint pt : pts)
            {
                int curX = px + (int) Math.round(((double) pt.getSecond() / maxSec) * pw);
                double val = getPointValueForMode(pt, mode);
                int curY = py + ph - (int) Math.round((val / maxY) * ph);
                curY = Math.max(py, Math.min(py + ph, curY));
                fillPoly.addPoint(curX, curY);
                prevX = curX;
            }
            fillPoly.addPoint(prevX, py + ph);
            g.setPaint(new GradientPaint(px, py, fillColor, px, py + ph, new Color(0, 0, 0, 0)));
            g.fillPolygon(fillPoly);
        }

        // The line itself (drawn for LINE and AREA).
        g.setColor(primaryColor);
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int lastX = px;
        int lastY = py + ph;
        boolean first = true;
        for (CombatTimeSeriesPoint pt : pts)
        {
            int curX = px + (int) Math.round(((double) pt.getSecond() / maxSec) * pw);
            double val = getPointValueForMode(pt, mode);
            int curY = py + ph - (int) Math.round((val / maxY) * ph);
            curY = Math.max(py, Math.min(py + ph, curY));
            if (!first)
            {
                g.drawLine(lastX, lastY, curX, curY);
            }
            first = false;
            lastX = curX;
            lastY = curY;
        }
        g.setStroke(new BasicStroke(1.0f));
    }

    private void renderCrosshairAndTooltip(Graphics2D g, int px, int py, int pw, int ph, int deltaX, int deltaY, List<CombatTimeSeriesPoint> pts, int maxSec, Point mousePos)
    {
        int screenPlotX = px + deltaX;
        int hoverSec = (int) Math.round(((double) (mousePos.x - screenPlotX) / pw) * maxSec);
        hoverSec = Math.max(0, Math.min(maxSec, hoverSec));
        int crosshairX = px + (int) Math.round(((double) hoverSec / maxSec) * pw);

        // Vertical Crosshair
        g.setColor(new Color(77, 208, 225, 180));
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3, 3}, 0));
        g.drawLine(crosshairX, py, crosshairX, py + ph);
        g.setStroke(new BasicStroke(1.0f));

        // Find matching point
        CombatTimeSeriesPoint match = null;
        for (CombatTimeSeriesPoint pt : pts)
        {
            if (pt.getSecond() == hoverSec)
            {
                match = pt;
                break;
            }
        }

        // Tooltip Box Dimensions
        boolean hasBreakdown = match != null && (match.getMeleeDamage() > 0 || match.getRangedDamage() > 0 || match.getMagicDamage() > 0 || match.getStatusDamage() > 0);
        int boxW = 175;
        int boxH = 46;
        if (hasBreakdown) boxH += 14;
        if (match != null && !match.getEventNotes().isEmpty()) boxH += 16;

        int boxX = (crosshairX + 10 + boxW > px + pw) ? crosshairX - boxW - 10 : crosshairX + 10;
        int localMouseY = mousePos.y - deltaY;
        int boxY = Math.min(py + ph - boxH, Math.max(py + 4, localMouseY - 20));

        g.setColor(new Color(15, 15, 22, 245));
        g.fillRoundRect(boxX, boxY, boxW, boxH, 6, 6);
        g.setColor(CombatMeterColors.HUD_BORDER);
        g.drawRoundRect(boxX, boxY, boxW - 1, boxH - 1, 6, 6);

        g.setFont(FontManager.getRunescapeBoldFont());
        g.setColor(CombatMeterColors.TEXT_ACCENT_GOLD);
        g.drawString("Time: " + formatDuration(hoverSec), boxX + 6, boxY + 13);

        g.setFont(FontManager.getRunescapeSmallFont());
        int curRowY = boxY + 27;

        if (match != null)
        {
            g.setColor(Color.WHITE);
            String statStr = String.format("Total DPS: %.1f | Dmg: %d", match.getRollingDps(), match.getCumulativeDamage());
            g.drawString(statStr, boxX + 6, curRowY);
            curRowY += 13;

            if (hasBreakdown)
            {
                g.setColor(new Color(200, 200, 210));
                String breakdownStr = String.format("M:%d  R:%d  Mg:%d  St:%d",
                    match.getMeleeDamage(), match.getRangedDamage(), match.getMagicDamage(), match.getStatusDamage());
                g.drawString(breakdownStr, boxX + 6, curRowY);
                curRowY += 13;
            }

            g.setColor(new Color(239, 83, 80));
            String defStr = String.format("DTPS: %.1f | Heal: %d", match.getRollingDtps(), match.getCumulativeHealing());
            g.drawString(defStr, boxX + 6, curRowY);
            curRowY += 14;

            if (!match.getEventNotes().isEmpty())
            {
                g.setColor(new Color(129, 199, 132));
                String note = match.getEventNotes().get(match.getEventNotes().size() - 1);
                g.drawString(truncateString(note, boxW - 12, g), boxX + 6, curRowY);
            }
        }
        else
        {
            g.setColor(new Color(160, 160, 170));
            g.drawString("Idle / No combat actions", boxX + 6, curRowY);
        }
    }

    public boolean handleClick(Point p, CombatMeterOverlay overlay)
    {
        if (closeBounds != null && closeBounds.contains(p))
        {
            if (onClose != null) onClose.run();
            if (overlay != null) overlay.setGraphOpen(false);
            return true;
        }

        for (Map.Entry<GraphMode, Rectangle> entry : modeButtonHitboxes.entrySet())
        {
            if (entry.getValue().contains(p))
            {
                currentMode = entry.getKey();
                return true;
            }
        }

        return false;
    }

    public boolean isHeaderBar(Point p)
    {
        return headerBounds != null && headerBounds.contains(p);
    }

    public boolean isResizeGrip(Point p)
    {
        return resizeGripBounds != null && resizeGripBounds.contains(p);
    }

    private String formatDuration(double durationSeconds)
    {
        return com.osrscopilot.combat.CombatFormat.duration(durationSeconds);
    }

    private String truncateString(String text, int maxWidth, Graphics2D g)
    {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) return text;
        while (text.length() > 3 && fm.stringWidth(text + "...") > maxWidth)
        {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }
}
