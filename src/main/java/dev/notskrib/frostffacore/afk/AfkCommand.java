package dev.notskrib.frostffacore.afk;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /afk [player] (plan §6.7). Alias {@code away}. */
public class AfkCommand implements CommandExecutor, TabCompleter {

    private final FrostFFACore plugin;

    public AfkCommand(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.reply(sender, Palette.l(Palette.INK, "players only."));
            return true;
        }

        if (args.length >= 1) {
            return this.toggleOther(player, args[0]);
        }

        AfkManager.ToggleResult result = this.plugin.getAfkManager().toggleSelf(player);
        switch (result) {
            case NOW_AFK -> MessageUtil.send(player, Palette.l(Palette.INK, "you are now ")
                + Palette.l(Palette.DIM, "afk") + Palette.l(Palette.INK, "."));
            case NOW_BACK -> MessageUtil.send(player, Palette.l(Palette.INK, "welcome ")
                + Palette.c(Palette.BRAND, "back") + Palette.l(Palette.INK, "."));
            case REFUSED_COMBAT -> MessageUtil.sendError(player,
                Palette.sc("You can't go AFK while you're in combat."));
            case DISABLED -> MessageUtil.sendError(player, Palette.sc("AFK is disabled on this server."));
        }
        return true;
    }

    private boolean toggleOther(Player staff, String targetName) {
        if (!staff.hasPermission("frostffa.afk.others")) {
            MessageUtil.sendError(staff, Palette.sc("You don't have permission."));
            return true;
        }
        Player target = this.plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            MessageUtil.sendError(staff, Palette.sc("That player is not online."));
            return true;
        }
        boolean nowAfk = this.plugin.getAfkManager().setAfk(target, !this.plugin.getAfkManager().isAfk(target));
        MessageUtil.send(staff, Palette.c(Palette.GLACIER, target.getName()) + Palette.l(Palette.INK,
            nowAfk ? " is now AFK." : " is no longer AFK."));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)
            || !player.hasPermission("frostffa.afk.others")) {
            return List.of();
        }
        List<String> names = new java.util.ArrayList<>();
        for (Player online : this.plugin.getServer().getOnlinePlayers()) names.add(online.getName());
        names.removeIf(name -> !name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)));
        return names;
    }
}
