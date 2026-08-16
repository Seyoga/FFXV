package ru.siyoga.legacyofthelucii.client.masquerade;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import ru.siyoga.legacyofthelucii.client.gui.masquerade.MasqueradeKeybindings;
import ru.siyoga.legacyofthelucii.masquerade.MasqueradeMorph;
import ru.siyoga.legacyofthelucii.network.MasqueradeNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MasqueradeClient {
    private MasqueradeClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                MasqueradeNetwork.OWNER_STATE_PACKET,
                (client, handler, buf, responseSender) -> {
                    UUID localPlayerUuid = buf.readUuid();
                    int count = Math.min(buf.readVarInt(), 4096);
                    List<MasqueradeMorph> unlocked = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        unlocked.add(MasqueradeMorph.read(buf));
                    }
                    MasqueradeMorph active = readOptionalMorph(buf);
                    UUID targetUuid = buf.readBoolean() ? buf.readUuid() : null;
                    int targetEntityId = targetUuid == null ? -1 : buf.readInt();
                    client.execute(() ->
                            MasqueradeClientState.updateOwnerState(
                                    localPlayerUuid,
                                    unlocked,
                                    active,
                                    targetUuid,
                                    targetEntityId
                            ));
                }
        );
        ClientPlayNetworking.registerGlobalReceiver(
                MasqueradeNetwork.OBSERVER_VISUAL_STATE_PACKET,
                (client, handler, buf, responseSender) -> {
                    UUID ownerUuid = buf.readUuid();
                    MasqueradeMorph morph = readOptionalMorph(buf);
                    UUID swapSourceUuid = buf.readBoolean() ? buf.readUuid() : null;
                    MasqueradeMorph swapMorph = swapSourceUuid == null ? null : MasqueradeMorph.read(buf);
                    client.execute(() -> MasqueradeClientState.updateObserverVisual(
                            ownerUuid,
                            morph,
                            swapSourceUuid,
                            swapMorph
                    ));
                }
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MasqueradeClientState.reset();
            MasqueradeRenderEntityCache.clear();
        });
        MasqueradeKeybindings.register();
    }

    public static void select(MasqueradeMorph morph) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(morph.key());
        ClientPlayNetworking.send(MasqueradeNetwork.SELECT_PACKET, buf);
    }

    public static void selectTarget(UUID targetUuid) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(targetUuid);
        ClientPlayNetworking.send(MasqueradeNetwork.TARGET_PACKET, buf);
    }

    public static void clearTarget() {
        ClientPlayNetworking.send(MasqueradeNetwork.CLEAR_TARGET_PACKET, PacketByteBufs.create());
    }

    private static MasqueradeMorph readOptionalMorph(PacketByteBuf buf) {
        return buf.readBoolean() ? MasqueradeMorph.read(buf) : null;
    }
}
