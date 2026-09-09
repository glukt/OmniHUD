package com.osrscopilot.combat.tutorial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * An ordered set of {@link TutorialStep coach-marks}, plus optional whole-tour setup / teardown.
 *
 * <p>{@code onStart} runs once before the first step (the Combat tour uses it to install the demo
 * fixture on {@code CombatEncounterManager}); {@code onFinish} runs once when the tour ends for any
 * reason - Done, Skip, Esc, or a new tour replacing it - and must restore whatever {@code onStart}
 * changed. Both are optional and are guaranteed to run at most once per tour run.
 */
@Slf4j
public final class Tour
{
    private final String id;
    private final String displayName;
    private final List<TutorialStep> steps;
    private final Runnable onStart;
    private final Runnable onFinish;

    public Tour(String id, String displayName, List<TutorialStep> steps)
    {
        this(id, displayName, steps, null, null);
    }

    public Tour(String id, String displayName, List<TutorialStep> steps, Runnable onStart, Runnable onFinish)
    {
        this.id = id != null ? id : "tour";
        this.displayName = displayName != null ? displayName : this.id;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps != null ? steps : Collections.emptyList()));
        this.onStart = onStart;
        this.onFinish = onFinish;
    }

    public String getId()
    {
        return id;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public int getStepCount()
    {
        return steps.size();
    }

    public TutorialStep getStep(int index)
    {
        return steps.get(index);
    }

    public List<TutorialStep> getSteps()
    {
        return steps;
    }

    void start()
    {
        run(onStart);
    }

    void finish()
    {
        run(onFinish);
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
        catch (RuntimeException e)
        {
            log.trace("Tour callback threw", e);
        }
    }
}
