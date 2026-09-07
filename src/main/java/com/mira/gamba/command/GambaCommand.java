package com.mira.gamba.command;

import com.mira.gamba.MiraGambaPlugin;
import com.mira.gamba.model.SlotMachine;
import com.mira.gamba.service.MachineService;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class GambaCommand implements TabExecutor {
    private final MiraGambaPlugin plugin;
    private final MachineService machines;

    public GambaCommand(MiraGambaPlugin plugin, MachineService machines) {
        this.plugin = plugin;
        this.machines = machines;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("miragamba.admin")) {
            sender.sendMessage(plugin.component("&cYou do not have permission."));
            return true;
        }

        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.component("&cUsage: /gamba create <5000|50000|100000>"));
                    return true;
                }
                long bet;
                try {
                    bet = Long.parseLong(args[1]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(plugin.component("&cInvalid wager."));
                    return true;
                }
                if (!plugin.allowedBet(bet)) {
                    sender.sendMessage(plugin.component("&cAllowed wagers: $5,000, $50,000, $100,000."));
                    return true;
                }
                SlotMachine machine = machines.create(player, bet);
                sender.sendMessage(plugin.component("&aCreated slot machine &f" + machine.id() + " &awith wager &f" + plugin.money(bet) + "&a."));
                return true;
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                SlotMachine machine = machines.nearest(player.getLocation(), 6D);
                if (machine == null) {
                    sender.sendMessage(plugin.component("&cNo slot machine found within 6 blocks."));
                    return true;
                }
                machines.remove(machine);
                sender.sendMessage(plugin.component("&aRemoved slot machine &f" + machine.id() + "&a."));
                return true;
            }
            case "list" -> {
                sender.sendMessage(plugin.component("&5&lMiraGamba &8>> &7Machines: &f" + machines.all().size()));
                for (SlotMachine machine : machines.all()) {
                    sender.sendMessage(plugin.component("&7- &f" + machine.id() + " &7Bet &f" + plugin.money(machine.bet())
                            + " &8(" + machine.trigger().getWorld().getName() + " "
                            + machine.trigger().getBlockX() + ","
                            + machine.trigger().getBlockY() + ","
                            + machine.trigger().getBlockZ() + ")"));
                }
                return true;
            }
            case "reload" -> {
                plugin.reloadEverything();
                sender.sendMessage(plugin.component("&aMiraGamba configuration reloaded."));
                return true;
            }
            default -> {
                help(sender);
                return true;
            }
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(plugin.component("&5&lMiraGamba"));
        sender.sendMessage(plugin.component("&d/gamba create <5000|50000|100000> &7- place a machine"));
        sender.sendMessage(plugin.component("&d/gamba remove &7- remove nearest machine"));
        sender.sendMessage(plugin.component("&d/gamba list &7- list machines"));
        sender.sendMessage(plugin.component("&d/gamba reload &7- reload config"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("create", "remove", "list", "reload").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return List.of("5000", "50000", "100000").stream().filter(s -> s.startsWith(args[1])).toList();
        }
        return List.of();
    }
}
