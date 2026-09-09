package com.osrscopilot.combat.engine;

import com.osrscopilot.OsrsCopilotConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.StatChanged;

/**
 * Assembles a per-tick {@link CombatTick} stream from the raw client events - the structure the
 * Phase-2 attribution rework and {@link XpReconciler} run on. <b>Shadow mode:</b> nothing here
 * feeds the live meter; it only observes. All callbacks are on the client thread (RuneLite event
 * bus + the plugin's {@code onGameTick}), so no locking.
 *
 * <p>Gated by the {@code combatDebugCapture} config toggle (default off) - dormant in production
 * until Phase 2 wires the ledger into the meter. Strict no-op when off.
 */
@Singleton
@Slf4j
public class CombatTickLedger
{
    private static final int MAX_TICKS = 10_000; // ~100 min of ticks; a raid is well under
    private static final Skill[] TRACKED = {
        Skill.HITPOINTS, Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC,
    };

    private final Client client;
    private final SpellAttackResolver spellResolver; // may be null in tests
    private final OsrsCopilotConfig config;          // may be null in tests -> always enabled

    private final Deque<CombatTick> ring = new ArrayDeque<>();
    private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);
    private boolean xpSeeded;
    private int lastHp = -1;
    private CombatTick current;

    @Inject
    public CombatTickLedger(Client client, SpellAttackResolver spellResolver, OsrsCopilotConfig config)
    {
        this.client = client;
        this.spellResolver = spellResolver;
        this.config = config;
    }

    /** Test seam - no SpellAttackResolver / config (always enabled). */
    CombatTickLedger(Client client)
    {
        this(client, null, null);
    }

    private boolean enabled()
    {
        return config == null || config.combatDebugCapture();
    }

    // ------------------------------------------------------------------ events

    public void onHitsplatApplied(HitsplatApplied e)
    {
        if (!enabled() || e == null)
        {
            return;
        }
        Hitsplat hs = e.getHitsplat();
        Actor a = e.getActor();
        if (hs == null || a == null)
        {
            return;
        }
        Player me = client.getLocalPlayer();
        if (a == me)
        {
            cur().addIncoming(new CombatTick.Splat("me", hs.getAmount(), hs.getHitsplatType()));
        }
        else if (hs.isMine() && a instanceof NPC)
        {
            cur().addMyHit(new CombatTick.Splat(npcTag((NPC) a), hs.getAmount(), hs.getHitsplatType()));
        }
    }

    public void onStatChanged(StatChanged e)
    {
        if (!enabled() || e == null)
        {
            return;
        }
        Skill sk = e.getSkill();
        if (!tracked(sk))
        {
            return;
        }
        seedXpIfNeeded();
        Integer prev = lastXp.put(sk, e.getXp());
        int d = prev == null ? 0 : e.getXp() - prev;
        if (d == 0)
        {
            return;
        }
        CombatTick c = cur();
        switch (sk)
        {
            case HITPOINTS: c.dHpXp += d; break;
            case ATTACK:    c.dAtkXp += d; break;
            case STRENGTH:  c.dStrXp += d; break;
            case DEFENCE:   c.dDefXp += d; break;
            case RANGED:    c.dRngXp += d; break;
            case MAGIC:     c.dMagXp += d; break;
            default: break;
        }
    }

    public void onAnimationChanged(AnimationChanged e)
    {
        if (!enabled() || e == null || e.getActor() != client.getLocalPlayer())
        {
            return;
        }
        int anim = e.getActor().getAnimation();
        if (anim != -1)
        {
            CombatTick c = cur();
            c.attacked = true;
            c.playerAnim = anim;
        }
    }

    /** Call from the plugin's onGameTick to finalise the tick just past. */
    public void onGameTick()
    {
        if (!enabled())
        {
            if (!ring.isEmpty() || current != null)
            {
                clear(); // dropped to off - discard the shadow state
            }
            return;
        }
        if (current == null)
        {
            return;
        }
        finalise(current);
        push(current);
        current = null;
    }

    // ------------------------------------------------------------------ read side

    /** The last {@code n} finalised ticks, oldest first. O(n) - does not copy the whole ring. */
    public List<CombatTick> recent(int n)
    {
        List<CombatTick> out = new ArrayList<>(Math.min(Math.max(n, 0), ring.size()));
        Iterator<CombatTick> it = ring.descendingIterator();
        while (it.hasNext() && out.size() < n)
        {
            out.add(it.next());
        }
        Collections.reverse(out);
        return out;
    }

    /** Finalised ticks with {@code lo <= tick <= hi}, oldest first. */
    public List<CombatTick> range(int lo, int hi)
    {
        List<CombatTick> out = new ArrayList<>();
        for (CombatTick t : ring)
        {
            if (t.getTick() >= lo && t.getTick() <= hi)
            {
                out.add(t);
            }
        }
        return out;
    }

    public int size()
    {
        return ring.size();
    }

    public void clear()
    {
        ring.clear();
        lastXp.clear();
        xpSeeded = false;
        lastHp = -1;
        current = null;
    }

    // ------------------------------------------------------------------ internals

    private CombatTick cur()
    {
        int t = client.getTickCount();
        if (current == null || current.getTick() != t)
        {
            if (current != null)
            {
                // a tick with events but no onGameTick between (shouldn't normally happen) - flush it
                finalise(current);
                push(current);
            }
            current = new CombatTick(t);
        }
        return current;
    }

    private void finalise(CombatTick c)
    {
        if (spellResolver != null && c.attacked)
        {
            SpellAttackResolver.AttackResolution a = spellResolver.resolveCurrentAttack(c.getTick());
            if (a != null)
            {
                c.attackName = a.getAttackName();
                c.attackStyle = a.getStyle();
                c.spec = a.isSpecial();
            }
        }
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
            int max = client.getRealSkillLevel(Skill.HITPOINTS);
            c.hp = hp;
            c.maxHp = max;
            c.dHp = lastHp < 0 ? 0 : hp - lastHp;
            lastHp = hp;
            try
            {
                c.specEnergyPct = client.getVarpValue(300) / 10;
            }
            catch (RuntimeException ignored)
            {
                // varp not ready
            }
        }
    }

    private void push(CombatTick c)
    {
        ring.addLast(c);
        while (ring.size() > MAX_TICKS)
        {
            ring.removeFirst();
        }
    }

    private void seedXpIfNeeded()
    {
        if (xpSeeded)
        {
            return;
        }
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            for (Skill s : TRACKED)
            {
                lastXp.put(s, client.getSkillExperience(s));
            }
            xpSeeded = true;
        }
    }

    private static boolean tracked(Skill sk)
    {
        return sk == Skill.HITPOINTS || sk == Skill.ATTACK || sk == Skill.STRENGTH
            || sk == Skill.DEFENCE || sk == Skill.RANGED || sk == Skill.MAGIC;
    }

    private static String npcTag(NPC n)
    {
        return n.getName() + "#" + n.getIndex();
    }
}
