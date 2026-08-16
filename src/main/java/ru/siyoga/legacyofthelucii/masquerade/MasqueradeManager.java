package ru.siyoga.legacyofthelucii.masquerade;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.MasqueradeNetwork;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

public final class MasqueradeManager {
    public static final int MANA_COST = 2;
    public static final int MANA_DRAIN_INTERVAL_TICKS = 20 * 3;
    public static final double TARGET_SELECT_RANGE = 10.0D;
    private static final java.util.Map<java.util.UUID, Integer> DRAIN_TICKS = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Boolean> SWAP_VISIBLE = new java.util.HashMap<>();

    private MasqueradeManager() {
    }

    public static boolean unlockMorph(PlayerEntity player, LivingEntity entity) {
        if (entity instanceof PlayerEntity) {
            return false;
        }
        return unlockMorph(player, MasqueradeMorph.entity(entity.getType()));
    }

    public static boolean unlockMorph(PlayerEntity player, EntityType<?> entityType) {
        if (entityType == EntityType.PLAYER) {
            return false;
        }
        return unlockMorph(player, MasqueradeMorph.entity(entityType));
    }

    public static boolean unlockMorph(PlayerEntity player, GameProfile profile) {
        if (profile == null || profile.getId() == null || profile.getName() == null) {
            return false;
        }
        return unlockMorph(player, MasqueradeMorph.player(profile));
    }

    public static boolean unlockMorph(PlayerEntity player, MasqueradeMorph morph) {
        boolean changed = LuciiPlayerStates.get(player).unlockMorph(morph);
        if (changed && player instanceof ServerPlayerEntity serverPlayer) {
            MasqueradeNetwork.sendOwnerState(serverPlayer);
        }
        return changed;
    }

    public static boolean toggleMorph(ServerPlayerEntity player, String morphKey) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() != LuciiLegacy.ARDYN) {
            clearActiveMorph(player);
            return false;
        }
        if (state.ardynOverkillActive()) {
            MasqueradeNetwork.sendOwnerState(player);
            return false;
        }

        MasqueradeMorph requested = state.findUnlockedMorph(morphKey);
        if (requested == null) {
            MasqueradeNetwork.sendOwnerState(player);
            return false;
        }

        MasqueradeMorph next = requested.equals(state.activeMorph()) ? null : requested;
        if (next != null && !hasAvailableTarget(player, state)) {
            player.sendMessage(Text.translatable("message.legacyofthelucii.masquerade.requires_target"), true);
            MasqueradeNetwork.sendOwnerState(player);
            return false;
        }
        if (next != null && !state.hasMana(MANA_COST)) {
            player.sendMessage(Text.translatable("message.legacyofthelucii.royal_arms.not_enough_mana"), true);
            MasqueradeNetwork.sendOwnerState(player);
            return false;
        }
        if (state.setActiveMorph(next)) {
            syncChangedState(player);
        }
        return true;
    }

    public static boolean clearTarget(ServerPlayerEntity player) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() != LuciiLegacy.ARDYN || state.masqueradeTargetUuid() == null) {
            return false;
        }
        return clearTarget(player, true);
    }

    public static boolean selectTarget(ServerPlayerEntity player, java.util.UUID targetUuid) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() != LuciiLegacy.ARDYN
                || state.ardynOverkillActive()
                || targetUuid == null
                || targetUuid.equals(player.getUuid())) {
            return false;
        }

        Entity target = player.getServerWorld().getEntity(targetUuid);
        if (!isEligibleTarget(player, target)
                || player.squaredDistanceTo(target) > TARGET_SELECT_RANGE * TARGET_SELECT_RANGE) {
            player.sendMessage(Text.translatable("message.legacyofthelucii.masquerade.invalid_target"), true);
            return false;
        }

        java.util.UUID previousTarget = state.masqueradeTargetUuid();
        if (previousTarget != null && !previousTarget.equals(targetUuid)) {
            clearMobPerception(player, previousTarget);
        }
        state.setMasqueradeTargetUuid(targetUuid);
        clearRuntimeState(player.getUuid());
        if (previousTarget != null && !previousTarget.equals(targetUuid)) {
            MasqueradeNetwork.clearObserverVisual(player, previousTarget);
        }
        MasqueradeNetwork.sendOwnerState(player);
        MasqueradeNetwork.sendObserverVisualState(player);
        refreshMobTarget(player);
        return true;
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            LuciiPlayerState state = LuciiPlayerStates.get(player);
            if (state.legacy() != LuciiLegacy.ARDYN) {
                clearRuntimeState(player.getUuid());
                continue;
            }

            LivingEntity target = getTargetEntity(player);
            if (state.masqueradeTargetUuid() != null && target == null) {
                clearTarget(player, true);
                target = null;
            }

            if (state.activeMorph() == null || target == null) {
                clearRuntimeState(player.getUuid());
                continue;
            }

            int elapsed = DRAIN_TICKS.getOrDefault(player.getUuid(), 0) + 1;
            if (elapsed >= MANA_DRAIN_INTERVAL_TICKS) {
                elapsed = 0;
                if (!state.spendMana(MANA_COST)) {
                    clearActiveMorph(player);
                    LuciiNetwork.sendState(player);
                    continue;
                }
                LuciiNetwork.sendState(player);
            }
            DRAIN_TICKS.put(player.getUuid(), elapsed);

            if (target instanceof MobEntity mob) {
                MasqueradePerception.refreshTargetRetention(mob, player);
            }

            boolean swapVisible = MasqueradeNetwork.isSwapVisible(player);
            Boolean previousSwapVisible = SWAP_VISIBLE.put(player.getUuid(), swapVisible);
            if (previousSwapVisible == null || previousSwapVisible != swapVisible) {
                MasqueradeNetwork.sendObserverVisualState(player);
            }
        }
    }

    public static void onPlayerEntityReplaced(ServerPlayerEntity player) {
        clearTarget(player, false);
    }

    public static void onDisconnect(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        java.util.UUID disconnectedUuid = player.getUuid();

        LuciiPlayerState ownState = LuciiPlayerStates.get(player);
        java.util.UUID ownTarget = ownState.masqueradeTargetUuid();
        if (ownTarget != null) {
            clearMobPerception(player, ownTarget);
            MasqueradeNetwork.clearObserverVisual(player, ownTarget);
            ownState.setMasqueradeTargetUuid(null);
        }
        clearRuntimeState(disconnectedUuid);

        if (server == null) {
            return;
        }

        for (ServerPlayerEntity owner : server.getPlayerManager().getPlayerList()) {
            if (owner == player) {
                continue;
            }
            LuciiPlayerState state = LuciiPlayerStates.get(owner);
            if (!disconnectedUuid.equals(state.masqueradeTargetUuid())) {
                continue;
            }

            MasqueradeNetwork.clearObserverVisual(owner, disconnectedUuid);
            state.setMasqueradeTargetUuid(null);
            clearRuntimeState(owner.getUuid());
            MasqueradeNetwork.sendOwnerState(owner);
        }

        server.execute(() -> refreshSwapsAfterPlayerLeft(server, disconnectedUuid));
    }

    public static void clearIfUnavailable(ServerPlayerEntity player) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() != LuciiLegacy.ARDYN) {
            clearActiveMorph(player);
        }
    }

    public static void clearForOverkill(ServerPlayerEntity player) {
        clearTarget(player, true);
    }

    private static void syncChangedState(ServerPlayerEntity player) {
        MasqueradeNetwork.sendOwnerState(player);
        MasqueradeNetwork.sendObserverVisualState(player);
        refreshMobTarget(player);
        clearRuntimeState(player.getUuid());
    }

    private static void clearActiveMorph(ServerPlayerEntity player) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        java.util.UUID targetUuid = state.masqueradeTargetUuid();
        if (state.setActiveMorph(null)) {
            clearMobPerception(player, targetUuid);
            syncChangedState(player);
        }
    }

    private static boolean clearTarget(ServerPlayerEntity player, boolean syncOwner) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        java.util.UUID targetUuid = state.masqueradeTargetUuid();
        if (targetUuid == null) {
            return false;
        }

        clearMobPerception(player, targetUuid);
        MasqueradeNetwork.clearObserverVisual(player, targetUuid);
        state.setMasqueradeTargetUuid(null);
        clearRuntimeState(player.getUuid());
        if (syncOwner) {
            MasqueradeNetwork.sendOwnerState(player);
        }
        return true;
    }

    private static void clearRuntimeState(java.util.UUID playerUuid) {
        DRAIN_TICKS.remove(playerUuid);
        SWAP_VISIBLE.remove(playerUuid);
    }
    private static void clearMobPerception(ServerPlayerEntity player, java.util.UUID targetUuid) {
        if (targetUuid == null) {
            return;
        }
        Entity target = player.getServerWorld().getEntity(targetUuid);
        if (target instanceof MobEntity mob) {
            MasqueradePerception.clearMasqueradeAggression(mob, player);
        } else {
            MasqueradePerception.forgetMasqueradeAggression(targetUuid, player.getUuid());
        }
    }


    private static void refreshSwapsAfterPlayerLeft(MinecraftServer server, java.util.UUID disconnectedUuid) {
        for (ServerPlayerEntity owner : server.getPlayerManager().getPlayerList()) {
            LuciiPlayerState state = LuciiPlayerStates.get(owner);
            MasqueradeMorph morph = state.activeMorph();
            if (morph == null
                    || morph.kind() != MasqueradeMorph.Kind.PLAYER
                    || morph.playerProfile() == null
                    || !disconnectedUuid.equals(morph.playerProfile().getId())) {
                continue;
            }
            SWAP_VISIBLE.put(owner.getUuid(), false);
            MasqueradeNetwork.sendObserverVisualState(owner);
        }
    }

    private static boolean hasAvailableTarget(ServerPlayerEntity player, LuciiPlayerState state) {
        return getTargetEntity(player) != null;
    }

    public static LivingEntity getTargetEntity(ServerPlayerEntity player) {
        java.util.UUID targetUuid = LuciiPlayerStates.get(player).masqueradeTargetUuid();
        Entity target = targetUuid == null ? null : player.getServerWorld().getEntity(targetUuid);
        return isEligibleTarget(player, target) ? (LivingEntity) target : null;
    }

    private static boolean isEligibleTarget(ServerPlayerEntity player, Entity target) {
        return target instanceof LivingEntity
                && target != player
                && !target.isRemoved()
                && (target instanceof ServerPlayerEntity || target instanceof MobEntity);
    }

    private static void refreshMobTarget(ServerPlayerEntity player) {
        LivingEntity target = getTargetEntity(player);
        if (target instanceof MobEntity mob) {
            MasqueradePerception.refreshTargetRetention(mob, player);
        }
    }
}
