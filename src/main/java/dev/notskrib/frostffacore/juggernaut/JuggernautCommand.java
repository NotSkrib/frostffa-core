package dev.notskrib.frostffacore.juggernaut;

import dev.notskrib.frostffacore.FrostFFACore;
import java.util.Collections;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class JuggernautCommand implements CommandExecutor, TabCompleter {
    private final FrostFFACore plugin;
    public JuggernautCommand(FrostFFACore plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("frostffa.juggernaut.admin")) {
            sender.sendMessage("You do not have permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("/juggernaut <start|stop|status|kit [extract]>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "start" -> sender.sendMessage(plugin.getJuggernautManager().start()
                ? "Juggernaut event started." : "Could not start: need at least 2 non-AFK players, or an event is already running.");
            case "stop" -> { plugin.getJuggernautManager().stop(); sender.sendMessage("Juggernaut event stopped."); }
            case "status" -> sender.sendMessage(plugin.getJuggernautManager().status());
            case "kit" -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage("Players only.");
                } else if (args.length >= 2 && args[1].equalsIgnoreCase("extract")) {
                    boolean saved = plugin.getJuggernautManager().extractKitFromInventory(player);
                    sender.sendMessage(saved
                        ? "Juggernaut kit set from your current inventory."
                        : "Could not save the Juggernaut kit.");
                } else {
                    plugin.getJuggernautManager().openKitEditor(player);
                }
            }
            default -> sender.sendMessage("/juggernaut <start|stop|status|kit [extract]>");
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("start", "stop", "status", "kit").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("kit")) {
            return "extract".startsWith(args[1].toLowerCase()) ? List.of("extract") : Collections.emptyList();
        }
        return Collections.emptyList();
    }
}
