package dev.notskrib.frostffacore.commands;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import dev.notskrib.frostffacore.kits.KitManager;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/** Chest-based kit editor with explicit Save/Cancel actions. */
public final class KitLayoutEditor implements CommandExecutor, Listener, TabCompleter {
    private static final int SAVE_SLOT = 45;
    private static final int RESET_SLOT = 46;
    private static final int CANCEL_SLOT = 47;
    private static final int IMPORT_SLOT = 48;
    private static final int PLAYER_SAVE_SLOT = 11;
    private static final int PLAYER_RESET_SLOT = 13;
    private static final int PLAYER_CANCEL_SLOT = 15;

    private final FrostFFACore plugin;
    private final KitManager kits;
    private final File layoutsFile;

    public KitLayoutEditor(FrostFFACore plugin, KitManager kits) {
        this.plugin = plugin;
        this.kits = kits;
        this.layoutsFile = new File(plugin.getDataFolder(), "kit-layouts.yml");
    }

    private void importIntoChest(Inventory editor, ItemStack[] items) {
        for (int slot = 0; slot < 41; slot++) editor.setItem(slot, items[slot]);
    }

    private ItemStack[] chestKitContents(Inventory editor) {
        ItemStack[] contents = new ItemStack[41];
        for (int slot = 0; slot < contents.length; slot++) {
            contents[slot] = editor.getItem(slot);
        }
        return contents;
    }

    private void loadIntoPlayer(Player player, ItemStack[] items) {
        ItemStack[] storage = Arrays.copyOf(items, 36);
        player.getInventory().setStorageContents(storage);
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < armor.length && 36 + i < items.length; i++) {
            armor[i] = items[36 + i];
        }
        player.getInventory().setArmorContents(armor);
        if (items.length > 40) player.getInventory().setItemInOffHand(items[40]);
    }

    public void openAdminEditor(Player player, String kit) {
        open(player, kit, true);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.reply(sender, "Players only.");
            return true;
        }
        String kit = args.length == 0
            ? plugin.getConfig().getString("kit-layout.default-kit", "default") : args[0];
        if (kit == null || kit.isBlank()) {
            MessageUtil.reply(player, "Specify a kit.");
            return true;
        }
        kit = kit.toLowerCase(Locale.ROOT);
        if (!kits.exists(kit)) {
            MessageUtil.reply(player, "That kit does not exist. Available kits: "
                + String.join(", ", kits.kitNames()));
            return true;
        }
        if (!player.hasPermission("frostffa.kits.admin")
                && !player.hasPermission("frostffa.kit." + kit)) {
            MessageUtil.reply(player, "You do not have permission for that kit.");
            return true;
        }
        open(player, kit, false);
        return true;
    }

    private void open(Player player, String kit, boolean admin) {
        Holder holder = new Holder(kit, admin, player);
        int inventorySize = admin ? 54 : 27;
        Inventory inventory = Bukkit.createInventory(holder, inventorySize,
            net.kyori.adventure.text.Component.text((admin ? "Edit kit: " : "Layout: ") + kit));
        ItemStack[] saved = admin ? loadKit(kit) : loadLayout(player, kit);
        if (admin) {
            for (int slot = 0; slot < Math.min(41, saved.length); slot++) {
                if (saved[slot] != null) inventory.setItem(slot, saved[slot].clone());
            }
        }
        if (admin) {
            for (int slot = 41; slot <= 44; slot++) inventory.setItem(slot, icon(Material.GRAY_STAINED_GLASS_PANE, " "));
            for (int slot = 48; slot <= 53; slot++) inventory.setItem(slot, icon(Material.GRAY_STAINED_GLASS_PANE, " "));
            inventory.setItem(SAVE_SLOT, icon(Material.LIME_WOOL, "&a&lSAVE"));
            inventory.setItem(RESET_SLOT, icon(Material.YELLOW_WOOL, "&e&lRESET"));
            inventory.setItem(CANCEL_SLOT, icon(Material.RED_WOOL, "&c&lCANCEL"));
            inventory.setItem(IMPORT_SLOT, icon(Material.HOPPER, "&b&lIMPORT INVENTORY"));
        } else {
            for (int slot = 0; slot < 27; slot++) {
                inventory.setItem(slot, icon(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
            inventory.setItem(PLAYER_SAVE_SLOT, icon(Material.LIME_WOOL, "&a&lSAVE"));
            inventory.setItem(PLAYER_RESET_SLOT, icon(Material.YELLOW_WOOL, "&e&lRESET"));
            inventory.setItem(PLAYER_CANCEL_SLOT, icon(Material.RED_WOOL, "&c&lCANCEL"));
        }
        if (!admin) {
            player.getInventory().clear();
            loadIntoPlayer(player, saved);
            player.setItemOnCursor(null);
        }
        player.openInventory(inventory);
        MessageUtil.sendInfo(player, admin
            ? "Arrange the kit, then click SAVE, RESET or CANCEL."
            : "Arrange your layout, then click SAVE, RESET or CANCEL.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)
                || !(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        // Admins edit the kit in the chest inventory. Players edit their personal
        // layout in their player inventory, so their chest only contains controls.
        if (!holder.admin && slot < topSize && slot != PLAYER_SAVE_SLOT
                && slot != PLAYER_RESET_SLOT && slot != PLAYER_CANCEL_SLOT) {
            event.setCancelled(true);
            return;
        }
        if (holder.admin && slot >= topSize) {
            event.setCancelled(true);
            return;
        }
        if (holder.admin && slot >= 41 && slot != SAVE_SLOT
                && slot != RESET_SLOT && slot != CANCEL_SLOT && slot != IMPORT_SLOT) {
            event.setCancelled(true);
            return;
        }
        if (holder.admin && slot == SAVE_SLOT || !holder.admin && slot == PLAYER_SAVE_SLOT) {
            event.setCancelled(true);
            if (event.getWhoClicked().getItemOnCursor().getType() != Material.AIR) {
                MessageUtil.reply(player, "Place the item back into the layout before saving.");
                return;
            }
            if (!holder.admin && containsForbidden(playerKitContents(player))) {
                MessageUtil.reply(player, "That kit contains an item disallowed by the FFA kit rules.");
                return;
            }
            save(player, holder);
            holder.finished = true;
            player.closeInventory();
        } else if (holder.admin && slot == RESET_SLOT || !holder.admin && slot == PLAYER_RESET_SLOT) {
            event.setCancelled(true);
            reset(event.getInventory(), holder, player);
            MessageUtil.sendInfo(player, "Layout reset to the original kit.");
        } else if (holder.admin && slot == CANCEL_SLOT || !holder.admin && slot == PLAYER_CANCEL_SLOT) {
            event.setCancelled(true);
            holder.finished = true;
            player.closeInventory();
            MessageUtil.sendInfo(player, "Kit changes cancelled.");
        } else if (slot == IMPORT_SLOT && holder.admin) {
            event.setCancelled(true);
            importIntoChest(event.getInventory(), holder.originalContents());
            MessageUtil.sendInfo(player, "Imported your inventory from before the editor opened.");
        }

        if (!holder.admin && slot >= topSize
                && (event.isShiftClick() || event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.DOUBLE_CLICK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize
                && (!holder.admin || (slot >= 41 && slot != SAVE_SLOT
                && slot != RESET_SLOT && slot != CANCEL_SLOT && slot != IMPORT_SLOT)))) {
            event.setCancelled(true);
        }
        if (!holder.admin && event.getRawSlots().stream().anyMatch(slot -> slot >= topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)
                || !(event.getPlayer() instanceof Player player)) return;
        if (!holder.admin) {
            restore(player, holder);
        }
        if (!holder.finished) MessageUtil.sendInfo(player, "Kit changes cancelled.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof Holder holder
                && !holder.admin) {
            restore(event.getPlayer(), holder);
        }
    }

    private void save(Player player, Holder holder) {
        ItemStack[] contents = holder.admin
            ? chestKitContents(player.getOpenInventory().getTopInventory())
            : playerKitContents(player);
        if (holder.admin) kits.saveKit(holder.kit, contents);
        else {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(layoutsFile);
            config.set("layouts." + player.getUniqueId() + "." + holder.kit, Arrays.asList(contents));
            try { config.save(layoutsFile); }
            catch (IOException e) { plugin.getLogger().warning("Could not save kit layout: " + e.getMessage()); }
        }
        MessageUtil.sendSuccess(player, "Kit layout saved.");
    }

    private ItemStack[] playerKitContents(Player player) {
        ItemStack[] contents = new ItemStack[41];
        System.arraycopy(player.getInventory().getStorageContents(), 0, contents, 0, 36);
        System.arraycopy(player.getInventory().getArmorContents(), 0, contents, 36, 4);
        contents[40] = player.getInventory().getItemInOffHand();
        return contents;
    }

    private ItemStack[] loadKit(String kit) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "kits.yml"));
        List<?> list = config.getList("kits." + kit);
        return toItems(list);
    }

    private ItemStack[] loadLayout(Player player, String kit) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(layoutsFile);
        List<?> personal = config.getList("layouts." + player.getUniqueId() + "." + kit);
        return personal == null ? loadKit(kit) : toItems(personal);
    }

    private ItemStack[] toItems(List<?> list) {
        ItemStack[] items = new ItemStack[41];
        if (list != null) for (int i = 0; i < Math.min(items.length, list.size()); i++)
            if (list.get(i) instanceof ItemStack item) items[i] = item.clone();
        return items;
    }

    private void reset(Inventory editor, Holder holder, Player player) {
        ItemStack[] items = loadKit(holder.kit);
        if (holder.admin) {
            for (int slot = 0; slot < 41; slot++) {
                editor.setItem(slot, items[slot]);
            }
            return;
        }
        player.getInventory().clear();
        loadIntoPlayer(player, items);
        player.setItemOnCursor(null);
    }

    private void restore(Player player, Holder holder) {
        player.getInventory().setStorageContents(holder.storage);
        player.getInventory().setArmorContents(holder.armor);
        player.getInventory().setItemInOffHand(holder.offhand);
        player.setItemOnCursor(holder.cursor);
    }

    private ItemStack icon(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
        item.setItemMeta(meta);
        return item;
    }

    private boolean containsForbidden(ItemStack[] items) {
        List<String> names = plugin.getConfig().getStringList("kit-layout.disallowed-names");
        List<String> materials = plugin.getConfig().getStringList("kit-layout.disallowed-materials");
        for (ItemStack item : items) if (item != null) {
            String material = item.getType().name().toLowerCase(Locale.ROOT);
            String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName().toLowerCase(Locale.ROOT) : "";
            if (material.equals("nether_star") || materials.stream().anyMatch(material::equals)
                    || names.stream().anyMatch(name::contains)) return true;
        }
        return false;
    }

    private static final class Holder implements InventoryHolder {
        private final String kit;
        private final boolean admin;
        private final ItemStack[] storage;
        private final ItemStack[] armor;
        private final ItemStack offhand;
        private final ItemStack cursor;
        private boolean finished;
        private Holder(String kit, boolean admin, Player player) {
            this.kit = kit;
            this.admin = admin;
            this.storage = cloneItems(player.getInventory().getStorageContents());
            this.armor = cloneItems(player.getInventory().getArmorContents());
            this.offhand = player.getInventory().getItemInOffHand() == null
                ? null : player.getInventory().getItemInOffHand().clone();
            this.cursor = player.getItemOnCursor() == null ? null : player.getItemOnCursor().clone();
        }
        @Override public Inventory getInventory() { return null; }

        private ItemStack[] originalContents() {
            ItemStack[] contents = new ItemStack[41];
            System.arraycopy(storage, 0, contents, 0, 36);
            System.arraycopy(armor, 0, contents, 36, 4);
            contents[40] = offhand;
            return contents;
        }

        private static ItemStack[] cloneItems(ItemStack[] source) {
            ItemStack[] copy = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++) {
                copy[i] = source[i] == null ? null : source[i].clone();
            }
            return copy;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        return kits.kitNames().stream()
            .filter(kit -> kit.toLowerCase(Locale.ROOT).startsWith(input)).toList();
    }
}
