package ru.siyoga.legacyofthelucii.client.sniper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.sniper.ArdynSniperContent;
import ru.siyoga.legacyofthelucii.sniper.ArdynSniperNetwork;

public final class ArdynSniperClientInitializer implements ClientModInitializer {
    private static final String LOG = "[Sniper/CLIENT]";

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                ArdynSniperNetwork.STATE_PACKET,
                (client, handler, buf, responseSender) -> {
                    java.util.UUID ownerUuid = buf.readUuid();
                    boolean active = buf.readBoolean();
                    int cooldownTicks = buf.readVarInt();
                    client.execute(() -> ArdynSniperClient.setState(ownerUuid, active, cooldownTicks));
                }
        );
        EntityRendererRegistry.register(ArdynSniperContent.SNIPER_BULLET_ENTITY, ArdynSniperBulletRenderer::new);
        ArdynSniperClient.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ArdynSniperClient.clear());
        LegacyOfTheLucii.LOGGER.info("{} Registered Ctrl+6 sniper HUD/input.", LOG);
    }
}
