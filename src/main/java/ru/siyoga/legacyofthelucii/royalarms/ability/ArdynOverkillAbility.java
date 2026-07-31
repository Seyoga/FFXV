package ru.siyoga.legacyofthelucii.royalarms.ability;

import org.joml.Vector3f;

import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.ArdynOverkillNetwork;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

public final class ArdynOverkillAbility {
    private static final DustParticleEffect DAEMON_PARTICLE = new DustParticleEffect(
            new Vector3f(0.34F, 0.10F, 0.42F),
            1.25F
    );
    private static final DustParticleEffect PALE_DAEMON_PARTICLE = new DustParticleEffect(
            new Vector3f(0.56F, 0.50F, 0.62F),
            0.85F
    );

    private ArdynOverkillAbility() {
    }

    /**
     * Called once when a server-side Ardyn player would receive lethal damage.
     * Overkill may replace only the FIRST lethal health value. Lethal damage received
     * while Overkill is already active is intentionally not intercepted.
     */
    public static boolean tryEnterOverkill(ServerPlayerEntity player) {
        if (player.isRemoved() || player.isSpectator()) {
            return false;
        }

        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() != LuciiLegacy.ARDYN) {
            return false;
        }

        if (state.ardynOverkillActive()) {
            return false;
        }

        if (!state.beginArdynOverkill()) {
            return false;
        }

        player.fallDistance = 0.0F;
        player.getServerWorld().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.ENTITY_WITHER_SPAWN,
                SoundCategory.PLAYERS,
                0.72F,
                0.62F
        );
        spawnTransitionBurst(player.getServerWorld(), player, true);
        LuciiNetwork.sendState(player);
        ArdynOverkillNetwork.broadcastState(player);
        return true;
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            LuciiPlayerState state = LuciiPlayerStates.get(player);
            if (!state.ardynOverkillActive()) {
                continue;
            }

            if (state.legacy() != LuciiLegacy.ARDYN) {
                finish(player, state, false);
                continue;
            }

            spawnPersistentAura(player.getServerWorld(), player);
            if (state.mana() >= state.maxMana()) {
                finish(player, state, true);
            }
        }
    }

    private static void finish(ServerPlayerEntity player, LuciiPlayerState state, boolean recovered) {
        state.endArdynOverkill();
        if (recovered) {
            player.getServerWorld().playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE,
                    SoundCategory.PLAYERS,
                    0.75F,
                    0.72F
            );
            spawnTransitionBurst(player.getServerWorld(), player, false);
        }
        LuciiNetwork.sendState(player);
        ArdynOverkillNetwork.broadcastState(player);
    }

    private static void spawnPersistentAura(ServerWorld world, ServerPlayerEntity player) {
        int tick = player.age;
        for (int i = 0; i < 3; i++) {
            double angle = tick * 0.31D + i * (Math.PI * 2.0D / 3.0D);
            double radius = 0.36D + world.random.nextDouble() * 0.28D;
            double x = player.getX() + Math.cos(angle) * radius;
            double y = player.getY() + 0.15D + world.random.nextDouble() * 1.85D;
            double z = player.getZ() + Math.sin(angle) * radius;
            world.spawnParticles(DAEMON_PARTICLE, x, y, z, 1, 0.025D, 0.045D, 0.025D, 0.002D);
        }

        if (tick % 2 == 0) {
            world.spawnParticles(
                    ParticleTypes.ASH,
                    player.getX(),
                    player.getY() + 1.0D,
                    player.getZ(),
                    3,
                    0.52D,
                    0.85D,
                    0.52D,
                    0.012D
            );
        }

        if (tick % 5 == 0) {
            world.spawnParticles(
                    ParticleTypes.SOUL,
                    player.getX(),
                    player.getY() + 0.9D,
                    player.getZ(),
                    1,
                    0.32D,
                    0.62D,
                    0.32D,
                    0.015D
            );
        }
    }

    private static void spawnTransitionBurst(ServerWorld world, ServerPlayerEntity player, boolean entering) {
        Vec3d center = player.getPos().add(0.0D, 0.9D, 0.0D);
        world.spawnParticles(
                entering ? DAEMON_PARTICLE : PALE_DAEMON_PARTICLE,
                center.x,
                center.y,
                center.z,
                entering ? 54 : 34,
                0.72D,
                1.0D,
                0.72D,
                entering ? 0.045D : 0.025D
        );
        world.spawnParticles(
                ParticleTypes.ASH,
                center.x,
                center.y,
                center.z,
                entering ? 48 : 24,
                0.8D,
                1.0D,
                0.8D,
                0.03D
        );
        world.spawnParticles(
                ParticleTypes.SOUL,
                center.x,
                center.y,
                center.z,
                entering ? 16 : 8,
                0.55D,
                0.8D,
                0.55D,
                0.035D
        );
    }
}
