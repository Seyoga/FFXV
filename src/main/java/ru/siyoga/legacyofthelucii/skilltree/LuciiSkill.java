package ru.siyoga.legacyofthelucii.skilltree;

import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;

public enum LuciiSkill {
    NOCTIS_WARP("noctis_warp", LuciiLegacy.NOCTIS, Category.BASIC),
    NOCTIS_BIND("noctis_bind", LuciiLegacy.NOCTIS, Category.BASIC),
    NOCTIS_WALL("noctis_wall", LuciiLegacy.NOCTIS, Category.ABILITY),
    NOCTIS_GUARD("noctis_guard", LuciiLegacy.NOCTIS, Category.ABILITY),

    ARDYN_SHADOW_STEP("ardyn_shadow_step", LuciiLegacy.ARDYN, Category.BASIC),
    ARDYN_WARP("ardyn_warp", LuciiLegacy.ARDYN, Category.BASIC),
    ARDYN_BIND("ardyn_bind", LuciiLegacy.ARDYN, Category.BASIC),
    ARDYN_BARRAGE("ardyn_barrage", LuciiLegacy.ARDYN, Category.ABILITY),
    ARDYN_MASQUERADE("ardyn_masquerade", LuciiLegacy.ARDYN, Category.ABILITY),
    ARDYN_DARK_TORNADO("ardyn_dark_tornado", LuciiLegacy.ARDYN, Category.ABILITY),
    ARDYN_CERBERUS("ardyn_cerberus", LuciiLegacy.ARDYN, Category.ABILITY),
    ARDYN_TIME_SLOW("ardyn_time_slow", LuciiLegacy.ARDYN, Category.ABILITY),
    ARDYN_OVERKILL("ardyn_overkill", LuciiLegacy.ARDYN, Category.ABILITY),

    NOCTIS_INVENTORY_6("noctis_inventory_6", LuciiLegacy.NOCTIS, Category.INVENTORY, 6, null),
    NOCTIS_INVENTORY_9("noctis_inventory_9", LuciiLegacy.NOCTIS, Category.INVENTORY, 9, NOCTIS_INVENTORY_6),
    NOCTIS_INVENTORY_12("noctis_inventory_12", LuciiLegacy.NOCTIS, Category.INVENTORY, 12, NOCTIS_INVENTORY_9),
    NOCTIS_INVENTORY_15("noctis_inventory_15", LuciiLegacy.NOCTIS, Category.INVENTORY, 15, NOCTIS_INVENTORY_12),
    NOCTIS_INVENTORY_18("noctis_inventory_18", LuciiLegacy.NOCTIS, Category.INVENTORY, 18, NOCTIS_INVENTORY_15),

    ARDYN_INVENTORY_6("ardyn_inventory_6", LuciiLegacy.ARDYN, Category.INVENTORY, 6, null),
    ARDYN_INVENTORY_9("ardyn_inventory_9", LuciiLegacy.ARDYN, Category.INVENTORY, 9, ARDYN_INVENTORY_6),
    ARDYN_INVENTORY_12("ardyn_inventory_12", LuciiLegacy.ARDYN, Category.INVENTORY, 12, ARDYN_INVENTORY_9),
    ARDYN_INVENTORY_15("ardyn_inventory_15", LuciiLegacy.ARDYN, Category.INVENTORY, 15, ARDYN_INVENTORY_12),
    ARDYN_INVENTORY_18("ardyn_inventory_18", LuciiLegacy.ARDYN, Category.INVENTORY, 18, ARDYN_INVENTORY_15);

    private final String id;
    private final LuciiLegacy legacy;
    private final Category category;
    private final int inventorySlots;
    private final LuciiSkill prerequisite;

    LuciiSkill(String id, LuciiLegacy legacy, Category category) {
        this(id, legacy, category, 0, null);
    }

    LuciiSkill(String id, LuciiLegacy legacy, Category category, int inventorySlots, LuciiSkill prerequisite) {
        this.id = id;
        this.legacy = legacy;
        this.category = category;
        this.inventorySlots = inventorySlots;
        this.prerequisite = prerequisite;
    }

    public String id() {
        return id;
    }

    public LuciiLegacy legacy() {
        return legacy;
    }

    public Category category() {
        return category;
    }

    public int inventorySlots() {
        return inventorySlots;
    }

    public LuciiSkill prerequisite() {
        return prerequisite;
    }

    public static LuciiSkill byId(String id) {
        for (LuciiSkill skill : values()) {
            if (skill.id.equals(id)) return skill;
        }
        return null;
    }

    public enum Category {
        BASIC,
        ABILITY,
        INVENTORY
    }
}
