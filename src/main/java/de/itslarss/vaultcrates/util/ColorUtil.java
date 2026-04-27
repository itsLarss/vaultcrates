package de.itslarss.vaultcrates.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for translating colour codes and building Adventure Components.
 *
 * <h3>Supported formats</h3>
 * <ul>
 *   <li><b>Legacy {@code &} codes</b> — {@code &a}, {@code &l}, etc.</li>
 *   <li><b>Hex colour</b> — {@code &#RRGGBB} (converted to legacy §x format)</li>
 *   <li><b>MiniMessage</b> — {@code <gradient:#ff0000:#0000ff>Text</gradient>},
 *       {@code <rainbow>Text</rainbow>}, {@code <#ff0000>Text</#ff0000>}, etc.
 *       Detected automatically when a string contains MiniMessage-specific tags.</li>
 *   <li><b>ItemsAdder font icons</b> — {@code :icon_name:} placeholders are replaced
 *       with the actual Unicode glyphs from the resource pack before any other
 *       processing, if ItemsAdder is loaded.</li>
 * </ul>
 *
 * <h3>Usage in configs</h3>
 * <pre>{@code
 * Name: "&6&lGold Item"                                      # legacy
 * Name: "&#FF8800Orange Item"                                # hex
 * Name: "<gradient:#ff0000:#0000ff>Rainbow</gradient>"       # MiniMessage gradient
 * Name: ":some_icon: &6With IA Icon"                        # IA font icon + legacy
 * }</pre>
 */
public final class ColorUtil {

    // -------------------------------------------------------------------------
    // Patterns
    // -------------------------------------------------------------------------

    /** Matches {@code &#RRGGBB} hex colour codes. */
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    /**
     * Matches the start of a MiniMessage-exclusive tag that would never appear
     * in legacy formatted text.  Detecting any of these means we should use the
     * MiniMessage parser instead of the legacy one.
     */
    private static final Pattern MINI_MESSAGE_PATTERN = Pattern.compile(
            "<(?:gradient|rainbow|transition|#[0-9a-fA-F]{3,6}|color:#|hover|click|insertion|font:)[^>]*>"
    );

    // -------------------------------------------------------------------------
    // IA font-icon preprocessor (injected from VaultCrates once IA is loaded)
    // -------------------------------------------------------------------------

    /**
     * Optional text preprocessor that replaces {@code :icon_name:} placeholders
     * with the corresponding Unicode characters from an ItemsAdder resource pack.
     *
     * <p>Set via {@link #setTextPreprocessor(UnaryOperator)} after ItemsAdder loads.
     * Defaults to a no-op identity function.</p>
     */
    private static UnaryOperator<String> textPreprocessor = s -> s;

    /**
     * Registers the IA font-icon text preprocessor.
     * Should be called from {@code VaultCrates.onEnable()} once the
     * {@code ItemsAdderHook} has confirmed ItemsAdder is available.
     *
     * @param processor a function that replaces {@code :icon:} placeholders;
     *                  typically {@code itemsAdderHook::processText}
     */
    public static void setTextPreprocessor(UnaryOperator<String> processor) {
        textPreprocessor = processor == null ? s -> s : processor;
    }

    // -------------------------------------------------------------------------
    // Private constructor
    // -------------------------------------------------------------------------

    private ColorUtil() {}

    // -------------------------------------------------------------------------
    // Public API — string form (for sending chat messages etc.)
    // -------------------------------------------------------------------------

    /**
     * Translates {@code &} colour codes and {@code &#RRGGBB} hex codes to a
     * Minecraft-coloured string.  Also runs the IA font-icon preprocessor first.
     *
     * <p>Note: MiniMessage tags are <em>not</em> stripped here — use
     * {@link #toComponent(String)} when you need a rendered Component.</p>
     *
     * @param text the raw text
     * @return the colourised string
     */
    public static String colorize(String text) {
        if (text == null) return "";
        text = textPreprocessor.apply(text);
        text = translateHex(text);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /** Applies {@link #colorize(String)} to every element of a list. */
    public static List<String> colorize(List<String> list) {
        if (list == null) return List.of();
        return list.stream().map(ColorUtil::colorize).collect(Collectors.toList());
    }

    /** Strips all colour codes from a string. */
    public static String strip(String text) {
        return ChatColor.stripColor(colorize(text));
    }

    // -------------------------------------------------------------------------
    // Public API — Component form (for item names, lore, holograms)
    // -------------------------------------------------------------------------

    /**
     * Converts a raw text string to an Adventure {@link Component} with italic
     * explicitly disabled.
     *
     * <p>Processing pipeline:</p>
     * <ol>
     *   <li>IA font-icon placeholder replacement ({@code :icon:} → Unicode)</li>
     *   <li>If MiniMessage tags are detected → parsed with
     *       {@link MiniMessage#miniMessage()}</li>
     *   <li>Otherwise → {@code &#RRGGBB} hex conversion + legacy {@code &} parsing</li>
     * </ol>
     *
     * @param text the raw text (may contain {@code &} codes, hex codes, MiniMessage
     *             tags, or {@code :icon:} placeholders)
     * @return the rendered Component with italic = false
     */
    public static Component toComponent(String text) {
        if (text == null) return Component.empty();

        // Step 1: IA font icon placeholders
        text = textPreprocessor.apply(text);

        // Step 2: choose parser
        Component component;
        if (MINI_MESSAGE_PATTERN.matcher(text).find()) {
            // MiniMessage path — also handles <bold>, <red> etc.
            component = MiniMessage.miniMessage().deserialize(text);
        } else {
            // Legacy path: convert &#RRGGBB + & codes → § codes, then parse with
            // legacySection which is the native Minecraft format and more reliable
            String colored = ChatColor.translateAlternateColorCodes('&', translateHex(text));
            component = LegacyComponentSerializer.legacySection().deserialize(colored);
        }

        // Always disable italic so items don't show purple-italic text by default
        return component.decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Converts a list of strings to a list of Components.
     * Each line is processed independently via {@link #toComponent(String)}.
     */
    public static List<Component> toComponents(List<String> lines) {
        if (lines == null) return List.of();
        return lines.stream().map(ColorUtil::toComponent).collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Converts every {@code &#RRGGBB} occurrence to the Bukkit legacy hex format
     * {@code §x§R§R§G§G§B§B}.
     */
    private static String translateHex(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
