package dev.bekololek.crossthefloor.commands;

import dev.bekololek.crossthefloor.Main;
import dev.bekololek.crossthefloor.managers.ArenaManager;
import dev.bekololek.crossthefloor.managers.GameManager;
import dev.bekololek.crossthefloor.managers.StatsManager;
import dev.bekololek.crossthefloor.models.Arena;
import dev.bekololek.crossthefloor.models.Difficulty;
import dev.bekololek.crossthefloor.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class CrossTheFloorCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final ArenaManager arenaManager;
    private final GameManager gameManager;
    private final StatsManager statsManager;

    public CrossTheFloorCommand(Main plugin, ArenaManager arenaManager,
                                GameManager gameManager, StatsManager statsManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.gameManager = gameManager;
        this.statsManager = statsManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String prefix = plugin.getConfig().getString("prefix", "&6[CTF] &r");

        if (args.length == 0) {
            sendUsage(sender, prefix);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "join" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cOnly players can join."));
                    return true;
                }
                if (!checkPerm(sender, "crossthefloor.play", prefix)) return true;
                if (args.length < 2) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cUsage: /ctf join <arena>"));
                    return true;
                }
                Arena joinArena = arenaManager.getArena(args[1]);
                if (joinArena == null) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cArena '" + args[1] + "' not found."));
                    return true;
                }
                gameManager.joinArena(player, joinArena);
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) return true;
                if (!gameManager.isInGame(player)) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cYou are not in a game."));
                    return true;
                }
                gameManager.leaveArena(player);
            }
            case "ready" -> {
                if (!(sender instanceof Player player)) return true;
                if (!gameManager.isInGame(player)) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cYou are not in a game."));
                    return true;
                }
                gameManager.setReady(player);
            }
            case "create" -> {
                if (!checkPerm(sender, "crossthefloor.admin", prefix)) return true;
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cOnly players can create arenas."));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cUsage: /ctf create <name> <easy|medium|hard>"));
                    return true;
                }
                String name = args[1];
                Difficulty diff = Difficulty.fromString(args[2]);
                if (diff == null) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cInvalid difficulty. Use: easy, medium, hard"));
                    return true;
                }
                if (arenaManager.arenaExists(name)) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cArena '" + name + "' already exists!"));
                    return true;
                }
                if (arenaManager.hasPending(player)) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cYou already have a pending creation. Use /ctf confirm or /ctf cancel."));
                    return true;
                }
                arenaManager.startCreation(player, name, diff);
                sender.sendMessage(MessageUtil.colorize(prefix + "&aArena outline shown! Look at the particle preview."));
                sender.sendMessage(MessageUtil.colorize(prefix + "&7Use &e/ctf confirm &7to build or &e/ctf cancel &7to abort."));
            }
            case "confirm" -> {
                if (!checkPerm(sender, "crossthefloor.admin", prefix)) return true;
                if (!(sender instanceof Player player)) return true;
                if (!arenaManager.hasPending(player)) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cNo pending arena creation."));
                    return true;
                }
                String arenaName = arenaManager.getPending(player).name();
                arenaManager.confirmCreation(player);
                sender.sendMessage(MessageUtil.colorize(prefix + "&aArena '&e" + arenaName + "&a' created successfully!"));
            }
            case "cancel" -> {
                if (!checkPerm(sender, "crossthefloor.admin", prefix)) return true;
                if (!(sender instanceof Player player)) return true;
                if (!arenaManager.hasPending(player)) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cNo pending arena creation."));
                    return true;
                }
                arenaManager.cancelCreation(player);
                sender.sendMessage(MessageUtil.colorize(prefix + "&eArena creation cancelled."));
            }
            case "delete" -> {
                if (!checkPerm(sender, "crossthefloor.admin", prefix)) return true;
                if (args.length < 2) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cUsage: /ctf delete <name>"));
                    return true;
                }
                String delName = args[1];
                if (!arenaManager.arenaExists(delName)) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cArena '" + delName + "' not found."));
                    return true;
                }
                if (gameManager.isArenaInUse(arenaManager.getArena(delName))) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&cCannot delete — arena is currently in use!"));
                    return true;
                }
                arenaManager.deleteArena(delName);
                sender.sendMessage(MessageUtil.colorize(prefix + "&aArena '&e" + delName + "&a' deleted."));
            }
            case "list" -> {
                if (!checkPerm(sender, "crossthefloor.admin", prefix)) return true;
                var allArenas = arenaManager.getAllArenas();
                if (allArenas.isEmpty()) {
                    sender.sendMessage(MessageUtil.colorize(prefix + "&7No arenas configured."));
                    return true;
                }
                sender.sendMessage(MessageUtil.colorize(prefix + "&6Arenas:"));
                for (Arena a : allArenas) {
                    String status = gameManager.isArenaInUse(a) ? "&cIn Use" : "&aOpen";
                    sender.sendMessage(MessageUtil.colorize("  &e" + a.getName() + " &8- &7" +
                            capitalize(a.getDifficulty().getKey()) + " &8| " + status));
                }
            }
            case "reload" -> {
                if (!checkPerm(sender, "crossthefloor.admin", prefix)) return true;
                plugin.reloadConfig();
                arenaManager.load();
                sender.sendMessage(MessageUtil.colorize(prefix + "&aConfig and arenas reloaded."));
            }
            case "stats" -> {
                if (args.length == 1) {
                    // Own stats
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(MessageUtil.colorize(prefix + "&cOnly players can view stats."));
                        return true;
                    }
                    if (!checkPerm(sender, "crossthefloor.stats", prefix)) return true;
                    showStats(sender, player.getUniqueId(), player.getName(), prefix);
                } else if (args[1].equalsIgnoreCase("top")) {
                    if (!checkPerm(sender, "crossthefloor.stats", prefix)) return true;
                    String statName = args.length >= 3 ? args[2] : "games_won";
                    showLeaderboard(sender, statName, prefix);
                } else {
                    // Other player stats
                    if (!checkPerm(sender, "crossthefloor.stats.others", prefix)) return true;
                    OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                    if (target == null) {
                        sender.sendMessage(MessageUtil.colorize(prefix + "&cPlayer not found."));
                        return true;
                    }
                    showStats(sender, target.getUniqueId(), target.getName(), prefix);
                }
            }
            default -> sendUsage(sender, prefix);
        }
        return true;
    }

    private void showStats(CommandSender sender, UUID uuid, String name, String prefix) {
        sender.sendMessage(MessageUtil.colorize(prefix + "&6Stats for &e" + name + "&6:"));
        sender.sendMessage(MessageUtil.colorize("  &7Games Played: &f" + statsManager.getPlayerStat(uuid, "games_played")));
        sender.sendMessage(MessageUtil.colorize("  &7Games Won: &a" + statsManager.getPlayerStat(uuid, "games_won")));
        sender.sendMessage(MessageUtil.colorize("  &7Games Lost: &c" + statsManager.getPlayerStat(uuid, "games_lost")));
        sender.sendMessage(MessageUtil.colorize("  &7Total Falls: &e" + statsManager.getPlayerStat(uuid, "total_falls")));
        sender.sendMessage(MessageUtil.colorize("  &7Win Rate: &b" + statsManager.getPlayerStat(uuid, "win_rate") + "%"));
        sender.sendMessage(MessageUtil.colorize("  &7Fastest Win: &d" + statsManager.getPlayerStat(uuid, "fastest_win") + "s"));
        sender.sendMessage(MessageUtil.colorize("  &7Current Streak: &6" + statsManager.getPlayerStat(uuid, "current_streak")));
        sender.sendMessage(MessageUtil.colorize("  &7Best Streak: &6" + statsManager.getPlayerStat(uuid, "best_streak")));
    }

    private void showLeaderboard(CommandSender sender, String statName, String prefix) {
        if (!StatsManager.leaderboardStats().contains(statName.toLowerCase())) {
            sender.sendMessage(MessageUtil.colorize(prefix + "&cInvalid stat. Available: " +
                    String.join(", ", StatsManager.leaderboardStats())));
            return;
        }
        String label = StatsManager.statLabel(statName);
        List<Map.Entry<String, Number>> top = statsManager.getTopPlayers(statName, 10);
        sender.sendMessage(MessageUtil.colorize(prefix + "&6Top 10 — " + label + ":"));
        for (int i = 0; i < top.size(); i++) {
            Map.Entry<String, Number> entry = top.get(i);
            String value;
            if (entry.getValue() instanceof Double d) {
                value = String.format("%.1f", d);
            } else {
                value = String.valueOf(entry.getValue());
            }
            sender.sendMessage(MessageUtil.colorize("  &e#" + (i + 1) + " &f" + entry.getKey() + " &8- &a" + value));
        }
        if (top.isEmpty()) {
            sender.sendMessage(MessageUtil.colorize("  &7No data yet."));
        }
    }

    private void sendUsage(CommandSender sender, String prefix) {
        sender.sendMessage(MessageUtil.colorize(prefix + "&6CrossTheFloor Commands:"));
        sender.sendMessage(MessageUtil.colorize("  &e/ctf join <arena> &8- Join an arena"));
        sender.sendMessage(MessageUtil.colorize("  &e/ctf leave &8- Leave current game"));
        sender.sendMessage(MessageUtil.colorize("  &e/ctf create <name> <difficulty> &8- Create arena"));
        sender.sendMessage(MessageUtil.colorize("  &e/ctf confirm &8- Confirm arena creation"));
        sender.sendMessage(MessageUtil.colorize("  &e/ctf cancel &8- Cancel arena creation"));
        sender.sendMessage(MessageUtil.colorize("  &e/ctf delete <name> &8- Delete arena"));
        sender.sendMessage(MessageUtil.colorize("  &e/ctf list &8- List arenas"));
        sender.sendMessage(MessageUtil.colorize("  &e/ctf reload &8- Reload config"));
        sender.sendMessage(MessageUtil.colorize("  &e/ctf stats [player|top [stat]] &8- View stats"));
    }

    private boolean checkPerm(CommandSender sender, String perm, String prefix) {
        if (!sender.hasPermission(perm)) {
            sender.sendMessage(MessageUtil.colorize(prefix + "&cNo permission."));
            return false;
        }
        return true;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("join", "leave", "ready", "create", "confirm", "cancel", "delete", "list", "reload", "stats"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "delete", "join" -> {
                    for (Arena a : arenaManager.getAllArenas()) completions.add(a.getName());
                }
                case "create" -> completions.add("<name>");
                case "stats" -> {
                    completions.add("top");
                    for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
                }
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "create" -> completions.addAll(List.of("easy", "medium", "hard"));
                case "stats" -> {
                    if (args[1].equalsIgnoreCase("top")) {
                        completions.addAll(StatsManager.leaderboardStats());
                    }
                }
            }
        }

        String input = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(input)).collect(Collectors.toList());
    }
}
