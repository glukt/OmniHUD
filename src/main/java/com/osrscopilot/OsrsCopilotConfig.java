package com.osrscopilot;

import com.osrscopilot.data.model.MembershipFilter;
import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("osrscopilot")
public interface OsrsCopilotConfig extends Config
{
    @ConfigSection(
        name = "Display Markers",
        description = "Settings for map marker visibility, styling, and alignment",
        position = 0
    )
    String markersSection = "markersSection";

    @ConfigItem(
        keyName = "alignToNativeIcons",
        name = "Align to Native Shop Icons",
        description = "Snap coordinates directly over native OSRS map symbols for seamless integration",
        section = markersSection,
        position = 2
    )
    default boolean alignToNativeIcons()
    {
        return true;
    }

    @ConfigItem(
        keyName = "highlightNativeIcons",
        name = "Highlight Native Map Icons",
        description = "For shops already shown by a native OSRS world map icon, draw a soft glowing halo around it instead of a second plugin marker. Off by default - the halo does not always line up cleanly with the native icon.",
        section = markersSection,
        position = 3
    )
    default boolean highlightNativeIcons()
    {
        return false;
    }

    @ConfigItem(
        keyName = "showMinimapVendors",
        name = "Show Vendors on Minimap",
        description = "Display nearby vendor orbs on the minimap",
        section = markersSection,
        position = 4
    )
    default boolean showMinimapVendors()
    {
        return true;
    }

    @ConfigItem(
        keyName = "enableTownMarkers",
        name = "Show Town Hubs",
        description = "Display town and settlement markers on the world map",
        section = markersSection,
        position = 6
    )
    default boolean enableTownMarkers()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showVendorIcons",
        name = "Show Shop Vendors",
        description = "Display individual vendor and shopkeeper icons on the world map",
        section = markersSection,
        position = 7
    )
    default boolean showVendorIcons()
    {
        return true;
    }

    @ConfigItem(
        keyName = "snapToEdge",
        name = "Edge Snapping",
        description = "Snap markers to the edge of the world map screen when out of view",
        section = markersSection,
        position = 8
    )
    default boolean snapToEdge()
    {
        return false;
    }

    @ConfigItem(
        keyName = "showMonsterZones",
        name = "Show Monster Spawn Zones",
        description = "Draw monster spawn-zone boxes / dungeon-entrance portals on the world map when a monster is focused from the Bestiary. Also toggled by the map legend's Monsters button; this setting is what persists.",
        section = markersSection,
        position = 9
    )
    default boolean showMonsterZones()
    {
        return true;
    }

    @ConfigSection(
        name = "Ironman & Stock Filters",
        description = "Filters for Ironman buying rules, zero-stock items, and restrictions",
        position = 10
    )
    String ironmanSection = "ironmanSection";

    @ConfigItem(
        keyName = "hideZeroStock",
        name = "Hide 0-Stock Items",
        description = "Excludes inventory listings that have 0 stock by default (Ironmen cannot purchase items with 0 default stock)",
        section = ironmanSection,
        position = 11
    )
    default boolean hideZeroStock()
    {
        return true;
    }

    @ConfigItem(
        keyName = "hideIronmanRestricted",
        name = "Hide Ironman-Restricted Items",
        description = "Hides items completely blocked for Ironman accounts (e.g. NMZ resource boxes, PvP crates)",
        section = ironmanSection,
        position = 12
    )
    default boolean hideIronmanRestricted()
    {
        return true;
    }

    @ConfigSection(
        name = "General Settings",
        description = "Filter shops by membership or distance",
        position = 20
    )
    String generalSection = "generalSection";

    @ConfigItem(
        keyName = "membershipFilter",
        name = "Membership Filter",
        description = "Show all shops, F2P only, or P2P only",
        section = generalSection,
        position = 21
    )
    default MembershipFilter membershipFilter()
    {
        return MembershipFilter.ALL;
    }

    @ConfigItem(
        keyName = "showDetailedTooltips",
        name = "Detailed Shop Tooltips",
        description = "Show item names, stock counts, and prices on hover",
        section = generalSection,
        position = 22
    )
    default boolean showDetailedTooltips()
    {
        return true;
    }

    @Range(min = 0, max = 2000)
    @ConfigItem(
        keyName = "searchDistanceLimit",
        name = "Distance Limit",
        description = "Maximum distance in tiles to show shops from player (0 for unlimited)",
        section = generalSection,
        position = 23
    )
    default int searchDistanceLimit()
    {
        return 0;
    }

    @ConfigItem(
        keyName = "showMonsterArt",
        name = "Load monster art from the OSRS Wiki",
        description = "Fetch monster portraits from oldschool.runescape.wiki for the Bestiary. "
            + "Turn off to keep a plain silhouette and make no external requests.",
        section = generalSection,
        position = 24
    )
    default boolean showMonsterArt()
    {
        return true;
    }

    @ConfigSection(
        name = "Combat & Encounter Meter (HUD)",
        description = "Behaviour of the in-game combat & encounter meter HUD. Bar / overlay "
            + "cosmetics are set from the HUD's own settings card (right-click the meter -> "
            + "\"Meter settings\").",
        position = 30
    )
    String combatSection = "combatSection";

    @ConfigItem(
        keyName = "showCombatOverlay",
        name = "Show Combat HUD",
        description = "Display the floating combat meter overlay on the game canvas",
        section = combatSection,
        position = 31
    )
    default boolean showCombatOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showMinimapCombatButton",
        name = "Show Combat Button on Minimap",
        description = "Display the Combat Meter / Details shortcut button on the minimap rim",
        section = combatSection,
        position = 32
    )
    default boolean showMinimapCombatButton()
    {
        return true;
    }

    @Range(min = 1, max = 10)
    @ConfigItem(
        keyName = "maxCombatBars",
        name = "Max Visible Bars",
        description = "Maximum number of damage bars shown in the HUD (1-10)",
        section = combatSection,
        position = 34
    )
    default int maxCombatBars()
    {
        return 3;
    }

    @ConfigItem(
        keyName = "combatAutoHide",
        name = "Dim HUD When Idle",
        description = "Fades the combat HUD to half opacity while you're out of combat. It never fully hides - every control stays readable and clickable.",
        section = combatSection,
        position = 33
    )
    default boolean combatAutoHide()
    {
        return false;
    }

    @Range(min = 20, max = 100)
    @ConfigItem(
        keyName = "combatOverlayOpacity",
        name = "Overlay Opacity %",
        description = "Background opacity of the HUD overlay window",
        section = combatSection,
        position = 37,
        hidden = true // set from the HUD settings card
    )
    default int combatOverlayOpacity()
    {
        return 88;
    }

    @Range(min = 160, max = 420)
    @ConfigItem(
        keyName = "combatOverlayWidth",
        name = "Overlay Width (px)",
        description = "Width of the HUD overlay window. The height is derived automatically from bar size x bar count.",
        section = combatSection,
        position = 35,
        hidden = true // set from the HUD settings card
    )
    default int combatOverlayWidth()
    {
        return 220;
    }

    @ConfigItem(
        keyName = "combatShowBossProgress",
        name = "Boss fight progress line",
        description = "While fighting a single monster with a health bar, show its HP%, an estimated "
            + "time-to-kill and an on-pace kills/hour under the bars.",
        section = combatSection,
        position = 50
    )
    default boolean combatShowBossProgress()
    {
        return true;
    }

    @ConfigItem(
        keyName = "combatShowMiniFooter",
        name = "Show Mini-Stat Footer",
        description = "Display damage taken, healing, consumables used, and duration below bars",
        section = combatSection,
        position = 49
    )
    default boolean combatShowMiniFooter()
    {
        return true;
    }

    @Range(min = 0, max = 3600)
    @ConfigItem(
        keyName = "combatSessionTimeout",
        name = "Auto-reset Current Session after idle (s)",
        description = "Current Session resets on logout. Set this to also reset it after N seconds out of combat (0 = off).",
        section = combatSection,
        position = 53
    )
    default int combatSessionTimeout()
    {
        return 0;
    }

    @Range(min = 6, max = 120)
    @ConfigItem(
        keyName = "combatEncounterTimeout",
        name = "End a fight after (s) idle",
        description = "A fight is considered over this many seconds after your last hit. Raise it for phase bosses (Vorkath acid walk, Zulrah dive) so one kill isn't split into several rows.",
        section = combatSection,
        position = 54
    )
    default int combatEncounterTimeout()
    {
        return 20;
    }

    @Range(min = 5, max = 100)
    @ConfigItem(
        keyName = "combatSegmentHistoryDepth",
        name = "Past fights to keep",
        description = "How many finished fights the 'Past fights' list keeps browsable in the scope dropdown. Older fights still count toward Current Session and Total.",
        section = combatSection,
        position = 55
    )
    default int combatSegmentHistoryDepth()
    {
        return 25;
    }

    @ConfigItem(
        keyName = "combatMergeTrash",
        name = "Merge repeat kills",
        description = "Trash merge: consecutive kills of the same monster fold into one 'Gargoyle x47' past-fight row instead of one row each. Turn off to keep every kill separate (e.g. boss splits).",
        section = combatSection,
        position = 56
    )
    default boolean combatMergeTrash()
    {
        return true;
    }

    @Range(min = 1, max = 30)
    @ConfigItem(
        keyName = "combatGraphSmoothing",
        name = "Graph smoothing (s)",
        description = "Rolling-average window for the fight graph's DPS / DTPS / HPS lines. 1 = raw per-second (very spiky); higher = smoother.",
        section = combatSection,
        position = 52,
        hidden = true // set from the HUD settings card
    )
    default int combatGraphSmoothing()
    {
        return 6;
    }

    @ConfigItem(
        keyName = "combatGraphStyle",
        name = "Graph line style",
        description = "How the fight-graph curve is drawn.",
        section = combatSection,
        position = 51,
        hidden = true // set from the HUD settings card
    )
    default GraphStyleOption combatGraphStyle()
    {
        return GraphStyleOption.AREA;
    }

    enum GraphStyleOption
    {
        LINE, AREA, BARS
    }

    @ConfigItem(
        keyName = "combatBarTexture",
        name = "Bar texture",
        description = "Overlay drawn on top of the bar fill.",
        section = combatSection,
        position = 41,
        hidden = true // set from the HUD settings card
    )
    default BarTextureOption combatBarTexture()
    {
        return BarTextureOption.GLOSS;
    }

    enum BarTextureOption
    {
        NONE, GLOSS, STRIPED
    }

    @ConfigItem(
        keyName = "combatBarValueFormat",
        name = "Bar right-side text",
        description = "What each bar shows on its right edge.",
        section = combatSection,
        position = 44
    )
    default BarValueFormat combatBarValueFormat()
    {
        return BarValueFormat.RATE_AMOUNT_PERCENT;
    }

    enum BarValueFormat
    {
        RATE_AMOUNT_PERCENT, AMOUNT_PERCENT, AMOUNT, PERCENT, RATE
    }

    @ConfigItem(
        keyName = "combatBarFont",
        name = "Bar font",
        description = "Font for the bar labels.",
        section = combatSection,
        position = 45,
        hidden = true // set from the HUD settings card
    )
    default BarFontOption combatBarFont()
    {
        return BarFontOption.SMALL;
    }

    enum BarFontOption
    {
        SMALL, REGULAR, BOLD
    }

    @ConfigItem(
        keyName = "combatBarShowRank",
        name = "Show bar rank number",
        description = "Prefix each bar with its rank (\"1. \", \"2. \", ...).",
        section = combatSection,
        position = 46,
        hidden = true // set from the HUD settings card
    )
    default boolean combatBarShowRank()
    {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "combatBarCustomColor",
        name = "Custom bar colour",
        description = "Used when Palette Accent is set to Custom.",
        section = combatSection,
        position = 40
    )
    default Color combatBarCustomColor()
    {
        return new Color(255, 183, 77);
    }

    @Range(min = 0, max = 8)
    @ConfigItem(
        keyName = "combatBarCorner",
        name = "Bar corner radius (px)",
        description = "Rounded-corner radius of each bar (0 = square).",
        section = combatSection,
        position = 42,
        hidden = true // set from the HUD settings card
    )
    default int combatBarCorner()
    {
        return 4;
    }

    @Range(min = 0, max = 6)
    @ConfigItem(
        keyName = "combatBarGap",
        name = "Bar spacing (px)",
        description = "Vertical gap between bars.",
        section = combatSection,
        position = 43,
        hidden = true // set from the HUD settings card
    )
    default int combatBarGap()
    {
        return 2;
    }

    @ConfigItem(
        keyName = "combatBarTextShadow",
        name = "Bar text shadow",
        description = "Draw a dark shadow behind bar labels so they stay readable over bright fills.",
        section = combatSection,
        position = 47,
        hidden = true // set from the HUD settings card
    )
    default boolean combatBarTextShadow()
    {
        return true;
    }

    @ConfigItem(
        keyName = "combatBarShowStyleIcon",
        name = "Style dot on bars",
        description = "Show a small combat-style colour dot at the left of each bar.",
        section = combatSection,
        position = 48,
        hidden = true // set from the HUD settings card
    )
    default boolean combatBarShowStyleIcon()
    {
        return false;
    }

    enum HeaderModeOption
    {
        FULL("Full"),
        MINIMAL("Minimal"),
        HIDDEN("Hidden");

        private final String name;
        HeaderModeOption(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    @ConfigItem(
        keyName = "combatHeaderMode",
        name = "HUD header",
        description = "Full = all controls; Minimal = metric + scope only; Hidden = bars only "
            + "(right-click the meter for settings). Only Small/Regular/Bold bar fonts are available.",
        section = combatSection,
        position = 32,
        hidden = true // set from the HUD settings card
    )
    default HeaderModeOption combatHeaderMode()
    {
        return HeaderModeOption.FULL;
    }

    enum BarStyleOption
    {
        GRADIENT("Gradient"),
        SOLID("Solid"),
        CLASS_STYLE("Class Style");

        private final String name;
        BarStyleOption(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    enum PaletteAccentOption
    {
        DYNAMIC_STYLE("Dynamic / Type"),
        DRAGON_RED("Dragon Red"),
        SARADOMIN_BLUE("Saradomin Blue"),
        ZULRAH_TEAL("Zulrah Teal"),
        VOID_PURPLE("Void Purple"),
        TOA_GOLD("ToA Gold"),
        CUSTOM("Custom");

        private final String name;
        PaletteAccentOption(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    @ConfigItem(
        keyName = "combatBarStyle",
        name = "Bar Style",
        description = "Visual rendering style of the progress bars",
        section = combatSection,
        position = 38,
        hidden = true // set from the HUD settings card
    )
    default BarStyleOption combatBarStyle()
    {
        return BarStyleOption.GRADIENT;
    }

    @ConfigItem(
        keyName = "combatPaletteAccent",
        name = "Palette Accent",
        description = "Color accent palette for the HUD overlay",
        section = combatSection,
        position = 39,
        hidden = true // set from the HUD settings card
    )
    default PaletteAccentOption combatPaletteAccent()
    {
        return PaletteAccentOption.TOA_GOLD;
    }

    @Range(min = 10, max = 44)
    @ConfigItem(
        keyName = "combatBarHeight",
        name = "Bar Height (px)",
        description = "Pixel height of each progress bar in the HUD (10-44). The overlay resizes itself to fit.",
        section = combatSection,
        position = 36,
        hidden = true // set from the HUD settings card
    )
    default int combatBarHeight()
    {
        return 20;
    }

    @ConfigItem(
        keyName = "combatMultiTargetTagging",
        name = "Multi-Target Tagging",
        description = "Distinguishes and numbers identical NPCs in multi-combat (e.g. Cave horror (1), Cave horror (2))",
        section = combatSection,
        position = 57
    )
    default boolean combatMultiTargetTagging()
    {
        return true;
    }

    @ConfigItem(
        keyName = "combatPartyEnabled",
        name = "Group damage meter",
        description = "Share your combat contribution with your RuneLite party and automatically show a combined meter (you + each member's damage dealt, damage taken and splits) whenever someone in the party is fighting - no need to pick a 'Group' scope. Needs RuneLite's Party plugin. Turn off to stop sharing your numbers.",
        section = combatSection,
        position = 58
    )
    default boolean combatPartyEnabled()
    {
        return true;
    }

    @ConfigItem(
        keyName = "combatPartyShowOffline",
        name = "Keep offline members listed",
        description = "In the Group scope, keep a member's row (greyed, marked stale) after their updates stop, instead of dropping it.",
        section = combatSection,
        position = 59
    )
    default boolean combatPartyShowOffline()
    {
        return true;
    }

    @ConfigItem(
        keyName = "combatDebugCapture",
        name = "Debug: combat diagnostics",
        description = "Developer diagnostic, off by default, no effect on the meter. Turns on: (1) a "
            + "tick-by-tick event capture to RUNELITE_DIR/osrscopilot/combat/capture-<time>.jsonl "
            + "(+ a [CAPTURE] line per event-bearing tick in the client log); (2) the per-tick "
            + "combat ledger; (3) the XP-vs-attributed-damage reconciler, which logs "
            + "[xp-recon] <target>: observed=.. expected=.. conf=..% [OK|UNDER|OVER] at each "
            + "fight's end. Also toggled with ::ccap on / ::ccap off.",
        section = combatSection,
        position = 90
    )
    default boolean combatDebugCapture()
    {
        return false;
    }

    // ===================== Combat State Banner ("Entering / Left Combat") ==============

    @ConfigSection(
        name = "Combat State Banner",
        description = "Optional on-screen 'Entering Combat' / 'Left Combat' flash",
        position = 40
    )
    String combatBannerSection = "combatBannerSection";

    enum BannerFontStyle { SMALL, REGULAR, BOLD }
    enum BannerAnchor { TOP, CENTER }
    enum BannerAnimation { FADE, POP, SCALE_OUT, SLIDE_DOWN, SLIDE_UP, PULSE }

    @ConfigItem(keyName = "combatBannerEnabled", name = "Enable banner", description = "Show a brief flash when you enter or leave combat", section = combatBannerSection, position = 1)
    default boolean combatBannerEnabled() { return false; }

    @ConfigItem(keyName = "combatBannerShowLeaving", name = "Show 'left combat'", description = "Also flash when combat ends (not just when it starts)", section = combatBannerSection, position = 2)
    default boolean combatBannerShowLeaving() { return true; }

    @ConfigItem(keyName = "combatBannerEnterText", name = "Entering text", description = "Text shown when combat starts", section = combatBannerSection, position = 3)
    default String combatBannerEnterText() { return "Entering Combat"; }

    @ConfigItem(keyName = "combatBannerLeaveText", name = "Leaving text", description = "Text shown when combat ends", section = combatBannerSection, position = 4)
    default String combatBannerLeaveText() { return "Left Combat"; }

    @ConfigItem(keyName = "combatBannerFontStyle", name = "Font style", description = "Bitmap font weight for the banner", section = combatBannerSection, position = 5)
    default BannerFontStyle combatBannerFontStyle() { return BannerFontStyle.BOLD; }

    @Range(min = 10, max = 40)
    @ConfigItem(keyName = "combatBannerFontSize", name = "Font size", description = "Banner text size in points", section = combatBannerSection, position = 6)
    default int combatBannerFontSize() { return 18; }

    @ConfigItem(keyName = "combatBannerTextShadow", name = "Text shadow", description = "Draw a 1px drop shadow behind the banner text", section = combatBannerSection, position = 7)
    default boolean combatBannerTextShadow() { return true; }

    @net.runelite.client.config.Alpha
    @ConfigItem(keyName = "combatBannerEnterColor", name = "Entering colour", description = "Colour of the 'entering combat' text", section = combatBannerSection, position = 8)
    default java.awt.Color combatBannerEnterColor() { return new java.awt.Color(255, 78, 50); }

    @net.runelite.client.config.Alpha
    @ConfigItem(keyName = "combatBannerLeaveColor", name = "Leaving colour", description = "Colour of the 'left combat' text", section = combatBannerSection, position = 9)
    default java.awt.Color combatBannerLeaveColor() { return new java.awt.Color(159, 184, 160); }

    @ConfigItem(keyName = "combatBannerAnchor", name = "Anchor", description = "TOP measures from the top of the screen, CENTER from the middle. Both use the offsets below.", section = combatBannerSection, position = 10)
    default BannerAnchor combatBannerAnchor() { return BannerAnchor.TOP; }

    @Range(min = 0, max = 100)
    @ConfigItem(keyName = "combatBannerOffsetYPct", name = "Vertical position %", description = "Distance from the top of the screen (0 = top, 50 = centre) when anchor is TOP", section = combatBannerSection, position = 11)
    default int combatBannerOffsetYPct() { return 22; }

    @Range(min = -400, max = 400)
    @ConfigItem(keyName = "combatBannerOffsetX", name = "Horizontal offset", description = "Pixels left / right of centre", section = combatBannerSection, position = 12)
    default int combatBannerOffsetX() { return 0; }

    @ConfigItem(keyName = "combatBannerAnimation", name = "Animation", description = "How the banner appears", section = combatBannerSection, position = 13)
    default BannerAnimation combatBannerAnimation() { return BannerAnimation.FADE; }

    @Range(min = 0, max = 1000)
    @ConfigItem(keyName = "combatBannerFadeInMs", name = "Fade-in (ms)", description = "How long the banner takes to fade in", section = combatBannerSection, position = 14)
    default int combatBannerFadeInMs() { return 150; }

    @Range(min = 200, max = 5000)
    @ConfigItem(keyName = "combatBannerLingerMs", name = "Hold (ms)", description = "How long the banner stays fully visible", section = combatBannerSection, position = 15)
    default int combatBannerLingerMs() { return 1200; }

    @Range(min = 0, max = 2000)
    @ConfigItem(keyName = "combatBannerFadeOutMs", name = "Fade-out (ms)", description = "How long the banner takes to fade away", section = combatBannerSection, position = 16)
    default int combatBannerFadeOutMs() { return 400; }

    // "Preview banner" (one-shot flash) and "Reposition banner" (placement sample) are reachable
    // from the Combat Meter settings card, not as config toggles - a boolean that unticks itself
    // or that is really a mode is discouraged by Plugin-Hub review.
}



