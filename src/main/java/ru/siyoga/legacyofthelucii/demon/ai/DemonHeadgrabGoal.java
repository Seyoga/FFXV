package ru.siyoga.legacyofthelucii.demon.ai;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.demon.DemonFaction;
import ru.siyoga.legacyofthelucii.demon.headgrab.DemonHeadgrabSystem;

import java.util.Comparator;
import java.util.EnumSet;

public final class DemonHeadgrabGoal extends Goal {
    private static final double SEARCH_RANGE = 18.0D;
    private static final double SEARCH_RANGE_SQUARED =
            SEARCH_RANGE * SEARCH_RANGE;

    private static final double ATTACH_HORIZONTAL_DISTANCE = 1.25D;
    private static final double ATTACH_VERTICAL_DISTANCE = 1.55D;

    private final SlimeEntity slime;

    private ServerPlayerEntity target;
    private int leapCooldown;

    public DemonHeadgrabGoal(SlimeEntity slime) {
        this.slime = slime;
        setControls(EnumSet.of(
                Control.MOVE,
                Control.LOOK,
                Control.JUMP
        ));
    }

    @Override
    public boolean canStart() {
        if (!DemonHeadgrabSystem.canAttemptAttachment(slime)
                || slime.isAiDisabled()
                || !(slime.getWorld() instanceof ServerWorld world)) {
            return false;
        }

        target = world.getPlayers(player ->
                        isValidTarget(player)
                                && slime.squaredDistanceTo(player)
                                <= SEARCH_RANGE_SQUARED
                )
                .stream()
                .min(Comparator.comparingDouble(
                        slime::squaredDistanceTo
                ))
                .orElse(null);

        return target != null;
    }

    @Override
    public boolean shouldContinue() {
        return target != null
                && DemonHeadgrabSystem.canAttemptAttachment(slime)
                && isValidTarget(target)
                && slime.squaredDistanceTo(target)
                <= SEARCH_RANGE_SQUARED * 1.35D;
    }

    @Override
    public void start() {
        leapCooldown = 0;
        slime.setTarget(target);
        slime.setAttacking(true);
    }

    @Override
    public void stop() {
        slime.setAttacking(false);
        slime.getNavigation().stop();

        if (slime.getTarget() == target) {
            slime.setTarget(null);
        }

        target = null;
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }

        slime.getLookControl().lookAt(target, 35.0F, 35.0F);

        double dx = target.getX() - slime.getX();
        double dz = target.getZ() - slime.getZ();
        double horizontalSquared = dx * dx + dz * dz;

        double slimeCenterY =
                slime.getY() + slime.getHeight() * 0.5D;

        double targetHeadY =
                target.getEyeY() + 0.10D;

        if (!slime.isOnGround()
                && horizontalSquared
                <= ATTACH_HORIZONTAL_DISTANCE
                * ATTACH_HORIZONTAL_DISTANCE
                && Math.abs(slimeCenterY - targetHeadY)
                <= ATTACH_VERTICAL_DISTANCE
                && DemonHeadgrabSystem.tryAttach(slime, target)) {
            return;
        }

        Vec3d flatDirection = new Vec3d(
                dx,
                0.0D,
                dz
        );

        if (flatDirection.lengthSquared() > 0.0001D) {
            flatDirection = flatDirection.normalize();
        }

        if (leapCooldown > 0) {
            leapCooldown--;
        }

        if (slime.isOnGround() && leapCooldown <= 0) {
            double distance = Math.sqrt(horizontalSquared);
            double horizontalSpeed =
                    distance > 5.0D ? 0.50D : 0.39D;

            slime.setVelocity(
                    flatDirection.x * horizontalSpeed,
                    distance < 2.8D ? 0.66D : 0.56D,
                    flatDirection.z * horizontalSpeed
            );

            slime.velocityDirty = true;
            leapCooldown = 8;
            return;
        }

        if (!slime.isOnGround()) {
            Vec3d velocity = slime.getVelocity();

            slime.setVelocity(
                    velocity.x * 0.90D
                            + flatDirection.x * 0.045D,
                    velocity.y,
                    velocity.z * 0.90D
                            + flatDirection.z * 0.045D
            );

            slime.velocityDirty = true;
        }
    }

    private boolean isValidTarget(
            ServerPlayerEntity player
    ) {
        return player.isAlive()
                && !player.isRemoved()
                && player.getWorld() == slime.getWorld()
                && !DemonHeadgrabSystem.isVictim(player)
                && DemonFaction.canAttack(slime, player);
    }
}
