package de.itslarss.vaultcrates.listener;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.api.events.CrateKeyUseEvent;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.key.PhysicalKeyUtil;
import de.itslarss.vaultcrates.util.ColorUtil;
import de.itslarss.vaultcrates.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Handles all player interactions with crate blocks.
 */
public class CrateListener implements Listener {

    private final VaultCrates plugin;

    public CrateListener(VaultCrates plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Player right-click on a crate block
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only process main hand to avoid double-firing
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block == null) return;

        Crate crate = plugin.getCrateManager().getCrateAt(block.getLocation());
        if (crate == null) return;

        // Shift + left-click: let admins break the crate block normally.
        // The BlockBreakEvent handler below will take care of cleanup.
        if (event.getAction() == Action.LEFT_CLICK_BLOCK
                && player.isSneaking()
                && player.hasPermission("vaultcrates.admin")) {
            return; // do NOT cancel — allow BlockBreakEvent to fire
        }

        event.setCancelled(true);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Preview
            if (crate.isPreviewEnabled()) {
                plugin.getGuiManager().openCratePreview(player, crate);
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handleCrateOpen(player, crate, block.getLocation());
        }
    }

    // -------------------------------------------------------------------------
    // Core open logic
    // -------------------------------------------------------------------------

    private void handleCrateOpen(Player player, Crate crate, Location crateLoc) {
        // Permission check
        if (!player.hasPermission("vaultcrates.open")) {
            plugin.getMessages().send(player, "no-permission");
            return;
        }

        // Already animating
        if (plugin.getAnimationManager().isAnimating(player)) {
            plugin.getMessages().send(player, "crate-blocked");
            return;
        }

        // Creative block (if configured)
        if (plugin.getConfigManager().getBoolean("Settings.Cant_Open_Creative", false)
                && player.getGameMode() == GameMode.CREATIVE) {
            plugin.getMessages().send(player, "crate-blocked");
            return;
        }

        // Key requirement
        boolean usedVirtualKey = false;
        if (crate.getKeyConfig().isRequire()) {
            int keysNeeded = crate.getKeyConfig().getKeysRequired();

            // Determine key type
            boolean virtualEnabled = plugin.getConfigManager().getBoolean("Keys.Virtual_Keys_Enabled", true);
            boolean physicalEnabled = plugin.getConfigManager().getBoolean("Keys.Physical_Keys_Enabled", true);
            boolean mustHoldInHand = plugin.getConfigManager().getBoolean("Settings.Must_Hold_Key_In_Hand", false);

            int virtualKeys = virtualEnabled
                    ? plugin.getKeyManager().getVirtualKeys(player.getUniqueId(), crate.getName()) : 0;
            int physicalKeys = physicalEnabled
                    ? plugin.getKeyManager().countPhysicalKeys(player, crate) : 0;

            boolean hasKey = false;

            // Must hold in hand check
            if (mustHoldInHand && physicalEnabled) {
                ItemStack inHand = player.getInventory().getItemInMainHand();
                hasKey = PhysicalKeyUtil.matchesKey(inHand, crate) && physicalKeys >= keysNeeded;
            } else if (physicalKeys >= keysNeeded && physicalEnabled) {
                hasKey = true;
            } else if (virtualKeys >= keysNeeded && virtualEnabled) {
                hasKey = true;
                usedVirtualKey = true;
            }

            if (!hasKey) {
                // Economy fallback — pay to open without key
                if (crate.isUseEconomy() && plugin.hasVault() && crate.getEconomyPrice() > 0) {
                    double price = crate.getEconomyPrice();
                    if (plugin.getVaultHook().getBalance(player) < price) {
                        plugin.getMessages().send(player, "no-key");
                        return;
                    }
                    plugin.getVaultHook().withdraw(player, price);
                    // Continue without key
                } else {
                    if (crate.isPushbackEnabled()) {
                        org.bukkit.util.Vector dir = player.getLocation().getDirection().multiply(-1.0)
                                .multiply(crate.getPushbackStrength()).setY(0.3);
                        player.setVelocity(dir);
                    }
                    plugin.getMessages().send(player, "no-key");
                    return;
                }
            }

            // Fire key use event
            CrateKeyUseEvent keyEvent = new CrateKeyUseEvent(player, crate, usedVirtualKey);
            Bukkit.getPluginManager().callEvent(keyEvent);
            if (keyEvent.isCancelled()) return;

            // Consume key
            if (usedVirtualKey) {
                plugin.getKeyManager().removeVirtualKeys(player.getUniqueId(), crate.getName(), keysNeeded);
            } else {
                plugin.getKeyManager().removePhysicalKey(player, crate, keysNeeded);
            }
        }

        // Shift instant open
        if (player.isSneaking() && crate.isShiftInstantlyOpen()) {
            plugin.getAnimationManager().startAnimation(player, crate, crateLoc);
            return;
        }

        // Selectable rewards — open GUI instead of animation
        if (crate.isSelectableRewards()) {
            plugin.getGuiManager().openSelectableRewards(player, crate, crate.getSelectableRewardsCount(), crateLoc);
            return;
        }

        // Play open sound
        crateLoc.getWorld().playSound(crateLoc, Sound.BLOCK_CHEST_OPEN, 1f, 1f);

        // Start animation
        plugin.getAnimationManager().startAnimation(player, crate, crateLoc);
    }

    // -------------------------------------------------------------------------
    // Crate block breaking
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        Crate crate = plugin.getCrateManager().getCrateAt(loc);
        if (crate == null) return;

        // Prevent breaking without permission
        if (!event.getPlayer().hasPermission("vaultcrates.admin")) {
            event.setCancelled(true);
            plugin.getMessages().send(event.getPlayer(), "no-permission");
            return;
        }

        // Look up the placement ID before removing (for the feedback message)
        de.itslarss.vaultcrates.crate.CrateLocation placement =
                plugin.getCrateManager().getCrateLocationAt(loc);
        String placementId = placement != null ? placement.getPlacementId() : "?";

        // Remove crate registration and hologram
        plugin.getCrateManager().removeCrateAt(loc);
        plugin.getHologramManager().removeHologram(plugin.getHologramManager().locationToId(loc));
        event.getPlayer().sendMessage(ColorUtil.colorize(
                "&7[&6VC&7] &aCrate &6" + crate.getName()
                + " &aremoved. &8(ID was: &7" + placementId + "&8)"));
    }

    // -------------------------------------------------------------------------
    // Placing a crate block (from inventory with PDC)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || item.getType() == Material.AIR) return;
        if (!item.hasItemMeta()) return;

        // Check if item has vc_crate PDC
        org.bukkit.NamespacedKey crateKey = new org.bukkit.NamespacedKey(plugin, "vc_crate_name");
        if (!item.getItemMeta().getPersistentDataContainer()
                .has(crateKey, org.bukkit.persistence.PersistentDataType.STRING)) return;

        String crateName = item.getItemMeta().getPersistentDataContainer()
                .get(crateKey, org.bukkit.persistence.PersistentDataType.STRING);
        Crate crate = plugin.getCrateManager().getCrate(crateName);
        if (crate == null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtil.colorize(
                    "&c[VC] Crate config not found: &e" + crateName + "&c. Run &e/vc reload&c."));
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("vaultcrates.admin")) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "no-permission");
            return;
        }

        Location blockLoc = event.getBlock().getLocation();

        // Radius check
        double radius = plugin.getConfigManager().getDouble("Settings.Crate_Radius", 10.0);
        for (Location existing : plugin.getCrateManager().getCrateLocations().keySet()) {
            if (LocationUtil.isNearby(blockLoc, existing, radius)) {
                event.setCancelled(true);
                player.sendMessage(ColorUtil.colorize("&c[VC] Another crate is too close! (" + radius + " block radius)"));
                return;
            }
        }

        // Register crate — setCrateAt returns the unique placement ID
        String placementId = plugin.getCrateManager().setCrateAt(blockLoc, crate);
        if (!crate.getHologramLines().isEmpty()) {
            plugin.getHologramManager().spawnHologram(
                    plugin.getHologramManager().locationToId(blockLoc),
                    blockLoc, crate.getHologramLines());
        }

        player.sendMessage(ColorUtil.colorize("&7[&6VC&7] &a" + crate.getName()
                + " &aplaced! &7ID: &e" + placementId
                + " &8(use &7/vc remove " + placementId + " &8to remove)"));

        // Execute placement commands
        for (String cmd : crate.getCommandsOnPlace()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("{player_name}", player.getName()));
        }
    }

    // -------------------------------------------------------------------------
    // Key drop prevention
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!plugin.getConfigManager().getBoolean("Settings.Cant_Drop_Key", false)) return;
        if (PhysicalKeyUtil.isVaultCratesKey(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }
}
