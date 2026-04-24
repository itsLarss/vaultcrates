package de.itslarss.vaultcrates.gui;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.crate.reward.Reward;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Central manager for opening all VaultCrates GUI menus.
 */
public class GuiManager {

    private final VaultCrates plugin;

    public GuiManager(VaultCrates plugin) {
        this.plugin = plugin;
    }

    /** Opens the crate editor GUI for the given crate. */
    public void openCrateEditor(Player player, Crate crate) {
        new CrateEditorGui(plugin, crate, player).open(player);
    }

    /** Opens the reward editor for a specific reward. */
    public void openRewardEditor(Player player, Crate crate, Reward reward, boolean isBest) {
        new RewardEditorGui(plugin, crate, reward, isBest, player).open(player);
    }

    /** Opens the reward preview GUI for a crate. */
    public void openCratePreview(Player player, Crate crate) {
        new CratePreviewGui(plugin, crate, player).open(player);
    }

    /** Opens the reward list editor for a crate. */
    public void openRewardList(Player player, Crate crate) {
        new RewardListGui(plugin, crate, player).open(player);
    }

    /** Opens the key editor GUI for the given crate. */
    public void openKeyEditor(Player player, Crate crate) {
        new KeyEditorGui(plugin, crate, player).open(player);
    }

    /** Opens the key shop GUI. */
    public void openShop(Player player) {
        new ShopGui(plugin, player).open(player);
    }

    /** Opens the purchase confirmation GUI for buying a key. */
    public void openBuyConfirm(Player player, Crate crate, int amount) {
        new BuyConfirmGui(plugin, player, crate, amount).open(player);
    }

    /** Opens the selectable rewards GUI for the given crate. */
    public void openSelectableRewards(Player player, Crate crate, int count, Location crateLocation) {
        new SelectableRewardsGui(plugin, player, crate, count, crateLocation).open(player);
    }
}
