package com.osrscopilot.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlayerReward
{
    public enum RewardType
    {
        UNLOCK("Unlock"),
        EXTEND("Extension"),
        COSMETIC("Cosmetic"),
        BUY("Buy"),
        MANAGEMENT("Task management");

        private final String displayName;

        RewardType(String displayName)
        {
            this.displayName = displayName;
        }

        public String getDisplayName()
        {
            return displayName;
        }
    }

    private String name;
    private RewardType type;
    private int cost;
    private String description;
    private int varbitId;
    private boolean unlocked;
}
