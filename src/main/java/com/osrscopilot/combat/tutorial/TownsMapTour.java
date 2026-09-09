package com.osrscopilot.combat.tutorial;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * The Towns &amp; Map guided tour: the Towns / Shops / Search directories, and the part people
 * miss - how the <b>Map</b> button on any shop, monster or town opens the real World Map and drops
 * a beacon on it. Drives the panel (switches tabs, selects a town, runs a search) and opens the
 * real map on real locations - there is no staged data.
 *
 * <p>All the plugin-side specifics (which town, which shop, resolving a monster's spawn point,
 * opening / closing the World Map) live behind {@link Host}, implemented by the plugin.
 */
public final class TownsMapTour
{
    public static final String ID = "towns-map";
    public static final String NAME = "Towns & Map tour";

    private static final String FEATURED_SEARCH = "rune scimitar";

    private TownsMapTour()
    {
    }

    /** Everything the steps need the plugin to do. */
    public interface Host
    {
        void showTownsTab();
        void showShopsTab();
        void showSearchTab();

        /** Select the featured town (Al Kharid) in the Towns tab. */
        void selectFeaturedTown();
        /** Ring part of the Towns tab: {@code town} / {@code firstShop} / {@code mapButton} / null. */
        void townFocus(String key);

        /** Load the featured shop (Al Kharid General Store) into the Shops tab. */
        void showFeaturedShop();
        /** Ring part of the Shops tab: {@code hero} / {@code map} / {@code filters} / {@code items} / null. */
        void stockFocus(String key);

        void search(String query);
        /** Ring part of the Search tab: {@code box} / {@code results} / null. */
        void searchFocus(String key);

        /** Open the World Map + beacon on the featured shop / monster spawn / town. */
        void snapFeaturedShop();
        void snapFeaturedMonster();
        void snapFeaturedTown();

        /** Screen bounds of the open World Map frame, or null if it isn't up. */
        Rectangle worldMapBounds();
        /** Close the World Map if the tour opened it. */
        void closeMap();
        /** Return the side panel to the tab the user had before the tour. */
        void restoreTab();
    }

    public static Tour build(Host h)
    {
        java.util.function.Supplier<Rectangle> map = h::worldMapBounds;

        Runnable onFinish = () ->
        {
            h.townFocus(null);
            h.stockFocus(null);
            h.searchFocus(null);
            h.closeMap();
            h.restoreTab();
        };

        List<TutorialStep> s = new ArrayList<>();

        s.add(TutorialStep.builder("Welcome",
            "Towns, shops, and how to pin any of them on the in-game World Map. About 90 seconds - "
                + "press Esc to leave any time.")
            .build());

        s.add(TutorialStep.builder("The three lists",
            "Towns groups every settlement with its vendors. Shops is the flat vendor list. Search is "
                + "one box across both. Same tab strip up top.")
            .captionBySidePanel()
            .onEnter(h::showTownsTab)
            .build());

        s.add(TutorialStep.builder("A town hub",
            "Each settlement - its trade role, F2P or members, quest-locked services, and every shop "
                + "within walking distance.")
            .captionBySidePanel()
            .onEnter(() -> { h.showTownsTab(); h.selectFeaturedTown(); h.townFocus("town"); })
            .build());

        s.add(TutorialStep.builder("A shop in the town",
            "Its stock, buy and sell prices, restock timers, and whether an ironman can actually use it.")
            .captionBySidePanel()
            .onEnter(() -> h.townFocus("firstShop"))
            .build());

        s.add(TutorialStep.builder("The Map button",
            "This is the one to know. Every shop, monster and town has it - it opens the World Map "
                + "and snaps to the place.")
            .captionBySidePanel()
            .onEnter(() -> h.townFocus("mapButton"))
            .build());

        s.add(TutorialStep.builder("Watch it snap",
            "There it is - the World Map opened and jumped to the exact tile, with a beacon on the "
                + "spot. Drag or zoom from here as normal.")
            .target(map).demo()
            .onEnter(() -> { h.townFocus(null); h.snapFeaturedShop(); })
            .build());

        s.add(TutorialStep.builder("Markers vs the beacon",
            "Vendor and town markers are always on the map; the beacon is just the last thing you "
                + "asked for. In-game, right-click a minimap vendor to jump straight to its stock.")
            .target(map)
            .build());

        s.add(TutorialStep.builder("The Shops tab",
            "Every shop, filterable - 'sells runes', members-only, by region. Each one has the same "
                + "Map button.")
            .captionBySidePanel()
            .onEnter(() -> { h.closeMap(); h.showShopsTab(); h.showFeaturedShop(); h.stockFocus("filters"); })
            .build());

        s.add(TutorialStep.builder("Reading a stock list",
            "Each item shows the shop price against the Grand Exchange price, so the margin is one "
                + "glance away.")
            .captionBySidePanel()
            .onEnter(() -> h.stockFocus("items"))
            .build());

        s.add(TutorialStep.builder("Universal search",
            "One box across every shop inventory and every monster drop table at once.")
            .captionBySidePanel()
            .onEnter(() -> { h.showSearchTab(); h.searchFocus("box"); })
            .build());

        s.add(TutorialStep.builder("Search it",
            "Shops that sell \"" + FEATURED_SEARCH + "\" on one side, monsters that drop it on the "
                + "other - with the drop rate.")
            .captionBySidePanel().demo()
            .onEnter(() -> { h.search(FEATURED_SEARCH); h.searchFocus("results"); })
            .build());

        s.add(TutorialStep.builder("Jump from a result",
            "Every result has its own Map button. One click from \"where do I buy this\" to standing "
                + "at the door.")
            .target(map).demo()
            .onEnter(() -> { h.searchFocus(null); h.snapFeaturedShop(); })
            .build());

        s.add(TutorialStep.builder("The same button on monsters",
            "The Bestiary's spawn zones have it too - here's a boss's lair on the map. Confirmed "
                + "entrances get a sharp pin; ones we haven't verified yet show a soft \"approximate "
                + "area\" instead.")
            .target(map).demo()
            .onEnter(h::snapFeaturedMonster)
            .build());

        s.add(TutorialStep.builder("...and on towns",
            "And every town hub. Search a name, or open a town and hit Map.")
            .target(map).demo()
            .onEnter(h::snapFeaturedTown)
            .build());

        s.add(TutorialStep.builder("That's the tour",
            "Reopen it any time from the About tab. The World Map's back to normal and you're on the "
                + "tab you started from.")
            .onEnter(h::closeMap)
            .build());

        return new Tour(ID, NAME, s, () -> { }, onFinish);
    }
}
