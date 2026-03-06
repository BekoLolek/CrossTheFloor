package dev.bekololek.crossthefloor.stats;

import dev.bekololek.crossthefloor.Main;
import dev.bekololek.crossthefloor.managers.StatsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class CrossTheFloorExpansion extends PlaceholderExpansion {

    private final Main plugin;
    private final StatsManager statsManager;

    public CrossTheFloorExpansion(Main plugin, StatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
    }

    @Override public @NotNull String getIdentifier() { return "crossthefloor"; }
    @Override public @NotNull String getAuthor()     { return "Lolek"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // %crossthefloor_stat_<name>%
        if (params.startsWith("stat_") && player != null) {
            String statName = params.substring(5);
            return String.valueOf(statsManager.getPlayerStat(player.getUniqueId(), statName));
        }

        // %crossthefloor_global_<name>%
        if (params.startsWith("global_")) {
            String statName = params.substring(7);
            return String.valueOf(statsManager.getGlobalStat(statName));
        }

        // %crossthefloor_top_<name>_<pos>%
        if (params.startsWith("top_") && !params.startsWith("topvalue_")) {
            return parseLeaderboardEntry(params.substring(4), false);
        }

        // %crossthefloor_topvalue_<name>_<pos>%
        if (params.startsWith("topvalue_")) {
            return parseLeaderboardEntry(params.substring(9), true);
        }

        return null;
    }

    private String parseLeaderboardEntry(String rest, boolean valueOnly) {
        int lastUnderscore = rest.lastIndexOf('_');
        if (lastUnderscore < 0) return null;
        String statName = rest.substring(0, lastUnderscore);
        int position;
        try { position = Integer.parseInt(rest.substring(lastUnderscore + 1)); }
        catch (NumberFormatException e) { return null; }
        if (position < 1) return null;

        List<Map.Entry<String, Number>> top = statsManager.getTopPlayers(statName, position);
        if (position > top.size()) return valueOnly ? "0" : "-";
        Map.Entry<String, Number> entry = top.get(position - 1);
        if (valueOnly) {
            Number val = entry.getValue();
            if (val instanceof Double d) return String.format("%.1f", d);
            return String.valueOf(val);
        }
        return entry.getKey();
    }
}
