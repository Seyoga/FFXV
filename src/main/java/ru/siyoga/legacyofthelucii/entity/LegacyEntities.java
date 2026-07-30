package ru.siyoga.legacyofthelucii.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;

public final class LegacyEntities {
    public static final EntityType<ArdynBarrageWeaponEntity> ARDYN_BARRAGE_WEAPON = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_barrage_weapon"),
            FabricEntityTypeBuilder.<ArdynBarrageWeaponEntity>create(SpawnGroup.MISC, ArdynBarrageWeaponEntity::new)
                    .dimensions(EntityDimensions.fixed(0.35F, 0.35F))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(1)
                    .build()
    );

    private LegacyEntities() {
    }

    public static void register() {
        // Static initializer registers entity types.
    }
}
