package ru.siyoga.legacyofthelucii.mixin.demonization;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.siyoga.legacyofthelucii.demon.DemonFaction;
import ru.siyoga.legacyofthelucii.demon.DemonizedMobData;
import ru.siyoga.legacyofthelucii.demon.ai.DemonMeleeAttackGoal;
import ru.siyoga.legacyofthelucii.demon.ai.DemonTargetGoal;
import ru.siyoga.legacyofthelucii.effect.Demonization;

import java.util.UUID;

@Mixin(MobEntity.class)
public abstract class MobEntityDemonFactionMixin implements DemonizedMobData {
    @Unique
    private static final String DEMONIZER_UUID_NBT_KEY =
            "LegacyOfTheLuciiDemonizer";

    @Shadow
    @Final
    protected GoalSelector goalSelector;

    @Shadow
    @Final
    protected GoalSelector targetSelector;

    @Shadow
    @Nullable
    private LivingEntity target;

    @Unique
    @Nullable
    private UUID legacyofthelucii$demonizerUuid;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void legacyofthelucii$installDemonGoals(
            EntityType<? extends MobEntity> entityType,
            World world,
            CallbackInfo ci
    ) {
        MobEntity self = (MobEntity) (Object) this;

        // Priority zero makes demon behavior override grazing, fleeing and
        // vanilla hostile targeting only while the goals can actually start.
        targetSelector.add(0, new DemonTargetGoal(self));
        goalSelector.add(0, new DemonMeleeAttackGoal(self));
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void legacyofthelucii$writeDemonizer(
            NbtCompound nbt,
            CallbackInfo ci
    ) {
        if (legacyofthelucii$demonizerUuid != null) {
            nbt.putUuid(
                    DEMONIZER_UUID_NBT_KEY,
                    legacyofthelucii$demonizerUuid
            );
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void legacyofthelucii$readDemonizer(
            NbtCompound nbt,
            CallbackInfo ci
    ) {
        legacyofthelucii$demonizerUuid =
                nbt.containsUuid(DEMONIZER_UUID_NBT_KEY)
                        ? nbt.getUuid(DEMONIZER_UUID_NBT_KEY)
                        : null;
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void legacyofthelucii$preventFriendlyTargets(
            @Nullable LivingEntity requestedTarget,
            CallbackInfo ci
    ) {
        MobEntity self = (MobEntity) (Object) this;

        if (requestedTarget != null
                && Demonization.isDemonized(self)
                && !DemonFaction.canAttack(self, requestedTarget)) {
            target = null;
            ci.cancel();
        }
    }

    @Override
    public @Nullable UUID legacyofthelucii$getDemonizerUuid() {
        return legacyofthelucii$demonizerUuid;
    }

    @Override
    public void legacyofthelucii$setDemonizerUuid(
            @Nullable UUID demonizerUuid
    ) {
        legacyofthelucii$demonizerUuid = demonizerUuid;
    }
}
