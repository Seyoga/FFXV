package ru.siyoga.legacyofthelucii.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import ru.siyoga.legacyofthelucii.particle.LegacyParticles;

public final class DemonizedSlimeBallParticle extends SpriteBillboardParticle {
    private DemonizedSlimeBallParticle(
            ClientWorld world,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            SpriteProvider spriteProvider
    ) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        setSprite(spriteProvider);
        this.velocityX = velocityX * 0.8D;
        this.velocityY = velocityY * 0.8D + 0.03D;
        this.velocityZ = velocityZ * 0.8D;
        this.gravityStrength = 0.35F;
        this.velocityMultiplier = 0.86F;
        this.maxAge = 12 + this.random.nextInt(8);
        this.scale(0.72F + this.random.nextFloat() * 0.28F);
    }

    public static void register() {
        ParticleFactoryRegistry.getInstance().register(
                LegacyParticles.DEMONIZED_SLIME_BALL,
                Factory::new
        );
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    private record Factory(SpriteProvider spriteProvider) implements ParticleFactory<DefaultParticleType> {
        @Override
        public Particle createParticle(
                DefaultParticleType parameters,
                ClientWorld world,
                double x,
                double y,
                double z,
                double velocityX,
                double velocityY,
                double velocityZ
        ) {
            return new DemonizedSlimeBallParticle(
                    world,
                    x,
                    y,
                    z,
                    velocityX,
                    velocityY,
                    velocityZ,
                    spriteProvider
            );
        }
    }
}
