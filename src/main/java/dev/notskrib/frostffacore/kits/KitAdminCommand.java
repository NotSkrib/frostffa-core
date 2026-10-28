package dev.notskrib.frostffacore.kits;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

/** Small setup command for registering kit names and their NPC priority. */
public final class KitAdminCommand implements CommandExecutor, TabCompleter {
    private final FrostFFACore plugin;
    private final File kitsFile;

    public KitAdminCommand(FrostFFACore plugin) {
        this.plugin = plugin;
        this.kitsFile = new File(plugin.getDataFolder(), "kits.yml");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("frostffa.kits.admin")) {
            MessageUtil.reply(sender, "You do not have permission to manage kits.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            MessageUtil.reply(sender, "/kitadmin create <name> [role]");
            MessageUtil.reply(sender, "/kitadmin list");
            MessageUtil.reply(sender, "Then put the items in your inventory and use /kitlayout <name>.");
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            MessageUtil.reply(sender, "Registered kits: "
                + String.join(", ", plugin.getConfig().getStringList("kit-npc.kits")));
            return true;
        }
        if (!args[0].equalsIgnoreCase("create") || args.length < 2) {
            MessageUtil.reply(sender, "Usage: /kitadmin create <name> [role]");
            return true;
        }

        String kit = args[1].trim().toLowerCase(Locale.ROOT);
        if (!kit.matches("[a-z0-9_-]+")) {
            MessageUtil.reply(sender, "Kit names may only contain letters, numbers, _ and -.");
            return true;
        }
        YamlConfiguration kits = YamlConfiguration.loadConfiguration(kitsFile);
        if (kits.getList("kits." + kit) == null) {
            kits.set("kits." + kit, new ArrayList<>(java.util.Collections.nCopies(41, null)));
            try {
                kits.save(kitsFile);
            } catch (IOException e) {
                MessageUtil.reply(sender, "Could not save kits.yml: " + e.getMessage());
                return true;
            }

        }

        List<String> order = new ArrayList<>(plugin.getConfig().getStringList("kit-npc.kits"));
        order.removeIf(existing -> existing.equalsIgnoreCase(kit));
        order.add(kit);
        plugin.getConfig().set("kit-npc.kits", order);
        plugin.saveConfig();

        String role = args.length >= 3 ? args[2] : kit;
        MessageUtil.reply(sender, "Created kit '" + kit + "'.");
        MessageUtil.reply(sender, "Permission: frostffa.kit." + kit);
        MessageUtil.reply(sender, "LuckPerms: /lp group " + role
            + " permission set frostffa.kit." + kit + " true");
        MessageUtil.reply(sender, "Now configure it with: /kitlayout " + kit);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "list", "help").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return List.of("default", "media", "vip", "mvp").stream()
                .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
