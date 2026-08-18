package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.siyoga.legacyofthelucii.client.timeslow.ArdynTimeSlowClient;

@Mixin(ClientWorld.class)
public abstract class ClientWorldTimeSlowMixin {
    @Inject(
            method = "tickEntity(Lnet/minecraft/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void legacyOfTheLucii$slowRemoteEntityTick(Entity entity, CallbackInfo ci) {
        if (ArdynTimeSlowClient.shouldSkipEntityTick(MinecraftClient.getInstance(), entity)) {
            ci.cancel();
        }
    }
}
