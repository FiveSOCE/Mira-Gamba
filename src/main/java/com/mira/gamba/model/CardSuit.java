package com.mira.gamba.model;

import org.bukkit.Material;

public enum CardSuit {
    SPADES("Spades", Material.BLACK_DYE, false),
    CLUBS("Clubs", Material.COAL, false),
    HEARTS("Hearts", Material.RED_DYE, true),
    DIAMONDS("Diamonds", Material.REDSTONE, true);

    private final String display;
    private final Material material;
    private final boolean red;

    CardSuit(String display, Material material, boolean red) {
        this.display = display;
        this.material = material;
        this.red = red;
    }

    public String display() { return display; }
    public Material material() { return material; }
    public boolean red() { return red; }
}
