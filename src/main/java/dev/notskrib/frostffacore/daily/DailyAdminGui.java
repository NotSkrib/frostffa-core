package dev.notskrib.frostffacore.daily;

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
 * /daily admin &lt;tier&gt; — build one tier's item payload in-game: hold an item, click
 * an empty slot to add it; click an existing item to remove it; click the money button to
 * retype its coin payout via chat (plan §6.5).
 */
public class DailyAdminGui extends FrostGui {

    private static final int ROWS = 4;
    private static final int MONEY_SLOT = 4;
    private static final int CLOSE_SLOT = ROWS * 9 - 1;

    private final int tierId;
    private final Map<Integer, String> slotSlugs = new HashMap<>();

    public DailyAdminGui(FrostFFACore plugin, int tierId) {
        super(plugin, ROWS, Palette.b(Palette.WARN, "admin: daily tier " + tierId));
        this.tierId = tierId;
    }

    @Override
    public void redraw(Player player) {
        this.inventory.clear();
        this.slotSlugs.clear();
        this.fillBorder();

        DailyTier tier = this.plugin.getDailyManager().tiers().get(this.tierId);
        if (tier == null) {
            this.inventory.setItem(MONEY_SLOT, icon(Material.BARRIER, Palette.l(Palette.BAD, "unknown tier")));
            this.inventory.setItem(CLOSE_SLOT, this.closeButton());
            return;
        }

        this.inventory.setItem(MONEY_SLOT, icon(Material.SUNFLOWER, Palette.b(Palette.SHARD, "coin payout"),
            Points.colored(tier.pointsAmount(), this.plugin.getEconomyManager()),
            "",
            Palette.l(Palette.GLACIER, "▶ Click to retype")));

        List<Integer> slots = interior(ROWS);
        int i = 0;
        for (Map.Entry<String, ItemStack> entry : tier.items().entrySet()) {
            if (i >= slots.size()) break;
            this.inventory.setItem(slots.get(i), decorate(entry.getValue().clone(),
                "",
                Palette.l(Palette.BAD, "▶ Click to remove")));
            this.slotSlugs.put(slots.get(i), entry.getKey());
            i++;
        }
        for (; i < slots.size(); i++) {
            this.inventory.setItem(slots.get(i), icon(Material.LIME_STAINED_GLASS_PANE,
                Palette.l(Palette.GLACIER, "+ empty slot"),
                Palette.l(Palette.INK, "hold an item and click"),
                Palette.l(Palette.INK, "here to add it to this tier.")));
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
        DailyTier tier = this.plugin.getDailyManager().tiers().get(this.tierId);
        if (tier == null) return;

        if (slot == MONEY_SLOT) {
            click(player);
            this.plugin.getChatInputManager().await(player,
                Palette.l(Palette.INK, "Type the payout for this tier."),
                text -> {
                    int amount = Points.parseAllowingZero(text);
                    if (amount < 0) {
                        MessageUtil.sendError(player, "'" + text + "' is not a valid amount.");
                    } else {
                        this.plugin.getDailyManager().setTierMoney(this.tierId, amount);
                        MessageUtil.sendSuccess(player, "Set the payout to "
                            + Points.format(amount, this.plugin.getEconomyManager()) + ".");
                    }
                    new DailyAdminGui(this.plugin, this.tierId).openNextTick(player);
                },
                () -> new DailyAdminGui(this.plugin, this.tierId).openNextTick(player));
            return;
        }

        String slug = this.slotSlugs.get(slot);
        if (slug != null) {
            if (this.plugin.getDailyManager().removeTierItem(this.tierId, slug)) {
                success(player);
                this.redraw(player);
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
        String slugFinal = Slug.unique(desired, s -> tier.items().containsKey(s));

        this.plugin.getDailyManager().setTierItem(this.tierId, slugFinal, snapshot);
        success(player);
        MessageUtil.sendSuccess(player, "Added '" + slugFinal + "' to this tier.");
        this.redraw(player);
    }
}
