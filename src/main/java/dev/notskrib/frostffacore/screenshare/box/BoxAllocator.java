package dev.notskrib.frostffacore.screenshare.box;

import dev.notskrib.frostffacore.screenshare.config.ScreenShareConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Maps an integer "slot" to a concrete cuboid in the world. Slots are laid out in
 * a row along +X, spaced far enough apart that two simultaneous sessions never
 * share blocks. All coordinates are read live from config so a reload picks up
 * changes (existing sessions keep their original slot math for that server run).
 */
public final class BoxAllocator {

    private static final double MARGIN = 0.35D;

    private final ScreenShareConfig config;

    public BoxAllocator(ScreenShareConfig config) {
        this.config = config;
    }

    public World world() {
        return Bukkit.getWorld(config.boxWorld());
    }

    public boolean ready() {
        return world() != null;
    }

    private int originX(int slot) {
        return config.boxBaseX() + slot * config.boxSpacing();
    }

    /** Lowest corner block of the whole structure (outer bedrock shell included). */
    public Location corner(int slot) {
        World world = world();
        if (world == null) {
            return null;
        }
        return new Location(world, originX(slot), config.boxBaseY(), config.boxBaseZ());
    }

    /** Outer edge length of the structure: interior + one wall on each side. */
    public int shellSize() {
        return config.boxInterior() + 1;
    }

    /** Where the detainee is dropped: centred on the interior floor. */
    public Location standLocation(int slot) {
        World world = world();
        if (world == null) {
            return null;
        }
        double centre = 1 + (config.boxInterior() - 1) / 2.0 + 0.5;
        return new Location(world,
                originX(slot) + centre,
                config.boxBaseY() + 1,
                config.boxBaseZ() + centre);
    }

    private int interiorClamp(int value) {
        return Math.max(1, Math.min(config.boxInterior(), value));
    }

    /** Block that holds the "Admit Defeat" button, against the +Z wall. */
    public Location admitButton(int slot) {
        World world = world();
        if (world == null) {
            return null;
        }
        return new Location(world,
                originX(slot) + 1,
                config.boxBaseY() + interiorClamp(2),
                config.boxBaseZ() + config.boxInterior());
    }

    /** Block that holds the "Refuse" button, against the +Z wall. */
    public Location refuseButton(int slot) {
        World world = world();
        if (world == null) {
            return null;
        }
        return new Location(world,
                originX(slot) + config.boxInterior(),
                config.boxBaseY() + interiorClamp(2),
                config.boxBaseZ() + config.boxInterior());
    }

    /** Wall-sign block directly above {@link #admitButton(int)}. */
    public Location admitSign(int slot) {
        Location button = admitButton(slot);
        return button == null ? null : button.clone().add(0, interiorClamp(3) - interiorClamp(2), 0);
    }

    /** Wall-sign block directly above {@link #refuseButton(int)}. */
    public Location refuseSign(int slot) {
        Location button = refuseButton(slot);
        return button == null ? null : button.clone().add(0, interiorClamp(3) - interiorClamp(2), 0);
    }

    /** True if {@code target}'s block coordinates match (x, y, z). */
    public static boolean sameBlock(Location target, int x, int y, int z) {
        return target != null && target.getBlockX() == x && target.getBlockY() == y && target.getBlockZ() == z;
    }

    /** True if {@code location} is inside the interior air pocket of {@code slot}. */
    public boolean contains(int slot, Location location) {
        if (location == null || location.getWorld() == null
                || !location.getWorld().getName().equals(config.boxWorld())) {
            return false;
        }
        int interior = config.boxInterior();
        double minX = originX(slot) + 1;
        double minY = config.boxBaseY() + 1;
        double minZ = config.boxBaseZ() + 1;
        double maxX = minX + interior;
        double maxY = minY + interior;
        double maxZ = minZ + interior;
        return location.getX() >= minX - MARGIN && location.getX() <= maxX + MARGIN
                && location.getY() >= minY - MARGIN && location.getY() <= maxY + MARGIN
                && location.getZ() >= minZ - MARGIN && location.getZ() <= maxZ + MARGIN;
    }
}
