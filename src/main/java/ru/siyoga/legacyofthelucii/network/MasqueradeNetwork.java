package ru.siyoga.legacyofthelucii.network;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.masquerade.MasqueradeManager;
import ru.siyoga.legacyofthelucii.masquerade.MasqueradeMorph;

public final class MasqueradeNetwork {
    public static final Identifier SELECT_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "masquerade_select");
    public static final Identifier TARGET_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "masquerade_target");
    public static final Identifier CLEAR_TARGET_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "masquerade_clear_target");
    public static final Identifier OWNER_STATE_PACKET = new Identifier(LegacyOfTheLucii.MOD_ID, "masquerade_owner_state");
    public static final Identifier OBSERVER_VISUAL_STATE_PACKET = new Identifier(
            LegacyOfTheLucii.MOD_ID,
            "masquerade_observer_visual_state"
    );

    private MasqueradeNetwork() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(SELECT_PACKET, (server, player, handler, buf, responseSender) -> {
            String morphKey = buf.readString(256);
            server.execute(() -> MasqueradeManager.toggleMorph(player, morphKey));
        });
        ServerPlayNetworking.registerGlobalReceiver(TARGET_PACKET, (server, player, handler, buf, responseSender) -> {
            java.util.UUID targetUuid = buf.readUuid();
            server.execute(() -> MasqueradeManager.selectTarget(player, targetUuid));
        });
        ServerPlayNetworking.registerGlobalReceiver(CLEAR_TARGET_PACKET, (server, player, handler, buf, responseSender) ->
                server.execute(() -> MasqueradeManager.clearTarget(player)));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity joiningPlayer = handler.player;
            sendOwnerState(joiningPlayer);
            sendAllObserverVisualStates(joiningPlayer);
            sendObserverVisualState(joiningPlayer);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                MasqueradeManager.onDisconnect(handler.player));

        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                MasqueradeManager.onPlayerEntityReplaced(oldPlayer));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            sendOwnerState(newPlayer);
            sendAllObserverVisualStates(newPlayer);
            sendObserverVisualState(newPlayer);
        });
    }

    public static void sendOwnerState(ServerPlayerEntity player) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(player.getUuid());
        buf.writeVarInt(state.unlockedMorphs().size());
        for (MasqueradeMorph morph : state.unlockedMorphs()) {
            morph.write(buf);
        }
        writeOptionalMorph(buf, activeMorph(state));

        LivingEntity target = MasqueradeManager.getTargetEntity(player);
        java.util.UUID targetUuid = target == null ? null : state.masqueradeTargetUuid();
        buf.writeBoolean(targetUuid != null);
        if (targetUuid != null) {
            buf.writeUuid(targetUuid);
            buf.writeInt(target.getId());
        }
        ServerPlayNetworking.send(player, OWNER_STATE_PACKET, buf);
    }

    public static void sendObserverVisualState(ServerPlayerEntity owner) {
        LuciiPlayerState state = LuciiPlayerStates.get(owner);
        java.util.UUID targetUuid = state.masqueradeTargetUuid();
        LivingEntity target = MasqueradeManager.getTargetEntity(owner);
        if (!(target instanceof ServerPlayerEntity observer)) {
            return;
        }
        sendObserverVisualState(observer, owner, activeMorph(state));
    }

    public static void clearObserverVisual(ServerPlayerEntity owner, java.util.UUID targetUuid) {
        MinecraftServer server = owner.getServer();
        ServerPlayerEntity target = targetUuid == null || server == null
                ? null
                : server.getPlayerManager().getPlayer(targetUuid);
        if (target != null && !target.isRemoved()) {
            sendObserverVisualState(target, owner, null);
        }
    }

    public static void sendAllObserverVisualStates(ServerPlayerEntity viewer) {
        MinecraftServer server = viewer.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity owner : server.getPlayerManager().getPlayerList()) {
            LuciiPlayerState state = LuciiPlayerStates.get(owner);
            if (viewer.getUuid().equals(state.masqueradeTargetUuid())
                    && MasqueradeManager.getTargetEntity(owner) == viewer) {
                sendObserverVisualState(viewer, owner, activeMorph(state));
            }
        }
    }

    public static boolean isSwapVisible(ServerPlayerEntity owner) {
        LuciiPlayerState state = LuciiPlayerStates.get(owner);
        MasqueradeMorph morph = activeMorph(state);
        if (morph == null || morph.kind() != MasqueradeMorph.Kind.PLAYER) {
            return false;
        }
        if (!(MasqueradeManager.getTargetEntity(owner) instanceof ServerPlayerEntity)) {
            return false;
        }
        MinecraftServer server = owner.getServer();
        ServerPlayerEntity representedPlayer = server == null
                ? null
                : server.getPlayerManager().getPlayer(morph.playerProfile().getId());
        java.util.UUID targetUuid = state.masqueradeTargetUuid();
        return representedPlayer != null
                && !representedPlayer.isRemoved()
                && !representedPlayer.getUuid().equals(targetUuid)
                && representedPlayer.getWorld() == owner.getWorld()
                && owner.squaredDistanceTo(representedPlayer) <= 50.0D * 50.0D;
    }

    private static void sendObserverVisualState(
            ServerPlayerEntity viewer,
            ServerPlayerEntity owner,
            MasqueradeMorph morph
    ) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(owner.getUuid());
        writeOptionalMorph(buf, morph);
        boolean swapVisible = morph != null && isSwapVisible(owner);
        buf.writeBoolean(swapVisible);
        if (swapVisible) {
            ServerPlayerEntity representedPlayer = owner.getServer().getPlayerManager()
                    .getPlayer(morph.playerProfile().getId());
            buf.writeUuid(representedPlayer.getUuid());
            MasqueradeMorph.player(owner.getGameProfile()).write(buf);
        }
        ServerPlayNetworking.send(viewer, OBSERVER_VISUAL_STATE_PACKET, buf);
    }

    private static MasqueradeMorph activeMorph(LuciiPlayerState state) {
        return state.legacy() == LuciiLegacy.ARDYN ? state.activeMorph() : null;
    }

    private static void writeOptionalMorph(PacketByteBuf buf, MasqueradeMorph morph) {
        buf.writeBoolean(morph != null);
        if (morph != null) {
            morph.write(buf);
        }
    }
}
