package dev.notskrib.frostffacore.daily;

import java.util.Map;
import org.bukkit.Material;

/**
 * One row of daily.yml (plan §6.5/§9.6). {@code items} is keyed by slug so the admin GUI
 * can add/remove a specific payload item without disturbing the others.
 */
public record DailyTier(
    int id,
    String displayName,
    String permission,
    Material displayMaterial,
    int pointsAmount,
    Map<String, org.bukkit.inventory.ItemStack> items
) {

    public boolean isEmpty() {
        return this.pointsAmount <= 0 && this.items.isEmpty();
    }
}
