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

/**
 * GUI for editing a single reward's properties.
 * Changes are stored in-memory; the crate config file must be saved separately via the editor.
 */
public class RewardEditorGui extends ChestGui {

    private final Crate crate;
    private final Reward reward;
    private final boolean isBest;
    private final Player player;

    public RewardEditorGui(VaultCrates plugin, Crate crate, Reward reward, boolean isBest, Player player) {
        super(plugin);
        this.crate = crate;
        this.reward = reward;
        this.isBest = isBest;
        this.player = player;
    }

    @Override public String getTitle() { return ColorUtil.colorize("&8Edit Reward: &6" + reward.getId()); }
    @Override public int getSize() { return 54; }

    @Override
    public void populate() {
        ItemStack filler = getFiller();
        fillBorder(filler);

        // Centre: current reward preview
        setItem(22, reward.buildDisplayItem());

        // Edit name
        setItem(10, ItemBuilder.of(Material.NAME_TAG)
                .name("&6Edit Name")
                .addLore("&7Current: &f" + ColorUtil.strip(reward.getName()), "", "&aClick to change").build(),
                e -> promptChat(player, "Enter new reward name (use & for colours):", input -> {
                    reward.setName(input);
                    plugin.getGuiManager().openRewardEditor(player, crate, reward, isBest);
                }));

        // Toggle glow
        setItem(11, ItemBuilder.of(Material.GLOWSTONE_DUST)
                .name("&6Toggle Glow")
                .addLore("&7Current: " + (reward.isGlow() ? "&aEnabled" : "&cDisabled"), "", "&aClick to toggle").build(),
                e -> { reward.setGlow(!reward.isGlow()); open(player); });

        // Edit chance
        setItem(12, ItemBuilder.of(Material.GOLD_NUGGET)
                .name("&6Edit Chance")
                .addLore("&7Current: &e" + String.format("%.2f%%", reward.getChance()), "", "&aClick to change").build(),
                e -> promptChat(player, "Enter new chance (0.01 - 100):", input -> {
                    try {
                        double chance = Double.parseDouble(input);
                        reward.setChance(Math.max(0.01, Math.min(100, chance)));
                    } catch (NumberFormatException ignored) {}
                    plugin.getGuiManager().openRewardEditor(player, crate, reward, isBest);
                }));

        // Edit material
        setItem(13, ItemBuilder.of(Material.CHEST)
                .name("&6Edit Material")
                .addLore("&7Current: &f" + reward.getMaterial().name(), "", "&aClick to change").build(),
                e -> promptChat(player, "Enter material name (e.g. DIAMOND):", input -> {
                    try { reward.setMaterial(Material.valueOf(input.toUpperCase())); }
                    catch (IllegalArgumentException ex) { player.sendMessage(ColorUtil.colorize("&cInvalid material!")); }
                    plugin.getGuiManager().openRewardEditor(player, crate, reward, isBest);
                }));

        // Edit amount
        setItem(14, ItemBuilder.of(Material.HOPPER)
                .name("&6Edit Amount")
                .addLore("&7Current: &f" + reward.getAmount(), "", "&aClick to change").build(),
                e -> promptChat(player, "Enter amount (1-64):", input -> {
                    try { reward.setAmount(Integer.parseInt(input)); }
                    catch (NumberFormatException ignored) {}
                    plugin.getGuiManager().openRewardEditor(player, crate, reward, isBest);
                }));

        // Toggle give item
        setItem(15, ItemBuilder.of(Material.DIAMOND)
                .name("&6Toggle Give Item")
                .addLore("&7Current: " + (reward.isGiveItem() ? "&aEnabled" : "&cDisabled"), "", "&aClick to toggle").build(),
                e -> { reward.setGiveItem(!reward.isGiveItem()); open(player); });

        // Edit permission
        setItem(29, ItemBuilder.of(Material.PAPER)
                .name("&6Required Permission")
                .addLore("&7Current: " + (reward.getRequiredPermission() == null ? "&7None" : "&f" + reward.getRequiredPermission()),
                        "", "&aClick to set (&7type 'none' to clear)").build(),
                e -> promptChat(player, "Enter required permission (or 'none'):", input -> {
                    reward.setRequiredPermission(input.equalsIgnoreCase("none") ? null : input);
                    plugin.getGuiManager().openRewardEditor(player, crate, reward, isBest);
                }));

        // Back
        setItem(45, ItemBuilder.of(Material.ARROW).name("&7\u00AB Back").build(),
                e -> plugin.getGuiManager().openRewardList(player, crate));

        // Save info
        setItem(49, ItemBuilder.of(Material.GREEN_CONCRETE)
                .name("&aSave changes")
                .addLore("&7Changes are stored in memory.", "&7Edit the YAML file to make them permanent.").build(),
                e -> {
                    player.sendMessage(ColorUtil.colorize("&7[&6VC&7] &aReward updated in memory. Reload to revert."));
                    plugin.getGuiManager().openRewardList(player, crate);
                });
    }

    // -------------------------------------------------------------------------
    // Chat prompt helper
    // -------------------------------------------------------------------------

    private void promptChat(Player player, String prompt, java.util.function.Consumer<String> callback) {
        player.closeInventory();
        player.sendMessage(ColorUtil.colorize("&7[&6VC&7] " + prompt + " &8(type in chat, 30s timeout)"));
        // Register temporary chat listener
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            new de.itslarss.vaultcrates.gui.ChatInputListener(plugin, player, callback, 30).register();
        });
    }
}
