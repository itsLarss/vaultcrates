package de.itslarss.vaultcrates.hook;

import de.itslarss.vaultcrates.VaultCrates;
import de.itslarss.vaultcrates.crate.Crate;
import de.itslarss.vaultcrates.crate.Milestone;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for VaultCrates.
 *
 * <p>Available placeholders:</p>
 * <ul>
 *   <li>{@code %vaultcrates_keys_<CrateName>%} — virtual key count for the crate</li>
 *   <li>{@code %vaultcrates_is_animating%} — true/false</li>
 *   <li>{@code %vaultcrates_opens_<CrateName>%} — total crate opens for a player</li>
 *   <li>{@code %vaultcrates_milestone_<CrateName>_<MilestoneId>%} — "claimed" / "ready" / "opens_needed: N"</li>
 *   <li>{@code %vaultcrates_pity_<CrateName>_<RarityId>%} — current pity counter</li>
 * </ul>
 */
public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final VaultCrates plugin;

    public PlaceholderAPIHook(VaultCrates plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "vaultcrates"; }
    @Override public @NotNull String getAuthor() { return "itslarss"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.startsWith("keys_")) {
            String crateName = params.substring(5);
            return String.valueOf(plugin.getKeyManager().getVirtualKeys(player.getUniqueId(), crateName));
        }

        if (params.equals("is_animating")) {
            if (!player.isOnline()) return "false";
            return String.valueOf(plugin.getAnimationManager().isAnimating(player.getPlayer()));
        }

        if (params.equals("active_animations")) {
            return String.valueOf(plugin.getAnimationManager().getActiveCount());
        }

        // %vaultcrates_opens_<CrateName>% — total opens for this player
        if (params.startsWith("opens_")) {
            String crateName = params.substring(6);
            int opens = plugin.getStorageManager().getMilestoneStorage()
                    .getOpenCount(player.getUniqueId(), crateName);
            return String.valueOf(opens);
        }

        // %vaultcrates_milestone_<CrateName>_<MilestoneId>%
        // Returns: "claimed" / "ready" / "opens needed: N"
        if (params.startsWith("milestone_")) {
            String rest = params.substring(10);
            int sep = rest.indexOf('_');
            if (sep > 0) {
                String crateName = rest.substring(0, sep);
                String milestoneId = rest.substring(sep + 1);
                Crate crate = plugin.getCrateManager().getCrate(crateName);
                if (crate == null) return "unknown_crate";
                Milestone ms = crate.getMilestones().stream()
                        .filter(m -> m.getId().equalsIgnoreCase(milestoneId))
                        .findFirst().orElse(null);
                if (ms == null) return "unknown_milestone";
                var msStorage = plugin.getStorageManager().getMilestoneStorage();
                if (msStorage.isClaimed(player.getUniqueId(), crateName, milestoneId)) return "claimed";
                int opens = msStorage.getOpenCount(player.getUniqueId(), crateName);
                if (opens >= ms.getOpenCount()) return "ready";
                return "opens needed: " + (ms.getOpenCount() - opens);
            }
        }

        // %vaultcrates_pity_<CrateName>_<RarityId>% — current pity counter
        if (params.startsWith("pity_")) {
            String rest = params.substring(5);
            int sep = rest.indexOf('_');
            if (sep > 0) {
                String crateName = rest.substring(0, sep);
                String rarityId  = rest.substring(sep + 1);
                int count = plugin.getStorageManager().getPityStorage()
                        .getCount(player.getUniqueId(), crateName, rarityId);
                return String.valueOf(count);
            }
        }

        return null;
    }

    /**
     * Replaces PlaceholderAPI placeholders in the given string for an online player.
     */
    public String replacePlaceholders(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    /**
     * Replaces PlaceholderAPI placeholders for an offline player.
     */
    public String replacePlaceholders(OfflinePlayer player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
