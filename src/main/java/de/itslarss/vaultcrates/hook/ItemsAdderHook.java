package de.itslarss.vaultcrates.hook;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Soft-depend integration with ItemsAdder — zero compile-time dependency.
 * All ItemsAdder API calls happen via reflection so the plugin compiles and
 * runs without ItemsAdder being present on the server.
 */
public class ItemsAdderHook {

    private boolean enabled = false;

    // Cached reflection handles
    private Method methodGetInstance;
    private Method methodByItemStack;
    private Method methodGetItemStack;
    private Method methodGetNamespacedID;

    /**
     * Attempts to load ItemsAdder API classes via reflection.
     *
     * @return {@code true} if ItemsAdder is present and all methods resolved
     */
    public boolean setup() {
        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            methodGetInstance    = customStackClass.getMethod("getInstance", String.class);
            methodByItemStack    = customStackClass.getMethod("byItemStack", ItemStack.class);
            methodGetItemStack   = customStackClass.getMethod("getItemStack");
            methodGetNamespacedID = customStackClass.getMethod("getNamespacedID");
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
     * Returns an ItemsAdder custom item by its namespace:id, or {@code null} if not found.
     *
     * @param namespacedId e.g. {@code "itemsadder:ruby_sword"}
     */
    public ItemStack getItem(String namespacedId) {
        if (!enabled) return null;
        try {
            Object stack = methodGetInstance.invoke(null, namespacedId);
            if (stack == null) return null;
            return (ItemStack) methodGetItemStack.invoke(stack);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks whether the given ItemStack is an ItemsAdder custom item.
     */
    public boolean isCustomItem(ItemStack item) {
        if (!enabled || item == null) return false;
        try {
            return methodByItemStack.invoke(null, item) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the namespaced id of an ItemsAdder item, or {@code null}.
     */
    public String getNamespacedId(ItemStack item) {
        if (!enabled || item == null) return null;
        try {
            Object stack = methodByItemStack.invoke(null, item);
            if (stack == null) return null;
            return (String) methodGetNamespacedID.invoke(stack);
        } catch (Exception e) {
            return null;
        }
    }
}
