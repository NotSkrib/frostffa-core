package dev.notskrib.frostffacore.leaderboard;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private static final double REMOVE_RADIUS = 6.0;
    private static final Pattern HEX = Pattern.compile("(?i)^#?[0-9a-f]{6}$");
    private static final List<String> COLOR_PARTS = List.of("background", "header", "subtitle", "position", "name", "value");

    private final FrostFFACore plugin;

    public LeaderboardCommand(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.reply(sender, Palette.l(Palette.INK, "players only — a board goes where you stand."));
            return true;
        }
        LeaderboardManager manager = this.plugin.getLeaderboardManager();

        if (args.length == 0) {
            this.usage(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "cycle" -> {
                Location where = player.getLocation().clone();
                where.add(0.0, manager.yOffset(), 0.0);
                where.setYaw(player.getLocation().getYaw() + manager.yawOffset());
                where.setPitch(0f);

                manager.placeCycle(where);
                MessageUtil.sendSuccess(player, Palette.sc("Placed a cycling leaderboard here."));
                MessageUtil.send(player, Palette.l(Palette.DIM, "remove it with ")
                    + Palette.c(Palette.GLACIER, "/leaderboard remove"));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
            }

            case "remove", "delete" -> {
                Leaderboard nearest = manager.nearest(player.getLocation(), REMOVE_RADIUS);
                if (nearest == null) {
                    MessageUtil.sendError(player, Palette.sc("No leaderboard within "
                        + (int) REMOVE_RADIUS + " blocks."));
                    return true;
                }
                manager.remove(nearest);
                MessageUtil.sendSuccess(player, Palette.sc("Removed the " + nearest.type().basis() + " board."));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_FRAME_BREAK, 0.7f, 1.0f);
            }

            case "color", "colour" -> {
                if (!player.hasPermission("frostffa.leaderboard.admin")) {
                    MessageUtil.sendError(player, Palette.sc("You do not have permission to change leaderboard colors."));
                    return true;
                }
                this.color(player, manager, args);
            }

            case "list" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(MessageUtil.parse(Palette.b(Palette.BRAND, "leaderboards")));
                if (manager.boards().isEmpty()) {
                    player.sendMessage(MessageUtil.parse("  " + Palette.l(Palette.DIM, "none placed yet")));
                }
                for (Leaderboard board : manager.boards()) {
                    Location loc = board.location();
                    player.sendMessage(MessageUtil.parse("  "
                        + Palette.l(Palette.GLACIER, board.type().basis())
                        + "  " + Palette.c(Palette.DIM, (loc.getWorld() != null ? loc.getWorld().getName() : "?")
                            + " " + (int) loc.getX() + ", " + (int) loc.getY() + ", " + (int) loc.getZ())));
                }
                player.sendMessage(Component.empty());
            }

            default -> {
                LeaderboardType type = LeaderboardType.from(args[0]);
                if (type == null) {
                    MessageUtil.sendError(player, Palette.sc("Unknown leaderboard '" + args[0] + "'."));
                    this.usage(player);
                    return true;
                }
                Location where = player.getLocation().clone();
                where.add(0.0, manager.yOffset(), 0.0);
                where.setYaw(player.getLocation().getYaw() + manager.yawOffset());
                where.setPitch(0f);

                manager.place(type, where);
                MessageUtil.sendSuccess(player, Palette.sc("Placed the " + type.basis() + " board here."));
                MessageUtil.send(player, Palette.l(Palette.DIM, "remove it with ")
                    + Palette.c(Palette.GLACIER, "/leaderboard remove"));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
            }
        }
        return true;
    }

    private void usage(Player player) {
        MessageUtil.send(player, Palette.l(Palette.INK, "usage: ")
            + Palette.c(Palette.GLACIER, "/leaderboard <balance|kills|killstreak|bounty|cycle>"));
        MessageUtil.send(player, Palette.l(Palette.DIM, "also: ")
            + Palette.c(Palette.GLACIER, "/leaderboard remove")
            + Palette.l(Palette.DIM, ", ")
            + Palette.c(Palette.GLACIER, "/leaderboard list")
            + Palette.l(Palette.DIM, " and ")
            + Palette.c(Palette.GLACIER, "/leaderboard color"));
    }

    private void color(Player player, LeaderboardManager manager, String[] args) {
        if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
            ConfigurationSection presets = this.plugin.getConfig().getConfigurationSection("leaderboards.color-presets");
            String names = presets == null ? "" : String.join(", ", presets.getKeys(false));
            MessageUtil.send(player, Palette.l(Palette.INK, "presets: ")
                + Palette.c(Palette.GLACIER, names.isBlank() ? "none configured" : names));
            return;
        }
        if (args.length == 2) {
            String preset = args[1].toLowerCase(Locale.ROOT);
            ConfigurationSection section = this.plugin.getConfig()
                .getConfigurationSection("leaderboards.color-presets." + preset);
            if (section == null) {
                MessageUtil.sendError(player, Palette.sc("Unknown preset '" + args[1] + "'. Use /leaderboard color list."));
                return;
            }
            for (String part : COLOR_PARTS) {
                String hex = section.getString(part);
                if (hex != null) this.applyColor(part, hex);
            }
            this.plugin.saveConfig();
            manager.reload();
            MessageUtil.sendSuccess(player, Palette.sc("Applied the '" + preset + "' leaderboard color preset."));
            return;
        }
        if (args.length == 3) {
            String part = args[1].toLowerCase(Locale.ROOT);
            String hex = args[2];
            if (!COLOR_PARTS.contains(part)) {
                MessageUtil.sendError(player, Palette.sc("Unknown part '" + args[1] + "'. Use: " + String.join(", ", COLOR_PARTS)));
                return;
            }
            if (!HEX.matcher(hex).matches()) {
                MessageUtil.sendError(player, Palette.sc("'" + hex + "' is not a valid hex color (use RRGGBB or #RRGGBB)."));
                return;
            }
            this.applyColor(part, hex.startsWith("#") ? hex : "#" + hex);
            this.plugin.saveConfig();
            manager.reload();
            MessageUtil.sendSuccess(player, Palette.sc("Set leaderboard " + part + " color to " + hex + "."));
            return;
        }
        MessageUtil.send(player, Palette.l(Palette.INK, "usage: ")
            + Palette.c(Palette.GLACIER, "/leaderboard color <preset>")
            + Palette.l(Palette.DIM, " or ")
            + Palette.c(Palette.GLACIER, "/leaderboard color <part> <hex>")
            + Palette.l(Palette.DIM, " — parts: " + String.join(", ", COLOR_PARTS)));
    }

    private void applyColor(String part, String hex) {
        String key = part.equals("background") ? "leaderboards.background-color" : "leaderboards.animation-colors." + part;
        this.plugin.getConfig().set(key, hex);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            for (LeaderboardType type : LeaderboardType.values()) options.add(type.id());
            options.add("cycle");
            options.add("remove");
            options.add("list");
            options.add("color");
            options.removeIf(option -> !option.startsWith(args[0].toLowerCase(Locale.ROOT)));
            return options;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("color") || args[0].equalsIgnoreCase("colour"))) {
            List<String> options = new ArrayList<>(COLOR_PARTS);
            options.add("list");
            ConfigurationSection presets = this.plugin.getConfig().getConfigurationSection("leaderboards.color-presets");
            if (presets != null) options.addAll(presets.getKeys(false));
            options.removeIf(option -> !option.startsWith(args[1].toLowerCase(Locale.ROOT)));
            return options;
        }
        return List.of();
    }
}
