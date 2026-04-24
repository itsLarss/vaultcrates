package de.itslarss.vaultcrates.crate.reward;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents one extra item given alongside a reward (a "bundled" bonus item).
 * Parsed from a YAML ConfigurationSection.
 *
 * <p>Example YAML:</p>
 * <pre>
 * BundledItems:
 *   bonus1:
 *     Material: DIAMOND
 *     Amount: 3
 *     Name: "&b&lBonus Diamond"
 *     Lore:
 *       - "&7A little extra bonus!"
 * </pre>
 */
public class BundledItem {

    private final Material material;
    private final int amount;
    private final String name;
    private final List<String> lore;

    // -------------------------------------------------------------------------
    // Constructor (private — use fromConfig)
    // -------------------------------------------------------------------------

    private BundledItem(Material material, int amount, String name, List<String> lore) {
        this.material = material;
        this.amount = amount;
        this.name = name;
        this.lore = lore;
    }

    // -------------------------------------------------------------------------
    // Factory — parse from YAML ConfigurationSection
    // -------------------------------------------------------------------------

    /**
     * Parses a {@link BundledItem} from a YAML {@link ConfigurationSection}.
     *
     * @param sec the configuration section to read from
     * @return the parsed {@link BundledItem}, or a STONE fallback if the section is null
     */
    public static BundledItem fromConfig(ConfigurationSection sec) {
        if (sec == null) {
            return new BundledItem(Material.STONE, 1, "Unknown", Collections.emptyList());
        }

        // Material
        Material material = Material.STONE;
        String matStr = sec.getString("Material", "STONE");
        try {
            material = Material.valueOf(matStr.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // Keep default STONE
        }

        int amount = Math.max(1, sec.getInt("Amount", 1));
        String name = sec.getString("Name", "");
        List<String> lore = new ArrayList<>(sec.getStringList("Lore"));

        return new BundledItem(material, amount, name, lore);
    }

    // -------------------------------------------------------------------------
    // Build ItemStack
    // -------------------------------------------------------------------------

    /**
     * Builds and returns an {@link ItemStack} for this bundled item.
     * Translates legacy {@code &} colour codes in both name and lore.
     *
     * @return the fully constructed {@link ItemStack}
     */
    public ItemStack buildItemStack() {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Translate & colour codes in name
        if (name != null && !name.isEmpty()) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }

        // Translate & colour codes in every lore line
        if (!lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>(lore.size());
            for (String line : lore) {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(coloredLore);
        }

        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Material getMaterial() { return material; }
    public int getAmount()        { return amount; }
    public String getName()       { return name; }
    public List<String> getLore() { return Collections.unmodifiableList(lore); }
}
