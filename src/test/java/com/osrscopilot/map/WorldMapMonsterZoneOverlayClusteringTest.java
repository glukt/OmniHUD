package com.osrscopilot.map;

import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterSpawnZone;
import com.osrscopilot.map.WorldMapMonsterZoneOverlay.MonsterZoneEntry;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Assert;
import org.junit.Test;

/**
 * Pure-function tests for {@link WorldMapMonsterZoneOverlay#clusterEntries} - the low-zoom spawn zone
 * grouping used to reduce world map clutter. These exercise the grouping logic directly with synthetic
 * zone coordinates, independent of any live RuneLite client/rendering state (no mocking of Client/Widget
 * required, since the method only depends on plain zone/world-point data).
 */
public class WorldMapMonsterZoneOverlayClusteringTest
{
    private static MonsterZoneEntry entryAt(Monster monster, int x, int y, int plane)
    {
        MonsterSpawnZone zone = MonsterSpawnZone.builder()
            .zoneName("Zone " + x + "," + y)
            .centerPoint(new WorldPoint(x, y, plane))
            .build();
        return MonsterZoneEntry.builder().monster(monster).zone(zone).build();
    }

    private static Monster testMonster(String name)
    {
        return Monster.builder().id(1).name(name).build();
    }

    @Test
    public void tightlyPackedZones_atLowZoom_groupIntoOneCluster()
    {
        Monster m = testMonster("Cow");
        List<MonsterZoneEntry> entries = new ArrayList<>();
        // Five zones within a handful of tiles of each other.
        entries.add(entryAt(m, 3200, 3200, 0));
        entries.add(entryAt(m, 3202, 3201, 0));
        entries.add(entryAt(m, 3198, 3203, 0));
        entries.add(entryAt(m, 3201, 3198, 0));
        entries.add(entryAt(m, 3199, 3199, 0));

        // Zoomed out: 1 pixel-per-tile, 48px radius -> 48 tile radius. All zones are within a few tiles.
        List<List<MonsterZoneEntry>> clusters = WorldMapMonsterZoneOverlay.clusterEntries(entries, 1.0f, 48f);

        Assert.assertEquals("All 5 nearby zones should collapse into a single cluster", 1, clusters.size());
        Assert.assertEquals(5, clusters.get(0).size());
    }

    @Test
    public void widelySeparatedZones_neverCluster()
    {
        Monster m = testMonster("Goblin");
        List<MonsterZoneEntry> entries = new ArrayList<>();
        entries.add(entryAt(m, 3200, 3200, 0));
        entries.add(entryAt(m, 3400, 3200, 0)); // 200 tiles away
        entries.add(entryAt(m, 2800, 3600, 0)); // far away

        List<List<MonsterZoneEntry>> clusters = WorldMapMonsterZoneOverlay.clusterEntries(entries, 1.0f, 48f);

        Assert.assertEquals("Far-apart zones should each remain their own cluster", 3, clusters.size());
        for (List<MonsterZoneEntry> cluster : clusters)
        {
            Assert.assertEquals(1, cluster.size());
        }
    }

    @Test
    public void higherZoom_shrinksEffectiveClusterRadius()
    {
        Monster m = testMonster("Zombie");
        List<MonsterZoneEntry> entries = new ArrayList<>();
        entries.add(entryAt(m, 3200, 3200, 0));
        entries.add(entryAt(m, 3230, 3200, 0)); // 30 tiles away

        // At zoom 1.0 (48 tile radius), these should cluster together.
        List<List<MonsterZoneEntry>> zoomedOut = WorldMapMonsterZoneOverlay.clusterEntries(entries, 1.0f, 48f);
        Assert.assertEquals(1, zoomedOut.size());

        // At zoom 4.0 (48/4 = 12 tile radius), 30 tiles apart is now too far to cluster.
        List<List<MonsterZoneEntry>> zoomedIn = WorldMapMonsterZoneOverlay.clusterEntries(entries, 4.0f, 48f);
        Assert.assertEquals("Zooming in should shrink the effective tile radius and split the cluster", 2, zoomedIn.size());
    }

    @Test
    public void differentPlanes_neverClusterTogether()
    {
        Monster m = testMonster("Skeleton");
        List<MonsterZoneEntry> entries = new ArrayList<>();
        entries.add(entryAt(m, 3200, 3200, 0));
        entries.add(entryAt(m, 3200, 3200, 1)); // same X/Y, different plane

        List<List<MonsterZoneEntry>> clusters = WorldMapMonsterZoneOverlay.clusterEntries(entries, 1.0f, 48f);

        Assert.assertEquals(2, clusters.size());
    }

    @Test
    public void emptyOrNullInput_returnsNoClusters()
    {
        Assert.assertTrue(WorldMapMonsterZoneOverlay.clusterEntries(null, 1.0f, 48f).isEmpty());
        Assert.assertTrue(WorldMapMonsterZoneOverlay.clusterEntries(new ArrayList<>(), 1.0f, 48f).isEmpty());
    }

    @Test
    public void twoDistinctClusters_formSeparately()
    {
        Monster m = testMonster("Rat");
        List<MonsterZoneEntry> entries = new ArrayList<>();
        // Cluster A
        entries.add(entryAt(m, 3200, 3200, 0));
        entries.add(entryAt(m, 3203, 3202, 0));
        // Cluster B, far away
        entries.add(entryAt(m, 3500, 3500, 0));
        entries.add(entryAt(m, 3502, 3501, 0));

        List<List<MonsterZoneEntry>> clusters = WorldMapMonsterZoneOverlay.clusterEntries(entries, 1.0f, 48f);

        Assert.assertEquals(2, clusters.size());
        for (List<MonsterZoneEntry> cluster : clusters)
        {
            Assert.assertEquals(2, cluster.size());
        }
    }

    @Test
    public void zeroOrNegativeZoom_fallsBackSafelyInsteadOfDividingByZero()
    {
        Monster m = testMonster("Bat");
        List<MonsterZoneEntry> entries = new ArrayList<>();
        entries.add(entryAt(m, 3200, 3200, 0));
        entries.add(entryAt(m, 3201, 3200, 0));

        // Should not throw (division-by-zero / NaN radius) and should still produce a sane grouping.
        List<List<MonsterZoneEntry>> clusters = WorldMapMonsterZoneOverlay.clusterEntries(entries, 0f, 48f);
        Assert.assertFalse(clusters.isEmpty());
    }
}
