package ru.siyoga.legacyofthelucii.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;

public final class LegacyBlocks {
    public static final RoyalArmsWallBlock ROYAL_ARMS_WALL_BLOCK = Registry.register(
            Registries.BLOCK,
            new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_wall"),
            new RoyalArmsWallBlock(FabricBlockSettings.copyOf(Blocks.BARRIER).dropsNothing().nonOpaque())
    );

    public static final BlockEntityType<RoyalArmsWallBlockEntity> ROYAL_ARMS_WALL_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_wall"),
            FabricBlockEntityTypeBuilder.create(RoyalArmsWallBlockEntity::new, ROYAL_ARMS_WALL_BLOCK).build(null)
    );

    private LegacyBlocks() {
    }

    public static void register() {
        LegacyOfTheLucii.LOGGER.debug("Registered Legacy of the Lucii blocks: {}", Registries.BLOCK.getId(ROYAL_ARMS_WALL_BLOCK));
    }
}
