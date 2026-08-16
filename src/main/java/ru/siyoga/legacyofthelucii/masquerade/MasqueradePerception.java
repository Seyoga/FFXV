package ru.siyoga.legacyofthelucii.masquerade;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.masquerade.ai.ActiveTargetGoalAccess;
import ru.siyoga.legacyofthelucii.masquerade.ai.MobEntityTargetSelectorAccess;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MasqueradePerception {
    private static final Map<UUID, UUID> MASQUERADE_AGGRESSION = new HashMap<>();

    private MasqueradePerception() {
    }

    public static EntityType<?> getPerceivedEntityType(MobEntity observer, LivingEntity candidate) {
        if (!(candidate instanceof ServerPlayerEntity player)) {
            return null;
        }
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        MasqueradeMorph morph = state.legacy() == LuciiLegacy.ARDYN
                && observer.getUuid().equals(state.masqueradeTargetUuid())
                ? state.activeMorph()
                : null;
        if (morph == null) {
            return null;
        }
        if (morph.kind() == MasqueradeMorph.Kind.PLAYER) {
            return EntityType.PLAYER;
        }
        return Registries.ENTITY_TYPE.containsId(morph.entityTypeId())
                ? Registries.ENTITY_TYPE.get(morph.entityTypeId())
                : null;
    }

    public static ServerPlayerEntity getDeceiver(MobEntity observer) {
        if (!(observer.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            if (player.getWorld() != world) {
                continue;
            }
            LuciiPlayerState state = LuciiPlayerStates.get(player);
            if (state.legacy() == LuciiLegacy.ARDYN
                    && observer.getUuid().equals(state.masqueradeTargetUuid())
                    && state.activeMorph() != null) {
                return player;
            }
        }
        return null;
    }

    public static boolean canGoalTarget(
            MobEntity observer,
            LivingEntity candidate,
            Class<?> targetClass,
            TargetPredicate targetPredicate
    ) {
        EntityType<?> perceivedType = getPerceivedEntityType(observer, candidate);
        if (perceivedType == null) {
            return targetPredicate.test(observer, candidate);
        }
        if (!candidate.isAlive() || candidate.isRemoved() || observer.isTeammate(candidate)) {
            return false;
        }
        if (candidate instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
            return false;
        }

        if (perceivedType == EntityType.PLAYER) {
            return targetClass.isInstance(candidate) && targetPredicate.test(observer, candidate);
        }

        LivingEntity perceivedEntity = createPerceivedEntity(observer, candidate, perceivedType);
        if (perceivedEntity == null || !targetClass.isInstance(perceivedEntity)) {
            return false;
        }

        return targetPredicate.test(observer, perceivedEntity);
    }

    public static void markMasqueradeAcquisition(MobEntity observer, ServerPlayerEntity player) {
        if (observer.getAttacker() == player || hasRunningRevengeGoal(observer)) {
            MASQUERADE_AGGRESSION.remove(observer.getUuid());
            return;
        }
        MASQUERADE_AGGRESSION.put(observer.getUuid(), player.getUuid());
    }

    public static void refreshTargetRetention(MobEntity observer, ServerPlayerEntity player) {
        if (getPerceivedEntityType(observer, player) == null) {
            return;
        }
        if (observer.getAttacker() == player || hasRunningRevengeGoal(observer)) {
            forgetMasqueradeAggression(observer, player);
            return;
        }
        boolean canRetainTarget = isAllowedByConfiguredVanillaGoals(observer, player);
        MasqueradeDebug.logRetention(observer, player, "refreshTargetRetention.before", canRetainTarget);
        if (canRetainTarget) {
            return;
        }
        // This target can predate the Masquerade. It is therefore not necessarily
        // present in MASQUERADE_AGGRESSION, but it is still invalid now.
        clearTargetAndAnger(observer, player);
        forgetMasqueradeAggression(observer, player);
        MasqueradeDebug.logRetention(observer, player, "refreshTargetRetention.afterClear", false);
    }

    public static void clearMasqueradeAggression(MobEntity observer, ServerPlayerEntity player) {
        if (!isMasqueradeAggression(observer, player)) {
            return;
        }
        MASQUERADE_AGGRESSION.remove(observer.getUuid());
        if (observer.getAttacker() == player || hasRunningRevengeGoal(observer)) {
            return;
        }
        if (isAllowedByConfiguredVanillaGoalsReal(observer, player)) {
            return;
        }
        clearTargetAndAnger(observer, player);
    }

    public static void forgetMasqueradeAggression(UUID observerUuid, UUID playerUuid) {
        if (playerUuid.equals(MASQUERADE_AGGRESSION.get(observerUuid))) {
            MASQUERADE_AGGRESSION.remove(observerUuid);
        }
    }

    private static void forgetMasqueradeAggression(MobEntity observer, ServerPlayerEntity player) {
        if (isMasqueradeAggression(observer, player)) {
            MASQUERADE_AGGRESSION.remove(observer.getUuid());
        }
    }

    private static boolean isMasqueradeAggression(MobEntity observer, ServerPlayerEntity player) {
        return player.getUuid().equals(MASQUERADE_AGGRESSION.get(observer.getUuid()));
    }

    private static void clearTargetAndAnger(MobEntity observer, ServerPlayerEntity player) {
        if (observer.getTarget() == player) {
            observer.setTarget(null);
        }
        if (observer instanceof Angerable angerable && player.getUuid().equals(angerable.getAngryAt())) {
            angerable.stopAnger();
        }
    }

    private static LivingEntity createPerceivedEntity(
            MobEntity observer,
            LivingEntity candidate,
            EntityType<?> perceivedType
    ) {
        Entity entity;
        try {
            entity = perceivedType.create(observer.getWorld());
        } catch (RuntimeException ignored) {
            return null;
        }
        if (!(entity instanceof LivingEntity perceivedEntity)) {
            return null;
        }

        perceivedEntity.setId(candidate.getId());
        perceivedEntity.setUuid(candidate.getUuid());
        perceivedEntity.refreshPositionAndAngles(
                candidate.getX(),
                candidate.getY(),
                candidate.getZ(),
                candidate.getYaw(),
                candidate.getPitch()
        );
        perceivedEntity.setInvisible(candidate.isInvisible());
        perceivedEntity.setOnGround(candidate.isOnGround());
        return perceivedEntity;
    }

    private static boolean hasRunningRevengeGoal(MobEntity observer) {
        return ((MobEntityTargetSelectorAccess) observer).legacyofthelucii$getTargetSelector()
                .getRunningGoals()
                .map(PrioritizedGoal::getGoal)
                .anyMatch(RevengeGoal.class::isInstance);
    }

    private static boolean isAllowedByConfiguredVanillaGoals(MobEntity observer, ServerPlayerEntity player) {
        boolean hasActiveTargetGoal = false;
        for (PrioritizedGoal prioritizedGoal : ((MobEntityTargetSelectorAccess) observer)
                .legacyofthelucii$getTargetSelector().getGoals()) {
            Goal goal = prioritizedGoal.getGoal();
            if (!(goal instanceof ActiveTargetGoal<?> activeTargetGoal)
                    || !(activeTargetGoal instanceof ActiveTargetGoalAccess access)) {
                continue;
            }
            hasActiveTargetGoal = true;
            if (canGoalTarget(
                    observer,
                    player,
                    access.legacyofthelucii$getTargetClass(),
                    access.legacyofthelucii$getTargetPredicate()
            )) {
                return true;
            }
        }
        return !hasActiveTargetGoal;
    }

    private static boolean isAllowedByConfiguredVanillaGoalsReal(MobEntity observer, ServerPlayerEntity player) {
        boolean hasActiveTargetGoal = false;
        for (PrioritizedGoal prioritizedGoal : ((MobEntityTargetSelectorAccess) observer)
                .legacyofthelucii$getTargetSelector().getGoals()) {
            Goal goal = prioritizedGoal.getGoal();
            if (!(goal instanceof ActiveTargetGoal<?> activeTargetGoal)
                    || !(activeTargetGoal instanceof ActiveTargetGoalAccess access)) {
                continue;
            }
            hasActiveTargetGoal = true;
            if (access.legacyofthelucii$getTargetClass().isInstance(player)
                    && access.legacyofthelucii$getTargetPredicate().test(observer, player)) {
                return true;
            }
        }
        return !hasActiveTargetGoal;
    }
}
