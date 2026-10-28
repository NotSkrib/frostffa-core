package dev.notskrib.frostffacore.screenshare.listener;

import dev.notskrib.frostffacore.screenshare.box.BoxAllocator;
import dev.notskrib.frostffacore.screenshare.config.ScreenShareConfig;
import dev.notskrib.frostffacore.screenshare.menu.ConfirmMenu;
import dev.notskrib.frostffacore.screenshare.menu.ScreenShareMenu;
import dev.notskrib.frostffacore.screenshare.session.ScreenShareSession;
import dev.notskrib.frostffacore.screenshare.session.SessionManager;
import dev.notskrib.frostffacore.screenshare.util.Text;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.UUID;

/** Keeps the detainee inside their cell and strips their ability to interact. */
public final class LockdownListener implements Listener {

    private final Plugin plugin;
    private final SessionManager sessionManager;
    private final ScreenShareConfig config;

    public LockdownListener(Plugin plugin, SessionManager sessionManager, ScreenShareConfig config) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.config = config;
    }

    private boolean detained(Player player) {
        return sessionManager.isDetained(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        Player player = event.getPlayer();
        ScreenShareSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        // Detainees may move normally inside the screen-share room. In generated
        // boxes, the boundary check below still prevents escaping the cell.
        if (config.allowMovement() && config.customRoomEnabled()) {
            return;
        }
        if (config.customRoomEnabled()) {
            return;
        }
        Location to = event.getTo();
        if (sessionManager.allocator().contains(session.slot(), to)) {
            return;
        }
        if (config.escapeBan()) {
            sessionManager.punish(player.getUniqueId(), config.escapeCommand(), config.escapeReason());
            return;
        }
        Location stand = sessionManager.allocator().standLocation(session.slot());
        if (stand != null) {
            event.setTo(stand);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        ScreenShareSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null || event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        if (config.customRoomEnabled()) {
            // No grid box to check containment against — the custom room has no known bounds,
            // so any non-plugin teleport attempt while detained is simply refused outright.
            event.setCancelled(true);
            return;
        }
        if (!sessionManager.allocator().contains(session.slot(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (detained(player) && config.onQuitBan()) {
            sessionManager.punish(player.getUniqueId(), config.onQuitCommand(), config.onQuitReason());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (detained(player)) {
            player.getScheduler().runDelayed(plugin, task -> sessionManager.reassert(player), null, 20L);
            return;
        }
        // Not detained: if the plugin banned them earlier, send them to the rejoin spot.
        sessionManager.consumeRejoin(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!detained(player) || !config.blockCommands() || player.hasPermission("frostffa.screenshare.bypass")) {
            return;
        }
        String root = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (config.commandWhitelist().contains(root)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(Text.legacy(config.prefix() + config.msgDetainedBlocked()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (detained(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (detained(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (detained(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ScreenShareSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);

        Block clicked = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null) {
            return;
        }
        BoxAllocator allocator = sessionManager.allocator();
        int x = clicked.getX();
        int y = clicked.getY();
        int z = clicked.getZ();
        if (BoxAllocator.sameBlock(allocator.admitButton(session.slot()), x, y, z)) {
            sessionManager.choose(player, ConfirmMenu.Action.ADMIT);
        } else if (BoxAllocator.sameBlock(allocator.refuseButton(session.slot()), x, y, z)) {
            sessionManager.choose(player, ConfirmMenu.Action.REFUSE);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && detained(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDealDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && detained(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !detained(player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ScreenShareMenu || holder instanceof ConfirmMenu) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (detained(player) && !sessionManager.isInternalGamemodeChange(id)) {
            event.setCancelled(true);
        }
    }
}
