package dev.notskrib.frostffacore.bounty;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import dev.notskrib.frostffacore.core.util.NetworkUtil;
import dev.notskrib.frostffacore.economy.Points;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * /bounty (plan §6.9). Saves on every mutation, since bounties are rare and low-volume.
 * The killer is paid the pool minus their own contribution and gets that contribution
 * refunded, so funding a bounty and killing the target yourself nets zero. Funds move
 * through EconomyManager (PlayerPoints) as plain integers.
 */
public class BountyManager implements IManager {

    /** Marks a server-funded (killstreak auto-bounty) contribution — never refunded on expiry. */
    public static final UUID SERVER_FUNDED = new UUID(0L, 0L);

    public enum PlaceResult {
        OK, SELF, TARGET_UNKNOWN, TARGET_AFK, BELOW_MINIMUM, POOL_CAP, SAME_CONNECTION, INSUFFICIENT_FUNDS
    }

    public record PayoutResult(int totalPool, int paidToKiller, int refundedToKiller, int contributorCount) {}

    private final FrostFFACore plugin;
    private File file;
    private final Map<UUID, Bounty> active = new ConcurrentHashMap<>();

    private int minimumAmount;
    private int maxPoolAmount;
    private long expiryMillis;
    private boolean blockSameIp;
    private int autoStreak;
    private int autoAmount;
    private String broadcastPlaced;
    private String broadcastClaimed;

    private BukkitTask sweepTask;

    public BountyManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.file = new File(this.plugin.getDataFolder(), "bounties.yml");
        this.readConfig();
        this.load();

        long fiveMinutes = 20L * 60L * 5L;
        this.sweepTask = this.plugin.getServer().getScheduler()
            .runTaskTimer(this.plugin, this::sweepExpired, fiveMinutes, fiveMinutes);

        this.plugin.getLogger().info("BountyManager enabled — " + this.active.size() + " active bounty/bounties loaded.");
    }

    @Override
    public void onDisable() {
        if (this.sweepTask != null) {
            this.sweepTask.cancel();
            this.sweepTask = null;
        }
        this.save();
        this.plugin.getLogger().info("BountyManager disabled.");
    }

    public void reload() {
        this.readConfig();
    }

    private void readConfig() {
        this.minimumAmount = Points.parse(coinConfig("minimum-coins", "minimum-shards", "100"));
        if (this.minimumAmount < 0) this.minimumAmount = 100;
        this.maxPoolAmount = Points.parse(coinConfig("max-pool-coins", "max-pool-shards", "250000"));
        if (this.maxPoolAmount < 0) this.maxPoolAmount = 250_000;
        this.expiryMillis = Math.max(1L, this.plugin.getConfig().getInt("bounty.expiry-hours", 48)) * 3_600_000L;
        this.blockSameIp = this.plugin.getConfig().getBoolean("bounty.block-same-ip", true);
        this.autoStreak = Math.max(1, this.plugin.getConfig().getInt("bounty.auto-streak", 25));
        this.autoAmount = Points.parse(coinConfig("auto-coins", "auto-shards", "5000"));
        if (this.autoAmount < 0) this.autoAmount = 5_000;
        this.broadcastPlaced = this.plugin.getConfig().getString("bounty.broadcast-placed", "");
        this.broadcastClaimed = this.plugin.getConfig().getString("bounty.broadcast-claimed", "");
    }

    private String coinConfig(String coinsKey, String legacyKey, String defaultValue) {
        String coins = this.plugin.getConfig().getString("bounty." + coinsKey);
        String legacy = this.plugin.getConfig().getString("bounty." + legacyKey, defaultValue);
        return coins == null || coins.equals(defaultValue) ? legacy : coins;
    }

    public int autoStreak() {
        return this.autoStreak;
    }

    // Placing

    public PlaceResult place(Player placer, OfflinePlayer target, int amount) {
        // A null target (or one with a null name) means the caller couldn't resolve a real
        // player — see EconomyAdmin#resolve, which is what callers should use to build this.
        if (target == null || target.getName() == null) return PlaceResult.TARGET_UNKNOWN;
        if (placer.getUniqueId().equals(target.getUniqueId())) return PlaceResult.SELF;
        if (amount < this.minimumAmount) return PlaceResult.BELOW_MINIMUM;

        Player targetOnline = target.getPlayer();
        // Cannot bounty an AFK player (plan §6.7's cross-feature table).
        if (targetOnline != null && this.plugin.getAfkManager().isAfk(targetOnline)) return PlaceResult.TARGET_AFK;
        if (this.blockSameIp && targetOnline != null
            && !placer.hasPermission("frostffa.bounty.bypass-ip")
            && NetworkUtil.sameConnection(placer, targetOnline)) {
            return PlaceResult.SAME_CONNECTION;
        }

        Bounty existing = this.active.get(target.getUniqueId());
        int existingPool = existing != null ? existing.pool() : 0;
        if (existingPool + amount > this.maxPoolAmount) return PlaceResult.POOL_CAP;

        if (!this.plugin.getEconomyManager().withdraw(placer.getUniqueId(), amount,
            "bounty:place:" + target.getName())) {
            return PlaceResult.INSUFFICIENT_FUNDS;
        }

        Bounty bounty = this.active.computeIfAbsent(target.getUniqueId(),
            id -> new Bounty(target.getUniqueId(), System.currentTimeMillis()));
        bounty.addContribution(placer.getUniqueId(), amount);
        this.save();

        this.broadcast(this.broadcastPlaced, placer.getName(), target.getName(), amount);
        return PlaceResult.OK;
    }

    /** Killstreak-milestone auto-bounty (plan §6.9) — funded from nothing, never refunded. */
    public void placeServerBounty(UUID target, int amount) {
        if (amount <= 0) return;
        Bounty bounty = this.active.computeIfAbsent(target, id -> new Bounty(target, System.currentTimeMillis()));
        int room = Math.max(0, this.maxPoolAmount - bounty.pool());
        int added = Math.min(amount, room);
        if (added <= 0) return;

        bounty.addContribution(SERVER_FUNDED, added);
        this.save();

        OfflinePlayer offline = this.plugin.getServer().getOfflinePlayer(target);
        String name = offline.getName() != null ? offline.getName() : target.toString();
        this.broadcast(this.broadcastPlaced, "the server", name, added);
    }

    // Queries

    public Bounty bountyOn(UUID target) {
        return this.active.get(target);
    }

    public int poolOf(UUID target) {
        Bounty bounty = this.active.get(target);
        return bounty != null ? bounty.pool() : 0;
    }

    public List<Bounty> topByPool(int limit) {
        List<Bounty> ordered = new ArrayList<>(this.active.values());
        ordered.sort(Comparator.comparingInt(Bounty::pool).reversed());
        return ordered.size() <= limit ? ordered : List.copyOf(ordered.subList(0, limit));
    }

    /** For {@code LeaderboardType.BOUNTY} (plan §6.1). */
    public Map<UUID, Long> poolSnapshot() {
        Map<UUID, Long> out = new HashMap<>();
        this.active.forEach((target, bounty) -> out.put(target, (long) bounty.pool()));
        return out;
    }

    // Payout on kill

    /** Called from KillListener. Returns null when the victim had no bounty on them. */
    public PayoutResult onKill(Player killer, Player victim) {
        Bounty bounty = this.active.remove(victim.getUniqueId());
        if (bounty == null) return null;

        int total = bounty.pool();
        int killerContribution = bounty.contributors().getOrDefault(killer.getUniqueId(), 0);
        int payout = total - killerContribution;

        if (payout > 0) {
            this.plugin.getEconomyManager().deposit(killer.getUniqueId(), payout,
                "bounty:claimed:" + victim.getName());
        }
        if (killerContribution > 0) {
            this.plugin.getEconomyManager().deposit(killer.getUniqueId(), killerContribution,
                "bounty:own-contribution-refund:" + victim.getName());
        }
        this.save();

        this.broadcast(this.broadcastClaimed, killer.getName(), victim.getName(), total);
        return new PayoutResult(total, payout, killerContribution, bounty.contributorCount());
    }

    // Expiry sweeper

    private void sweepExpired() {
        long now = System.currentTimeMillis();
        boolean changed = false;

        var iterator = this.active.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Bounty bounty = entry.getValue();
            if (now - bounty.placedAt() < this.expiryMillis) continue;

            OfflinePlayer offline = this.plugin.getServer().getOfflinePlayer(entry.getKey());
            String targetName = offline.getName() != null ? offline.getName() : entry.getKey().toString();
            bounty.contributors().forEach((contributor, amount) -> {
                if (!contributor.equals(SERVER_FUNDED) && amount > 0) {
                    this.plugin.getEconomyManager().deposit(contributor, amount, "bounty:expired:" + targetName);
                }
            });
            iterator.remove();
            changed = true;
        }

        if (changed) this.save();
    }

    private void broadcast(String template, String killerOrPlacer, String target, int amount) {
        if (template == null || template.isEmpty()) return;
        MessageUtil.broadcastRaw(template,
            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("killer", killerOrPlacer),
            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("target", target),
            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("amount",
                Points.format(amount, this.plugin.getEconomyManager())));
    }

    // Persistence

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Bounty bounty : this.active.values()) {
            String base = "bounties." + bounty.target();
            cfg.set(base + ".placed-at", bounty.placedAt());
            bounty.contributors().forEach((contributor, amount) ->
                cfg.set(base + ".contributors." + contributor, amount));
        }
        try {
            cfg.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Could not save bounties.yml: " + e.getMessage());
        }
    }

    private void load() {
        this.active.clear();
        if (!this.file.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection root = cfg.getConfigurationSection("bounties");
        if (root == null) return;

        for (String targetKey : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(targetKey);
            if (section == null) continue;
            try {
                UUID target = UUID.fromString(targetKey);
                Bounty bounty = new Bounty(target, section.getLong("placed-at", System.currentTimeMillis()));
                ConfigurationSection contributors = section.getConfigurationSection("contributors");
                if (contributors != null) {
                    for (String contributorKey : contributors.getKeys(false)) {
                        try {
                            UUID contributor = UUID.fromString(contributorKey);
                            bounty.addContribution(contributor, contributors.getInt(contributorKey));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                if (bounty.pool() > 0) this.active.put(target, bounty);
            } catch (IllegalArgumentException e) {
                this.plugin.getLogger().warning("bounties.yml: skipping malformed entry '" + targetKey + "'.");
            }
        }
    }
}
