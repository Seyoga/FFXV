package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynTimeSlowAbility;

@Mixin(targets = "net.minecraft.world.chunk.WorldChunk$DirectBlockEntityTickInvoker")
public abstract class DirectBlockEntityTickInvokerTimeSlowMixin {
    @Shadow @Final private BlockEntity blockEntity;

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void legacyOfTheLucii$slowBlockEntityTick(CallbackInfo ci) {
        if (blockEntity.getWorld() instanceof ServerWorld world
                && ArdynTimeSlowAbility.shouldSkipPositionTick(world, blockEntity.getPos())) {
            ci.cancel();
        }
    }
}
