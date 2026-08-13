package ru.siyoga.legacyofthelucii.darktornado;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;

import java.util.UUID;

/** Packet identifiers and small packet helpers for Ardyn's Dark Tornado. */
public final class DarkTornadoNetwork {
    public static final Identifier ACTION_PACKET =
            new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_dark_tornado");
    public static final Identifier TARGETING_STATE_PACKET =
            new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_dark_tornado_targeting");
    public static final Identifier VISUAL_STATE_PACKET =
            new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_dark_tornado_visual");
    public static final Identifier ANIMATION_PACKET =
            new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_dark_tornado_animation");

    public static final int TOGGLE_TARGETING_ACTION = 0;
    public static final int CONFIRM_TARGET_ACTION = 1;
    public static final int ANIMATION_START = 0;
    public static final int ANIMATION_CANCEL = 1;
    public static final int ANIMATION_CLICK = 2;

    private DarkTornadoNetwork() {
    }

    public static void sendTargetingState(ServerPlayerEntity player, boolean targeting) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(targeting);
        ServerPlayNetworking.send(player, TARGETING_STATE_PACKET, buf);
    }

    /**
     * Visual lifecycle packet. The client currently only exposes an animation hook;
     * the tornado particles and physics remain server-authoritative.
     */
    public static void broadcastVisualState(
            ServerWorld world,
            UUID ownerUuid,
            boolean active,
            Vec3d center
    ) {
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(ownerUuid);
            buf.writeBoolean(active);
            buf.writeDouble(center.x);
            buf.writeDouble(center.y);
            buf.writeDouble(center.z);
            ServerPlayNetworking.send(viewer, VISUAL_STATE_PACKET, buf);
        }
    }

    public static void broadcastAnimation(ServerWorld world, UUID ownerUuid, int animation) {
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(ownerUuid);
            buf.writeVarInt(animation);
            ServerPlayNetworking.send(viewer, ANIMATION_PACKET, buf);
        }
    }
}
