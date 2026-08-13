package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynShadowStepAbility;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
    @Inject(
            method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void legacyOfTheLucii$allowShadowStepPhasing(
            BlockView world,
            BlockPos pos,
            ShapeContext context,
            CallbackInfoReturnable<VoxelShape> cir
    ) {
        if (!(context instanceof EntityShapeContext entityContext)) {
            return;
        }

        Entity entity = entityContext.getEntity();
        if (!(entity instanceof PlayerEntity player)
                || !ArdynShadowStepAbility.isManualStepActive(player.getUuid())) {
            return;
        }

        BlockState state = (BlockState) (Object) this;
        if (ArdynShadowStepAbility.canPhaseThrough(state)) {
            cir.setReturnValue(VoxelShapes.empty());
        }
    }
}
