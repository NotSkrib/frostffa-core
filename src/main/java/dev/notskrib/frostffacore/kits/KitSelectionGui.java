package dev.notskrib.frostffacore.kits;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Chest GUI for {@code /kit}: browse configured kits and claim one, subject to a per-kit cooldown. */
public final class KitSelectionGui implements Listener {
    private final FrostFFACore plugin;
    private final KitManager kits;
    private final Map<UUID, Map<String, Long>> cooldownExpiry = new HashMap<>();
    private Map<String, Material> tierIcons;

    public KitSelectionGui(FrostFFACore plugin, KitManager kits) {
        this.plugin = plugin;
        this.kits = kits;
    }

    public void open(Player player) {
        List<String> kitIds = eligibleKits(player);
        if (kitIds.isEmpty()) {
            MessageUtil.reply(player, "You do not have any available kits.");
            return;
        }
        int size = Math.min(54, Math.max(9, ((kitIds.size() + 8) / 9) * 9));
        Inventory inventory = Bukkit.createInventory(new Holder(), size,
            Component.text("Kits", NamedTextColor.AQUA, TextDecoration.BOLD));
        for (int i = 0; i < kitIds.size() && i < size; i++) {
            inventory.setItem(i, icon(player, kitIds.get(i)));
        }
        player.openInventory(inventory);
    }

    private List<String> eligibleKits(Player player) {
        List<String> configured = plugin.getConfig().getStringList("kit-npc.kits");
        List<String> result = new ArrayList<>();
        for (String raw : configured) {
            if (raw == null || raw.isBlank()) continue;
            String kit = raw.trim().toLowerCase(Locale.ROOT);
            if (player.hasPermission("frostffa.kit." + kit) && kits.exists(kit)) {
                result.add(kit);
            }
        }
        if (!result.contains("default") && player.hasPermission("frostffa.kit.default") && kits.exists("default")) {
            result.add(0, "default");
        }
        return result.stream().distinct().toList();
    }

    private ItemStack icon(Player player, String kit) {
        long remaining = remainingCooldownSeconds(player, kit);
        ItemStack item = new ItemStack(iconMaterial(kit));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
            (remaining > 0 ? "&7" : "&b&l") + capitalize(kit)));
        List<String> lore = new ArrayList<>();
        lore.add(remaining > 0
            ? ChatColor.translateAlternateColorCodes('&', "&cAvailable in " + formatDuration(remaining))
            : ChatColor.translateAlternateColorCodes('&', "&aClick to claim!"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Material iconMaterial(String kit) {
        Map<String, Material> icons = tierIcons();
        return icons.getOrDefault(kit, kit.equals("default") ? Material.LEATHER_CHESTPLATE : Material.CHEST);
    }

    private Map<String, Material> tierIcons() {
        if (tierIcons != null) return tierIcons;
        Map<String, Material> icons = new HashMap<>();
        File ranksFile = new File(plugin.getDataFolder(), "ranks.yml");
        YamlConfiguration ranks = YamlConfiguration.loadConfiguration(ranksFile);
        for (String path : new String[] {"free-tiers", "paid-tiers"}) {
            ConfigurationSection section = ranks.getConfigurationSection(path);
            if (section == null) continue;
            for (String key : section.getKeys(false)) {
                ConfigurationSection tier = section.getConfigurationSection(key);
                if (tier == null) continue;
                String kit = tier.getString("kit");
                String iconName = tier.getString("icon");
                if (kit == null || iconName == null) continue;
                Material material = Material.matchMaterial(iconName);
                if (material != null) icons.put(kit.toLowerCase(Locale.ROOT), material);
            }
        }
        tierIcons = icons;
        return icons;
    }

    private long remainingCooldownSeconds(Player player, String kit) {
        Map<String, Long> perPlayer = cooldownExpiry.get(player.getUniqueId());
        if (perPlayer == null) return 0;
        Long expiry = perPlayer.get(kit);
        if (expiry == null) return 0;
        long remainingMillis = expiry - System.currentTimeMillis();
        return remainingMillis > 0 ? (remainingMillis + 999) / 1000 : 0;
    }

    private void startCooldown(Player player, String kit) {
        long seconds = plugin.getConfig().getLong("kit-npc.cooldown-seconds", 300);
        if (seconds <= 0) return;
        cooldownExpiry.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>())
            .put(kit, System.currentTimeMillis() + seconds * 1000);
    }

    private String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return minutes > 0 ? minutes + "m " + secs + "s" : secs + "s";
    }

    private String capitalize(String kit) {
        return kit.isEmpty() ? kit : Character.toUpperCase(kit.charAt(0)) + kit.substring(1);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;
        String kit = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
        if (!player.hasPermission("frostffa.kit." + kit) || !kits.exists(kit)) {
            MessageUtil.sendError(player, "That kit is no longer available.");
            return;
        }
        long remaining = remainingCooldownSeconds(player, kit);
        if (remaining > 0) {
            MessageUtil.sendError(player, "That kit is on cooldown for " + formatDuration(remaining) + ".");
            return;
        }
        if (kits.apply(player, kit)) {
            startCooldown(player, kit);
            player.closeInventory();
            MessageUtil.sendSuccess(player, "You received the " + capitalize(kit) + " kit.");
        } else {
            MessageUtil.sendError(player, "That kit has not been configured yet.");
        }
    }

    private static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
