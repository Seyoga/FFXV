package ru.siyoga.legacyofthelucii.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.demon.DemonizedMobData;
import ru.siyoga.legacyofthelucii.masquerade.MasqueradeManager;
import ru.siyoga.legacyofthelucii.network.DemonizationNetwork;

import java.util.UUID;

public final class Demonization {
    private static final int PERMANENT_DURATION_TICKS = Integer.MAX_VALUE;

    private Demonization() {
    }

    public static boolean apply(
            LivingEntity target,
            int durationTicks,
            int strength
    ) {
        return apply(null, target, durationTicks, strength);
    }

    public static boolean apply(
            @Nullable LivingEntity demonizer,
            LivingEntity target,
            int durationTicks,
            int strength
    ) {
        if (!(target instanceof MobEntity mob)
                || !mob.isAlive()
                || mob.isRemoved()) {
            return false;
        }

        if (demonizer != null) {
            assignDemonizer(mob, demonizer);
        }

        int duration = Math.max(1, durationTicks);
        int amplifier = Math.max(0, strength);

        boolean added = mob.addStatusEffect(new StatusEffectInstance(
                LegacyStatusEffects.DEMONIZATION,
                duration,
                amplifier,
                false,
                false,
                false
        ));

        boolean demonized = mob.hasStatusEffect(
                LegacyStatusEffects.DEMONIZATION
        );

        if (demonized) {
            // A demon is a persistent faction member and should not vanish via
            // normal hostile-mob despawning.
            mob.setPersistent();

            if (mob.getWorld() instanceof ServerWorld world) {
                DemonizationNetwork.broadcast(world, mob, true);
            }

            if (demonizer instanceof ServerPlayerEntity player) {
                MasqueradeManager.unlockMorph(player, mob);
            }
        }

        return added || demonized;
    }

    public static boolean applyPermanent(
            LivingEntity target,
            int strength
    ) {
        return apply(
                null,
                target,
                PERMANENT_DURATION_TICKS,
                strength
        );
    }

    public static boolean applyPermanent(
            LivingEntity demonizer,
            LivingEntity target,
            int strength
    ) {
        return apply(
                demonizer,
                target,
                PERMANENT_DURATION_TICKS,
                strength
        );
    }

    public static boolean forceApplyPermanentFullHealth(
            LivingEntity target,
            int strength
    ) {
        return forceApplyPermanentFullHealth(
                null,
                target,
                strength
        );
    }

    public static boolean forceApplyPermanentFullHealth(
            @Nullable LivingEntity demonizer,
            LivingEntity target,
            int strength
    ) {
        if (!(target instanceof MobEntity mob)
                || !mob.isAlive()
                || mob.isRemoved()) {
            return false;
        }

        mob.setHealth(mob.getMaxHealth());
        return applyPermanentInternal(demonizer, mob, strength);
    }

    public static boolean damageOrDemonizeOnLethalHit(
            ServerPlayerEntity attacker,
            LivingEntity target,
            float damage,
            int strength
    ) {
        if (isEligibleNewDemon(target)
                && target.getHealth() <= damage) {
            return forceApplyPermanentFullHealth(
                    attacker,
                    target,
                    strength
            );
        }

        return target.damage(
                attacker.getDamageSources().playerAttack(attacker),
                damage
        );
    }

    public static boolean isDemonized(MobEntity mob) {
        return mob.isAlive()
                && !mob.isRemoved()
                && mob.hasStatusEffect(
                        LegacyStatusEffects.DEMONIZATION
                );
    }

    public static void assignDemonizer(
            MobEntity mob,
            LivingEntity demonizer
    ) {
        ((DemonizedMobData) mob)
                .legacyofthelucii$setDemonizerUuid(
                        demonizer.getUuid()
                );

        LegacyOfTheLucii.LOGGER.info(
                "Demonization: entity uuid={} assigned to demonizer uuid={}",
                mob.getUuid(),
                demonizer.getUuid()
        );
    }

    public static @Nullable UUID getDemonizerUuid(MobEntity mob) {
        return ((DemonizedMobData) mob)
                .legacyofthelucii$getDemonizerUuid();
    }

    public static boolean copyDemonization(
            MobEntity source,
            MobEntity target
    ) {
        // SlimeEntity creates its smaller children from remove(). At that point
        // the parent is already dead, so isDemonized(source) intentionally
        // returns false even though the status effect is still present.
        if (!source.hasStatusEffect(LegacyStatusEffects.DEMONIZATION)
                || !target.isAlive()
                || target.isRemoved()) {
            return false;
        }

        ((DemonizedMobData) target)
                .legacyofthelucii$setDemonizerUuid(
                        getDemonizerUuid(source)
                );
        return applyPermanentInternal(null, target, 0);
    }

    public static boolean wasDemonizedBy(
            MobEntity mob,
            LivingEntity entity
    ) {
        UUID demonizerUuid = getDemonizerUuid(mob);
        return demonizerUuid != null
                && demonizerUuid.equals(entity.getUuid());
    }

    private static boolean applyPermanentInternal(
            @Nullable LivingEntity demonizer,
            MobEntity mob,
            int strength
    ) {
        return apply(
                demonizer,
                mob,
                PERMANENT_DURATION_TICKS,
                strength
        );
    }

    private static boolean isEligibleNewDemon(
            LivingEntity target
    ) {
        return target instanceof MobEntity mob
                && mob.isAlive()
                && !mob.isRemoved()
                && !isDemonized(mob);
    }
}
