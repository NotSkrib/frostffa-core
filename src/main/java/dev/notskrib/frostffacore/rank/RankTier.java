package dev.notskrib.frostffacore.rank;

import org.bukkit.Material;

/**
 * One row of ranks.yml (plan §6.3). Ranks are a pure function of total kills — there is no
 * per-tier mutable state, so this is an immutable value record.
 */
public record RankTier(
    int index,
    String id,
    String name,
    String colour,
    long killsRequired,
    Material icon,
    int rewardBonusPercent,
    String kit
) {

    public double rewardBonus() {
        return this.rewardBonusPercent / 100.0;
    }

    public String coloredName() {
        return "<color:" + this.colour + "><bold>" + this.name + "</bold></color>";
    }
}
