package de.itslarss.vaultcrates.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fluent builder for {@link ItemStack} objects.
 * Supports name, lore, enchantments, glow, custom model data, PDC and Base64 skull textures.
 */
public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    private ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /** Creates a new builder for the given material. */
    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material == null ? Material.STONE : material);
    }

    // -------------------------------------------------------------------------
    // Builder methods
    // -------------------------------------------------------------------------

    /** Sets the display name (translates & colour codes). */
    public ItemBuilder name(String name) {
        if (name == null || name.isEmpty()) return this;
        meta.displayName(LegacyComponentSerializer.legacyAmpersand()
                .deserialize(ColorUtil.colorize(name)));
        return this;
    }

    /** Sets the lore lines (translates & colour codes on each line). */
    public ItemBuilder lore(List<String> lore) {
        if (lore == null || lore.isEmpty()) return this;
        List<Component> components = lore.stream()
                .map(l -> LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(ColorUtil.colorize(l)))
                .collect(Collectors.toList());
        meta.lore(components);
        return this;
    }

    /** Appends additional lore lines. */
    public ItemBuilder addLore(String... lines) {
        if (lines == null) return this;
        List<Component> existing = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        for (String line : lines) {
            existing.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(ColorUtil.colorize(line)));
        }
        meta.lore(existing);
        return this;
    }

    /** Sets the stack size. */
    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        return this;
    }

    /**
     * Adds a glow effect by applying an invisible enchantment.
     * Uses the Registry API (compatible with all Paper 1.21.x versions).
     */
    public ItemBuilder glow(boolean glow) {
        if (!glow) return this;
        Enchantment infinity = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("infinity"));
        if (infinity != null) {
            meta.addEnchant(infinity, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    /** Sets the CustomModelData value. */
    public ItemBuilder customModelData(int data) {
        if (data > 0) meta.setCustomModelData(data);
        return this;
    }

    /**
     * Adds an enchantment to the item.
     * Automatically uses {@link org.bukkit.inventory.meta.EnchantmentStorageMeta#addStoredEnchant}
     * for {@link Material#ENCHANTED_BOOK} items, and {@link ItemMeta#addEnchant} for all others.
     */
    public ItemBuilder enchantment(Enchantment ench, int level) {
        if (ench == null) return this;
        if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta esm) {
            esm.addStoredEnchant(ench, level, true);
        } else {
            meta.addEnchant(ench, level, true);
        }
        return this;
    }

    /** Hides the specified item flags. */
    public ItemBuilder flags(ItemFlag... flags) {
        if (flags != null) meta.addItemFlags(flags);
        return this;
    }

    /**
     * Applies a Base64 skull texture to a PLAYER_HEAD item.
     * Decodes the Base64 value to extract the skin URL from the embedded JSON.
     *
     * @param base64 the Base64-encoded texture value (from minecraft-heads.com "Value" field)
     */
    public ItemBuilder skullTexture(String base64) {
        if (base64 == null || base64.isEmpty()) return this;
        if (!(meta instanceof SkullMeta skullMeta)) return this;
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            String json = new String(decoded);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String url = root.getAsJsonObject("textures")
                    .getAsJsonObject("SKIN")
                    .get("url").getAsString();

            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create(url).toURL());
            profile.setTextures(textures);
            skullMeta.setOwnerProfile(profile);
        } catch (Exception e) {
            // Silently ignore invalid textures
        }
        return this;
    }

    /**
     * Writes an arbitrary value into the item's PersistentDataContainer.
     *
     * @param key   the namespaced key
     * @param type  the PDC type
     * @param value the value to store
     */
    public <T, Z> ItemBuilder pdc(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        meta.getPersistentDataContainer().set(key, type, value);
        return this;
    }

    /** Builds and returns the finished {@link ItemStack}. */
    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
