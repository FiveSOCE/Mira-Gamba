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
    private final MiraGambaPlugin plugin;
    private final MachineService machines;
    private final SymbolService symbols;
    private final EconomyService economy;
    private final Set<UUID> spinning = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public SpinService(MiraGambaPlugin plugin, MachineService machines, SymbolService symbols, EconomyService economy) {
        this.plugin = plugin;
        this.machines = machines;
        this.symbols = symbols;
        this.economy = economy;
    }

    public void spin(Player player, SlotMachine machine) {
        if (!player.hasPermission("miragamba.use")) return;

        long now = System.currentTimeMillis();
        long cooldown = Math.max(0L, plugin.getConfig().getLong("machine.cooldown-millis", 1500L));
        if (cooldowns.getOrDefault(player.getUniqueId(), 0L) > now) {
            plugin.msg(player, "messages.cooldown");
            return;
        }

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

        cooldowns.put(player.getUniqueId(), now + cooldown);
        plugin.msg(player, "messages.charged", "%amount%", economy.format(bet));

        List<ItemFrame> frames = machines.frames(machine);
        if (frames.size() != 9) {
            machines.respawnMissingFrames();
            frames = machines.frames(machine);
        }
        if (frames.size() != 9) {
            economy.deposit(player, bet);
            spinning.remove(machine.id());
            player.sendMessage(Component.text("This slot machine is missing item frames; your bet was refunded."));
            return;
        }

        SlotSymbol[] result = new SlotSymbol[9];
        for (int i = 0; i < result.length; i++) result[i] = symbols.random();

        int totalTicks = Math.max(30, plugin.getConfig().getInt("machine.spin-ticks", 70));
        int updateTicks = Math.max(1, plugin.getConfig().getInt("machine.frame-update-ticks", 2));
        int leftStop = plugin.getConfig().getInt("machine.reel-stop-offset-ticks.left", 40);
        int middleStop = plugin.getConfig().getInt("machine.reel-stop-offset-ticks.middle", 52);
        int rightStop = plugin.getConfig().getInt("machine.reel-stop-offset-ticks.right", 64);

        List<ItemFrame> finalFrames = frames;
        final int[] elapsed = {0};
        final int[] taskId = {-1};
        taskId[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            elapsed[0] += updateTicks;

            for (int i = 0; i < 9; i++) {
                int col = i % 3;
                int stopAt = col == 0 ? leftStop : col == 1 ? middleStop : rightStop;
                SlotSymbol display = elapsed[0] >= stopAt ? result[i] : symbols.random();
                finalFrames.get(i).setItem(new ItemStack(display.material()), false);
            }

            player.getWorld().playSound(machine.trigger(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 1.6f);

            if (elapsed[0] >= totalTicks && taskId[0] >= 0) {
                plugin.getServer().getScheduler().cancelTask(taskId[0]);
            }
        }, 0L, updateTicks);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (taskId[0] >= 0) plugin.getServer().getScheduler().cancelTask(taskId[0]);
            for (int i = 0; i < 9; i++) finalFrames.get(i).setItem(new ItemStack(result[i].material()), false);
            settle(player, machine, result);
            spinning.remove(machine.id());
        }, totalTicks + 2L);
    }

    private void settle(Player player, SlotMachine machine, SlotSymbol[] result) {
        double multiplier = 0D;
        boolean jackpot = false;

        var lines = plugin.getConfig().getConfigurationSection("paylines");
        if (lines != null) {
            for (String key : lines.getKeys(false)) {
                List<Integer> cells = lines.getIntegerList(key);
                if (cells.size() != 3) continue;
                int a = cells.get(0), b = cells.get(1), c = cells.get(2);
                if (a < 0 || a > 8 || b < 0 || b > 8 || c < 0 || c > 8) continue;

                SlotSymbol first = result[a];
                if (first.id().equals(result[b].id()) && first.id().equals(result[c].id())) {
                    multiplier += first.multiplier();
                    jackpot |= first.jackpot();
                }
            }
        }

        double cap = Math.max(1D, plugin.getConfig().getDouble("machine.max-payout-multiplier", 100D));
        multiplier = Math.min(multiplier, cap);

        if (multiplier <= 0D) {
            plugin.msg(player, "messages.lose");
            player.getWorld().playSound(machine.trigger(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            return;
        }

        double payout = machine.bet() * multiplier;
        economy.deposit(player, payout);
        plugin.msg(player, "messages.win", "%amount%", economy.format(payout));
        player.getWorld().playSound(machine.trigger(), jackpot ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, jackpot ? 1.15f : 1.4f);

        if (jackpot) {
            String text = plugin.raw("messages.jackpot")
                    .replace("%player%", player.getName())
                    .replace("%amount%", economy.format(payout));
            plugin.getServer().broadcast(plugin.component(text));
        }
    }
}
