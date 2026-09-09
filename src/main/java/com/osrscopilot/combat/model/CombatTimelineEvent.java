package com.osrscopilot.combat.model;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * One line in the combat action ledger / full log.
 *
 * <p>{@link #description} is a ready-to-render human string ("Dealt 22 (Dragon scimitar)"); the
 * structured fields ({@link #sourceName}, {@link #targetName}, {@link #style}, {@link #weaponOrSpell},
 * {@link #miss}, {@link #special}, {@link #amount}, {@link #wallClockMillis}) carry the same
 * information un-formatted so the log can search, filter per source/target, show an absolute
 * timestamp per line, and (later) re-render in a verbose mode. Build new events with
 * {@link #builder()}; the positional constructors are kept for the simpler event types.
 */
public class CombatTimelineEvent
{
    private static final DateTimeFormatter WALL_CLOCK =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter WALL_CLOCK_DATE =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Sentinel for "this event carries no numeric amount" (fight boundaries, CC lines, ...). */
    public static final int NO_AMOUNT = -1;

    private final int gameTick;
    private final String timeFormatted;
    private final String eventType;
    private final String icon;
    private final String description;
    private final Color color;
    /** Real-world time the event was recorded, epoch millis. */
    private final long wallClockMillis;
    /** Structured hit/heal size for this event; {@link #NO_AMOUNT} when not applicable. */
    private final int amount;

    private final String sourceName;
    private final String targetName;
    private final CombatStyle style;
    private final String weaponOrSpell;
    private final boolean miss;
    private final boolean special;

    public CombatTimelineEvent(int gameTick, String timeFormatted, String eventType, String icon, String description, Color color)
    {
        this(gameTick, timeFormatted, eventType, icon, description, color, NO_AMOUNT);
    }

    public CombatTimelineEvent(int gameTick, String timeFormatted, String eventType, String icon, String description, Color color, int amount)
    {
        this(new Builder()
            .clientTick(gameTick).timeFormatted(timeFormatted).eventType(eventType).icon(icon)
            .description(description).color(color).amount(amount));
    }

    private CombatTimelineEvent(Builder b)
    {
        this.gameTick = b.gameTick;
        this.timeFormatted = b.timeFormatted;
        this.eventType = b.eventType;
        this.icon = b.icon;
        this.description = b.description;
        this.color = b.color;
        this.amount = b.amount;
        this.wallClockMillis = b.wallClockMillis > 0 ? b.wallClockMillis : System.currentTimeMillis();
        this.sourceName = b.sourceName;
        this.targetName = b.targetName;
        this.style = b.style;
        this.weaponOrSpell = b.weaponOrSpell;
        this.miss = b.miss;
        this.special = b.special;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    /** Real-world clock time the event was recorded (HH:mm:ss, local zone). */
    public String getWallClock()
    {
        return WALL_CLOCK.format(Instant.ofEpochMilli(wallClockMillis));
    }

    /** Real-world date + clock (yyyy-MM-dd HH:mm:ss) - for a saved log reviewed later. */
    public String getWallClockWithDate()
    {
        return WALL_CLOCK_DATE.format(Instant.ofEpochMilli(wallClockMillis));
    }

    public long getWallClockMillis()
    {
        return wallClockMillis;
    }

    public int getGameTick()
    {
        return gameTick;
    }

    public String getTimeFormatted()
    {
        return timeFormatted;
    }

    public String getEventType()
    {
        return eventType;
    }

    public String getIcon()
    {
        return icon;
    }

    public String getDescription()
    {
        return description;
    }

    public Color getColor()
    {
        return color;
    }

    /** Hit/heal size for this event, or {@link #NO_AMOUNT} (-1) when the event has no numeric value. */
    public int getAmount()
    {
        return amount;
    }

    /** Who performed the action ("You" / an NPC name), or null for events with no clear actor. */
    public String getSourceName()
    {
        return sourceName;
    }

    /** Who the action landed on ("You" / an NPC name), or null. */
    public String getTargetName()
    {
        return targetName;
    }

    public CombatStyle getStyle()
    {
        return style;
    }

    public String getWeaponOrSpell()
    {
        return weaponOrSpell;
    }

    public boolean isMiss()
    {
        return miss;
    }

    public boolean isSpecial()
    {
        return special;
    }

    /** Lower-cased haystack for the log's free-text search. */
    public String searchText()
    {
        StringBuilder sb = new StringBuilder();
        if (description != null) sb.append(description).append(' ');
        if (sourceName != null) sb.append(sourceName).append(' ');
        if (targetName != null) sb.append(targetName).append(' ');
        if (weaponOrSpell != null) sb.append(weaponOrSpell).append(' ');
        if (style != null) sb.append(style.getDisplayName()).append(' ');
        if (eventType != null) sb.append(eventType);
        return sb.toString().toLowerCase();
    }

    public static final class Builder
    {
        private int gameTick;
        private String timeFormatted = "";
        private String eventType = "";
        private String icon = " ";
        private String description = "";
        private Color color = Color.LIGHT_GRAY;
        private long wallClockMillis = 0L;
        private int amount = NO_AMOUNT;
        private String sourceName;
        private String targetName;
        private CombatStyle style;
        private String weaponOrSpell;
        private boolean miss;
        private boolean special;

        public Builder clientTick(int t) { this.gameTick = t; return this; }
        public Builder timeFormatted(String s) { this.timeFormatted = s; return this; }
        public Builder eventType(String s) { this.eventType = s; return this; }
        public Builder icon(String s) { this.icon = s; return this; }
        public Builder description(String s) { this.description = s; return this; }
        public Builder color(Color c) { this.color = c; return this; }
        public Builder wallClockMillis(long m) { this.wallClockMillis = m; return this; }
        public Builder amount(int a) { this.amount = a; return this; }
        public Builder source(String s) { this.sourceName = s; return this; }
        public Builder target(String s) { this.targetName = s; return this; }
        public Builder style(CombatStyle s) { this.style = s; return this; }
        public Builder weaponOrSpell(String s) { this.weaponOrSpell = s; return this; }
        public Builder miss(boolean b) { this.miss = b; return this; }
        public Builder special(boolean b) { this.special = b; return this; }

        public CombatTimelineEvent build()
        {
            return new CombatTimelineEvent(this);
        }
    }
}
