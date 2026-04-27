package de.itslarss.vaultcrates.config;

import com.google.gson.*;
import de.itslarss.vaultcrates.VaultCrates;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages the main plugin configuration ({@code config.yml}) and all
 * machine-managed JSON data files under {@code data/}.
 *
 * <h3>config.yml versioning</h3>
 * A {@code _version} key is stored in the file. On every load the bundled
 * default config is compared; any keys that are missing from the user's file
 * are filled in automatically without touching existing values.
 *
 * <h3>Locations</h3>
 * Placed crate instances are persisted in {@code data/locations.json}.
 * If an old {@code locations.yml} exists it is migrated automatically on first
 * load and renamed to {@code locations.yml.bak}.
 */
public class ConfigManager {

    /** Increment this whenever new keys are added to the bundled config.yml. */
    private static final int CONFIG_VERSION = 3;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final VaultCrates plugin;

    /** {@code data/locations.json} */
    private final File locationsFile;

    public ConfigManager(VaultCrates plugin) {
        this.plugin = plugin;

        // Ensure data/ directory exists
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();

        this.locationsFile = new File(dataDir, "locations.json");

        // Save default config.yml only if it does not exist yet
        plugin.saveDefaultConfig();

        // Fill in any keys that are missing (keeps user values intact)
        applyConfigVersioning();

        // One-time migration from the old locations.yml
        migrateLocationsYaml();
    }

    // -------------------------------------------------------------------------
    // config.yml — versioning
    // -------------------------------------------------------------------------

    /**
     * Compares the stored {@code _version} against {@link #CONFIG_VERSION}.
     * When the stored version is lower (or absent), the bundled {@code config.yml}
     * is loaded as a defaults overlay: every missing key is copied in, existing
     * keys are left untouched, and {@code _version} is updated.
     */
    private void applyConfigVersioning() {
        FileConfiguration cfg = plugin.getConfig();
        int storedVersion = cfg.getInt("_version", 0);

        if (storedVersion >= CONFIG_VERSION) return;

        InputStream defStream = plugin.getResource("config.yml");
        if (defStream == null) return;

        try (InputStreamReader reader = new InputStreamReader(defStream, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            cfg.setDefaults(defaults);
            cfg.options().copyDefaults(true);
            cfg.set("_version", CONFIG_VERSION);
            plugin.saveConfig();
            plugin.getLogger().info("config.yml updated to version " + CONFIG_VERSION
                    + " (missing keys have been added).");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not apply config defaults", e);
        }
    }

    // -------------------------------------------------------------------------
    // config.yml accessors
    // -------------------------------------------------------------------------

    /** Returns the main plugin configuration ({@code config.yml}). */
    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public boolean getBoolean(String path, boolean def)  { return plugin.getConfig().getBoolean(path, def); }
    public boolean getBoolean(String path)               { return plugin.getConfig().getBoolean(path); }
    public int     getInt(String path, int def)          { return plugin.getConfig().getInt(path, def); }
    public int     getInt(String path)                   { return plugin.getConfig().getInt(path); }
    public double  getDouble(String path, double def)    { return plugin.getConfig().getDouble(path, def); }
    public double  getDouble(String path)                { return plugin.getConfig().getDouble(path); }

    public String getString(String path, String def) {
        String v = plugin.getConfig().getString(path, def);
        return v != null ? v : def;
    }

    public String getString(String path) {
        return plugin.getConfig().getString(path);
    }

    public List<String> getStringList(String path) {
        return plugin.getConfig().getStringList(path);
    }

    // -------------------------------------------------------------------------
    // data/locations.json
    // -------------------------------------------------------------------------

    /**
     * Loads {@code data/locations.json} and returns its root as a {@link JsonObject}.
     * Returns an empty object if the file is absent or contains invalid JSON.
     */
    public JsonObject loadLocationsJson() {
        if (!locationsFile.exists()) return new JsonObject();
        try (Reader reader = new InputStreamReader(
                new FileInputStream(locationsFile), StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(reader);
            if (el.isJsonObject()) return el.getAsJsonObject();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not read data/locations.json", e);
        }
        return new JsonObject();
    }

    /**
     * Writes {@code json} to {@code data/locations.json} (pretty-printed, UTF-8).
     */
    public void saveLocationsJson(JsonObject json) {
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(locationsFile), StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data/locations.json", e);
        }
    }

    // -------------------------------------------------------------------------
    // Migration: locations.yml → data/locations.json
    // -------------------------------------------------------------------------

    /**
     * Converts an existing {@code locations.yml} in the plugin data folder to
     * {@code data/locations.json}. Only runs when the YAML file exists but the
     * JSON file does not. After conversion the YAML file is renamed to
     * {@code locations.yml.bak}.
     */
    private void migrateLocationsYaml() {
        File yamlFile = new File(plugin.getDataFolder(), "locations.yml");
        if (!yamlFile.exists() || locationsFile.exists()) return;

        plugin.getLogger().info("Migrating locations.yml → data/locations.json ...");

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(yamlFile);
        JsonObject placements = new JsonObject();

        // ── New YAML format: placements.<id>.crate / .location ──────────────
        var newSection = yml.getConfigurationSection("placements");
        if (newSection != null) {
            for (String id : newSection.getKeys(false)) {
                var entry = newSection.getConfigurationSection(id);
                if (entry == null) continue;
                String crate = entry.getString("crate");
                String loc   = entry.getString("location");
                if (crate == null || loc == null) continue;
                JsonObject obj = new JsonObject();
                obj.addProperty("crate", crate);
                obj.addProperty("location", loc);
                placements.add(id, obj);
            }
        }

        // ── Legacy YAML format: crate-locations.<loc_key>: CrateName ────────
        var legacySection = yml.getConfigurationSection("crate-locations");
        if (legacySection != null) {
            for (String locKey : legacySection.getKeys(false)) {
                String crate = legacySection.getString(locKey);
                if (crate == null) continue;
                String locStr = locKey.replace("_", ",");
                String id     = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
                JsonObject obj = new JsonObject();
                obj.addProperty("crate", crate);
                obj.addProperty("location", locStr);
                placements.add(id, obj);
            }
        }

        JsonObject root = new JsonObject();
        root.add("placements", placements);
        saveLocationsJson(root);

        // Rename old file so we do not migrate again on the next start
        File bak = new File(plugin.getDataFolder(), "locations.yml.bak");
        if (yamlFile.renameTo(bak)) {
            plugin.getLogger().info("locations.yml renamed to locations.yml.bak (migration done).");
        }
    }

    // -------------------------------------------------------------------------
    // Reload
    // -------------------------------------------------------------------------

    /**
     * Reloads {@code config.yml} from disk and re-applies versioning.
     * Location data is read on demand by {@link de.itslarss.vaultcrates.crate.CrateManager},
     * so no explicit reload is needed here.
     */
    public void reload() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration test = new YamlConfiguration();
        try {
            test.load(configFile);
        } catch (InvalidConfigurationException e) {
            plugin.getLogger().severe("config.yml contains invalid YAML and could not be reloaded!");
            plugin.getLogger().severe("Fix the error below, then run /vc reload again.");
            plugin.getLogger().severe(e.getMessage());
            return; // keep the last valid in-memory config — do NOT overwrite
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not read config.yml", e);
            return;
        }
        plugin.reloadConfig();
        applyConfigVersioning();
    }
}
