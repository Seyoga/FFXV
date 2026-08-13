package ru.siyoga.legacyofthelucii.darktornado;

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
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynDarkTornadoAbility;

/** Standalone server entrypoint for Ardyn's Dark Tornado. */
public final class ArdynDarkTornadoInitializer implements ModInitializer {
    private static final String LOG = "[DarkTornado/SERVER]";

    @Override
    public void onInitialize() {
        LegacyOfTheLucii.LOGGER.info("{} Initializer loaded. Registering packet {}.",
                LOG, DarkTornadoNetwork.ACTION_PACKET);

        ServerPlayNetworking.registerGlobalReceiver(
                DarkTornadoNetwork.ACTION_PACKET,
                ArdynDarkTornadoInitializer::handlePacket
        );
        ServerTickEvents.END_SERVER_TICK.register(ArdynDarkTornadoAbility::tick);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ArdynDarkTornadoAbility.clearPlayer(handler.player, "disconnect"));

        ServerLifecycleEvents.SERVER_STOPPING.register(ArdynDarkTornadoAbility::clearAll);
        LegacyOfTheLucii.LOGGER.info("{} Registration complete.", LOG);
    }

    private static void handlePacket(
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
            LegacyOfTheLucii.LOGGER.error("{} Malformed packet from {}: no action.",
                    LOG, player.getGameProfile().getName(), exception);
            return;
        }

        if (action == DarkTornadoNetwork.TOGGLE_TARGETING_ACTION) {
            server.execute(() -> ArdynDarkTornadoAbility.toggleTargeting(player));
            return;
        }

        if (action == DarkTornadoNetwork.CONFIRM_TARGET_ACTION) {
            final BlockPos targetBlock;
            try {
                targetBlock = buf.readBlockPos();
            } catch (RuntimeException exception) {
                LegacyOfTheLucii.LOGGER.error("{} Malformed confirm packet from {}.",
                        LOG, player.getGameProfile().getName(), exception);
                return;
            }
            server.execute(() -> ArdynDarkTornadoAbility.confirmTarget(player, targetBlock));
            return;
        }

        LegacyOfTheLucii.LOGGER.warn("{} Unknown action {} from {}.",
                LOG, action, player.getGameProfile().getName());
    }
}
