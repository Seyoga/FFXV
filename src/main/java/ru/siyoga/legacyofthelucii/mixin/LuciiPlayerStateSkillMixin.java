package ru.siyoga.legacyofthelucii.mixin;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.skilltree.LuciiSkillStateAccess;
import ru.siyoga.legacyofthelucii.skilltree.LuciiSkills;

import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(LuciiPlayerState.class)
public abstract class LuciiPlayerStateSkillMixin implements LuciiSkillStateAccess {
    @Unique
    private static final String LEGACY_OF_THE_LUCII_SKILLS_KEY = "UnlockedSkills";

    @Unique
    private final Set<String> legacyOfTheLucii$unlockedSkills = new LinkedHashSet<>();

    @Override
    public Set<String> legacyOfTheLucii$getUnlockedSkills() {
        return legacyOfTheLucii$unlockedSkills;
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void legacyOfTheLucii$readSkills(NbtCompound nbt, CallbackInfo ci) {
        legacyOfTheLucii$unlockedSkills.clear();
        NbtList list = nbt.getList(LEGACY_OF_THE_LUCII_SKILLS_KEY, 8);
        for (int i = 0; i < list.size(); i++) {
            legacyOfTheLucii$unlockedSkills.add(list.getString(i));
        }
        LuciiSkills.syncInventorySlots((LuciiPlayerState) (Object) this);
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void legacyOfTheLucii$writeSkills(NbtCompound nbt, CallbackInfo ci) {
        NbtList list = new NbtList();
        for (String id : legacyOfTheLucii$unlockedSkills) {
            list.add(NbtString.of(id));
        }
        nbt.put(LEGACY_OF_THE_LUCII_SKILLS_KEY, list);
    }

    @Inject(method = "unlockLegacy", at = @At("TAIL"))
    private void legacyOfTheLucii$refreshInventorySlots(LuciiLegacy legacy, CallbackInfo ci) {
        LuciiSkills.syncInventorySlots((LuciiPlayerState) (Object) this);
    }
}
