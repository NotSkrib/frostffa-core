package dev.notskrib.frostffacore.economy;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class KillRewardManager implements IManager {

    public record Payout(int amount, boolean farmed, double multiplier) {}

    private final FrostFFACore plugin;
    private final Map<UUID, Map<UUID, Long>> recent = new ConcurrentHashMap<>();

    private File file;
    private int fullReward;
    private int repeatReward;
    private long cooldownMillis;

    public KillRewardManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.file = new File(this.plugin.getDataFolder(), "kill_cooldowns.yml");
        this.readConfig();
        this.load();
        this.plugin.getLogger().info("KillRewardManager enabled — " + Points.format(this.fullReward)
            + " per kill, " + Points.format(this.repeatReward) + " on a repeat within "
            + (this.cooldownMillis / 60_000L) + " min.");
    }

    @Override
    public void onDisable() {
        this.save();
        this.plugin.getLogger().info("KillRewardManager disabled — anti-farm windows saved.");
    }

    public void reload() {
        this.readConfig();
    }

    private void readConfig() {
        this.fullReward = Points.parse(this.plugin.getConfig().getString("economy.kill-reward", "10"));
        if (this.fullReward < 0) this.fullReward = 10;
        this.repeatReward = Points.parse(this.plugin.getConfig().getString("economy.repeat-kill-reward", "1"));
        if (this.repeatReward < 0) this.repeatReward = 1;
        this.cooldownMillis = Math.max(0L,
            this.plugin.getConfig().getLong("economy.repeat-cooldown-minutes", 10L)) * 60_000L;
    }

    public boolean isFarmed(UUID killer, UUID victim) {
        Map<UUID, Long> victims = this.recent.get(killer);
        if (victims == null) return false;
        Long until = victims.get(victim);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            victims.remove(victim);
            return false;
        }
        return true;
    }

    public long remainingCooldown(UUID killer, UUID victim) {
        Map<UUID, Long> victims = this.recent.get(killer);
        if (victims == null) return 0L;
        Long until = victims.get(victim);
        return until == null ? 0L : Math.max(0L, until - System.currentTimeMillis());
    }

    public Payout reward(Player killer, Player victim, int killstreak) {
        boolean farmed = this.isFarmed(killer.getUniqueId(), victim.getUniqueId());
        int base = farmed ? this.repeatReward : this.fullReward;

        double multiplier = 1.0 + this.plugin.getRankManager().rewardBonus(killer);
        int payout = (int) Math.round(base * multiplier);

        // Payout is calculated here and deposited directly to the killer's electronic coin balance
        // by the caller (KillListener) via EconomyManager.

        // The window restarts on every kill of this victim, so a farm pair never drifts
        // back into full payouts by killing on a metronome.
        this.recent.computeIfAbsent(killer.getUniqueId(), key -> new ConcurrentHashMap<>())
            .put(victim.getUniqueId(), System.currentTimeMillis() + this.cooldownMillis);

        return new Payout(payout, farmed, multiplier);
    }

    public int resetAllPlayerData() {
        int removed = this.recent.size();
        this.recent.clear();
        this.save();
        return removed;
    }

    public void save() {
        this.prune();
        YamlConfiguration out = new YamlConfiguration();
        this.recent.forEach((killer, victims) ->
            victims.forEach((victim, until) -> out.set("windows." + killer + "." + victim, until)));
        try {
            out.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Could not save kill_cooldowns.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!this.file.exists()) return;
        YamlConfiguration in = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection root = in.getConfigurationSection("windows");
        if (root == null) return;
        long now = System.currentTimeMillis();
        for (String killerKey : root.getKeys(false)) {
            ConfigurationSection victims = root.getConfigurationSection(killerKey);
            if (victims == null) continue;
            try {
                UUID killer = UUID.fromString(killerKey);
                Map<UUID, Long> map = new ConcurrentHashMap<>();
                for (String victimKey : victims.getKeys(false)) {
                    long until = victims.getLong(victimKey);
                    if (until > now) map.put(UUID.fromString(victimKey), until);
                }
                if (!map.isEmpty()) this.recent.put(killer, map);
            } catch (IllegalArgumentException e) {
                this.plugin.getLogger().warning("kill_cooldowns.yml: skipping malformed entry '" + killerKey + "'.");
            }
        }
    }

    private void prune() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Map<UUID, Long>>> killers = this.recent.entrySet().iterator();
        while (killers.hasNext()) {
            Map<UUID, Long> victims = killers.next().getValue();
            victims.entrySet().removeIf(entry -> now >= entry.getValue());
            if (victims.isEmpty()) killers.remove();
        }
    }
}
