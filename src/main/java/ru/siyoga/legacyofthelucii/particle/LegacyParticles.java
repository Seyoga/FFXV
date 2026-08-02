package ru.siyoga.legacyofthelucii.particle;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;

public final class LegacyParticles {
    public static final DefaultParticleType DEMONIZED_SLIME_BALL = Registry.register(
            Registries.PARTICLE_TYPE,
            new Identifier(LegacyOfTheLucii.MOD_ID, "demonized_slime_ball"),
            FabricParticleTypes.simple()
    );

    private LegacyParticles() {
    }

    public static void register() {
        LegacyOfTheLucii.LOGGER.info("Registering Legacy of the Lucii particles.");
    }
}
