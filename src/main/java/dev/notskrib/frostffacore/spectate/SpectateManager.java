package dev.notskrib.frostffacore.spectate;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import dev.notskrib.frostffacore.vanish.VanishManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpectateManager implements IManager {

    public static final String SPECTATE_PERMISSION = "frostffa.spectate.use";

    private final FrostFFACore plugin;
    private final VanishManager vanishManager;
    private final Map<UUID, UUID> activeSessions = new ConcurrentHashMap<>();
    private BukkitTask followTask;

    public SpectateManager(FrostFFACore plugin, VanishManager vanishManager) {
        this.plugin = plugin;
        this.vanishManager = vanishManager;
    }

    @Override
    public void onEnable() {
        this.followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickFollowers, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
        activeSessions.clear();
    }

    public boolean startSpectating(Player spectator, Player target) {
        if (spectator.getUniqueId().equals(target.getUniqueId())) {
            MessageUtil.sendError(spectator, "You cannot spectate yourself.");
            return false;
        }

        stopSpectating(spectator, false);

        if (!vanishManager.isVanished(spectator.getUniqueId())) {
            vanishManager.vanish(spectator);
            MessageUtil.sendSuccess(spectator, "Auto-vanished while starting spectate mode.");
        }

        spectator.teleport(target.getLocation());
        activeSessions.put(spectator.getUniqueId(), target.getUniqueId());
        MessageUtil.sendSuccess(spectator, "Now spectating <white>" + target.getName() + "</white>.");
        return true;
    }

    public boolean stopSpectating(Player spectator, boolean notify) {
        UUID previousTargetId = activeSessions.remove(spectator.getUniqueId());
        if (previousTargetId != null) {
            if (notify) {
                MessageUtil.sendInfo(spectator, "You stopped spectating.");
            }
            return true;
        }
        return false;
    }

    public boolean isSpectating(UUID spectatorId) {
        return activeSessions.containsKey(spectatorId);
    }

    public UUID getTarget(UUID spectatorId) {
        return activeSessions.get(spectatorId);
    }

    public void forget(UUID uuid) {
        activeSessions.remove(uuid);
        activeSessions.values().removeIf(targetId -> targetId.equals(uuid));
    }

    private void tickFollowers() {
        for (Map.Entry<UUID, UUID> entry : activeSessions.entrySet()) {
            UUID spectatorId = entry.getKey();
            UUID targetId = entry.getValue();

            Player spectator = Bukkit.getPlayer(spectatorId);
            Player target = Bukkit.getPlayer(targetId);

            if (spectator == null || !spectator.isOnline()) {
                activeSessions.remove(spectatorId);
                continue;
            }

            if (target == null || !target.isOnline()) {
                activeSessions.remove(spectatorId);
                MessageUtil.sendInfo(spectator, "Target logged off. Spectate session ended.");
                continue;
            }

            if (!spectator.getWorld().equals(target.getWorld()) ||
                    spectator.getLocation().distance(target.getLocation()) > 8.0) {
                spectator.teleport(target.getLocation());
            }
        }
    }
}
