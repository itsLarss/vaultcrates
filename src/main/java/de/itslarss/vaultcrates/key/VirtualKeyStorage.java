package de.itslarss.vaultcrates.key;

import de.itslarss.vaultcrates.VaultCrates;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages persistent storage of virtual (per-player, per-crate) key counts.
 * Data is stored in {@code virtual_keys.yml} under the plugin data folder.
 *
 * <p>YAML structure:</p>
 * <pre>
 * players:
 *   UUID:
 *     CrateName: 5
 *     AnotherCrate: 2
 * </pre>
 */
public class VirtualKeyStorage {

    private final VaultCrates plugin;
    private final File file;

    /** In-memory cache: UUID → (lowercaseCrateName → count). */
    private final Map<UUID, Map<String, Integer>> cache = new HashMap<>();

    public VirtualKeyStorage(VaultCrates plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "virtual_keys.yml");
    }

    // -------------------------------------------------------------------------
    // Load / Save
    // -------------------------------------------------------------------------

    /** Loads all virtual key data from disk into the in-memory cache. */
    public void loadAll() {
        cache.clear();
        if (!file.exists()) return;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (!cfg.isConfigurationSection("players")) return;

        for (String uuidStr : cfg.getConfigurationSection("players").getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); } catch (IllegalArgumentException e) { continue; }

            Map<String, Integer> keys = new HashMap<>();
            if (cfg.isConfigurationSection("players." + uuidStr)) {
                for (String crate : cfg.getConfigurationSection("players." + uuidStr).getKeys(false)) {
                    int amount = cfg.getInt("players." + uuidStr + "." + crate, 0);
                    if (amount > 0) keys.put(crate.toLowerCase(), amount);
                }
            }
            if (!keys.isEmpty()) cache.put(uuid, keys);
        }
    }

    /** Saves all in-memory cache data to disk. */
    public void saveAll() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Integer>> player : cache.entrySet()) {
            String path = "players." + player.getKey();
            for (Map.Entry<String, Integer> entry : player.getValue().entrySet()) {
                cfg.set(path + "." + entry.getKey(), entry.getValue());
            }
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save virtual_keys.yml", e);
        }
    }

    // -------------------------------------------------------------------------
    // Key operations
    // -------------------------------------------------------------------------

    /** Returns the number of virtual keys a player has for a crate (0 if none). */
    public int getKeys(UUID playerId, String crateName) {
        Map<String, Integer> playerKeys = cache.get(playerId);
        if (playerKeys == null) return 0;
        return playerKeys.getOrDefault(crateName.toLowerCase(), 0);
    }

    /**
     * Sets the virtual key count for a player and crate.
     * If amount <= 0, the entry is removed.
     */
    public void setKeys(UUID playerId, String crateName, int amount) {
        String lower = crateName.toLowerCase();
        if (amount <= 0) {
            Map<String, Integer> keys = cache.get(playerId);
            if (keys != null) {
                keys.remove(lower);
                if (keys.isEmpty()) cache.remove(playerId);
            }
        } else {
            cache.computeIfAbsent(playerId, k -> new HashMap<>()).put(lower, amount);
        }
    }

    /** Adds keys to a player's balance for a crate. */
    public void addKeys(UUID playerId, String crateName, int amount) {
        int current = getKeys(playerId, crateName);
        setKeys(playerId, crateName, current + amount);
    }

    /**
     * Removes keys from a player's balance.
     *
     * @return {@code false} if the player does not have enough keys
     */
    public boolean removeKeys(UUID playerId, String crateName, int amount) {
        int current = getKeys(playerId, crateName);
        if (current < amount) return false;
        setKeys(playerId, crateName, current - amount);
        return true;
    }

    /**
     * Returns all crate → key-count pairs for a player.
     */
    public Map<String, Integer> getAllKeys(UUID playerId) {
        Map<String, Integer> keys = cache.get(playerId);
        return keys == null ? Collections.emptyMap() : Collections.unmodifiableMap(keys);
    }
}
