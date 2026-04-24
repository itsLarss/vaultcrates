package de.itslarss.vaultcrates.listener;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.gui.base.ChestGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Handles all inventory interactions for VaultCrates GUI menus.
 */
public class GuiListener implements Listener {

    private final VaultCrates plugin;

    public GuiListener(VaultCrates plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ChestGui gui)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        gui.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ChestGui) {
            event.setCancelled(true);
        }
    }
}
