package de.itslarss.vaultcrates.storage;

import de.itslarss.vaultcrates.VaultCrates;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Tracks per-player, per-crate open counts and which milestones have been claimed.
 * Data is stored in {@code data/milestones.yml} under the plugin data folder.
 *
 * <p>YAML structure per player per crate:</p>
 * <pre>
 * players:
 *   &lt;uuid&gt;:
 *     &lt;crateName&gt;:
 *       opens: 15
 *       claimed:
 *         - tenth_open
 * </pre>
 */
public class MilestoneStorage {

    private final VaultCrates plugin;
    private final File file;

    /** Open counts: UUID → (crate → total opens). */
    private final Map<UUID, Map<String, Integer>> openCounts = new HashMap<>();

    /** Claimed milestones: UUID → (crate → set of claimed milestone IDs). */
    private final Map<UUID, Map<String, Set<String>>> claimedMilestones = new HashMap<>();

    public MilestoneStorage(VaultCrates plugin) {
        this.plugin = plugin;
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.file = new File(dataDir, "milestones.yml");
    }

    // -------------------------------------------------------------------------
    // Load / Save
    // -------------------------------------------------------------------------

    /** Loads all milestone data from {@code data/milestones.yml}. */
    public void load() {
        openCounts.clear();
        claimedMilestones.clear();
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

            Map<String, Integer> playerOpens = new HashMap<>();
            Map<String, Set<String>> playerClaimed = new HashMap<>();

            for (String crate : playerSection.getKeys(false)) {
                ConfigurationSection crateSection = playerSection.getConfigurationSection(crate);
                if (crateSection == null) continue;

                int opens = crateSection.getInt("opens", 0);
                if (opens > 0) playerOpens.put(crate, opens);

                List<String> claimedList = crateSection.getStringList("claimed");
                if (!claimedList.isEmpty()) {
                    playerClaimed.put(crate, new HashSet<>(claimedList));
                }
            }

            if (!playerOpens.isEmpty()) openCounts.put(uuid, playerOpens);
            if (!playerClaimed.isEmpty()) claimedMilestones.put(uuid, playerClaimed);
        }
    }

    /** Saves all milestone data to {@code data/milestones.yml}. */
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();

        // Collect all UUIDs that appear in either map
        Set<UUID> allUUIDs = new HashSet<>();
        allUUIDs.addAll(openCounts.keySet());
        allUUIDs.addAll(claimedMilestones.keySet());

        for (UUID uuid : allUUIDs) {
            String playerPath = "players." + uuid;

            // Collect all crates for this player
            Set<String> allCrates = new HashSet<>();
            Map<String, Integer> playerOpens = openCounts.get(uuid);
            Map<String, Set<String>> playerClaimed = claimedMilestones.get(uuid);
            if (playerOpens != null) allCrates.addAll(playerOpens.keySet());
            if (playerClaimed != null) allCrates.addAll(playerClaimed.keySet());

            for (String crate : allCrates) {
                String cratePath = playerPath + "." + crate;
                int opens = (playerOpens != null) ? playerOpens.getOrDefault(crate, 0) : 0;
                cfg.set(cratePath + ".opens", opens);

                Set<String> claimed = (playerClaimed != null) ? playerClaimed.get(crate) : null;
                if (claimed != null && !claimed.isEmpty()) {
                    cfg.set(cratePath + ".claimed", new ArrayList<>(claimed));
                } else {
                    cfg.set(cratePath + ".claimed", Collections.emptyList());
                }
            }
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data/milestones.yml", e);
        }
    }

    // -------------------------------------------------------------------------
    // Open count operations
    // -------------------------------------------------------------------------

    /**
     * Returns the total number of times a player has opened a given crate.
     *
     * @param playerId the player UUID
     * @param crate    the crate name
     * @return the open count, or 0 if none recorded
     */
    public int getOpenCount(UUID playerId, String crate) {
        Map<String, Integer> playerOpens = openCounts.get(playerId);
        if (playerOpens == null) return 0;
        return playerOpens.getOrDefault(crate, 0);
    }

    /**
     * Increments the player's open count for the given crate by 1 and returns
     * the new total.
     *
     * @param playerId the player UUID
     * @param crate    the crate name
     * @return the new open count after incrementing
     */
    public int incrementAndGet(UUID playerId, String crate) {
        int newCount = getOpenCount(playerId, crate) + 1;
        openCounts.computeIfAbsent(playerId, k -> new HashMap<>()).put(crate, newCount);
        return newCount;
    }

    // -------------------------------------------------------------------------
    // Claimed milestone operations
    // -------------------------------------------------------------------------

    /**
     * Returns whether a specific milestone has already been claimed by the player
     * for a given crate.
     *
     * @param playerId    the player UUID
     * @param crate       the crate name
     * @param milestoneId the milestone ID
     * @return {@code true} if the milestone has been claimed
     */
    public boolean isClaimed(UUID playerId, String crate, String milestoneId) {
        Map<String, Set<String>> playerClaimed = claimedMilestones.get(playerId);
        if (playerClaimed == null) return false;
        Set<String> claimed = playerClaimed.get(crate);
        if (claimed == null) return false;
        return claimed.contains(milestoneId);
    }

    /**
     * Marks a milestone as claimed for the player and crate.
     *
     * @param playerId    the player UUID
     * @param crate       the crate name
     * @param milestoneId the milestone ID to mark as claimed
     */
    public void setClaimed(UUID playerId, String crate, String milestoneId) {
        claimedMilestones.computeIfAbsent(playerId, k -> new HashMap<>())
                         .computeIfAbsent(crate, k -> new HashSet<>())
                         .add(milestoneId);
    }
}
