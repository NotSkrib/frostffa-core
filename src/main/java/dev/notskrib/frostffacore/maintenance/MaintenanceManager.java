package dev.notskrib.frostffacore.maintenance;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import org.bukkit.entity.Player;

/**
 * Maintenance toggle: {@link MaintenanceListener} refuses non-staff logins and
 * {@link MaintenanceCommand} kicks online non-bypass players. Persisted to config.yml so a
 * reboot doesn't silently reopen the server.
 */
public class MaintenanceManager implements IManager {

    private final FrostFFACore plugin;
    private boolean enabled;
    private String joinDeniedMessage;

    public MaintenanceManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.readConfig();
    }

    @Override
    public void onDisable() {}

    public void reload() {
        this.readConfig();
    }

    private void readConfig() {
        this.enabled = this.plugin.getConfig().getBoolean("maintenance.enabled", false);
        this.joinDeniedMessage = this.plugin.getConfig().getString("maintenance.join-denied-message",
            "<red><bold>Server is in maintenance.</bold></red>\n<gray>Check back soon.</gray>");
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public String joinDeniedMessage() {
        return this.joinDeniedMessage;
    }

    /** Flips the toggle and writes it back to config.yml immediately. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.plugin.getConfig().set("maintenance.enabled", enabled);
        this.plugin.saveConfig();
    }

    public boolean bypasses(Player player) {
        return player.isOp() || player.hasPermission("frostffa.maintenance.bypass");
    }
}
