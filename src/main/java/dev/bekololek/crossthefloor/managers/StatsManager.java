package dev.bekololek.crossthefloor.managers;

import dev.bekololek.crossthefloor.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class StatsManager {

    private final Main plugin;
    private final File statsFile;
    private final Map<UUID, PlayerStats> players = new HashMap<>();

    private static final List<StatDef> PLAYER_SCHEMA = List.of(
            new StatDef("games_played",    "Games Played",    "int",    null, true),
            new StatDef("games_won",       "Games Won",       "int",    null, true),
            new StatDef("games_lost",      "Games Lost",      "int",    null, true),
            new StatDef("total_falls",     "Total Falls",     "int",    null, true),
            new StatDef("win_rate",        "Win Rate",        "double", "%",  true),
            new StatDef("fastest_win",     "Fastest Win",     "double", "s",  true),
            new StatDef("current_streak",  "Current Streak",  "int",    null, false),
            new StatDef("best_streak",     "Best Streak",     "int",    null, true)
    );

    private static final List<StatDef> GLOBAL_SCHEMA = List.of(
            new StatDef("total_games",       "Total Games",       "int",    null, false),
            new StatDef("total_wins",        "Total Wins",        "int",    null, false),
            new StatDef("total_falls",       "Total Falls",       "int",    null, false),
            new StatDef("top_winner",        "Top Winner",        "string", null, false),
            new StatDef("fastest_win_ever",  "Fastest Win Ever",  "double", "s",  false)
    );

    record StatDef(String key, String label, String type, String unit, boolean leaderboard) {}

    public StatsManager(Main plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
    }

    // ── Data Class ───────────────────────────────────────────────────────────

    public static class PlayerStats {
        String name;
        int gamesPlayed;
        int gamesWon;
        int gamesLost;
        int totalFalls;
        long fastestWinMs = Long.MAX_VALUE; // milliseconds
        int currentStreak;
        int bestStreak;
    }

    // ── Load / Save ──────────────────────────────────────────────────────────

    public void load() {
        if (!statsFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(statsFile);
        ConfigurationSection sec = yaml.getConfigurationSection("players");
        if (sec == null) return;

        for (String uuidStr : sec.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); }
            catch (IllegalArgumentException e) { continue; }

            ConfigurationSection p = sec.getConfigurationSection(uuidStr);
            if (p == null) continue;

            PlayerStats ps = new PlayerStats();
            ps.name = p.getString("name", "Unknown");
            ps.gamesPlayed = p.getInt("games_played", 0);
            ps.gamesWon = p.getInt("games_won", 0);
            ps.gamesLost = p.getInt("games_lost", 0);
            ps.totalFalls = p.getInt("total_falls", 0);
            ps.fastestWinMs = p.getLong("fastest_win_ms", Long.MAX_VALUE);
            ps.currentStreak = p.getInt("current_streak", 0);
            ps.bestStreak = p.getInt("best_streak", 0);
            players.put(uuid, ps);
        }
        plugin.getLogger().info("Loaded stats for " + players.size() + " players.");
    }

    public void save() { saveToYaml(true); }
    public void saveSync() { saveToYaml(false); }

    private void saveToYaml(boolean async) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("plugin", "CrossTheFloor");
        yaml.set("version", 1);

        // Schema
        for (StatDef def : PLAYER_SCHEMA) {
            String base = "schema.player." + def.key();
            yaml.set(base + ".label", def.label());
            yaml.set(base + ".type", def.type());
            if (def.unit() != null) yaml.set(base + ".unit", def.unit());
            yaml.set(base + ".leaderboard", def.leaderboard());
        }
        for (StatDef def : GLOBAL_SCHEMA) {
            String base = "schema.global." + def.key();
            yaml.set(base + ".label", def.label());
            yaml.set(base + ".type", def.type());
            if (def.unit() != null) yaml.set(base + ".unit", def.unit());
        }

        // Global
        yaml.set("global.total_games", players.values().stream().mapToInt(p -> p.gamesPlayed).sum());
        yaml.set("global.total_wins", players.values().stream().mapToInt(p -> p.gamesWon).sum());
        yaml.set("global.total_falls", players.values().stream().mapToInt(p -> p.totalFalls).sum());
        yaml.set("global.top_winner", computeTopWinner());
        yaml.set("global.fastest_win_ever", computeFastestWinEver());

        // Players
        for (var entry : players.entrySet()) {
            String path = "players." + entry.getKey().toString();
            PlayerStats ps = entry.getValue();
            yaml.set(path + ".name", ps.name);
            yaml.set(path + ".games_played", ps.gamesPlayed);
            yaml.set(path + ".games_won", ps.gamesWon);
            yaml.set(path + ".games_lost", ps.gamesLost);
            yaml.set(path + ".total_falls", ps.totalFalls);
            yaml.set(path + ".fastest_win_ms", ps.fastestWinMs);
            yaml.set(path + ".current_streak", ps.currentStreak);
            yaml.set(path + ".best_streak", ps.bestStreak);
        }

        if (async) {
            new BukkitRunnable() {
                @Override public void run() {
                    try { yaml.save(statsFile); }
                    catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Failed to save stats.yml", e); }
                }
            }.runTaskAsynchronously(plugin);
        } else {
            try { yaml.save(statsFile); }
            catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Failed to save stats.yml", e); }
        }
    }

    public void startAutoSave() {
        new BukkitRunnable() {
            @Override public void run() { save(); }
        }.runTaskTimer(plugin, 6000L, 6000L);
    }

    // ── Recording ────────────────────────────────────────────────────────────

    private PlayerStats getOrCreate(Player player) {
        PlayerStats ps = players.computeIfAbsent(player.getUniqueId(), k -> new PlayerStats());
        ps.name = player.getName();
        return ps;
    }

    public void recordGamePlayed(Player player) {
        getOrCreate(player).gamesPlayed++;
    }

    public void recordWin(Player player, long winTimeMs) {
        PlayerStats ps = getOrCreate(player);
        ps.gamesWon++;
        ps.currentStreak++;
        if (ps.currentStreak > ps.bestStreak) ps.bestStreak = ps.currentStreak;
        if (winTimeMs < ps.fastestWinMs) ps.fastestWinMs = winTimeMs;
    }

    public void recordLoss(Player player) {
        PlayerStats ps = getOrCreate(player);
        ps.gamesLost++;
        ps.currentStreak = 0;
    }

    public void recordFall(Player player) {
        getOrCreate(player).totalFalls++;
    }

    public void updateName(Player player) {
        PlayerStats ps = players.get(player.getUniqueId());
        if (ps != null) ps.name = player.getName();
    }

    // ── Stat Access ──────────────────────────────────────────────────────────

    public Object getPlayerStat(UUID uuid, String statName) {
        PlayerStats ps = players.get(uuid);
        if (ps == null) return statDefault(statName);
        return switch (statName.toLowerCase()) {
            case "games_played"   -> ps.gamesPlayed;
            case "games_won"      -> ps.gamesWon;
            case "games_lost"     -> ps.gamesLost;
            case "total_falls"    -> ps.totalFalls;
            case "win_rate"       -> ps.gamesPlayed > 0
                    ? String.format("%.1f", ps.gamesWon * 100.0 / ps.gamesPlayed) : "0.0";
            case "fastest_win"    -> ps.fastestWinMs < Long.MAX_VALUE
                    ? String.format("%.1f", ps.fastestWinMs / 1000.0) : "-";
            case "current_streak" -> ps.currentStreak;
            case "best_streak"    -> ps.bestStreak;
            default -> 0;
        };
    }

    private Object statDefault(String statName) {
        return switch (statName.toLowerCase()) {
            case "win_rate" -> "0.0";
            case "fastest_win" -> "-";
            default -> 0;
        };
    }

    public Object getGlobalStat(String statName) {
        return switch (statName.toLowerCase()) {
            case "total_games"      -> players.values().stream().mapToInt(p -> p.gamesPlayed).sum();
            case "total_wins"       -> players.values().stream().mapToInt(p -> p.gamesWon).sum();
            case "total_falls"      -> players.values().stream().mapToInt(p -> p.totalFalls).sum();
            case "top_winner"       -> computeTopWinner();
            case "fastest_win_ever" -> computeFastestWinEver();
            default -> 0;
        };
    }

    private String computeTopWinner() {
        return players.values().stream()
                .max((a, b) -> Integer.compare(a.gamesWon, b.gamesWon))
                .map(ps -> ps.name)
                .orElse("None");
    }

    private String computeFastestWinEver() {
        long fastest = players.values().stream()
                .mapToLong(p -> p.fastestWinMs)
                .filter(ms -> ms < Long.MAX_VALUE)
                .min().orElse(Long.MAX_VALUE);
        return fastest < Long.MAX_VALUE ? String.format("%.1f", fastest / 1000.0) : "-";
    }

    // ── Leaderboard ──────────────────────────────────────────────────────────

    public List<Map.Entry<String, Number>> getTopPlayers(String statName, int limit) {
        List<Map.Entry<String, Number>> list = new ArrayList<>();
        for (var entry : players.entrySet()) {
            PlayerStats ps = entry.getValue();
            Number value = switch (statName.toLowerCase()) {
                case "games_played"   -> ps.gamesPlayed;
                case "games_won"      -> ps.gamesWon;
                case "games_lost"     -> ps.gamesLost;
                case "total_falls"    -> ps.totalFalls;
                case "win_rate"       -> ps.gamesPlayed > 0 ? ps.gamesWon * 100.0 / ps.gamesPlayed : 0.0;
                case "fastest_win"    -> ps.fastestWinMs < Long.MAX_VALUE ? ps.fastestWinMs / 1000.0 : Double.MAX_VALUE;
                case "best_streak"    -> ps.bestStreak;
                default -> 0;
            };
            list.add(new AbstractMap.SimpleEntry<>(ps.name, value));
        }
        // For fastest_win, lower is better
        if (statName.equalsIgnoreCase("fastest_win")) {
            list.sort((a, b) -> Double.compare(a.getValue().doubleValue(), b.getValue().doubleValue()));
        } else {
            list.sort((a, b) -> Double.compare(b.getValue().doubleValue(), a.getValue().doubleValue()));
        }
        return list.subList(0, Math.min(limit, list.size()));
    }

    public static List<String> leaderboardStats() {
        return PLAYER_SCHEMA.stream().filter(StatDef::leaderboard).map(StatDef::key).toList();
    }

    public static String statLabel(String statName) {
        for (StatDef def : PLAYER_SCHEMA) {
            if (def.key().equals(statName.toLowerCase())) return def.label();
        }
        for (StatDef def : GLOBAL_SCHEMA) {
            if (def.key().equals(statName.toLowerCase())) return def.label();
        }
        return statName;
    }
}
