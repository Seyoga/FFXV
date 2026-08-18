package ru.siyoga.legacyofthelucii.client.sniper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.EntityType;
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
        ClientPlayNetworking.registerGlobalReceiver(
                ArdynSniperNetwork.ANIMATION_PACKET,
                (client, handler, buf, responseSender) -> {
                    java.util.UUID ownerUuid = buf.readUuid();
                    int animation = buf.readVarInt();
                    client.execute(() -> {
                        if (animation == ArdynSniperNetwork.ANIMATION_EQUIP) {
                            ArdynSniperAnimations.playEquip(ownerUuid);
                        } else if (animation == ArdynSniperNetwork.ANIMATION_HOLD) {
                            ArdynSniperAnimations.playHold(ownerUuid);
                        } else if (animation == ArdynSniperNetwork.ANIMATION_SHOOT) {
                            ArdynSniperAnimations.playShoot(ownerUuid);
                        } else if (animation == ArdynSniperNetwork.ANIMATION_UNEQUIP) {
                            ArdynSniperAnimations.playUnequip(ownerUuid);
                        } else if (animation == ArdynSniperNetwork.ANIMATION_CLEAR) {
                            ArdynSniperAnimations.clear(ownerUuid);
                        }
                    });
                }
        );
        EntityRendererRegistry.register(ArdynSniperContent.SNIPER_BULLET_ENTITY, ArdynSniperBulletRenderer::new);
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, entityRenderer, registrationHelper, context) -> {
                    if (entityType == EntityType.PLAYER
                            && entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
                        registrationHelper.register(new ArdynCerberusFeatureRenderer(playerRenderer));
                    }
                }
        );
        ArdynSniperClient.register();
        ClientTickEvents.END_CLIENT_TICK.register(ArdynSniperAnimations::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ArdynSniperClient.clear();
            ArdynSniperAnimations.clearAll();
        });
        LegacyOfTheLucii.LOGGER.info("{} Registered Ctrl+6 sniper HUD/input/animations.", LOG);
    }
}
