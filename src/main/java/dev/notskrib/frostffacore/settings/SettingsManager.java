package dev.notskrib.frostffacore.settings;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

public class SettingsManager implements IManager, Listener {

    private final FrostFFACore plugin;
    private final Map<UUID, Map<PlayerSetting, Boolean>> overrides = new ConcurrentHashMap<>();

    private File file;
    private BukkitTask saveTask;
    private volatile boolean dirty = false;

    public SettingsManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.file = new File(this.plugin.getDataFolder(), "settings.yml");
        this.load();
        this.saveTask = this.plugin.getServer().getScheduler()
            .runTaskTimer(this.plugin, this::saveIfDirty, 20L * 60L, 20L * 60L);
        this.plugin.getLogger().info("SettingsManager enabled — " + this.overrides.size()
            + " player(s) with custom settings.");
    }

    @Override
    public void onDisable() {
        if (this.saveTask != null) {
            this.saveTask.cancel();
            this.saveTask = null;
        }
        this.save();
        this.plugin.getLogger().info("SettingsManager disabled.");
    }

    public boolean get(UUID uuid, PlayerSetting setting) {
        Map<PlayerSetting, Boolean> mine = this.overrides.get(uuid);
        if (mine == null) return setting.byDefault();
        return mine.getOrDefault(setting, setting.byDefault());
    }

    public boolean get(Player player, PlayerSetting setting) {
        return this.get(player.getUniqueId(), setting);
    }

    public void set(UUID uuid, PlayerSetting setting, boolean value) {
        if (value == setting.byDefault()) {
            Map<PlayerSetting, Boolean> mine = this.overrides.get(uuid);
            if (mine != null) {
                mine.remove(setting);
                if (mine.isEmpty()) this.overrides.remove(uuid);
            }
        } else {
            this.overrides.computeIfAbsent(uuid, id -> new ConcurrentHashMap<>())
                .put(setting, value);
        }
        this.dirty = true;
    }

    public boolean toggle(UUID uuid, PlayerSetting setting) {
        boolean next = !this.get(uuid, setting);
        this.set(uuid, setting, next);
        return next;
    }

    public void reload() {
        this.load();
    }

    private void saveIfDirty() {
        if (!this.dirty) return;
        this.save();
        this.dirty = false;
    }

    private void save() {
        YamlConfiguration out = new YamlConfiguration();
        this.overrides.forEach((uuid, mine) ->
            mine.forEach((setting, value) -> out.set("players." + uuid + "." + setting.key(), value)));
        try {
            out.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Could not save settings.yml: " + e.getMessage());
        }
    }

    private void load() {
        this.overrides.clear();
        if (!this.file.exists()) return;

        YamlConfiguration in = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection players = in.getConfigurationSection("players");
        if (players == null) return;

        for (String key : players.getKeys(false)) {
            ConfigurationSection mine = players.getConfigurationSection(key);
            if (mine == null) continue;
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Map<PlayerSetting, Boolean> map = new ConcurrentHashMap<>();
            for (String settingKey : mine.getKeys(false)) {
                PlayerSetting setting = PlayerSetting.byKey(settingKey);
                // A key from an older build whose setting no longer exists is dropped on the
                // next save rather than crashing the load.
                if (setting != null) map.put(setting, mine.getBoolean(settingKey));
            }
            if (!map.isEmpty()) this.overrides.put(uuid, map);
        }
    }
}
