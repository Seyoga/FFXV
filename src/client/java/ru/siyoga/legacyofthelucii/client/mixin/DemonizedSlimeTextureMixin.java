package ru.siyoga.legacyofthelucii.client.mixin;

import net.minecraft.client.render.entity.SlimeEntityRenderer;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.state.DemonizationClientState;
import ru.siyoga.legacyofthelucii.effect.Demonization;

@Mixin(SlimeEntityRenderer.class)
public abstract class DemonizedSlimeTextureMixin {
    private static final Identifier DEMONIZED_SLIME_TEXTURE = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "textures/entity/demonized_slime.png"
    );

    @Inject(method = "getTexture(Lnet/minecraft/entity/mob/SlimeEntity;)Lnet/minecraft/util/Identifier;", at = @At("HEAD"), cancellable = true)
    private void legacyofthelucii$useDemonizedSlimeTexture(
            SlimeEntity slime,
            CallbackInfoReturnable<Identifier> cir
    ) {
        if (slime.isRemoved()) {
            return;
        }

        if (DemonizationClientState.isDemonized(slime.getUuid()) || Demonization.isDemonized(slime)) {
            cir.setReturnValue(DEMONIZED_SLIME_TEXTURE);
        }
    }
}
