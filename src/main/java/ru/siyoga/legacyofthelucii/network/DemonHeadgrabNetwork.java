package ru.siyoga.legacyofthelucii.network;

import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.demon.headgrab.DemonHeadgrabSystem;

public final class DemonHeadgrabNetwork {
    public static final Identifier STATE_PACKET =
            new Identifier(
                    LegacyOfTheLucii.MOD_ID,
                    "demon_headgrab_state"
            );

    public static final Identifier VISUAL_STATE_PACKET =
            new Identifier(
                    LegacyOfTheLucii.MOD_ID,
                    "demon_headgrab_visual_state"
            );

    public static final Identifier QTE_INPUT_PACKET =
            new Identifier(
                    LegacyOfTheLucii.MOD_ID,
                    "demon_headgrab_qte_input"
            );

    public static final Identifier QTE_RESULT_PACKET =
            new Identifier(
                    LegacyOfTheLucii.MOD_ID,
                    "demon_headgrab_qte_result"
            );

    private static boolean registered;

    private DemonHeadgrabNetwork() {
    }

    public static void registerServer() {
        if (registered) {
            return;
        }
        registered = true;

        ServerPlayNetworking.registerGlobalReceiver(
                QTE_INPUT_PACKET,
                (server, player, handler, buf, responseSender) -> {
                    boolean pressed = buf.readBoolean();

                    server.execute(() ->
                            DemonHeadgrabSystem.handleQteInput(
                                    player,
                                    pressed
                            )
                    );
                }
        );

        /*
         * A player beginning to track an already attached slime must receive
         * its visual state too, otherwise only players present at attachment
         * time would see the enlarged model.
         */
        EntityTrackingEvents.START_TRACKING.register(
                (trackedEntity, observer) -> {
                    if (!(trackedEntity instanceof SlimeEntity slime)) {
                        return;
                    }

                    ServerPlayerEntity victim =
                            DemonHeadgrabSystem.getAttachedVictim(
                                    slime
                            );

                    if (victim != null) {
                        sendVisualState(
                                observer,
                                true,
                                slime,
                                victim
                        );
                    }
                }
        );

        LegacyOfTheLucii.LOGGER.info(
                "Demon headgrab: server networking and observer visual sync registered."
        );
    }

    public static void sendState(
            ServerPlayerEntity victim,
            boolean attached,
            SlimeEntity slime
    ) {
        PacketByteBuf victimBuf =
                PacketByteBufs.create();

        victimBuf.writeBoolean(attached);
        victimBuf.writeVarInt(
                attached ? slime.getId() : -1
        );

        ServerPlayNetworking.send(
                victim,
                STATE_PACKET,
                victimBuf
        );

        broadcastVisualState(
                attached,
                slime,
                victim
        );
    }

    public static void sendQteResult(
            ServerPlayerEntity victim,
            boolean success,
            float progress
    ) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(success);
        buf.writeFloat(progress);

        ServerPlayNetworking.send(
                victim,
                QTE_RESULT_PACKET,
                buf
        );
    }

    private static void broadcastVisualState(
            boolean attached,
            SlimeEntity slime,
            ServerPlayerEntity victim
    ) {
        if (!(slime.getWorld() instanceof ServerWorld world)) {
            return;
        }

        for (ServerPlayerEntity observer :
                world.getPlayers()) {
            sendVisualState(
                    observer,
                    attached,
                    slime,
                    victim
            );
        }
    }

    private static void sendVisualState(
            ServerPlayerEntity observer,
            boolean attached,
            SlimeEntity slime,
            ServerPlayerEntity victim
    ) {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeBoolean(attached);
        buf.writeVarInt(slime.getId());
        buf.writeVarInt(victim.getId());

        ServerPlayNetworking.send(
                observer,
                VISUAL_STATE_PACKET,
                buf
        );
    }
}
