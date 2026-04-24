package de.itslarss.vaultcrates.key;

import de.itslarss.vaultcrates.VaultCrates;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Tracks used physical key UUIDs to prevent key duplication exploits.
 *
 * <p>Each physical key item is stamped with a unique UUID at creation time.
 * When the key is consumed, its UUID is recorded here. Any subsequent attempt
 * to use a key with the same UUID is rejected.</p>
 *
 * <p>To prevent unbounded file growth, entries older than {@value #EXPIRY_DAYS}
 * days are automatically pruned on load.</p>
 *
 * <p>YAML structure ({@code data/used_keys.yml}):</p>
 * <pre>
 * keys:
 *   &lt;uuid&gt;: &lt;unix-epoch-seconds-when-used&gt;
 * </pre>
 */
public class UsedKeyStorage {

    /** Keys used more than this many days ago are pruned on next load. */
    private static final int EXPIRY_DAYS = 30;

    private final VaultCrates plugin;
    private final File file;

    /**
     * In-memory store: key UUID → unix epoch seconds when it was first consumed.
     * Loaded from disk on startup, saved on shutdown.
     */
    private final Map<UUID, Long> usedKeys = new HashMap<>();

    public UsedKeyStorage(VaultCrates plugin) {
        this.plugin = plugin;
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.file = new File(dataDir, "used_keys.yml");
    }

    // -------------------------------------------------------------------------
    // Load / Save
    // -------------------------------------------------------------------------

    /**
     * Loads all used-key entries from disk, pruning entries older than
     * {@value #EXPIRY_DAYS} days in the process.
     */
    public void load() {
        usedKeys.clear();
        if (!file.exists()) return;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("keys");
        if (section == null) return;

        long cutoff = System.currentTimeMillis() / 1000L - (long) EXPIRY_DAYS * 86400;

        for (String uuidStr : section.getKeys(false)) {
            long timestamp = section.getLong(uuidStr, 0);
            if (timestamp < cutoff) continue; // prune expired entries
            try {
                usedKeys.put(UUID.fromString(uuidStr), timestamp);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    /** Saves all in-memory used-key entries to disk. */
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : usedKeys.entrySet()) {
            cfg.set("keys." + entry.getKey(), entry.getValue());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data/used_keys.yml", e);
        }
    }

    // -------------------------------------------------------------------------
    // Operations
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the given key UUID has already been consumed.
     *
     * @param keyUuid the key's unique identifier
     * @return {@code true} if this UUID was already used
     */
    public boolean isUsed(UUID keyUuid) {
        return usedKeys.containsKey(keyUuid);
    }

    /**
     * Marks the given key UUID as consumed at the current time.
     *
     * @param keyUuid the key's unique identifier
     */
    public void markUsed(UUID keyUuid) {
        usedKeys.put(keyUuid, System.currentTimeMillis() / 1000L);
    }

    /** Returns the total number of tracked used keys (for diagnostics). */
    public int size() {
        return usedKeys.size();
    }
}
