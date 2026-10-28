package dev.notskrib.frostffacore.shop;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.gui.FrostGui;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** /store — pick which shop to browse. */
public class ShopListGui extends FrostGui {

    private static final int ROWS = 6;
    private static final int CLOSE_SLOT = ROWS * 9 - 1;

    private final Map<Integer, String> slotIds = new HashMap<>();

    public ShopListGui(FrostFFACore plugin) {
        super(plugin, ROWS, Palette.b(Palette.BRAND, "❄ shops"));
    }

    @Override
    public void redraw(Player player) {
        this.inventory.clear();
        this.slotIds.clear();
        this.fillBorder();

        List<Integer> slots = interior(ROWS);
        int i = 0;
        for (ShopCategory shop : this.plugin.getShopManager().shops()) {
            if (i >= slots.size()) break;
            this.inventory.setItem(slots.get(i), icon(shop.icon(),
                Palette.b(Palette.GLACIER, shop.name()),
                Palette.l(Palette.INK, shop.entries().size() + " item(s)"),
                "",
                Palette.l(Palette.GLACIER, "▶ Click to browse")));
            this.slotIds.put(slots.get(i), shop.id());
            i++;
        }
        if (i == 0) {
            this.inventory.setItem(slots.get(0), icon(org.bukkit.Material.BARRIER,
                Palette.l(Palette.BAD, "No shops yet.")));
        }
        this.inventory.setItem(CLOSE_SLOT, this.closeButton());
    }

    @Override
    public void onClick(Player player, int slot, ClickType click, ItemStack cursor) {
        if (slot == CLOSE_SLOT) {
            click(player);
            player.closeInventory();
            return;
        }
        String shopId = this.slotIds.get(slot);
        if (shopId == null) return;
        click(player);
        new ShopGui(this.plugin, shopId).open(player);
    }
}
