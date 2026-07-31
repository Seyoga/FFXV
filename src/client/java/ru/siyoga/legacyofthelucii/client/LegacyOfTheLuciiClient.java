package ru.siyoga.legacyofthelucii.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.block.LegacyBlocks;
import ru.siyoga.legacyofthelucii.client.config.LegacyClientConfig;
import ru.siyoga.legacyofthelucii.client.gui.skilltree.SkillTreeKeybindings;
import ru.siyoga.legacyofthelucii.client.hud.LuciiHudOverlay;
import ru.siyoga.legacyofthelucii.client.royalarms.RoyalArmsAbility;
import ru.siyoga.legacyofthelucii.client.royalarms.RoyalArmsGuardClient;
import ru.siyoga.legacyofthelucii.client.royalarms.RoyalArmsWallClient;
import ru.siyoga.legacyofthelucii.client.royalarms.ardyn.ArdynBarrageWeaponRenderer;
import ru.siyoga.legacyofthelucii.client.royalarms.ardyn.ArdynOverkillClient;
import ru.siyoga.legacyofthelucii.client.royalarms.ardyn.ArdynShadowStepClient;
import ru.siyoga.legacyofthelucii.client.royalarms.bind.RoyalArmsBindClient;
import ru.siyoga.legacyofthelucii.client.royalarms.wall.RoyalArmsWallAnimations;
import ru.siyoga.legacyofthelucii.client.royalarms.wall.RoyalArmsWallBlockEntityRenderer;
import ru.siyoga.legacyofthelucii.client.royalarms.warp.RoyalArmsWarpTrailClient;
import ru.siyoga.legacyofthelucii.client.state.ArdynOverkillClientState;
import ru.siyoga.legacyofthelucii.client.state.ClientLuciiState;
import ru.siyoga.legacyofthelucii.entity.LegacyEntities;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.network.ArdynOverkillNetwork;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;
import ru.siyoga.legacyofthelucii.network.RoyalArmsGuardNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LegacyOfTheLuciiClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LegacyClientConfig.load();
        ClientPlayNetworking.registerGlobalReceiver(LuciiNetwork.STATE_SYNC_PACKET, (client, handler, buf, responseSender) -> {
            LuciiLegacy legacy = LuciiLegacy.byId(buf.readString());
            int mana = buf.readVarInt();
            int maxMana = buf.readVarInt();
            boolean royalArmsActive = buf.readBoolean();
            int ardynWarpCharges = buf.readVarInt();
            boolean ardynOverkillActive = buf.readBoolean();
            client.execute(() -> {
                ClientLuciiState.update(
                        legacy,
                        mana,
                        maxMana,
                        royalArmsActive,
                        ardynWarpCharges,
                        ardynOverkillActive
                );

                // Keep the UUID-based map in sync for remote-player rendering, but
                // local HUD code no longer depends on this transient visual packet.
                if (client.player != null) {
                    ArdynOverkillClientState.update(client.player.getUuid(), ardynOverkillActive);
                }

                if (!ardynOverkillActive) {
                    ArdynOverkillClient.clearImmediately(client);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(ArdynOverkillNetwork.STATE_PACKET, (client, handler, buf, responseSender) -> {
            UUID ownerUuid = buf.readUuid();
            boolean active = buf.readBoolean();
            client.execute(() -> {
                ArdynOverkillClientState.update(ownerUuid, active);
                if (!active && client.player != null && ownerUuid.equals(client.player.getUuid())) {
                    // A respawn creates a clean player life. Remove the fake Wither HUD
                    // and screen overlay immediately instead of waiting for their fade/expiry.
                    ArdynOverkillClient.clearImmediately(client);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(LuciiNetwork.ROYAL_ARMS_VISUAL_PACKET, (client, handler, buf, responseSender) -> {
            UUID ownerUuid = buf.readUuid();
            boolean active = buf.readBoolean();
            LuciiLegacy legacy = LuciiLegacy.NONE;
            List<ItemStack> stacks = List.of();
            if (active) {
                legacy = LuciiLegacy.byId(buf.readString());
                int ardynWarpCharges = buf.readVarInt();
                int count = buf.readVarInt();
                List<ItemStack> receivedStacks = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    receivedStacks.add(buf.readItemStack());
                }
                stacks = receivedStacks;
                int visualArdynWarpCharges = ardynWarpCharges;
                LuciiLegacy visualLegacy = legacy;
                List<ItemStack> visualStacks = stacks;
                client.execute(() -> RoyalArmsAbility.updateRemoteVisual(ownerUuid, active, visualLegacy, visualStacks, visualArdynWarpCharges));
                return;
            }

            LuciiLegacy visualLegacy = legacy;
            List<ItemStack> visualStacks = stacks;
            client.execute(() -> RoyalArmsAbility.updateRemoteVisual(ownerUuid, active, visualLegacy, visualStacks, 0));
        });
        ClientPlayNetworking.registerGlobalReceiver(LuciiNetwork.ROYAL_ARMS_WALL_ANIMATION_PACKET, (client, handler, buf, responseSender) -> {
            UUID ownerUuid = buf.readUuid();
            BlockPos pos = buf.readBlockPos();
            BlockState sourceState = Block.getStateFromRawId(buf.readVarInt());
            boolean appearing = buf.readBoolean();
            client.execute(() -> RoyalArmsWallAnimations.add(ownerUuid, pos, sourceState, appearing));
        });
        ClientPlayNetworking.registerGlobalReceiver(LuciiNetwork.ROYAL_ARMS_WARP_TRAIL_PACKET, (client, handler, buf, responseSender) -> {
            UUID ownerUuid = buf.readUuid();
            LuciiLegacy legacy = LuciiLegacy.byId(buf.readString());
            Vec3d from = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3d to = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            float yaw = buf.readFloat();
            client.execute(() -> RoyalArmsWarpTrailClient.add(ownerUuid, legacy, from, to, yaw));
        });
        ClientPlayNetworking.registerGlobalReceiver(LuciiNetwork.ARDYN_WARP_CHARGE_PACKET, (client, handler, buf, responseSender) -> {
            UUID ownerUuid = buf.readUuid();
            boolean active = buf.readBoolean();
            client.execute(() -> RoyalArmsWarpTrailClient.updateArdynCharge(ownerUuid, active));
        });
        ClientPlayNetworking.registerGlobalReceiver(LuciiNetwork.ARDYN_BARRAGE_VISUAL_PACKET, (client, handler, buf, responseSender) -> {
            UUID ownerUuid = buf.readUuid();
            boolean active = buf.readBoolean();
            client.execute(() -> RoyalArmsAbility.updateArdynBarrage(ownerUuid, active));
        });
        ClientPlayNetworking.registerGlobalReceiver(LuciiNetwork.ARDYN_SHADOW_STEP_VISUAL_PACKET, (client, handler, buf, responseSender) -> {
            UUID ownerUuid = buf.readUuid();
            boolean active = buf.readBoolean();
            client.execute(() -> ArdynShadowStepClient.update(ownerUuid, active));
        });
        ClientPlayNetworking.registerGlobalReceiver(LuciiNetwork.ROYAL_ARMS_BIND_VISUAL_PACKET, (client, handler, buf, responseSender) -> {
            UUID ownerUuid = buf.readUuid();
            int targetEntityId = buf.readVarInt();
            Vec3d targetCenter = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            boolean active = buf.readBoolean();
            boolean impaled = buf.readBoolean();
            LuciiLegacy legacy = LuciiLegacy.byId(buf.readString());
            int count = buf.readVarInt();
            List<ItemStack> stacks = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                stacks.add(buf.readItemStack());
            }
            client.execute(() -> RoyalArmsBindClient.update(ownerUuid, targetEntityId, targetCenter, active, impaled, legacy, stacks));
        });
        ClientPlayNetworking.registerGlobalReceiver(RoyalArmsGuardNetwork.STATE_PACKET, (client, handler, buf, responseSender) -> {
            boolean active = buf.readBoolean();
            client.execute(() -> RoyalArmsGuardClient.updateState(active));
        });
        ClientPlayNetworking.registerGlobalReceiver(RoyalArmsGuardNetwork.BLOCK_VISUAL_PACKET, (client, handler, buf, responseSender) -> {
            UUID ownerUuid = buf.readUuid();
            Vec3d interceptPos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3d incomingVelocity = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            client.execute(() -> RoyalArmsGuardClient.block(ownerUuid, interceptPos, incomingVelocity));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // Clear client-only Wither hearts and the color filter before the old
            // ClientPlayerEntity is discarded. Otherwise static renderer state can
            // leak into the next world/session.
            ArdynOverkillClient.reset(client);
            ClientLuciiState.reset();
            ArdynOverkillClientState.reset();
            RoyalArmsAbility.clearRemoteVisuals();
            RoyalArmsWallAnimations.clear();
            RoyalArmsWarpTrailClient.clear();
            ArdynShadowStepClient.clear();
            RoyalArmsBindClient.clear();
            RoyalArmsGuardClient.reset();
        });
        LuciiHudOverlay.register();
        ArdynOverkillClient.register();
        RoyalArmsAbility.register();
        RoyalArmsWallClient.register();
        RoyalArmsWallAnimations.register();
        RoyalArmsWarpTrailClient.register();
        ArdynShadowStepClient.register();
        RoyalArmsBindClient.register();
        SkillTreeKeybindings.register();
        EntityRendererRegistry.register(LegacyEntities.ARDYN_BARRAGE_WEAPON, ArdynBarrageWeaponRenderer::new);
        BlockEntityRendererFactories.register(LegacyBlocks.ROYAL_ARMS_WALL_BLOCK_ENTITY, RoyalArmsWallBlockEntityRenderer::new);
    }
}
