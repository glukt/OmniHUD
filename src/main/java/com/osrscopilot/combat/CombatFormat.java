package com.osrscopilot.combat;

/** Shared number / duration formatting for the combat feature (was copy-pasted ~10 times). */
public final class CombatFormat
{
    private CombatFormat()
    {
    }

    /** {@code MM:SS} (or {@code H:MM:SS} past an hour). */
    public static String duration(int totalSeconds)
    {
        int s = Math.max(0, totalSeconds);
        int h = s / 3600;
        int m = (s % 3600) / 60;
        int sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }

    public static String duration(double seconds)
    {
        return duration((int) Math.round(seconds));
    }

    /** Compact magnitude: {@code 42}, {@code 1.2k}, {@code 3.4M}. */
    public static String amount(long v)
    {
        long a = Math.abs(v);
        if (a >= 1_000_000)
        {
            return String.format("%.1fM", v / 1_000_000.0);
        }
        if (a >= 1_000)
        {
            return String.format("%.1fk", v / 1_000.0);
        }
        return Long.toString(v);
    }
}
