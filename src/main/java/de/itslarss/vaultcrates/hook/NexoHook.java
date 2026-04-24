package de.itslarss.vaultcrates.hook;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Soft-depend integration with Nexo — zero compile-time dependency.
 * All Nexo API calls happen via reflection so the plugin compiles and runs
 * even when Nexo is absent.
 *
 * <p>Nexo class used: {@code com.nexomc.nexo.api.NexoItems}</p>
 */
public class NexoHook {

    private boolean enabled = false;

    // Cached reflection handles
    private Method methodItemFromId; // NexoItems.itemFromId(String) → ItemBuilder
    private Method methodBuild;      // ItemBuilder.build() → ItemStack

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    /**
     * Attempts to load Nexo API classes via reflection.
     *
     * @return {@code true} if Nexo is present and all methods resolved
     */
    public boolean setup() {
        try {
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");

            // itemFromId returns a Nexo ItemBuilder — resolve the class from the return type
            methodItemFromId = nexoItemsClass.getMethod("itemFromId", String.class);
            Class<?> itemBuilderClass = methodItemFromId.getReturnType();
            methodBuild = itemBuilderClass.getMethod("build");

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
     * Returns a Nexo custom item by its string ID, or {@code null} if not found
     * or if Nexo is not available.
     *
     * @param id the Nexo item ID
     * @return the built {@link ItemStack}, or {@code null}
     */
    public ItemStack getItem(String id) {
        if (!enabled || id == null) return null;
        try {
            Object itemBuilder = methodItemFromId.invoke(null, id);
            if (itemBuilder == null) return null;
            return (ItemStack) methodBuild.invoke(itemBuilder);
        } catch (Exception e) {
            return null;
        }
    }
}
