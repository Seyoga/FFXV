package ru.siyoga.legacyofthelucii.client.mixin;

import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.siyoga.legacyofthelucii.client.state.DemonizationClientState;
import ru.siyoga.legacyofthelucii.effect.Demonization;
import ru.siyoga.legacyofthelucii.particle.LegacyParticles;

@Mixin(SlimeEntity.class)
public abstract class DemonizedSlimeParticleMixin {
    @Inject(method = "getParticles", at = @At("HEAD"), cancellable = true)
    private void legacyofthelucii$useDemonizedSlimeParticles(
            CallbackInfoReturnable<ParticleEffect> cir
    ) {
        SlimeEntity slime = (SlimeEntity) (Object) this;
        if (DemonizationClientState.isDemonized(slime.getUuid())
                || Demonization.isDemonized(slime)) {
            cir.setReturnValue(LegacyParticles.DEMONIZED_SLIME_BALL);
        }
    }
}
