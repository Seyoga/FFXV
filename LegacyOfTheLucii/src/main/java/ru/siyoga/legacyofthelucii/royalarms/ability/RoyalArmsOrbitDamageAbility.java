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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RoyalArmsOrbitDamageAbility {
    public static final TagKey<Item> ROYAL_ARMS_WEAPONS = TagKey.of(
            RegistryKeys.ITEM,
            new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_weapons")
    );

    private static final double RADIUS = 2.5D;
    private static final double INNER_RADIUS = 1.45D;
    private static final double ORBIT_Y_OFFSET = 1.0D;
    private static final double BOB_HEIGHT = 0.3D;
    private static final float NORMAL_ORBIT_SPEED = 2.0F;
    private static final float FAST_ORBIT_SPEED = 5.0F;
    private static final float ARDYN_INNER_RING_SPEED_MULTIPLIER = 1.65F;
    private static final double HIT_RADIUS = 0.55D;
    private static final float BASE_TOUCH_DAMAGE = 0.2F;
    private static final float WEAPON_DAMAGE_MULTIPLIER = 1.0F / 10.0F;
    private static final int HIT_COOLDOWN_TICKS = 10;

    private static final Map<DamageKey, Integer> HIT_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Float> ORBIT_TIMES = new HashMap<>();
    private static final Map<UUID, Boolean> PREVIOUS_SNEAKING = new HashMap<>();
    private static final Map<UUID, Boolean> PAUSED_BY_DOUBLE_SNEAK = new HashMap<>();
    private static final Map<UUID, Integer> SNEAK_DOUBLE_TAP_WINDOWS = new HashMap<>();

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

            if (RoyalArmsBindAbility.isBinding(player)) {
                continue;
            }

            if (RoyalArmsWarpStrikeAbility.isArdynBarrageActive(player)) {
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

        ORBIT_TIMES.keySet().removeIf(uuid -> !activePlayers.getOrDefault(uuid, false));
        PREVIOUS_SNEAKING.keySet().removeIf(uuid -> !activePlayers.getOrDefault(uuid, false));
        PAUSED_BY_DOUBLE_SNEAK.keySet().removeIf(uuid -> !activePlayers.getOrDefault(uuid, false));
        SNEAK_DOUBLE_TAP_WINDOWS.keySet().removeIf(uuid -> !activePlayers.getOrDefault(uuid, false));
    }

    private static float tickOrbitTime(ServerPlayerEntity owner) {
        UUID ownerUuid = owner.getUuid();
        boolean sneaking = owner.isSneaking();
        boolean wasSneaking = PREVIOUS_SNEAKING.getOrDefault(ownerUuid, false);
        int doubleTapTicks = SNEAK_DOUBLE_TAP_WINDOWS.getOrDefault(ownerUuid, 0);
        boolean paused = PAUSED_BY_DOUBLE_SNEAK.getOrDefault(ownerUuid, false);

        if (sneaking && !wasSneaking) {
            if (doubleTapTicks > 0) {
                paused = true;
                doubleTapTicks = 0;
            } else {
                doubleTapTicks = 8;
            }
        }

        if (!sneaking && wasSneaking) {
            paused = false;
        }

        if (doubleTapTicks > 0) {
            doubleTapTicks--;
        }

        float speed = paused && sneaking ? 0.0F : sneaking ? NORMAL_ORBIT_SPEED : FAST_ORBIT_SPEED;
        float time = ORBIT_TIMES.getOrDefault(ownerUuid, 0.0F) + speed;
        ORBIT_TIMES.put(ownerUuid, time);
        PREVIOUS_SNEAKING.put(ownerUuid, sneaking);
        PAUSED_BY_DOUBLE_SNEAK.put(ownerUuid, paused);
        SNEAK_DOUBLE_TAP_WINDOWS.put(ownerUuid, doubleTapTicks);
        return time;
    }

    private static void tickPlayer(ServerPlayerEntity owner) {
        List<ItemStack> stacks = RoyalArmsInventoryItems.collect(owner);
        if (stacks.isEmpty()) {
            return;
        }

        LuciiPlayerState state = LuciiPlayerStates.get(owner);
        ServerWorld world = owner.getServerWorld();
        Vec3d ownerPos = owner.getPos();
        float time = tickOrbitTime(owner);
        Box searchBox = owner.getBoundingBox().expand(RADIUS + HIT_RADIUS + 1.0D, 2.0D, RADIUS + HIT_RADIUS + 1.0D);
        Map<LivingEntity, Float> pendingDamage = new HashMap<>();

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            Vec3d itemPos = getItemPosition(i, stacks.size(), ownerPos, time, state.legacy(), state.ardynWarpCharges());
            float damage = damageFor(stack);

            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, searchBox, target -> canDamage(owner, target))) {
                if (RoyalArmsBindAbility.isBoundTarget(owner, target)) {
                    continue;
                }

                if (!target.getBoundingBox().expand(HIT_RADIUS).contains(itemPos)) {
                    continue;
                }

                pendingDamage.merge(target, damage, Math::max);
            }
        }

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

    private static Vec3d getItemPosition(int index, int total, Vec3d ownerPos, float time, LuciiLegacy legacy, int charges) {
        int visualIndex = index + 1;
        boolean innerRing = shouldUseArdynInnerRing(legacy, charges, visualIndex, total);
        float direction = innerRing ? -ARDYN_INNER_RING_SPEED_MULTIPLIER : 1.0F;
        float angleDegrees = getRingAngleDegrees(legacy, charges, visualIndex, total) + time * direction;
        double angle = Math.toRadians(angleDegrees);
        double radius = innerRing ? INNER_RADIUS : RADIUS;
        double x = Math.sin(angle) * radius;
        double z = Math.cos(angle) * radius;
        double y = ORBIT_Y_OFFSET + Math.sin(time * 0.05F + visualIndex) * BOB_HEIGHT;
        return ownerPos.add(x, y, z);
    }

    private static boolean shouldUseArdynInnerRing(LuciiLegacy legacy, int charges, int index, int total) {
        return index <= getArdynInnerRingCount(legacy, charges, total);
    }

    private static int getArdynInnerRingCount(LuciiLegacy legacy, int charges, int total) {
        if (legacy != LuciiLegacy.ARDYN || charges < 3 || total < 2) {
            return 0;
        }

        int stage = MathHelper.clamp(charges / 3, 1, 4);
        int maxInnerCount = Math.max(1, total / 2);
        return Math.max(1, MathHelper.ceil(maxInnerCount * (stage / 4.0F)));
    }

    private static float getRingAngleDegrees(LuciiLegacy legacy, int charges, int index, int total) {
        int innerCount = getArdynInnerRingCount(legacy, charges, total);
        if (innerCount <= 0) {
            return index * 360.0F / total;
        }

        if (index <= innerCount) {
            return index * 360.0F / innerCount;
        }

        int outerCount = total - innerCount;
        if (outerCount <= 0) {
            return index * 360.0F / total;
        }

        int outerIndex = index - innerCount;
        return outerIndex * 360.0F / outerCount;
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
