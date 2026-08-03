package ru.siyoga.legacyofthelucii.legacy;

import net.minecraft.nbt.NbtCompound;
public final class LuciiPlayerState {
    private static final int DEFAULT_MAX_MANA = 100;
    private static final int DEFAULT_MANA_REGEN_INTERVAL = 20;
    private static final int DEFAULT_MANA_REGEN_DELAY = 20 * 5;
    private static final int ARDYN_OVERKILL_MANA_REGEN_INTERVAL = 4;
    private static final String MANA_KEY = "Mana";
    private static final String MAX_MANA_KEY = "MaxMana";
    private static final String LEVEL_KEY = "Level";
    private static final String EXPERIENCE_KEY = "Experience";
    private static final String LEGACY_KEY = "Legacy";
    private static final String ROYAL_ARMS_ACTIVE_KEY = "RoyalArmsActive";
    private static final String ROYAL_ARMS_FILTER_KEY = "RoyalArmsFilter";
    private static final String ARDYN_WARP_CHARGES_KEY = "ArdynWarpCharges";
    private static final String ARDYN_OVERKILL_ACTIVE_KEY = "ArdynOverkillActive";
    private static final String REGEN_TIMER_KEY = "ManaRegenTimer";
    private static final String MANA_REGEN_DELAY_KEY = "ManaRegenDelay";
    private static final int MAX_ARDYN_WARP_CHARGES = 12;
    private int mana = DEFAULT_MAX_MANA;
    private int maxMana = DEFAULT_MAX_MANA;
    private int level = 1;
    private int experience;
    private LuciiLegacy legacy = LuciiLegacy.NONE;
    private boolean royalArmsActive;
    private RoyalArmsInventoryFilter royalArmsFilter = RoyalArmsInventoryFilter.ALL;
    private int ardynWarpCharges;
    private boolean ardynOverkillActive;
    private int regenTimer;
    private int manaRegenDelay;

    public int mana() {
        return mana;
    }
    public int maxMana() {
        return maxMana;
    }

    public int level() {
        return level;
    }

    public int experience() {
        return experience;
    }

    public LuciiLegacy legacy() {
        return legacy;
    }

    public void unlockLegacy(LuciiLegacy legacy) {
        if (legacy == LuciiLegacy.NONE) {
            return;
        }

        if (this.legacy != legacy) {
            royalArmsActive = false;
        }
        this.legacy = legacy;
        this.mana = Math.max(mana, maxMana / 2);
    }

    public boolean hasLegacy() {
        return legacy != LuciiLegacy.NONE;
    }

    public boolean royalArmsActive() {
        return royalArmsActive;
    }

    public void setRoyalArmsActive(boolean active) {
        royalArmsActive = active && hasLegacy();
    }

    public RoyalArmsInventoryFilter royalArmsFilter() {
        return royalArmsFilter;
    }
    public int ardynWarpCharges() {
        return ardynWarpCharges;
    }

    public void setArdynWarpCharges(int charges) {
        ardynWarpCharges = Math.max(0, Math.min(MAX_ARDYN_WARP_CHARGES, charges));
    }

    public boolean addArdynWarpCharge() {
        if (ardynWarpCharges >= MAX_ARDYN_WARP_CHARGES) {
            return false;
        }

        ardynWarpCharges++;
        return true;
    }

    public boolean ardynOverkillActive() {
        return ardynOverkillActive;
    }
    public boolean beginArdynOverkill() {
        if (legacy != LuciiLegacy.ARDYN || ardynOverkillActive) {
            return false;
        }

        ardynOverkillActive = true;
        mana = 0;
        manaRegenDelay = 0;
        regenTimer = 0;
        return true;
    }

    public void endArdynOverkill() {
        ardynOverkillActive = false;
        regenTimer = 0;
        manaRegenDelay = 0;
    }
    public void setRoyalArmsFilter(RoyalArmsInventoryFilter filter) {
        royalArmsFilter = filter == null ? RoyalArmsInventoryFilter.ALL : filter;
    }

    public boolean spendMana(int amount) {
        if (amount <= 0) {
            return true;
        }

        // During Overkill the mana bar is a recovery timer, not a spendable resource.
        if (ardynOverkillActive || mana < amount) {
            return false;
        }
        mana -= amount;
        manaRegenDelay = DEFAULT_MANA_REGEN_DELAY;
        regenTimer = 0;
        return true;
    }

    public boolean hasMana(int amount) {
        return !ardynOverkillActive && (amount <= 0 || mana >= amount);
    }

    public boolean restoreMana() {
        if (mana >= maxMana && manaRegenDelay == 0 && regenTimer == 0) {
            return false;
        }

        mana = maxMana;
        manaRegenDelay = 0;
        regenTimer = 0;
        return true;
    }
    public boolean addMana(int amount) {
        if (amount <= 0 || mana >= maxMana) {
            return false;
        }

        int previousMana = mana;
        mana = Math.min(maxMana, mana + amount);
        return mana != previousMana;
    }
    public void addExperience(int amount) {
        if (amount <= 0) {
            return;
        }

        experience += amount;
        while (experience >= experienceForNextLevel()) {
            experience -= experienceForNextLevel();
            level++;
            maxMana += 10;
            mana = maxMana;
        }
    }
    public void tick() {
        if (ardynOverkillActive) {
            regenTimer++;
            if (regenTimer >= ARDYN_OVERKILL_MANA_REGEN_INTERVAL) {
                regenTimer = 0;
                if (mana < maxMana) {
                    mana++;
                }
            }
            return;
        }

        if (manaRegenDelay > 0) {
            manaRegenDelay--;
            return;
        }
        regenTimer++;
        if (regenTimer < DEFAULT_MANA_REGEN_INTERVAL) {
            return;
        }

        regenTimer = 0;
        if (mana < maxMana) {
            mana++;
        }
    }
    public void readNbt(NbtCompound nbt) {
        mana = nbt.contains(MANA_KEY) ? nbt.getInt(MANA_KEY) : DEFAULT_MAX_MANA;
        maxMana = nbt.contains(MAX_MANA_KEY) ? nbt.getInt(MAX_MANA_KEY) : DEFAULT_MAX_MANA;
        level = Math.max(1, nbt.getInt(LEVEL_KEY));
        experience = Math.max(0, nbt.getInt(EXPERIENCE_KEY));
        legacy = LuciiLegacy.byId(nbt.getString(LEGACY_KEY));
        royalArmsActive = nbt.getBoolean(ROYAL_ARMS_ACTIVE_KEY) && hasLegacy();
        royalArmsFilter = RoyalArmsInventoryFilter.byOrdinal(nbt.getInt(ROYAL_ARMS_FILTER_KEY));
        setArdynWarpCharges(nbt.getInt(ARDYN_WARP_CHARGES_KEY));
        mana = Math.min(Math.max(0, mana), maxMana);
        regenTimer = Math.max(0, nbt.getInt(REGEN_TIMER_KEY));
        manaRegenDelay = Math.max(0, nbt.getInt(MANA_REGEN_DELAY_KEY));
        ardynOverkillActive = nbt.getBoolean(ARDYN_OVERKILL_ACTIVE_KEY)
                && legacy == LuciiLegacy.ARDYN
                && mana < maxMana;
        if (ardynOverkillActive) {
            // Preserve the exact online recovery position, but never advance it while offline.
            // The interval is clamped so old/corrupt NBT cannot instantly refill mana on login.
            regenTimer = Math.min(regenTimer, ARDYN_OVERKILL_MANA_REGEN_INTERVAL - 1);
            manaRegenDelay = 0;
        }
    }
    public void writeNbt(NbtCompound nbt) {
        nbt.putInt(MANA_KEY, mana);
        nbt.putInt(MAX_MANA_KEY, maxMana);
        nbt.putInt(LEVEL_KEY, level);
        nbt.putInt(EXPERIENCE_KEY, experience);
        nbt.putString(LEGACY_KEY, legacy.id());
        nbt.putBoolean(ROYAL_ARMS_ACTIVE_KEY, royalArmsActive);
        nbt.putInt(ROYAL_ARMS_FILTER_KEY, royalArmsFilter.ordinal());
        nbt.putInt(ARDYN_WARP_CHARGES_KEY, ardynWarpCharges);
        nbt.putBoolean(ARDYN_OVERKILL_ACTIVE_KEY, ardynOverkillActive);
        nbt.putInt(REGEN_TIMER_KEY, regenTimer);
        nbt.putInt(MANA_REGEN_DELAY_KEY, manaRegenDelay);
    }
    private int experienceForNextLevel() {
        return 50 + (level - 1) * 25;
    }
}
