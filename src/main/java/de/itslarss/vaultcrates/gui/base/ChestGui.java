package de.itslarss.vaultcrates.gui.base;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.util.ColorUtil;
import de.itslarss.vaultcrates.util.ItemBuilder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class for all VaultCrates chest GUI menus.
 * Handles inventory creation, item placement and click routing.
 */
public abstract class ChestGui implements InventoryHolder {

    protected final VaultCrates plugin;
    protected Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();

    protected ChestGui(VaultCrates plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Abstract contract
    // -------------------------------------------------------------------------

    /** Returns the GUI title (supports & colour codes). */
    public abstract String getTitle();

    /** Returns the inventory size (must be a multiple of 9, max 54). */
    public abstract int getSize();

    /** Fills the inventory with items. Called after the inventory is created. */
    public abstract void populate();

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Creates the inventory, calls {@link #populate()} and opens it for the player.
     */
    public void open(Player player) {
        this.handlers.clear();
        this.inventory = plugin.getServer().createInventory(this, getSize(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(getTitle()));
        populate();
        player.openInventory(inventory);
    }

    // -------------------------------------------------------------------------
    // Item placement
    // -------------------------------------------------------------------------

    /**
     * Places an item in the given slot with an optional click handler.
     */
    protected void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> handler) {
        if (inventory == null || slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        if (handler != null) handlers.put(slot, handler);
    }

    protected void setItem(int slot, ItemStack item) {
        setItem(slot, item, null);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Fills the border of the inventory with the given filler item. */
    protected void fillBorder(ItemStack filler) {
        int rows = getSize() / 9;
        for (int i = 0; i < 9; i++) setItem(i, filler);
        for (int i = getSize() - 9; i < getSize(); i++) setItem(i, filler);
        for (int row = 1; row < rows - 1; row++) {
            setItem(row * 9, filler);
            setItem(row * 9 + 8, filler);
        }
    }

    /** Fills all empty slots with the given filler item. */
    protected void fillEmpty(ItemStack filler) {
        for (int i = 0; i < getSize(); i++) {
            if (inventory.getItem(i) == null) setItem(i, filler);
        }
    }

    /** Returns the standard grey glass pane used as a filler. */
    protected ItemStack getFiller() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name("&r").build();
    }

    // -------------------------------------------------------------------------
    // Click handling
    // -------------------------------------------------------------------------

    /**
     * Handles a click event from {@link de.itslarss.vaultcrates.listener.GuiListener}.
     * Cancels the event and dispatches to the registered handler.
     */
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Consumer<InventoryClickEvent> handler = handlers.get(event.getSlot());
        if (handler != null) handler.accept(event);
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
