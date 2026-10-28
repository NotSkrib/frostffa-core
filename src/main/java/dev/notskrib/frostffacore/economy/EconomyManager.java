package dev.notskrib.frostffacore.economy;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Physical kill-token economy. Tokens are Nether Stars marked with a private PDC key,
 * so renamed or unrelated Nether Stars cannot be spent as currency.
 */
public final class EconomyManager implements IManager, Listener {
    private final FrostFFACore plugin;
    private final Queue<String> auditQueue = new ConcurrentLinkedQueue<>();
    private final Map<UUID, Boolean> inTransaction = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pending = new ConcurrentHashMap<>();
    private File auditFile;
    private File pendingFile;
    private NamespacedKey tokenKey;
    private String tokenName;

    public EconomyManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.auditFile = new File(plugin.getDataFolder(), "transactions.log");
        this.pendingFile = new File(plugin.getDataFolder(), "pending-kill-tokens.yml");
        this.tokenKey = new NamespacedKey(plugin, "kill_token");
        this.tokenName = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("economy.kill-token-name", "&e&lᴋɪʟʟᴛᴏᴋᴇɴ"));
        loadPending();
        plugin.getLogger().info("EconomyManager enabled — physical Nether Star kill tokens.");
    }

    @Override
    public void onDisable() {
        savePending();
        drainAudit();
    }

    public void reload() {
        this.tokenName = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("economy.kill-token-name", "&e&lᴋɪʟʟᴛᴏᴋᴇɴ"));
    }

    public String currencyNameSingular() { return "Kill Token"; }
    public String currencyNamePlural() { return "Kill Tokens"; }

    public int balance(UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
        return player == null ? pending.getOrDefault(uuid, 0) : balance(player);
    }

    public int balance(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isToken(item)) total += item.getAmount();
        }
        return total;
    }

    public String formatted(UUID uuid) {
        return Points.format(balance(uuid), this);
    }

    public boolean has(UUID uuid, int amount) {
        Player player = plugin.getServer().getPlayer(uuid);
        return player != null && balance(player) >= amount;
    }

    public boolean beginTransaction(UUID uuid) {
        return inTransaction.putIfAbsent(uuid, Boolean.TRUE) == null;
    }

    public void endTransaction(UUID uuid) {
        inTransaction.remove(uuid);
    }

    public boolean deposit(UUID uuid, int amount, String reason) {
        if (amount <= 0) return false;
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) {
            pending.merge(uuid, amount, Integer::sum);
            savePending();
        } else {
            give(player, amount);
        }
        audit("DEPOSIT", uuid, amount, reason);
        return true;
    }

    public boolean withdraw(UUID uuid, int amount, String reason) {
        if (amount <= 0) return false;
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || balance(player) < amount) return false;
        int left = amount;
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack item = contents[slot];
            if (!isToken(item)) continue;
            int removed = Math.min(left, item.getAmount());
            if (removed == item.getAmount()) contents[slot] = null;
            else item.setAmount(item.getAmount() - removed);
            left -= removed;
        }
        inventory.setStorageContents(contents);
        audit("WITHDRAW", uuid, amount, reason);
        return true;
    }

    public ItemStack token(int amount) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(tokenName);
        meta.setLore(List.of(ChatColor.GRAY + "A token earned from slaying a player."));
        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private void give(Player player, int amount) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(token(amount));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private boolean isToken(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(tokenKey, PersistentDataType.BYTE);
        String displayName = item.getItemMeta().getDisplayName();
        // Accept the legacy Skript token name so existing tokens remain spendable after
        // switching the server to FrostFFACore's physical economy.
        return (marker != null && marker == 1) || tokenName.equals(displayName);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Integer amount = pending.remove(event.getPlayer().getUniqueId());
        if (amount != null && amount > 0) {
            give(event.getPlayer(), amount);
            savePending();
        }
    }

    private void audit(String type, UUID uuid, int amount, String reason) {
        auditQueue.add(type + " | " + uuid + " | " + Points.format(amount) + " | " + reason);
        drainAudit();
    }

    private void drainAudit() {
        if (auditQueue.isEmpty()) return;
        List<String> batch = new ArrayList<>();
        for (String line = auditQueue.poll(); line != null; line = auditQueue.poll()) batch.add(line);
        try {
            java.nio.file.Files.write(auditFile.toPath(), batch,
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not append to transactions.log: " + e.getMessage());
        }
    }

    private void loadPending() {
        if (!pendingFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(pendingFile);
        for (String key : config.getStringList("tokens")) {
            String[] split = key.split("=", 2);
            if (split.length != 2) continue;
            try { pending.put(UUID.fromString(split[0]), Integer.parseInt(split[1])); }
            catch (IllegalArgumentException ignored) { }
        }
    }

    private void savePending() {
        YamlConfiguration config = new YamlConfiguration();
        List<String> values = pending.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue()).toList();
        config.set("tokens", values);
        try { config.save(pendingFile); }
        catch (IOException e) { plugin.getLogger().warning("Could not save pending tokens: " + e.getMessage()); }
    }
}
