package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.siyoga.legacyofthelucii.client.timeslow.ArdynTimeSlowClient;

@Mixin(LivingEntity.class)
public abstract class LivingEntityTimeSlowClientMixin {
    @Inject(method = "jump", at = @At("TAIL"))
    private void legacyOfTheLucii$slowLocalArdynConcentrationJump(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if ((Object) this == client.player && ArdynTimeSlowClient.isLocalPlayerSlowed(client)) {
            ArdynTimeSlowClient.onLocalPlayerJump(client);
        }
    }
}
