package ru.siyoga.legacyofthelucii.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsGuardAbility;

public final class RoyalArmsGuardNetwork {
    public static final Identifier TOGGLE_PACKET = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "royal_arms_guard_toggle"
    );
    public static final Identifier STATE_PACKET = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "royal_arms_guard_state"
    );
    public static final Identifier BLOCK_VISUAL_PACKET = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "royal_arms_guard_block"
    );
    public static final Identifier EXPLOSION_VISUAL_PACKET = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "royal_arms_guard_explosion"
    );

    private RoyalArmsGuardNetwork() {
    }

    public static void sendState(
            ServerPlayerEntity viewer,
            ServerPlayerEntity owner,
            boolean active
    ) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(owner.getUuid());
        buf.writeBoolean(active);
        ServerPlayNetworking.send(viewer, STATE_PACKET, buf);
    }

    public static void broadcastState(
            ServerWorld world,
            ServerPlayerEntity owner,
            boolean active
    ) {
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            sendState(viewer, owner, active);
        }
    }

    public static void sendAllStates(ServerPlayerEntity viewer) {
        if (!(viewer.getWorld() instanceof ServerWorld world)) {
            return;
        }

        for (ServerPlayerEntity owner : world.getPlayers()) {
            sendState(
                    viewer,
                    owner,
                    RoyalArmsGuardAbility.isActive(owner.getUuid())
            );
        }
    }

    public static void broadcastBlock(
            ServerWorld world,
            ServerPlayerEntity owner,
            Vec3d interceptPos,
            Vec3d incomingVelocity,
            int travelTicks,
            int layer
    ) {
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(owner.getUuid());
            buf.writeDouble(interceptPos.x);
            buf.writeDouble(interceptPos.y);
            buf.writeDouble(interceptPos.z);
            buf.writeDouble(incomingVelocity.x);
            buf.writeDouble(incomingVelocity.y);
            buf.writeDouble(incomingVelocity.z);
            buf.writeVarInt(travelTicks);
            buf.writeVarInt(layer);
            ServerPlayNetworking.send(viewer, BLOCK_VISUAL_PACKET, buf);
        }
    }

    public static void broadcastExplosionGuard(
            ServerWorld world,
            ServerPlayerEntity owner,
            int itemCount,
            float protection
    ) {
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(owner.getUuid());
            buf.writeVarInt(itemCount);
            buf.writeFloat(protection);
            ServerPlayNetworking.send(viewer, EXPLOSION_VISUAL_PACKET, buf);
        }
    }
}
