package dev.notskrib.frostffacore.shop;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import dev.notskrib.frostffacore.economy.Points;
import dev.notskrib.frostffacore.gui.FrostGui;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** /store <shop> — browse one shop's admin-built catalog and buy with a single click. */
public class ShopGui extends FrostGui {

    private static final int ROWS = 6;
    private static final int BACK_SLOT = ROWS * 9 - 6;
    private static final int CLOSE_SLOT = ROWS * 9 - 1;

    private final String shopId;
    private final Map<Integer, String> slotIds = new HashMap<>();

    public ShopGui(FrostFFACore plugin, String shopId) {
        super(plugin, ROWS, title(plugin, shopId));
        this.shopId = shopId;
    }

    private static String title(FrostFFACore plugin, String shopId) {
        ShopCategory shop = plugin.getShopManager().shop(shopId);
        return Palette.b(Palette.BRAND, "❄ " + (shop != null ? shop.name() : shopId));
    }

    @Override
    public void redraw(Player player) {
        this.inventory.clear();
        this.slotIds.clear();
        this.fillBorder();

        List<Integer> slots = interior(ROWS);
        int i = 0;
        for (ShopEntry entry : this.plugin.getShopManager().entries(this.shopId)) {
            if (i >= slots.size()) break;
            boolean afford = this.plugin.getEconomyManager().has(player.getUniqueId(), entry.price());
            this.inventory.setItem(slots.get(i), decorate(entry.display().clone(),
                "",
                Points.colored(entry.price(), this.plugin.getEconomyManager()),
                "",
                afford ? Palette.l(Palette.GLACIER, "▶ Click to buy")
                       : Palette.l(Palette.BAD, "you can't afford this")));
            this.slotIds.put(slots.get(i), entry.id());
            i++;
        }
        this.inventory.setItem(BACK_SLOT, this.backButton());
        this.inventory.setItem(CLOSE_SLOT, this.closeButton());
    }

    @Override
    public void onClick(Player player, int slot, ClickType click, ItemStack cursor) {
        if (slot == CLOSE_SLOT) {
            click(player);
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            click(player);
            new ShopListGui(this.plugin).open(player);
            return;
        }
        String id = this.slotIds.get(slot);
        if (id == null) return;

        ShopEntry entry = this.plugin.getShopManager().get(this.shopId, id);
        ShopManager.BuyResult result = this.plugin.getShopManager().buy(player, this.shopId, id);
        switch (result) {
            case SUCCESS -> {
                success(player);
                String priceStr = entry != null ? Points.format(entry.price(), this.plugin.getEconomyManager()) : "coins";
                MessageUtil.sendSuccess(player, "Purchased item for " + priceStr + ".");
            }
            case INSUFFICIENT_FUNDS -> {
                deny(player);
                MessageUtil.sendError(player, "You don't have enough coins!");
            }
            case INVENTORY_FULL -> {
                deny(player);
                MessageUtil.sendError(player, "Your inventory is full.");
            }
            case WITHDRAW_FAILED -> {
                deny(player);
                MessageUtil.sendError(player, "Payment failed — try again.");
            }
            case UNKNOWN_ITEM, SHOP_NOT_FOUND -> {
                deny(player);
                MessageUtil.sendError(player, "That item is no longer available.");
            }
        }
        this.redraw(player);
    }
}
