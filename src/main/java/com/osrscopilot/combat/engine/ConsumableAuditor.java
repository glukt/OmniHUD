package com.osrscopilot.combat.engine;

import com.osrscopilot.combat.model.EntityCombatStats;
import java.awt.Color;
import java.util.HashMap;

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.game.ItemManager;

@Singleton
@Slf4j
public class ConsumableAuditor
{
    private final Client client;
    private final ItemManager itemManager;

    private final Map<Integer, Integer> cachedInventory = new HashMap<>();
    private int droppedSlotThisTick = -1;
    // True only when an "Eat" / "Drink" menu option was clicked this tick. A food/potion whose
    // inventory count drops WITHOUT that click was used as an ingredient / fishing bait / on an
    // object - not eaten (fix: karambwan fishing burned "Raw karambwanji" as bait and it booked
    // as "Ate Raw karambwanji (+18)").
    private boolean consumeIntentThisTick = false;
    private int lastHp = -1;
    private int lastPrayer = -1;

    // Optional sink for each supply cost as it is booked (used by the Slayer tab's net-gain tracker).
    private java.util.function.LongConsumer supplyCostSink;

    public void setSupplyCostSink(java.util.function.LongConsumer sink)
    {
        this.supplyCostSink = sink;
    }

    private void reportSupplyCost(long cost)
    {
        if (supplyCostSink != null && cost > 0)
        {
            supplyCostSink.accept(cost);
        }
    }

    @Inject
    public ConsumableAuditor(Client client, ItemManager itemManager)

    {
        this.client = client;
        this.itemManager = itemManager;
    }

    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (event != null && event.getMenuOption() != null)
        {
            String opt = event.getMenuOption();
            if (opt.equalsIgnoreCase("Drop") || opt.equalsIgnoreCase("Destroy") || opt.equalsIgnoreCase("Empty"))
            {
                droppedSlotThisTick = event.getParam0();
            }
            else if (opt.equalsIgnoreCase("Eat") || opt.equalsIgnoreCase("Drink"))
            {
                consumeIntentThisTick = true;
            }
        }
    }

    public void auditInventory(ItemContainerChanged event, EntityCombatStats... statsList)
    {
        if (client == null || event == null || event.getContainerId() != InventoryID.INVENTORY.getId())
        {
            return;
        }

        ItemContainer container = event.getItemContainer();
        if (container == null) return;

        // 1. Guard against Bank/Deposit Box open
        if (isInterfaceSuppressed())
        {
            refreshCache(container);
            droppedSlotThisTick = -1;
            consumeIntentThisTick = false;
            return;
        }

        Map<Integer, Integer> currentInventory = new HashMap<>();
        for (Item item : container.getItems())
        {
            if (item != null && item.getId() > 0)
            {
                currentInventory.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }

        // 2. Compute decremented items
        if (!cachedInventory.isEmpty() && statsList != null && statsList.length > 0)
        {
            for (Map.Entry<Integer, Integer> entry : cachedInventory.entrySet())
            {
                int itemId = entry.getKey();
                int prevCount = entry.getValue();
                int currCount = currentInventory.getOrDefault(itemId, 0);

                if (currCount < prevCount)
                {
                    int delta = prevCount - currCount;
                    ItemComposition comp = client.getItemDefinition(itemId);
                    ConsumableKind kind = classifyConsumable(itemId, comp);

                    // Only true consumables cost anything. A wieldable/wearable item whose inventory
                    // count dropped was equipped / sold / alched / dropped, not consumed. A food or
                    // potion whose count dropped with no "Eat" / "Drink" click was used as an
                    // ingredient / bait / on an object (runes & ammo are consumed without a click,
                    // so they're exempt from the intent gate).
                    boolean needsIntent = kind == ConsumableKind.FOOD || kind == ConsumableKind.POTION;
                    boolean skip = kind == ConsumableKind.NONE
                        || wasItemEquipped(itemId)
                        || justEquipped(itemId)
                        || droppedSlotThisTick != -1
                        || (needsIntent && !consumeIntentThisTick);

                    if (!skip)
                    {
                        String itemName = (comp != null && comp.getName() != null) ? comp.getName() : "Item (" + itemId + ")";
                        int price = itemManager != null ? itemManager.getItemPrice(itemId) : 0;
                        long cost = (long) price * delta;

                        boolean isPot = kind == ConsumableKind.POTION;
                        boolean isFd = kind == ConsumableKind.FOOD;
                        int foodHeal = isFd ? estimateFoodHeal(itemName) : 0;

                        int effectiveHeal = 0;
                        int overheal = 0;
                        int currentHp = lastHp > 0 ? lastHp : 99;
                        int maxHp = 99;
                        try
                        {
                            if (lastHp <= 0 && client != null)
                            {
                                currentHp = client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS);
                            }
                            if (client != null)
                            {
                                maxHp = client.getRealSkillLevel(net.runelite.api.Skill.HITPOINTS);
                            }
                            if (maxHp <= 0) maxHp = 99;
                        }
                        catch (RuntimeException e)
                        {
                            log.debug("HP level read failed during consumable audit", e);
                        }

                        if (isFd)
                        {
                            int totalPotential = foodHeal * delta;
                            int cap = maxHp;
                            if (itemName.toLowerCase().contains("anglerfish"))
                            {
                                cap = maxHp + (maxHp / 10) + 2;
                            }

                            if (currentHp < cap)
                            {
                                effectiveHeal = Math.min(totalPotential, Math.max(0, cap - currentHp));
                                overheal = Math.max(0, totalPotential - effectiveHeal);
                            }
                            else
                            {
                                effectiveHeal = 0;
                                overheal = totalPotential;
                            }
                            lastHp = Math.min(cap, currentHp + effectiveHeal);
                        }
                        else if (isPot && itemName.toLowerCase().contains("brew"))
                        {
                            int brewNominal = Math.max(1, (int) (maxHp * 0.15) + 2) * delta;
                            int brewCap = maxHp + (int) (maxHp * 0.15) + 2;
                            if (currentHp < brewCap)
                            {
                                effectiveHeal = Math.min(brewNominal, Math.max(0, brewCap - currentHp));
                                overheal = Math.max(0, brewNominal - effectiveHeal);
                            }
                            else
                            {
                                effectiveHeal = 0;
                                overheal = brewNominal;
                            }
                            lastHp = Math.min(brewCap, currentHp + effectiveHeal);
                        }

                        for (EntityCombatStats stats : statsList)
                        {
                            if (stats != null)
                            {
                                stats.recordConsumable(itemId, itemName, delta, cost);

                                if (isPot)
                                {
                                    stats.setPotionsDrunkCount(stats.getPotionsDrunkCount() + delta);
                                }
                                else if (isFd)
                                {
                                    stats.setFoodEatenCount(stats.getFoodEatenCount() + delta);
                                }

                                if (effectiveHeal > 0)
                                {
                                    stats.setHpHealed(stats.getHpHealed() + effectiveHeal);
                                    String note = (isFd ? "Ate " : "Drank ") + itemName + " (+" + effectiveHeal + " HP)";
                                    stats.recordTimeSeriesSecond((int) stats.getDurationSeconds(), 0, 0, effectiveHeal, note);
                                }
                                if (overheal > 0)
                                {
                                    stats.setHpOverhealed(stats.getHpOverhealed() + overheal);
                                }

                                int tick = client != null ? client.getTickCount() : 0;
                                String timeStr = formatDuration(stats.getDurationSeconds());
                                if (isFd)
                                {
                                    String healStr = effectiveHeal > 0 ? (" (+" + effectiveHeal + " HP" + (overheal > 0 ? " +" + overheal + "o)" : ")")) : (" (+" + overheal + "o)");
                                    stats.addTimelineEvent(com.osrscopilot.combat.model.CombatTimelineEvent.builder()
                                        .clientTick(tick).timeFormatted(timeStr).eventType("CONSUME").icon("^")
                                        .description("Ate " + itemName + healStr).color(new Color(129, 199, 132))
                                        .source("You").target("You").weaponOrSpell(itemName).build());
                                }
                                else if (isPot)
                                {
                                    stats.addTimelineEvent(com.osrscopilot.combat.model.CombatTimelineEvent.builder()
                                        .clientTick(tick).timeFormatted(timeStr).eventType("POTION").icon("~")
                                        .description("Drank " + itemName).color(new Color(77, 208, 225))
                                        .source("You").target("You").weaponOrSpell(itemName).build());
                                }
                            }
                        }

                        reportSupplyCost(cost);
                    }
                }
            }
        }

        droppedSlotThisTick = -1;
        consumeIntentThisTick = false;
        cachedInventory.clear();
        cachedInventory.putAll(currentInventory);
    }

    private final Map<Integer, Integer> cachedEquipment = new HashMap<>();

    public void auditEquipment(ItemContainerChanged event, EntityCombatStats... statsList)
    {
        if (client == null || event == null || event.getContainerId() != InventoryID.EQUIPMENT.getId())
        {
            return;
        }

        ItemContainer container = event.getItemContainer();
        if (container == null || isInterfaceSuppressed())
        {
            return;
        }

        Map<Integer, Integer> currentEquip = new HashMap<>();
        for (Item item : container.getItems())
        {
            if (item != null && item.getId() > 0)
            {
                currentEquip.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }

        if (!cachedEquipment.isEmpty() && statsList != null && statsList.length > 0)
        {
            for (Map.Entry<Integer, Integer> entry : cachedEquipment.entrySet())
            {
                int itemId = entry.getKey();
                int prevCount = entry.getValue();
                int currCount = currentEquip.getOrDefault(itemId, 0);

                if (currCount < prevCount)
                {
                    int delta = prevCount - currCount;
                    ItemComposition comp = client.getItemDefinition(itemId);
                    String ammoName = (comp != null && comp.getName() != null) ? comp.getName() : "Ammo (" + itemId + ")";

                    // Only count a worn-slot decrease that is actually ammo being spent — not a
                    // weapon/shield swap out of the slot, and not ammo being unequipped back to
                    // the inventory (which shows up as a matching inventory increase).
                    String ammoLower = ammoName.toLowerCase();
                    if (!isAmmoName(ammoLower) || movedToInventory(itemId))
                    {
                        continue;
                    }

                    int price = itemManager != null ? itemManager.getItemPrice(itemId) : 0;
                    long cost = (long) price * delta;

                    for (EntityCombatStats stats : statsList)
                    {
                        if (stats != null)
                        {
                            stats.recordConsumable(itemId, ammoName, delta, cost);
                        }
                    }
                    reportSupplyCost(cost);
                }
            }
        }

        cachedEquipment.clear();
        cachedEquipment.putAll(currentEquip);
    }

    public void onChatMessage(net.runelite.api.events.ChatMessage event, EntityCombatStats... statsList)
    {
        if (event == null || event.getMessage() == null || statsList == null) return;
        String msg = event.getMessage().toLowerCase();
        if (msg.contains("you eat the") || msg.contains("you eat a") || msg.contains("it heals some health"))
        {
            for (EntityCombatStats stats : statsList)
            {
                if (stats != null && stats.getFoodEatenCount() == 0 && !stats.getConsumablesUsed().isEmpty())
                {
                    stats.setFoodEatenCount(Math.max(1, stats.getFoodEatenCount()));
                }
            }
        }
    }

    public void onStatChanged(net.runelite.api.events.StatChanged event, EntityCombatStats... statsList)
    {
        if (client == null || event == null || statsList == null || statsList.length == 0) return;

        if (event.getSkill() == net.runelite.api.Skill.HITPOINTS)
        {
            int currentHp = event.getBoostedLevel();
            if (lastHp > 0 && currentHp > lastHp)
            {
                int delta = currentHp - lastHp;
                for (EntityCombatStats stats : statsList)
                {
                    if (stats != null)
                    {
                        stats.setHpHealed(stats.getHpHealed() + delta);
                        stats.recordTimeSeriesSecond((int) stats.getDurationSeconds(), 0, 0, delta, "Healed " + delta + " HP");
                    }
                }
            }
            lastHp = currentHp;
        }
        else if (event.getSkill() == net.runelite.api.Skill.PRAYER)
        {
            int currentPrayer = event.getBoostedLevel();
            if (lastPrayer > 0 && currentPrayer > lastPrayer)
            {
                int delta = currentPrayer - lastPrayer;
                int tick = client != null ? client.getTickCount() : 0;
                for (EntityCombatStats stats : statsList)
                {
                    if (stats != null)
                    {
                        stats.setPrayerPointsRestored(stats.getPrayerPointsRestored() + delta);
                        // A real restore chunk (potion / altar) - log it. Skip small ticks so a
                        // Holy Wrench / passive fluctuation doesn't spam the ledger.
                        if (delta >= 8)
                        {
                            stats.addTimelineEvent(com.osrscopilot.combat.model.CombatTimelineEvent.builder()
                                .clientTick(tick).timeFormatted(formatDuration(stats.getDurationSeconds()))
                                .eventType("POTION").icon("~").description("Restored " + delta + " prayer")
                                .color(new Color(149, 165, 246)).amount(delta).source("You").target("You").build());
                        }
                    }
                }
            }
            lastPrayer = currentPrayer;
        }
    }

    private String formatDuration(double durationSeconds)
    {
        return com.osrscopilot.combat.CombatFormat.duration(durationSeconds);
    }



    public void onGameStateChanged(net.runelite.api.events.GameStateChanged event)
    {
        if (client == null || event == null) return;
        if (event.getGameState() == net.runelite.api.GameState.LOGGED_IN)
        {
            try
            {
                lastHp = client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS);
                lastPrayer = client.getBoostedSkillLevel(net.runelite.api.Skill.PRAYER);
            }
            catch (RuntimeException e)
            {
                log.debug("HP/Prayer snapshot on login failed", e);
            }
        }
        else
        {
            lastHp = -1;
            lastPrayer = -1;
            cachedInventory.clear();
            cachedEquipment.clear();
        }
    }

    private int estimateFoodHeal(String name)
    {
        String lower = name.toLowerCase();
        if (lower.contains("anglerfish") || lower.contains("manta ray")) return 22;
        if (lower.contains("sea turtle")) return 21;
        if (lower.contains("shark")) return 20;
        if (lower.contains("karambwan")) return 18;
        if (lower.contains("monkfish")) return 16;
        if (lower.contains("swordfish")) return 14;
        if (lower.contains("lobster")) return 12;
        if (lower.contains("bass")) return 13;
        if (lower.contains("tuna")) return 10;
        return 16;
    }

    private boolean isInterfaceSuppressed()
    {
        if (client == null) return false;
        try
        {
            net.runelite.api.widgets.Widget bank = client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.UNIVERSE);
            if (bank != null && !bank.isHidden()) return true;

            net.runelite.api.widgets.Widget deposit = client.getWidget(192, 1);
            if (deposit != null && !deposit.isHidden()) return true;

            net.runelite.api.widgets.Widget ge = client.getWidget(net.runelite.api.gameval.InterfaceID.GeOffers.UNIVERSE);
            if (ge != null && !ge.isHidden()) return true;

            return false;
        }
        catch (Exception e)
        {
            return false;
        }
    }



    private boolean wasItemEquipped(int itemId)
    {
        try
        {
            ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
            return equipment != null && equipment.contains(itemId);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /** True when this item's worn count went up since the last equipment snapshot (i.e. just equipped). */
    private boolean justEquipped(int itemId)
    {
        try
        {
            ItemContainer eq = client.getItemContainer(InventoryID.EQUIPMENT);
            if (eq == null)
            {
                return false;
            }
            return eq.count(itemId) > cachedEquipment.getOrDefault(itemId, 0);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /** True when this item's inventory count went up since the last snapshot (i.e. just unequipped). */
    private boolean movedToInventory(int itemId)
    {
        try
        {
            ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
            if (inv == null)
            {
                return false;
            }
            return inv.count(itemId) > cachedInventory.getOrDefault(itemId, 0);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    // ----- Consumable classification -------------------------------------------------------------

    enum ConsumableKind { FOOD, POTION, RUNE, AMMO, CHARGE, NONE }

    private static final Pattern DOSE_SUFFIX = Pattern.compile(".*\\(\\d\\)$");

    /**
     * Strict positive whitelist: an inventory-slot decrease is a real supply cost only when the item
     * classifies as food / potion / rune / ammo. Anything wieldable or wearable (a weapon or armour
     * swap, which also drops the inventory count by 1) classifies as NONE, as do alching / selling /
     * burying bones / item loss on death.
     */
    private ConsumableKind classifyConsumable(int itemId, ItemComposition comp)
    {
        if (itemId == ItemID.COINS_995)
        {
            return ConsumableKind.NONE;
        }

        // Karambwan-fishing bait: "Raw karambwanji" (id 3150) carries an "Eat" inventory action but
        // is only ever burned as bait or used as a cooking ingredient - never a combat heal. Raw
        // fish / meat in general are ingredients, so a "Raw ..." name is never tracked as food.
        {
            String n = (comp != null && comp.getName() != null) ? comp.getName().toLowerCase() : "";
            if (n.contains("karambwanji") || n.startsWith("raw "))
            {
                return ConsumableKind.NONE;
            }
        }

        if (comp != null)
        {
            String[] actions = comp.getInventoryActions();
            if (actions != null)
            {
                for (String a : actions)
                {
                    if ("Wield".equalsIgnoreCase(a) || "Wear".equalsIgnoreCase(a))
                    {
                        return ConsumableKind.NONE;
                    }
                }
                for (String a : actions)
                {
                    if ("Drink".equalsIgnoreCase(a))
                    {
                        return ConsumableKind.POTION;
                    }
                    if ("Eat".equalsIgnoreCase(a))
                    {
                        return ConsumableKind.FOOD;
                    }
                }
            }
        }

        // Name fallbacks — unit tests and un-cached defs only supply the name.
        String name = (comp != null && comp.getName() != null) ? comp.getName().toLowerCase() : "";
        if (name.isEmpty())
        {
            return ConsumableKind.NONE;
        }
        if (isPotionName(name))
        {
            return ConsumableKind.POTION;
        }
        if (isFoodName(name))
        {
            return ConsumableKind.FOOD;
        }
        if (name.endsWith(" rune") || name.endsWith(" runes"))
        {
            return ConsumableKind.RUNE;
        }
        if (isAmmoName(name))
        {
            return ConsumableKind.AMMO;
        }
        return ConsumableKind.NONE;
    }

    private boolean isAmmoName(String lower)
    {
        return lower.contains("cannonball") || lower.contains("arrow") || lower.contains("bolts")
            || lower.contains("bolt rack") || lower.contains("dart") || lower.contains("javelin")
            || lower.contains("throwing axe") || lower.contains("chinchompa") || lower.contains("blowpipe dart");
    }

    private boolean isPotionName(String lower)
    {
        // A trailing "(n)" dose is the strong signal; the bare "(" check was catching charged
        // jewellery (glory(6), ring of dueling(8), ...) and mis-booking it as a potion.
        return DOSE_SUFFIX.matcher(lower).matches()
            || lower.contains("potion") || lower.contains("brew") || lower.contains("restore")
            || lower.contains("antifire") || lower.contains("antidote") || lower.contains("antipoison")
            || lower.contains("stamina") || lower.contains("sanfew") || lower.contains("elixir")
            || lower.contains("serum");
    }

    private boolean isFoodName(String lower)
    {
        // "Raw ..." items are cooking ingredients / bait, never eaten in combat. "karambwanji"
        // (the karambwan-fishing bait) contains "karambwan" as a substring - exclude it explicitly.
        if (lower.startsWith("raw ") || lower.contains("karambwanji")
            || lower.contains("rock cake") || lower.contains("seed"))
        {
            return false;
        }
        return lower.contains("shark") || lower.contains("anglerfish") || lower.contains("karambwan")
            || lower.contains("manta ray") || lower.contains("tuna") || lower.contains("lobster")
            || lower.contains("swordfish") || lower.contains("monkfish") || lower.contains("sea turtle")
            || lower.contains("cake") || lower.contains("pie") || lower.contains("pizza")
            || lower.contains("bass") || lower.contains("stew") || lower.contains("brew ")
            || lower.contains("cooked") || lower.contains("potato");
    }

    private void refreshCache(ItemContainer container)
    {
        cachedInventory.clear();
        for (Item item : container.getItems())
        {
            if (item != null && item.getId() > 0)
            {
                cachedInventory.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }
    }
}
