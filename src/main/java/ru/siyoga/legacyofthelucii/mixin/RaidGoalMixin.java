package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.RaidGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.siyoga.legacyofthelucii.masquerade.MasqueradePerception;
import ru.siyoga.legacyofthelucii.masquerade.ai.ActiveTargetGoalAccess;
import ru.siyoga.legacyofthelucii.masquerade.ai.TrackTargetGoalAccess;

/**
 * RaidGoal has its own target search instead of delegating to
 * ActiveTargetGoal.canStart, so it needs the same virtual candidate path.
 */
@Mixin(RaidGoal.class)
public abstract class RaidGoalMixin {
    @Shadow private int cooldown;

    @Inject(method = "canStart", at = @At("HEAD"), cancellable = true)
    private void legacyofthelucii$selectPerceivedRaidTarget(CallbackInfoReturnable<Boolean> cir) {
        MobEntity observer = ((TrackTargetGoalAccess) this).legacyofthelucii$getMob();
        if (!(observer instanceof RaiderEntity raider)) {
            return;
        }
        ServerPlayerEntity deceiver = MasqueradePerception.getDeceiver(observer);
        if (deceiver == null) {
            return;
        }

        // These are RaidGoal's native activation constraints. Reproduce them
        // before substituting the candidate so raid timing remains unchanged.
        if (cooldown > 0 || !observer.getRandom().nextBoolean() || !raider.hasActiveRaid()) {
            cir.setReturnValue(false);
            return;
        }

        ActiveTargetGoalAccess access = (ActiveTargetGoalAccess) this;
        access.legacyofthelucii$findClosestTarget();
        LivingEntity target = access.legacyofthelucii$getTargetEntity();
        boolean deceiverAllowed = MasqueradePerception.canGoalTarget(
                observer,
                deceiver,
                access.legacyofthelucii$getTargetClass(),
                access.legacyofthelucii$getTargetPredicate()
        );
        if (deceiverAllowed && (target == null || observer.squaredDistanceTo(deceiver) < observer.squaredDistanceTo(target))) {
            access.legacyofthelucii$setTargetEntity(deceiver);
            target = deceiver;
        }
        if (target == deceiver && !deceiverAllowed) {
            access.legacyofthelucii$setTargetEntity(null);
            target = null;
        }
        cir.setReturnValue(target != null);
    }
}
