package com.osrscopilot.combat.tutorial;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * One coach-mark in a {@link Tour}: where on the canvas to point, what to say, and how the player
 * gets past it.
 *
 * <ul>
 *   <li>{@link Mode#GUIDED} - the ring points; the player advances with Next when ready (the default).</li>
 *   <li>{@link Mode#GATED} - the tour waits for a real click on the highlighted control
 *       ({@link #advancesOn}); a Next button is still shown as an escape hatch.</li>
 *   <li>{@link Mode#DEMO} - the step performs the action itself on the sample data via
 *       {@link #enter()} (open a card, cycle a control); {@link #exit()} undoes it.</li>
 * </ul>
 *
 * <p>{@code target} is resolved fresh every frame (never cached) so the ring tracks live layout.
 * A null or empty target renders a centred caption with no spotlight - used for the intro / outro.
 */
public final class TutorialStep
{
    public enum Mode { GUIDED, GATED, DEMO }

    /** Where an un-spotlit caption sits: {@code AUTO} = upper-centre; {@code SIDE_PANEL} = pinned to
     *  the canvas's right edge, right next to the RuneLite side panel it is describing. */
    public enum Placement { AUTO, SIDE_PANEL }

    private final String title;
    private final String body;
    private final Mode mode;
    private final Placement placement;
    private final Supplier<Rectangle> target;
    private final Runnable onEnter;
    private final Runnable onExit;
    private final Predicate<MouseEvent> advanceOn;

    private TutorialStep(Builder b)
    {
        this.title = b.title;
        this.body = b.body;
        this.mode = b.mode;
        this.placement = b.placement;
        this.target = b.target;
        this.onEnter = b.onEnter;
        this.onExit = b.onExit;
        this.advanceOn = b.advanceOn;
    }

    public Placement getPlacement()
    {
        return placement;
    }

    public String getTitle()
    {
        return title;
    }

    public String getBody()
    {
        return body;
    }

    public Mode getMode()
    {
        return mode;
    }

    /** The control to spotlight, in canvas coordinates, or null / empty for a centred caption. */
    public Rectangle resolveTarget()
    {
        if (target == null)
        {
            return null;
        }
        try
        {
            return target.get();
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    /** Run this step's setup hook (DEMO steps open their card here). Never throws. */
    public void enter()
    {
        run(onEnter);
    }

    /** Undo whatever {@link #enter()} did. Never throws. */
    public void exit()
    {
        run(onExit);
    }

    public boolean advancesOn(MouseEvent e)
    {
        if (mode != Mode.GATED || advanceOn == null || e == null)
        {
            return false;
        }
        try
        {
            return advanceOn.test(e);
        }
        catch (RuntimeException ignored)
        {
            return false;
        }
    }

    private static void run(Runnable r)
    {
        if (r == null)
        {
            return;
        }
        try
        {
            r.run();
        }
        catch (RuntimeException ignored)
        {
            // a bad hook must never break the tour or the render loop
        }
    }

    public static Builder builder(String title, String body)
    {
        return new Builder(title, body);
    }

    public static final class Builder
    {
        private final String title;
        private final String body;
        private Mode mode = Mode.GUIDED;
        private Placement placement = Placement.AUTO;
        private Supplier<Rectangle> target;
        private Runnable onEnter;
        private Runnable onExit;
        private Predicate<MouseEvent> advanceOn;

        private Builder(String title, String body)
        {
            this.title = title != null ? title : "";
            this.body = body != null ? body : "";
        }

        public Builder target(Supplier<Rectangle> target)
        {
            this.target = target;
            return this;
        }

        public Builder guided()
        {
            this.mode = Mode.GUIDED;
            return this;
        }

        public Builder gated(Predicate<MouseEvent> advanceOn)
        {
            this.mode = Mode.GATED;
            this.advanceOn = advanceOn;
            return this;
        }

        public Builder demo()
        {
            this.mode = Mode.DEMO;
            return this;
        }

        /** Pin this step's (un-spotlit) caption to the right edge, next to the side panel. */
        public Builder captionBySidePanel()
        {
            this.placement = Placement.SIDE_PANEL;
            return this;
        }

        public Builder onEnter(Runnable onEnter)
        {
            this.onEnter = onEnter;
            return this;
        }

        public Builder onExit(Runnable onExit)
        {
            this.onExit = onExit;
            return this;
        }

        public TutorialStep build()
        {
            return new TutorialStep(this);
        }
    }
}
