package com.osrscopilot.combat.tutorial;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The Slayer tour: step-list shape, and the sequence of Host calls a full run makes - install the
 * demo task, walk the picker (auto-follow then manual pin), snap a spawn, open the drop table, and
 * tear the demo task back down on finish.
 */
public class SlayerTourTest
{
    private static final class SpyHost implements SlayerTour.Host
    {
        final List<String> log = new ArrayList<>();
        Rectangle mapBounds = null;

        public void showSlayerTab()        { log.add("showSlayerTab"); }
        public void showLootTab()          { log.add("showLootTab"); }
        public void installDemoTask()      { log.add("installDemoTask"); }
        public void installDemoLoot()      { log.add("installDemoLoot"); }
        public void noteKill(String n)     { log.add("noteKill:" + n); }
        public void pinSubtype(String m)   { log.add("pinSubtype:" + m); }
        public void focus(String k)        { log.add("focus:" + k); }
        public void snapSpawn()            { log.add("snapSpawn"); }
        public void openDrops()            { log.add("openDrops"); }
        public Rectangle worldMapBounds()  { return mapBounds; }
        public void closeMap()             { log.add("closeMap"); }
        public void teardown()             { log.add("teardown"); }
    }

    @Test
    public void stepListShape()
    {
        Tour t = SlayerTour.build(new SpyHost());
        assertEquals(12, t.getStepCount());
        for (int i = 0; i < t.getStepCount(); i++)
        {
            TutorialStep s = t.getStep(i);
            assertFalse("step " + i + " title", s.getTitle().trim().isEmpty());
            assertFalse("step " + i + " body", s.getBody().trim().isEmpty());
        }
        // Intro + outro carry no spotlight.
        assertNull(t.getStep(0).resolveTarget());
        assertNull(t.getStep(11).resolveTarget());
        // The "snap a spawn" step targets the world map.
        SpyHost h = new SpyHost();
        h.mapBounds = new Rectangle(10, 20, 300, 200);
        assertNotNull("step 6 targets the map", SlayerTour.build(h).getStep(6).resolveTarget());
        // The panel-walk steps sit beside the side panel.
        assertEquals(TutorialStep.Placement.SIDE_PANEL, t.getStep(1).getPlacement());
        assertEquals(TutorialStep.Placement.SIDE_PANEL, t.getStep(8).getPlacement());
    }

    @Test
    public void aFullRunDrivesTheHostInOrderAndTearsDown()
    {
        SpyHost h = new SpyHost();
        Tour t = SlayerTour.build(h);
        TutorialOverlay ov = new TutorialOverlay(mock(Client.class));
        ov.start(t);
        for (int i = 0; i < t.getStepCount(); i++)
        {
            ov.next();
        }
        assertFalse(ov.isActive());

        String seq = String.join(" | ", h.log);
        // onStart installs the throwaway task + demo loot before anything else.
        assertEquals("installDemoTask", h.log.get(0));
        assertTrue(seq, ordered(h.log, "installDemoTask", "installDemoLoot"));
        // Task card -> picker -> auto-follow a kill -> manual pin -> back to auto for the spawn list.
        assertTrue(seq, ordered(h.log, "focus:task", "focus:picker",
            "noteKill:Rune dragon", "pinSubtype:Adamant dragon", "pinSubtype:null", "focus:spawns"));
        // Snap a spawn -> drop table -> the Loot tab -> reward points -> masters.
        assertTrue(seq, ordered(h.log, "focus:spawns", "snapSpawn", "openDrops",
            "showLootTab", "focus:rewards", "focus:masters"));
        // Teardown on finish: clear the highlight, close the map, drop the demo task.
        assertTrue(seq, ordered(h.log, "focus:masters", "focus:null", "closeMap", "teardown"));
    }

    /** True if {@code needles} appear in {@code haystack} in that relative order (gaps allowed). */
    private static boolean ordered(List<String> haystack, String... needles)
    {
        int at = 0;
        for (String n : needles)
        {
            int found = -1;
            for (int i = at; i < haystack.size(); i++)
            {
                if (haystack.get(i).equals(n)) { found = i; break; }
            }
            if (found < 0)
            {
                return false;
            }
            at = found + 1;
        }
        return true;
    }
}
