package com.mira.gamba.model;

public record PlayingCard(int value, CardSuit suit) {
    public String rankName() {
        return switch (value) {
            case 11 -> "Jack";
            case 12 -> "Queen";
            case 13 -> "King";
            case 14 -> "Ace";
            default -> Integer.toString(value);
        };
    }

    public String displayName() {
        return rankName() + " of " + suit.display();
    }
}
