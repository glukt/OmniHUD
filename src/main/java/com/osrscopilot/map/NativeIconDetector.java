package com.osrscopilot.map;

import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.model.Shop;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Determines whether a shop's on-map marker sits exactly on top of a native OSRS world map icon,
 * so callers can swap the plugin's own pin for a subtle "halo" highlight around the existing icon
 * instead of drawing a second, competing dot.
 *
 * This is deliberately curated-data-driven rather than live-detection-based: {@link ShopDatabase}
 * maintains a hand-verified table ({@code KNOWN_NATIVE_SHOP_ALIGNMENTS}) of shops whose native map
 * symbol coordinate is known exactly. Live scanning of RuneLite's {@code WorldMapRenderer} icons was
 * evaluated and rejected (see project research notes) - it only covers currently-rendered regions,
 * has no ready-made "shop" icon category constant, and would require empirical, in-client discovery
 * this environment cannot perform. The curated table is the only source of truth this class trusts.
 *
 * <p>Membership is read from {@link ShopDatabase#knownNativeAlignedShopNames()} (cached once, see
 * {@link #loadCuratedShopNames()}), rather than duplicating that table's contents here or inferring
 * membership from "does the aligned point differ from the raw point". The latter was the original
 * approach but proved unsound: if a shop's underlying NPC spawn data ever happens to already sit
 * exactly on the curated coordinate (raw == aligned), that heuristic silently stops flagging it
 * even though the shop is genuinely in the curated table.</p>
 */
public final class NativeIconDetector
{
    private static final Set<String> CURATED_SHOP_NAMES = loadCuratedShopNames();

    private NativeIconDetector()
    {
    }

    /**
     * @return true if this shop's marker is known (via the curated alignment table) to sit exactly
     * on top of a pre-existing native OSRS world map icon.
     */
    public static boolean isNativeIconAligned(ShopDatabase shopDatabase, Shop shop)
    {
        if (shopDatabase == null || shop == null)
        {
            return false;
        }

        if (!CURATED_SHOP_NAMES.isEmpty())
        {
            return CURATED_SHOP_NAMES.contains(shop.getName());
        }

        // Fallback only reached if reflection into ShopDatabase ever fails (e.g. the field is
        // renamed by a future change) - infer membership from the fact that getAlignedShopLocation
        // repositions the shop even though no explicit per-shop override exists. An explicit
        // per-shop map-location override is a separate curation mechanism (arbitrary building-
        // center repositioning), not a claim that a native map icon exists there, so it's excluded.
        if (shop.hasExplicitMapLocation())
        {
            return false;
        }

        WorldPoint aligned = shopDatabase.getAlignedShopLocation(shop);
        WorldPoint raw = shop.getWorldPoint();
        return aligned != null && !aligned.equals(raw);
    }

    private static Set<String> loadCuratedShopNames()
    {
        return new HashSet<>(ShopDatabase.knownNativeAlignedShopNames());
    }
}
