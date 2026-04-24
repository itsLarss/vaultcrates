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
 * Tracks global and per-player reward drop counts for reward limit enforcement.
 * Data is stored in {@code data/reward_limits.yml} under the plugin data folder.
 *
 * <p>YAML structure:</p>
 * <pre>
 * global:
 *   &lt;crateName&gt;:
 *     &lt;rewardId&gt;: 42
 * players:
 *   &lt;uuid&gt;:
 *     &lt;crateName&gt;:
 *       &lt;rewardId&gt;: 3
 * </pre>
 */
public class RewardLimitStorage {

    private final VaultCrates plugin;
    private final File file;

    /** Global drop counts: crate → rewardId → count. */
    private final Map<String, Map<String, Integer>> globalCounts = new HashMap<>();

    /** Per-player drop counts: uuid → crate → rewardId → count. */
    private final Map<UUID, Map<String, Map<String, Integer>>> playerCounts = new HashMap<>();

    public RewardLimitStorage(VaultCrates plugin) {
        this.plugin = plugin;
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.file = new File(dataDir, "reward_limits.yml");
    }

    // -------------------------------------------------------------------------
    // Load / Save
    // -------------------------------------------------------------------------

    /** Loads all reward limit counts from {@code data/reward_limits.yml}. */
    public void load() {
        globalCounts.clear();
        playerCounts.clear();
        if (!file.exists()) return;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Load global counts
        ConfigurationSection globalSection = cfg.getConfigurationSection("global");
        if (globalSection != null) {
            for (String crate : globalSection.getKeys(false)) {
                ConfigurationSection crateSection = globalSection.getConfigurationSection(crate);
                if (crateSection == null) continue;
                Map<String, Integer> rewardMap = new HashMap<>();
                for (String rewardId : crateSection.getKeys(false)) {
                    int count = crateSection.getInt(rewardId, 0);
                    if (count > 0) rewardMap.put(rewardId, count);
                }
                if (!rewardMap.isEmpty()) globalCounts.put(crate, rewardMap);
            }
        }

        // Load per-player counts
        ConfigurationSection playersSection = cfg.getConfigurationSection("players");
        if (playersSection != null) {
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
                    Map<String, Integer> rewardMap = new HashMap<>();
                    for (String rewardId : crateSection.getKeys(false)) {
                        int count = crateSection.getInt(rewardId, 0);
                        if (count > 0) rewardMap.put(rewardId, count);
                    }
                    if (!rewardMap.isEmpty()) crateMap.put(crate, rewardMap);
                }
                if (!crateMap.isEmpty()) playerCounts.put(uuid, crateMap);
            }
        }
    }

    /** Saves all reward limit counts to {@code data/reward_limits.yml}. */
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();

        // Save global counts
        for (Map.Entry<String, Map<String, Integer>> crateEntry : globalCounts.entrySet()) {
            String basePath = "global." + crateEntry.getKey();
            for (Map.Entry<String, Integer> rewardEntry : crateEntry.getValue().entrySet()) {
                cfg.set(basePath + "." + rewardEntry.getKey(), rewardEntry.getValue());
            }
        }

        // Save per-player counts
        for (Map.Entry<UUID, Map<String, Map<String, Integer>>> playerEntry : playerCounts.entrySet()) {
            String playerPath = "players." + playerEntry.getKey();
            for (Map.Entry<String, Map<String, Integer>> crateEntry : playerEntry.getValue().entrySet()) {
                String cratePath = playerPath + "." + crateEntry.getKey();
                for (Map.Entry<String, Integer> rewardEntry : crateEntry.getValue().entrySet()) {
                    cfg.set(cratePath + "." + rewardEntry.getKey(), rewardEntry.getValue());
                }
            }
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data/reward_limits.yml", e);
        }
    }

    // -------------------------------------------------------------------------
    // Global count operations
    // -------------------------------------------------------------------------

    /**
     * Returns the global drop count for a specific reward in a crate.
     *
     * @param crate    the crate name
     * @param rewardId the reward ID
     * @return the current global count, or 0 if none recorded
     */
    public int getGlobalCount(String crate, String rewardId) {
        Map<String, Integer> rewardMap = globalCounts.get(crate);
        if (rewardMap == null) return 0;
        return rewardMap.getOrDefault(rewardId, 0);
    }

    /**
     * Increments the global drop count by 1 for a reward in a crate.
     *
     * @param crate    the crate name
     * @param rewardId the reward ID
     */
    public void incrementGlobal(String crate, String rewardId) {
        int current = getGlobalCount(crate, rewardId);
        globalCounts.computeIfAbsent(crate, k -> new HashMap<>()).put(rewardId, current + 1);
    }

    // -------------------------------------------------------------------------
    // Per-player count operations
    // -------------------------------------------------------------------------

    /**
     * Returns the per-player drop count for a specific reward in a crate.
     *
     * @param playerId the player UUID
     * @param crate    the crate name
     * @param rewardId the reward ID
     * @return the current player count, or 0 if none recorded
     */
    public int getPlayerCount(UUID playerId, String crate, String rewardId) {
        Map<String, Map<String, Integer>> crateMap = playerCounts.get(playerId);
        if (crateMap == null) return 0;
        Map<String, Integer> rewardMap = crateMap.get(crate);
        if (rewardMap == null) return 0;
        return rewardMap.getOrDefault(rewardId, 0);
    }

    /**
     * Increments the per-player drop count by 1 for a reward in a crate.
     *
     * @param playerId the player UUID
     * @param crate    the crate name
     * @param rewardId the reward ID
     */
    public void incrementPlayer(UUID playerId, String crate, String rewardId) {
        int current = getPlayerCount(playerId, crate, rewardId);
        playerCounts.computeIfAbsent(playerId, k -> new HashMap<>())
                    .computeIfAbsent(crate, k -> new HashMap<>())
                    .put(rewardId, current + 1);
    }
}
