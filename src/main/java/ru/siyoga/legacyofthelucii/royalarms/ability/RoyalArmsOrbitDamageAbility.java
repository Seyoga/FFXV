package ru.siyoga.legacyofthelucii.royalarms.ability;

import com.google.common.collect.Multimap;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;
import ru.siyoga.legacyofthelucii.royalarms.orbit.RoyalArmsOrbitMath;
import ru.siyoga.legacyofthelucii.royalarms.orbit.RoyalArmsOrbitState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RoyalArmsOrbitDamageAbility {
    public static final TagKey<Item> ROYAL_ARMS_WEAPONS = TagKey.of(
            RegistryKeys.ITEM,
            new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_weapons")
    );

    private static final double HIT_RADIUS = 0.55D;
    private static final double MAX_CONTINUOUS_OWNER_MOVEMENT_SQUARED = 64.0D;
    private static final float BASE_TOUCH_DAMAGE = 0.2F;
    private static final float WEAPON_DAMAGE_MULTIPLIER = 1.0F / 10.0F;
    private static final int HIT_COOLDOWN_TICKS = 10;
    private static final int GUARD_EXIT_DAMAGE_DELAY_TICKS = 45;
    private static final int ABILITY_EXIT_DAMAGE_DELAY_TICKS = 10;

    private static final Map<DamageKey, Integer> HIT_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, RoyalArmsOrbitState> ORBIT_STATES = new HashMap<>();
    private static final Map<UUID, Map<String, Vec3d>> PREVIOUS_ITEM_POSITIONS = new HashMap<>();
    private static final Map<UUID, Vec3d> PREVIOUS_OWNER_POSITIONS = new HashMap<>();
    private static final Map<UUID, net.minecraft.registry.RegistryKey<World>> PREVIOUS_OWNER_WORLDS = new HashMap<>();
    private static final Map<UUID, Integer> VISUAL_OVERRIDE_EXIT_DAMAGE_DELAYS = new HashMap<>();

    private RoyalArmsOrbitDamageAbility() {
    }

    public static void tick(MinecraftServer server) {
        tickCooldowns();
        clearInactivePlayerOrbitStates(server);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            LuciiPlayerState state = LuciiPlayerStates.get(player);
            if (!state.royalArmsActive()) {
                continue;
            }
            tickPlayer(player);
        }
    }

    private static void tickCooldowns() {
        HIT_COOLDOWNS.replaceAll((key, ticks) -> ticks - 1);
        HIT_COOLDOWNS.values().removeIf(ticks -> ticks <= 0);
    }

    private static void clearInactivePlayerOrbitStates(MinecraftServer server) {
        Map<UUID, Boolean> activePlayers = new HashMap<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            activePlayers.put(player.getUuid(), LuciiPlayerStates.get(player).royalArmsActive());
        }

        ORBIT_STATES.keySet().removeIf(uuid -> !activePlayers.getOrDefault(uuid, false));
        PREVIOUS_ITEM_POSITIONS.keySet().removeIf(uuid -> !activePlayers.getOrDefault(uuid, false));
        PREVIOUS_OWNER_POSITIONS.keySet().removeIf(uuid -> !activePlayers.getOrDefault(uuid, false));
        PREVIOUS_OWNER_WORLDS.keySet().removeIf(uuid -> !activePlayers.getOrDefault(uuid, false));
        VISUAL_OVERRIDE_EXIT_DAMAGE_DELAYS.keySet().removeIf(uuid -> !activePlayers.getOrDefault(uuid, false));
    }

    private static void tickPlayer(ServerPlayerEntity owner) {
        List<RoyalArmsInventoryItems.OrbitItem> items = RoyalArmsInventoryItems.collectSlots(owner);
        LuciiPlayerState state = LuciiPlayerStates.get(owner);
        ServerWorld world = owner.getServerWorld();
        UUID ownerUuid = owner.getUuid();
        RoyalArmsOrbitState orbitState = ORBIT_STATES.computeIfAbsent(ownerUuid, ignored -> new RoyalArmsOrbitState());
        RoyalArmsOrbitState.TickResult tickResult = orbitState.tick(
                world.getTime(),
                owner.isSneaking(),
                state.legacy(),
                state.ardynWarpCharges(),
                items
        );
        if (tickResult.itemsChanged()) {
            LuciiNetwork.broadcastRoyalArmsVisual(owner);
        } else if (tickResult.motionModeChanged()) {
            LuciiNetwork.broadcastRoyalArmsOrbitState(owner);
        }

        boolean guardActive = RoyalArmsGuardAbility.isActive(ownerUuid);
        boolean bindActive = RoyalArmsBindAbility.isBinding(owner);
        boolean barrageActive = RoyalArmsWarpStrikeAbility.isArdynBarrageActive(owner);
        boolean visualOverrideActive = guardActive || bindActive || barrageActive;
        if (visualOverrideActive) {
            int delay = guardActive ? GUARD_EXIT_DAMAGE_DELAY_TICKS : ABILITY_EXIT_DAMAGE_DELAY_TICKS;
            VISUAL_OVERRIDE_EXIT_DAMAGE_DELAYS.merge(ownerUuid, delay, Math::max);
        }
        int visualOverrideExitDelay = VISUAL_OVERRIDE_EXIT_DAMAGE_DELAYS.getOrDefault(ownerUuid, 0);
        if (!visualOverrideActive && visualOverrideExitDelay > 0) {
            visualOverrideExitDelay--;
            if (visualOverrideExitDelay == 0) {
                VISUAL_OVERRIDE_EXIT_DAMAGE_DELAYS.remove(ownerUuid);
            } else {
                VISUAL_OVERRIDE_EXIT_DAMAGE_DELAYS.put(ownerUuid, visualOverrideExitDelay);
            }
        }

        if (items.isEmpty()
                || visualOverrideActive
                || visualOverrideExitDelay > 0) {
            resetPreviousPositions(owner);
            return;
        }

        damageAlongOrbit(owner, orbitState.snapshot(world.getTime()));
    }

    private static void damageAlongOrbit(ServerPlayerEntity owner, RoyalArmsOrbitState.Snapshot snapshot) {
        ServerWorld world = owner.getServerWorld();
        Vec3d ownerPos = owner.getPos();
        UUID ownerUuid = owner.getUuid();
        Vec3d previousOwnerPos = PREVIOUS_OWNER_POSITIONS.get(ownerUuid);
        net.minecraft.registry.RegistryKey<World> previousWorld = PREVIOUS_OWNER_WORLDS.get(ownerUuid);
        boolean discontinuity = previousOwnerPos == null
                || !previousWorld.equals(world.getRegistryKey())
                || previousOwnerPos.squaredDistanceTo(ownerPos) > MAX_CONTINUOUS_OWNER_MOVEMENT_SQUARED;
        Map<String, Vec3d> previousPositions = PREVIOUS_ITEM_POSITIONS.computeIfAbsent(
                ownerUuid,
                ignored -> new HashMap<>()
        );
        if (discontinuity) {
            previousPositions.clear();
        }

        Map<LivingEntity, Float> pendingDamage = new HashMap<>();
        Map<String, Vec3d> currentPositions = new HashMap<>();
        for (RoyalArmsOrbitState.SlotSnapshot slot : snapshot.slots()) {
            Vec3d currentPos = RoyalArmsOrbitMath.position(
                    ownerPos,
                    slot.index(),
                    snapshot.phase(),
                    slot.baseAngle(),
                    slot.innerProgress(),
                    slot.innerTarget(),
                    RoyalArmsOrbitMath.appearanceProgress(slot.spawnTicks())
            );
            Vec3d previousPos = previousPositions.getOrDefault(slot.key(), currentPos);
            currentPositions.put(slot.key(), currentPos);
            Box sweptBounds = boxBetween(previousPos, currentPos).expand(HIT_RADIUS);
            float damage = damageFor(slot.stack());

            for (LivingEntity target : world.getEntitiesByClass(
                    LivingEntity.class,
                    sweptBounds,
                    target -> canDamage(owner, target)
            )) {
                if (RoyalArmsBindAbility.isBoundTarget(owner, target)) {
                    continue;
                }
                if (!intersectsSweptPoint(previousPos, currentPos, target.getBoundingBox().expand(HIT_RADIUS))) {
                    continue;
                }
                pendingDamage.merge(target, damage, Math::max);
            }
        }

        PREVIOUS_ITEM_POSITIONS.put(ownerUuid, currentPositions);
        PREVIOUS_OWNER_POSITIONS.put(ownerUuid, ownerPos);
        PREVIOUS_OWNER_WORLDS.put(ownerUuid, world.getRegistryKey());

        for (Map.Entry<LivingEntity, Float> entry : pendingDamage.entrySet()) {
            LivingEntity target = entry.getKey();
            DamageKey key = new DamageKey(owner.getUuid(), target.getUuid());
            if (HIT_COOLDOWNS.containsKey(key)) {
                continue;
            }

            if (target.damage(owner.getDamageSources().playerAttack(owner), entry.getValue())) {
                HIT_COOLDOWNS.put(key, HIT_COOLDOWN_TICKS);
                addArdynWarpCharge(owner);
            }
        }
    }

    private static Box boxBetween(Vec3d from, Vec3d to) {
        return new Box(
                Math.min(from.x, to.x),
                Math.min(from.y, to.y),
                Math.min(from.z, to.z),
                Math.max(from.x, to.x),
                Math.max(from.y, to.y),
                Math.max(from.z, to.z)
        );
    }

    private static boolean intersectsSweptPoint(Vec3d from, Vec3d to, Box box) {
        return box.contains(from) || box.contains(to) || box.raycast(from, to).isPresent();
    }

    private static void resetPreviousPositions(ServerPlayerEntity owner) {
        UUID ownerUuid = owner.getUuid();
        PREVIOUS_ITEM_POSITIONS.remove(ownerUuid);
        PREVIOUS_OWNER_POSITIONS.remove(ownerUuid);
        PREVIOUS_OWNER_WORLDS.remove(ownerUuid);
    }

    public static RoyalArmsOrbitState.Snapshot snapshot(ServerPlayerEntity owner) {
        ServerWorld world = owner.getServerWorld();
        LuciiPlayerState playerState = LuciiPlayerStates.get(owner);
        RoyalArmsOrbitState orbitState = ORBIT_STATES.computeIfAbsent(
                owner.getUuid(),
                ignored -> new RoyalArmsOrbitState()
        );
        orbitState.synchronizeItems(
                playerState.legacy(),
                playerState.ardynWarpCharges(),
                RoyalArmsInventoryItems.collectSlots(owner)
        );
        return orbitState.snapshot(world.getTime());
    }

    private static void addArdynWarpCharge(ServerPlayerEntity owner) {
        LuciiPlayerState state = LuciiPlayerStates.get(owner);
        if (state.legacy() != LuciiLegacy.ARDYN) {
            return;
        }

        if (!state.addArdynWarpCharge()) {
            return;
        }

        int charges = state.ardynWarpCharges();
        if (charges % 3 == 0) {
            int stage = charges / 3;
            float pitch = 0.65F + stage * 0.22F;
            owner.getServerWorld().playSound(
                    null,
                    owner.getX(),
                    owner.getY(),
                    owner.getZ(),
                    SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                    SoundCategory.PLAYERS,
                    0.85F,
                    pitch
            );
        }

        LuciiNetwork.sendState(owner);
        LuciiNetwork.broadcastRoyalArmsVisual(owner);
    }

    private static boolean canDamage(ServerPlayerEntity owner, LivingEntity target) {
        return target.isAlive()
                && target != owner
                && !target.isSpectator()
                && target.getWorld() == owner.getWorld();
    }

    private static float damageFor(ItemStack stack) {
        float weaponDamage = weaponAttackDamage(stack);
        if (stack.isIn(ROYAL_ARMS_WEAPONS) || weaponDamage > 0.0F) {
            return weaponDamage * WEAPON_DAMAGE_MULTIPLIER;
        }

        return BASE_TOUCH_DAMAGE;
    }

    private static float weaponAttackDamage(ItemStack stack) {
        Multimap<EntityAttribute, EntityAttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        float damage = 0.0F;
        for (EntityAttributeModifier modifier : modifiers.get(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
            damage += modifier.getValue();
        }

        return damage <= 0.0F ? 0.0F : damage + 1.0F;
    }

    private record DamageKey(UUID ownerUuid, UUID targetUuid) {
    }
}
