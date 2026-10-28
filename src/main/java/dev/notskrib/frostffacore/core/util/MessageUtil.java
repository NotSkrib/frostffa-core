package dev.notskrib.frostffacore.core.util;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = Palette.PREFIX;

    private MessageUtil() {}

    public static Component parse(String message, TagResolver... resolvers) {
        String s = message
            .replace("{player}", "<player>")
            .replace("{x}", "<x>")
            .replace("{y}", "<y>")
            .replace("{z}", "<z>");
        return MM.deserialize(s, resolvers);
    }

    public static void send(Player player, String message, TagResolver... resolvers) {
        player.sendMessage(parse(PREFIX + message, resolvers));
    }

    public static void reply(CommandSender sender, String message, TagResolver... resolvers) {
        sender.sendMessage(parse(PREFIX + message, resolvers));
    }

    public static void sendRaw(Player player, String message, TagResolver... resolvers) {
        player.sendMessage(parse(message, resolvers));
    }

    public static void sendActionBar(Player player, String message, TagResolver... resolvers) {
        player.sendActionBar(parse(message, resolvers));
        yieldActionBar(player);
    }

    public static void yieldActionBar(Player player) {
        // Retained as a compatibility no-op for integrations compiled against older builds.
    }

    public static void broadcast(String message, TagResolver... resolvers) {
        Bukkit.getServer().sendMessage(parse(PREFIX + message, resolvers));
    }

    public static void broadcastRaw(String message, TagResolver... resolvers) {
        Bukkit.getServer().sendMessage(parse(message, resolvers));
    }

    /**
     * Shows a title + subtitle to every online player at once, with a short fade in/out
     * so it doesn't snap. Used for events worth interrupting the screen for — server-wide
     * announcements, not routine notices (those belong in chat/action bar).
     */
    public static void broadcastTitle(String titleMiniMessage, String subtitleMiniMessage,
                                       TagResolver... resolvers) {
        Title title = Title.title(
            parse(titleMiniMessage, resolvers),
            parse(subtitleMiniMessage, resolvers),
            Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(600)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
    }

    public static void broadcastSound(Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
        }
    }

    public static TagResolver player(String name) {
        return Placeholder.unparsed("player", name);
    }

    public static void sendError(Player player, String message) {
        send(player, "<color:" + Palette.BAD + ">" + message + "</color>");
    }

    public static void sendSuccess(Player player, String message) {
        send(player, "<color:" + Palette.BRAND + ">" + message + "</color>");
    }

    public static void sendInfo(Player player, String message) {
        send(player, "<color:" + Palette.INK + ">" + message + "</color>");
    }

    public static void replyError(CommandSender sender, String message) {
        reply(sender, "<color:" + Palette.BAD + ">" + message + "</color>");
    }

    public static void replySuccess(CommandSender sender, String message) {
        reply(sender, "<color:" + Palette.BRAND + ">" + message + "</color>");
    }
}
