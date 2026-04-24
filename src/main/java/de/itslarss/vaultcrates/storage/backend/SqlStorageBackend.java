package de.itslarss.vaultcrates.storage.backend;

import de.itslarss.vaultcrates.VaultCrates;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQL-based storage backend supporting both SQLite and MySQL.
 *
 * <p>SQLite driver is bundled with Paper: {@code org.sqlite.JDBC}.<br>
 * MySQL driver is shaded into the plugin: {@code com.mysql.cj.jdbc.Driver}
 * (relocated to {@code de.itslarss.vaultcrates.libs.mysql.cj.jdbc.Driver} in
 * the final JAR, but referenced here by the original name — the shade plugin
 * rewrites the string literal at build time because the class is loaded via
 * {@link Class#forName(String)} after relocation).</p>
 */
public class SqlStorageBackend implements StorageBackend {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final VaultCrates plugin;
    private final String driverClass;
    private final String jdbcUrl;
    private final String user;
    private final String password;

    private Connection connection;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public SqlStorageBackend(VaultCrates plugin,
                             String driverClass,
                             String jdbcUrl,
                             String user,
                             String password) {
        this.plugin      = plugin;
        this.driverClass = driverClass;
        this.jdbcUrl     = jdbcUrl;
        this.user        = user;
        this.password    = password;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void init() throws Exception {
        Class.forName(driverClass);
        connection = openConnection();
        createTables();
    }

    @Override
    public void saveAll() {
        // Direct-write backend — nothing to flush
    }

    @Override
    public void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
            connection = null;
        }
    }

    // -------------------------------------------------------------------------
    // Connection handling
    // -------------------------------------------------------------------------

    private Connection openConnection() throws SQLException {
        if (user != null && !user.isEmpty()) {
            return DriverManager.getConnection(jdbcUrl, user, password);
        }
        return DriverManager.getConnection(jdbcUrl);
    }

    /** Returns a live connection, reconnecting if the current one is closed. */
    private Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = openConnection();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not reconnect to database", e);
        }
        return connection;
    }

    // -------------------------------------------------------------------------
    // Table creation
    // -------------------------------------------------------------------------

    private boolean isMySql() {
        return driverClass.contains("mysql");
    }

    private void createTables() throws SQLException {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS vc_virtual_keys ("
            + "  player_uuid VARCHAR(36) NOT NULL,"
            + "  crate_name  VARCHAR(64) NOT NULL,"
            + "  amount      INT NOT NULL DEFAULT 0,"
            + "  PRIMARY KEY (player_uuid, crate_name)"
            + ")",

            "CREATE TABLE IF NOT EXISTS vc_pity ("
            + "  player_uuid VARCHAR(36) NOT NULL,"
            + "  crate_name  VARCHAR(64) NOT NULL,"
            + "  rarity_id   VARCHAR(64) NOT NULL,"
            + "  count       INT NOT NULL DEFAULT 0,"
            + "  PRIMARY KEY (player_uuid, crate_name, rarity_id)"
            + ")",

            "CREATE TABLE IF NOT EXISTS vc_reward_limits_global ("
            + "  crate_name  VARCHAR(64) NOT NULL,"
            + "  reward_id   VARCHAR(64) NOT NULL,"
            + "  count       INT NOT NULL DEFAULT 0,"
            + "  PRIMARY KEY (crate_name, reward_id)"
            + ")",

            "CREATE TABLE IF NOT EXISTS vc_reward_limits_player ("
            + "  player_uuid VARCHAR(36) NOT NULL,"
            + "  crate_name  VARCHAR(64) NOT NULL,"
            + "  reward_id   VARCHAR(64) NOT NULL,"
            + "  count       INT NOT NULL DEFAULT 0,"
            + "  PRIMARY KEY (player_uuid, crate_name, reward_id)"
            + ")",

            "CREATE TABLE IF NOT EXISTS vc_milestones ("
            + "  player_uuid        VARCHAR(36)  NOT NULL,"
            + "  crate_name         VARCHAR(64)  NOT NULL,"
            + "  open_count         INT          NOT NULL DEFAULT 0,"
            + "  claimed_milestones TEXT         NOT NULL DEFAULT '',"
            + "  PRIMARY KEY (player_uuid, crate_name)"
            + ")",

            "CREATE TABLE IF NOT EXISTS vc_used_keys ("
            + "  key_uuid VARCHAR(36) NOT NULL,"
            + "  used_at  BIGINT      NOT NULL,"
            + "  PRIMARY KEY (key_uuid)"
            + ")"
        };

        try (Statement stmt = getConnection().createStatement()) {
            for (String sql : ddl) {
                stmt.execute(sql);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Upsert helper
    // -------------------------------------------------------------------------

    /**
     * Builds the upsert prefix appropriate for the current driver.
     * SQLite uses {@code INSERT OR REPLACE}, MySQL uses {@code INSERT ... ON DUPLICATE KEY UPDATE}.
     *
     * <p>For simple single-value upserts the method builds and executes the full statement.</p>
     */
    private PreparedStatement prepareUpsert(String table,
                                            String[] pkCols,
                                            String valueCol,
                                            Object[] pkVals,
                                            Object value) throws SQLException {
        if (isMySql()) {
            // Build: INSERT INTO t (c1,c2,...,cv) VALUES (?,?,...,?) ON DUPLICATE KEY UPDATE cv=?
            StringBuilder cols = new StringBuilder();
            StringBuilder qs   = new StringBuilder();
            for (String col : pkCols) {
                cols.append(col).append(",");
                qs.append("?,");
            }
            cols.append(valueCol);
            qs.append("?");
            String sql = "INSERT INTO " + table + " (" + cols + ") VALUES (" + qs
                       + ") ON DUPLICATE KEY UPDATE " + valueCol + "=?";
            PreparedStatement ps = getConnection().prepareStatement(sql);
            int i = 1;
            for (Object pv : pkVals) ps.setObject(i++, pv);
            ps.setObject(i++, value);
            ps.setObject(i,   value); // ON DUPLICATE KEY UPDATE value
            return ps;
        } else {
            // SQLite: INSERT OR REPLACE INTO t (c1,c2,...,cv) VALUES (?,?,...,?)
            StringBuilder cols = new StringBuilder();
            StringBuilder qs   = new StringBuilder();
            for (String col : pkCols) {
                cols.append(col).append(",");
                qs.append("?,");
            }
            cols.append(valueCol);
            qs.append("?");
            String sql = "INSERT OR REPLACE INTO " + table + " (" + cols + ") VALUES (" + qs + ")";
            PreparedStatement ps = getConnection().prepareStatement(sql);
            int i = 1;
            for (Object pv : pkVals) ps.setObject(i++, pv);
            ps.setObject(i, value);
            return ps;
        }
    }

    // -------------------------------------------------------------------------
    // Virtual keys
    // -------------------------------------------------------------------------

    @Override
    public int getVirtualKeys(UUID player, String crate) {
        String sql = "SELECT amount FROM vc_virtual_keys WHERE player_uuid=? AND crate_name=?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, crate.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "getVirtualKeys failed", e);
        }
        return 0;
    }

    @Override
    public void setVirtualKeys(UUID player, String crate, int amount) {
        try (PreparedStatement ps = prepareUpsert(
                "vc_virtual_keys",
                new String[]{"player_uuid", "crate_name"},
                "amount",
                new Object[]{player.toString(), crate.toLowerCase()},
                amount)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "setVirtualKeys failed", e);
        }
    }

    @Override
    public void addVirtualKeys(UUID player, String crate, int amount) {
        setVirtualKeys(player, crate, getVirtualKeys(player, crate) + amount);
    }

    @Override
    public boolean removeVirtualKeys(UUID player, String crate, int amount) {
        int current = getVirtualKeys(player, crate);
        if (current < amount) return false;
        setVirtualKeys(player, crate, current - amount);
        return true;
    }

    // -------------------------------------------------------------------------
    // Pity
    // -------------------------------------------------------------------------

    @Override
    public int getPity(UUID player, String crate, String rarityId) {
        String sql = "SELECT count FROM vc_pity WHERE player_uuid=? AND crate_name=? AND rarity_id=?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, crate);
            ps.setString(3, rarityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "getPity failed", e);
        }
        return 0;
    }

    @Override
    public void incrementPity(UUID player, String crate, String rarityId) {
        int newVal = getPity(player, crate, rarityId) + 1;
        try (PreparedStatement ps = prepareUpsert(
                "vc_pity",
                new String[]{"player_uuid", "crate_name", "rarity_id"},
                "count",
                new Object[]{player.toString(), crate, rarityId},
                newVal)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "incrementPity failed", e);
        }
    }

    @Override
    public void resetPity(UUID player, String crate, String rarityId) {
        String sql = "DELETE FROM vc_pity WHERE player_uuid=? AND crate_name=? AND rarity_id=?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, crate);
            ps.setString(3, rarityId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "resetPity failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Reward limits
    // -------------------------------------------------------------------------

    @Override
    public int getGlobalLimitCount(String crate, String rewardId) {
        String sql = "SELECT count FROM vc_reward_limits_global WHERE crate_name=? AND reward_id=?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, crate);
            ps.setString(2, rewardId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "getGlobalLimitCount failed", e);
        }
        return 0;
    }

    @Override
    public void incrementGlobalLimit(String crate, String rewardId) {
        int newVal = getGlobalLimitCount(crate, rewardId) + 1;
        try (PreparedStatement ps = prepareUpsert(
                "vc_reward_limits_global",
                new String[]{"crate_name", "reward_id"},
                "count",
                new Object[]{crate, rewardId},
                newVal)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "incrementGlobalLimit failed", e);
        }
    }

    @Override
    public int getPlayerLimitCount(UUID player, String crate, String rewardId) {
        String sql = "SELECT count FROM vc_reward_limits_player WHERE player_uuid=? AND crate_name=? AND reward_id=?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, crate);
            ps.setString(3, rewardId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "getPlayerLimitCount failed", e);
        }
        return 0;
    }

    @Override
    public void incrementPlayerLimit(UUID player, String crate, String rewardId) {
        int newVal = getPlayerLimitCount(player, crate, rewardId) + 1;
        try (PreparedStatement ps = prepareUpsert(
                "vc_reward_limits_player",
                new String[]{"player_uuid", "crate_name", "reward_id"},
                "count",
                new Object[]{player.toString(), crate, rewardId},
                newVal)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "incrementPlayerLimit failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Milestone open counts
    // -------------------------------------------------------------------------

    @Override
    public int getOpenCount(UUID player, String crate) {
        String sql = "SELECT open_count FROM vc_milestones WHERE player_uuid=? AND crate_name=?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, crate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "getOpenCount failed", e);
        }
        return 0;
    }

    @Override
    public int incrementOpenCount(UUID player, String crate) {
        int newCount = getOpenCount(player, crate) + 1;
        String claimed = getClaimedRaw(player, crate);
        upsertMilestoneRow(player, crate, newCount, claimed);
        return newCount;
    }

    @Override
    public boolean isMilestoneClaimed(UUID player, String crate, String milestoneId) {
        String raw = getClaimedRaw(player, crate);
        if (raw == null || raw.isEmpty()) return false;
        for (String id : raw.split(",")) {
            if (id.equals(milestoneId)) return true;
        }
        return false;
    }

    @Override
    public void setMilestoneClaimed(UUID player, String crate, String milestoneId) {
        if (isMilestoneClaimed(player, crate, milestoneId)) return;
        String existing = getClaimedRaw(player, crate);
        String newClaimed = (existing == null || existing.isEmpty())
                ? milestoneId
                : existing + "," + milestoneId;
        int openCount = getOpenCount(player, crate);
        upsertMilestoneRow(player, crate, openCount, newClaimed);
    }

    private String getClaimedRaw(UUID player, String crate) {
        String sql = "SELECT claimed_milestones FROM vc_milestones WHERE player_uuid=? AND crate_name=?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, crate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "getClaimedRaw failed", e);
        }
        return "";
    }

    private void upsertMilestoneRow(UUID player, String crate, int openCount, String claimed) {
        if (claimed == null) claimed = "";
        if (isMySql()) {
            String sql = "INSERT INTO vc_milestones (player_uuid, crate_name, open_count, claimed_milestones)"
                       + " VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE open_count=?, claimed_milestones=?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, player.toString());
                ps.setString(2, crate);
                ps.setInt(3, openCount);
                ps.setString(4, claimed);
                ps.setInt(5, openCount);
                ps.setString(6, claimed);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "upsertMilestoneRow failed", e);
            }
        } else {
            String sql = "INSERT OR REPLACE INTO vc_milestones (player_uuid, crate_name, open_count, claimed_milestones)"
                       + " VALUES (?,?,?,?)";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, player.toString());
                ps.setString(2, crate);
                ps.setInt(3, openCount);
                ps.setString(4, claimed);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "upsertMilestoneRow failed", e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Used key UUIDs (anti-dupe)
    // -------------------------------------------------------------------------

    @Override
    public boolean isKeyUsed(UUID keyUuid) {
        String sql = "SELECT 1 FROM vc_used_keys WHERE key_uuid=?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, keyUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "isKeyUsed failed", e);
        }
        return false;
    }

    @Override
    public void markKeyUsed(UUID keyUuid) {
        try (PreparedStatement ps = prepareUpsert(
                "vc_used_keys",
                new String[]{"key_uuid"},
                "used_at",
                new Object[]{keyUuid.toString()},
                System.currentTimeMillis() / 1000L)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "markKeyUsed failed", e);
        }
    }

    @Override
    public void pruneOldKeys(int expiryDays) {
        if (expiryDays <= 0) return;
        long cutoff = System.currentTimeMillis() / 1000L - (long) expiryDays * 86400L;
        String sql = "DELETE FROM vc_used_keys WHERE used_at < ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "pruneOldKeys failed", e);
        }
    }
}
