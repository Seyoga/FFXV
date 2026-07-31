package ru.siyoga.legacyofthelucii.royalarms.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
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
    private static final double DETECTION_RADIUS = 14.0D;
    private static final double MAX_CLOSEST_DISTANCE = 1.05D;
    private static final double LOOKAHEAD_TICKS = 12.0D;
    private static final double INTERCEPT_DISTANCE_FROM_PLAYER = 1.18D;
    private static final double MIN_PROJECTILE_SPEED_SQUARED = 0.0025D;
    private static final int IMPACT_DELAY_TICKS = 3;
    private static final int ITEM_BUSY_TICKS = 18;
    private static final int UPKEEP_INTERVAL_TICKS = 20;
    private static final int UPKEEP_MANA_COST = 2;
    private static final int INTERCEPT_MANA_COST = 5;

    private static final Set<UUID> ACTIVE_OWNERS = new HashSet<>();
    private static final Map<UUID, List<Reservation>> RESERVATIONS = new HashMap<>();
    private static final Map<UUID, BlockedProjectile> BLOCKED_PROJECTILES = new HashMap<>();
    private static final Map<UUID, Integer> UPKEEP_TIMERS = new HashMap<>();

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

        RoyalArmsGuardNetwork.sendState(player, active);
    }

    public static void tick(MinecraftServer server) {
        tickReservations();
        tickBlockedProjectiles();

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
                RoyalArmsGuardNetwork.sendState(player, false);
                LuciiNetwork.sendState(player);
                continue;
            }

            int itemCount = RoyalArmsInventoryItems.collect(player).size();
            if (itemCount <= 0 || busyCount(ownerUuid) >= itemCount) {
                continue;
            }

            interceptNearestThreat(player, itemCount - busyCount(ownerUuid));
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

    private static void interceptNearestThreat(ServerPlayerEntity player, int freeItems) {
        if (!(player.getWorld() instanceof ServerWorld world) || freeItems <= 0) {
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
            Threat threat = calculateThreat(playerCenter, projectile);
            if (threat != null) {
                threats.add(threat);
            }
        }

        threats.sort(Comparator.comparingDouble(Threat::timeToClosestApproach));
        int intercepted = 0;
        for (Threat threat : threats) {
            if (intercepted >= freeItems) {
                break;
            }

            PersistentProjectileEntity projectile = threat.projectile();
            if (BLOCKED_PROJECTILES.containsKey(projectile.getUuid())) {
                continue;
            }

            LuciiPlayerState state = LuciiPlayerStates.get(player);
            if (!state.spendMana(INTERCEPT_MANA_COST)) {
                clearAll(player);
                RoyalArmsGuardNetwork.sendState(player, false);
                LuciiNetwork.sendState(player);
                return;
            }

            beginBlock(player, projectile, threat.interceptPos());
            LuciiNetwork.sendState(player);
            intercepted++;
        }
    }

    private static boolean isCandidate(ServerPlayerEntity player, PersistentProjectileEntity projectile) {
        if (projectile.isRemoved() || BLOCKED_PROJECTILES.containsKey(projectile.getUuid())) {
            return false;
        }

        Entity projectileOwner = projectile.getOwner();
        if (projectileOwner != null && projectileOwner.getUuid().equals(player.getUuid())) {
            return false;
        }

        return projectile.getVelocity().lengthSquared() >= MIN_PROJECTILE_SPEED_SQUARED;
    }

    private static Threat calculateThreat(Vec3d playerCenter, PersistentProjectileEntity projectile) {
        Vec3d velocity = projectile.getVelocity();
        double speedSquared = velocity.lengthSquared();
        if (speedSquared < MIN_PROJECTILE_SPEED_SQUARED) {
            return null;
        }

        Vec3d toPlayer = playerCenter.subtract(projectile.getPos());
        double time = toPlayer.dotProduct(velocity) / speedSquared;
        if (time < 0.0D || time > LOOKAHEAD_TICKS) {
            return null;
        }

        Vec3d closestPoint = projectile.getPos().add(velocity.multiply(time));
        if (closestPoint.squaredDistanceTo(playerCenter) > MAX_CLOSEST_DISTANCE * MAX_CLOSEST_DISTANCE) {
            return null;
        }

        Vec3d incomingDirection = velocity.normalize();
        Vec3d interceptPos = playerCenter.subtract(incomingDirection.multiply(INTERCEPT_DISTANCE_FROM_PLAYER));
        return new Threat(projectile, time, interceptPos);
    }

    private static void beginBlock(
            ServerPlayerEntity player,
            PersistentProjectileEntity projectile,
            Vec3d interceptPos
    ) {
        Vec3d incomingVelocity = projectile.getVelocity();

        projectile.setNoGravity(true);
        projectile.setVelocity(Vec3d.ZERO);
        projectile.setPosition(interceptPos.x, interceptPos.y, interceptPos.z);

        UUID projectileUuid = projectile.getUuid();
        BLOCKED_PROJECTILES.put(
                projectileUuid,
                new BlockedProjectile(player.getUuid(), projectile, interceptPos, incomingVelocity)
        );
        RESERVATIONS.computeIfAbsent(player.getUuid(), ignored -> new ArrayList<>())
                .add(new Reservation(ITEM_BUSY_TICKS));

        RoyalArmsGuardNetwork.broadcastBlock((ServerWorld) player.getWorld(), player, interceptPos, incomingVelocity);
    }

    private static void tickBlockedProjectiles() {
        Iterator<Map.Entry<UUID, BlockedProjectile>> iterator = BLOCKED_PROJECTILES.entrySet().iterator();
        while (iterator.hasNext()) {
            BlockedProjectile blocked = iterator.next().getValue();
            blocked.age++;

            PersistentProjectileEntity projectile = blocked.projectile;
            if (projectile.isRemoved()) {
                iterator.remove();
                continue;
            }

            projectile.setNoGravity(true);
            projectile.setVelocity(Vec3d.ZERO);
            projectile.setPosition(blocked.interceptPos.x, blocked.interceptPos.y, blocked.interceptPos.z);

            if (blocked.age < IMPACT_DELAY_TICKS) {
                continue;
            }

            if (projectile.getWorld() instanceof ServerWorld world) {
                spawnImpact(world, blocked.interceptPos, blocked.incomingVelocity);
            }
            projectile.discard();
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

    private static void tickReservations() {
        Iterator<Map.Entry<UUID, List<Reservation>>> owners = RESERVATIONS.entrySet().iterator();
        while (owners.hasNext()) {
            List<Reservation> reservations = owners.next().getValue();
            reservations.removeIf(Reservation::tickAndFinished);
            if (reservations.isEmpty()) {
                owners.remove();
            }
        }
    }

    private static int busyCount(UUID ownerUuid) {
        List<Reservation> reservations = RESERVATIONS.get(ownerUuid);
        return reservations == null ? 0 : reservations.size();
    }

    public static void clearAll(ServerPlayerEntity player) {
        UUID ownerUuid = player.getUuid();
        ACTIVE_OWNERS.remove(ownerUuid);
        RESERVATIONS.remove(ownerUuid);
        UPKEEP_TIMERS.remove(ownerUuid);

        Iterator<Map.Entry<UUID, BlockedProjectile>> iterator = BLOCKED_PROJECTILES.entrySet().iterator();
        while (iterator.hasNext()) {
            BlockedProjectile blocked = iterator.next().getValue();
            if (!blocked.ownerUuid.equals(ownerUuid)) {
                continue;
            }

            if (!blocked.projectile.isRemoved()) {
                blocked.projectile.discard();
            }
            iterator.remove();
        }
    }

    public static void clearAll(MinecraftServer server) {
        for (BlockedProjectile blocked : BLOCKED_PROJECTILES.values()) {
            if (!blocked.projectile.isRemoved()) {
                blocked.projectile.discard();
            }
        }
        ACTIVE_OWNERS.clear();
        RESERVATIONS.clear();
        BLOCKED_PROJECTILES.clear();
        UPKEEP_TIMERS.clear();
    }

    private record Threat(
            PersistentProjectileEntity projectile,
            double timeToClosestApproach,
            Vec3d interceptPos
    ) {
    }

    private static final class Reservation {
        private int remainingTicks;

        private Reservation(int remainingTicks) {
            this.remainingTicks = remainingTicks;
        }

        private boolean tickAndFinished() {
            remainingTicks--;
            return remainingTicks <= 0;
        }
    }

    private static final class BlockedProjectile {
        private final UUID ownerUuid;
        private final PersistentProjectileEntity projectile;
        private final Vec3d interceptPos;
        private final Vec3d incomingVelocity;
        private int age;

        private BlockedProjectile(
                UUID ownerUuid,
                PersistentProjectileEntity projectile,
                Vec3d interceptPos,
                Vec3d incomingVelocity
        ) {
            this.ownerUuid = ownerUuid;
            this.projectile = projectile;
            this.interceptPos = interceptPos;
            this.incomingVelocity = incomingVelocity;
        }
    }
}
