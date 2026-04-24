package de.itslarss.vaultcrates.hook;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Soft-depend integration with MMOItems — zero compile-time dependency.
 * All MMOItems API calls happen via reflection so the plugin compiles and runs
 * even when MMOItems is absent.
 *
 * <p>Class used: {@code net.Indyuce.mmoitems.MMOItems}</p>
 *
 * <p>MMOItems items require both a type ({@code MMOItemType}) and a string ID.
 * The {@link #getItem(String, String)} method accepts the type name and item ID
 * separately so callers can pass both values.</p>
 */
public class MMOItemsHook {

    private boolean enabled = false;

    // Cached reflection handles
    private Object pluginInstance;     // MMOItems.plugin (static field)
    private Method methodGetType;      // MMOItemType.get(String) → MMOItemType
    private Method methodGetItem;      // MMOItems#getItem(MMOItemType, String) → ItemStack (or NBTItem)
    private Method methodBuildToItemStack; // NBTItem#toItemStack() / or getItem returns ItemStack directly

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    /**
     * Attempts to load MMOItems API classes via reflection.
     *
     * @return {@code true} if MMOItems is present and all methods resolved
     */
    public boolean setup() {
        try {
            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Class<?> mmoItemTypeClass = Class.forName("net.Indyuce.mmoitems.api.item.MMOItemType");

            // MMOItems.plugin is a public static field
            Field pluginField = mmoItemsClass.getField("plugin");
            pluginInstance = pluginField.get(null);
            if (pluginInstance == null) {
                enabled = false;
                return false;
            }

            // MMOItemType.get(String) returns an MMOItemType instance
            methodGetType = mmoItemTypeClass.getMethod("get", String.class);

            // MMOItems#getItem(MMOItemType, String) — returns an MMTItem / ItemStack variant
            methodGetItem = mmoItemsClass.getMethod("getItem", mmoItemTypeClass, String.class);

            // Determine whether getItem returns ItemStack directly or an intermediate type
            Class<?> returnType = methodGetItem.getReturnType();
            if (!ItemStack.class.isAssignableFrom(returnType)) {
                // Common pattern: result has .build() or .toItemStack() or .newBuilder().build()
                try {
                    methodBuildToItemStack = returnType.getMethod("newBuilder");
                    // newBuilder() returns a builder; we may need .build() on that
                    // Handle by chaining in getItem()
                } catch (NoSuchMethodException e1) {
                    try {
                        methodBuildToItemStack = returnType.getMethod("toItemStack");
                    } catch (NoSuchMethodException e2) {
                        try {
                            methodBuildToItemStack = returnType.getMethod("build");
                        } catch (NoSuchMethodException e3) {
                            // Will attempt to cast or return null gracefully in getItem()
                        }
                    }
                }
            }

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
     * Returns an MMOItems item by type ID and item ID, or {@code null} if not found
     * or if MMOItems is not available.
     *
     * @param typeId the MMOItemType name (e.g. {@code "SWORD"}, {@code "BOW"})
     * @param itemId the MMOItems item identifier (e.g. {@code "FIRE_SWORD"})
     * @return the built {@link ItemStack}, or {@code null}
     */
    public ItemStack getItem(String typeId, String itemId) {
        if (!enabled || typeId == null || itemId == null) return null;
        try {
            // Resolve MMOItemType from the string
            Object mmoItemType = methodGetType.invoke(null, typeId.toUpperCase());
            if (mmoItemType == null) return null;

            // Retrieve the raw result from MMOItems#getItem
            Object result = methodGetItem.invoke(pluginInstance, mmoItemType, itemId.toUpperCase());
            if (result == null) return null;

            // If it is already an ItemStack, return directly
            if (result instanceof ItemStack is) return is;

            // Otherwise attempt to convert via a cached or discovered method
            if (methodBuildToItemStack != null) {
                Object built = methodBuildToItemStack.invoke(result);
                if (built == null) return null;

                // If methodBuildToItemStack was newBuilder(), we need another .build()
                if (built instanceof ItemStack is) return is;
                try {
                    Method buildMethod = built.getClass().getMethod("build");
                    Object finalItem = buildMethod.invoke(built);
                    if (finalItem instanceof ItemStack is) return is;
                } catch (NoSuchMethodException ignored) {}
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
