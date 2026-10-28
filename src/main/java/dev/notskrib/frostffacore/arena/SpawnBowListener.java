package dev.notskrib.frostffacore.arena;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;

/** Bows (and crossbows) can't be fired inside the spawn-protected area. */
public class SpawnBowListener implements Listener {

    private final FrostFFACore plugin;

    public SpawnBowListener(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!this.plugin.getConfig().getBoolean("building.block-bows-at-spawn", true)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.hasPermission("frostffa.buildpermission")) return;
        if (!this.plugin.getBuildGate().denied(player.getLocation())) return;

        event.setCancelled(true);
        MessageUtil.sendError(player, Palette.sc("You cannot shoot bows here."));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
    }
}
