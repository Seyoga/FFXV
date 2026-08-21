package ru.siyoga.legacyofthelucii.pointwarp;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;
import ru.siyoga.legacyofthelucii.skilltree.LuciiSkill;
import ru.siyoga.legacyofthelucii.skilltree.LuciiSkills;

/** Server-authoritative Noctis point warp. */
public final class NoctisPointWarpAbility {
    private static final String LOG = "[NoctisPointWarp/SERVER]";

    private NoctisPointWarpAbility() {
    }

    public static boolean start(ServerPlayerEntity player, BlockPos blockPos) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() != LuciiLegacy.NOCTIS
                || !state.royalArmsActive()
                || !LuciiSkills.isUnlocked(state, LuciiSkill.NOCTIS_WARP)) {
            return false;
        }

        NoctisWarpPointFinder.WarpPoint point = NoctisWarpPointFinder.resolve(
                player.getServerWorld(), player.getPos(), blockPos
        );
        if (point == null || !canOccupy(player, point.landingPos())) {
            LegacyOfTheLucii.LOGGER.warn("{} Rejected target {} for {}.",
                    LOG, blockPos, player.getGameProfile().getName());
            return false;
        }

        ServerWorld world = player.getServerWorld();
        Vec3d from = player.getPos();
        Vec3d destination = point.landingPos();
        player.teleport(world, destination.x, destination.y, destination.z, player.getYaw(), player.getPitch());
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0.0F;

        if (state.restoreMana()) {
            LuciiNetwork.sendState(player);
        }
        spawnWarpEffect(world, from, destination);
        LegacyOfTheLucii.LOGGER.info("{} Warped {} from {} to {} and restored mana.",
                LOG, player.getGameProfile().getName(), from, destination);
        return true;
    }

    private static boolean canOccupy(ServerPlayerEntity player, Vec3d destination) {
        Vec3d offset = destination.subtract(player.getPos());
        Box box = player.getBoundingBox().offset(offset);
        return player.getServerWorld().isSpaceEmpty(player, box);
    }

    private static void spawnWarpEffect(ServerWorld world, Vec3d from, Vec3d destination) {
        world.spawnParticles(ParticleTypes.END_ROD, from.x, from.y + 1.0D, from.z,
                18, 0.35D, 0.65D, 0.35D, 0.04D);
        world.spawnParticles(ParticleTypes.END_ROD, destination.x, destination.y + 1.0D, destination.z,
                28, 0.45D, 0.85D, 0.45D, 0.06D);
        world.playSound(null, destination.x, destination.y, destination.z,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8F, 1.25F);
    }
}
