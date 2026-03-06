package dev.bekololek.crossthefloor.models;

public enum Difficulty {
    EASY("easy"),
    MEDIUM("medium"),
    HARD("hard");

    private final String key;

    Difficulty(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static Difficulty fromString(String s) {
        for (Difficulty d : values()) {
            if (d.key.equalsIgnoreCase(s)) return d;
        }
        return null;
    }
}
