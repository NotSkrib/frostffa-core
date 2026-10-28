package dev.notskrib.frostffacore.screenshare.box;

import dev.notskrib.frostffacore.screenshare.config.ScreenShareConfig;
import dev.notskrib.frostffacore.screenshare.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.sign.Side;
import org.bukkit.plugin.Plugin;

/**
 * Places and removes the bedrock detainment cell (and, in BUTTONS mode, the two
 * wall buttons plus their signs). All block edits run on the region that owns the
 * cell's chunk (Folia requirement; harmless on Paper).
 */
public final class BoxBuilder {

    private final Plugin plugin;
    private final BoxAllocator allocator;
    private final ScreenShareConfig config;

    public BoxBuilder(Plugin plugin, BoxAllocator allocator, ScreenShareConfig config) {
        this.plugin = plugin;
        this.allocator = allocator;
        this.config = config;
    }

    /** Builds a hollow bedrock shell around the interior and runs {@code afterBuilt} when done. */
    public void build(int slot, Runnable afterBuilt) {
        Location corner = allocator.corner(slot);
        if (corner == null) {
            plugin.getLogger().severe("Box world '" + describeWorld() + "' is not loaded; cannot build cell.");
            if (afterBuilt != null) {
                afterBuilt.run();
            }
            return;
        }
        Bukkit.getRegionScheduler().run(plugin, corner, task -> {
            edit(corner, true);
            if (config.menuButtons()) {
                placeControls(slot);
            }
            if (afterBuilt != null) {
                afterBuilt.run();
            }
        });
    }

    /** Fills the whole structure (shell + interior) back to air. */
    public void clear(int slot) {
        Location corner = allocator.corner(slot);
        if (corner == null) {
            return;
        }
        Bukkit.getRegionScheduler().run(plugin, corner, task -> edit(corner, false));
    }

    private void edit(Location corner, boolean building) {
        World world = corner.getWorld();
        int ox = corner.getBlockX();
        int oy = corner.getBlockY();
        int oz = corner.getBlockZ();
        int size = allocator.shellSize();

        for (int dx = 0; dx <= size; dx++) {
            for (int dy = 0; dy <= size; dy++) {
                for (int dz = 0; dz <= size; dz++) {
                    boolean edge = dx == 0 || dy == 0 || dz == 0 || dx == size || dy == size || dz == size;
                    Material material = building && edge ? Material.BEDROCK : Material.AIR;
                    world.getBlockAt(ox + dx, oy + dy, oz + dz).setType(material, false);
                }
            }
        }

        if (building) {
            // A light source in the ceiling so the detainee is not left in the dark.
            world.getBlockAt(ox + size / 2, oy + size - 1, oz + size / 2).setType(Material.GLOWSTONE, false);
        }
    }

    private void placeControls(int slot) {
        button(allocator.admitButton(slot));
        button(allocator.refuseButton(slot));
        sign(allocator.admitSign(slot), config.admitName(), "&8[ click below ]");
        sign(allocator.refuseSign(slot), config.refuseName(), "&8[ click below ]");
    }

    private void button(Location where) {
        if (where == null) {
            return;
        }
        World world = where.getWorld();
        world.getBlockAt(where).setType(Material.POLISHED_BLACKSTONE_BUTTON, false);
        BlockData data = world.getBlockAt(where).getBlockData();
        if (data instanceof Switch button) {
            // Mounted on the +Z bedrock wall, face pointing back toward the detainee (-Z).
            button.setAttachedFace(FaceAttachable.AttachedFace.WALL);
            button.setFacing(BlockFace.NORTH);
            world.getBlockAt(where).setBlockData(button, false);
        }
    }

    private void sign(Location where, String topLine, String bottomLine) {
        if (where == null) {
            return;
        }
        World world = where.getWorld();
        world.getBlockAt(where).setType(Material.OAK_WALL_SIGN, false);
        BlockData data = world.getBlockAt(where).getBlockData();
        if (data instanceof Directional directional) {
            directional.setFacing(BlockFace.NORTH);
            world.getBlockAt(where).setBlockData(directional, false);
        }
        if (world.getBlockAt(where).getState() instanceof Sign signState) {
            signState.getSide(Side.FRONT).line(1, Text.item(topLine));
            signState.getSide(Side.FRONT).line(2, Text.item(bottomLine));
            signState.setWaxed(true);
            signState.update(true, false);
        }
    }

    private String describeWorld() {
        return allocator.world() == null ? "<missing>" : allocator.world().getName();
    }
}
