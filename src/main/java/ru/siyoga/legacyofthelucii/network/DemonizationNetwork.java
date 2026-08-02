package ru.siyoga.legacyofthelucii.network;

import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.effect.Demonization;

public final class DemonizationNetwork {
    public static final Identifier STATE_PACKET =
            new Identifier(LegacyOfTheLucii.MOD_ID, "demonization_state");

    private static boolean registered;

    private DemonizationNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            if (trackedEntity instanceof MobEntity mob && Demonization.isDemonized(mob)) {
                send(player, mob, true);
            }
        });

        LegacyOfTheLucii.LOGGER.info("Demonization network: server tracking sync registered.");
    }

    public static void broadcast(ServerWorld world, MobEntity mob, boolean demonized) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            send(player, mob, demonized);
        }
    }

    public static void send(ServerPlayerEntity player, MobEntity mob, boolean demonized) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(mob.getUuid());
        buf.writeBoolean(demonized);
        ServerPlayNetworking.send(player, STATE_PACKET, buf);
    }
}
