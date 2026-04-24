package de.itslarss.vaultcrates.hook;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Soft-depend integration with ExecutableItems — zero compile-time dependency.
 * All ExecutableItems API calls happen via reflection so the plugin compiles and
 * runs even when ExecutableItems is absent.
 *
 * <p>Class used: {@code com.ssomar.score.api.executableitems.ExecutableItemsAPI}</p>
 */
public class ExecutableItemsHook {

    private boolean enabled = false;

    // Cached reflection handles
    private Method methodGetManager;      // ExecutableItemsAPI.getExecutableItemManager()
    private Method methodGetItem;         // manager.getExecutableItem(String) → Optional<?>
    private Method methodBuildItem;       // executableItem.buildItem(int, Optional<Player>) → ItemStack

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    /**
     * Attempts to load ExecutableItems API classes via reflection.
     *
     * @return {@code true} if ExecutableItems is present and all methods resolved
     */
    public boolean setup() {
        try {
            Class<?> apiClass = Class.forName(
                    "com.ssomar.score.api.executableitems.ExecutableItemsAPI");

            methodGetManager = apiClass.getMethod("getExecutableItemManager");

            // Invoke once to get the manager instance and resolve further methods
            Object manager = methodGetManager.invoke(null);
            if (manager == null) {
                enabled = false;
                return false;
            }

            methodGetItem = manager.getClass().getMethod("getExecutableItem", String.class);

            // Resolve buildItem from the type inside the Optional
            // We call getExecutableItem with a dummy to determine the item class type
            // Instead, find the method by iterating declared methods of the manager's class
            // that returns Optional — then find buildItem on the wrapped type.
            // We resolve buildItem lazily in getItem() to avoid a dummy-id call here.

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
     * Builds an ExecutableItems item by its ID for the given player.
     * Returns {@code null} on any failure (item not found, reflection error, etc.).
     *
     * @param id     the ExecutableItems item ID
     * @param player the player context (used for placeholders inside the item)
     * @return the built {@link ItemStack}, or {@code null}
     */
    public ItemStack getItem(String id, Player player) {
        if (!enabled || id == null) return null;
        try {
            // Get manager fresh every time (handles reloads)
            Object manager = methodGetManager.invoke(null);
            if (manager == null) return null;

            // Ensure getItem method is resolved on the current manager type
            if (methodGetItem == null) {
                methodGetItem = manager.getClass().getMethod("getExecutableItem", String.class);
            }

            Object optionalItem = methodGetItem.invoke(manager, id);
            if (!(optionalItem instanceof Optional<?> opt)) return null;
            if (opt.isEmpty()) return null;

            Object executableItem = opt.get();

            // Resolve buildItem lazily
            if (methodBuildItem == null) {
                // Try the common signature: buildItem(int amount, Optional<Player> player)
                try {
                    methodBuildItem = executableItem.getClass()
                            .getMethod("buildItem", int.class, Optional.class);
                } catch (NoSuchMethodException ex) {
                    // Fallback: buildItem(int amount)
                    methodBuildItem = executableItem.getClass().getMethod("buildItem", int.class);
                }
            }

            if (methodBuildItem.getParameterCount() == 2) {
                return (ItemStack) methodBuildItem.invoke(
                        executableItem, 1, Optional.ofNullable(player));
            } else {
                return (ItemStack) methodBuildItem.invoke(executableItem, 1);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
