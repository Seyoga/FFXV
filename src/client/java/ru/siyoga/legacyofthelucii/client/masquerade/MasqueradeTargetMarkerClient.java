package ru.siyoga.legacyofthelucii.client.masquerade;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;

import java.util.UUID;

/** Client-only billboard marking the observer selected by the local Ardyn player. */
public final class MasqueradeTargetMarkerClient {
    private static final Identifier TARGET_TEXTURE = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "textures/gui/masquerade/masquarade_target.png"
    );
    private static final float MARKER_SIZE = 0.52F;

    private MasqueradeTargetMarkerClient() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(MasqueradeTargetMarkerClient::render);
    }

    private static void render(WorldRenderContext context) {
        if (context.matrixStack() == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        UUID targetUuid = MasqueradeClientState.localTargetUuid();
        int targetEntityId = MasqueradeClientState.localTargetEntityId();
        if (client.world == null || client.player == null || targetUuid == null || targetEntityId < 0) {
            return;
        }
        Entity target = client.world.getEntityById(targetEntityId);
        if (target == null || target.isRemoved() || !targetUuid.equals(target.getUuid())) {
            return;
        }

        Vec3d cameraPos = context.camera().getPos();
        float tickDelta = context.tickDelta();
        double targetX = MathHelper.lerp(tickDelta, target.prevX, target.getX());
        double targetY = MathHelper.lerp(tickDelta, target.prevY, target.getY());
        double targetZ = MathHelper.lerp(tickDelta, target.prevZ, target.getZ());
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(
                targetX - cameraPos.x,
                targetY + target.getHeight() + 1.05D - cameraPos.y,
                targetZ - cameraPos.z
        );
        matrices.multiply(context.camera().getRotation());
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        float halfSize = MARKER_SIZE * 0.5F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, TARGET_TEXTURE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(positionMatrix, -halfSize, -halfSize, 0.0F).texture(0.0F, 1.0F).next();
        buffer.vertex(positionMatrix, halfSize, -halfSize, 0.0F).texture(1.0F, 1.0F).next();
        buffer.vertex(positionMatrix, halfSize, halfSize, 0.0F).texture(1.0F, 0.0F).next();
        buffer.vertex(positionMatrix, -halfSize, halfSize, 0.0F).texture(0.0F, 0.0F).next();
        tessellator.draw();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        matrices.pop();
    }
}
