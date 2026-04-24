package de.itslarss.vaultcrates.hook;

import com.google.gson.*;
import de.itslarss.vaultcrates.VaultCrates;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Central manager that wires together all NPC plugin hooks and persists NPC→crate
 * mappings to {@code data/npcs.json}.
 *
 * <p>JSON structure:</p>
 * <pre>
 * {
 *   "entities":  { "&lt;uuid&gt;": "CrateName" },
 *   "citizens":  { "1": "MyCrate", "2": "AnotherCrate" },
 *   "znpcs":     { "guard_npc": "MyCrate" },
 *   "znpcsplus": { "trader": "ShopCrate" },
 *   "fancynpcs": { "shopkeeper": "VaultCrate" }
 * }
 * </pre>
 *
 * <h3>Migration</h3>
 * If {@code data/npcs.yml} exists and {@code data/npcs.json} does not, the
 * YAML data is converted automatically and the old file renamed to
 * {@code data/npcs.yml.bak}.
 */
public class NpcManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final VaultCrates plugin;
    /** {@code data/npcs.json} */
    private final File file;

    private final CitizensHook citizensHook;
    private final ZNPCsHook znpcsHook;
    private final ZNPCsPlusHook znpcsPlusHook;
    private final FancyNpcsHook fancyNpcsHook;

    /** Generic entity UUID → crate mapping for NPCs detected by proximity (any plugin). */
    private final Map<UUID, String> entityUuidToCrate = new HashMap<>();

    public NpcManager(VaultCrates plugin,
                      CitizensHook citizensHook,
                      ZNPCsHook znpcsHook,
                      ZNPCsPlusHook znpcsPlusHook,
                      FancyNpcsHook fancyNpcsHook) {
        this.plugin = plugin;
        this.citizensHook = citizensHook;
        this.znpcsHook = znpcsHook;
        this.znpcsPlusHook = znpcsPlusHook;
        this.fancyNpcsHook = fancyNpcsHook;

        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.file = new File(dataDir, "npcs.json");

        migrateNpcsYaml();
    }

    // -------------------------------------------------------------------------
    // Load / Save
    // -------------------------------------------------------------------------

    /**
     * Reads {@code data/npcs.json} and pushes each mapping into the corresponding
     * hook's internal map.
     */
    public void loadMappings() {
        entityUuidToCrate.clear();
        if (!file.exists()) return;

        JsonObject root = readJson();

        // Generic entity UUID mappings
        JsonObject entities = obj(root, "entities");
        for (Map.Entry<String, JsonElement> e : entities.entrySet()) {
            try {
                UUID uuid = UUID.fromString(e.getKey());
                entityUuidToCrate.put(uuid, e.getValue().getAsString());
            } catch (IllegalArgumentException ignored) {}
        }

        // Citizens — keys are integer NPC IDs
        if (citizensHook != null) {
            JsonObject citizens = obj(root, "citizens");
            for (Map.Entry<String, JsonElement> e : citizens.entrySet()) {
                try {
                    int id = Integer.parseInt(e.getKey());
                    citizensHook.linkNpc(id, e.getValue().getAsString());
                } catch (NumberFormatException ignored) {}
            }
        }

        // ZNPCs — keys are NPC names
        if (znpcsHook != null) {
            JsonObject znpcs = obj(root, "znpcs");
            for (Map.Entry<String, JsonElement> e : znpcs.entrySet()) {
                znpcsHook.link(e.getKey(), e.getValue().getAsString());
            }
        }

        // ZNPCsPlus — keys are NPC IDs/names
        if (znpcsPlusHook != null) {
            JsonObject znpcsPlus = obj(root, "znpcsplus");
            for (Map.Entry<String, JsonElement> e : znpcsPlus.entrySet()) {
                znpcsPlusHook.link(e.getKey(), e.getValue().getAsString());
            }
        }

        // FancyNpcs — keys are NPC names
        if (fancyNpcsHook != null) {
            JsonObject fancyNpcs = obj(root, "fancynpcs");
            for (Map.Entry<String, JsonElement> e : fancyNpcs.entrySet()) {
                fancyNpcsHook.link(e.getKey(), e.getValue().getAsString());
            }
        }
    }

    /**
     * Serialises all current NPC→crate mappings from every hook back to
     * {@code data/npcs.json}.
     */
    public void saveMappings() {
        JsonObject root = new JsonObject();

        // Generic entity UUIDs
        JsonObject entities = new JsonObject();
        for (Map.Entry<UUID, String> e : entityUuidToCrate.entrySet()) {
            entities.addProperty(e.getKey().toString(), e.getValue());
        }
        root.add("entities", entities);

        // Citizens
        JsonObject citizens = new JsonObject();
        if (citizensHook != null) {
            for (Map.Entry<Integer, String> e : citizensHook.getNpcCrateMap().entrySet()) {
                citizens.addProperty(String.valueOf(e.getKey()), e.getValue());
            }
        }
        root.add("citizens", citizens);

        // ZNPCs
        JsonObject znpcs = new JsonObject();
        if (znpcsHook != null) {
            for (Map.Entry<String, String> e : znpcsHook.getNpcNameToCrate().entrySet()) {
                znpcs.addProperty(e.getKey(), e.getValue());
            }
        }
        root.add("znpcs", znpcs);

        // ZNPCsPlus
        JsonObject znpcsPlus = new JsonObject();
        if (znpcsPlusHook != null) {
            for (Map.Entry<String, String> e : znpcsPlusHook.getNpcIdToCrate().entrySet()) {
                znpcsPlus.addProperty(e.getKey(), e.getValue());
            }
        }
        root.add("znpcsplus", znpcsPlus);

        // FancyNpcs
        JsonObject fancyNpcs = new JsonObject();
        if (fancyNpcsHook != null) {
            for (Map.Entry<String, String> e : fancyNpcsHook.getNpcNameToCrate().entrySet()) {
                fancyNpcs.addProperty(e.getKey(), e.getValue());
            }
        }
        root.add("fancynpcs", fancyNpcs);

        writeJson(root);
    }

    // -------------------------------------------------------------------------
    // Link helpers (per plugin type)
    // -------------------------------------------------------------------------

    public void linkCitizens(int id, String crateName) {
        if (citizensHook == null) return;
        citizensHook.linkNpc(id, crateName);
        saveMappings();
    }

    public void linkZNPCs(String name, String crateName) {
        if (znpcsHook == null) return;
        znpcsHook.link(name, crateName);
        saveMappings();
    }

    public void linkZNPCsPlus(String id, String crateName) {
        if (znpcsPlusHook == null) return;
        znpcsPlusHook.link(id, crateName);
        saveMappings();
    }

    public void linkFancyNpcs(String name, String crateName) {
        if (fancyNpcsHook == null) return;
        fancyNpcsHook.link(name, crateName);
        saveMappings();
    }

    // -------------------------------------------------------------------------
    // Generic entity UUID link (used by /vc linknpc)
    // -------------------------------------------------------------------------

    public void linkEntity(UUID uuid, String crateName) {
        entityUuidToCrate.put(uuid, crateName);
        saveMappings();
    }

    public String getCrateForEntity(UUID uuid) {
        return entityUuidToCrate.get(uuid);
    }

    public void unlinkEntity(UUID uuid) {
        entityUuidToCrate.remove(uuid);
        saveMappings();
    }

    // -------------------------------------------------------------------------
    // Unlink helper
    // -------------------------------------------------------------------------

    public void unlinkAll(String entityKey) {
        if (citizensHook != null) {
            try {
                int id = Integer.parseInt(entityKey);
                citizensHook.unlinkNpc(id);
            } catch (NumberFormatException ignored) {}
        }
        if (znpcsHook      != null) znpcsHook.unlink(entityKey);
        if (znpcsPlusHook  != null) znpcsPlusHook.unlink(entityKey);
        if (fancyNpcsHook  != null) fancyNpcsHook.unlink(entityKey);
        saveMappings();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public CitizensHook getCitizensHook()       { return citizensHook; }
    public ZNPCsHook getZnpcsHook()             { return znpcsHook; }
    public ZNPCsPlusHook getZnpcsPlusHook()     { return znpcsPlusHook; }
    public FancyNpcsHook getFancyNpcsHook()     { return fancyNpcsHook; }

    // -------------------------------------------------------------------------
    // JSON I/O helpers
    // -------------------------------------------------------------------------

    private JsonObject readJson() {
        try (Reader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(reader);
            if (el.isJsonObject()) return el.getAsJsonObject();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not read data/npcs.json", e);
        }
        return new JsonObject();
    }

    private void writeJson(JsonObject root) {
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data/npcs.json", e);
        }
    }

    /** Returns the named member as a JsonObject, or an empty one if absent. */
    private static JsonObject obj(JsonObject root, String key) {
        JsonElement el = root.get(key);
        return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : new JsonObject();
    }

    // -------------------------------------------------------------------------
    // Migration: data/npcs.yml → data/npcs.json
    // -------------------------------------------------------------------------

    /**
     * One-time migration: if {@code data/npcs.yml} exists and {@code data/npcs.json}
     * does not, convert the YAML data to JSON and rename the old file to
     * {@code data/npcs.yml.bak}.
     */
    private void migrateNpcsYaml() {
        File yamlFile = new File(file.getParentFile(), "npcs.yml");
        if (!yamlFile.exists() || file.exists()) return;

        plugin.getLogger().info("Migrating data/npcs.yml → data/npcs.json ...");

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(yamlFile);
        JsonObject root = new JsonObject();

        String[] sections = {"entities", "citizens", "znpcs", "znpcsplus", "fancynpcs"};
        for (String section : sections) {
            JsonObject jsonSection = new JsonObject();
            var cfg = yml.getConfigurationSection(section);
            if (cfg != null) {
                for (String key : cfg.getKeys(false)) {
                    String value = cfg.getString(key);
                    if (value != null) jsonSection.addProperty(key, value);
                }
            }
            root.add(section, jsonSection);
        }

        writeJson(root);

        File bak = new File(file.getParentFile(), "npcs.yml.bak");
        if (yamlFile.renameTo(bak)) {
            plugin.getLogger().info("data/npcs.yml renamed to data/npcs.yml.bak (migration done).");
        }
    }
}
