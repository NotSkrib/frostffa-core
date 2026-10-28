package dev.notskrib.frostffacore.daily;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import dev.notskrib.frostffacore.core.util.TimeUtil;
import dev.notskrib.frostffacore.economy.Points;
import dev.notskrib.frostffacore.gui.FrostGui;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** /daily — one icon per tier, claim by clicking (plan §6.5). */
public class DailyGui extends FrostGui {

    private static final int ROWS = 4;
    private static final int[] TIER_SLOTS = {11, 13, 15, 20, 22, 24, 29, 31, 33};
    private static final int HEAD_SLOT = 8;
    private static final int CLOSE_SLOT = ROWS * 9 - 1;

    private final Map<Integer, Integer> slotTier = new HashMap<>();

    public DailyGui(FrostFFACore plugin) {
        super(plugin, ROWS, Palette.b(Palette.BRAND, "daily rewards"));
    }

    @Override
    public void redraw(Player player) {
        this.inventory.clear();
        this.slotTier.clear();
        this.fillBorder();
        this.fillEmpty();

        DailyManager daily = this.plugin.getDailyManager();
        Map<Integer, DailyTier> ordered = new TreeMap<>(daily.tiers());

        int i = 0;
        for (DailyTier tier : ordered.values()) {
            if (i >= TIER_SLOTS.length) break;
            this.inventory.setItem(TIER_SLOTS[i], this.buildTierIcon(player, daily, tier));
            this.slotTier.put(TIER_SLOTS[i], tier.id());
            i++;
        }

        this.inventory.setItem(HEAD_SLOT, this.buildHead(player, daily));
        this.inventory.setItem(CLOSE_SLOT, this.closeButton());
    }

    private ItemStack buildTierIcon(Player player, DailyManager daily, DailyTier tier) {
        boolean hasPermission = tier.permission().isEmpty() || player.hasPermission(tier.permission());
        long remaining = daily.remainingCooldown(player.getUniqueId(), tier.id());

        String status;
        if (!hasPermission) {
            status = Palette.l(Palette.BAD, "🔒 requires " + tier.permission());
        } else if (remaining > 0) {
            status = Palette.l(Palette.DIM, "⏳ ready in " + TimeUtil.shortDuration(remaining));
        } else if (tier.isEmpty()) {
            status = Palette.l(Palette.DIM, "nothing configured yet");
        } else {
            status = Palette.l(Palette.BRAND, "▶ click to claim");
        }

        return icon(tier.displayMaterial(), tier.displayName(),
            Palette.l(Palette.INK, "payout ") + Points.colored(tier.pointsAmount(), this.plugin.getEconomyManager()),
            Palette.l(Palette.INK, "items " + tier.items().size()),
            "",
            status);
    }

    private ItemStack buildHead(Player player, DailyManager daily) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(MM.deserialize("<!italic>" + Palette.c(Palette.GLACIER, player.getName())));
        int streak = daily.streakCount(player.getUniqueId());
        meta.lore(java.util.List.of(
            MM.deserialize("<!italic>" + Palette.l(Palette.INK, "claim streak ")
                + Palette.c(Palette.WARN, streak + " day" + (streak == 1 ? "" : "s")))));
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

        Integer tierId = this.slotTier.get(slot);
        if (tierId == null) return;

        DailyManager.ClaimResult result = this.plugin.getDailyManager().claim(player, tierId);
        switch (result) {
            case OK -> {
                success(player);
                MessageUtil.sendSuccess(player, "Claimed your daily reward!");
            }
            case ON_COOLDOWN -> {
                deny(player);
                long remaining = this.plugin.getDailyManager().remainingCooldown(player.getUniqueId(), tierId);
                MessageUtil.sendError(player, "Come back in " + TimeUtil.shortDuration(remaining) + ".");
            }
            case NO_PERMISSION -> {
                deny(player);
                MessageUtil.sendError(player, "You don't have permission for that tier.");
            }
            case NO_SPACE -> {
                deny(player);
                MessageUtil.sendError(player, "Your inventory is full.");
            }
            case EMPTY_STAGE -> {
                deny(player);
                MessageUtil.sendError(player, "That tier isn't set up yet.");
            }
            case BUSY -> deny(player);
            case UNKNOWN_STAGE -> deny(player);
        }
        this.redraw(player);
    }
}
