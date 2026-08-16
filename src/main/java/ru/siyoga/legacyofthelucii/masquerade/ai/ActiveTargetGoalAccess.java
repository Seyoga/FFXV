package ru.siyoga.legacyofthelucii.masquerade.ai;

import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.LivingEntity;

public interface ActiveTargetGoalAccess {
    Class<?> legacyofthelucii$getTargetClass();

    TargetPredicate legacyofthelucii$getTargetPredicate();

    LivingEntity legacyofthelucii$getTargetEntity();

    void legacyofthelucii$setTargetEntity(LivingEntity targetEntity);

    void legacyofthelucii$findClosestTarget();
}
