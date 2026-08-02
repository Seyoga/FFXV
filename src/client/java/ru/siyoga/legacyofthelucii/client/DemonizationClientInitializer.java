package ru.siyoga.legacyofthelucii.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.state.DemonizationClientState;
import ru.siyoga.legacyofthelucii.network.DemonizationNetwork;

public final class DemonizationClientInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                DemonizationNetwork.STATE_PACKET,
                (client, handler, buf, responseSender) -> {
                    var entityUuid = buf.readUuid();
                    boolean demonized = buf.readBoolean();

                    client.execute(() -> {
                        DemonizationClientState.update(entityUuid, demonized);
                        LegacyOfTheLucii.LOGGER.info(
                                "Demonization client: state={} for entity uuid={}",
                                demonized,
                                entityUuid
                        );
                    });
                }
        );

        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> DemonizationClientState.clear()
        );

        LegacyOfTheLucii.LOGGER.info(
                "Demonization client: explicit state receiver registered; glow disabled."
        );
    }
}
