package dev.notskrib.frostffacore.screenshare.config;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Typed view over config.yml. Mirrors ErrorSMP-PlayerLink's ConfigManager: load
 * everything once in {@link #reload()} and hand out plain getters.
 */
public final class ScreenShareConfig {

    private final JavaPlugin plugin;

    private String boxWorld;
    private int boxBaseX;
    private int boxBaseY;
    private int boxBaseZ;
    private int boxSpacing;
    private int boxInterior;
    private boolean cleanupBox;

    private boolean customRoomEnabled;
    private String customRoomWorld;
    private double customRoomX;
    private double customRoomY;
    private double customRoomZ;
    private float customRoomYaw;
    private float customRoomPitch;
    private String msgRoomOccupied;

    private int titleFadeIn;
    private int titleStay;
    private int titleFadeOut;
    private int titleResendSeconds;
    private String titleMain;
    private String titleSubtitle;

    private String lockdownGamemode;
    private List<PotionEffect> lockdownEffects;
    private boolean allowMovement;
    private boolean blockCommands;
    private List<String> commandWhitelist;
    private boolean escapeBan;

    private boolean onQuitBan;
    private String onQuitCommand;
    private String onQuitReason;

    private boolean rejoinEnabled;
    private String rejoinWorld;
    private double rejoinX;
    private double rejoinY;
    private double rejoinZ;
    private float rejoinYaw;
    private float rejoinPitch;
    private String rejoinMessage;

    private String admitCommand;
    private String admitReason;
    private String refuseCommand;
    private String refuseReason;
    private String escapeCommand;
    private String escapeReason;

    private String menuMode;
    private String menuTitle;
    private boolean forceMenu;
    private boolean requireConfirm;
    private int admitSlot;
    private int refuseSlot;
    private Material admitMaterial;
    private Material refuseMaterial;
    private String admitName;
    private String refuseName;
    private List<String> admitLore;
    private List<String> refuseLore;
    private String confirmTitle;
    private int confirmSlot;
    private int cancelSlot;

    private String prefix;
    private String msgNoPermission;
    private String msgPlayerNotFound;
    private String msgTargetExempt;
    private String msgStarted;
    private String msgReleased;
    private String msgReloaded;
    private String msgDetainedBlocked;
    private String msgUsage;

    public ScreenShareConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        boxWorld = c.getString("screenshare.box.world", "world");
        boxBaseX = c.getInt("screenshare.box.base-x", 10_000_000);
        boxBaseY = c.getInt("screenshare.box.base-y", 100);
        boxBaseZ = c.getInt("screenshare.box.base-z", 10_000_000);
        boxSpacing = Math.max(8, c.getInt("screenshare.box.spacing", 64));
        boxInterior = Math.max(3, c.getInt("screenshare.box.interior-size", 5));
        cleanupBox = c.getBoolean("screenshare.box.cleanup-box", true);

        customRoomEnabled = c.getBoolean("screenshare.custom-room.enabled", false);
        customRoomWorld = c.getString("screenshare.custom-room.world", "world");
        customRoomX = c.getDouble("screenshare.custom-room.x", 0.5);
        customRoomY = c.getDouble("screenshare.custom-room.y", 100);
        customRoomZ = c.getDouble("screenshare.custom-room.z", 0.5);
        customRoomYaw = (float) c.getDouble("screenshare.custom-room.yaw", 0);
        customRoomPitch = (float) c.getDouble("screenshare.custom-room.pitch", 0);

        titleFadeIn = c.getInt("screenshare.title.fade-in-ticks", 10);
        titleStay = c.getInt("screenshare.title.stay-ticks", 12_000);
        titleFadeOut = c.getInt("screenshare.title.fade-out-ticks", 20);
        titleResendSeconds = Math.max(1, c.getInt("screenshare.title.resend-seconds", 8));
        titleMain = c.getString("screenshare.title.main", "&c&lSCREEN SHARE");
        titleSubtitle = c.getString("screenshare.title.subtitle", "&fJoin the ScreenShare VC in Discord  -  leaving = ban");

        lockdownGamemode = c.getString("screenshare.lockdown.gamemode", "SURVIVAL");
        lockdownEffects = parseEffects(c.getStringList("screenshare.lockdown.effects"));
        allowMovement = c.getBoolean("screenshare.lockdown.allow-movement", true);
        blockCommands = c.getBoolean("screenshare.lockdown.block-commands", true);
        commandWhitelist = lowercase(c.getStringList("screenshare.lockdown.command-whitelist"));
        escapeBan = c.getBoolean("screenshare.lockdown.escape-attempt-ban", true);

        onQuitBan = c.getBoolean("screenshare.on-quit.ban", true);
        onQuitCommand = c.getString("screenshare.on-quit.command", "ban %player% %reason%");
        onQuitReason = c.getString("screenshare.on-quit.reason", "Logged out during a screen share");

        rejoinEnabled = c.getBoolean("screenshare.rejoin.enabled", true);
        rejoinWorld = c.getString("screenshare.rejoin.world", "world");
        rejoinX = c.getDouble("screenshare.rejoin.x", 0.5);
        rejoinY = c.getDouble("screenshare.rejoin.y", 100);
        rejoinZ = c.getDouble("screenshare.rejoin.z", 0.5);
        rejoinYaw = (float) c.getDouble("screenshare.rejoin.yaw", 0);
        rejoinPitch = (float) c.getDouble("screenshare.rejoin.pitch", 0);
        rejoinMessage = c.getString("screenshare.rejoin.message", "&cYou were banned from a screen share. Don't cheat.");

        admitCommand = c.getString("screenshare.punishments.admit.command", "ban %player% %reason%");
        admitReason = c.getString("screenshare.punishments.admit.reason", "Admitted to cheating during screen share");
        refuseCommand = c.getString("screenshare.punishments.refuse.command", "ban %player% %reason%");
        refuseReason = c.getString("screenshare.punishments.refuse.reason", "Refused to screen share");
        escapeCommand = c.getString("screenshare.punishments.escape.command", "ban %player% %reason%");
        escapeReason = c.getString("screenshare.punishments.escape.reason", "Attempted to escape screen share");

        menuMode = c.getString("screenshare.menu.mode", "BUTTONS").toUpperCase(Locale.ROOT);
        menuTitle = c.getString("screenshare.menu.title", "&cScreen Share");
        forceMenu = c.getBoolean("screenshare.menu.force-menu", true);
        requireConfirm = c.getBoolean("screenshare.menu.require-confirm", true);
        admitSlot = c.getInt("screenshare.menu.admit.slot", 11);
        refuseSlot = c.getInt("screenshare.menu.refuse.slot", 15);
        admitMaterial = material(c.getString("screenshare.menu.admit.material", "RED_CONCRETE"), Material.RED_CONCRETE);
        refuseMaterial = material(c.getString("screenshare.menu.refuse.material", "GRAY_CONCRETE"), Material.GRAY_CONCRETE);
        admitName = c.getString("screenshare.menu.admit.name", "&c&lAdmit Defeat");
        refuseName = c.getString("screenshare.menu.refuse.name", "&8&lRefuse Screen Share");
        admitLore = c.getStringList("screenshare.menu.admit.lore");
        refuseLore = c.getStringList("screenshare.menu.refuse.lore");
        confirmTitle = c.getString("screenshare.menu.confirm.title", "&4Are you sure?");
        confirmSlot = c.getInt("screenshare.menu.confirm.confirm-slot", 11);
        cancelSlot = c.getInt("screenshare.menu.confirm.cancel-slot", 15);

        prefix = c.getString("screenshare.messages.prefix", "&8[&cSS&8] &r");
        msgNoPermission = c.getString("screenshare.messages.no-permission", "&cYou don't have permission.");
        msgPlayerNotFound = c.getString("screenshare.messages.player-not-found", "&cPlayer not found: %player%");
        msgTargetExempt = c.getString("screenshare.messages.target-exempt", "&c%player% is exempt from screen share.");
        msgStarted = c.getString("screenshare.messages.started", "&e%player% &7has been put into screen share.");
        msgReleased = c.getString("screenshare.messages.released", "&a%player% &7has been released from screen share.");
        msgReloaded = c.getString("screenshare.messages.reloaded", "&aScreenShare config reloaded.");
        msgDetainedBlocked = c.getString("screenshare.messages.detained-blocked", "&cYou cannot do that during a screen share.");
        msgUsage = c.getString("screenshare.messages.usage", "&7Usage: /ss <player> | /ss reload");
        msgRoomOccupied = c.getString("screenshare.messages.room-occupied",
                "&cThe screen-share room is already occupied.");
    }

    private List<PotionEffect> parseEffects(List<String> raw) {
        List<PotionEffect> effects = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.split(":");
            PotionEffectType type = effectType(parts[0].trim());
            if (type == null) {
                plugin.getLogger().warning("Ignoring unknown potion effect in config: " + entry);
                continue;
            }
            int amplifier = 0;
            if (parts.length > 1) {
                try {
                    amplifier = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Bad amplifier in config effect '" + entry + "', using 0.");
                }
            }
            effects.add(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, false, false, false));
        }
        return effects;
    }

    @SuppressWarnings("deprecation")
    private PotionEffectType effectType(String name) {
        try {
            PotionEffectType byKey = Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
            if (byKey != null) {
                return byKey;
            }
        } catch (RuntimeException ignored) {
            // fall through to the legacy lookup
        }
        return PotionEffectType.getByName(name);
    }

    private Material material(String name, Material fallback) {
        Material parsed = Material.matchMaterial(name == null ? "" : name);
        return parsed == null ? fallback : parsed;
    }

    private List<String> lowercase(List<String> input) {
        List<String> out = new ArrayList<>(input.size());
        for (String value : input) {
            out.add(value.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    public String boxWorld() {
        return boxWorld;
    }

    public int boxBaseX() {
        return boxBaseX;
    }

    public int boxBaseY() {
        return boxBaseY;
    }

    public int boxBaseZ() {
        return boxBaseZ;
    }

    public int boxSpacing() {
        return boxSpacing;
    }

    public int boxInterior() {
        return boxInterior;
    }

    public boolean cleanupBox() {
        return cleanupBox;
    }

    public boolean customRoomEnabled() {
        return customRoomEnabled;
    }

    /** The fixed teleport target for the custom room, or null if its world isn't loaded. */
    public org.bukkit.Location customRoomLocation() {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(customRoomWorld);
        if (world == null) {
            return null;
        }
        return new org.bukkit.Location(world, customRoomX, customRoomY, customRoomZ, customRoomYaw, customRoomPitch);
    }

    public String msgRoomOccupied() {
        return msgRoomOccupied;
    }

    public int titleFadeIn() {
        return titleFadeIn;
    }

    public int titleStay() {
        return titleStay;
    }

    public int titleFadeOut() {
        return titleFadeOut;
    }

    public int titleResendSeconds() {
        return titleResendSeconds;
    }

    public String titleMain() {
        return titleMain;
    }

    public String titleSubtitle() {
        return titleSubtitle;
    }

    public GameMode lockdownGamemode() {
        try {
            return GameMode.valueOf(lockdownGamemode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GameMode.SURVIVAL;
        }
    }

    public List<PotionEffect> lockdownEffects() {
        return lockdownEffects;
    }

    public boolean allowMovement() {
        return allowMovement;
    }

    public boolean blockCommands() {
        return blockCommands;
    }

    public List<String> commandWhitelist() {
        return commandWhitelist;
    }

    public boolean escapeBan() {
        return escapeBan;
    }

    public boolean onQuitBan() {
        return onQuitBan;
    }

    public String onQuitCommand() {
        return onQuitCommand;
    }

    public String onQuitReason() {
        return onQuitReason;
    }

    public boolean rejoinEnabled() {
        return rejoinEnabled;
    }

    public String rejoinMessage() {
        return rejoinMessage;
    }

    /** Where a previously-banned player is sent on their next login, or null if the world is missing. */
    public org.bukkit.Location rejoinLocation() {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(rejoinWorld);
        if (world == null) {
            return null;
        }
        return new org.bukkit.Location(world, rejoinX, rejoinY, rejoinZ, rejoinYaw, rejoinPitch);
    }

    public String admitCommand() {
        return admitCommand;
    }

    public String admitReason() {
        return admitReason;
    }

    public String refuseCommand() {
        return refuseCommand;
    }

    public String refuseReason() {
        return refuseReason;
    }

    public String escapeCommand() {
        return escapeCommand;
    }

    public String escapeReason() {
        return escapeReason;
    }

    public String menuTitle() {
        return menuTitle;
    }

    public boolean menuButtons() {
        return !"CHEST".equals(menuMode);
    }

    public boolean menuChest() {
        return "CHEST".equals(menuMode);
    }

    public boolean forceMenu() {
        return forceMenu;
    }

    public boolean requireConfirm() {
        return requireConfirm;
    }

    public int admitSlot() {
        return admitSlot;
    }

    public int refuseSlot() {
        return refuseSlot;
    }

    public Material admitMaterial() {
        return admitMaterial;
    }

    public Material refuseMaterial() {
        return refuseMaterial;
    }

    public String admitName() {
        return admitName;
    }

    public String refuseName() {
        return refuseName;
    }

    public List<String> admitLore() {
        return admitLore;
    }

    public List<String> refuseLore() {
        return refuseLore;
    }

    public String confirmTitle() {
        return confirmTitle;
    }

    public int confirmSlot() {
        return confirmSlot;
    }

    public int cancelSlot() {
        return cancelSlot;
    }

    public String prefix() {
        return prefix;
    }

    public String msgNoPermission() {
        return msgNoPermission;
    }

    public String msgPlayerNotFound() {
        return msgPlayerNotFound;
    }

    public String msgTargetExempt() {
        return msgTargetExempt;
    }

    public String msgStarted() {
        return msgStarted;
    }

    public String msgReleased() {
        return msgReleased;
    }

    public String msgReloaded() {
        return msgReloaded;
    }

    public String msgDetainedBlocked() {
        return msgDetainedBlocked;
    }

    public String msgUsage() {
        return msgUsage;
    }
}
