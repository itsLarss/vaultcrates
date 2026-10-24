package de.itslarss.vaultcrates.hook;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Soft-depend integration with FancyNpcs 2.x ({@code de.oliver.fancynpcs}).
 *
 * <p>FancyNpcs NPCs are packet-only — there is no Bukkit entity to click, so
 * {@code PlayerInteractEntityEvent} never fires for them. This hook listens to
 * FancyNpcs' own {@code NpcInteractEvent} (registered via reflection) and matches
 * the NPC by its name ({@code NpcData#getName()}).</p>
 */
public class FancyNpcsHook implements Listener {

    private static final String EVENT = "de.oliver.fancynpcs.api.events.NpcInteractEvent";

    private final VaultCrates plugin;

    /** FancyNpcs NPC name → crate name. */
    private final Map<String, String> npcNameToCrate = new HashMap<>();

    private boolean enabled = false;

    private Method eventGetNpc;
    private Method eventGetPlayer;
    private Method eventGetTrigger;
    private Method npcGetData;
    private Method dataGetName;
    private Method dataGetLocation;

    public FancyNpcsHook(VaultCrates plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (!Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) return false;
        try {
            Class<?> raw = Class.forName(EVENT);
            Class<?> npc  = Class.forName("de.oliver.fancynpcs.api.Npc");
            Class<?> data = Class.forName("de.oliver.fancynpcs.api.NpcData");
            eventGetNpc     = raw.getMethod("getNpc");
            eventGetPlayer  = raw.getMethod("getPlayer");
            eventGetTrigger = raw.getMethod("getInteractionType");
            npcGetData      = npc.getMethod("getData");
            dataGetName     = data.getMethod("getName");
            dataGetLocation = data.getMethod("getLocation");

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

    public void link(String npcName, String crateName) {
        npcNameToCrate.put(npcName, crateName);
    }

    public void unlink(String npcName) {
        npcNameToCrate.remove(npcName);
    }

    public String getLinkedCrate(String npcName) {
        return npcNameToCrate.get(npcName);
    }

    public Map<String, String> getNpcNameToCrate() {
        return npcNameToCrate;
    }

    // -------------------------------------------------------------------------
    // Event
    // -------------------------------------------------------------------------

    private void onInteract(Event event) {
        try {
            Object trigger = eventGetTrigger.invoke(event);
            if (trigger != null && !"RIGHT_CLICK".equals(trigger.toString())
                    && !"ANY_CLICK".equals(trigger.toString())) return;

            Object data = npcGetData.invoke(eventGetNpc.invoke(event));
            if (!(dataGetName.invoke(data) instanceof String name)) return;
            String crateName = npcNameToCrate.get(name);
            Crate crate = crateName != null ? plugin.getCrateManager().getCrate(crateName) : null;
            if (crate == null) return;
            if (!(eventGetPlayer.invoke(event) instanceof Player player)) return;
            Location loc = dataGetLocation.invoke(data) instanceof Location l ? l : player.getLocation();

            // Stop FancyNpcs from running the NPC's own actions
            if (event instanceof Cancellable c) c.setCancelled(true);
            runSync(() -> plugin.getCrateListener().openCrate(player, crate, loc));
        } catch (Exception ignored) {}
    }

    private void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }
}
