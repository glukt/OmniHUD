package com.osrscopilot;

import com.google.inject.Provides;
import com.osrscopilot.combat.engine.CombatEncounterManager;
import com.osrscopilot.combat.engine.CombatEventRecorder;
import com.osrscopilot.combat.engine.CombatTickLedger;
import com.osrscopilot.combat.engine.ConsumableAuditor;
import com.osrscopilot.combat.engine.DamageAttributionEngine;
import com.osrscopilot.combat.engine.XpReconciler;
import com.osrscopilot.combat.overlay.CombatMeterOverlay;
import com.osrscopilot.combat.overlay.CombatGraphOverlay;

import com.osrscopilot.data.DungeonEntranceDatabase;
import com.osrscopilot.data.MonsterDatabase;
import com.osrscopilot.data.ShopDatabase;
import com.osrscopilot.data.ShopLiveStockManager;
import com.osrscopilot.data.ShoppingCartManager;
import com.osrscopilot.data.SlayerTaskManager;
import com.osrscopilot.combat.model.CombatStyle;
import com.osrscopilot.data.model.Monster;
import com.osrscopilot.data.model.MonsterSpawnZone;
import com.osrscopilot.data.model.Shop;
import com.osrscopilot.combat.engine.SpellAttackResolver;
import com.osrscopilot.data.model.TownNode;
import com.osrscopilot.map.MinimapVendorOverlay;
import com.osrscopilot.map.TownMapNode;
import com.osrscopilot.map.VendorMapNode;
import com.osrscopilot.map.WorldMapFocusBeaconOverlay;
import com.osrscopilot.map.WorldMapLegendOverlay;
import com.osrscopilot.map.WorldMapMarkerManager;
import com.osrscopilot.map.WorldMapMonsterZoneOverlay;
import com.osrscopilot.map.WorldMapShopTooltipOverlay;
import com.osrscopilot.ui.ShopDirectorySpreadsheetDialog;
import com.osrscopilot.ui.OsrsCopilotPanel;
import com.osrscopilot.util.CopilotPaths;
import com.osrscopilot.util.IconCache;
import com.osrscopilot.util.NpcPortraitManager;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.ScriptID;
import net.runelite.api.WidgetNode;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.ScriptPostFired;

import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.util.Text;

// NOT a hard dependency on RuneLite's stock Loot Tracker (the @PluginDependency was dropped so
// users can opt out). Its LootReceived stream (pickpocket / clue / chest / raid / event loot)
// feeds onLootReceived and the one-time backfill reads its config, but NPC-kill loot arrives
// independently via onNpcLootReceived, so the Loot tab still works without it. Instead of
// force-enabling it, LootTrackerManager.stockLootTrackerAvailable() probes whether it's on and
// LootTabView shows a partial-capability hint when it isn't - mirrors the Party-mode pattern.
// NOT a dependency on the Party plugin - the group damage meter is opt-in (Party mode in the
// Combat panel), so a player who never uses it shouldn't be forced to run Party. CombatPartyService
// checks whether Party is enabled at click time and tells the user to turn it on if it isn't.
@PluginDescriptor(
    name = "OmniHUD",
    description = "Interactive world map, combat and Slayer helpers, loot tracking, and a live stats HUD - one companion panel.",
    tags = {"omnihud", "hud", "copilot", "combat", "dps", "encounter", "slayer", "bestiary", "worldmap", "shops", "loot", "metrics"}
)
@Slf4j
public class OsrsCopilotPlugin extends Plugin
{
    private static final int TOWN_CLICK_RADIUS_SQ = 24 * 24;
    private static final int VENDOR_CLICK_RADIUS_SQ = 18 * 18;

    // Bumped whenever a new one-shot branch is added to runConfigMigrations(). The stored
    // "migrationVersion" key gates each branch so a migration runs once and a later deliberate
    // user choice is never reverted on the next launch.
    //   v1 - clear the reverted Luminous-Halo marker style / native-icon halo defaults.
    //   v2 - carry settings and stored data across the package/identity rename (the RuneLite
    //        config group and the RUNELITE_DIR data directory both moved to the new "osrscopilot"
    //        name).
    private static final int CONFIG_SCHEMA_VERSION = 3;

    // Pre-rename identity: the RuneLite config group and the RUNELITE_DIR data directory were both
    // named after the old package root before it became com.osrscopilot. This is the ONLY place
    // that old name is written down - the one-time migrations in runConfigMigrations() and
    // migrateLegacyDataDir() need it to locate pre-rename data and copy it forward. It is never
    // used as a live group or path anywhere else; do not reintroduce it.
    static final String LEGACY_CONFIG_GROUP = "worldmapdirectory";

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private MouseManager mouseManager;

    @Inject
    private net.runelite.client.input.KeyManager keyManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private OsrsCopilotConfig config;

    @Inject
    private ShopDatabase shopDatabase;

    @Inject
    private MonsterDatabase monsterDatabase;

    @Inject
    private SlayerTaskManager slayerTaskManager;

    @Inject
    private CombatEncounterManager combatEncounterManager;

    @Inject
    private CombatMeterOverlay combatMeterOverlay;

    @Inject
    private CombatGraphOverlay combatGraphOverlay;

    @Inject
    private com.osrscopilot.combat.overlay.CombatStateBannerOverlay combatStateBannerOverlay;

    @Inject
    private com.osrscopilot.combat.tutorial.TutorialOverlay tutorialOverlay;

    @Inject
    private DamageAttributionEngine damageAttributionEngine;

    @Inject
    private CombatEventRecorder combatEventRecorder;

    @Inject
    private CombatTickLedger combatTickLedger;

    @Inject
    private XpReconciler xpReconciler;

    @Inject
    private SpellAttackResolver spellAttackResolver;

    @Inject
    private com.osrscopilot.combat.CombatChatShare combatChatShare;

    @Inject
    private com.osrscopilot.combat.party.CombatPartyService combatPartyService;
    private com.osrscopilot.combat.ui.SourceDetailsWindow sourceDetailsWindow;

    @Inject
    private ConsumableAuditor consumableAuditor;

    @Inject
    private DungeonEntranceDatabase dungeonEntranceDatabase;


    @Inject
    private ShopLiveStockManager liveStockManager;

    @Inject
    private ShoppingCartManager cartManager;

    @Inject
    private ShopDirectorySpreadsheetDialog spreadsheetDialog;

    @Inject
    private WorldMapMarkerManager markerManager;

    @Inject
    private IconCache iconCache;

    @Inject
    private NpcPortraitManager npcPortraitManager;

    @Inject
    private WorldMapOverlay worldMapOverlay;

    @Inject
    private WorldMapShopTooltipOverlay tooltipOverlay;

    @Inject
    private WorldMapMonsterZoneOverlay monsterZoneOverlay;

    @Inject
    private WorldMapLegendOverlay legendOverlay;

    @Inject
    private WorldMapFocusBeaconOverlay beaconOverlay;

    @Inject
    private MinimapVendorOverlay minimapOverlay;

    @Inject
    private com.osrscopilot.map.MinimapCombatButtonOverlay minimapCombatButtonOverlay;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ScheduledExecutorService executorService;

    @Inject
    private com.osrscopilot.loot.LootTrackerManager lootTrackerManager;

    private OsrsCopilotPanel panel;
    private NavigationButton navButton;

    // Flipped false at the top of shutDown() so the background startup task can't touch a
    // torn-down panel / re-add markers after the plugin is disabled.
    private volatile boolean active = false;
    private Shop lastInteractedShop = null;

    private WorldPoint pendingFocusTarget;
    private String pendingFocusLabel;
    private int pendingFocusAttempts;
    private long lastHudToggleMs = 0;

    @Provides
    OsrsCopilotConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(OsrsCopilotConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        log.debug("Starting OmniHUD plugin");

        slayerTaskManager.setMonsterDatabase(monsterDatabase);
        if (monsterZoneOverlay != null)
        {
            monsterZoneOverlay.setZonesVisible(config.showMonsterZones()); // respect the persisted toggle
        }
        if (npcPortraitManager != null)
        {
            npcPortraitManager.setWikiArtEnabled(config.showMonsterArt()); // respect the persisted toggle
        }
        combatEncounterManager.setSessionTimeoutSeconds(config.combatSessionTimeout());
        combatEncounterManager.setEncounterTimeoutSeconds(config.combatEncounterTimeout());
        combatEncounterManager.setSegmentHistoryDepth(config.combatSegmentHistoryDepth());
        combatEncounterManager.setMergeTrash(config.combatMergeTrash());
        combatEncounterManager.setGraphSmoothingSeconds(config.combatGraphSmoothing());
        combatEncounterManager.setBossHpLookup(name ->
        {
            com.osrscopilot.data.model.Monster m = monsterDatabase.getMonsterByName(name);
            return m != null ? m.getHitpoints() : 0;
        });
        combatEncounterManager.setChatShare(combatChatShare);
        if (combatPartyService != null)
        {
            combatPartyService.register();
            combatEncounterManager.setGroupScopeSupplier(combatPartyService::getGroupScopeOrNull);
            combatEncounterManager.setGroupAutoFollowSupplier(combatPartyService::shouldAutoShowGroup);
        }
        combatEncounterManager.setPersistence(configManager);
        combatEncounterManager.restoreTotal(); // "Total" scope survives logout

        if (config.combatDebugCapture())
        {
            combatEventRecorder.start(); // diagnostic capture, opt-in; no-op otherwise
        }

        // Every confirmed player kill (drop or not) advances the loot tracker's kill count.
        if (combatEncounterManager != null && lootTrackerManager != null)
        {
            combatEncounterManager.setKillSink((name, npcId) -> lootTrackerManager.recordKill(name));
            // The game's own "kill count is: N" chat lines raise the counter via Math.max as a
            // backstop, so Loot's dryness maths stays honest even when a kill signal is missed.
            combatEncounterManager.setKillCountSink(lootTrackerManager::raiseKillCount);
        }

        // Supply costs booked by the combat auditor feed the Slayer tab's net-gain (loot - supplies).
        consumableAuditor.setSupplyCostSink(cost ->
        {
            if (slayerTaskManager.hasActiveTask())
            {
                slayerTaskManager.addTaskSupplyCost(cost);
            }
        });

        panel = new OsrsCopilotPanel(
            shopDatabase,
            monsterDatabase,
            slayerTaskManager,
            itemManager,
            npcPortraitManager,
            liveStockManager,
            spreadsheetDialog,
            config,
            cartManager,
            this::focusShopOnMap,
            this::focusMonsterOnMap,
            this::focusMonsterZoneOnMap,
            this::focusPointOnMap,
            combatEncounterManager,
            lootTrackerManager
        );

        BufferedImage icon;
        try
        {
            icon = net.runelite.client.util.ImageUtil.loadImageResource(
                OsrsCopilotPlugin.class, "/com/osrscopilot/nav_icon.png");
        }
        catch (RuntimeException e)
        {
            icon = iconCache.getShopIcon(); // fall back to the procedural globe if the PNG is missing
        }
        navButton = NavigationButton.builder()
            .tooltip("OmniHUD")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);
        overlayManager.add(tooltipOverlay);
        overlayManager.add(monsterZoneOverlay);
        overlayManager.add(legendOverlay);
        overlayManager.add(beaconOverlay);
        overlayManager.add(minimapOverlay);
        overlayManager.add(minimapCombatButtonOverlay);
        overlayManager.add(combatMeterOverlay);
        overlayManager.add(combatGraphOverlay);
        overlayManager.add(combatStateBannerOverlay);
        overlayManager.add(tutorialOverlay);
        minimapOverlay.setShopClickHandler(this::openShopInSidePanel);
        minimapCombatButtonOverlay.setNavButton(navButton);
        minimapCombatButtonOverlay.setPanel(panel);
        spreadsheetDialog.setClientToolbar(clientToolbar, navButton);
        spreadsheetDialog.setNpcPortraitManager(npcPortraitManager);
        monsterZoneOverlay.setOnZoneClickListener((monster, zone) -> {
            if (monster != null)
            {
                SwingUtilities.invokeLater(() -> panel.openMonster(monster));
            }
        });
        combatMeterOverlay.setOnOpenCombatTab(() -> SwingUtilities.invokeLater(() -> {
            if (!panel.isShowing())
            {
                clientToolbar.openPanel(navButton);
            }
            panel.showTab(OsrsCopilotPanel.VIEW_COMBAT);
        }));
        // Right-click the HUD -> the floating "Damage Sources" window (per-weapon / per-spell drill-down).
        combatMeterOverlay.setOnOpenSourceDetails((scope, entity) -> SwingUtilities.invokeLater(() -> {
            if (sourceDetailsWindow == null)
            {
                sourceDetailsWindow = new com.osrscopilot.combat.ui.SourceDetailsWindow(
                    combatEncounterManager, itemManager);
            }
            sourceDetailsWindow.showFor(scope, entity);
        }));
        if (panel != null && panel.getCombatEncounterTabView() != null)
        {
            panel.getCombatEncounterTabView().setOnOpenGraph(() -> {
                if (combatGraphOverlay != null)
                {
                    combatGraphOverlay.toggle();
                }
            });
            panel.getCombatEncounterTabView().setOnToggleHud(this::toggleCombatHud);
            panel.getCombatEncounterTabView().setCombatPartyService(combatPartyService);
        }
        if (panel != null && panel.getAboutView() != null)
        {
            panel.getAboutView().setOnStartCombatTour(this::startCombatHudTour);
            panel.getAboutView().setOnStartTownsMapTour(this::startTownsMapTour);
            panel.getAboutView().setOnStartSlayerTour(this::startSlayerTour);
        }
        combatMeterOverlay.setOnStartTour(this::startCombatHudTour);
        minimapCombatButtonOverlay.setOnToggleHud(this::toggleCombatHud);
        // Tutorial overlay first: while a tour is running it needs to see clicks before the HUD
        // (to advance a gated step, and to swallow clicks on the dimmed-out UI). It is inert and
        // passes every event straight through when no tour is active.
        mouseManager.registerMouseListener(tutorialOverlay);
        mouseManager.registerMouseListener(combatMeterOverlay);
        mouseManager.registerMouseWheelListener(combatMeterOverlay);
        mouseManager.registerMouseListener(combatGraphOverlay);
        mouseManager.registerMouseListener(minimapCombatButtonOverlay);
        mouseManager.registerMouseListener(legendOverlay);
        mouseManager.registerMouseListener(monsterZoneOverlay);
        mouseManager.registerMouseListener(minimapOverlay);

        if (keyManager != null)
        {
            keyManager.registerKeyListener(legendOverlay);
            keyManager.registerKeyListener(tutorialOverlay);
        }


        active = true;
        executorService.execute(() -> {
            runConfigMigrations();
            migrateLegacyDataDir();
            shopDatabase.load();
            monsterDatabase.load();
            slayerTaskManager.loadFromConfig();
            if (!active)
            {
                return; // plugin was disabled while the DBs were loading
            }
            SwingUtilities.invokeLater(() -> { if (active && panel != null) panel.initialize(); });
            clientThread.invokeLater(() -> {
                if (active && client.getGameState() == GameState.LOGGED_IN)
                {
                    markerManager.rebuildMarkers();
                }
            });
        });
    }

    /**
     * One-shot config migrations, gated by a persisted schema version so each runs exactly once.
     * Earlier builds re-applied these resets on every launch, which meant a user who deliberately
     * re-enabled the affected option found it silently reverted after a restart. With the version
     * gate a later deliberate choice sticks; both options stay selectable in the config UI.
     */
    void runConfigMigrations()
    {
        int stored = readMigrationVersion("osrscopilot");

        if (stored >= CONFIG_SCHEMA_VERSION)
        {
            return;
        }

        if (stored < 2)
        {
            // v2: the package/identity rename moved the RuneLite config group to "osrscopilot". A
            // group rename is invisible to RuneLite - every persisted setting would look reset - so
            // copy each key across from the legacy group. The legacy migrationVersion is copied
            // too, which gates the v1 branch below so it is not re-applied. The old group is
            // deliberately left in place as an untouched backup.
            String prefix = LEGACY_CONFIG_GROUP + ".";
            for (String fullKey : configManager.getConfigurationKeys(prefix))
            {
                String key = fullKey.startsWith(prefix) ? fullKey.substring(prefix.length()) : fullKey;
                String value = configManager.getConfiguration(LEGACY_CONFIG_GROUP, key);
                if (value != null)
                {
                    configManager.setConfiguration("osrscopilot", key, value);
                }
            }
            stored = readMigrationVersion("osrscopilot");
        }

        if (stored < 3)
        {
            // v3: the group damage meter went default-ON and now auto-shows the combined meter
            // during any shared party fight (it was default-off and required manually selecting a
            // "Group" scope, and never worked reliably). Clear any persisted value once so the new
            // default applies; a user who wants their numbers private just toggles it back off.
            configManager.unsetConfiguration("osrscopilot", "combatPartyEnabled");
        }

        if (stored < 1)
        {
            // v1: an experimental "Luminous Halo" marker style and the native-icon halo
            // (highlightNativeIcons) both shipped and were reverted to non-default the same day
            // because the halo didn't line up with the real native map icon. A code-level default
            // change can't override a value already persisted to disk, so clear those once. The
            // marker-style option has since been removed entirely - drop any orphaned key so it
            // doesn't linger in the user's RuneLite profile.
            configManager.unsetConfiguration("osrscopilot", "iconStyle");
            if (config.highlightNativeIcons())
            {
                configManager.setConfiguration("osrscopilot", "highlightNativeIcons", false);
            }
        }

        configManager.setConfiguration("osrscopilot", "migrationVersion", CONFIG_SCHEMA_VERSION);
    }

    /** Reads the persisted {@code migrationVersion} for a config group; 0 if unset or unparseable. */
    private int readMigrationVersion(String group)
    {
        try
        {
            String raw = configManager.getConfiguration(group, "migrationVersion");
            if (raw != null && !raw.trim().isEmpty())
            {
                return Integer.parseInt(raw.trim());
            }
        }
        catch (RuntimeException ignored)
        {
            // unreadable / corrupt value - treat as "never migrated" and re-stamp by the caller
        }
        return 0;
    }

    /**
     * One-time move of the persistent data directory after the rename: everything the plugin
     * stores (loot history, portrait cache, CSV exports) lived under the pre-rename
     * {@code RUNELITE_DIR} sub-directory and now lives under {@link CopilotPaths#DATA_DIR}.
     */
    void migrateLegacyDataDir()
    {
        migrateLegacyDataDir(new File(RuneLite.RUNELITE_DIR, LEGACY_CONFIG_GROUP), CopilotPaths.DATA_DIR);
    }

    /**
     * Moves {@code legacyDir} to {@code newDir} exactly once. No-ops (leaving the legacy directory
     * as a backup) once {@code newDir} exists, or if there is nothing to move. Any failure is
     * swallowed - the individual managers recreate their sub-directories lazily, so a failed move
     * degrades to a fresh empty data dir and never blocks start-up.
     */
    static void migrateLegacyDataDir(File legacyDir, File newDir)
    {
        try
        {
            if (!legacyDir.isDirectory())
            {
                return;
            }
            if (newDir.exists())
            {
                log.debug("Both {} and {} exist; leaving the legacy data dir untouched", legacyDir, newDir);
                return;
            }
            File parent = newDir.getParentFile();
            if (parent != null)
            {
                parent.mkdirs();
            }
            Files.move(legacyDir.toPath(), newDir.toPath());
            log.info("Moved plugin data dir {} -> {}", legacyDir, newDir);
        }
        catch (Exception e)
        {
            log.warn("Could not move legacy data dir {} -> {}; a fresh {} will be used",
                legacyDir, newDir, newDir.getName(), e);
        }
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.debug("Stopping OmniHUD plugin");
        active = false;
        pendingFocusTarget = null;
        pendingFocusLabel = null;
        pendingFocusAttempts = 0;
        if (slayerTaskManager != null)
        {
            slayerTaskManager.saveToConfig();
        }
        if (combatEncounterManager != null)
        {
            combatEncounterManager.persistTotal();
            combatEncounterManager.setKillSink(null);
            combatEncounterManager.setKillCountSink(null);
            combatEncounterManager.setBossHpLookup(null);
        }
        if (lootTrackerManager != null)
        {
            lootTrackerManager.flushNow();
        }
        if (spreadsheetDialog != null)
        {
            spreadsheetDialog.setVisible(false);
        }
        if (keyManager != null)
        {
            keyManager.unregisterKeyListener(legendOverlay);
            keyManager.unregisterKeyListener(tutorialOverlay);
        }
        if (tutorialOverlay != null)
        {
            tutorialOverlay.end();
        }
        if (legendOverlay != null)
        {
            // Clears search focus, which removes the global KeyEventDispatcher the overlay adds to
            // the shared KeyboardFocusManager - otherwise disabling the plugin with the map search
            // box focused pins the whole plugin object graph until JVM exit.
            legendOverlay.setSearchFocused(false);
        }
        mouseManager.unregisterMouseListener(combatMeterOverlay);
        mouseManager.unregisterMouseWheelListener(combatMeterOverlay);
        mouseManager.unregisterMouseListener(tutorialOverlay);
        mouseManager.unregisterMouseListener(combatGraphOverlay);
        mouseManager.unregisterMouseListener(minimapCombatButtonOverlay);
        mouseManager.unregisterMouseListener(minimapOverlay);
        mouseManager.unregisterMouseListener(monsterZoneOverlay);
        mouseManager.unregisterMouseListener(legendOverlay);
        overlayManager.remove(tutorialOverlay);
        overlayManager.remove(combatStateBannerOverlay);
        overlayManager.remove(combatGraphOverlay);
        overlayManager.remove(combatMeterOverlay);
        overlayManager.remove(minimapCombatButtonOverlay);
        overlayManager.remove(minimapOverlay);
        overlayManager.remove(beaconOverlay);
        overlayManager.remove(legendOverlay);
        overlayManager.remove(monsterZoneOverlay);
        overlayManager.remove(tooltipOverlay);
        if (monsterZoneOverlay != null)
        {
            monsterZoneOverlay.clear();
        }
        clientToolbar.removeNavigation(navButton);
        if (combatEventRecorder != null)
        {
            combatEventRecorder.stop();
        }
        if (combatTickLedger != null)
        {
            combatTickLedger.clear();
        }
        if (combatPartyService != null)
        {
            combatPartyService.unregister();
        }
        if (combatEncounterManager != null)
        {
            combatEncounterManager.setGroupScopeSupplier(null);
            combatEncounterManager.setGroupAutoFollowSupplier(null);
        }
        if (sourceDetailsWindow != null)
        {
            sourceDetailsWindow.dispose();
            sourceDetailsWindow = null;
        }
        clientThread.invokeLater(markerManager::clearMarkers);
        clientThread.invokeLater(this::closeWorldMapIfWeOpenedIt);
        if (panel != null)
        {
            panel.dispose(); // unregister the cart listener
        }
        panel = null;
        navButton = null;
    }


    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (consumableAuditor != null)
        {
            consumableAuditor.onGameStateChanged(event);
        }

        GameState state = event.getGameState();
        if (state == GameState.LOGGED_IN)
        {
            if (slayerTaskManager != null)
            {
                slayerTaskManager.loadFromConfig();
                slayerTaskManager.syncFromSlayerPluginConfig(configManager);
                slayerTaskManager.updateFromClient(client);
            }
            markerManager.rebuildMarkers();
            // A real login (from the login screen or first load) starts the "Current Session"
            // wall-clock; a world-hop (HOPPING -> LOADING -> LOGGED_IN) must not reset it.
            if (combatEncounterManager != null
                && (previousGameState == null || previousGameState == GameState.LOGIN_SCREEN))
            {
                combatEncounterManager.onLogin();
            }
            // Loot store is (re)pointed at this character in onGameTick, once getLocalPlayer() exists.
        }
        else if (state == GameState.HOPPING)
        {
            // World-hop: close the in-flight fight so it can't merge with the post-hop one, but
            // keep the Current Session / Total scopes running.
            if (combatEncounterManager != null)
            {
                combatEncounterManager.onWorldHop();
            }
        }
        else if (state == GameState.LOGIN_SCREEN)
        {
            markerManager.clearMarkers();
            pendingFocusTarget = null;
            pendingFocusLabel = null;
            pendingFocusAttempts = 0;
            if (lootTrackerManager != null)
            {
                lootTrackerManager.flushNow();
            }
            if (combatEncounterManager != null)
            {
                if (previousGameState == GameState.CONNECTION_LOST)
                {
                    // Dropped connection, not a deliberate logout - don't wipe Current Session.
                    combatEncounterManager.onWorldHop();
                }
                else
                {
                    // Real logout: roll "Current Session" over and flush "Total" to disk.
                    combatEncounterManager.onLogout();
                    // Match the combat session: a deliberate logout ends the loot "This session"
                    // window too (a dropped connection does not).
                    if (lootTrackerManager != null)
                    {
                        lootTrackerManager.resetSession();
                    }
                }
            }
        }
        previousGameState = state;
    }

    private GameState previousGameState = null;

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (slayerTaskManager != null)
        {
            slayerTaskManager.onChatMessage(event);
        }

        if (combatEncounterManager != null)
        {
            // Reads the game's own kill-count lines as a KC backstop; never writes chat.
            combatEncounterManager.onChatMessage(event);
        }

        if (consumableAuditor != null && combatEncounterManager != null)
        {
            consumableAuditor.onChatMessage(
                event,
                combatEncounterManager.getOverallSessionEncounter().getLocalPlayerStats(),
                combatEncounterManager.getCurrentSessionEncounter() != null ? combatEncounterManager.getCurrentSessionEncounter().getLocalPlayerStats() : null,
                combatEncounterManager.getCurrentEncounter() != null ? combatEncounterManager.getCurrentEncounter().getLocalPlayerStats() : null
            );
        }

        if (combatEncounterManager != null && combatEncounterManager.getBuffTracker() != null)
        {
            combatEncounterManager.getBuffTracker().onChatMessage(event);
        }

        if (combatEventRecorder != null)
        {
            combatEventRecorder.onChatMessage(event);
        }
    }


    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if ("slayer".equals(event.getGroup()) && slayerTaskManager != null)
        {
            slayerTaskManager.syncFromSlayerPluginConfig(configManager);
        }

        if (!"osrscopilot".equals(event.getGroup()))
        {
            return;
        }

        if (combatEncounterManager != null)
        {
            combatEncounterManager.setSessionTimeoutSeconds(config.combatSessionTimeout());
            combatEncounterManager.setEncounterTimeoutSeconds(config.combatEncounterTimeout());
            combatEncounterManager.setSegmentHistoryDepth(config.combatSegmentHistoryDepth());
            combatEncounterManager.setMergeTrash(config.combatMergeTrash());
            combatEncounterManager.setGraphSmoothingSeconds(config.combatGraphSmoothing());
            // Turning the HUD back on clears any stray scope selection so it re-follows the live fight.
            if ("showCombatOverlay".equals(event.getKey()) && config.showCombatOverlay())
            {
                combatEncounterManager.selectEncounter(null);
            }
        }

        if ("combatDebugCapture".equals(event.getKey()) && combatEventRecorder != null)
        {
            if (config.combatDebugCapture())
            {
                combatEventRecorder.start();
            }
            else
            {
                combatEventRecorder.stop();
            }
        }

        // Monster-zone overlay master toggle - sync the overlay's in-memory flag so the RuneLite
        // config-panel checkbox takes effect without needing the map legend button.
        if ("showMonsterZones".equals(event.getKey()) && monsterZoneOverlay != null)
        {
            monsterZoneOverlay.setZonesVisible(config.showMonsterZones());
        }

        if ("showMonsterArt".equals(event.getKey()) && npcPortraitManager != null)
        {
            npcPortraitManager.setWikiArtEnabled(config.showMonsterArt());
        }

        // Only marker/filter keys need a marker rebuild - combat/banner keys don't touch the map.
        if (isMarkerKey(event.getKey()))
        {
            clientThread.invokeLater(markerManager::rebuildMarkers);
        }
    }

    /** True for keys that affect world-map markers / filters (i.e. everything except the combat HUD/banner). */
    private static boolean isMarkerKey(String key)
    {
        if (key == null)
        {
            return true;
        }
        // A combat-HUD shortcut, not a map marker - the "combat" prefix rule misses this one.
        if ("showMinimapCombatButton".equals(key) || "showMonsterArt".equals(key))
        {
            return false;
        }
        return !key.startsWith("combat");
    }

    /** Single, debounced, idempotent entry point for every "toggle the combat HUD" control. */
    void toggleCombatHud()
    {
        long now = System.currentTimeMillis();
        if (now - lastHudToggleMs < 200)
        {
            return;
        }
        lastHudToggleMs = now;
        setCombatHudVisible(config == null || !config.showCombatOverlay());
    }

    void setCombatHudVisible(boolean visible)
    {
        if (configManager == null || config == null || config.showCombatOverlay() == visible)
        {
            return;
        }
        configManager.setConfiguration("osrscopilot", "showCombatOverlay", visible);
    }

    /**
     * Launch the interactive Combat HUD walkthrough. The HUD renders itself during demo mode even
     * if the player has it switched off, so nothing here touches config; the tour puts the encounter
     * manager into demo mode on start and takes it out on finish.
     */
    public void startCombatHudTour()
    {
        if (tutorialOverlay == null || combatMeterOverlay == null || combatEncounterManager == null)
        {
            return;
        }
        Runnable openPanel = () -> SwingUtilities.invokeLater(() ->
        {
            if (panel != null && !panel.isShowing())
            {
                clientToolbar.openPanel(navButton);
            }
            if (panel != null)
            {
                panel.showTab(OsrsCopilotPanel.VIEW_COMBAT);
            }
        });
        Runnable openSource = () -> combatMeterOverlay.openSourceDetailsForTopParticipant();
        Runnable closeSource = () -> SwingUtilities.invokeLater(() ->
        {
            if (sourceDetailsWindow != null)
            {
                sourceDetailsWindow.setVisible(false);
            }
        });
        java.util.function.Consumer<String> panelScrollTo = key -> SwingUtilities.invokeLater(() ->
        {
            if (panel != null && panel.getCombatEncounterTabView() != null)
            {
                panel.getCombatEncounterTabView().tourScrollTo(key);
            }
        });
        tutorialOverlay.start(com.osrscopilot.combat.tutorial.CombatHudTour.build(
            combatMeterOverlay, combatEncounterManager, openPanel, openSource, closeSource, panelScrollTo));
    }

    /** Launch the Towns &amp; Map walkthrough (Towns / Shops / Search + the World Map snap). */
    public void startTownsMapTour()
    {
        if (tutorialOverlay == null || panel == null || shopDatabase == null)
        {
            return;
        }

        // Featured entities, resolved once from the real bundled data.
        final com.osrscopilot.data.model.Shop featShop = resolveFeaturedShop();
        final com.osrscopilot.data.model.TownNode featTown = shopDatabase.getTownByName("Varrock");
        final com.osrscopilot.data.model.Monster featMonster = resolveFeaturedMonster();
        final String startTab = panel.getCurrentTab();

        com.osrscopilot.combat.tutorial.TownsMapTour.Host host =
            new com.osrscopilot.combat.tutorial.TownsMapTour.Host()
        {
            private void ui(Runnable r) { SwingUtilities.invokeLater(r); }

            public void showTownsTab()  { ui(() -> openPanelTab(OsrsCopilotPanel.VIEW_TOWNS)); }
            public void showShopsTab()  { ui(() -> openPanelTab(OsrsCopilotPanel.VIEW_STOCK)); }
            public void showSearchTab() { ui(() -> openPanelTab(OsrsCopilotPanel.VIEW_SEARCH)); }

            public void selectFeaturedTown() { ui(() -> panel.getTownHubView().selectTownByName("Al Kharid")); }
            public void townFocus(String key) { ui(() -> panel.getTownHubView().tourFocus(key)); }

            public void showFeaturedShop()
            {
                if (featShop != null) { ui(() -> panel.getVendorStockView().setShop(featShop)); }
            }
            public void stockFocus(String key) { ui(() -> panel.getVendorStockView().tourFocus(key)); }

            public void search(String query) { ui(() -> panel.getGlobalItemSearchView().performSearch(query)); }
            public void searchFocus(String key) { ui(() -> panel.getGlobalItemSearchView().tourFocus(key)); }

            public void snapFeaturedShop()
            {
                if (featShop != null) { focusShopOnMap(featShop); }
            }
            public void snapFeaturedMonster()
            {
                if (featMonster != null) { focusMonsterOnMap(featMonster); }
            }
            public void snapFeaturedTown()
            {
                if (featTown != null) { focusTownOnMap(featTown); }
            }

            public java.awt.Rectangle worldMapBounds()
            {
                if (client == null) { return null; }
                net.runelite.api.widgets.Widget w = client.getWidget(net.runelite.api.gameval.InterfaceID.Worldmap.FRAME);
                return (w != null && !w.isHidden()) ? w.getBounds() : null;
            }
            public void closeMap() { if (clientThread != null) { clientThread.invokeLater(OsrsCopilotPlugin.this::closeWorldMapIfWeOpenedIt); } }
            public void restoreTab() { ui(() -> openPanelTab(startTab)); }
        };

        tutorialOverlay.start(com.osrscopilot.combat.tutorial.TownsMapTour.build(host));
    }

    /**
     * Launch the interactive Slayer walkthrough. Installs a throwaway "Metal dragons" demo task on
     * the {@link SlayerTaskManager} for the duration - nothing is persisted and the real task is
     * restored on finish.
     */
    public void startSlayerTour()
    {
        if (tutorialOverlay == null || panel == null || slayerTaskManager == null || monsterDatabase == null)
        {
            return;
        }

        final String startTab = panel.getCurrentTab();

        com.osrscopilot.combat.tutorial.SlayerTour.Host host =
            new com.osrscopilot.combat.tutorial.SlayerTour.Host()
        {
            private void ui(Runnable r) { SwingUtilities.invokeLater(r); }

            private Monster taskMonster()
            {
                return SlayerTaskManager.findMonsterForTask(
                    slayerTaskManager.getEffectiveMonsterName(), monsterDatabase);
            }

            public void showSlayerTab() { ui(() -> openPanelTab(OsrsCopilotPanel.VIEW_SLAYER)); }

            public void showLootTab() { ui(() -> openPanelTab(OsrsCopilotPanel.VIEW_LOOT)); }

            public void installDemoTask()
            {
                slayerTaskManager.setDemoTask("Metal dragons", 35, 35, null, "Duradel");
            }

            public void installDemoLoot()
            {
                if (lootTrackerManager != null)
                {
                    lootTrackerManager.installSlayerTourDemoLoot();
                }
            }

            public void noteKill(String npcName) { slayerTaskManager.noteKilledNpc(npcName); }

            public void pinSubtype(String member) { slayerTaskManager.setTaskSubtype(member); }

            public void focus(String key) { ui(() -> panel.getSlayerTabView().tourFocus(key)); }

            public void snapSpawn()
            {
                Monster m = taskMonster();
                if (m != null && m.hasSpawnZones())
                {
                    focusMonsterOnMap(m);
                }
            }

            public void openDrops()
            {
                Monster m = taskMonster();
                if (m != null) { ui(() -> panel.openMonster(m)); }
            }

            public java.awt.Rectangle worldMapBounds()
            {
                if (client == null) { return null; }
                net.runelite.api.widgets.Widget w = client.getWidget(net.runelite.api.gameval.InterfaceID.Worldmap.FRAME);
                return (w != null && !w.isHidden()) ? w.getBounds() : null;
            }

            public void closeMap() { if (clientThread != null) { clientThread.invokeLater(OsrsCopilotPlugin.this::closeWorldMapIfWeOpenedIt); } }

            public void teardown()
            {
                slayerTaskManager.clearDemoTask();
                if (lootTrackerManager != null)
                {
                    lootTrackerManager.clearDemoLoot();
                }
                ui(() ->
                {
                    if (panel != null && panel.getSlayerTabView() != null)
                    {
                        panel.getSlayerTabView().tourFocus(null);
                    }
                    openPanelTab(startTab);
                });
            }
        };

        tutorialOverlay.start(com.osrscopilot.combat.tutorial.SlayerTour.build(host));
    }

    private void openPanelTab(String view)
    {
        if (panel != null && view != null)
        {
            if (!panel.isShowing())
            {
                clientToolbar.openPanel(navButton);
            }
            panel.showTab(view);
        }
    }

    private com.osrscopilot.data.model.Shop resolveFeaturedShop()
    {
        com.osrscopilot.data.model.Shop s = shopDatabase.getShopByName("Al Kharid General Store");
        if (s == null)
        {
            java.util.List<com.osrscopilot.data.model.Shop> inTown = shopDatabase.getShopsByTown("al kharid");
            s = inTown.isEmpty() ? null : inTown.get(0);
        }
        return s;
    }

    /**
     * A monster with a spawn zone whose surface entrance is <b>verified</b>, so the Towns &amp; Map
     * tour's "same Map button on monsters" step drops a real pin (not the soft "approximate area"
     * marker). General Graardor -&gt; the confirmed God Wars Dungeon entrance; Vorkath is the
     * fallback.
     */
    private com.osrscopilot.data.model.Monster resolveFeaturedMonster()
    {
        if (monsterDatabase == null)
        {
            return null;
        }
        for (String name : new String[]{"General Graardor", "Vorkath", "Zulrah"})
        {
            com.osrscopilot.data.model.Monster m = monsterDatabase.getMonsterByName(name);
            if (m != null && m.hasSpawnZones())
            {
                return m;
            }
        }
        return null;
    }


    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (slayerTaskManager != null)
        {
            slayerTaskManager.onVarbitChanged(client, event);
        }
        if (spellAttackResolver != null)
        {
            spellAttackResolver.onVarbitChanged(event);
        }
        if (combatEncounterManager != null && combatEncounterManager.getBuffTracker() != null)
        {
            combatEncounterManager.getBuffTracker().onVarbitChanged(event);
        }
        if (combatEventRecorder != null)
        {
            combatEventRecorder.onVarbitChanged(event);
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        if (slayerTaskManager != null)
        {
            slayerTaskManager.onStatChanged(client, event);
        }

        if (consumableAuditor != null && combatEncounterManager != null)
        {
            com.osrscopilot.combat.model.EntityCombatStats overallStats = combatEncounterManager.getOverallSessionEncounter() != null
                ? combatEncounterManager.getOverallSessionEncounter().getLocalPlayerStats() : null;
            com.osrscopilot.combat.model.EntityCombatStats sessionStats = combatEncounterManager.getCurrentSessionEncounter() != null
                ? combatEncounterManager.getCurrentSessionEncounter().getLocalPlayerStats() : null;
            com.osrscopilot.combat.model.EntityCombatStats encounterStats = combatEncounterManager.getCurrentEncounter() != null
                ? combatEncounterManager.getCurrentEncounter().getLocalPlayerStats() : null;

            consumableAuditor.onStatChanged(event, overallStats, sessionStats, encounterStats);
        }

        if (combatEventRecorder != null)
        {
            combatEventRecorder.onStatChanged(event);
        }
        if (combatTickLedger != null)
        {
            combatTickLedger.onStatChanged(event);
        }
    }

    /**
     * ClientTick fires every client frame (~20ms). Provides instantaneous reactive
     * centering and beacon activation as soon as the World Map finishes opening.
     */
    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (pendingFocusTarget != null && !isWorldMapHidden())
        {
            processPendingFocus();
        }
    }

    /**
     * ScriptPostFired hook for WORLDMAP_LOADMAP (Script 1712).
     * Fired when the native client finishes initializing the map.
     */
    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() == ScriptID.WORLDMAP_LOADMAP)
        {
            processPendingFocus();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        int currentTick = client != null ? client.getTickCount() : 0;
        if (slayerTaskManager != null)
        {
            slayerTaskManager.onGameTick(client);
        }
        // getLocalPlayer() is often still null on the LOGGED_IN state change - make sure the loot
        // store is pointed at this character once the player object exists.
        if (lootTrackerManager != null && client != null && client.getLocalPlayer() != null)
        {
            if (lootTrackerManager.getActiveAccountHash() != client.getAccountHash())
            {
                lootTrackerManager.setActiveCharacter(client.getAccountHash(), client.getLocalPlayer().getName());
            }
            if (!lootTrackerManager.hasImported())
            {
                maybeImportStockLootTracker();
            }
            // Resolve any still-nameless drop ids on the CLIENT thread (ItemManager.getItemComposition
            // is client-thread-only) - a few per tick so a big backlog clears in a second or two.
            // hasUnresolvedItemNames() is an incremental counter, so a fully-resolved store skips the
            // whole summaries scan every tick.
            if (itemManager != null && lootTrackerManager.hasUnresolvedItemNames())
            {
                int budget = 25;
                for (Integer id : lootTrackerManager.itemIdsMissingNames())
                {
                    if (budget-- <= 0)
                    {
                        break;
                    }
                    try
                    {
                        net.runelite.api.ItemComposition c = itemManager.getItemComposition(id);
                        if (c != null)
                        {
                            lootTrackerManager.applyItemName(id, c.getName());
                        }
                    }
                    catch (RuntimeException ignored)
                    {
                        // unknown id - leave it as "Item <id>"
                    }
                }
            }
            // Guaranteed live-refresh path for the Loot tab (doesn't rely on Swing-timer timing).
            if (panel != null && panel.getLootTabView() != null && lootTrackerManager.pollChanged())
            {
                javax.swing.SwingUtilities.invokeLater(panel.getLootTabView()::refreshLive);
            }
        }
        if (combatEncounterManager != null)
        {
            combatEncounterManager.onGameTick(currentTick);
            // Buff-uptime polling (~24 client reads/tick) only feeds the combat HUD / tab and the
            // post-fight buff summary - skip it when the HUD is off and no fight is live.
            if (combatEncounterManager.getBuffTracker() != null
                && ((config != null && config.showCombatOverlay())
                    || combatEncounterManager.getCurrentEncounter() != null))
            {
                combatEncounterManager.getBuffTracker().onGameTick(event);
            }
        }
        if (combatPartyService != null)
        {
            combatPartyService.onGameTick(currentTick);
        }
        if (damageAttributionEngine != null)
        {
            damageAttributionEngine.cleanExpiredPendingHits(currentTick);
        }
        processPendingFocus();

        // If the map we opened has since been closed by some other route (ESC, logout), forget the
        // node so a later "Close" click isn't wrongly swallowed and we don't double-close. Skip the
        // first few ticks after we opened it: the frame widget is not un-hidden yet in that window,
        // so isWorldMapHidden() would be a false positive and we'd drop a node we still own.
        boolean pastOpenGrace = worldMapNodeOpenedTick < 0
            || currentTick < worldMapNodeOpenedTick
            || currentTick - worldMapNodeOpenedTick >= WORLD_MAP_NODE_GRACE_TICKS;
        if (worldMapNode != null && isWorldMapHidden() && pastOpenGrace)
        {
            worldMapNode = null;
            worldMapNodeOpenedTick = -1;
        }

        if (combatEventRecorder != null)
        {
            combatEventRecorder.onGameTick(); // last: flush this tick's captured events
        }
        if (combatTickLedger != null)
        {
            combatTickLedger.onGameTick(); // shadow-mode: assemble the per-tick combat record
        }
        if (xpReconciler != null)
        {
            xpReconciler.onGameTick(); // observe-only: HP-XP vs attributed-damage confidence
        }
    }

    @Subscribe
    public void onRemoteCombatSnapshot(com.osrscopilot.combat.party.RemoteCombatSnapshot msg)
    {
        if (combatPartyService != null)
        {
            combatPartyService.ingest(msg);
        }
    }

    @Subscribe
    public void onUserPart(net.runelite.client.party.events.UserPart event)
    {
        if (combatPartyService != null && event != null)
        {
            combatPartyService.onMemberLeft(event.getMemberId());
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (combatEventRecorder != null)
        {
            combatEventRecorder.onHitsplatApplied(event); // raw capture, before attribution filtering
        }
        if (combatTickLedger != null)
        {
            combatTickLedger.onHitsplatApplied(event); // shadow-mode per-tick record
        }
        if (damageAttributionEngine == null || combatEncounterManager == null)
        {
            return;
        }

        int currentTick = client != null ? client.getTickCount() : 0;
        DamageAttributionEngine.ProcessedHit hit = damageAttributionEngine.processHitsplat(event, currentTick);
        if (hit == null)
        {
            return;
        }

        switch (hit.targetType)
        {
            case PLAYER_DEALT:
                String enemyName = null;
                if (hit.actor instanceof NPC)
                {
                    trackCombatTarget((NPC) hit.actor, currentTick);
                    combatEncounterManager.notifyCombatAction(hit.actor, currentTick);
                    enemyName = resolveEnemyName((NPC) hit.actor);
                }
                if (hit.isMiss)
                {
                    combatEncounterManager.recordPlayerMiss(hit.style, hit.source, currentTick, enemyName, hit.isSpecial);
                }
                else
                {
                    combatEncounterManager.recordPlayerDamageDealt(hit.style, hit.amount, hit.source, currentTick, enemyName, hit.isSpecial);
                }
                break;
            case NPC_DEALT:
                // Damage-over-time tick on an NPC — credited only if it's our target (handled in the manager).
                combatEncounterManager.recordPlayerStatusDamage(hit.actor, hit.style, hit.amount, hit.source, currentTick);
                break;
            case THRALL_DEALT:
                if (hit.actor instanceof NPC)
                {
                    combatEncounterManager.notifyCombatAction(hit.actor, currentTick);
                }
                combatEncounterManager.recordThrallDamage(hit.style, hit.amount, hit.source, currentTick);
                break;
            case CANNON_DEALT:
                if (hit.actor instanceof NPC)
                {
                    combatEncounterManager.notifyCombatAction(hit.actor, currentTick);
                }
                combatEncounterManager.recordCannonDamage(hit.amount, currentTick);
                break;
            case PLAYER_TAKEN:
                Actor interacting = (client != null && client.getLocalPlayer() != null)
                    ? client.getLocalPlayer().getInteracting() : null;

                // Attribute the incoming hit to the NPC that is actually attacking the player,
                // and infer its damage type from the bestiary so a multi-mob pull isn't just
                // "8 damage from Enemy (Other)".
                NPC attacker = findAttackerNpc();
                if (attacker != null)
                {
                    // Register the attacker too (not just NPCs we hit), so two identical mobs
                    // beating on us resolve to "Blue dragon (1)" / "(2)" in the taken breakdown.
                    trackCombatTarget(attacker, currentTick);
                }

                // When an NPC hits you FIRST, getInteracting() is still null (you haven't
                // targeted it), so fall back to the attacker so an encounter still opens - this
                // is what makes the "Entering Combat" banner fire on being jumped.
                combatEncounterManager.notifyDamageTakenInCombat(
                    interacting != null ? interacting : attacker, currentTick);

                // A poison / venom / burn / bleed tick already carries its own style and source
                // ("Venom" etc.) from resolveStatusEffect - don't overwrite it with the current
                // attacker's melee/ranged/magic style, or self-inflicted venom reads as
                // "25 from Vorkath (Magic)".
                boolean statusTick = hit.style != null && hit.style.isStatusEffect();
                String takenSource = statusTick ? hit.source
                    : (attacker != null ? resolveEnemyName(attacker) : hit.source);
                CombatStyle takenStyle = statusTick ? hit.style
                    : (attacker != null ? resolveIncomingStyle(attacker) : hit.style);
                combatEncounterManager.recordPlayerDamageTaken(takenStyle, hit.amount, takenSource, currentTick);
                break;
            case PLAYER_HEAL:
                combatEncounterManager.notifyHealInCombat(currentTick);
                combatEncounterManager.recordHpHealed(hit.amount);
                break;
            default:
                break;
        }
    }

    private void trackCombatTarget(NPC npc, int currentTick)
    {
        if (npc == null || (client != null && npc == client.getFollower()))
        {
            return; // never let a follower pet inflate the multi-target count
        }
        if (combatEncounterManager.getMultiTargetTracker() != null)
        {
            combatEncounterManager.getMultiTargetTracker().trackTarget(
                npc, currentTick, config != null && config.combatMultiTargetTagging());
        }
    }

    private String resolveEnemyName(NPC npc)
    {
        if (combatEncounterManager.getMultiTargetTracker() != null)
        {
            return combatEncounterManager.getMultiTargetTracker().getTargetDisplayName(npc);
        }
        return npc.getName();
    }

    /** The NPC currently attacking the local player (prefers the active combat target). */
    private NPC findAttackerNpc()
    {
        if (client == null || client.getLocalPlayer() == null)
        {
            return null;
        }
        Actor active = combatEncounterManager.getActiveTargetActor();
        // A follower pet permanently "interacts with" its owner to face them - that is not an
        // attack. Without this, on any tick the real attacker isn't the first interacting match
        // (just spawned, walking, cannon safespot so the active ref is stale) the pet is returned
        // and ends up naming the fight / in the damage-taken breakdown.
        NPC follower = client.getFollower();
        NPC firstAttacker = null;
        for (NPC npc : client.getNpcs())
        {
            if (npc == null || npc == follower || npc.getInteracting() != client.getLocalPlayer())
            {
                continue;
            }
            if (npc == active)
            {
                return npc;
            }
            if (firstAttacker == null)
            {
                firstAttacker = npc;
            }
        }
        return firstAttacker;
    }

    /**
     * Infer an incoming attack's combat style from the bestiary's recorded attack type.
     *
     * <p>TODO(combat-accuracy): known limitation, in progress. This returns one fixed style per NPC
     * id, so "damage taken by style" is approximate for a multi-style boss (Vorkath fireball vs
     * acid vs melee all read the same). Planned fix derives the style per hit from the attacker's
     * animation / projectile / graphic.
     */
    private CombatStyle resolveIncomingStyle(NPC npc)
    {
        Monster m = null;
        if (monsterDatabase != null)
        {
            // Most records carry a single NPC id, and ~250 variant records only have a synthetic
            // hash, so getMonsterById misses often. Fall back to the NPC's name (the primary variant
            // is close enough for a style guess) rather than silently returning OTHER.
            m = monsterDatabase.getMonsterById(npc.getId());
            if (m == null)
            {
                m = monsterDatabase.getMonsterByName(npc.getName());
            }
        }
        if (m == null)
        {
            return CombatStyle.OTHER;
        }
        switch (m.getAttackStyleClass())
        {
            case MAGIC:
            case MIXED:   // magic-first, matching the old keyword priority
                return CombatStyle.MAGIC;
            case RANGED:
                return CombatStyle.RANGED;
            case MELEE:
                return CombatStyle.MELEE;
            default:
                return CombatStyle.OTHER;
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event)
    {
        if (damageAttributionEngine != null)
        {
            damageAttributionEngine.onProjectileMoved(event);
        }
        if (combatEventRecorder != null)
        {
            combatEventRecorder.onProjectileMoved(event);
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        if (combatEventRecorder != null)
        {
            combatEventRecorder.onAnimationChanged(event);
        }
        if (combatTickLedger != null)
        {
            combatTickLedger.onAnimationChanged(event);
        }
    }

    @Subscribe
    public void onGraphicChanged(GraphicChanged event)
    {
        if (combatEventRecorder != null)
        {
            combatEventRecorder.onGraphicChanged(event);
        }
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event)
    {
        if (combatEventRecorder != null)
        {
            combatEventRecorder.onInteractingChanged(event);
        }
    }

    @Subscribe
    public void onCommandExecuted(CommandExecuted event)
    {
        if (combatEventRecorder == null || !"ccap".equalsIgnoreCase(event.getCommand()))
        {
            return;
        }
        String[] args = event.getArguments();
        String sub = args != null && args.length > 0 ? args[0].toLowerCase() : "toggle";
        switch (sub)
        {
            case "on":
                combatEventRecorder.start();
                break;
            case "off":
                combatEventRecorder.stop();
                break;
            case "mark":
                combatEventRecorder.mark(args.length > 1 ? String.join(" ",
                    java.util.Arrays.copyOfRange(args, 1, args.length)) : "mark");
                break;
            default:
                combatEventRecorder.toggle();
                break;
        }
    }


    @Subscribe
    public void onActorDeath(ActorDeath event)
    {
        if (combatEventRecorder != null)
        {
            combatEventRecorder.onActorDeath(event);
        }
        if (combatEncounterManager != null)
        {
            int currentTick = client != null ? client.getTickCount() : 0;
            combatEncounterManager.handleActorDeath(event.getActor(), currentTick);
        }
        // Auto-follow the umbrella-task subtype (e.g. which metal dragon) from what's dying near you.
        if (slayerTaskManager != null && event.getActor() instanceof net.runelite.api.NPC)
        {
            slayerTaskManager.noteKilledNpc(((net.runelite.api.NPC) event.getActor()).getName());
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        if (itemManager == null || event == null || event.getItems() == null || event.getNpc() == null)
        {
            return;
        }

        java.util.List<com.osrscopilot.loot.model.LootItem> items = buildLootItems(event.getItems());

        // Slayer tab's per-task GP figure - unchanged behaviour, still only while on a task.
        if (slayerTaskManager != null && slayerTaskManager.hasActiveTask())
        {
            long value = 0;
            for (com.osrscopilot.loot.model.LootItem i : items)
            {
                value += i.bestValueTotal();
            }
            slayerTaskManager.addTaskLoot(value);
        }

        // Loot tracker - every NPC kill, task or not.
        if (lootTrackerManager != null && !items.isEmpty())
        {
            net.runelite.api.NPC npc = event.getNpc();
            String task = slayerTaskManager != null && slayerTaskManager.hasActiveTask()
                ? slayerTaskManager.getTaskName() : null;
            lootTrackerManager.recordLoot(
                com.osrscopilot.loot.model.SourceKind.NPC_KILL,
                npc.getId(),
                npc.getName(),
                npc.getCombatLevel(),
                client != null ? client.getWorld() : 0,
                currentRegionId(),
                task,
                isInInstance(),
                items);
        }
    }

    @Subscribe
    public void onPlayerLootReceived(net.runelite.client.events.PlayerLootReceived event)
    {
        if (lootTrackerManager == null || itemManager == null || event == null
            || event.getItems() == null || event.getPlayer() == null)
        {
            return;
        }
        java.util.List<com.osrscopilot.loot.model.LootItem> items = buildLootItems(event.getItems());
        if (items.isEmpty())
        {
            return;
        }
        lootTrackerManager.recordLoot(
            com.osrscopilot.loot.model.SourceKind.PVP,
            -1,
            event.getPlayer().getName(),
            event.getPlayer().getCombatLevel(),
            client != null ? client.getWorld() : 0,
            currentRegionId(),
            null,
            isInInstance(),
            items);
    }

    /**
     * The stock Loot Tracker plugin re-publishes this for chest / clue / pickpocket / event loot that
     * core doesn't emit as NPC loot. Only handle the non-NPC / non-PvP kinds here so we don't
     * double-count kills already captured via {@link #onNpcLootReceived}.
     */
    @Subscribe
    public void onLootReceived(net.runelite.client.plugins.loottracker.LootReceived event)
    {
        if (lootTrackerManager == null || itemManager == null || event == null || event.getItems() == null)
        {
            return;
        }
        com.osrscopilot.loot.model.SourceKind kind;
        String typeName = event.getType() != null ? event.getType().name() : "UNKNOWN";
        switch (typeName)
        {
            case "NPC":
            case "PLAYER":
                return; // handled by the dedicated NPC / PvP subscriptions
            case "PICKPOCKET":
                kind = com.osrscopilot.loot.model.SourceKind.PICKPOCKET;
                break;
            case "EVENT":
                kind = com.osrscopilot.loot.model.SourceKind.CHEST_CLUE;
                break;
            default:
                kind = com.osrscopilot.loot.model.SourceKind.UNKNOWN;
        }
        java.util.List<com.osrscopilot.loot.model.LootItem> items = buildLootItems(event.getItems());
        if (items.isEmpty())
        {
            return;
        }
        lootTrackerManager.recordLoot(
            kind, -1, event.getName(), event.getCombatLevel(),
            client != null ? client.getWorld() : 0, currentRegionId(), null, isInInstance(), items);
    }

    // Package-private for direct unit coverage of the coin / platinum-token valuation below.
    java.util.List<com.osrscopilot.loot.model.LootItem> buildLootItems(java.util.Collection<ItemStack> stacks)
    {
        java.util.List<com.osrscopilot.loot.model.LootItem> out = new java.util.ArrayList<>();
        if (stacks == null)
        {
            return out;
        }
        for (ItemStack stack : stacks)
        {
            int canonId = itemManager.canonicalize(stack.getId());
            long ge = Math.max(0, itemManager.getItemPrice(canonId));
            // Stackable currency has no GE listing (getItemPrice == 0) and no HA value, so it would
            // otherwise be worth 0 gp and never reach the GP totals / GP-hr / value sort. Match the
            // stock Loot Tracker: coins are 1 gp each (value == quantity), platinum tokens 1,000.
            if (canonId == ItemID.COINS_995)
            {
                ge = 1;
            }
            else if (canonId == ItemID.PLATINUM_TOKEN)
            {
                ge = 1000;
            }
            long ha = 0;
            String name = null;
            try
            {
                net.runelite.api.ItemComposition comp = itemManager.getItemComposition(canonId);
                if (comp != null)
                {
                    ha = Math.max(0, comp.getHaPrice());
                    name = comp.getName();
                }
            }
            catch (RuntimeException ignored)
            {
                // no composition - leave ha at 0 / name null
            }
            out.add(com.osrscopilot.loot.model.LootItem.builder()
                .itemId(canonId)
                .quantity(stack.getQuantity())
                .name(name)
                .gePriceEach(ge)
                .haPriceEach(ha)
                .noted(canonId != stack.getId())
                .build());
        }
        return out;
    }

    /**
     * One-time backfill from RuneLite's stock Loot Tracker config (RSProfile-scoped
     * {@code loottracker.drops_*} keys). Aggregate only - counts + KC + first/last, no per-drop
     * timeline - so each entry becomes one imported record + a kill-count seed. Always marks the
     * import done (even with nothing to import) so it doesn't retry every tick.
     */
    private void maybeImportStockLootTracker()
    {
        java.util.List<com.osrscopilot.loot.LootTrackerManager.StockEntry> entries = new java.util.ArrayList<>();
        try
        {
            String profile = configManager.getRSProfileKey();
            java.util.List<String> keys = profile != null
                ? configManager.getRSProfileConfigurationKeys("loottracker", profile, "drops_")
                : java.util.Collections.emptyList();
            for (String key : keys)
            {
                String shortKey = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
                String json = configManager.getConfiguration("loottracker", profile, shortKey);
                if (json == null || json.isEmpty())
                {
                    continue;
                }
                com.google.gson.JsonObject o;
                try
                {
                    o = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
                }
                catch (RuntimeException bad)
                {
                    continue;
                }

                com.osrscopilot.loot.LootTrackerManager.StockEntry e =
                    new com.osrscopilot.loot.LootTrackerManager.StockEntry();
                e.name = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : null;
                e.kills = o.has("kills") ? o.get("kills").getAsInt() : 0;
                String type = o.has("type") && !o.get("type").isJsonNull()
                    ? o.get("type").getAsString().toUpperCase(java.util.Locale.ROOT) : "";
                switch (type)
                {
                    case "NPC":
                        e.kind = com.osrscopilot.loot.model.SourceKind.NPC_KILL;
                        break;
                    case "PLAYER":
                        e.kind = com.osrscopilot.loot.model.SourceKind.PVP;
                        break;
                    case "PICKPOCKET":
                        e.kind = com.osrscopilot.loot.model.SourceKind.PICKPOCKET;
                        break;
                    case "EVENT":
                        e.kind = com.osrscopilot.loot.model.SourceKind.CHEST_CLUE;
                        break;
                    default:
                        e.kind = com.osrscopilot.loot.model.SourceKind.UNKNOWN;
                }
                e.firstMs = parseStockInstant(o.get("first"));
                e.lastMs = parseStockInstant(o.get("last"));
                e.itemQty = new java.util.LinkedHashMap<>();
                if (o.has("drops") && o.get("drops").isJsonArray())
                {
                    com.google.gson.JsonArray arr = o.getAsJsonArray("drops");
                    for (int i = 0; i + 1 < arr.size(); i += 2)
                    {
                        int id = arr.get(i).getAsInt();
                        int qty = arr.get(i + 1).getAsInt();
                        if (id > 0 && qty > 0)
                        {
                            e.itemQty.merge(id, qty, Integer::sum);
                        }
                    }
                }
                if (e.name != null && (!e.itemQty.isEmpty() || e.kills > 0))
                {
                    entries.add(e);
                }
            }
        }
        catch (RuntimeException ex)
        {
            log.debug("Stock Loot Tracker import skipped", ex);
        }
        lootTrackerManager.importFromStock(entries);
        if (!entries.isEmpty())
        {
            log.debug("Loot tracker: imported {} source(s) from RuneLite's Loot Tracker", entries.size());
        }
    }

    private static long parseStockInstant(com.google.gson.JsonElement el)
    {
        if (el == null || el.isJsonNull())
        {
            return 0L;
        }
        try
        {
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber())
            {
                return (long) (el.getAsDouble() * 1000.0); // gson Instant default is "seconds.nanos"
            }
            return java.time.Instant.parse(el.getAsString()).toEpochMilli();
        }
        catch (RuntimeException e)
        {
            return 0L;
        }
    }

    private int currentRegionId()
    {
        if (client == null || client.getLocalPlayer() == null || client.getLocalPlayer().getWorldLocation() == null)
        {
            return -1;
        }
        return client.getLocalPlayer().getWorldLocation().getRegionID();
    }

    private boolean isInInstance()
    {
        return client != null && client.isInInstancedRegion();
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        if (combatEncounterManager != null && event.getNpc() != null)
        {
            int currentTick = client != null ? client.getTickCount() : 0;
            combatEncounterManager.handleNpcDespawned(event.getNpc(), currentTick);
            if (combatEncounterManager.getMultiTargetTracker() != null)
            {
                combatEncounterManager.getMultiTargetTracker().handleNpcDespawn(event.getNpc());
            }
        }
    }

    private void processPendingFocus()
    {
        if (pendingFocusTarget == null)
        {
            return;
        }

        net.runelite.api.worldmap.WorldMap worldMap = client != null ? client.getWorldMap() : null;
        boolean mapOpen = !isWorldMapHidden();
        boolean rendererReady = worldMap != null && (worldMap.getWorldMapRenderer() == null || worldMap.getWorldMapRenderer().isLoaded());

        if (mapOpen && rendererReady)
        {
            WorldPoint target = pendingFocusTarget;
            String label = pendingFocusLabel;
            clearPendingFocus();

            worldMap.setWorldMapPositionTarget(target);
            if (beaconOverlay != null)
            {
                beaconOverlay.triggerBeacon(target, label);
            }
            return;
        }

        pendingFocusAttempts++;
        if (pendingFocusAttempts >= 40)
        {
            log.debug("Timed out waiting for World Map to open for target: {}", pendingFocusTarget);
            clearPendingFocus();
            return;
        }
        // The map was already asked to open in requestPendingFocus(); here we only wait for its
        // renderer to come up so setWorldMapPositionTarget lands (~1-2 ticks after it appears).
    }

    private void clearPendingFocus()
    {
        pendingFocusTarget = null;
        pendingFocusLabel = null;
        pendingFocusAttempts = 0;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (consumableAuditor != null && combatEncounterManager != null)
        {
            if (event.getContainerId() == InventoryID.INVENTORY.getId())
            {
                consumableAuditor.auditInventory(
                    event,
                    combatEncounterManager.getOverallSessionEncounter().getLocalPlayerStats(),
                    combatEncounterManager.getCurrentSessionEncounter() != null ? combatEncounterManager.getCurrentSessionEncounter().getLocalPlayerStats() : null,
                    combatEncounterManager.getCurrentEncounter() != null ? combatEncounterManager.getCurrentEncounter().getLocalPlayerStats() : null
                );
            }
            else if (event.getContainerId() == InventoryID.EQUIPMENT.getId())
            {
                consumableAuditor.auditEquipment(
                    event,
                    combatEncounterManager.getOverallSessionEncounter().getLocalPlayerStats(),
                    combatEncounterManager.getCurrentSessionEncounter() != null ? combatEncounterManager.getCurrentSessionEncounter().getLocalPlayerStats() : null,
                    combatEncounterManager.getCurrentEncounter() != null ? combatEncounterManager.getCurrentEncounter().getLocalPlayerStats() : null
                );
            }
        }



        if (event.getContainerId() == 300 || event.getContainerId() == 301)
        {
            ItemContainer container = event.getItemContainer();
            if (lastInteractedShop != null)
            {
                liveStockManager.updateFromContainer(lastInteractedShop.getId(), container);
                SwingUtilities.invokeLater(() -> panel.openShop(lastInteractedShop));
            }
        }
        else if (event.getContainerId() == InventoryID.BANK.getId())
        {
            ItemContainer container = event.getItemContainer();
            if (container != null && container.getItems() != null)
            {
                long bankCoins = 0;
                for (Item item : container.getItems())
                {
                    if (item != null && item.getId() == ItemID.COINS_995)
                    {
                        bankCoins += item.getQuantity();
                    }
                }
                cartManager.setBankCoins(bankCoins);
            }
        }
        else if (event.getContainerId() == InventoryID.INVENTORY.getId())
        {
            ItemContainer container = event.getItemContainer();
            if (container != null && container.getItems() != null)
            {
                long invCoins = 0;
                for (Item item : container.getItems())
                {
                    if (item != null && item.getId() == ItemID.COINS_995)
                    {
                        invCoins += item.getQuantity();
                    }
                }
                cartManager.setInventoryCoins(invCoins);
            }
        }

        if (combatEventRecorder != null)
        {
            combatEventRecorder.onItemContainerChanged(event);
        }
    }


    // Per-mouse-position memo for the world-map node scan: MenuEntryAdded fires once per menu
    // entry while the menu is built, all at the same cursor position, so scanning hundreds of
    // nodes each time is pure waste.
    private int hoverScanMx = Integer.MIN_VALUE;
    private int hoverScanMy = Integer.MIN_VALUE;
    private TownNode hoverScanTown;
    private Shop hoverScanShop;

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        // 1. World Map Canvas Node hover / left-click & right-click
        Widget worldMap = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        if (worldMap != null && !worldMap.isHidden())
        {
            net.runelite.api.Point mousePos = client.getMouseCanvasPosition();
            if (mousePos != null && worldMap.getBounds().contains(mousePos.getX(), mousePos.getY()))
            {
                int mx = mousePos.getX();
                int my = mousePos.getY();

                if (mx != hoverScanMx || my != hoverScanMy)
                {
                    hoverScanMx = mx;
                    hoverScanMy = my;
                    hoverScanTown = null;
                    hoverScanShop = null;

                    for (TownMapNode node : markerManager.getActiveTownNodes())
                    {
                        net.runelite.api.Point screenPt = worldMapOverlay.mapWorldPointToGraphicsPoint(node.getWorldPoint());
                        if (screenPt != null)
                        {
                            int dx = screenPt.getX() - mx;
                            int dy = screenPt.getY() - my;
                            if ((dx * dx + dy * dy) <= TOWN_CLICK_RADIUS_SQ)
                            {
                                hoverScanTown = node.getTown();
                                break;
                            }
                        }
                    }
                    if (hoverScanTown == null)
                    {
                        for (VendorMapNode node : markerManager.getActiveVendorNodes())
                        {
                            net.runelite.api.Point screenPt = worldMapOverlay.mapWorldPointToGraphicsPoint(node.getWorldPoint());
                            if (screenPt != null)
                            {
                                int dx = screenPt.getX() - mx;
                                int dy = screenPt.getY() - my;
                                if ((dx * dx + dy * dy) <= VENDOR_CLICK_RADIUS_SQ)
                                {
                                    hoverScanShop = node.getShop();
                                    break;
                                }
                            }
                        }
                    }
                }

                if (hoverScanTown != null)
                {
                    final TownNode town = hoverScanTown;
                    client.createMenuEntry(-1)
                        .setOption("View Hub")
                        .setTarget("")
                        .setType(MenuAction.RUNELITE)
                        .onClick(e -> openTownInSidePanel(town.getName()));
                    return;
                }
                if (hoverScanShop != null)
                {
                    final Shop shop = hoverScanShop;
                    lastInteractedShop = shop;
                    if (shop.getTown() != null && !shop.getTown().isEmpty())
                    {
                        client.createMenuEntry(-1)
                            .setOption("View Hub")
                            .setTarget("")
                            .setType(MenuAction.RUNELITE)
                            .onClick(e -> openTownInSidePanel(shop.getTown()));
                    }
                    client.createMenuEntry(-1)
                        .setOption("View Stock")
                        .setTarget("")
                        .setType(MenuAction.RUNELITE)
                        .onClick(e -> openShopInSidePanel(shop));
                    return;
                }
            }
        }

        // 2. Minimap Toggle Buttons hover / click
        if (client.getGameState() == GameState.LOGGED_IN && minimapOverlay != null)
        {
            net.runelite.api.Point mouseCanvasPos = client.getMouseCanvasPosition();
            if (mouseCanvasPos != null && minimapOverlay.isVendorToggleHovered(mouseCanvasPos))
            {
                boolean showVendors = config.showMinimapVendors();
                client.createMenuEntry(-1)
                    .setOption(showVendors ? "Disable" : "Enable")
                    .setTarget("<col=ff981f>Vendor Minimap Icons</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> minimapOverlay.toggleMinimapVendors());
                return;
            }
        }
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        if (event == null || client == null)
        {
            return;
        }

        // 1. Check in-game NPC right-click
        if (shopDatabase != null)
        {
            final MenuEntry[] entries = event.getMenuEntries();
            if (entries != null)
            {
                for (MenuEntry entry : entries)
                {
                    if (entry != null && (entry.getType() == MenuAction.EXAMINE_NPC || entry.getType() == MenuAction.NPC_FIRST_OPTION || entry.getType() == MenuAction.NPC_SECOND_OPTION || entry.getType() == MenuAction.NPC_THIRD_OPTION))
                    {
                        int npcId = entry.getIdentifier();
                        Shop shop = shopDatabase.getShopByNpcId(npcId);
                        if (shop != null)
                        {
                            lastInteractedShop = shop;
                            if (liveStockManager != null)
                            {
                                liveStockManager.setActiveShop(shop);
                            }

                            MenuEntry newEntry = client.createMenuEntry(-1)
                                .setOption("View Shop")
                                .setTarget("<col=ff981f>" + shop.getName() + "</col>")
                                .setType(MenuAction.RUNELITE)
                                .onClick(e -> openShopInSidePanel(shop));
                            addMenuEntry(event, newEntry);
                            return;
                        }
                    }
                }
            }
        }

        // 2. Check Minimap Vendor Orb right-click (within 24px radius)
        if (config != null && config.showMinimapVendors() && client.getGameState() == GameState.LOGGED_IN && minimapOverlay != null)
        {
            net.runelite.api.Point mouseCanvasPos = client.getMouseCanvasPosition();
            if (mouseCanvasPos != null && minimapOverlay.getActiveMarkers() != null)
            {
                for (MinimapVendorOverlay.MinimapShopMarker marker : minimapOverlay.getActiveMarkers())
                {
                    Point minimapPoint = marker.getMinimapPoint();
                    if (minimapPoint != null && minimapPoint.distanceTo(mouseCanvasPos) <= 24)
                    {
                        final Shop shop = marker.getShop();
                        lastInteractedShop = shop;
                        MenuEntry newEntry = client.createMenuEntry(-1)
                            .setOption("View Shop")
                            .setTarget("<col=ff981f>" + shop.getName() + "</col>")
                            .setType(MenuAction.RUNELITE)
                            .onClick(e -> openShopInSidePanel(shop));
                        addMenuEntry(event, newEntry);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Appends a newly-created {@link MenuEntry} to the given {@link MenuOpened} event and commits
     * it back via {@link MenuOpened#setMenuEntries(MenuEntry[])}. Entries created inside a
     * {@code MenuOpened} subscriber (via {@code client.createMenuEntry}) are NOT automatically
     * added to the popup menu the player sees - per RuneLite's Menu/MenuOpened API contract they
     * must be explicitly pushed back onto the event, or they are silently dropped.
     */
    private void addMenuEntry(MenuOpened event, MenuEntry newEntry)
    {
        if (event == null || newEntry == null)
        {
            return;
        }
        MenuEntry[] existing = event.getMenuEntries();
        MenuEntry[] updated = java.util.Arrays.copyOf(existing, existing.length + 1);
        updated[updated.length - 1] = newEntry;
        event.setMenuEntries(updated);
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        // If we opened the World Map (via openInterface), its own X and the minimap orb can't
        // close it - we have to. Intercept those clicks and dispose our interface node.
        if (worldMapNode != null && event.getWidget() != null)
        {
            int group = event.getWidget().getId() >> 16;
            boolean onCross = group == InterfaceID.WORLDMAP;
            boolean onMinimapWorldMapToggle = (group == 160 || group == 161 || group == 164
                || group == 895 || group == 897 || group == 898)
                && "World Map".equalsIgnoreCase(event.getMenuOption());
            if (onCross || onMinimapWorldMapToggle)
            {
                closeWorldMapIfWeOpenedIt();
                event.consume();
                return;
            }
        }

        if (spellAttackResolver != null)
        {
            spellAttackResolver.onMenuOptionClicked(event);
        }

        if (consumableAuditor != null)
        {
            consumableAuditor.onMenuOptionClicked(event);
        }

        if (event.getMenuAction() == MenuAction.RUNELITE)
        {

            String option = event.getMenuOption();
            String target = event.getMenuTarget();
            if (option != null && target != null)
            {
                String cleanTarget = Text.removeTags(target).trim();
                if ("Toggle Vendors".equals(option) && ("<col=ff981f>Minimap</col>".equals(target) || "Minimap".equals(cleanTarget)))
                {
                    configManager.setConfiguration("osrscopilot", "showMinimapVendors", !config.showMinimapVendors());
                    return;
                }
                if ("View Hub".equals(option) || "Open Town Hub".equals(option))
                {
                    openTownInSidePanel(cleanTarget);
                }
                else if ("View Monster".equals(option))
                {
                    Monster monster = monsterDatabase.getMonsterByName(cleanTarget);
                    if (monster != null)
                    {
                        openMonsterInSidePanel(monster);
                    }
                }
                else if ("View Shop".equals(option) || "Open in Side Panel".equals(option) || "View Stock".equals(option) || "View Shop Stock".equals(option))
                {
                    Shop shop = shopDatabase.getShopByName(cleanTarget);
                    if (shop == null && lastInteractedShop != null)
                    {
                        shop = lastInteractedShop;
                    }
                    if (shop != null)
                    {
                        openShopInSidePanel(shop);
                    }
                }
            }
        }
    }

    public void openMonsterInSidePanel(Monster monster)
    {
        if (monster == null)
        {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (clientToolbar != null && navButton != null)
            {
                clientToolbar.openPanel(navButton);
            }
            if (panel != null)
            {
                panel.openMonster(monster);
            }
        });
    }

    public void openShopInSidePanel(Shop shop)
    {
        if (shop == null)
        {
            return;
        }
        lastInteractedShop = shop;
        SwingUtilities.invokeLater(() -> {
            if (clientToolbar != null && navButton != null)
            {
                clientToolbar.openPanel(navButton);
            }
            if (panel != null)
            {
                panel.openShop(shop);
            }
        });
    }

    public void openTownInSidePanel(String townName)
    {
        if (townName == null)
        {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (clientToolbar != null && navButton != null)
            {
                clientToolbar.openPanel(navButton);
            }
            if (panel != null)
            {
                panel.openTown(townName);
            }
        });
    }

    public void focusShopOnMap(Shop shop)
    {
        if (shop == null || shop.getWorldX() == 0 || shop.getWorldY() == 0)
        {
            return;
        }
        focusPointOnMap(shop.getWorldPoint(), shop.getName());
    }

    public void focusTownOnMap(TownNode town)
    {
        if (town == null || town.getWorldX() == 0 || town.getWorldY() == 0)
        {
            return;
        }
        focusPointOnMap(town.getWorldPoint(), town.getName());
    }

    public void focusPointOnMap(WorldPoint pt, String label)
    {
        if (pt == null)
        {
            return;
        }
        clientThread.invokeLater(() -> {
            requestPendingFocus(pt, label);
        });
    }

    public void requestPendingFocus(WorldPoint pt, String label)
    {
        if (pt == null)
        {
            return;
        }

        pendingFocusTarget = pt;
        pendingFocusLabel = label;
        pendingFocusAttempts = 0;

        if (clientThread != null)
        {
            clientThread.invokeLater(() -> {
                if (isWorldMapHidden())
                {
                    triggerOpenWorldMap();
                }
            });
        }
        else if (isWorldMapHidden())
        {
            triggerOpenWorldMap();
        }
    }

    public WorldPoint getPendingFocusTarget()
    {
        return pendingFocusTarget;
    }

    public String getPendingFocusLabel()
    {
        return pendingFocusLabel;
    }

    public int getPendingFocusAttempts()
    {
        return pendingFocusAttempts;
    }

    public void focusMonsterOnMap(Monster monster)
    {
        if (monster == null)
        {
            return;
        }
        if (monsterZoneOverlay != null)
        {
            monsterZoneOverlay.setFocusedMonster(monster);
        }
        if (monster.hasSpawnZones())
        {
            MonsterSpawnZone firstZone = monster.getSpawnZones().get(0);
            focusMonsterZoneOnMap(monster, firstZone);
        }
    }

    public void focusMonsterZoneOnMap(Monster monster, MonsterSpawnZone zone)
    {
        if (zone == null)
        {
            return;
        }
        if (monsterZoneOverlay != null)
        {
            monsterZoneOverlay.setFocusedZone(monster, zone);
        }

        WorldPoint zoneCenter = zone.getZoneCenter();
        if (zoneCenter == null)
        {
            zoneCenter = zone.getEffectiveFocusPoint();
        }

        String monsterName = monster != null ? monster.getName() : "Monster";
        boolean isUnderground = (zone.getSurfaceEntrance() != null)
            || (zone.getDungeonName() != null && !zone.getDungeonName().isEmpty())
            || (dungeonEntranceDatabase != null && zoneCenter != null && dungeonEntranceDatabase.isUndergroundOrDungeon(zoneCenter));

        if (isUnderground)
        {
            // Two candidate surface entrances: the one baked into the spawn zone, then the
            // DungeonEntranceDatabase fallback. Either can be wrong (a lot of the scraped ones point
            // straight back underground), so the actual pick is validated against the world map on
            // the client thread below.
            WorldPoint zoneEntrance = zone.getSurfaceEntrance();
            WorldPoint dbEntrance = null;
            String dungeonName = zone.getDungeonName();
            boolean dbEntranceVerified = false;
            if (dungeonEntranceDatabase != null && zoneCenter != null)
            {
                DungeonEntranceDatabase.DungeonMapping mapping = dungeonEntranceDatabase.findDungeon(zoneCenter);
                if (mapping != null)
                {
                    dbEntrance = mapping.getSurfaceEntrance();
                    dbEntranceVerified = mapping.isEntranceVerified();
                    if (dungeonName == null || dungeonName.isEmpty())
                    {
                        dungeonName = mapping.getDungeonName();
                    }
                }
            }

            final WorldPoint finalZoneCenter = zoneCenter;
            final WorldPoint finalZoneEntrance = zoneEntrance;
            final WorldPoint finalDbEntrance = dbEntrance;
            final boolean finalZoneEntranceVerified = zone.hasVerifiedSurfaceEntrance();
            final boolean finalDbEntranceVerified = dbEntranceVerified;
            final String finalDungeonName = (dungeonName != null && !dungeonName.isEmpty())
                ? dungeonName
                : (zone.getLocationName() != null ? zone.getLocationName() : "Dungeon");

            clientThread.invokeLater(() -> {
                net.runelite.api.worldmap.WorldMapData mapData =
                    (client != null && client.getWorldMap() != null) ? client.getWorldMap().getWorldMapData() : null;

                // Prefer a candidate the world map can actually render as a surface point; this is
                // what discards the "surface entrance" values that themselves point underground
                // (a lot of the scraped ones do). Fall back to whatever entrance we have when the
                // check is inconclusive (map data not loaded) - that's the old behaviour.
                WorldPoint entrance;
                boolean approximate;
                if (surfaceOnMap(mapData, finalZoneEntrance))
                {
                    entrance = finalZoneEntrance;
                    approximate = !finalZoneEntranceVerified;
                }
                else if (surfaceOnMap(mapData, finalDbEntrance))
                {
                    entrance = finalDbEntrance;
                    approximate = !finalDbEntranceVerified;
                }
                else if (finalZoneEntrance != null)
                {
                    entrance = finalZoneEntrance;
                    approximate = !finalZoneEntranceVerified;
                }
                else
                {
                    entrance = finalDbEntrance;
                    approximate = !finalDbEntranceVerified;
                }

                if (entrance != null)
                {
                    // A "~ "-prefixed label tells WorldMapFocusBeaconOverlay to draw a soft "area"
                    // marker instead of a pinpoint beacon - the exact entrance isn't confirmed.
                    requestPendingFocus(entrance, approximate
                        ? "~ " + finalDungeonName + " area (" + monsterName + ") - exact entrance unconfirmed"
                        : "[Dungeon Entrance] " + finalDungeonName + " (" + monsterName + " Spawn)");
                }
                else if (finalZoneCenter != null)
                {
                    // No entrance at all - aim at the zone centre. If it's underground the map just
                    // won't move, but the click still did something.
                    requestPendingFocus(finalZoneCenter,
                        monsterName + " (" + (zone.getSpawnCount() > 0 ? zone.getSpawnCount() + "x" : "Spawn") + ")");
                }
            });
            return;
        }

        String label = (zone.getZoneName() != null && !zone.getZoneName().isEmpty())
            ? zone.getZoneName() + " (" + monsterName + ")"
            : monsterName + " Spawn";
        focusPointOnMap(zoneCenter, label);
    }

    /**
     * True only when the world map's surface view can actually render {@code p}. Returns false when
     * the map data isn't available (can't tell) - callers treat that as "fall back to old behaviour".
     */
    private static boolean surfaceOnMap(net.runelite.api.worldmap.WorldMapData mapData, WorldPoint p)
    {
        return p != null && mapData != null && mapData.surfaceContainsPosition(p.getX(), p.getY());
    }

    public WorldMapMonsterZoneOverlay getMonsterZoneOverlay()
    {
        return monsterZoneOverlay;
    }

    public boolean isWorldMapHidden()
    {
        if (client == null)
        {
            return true;
        }
        // The map's root frame is the reliable "is it up" signal (survives surface/overview toggles).
        Widget frame = client.getWidget(InterfaceID.Worldmap.FRAME);
        if (frame != null)
        {
            return frame.isHidden();
        }
        Widget worldMap = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        if (worldMap != null)
        {
            return worldMap.isHidden();
        }
        Widget worldMapOverview = client.getWidget(InterfaceID.Worldmap.OVERVIEW_CONTAINER);
        if (worldMapOverview != null)
        {
            return worldMapOverview.isHidden();
        }
        return true;
    }

    private WidgetNode worldMapNode;

    /**
     * Client tick {@link #triggerOpenWorldMap()} last linked {@link #worldMapNode}; -1 when we hold
     * no node. For the first couple of ticks after {@code openInterface} the map's frame widget is
     * not un-hidden yet, so {@link #isWorldMapHidden()} briefly still reports true - without this
     * grace window the {@code onGameTick} cleanup would null out the node we just opened and the
     * native X / minimap toggle could then never close the map (an {@code openInterface}'d node has
     * to be closed by us).
     */
    private int worldMapNodeOpenedTick = -1;

    private static final int WORLD_MAP_NODE_GRACE_TICKS = 3;

    /**
     * Opens the in-game World Map by linking interface {@code 595} into the client's floating-window
     * layer - the same thing the engine does on a real minimap-orb click. (The orb's own op handler
     * is just a click sound, which is why {@code menuAction} / synthetic key events on it never
     * opened the map.) The pan to {@link #pendingFocusTarget} is finished by
     * {@link #processPendingFocus()} once the map renderer has loaded.
     */
    public void triggerOpenWorldMap()
    {
        if (client == null)
        {
            return;
        }
        Widget frame = client.getWidget(InterfaceID.Worldmap.FRAME);
        if (frame != null && !frame.isHidden())
        {
            return; // already open, via us or the orb
        }
        try
        {
            worldMapNode = client.openInterface(worldMapParentId(), InterfaceID.WORLDMAP, WidgetModalMode.NON_MODAL);
            worldMapNodeOpenedTick = client.getTickCount();
        }
        catch (RuntimeException e)
        {
            // parent slot busy / already bound elsewhere - the pan retry in processPendingFocus still runs
            log.debug("openInterface(worldmap) failed: {}", e.getMessage());
        }
        // Jump the camera straight away; onScriptPostFired(WORLDMAP_LOADMAP) + processPendingFocus
        // cover it again once the renderer exists.
        if (pendingFocusTarget != null)
        {
            WorldPoint p = pendingFocusTarget;
            int packed = (p.getPlane() << 28) | (p.getX() << 14) | p.getY();
            try
            {
                client.runScript(1756, 1, packed, 1); // [clientscript,worldmap_jumptodisplaycoord]
            }
            catch (RuntimeException ignored)
            {
                // unofficial script id - setWorldMapPositionTarget in processPendingFocus is the backstop
            }
        }
    }

    /** The client layer the World Map interface is linked into for the current layout. */
    private int worldMapParentId()
    {
        if (!client.isResized())
        {
            return InterfaceID.Toplevel.MAINCRM;
        }
        return client.getTopLevelInterfaceId() == InterfaceID.TOPLEVEL_OSRS_STRETCH
            ? InterfaceID.ToplevelOsrsStretch.FLOATER
            : InterfaceID.ToplevelPreEoc.FLOATER;
    }

    /** Close the World Map interface we opened (no-op if the user already closed it). */
    private void closeWorldMapIfWeOpenedIt()
    {
        if (client == null || worldMapNode == null)
        {
            return;
        }
        try
        {
            client.closeInterface(worldMapNode, true);
        }
        catch (RuntimeException e)
        {
            log.debug("closeInterface for world map failed", e);
        }
        worldMapNode = null;
        worldMapNodeOpenedTick = -1;
    }

    public SlayerTaskManager getSlayerTaskManager()
    {
        return slayerTaskManager;
    }
}
