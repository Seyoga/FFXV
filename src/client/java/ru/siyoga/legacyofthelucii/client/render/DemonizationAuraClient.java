package ru.siyoga.legacyofthelucii.client.render;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;
import ru.siyoga.legacyofthelucii.effect.Demonization;

/** Spawns a restrained dark-purple aura around nearby demonized mobs. */
public final class DemonizationAuraClient {
    private static final double SEARCH_RANGE = 56.0D;
    private static final DustParticleEffect DARK_ASH = new DustParticleEffect(
            new Vector3f(0.16F, 0.008F, 0.24F),
            0.78F
    );

    private static boolean registered;

    private DemonizationAuraClient() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        ClientTickEvents.END_CLIENT_TICK.register(DemonizationAuraClient::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null || client.isPaused()) {
            return;
        }

        // One small update every two ticks keeps the aura visible without turning it into a particle cloud.
        if ((client.world.getTime() & 1L) != 0L) {
            return;
        }

        Box searchBox = client.player.getBoundingBox().expand(SEARCH_RANGE);
        for (MobEntity mob : client.world.getEntitiesByClass(
                MobEntity.class,
                searchBox,
                candidate -> candidate.isAlive()
                        && !candidate.isRemoved()
                        && !candidate.isInvisible()
                        && Demonization.isDemonized(candidate)
        )) {
            spawnAuraParticle(client, mob);
        }
    }

    private static void spawnAuraParticle(MinecraftClient client, MobEntity mob) {
        Random random = mob.getRandom();
        double radius = Math.max(0.28D, mob.getWidth() * 0.62D);
        double x = mob.getX() + (random.nextDouble() * 2.0D - 1.0D) * radius;
        double y = mob.getY() + 0.08D + random.nextDouble() * Math.max(0.35D, mob.getHeight() * 0.92D);
        double z = mob.getZ() + (random.nextDouble() * 2.0D - 1.0D) * radius;

        client.world.addParticle(
                DARK_ASH,
                x,
                y,
                z,
                (random.nextDouble() - 0.5D) * 0.008D,
                0.006D + random.nextDouble() * 0.008D,
                (random.nextDouble() - 0.5D) * 0.008D
        );

        // An occasional portal mote provides a faint purple accent like Ardyn's other effects.
        if (random.nextInt(5) == 0) {
            client.world.addParticle(
                    ParticleTypes.REVERSE_PORTAL,
                    x,
                    y,
                    z,
                    (mob.getX() - x) * 0.018D,
                    0.008D,
                    (mob.getZ() - z) * 0.018D
            );
        }
    }
}
