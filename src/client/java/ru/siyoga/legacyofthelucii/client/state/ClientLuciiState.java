package ru.siyoga.legacyofthelucii.client.state;

import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;

public final class ClientLuciiState {
    private static LuciiLegacy legacy = LuciiLegacy.NONE;
    private static int mana;
    private static int maxMana = 100;
    private static boolean royalArmsActive;
    private static int ardynWarpCharges;

    private ClientLuciiState() {
    }

    public static LuciiLegacy legacy() {
        return legacy;
    }

    public static int mana() {
        return mana;
    }

    public static int maxMana() {
        return maxMana;
    }

    public static boolean hasLegacy() {
        return legacy != LuciiLegacy.NONE;
    }

    public static boolean royalArmsActive() {
        return royalArmsActive;
    }

    public static int ardynWarpCharges() {
        return ardynWarpCharges;
    }

    public static void update(LuciiLegacy legacy, int mana, int maxMana, boolean royalArmsActive, int ardynWarpCharges) {
        ClientLuciiState.legacy = legacy;
        ClientLuciiState.mana = Math.max(0, mana);
        ClientLuciiState.maxMana = Math.max(1, maxMana);
        ClientLuciiState.royalArmsActive = royalArmsActive && legacy != LuciiLegacy.NONE;
        ClientLuciiState.ardynWarpCharges = Math.max(0, Math.min(12, ardynWarpCharges));
    }

    public static void reset() {
        legacy = LuciiLegacy.NONE;
        mana = 0;
        maxMana = 100;
        royalArmsActive = false;
        ardynWarpCharges = 0;
    }
}
