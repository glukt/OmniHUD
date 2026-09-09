package com.osrscopilot.combat.engine;

import com.osrscopilot.combat.model.CombatStyle;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.HitsplatID;

/**
 * One game tick of combat, assembled by {@link CombatTickLedger} from the raw client events.
 * Shadow-mode only for now - nothing reads this into the live meter yet; it is the structure the
 * Phase-2 attribution work and {@link XpReconciler} run on.
 *
 * <p>Captures (2026-09) established the timing: the XP drop fires on the <em>attack</em> tick
 * (swing / cast), and the hitsplat renders {@code attackTick + projectileFlight + 1} ticks later
 * (melee +1, magic Wave +2..+3). So a hitsplat here and the XP that paid for it can land on
 * different {@code CombatTick}s - callers must correlate over a small window, not per tick.
 */
public final class CombatTick
{
    private final int tick;

    // Loose "I started an attack this tick" signal: the local player's attack animation fired.
    // Refined in Phase 2; for now it can false-positive on emotes / eating.
    boolean attacked;
    int playerAnim = -1;
    String attackName;
    CombatStyle attackStyle;
    boolean spec;

    // Hitsplats that landed this tick.
    private final List<Splat> myHits = new ArrayList<>();   // isMine(), target is an NPC
    private final List<Splat> incoming = new ArrayList<>(); // landed on the local player

    // XP gained this tick, per skill.
    int dHpXp;
    int dAtkXp;
    int dStrXp;
    int dDefXp;
    int dRngXp;
    int dMagXp;

    // Hitpoints level (points, not XP) change this tick - for the Phase-2 healing classifier.
    int dHp;
    int hp;
    int maxHp;
    int specEnergyPct;

    CombatTick(int tick)
    {
        this.tick = tick;
    }

    void addMyHit(Splat s)
    {
        myHits.add(s);
    }

    void addIncoming(Splat s)
    {
        incoming.add(s);
    }

    public int getTick()
    {
        return tick;
    }

    public boolean wasAttack()
    {
        return attacked;
    }

    public String getAttackName()
    {
        return attackName;
    }

    public CombatStyle getAttackStyle()
    {
        return attackStyle;
    }

    public boolean isSpec()
    {
        return spec;
    }

    public List<Splat> getMyHits()
    {
        return myHits;
    }

    public List<Splat> getIncoming()
    {
        return incoming;
    }

    public int getDHpXp()
    {
        return dHpXp;
    }

    /** Whichever combat skill got the 4x drop this tick, 0 if none - a style cross-check. */
    public int getDStyleXp()
    {
        return Math.max(Math.max(dAtkXp, dStrXp), Math.max(dDefXp, Math.max(dRngXp, dMagXp)));
    }

    public int getDAtkXp()
    {
        return dAtkXp;
    }

    public int getDStrXp()
    {
        return dStrXp;
    }

    public int getDDefXp()
    {
        return dDefXp;
    }

    public int getDRngXp()
    {
        return dRngXp;
    }

    public int getDMagXp()
    {
        return dMagXp;
    }

    public int getDHp()
    {
        return dHp;
    }

    /** Sum of this tick's landed hitsplats that count as self weapon/spell damage (gave HP XP). */
    public long myWeaponDamage()
    {
        long s = 0;
        for (Splat h : myHits)
        {
            if (h.isSelfDamage())
            {
                s += h.amount;
            }
        }
        return s;
    }

    /** Sum of damage taken this tick. */
    public long incomingDamage()
    {
        long s = 0;
        for (Splat h : incoming)
        {
            if (h.amount > 0)
            {
                s += h.amount;
            }
        }
        return s;
    }

    /** One landed hitsplat. */
    public static final class Splat
    {
        final String target; // "Name#idx" for an NPC, "me" for the local player
        final int amount;
        final int type;       // HitsplatID.*

        Splat(String target, int amount, int type)
        {
            this.target = target;
            this.amount = amount;
            this.type = type;
        }

        public String getTarget()
        {
            return target;
        }

        public int getAmount()
        {
            return amount;
        }

        public int getType()
        {
            return type;
        }

        public boolean isMax()
        {
            return type == HitsplatID.DAMAGE_MAX_ME || type == HitsplatID.DAMAGE_MAX_ME_CYAN
                || type == HitsplatID.DAMAGE_MAX_ME_ORANGE || type == HitsplatID.DAMAGE_MAX_ME_YELLOW
                || type == HitsplatID.DAMAGE_MAX_ME_WHITE || type == HitsplatID.DAMAGE_MAX_ME_POISE;
        }

        /** A normal "you dealt this" splat (any colour), i.e. one that awarded the player HP XP. */
        public boolean isSelfDamage()
        {
            switch (type)
            {
                case HitsplatID.DAMAGE_ME:
                case HitsplatID.DAMAGE_ME_CYAN:
                case HitsplatID.DAMAGE_ME_ORANGE:
                case HitsplatID.DAMAGE_ME_YELLOW:
                case HitsplatID.DAMAGE_ME_WHITE:
                case HitsplatID.DAMAGE_ME_POISE:
                case HitsplatID.DAMAGE_MAX_ME:
                case HitsplatID.DAMAGE_MAX_ME_CYAN:
                case HitsplatID.DAMAGE_MAX_ME_ORANGE:
                case HitsplatID.DAMAGE_MAX_ME_YELLOW:
                case HitsplatID.DAMAGE_MAX_ME_WHITE:
                case HitsplatID.DAMAGE_MAX_ME_POISE:
                    return true;
                default:
                    return false;
            }
        }
    }
}
