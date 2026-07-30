package ru.siyoga.legacyofthelucii.mixin;

import com.mojang.authlib.GameProfile;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.siyoga.legacyofthelucii.client.animation.LuciiAnimatedPlayer;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin implements LuciiAnimatedPlayer {
    @Unique
    private final ModifierLayer<IAnimation> legacyOfTheLucii$animationLayer = new ModifierLayer<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void legacyOfTheLucii$registerAnimationLayer(ClientWorld world, GameProfile profile, CallbackInfo ci) {
        PlayerAnimationAccess.getPlayerAnimLayer((AbstractClientPlayerEntity) (Object) this)
                .addAnimLayer(1200, legacyOfTheLucii$animationLayer);
    }

    @Override
    public ModifierLayer<IAnimation> legacyOfTheLucii$getAnimationLayer() {
        return legacyOfTheLucii$animationLayer;
    }
}
