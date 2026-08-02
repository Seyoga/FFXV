package ru.siyoga.legacyofthelucii.client.state;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DemonizationClientState {
    private static final Set<UUID> DEMONIZED = new HashSet<>();

    private DemonizationClientState() {
    }

    public static void update(UUID entityUuid, boolean demonized) {
        if (demonized) {
            DEMONIZED.add(entityUuid);
        } else {
            DEMONIZED.remove(entityUuid);
        }
    }

    public static boolean isDemonized(UUID entityUuid) {
        return DEMONIZED.contains(entityUuid);
    }

    public static void clear() {
        DEMONIZED.clear();
    }
}
