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
 * Shop GUI that displays all crates with shop support enabled.
 * Clicking a crate icon opens a {@link BuyConfirmGui} for that crate.
 *
 * <p>Crates appear in the shop if their {@code KeyConfig} has
 * {@code ShopEnabled: true} and a {@code ShopPrice} set.</p>
 */
public class ShopGui extends ChestGui {

    private final Player player;

    public ShopGui(VaultCrates plugin, Player player) {
        super(plugin);
        this.player = player;
    }

    // -------------------------------------------------------------------------
    // ChestGui contract
    // -------------------------------------------------------------------------

    @Override
    public String getTitle() {
        boolean virtualMode = plugin.getConfigManager().getBoolean("Shop.VirtualKeys", true);
        return virtualMode ? "&6&lVirtual Key Shop" : "&6&lKey Shop";
    }

    @Override
    public int getSize() {
        return 54;
    }

    @Override
    public void populate() {
        // Fill every slot with grey glass first, then overwrite with crate items
        ItemStack filler = getFiller();
        fillEmpty(filler);

        // Collect shop-enabled crates.
        // KeyConfig.isShopEnabled() and KeyConfig.getShopPrice() are added when the
        // shop feature is wired into Crate.KeyConfig. Until then this list is empty.
        List<Crate> shopCrates = new ArrayList<>();
        for (Crate crate : plugin.getCrateManager().getCrates().values()) {
            Crate.KeyConfig kc = crate.getKeyConfig();
            if (kc != null && isShopEnabled(kc)) {
                shopCrates.add(crate);
            }
        }

        // Place crate items in the content area (slots 0–53, skip last row navigation)
        // Content slots: rows 0–4 (slots 0–44), bottom row reserved for back button
        int contentEnd = 45;
        int slot = 0;

        for (Crate crate : shopCrates) {
            if (slot >= contentEnd) break;

            Crate.KeyConfig kc = crate.getKeyConfig();
            double price = getShopPrice(kc);
            double balance = plugin.hasVault()
                    ? plugin.getVaultHook().getBalance(player) : 0.0;
            String formattedPrice   = plugin.hasVault()
                    ? plugin.getVaultHook().format(price) : String.format("%.2f", price);
            String formattedBalance = plugin.hasVault()
                    ? plugin.getVaultHook().format(balance) : String.format("%.2f", balance);

            ItemStack icon = crate.buildCrateItem();
            // Append shop lore
            org.bukkit.inventory.meta.ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<net.kyori.adventure.text.Component> lore =
                        meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(""));
                lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize("&7Price: &e" + formattedPrice));
                lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize("&7Your balance: &e" + formattedBalance));
                boolean virtualMode = plugin.getConfigManager().getBoolean("Shop.VirtualKeys", true);
                lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(
                                virtualMode ? "&aClick to buy virtual key" : "&aClick to buy key"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            final Crate finalCrate = crate;
            setItem(slot, icon, e -> {
                player.closeInventory();
                new BuyConfirmGui(plugin, player, finalCrate, 1).open(player);
            });

            slot++;
        }

        // Back button at slot 49
        ItemStack back = ItemBuilder.of(Material.ARROW).name("&7Back").build();
        setItem(49, back, e -> player.closeInventory());

        // Re-fill any remaining empty content slots with the filler
        fillEmpty(filler);
    }

    // -------------------------------------------------------------------------
    // Compatibility helpers — replaced once KeyConfig gains shop fields
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the KeyConfig has shop support enabled.
     * Delegates to {@link Crate.KeyConfig#isShopEnabled()} once that method exists.
     */
    private static boolean isShopEnabled(Crate.KeyConfig kc) {
        try {
            return (boolean) kc.getClass().getMethod("isShopEnabled").invoke(kc);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the shop key price from the KeyConfig.
     * Delegates to {@link Crate.KeyConfig#getShopPrice()} once that method exists.
     */
    private static double getShopPrice(Crate.KeyConfig kc) {
        try {
            return (double) kc.getClass().getMethod("getShopPrice").invoke(kc);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
