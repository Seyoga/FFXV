package ru.siyoga.legacyofthelucii.client.royalarms.warp;

import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.animation.LuciiAnimatedPlayer;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RoyalArmsWarpTrailClient {
    private static final int TRAIL_TICKS = 10;
    private static final int HIDE_TICKS = 10;
    private static final int ARDYN_TRAIL_TICKS = 14;
    private static final int ARDYN_HIDE_TICKS = 12;
    private static final int ARDYN_CHARGE_VISUAL_TICKS = 18;
    private static final int SILHOUETTE_DELAY_TICKS = 2;
    private static final int SILHOUETTES = 4;
    private static final double BACK_OFFSET = 0.22D;
    private static final Identifier ARDYN_WARP_SLAM_ANIMATION = new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_warp_slam");
    private static final List<WarpTrail> TRAILS = new ArrayList<>();
    private static final Map<UUID, Long> HIDDEN_PLAYERS = new HashMap<>();
    private static final Map<UUID, ChargeState> ARDYN_CHARGING_PLAYERS = new HashMap<>();
    private static boolean renderingSilhouette;

    private RoyalArmsWarpTrailClient() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(RoyalArmsWarpTrailClient::render);
    }

    public static void add(UUID ownerUuid, LuciiLegacy legacy, Vec3d from, Vec3d to, float yaw) {
        MinecraftClient client = MinecraftClient.getInstance();
        long startTick = client.world == null ? 0L : client.world.getTime();
        int lifeTicks = legacy == LuciiLegacy.ARDYN ? ARDYN_TRAIL_TICKS : TRAIL_TICKS;
        int hideTicks = legacy == LuciiLegacy.ARDYN ? ARDYN_HIDE_TICKS : HIDE_TICKS;
        TRAILS.add(new WarpTrail(ownerUuid, legacy, from, to, yaw, startTick, lifeTicks));
        HIDDEN_PLAYERS.put(ownerUuid, startTick + hideTicks);
    }

    public static boolean shouldHidePlayer(UUID ownerUuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = client.world == null ? Long.MAX_VALUE : client.world.getTime();
        return !renderingSilhouette
                && !shouldPoseArdynWarpArms(ownerUuid)
                && HIDDEN_PLAYERS.getOrDefault(ownerUuid, 0L) > now;
    }

    public static boolean isRenderingSilhouette() {
        return renderingSilhouette;
    }

    public static void updateArdynCharge(UUID ownerUuid, boolean active) {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = client.world == null ? 0L : client.world.getTime();
        if (active) {
            ARDYN_CHARGING_PLAYERS.put(ownerUuid, new ChargeState(now, now + ARDYN_CHARGE_VISUAL_TICKS));
            playArdynWarpSlam(ownerUuid);
        } else {
            ARDYN_CHARGING_PLAYERS.remove(ownerUuid);
        }
    }

    public static boolean shouldPoseArdynWarpArms(UUID ownerUuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = client.world == null ? Long.MAX_VALUE : client.world.getTime();
        ChargeState state = ARDYN_CHARGING_PLAYERS.get(ownerUuid);
        if (state == null) {
            return false;
        }
        if (state.untilTick <= now) {
            ARDYN_CHARGING_PLAYERS.remove(ownerUuid);
            return false;
        }
        return true;
    }

    public static float ardynWarpChargeProgress(UUID ownerUuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = client.world == null ? Long.MAX_VALUE : client.world.getTime();
        ChargeState state = ARDYN_CHARGING_PLAYERS.get(ownerUuid);
        if (state == null || state.untilTick <= state.startTick) {
            return 0.0F;
        }
        return MathHelper.clamp((now - state.startTick) / (float) (state.untilTick - state.startTick), 0.0F, 1.0F);
    }

    public static void clear() {
        TRAILS.clear();
        HIDDEN_PLAYERS.clear();
        ARDYN_CHARGING_PLAYERS.clear();
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || context.matrixStack() == null || context.consumers() == null) {
            return;
        }

        Vec3d cameraPos = context.camera().getPos();
        float tickDelta = context.tickDelta();
        long worldTime = client.world.getTime();
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        Iterator<WarpTrail> iterator = TRAILS.iterator();
        while (iterator.hasNext()) {
            WarpTrail trail = iterator.next();
            PlayerEntity player = findPlayer(client, trail.ownerUuid);
            if (player == null) {
                iterator.remove();
                continue;
            }

            float age = worldTime + tickDelta - trail.startTick;
            if (age < SILHOUETTE_DELAY_TICKS) {
                continue;
            }

            float life = MathHelper.clamp(age / trail.lifeTicks, 0.0F, 1.0F);
            Vec3d direction = trail.to.subtract(trail.from);
            Vec3d forward = direction.lengthSquared() <= 0.0001D ? Vec3d.ZERO : direction.normalize();
            Vec3d backwards = forward.multiply(-0.18D);
            Vec3d basePos = trail.from.add(forward.multiply(-BACK_OFFSET));

            for (int i = 0; i < SILHOUETTES; i++) {
                float depthFade = 1.0F - i / (float) SILHOUETTES;
                float alpha = (1.0F - life) * (0.54F - i * 0.06F) * depthFade;
                if (alpha <= 0.02F) {
                    continue;
                }

                Vec3d pos = basePos.add(backwards.multiply(i));
                renderSilhouette(dispatcher, player, context.matrixStack(), context.consumers(), cameraPos, pos, trail.yaw, alpha, tickDelta, trail.legacy);
            }

            if (age > trail.lifeTicks) {
                iterator.remove();
            }
        }
    }

    private static void renderSilhouette(
            EntityRenderDispatcher dispatcher,
            PlayerEntity player,
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            Vec3d cameraPos,
            Vec3d pos,
            float yaw,
            float alpha,
            float tickDelta,
            LuciiLegacy legacy
    ) {
        matrices.push();
        VertexConsumerProvider tintedConsumers = new TintedEntityVertexConsumerProvider(consumers, alpha, legacy);
        renderingSilhouette = true;
        dispatcher.render(
                player,
                pos.x - cameraPos.x,
                pos.y - cameraPos.y,
                pos.z - cameraPos.z,
                yaw,
                tickDelta,
                matrices,
                tintedConsumers,
                0xF000F0
        );
        renderingSilhouette = false;
        matrices.pop();
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

    private static void playArdynWarpSlam(UUID ownerUuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = findPlayer(client, ownerUuid);
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer) || !(clientPlayer instanceof LuciiAnimatedPlayer animatedPlayer)) {
            return;
        }

        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(ARDYN_WARP_SLAM_ANIMATION);
        if (animation == null) {
            return;
        }

        animatedPlayer.legacyOfTheLucii$getAnimationLayer().setAnimation(new KeyframeAnimationPlayer(animation));
    }

    private static final class TintedEntityVertexConsumerProvider implements VertexConsumerProvider {
        private final VertexConsumerProvider delegate;
        private final float alpha;
        private final LuciiLegacy legacy;

        private TintedEntityVertexConsumerProvider(VertexConsumerProvider delegate, float alpha, LuciiLegacy legacy) {
            this.delegate = delegate;
            this.alpha = alpha;
            this.legacy = legacy;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return new TintedEntityVertexConsumer(delegate.getBuffer(layer), alpha, legacy);
        }
    }

    private static final class TintedEntityVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;
        private final LuciiLegacy legacy;

        private TintedEntityVertexConsumer(VertexConsumer delegate, float alpha, LuciiLegacy legacy) {
            this.delegate = delegate;
            this.alpha = alpha;
            this.legacy = legacy;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            if (legacy == LuciiLegacy.ARDYN) {
                delegate.color(
                        MathHelper.clamp((int) (red * 0.34F + 130), 0, 255),
                        MathHelper.clamp((int) (green * 0.18F + 20), 0, 255),
                        MathHelper.clamp((int) (blue * 0.26F + 42), 0, 255),
                        MathHelper.clamp((int) (alpha * this.alpha), 0, 255)
                );
            } else {
                delegate.color(
                        MathHelper.clamp((int) (red * 0.24F + 52), 0, 255),
                        MathHelper.clamp((int) (green * 0.45F + 104), 0, 255),
                        255,
                        MathHelper.clamp((int) (alpha * this.alpha), 0, 255)
                );
            }
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void next() {
            delegate.next();
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {
            if (legacy == LuciiLegacy.ARDYN) {
                delegate.fixedColor(210, 34, 58, MathHelper.clamp((int) (alpha * this.alpha), 0, 255));
            } else {
                delegate.fixedColor(64, 144, 255, MathHelper.clamp((int) (alpha * this.alpha), 0, 255));
            }
        }

        @Override
        public void unfixColor() {
            delegate.unfixColor();
        }

        @Override
        public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
            delegate.vertex(matrix, x, y, z);
            return this;
        }

        @Override
        public VertexConsumer normal(Matrix3f matrix, float x, float y, float z) {
            delegate.normal(matrix, x, y, z);
            return this;
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
            if (legacy == LuciiLegacy.ARDYN) {
                delegate.vertex(
                        x,
                        y,
                        z,
                        red * 0.34F + 0.52F,
                        green * 0.18F + 0.08F,
                        blue * 0.26F + 0.16F,
                        alpha * this.alpha,
                        u,
                        v,
                        overlay,
                        light,
                        normalX,
                        normalY,
                        normalZ
                );
            } else {
                delegate.vertex(
                        x,
                        y,
                        z,
                        red * 0.24F + 0.20F,
                        green * 0.45F + 0.40F,
                        1.0F,
                        alpha * this.alpha,
                        u,
                        v,
                        overlay,
                        light,
                        normalX,
                        normalY,
                        normalZ
                );
            }
        }
    }

    private static final class WarpTrail {
        private final UUID ownerUuid;
        private final LuciiLegacy legacy;
        private final Vec3d from;
        private final Vec3d to;
        private final float yaw;
        private final long startTick;
        private final int lifeTicks;

        private WarpTrail(UUID ownerUuid, LuciiLegacy legacy, Vec3d from, Vec3d to, float yaw, long startTick, int lifeTicks) {
            this.ownerUuid = ownerUuid;
            this.legacy = legacy;
            this.from = from;
            this.to = to;
            this.yaw = yaw;
            this.startTick = startTick;
            this.lifeTicks = lifeTicks;
        }
    }

    private record ChargeState(long startTick, long untilTick) {
    }
}
