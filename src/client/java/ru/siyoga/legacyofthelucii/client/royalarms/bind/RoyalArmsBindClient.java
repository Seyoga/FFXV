package ru.siyoga.legacyofthelucii.client.royalarms.bind;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.siyoga.legacyofthelucii.client.royalarms.RoyalArmsAbility;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RoyalArmsBindClient {
    private static final int GATHER_TICKS = 18;
    private static final int CANCEL_TICKS = 18;
    private static final int IMPALED_HOLD_TICKS = 100;
    private static final int IMPALED_FADE_TICKS = 24;
    private static final int IMPALED_TICKS = IMPALED_HOLD_TICKS + IMPALED_FADE_TICKS;
    private static final double AIM_RADIUS = 1.55D;
    private static final double AIM_START_EXTRA_RADIUS = 2.35D;
    private static final double IMPALED_RADIUS = 0.78D;
    private static final float ACTIVE_SCALE = 0.95F;
    private static final float IMPALED_SCALE = 0.82F;
    private static final Vector3f LOCAL_ITEM_TIP_DIRECTION = new Vector3f(1.0F, 1.0F, 0.0F).normalize();
    private static final Vector3f LOCAL_TRIDENT_TIP_DIRECTION = new Vector3f(0.0F, 1.0F, 0.0F).normalize();

    private static final Map<UUID, BindVisual> VISUALS = new HashMap<>();

    private RoyalArmsBindClient() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(RoyalArmsBindClient::render);
    }

    public static void update(UUID ownerUuid, int targetEntityId, Vec3d targetCenter, boolean active, boolean impaled, LuciiLegacy legacy, List<ItemStack> stacks) {
        if (active) {
            VISUALS.put(ownerUuid, new BindVisual(targetEntityId, targetCenter, legacy, copyStacks(stacks), true));
            return;
        }

        if (impaled) {
            VISUALS.put(ownerUuid, new BindVisual(targetEntityId, targetCenter, legacy, copyStacks(stacks), false));
            return;
        }

        BindVisual visual = VISUALS.get(ownerUuid);
        if (visual == null) {
            RoyalArmsAbility.restartAuraAppearance(ownerUuid);
            return;
        }

        visual.canceling = true;
        visual.active = false;
        visual.age = 0;
    }

    public static void clear() {
        VISUALS.clear();
    }

    public static boolean isBinding(UUID ownerUuid) {
        BindVisual visual = VISUALS.get(ownerUuid);
        return visual != null;
    }

    public static boolean isAiming(UUID ownerUuid) {
        BindVisual visual = VISUALS.get(ownerUuid);
        return visual != null && visual.active && !visual.canceling;
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || context.matrixStack() == null || context.consumers() == null) {
            return;
        }

        Vec3d cameraPos = context.camera().getPos();
        float tickDelta = context.tickDelta();
        Iterator<Map.Entry<UUID, BindVisual>> iterator = VISUALS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BindVisual> entry = iterator.next();
            BindVisual visual = entry.getValue();
            Entity entity = client.world.getEntityById(visual.targetEntityId);
            LivingEntity target = entity instanceof LivingEntity livingEntity ? livingEntity : null;
            if ((visual.active && target == null) || visual.stacks.isEmpty()) {
                RoyalArmsAbility.restartAuraAppearance(entry.getKey());
                iterator.remove();
                continue;
            }

            Vec3d targetCenter = target == null ? visual.fallbackCenter : targetCenter(target, tickDelta);
            RenderTint tint = RenderTint.forLegacy(visual.legacy, visual.active && !visual.canceling);
            for (int i = 0; i < visual.stacks.size(); i++) {
                float fade = visual.canceling ? cancelFade(visual.age + tickDelta) : (visual.active ? 1.0F : impaledFade(visual.age + tickDelta));
                Vec3d itemPos = visual.canceling
                        ? cancelItemPos(targetCenter, target == null ? visual.fallbackHeight : target.getHeight(), i, visual.stacks.size(), visual.age + tickDelta)
                        : visual.active
                        ? activeItemPos(targetCenter, target.getHeight(), i, visual.stacks.size(), visual.age + tickDelta)
                        : impaledItemPos(targetCenter, target == null ? visual.fallbackHeight : target.getHeight(), i, visual.stacks.size());
                float scale = visual.active
                        ? ACTIVE_SCALE * easeOutCubic(Math.min(1.0F, (visual.age + tickDelta) / GATHER_TICKS))
                        : IMPALED_SCALE * fade;
                renderItem(context, visual.stacks.get(i), itemPos, targetCenter, cameraPos, scale, tint.withAlphaMultiplier(fade), i);
            }

            visual.age++;
            if (visual.canceling && visual.age > CANCEL_TICKS) {
                RoyalArmsAbility.restartAuraAppearance(entry.getKey());
                iterator.remove();
            } else if (!visual.active && !visual.canceling && visual.age > IMPALED_TICKS) {
                RoyalArmsAbility.restartAuraAppearance(entry.getKey());
                iterator.remove();
            }
        }
    }

    private static Vec3d activeItemPos(Vec3d center, float targetHeight, int index, int total, float age) {
        Vec3d direction = direction(index, total);
        double gatherProgress = easeOutCubic(Math.min(1.0F, age / GATHER_TICKS));
        double radius = MathHelper.lerp(gatherProgress, AIM_RADIUS + AIM_START_EXTRA_RADIUS, AIM_RADIUS);
        double heightWave = Math.sin(index * 1.7D) * targetHeight * 0.12D;
        return center.add(direction.multiply(radius)).add(0.0D, heightWave, 0.0D);
    }

    private static Vec3d impaledItemPos(Vec3d center, float targetHeight, int index, int total) {
        Vec3d direction = direction(index, total);
        double heightWave = Math.sin(index * 1.7D) * targetHeight * 0.08D;
        return center.add(direction.multiply(IMPALED_RADIUS)).add(0.0D, heightWave, 0.0D);
    }

    private static Vec3d cancelItemPos(Vec3d center, float targetHeight, int index, int total, float age) {
        Vec3d direction = direction(index, total);
        double cancelProgress = easeInOutCubic(Math.min(1.0F, age / CANCEL_TICKS));
        double radius = MathHelper.lerp(cancelProgress, AIM_RADIUS, AIM_RADIUS + AIM_START_EXTRA_RADIUS);
        double heightWave = Math.sin(index * 1.7D) * targetHeight * 0.12D;
        return center.add(direction.multiply(radius)).add(0.0D, heightWave, 0.0D);
    }

    private static Vec3d direction(int index, int total) {
        double angle = index * 2.399963229728653D;
        double vertical;
        if (index % 5 == 0) {
            vertical = 0.88D;
        } else {
            double band = (index % 7) / 6.0D;
            vertical = MathHelper.lerp(band, -0.35D, 0.58D);
        }

        double horizontal = Math.sqrt(Math.max(0.05D, 1.0D - vertical * vertical));
        Vec3d direction = new Vec3d(Math.sin(angle) * horizontal, vertical, Math.cos(angle) * horizontal);
        return direction.normalize();
    }

    private static Vec3d targetCenter(LivingEntity target, float tickDelta) {
        double x = MathHelper.lerp(tickDelta, target.prevX, target.getX());
        double y = MathHelper.lerp(tickDelta, target.prevY, target.getY()) + target.getHeight() * 0.55D;
        double z = MathHelper.lerp(tickDelta, target.prevZ, target.getZ());
        return new Vec3d(x, y, z);
    }

    private static void renderItem(
            WorldRenderContext context,
            ItemStack stack,
            Vec3d itemPos,
            Vec3d targetCenter,
            Vec3d cameraPos,
            float scale,
            RenderTint tint,
            int seed
    ) {
        Vec3d toTarget = targetCenter.subtract(itemPos).normalize();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = new TintedItemVertexConsumerProvider(context.consumers(), tint);

        matrices.push();
        matrices.translate(itemPos.x - cameraPos.x, itemPos.y - cameraPos.y, itemPos.z - cameraPos.z);
        matrices.multiply(tipRotation(stack, toTarget));
        matrices.scale(scale, scale, scale);
        MinecraftClient.getInstance().getItemRenderer().renderItem(
                stack,
                ModelTransformationMode.NONE,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV,
                matrices,
                consumers,
                context.world(),
                seed
        );
        matrices.pop();
    }

    private static Quaternionf tipRotation(ItemStack stack, Vec3d toTarget) {
        Vector3f targetDirection = new Vector3f((float) toTarget.x, (float) toTarget.y, (float) toTarget.z).normalize();
        Vector3f localTipDirection = stack.isOf(Items.TRIDENT) ? LOCAL_TRIDENT_TIP_DIRECTION : LOCAL_ITEM_TIP_DIRECTION;
        return new Quaternionf().rotationTo(localTipDirection, targetDirection);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copied = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                copied.add(stack.copyWithCount(stack.getCount()));
            }
        }
        return copied;
    }

    private static float easeOutCubic(float value) {
        float clamped = MathHelper.clamp(value, 0.0F, 1.0F);
        float inverse = 1.0F - clamped;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float impaledFade(float age) {
        if (age <= IMPALED_HOLD_TICKS) {
            return 1.0F;
        }

        float fadeProgress = MathHelper.clamp((age - IMPALED_HOLD_TICKS) / IMPALED_FADE_TICKS, 0.0F, 1.0F);
        return 1.0F - easeInOutCubic(fadeProgress);
    }

    private static float cancelFade(float age) {
        float progress = MathHelper.clamp(age / CANCEL_TICKS, 0.0F, 1.0F);
        return 1.0F - easeInOutCubic(progress);
    }

    private static float easeInOutCubic(float value) {
        float clamped = MathHelper.clamp(value, 0.0F, 1.0F);
        if (clamped < 0.5F) {
            return 4.0F * clamped * clamped * clamped;
        }

        float shifted = -2.0F * clamped + 2.0F;
        return 1.0F - shifted * shifted * shifted / 2.0F;
    }

    private record RenderTint(float red, float green, float blue, float alpha) {
        private RenderTint withAlphaMultiplier(float multiplier) {
            return new RenderTint(red, green, blue, alpha * multiplier);
        }

        private static RenderTint forLegacy(LuciiLegacy legacy, boolean active) {
            if (legacy == LuciiLegacy.ARDYN) {
                return active ? new RenderTint(1.0F, 0.18F, 0.28F, 0.74F) : new RenderTint(1.0F, 0.28F, 0.34F, 0.88F);
            }
            return active ? new RenderTint(0.58F, 0.78F, 1.0F, 0.70F) : new RenderTint(0.75F, 0.9F, 1.0F, 0.86F);
        }
    }

    private static final class BindVisual {
        private final int targetEntityId;
        private final Vec3d fallbackCenter;
        private final float fallbackHeight;
        private final LuciiLegacy legacy;
        private final List<ItemStack> stacks;
        private boolean active;
        private boolean canceling;
        private int age;

        private BindVisual(int targetEntityId, Vec3d fallbackCenter, LuciiLegacy legacy, List<ItemStack> stacks, boolean active) {
            this.targetEntityId = targetEntityId;
            this.fallbackCenter = fallbackCenter;
            this.fallbackHeight = 1.8F;
            this.legacy = legacy;
            this.stacks = stacks;
            this.active = active;
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
