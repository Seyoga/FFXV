package ru.siyoga.legacyofthelucii.sniper;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.entity.ArdynSniperBulletEntity;

public final class ArdynSniperContent {
    public static final Item SNIPER_BULLET_ITEM = Registry.register(
            Registries.ITEM,
            new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_sniper_bullet"),
            new Item(new Item.Settings().maxCount(1))
    );

    public static final EntityType<ArdynSniperBulletEntity> SNIPER_BULLET_ENTITY = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_sniper_bullet_projectile"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ArdynSniperBulletEntity::new)
                    .dimensions(EntityDimensions.fixed(0.16F, 0.16F))
                    .trackRangeBlocks(160)
                    .trackedUpdateRate(1)
                    .forceTrackedVelocityUpdates(true)
                    .disableSaving()
                    .disableSummon()
                    .build()
    );

    private ArdynSniperContent() {
    }

    public static void register() {
        LegacyOfTheLucii.LOGGER.info("Registering Ardyn sniper bullet content.");
    }
}
