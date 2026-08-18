package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.tick.TickPriority;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynTimeSlowAbility;

@Mixin(WorldAccess.class)
public interface WorldAccessTimeSlowMixin {
    @ModifyVariable(
            method = "scheduleBlockTick(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;ILnet/minecraft/world/tick/TickPriority;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private int legacyOfTheLucii$scaleScheduledBlockDelay(
            int delay,
            BlockPos pos,
            Block block,
            int originalDelay,
            TickPriority priority
    ) {
        Object self = this;
        if (self instanceof ServerWorld world) {
            return ArdynTimeSlowAbility.scaleScheduledDelay(world, pos, delay);
        }
        return delay;
    }

    @ModifyVariable(
            method = "scheduleFluidTick(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/fluid/Fluid;ILnet/minecraft/world/tick/TickPriority;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private int legacyOfTheLucii$scaleScheduledFluidDelay(
            int delay,
            BlockPos pos,
            Fluid fluid,
            int originalDelay,
            TickPriority priority
    ) {
        Object self = this;
        if (self instanceof ServerWorld world) {
            return ArdynTimeSlowAbility.scaleScheduledDelay(world, pos, delay);
        }
        return delay;
    }
}
