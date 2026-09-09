package com.osrscopilot.combat.party;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * One party member's own contribution to the shared fight, broadcast on a throttled cadence.
 * Idempotent - a member's row is rebuilt from their latest snapshot, so a dropped message just
 * means a briefly-stale row that the next snapshot corrects. Kept small (~175 bytes) to stay well
 * inside the Party message budget.
 *
 * <p>{@code memberId} (the authenticated sender) is filled in by {@code PartyService.send()}.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class RemoteCombatSnapshot extends PartyMemberMessage
{
    /** Shared-encounter id all members' snapshots for one fight agree on ("" until negotiated). */
    private String sharedId = "";
    /** Resolved boss / target name of the shared fight. */
    private String boss = "";
    /** Sender's display name (identity is memberId; this is for the label only). */
    private String rsn = "";
    /** Sender's plugin version, for graceful feature gating (new fields default to 0). */
    private String ver = "";

    /**
     * Sender's world tile, so a peer can decide "is this teammate in the same fight as me" by
     * distance as well as target name. All zero from an older peer that doesn't send a location -
     * readers treat that as "unknown" and fall back to name-match only.
     */
    private int worldX;
    private int worldY;
    private int worldPlane;

    private int combatSeconds;
    private long totalDamage;
    private long dmgMelee;
    private long dmgRanged;
    private long dmgMagic;
    private long dmgTaken;
    private int healed;
    private int deaths;
    private int maxHit;
    private int attempts;
    private int hits;
    private int specs;
    /** True while the sender is actively in the fight (false = banking / dead / disengaged). */
    private boolean inCombat;

    /** Top few weapons / spells that made up this member's damage - the drill-down payload. */
    private List<WeaponSlice> weapons;

    @Data
    public static class WeaponSlice
    {
        private String name = "";
        private long dmg;
        private int hits;
        private int max;
    }
}
