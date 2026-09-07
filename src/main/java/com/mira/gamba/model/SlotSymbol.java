package com.mira.gamba.model;

import org.bukkit.Material;

public record SlotSymbol(
        String id,
        Material material,
        int weight,
        double pay3,
        double pay4,
        double pay5,
        boolean wild,
        boolean orb,
        boolean scatter
) {
    public double payFor(int count) {
        return switch (count) {
            case 3 -> pay3;
            case 4 -> pay4;
            case 5 -> pay5;
            default -> 0D;
        };
    }
}
