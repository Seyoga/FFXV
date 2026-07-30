package ru.siyoga.legacyofthelucii.legacy;

public enum LuciiLegacy {
    NONE("none"),
    NOCTIS("noctis"),
    ARDYN("ardyn");

    private final String id;

    LuciiLegacy(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static LuciiLegacy byId(String id) {
        for (LuciiLegacy legacy : values()) {
            if (legacy.id.equals(id)) {
                return legacy;
            }
        }

        return NONE;
    }
}
