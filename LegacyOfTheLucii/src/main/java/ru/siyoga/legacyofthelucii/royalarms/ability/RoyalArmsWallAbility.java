package ru.siyoga.legacyofthelucii.royalarms.ability;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ru.siyoga.legacyofthelucii.block.LegacyBlocks;
import ru.siyoga.legacyofthelucii.block.RoyalArmsWallBlockEntity;
import ru.siyoga.legacyofthelucii.legacy.LuciiLegacy;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerState;
import ru.siyoga.legacyofthelucii.legacy.LuciiPlayerStates;
import ru.siyoga.legacyofthelucii.network.LuciiNetwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RoyalArmsWallAbility {
    private static final int WALL_WIDTH = 5;
    private static final int WALL_HEIGHT = 3;
    private static final double MAX_CAST_DISTANCE = 5.0D;
    private static final float LIFT_LOOK_PITCH = 55.0F;
    private static final int WALL_MANA_COST = 20;
    private static final int LIFT_MANA_COST = 5;
    private static final int MIN_RESTORE_TICKS = 60;
    private static final int MAX_RESTORE_TICKS = 20 * 90;
    private static final float RESTORE_TICKS_PER_HARDNESS = 20.0F;

    private static final Map<UUID, ActiveWall> ACTIVE_WALLS = new HashMap<>();
    private static final Map<SegmentKey, CooldownSegment> COOLDOWNS = new HashMap<>();
    private static final Set<WorldBlockKey> INTERNAL_REMOVALS = new HashSet<>();

    private RoyalArmsWallAbility() {
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<SegmentKey, CooldownSegment>> iterator = COOLDOWNS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SegmentKey, CooldownSegment> entry = iterator.next();
            CooldownSegment cooldown = entry.getValue();
            cooldown.ticksRemaining--;
            if (cooldown.ticksRemaining > 0) {
                continue;
            }

            ActiveWall activeWall = ACTIVE_WALLS.get(cooldown.ownerUuid);
            ServerWorld world = server.getWorld(cooldown.worldKey);
            if (activeWall != null && world != null && activeWall.hasPosition(cooldown.pos)) {
                if (!canPlaceAt(world, cooldown.pos)) {
                    cooldown.ticksRemaining = 20;
                    continue;
                }

                placeSegment(world, cooldown.ownerUuid, cooldown.pos, cooldown.reservation.sourceState);
                activeWall.restore(cooldown.pos, cooldown.reservation);
            } else {
                returnReservation(server, cooldown.ownerUuid, cooldown.reservation);
            }
            iterator.remove();
        }
    }

    public static void toggle(ServerPlayerEntity player) {
        LuciiPlayerState state = LuciiPlayerStates.get(player);
        if (state.legacy() != LuciiLegacy.NOCTIS || !state.royalArmsActive()) {
            player.sendMessage(Text.translatable("message.legacyofthelucii.royal_arms.wall.requires_noctis"), true);
            return;
        }

        if (player.getPitch() >= LIFT_LOOK_PITCH) {
            activateLift(player, state);
            return;
        }

        if (ACTIVE_WALLS.containsKey(player.getUuid())) {
            deactivate(player, false);
            return;
        }

        activateWall(player, state);
    }

    public static void deactivate(ServerPlayerEntity player, boolean clearCooldowns) {
        MinecraftServer server = player.getServer();
        ActiveWall activeWall = ACTIVE_WALLS.remove(player.getUuid());
        if (activeWall == null) {
            if (clearCooldowns) {
                returnCooldownReservations(server, player.getUuid());
            }
            return;
        }

        ServerWorld world = server == null ? player.getServerWorld() : server.getWorld(activeWall.worldKey);
        if (world != null) {
            for (BlockPos pos : activeWall.placedPositions) {
                removeSegmentWithoutCooldown(world, pos);
            }
        }
        returnActiveReservations(player, activeWall);
        if (clearCooldowns) {
            returnCooldownReservations(server, player.getUuid());
        }
    }

    public static void clearAll(ServerPlayerEntity player) {
        deactivate(player, true);
    }

    public static void clearAll(MinecraftServer server) {
        List<ServerPlayerEntity> players = List.copyOf(server.getPlayerManager().getPlayerList());
        for (ServerPlayerEntity player : players) {
            clearAll(player);
        }

        for (ActiveWall activeWall : ACTIVE_WALLS.values()) {
            ServerWorld world = server.getWorld(activeWall.worldKey);
            if (world != null) {
                for (BlockPos pos : activeWall.placedPositions) {
                    removeSegmentWithoutCooldown(world, pos);
                }
            }
        }

        ACTIVE_WALLS.clear();
        COOLDOWNS.clear();
        INTERNAL_REMOVALS.clear();
    }

    public static boolean isManagedSegment(ServerWorld world, BlockPos pos, UUID ownerUuid) {
        if (ownerUuid == null) {
            return false;
        }

        ActiveWall activeWall = ACTIVE_WALLS.get(ownerUuid);
        return activeWall != null
                && activeWall.worldKey == world.getRegistryKey()
                && activeWall.placedPositions.contains(pos.toImmutable());
    }

    public static void onSegmentRemoved(ServerWorld world, BlockPos pos, RoyalArmsWallBlockEntity blockEntity) {
        WorldBlockKey worldBlockKey = new WorldBlockKey(world.getRegistryKey(), pos.toImmutable());
        if (INTERNAL_REMOVALS.remove(worldBlockKey)) {
            return;
        }

        UUID ownerUuid = blockEntity.ownerUuid();
        if (ownerUuid == null) {
            return;
        }

        ActiveWall activeWall = ACTIVE_WALLS.get(ownerUuid);
        SegmentReservation reservation = null;
        if (activeWall != null) {
            reservation = activeWall.markBroken(pos);
        }

        if (reservation != null) {
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerUuid);
            if (owner != null) {
                LuciiNetwork.broadcastRoyalArmsWallAnimation(world, owner, pos, reservation.sourceState, false);
            }
            COOLDOWNS.put(
                    new SegmentKey(ownerUuid, world.getRegistryKey(), pos.toImmutable()),
                    new CooldownSegment(ownerUuid, world.getRegistryKey(), pos.toImmutable(), reservation, restoreTicks(world, pos, reservation.sourceState))
            );
        }
    }

    private static void activateWall(ServerPlayerEntity player, LuciiPlayerState state) {
        if (!player.getAbilities().creativeMode && !state.hasMana(WALL_MANA_COST)) {
            player.sendMessage(Text.translatable("message.legacyofthelucii.royal_arms.not_enough_mana"), true);
            return;
        }

        ServerWorld world = player.getServerWorld();
        ActiveWall activeWall = new ActiveWall(world.getRegistryKey());
        List<BlockPos> positions = wallPositions(player);

        for (BlockPos pos : positions) {
            SegmentKey segmentKey = new SegmentKey(player.getUuid(), world.getRegistryKey(), pos);
            activeWall.track(pos);
            if (COOLDOWNS.containsKey(segmentKey) || !canPlaceAt(world, pos)) {
                activeWall.markBroken(pos);
                continue;
            }

            SegmentReservation reservation = reserveNextBlock(player, pos);
            if (reservation == null) {
                activeWall.markBroken(pos);
                continue;
            }

            placeSegment(world, player.getUuid(), pos, reservation.sourceState);
            activeWall.restore(pos, reservation);
        }

        if (activeWall.placedPositions.isEmpty()) {
            returnActiveReservations(player, activeWall);
            return;
        }

        if (!player.getAbilities().creativeMode) {
            state.spendMana(WALL_MANA_COST);
            LuciiNetwork.sendState(player);
        }
        ACTIVE_WALLS.put(player.getUuid(), activeWall);
    }

    private static void activateLift(ServerPlayerEntity player, LuciiPlayerState state) {
        if (!player.getAbilities().creativeMode && !state.hasMana(LIFT_MANA_COST)) {
            player.sendMessage(Text.translatable("message.legacyofthelucii.royal_arms.not_enough_mana"), true);
            return;
        }

        ServerWorld world = player.getServerWorld();
        BlockPos supportPos = player.getBlockPos();
        BlockPos targetFeetPos = supportPos.up();
        BlockPos targetHeadPos = targetFeetPos.up();
        if (!canPlaceAt(world, supportPos) || !canStandAt(world, targetFeetPos) || !canStandAt(world, targetHeadPos)) {
            return;
        }

        SegmentReservation reservation = reserveNextBlock(player, supportPos);
        if (reservation == null) {
            return;
        }

        if (!player.getAbilities().creativeMode) {
            state.spendMana(LIFT_MANA_COST);
            LuciiNetwork.sendState(player);
        }

        placeSegment(world, player.getUuid(), supportPos, reservation.sourceState);
        player.teleport(world, player.getX(), player.getY() + 1.0D, player.getZ(), player.getYaw(), player.getPitch());

        ActiveWall activeWall = ACTIVE_WALLS.computeIfAbsent(player.getUuid(), uuid -> new ActiveWall(world.getRegistryKey()));
        activeWall.track(supportPos);
        activeWall.restore(supportPos, reservation);
    }

    private static List<BlockPos> wallPositions(ServerPlayerEntity player) {
        Direction forward = player.getHorizontalFacing();
        Direction right = forward.rotateYClockwise();
        BlockPos center = targetWallCenter(player, forward);
        List<BlockPos> positions = new ArrayList<>(WALL_WIDTH * WALL_HEIGHT);

        for (int y = 0; y < WALL_HEIGHT; y++) {
            for (int x = -WALL_WIDTH / 2; x <= WALL_WIDTH / 2; x++) {
                positions.add(center.offset(right, x).up(y).toImmutable());
            }
        }

        return positions;
    }

    private static BlockPos targetWallCenter(ServerPlayerEntity player, Direction forward) {
        HitResult hitResult = player.raycast(MAX_CAST_DISTANCE, 0.0F, false);
        if (hitResult instanceof BlockHitResult blockHitResult && hitResult.getType() == HitResult.Type.BLOCK) {
            return blockHitResult.getBlockPos().offset(blockHitResult.getSide()).toImmutable();
        }

        Vec3d target = player.getEyePos().add(player.getRotationVec(0.0F).multiply(MAX_CAST_DISTANCE));
        return new BlockPos(
                MathHelper.floor(target.x),
                MathHelper.floor(target.y),
                MathHelper.floor(target.z)
        ).toImmutable();
    }

    private static SegmentReservation reserveNextBlock(ServerPlayerEntity player, BlockPos targetPos) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.main.size(); slot++) {
            ItemStack stack = inventory.main.get(slot);
            BlockState state = blockStateFromStack(player, targetPos, stack);
            if (state != null) {
                return reserveOne(player, stack, state);
            }
        }

        for (int slot = 0; slot < inventory.offHand.size(); slot++) {
            ItemStack stack = inventory.offHand.get(slot);
            BlockState state = blockStateFromStack(player, targetPos, stack);
            if (state != null) {
                return reserveOne(player, stack, state);
            }
        }

        return null;
    }

    private static SegmentReservation reserveOne(ServerPlayerEntity player, ItemStack stack, BlockState sourceState) {
        if (player.getAbilities().creativeMode) {
            return new SegmentReservation(sourceState, ItemStack.EMPTY);
        }

        ItemStack reservedStack = stack.copyWithCount(1);
        stack.decrement(1);
        player.currentScreenHandler.sendContentUpdates();
        return new SegmentReservation(sourceState, reservedStack);
    }

    private static BlockState blockStateFromStack(ServerPlayerEntity player, BlockPos targetPos, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }

        BlockState state = blockItem.getBlock().getDefaultState();
        if (state.isAir()
                || state.isOf(Blocks.BEDROCK)
                || state.getHardness(player.getServerWorld(), targetPos) < 0.0F
                || state.getCollisionShape(player.getServerWorld(), targetPos).isEmpty()) {
            return null;
        }
        return state;
    }

    private static boolean canPlaceAt(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isAir() || state.isReplaceable();
    }

    private static boolean canStandAt(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    private static void placeSegment(ServerWorld world, UUID ownerUuid, BlockPos pos, BlockState sourceState) {
        BlockState wallState = LegacyBlocks.ROYAL_ARMS_WALL_BLOCK.getDefaultState();
        world.setBlockState(pos, wallState, Block.NOTIFY_ALL);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RoyalArmsWallBlockEntity wallBlockEntity) {
            wallBlockEntity.configure(ownerUuid, sourceState);
            world.updateListeners(pos, wallState, wallState, Block.NOTIFY_ALL);
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerUuid);
            if (owner != null) {
                LuciiNetwork.broadcastRoyalArmsWallAnimation(world, owner, pos, sourceState, true);
            }
        }
    }

    private static void removeSegmentWithoutCooldown(ServerWorld world, BlockPos pos) {
        if (!world.getBlockState(pos).isOf(LegacyBlocks.ROYAL_ARMS_WALL_BLOCK)) {
            return;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RoyalArmsWallBlockEntity wallBlockEntity && wallBlockEntity.ownerUuid() != null) {
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(wallBlockEntity.ownerUuid());
            if (owner != null) {
                LuciiNetwork.broadcastRoyalArmsWallAnimation(world, owner, pos, wallBlockEntity.sourceState(), false);
            }
        }
        INTERNAL_REMOVALS.add(new WorldBlockKey(world.getRegistryKey(), pos.toImmutable()));
        world.removeBlock(pos, false);
    }

    private static int restoreTicks(ServerWorld world, BlockPos pos, BlockState sourceState) {
        float hardness = sourceState.getHardness(world, pos);
        if (hardness < 0.0F) {
            return MAX_RESTORE_TICKS;
        }
        return Math.min(MAX_RESTORE_TICKS, Math.max(MIN_RESTORE_TICKS, Math.round(hardness * RESTORE_TICKS_PER_HARDNESS)));
    }

    private static void returnActiveReservations(ServerPlayerEntity player, ActiveWall activeWall) {
        for (SegmentReservation reservation : activeWall.reservations.values()) {
            returnReservation(player, reservation);
        }
        activeWall.reservations.clear();
    }

    private static void returnCooldownReservations(MinecraftServer server, UUID ownerUuid) {
        if (server == null) {
            COOLDOWNS.keySet().removeIf(key -> key.ownerUuid.equals(ownerUuid));
            return;
        }

        Iterator<Map.Entry<SegmentKey, CooldownSegment>> iterator = COOLDOWNS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SegmentKey, CooldownSegment> entry = iterator.next();
            if (entry.getKey().ownerUuid.equals(ownerUuid)) {
                returnReservation(server, ownerUuid, entry.getValue().reservation);
                iterator.remove();
            }
        }
    }

    private static void returnReservation(MinecraftServer server, UUID ownerUuid, SegmentReservation reservation) {
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerUuid);
        if (owner != null) {
            returnReservation(owner, reservation);
        }
    }

    private static void returnReservation(ServerPlayerEntity player, SegmentReservation reservation) {
        if (reservation.reservedStack.isEmpty()) {
            return;
        }

        player.getInventory().offerOrDrop(reservation.reservedStack.copy());
        player.currentScreenHandler.sendContentUpdates();
    }

    private record SegmentKey(UUID ownerUuid, RegistryKey<World> worldKey, BlockPos pos) {
    }

    private record WorldBlockKey(RegistryKey<World> worldKey, BlockPos pos) {
    }

    private static final class CooldownSegment {
        private final UUID ownerUuid;
        private final RegistryKey<World> worldKey;
        private final BlockPos pos;
        private final SegmentReservation reservation;
        private int ticksRemaining;

        private CooldownSegment(UUID ownerUuid, RegistryKey<World> worldKey, BlockPos pos, SegmentReservation reservation, int ticksRemaining) {
            this.ownerUuid = ownerUuid;
            this.worldKey = worldKey;
            this.pos = pos;
            this.reservation = reservation;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private record SegmentReservation(BlockState sourceState, ItemStack reservedStack) {
    }

    private static final class ActiveWall {
        private final RegistryKey<World> worldKey;
        private final Set<BlockPos> trackedPositions = new HashSet<>();
        private final Set<BlockPos> placedPositions = new HashSet<>();
        private final Map<BlockPos, SegmentReservation> reservations = new HashMap<>();

        private ActiveWall(RegistryKey<World> worldKey) {
            this.worldKey = worldKey;
        }

        private void track(BlockPos pos) {
            trackedPositions.add(pos.toImmutable());
        }

        private boolean hasPosition(BlockPos pos) {
            return trackedPositions.contains(pos);
        }

        private void restore(BlockPos pos, SegmentReservation reservation) {
            BlockPos immutablePos = pos.toImmutable();
            placedPositions.add(immutablePos);
            reservations.put(immutablePos, reservation);
        }

        private SegmentReservation markBroken(BlockPos pos) {
            BlockPos immutablePos = pos.toImmutable();
            placedPositions.remove(immutablePos);
            return reservations.remove(immutablePos);
        }
    }
}
