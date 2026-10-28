package dev.notskrib.frostffacore.rank;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.gui.FrostGui;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * /rank list — 6 rows: Free Ranks in slots 10-17, Paid Donator Ranks in slots 20-24,
 * and player overall status head in slot 31.
 */
public class RankGui extends FrostGui {

    private static final int ROWS = 6;
    private static final int[] FREE_TIER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 17};
    private static final int[] PAID_TIER_SLOTS = {20, 21, 22, 23, 24};
    private static final int PROGRESS_SLOT = 31;
    private static final int PROGRESS_BAR_LENGTH = 20;

    public RankGui(FrostFFACore plugin) {
        super(plugin, ROWS, Palette.b(Palette.BRAND, "ranks & donator perks"));
    }

    @Override
    public void redraw(Player player) {
        this.inventory.clear();
        this.fillBorder();
        this.fillEmpty();

        RankManager ranks = this.plugin.getRankManager();
        long kills = this.plugin.getKillsManager().getKills(player.getUniqueId());
        RankTier currentFree = ranks.tierOf(kills);
        PaidRankTier highestPaid = ranks.highestPaidTierOf(player);

        List<RankTier> freeTiers = ranks.tiers();
        for (int i = 0; i < freeTiers.size() && i < FREE_TIER_SLOTS.length; i++) {
            RankTier tier = freeTiers.get(i);
            boolean unlocked = currentFree != null && tier.killsRequired() <= kills;
            long remaining = Math.max(0L, tier.killsRequired() - kills);

            this.inventory.setItem(FREE_TIER_SLOTS[i], icon(tier.icon(), tier.coloredName(),
                Palette.l(Palette.INK, "free kill rank"),
                Palette.l(Palette.INK, "requires ") + Palette.c(Palette.GLACIER, tier.killsRequired() + " kills"),
                Palette.l(Palette.INK, "kit ") + Palette.c(Palette.GLACIER, tier.kit()),
                Palette.l(Palette.INK, "reward bonus ") + Palette.c(Palette.WARN, "+" + tier.rewardBonusPercent() + "%"),
                "",
                unlocked
                    ? Palette.l(Palette.BRAND, "✔ unlocked")
                    : Palette.l(Palette.DIM, "🔒 " + remaining + " kills to go")));
        }

        List<PaidRankTier> paidTiers = ranks.paidTiers();
        for (int i = 0; i < paidTiers.size() && i < PAID_TIER_SLOTS.length; i++) {
            PaidRankTier paid = paidTiers.get(i);
            boolean owned = player.hasPermission(paid.permission());

            this.inventory.setItem(PAID_TIER_SLOTS[i], icon(paid.icon(), paid.coloredName(),
                Palette.l(Palette.INK, "donator rank"),
                Palette.l(Palette.INK, paid.description()),
                Palette.l(Palette.INK, "kit ") + Palette.c(Palette.GLACIER, paid.kit()),
                Palette.l(Palette.INK, "reward bonus ") + Palette.c(Palette.WARN, "+" + paid.rewardBonusPercent() + "%"),
                "",
                owned
                    ? Palette.l(Palette.BRAND, "✔ unlocked (donator)")
                    : Palette.l(Palette.DIM, "🔒 available in store")));
        }

        this.inventory.setItem(PROGRESS_SLOT, this.buildProgressHead(player, ranks, currentFree, highestPaid, kills));
        this.inventory.setItem(this.inventory.getSize() - 1, this.closeButton());
    }

    private ItemStack buildProgressHead(Player player, RankManager ranks, RankTier currentFree, PaidRankTier highestPaid, long kills) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(MM.deserialize("<!italic>" + Palette.c(Palette.GLACIER, player.getName())));

        RankTier nextTier = ranks.next(currentFree);
        int totalBonus = (int) Math.round(ranks.rewardBonus(player) * 100);

        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        lore.add(MM.deserialize("<!italic>" + Palette.l(Palette.INK, "total kills ")
            + Palette.c(Palette.GLACIER, String.valueOf(kills))));
        lore.add(MM.deserialize("<!italic>" + Palette.l(Palette.INK, "free rank ")
            + (currentFree != null ? currentFree.coloredName() : Palette.c(Palette.DIM, "none"))));
        lore.add(MM.deserialize("<!italic>" + Palette.l(Palette.INK, "donator rank ")
            + (highestPaid != null ? highestPaid.coloredName() : Palette.c(Palette.DIM, "none"))));
        lore.add(MM.deserialize("<!italic>" + Palette.l(Palette.INK, "total coin bonus ")
            + Palette.c(Palette.WARN, "+" + totalBonus + "%")));
        lore.add(net.kyori.adventure.text.Component.empty());

        if (nextTier == null) {
            lore.add(MM.deserialize("<!italic>" + Palette.l(Palette.BRAND, "maximum free rank reached")));
        } else {
            long span = Math.max(1L, nextTier.killsRequired() - (currentFree != null ? currentFree.killsRequired() : 0L));
            long into = Math.max(0L, kills - (currentFree != null ? currentFree.killsRequired() : 0L));
            int filled = (int) Math.min(PROGRESS_BAR_LENGTH, (into * PROGRESS_BAR_LENGTH) / span);
            StringBuilder bar = new StringBuilder("<!italic><color:").append(Palette.BRAND).append(">");
            bar.append("█".repeat(Math.max(0, filled)));
            bar.append("<color:").append(Palette.DIM).append(">");
            bar.append("░".repeat(Math.max(0, PROGRESS_BAR_LENGTH - filled)));
            lore.add(MM.deserialize(bar.toString()));
            lore.add(MM.deserialize("<!italic>" + Palette.l(Palette.INK, "next free rank: ") + nextTier.coloredName()
                + Palette.l(Palette.INK, " at ") + Palette.c(Palette.GLACIER, nextTier.killsRequired() + " kills")));
        }
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    @Override
    public void onClick(Player player, int slot, ClickType click, ItemStack cursor) {
        if (slot == this.inventory.getSize() - 1) {
            click(player);
            player.closeInventory();
            return;
        }
        click(player);
    }
}
