package dev.notskrib.frostffacore.screenshare.menu;

import dev.notskrib.frostffacore.screenshare.config.ScreenShareConfig;
import dev.notskrib.frostffacore.screenshare.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

/**
 * Handles the confirmation chest ({@link ConfirmMenu}) used by both the wall
 * buttons and the optional CHEST-mode forced menu ({@link ScreenShareMenu}).
 */
public final class MenuListener implements Listener {

    private final Plugin plugin;
    private final SessionManager sessionManager;
    private final ScreenShareConfig config;

    public MenuListener(Plugin plugin, SessionManager sessionManager, ScreenShareConfig config) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.config = config;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (holder instanceof ScreenShareMenu) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == ScreenShareMenu.clamp(config.admitSlot())) {
                sessionManager.choose(player, ConfirmMenu.Action.ADMIT);
            } else if (slot == ScreenShareMenu.clamp(config.refuseSlot())) {
                sessionManager.choose(player, ConfirmMenu.Action.REFUSE);
            }
            return;
        }

        if (holder instanceof ConfirmMenu confirmMenu) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == ScreenShareMenu.clamp(config.confirmSlot())) {
                sessionManager.confirm(player, confirmMenu.action());
            } else if (slot == ScreenShareMenu.clamp(config.cancelSlot())) {
                if (config.menuChest()) {
                    open(player, new ScreenShareMenu(config));
                } else {
                    player.getScheduler().run(plugin, task -> player.closeInventory(), null);
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // Only the CHEST-mode forced menu is re-opened on close; the confirm chest
        // and the BUTTONS mode let the detainee simply click again.
        if (!config.menuChest() || !config.forceMenu()) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ScreenShareMenu)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)
                || !sessionManager.isDetained(player.getUniqueId())) {
            return;
        }
        player.getScheduler().runDelayed(plugin, task -> {
            if (sessionManager.isDetained(player.getUniqueId())) {
                player.openInventory(new ScreenShareMenu(config).getInventory());
            }
        }, null, 2L);
    }

    private void open(Player player, InventoryHolder menu) {
        player.getScheduler().run(plugin, task -> player.openInventory(menu.getInventory()), null);
    }
}
