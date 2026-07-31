package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.siyoga.legacyofthelucii.client.state.ClientLuciiState;

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
    @Inject(
            method = "fromPlayerState(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/client/gui/hud/InGameHud$HeartType;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void legacyOfTheLucii$useWitheredHeartTextureDuringOverkill(
            PlayerEntity player,
            CallbackInfoReturnable<Object> cir
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null
                || !player.getUuid().equals(client.player.getUuid())
                || !ClientLuciiState.ardynOverkillActive()) {
            return;
        }

        Object currentHeartType = cir.getReturnValue();
        if (!(currentHeartType instanceof Enum<?> currentEnum)) {
            return;
        }

        Object withered = findWitheredHeartType(currentEnum);
        if (withered != null && currentHeartType != withered) {
            cir.setReturnValue(withered);
        }
    }

    private static Object findWitheredHeartType(Enum<?> currentHeartType) {
        Object[] constants = currentHeartType.getDeclaringClass().getEnumConstants();
        // In Minecraft 1.20.1 HeartType is declared as:
        // CONTAINER, NORMAL, POISONED, WITHERED, ABSORBING, FROZEN.
        // Using ordinal 3 is remap-safe; Enum.valueOf("WITHERED") is not, because
        // enum constant names are obfuscated outside the named development runtime.
        return constants != null && constants.length > 3 ? constants[3] : null;
    }
}
