package ru.siyoga.legacyofthelucii.client.gui.skilltree;

import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.skilltree.LuciiSkill;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ClientSkillTreeState {
    private static final Set<String> UNLOCKED = new LinkedHashSet<>();
    private static int unlockedInventorySlots = 3;

    private ClientSkillTreeState() {
    }

    public static void update(Set<String> ids, int inventorySlots) {
        UNLOCKED.clear();
        UNLOCKED.addAll(ids);
        unlockedInventorySlots = Math.max(3, Math.min(18, inventorySlots));
    }

    public static boolean isUnlocked(LuciiSkill skill) {
        return skill != null && UNLOCKED.contains(skill.id());
    }

    public static int unlockedInventorySlots() {
        return unlockedInventorySlots;
    }

    public static LuciiSkill requiredForKey(String key, LuciiLegacy legacy) {
        if (key == null) return null;
        return switch (key) {
            case "key.legacyofthelucii.royal_arms.wall" -> LuciiSkill.NOCTIS_WALL;
            case "key.legacyofthelucii.royal_arms.guard" -> LuciiSkill.NOCTIS_GUARD;
            case "key.legacyofthelucii.royal_arms.bind" ->
                    legacy == LuciiLegacy.ARDYN ? LuciiSkill.ARDYN_BIND : LuciiSkill.NOCTIS_BIND;
            case "key.legacyofthelucii.royal_arms.warp" ->
                    legacy == LuciiLegacy.ARDYN ? LuciiSkill.ARDYN_WARP : LuciiSkill.NOCTIS_WARP;
            case "key.legacyofthelucii.royal_arms.ardyn_shadow_step" -> LuciiSkill.ARDYN_SHADOW_STEP;
            case "key.legacyofthelucii.royal_arms.ardyn_point_warp" -> LuciiSkill.ARDYN_WARP;
            case "key.legacyofthelucii.royal_arms.ardyn_dark_tornado" -> LuciiSkill.ARDYN_DARK_TORNADO;
            case "key.legacyofthelucii.masquerade.open" -> LuciiSkill.ARDYN_MASQUERADE;
            case "key.legacyofthelucii.ardyn_sniper" -> LuciiSkill.ARDYN_CERBERUS;
            default -> null;
        };
    }

    public static void reset() {
        UNLOCKED.clear();
        unlockedInventorySlots = 3;
    }
}
