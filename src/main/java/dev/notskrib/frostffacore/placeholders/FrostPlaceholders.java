package dev.notskrib.frostffacore.placeholders;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.economy.Points;
import dev.notskrib.frostffacore.leaderboard.LeaderboardManager;
import dev.notskrib.frostffacore.leaderboard.LeaderboardType;
import dev.notskrib.frostffacore.rank.RankTier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * PlaceholderAPI expansion (plan §10 Phase 3), registered under the same presence check as the
 * other optional integrations.
 *
 * {@code %frostffa_points%} {@code _rank%} {@code _rank_progress%} {@code _streak%}
 * {@code _kills%} {@code _bounty%} {@code _afk%}
 * {@code %frostffa_top_<type>_<n>_name%} / {@code _value%} (type: kills, deaths, balance, killstreak, bounty)
 */
public class FrostPlaceholders extends PlaceholderExpansion {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final FrostFFACore plugin;

    public FrostPlaceholders(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "frostffa";
    }

    @Override
    public @NotNull String getAuthor() {
        return "notskrib";
    }

    @Override
    public @NotNull String getVersion() {
        return this.plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        // Survives a /papi reload without FrostFFACore having to re-register it.
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        String top = this.topPlaceholder(params);
        if (top != null) return top;
        if (offlinePlayer == null) return "";
        UUID uuid = offlinePlayer.getUniqueId();

        return switch (params.toLowerCase(Locale.ROOT)) {
            case "points" -> Points.format(this.plugin.getEconomyManager().balance(uuid));
            case "kills" -> String.valueOf(this.plugin.getKillsManager().getKills(uuid));
            case "streak" -> String.valueOf(this.currentStreak(uuid));
            case "bounty" -> Points.format(this.plugin.getBountyManager().poolOf(uuid));
            case "afk" -> String.valueOf(this.plugin.getAfkManager().isAfk(uuid));
            case "rank" -> this.rankName(uuid);
            case "rank_raw" -> this.rankRawId(uuid);
            case "rank_progress" -> String.valueOf(this.rankProgressPercent(uuid));
            default -> null;
        };
    }

    /**
     * {@code top_<type>_<position>_name} / {@code _value}, e.g. {@code top_kills_1_name}.
     * Works without a player, so it can feed holograms and scripts. Returns null when the
     * params aren't a top placeholder; "---" for an empty slot.
     */
    private String topPlaceholder(String params) {
        String[] parts = params.toLowerCase(Locale.ROOT).split("_");
        if (parts.length != 4 || !parts[0].equals("top")) return null;
        LeaderboardType type = LeaderboardType.from(parts[1]);
        if (type == null) return null;
        int position;
        try {
            position = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        boolean wantName = parts[3].equals("name");
        if (!wantName && !parts[3].equals("value")) return null;

        List<LeaderboardManager.Row> rows = this.plugin.getLeaderboardManager().topRows(type);
        if (position < 1 || position > rows.size()) return "---";
        LeaderboardManager.Row row = rows.get(position - 1);
        return wantName ? row.name() : row.value();
    }

    private int currentStreak(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        return online != null ? this.plugin.getKillstreakManager().getStreak(online) : 0;
    }

    private String rankName(UUID uuid) {
        RankTier tier = this.plugin.getRankManager().tierOf(uuid);
        if (tier == null) return "none";
        return PLAIN.serialize(MM.deserialize(tier.coloredName()));
    }

    private String rankRawId(UUID uuid) {
        RankTier tier = this.plugin.getRankManager().tierOf(uuid);
        return tier != null ? tier.id() : "none";
    }

    /** 0-100 percent progress toward the next tier; 100 if already at the highest tier. */
    private int rankProgressPercent(UUID uuid) {
        long kills = this.plugin.getKillsManager().getKills(uuid);
        RankTier current = this.plugin.getRankManager().tierOf(kills);
        RankTier next = this.plugin.getRankManager().next(current);
        if (next == null) return 100;

        long base = current != null ? current.killsRequired() : 0L;
        long span = Math.max(1L, next.killsRequired() - base);
        long into = Math.max(0L, kills - base);
        return (int) Math.min(100L, (into * 100L) / span);
    }
}
