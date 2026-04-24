package de.itslarss.vaultcrates.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.itslarss.vaultcrates.VaultCrates;
import org.bukkit.Bukkit;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Checks Modrinth for a newer version of VaultCrates asynchronously.
 *
 * <ul>
 *   <li>Initial check runs ~5 ticks after startup (off the main thread).</li>
 *   <li>A periodic re-check fires every 6 hours.</li>
 *   <li>Can be disabled via {@code Settings.Update_Checker: false} in config.yml.</li>
 * </ul>
 *
 * When an update is found, the result is logged to the console and stored so that
 * {@link de.itslarss.vaultcrates.listener.PlayerListener} can notify admins on join.
 */
public class UpdateChecker {

    private static final String MODRINTH_API =
            "https://api.modrinth.com/v2/project/vaultcrates/version?loaders=[\"paper\"]";
    private static final String MODRINTH_URL =
            "https://modrinth.com/plugin/vaultcrates";

    /** Re-check every 6 hours (20 ticks/s × 3600 s/h × 6 h). */
    private static final long PERIOD_TICKS = 20L * 3600 * 6;

    private final VaultCrates plugin;
    private final String currentVersion;

    /** Latest remote version string, or {@code null} if not yet fetched. */
    private volatile String latestVersion = null;

    /** {@code true} once we confirm a newer version exists. */
    private volatile boolean updateAvailable = false;

    public UpdateChecker(VaultCrates plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Schedules the first check (5-tick delay) and a periodic re-check every 6 h.
     * Both run fully off the main thread.
     */
    public void start() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::check, 5L, PERIOD_TICKS);
    }

    // -------------------------------------------------------------------------
    // Core check logic (runs async)
    // -------------------------------------------------------------------------

    private void check() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(MODRINTH_API).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setRequestProperty("User-Agent",
                    "VaultCrates/" + currentVersion + " (modrinth.com/plugin/vaultcrates)");

            if (conn.getResponseCode() != 200) return;

            try (InputStreamReader reader = new InputStreamReader(
                    conn.getInputStream(), StandardCharsets.UTF_8)) {

                JsonElement el = JsonParser.parseReader(reader);
                if (!el.isJsonArray()) return;
                JsonArray arr = el.getAsJsonArray();
                if (arr.isEmpty()) return;

                String remote = arr.get(0).getAsJsonObject()
                        .get("version_number").getAsString();
                latestVersion   = remote;
                updateAvailable = isNewer(remote, currentVersion);

                if (updateAvailable) {
                    plugin.getLogger().info("╔═══════════════════════════════════════╗");
                    plugin.getLogger().info("║   VaultCrates update available!       ║");
                    plugin.getLogger().info("║   Current : " + pad(currentVersion, 28) + "║");
                    plugin.getLogger().info("║   Latest  : " + pad(remote, 28)          + "║");
                    plugin.getLogger().info("║   " + pad(MODRINTH_URL, 38)              + "║");
                    plugin.getLogger().info("╚═══════════════════════════════════════╝");
                }
            }
        } catch (Exception e) {
            // Network issues are not critical — log at FINE so they don't spam the console
            plugin.getLogger().log(Level.FINE, "Update check failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Semver comparison
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code remote} is a strictly newer semantic version
     * than {@code current}.  Falls back to a plain string inequality check for
     * non-standard version strings (e.g. {@code "1.0.0-beta"}).
     */
    static boolean isNewer(String remote, String current) {
        try {
            int[] r = semver(remote);
            int[] c = semver(current);
            int len = Math.max(r.length, c.length);
            for (int i = 0; i < len; i++) {
                int rv = i < r.length ? r[i] : 0;
                int cv = i < c.length ? c[i] : 0;
                if (rv > cv) return true;
                if (rv < cv) return false;
            }
            return false;
        } catch (Exception ignored) {
            return !remote.equals(current);
        }
    }

    private static int[] semver(String v) {
        String[] parts = v.split("[.\\-]");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String digits = parts[i].replaceAll("[^0-9]", "");
            nums[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        }
        return nums;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** {@code true} if a newer version was found on Modrinth. */
    public boolean isUpdateAvailable() { return updateAvailable; }

    /** Latest version string returned by Modrinth, or {@code null} if not yet checked. */
    public String getLatestVersion()   { return latestVersion; }

    /** Direct Modrinth project URL for the update message. */
    public String getDownloadUrl()     { return MODRINTH_URL; }

    /** The version this server is currently running. */
    public String getCurrentVersion()  { return currentVersion; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String pad(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}
