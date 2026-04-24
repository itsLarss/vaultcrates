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
 * Soft-depend integration with ZNPCs (old — {@code io.github.znetworkw.znpcservers}).
 * All ZNPCs API calls happen via reflection so the plugin compiles and runs
 * even when ZNPCs is absent.
 *
 * <p>Listens to {@link PlayerInteractEntityEvent} and checks if the clicked entity
 * is a ZNPCs NPC using reflection. If the NPC's name maps to a crate, the crate
 * animation is triggered.</p>
 */
public class ZNPCsHook implements Listener {

    private final VaultCrates plugin;

    /** Maps NPC name (as set in ZNPCs) to crate name. */
    private final Map<String, String> npcNameToCrate = new HashMap<>();

    private boolean enabled = false;

    // Cached reflection handles
    private Method methodGetNPCList;  // ZNPC registry / cache → collection of NPCs
    private Method methodGetEntity;   // ZNPC#getEntity() → Entity
    private Method methodGetName;     // ZNPC#getNpcPojo().getHologramLines() or getNpcName()

    // Alternative approach: check entity via ZNPCsAPI
    private Method methodFindNPCByEntity; // ZNPCsAPI or NPC cache lookup by entity

    public ZNPCsHook(VaultCrates plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    /**
     * Attempts to load ZNPCs API classes via reflection.
     * If successful, registers this listener with Bukkit.
     *
     * @return {@code true} if ZNPCs is present and reflection succeeded
     */
    public boolean setup() {
        try {
            // Try loading the primary ZNPCs class — presence check only
            Class.forName("io.github.znetworkw.znpcservers.npc.ZNPC");

            // Try to find a way to look up NPCs by entity
            // ZNPCsAPI approach (if available)
            try {
                Class<?> apiClass = Class.forName(
                        "io.github.znetworkw.znpcservers.api.ZNPCsAPI");
                // ZNPCsAPI.getZNPC(Entity) or similar — attempt common method signatures
                try {
                    methodFindNPCByEntity = apiClass.getMethod("getZNPC", Entity.class);
                } catch (NoSuchMethodException e1) {
                    try {
                        methodFindNPCByEntity = apiClass.getMethod("getNpc", Entity.class);
                    } catch (NoSuchMethodException e2) {
                        // Will fall back to iterating the NPC cache
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // ZNPCsAPI class not present — use cache iteration fallback
            }

            // Resolve ZNPC cache / list for entity matching
            try {
                Class<?> znpcClass = Class.forName(
                        "io.github.znetworkw.znpcservers.npc.ZNPC");
                Class<?> npcManagerClass = Class.forName(
                        "io.github.znetworkw.znpcservers.npc.NPCManager");
                methodGetNPCList = npcManagerClass.getMethod("getLoadedNPCS");
                methodGetEntity  = znpcClass.getMethod("getEntity");
                // Try common name-getters
                try {
                    methodGetName = znpcClass.getMethod("getNpcName");
                } catch (NoSuchMethodException ex) {
                    // getNpcName may not exist; we'll fall back to entity name matching
                }
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // Some versions lack NPCManager — rely on entity UUID matching
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
     * Links a ZNPCs NPC (identified by its name) to a crate.
     *
     * @param npcName  the NPC name as configured in ZNPCs
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
     * Returns the crate name linked to the given NPC name, or {@code null} if
     * no mapping exists.
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
     * Attempts to resolve the ZNPCs NPC name for the given entity.
     * Returns {@code null} if the entity is not a ZNPCs NPC or on any error.
     */
    private String resolveNpcName(Entity entity) {
        try {
            // Fast path: direct API lookup
            if (methodFindNPCByEntity != null) {
                Object npc = methodFindNPCByEntity.invoke(null, entity);
                if (npc == null) return null;
                return extractNpcName(npc, entity);
            }

            // Slower path: iterate NPC list
            if (methodGetNPCList != null) {
                Object collection = methodGetNPCList.invoke(null);
                if (collection instanceof Iterable<?> iterable) {
                    for (Object npc : iterable) {
                        if (methodGetEntity != null) {
                            Object npcEntity = methodGetEntity.invoke(npc);
                            if (entity.equals(npcEntity)) {
                                return extractNpcName(npc, entity);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Extracts the name from a ZNPC object. Falls back to the entity's custom name
     * if the dedicated name method is unavailable.
     */
    private String extractNpcName(Object npc, Entity entity) {
        try {
            if (methodGetName != null) {
                Object result = methodGetName.invoke(npc);
                if (result instanceof String s) return s;
            }
        } catch (Exception ignored) {}

        // Fallback: use entity custom name
        String customName = entity.getCustomName();
        return customName;
    }
}
