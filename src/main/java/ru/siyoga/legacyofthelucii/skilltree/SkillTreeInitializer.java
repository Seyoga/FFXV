package ru.siyoga.legacyofthelucii.skilltree;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.text.Text;
import ru.siyoga.legacyofthelucii.command.FfxvCommands;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

public final class SkillTreeInitializer implements ModInitializer {
    @Override
    public void onInitialize() {
        FfxvCommands.register();

        ServerPlayNetworking.registerGlobalReceiver(SkillTreeNetwork.UNLOCK_PACKET, (server, player, handler, buf, responseSender) -> {
            String skillId = buf.readString(64);
            server.execute(() -> {
                if (!player.getAbilities().creativeMode) {
                    player.sendMessage(Text.literal("Навыки бесплатно открываются только в креативе."), true);
                    return;
                }
                LuciiSkill skill = LuciiSkill.byId(skillId);
                LuciiPlayerState state = LuciiPlayerStates.get(player);
                if (skill == null || skill.legacy() != state.legacy()) return;
                if (skill.prerequisite() != null && !LuciiSkills.isUnlocked(state, skill.prerequisite())) {
                    player.sendMessage(Text.literal("Сначала открой предыдущую ступень."), true);
                    return;
                }
                if (LuciiSkills.unlock(state, skill)) {
                    SkillTreeNetwork.sendState(player);
                    LuciiNetwork.sendState(player);
                    if (state.royalArmsActive()) LuciiNetwork.broadcastRoyalArmsVisual(player);
                }
            });
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                SkillTreeNetwork.sendState(handler.player));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                SkillTreeNetwork.sendState(newPlayer));
    }
}
