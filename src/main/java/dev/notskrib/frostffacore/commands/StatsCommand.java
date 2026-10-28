package dev.notskrib.frostffacore.commands;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import dev.notskrib.frostffacore.rank.RankTier;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class StatsCommand implements CommandExecutor {
    private final FrostFFACore plugin;

    public StatsCommand(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        OfflinePlayer target;
        if (args.length > 0) {
            target = this.plugin.getServer().getPlayerExact(args[0]);
            if (target == null) target = this.plugin.getServer().getOfflinePlayer(args[0]);
            if (target.getName() == null || args[0].startsWith("<@") || args[0].matches("\\d{15,22}")) {
                java.util.UUID linked = this.plugin.getDiscordLinkManager().resolveDiscord(args[0]);
                if (linked != null) target = this.plugin.getServer().getOfflinePlayer(linked);
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            MessageUtil.reply(sender, Palette.l(Palette.INK, "Usage: /stats <player>"));
            return true;
        }
        String name = target.getName() == null ? (args.length > 0 ? args[0] : "?") : target.getName();
        int kills = this.plugin.getKillsManager().getKills(target.getUniqueId());
        int deaths = this.plugin.getKillsManager().getDeaths(target.getUniqueId());
        int streak = target.isOnline() ? this.plugin.getKillstreakManager().getStreak(target.getPlayer()) : 0;
        RankTier tier = this.plugin.getRankManager().tierOf(kills);
        String rank = tier == null ? "Unranked" : tier.name();
        MessageUtil.reply(sender, Palette.b(Palette.BRAND, "Frost FFA stats"));
        MessageUtil.reply(sender, Palette.l(Palette.INK, "Player: ")
            + Palette.c(Palette.GLACIER, name));
        MessageUtil.reply(sender, Palette.l(Palette.INK, "Rank: ")
            + (tier == null ? Palette.c(Palette.DIM, rank) : tier.coloredName()));
        MessageUtil.reply(sender, Palette.l(Palette.INK, "Kills: ")
            + Palette.c(Palette.GLACIER, String.valueOf(kills)));
        MessageUtil.reply(sender, Palette.l(Palette.INK, "Deaths: ")
            + Palette.c(Palette.GLACIER, String.valueOf(deaths)));
        MessageUtil.reply(sender, Palette.l(Palette.INK, "Streak: ")
            + Palette.c(Palette.GLACIER, String.valueOf(streak)));
        MessageUtil.reply(sender, Palette.l(Palette.INK, "Balance: ")
            + Palette.c(Palette.GLACIER,
                this.plugin.getEconomyManager().formatted(target.getUniqueId())));
        if (this.plugin.getDiscordLinkManager().isLinked(target.getUniqueId())) {
            MessageUtil.reply(sender, Palette.l(Palette.INK, "Discord: ")
                + Palette.c(Palette.BRAND, "Verified / linked"));
        } else {
            MessageUtil.reply(sender, Palette.l(Palette.INK, "Discord: ")
                + Palette.c(Palette.DIM, "Not linked"));
        }
        return true;
    }
}
