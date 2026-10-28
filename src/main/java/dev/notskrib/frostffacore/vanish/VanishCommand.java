package dev.notskrib.frostffacore.vanish;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VanishCommand implements CommandExecutor, TabCompleter {

    private final FrostFFACore plugin;
    private final VanishManager manager;

    public VanishCommand(FrostFFACore plugin, VanishManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(VanishManager.USE_PERMISSION)) {
            MessageUtil.replyError(sender, "You do not have permission to use vanish.");
            return true;
        }

        if (args.length > 0) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                MessageUtil.replyError(sender, "Player '<white>" + args[0] + "</white>' is not online.");
                return true;
            }
            boolean nowVanished = manager.toggle(target);
            if (nowVanished) {
                MessageUtil.replySuccess(sender, "Vanished <white>" + target.getName() + "</white>.");
                MessageUtil.sendSuccess(target, "You are now vanished.");
            } else {
                MessageUtil.replySuccess(sender, "Unvanished <white>" + target.getName() + "</white>.");
                MessageUtil.sendInfo(target, "You are no longer vanished.");
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            MessageUtil.replyError(sender, "Console must specify a player: /vanish <player>");
            return true;
        }

        boolean nowVanished = manager.toggle(player);
        if (nowVanished) {
            MessageUtil.sendSuccess(player, "You are now vanished.");
        } else {
            MessageUtil.sendInfo(player, "You are no longer vanished.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(VanishManager.USE_PERMISSION)) {
            List<String> matches = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    matches.add(p.getName());
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }
}
