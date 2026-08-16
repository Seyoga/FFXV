package ru.siyoga.legacyofthelucii.masquerade;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
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
import ru.siyoga.legacyofthelucii.masquerade.ai.TargetPredicateAccess;

/**
 * Server-side perception for a single marked mob. It never changes the player
 * entity; it only adapts the class checks made by that mob's vanilla target goals.
 */
public final class MasqueradePerception {
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
        if (!targetClass.isAssignableFrom(perceivedType.getBaseClass())) {
            return false;
        }
        // The regular predicate is retained when it already accepts the real player.
        // Its optional custom predicate can contain instanceof checks, so the fallback
        // reproduces the standard non-type checks after the perceived class matched.
        return targetPredicate.test(observer, candidate)
                || passesStandardTargetChecks(observer, candidate, targetPredicate);
    }

    public static void refreshTargetRetention(MobEntity observer, ServerPlayerEntity player) {
        if (observer.getTarget() != player || getPerceivedEntityType(observer, player) == null) {
            return;
        }
        if (hasRunningRevengeGoal(observer)) {
            return;
        }
        if (!isAllowedByConfiguredVanillaGoals(observer, player)) {
            observer.setTarget(null);
        }
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
        // Fully custom mod AI that does not use ActiveTargetGoal is left untouched.
        return !hasActiveTargetGoal;
    }

    private static boolean passesStandardTargetChecks(
            MobEntity observer,
            LivingEntity candidate,
            TargetPredicate targetPredicate
    ) {
        TargetPredicateAccess access = (TargetPredicateAccess) targetPredicate;
        if (!candidate.isAlive() || candidate.isRemoved() || observer.isTeammate(candidate)) {
            return false;
        }
        if (candidate instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        if (access.legacyofthelucii$isAttackable() && !observer.canTarget(candidate)) {
            return false;
        }
        double maxDistance = access.legacyofthelucii$getBaseMaxDistance();
        if (maxDistance >= 0.0D && observer.squaredDistanceTo(candidate) > maxDistance * maxDistance) {
            return false;
        }
        return !access.legacyofthelucii$respectsVisibility()
                || observer.getVisibilityCache().canSee(candidate);
    }
}
