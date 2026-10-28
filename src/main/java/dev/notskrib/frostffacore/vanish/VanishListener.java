package dev.notskrib.frostffacore.vanish;

import dev.notskrib.frostffacore.FrostFFACore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class VanishListener implements Listener {

    private final FrostFFACore plugin;
    private final VanishManager manager;

    public VanishListener(FrostFFACore plugin, VanishManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        if (joined.hasPermission(VanishManager.SEE_PERMISSION)) {
            return;
        }
        for (UUID vanishedId : manager.vanishedIds()) {
            Player vanishedPlayer = Bukkit.getPlayer(vanishedId);
            if (vanishedPlayer != null && !vanishedPlayer.getUniqueId().equals(joined.getUniqueId())) {
                joined.hidePlayer(plugin, vanishedPlayer);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && manager.isVanished(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (isVanishedAttacker(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    private boolean isVanishedAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return manager.isVanished(player.getUniqueId());
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return manager.isVanished(shooter.getUniqueId());
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(EntityInteractEvent event) {
        if (event.getEntity() instanceof Player player && manager.isVanished(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && manager.isVanished(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
