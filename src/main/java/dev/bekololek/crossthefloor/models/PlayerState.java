package dev.bekololek.crossthefloor.models;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlayerState {

    private final ItemStack[] inventoryContents;
    private final ItemStack[] armorContents;
    private final ItemStack offHand;
    private final Location location;
    private final GameMode gameMode;
    private final int foodLevel;
    private final double health;
    private final float exp;
    private final int expLevel;

    public PlayerState(Player player) {
        this.inventoryContents = player.getInventory().getContents().clone();
        this.armorContents = player.getInventory().getArmorContents().clone();
        this.offHand = player.getInventory().getItemInOffHand().clone();
        this.location = player.getLocation().clone();
        this.gameMode = player.getGameMode();
        this.foodLevel = player.getFoodLevel();
        this.health = player.getHealth();
        this.exp = player.getExp();
        this.expLevel = player.getLevel();
    }

    public void restore(Player player) {
        player.getInventory().setContents(inventoryContents);
        player.getInventory().setArmorContents(armorContents);
        player.getInventory().setItemInOffHand(offHand);
        player.teleport(location);
        player.setGameMode(gameMode);
        player.setFoodLevel(foodLevel);
        player.setHealth(health);
        player.setExp(exp);
        player.setLevel(expLevel);
    }

    public Location getLocation() {
        return location;
    }
}
