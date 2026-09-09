package com.osrscopilot.util;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Singleton;

/**
 * Category-themed map-marker graphics generator and cache for the OSRS World Map.
 * Provides:
 * 1. Sleek 12x12 glossy colored orbs for map shop categories (Magic, Melee, Archery, Food, Herblore, General).
 * 2. Standout 28x28 Radiant Gold Heraldic Hub Emblem for town centers.
 * 3. Authentic 20x20 pixel-perfect OSRS Mini Globe icon for RuneLite sidebar and directory headers.
 */
@Singleton
public class IconCache
{
    public static final int ORB_SIZE = 12;
    public static final int TOWN_ORB_SIZE = 28;
    public static final int GLOBE_SIZE = 20;

    /**
     * Global opacity applied to every generated category orb so this plugin's default marker reads
     * as slightly softer / more "hover for info" than a fully opaque pin competing with the game's
     * own HUD and native map icons - a deliberately small, cheap tweak (single AlphaComposite wrap
     * per icon, applied once at generation time, not per-frame) rather than a redesign.
     */
    private static final float MARKER_OPACITY = 0.92f;

    private final Map<String, BufferedImage> categoryIcons = new HashMap<>();
    private final BufferedImage townIcon;
    private final BufferedImage pluginIcon;
    private final BufferedImage minimapToggleOnIcon;
    private final BufferedImage minimapToggleOffIcon;
    private final BufferedImage minimapMonsterToggleOnIcon;
    private final BufferedImage minimapMonsterToggleOffIcon;

    /**
     * Fully transparent ORB_SIZE x ORB_SIZE placeholder icon. Used in place of a colored category
     * orb for shops known to sit exactly on a native OSRS world map icon (see NativeIconDetector) -
     * the WorldMapPoint / click-and-hover hit-zone stays registered at the same location, it simply
     * renders nothing of its own so the halo highlight (WorldMapShopTooltipOverlay) reads as the one
     * and only visual treatment at that spot instead of a competing pin.
     */
    private final BufferedImage invisibleIcon;

    public IconCache()
    {
        // 1. Generate sleek 12x12 glossy category orbs
        BufferedImage magicOrb = createMagicOrb();
        BufferedImage meleeOrb = createMeleeOrb();
        BufferedImage archeryOrb = createArcheryOrb();
        BufferedImage foodOrb = createFoodOrb();
        BufferedImage herbloreOrb = createHerbloreOrb();
        BufferedImage generalOrb = createGeneralOrb();

        // 2. Generate standout 28x28 radiant gold heraldic town hub emblem
        BufferedImage townOrb = createTownOrb();
        this.townIcon = townOrb;

        // 3. Generate authentic 20x20 OSRS mini globe icon
        this.pluginIcon = createOsrsGlobeIcon();

        // 3b. Fully transparent placeholder for native-icon-highlighted shops (see field javadoc)
        this.invisibleIcon = new BufferedImage(ORB_SIZE, ORB_SIZE, BufferedImage.TYPE_INT_ARGB);

        // 4. Generate sleek 18x18 minimap edge toggle button icons (ON & OFF)
        this.minimapToggleOnIcon = createMinimapToggleOnIcon();
        this.minimapToggleOffIcon = createMinimapToggleOffIcon();
        this.minimapMonsterToggleOnIcon = createMinimapMonsterToggleOnIcon();
        this.minimapMonsterToggleOffIcon = createMinimapMonsterToggleOffIcon();

        // 5. Register the category orbs by keyword bucket
        categoryIcons.put("magic", magicOrb);
        categoryIcons.put("melee", meleeOrb);
        categoryIcons.put("archery", archeryOrb);
        categoryIcons.put("food", foodOrb);
        categoryIcons.put("herblore", herbloreOrb);
        categoryIcons.put("general", generalOrb);
        categoryIcons.put("town", townOrb);
    }

    /**
     * Resolves a shop-marker icon from free-text category keywords, falling back to the general orb.
     */
    public BufferedImage getIconForCategory(String category)
    {
        String key = getNormalizedCategory(category);
        return categoryIcons.getOrDefault(key, categoryIcons.get("general"));
    }

    /**
     * Returns the town-hub marker icon.
     */
    public BufferedImage getTownIcon()
    {
        return townIcon;
    }

    /**
     * Returns the authentic OSRS World Map Globe icon for RuneLite sidebar navigation.
     */
    public BufferedImage getPluginIcon()
    {
        return pluginIcon;
    }

    /**
     * Returns the authentic OSRS World Map Globe icon for directory header navigation.
     */
    public BufferedImage getShopIcon()
    {
        return pluginIcon;
    }

    /**
     * Returns the fully transparent placeholder icon used for shops whose marker is being replaced
     * by a native-icon halo highlight instead of this plugin's own pin.
     */
    public BufferedImage getInvisibleIcon()
    {
        return invisibleIcon;
    }

    public BufferedImage getMinimapToggleOnIcon()
    {
        return minimapToggleOnIcon;
    }

    public BufferedImage getMinimapToggleOffIcon()
    {
        return minimapToggleOffIcon;
    }

    public BufferedImage getMinimapMonsterToggleOnIcon()
    {
        return minimapMonsterToggleOnIcon;
    }

    public BufferedImage getMinimapMonsterToggleOffIcon()
    {
        return minimapMonsterToggleOffIcon;
    }

    public BufferedImage getMagicIcon()
    {
        return getIconForCategory("magic");
    }

    public BufferedImage getMeleeIcon()
    {
        return getIconForCategory("melee");
    }

    public BufferedImage getArcheryIcon()
    {
        return getIconForCategory("archery");
    }

    public BufferedImage getFoodIcon()
    {
        return getIconForCategory("food");
    }

    public BufferedImage getHerbloreIcon()
    {
        return getIconForCategory("herblore");
    }

    public BufferedImage getGeneralIcon()
    {
        return getIconForCategory("general");
    }

    private static String getNormalizedCategory(String category)
    {
        if (category == null)
        {
            return "general";
        }

        String cat = category.toLowerCase(Locale.ROOT).trim();
        if (cat.contains("magic") || cat.contains("rune") || cat.contains("teleport") || cat.contains("staff") || cat.contains("wizard"))
        {
            return "magic";
        }
        if (cat.contains("melee") || cat.contains("weapon") || cat.contains("armour") || cat.contains("armor") || cat.contains("sword") || cat.contains("shield") || cat.contains("smithing") || cat.contains("axe") || cat.contains("mace") || cat.contains("scimitar"))
        {
            return "melee";
        }
        if (cat.contains("archery") || cat.contains("range") || cat.contains("ranged") || cat.contains("bow") || cat.contains("arrow") || cat.contains("fletch"))
        {
            return "archery";
        }
        if (cat.contains("food") || cat.contains("cook") || cat.contains("bread") || cat.contains("fish") || cat.contains("bar") || cat.contains("pub") || cat.contains("inn") || cat.contains("bake"))
        {
            return "food";
        }
        if (cat.contains("herb") || cat.contains("herblore") || cat.contains("potion") || cat.contains("apothecary") || cat.contains("farming") || cat.contains("farm") || cat.contains("seed"))
        {
            return "herblore";
        }
        if (cat.contains("town") || cat.contains("city") || cat.contains("settlement") || cat.contains("village") || cat.contains("hub"))
        {
            return "town";
        }

        return "general";
    }

    private static Graphics2D initGraphics(BufferedImage img)
    {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        return g;
    }

    private static Color blend(Color c1, Color c2, float ratio)
    {
        float ir = 1.0f - ratio;
        int r = Math.min(255, Math.max(0, Math.round(c1.getRed() * ir + c2.getRed() * ratio)));
        int g = Math.min(255, Math.max(0, Math.round(c1.getGreen() * ir + c2.getGreen() * ratio)));
        int b = Math.min(255, Math.max(0, Math.round(c1.getBlue() * ir + c2.getBlue() * ratio)));
        int a = Math.min(255, Math.max(0, Math.round(c1.getAlpha() * ir + c2.getAlpha() * ratio)));
        return new Color(r, g, b, a);
    }

    // =========================================================================
    // 12x12 AUTHENTIC OSRS GOLD COIN / MERCHANT TOKEN MARKERS
    // =========================================================================

    private static BufferedImage createOrb(Color primaryColor, Color highlightColor, Color shadowColor)
    {
        final int size = ORB_SIZE;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = initGraphics(img);

        // Slightly soften overall marker opacity so markers blend cleanly with the map canvas
        Composite baseComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, MARKER_OPACITY));

        // 1. Dark outer drop shadow / rim for maximum contrast against any terrain (11x11 circle)
        g.setColor(new Color(15, 12, 6, 230));
        g.fill(new Ellipse2D.Float(0.5f, 0.5f, 11.0f, 11.0f));

        // 2. Outer Beveled Gold Coin Rim (10x10)
        GradientPaint coinRimPaint = new GradientPaint(
            1.5f, 1.5f, new Color(254, 240, 138),
            10.5f, 10.5f, new Color(146, 64, 14)
        );
        g.setPaint(coinRimPaint);
        g.fill(new Ellipse2D.Float(1.0f, 1.0f, 10.0f, 10.0f));

        // 3. Inner Metallic Coin Face (8x8)
        float[] fractions = {0.0f, 0.55f, 1.0f};
        Color[] colors = {
            new Color(254, 240, 138), // Pale Gold
            new Color(234, 179, 8),   // Warm Gold
            new Color(120, 53, 15)    // Deep Bronze Gold
        };
        RadialGradientPaint coinFace = new RadialGradientPaint(
            new Point2D.Float(4.5f, 4.0f),
            5.0f,
            fractions,
            colors
        );
        g.setPaint(coinFace);
        g.fill(new Ellipse2D.Float(2.0f, 2.0f, 8.0f, 8.0f));

        // 4. Center Category Gem / Coin Pip (4x4)
        g.setColor(new Color(20, 15, 8, 200));
        g.fill(new Ellipse2D.Float(4.0f, 4.0f, 4.0f, 4.0f));

        GradientPaint gemPaint = new GradientPaint(
            4.0f, 4.0f, highlightColor,
            8.0f, 8.0f, primaryColor
        );
        g.setPaint(gemPaint);
        g.fill(new Ellipse2D.Float(4.5f, 4.5f, 3.0f, 3.0f));

        // Specular glint on gem core
        g.setColor(new Color(255, 255, 255, 220));
        g.fill(new Ellipse2D.Float(4.8f, 4.8f, 1.2f, 1.2f));

        // 5. Top-Left Metallic Specular Glint Arc
        g.setColor(new Color(255, 255, 255, 200));
        g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Float(1.8f, 1.8f, 8.4f, 8.4f, 105, 80, Arc2D.OPEN));

        // 6. Crisp Dark Perimeter Definition
        g.setColor(new Color(18, 12, 5, 240));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Ellipse2D.Float(0.95f, 0.95f, 10.1f, 10.1f));

        g.setComposite(baseComposite);
        g.dispose();
        return img;
    }

    /**
     * Magic: Sapphire Blue Orb (#0284C7 / #38BDF8)
     */
    private BufferedImage createMagicOrb()
    {
        return createOrb(
            Color.decode("#0284C7"),
            Color.decode("#38BDF8"),
            new Color(8, 47, 73)
        );
    }

    /**
     * Melee: Crimson Red Orb (#DC2626 / #F87171)
     */
    private BufferedImage createMeleeOrb()
    {
        return createOrb(
            Color.decode("#DC2626"),
            Color.decode("#F87171"),
            new Color(69, 10, 10)
        );
    }

    /**
     * Archery: Emerald Green Orb (#16A34A / #4ADE80)
     */
    private BufferedImage createArcheryOrb()
    {
        return createOrb(
            Color.decode("#16A34A"),
            Color.decode("#4ADE80"),
            new Color(5, 46, 22)
        );
    }

    /**
     * Food: Amber Gold Orb (#D97706 / #FBBF24)
     */
    private BufferedImage createFoodOrb()
    {
        return createOrb(
            Color.decode("#D97706"),
            Color.decode("#FBBF24"),
            new Color(69, 26, 3)
        );
    }

    /**
     * Herblore: Herbal Purple Orb (#9333EA / #C084FC)
     */
    private BufferedImage createHerbloreOrb()
    {
        return createOrb(
            Color.decode("#9333EA"),
            Color.decode("#C084FC"),
            new Color(59, 7, 100)
        );
    }

    /**
     * General: Warm Bronze/Gold Orb (#CA8A04 / #FDE047)
     */
    private BufferedImage createGeneralOrb()
    {
        return createOrb(
            Color.decode("#CA8A04"),
            Color.decode("#FDE047"),
            new Color(66, 32, 6)
        );
    }

    // =========================================================================
    // 28x28 STANDOUT RADIANT GOLD HERALDIC HUB EMBLEM (Towns)
    // =========================================================================

    private static BufferedImage createTownOrb()
    {
        final int size = TOWN_ORB_SIZE;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = initGraphics(img);

        // 1. Distinct Outer Dark Drop Shadow (26x26) & Ambient Gold Aura
        g.setColor(new Color(15, 10, 5, 230));
        g.fill(new Ellipse2D.Float(1.0f, 1.0f, 26.0f, 26.0f));

        g.setColor(new Color(245, 158, 11, 80));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Ellipse2D.Float(1.5f, 1.5f, 25.0f, 25.0f));

        // 2. Heraldic 8-Pointed Starburst & Compass Rose Backing Points
        // 4 Cardinal Compass Points (N, S, W, E extending to radius ~13)
        Path2D.Float compassRose = new Path2D.Float();
        // North
        compassRose.moveTo(12.0f, 6.0f);
        compassRose.lineTo(14.0f, 0.8f);
        compassRose.lineTo(16.0f, 6.0f);
        // East
        compassRose.lineTo(22.0f, 12.0f);
        compassRose.lineTo(27.2f, 14.0f);
        compassRose.lineTo(22.0f, 16.0f);
        // South
        compassRose.lineTo(16.0f, 22.0f);
        compassRose.lineTo(14.0f, 27.2f);
        compassRose.lineTo(12.0f, 22.0f);
        // West
        compassRose.lineTo(6.0f, 16.0f);
        compassRose.lineTo(0.8f, 14.0f);
        compassRose.lineTo(6.0f, 12.0f);
        compassRose.closePath();

        GradientPaint compassPaint = new GradientPaint(
            2.0f, 2.0f, new Color(254, 240, 138),
            26.0f, 26.0f, new Color(180, 83, 9)
        );
        g.setPaint(compassPaint);
        g.fill(compassRose);

        g.setColor(new Color(20, 14, 5, 220));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(compassRose);

        // 4 Diagonal Micro Points (NE, NW, SE, SW)
        Path2D.Float ordinalPoints = new Path2D.Float();
        // NE
        ordinalPoints.moveTo(17.5f, 10.5f);
        ordinalPoints.lineTo(23.5f, 4.5f);
        ordinalPoints.lineTo(19.5f, 6.5f);
        // SE
        ordinalPoints.moveTo(19.5f, 21.5f);
        ordinalPoints.lineTo(23.5f, 23.5f);
        ordinalPoints.lineTo(17.5f, 17.5f);
        // SW
        ordinalPoints.moveTo(10.5f, 17.5f);
        ordinalPoints.lineTo(4.5f, 23.5f);
        ordinalPoints.lineTo(8.5f, 21.5f);
        // NW
        ordinalPoints.moveTo(8.5f, 6.5f);
        ordinalPoints.lineTo(4.5f, 4.5f);
        ordinalPoints.lineTo(10.5f, 10.5f);

        g.setColor(new Color(251, 191, 36, 230));
        g.setStroke(new BasicStroke(1.0f));
        g.draw(ordinalPoints);

        // 3. Thick Metallic Gold Outer Ring / Bezel (22x22)
        GradientPaint goldRingPaint = new GradientPaint(
            3.0f, 3.0f, new Color(254, 240, 138),
            25.0f, 25.0f, new Color(146, 64, 14)
        );
        g.setPaint(goldRingPaint);
        g.fill(new Ellipse2D.Float(3.0f, 3.0f, 22.0f, 22.0f));

        // Outer ring glint arc (top-left)
        g.setColor(new Color(255, 255, 255, 220));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Float(3.8f, 3.8f, 20.4f, 20.4f, 105, 80, Arc2D.OPEN));

        // Outer ring shadow arc (bottom-right)
        g.setColor(new Color(69, 26, 3, 200));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Float(3.8f, 3.8f, 20.4f, 20.4f, 285, 80, Arc2D.OPEN));

        // 4. Dark Recessed Inner Bezel Rim (16x16)
        g.setColor(new Color(24, 15, 6, 250));
        g.fill(new Ellipse2D.Float(6.0f, 6.0f, 16.0f, 16.0f));

        // 5. Radiant Royal Golden Core Orb (14x14)
        float[] fractions = {0.0f, 0.30f, 0.65f, 1.0f};
        Color[] colors = {
            new Color(255, 255, 255),
            new Color(254, 240, 138),
            new Color(234, 179, 8),
            new Color(120, 53, 15)
        };
        RadialGradientPaint coreRgp = new RadialGradientPaint(
            new Point2D.Float(11.5f, 10.5f),
            9.5f,
            fractions,
            colors
        );
        g.setPaint(coreRgp);
        g.fill(new Ellipse2D.Float(7.0f, 7.0f, 14.0f, 14.0f));

        // 6. Radiant Center Royal Starburst & Compass Points
        Path2D.Float centerStar = new Path2D.Float();
        centerStar.moveTo(14.0f, 8.5f);  // N
        centerStar.lineTo(15.2f, 12.8f);
        centerStar.lineTo(19.5f, 14.0f); // E
        centerStar.lineTo(15.2f, 15.2f);
        centerStar.lineTo(14.0f, 19.5f); // S
        centerStar.lineTo(12.8f, 15.2f);
        centerStar.lineTo(8.5f, 14.0f);  // W
        centerStar.lineTo(12.8f, 12.8f);
        centerStar.closePath();

        // Fill Star with Brilliant White-Gold Glow
        GradientPaint starPaint = new GradientPaint(
            10.0f, 10.0f, new Color(255, 255, 255, 245),
            18.0f, 18.0f, new Color(253, 224, 71, 230)
        );
        g.setPaint(starPaint);
        g.fill(centerStar);

        // Micro Star facets shading & outline
        g.setColor(new Color(255, 255, 255, 220));
        g.setStroke(new BasicStroke(0.6f));
        g.draw(new Line2D.Float(14.0f, 8.5f, 14.0f, 19.5f));
        g.draw(new Line2D.Float(8.5f, 14.0f, 19.5f, 14.0f));

        // Starburst Sparkle Core Spot
        g.setColor(new Color(255, 255, 255, 255));
        g.fill(new Ellipse2D.Float(12.8f, 12.8f, 2.4f, 2.4f));

        // Specular Glint on Top-Left Core
        g.setColor(new Color(255, 255, 255, 190));
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Float(7.8f, 7.8f, 12.4f, 12.4f, 110, 70, Arc2D.OPEN));

        // 7. Perimeter Edge Definition
        g.setColor(new Color(20, 14, 5, 245));
        g.setStroke(new BasicStroke(1.0f));
        g.draw(new Ellipse2D.Float(2.95f, 2.95f, 22.1f, 22.1f));

        g.dispose();
        return img;
    }

    // =========================================================================
    // 20x20 AUTHENTIC OSRS MINI GLOBE ICON (Plugin / Nav Header)
    // =========================================================================

    private static BufferedImage createOsrsGlobeIcon()
    {
        final int size = GLOBE_SIZE;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = initGraphics(img);

        // 1. Outer Dark Shadow / Rim (19x19)
        g.setColor(new Color(15, 10, 5, 240));
        g.fill(new Ellipse2D.Float(0.5f, 0.5f, 19.0f, 19.0f));

        // 2. Authentic OSRS Minimap Bronze Bezel (18x18)
        GradientPaint bronzePaint = new GradientPaint(
            3.0f, 3.0f, new Color(212, 163, 89),
            17.0f, 17.0f, new Color(84, 54, 16)
        );
        g.setPaint(bronzePaint);
        g.fill(new Ellipse2D.Float(1.0f, 1.0f, 18.0f, 18.0f));

        // Top-left metallic glint on bronze rim
        g.setColor(new Color(255, 235, 170, 220));
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Float(1.5f, 1.5f, 17.0f, 17.0f, 105, 75, Arc2D.OPEN));

        // Bottom-right shadow on bronze rim
        g.setColor(new Color(40, 22, 6, 180));
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Float(1.5f, 1.5f, 17.0f, 17.0f, 285, 75, Arc2D.OPEN));

        // 4 Cardinal Bronze Bezel Rivet Accents (OSRS minimap bezel styling)
        g.setColor(new Color(60, 36, 10, 220));
        g.fillRect(9, 1, 2, 1);  // N
        g.fillRect(9, 18, 2, 1); // S
        g.fillRect(1, 9, 1, 2);  // W
        g.fillRect(18, 9, 1, 2); // E

        // 3. Inner Dark Bezel Rim (14.6x14.6)
        g.setColor(new Color(20, 14, 8, 255));
        g.fill(new Ellipse2D.Float(2.7f, 2.7f, 14.6f, 14.6f));

        // 4. Globe Sphere Base & Continents (Diameter 14.0px, bounds 3.0f, 3.0f, 14.0f, 14.0f)
        Shape origClip = g.getClip();
        Ellipse2D.Float globeClip = new Ellipse2D.Float(3.0f, 3.0f, 14.0f, 14.0f);
        g.setClip(globeClip);

        // 4a. Deep Sapphire Ocean Base with 3D Spherical Radial Depth
        float[] oceanFractions = {0.0f, 0.55f, 1.0f};
        Color[] oceanColors = {
            new Color(56, 189, 248),  // vibrant shallow cyan/blue (#38BDF8)
            new Color(30, 64, 175),   // deep sapphire blue (#1E40AF)
            new Color(15, 23, 42)     // deep ocean shadow (#0F172A)
        };
        RadialGradientPaint oceanRgp = new RadialGradientPaint(
            new Point2D.Float(8.5f, 7.5f),
            7.5f,
            oceanFractions,
            oceanColors
        );
        g.setPaint(oceanRgp);
        g.fill(globeClip);

        // 4b. Continents / Landmasses (Kandarin/Misthalin/Karamja inspired shapes)
        // West Continent (Kandarin / Fremennik / Tirannwn)
        Path2D.Float westContinent = new Path2D.Float();
        westContinent.moveTo(4.0f, 6.0f);
        westContinent.quadTo(5.5f, 4.5f, 7.2f, 5.0f);
        westContinent.quadTo(6.6f, 7.2f, 8.2f, 8.8f);
        westContinent.quadTo(7.2f, 11.2f, 5.0f, 12.0f);
        westContinent.quadTo(3.8f, 9.8f, 4.0f, 6.0f);
        westContinent.closePath();

        // East Continent (Misthalin / Wilderness / Morytania / Desert)
        Path2D.Float eastContinent = new Path2D.Float();
        eastContinent.moveTo(9.5f, 4.6f);
        eastContinent.quadTo(12.5f, 4.0f, 14.8f, 6.2f);
        eastContinent.quadTo(15.4f, 8.8f, 13.6f, 11.2f);
        eastContinent.quadTo(11.2f, 12.4f, 10.0f, 10.4f);
        eastContinent.quadTo(10.8f, 7.8f, 9.5f, 6.4f);
        eastContinent.closePath();

        // South Island (Karamja)
        Path2D.Float southIsland = new Path2D.Float();
        southIsland.moveTo(6.5f, 12.2f);
        southIsland.quadTo(8.5f, 11.6f, 9.4f, 13.2f);
        southIsland.quadTo(7.6f, 15.0f, 6.0f, 13.6f);
        southIsland.closePath();

        // Fill Landmasses with lush OSRS emerald green gradient
        GradientPaint landPaint = new GradientPaint(
            4.0f, 4.0f, new Color(74, 222, 128),
            14.0f, 14.0f, new Color(21, 128, 61)
        );
        g.setPaint(landPaint);
        g.fill(westContinent);
        g.fill(eastContinent);
        g.fill(southIsland);

        // Warm Al Kharid Desert accent on South-East portion of East continent
        g.setColor(new Color(234, 179, 8, 220));
        g.fill(new Ellipse2D.Float(11.2f, 9.2f, 3.0f, 2.2f));

        // Crisp Coastline outline for clarity
        g.setColor(new Color(15, 75, 40, 180));
        g.setStroke(new BasicStroke(0.6f));
        g.draw(westContinent);
        g.draw(eastContinent);
        g.draw(southIsland);

        // 4c. Subtle Sphere Meridian & Equator Grid Lines
        g.setColor(new Color(255, 255, 255, 35));
        g.setStroke(new BasicStroke(0.7f));
        g.draw(new Arc2D.Float(3.0f, 7.6f, 14.0f, 4.8f, 0, 360, Arc2D.OPEN));
        g.draw(new Arc2D.Float(7.6f, 3.0f, 4.8f, 14.0f, 0, 360, Arc2D.OPEN));

        // 4d. White Polar Ice Cap (North Pole)
        g.setColor(new Color(255, 255, 255, 230));
        g.fill(new Ellipse2D.Float(7.8f, 3.0f, 4.4f, 1.8f));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Float(8.6f, 3.2f, 2.8f, 1.1f));

        // 4e. 3D Spherical Limb Shadow / Curvature Vignette
        float[] vignetteFractions = {0.0f, 0.65f, 1.0f};
        Color[] vignetteColors = {
            new Color(0, 0, 0, 0),
            new Color(0, 0, 0, 0),
            new Color(5, 12, 28, 170)
        };
        RadialGradientPaint vignetteRgp = new RadialGradientPaint(
            new Point2D.Float(8.5f, 7.5f),
            7.5f,
            vignetteFractions,
            vignetteColors
        );
        g.setPaint(vignetteRgp);
        g.fill(globeClip);

        // 4f. Top-Left Glass Specular Gloss Glint Arc & Spot
        g.setColor(new Color(255, 255, 255, 190));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Float(4.2f, 4.2f, 11.6f, 11.6f, 105, 75, Arc2D.OPEN));

        GradientPaint specularPaint = new GradientPaint(
            5.5f, 4.5f, new Color(255, 255, 255, 180),
            7.5f, 6.5f, new Color(255, 255, 255, 0)
        );
        g.setPaint(specularPaint);
        g.fill(new Ellipse2D.Float(5.2f, 4.6f, 3.2f, 2.0f));

        // Restore clip
        g.setClip(origClip);

        // 5. Inner Edge Dark Bezel Definition
        g.setColor(new Color(15, 10, 5, 240));
        g.setStroke(new BasicStroke(0.9f));
        g.draw(new Ellipse2D.Float(2.95f, 2.95f, 14.1f, 14.1f));

        // 6. Outer Border Crisp Definition
        g.setColor(new Color(15, 10, 5, 255));
        g.setStroke(new BasicStroke(1.0f));
        g.draw(new Ellipse2D.Float(0.95f, 0.95f, 18.1f, 18.1f));

        g.dispose();
        return img;
    }

    // =========================================================================
    // 18x18 MINIMAP EDGE VENDOR TOGGLE ICONS (ON & OFF) - OSRS COIN POUCH
    // =========================================================================

    private static BufferedImage createMinimapToggleOnIcon()
    {
        final int size = 18;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = initGraphics(img);

        // 1. Dark circular stone backing
        g.setColor(new Color(15, 15, 20, 240));
        g.fill(new Ellipse2D.Float(0.5f, 0.5f, 17.0f, 17.0f));

        // 2. Base Slate Stone Fill
        g.setColor(new Color(30, 34, 42));
        g.fill(new Ellipse2D.Float(1.5f, 1.5f, 15.0f, 15.0f));

        // 3. Pixel-Crafted OSRS Gold Coin Pouch
        // Leather Pouch Base (round body)
        GradientPaint leatherPaint = new GradientPaint(
            4.0f, 7.0f, new Color(180, 83, 9),
            14.0f, 14.0f, new Color(120, 53, 15)
        );
        g.setPaint(leatherPaint);
        g.fillOval(4, 7, 10, 8);

        // Pouch Neck / Pleats
        g.setColor(new Color(146, 64, 14));
        g.fillPolygon(new int[]{6, 12, 11, 7}, new int[]{4, 4, 7, 7}, 4);

        // Gold Cord Tie
        g.setColor(new Color(253, 224, 71));
        g.fillRect(6, 6, 6, 1);
        g.fillRect(5, 7, 2, 2); // Tie tassel

        // Gold Coins Spilling / Glistening from top
        g.setColor(new Color(254, 240, 138));
        g.fillOval(6, 3, 4, 3);
        g.setColor(new Color(234, 179, 8));
        g.fillOval(8, 3, 4, 3);

        // 4. Gold Outer Border Rim
        g.setColor(new Color(255, 190, 40));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Float(1.0f, 1.0f, 16.0f, 16.0f));

        // 5. Active Green Status Dot (Top-Right)
        g.setColor(new Color(34, 197, 94)); // Emerald green (#22C55E)
        g.fillOval(13, 1, 4, 4);
        g.setColor(new Color(20, 83, 45));
        g.setStroke(new BasicStroke(0.5f));
        g.drawOval(13, 1, 4, 4);

        g.dispose();
        return img;
    }

    private static BufferedImage createMinimapToggleOffIcon()
    {
        final int size = 18;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = initGraphics(img);

        // 1. Dark circular stone backing
        g.setColor(new Color(15, 15, 20, 240));
        g.fill(new Ellipse2D.Float(0.5f, 0.5f, 17.0f, 17.0f));

        // 2. Dim Dark Slate Fill
        g.setColor(new Color(25, 28, 35));
        g.fill(new Ellipse2D.Float(1.5f, 1.5f, 15.0f, 15.0f));

        // 3. Muted / Desaturated Coin Pouch
        g.setColor(new Color(80, 85, 95));
        g.fillOval(4, 7, 10, 8);
        g.fillPolygon(new int[]{6, 12, 11, 7}, new int[]{4, 4, 7, 7}, 4);
        g.setColor(new Color(100, 105, 115));
        g.fillRect(6, 6, 6, 1);

        // 4. Muted Border Rim
        g.setColor(new Color(100, 110, 125));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Float(1.0f, 1.0f, 16.0f, 16.0f));

        // 5. Inactive Red Status Slash across the icon
        g.setColor(new Color(15, 23, 42, 220));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(4.0f, 4.0f, 14.0f, 14.0f));

        g.setColor(new Color(239, 68, 68, 240)); // Red (#EF4444)
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(4.0f, 4.0f, 14.0f, 14.0f));

        g.dispose();
        return img;
    }

    private static BufferedImage createMinimapMonsterToggleOnIcon()
    {
        final int size = 18;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = initGraphics(img);

        // 1. Dark circular stone backing
        g.setColor(new Color(15, 15, 20, 240));
        g.fill(new Ellipse2D.Float(0.5f, 0.5f, 17.0f, 17.0f));

        // 2. Base Dark Slate Fill
        g.setColor(new Color(30, 32, 40));
        g.fill(new Ellipse2D.Float(1.5f, 1.5f, 15.0f, 15.0f));

        // 3. Monster Skull / Crossed Blades Emblem
        // Monster Horns / Brow
        g.setColor(new Color(245, 158, 11)); // Amber Horns
        g.fillPolygon(new int[]{4, 6, 7}, new int[]{4, 3, 7}, 3); // Left horn
        g.fillPolygon(new int[]{14, 12, 11}, new int[]{4, 3, 7}, 3); // Right horn

        // Skull Face Dome
        g.setColor(new Color(220, 38, 38)); // Crimson Skull (#DC2626)
        g.fillRoundRect(5, 5, 8, 7, 3, 3);
        g.fillRect(6, 10, 6, 4); // Jaw

        // Eye sockets
        g.setColor(new Color(15, 15, 20));
        g.fillRect(6, 7, 2, 2);
        g.fillRect(10, 7, 2, 2);

        // Teeth / Fangs
        g.setColor(new Color(254, 243, 199));
        g.fillRect(7, 12, 1, 2);
        g.fillRect(9, 12, 1, 2);
        g.fillRect(11, 12, 1, 2);

        // 4. Gold Outer Border Rim
        g.setColor(new Color(255, 190, 40));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Float(1.0f, 1.0f, 16.0f, 16.0f));

        // 5. Active Green Status Dot (Top-Right)
        g.setColor(new Color(34, 197, 94)); // Emerald green (#22C55E)
        g.fillOval(13, 1, 4, 4);
        g.setColor(new Color(20, 83, 45));
        g.setStroke(new BasicStroke(0.5f));
        g.drawOval(13, 1, 4, 4);

        g.dispose();
        return img;
    }

    private static BufferedImage createMinimapMonsterToggleOffIcon()
    {
        final int size = 18;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = initGraphics(img);

        // 1. Dark circular stone backing
        g.setColor(new Color(15, 15, 20, 240));
        g.fill(new Ellipse2D.Float(0.5f, 0.5f, 17.0f, 17.0f));

        // 2. Dim Dark Slate Fill
        g.setColor(new Color(25, 28, 35));
        g.fill(new Ellipse2D.Float(1.5f, 1.5f, 15.0f, 15.0f));

        // 3. Muted / Desaturated Skull
        g.setColor(new Color(90, 95, 105));
        g.fillPolygon(new int[]{4, 6, 7}, new int[]{4, 3, 7}, 3);
        g.fillPolygon(new int[]{14, 12, 11}, new int[]{4, 3, 7}, 3);

        g.setColor(new Color(80, 85, 95));
        g.fillRoundRect(5, 5, 8, 7, 3, 3);
        g.fillRect(6, 10, 6, 4);

        // Eye sockets
        g.setColor(new Color(25, 28, 35));
        g.fillRect(6, 7, 2, 2);
        g.fillRect(10, 7, 2, 2);

        // 4. Muted Border Rim
        g.setColor(new Color(100, 110, 125));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Float(1.0f, 1.0f, 16.0f, 16.0f));

        // 5. Inactive Red Status Slash across the icon
        g.setColor(new Color(15, 23, 42, 220));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(4.0f, 4.0f, 14.0f, 14.0f));

        g.setColor(new Color(239, 68, 68, 240)); // Red (#EF4444)
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(4.0f, 4.0f, 14.0f, 14.0f));

        g.dispose();
        return img;
    }
}
