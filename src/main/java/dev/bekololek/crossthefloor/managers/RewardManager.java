package dev.bekololek.crossthefloor.managers;

import dev.bekololek.crossthefloor.Main;
import dev.bekololek.crossthefloor.models.Arena;
import dev.bekololek.crossthefloor.utils.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class RewardManager {

    private final Main plugin;
    private Economy economy;

    public RewardManager(Main plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
            plugin.getLogger().info("Vault economy hooked.");
        }
    }

    public void giveRewards(Player player, Arena arena) {
        String prefix = plugin.getConfig().getString("prefix", "&6[CTF] &r");

        // Money reward
        if (economy != null && arena.getRewardMoney() > 0) {
            economy.depositPlayer(player, arena.getRewardMoney());
            MessageUtil.send(player, prefix, "&aYou received &e$" +
                    String.format("%.2f", arena.getRewardMoney()) + "&a!");
        }

        // Command rewards
        for (String cmd : arena.getRewardCommands()) {
            String parsed = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }
}
