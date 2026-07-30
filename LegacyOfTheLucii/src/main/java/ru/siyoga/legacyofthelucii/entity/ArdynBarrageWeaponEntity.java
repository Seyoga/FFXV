package ru.siyoga.legacyofthelucii.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class ArdynBarrageWeaponEntity extends Entity {
    private static final TrackedData<ItemStack> STACK = DataTracker.registerData(ArdynBarrageWeaponEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    private static final TrackedData<Integer> STATE = DataTracker.registerData(ArdynBarrageWeaponEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> IMPACT_X = DataTracker.registerData(ArdynBarrageWeaponEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> IMPACT_Y = DataTracker.registerData(ArdynBarrageWeaponEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> IMPACT_Z = DataTracker.registerData(ArdynBarrageWeaponEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final String STACK_KEY = "Stack";
    private static final String STATE_KEY = "BarrageState";
    private static final String IMPACT_X_KEY = "ImpactX";
    private static final String IMPACT_Y_KEY = "ImpactY";
    private static final String IMPACT_Z_KEY = "ImpactZ";

    private ItemStack stack = ItemStack.EMPTY;
    private State state = State.FLYING;
    private Vec3d impactDirection = Vec3d.ZERO;
    private Entity stuckEntity;
    private Vec3d stuckEntityOffset = Vec3d.ZERO;
    private Entity recallTarget;
    private int ageInState;

    public ArdynBarrageWeaponEntity(EntityType<? extends ArdynBarrageWeaponEntity> entityType, World world) {
        super(entityType, world);
        noClip = true;
    }

    public ArdynBarrageWeaponEntity(World world, ItemStack stack, Vec3d pos, Vec3d velocity) {
        this(LegacyEntities.ARDYN_BARRAGE_WEAPON, world);
        this.stack = stack.copyWithCount(1);
        dataTracker.set(STACK, this.stack);
        setPosition(pos);
        setVelocity(velocity);
        impactDirection = velocity.lengthSquared() <= 0.0001D ? Vec3d.ZERO : velocity.normalize();
        syncImpactDirection(impactDirection);
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(STACK, ItemStack.EMPTY);
        dataTracker.startTracking(STATE, State.FLYING.id);
        dataTracker.startTracking(IMPACT_X, 0.0F);
        dataTracker.startTracking(IMPACT_Y, 0.0F);
        dataTracker.startTracking(IMPACT_Z, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        ageInState++;

        if (state == State.FLYING) {
            Vec3d velocity = getVelocity();
            if (velocity.lengthSquared() > 0.0001D) {
                impactDirection = velocity.normalize();
            }
            setPosition(getPos().add(velocity));
            return;
        }

        if (state == State.STUCK_ENTITY) {
            if (stuckEntity == null || stuckEntity.isRemoved()) {
                state = State.STUCK_BLOCK;
                return;
            }

            setPosition(stuckEntity.getPos().add(stuckEntityOffset));
            setVelocity(Vec3d.ZERO);
            return;
        }

        if (state == State.RECALLING) {
            if (recallTarget == null || recallTarget.isRemoved()) {
                discard();
                return;
            }

            Vec3d target = recallTarget.getPos().add(0.0D, recallTarget.getHeight() * 0.55D, 0.0D);
            Vec3d toTarget = target.subtract(getPos());
            if (toTarget.lengthSquared() < 0.25D) {
                discard();
                return;
            }

            Vec3d velocity = toTarget.normalize().multiply(MathHelper.clamp(0.38D + ageInState * 0.025D, 0.38D, 1.05D));
            setVelocity(velocity);
            impactDirection = velocity.normalize();
            setPosition(getPos().add(velocity));
        }
    }

    public ItemStack stack() {
        return getWorld().isClient ? dataTracker.get(STACK) : stack;
    }

    public State barrageState() {
        return getWorld().isClient ? State.byId(dataTracker.get(STATE)) : state;
    }

    public Vec3d impactDirection() {
        if (getWorld().isClient) {
            Vec3d direction = new Vec3d(dataTracker.get(IMPACT_X), dataTracker.get(IMPACT_Y), dataTracker.get(IMPACT_Z));
            if (direction.lengthSquared() > 0.0001D) {
                return direction.normalize();
            }
        }
        if (impactDirection.lengthSquared() <= 0.0001D) {
            Vec3d velocity = getVelocity();
            return velocity.lengthSquared() <= 0.0001D ? Vec3d.ZERO : velocity.normalize();
        }
        return impactDirection;
    }

    public void stickInBlock(Vec3d pos, Vec3d direction) {
        setState(State.STUCK_BLOCK);
        setPosition(pos);
        setVelocity(Vec3d.ZERO);
        impactDirection = normalizedOrFallback(direction);
    }

    public void stickInEntity(Entity entity, Vec3d pos, Vec3d direction) {
        stuckEntity = entity;
        stuckEntityOffset = pos.subtract(entity.getPos());
        setState(State.STUCK_ENTITY);
        setPosition(pos);
        setVelocity(Vec3d.ZERO);
        impactDirection = normalizedOrFallback(direction);
    }

    public void recallTo(Entity target) {
        stuckEntity = null;
        recallTarget = target;
        setState(State.RECALLING);
    }

    private void setState(State state) {
        this.state = state;
        dataTracker.set(STATE, state.id);
        ageInState = 0;
    }

    private Vec3d normalizedOrFallback(Vec3d direction) {
        if (direction.lengthSquared() > 0.0001D) {
            Vec3d normalized = direction.normalize();
            syncImpactDirection(normalized);
            return normalized;
        }
        Vec3d velocity = getVelocity();
        Vec3d normalized = velocity.lengthSquared() > 0.0001D ? velocity.normalize() : Vec3d.ZERO;
        syncImpactDirection(normalized);
        return normalized;
    }

    private void syncImpactDirection(Vec3d direction) {
        dataTracker.set(IMPACT_X, (float) direction.x);
        dataTracker.set(IMPACT_Y, (float) direction.y);
        dataTracker.set(IMPACT_Z, (float) direction.z);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        stack = ItemStack.fromNbt(nbt.getCompound(STACK_KEY));
        state = State.byId(nbt.getInt(STATE_KEY));
        impactDirection = new Vec3d(nbt.getDouble(IMPACT_X_KEY), nbt.getDouble(IMPACT_Y_KEY), nbt.getDouble(IMPACT_Z_KEY));
        dataTracker.set(STACK, stack);
        dataTracker.set(STATE, state.id);
        syncImpactDirection(impactDirection);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (!stack.isEmpty()) {
            nbt.put(STACK_KEY, stack.writeNbt(new NbtCompound()));
        }
        nbt.putInt(STATE_KEY, state.id);
        nbt.putDouble(IMPACT_X_KEY, impactDirection.x);
        nbt.putDouble(IMPACT_Y_KEY, impactDirection.y);
        nbt.putDouble(IMPACT_Z_KEY, impactDirection.z);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    public enum State {
        FLYING(0),
        STUCK_BLOCK(1),
        STUCK_ENTITY(2),
        RECALLING(3);

        private final int id;

        State(int id) {
            this.id = id;
        }

        private static State byId(int id) {
            for (State state : values()) {
                if (state.id == id) {
                    return state;
                }
            }
            return FLYING;
        }
    }
}
