package dev.notskrib.frostffacore.arena;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

/** Keeps the Mace out of play entirely — no crafting it, no picking one up off the ground. */
public class MaceBlockListener implements Listener {

    private final FrostFFACore plugin;

    public MaceBlockListener(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return this.plugin.getConfig().getBoolean("building.block-mace", true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!this.enabled()) return;
        ItemStack result = event.getInventory().getResult();
        if (result != null && result.getType() == Material.MACE) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(CraftItemEvent event) {
        if (!this.enabled()) return;
        if (event.getRecipe().getResult().getType() == Material.MACE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!this.enabled() || !(event.getEntity() instanceof Player player)) return;
        if (player.hasPermission("frostffa.buildpermission")) return;
        Item entity = event.getItem();
        if (entity.getItemStack().getType() != Material.MACE) return;
        event.setCancelled(true);
        MessageUtil.sendError(player, Palette.sc("The Mace is disabled on this server."));
    }
}
