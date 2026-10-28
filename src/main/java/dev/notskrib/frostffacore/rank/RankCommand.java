package dev.notskrib.frostffacore.rank;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RankCommand implements CommandExecutor, TabCompleter {

    private final FrostFFACore plugin;

    public RankCommand(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("frostffa.rank.use")) {
            MessageUtil.reply(sender, Palette.l(Palette.BAD, "You don't have permission."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            if (!(sender instanceof Player player)) {
                MessageUtil.reply(sender, Palette.l(Palette.INK, "players only."));
                return true;
            }
            new RankGui(this.plugin).openNextTick(player);
            return true;
        }

        RankManager ranks = this.plugin.getRankManager();
        OfflinePlayer target;
        if (args.length > 0) {
            target = this.plugin.getServer().getPlayerExact(args[0]);
            if (target == null) target = this.plugin.getServer().getOfflinePlayer(args[0]);
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            MessageUtil.reply(sender, Palette.l(Palette.INK, "Usage: ")
                + Palette.c(Palette.GLACIER, "/rank [player|list]"));
            return true;
        }

        long kills = this.plugin.getKillsManager().getKills(target.getUniqueId());
        RankTier tier = ranks.tierOf(kills);
        String name = target.getName() != null ? target.getName() : args.length > 0 ? args[0] : "?";

        if (tier == null) {
            MessageUtil.reply(sender, Palette.c(Palette.GLACIER, name)
                + Palette.l(Palette.INK, " has no rank yet — ranks.yml has no tiers."));
            return true;
        }

        RankTier next = ranks.next(tier);
        MessageUtil.reply(sender, Palette.c(Palette.GLACIER, name) + Palette.l(Palette.INK, " is ")
            + tier.coloredName() + Palette.l(Palette.INK, " (" + kills + " kills)"));
        if (next != null) {
            long remaining = Math.max(0L, next.killsRequired() - kills);
            MessageUtil.reply(sender, Palette.l(Palette.INK, remaining + " kills to ") + next.coloredName());
        } else {
            MessageUtil.reply(sender, Palette.l(Palette.BRAND, "maximum rank reached."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        List<String> options = new ArrayList<>(List.of("list"));
        for (Player online : this.plugin.getServer().getOnlinePlayers()) options.add(online.getName());
        options.removeIf(option -> !option.toLowerCase().startsWith(args[0].toLowerCase()));
        return options;
    }
}
