package de.itslarss.vaultcrates.gui;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.crate.reward.Reward;
import de.itslarss.vaultcrates.gui.base.ChestGui;
import de.itslarss.vaultcrates.util.ColorUtil;
import de.itslarss.vaultcrates.util.ItemBuilder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Preview GUI that shows all prizes and best prizes for a crate.
 * Supports pagination for crates with many rewards.
 *
 * <p>Whether the drop-chance is displayed can be toggled per-crate via
 * {@code ShowChanceInPreview: true/false} in the crate's YAML config.</p>
 */
public class CratePreviewGui extends ChestGui {

    private final Crate crate;
    private final Player player;
    private int page;

    /** All rewards combined (prizes first, then best prizes). */
    private final List<Reward> allRewards = new ArrayList<>();

    /** Total chance of all normal prizes — used for normalised percentage display. */
    private final double totalChance;

    private static final int ROWS = 6;

    public CratePreviewGui(VaultCrates plugin, Crate crate, Player player) {
        super(plugin);
        this.crate = crate;
        this.player = player;
        this.page = 0;
        allRewards.addAll(crate.getPrizes());
        allRewards.addAll(crate.getBestPrizes());

        // Pre-compute total chance for normalisation
        double tc = crate.getPrizes().stream().mapToDouble(Reward::getChance).sum();
        this.totalChance = tc > 0 ? tc : 1.0; // avoid division by zero
    }

    @Override
    public String getTitle() {
        return ColorUtil.colorize(crate.getPreviewTitle() != null
                ? crate.getPreviewTitle() : "&e&lRewards Preview");
    }

    @Override
    public int getSize() {
        return Math.max(18, Math.min(54, ((crate.getPreviewSize() + 8) / 9) * 9));
    }

    @Override
    public void populate() {
        // Top and bottom rows filled with glass panes
        ItemStack topFiller  = ItemBuilder.of(Material.YELLOW_STAINED_GLASS_PANE).name("&r").build();
        ItemStack greyFiller = getFiller();

        for (int i = 0; i < 9; i++) setItem(i, topFiller);
        for (int i = getSize() - 9; i < getSize(); i++) setItem(i, greyFiller);

        // Content area: rows 1..ROWS-2
        int startSlot    = 9;
        int endSlot      = getSize() - 9;
        int contentSlots = endSlot - startSlot;

        int start = page * contentSlots;
        int end   = Math.min(start + contentSlots, allRewards.size());

        for (int i = start; i < end; i++) {
            int slot = startSlot + (i - start);
            if (slot >= endSlot) break;
            Reward reward = allRewards.get(i);
            boolean isBest = i >= crate.getPrizes().size();
            setItem(slot, buildPreviewItem(reward, isBest), null);
        }

        // Fill remaining content slots with grey filler
        for (int s = startSlot + (end - start); s < endSlot; s++) {
            setItem(s, greyFiller);
        }

        // ── Navigation ───────────────────────────────────────────────────────
        int lastRow = getSize() - 9;

        if (page > 0) {
            setItem(lastRow + 3,
                    ItemBuilder.of(Material.ARROW).name("&7\u00AB Previous Page").build(),
                    e -> { page--; inventory.clear(); populate(); });
        }

        setItem(lastRow + 4,
                ItemBuilder.of(Material.PAPER)
                        .name("&ePage &6" + (page + 1) + " &e/ &6" + getMaxPage())
                        .build());

        if (page < getMaxPage() - 1) {
            setItem(lastRow + 5,
                    ItemBuilder.of(Material.ARROW).name("&7Next Page \u00BB").build(),
                    e -> { page++; inventory.clear(); populate(); });
        }

        setItem(lastRow + 8,
                ItemBuilder.of(Material.BARRIER).name("&cClose").build(),
                e -> player.closeInventory());
    }

    // -------------------------------------------------------------------------
    // Item builder
    // -------------------------------------------------------------------------

    private ItemStack buildPreviewItem(Reward reward, boolean isBest) {
        ItemStack base = reward.buildDisplayItem();
        ItemMeta meta = base.getItemMeta();
        if (meta == null) return base;

        List<net.kyori.adventure.text.Component> lore =
                meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());

        // ── Chance line (only if enabled for this crate) ──────────────────────
        if (crate.isShowChanceInPreview()) {
            lore.add(component(""));

            if (isBest) {
                // Best prizes have their own pool — show raw chance relative to best-prize pool
                double bestTotal = crate.getBestPrizes().stream()
                        .mapToDouble(Reward::getChance).sum();
                if (bestTotal <= 0) bestTotal = 1.0;
                double pct = (reward.getChance() / bestTotal) * 100.0;
                lore.add(component("&7Chance: &e" + formatPct(pct) + " &8(Best Prize Pool)"));
            } else {
                // Normal prize — normalise against the sum of all normal prizes
                double pct = (reward.getChance() / totalChance) * 100.0;
                lore.add(component("&7Chance: &e" + formatPct(pct)));
            }
        }

        // ── Best prize indicator ──────────────────────────────────────────────
        if (isBest) {
            if (!crate.isShowChanceInPreview()) {
                // still add a blank separator even without chance shown
                lore.add(component(""));
            }
            lore.add(component("&6\u2605 Best Prize"));
        }

        // ── Preview lores (extra lines from YAML) ────────────────────────────
        if (!reward.getPreviewLores().isEmpty()) {
            lore.add(component(""));
            for (String pl : reward.getPreviewLores()) {
                lore.add(component(pl));
            }
        }

        meta.lore(lore);
        base.setItemMeta(meta);
        return base;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static net.kyori.adventure.text.Component component(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(ColorUtil.colorize(text));
    }

    /**
     * Formats a percentage with 2 decimal places, but trims trailing zeros
     * (e.g. 33.33%, 5.00% → 5%, 0.01%).
     */
    private static String formatPct(double pct) {
        if (pct >= 1.0) {
            // Round to 2 decimals, strip ".00" suffix
            String s = String.format("%.2f", pct);
            if (s.endsWith(".00")) s = s.substring(0, s.length() - 3);
            else if (s.endsWith("0"))  s = s.substring(0, s.length() - 1);
            return s + "%";
        } else {
            // Very small: show up to 3 significant digits
            return String.format("%.3f%%", pct);
        }
    }

    private int getMaxPage() {
        int contentSlots = getSize() - 18;
        return Math.max(1, (int) Math.ceil((double) allRewards.size() / contentSlots));
    }
}
