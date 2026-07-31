package ru.siyoga.legacyofthelucii.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;

public final class RoyalArmsGuardNetwork {
    public static final Identifier TOGGLE_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_guard_toggle");
    public static final Identifier STATE_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_guard_state");
    public static final Identifier BLOCK_VISUAL_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_guard_block");

    private RoyalArmsGuardNetwork() {
    }

    public static void sendState(ServerPlayerEntity player, boolean active) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        ServerPlayNetworking.send(player, STATE_PACKET, buf);
    }

    public static void broadcastBlock(ServerWorld world, ServerPlayerEntity owner, Vec3d interceptPos, Vec3d incomingVelocity) {
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(owner.getUuid());
            buf.writeDouble(interceptPos.x);
            buf.writeDouble(interceptPos.y);
            buf.writeDouble(interceptPos.z);
            buf.writeDouble(incomingVelocity.x);
            buf.writeDouble(incomingVelocity.y);
            buf.writeDouble(incomingVelocity.z);
            ServerPlayNetworking.send(viewer, BLOCK_VISUAL_PACKET, buf);
        }
    }
}
