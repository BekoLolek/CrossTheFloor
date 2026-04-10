package dev.bekololek.crossthefloor.managers;

import dev.bekololek.crossthefloor.Main;
import dev.bekololek.crossthefloor.models.Arena;
import dev.bekololek.crossthefloor.utils.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;

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

    public void giveRewards(Player player, Arena arena, int placement) {
        String prefix = plugin.getConfig().getString("prefix", "&6[CTF] &r");

        // Resolve tier index: clamp to last defined tier
        List<Double> moneyList = arena.getPlacementMoney();
        List<List<String>> cmdList = arena.getPlacementCommands();

        int moneyIndex = moneyList.isEmpty() ? -1 : Math.min(placement - 1, moneyList.size() - 1);
        int cmdIndex = cmdList.isEmpty() ? -1 : Math.min(placement - 1, cmdList.size() - 1);

        // Money reward
        if (economy != null && moneyIndex >= 0 && moneyList.get(moneyIndex) > 0) {
            double amount = moneyList.get(moneyIndex);
            economy.depositPlayer(player, amount);
            MessageUtil.send(player, prefix, "&aYou received &e$" +
                    String.format("%.2f", amount) + "&a!");
        }

        // Command rewards
        if (cmdIndex >= 0) {
            for (String cmd : cmdList.get(cmdIndex)) {
                String parsed = cmd.replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }
    }
}
