package ru.siyoga.legacyofthelucii.royalarms.inventory;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;

public final class RoyalArmsScreenHandlers {
    public static final ScreenHandlerType<RoyalArmsScreenHandler> ROYAL_ARMS = Registry.register(
            Registries.SCREEN_HANDLER,
            new Identifier(LegacyOfTheLucii.MOD_ID, "royal_arms_inventory"),
            new ScreenHandlerType<>(RoyalArmsScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
    );

    private RoyalArmsScreenHandlers() {
    }

    public static void register() {
    }
}
