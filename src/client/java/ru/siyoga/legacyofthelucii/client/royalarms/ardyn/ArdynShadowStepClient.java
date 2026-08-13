package ru.siyoga.legacyofthelucii.client.royalarms.ardyn;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ArdynShadowStepClient {
    private static final int ACTIVE_GRACE_TICKS = 4;
    private static final int SILHOUETTE_TICKS = 12;
    private static final int START_SILHOUETTE_TICKS = 6;
    private static final Map<UUID, VisualState> VISUALS = new HashMap<>();
    private static boolean renderingSilhouette;

    private ArdynShadowStepClient() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ArdynShadowStepClient::renderWorld);
        HudRenderCallback.EVENT.register(ArdynShadowStepClient::renderOverlay);
    }

    public static void update(UUID ownerUuid, boolean active) {
        update(ownerUuid, active, true);
    }

    /**
     * Updates the shared Shadow Step visual state.
     * Point Warp passes screenOverlay=false, preserving the hidden model and
     * silhouette while leaving the screen colors untouched.
     */
    public static void update(UUID ownerUuid, boolean active, boolean screenOverlay) {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = client.world == null ? 0L : client.world.getTime();
        VisualState state = VISUALS.computeIfAbsent(ownerUuid, uuid -> new VisualState());
        state.active = active;
        state.lastUpdateTick = now;
        if (active) {
            state.activeStartTick = now;
            state.screenOverlay = screenOverlay;
        }
        if (!active) {
            state.fadeUntilTick = now + SILHOUETTE_TICKS;
        }
    }

    public static boolean shouldHidePlayer(UUID ownerUuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = client.world == null ? Long.MAX_VALUE : client.world.getTime();
        VisualState state = VISUALS.get(ownerUuid);
        return !renderingSilhouette && state != null && (state.active || state.lastUpdateTick + ACTIVE_GRACE_TICKS >= now);
    }

    public static boolean isActive(UUID ownerUuid) {
        VisualState state = VISUALS.get(ownerUuid);
        return state != null && state.active && state.screenOverlay;
    }

    public static void clear() {
        VISUALS.clear();
    }

    private static void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || context.matrixStack() == null || context.consumers() == null) {
            return;
        }
        long now = client.world.getTime();
        Vec3d cameraPos = context.camera().getPos();
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        Iterator<Map.Entry<UUID, VisualState>> iterator = VISUALS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, VisualState> entry = iterator.next();
            VisualState state = entry.getValue();
            PlayerEntity player = findPlayer(client, entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (!state.active && now > state.fadeUntilTick) {
                iterator.remove();
                continue;
            }
            if (state.active) {
                float activeAge = now + context.tickDelta() - state.activeStartTick;
                if (activeAge <= START_SILHOUETTE_TICKS) {
                    float fade = 1.0F - MathHelper.clamp(activeAge / START_SILHOUETTE_TICKS, 0.0F, 1.0F);
                    renderSilhouette(
                            dispatcher,
                            player,
                            context.matrixStack(),
                            context.consumers(),
                            cameraPos,
                            player.getLerpedPos(context.tickDelta()),
                            player.getYaw(context.tickDelta()),
                            0.72F * fade,
                            context.tickDelta()
                    );
                }
            }
        }
    }

    private static void renderOverlay(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        VisualState state = client.player == null ? null : VISUALS.get(client.player.getUuid());
        if (client.player == null
                || state == null
                || !state.screenOverlay
                || !shouldHidePlayer(client.player.getUuid())) {
            return;
        }

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        context.fill(0, 0, width, height, 0x3A8E2630);
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
            float tickDelta
    ) {
        matrices.push();
        renderingSilhouette = true;
        dispatcher.render(
                player,
                pos.x - cameraPos.x,
                pos.y - cameraPos.y,
                pos.z - cameraPos.z,
                yaw,
                tickDelta,
                matrices,
                new ShadowVertexConsumerProvider(consumers, alpha),
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

    private static final class ShadowVertexConsumerProvider implements VertexConsumerProvider {
        private final VertexConsumerProvider delegate;
        private final float alpha;

        private ShadowVertexConsumerProvider(VertexConsumerProvider delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return new ShadowVertexConsumer(delegate.getBuffer(layer), alpha);
        }
    }

    private static final class ShadowVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;

        private ShadowVertexConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(8, 4, 5, MathHelper.clamp((int) (alpha * this.alpha), 0, 255));
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
            delegate.fixedColor(8, 4, 5, MathHelper.clamp((int) (alpha * this.alpha), 0, 255));
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
        public void vertex(
                float x,
                float y,
                float z,
                float red,
                float green,
                float blue,
                float alpha,
                float u,
                float v,
                int overlay,
                int light,
                float normalX,
                float normalY,
                float normalZ
        ) {
            delegate.vertex(
                    x,
                    y,
                    z,
                    red * 0.03F,
                    green * 0.015F,
                    blue * 0.02F,
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

    private static final class VisualState {
        private boolean active;
        private long lastUpdateTick;
        private long fadeUntilTick;
        private long activeStartTick;
        private boolean screenOverlay;
    }
}
