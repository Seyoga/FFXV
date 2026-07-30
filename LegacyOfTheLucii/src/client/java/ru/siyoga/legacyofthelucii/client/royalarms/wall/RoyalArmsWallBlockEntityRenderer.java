package ru.siyoga.legacyofthelucii.client.royalarms.wall;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ru.siyoga.legacyofthelucii.block.RoyalArmsWallBlockEntity;

public final class RoyalArmsWallBlockEntityRenderer implements BlockEntityRenderer<RoyalArmsWallBlockEntity> {
    private static final float MIN = 0.02F;
    private static final float MAX = 0.98F;
    private static final int ALPHA = 118;
    private static final float RED = 0.45F;
    private static final float GREEN = 0.78F;
    private static final float BLUE = 1.0F;

    public RoyalArmsWallBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(
            RoyalArmsWallBlockEntity entity,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay
    ) {
        BlockState sourceState = entity.sourceState();
        if (RoyalArmsWallAnimations.isAppearing(entity.getPos())) {
            return;
        }

        renderCube(sourceState, entity.getWorld(), entity.getPos(), matrices, vertexConsumers, light, 1.0F);
    }

    public static void renderCube(
            BlockState sourceState,
            BlockRenderView world,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            float alphaScale
    ) {
        Sprite sprite = MinecraftClient.getInstance()
                .getBlockRenderManager()
                .getModel(sourceState)
                .getParticleSprite();
        int averageColor = MinecraftClient.getInstance().getBlockColors().getColor(sourceState, world, pos, 0);
        int red = tintChannel(averageColor, 16, RED);
        int green = tintChannel(averageColor, 8, GREEN);
        int blue = tintChannel(averageColor, 0, BLUE);
        int alpha = Math.max(0, Math.min(255, Math.round(ALPHA * alphaScale)));

        matrices.push();
        MatrixStack.Entry entry = matrices.peek();
        VertexConsumer vertices = vertexConsumers.getBuffer(RenderLayer.getTranslucent());
        renderFace(vertices, entry, Direction.NORTH, sprite, red, green, blue, alpha, light);
        renderFace(vertices, entry, Direction.SOUTH, sprite, red, green, blue, alpha, light);
        renderFace(vertices, entry, Direction.WEST, sprite, red, green, blue, alpha, light);
        renderFace(vertices, entry, Direction.EAST, sprite, red, green, blue, alpha, light);
        renderFace(vertices, entry, Direction.DOWN, sprite, red, green, blue, alpha, light);
        renderFace(vertices, entry, Direction.UP, sprite, red, green, blue, alpha, light);
        matrices.pop();
    }

    private static int tintChannel(int color, int shift, float aura) {
        int base = color == -1 ? 255 : (color >> shift) & 255;
        return Math.min(255, Math.round(base * 0.55F + 255.0F * aura * 0.45F));
    }

    private static void renderFace(
            VertexConsumer vertices,
            MatrixStack.Entry entry,
            Direction direction,
            Sprite sprite,
            int red,
            int green,
            int blue,
            int alpha,
            int light
    ) {
        Matrix4f positionMatrix = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();
        float minU = sprite.getMinU();
        float maxU = sprite.getMaxU();
        float minV = sprite.getMinV();
        float maxV = sprite.getMaxV();
        float normalX = direction.getOffsetX();
        float normalY = direction.getOffsetY();
        float normalZ = direction.getOffsetZ();

        switch (direction) {
            case NORTH -> renderQuad(vertices, positionMatrix, normalMatrix, normalX, normalY, normalZ, red, green, blue, alpha, light, minU, maxU, minV, maxV,
                    MIN, MIN, MIN,
                    MAX, MIN, MIN,
                    MAX, MAX, MIN,
                    MIN, MAX, MIN);
            case SOUTH -> renderQuad(vertices, positionMatrix, normalMatrix, normalX, normalY, normalZ, red, green, blue, alpha, light, minU, maxU, minV, maxV,
                    MAX, MIN, MAX,
                    MIN, MIN, MAX,
                    MIN, MAX, MAX,
                    MAX, MAX, MAX);
            case WEST -> renderQuad(vertices, positionMatrix, normalMatrix, normalX, normalY, normalZ, red, green, blue, alpha, light, minU, maxU, minV, maxV,
                    MIN, MIN, MAX,
                    MIN, MIN, MIN,
                    MIN, MAX, MIN,
                    MIN, MAX, MAX);
            case EAST -> renderQuad(vertices, positionMatrix, normalMatrix, normalX, normalY, normalZ, red, green, blue, alpha, light, minU, maxU, minV, maxV,
                    MAX, MIN, MIN,
                    MAX, MIN, MAX,
                    MAX, MAX, MAX,
                    MAX, MAX, MIN);
            case DOWN -> renderQuad(vertices, positionMatrix, normalMatrix, normalX, normalY, normalZ, red, green, blue, alpha, light, minU, maxU, minV, maxV,
                    MIN, MIN, MAX,
                    MAX, MIN, MAX,
                    MAX, MIN, MIN,
                    MIN, MIN, MIN);
            case UP -> renderQuad(vertices, positionMatrix, normalMatrix, normalX, normalY, normalZ, red, green, blue, alpha, light, minU, maxU, minV, maxV,
                    MIN, MAX, MIN,
                    MAX, MAX, MIN,
                    MAX, MAX, MAX,
                    MIN, MAX, MAX);
        }
    }

    private static void renderQuad(
            VertexConsumer vertices,
            Matrix4f positionMatrix,
            Matrix3f normalMatrix,
            float normalX,
            float normalY,
            float normalZ,
            int red,
            int green,
            int blue,
            int alpha,
            int light,
            float minU,
            float maxU,
            float minV,
            float maxV,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float x4,
            float y4,
            float z4
    ) {
        vertex(vertices, positionMatrix, normalMatrix, x1, y1, z1, red, green, blue, alpha, minU, maxV, light, normalX, normalY, normalZ);
        vertex(vertices, positionMatrix, normalMatrix, x2, y2, z2, red, green, blue, alpha, maxU, maxV, light, normalX, normalY, normalZ);
        vertex(vertices, positionMatrix, normalMatrix, x3, y3, z3, red, green, blue, alpha, maxU, minV, light, normalX, normalY, normalZ);
        vertex(vertices, positionMatrix, normalMatrix, x4, y4, z4, red, green, blue, alpha, minU, minV, light, normalX, normalY, normalZ);
        vertex(vertices, positionMatrix, normalMatrix, x4, y4, z4, red, green, blue, alpha, minU, minV, light, -normalX, -normalY, -normalZ);
        vertex(vertices, positionMatrix, normalMatrix, x3, y3, z3, red, green, blue, alpha, maxU, minV, light, -normalX, -normalY, -normalZ);
        vertex(vertices, positionMatrix, normalMatrix, x2, y2, z2, red, green, blue, alpha, maxU, maxV, light, -normalX, -normalY, -normalZ);
        vertex(vertices, positionMatrix, normalMatrix, x1, y1, z1, red, green, blue, alpha, minU, maxV, light, -normalX, -normalY, -normalZ);
    }

    private static void vertex(
            VertexConsumer vertices,
            Matrix4f positionMatrix,
            Matrix3f normalMatrix,
            float x,
            float y,
            float z,
            int red,
            int green,
            int blue,
            int alpha,
            float u,
            float v,
            int light,
            float normalX,
            float normalY,
            float normalZ
    ) {
        vertices.vertex(positionMatrix, x, y, z)
                .color(red, green, blue, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normalMatrix, normalX, normalY, normalZ)
                .next();
    }
}
