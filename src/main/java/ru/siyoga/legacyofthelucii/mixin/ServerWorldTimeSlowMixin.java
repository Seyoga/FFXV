package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynTimeSlowAbility;

@Mixin(ServerWorld.class)
public abstract class ServerWorldTimeSlowMixin {
    @Inject(
            method = "tickEntity(Lnet/minecraft/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void legacyOfTheLucii$slowEntity(Entity entity, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (ArdynTimeSlowAbility.shouldSkipEntityTick(world, entity)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "tickBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void legacyOfTheLucii$slowScheduledBlockTick(BlockPos pos, Block block, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (ArdynTimeSlowAbility.shouldSkipPositionTick(world, pos)) {
            ArdynTimeSlowAbility.scheduleUnscaledBlockTick(world, pos, block, 1);
            ci.cancel();
        }
    }

    @Inject(
            method = "tickFluid(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/fluid/Fluid;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void legacyOfTheLucii$slowScheduledFluidTick(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (ArdynTimeSlowAbility.shouldSkipPositionTick(world, pos)) {
            ArdynTimeSlowAbility.scheduleUnscaledFluidTick(world, pos, fluid, 1);
            ci.cancel();
        }
    }
}
