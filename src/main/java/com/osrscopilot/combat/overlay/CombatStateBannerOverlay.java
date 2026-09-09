package com.osrscopilot.combat.overlay;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.OsrsCopilotConfig.BannerAnchor;
import com.osrscopilot.OsrsCopilotConfig.BannerAnimation;
import com.osrscopilot.OsrsCopilotConfig.BannerFontStyle;
import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.model.EncounterSegment;
import java.awt.AlphaComposite;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * An "Entering Combat" / "Left Combat" flash. Fully optional and configurable (text, font,
 * size, colour per state, screen position, animation, timing) via the "Combat State Banner" config
 * section. Fires only on the in-combat &lt;-&gt; out-of-combat transition, never per hit.
 *
 * <p>Draws directly in canvas coordinates and returns {@code null} (the standard RuneLite
 * screen-flash pattern), so OverlayManager never fights it for bounds. Positioning is done through
 * the anchor + offset config; the Combat Meter settings card drives the "Reposition banner"
 * placement sample ({@link #setEditMode}) and the one-shot {@link #previewBanner()} flash.
 */
@Singleton
public class CombatStateBannerOverlay extends Overlay
{
    private static final long ENTER_RATE_LIMIT_MS = 3000;
    private static final int PREVIEW_MIN_LINGER_MS = 1500;
    private static final Color SHADOW = new Color(0, 0, 0, 180);

    private final Client client;
    private final OsrsCopilotConfig config;
    private final CombatEncounterManager encounterManager;

    private boolean wasInCombat = false;
    private long lastEnterMs = 0;

    // "Reposition banner" mode: keeps a sample banner on screen so placement is easy to judge.
    // Transient UI state toggled from the Combat Meter settings card - deliberately not persisted.
    // volatile: the card's actions run on the AWT thread; render() reads these on the client thread.
    private volatile boolean editMode = false;

    private volatile String bannerText;
    private volatile Color bannerColor;
    private volatile long bannerStartMs;
    private volatile int lingerOverrideMs = -1; // >=0 when a Preview forces a longer hold

    @Inject
    public CombatStateBannerOverlay(Client client, OsrsCopilotConfig config, CombatEncounterManager encounterManager)
    {
        this.client = client;
        this.config = config;
        this.encounterManager = encounterManager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    /** One-shot flash with the current "entering" text/colour/animation. Works even when the banner is disabled. */
    public void previewBanner()
    {
        trigger(safe(config.combatBannerEnterText(), "Entering Combat"), config.combatBannerEnterColor());
        this.lingerOverrideMs = Math.max(PREVIEW_MIN_LINGER_MS, config == null ? 0 : config.combatBannerLingerMs());
    }

    /** "Reposition banner" placement sample - toggled from the Combat Meter settings card. */
    public void setEditMode(boolean editMode)
    {
        this.editMode = editMode;
    }

    public boolean isEditMode()
    {
        return editMode;
    }

    /** The banner text currently on screen, or null. For tests / inspection. */
    public String currentBannerText()
    {
        return bannerText;
    }

    public void toggleEditMode()
    {
        this.editMode = !this.editMode;
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (config == null)
        {
            return null;
        }

        // Out of the game world: drop any in-flight banner and reset the edge tracker so a logout /
        // world-hop mid-fight can't leave "Left Combat" primed for the next login.
        if (client != null && client.getGameState() != GameState.LOGGED_IN)
        {
            wasInCombat = false;
            bannerText = null;
            return null;
        }

        // Always keep the combat-edge tracker honest so toggling the banner on mid-fight can't
        // fire a stale edge; only actually raise a banner when the feature is enabled.
        detectCombatEdge(config.combatBannerEnabled());

        boolean editMode = this.editMode;

        int fadeIn = Math.max(0, config.combatBannerFadeInMs());
        int linger = lingerOverrideMs >= 0 ? lingerOverrideMs : Math.max(0, config.combatBannerLingerMs());
        int fadeOut = Math.max(0, config.combatBannerFadeOutMs());
        long life = fadeIn + linger + fadeOut;

        // Retire an expired banner up-front so the branch below is a clean "live banner vs sample".
        if (bannerText != null && System.currentTimeMillis() - bannerStartMs >= life)
        {
            bannerText = null;
            lingerOverrideMs = -1;
        }
        if (bannerText == null && !editMode)
        {
            return null;
        }

        String text;
        Color color;
        float alpha;
        float progress;   // 0..1 over fade-in, then 1
        float pulse = 0f; // 0..1 triangle wave over the linger, for PULSE

        if (bannerText == null)
        {
            // Position-mode sample: full-ish alpha, no animation, so placement is easy to judge.
            text = safe(config.combatBannerEnterText(), "Entering Combat") + "  (position mode)";
            color = config.combatBannerEnterColor();
            alpha = 0.85f;
            progress = 1f;
        }
        else
        {
            long elapsed = System.currentTimeMillis() - bannerStartMs;
            text = bannerText;
            color = bannerColor;
            if (elapsed < fadeIn)
            {
                alpha = fadeIn == 0 ? 1f : (float) elapsed / fadeIn;
                progress = alpha;
            }
            else if (elapsed < fadeIn + linger)
            {
                alpha = 1f;
                progress = 1f;
                if (linger > 0)
                {
                    float t = (elapsed - fadeIn) / (float) linger; // 0..1
                    pulse = 1f - Math.abs(t * 2f - 1f);            // triangle: 0 -> 1 -> 0
                }
            }
            else
            {
                alpha = fadeOut == 0 ? 0f : 1f - ((float) (elapsed - fadeIn - linger) / fadeOut);
                progress = 1f;
            }
            alpha = clamp01(alpha);
        }

        Canvas canvas = client != null ? client.getCanvas() : null;
        int cw = canvas != null ? canvas.getWidth() : 765;
        int ch = canvas != null ? canvas.getHeight() : 503;

        Font base;
        switch (config.combatBannerFontStyle() == null ? BannerFontStyle.BOLD : config.combatBannerFontStyle())
        {
            case SMALL:   base = FontManager.getRunescapeSmallFont(); break;
            case REGULAR: base = FontManager.getRunescapeFont(); break;
            default:      base = FontManager.getRunescapeBoldFont(); break;
        }

        BannerAnimation anim = config.combatBannerAnimation() == null ? BannerAnimation.FADE : config.combatBannerAnimation();
        float sizeScale = 1f;
        if (anim == BannerAnimation.POP && progress < 1f)
        {
            sizeScale = 0.6f + 0.4f * progress;
        }
        else if (anim == BannerAnimation.SCALE_OUT && progress < 1f)
        {
            sizeScale = 1.6f - 0.6f * progress;
        }
        else if (anim == BannerAnimation.PULSE)
        {
            sizeScale = 1f + 0.06f * pulse;
        }

        // combatBannerFontSize is an absolute point size; scale it by the animation factor.
        Font font = base.deriveFont(config.combatBannerFontSize() * sizeScale);
        g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);
        int th = fm.getAscent();

        BannerAnchor anchor = config.combatBannerAnchor() == null ? BannerAnchor.TOP : config.combatBannerAnchor();
        int x = cw / 2 - tw / 2 + config.combatBannerOffsetX();
        int y;
        if (anchor == BannerAnchor.CENTER)
        {
            y = ch / 2 + Math.round(ch * ((config.combatBannerOffsetYPct() - 50) / 100f));
        }
        else // TOP uses the vertical-percent slider, measured from the top of the screen
        {
            y = Math.round(ch * (config.combatBannerOffsetYPct() / 100f)) + th;
        }
        if (anim == BannerAnimation.SLIDE_DOWN && progress < 1f)
        {
            y -= Math.round((1f - progress) * 24f);
        }
        else if (anim == BannerAnimation.SLIDE_UP && progress < 1f)
        {
            y += Math.round((1f - progress) * 24f);
        }

        Composite oldComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        if (config.combatBannerTextShadow())
        {
            g.setColor(SHADOW);
            g.drawString(text, x + 1, y + 1);
        }
        g.setColor(color != null ? color : Color.WHITE);
        g.drawString(text, x, y);
        g.setComposite(oldComposite);

        return null;
    }

    private void detectCombatEdge(boolean enabled)
    {
        EncounterSegment enc = encounterManager != null ? encounterManager.getCurrentEncounter() : null;
        // A live shared party fight counts as "in combat" too, so the banner fires when a nearby
        // teammate pulls first and the local player hasn't thrown a hit yet.
        boolean inCombat = (enc != null && enc.isInCombat())
            || (encounterManager != null && encounterManager.isGroupCombatActive());

        if (enabled && inCombat && !wasInCombat)
        {
            long now = System.currentTimeMillis();
            if (now - lastEnterMs > ENTER_RATE_LIMIT_MS)
            {
                lastEnterMs = now;
                trigger(safe(config.combatBannerEnterText(), "Entering Combat"), config.combatBannerEnterColor());
            }
        }
        else if (enabled && !inCombat && wasInCombat && config.combatBannerShowLeaving())
        {
            trigger(safe(config.combatBannerLeaveText(), "Left Combat"), config.combatBannerLeaveColor());
        }
        wasInCombat = inCombat;
    }

    private void trigger(String text, Color color)
    {
        this.bannerText = text;
        this.bannerColor = color;
        this.bannerStartMs = System.currentTimeMillis();
        this.lingerOverrideMs = -1;
    }

    private static float clamp01(float v)
    {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static String safe(String s, String fallback)
    {
        return (s == null || s.trim().isEmpty()) ? fallback : s.trim();
    }
}
