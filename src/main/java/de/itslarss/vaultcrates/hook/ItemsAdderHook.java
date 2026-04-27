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

    // Cached reflection handles — CustomStack
    private Method methodGetInstance;
    private Method methodByItemStack;
    private Method methodGetItemStack;
    private Method methodGetNamespacedID;

    // Cached reflection handle — FontImages (for :icon: placeholder processing)
    private Method methodSetAllFontImages;

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

        // FontImages — optional, don't fail setup if missing
        try {
            Class<?> fontImagesClass = Class.forName("dev.lone.itemsadder.api.FontImages");
            methodSetAllFontImages = fontImagesClass.getMethod("setAllFontImages", String.class);
        } catch (Exception ignored) { /* not available in this IA version */ }

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
     * Processes IA font-image placeholders ({@code :icon_name:}) in a string,
     * replacing them with the actual Unicode characters from the resource pack.
     *
     * <p>Requires ItemsAdder to be loaded and {@code FontImages.setAllFontImages}
     * to be available (IA 2.x+). Returns the original string unchanged if IA is
     * not enabled or the method is not available.</p>
     *
     * <p>Example: {@code ":heart_of_sea: &6Rank"} → {@code "\uE001 &6Rank"}</p>
     *
     * @param text the raw text containing optional {@code :icon:} placeholders
     * @return the text with all known placeholders replaced
     */
    public String processText(String text) {
        if (!enabled || methodSetAllFontImages == null || text == null) return text;
        try {
            return (String) methodSetAllFontImages.invoke(null, text);
        } catch (Exception e) {
            return text;
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
