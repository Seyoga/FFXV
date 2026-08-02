package ru.siyoga.legacyofthelucii.demon.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.util.math.Box;
import ru.siyoga.legacyofthelucii.demon.DemonFaction;
import ru.siyoga.legacyofthelucii.demon.headgrab.DemonHeadgrabSystem;
import ru.siyoga.legacyofthelucii.effect.Demonization;

public final class DemonTargetGoal
        extends ActiveTargetGoal<LivingEntity> {
    private static final double HORIZONTAL_RANGE = 24.0D;
    private static final double VERTICAL_RANGE = 12.0D;

    private final MobEntity demon;

    public DemonTargetGoal(MobEntity demon) {
        super(
                demon,
                LivingEntity.class,
                5,
                true,
                false,
                target -> DemonFaction.canAttack(demon, target)
        );

        this.demon = demon;
    }

    @Override
    public boolean canStart() {
        return !isHeadgrabber()
                && Demonization.isDemonized(demon)
                && super.canStart();
    }

    @Override
    public boolean shouldContinue() {
        LivingEntity target = demon.getTarget();

        return !isHeadgrabber()
                && Demonization.isDemonized(demon)
                && target != null
                && DemonFaction.canAttack(demon, target)
                && super.shouldContinue();
    }

    @Override
    protected Box getSearchBox(double distance) {
        return demon.getBoundingBox().expand(
                HORIZONTAL_RANGE,
                VERTICAL_RANGE,
                HORIZONTAL_RANGE
        );
    }

    private boolean isHeadgrabber() {
        return demon instanceof SlimeEntity slime
                && DemonHeadgrabSystem.isDemonHeadgrabber(slime);
    }
}
