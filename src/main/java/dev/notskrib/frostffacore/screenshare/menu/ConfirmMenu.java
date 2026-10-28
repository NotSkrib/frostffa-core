package dev.notskrib.frostffacore.screenshare.menu;

import dev.notskrib.frostffacore.screenshare.config.ScreenShareConfig;
import dev.notskrib.frostffacore.screenshare.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/** "Are you sure?" step so a misclick in {@link ScreenShareMenu} does not self-ban. */
public final class ConfirmMenu implements InventoryHolder {

    public enum Action {
        ADMIT,
        REFUSE
    }

    public static final int SIZE = 27;

    private final Inventory inventory;
    private final Action action;

    public ConfirmMenu(ScreenShareConfig config, Action action) {
        this.action = action;
        this.inventory = Bukkit.createInventory(this, SIZE, Text.item(config.confirmTitle()));

        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, ScreenShareMenu.icon(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        }
        inventory.setItem(ScreenShareMenu.clamp(config.confirmSlot()),
                ScreenShareMenu.icon(Material.LIME_CONCRETE, "&a&lConfirm",
                        List.of("&7Yes, I understand I will be banned.")));
        inventory.setItem(ScreenShareMenu.clamp(config.cancelSlot()),
                ScreenShareMenu.icon(Material.RED_CONCRETE, "&c&lGo back", List.of("&7Return to the previous screen.")));
    }

    public Action action() {
        return action;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
