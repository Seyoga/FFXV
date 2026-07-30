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
    private static final int ARDYN_BARRAGE_RETURN_TICKS = 28;
    private static final int ARDYN_BARRAGE_SHOT_INTERVAL = 2;
    private static final int ARDYN_BARRAGE_PROJECTILE_TICKS = 4;
    private static final double ARDYN_BARRAGE_DISTANCE = 16.0D;
    private static final double ARDYN_BARRAGE_SPREAD = 3.2D;
    private static final double ARDYN_BARRAGE_SIDE_OFFSET = 1.8D;
    private static final double ARDYN_BARRAGE_UP_OFFSET = 1.0D;
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
            tickArdynBarrageProjectiles(world, player, barrage);
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
            }

            tickArdynBarrageReturns(world, player, barrage);
            if (barrage.age >= ARDYN_BARRAGE_FIRE_TICKS + ARDYN_BARRAGE_RETURN_TICKS) {
                cleanupArdynBarrage(barrage);
                iterator.remove();
            }
        }
    }

    private static void launchArdynBarrageShot(ServerWorld world, ServerPlayerEntity player, ActiveArdynBarrage barrage) {
        ItemStack stack = barrage.nextStack().copyWithCount(1);
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d right = new Vec3d(-look.z, 0.0D, look.x).normalize();
        int pattern = barrage.shotIndex++;
        double side = ((pattern % 7) - 3) * (ARDYN_BARRAGE_SPREAD / 6.0D) + (world.random.nextDouble() - 0.5D) * 0.85D;
        double height = ARDYN_BARRAGE_UP_OFFSET + ((pattern / 7) % 3) * 0.35D + world.random.nextDouble() * 0.35D;
        Vec3d start = player.getPos()
                .add(0.0D, height, 0.0D)
                .add(right.multiply(side + (pattern % 2 == 0 ? ARDYN_BARRAGE_SIDE_OFFSET : -ARDYN_BARRAGE_SIDE_OFFSET)))
                .subtract(look.multiply(0.85D));
        Vec3d target = barrageTarget(world, player, look, right, side, pattern);

        Vec3d velocity = target.subtract(start).multiply(1.0D / ARDYN_BARRAGE_PROJECTILE_TICKS);
        ArdynBarrageWeaponEntity weapon = new ArdynBarrageWeaponEntity(world, stack, start, velocity);
        world.spawnEntity(weapon);
        barrage.projectiles.add(new BarrageProjectile(weapon, stack, target, ARDYN_BARRAGE_PROJECTILE_TICKS));
    }

    private static void tickArdynBarrageProjectiles(ServerWorld world, ServerPlayerEntity player, ActiveArdynBarrage barrage) {
        Iterator<BarrageProjectile> iterator = barrage.projectiles.iterator();
        while (iterator.hasNext()) {
            BarrageProjectile projectile = iterator.next();
            projectile.age++;
            Vec3d previous = projectile.previousPos;
            if (projectile.entity == null || projectile.entity.isRemoved()) {
                iterator.remove();
                continue;
            }

            Vec3d next = projectile.entity.getPos();
            projectile.previousPos = next;

            spawnArdynBarrageTrail(world, previous, next);
            LivingEntity hitEntity = barrageHitTarget(player, world, previous, next);
            if (hitEntity != null) {
                Vec3d hitPos = hitEntity.getBoundingBox().getCenter()
                        .add((world.random.nextDouble() - 0.5D) * 0.55D, (world.random.nextDouble() - 0.5D) * 0.75D, (world.random.nextDouble() - 0.5D) * 0.55D);
                hitEntity.damage(player.getDamageSources().playerAttack(player), barrageDamageFor(projectile.stack));
                lodgeBarrageProjectileInEntity(barrage, projectile, hitEntity, hitPos);
                iterator.remove();
                continue;
            }

            if (projectile.age >= projectile.totalTicks) {
                lodgeBarrageProjectile(barrage, projectile, next);
                iterator.remove();
            }
        }
    }

    private static void tickArdynBarrageReturns(ServerWorld world, ServerPlayerEntity player, ActiveArdynBarrage barrage) {
        Vec3d center = player.getPos().add(0.0D, 1.0D, 0.0D);
        Iterator<ArdynBarrageWeaponEntity> iterator = barrage.lodgedItems.iterator();
        while (iterator.hasNext()) {
            ArdynBarrageWeaponEntity item = iterator.next();
            if (item == null || item.isRemoved()) {
                iterator.remove();
                continue;
            }

            Vec3d pos = item.getPos();
            Vec3d toPlayer = center.subtract(pos);
            world.spawnParticles(ARDYN_TRAIL_PARTICLE, pos.x, pos.y, pos.z, 3, 0.08D, 0.08D, 0.08D, 0.0D);
            if (toPlayer.lengthSquared() < 0.28D || barrage.age % 3 == 0 && world.random.nextFloat() < 0.28F) {
                item.discard();
                iterator.remove();
            } else {
                item.recallTo(player);
            }
        }
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

    private static Vec3d barrageTarget(ServerWorld world, ServerPlayerEntity player, Vec3d look, Vec3d right, double side, int pattern) {
        LivingEntity target = selectBarrageTarget(world, player, look, pattern);
        if (target != null) {
            return target.getBoundingBox().getCenter()
                    .add(right.multiply((world.random.nextDouble() - 0.5D) * 0.75D))
                    .add(0.0D, (world.random.nextDouble() - 0.5D) * 0.7D, 0.0D);
        }

        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(look.multiply(ARDYN_BARRAGE_DISTANCE))
                .add(right.multiply(side))
                .add(0.0D, ((pattern % 5) - 2) * 0.35D, 0.0D);
        HitResult hit = world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
    }

    private static LivingEntity selectBarrageTarget(ServerWorld world, ServerPlayerEntity player, Vec3d look, int pattern) {
        Box searchBox = player.getBoundingBox().stretch(look.multiply(ARDYN_BARRAGE_DISTANCE)).expand(6.0D, 3.0D, 6.0D);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, searchBox, entity -> canDamage(player, entity)
                && entity.getBoundingBox().getCenter().subtract(player.getEyePos()).normalize().dotProduct(look) > 0.45D);
        if (targets.isEmpty() || pattern % 4 == 0) {
            return null;
        }
        return targets.get(Math.floorMod(pattern + world.random.nextInt(targets.size()), targets.size()));
    }

    private static LivingEntity barrageHitTarget(ServerPlayerEntity player, ServerWorld world, Vec3d from, Vec3d to) {
        Box searchBox = new Box(from, to).expand(0.65D);
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, searchBox, entity -> canDamage(player, entity))) {
            if (target.getBoundingBox().expand(0.45D).raycast(from, to).isPresent()) {
                double distance = target.squaredDistanceTo(from);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = target;
                }
            }
        }
        return closest;
    }

    private static void lodgeBarrageProjectile(ActiveArdynBarrage barrage, BarrageProjectile projectile, Vec3d pos) {
        if (projectile.entity != null && !projectile.entity.isRemoved()) {
            Vec3d direction = pos.subtract(projectile.previousPos);
            projectile.entity.stickInBlock(pos, direction);
            barrage.lodgedItems.add(projectile.entity);
        }
    }

    private static void lodgeBarrageProjectileInEntity(ActiveArdynBarrage barrage, BarrageProjectile projectile, Entity target, Vec3d pos) {
        if (projectile.entity != null && !projectile.entity.isRemoved()) {
            Vec3d direction = pos.subtract(projectile.previousPos);
            projectile.entity.stickInEntity(target, pos, direction);
            barrage.lodgedItems.add(projectile.entity);
        }
    }

    private static float barrageDamageFor(ItemStack stack) {
        float weaponDamage = weaponAttackDamage(stack);
        if (stack.isIn(ROYAL_ARMS_WEAPONS) || weaponDamage > 0.0F) {
            return weaponDamage * ARDYN_BARRAGE_WEAPON_MULTIPLIER;
        }

        return ARDYN_BARRAGE_NORMAL_DAMAGE;
    }

    private static void cleanupArdynBarrage(ActiveArdynBarrage barrage) {
        for (BarrageProjectile projectile : barrage.projectiles) {
            if (projectile.entity != null && !projectile.entity.isRemoved()) {
                projectile.entity.discard();
            }
        }
        for (ArdynBarrageWeaponEntity item : barrage.lodgedItems) {
            if (item != null && !item.isRemoved()) {
                item.discard();
            }
        }
        barrage.projectiles.clear();
        barrage.lodgedItems.clear();
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
        private final List<BarrageProjectile> projectiles = new ArrayList<>();
        private final List<ArdynBarrageWeaponEntity> lodgedItems = new ArrayList<>();
        private int age;
        private int shotIndex;
        private boolean finalImpactDone;

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

    private static final class BarrageProjectile {
        private final ArdynBarrageWeaponEntity entity;
        private final ItemStack stack;
        private final Vec3d target;
        private Vec3d previousPos;
        private final int totalTicks;
        private int age;

        private BarrageProjectile(ArdynBarrageWeaponEntity entity, ItemStack stack, Vec3d target, int totalTicks) {
            this.entity = entity;
            this.stack = stack;
            this.target = target;
            this.previousPos = entity.getPos();
            this.totalTicks = totalTicks;
        }
    }

    private enum WarpPhase {
        PROJECTILE,
        CHARGE
    }
}
