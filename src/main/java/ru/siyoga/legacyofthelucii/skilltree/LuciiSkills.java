package ru.siyoga.legacyofthelucii.skilltree;

import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;

import java.util.LinkedHashSet;
import java.util.Set;

public final class LuciiSkills {
    private LuciiSkills() {
    }

    public static Set<String> unlockedIds(LuciiPlayerState state) {
        return Set.copyOf(access(state));
    }

    public static boolean isUnlocked(LuciiPlayerState state, LuciiSkill skill) {
        return skill != null && access(state).contains(skill.id());
    }

    public static boolean unlock(LuciiPlayerState state, LuciiSkill skill) {
        if (skill == null || state.legacy() != skill.legacy()) return false;
        if (skill.prerequisite() != null && !isUnlocked(state, skill.prerequisite())) return false;
        boolean changed = access(state).add(skill.id());
        if (changed) syncInventorySlots(state);
        return changed;
    }

    public static boolean unlockAll(LuciiPlayerState state, LuciiLegacy legacy) {
        if (legacy == LuciiLegacy.NONE) return false;
        boolean changed = false;
        for (LuciiSkill skill : LuciiSkill.values()) {
            if (skill.legacy() == legacy) changed |= access(state).add(skill.id());
        }
        syncInventorySlots(state);
        return changed;
    }

    public static void lockAll(LuciiPlayerState state) {
        access(state).clear();
        syncInventorySlots(state);
    }

    public static void syncInventorySlots(LuciiPlayerState state) {
        int slots = 3;
        LuciiLegacy legacy = state.legacy();
        for (LuciiSkill skill : LuciiSkill.values()) {
            if (skill.legacy() == legacy
                    && skill.category() == LuciiSkill.Category.INVENTORY
                    && isUnlocked(state, skill)) {
                slots = Math.max(slots, skill.inventorySlots());
            }
        }
        state.setRoyalArmsUnlockedSlots(slots);
    }

    public static Set<String> mutableUnlockedIds(LuciiPlayerState state) {
        return access(state);
    }

    private static Set<String> access(LuciiPlayerState state) {
        return ((LuciiSkillStateAccess) (Object) state).legacyOfTheLucii$getUnlockedSkills();
    }
}
