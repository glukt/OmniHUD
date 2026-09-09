package com.osrscopilot.combat.engine;

import com.google.gson.Gson;
import com.osrscopilot.util.CopilotPaths;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;

/**
 * Diagnostic capture harness for the combat-accuracy work. Opt-in ({@code combatDebugCapture}
 * config toggle, or {@code ::ccap on}); off by default and a strict no-op when off.
 *
 * <p>While recording it writes one JSON object per game tick to
 * {@code RUNELITE_DIR/osrscopilot/combat/capture-&lt;timestamp&gt;.jsonl} - the local player's state,
 * the current target's health bar, and every combat-relevant event that fired during that tick
 * (hitsplats, animations, projectiles, graphics, interaction changes, XP drops, a whitelist of
 * varbits, deaths, game chat). A compact {@code [CAPTURE]} line goes to the client log for the
 * ticks that had events. Nothing here reads or feeds the live meter - it only observes.
 *
 * <p>Every game event is dispatched on the client thread; {@link #start()}/{@link #stop()} are
 * marshalled onto it, so the whole recorder is single-threaded and each public event method
 * returns on the {@code capturing} flag before touching anything. Recorder failures are swallowed
 * so a capture bug can never disturb the meter or any other plugin.
 */
@Singleton
@Slf4j
public class CombatEventRecorder
{
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_LINES = 50_000;
    private static final int QUIET_TAIL_TICKS = 10; // keep logging this many ticks after the last event
    private static final int NEAR_TILES = 8;    // radius for the per-tick "nearby combat NPCs" snapshot
    private static final int MAX_NEAR = 10;
    private static final int SPEC_VARP = 300;   // special-attack energy, 0-1000
    private static final int POISON_VARP = 102; // VarPlayer.POISON
    private static final int THRALL_VARBIT = 12411;
    private static final int VENG_VARBIT = 2450;
    private static final int AUTOCAST_VARBIT = 276;
    private static final Skill[] TRACKED_SKILLS = {
        Skill.HITPOINTS, Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC,
    };

    private final Client client;
    private final ClientThread clientThread; // null in unit tests -> run inline
    private final SpellAttackResolver spellResolver; // null in unit tests -> attack field omitted

    private volatile boolean capturing;
    private BufferedWriter writer;
    private File file;
    private long lines;
    private int lastTickWritten = Integer.MIN_VALUE;
    private int lastEventTick = Integer.MIN_VALUE;
    private boolean weaponLogged;

    private final Gson gson;
    private final List<Map<String, Object>> tickBuf = new ArrayList<>();
    private final Map<Skill, Integer> lastXp = new HashMap<>();
    private final Map<Integer, Integer> lastProjTick = new HashMap<>();
    private final Map<String, String> lastInteract = new HashMap<>();

    /** Test-only override for the capture directory; null uses {@link CopilotPaths}. */
    private File outputDirOverride;

    @Inject
    public CombatEventRecorder(Client client, ClientThread clientThread, SpellAttackResolver spellResolver, Gson gson)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.spellResolver = spellResolver;
        this.gson = gson.newBuilder().serializeNulls().create();
    }

    /** Test seam - no ClientThread / SpellAttackResolver. */
    CombatEventRecorder(Client client, Gson gson)
    {
        this(client, null, null, gson);
    }

    void setOutputDirForTest(File dir)
    {
        this.outputDirOverride = dir;
    }

    // ------------------------------------------------------------------ control

    public boolean isCapturing()
    {
        return capturing;
    }

    public void start()
    {
        onClient(this::doStart);
    }

    public void stop()
    {
        onClient(this::doStop);
    }

    public void toggle()
    {
        onClient(() ->
        {
            if (capturing)
            {
                doStop();
            }
            else
            {
                doStart();
            }
        });
    }

    public void mark(String label)
    {
        onClient(() ->
        {
            if (!capturing)
            {
                log.info("[CAPTURE] (not recording) ignored mark: {}", label);
                return;
            }
            Map<String, Object> m = base("mark");
            m.put("label", label == null ? "" : label);
            writeLine(m);
            log.info("[CAPTURE] mark: {}", label);
        });
    }

    private void onClient(Runnable r)
    {
        if (clientThread != null)
        {
            clientThread.invoke(r); // runs inline when already on the client thread, else next tick
        }
        else
        {
            r.run();
        }
    }

    private void doStart()
    {
        if (capturing)
        {
            return;
        }
        BufferedWriter w = null;
        try
        {
            File dir = outputDirOverride != null ? outputDirOverride : CopilotPaths.dataSubDir("combat");
            if (!dir.isDirectory() && !dir.mkdirs())
            {
                log.warn("[CAPTURE] could not create {}", dir);
                return;
            }
            file = new File(dir, "capture-" + LocalDateTime.now().format(STAMP) + ".jsonl");
            w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            writer = w;
            lines = 0;
            lastTickWritten = Integer.MIN_VALUE;
            lastEventTick = Integer.MIN_VALUE;
            weaponLogged = false;
            tickBuf.clear();
            lastXp.clear();
            lastProjTick.clear();
            lastInteract.clear();
            // Seed XP baselines from the current totals so the first real drop isn't swallowed as a
            // "base" anchor. Only when logged in - otherwise the first drop would diff against 0.
            if (client.getGameState() == GameState.LOGGED_IN)
            {
                for (Skill sk : TRACKED_SKILLS)
                {
                    lastXp.put(sk, client.getSkillExperience(sk));
                }
            }

            Map<String, Object> head = base("session");
            head.put("world", client.getWorld());
            head.put("rsn", localName());
            Map<String, Object> w0 = equippedWeapon();
            head.put("weapon", w0);
            weaponLogged = w0 != null;
            writeLineTo(w, head);
            capturing = true;
            log.info("[CAPTURE] started -> {}", file);
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("[CAPTURE] failed to start", e);
            if (w != null)
            {
                closeQuietly(w);
            }
            writer = null;
            capturing = false;
        }
    }

    private void doStop()
    {
        if (!capturing)
        {
            return;
        }
        capturing = false;
        try
        {
            flushTick();
            writeLine(base("session-end"));
        }
        catch (RuntimeException e)
        {
            log.debug("[CAPTURE] error on final flush", e);
        }
        closeQuietly(writer);
        writer = null;
        log.info("[CAPTURE] stopped -> {} ({} lines)", file, lines);
    }

    // ------------------------------------------------------------------ events

    public void onHitsplatApplied(HitsplatApplied e)
    {
        if (!capturing || e == null)
        {
            return;
        }
        safe(() ->
        {
            Hitsplat hs = e.getHitsplat();
            if (hs == null)
            {
                return;
            }
            Map<String, Object> ev = ev("hit");
            ev.put("who", actorTag(e.getActor()));
            ev.put("npc", e.getActor() instanceof NPC);
            ev.put("amt", hs.getAmount());
            ev.put("type", hs.getHitsplatType());
            ev.put("tname", hitsplatName(hs.getHitsplatType()));
            ev.put("mine", hs.isMine());
            ev.put("others", hs.isOthers());
            buffer(ev);
        });
    }

    public void onAnimationChanged(AnimationChanged e)
    {
        if (!capturing || e == null)
        {
            return;
        }
        safe(() ->
        {
            if (!interesting(e.getActor()))
            {
                return;
            }
            int anim = e.getActor().getAnimation();
            if (anim == -1)
            {
                return;
            }
            Map<String, Object> ev = ev("anim");
            ev.put("who", actorTag(e.getActor()));
            ev.put("id", anim);
            buffer(ev);
        });
    }

    public void onGraphicChanged(GraphicChanged e)
    {
        if (!capturing || e == null)
        {
            return;
        }
        safe(() ->
        {
            if (!interesting(e.getActor()))
            {
                return;
            }
            int g = e.getActor().getGraphic();
            if (g == -1)
            {
                return;
            }
            Map<String, Object> ev = ev("gfx");
            ev.put("who", actorTag(e.getActor()));
            ev.put("id", g);
            buffer(ev);
        });
    }

    public void onProjectileMoved(ProjectileMoved e)
    {
        if (!capturing || e == null)
        {
            return;
        }
        safe(() ->
        {
            Projectile p = e.getProjectile();
            if (p == null)
            {
                return;
            }
            // ProjectileMoved fires every cycle a projectile is in flight; log only the first
            // sighting. Keyed by id + a >1-tick gap so a genuine new shot of the same id later
            // is still recorded (dozens of ids max, so the map stays tiny).
            int tick = client.getTickCount();
            Integer seen = lastProjTick.put(p.getId(), tick);
            if (seen != null && tick - seen <= 1)
            {
                return;
            }
            Map<String, Object> ev = ev("proj");
            ev.put("id", p.getId());
            ev.put("tgt", actorTag(p.getInteracting()));
            ev.put("rc", p.getRemainingCycles());
            ev.put("flightTicks", Math.round(p.getRemainingCycles() / 30.0));
            buffer(ev);
        });
    }

    public void onInteractingChanged(InteractingChanged e)
    {
        if (!capturing || e == null)
        {
            return;
        }
        safe(() ->
        {
            Actor src = e.getSource();
            Actor tgt = e.getTarget();
            Player me = client.getLocalPlayer();
            if (src != me && tgt != me && !(src instanceof NPC))
            {
                return;
            }
            String srcTag = actorTag(src);
            String tgtTag = actorTag(tgt);
            // InteractingChanged re-fires every tick a pairing holds; log only actual changes.
            if (java.util.Objects.equals(lastInteract.get(srcTag), tgtTag))
            {
                return;
            }
            lastInteract.put(srcTag, tgtTag);
            Map<String, Object> ev = ev("interact");
            ev.put("src", srcTag);
            ev.put("tgt", tgtTag);
            buffer(ev);
        });
    }

    public void onStatChanged(StatChanged e)
    {
        if (!capturing || e == null)
        {
            return;
        }
        safe(() ->
        {
            Skill sk = e.getSkill();
            if (sk != Skill.HITPOINTS && sk != Skill.ATTACK && sk != Skill.STRENGTH
                && sk != Skill.DEFENCE && sk != Skill.RANGED && sk != Skill.MAGIC)
            {
                return;
            }
            int xp = e.getXp();
            Integer prev = lastXp.put(sk, xp);
            int delta = prev == null ? 0 : xp - prev;
            if (prev != null && delta == 0)
            {
                return;
            }
            Map<String, Object> ev = ev("xp");
            ev.put("sk", sk.getName());
            ev.put("xp", xp);
            ev.put("d", delta);
            if (prev == null)
            {
                ev.put("base", true); // first sighting this capture - anchor, not a real drop
            }
            buffer(ev);
        });
    }

    public void onVarbitChanged(VarbitChanged e)
    {
        if (!capturing || e == null)
        {
            return;
        }
        safe(() ->
        {
            int varp = e.getVarpId();
            int varbit = e.getVarbitId();
            boolean want = varp == SPEC_VARP || varp == POISON_VARP
                || varbit == THRALL_VARBIT || varbit == VENG_VARBIT || varbit == AUTOCAST_VARBIT;
            if (!want)
            {
                return;
            }
            Map<String, Object> ev = ev("var");
            ev.put("varp", varp);
            ev.put("varbit", varbit);
            ev.put("val", e.getValue());
            if (varbit == AUTOCAST_VARBIT)
            {
                ev.put("autocast", autocastName(e.getValue()));
            }
            buffer(ev);
        });
    }

    public void onActorDeath(ActorDeath e)
    {
        if (!capturing || e == null)
        {
            return;
        }
        safe(() ->
        {
            Map<String, Object> ev = ev("death");
            ev.put("who", actorTag(e.getActor()));
            ev.put("npc", e.getActor() instanceof NPC);
            buffer(ev);
        });
    }

    public void onChatMessage(ChatMessage e)
    {
        if (!capturing || e == null)
        {
            return;
        }
        safe(() ->
        {
            String t = e.getType() == null ? "" : e.getType().name();
            if (!"GAMEMESSAGE".equals(t) && !"SPAM".equals(t) && !"CONSOLE".equals(t))
            {
                return;
            }
            Map<String, Object> ev = ev("chat");
            ev.put("t", t);
            ev.put("m", e.getMessage());
            buffer(ev);
        });
    }

    public void onItemContainerChanged(ItemContainerChanged e)
    {
        if (!capturing || e == null || e.getContainerId() != InventoryID.EQUIPMENT.getId())
        {
            return;
        }
        safe(() ->
        {
            Map<String, Object> weapon = equippedWeapon();
            Map<String, Object> ev = ev("equip");
            ev.put("weapon", weapon);
            buffer(ev);
        });
    }

    /** Call last in the plugin's onGameTick so the tick's events are all buffered. */
    public void onGameTick()
    {
        if (!capturing)
        {
            return;
        }
        safe(this::flushTick);
        if (capturing && lines >= MAX_LINES)
        {
            log.warn("[CAPTURE] line cap {} reached - stopping", MAX_LINES);
            doStop();
        }
    }

    // ------------------------------------------------------------------ internals

    private void safe(Runnable body)
    {
        try
        {
            body.run();
        }
        catch (RuntimeException ex)
        {
            log.debug("[CAPTURE] recorder handler threw (swallowed)", ex);
        }
    }

    private void buffer(Map<String, Object> ev)
    {
        tickBuf.add(ev);
        lastEventTick = client.getTickCount();
    }

    private void flushTick()
    {
        int tick = client.getTickCount();
        if (!weaponLogged)
        {
            // The session header ran before login, so it missed the weapon. Emit it once now.
            Map<String, Object> w = equippedWeapon();
            if (w != null)
            {
                Map<String, Object> ev = ev("equip");
                ev.put("weapon", w);
                ev.put("initial", true);
                buffer(ev);
                weaponLogged = true;
            }
        }
        Player me = client.getLocalPlayer();
        boolean targetPresent = me != null && me.getInteracting() instanceof NPC;
        boolean recentActivity = lastEventTick != Integer.MIN_VALUE && tick - lastEventTick <= QUIET_TAIL_TICKS;
        // Skip pure-idle ticks (no target, nothing happening) so a capture the user forgets to
        // stop doesn't fill with noise.
        if (tickBuf.isEmpty() && !targetPresent && !recentActivity)
        {
            return;
        }
        if (tick == lastTickWritten && tickBuf.isEmpty())
        {
            return;
        }
        lastTickWritten = tick;

        Map<String, Object> row = base("t");
        row.put("me", meState());
        row.put("attack", attackState(tick));
        row.put("tgt", targetState());
        row.put("near", nearNpcs());
        row.put("ev", new ArrayList<>(tickBuf));
        writeLine(row);

        if (!tickBuf.isEmpty())
        {
            StringBuilder types = new StringBuilder();
            for (Map<String, Object> ev : tickBuf)
            {
                types.append(types.length() == 0 ? "" : ",").append(ev.get("e"));
            }
            log.info("[CAPTURE] t={} ev={} [{}]", tick, tickBuf.size(), types);
        }
        tickBuf.clear();
    }

    private Map<String, Object> meState()
    {
        Map<String, Object> me = new LinkedHashMap<>();
        Player p = client.getLocalPlayer();
        if (p != null && p.getWorldLocation() != null)
        {
            me.put("x", p.getWorldLocation().getX());
            me.put("y", p.getWorldLocation().getY());
            me.put("p", p.getWorldLocation().getPlane());
            me.put("anim", p.getAnimation());
            me.put("pose", p.getPoseAnimation());
        }
        me.put("hp", client.getBoostedSkillLevel(Skill.HITPOINTS));
        me.put("maxhp", client.getRealSkillLevel(Skill.HITPOINTS));
        try
        {
            me.put("specPct", client.getVarpValue(SPEC_VARP) / 10);
            me.put("poison", client.getVarpValue(POISON_VARP));
        }
        catch (RuntimeException ignored)
        {
            // varp read can throw before the varps load; leave the fields off
        }
        List<String> pray = new ArrayList<>();
        for (Prayer pr : Prayer.values())
        {
            if (client.isPrayerActive(pr))
            {
                pray.add(pr.name());
            }
        }
        me.put("pray", pray);
        return me;
    }

    /**
     * Combat NPCs within {@link #NEAR_TILES} of the local player - catches thralls (which stand
     * adjacent), the mob being cannoned, and adds in a multi-pull. Filtered to "doing something"
     * so a crowded area doesn't flood the row.
     */
    private List<Map<String, Object>> nearNpcs()
    {
        Player me = client.getLocalPlayer();
        WorldPoint myWp = me == null ? null : me.getWorldLocation();
        List<Map<String, Object>> out = new ArrayList<>();
        if (myWp == null)
        {
            return out;
        }
        List<NPC> npcs = client.getNpcs();
        if (npcs == null)
        {
            return out;
        }
        NPC follower = client.getFollower();
        List<NPC> sorted = new ArrayList<>();
        for (NPC n : npcs)
        {
            if (n == null || n == follower || n.getWorldLocation() == null)
            {
                continue; // a follower pet stands adjacent and "interacts" - it's not a combatant
            }
            int dist = n.getWorldLocation().distanceTo(myWp);
            if (dist > NEAR_TILES)
            {
                continue;
            }
            String nm = n.getName();
            boolean active = n.getInteracting() != null || n.getAnimation() != -1
                || n.getHealthRatio() >= 0
                || (nm != null && nm.toLowerCase().contains("thrall"));
            if (active)
            {
                sorted.add(n);
            }
        }
        sorted.sort((a, b) -> Integer.compare(
            a.getWorldLocation().distanceTo(myWp), b.getWorldLocation().distanceTo(myWp)));
        for (int i = 0; i < sorted.size() && i < MAX_NEAR; i++)
        {
            NPC n = sorted.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("name", n.getName());
            m.put("idx", n.getIndex());
            m.put("dist", n.getWorldLocation().distanceTo(myWp));
            m.put("anim", n.getAnimation());
            m.put("gfx", n.getGraphic());
            m.put("tgt", actorTag(n.getInteracting()));
            m.put("hpr", n.getHealthRatio());
            m.put("hps", n.getHealthScale());
            out.add(m);
        }
        return out;
    }

    /** The engine's own resolution of what the player is attacking with this tick (weapon / spell / spec). */
    private Map<String, Object> attackState(int tick)
    {
        if (spellResolver == null)
        {
            return null;
        }
        SpellAttackResolver.AttackResolution a = spellResolver.resolveCurrentAttack(tick);
        if (a == null)
        {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", a.getAttackName());
        m.put("style", a.getStyle() == null ? null : a.getStyle().name());
        m.put("spec", a.isSpecial());
        return m;
    }

    private static String autocastName(int val)
    {
        switch (val)
        {
            case 1: return "Wind Strike";
            case 2: return "Water Strike";
            case 3: return "Earth Strike";
            case 4: return "Fire Strike";
            case 5: return "Wind Bolt";
            case 6: return "Water Bolt";
            case 7: return "Earth Bolt";
            case 8: return "Fire Bolt";
            case 9: return "Wind Blast";
            case 10: return "Water Blast";
            case 11: return "Earth Blast";
            case 12: return "Fire Blast";
            case 13: return "Wind Wave";
            case 14: return "Water Wave";
            case 15: return "Earth Wave";
            case 16: return "Fire Wave";
            case 48: return "Wind Surge";
            case 49: return "Water Surge";
            case 50: return "Earth Surge";
            case 51: return "Fire Surge";
            case 0: return "(none)";
            default: return "id:" + val;
        }
    }

    private Map<String, Object> targetState()
    {
        Player me = client.getLocalPlayer();
        Actor a = me == null ? null : me.getInteracting();
        if (!(a instanceof NPC))
        {
            return null;
        }
        NPC npc = (NPC) a;
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("id", npc.getId());
        t.put("name", npc.getName());
        t.put("idx", npc.getIndex());
        t.put("hpr", npc.getHealthRatio());
        t.put("hps", npc.getHealthScale());
        return t;
    }

    private Map<String, Object> base(String ty)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ty", ty);
        m.put("tick", client.getTickCount());
        m.put("ms", System.currentTimeMillis());
        return m;
    }

    private static Map<String, Object> ev(String e)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("e", e);
        return m;
    }

    private void writeLine(Map<String, Object> row)
    {
        writeLineTo(writer, row);
    }

    private void writeLineTo(BufferedWriter w, Map<String, Object> row)
    {
        if (w == null)
        {
            return;
        }
        try
        {
            w.write(gson.toJson(row));
            w.write('\n');
            w.flush();
            lines++;
        }
        catch (IOException e)
        {
            log.warn("[CAPTURE] write failed - stopping", e);
            closeQuietly(w);
            if (w == writer)
            {
                writer = null;
            }
            capturing = false;
        }
    }

    private static void closeQuietly(BufferedWriter w)
    {
        if (w != null)
        {
            try
            {
                w.close();
            }
            catch (IOException ignored)
            {
                // best effort
            }
        }
    }

    private boolean interesting(Actor a)
    {
        if (a == null)
        {
            return false;
        }
        Player me = client.getLocalPlayer();
        if (a == me || a instanceof NPC)
        {
            return true;
        }
        if (a instanceof Player)
        {
            return a.getInteracting() == me || (me != null && me.getInteracting() == a);
        }
        return false;
    }

    private String actorTag(Actor a)
    {
        if (a == null)
        {
            return null;
        }
        if (a == client.getLocalPlayer())
        {
            return "me";
        }
        if (a instanceof NPC)
        {
            return a.getName() + "#" + ((NPC) a).getIndex();
        }
        if (a instanceof Player)
        {
            return ((Player) a).getName();
        }
        return a.getName();
    }

    private String localName()
    {
        Player p = client.getLocalPlayer();
        return p != null ? p.getName() : null;
    }

    private Map<String, Object> equippedWeapon()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return null;
        }
        try
        {
            ItemContainer eq = client.getItemContainer(InventoryID.EQUIPMENT);
            if (eq == null)
            {
                return null;
            }
            Item w = eq.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
            if (w == null)
            {
                return null;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.getId());
            m.put("name", client.getItemDefinition(w.getId()).getName());
            return m;
        }
        catch (RuntimeException e)
        {
            log.debug("[CAPTURE] weapon read failed", e);
            return null;
        }
    }

    private static String hitsplatName(int type)
    {
        switch (type)
        {
            case HitsplatID.DAMAGE_ME: return "DAMAGE_ME";
            case HitsplatID.DAMAGE_OTHER: return "DAMAGE_OTHER";
            case HitsplatID.DAMAGE_ME_CYAN: return "DAMAGE_ME_CYAN";
            case HitsplatID.DAMAGE_OTHER_CYAN: return "DAMAGE_OTHER_CYAN";
            case HitsplatID.DAMAGE_ME_ORANGE: return "DAMAGE_ME_ORANGE";
            case HitsplatID.DAMAGE_OTHER_ORANGE: return "DAMAGE_OTHER_ORANGE";
            case HitsplatID.DAMAGE_ME_YELLOW: return "DAMAGE_ME_YELLOW";
            case HitsplatID.DAMAGE_OTHER_YELLOW: return "DAMAGE_OTHER_YELLOW";
            case HitsplatID.DAMAGE_ME_WHITE: return "DAMAGE_ME_WHITE";
            case HitsplatID.DAMAGE_OTHER_WHITE: return "DAMAGE_OTHER_WHITE";
            case HitsplatID.DAMAGE_ME_POISE: return "DAMAGE_ME_POISE";
            case HitsplatID.DAMAGE_OTHER_POISE: return "DAMAGE_OTHER_POISE";
            case HitsplatID.DAMAGE_MAX_ME: return "DAMAGE_MAX_ME";
            case HitsplatID.DAMAGE_MAX_ME_CYAN: return "DAMAGE_MAX_ME_CYAN";
            case HitsplatID.DAMAGE_MAX_ME_ORANGE: return "DAMAGE_MAX_ME_ORANGE";
            case HitsplatID.DAMAGE_MAX_ME_YELLOW: return "DAMAGE_MAX_ME_YELLOW";
            case HitsplatID.DAMAGE_MAX_ME_WHITE: return "DAMAGE_MAX_ME_WHITE";
            case HitsplatID.DAMAGE_MAX_ME_POISE: return "DAMAGE_MAX_ME_POISE";
            case HitsplatID.BLOCK_ME: return "BLOCK_ME";
            case HitsplatID.BLOCK_OTHER: return "BLOCK_OTHER";
            case HitsplatID.POISON: return "POISON";
            case HitsplatID.VENOM: return "VENOM";
            case HitsplatID.BURN: return "BURN";
            case HitsplatID.BLEED: return "BLEED";
            case HitsplatID.HEAL: return "HEAL";
            case HitsplatID.DISEASE: return "DISEASE";
            case HitsplatID.DISEASE_BLOCKED: return "DISEASE_BLOCKED";
            case HitsplatID.CORRUPTION: return "CORRUPTION";
            case HitsplatID.PRAYER_DRAIN: return "PRAYER_DRAIN";
            case HitsplatID.DOOM: return "DOOM";
            case HitsplatID.SANITY_DRAIN: return "SANITY_DRAIN";
            case HitsplatID.SANITY_RESTORE: return "SANITY_RESTORE";
            case HitsplatID.CYAN_UP: return "CYAN_UP";
            case HitsplatID.CYAN_DOWN: return "CYAN_DOWN";
            default: return "?"; // raw int is emitted alongside
        }
    }
}
