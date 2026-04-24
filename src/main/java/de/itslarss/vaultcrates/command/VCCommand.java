package de.itslarss.vaultcrates.command;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.pouch.Pouch;
import de.itslarss.vaultcrates.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Map;

/**
 * Executor for /vc (aliases: vaultcrates, crate).
 *
 * <p>Subcommands:</p>
 * <ul>
 *   <li>give &lt;player&gt; &lt;crate&gt; [amount] — give a physical crate key</li>
 *   <li>sgive &lt;player&gt; &lt;crate&gt; [amount] — give silently</li>
 *   <li>giveall &lt;crate&gt; [amount] — give key to all online players</li>
 *   <li>reload — reload all configs</li>
 *   <li>list — list all loaded crates</li>
 *   <li>errors — show config load errors</li>
 *   <li>editor &lt;crate&gt; — open GUI editor (in-game only)</li>
 *   <li>key give|sgive|giveall|set|balance|withdraw — virtual key management</li>
 *   <li>pouch give|sgive|giveall|list — pouch management</li>
 * </ul>
 */
public class VCCommand implements CommandExecutor {

    private final VaultCrates plugin;

    private static final String PREFIX = "&7[&6VC&7] ";

    public VCCommand(VaultCrates plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help"     -> sendHelp(sender);
            case "reload"   -> handleReload(sender);
            case "list"     -> handleList(sender);
            case "errors"   -> handleErrors(sender);
            case "give"     -> handleGive(sender, args, false);
            case "sgive"    -> handleSGive(sender, args);
            case "giveall"  -> handleGiveAll(sender, args);
            case "key"      -> handleKey(sender, args);
            case "pouch"    -> handlePouch(sender, args);
            case "editor"   -> handleEditor(sender, args);
            case "getcrate"  -> handleGetCrate(sender, args);
            case "create"    -> handleCreate(sender, args);
            case "keyeditor" -> handleKeyEditor(sender, args);
            case "shop"        -> handleShop(sender);
            case "linknpc"     -> handleLinkNpc(sender, args);
            case "remove"      -> handleRemove(sender, args);
            case "placements"  -> handlePlacements(sender);
            default            -> msg(sender, "&cUnknown sub-command. Use &e/vc help&c.");
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Help
    // -------------------------------------------------------------------------

    private void sendHelp(CommandSender sender) {
        String[] lines = {
            "&6&lVaultCrates &7— Command Reference",
            "&e/vc give <player> <crate> [amount]  &7— Give physical key",
            "&e/vc sgive <player> <crate> [amount] &7— Give silently",
            "&e/vc giveall <crate> [amount]         &7— Give to all online",
            "&e/vc reload                            &7— Reload configs",
            "&e/vc list                              &7— List all crates",
            "&e/vc errors                            &7— Show load errors",
            "&e/vc editor <crate>                   &7— Open GUI editor",
            "&e/vc getcrate <crate>                  &7— Get placeable crate block",
            "&e/vc create <name>                    &7— Create new crate in-game",
            "&e/vc keyeditor <crate>                &7— Open key editor GUI",
            "&e/vc shop                              &7— Open key shop",
            "&e/vc linknpc <crate>                  &7— Link looked-at NPC to crate",
            "&e/vc placements                        &7— List all placed crate instances",
            "&e/vc remove <id>                       &7— Remove a placed crate by ID",
            "&e/vc key <give|set|balance|...> ...   &7— Virtual key management",
            "&e/vc pouch <give|giveall|list> ...    &7— Pouch management"
        };
        for (String line : lines) msg(sender, line);
    }

    // -------------------------------------------------------------------------
    // reload
    // -------------------------------------------------------------------------

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("vaultcrates.admin")) { noPermission(sender); return; }
        plugin.reload();
        msg(sender, "&aPlugin reloaded successfully.");
    }

    // -------------------------------------------------------------------------
    // list
    // -------------------------------------------------------------------------

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("vaultcrates.admin")) { noPermission(sender); return; }
        Map<String, Crate> crates = plugin.getCrateManager().getCrates();
        if (crates.isEmpty()) {
            msg(sender, "&cNo crates loaded.");
            return;
        }
        msg(sender, "&6Loaded crates &7(" + crates.size() + "):");
        for (Map.Entry<String, Crate> entry : crates.entrySet()) {
            Crate c = entry.getValue();
            sender.sendMessage(ColorUtil.colorize(
                    PREFIX + "&e" + c.getName() + " &7— " +
                    c.getPrizes().size() + " rewards, animation: &b" + c.getAnimationType().name()));
        }
    }

    // -------------------------------------------------------------------------
    // errors
    // -------------------------------------------------------------------------

    private void handleErrors(CommandSender sender) {
        if (!sender.hasPermission("vaultcrates.admin")) { noPermission(sender); return; }
        java.util.List<String> errors = plugin.getCrateManager().getErrors();
        if (errors.isEmpty()) {
            msg(sender, "&aNo load errors found.");
            return;
        }
        msg(sender, "&cLoad errors &7(" + errors.size() + "):");
        for (String err : errors) {
            sender.sendMessage(ColorUtil.colorize("&c  - " + err));
        }
    }

    // -------------------------------------------------------------------------
    // give  (physical key)
    // -------------------------------------------------------------------------

    private void handleGive(CommandSender sender, String[] args, boolean silent) {
        if (!sender.hasPermission("vaultcrates.give")) { noPermission(sender); return; }
        if (args.length < 3) { usage(sender, "/vc give <player> <crate> [amount]"); return; }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { msg(sender, "&cPlayer &e" + args[1] + " &cnot found online."); return; }

        Crate crate = plugin.getCrateManager().getCrate(args[2]);
        if (crate == null) { msg(sender, "&cCrate &e" + args[2] + " &cnot found."); return; }

        int amount = parseAmount(sender, args, 3, 1);
        if (amount < 1) return;

        plugin.getKeyManager().createPhysicalKeys(crate, amount).forEach(key -> giveOrDrop(target, key));

        if (!silent) {
            msg(sender, "&aGave &e" + amount + "x &6" + crate.getName() + " &akey to &e" + target.getName() + "&a.");
            if (!sender.getName().equals(target.getName()))
                msg(target, "&aYou received &e" + amount + "x &6" + crate.getName() + " &akey!");
        }
    }

    private void handleSGive(CommandSender sender, String[] args) {
        handleGive(sender, args, true);
    }

    // -------------------------------------------------------------------------
    // giveall  (physical key to all online)
    // -------------------------------------------------------------------------

    private void handleGiveAll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vaultcrates.give")) { noPermission(sender); return; }
        if (args.length < 2) { usage(sender, "/vc giveall <crate> [amount]"); return; }

        Crate crate = plugin.getCrateManager().getCrate(args[1]);
        if (crate == null) { msg(sender, "&cCrate &e" + args[1] + " &cnot found."); return; }

        int amount = parseAmount(sender, args, 2, 1);
        if (amount < 1) return;

        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        for (Player p : online) {
            plugin.getKeyManager().createPhysicalKeys(crate, amount).forEach(key -> giveOrDrop(p, key));
            msg(p, "&aYou received &e" + amount + "x &6" + crate.getName() + " &akey!");
        }
        msg(sender, "&aGave &e" + amount + "x &6" + crate.getName() + " &akey to &e" + online.size() + " &aplayers.");
    }

    // -------------------------------------------------------------------------
    // key sub-command
    // -------------------------------------------------------------------------

    private void handleKey(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vaultcrates.key")) { noPermission(sender); return; }
        if (args.length < 2) {
            String[] help = {
                "&6/vc key give <player> <crate> [amount]",
                "&6/vc key sgive <player> <crate> [amount]",
                "&6/vc key giveall <crate> [amount]",
                "&6/vc key set <player> <crate> <amount>",
                "&6/vc key balance <player> <crate>",
                "&6/vc key withdraw <player> <crate> [amount]"
            };
            for (String h : help) msg(sender, h);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "give"     -> keyGive(sender, args, false);
            case "sgive"    -> keyGive(sender, args, true);
            case "giveall"  -> keyGiveAll(sender, args);
            case "set"      -> keySet(sender, args);
            case "balance"  -> keyBalance(sender, args);
            case "withdraw" -> keyWithdraw(sender, args);
            default -> msg(sender, "&cUnknown key sub-command.");
        }
    }

    private void keyGive(CommandSender sender, String[] args, boolean silent) {
        // args: key give <player> <crate> [amount]
        if (args.length < 4) { usage(sender, "/vc key give <player> <crate> [amount]"); return; }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { msg(sender, "&cPlayer not online."); return; }
        Crate crate = plugin.getCrateManager().getCrate(args[3]);
        if (crate == null) { msg(sender, "&cCrate not found."); return; }
        int amount = parseAmount(sender, args, 4, 1);
        if (amount < 1) return;

        plugin.getKeyManager().addVirtualKeys(target.getUniqueId(), crate.getName(), amount);
        if (!silent)
            msg(target, "&aYou received &e" + amount + "x &6" + crate.getName() + " &avirtual key(s)!");
        msg(sender, "&aGave &e" + amount + "x &evirtual &6" + crate.getName() + " &akeys to &e" + target.getName());
    }

    private void keyGiveAll(CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender, "/vc key giveall <crate> [amount]"); return; }
        Crate crate = plugin.getCrateManager().getCrate(args[2]);
        if (crate == null) { msg(sender, "&cCrate not found."); return; }
        int amount = parseAmount(sender, args, 3, 1);
        if (amount < 1) return;

        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        for (Player p : online) {
            plugin.getKeyManager().addVirtualKeys(p.getUniqueId(), crate.getName(), amount);
            msg(p, "&aYou received &e" + amount + "x &6" + crate.getName() + " &avirtual key(s)!");
        }
        msg(sender, "&aGave &e" + amount + "x virtual keys to &e" + online.size() + " players.");
    }

    private void keySet(CommandSender sender, String[] args) {
        if (args.length < 5) { usage(sender, "/vc key set <player> <crate> <amount>"); return; }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { msg(sender, "&cPlayer not online."); return; }
        Crate crate = plugin.getCrateManager().getCrate(args[3]);
        if (crate == null) { msg(sender, "&cCrate not found."); return; }
        int amount = parseAmount(sender, args, 4, -1);
        if (amount < 0) return;

        plugin.getKeyManager().setVirtualKeys(target.getUniqueId(), crate.getName(), amount);
        msg(sender, "&aSet &e" + target.getName() + "'s &6" + crate.getName() + " &akeys to &e" + amount + "&a.");
    }

    private void keyBalance(CommandSender sender, String[] args) {
        if (args.length < 4) { usage(sender, "/vc key balance <player> <crate>"); return; }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { msg(sender, "&cPlayer not online."); return; }
        Crate crate = plugin.getCrateManager().getCrate(args[3]);
        if (crate == null) { msg(sender, "&cCrate not found."); return; }

        int keys = plugin.getKeyManager().getVirtualKeys(target.getUniqueId(), crate.getName());
        msg(sender, "&e" + target.getName() + " &ahas &e" + keys + "x &6" + crate.getName() + " &avirtual key(s).");
    }

    private void keyWithdraw(CommandSender sender, String[] args) {
        if (args.length < 4) { usage(sender, "/vc key withdraw <player> <crate> [amount]"); return; }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { msg(sender, "&cPlayer not online."); return; }
        Crate crate = plugin.getCrateManager().getCrate(args[3]);
        if (crate == null) { msg(sender, "&cCrate not found."); return; }
        int amount = parseAmount(sender, args, 4, 1);
        if (amount < 1) return;

        int current = plugin.getKeyManager().getVirtualKeys(target.getUniqueId(), crate.getName());
        if (current < amount) {
            msg(sender, "&cNot enough keys (&e" + current + " &cavailable).");
            return;
        }
        plugin.getKeyManager().removeVirtualKeys(target.getUniqueId(), crate.getName(), amount);
        msg(sender, "&aWithdrew &e" + amount + "x &6" + crate.getName() + " &akeys from &e" + target.getName() + "&a.");
    }

    // -------------------------------------------------------------------------
    // pouch sub-command
    // -------------------------------------------------------------------------

    private void handlePouch(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vaultcrates.pouch")) { noPermission(sender); return; }
        if (args.length < 2) {
            msg(sender, "&6/vc pouch give <player> <pouch> [amount]");
            msg(sender, "&6/vc pouch sgive <player> <pouch> [amount]");
            msg(sender, "&6/vc pouch giveall <pouch> [amount]");
            msg(sender, "&6/vc pouch list");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "give"    -> pouchGive(sender, args, false);
            case "sgive"   -> pouchGive(sender, args, true);
            case "giveall" -> pouchGiveAll(sender, args);
            case "list"    -> pouchList(sender);
            default -> msg(sender, "&cUnknown pouch sub-command.");
        }
    }

    private void pouchGive(CommandSender sender, String[] args, boolean silent) {
        if (args.length < 4) { usage(sender, "/vc pouch give <player> <pouch> [amount]"); return; }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { msg(sender, "&cPlayer not online."); return; }
        Pouch pouch = plugin.getPouchManager().getPouch(args[3]);
        if (pouch == null) { msg(sender, "&cPouch not found."); return; }
        int amount = parseAmount(sender, args, 4, 1);
        if (amount < 1) return;

        ItemStack item = pouch.buildItem(amount);
        giveOrDrop(target, item);
        if (!silent)
            msg(target, "&aYou received &e" + amount + "x &6" + pouch.getDisplayName() + "&a!");
        msg(sender, "&aGave &e" + amount + "x &6" + pouch.getDisplayName() + " &ato &e" + target.getName() + "&a.");
    }

    private void pouchGiveAll(CommandSender sender, String[] args) {
        if (args.length < 3) { usage(sender, "/vc pouch giveall <pouch> [amount]"); return; }
        Pouch pouch = plugin.getPouchManager().getPouch(args[2]);
        if (pouch == null) { msg(sender, "&cPouch not found."); return; }
        int amount = parseAmount(sender, args, 3, 1);
        if (amount < 1) return;

        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        for (Player p : online) {
            ItemStack item = pouch.buildItem(amount);
            giveOrDrop(p, item);
            msg(p, "&aYou received &e" + amount + "x &6" + pouch.getDisplayName() + "&a!");
        }
        msg(sender, "&aGave &e" + amount + "x pouch to &e" + online.size() + " players.");
    }

    private void pouchList(CommandSender sender) {
        Map<String, Pouch> pouches = plugin.getPouchManager().getPouches();
        if (pouches.isEmpty()) { msg(sender, "&cNo pouches loaded."); return; }
        msg(sender, "&6Loaded pouches &7(" + pouches.size() + "):");
        for (Pouch p : pouches.values()) {
            sender.sendMessage(ColorUtil.colorize(PREFIX + "&e" + p.getName()
                    + " &7— " + p.getDisplayName()));
        }
    }

    // -------------------------------------------------------------------------
    // getcrate  (give placeable crate block)
    // -------------------------------------------------------------------------

    private void handleGetCrate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vaultcrates.admin")) { noPermission(sender); return; }
        if (!(sender instanceof Player player)) { msg(sender, "&cThis command is for players only."); return; }
        if (args.length < 2) { usage(sender, "/vc getcrate <crate>"); return; }

        Crate crate = plugin.getCrateManager().getCrate(args[1]);
        if (crate == null) { msg(sender, "&cCrate &e" + args[1] + " &cnot found."); return; }

        ItemStack crateItem = crate.buildCrateItem();
        giveOrDrop(player, crateItem);
        msg(player, "&aYou received the &6" + crate.getName()
                + " &acrate block. Place it to create a crate!");
    }

    // -------------------------------------------------------------------------
    // editor
    // -------------------------------------------------------------------------

    private void handleEditor(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vaultcrates.admin")) { noPermission(sender); return; }
        if (!(sender instanceof Player player)) { msg(sender, "&cThis command is for players only."); return; }
        if (args.length < 2) { usage(sender, "/vc editor <crate>"); return; }

        Crate crate = plugin.getCrateManager().getCrate(args[1]);
        if (crate == null) { msg(sender, "&cCrate &e" + args[1] + " &cnot found."); return; }

        plugin.getGuiManager().openCrateEditor(player, crate);
    }

    // -------------------------------------------------------------------------
    // create  (in-game crate creation)
    // -------------------------------------------------------------------------

    private void handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vaultcrates.admin")) { noPermission(sender); return; }
        if (args.length < 2) { usage(sender, "/vc create <name>"); return; }

        String crateName = args[1];
        java.io.File cratesDir = new java.io.File(plugin.getDataFolder(), "crates");
        java.io.File newFile   = new java.io.File(cratesDir, crateName + ".yml");

        if (newFile.exists()) {
            msg(sender, "&cCrate &e" + crateName + " &calready exists.");
            return;
        }
        if (!cratesDir.exists()) cratesDir.mkdirs();

        try (java.io.PrintWriter pw = new java.io.PrintWriter(
                new java.io.OutputStreamWriter(new java.io.FileOutputStream(newFile), java.nio.charset.StandardCharsets.UTF_8))) {
            pw.println("# VaultCrates — " + crateName + ".yml");
            pw.println("# Erstellt mit /vc create | Created with /vc create");
            pw.println();
            pw.println("Name: \"" + crateName + "\"");
            pw.println("Animation: ROUND");
            pw.println("AnimationVisibilityOption: PROXIMITY");
            pw.println("Proximity_Distance: 8");
            pw.println("Material: ENDER_CHEST");
            pw.println("# IAModel: \"namespace:block_id\"  # Optional: ItemsAdder crate block");
            pw.println("Enchanted: false");
            pw.println("OnlyPlaceable: true");
            pw.println("PreviewEnabled: true");
            pw.println("ShiftInstantlyOpen: false");
            pw.println("Size: 1");
            pw.println();
            pw.println("HologramLines:");
            pw.println("  - \"&6&l" + crateName + "\"");
            pw.println("  - \"&7Rechtsklick / Right-click to open\"");
            pw.println();
            pw.println("CommandsOnPlace: []");
            pw.println("FinalMessage: []");
            pw.println();
            pw.println("Lores:");
            pw.println("  - \"&7" + crateName + " Crate\"");
            pw.println();
            pw.println("KeyCrate:");
            pw.println("  Require: true");
            pw.println("  KeysRequired: 1");
            pw.println("  MatchNBT: true");
            pw.println("  MatchName: false");
            pw.println("  MatchLore: false");
            pw.println("  Material: TRIPWIRE_HOOK");
            pw.println("  # IAModel: \"namespace:key_id\"  # Optional: ItemsAdder key item");
            pw.println("  Name: \"&6&l" + crateName + " Key\"");
            pw.println("  Enchanted: true");
            pw.println("  Lores:");
            pw.println("    - \"&7Öffnet die &6" + crateName + " Crate&7.\"");
            pw.println();
            pw.println("Prizes:");
            pw.println("  example_reward:");
            pw.println("    Name: \"&a&lBeispiel-Belohnung\"");
            pw.println("    Rarity: gewohnlich");
            pw.println("    Chance: 100.0");
            pw.println("    Material: DIAMOND");
            pw.println("    Amount: 1");
            pw.println("    Glow: false");
            pw.println("    GiveItem: true");
            pw.println("    Lores: []");
            pw.println("    Enchantments: []");
            pw.println("    Commands: []");
            pw.println("    Permission: \"\"");
            pw.println();
            pw.println("BestPrizes: {}");
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to create crate " + crateName + ": " + ex.getMessage());
            msg(sender, "&cFailed to create crate. Check console.");
            return;
        }

        plugin.getCrateManager().reload();
        msg(sender, "&aCrate &e" + crateName + " &acreated! Use &e/vc getcrate "
                + crateName + " &ato get the block, &e/vc editor "
                + crateName + " &ato configure it.");
    }

    // -------------------------------------------------------------------------
    // shop
    // -------------------------------------------------------------------------

    private void handleShop(CommandSender sender) {
        if (!(sender instanceof Player player)) { msg(sender, "&cThis command is for players only."); return; }
        if (!sender.hasPermission("vaultcrates.shop")) { noPermission(sender); return; }
        if (!plugin.getConfigManager().getBoolean("Shop.Enabled", true)) {
            msg(sender, "&cThe key shop is currently disabled.");
            return;
        }
        plugin.getGuiManager().openShop(player);
    }

    // -------------------------------------------------------------------------
    // linknpc
    // -------------------------------------------------------------------------

    private void handleLinkNpc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { msg(sender, "&cThis command is for players only."); return; }
        if (!sender.hasPermission("vaultcrates.linknpc")) { noPermission(sender); return; }
        if (args.length < 2) { usage(sender, "/vc linknpc <crate>"); return; }

        Crate crate = plugin.getCrateManager().getCrate(args[1]);
        if (crate == null) { msg(sender, "&cCrate &e" + args[1] + " &cnot found."); return; }

        if (!plugin.getConfigManager().getBoolean("NPC.Enabled", true)) {
            msg(sender, "&cNPC support is disabled in config.yml.");
            return;
        }

        // Try to find a nearby NPC entity the player is looking at
        org.bukkit.entity.Entity target = findNearbyNpc(player);
        if (target == null) {
            msg(sender, "&cNo NPC found nearby. Stand close to an NPC and try again.");
            return;
        }

        plugin.getNpcManager().linkEntity(target.getUniqueId(), crate.getName());
        msg(sender, "&aLinked NPC &e" + target.getUniqueId() + " &ato crate &6" + crate.getName() + "&a.");
    }

    /** Finds the nearest entity within 3 blocks that is not a player. */
    private org.bukkit.entity.Entity findNearbyNpc(Player player) {
        org.bukkit.Location loc = player.getLocation();
        org.bukkit.entity.Entity nearest = null;
        double nearestDist = 4.0;
        for (org.bukkit.entity.Entity e : player.getNearbyEntities(3, 3, 3)) {
            if (e instanceof Player) continue;
            double dist = e.getLocation().distance(loc);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = e;
            }
        }
        return nearest;
    }

    // -------------------------------------------------------------------------
    // placements  —  list all placed crate instances with their IDs
    // -------------------------------------------------------------------------

    private void handlePlacements(CommandSender sender) {
        if (!sender.hasPermission("vaultcrates.admin")) { noPermission(sender); return; }

        Map<String, de.itslarss.vaultcrates.crate.CrateLocation> placements =
                plugin.getCrateManager().getAllPlacements();

        if (placements.isEmpty()) {
            msg(sender, "&7No crates are currently placed in the world.");
            return;
        }

        msg(sender, "&6Placed crate instances &7(" + placements.size() + ")"
                + " &8— use &7/vc remove <id> &8to remove:");
        for (Map.Entry<String, de.itslarss.vaultcrates.crate.CrateLocation> entry
                : placements.entrySet()) {
            de.itslarss.vaultcrates.crate.CrateLocation cl = entry.getValue();
            org.bukkit.Location loc = cl.getLocation();
            String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "?";
            sender.sendMessage(ColorUtil.colorize(
                    PREFIX + "&e" + entry.getKey()
                    + " &7→ &6" + cl.getCrateName()
                    + " &8@ &7" + worldName
                    + " &8[" + loc.getBlockX() + "&8, " + loc.getBlockY() + "&8, " + loc.getBlockZ() + "&8]"));
        }
    }

    // -------------------------------------------------------------------------
    // remove  —  remove a placed crate instance by ID
    // -------------------------------------------------------------------------

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vaultcrates.admin")) { noPermission(sender); return; }
        if (args.length < 2) {
            usage(sender, "/vc remove <placement-id>");
            msg(sender, "&7Tip: use &e/vc placements &7to list all IDs.");
            return;
        }

        String id = args[1].toLowerCase();
        de.itslarss.vaultcrates.crate.CrateLocation cl =
                plugin.getCrateManager().getPlacement(id);

        if (cl == null) {
            msg(sender, "&cNo placement found with ID &e" + id + "&c.");
            msg(sender, "&7Use &e/vc placements &7to see all placed crates and their IDs.");
            return;
        }

        org.bukkit.Location loc = cl.getLocation();
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "?";

        // Remove placement data and hologram
        plugin.getCrateManager().removePlacement(id);
        plugin.getHologramManager().removeHologram(
                plugin.getHologramManager().locationToId(loc));

        msg(sender, "&aCrate &6" + cl.getCrateName()
                + " &a(ID: &e" + id + "&a) removed from &7"
                + worldName + " &8[" + loc.getBlockX() + ", "
                + loc.getBlockY() + ", " + loc.getBlockZ() + "&8]&a.");
        msg(sender, "&7Note: the physical block was &enot &7broken — "
                + "break it manually or it will be treated as a normal block.");
    }

    // -------------------------------------------------------------------------
    // keyeditor
    // -------------------------------------------------------------------------

    private void handleKeyEditor(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vaultcrates.admin")) { noPermission(sender); return; }
        if (!(sender instanceof Player player)) { msg(sender, "&cThis command is for players only."); return; }
        if (args.length < 2) { usage(sender, "/vc keyeditor <crate>"); return; }

        Crate crate = plugin.getCrateManager().getCrate(args[1]);
        if (crate == null) { msg(sender, "&cCrate &e" + args[1] + " &cnot found."); return; }

        plugin.getGuiManager().openKeyEditor(player, crate);
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(leftOver -> player.getWorld().dropItemNaturally(player.getLocation(), leftOver));
    }

    /** Parses an integer from args[index], returning {@code defaultValue} if missing or invalid. */
    private int parseAmount(CommandSender sender, String[] args, int index, int defaultValue) {
        if (args.length <= index) return defaultValue;
        try {
            int v = Integer.parseInt(args[index]);
            if (v < 1) { msg(sender, "&cAmount must be at least 1."); return -1; }
            return v;
        } catch (NumberFormatException e) {
            msg(sender, "&c&e" + args[index] + " &cis not a valid number.");
            return -1;
        }
    }

    private void msg(CommandSender sender, String text) {
        sender.sendMessage(ColorUtil.colorize(PREFIX + text));
    }

    private void noPermission(CommandSender sender) {
        msg(sender, "&cYou don't have permission to do this.");
    }

    private void usage(CommandSender sender, String usage) {
        msg(sender, "&cUsage: &e" + usage);
    }
}
