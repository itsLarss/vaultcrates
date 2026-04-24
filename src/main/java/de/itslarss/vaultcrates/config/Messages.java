package de.itslarss.vaultcrates.config;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages localised messages. Language is set via {@code Language:} in config.yml.
 *
 * <h3>Built-in languages</h3>
 * <ul>
 *   <li>{@code en} — English (default)</li>
 *   <li>{@code de} — German / Deutsch</li>
 *   <li>{@code fr} — French / Français</li>
 *   <li>{@code es} — Spanish / Español</li>
 * </ul>
 *
 * <h3>Custom languages &amp; locale codes</h3>
 * You can use any locale code (e.g. {@code en_us}, {@code pt_br}, {@code zh_cn}).
 * Just drop a {@code messages_<locale>.yml} file in the plugin data folder.
 *
 * <h3>Fallback chain</h3>
 * <ol>
 *   <li>Exact match — {@code messages_en_us.yml}</li>
 *   <li>Base language — {@code messages_en.yml} (only when a locale code is used)</li>
 *   <li>Generic fallback — {@code messages.yml}</li>
 * </ol>
 */
public class Messages {

    private final VaultCrates plugin;
    private File messagesFile;
    private FileConfiguration messagesConfig;

    public Messages(VaultCrates plugin) {
        this.plugin = plugin;
        load();
    }

    // -------------------------------------------------------------------------
    // Reload
    // -------------------------------------------------------------------------

    public void reload() { load(); }

    private void load() {
        String lang = plugin.getConfigManager()
                .getString("Language", "en").toLowerCase().trim();

        // 1. Exact locale match  (e.g. "en_us" → messages_en_us.yml)
        messagesFile = resolve("messages_" + lang + ".yml");

        // 2. Base-language fallback when a locale suffix is present
        //    (e.g. "en_us" → messages_en.yml)
        if (messagesFile == null && lang.contains("_")) {
            String base = lang.split("_")[0];
            messagesFile = resolve("messages_" + base + ".yml");
        }

        // 3. Generic fallback (messages.yml — always bundled)
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
            if (!messagesFile.exists()) plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        plugin.getLogger().info("Language file loaded: " + messagesFile.getName());
    }

    /**
     * Tries to resolve a messages file by name.
     * <ol>
     *   <li>Returns the {@link File} immediately if it already exists on disk.</li>
     *   <li>Attempts to extract it from the JAR via {@link VaultCrates#saveResource}.</li>
     *   <li>Returns {@code null} if neither source is available.</li>
     * </ol>
     *
     * @param fileName the file name, e.g. {@code "messages_en_us.yml"}
     * @return the resolved file, or {@code null} if unavailable
     */
    private File resolve(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (file.exists()) return file;
        try {
            plugin.saveResource(fileName, false);
            return file; // saveResource succeeded → file now exists
        } catch (Exception ignored) {
            return null; // not bundled, not on disk
        }
    }

    // -------------------------------------------------------------------------
    // Core getters
    // -------------------------------------------------------------------------

    /**
     * Retrieves a single message, replaces {prefix} and any additional placeholder
     * pairs supplied as (key, value, key, value, ...) varargs, then colourises.
     */
    public String get(String key, String... placeholders) {
        String raw = messagesConfig.getString(key, "&c[Missing: " + key + "]");
        if (raw == null) raw = "&c[Missing: " + key + "]";

        String prefix = messagesConfig.getString("prefix", "");
        if (prefix == null) prefix = "";
        raw = raw.replace("{prefix}", prefix);
        raw = applyPlaceholders(raw, placeholders);
        return ColorUtil.colorize(raw);
    }

    /** Retrieves a list of messages with placeholder replacement and colourisation. */
    public List<String> getList(String key, String... placeholders) {
        List<String> lines = messagesConfig.getStringList(key);
        if (lines == null || lines.isEmpty()) return Collections.emptyList();

        String prefix = messagesConfig.getString("prefix", "");
        if (prefix == null) prefix = "";

        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            line = line.replace("{prefix}", prefix);
            line = applyPlaceholders(line, placeholders);
            result.add(ColorUtil.colorize(line));
        }
        return result;
    }

    /** Returns the colourised prefix string. */
    public String prefix() { return get("prefix"); }

    // -------------------------------------------------------------------------
    // Sending helpers
    // -------------------------------------------------------------------------

    public void send(CommandSender sender, String key, String... placeholders) {
        sender.sendMessage(get(key, placeholders));
    }

    public void sendList(CommandSender sender, String key, String... placeholders) {
        for (String line : getList(key, placeholders)) sender.sendMessage(line);
    }

    public void broadcast(String key, String... placeholders) {
        String message = get(key, placeholders);
        for (Player player : Bukkit.getOnlinePlayers()) player.sendMessage(message);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String applyPlaceholders(String text, String... placeholders) {
        if (placeholders == null || placeholders.length == 0) return text;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            if (placeholders[i] != null && placeholders[i + 1] != null) {
                text = text.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
            }
        }
        return text;
    }

    public FileConfiguration getMessagesConfig() { return messagesConfig; }
}
