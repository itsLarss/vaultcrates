package de.itslarss.vaultcrates.storage.backend;

import java.util.UUID;

/**
 * Unified storage backend interface for all persistent VaultCrates data.
 * Implementations handle JSON file storage, SQLite, and MySQL.
 */
public interface StorageBackend {

    /** Creates tables / loads file. Called once on startup. */
    void init() throws Exception;

    /** Disconnects / closes file handle. Called on shutdown. */
    void close();

    /** Explicitly flushes pending data (for /vc reload and onDisable). */
    void saveAll();

    // -------------------------------------------------------------------------
    // Virtual keys
    // -------------------------------------------------------------------------

    int  getVirtualKeys(UUID player, String crate);
    void setVirtualKeys(UUID player, String crate, int amount);
    void addVirtualKeys(UUID player, String crate, int amount);

    /**
     * Removes {@code amount} virtual keys from the player's balance.
     *
     * @return {@code false} if the player does not have enough keys
     */
    boolean removeVirtualKeys(UUID player, String crate, int amount);

    // -------------------------------------------------------------------------
    // Pity
    // -------------------------------------------------------------------------

    int  getPity(UUID player, String crate, String rarityId);
    void incrementPity(UUID player, String crate, String rarityId);
    void resetPity(UUID player, String crate, String rarityId);

    // -------------------------------------------------------------------------
    // Reward limits
    // -------------------------------------------------------------------------

    int  getGlobalLimitCount(String crate, String rewardId);
    void incrementGlobalLimit(String crate, String rewardId);
    int  getPlayerLimitCount(UUID player, String crate, String rewardId);
    void incrementPlayerLimit(UUID player, String crate, String rewardId);

    // -------------------------------------------------------------------------
    // Milestone open counts
    // -------------------------------------------------------------------------

    int  getOpenCount(UUID player, String crate);

    /**
     * Increments the open count by 1 and returns the new value.
     */
    int  incrementOpenCount(UUID player, String crate);

    boolean isMilestoneClaimed(UUID player, String crate, String milestoneId);
    void    setMilestoneClaimed(UUID player, String crate, String milestoneId);

    // -------------------------------------------------------------------------
    // Used key UUIDs (anti-dupe)
    // -------------------------------------------------------------------------

    boolean isKeyUsed(UUID keyUuid);
    void    markKeyUsed(UUID keyUuid);

    /**
     * Deletes used-key entries older than {@code expiryDays} days.
     * A value of {@code 0} is treated as "never expire" and this method does nothing.
     */
    void pruneOldKeys(int expiryDays);
}
