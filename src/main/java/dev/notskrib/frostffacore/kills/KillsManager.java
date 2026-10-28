package dev.notskrib.frostffacore.kills;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class KillsManager implements IManager {

    private final FrostFFACore plugin;
    private File storageFile;
    private FileConfiguration storageConfig;
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> deaths = new HashMap<>();

    public KillsManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }
    @Override
    public void onEnable() {
        this.storageFile = new File(this.plugin.getDataFolder(), "kills.yml");
        this.loadData();
        this.plugin.getLogger().info("KillsManager enabled.");
    }

    @Override
    public void onDisable() {
        this.saveData();
        this.plugin.getLogger().info("KillsManager disabled.");
    }

    public void addKill(Player player) {
        this.kills.merge(player.getUniqueId(), 1, Integer::sum);
        this.saveData();
    }

    public Map<UUID, Integer> snapshot() {
        return new HashMap<>(this.kills);
    }

    public int getKills(UUID uuid) {
        return this.kills.getOrDefault(uuid, 0);
    }

    public void addDeath(Player player) {
        this.deaths.merge(player.getUniqueId(), 1, Integer::sum);
        this.saveData();
    }

    public int getDeaths(UUID uuid) {
        return this.deaths.getOrDefault(uuid, 0);
    }

    public Map<UUID, Integer> deathsSnapshot() {
        return new HashMap<>(this.deaths);
    }

    public int resetAllPlayerData() {
        int removed = this.kills.size();
        this.kills.clear();
        this.deaths.clear();
        this.saveData();
        return removed;
    }

    private void loadData() {
        if (!this.storageFile.exists()) {
            try {
                this.storageFile.createNewFile();
            } catch (IOException ignored) {
            }
        }
        this.storageConfig = YamlConfiguration.loadConfiguration(this.storageFile);
        ConfigurationSection section = this.storageConfig.getConfigurationSection("kills");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    this.kills.put(UUID.fromString(key), section.getInt(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
            ConfigurationSection deathSection = this.storageConfig.getConfigurationSection("deaths");
            if (deathSection != null) {
                for (String key : deathSection.getKeys(false)) {
                    try {
                        this.deaths.put(UUID.fromString(key), deathSection.getInt(key));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
    }

    private void saveData() {
        this.storageConfig = new YamlConfiguration();
        this.kills.forEach((uuid, k) -> this.storageConfig.set("kills." + uuid, k));
        this.deaths.forEach((uuid, d) -> this.storageConfig.set("deaths." + uuid, d));
        try {
            this.storageConfig.save(this.storageFile);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Could not save kills.yml!");
        }
    }
}
