package com.osrscopilot.combat.engine;

import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.GameState;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CombatEventRecorderTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Client client;
    private CombatEventRecorder rec;
    private int tick;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        Player me = mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(me);
        when(client.getTickCount()).thenAnswer(i -> tick);
        rec = new CombatEventRecorder(client, new com.google.gson.Gson()); // test seam: no ClientThread / SpellAttackResolver
        rec.setOutputDirForTest(tmp.getRoot());
    }

    private HitsplatApplied hit(int amount, int type, boolean mine, boolean others, String npcName)
    {
        NPC npc = mock(NPC.class);
        when(npc.getName()).thenReturn(npcName);
        when(npc.getIndex()).thenReturn(7);
        Hitsplat hs = mock(Hitsplat.class);
        when(hs.getAmount()).thenReturn(amount);
        when(hs.getHitsplatType()).thenReturn(type);
        when(hs.isMine()).thenReturn(mine);
        when(hs.isOthers()).thenReturn(others);
        HitsplatApplied e = mock(HitsplatApplied.class);
        when(e.getActor()).thenReturn(npc);
        when(e.getHitsplat()).thenReturn(hs);
        return e;
    }

    private StatChanged xp(Skill skill, int total)
    {
        return new StatChanged(skill, total, 1, 1); // @Value - construct directly
    }

    private File theCaptureFile()
    {
        File[] files = tmp.getRoot().listFiles((d, n) -> n.startsWith("capture-") && n.endsWith(".jsonl"));
        assertEquals("exactly one capture file", 1, files == null ? 0 : files.length);
        return files[0];
    }

    private List<String> capturedLines() throws Exception
    {
        return Files.readAllLines(theCaptureFile().toPath(), StandardCharsets.UTF_8);
    }

    @Test
    public void captureOffIsANoOp() throws Exception
    {
        rec.onHitsplatApplied(hit(10, HitsplatID.DAMAGE_ME, true, false, "Vorkath"));
        rec.onGameTick();
        assertFalse(rec.isCapturing());
        File[] files = tmp.getRoot().listFiles();
        assertEquals("no file written while off", 0, files == null ? 0 : files.length);
    }

    @Test
    public void capturesHitsplatAndTickShape() throws Exception
    {
        rec.start();
        assertTrue(rec.isCapturing());
        tick = 101;
        rec.onHitsplatApplied(hit(22, HitsplatID.DAMAGE_ME, true, false, "Vorkath"));
        rec.onGameTick();
        rec.stop();

        List<String> lines = capturedLines();
        assertTrue("session header first", lines.get(0).contains("\"ty\":\"session\""));
        assertTrue("session-end last", lines.get(lines.size() - 1).contains("\"ty\":\"session-end\""));

        String tickLine = lines.stream().filter(l -> l.contains("\"ty\":\"t\"")).findFirst().orElse("");
        assertTrue("tick recorded", tickLine.contains("\"tick\":101"));
        assertTrue("hit event present", tickLine.contains("\"e\":\"hit\""));
        assertTrue("amount", tickLine.contains("\"amt\":22"));
        assertTrue("isMine", tickLine.contains("\"mine\":true"));
        assertTrue("type name resolved", tickLine.contains("\"tname\":\"DAMAGE_ME\""));
        assertTrue("npc target tagged with index", tickLine.contains("Vorkath#7"));
    }

    @Test
    public void capturesHitpointsXpDelta() throws Exception
    {
        rec.start();
        tick = 200;
        rec.onStatChanged(xp(Skill.HITPOINTS, 100_000)); // first sighting -> baseline, delta 0, skipped
        rec.onGameTick();
        tick = 201;
        rec.onStatChanged(xp(Skill.HITPOINTS, 100_016)); // +16 xp  -> 12 damage
        rec.onGameTick();
        rec.stop();

        List<String> lines = capturedLines();
        String xpLine = lines.stream()
            .filter(l -> l.contains("\"e\":\"xp\"") && l.contains("\"tick\":201"))
            .findFirst().orElse("");
        assertTrue("xp event on tick 201: " + lines, xpLine.contains("\"sk\":\"Hitpoints\""));
        assertTrue("delta captured", xpLine.contains("\"d\":16"));
    }

    @Test
    public void markWritesALabelLine() throws Exception
    {
        rec.start();
        rec.mark("Vorkath kill - DHCB");
        rec.stop();
        List<String> lines = capturedLines();
        assertTrue(lines.stream().anyMatch(l ->
            l.contains("\"ty\":\"mark\"") && l.contains("Vorkath kill - DHCB")));
    }

    @Test
    public void toggleFlipsCapturing()
    {
        assertFalse(rec.isCapturing());
        rec.toggle();
        assertTrue(rec.isCapturing());
        rec.toggle();
        assertFalse(rec.isCapturing());
    }

    @Test
    public void projectileLoggedOncePerFlightNotEveryCycle() throws Exception
    {
        rec.start();
        tick = 300;
        Projectile p = mock(Projectile.class);
        when(p.getId()).thenReturn(1481);
        when(p.getRemainingCycles()).thenReturn(60, 30); // two cycles of the same shot
        ProjectileMoved e = mock(ProjectileMoved.class);
        when(e.getProjectile()).thenReturn(p);
        rec.onProjectileMoved(e);
        rec.onProjectileMoved(e); // same tick -> deduped
        rec.onGameTick();
        rec.stop();

        long projLines = capturedLines().stream()
            .filter(l -> l.contains("\"e\":\"proj\"")).count();
        assertEquals("one proj event for the flight", 1, projLines);
    }

    @Test
    public void nearbyThrallAppearsInTheNearArray() throws Exception
    {
        Player me = client.getLocalPlayer();
        WorldPoint here = new WorldPoint(3200, 3200, 0);
        when(me.getWorldLocation()).thenReturn(here);

        NPC thrall = mock(NPC.class);
        when(thrall.getName()).thenReturn("Skeleton thrall");
        when(thrall.getId()).thenReturn(11_209);
        when(thrall.getIndex()).thenReturn(42);
        when(thrall.getWorldLocation()).thenReturn(new WorldPoint(3201, 3200, 0)); // 1 tile away
        when(thrall.getAnimation()).thenReturn(-1);
        when(thrall.getHealthRatio()).thenReturn(-1);
        when(client.getNpcs()).thenReturn(Collections.singletonList(thrall));

        rec.start();
        tick = 500;
        rec.onHitsplatApplied(hit(2, HitsplatID.DAMAGE_ME, false, true, "Hill Giant")); // ensure the tick is written
        rec.onGameTick();
        rec.stop();

        String tickLine = capturedLines().stream()
            .filter(l -> l.contains("\"ty\":\"t\"")).findFirst().orElse("");
        assertTrue("near[] present", tickLine.contains("\"near\""));
        assertTrue("thrall listed by name matching", tickLine.contains("\"name\":\"Skeleton thrall\""));
        assertTrue("thrall id captured", tickLine.contains("\"id\":11209"));
    }

    @Test
    public void equipmentChangeLogsTheCurrentWeapon() throws Exception
    {
        when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
        ItemContainer eq = mock(ItemContainer.class);
        Item weapon = new Item(4587, 1); // @Value - construct directly
        when(eq.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx())).thenReturn(weapon);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(eq);
        ItemComposition comp = mock(ItemComposition.class);
        when(comp.getName()).thenReturn("Dragon scimitar");
        when(client.getItemDefinition(4587)).thenReturn(comp);

        rec.start();
        tick = 600;
        rec.onItemContainerChanged(new ItemContainerChanged(InventoryID.EQUIPMENT.getId(), eq));
        rec.onGameTick();
        rec.stop();

        String line = capturedLines().stream()
            .filter(l -> l.contains("\"e\":\"equip\"")).findFirst().orElse("");
        assertTrue("weapon name in equip event: " + line, line.contains("Dragon scimitar"));
    }

    @Test
    public void nonEquipmentContainerChangeIsIgnored() throws Exception
    {
        rec.start();
        tick = 601;
        rec.onItemContainerChanged(new ItemContainerChanged(InventoryID.INVENTORY.getId(), null));
        rec.onGameTick();
        rec.stop();
        assertFalse(capturedLines().stream().anyMatch(l -> l.contains("\"e\":\"equip\"")));
    }

    @Test
    public void interactingChangeIsDedupedPerSource() throws Exception
    {
        NPC cyc = mock(NPC.class);
        when(cyc.getName()).thenReturn("Cyclops");
        when(cyc.getIndex()).thenReturn(9);
        Player me = client.getLocalPlayer();

        rec.start();
        tick = 800;
        rec.onInteractingChanged(new InteractingChanged(cyc, me)); // Cyclops -> me  (new -> logged)
        rec.onInteractingChanged(new InteractingChanged(cyc, me)); // same -> deduped
        rec.onInteractingChanged(new InteractingChanged(cyc, me)); // same -> deduped
        rec.onInteractingChanged(new InteractingChanged(cyc, null)); // Cyclops -> null (change -> logged)
        rec.onGameTick();
        rec.stop();

        int interacts = CombatCaptureReader.eventsOfKind(
            CombatCaptureReader.readLines(theCaptureFile().toPath()), "interact").size();
        assertEquals("only the two actual changes", 2, interacts);
    }

    @Test
    public void autocastVarbitIsDecodedToASpellName() throws Exception
    {
        rec.start();
        tick = 66;
        VarbitChanged v = new VarbitChanged();
        v.setVarbitId(276);
        v.setValue(6);
        rec.onVarbitChanged(v);
        rec.onGameTick();
        rec.stop();

        String line = capturedLines().stream()
            .filter(l -> l.contains("\"e\":\"var\"")).findFirst().orElse("");
        assertTrue("autocast decoded: " + line, line.contains("\"autocast\":\"Water Bolt\""));
    }

    @Test
    public void xpBaselineIsSeededWhenLoggedInSoTheFirstDropCounts() throws Exception
    {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.HITPOINTS)).thenReturn(100_000);

        rec.start();
        tick = 900;
        rec.onStatChanged(xp(Skill.HITPOINTS, 100_012)); // first drop, but baseline was seeded
        rec.onGameTick();
        rec.stop();

        String xpLine = capturedLines().stream()
            .filter(l -> l.contains("\"e\":\"xp\"")).findFirst().orElse("");
        assertTrue("first drop has a real delta, not a base anchor: " + xpLine,
            xpLine.contains("\"d\":12") && !xpLine.contains("\"base\""));
    }

    @Test
    public void captureRoundTripsThroughTheReader() throws Exception
    {
        // A tiny scripted fight: 3 hits, matching Hitpoints XP drops (4/3 x damage).
        rec.start();
        int[] hits = {8, 11, 6};
        int hpXp = 1_000_000;
        for (int i = 0; i < hits.length; i++)
        {
            tick = 700 + i * 4;
            rec.onHitsplatApplied(hit(hits[i], HitsplatID.DAMAGE_ME, true, false, "Hill Giant"));
            hpXp += Math.round(hits[i] * 4 / 3.0);
            rec.onStatChanged(xp(Skill.HITPOINTS, hpXp));
            rec.onGameTick();
        }
        rec.stop();

        File[] files = tmp.getRoot().listFiles((d, n) -> n.startsWith("capture-"));
        List<JsonObject> lines = CombatCaptureReader.readLines(files[0].toPath());

        assertEquals("three tick rows", 3, CombatCaptureReader.ticks(lines).size());
        assertEquals("three hit events", 3, CombatCaptureReader.eventsOfKind(lines, "hit").size());
        assertEquals("summed my-NPC damage", 25, CombatCaptureReader.myNpcDamage(lines));
        // XP deltas: round(8*4/3)+round(11*4/3)+round(6*4/3) = 11+15+8 = 34 (first is a baseline, excluded)
        long xpGained = CombatCaptureReader.hitpointsXpGained(lines);
        assertEquals("hp xp deltas after the baseline", 15 + 8, xpGained);
    }

    @Test
    public void malformedEventIsSwallowedNotPropagated() throws Exception
    {
        rec.start();
        tick = 400;
        HitsplatApplied broken = mock(HitsplatApplied.class);
        when(broken.getHitsplat()).thenThrow(new IllegalStateException("boom"));
        rec.onHitsplatApplied(broken); // must not throw - reaching the next line is the assertion
        rec.onHitsplatApplied(hit(15, HitsplatID.DAMAGE_ME, true, false, "Vorkath")); // still works
        rec.onGameTick();
        rec.stop();

        String tickLine = capturedLines().stream()
            .filter(l -> l.contains("\"ty\":\"t\"")).findFirst().orElse("");
        assertTrue("the good hit is still recorded after the broken one", tickLine.contains("\"amt\":15"));
    }
}
