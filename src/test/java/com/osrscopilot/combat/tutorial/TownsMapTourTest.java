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
 * The Towns &amp; Map tour: step-list shape, and the exact sequence of host calls a full run makes
 * (tab switches, town / shop / search focus, the three map snaps, and teardown on finish).
 */
public class TownsMapTourTest
{
    /** Records every Host call in order. */
    private static final class SpyHost implements TownsMapTour.Host
    {
        final List<String> log = new ArrayList<>();
        Rectangle mapBounds = null;

        public void showTownsTab()        { log.add("tab:towns"); }
        public void showShopsTab()        { log.add("tab:shops"); }
        public void showSearchTab()       { log.add("tab:search"); }
        public void selectFeaturedTown()  { log.add("selectTown"); }
        public void townFocus(String k)   { log.add("townFocus:" + k); }
        public void showFeaturedShop()    { log.add("showShop"); }
        public void stockFocus(String k)  { log.add("stockFocus:" + k); }
        public void search(String q)      { log.add("search:" + q); }
        public void searchFocus(String k) { log.add("searchFocus:" + k); }
        public void snapFeaturedShop()    { log.add("snap:shop"); }
        public void snapFeaturedMonster() { log.add("snap:monster"); }
        public void snapFeaturedTown()    { log.add("snap:town"); }
        public Rectangle worldMapBounds() { return mapBounds; }
        public void closeMap()            { log.add("closeMap"); }
        public void restoreTab()          { log.add("restoreTab"); }
    }

    @Test
    public void stepListShape()
    {
        Tour t = TownsMapTour.build(new SpyHost());
        assertEquals(15, t.getStepCount());
        for (int i = 0; i < t.getStepCount(); i++)
        {
            TutorialStep s = t.getStep(i);
            assertFalse("step " + i + " title", s.getTitle().trim().isEmpty());
            assertFalse("step " + i + " body", s.getBody().trim().isEmpty());
        }
        // Map steps carry the world-map target; welcome + outro don't.
        assertNull(t.getStep(0).resolveTarget());
        assertNull(t.getStep(14).resolveTarget());
        for (int i : new int[]{5, 6, 11, 12, 13})
        {
            SpyHost h = new SpyHost();
            h.mapBounds = new Rectangle(10, 20, 300, 200);
            assertNotNull("step " + i + " targets the map",
                TownsMapTour.build(h).getStep(i).resolveTarget());
        }
        // The panel steps sit beside the side panel.
        assertEquals(TutorialStep.Placement.SIDE_PANEL, t.getStep(2).getPlacement());
        assertEquals(TutorialStep.Placement.SIDE_PANEL, t.getStep(10).getPlacement());
    }

    @Test
    public void aFullRunDrivesTheHostInOrderAndTearsDown()
    {
        SpyHost h = new SpyHost();
        Tour t = TownsMapTour.build(h);
        TutorialOverlay ov = new TutorialOverlay(mock(Client.class));
        ov.start(t);
        for (int i = 0; i < t.getStepCount(); i++)
        {
            ov.next();
        }
        assertFalse(ov.isActive());

        String seq = String.join(" | ", h.log);
        // Towns -> a shop -> its Map button -> snap.
        assertTrue(seq, ordered(h.log, "tab:towns", "selectTown", "townFocus:town",
            "townFocus:firstShop", "townFocus:mapButton", "snap:shop"));
        // Shops tab (map closed first) -> stock.
        assertTrue(seq, ordered(h.log, "snap:shop", "closeMap", "tab:shops", "showShop", "stockFocus:filters"));
        // Search -> query -> jump from a result.
        assertTrue(seq, ordered(h.log, "tab:search", "search:rune scimitar", "snap:shop"));
        // The same Map button from a monster and a town.
        assertTrue(seq, ordered(h.log, "snap:monster", "snap:town"));
        // Teardown on finish.
        assertTrue(seq, ordered(h.log, "snap:town", "closeMap", "restoreTab"));
        assertTrue("clears every panel highlight on finish",
            h.log.contains("townFocus:null") && h.log.contains("stockFocus:null") && h.log.contains("searchFocus:null"));
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
