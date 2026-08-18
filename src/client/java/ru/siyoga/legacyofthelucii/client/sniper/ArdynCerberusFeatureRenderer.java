package ru.siyoga.legacyofthelucii.client.sniper;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;

public final class ArdynCerberusFeatureRenderer extends FeatureRenderer<
        AbstractClientPlayerEntity,
        PlayerEntityModel<AbstractClientPlayerEntity>
        > {
    private static final Identifier TEXTURE = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "textures/entity/cerberus_0.png"
    );

    private final ArdynCerberusModel model = new ArdynCerberusModel();

    public ArdynCerberusFeatureRenderer(PlayerEntityRenderer context) {
        super(context);
    }

    @Override
    public void render(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            AbstractClientPlayerEntity player,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
        if (player.isInvisible()) {
            return;
        }

        ArdynSniperAnimations.CerberusVisual visual =
                ArdynSniperAnimations.getCerberusVisual(player.getUuid(), tickDelta);
        if (visual == null) {
            return;
        }

        ArdynSniperAnimations.CerberusPose pose =
                ArdynSniperAnimations.getCerberusPose(player, tickDelta);
        if (pose == null) {
            return;
        }

        model.setPose(pose);
        VertexConsumer vertices = vertexConsumers.getBuffer(
                RenderLayer.getEntityTranslucentEmissive(TEXTURE)
        );
        int fullBright = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        if (visual.purpleOuterAlpha() > 0.001F) {
            model.renderPurpleOuterAura(
                    matrices,
                    vertices,
                    fullBright,
                    OverlayTexture.DEFAULT_UV,
                    visual.purpleOuterAlpha()
            );
        }
        if (visual.outerAuraAlpha() > 0.001F) {
            model.renderOuterAura(
                    matrices,
                    vertices,
                    fullBright,
                    OverlayTexture.DEFAULT_UV,
                    visual.outerAuraAlpha()
            );
        }
        if (visual.purpleInnerAlpha() > 0.001F) {
            model.renderPurpleInnerAura(
                    matrices,
                    vertices,
                    fullBright,
                    OverlayTexture.DEFAULT_UV,
                    visual.purpleInnerAlpha()
            );
        }
        if (visual.innerAuraAlpha() > 0.001F) {
            model.renderInnerAura(
                    matrices,
                    vertices,
                    fullBright,
                    OverlayTexture.DEFAULT_UV,
                    visual.innerAuraAlpha()
            );
        }
        if (visual.mainAlpha() > 0.001F) {
            model.renderMain(
                    matrices,
                    vertices,
                    fullBright,
                    OverlayTexture.DEFAULT_UV,
                    visual.mainAlpha()
            );
        }
        if (visual.purpleCoreAlpha() > 0.001F) {
            model.renderPurpleCore(
                    matrices,
                    vertices,
                    fullBright,
                    OverlayTexture.DEFAULT_UV,
                    visual.purpleCoreAlpha()
            );
        }
    }
}
