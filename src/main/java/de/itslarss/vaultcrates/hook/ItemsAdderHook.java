package de.itslarss.vaultcrates.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * Soft-depend integration with ItemsAdder (3.6 – 4.x) — zero compile-time dependency.
 * All ItemsAdder API calls happen via reflection so the plugin compiles and
 * runs without ItemsAdder being present on the server.
 */
public class ItemsAdderHook {

    private boolean enabled = false;

    // CustomStack
    private Method methodGetInstance;
    private Method methodByItemStack;
    private Method methodGetItemStack;
    private Method methodGetNamespacedID;

    // CustomFurniture — maps any furniture entity (incl. hitbox/Interaction parts) to its base entity
    private Method methodFurnitureByEntity;
    private Method methodFurnitureGetEntity;

    // :icon: replacement — IA 4.x: FontImages.FontImageWrapper.replaceFontImages(String),
    // IA 3.x: FontImages.setAllFontImages(String)
    private Method methodReplaceFontImages;

    public boolean setup() {
        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            methodGetInstance     = customStackClass.getMethod("getInstance", String.class);
            methodByItemStack     = customStackClass.getMethod("byItemStack", ItemStack.class);
            methodGetItemStack    = customStackClass.getMethod("getItemStack");
            methodGetNamespacedID = customStackClass.getMethod("getNamespacedID");
            enabled = true;
        } catch (Exception e) {
            enabled = false;
            return false;
        }

        try {
            Class<?> furniture = Class.forName("dev.lone.itemsadder.api.CustomFurniture");
            methodFurnitureByEntity  = furniture.getMethod("byAlreadySpawned", Entity.class);
            methodFurnitureGetEntity = furniture.getMethod("getEntity");
        } catch (Exception ignored) { /* very old IA */ }

        try {
            methodReplaceFontImages = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper")
                    .getMethod("replaceFontImages", String.class);
        } catch (Exception newApiMissing) {
            try {
                methodReplaceFontImages = Class.forName("dev.lone.itemsadder.api.FontImages")
                        .getMethod("setAllFontImages", String.class);
            } catch (Exception ignored) { /* no font image support */ }
        }

        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns an ItemsAdder custom item by its namespace:id, or {@code null} if not found.
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

    public boolean isCustomItem(ItemStack item) {
        if (!enabled || item == null) return false;
        try {
            return methodByItemStack.invoke(null, item) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Replaces {@code :icon_name:} placeholders with the resource-pack glyphs. */
    public String processText(String text) {
        if (!enabled || methodReplaceFontImages == null || text == null) return text;
        try {
            return (String) methodReplaceFontImages.invoke(null, text);
        } catch (Exception e) {
            return text;
        }
    }

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

    /**
     * IA furniture can consist of several entities (display + interaction hitbox).
     * Returns the furniture's base entity for any of its parts, or {@code null}
     * if the entity is not IA furniture.
     */
    public Entity getFurnitureBaseEntity(Entity entity) {
        if (!enabled || methodFurnitureByEntity == null || entity == null) return null;
        try {
            Object furniture = methodFurnitureByEntity.invoke(null, entity);
            if (furniture == null) return null;
            return (Entity) methodFurnitureGetEntity.invoke(furniture);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Cancels ItemsAdder's own break and interact handling for furniture that
     * belongs to a placed crate, so IA never picks the crate up or runs its
     * furniture actions. VaultCrates handles those clicks itself.
     */
    public void registerFurnitureGuard(Plugin plugin, Predicate<Entity> isCrate) {
        if (!enabled) return;
        Listener owner = new Listener() {};
        for (String name : new String[]{"FurnitureBreakEvent", "FurnitureInteractEvent"}) {
            try {
                Class<?> raw = Class.forName("dev.lone.itemsadder.api.Events." + name);
                if (!Event.class.isAssignableFrom(raw)) continue;
                @SuppressWarnings("unchecked")
                Class<? extends Event> type = (Class<? extends Event>) raw;
                Method getEntity = raw.getMethod("getBukkitEntity");
                Bukkit.getPluginManager().registerEvent(type, owner, EventPriority.LOWEST, (l, event) -> {
                    if (!type.isInstance(event) || !(event instanceof Cancellable c)) return;
                    try {
                        if (getEntity.invoke(event) instanceof Entity e && isCrate.test(e)) {
                            c.setCancelled(true);
                        }
                    } catch (Exception ignored) {}
                }, plugin, false);
            } catch (Exception ignored) { /* event not present in this IA version */ }
        }
    }
}
