package de.itslarss.vaultcrates.hook;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Soft-depend integration with ExecutableItems (via SCore) — zero compile-time dependency.
 *
 * <p>Call chain: {@code ExecutableItemsAPI.getExecutableItemsManager()
 * .getExecutableItem(id) → Optional<ExecutableItemInterface>
 * .buildItem(amount, Optional<Player>)}</p>
 *
 * <p>Methods are resolved on the public API interfaces, not on the implementation
 * classes, so reflection keeps working when SCore changes its internals.</p>
 */
public class ExecutableItemsHook {

    private static final String API      = "com.ssomar.score.api.executableitems.ExecutableItemsAPI";
    private static final String MANAGER  = "com.ssomar.score.api.executableitems.config.ExecutableItemsManagerInterface";
    private static final String ITEM     = "com.ssomar.score.api.executableitems.config.ExecutableItemInterface";

    private boolean enabled = false;

    private Method methodGetManager;
    private Method methodGetItem;
    private Method methodBuildItem;

    public boolean setup() {
        try {
            Class<?> apiClass = Class.forName(API);
            try {
                methodGetManager = apiClass.getMethod("getExecutableItemsManager");
            } catch (NoSuchMethodException old) {
                methodGetManager = apiClass.getMethod("getExecutableItemManager");
            }
            methodGetItem   = Class.forName(MANAGER).getMethod("getExecutableItem", String.class);
            methodBuildItem = Class.forName(ITEM).getMethod("buildItem", int.class, Optional.class);
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
     * @param id     the ExecutableItems item id
     * @param player placeholder context, may be {@code null}
     * @return the built item, or {@code null} if unknown
     */
    public ItemStack getItem(String id, Player player) {
        if (!enabled || id == null) return null;
        try {
            Object manager = methodGetManager.invoke(null);
            if (manager == null) return null;
            if (!(methodGetItem.invoke(manager, id) instanceof Optional<?> opt) || opt.isEmpty()) return null;
            Object built = methodBuildItem.invoke(opt.get(), 1, Optional.ofNullable(player));
            return built instanceof ItemStack is ? is : null;
        } catch (Exception e) {
            return null;
        }
    }
}
