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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
import ru.siyoga.legacyofthelucii.network.RoyalArmsGuardNetwork;
import ru.siyoga.legacyofthelucii.particle.LegacyParticles;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynOverkillAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynShadowStepAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.DebugDemonizeAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsBindAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsGuardAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsOrbitDamageAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsWallAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsWarpStrikeAbility;

@SuppressWarnings("unused")
public final class LegacyOfTheLucii implements ModInitializer {
    public static final String MOD_ID = "legacyofthelucii";
    public static final Logger LOGGER = LoggerFactory.getLogger("Legacy of the Lucii");
    public static final Identifier ROYAL_ARMS_EQUIP_PACKET = new Identifier(MOD_ID, "royal_arms_equip");

    @Override
    public void onInitialize() {
        LegacyBlocks.register();
        LegacyEntities.register();
        LegacyStatusEffects.register();
        LegacyParticles.register();
        LegacyItems.register();
        ServerPlayNetworking.registerGlobalReceiver(ROYAL_ARMS_EQUIP_PACKET, LegacyOfTheLucii::handleRoyalArmsEquip);
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
            // Restore all active forms for the joining client, including its own persisted state.
            ArdynOverkillNetwork.sendAllStates(handler.player);
            // Existing clients also need the rejoining player's restored state.
            ArdynOverkillNetwork.broadcastState(handler.player);
            RoyalArmsGuardNetwork.sendAllStates(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // This only clears remote client visuals for the absent entity. The server-side
            // Overkill flag and mana remain in player NBT and resume on the next login.
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
                // A real death ends Overkill on BOTH entity instances. Clearing the old
                // state prevents any late sync from resurrecting the visual flag while the
                // client is replacing its player entity.
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

            // AFTER_RESPAWN runs after the replacement ServerPlayerEntity exists. Send the
            // authoritative state here rather than only from COPY_FROM, which is documented
            // to run before the respawn is completely finished.
            LuciiNetwork.sendState(newPlayer);
            RoyalArmsGuardAbility.clearAll(newPlayer);
            if (newPlayer.getWorld() instanceof ServerWorld world) {
                RoyalArmsGuardNetwork.broadcastState(world, newPlayer, false);
            }
            ArdynOverkillNetwork.broadcastState(newPlayer, alive && newState.ardynOverkillActive());
            LuciiNetwork.broadcastRoyalArmsVisual(newPlayer);

            // Queue one duplicate sync for the next server task. This reaches the client
            // after its local player reference has changed and clears any fake Wither HUD
            // instance or screen overlay that belonged to the dead entity.
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
            LuciiNetwork.sendState(player);
            LuciiNetwork.broadcastRoyalArmsVisual(player);
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

        int selectedSlot = player.getInventory().selectedSlot;
        if (slot == -1) {
            ItemStack offhandStack = player.getInventory().offHand.get(0);
            if (offhandStack.isEmpty()) {
                return;
            }

            ItemStack heldStack = player.getInventory().getStack(selectedSlot);
            player.getInventory().setStack(selectedSlot, offhandStack);
            player.getInventory().offHand.set(0, heldStack);
            player.currentScreenHandler.sendContentUpdates();
            return;
        }

        if (slot < 0 || slot >= player.getInventory().main.size()) {
            return;
        }

        if (slot == selectedSlot) {
            return;
        }

        ItemStack targetStack = player.getInventory().getStack(slot);
        if (targetStack.isEmpty()) {
            return;
        }

        ItemStack heldStack = player.getInventory().getStack(selectedSlot);
        player.getInventory().setStack(selectedSlot, targetStack);
        player.getInventory().setStack(slot, heldStack);
        player.currentScreenHandler.sendContentUpdates();
    }
}
