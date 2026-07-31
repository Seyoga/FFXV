package ru.siyoga.legacyofthelucii.client.royalarms;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class RoyalArmsGuardClient {
    private static final Set<UUID> ACTIVE_OWNERS = new HashSet<>();

    private RoyalArmsGuardClient() {
    }

    public static boolean isActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && isActive(client.player.getUuid());
    }

    public static boolean isActive(UUID ownerUuid) {
        return ACTIVE_OWNERS.contains(ownerUuid);
    }

    public static void updateState(UUID ownerUuid, boolean active) {
        if (active) {
            ACTIVE_OWNERS.add(ownerUuid);
        } else {
            ACTIVE_OWNERS.remove(ownerUuid);
        }
        RoyalArmsAbility.updateGuardState(ownerUuid, active);
    }

    public static void block(
            UUID ownerUuid,
            Vec3d interceptPos,
            Vec3d incomingVelocity,
            int travelTicks,
            int layer
    ) {
        RoyalArmsAbility.beginGuardBlock(
                ownerUuid,
                interceptPos,
                incomingVelocity,
                travelTicks,
                layer
        );
    }

    public static void explosion(UUID ownerUuid, int itemCount, float protection) {
        RoyalArmsAbility.beginExplosionGuard(ownerUuid, itemCount, protection);
    }

    public static void reset() {
        ACTIVE_OWNERS.clear();
        RoyalArmsAbility.clearGuardBlocks();
    }
}
