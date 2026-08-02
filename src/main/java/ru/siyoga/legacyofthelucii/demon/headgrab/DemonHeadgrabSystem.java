package ru.siyoga.legacyofthelucii.demon.headgrab;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import ru.siyoga.legacyofthelucii.LegacyOfTheLucii;
import ru.siyoga.legacyofthelucii.demon.DemonFaction;
import ru.siyoga.legacyofthelucii.effect.Demonization;
import ru.siyoga.legacyofthelucii.network.DemonHeadgrabNetwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class DemonHeadgrabSystem {
    public static final int SMALLEST_SLIME_SIZE = 1;
    public static final int MEDIUM_SLIME_SIZE = 2;

    private static final int AIR_DRAIN_TICKS = 100;
    private static final int LARGE_AIR_DRAIN_TICKS = 140;
    private static final int DROWNING_DAMAGE_INTERVAL = 20;
    private static final float DROWNING_DAMAGE = 2.0F;

    private static final int HEALTH_SIPHON_INTERVAL_TICKS = 60;
    private static final float HEALTH_SIPHON_AMOUNT = 2.0F;
    private static final int MAX_ESCAPE_ATTEMPTS = 15;

    private static final int ESCAPE_COOLDOWN_TICKS = 60;
    private static final int DAMAGED_SLIME_COOLDOWN_TICKS = 20;
    public static final int FAILED_QTE_RETRY_TICKS = 10;

    public static final int VISUAL_GROWTH_TICKS = 12;
    public static final float MAX_VISUAL_SCALE = 1.18F;

    /*
     * The bar rises from 0 to 1 and falls back to 0 in this many ticks.
     * A release strictly above 50% succeeds.
     */
    public static final int QTE_CYCLE_TICKS = 28;
    public static final float QTE_SUCCESS_THRESHOLD = 0.5F;

    private static final Map<UUID, Attachment> BY_VICTIM = new HashMap<>();
    private static final Map<UUID, Attachment> BY_SLIME = new HashMap<>();
    private static final Map<UUID, Integer> ATTACH_COOLDOWNS = new HashMap<>();

    private static boolean registered;

    private DemonHeadgrabSystem() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        ServerTickEvents.END_SERVER_TICK.register(
                DemonHeadgrabSystem::tick
        );

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());

        LegacyOfTheLucii.LOGGER.info(
                "Demon headgrab: attachment, air drain and QTE system registered."
        );
    }

    public static boolean isSmallDemonSlime(SlimeEntity slime) {
        return getMode(slime) == GrabMode.SMALL;
    }

    public static boolean isDemonHeadgrabber(SlimeEntity slime) {
        return getMode(slime) != null;
    }

    public static boolean isAttached(SlimeEntity slime) {
        return BY_SLIME.containsKey(slime.getUuid());
    }

    public static boolean isVictim(ServerPlayerEntity player) {
        return BY_VICTIM.containsKey(player.getUuid());
    }

    public static void onSlimeDamaged(SlimeEntity slime) {
        if (!isDemonHeadgrabber(slime)) {
            return;
        }

        ATTACH_COOLDOWNS.merge(
                slime.getUuid(),
                DAMAGED_SLIME_COOLDOWN_TICKS,
                Math::max
        );
    }

    /**
     * A second player can break any active slime grab with one direct hit.
     * The attached victim may still damage the slime, but cannot use their
     * own attack as an immediate escape shortcut.
     */
    public static boolean tryRescue(
            SlimeEntity slime,
            ServerPlayerEntity rescuer
    ) {
        Attachment attachment = BY_SLIME.get(slime.getUuid());
        if (attachment == null
                || attachment.victim == rescuer) {
            return false;
        }

        detach(attachment, false, false, true);
        return true;
    }

    public static ServerPlayerEntity getAttachedVictim(
            SlimeEntity slime
    ) {
        Attachment attachment =
                BY_SLIME.get(slime.getUuid());

        return attachment == null
                ? null
                : attachment.victim;
    }

    public static boolean canAttemptAttachment(SlimeEntity slime) {
        return isDemonHeadgrabber(slime)
                && slime.isAlive()
                && !slime.isRemoved()
                && !isAttached(slime)
                && ATTACH_COOLDOWNS.getOrDefault(slime.getUuid(), 0) <= 0;
    }

    public static boolean tryAttach(
            SlimeEntity slime,
            ServerPlayerEntity victim
    ) {
        if (!canAttemptAttachment(slime)
                || !victim.isAlive()
                || victim.isRemoved()
                || victim.getWorld() != slime.getWorld()
                || BY_VICTIM.containsKey(victim.getUuid())
                || !DemonFaction.canAttack(slime, victim)) {
            return false;
        }

        GrabMode mode = getMode(slime);
        if (mode == null) {
            return false;
        }

        Attachment attachment = new Attachment(
                slime,
                victim,
                Math.max(0, victim.getAir()),
                mode
        );

        BY_VICTIM.put(victim.getUuid(), attachment);
        BY_SLIME.put(slime.getUuid(), attachment);

        slime.getNavigation().stop();
        slime.setTarget(null);
        slime.setNoGravity(true);
        slime.noClip = true;
        slime.fallDistance = 0.0F;
        slime.setVelocity(Vec3d.ZERO);
        slime.velocityDirty = true;

        lockVictim(attachment);
        placeAttachedSlime(attachment);
        DemonHeadgrabNetwork.sendState(victim, true, slime);

        LegacyOfTheLucii.LOGGER.info(
                "Demon headgrab: slime uuid={} attached to player uuid={}",
                slime.getUuid(),
                victim.getUuid()
        );

        return true;
    }

    public static void handleQteInput(
            ServerPlayerEntity player,
            boolean pressed
    ) {
        Attachment attachment = BY_VICTIM.get(player.getUuid());
        if (attachment == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        int now = server.getTicks();

        if (pressed) {
            if (!attachment.qteHolding
                    && now >= attachment.nextQteAttemptTick) {
                attachment.qteHolding = true;
                attachment.qteStartTick = now;
            }
            return;
        }

        if (!attachment.qteHolding) {
            return;
        }

        attachment.qteHolding = false;

        int heldTicks = Math.max(
                0,
                now - attachment.qteStartTick
        );

        float progress = calculateQteProgress(heldTicks);
        boolean success = progress > attachment.mode.successThreshold;

        DemonHeadgrabNetwork.sendQteResult(
                player,
                success,
                progress
        );

        if (success) {
            detach(attachment, true);
        } else {
            attachment.nextQteAttemptTick =
                    now + FAILED_QTE_RETRY_TICKS;

            if (attachment.mode.hasAttemptLimit()) {
                attachment.failedAttempts++;
                if (attachment.failedAttempts >= MAX_ESCAPE_ATTEMPTS) {
                    detach(attachment, false, true);
                }
            }
        }
    }

    public static float calculateQteProgress(int heldTicks) {
        int phase = Math.floorMod(heldTicks, QTE_CYCLE_TICKS);
        int half = QTE_CYCLE_TICKS / 2;

        if (phase <= half) {
            return phase / (float) half;
        }

        return (QTE_CYCLE_TICKS - phase) / (float) half;
    }

    public static float calculateVisualScale(
            int ageTicks,
            float tickDelta
    ) {
        float value = Math.min(
                1.0F,
                Math.max(
                        0.0F,
                        (ageTicks + tickDelta)
                                / VISUAL_GROWTH_TICKS
                )
        );

        float smooth = value * value
                * (3.0F - 2.0F * value);

        return 1.0F
                + (MAX_VISUAL_SCALE - 1.0F) * smooth;
    }

    private static void tick(MinecraftServer server) {
        tickCooldowns();

        if (BY_VICTIM.isEmpty()) {
            return;
        }

        for (Attachment attachment :
                new ArrayList<>(BY_VICTIM.values())) {
            if (!isAttachmentValid(attachment)) {
                detach(attachment, false);
                continue;
            }

            lockVictim(attachment);
            placeAttachedSlime(attachment);
            applyAttachmentDamage(attachment);
        }
    }

    private static boolean isAttachmentValid(
            Attachment attachment
    ) {
        SlimeEntity slime = attachment.slime;
        ServerPlayerEntity victim = attachment.victim;

        return slime.isAlive()
                && !slime.isRemoved()
                && victim.isAlive()
                && !victim.isRemoved()
                && slime.getWorld() == victim.getWorld()
                && getMode(slime) == attachment.mode
                && DemonFaction.canAttack(slime, victim);
    }

    private static void lockVictim(Attachment attachment) {
        ServerPlayerEntity victim = attachment.victim;
        victim.refreshPositionAndAngles(
                attachment.anchor.x,
                attachment.anchor.y,
                attachment.anchor.z,
                victim.getYaw(),
                victim.getPitch()
        );
        victim.setVelocity(Vec3d.ZERO);
        victim.fallDistance = 0.0F;
    }

    private static void placeAttachedSlime(Attachment attachment) {
        SlimeEntity slime = attachment.slime;
        ServerPlayerEntity victim = attachment.victim;

        if (attachment.mode == GrabMode.MEDIUM) {
            Vec3d forward = victim.getRotationVec(1.0F)
                    .multiply(1.0D, 0.0D, 1.0D);
            if (forward.lengthSquared() < 0.0001D) {
                forward = new Vec3d(0.0D, 0.0D, 1.0D);
            } else {
                forward = forward.normalize();
            }

            placeSlime(
                    slime,
                    victim,
                    attachment.anchor.x + forward.x * 0.48D,
                    attachment.anchor.y,
                    attachment.anchor.z + forward.z * 0.48D
            );
            return;
        }

        if (attachment.mode == GrabMode.LARGE) {
            double y = attachment.anchor.y
                    + (victim.getHeight() - slime.getHeight()) * 0.5D;
            placeSlime(
                    slime,
                    victim,
                    attachment.anchor.x,
                    y,
                    attachment.anchor.z
            );
            return;
        }

        /*
         * The extra client scale grows from the slime renderer's lower origin.
         * Move the entity down while it grows so the visual center stays fixed
         * inside the player's head instead of rising above it like a hat.
         */
        float visualScale = calculateVisualScale(
                attachment.chokeTicks,
                0.0F
        );

        double headCenterY = victim.getEyeY() + 0.07D;

        double y = headCenterY
                - slime.getHeight() * visualScale * 0.5D;

        placeSlime(slime, victim, victim.getX(), y, victim.getZ());
    }

    private static void placeSlime(
            SlimeEntity slime,
            ServerPlayerEntity victim,
            double x,
            double y,
            double z
    ) {
        slime.refreshPositionAndAngles(x, y, z, victim.getYaw(), 0.0F);

        slime.setVelocity(Vec3d.ZERO);
        slime.velocityDirty = true;
        slime.fallDistance = 0.0F;
        slime.setNoGravity(true);
        slime.noClip = true;
    }

    private static void applyAttachmentDamage(
            Attachment attachment
    ) {
        ServerPlayerEntity victim = attachment.victim;
        attachment.chokeTicks++;

        if (attachment.mode.siphonsHealth
                && attachment.chokeTicks % HEALTH_SIPHON_INTERVAL_TICKS == 0) {
            boolean damaged = victim.damage(
                    victim.getDamageSources().magic(),
                    HEALTH_SIPHON_AMOUNT
            );
            if (damaged) {
                strengthenSlime(attachment.slime, HEALTH_SIPHON_AMOUNT);
            }
        }

        if (!attachment.mode.drainsAir) {
            return;
        }

        int airDrainTicks = attachment.mode == GrabMode.LARGE
                ? LARGE_AIR_DRAIN_TICKS
                : AIR_DRAIN_TICKS;

        int remainingAir = Math.max(
                0,
                attachment.startingAir
                        - Math.round(
                                attachment.startingAir
                                        * Math.min(
                                                attachment.chokeTicks,
                                                airDrainTicks
                                        )
                                        / (float) airDrainTicks
                        )
        );

        /*
         * Player ticks normally restore air while on land. This server-end-tick
         * assignment overrides that recovery and guarantees a five-second drain.
         */
        victim.setAir(remainingAir);

        if (attachment.chokeTicks >= airDrainTicks
                && (attachment.chokeTicks - airDrainTicks)
                % DROWNING_DAMAGE_INTERVAL == 0) {
            victim.damage(
                    victim.getDamageSources().drown(),
                    DROWNING_DAMAGE
            );
        }
    }

    private static void strengthenSlime(
            SlimeEntity slime,
            float amount
    ) {
        EntityAttributeInstance maxHealth = slime.getAttributeInstance(
                EntityAttributes.GENERIC_MAX_HEALTH
        );

        if (maxHealth != null) {
            maxHealth.setBaseValue(
                    maxHealth.getBaseValue() + amount
            );
        }

        slime.setHealth(Math.min(
                slime.getMaxHealth(),
                slime.getHealth() + amount
        ));
    }

    private static void detach(
            Attachment attachment,
            boolean successfulEscape
    ) {
        detach(attachment, successfulEscape, false, false);
    }

    private static void detach(
            Attachment attachment,
            boolean successfulEscape,
            boolean automaticEscape
    ) {
        detach(attachment, successfulEscape, automaticEscape, false);
    }

    private static void detach(
            Attachment attachment,
            boolean successfulEscape,
            boolean automaticEscape,
            boolean rescued
    ) {
        if (BY_VICTIM.get(attachment.victim.getUuid())
                != attachment) {
            return;
        }

        BY_VICTIM.remove(attachment.victim.getUuid());
        BY_SLIME.remove(attachment.slime.getUuid());

        SlimeEntity slime = attachment.slime;
        ServerPlayerEntity victim = attachment.victim;

        if (!victim.isRemoved()) {
            victim.setAir(victim.getMaxAir());
            DemonHeadgrabNetwork.sendState(
                    victim,
                    false,
                    slime
            );
        }

        slime.noClip = false;
        slime.setNoGravity(false);

        if ((successfulEscape || automaticEscape || rescued)
                && slime.isAlive()
                && !slime.isRemoved()) {
            victim.sendMessage(Text.literal(
                    rescued
                            ? "Союзник прервал захват слизня!"
                            : automaticEscape
                                    ? "Чудом удалось вырваться из захвата!"
                                    : "Удалось вырваться из захвата!"
            ), true);
            playEscapeSound(victim);
            ATTACH_COOLDOWNS.put(
                    slime.getUuid(),
                    ESCAPE_COOLDOWN_TICKS
            );

            Vec3d away = slime.getPos()
                    .subtract(victim.getPos())
                    .multiply(1.0D, 0.0D, 1.0D);

            if (away.lengthSquared() < 0.0001D) {
                away = victim.getRotationVec(1.0F)
                        .multiply(-1.0D, 0.0D, -1.0D);
            }

            away = away.normalize();

            slime.refreshPositionAndAngles(
                    victim.getX() + away.x * 0.85D,
                    victim.getEyeY() + 0.20D,
                    victim.getZ() + away.z * 0.85D,
                    victim.getYaw(),
                    0.0F
            );

            slime.setVelocity(
                    away.x * 0.48D,
                    0.42D,
                    away.z * 0.48D
            );
            slime.velocityDirty = true;
            slime.setTarget(null);
        }

        LegacyOfTheLucii.LOGGER.info(
                "Demon headgrab: slime uuid={} detached from player uuid={}, qteSuccess={}",
                slime.getUuid(),
                victim.getUuid(),
                successfulEscape
        );
    }

    private static void playEscapeSound(ServerPlayerEntity victim) {
        victim.getWorld().playSound(
                null,
                victim.getBlockPos(),
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                victim.getSoundCategory(),
                0.9F,
                1.25F
        );
    }

    private static GrabMode getMode(SlimeEntity slime) {
        if (!Demonization.isDemonized(slime)) {
            return null;
        }

        if (slime.getSize() == SMALLEST_SLIME_SIZE) {
            return GrabMode.SMALL;
        }

        if (slime.getSize() == MEDIUM_SLIME_SIZE) {
            return GrabMode.MEDIUM;
        }

        return GrabMode.LARGE;
    }

    private static void tickCooldowns() {
        Iterator<Map.Entry<UUID, Integer>> iterator =
                ATTACH_COOLDOWNS.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int next = entry.getValue() - 1;

            if (next <= 0) {
                iterator.remove();
            } else {
                entry.setValue(next);
            }
        }
    }

    private static void clear() {
        for (Attachment attachment :
                new ArrayList<>(BY_VICTIM.values())) {
            SlimeEntity slime = attachment.slime;
            slime.noClip = false;
            slime.setNoGravity(false);
        }

        BY_VICTIM.clear();
        BY_SLIME.clear();
        ATTACH_COOLDOWNS.clear();
    }

    private static final class Attachment {
        private final SlimeEntity slime;
        private final ServerPlayerEntity victim;
        private final int startingAir;
        private final GrabMode mode;
        private final Vec3d anchor;

        private int chokeTicks;
        private int failedAttempts;
        private boolean qteHolding;
        private int qteStartTick;
        private int nextQteAttemptTick;

        private Attachment(
                SlimeEntity slime,
                ServerPlayerEntity victim,
                int startingAir,
                GrabMode mode
        ) {
            this.slime = slime;
            this.victim = victim;
            this.startingAir = startingAir;
            this.mode = mode;
            this.anchor = victim.getPos();
        }
    }

    private enum GrabMode {
        SMALL(QTE_SUCCESS_THRESHOLD, true, false),
        MEDIUM(0.75F, false, true),
        LARGE(0.95F, true, true);

        private final float successThreshold;
        private final boolean drainsAir;
        private final boolean siphonsHealth;

        GrabMode(
                float successThreshold,
                boolean drainsAir,
                boolean siphonsHealth
        ) {
            this.successThreshold = successThreshold;
            this.drainsAir = drainsAir;
            this.siphonsHealth = siphonsHealth;
        }

        private boolean hasAttemptLimit() {
            return this != SMALL;
        }
    }
}
