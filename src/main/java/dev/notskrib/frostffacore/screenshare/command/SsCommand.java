package dev.notskrib.frostffacore.screenshare.command;

import dev.notskrib.frostffacore.screenshare.config.ScreenShareConfig;
import dev.notskrib.frostffacore.screenshare.session.SessionManager;
import dev.notskrib.frostffacore.screenshare.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /ss <player>} toggles a screen-share session; {@code /ss reload} reloads
 * the config. A session can be ended even if the target is offline.
 */
public final class SsCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final SessionManager sessionManager;
    private final ScreenShareConfig config;

    public SsCommand(Plugin plugin, SessionManager sessionManager, ScreenShareConfig config) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("frostffa.screenshare.use")) {
            sender.sendMessage(message(config.msgNoPermission()));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(message(config.msgUsage()));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("frostffa.screenshare.reload")) {
                sender.sendMessage(message(config.msgNoPermission()));
                return true;
            }
            config.reload();
            sender.sendMessage(message(config.msgReloaded()));
            return true;
        }

        String name = args[0];
        Player online = Bukkit.getPlayerExact(name);
        UUID targetId = online != null
                ? online.getUniqueId()
                : Bukkit.getOfflinePlayer(name).getUniqueId();

        if (sessionManager.isDetained(targetId)) {
            sessionManager.release(targetId);
            sender.sendMessage(message(config.msgReleased().replace("%player%", name)));
            plugin.getLogger().info(sender.getName() + " released " + name + " from screen share.");
            return true;
        }

        if (online == null) {
            sender.sendMessage(message(config.msgPlayerNotFound().replace("%player%", name)));
            return true;
        }
        if (online.hasPermission("frostffa.screenshare.exempt")) {
            sender.sendMessage(message(config.msgTargetExempt().replace("%player%", online.getName())));
            return true;
        }
        if (config.customRoomEnabled() && sessionManager.hasActiveSession()) {
            sender.sendMessage(message(config.msgRoomOccupied()));
            return true;
        }

        if (!sessionManager.start(online, sender)) {
            sender.sendMessage(message("&cCould not start screen share (see console)."));
            return true;
        }
        sender.sendMessage(message(config.msgStarted().replace("%player%", online.getName())));
        plugin.getLogger().info(sender.getName() + " put " + online.getName() + " into screen share.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        options.add("reload");
        for (Player player : Bukkit.getOnlinePlayers()) {
            options.add(player.getName());
        }
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(option);
            }
        }
        return matches;
    }

    private Component message(String body) {
        return Text.legacy(config.prefix() + body);
    }
}
