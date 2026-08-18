package ru.siyoga.legacyofthelucii.client.sniper;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.animation.LuciiAnimatedPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ArdynSniperAnimations {
    private static final Identifier EQUIP = new Identifier(LegacyOfTheLucii.MOD_ID, "cerberus_equip");
    private static final Identifier HOLD = new Identifier(LegacyOfTheLucii.MOD_ID, "cerberus_hold");
    private static final Identifier SHOOT = new Identifier(LegacyOfTheLucii.MOD_ID, "cerberus_shoot");
    private static final Identifier UNEQUIP = new Identifier(LegacyOfTheLucii.MOD_ID, "cerberus_unequip");

    private static final float EQUIP_PURPLE_BUILDUP_START = 3.4F;
    private static final float EQUIP_APPEAR_TIME = 5.8F;
    private static final float EQUIP_REVEAL_TICKS = 3.2F;
    private static final float EQUIP_PURPLE_FADE_TICKS = 5.0F;
    private static final float UNEQUIP_DISAPPEAR_TIME = 4.2F;

    private static final Map<UUID, VisualState> STATES = new HashMap<>();

    private ArdynSniperAnimations() {
    }

    public static void playEquip(UUID ownerUuid) {
        if (play(ownerUuid, EQUIP, Playback.HOLD_LAST_FRAME)) {
            setPhase(ownerUuid, Phase.EQUIP);
        }
    }

    public static void playHold(UUID ownerUuid) {
        if (play(ownerUuid, HOLD, Playback.NORMAL)) {
            setPhase(ownerUuid, Phase.HOLD);
        }
    }

    public static void playShoot(UUID ownerUuid) {
        if (play(ownerUuid, SHOOT, Playback.ADDITIVE_HEAD_HOLD_LAST_FRAME)) {
            setPhase(ownerUuid, Phase.SHOOT);
        }
    }

    public static void playUnequip(UUID ownerUuid) {
        VisualState existing = STATES.get(ownerUuid);
        if (existing != null && existing.phase == Phase.UNEQUIP) {
            existing.predicted = false;
            return;
        }
        if (play(ownerUuid, UNEQUIP, Playback.ADDITIVE_HEAD_HOLD_LAST_FRAME)) {
            setPhase(ownerUuid, Phase.UNEQUIP);
        }
    }

    public static void tick(MinecraftClient client) {
        if (client.isPaused() || client.world == null) {
            return;
        }

        for (VisualState state : STATES.values()) {
            state.ageTicks++;
        }
    }

    public static boolean canLocalShoot(UUID playerUuid) {
        VisualState state = STATES.get(playerUuid);
        return state != null && state.phase == Phase.HOLD;
    }

    public static boolean canLocalUnequip(UUID playerUuid) {
        VisualState state = STATES.get(playerUuid);
        return state != null && state.phase == Phase.HOLD;
    }

    public static void predictLocalUnequip(UUID playerUuid) {
        VisualState state = STATES.get(playerUuid);
        if (state == null || state.phase != Phase.HOLD) {
            return;
        }
        if (play(playerUuid, UNEQUIP, Playback.ADDITIVE_HEAD_HOLD_LAST_FRAME)) {
            VisualState predicted = new VisualState(Phase.UNEQUIP);
            predicted.predicted = true;
            STATES.put(playerUuid, predicted);
        }
    }

    public static CerberusVisual getCerberusVisual(UUID playerUuid, float tickDelta) {
        VisualState state = STATES.get(playerUuid);
        if (state == null) {
            return null;
        }

        float time = state.ageTicks + tickDelta;
        float pulse = 0.5F + 0.5F * (float) Math.sin(time * 0.72F);

        if (state.phase == Phase.EQUIP) {
            if (time < EQUIP_PURPLE_BUILDUP_START) {
                return null;
            }

            float shimmer = 0.5F + 0.5F * (float) Math.sin(time * 2.35F);
            if (time < EQUIP_APPEAR_TIME) {
                float buildup = clamp01(
                        (time - EQUIP_PURPLE_BUILDUP_START)
                                / (EQUIP_APPEAR_TIME - EQUIP_PURPLE_BUILDUP_START)
                );
                float charge = buildup * buildup;
                return new CerberusVisual(
                        0.0F,
                        clamp01(0.05F + charge * 0.16F),
                        clamp01(0.03F + charge * 0.11F),
                        clamp01(0.18F + charge * 0.48F + shimmer * 0.16F),
                        clamp01(0.24F + charge * 0.58F + shimmer * 0.18F),
                        clamp01(0.20F + charge * 0.52F + shimmer * 0.20F)
                );
            }

            float reveal = clamp01((time - EQUIP_APPEAR_TIME) / EQUIP_REVEAL_TICKS);
            float flash = 1.0F - reveal;
            float purpleFade = 1.0F - clamp01((time - EQUIP_APPEAR_TIME) / EQUIP_PURPLE_FADE_TICKS);
            return new CerberusVisual(
                    reveal,
                    clamp01(0.32F + pulse * 0.10F + flash * 0.48F),
                    clamp01(0.13F + pulse * 0.06F + flash * 0.36F),
                    clamp01(purpleFade * (0.50F + flash * 0.34F + shimmer * 0.16F)),
                    clamp01(purpleFade * (0.62F + flash * 0.28F + shimmer * 0.18F)),
                    clamp01(purpleFade * (0.50F + flash * 0.30F + shimmer * 0.20F))
            );
        }

        if (state.phase == Phase.UNEQUIP) {
            float disappear = clamp01(time / UNEQUIP_DISAPPEAR_TIME);
            if (disappear >= 1.0F) {
                return null;
            }
            float remaining = 1.0F - disappear;
            float flash = (float) Math.sin(disappear * Math.PI);
            float shimmer = 0.5F + 0.5F * (float) Math.sin(time * 2.85F + 0.8F);
            float purpleBurst = clamp01(0.28F + flash * 0.72F);
            return new CerberusVisual(
                    remaining,
                    clamp01(remaining * 0.34F + pulse * 0.08F + flash * 0.52F),
                    clamp01(remaining * 0.13F + pulse * 0.05F + flash * 0.40F),
                    clamp01(purpleBurst * (0.54F + shimmer * 0.18F)),
                    clamp01(purpleBurst * (0.68F + shimmer * 0.20F)),
                    clamp01(purpleBurst * (0.56F + shimmer * 0.24F))
            );
        }

        return new CerberusVisual(
                1.0F,
                0.30F + pulse * 0.12F,
                0.10F + pulse * 0.07F,
                0.0F,
                0.0F,
                0.0F
        );
    }

    public static boolean shouldRenderCerberus(UUID playerUuid) {
        return getCerberusVisual(playerUuid, 0.0F) != null;
    }

    public static CerberusPose getCerberusPose(
            AbstractClientPlayerEntity player,
            float tickDelta
    ) {
        if (!(player instanceof LuciiAnimatedPlayer animatedPlayer)) {
            return null;
        }

        var layer = animatedPlayer.legacyOfTheLucii$getAnimationLayer();
        if (!layer.isActive()) {
            return null;
        }

        layer.setupAnim(tickDelta);
        Vec3f zero = new Vec3f(0.0F, 0.0F, 0.0F);
        Vec3f position = layer.get3DTransform(
                ArdynCerberusModel.BONE_NAME,
                TransformType.POSITION,
                tickDelta,
                zero
        );
        Vec3f rotation = layer.get3DTransform(
                ArdynCerberusModel.BONE_NAME,
                TransformType.ROTATION,
                tickDelta,
                zero
        );

        return new CerberusPose(
                position.getX(),
                position.getY(),
                position.getZ(),
                rotation.getX(),
                rotation.getY(),
                rotation.getZ()
        );
    }

    public static void onLocalState(UUID playerUuid, boolean active) {
        if (!active) {
            VisualState state = STATES.get(playerUuid);
            if (state == null || state.phase != Phase.UNEQUIP) {
                STATES.remove(playerUuid);
            }
        }
    }

    public static void clear(UUID ownerUuid) {
        STATES.remove(ownerUuid);
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = findPlayer(client, ownerUuid);
        if (player instanceof AbstractClientPlayerEntity clientPlayer
                && clientPlayer instanceof LuciiAnimatedPlayer animatedPlayer) {
            animatedPlayer.legacyOfTheLucii$getAnimationLayer().setAnimation(null);
        }
    }

    public static void clearAll() {
        STATES.clear();
    }

    private static void setPhase(UUID ownerUuid, Phase phase) {
        VisualState existing = STATES.get(ownerUuid);
        if (phase == Phase.UNEQUIP && existing != null && existing.phase == Phase.UNEQUIP) {
            existing.predicted = false;
            return;
        }
        STATES.put(ownerUuid, new VisualState(phase));
    }

    private static boolean play(UUID ownerUuid, Identifier animationId, Playback playback) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = findPlayer(client, ownerUuid);
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)
                || !(clientPlayer instanceof LuciiAnimatedPlayer animatedPlayer)) {
            return false;
        }

        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(animationId);
        if (animation == null) {
            LegacyOfTheLucii.LOGGER.warn("[Sniper/ANIM] Missing player animation {}.", animationId);
            return false;
        }

        if (playback == Playback.NORMAL) {
            animatedPlayer.legacyOfTheLucii$getAnimationLayer()
                    .setAnimation(new KeyframeAnimationPlayer(animation));
            return true;
        }

        KeyframeAnimation runtimeAnimation = withoutLoop(animation);
        if (playback == Playback.ADDITIVE_HEAD_HOLD_LAST_FRAME) {
            animatedPlayer.legacyOfTheLucii$getAnimationLayer()
                    .setAnimation(new AdditiveHeadHoldLastFrameKeyframeAnimationPlayer(runtimeAnimation));
        } else {
            animatedPlayer.legacyOfTheLucii$getAnimationLayer()
                    .setAnimation(new HoldLastFrameKeyframeAnimationPlayer(runtimeAnimation));
        }
        return true;
    }

    private static KeyframeAnimation withoutLoop(KeyframeAnimation animation) {
        KeyframeAnimation.AnimationBuilder builder = animation.mutableCopy();
        builder.isLooped = false;
        builder.returnTick = 0;
        builder.stopTick = Math.max(builder.stopTick, builder.endTick + 3);
        return builder.build();
    }

    private static PlayerEntity findPlayer(MinecraftClient client, UUID ownerUuid) {
        if (client.world == null) {
            return null;
        }
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player.getUuid().equals(ownerUuid)) {
                return player;
            }
        }
        return null;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public record CerberusVisual(
            float mainAlpha,
            float innerAuraAlpha,
            float outerAuraAlpha,
            float purpleCoreAlpha,
            float purpleInnerAlpha,
            float purpleOuterAlpha
    ) {
    }

    public record CerberusPose(
            float x,
            float y,
            float z,
            float pitch,
            float yaw,
            float roll
    ) {
    }

    private enum Phase {
        EQUIP,
        HOLD,
        SHOOT,
        UNEQUIP
    }

    private enum Playback {
        NORMAL,
        HOLD_LAST_FRAME,
        ADDITIVE_HEAD_HOLD_LAST_FRAME
    }

    private static final class VisualState {
        private final Phase phase;
        private int ageTicks;
        private boolean predicted;

        private VisualState(Phase phase) {
            this.phase = phase;
        }
    }

    private static class HoldLastFrameKeyframeAnimationPlayer extends KeyframeAnimationPlayer {
        protected final KeyframeAnimation animation;

        private HoldLastFrameKeyframeAnimationPlayer(KeyframeAnimation animation) {
            super(animation);
            this.animation = animation;
        }

        @Override
        public void tick() {
            if (getCurrentTick() < animation.endTick) {
                super.tick();
            }
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void setupAnim(float tickDelta) {
            if (getCurrentTick() >= animation.endTick) {
                super.setupAnim(0.0F);
                return;
            }
            super.setupAnim(tickDelta);
        }
    }

    private static final class AdditiveHeadHoldLastFrameKeyframeAnimationPlayer
            extends HoldLastFrameKeyframeAnimationPlayer {
        private static final Vec3f ZERO = new Vec3f(0.0F, 0.0F, 0.0F);

        private AdditiveHeadHoldLastFrameKeyframeAnimationPlayer(KeyframeAnimation animation) {
            super(animation);
        }

        @Override
        public Vec3f get3DTransform(
                String modelName,
                TransformType type,
                float tickDelta,
                Vec3f value0
        ) {
            if ("head".equals(modelName)
                    && (type == TransformType.ROTATION || type == TransformType.POSITION)) {
                Vec3f recoil = super.get3DTransform(modelName, type, tickDelta, ZERO);
                return new Vec3f(
                        value0.getX() + recoil.getX(),
                        value0.getY() + recoil.getY(),
                        value0.getZ() + recoil.getZ()
                );
            }
            return super.get3DTransform(modelName, type, tickDelta, value0);
        }
    }
}
