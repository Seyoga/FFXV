package ru.siyoga.legacyofthelucii.client.timeslow;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynTimeSlowAbility;
import ru.siyoga.legacyofthelucii.timeslow.ArdynTimeSlowNetwork;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ArdynTimeSlowClient {
    private static final Set<UUID> ACTIVE_FIELDS = new HashSet<>();

    private static boolean rightMouseWasDown;
    private static int slowPhase;
    private static boolean localVerticalSlowWasActive;
    private static boolean localVerticalCaptured;
    private static boolean localJumpedThisTick;
    private static double localVerticalVelocityAtTickStart;

    private ArdynTimeSlowClient() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(ArdynTimeSlowClient::beginTick);
        ClientTickEvents.END_CLIENT_TICK.register(ArdynTimeSlowClient::endTick);
    }

    public static void setFieldState(UUID ownerUuid, boolean active) {
        if (active) {
            boolean wasEmpty = ACTIVE_FIELDS.isEmpty();
            ACTIVE_FIELDS.add(ownerUuid);
            if (wasEmpty) {
                slowPhase = ArdynTimeSlowAbility.SLOW_FACTOR - 1;
            }
        } else {
            ACTIVE_FIELDS.remove(ownerUuid);
            if (ACTIVE_FIELDS.isEmpty()) {
                slowPhase = 0;
            }
        }
    }

    public static void clear() {
        ACTIVE_FIELDS.clear();
        rightMouseWasDown = false;
        slowPhase = 0;
        localVerticalSlowWasActive = false;
        localVerticalCaptured = false;
        localJumpedThisTick = false;
        localVerticalVelocityAtTickStart = 0.0D;
    }

    public static boolean shouldSkipEntityTick(MinecraftClient client, Entity entity) {
        if (!isAffected(client, entity)) {
            return false;
        }
        return slowPhase != 0;
    }

    public static boolean shouldSmoothRender(MinecraftClient client, Entity entity) {
        return isAffected(client, entity);
    }

    public static float slowRenderTickDelta(float vanillaTickDelta) {
        float clamped = Math.max(0.0F, Math.min(1.0F, vanillaTickDelta));
        return Math.min(1.0F,
                (slowPhase + clamped) / (float) ArdynTimeSlowAbility.SLOW_FACTOR);
    }

    public static boolean isLocalPlayerSlowed(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return false;
        }
        return ACTIVE_FIELDS.contains(client.player.getUuid()) || isInsideAnyField(client, client.player);
    }

    public static boolean isLocalOwnerConcentrating(MinecraftClient client) {
        return client.player != null && ACTIVE_FIELDS.contains(client.player.getUuid());
    }

    public static void onLocalPlayerJump(MinecraftClient client) {
        if (!isLocalPlayerSlowed(client) || client.player == null
                || client.player.getAbilities().flying
                || client.player.isFallFlying()
                || client.player.isTouchingWater()
                || client.player.isClimbing()) {
            return;
        }

        net.minecraft.util.math.Vec3d velocity = client.player.getVelocity();
        double slowedY = ArdynTimeSlowAbility.slowInitialJumpVelocity(velocity.y);
        client.player.setVelocity(velocity.x, slowedY, velocity.z);
        localJumpedThisTick = true;
    }

    private static void beginTick(MinecraftClient client) {
        if (client.world == null || ACTIVE_FIELDS.isEmpty()) {
            slowPhase = 0;
            resetLocalVerticalSlow();
            return;
        }
        slowPhase = (slowPhase + 1) % ArdynTimeSlowAbility.SLOW_FACTOR;
        captureLocalVerticalVelocity(client);
    }

    private static void endTick(MinecraftClient client) {
        if (client.getNetworkHandler() == null || client.world == null || client.player == null) {
            clear();
            return;
        }

        tickLocalVerticalSlow(client);

        boolean rightMouseDown = client.currentScreen == null && isRightMouseDown(client);
        if (rightMouseDown != rightMouseWasDown) {
            sendHeldState(rightMouseDown);
            rightMouseWasDown = rightMouseDown;
        }
    }

    private static void captureLocalVerticalVelocity(MinecraftClient client) {
        localJumpedThisTick = false;
        localVerticalCaptured = false;

        if (!isLocalPlayerSlowed(client)
                || client.player == null
                || client.player.getAbilities().flying
                || client.player.isFallFlying()
                || client.player.isTouchingWater()
                || client.player.isClimbing()) {
            localVerticalSlowWasActive = false;
            return;
        }

        net.minecraft.util.math.Vec3d velocity = client.player.getVelocity();
        if (!localVerticalSlowWasActive && !client.player.isOnGround()) {
            // Entering a time field while already airborne must slow the velocity itself,
            // otherwise a player who was falling before the field started keeps the old
            // full-speed fall until the next collision/server correction.
            velocity = new net.minecraft.util.math.Vec3d(
                    velocity.x,
                    velocity.y / ArdynTimeSlowAbility.SLOW_FACTOR,
                    velocity.z
            );
            client.player.setVelocity(velocity);
        }

        localVerticalSlowWasActive = true;
        localVerticalCaptured = true;
        localVerticalVelocityAtTickStart = velocity.y;
    }

    private static void tickLocalVerticalSlow(MinecraftClient client) {
        if (!isLocalPlayerSlowed(client)
                || client.player == null
                || client.player.getAbilities().flying
                || client.player.isFallFlying()
                || client.player.isTouchingWater()
                || client.player.isClimbing()) {
            resetLocalVerticalSlow();
            return;
        }

        if (client.player.isOnGround()) {
            localVerticalSlowWasActive = true;
            localVerticalCaptured = false;
            localJumpedThisTick = false;
            return;
        }

        net.minecraft.util.math.Vec3d velocity = client.player.getVelocity();
        double baseY;
        if (localJumpedThisTick) {
            // jump() has already replaced vanilla 0.42-ish velocity with its temporal
            // equivalent. Continue from that slowed value.
            baseY = velocity.y;
        } else if (localVerticalCaptured) {
            // Ignore the full vanilla gravity step that ran this client tick and advance
            // vertical physics by only 1/SLOW_FACTOR of a normal tick instead. This keeps
            // both jumping and ordinary falling smooth in multiplayer.
            baseY = localVerticalVelocityAtTickStart;
        } else {
            baseY = velocity.y;
        }

        double slowedY = ArdynTimeSlowAbility.nextTemporalJumpVelocity(baseY);
        client.player.setVelocity(velocity.x, slowedY, velocity.z);
        localVerticalVelocityAtTickStart = slowedY;
        localVerticalCaptured = true;
        localVerticalSlowWasActive = true;
        localJumpedThisTick = false;
    }

    private static void resetLocalVerticalSlow() {
        localVerticalSlowWasActive = false;
        localVerticalCaptured = false;
        localJumpedThisTick = false;
        localVerticalVelocityAtTickStart = 0.0D;
    }

    private static boolean isAffected(MinecraftClient client, Entity entity) {
        if (client.player == null || client.world == null || ACTIVE_FIELDS.isEmpty()) {
            return false;
        }
        if (entity == client.player || ACTIVE_FIELDS.contains(entity.getUuid())) {
            return false;
        }
        return isInsideAnyField(client, entity);
    }

    private static boolean isInsideAnyField(MinecraftClient client, Entity entity) {
        for (UUID ownerUuid : ACTIVE_FIELDS) {
            PlayerEntity owner = client.world.getPlayerByUuid(ownerUuid);
            if (owner != null
                    && owner.squaredDistanceTo(entity) <= ArdynTimeSlowAbility.RADIUS_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRightMouseDown(MinecraftClient client) {
        if (client.getWindow() == null) {
            return false;
        }
        return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                == GLFW.GLFW_PRESS;
    }

    private static void sendHeldState(boolean held) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(held ? ArdynTimeSlowNetwork.START_ACTION : ArdynTimeSlowNetwork.STOP_ACTION);
        ClientPlayNetworking.send(ArdynTimeSlowNetwork.ACTION_PACKET, buf);
    }
}
