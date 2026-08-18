package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.siyoga.legacyofthelucii.client.hud.LuciiHudOverlay;
import ru.siyoga.legacyofthelucii.client.sniper.ArdynSniperClient;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void legacyOfTheLucii$renderSniperOnly(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (!ArdynSniperClient.isActive()) {
            return;
        }

        ArdynSniperClient.renderScope(context, tickDelta);
        ci.cancel();
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void legacyOfTheLucii$replaceExperienceBarWithMana(DrawContext context, int x, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!LuciiHudOverlay.shouldReplaceExperienceBar(client)) {
            return;
        }

        LuciiHudOverlay.renderExperienceBarMana(context, client, x);
        ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void legacyOfTheLucii$hideCrosshairInSniperMode(DrawContext context, CallbackInfo ci) {
        if (ArdynSniperClient.isActive()) {
            ci.cancel();
        }
    }
}
