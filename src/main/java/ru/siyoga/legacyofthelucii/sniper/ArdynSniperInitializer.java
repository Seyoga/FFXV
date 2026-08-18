package ru.siyoga.legacyofthelucii.sniper;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.BlockItem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynSniperAbility;

public final class ArdynSniperInitializer implements ModInitializer {
    private static final String LOG = "[Sniper/SERVER]";

    @Override
    public void onInitialize() {
        ArdynSniperContent.register();
        ServerPlayNetworking.registerGlobalReceiver(
                ArdynSniperNetwork.ACTION_PACKET,
                ArdynSniperInitializer::handleAction
        );
        ServerTickEvents.END_SERVER_TICK.register(ArdynSniperAbility::tick);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !(player instanceof ServerPlayerEntity serverPlayer
                        && ArdynSniperAbility.isActive(serverPlayer.getUuid())));
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient
                    && player instanceof ServerPlayerEntity serverPlayer
                    && ArdynSniperAbility.isActive(serverPlayer.getUuid())
                    && player.getStackInHand(hand).getItem() instanceof BlockItem) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ArdynSniperAbility.syncPlayer(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ArdynSniperAbility.clearPlayer(handler.player, "disconnect", false));
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                ArdynSniperAbility.clearPlayer(oldPlayer, "player entity replaced"));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                ArdynSniperAbility.syncPlayer(newPlayer));
        ServerLifecycleEvents.SERVER_STOPPING.register(ArdynSniperAbility::clearAll);
        LegacyOfTheLucii.LOGGER.info("{} Registered Ctrl+6 sniper mode.", LOG);
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

        if (action == ArdynSniperNetwork.TOGGLE_ACTION) {
            server.execute(() -> ArdynSniperAbility.toggle(player));
            return;
        }
        if (action == ArdynSniperNetwork.SHOOT_ACTION) {
            server.execute(() -> ArdynSniperAbility.shoot(player));
            return;
        }

        LegacyOfTheLucii.LOGGER.warn("{} Unknown action {} from {}.",
                LOG, action, player.getGameProfile().getName());
    }
}
