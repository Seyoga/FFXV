package ru.siyoga.legacyofthelucii.client.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.SlimeEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.siyoga.legacyofthelucii.client.demon.DemonHeadgrabVisualState;
import ru.siyoga.legacyofthelucii.demon.headgrab.DemonHeadgrabSystem;

@Mixin(SlimeEntityRenderer.class)
public abstract class DemonHeadgrabSlimeScaleMixin {
    @Inject(
            method = "render(Lnet/minecraft/entity/mob/SlimeEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD")
    )
    private void legacyofthelucii$lockAttachedSlimeToVictimHead(
            SlimeEntity slime,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        int victimEntityId = DemonHeadgrabVisualState.getVictimEntityId(
                slime.getId()
        );

        if (victimEntityId < 0
                || !DemonHeadgrabVisualState.isAttached(slime.getId())
                || slime.getSize()
                != DemonHeadgrabSystem.SMALLEST_SLIME_SIZE
                || slime.getWorld() == null) {
            return;
        }

        Entity entity = slime.getWorld().getEntityById(victimEntityId);
        if (!(entity instanceof LivingEntity victim)) {
            return;
        }

        float visualScale = DemonHeadgrabVisualState.getScale(
                slime.getId(),
                tickDelta
        );

        Vec3d slimePos = slime.getLerpedPos(tickDelta);
        Vec3d victimPos = victim.getLerpedPos(tickDelta);
        double headCenterY = victimPos.y
                + victim.getEyeHeight(victim.getPose())
                + 0.07D;
        double targetY = headCenterY
                - slime.getHeight() * visualScale * 0.5D;

        matrices.translate(
                victimPos.x - slimePos.x,
                targetY - slimePos.y,
                victimPos.z - slimePos.z
        );
    }

    @Inject(
            method = "scale(Lnet/minecraft/entity/mob/SlimeEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V",
            at = @At("TAIL")
    )
    private void legacyofthelucii$growAroundVictimHead(
            SlimeEntity slime,
            MatrixStack matrices,
            float tickDelta,
            CallbackInfo ci
    ) {
        float scale = DemonHeadgrabVisualState.getScale(
                slime.getId(),
                tickDelta
        );

        if (slime.getSize()
                != DemonHeadgrabSystem.SMALLEST_SLIME_SIZE
                || scale <= 1.0001F) {
            return;
        }

        /*
         * Keep the renderer's natural lower-origin scaling. The server moves
         * the attached slime down by half of its visual height, so its center
         * remains inside the player's head throughout the growth animation.
         */
        matrices.scale(
                scale,
                scale,
                scale
        );
    }
}
