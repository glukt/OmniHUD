package com.osrscopilot.combat.tutorial;

import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.model.EncounterSegment;
import com.osrscopilot.combat.overlay.CombatMeterOverlay;
import com.osrscopilot.combat.overlay.CombatMeterOverlay.MetricMode;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Builds the ~11-step guided walkthrough of the on-canvas Combat HUD. Every spotlight is anchored
 * to a live {@link CombatMeterOverlay#getTutorialAnchors() anchor} so it tracks the real layout,
 * and every DEMO step drives the HUD's own widgets against the staged {@link CombatTutorialFixture}
 * - the real encounter manager is only ever put into (and taken out of) demo mode.
 */
public final class CombatHudTour
{
    public static final String ID = "combat-hud";
    public static final String NAME = "Combat HUD tour";

    private CombatHudTour()
    {
    }

    /**
     * @param hud                 the live Combat HUD overlay (anchors + DEMO widget control)
     * @param mgr                 the encounter manager (demo-mode override only)
     * @param openPanel           opens the Combat side panel (the hand-off + panel steps; may be null)
     * @param openSourceDetails   opens the "Damage Sources" window on the fixture (may be null)
     * @param closeSourceDetails  hides that window again (may be null)
     * @param panelScrollTo       scrolls / expands a section of the Combat panel for the panel steps -
     *                            keys "top", "damage", "ledger", "share" (may be null)
     */
    public static Tour build(CombatMeterOverlay hud, CombatEncounterManager mgr,
        Runnable openPanel, Runnable openSourceDetails, Runnable closeSourceDetails,
        java.util.function.Consumer<String> panelScrollTo)
    {
        EncounterSegment fixture = CombatTutorialFixture.sampleEncounter();
        List<EncounterSegment> dropdown = CombatTutorialFixture.dropdownFor(fixture);
        CombatEncounterManager.LiveBossProgress boss = CombatTutorialFixture.sampleBossProgress();

        Runnable onStart = () ->
        {
            mgr.setDemoOverride(fixture, dropdown, boss);
            hud.beginTutorialLayout();
            if (hud.getGraphOverlay() != null)
            {
                hud.getGraphOverlay().beginTutorialLayout(hud::getBounds);
            }
        };
        Runnable onFinish = () ->
        {
            hud.setSettingsOpen(false);
            hud.setGraphOpen(false);
            hud.setScopeDropdownOpen(false);
            if (closeSourceDetails != null)
            {
                closeSourceDetails.run();
            }
            if (panelScrollTo != null)
            {
                panelScrollTo.accept(null); // clear the panel section highlights
            }
            if (hud.getGraphOverlay() != null)
            {
                hud.getGraphOverlay().endTutorialLayout();
            }
            hud.endTutorialLayout();
            mgr.clearDemoOverride();
        };

        Supplier<Rectangle> bars = anchor(hud, "bars");
        Supplier<Rectangle> metric = anchor(hud, "metric");
        // The demo opens a popup below the header for these - spotlight the popup once it is up, so
        // it is the un-dimmed cut-out and the caption places clear of it.
        Supplier<Rectangle> scope = anchor(hud, "scopemenu", "scope");
        Supplier<Rectangle> bossLine = anchor(hud, "bossline");
        Supplier<Rectangle> miniFooter = anchor(hud, "minifooter");
        Supplier<Rectangle> graph = anchor(hud, "graphcard", "graph");
        Supplier<Rectangle> settings = anchor(hud, "settingscard", "settings");
        Supplier<Rectangle> panel = anchor(hud, "panel");

        MetricMode[] savedMode = new MetricMode[1];

        List<TutorialStep> steps = new ArrayList<>();

        steps.add(TutorialStep.builder("Welcome to the Combat HUD",
            "This tracks every fight in real time. About 90 seconds - press Esc to leave any time. "
                + "The numbers you'll see are a staged God Wars Dungeon kill, not your own data.")
            .build());

        steps.add(TutorialStep.builder("The bars",
            "Each bar is a combatant in the fight - you and every party member - ranked by the "
                + "current metric. Your cannon and thrall roll into your bar; the fill shows each "
                + "one's share of the total.")
            .target(bars).build());

        steps.add(TutorialStep.builder("Pick a metric",
            "The metric pill cycles Damage, Taken, Healing and Supplies. Every bar and number "
                + "re-reads for that view.")
            .target(metric).demo()
            .onEnter(() -> { savedMode[0] = hud.getCurrentMode(); hud.cycleMode(); })
            .onExit(() -> { if (savedMode[0] != null) hud.setMode(savedMode[0]); })
            .build());

        steps.add(TutorialStep.builder("Choose a scope",
            "The scope pill switches between this fight, the current session, all-time, your party, "
                + "and any past kill. A merged \"Bloodveld x21\" row opens into every individual kill.")
            .target(scope).demo()
            .onEnter(() -> hud.setScopeDropdownOpen(true))
            .onExit(() -> hud.setScopeDropdownOpen(false))
            .build());

        steps.add(TutorialStep.builder("Boss fight progress",
            "While you're on one target with a health bar, this line shows its HP%, an estimated "
                + "time-to-kill, and an on-pace kills-per-hour.")
            .target(bossLine).build());

        steps.add(TutorialStep.builder("The footer at a glance",
            "Damage taken, healing, potions and food for the current fight - always one glance away. "
                + "Toggle it from the settings.")
            .target(miniFooter).build());

        steps.add(TutorialStep.builder("Right-click for the breakdown",
            "Right-click any bar for the full source breakdown - every weapon and spell, hit-by-hit "
                + "accuracy, spec versus normal, per-target damage, and a hit-size histogram.")
            .target(bars).demo()
            .onEnter(() -> { if (openSourceDetails != null) openSourceDetails.run(); })
            .onExit(() -> { if (closeSourceDetails != null) closeSourceDetails.run(); })
            .build());

        steps.add(TutorialStep.builder("The fight graph",
            "This toggles the fight graph - DPS over time, overall or per style, with a peak marker "
                + "and an adjustable rolling average.")
            .target(graph).demo()
            .onEnter(() -> hud.setGraphOpen(true))
            .onExit(() -> hud.setGraphOpen(false))
            .build());

        steps.add(TutorialStep.builder("Make it yours",
            "Bar colour and size, corner radius, text shadow, a compact or hidden header, the graph "
                + "style - all here, and the card scrolls.")
            .target(settings).demo()
            .onEnter(() -> hud.setSettingsOpen(true))
            .onExit(() -> hud.setSettingsOpen(false))
            .build());

        steps.add(TutorialStep.builder("Open the Combat panel",
            "Everything on the HUD, in depth, lives in the Combat panel. Click here to open it - "
                + "or press Next and the tour will open it for you.")
            .target(panel)
            .gated(e ->
            {
                Rectangle r = panel.get();
                boolean hit = r != null && r.width > 0 && r.contains(e.getPoint());
                if (hit && openPanel != null)
                {
                    openPanel.run();
                }
                return hit;
            })
            .build());

        // ---- Panel walk-through: same staged Graardor fight, seen in the sidebar. Captions sit on
        // the left so the panel on the right stays visible; each step scrolls/expands its section.
        Runnable ensurePanel = () ->
        {
            if (openPanel != null)
            {
                openPanel.run();
            }
        };

        steps.add(TutorialStep.builder("The headline rates",
            "Top of the panel: DPS, damage taken per second, healing per second and GP spent, for "
                + "whichever scope is selected. This is the same staged fight you just saw on the HUD.")
            .captionBySidePanel()
            .onEnter(() ->
            {
                ensurePanel.run();
                if (panelScrollTo != null) panelScrollTo.accept("top");
            })
            .build());

        steps.add(TutorialStep.builder("Damage Dealt, in full",
            "Peak DPS, attack uptime, hit accuracy and max hit - then the split BY STYLE and BY "
                + "WEAPON. Every weapon, spell, your cannon and your thrall get a row here; click one "
                + "for its per-hit detail and a hit-size histogram.")
            .captionBySidePanel()
            .onEnter(() ->
            {
                ensurePanel.run();
                if (panelScrollTo != null) panelScrollTo.accept("damage");
            })
            .build());

        steps.add(TutorialStep.builder("The action ledger",
            "A timestamped log of the fight - every hit, special attack, heal, prayer swap and death. "
                + "The Full Log button opens the complete stream with search and a jump-to-death.")
            .captionBySidePanel()
            .onEnter(() ->
            {
                ensurePanel.run();
                if (panelScrollTo != null) panelScrollTo.accept("ledger");
            })
            .build());

        steps.add(TutorialStep.builder("Share it",
            "Copy a one-line summary to paste into chat, or send it straight to public / friends / "
                + "clan chat. The plugin never sends anything for you. Party shares your live numbers "
                + "with a RuneLite party for a combined group meter.")
            .captionBySidePanel()
            .onEnter(() ->
            {
                ensurePanel.run();
                if (panelScrollTo != null) panelScrollTo.accept("share");
            })
            .build());

        steps.add(TutorialStep.builder("That's the tour",
            "Reopen it any time from the About tab or the \"?\" on the HUD. The DEMO badge is gone - "
                + "you're back on your real fight.")
            .build());

        return new Tour(ID, NAME, steps, onStart, onFinish);
    }

    private static Supplier<Rectangle> anchor(CombatMeterOverlay hud, String... keys)
    {
        return () ->
        {
            Map<String, Rectangle> a = hud.getTutorialAnchors();
            if (a == null)
            {
                return null;
            }
            for (String key : keys)
            {
                Rectangle r = a.get(key);
                if (r != null && r.width > 0 && r.height > 0)
                {
                    return r;
                }
            }
            return null;
        };
    }
}
