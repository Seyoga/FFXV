package ru.siyoga.legacyofthelucii.client.royalarms;

import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class RoyalArmsGuardClient {
    private static boolean active;

    private RoyalArmsGuardClient() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void updateState(boolean active) {
        RoyalArmsGuardClient.active = active;
        if (!active) {
            RoyalArmsAbility.clearGuardBlocks();
        }
    }

    public static void block(UUID ownerUuid, Vec3d interceptPos, Vec3d incomingVelocity) {
        RoyalArmsAbility.beginGuardBlock(ownerUuid, interceptPos, incomingVelocity);
    }

    public static void reset() {
        active = false;
        RoyalArmsAbility.clearGuardBlocks();
    }
}
