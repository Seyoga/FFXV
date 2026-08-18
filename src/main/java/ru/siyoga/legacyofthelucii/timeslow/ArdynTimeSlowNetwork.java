package ru.siyoga.legacyofthelucii.timeslow;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;

import java.util.UUID;

public final class ArdynTimeSlowNetwork {
    public static final Identifier ACTION_PACKET =
            new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_time_slow_action");
    public static final Identifier FIELD_STATE_PACKET =
            new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_time_slow_field");

    public static final int START_ACTION = 0;
    public static final int STOP_ACTION = 1;

    private ArdynTimeSlowNetwork() {
    }

    public static void sendFieldState(ServerPlayerEntity viewer, UUID ownerUuid, boolean active) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(ownerUuid);
        buf.writeBoolean(active);
        ServerPlayNetworking.send(viewer, FIELD_STATE_PACKET, buf);
    }
}
