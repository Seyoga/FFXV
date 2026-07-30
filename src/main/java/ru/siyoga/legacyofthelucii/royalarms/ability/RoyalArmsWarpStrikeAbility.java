package ru.siyoga.legacyofthelucii.royalarms.ability;

import com.google.common.collect.Multimap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.entity.ArdynBarrageWeaponEntity;
import ru.siyoga.legacyofthelucii.entity.LegacyEntities;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RoyalArmsWarpStrikeAbility {
    public static final TagKey<Item> ROYAL_ARMS_WEAPONS = TagKey.of(
            RegistryKeys.ITEM,
            new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_weapons")
    );

    private static final double MAX_DISTANCE = 10.0D;
    private static final double PROJECTILE_SPEED = 1.65D;
    private static final double ENTITY_HIT_EXPAND = 0.35D;
    private static final double ITEM_ENTITY_HIT_EXPAND = 0.75D;
    private static final double WARP_DAMAGE_RADIUS = 2.0D;
    private static final double ARDYN_IMPACT_RADIUS = 2.75D;
    private static final double ARDYN_IMPACT_KNOCKBACK = 0.75D;
    private static final int MANA_COST = 20;
    private static final int WARP_TICKS = 10;
    private static final int ARDYN_PROJECTILE_TICKS = 12;
    private static final int ARDYN_CHARGE_TICKS = 14;
    private static final int ARDYN_BARRAGE_CHARGES_REQUIRED = 12;
    private static final int ARDYN_BARRAGE_FIRE_TICKS = 80;
    private static final int ARDYN_BARRAGE_RETURN_TICKS = 100;
    private static final int ARDYN_BARRAGE_SHOT_INTERVAL = 2;
    private static final int ARDYN_BARRAGE_MAX_FLIGHT_TICKS = 9;
    private static final int ARDYN_BARRAGE_RECALL_MAX_DELAY = 72;
    private static final double ARDYN_BARRAGE_DISTANCE = 16.0D;
    private static final double ARDYN_BARRAGE_PROJECTILE_SPEED = 1.85D;
    private static final double ARDYN_BARRAGE_HORIZONTAL_SPREAD = 0.38D;
    private static final double ARDYN_BARRAGE_VERTICAL_SPREAD = 0.24D;
    private static final double ARDYN_BARRAGE_START_SIDE_RADIUS = 1.55D;
    private static final float ARDYN_BARRAGE_NORMAL_DAMAGE = 0.5F;
    private static final float ARDYN_BARRAGE_WEAPON_MULTIPLIER = 0.15F;
    private static final double ARDYN_ARC_HEIGHT = 0.3D;
    private static final double ARDYN_MAX_ARC_ABOVE_THROW = 0.85D;
    private static final double ARDYN_THROW_FORWARD_OFFSET = 2.55D;
    private static final float FIST_DAMAGE = 1.0F;
    private static final float WEAPON_DAMAGE_MULTIPLIER = 0.5F;
    private static final DustParticleEffect NOCTIS_TRAIL_PARTICLE = new DustParticleEffect(new Vector3f(0.36F, 0.62F, 1.0F), 1.15F);
    private static final DustParticleEffect ARDYN_TRAIL_PARTICLE = new DustParticleEffect(new Vector3f(1.0F, 0.42F, 0.92F), 1.15F);
    private static final DustParticleEffect ARDYN_IMPACT_PARTICLE = new DustParticleEffect(new Vector3f(0.72F, 0.18F, 1.0F), 1.7F);

    private static final Map<UUID, ActiveWarp> ACTIVE_WARPS = new HashMap<>();
    private static final Map<UUID, ActiveArdynBarrage> ACTIVE_ARDYN_BARRAGES = new HashMap<>();

    private RoyalArmsWarpStrikeAbility() {
    }

    public static void tick(MinecraftServer server) {
        tickArdynBarrages(server);

        Iterator<Map.Entry<UUID, ActiveWarp>> iterator = ACTIVE_WARPS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveWarp> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            ActiveWarp warp = entry.getValue();
            ServerWorld world = server.getWorld(warp.world.getRegistryKey());
            if (player == null || world == null || player.isRemoved() || player.isDead()) {
                cleanup(warp, player);
                iterator.remove();
                continue;
            }

            if (warp.legacy == LuciiLegacy.ARDYN) {
                if (tickArdynWarp(world, player, warp)) {
                    returnHeldItem(player, warp);
                    cleanup(warp, player);
                    iterator.remove();
                }
                continue;
            }

            tickNoctisProjectile(world, player, warp);
            if (warp.ticksRemaining <= 0) {
                player.teleport(world, warp.teleportPos.x, warp.teleportPos.y, warp.teleportPos.z, player.getYaw(), player.getPitch());
                damageWarpTarget(player, world, warp);
                returnHeldItem(player, warp);
                cleanup(warp, player);
                iterator.remove();
            }
        }
    }

    public static void start(ServerPlayerEntity player) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        LuciiLegacy legacy = state.legacy();
        if ((legacy != LuciiLegacy.NOCTIS && legacy != LuciiLegacy.ARDYN) || !state.royalArmsActive()) {
            player.sendMessage(Text.translatable("message.legacyofthelucii.royal_arms.warp.requires_active"), true);
            return;
        }

        if (ACTIVE_WARPS.containsKey(player.getUuid())) {
            return;
        }

        if (ACTIVE_ARDYN_BARRAGES.containsKey(player.getUuid())) {
            return;
        }

        if (legacy == LuciiLegacy.ARDYN && state.ardynWarpCharges() >= ARDYN_BARRAGE_CHARGES_REQUIRED) {
            startArdynBarrage(player, state);
            return;
        }

        if (!player.getAbilities().creativeMode && !state.hasMana(MANA_COST)) {
            player.sendMessage(Text.translatable("message.legacyofthelucii.royal_arms.not_enough_mana"), true);
            return;
        }

        ItemStack heldStack = player.getMainHandStack();
        if (heldStack.isEmpty()) {
            player.sendMessage(Text.translatable("message.legacyofthelucii.royal_arms.warp.requires_item"), true);
            return;
        }

        ServerWorld world = player.getServerWorld();
        Vec3d start = player.getEyePos().subtract(0.0D, 0.2D, 0.0D);
        Vec3d look = player.getRotationVec(1.0F).normalize();
        HitResult hitResult = world.raycast(new RaycastContext(
                start,
                start.add(look.multiply(MAX_DISTANCE)),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        Vec3d target = hitResult.getType() == HitResult.Type.MISS ? start.add(look.multiply(MAX_DISTANCE)) : hitResult.getPos();
        LivingEntity targetEntity = raycastEntity(world, player, start, start.add(look.multiply(MAX_DISTANCE)), start.squaredDistanceTo(target));
        if (targetEntity != null) {
            target = targetEntity.getBoundingBox().getCenter();
        }
        Vec3d teleportPos = targetEntity == null
                ? safeTeleportPos(world, target, hitResult, player)
                : safeEntityTeleportPos(world, targetEntity, player);
        if (teleportPos == null) {
            return;
        }

        ItemStack reservedStack = reserveHeldItem(player);
        if (reservedStack.isEmpty()) {
            return;
        }

        if (!player.getAbilities().creativeMode) {
            state.spendMana(MANA_COST);
            LuciiNetwork.sendState(player);
        }

        Vec3d projectileStart = legacy == LuciiLegacy.ARDYN
                ? start.add(look.multiply(ARDYN_THROW_FORWARD_OFFSET)).add(0.0D, 0.05D, 0.0D)
                : start;
        ItemEntity itemEntity = new ItemEntity(world, projectileStart.x, projectileStart.y, projectileStart.z, reservedStack.copy());
        itemEntity.setPickupDelay(32767);
        itemEntity.setNoGravity(true);
        itemEntity.setInvulnerable(true);
        itemEntity.setVelocity(legacy == LuciiLegacy.ARDYN ? Vec3d.ZERO : look.multiply(PROJECTILE_SPEED));
        world.spawnEntity(itemEntity);
        Vec3d trailOrigin = player.getPos();
        LuciiNetwork.broadcastRoyalArmsWarpTrail(world, player, trailOrigin, teleportPos, legacy);

        UUID targetEntityUuid = targetEntity == null ? null : targetEntity.getUuid();
        ACTIVE_WARPS.put(player.getUuid(), new ActiveWarp(world, itemEntity, reservedStack, player.getInventory().selectedSlot, trailOrigin.add(0.0D, 1.0D, 0.0D), projectileStart, target, teleportPos, targetEntityUuid, legacy));
    }

    public static void clearAll(ServerPlayerEntity player) {
        ActiveWarp warp = ACTIVE_WARPS.remove(player.getUuid());
        if (warp != null) {
            returnHeldItem(player, warp);
            cleanup(warp, player);
        }
        ActiveArdynBarrage barrage = ACTIVE_ARDYN_BARRAGES.remove(player.getUuid());
        if (barrage != null) {
            cleanupArdynBarrage(barrage);
        }
    }

    public static void clearAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            clearAll(player);
        }
        ACTIVE_WARPS.clear();
        for (ActiveArdynBarrage barrage : ACTIVE_ARDYN_BARRAGES.values()) {
            cleanupArdynBarrage(barrage);
        }
        ACTIVE_ARDYN_BARRAGES.clear();
    }

    public static boolean isArdynBarrageActive(ServerPlayerEntity player) {
        return ACTIVE_ARDYN_BARRAGES.containsKey(player.getUuid());
    }

    private static void startArdynBarrage(ServerPlayerEntity player, LuciiPlayerState state) {
        List<ItemStack> stacks = RoyalArmsInventoryItems.collect(player);
        if (stacks.isEmpty()) {
            player.sendMessage(Text.translatable("message.legacyofthelucii.royal_arms.warp.requires_item"), true);
            return;
        }

        state.setArdynWarpCharges(0);
        LuciiNetwork.sendState(player);
        LuciiNetwork.broadcastRoyalArmsVisual(player);
        LuciiNetwork.broadcastArdynBarrage(player.getServerWorld(), player, true);

        ACTIVE_ARDYN_BARRAGES.put(player.getUuid(), new ActiveArdynBarrage(player.getUuid(), player.getServerWorld(), stacks));
    }

    private static void tickArdynBarrages(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveArdynBarrage>> iterator = ACTIVE_ARDYN_BARRAGES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveArdynBarrage> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            ActiveArdynBarrage barrage = entry.getValue();
            ServerWorld world = server.getWorld(barrage.world.getRegistryKey());
            if (player == null || world == null || player.isRemoved() || player.isDead()) {
                cleanupArdynBarrage(barrage);
                iterator.remove();
                continue;
            }

            barrage.age++;
            barrage.weapons.removeIf(Entity::isRemoved);
            if (barrage.age <= ARDYN_BARRAGE_FIRE_TICKS) {
                spawnArdynBarrageRingParticles(world, player);
                if (barrage.age % ARDYN_BARRAGE_SHOT_INTERVAL == 1) {
                    launchArdynBarrageShot(world, player, barrage);
                }
                continue;
            }

            if (!barrage.finalImpactDone) {
                barrage.finalImpactDone = true;
                Vec3d impactPos = lookTarget(world, player, ARDYN_BARRAGE_DISTANCE);
                Vec3d teleportPos = safeTeleportPos(world, impactPos, BlockHitResult.createMissed(impactPos, player.getHorizontalFacing(), BlockPos.ofFloored(impactPos)), player);
                if (teleportPos != null) {
                    LuciiNetwork.broadcastRoyalArmsWarpTrail(world, player, player.getPos(), teleportPos, LuciiLegacy.ARDYN);
                    player.teleport(world, teleportPos.x, teleportPos.y, teleportPos.z, player.getYaw(), player.getPitch());
                    player.fallDistance = 0.0F;
                    impactPos = teleportPos;
                }
                damageArdynImpact(player, world, impactPos, barrageDamageFor(barrage.nextStack()) * 2.0F);
                scheduleArdynBarrageRecall(world, barrage);
            }

            if (barrage.age >= ARDYN_BARRAGE_FIRE_TICKS + ARDYN_BARRAGE_RETURN_TICKS) {
                cleanupArdynBarrage(barrage);
                iterator.remove();
            }
        }
    }

    private static void launchArdynBarrageShot(ServerWorld world, ServerPlayerEntity player, ActiveArdynBarrage barrage) {
        ItemStack stack = barrage.nextStack().copyWithCount(1);
        if (stack.isEmpty()) {
            return;
        }

        // Every shot samples the CURRENT view direction. This lets the player steer
        // the barrage with the mouse without turning already-fired weapons into homing projectiles.
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d right = new Vec3d(-look.z, 0.0D, look.x);
        if (right.lengthSquared() < 1.0E-6D) {
            right = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3d up = right.crossProduct(look).normalize();

        int pattern = barrage.shotIndex++;
        double startBand = ((pattern % 5) - 2) / 2.0D;
        double startSide = startBand * ARDYN_BARRAGE_START_SIDE_RADIUS
                + (world.random.nextDouble() - 0.5D) * 0.45D;
        double startHeight = 0.70D
                + ((pattern / 5) % 4) * 0.38D
                + world.random.nextDouble() * 0.22D;
        Vec3d start = player.getPos()
                .add(0.0D, startHeight, 0.0D)
                .add(right.multiply(startSide))
                .subtract(look.multiply(0.65D));

        // Three samples averaged together give a center-heavy cone instead of a flat random square:
        // most blades travel near the crosshair, but the outer shots intentionally miss.
        double horizontalSpread = centeredSpread(world, ARDYN_BARRAGE_HORIZONTAL_SPREAD);
        double verticalSpread = centeredSpread(world, ARDYN_BARRAGE_VERTICAL_SPREAD);
        Vec3d direction = look
                .add(right.multiply(horizontalSpread))
                .add(up.multiply(verticalSpread))
                .normalize();

        ArdynBarrageWeaponEntity weapon = new ArdynBarrageWeaponEntity(LegacyEntities.ARDYN_BARRAGE_WEAPON, world);
        weapon.setPosition(start.x, start.y, start.z);
        weapon.configure(
                player,
                stack,
                direction.multiply(ARDYN_BARRAGE_PROJECTILE_SPEED),
                barrageDamageFor(stack),
                ARDYN_BARRAGE_MAX_FLIGHT_TICKS
        );
        if (world.spawnEntity(weapon)) {
            barrage.weapons.add(weapon);
        }
    }

    private static void scheduleArdynBarrageRecall(ServerWorld world, ActiveArdynBarrage barrage) {
        if (barrage.recallScheduled) {
            return;
        }
        barrage.recallScheduled = true;

        // Delays are shuffled independently so the field breaks apart in a chaotic order.
        // The final ~28 ticks of the 5 second recall window are left for the curved flight itself.
        for (ArdynBarrageWeaponEntity weapon : barrage.weapons) {
            if (weapon != null && !weapon.isRemoved()) {
                weapon.scheduleRecall(world.random.nextInt(ARDYN_BARRAGE_RECALL_MAX_DELAY + 1));
            }
        }
    }

    private static double centeredSpread(ServerWorld world, double amount) {
        double sample = (world.random.nextDouble() + world.random.nextDouble() + world.random.nextDouble()) / 3.0D;
        return (sample - 0.5D) * 2.0D * amount;
    }

    private static Vec3d lookTarget(ServerWorld world, ServerPlayerEntity player, double distance) {
        Vec3d start = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        HitResult hit = world.raycast(new RaycastContext(
                start,
                start.add(look.multiply(distance)),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        return hit.getType() == HitResult.Type.MISS ? start.add(look.multiply(distance)) : hit.getPos();
    }

    private static float barrageDamageFor(ItemStack stack) {
        float weaponDamage = weaponAttackDamage(stack);
        if (stack.isIn(ROYAL_ARMS_WEAPONS) || weaponDamage > 0.0F) {
            return weaponDamage * ARDYN_BARRAGE_WEAPON_MULTIPLIER;
        }

        return ARDYN_BARRAGE_NORMAL_DAMAGE;
    }

    private static void cleanupArdynBarrage(ActiveArdynBarrage barrage) {
        for (ArdynBarrageWeaponEntity weapon : barrage.weapons) {
            if (weapon != null && !weapon.isRemoved()) {
                weapon.discard();
            }
        }
        barrage.weapons.clear();
        LuciiNetwork.broadcastArdynBarrage(barrage.world, barrage.ownerUuid, false);
    }

    private static ItemStack reserveHeldItem(ServerPlayerEntity player) {
        ItemStack heldStack = player.getMainHandStack();
        if (player.getAbilities().creativeMode) {
            return heldStack.copyWithCount(1);
        }

        ItemStack reservedStack = heldStack.copyWithCount(1);
        heldStack.decrement(1);
        player.currentScreenHandler.sendContentUpdates();
        return reservedStack;
    }

    private static void returnHeldItem(ServerPlayerEntity player, ActiveWarp warp) {
        if (warp.reservedStack.isEmpty()) {
            return;
        }

        if (!player.getAbilities().creativeMode) {
            ItemStack selectedStack = player.getInventory().getStack(warp.selectedSlot);
            if (selectedStack.isEmpty()) {
                player.getInventory().setStack(warp.selectedSlot, warp.reservedStack.copy());
            } else {
                player.getInventory().offerOrDrop(warp.reservedStack.copy());
            }
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    private static void cleanup(ActiveWarp warp, ServerPlayerEntity player) {
        if (player != null && warp.legacy == LuciiLegacy.ARDYN && warp.phase == WarpPhase.CHARGE) {
            LuciiNetwork.broadcastArdynWarpCharge(player.getServerWorld(), player, false);
        }
        if (warp.itemEntity != null && !warp.itemEntity.isRemoved()) {
            warp.itemEntity.discard();
        }
    }

    private static void tickNoctisProjectile(ServerWorld world, ServerPlayerEntity player, ActiveWarp warp) {
        LivingEntity collidedEntity = itemCollisionTarget(world, player, warp);
        if (collidedEntity != null) {
            warp.target = collidedEntity.getBoundingBox().getCenter();
            Vec3d teleportPos = safeEntityTeleportPos(world, collidedEntity, player);
            if (teleportPos != null) {
                warp.teleportPos = teleportPos;
            }
            warp.targetEntityUuid = collidedEntity.getUuid();
            warp.ticksRemaining = 0;
            if (warp.itemEntity != null && !warp.itemEntity.isRemoved()) {
                warp.itemEntity.setVelocity(Vec3d.ZERO);
            }
            return;
        }

        spawnTrail(world, warp.trailOrigin, warp.target, warp.legacy);
        if (warp.ticksRemaining > 0) {
            warp.ticksRemaining--;
        }
        if (warp.itemEntity != null && !warp.itemEntity.isRemoved()) {
            Vec3d toTarget = warp.target.subtract(warp.itemEntity.getPos());
            if (toTarget.lengthSquared() > 0.05D) {
                warp.itemEntity.setVelocity(toTarget.normalize().multiply(PROJECTILE_SPEED));
            } else {
                warp.itemEntity.setVelocity(Vec3d.ZERO);
            }
        }
    }

    private static boolean tickArdynWarp(ServerWorld world, ServerPlayerEntity player, ActiveWarp warp) {
        if (warp.phase == WarpPhase.CHARGE) {
            player.fallDistance = 0.0F;
            player.setVelocity(Vec3d.ZERO);
            player.velocityModified = true;
            spawnArdynCharge(world, player);
            warp.ticksRemaining--;
            if (warp.ticksRemaining > 0) {
                return false;
            }

            player.teleport(world, warp.teleportPos.x, warp.teleportPos.y, warp.teleportPos.z, player.getYaw(), player.getPitch());
            player.fallDistance = 0.0F;
            LuciiNetwork.broadcastArdynWarpCharge(world, player, false);
            damageArdynImpact(player, world, warp);
            return true;
        }

        LivingEntity collidedEntity = itemCollisionTarget(world, player, warp);
        if (collidedEntity != null) {
            warp.target = collidedEntity.getBoundingBox().getCenter();
            Vec3d teleportPos = safeEntityTeleportPos(world, collidedEntity, player);
            if (teleportPos != null) {
                warp.teleportPos = teleportPos;
            }
            warp.targetEntityUuid = collidedEntity.getUuid();
            enterArdynChargePhase(world, player, warp);
            return false;
        }

        if (warp.ticksRemaining > 0) {
            warp.ticksRemaining--;
        }

        double progress = 1.0D - warp.ticksRemaining / (double) warp.totalTicks;
        Vec3d nextPos = ardynArcPosition(warp, progress);
        Vec3d previousPos = warp.previousProjectilePos;
        warp.previousProjectilePos = nextPos;

        if (warp.itemEntity != null && !warp.itemEntity.isRemoved()) {
            float yaw = (float) (360.0D * progress);
            warp.itemEntity.refreshPositionAndAngles(nextPos.x, nextPos.y, nextPos.z, yaw, warp.itemEntity.getPitch());
            warp.itemEntity.setVelocity(nextPos.subtract(previousPos));
        }

        spawnThinArdynWeaponTrail(world, previousPos, nextPos);
        if (warp.ticksRemaining <= 0) {
            enterArdynChargePhase(world, player, warp);
        }
        return false;
    }

    private static Vec3d ardynArcPosition(ActiveWarp warp, double progress) {
        double clampedProgress = Math.max(0.0D, Math.min(1.0D, progress));
        Vec3d catchPos = new Vec3d(warp.teleportPos.x, warp.projectileStart.y + 0.15D, warp.teleportPos.z);
        Vec3d base = warp.projectileStart.lerp(catchPos, clampedProgress);
        double height = Math.sin(clampedProgress * Math.PI) * ARDYN_ARC_HEIGHT;
        double y = Math.min(base.y + height, warp.projectileStart.y + ARDYN_MAX_ARC_ABOVE_THROW);
        return new Vec3d(base.x, y, base.z);
    }

    private static void enterArdynChargePhase(ServerWorld world, ServerPlayerEntity player, ActiveWarp warp) {
        warp.phase = WarpPhase.CHARGE;
        warp.ticksRemaining = ARDYN_CHARGE_TICKS;
        Vec3d visualCatchPos = new Vec3d(warp.teleportPos.x, warp.projectileStart.y + 0.15D, warp.teleportPos.z);
        player.teleport(world, warp.teleportPos.x, warp.teleportPos.y, warp.teleportPos.z, player.getYaw(), player.getPitch());
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0.0F;
        if (warp.itemEntity != null && !warp.itemEntity.isRemoved()) {
            warp.itemEntity.refreshPositionAndAngles(visualCatchPos.x, visualCatchPos.y, visualCatchPos.z, warp.itemEntity.getYaw(), warp.itemEntity.getPitch());
            warp.itemEntity.setVelocity(Vec3d.ZERO);
        }
        LuciiNetwork.broadcastArdynWarpCharge(world, player, true);
    }

    private static LivingEntity raycastEntity(ServerWorld world, ServerPlayerEntity player, Vec3d start, Vec3d end, double maxDistanceSquared) {
        Box searchBox = player.getBoundingBox().stretch(end.subtract(start)).expand(1.0D);
        LivingEntity closestEntity = null;
        double closestDistance = maxDistanceSquared;

        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, searchBox, entity -> canDamage(player, entity))) {
            Optional<Vec3d> hit = entity.getBoundingBox().expand(ENTITY_HIT_EXPAND).raycast(start, end);
            if (hit.isEmpty()) {
                continue;
            }

            double distance = start.squaredDistanceTo(hit.get());
            if (distance < closestDistance) {
                closestDistance = distance;
                closestEntity = entity;
            }
        }

        return closestEntity;
    }

    private static LivingEntity itemCollisionTarget(ServerWorld world, ServerPlayerEntity player, ActiveWarp warp) {
        if (warp.itemEntity == null || warp.itemEntity.isRemoved()) {
            return null;
        }

        Box searchBox = warp.itemEntity.getBoundingBox().expand(ITEM_ENTITY_HIT_EXPAND);
        LivingEntity closestEntity = null;
        double closestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, searchBox, entity -> canDamage(player, entity))) {
            double distance = entity.squaredDistanceTo(warp.itemEntity);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestEntity = entity;
            }
        }
        return closestEntity;
    }

    private static void damageWarpTarget(ServerPlayerEntity player, ServerWorld world, ActiveWarp warp) {
        if (warp.targetEntityUuid == null) {
            return;
        }

        Entity entity = world.getEntity(warp.targetEntityUuid);
        if (!(entity instanceof LivingEntity target) || !canDamage(player, target)) {
            return;
        }

        if (target.squaredDistanceTo(warp.target) > WARP_DAMAGE_RADIUS * WARP_DAMAGE_RADIUS) {
            return;
        }

        target.damage(player.getDamageSources().playerAttack(player), warpDamageFor(warp.reservedStack));
    }

    private static void damageArdynImpact(ServerPlayerEntity player, ServerWorld world, ActiveWarp warp) {
        damageArdynImpact(player, world, warp.teleportPos, warpDamageFor(warp.reservedStack));
    }

    private static void damageArdynImpact(ServerPlayerEntity player, ServerWorld world, Vec3d impactPos, float damage) {
        Vec3d center = impactPos.add(0.0D, 0.15D, 0.0D);
        Box box = new Box(
                center.x - ARDYN_IMPACT_RADIUS,
                center.y - ARDYN_IMPACT_RADIUS,
                center.z - ARDYN_IMPACT_RADIUS,
                center.x + ARDYN_IMPACT_RADIUS,
                center.y + ARDYN_IMPACT_RADIUS,
                center.z + ARDYN_IMPACT_RADIUS
        );

        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box, entity -> canDamage(player, entity))) {
            if (target.squaredDistanceTo(center) > ARDYN_IMPACT_RADIUS * ARDYN_IMPACT_RADIUS) {
                continue;
            }

            target.damage(player.getDamageSources().playerAttack(player), damage);
            Vec3d knockback = target.getPos().subtract(center);
            if (knockback.lengthSquared() <= 0.0001D) {
                knockback = player.getRotationVec(1.0F);
            }
            target.addVelocity(knockback.normalize().multiply(ARDYN_IMPACT_KNOCKBACK).add(0.0D, 0.25D, 0.0D));
            target.velocityModified = true;
        }

        world.spawnParticles(ARDYN_IMPACT_PARTICLE, center.x, center.y + 0.35D, center.z, 96, 1.65D, 0.9D, 1.65D, 0.055D);
        world.spawnParticles(ParticleTypes.POOF, center.x, center.y + 0.15D, center.z, 42, 1.35D, 0.3D, 1.35D, 0.04D);
        world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.2D, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        spawnArdynImpactSphere(world, center);
    }

    private static boolean canDamage(ServerPlayerEntity player, LivingEntity target) {
        return target.isAlive()
                && target != player
                && !target.isSpectator()
                && target.getWorld() == player.getWorld();
    }

    private static Vec3d safeTeleportPos(ServerWorld world, Vec3d target, HitResult hitResult, ServerPlayerEntity player) {
        BlockPos basePos;
        if (hitResult instanceof BlockHitResult blockHitResult && hitResult.getType() == HitResult.Type.BLOCK) {
            basePos = blockHitResult.getBlockPos().offset(blockHitResult.getSide());
        } else {
            basePos = BlockPos.ofFloored(target);
        }

        for (BlockPos pos : new BlockPos[]{basePos, basePos.up(), basePos.down()}) {
            if (canStandAt(world, pos) && canStandAt(world, pos.up())) {
                return new Vec3d(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            }
        }
        return null;
    }

    private static Vec3d safeEntityTeleportPos(ServerWorld world, LivingEntity target, ServerPlayerEntity player) {
        BlockPos basePos = target.getBlockPos();
        BlockPos[] candidates = new BlockPos[]{
                basePos,
                basePos.offset(player.getHorizontalFacing().getOpposite()),
                basePos.offset(player.getHorizontalFacing().rotateYClockwise()),
                basePos.offset(player.getHorizontalFacing().rotateYCounterclockwise()),
                basePos.up(),
                basePos.down()
        };

        for (BlockPos pos : candidates) {
            if (canStandAt(world, pos) && canStandAt(world, pos.up())) {
                return new Vec3d(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            }
        }
        return null;
    }

    private static boolean canStandAt(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    private static void spawnTrail(ServerWorld world, Vec3d from, Vec3d to, LuciiLegacy legacy) {
        DustParticleEffect particle = legacy == LuciiLegacy.ARDYN ? ARDYN_TRAIL_PARTICLE : NOCTIS_TRAIL_PARTICLE;
        Vec3d delta = to.subtract(from);
        int points = Math.max(4, Math.min(18, (int) (delta.length() * 2.0D)));
        for (int i = 0; i <= points; i++) {
            Vec3d pos = from.add(delta.multiply(i / (double) points));
            world.spawnParticles(particle, pos.x, pos.y, pos.z, 1, 0.08D, 0.12D, 0.08D, 0.0D);
        }
    }

    private static void spawnThinArdynWeaponTrail(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        int points = Math.max(1, Math.min(4, (int) (delta.length() * 3.0D)));
        for (int i = 0; i <= points; i++) {
            Vec3d pos = from.add(delta.multiply(i / (double) points));
            world.spawnParticles(ARDYN_TRAIL_PARTICLE, pos.x, pos.y, pos.z, 1, 0.015D, 0.015D, 0.015D, 0.0D);
        }
    }

    private static void spawnArdynBarrageTrail(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        int points = Math.max(2, Math.min(7, (int) (delta.length() * 4.0D)));
        for (int i = 0; i <= points; i++) {
            Vec3d pos = from.add(delta.multiply(i / (double) points));
            world.spawnParticles(ARDYN_TRAIL_PARTICLE, pos.x, pos.y, pos.z, 2, 0.03D, 0.03D, 0.03D, 0.0D);
            if (world.random.nextFloat() < 0.45F) {
                world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 1, 0.04D, 0.04D, 0.04D, 0.0D);
            }
        }
    }

    private static void spawnArdynBarrageRingParticles(ServerWorld world, ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d right = new Vec3d(-look.z, 0.0D, look.x).normalize();
        Vec3d center = player.getPos().add(0.0D, 0.75D, 0.0D).add(look.multiply(1.2D));
        for (int i = 0; i < 6; i++) {
            double angle = (world.getTime() * 0.22D + i) * Math.PI * 2.0D / 6.0D;
            Vec3d pos = center
                    .add(right.multiply(Math.cos(angle) * 1.65D))
                    .add(0.0D, 0.7D + Math.sin(angle) * 0.35D, 0.0D)
                    .add(look.multiply(Math.sin(angle) * 0.28D));
            world.spawnParticles(ARDYN_TRAIL_PARTICLE, pos.x, pos.y, pos.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
        }
    }

    private static void spawnArdynCharge(ServerWorld world, ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d right = new Vec3d(-look.z, 0.0D, look.x);
        Vec3d fistPos = player.getPos()
                .add(0.0D, 1.15D, 0.0D)
                .add(look.multiply(0.45D))
                .add(right.multiply(0.28D));
        world.spawnParticles(ARDYN_IMPACT_PARTICLE, fistPos.x, fistPos.y, fistPos.z, 12, 0.12D, 0.12D, 0.12D, 0.015D);
        world.spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.25D, player.getZ(), 8, 0.25D, 0.08D, 0.25D, 0.01D);
    }

    private static void spawnArdynImpactSphere(ServerWorld world, Vec3d center) {
        int rings = 5;
        int pointsPerRing = 24;
        for (int ring = 0; ring < rings; ring++) {
            double verticalProgress = ring / (double) (rings - 1);
            double yOffset = Math.cos(verticalProgress * Math.PI) * 1.15D;
            double ringRadius = Math.sin(verticalProgress * Math.PI) * 2.25D;
            for (int i = 0; i < pointsPerRing; i++) {
                double angle = (i / (double) pointsPerRing) * Math.PI * 2.0D;
                Vec3d pos = center.add(Math.cos(angle) * ringRadius, 0.75D + yOffset, Math.sin(angle) * ringRadius);
                world.spawnParticles(ARDYN_IMPACT_PARTICLE, pos.x, pos.y, pos.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
        }
    }

    private static float warpDamageFor(ItemStack stack) {
        float weaponDamage = weaponAttackDamage(stack);
        if (stack.isIn(ROYAL_ARMS_WEAPONS) || weaponDamage > 0.0F) {
            return weaponDamage * WEAPON_DAMAGE_MULTIPLIER;
        }

        return FIST_DAMAGE;
    }

    private static float weaponAttackDamage(ItemStack stack) {
        Multimap<EntityAttribute, EntityAttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        float damage = 0.0F;
        for (EntityAttributeModifier modifier : modifiers.get(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
            damage += modifier.getValue();
        }

        return damage <= 0.0F ? 0.0F : damage + 1.0F;
    }

    private static final class ActiveWarp {
        private final ServerWorld world;
        private final ItemEntity itemEntity;
        private final ItemStack reservedStack;
        private final int selectedSlot;
        private final Vec3d trailOrigin;
        private final Vec3d projectileStart;
        private Vec3d previousProjectilePos;
        private Vec3d target;
        private Vec3d teleportPos;
        private UUID targetEntityUuid;
        private final LuciiLegacy legacy;
        private WarpPhase phase = WarpPhase.PROJECTILE;
        private final int totalTicks;
        private int ticksRemaining;

        private ActiveWarp(ServerWorld world, ItemEntity itemEntity, ItemStack reservedStack, int selectedSlot, Vec3d trailOrigin, Vec3d projectileStart, Vec3d target, Vec3d teleportPos, UUID targetEntityUuid, LuciiLegacy legacy) {
            this.world = world;
            this.itemEntity = itemEntity;
            this.reservedStack = reservedStack;
            this.selectedSlot = selectedSlot;
            this.trailOrigin = trailOrigin;
            this.projectileStart = projectileStart;
            this.previousProjectilePos = projectileStart;
            this.target = target;
            this.teleportPos = teleportPos;
            this.targetEntityUuid = targetEntityUuid;
            this.legacy = legacy;
            this.totalTicks = legacy == LuciiLegacy.ARDYN ? ARDYN_PROJECTILE_TICKS : WARP_TICKS;
            this.ticksRemaining = totalTicks;
        }
    }

    private static final class ActiveArdynBarrage {
        private final UUID ownerUuid;
        private final ServerWorld world;
        private final List<ItemStack> stacks;
        private final List<ArdynBarrageWeaponEntity> weapons = new ArrayList<>();
        private int age;
        private int shotIndex;
        private boolean finalImpactDone;
        private boolean recallScheduled;

        private ActiveArdynBarrage(UUID ownerUuid, ServerWorld world, List<ItemStack> stacks) {
            this.ownerUuid = ownerUuid;
            this.world = world;
            this.stacks = stacks;
        }

        private ItemStack nextStack() {
            if (stacks.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return stacks.get(Math.floorMod(shotIndex, stacks.size()));
        }
    }

    private enum WarpPhase {
        PROJECTILE,
        CHARGE
    }
}
