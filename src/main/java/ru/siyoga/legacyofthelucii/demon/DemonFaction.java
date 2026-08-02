package ru.siyoga.legacyofthelucii.demon;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.effect.Demonization;

import java.util.UUID;

public final class DemonFaction {
    private static boolean registered;

    private DemonFaction() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
            Entity attacker = source.getAttacker();
            if (!(attacker instanceof MobEntity demon)
                    || !Demonization.isDemonized(demon)) {
                return true;
            }

            // This also blocks explosion/projectile friendly fire when Minecraft
            // reports the demon as the attacker.
            return canAttack(demon, victim);
        });

        LegacyOfTheLucii.LOGGER.info(
                "Demon faction: targeting and friendly-fire rules registered."
        );
    }

    public static boolean canAttack(MobEntity demon, LivingEntity target) {
        if (!Demonization.isDemonized(demon)
                || target == demon
                || !target.isAlive()
                || target.isRemoved()
                || target instanceof ArmorStandEntity) {
            return false;
        }

        UUID demonizerUuid = Demonization.getDemonizerUuid(demon);
        if (demonizerUuid != null && demonizerUuid.equals(target.getUuid())) {
            return false;
        }

        if (target instanceof MobEntity targetMob
                && Demonization.isDemonized(targetMob)) {
            return false;
        }

        if (target instanceof PlayerEntity player
                && (player.isCreative() || player.isSpectator())) {
            return false;
        }

        return true;
    }
}
