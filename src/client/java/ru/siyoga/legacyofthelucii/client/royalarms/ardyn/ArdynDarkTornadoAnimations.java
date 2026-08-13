package ru.siyoga.legacyofthelucii.client.royalarms.ardyn;

import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.animation.LuciiAnimatedPlayer;

import java.util.UUID;

/** Local player-animation triggers for Ardyn Dark Tornado targeting. */
public final class ArdynDarkTornadoAnimations {
    private static final Identifier FRAME_START = new Identifier(
            LegacyOfTheLucii.MOD_ID, "ardyn_dark_tornado_frame_start");
    private static final Identifier FRAME_CANCEL = new Identifier(
            LegacyOfTheLucii.MOD_ID, "ardyn_dark_tornado_frame_cancel2");
    private static final Identifier FRAME_CLICK = new Identifier(
            LegacyOfTheLucii.MOD_ID, "ardyn_dark_tornado_frame_click");

    private ArdynDarkTornadoAnimations() {
    }

    public static void tick(MinecraftClient client) {
    }

    public static void playStart(UUID ownerUuid) {
        play(ownerUuid, FRAME_START, Playback.HOLD_LAST_FRAME);
    }

    public static void playCancel(UUID ownerUuid) {
        play(ownerUuid, FRAME_CANCEL, Playback.STOP_AT_LAST_FRAME);
    }

    public static void playClick(UUID ownerUuid) {
        play(ownerUuid, FRAME_CLICK, Playback.STOP_AT_LAST_FRAME);
    }

    public static void clear() {
    }

    private static void play(UUID ownerUuid, Identifier animationId, Playback playback) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = findPlayer(client, ownerUuid);
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)
                || !(clientPlayer instanceof LuciiAnimatedPlayer animatedPlayer)) {
            return;
        }

        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(animationId);
        if (animation == null) {
            LegacyOfTheLucii.LOGGER.warn(
                    "[DarkTornado/ANIM] Missing player animation {}.", animationId);
            return;
        }

        KeyframeAnimation runtimeAnimation = withoutLoop(animation);
        animatedPlayer.legacyOfTheLucii$getAnimationLayer()
                .setAnimation(new ClampedKeyframeAnimationPlayer(runtimeAnimation, playback));
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

    private enum Playback {
        HOLD_LAST_FRAME,
        STOP_AT_LAST_FRAME
    }

    private static final class ClampedKeyframeAnimationPlayer extends KeyframeAnimationPlayer {
        private final KeyframeAnimation animation;
        private final Playback playback;

        private ClampedKeyframeAnimationPlayer(KeyframeAnimation animation, Playback playback) {
            super(animation);
            this.animation = animation;
            this.playback = playback;
        }

        @Override
        public void tick() {
            if (getCurrentTick() < animation.endTick) {
                super.tick();
                return;
            }

            if (playback == Playback.STOP_AT_LAST_FRAME) {
                stop();
            }
        }

        @Override
        public boolean isActive() {
            return playback == Playback.HOLD_LAST_FRAME || super.isActive();
        }

        @Override
        public void setupAnim(float tickDelta) {
            if (playback == Playback.HOLD_LAST_FRAME && getCurrentTick() >= animation.endTick) {
                super.setupAnim(0.0F);
                return;
            }
            super.setupAnim(tickDelta);
        }
    }
}
