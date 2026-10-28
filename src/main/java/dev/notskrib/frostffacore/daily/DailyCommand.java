package dev.notskrib.frostffacore.daily;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DailyCommand implements CommandExecutor, TabCompleter {

    private final FrostFFACore plugin;

    public DailyCommand(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.reply(sender, Palette.l(Palette.INK, "players only."));
            return true;
        }

        if (args.length == 0) {
            new DailyGui(this.plugin).openNextTick(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("frostffa.daily.admin")) {
                MessageUtil.sendError(player, "You don't have permission.");
                return true;
            }
            if (args.length < 2) {
                MessageUtil.reply(player, Palette.l(Palette.INK, "Usage: ")
                    + Palette.c(Palette.GLACIER, "/daily admin <tier id>"));
                return true;
            }
            int tierId;
            try {
                tierId = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                MessageUtil.sendError(player, "'" + args[1] + "' is not a tier id.");
                return true;
            }
            if (!this.plugin.getDailyManager().tiers().containsKey(tierId)) {
                MessageUtil.sendError(player, "No tier " + tierId + " in daily.yml.");
                return true;
            }
            new DailyAdminGui(this.plugin, tierId).openNextTick(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("frostffa.daily.admin")) {
                MessageUtil.sendError(player, "You don't have permission.");
                return true;
            }
            this.plugin.getDailyManager().reload();
            MessageUtil.sendSuccess(player, "daily.yml reloaded.");
            return true;
        }

        MessageUtil.reply(player, Palette.l(Palette.INK, "Usage: ")
            + Palette.c(Palette.GLACIER, "/daily [admin <tier id>|reload]"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("admin", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            List<String> ids = new ArrayList<>();
            this.plugin.getDailyManager().tiers().keySet().forEach(id -> ids.add(String.valueOf(id)));
            return ids;
        }
        return List.of();
    }
}
