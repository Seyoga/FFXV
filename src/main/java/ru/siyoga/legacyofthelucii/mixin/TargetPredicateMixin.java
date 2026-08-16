package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.entity.ai.TargetPredicate;
import ru.siyoga.legacyofthelucii.masquerade.ai.TargetPredicateAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TargetPredicate.class)
public interface TargetPredicateMixin extends TargetPredicateAccess {
    @Override
    @Accessor("attackable")
    boolean legacyofthelucii$isAttackable();

    @Override
    @Accessor("baseMaxDistance")
    double legacyofthelucii$getBaseMaxDistance();

    @Override
    @Accessor("respectsVisibility")
    boolean legacyofthelucii$respectsVisibility();
}
