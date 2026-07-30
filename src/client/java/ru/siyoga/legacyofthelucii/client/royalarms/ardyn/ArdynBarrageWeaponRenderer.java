package ru.siyoga.legacyofthelucii.client.royalarms.ardyn;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.siyoga.legacyofthelucii.entity.ArdynBarrageWeaponEntity;

public final class ArdynBarrageWeaponRenderer extends EntityRenderer<ArdynBarrageWeaponEntity> {
    private static final Vector3f LOCAL_ITEM_TIP_DIRECTION = new Vector3f(1.0F, 1.0F, 0.0F).normalize();
    private static final Vector3f LOCAL_TRIDENT_TIP_DIRECTION = new Vector3f(0.0F, 1.0F, 0.0F).normalize();
    private static final RenderTint BASE_TINT = new RenderTint(1.0F, 0.18F, 0.28F, 0.80F);

    private final ItemRenderer itemRenderer;

    public ArdynBarrageWeaponRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            ArdynBarrageWeaponEntity entity,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light
    ) {
        ItemStack stack = entity.getWeaponStack();
        if (stack.isEmpty()) {
            return;
        }

        float recallProgress = entity.getRecallProgress();
        float scale = entity.isRecalling()
                ? MathHelper.lerp(recallProgress, 0.92F, 0.46F)
                : 0.92F;
        float alpha = entity.isRecalling()
                ? MathHelper.lerp(recallProgress, 0.82F, 0.30F)
                : 0.82F;
        RenderTint tint = BASE_TINT.withAlpha(alpha);
        VertexConsumerProvider tintedConsumers = new TintedItemVertexConsumerProvider(vertexConsumers, tint);

        matrices.push();
        matrices.multiply(tipRotation(stack, entity.getRenderDirection()));
        matrices.scale(scale, scale, scale);
        itemRenderer.renderItem(
                stack,
                ModelTransformationMode.NONE,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV,
                matrices,
                tintedConsumers,
                entity.getWorld(),
                entity.getId()
        );
        matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(ArdynBarrageWeaponEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }

    private static Quaternionf tipRotation(ItemStack stack, Vec3d direction) {
        Vec3d safeDirection = direction.lengthSquared() < 1.0E-6D
                ? new Vec3d(0.0D, 1.0D, 0.0D)
                : direction.normalize();
        Vector3f targetDirection = new Vector3f(
                (float) safeDirection.x,
                (float) safeDirection.y,
                (float) safeDirection.z
        ).normalize();
        Vector3f localTipDirection = stack.isOf(Items.TRIDENT)
                ? LOCAL_TRIDENT_TIP_DIRECTION
                : LOCAL_ITEM_TIP_DIRECTION;
        return new Quaternionf().rotationTo(localTipDirection, targetDirection);
    }

    private record RenderTint(float red, float green, float blue, float alpha) {
        private RenderTint withAlpha(float value) {
            return new RenderTint(red, green, blue, value);
        }
    }

    private static final class TintedItemVertexConsumerProvider implements VertexConsumerProvider {
        private final VertexConsumerProvider delegate;
        private final RenderTint tint;

        private TintedItemVertexConsumerProvider(VertexConsumerProvider delegate, RenderTint tint) {
            this.delegate = delegate;
            this.tint = tint;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return new TintedItemVertexConsumer(delegate.getBuffer(layer), tint);
        }
    }

    private static final class TintedItemVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final RenderTint tint;

        private TintedItemVertexConsumer(VertexConsumer delegate, RenderTint tint) {
            this.delegate = delegate;
            this.tint = tint;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(
                    MathHelper.clamp((int) (red * tint.red), 0, 255),
                    MathHelper.clamp((int) (green * tint.green), 0, 255),
                    MathHelper.clamp((int) (blue * tint.blue), 0, 255),
                    MathHelper.clamp((int) (alpha * tint.alpha), 0, 255)
            );
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
            delegate.fixedColor(red, green, blue, alpha);
        }

        @Override
        public void unfixColor() {
            delegate.unfixColor();
        }
    }
}
