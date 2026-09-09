package com.osrscopilot.combat.tutorial;

import java.awt.Canvas;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import org.junit.Before;
import org.junit.Test;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P2: the coach-mark engine. Exercised here with a throwaway 2-3 step tour - state machine and hook
 * ordering, then a headless render to check the spotlight, the button hitboxes and the edge-flip.
 */
public class TutorialOverlayTest
{
    private final List<String> log = new ArrayList<>();
    private Client client;
    private Canvas canvas;
    private Graphics2D g;

    @Before
    public void setUp()
    {
        log.clear();
        client = mock(Client.class);
        canvas = mock(Canvas.class);
        when(client.getCanvas()).thenReturn(canvas);
        when(canvas.getWidth()).thenReturn(800);
        when(canvas.getHeight()).thenReturn(600);
        g = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB).createGraphics();
    }

    private TutorialStep step(String name, Rectangle target)
    {
        TutorialStep.Builder b = TutorialStep.builder(name,
            "Body copy for " + name + " that is long enough to wrap onto a couple of lines in the card.")
            .onEnter(() -> log.add("enter:" + name))
            .onExit(() -> log.add("exit:" + name));
        if (target != null)
        {
            b.target(() -> target);
        }
        return b.build();
    }

    private Tour threeStepTour()
    {
        return new Tour("demo", "Demo tour",
            asList(step("A", new Rectangle(600, 60, 120, 22)), step("B", null), step("C", new Rectangle(40, 300, 90, 20))),
            () -> log.add("start"), () -> log.add("finish"));
    }

    private TutorialOverlay overlay()
    {
        return new TutorialOverlay(client);
    }

    private static MouseEvent press(int x, int y)
    {
        return new MouseEvent(new Canvas(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
            0, x, y, 1, false, MouseEvent.BUTTON1);
    }

    private static KeyEvent key(int code)
    {
        return new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    public void forwardRunFiresHooksInOrderAndEndsAfterLastStep()
    {
        TutorialOverlay ov = overlay();
        ov.start(threeStepTour());
        assertEquals(asList("start", "enter:A"), log);

        ov.next();
        ov.next();
        assertEquals(asList("start", "enter:A", "exit:A", "enter:B", "exit:B", "enter:C"), log);
        assertTrue(ov.isActive());

        ov.next(); // past the last step -> end
        assertEquals(asList("start", "enter:A", "exit:A", "enter:B", "exit:B", "enter:C", "exit:C", "finish"), log);
        assertFalse(ov.isActive());
        assertEquals(-1, ov.currentIndex());
    }

    @Test
    public void backFromFirstStepIsANoOp()
    {
        TutorialOverlay ov = overlay();
        ov.start(threeStepTour());
        log.clear();
        ov.back();
        assertTrue(log.isEmpty());
        assertEquals(0, ov.currentIndex());
    }

    @Test
    public void backRunsExitThenPreviousEnter()
    {
        TutorialOverlay ov = overlay();
        ov.start(threeStepTour());
        ov.next();
        log.clear();
        ov.back();
        assertEquals(asList("exit:B", "enter:A"), log);
        assertEquals(0, ov.currentIndex());
    }

    @Test
    public void endMidTourExitsCurrentStepThenFinishes()
    {
        TutorialOverlay ov = overlay();
        ov.start(threeStepTour());
        ov.next();
        log.clear();
        ov.end();
        assertEquals(asList("exit:B", "finish"), log);
        assertFalse(ov.isActive());
    }

    @Test
    public void startingASecondTourTearsDownTheFirst()
    {
        TutorialOverlay ov = overlay();
        ov.start(threeStepTour());
        ov.next();
        log.clear();
        ov.start(new Tour("t2", "Second", singletonList(step("Z", null)), () -> log.add("start2"), () -> log.add("finish2")));
        assertEquals(asList("exit:B", "finish", "start2", "enter:Z"), log);
    }

    @Test
    public void escEndsAndArrowsNavigate()
    {
        TutorialOverlay ov = overlay();
        ov.start(threeStepTour());
        ov.keyPressed(key(KeyEvent.VK_RIGHT));
        assertEquals(1, ov.currentIndex());
        ov.keyPressed(key(KeyEvent.VK_LEFT));
        assertEquals(0, ov.currentIndex());
        ov.keyPressed(key(KeyEvent.VK_ESCAPE));
        assertFalse(ov.isActive());
    }

    @Test
    public void renderPopulatesSpotlightAndButtonHitboxes()
    {
        TutorialOverlay ov = overlay();
        ov.start(threeStepTour());
        ov.render(g);

        assertFalse("Next hitbox", ov.nextHit.isEmpty());
        assertFalse("Skip hitbox", ov.skipHit.isEmpty());
        assertTrue("no Back on step 0", ov.backHit.isEmpty());
        assertTrue("spotlight rings the target", ov.spotlight.contains(new Point(660, 71)));
        assertFalse("caption drawn", ov.captionBounds.isEmpty());

        ov.next(); // step B has no target -> centred caption, no spotlight
        ov.render(g);
        assertTrue("no spotlight without a target", ov.spotlight.isEmpty());
        assertFalse("Back shown from step 1", ov.backHit.isEmpty());
        assertTrue("caption on-canvas", ov.captionBounds.x >= 0 && ov.captionBounds.y >= 0);
    }

    @Test
    public void captionFlipsAboveWhenTargetSitsLowOnScreen()
    {
        TutorialOverlay ov = overlay();
        ov.start(new Tour("low", "Low", singletonList(step("Low", new Rectangle(350, 545, 120, 22))), null, null));
        ov.render(g);
        assertFalse(ov.spotlight.isEmpty());
        assertTrue("caption flips above the spotlight",
            ov.captionBounds.y + ov.captionBounds.height <= ov.spotlight.y);
    }

    @Test
    public void sidePanelCaptionHugsTheRightEdge()
    {
        TutorialStep panelStep = TutorialStep.builder("Panel", "A panel step, no spotlight, caption by the side panel.")
            .captionBySidePanel().build();
        TutorialOverlay ov = overlay();
        ov.start(new Tour("p", "P", singletonList(panelStep), null, null));
        ov.render(g);
        assertTrue("no spotlight", ov.spotlight.isEmpty());
        assertTrue("caption is against the right edge",
            ov.captionBounds.x + ov.captionBounds.width >= 800 - 20);
    }

    @Test
    public void clickingTheNextButtonAdvances()
    {
        TutorialOverlay ov = overlay();
        ov.start(threeStepTour());
        ov.render(g);
        Rectangle nb = ov.nextHit;
        ov.mousePressed(press(nb.x + nb.width / 2, nb.y + nb.height / 2));
        assertEquals(1, ov.currentIndex());
    }

    @Test
    public void clickingSkipEndsTheTour()
    {
        TutorialOverlay ov = overlay();
        ov.start(threeStepTour());
        ov.render(g);
        Rectangle sb = ov.skipHit;
        ov.mousePressed(press(sb.x + sb.width / 2, sb.y + sb.height / 2));
        assertFalse(ov.isActive());
    }

    @Test
    public void inertWhenNoTourIsRunning()
    {
        TutorialOverlay ov = overlay();
        assertFalse(ov.isActive());
        assertEquals(null, ov.render(g)); // no NPE, nothing drawn
        MouseEvent e = press(10, 10);
        ov.mousePressed(e);
        assertFalse("passes clicks through when idle", e.isConsumed());
    }
}
