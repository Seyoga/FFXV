package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynOverkillAbility;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float legacyOfTheLucii$preventArdynDeath(float health) {
        if (health > 0.0F) {
            return health;
        }

        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayerEntity player && ArdynOverkillAbility.tryEnterOverkill(player)) {
            return player.getMaxHealth();
        }

        return health;
    }
}
