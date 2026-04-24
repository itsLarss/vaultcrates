package de.itslarss.vaultcrates.crate;

import de.itslarss.vaultcrates.crate.reward.Reward;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Represents a crate milestone — a special reward granted after a player has
 * opened a crate a certain number of times.
 *
 * <p>Example YAML:</p>
 * <pre>
 * Milestones:
 *   tenth_open:
 *     OpenCount: 10
 *     ResetAfter: false
 *     Reward:
 *       Name: "&6&lMilestone Prize"
 *       Material: GOLD_INGOT
 *       Chance: 100
 *       Commands:
 *         - "give {player_name} diamond 1"
 * </pre>
 */
public class Milestone {

    private final String id;
    private final int openCount;
    private final boolean resetAfter;
    private final Reward reward; // nullable — milestone may grant only a command

    // -------------------------------------------------------------------------
    // Constructor (private — use fromConfig)
    // -------------------------------------------------------------------------

    private Milestone(String id, int openCount, boolean resetAfter, Reward reward) {
        this.id = id;
        this.openCount = openCount;
        this.resetAfter = resetAfter;
        this.reward = reward;
    }

    // -------------------------------------------------------------------------
    // Factory — parse from YAML ConfigurationSection
    // -------------------------------------------------------------------------

    /**
     * Parses a {@link Milestone} from a YAML {@link ConfigurationSection}.
     *
     * @param id  the key of this section (used as the milestone ID)
     * @param sec the configuration section to read from
     * @return the parsed {@link Milestone}
     */
    public static Milestone fromConfig(String id, ConfigurationSection sec) {
        if (sec == null) {
            return new Milestone(id, 10, false, null);
        }

        int openCount = sec.getInt("OpenCount", 10);
        boolean resetAfter = sec.getBoolean("ResetAfter", false);

        // Parse optional Reward subsection
        Reward reward = null;
        ConfigurationSection rewardSection = sec.getConfigurationSection("Reward");
        if (rewardSection != null) {
            reward = Reward.fromConfig(id + "_reward", rewardSection);
        }

        return new Milestone(id, openCount, resetAfter, reward);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** The unique string identifier of this milestone (its YAML key). */
    public String getId() { return id; }

    /** How many crate opens are required to trigger this milestone. */
    public int getOpenCount() { return openCount; }

    /**
     * Whether the player's open counter should reset to zero after this
     * milestone fires, allowing it to repeat.
     */
    public boolean isResetAfter() { return resetAfter; }

    /**
     * The reward to grant when this milestone is reached, or {@code null} if
     * no item/command reward is configured.
     */
    public Reward getReward() { return reward; }

    /** Returns {@code true} if this milestone has a configured reward. */
    public boolean hasReward() { return reward != null; }
}
