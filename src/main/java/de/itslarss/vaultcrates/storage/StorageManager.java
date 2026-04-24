package de.itslarss.vaultcrates.storage;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.storage.backend.JsonStorageBackend;
import de.itslarss.vaultcrates.storage.backend.SqlStorageBackend;
import de.itslarss.vaultcrates.storage.backend.StorageBackend;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Central storage manager.
 * Creates the appropriate {@link StorageBackend} based on {@code config.yml → Storage.Type}
 * and exposes adapter objects that keep existing call-sites working without changes.
 */
public class StorageManager {

    private final VaultCrates plugin;

    /** The active storage backend (JSON / SQLite / MySQL). */
    private final StorageBackend backend;

    // Adapter objects — delegate to the backend; kept for backwards-compat with call-sites
    private final PityStorageAdapter        pityStorage;
    private final RewardLimitStorageAdapter rewardLimitStorage;
    private final MilestoneStorageAdapter   milestoneStorage;
    private final VirtualKeyStorageAdapter  virtualKeyStorageAdapter;

    public StorageManager(VaultCrates plugin) {
        this.plugin = plugin;

        // ------------------------------------------------------------------
        // Select and instantiate the backend
        // ------------------------------------------------------------------
        String type = plugin.getConfigManager()
                .getString("Storage.Type", "JSON")
                .toUpperCase()
                .trim();

        StorageBackend chosen;
        switch (type) {
            case "SQLITE" -> {
                String dbFile = plugin.getDataFolder().getAbsolutePath() + "/"
                        + plugin.getConfigManager().getString("Storage.SQLite.File", "data/vaultcrates.db");
                chosen = new SqlStorageBackend(plugin,
                        "org.sqlite.JDBC",
                        "jdbc:sqlite:" + dbFile,
                        null, null);
            }
            case "MYSQL" -> {
                String host = plugin.getConfigManager().getString("Storage.MySQL.Host", "localhost");
                int    port = plugin.getConfigManager().getInt("Storage.MySQL.Port", 3306);
                String db   = plugin.getConfigManager().getString("Storage.MySQL.Database", "vaultcrates");
                String user = plugin.getConfigManager().getString("Storage.MySQL.Username", "root");
                String pass = plugin.getConfigManager().getString("Storage.MySQL.Password", "");
                chosen = new SqlStorageBackend(plugin,
                        "com.mysql.cj.jdbc.Driver",
                        "jdbc:mysql://" + host + ":" + port + "/" + db
                                + "?useSSL=false&autoReconnect=true",
                        user, pass);
            }
            default -> chosen = new JsonStorageBackend(plugin); // JSON is the default
        }

        try {
            chosen.init();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to initialise storage backend (" + type + "). Falling back to JSON.", e);
            chosen = new JsonStorageBackend(plugin);
            try { chosen.init(); } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "JSON fallback init also failed!", ex);
            }
        }

        this.backend = chosen;

        // Prune old key UUIDs according to config
        int expiryDays = plugin.getConfigManager().getInt("Storage.KeyUuidExpiryDays", 365);
        backend.pruneOldKeys(expiryDays);

        // ------------------------------------------------------------------
        // Build adapters
        // ------------------------------------------------------------------
        this.pityStorage             = new PityStorageAdapter();
        this.rewardLimitStorage      = new RewardLimitStorageAdapter();
        this.milestoneStorage        = new MilestoneStorageAdapter();
        this.virtualKeyStorageAdapter = new VirtualKeyStorageAdapter();

        plugin.getLogger().info("Storage backend initialised: " + type);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Called on plugin enable (compat with existing code that calls {@code loadAll()}).
     * For SQL backends this is a no-op; for JSON it is already done in {@code init()}.
     */
    public void loadAll() {
        // Backend already initialised in constructor — nothing extra to do
        plugin.getLogger().info("Storage data loaded.");
    }

    /** Flushes pending data. Called on plugin disable and /vc reload. */
    public void saveAll() {
        backend.saveAll();
    }

    /** Closes the backend. Should be called on plugin disable after saveAll(). */
    public void closeBackend() {
        backend.close();
    }

    // -------------------------------------------------------------------------
    // Direct backend access (used by KeyManager bridge)
    // -------------------------------------------------------------------------

    public StorageBackend getBackend() {
        return backend;
    }

    // -------------------------------------------------------------------------
    // Adapter accessors — backwards-compatible with existing call-sites
    // -------------------------------------------------------------------------

    /** Returns the pity storage adapter (wraps the backend). */
    public PityStorageAdapter getPityStorage() {
        return pityStorage;
    }

    /** Returns the reward-limit storage adapter (wraps the backend). */
    public RewardLimitStorageAdapter getRewardLimitStorage() {
        return rewardLimitStorage;
    }

    /** Returns the milestone storage adapter (wraps the backend). */
    public MilestoneStorageAdapter getMilestoneStorage() {
        return milestoneStorage;
    }

    /**
     * Returns a {@link VirtualKeyStorage}-compatible adapter.
     * Existing code that calls {@code getVirtualKeyStorage()} on StorageManager
     * receives this adapter, which delegates to the active backend.
     */
    public VirtualKeyStorageAdapter getVirtualKeyStorage() {
        return virtualKeyStorageAdapter;
    }

    // =========================================================================
    // Inner adapter classes
    // =========================================================================

    /**
     * Adapter that exposes the same API surface as the original {@link PityStorage}
     * but routes calls through the active {@link StorageBackend}.
     */
    public class PityStorageAdapter {

        /** @see de.itslarss.vaultcrates.storage.PityStorage#getCount */
        public int getCount(UUID playerId, String crate, String rarityId) {
            return backend.getPity(playerId, crate, rarityId);
        }

        /** @see de.itslarss.vaultcrates.storage.PityStorage#increment */
        public void increment(UUID playerId, String crate, String rarityId) {
            backend.incrementPity(playerId, crate, rarityId);
        }

        /** @see de.itslarss.vaultcrates.storage.PityStorage#reset */
        public void reset(UUID playerId, String crate, String rarityId) {
            backend.resetPity(playerId, crate, rarityId);
        }
    }

    /**
     * Adapter that exposes the same API surface as the original {@link RewardLimitStorage}
     * but routes calls through the active {@link StorageBackend}.
     */
    public class RewardLimitStorageAdapter {

        /** @see de.itslarss.vaultcrates.storage.RewardLimitStorage#getGlobalCount */
        public int getGlobalCount(String crate, String rewardId) {
            return backend.getGlobalLimitCount(crate, rewardId);
        }

        /** @see de.itslarss.vaultcrates.storage.RewardLimitStorage#incrementGlobal */
        public void incrementGlobal(String crate, String rewardId) {
            backend.incrementGlobalLimit(crate, rewardId);
        }

        /** @see de.itslarss.vaultcrates.storage.RewardLimitStorage#getPlayerCount */
        public int getPlayerCount(UUID playerId, String crate, String rewardId) {
            return backend.getPlayerLimitCount(playerId, crate, rewardId);
        }

        /** @see de.itslarss.vaultcrates.storage.RewardLimitStorage#incrementPlayer */
        public void incrementPlayer(UUID playerId, String crate, String rewardId) {
            backend.incrementPlayerLimit(playerId, crate, rewardId);
        }
    }

    /**
     * Adapter that exposes the same API surface as the original {@link MilestoneStorage}
     * but routes calls through the active {@link StorageBackend}.
     */
    public class MilestoneStorageAdapter {

        /** @see de.itslarss.vaultcrates.storage.MilestoneStorage#getOpenCount */
        public int getOpenCount(UUID playerId, String crate) {
            return backend.getOpenCount(playerId, crate);
        }

        /** @see de.itslarss.vaultcrates.storage.MilestoneStorage#incrementAndGet */
        public int incrementAndGet(UUID playerId, String crate) {
            return backend.incrementOpenCount(playerId, crate);
        }

        /** @see de.itslarss.vaultcrates.storage.MilestoneStorage#isClaimed */
        public boolean isClaimed(UUID playerId, String crate, String milestoneId) {
            return backend.isMilestoneClaimed(playerId, crate, milestoneId);
        }

        /** @see de.itslarss.vaultcrates.storage.MilestoneStorage#setClaimed */
        public void setClaimed(UUID playerId, String crate, String milestoneId) {
            backend.setMilestoneClaimed(playerId, crate, milestoneId);
        }
    }

    /**
     * Adapter that exposes the same API surface as the original {@link VirtualKeyStorage}
     * but routes calls through the active {@link StorageBackend}.
     *
     * <p>Note: {@link #getAllKeys(UUID)} is not backed by the backend interface
     * because SQL backends do not support bulk-read efficiently here; this method
     * returns an empty map for SQL backends. Only the JSON backend (which keeps
     * everything in memory) fully supports it.</p>
     */
    public class VirtualKeyStorageAdapter {

        public int getKeys(UUID playerId, String crateName) {
            return backend.getVirtualKeys(playerId, crateName);
        }

        public void setKeys(UUID playerId, String crateName, int amount) {
            backend.setVirtualKeys(playerId, crateName, amount);
        }

        public void addKeys(UUID playerId, String crateName, int amount) {
            backend.addVirtualKeys(playerId, crateName, amount);
        }

        public boolean removeKeys(UUID playerId, String crateName, int amount) {
            return backend.removeVirtualKeys(playerId, crateName, amount);
        }

        /**
         * Returns all crate→key-count pairs for a player.
         * Only fully supported by the JSON backend; SQL backends return an empty map.
         */
        public java.util.Map<String, Integer> getAllKeys(UUID playerId) {
            if (backend instanceof JsonStorageBackend jsb) {
                // Access the in-memory player data directly via the same helper
                return jsb.getAllVirtualKeys(playerId);
            }
            return java.util.Collections.emptyMap();
        }
    }
}
