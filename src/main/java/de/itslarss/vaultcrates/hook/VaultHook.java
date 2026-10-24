package de.itslarss.vaultcrates.hook;

import de.itslarss.vaultcrates.VaultCrates;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Integration with the Vault economy API — works with Vault and VaultUnlocked
 * (VaultUnlocked keeps the {@code net.milkbowl.vault.economy.Economy} service).
 */
public class VaultHook {

    private final VaultCrates plugin;
    private boolean apiPresent = false;
    private Economy economy;

    public VaultHook(VaultCrates plugin) {
        this.plugin = plugin;
    }

    /**
     * @return {@code true} if an economy provider is already registered.
     *         If not, the provider is looked up again on first use, so economy
     *         plugins that enable after VaultCrates are still picked up.
     */
    public boolean setup() {
        try {
            Class.forName("net.milkbowl.vault.economy.Economy");
            apiPresent = true;
        } catch (ClassNotFoundException e) {
            return false;
        }
        return resolve();
    }

    private boolean resolve() {
        if (economy != null) return true;
        if (!apiPresent) return false;
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        economy = rsp != null ? rsp.getProvider() : null;
        return economy != null;
    }

    public boolean isEnabled() { return resolve(); }

    public boolean deposit(Player player, double amount) {
        return resolve() && economy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean withdraw(Player player, double amount) {
        return resolve() && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public double getBalance(Player player) {
        return resolve() ? economy.getBalance(player) : 0;
    }

    public String format(double amount) {
        return resolve() ? economy.format(amount) : String.format("%.2f", amount);
    }
}
