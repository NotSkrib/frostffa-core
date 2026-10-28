package dev.notskrib.frostffacore.rules;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RulesCommand implements CommandExecutor, TabCompleter {

    private final FrostFFACore plugin;

    public RulesCommand(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        RulesManager rules = this.plugin.getRulesManager();

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("frostffa.admin")) {
                MessageUtil.replyError(sender, Palette.sc("You don't have permission."));
                return true;
            }
            rules.reload();
            MessageUtil.replySuccess(sender, Palette.sc("Reloaded rules.yml — ")
                + rules.size() + Palette.sc(" rule(s)."));
            return true;
        }

        if (rules.size() == 0) {
            MessageUtil.replyError(sender, Palette.sc("No rules are set up yet."));
            return true;
        }

        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                MessageUtil.replyError(sender, Palette.sc("That isn't a page number."));
                return true;
            }
        }
        int pages = rules.pages();
        int clamped = Math.max(1, Math.min(page, pages));

        MessageUtil.reply(sender, "");
        MessageUtil.reply(sender, rules.header()
            .replace("{page}", String.valueOf(clamped))
            .replace("{pages}", String.valueOf(pages)));
        MessageUtil.reply(sender, "");

        for (String line : rules.page(clamped)) {
            MessageUtil.reply(sender, line);
        }

        MessageUtil.reply(sender, "");
        MessageUtil.reply(sender, clamped < pages
            ? rules.footer().replace("{next}", String.valueOf(clamped + 1))
            : rules.footerLast());
        MessageUtil.reply(sender, "");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= this.plugin.getRulesManager().pages(); i++) out.add(String.valueOf(i));
        if (sender.hasPermission("frostffa.admin")) out.add("reload");
        return out;
    }
}
