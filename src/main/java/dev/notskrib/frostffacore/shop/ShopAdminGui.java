package dev.notskrib.frostffacore.shop;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import dev.notskrib.frostffacore.core.util.Slug;
import dev.notskrib.frostffacore.economy.Points;
import dev.notskrib.frostffacore.gui.FrostGui;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * /store admin <shop> — hold an item and click an empty slot to price and add it to THIS shop;
 * click an existing entry to remove it, or shift-click to retype its price. Same "hold item,
 * click slot" flow as {@code DailyAdminGui}, just with a per-item price and per-shop scope.
 */
public class ShopAdminGui extends FrostGui {

    private static final int ROWS = 6;
    private static final int BACK_SLOT = ROWS * 9 - 6;
    private static final int CLOSE_SLOT = ROWS * 9 - 1;

    private final String shopId;
    private final Map<Integer, String> slotIds = new HashMap<>();

    public ShopAdminGui(FrostFFACore plugin, String shopId) {
        super(plugin, ROWS, title(plugin, shopId));
        this.shopId = shopId;
    }

    private static String title(FrostFFACore plugin, String shopId) {
        ShopCategory shop = plugin.getShopManager().shop(shopId);
        return Palette.b(Palette.WARN, "admin: " + (shop != null ? shop.name() : shopId));
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
            this.inventory.setItem(slots.get(i), decorate(entry.display().clone(),
                "",
                Points.colored(entry.price(), this.plugin.getEconomyManager()),
                "",
                Palette.l(Palette.BAD, "▶ Click to remove"),
                Palette.l(Palette.GLACIER, "▶ Shift-click to reprice")));
            this.slotIds.put(slots.get(i), entry.id());
            i++;
        }
        for (; i < slots.size(); i++) {
            this.inventory.setItem(slots.get(i), icon(Material.LIME_STAINED_GLASS_PANE,
                Palette.l(Palette.GLACIER, "+ empty slot"),
                Palette.l(Palette.INK, "hold an item and click"),
                Palette.l(Palette.INK, "here to add it to the shop.")));
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
            new ShopAdminListGui(this.plugin).open(player);
            return;
        }

        String id = this.slotIds.get(slot);
        if (id != null) {
            if (click.isShiftClick()) {
                this.reprice(player, id);
            } else {
                if (this.plugin.getShopManager().remove(this.shopId, id)) {
                    success(player);
                    this.redraw(player);
                }
            }
            return;
        }

        if (isEmpty(cursor)) {
            deny(player);
            MessageUtil.sendError(player, "Hold an item, then click an empty slot to add it.");
            return;
        }

        ItemStack snapshot = cursor.clone();
        String rawName = snapshot.hasItemMeta() && snapshot.getItemMeta().hasDisplayName()
            ? PlainTextComponentSerializer.plainText().serialize(snapshot.getItemMeta().displayName())
            : snapshot.getType().name();
        String desired = Slug.of(rawName, snapshot.getType().name().toLowerCase());
        String id2 = Slug.unique(desired, candidate -> this.plugin.getShopManager().contains(this.shopId, candidate));

        click(player);
        this.plugin.getChatInputManager().await(player,
            Palette.l(Palette.INK, "Type the price for " + rawName + "."),
            text -> {
                int amount = Points.parse(text);
                if (amount < 0) {
                    MessageUtil.sendError(player, "'" + text + "' is not a valid price.");
                } else {
                    this.plugin.getShopManager().put(this.shopId, id2, snapshot, amount);
                    MessageUtil.sendSuccess(player, "Added '" + id2 + "' at "
                        + Points.format(amount, this.plugin.getEconomyManager()) + ".");
                }
                new ShopAdminGui(this.plugin, this.shopId).openNextTick(player);
            },
            () -> new ShopAdminGui(this.plugin, this.shopId).openNextTick(player));
    }

    private void reprice(Player player, String id) {
        ShopEntry entry = this.plugin.getShopManager().get(this.shopId, id);
        if (entry == null) return;
        click(player);
        this.plugin.getChatInputManager().await(player,
            Palette.l(Palette.INK, "Type the new price for '" + id + "'."),
            text -> {
                int amount = Points.parse(text);
                if (amount < 0) {
                    MessageUtil.sendError(player, "'" + text + "' is not a valid price.");
                } else {
                    this.plugin.getShopManager().put(this.shopId, id, entry.display(), amount);
                    MessageUtil.sendSuccess(player, "Repriced '" + id + "' to "
                        + Points.format(amount, this.plugin.getEconomyManager()) + ".");
                }
                new ShopAdminGui(this.plugin, this.shopId).openNextTick(player);
            },
            () -> new ShopAdminGui(this.plugin, this.shopId).openNextTick(player));
    }
}
