package dev.notskrib.frostffacore.commands;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

/** /frostffa reload | mapclear | info. */
public class FrostFFACommand implements CommandExecutor, TabCompleter {

    private final FrostFFACore plugin;

    public FrostFFACommand(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            this.usage(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> this.reload(sender);
            case "mapclear" -> this.mapclear(sender);
            case "info" -> this.info(sender);
            case "clearlag" -> this.clearlag(sender, args);
            default -> this.usage(sender);
        }
        return true;
    }

    private void usage(CommandSender sender) {
        MessageUtil.reply(sender, Palette.l(Palette.INK, "usage: ")
            + Palette.c(Palette.GLACIER, "/frostffa <reload|mapclear|info|clearlag <on|off|now>>"));
    }

    private void clearlag(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.reply(sender, Palette.l(Palette.INK, "usage: ")
                + Palette.c(Palette.GLACIER, "/frostffa clearlag <on|off|now>"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                this.plugin.getClearLagManager().setEnabled(true);
                MessageUtil.replySuccess(sender, "Clear lag is now ON.");
            }
            case "off" -> {
                this.plugin.getClearLagManager().setEnabled(false);
                MessageUtil.replySuccess(sender, "Clear lag is now OFF.");
            }
            case "now" -> {
                int removed = this.plugin.getClearLagManager().sweep(false);
                MessageUtil.replySuccess(sender, "Cleared " + removed + " entities.");
            }
            default -> MessageUtil.reply(sender, Palette.l(Palette.INK, "usage: ")
                + Palette.c(Palette.GLACIER, "/frostffa clearlag <on|off|now>"));
        }
    }

    private void reload(CommandSender sender) {
        long startedAt = System.currentTimeMillis();

        // Config first — every manager below reads from this.plugin.getConfig() fresh.
        this.plugin.getConfigManager().reloadAll();

        this.plugin.getMaintenanceManager().reload();
        this.plugin.getClearLagManager().reload();
        this.plugin.getSettingsManager().reload();
        this.plugin.getEconomyManager().reload();
        this.plugin.getCombatManager().reload();
        this.plugin.getAfkManager().reload();
        this.plugin.getKillMessageManager().reload();
        this.plugin.getRulesManager().reload();
        this.plugin.getBlockLedger().reload();
        this.plugin.getBlockPlaceListener().reload();
        this.plugin.getDailyManager().reload();
        this.plugin.getBountyManager().reload();
        this.plugin.getRankManager().reload();
        // Leaderboard last — it reads Economy/Kills/Killstreak/Bounty snapshots and also
        // restarts LeaderboardAnimator with whatever animation keys changed.
        this.plugin.getLeaderboardManager().reload();
        this.plugin.getKillRewardManager().reload();

        long tookMs = System.currentTimeMillis() - startedAt;
        MessageUtil.replySuccess(sender, "FrostFFACore reloaded in " + tookMs + "ms.");
    }

    private void mapclear(CommandSender sender) {
        this.plugin.getBlockLedger().clearMap();
        MessageUtil.replySuccess(sender, "Clearing the map now.");
    }

    private void info(CommandSender sender) {
        sender.sendMessage(Component.empty());
        MessageUtil.reply(sender, Palette.b(Palette.BRAND, "frostffacore ")
            + Palette.c(Palette.INK, "v" + this.plugin.getPluginMeta().getVersion()));
        MessageUtil.reply(sender, Palette.l(Palette.INK, "leaderboards placed ")
            + Palette.c(Palette.GLACIER, String.valueOf(this.plugin.getLeaderboardManager().boards().size())));
        MessageUtil.reply(sender, Palette.l(Palette.INK, "players online ")
            + Palette.c(Palette.GLACIER, String.valueOf(this.plugin.getServer().getOnlinePlayers().size())));

        sender.sendMessage(Component.empty());
        MessageUtil.reply(sender, Palette.b(Palette.BRAND, "modules ")
            + Palette.c(Palette.INK, "(" + this.plugin.getManagers().size() + ")"));
        StringBuilder modules = new StringBuilder();
        for (var manager : this.plugin.getManagers()) {
            if (modules.length() > 0) modules.append(Palette.l(Palette.DIM, ", "));
            modules.append(Palette.c(Palette.GLACIER, manager.getClass().getSimpleName()));
        }
        MessageUtil.reply(sender, modules.toString());

        sender.sendMessage(Component.empty());
        Map<String, Map<String, Object>> commands = this.plugin.getDescription().getCommands();
        List<String> names = new ArrayList<>(commands.keySet());
        Collections.sort(names);
        MessageUtil.reply(sender, Palette.b(Palette.BRAND, "commands ")
            + Palette.c(Palette.INK, "(" + names.size() + ")"));
        StringBuilder cmds = new StringBuilder();
        for (String name : names) {
            if (cmds.length() > 0) cmds.append(Palette.l(Palette.DIM, ", "));
            cmds.append(Palette.c(Palette.GLACIER, "/" + name));
        }
        MessageUtil.reply(sender, cmds.toString());
        sender.sendMessage(Component.empty());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("reload", "mapclear", "info", "clearlag");
        if (args.length == 2 && args[0].equalsIgnoreCase("clearlag")) return List.of("on", "off", "now");
        return List.of();
    }
}
