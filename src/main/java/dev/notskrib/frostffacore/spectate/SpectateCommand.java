package dev.notskrib.frostffacore.spectate;

import dev.notskrib.frostffacore.FrostFFACore;
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

public final class SpectateCommand implements CommandExecutor, TabCompleter {

    private final FrostFFACore plugin;
    private final SpectateManager manager;

    public SpectateCommand(FrostFFACore plugin, SpectateManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /spectate.");
            return true;
        }

        if (!player.hasPermission(SpectateManager.SPECTATE_PERMISSION)) {
            MessageUtil.sendError(player, "You do not have permission to use spectate.");
            return true;
        }

        if (args.length == 0) {
            if (manager.isSpectating(player.getUniqueId())) {
                manager.stopSpectating(player, true);
            } else {
                MessageUtil.sendError(player, "Usage: /spectate <player|off>");
            }
            return true;
        }

        String sub = args[0];
        if (sub.equalsIgnoreCase("off") || sub.equalsIgnoreCase("stop")) {
            if (!manager.isSpectating(player.getUniqueId())) {
                MessageUtil.sendError(player, "You are not currently spectating anyone.");
                return true;
            }
            manager.stopSpectating(player, true);
            return true;
        }

        Player target = Bukkit.getPlayer(sub);
        if (target == null) {
            MessageUtil.sendError(player, "Player '<white>" + sub + "</white>' is not online.");
            return true;
        }

        manager.startSpectating(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(SpectateManager.SPECTATE_PERMISSION)) {
            List<String> matches = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            if ("off".startsWith(prefix)) matches.add("off");
            if ("stop".startsWith(prefix)) matches.add("stop");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().equals(sender.getName()) && p.getName().toLowerCase().startsWith(prefix)) {
                    matches.add(p.getName());
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }
}
