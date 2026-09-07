package com.mira.gamba.service;

import com.mira.gamba.MiraGambaPlugin;
import com.mira.gamba.model.SlotMachine;
import com.mira.gamba.model.SlotSymbol;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SpinService {
    private static final int ROWS = 3;
    private static final int REELS = 5;
    private static final int FRAME_COUNT = ROWS * REELS;

    private final MiraGambaPlugin plugin;
    private final MachineService machines;
    private final SymbolService symbols;
    private final EconomyService economy;
    private final Set<UUID> spinning = ConcurrentHashMap.newKeySet();

    public SpinService(
            MiraGambaPlugin plugin,
            MachineService machines,
            SymbolService symbols,
            EconomyService economy
    ) {
        this.plugin = plugin;
        this.machines = machines;
        this.symbols = symbols;
        this.economy = economy;
    }

    public void spin(Player player, SlotMachine machine) {
        if (!player.hasPermission("miragamba.use")) return;

        if (!spinning.add(machine.id())) {
            plugin.msg(player, "messages.busy");
            return;
        }

        double bet = machine.bet();
        if (!economy.has(player, bet)) {
            spinning.remove(machine.id());
            plugin.msg(player, "messages.no-money", "%amount%", economy.format(bet));
            return;
        }

        if (!economy.withdraw(player, bet)) {
            spinning.remove(machine.id());
            plugin.msg(player, "messages.no-money", "%amount%", economy.format(bet));
            return;
        }

        plugin.msg(player, "messages.charged", "%amount%", economy.format(bet));

        List<ItemFrame> frames = machines.frames(machine);
        if (frames.size() != FRAME_COUNT) {
            machines.respawnMissingFrames();
            frames = machines.frames(machine);
        }

        if (frames.size() != FRAME_COUNT) {
            economy.deposit(player, bet);
            spinning.remove(machine.id());
            player.sendMessage(Component.text(
                    "This slot machine is missing item frames; your bet was refunded."
            ));
            return;
        }

        SlotSymbol[] result = new SlotSymbol[FRAME_COUNT];
        for (int i = 0; i < result.length; i++) {
            result[i] = symbols.random();
        }

        int updateTicks = Math.max(
                1,
                plugin.getConfig().getInt("machine.frame-update-ticks", 2)
        );

        List<Integer> stops = plugin.getConfig().getIntegerList(
                "machine.reel-stop-offset-ticks"
        );
        if (stops.size() != REELS) {
            stops = List.of(38, 46, 54, 62, 70);
        }

        int totalTicks = Math.max(
                stops.get(stops.size() - 1) + 6,
                plugin.getConfig().getInt("machine.spin-ticks", 76)
        );

        List<ItemFrame> finalFrames = frames;
        List<Integer> finalStops = stops;
        final int[] elapsed = {0};
        final int[] taskId = {-1};

        taskId[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(
                plugin,
                () -> {
                    elapsed[0] += updateTicks;

                    for (int i = 0; i < FRAME_COUNT; i++) {
                        int reel = i % REELS;
                        int stopAt = finalStops.get(reel);
                        SlotSymbol display = elapsed[0] >= stopAt
                                ? result[i]
                                : symbols.random();

                        finalFrames.get(i).setItem(
                                new ItemStack(display.material()),
                                false
                        );
                    }

                    player.getWorld().playSound(
                            machine.trigger(),
                            Sound.BLOCK_NOTE_BLOCK_HAT,
                            0.35f,
                            1.6f
                    );

                    if (elapsed[0] >= totalTicks && taskId[0] >= 0) {
                        plugin.getServer().getScheduler().cancelTask(taskId[0]);
                    }
                },
                0L,
                updateTicks
        );

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (taskId[0] >= 0) {
                plugin.getServer().getScheduler().cancelTask(taskId[0]);
            }

            for (int i = 0; i < FRAME_COUNT; i++) {
                finalFrames.get(i).setItem(
                        new ItemStack(result[i].material()),
                        false
                );
            }

            settle(player, machine, result);
            spinning.remove(machine.id());
        }, totalTicks + 2L);
    }

    private void settle(Player player, SlotMachine machine, SlotSymbol[] result) {
        double multiplier = 0D;
        boolean jackpot = false;
        int winningLines = 0;

        var lines = plugin.getConfig().getConfigurationSection("paylines");
        if (lines != null) {
            for (String key : lines.getKeys(false)) {
                List<Integer> cells = lines.getIntegerList(key);
                if (cells.size() != REELS) continue;
                if (cells.stream().anyMatch(cell -> cell < 0 || cell >= FRAME_COUNT)) {
                    continue;
                }

                SlotSymbol first = result[cells.get(0)];
                boolean allMatch = true;

                for (int i = 1; i < cells.size(); i++) {
                    if (!first.id().equals(result[cells.get(i)].id())) {
                        allMatch = false;
                        break;
                    }
                }

                if (allMatch) {
                    multiplier += first.multiplier();
                    jackpot |= first.jackpot();
                    winningLines++;
                }
            }
        }

        double cap = Math.max(
                1D,
                plugin.getConfig().getDouble(
                        "machine.max-payout-multiplier",
                        100D
                )
        );
        multiplier = Math.min(multiplier, cap);

        if (multiplier <= 0D) {
            plugin.msg(player, "messages.lose");
            player.getWorld().playSound(
                    machine.trigger(),
                    Sound.BLOCK_NOTE_BLOCK_BASS,
                    0.8f,
                    0.8f
            );
            return;
        }

        double payout = machine.bet() * multiplier;
        economy.deposit(player, payout);

        plugin.msg(
                player,
                "messages.win",
                "%amount%",
                economy.format(payout),
                "%lines%",
                Integer.toString(winningLines)
        );

        player.getWorld().playSound(
                machine.trigger(),
                jackpot
                        ? Sound.UI_TOAST_CHALLENGE_COMPLETE
                        : Sound.ENTITY_PLAYER_LEVELUP,
                1f,
                jackpot ? 1.15f : 1.4f
        );

        if (jackpot) {
            String text = plugin.raw("messages.jackpot")
                    .replace("%player%", player.getName())
                    .replace("%amount%", economy.format(payout));

            plugin.getServer().broadcast(plugin.component(text));
        }
    }
}
