package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.entity.ai.goal.UntamedActiveTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.siyoga.legacyofthelucii.masquerade.MasqueradePerception;
import ru.siyoga.legacyofthelucii.masquerade.ai.ActiveTargetGoalAccess;
import ru.siyoga.legacyofthelucii.masquerade.ai.TrackTargetGoalAccess;

/**
 * UntamedActiveTargetGoal keeps a target through TargetPredicate directly,
 * bypassing TrackTargetGoal's usual retention path.
 */
@Mixin(UntamedActiveTargetGoal.class)
public abstract class UntamedActiveTargetGoalMixin {
    @Inject(method = "shouldContinue", at = @At("HEAD"), cancellable = true)
    private void legacyofthelucii$continueTrackingPerceivedTarget(CallbackInfoReturnable<Boolean> cir) {
        MobEntity observer = ((TrackTargetGoalAccess) this).legacyofthelucii$getMob();
        ServerPlayerEntity deceiver = MasqueradePerception.getDeceiver(observer);
        if (deceiver == null || observer.getTarget() != deceiver) {
            return;
        }

        ActiveTargetGoalAccess access = (ActiveTargetGoalAccess) this;
        cir.setReturnValue(MasqueradePerception.canGoalTarget(
                observer,
                deceiver,
                access.legacyofthelucii$getTargetClass(),
                access.legacyofthelucii$getTargetPredicate()
        ));
    }
}
