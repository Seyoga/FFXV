package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityTimeSlowRenderAccessor {
    @Accessor("lastRenderX")
    double legacyOfTheLucii$getLastRenderX();

    @Accessor("lastRenderY")
    double legacyOfTheLucii$getLastRenderY();

    @Accessor("lastRenderZ")
    double legacyOfTheLucii$getLastRenderZ();
}
