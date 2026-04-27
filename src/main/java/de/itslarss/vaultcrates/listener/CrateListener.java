package de.itslarss.vaultcrates.listener;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.api.events.CrateKeyUseEvent;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.crate.CrateLocation;
import de.itslarss.vaultcrates.crate.CrateManager;
import de.itslarss.vaultcrates.key.PhysicalKeyUtil;
import de.itslarss.vaultcrates.util.ColorUtil;
import de.itslarss.vaultcrates.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Handles all player interactions with crate blocks and crate furniture entities.
 *
 * <h3>Block crates</h3>
 * Vanilla blocks (e.g. ENDER_CHEST) placed and interacted with via standard Bukkit
 * block events ({@code BlockPlaceEvent}, {@code BlockBreakEvent},
 * {@code PlayerInteractEvent}).
 *
 * <h3>Furniture crates</h3>
 * ItemsAdder furniture entities (ArmorStand) whose base material is non-placeable
 * (e.g. PAPER).  Standard {@code BlockPlaceEvent} never fires for these items.
 * Instead:
 * <ol>
 *   <li>{@link #onFurnitureCratePlace} (LOWEST priority) intercepts
 *       {@code PlayerInteractEvent} when the player holds a furniture crate item,
 *       marks a pending placement, and lets ItemsAdder handle entity spawning.</li>
 *   <li>A 1-tick delayed task calls
 *       {@link CrateManager#resolvePendingFurniture} to find and tag the new
 *       {@link ArmorStand}.</li>
 *   <li>{@link #onEntityInteract} handles right-click opens and left-click previews
 *       on registered furniture entities.</li>
 *   <li>{@link #onEntityDamage} handles admin sneak+left-click breaking.</li>
 *   <li>{@link #onChunkLoad} re-indexes furniture entities when their chunk loads.</li>
 * </ol>
 */
public class CrateListener implements Listener {

    private final VaultCrates plugin;

    public CrateListener(VaultCrates plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // BLOCK CRATES — PlayerInteractEvent
    // =========================================================================

    /**
     * Handles left-click (preview) and right-click (open) on registered block crates.
     * Priority HIGH so other plugins (e.g. ItemsAdder) run before us.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        Crate crate = plugin.getCrateManager().getCrateAt(block.getLocation());
        if (crate == null) return;

        // Shift + left-click → let admins break the block; BlockBreakEvent handles cleanup
        if (event.getAction() == Action.LEFT_CLICK_BLOCK
                && player.isSneaking()
                && player.hasPermission("vaultcrates.admin")) {
            return;
        }

        event.setCancelled(true);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (crate.isPreviewEnabled()) {
                plugin.getGuiManager().openCratePreview(player, crate);
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handleCrateOpen(player, crate, block.getLocation());
        }
    }

    // =========================================================================
    // FURNITURE CRATES — Placement (PlayerInteractEvent, LOWEST)
    // =========================================================================

    /**
     * Intercepts right-click placement of furniture crate items <em>before</em>
     * ItemsAdder processes the event (IA typically uses NORMAL priority).
     *
     * <p>If the item in hand is a VaultCrates furniture crate item:
     * <ol>
     *   <li>Validates placement permission and proximity.</li>
     *   <li>Registers a pending placement in {@link CrateManager}.</li>
     *   <li>Schedules a 1-tick task to resolve the spawned ArmorStand.</li>
     *   <li>Does NOT cancel the event — IA must be allowed to spawn the furniture.</li>
     * </ol>
     * </p>
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onFurnitureCratePlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        // Check for vc_crate_name PDC on the held item
        NamespacedKey crateKey = new NamespacedKey(plugin, CrateManager.PDC_CRATE_NAME);
        if (!item.getItemMeta().getPersistentDataContainer()
                .has(crateKey, PersistentDataType.STRING)) return;

        String crateName = item.getItemMeta().getPersistentDataContainer()
                .get(crateKey, PersistentDataType.STRING);
        Crate crate = plugin.getCrateManager().getCrate(crateName);
        if (crate == null) return;

        // Only handle furniture crates here; block crates go through BlockPlaceEvent
        if (!crate.isFurnitureCrate()) return;

        Player player = event.getPlayer();

        // Permission check
        if (!player.hasPermission("vaultcrates.admin")) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "no-permission");
            return;
        }

        // Calculate expected furniture location: block adjacent to the clicked face
        BlockFace face = event.getBlockFace();
        Location targetLoc = clickedBlock.getRelative(face).getLocation();

        // Proximity check — prevent crates being too close together
        double radius = plugin.getConfigManager().getDouble("Settings.Crate_Radius", 10.0);
        if (radius > 0) {
            CrateManager cm = plugin.getCrateManager();
            for (CrateLocation cl : cm.getAllPlacements().values()) {
                if (!cm.isStillPlaced(cl)) continue;
                if (LocationUtil.isNearby(targetLoc, cl.getLocation(), radius)) {
                    event.setCancelled(true);
                    player.sendMessage(ColorUtil.colorize(
                            "&c[VC] Another crate is too close! (" + radius + " block radius)"));
                    return;
                }
            }
        }

        // Mark as pending — do NOT cancel the event so IA can spawn the furniture
        plugin.getCrateManager().addPendingFurniture(player.getUniqueId(), crateName, targetLoc);

        UUID playerUuid = player.getUniqueId();
        String crateNameFinal = crateName;

        // 1-tick task: find + register the newly spawned ArmorStand
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String placementId = plugin.getCrateManager().resolvePendingFurniture(playerUuid);
            if (placementId == null) {
                // ItemsAdder may have cancelled the placement (invalid surface etc.)
                plugin.getCrateManager().addPendingFurniture(playerUuid, null, null); // clean up
                return;
            }

            // Consume one item from the player's hand (furniture items are not blocks,
            // so Minecraft does not consume them automatically)
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getAmount() > 1) {
                    hand.setAmount(hand.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
            }

            player.sendMessage(ColorUtil.colorize("&7[&6VC&7] &a" + crateNameFinal
                    + " &aplaced! &7ID: &e" + placementId
                    + " &8(use &7/vc remove " + placementId + " &8to remove)"));

            // Execute placement commands
            for (String cmd : crate.getCommandsOnPlace()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        cmd.replace("{player_name}", player.getName()));
            }
        }, 1L);
    }

    // =========================================================================
    // FURNITURE CRATES — Interact (open / preview)
    // =========================================================================

    /**
     * Handles right-click and attack on registered furniture crate entities.
     *
     * <ul>
     *   <li>Right-click → open the crate.</li>
     *   <li>Left-click (attack) → open preview (handled via
     *       {@link EntityDamageByEntityEvent} below for sneak+left breaking; normal
     *       left-click previews use {@link PlayerInteractAtEntityEvent} which only
     *       fires on right-click — so we repurpose the damage event for that too).</li>
     * </ul>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onEntityInteract(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand)) return;

        Crate crate = plugin.getCrateManager().getCrateAtEntity(entity.getUniqueId());
        if (crate == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        handleCrateOpen(player, crate, entity.getLocation());
    }

    // =========================================================================
    // FURNITURE CRATES — Break / cleanup
    // =========================================================================

    /**
     * Handles two furniture crate entity interactions via damage events:
     *
     * <ul>
     *   <li><b>Admin sneak + left-click</b>: remove the crate registration and
     *       let IA break the furniture naturally (drop the item).</li>
     *   <li><b>Normal left-click</b>: open the reward preview (non-sneaking players).</li>
     *   <li><b>Any other damage</b>: cancel — furniture crates should not be damaged.</li>
     * </ul>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ArmorStand)) return;

        Crate crate = plugin.getCrateManager().getCrateAtEntity(entity.getUniqueId());
        if (crate == null) return;

        // Always cancel the raw damage — we handle everything manually
        event.setCancelled(true);

        if (!(event.getDamager() instanceof Player player)) return;

        if (player.isSneaking() && player.hasPermission("vaultcrates.admin")) {
            // Admin break: unregister, remove hologram, let IA handle the entity removal
            CrateLocation cl = plugin.getCrateManager()
                    .getCrateLocationAtEntity(entity.getUniqueId());
            String placementId = cl != null ? cl.getPlacementId() : "?";

            plugin.getCrateManager().removeCrateAtEntity(entity.getUniqueId());
            plugin.getHologramManager().removeHologram(
                    plugin.getHologramManager().locationToId(entity.getLocation()));

            player.sendMessage(ColorUtil.colorize(
                    "&7[&6VC&7] &aCrate &6" + crate.getName()
                    + " &aremoved. &8(ID was: &7" + placementId + "&8)"));

            // Kill the furniture ArmorStand so IA can drop the item back to the player
            entity.remove();

        } else {
            // Normal left-click → preview
            if (crate.isPreviewEnabled()) {
                plugin.getGuiManager().openCratePreview(player, crate);
            }
        }
    }

    /**
     * Cleans up furniture crate registration when a registered ArmorStand dies
     * (e.g. removed by {@code /kill}, a world-cleanup plugin, or similar).
     *
     * <p>Admin sneak+left-click is handled in {@link #onEntityDamage} which calls
     * {@code entity.remove()} — that does NOT fire {@code EntityDeathEvent}, so
     * it is cleaned up there directly.  This handler catches all other cases.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ArmorStand)) return;

        Crate crate = plugin.getCrateManager().getCrateAtEntity(entity.getUniqueId());
        if (crate == null) return;

        // Clean up silently — no admin message needed for external removal
        plugin.getCrateManager().removeCrateAtEntity(entity.getUniqueId());
        plugin.getHologramManager().removeHologram(
                plugin.getHologramManager().locationToId(entity.getLocation()));
    }

    // =========================================================================
    // FURNITURE CRATES — Chunk load (re-index entities)
    // =========================================================================

    /**
     * When a chunk loads, scans all entities in that chunk and re-indexes any
     * VaultCrates-tagged ArmorStands whose entity UUID was not yet resolved
     * (e.g. on first startup or after a plugin reload while the chunk was unloaded).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            plugin.getCrateManager().indexEntityIfCrate(entity);
        }
    }

    // =========================================================================
    // BLOCK CRATES — BlockBreakEvent
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        Crate crate = plugin.getCrateManager().getCrateAt(loc);
        if (crate == null) return;

        if (!event.getPlayer().hasPermission("vaultcrates.admin")) {
            event.setCancelled(true);
            plugin.getMessages().send(event.getPlayer(), "no-permission");
            return;
        }

        CrateLocation placement = plugin.getCrateManager().getCrateLocationAt(loc);
        String placementId = placement != null ? placement.getPlacementId() : "?";

        plugin.getCrateManager().removeCrateAt(loc);
        plugin.getHologramManager().removeHologram(
                plugin.getHologramManager().locationToId(loc));
        event.getPlayer().sendMessage(ColorUtil.colorize(
                "&7[&6VC&7] &aCrate &6" + crate.getName()
                + " &aremoved. &8(ID was: &7" + placementId + "&8)"));
    }

    // =========================================================================
    // BLOCK CRATES — BlockPlaceEvent
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || item.getType() == Material.AIR) return;
        if (!item.hasItemMeta()) return;

        NamespacedKey crateKey = new NamespacedKey(plugin, CrateManager.PDC_CRATE_NAME);
        if (!item.getItemMeta().getPersistentDataContainer()
                .has(crateKey, PersistentDataType.STRING)) return;

        String crateName = item.getItemMeta().getPersistentDataContainer()
                .get(crateKey, PersistentDataType.STRING);
        Crate crate = plugin.getCrateManager().getCrate(crateName);
        if (crate == null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtil.colorize(
                    "&c[VC] Crate config not found: &e" + crateName + "&c. Run &e/vc reload&c."));
            return;
        }

        // Furniture crates are handled by onFurnitureCratePlace — skip here
        if (crate.isFurnitureCrate()) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("vaultcrates.admin")) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "no-permission");
            return;
        }

        Location blockLoc = event.getBlock().getLocation();

        // Proximity check
        double radius = plugin.getConfigManager().getDouble("Settings.Crate_Radius", 10.0);
        if (radius > 0) {
            CrateManager cm = plugin.getCrateManager();
            for (CrateLocation cl : cm.getAllPlacements().values()) {
                if (!cm.isStillPlaced(cl)) continue;
                if (LocationUtil.isNearby(blockLoc, cl.getLocation(), radius)) {
                    event.setCancelled(true);
                    player.sendMessage(ColorUtil.colorize(
                            "&c[VC] Another crate is too close! (" + radius + " block radius)"));
                    return;
                }
            }
        }

        String placementId = plugin.getCrateManager().setCrateAt(blockLoc, crate);
        if (!crate.getHologramLines().isEmpty()) {
            plugin.getHologramManager().spawnHologram(
                    plugin.getHologramManager().locationToId(blockLoc),
                    blockLoc, crate.getHologramLines());
        }

        player.sendMessage(ColorUtil.colorize("&7[&6VC&7] &a" + crate.getName()
                + " &aplaced! &7ID: &e" + placementId
                + " &8(use &7/vc remove " + placementId + " &8to remove)"));

        for (String cmd : crate.getCommandsOnPlace()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("{player_name}", player.getName()));
        }
    }

    // =========================================================================
    // Key drop prevention
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!plugin.getConfigManager().getBoolean("Settings.Cant_Drop_Key", false)) return;
        if (PhysicalKeyUtil.isVaultCratesKey(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    // =========================================================================
    // Core open logic (shared by block and furniture crates)
    // =========================================================================

    private void handleCrateOpen(Player player, Crate crate, Location crateLoc) {
        if (!player.hasPermission("vaultcrates.open")) {
            plugin.getMessages().send(player, "no-permission");
            return;
        }

        if (plugin.getAnimationManager().isAnimating(player)) {
            plugin.getMessages().send(player, "crate-blocked");
            return;
        }

        if (plugin.getConfigManager().getBoolean("Settings.Cant_Open_Creative", false)
                && player.getGameMode() == GameMode.CREATIVE) {
            plugin.getMessages().send(player, "crate-blocked");
            return;
        }

        boolean usedVirtualKey = false;
        if (crate.getKeyConfig().isRequire()) {
            int keysNeeded = crate.getKeyConfig().getKeysRequired();

            boolean virtualEnabled  = plugin.getConfigManager().getBoolean("Keys.Virtual_Keys_Enabled", true);
            boolean physicalEnabled = plugin.getConfigManager().getBoolean("Keys.Physical_Keys_Enabled", true);
            boolean mustHoldInHand  = plugin.getConfigManager().getBoolean("Settings.Must_Hold_Key_In_Hand", false);

            int virtualKeys  = virtualEnabled
                    ? plugin.getKeyManager().getVirtualKeys(player.getUniqueId(), crate.getName()) : 0;
            int physicalKeys = physicalEnabled
                    ? plugin.getKeyManager().countPhysicalKeys(player, crate) : 0;

            boolean hasKey = false;

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
                if (crate.isUseEconomy() && plugin.hasVault() && crate.getEconomyPrice() > 0) {
                    double price = crate.getEconomyPrice();
                    if (plugin.getVaultHook().getBalance(player) < price) {
                        plugin.getMessages().send(player, "no-key", "{crate}", crate.getName());
                        return;
                    }
                    plugin.getVaultHook().withdraw(player, price);
                } else {
                    if (crate.isPushbackEnabled()) {
                        org.bukkit.util.Vector dir = player.getLocation().getDirection()
                                .multiply(-1.0).multiply(crate.getPushbackStrength()).setY(0.3);
                        player.setVelocity(dir);
                    }
                    plugin.getMessages().send(player, "no-key", "{crate}", crate.getName());
                    return;
                }
            }

            CrateKeyUseEvent keyEvent = new CrateKeyUseEvent(player, crate, usedVirtualKey);
            Bukkit.getPluginManager().callEvent(keyEvent);
            if (keyEvent.isCancelled()) return;

            if (usedVirtualKey) {
                plugin.getKeyManager().removeVirtualKeys(player.getUniqueId(), crate.getName(), keysNeeded);
            } else {
                plugin.getKeyManager().removePhysicalKey(player, crate, keysNeeded);
            }
        }

        if (player.isSneaking() && crate.isShiftInstantlyOpen()) {
            plugin.getAnimationManager().startAnimation(player, crate, crateLoc);
            return;
        }

        if (crate.isSelectableRewards()) {
            plugin.getGuiManager().openSelectableRewards(player, crate,
                    crate.getSelectableRewardsCount(), crateLoc);
            return;
        }

        crateLoc.getWorld().playSound(crateLoc, Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        plugin.getAnimationManager().startAnimation(player, crate, crateLoc);
    }
}
