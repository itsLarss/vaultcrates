package de.itslarss.vaultcrates;

import de.itslarss.vaultcrates.animation.AnimationManager;
import de.itslarss.vaultcrates.util.UpdateChecker;
import de.itslarss.vaultcrates.api.VaultCratesAPI;
import de.itslarss.vaultcrates.command.VCCommand;
import de.itslarss.vaultcrates.command.VCTabCompleter;
import de.itslarss.vaultcrates.config.ConfigManager;
import de.itslarss.vaultcrates.config.Messages;
import de.itslarss.vaultcrates.crate.CrateManager;
import de.itslarss.vaultcrates.gui.GuiManager;
import de.itslarss.vaultcrates.hologram.HologramManager;
import de.itslarss.vaultcrates.hook.*;
import de.itslarss.vaultcrates.key.KeyManager;
import de.itslarss.vaultcrates.listener.CrateListener;
import de.itslarss.vaultcrates.listener.GuiListener;
import de.itslarss.vaultcrates.listener.PlayerListener;
import de.itslarss.vaultcrates.listener.PouchListener;
import de.itslarss.vaultcrates.pouch.PouchManager;
import de.itslarss.vaultcrates.storage.StorageManager;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Main class for VaultCrates.
 * Wires together all managers, listeners, hooks, and commands.
 */
public final class VaultCrates extends JavaPlugin {

    private static VaultCrates instance;

    // Managers
    private ConfigManager configManager;
    private Messages messages;
    private CrateManager crateManager;
    private KeyManager keyManager;
    private StorageManager storageManager;
    private PouchManager pouchManager;
    private HologramManager hologramManager;
    private AnimationManager animationManager;
    private GuiManager guiManager;

    // Update checker
    private UpdateChecker updateChecker;

    // Hooks
    private VaultHook vaultHook;
    private PlaceholderAPIHook placeholderAPIHook;
    private ItemsAdderHook itemsAdderHook;
    private OraxenHook oraxenHook;
    private NexoHook nexoHook;
    private ExecutableItemsHook executableItemsHook;
    private MMOItemsHook mmoItemsHook;
    private CitizensHook citizensHook;
    private ZNPCsHook znpcsHook;
    private ZNPCsPlusHook znpcsPlusHook;
    private FancyNpcsHook fancyNpcsHook;
    private NpcManager npcManager;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onEnable() {
        instance = this;

        printBanner();

        // 0. Initialize static NamespacedKeys that depend on the plugin instance
        de.itslarss.vaultcrates.pouch.Pouch.initKeys();
        de.itslarss.vaultcrates.key.PhysicalKeyUtil.initKeys();

        // 1. Config
        configManager = new ConfigManager(this);
        configManager.reload();

        messages = new Messages(this);
        messages.reload();

        // Load rarity registry (after config, before crates so rewards can resolve rarities)
        de.itslarss.vaultcrates.crate.reward.Rarity.loadFromConfig();

        // 2. Storage + Keys
        storageManager = new StorageManager(this);
        storageManager.loadAll();

        keyManager = new KeyManager(this);
        keyManager.load();

        // 3. Crates & Pouches
        crateManager = new CrateManager(this);
        crateManager.reload();

        pouchManager = new PouchManager(this);
        pouchManager.reload();

        // 4. Holograms — delay by 1 tick so TextDisplay entities are spawned
        //    after the server tick has started (avoids issues on startup)
        hologramManager = new HologramManager(this);
        getServer().getScheduler().runTask(this, () -> hologramManager.reloadAll());

        // 5. Animations
        animationManager = new AnimationManager(this);

        // 6. GUI
        guiManager = new GuiManager(this);

        // 7. Hooks
        setupHooks();

        // 8. Listeners
        registerListeners();

        // 9. Commands
        registerCommands();

        // 10. API
        VaultCratesAPI.init(this);

        // 11. bStats
        setupMetrics();

        // 12. Update checker
        if (configManager.getBoolean("Settings.Update_Checker", true)) {
            updateChecker = new UpdateChecker(this);
            updateChecker.start();
        }

        getLogger().info("VaultCrates v" + getDescription().getVersion() + " enabled successfully.");
    }

    @Override
    public void onDisable() {
        // Stop all active animations gracefully
        if (animationManager != null) {
            animationManager.stopAll();
        }

        // Remove all holograms
        if (hologramManager != null) {
            hologramManager.removeAll();
        }

        // Persist crate locations
        if (crateManager != null) {
            crateManager.saveCrateLocations();
        }

        // Save all data and close the storage backend
        if (storageManager != null) {
            storageManager.saveAll();
            storageManager.closeBackend();
        }

        instance = null;
        getLogger().info("VaultCrates disabled. Goodbye!");
    }

    // -------------------------------------------------------------------------
    // Public reload (called by /vc reload)
    // -------------------------------------------------------------------------

    public void reload() {
        // Stop all running animations first
        if (animationManager != null) animationManager.stopAll();

        // Remove existing holograms
        if (hologramManager != null) hologramManager.removeAll();

        // Save before reloading
        if (crateManager != null) crateManager.saveCrateLocations();
        if (storageManager != null) storageManager.saveAll();

        // Reload configs
        configManager.reload();
        messages.reload();
        de.itslarss.vaultcrates.crate.reward.Rarity.loadFromConfig();

        // Reload data
        crateManager.reload();
        pouchManager.reload();

        // Respawn holograms on next tick (safe for both initial load and /vc reload)
        getServer().getScheduler().runTask(this, () -> hologramManager.reloadAll());

        // Reload NPC mappings
        if (npcManager != null) npcManager.loadMappings();

        getLogger().info("VaultCrates reloaded.");
    }

    // -------------------------------------------------------------------------
    // Setup helpers
    // -------------------------------------------------------------------------

    private void setupHooks() {
        // Vault
        vaultHook = new VaultHook(this);
        if (vaultHook.setup()) {
            getLogger().info("Hooked into Vault economy.");
        } else {
            getLogger().warning("Vault not found — economy features disabled.");
        }

        // PlaceholderAPI
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderAPIHook = new PlaceholderAPIHook(this);
            placeholderAPIHook.register();
            getLogger().info("Hooked into PlaceholderAPI.");
        } else {
            getLogger().info("PlaceholderAPI not found — placeholders disabled.");
        }

        // ItemsAdder
        itemsAdderHook = new ItemsAdderHook();
        if (itemsAdderHook.setup()) getLogger().info("Hooked into ItemsAdder.");

        // Oraxen
        oraxenHook = new OraxenHook();
        if (oraxenHook.setup()) getLogger().info("Hooked into Oraxen.");

        // Nexo
        nexoHook = new NexoHook();
        if (nexoHook.setup()) getLogger().info("Hooked into Nexo.");

        // ExecutableItems
        executableItemsHook = new ExecutableItemsHook();
        if (executableItemsHook.setup()) getLogger().info("Hooked into ExecutableItems.");

        // MMOItems
        mmoItemsHook = new MMOItemsHook();
        if (mmoItemsHook.setup()) getLogger().info("Hooked into MMOItems.");

        // Citizens
        citizensHook = new CitizensHook(this);
        if (citizensHook.setup()) getLogger().info("Hooked into Citizens.");

        // ZNPCs (old)
        znpcsHook = new ZNPCsHook(this);
        if (znpcsHook.setup()) getLogger().info("Hooked into ZNPCs.");

        // ZNPCsPlus
        znpcsPlusHook = new ZNPCsPlusHook(this);
        if (znpcsPlusHook.setup()) getLogger().info("Hooked into ZNPCsPlus.");

        // FancyNpcs
        fancyNpcsHook = new FancyNpcsHook(this);
        if (fancyNpcsHook.setup()) getLogger().info("Hooked into FancyNpcs.");

        // NPC manager (loads npcs.yml mappings into all active hooks)
        npcManager = new NpcManager(this, citizensHook, znpcsHook, znpcsPlusHook, fancyNpcsHook);
        npcManager.loadMappings();
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new CrateListener(this), this);
        pm.registerEvents(new PouchListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new GuiListener(this), this);
    }

    private void registerCommands() {
        PluginCommand vc = Objects.requireNonNull(getCommand("vc"), "Command 'vc' not defined in plugin.yml");
        VCCommand executor = new VCCommand(this);
        vc.setExecutor(executor);
        vc.setTabCompleter(new VCTabCompleter(this));
    }

    private void setupMetrics() {
        try {
            Metrics metrics = new Metrics(this, 24000);
            metrics.addCustomChart(new SimplePie("crate_count",
                    () -> String.valueOf(crateManager.getCrates().size())));
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to start bStats metrics", e);
        }
    }

    private void printBanner() {
        getLogger().info("╔══════════════════════════════════╗");
        getLogger().info("║       VaultCrates  by itslarss   ║");
        getLogger().info("║   v" + padRight(getDescription().getVersion(), 29) + "║");
        getLogger().info("╚══════════════════════════════════╝");
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    // -------------------------------------------------------------------------
    // Singleton accessor
    // -------------------------------------------------------------------------

    public static VaultCrates getInstance() {
        return instance;
    }

    // -------------------------------------------------------------------------
    // Manager accessors
    // -------------------------------------------------------------------------

    public UpdateChecker getUpdateChecker()          { return updateChecker; }
    public ConfigManager getConfigManager()         { return configManager; }
    public Messages getMessages()                   { return messages; }
    public CrateManager getCrateManager()           { return crateManager; }
    public KeyManager getKeyManager()               { return keyManager; }
    public StorageManager getStorageManager()       { return storageManager; }
    public PouchManager getPouchManager()           { return pouchManager; }
    public HologramManager getHologramManager()     { return hologramManager; }
    public AnimationManager getAnimationManager()   { return animationManager; }
    public GuiManager getGuiManager()               { return guiManager; }

    // -------------------------------------------------------------------------
    // Hook accessors
    // -------------------------------------------------------------------------

    public VaultHook           getVaultHook()            { return vaultHook; }
    public PlaceholderAPIHook  getPlaceholderAPIHook()   { return placeholderAPIHook; }
    public ItemsAdderHook      getItemsAdderHook()       { return itemsAdderHook; }
    public OraxenHook          getOraxenHook()           { return oraxenHook; }
    public NexoHook            getNexoHook()             { return nexoHook; }
    public ExecutableItemsHook getExecutableItemsHook()  { return executableItemsHook; }
    public MMOItemsHook        getMmoItemsHook()         { return mmoItemsHook; }
    public CitizensHook        getCitizensHook()         { return citizensHook; }
    public ZNPCsHook           getZnpcsHook()            { return znpcsHook; }
    public ZNPCsPlusHook       getZnpcsPlusHook()        { return znpcsPlusHook; }
    public FancyNpcsHook       getFancyNpcsHook()        { return fancyNpcsHook; }
    public NpcManager          getNpcManager()           { return npcManager; }

    /** @return {@code true} if PlaceholderAPI is hooked */
    public boolean hasPAPI() {
        return placeholderAPIHook != null;
    }

    /** @return {@code true} if Vault economy is available */
    public boolean hasVault() {
        return vaultHook != null && vaultHook.isEnabled();
    }
}
