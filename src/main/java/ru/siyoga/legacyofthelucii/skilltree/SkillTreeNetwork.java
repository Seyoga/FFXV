package ru.siyoga.legacyofthelucii.skilltree;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;

public final class SkillTreeNetwork {
    public static final Identifier UNLOCK_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "skill_unlock");
    public static final Identifier STATE_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "skill_state");

    private SkillTreeNetwork() {
    }

    public static void sendState(ServerPlayerEntity player) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(LuciiSkills.unlockedIds(state).size());
        for (String id : LuciiSkills.unlockedIds(state)) {
            buf.writeString(id);
        }
        buf.writeVarInt(state.royalArmsUnlockedSlots());
        ServerPlayNetworking.send(player, STATE_PACKET, buf);
    }
}
