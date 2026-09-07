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
                double pay3 = Math.max(0D, s.getDouble("pay-3", 0D));
                double pay4 = Math.max(pay3, s.getDouble("pay-4", pay3));
                double pay5 = Math.max(pay4, s.getDouble("pay-5", pay4));

                loaded.add(new SlotSymbol(
                        id,
                        material,
                        weight,
                        pay3,
                        pay4,
                        pay5,
                        s.getBoolean("wild", false),
                        s.getBoolean("orb", false),
                        s.getBoolean("scatter", false)
                ));
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

    public Optional<SlotSymbol> firstOrb() {
        return symbols.stream().filter(SlotSymbol::orb).findFirst();
    }

    public List<SlotSymbol> symbols() {
        return symbols;
    }
}
