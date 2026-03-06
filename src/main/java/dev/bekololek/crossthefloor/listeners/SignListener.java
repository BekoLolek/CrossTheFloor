package dev.bekololek.crossthefloor.listeners;

import dev.bekololek.crossthefloor.Main;
import dev.bekololek.crossthefloor.managers.ArenaManager;
import dev.bekololek.crossthefloor.managers.GameManager;
import dev.bekololek.crossthefloor.models.Arena;
import dev.bekololek.crossthefloor.utils.MessageUtil;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;

public class SignListener implements Listener {

    private final Main plugin;
    private final ArenaManager arenaManager;
    private final GameManager gameManager;

    public SignListener(Main plugin, ArenaManager arenaManager, GameManager gameManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        String line0 = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.line(0));

        if (!line0.equalsIgnoreCase("[CTF]")) return;

        String prefix = plugin.getConfig().getString("prefix", "&6[CTF] &r");

        if (!player.hasPermission("crossthefloor.admin")) {
            MessageUtil.send(player, prefix, "&cNo permission to create CTF signs.");
            return;
        }

        String arenaName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.line(1));

        if (arenaName.isBlank()) {
            MessageUtil.send(player, prefix, "&cWrite the arena name on line 2.");
            return;
        }

        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            MessageUtil.send(player, prefix, "&cArena '" + arenaName + "' not found.");
            return;
        }

        // Format the sign
        event.line(0, MessageUtil.colorize("&1[CTF]"));
        event.line(1, MessageUtil.colorize("&0" + arena.getName()));
        event.line(2, MessageUtil.colorize("&2" + capitalize(arena.getDifficulty().getKey())));
        event.line(3, MessageUtil.colorize("&5Open &8| &50 players"));

        // Register sign location
        arenaManager.addSign(arena, event.getBlock().getLocation());
        MessageUtil.send(player, prefix, "&aJoin sign created for arena '&e" + arena.getName() + "&a'.");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!(block.getState() instanceof Sign)) return;

        Player player = event.getPlayer();
        Arena arena = arenaManager.getArenaBySign(block.getLocation());
        if (arena == null) return;

        event.setCancelled(true);

        String prefix = plugin.getConfig().getString("prefix", "&6[CTF] &r");
        if (!player.hasPermission("crossthefloor.play")) {
            MessageUtil.send(player, prefix, "&cNo permission to join games.");
            return;
        }

        gameManager.joinArena(player, arena);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
