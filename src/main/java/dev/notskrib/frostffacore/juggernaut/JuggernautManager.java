package dev.notskrib.frostffacore.juggernaut;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import dev.notskrib.frostffacore.core.manager.IManager;

public final class JuggernautManager implements IManager, Listener {
    private final FrostFFACore plugin;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> alive = new HashSet<>();
    private final Map<UUID, ItemStack[]> savedContents = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();
    private final Map<UUID, ItemStack> savedOffhand = new HashMap<>();
    private final Map<UUID, List<PotionEffect>> appliedEffects = new HashMap<>();
    private static final int ARMOR_SLOT = 36;
    private static final int OFFHAND_SLOT = 40;
    private static final int EXTRACT_BUTTON_SLOT = 44;
    private UUID juggernaut;
    private boolean running;
    private File kitFile;

    public JuggernautManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override public void onEnable() {
        kitFile = new File(plugin.getDataFolder(), "juggernaut-kit.yml");
    }

    @Override public void onDisable() {
        stop(false);
    }

    public boolean isRunning() { return running; }

    public String status() {
        if (!running) return "Juggernaut is not running.";
        Player player = juggernaut == null ? null : Bukkit.getPlayer(juggernaut);
        return "Juggernaut: " + (player == null ? "offline" : player.getName())
            + " | alive: " + alive.size() + "/" + participants.size();
    }

    public boolean start() {
        if (running) return false;
        List<Player> eligible = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getAfkManager().isAfk(player)) eligible.add(player);
        }
        if (eligible.size() < 2) return false;
        Player selected = eligible.get((int) (Math.random() * eligible.size()));
        for (Player player : eligible) {
            participants.add(player.getUniqueId());
            alive.add(player.getUniqueId());
        }
        juggernaut = selected.getUniqueId();
        running = true;
        broadcast("messages.started", "<yellow>Juggernaut event started! <player> is the Juggernaut.");
        if (plugin.getConfig().getBoolean("juggernaut.name-roll.enabled", true)) {
            runNameRoll(eligible, selected);
        } else {
            equip(selected);
            broadcast("messages.juggernaut", "<gold><player> is the Juggernaut!</gold>");
        }
        return true;
    }

    /**
     * A slot-machine-style reveal: flashes random eligible names as a title, decelerating,
     * before landing on the actual Juggernaut — equip() and the "chosen" broadcast only fire
     * once the roll settles, so the kit doesn't appear before the reveal does.
     */
    private void runNameRoll(List<Player> eligible, Player winner) {
        MiniMessage mm = MiniMessage.miniMessage();
        Random random = new Random();
        int[] delaysTicks = {2, 2, 2, 3, 3, 3, 4, 4, 5, 6, 7, 9, 11, 14, 18};

        long cumulative = 0;
        for (int step = 0; step < delaysTicks.length; step++) {
            cumulative += delaysTicks[step];
            final float soundPitch = 0.8f + (1.0f * step / delaysTicks.length);
            String name = eligible.get(random.nextInt(eligible.size())).getName();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Title.Times times = Title.Times.times(
                    Duration.ZERO, Duration.ofMillis(250), Duration.ofMillis(50));
                Component main = mm.deserialize("<color:#4FC3F7><bold>SELECTING JUGGERNAUT...</bold></color>");
                Component sub = mm.deserialize("<color:#FFD166><bold>" + name + "</bold></color>");
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.showTitle(Title.title(main, sub, times));
                    online.playSound(online.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, soundPitch);
                }
            }, cumulative);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Title.Times times = Title.Times.times(
                Duration.ofMillis(150), Duration.ofMillis(1800), Duration.ofMillis(500));
            Component main = mm.deserialize("<color:#FFD166><bold>" + winner.getName() + "</bold></color>");
            Component sub = mm.deserialize("<color:#4FC3F7><bold>IS THE JUGGERNAUT!</bold></color>");
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showTitle(Title.title(main, sub, times));
                online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
            }
            equip(winner);
            broadcast("messages.juggernaut", "<gold><player> is the Juggernaut!</gold>", winner.getName());
        }, cumulative + 4);
    }

    public void stop() { stop(true); }

    private void stop(boolean announce) {
        if (!running && participants.isEmpty()) return;
        cleanup();
        if (announce) broadcast("messages.stopped", "<gray>Juggernaut event stopped.</gray>");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!running) return;
        UUID victim = event.getEntity().getUniqueId();
        if (!participants.remove(victim)) return;
        alive.remove(victim);
        broadcast("messages.eliminated", "<red><player> has been eliminated.</red>",
            event.getEntity().getName());
        if (alive.size() == 1) {
            UUID winner = alive.iterator().next();
            Player player = Bukkit.getPlayer(winner);
            broadcast("messages.winner", "<green><player> is the last survivor!</green>",
                player == null ? "Unknown" : player.getName());
            cleanup();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!running || !participants.remove(event.getPlayer().getUniqueId())) return;
        alive.remove(event.getPlayer().getUniqueId());
        if (alive.size() == 1) {
            UUID winner = alive.iterator().next();
            Player player = Bukkit.getPlayer(winner);
            broadcast("messages.winner", "<green><player> is the last survivor!</green>",
                player == null ? "Unknown" : player.getName());
            cleanup();
        }
    }

    private void equip(Player player) {
        UUID id = player.getUniqueId();
        savedContents.put(id, player.getInventory().getContents().clone());
        savedArmor.put(id, player.getInventory().getArmorContents().clone());
        savedOffhand.put(id, player.getInventory().getItemInOffHand().clone());
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        ConfigurationSection kit = loadKit();
        if (kit != null) {
            List<?> savedItems = kit.getList("items");
            if (savedItems != null) {
                for (int slot = 0; slot < savedItems.size() && slot < player.getInventory().getSize(); slot++) {
                    if (savedItems.get(slot) instanceof ItemStack item && item.getType() != Material.AIR) {
                        player.getInventory().setItem(slot, item);
                    }
                }
            }
            for (Map<?, ?> entry : kit.getMapList("items")) {
                Material material = Material.matchMaterial(String.valueOf(entry.get("material")));
                if (material == null) continue;
                int amount = number(entry.get("amount"), 1);
                int slot = number(entry.get("slot"), -1);
                ItemStack item = new ItemStack(material, Math.max(1, amount));
                if (slot >= 0 && slot < player.getInventory().getSize()) player.getInventory().setItem(slot, item);
                else player.getInventory().addItem(item);
            }

            List<?> savedArmorItems = kit.getList("armor");
            if (savedArmorItems != null && !savedArmorItems.isEmpty()) {
                ItemStack[] armor = new ItemStack[4];
                for (int i = 0; i < Math.min(4, savedArmorItems.size()); i++) {
                    if (savedArmorItems.get(i) instanceof ItemStack item) armor[i] = item;
                }
                player.getInventory().setArmorContents(armor);
            }
            if (kit.get("offhand") instanceof ItemStack offhandItem) {
                player.getInventory().setItemInOffHand(offhandItem);
            }

            List<PotionEffect> effects = new ArrayList<>();
            for (Map<?, ?> entry : kit.getMapList("effects")) {
                PotionEffectType type = PotionEffectType.getByName(String.valueOf(entry.get("type")));
                if (type == null) continue;
                PotionEffect effect = new PotionEffect(type,
                    Math.max(1, number(entry.get("duration-seconds"), 3600)) * 20,
                    Math.max(0, number(entry.get("amplifier"), 0)));
                player.addPotionEffect(effect);
                effects.add(effect);
            }
            appliedEffects.put(id, effects);
        }
        player.updateInventory();
    }

    private ConfigurationSection loadKit() {
        if (kitFile != null && kitFile.isFile()) {
            YamlConfiguration custom = YamlConfiguration.loadConfiguration(kitFile);
            if (custom.contains("items") || custom.contains("effects")) return custom;
        }
        return plugin.getConfig().getConfigurationSection("juggernaut.kit");
    }

    public void openKitEditor(Player player) {
        YamlConfiguration custom = kitFile == null ? new YamlConfiguration()
            : YamlConfiguration.loadConfiguration(kitFile);
        Inventory inventory = Bukkit.createInventory(new KitHolder(), 45,
            Component.text("Juggernaut kit editor"));

        List<?> saved = custom.getList("items");
        if (saved == null) saved = plugin.getConfig().getList("juggernaut.kit.items");
        if (saved != null) {
            for (int i = 0; i < Math.min(ARMOR_SLOT, saved.size()); i++) {
                if (saved.get(i) instanceof ItemStack item) inventory.setItem(i, item);
            }
        }
        List<?> savedArmorItems = custom.getList("armor");
        if (savedArmorItems != null) {
            for (int i = 0; i < Math.min(4, savedArmorItems.size()); i++) {
                if (savedArmorItems.get(i) instanceof ItemStack item) inventory.setItem(ARMOR_SLOT + i, item);
            }
        }
        if (custom.get("offhand") instanceof ItemStack offhandItem) {
            inventory.setItem(OFFHAND_SLOT, offhandItem);
        }
        ItemStack divider = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta divMeta = divider.getItemMeta();
        divMeta.displayName(Component.text(" ", NamedTextColor.GRAY));
        divider.setItemMeta(divMeta);
        inventory.setItem(41, divider);
        inventory.setItem(42, divider);
        inventory.setItem(43, divider);

        inventory.setItem(EXTRACT_BUTTON_SLOT, extractButtonIcon());
        player.openInventory(inventory);
    }

    /** Sets the kit directly from whatever the player is currently carrying — no GUI needed. */
    public boolean extractKitFromInventory(Player player) {
        YamlConfiguration custom = kitFile == null ? new YamlConfiguration()
            : YamlConfiguration.loadConfiguration(kitFile);
        custom.set("items", player.getInventory().getStorageContents());
        custom.set("armor", player.getInventory().getArmorContents());
        custom.set("offhand", player.getInventory().getItemInOffHand());
        if (!custom.contains("effects")) {
            custom.set("effects", plugin.getConfig().getList("juggernaut.kit.effects"));
        }
        try {
            if (kitFile != null) custom.save(kitFile);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save juggernaut kit: " + ex.getMessage());
            return false;
        }
    }

    /**
     * The kit editor's "pull from my inventory" button — copies the admin's live armor,
     * offhand, hotbar and main inventory into the editor's own slots so closing it (which
     * saves whatever the editor shows) captures everything in one click.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onKitEditorButtonClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitHolder)) return;
        if (event.getRawSlot() != EXTRACT_BUTTON_SLOT) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory editor = event.getInventory();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < Math.min(ARMOR_SLOT, contents.length); i++) {
            editor.setItem(i, contents[i]);
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < Math.min(4, armor.length); i++) {
            editor.setItem(ARMOR_SLOT + i, armor[i]);
        }
        editor.setItem(OFFHAND_SLOT, player.getInventory().getItemInOffHand());
        editor.setItem(EXTRACT_BUTTON_SLOT, extractButtonIcon());
        player.sendMessage("Pulled your armor, offhand, hotbar and inventory in — close this to save it.");
    }

    private ItemStack extractButtonIcon() {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Pull from my inventory", NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("Copies your armor, offhand,", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("hotbar and inventory in here.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onKitClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitHolder)
            || !(event.getPlayer() instanceof Player player)) return;
        YamlConfiguration custom = kitFile == null ? new YamlConfiguration()
            : YamlConfiguration.loadConfiguration(kitFile);
        ItemStack[] full = event.getInventory().getContents();
        custom.set("items", Arrays.copyOfRange(full, 0, ARMOR_SLOT));
        custom.set("armor", Arrays.copyOfRange(full, ARMOR_SLOT, ARMOR_SLOT + 4));
        custom.set("offhand", full.length > OFFHAND_SLOT ? full[OFFHAND_SLOT] : null);
        if (!custom.contains("effects")) {
            custom.set("effects", plugin.getConfig().getList("juggernaut.kit.effects"));
        }
        try {
            if (kitFile != null) custom.save(kitFile);
            player.sendMessage("Juggernaut kit saved.");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save juggernaut kit: " + ex.getMessage());
            player.sendMessage("Could not save Juggernaut kit.");
        }
    }

    private static final class KitHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private void cleanup() {
        for (UUID id : new HashSet<>(savedContents.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            List<PotionEffect> effects = appliedEffects.get(id);
            if (effects != null) for (PotionEffect effect : effects) player.removePotionEffect(effect.getType());
            player.getInventory().setContents(savedContents.get(id));
            player.getInventory().setArmorContents(savedArmor.get(id));
            ItemStack offhand = savedOffhand.get(id);
            if (offhand != null) player.getInventory().setItemInOffHand(offhand);
            player.updateInventory();
        }
        participants.clear();
        alive.clear();
        savedContents.clear();
        savedArmor.clear();
        savedOffhand.clear();
        appliedEffects.clear();
        juggernaut = null;
        running = false;
    }

    private int number(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return fallback; }
    }

    private void broadcast(String key, String fallback) { broadcast(key, fallback, null); }

    private void broadcast(String key, String fallback, String player) {
        String message = plugin.getConfig().getString("juggernaut." + key, fallback);
        if (message == null || message.isBlank()) return;
        if (player != null) message = message.replace("<player>", player);
        MessageUtil.broadcastRaw(message);
    }
}
