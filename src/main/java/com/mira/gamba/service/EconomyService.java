package com.mira.gamba.service;

import com.mira.gamba.MiraGambaPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class EconomyService {
    private final MiraGambaPlugin plugin;
    private Economy economy;

    public EconomyService(MiraGambaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hook() {
        var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) return false;
        economy = registration.getProvider();
        return economy != null;
    }

    public boolean has(Player player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        return economy != null && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(Player player, double amount) {
        return economy != null && economy.depositPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        return economy == null ? String.format("$%,.2f", amount) : economy.format(amount);
    }
}
