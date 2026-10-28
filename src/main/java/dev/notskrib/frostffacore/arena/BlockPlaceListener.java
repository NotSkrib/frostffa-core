package dev.notskrib.frostffacore.arena;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Records player placements in the {@link BlockLedger} for the Plier Axe. Whitelist-gated by
 * {@code block-whitelist} and refused in {@link BuildGate}-denied spots; holders of
 * {@code frostffa.buildpermission} skip all of it so their decoration survives map clears (plan §5.5).
 */
public class BlockPlaceListener implements Listener {

    private final FrostFFACore plugin;
    private Set<Material> whitelist = new HashSet<>();

    public BlockPlaceListener(FrostFFACore plugin) {
        this.plugin = plugin;
        this.reload();
    }

    public void reload() {
        Set<Material> loaded = new HashSet<>();
        for (String name : this.plugin.getConfig().getStringList("block-whitelist")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                loaded.add(material);
            } else {
                this.plugin.getLogger().warning("BlockPlaceListener: unknown material '" + name + "' in block-whitelist.");
            }
        }
        this.whitelist = loaded;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("frostffa.buildpermission")) return;

        if (this.plugin.getBuildGate().denied(event.getBlock().getLocation())) {
            event.setCancelled(true);
            MessageUtil.sendError(player, Palette.sc("You cannot build here."));
            return;
        }

        if (!this.whitelist.contains(event.getBlock().getType())) {
            event.setCancelled(true);
            MessageUtil.sendError(player, Palette.sc("That block is not in the whitelist."));
            return;
        }

        if (event.getBlock().getType() == Material.COBWEB && this.aboveCobwebLimit(event.getBlock().getY())) {
            event.setCancelled(true);
            MessageUtil.sendError(player, Palette.sc("You cannot place cobwebs this high."));
        }
    }

    private boolean aboveCobwebLimit(int y) {
        return y >= this.plugin.getConfig().getInt("building.cobweb-max-y", 107);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("frostffa.buildpermission")) return;

        if (this.plugin.getBuildGate().denied(event.getBlock().getLocation())) {
            event.setCancelled(true);
            MessageUtil.sendError(player, Palette.sc("You cannot break blocks here."));
            return;
        }

        Material material = event.getBlock().getType();
        if (material == Material.COBWEB && this.aboveCobwebLimit(event.getBlock().getY())) {
            event.setCancelled(true);
            MessageUtil.sendError(player, Palette.sc("You cannot break cobwebs this high."));
            return;
        }

        if (this.plugin.getConfig().getStringList("building.always-breakable").stream()
                .map(Material::matchMaterial)
                .anyMatch(material::equals)) {
            return;
        }

        if (this.plugin.getConfig().getBoolean("building.player-placed-only", true)
                && !this.plugin.getBlockLedger().isPlaced(event.getBlock().getLocation())) {
            event.setCancelled(true);
            MessageUtil.sendError(player, Palette.sc("You can only break blocks you placed."));
            return;
        }

        this.plugin.getBlockLedger().removeBlock(event.getBlock().getLocation());
    }

    // Ledgered only once the placement actually stuck — matching every other plugin's
    // veto (this listener's own, or a later one) having had its chance to cancel it first.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void recordPlacement(BlockPlaceEvent event) {
        if (event.getPlayer().hasPermission("frostffa.buildpermission")) return;
        if (!this.whitelist.contains(event.getBlock().getType())) return;
        this.plugin.getBlockLedger().addBlock(event.getBlock().getLocation());
    }
}
