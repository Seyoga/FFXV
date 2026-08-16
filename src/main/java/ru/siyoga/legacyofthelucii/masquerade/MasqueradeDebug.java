package ru.siyoga.legacyofthelucii.masquerade;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;

/** Debug output for a mob that is currently marked by an active Masquerade. */
public final class MasqueradeDebug {
    private static final boolean ENABLED = Boolean.getBoolean("legacyofthelucii.masqueradeDebug");

    private MasqueradeDebug() {
    }

    public static void logGoalDecision(
            MobEntity observer,
            ServerPlayerEntity deceiver,
            Goal goal,
            String phase,
            Class<?> targetClass,
            boolean canGoalTarget,
            Boolean vanillaCanStart
    ) {
        if (!ENABLED || MasqueradePerception.getPerceivedEntityType(observer, deceiver) == null) {
            return;
        }

        EntityType<?> perceivedType = MasqueradePerception.getPerceivedEntityType(observer, deceiver);
        LivingEntity currentTarget = observer.getTarget();
        LegacyOfTheLucii.LOGGER.info(
                "[MasqueradeDebug] phase={} observer={}({}) deceiver={} goal={} targetClass={} perceivedType={} perceivedBaseClass={} canGoalTarget={} vanillaCanStart={} mobTarget={} navigationIdle={}",
                phase,
                observer.getType(),
                observer.getUuid(),
                deceiver.getGameProfile().getName(),
                goal.getClass().getName(),
                targetClass.getName(),
                perceivedType,
                perceivedType.getBaseClass().getName(),
                canGoalTarget,
                vanillaCanStart,
                currentTarget == null ? "null" : currentTarget.getType() + "(" + currentTarget.getUuid() + ")",
                observer.getNavigation().isIdle()
        );
    }

    public static void logRetention(
            MobEntity observer,
            ServerPlayerEntity deceiver,
            String phase,
            boolean canRetainTarget
    ) {
        if (!ENABLED || MasqueradePerception.getPerceivedEntityType(observer, deceiver) == null) {
            return;
        }

        LivingEntity currentTarget = observer.getTarget();
        LegacyOfTheLucii.LOGGER.info(
                "[MasqueradeDebug] phase={} observer={}({}) deceiver={} canRetainTarget={} mobTarget={} navigationIdle={}",
                phase,
                observer.getType(),
                observer.getUuid(),
                deceiver.getGameProfile().getName(),
                canRetainTarget,
                currentTarget == null ? "null" : currentTarget.getType() + "(" + currentTarget.getUuid() + ")",
                observer.getNavigation().isIdle()
        );
    }
}
