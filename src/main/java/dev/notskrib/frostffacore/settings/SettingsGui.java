package dev.notskrib.frostffacore.settings;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.gui.FrostGui;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class SettingsGui extends FrostGui {

    private static final int[] SLOTS = { 10, 11, 12, 13, 14, 15 };

    private final PlayerSetting[] settings = PlayerSetting.values();

    public SettingsGui(FrostFFACore plugin) {
        super(plugin, 4, Palette.b(Palette.BRAND, "settings"));
    }

    @Override
    public void redraw(Player player) {
        this.inventory.clear();
        this.fillBorder();

        for (int i = 0; i < this.settings.length && i < SLOTS.length; i++) {
            PlayerSetting setting = this.settings[i];
            boolean on = this.plugin.getSettingsManager().get(player, setting);

            List<String> lore = new ArrayList<>();
            for (String line : setting.description()) {
                lore.add(Palette.l(Palette.INK, line));
            }
            lore.add("");
            lore.add(on ? Palette.b(Palette.BRAND, "on") : Palette.b(Palette.BAD, "off"));
            lore.add(Palette.l(Palette.DIM, "click to turn " + (on ? "off" : "on")));

            this.inventory.setItem(SLOTS[i], icon(on ? setting.icon() : Material.GRAY_DYE,
                Palette.c(on ? Palette.GLACIER : Palette.DIM, setting.label()),
                lore.toArray(new String[0])));
        }

        this.inventory.setItem(this.inventory.getSize() - 1, this.closeButton());
        this.fillEmpty();
    }

    @Override
    public void onClick(Player player, int slot, ClickType clickType, ItemStack cursor) {
        if (slot == this.inventory.getSize() - 1) {
            click(player);
            player.closeInventory();
            return;
        }
        for (int i = 0; i < this.settings.length && i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            boolean now = this.plugin.getSettingsManager().toggle(player.getUniqueId(), this.settings[i]);
            if (this.settings[i] == PlayerSetting.SCOREBOARD) {
                // TAB owns the actual sidebar and its own per-player visibility state — this
                // setting is just a menu shortcut onto TAB's own toggle command rather than a
                // thing FrostFFACore renders itself.
                player.performCommand("sb");
            }
            if (now) {
                success(player);
            } else {
                click(player);
            }
            this.redraw(player);
            return;
        }
    }
}
