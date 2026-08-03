package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStateAccess;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynPointWarpAbility;
import ru.siyoga.legacyofthelucii.royalarms.ability.ArdynShadowStepAbility;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements LuciiPlayerStateAccess {
    @Unique
    private final LuciiPlayerState legacyOfTheLucii$state = new LuciiPlayerState();

    @Override
    public LuciiPlayerState legacyOfTheLucii$getLuciiState() {
        return legacyOfTheLucii$state;
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void legacyOfTheLucii$readState(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains(LegacyOfTheLucii.MOD_ID)) {
            legacyOfTheLucii$state.readNbt(nbt.getCompound(LegacyOfTheLucii.MOD_ID));
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void legacyOfTheLucii$writeState(NbtCompound nbt, CallbackInfo ci) {
        NbtCompound stateNbt = new NbtCompound();
        legacyOfTheLucii$state.writeNbt(stateNbt);
        nbt.put(LegacyOfTheLucii.MOD_ID, stateNbt);
    }

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void legacyOfTheLucii$muteWarpFootsteps(BlockPos pos, BlockState state, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient
                && (ArdynShadowStepAbility.isActive(player.getUuid())
                || ArdynPointWarpAbility.isActive(player.getUuid()))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void legacyOfTheLucii$preventWarpFallDamage(
            float fallDistance,
            float damageMultiplier,
            DamageSource damageSource,
            CallbackInfoReturnable<Boolean> cir
    ) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient
                && (ArdynShadowStepAbility.hasFallProtection(player.getUuid())
                || ArdynPointWarpAbility.hasFallProtection(player.getUuid()))) {
            player.fallDistance = 0.0F;
            cir.setReturnValue(false);
        }
    }
}
