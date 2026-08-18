package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynTimeSlowAbility;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateTimeSlowMixin {
    @Inject(
            method = "randomTick(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void legacyOfTheLucii$slowRandomBlockTick(
            ServerWorld world,
            BlockPos pos,
            Random random,
            CallbackInfo ci
    ) {
        if (ArdynTimeSlowAbility.shouldSkipPositionTick(world, pos)) {
            ci.cancel();
        }
    }
}
