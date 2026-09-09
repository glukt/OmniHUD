package com.osrscopilot.combat.engine;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Test-side reader for the {@code capture-*.jsonl} files that {@link CombatEventRecorder} writes.
 * Groundwork for the Phase-2 replay/verification rig: parse a real capture into rows, pull out the
 * per-tick event stream, and drive the ledger / reconciler through it in tests.
 *
 * <p>Deliberately dumb: each JSONL line is one {@link JsonObject}. Helpers below just navigate it.
 */
public final class CombatCaptureReader
{
    private static final Gson GSON = new Gson();

    private CombatCaptureReader()
    {
    }

    /** Every line of the capture as a JsonObject, in order (session / mark / t / session-end). */
    public static List<JsonObject> readLines(Path jsonl)
    {
        try
        {
            List<JsonObject> out = new ArrayList<>();
            for (String line : Files.readAllLines(jsonl, StandardCharsets.UTF_8))
            {
                if (!line.isBlank())
                {
                    out.add(GSON.fromJson(line, JsonObject.class));
                }
            }
            return out;
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    /** Just the per-tick rows ({@code "ty":"t"}). */
    public static List<JsonObject> ticks(List<JsonObject> lines)
    {
        return lines.stream()
            .filter(o -> o.has("ty") && "t".equals(o.get("ty").getAsString()))
            .collect(Collectors.toList());
    }

    /** The {@code ev[]} array of one tick row, flattened to JsonObjects (empty if none). */
    public static List<JsonObject> events(JsonObject tickRow)
    {
        List<JsonObject> out = new ArrayList<>();
        if (tickRow.has("ev") && tickRow.get("ev").isJsonArray())
        {
            tickRow.getAsJsonArray("ev").forEach(el -> out.add(el.getAsJsonObject()));
        }
        return out;
    }

    /** All events of a given kind across the whole capture, in order. */
    public static List<JsonObject> eventsOfKind(List<JsonObject> lines, String kind)
    {
        List<JsonObject> out = new ArrayList<>();
        for (JsonObject t : ticks(lines))
        {
            for (JsonObject ev : events(t))
            {
                if (ev.has("e") && kind.equals(ev.get("e").getAsString()))
                {
                    out.add(ev);
                }
            }
        }
        return out;
    }

    /** Sum of Hitpoints-XP deltas over the capture ({@code base} anchors excluded). */
    public static long hitpointsXpGained(List<JsonObject> lines)
    {
        long total = 0;
        for (JsonObject xp : eventsOfKind(lines, "xp"))
        {
            if ("Hitpoints".equals(optString(xp, "sk")) && !xp.has("base"))
            {
                total += xp.get("d").getAsLong();
            }
        }
        return total;
    }

    /** Sum of {@code isMine} damage hitsplats landed on NPCs over the capture. */
    public static long myNpcDamage(List<JsonObject> lines)
    {
        long total = 0;
        for (JsonObject hit : eventsOfKind(lines, "hit"))
        {
            if (bool(hit, "mine") && bool(hit, "npc"))
            {
                total += hit.get("amt").getAsLong();
            }
        }
        return total;
    }

    private static String optString(JsonObject o, String k)
    {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static boolean bool(JsonObject o, String k)
    {
        return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean();
    }
}
