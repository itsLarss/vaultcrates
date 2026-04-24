package de.itslarss.vaultcrates.hook;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Soft-depend integration with Oraxen — zero compile-time dependency.
 * All Oraxen API calls happen via reflection so the plugin compiles and runs
 * even when Oraxen is absent.
 *
 * <p>Oraxen class used: {@code io.th0rgal.oraxen.api.OraxenItems}</p>
 */
public class OraxenHook {

    private boolean enabled = false;

    // Cached reflection handles
    private Method methodGetItemById;   // OraxenItems.getItemById(String) → ItemBuilder
    private Method methodBuild;         // ItemBuilder.build() → ItemStack
    private Method methodGetIdByItem;   // OraxenItems.getIdByItem(ItemStack) → String

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    /**
     * Attempts to load Oraxen API classes via reflection.
     *
     * @return {@code true} if Oraxen is present and all methods resolved
     */
    public boolean setup() {
        try {
            Class<?> oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");

            // getItemById returns an ItemBuilder — resolve its class from the return type
            methodGetItemById = oraxenItemsClass.getMethod("getItemById", String.class);
            Class<?> itemBuilderClass = methodGetItemById.getReturnType();
            methodBuild = itemBuilderClass.getMethod("build");

            methodGetIdByItem = oraxenItemsClass.getMethod("getIdByItem", ItemStack.class);

            enabled = true;
        } catch (Exception e) {
            enabled = false;
        }
        return enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // -------------------------------------------------------------------------
    // Item retrieval
    // -------------------------------------------------------------------------

    /**
     * Returns an Oraxen custom item by its string ID, or {@code null} if not found
     * or if Oraxen is not available.
     *
     * @param id the Oraxen item ID (e.g. {@code "ruby_sword"})
     * @return the built {@link ItemStack}, or {@code null}
     */
    public ItemStack getItem(String id) {
        if (!enabled || id == null) return null;
        try {
            Object itemBuilder = methodGetItemById.invoke(null, id);
            if (itemBuilder == null) return null;
            return (ItemStack) methodBuild.invoke(itemBuilder);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns {@code true} if the given {@link ItemStack} is an Oraxen custom item.
     *
     * @param item the item to check
     * @return {@code true} if it is an Oraxen item
     */
    public boolean isOraxenItem(ItemStack item) {
        if (!enabled || item == null) return false;
        try {
            return methodGetIdByItem.invoke(null, item) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the Oraxen item ID for the given {@link ItemStack}, or {@code null}
     * if it is not an Oraxen item.
     *
     * @param item the item to look up
     * @return the Oraxen item ID, or {@code null}
     */
    public String getOraxenId(ItemStack item) {
        if (!enabled || item == null) return null;
        try {
            Object result = methodGetIdByItem.invoke(null, item);
            return result != null ? (String) result : null;
        } catch (Exception e) {
            return null;
        }
    }
}
