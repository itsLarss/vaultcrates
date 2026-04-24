package de.itslarss.vaultcrates.crate.reward;

import de.itslarss.vaultcrates.VaultCrates;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * Appends reward drop log entries to {@code data/rewards.log} in the plugin data folder.
 * Logging is enabled/disabled via {@code Settings.Reward_Logging} in the plugin config
 * (defaults to {@code false}).
 *
 * <p>Log line format:</p>
 * <pre>
 * [YYYY-MM-DD HH:mm:ss] Player=NAME Crate=CRATE Reward=ID Rarity=RARITY
 * </pre>
 */
public class RewardLogger {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VaultCrates plugin;
    private final File logFile;
    private final boolean enabled;

    public RewardLogger(VaultCrates plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("Settings.Reward_Logging", false);

        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.logFile = new File(dataDir, "rewards.log");
    }

    // -------------------------------------------------------------------------
    // Logging
    // -------------------------------------------------------------------------

    /**
     * Appends a single reward drop entry to the log file.
     * Does nothing if {@code Settings.Reward_Logging} is {@code false}.
     *
     * @param player    the player who received the reward
     * @param crateName the name of the crate that was opened
     * @param reward    the reward that was granted
     */
    public void log(Player player, String crateName, Reward reward) {
        if (!enabled) return;

        String timestamp = LocalDateTime.now().format(FORMATTER);
        String line = "[" + timestamp + "]"
                + " Player=" + player.getName()
                + " Crate=" + crateName
                + " Reward=" + reward.getId()
                + " Rarity=" + reward.getRarityId();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write to data/rewards.log", e);
        }
    }

    // -------------------------------------------------------------------------
    // Getter
    // -------------------------------------------------------------------------

    /** Returns {@code true} if reward logging is enabled in the config. */
    public boolean isEnabled() {
        return enabled;
    }
}
