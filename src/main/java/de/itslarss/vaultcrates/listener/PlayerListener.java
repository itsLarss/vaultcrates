package de.itslarss.vaultcrates.listener;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.util.ColorUtil;
import de.itslarss.vaultcrates.util.UpdateChecker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

/**
 * Handles miscellaneous player events for VaultCrates:
 * update notifications on join, animation stop on logout/death,
 * and blocking commands during crate openings.
 */
public class PlayerListener implements Listener {

    private final VaultCrates plugin;

    public PlayerListener(VaultCrates plugin) {
        this.plugin = plugin;
    }

    /**
     * Notifies admins and OPs about a pending VaultCrates update when they join.
     * Only fires if the update checker is enabled and has detected a newer version.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Only notify admins and OPs
        if (!player.hasPermission("vaultcrates.admin") && !player.isOp()) return;

        UpdateChecker checker = plugin.getUpdateChecker();
        if (checker == null || !checker.isUpdateAvailable()) return;

        // Delay by 1 tick so the join message has already been sent
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage(ColorUtil.colorize("&8[&6VaultCrates&8] &eA new update is available!"));
            player.sendMessage(ColorUtil.colorize("  &7Current version: &c" + checker.getCurrentVersion()));
            player.sendMessage(ColorUtil.colorize("  &7Latest version:  &a" + checker.getLatestVersion()));
            player.sendMessage(ColorUtil.colorize("  &7Download: &b" + checker.getDownloadUrl()));
        }, 1L);
    }

    /** Force-stops any active animation when a player disconnects. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getAnimationManager().isAnimating(player)) {
            plugin.getAnimationManager().forceStop(player);
        }
    }

    /** Force-stops any active animation when a player dies. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.getAnimationManager().isAnimating(player)) {
            plugin.getAnimationManager().forceStop(player);
        }
    }

    /** Blocks configured commands while a player is in a crate animation. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getAnimationManager().isAnimating(player)) return;
        if (player.hasPermission("vaultcrates.bypass")) return;

        List<String> blocked = plugin.getConfigManager().getConfig()
                .getStringList("Blocked_Commands");
        if (blocked.isEmpty()) return;

        String command = event.getMessage().toLowerCase().substring(1).split(" ")[0];
        if (blocked.stream().anyMatch(b -> b.equalsIgnoreCase(command))) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.colorize("&c[VC] You cannot use that command while opening a crate!"));
        }
    }
}
