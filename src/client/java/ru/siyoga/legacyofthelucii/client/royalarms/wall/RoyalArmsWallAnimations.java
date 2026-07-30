package ru.siyoga.legacyofthelucii.client.royalarms.wall;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class RoyalArmsWallAnimations {
    private static final int ANIMATION_TICKS = 10;
    private static final float MIN_SCALE = 0.08F;
    private static final double CENTER_OFFSET = 0.5D;
    private static final List<SegmentAnimation> ANIMATIONS = new ArrayList<>();

    private RoyalArmsWallAnimations() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(RoyalArmsWallAnimations::render);
    }

    public static void add(UUID ownerUuid, BlockPos pos, BlockState sourceState, boolean appearing) {
        ANIMATIONS.add(new SegmentAnimation(pos.toImmutable(), sourceState, appearing));
    }

    public static boolean isAppearing(BlockPos pos) {
        for (SegmentAnimation animation : ANIMATIONS) {
            if (animation.appearing && animation.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        ANIMATIONS.clear();
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || context.matrixStack() == null || context.consumers() == null) {
            return;
        }

        Vec3d cameraPos = context.camera().getPos();
        float tickDelta = context.tickDelta();
        Iterator<SegmentAnimation> iterator = ANIMATIONS.iterator();
        while (iterator.hasNext()) {
            SegmentAnimation animation = iterator.next();
            float age = animation.age + tickDelta;
            float progress = Math.min(1.0F, age / ANIMATION_TICKS);
            float eased = animation.appearing ? easeOutCubic(progress) : 1.0F - easeInCubic(progress);
            float scale = MIN_SCALE + (1.0F - MIN_SCALE) * eased;
            Vec3d renderPos = Vec3d.of(animation.pos);

            context.matrixStack().push();
            context.matrixStack().translate(renderPos.x - cameraPos.x, renderPos.y - cameraPos.y, renderPos.z - cameraPos.z);
            context.matrixStack().translate(CENTER_OFFSET, CENTER_OFFSET, CENTER_OFFSET);
            context.matrixStack().scale(scale, scale, scale);
            context.matrixStack().translate(-CENTER_OFFSET, -CENTER_OFFSET, -CENTER_OFFSET);
            RoyalArmsWallBlockEntityRenderer.renderCube(
                    animation.sourceState,
                    client.world,
                    animation.pos,
                    context.matrixStack(),
                    context.consumers(),
                    LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    animation.appearing ? progress : 1.0F - progress
            );
            context.matrixStack().pop();

            animation.age++;
            if (animation.age > ANIMATION_TICKS) {
                iterator.remove();
            }
        }
    }

    private static float easeOutCubic(float progress) {
        float inverted = 1.0F - progress;
        return 1.0F - inverted * inverted * inverted;
    }

    private static float easeInCubic(float progress) {
        return progress * progress * progress;
    }

    private static final class SegmentAnimation {
        private final BlockPos pos;
        private final BlockState sourceState;
        private final boolean appearing;
        private int age;

        private SegmentAnimation(BlockPos pos, BlockState sourceState, boolean appearing) {
            this.pos = pos;
            this.sourceState = sourceState;
            this.appearing = appearing;
        }
    }
}
