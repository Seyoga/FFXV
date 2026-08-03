package ru.siyoga.legacyofthelucii.client.pointwarp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.royalarms.ardyn.ArdynPointWarpClient;
import ru.siyoga.legacyofthelucii.client.royalarms.ardyn.ArdynShadowStepClient;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

import java.util.UUID;

/** Standalone client entrypoint so key registration cannot be skipped silently. */
public final class ArdynPointWarpClientInitializer implements ClientModInitializer {
    private static final String LOG = "[PointWarp/CLIENT]";

    @Override
    public void onInitializeClient() {
        LegacyOfTheLucii.LOGGER.info("{} Initializer loaded.", LOG);

        ClientPlayNetworking.registerGlobalReceiver(
                LuciiNetwork.ARDYN_POINT_WARP_VISUAL_PACKET,
                (client, handler, buf, responseSender) -> {
                    UUID ownerUuid = buf.readUuid();
                    boolean active = buf.readBoolean();
                    LegacyOfTheLucii.LOGGER.info("{} VISUAL packet: owner={}, active={}.",
                            LOG, ownerUuid, active);
                    client.execute(() -> {
                        ArdynPointWarpClient.update(ownerUuid, active);
                        // Reuse Shadow Step's hidden-player/silhouette state, but keep its HUD filter disabled.
                        ArdynShadowStepClient.update(ownerUuid, active, false);
                    });
                }
        );

        ArdynPointWarpClient.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                LegacyOfTheLucii.LOGGER.info("{} Joined a world. Point Warp client hooks are active.", LOG));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LegacyOfTheLucii.LOGGER.info("{} Disconnected. Clearing Point Warp client state.", LOG);
            ArdynPointWarpClient.clear();
        });

        LegacyOfTheLucii.LOGGER.info("{} Registration complete. The key must appear in Controls as 'Точечный варп Ардина'.", LOG);
    }
}
