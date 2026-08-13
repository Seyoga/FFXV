package ru.siyoga.legacyofthelucii.royalarms.ability;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.darktornado.DarkTornadoNetwork;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Server-authoritative implementation of Ardyn's Dark Tornado.
 *
 * Flow:
 * Ctrl+5 -> frozen targeting mode -> LMB confirms a server-validated block.
 * A second Ctrl+5 cancels targeting without spending charges. Once spawned,
 * the tornado consumes one Ardyn charge every second and ends only at zero
 * (apart from required cleanup on death, disconnect, or server shutdown).
 */
public final class ArdynDarkTornadoAbility {
    private static final String LOG = "[DarkTornado/SERVER]";

    public static final int MIN_STARTING_CHARGES = 3;
    public static final int CHARGE_DRAIN_INTERVAL_TICKS = 20;
    public static final double MAX_TARGET_RANGE = 32.0D;
    public static final double EFFECT_RADIUS = 6.5D;
    public static final double EFFECT_HEIGHT = 10.0D;

    private static final float DAMAGE_PER_SECOND = 2.0F;
    private static final double PULL_STRENGTH = 0.105D;
    private static final double SWIRL_STRENGTH = 0.145D;
    private static final double LIFT_STRENGTH = 0.055D;
    private static final double END_THROW_STRENGTH = 1.05D;
    private static final double END_THROW_UP = 0.62D;

    private static final int MAX_ORBITING_BLOCKS = 14;
    private static final double BLOCK_CAPTURE_MIN_RADIUS = 2.0D;
    private static final double BLOCK_CAPTURE_MAX_RADIUS = 5.2D;
    private static final float MAX_LIFTABLE_BLOCK_WEIGHT = 8.0F;
    private static final int MIN_BLOCK_LIFT_TICKS = 14;
    private static final int MAX_BLOCK_LIFT_TICKS = 42;
    private static final double BLOCK_TRACKING_STRENGTH = 0.42D;
    private static final double MAX_BLOCK_TRACKING_SPEED = 0.78D;
    private static final int RELEASED_BLOCK_TIMEOUT_TICKS = 20 * 12;

    private static final DustParticleEffect DARK_PURPLE = new DustParticleEffect(
            new Vector3f(0.17F, 0.015F, 0.28F), 1.75F
    );
    private static final DustParticleEffect BRIGHT_PURPLE = new DustParticleEffect(
            new Vector3f(0.62F, 0.08F, 0.92F), 1.25F
    );
    private static final DustParticleEffect BLACK_CORE = new DustParticleEffect(
            new Vector3f(0.015F, 0.005F, 0.025F), 2.15F
    );

    private static final Map<UUID, TargetingState> TARGETING = new HashMap<>();
    private static final Map<UUID, ActiveTornado> ACTIVE_TORNADOES = new HashMap<>();
    private static final List<ReleasedBlock> RELEASED_BLOCKS = new ArrayList<>();

    private ArdynDarkTornadoAbility() {
    }

    public static void toggleTargeting(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        if (TARGETING.containsKey(playerUuid)) {
            cancelTargeting(player, true, "manual cancel");
            player.sendMessage(Text.translatable(
                    "message.legacyofthelucii.ardyn.dark_tornado.cancelled"), true);
            return;
        }

        if (ACTIVE_TORNADOES.containsKey(playerUuid)) {
            DarkTornadoNetwork.sendTargetingState(player, false);
            player.sendMessage(Text.translatable(
                    "message.legacyofthelucii.ardyn.dark_tornado.already_active"), true);
            return;
        }

        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() != LuciiLegacy.ARDYN) {
            DarkTornadoNetwork.sendTargetingState(player, false);
            player.sendMessage(Text.translatable(
                    "message.legacyofthelucii.ardyn.dark_tornado.requires_ardyn"), true);
            return;
        }
        if (!state.royalArmsActive()) {
            DarkTornadoNetwork.sendTargetingState(player, false);
            player.sendMessage(Text.translatable(
                    "message.legacyofthelucii.ardyn.dark_tornado.requires_active"), true);
            return;
        }
        if (state.ardynWarpCharges() < MIN_STARTING_CHARGES) {
            DarkTornadoNetwork.sendTargetingState(player, false);
            player.sendMessage(Text.translatable(
                    "message.legacyofthelucii.ardyn.dark_tornado.not_enough_charges",
                    MIN_STARTING_CHARGES), true);
            return;
        }
        if (ArdynPointWarpAbility.isActive(playerUuid)
                || ArdynShadowStepAbility.isActive(playerUuid)) {
            DarkTornadoNetwork.sendTargetingState(player, false);
            player.sendMessage(Text.translatable(
                    "message.legacyofthelucii.ardyn.dark_tornado.movement_conflict"), true);
            return;
        }

        TargetingState targetingState = new TargetingState(player.getServerWorld());
        TARGETING.put(playerUuid, targetingState);
        stopHorizontalMovementPreservingFall(player);
        DarkTornadoNetwork.sendTargetingState(player, true);
        DarkTornadoNetwork.broadcastAnimation(
                player.getServerWorld(), playerUuid, DarkTornadoNetwork.ANIMATION_START);
        player.sendMessage(Text.translatable(
                "message.legacyofthelucii.ardyn.dark_tornado.targeting"), true);

        LegacyOfTheLucii.LOGGER.info("{} {} entered targeting mode with {} charge(s).",
                LOG, player.getGameProfile().getName(), state.ardynWarpCharges());
    }

    public static void confirmTarget(ServerPlayerEntity player, BlockPos targetBlock) {
        TargetingState targetingState = TARGETING.get(player.getUuid());
        if (targetingState == null) {
            DarkTornadoNetwork.sendTargetingState(player, false);
            return;
        }

        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() != LuciiLegacy.ARDYN
                || !state.royalArmsActive()
                || state.ardynWarpCharges() < MIN_STARTING_CHARGES) {
            cancelTargeting(player, true, "eligibility changed before confirmation");
            player.sendMessage(Text.translatable(
                    "message.legacyofthelucii.ardyn.dark_tornado.not_enough_charges",
                    MIN_STARTING_CHARGES), true);
            return;
        }

        TornadoTarget target = resolveTarget(player, targetBlock);
        if (target == null) {
            // Keep targeting active, but acknowledge the failed request so the
            // client can clear its local pending-click guard and try again.
            DarkTornadoNetwork.sendTargetingState(player, true);
            player.sendMessage(Text.translatable(
                    "message.legacyofthelucii.ardyn.dark_tornado.invalid_target"), true);
            return;
        }

        DarkTornadoNetwork.broadcastAnimation(
                player.getServerWorld(), player.getUuid(), DarkTornadoNetwork.ANIMATION_CLICK);
        cancelTargeting(player, true, "cast confirmed");

        ServerWorld world = player.getServerWorld();
        Random random = new Random(world.getTime() ^ player.getUuid().getMostSignificantBits());
        List<OrbitingBlock> blocks = captureBlocks(world, player, target.center(), random);
        ActiveTornado tornado = new ActiveTornado(world, target.center(), blocks, random);
        ACTIVE_TORNADOES.put(player.getUuid(), tornado);

        DarkTornadoNetwork.broadcastVisualState(
                world, player.getUuid(), true, target.center());
        player.sendMessage(Text.translatable(
                "message.legacyofthelucii.ardyn.dark_tornado.started",
                state.ardynWarpCharges()), true);

        LegacyOfTheLucii.LOGGER.info(
                "{} {} created Dark Tornado at {} with {} charge(s) and {} captured block(s).",
                LOG,
                player.getGameProfile().getName(),
                format(target.center()),
                state.ardynWarpCharges(),
                blocks.size()
        );
    }

    public static void tick(MinecraftServer server) {
        tickTargeting(server);
        tickTornadoes(server);
        tickReleasedBlocks();
    }

    public static boolean isTargeting(UUID playerUuid) {
        return TARGETING.containsKey(playerUuid);
    }

    public static boolean isActive(UUID playerUuid) {
        return ACTIVE_TORNADOES.containsKey(playerUuid);
    }

    public static void clearPlayer(ServerPlayerEntity player, String reason) {
        TargetingState targetingState = TARGETING.remove(player.getUuid());
        if (targetingState != null) {
            restorePlayerAfterTargeting(player, targetingState);
        }

        ActiveTornado tornado = ACTIVE_TORNADOES.remove(player.getUuid());
        if (tornado != null) {
            endTornado(player, player.getUuid(), tornado, reason, true);
        }
    }

    /** Server shutdown cleanup: restore captured blocks instead of leaving entities orphaned. */
    public static void clearAll(MinecraftServer server) {
        for (Map.Entry<UUID, TargetingState> entry : TARGETING.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null) {
                restorePlayerAfterTargeting(player, entry.getValue());
            }
        }
        TARGETING.clear();

        for (Map.Entry<UUID, ActiveTornado> entry : ACTIVE_TORNADOES.entrySet()) {
            ActiveTornado tornado = entry.getValue();
            restoreOrbitingBlocks(tornado);
        }
        ACTIVE_TORNADOES.clear();

        for (ReleasedBlock released : RELEASED_BLOCKS) {
            if (!released.entity.isRemoved()) {
                released.lastPos = released.entity.getPos();
                released.entity.discard();
            }
            forcePlaceBlock(released.world, released.state, released.lastPos, released.sourcePos);
        }
        RELEASED_BLOCKS.clear();
        LegacyOfTheLucii.LOGGER.info("{} Server cleanup complete.", LOG);
    }

    private static void tickTargeting(MinecraftServer server) {
        Iterator<Map.Entry<UUID, TargetingState>> iterator = TARGETING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TargetingState> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            TargetingState state = entry.getValue();

            if (player == null || player.isRemoved() || player.isDead()) {
                iterator.remove();
                continue;
            }

            LuciiPlayerState luciiState = LuciiPlayerStates.get(player);
            boolean invalid = player.getServerWorld() != state.world
                    || luciiState.legacy() != LuciiLegacy.ARDYN
                    || !luciiState.royalArmsActive()
                    || luciiState.ardynWarpCharges() < MIN_STARTING_CHARGES;
            if (invalid) {
                iterator.remove();
                restorePlayerAfterTargeting(player, state);
                DarkTornadoNetwork.broadcastAnimation(
                        player.getServerWorld(), player.getUuid(), DarkTornadoNetwork.ANIMATION_CANCEL);
                DarkTornadoNetwork.sendTargetingState(player, false);
                continue;
            }

            stopHorizontalMovementPreservingFall(player);
        }
    }

    private static void tickTornadoes(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveTornado>> iterator = ACTIVE_TORNADOES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveTornado> entry = iterator.next();
            UUID ownerUuid = entry.getKey();
            ActiveTornado tornado = entry.getValue();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerUuid);

            if (owner == null || owner.isRemoved() || owner.isDead()) {
                iterator.remove();
                endTornado(owner, ownerUuid, tornado, "owner unavailable", true);
                continue;
            }

            LuciiPlayerState playerState = LuciiPlayerStates.get(owner);
            if (playerState.ardynWarpCharges() <= 0) {
                iterator.remove();
                endTornado(owner, ownerUuid, tornado, "no charges", true);
                continue;
            }

            tornado.ticks++;
            spawnTornadoParticles(tornado);
            tickOrbitingBlocks(tornado);
            affectMobs(owner, tornado, tornado.ticks % 20 == 0);

            if (tornado.ticks % CHARGE_DRAIN_INTERVAL_TICKS == 0) {
                int chargesBefore = playerState.ardynWarpCharges();
                playerState.setArdynWarpCharges(chargesBefore - 1);
                LuciiNetwork.sendState(owner);
                LuciiNetwork.broadcastRoyalArmsVisual(owner);

                LegacyOfTheLucii.LOGGER.info(
                        "{} {} consumed a charge: {} -> {} (tornado tick {}).",
                        LOG,
                        owner.getGameProfile().getName(),
                        chargesBefore,
                        playerState.ardynWarpCharges(),
                        tornado.ticks
                );

                if (playerState.ardynWarpCharges() <= 0) {
                    iterator.remove();
                    endTornado(owner, ownerUuid, tornado, "last charge consumed", true);
                }
            }
        }
    }

    private static void affectMobs(
            ServerPlayerEntity owner,
            ActiveTornado tornado,
            boolean damageTick
    ) {
        ServerWorld world = tornado.world;
        Box area = new Box(
                tornado.center.x - EFFECT_RADIUS,
                tornado.center.y - 1.0D,
                tornado.center.z - EFFECT_RADIUS,
                tornado.center.x + EFFECT_RADIUS,
                tornado.center.y + EFFECT_HEIGHT,
                tornado.center.z + EFFECT_RADIUS
        );

        for (MobEntity mob : world.getEntitiesByClass(
                MobEntity.class,
                area,
                entity -> entity.isAlive() && !entity.isSpectator()
        )) {
            Vec3d bodyCenter = mob.getPos().add(0.0D, mob.getHeight() * 0.5D, 0.0D);
            Vec3d fromCenter = bodyCenter.subtract(tornado.center);
            Vec3d horizontal = new Vec3d(fromCenter.x, 0.0D, fromCenter.z);
            double horizontalDistance = horizontal.length();
            if (horizontalDistance > EFFECT_RADIUS || fromCenter.y > EFFECT_HEIGHT) {
                continue;
            }

            Vec3d radial = horizontalDistance < 0.001D
                    ? randomHorizontal(tornado.random)
                    : horizontal.multiply(1.0D / horizontalDistance);
            Vec3d inward = radial.multiply(-PULL_STRENGTH * (0.65D + horizontalDistance / EFFECT_RADIUS));
            Vec3d tangent = new Vec3d(-radial.z, 0.0D, radial.x).multiply(SWIRL_STRENGTH);
            double lift = LIFT_STRENGTH + Math.max(0.0D, 0.035D * (1.0D - fromCenter.y / EFFECT_HEIGHT));

            Vec3d velocity = mob.getVelocity().multiply(0.78D)
                    .add(inward)
                    .add(tangent)
                    .add(0.0D, lift, 0.0D);
            mob.setVelocity(
                    clamp(velocity.x, -0.72D, 0.72D),
                    clamp(velocity.y, -0.18D, 0.48D),
                    clamp(velocity.z, -0.72D, 0.72D)
            );
            mob.velocityModified = true;
            mob.fallDistance = 0.0F;

            if (damageTick) {
                if (owner.getServerWorld() == world) {
                    mob.damage(world.getDamageSources().indirectMagic(owner, owner), DAMAGE_PER_SECOND);
                } else {
                    mob.damage(world.getDamageSources().magic(), DAMAGE_PER_SECOND);
                }
            }
        }
    }

    private static void throwMobs(ActiveTornado tornado) {
        Box area = new Box(
                tornado.center.x - EFFECT_RADIUS - 1.5D,
                tornado.center.y - 2.0D,
                tornado.center.z - EFFECT_RADIUS - 1.5D,
                tornado.center.x + EFFECT_RADIUS + 1.5D,
                tornado.center.y + EFFECT_HEIGHT + 1.5D,
                tornado.center.z + EFFECT_RADIUS + 1.5D
        );
        for (MobEntity mob : tornado.world.getEntitiesByClass(
                MobEntity.class,
                area,
                entity -> entity.isAlive() && !entity.isSpectator()
        )) {
            Vec3d horizontal = new Vec3d(
                    mob.getX() - tornado.center.x,
                    0.0D,
                    mob.getZ() - tornado.center.z
            );
            Vec3d direction = horizontal.lengthSquared() < 0.001D
                    ? randomHorizontal(tornado.random)
                    : horizontal.normalize();
            mob.setVelocity(direction.multiply(END_THROW_STRENGTH)
                    .add(0.0D, END_THROW_UP, 0.0D));
            mob.velocityModified = true;
            mob.fallDistance = 0.0F;
        }
    }

    private static void spawnTornadoParticles(ActiveTornado tornado) {
        ServerWorld world = tornado.world;
        Vec3d center = tornado.center;

        // Dense black-violet body, widest near the ground and narrower above.
        world.spawnParticles(DARK_PURPLE,
                center.x, center.y + 1.5D, center.z,
                48, 3.4D, 1.45D, 3.4D, 0.025D);
        world.spawnParticles(BRIGHT_PURPLE,
                center.x, center.y + 4.2D, center.z,
                38, 2.55D, 2.15D, 2.55D, 0.035D);
        world.spawnParticles(BLACK_CORE,
                center.x, center.y + 4.6D, center.z,
                34, 1.55D, 3.7D, 1.55D, 0.018D);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                center.x, center.y + 3.4D, center.z,
                24, 2.9D, 2.9D, 2.9D, 0.09D);

        // A moving helix makes the cloud read as a rotating funnel rather than fog.
        if ((tornado.ticks & 1) == 0) {
            for (int i = 0; i < 14; i++) {
                double height = 0.35D + i * 0.68D;
                double taper = 1.0D - Math.min(0.72D, height / (EFFECT_HEIGHT * 1.24D));
                double radius = 0.85D + 4.0D * taper;
                double angle = tornado.ticks * 0.25D + i * 0.82D;
                double x = center.x + Math.cos(angle) * radius;
                double z = center.z + Math.sin(angle) * radius;
                world.spawnParticles(BRIGHT_PURPLE, x, center.y + height, z,
                        3, 0.18D, 0.22D, 0.18D, 0.012D);
            }
        }
    }

    private static List<OrbitingBlock> captureBlocks(
            ServerWorld world,
            ServerPlayerEntity owner,
            Vec3d center,
            Random random
    ) {
        BlockPos centerBlock = BlockPos.ofFloored(center.x, center.y - 0.1D, center.z);
        List<BlockPos> candidates = new ArrayList<>();
        int horizontalRange = (int) Math.ceil(BLOCK_CAPTURE_MAX_RADIUS);

        for (int x = -horizontalRange; x <= horizontalRange; x++) {
            for (int z = -horizontalRange; z <= horizontalRange; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance < BLOCK_CAPTURE_MIN_RADIUS || distance > BLOCK_CAPTURE_MAX_RADIUS) {
                    continue;
                }
                for (int y = -2; y <= 1; y++) {
                    BlockPos pos = centerBlock.add(x, y, z);
                    if (isCapturableBlock(world, owner, pos)) {
                        candidates.add(pos.toImmutable());
                    }
                }
            }
        }

        Collections.shuffle(candidates, random);
        List<OrbitingBlock> result = new ArrayList<>();
        for (BlockPos pos : candidates) {
            if (result.size() >= MAX_ORBITING_BLOCKS) {
                break;
            }
            BlockState blockState = world.getBlockState(pos);
            if (!isCapturableBlock(world, owner, pos)) {
                continue;
            }
            float weight = blockWeight(world, pos, blockState);

            FallingBlockEntity entity = FallingBlockEntity.spawnFromBlock(world, pos, blockState);
            entity.dropItem = false;
            entity.timeFalling = 1;
            entity.setNoGravity(true);
            entity.setSilent(true);
            entity.noClip = true;
            entity.setVelocity(Vec3d.ZERO);

            OrbitingBlock orbiting = new OrbitingBlock(
                    entity,
                    blockState,
                    pos,
                    Vec3d.ofCenter(pos),
                    random.nextDouble() * Math.PI * 2.0D,
                    2.15D + random.nextDouble() * 2.85D,
                    0.65D + random.nextDouble() * 6.35D,
                    0.72D + random.nextDouble() * 0.58D,
                    liftTicksForWeight(weight),
                    releaseVelocityMultiplier(weight)
            );
            result.add(orbiting);
        }
        return result;
    }

    private static boolean isCapturableBlock(
            ServerWorld world,
            ServerPlayerEntity owner,
            BlockPos pos
    ) {
        if (!world.isInBuildLimit(pos) || !world.canPlayerModifyAt(owner, pos)) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (state.isAir()
                || state.hasBlockEntity()
                || !state.getFluidState().isEmpty()
                || state.getHardness(world, pos) < 0.0F
                || blockWeight(world, pos, state) > MAX_LIFTABLE_BLOCK_WEIGHT
                || !state.isFullCube(world, pos)) {
            return false;
        }
        // Only tear exposed surface blocks out of the terrain, avoiding buried veins
        // and minimizing invisible holes below the playable surface.
        return world.getBlockState(pos.up()).isReplaceable();
    }

    private static void tickOrbitingBlocks(ActiveTornado tornado) {
        Iterator<OrbitingBlock> iterator = tornado.blocks.iterator();
        while (iterator.hasNext()) {
            OrbitingBlock block = iterator.next();
            if (block.entity.isRemoved()) {
                forcePlaceBlock(tornado.world, block.state,
                        Vec3d.ofCenter(block.sourcePos), block.sourcePos);
                iterator.remove();
                continue;
            }
            block.entity.setNoGravity(true);
            block.entity.noClip = true;
            block.entity.timeFalling = 1;
            Vec3d desired = desiredOrbitingBlockPosition(block, tornado.center, tornado.ticks);
            moveBlockToward(block.entity, desired);
            block.liftTicks++;
        }
    }

    private static Vec3d desiredOrbitingBlockPosition(
            OrbitingBlock block,
            Vec3d center,
            int ticks
    ) {
        double angle = block.baseAngle + ticks * (0.155D * block.speedMultiplier);
        double breathingRadius = block.radius * (0.91D + 0.09D * Math.sin(ticks * 0.11D + block.baseAngle));
        double y = center.y + block.height + Math.sin(ticks * 0.16D + block.baseAngle) * 0.36D;
        Vec3d orbit = new Vec3d(
                center.x + Math.cos(angle) * breathingRadius,
                y,
                center.z + Math.sin(angle) * breathingRadius
        );
        if (block.liftTicks >= block.totalLiftTicks) {
            return orbit;
        }

        double progress = clamp(block.liftTicks / (double) block.totalLiftTicks, 0.0D, 1.0D);
        double eased = easeOutCubic(progress);
        return block.startPos.lerp(orbit, eased);
    }

    private static void moveBlockToward(
            FallingBlockEntity entity,
            Vec3d desired
    ) {
        Vec3d current = entity.getPos();
        Vec3d delta = desired.subtract(current);
        Vec3d velocity = delta.multiply(BLOCK_TRACKING_STRENGTH);
        double speed = velocity.length();
        if (speed > MAX_BLOCK_TRACKING_SPEED) {
            velocity = velocity.multiply(MAX_BLOCK_TRACKING_SPEED / speed);
        }
        entity.setVelocity(velocity);
        entity.velocityModified = true;
    }

    private static void releaseOrbitingBlocks(ActiveTornado tornado) {
        for (OrbitingBlock block : tornado.blocks) {
            if (block.entity.isRemoved()) {
                forcePlaceBlock(tornado.world, block.state,
                        Vec3d.ofCenter(block.sourcePos), block.sourcePos);
                continue;
            }

            Vec3d horizontal = new Vec3d(
                    block.entity.getX() - tornado.center.x,
                    0.0D,
                    block.entity.getZ() - tornado.center.z
            );
            Vec3d direction = horizontal.lengthSquared() < 0.001D
                    ? randomHorizontal(tornado.random)
                    : horizontal.normalize();
            Vec3d velocity = direction.multiply(0.72D + tornado.random.nextDouble() * 0.46D)
                    .add(0.0D, 0.56D + tornado.random.nextDouble() * 0.34D, 0.0D);

            // Keep vanilla placement disabled and simulate the thrown flight ourselves.
            // This lets us deterministically convert every visual debris entity back
            // into a real world block on impact instead of guessing whether vanilla
            // FallingBlockEntity placement succeeded after the entity was removed.
            block.entity.setSilent(false);
            block.entity.setNoGravity(true);
            block.entity.noClip = true;
            block.entity.timeFalling = 1;
            block.entity.dropItem = false;
            block.entity.setVelocity(Vec3d.ZERO);
            block.entity.velocityModified = true;

            RELEASED_BLOCKS.add(new ReleasedBlock(
                    tornado.world,
                    block.entity,
                    block.state,
                    block.sourcePos,
                    block.entity.getPos(),
                    velocity.multiply(block.releaseMultiplier)
            ));
        }
        tornado.blocks.clear();
    }

    private static void restoreOrbitingBlocks(ActiveTornado tornado) {
        for (OrbitingBlock block : tornado.blocks) {
            if (!block.entity.isRemoved()) {
                block.entity.discard();
            }
            forcePlaceBlock(tornado.world, block.state,
                    Vec3d.ofCenter(block.sourcePos), block.sourcePos);
        }
        tornado.blocks.clear();
    }

    private static void tickReleasedBlocks() {
        Iterator<ReleasedBlock> iterator = RELEASED_BLOCKS.iterator();
        while (iterator.hasNext()) {
            ReleasedBlock released = iterator.next();
            released.ticks++;

            if (released.entity.isRemoved()) {
                forcePlaceBlock(released.world, released.state,
                        released.lastPos, released.sourcePos);
                iterator.remove();
                continue;
            }

            Vec3d current = released.entity.getPos();
            released.velocity = released.velocity
                    .add(0.0D, -0.045D, 0.0D)
                    .multiply(0.985D);
            Vec3d next = current.add(released.velocity);

            BlockHitResult collision = released.world.raycast(new RaycastContext(
                    current,
                    next,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    released.entity
            ));
            if (collision.getType() == HitResult.Type.BLOCK) {
                BlockPos preferred = collision.getBlockPos().offset(collision.getSide());
                released.lastPos = Vec3d.ofCenter(preferred);
                released.entity.discard();
                forcePlaceBlock(released.world, released.state,
                        released.lastPos, released.sourcePos);
                iterator.remove();
                continue;
            }

            released.lastPos = next;
            released.entity.setNoGravity(true);
            released.entity.noClip = true;
            released.entity.timeFalling = 1;
            released.entity.dropItem = false;
            released.entity.setVelocity(released.velocity);
            released.entity.velocityModified = true;

            if (released.ticks >= RELEASED_BLOCK_TIMEOUT_TICKS
                    || next.y < released.world.getBottomY() - 8) {
                released.entity.discard();
                forcePlaceBlock(released.world, released.state,
                        released.lastPos, released.sourcePos);
                iterator.remove();
            }
        }
    }

    /**
     * Guarantees that a captured block is returned to the world. Orbit and thrown
     * flight are visualized by FallingBlockEntity, while final placement is explicit.
     */
    private static void forcePlaceBlock(
            ServerWorld world,
            BlockState state,
            Vec3d preferredPosition,
            BlockPos sourcePos
    ) {
        BlockPos preferred = BlockPos.ofFloored(preferredPosition);
        BlockPos placement = findPlacement(world, state, preferred, 4);
        if (placement == null) {
            placement = findPlacement(world, state, sourcePos, 10);
        }
        if (placement == null && world.isInBuildLimit(sourcePos)) {
            // Last-resort guarantee: the original block wins over a later placement
            // in its vacated source cell. This branch should be practically unreachable.
            placement = sourcePos;
            LegacyOfTheLucii.LOGGER.warn(
                    "{} No free fallback cell found; restoring {} forcibly at original {}.",
                    LOG, state, sourcePos
            );
        }
        if (placement != null) {
            world.setBlockState(placement, state, Block.NOTIFY_ALL | Block.FORCE_STATE);
        }
    }

    private static BlockPos findPlacement(
            ServerWorld world,
            BlockState state,
            BlockPos center,
            int radius
    ) {
        for (int shell = 0; shell <= radius; shell++) {
            for (int y = -3; y <= 5; y++) {
                for (int x = -shell; x <= shell; x++) {
                    for (int z = -shell; z <= shell; z++) {
                        if (shell > 0 && Math.max(Math.abs(x), Math.abs(z)) != shell) {
                            continue;
                        }
                        BlockPos candidate = center.add(x, y, z);
                        if (!world.isInBuildLimit(candidate)
                                || !world.getBlockState(candidate).isReplaceable()
                                || !state.canPlaceAt(world, candidate)) {
                            continue;
                        }
                        BlockPos belowPos = candidate.down();
                        if (world.getBlockState(belowPos)
                                .getCollisionShape(world, belowPos).isEmpty()) {
                            continue;
                        }
                        return candidate.toImmutable();
                    }
                }
            }
        }
        return null;
    }

    private static float blockWeight(
            ServerWorld world,
            BlockPos pos,
            BlockState state
    ) {
        float hardness = Math.max(0.1F, state.getHardness(world, pos));
        float toolWeight = 0.0F;
        if (state.isIn(BlockTags.NEEDS_DIAMOND_TOOL)) {
            toolWeight = 8.0F;
        } else if (state.isIn(BlockTags.NEEDS_IRON_TOOL)) {
            toolWeight = 4.0F;
        } else if (state.isIn(BlockTags.NEEDS_STONE_TOOL)) {
            toolWeight = 2.0F;
        }
        return hardness + toolWeight;
    }

    private static int liftTicksForWeight(float weight) {
        double progress = clamp(weight / MAX_LIFTABLE_BLOCK_WEIGHT, 0.0D, 1.0D);
        return (int) Math.round(MIN_BLOCK_LIFT_TICKS
                + (MAX_BLOCK_LIFT_TICKS - MIN_BLOCK_LIFT_TICKS) * progress);
    }

    private static double releaseVelocityMultiplier(float weight) {
        double progress = clamp(weight / MAX_LIFTABLE_BLOCK_WEIGHT, 0.0D, 1.0D);
        return 1.08D - progress * 0.42D;
    }

    private static double easeOutCubic(double progress) {
        double inverted = 1.0D - progress;
        return 1.0D - inverted * inverted * inverted;
    }

    private static TornadoTarget resolveTarget(
            ServerPlayerEntity player,
            BlockPos blockPos
    ) {
        ServerWorld world = player.getServerWorld();
        if (!world.isInBuildLimit(blockPos) || !world.canPlayerModifyAt(player, blockPos)) {
            return null;
        }

        BlockState state = world.getBlockState(blockPos);
        VoxelShape shape = state.getCollisionShape(world, blockPos);
        if (shape.isEmpty()) {
            return null;
        }
        Box bounds = shape.getBoundingBox();
        double topY = blockPos.getY() + bounds.maxY;
        double x = blockPos.getX() + (bounds.minX + bounds.maxX) * 0.5D;
        double z = blockPos.getZ() + (bounds.minZ + bounds.maxZ) * 0.5D;
        Vec3d center = new Vec3d(x, topY + 0.025D, z);

        if (player.getEyePos().squaredDistanceTo(center) > MAX_TARGET_RANGE * MAX_TARGET_RANGE) {
            return null;
        }
        HitResult hit = world.raycast(new RaycastContext(
                player.getEyePos(),
                new Vec3d(x, topY - 0.025D, z),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        if (hit.getType() != HitResult.Type.BLOCK
                || !((BlockHitResult) hit).getBlockPos().equals(blockPos)) {
            return null;
        }
        return new TornadoTarget(center);
    }

    private static void cancelTargeting(
            ServerPlayerEntity player,
            boolean notifyClient,
            String reason
    ) {
        TargetingState state = TARGETING.remove(player.getUuid());
        if (state == null) {
            if (notifyClient) {
                DarkTornadoNetwork.sendTargetingState(player, false);
            }
            return;
        }
        restorePlayerAfterTargeting(player, state);
        if (!"cast confirmed".equals(reason)) {
            DarkTornadoNetwork.broadcastAnimation(
                    player.getServerWorld(), player.getUuid(), DarkTornadoNetwork.ANIMATION_CANCEL);
        }
        if (notifyClient) {
            DarkTornadoNetwork.sendTargetingState(player, false);
        }
        LegacyOfTheLucii.LOGGER.info("{} {} left targeting mode: {}.",
                LOG, player.getGameProfile().getName(), reason);
    }

    private static void restorePlayerAfterTargeting(
            ServerPlayerEntity player,
            TargetingState state
    ) {
        stopHorizontalMovementPreservingFall(player);
    }

    private static void stopHorizontalMovementPreservingFall(ServerPlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        player.setVelocity(0.0D, velocity.y, 0.0D);
        player.velocityModified = true;
    }

    private static void endTornado(
            ServerPlayerEntity owner,
            UUID ownerUuid,
            ActiveTornado tornado,
            String reason,
            boolean release
    ) {
        throwMobs(tornado);
        if (release) {
            releaseOrbitingBlocks(tornado);
        } else {
            restoreOrbitingBlocks(tornado);
        }
        DarkTornadoNetwork.broadcastVisualState(
                tornado.world, ownerUuid, false, tornado.center);

        if (owner != null && !owner.isRemoved()) {
            owner.sendMessage(Text.translatable(
                    "message.legacyofthelucii.ardyn.dark_tornado.ended"), true);
        }
        LegacyOfTheLucii.LOGGER.info(
                "{} Tornado owned by {} ended after {} ticks. Reason: {}.",
                LOG, ownerUuid, tornado.ticks, reason
        );
    }

    private static Vec3d randomHorizontal(Random random) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        return new Vec3d(Math.cos(angle), 0.0D, Math.sin(angle));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String format(Vec3d pos) {
        return String.format(java.util.Locale.ROOT,
                "(%.2f, %.2f, %.2f)", pos.x, pos.y, pos.z);
    }

    private record TornadoTarget(Vec3d center) {
    }

    private static final class TargetingState {
        private final ServerWorld world;

        private TargetingState(ServerWorld world) {
            this.world = world;
        }
    }

    private static final class ActiveTornado {
        private final ServerWorld world;
        private final Vec3d center;
        private final List<OrbitingBlock> blocks;
        private final Random random;
        private int ticks;

        private ActiveTornado(
                ServerWorld world,
                Vec3d center,
                List<OrbitingBlock> blocks,
                Random random
        ) {
            this.world = world;
            this.center = center;
            this.blocks = blocks;
            this.random = random;
        }
    }

    private static final class OrbitingBlock {
        private final FallingBlockEntity entity;
        private final BlockState state;
        private final BlockPos sourcePos;
        private final Vec3d startPos;
        private final double baseAngle;
        private final double radius;
        private final double height;
        private final double speedMultiplier;
        private final int totalLiftTicks;
        private final double releaseMultiplier;
        private int liftTicks;

        private OrbitingBlock(
                FallingBlockEntity entity,
                BlockState state,
                BlockPos sourcePos,
                Vec3d startPos,
                double baseAngle,
                double radius,
                double height,
                double speedMultiplier,
                int totalLiftTicks,
                double releaseMultiplier
        ) {
            this.entity = entity;
            this.state = state;
            this.sourcePos = sourcePos;
            this.startPos = startPos;
            this.baseAngle = baseAngle;
            this.radius = radius;
            this.height = height;
            this.speedMultiplier = speedMultiplier;
            this.totalLiftTicks = totalLiftTicks;
            this.releaseMultiplier = releaseMultiplier;
        }
    }

    private static final class ReleasedBlock {
        private final ServerWorld world;
        private final FallingBlockEntity entity;
        private final BlockState state;
        private final BlockPos sourcePos;
        private Vec3d lastPos;
        private Vec3d velocity;
        private int ticks;

        private ReleasedBlock(
                ServerWorld world,
                FallingBlockEntity entity,
                BlockState state,
                BlockPos sourcePos,
                Vec3d lastPos,
                Vec3d velocity
        ) {
            this.world = world;
            this.entity = entity;
            this.state = state;
            this.sourcePos = sourcePos;
            this.lastPos = lastPos;
            this.velocity = velocity;
        }
    }
}
