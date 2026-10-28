package dev.notskrib.frostffacore.maintenance;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /maintenance [on|off] — toggles maintenance mode, kicking everyone without bypass. */
public final class MaintenanceCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final FrostFFACore plugin;

    public MaintenanceCommand(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MaintenanceManager manager = this.plugin.getMaintenanceManager();

        boolean next;
        if (args.length > 0 && args[0].equalsIgnoreCase("on")) {
            next = true;
        } else if (args.length > 0 && args[0].equalsIgnoreCase("off")) {
            next = false;
        } else {
            next = !manager.isEnabled();
        }

        manager.setEnabled(next);

        if (next) {
            for (Player online : this.plugin.getServer().getOnlinePlayers()) {
                if (!manager.bypasses(online)) {
                    online.kick(MM.deserialize(manager.joinDeniedMessage()));
                }
            }
        }

        MessageUtil.replySuccess(sender, "Maintenance mode is now " + (next ? "ON" : "OFF") + ".");
        return true;
    }
}
