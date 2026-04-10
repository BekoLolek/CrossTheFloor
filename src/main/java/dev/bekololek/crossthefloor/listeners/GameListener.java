package dev.bekololek.crossthefloor.listeners;

import com.terranova.versionadapter.api.Materials;
import dev.bekololek.crossthefloor.Main;
import dev.bekololek.crossthefloor.managers.GameManager;
import dev.bekololek.crossthefloor.models.Arena;
import dev.bekololek.crossthefloor.models.GameSession;
import dev.bekololek.crossthefloor.utils.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {

    private final Main plugin;
    private final GameManager gameManager;

    public GameListener(Main plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.isInGame(player)) return;

        GameSession session = gameManager.getSession(player);
        if (session == null) return;

        Arena arena = session.getArena();
        int fallThreshold = plugin.getConfig().getInt("fall-threshold", 3);

        // Fall detection: works during COUNTDOWN and PLAYING
        if (session.getState() == GameSession.State.COUNTDOWN ||
            session.getState() == GameSession.State.PLAYING) {
            if (player.getLocation().getY() < arena.getCornerY() - fallThreshold) {
                // During countdown, just teleport back silently
                if (session.getState() == GameSession.State.COUNTDOWN) {
                    player.teleport(arena.getStartSpawn());
                } else {
                    gameManager.handleFall(player);
                }
                return;
            }
        }

        // Finish detection: only during PLAYING and not already finished
        if (session.getState() == GameSession.State.PLAYING) {
            if (!session.getFinishedPlayers().contains(player.getUniqueId()) &&
                arena.isPastFinish(player.getLocation())) {
                gameManager.handleFinish(player);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.isInGame(player)) return;

        GameSession session = gameManager.getSession(player);
        if (session == null) return;

        // Only handle hotbar items in WAITING state
        if (session.getState() != GameSession.State.WAITING) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);

        ItemStack item = event.getItem();
        if (item == null) return;

        Material limeDye = Materials.get("LIME_DYE");
        Material redDye = Materials.get("RED_DYE");

        if (item.getType() == limeDye) {
            gameManager.setReady(player);
        } else if (item.getType() == redDye) {
            gameManager.leaveArena(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (gameManager.isInGame(event.getPlayer())) {
            gameManager.leaveArena(event.getPlayer());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!gameManager.isInGame(player)) return;
        // Cancel all damage in the game (fall damage, etc)
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (gameManager.isInGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
