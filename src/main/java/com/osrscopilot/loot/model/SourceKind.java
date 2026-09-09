package com.osrscopilot.loot.model;

/**
 * How a loot drop was obtained. The mechanism only - a kill made while on a slayer task is still an
 * {@link #NPC_KILL} (the task name rides along on {@link LootRecord#getSlayerTaskName()}).
 */
public enum SourceKind
{
    NPC_KILL,
    PVP,
    PICKPOCKET,
    CHEST_CLUE,
    EVENT,
    UNKNOWN
}
