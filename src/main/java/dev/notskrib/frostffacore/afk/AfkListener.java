package dev.notskrib.frostffacore.afk;

import dev.notskrib.frostffacore.FrostFFACore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Observes player activity for {@link AfkManager} (plan §6.7). Runs at MONITOR with
 * {@code ignoreCancelled = true} and never affects the event. Chat uses Paper's
 * {@link AsyncChatEvent}, since legacy chat events may not fire with legacy decoration off.
 */
public class AfkListener implements Listener {

    private final FrostFFACore plugin;

    public AfkListener(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Ignore head-turn spam — only a real positional change counts as activity.
        if (event.getTo() == null || event.getFrom().distanceSquared(event.getTo()) <= 0.01) return;
        this.plugin.getAfkManager().touch(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        this.plugin.getAfkManager().touch(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        this.plugin.getAfkManager().touch(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        this.plugin.getAfkManager().touch(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            this.plugin.getAfkManager().touch(player.getUniqueId());
        }
    }

    /** Either side of a hit counts as activity — the attacker AND whoever got hit. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            this.plugin.getAfkManager().touch(victim.getUniqueId());
        }
        if (event.getDamager() instanceof Player attacker) {
            this.plugin.getAfkManager().touch(attacker.getUniqueId());
        }
    }
}
