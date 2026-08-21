package ru.siyoga.legacyofthelucii.pointwarp;

import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Finds high, exposed and occupiable surfaces in the existing world geometry. */
public final class NoctisWarpPointFinder {
    public static final double MAX_RANGE = 48.0D;
    public static final int MAX_POINTS = 12;
    private static final int SAMPLE_STEP = 2;
    private static final double MIN_HEIGHT_ABOVE_PLAYER = 2.5D;
    private static final double MIN_LOCAL_RISE = 1.5D;
    private static final double MIN_POINT_SEPARATION = 3.5D;

    private NoctisWarpPointFinder() {
    }

    public static List<WarpPoint> find(World world, Vec3d origin) {
        List<WarpPoint> candidates = new ArrayList<>();
        int centerX = (int) Math.floor(origin.x);
        int centerZ = (int) Math.floor(origin.z);
        int radius = (int) MAX_RANGE;

        for (int x = centerX - radius; x <= centerX + radius; x += SAMPLE_STEP) {
            for (int z = centerZ - radius; z <= centerZ + radius; z += SAMPLE_STEP) {
                double dx = x + 0.5D - origin.x;
                double dz = z + 0.5D - origin.z;
                if (dx * dx + dz * dz > MAX_RANGE * MAX_RANGE) {
                    continue;
                }
                BlockPos surface = surfaceBlock(world, x, z);
                if (surface == null) {
                    continue;
                }
                WarpPoint point = evaluate(world, origin, surface, true);
                if (point != null) {
                    candidates.add(point);
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(WarpPoint::score).reversed());
        List<WarpPoint> result = new ArrayList<>();
        for (WarpPoint candidate : candidates) {
            boolean tooClose = result.stream().anyMatch(existing ->
                    existing.landingPos().squaredDistanceTo(candidate.landingPos())
                            < MIN_POINT_SEPARATION * MIN_POINT_SEPARATION);
            if (tooClose) {
                continue;
            }
            result.add(candidate);
            if (result.size() >= MAX_POINTS) {
                break;
            }
        }
        return result;
    }

    public static WarpPoint resolve(World world, Vec3d origin, BlockPos expected) {
        if (expected == null) {
            return null;
        }
        return evaluate(world, origin, expected, true);
    }

    private static WarpPoint evaluate(World world, Vec3d origin, BlockPos surface, boolean requireVisibility) {
        BlockState state = world.getBlockState(surface);
        VoxelShape shape = state.getCollisionShape(world, surface);
        if (shape.isEmpty()) {
            return null;
        }

        double topY = surface.getY() + shape.getBoundingBox().maxY;
        Vec3d landing = new Vec3d(surface.getX() + 0.5D, topY + 0.02D, surface.getZ() + 0.5D);
        double distanceSquared = origin.squaredDistanceTo(landing);
        if (distanceSquared > MAX_RANGE * MAX_RANGE
                || topY < origin.y + MIN_HEIGHT_ABOVE_PLAYER
                || !hasFreeSpace(world, landing)) {
            return null;
        }

        double averageNeighbourHeight = 0.0D;
        int openSides = 0;
        int neighbours = 0;
        for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            double neighbourHeight = surfaceHeight(world, surface.getX() + direction[0], surface.getZ() + direction[1]);
            if (neighbourHeight < 0.0D) {
                continue;
            }
            averageNeighbourHeight += neighbourHeight;
            neighbours++;
            if (neighbourHeight <= topY - 1.5D) {
                openSides++;
            }
        }
        if (neighbours == 0) {
            return null;
        }
        averageNeighbourHeight /= neighbours;
        double localRise = topY - averageNeighbourHeight;
        if (localRise < MIN_LOCAL_RISE && openSides == 0) {
            return null;
        }

        Vec3d marker = landing.add(0.0D, 0.30D, 0.0D);
        // The marker is rendered above the surface. Raycast just inside the top face;
        // aiming at the floating marker itself can otherwise pass over a flat roof.
        Vec3d visibilityPoint = landing.add(0.0D, -0.03D, 0.0D);
        if (requireVisibility && !canSee(world, origin.add(0.0D, 1.62D, 0.0D), visibilityPoint, surface)) {
            return null;
        }

        double heightScore = topY - origin.y;
        double distancePenalty = Math.sqrt(distanceSquared) * 0.025D;
        double score = heightScore + localRise * 2.0D + openSides * 0.75D - distancePenalty;
        return new WarpPoint(surface.toImmutable(), marker, landing, score);
    }

    private static BlockPos surfaceBlock(World world, int x, int z) {
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (topY <= world.getBottomY()) {
            return null;
        }
        BlockPos candidate = new BlockPos(x, topY - 1, z);
        return world.getBlockState(candidate).getCollisionShape(world, candidate).isEmpty()
                ? null
                : candidate;
    }

    private static double surfaceHeight(World world, int x, int z) {
        BlockPos surface = surfaceBlock(world, x, z);
        if (surface == null) {
            return -1.0D;
        }
        VoxelShape shape = world.getBlockState(surface).getCollisionShape(world, surface);
        return surface.getY() + shape.getBoundingBox().maxY;
    }

    private static boolean hasFreeSpace(World world, Vec3d landing) {
        Box playerBox = new Box(
                landing.x - 0.30D, landing.y, landing.z - 0.30D,
                landing.x + 0.30D, landing.y + 1.80D, landing.z + 0.30D
        );
        return world.isSpaceEmpty(playerBox);
    }

    private static boolean canSee(World world, Vec3d eye, Vec3d marker, BlockPos expected) {
        HitResult hit = world.raycast(new RaycastContext(
                eye,
                marker,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                null
        ));
        return hit.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult) hit).getBlockPos().equals(expected);
    }

    public record WarpPoint(BlockPos blockPos, Vec3d markerPos, Vec3d landingPos, double score) {
    }
}
