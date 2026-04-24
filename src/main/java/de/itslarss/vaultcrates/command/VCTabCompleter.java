package de.itslarss.vaultcrates.command;

import de.itslarss.vaultcrates.VaultCrates;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tab-completion for the /vc command tree.
 */
public class VCTabCompleter implements TabCompleter {

    private final VaultCrates plugin;

    private static final List<String> ROOT_SUBS = Arrays.asList(
            "give", "sgive", "giveall", "reload", "list", "errors",
            "key", "pouch", "editor", "getcrate", "create", "keyeditor",
            "shop", "linknpc", "placements", "remove", "help"
    );
    private static final List<String> KEY_SUBS = Arrays.asList(
            "give", "sgive", "giveall", "set", "balance", "withdraw"
    );
    private static final List<String> POUCH_SUBS = Arrays.asList(
            "give", "sgive", "giveall", "list"
    );

    public VCTabCompleter(VaultCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Root sub-commands
            return filter(ROOT_SUBS, args[0]);
        }

        String sub = args[0].toLowerCase();

        // /vc give <player> <crate> [amount]
        // /vc sgive <player> <crate> [amount]
        if ((sub.equals("give") || sub.equals("sgive")) && sender.hasPermission("vaultcrates.give")) {
            if (args.length == 2) return onlinePlayers(args[1]);
            if (args.length == 3) return crateNames(args[2]);
            if (args.length == 4) return filter(List.of("1", "5", "10", "64"), args[3]);
        }

        // /vc giveall <crate> [amount]
        if (sub.equals("giveall") && sender.hasPermission("vaultcrates.give")) {
            if (args.length == 2) return crateNames(args[1]);
            if (args.length == 3) return filter(List.of("1", "5", "10"), args[2]);
        }

        // /vc reload
        if (sub.equals("reload") && args.length == 1) return completions;

        // /vc list / errors / help — no further args
        if (sub.equals("list") || sub.equals("errors") || sub.equals("help")) return completions;

        // /vc editor <crate>
        if (sub.equals("editor") && sender.hasPermission("vaultcrates.admin")) {
            if (args.length == 2) return crateNames(args[1]);
        }

        // /vc getcrate <crate>
        if (sub.equals("getcrate") && sender.hasPermission("vaultcrates.admin")) {
            if (args.length == 2) return crateNames(args[1]);
        }

        // /vc create <name>  — no completions (free text)
        if (sub.equals("create")) return completions;

        // /vc keyeditor <crate>
        if (sub.equals("keyeditor") && sender.hasPermission("vaultcrates.admin")) {
            if (args.length == 2) return crateNames(args[1]);
        }

        // /vc key <sub> ...
        if (sub.equals("key") && sender.hasPermission("vaultcrates.key")) {
            if (args.length == 2) return filter(KEY_SUBS, args[1]);

            String keySub = args[1].toLowerCase();
            // /vc key give <player> <crate> [amount]
            // /vc key sgive <player> <crate> [amount]
            if (keySub.equals("give") || keySub.equals("sgive")) {
                if (args.length == 3) return onlinePlayers(args[2]);
                if (args.length == 4) return crateNames(args[3]);
                if (args.length == 5) return filter(List.of("1", "5", "10", "64"), args[4]);
            }
            // /vc key giveall <crate> [amount]
            if (keySub.equals("giveall")) {
                if (args.length == 3) return crateNames(args[2]);
                if (args.length == 4) return filter(List.of("1", "5", "10"), args[3]);
            }
            // /vc key set <player> <crate> <amount>
            if (keySub.equals("set")) {
                if (args.length == 3) return onlinePlayers(args[2]);
                if (args.length == 4) return crateNames(args[3]);
                if (args.length == 5) return filter(List.of("0", "1", "5", "10"), args[4]);
            }
            // /vc key balance <player> <crate>
            if (keySub.equals("balance")) {
                if (args.length == 3) return onlinePlayers(args[2]);
                if (args.length == 4) return crateNames(args[3]);
            }
            // /vc key withdraw <player> <crate> [amount]
            if (keySub.equals("withdraw")) {
                if (args.length == 3) return onlinePlayers(args[2]);
                if (args.length == 4) return crateNames(args[3]);
                if (args.length == 5) return filter(List.of("1", "5", "10"), args[4]);
            }
        }

        // /vc shop — no extra args
        if (sub.equals("shop")) return completions;

        // /vc placements — no extra args
        if (sub.equals("placements")) return completions;

        // /vc remove <placement-id>
        if (sub.equals("remove") && sender.hasPermission("vaultcrates.admin")) {
            if (args.length == 2) {
                List<String> ids = new ArrayList<>(
                        plugin.getCrateManager().getAllPlacements().keySet());
                return filter(ids, args[1]);
            }
        }

        // /vc linknpc <crate>
        if (sub.equals("linknpc") && sender.hasPermission("vaultcrates.linknpc")) {
            if (args.length == 2) return crateNames(args[1]);
        }

        // /vc pouch <sub> ...
        if (sub.equals("pouch") && sender.hasPermission("vaultcrates.pouch")) {
            if (args.length == 2) return filter(POUCH_SUBS, args[1]);

            String pouchSub = args[1].toLowerCase();
            if (pouchSub.equals("give") || pouchSub.equals("sgive")) {
                if (args.length == 3) return onlinePlayers(args[2]);
                if (args.length == 4) return pouchNames(args[3]);
                if (args.length == 5) return filter(List.of("1", "5", "10", "64"), args[4]);
            }
            if (pouchSub.equals("giveall")) {
                if (args.length == 3) return pouchNames(args[2]);
                if (args.length == 4) return filter(List.of("1", "5", "10"), args[3]);
            }
            // list has no extra args
        }

        return completions;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<String> filter(List<String> options, String partial) {
        String lower = partial.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    private List<String> onlinePlayers(String partial) {
        String lower = partial.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    private List<String> crateNames(String partial) {
        return filter(new ArrayList<>(plugin.getCrateManager().getCrates().keySet()), partial);
    }

    private List<String> pouchNames(String partial) {
        return filter(new ArrayList<>(plugin.getPouchManager().getPouches().keySet()), partial);
    }
}
