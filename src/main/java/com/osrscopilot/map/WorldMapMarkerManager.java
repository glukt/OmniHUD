package com.osrscopilot.map;

import com.osrscopilot.OsrsCopilotConfig;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.model.MembershipFilter;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.data.model.TownNode;
import com.osrscopilot.util.IconCache;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

@Slf4j
@Singleton
public class WorldMapMarkerManager
{
    private final Client client;
    private final ClientThread clientThread;
    private final WorldMapPointManager worldMapPointManager;
    private final ShopDatabase shopDatabase;
    private final OsrsCopilotConfig config;
    private final IconCache iconCache;

    private final List<WorldMapPoint> activePoints = new ArrayList<>();
    private final List<VendorMapNode> activeVendorNodes = new ArrayList<>();
    private final List<TownMapNode> activeTownNodes = new ArrayList<>();

    private String selectedCategoryFilter = "All";

    @Inject
    public WorldMapMarkerManager(
        Client client,
        ClientThread clientThread,
        WorldMapPointManager worldMapPointManager,
        ShopDatabase shopDatabase,
        OsrsCopilotConfig config,
        IconCache iconCache)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.worldMapPointManager = worldMapPointManager;
        this.shopDatabase = shopDatabase;
        this.config = config;
        this.iconCache = iconCache;
    }

    public void setCategoryFilter(String category)
    {
        this.selectedCategoryFilter = category != null ? category : "All";
        rebuildMarkers();
    }

    public String getSelectedCategoryFilter()
    {
        return selectedCategoryFilter;
    }

    public void rebuildMarkers()
    {
        clientThread.invokeLater(this::rebuildMarkersInternal);
    }

    private synchronized void rebuildMarkersInternal()
    {
        clearMarkersInternal();

        boolean snapToEdge = config.snapToEdge();
        boolean alignToNative = config.alignToNativeIcons();
        boolean highlightNative = config.highlightNativeIcons();
        MembershipFilter memFilter = config.membershipFilter();
        int maxDist = config.searchDistanceLimit();

        WorldPoint playerLoc = (client.getLocalPlayer() != null)
            ? client.getLocalPlayer().getWorldLocation()
            : null;

        // 1. Build Town Nodes (only when Towns category is active or "All")
        if (config.enableTownMarkers() && ("All".equalsIgnoreCase(selectedCategoryFilter) || "Towns".equalsIgnoreCase(selectedCategoryFilter)))
        {
            for (TownNode town : shopDatabase.getAllTowns())
            {
                TownMapNode node = new TownMapNode(town, iconCache.getTownIcon(), snapToEdge);
                activePoints.add(node);
                activeTownNodes.add(node);
                worldMapPointManager.add(node);
            }
        }

        // 2. Build Category-Themed Vendor Nodes
        if (config.showVendorIcons())
        {
            for (Shop shop : shopDatabase.getAllShops())
            {
                if (shop.getWorldX() == 0 || shop.getWorldY() == 0)
                {
                    continue;
                }

                if (memFilter == MembershipFilter.F2P_ONLY && shop.isMembersOnly())
                {
                    continue;
                }
                if (memFilter == MembershipFilter.MEMBERS_ONLY && !shop.isMembersOnly())
                {
                    continue;
                }

                if (maxDist > 0 && playerLoc != null)
                {
                    if (playerLoc.distanceTo(shop.getWorldPoint()) > maxDist)
                    {
                        continue;
                    }
                }

                String category = getShopPrimaryCategory(shop);
                if (!matchesCategoryFilter(shop, category))
                {
                    continue;
                }

                // Resolve aligned WorldPoint directly over the shop building or native OSRS map symbol
                WorldPoint displayPoint = alignToNative
                    ? shopDatabase.getAlignedShopLocation(shop)
                    : shop.getWorldPoint();

                // A shop is "native icon aligned" only when we're actually displaying it at the
                // curated coordinate (alignToNative on) AND that coordinate is known (via the
                // curated ShopDatabase table, see NativeIconDetector) to sit on a pre-existing
                // native OSRS map icon - not merely an explicit per-shop map-location override.
                boolean nativeAligned = alignToNative && NativeIconDetector.isNativeIconAligned(shopDatabase, shop);

                // When highlighted, swap this plugin's colored badge for a fully transparent icon:
                // the halo highlight (WorldMapShopTooltipOverlay) draws the visual treatment instead,
                // while this node stays registered at the exact same point for hover/click handling.
                BufferedImage icon = (nativeAligned && highlightNative)
                    ? iconCache.getInvisibleIcon()
                    : iconCache.getIconForCategory(category);

                VendorMapNode node = new VendorMapNode(shop, displayPoint, icon, snapToEdge, nativeAligned);
                activePoints.add(node);
                activeVendorNodes.add(node);
                worldMapPointManager.add(node);
            }
        }

        log.debug("Rebuilt OmniHUD map markers: {} active points (Filter: {}, Aligned: {})",
            activePoints.size(), selectedCategoryFilter, alignToNative);
    }

    private String getShopPrimaryCategory(Shop shop)
    {
        return shop.getCategory();
    }

    private boolean matchesCategoryFilter(Shop shop, String primaryCategory)
    {
        if (selectedCategoryFilter == null || "All".equalsIgnoreCase(selectedCategoryFilter))
        {
            return true;
        }

        String filter = selectedCategoryFilter.toLowerCase();
        List<String> tags = shop.getTags();

        if (filter.contains("magic"))
        {
            return "magic".equalsIgnoreCase(primaryCategory) || (tags != null && tags.contains("magic"));
        }
        if (filter.contains("melee"))
        {
            return "melee".equalsIgnoreCase(primaryCategory) || (tags != null && tags.contains("melee"));
        }
        if (filter.contains("archery"))
        {
            return "archery".equalsIgnoreCase(primaryCategory) || (tags != null && tags.contains("archery"));
        }
        if (filter.contains("food"))
        {
            return "food".equalsIgnoreCase(primaryCategory) || (tags != null && tags.contains("food"));
        }
        if (filter.contains("herb"))
        {
            return "herblore".equalsIgnoreCase(primaryCategory) || (tags != null && tags.contains("herblore"));
        }
        if (filter.contains("craft"))
        {
            return "crafting".equalsIgnoreCase(primaryCategory) || "general".equalsIgnoreCase(primaryCategory)
                || (tags != null && (tags.contains("crafting") || tags.contains("general")));
        }
        if (filter.contains("general"))
        {
            return "general".equalsIgnoreCase(primaryCategory) || "crafting".equalsIgnoreCase(primaryCategory)
                || (tags != null && (tags.contains("general") || tags.contains("crafting")));
        }

        return false;
    }

    public void clearMarkers()
    {
        clientThread.invokeLater(this::clearMarkersInternal);
    }

    private synchronized void clearMarkersInternal()
    {
        for (WorldMapPoint p : activePoints)
        {
            worldMapPointManager.remove(p);
        }
        activePoints.clear();
        activeVendorNodes.clear();
        activeTownNodes.clear();
    }

    public synchronized List<VendorMapNode> getActiveVendorNodes()
    {
        return Collections.unmodifiableList(activeVendorNodes);
    }

    public synchronized List<TownMapNode> getActiveTownNodes()
    {
        return Collections.unmodifiableList(activeTownNodes);
    }
}
