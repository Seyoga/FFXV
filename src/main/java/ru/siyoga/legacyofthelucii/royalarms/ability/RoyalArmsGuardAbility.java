package ru.siyoga.legacyofthelucii.royalarms.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;
import ru.siyoga.legacyofthelucii.network.RoyalArmsGuardNetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RoyalArmsGuardAbility {
    public static final int LAYER_UPPER = 0;
    public static final int LAYER_MIDDLE = 1;
    public static final int LAYER_LOWER = 2;
    public static final int LAYER_COUNT = 3;

    private static final double DETECTION_RADIUS = 14.0D;
    private static final double MAX_HORIZONTAL_CLOSEST_DISTANCE = 0.82D;
    private static final double LOOKAHEAD_TICKS = 14.0D;
    private static final double GUARD_ORBIT_RADIUS = 1.45D;
    private static final double UPPER_LAYER_Y_OFFSET = 1.62D;
    private static final double MIDDLE_LAYER_Y_OFFSET = 1.02D;
    private static final double LOWER_LAYER_Y_OFFSET = 0.42D;
    private static final double MIN_HORIZONTAL_SPEED_SQUARED = 0.0010D;
    private static final double MIN_PROJECTILE_SPEED_SQUARED = 0.0025D;
    private static final int MAX_GUARD_TRAVEL_TICKS = 6;
    private static final int UPKEEP_INTERVAL_TICKS = 20;
    private static final int UPKEEP_MANA_COST = 2;
    private static final int INTERCEPT_MANA_COST = 5;
    private static final double FALL_SPEED = -0.16D;
    private static final float EXPLOSION_REDUCTION_PER_ITEM = 0.10F;
    private static final float MAX_EXPLOSION_REDUCTION = 0.70F;

    private static final Set<UUID> ACTIVE_OWNERS = new HashSet<>();
    private static final Map<OwnerLayerKey, UUID> PENDING_BY_LAYER = new HashMap<>();
    private static final Map<UUID, PendingBlock> PENDING_BLOCKS = new HashMap<>();
    private static final Map<UUID, Integer> UPKEEP_TIMERS = new HashMap<>();
    private static final Set<UUID> EXPLOSION_DAMAGE_REENTRY = new HashSet<>();

    private RoyalArmsGuardAbility() {
    }

    public static boolean isActive(UUID ownerUuid) {
        return ACTIVE_OWNERS.contains(ownerUuid);
    }

    public static void setActive(ServerPlayerEntity player, boolean requestedActive) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        boolean active = requestedActive
                && state.legacy() == LuciiLegacy.NOCTIS
                && state.royalArmsActive()
                && state.hasMana(UPKEEP_MANA_COST);

        if (active) {
            UUID ownerUuid = player.getUuid();
            ACTIVE_OWNERS.add(ownerUuid);
            UPKEEP_TIMERS.putIfAbsent(ownerUuid, 0);
        } else {
            clearAll(player);
        }

        if (player.getWorld() instanceof ServerWorld world) {
            RoyalArmsGuardNetwork.broadcastState(world, player, active);
        }
    }

    public static void tick(MinecraftServer server) {
        tickPendingBlocks();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID ownerUuid = player.getUuid();
            if (!ACTIVE_OWNERS.contains(ownerUuid)) {
                continue;
            }

            LuciiPlayerState state = LuciiPlayerStates.get(player);
            if (player.isDead()
                    || state.legacy() != LuciiLegacy.NOCTIS
                    || !state.royalArmsActive()
                    || !tickUpkeep(player, state)) {
                clearAll(player);
                if (player.getWorld() instanceof ServerWorld world) {
                    RoyalArmsGuardNetwork.broadcastState(world, player, false);
                }
                LuciiNetwork.sendState(player);
                continue;
            }

            int itemCount = RoyalArmsInventoryItems.collect(player).size();
            if (itemCount <= 0) {
                continue;
            }

            interceptThreats(player, itemCount);
        }
    }

    private static boolean tickUpkeep(ServerPlayerEntity player, LuciiPlayerState state) {
        UUID ownerUuid = player.getUuid();
        int elapsed = UPKEEP_TIMERS.getOrDefault(ownerUuid, 0) + 1;
        if (elapsed < UPKEEP_INTERVAL_TICKS) {
            UPKEEP_TIMERS.put(ownerUuid, elapsed);
            return true;
        }

        UPKEEP_TIMERS.put(ownerUuid, 0);
        if (!state.spendMana(UPKEEP_MANA_COST)) {
            return false;
        }

        LuciiNetwork.sendState(player);
        return true;
    }

    private static void interceptThreats(ServerPlayerEntity player, int itemCount) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        Vec3d playerCenter = player.getBoundingBox().getCenter();
        Box searchBox = player.getBoundingBox().expand(DETECTION_RADIUS);
        List<Threat> threats = new ArrayList<>();

        for (PersistentProjectileEntity projectile : world.getEntitiesByClass(
                PersistentProjectileEntity.class,
                searchBox,
                candidate -> isCandidate(player, candidate)
        )) {
            Threat threat = calculateThreat(player, playerCenter, projectile);
            if (threat != null) {
                threats.add(threat);
            }
        }

        threats.sort(Comparator.comparingDouble(Threat::timeToClosestApproach));
        for (Threat threat : threats) {
            PersistentProjectileEntity projectile = threat.projectile();
            if (PENDING_BLOCKS.containsKey(projectile.getUuid())
                    || itemCountInLayer(itemCount, threat.layer()) <= 0) {
                continue;
            }

            OwnerLayerKey layerKey = new OwnerLayerKey(player.getUuid(), threat.layer());
            if (PENDING_BY_LAYER.containsKey(layerKey)) {
                continue;
            }

            LuciiPlayerState state = LuciiPlayerStates.get(player);
            if (!state.spendMana(INTERCEPT_MANA_COST)) {
                clearAll(player);
                RoyalArmsGuardNetwork.broadcastState(world, player, false);
                LuciiNetwork.sendState(player);
                return;
            }

            beginBlock(
                    player,
                    projectile,
                    threat.interceptPos(),
                    threat.travelTicks(),
                    threat.layer()
            );
            LuciiNetwork.sendState(player);
        }
    }

    private static boolean isCandidate(ServerPlayerEntity player, PersistentProjectileEntity projectile) {
        if (projectile.isRemoved() || PENDING_BLOCKS.containsKey(projectile.getUuid())) {
            return false;
        }

        Entity projectileOwner = projectile.getOwner();
        if (projectileOwner != null && projectileOwner.getUuid().equals(player.getUuid())) {
            return false;
        }

        return projectile.getVelocity().lengthSquared() >= MIN_PROJECTILE_SPEED_SQUARED;
    }

    private static Threat calculateThreat(
            ServerPlayerEntity player,
            Vec3d playerCenter,
            PersistentProjectileEntity projectile
    ) {
        Vec3d projectilePos = projectile.getPos();
        Vec3d velocity = projectile.getVelocity();
        double speedSquared = velocity.lengthSquared();
        if (speedSquared < MIN_PROJECTILE_SPEED_SQUARED) {
            return null;
        }

        Vec3d toPlayer = playerCenter.subtract(projectilePos);
        double timeToClosest = toPlayer.dotProduct(velocity) / speedSquared;
        if (timeToClosest < 0.0D || timeToClosest > LOOKAHEAD_TICKS) {
            return null;
        }

        Vec3d closestPoint = projectilePos.add(velocity.multiply(timeToClosest));
        double closestDx = closestPoint.x - playerCenter.x;
        double closestDz = closestPoint.z - playerCenter.z;
        if (closestDx * closestDx + closestDz * closestDz
                > MAX_HORIZONTAL_CLOSEST_DISTANCE * MAX_HORIZONTAL_CLOSEST_DISTANCE) {
            return null;
        }

        double playerMinY = player.getY() - 0.15D;
        double playerMaxY = player.getY() + player.getHeight() + 0.15D;
        if (closestPoint.y < playerMinY || closestPoint.y > playerMaxY) {
            return null;
        }

        double relativeX = projectilePos.x - playerCenter.x;
        double relativeZ = projectilePos.z - playerCenter.z;
        double horizontalSpeedSquared = velocity.x * velocity.x + velocity.z * velocity.z;
        if (horizontalSpeedSquared < MIN_HORIZONTAL_SPEED_SQUARED) {
            return null;
        }

        double b = 2.0D * (relativeX * velocity.x + relativeZ * velocity.z);
        double c = relativeX * relativeX + relativeZ * relativeZ
                - GUARD_ORBIT_RADIUS * GUARD_ORBIT_RADIUS;
        if (c <= 0.0D) {
            return null;
        }

        double discriminant = b * b - 4.0D * horizontalSpeedSquared * c;
        if (discriminant < 0.0D) {
            return null;
        }

        double root = Math.sqrt(discriminant);
        double timeToOrbit = (-b - root) / (2.0D * horizontalSpeedSquared);
        if (timeToOrbit < 0.0D) {
            timeToOrbit = (-b + root) / (2.0D * horizontalSpeedSquared);
        }
        if (timeToOrbit < 0.0D || timeToOrbit > MAX_GUARD_TRAVEL_TICKS + 0.25D) {
            return null;
        }

        int travelTicks = MathHelper.clamp(
                (int) Math.ceil(timeToOrbit),
                1,
                MAX_GUARD_TRAVEL_TICKS
        );

        double predictedX = projectilePos.x + velocity.x * timeToOrbit;
        double predictedZ = projectilePos.z + velocity.z * timeToOrbit;
        Vec3d radial = new Vec3d(
                predictedX - playerCenter.x,
                0.0D,
                predictedZ - playerCenter.z
        );
        if (radial.lengthSquared() < 0.0001D) {
            radial = new Vec3d(-velocity.x, 0.0D, -velocity.z);
        }
        radial = radial.normalize().multiply(GUARD_ORBIT_RADIUS);

        double predictedY = projectilePos.y
                + velocity.y * timeToOrbit
                - 0.025D * timeToOrbit * timeToOrbit;
        int layer = layerForHeight(predictedY - player.getY());
        Vec3d interceptPos = new Vec3d(
                playerCenter.x + radial.x,
                player.getY() + layerYOffset(layer),
                playerCenter.z + radial.z
        );

        return new Threat(
                projectile,
                timeToClosest,
                interceptPos,
                travelTicks,
                layer
        );
    }

    private static int layerForHeight(double relativeY) {
        if (relativeY >= 1.34D) {
            return LAYER_UPPER;
        }
        if (relativeY <= 0.70D) {
            return LAYER_LOWER;
        }
        return LAYER_MIDDLE;
    }

    private static double layerYOffset(int layer) {
        return switch (layer) {
            case LAYER_UPPER -> UPPER_LAYER_Y_OFFSET;
            case LAYER_LOWER -> LOWER_LAYER_Y_OFFSET;
            default -> MIDDLE_LAYER_Y_OFFSET;
        };
    }

    private static int itemLayerForIndex(int zeroBasedIndex) {
        return switch (Math.floorMod(zeroBasedIndex, LAYER_COUNT)) {
            case 0 -> LAYER_MIDDLE;
            case 1 -> LAYER_UPPER;
            default -> LAYER_LOWER;
        };
    }

    private static int itemCountInLayer(int itemCount, int layer) {
        int count = 0;
        for (int index = 0; index < itemCount; index++) {
            if (itemLayerForIndex(index) == layer) {
                count++;
            }
        }
        return count;
    }

    private static void beginBlock(
            ServerPlayerEntity player,
            PersistentProjectileEntity projectile,
            Vec3d interceptPos,
            int travelTicks,
            int layer
    ) {
        Vec3d incomingVelocity = projectile.getVelocity();
        PENDING_BLOCKS.put(
                projectile.getUuid(),
                new PendingBlock(
                        player.getUuid(),
                        projectile,
                        interceptPos,
                        incomingVelocity,
                        travelTicks,
                        layer
                )
        );
        PENDING_BY_LAYER.put(
                new OwnerLayerKey(player.getUuid(), layer),
                projectile.getUuid()
        );

        RoyalArmsGuardNetwork.broadcastBlock(
                (ServerWorld) player.getWorld(),
                player,
                interceptPos,
                incomingVelocity,
                travelTicks,
                layer
        );
    }

    private static void tickPendingBlocks() {
        Iterator<Map.Entry<UUID, PendingBlock>> iterator = PENDING_BLOCKS.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingBlock pending = iterator.next().getValue();
            PersistentProjectileEntity projectile = pending.projectile;
            OwnerLayerKey layerKey = new OwnerLayerKey(pending.ownerUuid, pending.layer);

            if (projectile.isRemoved()) {
                PENDING_BY_LAYER.remove(layerKey, projectile.getUuid());
                iterator.remove();
                continue;
            }

            pending.remainingTicks--;
            if (pending.remainingTicks > 0) {
                continue;
            }

            projectile.setPosition(
                    pending.interceptPos.x,
                    pending.interceptPos.y,
                    pending.interceptPos.z
            );
            projectile.setNoGravity(false);
            projectile.setVelocity(0.0D, FALL_SPEED, 0.0D);

            if (projectile.getWorld() instanceof ServerWorld world) {
                spawnImpact(world, pending.interceptPos, pending.incomingVelocity);
            }

            PENDING_BY_LAYER.remove(layerKey, projectile.getUuid());
            iterator.remove();
        }
    }

    private static void spawnImpact(ServerWorld world, Vec3d pos, Vec3d incomingVelocity) {
        Vec3d direction = incomingVelocity.lengthSquared() > 0.0D
                ? incomingVelocity.normalize()
                : Vec3d.ZERO;

        world.spawnParticles(
                ParticleTypes.CRIT,
                pos.x,
                pos.y,
                pos.z,
                12,
                0.18D + Math.abs(direction.x) * 0.12D,
                0.18D + Math.abs(direction.y) * 0.12D,
                0.18D + Math.abs(direction.z) * 0.12D,
                0.16D
        );
        world.spawnParticles(
                ParticleTypes.END_ROD,
                pos.x,
                pos.y,
                pos.z,
                5,
                0.12D,
                0.12D,
                0.12D,
                0.04D
        );
        world.playSound(
                null,
                pos.x,
                pos.y,
                pos.z,
                SoundEvents.ITEM_SHIELD_BLOCK,
                SoundCategory.PLAYERS,
                0.9F,
                1.25F
        );
    }

    /**
     * Fabric's damage event can cancel but cannot replace the incoming amount. To preserve
     * vanilla armor, enchantment and invulnerability processing, the original explosion hit
     * is cancelled and immediately re-applied once with a guarded amount. The UUID set only
     * bypasses this listener for that nested call.
     */
    public static boolean allowDamage(
            ServerPlayerEntity player,
            DamageSource source,
            float amount
    ) {
        UUID ownerUuid = player.getUuid();
        if (EXPLOSION_DAMAGE_REENTRY.contains(ownerUuid)) {
            return true;
        }

        if (amount <= 0.0F
                || !source.isIn(DamageTypeTags.IS_EXPLOSION)
                || !ACTIVE_OWNERS.contains(ownerUuid)) {
            return true;
        }

        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() != LuciiLegacy.NOCTIS || !state.royalArmsActive()) {
            return true;
        }

        int itemCount = RoyalArmsInventoryItems.collect(player).size();
        if (itemCount <= 0) {
            return true;
        }

        float protection = Math.min(
                MAX_EXPLOSION_REDUCTION,
                itemCount * EXPLOSION_REDUCTION_PER_ITEM
        );
        float guardedAmount = amount * (1.0F - protection);

        if (player.getWorld() instanceof ServerWorld world) {
            RoyalArmsGuardNetwork.broadcastExplosionGuard(
                    world,
                    player,
                    itemCount,
                    protection
            );
            spawnExplosionGuardImpact(world, player, itemCount);
        }

        if (guardedAmount <= 0.0F) {
            return false;
        }

        EXPLOSION_DAMAGE_REENTRY.add(ownerUuid);
        try {
            player.damage(source, guardedAmount);
        } finally {
            EXPLOSION_DAMAGE_REENTRY.remove(ownerUuid);
        }
        return false;
    }

    private static void spawnExplosionGuardImpact(
            ServerWorld world,
            ServerPlayerEntity player,
            int itemCount
    ) {
        Vec3d playerPos = player.getPos();
        for (int layer = 0; layer < LAYER_COUNT; layer++) {
            int layerItems = itemCountInLayer(itemCount, layer);
            if (layerItems <= 0) {
                continue;
            }

            int particleCount = MathHelper.clamp(layerItems * 4, 5, 16);
            double y = playerPos.y + layerYOffset(layer);
            world.spawnParticles(
                    ParticleTypes.END_ROD,
                    playerPos.x,
                    y,
                    playerPos.z,
                    particleCount,
                    1.05D,
                    0.10D,
                    1.05D,
                    0.035D
            );
            world.spawnParticles(
                    ParticleTypes.CRIT,
                    playerPos.x,
                    y,
                    playerPos.z,
                    particleCount * 2,
                    1.35D,
                    0.14D,
                    1.35D,
                    0.12D
            );
        }

        Vec3d center = player.getBoundingBox().getCenter();
        world.playSound(
                null,
                center.x,
                center.y,
                center.z,
                SoundEvents.ITEM_SHIELD_BLOCK,
                SoundCategory.PLAYERS,
                1.15F,
                0.72F
        );
    }

    public static void clearAll(ServerPlayerEntity player) {
        UUID ownerUuid = player.getUuid();
        ACTIVE_OWNERS.remove(ownerUuid);
        UPKEEP_TIMERS.remove(ownerUuid);
        PENDING_BY_LAYER.keySet().removeIf(key -> key.ownerUuid.equals(ownerUuid));
        PENDING_BLOCKS.entrySet().removeIf(
                entry -> entry.getValue().ownerUuid.equals(ownerUuid)
        );
        EXPLOSION_DAMAGE_REENTRY.remove(ownerUuid);
    }

    public static void clearAll(MinecraftServer server) {
        ACTIVE_OWNERS.clear();
        PENDING_BY_LAYER.clear();
        PENDING_BLOCKS.clear();
        UPKEEP_TIMERS.clear();
        EXPLOSION_DAMAGE_REENTRY.clear();
    }

    private record OwnerLayerKey(UUID ownerUuid, int layer) {
    }

    private record Threat(
            PersistentProjectileEntity projectile,
            double timeToClosestApproach,
            Vec3d interceptPos,
            int travelTicks,
            int layer
    ) {
    }

    private static final class PendingBlock {
        private final UUID ownerUuid;
        private final PersistentProjectileEntity projectile;
        private final Vec3d interceptPos;
        private final Vec3d incomingVelocity;
        private final int layer;
        private int remainingTicks;

        private PendingBlock(
                UUID ownerUuid,
                PersistentProjectileEntity projectile,
                Vec3d interceptPos,
                Vec3d incomingVelocity,
                int remainingTicks,
                int layer
        ) {
            this.ownerUuid = ownerUuid;
            this.projectile = projectile;
            this.interceptPos = interceptPos;
            this.incomingVelocity = incomingVelocity;
            this.remainingTicks = remainingTicks;
            this.layer = layer;
        }
    }
}
