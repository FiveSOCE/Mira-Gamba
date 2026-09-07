package com.mira.gamba.service;

import com.mira.gamba.MiraGambaPlugin;
import com.mira.gamba.model.SlotMachine;
import com.mira.gamba.model.SlotSymbol;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

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
        if (!economy.has(player, bet) || !economy.withdraw(player, bet)) {
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
        for (int i = 0; i < result.length; i++) result[i] = symbols.random();

        int updateTicks = Math.max(1, plugin.getConfig().getInt("machine.frame-update-ticks", 2));
        List<Integer> stops = plugin.getConfig().getIntegerList("machine.reel-stop-offset-ticks");
        if (stops.size() != REELS) stops = List.of(38, 46, 54, 62, 70);

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
                        SlotSymbol display = elapsed[0] >= finalStops.get(reel)
                                ? result[i]
                                : symbols.random();
                        finalFrames.get(i).setItem(new ItemStack(display.material()), false);
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
            if (taskId[0] >= 0) plugin.getServer().getScheduler().cancelTask(taskId[0]);

            for (int i = 0; i < FRAME_COUNT; i++) {
                finalFrames.get(i).setItem(new ItemStack(result[i].material()), false);
            }

            settle(player, machine, result, () -> spinning.remove(machine.id()));
        }, totalTicks + 2L);
    }

    private void settle(Player player, SlotMachine machine, SlotSymbol[] result, Runnable done) {
        BaseWin base = evaluateBase(result);

        if (base.multiplier() > 0D) {
            double payout = machine.bet() * base.multiplier();
            economy.deposit(player, payout);
            plugin.msg(
                    player,
                    "messages.win",
                    "%amount%", economy.format(payout),
                    "%lines%", Integer.toString(base.winningLines())
            );
            player.getWorld().playSound(
                    machine.trigger(),
                    Sound.ENTITY_PLAYER_LEVELUP,
                    0.8f,
                    1.4f
            );
        }

        int orbCount = 0;
        for (SlotSymbol symbol : result) if (symbol.orb()) orbCount++;

        int trigger = Math.max(1, plugin.getConfig().getInt("hold-spin.trigger-orbs", 6));
        if (orbCount >= trigger) {
            startHoldSpin(player, machine, result, done);
            return;
        }

        if (base.multiplier() <= 0D) {
            plugin.msg(player, "messages.lose");
            player.getWorld().playSound(
                    machine.trigger(),
                    Sound.BLOCK_NOTE_BLOCK_BASS,
                    0.8f,
                    0.8f
            );
        }
        done.run();
    }

    private BaseWin evaluateBase(SlotSymbol[] result) {
        double total = 0D;
        int winningLines = 0;

        var lines = plugin.getConfig().getConfigurationSection("paylines");
        if (lines != null) {
            for (String key : lines.getKeys(false)) {
                List<Integer> cells = lines.getIntegerList(key);
                if (cells.size() != REELS) continue;
                if (cells.stream().anyMatch(cell -> cell < 0 || cell >= FRAME_COUNT)) continue;

                SlotSymbol target = null;
                for (int cell : cells) {
                    SlotSymbol candidate = result[cell];
                    if (!candidate.wild() && !candidate.orb() && !candidate.scatter()) {
                        target = candidate;
                        break;
                    }
                }
                if (target == null) continue;

                int matched = 0;
                for (int cell : cells) {
                    SlotSymbol symbol = result[cell];
                    if (symbol.wild() || symbol.id().equals(target.id())) {
                        matched++;
                    } else {
                        break;
                    }
                }

                if (matched >= 3) {
                    double linePay = target.payFor(Math.min(matched, 5));
                    if (linePay > 0D) {
                        total += linePay;
                        winningLines++;
                    }
                }
            }
        }

        int scatters = 0;
        for (SlotSymbol symbol : result) if (symbol.scatter()) scatters++;
        if (scatters >= 3) {
            double scatterPay = switch (Math.min(scatters, 5)) {
                case 3 -> plugin.getConfig().getDouble("scatter.pay-3", 1.0D);
                case 4 -> plugin.getConfig().getDouble("scatter.pay-4", 5.0D);
                case 5 -> plugin.getConfig().getDouble("scatter.pay-5", 20.0D);
                default -> 0D;
            };
            total += scatterPay;
            if (scatterPay > 0D) winningLines++;
        }

        double cap = Math.max(
                1D,
                plugin.getConfig().getDouble("machine.max-base-payout-multiplier", 100D)
        );
        return new BaseWin(Math.min(total, cap), winningLines);
    }

    private void startHoldSpin(
            Player player,
            SlotMachine machine,
            SlotSymbol[] initial,
            Runnable done
    ) {
        Optional<SlotSymbol> orbOptional = symbols.firstOrb();
        if (orbOptional.isEmpty()) {
            done.run();
            return;
        }

        SlotSymbol orb = orbOptional.get();
        List<ItemFrame> frames = machines.frames(machine);
        boolean[] locked = new boolean[FRAME_COUNT];
        double[] values = new double[FRAME_COUNT];

        List<Double> configuredValues = plugin.getConfig().getDoubleList("hold-spin.orb-values");
        if (configuredValues.isEmpty()) {
            configuredValues = List.of(1D, 1D, 2D, 2D, 5D, 10D, 20D);
        }
        List<Double> orbValues = configuredValues;

        for (int i = 0; i < FRAME_COUNT; i++) {
            if (initial[i].orb()) {
                locked[i] = true;
                values[i] = randomOrbValue(orbValues);
                frames.get(i).setItem(orbItem(orb, values[i]), false);
            } else {
                frames.get(i).setItem(new ItemStack(Material.AIR), false);
            }
        }

        plugin.msg(player, "hold-spin.messages.start");
        runHoldRespins(player, machine, frames, orb, locked, values, orbValues, 3, 0, done);
    }

    private void runHoldRespins(
            Player player,
            SlotMachine machine,
            List<ItemFrame> frames,
            SlotSymbol orb,
            boolean[] locked,
            double[] values,
            List<Double> orbValues,
            int respins,
            int round,
            Runnable done
    ) {
        if (respins <= 0 || round >= 40 || allLocked(locked)) {
            finishHoldSpin(player, machine, locked, values, done);
            return;
        }

        int delay = Math.max(5, plugin.getConfig().getInt("hold-spin.respin-ticks", 18));
        double chance = Math.max(
                0D,
                Math.min(1D, plugin.getConfig().getDouble("hold-spin.new-orb-chance", 0.16D))
        );

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            boolean landed = false;

            for (int i = 0; i < FRAME_COUNT; i++) {
                if (locked[i]) continue;

                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    locked[i] = true;
                    values[i] = randomOrbValue(orbValues);
                    frames.get(i).setItem(orbItem(orb, values[i]), false);
                    landed = true;
                } else {
                    frames.get(i).setItem(
                            ThreadLocalRandom.current().nextBoolean()
                                    ? new ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                                    : new ItemStack(Material.AIR),
                            false
                    );
                }
            }

            player.getWorld().playSound(
                    machine.trigger(),
                    landed ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_NOTE_BLOCK_HAT,
                    0.7f,
                    landed ? 1.5f : 1.0f
            );

            int nextRespins = landed ? 3 : respins - 1;
            plugin.msg(
                    player,
                    "hold-spin.messages.respin",
                    "%respins%", Integer.toString(nextRespins),
                    "%orbs%", Integer.toString(countLocked(locked))
            );

            runHoldRespins(
                    player,
                    machine,
                    frames,
                    orb,
                    locked,
                    values,
                    orbValues,
                    nextRespins,
                    round + 1,
                    done
            );
        }, delay);
    }

    private void finishHoldSpin(
            Player player,
            SlotMachine machine,
            boolean[] locked,
            double[] values,
            Runnable done
    ) {
        double multiplier = 0D;
        for (int i = 0; i < values.length; i++) {
            if (locked[i]) multiplier += values[i];
        }

        boolean fullGrid = allLocked(locked);
        if (fullGrid) {
            multiplier += Math.max(
                    0D,
                    plugin.getConfig().getDouble("hold-spin.full-grid-grand-multiplier", 500D)
            );
        }

        double cap = Math.max(
                1D,
                plugin.getConfig().getDouble("hold-spin.max-payout-multiplier", 1000D)
        );
        multiplier = Math.min(multiplier, cap);

        double payout = machine.bet() * multiplier;
        if (payout > 0D) economy.deposit(player, payout);

        plugin.msg(
                player,
                fullGrid ? "hold-spin.messages.grand" : "hold-spin.messages.finish",
                "%amount%", economy.format(payout),
                "%multiplier%", String.format(Locale.US, "%.1f", multiplier),
                "%orbs%", Integer.toString(countLocked(locked))
        );

        player.getWorld().playSound(
                machine.trigger(),
                fullGrid ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP,
                1.0f,
                fullGrid ? 0.9f : 1.25f
        );

        done.run();
    }

    private ItemStack orbItem(SlotSymbol orb, double value) {
        ItemStack item = new ItemStack(orb.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.component("&6&l" + stripZero(value) + "x"));
        item.setItemMeta(meta);
        return item;
    }

    private static String stripZero(double value) {
        return value == Math.rint(value)
                ? Long.toString((long) value)
                : String.format(Locale.US, "%.1f", value);
    }

    private static double randomOrbValue(List<Double> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    private static boolean allLocked(boolean[] locked) {
        for (boolean value : locked) if (!value) return false;
        return true;
    }

    private static int countLocked(boolean[] locked) {
        int count = 0;
        for (boolean value : locked) if (value) count++;
        return count;
    }

    private record BaseWin(double multiplier, int winningLines) {}
}
