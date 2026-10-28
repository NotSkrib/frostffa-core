package dev.notskrib.frostffacore.discord;

import dev.notskrib.frostffacore.FrostFFACore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class LinkPanelPostCommand implements CommandExecutor {
    private final FrostFFACore plugin;

    public LinkPanelPostCommand(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("frostffa.discord.panel")) {
            sender.sendMessage("You do not have permission.");
            return true;
        }
        if (this.plugin.getDiscordIntegration() == null
            || !this.plugin.getDiscordIntegration().postLinkButton()) {
            sender.sendMessage("The Discord bot is not ready, or discord.channel-id is not configured.");
            return true;
        }
        sender.sendMessage("Discord link panel posted.");
        return true;
    }
}
