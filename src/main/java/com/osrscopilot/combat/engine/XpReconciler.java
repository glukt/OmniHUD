package com.osrscopilot.combat.engine;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.combat.model.EncounterSegment;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Observe-only cross-check: does the meter's attributed self-damage match the damage implied by
 * the player's Hitpoints XP gain? (HP XP = damage * 4/3, so damage = HP XP * 0.75.) Runs off
 * {@link CombatTickLedger}, tracks it cumulatively per fight, and writes a 0..1
 * {@code attributionConfidence} onto the {@link EncounterSegment}. Nothing here changes a
 * displayed number - it only flags drift (under = we're missing damage, e.g. dropped thrall;
 * over = we're crediting something that isn't ours, e.g. a recoil hit).
 *
 * <p>Captures (2026-09): melee HP XP is dead-on 4/3 with ~0 drift; magic runs ~2-3% high from
 * per-cast spell-XP rounding. XP and its hitsplat can be 1-3 ticks apart, but cumulatively over a
 * fight that lag just shifts the totals by a hit or two, so the running sums still converge.
 *
 * <p>Gated by {@code combatDebugCapture} (default off) - dormant in production until Phase 2.
 */
@Singleton
@Slf4j
public class XpReconciler
{
    private static final double DMG_PER_HP_XP = 0.75; // damage = hpXp * 3/4
    private static final int HEARTBEAT_TICKS = 100;

    private final CombatEncounterManager encounters;
    private final CombatTickLedger ledger;
    private final OsrsCopilotConfig config; // may be null in tests -> always enabled

    private EncounterSegment tracked;
    private long cumHpXp;
    private long cumObserved;
    private int hitCount;
    private int ticksInFight;
    private int lastConsumedTick = Integer.MIN_VALUE;
    private Report lastReport;

    @Inject
    public XpReconciler(CombatEncounterManager encounters, CombatTickLedger ledger, OsrsCopilotConfig config)
    {
        this.encounters = encounters;
        this.ledger = ledger;
        this.config = config;
    }

    /** Test seam - no config (always enabled). */
    XpReconciler(CombatEncounterManager encounters, CombatTickLedger ledger)
    {
        this(encounters, ledger, null);
    }

    private boolean enabled()
    {
        return config == null || config.combatDebugCapture();
    }

    /** Call from the plugin's onGameTick, after {@link CombatTickLedger#onGameTick()}. */
    public void onGameTick()
    {
        if (!enabled())
        {
            tracked = null;
            return;
        }
        EncounterSegment cur = encounters.getCurrentEncounter();
        if (cur != tracked)
        {
            if (tracked != null)
            {
                endFight();
            }
            if (cur != null)
            {
                beginFight(cur);
            }
        }
        if (tracked == null)
        {
            return;
        }

        // Consume ticks the ledger has finalised since we last looked (normally exactly one).
        for (CombatTick t : ledger.recent(6))
        {
            if (t.getTick() <= lastConsumedTick)
            {
                continue;
            }
            lastConsumedTick = t.getTick();
            cumHpXp += t.getDHpXp();
            cumObserved += t.myWeaponDamage();
            for (CombatTick.Splat s : t.getMyHits())
            {
                if (s.isSelfDamage() && s.getAmount() > 0)
                {
                    hitCount++;
                }
            }
        }

        ticksInFight++;
        double conf = confidence();
        tracked.setAttributionConfidence(conf);

        if (ticksInFight % HEARTBEAT_TICKS == 0)
        {
            log.info("[xp-recon] {} (running): observed={} expected={} hits={} conf={}% [{}]",
                targetName(tracked), cumObserved, expected(), hitCount, pct(conf), verdict());
        }
    }

    private void beginFight(EncounterSegment s)
    {
        tracked = s;
        cumHpXp = 0;
        cumObserved = 0;
        hitCount = 0;
        ticksInFight = 0;
        List<CombatTick> newest = ledger.recent(1);
        // Start from the newest tick already in the ledger so we don't re-consume pre-fight ticks;
        // the fight's own first tick is pushed by the ledger before this reconciler runs.
        lastConsumedTick = newest.isEmpty() ? Integer.MIN_VALUE : newest.get(0).getTick() - 1;
    }

    private void endFight()
    {
        double conf = confidence();
        Report r = new Report(targetName(tracked), cumObserved, expected(), hitCount, conf, verdict());
        lastReport = r;
        tracked.setAttributionConfidence(conf);
        log.info("[xp-recon] {}: observed={} expected={} (dHP-xp={}) hits={} conf={}% [{}]",
            r.target, r.observed, r.expected, cumHpXp, r.hitCount, pct(conf), r.verdict);
        tracked = null;
    }

    // ------------------------------------------------------------------ maths

    private long expected()
    {
        return Math.round(cumHpXp * DMG_PER_HP_XP);
    }

    private double tolerance()
    {
        return 3.0 + 0.06 * hitCount; // melee drift ~0, magic ~2-3%; this covers both with margin
    }

    private double confidence()
    {
        long exp = expected();
        if (exp <= 0)
        {
            return 1.0;
        }
        double excess = Math.max(0.0, Math.abs(cumObserved - exp) - tolerance());
        return Math.max(0.0, 1.0 - excess / exp);
    }

    private String verdict()
    {
        long delta = cumObserved - expected();
        if (delta < -tolerance())
        {
            return "UNDER"; // meter is missing self-damage (dropped thrall / cannon / hit)
        }
        if (delta > tolerance())
        {
            return "OVER"; // meter is crediting damage that isn't the player's (recoil / veng / mislabel)
        }
        return "OK";
    }

    // ------------------------------------------------------------------ read side

    public Report getLastReport()
    {
        return lastReport;
    }

    private static String targetName(EncounterSegment s)
    {
        return s == null || s.getTargetName() == null ? "?" : s.getTargetName();
    }

    private static long pct(double c)
    {
        return Math.round(c * 100);
    }

    /** Result of reconciling one fight. */
    public static final class Report
    {
        public final String target;
        public final long observed;
        public final long expected;
        public final int hitCount;
        public final double confidence;
        public final String verdict;

        Report(String target, long observed, long expected, int hitCount, double confidence, String verdict)
        {
            this.target = target;
            this.observed = observed;
            this.expected = expected;
            this.hitCount = hitCount;
            this.confidence = confidence;
            this.verdict = verdict;
        }
    }
}
