package dev.notskrib.frostffacore.shop;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import dev.notskrib.frostffacore.core.util.Slug;
import dev.notskrib.frostffacore.gui.FrostGui;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * /store admin — create/delete shops and jump into editing one. Click an existing shop to open
 * its {@link ShopAdminGui}; shift-click to delete it; click an empty slot to create a new one
 * by typing its name in chat (icon defaults to a chest, changeable later if needed).
 */
public class ShopAdminListGui extends FrostGui {

    private static final int ROWS = 6;
    private static final int CLOSE_SLOT = ROWS * 9 - 1;

    private final Map<Integer, String> slotIds = new HashMap<>();

    public ShopAdminListGui(FrostFFACore plugin) {
        super(plugin, ROWS, Palette.b(Palette.WARN, "admin: shops"));
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
                Palette.l(Palette.GLACIER, "▶ Click to edit items"),
                Palette.l(Palette.BAD, "▶ Shift-click to delete")));
            this.slotIds.put(slots.get(i), shop.id());
            i++;
        }
        for (; i < slots.size(); i++) {
            this.inventory.setItem(slots.get(i), icon(Material.LIME_STAINED_GLASS_PANE,
                Palette.l(Palette.GLACIER, "+ new shop"),
                Palette.l(Palette.INK, "click to create a shop")));
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
        if (shopId != null) {
            if (click.isShiftClick()) {
                if (this.plugin.getShopManager().deleteShop(shopId)) {
                    success(player);
                    this.redraw(player);
                }
            } else {
                click(player);
                new ShopAdminGui(this.plugin, shopId).open(player);
            }
            return;
        }

        click(player);
        this.plugin.getChatInputManager().await(player,
            Palette.l(Palette.INK, "Type a name for the new shop."),
            text -> {
                String id = Slug.unique(Slug.of(text, "shop"), this.plugin.getShopManager()::hasShop);
                this.plugin.getShopManager().createShop(id, text.trim(), Material.CHEST);
                MessageUtil.sendSuccess(player, "Created shop '" + text.trim() + "'.");
                new ShopAdminListGui(this.plugin).openNextTick(player);
            },
            () -> new ShopAdminListGui(this.plugin).openNextTick(player));
    }
}
