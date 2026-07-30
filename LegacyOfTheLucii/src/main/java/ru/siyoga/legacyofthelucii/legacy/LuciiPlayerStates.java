package ru.siyoga.legacyofthelucii.legacy;

import net.minecraft.entity.player.PlayerEntity;

public final class LuciiPlayerStates {
    private LuciiPlayerStates() {
    }

    public static LuciiPlayerState get(PlayerEntity player) {
        return ((LuciiPlayerStateAccess) player).legacyOfTheLucii$getLuciiState();
    }
}
