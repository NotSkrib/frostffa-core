package dev.notskrib.frostffacore.daily;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * /daily: fork of MoneyFFAV2's RewardManager (plan §6.5) renamed stage to tier, with
 * daily.yml, daily_claims.yml and frostffa.daily.tier&lt;n&gt;. Kept from the fork: ClaimResult, the
 * transaction wrapper, simulating delivery before consuming, and writing the claim before the
 * payout. Added: a claim streak tracked apart from per-tier cooldowns.
 */
public class DailyManager implements IManager {

    public enum ClaimResult {
        OK, BUSY, NO_PERMISSION, ON_COOLDOWN, NO_SPACE, EMPTY_STAGE, UNKNOWN_STAGE
    }

    private enum ClaimMode { PER_TIER, HIGHEST_ONLY }

    private record StreakState(int count, long last) {}

    // A streak only ticks once per roughly a day even if several tiers are claimed inside
    // that window — otherwise PER_TIER mode would let one player inflate their own streak
    // by claiming tier1, tier2 and tier3 back to back.
    private static final long STREAK_MIN_GAP_MILLIS = 20L * 3_600_000L;

    private final FrostFFACore plugin;
    private File tiersFile;
    private File claimsFile;

    private Map<Integer, DailyTier> tiers = Map.of();
    private ClaimMode claimMode = ClaimMode.PER_TIER;
    private long cooldownMillis;
    private boolean streakEnabled;
    private long streakGraceMillis;
    private double streakBonusPerDay;
    private double streakMaxBonus;

    private final Map<UUID, Map<Integer, Long>> claims = new ConcurrentHashMap<>();
    private final Map<UUID, StreakState> streaks = new ConcurrentHashMap<>();

    public DailyManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.plugin.saveResource("daily.yml", false);
        this.tiersFile = new File(this.plugin.getDataFolder(), "daily.yml");
        this.claimsFile = new File(this.plugin.getDataFolder(), "daily_claims.yml");
        this.loadTiers();
        this.loadClaims();
        this.plugin.getLogger().info("DailyManager enabled — " + this.tiers.size() + " tier(s) loaded.");
    }

    @Override
    public void onDisable() {
        this.saveClaims();
        this.plugin.getLogger().info("DailyManager disabled.");
    }

    public void reload() {
        this.loadTiers();
    }

    public Map<Integer, DailyTier> tiers() {
        return this.tiers;
    }

    public long cooldownMillis() {
        return this.cooldownMillis;
    }

    public long remainingCooldown(UUID uuid, int tierId) {
        long since = System.currentTimeMillis() - this.lastClaimFor(uuid, tierId);
        return Math.max(0L, this.cooldownMillis - since);
    }

    public int streakCount(UUID uuid) {
        StreakState state = this.streaks.get(uuid);
        return state != null ? state.count() : 0;
    }

    // Claiming

    public ClaimResult claim(Player player, int tierId) {
        UUID uuid = player.getUniqueId();
        if (!this.plugin.getEconomyManager().beginTransaction(uuid)) return ClaimResult.BUSY;
        try {
            DailyTier tier = this.tiers.get(tierId);
            if (tier == null) return ClaimResult.UNKNOWN_STAGE;
            if (!tier.permission().isEmpty() && !player.hasPermission(tier.permission())) {
                return ClaimResult.NO_PERMISSION;
            }
            long now = System.currentTimeMillis();
            if (now - this.lastClaimFor(uuid, tierId) < this.cooldownMillis) return ClaimResult.ON_COOLDOWN;
            if (tier.isEmpty()) return ClaimResult.EMPTY_STAGE;

            if (!tier.items().isEmpty() && !this.fitsAll(player, tier.items().values())) {
                return ClaimResult.NO_SPACE;
            }

            // Claim recorded BEFORE the payout — a delivery hiccup after this point must
            // never let the same claim be replayed.
            this.recordClaim(uuid, tierId, now);
            double bonusPercent = this.applyStreakAndGetBonus(uuid, now);

            int points = tier.pointsAmount() <= 0 ? 0
                : (int) Math.round(tier.pointsAmount() * (1.0 + bonusPercent / 100.0));
            if (points > 0) {
                this.plugin.getEconomyManager().deposit(uuid, points, "daily:tier" + tierId);
            }
            for (ItemStack item : tier.items().values()) {
                player.getInventory().addItem(item.clone());
            }

            this.saveClaims();
            return ClaimResult.OK;
        } finally {
            this.plugin.getEconomyManager().endTransaction(uuid);
        }
    }

    private boolean fitsAll(Player player, Iterable<ItemStack> items) {
        Inventory simulation = Bukkit.createInventory(null, 36);
        simulation.setContents(player.getInventory().getStorageContents());
        for (ItemStack item : items) {
            if (!simulation.addItem(item.clone()).isEmpty()) return false;
        }
        return true;
    }

    private long lastClaimFor(UUID uuid, int tierId) {
        Map<Integer, Long> mine = this.claims.get(uuid);
        if (mine == null) return 0L;
        if (this.claimMode == ClaimMode.HIGHEST_ONLY) {
            return mine.values().stream().max(Long::compare).orElse(0L);
        }
        return mine.getOrDefault(tierId, 0L);
    }

    private void recordClaim(UUID uuid, int tierId, long now) {
        this.claims.computeIfAbsent(uuid, id -> new ConcurrentHashMap<>()).put(tierId, now);
    }

    /** Advances the streak at most once per ~day and returns the shard bonus to apply now. */
    private double applyStreakAndGetBonus(UUID uuid, long now) {
        if (!this.streakEnabled) return 0.0;
        StreakState previous = this.streaks.get(uuid);

        if (previous == null) {
            this.streaks.put(uuid, new StreakState(1, now));
            return 0.0;
        }

        long sinceLast = now - previous.last();
        if (sinceLast < STREAK_MIN_GAP_MILLIS) {
            // Already ticked within this claim-cycle — reuse the existing count's bonus.
            return Math.min(this.streakMaxBonus, previous.count() * this.streakBonusPerDay);
        }

        int newCount = sinceLast <= this.streakGraceMillis ? previous.count() + 1 : 1;
        this.streaks.put(uuid, new StreakState(newCount, now));
        return Math.min(this.streakMaxBonus, newCount * this.streakBonusPerDay);
    }

    // Admin CRUD

    public void setTierMoney(int tierId, int points) {
        DailyTier current = this.tiers.get(tierId);
        if (current == null) return;
        this.tiers = replace(this.tiers, tierId, new DailyTier(current.id(), current.displayName(),
            current.permission(), current.displayMaterial(), Math.max(0, points), current.items()));
        this.saveTierField(tierId, "money", Math.max(0, points));
    }

    public void setTierItem(int tierId, String slug, ItemStack item) {
        DailyTier current = this.tiers.get(tierId);
        if (current == null) return;
        Map<String, ItemStack> items = new LinkedHashMap<>(current.items());
        items.put(slug, item.clone());
        this.tiers = replace(this.tiers, tierId, new DailyTier(current.id(), current.displayName(),
            current.permission(), current.displayMaterial(), current.pointsAmount(), items));
        this.saveTierItem(tierId, slug, item);
    }

    public boolean removeTierItem(int tierId, String slug) {
        DailyTier current = this.tiers.get(tierId);
        if (current == null || !current.items().containsKey(slug)) return false;
        Map<String, ItemStack> items = new LinkedHashMap<>(current.items());
        items.remove(slug);
        this.tiers = replace(this.tiers, tierId, new DailyTier(current.id(), current.displayName(),
            current.permission(), current.displayMaterial(), current.pointsAmount(), items));

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.tiersFile);
        cfg.set("tiers." + tierId + ".items." + slug, null);
        this.save(cfg, this.tiersFile, "daily.yml");
        return true;
    }

    private static Map<Integer, DailyTier> replace(Map<Integer, DailyTier> source, int id, DailyTier tier) {
        Map<Integer, DailyTier> copy = new TreeMap<>(source);
        copy.put(id, tier);
        return Map.copyOf(copy);
    }

    private void saveTierField(int tierId, String field, Object value) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.tiersFile);
        cfg.set("tiers." + tierId + "." + field, value);
        this.save(cfg, this.tiersFile, "daily.yml");
    }

    private void saveTierItem(int tierId, String slug, ItemStack item) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.tiersFile);
        cfg.set("tiers." + tierId + ".items." + slug, item);
        this.save(cfg, this.tiersFile, "daily.yml");
    }

    private void save(YamlConfiguration cfg, File file, String label) {
        try {
            cfg.save(file);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Could not save " + label + ": " + e.getMessage());
        }
    }

    // Persistence

    private void loadTiers() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.tiersFile);

        this.cooldownMillis = Math.max(1L, cfg.getInt("cooldown-hours", 24)) * 3_600_000L;
        this.claimMode = "HIGHEST_ONLY".equalsIgnoreCase(cfg.getString("claim-mode", "PER_TIER"))
            ? ClaimMode.HIGHEST_ONLY : ClaimMode.PER_TIER;

        ConfigurationSection streak = cfg.getConfigurationSection("streak");
        this.streakEnabled = streak == null || streak.getBoolean("enabled", true);
        this.streakGraceMillis = Math.max(1L, streak != null ? streak.getInt("grace-hours", 48) : 48) * 3_600_000L;
        this.streakBonusPerDay = streak != null ? streak.getDouble("bonus-percent-per-day", 5.0) : 5.0;
        this.streakMaxBonus = streak != null ? streak.getDouble("max-bonus-percent", 50.0) : 50.0;

        Map<Integer, DailyTier> built = new TreeMap<>();
        ConfigurationSection tiersSection = cfg.getConfigurationSection("tiers");
        if (tiersSection != null) {
            for (String key : tiersSection.getKeys(false)) {
                ConfigurationSection section = tiersSection.getConfigurationSection(key);
                if (section == null) continue;
                int id;
                try {
                    id = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    continue;
                }
                Material material;
                try {
                    material = Material.valueOf(section.getString("display-material", "CHEST"));
                } catch (IllegalArgumentException e) {
                    material = Material.CHEST;
                }
                Map<String, ItemStack> items = new LinkedHashMap<>();
                ConfigurationSection itemsSection = section.getConfigurationSection("items");
                if (itemsSection != null) {
                    for (String slug : itemsSection.getKeys(false)) {
                        ItemStack item = itemsSection.getItemStack(slug);
                        if (item != null) items.put(slug, item);
                    }
                }
                built.put(id, new DailyTier(id, section.getString("display-name", "Tier " + id),
                    section.getString("permission", ""), material,
                    section.getInt("money", 0), items));
            }
        }
        this.tiers = Map.copyOf(built);
    }

    private void loadClaims() {
        this.claims.clear();
        this.streaks.clear();
        if (!this.claimsFile.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.claimsFile);
        ConfigurationSection claimsSection = cfg.getConfigurationSection("claims");
        if (claimsSection != null) {
            for (String uuidKey : claimsSection.getKeys(false)) {
                ConfigurationSection tiersOf = claimsSection.getConfigurationSection(uuidKey + ".tiers");
                if (tiersOf == null) continue;
                try {
                    UUID uuid = UUID.fromString(uuidKey);
                    Map<Integer, Long> mine = new ConcurrentHashMap<>();
                    for (String tierKey : tiersOf.getKeys(false)) {
                        try {
                            mine.put(Integer.parseInt(tierKey), tiersOf.getLong(tierKey));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    if (!mine.isEmpty()) this.claims.put(uuid, mine);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        ConfigurationSection streaksSection = cfg.getConfigurationSection("streaks");
        if (streaksSection != null) {
            for (String uuidKey : streaksSection.getKeys(false)) {
                ConfigurationSection mine = streaksSection.getConfigurationSection(uuidKey);
                if (mine == null) continue;
                try {
                    UUID uuid = UUID.fromString(uuidKey);
                    this.streaks.put(uuid, new StreakState(mine.getInt("count", 0), mine.getLong("last", 0L)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void saveClaims() {
        YamlConfiguration cfg = new YamlConfiguration();
        this.claims.forEach((uuid, mine) ->
            mine.forEach((tierId, millis) -> cfg.set("claims." + uuid + ".tiers." + tierId, millis)));
        this.streaks.forEach((uuid, state) -> {
            cfg.set("streaks." + uuid + ".count", state.count());
            cfg.set("streaks." + uuid + ".last", state.last());
        });
        this.save(cfg, this.claimsFile, "daily_claims.yml");
    }
}
