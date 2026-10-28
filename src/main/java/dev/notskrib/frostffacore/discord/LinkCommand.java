package dev.notskrib.frostffacore.discord;

import dev.notskrib.frostffacore.FrostFFACore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

public final class LinkCommand implements CommandExecutor {
    private final FrostFFACore plugin;
    public LinkCommand(FrostFFACore plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only a player can create a Discord link code.");
            return true;
        }
        if (plugin.getDiscordLinkManager().isLinked(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Your Minecraft account is already linked.");
            return true;
        }
        if (args.length > 0) {
            var result = plugin.getDiscordLinkManager().linkFromMinecraft(args[0], player.getUniqueId());
            switch (result) {
                case SUCCESS -> player.sendMessage(ChatColor.GREEN
                    + "Your Discord account is now linked.");
                case EXPIRED -> player.sendMessage(ChatColor.RED
                    + "That code has expired. Generate a new one with Discord's \"Generate a code\" button.");
                case MINECRAFT_ALREADY_LINKED -> player.sendMessage(ChatColor.YELLOW
                    + "Your Minecraft account is already linked.");
                case DISCORD_ALREADY_LINKED -> player.sendMessage(ChatColor.RED
                    + "That Discord account is already linked to a different Minecraft account.");
                default -> player.sendMessage(ChatColor.RED
                    + "That code is invalid. Check it and try again.");
            }
            return true;
        }
        String code = plugin.getDiscordLinkManager().createCode(player);
        player.sendMessage(Component.text("Your one-time Discord link code: ")
            .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
            .append(Component.text(code).color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
                .clickEvent(ClickEvent.copyToClipboard(code))));
        player.sendMessage(ChatColor.GRAY + "Use it with the configured Discord bot within "
            + plugin.getDiscordLinkManager().expirySeconds() + " seconds.");
        return true;
    }
}
