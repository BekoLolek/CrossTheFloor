package dev.bekololek.crossthefloor;

import dev.bekololek.crossthefloor.commands.CrossTheFloorCommand;
import dev.bekololek.crossthefloor.listeners.GameListener;
import dev.bekololek.crossthefloor.listeners.ProtectionListener;
import dev.bekololek.crossthefloor.listeners.SignListener;
import dev.bekololek.crossthefloor.managers.ArenaManager;
import dev.bekololek.crossthefloor.managers.GameManager;
import dev.bekololek.crossthefloor.managers.RewardManager;
import dev.bekololek.crossthefloor.managers.StatsManager;
import dev.bekololek.crossthefloor.stats.CrossTheFloorExpansion;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private ArenaManager arenaManager;
    private GameManager gameManager;
    private StatsManager statsManager;
    private RewardManager rewardManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        arenaManager  = new ArenaManager(this);
        rewardManager = new RewardManager(this);
        statsManager  = new StatsManager(this);
        statsManager.load();
        statsManager.startAutoSave();
        arenaManager.load();
        gameManager = new GameManager(this, arenaManager, statsManager, rewardManager);

        // Listeners
        var pm = getServer().getPluginManager();
        pm.registerEvents(new SignListener(this, arenaManager, gameManager), this);
        pm.registerEvents(new GameListener(this, gameManager), this);
        pm.registerEvents(new ProtectionListener(gameManager), this);

        // Commands
        var cmd = getCommand("ctf");
        var handler = new CrossTheFloorCommand(this, arenaManager, gameManager, statsManager);
        if (cmd != null) {
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        // PlaceholderAPI expansion
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CrossTheFloorExpansion(this, statsManager).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        getLogger().info("CrossTheFloor.v1 - BL enabled.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.endAll();
        if (statsManager != null) statsManager.saveSync();
        getLogger().info("CrossTheFloor.v1 - BL disabled.");
    }
}
