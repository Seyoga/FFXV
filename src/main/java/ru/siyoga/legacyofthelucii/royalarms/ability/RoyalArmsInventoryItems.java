package ru.siyoga.legacyofthelucii.royalarms.ability;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;

import java.util.ArrayList;
import java.util.List;

public final class RoyalArmsInventoryItems {
    private RoyalArmsInventoryItems() {
    }

    public static List<ItemStack> collect(ServerPlayerEntity owner) {
        List<OrbitItem> items = collectSlots(owner);
        List<ItemStack> stacks = new ArrayList<>(items.size());
        for (OrbitItem item : items) {
            stacks.add(item.stack());
        }
        return stacks;
    }

    public static List<OrbitItem> collectSlots(ServerPlayerEntity owner) {
        LuciiPlayerState state = LuciiPlayerStates.get(owner);
        SimpleInventory storage = state.royalArmsInventory();
        List<OrbitItem> items = new ArrayList<>();
        int unlockedSlots = Math.min(state.royalArmsUnlockedSlots(), storage.size());

        for (int slot = 0; slot < unlockedSlots; slot++) {
            ItemStack stack = storage.getStack(slot);
            if (!stack.isEmpty() && state.royalArmsFilter().matches(stack)) {
                items.add(new OrbitItem(
                        "armiger:" + slot,
                        slot,
                        stack.copyWithCount(stack.getCount())
                ));
            }
        }

        return items;
    }

    public record OrbitItem(String key, int sourceSlot, ItemStack stack) {
    }
}
