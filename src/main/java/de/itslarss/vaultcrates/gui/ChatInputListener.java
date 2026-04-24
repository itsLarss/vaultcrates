package de.itslarss.vaultcrates.gui;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.function.Consumer;

/**
 * Temporary listener that captures the next chat message from a specific player,
 * invokes a callback and then unregisters itself.
 * Used by the in-game editor to collect text input.
 */
public class ChatInputListener implements Listener {

    private final VaultCrates plugin;
    private final Player player;
    private final Consumer<String> callback;
    private final int timeoutSeconds;
    private boolean consumed = false;

    public ChatInputListener(VaultCrates plugin, Player player,
                             Consumer<String> callback, int timeoutSeconds) {
        this.plugin = plugin;
        this.player = player;
        this.callback = callback;
        this.timeoutSeconds = timeoutSeconds;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Auto-unregister on timeout
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!consumed) {
                HandlerList.unregisterAll(this);
                player.sendMessage(ColorUtil.colorize("&c[VC] Input timed out."));
            }
        }, timeoutSeconds * 20L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
        if (consumed) return;
        consumed = true;
        event.setCancelled(true);
        String input = event.getMessage();
        HandlerList.unregisterAll(this);
        // Callback must run on main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(input));
    }
}
