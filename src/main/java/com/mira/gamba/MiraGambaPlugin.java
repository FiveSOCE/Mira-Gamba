package com.mira.gamba;

import com.mira.gamba.command.GambaCommand;
import com.mira.gamba.listener.MachineListener;
import com.mira.gamba.listener.CardMachineListener;
import com.mira.gamba.service.EconomyService;
import com.mira.gamba.service.CardMachineService;
import com.mira.gamba.service.CardGameService;
import com.mira.gamba.service.MachineService;
import com.mira.gamba.service.SpinService;
import com.mira.gamba.service.SymbolService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class MiraGambaPlugin extends JavaPlugin {
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    private EconomyService economy;
    private SymbolService symbols;
    private MachineService machines;
    private SpinService spins;
    private CardMachineService cardMachines;
    private CardGameService cardGames;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        economy = new EconomyService(this);
        if (!economy.hook()) {
            getLogger().severe("No Vault economy provider found. Disabling MiraGamba.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        symbols = new SymbolService(this);
        machines = new MachineService(this);
        spins = new SpinService(this, machines, symbols, economy);
        cardMachines = new CardMachineService(this);
        cardGames = new CardGameService(this, cardMachines, economy);

        GambaCommand executor = new GambaCommand(this, machines, cardMachines);
        PluginCommand command = getCommand("gamba");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new MachineListener(this, machines, spins), this);
        getServer().getPluginManager().registerEvents(new CardMachineListener(cardMachines, cardGames), this);
        getServer().getScheduler().runTaskLater(this, machines::respawnMissingFrames, 20L);

        getLogger().info("MiraGamba enabled with " + machines.all().size() + " slot machine(s).");
    }

    @Override
    public void onDisable() {
        if (machines != null) machines.save();
        if (cardMachines != null) cardMachines.save();
    }

    public void reloadEverything() {
        reloadConfig();
        symbols.reload();
    }

    public boolean allowedBet(long bet) {
        return getConfig().getLongList("bets.allowed").contains(bet);
    }

    public Component component(String text) {
        return AMP.deserialize(text == null ? "" : text);
    }

    public String raw(String path) {
        return getConfig().getString(path, "");
    }

    public void msg(org.bukkit.command.CommandSender sender, String path, String... replacements) {
        String text = raw("messages.prefix") + raw(path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        sender.sendMessage(component(text));
    }

    public String money(double value) {
        return economy.format(value);
    }
}
