package ru.siyoga.legacyofthelucii.legacy;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import ru.siyoga.legacyofthelucii.masquerade.MasqueradeMorph;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LuciiPlayerState {
    private static final int DEFAULT_MAX_MANA = 100;
    private static final int DEFAULT_MANA_REGEN_INTERVAL = 20;
    private static final int DEFAULT_MANA_REGEN_DELAY = 20 * 5;
    private static final int ARDYN_OVERKILL_MANA_REGEN_INTERVAL = 4;
    private static final int ROYAL_ARMS_STORAGE_SIZE = 18;
    private static final int DEFAULT_ROYAL_ARMS_UNLOCKED_SLOTS = 3;
    private static final String MANA_KEY = "Mana";
    private static final String MAX_MANA_KEY = "MaxMana";
    private static final String LEVEL_KEY = "Level";
    private static final String EXPERIENCE_KEY = "Experience";
    private static final String LEGACY_KEY = "Legacy";
    private static final String ROYAL_ARMS_ACTIVE_KEY = "RoyalArmsActive";
    private static final String ROYAL_ARMS_FILTER_KEY = "RoyalArmsFilter";
    private static final String ROYAL_ARMS_STORAGE_KEY = "RoyalArmsStorage";
    private static final String ROYAL_ARMS_UNLOCKED_SLOTS_KEY = "RoyalArmsUnlockedSlots";
    private static final String ARDYN_WARP_CHARGES_KEY = "ArdynWarpCharges";
    private static final String ARDYN_OVERKILL_ACTIVE_KEY = "ArdynOverkillActive";
    private static final String REGEN_TIMER_KEY = "ManaRegenTimer";
    private static final String MANA_REGEN_DELAY_KEY = "ManaRegenDelay";
    private static final String MASQUERADE_MORPHS_KEY = "MasqueradeMorphs";
    private static final String MASQUERADE_ACTIVE_KEY = "MasqueradeActive";
    private static final String MASQUERADE_DATA_VERSION_KEY = "MasqueradeDataVersion";
    private static final int MASQUERADE_DATA_VERSION = 1;
    private static final Set<String> LEGACY_STARTER_MORPH_KEYS = Set.of(
            "entity:minecraft:zombie",
            "entity:minecraft:skeleton",
            "entity:minecraft:creeper",
            "entity:minecraft:enderman",
            "entity:minecraft:spider",
            "entity:minecraft:cow",
            "entity:minecraft:wolf",
            "entity:minecraft:villager"
    );
    private static final int MAX_ARDYN_WARP_CHARGES = 12;
    private int mana = DEFAULT_MAX_MANA;
    private int maxMana = DEFAULT_MAX_MANA;
    private int level = 1;
    private int experience;
    private LuciiLegacy legacy = LuciiLegacy.NONE;
    private boolean royalArmsActive;
    private RoyalArmsInventoryFilter royalArmsFilter = RoyalArmsInventoryFilter.ALL;
    private final SimpleInventory royalArmsInventory = new SimpleInventory(ROYAL_ARMS_STORAGE_SIZE);
    private int royalArmsUnlockedSlots = DEFAULT_ROYAL_ARMS_UNLOCKED_SLOTS;
    private int ardynWarpCharges;
    private boolean ardynOverkillActive;
    private int regenTimer;
    private int manaRegenDelay;
    private final Map<String, MasqueradeMorph> unlockedMorphs = new LinkedHashMap<>();
    private MasqueradeMorph activeMorph;
    private UUID masqueradeTargetUuid;

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
        if (legacy != LuciiLegacy.ARDYN) {
            activeMorph = null;
            masqueradeTargetUuid = null;
        }
        this.mana = Math.max(mana, maxMana / 2);
    }

    public Collection<MasqueradeMorph> unlockedMorphs() {
        return java.util.List.copyOf(unlockedMorphs.values());
    }

    public boolean unlockMorph(MasqueradeMorph morph) {
        if (morph == null || unlockedMorphs.containsKey(morph.key())) {
            return false;
        }
        unlockedMorphs.put(morph.key(), morph);
        return true;
    }

    public MasqueradeMorph findUnlockedMorph(String key) {
        return unlockedMorphs.get(key);
    }

    public MasqueradeMorph activeMorph() {
        return activeMorph;
    }

    public boolean setActiveMorph(MasqueradeMorph morph) {
        if (morph != null && !unlockedMorphs.containsKey(morph.key())) {
            return false;
        }
        if (java.util.Objects.equals(activeMorph, morph)) {
            return false;
        }
        activeMorph = morph;
        return true;
    }

    public UUID masqueradeTargetUuid() {
        return masqueradeTargetUuid;
    }

    public boolean setMasqueradeTargetUuid(UUID targetUuid) {
        if (java.util.Objects.equals(masqueradeTargetUuid, targetUuid)) {
            return false;
        }
        masqueradeTargetUuid = targetUuid;
        return true;
    }

    public boolean hasLegacy() {
        return legacy != LuciiLegacy.NONE;
    }

    public boolean royalArmsActive() {
        return royalArmsActive;
    }

    public void setRoyalArmsActive(boolean active) {
        royalArmsActive = active
                && hasLegacy()
                && !(legacy == LuciiLegacy.ARDYN && ardynOverkillActive);
    }

    public RoyalArmsInventoryFilter royalArmsFilter() {
        return royalArmsFilter;
    }

    public SimpleInventory royalArmsInventory() {
        return royalArmsInventory;
    }

    public int royalArmsUnlockedSlots() {
        return royalArmsUnlockedSlots;
    }

    public void setRoyalArmsUnlockedSlots(int slots) {
        royalArmsUnlockedSlots = Math.max(
                DEFAULT_ROYAL_ARMS_UNLOCKED_SLOTS,
                Math.min(ROYAL_ARMS_STORAGE_SIZE, slots)
        );
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
        royalArmsActive = false;
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
        setRoyalArmsUnlockedSlots(nbt.contains(ROYAL_ARMS_UNLOCKED_SLOTS_KEY)
                ? nbt.getInt(ROYAL_ARMS_UNLOCKED_SLOTS_KEY)
                : DEFAULT_ROYAL_ARMS_UNLOCKED_SLOTS);
        readRoyalArmsInventory(nbt);
        setArdynWarpCharges(nbt.getInt(ARDYN_WARP_CHARGES_KEY));
        mana = Math.min(Math.max(0, mana), maxMana);
        regenTimer = Math.max(0, nbt.getInt(REGEN_TIMER_KEY));
        manaRegenDelay = Math.max(0, nbt.getInt(MANA_REGEN_DELAY_KEY));
        ardynOverkillActive = nbt.getBoolean(ARDYN_OVERKILL_ACTIVE_KEY)
                && legacy == LuciiLegacy.ARDYN
                && mana < maxMana;
        if (ardynOverkillActive) {
            royalArmsActive = false;
            regenTimer = Math.min(regenTimer, ARDYN_OVERKILL_MANA_REGEN_INTERVAL - 1);
            manaRegenDelay = 0;
        }

        unlockedMorphs.clear();
        NbtList morphList = nbt.getList(MASQUERADE_MORPHS_KEY, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < morphList.size(); i++) {
            MasqueradeMorph.fromNbt(morphList.getCompound(i)).ifPresent(this::unlockMorph);
        }
        if (nbt.getInt(MASQUERADE_DATA_VERSION_KEY) < MASQUERADE_DATA_VERSION) {
            LEGACY_STARTER_MORPH_KEYS.forEach(unlockedMorphs::remove);
        }
        activeMorph = null;
        if (legacy == LuciiLegacy.ARDYN && nbt.contains(MASQUERADE_ACTIVE_KEY, NbtElement.COMPOUND_TYPE)) {
            MasqueradeMorph.fromNbt(nbt.getCompound(MASQUERADE_ACTIVE_KEY))
                    .filter(morph -> unlockedMorphs.containsKey(morph.key()))
                    .ifPresent(morph -> activeMorph = unlockedMorphs.get(morph.key()));
        }

        masqueradeTargetUuid = null;
    }
    public void writeNbt(NbtCompound nbt) {
        nbt.putInt(MANA_KEY, mana);
        nbt.putInt(MAX_MANA_KEY, maxMana);
        nbt.putInt(LEVEL_KEY, level);
        nbt.putInt(EXPERIENCE_KEY, experience);
        nbt.putString(LEGACY_KEY, legacy.id());
        nbt.putBoolean(ROYAL_ARMS_ACTIVE_KEY, royalArmsActive);
        nbt.putInt(ROYAL_ARMS_FILTER_KEY, royalArmsFilter.ordinal());
        nbt.putInt(ROYAL_ARMS_UNLOCKED_SLOTS_KEY, royalArmsUnlockedSlots);
        writeRoyalArmsInventory(nbt);
        nbt.putInt(ARDYN_WARP_CHARGES_KEY, ardynWarpCharges);
        nbt.putBoolean(ARDYN_OVERKILL_ACTIVE_KEY, ardynOverkillActive);
        nbt.putInt(REGEN_TIMER_KEY, regenTimer);
        nbt.putInt(MANA_REGEN_DELAY_KEY, manaRegenDelay);

        NbtList morphList = new NbtList();
        for (MasqueradeMorph morph : unlockedMorphs.values()) {
            morphList.add(morph.writeNbt());
        }
        nbt.put(MASQUERADE_MORPHS_KEY, morphList);
        nbt.putInt(MASQUERADE_DATA_VERSION_KEY, MASQUERADE_DATA_VERSION);
        if (activeMorph != null && legacy == LuciiLegacy.ARDYN) {
            nbt.put(MASQUERADE_ACTIVE_KEY, activeMorph.writeNbt());
        }
    }

    private void readRoyalArmsInventory(NbtCompound nbt) {
        royalArmsInventory.clear();
        NbtList list = nbt.getList(ROYAL_ARMS_STORAGE_KEY, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound itemNbt = list.getCompound(i);
            int slot = itemNbt.getByte("Slot") & 255;
            if (slot >= 0 && slot < ROYAL_ARMS_STORAGE_SIZE) {
                royalArmsInventory.setStack(slot, ItemStack.fromNbt(itemNbt));
            }
        }
    }

    private void writeRoyalArmsInventory(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (int slot = 0; slot < ROYAL_ARMS_STORAGE_SIZE; slot++) {
            ItemStack stack = royalArmsInventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            NbtCompound itemNbt = new NbtCompound();
            itemNbt.putByte("Slot", (byte) slot);
            stack.writeNbt(itemNbt);
            list.add(itemNbt);
        }
        nbt.put(ROYAL_ARMS_STORAGE_KEY, list);
    }

    private int experienceForNextLevel() {
        return 50 + (level - 1) * 25;
    }
}
