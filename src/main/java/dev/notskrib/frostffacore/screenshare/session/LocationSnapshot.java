package dev.notskrib.frostffacore.screenshare.session;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Immutable snapshot of where a detainee was standing before detainment. */
public record LocationSnapshot(String world, double x, double y, double z, float yaw, float pitch) {

    public static LocationSnapshot of(Location location) {
        return new LocationSnapshot(
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    /** Rebuilds a Location, or null if the world is no longer loaded. */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }
}
