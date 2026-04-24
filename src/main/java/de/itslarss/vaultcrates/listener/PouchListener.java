package de.itslarss.vaultcrates.listener;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.pouch.Pouch;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Handles player interactions with pouch items.
 * Pouches can be right-clicked (placed-on-block style or in air).
 */
public class PouchListener implements Listener {

    private final VaultCrates plugin;

    public PouchListener(VaultCrates plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!plugin.getPouchManager().isPouch(item)) return;

        String pouchName = plugin.getPouchManager().getPouchName(item);
        Pouch pouch = plugin.getPouchManager().getPouch(pouchName);
        if (pouch == null) return;

        event.setCancelled(true);

        // Determine location
        Location loc;
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block clicked = event.getClickedBlock();
            loc = clicked.getRelative(event.getBlockFace()).getLocation();
        } else {
            loc = player.getLocation().clone().add(0, 1, 0);
        }

        // Remove one pouch from hand
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        plugin.getPouchManager().openPouch(player, pouch, loc);
    }

    /**
     * Prevents pouches from being placed as blocks (if OnlyPlaceable mode).
     * Instead intercepts the place event and opens the pouch.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!plugin.getPouchManager().isPouch(item)) return;

        event.setCancelled(true);

        String pouchName = plugin.getPouchManager().getPouchName(item);
        Pouch pouch = plugin.getPouchManager().getPouch(pouchName);
        if (pouch == null) return;

        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        // Consume item
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        plugin.getPouchManager().openPouch(player, pouch, loc);
    }
}
