package dev.notskrib.frostffacore.gui;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public abstract class FrostGui implements InventoryHolder {

    protected static final MiniMessage MM = MiniMessage.miniMessage();

    protected final FrostFFACore plugin;
    protected final Inventory inventory;

    protected FrostGui(FrostFFACore plugin, int rows, String titleMiniMessage) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, rows * 9,
            MM.deserialize(titleMiniMessage).decoration(TextDecoration.ITALIC, false));
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    public abstract void onClick(Player player, int slot, ClickType click, ItemStack cursor);

    public void onClose(Player player) {}

    public void open(Player player) {
        this.redraw(player);
        player.openInventory(this.inventory);
    }

    public void openNextTick(Player player) {
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) this.open(player);
        });
    }

    public abstract void redraw(Player player);

    protected void fillBorder() {
        ItemStack pane = icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "<!italic> ");
        int size = this.inventory.getSize();
        for (int i = 0; i < size; i++) {
            boolean edge = i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8;
            if (edge) this.inventory.setItem(i, pane);
        }
    }

    protected void fillEmpty() {
        ItemStack pane = icon(Material.BLACK_STAINED_GLASS_PANE, "<!italic> ");
        for (int i = 0; i < this.inventory.getSize(); i++) {
            if (this.inventory.getItem(i) == null) this.inventory.setItem(i, pane);
        }
    }

    protected static ItemStack icon(Material material, String nameMiniMessage, String... loreMiniMessage) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<!italic>" + nameMiniMessage));
        if (loreMiniMessage.length > 0) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreMiniMessage) {
                lore.add(MM.deserialize("<!italic>" + line));
            }
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    protected static ItemStack decorate(ItemStack source, String... extraLore) {
        ItemStack copy = source.clone();
        ItemMeta meta = copy.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        if (!lore.isEmpty()) lore.add(Component.empty());
        for (String line : extraLore) {
            lore.add(MM.deserialize("<!italic>" + line));
        }
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    protected ItemStack backButton() {
        return icon(Material.ARROW, Palette.l(Palette.INK, "◀ Back"));
    }

    protected ItemStack closeButton() {
        return icon(Material.BARRIER, Palette.l(Palette.BAD, "✖ Close"));
    }

    protected static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
    }

    protected static void success(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.4f);
    }

    protected static void deny(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.1f);
    }

    protected static List<Integer> interior(int rows) {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col < 8; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    protected static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }
}
