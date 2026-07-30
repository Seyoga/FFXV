package ru.siyoga.legacyofthelucii.legacy;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.registry.Registries;

public enum RoyalArmsInventoryFilter {
    ALL("All inventory"),
    FOOD("Food"),
    BLOCKS("Blocks"),
    WEAPONS("Weapons"),
    TOOLS("Tools"),
    ARMOR("Armor");

    private final String displayName;

    RoyalArmsInventoryFilter(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public RoyalArmsInventoryFilter next() {
        RoyalArmsInventoryFilter[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public boolean matches(ItemStack stack) {
        return switch (this) {
            case ALL -> true;
            case FOOD -> stack.isFood();
            case BLOCKS -> stack.getItem() instanceof BlockItem;
            case WEAPONS -> stack.getItem() instanceof SwordItem
                    || stack.getItem() instanceof AxeItem
                    || stack.getItem() instanceof BowItem
                    || stack.getItem() instanceof CrossbowItem
                    || Registries.ITEM.getId(stack.getItem()).getPath().contains("trident");
            case TOOLS -> stack.getItem() instanceof ToolItem;
            case ARMOR -> stack.getItem() instanceof ArmorItem || stack.isOf(Items.ELYTRA);
        };
    }

    public static RoyalArmsInventoryFilter byOrdinal(int ordinal) {
        RoyalArmsInventoryFilter[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return ALL;
        }
        return values[ordinal];
    }
}
