package ru.siyoga.legacyofthelucii.royalarms.orbit;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;

/** Shared deterministic trajectory math used by server collision and client rendering. */
public final class RoyalArmsOrbitMath {
    public static final double RADIUS = 2.5D;
    public static final double INNER_RADIUS = 1.45D;
    public static final double ORBIT_Y_OFFSET = 1.0D;
    public static final double BOB_HEIGHT = 0.3D;
    public static final float NORMAL_ORBIT_SPEED = 2.0F;
    public static final float FAST_ORBIT_SPEED = 5.0F;
    public static final float ARDYN_INNER_RING_SPEED_MULTIPLIER = 1.65F;
    public static final float ANGLE_APPROACH = 0.18F;
    public static final float INNER_APPROACH = 0.12F;
    public static final int APPEAR_TICKS = 24;

    private RoyalArmsOrbitMath() {
    }

    public static Vec3d position(
            Vec3d ownerPos,
            int index,
            double phase,
            float baseAngle,
            float innerProgress,
            boolean innerTarget,
            float appearanceProgress
    ) {
        float direction = innerTarget ? -ARDYN_INNER_RING_SPEED_MULTIPLIER : 1.0F;
        double angle = Math.toRadians(baseAngle + phase * direction);
        double ringRadius = MathHelper.lerp(
                MathHelper.clamp(innerProgress, 0.0F, 1.0F),
                (float) RADIUS,
                (float) INNER_RADIUS
        );
        double radiusProgress = easeOutCubic(appearanceProgress);
        double radius = ringRadius * radiusProgress;
        double x = Math.sin(angle) * radius;
        double z = Math.cos(angle) * radius;
        double y = ORBIT_Y_OFFSET
                + Math.sin(phase * 0.05D + index) * BOB_HEIGHT * radiusProgress;
        return ownerPos.add(x, y, z);
    }

    public static int innerRingCount(LuciiLegacy legacy, int charges, int total) {
        if (legacy != LuciiLegacy.ARDYN || charges < 3 || total < 2) {
            return 0;
        }
        int stage = MathHelper.clamp(charges / 3, 1, 4);
        int maxInnerCount = Math.max(1, total / 2);
        return Math.max(1, MathHelper.ceil(maxInnerCount * (stage / 4.0F)));
    }

    public static boolean innerRingTarget(LuciiLegacy legacy, int charges, int index, int total) {
        return index <= innerRingCount(legacy, charges, total);
    }

    public static float targetBaseAngle(LuciiLegacy legacy, int charges, int index, int total) {
        int innerCount = innerRingCount(legacy, charges, total);
        if (innerCount <= 0) {
            return index * 360.0F / Math.max(1, total);
        }
        if (index <= innerCount) {
            return index * 360.0F / innerCount;
        }
        int outerCount = total - innerCount;
        if (outerCount <= 0) {
            return index * 360.0F / Math.max(1, total);
        }
        return (index - innerCount) * 360.0F / outerCount;
    }

    public static float advanceAngle(float current, float target, double ticks) {
        if (ticks <= 0.0D) {
            return current;
        }
        float diff = MathHelper.wrapDegrees(target - current);
        double remaining = Math.pow(1.0D - ANGLE_APPROACH, ticks);
        return current + diff * (float) (1.0D - remaining);
    }

    public static float advanceInnerProgress(float current, boolean target, double ticks) {
        float targetValue = target ? 1.0F : 0.0F;
        if (ticks <= 0.0D) {
            return current;
        }
        double remaining = Math.pow(1.0D - INNER_APPROACH, ticks);
        return targetValue + (current - targetValue) * (float) remaining;
    }

    public static float appearanceProgress(double spawnTicks) {
        return MathHelper.clamp((float) (spawnTicks / APPEAR_TICKS), 0.0F, 1.0F);
    }

    public static float easeOutCubic(float value) {
        float clamped = MathHelper.clamp(value, 0.0F, 1.0F);
        float inverse = 1.0F - clamped;
        return 1.0F - inverse * inverse * inverse;
    }
}
