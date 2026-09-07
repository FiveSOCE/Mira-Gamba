package com.mira.gamba.service;

import com.mira.gamba.MiraGambaPlugin;
import com.mira.gamba.model.SlotSymbol;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class SymbolService {
    private final MiraGambaPlugin plugin;
    private List<SlotSymbol> symbols = List.of();

    public SymbolService(MiraGambaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        List<SlotSymbol> loaded = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("symbols");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(id);
                if (s == null) continue;
                Material material = Material.matchMaterial(s.getString("material", ""));
                if (material == null) continue;
                int weight = Math.max(1, s.getInt("weight", 1));
                double multiplier = Math.max(0D, s.getDouble("multiplier", 0D));
                boolean jackpot = s.getBoolean("jackpot", false);
                loaded.add(new SlotSymbol(id, material, weight, multiplier, jackpot));
            }
        }
        symbols = List.copyOf(loaded);
    }

    public SlotSymbol random() {
        if (symbols.isEmpty()) throw new IllegalStateException("No slot symbols configured");
        int total = symbols.stream().mapToInt(SlotSymbol::weight).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (SlotSymbol symbol : symbols) {
            roll -= symbol.weight();
            if (roll < 0) return symbol;
        }
        return symbols.get(symbols.size() - 1);
    }

    public List<SlotSymbol> symbols() {
        return symbols;
    }
}
