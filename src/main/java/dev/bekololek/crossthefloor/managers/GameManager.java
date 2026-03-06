package dev.bekololek.crossthefloor.managers;

import com.terranova.versionadapter.api.Materials;
import com.terranova.versionadapter.api.Sounds;
import dev.bekololek.crossthefloor.Main;
import dev.bekololek.crossthefloor.models.Arena;
import dev.bekololek.crossthefloor.models.Difficulty;
import dev.bekololek.crossthefloor.models.GameSession;
import dev.bekololek.crossthefloor.models.PlayerState;
import dev.bekololek.crossthefloor.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class GameManager {

    private final Main plugin;
    private final ArenaManager arenaManager;
    private final StatsManager statsManager;
    private final RewardManager rewardManager;

    private final Map<String, GameSession> activeSessions = new HashMap<>(); // arenaName (lowercase) -> session
    private final Map<UUID, String> playerArenas = new HashMap<>(); // player UUID -> arena name (lowercase)

    private final Random random = new Random();

    public GameManager(Main plugin, ArenaManager arenaManager,
                       StatsManager statsManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.statsManager = statsManager;
        this.rewardManager = rewardManager;
    }

    // ── Join / Leave ─────────────────────────────────────────────────────────

    public boolean joinArena(Player player, Arena arena) {
        String key = arena.getName().toLowerCase();
        String prefix = plugin.getConfig().getString("prefix", "&6[CTF] &r");

        if (playerArenas.containsKey(player.getUniqueId())) {
            MessageUtil.send(player, prefix, "&cYou are already in a game!");
            return false;
        }

        GameSession session = activeSessions.get(key);
        if (session != null && session.getState() != GameSession.State.WAITING) {
            MessageUtil.send(player, prefix, "&cThis game is already in progress! Wait for it to finish.");
            return false;
        }

        // Create session if needed
        if (session == null) {
            session = new GameSession(arena);
            activeSessions.put(key, session);
        }

        // Save state and prepare player
        session.getSavedStates().put(player.getUniqueId(), new PlayerState(player));
        session.getPlayers().add(player.getUniqueId());
        playerArenas.put(player.getUniqueId(), key);

        preparePlayer(player, arena);
        broadcastToSession(session, prefix + "&e" + player.getName() + " &7joined the game! &8(" +
                session.getPlayers().size() + " players)");
        arenaManager.updateSigns(arena, "Waiting", session.getPlayers().size());

        // Start ready check if not already running
        if (session.getReadyCheckTask() == null) {
            startReadyCheck(session);
        }

        return true;
    }

    public void leaveArena(Player player) {
        String key = playerArenas.remove(player.getUniqueId());
        if (key == null) return;

        GameSession session = activeSessions.get(key);
        if (session == null) return;

        String prefix = plugin.getConfig().getString("prefix", "&6[CTF] &r");

        // Restore player
        PlayerState state = session.getSavedStates().remove(player.getUniqueId());
        if (state != null) state.restore(player);

        session.getPlayers().remove(player.getUniqueId());
        session.getReadyPlayers().remove(player.getUniqueId());
        session.getReadyWarnings().remove(player.getUniqueId());

        broadcastToSession(session, prefix + "&e" + player.getName() + " &7left the game.");

        // Check if game should end
        if (session.getState() == GameSession.State.PLAYING || session.getState() == GameSession.State.COUNTDOWN) {
            if (session.getPlayers().size() < 2) {
                // Auto-win for last player or end game
                if (session.getPlayers().size() == 1) {
                    UUID lastPlayer = session.getPlayers().iterator().next();
                    Player winner = Bukkit.getPlayer(lastPlayer);
                    if (winner != null) {
                        endGame(session, winner);
                        return;
                    }
                }
                endGame(session, null);
                return;
            }
        }

        // Clean up empty sessions
        if (session.getPlayers().isEmpty()) {
            cleanupSession(session);
        } else {
            arenaManager.updateSigns(session.getArena(),
                    session.getState() == GameSession.State.WAITING ? "Waiting" : "Playing",
                    session.getPlayers().size());
        }
    }

    // ── Ready System ─────────────────────────────────────────────────────────

    public void setReady(Player player) {
        String key = playerArenas.get(player.getUniqueId());
        if (key == null) return;
        GameSession session = activeSessions.get(key);
        if (session == null || session.getState() != GameSession.State.WAITING) return;

        String prefix = plugin.getConfig().getString("prefix", "&6[CTF] &r");

        if (session.getReadyPlayers().contains(player.getUniqueId())) {
            MessageUtil.send(player, prefix, "&7You are already ready.");
            return;
        }

        session.getReadyPlayers().add(player.getUniqueId());
        session.getReadyWarnings().remove(player.getUniqueId());
        broadcastToSession(session, prefix + "&a" + player.getName() + " &7is ready! &8(" +
                session.getReadyPlayers().size() + "/" + session.getPlayers().size() + ")");

        if (session.isAllReady()) {
            startCountdown(session);
        }
    }

    private void startReadyCheck(GameSession session) {
        int warningInterval = plugin.getConfig().getInt("ready-check.warning-interval", 5) * 20;
        int maxWarnings = plugin.getConfig().getInt("ready-check.max-warnings", 3);
        String prefix = plugin.getConfig().getString("prefix", "&6[CTF] &r");

        session.setReadyCheckTask(new BukkitRunnable() {
            @Override
            public void run() {
                if (session.getState() != GameSession.State.WAITING) {
                    cancel();
                    return;
                }

                List<UUID> toKick = new ArrayList<>();
                for (UUID uuid : session.getPlayers()) {
                    if (session.getReadyPlayers().contains(uuid)) continue;
                    int warnings = session.getReadyWarnings().merge(uuid, 1, Integer::sum);
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) {
                        toKick.add(uuid);
                        continue;
                    }
                    if (warnings >= maxWarnings) {
                        toKick.add(uuid);
                        MessageUtil.send(p, prefix, "&cYou were kicked for not readying up!");
                    } else {
                        MessageUtil.send(p, prefix, "&eReady up! Click the &agreen item &ein your hotbar. &8(Warning " +
                                warnings + "/" + maxWarnings + ")");
                    }
                }
                for (UUID uuid : toKick) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) leaveArena(p);
                }
            }
        }.runTaskTimer(plugin, warningInterval, warningInterval));
    }

    // ── Game Start ───────────────────────────────────────────────────────────

    private void startCountdown(GameSession session) {
        session.setState(GameSession.State.COUNTDOWN);
        if (session.getReadyCheckTask() != null) {
            session.getReadyCheckTask().cancel();
            session.setReadyCheckTask(null);
        }

        int countdownSeconds = plugin.getConfig().getInt("start-countdown", 3);
        Arena arena = session.getArena();
        arenaManager.updateSigns(arena, "Starting", session.getPlayers().size());

        // Teleport all to start
        Location startSpawn = arena.getStartSpawn();
        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.teleport(startSpawn);
                p.getInventory().clear();
                p.setGameMode(GameMode.ADVENTURE);
            }
        }

        // Countdown
        new BukkitRunnable() {
            int count = countdownSeconds;
            @Override
            public void run() {
                if (count <= 0) {
                    cancel();
                    startGame(session);
                    return;
                }
                String color = count == 3 ? "&c" : count == 2 ? "&e" : "&a";
                for (UUID uuid : session.getPlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        MessageUtil.sendTitle(p, color + "&l" + count, "&7Get ready!", 0, 20, 5);
                        p.playSound(p.getLocation(), Sounds.CLICK, 1f, 1f);
                    }
                }
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startGame(GameSession session) {
        session.setState(GameSession.State.PLAYING);
        session.setGameStartTime(System.currentTimeMillis());
        Arena arena = session.getArena();
        arenaManager.updateSigns(arena, "Playing", session.getPlayers().size());

        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                MessageUtil.sendTitle(p, "&a&lGO!", "&7Race to the end!", 0, 20, 10);
                p.playSound(p.getLocation(), Sounds.LEVEL_UP, 1f, 1.5f);
            }
        }

        // Record game played
        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) statsManager.recordGamePlayed(p);
        }

        // Begin first round
        beginRound(session);
    }

    // ── Round Logic ──────────────────────────────────────────────────────────

    private void beginRound(GameSession session) {
        if (session.getState() != GameSession.State.PLAYING) return;

        Arena arena = session.getArena();

        // Generate new floor
        generateFloor(session);
        placeFloor(session);

        // Choose safe block
        Material[] blocks = getBlocksForDifficulty(arena.getDifficulty());
        Material safe = blocks[random.nextInt(blocks.length)];

        // Get round interval
        int intervalTicks = getRoundInterval(arena.getDifficulty()) * 20;

        // Ensure safe block appears at required frequency
        session.setSafeBlock(safe);
        ensureSafeBlockDistribution(session);

        // Re-place floor after distribution fix
        placeFloor(session);

        // Announce safe block
        String safeName = MessageUtil.formatMaterialName(safe.name());
        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                MessageUtil.sendTitle(p, "&b&l" + safeName, "&7Stand on this block!", 5, 40, 5);
                p.playSound(p.getLocation(), Sounds.EXPERIENCE_ORB, 1f, 1.2f);
            }
        }

        // Schedule block disappearance
        session.setRoundTask(new BukkitRunnable() {
            @Override
            public void run() {
                disappearBlocks(session);
            }
        }.runTaskLater(plugin, intervalTicks));
    }

    private void disappearBlocks(GameSession session) {
        if (session.getState() != GameSession.State.PLAYING) return;

        Arena arena = session.getArena();
        Material safe = session.getSafeBlock();
        Material[][] grid = session.getTileGrid();
        int[] pathBounds = arena.getPathBounds();
        World world = arena.getWorld();
        if (world == null) return;

        // Remove non-safe blocks
        int rows = arena.getTileRows();
        int cols = arena.getTileCols();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (grid[row][col] != safe) {
                    clearTile(world, arena, row, col);
                }
            }
        }

        // Play sound
        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.playSound(p.getLocation(), Sounds.ANVIL_USE, 0.5f, 0.5f);
            }
        }

        // After a delay, rebuild floor and start next round
        int rebuildDelay = plugin.getConfig().getInt("rebuild-delay-ticks", 30);
        session.setRoundTask(new BukkitRunnable() {
            @Override
            public void run() {
                beginRound(session);
            }
        }.runTaskLater(plugin, rebuildDelay));
    }

    // ── Floor Generation ─────────────────────────────────────────────────────

    private void generateFloor(GameSession session) {
        Arena arena = session.getArena();
        int rows = arena.getTileRows();
        int cols = arena.getTileCols();
        Material[] blocks = getBlocksForDifficulty(arena.getDifficulty());
        int safeInterval = getSafeRowInterval(arena.getDifficulty());

        Material[][] grid = new Material[rows][cols];

        // Fill randomly
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                grid[row][col] = blocks[random.nextInt(blocks.length)];
            }
        }

        // We don't know the safe block yet during generation,
        // but we guarantee diversity — the safe block placement is
        // ensured AFTER the safe block is chosen.
        // Instead, we'll guarantee that every safeInterval rows,
        // there's at least one tile of EACH block type present in the row group.
        // This ensures no matter which block is chosen as safe, it will appear.

        // Actually, let's pre-guarantee: for every safeInterval rows,
        // ensure at least one tile is a specific block from the pool.
        // Since we don't know the safe block, we ensure ALL blocks appear regularly.
        // Simpler approach: just guarantee even distribution.

        session.setTileGrid(grid);
    }

    /**
     * After safe block is chosen, ensure it appears at the required frequency.
     */
    private void ensureSafeBlockDistribution(GameSession session) {
        Arena arena = session.getArena();
        Material safe = session.getSafeBlock();
        Material[][] grid = session.getTileGrid();
        int rows = arena.getTileRows();
        int cols = arena.getTileCols();
        int safeInterval = getSafeRowInterval(arena.getDifficulty());

        for (int row = 0; row < rows; row += safeInterval) {
            boolean hasSafe = false;
            int endRow = Math.min(row + safeInterval, rows);
            for (int r = row; r < endRow && !hasSafe; r++) {
                for (int c = 0; c < cols; c++) {
                    if (grid[r][c] == safe) {
                        hasSafe = true;
                        break;
                    }
                }
            }
            if (!hasSafe) {
                // Place one safe tile randomly in this row group
                int r = row + random.nextInt(endRow - row);
                int c = random.nextInt(cols);
                grid[r][c] = safe;
            }
        }
    }

    private void placeFloor(GameSession session) {
        Arena arena = session.getArena();
        World world = arena.getWorld();
        if (world == null) return;

        Material[][] grid = session.getTileGrid();
        int rows = arena.getTileRows();
        int cols = arena.getTileCols();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                placeTile(world, arena, row, col, grid[row][col]);
            }
        }
    }

    private void placeTile(World world, Arena arena, int row, int col, Material material) {
        int[] pathBounds = arena.getPathBounds();
        int y = arena.getCornerY();

        // Calculate tile block positions based on direction
        int baseX, baseZ;
        switch (arena.getDirection()) {
            case NORTH -> {
                baseX = pathBounds[0] + col * 2;
                baseZ = pathBounds[5] - row * 2;
            }
            case SOUTH -> {
                baseX = pathBounds[0] + col * 2;
                baseZ = pathBounds[2] + row * 2;
            }
            case EAST -> {
                baseX = pathBounds[0] + row * 2;
                baseZ = pathBounds[2] + col * 2;
            }
            case WEST -> {
                baseX = pathBounds[3] - row * 2;
                baseZ = pathBounds[2] + col * 2;
            }
            default -> { return; }
        }

        // Place 2x2 tile
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                int bx, bz;
                switch (arena.getDirection()) {
                    case NORTH -> { bx = baseX + dx; bz = baseZ - dz; }
                    case SOUTH -> { bx = baseX + dx; bz = baseZ + dz; }
                    case EAST  -> { bx = baseX + dx; bz = baseZ + dz; }
                    case WEST  -> { bx = baseX - dx; bz = baseZ + dz; }
                    default    -> { return; }
                }
                world.getBlockAt(bx, y, bz).setType(material, false);
            }
        }
    }

    private void clearTile(World world, Arena arena, int row, int col) {
        placeTile(world, arena, row, col, Material.AIR);
    }

    public void clearFloor(Arena arena) {
        World world = arena.getWorld();
        if (world == null) return;
        int[] pathBounds = arena.getPathBounds();
        for (int x = pathBounds[0]; x <= pathBounds[3]; x++) {
            for (int z = pathBounds[2]; z <= pathBounds[5]; z++) {
                world.getBlockAt(x, arena.getCornerY(), z).setType(Material.AIR, false);
            }
        }
    }

    // ── Fall & Finish Detection ──────────────────────────────────────────────

    public void handleFall(Player player) {
        String key = playerArenas.get(player.getUniqueId());
        if (key == null) return;
        GameSession session = activeSessions.get(key);
        if (session == null || session.getState() != GameSession.State.PLAYING) return;

        Arena arena = session.getArena();
        Location startSpawn = arena.getStartSpawn();
        player.teleport(startSpawn);
        statsManager.recordFall(player);

        String prefix = plugin.getConfig().getString("prefix", "&6[CTF] &r");
        MessageUtil.send(player, prefix, "&cWrong block! Back to the start.");
        MessageUtil.sendActionBar(player, "&cRespawned at start!");
    }

    public void handleFinish(Player player) {
        String key = playerArenas.get(player.getUniqueId());
        if (key == null) return;
        GameSession session = activeSessions.get(key);
        if (session == null || session.getState() != GameSession.State.PLAYING) return;

        endGame(session, player);
    }

    // ── Game End ─────────────────────────────────────────────────────────────

    private void endGame(GameSession session, Player winner) {
        session.setState(GameSession.State.ENDING);
        session.cancelTasks();

        Arena arena = session.getArena();
        String prefix = plugin.getConfig().getString("prefix", "&6[CTF] &r");

        // Calculate win time
        long winTime = System.currentTimeMillis() - session.getGameStartTime();

        if (winner != null) {
            String winMsg = prefix + "&a&l" + winner.getName() + " &awon the game!";
            broadcastToSession(session, winMsg);

            for (UUID uuid : session.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    MessageUtil.sendTitle(p, "&6&l" + winner.getName() + " Wins!", "&7Game over!", 5, 60, 20);
                    p.playSound(p.getLocation(), Sounds.LEVEL_UP, 1f, 1f);
                }
            }

            // Stats
            statsManager.recordWin(winner, winTime);
            for (UUID uuid : session.getPlayers()) {
                if (!uuid.equals(winner.getUniqueId())) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) statsManager.recordLoss(p);
                }
            }

            // Rewards
            rewardManager.giveRewards(winner, arena);
        } else {
            broadcastToSession(session, prefix + "&7Game ended — no winner.");
        }

        // Delayed cleanup to let players see the results
        new BukkitRunnable() {
            @Override
            public void run() {
                // Restore all players
                List<UUID> toRestore = new ArrayList<>(session.getPlayers());
                for (UUID uuid : toRestore) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        playerArenas.remove(uuid);
                        PlayerState state = session.getSavedStates().remove(uuid);
                        if (state != null) state.restore(p);
                    }
                }
                session.getPlayers().clear();
                cleanupSession(session);
            }
        }.runTaskLater(plugin, 60L); // 3 second delay
    }

    private void cleanupSession(GameSession session) {
        session.cancelTasks();
        clearFloor(session.getArena());
        activeSessions.remove(session.getArena().getName().toLowerCase());
        arenaManager.updateSigns(session.getArena(), "Open", 0);
    }

    // ── Player Preparation ───────────────────────────────────────────────────

    private void preparePlayer(Player player, Arena arena) {
        player.teleport(arena.getLobbySpawn());
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(20f);

        // Ready item (slot 0) — lime dye
        Material readyMat = Materials.get("LIME_DYE");
        if (readyMat == null) readyMat = Material.LIME_DYE;
        ItemStack readyItem = new ItemStack(readyMat);
        ItemMeta readyMeta = readyItem.getItemMeta();
        readyMeta.displayName(MessageUtil.colorize("&a&lReady Up"));
        readyItem.setItemMeta(readyMeta);
        player.getInventory().setItem(0, readyItem);

        // Leave item (slot 8) — red dye
        Material leaveMat = Materials.get("RED_DYE");
        if (leaveMat == null) leaveMat = Material.RED_DYE;
        ItemStack leaveItem = new ItemStack(leaveMat);
        ItemMeta leaveMeta = leaveItem.getItemMeta();
        leaveMeta.displayName(MessageUtil.colorize("&c&lLeave Game"));
        leaveItem.setItemMeta(leaveMeta);
        player.getInventory().setItem(8, leaveItem);
    }

    // ── Query Methods ────────────────────────────────────────────────────────

    public boolean isInGame(Player player) {
        return playerArenas.containsKey(player.getUniqueId());
    }

    public GameSession getSession(Player player) {
        String key = playerArenas.get(player.getUniqueId());
        return key != null ? activeSessions.get(key) : null;
    }

    public GameSession getSession(Arena arena) {
        return activeSessions.get(arena.getName().toLowerCase());
    }

    public boolean isArenaInUse(Arena arena) {
        return activeSessions.containsKey(arena.getName().toLowerCase());
    }

    /**
     * End all games (called on plugin disable).
     */
    public void endAll() {
        for (GameSession session : new ArrayList<>(activeSessions.values())) {
            session.cancelTasks();
            for (UUID uuid : new ArrayList<>(session.getPlayers())) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    playerArenas.remove(uuid);
                    PlayerState state = session.getSavedStates().remove(uuid);
                    if (state != null) state.restore(p);
                }
            }
            clearFloor(session.getArena());
        }
        activeSessions.clear();
        playerArenas.clear();
    }

    // ── Config Helpers ───────────────────────────────────────────────────────

    public Material[] getBlocksForDifficulty(Difficulty difficulty) {
        List<String> blockNames = plugin.getConfig().getStringList("difficulties." + difficulty.getKey() + ".blocks");
        List<Material> mats = new ArrayList<>();
        for (String name : blockNames) {
            Material m = Materials.get(name);
            if (m != null) mats.add(m);
        }
        if (mats.isEmpty()) {
            mats.add(Material.STONE);
        }
        return mats.toArray(new Material[0]);
    }

    private int getRoundInterval(Difficulty difficulty) {
        return plugin.getConfig().getInt("difficulties." + difficulty.getKey() + ".round-interval", 5);
    }

    private int getSafeRowInterval(Difficulty difficulty) {
        return plugin.getConfig().getInt("difficulties." + difficulty.getKey() + ".safe-row-interval", 2);
    }

    // ── Broadcasting ─────────────────────────────────────────────────────────

    private void broadcastToSession(GameSession session, String message) {
        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(MessageUtil.colorize(message));
        }
    }
}
