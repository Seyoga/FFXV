package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.siyoga.legacyofthelucii.masquerade.MasqueradePerception;
import ru.siyoga.legacyofthelucii.masquerade.ai.ActiveTargetGoalAccess;
import ru.siyoga.legacyofthelucii.masquerade.ai.TrackTargetGoalAccess;

@Mixin(ActiveTargetGoal.class)
public abstract class ActiveTargetGoalMixin<T extends LivingEntity> implements ActiveTargetGoalAccess {
    @Shadow @Final private Class<T> targetClass;
    @Shadow private TargetPredicate targetPredicate;
    @Shadow @Nullable private LivingEntity targetEntity;

    @Override
    public Class<?> legacyofthelucii$getTargetClass() {
        return targetClass;
    }

    @Override
    public TargetPredicate legacyofthelucii$getTargetPredicate() {
        return targetPredicate;
    }

    @Override
    public LivingEntity legacyofthelucii$getTargetEntity() {
        return targetEntity;
    }

    @Override
    public void legacyofthelucii$setTargetEntity(LivingEntity targetEntity) {
        this.targetEntity = targetEntity;
    }

    @Override
    public void legacyofthelucii$findClosestTarget() {
        findClosestTarget();
    }

    @Inject(method = "canStart", at = @At("HEAD"), cancellable = true)
    private void legacyofthelucii$selectPerceivedTarget(CallbackInfoReturnable<Boolean> cir) {
        MobEntity observer = ((TrackTargetGoalAccess) this).legacyofthelucii$getMob();
        ServerPlayerEntity deceiver = MasqueradePerception.getDeceiver(observer);
        if (deceiver == null) {
            return;
        }
        if (!MasqueradePerception.canGoalTarget(observer, deceiver, targetClass, targetPredicate)) {
            return;
        }

        // The vanilla query is still used for ordinary entities. It cannot see the
        // deceiver for non-player target classes, so add the perceived candidate
        // before the goal's reciprocal-chance early return can discard the search.
        findClosestTarget();
        if (targetEntity == null || observer.squaredDistanceTo(deceiver) < observer.squaredDistanceTo(targetEntity)) {
            targetEntity = deceiver;
        }
        cir.setReturnValue(targetEntity != null);
    }

    @Inject(method = "canStart", at = @At("TAIL"), cancellable = true)
    private void legacyofthelucii$rejectInvalidPerceivedPlayerTarget(CallbackInfoReturnable<Boolean> cir) {
        MobEntity observer = ((TrackTargetGoalAccess) this).legacyofthelucii$getMob();
        ServerPlayerEntity deceiver = MasqueradePerception.getDeceiver(observer);
        if (deceiver != null
                && !MasqueradePerception.canGoalTarget(observer, deceiver, targetClass, targetPredicate)
                && targetEntity == deceiver) {
            targetEntity = null;
            cir.setReturnValue(false);
        }
    }

    @Shadow
    protected abstract void findClosestTarget();
}
