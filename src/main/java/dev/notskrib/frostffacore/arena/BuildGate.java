package dev.notskrib.frostffacore.arena;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.util.WorldGuardUtil;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * Stand-in for MoneyFFAV2's {@code duelManager.buildingDenied(Location)} (plan §5.5). Denies a
 * location at or above the minimum Y inside a protected region (the spawn region if none are
 * configured), or next to a barrier when barrier placement is disabled.
 */
public class BuildGate {

    private final FrostFFACore plugin;

    public BuildGate(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    public boolean denied(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        if (this.plugin.getConfig().getBoolean("building.no-build-on-barriers", true) && this.hasBarrierNeighbour(loc)) {
            return true;
        }

        if (loc.getBlockY() < this.plugin.getConfig().getInt("building.protection-min-y", -64)) {
            return false;
        }
        return WorldGuardUtil.isInAnyRegion(loc, this.protectedRegions());
    }

    private boolean hasBarrierNeighbour(Location loc) {
        Location block = loc.toBlockLocation();
        return block.clone().add(1, 0, 0).getBlock().getType() == Material.BARRIER
            || block.clone().add(-1, 0, 0).getBlock().getType() == Material.BARRIER
            || block.clone().add(0, 1, 0).getBlock().getType() == Material.BARRIER
            || block.clone().add(0, -1, 0).getBlock().getType() == Material.BARRIER
            || block.clone().add(0, 0, 1).getBlock().getType() == Material.BARRIER
            || block.clone().add(0, 0, -1).getBlock().getType() == Material.BARRIER;
    }

    private List<String> protectedRegions() {
        List<String> configured = this.plugin.getConfig().getStringList("building.protected-regions");
        if (!configured.isEmpty()) return configured;
        String spawn = this.plugin.getConfig().getString("spawn.region",
            this.plugin.getConfig().getString("spawn-region", "spawn"));
        return spawn == null || spawn.isBlank() ? List.of() : List.of(spawn);
    }
}
