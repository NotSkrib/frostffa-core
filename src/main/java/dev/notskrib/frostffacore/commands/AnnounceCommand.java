package dev.notskrib.frostffacore.commands;

import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Server-wide announcement: a full-screen title (for impact) plus a boxed chat message
 * (so it's still readable in scrollback afterward) and a chime. Deliberately louder than
 * every other broadcast in this plugin — reserve for things actually worth interrupting
 * the screen for.
 */
public final class AnnounceCommand implements CommandExecutor {

    private static final String DIVIDER =
        "<gradient:" + Palette.BRAND + ":" + Palette.SNOW + ":" + Palette.BRAND + ">"
            + "❅ ❆ ❅ ❆ ❅ ❆ ❅ ❆ ❅ ❆ ❅ ❆ ❅ ❆ ❅ ❆ ❅"
            + "</gradient>";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            MessageUtil.replyError(sender, "usage: /announce <message>");
            return true;
        }
        String message = String.join(" ", args);

        String senderName = sender.getName();

        MessageUtil.broadcastTitle(
            "<gradient:" + Palette.BRAND + ":" + Palette.SNOW + ":" + Palette.BRAND + ">"
                + "<bold>❄ ANNOUNCEMENT ❄</bold></gradient>",
            "<color:" + Palette.GLACIER + ">" + escape(message) + "</color>");

        MessageUtil.broadcastRaw(DIVIDER);
        MessageUtil.broadcastRaw("<color:" + Palette.SNOW + "><bold>" + Palette.sc("announcement")
            + "</bold></color>");
        MessageUtil.broadcastRaw("<color:" + Palette.INK + ">" + escape(message) + "</color>");
        MessageUtil.broadcastRaw("<color:" + Palette.GLACIER + "><italic>— " + escape(senderName)
            + "</italic></color>");
        MessageUtil.broadcastRaw(DIVIDER);

        MessageUtil.broadcastSound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
        return true;
    }

    /**
     * Admin-supplied text still goes through MiniMessage (so the surrounding gradient/color
     * tags render), but a literal '<' from ordinary typed punctuation would otherwise be
     * parsed as a tag start and can unbalance the frame around it — escape it so stray
     * punctuation can't break the announcement's styling.
     */
    private static String escape(String raw) {
        return raw.replace("<", "\\<");
    }
}
