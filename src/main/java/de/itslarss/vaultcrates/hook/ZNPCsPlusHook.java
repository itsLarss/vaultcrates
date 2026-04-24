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
 * Soft-depend integration with ZNPCsPlus ({@code lol.pyr.znpcsplus}).
 * All ZNPCsPlus API calls happen via reflection so the plugin compiles and runs
 * even when ZNPCsPlus is absent.
 *
 * <p>Listens to {@link PlayerInteractEntityEvent} and detects ZNPCsPlus NPCs
 * using reflection. If the NPC's ID/name maps to a crate, the crate animation
 * is triggered.</p>
 */
public class ZNPCsPlusHook implements Listener {

    private final VaultCrates plugin;

    /** Maps NPC string ID/name to crate name. */
    private final Map<String, String> npcIdToCrate = new HashMap<>();

    private boolean enabled = false;

    // Cached reflection handles
    private Method methodGetNpcById;   // ZNPCsPlus API lookup by entity or id
    private Method methodGetId;        // NPC#getId() → String
    private Method methodGetEntityId;  // NPC lookup via entity

    public ZNPCsPlusHook(VaultCrates plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    /**
     * Attempts to load ZNPCsPlus API classes via reflection.
     * If successful, registers this listener with Bukkit.
     *
     * @return {@code true} if ZNPCsPlus is present and reflection succeeded
     */
    public boolean setup() {
        try {
            // Presence check — main plugin class
            Class<?> mainClass = Class.forName("lol.pyr.znpcsplus.ZNPCsPlus");

            // Try to locate an API or registry class for entity→NPC lookup
            try {
                Class<?> apiClass = Class.forName("lol.pyr.znpcsplus.api.NpcApi");
                // Attempt common lookup methods
                try {
                    methodGetNpcById = apiClass.getMethod("getNpcByEntity", Entity.class);
                } catch (NoSuchMethodException e1) {
                    try {
                        methodGetNpcById = apiClass.getMethod("get", Entity.class);
                    } catch (NoSuchMethodException e2) {
                        // Will fall back to iterating all NPCs
                    }
                }

                // Resolve getId on NPC instances
                try {
                    Class<?> npcClass = Class.forName("lol.pyr.znpcsplus.api.npc.Npc");
                    methodGetId = npcClass.getMethod("getId");
                } catch (ClassNotFoundException | NoSuchMethodException ex) {
                    try {
                        Class<?> npcClass = Class.forName("lol.pyr.znpcsplus.npc.Npc");
                        methodGetId = npcClass.getMethod("getId");
                    } catch (ClassNotFoundException | NoSuchMethodException ignored) {}
                }

            } catch (ClassNotFoundException ignored) {
                // No API class — entity matching will use custom name fallback
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
     * Links a ZNPCsPlus NPC (identified by its string ID or name) to a crate.
     *
     * @param npcId     the NPC identifier as used in ZNPCsPlus config
     * @param crateName the crate name to open on interaction
     */
    public void link(String npcId, String crateName) {
        npcIdToCrate.put(npcId, crateName);
    }

    /**
     * Removes the crate link for the given NPC ID.
     *
     * @param npcId the NPC ID to unlink
     */
    public void unlink(String npcId) {
        npcIdToCrate.remove(npcId);
    }

    /**
     * Returns the crate name linked to the given NPC ID, or {@code null} if none.
     *
     * @param npcId the NPC identifier
     * @return the linked crate name, or {@code null}
     */
    public String getLinkedCrate(String npcId) {
        return npcIdToCrate.get(npcId);
    }

    public Map<String, String> getNpcIdToCrate() {
        return npcIdToCrate;
    }

    // -------------------------------------------------------------------------
    // Event
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!enabled) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity entity = event.getRightClicked();
        String npcId = resolveNpcId(entity);
        if (npcId == null) return;

        String crateName = npcIdToCrate.get(npcId);
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
     * Attempts to resolve the ZNPCsPlus NPC id/name for the given entity.
     * Returns {@code null} if the entity is not a ZNPCsPlus NPC or on any error.
     */
    private String resolveNpcId(Entity entity) {
        try {
            if (methodGetNpcById != null) {
                Object npc = methodGetNpcById.invoke(null, entity);
                if (npc == null) return null;
                if (methodGetId != null) {
                    Object id = methodGetId.invoke(npc);
                    if (id instanceof String s) return s;
                }
                return null;
            }
        } catch (Exception ignored) {}

        // Fallback: entity custom name
        return entity.getCustomName();
    }
}
