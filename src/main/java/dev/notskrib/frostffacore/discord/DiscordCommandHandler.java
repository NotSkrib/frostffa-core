package dev.notskrib.frostffacore.discord;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.rank.RankTier;
import java.util.UUID;

/**
 * JDA-neutral command boundary. A future JDA listener should invoke this off-thread, then
 * marshal any Bukkit-only presentation back to the server thread.
 */
public final class DiscordCommandHandler {
    private final FrostFFACore plugin;
    public DiscordCommandHandler(FrostFFACore plugin) { this.plugin = plugin; }

    public String handle(String content, String discordUserId, boolean staffRolePresent) {
        String prefix = plugin.getConfig().getString("discord.bot-command-prefix", "!");
        if (content == null || !content.startsWith(prefix + "stats")) return null;
        if (!staffRolePresent) return "You do not have permission to use that command.";
        String[] parts = content.trim().split("\\s+");
        if (parts.length < 2) return "Usage: " + prefix + "stats <Discord mention or user ID>";
        UUID uuid = plugin.getDiscordLinkManager().resolveDiscord(parts[1]);
        if (uuid == null) return "That Discord account is not linked.";
        int kills = plugin.getKillsManager().getKills(uuid);
        int deaths = plugin.getKillsManager().getDeaths(uuid);
        RankTier tier = plugin.getRankManager().tierOf(kills);
        return "Minecraft " + uuid + " — kills: " + kills + " | deaths: " + deaths
            + " | rank: " + (tier == null ? "Unranked" : tier.name());
    }
}
