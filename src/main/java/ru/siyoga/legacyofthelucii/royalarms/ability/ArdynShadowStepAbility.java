package ru.siyoga.legacyofthelucii.royalarms.ability;

import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ArdynShadowStepAbility {
    private static final int MAX_TICKS = 30;
    private static final int MAX_MANA_COST = 25;
    private static final int FALL_PROTECTION_TICKS = 20;
    private static final double AUTO_MIN_TOTAL_FALL_DISTANCE = 10.0D;
    private static final double AUTO_TRIGGER_GROUND_DISTANCE = 5.0D;
    private static final double AUTO_LANDING_SPEED = -0.18D;
    private static final double AUTO_HORIZONTAL_DAMPING = 0.25D;
    private static final double SPEED = ArdynMovementTuning.PHASE_SPEED_PER_TICK;
    private static final DustParticleEffect ASH_PARTICLE = new DustParticleEffect(new Vector3f(0.02F, 0.01F, 0.01F), 1.45F);

    private static final Map<UUID, ActiveStep> ACTIVE_STEPS = new HashMap<>();
    private static final Map<UUID, Integer> FALL_PROTECTION = new HashMap<>();

    private ArdynShadowStepAbility() {
    }

    public static void setActive(ServerPlayerEntity player, boolean active) {
        if (active) {
            start(player);
        } else {
            stop(player);
        }
    }

    public static void tick(MinecraftServer server) {
        tickFallProtection();
        tryStartAutomaticLandingSteps(server);

        Iterator<Map.Entry<UUID, ActiveStep>> iterator = ACTIVE_STEPS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveStep> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            ActiveStep step = entry.getValue();
            if (player == null || player.isRemoved() || player.isDead()) {
                iterator.remove();
                continue;
            }

            LuciiPlayerState state = LuciiPlayerStates.get(player);
            if (state.legacy() != LuciiLegacy.ARDYN || !state.royalArmsActive()) {
                end(player, iterator, true);
                continue;
            }

            player.fallDistance = 0.0F;
            step.ticks++;
            if (!player.getAbilities().creativeMode) {
                int requiredManaSpent = (int) Math.ceil(step.ticks * (MAX_MANA_COST / (double) MAX_TICKS));
                int manaToSpend = requiredManaSpent - step.manaSpent;
                if (manaToSpend > 0) {
                    if (!state.spendMana(manaToSpend)) {
                        end(player, iterator, true);
                        continue;
                    }
                    step.manaSpent += manaToSpend;
                    LuciiNetwork.sendState(player);
                }
            }

            if (step.automaticLanding) {
                moveAutomaticLanding(player);
            } else {
                move(player);
            }
            spawnAsh(player.getServerWorld(), player.getPos().add(0.0D, 1.0D, 0.0D));
            if (step.ticks >= MAX_TICKS || (step.automaticLanding && player.isOnGround())) {
                end(player, iterator, true);
            }
        }
    }

    public static void clearAll(ServerPlayerEntity player) {
        if (ACTIVE_STEPS.remove(player.getUuid()) != null) {
            LuciiNetwork.broadcastArdynShadowStep(player.getServerWorld(), player, false);
        }
        FALL_PROTECTION.remove(player.getUuid());
    }

    public static void clearAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            clearAll(player);
        }
        ACTIVE_STEPS.clear();
    }

    public static boolean isActive(UUID playerUuid) {
        return ACTIVE_STEPS.containsKey(playerUuid);
    }

    public static boolean hasFallProtection(UUID playerUuid) {
        return ACTIVE_STEPS.containsKey(playerUuid) || FALL_PROTECTION.containsKey(playerUuid);
    }

    private static void start(ServerPlayerEntity player) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() != LuciiLegacy.ARDYN
                || !state.royalArmsActive()
                || (!player.getAbilities().creativeMode && !state.hasMana(1))
                || ACTIVE_STEPS.containsKey(player.getUuid())) {
            return;
        }

        ACTIVE_STEPS.put(player.getUuid(), new ActiveStep(false));
        player.fallDistance = 0.0F;
        LuciiNetwork.broadcastArdynShadowStep(player.getServerWorld(), player, true);
    }

    private static void stop(ServerPlayerEntity player) {
        if (ACTIVE_STEPS.remove(player.getUuid()) != null) {
            protectFromFall(player);
            LuciiNetwork.broadcastArdynShadowStep(player.getServerWorld(), player, false);
        }
    }

    private static void end(ServerPlayerEntity player, Iterator<Map.Entry<UUID, ActiveStep>> iterator, boolean protectFromFall) {
        iterator.remove();
        if (protectFromFall) {
            protectFromFall(player);
        }
        LuciiNetwork.broadcastArdynShadowStep(player.getServerWorld(), player, false);
    }

    private static void move(ServerPlayerEntity player) {
        Vec3d direction = player.getRotationVec(1.0F);
        player.setVelocity(direction.x * SPEED, direction.y * SPEED * 0.65D, direction.z * SPEED);
        player.velocityModified = true;
    }

    private static void moveAutomaticLanding(ServerPlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        player.setVelocity(velocity.x * AUTO_HORIZONTAL_DAMPING, AUTO_LANDING_SPEED, velocity.z * AUTO_HORIZONTAL_DAMPING);
        player.velocityModified = true;
        player.fallDistance = 0.0F;
    }

    private static void spawnAsh(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ASH_PARTICLE, pos.x, pos.y, pos.z, 18, 0.45D, 0.65D, 0.45D, 0.02D);
    }

    private static void tryStartAutomaticLandingSteps(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (ACTIVE_STEPS.containsKey(player.getUuid())
                    || player.isOnGround()
                    || player.getVelocity().y >= -0.1D) {
                continue;
            }

            LuciiPlayerState state = LuciiPlayerStates.get(player);
            if (state.legacy() != LuciiLegacy.ARDYN
                    || !state.royalArmsActive()
                    || (!player.getAbilities().creativeMode && !state.hasMana(MAX_MANA_COST))) {
                continue;
            }

            double groundDistance = distanceToGround(player);
            double projectedFallDistance = player.fallDistance + groundDistance;
            if (groundDistance <= AUTO_TRIGGER_GROUND_DISTANCE
                    && projectedFallDistance >= AUTO_MIN_TOTAL_FALL_DISTANCE) {
                ACTIVE_STEPS.put(player.getUuid(), new ActiveStep(true));
                player.fallDistance = 0.0F;
                LuciiNetwork.broadcastArdynShadowStep(player.getServerWorld(), player, true);
            }
        }
    }

    private static double distanceToGround(ServerPlayerEntity player) {
        Vec3d start = player.getPos().add(0.0D, 0.1D, 0.0D);
        Vec3d end = start.subtract(0.0D, AUTO_TRIGGER_GROUND_DISTANCE, 0.0D);
        HitResult hit = player.getServerWorld().raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return Double.MAX_VALUE;
        }
        return start.y - hit.getPos().y;
    }

    private static void protectFromFall(ServerPlayerEntity player) {
        player.fallDistance = 0.0F;
        FALL_PROTECTION.put(player.getUuid(), FALL_PROTECTION_TICKS);
    }

    private static void tickFallProtection() {
        Iterator<Map.Entry<UUID, Integer>> iterator = FALL_PROTECTION.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int ticks = entry.getValue() - 1;
            if (ticks <= 0) {
                iterator.remove();
            } else {
                entry.setValue(ticks);
            }
        }
    }

    private static final class ActiveStep {
        private int ticks;
        private int manaSpent;
        private final boolean automaticLanding;

        private ActiveStep(boolean automaticLanding) {
            this.automaticLanding = automaticLanding;
        }
    }
}
