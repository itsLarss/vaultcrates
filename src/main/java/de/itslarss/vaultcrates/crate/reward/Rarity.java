package de.itslarss.vaultcrates.crate.reward;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.util.ColorUtil;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A reward rarity tier. Six tiers are built-in; any number of custom tiers can be
 * defined in {@code config.yml} under the {@code Rarities:} section.
 *
 * <pre>
 * Rarities:
 *   goettlich:
 *     Name: "&d&l⚡ Göttlich ⚡"
 *     GlowColor: "170,0,255"   # R,G,B — glow outline on item display entities
 *     Chance: 1.5              # display-only percentage shown in win message {chance}
 * </pre>
 *
 * The rarity is cosmetic — shown in the personal win message ({rarity}, {chance}) and
 * controls the glow colour of displayed item entities during animations.
 * Actual drop probability is still set per-reward via the {@code Chance:} field.
 */
public class Rarity {

    // -------------------------------------------------------------------------
    // Instance
    // -------------------------------------------------------------------------

    private final String id;
    private final String rawDisplayName; // may contain & color codes
    private final Color glowColor;       // nullable — null means no glow override
    private final double displayChance;  // display-only %, 0 = not configured
    private final boolean displayInGUI;  // show rarity in preview GUI lore
    private final int pityAfter;         // 0 = disabled; after N opens without this rarity → guaranteed
    private final boolean broadcast;     // broadcast to all players on drop
    private final String broadcastMessage; // message template, supports {player} {item} {rarity}

    public Rarity(String id, String rawDisplayName, Color glowColor, double displayChance,
                  boolean displayInGUI, int pityAfter, boolean broadcast, String broadcastMessage) {
        this.id = id;
        this.rawDisplayName = rawDisplayName;
        this.glowColor = glowColor;
        this.displayChance = displayChance;
        this.displayInGUI = displayInGUI;
        this.pityAfter = pityAfter;
        this.broadcast = broadcast;
        this.broadcastMessage = broadcastMessage;
    }

    /** ID as defined in config (lower-case). */
    public String getId() { return id; }

    /** Display name with color codes applied (ready for chat). */
    public String getDisplayName() { return ColorUtil.colorize(rawDisplayName); }

    /** Raw display name with original {@code &} color codes, for storage / comparisons. */
    public String getRawDisplayName() { return rawDisplayName; }

    /**
     * Extracts the leading {@code &X} color/format codes from the raw display name.
     * Example: {@code "&6&lLegendär"} → {@code "&6&l"}.
     * Used by the reward footer to colour the rarity name.
     */
    public String getColorPrefix() {
        StringBuilder prefix = new StringBuilder();
        int i = 0;
        while (i + 1 < rawDisplayName.length() && rawDisplayName.charAt(i) == '&') {
            char code = rawDisplayName.charAt(i + 1);
            if (Character.isLetterOrDigit(code)) {
                prefix.append('&').append(code);
                i += 2;
            } else {
                break;
            }
        }
        return prefix.toString();
    }

    /**
     * Returns the display name stripped of its leading color/format codes.
     * Example: {@code "&6&lLegendär"} → {@code "Legendär"}.
     */
    public String getPlainName() {
        return rawDisplayName.substring(getColorPrefix().length());
    }

    /**
     * Glow outline colour for item display entities. {@code null} means no glow.
     * Configurable via {@code GlowColor: "R,G,B"} in the Rarities section.
     */
    public Color getGlowColor() { return glowColor; }

    /**
     * Display-only drop percentage. {@code 0} means not configured; use the
     * reward's own {@code Chance:} value instead.
     */
    public double getDisplayChance() { return displayChance; }

    /** Whether this rarity is shown in the crate preview GUI. */
    public boolean isDisplayInGUI() { return displayInGUI; }

    /**
     * Number of crate opens without this rarity before a pity guarantee triggers.
     * {@code 0} means the pity system is disabled for this rarity.
     */
    public int getPityAfter() { return pityAfter; }

    /** Whether to broadcast a global chat message when a reward of this rarity drops. */
    public boolean isBroadcast() { return broadcast; }

    /**
     * Broadcast message template. Supports {@code {player}}, {@code {item}}, {@code {rarity}}.
     */
    public String getBroadcastMessage() { return broadcastMessage; }

    @Override
    public String toString() { return id; }

    // -------------------------------------------------------------------------
    // Static registry
    // -------------------------------------------------------------------------

    private static final Map<String, Rarity> REGISTRY = new LinkedHashMap<>();

    private static final String DEFAULT_BROADCAST_MSG =
            "&6✦ &e{player} &6won &f{item} &6[{rarity}&6] &6from a crate!";

    /** Fallback returned when no matching rarity is found. */
    public static final Rarity UNKNOWN = new Rarity("unknown", "&7Unknown", null, 0, true, 0, false, DEFAULT_BROADCAST_MSG);

    /**
     * Loads (or reloads) all rarities.
     * Built-in defaults are always registered first; custom entries from
     * {@code config.yml → Rarities} are applied afterwards and can override or extend them.
     */
    public static void loadFromConfig() {
        REGISTRY.clear();

        // ── Built-in tiers (ordered from common → rarest) ─────────────────────
        register(new Rarity("common",       "&7Common",              Color.fromRGB(170, 170, 170), 0, true, 0,   false, DEFAULT_BROADCAST_MSG));
        register(new Rarity("rare",         "&aRare",                Color.fromRGB(85,  255, 85),  0, true, 0,   false, DEFAULT_BROADCAST_MSG));
        register(new Rarity("epic",         "&5Epic",                Color.fromRGB(170, 0,   170), 0, true, 0,   false, DEFAULT_BROADCAST_MSG));
        register(new Rarity("legendary",    "&6&lLegendary",         Color.fromRGB(255, 170, 0),   0, true, 0,   false, DEFAULT_BROADCAST_MSG));
        register(new Rarity("jackpot",      "&c&lJackpot",           Color.fromRGB(255, 85,  85),  0, true, 0,   true,  DEFAULT_BROADCAST_MSG));
        register(new Rarity("mega_jackpot", "&4&l✦ Mega Jackpot ✦", Color.fromRGB(170, 0,   0),   0, true, 0,   true,  DEFAULT_BROADCAST_MSG));

        // ── Custom rarities from config.yml → Rarities: ───────────────────────
        VaultCrates plugin = VaultCrates.getInstance();
        if (plugin == null) return;

        ConfigurationSection section = plugin.getConfigManager()
                .getConfig().getConfigurationSection("Rarities");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String normalised = key.toLowerCase();
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;

            // Inherit built-in defaults when overriding an existing rarity
            Rarity existing = REGISTRY.get(normalised);
            String name            = entry.getString("Name",             existing != null ? existing.rawDisplayName : key);
            double chance          = entry.getDouble("Chance",           existing != null ? existing.displayChance  : 0.0);
            boolean displayInGUI   = entry.getBoolean("DisplayInGUI",   existing != null ? existing.displayInGUI   : true);
            int pityAfter          = entry.getInt("PityAfter",           existing != null ? existing.pityAfter      : 0);
            boolean broadcast      = entry.getBoolean("Broadcast",       existing != null ? existing.broadcast      : false);
            String broadcastMsg    = entry.getString("BroadcastMessage", existing != null ? existing.broadcastMessage : DEFAULT_BROADCAST_MSG);

            // Optional glow color — format "R,G,B"
            Color color = existing != null ? existing.glowColor : null;
            String colorStr = entry.getString("GlowColor", null);
            if (colorStr != null) {
                String[] parts = colorStr.split(",");
                if (parts.length == 3) {
                    try {
                        color = Color.fromRGB(
                                Integer.parseInt(parts[0].trim()),
                                Integer.parseInt(parts[1].trim()),
                                Integer.parseInt(parts[2].trim()));
                    } catch (Exception ignored) {}
                }
            }

            REGISTRY.put(normalised, new Rarity(normalised, name, color, chance, displayInGUI, pityAfter, broadcast, broadcastMsg));
        }
    }

    private static void register(Rarity r) {
        REGISTRY.put(r.id, r);
    }

    /**
     * Returns the {@link Rarity} for the given ID (case-insensitive).
     * Returns a rarity whose display name equals the raw {@code id} string when unknown,
     * so config typos still render something readable rather than crashing.
     */
    public static Rarity get(String id) {
        if (id == null || id.isBlank()) return REGISTRY.getOrDefault("common", UNKNOWN);
        Rarity r = REGISTRY.get(id.toLowerCase());
        return r != null ? r : new Rarity(id.toLowerCase(), id, null, 0, true, 0, false, DEFAULT_BROADCAST_MSG);
    }

    /** Returns an unmodifiable view of the full registry (ordered by insertion = common → rare). */
    public static Map<String, Rarity> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }
}
