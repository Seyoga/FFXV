package ru.siyoga.legacyofthelucii.royalarms.ability;

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
        List<OrbitItem> items = new ArrayList<>();
        int selectedSlot = owner.getInventory().selectedSlot;

        ItemStack offhandStack = owner.getOffHandStack();
        if (!offhandStack.isEmpty() && state.royalArmsFilter().matches(offhandStack)) {
            items.add(new OrbitItem("offhand", -1, offhandStack.copyWithCount(offhandStack.getCount())));
        }

        for (int slot = 0; slot < owner.getInventory().main.size(); slot++) {
            ItemStack stack = owner.getInventory().main.get(slot);
            if (slot != selectedSlot && !stack.isEmpty() && state.royalArmsFilter().matches(stack)) {
                items.add(new OrbitItem("main:" + slot, slot, stack.copyWithCount(stack.getCount())));
            }
        }

        return items;
    }

    public record OrbitItem(String key, int sourceSlot, ItemStack stack) {
    }
}
