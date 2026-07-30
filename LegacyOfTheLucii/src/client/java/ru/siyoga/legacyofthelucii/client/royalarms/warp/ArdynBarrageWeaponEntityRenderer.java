package ru.siyoga.legacyofthelucii.client.royalarms.warp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.entity.ArdynBarrageWeaponEntity;

public final class ArdynBarrageWeaponEntityRenderer extends EntityRenderer<ArdynBarrageWeaponEntity> {
    private static final Identifier TEXTURE = new Identifier(LegacyOfTheLucii.MOD_ID, "textures/entity/ardyn_barrage_weapon.png");
    private static final Vector3f LOCAL_ITEM_TIP_DIRECTION = new Vector3f(1.0F, 1.0F, 0.0F).normalize();
    private static final Vector3f LOCAL_TRIDENT_TIP_DIRECTION = new Vector3f(0.0F, 1.0F, 0.0F).normalize();
    private static final float SCALE = 1.05F;

    public ArdynBarrageWeaponEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
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
        ItemStack stack = entity.stack();
        if (stack.isEmpty()) {
            return;
        }

        Vec3d direction = renderDirection(entity);
        matrices.push();
        matrices.multiply(tipRotation(stack, direction));
        matrices.scale(SCALE, SCALE, SCALE);
        MinecraftClient.getInstance().getItemRenderer().renderItem(
                stack,
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

    private static Vec3d renderDirection(ArdynBarrageWeaponEntity entity) {
        Vec3d velocity = entity.getVelocity();
        if (entity.barrageState() == ArdynBarrageWeaponEntity.State.FLYING && velocity.lengthSquared() > 0.0001D) {
            return velocity.normalize();
        }

        Vec3d impactDirection = entity.impactDirection();
        if (impactDirection.lengthSquared() > 0.0001D) {
            return impactDirection.normalize();
        }

        return new Vec3d(0.0D, 0.0D, 1.0D);
    }

    private static Quaternionf tipRotation(ItemStack stack, Vec3d direction) {
        Vector3f targetDirection = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z).normalize();
        Vector3f localTipDirection = stack.isOf(Items.TRIDENT) ? LOCAL_TRIDENT_TIP_DIRECTION : LOCAL_ITEM_TIP_DIRECTION;
        return new Quaternionf().rotationTo(localTipDirection, targetDirection);
    }

    @Override
    public Identifier getTexture(ArdynBarrageWeaponEntity entity) {
        return TEXTURE;
    }
}
