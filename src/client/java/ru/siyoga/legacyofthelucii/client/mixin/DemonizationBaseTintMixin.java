package ru.siyoga.legacyofthelucii.client.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.state.DemonizationClientState;
import ru.siyoga.legacyofthelucii.effect.Demonization;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mixin(LivingEntityRenderer.class)
public abstract class DemonizationBaseTintMixin {
    private static final Set<UUID> LOGGED = new HashSet<>();

    @ModifyArgs(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"
            )
    )
    private void legacyofthelucii$tintBaseModel(
            Args args,
            LivingEntity entity,
            float entityYaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light
    ) {
        if (!(entity instanceof MobEntity mob) || mob.isRemoved() || entity instanceof SlimeEntity) {
            return;
        }

        boolean demonizedOnClient =
                DemonizationClientState.isDemonized(mob.getUuid())
                        || Demonization.isDemonized(mob);

        if (!demonizedOnClient) {
            return;
        }

        if (LOGGED.add(mob.getUuid())) {
            LegacyOfTheLucii.LOGGER.info(
                    "Demonization renderer v9.1: tinting base model for type={}, uuid={}",
                    mob.getType().getTranslationKey(),
                    mob.getUuid()
            );
        }

        float pulse = 0.5F + 0.5F
                * (float) Math.sin((entity.age + tickDelta) * 0.12F);

        // Indices in EntityModel#render:
        // 0 MatrixStack, 1 VertexConsumer, 2 light, 3 overlay, 4 red, 5 green, 6 blue, 7 alpha
        float red = 0.10F + pulse * 0.04F;
        float green = 0.01F + pulse * 0.01F;
        float blue = 0.24F + pulse * 0.10F;

        args.set(4, red);
        args.set(5, green);
        args.set(6, blue);
        args.set(7, 1.0F);
    }
}
