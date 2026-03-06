package dev.bekololek.crossthefloor.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class MessageUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {}

    public static Component colorize(String text) {
        return LEGACY.deserialize(text);
    }

    public static void send(Player player, String prefix, String message) {
        player.sendMessage(colorize(prefix + message));
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
        );
        player.showTitle(Title.title(colorize(title), colorize(subtitle), times));
    }

    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(colorize(message));
    }

    /**
     * Format a Material name for display: RED_CONCRETE -> Red Concrete
     */
    public static String formatMaterialName(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(parts[i].charAt(0)).append(parts[i].substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
