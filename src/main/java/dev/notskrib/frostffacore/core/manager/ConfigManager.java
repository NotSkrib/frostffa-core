package dev.notskrib.frostffacore.core.manager;

import dev.notskrib.frostffacore.FrostFFACore;
import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigManager implements IManager {

    private final FrostFFACore plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    public ConfigManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.plugin.saveDefaultConfig();
        this.plugin.getConfig().options().copyDefaults(true);
        this.plugin.saveConfig();
        this.dataFile = new File(this.plugin.getDataFolder(), "data.yml");
        if (!this.dataFile.exists()) {
            try {
                boolean created = this.dataFile.createNewFile();
                if (!created) {
                    this.plugin.getLogger().warning("data.yml already exists — skipping creation.");
                }
            } catch (IOException e) {
                this.plugin.getLogger().severe("Could not create data.yml: " + e.getMessage());
            }
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(this.dataFile);
        this.plugin.getLogger().info("ConfigManager enabled.");
    }

    @Override
    public void onDisable() {
        this.saveDataConfig();
        this.plugin.getLogger().info("ConfigManager disabled — data saved.");
    }

    public FileConfiguration getConfig() {
        return this.plugin.getConfig();
    }

    public FileConfiguration getDataConfig() {
        return this.dataConfig;
    }

    public void saveDataConfig() {
        if (this.dataConfig == null || this.dataFile == null) {
            return;
        }
        try {
            this.dataConfig.save(this.dataFile);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }

    public void reloadAll() {
        this.plugin.reloadConfig();
        this.dataConfig = YamlConfiguration.loadConfiguration(this.dataFile);
    }
}
