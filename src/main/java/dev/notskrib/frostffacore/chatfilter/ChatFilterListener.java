package dev.notskrib.frostffacore.chatfilter;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatFilterListener implements Listener {

    private final FrostFFACore plugin;

    public ChatFilterListener(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("frostffa.chatfilter.bypass")) {
            return;
        }

        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        ChatFilterManager.FilterResult result = this.plugin.getChatFilterManager().check(rawMessage);

        if (result.blocked()) {
            if (this.plugin.getChatFilterManager().isBlockMessage()) {
                event.setCancelled(true);
            }

            String warnMsg = this.plugin.getChatFilterManager().getNotifyPlayerMessage();
            if (warnMsg != null && !warnMsg.isBlank()) {
                MessageUtil.sendRaw(player, warnMsg);
            }

            if (this.plugin.getChatFilterManager().isStaffAlerts()) {
                String alertFormat = this.plugin.getChatFilterManager().getStaffAlertFormat();
                if (alertFormat != null && !alertFormat.isBlank()) {
                    String alert = alertFormat
                        .replace("<player>", player.getName())
                        .replace("<message>", rawMessage)
                        .replace("<rule>", result.matchedPattern() != null ? result.matchedPattern() : "blacklist");
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.isOp() || online.hasPermission("frostffa.admin")
                                || online.hasPermission("frostffa.staff") || online.hasPermission("frostffa.chatfilter.notify")) {
                            MessageUtil.sendRaw(online, alert);
                        }
                    }
                }
            }

            this.plugin.getChatFilterManager().logBlocked(player, result);
        }
    }
}
