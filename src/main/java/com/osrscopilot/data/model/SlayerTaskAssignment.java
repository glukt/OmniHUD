package com.osrscopilot.data.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SlayerTaskAssignment
{
    String monster;
    int minAmount;
    int maxAmount;
    String extended;
    int weight;
    String requirement;
    String alternatives;
    String locations;

    public String getAmountDisplay()
    {
        if (minAmount > 0 && maxAmount > 0)
        {
            if (minAmount == maxAmount)
            {
                return String.valueOf(minAmount);
            }
            return minAmount + "–" + maxAmount;
        }
        return "Varies";
    }
}
