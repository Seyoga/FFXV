package ru.siyoga.legacyofthelucii.client.masquerade;

import ru.siyoga.legacyofthelucii.masquerade.MasqueradeMorph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MasqueradeClientState {
    private static final Map<UUID, MasqueradeMorph> OBSERVER_MORPHS = new HashMap<>();
    private static final Map<UUID, UUID> SWAP_SOURCES = new HashMap<>();
    private static List<MasqueradeMorph> unlockedMorphs = List.of();
    private static MasqueradeMorph localActiveMorph;
    private static UUID localTargetUuid;
    private static int localTargetEntityId = -1;
    private static int revision;

    private MasqueradeClientState() {
    }

    public static List<MasqueradeMorph> unlockedMorphs() {
        return Collections.unmodifiableList(unlockedMorphs);
    }

    public static MasqueradeMorph localActiveMorph() {
        return localActiveMorph;
    }

    public static MasqueradeMorph activeMorph(UUID playerUuid) {
        return OBSERVER_MORPHS.get(playerUuid);
    }

    public static UUID localTargetUuid() {
        return localTargetUuid;
    }

    public static int localTargetEntityId() {
        return localTargetEntityId;
    }

    public static int revision() {
        return revision;
    }

    public static void updateOwnerState(
            UUID localPlayerUuid,
            List<MasqueradeMorph> unlocked,
            MasqueradeMorph active,
            UUID targetUuid,
            int targetEntityId
    ) {
        unlockedMorphs = List.copyOf(new ArrayList<>(unlocked));
        localActiveMorph = active;
        localTargetUuid = targetUuid;
        localTargetEntityId = targetEntityId;
        revision++;
    }

    public static void updateObserverVisual(
            UUID ownerUuid,
            MasqueradeMorph morph,
            UUID swapSourceUuid,
            MasqueradeMorph swapMorph
    ) {
        UUID previousSwapSource = SWAP_SOURCES.remove(ownerUuid);
        if (previousSwapSource != null) {
            OBSERVER_MORPHS.remove(previousSwapSource);
        }
        if (morph == null) {
            OBSERVER_MORPHS.remove(ownerUuid);
            return;
        }
        OBSERVER_MORPHS.put(ownerUuid, morph);
        if (swapSourceUuid != null && swapMorph != null) {
            SWAP_SOURCES.put(ownerUuid, swapSourceUuid);
            OBSERVER_MORPHS.put(swapSourceUuid, swapMorph);
        }
    }

    public static void reset() {
        OBSERVER_MORPHS.clear();
        SWAP_SOURCES.clear();
        unlockedMorphs = List.of();
        localActiveMorph = null;
        localTargetUuid = null;
        localTargetEntityId = -1;
        revision++;
    }
}
