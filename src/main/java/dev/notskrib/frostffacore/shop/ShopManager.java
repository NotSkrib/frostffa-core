package dev.notskrib.frostffacore.shop;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Multiple named, admin-built catalogs — no NPC entity, no rotation. Each {@link ShopCategory}
 * (e.g. "weapons", "cosmetics") holds its own flat item list; admins add/reprice/remove items
 * per-shop through {@link ShopAdminGui}, players browse and buy through {@link ShopGui}. Shops
 * themselves are created/deleted through {@link ShopAdminListGui}.
 */
public class ShopManager implements IManager {

    public enum BuyResult { SUCCESS, INSUFFICIENT_FUNDS, INVENTORY_FULL, UNKNOWN_ITEM, WITHDRAW_FAILED, SHOP_NOT_FOUND }

    private final FrostFFACore plugin;
    private final Map<String, ShopCategory> shops = new LinkedHashMap<>();
    private File file;

    public ShopManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.file = new File(this.plugin.getDataFolder(), "shops.yml");
        this.load();
        this.plugin.getLogger().info("ShopManager enabled — " + this.shops.size() + " shop(s).");
    }

    @Override
    public void onDisable() {
        this.save();
        this.plugin.getLogger().info("ShopManager disabled.");
    }

    public void reload() {
        this.load();
    }

    public Collection<ShopCategory> shops() {
        return this.shops.values();
    }

    public ShopCategory shop(String shopId) {
        return this.shops.get(shopId);
    }

    public boolean hasShop(String shopId) {
        return this.shops.containsKey(shopId);
    }

    public boolean createShop(String shopId, String name, Material icon) {
        if (this.shops.containsKey(shopId)) return false;
        this.shops.put(shopId, new ShopCategory(shopId, name, icon));
        this.save();
        return true;
    }

    public boolean deleteShop(String shopId) {
        boolean removed = this.shops.remove(shopId) != null;
        if (removed) this.save();
        return removed;
    }

    public Collection<ShopEntry> entries(String shopId) {
        ShopCategory shop = this.shops.get(shopId);
        return shop == null ? java.util.List.of() : shop.entries().values();
    }

    public ShopEntry get(String shopId, String id) {
        ShopCategory shop = this.shops.get(shopId);
        return shop == null ? null : shop.entries().get(id);
    }

    public boolean contains(String shopId, String id) {
        ShopCategory shop = this.shops.get(shopId);
        return shop != null && shop.entries().containsKey(id);
    }

    public void put(String shopId, String id, ItemStack display, int price) {
        ShopCategory shop = this.shops.get(shopId);
        if (shop == null) return;
        shop.entries().put(id, new ShopEntry(id, display.clone(), price));
        this.save();
    }

    public boolean remove(String shopId, String id) {
        ShopCategory shop = this.shops.get(shopId);
        if (shop == null) return false;
        boolean removed = shop.entries().remove(id) != null;
        if (removed) this.save();
        return removed;
    }

    /**
     * Charges the player, then hands over a fresh copy of the item. Checks funds AND
     * inventory space before touching either — a failed delivery must never have already
     * taken the player's points, and points are only withdrawn once delivery is guaranteed
     * to succeed (same ordering discipline as the removed Store's PurchaseService).
     */
    public BuyResult buy(Player player, String shopId, String id) {
        ShopCategory shop = this.shops.get(shopId);
        if (shop == null) return BuyResult.SHOP_NOT_FOUND;
        ShopEntry entry = shop.entries().get(id);
        if (entry == null) return BuyResult.UNKNOWN_ITEM;

        if (!this.plugin.getEconomyManager().has(player.getUniqueId(), entry.price())) {
            return BuyResult.INSUFFICIENT_FUNDS;
        }
        ItemStack toGive = entry.display().clone();
        if (player.getInventory().firstEmpty() == -1 && !canStackInto(player, toGive)) {
            return BuyResult.INVENTORY_FULL;
        }
        if (!this.plugin.getEconomyManager().withdraw(player.getUniqueId(), entry.price(),
                "shop purchase: " + shopId + "/" + entry.id())) {
            return BuyResult.WITHDRAW_FAILED;
        }
        player.getInventory().addItem(toGive);
        return BuyResult.SUCCESS;
    }

    private static boolean canStackInto(Player player, ItemStack item) {
        for (ItemStack existing : player.getInventory().getStorageContents()) {
            if (existing != null && existing.isSimilar(item) && existing.getAmount() < existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void load() {
        this.shops.clear();
        if (!this.file.exists()) return;
        YamlConfiguration in = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection root = in.getConfigurationSection("shops");
        if (root == null) return;
        for (String shopId : root.getKeys(false)) {
            ConfigurationSection shopSection = root.getConfigurationSection(shopId);
            if (shopSection == null) continue;
            String name = shopSection.getString("name", shopId);
            Material icon = Material.matchMaterial(shopSection.getString("icon", "CHEST"));
            if (icon == null) icon = Material.CHEST;
            ShopCategory shop = new ShopCategory(shopId, name, icon);

            ConfigurationSection items = shopSection.getConfigurationSection("items");
            if (items != null) {
                for (String id : items.getKeys(false)) {
                    ConfigurationSection section = items.getConfigurationSection(id);
                    if (section == null) continue;
                    ItemStack display = section.getItemStack("item");
                    int price = section.getInt("price", 0);
                    if (display == null) continue;
                    shop.entries().put(id, new ShopEntry(id, display, price));
                }
            }
            this.shops.put(shopId, shop);
        }
    }

    private void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (ShopCategory shop : this.shops.values()) {
            String shopBase = "shops." + shop.id();
            out.set(shopBase + ".name", shop.name());
            out.set(shopBase + ".icon", shop.icon().name());
            for (ShopEntry entry : shop.entries().values()) {
                String base = shopBase + ".items." + entry.id();
                out.set(base + ".item", entry.display());
                out.set(base + ".price", entry.price());
            }
        }
        try {
            out.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Could not save shops.yml: " + e.getMessage());
        }
    }
}
