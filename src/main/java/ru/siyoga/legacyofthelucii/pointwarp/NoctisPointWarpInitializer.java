package ru.siyoga.legacyofthelucii.pointwarp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

/** Registers the server endpoint for the separate Noctis point warp. */
public final class NoctisPointWarpInitializer implements ModInitializer {
    private static final String LOG = "[NoctisPointWarp/SERVER]";

    @Override
    public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(
                LuciiNetwork.NOCTIS_POINT_WARP_PACKET,
                NoctisPointWarpInitializer::handle
        );
        LegacyOfTheLucii.LOGGER.info("{} Registered packet {}.", LOG, LuciiNetwork.NOCTIS_POINT_WARP_PACKET);
    }

    private static void handle(
            MinecraftServer server,
            ServerPlayerEntity player,
            ServerPlayNetworkHandler handler,
            PacketByteBuf buf,
            PacketSender responseSender
    ) {
        final BlockPos target;
        try {
            target = buf.readBlockPos();
        } catch (RuntimeException exception) {
            LegacyOfTheLucii.LOGGER.warn("{} Rejected malformed packet from {}.",
                    LOG, player.getGameProfile().getName());
            return;
        }

        server.execute(() -> NoctisPointWarpAbility.start(player, target));
    }
}
