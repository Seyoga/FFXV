package ru.siyoga.legacyofthelucii.client.state;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ArdynOverkillClientState {
    private static final Set<UUID> ACTIVE_PLAYERS = new HashSet<>();

    private ArdynOverkillClientState() {
    }

    public static boolean active(UUID playerUuid) {
        return playerUuid != null && ACTIVE_PLAYERS.contains(playerUuid);
    }

    public static void update(UUID playerUuid, boolean active) {
        if (active) {
            ACTIVE_PLAYERS.add(playerUuid);
        } else {
            ACTIVE_PLAYERS.remove(playerUuid);
        }
    }

    public static void reset() {
        ACTIVE_PLAYERS.clear();
    }
}
