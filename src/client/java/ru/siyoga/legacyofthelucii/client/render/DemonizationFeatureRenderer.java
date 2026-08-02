package ru.siyoga.legacyofthelucii.client.render;

import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;

/**
 * The demonic body color is rendered directly by DemonizationBaseTintMixin.
 * This class deliberately registers no extra shell, outline or glow.
 */
public final class DemonizationFeatureRenderer {
    private static boolean logged;

    private DemonizationFeatureRenderer() {
    }

    public static void register() {
        if (!logged) {
            logged = true;
            LegacyOfTheLucii.LOGGER.info(
                    "Demonization renderer v10: extra glow/outline disabled; base tint only."
            );
        }
    }
}
