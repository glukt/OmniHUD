package com.osrscopilot.combat.tutorial;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * The Slayer guided tour: how a task gets tracked, how an umbrella task's subtype picker works,
 * where the current monster spawns, and where its drop table lives. Drives the Slayer tab on a
 * throwaway demo task ("Metal dragons", x35) - nothing the player has is touched, and the real
 * task is restored on finish.
 *
 * <p>Everything plugin-side (installing the demo task, ringing a section, snapping the World Map,
 * opening the Bestiary drop table) lives behind {@link Host}, implemented by the plugin.
 */
public final class SlayerTour
{
    public static final String ID = "slayer";
    public static final String NAME = "Slayer tour";

    /** The group member the demo "kills" so the picker + spawns have something concrete to show. */
    private static final String DEMO_KILL = "Rune dragon";
    /** The member the demo pins by hand, to show the manual override. */
    private static final String DEMO_PIN = "Adamant dragon";

    private SlayerTour()
    {
    }

    /** Everything the steps need the plugin to do. */
    public interface Host
    {
        /** Bring the side panel to the Slayer tab. */
        void showSlayerTab();
        /** Bring the side panel to the Loot tab. */
        void showLootTab();
        /** Install the non-persisted demo task ("Metal dragons", x35, from Duradel). */
        void installDemoTask();
        /** Install a non-persisted canned Rune-dragon loot history for the Loot-tab step. */
        void installDemoLoot();
        /** Auto mode: record that the player just killed this group member (drives picker + spawns). */
        void noteKill(String npcName);
        /** Pin a specific subtype in the picker, or null to go back to Auto. */
        void pinSubtype(String member);
        /** Ring a part of the Slayer tab: {@code task} / {@code picker} / {@code spawns} /
         *  {@code rewards} / {@code masters} / null. */
        void focus(String key);
        /** Open the World Map + beacon on the current task monster's first spawn zone. */
        void snapSpawn();
        /** Open the Bestiary drop table for the current task monster (switches the side panel). */
        void openDrops();
        /** Screen bounds of the open World Map frame, or null if it isn't up. */
        Rectangle worldMapBounds();
        /** Close the World Map if the tour opened it. */
        void closeMap();
        /** Remove the demo task, clear highlights, and return to the tab the user started on. */
        void teardown();
    }

    public static Tour build(Host h)
    {
        java.util.function.Supplier<Rectangle> map = h::worldMapBounds;

        Runnable onStart = () ->
        {
            h.installDemoTask();
            h.installDemoLoot();
            h.showSlayerTab();
        };
        Runnable onFinish = () ->
        {
            h.focus(null);
            h.closeMap();
            h.teardown();
        };

        List<TutorialStep> s = new ArrayList<>();

        s.add(TutorialStep.builder("The Slayer tab",
            "A quick look at how tasks are tracked, how you find where to fight, and where the drop "
                + "table is. About a minute - press Esc to leave any time. (This is a demo task; your "
                + "real one is untouched.)")
            .onEnter(h::showSlayerTab)
            .build());

        s.add(TutorialStep.builder("Your task, tracked for you",
            "When a master assigns a task - or you check your gem - it lands here: the monster, the "
                + "count, your master, and a live progress bar. Nothing to type in.")
            .captionBySidePanel()
            .onEnter(() -> { h.showSlayerTab(); h.focus("task"); })
            .build());

        s.add(TutorialStep.builder("Umbrella tasks",
            "\"Metal dragons\" is one task covering six dragons. The Killing: picker chooses which "
                + "one's spawns and drops the tab shows - task credit counts for the whole group "
                + "either way.")
            .captionBySidePanel()
            .onEnter(() -> h.focus("picker"))
            .build());

        s.add(TutorialStep.builder("It follows your kills",
            "On Auto it watches what you're actually fighting. Kill a Rune dragon and the picker - "
                + "and the spawn list below - switch to Rune dragons on their own.")
            .captionBySidePanel().demo()
            .onEnter(() -> { h.noteKill(DEMO_KILL); h.focus("picker"); })
            .build());

        s.add(TutorialStep.builder("...or pin one yourself",
            "Prefer to lock it? Pick a member from the list. Here it's pinned to Adamant dragons; "
                + "set it back to Auto whenever.")
            .captionBySidePanel().demo()
            .onEnter(() -> { h.pinSubtype(DEMO_PIN); h.focus("picker"); })
            .build());

        s.add(TutorialStep.builder("Spawn locations",
            "Back on Auto - tracking Rune dragons. Every known spawn zone for what you're killing is "
                + "listed here, closest-fit first. A Konar location match is flagged in gold.")
            .captionBySidePanel().demo()
            .onEnter(() -> { h.pinSubtype(null); h.noteKill(DEMO_KILL); h.focus("spawns"); })
            .build());

        s.add(TutorialStep.builder("Snap the map to a spawn",
            "Every spawn card opens the World Map on the spot with a beacon. Confirmed dungeon "
                + "entrances get a sharp pin (like this one); ones still being verified show a soft "
                + "\"approximate area\" instead.")
            .target(map).demo()
            .onEnter(() -> { h.focus(null); h.snapSpawn(); })
            .build());

        s.add(TutorialStep.builder("The drop table",
            "Mob Details opens the full Bestiary entry: every drop and its rate, the monster's "
                + "weakness, and quantities. It's the reference table - what it <i>can</i> drop.")
            .captionBySidePanel().demo()
            .onEnter(() -> { h.closeMap(); h.openDrops(); })
            .build());

        s.add(TutorialStep.builder("Your drops & luck",
            "The Loot tab is what you've <i>actually</i> had. Per source: every drop with your rate "
                + "vs the wiki rate (1/12 vs 1/128), GP and GP/hr, and how lucky or dry you are. "
                + "Here's ~90 Rune dragon kills on this task.")
            .captionBySidePanel().demo()
            .onEnter(h::showLootTab)
            .build());

        s.add(TutorialStep.builder("Slayer reward points",
            "Back on the Slayer tab. Point rewards, grouped: Unlocks (abilities, monster unlocks), "
                + "Extensions (longer tasks), Cosmetic (helmet recolours), and Buys. Each shows its "
                + "cost or an [Unlocked] tag.")
            .captionBySidePanel()
            .onEnter(() -> { h.showSlayerTab(); h.focus("rewards"); })
            .build());

        s.add(TutorialStep.builder("Every master",
            "Every Slayer Master with their combat / Slayer-level requirements and full task lists. "
                + "A master you can't use yet is greyed with the requirement; an unlock-gated or "
                + "blocked task is greyed in its list - right-click a task to block or unblock it.")
            .captionBySidePanel()
            .onEnter(() -> h.focus("masters"))
            .build());

        s.add(TutorialStep.builder("That's the tour",
            "Reopen it any time from the About tab. Your real task is back and the panel's on the tab "
                + "you started from.")
            .onEnter(() -> { h.focus(null); h.closeMap(); })
            .build());

        return new Tour(ID, NAME, s, onStart, onFinish);
    }
}
