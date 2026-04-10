package dev.bekololek.crossthefloor.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

public class Arena {

    private String name;
    private Difficulty difficulty;
    private String worldName;

    // Corner of the tiled path nearest to the start platform (min-x corner for N/S, min-z corner for E/W)
    private int cornerX, cornerY, cornerZ;
    private BlockFace direction; // NORTH, SOUTH, EAST, WEST
    private int pathLength; // in blocks (default 60)

    // Per-placement rewards (index 0 = 1st place, 1 = 2nd place, ...)
    private List<Double> placementMoney = new ArrayList<>();
    private List<List<String>> placementCommands = new ArrayList<>();

    // Sign locations referencing this arena
    private final List<int[]> signLocations = new ArrayList<>(); // [world-hash, x, y, z]

    private static final int PATH_WIDTH = 12;
    private static final int START_PLATFORM_DEPTH = 4;
    private static final int END_PLATFORM_DEPTH = 4;

    public Arena(String name, Difficulty difficulty, String worldName,
                 int cornerX, int cornerY, int cornerZ,
                 BlockFace direction, int pathLength) {
        this.name = name;
        this.difficulty = difficulty;
        this.worldName = worldName;
        this.cornerX = cornerX;
        this.cornerY = cornerY;
        this.cornerZ = cornerZ;
        this.direction = direction;
        this.pathLength = pathLength;
    }

    // ── Computed locations ────────────────────────────────────────────────────

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    /**
     * Lobby/start spawn — center of the start platform, facing the path direction.
     */
    public Location getLobbySpawn() {
        float yaw = directionToYaw();
        double cx, cz;
        switch (direction) {
            case NORTH -> { cx = cornerX + PATH_WIDTH / 2.0; cz = cornerZ + START_PLATFORM_DEPTH / 2.0; }
            case SOUTH -> { cx = cornerX + PATH_WIDTH / 2.0; cz = cornerZ - START_PLATFORM_DEPTH / 2.0; }
            case EAST  -> { cz = cornerZ + PATH_WIDTH / 2.0; cx = cornerX - START_PLATFORM_DEPTH / 2.0; }
            case WEST  -> { cz = cornerZ + PATH_WIDTH / 2.0; cx = cornerX + START_PLATFORM_DEPTH / 2.0; }
            default    -> { cx = cornerX; cz = cornerZ; }
        }
        return new Location(getWorld(), cx, cornerY + 1, cz, yaw, 0);
    }

    /**
     * Start spawn — front edge of the start platform (solid ground), facing the path.
     */
    public Location getStartSpawn() {
        float yaw = directionToYaw();
        double cx, cz;
        switch (direction) {
            case NORTH -> { cx = cornerX + PATH_WIDTH / 2.0; cz = cornerZ + 1.5; }
            case SOUTH -> { cx = cornerX + PATH_WIDTH / 2.0; cz = cornerZ - 1.5; }
            case EAST  -> { cz = cornerZ + PATH_WIDTH / 2.0; cx = cornerX - 1.5; }
            case WEST  -> { cz = cornerZ + PATH_WIDTH / 2.0; cx = cornerX + 1.5; }
            default    -> { cx = cornerX; cz = cornerZ; }
        }
        return new Location(getWorld(), cx, cornerY + 1, cz, yaw, 0);
    }

    /**
     * Returns the path bounding box: [minX, minY, minZ, maxX, maxY, maxZ]
     */
    public int[] getPathBounds() {
        int minX, minZ, maxX, maxZ;
        switch (direction) {
            case NORTH -> {
                minX = cornerX; maxX = cornerX + PATH_WIDTH - 1;
                minZ = cornerZ - pathLength + 1; maxZ = cornerZ;
            }
            case SOUTH -> {
                minX = cornerX; maxX = cornerX + PATH_WIDTH - 1;
                minZ = cornerZ; maxZ = cornerZ + pathLength - 1;
            }
            case EAST -> {
                minZ = cornerZ; maxZ = cornerZ + PATH_WIDTH - 1;
                minX = cornerX; maxX = cornerX + pathLength - 1;
            }
            case WEST -> {
                minZ = cornerZ; maxZ = cornerZ + PATH_WIDTH - 1;
                minX = cornerX - pathLength + 1; maxX = cornerX;
            }
            default -> { minX = cornerX; minZ = cornerZ; maxX = cornerX; maxZ = cornerZ; }
        }
        return new int[]{minX, cornerY, minZ, maxX, cornerY, maxZ};
    }

    /**
     * Returns the start platform bounds: [minX, minY, minZ, maxX, maxY, maxZ]
     */
    public int[] getStartPlatformBounds() {
        int minX, minZ, maxX, maxZ;
        switch (direction) {
            case NORTH -> {
                minX = cornerX; maxX = cornerX + PATH_WIDTH - 1;
                minZ = cornerZ + 1; maxZ = cornerZ + START_PLATFORM_DEPTH;
            }
            case SOUTH -> {
                minX = cornerX; maxX = cornerX + PATH_WIDTH - 1;
                minZ = cornerZ - START_PLATFORM_DEPTH; maxZ = cornerZ - 1;
            }
            case EAST -> {
                minZ = cornerZ; maxZ = cornerZ + PATH_WIDTH - 1;
                minX = cornerX - START_PLATFORM_DEPTH; maxX = cornerX - 1;
            }
            case WEST -> {
                minZ = cornerZ; maxZ = cornerZ + PATH_WIDTH - 1;
                minX = cornerX + 1; maxX = cornerX + START_PLATFORM_DEPTH;
            }
            default -> { minX = cornerX; minZ = cornerZ; maxX = cornerX; maxZ = cornerZ; }
        }
        return new int[]{minX, cornerY, minZ, maxX, cornerY, maxZ};
    }

    /**
     * Returns the end platform bounds: [minX, minY, minZ, maxX, maxY, maxZ]
     */
    public int[] getEndPlatformBounds() {
        int[] path = getPathBounds();
        int minX, minZ, maxX, maxZ;
        switch (direction) {
            case NORTH -> {
                minX = cornerX; maxX = cornerX + PATH_WIDTH - 1;
                maxZ = path[2] - 1; minZ = maxZ - END_PLATFORM_DEPTH + 1;
            }
            case SOUTH -> {
                minX = cornerX; maxX = cornerX + PATH_WIDTH - 1;
                minZ = path[5] + 1; maxZ = minZ + END_PLATFORM_DEPTH - 1;
            }
            case EAST -> {
                minZ = cornerZ; maxZ = cornerZ + PATH_WIDTH - 1;
                minX = path[3] + 1; maxX = minX + END_PLATFORM_DEPTH - 1;
            }
            case WEST -> {
                minZ = cornerZ; maxZ = cornerZ + PATH_WIDTH - 1;
                maxX = path[0] - 1; minX = maxX - END_PLATFORM_DEPTH + 1;
            }
            default -> { minX = cornerX; minZ = cornerZ; maxX = cornerX; maxZ = cornerZ; }
        }
        return new int[]{minX, cornerY, minZ, maxX, cornerY, maxZ};
    }

    /**
     * Full arena bounding box (start platform + path + end platform):
     * [minX, minY, minZ, maxX, maxY, maxZ]
     */
    public int[] getFullBounds() {
        int[] s = getStartPlatformBounds();
        int[] e = getEndPlatformBounds();
        return new int[]{
                Math.min(s[0], e[0]), cornerY, Math.min(s[2], e[2]),
                Math.max(s[3], e[3]), cornerY, Math.max(s[5], e[5])
        };
    }

    /**
     * Check if a location is past the finish line.
     */
    public boolean isPastFinish(Location loc) {
        return switch (direction) {
            case NORTH -> loc.getBlockZ() < getPathBounds()[2];
            case SOUTH -> loc.getBlockZ() > getPathBounds()[5];
            case EAST  -> loc.getBlockX() > getPathBounds()[3];
            case WEST  -> loc.getBlockX() < getPathBounds()[0];
            default -> false;
        };
    }

    /**
     * Check if a location is within the path area (XZ only, any Y).
     */
    public boolean isInPathArea(Location loc) {
        int[] b = getPathBounds();
        int x = loc.getBlockX(), z = loc.getBlockZ();
        return x >= b[0] && x <= b[3] && z >= b[2] && z <= b[5];
    }

    /**
     * Check if a location is within the full arena bounds (XZ, any Y).
     */
    public boolean isInArenaBounds(Location loc) {
        int[] b = getFullBounds();
        int x = loc.getBlockX(), z = loc.getBlockZ();
        return x >= b[0] && x <= b[3] && z >= b[2] && z <= b[5];
    }

    /**
     * Number of tile rows (each row is 2 blocks in the length direction).
     */
    public int getTileRows() {
        return pathLength / 2;
    }

    /**
     * Number of tile columns (each column is 2 blocks wide).
     */
    public int getTileCols() {
        return PATH_WIDTH / 2;
    }

    private float directionToYaw() {
        return switch (direction) {
            case NORTH -> 180f;
            case SOUTH -> 0f;
            case EAST -> -90f;
            case WEST -> 90f;
            default -> 0f;
        };
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getName() { return name; }
    public Difficulty getDifficulty() { return difficulty; }
    public String getWorldName() { return worldName; }
    public int getCornerX() { return cornerX; }
    public int getCornerY() { return cornerY; }
    public int getCornerZ() { return cornerZ; }
    public BlockFace getDirection() { return direction; }
    public int getPathLength() { return pathLength; }
    public List<Double> getPlacementMoney() { return placementMoney; }
    public void setPlacementMoney(List<Double> placementMoney) { this.placementMoney = placementMoney; }
    public List<List<String>> getPlacementCommands() { return placementCommands; }
    public void setPlacementCommands(List<List<String>> placementCommands) { this.placementCommands = placementCommands; }
    public List<int[]> getSignLocations() { return signLocations; }

    public static int getPathWidth() { return PATH_WIDTH; }
    public static int getStartPlatformDepth() { return START_PLATFORM_DEPTH; }
    public static int getEndPlatformDepth() { return END_PLATFORM_DEPTH; }
}
