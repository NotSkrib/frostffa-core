package dev.notskrib.frostffacore.screenshare.menu;

import dev.notskrib.frostffacore.screenshare.config.ScreenShareConfig;
import dev.notskrib.frostffacore.screenshare.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** The forced menu the detainee sees: "Admit Defeat" or "Refuse Screen Share". */
public final class ScreenShareMenu implements InventoryHolder {

    public static final int SIZE = 27;

    private final Inventory inventory;

    public ScreenShareMenu(ScreenShareConfig config) {
        this.inventory = Bukkit.createInventory(this, SIZE, Text.item(config.menuTitle()));

        ItemStack filler = icon(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(clamp(config.admitSlot()),
                icon(config.admitMaterial(), config.admitName(), config.admitLore()));
        inventory.setItem(clamp(config.refuseSlot()),
                icon(config.refuseMaterial(), config.refuseName(), config.refuseLore()));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    static int clamp(int slot) {
        return Math.max(0, Math.min(SIZE - 1, slot));
    }

    static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.item(name));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Text.item(line));
            }
            meta.lore(lines);
            item.setItemMeta(meta);
        }
        return item;
    }
}
