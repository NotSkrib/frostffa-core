package dev.notskrib.frostffacore.clearlag;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Periodically sweeps worlds for lag entities (items, arrows, XP orbs by default) after a
 * warning broadcast. /frostffa clearlag on|off persists to config.yml.
 */
public class ClearLagManager implements IManager {

    private final FrostFFACore plugin;
    private BukkitTask task;

    private boolean enabled;
    private int intervalSeconds;
    private int warningSeconds;
    private Set<EntityType> types;
    private String warningMessage;
    private String clearedMessage;

    private int secondsUntilNext;

    public ClearLagManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.readConfig();
        this.start();
    }

    @Override
    public void onDisable() {
        this.stop();
    }

    public void reload() {
        this.stop();
        this.readConfig();
        this.start();
    }

    private void readConfig() {
        this.enabled = this.plugin.getConfig().getBoolean("clearlag.enabled", true);
        this.intervalSeconds = Math.max(30, this.plugin.getConfig().getInt("clearlag.interval-seconds", 300));
        this.warningSeconds = Math.max(0, this.plugin.getConfig().getInt("clearlag.warning-seconds", 10));
        this.warningMessage = this.plugin.getConfig().getString("clearlag.warning-message",
            "<color:#FFD166>Clearing dropped items in <seconds>s.</color>");
        this.clearedMessage = this.plugin.getConfig().getString("clearlag.cleared-message",
            "<color:#9FB3C0>Cleared <count> item(s) from the ground.</color>");

        List<String> names = this.plugin.getConfig().getStringList("clearlag.entities");
        if (names.isEmpty()) names = List.of("DROPPED_ITEM", "ARROW", "EXPERIENCE_ORB");
        Set<EntityType> parsed = EnumSet.noneOf(EntityType.class);
        for (String name : names) {
            try {
                parsed.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                this.plugin.getLogger().warning("clearlag.entities: unknown entity type '" + name + "'.");
            }
        }
        this.types = parsed;
        this.secondsUntilNext = this.intervalSeconds;
    }

    private void start() {
        if (this.task != null) return;
        this.task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tick, 20L, 20L);
    }

    private void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    private void tick() {
        if (!this.enabled) return;
        this.secondsUntilNext--;
        if (this.secondsUntilNext == this.warningSeconds && this.warningSeconds > 0) {
            MessageUtil.broadcastRaw(this.warningMessage.replace("<seconds>", String.valueOf(this.warningSeconds)));
        }
        if (this.secondsUntilNext <= 0) {
            this.sweep(true);
            this.secondsUntilNext = this.intervalSeconds;
        }
    }

    /** Removes every matching entity across all worlds right now, regardless of the toggle. */
    public int sweep(boolean announce) {
        int removed = 0;
        for (World world : this.plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (this.types.contains(entity.getType())) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (announce && removed > 0) {
            MessageUtil.broadcastRaw(this.clearedMessage.replace("<count>", String.valueOf(removed)));
        }
        return removed;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.plugin.getConfig().set("clearlag.enabled", enabled);
        this.plugin.saveConfig();
        if (enabled) this.secondsUntilNext = this.intervalSeconds;
    }
}
