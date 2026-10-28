package dev.notskrib.frostffacore.afk;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import dev.notskrib.frostffacore.core.util.LocationUtil;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * AFK detection and the idle timer (plan §6.7). Holds no persisted data, so a restart or
 * reload forgets who was idle. The AFK-kill consequences are decided once in KillListener;
 * this class only exposes the two config toggles it reads.
 */
public class AfkManager implements IManager {

    public enum ToggleResult { NOW_AFK, NOW_BACK, REFUSED_COMBAT, DISABLED }

    private final FrostFFACore plugin;

    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Set<UUID> afk = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> becameAfkAt = new ConcurrentHashMap<>();
    private final Set<UUID> movedToSpawn = ConcurrentHashMap.newKeySet();

    private BukkitTask task;
    private boolean enabled;
    private long idleMillis;
    private boolean afkKillsPay;
    private boolean afkKillsCount;
    private long moveToSpawnMillis;
    private String broadcastAway;
    private String broadcastBack;

    public AfkManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.reload();
        this.task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tick, 20L, 20L);
        this.plugin.getLogger().info("AfkManager enabled — idle threshold " + (this.idleMillis / 1000L) + "s.");
    }

    @Override
    public void onDisable() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        this.lastActivity.clear();
        this.afk.clear();
        this.becameAfkAt.clear();
        this.movedToSpawn.clear();
        this.plugin.getLogger().info("AfkManager disabled.");
    }

    public void reload() {
        this.enabled = this.plugin.getConfig().getBoolean("afk.enabled", true);
        this.idleMillis = Math.max(1, this.plugin.getConfig().getInt("afk.idle-seconds", 300)) * 1000L;
        this.afkKillsPay = this.plugin.getConfig().getBoolean("afk.kills-pay", false);
        this.afkKillsCount = this.plugin.getConfig().getBoolean("afk.kills-count", false);
        this.moveToSpawnMillis = Math.max(0, this.plugin.getConfig().getInt("afk.move-to-spawn-seconds", 0)) * 1000L;
        this.broadcastAway = this.plugin.getConfig().getString("afk.broadcast-away", "");
        this.broadcastBack = this.plugin.getConfig().getString("afk.broadcast-back", "");
    }

    // Queries used by other systems (plan §7.1)

    public boolean isAfk(Player player) {
        return player != null && this.afk.contains(player.getUniqueId());
    }

    public boolean isAfk(UUID uuid) {
        return uuid != null && this.afk.contains(uuid);
    }

    /** {@code afk.kills-pay} — killing an AFK victim pays 0 unless the server opts back in. */
    public boolean afkKillsPay() {
        return this.afkKillsPay;
    }

    /** {@code afk.kills-count} — an AFK victim's death doesn't credit streak/kills/rank unless opted back in. */
    public boolean afkKillsCount() {
        return this.afkKillsCount;
    }

    // Manual toggle (/afk)

    public ToggleResult toggleSelf(Player player) {
        if (!this.enabled) return ToggleResult.DISABLED;
        // "/afk while combat-tagged is refused" (plan §6.7) — otherwise it's a button that
        // makes your own kills worthless mid-fight.
        if (this.plugin.getCombatManager().isTagged(player)) return ToggleResult.REFUSED_COMBAT;

        if (this.isAfk(player)) {
            this.clear(player);
            return ToggleResult.NOW_BACK;
        }
        this.lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        this.markAfk(player);
        return ToggleResult.NOW_AFK;
    }

    /** Staff path (/afk &lt;player&gt;) — bypasses the combat refusal, matches the admin override. */
    public boolean setAfk(Player target, boolean value) {
        if (value) this.markAfk(target);
        else this.clear(target);
        return this.isAfk(target);
    }

    // Activity tracking (AfkListener calls this)

    public void touch(UUID uuid) {
        this.lastActivity.put(uuid, System.currentTimeMillis());
        Player player = this.plugin.getServer().getPlayer(uuid);
        if (player != null && this.isAfk(player)) {
            this.clear(player);
        }
    }

    private void clear(Player player) {
        UUID uuid = player.getUniqueId();
        boolean was = this.afk.remove(uuid);
        this.becameAfkAt.remove(uuid);
        this.movedToSpawn.remove(uuid);
        this.lastActivity.put(uuid, System.currentTimeMillis());
        if (was) {
            this.broadcast(this.broadcastBack, player.getName());
        }
    }

    private void markAfk(Player player) {
        UUID uuid = player.getUniqueId();
        if (!this.afk.add(uuid)) return; // already AFK — no-op, don't re-broadcast
        this.becameAfkAt.put(uuid, System.currentTimeMillis());
        this.broadcast(this.broadcastAway, player.getName());
    }

    private void broadcast(String template, String playerName) {
        if (template == null || template.isBlank()) return;
        MessageUtil.broadcastRaw(template, MessageUtil.player(playerName));
    }

    // Auto-flag timer

    private void tick() {
        if (!this.enabled) return;
        long now = System.currentTimeMillis();

        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            // Combat-tagged players never accrue idle time — the timer simply doesn't
            // advance while tagged (plan §6.7), rather than being paused and resumed.
            if (this.plugin.getCombatManager().isTagged(player)) {
                this.lastActivity.put(uuid, now);
                continue;
            }
            if (this.isAfk(player)) {
                this.maybeMoveToSpawn(player, now);
                continue;
            }
            // Exempt players are never AUTO-flagged, but they can still /afk manually.
            if (player.hasPermission("frostffa.afk.exempt")) continue;

            long last = this.lastActivity.getOrDefault(uuid, now);
            if (now - last >= this.idleMillis) {
                this.markAfk(player);
            }
        }
    }

    private void maybeMoveToSpawn(Player player, long now) {
        if (this.moveToSpawnMillis <= 0L) return;
        UUID uuid = player.getUniqueId();
        if (this.movedToSpawn.contains(uuid)) return;
        Long since = this.becameAfkAt.get(uuid);
        if (since == null || now - since < this.moveToSpawnMillis) return;

        this.movedToSpawn.add(uuid);
        Location spawn = LocationUtil.deserialize(this.plugin.getConfig().getString("spawn.location", ""));
        player.teleportAsync(spawn != null ? spawn : player.getWorld().getSpawnLocation());
    }
}
