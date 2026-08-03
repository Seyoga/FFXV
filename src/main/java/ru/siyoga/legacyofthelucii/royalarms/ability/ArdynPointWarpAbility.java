package ru.siyoga.legacyofthelucii.royalarms.ability;

import net.minecraft.block.BlockState;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Ardyn point warp.
 *
 * The client only suggests a block/corner. The server reconstructs and validates
 * the marker, line of sight, range and landing box before starting movement.
 */
public final class ArdynPointWarpAbility {
    private static final String LOG = "[PointWarp/SERVER]";
    public static final double MAX_RANGE = 48.0D;
    public static final double BLOCKS_PER_SECOND = 17.5D;

    private static final double SPEED_PER_TICK = BLOCKS_PER_SECOND / 20.0D;
    private static final double CORNER_INSET = 0.055D;
    private static final double MARKER_Y_OFFSET = 0.035D;
    private static final double FLIGHT_Y_OFFSET = 0.42D;
    private static final double LANDING_EPSILON = 0.015D;
    private static final double ARRIVAL_DISTANCE = 0.48D;
    private static final int MAX_TICKS = 180;
    private static final int MAX_STUCK_TICKS = 8;
    private static final int MANA_REGEN_INTERVAL_TICKS = 4;
    private static final int FALL_PROTECTION_TICKS = 20;
    private static final double TOP_EPSILON = 0.045D;

    private static final DustParticleEffect ASH_PARTICLE = new DustParticleEffect(
            new Vector3f(0.02F, 0.01F, 0.01F),
            1.45F
    );

    private static final int[] CORNER_X_SIGN = {-1, 1, 1, -1};
    private static final int[] CORNER_Z_SIGN = {-1, -1, 1, 1};

    private static final Map<UUID, ActiveWarp> ACTIVE_WARPS = new HashMap<>();
    private static final Map<UUID, Integer> FALL_PROTECTION = new HashMap<>();
    private static int lastProcessedServerTick = Integer.MIN_VALUE;

    private ArdynPointWarpAbility() {
    }

    public static boolean start(ServerPlayerEntity player, BlockPos blockPos, int cornerIndex) {
        String playerName = player.getGameProfile().getName();
        LegacyOfTheLucii.LOGGER.info("{} Start validation for {}: block={}, corner={}, position={}.",
                LOG, playerName, blockPos.toString(), cornerIndex, format(player.getPos()));

        if (cornerIndex < 0 || cornerIndex >= 4) {
            LegacyOfTheLucii.LOGGER.warn("{} Rejected {}: corner index {} is outside 0..3.",
                    LOG, playerName, cornerIndex);
            return false;
        }
        if (ACTIVE_WARPS.containsKey(player.getUuid())) {
            LegacyOfTheLucii.LOGGER.warn("{} Rejected {}: point warp is already active.", LOG, playerName);
            return false;
        }

        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() != LuciiLegacy.ARDYN) {
            LegacyOfTheLucii.LOGGER.warn("{} Rejected {}: legacy is {}, expected ARDYN.",
                    LOG, playerName, state.legacy());
            return false;
        }
        if (!state.royalArmsActive()) {
            LegacyOfTheLucii.LOGGER.warn("{} Rejected {}: Royal Arms is not active.", LOG, playerName);
            return false;
        }
        if (ArdynShadowStepAbility.isActive(player.getUuid())) {
            LegacyOfTheLucii.LOGGER.warn("{} Rejected {}: Shadow Step is active.", LOG, playerName);
            return false;
        }

        Target target = resolveTarget(player, blockPos, cornerIndex);
        if (target == null) {
            LegacyOfTheLucii.LOGGER.warn("{} Rejected {}: server target validation failed.", LOG, playerName);
            return false;
        }

        boolean previousNoGravity = player.hasNoGravity();
        double initialDistance = player.getPos().distanceTo(target.flightTarget());
        ACTIVE_WARPS.put(player.getUuid(), new ActiveWarp(
                target.flightTarget(),
                target.landingPos(),
                previousNoGravity,
                initialDistance
        ));

        player.setNoGravity(true);
        player.fallDistance = 0.0F;
        LegacyOfTheLucii.LOGGER.info("{} Started for {}: distance={} blocks, flightTarget={}, landing={}.",
                LOG, playerName, String.format(java.util.Locale.ROOT, "%.2f", initialDistance),
                format(target.flightTarget()), format(target.landingPos()));
        LuciiNetwork.broadcastArdynPointWarp(player.getServerWorld(), player, true);
        return true;
    }

    /** Stops at the player's current position. Used by Space and releasing X. */
    public static void stop(ServerPlayerEntity player) {
        stop(player, "unspecified");
    }

    public static void stop(ServerPlayerEntity player, String reason) {
        ActiveWarp warp = ACTIVE_WARPS.remove(player.getUuid());
        if (warp != null) {
            LegacyOfTheLucii.LOGGER.info("{} Stopping {} at current position {}. Reason: {}.",
                    LOG, player.getGameProfile().getName(), format(player.getPos()), reason);
            finish(player, warp, true, reason);
        } else {
            LegacyOfTheLucii.LOGGER.info("{} Stop ignored for {}: no active point warp. Reason: {}.",
                    LOG, player.getGameProfile().getName(), reason);
        }
    }

    public static void tick(MinecraftServer server) {
        int serverTick = server.getTicks();
        if (lastProcessedServerTick == serverTick) {
            return;
        }
        lastProcessedServerTick = serverTick;
        tickFallProtection();

        Iterator<Map.Entry<UUID, ActiveWarp>> iterator = ACTIVE_WARPS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveWarp> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            ActiveWarp warp = entry.getValue();

            if (player == null || player.isRemoved() || player.isDead()) {
                LegacyOfTheLucii.LOGGER.warn("{} Removing orphaned active warp for UUID {}.", LOG, entry.getKey());
                iterator.remove();
                continue;
            }

            LuciiPlayerState state = LuciiPlayerStates.get(player);
            if (state.legacy() != LuciiLegacy.ARDYN || !state.royalArmsActive()) {
                iterator.remove();
                LegacyOfTheLucii.LOGGER.warn("{} Cancelling {} during flight: legacy={}, royalArmsActive={}.",
                        LOG, player.getGameProfile().getName(), state.legacy(), state.royalArmsActive());
                finish(player, warp, true, "state became invalid");
                continue;
            }

            warp.ticks++;
            player.setNoGravity(true);
            player.fallDistance = 0.0F;

            if (++warp.manaTimer >= MANA_REGEN_INTERVAL_TICKS) {
                warp.manaTimer = 0;
                if (!player.getAbilities().creativeMode && state.addMana(1)) {
                    warp.manaRestored++;
                    LuciiNetwork.sendState(player);
                }
            }

            Vec3d delta = warp.flightTarget.subtract(player.getPos());
            double distance = delta.length();

            if (distance <= ARRIVAL_DISTANCE) {
                iterator.remove();
                boolean landingFree = canOccupy(player, warp.landingPos);
                LegacyOfTheLucii.LOGGER.info("{} {} reached target after {} ticks; landingFree={}, manaRestored={}.",
                        LOG, player.getGameProfile().getName(), warp.ticks, landingFree, warp.manaRestored);
                if (landingFree) {
                    player.teleport(
                            player.getServerWorld(),
                            warp.landingPos.x,
                            warp.landingPos.y,
                            warp.landingPos.z,
                            player.getYaw(),
                            player.getPitch()
                    );
                }
                finish(player, warp, true, landingFree ? "arrived" : "arrived but landing became blocked");
                continue;
            }

            if (distance >= warp.previousDistance - 0.01D) {
                warp.stuckTicks++;
            } else {
                warp.stuckTicks = 0;
            }
            warp.previousDistance = distance;

            if (warp.ticks >= MAX_TICKS || warp.stuckTicks >= MAX_STUCK_TICKS) {
                iterator.remove();
                String reason = warp.ticks >= MAX_TICKS ? "timeout" : "stuck";
                LegacyOfTheLucii.LOGGER.warn("{} Cancelling {}: {}, ticks={}, stuckTicks={}, remainingDistance={}.",
                        LOG, player.getGameProfile().getName(), reason, warp.ticks, warp.stuckTicks,
                        String.format(java.util.Locale.ROOT, "%.2f", distance));
                finish(player, warp, true, reason);
                continue;
            }

            if (warp.ticks == 1 || warp.ticks % 20 == 0) {
                LegacyOfTheLucii.LOGGER.info("{} Flight {}: tick={}, position={}, remaining={}, manaRestored={}.",
                        LOG, player.getGameProfile().getName(), warp.ticks, format(player.getPos()),
                        String.format(java.util.Locale.ROOT, "%.2f", distance), warp.manaRestored);
            }

            Vec3d velocity = delta.normalize().multiply(Math.min(SPEED_PER_TICK, distance));
            player.setVelocity(velocity);
            player.velocityModified = true;
            spawnAsh(player.getServerWorld(), player.getPos().add(0.0D, 1.0D, 0.0D));
        }
    }

    public static boolean isActive(UUID playerUuid) {
        return ACTIVE_WARPS.containsKey(playerUuid);
    }

    public static boolean hasFallProtection(UUID playerUuid) {
        return ACTIVE_WARPS.containsKey(playerUuid) || FALL_PROTECTION.containsKey(playerUuid);
    }

    public static void clearAll(ServerPlayerEntity player) {
        ActiveWarp warp = ACTIVE_WARPS.remove(player.getUuid());
        if (warp != null) {
            finish(player, warp, true, "clearAll(player)");
        }
        FALL_PROTECTION.remove(player.getUuid());
    }

    public static void clearAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            clearAll(player);
        }
        ACTIVE_WARPS.clear();
        FALL_PROTECTION.clear();
    }

    private static Target resolveTarget(ServerPlayerEntity player, BlockPos blockPos, int cornerIndex) {
        ServerWorld world = player.getServerWorld();
        BlockState state = world.getBlockState(blockPos);
        VoxelShape shape = state.getCollisionShape(world, blockPos);
        if (shape.isEmpty()) {
            return null;
        }

        Box bounds = shape.getBoundingBox();
        double topY = blockPos.getY() + bounds.maxY;
        double markerX = blockPos.getX() + (bounds.minX + bounds.maxX) * 0.5D;
        double markerZ = blockPos.getZ() + (bounds.minZ + bounds.maxZ) * 0.5D;

        Vec3d markerPos = new Vec3d(markerX, topY + MARKER_Y_OFFSET, markerZ);
        Vec3d visibilityPoint = new Vec3d(markerX, topY - 0.025D, markerZ);
        Vec3d eyePos = player.getEyePos();

        double squaredDistance = eyePos.squaredDistanceTo(markerPos);
        if (squaredDistance > MAX_RANGE * MAX_RANGE) {
            LegacyOfTheLucii.LOGGER.warn("{} Target rejected for {}: distance {} exceeds {}.",
                    LOG, player.getGameProfile().getName(),
                    String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(squaredDistance)), MAX_RANGE);
            return null;
        }
        if (!canSeeBlock(player, visibilityPoint, blockPos)) {
            LegacyOfTheLucii.LOGGER.warn("{} Target rejected for {}: server raycast cannot see block {}.",
                    LOG, player.getGameProfile().getName(), blockPos.toString());
            return null;
        }

        Vec3d landingPos = new Vec3d(markerX, topY + LANDING_EPSILON, markerZ);
        if (!canOccupy(player, landingPos)) {
            LegacyOfTheLucii.LOGGER.warn("{} Target rejected for {}: landing box at {} is occupied.",
                    LOG, player.getGameProfile().getName(), format(landingPos));
            return null;
        }

        Vec3d flightTarget = markerPos.add(0.0D, FLIGHT_Y_OFFSET, 0.0D);
        return new Target(markerPos, flightTarget, landingPos);
    }

    private static boolean canSeeBlock(ServerPlayerEntity player, Vec3d target, BlockPos expectedBlock) {
        HitResult hit = player.getServerWorld().raycast(new RaycastContext(
                player.getEyePos(),
                target,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        return hit.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult) hit).getBlockPos().equals(expectedBlock);
    }

    private static boolean isCornerExposed(
            ServerWorld world,
            BlockPos blockPos,
            double targetTopY,
            int xSign,
            int zSign
    ) {
        return collisionTop(world, blockPos.add(xSign, 0, 0)) < targetTopY - TOP_EPSILON
                && collisionTop(world, blockPos.add(0, 0, zSign)) < targetTopY - TOP_EPSILON;
    }

    private static double collisionTop(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(world, pos);
        if (shape.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }
        return pos.getY() + shape.getMax(Direction.Axis.Y);
    }

    private static boolean canOccupy(ServerPlayerEntity player, Vec3d destination) {
        Vec3d offset = destination.subtract(player.getPos());
        return player.getServerWorld().isSpaceEmpty(player, player.getBoundingBox().offset(offset));
    }

    private static void finish(ServerPlayerEntity player, ActiveWarp warp, boolean protectFromFall, String reason) {
        player.setNoGravity(warp.previousNoGravity);
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0.0F;
        if (protectFromFall) {
            FALL_PROTECTION.put(player.getUuid(), FALL_PROTECTION_TICKS);
        }
        LegacyOfTheLucii.LOGGER.info("{} Finished {} at {}. Reason: {}; ticks={}, manaRestored={}.",
                LOG, player.getGameProfile().getName(), format(player.getPos()), reason, warp.ticks, warp.manaRestored);
        LuciiNetwork.broadcastArdynPointWarp(player.getServerWorld(), player, false);
    }

    private static String format(Vec3d pos) {
        return String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", pos.x, pos.y, pos.z);
    }

    private static void spawnAsh(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ASH_PARTICLE, pos.x, pos.y, pos.z, 18, 0.45D, 0.65D, 0.45D, 0.02D);
    }

    private static void tickFallProtection() {
        Iterator<Map.Entry<UUID, Integer>> iterator = FALL_PROTECTION.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int ticks = entry.getValue() - 1;
            if (ticks <= 0) {
                iterator.remove();
            } else {
                entry.setValue(ticks);
            }
        }
    }

    private record Target(Vec3d markerPos, Vec3d flightTarget, Vec3d landingPos) {
    }

    private static final class ActiveWarp {
        private final Vec3d flightTarget;
        private final Vec3d landingPos;
        private final boolean previousNoGravity;
        private int ticks;
        private int manaTimer;
        private int manaRestored;
        private int stuckTicks;
        private double previousDistance;

        private ActiveWarp(
                Vec3d flightTarget,
                Vec3d landingPos,
                boolean previousNoGravity,
                double previousDistance
        ) {
            this.flightTarget = flightTarget;
            this.landingPos = landingPos;
            this.previousNoGravity = previousNoGravity;
            this.previousDistance = previousDistance;
        }
    }
}
