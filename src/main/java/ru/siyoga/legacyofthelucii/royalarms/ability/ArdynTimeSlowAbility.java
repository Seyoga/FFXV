package ru.siyoga.legacyofthelucii.royalarms.ability;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.fluid.Fluid;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;
import ru.siyoga.legacyofthelucii.timeslow.ArdynTimeSlowNetwork;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ArdynTimeSlowAbility {
    public static final double RADIUS = 48.0D;
    public static final double RADIUS_SQUARED = RADIUS * RADIUS;
    public static final int SLOW_FACTOR = 4;
    public static final int FULL_MANA_DURATION_TICKS = 200;
    public static final double MANA_PER_TICK = 0.8D;
    private static final double TEMPORAL_JUMP_GRAVITY = 0.08D / (SLOW_FACTOR * SLOW_FACTOR);
    private static final double TEMPORAL_JUMP_DRAG = Math.pow(0.98D, 1.0D / SLOW_FACTOR);

    private static final int MANA_DRAIN_INTERVAL_TICKS = 5;
    private static final int MANA_DRAIN_PER_INTERVAL = 4;
    private static final UUID MOVEMENT_SLOW_UUID = UUID.fromString("8eb21fa7-1a8a-4b5d-b58b-49706357b04f");
    private static final EntityAttributeModifier MOVEMENT_SLOW = new EntityAttributeModifier(
            MOVEMENT_SLOW_UUID,
            "Ardyn temporal concentration",
            -0.75D,
            EntityAttributeModifier.Operation.MULTIPLY_TOTAL
    );

    private static final Map<UUID, ActiveField> ACTIVE_FIELDS = new HashMap<>();
    private static final ThreadLocal<Boolean> SCHEDULE_SCALING_BYPASS = ThreadLocal.withInitial(() -> false);

    private ArdynTimeSlowAbility() {
    }

    public static void setHeld(ServerPlayerEntity player, boolean held) {
        if (!held) {
            clearPlayer(player, "right mouse released");
            return;
        }
        if (ACTIVE_FIELDS.containsKey(player.getUuid())) {
            return;
        }

        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() != LuciiLegacy.ARDYN) {
            return;
        }
        if (state.ardynOverkillActive() || player.isRemoved() || player.isDead() || player.isSpectator()) {
            return;
        }
        if (!player.getAbilities().creativeMode && state.mana() <= 0) {
            player.sendMessage(Text.translatable("message.legacyofthelucii.royal_arms.not_enough_mana"), true);
            return;
        }

        ACTIVE_FIELDS.put(player.getUuid(), new ActiveField(player.getServerWorld(), player.getPos()));
        broadcastFieldState(player.getServer(), player.getUuid(), true);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveField>> iterator = ACTIVE_FIELDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveField> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            ActiveField field = entry.getValue();

            if (player == null || player.isRemoved() || player.isDead() || player.isSpectator()) {
                iterator.remove();
                broadcastFieldState(server, entry.getKey(), false);
                continue;
            }

            LuciiPlayerState state = LuciiPlayerStates.get(player);
            if (state.legacy() != LuciiLegacy.ARDYN
                    || state.ardynOverkillActive()
                    || player.getServerWorld() != field.world) {
                iterator.remove();
                broadcastFieldState(server, entry.getKey(), false);
                continue;
            }

            field.center = player.getPos();
            field.age++;
            tickOwnerTemporalJump(player, field);

            if (!player.getAbilities().creativeMode && field.age % MANA_DRAIN_INTERVAL_TICKS == 0) {
                if (state.mana() < MANA_DRAIN_PER_INTERVAL) {
                    int remaining = state.mana();
                    if (remaining > 0) {
                        state.spendMana(remaining);
                        LuciiNetwork.sendState(player);
                    }
                    iterator.remove();
                    broadcastFieldState(server, entry.getKey(), false);
                    player.sendMessage(Text.translatable("message.legacyofthelucii.ardyn.time_slow.ended"), true);
                    continue;
                }

                if (!state.spendMana(MANA_DRAIN_PER_INTERVAL)) {
                    iterator.remove();
                    broadcastFieldState(server, entry.getKey(), false);
                    continue;
                }
                LuciiNetwork.sendState(player);

                if (state.mana() <= 0) {
                    iterator.remove();
                    broadcastFieldState(server, entry.getKey(), false);
                    player.sendMessage(Text.translatable("message.legacyofthelucii.ardyn.time_slow.ended"), true);
                }
            }
        }

        updatePlayerMovementModifiers(server);
    }

    public static boolean isActive(UUID playerUuid) {
        return ACTIVE_FIELDS.containsKey(playerUuid);
    }

    public static void onOwnerJump(ServerPlayerEntity player) {
        ActiveField field = ACTIVE_FIELDS.get(player.getUuid());
        if (field == null || player.getAbilities().flying || player.isFallFlying() || player.isTouchingWater() || player.isClimbing()) {
            return;
        }

        Vec3d velocity = player.getVelocity();
        double slowedY = velocity.y / SLOW_FACTOR;
        player.setVelocity(velocity.x, slowedY, velocity.z);
        field.temporalJumpActive = true;
        field.temporalJumpVelocity = slowedY;
    }

    public static double slowInitialJumpVelocity(double velocityY) {
        return velocityY / SLOW_FACTOR;
    }

    public static double nextTemporalJumpVelocity(double velocityY) {
        return (velocityY - TEMPORAL_JUMP_GRAVITY) * TEMPORAL_JUMP_DRAG;
    }

    public static boolean shouldSkipEntityTick(ServerWorld world, Entity entity) {
        // Player movement is client-predicted and sent to the server every tick. Skipping
        // ServerPlayerEntity ticks produces corrections/jitter and does not reliably slow
        // falling. Players stay on the normal tick rate; their movement speed and local
        // vertical physics are slowed separately.
        if (ACTIVE_FIELDS.isEmpty() || entity instanceof PlayerEntity || isImmuneEntity(entity)) {
            return false;
        }
        return isEntityInsideField(world, entity) && !isAdvanceTick(world);
    }

    public static boolean shouldSkipPositionTick(ServerWorld world, BlockPos pos) {
        return !ACTIVE_FIELDS.isEmpty()
                && isPositionInsideField(world, Vec3d.ofCenter(pos))
                && !isAdvanceTick(world);
    }

    public static boolean isEntitySlowed(ServerWorld world, Entity entity) {
        return !ACTIVE_FIELDS.isEmpty()
                && !isImmuneEntity(entity)
                && isEntityInsideField(world, entity);
    }

    public static int scaleScheduledDelay(ServerWorld world, BlockPos pos, int delay) {
        if (delay <= 0 || SCHEDULE_SCALING_BYPASS.get() || !isPositionInsideField(world, Vec3d.ofCenter(pos))) {
            return delay;
        }
        if (delay > Integer.MAX_VALUE / SLOW_FACTOR) {
            return Integer.MAX_VALUE;
        }
        return delay * SLOW_FACTOR;
    }

    public static void scheduleUnscaledBlockTick(ServerWorld world, BlockPos pos, Block block, int delay) {
        boolean previous = SCHEDULE_SCALING_BYPASS.get();
        SCHEDULE_SCALING_BYPASS.set(true);
        try {
            world.scheduleBlockTick(pos, block, delay);
        } finally {
            SCHEDULE_SCALING_BYPASS.set(previous);
        }
    }

    public static void scheduleUnscaledFluidTick(ServerWorld world, BlockPos pos, Fluid fluid, int delay) {
        boolean previous = SCHEDULE_SCALING_BYPASS.get();
        SCHEDULE_SCALING_BYPASS.set(true);
        try {
            world.scheduleFluidTick(pos, fluid, delay);
        } finally {
            SCHEDULE_SCALING_BYPASS.set(previous);
        }
    }

    public static void syncViewer(ServerPlayerEntity viewer) {
        for (UUID ownerUuid : ACTIVE_FIELDS.keySet()) {
            ArdynTimeSlowNetwork.sendFieldState(viewer, ownerUuid, true);
        }
    }

    public static void clearPlayer(ServerPlayerEntity player, String reason) {
        if (ACTIVE_FIELDS.remove(player.getUuid()) != null) {
            broadcastFieldState(player.getServer(), player.getUuid(), false);
            removeMovementSlow(player);
        }
    }

    public static void clearAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            removeMovementSlow(player);
        }
        for (UUID ownerUuid : java.util.List.copyOf(ACTIVE_FIELDS.keySet())) {
            broadcastFieldState(server, ownerUuid, false);
        }
        ACTIVE_FIELDS.clear();
    }

    private static boolean isAdvanceTick(ServerWorld world) {
        return Math.floorMod(world.getTime(), SLOW_FACTOR) == 0;
    }

    private static boolean isEntityInsideField(ServerWorld world, Entity entity) {
        Vec3d pos = entity.getPos();
        for (ActiveField field : ACTIVE_FIELDS.values()) {
            if (field.world == world && field.center.squaredDistanceTo(pos) <= RADIUS_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPositionInsideField(ServerWorld world, Vec3d pos) {
        for (ActiveField field : ACTIVE_FIELDS.values()) {
            if (field.world == world && field.center.squaredDistanceTo(pos) <= RADIUS_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private static boolean isImmuneEntity(Entity entity) {
        return ACTIVE_FIELDS.containsKey(entity.getUuid());
    }

    private static void tickOwnerTemporalJump(ServerPlayerEntity player, ActiveField field) {
        if (!field.temporalJumpActive) {
            return;
        }
        if (player.isOnGround() || player.getAbilities().flying || player.isFallFlying() || player.isTouchingWater() || player.isClimbing()) {
            field.temporalJumpActive = false;
            return;
        }

        field.temporalJumpVelocity = nextTemporalJumpVelocity(field.temporalJumpVelocity);
        Vec3d velocity = player.getVelocity();
        player.setVelocity(velocity.x, field.temporalJumpVelocity, velocity.z);
    }

    private static void updatePlayerMovementModifiers(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean slowed = isActive(player.getUuid()) || isEntitySlowed(player.getServerWorld(), player);
            EntityAttributeInstance movementSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (movementSpeed == null) {
                continue;
            }

            if (slowed) {
                if (movementSpeed.getModifier(MOVEMENT_SLOW_UUID) == null) {
                    movementSpeed.addTemporaryModifier(MOVEMENT_SLOW);
                }
            } else {
                movementSpeed.removeModifier(MOVEMENT_SLOW_UUID);
            }
        }
    }

    private static void removeMovementSlow(ServerPlayerEntity player) {
        EntityAttributeInstance movementSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(MOVEMENT_SLOW_UUID);
        }
    }

    private static void broadcastFieldState(MinecraftServer server, UUID ownerUuid, boolean active) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            ArdynTimeSlowNetwork.sendFieldState(viewer, ownerUuid, active);
        }
    }

    private static final class ActiveField {
        private final ServerWorld world;
        private Vec3d center;
        private int age;
        private boolean temporalJumpActive;
        private double temporalJumpVelocity;

        private ActiveField(ServerWorld world, Vec3d center) {
            this.world = world;
            this.center = center;
        }
    }
}
