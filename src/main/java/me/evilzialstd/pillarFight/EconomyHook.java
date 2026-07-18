package me.evilzialstd.pillarFight;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyHook {
    private Economy economy;

    public void setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
        }
    }

    public boolean isEnabled() {
        return economy != null;
    }

    public void deposit(Player player, double amount) {
        if (!isEnabled() || amount <= 0) {
            return;
        }
        economy.depositPlayer(player, amount);
    }
}