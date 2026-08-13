package ru.siyoga.legacyofthelucii.client.darktornado;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.client.royalarms.ardyn.ArdynDarkTornadoClient;
import ru.siyoga.legacyofthelucii.client.royalarms.ardyn.ArdynDarkTornadoAnimations;
import ru.siyoga.legacyofthelucii.darktornado.DarkTornadoNetwork;

import java.util.UUID;

/** Standalone client entrypoint for the Dark Tornado key, marker and animation hook. */
public final class ArdynDarkTornadoClientInitializer implements ClientModInitializer {
    private static final String LOG = "[DarkTornado/CLIENT]";

    @Override
    public void onInitializeClient() {
        LegacyOfTheLucii.LOGGER.info("{} Initializer loaded.", LOG);

        ClientPlayNetworking.registerGlobalReceiver(
                DarkTornadoNetwork.TARGETING_STATE_PACKET,
                (client, handler, buf, responseSender) -> {
                    boolean targeting = buf.readBoolean();
                    client.execute(() -> ArdynDarkTornadoClient.setTargeting(targeting));
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                DarkTornadoNetwork.VISUAL_STATE_PACKET,
                (client, handler, buf, responseSender) -> {
                    UUID ownerUuid = buf.readUuid();
                    boolean active = buf.readBoolean();
                    Vec3d center = new Vec3d(
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble()
                    );
                    client.execute(() ->
                            ArdynDarkTornadoClient.updateVisual(ownerUuid, active, center));
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                DarkTornadoNetwork.ANIMATION_PACKET,
                (client, handler, buf, responseSender) -> {
                    UUID ownerUuid = buf.readUuid();
                    int animation = buf.readVarInt();
                    client.execute(() -> {
                        if (animation == DarkTornadoNetwork.ANIMATION_START) {
                            ArdynDarkTornadoAnimations.playStart(ownerUuid);
                        } else if (animation == DarkTornadoNetwork.ANIMATION_CANCEL) {
                            ArdynDarkTornadoAnimations.playCancel(ownerUuid);
                        } else if (animation == DarkTornadoNetwork.ANIMATION_CLICK) {
                            ArdynDarkTornadoAnimations.playClick(ownerUuid);
                        }
                    });
                }
        );

        ArdynDarkTornadoClient.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ArdynDarkTornadoClient.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ArdynDarkTornadoAnimations.clear());

        LegacyOfTheLucii.LOGGER.info(
                "{} Registration complete. Default combination: Ctrl+5.", LOG);
    }
}
