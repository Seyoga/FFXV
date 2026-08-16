package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.siyoga.legacyofthelucii.masquerade.MasqueradeDebug;
import ru.siyoga.legacyofthelucii.masquerade.MasqueradePerception;
import ru.siyoga.legacyofthelucii.masquerade.ai.ActiveTargetGoalAccess;

@Mixin(TrackTargetGoal.class)
public abstract class TrackTargetGoalMixin {
    @Shadow @Final protected MobEntity mob;
    @Shadow @Nullable protected LivingEntity target;

    @Inject(method = "shouldContinue", at = @At("HEAD"), cancellable = true)
    private void legacyofthelucii$stopInvalidPerceivedTarget(CallbackInfoReturnable<Boolean> cir) {
        // ActiveTargetGoal.start() assigns MobEntity.target. The inherited
        // TrackTargetGoal.target field is only a fallback and is normally null.
        LivingEntity currentTarget = mob.getTarget();
        if (currentTarget == null) {
            currentTarget = target;
        }
        if (!(currentTarget instanceof ServerPlayerEntity deceiver)) {
            return;
        }
        if (MasqueradePerception.getPerceivedEntityType(mob, deceiver) == null) {
            return;
        }
        if (!((Object) this instanceof ActiveTargetGoalAccess access)) {
            return;
        }
        boolean canGoalTarget = MasqueradePerception.canGoalTarget(
                mob,
                deceiver,
                access.legacyofthelucii$getTargetClass(),
                access.legacyofthelucii$getTargetPredicate()
        );
        MasqueradeDebug.logGoalDecision(
                mob,
                deceiver,
                (TrackTargetGoal) (Object) this,
                "TrackTargetGoal.shouldContinue",
                access.legacyofthelucii$getTargetClass(),
                canGoalTarget,
                null
        );
        if (canGoalTarget) {
            return;
        }

        mob.setTarget(null);
        target = null;
        mob.getNavigation().stop();
        cir.setReturnValue(false);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void legacyofthelucii$logPerceivedTargetStop(CallbackInfo ci) {
        LivingEntity currentTarget = mob.getTarget();
        if (currentTarget == null) {
            currentTarget = target;
        }
        if (!(currentTarget instanceof ServerPlayerEntity deceiver)
                || !((Object) this instanceof ActiveTargetGoalAccess access)) {
            return;
        }
        MasqueradeDebug.logGoalDecision(
                mob,
                deceiver,
                (TrackTargetGoal) (Object) this,
                "TrackTargetGoal.stop",
                access.legacyofthelucii$getTargetClass(),
                MasqueradePerception.canGoalTarget(
                        mob,
                        deceiver,
                        access.legacyofthelucii$getTargetClass(),
                        access.legacyofthelucii$getTargetPredicate()
                ),
                null
        );
    }
}
