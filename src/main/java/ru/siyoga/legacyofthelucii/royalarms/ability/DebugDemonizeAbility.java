package ru.siyoga.legacyofthelucii.royalarms.ability;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.effect.Demonization;

import java.util.Comparator;
import java.util.List;

public final class DebugDemonizeAbility {
    private static final double RAY_DISTANCE = 5.0D;
    private static final double RAY_WIDTH = 1.0D;
    private static final double NEARBY_RADIUS = 2.25D;
    private static final int DEMONIZATION_STRENGTH = 0;

    private DebugDemonizeAbility() {
    }

    public static void tryDemonize(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        findTarget(player, world).stream()
                .findFirst()
                .ifPresent(target -> Demonization.forceApplyPermanentFullHealth(target, DEMONIZATION_STRENGTH));
    }

    private static List<MobEntity> findTarget(ServerPlayerEntity player, ServerWorld world) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d end = eye.add(look.multiply(RAY_DISTANCE));
        Box rayBox = player.getBoundingBox().stretch(look.multiply(RAY_DISTANCE)).expand(RAY_WIDTH);

        List<MobEntity> rayTargets = world.getEntitiesByClass(MobEntity.class, rayBox, mob -> isValidTarget(player, mob));
        rayTargets.sort(Comparator.comparingDouble(mob -> distanceToRay(eye, end, mob.getBoundingBox().getCenter())));
        if (!rayTargets.isEmpty() && distanceToRay(eye, end, rayTargets.get(0).getBoundingBox().getCenter()) <= RAY_WIDTH) {
            return rayTargets;
        }

        Box nearbyBox = player.getBoundingBox().expand(NEARBY_RADIUS);
        List<MobEntity> nearbyTargets = world.getEntitiesByClass(MobEntity.class, nearbyBox, mob -> isValidTarget(player, mob));
        nearbyTargets.sort(Comparator.comparingDouble(player::squaredDistanceTo));
        return nearbyTargets;
    }

    private static boolean isValidTarget(ServerPlayerEntity player, MobEntity mob) {
        return mob.isAlive()
                && !mob.isRemoved()
                && mob.getWorld() == player.getWorld()
                && !Demonization.isDemonized(mob);
    }

    private static double distanceToRay(Vec3d start, Vec3d end, Vec3d point) {
        Vec3d ray = end.subtract(start);
        double lengthSquared = ray.lengthSquared();
        if (lengthSquared <= 0.0001D) {
            return point.distanceTo(start);
        }

        double t = point.subtract(start).dotProduct(ray) / lengthSquared;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return point.distanceTo(start.add(ray.multiply(t)));
    }
}
