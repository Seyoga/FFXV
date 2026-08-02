package ru.siyoga.legacyofthelucii;

import net.fabricmc.api.ModInitializer;
import ru.siyoga.legacyofthelucii.demon.DemonFaction;
import ru.siyoga.legacyofthelucii.network.DemonizationNetwork;

public final class DemonizationNetworkingInitializer implements ModInitializer {
    @Override
    public void onInitialize() {
        DemonizationNetwork.register();
        DemonFaction.register();
    }
}
