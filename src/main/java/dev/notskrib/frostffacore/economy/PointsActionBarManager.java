package dev.notskrib.frostffacore.economy;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.settings.PlayerSetting;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.manager.IManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Renamed from ShardActionBarManager when the economy moved onto PlayerPoints — this class is currency-agnostic now. */
public class PointsActionBarManager implements IManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String SEP = "  <dark_gray>|</dark_gray>  ";

    private final FrostFFACore plugin;
    private final Map<UUID, Long> suppressed = new ConcurrentHashMap<>();
    private BukkitTask task;
    private boolean enabled;
    private long suppressMillis;

    public PointsActionBarManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.enabled = this.plugin.getConfig().getBoolean("economy.actionbar", true);
        this.suppressMillis = Math.max(0L, this.plugin.getConfig().getLong("economy.actionbar-yield-millis", 1800L));
        if (!this.enabled) {
            this.plugin.getLogger().info("PointsActionBarManager disabled by config.");
            return;
        }
        int interval = Math.max(2, this.plugin.getConfig().getInt("economy.actionbar-update-ticks", 10));
        this.task = this.plugin.getServer().getScheduler()
            .runTaskTimer(this.plugin, this::tick, interval, interval);
        this.plugin.getLogger().info("PointsActionBarManager enabled.");
    }

    @Override
    public void onDisable() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    public void restart() {
        this.onDisable();
        this.onEnable();
    }

    public void yieldTo(Player player) {
        if (this.suppressMillis > 0L) {
            this.suppressed.put(player.getUniqueId(), System.currentTimeMillis() + this.suppressMillis);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (!this.plugin.getSettingsManager().get(player, PlayerSetting.ACTION_BAR)) continue;
            Long until = this.suppressed.get(player.getUniqueId());
            if (until != null) {
                if (now < until) continue;
                this.suppressed.remove(player.getUniqueId());
            }
            player.sendActionBar(MM.deserialize(this.build(player)));
        }
    }

    private String build(Player player) {
        if (this.plugin.getAfkManager().isAfk(player)) {
            return "<color:" + Palette.DIM + ">" + Palette.l(Palette.DIM, "afk") + "</color>";
        }
        StringBuilder bar = new StringBuilder();
        bar.append(Points.colored(this.plugin.getEconomyManager().balance(player), this.plugin.getEconomyManager()));

        int combat = this.plugin.getCombatManager().getRemainingSeconds(player);
        if (combat > 0) {
            bar.append(SEP)
                .append("<color:").append(Palette.BAD).append("><bold>⚔ COMBAT</bold></color> ")
                .append("<color:").append(Palette.INK).append(">").append(combat).append("s</color>");
        } else {
            int streak = this.plugin.getKillstreakManager().getStreak(player);
            if (streak > 0) {
                bar.append(SEP)
                    .append("<color:").append(Palette.INK).append(">Streak </color>")
                    .append("<color:").append(Palette.GLACIER).append(">").append(streak).append("</color>");
            }
        }
        return bar.toString();
    }
}
