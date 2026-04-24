package de.itslarss.vaultcrates.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for translating colour codes, including legacy {@code &} codes
 * and hex codes in the {@code &#RRGGBB} format.
 */
public final class ColorUtil {

    /** Regex pattern that matches &#RRGGBB hex colour codes. */
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtil() {
        // Utility class — no instantiation
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Translates a string that may contain both {@code &} colour codes and
     * {@code &#RRGGBB} hex codes into a Minecraft-coloured string.
     *
     * @param text the raw text to translate
     * @return the colourised text
     */
    public static String colorize(String text) {
        if (text == null) return "";
        text = translateHex(text);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Applies {@link #colorize(String)} to every element of a list.
     *
     * @param list the list of raw strings
     * @return a new list with every element colourised
     */
    public static List<String> colorize(List<String> list) {
        if (list == null) return List.of();
        return list.stream()
                .map(ColorUtil::colorize)
                .collect(Collectors.toList());
    }

    /**
     * Strips all colour codes (after first colourising) from a string.
     *
     * @param text the raw text
     * @return plain text without any colour codes
     */
    public static String strip(String text) {
        return ChatColor.stripColor(colorize(text));
    }

    /**
     * Converts a colourised (legacy {@code &} + hex) string to an Adventure
     * {@link Component} using the legacy serialiser.
     *
     * @param text the raw text
     * @return the Adventure component
     */
    public static Component toComponent(String text) {
        if (text == null) return Component.empty();
        String hexTranslated = translateHex(text);
        return LegacyComponentSerializer.legacyAmpersand().deserialize(hexTranslated);
    }

    /**
     * Converts a list of lines to a single Adventure {@link Component} by
     * joining lines with the supplied separator then converting.
     *
     * @param lines     the lines to join
     * @param separator the string placed between each line
     * @return the combined Adventure component
     */
    public static Component toComponent(List<String> lines, String separator) {
        if (lines == null || lines.isEmpty()) return Component.empty();
        String joined = String.join(separator == null ? "\n" : separator, lines);
        return toComponent(joined);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Converts every {@code &#RRGGBB} occurrence in {@code text} into the
     * Bukkit/Spigot legacy hex format {@code §x§R§R§G§G§B§B} (where each letter
     * of the hex code is a separate §-prefixed character).
     *
     * @param text the raw text that may contain &#RRGGBB codes
     * @return the text with hex codes replaced by legacy §x format
     */
    private static String translateHex(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);          // 6-character hex string
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
