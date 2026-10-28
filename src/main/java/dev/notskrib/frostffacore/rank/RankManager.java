package dev.notskrib.frostffacore.rank;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.manager.IManager;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Kill-based ranks (plan §6.3), derived from {@link kills.KillsManager} totals so no rank
 * file exists. {@code cachedTier} only detects promotion crossings so each is broadcast once.
 */
public class RankManager implements IManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final FrostFFACore plugin;
    private final Map<UUID, Integer> cachedTierIndex = new ConcurrentHashMap<>();

    private List<RankTier> tiers = List.of();
    private List<PaidRankTier> paidTiers = List.of();
    private String promotionBroadcast = "";
    private String promotionTitle = "";
    private String promotionSubtitle = "";

    public RankManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.plugin.saveResource("ranks.yml", false);
        this.load();
        this.plugin.getLogger().info("RankManager enabled — " + this.tiers.size() + " free tier(s), " + this.paidTiers.size() + " paid tier(s) loaded.");
    }

    @Override
    public void onDisable() {
        this.cachedTierIndex.clear();
        this.plugin.getLogger().info("RankManager disabled.");
    }

    public void reload() {
        this.load();
    }

    private void load() {
        var file = new java.io.File(this.plugin.getDataFolder(), "ranks.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection freeSection = cfg.getConfigurationSection("free-tiers");
        if (freeSection == null) freeSection = cfg.getConfigurationSection("tiers");

        List<RankTier> builtFree = new ArrayList<>();
        if (freeSection != null) {
            for (String key : freeSection.getKeys(false)) {
                ConfigurationSection tier = freeSection.getConfigurationSection(key);
                if (tier == null) continue;
                Material icon;
                try {
                    icon = Material.valueOf(tier.getString("icon", "SNOWBALL"));
                } catch (IllegalArgumentException e) {
                    icon = Material.SNOWBALL;
                }
                int index;
                try {
                    index = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    index = builtFree.size() + 1;
                }
                builtFree.add(new RankTier(
                    index,
                    tier.getString("id", "tier" + index),
                    tier.getString("name", "TIER " + index),
                    tier.getString("colour", Palette.INK),
                    tier.getLong("kills", 0L),
                    icon,
                    tier.getInt("reward-bonus-percent", 0),
                    tier.getString("kit", tier.getString("id", "tier" + index))
                ));
            }
        }
        builtFree.sort(Comparator.comparingLong(RankTier::killsRequired));
        this.tiers = List.copyOf(builtFree);

        ConfigurationSection paidSection = cfg.getConfigurationSection("paid-tiers");
        List<PaidRankTier> builtPaid = new ArrayList<>();
        if (paidSection != null) {
            for (String key : paidSection.getKeys(false)) {
                ConfigurationSection tier = paidSection.getConfigurationSection(key);
                if (tier == null) continue;
                Material icon;
                try {
                    icon = Material.valueOf(tier.getString("icon", "EMERALD"));
                } catch (IllegalArgumentException e) {
                    icon = Material.EMERALD;
                }
                int index;
                try {
                    index = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    index = builtPaid.size() + 1;
                }
                builtPaid.add(new PaidRankTier(
                    index,
                    tier.getString("id", "paid" + index),
                    tier.getString("name", "PAID " + index),
                    tier.getString("colour", Palette.BRAND),
                    tier.getString("permission", "frostffa.rank." + tier.getString("id", "paid" + index)),
                    icon,
                    tier.getInt("reward-bonus-percent", 0),
                    tier.getString("kit", tier.getString("id", "paid" + index)),
                    tier.getString("description", "")
                ));
            }
        }
        builtPaid.sort(Comparator.comparingInt(PaidRankTier::index));
        this.paidTiers = List.copyOf(builtPaid);

        if (this.tiers.isEmpty()) {
            this.plugin.getLogger().warning("ranks.yml has no free tiers.");
        }

        ConfigurationSection promotion = cfg.getConfigurationSection("promotion");
        this.promotionBroadcast = promotion != null ? promotion.getString("broadcast", "") : "";
        this.promotionTitle = promotion != null ? promotion.getString("title", "") : "";
        this.promotionSubtitle = promotion != null ? promotion.getString("subtitle", "") : "";
    }

    public List<RankTier> tiers() {
        return this.tiers;
    }

    public List<PaidRankTier> paidTiers() {
        return this.paidTiers;
    }

    public PaidRankTier highestPaidTierOf(Player player) {
        if (player == null || this.paidTiers.isEmpty()) return null;
        for (int i = this.paidTiers.size() - 1; i >= 0; i--) {
            PaidRankTier paid = this.paidTiers.get(i);
            if (player.hasPermission(paid.permission())) {
                return paid;
            }
        }
        return null;
    }

    public RankTier tierOf(long kills) {
        RankTier best = null;
        for (RankTier tier : this.tiers) {
            if (tier.killsRequired() <= kills) {
                best = tier;
            } else {
                break;
            }
        }
        return best != null ? best : (this.tiers.isEmpty() ? null : this.tiers.get(0));
    }

    public RankTier tierOf(UUID uuid) {
        return this.tierOf(this.plugin.getKillsManager().getKills(uuid));
    }

    public RankTier tierOf(Player player) {
        return this.tierOf(player.getUniqueId());
    }

    public RankTier next(RankTier tier) {
        if (tier == null) return this.tiers.isEmpty() ? null : this.tiers.get(0);
        int position = this.tiers.indexOf(tier);
        if (position < 0 || position + 1 >= this.tiers.size()) return null;
        return this.tiers.get(position + 1);
    }

    public double rewardBonus(Player player) {
        RankTier freeTier = this.tierOf(player);
        double freeBonus = freeTier != null ? freeTier.rewardBonus() : 0.0;
        PaidRankTier paidTier = this.highestPaidTierOf(player);
        double paidBonus = paidTier != null ? paidTier.rewardBonus() : 0.0;
        return Math.max(freeBonus, paidBonus);
    }

    /**
     * Recomputes the killer's tier after a kill and fires the promotion broadcast exactly
     * once per threshold crossing. Called from KillListener right after
     * KillsManager#addKill, so player.getKills() already reflects this kill.
     */
    public void onKill(Player player) {
        if (this.tiers.isEmpty()) return;
        UUID uuid = player.getUniqueId();
        long currentKills = this.plugin.getKillsManager().getKills(uuid);

        RankTier newTier = this.tierOf(currentKills);
        int newIndex = this.tiers.indexOf(newTier);

        Integer cached = this.cachedTierIndex.get(uuid);
        if (cached == null) {
            // First kill this session for this player — seed from the tier BEFORE this
            // kill so a genuine crossing on the very first post-restart kill still fires.
            cached = this.tiers.indexOf(this.tierOf(Math.max(0L, currentKills - 1)));
        }

        this.cachedTierIndex.put(uuid, newIndex);
        if (newIndex > cached) {
            this.announcePromotion(player, newTier);
        }
    }

    private void announcePromotion(Player player, RankTier tier) {
        if (!this.promotionBroadcast.isEmpty()) {
            MessageUtil.broadcastRaw(this.promotionBroadcast,
                MessageUtil.player(player.getName()),
                Placeholder.component("rank", MM.deserialize(tier.coloredName())));
        }
        if (!this.promotionTitle.isEmpty() || !this.promotionSubtitle.isEmpty()) {
            player.showTitle(Title.title(
                MM.deserialize(this.promotionTitle.isEmpty() ? " " : this.promotionTitle),
                MM.deserialize(this.promotionSubtitle.isEmpty() ? " "
                    : this.promotionSubtitle.replace("<rank>", tier.coloredName())),
                Title.Times.times(Duration.ofMillis(200L), Duration.ofMillis(2500L), Duration.ofMillis(500L))
            ));
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }
}
