package ru.siyoga.legacyofthelucii.client.sniper;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.siyoga.legacyofthelucii.entity.ArdynSniperBulletEntity;
import ru.siyoga.legacyofthelucii.sniper.ArdynSniperContent;

public final class ArdynSniperBulletRenderer extends EntityRenderer<ArdynSniperBulletEntity> {
    private static final Vector3f LOCAL_TIP_DIRECTION = new Vector3f(0.0F, 0.0F, 1.0F);
    private static final float MODEL_SCALE = 1.35F;

    private final ItemRenderer itemRenderer;
    private final ItemStack bulletStack = new ItemStack(ArdynSniperContent.SNIPER_BULLET_ITEM);

    public ArdynSniperBulletRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            ArdynSniperBulletEntity entity,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light
    ) {
        Vec3d direction = entity.getVelocity();
        if (direction.lengthSquared() < 1.0E-8D) {
            direction = new Vec3d(0.0D, 0.0D, 1.0D);
        }

        matrices.push();
        matrices.multiply(rotationTo(direction));
        // The Blockbench model occupies Y=0..2 and Z=6.1..12.8. Re-center that
        // geometry around the entity origin without changing the uploaded cuboids.
        matrices.translate(0.0D, 7.0D / 16.0D, -1.45D / 16.0D);
        matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        itemRenderer.renderItem(
                bulletStack,
                ModelTransformationMode.NONE,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vertexConsumers,
                entity.getWorld(),
                entity.getId()
        );
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(ArdynSniperBulletEntity entity) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }

    private static Quaternionf rotationTo(Vec3d direction) {
        Vec3d normalized = direction.normalize();
        Vector3f target = new Vector3f(
                (float) normalized.x,
                (float) normalized.y,
                (float) normalized.z
        ).normalize();
        return new Quaternionf().rotationTo(LOCAL_TIP_DIRECTION, target);
    }
}
