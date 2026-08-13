package ru.siyoga.legacyofthelucii.client.royalarms.ardyn;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.darktornado.DarkTornadoNetwork;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynDarkTornadoAbility;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client input, targeting preview and animation lifecycle hook for Dark Tornado. */
public final class ArdynDarkTornadoClient {
    private static final String LOG = "[DarkTornado/CLIENT]";
    private static final String CATEGORY = "key.categories.legacyofthelucii";
    private static final double MARKER_Y_OFFSET = 0.035D;
    private static final int CONFIRM_ACK_TIMEOUT_TICKS = 12;

    private static final DustParticleEffect MARKER_PARTICLE = new DustParticleEffect(
            new Vector3f(0.66F, 0.09F, 0.95F), 1.0F
    );

    private static final Map<UUID, Vec3d> ACTIVE_VISUALS = new HashMap<>();

    private static KeyBinding darkTornadoKey;
    private static Target target;
    private static boolean targeting;
    private static boolean comboWasDown;
    private static boolean cancelRequested;
    private static boolean registered;
    private static int confirmPendingTicks;

    private ArdynDarkTornadoClient() {
    }

    public static void register() {
        if (registered) {
            LegacyOfTheLucii.LOGGER.warn("{} Duplicate register() ignored.", LOG);
            return;
        }
        registered = true;

        darkTornadoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.legacyofthelucii.royal_arms.ardyn_dark_tornado",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_5,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(ArdynDarkTornadoClient::tick);
        ClientPreAttackCallback.EVENT.register(ArdynDarkTornadoClient::onPreAttack);
        WorldRenderEvents.AFTER_ENTITIES.register(ArdynDarkTornadoClient::renderMarker);

        LegacyOfTheLucii.LOGGER.info(
                "{} Registered key {} with default key 5; Ctrl is checked separately.",
                LOG, darkTornadoKey.getTranslationKey());
    }

    private static void tick(MinecraftClient client) {
        ArdynDarkTornadoAnimations.tick(client);

        boolean comboDown = isCtrlDown(client) && isFiveDown(client);
        if (comboDown && !comboWasDown && client.getNetworkHandler() != null) {
            sendToggle();
        }
        comboWasDown = comboDown;

        if (confirmPendingTicks > 0) {
            confirmPendingTicks--;
        }

        if (!targeting) {
            target = null;
            confirmPendingTicks = 0;
            return;
        }

        if (client.player == null || client.world == null || client.getNetworkHandler() == null) {
            clearTargetingOnly();
            return;
        }

        // A GUI can swallow both the mouse and movement input. Cancel on the server
        // instead of leaving the player frozen behind an inventory/menu screen.
        if (client.currentScreen != null) {
            if (!cancelRequested) {
                cancelRequested = true;
                sendToggle();
            }
            return;
        }

        Vec3d velocity = client.player.getVelocity();
        client.player.setVelocity(0.0D, velocity.y, 0.0D);
        client.player.input.movementForward = 0.0F;
        client.player.input.movementSideways = 0.0F;
        client.player.input.pressingForward = false;
        client.player.input.pressingBack = false;
        client.player.input.pressingLeft = false;
        client.player.input.pressingRight = false;
        client.player.input.jumping = false;
        client.player.input.sneaking = false;

        target = findTarget(client);
        if (target != null && client.world.getTime() % 2L == 0L) {
            spawnMarkerParticles(client, target.markerPos());
        }
    }

    private static boolean onPreAttack(
            MinecraftClient client,
            net.minecraft.client.network.ClientPlayerEntity player,
            int clickCount
    ) {
        if (!targeting) {
            return false;
        }
        if (clickCount <= 0) {
            return true;
        }
        if (confirmPendingTicks > 0) {
            return true;
        }
        if (target == null) {
            player.sendMessage(Text.translatable(
                    "message.legacyofthelucii.ardyn.dark_tornado.invalid_target"), true);
            return true;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(DarkTornadoNetwork.CONFIRM_TARGET_ACTION);
        buf.writeBlockPos(target.blockPos());
        ClientPlayNetworking.send(DarkTornadoNetwork.ACTION_PACKET, buf);
        confirmPendingTicks = CONFIRM_ACK_TIMEOUT_TICKS;

        LegacyOfTheLucii.LOGGER.info("{} Confirm sent for {}.", LOG, target.blockPos());
        return true;
    }

    public static void setTargeting(boolean newTargeting) {
        targeting = newTargeting;
        cancelRequested = false;
        confirmPendingTicks = 0;
        if (!newTargeting) {
            target = null;
        }
        LegacyOfTheLucii.LOGGER.info("{} Targeting state={}", LOG, newTargeting);
    }

    /**
     * Player-animation integration hook.
     *
     * active=true  -> start your Dark Tornado cast/hold animation for ownerUuid
     * active=false -> stop that animation for ownerUuid
     *
     * The center is supplied in case the animation controller or later renderer
     * needs to orient the player toward the tornado.
     */
    public static void updateVisual(UUID ownerUuid, boolean active, Vec3d center) {
        if (active) {
            ACTIVE_VISUALS.put(ownerUuid, center);
        } else {
            ACTIVE_VISUALS.remove(ownerUuid);
        }
        LegacyOfTheLucii.LOGGER.info(
                "{} Visual state owner={}, active={}, center={}",
                LOG, ownerUuid, active, center);
    }

    public static boolean isVisualActive(UUID ownerUuid) {
        return ACTIVE_VISUALS.containsKey(ownerUuid);
    }

    public static Vec3d visualCenter(UUID ownerUuid) {
        return ACTIVE_VISUALS.get(ownerUuid);
    }

    public static boolean isTargeting() {
        return targeting;
    }

    public static void clear() {
        clearTargetingOnly();
        ACTIVE_VISUALS.clear();
        comboWasDown = false;
    }

    private static void clearTargetingOnly() {
        targeting = false;
        cancelRequested = false;
        target = null;
        confirmPendingTicks = 0;
    }

    private static void sendToggle() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(DarkTornadoNetwork.TOGGLE_TARGETING_ACTION);
        ClientPlayNetworking.send(DarkTornadoNetwork.ACTION_PACKET, buf);
        LegacyOfTheLucii.LOGGER.info("{} Toggle packet sent.", LOG);
    }

    private static Target findTarget(MinecraftClient client) {
        Vec3d eyePos = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0F).normalize();
        HitResult hit = client.world.raycast(new RaycastContext(
                eyePos,
                eyePos.add(look.multiply(ArdynDarkTornadoAbility.MAX_TARGET_RANGE)),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos blockPos = ((BlockHitResult) hit).getBlockPos().toImmutable();
        BlockState state = client.world.getBlockState(blockPos);
        VoxelShape shape = state.getCollisionShape(client.world, blockPos);
        if (shape.isEmpty()) {
            return null;
        }

        Box bounds = shape.getBoundingBox();
        Vec3d markerPos = new Vec3d(
                blockPos.getX() + (bounds.minX + bounds.maxX) * 0.5D,
                blockPos.getY() + bounds.maxY + MARKER_Y_OFFSET,
                blockPos.getZ() + (bounds.minZ + bounds.maxZ) * 0.5D
        );
        return new Target(blockPos, markerPos);
    }

    private static boolean isCtrlDown(MinecraftClient client) {
        if (client.getWindow() == null) {
            return false;
        }
        long handle = client.getWindow().getHandle();
        return InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean isFiveDown(MinecraftClient client) {
        if (darkTornadoKey != null && darkTornadoKey.isPressed()) {
            return true;
        }
        return client.getWindow() != null
                && InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_5);
    }

    private static void spawnMarkerParticles(MinecraftClient client, Vec3d center) {
        int count = 14;
        double time = client.world.getTime() * 0.12D;
        for (int i = 0; i < count; i++) {
            double angle = time + Math.PI * 2.0D * i / count;
            double radius = ArdynDarkTornadoAbility.EFFECT_RADIUS;
            client.world.addParticle(
                    MARKER_PARTICLE,
                    center.x + Math.cos(angle) * radius,
                    center.y + 0.10D,
                    center.z + Math.sin(angle) * radius,
                    0.0D, 0.018D, 0.0D
            );
        }
    }

    private static void renderMarker(WorldRenderContext context) {
        if (!targeting || target == null
                || context.matrixStack() == null
                || context.consumers() == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }

        Vec3d cameraPos = context.camera().getPos();
        Vec3d markerPos = target.markerPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        double time = client.world.getTime() + context.tickDelta();
        double pulse = 1.0D + Math.sin(time * 0.25D) * 0.035D;

        matrices.push();
        matrices.translate(
                markerPos.x - cameraPos.x,
                markerPos.y - cameraPos.y,
                markerPos.z - cameraPos.z
        );

        drawRing(matrices, lines,
                ArdynDarkTornadoAbility.EFFECT_RADIUS * pulse,
                0.055D, 42,
                0.69F, 0.12F, 0.96F, 0.92F);
        drawRing(matrices, lines,
                ArdynDarkTornadoAbility.EFFECT_RADIUS * 0.62D * pulse,
                0.042D, 32,
                0.28F, 0.03F, 0.48F, 0.88F);

        Box core = new Box(-0.12D, 0.0D, -0.12D, 0.12D, 0.45D, 0.12D);
        WorldRenderer.drawBox(matrices, lines, core,
                0.82F, 0.18F, 1.0F, 0.96F);
        matrices.pop();
    }

    private static void drawRing(
            MatrixStack matrices,
            VertexConsumer lines,
            double radius,
            double segmentHalfSize,
            int segments,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        for (int i = 0; i < segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Box segment = new Box(
                    x - segmentHalfSize, 0.01D, z - segmentHalfSize,
                    x + segmentHalfSize, 0.055D, z + segmentHalfSize
            );
            WorldRenderer.drawBox(matrices, lines, segment, red, green, blue, alpha);
        }
    }

    private record Target(BlockPos blockPos, Vec3d markerPos) {
    }
}
