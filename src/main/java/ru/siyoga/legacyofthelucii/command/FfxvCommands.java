package ru.siyoga.legacyofthelucii.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;
import ru.siyoga.legacyofthelucii.skilltree.LuciiSkills;
import ru.siyoga.legacyofthelucii.skilltree.SkillTreeNetwork;

import static net.minecraft.server.command.CommandManager.literal;

public final class FfxvCommands {
    private FfxvCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("ffxv")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("royalarms")
                        .then(literal("ardyn").executes(context ->
                                setLegacy(context.getSource(), LuciiLegacy.ARDYN)))
                        .then(literal("noctis").executes(context ->
                                setLegacy(context.getSource(), LuciiLegacy.NOCTIS))))
                .then(literal("skilltree")
                        .then(literal("unlocked").executes(context ->
                                unlockAll(context.getSource())))
                        .then(literal("lockall").executes(context ->
                                lockAll(context.getSource())))));
    }

    private static int setLegacy(ServerCommandSource source, LuciiLegacy legacy) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        state.unlockLegacy(legacy);
        LuciiSkills.syncInventorySlots(state);
        LuciiNetwork.sendState(player);
        SkillTreeNetwork.sendState(player);
        LuciiNetwork.broadcastRoyalArmsVisual(player);
        source.sendFeedback(() -> Text.literal("FFXV: Royal Arms -> " + legacy.id()), false);
        return 1;
    }

    private static int unlockAll(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() == LuciiLegacy.NONE) {
            source.sendError(Text.literal("Сначала выбери /ffxv royalarms ardyn или noctis."));
            return 0;
        }
        LuciiSkills.unlockAll(state, state.legacy());
        SkillTreeNetwork.sendState(player);
        LuciiNetwork.sendState(player);
        if (state.royalArmsActive()) LuciiNetwork.broadcastRoyalArmsVisual(player);
        source.sendFeedback(() -> Text.literal("FFXV: все навыки текущего персонажа открыты."), false);
        return 1;
    }

    private static int lockAll(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        LuciiSkills.lockAll(state);
        SkillTreeNetwork.sendState(player);
        LuciiNetwork.sendState(player);
        if (state.royalArmsActive()) LuciiNetwork.broadcastRoyalArmsVisual(player);
        source.sendFeedback(() -> Text.literal("FFXV: все навыки заблокированы, Оруженосец снова 3 слота."), false);
        return 1;
    }
}
