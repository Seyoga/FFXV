package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.siyoga.legacyofthelucii.client.sniper.ArdynSniperClient;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void legacyOfTheLucii$hideHandInSniperMode(
            MatrixStack matrices,
            Camera camera,
            float tickDelta,
            CallbackInfo ci
    ) {
        if (ArdynSniperClient.isActive()) {
            ci.cancel();
        }
    }
}
