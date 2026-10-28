package dev.notskrib.frostffacore.maintenance;

import dev.notskrib.frostffacore.FrostFFACore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Blocks joins while maintenance mode is on, except for staff. Uses PlayerLoginEvent rather
 * than AsyncPlayerPreLoginEvent — the Player object (and therefore hasPermission) isn't
 * available yet at pre-login, but permissions are already attached by the time LoginEvent
 * fires, before the player actually enters the world.
 */
public final class MaintenanceListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final FrostFFACore plugin;

    public MaintenanceListener(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onLogin(PlayerLoginEvent event) {
        MaintenanceManager manager = this.plugin.getMaintenanceManager();
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        if (manager.bypasses(player)) return;

        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, MM.deserialize(manager.joinDeniedMessage()));
    }
}
