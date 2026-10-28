package dev.notskrib.frostffacore.kills;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.manager.IManager;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import dev.notskrib.frostffacore.economy.Points;
import dev.notskrib.frostffacore.settings.PlayerSetting;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class KillstreakManager implements IManager {

    private final FrostFFACore plugin;
    private File storageFile;
    private FileConfiguration storageConfig;
    private final Map<UUID, Integer> streaks = new HashMap<>();
    private final Map<UUID, Integer> highestStreaks = new HashMap<>();

    // Performance fix while porting (plan §6.12): the forked source called saveData() on
    // every kill AND every death, rewriting the whole YAML synchronously on the main
    // thread. Replaced with a dirty flag + periodic flush (same pattern as
    // SettingsManager), with a forced synchronous save in onDisable().
    private volatile boolean dirty = false;
    private BukkitTask saveTask;

    public KillstreakManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.storageFile = new File(this.plugin.getDataFolder(), "killstreaks.yml");
        this.loadData();
        int saveSeconds = Math.max(5, this.plugin.getConfig().getInt("killstreak.save-seconds", 60));
        this.saveTask = this.plugin.getServer().getScheduler()
            .runTaskTimer(this.plugin, this::saveIfDirty, saveSeconds * 20L, saveSeconds * 20L);
        this.plugin.getLogger().info("KillstreakManager enabled.");
    }

    @Override
    public void onDisable() {
        if (this.saveTask != null) {
            this.saveTask.cancel();
            this.saveTask = null;
        }
        // Forced synchronous save regardless of the dirty flag — the scheduler is already
        // shutting down, so this is the last chance to persist.
        this.saveData();
        this.plugin.getLogger().info("KillstreakManager disabled.");
    }

    public void onKill(Player killer, Player victim) {
        UUID kId = killer.getUniqueId();
        UUID vId = victim.getUniqueId();
        int victimStreak = this.streaks.getOrDefault(vId, 0);
        // The streak-ended death message covers this when it's configured, so the two
        // don't both announce the same kill.
        if (victimStreak >= 5 && !this.plugin.getKillMessageManager().handlesStreakEnd()) {
            MessageUtil.broadcast("<color:" + Palette.BRAND + "><bold>KILLSTREAK</bold></color> <dark_gray>›</dark_gray> <color:"
                + Palette.GLACIER + ">" + killer.getName() + " <color:" + Palette.INK + ">ᴇɴᴅᴇᴅ <color:"
                + Palette.GLACIER + ">" + victim.getName() + "<color:" + Palette.INK + ">'ꜱ <color:"
                + Palette.BAD + ">" + victimStreak + " ᴋɪʟʟꜱᴛʀᴇᴀᴋ<color:" + Palette.INK + ">!");
        }
        this.streaks.remove(vId);
        int streak = this.streaks.merge(kId, 1, Integer::sum);
        int high = this.highestStreaks.getOrDefault(kId, 0);
        if (streak > high) {
            this.highestStreaks.put(kId, streak);
        }
        this.handleMilestone(killer, streak);
        this.maybeAutoBounty(killer, streak);
        this.dirty = true;
    }

    /**
     * Server-funded bounty at the configured killstreak milestone (plan §6.9). Fires on
     * the exact crossing (==), not >=, so it announces once per streak rather than on
     * every kill after the threshold.
     */
    private void maybeAutoBounty(Player killer, int streak) {
        if (streak != this.plugin.getBountyManager().autoStreak()) return;
        String rawCoins = this.plugin.getConfig().getString("bounty.auto-coins",
            this.plugin.getConfig().getString("bounty.auto-shards", "5000"));
        int autoAmount = Points.parse(rawCoins);
        if (autoAmount > 0) {
            this.plugin.getBountyManager().placeServerBounty(killer.getUniqueId(), autoAmount);
        }
    }

    public void onDeath(Player victim) {
        this.streaks.remove(victim.getUniqueId());
        this.dirty = true;
    }

    public int getStreak(Player player) {
        return this.streaks.getOrDefault(player.getUniqueId(), 0);
    }

    public Map<UUID, Integer> highestSnapshot() {
        return new HashMap<>(this.highestStreaks);
    }

    public int getHighest(Player player) {
        return this.highestStreaks.getOrDefault(player.getUniqueId(), 0);
    }

    public int getHighest(UUID uuid) {
        return this.highestStreaks.getOrDefault(uuid, 0);
    }

    public int resetAllPlayerData() {
        int removed = this.streaks.size() + this.highestStreaks.size();
        this.streaks.clear();
        this.highestStreaks.clear();
        this.dirty = true;
        this.saveData();
        return removed;
    }

    public void resetStreak(Player player) {
        this.streaks.remove(player.getUniqueId());
        this.dirty = true;
    }

    private void handleMilestone(Player player, int streak) {
        String msg;
        ConfigurationSection section = this.plugin.getConfig().getConfigurationSection("killstreak.milestones");
        if (section != null && (msg = section.getString(String.valueOf(streak))) != null) {
            // An AFK killer's milestone is not broadcast (plan §6.7's cross-feature
            // table) — the local particle/sound/title effects below still play for the
            // achieving player themselves, since nobody else sees those anyway.
            if (!this.plugin.getAfkManager().isAfk(player)) {
                this.announceMilestone(player, msg);
            }
            this.playMilestoneEffects(player, streak);
            return;
        }
        if (streak == 5) {
            this.playMilestoneEffects(player, streak);
        } else if (streak >= 10 && streak % 10 == 0) {
            this.playMilestoneEffects(player, streak);
        }
    }

    /**
     * Respects PlayerSetting.STREAK_BROADCASTS — the streaking player always sees their
     * own milestone; everyone else only if they haven't turned the setting off. Whether
     * to call this at all (an AFK killer's milestone isn't broadcast) is decided by the
     * caller, {@link #handleMilestone}.
     */
    private void announceMilestone(Player player, String raw) {
        var component = MessageUtil.parse(raw, Placeholder.unparsed("player", player.getName()));
        for (Player viewer : this.plugin.getServer().getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())
                || this.plugin.getSettingsManager().get(viewer, PlayerSetting.STREAK_BROADCASTS)) {
                viewer.sendMessage(component);
            }
        }
    }

    private void playMilestoneEffects(Player player, int streak) {
        // Retheme (plan §6.12): SNOWFLAKE below 10, END_ROD from 10 up; chime below 10,
        // dragon growl from 10 up (kept — a streak that big deserves the drama).
        player.getWorld().spawnParticle(streak >= 10 ? Particle.END_ROD : Particle.SNOWFLAKE, player.getLocation(), 60);
        player.playSound(player.getLocation(), streak >= 10 ? Sound.ENTITY_ENDER_DRAGON_GROWL : Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        player.showTitle(Title.title(
            MiniMessage.miniMessage().deserialize("<color:" + Palette.BRAND + "><bold>KILLSTREAK!</bold></color>"),
            MiniMessage.miniMessage().deserialize("<color:" + Palette.GLACIER + ">" + streak + " Kills"),
            Title.Times.times(Duration.ofMillis(200L), Duration.ofMillis(2000L), Duration.ofMillis(500L))
        ));
    }

    private void saveIfDirty() {
        if (!this.dirty) return;
        this.saveData();
    }

    private void loadData() {
        ConfigurationSection high;
        if (!this.storageFile.exists()) {
            try {
                this.storageFile.createNewFile();
            } catch (IOException ignored) {
            }
        }
        this.storageConfig = YamlConfiguration.loadConfiguration(this.storageFile);
        ConfigurationSection cur = this.storageConfig.getConfigurationSection("current");
        if (cur != null) {
            for (String key : cur.getKeys(false)) {
                try {
                    this.streaks.put(UUID.fromString(key), cur.getInt(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if ((high = this.storageConfig.getConfigurationSection("highest")) != null) {
            for (String key : high.getKeys(false)) {
                try {
                    this.highestStreaks.put(UUID.fromString(key), high.getInt(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void saveData() {
        this.storageConfig = new YamlConfiguration();
        this.streaks.forEach((uuid, s) -> this.storageConfig.set("current." + uuid, s));
        this.highestStreaks.forEach((uuid, s) -> this.storageConfig.set("highest." + uuid, s));
        try {
            this.storageConfig.save(this.storageFile);
            this.dirty = false;
        } catch (IOException e) {
            this.plugin.getLogger().severe("Could not save killstreaks.yml!");
        }
    }
}
