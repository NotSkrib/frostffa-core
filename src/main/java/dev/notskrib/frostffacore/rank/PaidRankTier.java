package dev.notskrib.frostffacore.rank;

import org.bukkit.Material;

/**
 * One row of paid/donator ranks from ranks.yml (paid-tiers).
 * Granted via permissions (e.g. frostffa.rank.vip).
 */
public record PaidRankTier(
    int index,
    String id,
    String name,
    String colour,
    String permission,
    Material icon,
    int rewardBonusPercent,
    String kit,
    String description
) {

    public double rewardBonus() {
        return this.rewardBonusPercent / 100.0;
    }

    public String coloredName() {
        return "<color:" + this.colour + "><bold>" + this.name + "</bold></color>";
    }
}
