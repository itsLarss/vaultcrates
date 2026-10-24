package de.itslarss.vaultcrates.hook;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Soft-depend integration with MMOItems — zero compile-time dependency.
 * Uses {@code MMOItems.plugin.getItem(String type, String id)} (MMOItems 6.x).
 */
public class MMOItemsHook {

    private boolean enabled = false;

    private Class<?> mmoItemsClass;
    private Method methodGetItem;

    public boolean setup() {
        try {
            mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            methodGetItem = mmoItemsClass.getMethod("getItem", String.class, String.class);
            enabled = true;
        } catch (Exception e) {
            enabled = false;
        }
        return enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param typeId the MMOItems type (e.g. {@code "SWORD"})
     * @param itemId the item id (e.g. {@code "FIRE_SWORD"})
     * @return the built item, or {@code null} if unknown
     */
    public ItemStack getItem(String typeId, String itemId) {
        if (!enabled || typeId == null || itemId == null) return null;
        try {
            // Read the static field on every call — MMOItems replaces it on reload
            Object plugin = mmoItemsClass.getField("plugin").get(null);
            if (plugin == null) return null;
            Object result = methodGetItem.invoke(plugin, typeId.toUpperCase(), itemId.toUpperCase());
            return result instanceof ItemStack is ? is : null;
        } catch (Exception e) {
            return null;
        }
    }
}
