package de.itslarss.vaultcrates.gui;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.gui.base.ChestGui;
import de.itslarss.vaultcrates.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Confirmation GUI that lets a player finalise buying a crate key from the shop.
 *
 * <p>Layout (3 rows / 27 slots):</p>
 * <pre>
 *  [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
 *  [ ] [✔] [ ] [KEY][KEY] [ ] [✘] [ ] [ ]
 *  [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
 * </pre>
 * Slot 11 — green confirm pane, slot 13 — key preview, slot 15 — red cancel pane.
 */
public class BuyConfirmGui extends ChestGui {

    private final Player player;
    private final Crate crate;
    private final int amount;

    public BuyConfirmGui(VaultCrates plugin, Player player, Crate crate, int amount) {
        super(plugin);
        this.player = player;
        this.crate = crate;
        this.amount = Math.max(1, amount);
    }

    // -------------------------------------------------------------------------
    // ChestGui contract
    // -------------------------------------------------------------------------

    @Override
    public String getTitle() {
        return "&6Confirm Purchase";
    }

    @Override
    public int getSize() {
        return 27;
    }

    @Override
    public void populate() {
        // Fill all empty slots with grey glass
        fillEmpty(getFiller());

        double price = getShopPrice(crate.getKeyConfig());
        double totalPrice = price * amount;
        double balance = plugin.hasVault() ? plugin.getVaultHook().getBalance(player) : 0.0;

        String formattedTotal   = plugin.hasVault()
                ? plugin.getVaultHook().format(totalPrice)
                : String.format("%.2f", totalPrice);
        String formattedBalance = plugin.hasVault()
                ? plugin.getVaultHook().format(balance)
                : String.format("%.2f", balance);

        // --- Slot 11: Confirm button ---
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add("&7Crate: &e" + crate.getName());
        confirmLore.add("&7Amount: &e" + amount + "x key");
        confirmLore.add("&7Total: &e" + formattedTotal);
        confirmLore.add("&7Your balance: &e" + formattedBalance);
        confirmLore.add("");
        confirmLore.add("&aClick to confirm!");

        ItemStack confirm = ItemBuilder.of(Material.GREEN_STAINED_GLASS_PANE)
                .name("&a\u2714 Confirm")
                .lore(confirmLore)
                .build();

        setItem(11, confirm, e -> {
            if (!plugin.hasVault()) {
                player.sendMessage(plugin.getMessages().get("shop.vault-not-found"));
                player.closeInventory();
                return;
            }

            double currentBalance = plugin.getVaultHook().getBalance(player);
            if (currentBalance < totalPrice) {
                player.sendMessage(plugin.getMessages().get("shop.not-enough-money",
                        "{price}", formattedTotal));
                player.closeInventory();
                return;
            }

            boolean success = plugin.getVaultHook().withdraw(player, totalPrice);
            if (!success) {
                player.sendMessage(plugin.getMessages().get("shop.withdraw-failed"));
                player.closeInventory();
                return;
            }

            // Give the keys to the player
            boolean useVirtual = plugin.getConfigManager().getBoolean("Shop.VirtualKeys", true);
            if (useVirtual) {
                plugin.getKeyManager().addVirtualKeys(player.getUniqueId(), crate.getName(), amount);
            } else {
                // Physical keys — each gets its own UUID for anti-dupe protection
                plugin.getKeyManager().createPhysicalKeys(crate, amount)
                        .forEach(k -> player.getInventory().addItem(k));
            }

            player.sendMessage(plugin.getMessages().get("shop.purchase-success",
                    "{amount}", String.valueOf(amount),
                    "{crate}", crate.getName(),
                    "{price}", formattedTotal));
            player.closeInventory();
        });

        // --- Slot 13: Key item preview ---
        ItemStack keyPreview = crate.buildKeyItem(amount);
        setItem(13, keyPreview);

        // --- Slot 15: Cancel button ---
        ItemStack cancel = ItemBuilder.of(Material.RED_STAINED_GLASS_PANE)
                .name("&c\u2718 Cancel")
                .lore(List.of("&7Click to cancel."))
                .build();

        setItem(15, cancel, e -> player.closeInventory());
    }

    // -------------------------------------------------------------------------
    // Compatibility helper
    // -------------------------------------------------------------------------

    /**
     * Returns the shop price from the KeyConfig via reflection, so this class
     * compiles before {@code getShopPrice()} is added to {@link Crate.KeyConfig}.
     */
    private static double getShopPrice(Crate.KeyConfig kc) {
        if (kc == null) return 0.0;
        try {
            return (double) kc.getClass().getMethod("getShopPrice").invoke(kc);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
