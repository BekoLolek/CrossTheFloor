package dev.bekololek.crossthefloor.managers;

import com.terranova.versionadapter.api.Materials;
import dev.bekololek.crossthefloor.Main;
import dev.bekololek.crossthefloor.models.Arena;
import dev.bekololek.crossthefloor.models.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class ArenaManager {

    private final Main plugin;
    private final File arenasFile;
    private final Map<String, Arena> arenas = new HashMap<>();

    // Pending arena creations: player UUID -> pending data
    private final Map<UUID, PendingArena> pendingCreations = new HashMap<>();
    private final Map<UUID, BukkitTask> outlineTasks = new HashMap<>();

    public record PendingArena(String name, Difficulty difficulty, String worldName,
                               int cornerX, int cornerY, int cornerZ,
                               BlockFace direction, int pathLength) {}

    public ArenaManager(Main plugin) {
        this.plugin = plugin;
        this.arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
    }

    // ── Load / Save ──────────────────────────────────────────────────────────

    public void load() {
        arenas.clear();
        if (!arenasFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arenasFile);
        ConfigurationSection sec = yaml.getConfigurationSection("arenas");
        if (sec == null) return;

        for (String name : sec.getKeys(false)) {
            ConfigurationSection a = sec.getConfigurationSection(name);
            if (a == null) continue;

            Difficulty diff = Difficulty.fromString(a.getString("difficulty", "easy"));
            if (diff == null) diff = Difficulty.EASY;

            Arena arena = new Arena(name, diff,
                    a.getString("world", "world"),
                    a.getInt("corner-x"), a.getInt("corner-y"), a.getInt("corner-z"),
                    BlockFace.valueOf(a.getString("direction", "NORTH")),
                    a.getInt("path-length", plugin.getConfig().getInt("default-path-length", 60)));

            arena.setRewardMoney(a.getDouble("rewards.money",
                    plugin.getConfig().getDouble("default-rewards.money", 0)));
            arena.setRewardCommands(a.getStringList("rewards.commands"));
            if (arena.getRewardCommands().isEmpty()) {
                arena.setRewardCommands(new ArrayList<>(
                        plugin.getConfig().getStringList("default-rewards.commands")));
            }

            ConfigurationSection signsSec = a.getConfigurationSection("signs");
            if (signsSec != null) {
                for (String key : signsSec.getKeys(false)) {
                    ConfigurationSection s = signsSec.getConfigurationSection(key);
                    if (s != null) {
                        arena.getSignLocations().add(new int[]{
                                s.getString("world", "world").hashCode(),
                                s.getInt("x"), s.getInt("y"), s.getInt("z")
                        });
                    }
                }
            }

            arenas.put(name.toLowerCase(), arena);
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arenas.");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            String base = "arenas." + arena.getName();
            yaml.set(base + ".difficulty", arena.getDifficulty().getKey());
            yaml.set(base + ".world", arena.getWorldName());
            yaml.set(base + ".corner-x", arena.getCornerX());
            yaml.set(base + ".corner-y", arena.getCornerY());
            yaml.set(base + ".corner-z", arena.getCornerZ());
            yaml.set(base + ".direction", arena.getDirection().name());
            yaml.set(base + ".path-length", arena.getPathLength());
            yaml.set(base + ".rewards.money", arena.getRewardMoney());
            yaml.set(base + ".rewards.commands", arena.getRewardCommands());

            int i = 0;
            for (int[] sign : arena.getSignLocations()) {
                yaml.set(base + ".signs." + i + ".world", arena.getWorldName());
                yaml.set(base + ".signs." + i + ".x", sign[1]);
                yaml.set(base + ".signs." + i + ".y", sign[2]);
                yaml.set(base + ".signs." + i + ".z", sign[3]);
                i++;
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    yaml.save(arenasFile);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save arenas.yml", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    // ── Arena CRUD ───────────────────────────────────────────────────────────

    public Arena getArena(String name) {
        return arenas.get(name.toLowerCase());
    }

    public Collection<Arena> getAllArenas() {
        return arenas.values();
    }

    public boolean arenaExists(String name) {
        return arenas.containsKey(name.toLowerCase());
    }

    public void deleteArena(String name) {
        Arena arena = arenas.remove(name.toLowerCase());
        if (arena != null) {
            clearArenaBlocks(arena);
            save();
        }
    }

    // ── Arena Creation Flow ──────────────────────────────────────────────────

    public void startCreation(Player player, String name, Difficulty difficulty) {
        BlockFace facing = getCardinalDirection(player);
        int pathLength = plugin.getConfig().getInt("default-path-length", 60);

        // Calculate corner based on player position and facing direction
        int px = player.getLocation().getBlockX();
        int py = player.getLocation().getBlockY();
        int pz = player.getLocation().getBlockZ();
        int halfWidth = Arena.getPathWidth() / 2;

        int cx, cy, cz;
        cy = py;

        switch (facing) {
            case NORTH -> {
                cx = px - halfWidth;
                cz = pz - Arena.getStartPlatformDepth() - 1;
            }
            case SOUTH -> {
                cx = px - halfWidth;
                cz = pz + Arena.getStartPlatformDepth() + 1;
            }
            case EAST -> {
                cx = px + Arena.getStartPlatformDepth() + 1;
                cz = pz - halfWidth;
            }
            case WEST -> {
                cx = px - Arena.getStartPlatformDepth() - pathLength;
                cz = pz - halfWidth;
            }
            default -> {
                cx = px; cz = pz;
            }
        }

        PendingArena pending = new PendingArena(name, difficulty,
                player.getWorld().getName(), cx, cy, cz, facing, pathLength);
        pendingCreations.put(player.getUniqueId(), pending);

        // Start outline particles
        startOutlineParticles(player, pending);
    }

    public boolean confirmCreation(Player player) {
        PendingArena pending = pendingCreations.remove(player.getUniqueId());
        if (pending == null) return false;

        stopOutlineParticles(player);

        Arena arena = new Arena(pending.name(), pending.difficulty(),
                pending.worldName(), pending.cornerX(), pending.cornerY(), pending.cornerZ(),
                pending.direction(), pending.pathLength());

        // Apply default rewards
        arena.setRewardMoney(plugin.getConfig().getDouble("default-rewards.money", 0));
        arena.setRewardCommands(new ArrayList<>(plugin.getConfig().getStringList("default-rewards.commands")));

        arenas.put(arena.getName().toLowerCase(), arena);
        buildArenaStructure(arena);
        save();
        return true;
    }

    public boolean cancelCreation(Player player) {
        PendingArena pending = pendingCreations.remove(player.getUniqueId());
        if (pending == null) return false;
        stopOutlineParticles(player);
        return true;
    }

    public boolean hasPending(Player player) {
        return pendingCreations.containsKey(player.getUniqueId());
    }

    public PendingArena getPending(Player player) {
        return pendingCreations.get(player.getUniqueId());
    }

    // ── Arena Building ───────────────────────────────────────────────────────

    public void buildArenaStructure(Arena arena) {
        World world = arena.getWorld();
        if (world == null) return;

        Material platformBlock = Materials.get("STONE_BRICKS");
        if (platformBlock == null) platformBlock = Material.STONE;
        Material glass = Materials.get("GLASS");
        if (glass == null) glass = Material.GLASS;

        int clearAbove = plugin.getConfig().getInt("clear-height-above", 5);
        int clearBelow = plugin.getConfig().getInt("clear-depth-below", 10);

        // Get all bounds
        int[] fullBounds = arena.getFullBounds();
        int minX = fullBounds[0], minZ = fullBounds[2];
        int maxX = fullBounds[3], maxZ = fullBounds[5];
        int y = arena.getCornerY();

        // Clear volume: below + path level + above
        for (int bx = minX - 1; bx <= maxX + 1; bx++) {
            for (int bz = minZ - 1; bz <= maxZ + 1; bz++) {
                for (int by = y - clearBelow; by <= y + clearAbove; by++) {
                    world.getBlockAt(bx, by, bz).setType(Material.AIR, false);
                }
            }
        }

        // Build start platform
        int[] startBounds = arena.getStartPlatformBounds();
        fillArea(world, startBounds, platformBlock);

        // Build end platform
        int[] endBounds = arena.getEndPlatformBounds();
        fillArea(world, endBounds, platformBlock);

        // Glass walls along the sides of the path + platforms
        buildWalls(world, arena, glass, clearAbove);

        // Barrier floor below to catch falls
        Material barrier = Materials.get("BARRIER");
        if (barrier == null) barrier = Material.BEDROCK;
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                world.getBlockAt(bx, y - clearBelow, bz).setType(barrier, false);
            }
        }
    }

    private void fillArea(World world, int[] bounds, Material mat) {
        for (int x = bounds[0]; x <= bounds[3]; x++) {
            for (int z = bounds[2]; z <= bounds[5]; z++) {
                world.getBlockAt(x, bounds[1], z).setType(mat, false);
            }
        }
    }

    private void buildWalls(World world, Arena arena, Material glass, int height) {
        int[] full = arena.getFullBounds();
        int y = arena.getCornerY();

        // Walls along the "width" edges (perpendicular to path direction)
        for (int h = 1; h <= height; h++) {
            switch (arena.getDirection()) {
                case NORTH, SOUTH -> {
                    for (int z = full[2]; z <= full[5]; z++) {
                        world.getBlockAt(full[0] - 1, y + h, z).setType(glass, false);
                        world.getBlockAt(full[3] + 1, y + h, z).setType(glass, false);
                    }
                }
                case EAST, WEST -> {
                    for (int x = full[0]; x <= full[3]; x++) {
                        world.getBlockAt(x, y + h, full[2] - 1).setType(glass, false);
                        world.getBlockAt(x, y + h, full[5] + 1).setType(glass, false);
                    }
                }
            }
        }
    }

    private void clearArenaBlocks(Arena arena) {
        World world = arena.getWorld();
        if (world == null) return;

        int[] full = arena.getFullBounds();
        int clearAbove = plugin.getConfig().getInt("clear-height-above", 5);
        int clearBelow = plugin.getConfig().getInt("clear-depth-below", 10);
        int y = arena.getCornerY();

        for (int bx = full[0] - 1; bx <= full[3] + 1; bx++) {
            for (int bz = full[2] - 1; bz <= full[5] + 1; bz++) {
                for (int by = y - clearBelow; by <= y + clearAbove; by++) {
                    world.getBlockAt(bx, by, bz).setType(Material.AIR, false);
                }
            }
        }
    }

    // ── Outline Particles ────────────────────────────────────────────────────

    private void startOutlineParticles(Player player, PendingArena pending) {
        Arena tempArena = new Arena(pending.name(), pending.difficulty(),
                pending.worldName(), pending.cornerX(), pending.cornerY(), pending.cornerZ(),
                pending.direction(), pending.pathLength());

        int[] full = tempArena.getFullBounds();
        int y = pending.cornerY();
        World world = player.getWorld();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                // Draw corner pillars and edge lines with dust particles
                org.bukkit.Particle particle = org.bukkit.Particle.FLAME;
                double step = 1.0;

                // Bottom edges
                for (double x = full[0]; x <= full[3]; x += step) {
                    world.spawnParticle(particle, x, y + 0.5, full[2], 1, 0, 0, 0, 0);
                    world.spawnParticle(particle, x, y + 0.5, full[5] + 1, 1, 0, 0, 0, 0);
                }
                for (double z = full[2]; z <= full[5]; z += step) {
                    world.spawnParticle(particle, full[0], y + 0.5, z, 1, 0, 0, 0, 0);
                    world.spawnParticle(particle, full[3] + 1, y + 0.5, z, 1, 0, 0, 0, 0);
                }
                // Corner pillars
                for (int h = 0; h <= 3; h++) {
                    world.spawnParticle(particle, full[0], y + h, full[2], 1, 0, 0, 0, 0);
                    world.spawnParticle(particle, full[3] + 1, y + h, full[2], 1, 0, 0, 0, 0);
                    world.spawnParticle(particle, full[0], y + h, full[5] + 1, 1, 0, 0, 0, 0);
                    world.spawnParticle(particle, full[3] + 1, y + h, full[5] + 1, 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);

        outlineTasks.put(player.getUniqueId(), task);
    }

    private void stopOutlineParticles(Player player) {
        BukkitTask task = outlineTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    // ── Sign Management ──────────────────────────────────────────────────────

    public void addSign(Arena arena, Location signLoc) {
        arena.getSignLocations().add(new int[]{
                signLoc.getWorld().getName().hashCode(),
                signLoc.getBlockX(), signLoc.getBlockY(), signLoc.getBlockZ()
        });
        save();
    }

    public Arena getArenaBySign(Location loc) {
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        for (Arena arena : arenas.values()) {
            for (int[] sign : arena.getSignLocations()) {
                if (sign[1] == x && sign[2] == y && sign[3] == z) {
                    return arena;
                }
            }
        }
        return null;
    }

    public void updateSigns(Arena arena, String status, int playerCount) {
        World world = arena.getWorld();
        if (world == null) return;

        for (int[] sign : arena.getSignLocations()) {
            Block block = world.getBlockAt(sign[1], sign[2], sign[3]);
            if (block.getState() instanceof Sign signState) {
                var side = signState.getSide(Side.FRONT);
                side.line(0, MessageUtil_colorize("&1[CTF]"));
                side.line(1, MessageUtil_colorize("&0" + arena.getName()));
                side.line(2, MessageUtil_colorize("&2" + capitalize(arena.getDifficulty().getKey())));
                side.line(3, MessageUtil_colorize("&5" + status + " &8| &5" + playerCount + " players"));
                signState.update();
            }
        }
    }

    private net.kyori.adventure.text.Component MessageUtil_colorize(String s) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private BlockFace getCardinalDirection(Player player) {
        float yaw = player.getLocation().getYaw();
        yaw = ((yaw % 360) + 360) % 360;
        if (yaw >= 315 || yaw < 45) return BlockFace.SOUTH;
        if (yaw >= 45 && yaw < 135) return BlockFace.WEST;
        if (yaw >= 135 && yaw < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }
}
