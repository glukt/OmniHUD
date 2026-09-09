package com.osrscopilot.ui.theme;

import com.osrscopilot.ui.AboutView;
import com.osrscopilot.ui.MonsterDetailView;
import com.osrscopilot.ui.SlayerTabView;
import java.awt.Color;
import java.lang.reflect.Field;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Locks in the theme slice: the anchor accent value, that the four rival "gold" literals really did
 * collapse onto {@link CopilotPalette#ACCENT}, that the migrated side-panel views resolve their
 * named colour constants to the shared tokens (so a tweak to {@link CopilotPalette#ACCENT}
 * re-themes all of them at once), and the {@link SidebarMetrics#htmlWrap} markup contract.
 */
public class CopilotPaletteTest
{
    @Test
    public void accentIsTheAnchorGold()
    {
        assertEquals("ACCENT stays the most-used of the four golds", new Color(255, 152, 31), CopilotPalette.ACCENT);
    }

    @Test
    public void theFourRivalGoldsAreUnifiedNotKeptDistinct()
    {
        // 255,185,45 (active tab), 255,112,67 (combat tab), 255,193,7 (tour title) all folded in.
        assertFalse(CopilotPalette.ACCENT.equals(new Color(255, 185, 45)));
        assertFalse(CopilotPalette.ACCENT.equals(new Color(255, 112, 67)));
        assertFalse(CopilotPalette.ACCENT.equals(new Color(255, 193, 7)));
        // GOLD (255,215,0) is a separate role and must NOT have been merged into the accent.
        assertEquals(new Color(255, 215, 0), CopilotPalette.GOLD);
        assertFalse(CopilotPalette.GOLD.equals(CopilotPalette.ACCENT));
    }

    @Test
    public void cardBgAliasesTheRuneLitePanelStandard()
    {
        assertSame(ColorScheme.DARKER_GRAY_COLOR, CopilotPalette.CARD_BG);
    }

    @Test
    public void positiveAndNegativeMatchTheCombatMeterValues()
    {
        assertEquals(new Color(129, 199, 132), CopilotPalette.POSITIVE);
        assertEquals(new Color(239, 83, 80), CopilotPalette.NEGATIVE);
    }

    @Test
    public void migratedViewsResolveTheirNamedColoursToSharedTokens() throws Exception
    {
        assertSame(CopilotPalette.ACCENT, staticColor(AboutView.class, "TITLE_COLOR"));

        assertSame(CopilotPalette.ACCENT, staticColor(MonsterDetailView.class, "TITLE_COLOR"));
        assertSame(CopilotPalette.GOLD, staticColor(MonsterDetailView.class, "COMBAT_LVL_COLOR"));
        assertSame(CopilotPalette.LINK, staticColor(MonsterDetailView.class, "FOCUS_BLUE"));
        assertSame(CopilotPalette.WEAKNESS, staticColor(MonsterDetailView.class, "WEAKNESS_GREEN"));
        assertSame(CopilotPalette.SLAYER, staticColor(MonsterDetailView.class, "SLAYER_PURPLE"));
        assertSame(CopilotPalette.QUEST, staticColor(MonsterDetailView.class, "QUEST_PURPLE"));
        assertSame(CopilotPalette.WILDY, staticColor(MonsterDetailView.class, "WILDY_RED"));
        assertSame(CopilotPalette.MULTI, staticColor(MonsterDetailView.class, "MULTI_ORANGE"));

        assertSame(CopilotPalette.ACCENT, staticColor(SlayerTabView.class, "TITLE_COLOR"));
        assertSame(CopilotPalette.LINK, staticColor(SlayerTabView.class, "FOCUS_BLUE"));
        assertSame(CopilotPalette.WEAKNESS, staticColor(SlayerTabView.class, "WEAKNESS_GREEN"));
        assertSame(CopilotPalette.SLAYER, staticColor(SlayerTabView.class, "SLAYER_PURPLE"));
        assertSame(CopilotPalette.QUEST, staticColor(SlayerTabView.class, "QUEST_PURPLE"));
        assertSame(CopilotPalette.WILDY, staticColor(SlayerTabView.class, "WILDY_RED"));
        assertSame(CopilotPalette.MULTI, staticColor(SlayerTabView.class, "MULTI_ORANGE"));
        assertSame(CopilotPalette.GOLD, staticColor(SlayerTabView.class, "REWARD_GOLD"));
    }

    @Test
    public void sidebarMetricsConstantsAndWrapMarkup()
    {
        assertEquals(175, SidebarMetrics.WRAP_WIDTH);
        assertEquals(215, SidebarMetrics.PANEL_WIDTH);
        assertEquals(205, SidebarMetrics.MIN_WIDTH);
        assertEquals(850, SidebarMetrics.MAX_WIDTH);
        // Must reproduce the legacy AboutView markup byte-for-byte so migrating it changed nothing.
        assertEquals("<html><body style='width:175px'>hi</body></html>", SidebarMetrics.htmlWrap("hi"));
        assertEquals("<html><body style='width:175px'></body></html>", SidebarMetrics.htmlWrap(null));
        assertTrue(SidebarMetrics.htmlWrap("<b>x</b>").contains("<b>x</b>")); // inline markup is passed through
    }

    private static Color staticColor(Class<?> owner, String field) throws Exception
    {
        Field f = owner.getDeclaredField(field);
        f.setAccessible(true);
        return (Color) f.get(null);
    }
}
