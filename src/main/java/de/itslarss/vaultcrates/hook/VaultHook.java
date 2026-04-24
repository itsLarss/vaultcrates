package de.itslarss.vaultcrates.hook;

import de.itslarss.vaultcrates.VaultCrates;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Integration with the Vault economy system.
 */
public class VaultHook {

    private final VaultCrates plugin;
    private Economy economy;
    private boolean enabled = false;

    public VaultHook(VaultCrates plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts to hook into Vault's Economy service.
     *
     * @return {@code true} if an Economy provider was found
     */
    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        enabled = economy != null;
        return enabled;
    }

    public boolean isEnabled() { return enabled; }

    public boolean deposit(Player player, double amount) {
        return enabled && economy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean withdraw(Player player, double amount) {
        return enabled && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public double getBalance(Player player) {
        return enabled ? economy.getBalance(player) : 0;
    }

    public String format(double amount) {
        return enabled ? economy.format(amount) : String.format("%.2f", amount);
    }
}
