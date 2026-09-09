package com.osrscopilot.combat.engine;

import com.osrscopilot.combat.model.CombatStyle;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Varbits;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ProjectileMoved;

@Singleton
@Slf4j
public class DamageAttributionEngine
{
    private final Client client;
    private final SpellAttackResolver spellResolver;
    private final Map<Actor, Deque<PendingHit>> pendingHits = new HashMap<>();

    @Inject
    public DamageAttributionEngine(Client client, SpellAttackResolver spellResolver)
    {
        this.client = client;
        this.spellResolver = spellResolver;
    }

    public DamageAttributionEngine(Client client)
    {
        this(client, new SpellAttackResolver(client));
    }

    public void onProjectileMoved(ProjectileMoved event)
    {
        if (client == null) return;
        Projectile proj = event.getProjectile();
        if (proj == null) return;


        int projId = proj.getId();
        // Detect Cannonball Projectiles (53 = regular, 1445 = granite)
        if (projId == 53 || projId == 1445)
        {
            Actor target = proj.getInteracting();
            if (target != null)
            {
                int remainingTicks = Math.max(1, proj.getRemainingCycles() / 30);
                int arrivalTick = client.getTickCount() + remainingTicks;
                pendingHits.computeIfAbsent(target, k -> new ArrayDeque<>())
                    .add(new PendingHit(CombatStyle.CANNON, arrivalTick, "Dwarf Multicannon"));
            }
        }
    }

    public ProcessedHit processHitsplat(HitsplatApplied event, int currentTick)
    {
        if (client == null || event == null) return null;

        Actor target = event.getActor();
        Hitsplat hitsplat = event.getHitsplat();
        if (target == null || hitsplat == null) return null;

        int hitsplatType = hitsplat.getHitsplatType();
        int amount = hitsplat.getAmount();
        Player localPlayer = client.getLocalPlayer();

        // 1. Status Effects (Poison, Venom, Burn, Bleed)
        CombatStyle statusStyle = resolveStatusEffect(hitsplatType);
        if (statusStyle != null)
        {
            boolean isPlayerVictim = (target == localPlayer);
            return new ProcessedHit(
                isPlayerVictim ? HitTarget.PLAYER_TAKEN : HitTarget.NPC_DEALT,
                statusStyle,
                amount,
                statusStyle.getDisplayName(),
                false,
                target
            );
        }

        // 2. Incoming Damage or Healing on Local Player
        if (target == localPlayer)
        {
            if (hitsplatType == HitsplatID.HEAL)
            {
                return new ProcessedHit(HitTarget.PLAYER_HEAL, CombatStyle.OTHER, amount, "Lifesteal / Passive", false, target);
            }
            boolean isMiss = (hitsplatType == HitsplatID.BLOCK_ME || hitsplatType == HitsplatID.BLOCK_OTHER || amount == 0);
            return new ProcessedHit(HitTarget.PLAYER_TAKEN, CombatStyle.OTHER, amount, "Enemy", isMiss, target);
        }


        // 3. Thrall Damage Attribution Check (isOthers() with 0-3 dmg).
        // Only consumes a *non-cannon* PendingHit - a cannonball's PendingHit must be left for the
        // isMine() cannon check below, or an isOthers() small hit would steal it and the real
        // cannonball would then be misbooked as a main-hand weapon hit.
        //
        // TODO(combat-accuracy): known limitation, in progress. This branch is effectively dead -
        // nothing enqueues a non-cannon PendingHit (only onProjectileMoved does, for cannonballs),
        // so thrall damage is not attributed yet. Planned fix: track the local thrall NPC directly
        // (NpcSpawned + varbit 12411 + adjacency), match its hitsplats by target + 4-tick cadence,
        // and reconcile self-damage against the Hitpoints XP drop.
        if (hitsplat.isOthers() && amount <= 3)
        {
            Deque<PendingHit> queue = pendingHits.get(target);
            if (queue != null && !queue.isEmpty()
                && queue.peek().style != CombatStyle.CANNON
                && queue.peek().arrivalTick <= currentTick)
            {
                PendingHit pending = queue.poll();
                return new ProcessedHit(HitTarget.THRALL_DEALT, pending.style, amount, pending.source, amount == 0, target);
            }
        }

        // 4. Cannon Damage Check. No arrivalTick guard here on purpose - the isMine() +
        // head-is-CANNON test is already specific, and the tick estimate from remainingCycles can
        // be a tick early/late; gating on it would drop real cannon hits into the weapon breakdown.
        //
        // Also treat a 0-damage splat on the NPC the player is currently attacking as our miss:
        // RuneLite does not always flag a splashed / rolled-0 hit as isMine(), and without this it
        // would fall through to `return null` and vanish from hit-accuracy entirely.
        boolean playerAttackingThis = localPlayer != null && localPlayer.getInteracting() == target;
        if (hitsplat.isMine() || (playerAttackingThis && amount == 0 && !hitsplat.isOthers()))
        {
            Deque<PendingHit> queue = pendingHits.get(target);
            if (queue != null && !queue.isEmpty() && queue.peek().style == CombatStyle.CANNON)
            {
                // TODO(combat-accuracy): known limitation, in progress. Your own weapon hit on the
                // NPC you are cannoning pops this queued CANNON pending and is misbooked as cannon
                // damage (total unaffected, weapon-vs-cannon split wrong). Planned fix tightens the
                // arrival-tick / "is this my current attack" gate.
                PendingHit pending = queue.poll();
                return new ProcessedHit(HitTarget.CANNON_DEALT, CombatStyle.CANNON, amount, pending.source, amount == 0, target);
            }

            // Standard Player Direct Attack (Resolved with true spell/spec/weapon name)
            boolean isMiss = (hitsplatType == HitsplatID.BLOCK_ME || amount == 0);
            SpellAttackResolver.AttackResolution attack = spellResolver != null
                ? spellResolver.resolveCurrentAttack(currentTick)
                : new SpellAttackResolver.AttackResolution(resolveEquippedWeaponName(), resolvePlayerCombatStyle(), false);

            return new ProcessedHit(HitTarget.PLAYER_DEALT, attack.getStyle(), amount, attack.getAttackName(), isMiss, target, attack.isSpecial());
        }

        // TODO(combat-accuracy): known limitation, in progress. Recoil / ring of recoil / Vengeance
        // reflect land here as isOthers() amount > 3 and are dropped - not attributed to the player.
        return null;
    }

    public void cleanExpiredPendingHits(int currentTick)
    {
        pendingHits.values().forEach(queue -> queue.removeIf(hit -> currentTick - hit.arrivalTick > 10));
        pendingHits.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private CombatStyle resolveStatusEffect(int hitsplatType)
    {
        switch (hitsplatType)
        {
            case HitsplatID.POISON:
                return CombatStyle.POISON;
            case HitsplatID.VENOM:
                return CombatStyle.VENOM;
            case HitsplatID.BURN:
                return CombatStyle.BURN;
            case HitsplatID.BLEED:
                return CombatStyle.BLEED;
            default:
                return null;
        }
    }

    private CombatStyle resolvePlayerCombatStyle()
    {
        try
        {
            ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
            if (equipment == null) return CombatStyle.MELEE;

            Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
            if (weapon == null) return CombatStyle.MELEE;

            int weaponType = client.getVarbitValue(Varbits.EQUIPPED_WEAPON_TYPE);
            // 3 = Bow, 5 = Crossbow, 7 = Thrown, 19 = Chinchompa, 23 = Blown (Blowpipe)
            if (weaponType == 3 || weaponType == 5 || weaponType == 7 || weaponType == 19 || weaponType == 23)
            {
                return CombatStyle.RANGED;
            }
            // 18 = Staff, 21 = Powered Staff (Trident, Sang, Shadow)
            if (weaponType == 18 || weaponType == 21)
            {
                return CombatStyle.MAGIC;
            }
            return CombatStyle.MELEE;
        }
        catch (Exception e)
        {
            return CombatStyle.MELEE;
        }
    }

    private String resolveEquippedWeaponName()
    {
        try
        {
            ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
            if (equipment != null)
            {
                Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
                if (weapon != null)
                {
                    ItemComposition def = client.getItemDefinition(weapon.getId());
                    if (def != null) return def.getName();
                }
            }
        }
        catch (RuntimeException e)
        {
            log.debug("Weapon-name lookup failed", e);
        }
        return "Unarmed";
    }

    public enum HitTarget
    {
        PLAYER_DEALT,
        PLAYER_TAKEN,
        PLAYER_HEAL,
        THRALL_DEALT,
        CANNON_DEALT,
        NPC_DEALT
    }


    public static class PendingHit
    {
        final CombatStyle style;
        final int arrivalTick;
        final String source;

        PendingHit(CombatStyle style, int arrivalTick, String source)
        {
            this.style = style;
            this.arrivalTick = arrivalTick;
            this.source = source;
        }
    }

    public static class ProcessedHit
    {
        public final HitTarget targetType;
        public final CombatStyle style;
        public final int amount;
        public final String source;
        public final boolean isMiss;
        public final Actor actor;
        public final boolean isSpecial;

        public ProcessedHit(HitTarget targetType, CombatStyle style, int amount, String source, boolean isMiss, Actor actor)
        {
            this(targetType, style, amount, source, isMiss, actor, false);
        }

        public ProcessedHit(HitTarget targetType, CombatStyle style, int amount, String source, boolean isMiss, Actor actor, boolean isSpecial)
        {
            this.targetType = targetType;
            this.style = style;
            this.amount = amount;
            this.source = source;
            this.isMiss = isMiss;
            this.actor = actor;
            this.isSpecial = isSpecial;
        }
    }
}
