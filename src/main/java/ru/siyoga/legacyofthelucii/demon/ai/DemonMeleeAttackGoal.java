package ru.siyoga.legacyofthelucii.demon.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Hand;
import ru.siyoga.legacyofthelucii.demon.DemonFaction;
import ru.siyoga.legacyofthelucii.effect.Demonization;

import java.util.EnumSet;

public final class DemonMeleeAttackGoal extends Goal {
    private static final double MOVEMENT_SPEED = 1.15D;
    private static final int ATTACK_INTERVAL_TICKS = 20;
    private static final float MINIMUM_ATTACK_DAMAGE = 3.0F;

    private final MobEntity demon;
    private int attackCooldown;
    private int pathUpdateCooldown;

    public DemonMeleeAttackGoal(MobEntity demon) {
        this.demon = demon;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return hasValidTarget();
    }

    @Override
    public boolean shouldContinue() {
        return hasValidTarget();
    }

    @Override
    public void start() {
        attackCooldown = 0;
        pathUpdateCooldown = 0;
        demon.setAttacking(true);
    }

    @Override
    public void stop() {
        demon.setAttacking(false);
        demon.getNavigation().stop();

        LivingEntity target = demon.getTarget();
        if (target != null && !DemonFaction.canAttack(demon, target)) {
            demon.setTarget(null);
        }
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = demon.getTarget();
        if (target == null || !DemonFaction.canAttack(demon, target)) {
            demon.setTarget(null);
            return;
        }

        if (attackCooldown > 0) {
            attackCooldown--;
        }

        demon.getLookControl().lookAt(target, 30.0F, 30.0F);

        if (--pathUpdateCooldown <= 0) {
            pathUpdateCooldown = 5 + demon.getRandom().nextInt(5);
            demon.getNavigation().startMovingTo(target, MOVEMENT_SPEED);
        }

        if (demon.isInAttackRange(target) && attackCooldown <= 0) {
            attackCooldown = ATTACK_INTERVAL_TICKS;
            demon.swingHand(Hand.MAIN_HAND);
            target.damage(
                    demon.getDamageSources().mobAttack(demon),
                    getAttackDamage()
            );
        }
    }

    private boolean hasValidTarget() {
        if (!Demonization.isDemonized(demon)
                || !demon.isAlive()
                || demon.isRemoved()
                || demon.isAiDisabled()) {
            return false;
        }

        LivingEntity target = demon.getTarget();
        return target != null && DemonFaction.canAttack(demon, target);
    }

    private float getAttackDamage() {
        EntityAttributeInstance attackDamage =
                demon.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);

        if (attackDamage == null) {
            return MINIMUM_ATTACK_DAMAGE;
        }

        return Math.max(
                MINIMUM_ATTACK_DAMAGE,
                (float) attackDamage.getValue()
        );
    }
}
