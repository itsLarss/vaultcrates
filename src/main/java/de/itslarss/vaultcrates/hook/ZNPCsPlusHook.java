package de.itslarss.vaultcrates.hook;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Soft-depend integration with ZNPCsPlus 2.x ({@code lol.pyr.znpcsplus}).
 *
 * <p>ZNPCsPlus NPCs are packet-only, so this hook listens to ZNPCsPlus'
 * {@code NpcInteractEvent} (registered via reflection) and matches the NPC by
 * its id ({@code NpcEntry#getId()} — the id used in {@code /npc} commands).</p>
 */
public class ZNPCsPlusHook implements Listener {

    private static final String EVENT = "lol.pyr.znpcsplus.api.event.NpcInteractEvent";

    private final VaultCrates plugin;

    /** ZNPCsPlus NPC id → crate name. */
    private final Map<String, String> npcIdToCrate = new HashMap<>();

    private boolean enabled = false;

    private Method eventGetEntry;
    private Method eventGetPlayer;
    private Method eventGetClickType;
    private Method entryGetId;
    private Method entryGetNpc;
    private Method npcGetLocation;
    private Method npcGetWorld;
    private Method locationToBukkit;

    public ZNPCsPlusHook(VaultCrates plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (!Bukkit.getPluginManager().isPluginEnabled("ZNPCsPlus")) return false;
        try {
            Class<?> raw   = Class.forName(EVENT);
            Class<?> entry = Class.forName("lol.pyr.znpcsplus.api.npc.NpcEntry");
            Class<?> npc   = Class.forName("lol.pyr.znpcsplus.api.npc.Npc");
            eventGetEntry     = raw.getMethod("getEntry");
            eventGetPlayer    = raw.getMethod("getPlayer");
            eventGetClickType = raw.getMethod("getClickType");
            entryGetId        = entry.getMethod("getId");
            entryGetNpc       = entry.getMethod("getNpc");
            npcGetLocation    = npc.getMethod("getLocation");
            npcGetWorld       = npc.getMethod("getWorld");
            locationToBukkit  = npcGetLocation.getReturnType().getMethod("toBukkitLocation", World.class);

            @SuppressWarnings("unchecked")
            Class<? extends Event> type = (Class<? extends Event>) raw;
            Bukkit.getPluginManager().registerEvent(type, this, EventPriority.HIGH,
                    (l, event) -> { if (type.isInstance(event)) onInteract(event); }, plugin, true);
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
    // NPC ↔ Crate mapping
    // -------------------------------------------------------------------------

    public void link(String npcId, String crateName) {
        npcIdToCrate.put(npcId, crateName);
    }

    public void unlink(String npcId) {
        npcIdToCrate.remove(npcId);
    }

    public String getLinkedCrate(String npcId) {
        return npcIdToCrate.get(npcId);
    }

    public Map<String, String> getNpcIdToCrate() {
        return npcIdToCrate;
    }

    // -------------------------------------------------------------------------
    // Event
    // -------------------------------------------------------------------------

    private void onInteract(Event event) {
        try {
            Object click = eventGetClickType.invoke(event);
            if (click != null && "LEFT_CLICK".equals(click.toString())) return;

            Object entry = eventGetEntry.invoke(event);
            if (!(entryGetId.invoke(entry) instanceof String id)) return;
            String crateName = npcIdToCrate.get(id);
            Crate crate = crateName != null ? plugin.getCrateManager().getCrate(crateName) : null;
            if (crate == null) return;
            if (!(eventGetPlayer.invoke(event) instanceof Player player)) return;

            Location loc = player.getLocation();
            Object npc = entryGetNpc.invoke(entry);
            if (npcGetWorld.invoke(npc) instanceof World world
                    && locationToBukkit.invoke(npcGetLocation.invoke(npc), world) instanceof Location l) {
                loc = l;
            }

            if (event instanceof Cancellable c) c.setCancelled(true);
            Location finalLoc = loc;
            runSync(() -> plugin.getCrateListener().openCrate(player, crate, finalLoc));
        } catch (Exception ignored) {}
    }

    private void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }
}
