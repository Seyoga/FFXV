package ru.siyoga.legacyofthelucii.demon;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface DemonizedMobData {
    @Nullable
    UUID legacyofthelucii$getDemonizerUuid();

    void legacyofthelucii$setDemonizerUuid(@Nullable UUID demonizerUuid);
}
