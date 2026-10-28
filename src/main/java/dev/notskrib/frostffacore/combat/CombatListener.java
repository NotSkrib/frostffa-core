package dev.notskrib.frostffacore.combat;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import dev.notskrib.frostffacore.core.util.WorldGuardUtil;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CombatListener implements Listener {

    private final FrostFFACore plugin;
    private final Set<UUID> kicked = ConcurrentHashMap.newKeySet();

    public CombatListener(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            attacker = player;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        String spawnRegion = this.plugin.getConfig().getString("spawn.region", "spawn");
        if (WorldGuardUtil.isInRegion(victim.getLocation(), spawnRegion) || WorldGuardUtil.isInRegion(attacker.getLocation(), spawnRegion)) {
            event.setCancelled(true);
            MessageUtil.sendError(attacker, Palette.sc("You cannot PvP inside spawn!"));
            return;
        }
        if (!WorldGuardUtil.isPvpAllowed(victim.getLocation()) || !WorldGuardUtil.isPvpAllowed(attacker.getLocation())) {
            event.setCancelled(true);
            MessageUtil.sendError(attacker, Palette.sc("PvP is not allowed here!"));
            return;
        }
        this.plugin.getCombatManager().tag(attacker);
        this.plugin.getCombatManager().tag(victim);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        this.plugin.getCombatManager().untag(victim, true);

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)
            && this.plugin.getConfig().getBoolean("combat.untag-on-kill", true)) {
            this.plugin.getCombatManager().untag(killer, false);
        }
        // The death message itself is KillMessageManager's job — this listener only reports
        // the cause, so there is one writer of it rather than two racing at MONITOR priority.
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!this.plugin.getCombatManager().isTagged(player)) return;

        if (this.spared(player)) {
            this.plugin.getCombatManager().forceUntag(player.getUniqueId());
            return;
        }

        this.plugin.getCombatManager().markCombatLogged(player.getUniqueId());
        event.quitMessage(null);
        player.setHealth(0.0);
    }

    private boolean spared(Player player) {
        if (this.plugin.isShuttingDown() || this.plugin.getServer().isStopping()) return true;
        if (this.kicked.remove(player.getUniqueId())) {
            return !this.plugin.getConfig().getBoolean("combat.punish-on-kick", false);
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        this.kicked.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!this.plugin.getCombatManager().isTagged(player)) {
            return;
        }
        String raw = event.getMessage().substring(1);
        if (this.plugin.getCombatManager().isBlacklisted(raw)) {
            event.setCancelled(true);
            MessageUtil.sendError(player, Palette.sc("You cannot use that command while in combat!"));
        }
    }
}
