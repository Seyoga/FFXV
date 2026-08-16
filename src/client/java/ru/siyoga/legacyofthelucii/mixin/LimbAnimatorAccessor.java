package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LimbAnimator.class)
public interface LimbAnimatorAccessor {
    @Accessor("prevSpeed")
    float legacyOfTheLucii$getPreviousSpeed();

    @Accessor("prevSpeed")
    void legacyOfTheLucii$setPreviousSpeed(float speed);

    @Accessor("speed")
    float legacyOfTheLucii$getSpeed();

    @Accessor("speed")
    void legacyOfTheLucii$setSpeed(float speed);

    @Accessor("pos")
    float legacyOfTheLucii$getPosition();

    @Accessor("pos")
    void legacyOfTheLucii$setPosition(float position);
}
