package dev.notskrib.frostffacore.bounty;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import dev.notskrib.frostffacore.economy.EconomyAdmin;
import dev.notskrib.frostffacore.economy.Points;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BountyCommand implements CommandExecutor, TabCompleter {

    private final FrostFFACore plugin;

    public BountyCommand(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.reply(sender, Palette.l(Palette.INK, "players only."));
            return true;
        }
        if (!player.hasPermission("frostffa.bounty.use")) {
            MessageUtil.sendError(player, "You don't have permission.");
            return true;
        }

        if (args.length == 0) {
            new BountyGui(this.plugin).openNextTick(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("top")) {
            this.showTop(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("check")) {
            this.showCheck(player, args.length > 1 ? args[1] : player.getName());
            return true;
        }

        if (args.length < 2) {
            MessageUtil.reply(player, Palette.l(Palette.INK, "Usage: ")
                + Palette.c(Palette.GLACIER, "/bounty <player> <amount>"));
            return true;
        }

        OfflinePlayer target = EconomyAdmin.resolve(this.plugin, args[0]);
        int amount = Points.parse(args[1]);
        if (amount <= 0) {
            MessageUtil.sendError(player, "'" + args[1] + "' is not a valid amount.");
            return true;
        }

        reportPlace(player, this.plugin.getBountyManager().place(player, target, amount), args[0], amount);
        return true;
    }

    private void showTop(Player player) {
        List<Bounty> top = this.plugin.getBountyManager().topByPool(10);
        MessageUtil.reply(player, Palette.b(Palette.BRAND, "biggest bounties"));
        if (top.isEmpty()) {
            MessageUtil.reply(player, Palette.l(Palette.DIM, "  nobody has a bounty right now."));
        }
        for (int i = 0; i < top.size(); i++) {
            Bounty bounty = top.get(i);
            OfflinePlayer offline = this.plugin.getServer().getOfflinePlayer(bounty.target());
            MessageUtil.reply(player, Palette.c(Palette.DIM, "  " + (i + 1) + ".") + " "
                + Palette.c(Palette.GLACIER, offline.getName()) + "  "
                + Points.colored(bounty.pool(), this.plugin.getEconomyManager()));
        }
    }

    private void showCheck(Player player, String name) {
        OfflinePlayer target = EconomyAdmin.resolve(this.plugin, name);
        if (target == null) {
            MessageUtil.sendError(player, "Never seen a player called '" + name + "'.");
            return;
        }
        int pool = this.plugin.getBountyManager().poolOf(target.getUniqueId());
        if (pool <= 0) {
            MessageUtil.reply(player, Palette.c(Palette.GLACIER, target.getName())
                + Palette.l(Palette.INK, " has no bounty."));
            return;
        }
        Bounty bounty = this.plugin.getBountyManager().bountyOn(target.getUniqueId());
        MessageUtil.reply(player, Palette.c(Palette.GLACIER, target.getName())
            + Palette.l(Palette.INK, " has a bounty of ") + Points.colored(pool, this.plugin.getEconomyManager())
            + Palette.l(Palette.INK, " from " + (bounty != null ? bounty.contributorCount() : 0) + " contributor(s)."));
    }

    /** Shared place-result feedback for the command and {@link BountyGui}'s chat prompt. */
    public static void reportPlace(Player player, BountyManager.PlaceResult result, String targetName, int amount) {
        switch (result) {
            case OK -> MessageUtil.sendSuccess(player, "Placed " + Points.format(amount) + " on " + targetName + ".");
            case SELF -> MessageUtil.sendError(player, "You can't bounty yourself.");
            case TARGET_UNKNOWN -> MessageUtil.sendError(player, "Never seen a player called '" + targetName + "'.");
            case TARGET_AFK -> MessageUtil.sendError(player, targetName + " is AFK — you can't bounty them right now.");
            case BELOW_MINIMUM -> MessageUtil.sendError(player, "That's below the minimum bounty.");
            case POOL_CAP -> MessageUtil.sendError(player, "That bounty is already at the pool cap.");
            case SAME_CONNECTION -> MessageUtil.sendError(player, "That account shares your connection — blocked.");
            case INSUFFICIENT_FUNDS -> MessageUtil.sendError(player, "You can't afford that.");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("top", "check"));
            for (Player online : this.plugin.getServer().getOnlinePlayers()) options.add(online.getName());
            options.removeIf(option -> !option.toLowerCase().startsWith(args[0].toLowerCase()));
            return options;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            List<String> names = new ArrayList<>();
            for (Player online : this.plugin.getServer().getOnlinePlayers()) names.add(online.getName());
            return names;
        }
        if (args.length == 2) return List.of("100", "500", "1000", "5k");
        return List.of();
    }
}
