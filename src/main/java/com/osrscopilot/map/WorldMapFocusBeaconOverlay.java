package com.osrscopilot.map;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

@Slf4j
@Singleton
public class WorldMapFocusBeaconOverlay extends Overlay
{
    private static final long DURATION_MS = 6000L;
    private static final long FADEOUT_START_MS = 5000L;

    private final Client client;
    private final WorldMapOverlay worldMapOverlay;

    private WorldPoint targetPoint = null;
    private String targetLabel = null;
    private long startTime = 0L;

    @Inject
    public WorldMapFocusBeaconOverlay(Client client, WorldMapOverlay worldMapOverlay)
    {
        this.client = client;
        this.worldMapOverlay = worldMapOverlay;

        setPosition(OverlayPosition.DYNAMIC);
        setPriority(PRIORITY_HIGHEST);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
    }

    public void triggerBeacon(WorldPoint point, String label)
    {
        this.targetPoint = point;
        this.targetLabel = label;
        this.startTime = System.currentTimeMillis();
    }

    public void clearBeacon()
    {
        this.targetPoint = null;
        this.targetLabel = null;
        this.startTime = 0L;
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (targetPoint == null || startTime == 0L)
        {
            return null;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > DURATION_MS)
        {
            clearBeacon();
            return null;
        }

        Widget worldMap = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        if (worldMap == null || worldMap.isHidden())
        {
            return null;
        }

        Point screenPt = worldMapOverlay.mapWorldPointToGraphicsPoint(targetPoint);
        if (screenPt == null)
        {
            return null;
        }

        int sx = screenPt.getX();
        int sy = screenPt.getY();

        if (sx < 0 || sx > client.getCanvasWidth() || sy < 0 || sy > client.getCanvasHeight())
        {
            return null;
        }

        // Global master alpha factor (fades in last second)
        float masterAlpha = 1.0f;
        if (elapsed > FADEOUT_START_MS)
        {
            masterAlpha = 1.0f - ((float) (elapsed - FADEOUT_START_MS) / (DURATION_MS - FADEOUT_START_MS));
            masterAlpha = Math.max(0.0f, Math.min(1.0f, masterAlpha));
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        double seconds = elapsed / 1000.0;

        // A "~ "-prefixed label means the target is only an approximate area (an unverified dungeon
        // entrance): draw a wide, soft ring pulse and NO pinpoint core / arrow, so it doesn't claim
        // a precision we don't have.
        if (isApproximate())
        {
            renderApproxArea(g, sx, sy, seconds, masterAlpha);
            return null;
        }

        // Layer A: 3 Concentric Expanding Radar Rings
        renderRadarRings(g, sx, sy, seconds, masterAlpha);

        // Layer B: Pulsating Golden Core Orb with Halo
        renderPulsatingCore(g, sx, sy, seconds, masterAlpha);

        // Layer C: Downward Bouncing Indicator Arrow (no duplicate text banner)
        renderBouncingPointer(g, sx, sy, seconds, masterAlpha);

        return null;
    }

    private boolean isApproximate()
    {
        return targetLabel != null && targetLabel.startsWith("~ ");
    }

    /** Soft, wide "somewhere around here" pulse - cool grey-blue, no sharp centre. */
    private void renderApproxArea(Graphics2D g, int cx, int cy, double seconds, float masterAlpha)
    {
        for (float offset : new float[]{0.0f, 0.5f})
        {
            float progress = (float) ((seconds + offset * 2.4f) % 2.4f) / 2.4f;
            float radius = 18.0f + (progress * 60.0f);
            float ringAlpha = (1.0f - progress) * masterAlpha;
            int a = Math.max(0, Math.min(255, (int) (ringAlpha * 150)));
            if (a > 0)
            {
                g.setColor(new Color(150, 180, 210, a));
                g.setStroke(new BasicStroke(2.0f));
                g.drawOval(Math.round(cx - radius), Math.round(cy - radius), Math.round(radius * 2), Math.round(radius * 2));
            }
        }
        // faint filled centre disc so the eye lands in the area without reading it as an exact pin
        int discAlpha = Math.max(0, Math.min(255, (int) (masterAlpha * 55)));
        g.setColor(new Color(150, 180, 210, discAlpha));
        g.fillOval(cx - 12, cy - 12, 24, 24);
    }

    private void renderRadarRings(Graphics2D g, int cx, int cy, double seconds, float masterAlpha)
    {
        float[] ringOffsets = {0.0f, 0.33f, 0.66f};
        float period = 1.5f;

        for (float offset : ringOffsets)
        {
            float progress = (float) ((seconds + offset * period) % period) / period;
            float radius = 10.0f + (progress * 42.0f); // 10px -> 52px
            float ringAlpha = (1.0f - (progress * progress)) * masterAlpha; // quadratic fade

            int alphaInt = Math.max(0, Math.min(255, (int) (ringAlpha * 220)));
            if (alphaInt > 0)
            {
                g.setColor(new Color(255, 215, 0, alphaInt));
                g.setStroke(new BasicStroke(2.0f - (progress * 1.0f)));
                g.drawOval(Math.round(cx - radius), Math.round(cy - radius), Math.round(radius * 2), Math.round(radius * 2));
            }
        }
    }

    private void renderPulsatingCore(Graphics2D g, int cx, int cy, double seconds, float masterAlpha)
    {
        double pulse = Math.sin(seconds * 6.0); // 6 rad/s oscillation
        float coreRadius = 8.0f + (float) (pulse * 3.0); // 5px -> 11px
        float haloRadius = coreRadius + 8.0f;

        int haloAlpha = Math.max(0, Math.min(255, (int) (masterAlpha * 120)));
        int coreAlpha = Math.max(0, Math.min(255, (int) (masterAlpha * 240)));

        if (haloRadius > 0 && haloAlpha > 0)
        {
            // Radial gradient halo
            float[] dist = {0.0f, 0.5f, 1.0f};
            Color[] colors = {
                new Color(255, 245, 157, coreAlpha),
                new Color(245, 158, 11, haloAlpha),
                new Color(245, 158, 11, 0)
            };
            RadialGradientPaint p = new RadialGradientPaint(cx, cy, haloRadius, dist, colors);
            g.setPaint(p);
            g.fillOval(Math.round(cx - haloRadius), Math.round(cy - haloRadius), Math.round(haloRadius * 2), Math.round(haloRadius * 2));
        }

        // Inner solid core
        g.setColor(new Color(255, 255, 255, coreAlpha));
        g.fillOval(Math.round(cx - 3.5f), Math.round(cy - 3.5f), 7, 7);
    }

    private void renderBouncingPointer(Graphics2D g, int cx, int cy, double seconds, float masterAlpha)
    {
        int goldAlpha = Math.max(0, Math.min(255, (int) (masterAlpha * 255)));
        if (goldAlpha <= 0) return;

        // Bouncing sinusoidal offset
        double bounce = Math.sin(seconds * 4.5) * 4.0;
        int tipY = cy - 14 + (int) bounce;
        int topY = tipY - 10;

        Path2D.Float triangle = new Path2D.Float();
        triangle.moveTo(cx - 6, topY);
        triangle.lineTo(cx + 6, topY);
        triangle.lineTo(cx, tipY);
        triangle.closePath();

        g.setColor(new Color(255, 215, 0, goldAlpha));
        g.fill(triangle);
        g.setColor(new Color(0, 0, 0, goldAlpha));
        g.setStroke(new BasicStroke(1.0f));
        g.draw(triangle);
    }
}
