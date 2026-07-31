package ru.siyoga.legacyofthelucii.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

public final class ArdynBarrageWeaponEntity extends ProjectileEntity {
    public static final int STATE_FLYING = 0;
    public static final int STATE_STUCK_WORLD = 1;
    public static final int STATE_STUCK_ENTITY = 2;
    public static final int STATE_RECALLING = 3;

    private static final int RECALL_FLIGHT_TICKS = 20;
    private static final double BLOCK_EMBED_DEPTH = 0.14D;
    private static final double ENTITY_EMBED_DEPTH = 0.46D;
    private static final double AIR_DRAG = 0.99D;
    private static final double WATER_DRAG = 0.80D;
    private static final double GRAVITY = 0.055D;
    private static final double MIN_FLIGHT_SPEED_SQUARED = 1.0E-6D;
    private static final float HIT_HEAL_AMOUNT = 0.5F;
    private static final DustParticleEffect TRAIL_PARTICLE = new DustParticleEffect(new Vector3f(1.0F, 0.28F, 0.38F), 1.05F);
    private static final DustParticleEffect RECALL_PARTICLE = new DustParticleEffect(new Vector3f(0.72F, 0.08F, 0.16F), 1.35F);

    private static final TrackedData<ItemStack> WEAPON_STACK = DataTracker.registerData(
            ArdynBarrageWeaponEntity.class,
            TrackedDataHandlerRegistry.ITEM_STACK
    );
    private static final TrackedData<Integer> WEAPON_STATE = DataTracker.registerData(
            ArdynBarrageWeaponEntity.class,
            TrackedDataHandlerRegistry.INTEGER
    );
    private static final TrackedData<Float> DIRECTION_X = DataTracker.registerData(
            ArdynBarrageWeaponEntity.class,
            TrackedDataHandlerRegistry.FLOAT
    );
    private static final TrackedData<Float> DIRECTION_Y = DataTracker.registerData(
            ArdynBarrageWeaponEntity.class,
            TrackedDataHandlerRegistry.FLOAT
    );
    private static final TrackedData<Float> DIRECTION_Z = DataTracker.registerData(
            ArdynBarrageWeaponEntity.class,
            TrackedDataHandlerRegistry.FLOAT
    );
    private static final TrackedData<Float> RECALL_PROGRESS = DataTracker.registerData(
            ArdynBarrageWeaponEntity.class,
            TrackedDataHandlerRegistry.FLOAT
    );

    private float damage;
    private LivingEntity attachedEntity;
    private Vec3d attachedCenterOffset = Vec3d.ZERO;

    private boolean recallScheduled;
    private int recallDelay;
    private int recallTicks;
    private Vec3d recallStart = Vec3d.ZERO;
    private Vec3d recallControl = Vec3d.ZERO;

    public ArdynBarrageWeaponEntity(EntityType<? extends ArdynBarrageWeaponEntity> type, World world) {
        super(type, world);
        this.ignoreCameraFrustum = true;
        this.setInvulnerable(true);
    }

    public void configure(ServerPlayerEntity owner, ItemStack stack, Vec3d velocity, float damage) {
        setOwner(owner);
        setWeaponStack(stack);
        this.damage = damage;
        setVelocity(velocity);
        setRenderDirection(velocity);
        setState(STATE_FLYING);
        setRecallProgress(0.0F);
    }

    public void scheduleRecall(int delayTicks) {
        if (isRemoved() || getState() == STATE_RECALLING) {
            return;
        }
        recallScheduled = true;
        recallDelay = Math.max(0, delayTicks);
    }

    public ItemStack getWeaponStack() {
        return dataTracker.get(WEAPON_STACK);
    }

    public int getState() {
        return dataTracker.get(WEAPON_STATE);
    }

    public boolean isRecalling() {
        return getState() == STATE_RECALLING;
    }

    public float getRecallProgress() {
        return dataTracker.get(RECALL_PROGRESS);
    }

    public Vec3d getRenderDirection() {
        Vec3d direction = new Vec3d(
                dataTracker.get(DIRECTION_X),
                dataTracker.get(DIRECTION_Y),
                dataTracker.get(DIRECTION_Z)
        );
        if (direction.lengthSquared() < 1.0E-6D) {
            return new Vec3d(0.0D, 1.0D, 0.0D);
        }
        return direction.normalize();
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(WEAPON_STACK, ItemStack.EMPTY);
        dataTracker.startTracking(WEAPON_STATE, STATE_FLYING);
        dataTracker.startTracking(DIRECTION_X, 0.0F);
        dataTracker.startTracking(DIRECTION_Y, 1.0F);
        dataTracker.startTracking(DIRECTION_Z, 0.0F);
        dataTracker.startTracking(RECALL_PROGRESS, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            return;
        }
        if (!(getWorld() instanceof ServerWorld world)) {
            discard();
            return;
        }

        if (recallScheduled && getState() != STATE_RECALLING) {
            if (recallDelay > 0) {
                recallDelay--;
            } else if (beginRecall(world)) {
                tickRecall(world);
                return;
            }
        }

        switch (getState()) {
            case STATE_FLYING -> tickFlying(world);
            case STATE_STUCK_ENTITY -> tickAttached();
            case STATE_RECALLING -> tickRecall(world);
            default -> setVelocity(Vec3d.ZERO);
        }
    }

    private void tickFlying(ServerWorld world) {
        Vec3d velocity = getVelocity();
        if (velocity.lengthSquared() < MIN_FLIGHT_SPEED_SQUARED) {
            // Never freeze a missed blade in empty space. A nearly stopped projectile
            // starts falling vertically until it collides with a block or gets recalled.
            velocity = new Vec3d(0.0D, -GRAVITY, 0.0D);
            setVelocity(velocity);
        }

        Vec3d previous = getPos();
        HitResult hit = ProjectileUtil.getCollision(this, this::canDamageEntity);
        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            if (entity instanceof LivingEntity target) {
                hitEntity(world, target, entityHit.getPos());
                return;
            }
        }
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            Vec3d direction = velocity.normalize();
            stickInWorld(blockHit.getPos().subtract(direction.multiply(BLOCK_EMBED_DEPTH)), direction);
            spawnImpactBurst(world, blockHit.getPos(), 7);
            return;
        }

        Vec3d next = previous.add(velocity);
        setPosition(next.x, next.y, next.z);
        spawnTrail(world, previous, next);

        // Arrow-like flight: retain most horizontal speed, lose a little energy to drag,
        // and continuously gain downward velocity. Missed blades therefore arc and land
        // instead of stopping after an arbitrary number of ticks.
        double drag = isTouchingWater() ? WATER_DRAG : AIR_DRAG;
        Vec3d nextVelocity = velocity.multiply(drag).add(0.0D, -GRAVITY, 0.0D);
        setVelocity(nextVelocity);
        setRenderDirection(nextVelocity);
    }

    private void hitEntity(ServerWorld world, LivingEntity target, Vec3d hitPos) {
        Entity owner = getOwner();
        if (owner instanceof ServerPlayerEntity player
                && target.damage(player.getDamageSources().playerAttack(player), damage)) {
            player.heal(HIT_HEAL_AMOUNT);
        }

        Vec3d direction = getVelocity().lengthSquared() > MIN_FLIGHT_SPEED_SQUARED
                ? getVelocity().normalize()
                : getRenderDirection();

        // Move the model origin behind the collision point while keeping its tip aimed
        // along the impact direction. This gives the same visibly impaled result as Bind
        // instead of leaving the whole item floating on the surface of the target.
        Vec3d lodgedPos = hitPos.subtract(direction.multiply(ENTITY_EMBED_DEPTH));
        attachedEntity = target;
        attachedCenterOffset = lodgedPos.subtract(target.getBoundingBox().getCenter());
        setPosition(lodgedPos.x, lodgedPos.y, lodgedPos.z);
        setVelocity(Vec3d.ZERO);
        setRenderDirection(direction);
        setState(STATE_STUCK_ENTITY);
        spawnImpactBurst(world, hitPos, 9);
    }

    private void tickAttached() {
        if (attachedEntity == null || attachedEntity.isRemoved()) {
            attachedEntity = null;
            setState(STATE_STUCK_WORLD);
            setVelocity(Vec3d.ZERO);
            return;
        }

        Vec3d next = attachedEntity.getBoundingBox().getCenter().add(attachedCenterOffset);
        setPosition(next.x, next.y, next.z);
        setVelocity(Vec3d.ZERO);
    }

    private void stickInWorld(Vec3d pos, Vec3d direction) {
        attachedEntity = null;
        attachedCenterOffset = Vec3d.ZERO;
        setPosition(pos.x, pos.y, pos.z);
        setVelocity(Vec3d.ZERO);
        setRenderDirection(direction);
        setState(STATE_STUCK_WORLD);
    }

    private boolean beginRecall(ServerWorld world) {
        Entity owner = getOwner();
        if (!(owner instanceof ServerPlayerEntity player) || player.isRemoved() || player.isDead()) {
            discard();
            return false;
        }

        recallScheduled = false;
        recallTicks = 0;
        recallStart = getPos();
        Vec3d ownerCenter = player.getPos().add(0.0D, 1.0D, 0.0D);
        Vec3d toOwner = ownerCenter.subtract(recallStart);
        Vec3d sideways = new Vec3d(-toOwner.z, 0.0D, toOwner.x);
        if (sideways.lengthSquared() < 1.0E-5D) {
            sideways = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            sideways = sideways.normalize();
        }
        double sideOffset = (world.random.nextDouble() - 0.5D) * 4.0D;
        double lift = 0.8D + world.random.nextDouble() * 1.7D;
        recallControl = recallStart.lerp(ownerCenter, 0.45D)
                .add(sideways.multiply(sideOffset))
                .add(0.0D, lift, 0.0D);
        attachedEntity = null;
        attachedCenterOffset = Vec3d.ZERO;
        setState(STATE_RECALLING);
        setRecallProgress(0.0F);
        setVelocity(Vec3d.ZERO);
        spawnImpactBurst(world, recallStart, 13);
        return true;
    }

    private void tickRecall(ServerWorld world) {
        Entity owner = getOwner();
        if (!(owner instanceof ServerPlayerEntity player) || player.isRemoved() || player.isDead()) {
            discard();
            return;
        }

        recallTicks++;
        float progress = MathHelper.clamp(recallTicks / (float) RECALL_FLIGHT_TICKS, 0.0F, 1.0F);
        float eased = easeInOutCubic(progress);
        Vec3d end = player.getPos().add(0.0D, 1.0D, 0.0D);
        Vec3d first = recallStart.lerp(recallControl, eased);
        Vec3d second = recallControl.lerp(end, eased);
        Vec3d next = first.lerp(second, eased);
        Vec3d previous = getPos();
        Vec3d velocity = next.subtract(previous);

        setPosition(next.x, next.y, next.z);
        setVelocity(velocity);
        if (velocity.lengthSquared() > 1.0E-6D) {
            setRenderDirection(velocity);
        }
        setRecallProgress(progress);
        spawnRecallTrail(world, previous, next);

        if (progress >= 1.0F || next.squaredDistanceTo(end) < 0.12D) {
            spawnImpactBurst(world, end, 8);
            discard();
        }
    }

    private boolean canDamageEntity(Entity entity) {
        Entity owner = getOwner();
        return entity instanceof LivingEntity living
                && living.isAlive()
                && living != owner
                && !living.isSpectator()
                && living.getWorld() == getWorld();
    }

    private void setWeaponStack(ItemStack stack) {
        dataTracker.set(WEAPON_STACK, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    private void setState(int state) {
        dataTracker.set(WEAPON_STATE, state);
    }

    private void setRecallProgress(float progress) {
        dataTracker.set(RECALL_PROGRESS, MathHelper.clamp(progress, 0.0F, 1.0F));
    }

    private void setRenderDirection(Vec3d direction) {
        if (direction.lengthSquared() < 1.0E-6D) {
            return;
        }
        Vec3d normalized = direction.normalize();
        dataTracker.set(DIRECTION_X, (float) normalized.x);
        dataTracker.set(DIRECTION_Y, (float) normalized.y);
        dataTracker.set(DIRECTION_Z, (float) normalized.z);
    }

    private static void spawnTrail(ServerWorld world, Vec3d from, Vec3d to) {
        double distance = from.distanceTo(to);
        int steps = Math.max(1, MathHelper.ceil(distance / 0.28D));
        for (int i = 0; i <= steps; i++) {
            Vec3d pos = from.lerp(to, i / (double) steps);
            world.spawnParticles(TRAIL_PARTICLE, pos.x, pos.y, pos.z, 1, 0.025D, 0.025D, 0.025D, 0.0D);
        }
    }

    private static void spawnRecallTrail(ServerWorld world, Vec3d from, Vec3d to) {
        double distance = from.distanceTo(to);
        int steps = Math.max(1, MathHelper.ceil(distance / 0.22D));
        for (int i = 0; i <= steps; i++) {
            Vec3d pos = from.lerp(to, i / (double) steps);
            world.spawnParticles(RECALL_PARTICLE, pos.x, pos.y, pos.z, 2, 0.045D, 0.045D, 0.045D, 0.0D);
        }
    }

    private static void spawnImpactBurst(ServerWorld world, Vec3d pos, int count) {
        world.spawnParticles(RECALL_PARTICLE, pos.x, pos.y, pos.z, count, 0.18D, 0.18D, 0.18D, 0.025D);
    }

    private static float easeInOutCubic(float value) {
        float clamped = MathHelper.clamp(value, 0.0F, 1.0F);
        if (clamped < 0.5F) {
            return 4.0F * clamped * clamped * clamped;
        }
        float shifted = -2.0F * clamped + 2.0F;
        return 1.0F - shifted * shifted * shifted / 2.0F;
    }
}
