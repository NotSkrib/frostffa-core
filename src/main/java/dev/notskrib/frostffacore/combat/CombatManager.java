package dev.notskrib.frostffacore.combat;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class CombatManager implements IManager {

    private final FrostFFACore plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, BukkitTask> combatTasks = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Integer> combatSeconds = new HashMap<>();
    private final Set<UUID> combatLogged = new HashSet<>();
    private int tagDuration;
    private boolean showBossBar;
    private List<String> blacklistedCommands;

    public CombatManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.tagDuration = this.plugin.getConfig().getInt("combat.duration", 15);
        // Off by default: the combat timer already rides along with the balance in the
        // action bar.
        this.showBossBar = this.plugin.getConfig().getBoolean("combat.bossbar", false);
        this.blacklistedCommands = this.plugin.getConfig().getStringList("combat.blacklisted-commands");
        this.plugin.getLogger().info("CombatManager enabled. Tag duration: " + this.tagDuration + "s");
    }

    @Override
    public void onDisable() {
        new HashSet<>(this.combatTasks.keySet()).forEach(uuid -> {
            Player p = this.plugin.getServer().getPlayer(uuid);
            if (p != null) {
                this.removeBossBar(p);
            }
            this.combatTasks.get(uuid).cancel();
        });
        this.combatTasks.clear();
        this.bossBars.clear();
        this.combatSeconds.clear();
        this.combatLogged.clear();
        this.plugin.getLogger().info("CombatManager disabled.");
    }

    public void tag(Player player) {
        UUID uuid = player.getUniqueId();
        if (this.combatTasks.containsKey(uuid)) {
            this.combatTasks.get(uuid).cancel();
        }
        this.combatSeconds.put(uuid, this.tagDuration);
        this.showOrUpdateBossBar(player);
        BukkitTask task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            int remaining = this.combatSeconds.getOrDefault(uuid, 0) - 1;
            if (remaining <= 0) {
                this.untag(player, false);
                return;
            }
            this.combatSeconds.put(uuid, remaining);
            this.updateBossBar(player, remaining);
        }, 20L, 20L);
        this.combatTasks.put(uuid, task);
    }

    public void untag(Player player, boolean byDeath) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = this.combatTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        this.combatSeconds.remove(uuid);
        this.removeBossBar(player);
        if (!byDeath && player.isOnline()) {
            MessageUtil.send(player, "<color:#9FB3C0>ʏᴏᴜ ᴀʀᴇ ɴᴏ ʟᴏɴɢᴇʀ ɪɴ ᴄᴏᴍʙᴀᴛ.");
        }
    }

    public void forceUntag(UUID uuid) {
        BukkitTask task = this.combatTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        this.combatSeconds.remove(uuid);
        Player player = this.plugin.getServer().getPlayer(uuid);
        if (player != null) {
            this.removeBossBar(player);
        }
        this.bossBars.remove(uuid);
    }

    public boolean isTagged(Player player) {
        return this.combatTasks.containsKey(player.getUniqueId());
    }

    public void markCombatLogged(UUID uuid) {
        this.combatLogged.add(uuid);
    }

    public boolean consumeCombatLogged(UUID uuid) {
        return this.combatLogged.remove(uuid);
    }

    public boolean isBlacklisted(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        return this.blacklistedCommands.stream().anyMatch(lower::startsWith);
    }

    public void reload() {
        this.tagDuration = this.plugin.getConfig().getInt("combat.duration", 15);
        this.showBossBar = this.plugin.getConfig().getBoolean("combat.bossbar", false);
        this.blacklistedCommands = this.plugin.getConfig().getStringList("combat.blacklisted-commands");
    }

    public int getRemainingSeconds(Player player) {
        return this.combatSeconds.getOrDefault(player.getUniqueId(), 0);
    }

    public int getTagDuration() {
        return this.tagDuration;
    }

    private void showOrUpdateBossBar(Player player) {
        if (!this.showBossBar) {
            return;
        }
        UUID uuid = player.getUniqueId();
        BossBar bar = this.bossBars.computeIfAbsent(uuid, k -> BossBar.bossBar(this.buildBarTitle(this.tagDuration), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10));
        bar.name(this.buildBarTitle(this.tagDuration));
        bar.progress(1.0f);
        player.showBossBar(bar);
    }

    private void updateBossBar(Player player, int remaining) {
        UUID uuid = player.getUniqueId();
        BossBar bar = this.bossBars.get(uuid);
        if (bar == null || !player.isOnline()) {
            return;
        }
        bar.name(this.buildBarTitle(remaining));
        bar.progress(Math.max(0.0f, Math.min(1.0f, (float) remaining / (float) this.tagDuration)));
    }

    private void removeBossBar(Player player) {
        BossBar bar = this.bossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private Component buildBarTitle(int seconds) {
        return this.mm.deserialize("<color:#4FC3F7><bold>⚔ COMBAT</bold></color> <dark_gray>›</dark_gray> <color:#FF6B7A>" + seconds + "s");
    }
}
