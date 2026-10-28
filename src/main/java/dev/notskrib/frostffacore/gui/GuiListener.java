package dev.notskrib.frostffacore.gui;

import dev.notskrib.frostffacore.FrostFFACore;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {

    private final FrostFFACore plugin;

    public GuiListener(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof FrostGui gui)) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory clicked = event.getClickedInventory();
        boolean clickedTop = clicked != null && clicked.equals(top);

        if (!clickedTop) {
            // A click in the player's OWN inventory (not the GUI) — leave ordinary clicks
            // alone so a player can freely pick an item up onto their cursor (needed by
            // every "hold item, click an empty slot" admin screen), but still block
            // shift-click, since that's vanilla's "dump into the other inventory" action
            // and would silently deposit an item into the GUI's display slots.
            if (event.isShiftClick()) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
            }
            return;
        }

        event.setCancelled(true);
        event.setResult(Event.Result.DENY);

        if (event.getSlot() < 0 || event.getSlot() >= top.getSize()) return;

        try {
            gui.onClick(player, event.getSlot(), event.getClick(), event.getCursor());
        } catch (Exception e) {
            // A screen that throws must not leave the player holding a half-finished
            // transaction, and must not leak a stack trace as a "reward".
            player.closeInventory();
            this.plugin.getLogger().severe("GUI click failed in " + gui.getClass().getSimpleName() + ": " + e);
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof FrostGui) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof FrostGui gui
            && event.getPlayer() instanceof Player player) {
            gui.onClose(player);
        }
    }
}
