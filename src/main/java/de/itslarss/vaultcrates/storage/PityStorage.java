package de.itslarss.vaultcrates.storage;

import de.itslarss.vaultcrates.VaultCrates;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Tracks per-player, per-crate, per-rarityId opening counts for the pity system.
 * Data is stored in {@code data/pity.yml} under the plugin data folder.
 *
 * <p>In-memory structure: {@code UUID → crate → rarityId → count}</p>
 *
 * <p>YAML structure:</p>
 * <pre>
 * players:
 *   &lt;uuid&gt;:
 *     &lt;crateName&gt;:
 *       &lt;rarityId&gt;: 7
 * </pre>
 */
public class PityStorage {

    private final VaultCrates plugin;
    private final File file;

    /** In-memory cache: UUID → (crate → (rarityId → count)). */
    private final Map<UUID, Map<String, Map<String, Integer>>> data = new HashMap<>();

    public PityStorage(VaultCrates plugin) {
        this.plugin = plugin;
        // Ensure data/ directory exists
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.file = new File(dataDir, "pity.yml");
    }

    // -------------------------------------------------------------------------
    // Load / Save
    // -------------------------------------------------------------------------

    /** Loads all pity counts from {@code data/pity.yml} into the in-memory cache. */
    public void load() {
        data.clear();
        if (!file.exists()) return;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection playersSection = cfg.getConfigurationSection("players");
        if (playersSection == null) return;

        for (String uuidStr : playersSection.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }

            ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidStr);
            if (playerSection == null) continue;

            Map<String, Map<String, Integer>> crateMap = new HashMap<>();
            for (String crate : playerSection.getKeys(false)) {
                ConfigurationSection crateSection = playerSection.getConfigurationSection(crate);
                if (crateSection == null) continue;

                Map<String, Integer> rarityMap = new HashMap<>();
                for (String rarityId : crateSection.getKeys(false)) {
                    int count = crateSection.getInt(rarityId, 0);
                    if (count > 0) rarityMap.put(rarityId, count);
                }
                if (!rarityMap.isEmpty()) crateMap.put(crate, rarityMap);
            }
            if (!crateMap.isEmpty()) data.put(uuid, crateMap);
        }
    }

    /** Saves all in-memory pity counts to {@code data/pity.yml}. */
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Map<String, Integer>>> playerEntry : data.entrySet()) {
            String uuidPath = "players." + playerEntry.getKey();
            for (Map.Entry<String, Map<String, Integer>> crateEntry : playerEntry.getValue().entrySet()) {
                String cratePath = uuidPath + "." + crateEntry.getKey();
                for (Map.Entry<String, Integer> rarityEntry : crateEntry.getValue().entrySet()) {
                    cfg.set(cratePath + "." + rarityEntry.getKey(), rarityEntry.getValue());
                }
            }
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data/pity.yml", e);
        }
    }

    // -------------------------------------------------------------------------
    // Pity operations
    // -------------------------------------------------------------------------

    /**
     * Returns the current pity count for a player, crate, and rarity.
     *
     * @param playerId the player UUID
     * @param crate    the crate name
     * @param rarityId the rarity identifier
     * @return the current count, or 0 if no entry exists
     */
    public int getCount(UUID playerId, String crate, String rarityId) {
        Map<String, Map<String, Integer>> crateMap = data.get(playerId);
        if (crateMap == null) return 0;
        Map<String, Integer> rarityMap = crateMap.get(crate);
        if (rarityMap == null) return 0;
        return rarityMap.getOrDefault(rarityId, 0);
    }

    /**
     * Increments the pity count by 1 for a player, crate, and rarity.
     *
     * @param playerId the player UUID
     * @param crate    the crate name
     * @param rarityId the rarity identifier
     */
    public void increment(UUID playerId, String crate, String rarityId) {
        int current = getCount(playerId, crate, rarityId);
        data.computeIfAbsent(playerId, k -> new HashMap<>())
            .computeIfAbsent(crate, k -> new HashMap<>())
            .put(rarityId, current + 1);
    }

    /**
     * Resets the pity count to 0 for a player, crate, and rarity.
     * Removes the entry from the map to keep the YAML tidy.
     *
     * @param playerId the player UUID
     * @param crate    the crate name
     * @param rarityId the rarity identifier
     */
    public void reset(UUID playerId, String crate, String rarityId) {
        Map<String, Map<String, Integer>> crateMap = data.get(playerId);
        if (crateMap == null) return;
        Map<String, Integer> rarityMap = crateMap.get(crate);
        if (rarityMap == null) return;
        rarityMap.remove(rarityId);
        if (rarityMap.isEmpty()) crateMap.remove(crate);
        if (crateMap.isEmpty()) data.remove(playerId);
    }
}
