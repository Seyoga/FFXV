package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import ru.siyoga.legacyofthelucii.masquerade.ai.TrackTargetGoalAccess;

@Mixin(TrackTargetGoal.class)
public interface TrackTargetGoalAccessor extends TrackTargetGoalAccess {
    @Accessor("mob")
    MobEntity legacyofthelucii$getMob();
}
