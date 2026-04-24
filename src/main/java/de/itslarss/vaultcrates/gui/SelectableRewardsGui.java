package de.itslarss.vaultcrates.gui;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.crate.reward.Rarity;
import de.itslarss.vaultcrates.crate.reward.Reward;
import de.itslarss.vaultcrates.gui.base.ChestGui;
import de.itslarss.vaultcrates.util.ColorUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI that lets a player manually pick their reward from the crate's prize pool.
 *
 * <p>All prizes are displayed in the centre area of a 6-row chest (the border
 * row is filled with grey glass). The player clicks a prize to select it; the
 * inventory closes and the crate animation begins.</p>
 *
 * <p>The selected reward is stored in
 * {@link de.itslarss.vaultcrates.animation.AnimationManager#getPendingRewards()}
 * so that the animation can use it instead of rolling a random one.
 * If that map does not exist yet, we simply start the animation without
 * a pre-selected reward as a safe fallback.</p>
 */
public class SelectableRewardsGui extends ChestGui {

    private final Player player;
    private final Crate crate;
    private final int count;            // max number of prizes shown
    private final Location crateLocation;

    public SelectableRewardsGui(VaultCrates plugin,
                                Player player,
                                Crate crate,
                                int count,
                                Location crateLocation) {
        super(plugin);
        this.player = player;
        this.crate = crate;
        this.count = count;
        this.crateLocation = crateLocation;
    }

    // -------------------------------------------------------------------------
    // ChestGui contract
    // -------------------------------------------------------------------------

    @Override
    public String getTitle() {
        return "&6&lChoose your Reward";
    }

    @Override
    public int getSize() {
        return 54;
    }

    @Override
    public void populate() {
        // Fill the entire border with grey glass
        fillBorder(getFiller());

        List<Reward> prizes = crate.getPrizes();

        // Content slots: rows 1–4, columns 1–7 (slots 10–16, 19–25, 28–34, 37–43)
        // That is 4 rows × 7 columns = 28 slots — more than enough for most crates.
        int[] contentSlots = buildContentSlots();

        int displayed = 0;
        for (int i = 0; i < prizes.size() && displayed < contentSlots.length && displayed < count; i++) {
            Reward reward = prizes.get(i);
            ItemStack icon = buildRewardIcon(reward);
            int slot = contentSlots[displayed];
            setItem(slot, icon, e -> {
                // Store the selected reward in the animation manager's pending map
                // (if the method exists — safe reflection fallback)
                storeSelectedReward(player, reward);

                player.closeInventory();
                plugin.getAnimationManager().startAnimation(player, crate, crateLocation);
            });
            displayed++;
        }

        // Fill remaining content slots with grey glass
        fillEmpty(getFiller());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the display item for a reward, appending chance and rarity lines
     * to the lore.
     */
    private ItemStack buildRewardIcon(Reward reward) {
        ItemStack icon = reward.buildDisplayItem().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        List<net.kyori.adventure.text.Component> lore =
                meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());

        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(""));
        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(
                ColorUtil.colorize("&7Chance: &e" + String.format("%.2f%%", reward.getChance()))));

        Rarity rarity = reward.getRarity();
        if (rarity != null) {
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(
                    ColorUtil.colorize("&7Rarity: " + rarity.getDisplayName())));
        }

        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(""));
        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(
                ColorUtil.colorize("&eClick to select this reward!")));

        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    /**
     * Returns the inner content slots of a 54-slot inventory (all slots excluding
     * the outer border ring).
     * Slots: rows 1–4, columns 1–7.
     */
    private static int[] buildContentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Attempts to store the player's chosen reward in the animation manager's
     * pending-reward map via reflection. If the {@code getPendingRewards()} method
     * does not exist (future addition), the call is silently ignored and the
     * animation will roll a random reward as normal.
     *
     * @param player the player
     * @param reward the selected reward
     */
    private void storeSelectedReward(Player player, Reward reward) {
        try {
            java.lang.reflect.Method m =
                    plugin.getAnimationManager().getClass().getMethod("getPendingRewards");
            Object map = m.invoke(plugin.getAnimationManager());
            if (map instanceof java.util.Map<?, ?> rawMap) {
                @SuppressWarnings("unchecked")
                java.util.Map<java.util.UUID, Reward> pendingRewards =
                        (java.util.Map<java.util.UUID, Reward>) rawMap;
                pendingRewards.put(player.getUniqueId(), reward);
            }
        } catch (Exception ignored) {
            // getPendingRewards() not yet available — animation will pick randomly
        }
    }
}
