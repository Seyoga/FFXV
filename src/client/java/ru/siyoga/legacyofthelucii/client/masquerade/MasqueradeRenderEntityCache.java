package ru.siyoga.legacyofthelucii.client.masquerade;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.masquerade.MasqueradeMorph;
import ru.siyoga.legacyofthelucii.mixin.LimbAnimatorAccessor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MasqueradeRenderEntityCache {
    private static final Map<String, LivingEntity> ENTITIES = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static ClientWorld cachedWorld;

    private MasqueradeRenderEntityCache() {
    }

    public static LivingEntity get(MasqueradeMorph morph, PlayerEntity source) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || source == null) {
            return null;
        }
        if (cachedWorld != client.world) {
            clear();
            cachedWorld = client.world;
        }
        if (FAILED.contains(morph.key())) {
            return null;
        }

        LivingEntity entity = ENTITIES.get(morph.key());
        if (entity == null) {
            entity = create(morph, client.world);
            if (entity == null) {
                FAILED.add(morph.key());
                return null;
            }
            ENTITIES.put(morph.key(), entity);
        }
        syncVisualState(entity, source);
        return entity;
    }

    public static void clear() {
        ENTITIES.clear();
        FAILED.clear();
        cachedWorld = null;
    }

    private static LivingEntity create(MasqueradeMorph morph, ClientWorld world) {
        try {
            if (morph.kind() == MasqueradeMorph.Kind.PLAYER) {
                return new MasqueradePlayerRenderEntity(world, morph.playerProfile());
            }
            Entity entity = Registries.ENTITY_TYPE.get(morph.entityTypeId()).create(world);
            if (entity instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
        } catch (RuntimeException exception) {
            LegacyOfTheLucii.LOGGER.warn("Could not create Masquerade render entity {}", morph.key(), exception);
        }
        return null;
    }

    private static void syncVisualState(LivingEntity target, PlayerEntity source) {
        target.setPosition(source.getX(), source.getY(), source.getZ());
        target.prevX = source.prevX;
        target.prevY = source.prevY;
        target.prevZ = source.prevZ;
        target.lastRenderX = source.lastRenderX;
        target.lastRenderY = source.lastRenderY;
        target.lastRenderZ = source.lastRenderZ;
        target.setYaw(source.getYaw());
        target.prevYaw = source.prevYaw;
        target.setPitch(source.getPitch());
        target.prevPitch = source.prevPitch;
        target.bodyYaw = source.bodyYaw;
        target.prevBodyYaw = source.prevBodyYaw;
        target.headYaw = source.headYaw;
        target.prevHeadYaw = source.prevHeadYaw;
        target.age = source.age;
        target.hurtTime = source.hurtTime;
        target.maxHurtTime = source.maxHurtTime;
        target.handSwinging = source.handSwinging;
        target.handSwingTicks = source.handSwingTicks;
        target.handSwingProgress = source.handSwingProgress;
        target.lastHandSwingProgress = source.lastHandSwingProgress;
        target.setPose(source.getPose());
        target.setSneaking(source.isSneaking());
        target.setSprinting(source.isSprinting());
        target.setSwimming(source.isSwimming());
        target.setInvisible(source.isInvisible());
        target.setGlowing(source.isGlowing());
        target.setOnFire(source.isOnFire());

        LimbAnimatorAccessor sourceLimb = (LimbAnimatorAccessor) (Object) source.limbAnimator;
        LimbAnimatorAccessor targetLimb = (LimbAnimatorAccessor) (Object) target.limbAnimator;
        targetLimb.legacyOfTheLucii$setPreviousSpeed(sourceLimb.legacyOfTheLucii$getPreviousSpeed());
        targetLimb.legacyOfTheLucii$setSpeed(sourceLimb.legacyOfTheLucii$getSpeed());
        targetLimb.legacyOfTheLucii$setPosition(sourceLimb.legacyOfTheLucii$getPosition());
    }
}
