package ru.siyoga.legacyofthelucii.client.royalarms.noctis;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.gui.skilltree.ClientSkillTreeState;
import ru.siyoga.legacyofthelucii.client.royalarms.ardyn.ArdynPointWarpClient;
import ru.siyoga.legacyofthelucii.client.state.ClientLuciiState;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;
import ru.siyoga.legacyofthelucii.pointwarp.NoctisWarpPointFinder;
import ru.siyoga.legacyofthelucii.skilltree.LuciiSkill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-side discovery and rendering for Noctis high-surface point warp. */
public final class NoctisPointWarpClient {
    private static final Identifier MARKER_TEXTURE = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "textures/gui/pointwarp/noctis_warp.png"
    );
    private static final int RESCAN_INTERVAL_TICKS = 6;
    private static final double RESCAN_MOVE_DISTANCE_SQUARED = 9.0D;
    private static final double RESCAN_LOOK_DOT = 0.94D;
    private static final double GENERATION_CONE_DOT = 0.90D;
    private static final double MAX_AIM_ANGLE_COS = 0.985D;
    private static final double RENDER_ZONE_DOT = 0.05D;
    private static final double CACHE_ZONE_DOT = -0.10D;
    private static final boolean DEBUG = false;
    private static final float MARKER_SIZE = 0.72F;
    private static final float SELECTED_MARKER_SIZE = 0.94F;

    private static List<NoctisWarpPointFinder.WarpPoint> points = List.of();
    private static final Map<WarpPointKey, NoctisWarpPointFinder.WarpPoint> cachedPoints = new LinkedHashMap<>();
    private static NoctisWarpPointFinder.WarpPoint selected;
    private static int rescanTicks;
    private static int confirmTicks;
    private static Vec3d lastScanPosition;
    private static Vec3d lastScanLook;
    private static boolean hasScan;
    private static boolean registered;

    private NoctisPointWarpClient() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(NoctisPointWarpClient::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(NoctisPointWarpClient::renderMarkers);
        ClientPreAttackCallback.EVENT.register(NoctisPointWarpClient::onPreAttack);
    }

    public static void clear() {
        cachedPoints.clear();
        points = List.of();
        selected = null;
        rescanTicks = 0;
        confirmTicks = 0;
        lastScanPosition = null;
        lastScanLook = null;
        hasScan = false;
    }

    private static void tick(MinecraftClient client) {
        if (confirmTicks > 0) {
            confirmTicks--;
        }

        boolean active = canUse(client) && ArdynPointWarpClient.isPointWarpKeyDown(client);
        if (!active) {
            clearTargeting();
            return;
        }

        Vec3d origin = client.player.getPos();
        Vec3d look = client.player.getRotationVec(1.0F).normalize();
        pruneCache(client, origin, look);

        if (shouldRescan(origin, look)) {
            List<NoctisWarpPointFinder.WarpPoint> freshPoints = NoctisWarpPointFinder.find(
                    client.world,
                    origin,
                    look,
                    client.player
            );
            Vec3d eye = client.player.getEyePos();
            for (NoctisWarpPointFinder.WarpPoint point : freshPoints) {
                if (aimDot(eye, look, point.markerPos()) < GENERATION_CONE_DOT) {
                    continue;
                }
                cachedPoints.putIfAbsent(keyOf(point), point);
            }
            points = new ArrayList<>(cachedPoints.values());
            rescanTicks = RESCAN_INTERVAL_TICKS;
            lastScanPosition = origin;
            lastScanLook = look;
            hasScan = true;
            if (DEBUG) {
                long top = points.stream()
                        .filter(point -> point.type() == NoctisWarpPointFinder.WarpPointType.TOP)
                        .count();
                long ledge = points.size() - top;
                LegacyOfTheLucii.LOGGER.info(
                        "[NoctisPointWarp/CLIENT] candidates: TOP={}, LEDGE={}, accepted={}",
                        top, ledge, points.size()
                );
            }
        }
        selected = selectTarget(client);
    }

    private static boolean shouldRescan(Vec3d origin, Vec3d look) {
        if (!hasScan || lastScanPosition == null || lastScanLook == null) {
            return true;
        }
        if (rescanTicks > 0) {
            rescanTicks--;
            return false;
        }
        return origin.squaredDistanceTo(lastScanPosition) >= RESCAN_MOVE_DISTANCE_SQUARED
                || look.dotProduct(lastScanLook) < RESCAN_LOOK_DOT;
    }

    private static void pruneCache(MinecraftClient client, Vec3d origin, Vec3d look) {
        Vec3d eye = client.player.getEyePos();
        Iterator<Map.Entry<WarpPointKey, NoctisWarpPointFinder.WarpPoint>> iterator = cachedPoints.entrySet().iterator();
        while (iterator.hasNext()) {
            NoctisWarpPointFinder.WarpPoint point = iterator.next().getValue();
            Vec3d toPoint = point.markerPos().subtract(eye);
            if (toPoint.lengthSquared() > (NoctisWarpPointFinder.MAX_RANGE + 2.0D)
                    * (NoctisWarpPointFinder.MAX_RANGE + 2.0D)
                    || look.dotProduct(toPoint.normalize()) < CACHE_ZONE_DOT) {
                iterator.remove();
            }
        }
        points = new ArrayList<>(cachedPoints.values());
    }

    private static boolean onPreAttack(
            MinecraftClient client,
            net.minecraft.client.network.ClientPlayerEntity player,
            int clickCount
    ) {
        if (!canUse(client) || !ArdynPointWarpClient.isPointWarpKeyDown(client)) {
            return false;
        }
        if (clickCount <= 0 || confirmTicks > 0 || selected == null) {
            return false;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(selected.blockPos());
        ClientPlayNetworking.send(LuciiNetwork.NOCTIS_POINT_WARP_PACKET, buf);
        LegacyOfTheLucii.LOGGER.info(
                "[NoctisPointWarp/CLIENT] START packet sent: block={}",
                selected.blockPos()
        );
        confirmTicks = 8;
        selected = null;
        return true;
    }

    private static boolean canUse(MinecraftClient client) {
        return client.player != null
                && client.world != null
                && client.getNetworkHandler() != null
                && client.currentScreen == null
                && ClientLuciiState.legacy() == LuciiLegacy.NOCTIS
                && ClientLuciiState.royalArmsActive()
                && ClientSkillTreeState.isUnlocked(LuciiSkill.NOCTIS_WARP);
    }

    private static NoctisWarpPointFinder.WarpPoint selectTarget(MinecraftClient client) {
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0F).normalize();
        return points.stream()
                .map(point -> new AimCandidate(point, aimDot(eye, look, point.markerPos())))
                .filter(candidate -> candidate.dot() >= MAX_AIM_ANGLE_COS)
                .min(Comparator.comparingDouble(candidate -> 1.0D - candidate.dot()))
                .map(AimCandidate::point)
                .orElse(null);
    }

    private static double aimDot(Vec3d eye, Vec3d look, Vec3d marker) {
        Vec3d toMarker = marker.subtract(eye);
        if (toMarker.lengthSquared() < 0.0001D) {
            return 1.0D;
        }
        return look.dotProduct(toMarker.normalize());
    }

    private static void clearTargeting() {
        cachedPoints.clear();
        points = List.of();
        selected = null;
        rescanTicks = 0;
        lastScanPosition = null;
        lastScanLook = null;
        hasScan = false;
    }

    private static void renderMarkers(WorldRenderContext context) {
        if (points.isEmpty() || context.matrixStack() == null || context.consumers() == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canUse(client) || !ArdynPointWarpClient.isPointWarpKeyDown(client)) {
            return;
        }

        Vec3d cameraPos = context.camera().getPos();
        double time = client.world.getTime() + context.tickDelta();
        VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getEntityTranslucent(MARKER_TEXTURE));
        for (NoctisWarpPointFinder.WarpPoint point : points) {
            Vec3d toPoint = point.markerPos().subtract(client.player.getEyePos());
            if (toPoint.lengthSquared() <= 0.0001D
                    || client.player.getRotationVec(1.0F).dotProduct(toPoint.normalize()) < RENDER_ZONE_DOT) {
                continue;
            }
            boolean isSelected = point.blockPos().equals(selected == null ? null : selected.blockPos());
            float pulse = 0.94F + (float) ((Math.sin(time * 0.28D) + 1.0D) * 0.03D);
            float halfSize = (isSelected ? SELECTED_MARKER_SIZE : MARKER_SIZE) * pulse * 0.5F;
            int red = isSelected ? 180 : 95;
            int green = isSelected ? 235 : 170;
            int blue = 255;
            drawMarker(context, vertices, point.markerPos(), cameraPos, halfSize, red, green, blue);
        }
    }

    private static void drawMarker(
            WorldRenderContext context,
            VertexConsumer vertices,
            Vec3d markerPos,
            Vec3d cameraPos,
            float halfSize,
            int red,
            int green,
            int blue
    ) {
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(markerPos.x - cameraPos.x, markerPos.y - cameraPos.y, markerPos.z - cameraPos.z);
        matrices.multiply(context.camera().getRotation());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        vertex(vertices, matrix, -halfSize, -halfSize, red, green, blue);
        vertex(vertices, matrix, halfSize, -halfSize, red, green, blue);
        vertex(vertices, matrix, halfSize, halfSize, red, green, blue);
        vertex(vertices, matrix, -halfSize, halfSize, red, green, blue);
        matrices.pop();
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, float x, float y, int red, int green, int blue) {
        vertices.vertex(matrix, x, y, 0.0F)
                .color(red, green, blue, 235)
                .texture(x < 0.0F ? 0.0F : 1.0F, y < 0.0F ? 1.0F : 0.0F)
                .overlay(0, 0)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0.0F, 1.0F, 0.0F)
                .next();
    }

    private record AimCandidate(NoctisWarpPointFinder.WarpPoint point, double dot) {
    }

    private static WarpPointKey keyOf(NoctisWarpPointFinder.WarpPoint point) {
        return new WarpPointKey(point.blockPos(), point.type(), point.outward());
    }

    private record WarpPointKey(
            net.minecraft.util.math.BlockPos blockPos,
            NoctisWarpPointFinder.WarpPointType type,
            Direction outward
    ) {
    }
}
