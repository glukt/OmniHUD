package com.osrscopilot.data;

import com.osrscopilot.data.model.SlayerReward;
import com.osrscopilot.data.model.SlayerReward.RewardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

@Slf4j
@Singleton
public class SlayerRewardCatalog
{
    private static final List<SlayerReward> REWARDS = new ArrayList<>();

    static
    {
        // Names / costs / categories verified against the OSRS Wiki "Slayer Rewards" page
        // (2026-09). Varbit ids track the underlying unlock and are kept across renames; -1 =
        // no known varbit (status not shown).

        // --- 1. Unlock abilities -------------------------------------------------------------
        add("Bigger and Badder", RewardType.UNLOCK, 50, "Superior slayer monsters can spawn during tasks", 5358);
        add("Broader Fletching", RewardType.UNLOCK, 300, "Fletch broad arrows, broad bolts and amethyst broad bolts", 3208);
        add("Malevolent Masquerade", RewardType.UNLOCK, 400, "Assemble a Slayer helmet (55 Crafting)", 3202);
        add("Ring Bling", RewardType.UNLOCK, 150, "Craft a Slayer ring (75 Crafting)", 3207);
        add("Gargoyle Smasher", RewardType.UNLOCK, 120, "Auto finishing-blow gargoyles with a rock hammer in your inventory", 4027);
        add("Slug Salter", RewardType.UNLOCK, 10, "Auto finishing-blow rockslugs with a bag of salt in your inventory", 4028);
        add("Reptile Freezer", RewardType.UNLOCK, 10, "Auto finishing-blow desert lizards with an ice cooler in your inventory", 4029);
        add("'Shroom Sprayer", RewardType.UNLOCK, 110, "Auto finishing-blow mutated zygomites with fungicide spray", 4030);
        add("Duly Noted", RewardType.UNLOCK, 200, "Mithril dragons drop mithril bars banknoted on task", 4094);

        // --- monster unlocks (a master will assign them once bought) ------------------------
        add("Seeing Red", RewardType.UNLOCK, 50, "Some masters can assign red dragons", 2462);
        add("Watch the Birdie", RewardType.UNLOCK, 80, "Some masters can assign aviansie (60 Agility or Strength)", 4095);
        add("Hot Stuff", RewardType.UNLOCK, 100, "Duradel / Nieve / Chaeldar can assign TzHaar", 4691);
        add("Reptile Got Ripped", RewardType.UNLOCK, 75, "Konar / Duradel / Nieve / Chaeldar can assign Lizardmen", 4996);
        add("Basilocked", RewardType.UNLOCK, 80, "Some masters can assign Basilisks", 9456);
        add("Actual Vampyre Slayer", RewardType.UNLOCK, 80, "Some masters can assign Vampyres", 10388);
        add("Warped Reality", RewardType.UNLOCK, 60, "Some masters can assign Warped creatures (Path of Glouphrie)", 15286);
        add("Wings Spread", RewardType.UNLOCK, 80, "Nieve / Duradel can assign Gryphons", -1);
        add("Lured In", RewardType.UNLOCK, 80, "Nieve / Duradel can assign Aquanites", -1);
        add("Like a Boss", RewardType.UNLOCK, 200, "Some masters can assign boss monsters (small quantities)", 4724);
        add("Double Trouble", RewardType.UNLOCK, 500, "Killing Dusk and Dawn each count as two towards a Grotesque Guardians task", 6485);
        add("Chance of Heavy Frost", RewardType.UNLOCK, 100, "Frost dragon task weighting raised 5 -> 8 for Nieve / Duradel", -1);
        add("Stop the Wyvern", RewardType.UNLOCK, 500, "Blocks Fossil Island wyvern tasks without using a block slot", -1);
        add("I Wildy More Slayer", RewardType.UNLOCK, 0, "Krystilia can assign Jellies, Dust devils, Nechryael and Abyssal demons", -1);
        add("Task Storage", RewardType.UNLOCK, 500, "Store one Slayer task for later at no extra cost", 12442);

        // --- 2. Task extensions ------------------------------------------------------------
        add("Need More Darkness", RewardType.EXTEND, 100, "Dark beasts -> 110-135", 4031);
        add("Ankou Very Much", RewardType.EXTEND, 100, "Ankou -> 91-150", 4085);
        add("Suq-a-nother One", RewardType.EXTEND, 100, "Suqah -> 186-250", 4086);
        add("Fire & Darkness", RewardType.EXTEND, 50, "Black dragons -> 40-60", 4087);
        add("Pedal to the Metals", RewardType.EXTEND, 200, "Metal dragons -> 150-200", 4088);
        add("Bleed Me Dry", RewardType.EXTEND, 75, "Bloodveld -> 200-250", 4746);
        add("Smell Ya Later", RewardType.EXTEND, 100, "Aberrant spectres -> 200-250", 4090);
        add("To Dust You Shall Return", RewardType.EXTEND, 100, "Dust devils -> 200-250", 4751);
        add("Get Smashed", RewardType.EXTEND, 100, "Gargoyles -> 200-250", 4753);
        add("Nechs Please", RewardType.EXTEND, 100, "Nechryael -> 200-250", 4754);
        // NOTE: "Augment my Abbies" (Abyssal demons) previously duplicated varbit 4090, which is
        // "Smell Ya Later" (Aberrant spectres); the two are distinct unlocks. Left as -1 (status
        // not shown) rather than mirror another unlock's varbit.
        add("Augment my Abbies", RewardType.EXTEND, 100, "Abyssal demons -> 200-250", -1);
        add("Horrorific", RewardType.EXTEND, 100, "Cave horrors -> 200-250", 4750);
        add("Wyver-nother One", RewardType.EXTEND, 100, "Skeletal Wyverns -> 50-75", 4752);
        add("Wyver-nother Two", RewardType.EXTEND, 100, "Fossil Island Wyverns -> 55-75", 5733);
        add("Basilonger", RewardType.EXTEND, 100, "Basilisks -> 200-250", 9455);
        add("More at Stake", RewardType.EXTEND, 100, "Vampyres -> 200-250", 10389);
        add("More eyes than sense", RewardType.EXTEND, 150, "Araxytes -> 200-250", 11022);
        add("Revenenenenenants", RewardType.EXTEND, 100, "Revenants -> 100-150", 14822);
        add("Krack On", RewardType.EXTEND, 100, "Cave kraken -> 150-200", -1);
        add("Can of Wyrms", RewardType.EXTEND, 100, "Wyrms -> 200-250", -1);

        // --- 3. Cosmetic (Slayer helmet recolours / themes) -------------------------------
        add("King Black Bonnet", RewardType.COSMETIC, 1000, "Recolour the Slayer helmet black with a KBD head", 5080);
        add("Kalphite Khat", RewardType.COSMETIC, 1000, "Recolour the Slayer helmet green with a Kalphite Queen head", 5081);
        add("Unholy Helmet", RewardType.COSMETIC, 1000, "Recolour the Slayer helmet red with an Abyssal demon head", 5082);
        add("Dark Mantle", RewardType.COSMETIC, 1000, "Recolour the Slayer helmet purple with a Dark claw", 5631);
        add("Undead Head", RewardType.COSMETIC, 1000, "Recolour the Slayer helmet turquoise with Vorkath's head", 6096);
        add("Use More Head", RewardType.COSMETIC, 1000, "Theme the Slayer helmet like Alchemical Hydra with a Hydra head", -1);
        add("Eye see you", RewardType.COSMETIC, 1000, "Theme the Slayer helmet like Araxxor with an Araxyte head", 11023);
        add("Twisted Vision", RewardType.COSMETIC, 200, "Theme the Slayer helmet like the Great Olm with Twisted Horns", 10104);
        add("Absolutely Slayin'", RewardType.COSMETIC, 1000, "Show a Slayer hood on the helmet (99 Slayer)", -1);
        add("Oath Breaker", RewardType.COSMETIC, 200, "Theme the Slayer helmet like Oathplate with a Demonic quill", -1);

        // --- 4. Item purchases ----------------------------------------------------------
        add("Rune pouch", RewardType.BUY, 750, "Holds up to 16,000 of 3 rune types; only one can be owned", -1);
        add("Herb sack", RewardType.BUY, 750, "Holds up to 30 of each grimy herb type", -1);
        add("Slayer ring (8)", RewardType.BUY, 75, "Equipable enchanted gem with 8 Slayer-site teleports", -1);
        add("Broad bolts (x250)", RewardType.BUY, 35, "55 Slayer / 61 Ranged - hit Turoth & Kurask", -1);
        add("Broad arrows (x250)", RewardType.BUY, 35, "55 Slayer / 50 Ranged - hit Turoth & Kurask", -1);
        add("Looting bag", RewardType.BUY, 10, "Carry extra tradeable loot in the Wilderness", -1);

        // --- 5. Task management --------------------------------------------------------
        add("Cancel task", RewardType.MANAGEMENT, 30, "Cancel the current assignment (per task)", -1);
        add("Block task", RewardType.MANAGEMENT, 100, "Permanently block the current monster (up to 6 slots)", -1);
    }

    private static void add(String name, RewardType type, int cost, String desc, int varbitId)
    {
        REWARDS.add(SlayerReward.builder()
            .name(name)
            .type(type)
            .cost(cost)
            .description(desc)
            .varbitId(varbitId)
            .unlocked(false)
            .build());
    }

    public List<SlayerReward> getAllRewards()
    {
        return Collections.unmodifiableList(REWARDS);
    }

    public List<SlayerReward> getRewards(RewardType type)
    {
        List<SlayerReward> result = new ArrayList<>();
        for (SlayerReward reward : REWARDS)
        {
            if (reward.getType() == type)
            {
                result.add(reward);
            }
        }
        return result;
    }

    public void updateUnlockStatus(Client client)
    {
        if (client == null)
        {
            return;
        }

        for (SlayerReward reward : REWARDS)
        {
            if (reward.getVarbitId() > 0)
            {
                try
                {
                    int val = client.getVarbitValue(reward.getVarbitId());
                    reward.setUnlocked(val == 1);
                }
                catch (Exception e)
                {
                    // Ignore varbit read exceptions
                }
            }
        }
    }
}
