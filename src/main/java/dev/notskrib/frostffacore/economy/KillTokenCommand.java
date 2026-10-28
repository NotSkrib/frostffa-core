package dev.notskrib.frostffacore.economy;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class KillTokenCommand implements CommandExecutor {
    private final FrostFFACore plugin;

    public KillTokenCommand(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("frostffa.killtoken.admin")) {
            MessageUtil.reply(sender, "You do not have permission to manage kill tokens.");
            return true;
        }
        if (args.length < 2 || args[0].equalsIgnoreCase("help")) {
            MessageUtil.reply(sender, "/killtoken give <player> <amount>");
            MessageUtil.reply(sender, "/killtoken remove <player> <amount>");
            MessageUtil.reply(sender, "/killtoken balance <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            MessageUtil.reply(sender, "That player must be online.");
            return true;
        }
        if (args[0].equalsIgnoreCase("balance")) {
            MessageUtil.reply(sender, target.getName() + " has "
                + plugin.getEconomyManager().balance(target) + " kill tokens.");
            return true;
        }
        if (args.length < 3) {
            MessageUtil.reply(sender, "Enter a valid amount.");
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            MessageUtil.reply(sender, "Enter a valid whole-number amount.");
            return true;
        }
        if (amount <= 0) {
            MessageUtil.reply(sender, "Amount must be greater than zero.");
            return true;
        }

        EconomyManager economy = plugin.getEconomyManager();
        if (args[0].equalsIgnoreCase("give")) {
            economy.deposit(target.getUniqueId(), amount, "admin:killtoken-give");
            MessageUtil.reply(sender, "Gave " + amount + " kill token(s) to " + target.getName() + ".");
            return true;
        }
        if (args[0].equalsIgnoreCase("remove")) {
            if (!economy.withdraw(target.getUniqueId(), amount, "admin:killtoken-remove")) {
                MessageUtil.reply(sender, target.getName() + " does not have enough kill tokens.");
                return true;
            }
            MessageUtil.reply(sender, "Removed " + amount + " kill token(s) from " + target.getName() + ".");
            return true;
        }
        MessageUtil.reply(sender, "Unknown action. Use give, remove, or balance.");
        return true;
    }
}
