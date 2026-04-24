package de.itslarss.vaultcrates.crate.reward;

/**
 * Defines what action(s) should be taken when a reward is given.
 */
public enum RewardType {

    /** Give only the physical item to the player. */
    ITEM,

    /** Execute only the configured commands. */
    COMMAND,

    /** Give the item AND execute commands. */
    BOTH;

    /**
     * Determines the reward type based on the crate configuration flags.
     *
     * @param giveItem    whether the configured item should be given
     * @param hasCommands whether commands are present
     * @return the appropriate {@link RewardType}
     */
    public static RewardType fromConfig(boolean giveItem, boolean hasCommands) {
        if (giveItem && hasCommands) return BOTH;
        if (giveItem) return ITEM;
        return COMMAND;
    }
}
