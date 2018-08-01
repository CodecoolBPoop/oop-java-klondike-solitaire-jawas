package com.codecool.klondike;

public enum Suits {
    HEARTS (1, "RED"),
    DIAMONDS (2, "RED"),
    SPADES (3, "BLACK"),
    CLUBS (4, "BLACK");

    public int suitId;
    public String suitColor;

    Suits(int suitId, String suitColor) {
        this.suitId = suitId;
        this.suitColor = suitColor;
    }

    public int returnId() {
        return suitId;
    }

    public String returnColor() {
        return suitColor;
    }
}
