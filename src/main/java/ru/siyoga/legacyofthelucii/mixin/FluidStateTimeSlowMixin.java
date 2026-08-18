package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.fluid.FluidState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynTimeSlowAbility;

@Mixin(FluidState.class)
public abstract class FluidStateTimeSlowMixin {
    @Inject(
            method = "onRandomTick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void legacyOfTheLucii$slowRandomFluidTick(
            World world,
            BlockPos pos,
            Random random,
            CallbackInfo ci
    ) {
        if (world instanceof ServerWorld serverWorld
                && ArdynTimeSlowAbility.shouldSkipPositionTick(serverWorld, pos)) {
            ci.cancel();
        }
    }
}
