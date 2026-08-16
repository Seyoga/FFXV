package ru.siyoga.legacyofthelucii.royalarms.orbit;

import net.minecraft.item.ItemStack;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.royalarms.ability.RoyalArmsInventoryItems;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mutable server-authoritative orbit model. Snapshots are safe to consume on clients. */
public final class RoyalArmsOrbitState {
    private final Map<String, SlotState> slots = new LinkedHashMap<>();
    private double previousPhase;
    private double phase;
    private float speed = RoyalArmsOrbitMath.FAST_ORBIT_SPEED;
    private boolean previousSneaking;
    private boolean pausedByDoubleSneak;
    private int sneakDoubleTapTicks;
    private int activeTicks;
    private long serverWorldTime;

    public TickResult tick(
            long worldTime,
            boolean sneaking,
            LuciiLegacy legacy,
            int charges,
            List<RoyalArmsInventoryItems.OrbitItem> items
    ) {
        boolean itemsChanged = synchronizeItems(legacy, charges, items);
        float previousSpeed = speed;
        boolean previousPaused = pausedByDoubleSneak;

        if (sneaking && !previousSneaking) {
            if (sneakDoubleTapTicks > 0) {
                pausedByDoubleSneak = true;
                sneakDoubleTapTicks = 0;
            } else {
                sneakDoubleTapTicks = 8;
            }
        }
        if (!sneaking && previousSneaking) {
            pausedByDoubleSneak = false;
        }
        if (sneakDoubleTapTicks > 0) {
            sneakDoubleTapTicks--;
        }

        speed = pausedByDoubleSneak && sneaking
                ? 0.0F
                : sneaking ? RoyalArmsOrbitMath.NORMAL_ORBIT_SPEED : RoyalArmsOrbitMath.FAST_ORBIT_SPEED;
        previousSneaking = sneaking;
        previousPhase = phase;
        phase += speed;
        activeTicks++;
        serverWorldTime = worldTime;

        for (SlotState slot : slots.values()) {
            slot.baseAngle = RoyalArmsOrbitMath.advanceAngle(slot.baseAngle, slot.targetBaseAngle, 1.0D);
            slot.innerProgress = RoyalArmsOrbitMath.advanceInnerProgress(slot.innerProgress, slot.innerTarget, 1.0D);
            slot.spawnTicks = Math.min(RoyalArmsOrbitMath.APPEAR_TICKS, slot.spawnTicks + 1);
        }

        return new TickResult(
                itemsChanged,
                Float.compare(previousSpeed, speed) != 0 || previousPaused != pausedByDoubleSneak
        );
    }

    public boolean synchronizeItems(
            LuciiLegacy legacy,
            int charges,
            List<RoyalArmsInventoryItems.OrbitItem> items
    ) {
        Map<String, SlotState> next = new LinkedHashMap<>();
        boolean changed = slots.size() != items.size();
        int total = items.size();
        for (int i = 0; i < total; i++) {
            RoyalArmsInventoryItems.OrbitItem item = items.get(i);
            int index = i + 1;
            float targetAngle = RoyalArmsOrbitMath.targetBaseAngle(legacy, charges, index, total);
            boolean innerTarget = RoyalArmsOrbitMath.innerRingTarget(legacy, charges, index, total);
            SlotState slot = slots.get(item.key());
            if (slot == null) {
                slot = new SlotState(
                        item.key(),
                        item.sourceSlot(),
                        item.stack(),
                        index,
                        targetAngle,
                        targetAngle,
                        innerTarget ? 1.0F : 0.0F,
                        innerTarget,
                        0
                );
                changed = true;
            } else {
                if (slot.index != index
                        || slot.sourceSlot != item.sourceSlot()
                        || !ItemStack.areEqual(slot.stack, item.stack())) {
                    changed = true;
                }
                slot.sourceSlot = item.sourceSlot();
                slot.stack = item.stack().copyWithCount(item.stack().getCount());
                slot.index = index;
                slot.targetBaseAngle = targetAngle;
                slot.innerTarget = innerTarget;
            }
            next.put(item.key(), slot);
        }
        if (!slots.keySet().equals(next.keySet())) {
            changed = true;
        }
        slots.clear();
        slots.putAll(next);
        return changed;
    }

    public Snapshot snapshot(long worldTime) {
        List<SlotSnapshot> slotSnapshots = new ArrayList<>(slots.size());
        for (SlotState slot : slots.values()) {
            slotSnapshots.add(slot.snapshot());
        }
        return new Snapshot(
                activeTicks == 0 ? worldTime : serverWorldTime,
                previousPhase,
                phase,
                speed,
                pausedByDoubleSneak,
                activeTicks,
                List.copyOf(slotSnapshots)
        );
    }

    public record TickResult(boolean itemsChanged, boolean motionModeChanged) {
    }

    public record Snapshot(
            long serverWorldTime,
            double previousPhase,
            double phase,
            float speed,
            boolean paused,
            int activeTicks,
            List<SlotSnapshot> slots
    ) {
        public static Snapshot empty(long serverWorldTime) {
            return new Snapshot(serverWorldTime, 0.0D, 0.0D, 0.0F, false, 0, List.of());
        }
    }

    public record SlotSnapshot(
            String key,
            int sourceSlot,
            ItemStack stack,
            int index,
            float baseAngle,
            float targetBaseAngle,
            float innerProgress,
            boolean innerTarget,
            int spawnTicks
    ) {
    }

    private static final class SlotState {
        private final String key;
        private int sourceSlot;
        private ItemStack stack;
        private int index;
        private float baseAngle;
        private float targetBaseAngle;
        private float innerProgress;
        private boolean innerTarget;
        private int spawnTicks;

        private SlotState(
                String key,
                int sourceSlot,
                ItemStack stack,
                int index,
                float baseAngle,
                float targetBaseAngle,
                float innerProgress,
                boolean innerTarget,
                int spawnTicks
        ) {
            this.key = key;
            this.sourceSlot = sourceSlot;
            this.stack = stack.copyWithCount(stack.getCount());
            this.index = index;
            this.baseAngle = baseAngle;
            this.targetBaseAngle = targetBaseAngle;
            this.innerProgress = innerProgress;
            this.innerTarget = innerTarget;
            this.spawnTicks = spawnTicks;
        }

        private SlotSnapshot snapshot() {
            return new SlotSnapshot(
                    key,
                    sourceSlot,
                    stack.copyWithCount(stack.getCount()),
                    index,
                    baseAngle,
                    targetBaseAngle,
                    innerProgress,
                    innerTarget,
                    spawnTicks
            );
        }
    }
}
