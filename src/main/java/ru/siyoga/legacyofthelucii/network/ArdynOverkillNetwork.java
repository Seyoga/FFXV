package ru.siyoga.legacyofthelucii.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;

public final class ArdynOverkillNetwork {
    public static final Identifier STATE_PACKET = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "ardyn_overkill_state"
    );

    private ArdynOverkillNetwork() {
    }

    public static void broadcastState(ServerPlayerEntity owner) {
        broadcastState(owner, LuciiPlayerStates.get(owner).ardynOverkillActive());
    }

    public static void broadcastState(ServerPlayerEntity owner, boolean active) {
        MinecraftServer server = owner.getServer();
        if (server == null) {
            return;
        }

        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            sendState(viewer, owner.getUuid(), active);
        }
    }

    public static void sendAllStates(ServerPlayerEntity viewer) {
        MinecraftServer server = viewer.getServer();
        if (server == null) {
            return;
        }

        for (ServerPlayerEntity owner : server.getPlayerManager().getPlayerList()) {
            sendState(
                    viewer,
                    owner.getUuid(),
                    LuciiPlayerStates.get(owner).ardynOverkillActive()
            );
        }
    }

    private static void sendState(ServerPlayerEntity viewer, java.util.UUID ownerUuid, boolean active) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(ownerUuid);
        buf.writeBoolean(active);
        ServerPlayNetworking.send(viewer, STATE_PACKET, buf);
    }
}
