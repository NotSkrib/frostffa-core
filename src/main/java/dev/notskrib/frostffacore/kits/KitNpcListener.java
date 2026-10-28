package dev.notskrib.frostffacore.kits;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import dev.notskrib.frostffacore.FrostFFACore;

/**
 * Bukkit-only FancyNpcs fallback. FancyNpcs is not a compile dependency, so this matches the
 * configured id against the NPC entity's custom name, scoreboard tag or string PDC entry.
 */
public final class KitNpcListener implements Listener {
    private final FrostFFACore plugin;

    public KitNpcListener(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onNpcRightClick(PlayerInteractEntityEvent event) {
        if (!plugin.getConfig().getBoolean("kit-npc.enabled", true)
                || !isKitNpc(event.getRightClicked())) {
            return;
        }

        Player player = event.getPlayer();
        String permissionPrefix = plugin.getConfig().getString(
                "kit-npc.permission-prefix", "frostffa.kit.");
        List<String> kits = plugin.getConfig().getStringList("kit-npc.kits");
        // Configuration is intentionally lowest rank to highest rank, so the last
        // permission present is the player's highest available kit.
        for (int i = kits.size() - 1; i >= 0; i--) {
            String kit = kits.get(i);
            if (kit == null || kit.isBlank()) {
                continue;
            }
            kit = kit.trim();
            if (player.hasPermission(permissionPrefix + kit)) {
                plugin.getServer().dispatchCommand(player, "kit " + kit);
                return;
            }
        }

        String noKitMessage = plugin.getConfig().getString("kit-npc.no-kit-message", "");
        if (noKitMessage != null && !noKitMessage.isBlank()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noKitMessage));
        }
    }

    private boolean isKitNpc(Entity entity) {
        String configuredId = plugin.getConfig().getString("kit-npc.id", "kit-npc");
        if (configuredId == null || configuredId.isBlank()) {
            configuredId = "kit-npc";
        }
        String id = configuredId.trim().toLowerCase(Locale.ROOT);

        String customName = entity.getCustomName();
        if (customName != null && normalize(customName).equals(id)) {
            return true;
        }
        Set<String> tags = entity.getScoreboardTags().stream()
                .map(this::normalize)
                .collect(Collectors.toSet());
        if (tags.contains(id)) {
            return true;
        }
        for (NamespacedKey key : entity.getPersistentDataContainer().getKeys()) {
            String value = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (normalize(key.getKey()).equals(id) || (value != null && normalize(value).equals(id))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return ChatColor.stripColor(value).trim().toLowerCase(Locale.ROOT);
    }
}
