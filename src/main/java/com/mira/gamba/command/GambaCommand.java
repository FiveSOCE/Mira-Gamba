package com.mira.gamba.command;

import com.mira.gamba.MiraGambaPlugin;
import com.mira.gamba.model.CardMachine;
import com.mira.gamba.model.SlotMachine;
import com.mira.gamba.service.CardMachineService;
import com.mira.gamba.service.MachineService;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class GambaCommand implements TabExecutor {
    private final MiraGambaPlugin plugin;
    private final MachineService slots;
    private final CardMachineService cards;

    public GambaCommand(MiraGambaPlugin plugin, MachineService slots, CardMachineService cards) {
        this.plugin = plugin;
        this.slots = slots;
        this.cards = cards;
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

        if (args[0].equalsIgnoreCase("cards")) {
            return handleCards(sender, args);
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.component("&cUsage: /gamba create <5000|50000|100000|1000000>"));
                    return true;
                }
                long bet = parseBet(sender, args[1]);
                if (bet < 0) return true;
                SlotMachine machine = slots.create(player, bet);
                sender.sendMessage(plugin.component("&aCreated slot machine &f" + machine.id()
                        + " &awith wager &f" + plugin.money(bet) + "&a."));
                return true;
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                SlotMachine machine = slots.nearest(player.getLocation(), 6D);
                if (machine == null) {
                    sender.sendMessage(plugin.component("&cNo slot machine found within 6 blocks."));
                    return true;
                }
                slots.remove(machine);
                sender.sendMessage(plugin.component("&aRemoved slot machine &f" + machine.id() + "&a."));
                return true;
            }
            case "list" -> {
                sender.sendMessage(plugin.component("&5&lMiraGamba &8>> &7Slot machines: &f" + slots.all().size()));
                for (SlotMachine machine : slots.all()) {
                    sender.sendMessage(plugin.component("&7- &f" + machine.id()
                            + " &7Bet &f" + plugin.money(machine.bet())));
                }
                sender.sendMessage(plugin.component("&7Card tables: &f" + cards.all().size()));
                for (CardMachine machine : cards.all()) {
                    sender.sendMessage(plugin.component("&7- &f" + machine.id()
                            + " &7Bet &f" + plugin.money(machine.bet())));
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

    private boolean handleCards(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.component("&cUsage: /gamba cards <create|remove|list> [bet]"));
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.component("&cUsage: /gamba cards create <5000|50000|100000|1000000>"));
                    return true;
                }
                long bet = parseBet(sender, args[2]);
                if (bet < 0) return true;

                CardMachine machine = cards.create(player, bet);
                sender.sendMessage(plugin.component("&aCreated four-guess card table &f" + machine.id()
                        + " &awith wager &f" + plugin.money(bet) + "&a."));
                return true;
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (!cards.removeNearest(player.getLocation(), 8D)) {
                    sender.sendMessage(plugin.component("&cNo card table found within 8 blocks."));
                    return true;
                }
                sender.sendMessage(plugin.component("&aRemoved nearest four-guess card table."));
                return true;
            }
            case "list" -> {
                sender.sendMessage(plugin.component("&5&lMiraGamba &8>> &7Card tables: &f" + cards.all().size()));
                for (CardMachine machine : cards.all()) {
                    sender.sendMessage(plugin.component("&7- &f" + machine.id()
                            + " &7Bet &f" + plugin.money(machine.bet())));
                }
                return true;
            }
            default -> {
                sender.sendMessage(plugin.component("&cUsage: /gamba cards <create|remove|list> [bet]"));
                return true;
            }
        }
    }

    private long parseBet(CommandSender sender, String raw) {
        long bet;
        try {
            bet = Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            sender.sendMessage(plugin.component("&cInvalid wager."));
            return -1L;
        }

        if (!plugin.allowedBet(bet)) {
            sender.sendMessage(plugin.component("&cAllowed wagers: $5,000, $50,000, $100,000, $1,000,000."));
            return -1L;
        }
        return bet;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(plugin.component("&5&lMiraGamba"));
        sender.sendMessage(plugin.component("&d/gamba create <bet> &7- place a 3x5 slot machine"));
        sender.sendMessage(plugin.component("&d/gamba cards create <bet> &7- place a four-guess card table"));
        sender.sendMessage(plugin.component("&d/gamba remove &7- remove nearest slot machine"));
        sender.sendMessage(plugin.component("&d/gamba cards remove &7- remove nearest card table"));
        sender.sendMessage(plugin.component("&d/gamba list &7- list all machines"));
        sender.sendMessage(plugin.component("&d/gamba reload &7- reload config"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "remove", "list", "reload", "cards").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return bets(args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("cards")) {
            return List.of("create", "remove", "list").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("cards")
                && args[1].equalsIgnoreCase("create")) {
            return bets(args[2]);
        }

        return List.of();
    }

    private List<String> bets(String prefix) {
        return List.of("5000", "50000", "100000", "1000000").stream()
                .filter(s -> s.startsWith(prefix)).toList();
    }
}
