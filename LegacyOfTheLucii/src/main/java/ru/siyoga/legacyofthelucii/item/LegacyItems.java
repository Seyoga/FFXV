package ru.siyoga.legacyofthelucii.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;

public final class LegacyItems {
    public static final Item RING_OF_THE_LUCII = register("ring_of_the_lucii", new LuciiRingItem(
            LuciiLegacy.NOCTIS,
            new Item.Settings().maxCount(1)
    ));
    public static final Item SCOURGED_LUCII_RING = register("scourged_lucii_ring", new LuciiRingItem(
            LuciiLegacy.ARDYN,
            new Item.Settings().maxCount(1)
    ));

    public static final ItemGroup ITEM_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(RING_OF_THE_LUCII))
            .displayName(Text.translatable("itemGroup.legacyofthelucii"))
            .entries((context, entries) -> {
                entries.add(RING_OF_THE_LUCII);
                entries.add(SCOURGED_LUCII_RING);
            })
            .build();

    private LegacyItems() {
    }

    public static void register() {
        Registry.register(Registries.ITEM_GROUP, new Identifier(LegacyOfTheLucii.MOD_ID, "main"), ITEM_GROUP);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(RING_OF_THE_LUCII);
            entries.add(SCOURGED_LUCII_RING);
        });
    }

    private static Item register(String path, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(LegacyOfTheLucii.MOD_ID, path), item);
    }
}
