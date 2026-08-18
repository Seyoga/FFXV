package ru.siyoga.legacyofthelucii;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.siyoga.legacyofthelucii.block.LegacyBlocks;
import ru.siyoga.legacyofthelucii.entity.LegacyEntities;
import ru.siyoga.legacyofthelucii.effect.LegacyStatusEffects;
import ru.siyoga.legacyofthelucii.item.LegacyItems;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.legacy.RoyalArmsInventoryFilter;
import ru.siyoga.legacyofthelucii.network.ArdynOverkillNetwork;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;
import ru.siyoga.legacyofthelucii.network.MasqueradeNetwork;
import ru.siyoga.legacyofthelucii.network.RoyalArmsGuardNetwork;
import ru.siyoga.legacyofthelucii.masquerade.MasqueradeManager;
import ru.siyoga.legacyofthelucii.particle.LegacyParticles;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynOverkillAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynShadowStepAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.DebugDemonizeAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsBindAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsGuardAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsOrbitDamageAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsWallAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsWarpStrikeAbility;
import ru.siyoga.legacyofthelucii.royalarms.inventory.RoyalArmsScreenHandler;
import ru.siyoga.legacyofthelucii.royalarms.inventory.RoyalArmsScreenHandlers;

@SuppressWarnings("unused")
public final class LegacyOfTheLucii implements ModInitializer {
    public static final String MOD_ID = "legacyofthelucii";
    public static final Logger LOGGER = LoggerFactory.getLogger("Legacy of the Lucii");
    public static final Identifier ROYAL_ARMS_EQUIP_PACKET = new Identifier(MOD_ID, "royal_arms_equip");
    public static final Identifier ROYAL_ARMS_INVENTORY_OPEN_PACKET = new Identifier(MOD_ID, "royal_arms_inventory_open");

    @Override
    public void onInitialize() {
        LegacyBlocks.register();
        LegacyEntities.register();
        LegacyStatusEffects.register();
        LegacyParticles.register();
        LegacyItems.register();
        RoyalArmsScreenHandlers.register();
        MasqueradeNetwork.registerServer();
        ServerPlayNetworking.registerGlobalReceiver(ROYAL_ARMS_EQUIP_PACKET, LegacyOfTheLucii::handleRoyalArmsEquip);
        ServerPlayNetworking.registerGlobalReceiver(ROYAL_ARMS_INVENTORY_OPEN_PACKET, LegacyOfTheLucii::handleRoyalArmsInventoryOpen);
        ServerPlayNetworking.registerGlobalReceiver(LuciiNetwork.ROYAL_ARMS_TOGGLE_PACKET, LegacyOfTheLucii::handleRoyalArmsToggle);
        ServerPlayNetworking.registerGlobalReceiver(LuciiNetwork.ROYAL_ARMS_FILTER_PACKET, LegacyOfTheLucii::handleRoyalArmsFilter);
        ServerPlayNetworking.registerGlobalReceiver(LuciiNetwork.ROYAL_ARMS_WALL_PACKET, LegacyOfTheLucii::handleRoyalArmsWall);
        ServerPlayNetworking.registerGlobalReceiver(LuciiNetwork.ROYAL_ARMS_WARP_PACKET, LegacyOfTheLucii::handleRoyalArmsWarp);
        ServerPlayNetworking.registerGlobalReceiver(LuciiNetwork.ROYAL_ARMS_BIND_PACKET, LegacyOfTheLucii::handleRoyalArmsBind);
        ServerPlayNetworking.registerGlobalReceiver(LuciiNetwork.ARDYN_SHADOW_STEP_PACKET, LegacyOfTheLucii::handleArdynShadowStep);
        ServerPlayNetworking.registerGlobalReceiver(LuciiNetwork.DEBUG_DEMONIZE_PACKET, LegacyOfTheLucii::handleDebugDemonize);
        ServerPlayNetworking.registerGlobalReceiver(RoyalArmsGuardNetwork.TOGGLE_PACKET, LegacyOfTheLucii::handleRoyalArmsGuard);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                return RoyalArmsGuardAbility.allowDamage(player, source, amount);
            }
            return true;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            LuciiNetwork.sendState(handler.player);
            ArdynOverkillNetwork.sendAllStates(handler.player);
            ArdynOverkillNetwork.broadcastState(handler.player);
            RoyalArmsGuardNetwork.sendAllStates(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ArdynOverkillNetwork.broadcastState(handler.player, false);
            if (handler.player.getWorld() instanceof ServerWorld world) {
                RoyalArmsGuardNetwork.broadcastState(world, handler.player, false);
            }
            RoyalArmsWallAbility.clearAll(handler.player);
            RoyalArmsWarpStrikeAbility.clearAll(handler.player);
            RoyalArmsBindAbility.clearAll(handler.player);
            ArdynShadowStepAbility.clearAll(handler.player);
            RoyalArmsGuardAbility.clearAll(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            RoyalArmsWallAbility.clearAll(server);
            RoyalArmsWarpStrikeAbility.clearAll(server);
            RoyalArmsBindAbility.clearAll(server);
            ArdynShadowStepAbility.clearAll(server);
            RoyalArmsGuardAbility.clearAll(server);
        });
        ServerTickEvents.START_SERVER_TICK.register(RoyalArmsGuardAbility::tick);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            RoyalArmsOrbitDamageAbility.tick(server);
            RoyalArmsWallAbility.tick(server);
            RoyalArmsWarpStrikeAbility.tick(server);
            RoyalArmsBindAbility.tick(server);
            ArdynShadowStepAbility.tick(server);

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                LuciiPlayerState state = LuciiPlayerStates.get(player);
                if (player.getAbilities().creativeMode) {
                    if (state.restoreMana()) {
                        LuciiNetwork.sendState(player);
                    }
                    continue;
                }

                state.tick();
            }
            ArdynOverkillAbility.tick(server);
            MasqueradeManager.tick(server);

            if (server.getTicks() % 20 == 0) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    LuciiPlayerState state = LuciiPlayerStates.get(player);
                    LuciiNetwork.sendState(player);
                    if (state.royalArmsActive()) {
                        LuciiNetwork.broadcastRoyalArmsVisual(player);
                    }
                    if (RoyalArmsGuardAbility.isActive(player.getUuid())
                            && player.getWorld() instanceof ServerWorld world) {
                        RoyalArmsGuardNetwork.broadcastState(world, player, true);
                    }
                }
            }
        });
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (entity instanceof PlayerEntity player) {
                LuciiPlayerState state = LuciiPlayerStates.get(player);
                if (state.hasLegacy()) {
                    state.addExperience(5);
                }
                if (player instanceof ServerPlayerEntity ardyn
                        && killedEntity instanceof ServerPlayerEntity killedPlayer
                        && state.legacy() == ru.siyoga.legacyofthelucii.legacy.LuciiLegacy.ARDYN) {
                    MasqueradeManager.unlockMorph(ardyn, killedPlayer.getGameProfile());
                }
            }
        });
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            RoyalArmsWallAbility.clearAll(oldPlayer);
            RoyalArmsWarpStrikeAbility.clearAll(oldPlayer);
            RoyalArmsBindAbility.clearAll(oldPlayer);
            ArdynShadowStepAbility.clearAll(oldPlayer);
            RoyalArmsGuardAbility.clearAll(oldPlayer);

            NbtCompound nbt = new NbtCompound();
            LuciiPlayerState oldState = LuciiPlayerStates.get(oldPlayer);
            oldState.writeNbt(nbt);
            LuciiPlayerState newState = LuciiPlayerStates.get(newPlayer);
            newState.readNbt(nbt);

            if (!alive) {
                oldState.endArdynOverkill();
                newState.endArdynOverkill();
                ArdynOverkillNetwork.broadcastState(oldPlayer, false);
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            LuciiPlayerState newState = LuciiPlayerStates.get(newPlayer);
            if (!alive) {
                newState.endArdynOverkill();
            }

            LuciiNetwork.sendState(newPlayer);
            RoyalArmsGuardAbility.clearAll(newPlayer);
            if (newPlayer.getWorld() instanceof ServerWorld world) {
                RoyalArmsGuardNetwork.broadcastState(world, newPlayer, false);
            }
            ArdynOverkillNetwork.broadcastState(newPlayer, alive && newState.ardynOverkillActive());
            LuciiNetwork.broadcastRoyalArmsVisual(newPlayer);

            if (!alive && newPlayer.getServer() != null) {
                newPlayer.getServer().execute(() -> {
                    if (!newPlayer.isRemoved()) {
                        LuciiNetwork.sendState(newPlayer);
                        ArdynOverkillNetwork.broadcastState(newPlayer, false);
                    }
                });
            }
        });
        LOGGER.info("Legacy of the Lucii initialized.");
    }

    private static void handleRoyalArmsToggle(
            net.minecraft.server.MinecraftServer server,
            ServerPlayerEntity player,
            net.minecraft.server.network.ServerPlayNetworkHandler handler,
            PacketByteBuf buf,
            PacketSender responseSender
    ) {
        boolean active = buf.readBoolean();

        server.execute(() -> {
            LuciiPlayerState state = LuciiPlayerStates.get(player);
            state.setRoyalArmsActive(active);
            if (!state.royalArmsActive()) {
                RoyalArmsWallAbility.deactivate(player, false);
                RoyalArmsBindAbility.clearAll(player);
                RoyalArmsGuardAbility.clearAll(player);
                if (player.getWorld() instanceof ServerWorld world) {
                    RoyalArmsGuardNetwork.broadcastState(world, player, false);
                }
            }
            if (!state.royalArmsActive()) {
                LuciiNetwork.broadcastRoyalArmsVisual(player);
                LuciiNetwork.sendState(player);
            } else {
                LuciiNetwork.sendState(player);
                LuciiNetwork.broadcastRoyalArmsVisual(player);
            }
        });
    }

    private static void handleRoyalArmsInventoryOpen(
            net.minecraft.server.MinecraftServer server,
            ServerPlayerEntity player,
            net.minecraft.server.network.ServerPlayNetworkHandler handler,
            PacketByteBuf buf,
            PacketSender responseSender
    ) {
        server.execute(() -> {
            LuciiPlayerState state = LuciiPlayerStates.get(player);
            if (!state.hasLegacy()) {
                player.sendMessage(Text.translatable("message.legacyofthelucii.royal_arms.requires_legacy"), true);
                return;
            }
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, playerInventory, ignored) -> RoyalArmsScreenHandler.server(
                            syncId,
                            playerInventory,
                            state
                    ),
                    Text.translatable("screen.legacyofthelucii.royal_arms.inventory")
            ));
        });
    }

    private static void handleRoyalArmsWall(
            net.minecraft.server.MinecraftServer server,
            ServerPlayerEntity player,
            net.minecraft.server.network.ServerPlayNetworkHandler handler,
            PacketByteBuf buf,
            PacketSender responseSender
    ) {
        server.execute(() -> RoyalArmsWallAbility.toggle(player));
    }

    private static void handleRoyalArmsWarp(
            net.minecraft.server.MinecraftServer server,
            ServerPlayerEntity player,
            net.minecraft.server.network.ServerPlayNetworkHandler handler,
            PacketByteBuf buf,
            PacketSender responseSender
    ) {
        server.execute(() -> RoyalArmsWarpStrikeAbility.start(player));
    }

    private static void handleRoyalArmsBind(
            net.minecraft.server.MinecraftServer server,
            ServerPlayerEntity player,
            net.minecraft.server.network.ServerPlayNetworkHandler handler,
            PacketByteBuf buf,
            PacketSender responseSender
    ) {
        int action = buf.readVarInt();
        server.execute(() -> RoyalArmsBindAbility.handleAction(player, action));
    }

    private static void handleArdynShadowStep(
            net.minecraft.server.MinecraftServer server,
            ServerPlayerEntity player,
            net.minecraft.server.network.ServerPlayNetworkHandler handler,
            PacketByteBuf buf,
            PacketSender responseSender
    ) {
        boolean active = buf.readBoolean();
        server.execute(() -> ArdynShadowStepAbility.setActive(player, active));
    }

    private static void handleRoyalArmsGuard(
            net.minecraft.server.MinecraftServer server,
            ServerPlayerEntity player,
            net.minecraft.server.network.ServerPlayNetworkHandler handler,
            PacketByteBuf buf,
            PacketSender responseSender
    ) {
        boolean active = buf.readBoolean();
        server.execute(() -> RoyalArmsGuardAbility.setActive(player, active));
    }

    private static void handleDebugDemonize(
            net.minecraft.server.MinecraftServer server,
            ServerPlayerEntity player,
            net.minecraft.server.network.ServerPlayNetworkHandler handler,
            PacketByteBuf buf,
            PacketSender responseSender
    ) {
        server.execute(() -> DebugDemonizeAbility.tryDemonize(player));
    }

    private static void handleRoyalArmsFilter(
            net.minecraft.server.MinecraftServer server,
            ServerPlayerEntity player,
            net.minecraft.server.network.ServerPlayNetworkHandler handler,
            PacketByteBuf buf,
            PacketSender responseSender
    ) {
        int filterOrdinal = buf.readVarInt();

        server.execute(() -> {
            LuciiPlayerState state = LuciiPlayerStates.get(player);
            state.setRoyalArmsFilter(RoyalArmsInventoryFilter.byOrdinal(filterOrdinal));
            if (state.royalArmsActive()) {
                LuciiNetwork.broadcastRoyalArmsVisual(player);
            }
        });
    }

    private static void handleRoyalArmsEquip(
            net.minecraft.server.MinecraftServer server,
            ServerPlayerEntity player,
            net.minecraft.server.network.ServerPlayNetworkHandler handler,
            PacketByteBuf buf,
            PacketSender responseSender
    ) {
        int slot = buf.readVarInt();
        server.execute(() -> swapIntoMainHand(player, slot));
    }

    private static void swapIntoMainHand(ServerPlayerEntity player, int slot) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (!state.hasLegacy() || !state.royalArmsActive()) {
            return;
        }
        if (slot < 0 || slot >= state.royalArmsUnlockedSlots()) {
            return;
        }

        SimpleInventory storage = state.royalArmsInventory();
        if (slot >= storage.size()) {
            return;
        }

        ItemStack targetStack = storage.getStack(slot);
        if (targetStack.isEmpty() || !state.royalArmsFilter().matches(targetStack)) {
            return;
        }

        int selectedSlot = player.getInventory().selectedSlot;
        ItemStack heldStack = player.getInventory().getStack(selectedSlot);
        player.getInventory().setStack(selectedSlot, targetStack);
        storage.setStack(slot, heldStack);
        storage.markDirty();
        player.currentScreenHandler.sendContentUpdates();
        LuciiNetwork.broadcastRoyalArmsVisual(player);
    }
}
