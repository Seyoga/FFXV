package ru.siyoga.legacyofthelucii.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import ru.siyoga.legacyofthelucii.effect.Demonization;

public final class DemonizationFeatureRenderer extends FeatureRenderer<MobEntity, EntityModel<MobEntity>> {
    private static final float RED = 0.25F;
    private static final float GREEN = 0.04F;
    private static final float BLUE = 0.40F;
    private static final float MIN_ALPHA = 0.58F;
    private static final float MAX_ALPHA = 0.68F;
    private static final int FULL_BRIGHT = 0x00F000F0;

    public DemonizationFeatureRenderer(FeatureRendererContext<MobEntity, EntityModel<MobEntity>> context) {
        super(context);
    }

    public static void register() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (MobEntity.class.isAssignableFrom(entityType.getBaseClass())) {
                registerMobFeature(entityRenderer, registrationHelper);
            }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerMobFeature(
            LivingEntityRenderer<?, ?> entityRenderer,
            LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper registrationHelper
    ) {
        FeatureRendererContext context = entityRenderer;
        registrationHelper.register(new DemonizationFeatureRenderer(context));
    }

    @Override
    public void render(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            MobEntity entity,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
        if (!Demonization.isDemonized(entity)) {
            return;
        }

        float pulse = (float) ((Math.sin((entity.age + tickDelta) * 0.16F) + 1.0D) * 0.5D);
        float intensity = 0.84F + pulse * 0.16F;
        float alpha = MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * pulse;
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(getTexture(entity)));
        matrices.push();
        matrices.scale(1.025F, 1.025F, 1.025F);
        getContextModel().render(
                matrices,
                consumer,
                light,
                LivingEntityRenderer.getOverlay(entity, 0.0F),
                RED * intensity,
                GREEN * intensity,
                BLUE * intensity,
                alpha
        );
        matrices.scale(1.018F, 1.018F, 1.018F);
        VertexConsumer glowConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(getTexture(entity)));
        getContextModel().render(
                matrices,
                glowConsumer,
                FULL_BRIGHT,
                LivingEntityRenderer.getOverlay(entity, 0.0F),
                0.55F + pulse * 0.20F,
                0.08F,
                0.85F + pulse * 0.15F,
                0.20F + pulse * 0.12F
        );
        matrices.pop();
    }
}
