package dev.notskrib.frostffacore.kits;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import dev.notskrib.frostffacore.commands.KitLayoutEditor;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

/** Native kit storage and application; no external kit plugin is required. */
public final class KitManager implements CommandExecutor, TabCompleter {
    private final FrostFFACore plugin;
    private final File file;
    private KitLayoutEditor editor;
    private KitSelectionGui selectionGui;

    public KitManager(FrostFFACore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
    }

    public void setEditor(KitLayoutEditor editor) {
        this.editor = editor;
    }

    public void setSelectionGui(KitSelectionGui selectionGui) {
        this.selectionGui = selectionGui;
    }

    public boolean giveHighest(Player player) {
        List<String> kits = plugin.getConfig().getStringList("kit-npc.kits");
        if (kits.stream().noneMatch(kit -> kit != null && kit.trim().equalsIgnoreCase("default"))) {
            kits = new java.util.ArrayList<>(kits);
            kits.add("default");
        }
        for (int i = kits.size() - 1; i >= 0; i--) {
            String kit = kits.get(i) == null ? "" : kits.get(i).trim().toLowerCase(Locale.ROOT);
            if (!kit.isBlank()
                && player.hasPermission(permissionNode(kit))
                && apply(player, kit)) return true;
        }
        return false;
    }

    public boolean apply(Player player, String kit) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> saved = config.getList("kits." + kit.toLowerCase(Locale.ROOT));
        if (saved == null || saved.stream().noneMatch(item -> item instanceof ItemStack)) return false;
        ItemStack[] contents = new ItemStack[36];
        for (int i = 0; i < Math.min(contents.length, saved.size()); i++) {
            if (saved.get(i) instanceof ItemStack item) contents[i] = item.clone();
        }
        File layoutsFile = new File(plugin.getDataFolder(), "kit-layouts.yml");
        YamlConfiguration layouts = YamlConfiguration.loadConfiguration(layoutsFile);
        List<?> personal = layouts.getList("layouts." + player.getUniqueId() + "." + kit.toLowerCase(Locale.ROOT));
        if (personal != null) {
            Arrays.fill(contents, null);
            for (int i = 0; i < Math.min(36, personal.size()); i++) {
                if (personal.get(i) instanceof ItemStack item) contents[i] = item.clone();
            }
        }
        player.getInventory().clear();
        player.getInventory().setContents(Arrays.copyOf(contents, 36));
        List<?> equipment = personal != null ? personal : saved;
        if (equipment.size() >= 40) {
            ItemStack[] armor = new ItemStack[4];
            for (int i = 0; i < armor.length; i++) {
                if (equipment.get(36 + i) instanceof ItemStack item) armor[i] = item.clone();
            }
            player.getInventory().setArmorContents(armor);
            if (equipment.size() > 40 && equipment.get(40) instanceof ItemStack item) {
                player.getInventory().setItemInOffHand(item.clone());
            }
        }
        return true;
    }

    public void saveKit(String kit, ItemStack[] contents) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("kits." + kit.toLowerCase(Locale.ROOT), Arrays.asList(contents));
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save kit " + kit + ": " + e.getMessage());
        }

    }

    public boolean exists(String kit) {
        if (kit == null || kit.isBlank()) return false;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> items = config.getList("kits." + kit.toLowerCase(Locale.ROOT));
        return items != null && items.stream().anyMatch(item -> item instanceof ItemStack);
    }

    public List<String> kitNames() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.getConfigurationSection("kits") == null) return List.of();
        return new java.util.ArrayList<>(config.getConfigurationSection("kits").getKeys(false));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.reply(sender, "Players only.");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("edit")) {
            if (!player.hasPermission("frostffa.kits.admin")) {
                MessageUtil.reply(player, "You do not have permission to edit kits.");
                return true;
            }
            if (editor == null) {
                MessageUtil.reply(player, "The kit editor is not available.");
                return true;
            }
            if (!exists(args[1])) {
                MessageUtil.reply(player, "That kit does not exist. Available kits: "
                    + String.join(", ", kitNames()));
                return true;
            }
            editor.openAdminEditor(player, args[1]);
            return true;
        }
        if (args.length == 0) {
            if (selectionGui != null) selectionGui.open(player);
            else if (!giveHighest(player)) MessageUtil.reply(player, "You do not have an available kit.");
            return true;
        }
        String kit = args[0].toLowerCase(Locale.ROOT);
        if (!exists(kit)) {
            MessageUtil.reply(player, "That kit does not exist. Available kits: "
                + String.join(", ", kitNames()));
            return true;
        }
        if (!player.hasPermission(permissionNode(kit))) {
            MessageUtil.reply(player, "You do not have permission for that kit.");
            return true;
        }
        if (!apply(player, kit)) MessageUtil.reply(player, "That kit has not been configured yet.");
        return true;
    }

    private String permissionNode(String kit) {
        return "frostffa.kit." + kit;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> values = new java.util.ArrayList<>();
            values.add("edit");
            values.addAll(kitNames());
            return values.stream().filter(value -> value.toLowerCase(Locale.ROOT)
                .startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edit")
                && sender.hasPermission("frostffa.kits.admin")) {
            return kitNames().stream().filter(value -> value.toLowerCase(Locale.ROOT)
                .startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
