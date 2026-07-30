package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.siyoga.legacyofthelucii.client.royalarms.ardyn.ArdynShadowStepClient;
import ru.siyoga.legacyofthelucii.client.royalarms.warp.RoyalArmsWarpTrailClient;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), cancellable = true)
    private void legacyofthelucii$hideWarpingPlayer(
            AbstractClientPlayerEntity player,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        if (RoyalArmsWarpTrailClient.shouldHidePlayer(player.getUuid()) || ArdynShadowStepClient.shouldHidePlayer(player.getUuid())) {
            ci.cancel();
        }
    }

}
