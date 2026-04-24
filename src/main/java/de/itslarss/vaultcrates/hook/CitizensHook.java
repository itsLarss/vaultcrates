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
 * Soft-depend integration with Citizens — zero compile-time dependency.
 * All Citizens API calls happen via reflection so the plugin compiles and runs
 * even when Citizens is absent. When Citizens is present, right-clicking a
 * linked NPC opens the associated crate.
 */
public class CitizensHook implements Listener {

    private final VaultCrates plugin;

    /** Maps Citizens NPC id → crate name */
    private final Map<Integer, String> npcCrateMap = new HashMap<>();

    private boolean enabled = false;

    // Cached reflection handles (resolved once in setup())
    private Method methodGetNPCRegistry;
    private Method methodIsNPC;
    private Method methodGetNPC;
    private Method methodGetId;
    private Method methodGetEntity;

    public CitizensHook(VaultCrates plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    /**
     * Attempts to load Citizens API classes via reflection.
     *
     * @return {@code true} if Citizens is present and all methods resolved
     */
    public boolean setup() {
        try {
            Class<?> apiClass      = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Class<?> registryClass = Class.forName("net.citizensnpcs.api.npc.NPCRegistry");
            Class<?> npcClass      = Class.forName("net.citizensnpcs.api.npc.NPC");

            methodGetNPCRegistry = apiClass.getMethod("getNPCRegistry");
            methodIsNPC          = registryClass.getMethod("isNPC", Entity.class);
            methodGetNPC         = registryClass.getMethod("getNPC", Entity.class);
            methodGetId          = npcClass.getMethod("getId");
            methodGetEntity      = npcClass.getMethod("getEntity");

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

    public void linkNpc(int npcId, String crateName) {
        npcCrateMap.put(npcId, crateName);
    }

    public void unlinkNpc(int npcId) {
        npcCrateMap.remove(npcId);
    }

    public String getLinkedCrate(int npcId) {
        return npcCrateMap.get(npcId);
    }

    public Map<Integer, String> getNpcCrateMap() {
        return npcCrateMap;
    }

    // -------------------------------------------------------------------------
    // Event (standard Bukkit — no Citizens import needed)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!enabled) return;
        // Only trigger on main hand to avoid double-firing
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity entity = event.getRightClicked();
        Integer npcId = getNpcId(entity);
        if (npcId == null) return;

        String crateName = npcCrateMap.get(npcId);
        if (crateName == null) return;

        Crate crate = plugin.getCrateManager().getCrate(crateName);
        if (crate == null) return;

        Player player = event.getPlayer();
        plugin.getAnimationManager().startAnimation(player, crate, entity.getLocation());
        event.setCancelled(true); // Prevent normal NPC interaction
    }

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the Citizens NPC id for the given entity, or {@code null} if the
     * entity is not a Citizens NPC or if reflection fails.
     */
    private Integer getNpcId(Entity entity) {
        try {
            Object registry = methodGetNPCRegistry.invoke(null);
            boolean isNpc   = (boolean) methodIsNPC.invoke(registry, entity);
            if (!isNpc) return null;
            Object npc = methodGetNPC.invoke(registry, entity);
            if (npc == null) return null;
            return (int) methodGetId.invoke(npc);
        } catch (Exception e) {
            return null;
        }
    }
}
