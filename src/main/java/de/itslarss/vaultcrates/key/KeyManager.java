package de.itslarss.vaultcrates.key;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.storage.backend.StorageBackend;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Central manager for both virtual and physical crate keys.
 * All persistence is delegated to the active {@link StorageBackend} via
 * {@link de.itslarss.vaultcrates.storage.StorageManager}.
 */
public class KeyManager {

    private final VaultCrates plugin;

    // Lightweight adapters that expose the old VirtualKeyStorage / UsedKeyStorage API
    // while routing all calls to the unified backend.
    private final VirtualKeyAdapter virtualKeyStorageAdapter;
    private final UsedKeyAdapter    usedKeyStorageAdapter;

    public KeyManager(VaultCrates plugin) {
        this.plugin = plugin;
        this.virtualKeyStorageAdapter = new VirtualKeyAdapter();
        this.usedKeyStorageAdapter    = new UsedKeyAdapter();
    }

    /** Called on plugin enable (StorageManager is already fully initialised). */
    public void load() {
        int expiryDays = plugin.getConfigManager().getInt("Storage.KeyUuidExpiryDays", 365);
        backend().pruneOldKeys(expiryDays);
    }

    /** Called on plugin disable — the backend flush is handled by StorageManager.saveAll(). */
    public void save() {
        // StorageManager.saveAll() is called separately in VaultCrates#onDisable
    }

    // -------------------------------------------------------------------------
    // Virtual keys
    // -------------------------------------------------------------------------

    public int getVirtualKeys(UUID playerId, String crateName) {
        return backend().getVirtualKeys(playerId, crateName);
    }

    public void setVirtualKeys(UUID playerId, String crateName, int amount) {
        backend().setVirtualKeys(playerId, crateName, amount);
    }

    public void addVirtualKeys(UUID playerId, String crateName, int amount) {
        backend().addVirtualKeys(playerId, crateName, amount);
    }

    /**
     * Removes virtual keys from a player's balance.
     *
     * @return {@code false} if the player does not have enough keys
     */
    public boolean removeVirtualKeys(UUID playerId, String crateName, int amount) {
        return backend().removeVirtualKeys(playerId, crateName, amount);
    }

    public boolean hasVirtualKey(UUID playerId, String crateName) {
        return getVirtualKeys(playerId, crateName) > 0;
    }

    public Map<String, Integer> getAllVirtualKeys(UUID playerId) {
        return plugin.getStorageManager().getVirtualKeyStorage().getAllKeys(playerId);
    }

    // -------------------------------------------------------------------------
    // Physical keys
    // -------------------------------------------------------------------------

    /**
     * Returns the total number of valid (non-duped) physical key items for the
     * given crate in the player's inventory.
     */
    public int countPhysicalKeys(Player player, Crate crate) {
        int count = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || !PhysicalKeyUtil.matchesKey(item, crate)) continue;

            UUID keyUuid = PhysicalKeyUtil.getKeyUuid(item);
            if (keyUuid != null && backend().isKeyUsed(keyUuid)) {
                player.getInventory().setItem(i, null);
                plugin.getLogger().warning("Removed already-used key UUID " + keyUuid
                        + " from " + player.getName() + "'s inventory (anti-dupe).");
                continue;
            }
            count += item.getAmount();
        }
        return count;
    }

    /**
     * Removes {@code amount} physical key items for the given crate from the
     * player's inventory and marks their UUIDs as used.
     *
     * @return {@code true} if enough valid keys were found and removed
     */
    public boolean removePhysicalKey(Player player, Crate crate, int amount) {
        if (countPhysicalKeys(player, crate) < amount) return false;

        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !PhysicalKeyUtil.matchesKey(item, crate)) continue;

            UUID keyUuid = PhysicalKeyUtil.getKeyUuid(item);
            if (keyUuid != null && backend().isKeyUsed(keyUuid)) {
                player.getInventory().setItem(i, null);
                continue;
            }

            if (keyUuid != null) backend().markKeyUsed(keyUuid);

            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
        }
        return remaining == 0;
    }

    /**
     * Creates {@code amount} individual physical key items, each with its own UUID.
     */
    public List<ItemStack> createPhysicalKeys(Crate crate, int amount) {
        List<ItemStack> keys = new ArrayList<>(amount);
        for (int i = 0; i < amount; i++) {
            keys.add(PhysicalKeyUtil.createKey(crate));
        }
        return keys;
    }

    /** Creates a single physical key item. */
    public ItemStack createPhysicalKey(Crate crate) {
        return PhysicalKeyUtil.createKey(crate);
    }

    public boolean isPhysicalKey(ItemStack item) {
        return PhysicalKeyUtil.isVaultCratesKey(item);
    }

    public String getCrateNameFromKey(ItemStack item) {
        return PhysicalKeyUtil.getCrateName(item);
    }

    // -------------------------------------------------------------------------
    // Backwards-compatible accessors
    // -------------------------------------------------------------------------

    /**
     * Returns an adapter that exposes the old {@link VirtualKeyStorage} API,
     * routing all calls through the active storage backend.
     */
    public VirtualKeyAdapter getVirtualKeyStorage() {
        return virtualKeyStorageAdapter;
    }

    /**
     * Returns an adapter that exposes the old {@link UsedKeyStorage} API,
     * routing all calls through the active storage backend.
     */
    public UsedKeyAdapter getUsedKeyStorage() {
        return usedKeyStorageAdapter;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private StorageBackend backend() {
        return plugin.getStorageManager().getBackend();
    }

    // =========================================================================
    // Inner adapters
    // =========================================================================

    /**
     * Drop-in replacement for the old {@link VirtualKeyStorage} class.
     * Routes all calls to the active {@link StorageBackend}.
     */
    public class VirtualKeyAdapter {

        public int getKeys(UUID playerId, String crateName) {
            return backend().getVirtualKeys(playerId, crateName);
        }

        public void setKeys(UUID playerId, String crateName, int amount) {
            backend().setVirtualKeys(playerId, crateName, amount);
        }

        public void addKeys(UUID playerId, String crateName, int amount) {
            backend().addVirtualKeys(playerId, crateName, amount);
        }

        public boolean removeKeys(UUID playerId, String crateName, int amount) {
            return backend().removeVirtualKeys(playerId, crateName, amount);
        }

        public Map<String, Integer> getAllKeys(UUID playerId) {
            return plugin.getStorageManager().getVirtualKeyStorage().getAllKeys(playerId);
        }
    }

    /**
     * Drop-in replacement for the old {@link UsedKeyStorage} class.
     * Routes all calls to the active {@link StorageBackend}.
     */
    public class UsedKeyAdapter {

        public boolean isUsed(UUID keyUuid)  { return backend().isKeyUsed(keyUuid); }
        public void    markUsed(UUID keyUuid) { backend().markKeyUsed(keyUuid); }
        public int     size()                 { return 0; } // not meaningful for SQL backends
    }
}
