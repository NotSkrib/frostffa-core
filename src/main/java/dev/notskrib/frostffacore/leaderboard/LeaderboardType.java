package dev.notskrib.frostffacore.leaderboard;

import dev.notskrib.frostffacore.economy.EconomyManager;
import dev.notskrib.frostffacore.economy.Points;
import java.util.Locale;

public enum LeaderboardType {

    BALANCE("balance", "coins", "POINTS", ""),
    KILLS("kills", "kills", "KILLS", " kills"),
    DEATHS("deaths", "deaths", "DEATHS", " deaths"),
    KILLSTREAK("killstreak", "streaks", "KILLSTREAK", " streak"),
    BOUNTY("bounty", "bounty pool", "BOUNTY", "");

    private final String id;
    private final String basis;
    private final String displayName;
    private final String suffix;

    LeaderboardType(String id, String basis, String displayName, String suffix) {
        this.id = id;
        this.basis = basis;
        this.displayName = displayName;
        this.suffix = suffix;
    }

    public String id() {
        return this.id;
    }

    public String basis() {
        return this.basis;
    }

    /** Short uppercase label for board titles — "TOP <displayName>". */
    public String displayName() {
        return this.displayName;
    }

    public String placeholder() {
        return "---" + this.suffix;
    }

    public String format(long value, EconomyManager economy) {
        return (this == BALANCE || this == BOUNTY) ? Points.format((int) value, economy) : value + this.suffix;
    }

    public static LeaderboardType from(String raw) {
        if (raw == null) return null;
        String needle = raw.toLowerCase(Locale.ROOT);
        for (LeaderboardType type : values()) {
            if (type.id.equals(needle) || type.name().toLowerCase(Locale.ROOT).equals(needle)) return type;
        }
        return switch (needle) {
            case "coins", "shards", "money", "bal", "baltop" -> BALANCE;
            case "kill", "killtop" -> KILLS;
            case "death", "deathtop" -> DEATHS;
            case "streak", "streaks", "beststreak" -> KILLSTREAK;
            case "bounties", "bountytop", "pool" -> BOUNTY;
            default -> null;
        };
    }
}
