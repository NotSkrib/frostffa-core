package dev.notskrib.frostffacore.screenshare.session;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Write-through persistence for active sessions. Kept as flat YAML (volume is a
 * handful of rows at most). Sessions are deliberately NOT cleared on shutdown so
 * a restart or /reload cannot free a detainee.
 */
public final class SessionStore {

    private final Plugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public SessionStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sessions.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create sessions.yml: " + e.getMessage());
            }
        }
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public Map<UUID, ScreenShareSession> readAll() {
        Map<UUID, ScreenShareSession> out = new HashMap<>();
        ConfigurationSection root = yaml.getConfigurationSection("sessions");
        if (root == null) {
            return out;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                UUID target = UUID.fromString(key);
                UUID staff = UUID.fromString(s.getString("staff", new UUID(0, 0).toString()));
                long started = s.getLong("started");
                int slot = s.getInt("slot");
                LocationSnapshot back = new LocationSnapshot(
                        s.getString("return.world", "world"),
                        s.getDouble("return.x"), s.getDouble("return.y"), s.getDouble("return.z"),
                        (float) s.getDouble("return.yaw"), (float) s.getDouble("return.pitch"));
                GameMode gameMode = parseGameMode(s.getString("gamemode", "SURVIVAL"));
                boolean allowFlight = s.getBoolean("allow-flight");
                boolean flying = s.getBoolean("flying");
                out.put(target, new ScreenShareSession(
                        target, staff, started, slot, back, gameMode, allowFlight, flying));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping malformed session entry '" + key + "': " + e.getMessage());
            }
        }
        return out;
    }

    public void write(ScreenShareSession session) {
        String base = "sessions." + session.target();
        yaml.set(base + ".staff", session.staff().toString());
        yaml.set(base + ".started", session.startedAt());
        yaml.set(base + ".slot", session.slot());
        yaml.set(base + ".gamemode", session.returnGameMode().name());
        yaml.set(base + ".allow-flight", session.returnAllowFlight());
        yaml.set(base + ".flying", session.returnFlying());
        LocationSnapshot loc = session.returnLocation();
        yaml.set(base + ".return.world", loc.world());
        yaml.set(base + ".return.x", loc.x());
        yaml.set(base + ".return.y", loc.y());
        yaml.set(base + ".return.z", loc.z());
        yaml.set(base + ".return.yaw", loc.yaw());
        yaml.set(base + ".return.pitch", loc.pitch());
        save();
    }

    public void delete(UUID target) {
        yaml.set("sessions." + target, null);
        save();
    }

    /** UUIDs of players banned by the plugin, awaiting a teleport on their next login. */
    public java.util.Set<UUID> readPendingRejoin() {
        java.util.Set<UUID> out = new java.util.HashSet<>();
        for (String raw : yaml.getStringList("pending-rejoin")) {
            try {
                out.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // drop malformed entries silently on next write
            }
        }
        return out;
    }

    public void writePendingRejoin(java.util.Set<UUID> pending) {
        java.util.List<String> raw = new java.util.ArrayList<>(pending.size());
        for (UUID id : pending) {
            raw.add(id.toString());
        }
        yaml.set("pending-rejoin", raw);
        save();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save sessions.yml: " + e.getMessage());
        }
    }

    private GameMode parseGameMode(String name) {
        try {
            return GameMode.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return GameMode.SURVIVAL;
        }
    }
}
