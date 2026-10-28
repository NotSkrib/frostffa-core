package dev.notskrib.frostffacore.bounty;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import dev.notskrib.frostffacore.economy.EconomyAdmin;
import dev.notskrib.frostffacore.economy.Points;
import dev.notskrib.frostffacore.gui.FrostGui;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** /bounty — paginated (capped, no page controls yet) list of bounty targets by pool descending, plan §6.9. */
public class BountyGui extends FrostGui {

    private static final int ROWS = 6;
    private static final int PLACE_SLOT = 49;
    private static final int CLOSE_SLOT = 45;

    private final Map<Integer, java.util.UUID> slotTarget = new HashMap<>();

    public BountyGui(FrostFFACore plugin) {
        super(plugin, ROWS, Palette.b(Palette.BRAND, "bounties"));
    }

    @Override
    public void redraw(Player player) {
        this.inventory.clear();
        this.slotTarget.clear();
        this.fillBorder();
        this.fillEmpty();

        List<Integer> slots = interior(ROWS);
        List<Bounty> top = this.plugin.getBountyManager().topByPool(slots.size());
        for (int i = 0; i < top.size(); i++) {
            Bounty bounty = top.get(i);
            this.inventory.setItem(slots.get(i), this.buildHead(bounty));
            this.slotTarget.put(slots.get(i), bounty.target());
        }

        this.inventory.setItem(PLACE_SLOT, icon(Material.GOLD_NUGGET, Palette.b(Palette.WARN, "+ place a bounty"),
            Palette.l(Palette.INK, "▶ Click to type a target"),
            Palette.l(Palette.INK, "and an amount in chat.")));
        this.inventory.setItem(CLOSE_SLOT, this.closeButton());
    }

    private ItemStack buildHead(Bounty bounty) {
        OfflinePlayer offline = this.plugin.getServer().getOfflinePlayer(bounty.target());
        String name = offline.getName() != null ? offline.getName() : bounty.target().toString();
        boolean online = offline.isOnline();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(offline);
        meta.displayName(MM.deserialize("<!italic>" + Palette.c(Palette.GLACIER, name)));
        meta.lore(List.of(
            MM.deserialize("<!italic>" + Palette.l(Palette.INK, "pool ")
                + Points.colored(bounty.pool(), this.plugin.getEconomyManager())),
            MM.deserialize("<!italic>" + Palette.l(Palette.INK, bounty.contributorCount() + " contributor(s)")),
            MM.deserialize("<!italic>" + (online
                ? Palette.l(Palette.BRAND, "● online")
                : Palette.l(Palette.DIM, "● offline")))
        ));
        head.setItemMeta(meta);
        return head;
    }

    @Override
    public void onClick(Player player, int slot, ClickType click, ItemStack cursor) {
        if (slot == CLOSE_SLOT) {
            click(player);
            player.closeInventory();
            return;
        }
        if (slot == PLACE_SLOT) {
            click(player);
            this.plugin.getChatInputManager().await(player,
                Palette.l(Palette.INK, "Type ") + Palette.c(Palette.GLACIER, "<player> <amount>")
                    + Palette.l(Palette.INK, "."),
                text -> this.handlePlace(player, text),
                () -> new BountyGui(this.plugin).openNextTick(player));
            return;
        }

        java.util.UUID target = this.slotTarget.get(slot);
        if (target != null) {
            OfflinePlayer offline = this.plugin.getServer().getOfflinePlayer(target);
            click(player);
            MessageUtil.send(player, Palette.c(Palette.GLACIER, offline.getName())
                + Palette.l(Palette.INK, " has a bounty of ")
                + Points.colored(this.plugin.getBountyManager().poolOf(target), this.plugin.getEconomyManager()));
        }
    }

    private void handlePlace(Player player, String text) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 2) {
            MessageUtil.sendError(player, "Usage: <player> <amount>");
            new BountyGui(this.plugin).openNextTick(player);
            return;
        }
        OfflinePlayer target = EconomyAdmin.resolve(this.plugin, parts[0]);
        int amount = Points.parse(parts[1]);
        if (amount <= 0) {
            MessageUtil.sendError(player, "'" + parts[1] + "' is not a valid amount.");
        } else {
            BountyCommand.reportPlace(player, this.plugin.getBountyManager().place(player, target, amount),
                parts[0], amount);
        }
        new BountyGui(this.plugin).openNextTick(player);
    }
}
