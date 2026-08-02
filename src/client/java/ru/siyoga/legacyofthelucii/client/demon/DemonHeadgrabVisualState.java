package ru.siyoga.legacyofthelucii.client.demon;

import net.minecraft.util.math.MathHelper;
import ru.siyoga.legacyofthelucii.demon.headgrab.DemonHeadgrabSystem;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class DemonHeadgrabVisualState {
    private static final int SHRINK_TICKS = 5;

    private static final Map<Integer, VisualAttachment> ATTACHMENTS =
            new HashMap<>();

    private DemonHeadgrabVisualState() {
    }

    public static void update(
            boolean attached,
            int slimeEntityId,
            int victimEntityId
    ) {
        if (slimeEntityId < 0) {
            return;
        }

        if (attached) {
            ATTACHMENTS.put(
                    slimeEntityId,
                    new VisualAttachment(victimEntityId)
            );
            return;
        }

        VisualAttachment state =
                ATTACHMENTS.get(slimeEntityId);

        if (state != null) {
            state.detaching = true;
            state.detachTicks = 0;
            state.scaleAtDetach = getScale(
                    slimeEntityId,
                    0.0F
            );
        }
    }

    public static void tick() {
        Iterator<Map.Entry<Integer, VisualAttachment>> iterator =
                ATTACHMENTS.entrySet().iterator();

        while (iterator.hasNext()) {
            VisualAttachment state =
                    iterator.next().getValue();

            if (!state.detaching) {
                state.ageTicks++;
                continue;
            }

            state.detachTicks++;

            if (state.detachTicks >= SHRINK_TICKS) {
                iterator.remove();
            }
        }
    }

    public static boolean isAttached(int slimeEntityId) {
        VisualAttachment state =
                ATTACHMENTS.get(slimeEntityId);

        return state != null && !state.detaching;
    }

    public static float getScale(
            int slimeEntityId,
            float tickDelta
    ) {
        VisualAttachment state =
                ATTACHMENTS.get(slimeEntityId);

        if (state == null) {
            return 1.0F;
        }

        if (state.detaching) {
            float t = MathHelper.clamp(
                    (state.detachTicks + tickDelta)
                            / SHRINK_TICKS,
                    0.0F,
                    1.0F
            );

            float smooth = smoothStep(t);

            return MathHelper.lerp(
                    smooth,
                    state.scaleAtDetach,
                    1.0F
            );
        }

        return DemonHeadgrabSystem.calculateVisualScale(
                state.ageTicks,
                tickDelta
        );
    }

    public static int getVictimEntityId(
            int slimeEntityId
    ) {
        VisualAttachment state =
                ATTACHMENTS.get(slimeEntityId);

        return state == null
                ? -1
                : state.victimEntityId;
    }

    public static void clear() {
        ATTACHMENTS.clear();
    }

    private static float smoothStep(float value) {
        return value * value
                * (3.0F - 2.0F * value);
    }

    private static final class VisualAttachment {
        private final int victimEntityId;

        private int ageTicks;

        private boolean detaching;
        private int detachTicks;
        private float scaleAtDetach = 1.0F;

        private VisualAttachment(int victimEntityId) {
            this.victimEntityId = victimEntityId;
        }
    }
}
