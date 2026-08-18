package ru.siyoga.legacyofthelucii.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
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

public final class ArdynSniperBulletEntity extends ProjectileEntity {
    private static final double AIR_DRAG = 0.9975D;
    private static final double WATER_DRAG = 0.72D;
    private static final double GRAVITY = 0.006D;
    private static final int MAX_LIFETIME_TICKS = 80;
    private static final double MIN_SPEED_SQUARED = 1.0E-8D;
    private static final DustParticleEffect TRAIL_PARTICLE = new DustParticleEffect(
            new Vector3f(0.78F, 0.08F, 0.82F),
            0.65F
    );
    private static final DustParticleEffect IMPACT_PARTICLE = new DustParticleEffect(
            new Vector3f(1.0F, 0.22F, 0.72F),
            1.0F
    );

    private float damage;
    private double maxDistance;
    private double travelledDistance;

    public ArdynSniperBulletEntity(EntityType<? extends ArdynSniperBulletEntity> type, World world) {
        super(type, world);
        this.ignoreCameraFrustum = true;
        this.setInvulnerable(true);
    }

    public void configure(ServerPlayerEntity owner, Vec3d velocity, float damage, double maxDistance) {
        setOwner(owner);
        setVelocity(velocity);
        this.damage = Math.max(0.0F, damage);
        this.maxDistance = Math.max(0.0D, maxDistance);
        this.travelledDistance = 0.0D;
    }

    @Override
    protected void initDataTracker() {
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
        if (age >= MAX_LIFETIME_TICKS || travelledDistance >= maxDistance) {
            discard();
            return;
        }

        Vec3d velocity = getVelocity();
        double speed = velocity.length();
        if (speed * speed <= MIN_SPEED_SQUARED) {
            discard();
            return;
        }

        double remainingDistance = Math.max(0.0D, maxDistance - travelledDistance);
        double travelThisTick = Math.min(speed, remainingDistance);
        if (travelThisTick <= 1.0E-6D) {
            discard();
            return;
        }

        Vec3d stepVelocity = velocity.multiply(travelThisTick / speed);
        setVelocity(stepVelocity);

        Vec3d previous = getPos();
        HitResult hit = ProjectileUtil.getCollision(this, this::canHitEntity);
        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit) {
            setPosition(entityHit.getPos().x, entityHit.getPos().y, entityHit.getPos().z);
            hitEntity(world, entityHit);
            return;
        }
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            setPosition(blockHit.getPos().x, blockHit.getPos().y, blockHit.getPos().z);
            spawnImpact(world, blockHit.getPos());
            discard();
            return;
        }

        Vec3d next = previous.add(stepVelocity);
        setPosition(next.x, next.y, next.z);
        travelledDistance += travelThisTick;
        spawnTrail(world, previous, next);

        if (travelledDistance >= maxDistance) {
            discard();
            return;
        }

        double drag = isTouchingWater() ? WATER_DRAG : AIR_DRAG;
        Vec3d nextVelocity = stepVelocity.multiply(drag).add(0.0D, -GRAVITY, 0.0D);
        setVelocity(nextVelocity);
    }

    private void hitEntity(ServerWorld world, EntityHitResult hit) {
        Entity entity = hit.getEntity();
        if (!(entity instanceof LivingEntity target)) {
            discard();
            return;
        }

        Entity owner = getOwner();
        if (owner instanceof ServerPlayerEntity player) {
            target.damage(target.getDamageSources().thrown(this, player), damage);
        } else {
            target.damage(target.getDamageSources().thrown(this, owner), damage);
        }
        spawnImpact(world, hit.getPos());
        discard();
    }

    private boolean canHitEntity(Entity entity) {
        Entity owner = getOwner();
        return entity instanceof LivingEntity living
                && living.isAlive()
                && !living.isRemoved()
                && !living.isSpectator()
                && living != owner
                && living.getWorld() == getWorld();
    }

    private static void spawnTrail(ServerWorld world, Vec3d from, Vec3d to) {
        double distance = from.distanceTo(to);
        int steps = Math.max(1, MathHelper.ceil(distance / 0.9D));
        for (int i = 0; i <= steps; i++) {
            Vec3d pos = from.lerp(to, i / (double) steps);
            world.spawnParticles(
                    TRAIL_PARTICLE,
                    pos.x,
                    pos.y,
                    pos.z,
                    1,
                    0.012D,
                    0.012D,
                    0.012D,
                    0.0D
            );
        }
    }

    private static void spawnImpact(ServerWorld world, Vec3d pos) {
        world.spawnParticles(
                IMPACT_PARTICLE,
                pos.x,
                pos.y,
                pos.z,
                7,
                0.08D,
                0.08D,
                0.08D,
                0.015D
        );
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
