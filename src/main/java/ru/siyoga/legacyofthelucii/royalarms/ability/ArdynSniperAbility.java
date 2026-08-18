package ru.siyoga.legacyofthelucii.royalarms.ability;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ru.siyoga.legacyofthelucii.entity.ArdynSniperBulletEntity;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;
import ru.siyoga.legacyofthelucii.sniper.ArdynSniperContent;
import ru.siyoga.legacyofthelucii.sniper.ArdynSniperNetwork;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ArdynSniperAbility {
    public static final double MAX_RANGE = 128.0D;
    public static final float DAMAGE = 6.0F;
    public static final int RELOAD_TICKS = 60;
    public static final int MANA_COST = 10;
    public static final double BULLET_SPEED = 6.0D;

    private static final double BULLET_SPAWN_FORWARD_OFFSET = 0.45D;
    private static final UUID MOVEMENT_SLOWDOWN_MODIFIER_ID = UUID.fromString("4d4cc3a7-14c6-4bf6-92d7-9e7ccf1d6798");
    private static final EntityAttributeModifier MOVEMENT_SLOWDOWN_MODIFIER = new EntityAttributeModifier(
            MOVEMENT_SLOWDOWN_MODIFIER_ID,
            "Ardyn sniper movement slowdown",
            -2.0D / 3.0D,
            EntityAttributeModifier.Operation.MULTIPLY_TOTAL
    );
    private static final Map<UUID, RuntimeState> STATES = new HashMap<>();

    private ArdynSniperAbility() {
    }

    public static void toggle(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        RuntimeState runtime = STATES.get(playerUuid);
        if (runtime != null && runtime.active) {
            runtime.active = false;
            removeMovementSlowdown(player);
            sendState(player, runtime);
            cleanupExpiredState(player.getServer(), playerUuid, runtime);
            return;
        }

        if (!canEnter(player)) {
            ArdynSniperNetwork.sendState(player, false, remainingCooldown(player, runtime));
            return;
        }

        if (runtime == null) {
            runtime = new RuntimeState();
            STATES.put(playerUuid, runtime);
        }
        runtime.active = true;
        runtime.world = player.getWorld().getRegistryKey();
        applyMovementSlowdown(player);
        sendState(player, runtime);
    }

    public static void shoot(ServerPlayerEntity player) {
        RuntimeState runtime = STATES.get(player.getUuid());
        if (runtime == null || !runtime.active) {
            ArdynSniperNetwork.sendState(player, false, remainingCooldown(player, runtime));
            return;
        }
        if (!canRemainActive(player, runtime)) {
            deactivate(player, runtime);
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        long now = server.getTicks();
        if (now < runtime.nextShotTick) {
            sendState(player, runtime);
            return;
        }

        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (!player.getAbilities().creativeMode && !state.hasMana(MANA_COST)) {
            player.sendMessage(Text.translatable("message.legacyofthelucii.royal_arms.not_enough_mana"), true);
            LuciiNetwork.sendState(player);
            sendState(player, runtime);
            return;
        }

        if (!player.getAbilities().creativeMode) {
            state.spendMana(MANA_COST);
            LuciiNetwork.sendState(player);
        }

        spawnBullet(player);
        runtime.nextShotTick = now + RELOAD_TICKS;
        sendState(player, runtime);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, RuntimeState>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, RuntimeState> entry = iterator.next();
            RuntimeState runtime = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

            if (player == null || player.isRemoved()) {
                iterator.remove();
                continue;
            }

            if (runtime.active && !canRemainActive(player, runtime)) {
                runtime.active = false;
                removeMovementSlowdown(player);
                ArdynSniperNetwork.sendState(player, false, remainingCooldown(server, runtime));
            } else if (runtime.active) {
                applyMovementSlowdown(player);
            } else {
                removeMovementSlowdown(player);
            }

            if (!runtime.active && runtime.nextShotTick <= server.getTicks()) {
                iterator.remove();
            }
        }
    }

    public static boolean isActive(UUID playerUuid) {
        RuntimeState runtime = STATES.get(playerUuid);
        return runtime != null && runtime.active;
    }

    public static void syncPlayer(ServerPlayerEntity player) {
        RuntimeState runtime = STATES.get(player.getUuid());
        if (runtime != null && runtime.active) {
            applyMovementSlowdown(player);
        } else {
            removeMovementSlowdown(player);
        }
        ArdynSniperNetwork.sendState(
                player,
                runtime != null && runtime.active,
                remainingCooldown(player, runtime)
        );
    }

    public static void clearPlayer(ServerPlayerEntity player, String reason) {
        clearPlayer(player, reason, true);
    }

    public static void clearPlayer(ServerPlayerEntity player, String reason, boolean notifyClient) {
        RuntimeState removed = STATES.remove(player.getUuid());
        removeMovementSlowdown(player);
        if (removed != null && notifyClient) {
            ArdynSniperNetwork.sendState(player, false, 0);
        }
    }

    public static void clearAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            removeMovementSlowdown(player);
            if (STATES.containsKey(player.getUuid())) {
                ArdynSniperNetwork.sendState(player, false, 0);
            }
        }
        STATES.clear();
    }

    private static boolean canEnter(ServerPlayerEntity player) {
        if (player.isRemoved() || player.isDead() || player.isSpectator()) {
            return false;
        }
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() != LuciiLegacy.ARDYN || state.ardynOverkillActive()) {
            return false;
        }
        return !hasIncompatibleMode(player);
    }

    private static boolean canRemainActive(ServerPlayerEntity player, RuntimeState runtime) {
        if (!canEnter(player)) {
            return false;
        }
        return runtime.world != null && runtime.world.equals(player.getWorld().getRegistryKey());
    }

    private static boolean hasIncompatibleMode(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        return ArdynDarkTornadoAbility.isTargeting(playerUuid)
                || ArdynDarkTornadoAbility.isActive(playerUuid)
                || ArdynPointWarpAbility.isActive(playerUuid)
                || ArdynShadowStepAbility.isActive(playerUuid)
                || RoyalArmsWarpStrikeAbility.isArdynBarrageActive(player)
                || RoyalArmsBindAbility.isBinding(player);
    }

    private static void spawnBullet(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        Vec3d start = player.getEyePos().add(direction.multiply(BULLET_SPAWN_FORWARD_OFFSET));

        ArdynSniperBulletEntity bullet = new ArdynSniperBulletEntity(
                ArdynSniperContent.SNIPER_BULLET_ENTITY,
                world
        );
        bullet.setPosition(start.x, start.y, start.z);
        bullet.configure(
                player,
                direction.multiply(BULLET_SPEED),
                DAMAGE,
                MAX_RANGE
        );
        world.spawnEntity(bullet);
    }

    private static void deactivate(ServerPlayerEntity player, RuntimeState runtime) {
        runtime.active = false;
        removeMovementSlowdown(player);
        sendState(player, runtime);
        cleanupExpiredState(player.getServer(), player.getUuid(), runtime);
    }

    private static void sendState(ServerPlayerEntity player, RuntimeState runtime) {
        ArdynSniperNetwork.sendState(player, runtime.active, remainingCooldown(player, runtime));
    }

    private static int remainingCooldown(ServerPlayerEntity player, RuntimeState runtime) {
        return remainingCooldown(player.getServer(), runtime);
    }

    private static int remainingCooldown(MinecraftServer server, RuntimeState runtime) {
        if (server == null || runtime == null) {
            return 0;
        }
        long remaining = Math.max(0L, runtime.nextShotTick - server.getTicks());
        return (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    private static void applyMovementSlowdown(ServerPlayerEntity player) {
        EntityAttributeInstance movementSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movementSpeed == null || movementSpeed.getModifier(MOVEMENT_SLOWDOWN_MODIFIER_ID) != null) {
            return;
        }
        movementSpeed.addTemporaryModifier(MOVEMENT_SLOWDOWN_MODIFIER);
    }

    private static void removeMovementSlowdown(ServerPlayerEntity player) {
        EntityAttributeInstance movementSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(MOVEMENT_SLOWDOWN_MODIFIER_ID);
        }
    }

    private static void cleanupExpiredState(MinecraftServer server, UUID playerUuid, RuntimeState runtime) {
        if (server != null && !runtime.active && runtime.nextShotTick <= server.getTicks()) {
            STATES.remove(playerUuid);
        }
    }

    private static final class RuntimeState {
        private boolean active;
        private RegistryKey<World> world;
        private long nextShotTick;
    }
}
