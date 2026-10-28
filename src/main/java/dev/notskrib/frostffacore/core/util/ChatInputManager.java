package dev.notskrib.frostffacore.core.util;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatInputManager implements Listener {

    private record Pending(Consumer<String> callback, Runnable cancelled, long expiresAt) {}

    private final FrostFFACore plugin;
    private final Map<UUID, Pending> waiting = new ConcurrentHashMap<>();

    public ChatInputManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    public void await(Player player, String prompt, Consumer<String> callback, Runnable cancelled) {
        player.closeInventory();
        this.waiting.put(player.getUniqueId(),
            new Pending(callback, cancelled, System.currentTimeMillis() + 60_000L));
        MessageUtil.send(player, prompt);
        MessageUtil.send(player, "<color:" + Palette.INK + ">ᴛʏᴘᴇ <color:" + Palette.BAD + ">cancel</color> to abort. Expires in 60s.</color>");
    }

    public void forget(UUID uuid) {
        this.waiting.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending pending = this.waiting.remove(player.getUniqueId());
        if (pending == null) return;

        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (System.currentTimeMillis() > pending.expiresAt() || text.equalsIgnoreCase("cancel")) {
                MessageUtil.send(player, Palette.l(Palette.BAD, "Cancelled."));
                if (pending.cancelled() != null) pending.cancelled().run();
                return;
            }
            pending.callback().accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.waiting.remove(event.getPlayer().getUniqueId());
    }
}
