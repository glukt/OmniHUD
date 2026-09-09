package com.osrscopilot.ui.theme;

/**
 * Shared width constants for the RuneLite side panel, so the "how wide is a wrapped label / a card"
 * numbers live in one place instead of being copy-pasted (as {@code PANEL_WIDTH} / {@code WRAP_WIDTH}
 * / {@code TEXT_WRAP_WIDTH}) into every view.
 *
 * <p>The panel itself starts at RuneLite's hard-coded {@code PluginPanel} default and can be dragged
 * between {@link #MIN_WIDTH} and {@link #MAX_WIDTH}; {@link #PANEL_WIDTH} is the conservative content
 * budget a view should size its children against (panel insets + the always-present vertical
 * scrollbar already subtracted), and {@link #WRAP_WIDTH} is the fixed {@code <html>} body width every
 * multi-line label uses so text wraps instead of clipping.
 */
public final class SidebarMetrics
{
    private SidebarMetrics() {}

    /** Usable content width (px) a view should cap its children at - a little under the real panel
     *  width once insets and the scrollbar are gone. */
    public static final int PANEL_WIDTH = 215;

    /** Fixed {@code <html>} body width (px) for wrapped labels across the plugin. */
    public static final int WRAP_WIDTH = 175;

    /** Narrowest the user can drag the panel (see {@code OsrsCopilotPanel}'s resize handle). */
    public static final int MIN_WIDTH = 205;

    /** Widest the user can drag the panel. */
    public static final int MAX_WIDTH = 850;

    /**
     * Wraps {@code text} in the standard fixed-width {@code <html>} body every multi-line side-panel
     * label uses. The text is passed through as-is (callers deliberately embed {@code <b>}, {@code <i>}
     * and {@code <br>}), so only hand it content you trust - not raw user input.
     */
    public static String htmlWrap(String text)
    {
        return "<html><body style='width:" + WRAP_WIDTH + "px'>" + (text != null ? text : "") + "</body></html>";
    }
}
