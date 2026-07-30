package ru.siyoga.legacyofthelucii;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
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
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.siyoga.legacyofthelucii.block.LegacyBlocks;
import ru.siyoga.legacyofthelucii.entity.LegacyEntities;
import ru.siyoga.legacyofthelucii.item.LegacyItems;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.legacy.RoyalArmsInventoryFilter;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynShadowStepAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsBindAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsOrbitDamageAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsWallAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsWarpStrikeAbility;

public final class LegacyOfTheLucii implements ModInitializer {
    public static final String MOD_ID = "legacyofthelucii";
    public static final Logger LOGGER = LoggerFactory.getLogger("Legacy of the Lucii");
    public static final Identifier ROYAL_ARMS_EQUIP_PACKET = new Identifier(MOD_ID, "royal_arms_equip");

    @Override
    public void onInitialize() {
        LegacyBlocks.register();
        LegacyEntities.register();
        LegacyItems.register();
        ServerPlayNetworking.registerGlobalReceiver(ROYAL_ARMS_EQUIP_PACKET, LegacyOfTheLucii::handleRoyalArmsEquip);
        ServerPlayNetworking.registerGlobalReceiver(LuciiNetwork.ROYAL_ARMS_TOGGLE_PACKET, LegacyOfTheLucii::handleRoyalArmsToggle);
        ServerPlayNetworking.registerGlobalReceiver(LuciiNetwork.ROYAL_ARMS_FILTER_PACKET, LegacyOfTheLucii::handleRoyalArmsFilter);
        ServerPlayNetworking.registerGlobalReceiver(LuciiNetwork.ROYAL_ARMS_WALL_PACKET, LegacyOfTheLucii::handleRoyalArmsWall);
        ServerPlayNetworking.registerGlobalReceiver(LuciiNetwork.ROYAL_ARMS_WARP_PACKET, LegacyOfTheLucii::handleRoyalArmsWarp);
        ServerPlayNetworking.registerGlobalReceiver(LuciiNetwork.ROYAL_ARMS_BIND_PACKET, LegacyOfTheLucii::handleRoyalArmsBind);
        ServerPlayNetworking.registerGlobalReceiver(LuciiNetwork.ARDYN_SHADOW_STEP_PACKET, LegacyOfTheLucii::handleArdynShadowStep);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> LuciiNetwork.sendState(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RoyalArmsWallAbility.clearAll(handler.player);
            RoyalArmsWarpStrikeAbility.clearAll(handler.player);
            RoyalArmsBindAbility.clearAll(handler.player);
            ArdynShadowStepAbility.clearAll(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            RoyalArmsWallAbility.clearAll(server);
            RoyalArmsWarpStrikeAbility.clearAll(server);
            RoyalArmsBindAbility.clearAll(server);
            ArdynShadowStepAbility.clearAll(server);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            RoyalArmsOrbitDamageAbility.tick(server);
            RoyalArmsWallAbility.tick(server);
            RoyalArmsWarpStrikeAbility.tick(server);
            RoyalArmsBindAbility.tick(server);
            ArdynShadowStepAbility.tick(server);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                LuciiPlayerState state = LuciiPlayerStates.get(player);
                state.tick();
                if (server.getTicks() % 20 == 0) {
                    LuciiNetwork.sendState(player);
                    if (state.royalArmsActive()) {
                        LuciiNetwork.broadcastRoyalArmsVisual(player);
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
            NbtCompound nbt = new NbtCompound();
            LuciiPlayerStates.get(oldPlayer).writeNbt(nbt);
            LuciiPlayerStates.get(newPlayer).readNbt(nbt);
            LuciiNetwork.sendState(newPlayer);
            LuciiNetwork.broadcastRoyalArmsVisual(newPlayer);
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
