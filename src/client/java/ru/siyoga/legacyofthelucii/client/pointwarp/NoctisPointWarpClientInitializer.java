package ru.siyoga.legacyofthelucii.client.pointwarp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import ru.siyoga.legacyofthelucii.client.royalarms.noctis.NoctisPointWarpClient;

/** Client entrypoint for the separate Noctis point warp. */
public final class NoctisPointWarpClientInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NoctisPointWarpClient.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> NoctisPointWarpClient.clear());
    }
}
