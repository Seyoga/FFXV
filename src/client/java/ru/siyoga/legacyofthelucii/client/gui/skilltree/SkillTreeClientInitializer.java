package ru.siyoga.legacyofthelucii.client.gui.skilltree;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import ru.siyoga.legacyofthelucii.skilltree.SkillTreeNetwork;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SkillTreeClientInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SkillTreeNetwork.STATE_PACKET, (client, handler, buf, responseSender) -> {
            int count = buf.readVarInt();
            Set<String> ids = new LinkedHashSet<>();
            for (int i = 0; i < count; i++) ids.add(buf.readString(64));
            int inventorySlots = buf.readVarInt();
            client.execute(() -> ClientSkillTreeState.update(ids, inventorySlots));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientSkillTreeState.reset());
    }
}
