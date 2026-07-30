package ru.siyoga.legacyofthelucii.royalarms.ability;

import com.google.common.collect.Multimap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RoyalArmsBindAbility {
    public static final TagKey<Item> ROYAL_ARMS_WEAPONS = TagKey.of(
            RegistryKeys.ITEM,
            new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_weapons")
    );

    private static final double MAX_TARGET_DISTANCE = 16.0D;
    private static final double ENTITY_HIT_EXPAND = 0.45D;
    private static final double PIN_DISTANCE = 0.08D;
    private static final int INITIAL_MANA_COST = 25;
    private static final double HOLD_MANA_PER_SECOND = 3.0D;
    private static final int MOVEMENT_DAMAGE_COOLDOWN_TICKS = 10;
    private static final float ITEM_DAMAGE = 0.1F;
    private static final float WEAPON_DAMAGE_MULTIPLIER = 1.0F / 20.0F;
    private static final DustParticleEffect NOCTIS_PARTICLE = new DustParticleEffect(new Vector3f(0.55F, 0.78F, 1.0F), 1.2F);
    private static final DustParticleEffect ARDYN_PARTICLE = new DustParticleEffect(new Vector3f(1.0F, 0.16F, 0.24F), 1.2F);

    private static final Map<UUID, ActiveBind> ACTIVE_BINDS = new HashMap<>();

    private RoyalArmsBindAbility() {
    }

    public static void handleAction(ServerPlayerEntity player, int action) {
        if (action == LuciiNetwork.ROYAL_ARMS_BIND_CONFIRM_ACTION) {
            release(player);
            return;
        }

        if (action == LuciiNetwork.ROYAL_ARMS_BIND_TOGGLE_ACTION) {
            toggle(player);
        }
    }

    public static void setActive(ServerPlayerEntity player, boolean active) {
        if (active) {
            start(player);
        } else {
            release(player);
        }
    }

    private static void toggle(ServerPlayerEntity player) {
        ActiveBind bind = ACTIVE_BINDS.remove(player.getUuid());
        if (bind != null) {
            end(bind, player, false);
            return;
        }

        start(player);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveBind>> iterator = ACTIVE_BINDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveBind> entry = iterator.next();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.getKey());
            ActiveBind bind = entry.getValue();
            ServerWorld world = server.getWorld(bind.world.getRegistryKey());
            if (owner == null || world == null || owner.isRemoved() || owner.isDead()) {
                end(bind, null, false);
                iterator.remove();
                continue;
            }

            LuciiPlayerState state = LuciiPlayerStates.get(owner);
            if (!canUseState(state)) {
                end(bind, owner, false);
                iterator.remove();
                continue;
            }

            LivingEntity target = target(world, bind.targetUuid);
            if (target == null || !canTarget(owner, target)) {
                end(bind, owner, false);
                iterator.remove();
                continue;
            }

            bind.ticks++;
            if (!owner.getAbilities().creativeMode) {
                int requiredHoldMana = (int) Math.floor(bind.ticks * HOLD_MANA_PER_SECOND / 20.0D);
                int manaToSpend = requiredHoldMana - bind.holdManaSpent;
                if (manaToSpend > 0) {
                    if (!state.spendMana(manaToSpend)) {
                        finish(owner, bind, iterator, true);
                        continue;
                    }
                    bind.holdManaSpent += manaToSpend;
                    LuciiNetwork.sendState(owner);
                }
            }

            if (pinTarget(owner, target, bind)) {
                LuciiNetwork.broadcastRoyalArmsBindVisual(world, owner, target, false, true, bind.legacy, bind.stacks);
                iterator.remove();
                continue;
            }

            spawnParticles(world, target.getBoundingBox().getCenter(), bind.legacy);
        }
    }

    public static boolean isBoundTarget(ServerPlayerEntity owner, LivingEntity target) {
        ActiveBind bind = ACTIVE_BINDS.get(owner.getUuid());
        return bind != null && bind.targetUuid.equals(target.getUuid());
    }

    public static boolean isBinding(ServerPlayerEntity owner) {
        return ACTIVE_BINDS.containsKey(owner.getUuid());
    }

    public static void clearAll(ServerPlayerEntity player) {
        ActiveBind bind = ACTIVE_BINDS.remove(player.getUuid());
        if (bind != null) {
            end(bind, player, false);
        }
    }

    public static void clearAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            clearAll(player);
        }
        ACTIVE_BINDS.clear();
    }

    private static void start(ServerPlayerEntity player) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (!canUseState(state) || ACTIVE_BINDS.containsKey(player.getUuid())) {
            return;
        }

        List<ItemStack> stacks = RoyalArmsInventoryItems.collect(player);
        if (stacks.isEmpty()) {
            return;
        }

        if (!player.getAbilities().creativeMode && !state.hasMana(INITIAL_MANA_COST)) {
            player.sendMessage(Text.translatable("message.legacyofthelucii.royal_arms.not_enough_mana"), true);
            return;
        }

        LivingEntity target = raycastTarget(player);
        if (target == null) {
            player.sendMessage(Text.translatable("message.legacyofthelucii.royal_arms.bind.no_target"), true);
            return;
        }

        if (!player.getAbilities().creativeMode) {
            state.spendMana(INITIAL_MANA_COST);
            LuciiNetwork.sendState(player);
        }

        List<ItemStack> copiedStacks = stacks.stream().map(stack -> stack.copyWithCount(stack.getCount())).toList();
        ActiveBind bind = new ActiveBind(player.getServerWorld(), target.getUuid(), target.getPos(), copiedStacks, state.legacy());
        ACTIVE_BINDS.put(player.getUuid(), bind);
        LuciiNetwork.broadcastRoyalArmsBindVisual(player.getServerWorld(), player, target, true, false, state.legacy(), copiedStacks);
    }

    private static void release(ServerPlayerEntity player) {
        ActiveBind bind = ACTIVE_BINDS.remove(player.getUuid());
        if (bind == null) {
            return;
        }

        ServerWorld world = player.getServerWorld();
        LivingEntity target = target(world, bind.targetUuid);
        if (target != null && canTarget(player, target)) {
            target.damage(player.getDamageSources().playerAttack(player), totalDamage(bind.stacks));
            LuciiNetwork.broadcastRoyalArmsBindVisual(world, player, target, false, true, bind.legacy, bind.stacks);
            return;
        }

        end(bind, player, false);
    }

    private static void finish(ServerPlayerEntity owner, ActiveBind bind, Iterator<Map.Entry<UUID, ActiveBind>> iterator, boolean impale) {
        iterator.remove();
        if (!impale) {
            end(bind, owner, false);
            return;
        }

        LivingEntity target = target(owner.getServerWorld(), bind.targetUuid);
        if (target != null && canTarget(owner, target)) {
            target.damage(owner.getDamageSources().playerAttack(owner), totalDamage(bind.stacks));
            LuciiNetwork.broadcastRoyalArmsBindVisual(owner.getServerWorld(), owner, target, false, true, bind.legacy, bind.stacks);
        } else {
            end(bind, owner, false);
        }
    }

    private static void end(ActiveBind bind, ServerPlayerEntity owner, boolean impaled) {
        if (owner == null) {
            return;
        }

        LivingEntity target = target(owner.getServerWorld(), bind.targetUuid);
        if (target != null) {
            LuciiNetwork.broadcastRoyalArmsBindVisual(owner.getServerWorld(), owner, target, false, impaled, bind.legacy, bind.stacks);
        }
    }

    private static boolean canUseState(LuciiPlayerState state) {
        return state.royalArmsActive() && (state.legacy() == LuciiLegacy.NOCTIS || state.legacy() == LuciiLegacy.ARDYN);
    }

    private static LivingEntity raycastTarget(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d start = player.getEyePos();
        Vec3d end = start.add(player.getRotationVec(1.0F).normalize().multiply(MAX_TARGET_DISTANCE));
        HitResult blockHit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        double maxDistanceSquared = blockHit.getType() == HitResult.Type.MISS
                ? start.squaredDistanceTo(end)
                : start.squaredDistanceTo(blockHit.getPos());

        Box searchBox = player.getBoundingBox().stretch(end.subtract(start)).expand(1.0D);
        LivingEntity closestEntity = null;
        double closestDistance = maxDistanceSquared;
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, searchBox, entity -> canTarget(player, entity))) {
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

    private static LivingEntity target(ServerWorld world, UUID targetUuid) {
        Entity entity = world.getEntity(targetUuid);
        return entity instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    private static boolean canTarget(ServerPlayerEntity owner, LivingEntity target) {
        return target.isAlive()
                && target != owner
                && !target.isSpectator()
                && target.getWorld() == owner.getWorld();
    }

    private static boolean pinTarget(ServerPlayerEntity owner, LivingEntity target, ActiveBind bind) {
        Vec3d current = target.getPos();
        if (current.squaredDistanceTo(bind.anchor) > PIN_DISTANCE * PIN_DISTANCE) {
            target.teleport(bind.anchor.x, bind.anchor.y, bind.anchor.z);
            target.setVelocity(Vec3d.ZERO);
            target.velocityModified = true;
            if (bind.movementDamageCooldown <= 0) {
                target.damage(owner.getDamageSources().playerAttack(owner), Math.max(ITEM_DAMAGE, totalDamage(bind.stacks) * 0.25F));
                bind.movementDamageCooldown = MOVEMENT_DAMAGE_COOLDOWN_TICKS;
                if (!target.isAlive()) {
                    return true;
                }
            }
        }

        if (bind.movementDamageCooldown > 0) {
            bind.movementDamageCooldown--;
        }
        return false;
    }

    private static void spawnParticles(ServerWorld world, Vec3d center, LuciiLegacy legacy) {
        DustParticleEffect particle = legacy == LuciiLegacy.ARDYN ? ARDYN_PARTICLE : NOCTIS_PARTICLE;
        world.spawnParticles(particle, center.x, center.y, center.z, 3, 0.55D, 0.65D, 0.55D, 0.0D);
    }

    private static float totalDamage(List<ItemStack> stacks) {
        float damage = 0.0F;
        for (ItemStack stack : stacks) {
            damage += damageFor(stack);
        }
        return damage;
    }

    private static float damageFor(ItemStack stack) {
        float weaponDamage = weaponAttackDamage(stack);
        if (stack.isIn(ROYAL_ARMS_WEAPONS) || weaponDamage > 0.0F) {
            return weaponDamage * WEAPON_DAMAGE_MULTIPLIER;
        }

        return ITEM_DAMAGE;
    }

    private static float weaponAttackDamage(ItemStack stack) {
        Multimap<EntityAttribute, EntityAttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        float damage = 0.0F;
        for (EntityAttributeModifier modifier : modifiers.get(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
            damage += modifier.getValue();
        }

        return damage <= 0.0F ? 0.0F : damage + 1.0F;
    }

    private static final class ActiveBind {
        private final ServerWorld world;
        private final UUID targetUuid;
        private final Vec3d anchor;
        private final List<ItemStack> stacks;
        private final LuciiLegacy legacy;
        private int ticks;
        private int holdManaSpent;
        private int movementDamageCooldown;

        private ActiveBind(ServerWorld world, UUID targetUuid, Vec3d anchor, List<ItemStack> stacks, LuciiLegacy legacy) {
            this.world = world;
            this.targetUuid = targetUuid;
            this.anchor = anchor;
            this.stacks = stacks;
            this.legacy = legacy;
        }
    }
}
