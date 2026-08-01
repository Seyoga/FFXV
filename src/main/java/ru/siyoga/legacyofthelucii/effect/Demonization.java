package ru.siyoga.legacyofthelucii.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;

public final class Demonization {
    private static final int PERMANENT_DURATION_TICKS = Integer.MAX_VALUE;

    private Demonization() {
    }

    /**
     * Applies the synchronized demonization marker to a living mob. The effect deliberately
     * has no particles, HUD icon, attributes, or periodic gameplay behavior.
     */
    public static boolean apply(LivingEntity target, int durationTicks, int strength) {
        if (!(target instanceof MobEntity mob) || !mob.isAlive() || mob.isRemoved()) {
            return false;
        }

        int duration = Math.max(1, durationTicks);
        int amplifier = Math.max(0, strength);
        return mob.addStatusEffect(new StatusEffectInstance(
                LegacyStatusEffects.DEMONIZATION,
                duration,
                amplifier,
                false,
                false,
                false
        ));
    }

    public static boolean applyPermanent(LivingEntity target, int strength) {
        return apply(target, PERMANENT_DURATION_TICKS, strength);
    }

    public static boolean forceApplyPermanentFullHealth(LivingEntity target, int strength) {
        if (!(target instanceof MobEntity mob) || !mob.isAlive() || mob.isRemoved()) {
            return false;
        }

        mob.setHealth(mob.getMaxHealth());
        return applyPermanent(mob, strength);
    }

    public static boolean damageOrDemonizeOnLethalHit(ServerPlayerEntity attacker, LivingEntity target, float damage, int strength) {
        if (isEligibleNewDemon(target) && target.getHealth() <= damage) {
            MobEntity mob = (MobEntity) target;
            return forceApplyPermanentFullHealth(mob, strength);
        }

        return target.damage(attacker.getDamageSources().playerAttack(attacker), damage);
    }

    public static boolean isDemonized(MobEntity mob) {
        return mob.isAlive() && !mob.isRemoved() && mob.hasStatusEffect(LegacyStatusEffects.DEMONIZATION);
    }

    private static boolean isEligibleNewDemon(LivingEntity target) {
        return target instanceof MobEntity mob
                && mob.isAlive()
                && !mob.isRemoved()
                && !isDemonized(mob);
    }
}
