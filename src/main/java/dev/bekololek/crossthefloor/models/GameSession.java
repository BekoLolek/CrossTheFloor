package dev.bekololek.crossthefloor.models;

import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameSession {

    public enum State {
        WAITING,    // players joining, ready checks
        COUNTDOWN,  // game starting countdown
        PLAYING,    // game in progress
        ENDING      // game ending, cleanup
    }

    private final Arena arena;
    private State state = State.WAITING;

    private final Set<UUID> players = new HashSet<>();
    private final Map<UUID, PlayerState> savedStates = new HashMap<>();
    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Map<UUID, Integer> readyWarnings = new HashMap<>();

    // Current round state
    private Material safeBlock;
    private Material[][] tileGrid; // [row][col] = material for that 2x2 tile
    private BukkitTask roundTask;
    private BukkitTask readyCheckTask;

    // Timing
    private long gameStartTime;

    public GameSession(Arena arena) {
        this.arena = arena;
    }

    public Arena getArena() { return arena; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }

    public Set<UUID> getPlayers() { return players; }
    public Map<UUID, PlayerState> getSavedStates() { return savedStates; }
    public Set<UUID> getReadyPlayers() { return readyPlayers; }
    public Map<UUID, Integer> getReadyWarnings() { return readyWarnings; }

    public Material getSafeBlock() { return safeBlock; }
    public void setSafeBlock(Material safeBlock) { this.safeBlock = safeBlock; }

    public Material[][] getTileGrid() { return tileGrid; }
    public void setTileGrid(Material[][] tileGrid) { this.tileGrid = tileGrid; }

    public BukkitTask getRoundTask() { return roundTask; }
    public void setRoundTask(BukkitTask roundTask) { this.roundTask = roundTask; }

    public BukkitTask getReadyCheckTask() { return readyCheckTask; }
    public void setReadyCheckTask(BukkitTask readyCheckTask) { this.readyCheckTask = readyCheckTask; }

    public long getGameStartTime() { return gameStartTime; }
    public void setGameStartTime(long gameStartTime) { this.gameStartTime = gameStartTime; }

    public boolean isAllReady() {
        return readyPlayers.containsAll(players) && players.size() >= 2;
    }

    public void cancelTasks() {
        if (roundTask != null) {
            roundTask.cancel();
            roundTask = null;
        }
        if (readyCheckTask != null) {
            readyCheckTask.cancel();
            readyCheckTask = null;
        }
    }
}
