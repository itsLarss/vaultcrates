package de.itslarss.vaultcrates.hook;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Soft-depend integration with FancyNpcs ({@code de.oliver.fancynpcs}).
 * All FancyNpcs API calls happen via reflection so the plugin compiles and runs
 * even when FancyNpcs is absent.
 *
 * <p>Uses reflection to call
 * {@code FancyNpcsPlugin.get().getNpcAdapter().getNpc(entity)} to detect NPC
 * entities. The NPC name is then matched against the configured mapping.</p>
 */
public class FancyNpcsHook implements Listener {

    private final VaultCrates plugin;

    /** Maps FancyNpcs NPC name to crate name. */
    private final Map<String, String> npcNameToCrate = new HashMap<>();

    private boolean enabled = false;

    // Cached reflection handles
    private Method methodGet;          // FancyNpcsPlugin.get() → FancyNpcsPlugin instance
    private Method methodGetAdapter;   // FancyNpcsPlugin#getNpcAdapter() → NpcAdapter
    private Method methodGetNpc;       // NpcAdapter#getNpc(Entity) → Npc (or null)
    private Method methodGetData;      // Npc#getData() → NpcData (contains name)
    private Method methodGetName;      // NpcData#getName() or Npc#getName() → String

    public FancyNpcsHook(VaultCrates plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    /**
     * Attempts to load FancyNpcs API classes via reflection.
     * If successful, registers this listener with Bukkit.
     *
     * @return {@code true} if FancyNpcs is present and reflection succeeded
     */
    public boolean setup() {
        try {
            Class<?> pluginClass = Class.forName("de.oliver.fancynpcs.api.FancyNpcsPlugin");

            // FancyNpcsPlugin.get() is a static method
            methodGet = pluginClass.getMethod("get");

            // Get the plugin instance to resolve further types
            Object fancyPlugin = methodGet.invoke(null);
            if (fancyPlugin == null) {
                enabled = false;
                return false;
            }

            // getNpcAdapter()
            methodGetAdapter = fancyPlugin.getClass().getMethod("getNpcAdapter");
            Object adapter = methodGetAdapter.invoke(fancyPlugin);
            if (adapter == null) {
                enabled = false;
                return false;
            }

            // NpcAdapter#getNpc(Entity)
            methodGetNpc = adapter.getClass().getMethod("getNpc", Entity.class);

            // Determine how to extract the name from the Npc object
            Class<?> npcReturnType = methodGetNpc.getReturnType();
            // Try Npc#getData() → NpcData#getName() first
            try {
                methodGetData = npcReturnType.getMethod("getData");
                Class<?> dataType = methodGetData.getReturnType();
                methodGetName = dataType.getMethod("getName");
            } catch (NoSuchMethodException e) {
                // Fallback: Npc#getName() directly
                methodGetData = null;
                methodGetName = npcReturnType.getMethod("getName");
            }

            enabled = true;
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        } catch (Exception e) {
            enabled = false;
        }
        return enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // -------------------------------------------------------------------------
    // NPC ↔ Crate mapping
    // -------------------------------------------------------------------------

    /**
     * Links a FancyNpcs NPC (identified by its name) to a crate.
     *
     * @param npcName   the NPC name as configured in FancyNpcs
     * @param crateName the crate name to open on interaction
     */
    public void link(String npcName, String crateName) {
        npcNameToCrate.put(npcName, crateName);
    }

    /**
     * Removes the crate link for the given NPC name.
     *
     * @param npcName the NPC name to unlink
     */
    public void unlink(String npcName) {
        npcNameToCrate.remove(npcName);
    }

    /**
     * Returns the crate name linked to the given NPC name, or {@code null} if none.
     *
     * @param npcName the NPC name
     * @return the linked crate name, or {@code null}
     */
    public String getLinkedCrate(String npcName) {
        return npcNameToCrate.get(npcName);
    }

    public Map<String, String> getNpcNameToCrate() {
        return npcNameToCrate;
    }

    // -------------------------------------------------------------------------
    // Event
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!enabled) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity entity = event.getRightClicked();
        String npcName = resolveNpcName(entity);
        if (npcName == null) return;

        String crateName = npcNameToCrate.get(npcName);
        if (crateName == null) return;

        Crate crate = plugin.getCrateManager().getCrate(crateName);
        if (crate == null) return;

        Player player = event.getPlayer();
        plugin.getAnimationManager().startAnimation(player, crate, entity.getLocation());
        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    /**
     * Attempts to resolve the FancyNpcs NPC name for the given entity using the
     * reflection chain:
     * {@code FancyNpcsPlugin.get().getNpcAdapter().getNpc(entity).[getData().getName()]}
     *
     * @param entity the entity to look up
     * @return the NPC name, or {@code null} if not a FancyNpcs NPC or on any error
     */
    private String resolveNpcName(Entity entity) {
        try {
            Object fancyPlugin = methodGet.invoke(null);
            if (fancyPlugin == null) return null;

            Object adapter = methodGetAdapter.invoke(fancyPlugin);
            if (adapter == null) return null;

            Object npc = methodGetNpc.invoke(adapter, entity);
            if (npc == null) return null;

            if (methodGetData != null) {
                Object data = methodGetData.invoke(npc);
                if (data == null) return null;
                Object name = methodGetName.invoke(data);
                return name instanceof String s ? s : null;
            } else {
                Object name = methodGetName.invoke(npc);
                return name instanceof String s ? s : null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
