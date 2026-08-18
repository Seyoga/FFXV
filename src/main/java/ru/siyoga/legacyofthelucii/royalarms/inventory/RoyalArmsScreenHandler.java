package ru.siyoga.legacyofthelucii.royalarms.inventory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;

public final class RoyalArmsScreenHandler extends ScreenHandler {
    public static final int STORAGE_SIZE = 18;
    public static final int STORAGE_COLUMNS = 6;
    public static final int STORAGE_ROWS = 3;
    private static final int STORAGE_SLOT_X = 54;
    private static final int STORAGE_SLOT_Y = 8;
    private static final int PLAYER_SLOT_X = 27;
    private static final int PLAYER_SLOT_Y = 76;
    private static final int HOTBAR_SLOT_Y = 134;
    private static final int STORAGE_SLOT_START = 0;
    private static final int PLAYER_SLOT_START = STORAGE_SIZE;
    private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 36;

    private final Inventory royalArmsInventory;
    private final PropertyDelegate properties;

    public RoyalArmsScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(STORAGE_SIZE), clientProperties());
    }

    private RoyalArmsScreenHandler(
            int syncId,
            PlayerInventory playerInventory,
            Inventory royalArmsInventory,
            PropertyDelegate properties
    ) {
        super(RoyalArmsScreenHandlers.ROYAL_ARMS, syncId);
        checkSize(royalArmsInventory, STORAGE_SIZE);
        checkDataCount(properties, 1);
        this.royalArmsInventory = royalArmsInventory;
        this.properties = properties;
        royalArmsInventory.onOpen(playerInventory.player);

        for (int row = 0; row < STORAGE_ROWS; row++) {
            for (int column = 0; column < STORAGE_COLUMNS; column++) {
                int slot = column + row * STORAGE_COLUMNS;
                addSlot(new RoyalArmsSlot(
                        royalArmsInventory,
                        slot,
                        STORAGE_SLOT_X + column * 18,
                        STORAGE_SLOT_Y + row * 18,
                        slot
                ));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        PLAYER_SLOT_X + column * 18,
                        PLAYER_SLOT_Y + row * 18
                ));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    playerInventory,
                    column,
                    PLAYER_SLOT_X + column * 18,
                    HOTBAR_SLOT_Y
            ));
        }

        addProperties(properties);
    }

    public static RoyalArmsScreenHandler server(
            int syncId,
            PlayerInventory playerInventory,
            LuciiPlayerState state
    ) {
        return new RoyalArmsScreenHandler(
                syncId,
                playerInventory,
                state.royalArmsInventory(),
                new PropertyDelegate() {
                    @Override
                    public int get(int index) {
                        return index == 0 ? state.royalArmsUnlockedSlots() : 0;
                    }

                    @Override
                    public void set(int index, int value) {
                        if (index == 0) {
                            state.setRoyalArmsUnlockedSlots(value);
                        }
                    }

                    @Override
                    public int size() {
                        return 1;
                    }
                }
        );
    }

    public int unlockedSlots() {
        return Math.max(0, Math.min(STORAGE_SIZE, properties.get(0)));
    }

    public boolean isStorageSlotUnlocked(int slot) {
        return slot >= 0 && slot < unlockedSlots();
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return royalArmsInventory.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return result;
        }

        Slot slot = slots.get(slotIndex);
        if (!slot.hasStack()) {
            return result;
        }

        ItemStack stack = slot.getStack();
        result = stack.copy();
        if (slotIndex < PLAYER_SLOT_START) {
            if (!insertItem(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int unlocked = unlockedSlots();
            if (unlocked <= 0 || !insertItem(stack, STORAGE_SLOT_START, unlocked, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }

        if (stack.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTakeItem(player, stack);
        return result;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        royalArmsInventory.onClose(player);
    }

    private static PropertyDelegate clientProperties() {
        ArrayPropertyDelegate properties = new ArrayPropertyDelegate(1);
        properties.set(0, 3);
        return properties;
    }

    private final class RoyalArmsSlot extends Slot {
        private final int storageSlot;

        private RoyalArmsSlot(Inventory inventory, int index, int x, int y, int storageSlot) {
            super(inventory, index, x, y);
            this.storageSlot = storageSlot;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return isStorageSlotUnlocked(storageSlot);
        }

        @Override
        public boolean canTakeItems(PlayerEntity playerEntity) {
            return isStorageSlotUnlocked(storageSlot);
        }

        @Override
        public boolean isEnabled() {
            return isStorageSlotUnlocked(storageSlot);
        }
    }
}
