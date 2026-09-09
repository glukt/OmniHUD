package com.osrscopilot.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Loads NPC portrait images (chatheads and full-body renders) from the OSRS Wiki, with a two-tier cache:
 * an in-memory {@link Cache} of already-processed circular portraits (fast path, per-session), and a
 * bounded on-disk cache of the raw wiki images (survives client restarts, avoids re-fetching from the
 * network every session).
 *
 * The disk cache lives under {@code CopilotPaths.DATA_DIR/portraits/} and is capped by
 * both file count and total bytes; when either cap is exceeded, the least-recently-accessed files are
 * evicted first. Chathead and full-body render variants are cached as separate files (they are visually
 * distinct assets), each keyed by a filesystem-safe name (sanitised NPC name + a short hash suffix)
 * derived from the NPC name.
 */
@Slf4j
@Singleton
public class NpcPortraitManager
{
    private static final String WIKI_FILEPATH_BASE = "https://oldschool.runescape.wiki/w/Special:FilePath/";
    private static final String USER_AGENT = "OmniHUD-RuneLite-plugin/1.0 (+https://github.com/glukt/OmniHUD)";

    // On-disk cache bounds. Portrait thumbnails are small (a few KB each at width=100), so these
    // caps are intentionally generous while still guaranteeing the cache can never grow unbounded on a
    // long-running client. Location comes from CopilotPaths so it stays in step with the other stores.
    private static final int MAX_CACHE_FILES = 400;
    private static final long MAX_CACHE_BYTES = 8L * 1024 * 1024; // 8 MB

    private final OkHttpClient okHttpClient;
    private final ScheduledExecutorService executorService;

    private final Cache<String, BufferedImage> portraitCache = CacheBuilder.newBuilder()
        .maximumSize(250)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build();

    private final Cache<Integer, BufferedImage> placeholderCache = CacheBuilder.newBuilder()
        .maximumSize(10)
        .build();

    // Negative cache: names whose every candidate URL 404'd. Without this, re-rendering a broad
    // bestiary search re-fires up to ~100 wiki fetches x 4 candidate URLs each on every rebuild.
    // Short-ish TTL so a transient wiki hiccup or a later-added image self-heals.
    private final Cache<String, Boolean> missCache = CacheBuilder.newBuilder()
        .maximumSize(2000)
        .expireAfterWrite(20, TimeUnit.MINUTES)
        .build();

    // When false, no monster art is fetched from (or served for) the OSRS Wiki - callers keep the
    // generated placeholder silhouette. Wired from OsrsCopilotConfig.showMonsterArt(); default true
    // so the manager behaves normally when no one has set it (tests, direct use).
    private volatile boolean wikiArtEnabled = true;

    @Inject
    public NpcPortraitManager(OkHttpClient okHttpClient, ScheduledExecutorService executorService)
    {
        this.okHttpClient = okHttpClient;
        this.executorService = executorService;
    }

    /** Enable/disable OSRS Wiki monster-art loading (see {@code OsrsCopilotConfig.showMonsterArt()}). */
    public void setWikiArtEnabled(boolean enabled)
    {
        this.wikiArtEnabled = enabled;
    }

    /**
     * Loads the standard small chathead portrait for an NPC (falls back to the full-body render or wiki image).
     */
    public void loadNpcPortrait(String npcName, int diameter, JLabel targetLabel)
    {
        loadPortrait(null, npcName, diameter, targetLabel, false);
    }

    /**
     * Loads the portrait for a specific Monster model, using its verified wiki image filename if available.
     */
    public void loadNpcPortrait(com.osrscopilot.data.model.Monster monster, int diameter, JLabel targetLabel)
    {
        if (monster != null)
        {
            loadPortrait(monster, monster.getName(), diameter, targetLabel, false);
        }
        else
        {
            loadPortrait(null, null, diameter, targetLabel, false);
        }
    }

    /**
     * Loads a "hero" portrait for an NPC, preferring the larger full-body wiki render over the small
     * chathead crop.
     */
    public void loadNpcHeroPortrait(String npcName, int diameter, JLabel targetLabel)
    {
        loadPortrait(null, npcName, diameter, targetLabel, true);
    }

    /**
     * Loads a "hero" portrait for a specific Monster model.
     */
    public void loadNpcHeroPortrait(com.osrscopilot.data.model.Monster monster, int diameter, JLabel targetLabel)
    {
        if (monster != null)
        {
            loadPortrait(monster, monster.getName(), diameter, targetLabel, true);
        }
        else
        {
            loadPortrait(null, null, diameter, targetLabel, true);
        }
    }

    private void loadPortrait(com.osrscopilot.data.model.Monster monster, String npcName, int diameter, JLabel targetLabel, boolean preferFullRender)
    {
        targetLabel.setIcon(new ImageIcon(getPlaceholderSilhouette(diameter)));

        if (!wikiArtEnabled)
        {
            return; // wiki art disabled - keep the placeholder silhouette
        }

        String effectiveName = npcName;
        if ((effectiveName == null || effectiveName.trim().isEmpty()) && monster != null)
        {
            effectiveName = monster.getName();
        }

        if (effectiveName == null || effectiveName.trim().isEmpty() || "Unknown".equalsIgnoreCase(effectiveName.trim()))
        {
            return;
        }

        String cacheKey = effectiveName.toLowerCase(Locale.ROOT) + "_" + diameter + (preferFullRender ? "_hero" : "_chat");
        BufferedImage cached = portraitCache.getIfPresent(cacheKey);
        if (cached != null)
        {
            targetLabel.setIcon(new ImageIcon(cached));
            return;
        }
        if (missCache.getIfPresent(cacheKey) != null)
        {
            // Known-missing this session - keep the placeholder, don't re-hit the wiki.
            return;
        }

        final String finalName = effectiveName;
        final String finalKey = cacheKey;
        executorService.submit(() -> fetchCandidates(monster, finalName, diameter, preferFullRender, portrait -> {
            portraitCache.put(finalKey, portrait);
            SwingUtilities.invokeLater(() -> {
                targetLabel.setIcon(new ImageIcon(portrait));
                targetLabel.revalidate();
                targetLabel.repaint();
            });
        }, () -> missCache.put(finalKey, Boolean.TRUE)));
    }

    private void fetchCandidates(com.osrscopilot.data.model.Monster monster, String npcName, int diameter, boolean preferFullRender, Consumer<BufferedImage> onLoaded, Runnable onExhausted)
    {
        String normalizedName = normalizeNpcName(npcName);
        File cacheFile = diskCacheFile(normalizedName, preferFullRender);

        BufferedImage fromDisk = readFromDiskCache(cacheFile);
        if (fromDisk != null)
        {
            onLoaded.accept(processCircularPortrait(fromDisk, diameter));
            return;
        }

        List<String> candidateFiles = new ArrayList<>();
        if (monster != null && monster.getWikiImage() != null && !monster.getWikiImage().trim().isEmpty())
        {
            candidateFiles.add(monster.getWikiImage().trim());
        }

        if (preferFullRender)
        {
            candidateFiles.add(normalizedName + ".png");
            candidateFiles.add(normalizedName + "_(1).png");
            candidateFiles.add(normalizedName + "_chathead.png");
            candidateFiles.add(normalizedName + "_icon.png");
        }
        else
        {
            candidateFiles.add(normalizedName + "_chathead.png");
            candidateFiles.add(normalizedName + ".png");
            candidateFiles.add(normalizedName + "_(1).png");
            candidateFiles.add(normalizedName + "_icon.png");
        }

        tryFetchCandidate(candidateFiles, 0, cacheFile, diameter, onLoaded, onExhausted);
    }

    private void tryFetchCandidate(List<String> candidateFiles, int index, File cacheFile, int diameter, Consumer<BufferedImage> onLoaded, Runnable onExhausted)
    {
        if (index >= candidateFiles.size())
        {
            // All candidates 404'd; remember that so a re-render doesn't re-fetch them all.
            if (onExhausted != null)
            {
                onExhausted.run();
            }
            return;
        }

        String fileName = candidateFiles.get(index);
        String url = WIKI_FILEPATH_BASE + urlEncode(fileName) + "?width=100";

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build();

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, java.io.IOException e)
            {
                tryFetchCandidate(candidateFiles, index + 1, cacheFile, diameter, onLoaded, onExhausted);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                if (!response.isSuccessful() || response.body() == null)
                {
                    response.close();
                    tryFetchCandidate(candidateFiles, index + 1, cacheFile, diameter, onLoaded, onExhausted);
                    return;
                }

                byte[] rawBytes;
                try
                {
                    rawBytes = response.body().bytes();
                }
                catch (Exception ex)
                {
                    tryFetchCandidate(candidateFiles, index + 1, cacheFile, diameter, onLoaded, onExhausted);
                    return;
                }
                finally
                {
                    response.close();
                }

                BufferedImage rawImg;
                try
                {
                    rawImg = ImageIO.read(new ByteArrayInputStream(rawBytes));
                }
                catch (Exception ex)
                {
                    rawImg = null;
                }

                if (rawImg == null)
                {
                    tryFetchCandidate(candidateFiles, index + 1, cacheFile, diameter, onLoaded, onExhausted);
                    return;
                }

                writeToDiskCache(cacheFile, rawBytes);
                onLoaded.accept(processCircularPortrait(rawImg, diameter));
            }
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Disk cache
    // ---------------------------------------------------------------------------------------------

    private File getDiskCacheDir()
    {
        try
        {
            File dir = CopilotPaths.dataSubDir("portraits");
            if (!dir.exists() && !dir.mkdirs() && !dir.exists())
            {
                return null;
            }
            return dir;
        }
        catch (Exception e)
        {
            log.debug("Unable to resolve/create portrait disk cache directory", e);
            return null;
        }
    }

    private File diskCacheFile(String normalizedName, boolean fullRenderVariant)
    {
        File dir = getDiskCacheDir();
        if (dir == null)
        {
            return null;
        }
        return new File(dir, diskCacheFileName(normalizedName, fullRenderVariant));
    }

    /**
     * Builds a filesystem-safe cache file name for an NPC name + variant: the sanitised name
     * (truncated to 60 chars) plus a hex {@code String.hashCode} suffix that disambiguates names
     * which sanitise or truncate to the same prefix. Not cryptographically collision-proof, but a
     * collision across the few thousand distinct NPC names is vanishingly unlikely.
     * Package-private (rather than private) purely so it is directly unit-testable.
     */
    static String diskCacheFileName(String normalizedName, boolean fullRenderVariant)
    {
        String lower = normalizedName == null ? "unknown" : normalizedName.toLowerCase(Locale.ROOT);
        String safe = lower.replaceAll("[^a-z0-9_-]", "_");
        if (safe.length() > 60)
        {
            safe = safe.substring(0, 60);
        }
        String hash = Integer.toHexString(lower.hashCode());
        return safe + "_" + hash + (fullRenderVariant ? "_full" : "_chathead") + ".png";
    }

    private BufferedImage readFromDiskCache(File file)
    {
        if (file == null || !file.isFile())
        {
            return null;
        }
        try
        {
            BufferedImage img = ImageIO.read(file);
            if (img != null)
            {
                // Touch the file so eviction treats it as recently used (LRU-by-mtime).
                file.setLastModified(System.currentTimeMillis());
            }
            return img;
        }
        catch (Exception e)
        {
            log.debug("Failed reading portrait disk cache file {}", file, e);
            return null;
        }
    }

    private void writeToDiskCache(File file, byte[] rawBytes)
    {
        if (file == null || rawBytes == null || rawBytes.length == 0)
        {
            return;
        }
        try
        {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists())
            {
                dir.mkdirs();
            }
            Files.write(file.toPath(), rawBytes);
            enforceCacheBounds(dir);
        }
        catch (Exception e)
        {
            log.debug("Failed writing portrait disk cache file {}", file, e);
        }
    }

    /**
     * Bounds the disk cache by both file count and total bytes, evicting least-recently-accessed files
     * (oldest {@code lastModified}) first until back under both caps. Only runs on a cache write (never on
     * the render/EDT path), and only scans the small, plugin-private portraits directory - cheap even
     * though it is a full directory listing.
     */
    private void enforceCacheBounds(File dir)
    {
        if (dir == null)
        {
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".png"));
        if (files == null || files.length == 0)
        {
            return;
        }

        long totalBytes = 0;
        for (File f : files)
        {
            totalBytes += f.length();
        }

        if (files.length <= MAX_CACHE_FILES && totalBytes <= MAX_CACHE_BYTES)
        {
            return;
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        int idx = 0;
        int remaining = files.length;
        while (idx < files.length && (remaining > MAX_CACHE_FILES || totalBytes > MAX_CACHE_BYTES))
        {
            File victim = files[idx];
            long size = victim.length();
            if (victim.delete())
            {
                totalBytes -= size;
                remaining--;
            }
            idx++;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Image processing
    // ---------------------------------------------------------------------------------------------

    public BufferedImage processCircularPortrait(BufferedImage src, int diameter)
    {
        BufferedImage output = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = output.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Dark circular backing
        g2.setColor(new Color(25, 25, 25, 240));
        g2.fillOval(1, 1, diameter - 2, diameter - 2);

        // Clip circle with slight inset
        Ellipse2D.Double clipCircle = new Ellipse2D.Double(2, 2, diameter - 4, diameter - 4);
        g2.setClip(clipCircle);

        // Zoom out just a bit (scale factor 0.86 with padding) so the full head/shoulders show
        double srcWidth = src.getWidth();
        double srcHeight = src.getHeight();
        double targetSize = (diameter - 4) * 0.86;
        double scale = Math.min(targetSize / srcWidth, targetSize / srcHeight);
        int drawW = (int) (srcWidth * scale);
        int drawH = (int) (srcHeight * scale);
        int drawX = 2 + ((diameter - 4) - drawW) / 2;
        int drawY = 2 + ((diameter - 4) - drawH) / 2;

        g2.drawImage(src, drawX, drawY, drawW, drawH, null);

        g2.setClip(null);
        g2.setColor(new Color(255, 185, 45, 220));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(1, 1, diameter - 2, diameter - 2);

        g2.dispose();
        return output;
    }

    public BufferedImage getPlaceholderSilhouette(int diameter)
    {
        BufferedImage cached = placeholderCache.getIfPresent(diameter);
        if (cached != null)
        {
            return cached;
        }

        BufferedImage img = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
        g2.fillOval(1, 1, diameter - 2, diameter - 2);

        g2.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(1, 1, diameter - 2, diameter - 2);

        g2.setColor(new Color(130, 130, 130, 190));
        int headSize = (int) (diameter * 0.32);
        int headX = (diameter - headSize) / 2;
        int headY = (int) (diameter * 0.22);
        g2.fillOval(headX, headY, headSize, headSize);

        int bodyW = (int) (diameter * 0.60);
        int bodyH = (int) (diameter * 0.38);
        int bodyX = (diameter - bodyW) / 2;
        int bodyY = (int) (diameter * 0.62);

        Ellipse2D.Double bodyClip = new Ellipse2D.Double(1, 1, diameter - 2, diameter - 2);
        g2.setClip(bodyClip);
        g2.fillOval(bodyX, bodyY, bodyW, bodyH);

        g2.dispose();
        placeholderCache.put(diameter, img);
        return img;
    }

    private static String normalizeNpcName(String raw)
    {
        if (raw == null) return "Unknown";
        String cleaned = raw.replaceAll("\\s*\\([^)]*\\)", "").trim();
        return cleaned.replace(' ', '_');
    }

    private static String urlEncode(String value)
    {
        try
        {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                .replace("+", "%20");
        }
        catch (Exception e)
        {
            return value;
        }
    }
}
