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
        LuciiPlayerState state = LuciiPlayerStates.get(owner);
        List<ItemStack> stacks = new ArrayList<>();
        int selectedSlot = owner.getInventory().selectedSlot;

        for (int slot = 0; slot < owner.getInventory().main.size(); slot++) {
            ItemStack stack = owner.getInventory().main.get(slot);
            if (slot != selectedSlot && !stack.isEmpty() && state.royalArmsFilter().matches(stack)) {
                stacks.add(stack.copyWithCount(stack.getCount()));
            }
        }

        ItemStack offhandStack = owner.getOffHandStack();
        if (!offhandStack.isEmpty() && state.royalArmsFilter().matches(offhandStack)) {
            stacks.add(offhandStack.copyWithCount(offhandStack.getCount()));
        }

        return stacks;
    }
}
