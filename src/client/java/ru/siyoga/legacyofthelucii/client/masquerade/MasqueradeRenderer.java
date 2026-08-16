package ru.siyoga.legacyofthelucii.client.masquerade;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import ru.siyoga.legacyofthelucii.masquerade.MasqueradeMorph;

public final class MasqueradeRenderer {
    private static boolean renderingMorph;

    private MasqueradeRenderer() {
    }

    public static void renderWithoutPlayerReplacement(Runnable renderCall) {
        boolean previous = renderingMorph;
        renderingMorph = true;
        try {
            renderCall.run();
        } finally {
            renderingMorph = previous;
        }
    }

    public static boolean renderWorldMorph(
            EntityRenderDispatcher dispatcher,
            PlayerEntity player,
            double x,
            double y,
            double z,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light
    ) {
        if (renderingMorph) {
            return false;
        }
        MasqueradeMorph morph = MasqueradeClientState.activeMorph(player.getUuid());
        if (morph == null) {
            return false;
        }
        LivingEntity renderEntity = MasqueradeRenderEntityCache.get(morph, player);
        if (renderEntity == null) {
            return false;
        }

        renderingMorph = true;
        try {
            dispatcher.render(renderEntity, x, y, z, yaw, tickDelta, matrices, vertexConsumers, light);
            return true;
        } finally {
            renderingMorph = false;
        }
    }
}
