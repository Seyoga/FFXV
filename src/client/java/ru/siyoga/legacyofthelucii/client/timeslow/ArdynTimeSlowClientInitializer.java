package ru.siyoga.legacyofthelucii.client.timeslow;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.timeslow.ArdynTimeSlowNetwork;

public final class ArdynTimeSlowClientInitializer implements ClientModInitializer {
    private static final String LOG = "[TimeSlow/CLIENT]";

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                ArdynTimeSlowNetwork.FIELD_STATE_PACKET,
                (client, handler, buf, responseSender) -> {
                    java.util.UUID ownerUuid = buf.readUuid();
                    boolean active = buf.readBoolean();
                    client.execute(() -> ArdynTimeSlowClient.setFieldState(ownerUuid, active));
                }
        );
        ArdynTimeSlowClient.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ArdynTimeSlowClient.clear());
        LegacyOfTheLucii.LOGGER.info("{} Registered hold-RMB temporal concentration.", LOG);
    }
}
