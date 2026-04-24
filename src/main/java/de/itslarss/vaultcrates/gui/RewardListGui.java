package de.itslarss.vaultcrates.gui;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.crate.reward.Reward;
import de.itslarss.vaultcrates.gui.base.ChestGui;
import de.itslarss.vaultcrates.util.ColorUtil;
import de.itslarss.vaultcrates.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI listing all rewards in a crate for the in-game editor.
 * Click a reward to edit it; use Add buttons to create new ones.
 */
public class RewardListGui extends ChestGui {

    private final Crate crate;
    private final Player player;
    private int page;

    private static final int CONTENT_SLOTS = 36; // rows 0-3

    public RewardListGui(VaultCrates plugin, Crate crate, Player player) {
        super(plugin);
        this.crate = crate;
        this.player = player;
        this.page = 0;
    }

    @Override public String getTitle() { return ColorUtil.colorize("&8Reward List: &6" + crate.getName()); }
    @Override public int getSize() { return 54; }

    @Override
    public void populate() {
        ItemStack filler = getFiller();
        for (int i = CONTENT_SLOTS; i < 54; i++) setItem(i, filler);

        // List all prizes + best prizes
        List<Object[]> all = new ArrayList<>(); // [Reward, isBest]
        for (Reward r : crate.getPrizes()) all.add(new Object[]{r, false});
        for (Reward r : crate.getBestPrizes()) all.add(new Object[]{r, true});

        int start = page * CONTENT_SLOTS;
        int end = Math.min(start + CONTENT_SLOTS, all.size());

        for (int i = start; i < end; i++) {
            int slot = i - start;
            Reward reward = (Reward) all.get(i)[0];
            boolean isBest = (boolean) all.get(i)[1];

            ItemStack item = reward.buildDisplayItem();
            ItemBuilder.of(item.getType())
                    .addLore("",
                            isBest ? "&6[Best Prize]" : "&7[Normal Prize]",
                            "&7Chance: &e" + String.format("%.2f%%", reward.getChance()),
                            "",
                            "&aLeft-click &7to edit",
                            "&cRight-click &7to delete");

            final Reward finalReward = reward;
            final boolean finalIsBest = isBest;
            setItem(slot, item, e -> {
                if (e.isLeftClick()) {
                    plugin.getGuiManager().openRewardEditor(player, crate, finalReward, finalIsBest);
                }
            });
        }

        // Fill empty
        for (int s = (end - start); s < CONTENT_SLOTS; s++) setItem(s, filler);

        // Bottom row controls
        // Back
        setItem(45, ItemBuilder.of(Material.ARROW).name("&7\u00AB Back").build(),
                e -> plugin.getGuiManager().openCrateEditor(player, crate));

        // Add Prize
        setItem(49, ItemBuilder.of(Material.LIME_CONCRETE).name("&a+ Add Normal Prize").build(), e -> {
            player.sendMessage(ColorUtil.colorize("&7[&6VC&7] Create a new prize YAML entry in the crate file to add prizes."));
            player.closeInventory();
        });

        // Add Best Prize
        setItem(50, ItemBuilder.of(Material.GOLD_BLOCK).name("&6+ Add Best Prize").build(), e -> {
            player.sendMessage(ColorUtil.colorize("&7[&6VC&7] Create a new BestPrize YAML entry in the crate file to add best prizes."));
            player.closeInventory();
        });

        // Pagination
        if (page > 0) {
            setItem(46, ItemBuilder.of(Material.ARROW).name("&7\u00AB Previous").build(), e -> { page--; inventory.clear(); populate(); });
        }
        if (end < all.size()) {
            setItem(52, ItemBuilder.of(Material.ARROW).name("&7Next \u00BB").build(), e -> { page++; inventory.clear(); populate(); });
        }
    }
}
