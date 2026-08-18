package ru.siyoga.legacyofthelucii.sniper;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;

import java.util.UUID;

public final class ArdynSniperNetwork {
    public static final Identifier ACTION_PACKET =
            new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_sniper_action");
    public static final Identifier STATE_PACKET =
            new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_sniper_state");
    public static final Identifier ANIMATION_PACKET =
            new Identifier(LegacyOfTheLucii.MOD_ID, "ardyn_sniper_animation");

    public static final int TOGGLE_ACTION = 0;
    public static final int SHOOT_ACTION = 1;

    public static final int ANIMATION_EQUIP = 0;
    public static final int ANIMATION_HOLD = 1;
    public static final int ANIMATION_SHOOT = 2;
    public static final int ANIMATION_UNEQUIP = 3;
    public static final int ANIMATION_CLEAR = 4;

    private ArdynSniperNetwork() {
    }

    public static void sendState(ServerPlayerEntity player, boolean active, int cooldownTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(player.getUuid());
        buf.writeBoolean(active);
        buf.writeVarInt(Math.max(0, cooldownTicks));
        ServerPlayNetworking.send(player, STATE_PACKET, buf);
    }

    public static void broadcastAnimation(ServerWorld world, UUID ownerUuid, int animation) {
        for (ServerPlayerEntity receiver : world.getPlayers()) {
            sendAnimation(receiver, ownerUuid, animation);
        }
    }

    public static void sendAnimation(ServerPlayerEntity receiver, UUID ownerUuid, int animation) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(ownerUuid);
        buf.writeVarInt(animation);
        ServerPlayNetworking.send(receiver, ANIMATION_PACKET, buf);
    }
}
