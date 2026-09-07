package com.mira.gamba.model;

import org.bukkit.Material;

public record SlotSymbol(String id, Material material, int weight, double multiplier, boolean jackpot) {}
