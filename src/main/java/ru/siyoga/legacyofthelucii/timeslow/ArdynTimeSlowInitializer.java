package ru.siyoga.legacyofthelucii.timeslow;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynTimeSlowAbility;

public final class ArdynTimeSlowInitializer implements ModInitializer {
    private static final String LOG = "[TimeSlow/SERVER]";

    @Override
    public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(
                ArdynTimeSlowNetwork.ACTION_PACKET,
                ArdynTimeSlowInitializer::handleAction
        );
        ServerTickEvents.START_SERVER_TICK.register(ArdynTimeSlowAbility::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ArdynTimeSlowAbility.syncViewer(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ArdynTimeSlowAbility.clearPlayer(handler.player, "disconnect"));
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                ArdynTimeSlowAbility.clearPlayer(oldPlayer, "player entity replaced"));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                ArdynTimeSlowAbility.syncViewer(newPlayer));
        ServerLifecycleEvents.SERVER_STOPPING.register(ArdynTimeSlowAbility::clearAll);
        LegacyOfTheLucii.LOGGER.info("{} Registered hold-RMB temporal concentration.", LOG);
    }

    private static void handleAction(
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
            LegacyOfTheLucii.LOGGER.warn("{} Malformed action packet from {}.",
                    LOG, player.getGameProfile().getName());
            return;
        }

        if (action == ArdynTimeSlowNetwork.START_ACTION) {
            server.execute(() -> ArdynTimeSlowAbility.setHeld(player, true));
            return;
        }
        if (action == ArdynTimeSlowNetwork.STOP_ACTION) {
            server.execute(() -> ArdynTimeSlowAbility.setHeld(player, false));
            return;
        }

        LegacyOfTheLucii.LOGGER.warn("{} Unknown action {} from {}.",
                LOG, action, player.getGameProfile().getName());
    }
}
