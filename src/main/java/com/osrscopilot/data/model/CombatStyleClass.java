package com.osrscopilot.data.model;

import java.util.Locale;

/**
 * Coarse classification of a monster's free-text {@code attackType} / {@code weakness} string
 * (which has ~90 / ~190 distinct raw values) into something typed and switchable. The raw strings
 * are kept on {@link Monster}; this is a derived view for callers that just need "is it melee".
 */
public enum CombatStyleClass
{
    MELEE,
    RANGED,
    MAGIC,
    MIXED,   // the text names more than one of the above
    NONE,    // explicitly none / neutral / not applicable
    UNKNOWN; // unrecognised or absent

    /**
     * Classify a raw attack-type / weakness string. Recognises the melee sub-styles (stab/slash/
     * crush), the ranged synonyms (arrow/bolt/thrown/dart), and magic (spell/dragonfire/breath).
     */
    public static CombatStyleClass classify(String raw)
    {
        if (raw == null)
        {
            return UNKNOWN;
        }
        String t = raw.toLowerCase(Locale.ROOT).trim();
        if (t.isEmpty() || t.equals("n/a") || t.equals("na") || t.contains("none") || t.contains("neutral") || t.equals("-"))
        {
            return NONE;
        }

        boolean melee = t.contains("melee") || t.contains("stab") || t.contains("slash") || t.contains("crush");
        boolean ranged = t.contains("rang") || t.contains("arrow") || t.contains("bolt")
            || t.contains("thrown") || t.contains("missile") || t.contains("dart");
        boolean magic = t.contains("magic") || t.contains("spell") || t.contains("dragonfire")
            || t.contains("dragon breath") || t.contains("breath");

        int n = (melee ? 1 : 0) + (ranged ? 1 : 0) + (magic ? 1 : 0);
        if (n >= 2)
        {
            return MIXED;
        }
        if (melee)
        {
            return MELEE;
        }
        if (ranged)
        {
            return RANGED;
        }
        if (magic)
        {
            return MAGIC;
        }
        return UNKNOWN;
    }
}
