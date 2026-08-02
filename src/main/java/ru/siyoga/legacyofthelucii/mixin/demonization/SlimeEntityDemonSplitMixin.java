package ru.siyoga.legacyofthelucii.mixin.demonization;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.siyoga.legacyofthelucii.effect.Demonization;

@Mixin(SlimeEntity.class)
public abstract class SlimeEntityDemonSplitMixin {
    @Redirect(
            method = "remove",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"
            )
    )
    private boolean legacyofthelucii$copyDemonizationToSplitSlime(
            World world,
            Entity child
    ) {
        SlimeEntity parent = (SlimeEntity) (Object) this;
        if (child instanceof MobEntity childMob) {
            Demonization.copyDemonization(parent, childMob);
        }
        return world.spawnEntity(child);
    }
}
