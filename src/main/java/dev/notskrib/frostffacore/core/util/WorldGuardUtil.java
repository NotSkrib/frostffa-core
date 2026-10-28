package dev.notskrib.frostffacore.core.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.Collection;
import org.bukkit.Location;
import org.bukkit.World;

public final class WorldGuardUtil {
    private WorldGuardUtil() {
    }

    public static boolean isInAnyRegion(Location loc, Collection<String> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) {
            return false;
        }
        try {
            RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt((World) loc.getWorld()));
            if (rm == null) {
                return false;
            }
            ApplicableRegionSet set = rm.getApplicableRegions(BlockVector3.at(loc.getX(), loc.getY(), loc.getZ()));
            for (ProtectedRegion region : set) {
                if (!regionIds.stream().anyMatch(id -> id.equalsIgnoreCase(region.getId()))) continue;
                return true;
            }
        } catch (Exception | NoClassDefFoundError throwable) {
        }
        return false;
    }

    public static boolean isPvpAllowed(Location loc) {
        try {
            StateFlag.State state = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().queryState(BukkitAdapter.adapt(loc), (LocalPlayer) null, Flags.PVP);
            return state != StateFlag.State.DENY;
        } catch (Exception | NoClassDefFoundError e) {
            return true;
        }
    }

    public static boolean isInRegion(Location loc, String regionId) {
        try {
            RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt((World) loc.getWorld()));
            if (rm == null) {
                return false;
            }
            ApplicableRegionSet set = rm.getApplicableRegions(BlockVector3.at(loc.getX(), loc.getY(), loc.getZ()));
            for (ProtectedRegion region : set) {
                if (!region.getId().equalsIgnoreCase(regionId)) continue;
                return true;
            }
        } catch (Exception | NoClassDefFoundError throwable) {
        }
        return false;
    }
}
