package ru.siyoga.legacyofthelucii.client.royalarms.ardyn;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.BlockState;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.state.ClientLuciiState;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

import java.util.UUID;

/** Client-only target acquisition and marker rendering for Ardyn point warp. */
public final class ArdynPointWarpClient {
    private static final String LOG = "[PointWarp/CLIENT]";
    private static final String CATEGORY = "key.categories.legacyofthelucii";
    private static final Identifier MARKER_TEXTURE = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "textures/gui/pointwarp/sprite-0001.png"
    );
    private static final double MAX_RANGE = 48.0D;
    private static final double MARKER_Y_OFFSET = 0.42D;
    private static final float MARKER_SIZE = 0.86F;
    private static final DustParticleEffect MARKER_PARTICLE = new DustParticleEffect(
            new Vector3f(0.95F, 0.12F, 0.28F),
            0.85F
    );

    private static KeyBinding pointWarpKey;
    private static Target target;
    private static boolean localFlying;
    private static boolean keyWasDown;
    private static boolean jumpWasDown;
    private static int startRequestTicks;
    private static long lastScanLogTick = -1000L;
    private static Target lastLoggedTarget;
    private static String lastEligibilityFailure = "";
    private static boolean registered;

    private ArdynPointWarpClient() {
    }

    public static void register() {
        if (registered) {
            LegacyOfTheLucii.LOGGER.warn("{} register() called more than once; ignoring duplicate registration.", LOG);
            return;
        }
        registered = true;
        LegacyOfTheLucii.LOGGER.info("{} Registering keybinding and client callbacks.", LOG);
        pointWarpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.legacyofthelucii.royal_arms.ardyn_point_warp",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                CATEGORY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(ArdynPointWarpClient::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(ArdynPointWarpClient::renderMarker);
        ClientPreAttackCallback.EVENT.register(ArdynPointWarpClient::onPreAttack);
        LegacyOfTheLucii.LOGGER.info("{} Keybinding registered: translationKey={}, defaultKey=X, category={}.",
                LOG, pointWarpKey.getTranslationKey(), CATEGORY);
    }

    public static void update(UUID ownerUuid, boolean active) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean localOwner = client.player != null && ownerUuid.equals(client.player.getUuid());
        LegacyOfTheLucii.LOGGER.info("{} Visual state update: owner={}, active={}, localOwner={}.",
                LOG, ownerUuid, active, localOwner);
        if (localOwner) {
            localFlying = active;
            startRequestTicks = 0;
            if (active) {
                target = null;
            }
        }
    }

    public static boolean isLocalFlying() {
        return localFlying;
    }

    public static void clear() {
        target = null;
        localFlying = false;
        keyWasDown = false;
        jumpWasDown = false;
        startRequestTicks = 0;
        lastLoggedTarget = null;
        lastEligibilityFailure = "";
    }

    private static void tick(MinecraftClient client) {
        boolean keyDown = isPointWarpKeyDown(client);
        boolean jumpDown = client.options.jumpKey.isPressed();

        if (keyDown != keyWasDown) {
            LegacyOfTheLucii.LOGGER.info("{} X {}. Eligibility: {}.",
                    LOG, keyDown ? "pressed" : "released", eligibilityDescription(client));
        }

        if (startRequestTicks > 0) {
            startRequestTicks--;
            if (startRequestTicks == 0 && !localFlying) {
                LegacyOfTheLucii.LOGGER.warn("{} START acknowledgement timeout: no active visual packet arrived within 10 ticks.", LOG);
            }
        }

        if (!canUse(client)) {
            String failure = eligibilityDescription(client);
            if (keyDown && !failure.equals(lastEligibilityFailure)) {
                LegacyOfTheLucii.LOGGER.warn("{} Cannot select target while X is held: {}.", LOG, failure);
                lastEligibilityFailure = failure;
            }
            if ((localFlying || startRequestTicks > 0) && client.getNetworkHandler() != null) {
                sendStop("client became ineligible");
            }
            clearTargetStateOnly();
            keyWasDown = keyDown;
            jumpWasDown = jumpDown;
            return;
        }
        lastEligibilityFailure = "";

        boolean releasedX = !keyDown && keyWasDown;
        boolean pressedSpace = jumpDown && !jumpWasDown;
        if ((releasedX || pressedSpace) && (localFlying || startRequestTicks > 0)) {
            sendStop(releasedX ? "X released" : "Space pressed");
        }

        Target previousTarget = target;
        if (localFlying) {
            target = null;
        } else if (keyDown && client.currentScreen == null) {
            target = findTarget(client);
        } else {
            target = null;
        }

        if (!sameTarget(previousTarget, target)) {
            if (target == null) {
                LegacyOfTheLucii.LOGGER.info("{} Marker lost/cleared.", LOG);
            } else {
                double distance = client.player.getEyePos().distanceTo(target.markerPos());
                LegacyOfTheLucii.LOGGER.info("{} Marker selected: block={}, corner={}, marker={}, distance={}.",
                        LOG, target.blockPos().toString(), target.cornerIndex(), format(target.markerPos()),
                        String.format(java.util.Locale.ROOT, "%.2f", distance));
            }
            lastLoggedTarget = target;
        }

        if (target != null && client.world.getTime() % 2L == 0L) {
            spawnMarkerParticles(client, target.markerPos());
        }

        keyWasDown = keyDown;
        jumpWasDown = jumpDown;
    }

    private static boolean onPreAttack(
            MinecraftClient client,
            net.minecraft.client.network.ClientPlayerEntity player,
            int clickCount
    ) {
        boolean xHeld = isPointWarpKeyDown(client);
        if (!xHeld) {
            return false;
        }

        LegacyOfTheLucii.LOGGER.info("{} LMB callback while X held: clickCount={}, localFlying={}, target={}, eligibility={}.",
                LOG, clickCount, localFlying, describeTarget(target), eligibilityDescription(client));

        if (clickCount <= 0) {
            LegacyOfTheLucii.LOGGER.warn("{} LMB ignored: clickCount <= 0.", LOG);
            return false;
        }
        if (localFlying) {
            LegacyOfTheLucii.LOGGER.warn("{} LMB ignored: already flying.", LOG);
            return true;
        }
        if (target == null) {
            LegacyOfTheLucii.LOGGER.warn("{} LMB ignored: no valid marker is selected.", LOG);
            return true;
        }
        if (!canUse(client)) {
            LegacyOfTheLucii.LOGGER.warn("{} LMB ignored: {}.", LOG, eligibilityDescription(client));
            return true;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(LuciiNetwork.ARDYN_POINT_WARP_START_ACTION);
        buf.writeBlockPos(target.blockPos());
        buf.writeVarInt(target.cornerIndex());
        ClientPlayNetworking.send(LuciiNetwork.ARDYN_POINT_WARP_PACKET, buf);
        LegacyOfTheLucii.LOGGER.info("{} START packet sent: block={}, corner={}.",
                LOG, target.blockPos().toString(), target.cornerIndex());
        startRequestTicks = 10;
        return true;
    }

    private static boolean canUse(MinecraftClient client) {
        return "ready".equals(eligibilityDescription(client));
    }

    private static String eligibilityDescription(MinecraftClient client) {
        if (pointWarpKey == null) {
            return "keybinding is null (register() did not run)";
        }
        if (client.player == null) {
            return "client.player is null";
        }
        if (client.world == null) {
            return "client.world is null";
        }
        if (client.getNetworkHandler() == null) {
            return "network handler is null";
        }
        if (client.currentScreen != null) {
            return "a GUI screen is open: " + client.currentScreen.getClass().getSimpleName();
        }
        if (!ClientLuciiState.royalArmsActive()) {
            return "Royal Arms is not active";
        }
        if (ClientLuciiState.legacy() != LuciiLegacy.ARDYN) {
            return "legacy is " + ClientLuciiState.legacy() + ", expected ARDYN";
        }
        return "ready";
    }

    private static void sendStop(String reason) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(LuciiNetwork.ARDYN_POINT_WARP_STOP_ACTION);
        ClientPlayNetworking.send(LuciiNetwork.ARDYN_POINT_WARP_PACKET, buf);
        LegacyOfTheLucii.LOGGER.info("{} STOP packet sent. Reason: {}.", LOG, reason);
        startRequestTicks = 0;
    }

    private static void clearTargetStateOnly() {
        target = null;
        localFlying = false;
        startRequestTicks = 0;
    }

    private static Target findTarget(MinecraftClient client) {
        Vec3d eyePos = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0F).normalize();
        HitResult hit = client.world.raycast(new RaycastContext(
                eyePos,
                eyePos.add(look.multiply(MAX_RANGE)),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));

        Target best = null;
        int emptyShapes = 0;
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos blockPos = ((BlockHitResult) hit).getBlockPos().toImmutable();
            BlockState state = client.world.getBlockState(blockPos);
            VoxelShape shape = state.getCollisionShape(client.world, blockPos);
            if (shape.isEmpty()) {
                emptyShapes++;
            } else {
                Box bounds = shape.getBoundingBox();
                double topY = blockPos.getY() + bounds.maxY;
                double x = blockPos.getX() + (bounds.minX + bounds.maxX) * 0.5D;
                double z = blockPos.getZ() + (bounds.minZ + bounds.maxZ) * 0.5D;
                Vec3d markerPos = new Vec3d(x, topY + MARKER_Y_OFFSET, z);
                // The client only indicates the visible surface. The server owns
                // range, line-of-sight and landing-space validation at activation.
                best = new Target(blockPos, 0, markerPos);
            }
        }

        long now = client.world.getTime();
        if (now - lastScanLogTick >= 20L || !sameTarget(lastLoggedTarget, best)) {
            lastScanLogTick = now;
            LegacyOfTheLucii.LOGGER.info(
                    "{} Target scan: hitType={}, emptyShapes={}, result={}.",
                    LOG, hit.getType(), emptyShapes, describeTarget(best)
            );
        }
        return best;
    }

    private static boolean isPointWarpKeyDown(MinecraftClient client) {
        if (pointWarpKey != null && pointWarpKey.isPressed()) {
            return true;
        }

        // KeyBinding normally reads this state itself. The direct GLFW fallback
        // keeps the default X binding responsive if another input handler leaves
        // the KeyBinding pressed state stale.
        return client.getWindow() != null
                && InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_X);
    }

    private static void spawnMarkerParticles(MinecraftClient client, Vec3d markerPos) {
        client.world.addParticle(
                MARKER_PARTICLE,
                markerPos.x,
                markerPos.y + 0.08D,
                markerPos.z,
                0.0D,
                0.012D,
                0.0D
        );
    }

    private static boolean sameTarget(Target first, Target second) {
        if (first == second) {
            return true;
        }
        return first != null && second != null
                && first.cornerIndex() == second.cornerIndex()
                && first.blockPos().equals(second.blockPos());
    }

    private static String describeTarget(Target value) {
        return value == null
                ? "none"
                : value.blockPos().toString() + "/corner=" + value.cornerIndex();
    }

    private static String format(Vec3d pos) {
        return String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", pos.x, pos.y, pos.z);
    }

    private static void renderMarker(WorldRenderContext context) {
        if (target == null || context.matrixStack() == null || context.consumers() == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (!canUse(client) || localFlying || !isPointWarpKeyDown(client)) {
            return;
        }

        Vec3d cameraPos = context.camera().getPos();
        Vec3d markerPos = target.markerPos();
        double time = client.world.getTime() + context.tickDelta();
        float pulseScale = 0.92F + (float) ((Math.sin(time * 0.32D) + 1.0D) * 0.04D);
        float halfSize = MARKER_SIZE * pulseScale * 0.5F;

        MatrixStack matrices = context.matrixStack();
        VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getEntityTranslucent(MARKER_TEXTURE));
        matrices.push();
        matrices.translate(
                markerPos.x - cameraPos.x,
                markerPos.y - cameraPos.y,
                markerPos.z - cameraPos.z
        );
        matrices.multiply(context.camera().getRotation());

        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        vertices.vertex(positionMatrix, -halfSize, -halfSize, 0.0F)
                .color(255, 255, 255, 230)
                .texture(0.0F, 1.0F)
                .overlay(0, 0)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0.0F, 1.0F, 0.0F)
                .next();
        vertices.vertex(positionMatrix, halfSize, -halfSize, 0.0F)
                .color(255, 255, 255, 230)
                .texture(1.0F, 1.0F)
                .overlay(0, 0)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0.0F, 1.0F, 0.0F)
                .next();
        vertices.vertex(positionMatrix, halfSize, halfSize, 0.0F)
                .color(255, 255, 255, 230)
                .texture(1.0F, 0.0F)
                .overlay(0, 0)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0.0F, 1.0F, 0.0F)
                .next();
        vertices.vertex(positionMatrix, -halfSize, halfSize, 0.0F)
                .color(255, 255, 255, 230)
                .texture(0.0F, 0.0F)
                .overlay(0, 0)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0.0F, 1.0F, 0.0F)
                .next();
        matrices.pop();
    }

    private record Target(BlockPos blockPos, int cornerIndex, Vec3d markerPos) {
    }
}
