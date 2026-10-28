package dev.notskrib.frostffacore.core;

import dev.notskrib.frostffacore.FrostFFACore;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Plays a configured sound when a player right-clicks a recognized NPC (matched the same
 * way {@code kit-npc} is — custom name, scoreboard tag, or a PDC string value — so it works
 * regardless of which plugin actually dispatches that NPC's command). Never cancels the
 * event; this is purely a feedback layer on top of whatever handles the click already.
 */
public final class NpcSoundListener implements Listener {

    private final FrostFFACore plugin;

    public NpcSoundListener(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcRightClick(PlayerInteractEntityEvent event) {
        ConfigurationSection section = this.plugin.getConfig().getConfigurationSection("npc-sounds");
        if (section == null) return;
        Player player = event.getPlayer();
        Set<String> ids = idsOf(event.getRightClicked());
        ids.add(event.getRightClicked().getType().name().toLowerCase(Locale.ROOT));
        for (String key : section.getKeys(false)) {
            if (!ids.contains(key.toLowerCase(Locale.ROOT))) continue;
            String soundName = section.getString(key);
            Sound sound = soundFrom(soundName);
            if (sound != null) player.playSound(player.getLocation(), sound, 1f, 1f);
            return;
        }
    }

    private static Sound soundFrom(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Set<String> idsOf(Entity entity) {
        Set<String> ids = entity.getScoreboardTags().stream()
            .map(NpcSoundListener::normalize)
            .collect(Collectors.toSet());
        String customName = entity.getCustomName();
        if (customName != null) ids.add(normalize(customName));
        for (NamespacedKey key : entity.getPersistentDataContainer().getKeys()) {
            String value = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            ids.add(normalize(key.getKey()));
            if (value != null) ids.add(normalize(value));
        }
        return ids;
    }

    private static String normalize(String value) {
        return ChatColor.stripColor(value).trim().toLowerCase(Locale.ROOT);
    }
}
