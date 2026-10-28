package dev.notskrib.frostffacore.shop;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /store — pick a shop to browse. /store <shop> — jump straight into one.
 * /store admin — create/delete shops and edit their items (permission-gated).
 */
public class ShopCommand implements CommandExecutor {

    private final FrostFFACore plugin;

    public ShopCommand(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.replyError(sender, "Players only.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("frostffa.shop.admin")) {
                MessageUtil.sendError(player, "You don't have permission.");
                return true;
            }
            if (args.length > 1 && this.plugin.getShopManager().hasShop(args[1])) {
                new ShopAdminGui(this.plugin, args[1]).open(player);
            } else {
                new ShopAdminListGui(this.plugin).open(player);
            }
            return true;
        }

        if (args.length > 0 && this.plugin.getShopManager().hasShop(args[0])) {
            new ShopGui(this.plugin, args[0]).open(player);
            return true;
        }

        new ShopListGui(this.plugin).open(player);
        return true;
    }
}
