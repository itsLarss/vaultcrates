package de.itslarss.vaultcrates.api;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.crate.CrateManager;
import de.itslarss.vaultcrates.key.KeyManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API facade for VaultCrates.
 * Other plugins can use this to interact with the crate system without depending on internal classes.
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * VaultCratesAPI api = VaultCratesAPI.get();
 * int keys = api.getVirtualKeys(player.getUniqueId(), "ExampleRound");
 * }</pre>
 */
public class VaultCratesAPI {

    private static VaultCratesAPI instance;
    private final VaultCrates plugin;

    private VaultCratesAPI(VaultCrates plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialises the API singleton. Called internally by the main class.
     */
    public static void init(VaultCrates plugin) {
        instance = new VaultCratesAPI(plugin);
    }

    /**
     * Returns the shared API instance.
     *
     * @throws IllegalStateException if VaultCrates is not loaded
     */
    public static VaultCratesAPI get() {
        if (instance == null) throw new IllegalStateException("VaultCrates is not loaded!");
        return instance;
    }

    // -------------------------------------------------------------------------
    // Virtual keys
    // -------------------------------------------------------------------------

    /**
     * Returns the number of virtual keys a player has for a specific crate.
     *
     * @param playerId  the player's UUID
     * @param crateName the crate name (case-insensitive)
     * @return the key count (0 if none)
     */
    public int getVirtualKeys(UUID playerId, String crateName) {
        return plugin.getKeyManager().getVirtualKeys(playerId, crateName);
    }

    /**
     * Sets the virtual key count for a player and crate.
     */
    public void setVirtualKeys(UUID playerId, String crateName, int amount) {
        plugin.getKeyManager().setVirtualKeys(playerId, crateName, amount);
    }

    /**
     * Adds virtual keys to a player's balance.
     */
    public void addVirtualKeys(UUID playerId, String crateName, int amount) {
        plugin.getKeyManager().addVirtualKeys(playerId, crateName, amount);
    }

    /**
     * Removes virtual keys from a player's balance.
     *
     * @return {@code false} if the player does not have enough keys
     */
    public boolean removeVirtualKeys(UUID playerId, String crateName, int amount) {
        return plugin.getKeyManager().removeVirtualKeys(playerId, crateName, amount);
    }

    // -------------------------------------------------------------------------
    // Physical keys
    // -------------------------------------------------------------------------

    /**
     * Gives physical key items to a player.
     * If the player's inventory is full the items are dropped at their feet.
     *
     * @param player    the recipient
     * @param crateName the crate name
     * @param amount    the number of key items
     */
    public void givePhysicalKey(Player player, String crateName, int amount) {
        Crate crate = plugin.getCrateManager().getCrate(crateName);
        if (crate == null) return;
        plugin.getKeyManager().createPhysicalKeys(crate, amount).forEach(key -> giveOrDrop(player, key));
    }

    // -------------------------------------------------------------------------
    // Crate opening
    // -------------------------------------------------------------------------

    /**
     * Attempts to open a crate for a player at a given location.
     * Performs all standard checks (key requirement, animation limit, etc.).
     *
     * @param player    the player
     * @param crateName the crate name
     * @param loc       the crate block location
     * @return {@code true} if the animation started successfully
     */
    public boolean openCrate(Player player, String crateName, Location loc) {
        Crate crate = plugin.getCrateManager().getCrate(crateName);
        if (crate == null || plugin.getAnimationManager().isAnimating(player)) return false;
        plugin.getAnimationManager().startAnimation(player, crate, loc);
        return true;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns a crate by name (case-insensitive).
     */
    public Optional<Crate> getCrate(String name) {
        return Optional.ofNullable(plugin.getCrateManager().getCrate(name));
    }

    public CrateManager getCrateManager() { return plugin.getCrateManager(); }
    public KeyManager getKeyManager() { return plugin.getKeyManager(); }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack drop : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }
}
