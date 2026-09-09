package com.osrscopilot.util;

import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import org.junit.Assert;
import org.junit.Test;

/**
 * Covers the parts of {@link NpcPortraitManager} that don't require a live network/disk round-trip:
 * the disk cache file-naming scheme (filesystem safety + collision resistance between variants/names)
 * and the pure image-processing helpers used by both the disk-cache-hit and network-fetch paths.
 */
public class NpcPortraitManagerTest
{
    private NpcPortraitManager newManager()
    {
        return new NpcPortraitManager(new OkHttpClient(), Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }));
    }

    @Test
    public void diskCacheFileName_isFilesystemSafe()
    {
        String name = NpcPortraitManager.diskCacheFileName("Vet'ion (Reborn)", false);
        Assert.assertTrue("Cache file name should only contain safe characters",
            name.matches("[a-z0-9_-]+\\.png"));
        Assert.assertTrue(name.endsWith("_chathead.png"));
    }

    @Test
    public void diskCacheFileName_distinguishesChatheadFromFullRender()
    {
        String chathead = NpcPortraitManager.diskCacheFileName("zulrah", false);
        String full = NpcPortraitManager.diskCacheFileName("zulrah", true);

        Assert.assertNotEquals("Chathead and full-render variants must not share a cache file",
            chathead, full);
        Assert.assertTrue(chathead.endsWith("_chathead.png"));
        Assert.assertTrue(full.endsWith("_full.png"));
    }

    @Test
    public void diskCacheFileName_distinguishesDifferentNpcNames()
    {
        String a = NpcPortraitManager.diskCacheFileName("giant_rat", false);
        String b = NpcPortraitManager.diskCacheFileName("giant_spider", false);
        Assert.assertNotEquals(a, b);
    }

    @Test
    public void diskCacheFileName_isStableAndDeterministic()
    {
        String first = NpcPortraitManager.diskCacheFileName("Abyssal_demon", true);
        String second = NpcPortraitManager.diskCacheFileName("Abyssal_demon", true);
        Assert.assertEquals("Same input should always produce the same cache key", first, second);
    }

    @Test
    public void diskCacheFileName_handlesVeryLongNamesWithoutExploding()
    {
        String longName = "A_Really_Extremely_Very_Long_NPC_Name_That_Exceeds_Sixty_Characters_In_Total_Length";
        String name = NpcPortraitManager.diskCacheFileName(longName, false);
        // Overall file name should stay reasonable (60 char safe-prefix cap + short hash + suffix).
        Assert.assertTrue("Cache file name should be capped to a reasonable length", name.length() < 90);
    }

    @Test
    public void processCircularPortrait_producesExpectedDiameter()
    {
        NpcPortraitManager manager = newManager();
        BufferedImage src = new BufferedImage(120, 200, BufferedImage.TYPE_INT_ARGB);
        BufferedImage result = manager.processCircularPortrait(src, 42);

        Assert.assertEquals(42, result.getWidth());
        Assert.assertEquals(42, result.getHeight());
        Assert.assertEquals(BufferedImage.TYPE_INT_ARGB, result.getType());
    }

    @Test
    public void getPlaceholderSilhouette_isCachedByDiameter()
    {
        NpcPortraitManager manager = newManager();
        BufferedImage first = manager.getPlaceholderSilhouette(32);
        BufferedImage second = manager.getPlaceholderSilhouette(32);
        Assert.assertSame("Placeholder should be cached per diameter, not regenerated each call", first, second);

        BufferedImage differentSize = manager.getPlaceholderSilhouette(48);
        Assert.assertNotSame(first, differentSize);
        Assert.assertEquals(48, differentSize.getWidth());
    }
}
