package ru.siyoga.legacyofthelucii.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.demon.DemonHeadgrabClient;
import ru.siyoga.legacyofthelucii.client.render.DemonizationDebugClient;
import ru.siyoga.legacyofthelucii.client.state.DemonizationClientState;
import ru.siyoga.legacyofthelucii.network.DemonizationNetwork;

public final class DemonizationClientInitializer
        implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                DemonizationNetwork.STATE_PACKET,
                (client, handler, buf, responseSender) -> {
                    var entityUuid = buf.readUuid();
                    boolean demonized = buf.readBoolean();

                    client.execute(() -> {
                        DemonizationClientState.update(
                                entityUuid,
                                demonized
                        );

                        LegacyOfTheLucii.LOGGER.info(
                                "Demonization client: state={} for entity uuid={}",
                                demonized,
                                entityUuid
                        );
                    });
                }
        );

        DemonHeadgrabClient.register();
        DemonizationDebugClient.register();

        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> {
                    DemonizationClientState.clear();
                    DemonHeadgrabClient.reset();
                }
        );

        LegacyOfTheLucii.LOGGER.info(
                "Demonization client: state receiver and headgrab QTE registered."
        );
    }
}
