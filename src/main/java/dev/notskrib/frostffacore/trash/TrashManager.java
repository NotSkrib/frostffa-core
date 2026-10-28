package dev.notskrib.frostffacore.trash;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.manager.IManager;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TrashManager implements IManager, Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int CONTROL_ROW = SIZE - 9;
    private static final int CLEAR_SLOT = SIZE - 5;

    private static final long CONFIRM_WINDOW_MS = 5000L;

    private final FrostFFACore plugin;
    private final Map<UUID, Long> armed = new HashMap<>();

    public TrashManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.plugin.getLogger().info("TrashManager enabled.");
    }

    @Override
    public void onDisable() {
        this.armed.clear();
        this.plugin.getLogger().info("TrashManager disabled.");
    }

    public void open(Player player) {
        TrashHolder holder = new TrashHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
            MM.deserialize(Palette.b(Palette.BRAND, "trash")).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inventory);

        this.drawControls(inventory, false);
        this.armed.remove(player.getUniqueId());
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.7f, 1.1f);
    }

    private void drawControls(Inventory inventory, boolean confirming) {
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = CONTROL_ROW; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(CLEAR_SLOT, confirming
            ? icon(Material.LAVA_BUCKET, Palette.b(Palette.BAD, "ARE YOU SURE?"),
                Palette.l(Palette.INK, "click again to empty your"),
                Palette.l(Palette.INK, "inventory, armour and offhand."),
                "",
                Palette.l(Palette.BAD, "this cannot be undone."))
            : icon(Material.BUCKET, Palette.b(Palette.BAD, "CLEAR INVENTORY"),
                Palette.l(Palette.INK, "empties your inventory,"),
                Palette.l(Palette.INK, "armour and offhand."),
                "",
                Palette.l(Palette.INK, "asks once before it does.")));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TrashHolder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getClickedInventory() == null) return;
        if (!(event.getClickedInventory().getHolder() instanceof TrashHolder)) return;

        int slot = event.getSlot();
        if (slot < CONTROL_ROW) return;

        event.setCancelled(true);
        if (slot != CLEAR_SLOT) return;
        // A number-key swap targets a hotbar slot, not the one clicked — refusing it
        // outright is simpler than reasoning about what it would have moved.
        if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND) return;

        this.handleClear(player, event.getView().getTopInventory());
    }

    private void handleClear(Player player, Inventory inventory) {
        UUID uuid = player.getUniqueId();
        Long armedAt = this.armed.get(uuid);
        long now = System.currentTimeMillis();

        if (armedAt == null || now - armedAt > CONFIRM_WINDOW_MS) {
            this.armed.put(uuid, now);
            this.drawControls(inventory, true);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.7f);
            MessageUtil.sendActionBar(player, Palette.b(Palette.BAD, "clear inventory")
                + " <dark_gray>›</dark_gray> " + Palette.l(Palette.INK, "click again to confirm"));
            return;
        }

        this.armed.remove(uuid);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();

        this.drawControls(inventory, false);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f);
        MessageUtil.send(player, Palette.l(Palette.INK, "inventory cleared."));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TrashHolder)) return;
        // A drag can spread one stack across many slots in a single packet, so it only takes
        // one of them landing on the control strip to matter.
        for (int raw : event.getRawSlots()) {
            if (raw >= CONTROL_ROW && raw < SIZE) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TrashHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        int binned = 0;
        for (int slot = 0; slot < CONTROL_ROW; slot++) {
            ItemStack item = event.getInventory().getItem(slot);
            if (item != null && !item.getType().isAir()) binned += item.getAmount();
        }
        this.armed.remove(player.getUniqueId());
        if (binned > 0) {
            MessageUtil.send(player, Palette.l(Palette.INK, "binned ")
                + Palette.c(Palette.GLACIER, String.valueOf(binned))
                + Palette.l(Palette.INK, binned == 1 ? " item." : " items."));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7f, 0.9f);
        }
    }

    private static ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            meta.lore(java.util.Arrays.stream(lore)
                .map(line -> MM.deserialize(line).decoration(TextDecoration.ITALIC, false))
                .toList());
        } else {
            meta.lore(List.of());
        }
        item.setItemMeta(meta);
        return item;
    }
}
