package dev.notskrib.frostffacore.chatfilter;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.util.Collections;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

public class ChatFilterCommand implements CommandExecutor, TabCompleter {

    private final FrostFFACore plugin;

    public ChatFilterCommand(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("frostffa.chatfilter.admin")) {
            MessageUtil.replyError(sender, "You don't have permission.");
            return true;
        }

        if (args.length < 1) {
            MessageUtil.reply(sender, Palette.l(Palette.BRAND, "ChatFilter commands:") + "\n"
                + Palette.l(Palette.INK, "/chatfilter reload") + " - reload chat filter config\n"
                + Palette.l(Palette.INK, "/chatfilter test <text>") + " - test text through normalization engine");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            this.plugin.getChatFilterManager().reload();
            MessageUtil.replySuccess(sender, "Chat filter configuration reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("test")) {
            if (args.length < 2) {
                MessageUtil.replyError(sender, "Usage: /chatfilter test <text>");
                return true;
            }
            String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            ChatFilterManager.FilterResult result = this.plugin.getChatFilterManager().check(text);
            String norm = this.plugin.getChatFilterManager().normalize(text);

            MessageUtil.reply(sender, Palette.b(Palette.BRAND, "ChatFilter Test Result:") + "\n"
                + Palette.l(Palette.INK, "Raw: ") + text + "\n"
                + Palette.l(Palette.INK, "Normalized: ") + norm + "\n"
                + Palette.l(Palette.INK, "Status: ") + (result.blocked()
                    ? Palette.l(Palette.BAD, "BLOCKED") + " (Matched: " + result.matchedPattern() + ")"
                    : Palette.l(Palette.GLACIER, "ALLOWED")));
            return true;
        }

        MessageUtil.replyError(sender, "Unknown subcommand. Usage: /chatfilter <reload|test <text>>");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("frostffa.chatfilter.admin")) {
            return List.of("reload", "test").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return Collections.emptyList();
    }
}
