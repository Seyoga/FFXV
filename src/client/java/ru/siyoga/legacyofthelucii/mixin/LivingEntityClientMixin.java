package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.siyoga.legacyofthelucii.client.state.ArdynOverkillClientState;

/**
 * Replaces only the HUD heart texture selected by vanilla.
 *
 * <p>No Wither status effect is added to the player. The mixin targets
 * InGameHud.HeartType#fromPlayerState and directly returns its built-in
 * WITHERED texture variant while the synchronized Overkill flag is active.</p>
 *
 * <p>The class name is kept for compatibility with the existing mixin json.</p>
 */
@Mixin(targets = "net.minecraft.client.gui.hud.InGameHud$HeartType")
public abstract class LivingEntityClientMixin {
    @Inject(method = "fromPlayerState", at = @At("RETURN"), cancellable = true)
    private static void legacyOfTheLucii$useWitheredHeartTextureDuringOverkill(
            PlayerEntity player,
            CallbackInfoReturnable<Object> cir
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null
                || player != client.player
                || !ArdynOverkillClientState.active(client.player.getUuid())) {
            return;
        }

        Object currentHeartType = cir.getReturnValue();
        if (!(currentHeartType instanceof Enum<?> currentEnum)) {
            return;
        }

        if ("WITHERED".equals(currentEnum.name())) {
            return;
        }

        try {
            cir.setReturnValue(findWitheredHeartType(currentEnum));
        } catch (IllegalArgumentException ignored) {
            // Fail safely if another Minecraft version changes the vanilla enum.
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object findWitheredHeartType(Enum<?> currentHeartType) {
        Class enumClass = currentHeartType.getDeclaringClass();
        return Enum.valueOf(enumClass, "WITHERED");
    }
}
