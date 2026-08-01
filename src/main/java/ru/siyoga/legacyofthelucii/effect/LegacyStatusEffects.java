package ru.siyoga.legacyofthelucii.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;

public final class LegacyStatusEffects {
    public static final StatusEffect DEMONIZATION = Registry.register(
            Registries.STATUS_EFFECT,
            new Identifier(LegacyOfTheLucii.MOD_ID, "demonization"),
            new DemonizationStatusEffect()
    );

    private LegacyStatusEffects() {
    }

    public static void register() {
        LegacyOfTheLucii.LOGGER.info("Registering Legacy of the Lucii status effects.");
    }

    private static final class DemonizationStatusEffect extends StatusEffect {
        private DemonizationStatusEffect() {
            super(StatusEffectCategory.NEUTRAL, 0x400A66);
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return false;
        }
    }
}
