package ru.siyoga.legacyofthelucii.client.sniper;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

public final class ArdynCerberusModel {
    public static final String BONE_NAME = "cerberus-0";
    private static final String INNER_AURA_NAME = "cerberus-0-inner-aura";
    private static final String OUTER_AURA_NAME = "cerberus-0-outer-aura";
    private static final float BASE_PIVOT_X = -6.0000F;
    private static final float BASE_PIVOT_Y = 27.0000F;
    private static final float BASE_PIVOT_Z = -2.7000F;

    private final ModelPart cerberus;
    private final ModelPart innerAura;
    private final ModelPart outerAura;

    public ArdynCerberusModel() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        root.addChild(
                BONE_NAME,
                createBuilder(Dilation.NONE),
                ModelTransform.pivot(BASE_PIVOT_X, BASE_PIVOT_Y, BASE_PIVOT_Z)
        );
        root.addChild(
                INNER_AURA_NAME,
                createBuilder(new Dilation(0.18F)),
                ModelTransform.pivot(BASE_PIVOT_X, BASE_PIVOT_Y, BASE_PIVOT_Z)
        );
        root.addChild(
                OUTER_AURA_NAME,
                createBuilder(new Dilation(0.42F)),
                ModelTransform.pivot(BASE_PIVOT_X, BASE_PIVOT_Y, BASE_PIVOT_Z)
        );

        ModelPart rootModel = TexturedModelData.of(modelData, 64, 64).createModel();
        cerberus = rootModel.getChild(BONE_NAME);
        innerAura = rootModel.getChild(INNER_AURA_NAME);
        outerAura = rootModel.getChild(OUTER_AURA_NAME);
    }

    private static ModelPartBuilder createBuilder(Dilation dilation) {
        return ModelPartBuilder.create()
                .uv(0, 0).cuboid(-3.0000F, -7.5000F, -11.0000F, 3.0000F, 2.0000F, 13.6000F, dilation)
                .uv(0, 0).cuboid(-2.6000F, -8.3000F, -9.0000F, 2.2000F, 0.8000F, 11.6000F, dilation)
                .uv(0, 0).cuboid(-2.4000F, -9.1000F, -10.0000F, 1.8000F, 0.8000F, 11.6000F, dilation)
                .uv(0, 0).cuboid(-2.8000F, -9.1000F, -10.9000F, 2.6000F, 1.6000F, 0.9000F, dilation)
                .uv(0, 0).cuboid(-2.8000F, -9.1000F, -10.9000F, 2.6000F, 1.6000F, 0.9000F, dilation)
                .uv(0, 0).cuboid(-2.6000F, -8.7000F, 1.6000F, 2.2000F, 0.8000F, 2.0000F, dilation)
                .uv(0, 0).cuboid(-2.2000F, -7.4000F, -24.9000F, 1.4000F, 0.5000F, 13.3000F, dilation)
                .uv(0, 0).cuboid(-2.5000F, -6.6000F, -25.6000F, 2.0000F, 0.6000F, 13.3000F, dilation)
                .uv(0, 0).cuboid(-2.8000F, -7.0000F, -25.6000F, 0.6000F, 1.0000F, 13.3000F, dilation)
                .uv(0, 0).cuboid(-1.1000F, -7.0000F, -25.6000F, 0.6000F, 1.0000F, 13.3000F, dilation)
                .uv(0, 0).cuboid(-2.5000F, -7.4000F, -25.6000F, 0.6000F, 1.4000F, 13.3000F, dilation)
                .uv(0, 0).cuboid(-2.8000F, -7.3000F, -11.6000F, 2.6000F, 1.6000F, 0.6000F, dilation)
                .uv(0, 0).cuboid(-3.1000F, -7.6000F, -12.6000F, 3.2000F, 2.2000F, 0.6000F, dilation)
                .uv(0, 0).cuboid(-2.0000F, -8.9000F, -23.5000F, 1.0000F, 0.5000F, 12.6000F, dilation)
                .uv(0, 0).cuboid(-2.0000F, -8.4000F, -18.9000F, 1.0000F, 1.0000F, 0.6000F, dilation)
                .uv(0, 0).cuboid(-2.0000F, -10.1000F, -0.9000F, 1.0000F, 1.0000F, 0.6000F, dilation)
                .uv(0, 0).cuboid(-2.0000F, -12.1000F, -3.9000F, 1.0000F, 0.7000F, 2.3000F, dilation)
                .uv(0, 0).cuboid(-2.6000F, -11.1000F, -3.9000F, 0.3000F, 0.7000F, 2.3000F, dilation)
                .uv(0, 0).cuboid(-0.7000F, -11.1000F, -3.9000F, 0.3000F, 0.7000F, 2.3000F, dilation)
                .uv(0, 0).cuboid(-2.0000F, -10.1000F, -5.9000F, 1.0000F, 1.0000F, 0.6000F, dilation)
                .uv(0, 0).cuboid(-2.3000F, -11.4000F, -6.9000F, 1.6000F, 1.3000F, 7.6000F, dilation)
                .uv(0, 0).cuboid(-2.5000F, -11.6000F, 0.7000F, 2.1000F, 1.8000F, 1.9000F, dilation)
                .uv(0, 0).cuboid(-2.5000F, -11.6000F, -8.6000F, 2.1000F, 1.8000F, 1.9000F, dilation)
                .uv(0, 0).cuboid(-2.0000F, -8.4000F, -17.5000F, 1.0000F, 1.0000F, 1.1000F, dilation)
                .uv(0, 0).cuboid(-2.0000F, -8.9000F, -23.0000F, 1.0000F, 1.0000F, 0.6000F, dilation)
                .uv(0, 0).cuboid(-2.0000F, -8.9000F, -23.0000F, 1.0000F, 1.0000F, 0.6000F, dilation)
                .uv(0, 0).cuboid(-2.2000F, -7.2000F, -24.3000F, 1.4000F, 1.0000F, 0.6000F, dilation)
                .uv(0, 0).cuboid(-2.5000F, -7.5000F, -26.2000F, 2.0000F, 1.6000F, 1.3000F, dilation)
                .uv(0, 0).cuboid(-2.3000F, -7.5000F, -27.5000F, 1.6000F, 0.5000F, 1.3000F, dilation)
                .uv(0, 0).cuboid(-2.3000F, -6.4000F, -27.5000F, 1.6000F, 0.5000F, 1.3000F, dilation)
                .uv(0, 0).cuboid(-2.3000F, -7.5000F, -27.5000F, 0.5000F, 1.6000F, 1.3000F, dilation)
                .uv(0, 0).cuboid(-1.2000F, -7.5000F, -27.5000F, 0.5000F, 1.6000F, 1.3000F, dilation)
                .uv(0, 0).cuboid(-3.5000F, -7.5000F, -9.1000F, 0.5000F, 0.7500F, 2.7500F, dilation)
                .uv(0, 0).cuboid(0.0000F, -7.5000F, -9.1000F, 0.5000F, 0.7500F, 2.7500F, dilation)
                .uv(0, 0).cuboid(-3.5000F, -6.9000F, -7.4000F, 0.5000F, 0.7500F, 7.7500F, dilation)
                .uv(0, 0).cuboid(0.0000F, -6.9000F, -7.4000F, 0.5000F, 0.7500F, 7.7500F, dilation)
                .uv(0, 0).cuboid(-2.5000F, -5.5000F, -1.3000F, 2.0000F, 0.5000F, 2.0000F, dilation)
                .uv(0, 0).cuboid(-2.1000F, -5.0000F, -1.0000F, 1.2000F, 2.0000F, 2.0000F, dilation)
                .uv(0, 0).cuboid(-2.1000F, -5.5000F, -7.8000F, 1.2000F, 2.0000F, 4.1000F, dilation)
                .uv(0, 0).cuboid(-2.1000F, -3.5000F, -7.5000F, 1.2000F, 0.9000F, 3.3000F, dilation)
                .uv(0, 0).cuboid(-2.6000F, -7.0000F, 2.6000F, 2.2000F, 2.0000F, 2.0000F, dilation)
                .uv(0, 0).cuboid(-2.2000F, -6.7000F, 4.6000F, 1.4000F, 2.0000F, 4.6000F, dilation)
                .uv(0, 0).cuboid(-2.6000F, -7.1000F, 9.2000F, 2.2000F, 4.2000F, 1.1000F, dilation)
                .uv(0, 0).cuboid(-2.2000F, -4.1000F, 8.9000F, 1.4000F, 0.5000F, 0.4000F, dilation)
                .uv(0, 0).cuboid(-2.1000F, -3.0000F, 0.0000F, 1.2000F, 2.0000F, 2.0000F, dilation)
                .uv(0, 0).cuboid(-2.1000F, -1.0000F, 0.0000F, 1.2000F, 1.0000F, 3.0000F, dilation);
    }

    public void setPose(ArdynSniperAnimations.CerberusPose pose) {
        applyPose(cerberus, pose);
        applyPose(innerAura, pose);
        applyPose(outerAura, pose);
    }

    private static void applyPose(ModelPart part, ArdynSniperAnimations.CerberusPose pose) {
        if (pose == null) {
            part.setPivot(BASE_PIVOT_X, BASE_PIVOT_Y, BASE_PIVOT_Z);
            part.setAngles(0.0F, 0.0F, 0.0F);
            return;
        }

        part.setPivot(
                BASE_PIVOT_X + pose.x(),
                BASE_PIVOT_Y + pose.y(),
                BASE_PIVOT_Z + pose.z()
        );
        part.setAngles(pose.pitch(), pose.yaw(), pose.roll());
    }

    public void renderMain(
            MatrixStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            float alpha
    ) {
        cerberus.render(matrices, vertices, light, overlay, 1.0F, 1.0F, 1.0F, alpha);
    }

    public void renderInnerAura(
            MatrixStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            float alpha
    ) {
        innerAura.render(matrices, vertices, light, overlay, 1.0F, 0.44F, 0.92F, alpha);
    }

    public void renderOuterAura(
            MatrixStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            float alpha
    ) {
        outerAura.render(matrices, vertices, light, overlay, 1.0F, 0.68F, 0.96F, alpha);
    }

    public void renderPurpleCore(
            MatrixStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            float alpha
    ) {
        cerberus.render(matrices, vertices, light, overlay, 0.62F, 0.10F, 1.0F, alpha);
    }

    public void renderPurpleInnerAura(
            MatrixStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            float alpha
    ) {
        innerAura.render(matrices, vertices, light, overlay, 0.56F, 0.08F, 1.0F, alpha);
    }

    public void renderPurpleOuterAura(
            MatrixStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            float alpha
    ) {
        outerAura.render(matrices, vertices, light, overlay, 0.38F, 0.03F, 0.98F, alpha);
    }
}
