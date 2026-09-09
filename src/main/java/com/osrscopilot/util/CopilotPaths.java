package com.osrscopilot.util;

import java.io.File;
import net.runelite.client.RuneLite;

/**
 * Single source of truth for the plugin's on-disk data location.
 *
 * <p>Every persistent artefact - the per-character loot stores, the wiki NPC-portrait cache and
 * the CSV exports - lives under {@link #DATA_DIR}. Defining it once here stops the directory name
 * being re-hardcoded in each manager (which is how it drifted before) and gives the one-time
 * post-rename move a single target to reason about.
 */
public final class CopilotPaths
{
    /** Directory name under {@code RUNELITE_DIR}. */
    public static final String DATA_DIR_NAME = "osrscopilot";

    /** {@code RUNELITE_DIR/osrscopilot} - the root of all persistent plugin data. */
    public static final File DATA_DIR = new File(RuneLite.RUNELITE_DIR, DATA_DIR_NAME);

    private CopilotPaths()
    {
    }

    /** A named sub-directory of {@link #DATA_DIR} (e.g. {@code loot}, {@code portraits}, {@code exports}). */
    public static File dataSubDir(String name)
    {
        return new File(DATA_DIR, name);
    }
}
