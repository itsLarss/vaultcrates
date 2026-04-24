package de.itslarss.vaultcrates.gui;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.animation.AnimationType;
import de.itslarss.vaultcrates.animation.AnimationVisibility;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.gui.base.ChestGui;
import de.itslarss.vaultcrates.util.ColorUtil;
import de.itslarss.vaultcrates.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * In-game crate editor — allows admins to modify crate properties without editing YAML files.
 */
public class CrateEditorGui extends ChestGui {

    private final Crate crate;
    private final Player player;

    private static final AnimationType[] ANIM_TYPES = AnimationType.values();
    private static final AnimationVisibility[] VISIBILITIES = AnimationVisibility.values();

    public CrateEditorGui(VaultCrates plugin, Crate crate, Player player) {
        super(plugin);
        this.crate = crate;
        this.player = player;
    }

    @Override public String getTitle() { return ColorUtil.colorize("&8Crate Editor: &6" + crate.getName()); }
    @Override public int getSize() { return 54; }

    @Override
    public void populate() {
        fillBorder(getFiller());

        // Crate preview (center)
        setItem(13, crate.buildCrateItem());

        // Animation Type (cycle on click)
        setItem(10, ItemBuilder.of(Material.FIREWORK_ROCKET)
                .name("&6Animation Type")
                .addLore("&7Current: &e" + crate.getAnimationType().name(),
                        "", "&aClick to cycle through types").build(),
                e -> {
                    int idx = (crate.getAnimationType().ordinal() + 1) % ANIM_TYPES.length;
                    crate.setAnimationType(ANIM_TYPES[idx]);
                    open(player);
                });

        // Animation Visibility (cycle)
        setItem(11, ItemBuilder.of(Material.ENDER_PEARL)
                .name("&6Visibility")
                .addLore("&7Current: &e" + crate.getVisibility().name(),
                        "&7INDIVIDUAL / ALL / PROXIMITY", "", "&aClick to cycle").build(),
                e -> {
                    int idx = (crate.getVisibility().ordinal() + 1) % VISIBILITIES.length;
                    crate.setVisibility(VISIBILITIES[idx]);
                    open(player);
                });

        // Size
        setItem(12, ItemBuilder.of(Material.HOPPER)
                .name("&6Reward Count")
                .addLore("&7How many prizes from the animation", "&7Current: &e" + crate.getSize(),
                        "", "&aClick to change").build(),
                e -> promptChat("Enter new size (1-50):", input -> {
                    try { crate.setSize(Integer.parseInt(input.trim())); }
                    catch (NumberFormatException ignored) {}
                    plugin.getGuiManager().openCrateEditor(player, crate);
                }));

        // Toggle enchanted/glow
        setItem(14, ItemBuilder.of(Material.ENCHANTED_BOOK)
                .name("&6Toggle Glow")
                .addLore("&7Current: " + (crate.isEnchanted() ? "&aEnabled" : "&cDisabled"),
                        "", "&aClick to toggle").build(),
                e -> { crate.setEnchanted(!crate.isEnchanted()); open(player); });

        // Toggle preview
        setItem(15, ItemBuilder.of(Material.ENDER_EYE)
                .name("&6Toggle Preview")
                .addLore("&7Current: " + (crate.isPreviewEnabled() ? "&aEnabled" : "&cDisabled"),
                        "", "&aClick to toggle").build(),
                e -> { crate.setPreviewEnabled(!crate.isPreviewEnabled()); open(player); });

        // Toggle shift open
        setItem(16, ItemBuilder.of(Material.FEATHER)
                .name("&6Toggle Shift+Open")
                .addLore("&7Current: " + (crate.isShiftInstantlyOpen() ? "&aEnabled" : "&cDisabled"),
                        "", "&aClick to toggle").build(),
                e -> { crate.setShiftInstantlyOpen(!crate.isShiftInstantlyOpen()); open(player); });

        // Hologram editor
        setItem(28, ItemBuilder.of(Material.ITEM_FRAME)
                .name("&6Edit Hologram")
                .addLore("&7Lines: &e" + crate.getHologramLines().size(),
                        "", "&aClick to view/edit lines in chat").build(),
                e -> {
                    player.closeInventory();
                    player.sendMessage(ColorUtil.colorize("&7Current hologram lines:"));
                    for (int i = 0; i < crate.getHologramLines().size(); i++) {
                        player.sendMessage(ColorUtil.colorize("  &e" + (i + 1) + ". &f" + crate.getHologramLines().get(i)));
                    }
                    player.sendMessage(ColorUtil.colorize("&7Edit the crate YAML file to change hologram lines."));
                });

        // Reward list
        setItem(31, ItemBuilder.of(Material.GOLD_BLOCK)
                .name("&6Edit Prizes")
                .addLore("&7Normal: &e" + crate.getPrizes().size(),
                        "&7Best: &e" + crate.getBestPrizes().size(),
                        "", "&aClick to open reward list").build(),
                e -> plugin.getGuiManager().openRewardList(player, crate));

        // Final message
        setItem(32, ItemBuilder.of(Material.PAPER)
                .name("&6Edit Final Message")
                .addLore("&7Lines: &e" + crate.getFinalMessage().size(),
                        "", "&7Edit in YAML for best results").build(),
                e -> {
                    player.closeInventory();
                    player.sendMessage(ColorUtil.colorize("&7Current final message:"));
                    crate.getFinalMessage().forEach(l -> player.sendMessage(ColorUtil.colorize("  &f" + l)));
                });

        // Back
        setItem(45, ItemBuilder.of(Material.ARROW).name("&7\u00AB Back").build(),
                e -> player.closeInventory());

        // Save notice
        setItem(49, ItemBuilder.of(Material.GREEN_CONCRETE)
                .name("&aSave Info")
                .addLore("&7In-memory changes are not saved to disk.",
                        "&7Use &e/vc reload &7to reload from files.").build(), null);
    }

    private void promptChat(String prompt, java.util.function.Consumer<String> callback) {
        player.closeInventory();
        player.sendMessage(ColorUtil.colorize("&7[&6VC&7] " + prompt + " &8(type in chat)"));
        new ChatInputListener(plugin, player, callback, 30).register();
    }
}
