package ru.siyoga.legacyofthelucii.network;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsOrbitDamageAbility;
import ru.siyoga.legacyofthelucii.royalarms.orbit.RoyalArmsOrbitState;
import java.util.List;
public final class LuciiNetwork {
    public static final Identifier STATE_SYNC_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "state_sync");
    public static final Identifier ROYAL_ARMS_TOGGLE_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_toggle");
    public static final Identifier ROYAL_ARMS_FILTER_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_filter");
    public static final Identifier ROYAL_ARMS_VISUAL_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_visual");
    public static final Identifier ROYAL_ARMS_ORBIT_STATE_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_orbit_state");
    public static final Identifier ROYAL_ARMS_WALL_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_wall");
    public static final Identifier ROYAL_ARMS_WARP_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_warp");
    public static final Identifier ROYAL_ARMS_WARP_TRAIL_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_warp_trail");
    public static final Identifier ARDYN_WARP_CHARGE_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_warp_charge");
    public static final Identifier ARDYN_BARRAGE_VISUAL_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_barrage_visual");
    public static final Identifier ROYAL_ARMS_BIND_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_bind");
    public static final Identifier ROYAL_ARMS_BIND_VISUAL_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_bind_visual");
    public static final int ROYAL_ARMS_BIND_TOGGLE_ACTION = 0;
    public static final int ROYAL_ARMS_BIND_CONFIRM_ACTION = 1;
    public static final Identifier ARDYN_SHADOW_STEP_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_shadow_step");
    public static final Identifier ARDYN_SHADOW_STEP_VISUAL_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_shadow_step_visual");
    public static final Identifier ARDYN_POINT_WARP_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_point_warp");
    public static final Identifier ARDYN_POINT_WARP_VISUAL_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_point_warp_visual");
    public static final int ARDYN_POINT_WARP_START_ACTION = 0;
    public static final int ARDYN_POINT_WARP_STOP_ACTION = 1;
    public static final Identifier ROYAL_ARMS_WALL_ANIMATION_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_wall_animation");
    public static final Identifier DEBUG_DEMONIZE_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "debug_demonize");
    private LuciiNetwork() {
    }
    public static void sendState(ServerPlayerEntity player) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(state.legacy().id());
        buf.writeVarInt(state.mana());
        buf.writeVarInt(state.maxMana());
        buf.writeBoolean(state.royalArmsActive());
        buf.writeVarInt(state.ardynWarpCharges());
        // Local Overkill state travels in the regular authoritative state packet.
        // This packet is sent on join, respawn and every second, so reconnects
        // cannot permanently lose the HUD flag if a one-shot visual packet races
        // with client world/player creation.
        buf.writeBoolean(state.ardynOverkillActive());
        ServerPlayNetworking.send(player, STATE_SYNC_PACKET, buf);
    }
    public static void broadcastRoyalArmsVisual(ServerPlayerEntity owner) {
        MinecraftServer server = owner.getServer();
        if (server == null) {
            return;
        }

        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(viewer, ROYAL_ARMS_VISUAL_PACKET, createRoyalArmsVisualPacket(owner));
        }
    }
    public static void broadcastRoyalArmsOrbitState(ServerPlayerEntity owner) {
        MinecraftServer server = owner.getServer();
        if (server == null) {
            return;
        }

        RoyalArmsOrbitState.Snapshot snapshot = RoyalArmsOrbitDamageAbility.snapshot(owner);
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(owner.getUuid());
            writeOrbitMotion(buf, snapshot);
            ServerPlayNetworking.send(viewer, ROYAL_ARMS_ORBIT_STATE_PACKET, buf);
        }
    }
    public static void broadcastRoyalArmsWallAnimation(
            ServerWorld world,
            ServerPlayerEntity owner,
            BlockPos pos,
            BlockState sourceState,
            boolean appearing
    ) {
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(owner.getUuid());
            buf.writeBlockPos(pos);
            buf.writeVarInt(Block.getRawIdFromState(sourceState));
            buf.writeBoolean(appearing);
            ServerPlayNetworking.send(viewer, ROYAL_ARMS_WALL_ANIMATION_PACKET, buf);
        }
    }
    public static void broadcastRoyalArmsWarpTrail(ServerWorld world, ServerPlayerEntity owner, Vec3d from, Vec3d to, LuciiLegacy legacy) {
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(owner.getUuid());
            buf.writeString(legacy.id());
            buf.writeDouble(from.x);
            buf.writeDouble(from.y);
            buf.writeDouble(from.z);
            buf.writeDouble(to.x);
            buf.writeDouble(to.y);
            buf.writeDouble(to.z);
            buf.writeFloat(owner.getYaw());
            ServerPlayNetworking.send(viewer, ROYAL_ARMS_WARP_TRAIL_PACKET, buf);
        }
    }
    public static void broadcastArdynWarpCharge(ServerWorld world, ServerPlayerEntity owner, boolean active) {
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(owner.getUuid());
            buf.writeBoolean(active);
            ServerPlayNetworking.send(viewer, ARDYN_WARP_CHARGE_PACKET, buf);
        }
    }
    public static void broadcastArdynBarrage(ServerWorld world, ServerPlayerEntity owner, boolean active) {
        broadcastArdynBarrage(world, owner.getUuid(), active);
    }
    public static void broadcastArdynBarrage(ServerWorld world, java.util.UUID ownerUuid, boolean active) {
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(ownerUuid);
            buf.writeBoolean(active);
            ServerPlayNetworking.send(viewer, ARDYN_BARRAGE_VISUAL_PACKET, buf);
        }
    }
    public static void broadcastArdynShadowStep(ServerWorld world, ServerPlayerEntity owner, boolean active) {
        broadcastArdynShadowStep(world, owner.getUuid(), active);
    }
    public static void broadcastArdynShadowStep(ServerWorld world, java.util.UUID ownerUuid, boolean active) {
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(ownerUuid);
            buf.writeBoolean(active);
            ServerPlayNetworking.send(viewer, ARDYN_SHADOW_STEP_VISUAL_PACKET, buf);
        }
    }
    public static void broadcastArdynPointWarp(ServerWorld world, ServerPlayerEntity owner, boolean active) {
        List<ServerPlayerEntity> viewers = world.getPlayers();
        LegacyOfTheLucii.LOGGER.info("[PointWarp/SERVER] Broadcasting visual active={} for {} to {} viewer(s).",
                active, owner.getGameProfile().getName(), viewers.size());
        for (ServerPlayerEntity viewer : viewers) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(owner.getUuid());
            buf.writeBoolean(active);
            ServerPlayNetworking.send(viewer, ARDYN_POINT_WARP_VISUAL_PACKET, buf);
        }
    }
    public static void broadcastRoyalArmsBindVisual(
            ServerWorld world,
            ServerPlayerEntity owner,
            net.minecraft.entity.LivingEntity target,
            boolean active,
            boolean impaled,
            LuciiLegacy legacy,
            List<ItemStack> stacks
    ) {
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            PacketByteBuf buf = PacketByteBufs.create();
            Vec3d center = target.getBoundingBox().getCenter();
            buf.writeUuid(owner.getUuid());
            buf.writeVarInt(target.getId());
            buf.writeDouble(center.x);
            buf.writeDouble(center.y);
            buf.writeDouble(center.z);
            buf.writeBoolean(active);
            buf.writeBoolean(impaled);
            buf.writeString(legacy.id());
            buf.writeVarInt(stacks.size());
            for (ItemStack stack : stacks) {
                buf.writeItemStack(stack);
            }
            ServerPlayNetworking.send(viewer, ROYAL_ARMS_BIND_VISUAL_PACKET, buf);
        }
    }
    private static PacketByteBuf createRoyalArmsVisualPacket(ServerPlayerEntity owner) {
        LuciiPlayerState state = LuciiPlayerStates.get(owner);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(owner.getUuid());
        buf.writeBoolean(state.royalArmsActive());
        RoyalArmsOrbitState.Snapshot snapshot = RoyalArmsOrbitDamageAbility.snapshot(owner);
        if (!state.royalArmsActive()) {
            // The client starts its recall from this exact authoritative orbit pose.
            writeOrbitMotion(buf, snapshot);
            writeOrbitSlots(buf, snapshot);
            return buf;
        }
        buf.writeString(state.legacy().id());
        buf.writeVarInt(state.ardynWarpCharges());
        writeOrbitMotion(buf, snapshot);
        writeOrbitSlots(buf, snapshot);
        return buf;
    }

    private static void writeOrbitSlots(PacketByteBuf buf, RoyalArmsOrbitState.Snapshot snapshot) {
        buf.writeVarInt(snapshot.slots().size());
        for (RoyalArmsOrbitState.SlotSnapshot slot : snapshot.slots()) {
            buf.writeString(slot.key());
            buf.writeVarInt(slot.sourceSlot());
            buf.writeItemStack(slot.stack());
            buf.writeVarInt(slot.index());
            buf.writeFloat(slot.baseAngle());
            buf.writeFloat(slot.targetBaseAngle());
            buf.writeFloat(slot.innerProgress());
            buf.writeBoolean(slot.innerTarget());
            buf.writeVarInt(slot.spawnTicks());
        }
    }

    private static void writeOrbitMotion(PacketByteBuf buf, RoyalArmsOrbitState.Snapshot snapshot) {
        buf.writeLong(snapshot.serverWorldTime());
        buf.writeDouble(snapshot.previousPhase());
        buf.writeDouble(snapshot.phase());
        buf.writeFloat(snapshot.speed());
        buf.writeBoolean(snapshot.paused());
        buf.writeVarInt(snapshot.activeTicks());
    }
}
