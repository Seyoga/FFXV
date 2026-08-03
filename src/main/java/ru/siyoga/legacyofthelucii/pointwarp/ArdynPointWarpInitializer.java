package ru.siyoga.legacyofthelucii.pointwarp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynPointWarpAbility;

/**
 * Standalone server entrypoint for Ardyn Point Warp.
 *
 * Keeping this separate from LegacyOfTheLucii makes it obvious in latest.log
 * whether the mechanic was loaded and avoids relying on a fragile text patch.
 */
public final class ArdynPointWarpInitializer implements ModInitializer {
    private static final String LOG = "[PointWarp/SERVER]";

    @Override
    public void onInitialize() {
        LegacyOfTheLucii.LOGGER.info("{} Initializer loaded. Registering packet {} and server tick.",
                LOG, LuciiNetwork.ARDYN_POINT_WARP_PACKET);

        ServerPlayNetworking.registerGlobalReceiver(
                LuciiNetwork.ARDYN_POINT_WARP_PACKET,
                ArdynPointWarpInitializer::handlePointWarpPacket
        );

        ServerTickEvents.END_SERVER_TICK.register(ArdynPointWarpAbility::tick);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LegacyOfTheLucii.LOGGER.info("{} Player disconnected: {}. Clearing active warp.",
                    LOG, handler.player.getGameProfile().getName());
            ArdynPointWarpAbility.clearAll(handler.player);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LegacyOfTheLucii.LOGGER.info("{} Server stopping. Clearing all point warps.", LOG);
            ArdynPointWarpAbility.clearAll(server);
        });

        LegacyOfTheLucii.LOGGER.info("{} Registration complete.", LOG);
    }

    private static void handlePointWarpPacket(
            MinecraftServer server,
            ServerPlayerEntity player,
            ServerPlayNetworkHandler handler,
            PacketByteBuf buf,
            PacketSender responseSender
    ) {
        final int action;
        try {
            action = buf.readVarInt();
        } catch (RuntimeException exception) {
            LegacyOfTheLucii.LOGGER.error("{} Malformed packet from {}: cannot read action.",
                    LOG, player.getGameProfile().getName(), exception);
            return;
        }

        if (action == LuciiNetwork.ARDYN_POINT_WARP_START_ACTION) {
            final BlockPos blockPos;
            final int cornerIndex;
            try {
                blockPos = buf.readBlockPos();
                cornerIndex = buf.readVarInt();
            } catch (RuntimeException exception) {
                LegacyOfTheLucii.LOGGER.error("{} Malformed START packet from {}.",
                        LOG, player.getGameProfile().getName(), exception);
                return;
            }

            LegacyOfTheLucii.LOGGER.info("{} START packet from {}: block={}, corner={}.",
                    LOG, player.getGameProfile().getName(), blockPos.toString(), cornerIndex);
            server.execute(() -> {
                boolean started = ArdynPointWarpAbility.start(player, blockPos, cornerIndex);
                LegacyOfTheLucii.LOGGER.info("{} START result for {}: {}.",
                        LOG, player.getGameProfile().getName(), started ? "accepted" : "rejected");
            });
            return;
        }

        if (action == LuciiNetwork.ARDYN_POINT_WARP_STOP_ACTION) {
            LegacyOfTheLucii.LOGGER.info("{} STOP packet from {}.",
                    LOG, player.getGameProfile().getName());
            server.execute(() -> ArdynPointWarpAbility.stop(player, "client request"));
            return;
        }

        LegacyOfTheLucii.LOGGER.warn("{} Unknown action {} from {}.",
                LOG, action, player.getGameProfile().getName());
    }
}
