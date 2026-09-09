package com.osrscopilot.combat;

/**
 * A chat channel the combat summary can be copied "for". The prefix is what OSRS uses to route a
 * typed line to that channel ({@code ""} public, {@code "/"} friends, {@code "//"} clan); the
 * plugin only puts {@code prefix + summary} on the clipboard - the player pastes and sends it.
 */
public enum ShareChannel
{
    PUBLIC("", "Public"),
    FRIENDS("/", "Friends chat"),
    CLAN("//", "Clan chat");

    private final String prefix;
    private final String label;

    ShareChannel(String prefix, String label)
    {
        this.prefix = prefix;
        this.label = label;
    }

    public String prefix()
    {
        return prefix;
    }

    public String label()
    {
        return label;
    }
}
