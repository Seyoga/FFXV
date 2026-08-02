package ru.siyoga.legacyofthelucii.royalarms.ability;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.effect.Demonization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Ctrl+5 development helper. Prefer a fresh mob so an old demon does not steal the test. */
public final class DebugDemonizeAbility {
    private static final double RAY_DISTANCE = 12.0D;
    private static final double RAY_WIDTH = 1.25D;
    private static final double NEARBY_RADIUS = 3.25D;
    private static final int DEMONIZATION_STRENGTH = 0;
    private static final DustParticleEffect CONFIRM_PARTICLE = new DustParticleEffect(
            new Vector3f(0.22F, 0.01F, 0.34F),
            1.15F
    );

    private DebugDemonizeAbility() {
    }

    public static void tryDemonize(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        List<MobEntity> candidates = findTargets(player, world);
        Optional<MobEntity> freshTarget = candidates.stream()
                .filter(mob -> !Demonization.isDemonized(mob))
                .findFirst();
        Optional<MobEntity> targetOptional = freshTarget.isPresent()
                ? freshTarget
                : candidates.stream().findFirst();

        if (targetOptional.isEmpty()) {
            LegacyOfTheLucii.LOGGER.info("Demonization debug: Ctrl+5 received, but no mob was found.");
            player.sendMessage(
                    Text.literal("Ctrl+5: моб не найден — наведи прицел на моба в радиусе 12 блоков.")
                            .formatted(Formatting.RED),
                    true
            );
            return;
        }

        MobEntity target = targetOptional.get();
        LegacyOfTheLucii.LOGGER.info(
                "Demonization debug: selected entity id={}, type={}, uuid={}, alreadyDemonized={}",
                target.getId(),
                target.getType(),
                target.getUuid(),
                Demonization.isDemonized(target)
        );

        if (Demonization.isDemonized(target)) {
            if (Demonization.getDemonizerUuid(target) == null) {
                Demonization.assignDemonizer(target, player);
                player.sendMessage(
                        Text.literal("Ctrl+5: старому демону назначен создатель — ")
                                .append(target.getDisplayName())
                                .formatted(Formatting.DARK_PURPLE),
                        true
                );
            } else {
                player.sendMessage(
                        Text.literal("Ctrl+5: ")
                                .append(target.getDisplayName())
                                .append(Text.literal(" уже демонифицирован. id=" + target.getId()))
                                .formatted(Formatting.DARK_PURPLE),
                        true
                );
            }
            return;
        }

        Demonization.forceApplyPermanentFullHealth(player, target, DEMONIZATION_STRENGTH);

        if (!Demonization.isDemonized(target)) {
            LegacyOfTheLucii.LOGGER.warn(
                    "Demonization debug: status effect was not present after application for entity id={}",
                    target.getId()
            );
            player.sendMessage(
                    Text.literal("Ctrl+5: эффект демонизации не применился.").formatted(Formatting.RED),
                    true
            );
            return;
        }

        Vec3d center = target.getBoundingBox().getCenter();
        world.spawnParticles(
                CONFIRM_PARTICLE,
                center.x,
                center.y,
                center.z,
                28,
                Math.max(0.25D, target.getWidth() * 0.55D),
                Math.max(0.35D, target.getHeight() * 0.45D),
                Math.max(0.25D, target.getWidth() * 0.55D),
                0.015D
        );

        LegacyOfTheLucii.LOGGER.info(
                "Demonization debug: applied permanent status to entity id={}, type={}, uuid={}",
                target.getId(),
                target.getType(),
                target.getUuid()
        );
        player.sendMessage(
                Text.literal("Ctrl+5: демонифицирован — ")
                        .append(target.getDisplayName())
                        .append(Text.literal(" id=" + target.getId()))
                        .formatted(Formatting.LIGHT_PURPLE),
                true
        );
    }

    private static List<MobEntity> findTargets(ServerPlayerEntity player, ServerWorld world) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d end = eye.add(look.multiply(RAY_DISTANCE));
        Box rayBox = player.getBoundingBox().stretch(look.multiply(RAY_DISTANCE)).expand(RAY_WIDTH);

        List<MobEntity> rayTargets = new ArrayList<>(world.getEntitiesByClass(
                MobEntity.class,
                rayBox,
                mob -> isValidTarget(player, mob)
                        && distanceToRay(eye, end, mob.getBoundingBox().getCenter()) <= RAY_WIDTH
        ));
        rayTargets.sort(Comparator
                .comparingDouble((MobEntity mob) -> distanceToRay(eye, end, mob.getBoundingBox().getCenter()))
                .thenComparingDouble(player::squaredDistanceTo));

        if (!rayTargets.isEmpty()) {
            return rayTargets;
        }

        List<MobEntity> nearbyTargets = new ArrayList<>(world.getEntitiesByClass(
                MobEntity.class,
                player.getBoundingBox().expand(NEARBY_RADIUS),
                mob -> isValidTarget(player, mob)
        ));
        nearbyTargets.sort(Comparator.comparingDouble(player::squaredDistanceTo));
        return nearbyTargets;
    }

    private static boolean isValidTarget(ServerPlayerEntity player, MobEntity mob) {
        return mob.isAlive()
                && !mob.isRemoved()
                && mob.getWorld() == player.getWorld();
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
