package dev.notskrib.frostffacore.commands;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.io.File;
import java.io.IOException;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A 54-slot personal storage persisted to {@code ender-chests.yml}; vanilla ender chests are
 * capped at 27 slots. It has no target-player variant, so nobody else's storage can be viewed.
 */
public final class EnderChestCommand implements CommandExecutor, Listener {

    private static final int SIZE = 54;

    private final FrostFFACore plugin;
    private final File file;

    public EnderChestCommand(FrostFFACore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ender-chests.yml");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.reply(sender, Palette.l(Palette.INK, "players only."));
            return true;
        }
        if (!player.hasPermission("frostffa.enderchest.use")) {
            MessageUtil.sendError(player, "You don't have permission.");
            return true;
        }

        Inventory inventory = Bukkit.createInventory(new EnderChestHolder(), SIZE,
            Component.text("Ender Chest"));
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.file);
        java.util.List<?> saved = cfg.getList("chests." + player.getUniqueId());
        if (saved != null) {
            ItemStack[] contents = new ItemStack[SIZE];
            for (int i = 0; i < Math.min(SIZE, saved.size()); i++) {
                if (saved.get(i) instanceof ItemStack item) contents[i] = item;
            }
            inventory.setContents(contents);
        }
        player.openInventory(inventory);
        return true;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof EnderChestHolder)
            || !(event.getPlayer() instanceof Player player)) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.file);
        cfg.set("chests." + player.getUniqueId(), event.getInventory().getContents());
        try {
            cfg.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().warning("Could not save ender chest: " + e.getMessage());
        }
    }

    private static final class EnderChestHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
