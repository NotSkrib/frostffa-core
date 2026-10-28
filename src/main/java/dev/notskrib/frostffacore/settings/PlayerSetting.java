package dev.notskrib.frostffacore.settings;

import org.bukkit.Material;

public enum PlayerSetting {

    KILL_MESSAGES("kill-messages", "Kill Messages", true, Material.IRON_SWORD,
        "see who killed who in chat.",
        "your own deaths are always shown."),

    MENTIONS("mentions", "Mentions", true, Material.NAME_TAG,
        "let people ping you by typing",
        "@yourname in chat."),

    ACTION_BAR("action-bar", "Action Bar", true, Material.PAPER,
        "show your coins and combat",
        "timer above the hotbar."),

    BOUNTY_ALERTS("bounty-alerts", "Bounty Alerts", true, Material.SKELETON_SKULL,
        "hear about new bounties,",
        "including ones on you."),

    STREAK_BROADCASTS("streak-broadcasts", "Streak Calls", true, Material.BLAZE_POWDER,
        "see killstreak milestones",
        "announced in chat."),

    SCOREBOARD("scoreboard", "Scoreboard", true, Material.OAK_SIGN,
        "show the sidebar scoreboard.",
        "bridges to TAB's own /sb toggle.");

    private final String key;
    private final String label;
    private final boolean byDefault;
    private final Material icon;
    private final String[] description;

    PlayerSetting(String key, String label, boolean byDefault, Material icon, String... description) {
        this.key = key;
        this.label = label;
        this.byDefault = byDefault;
        this.icon = icon;
        this.description = description;
    }

    public String key() {
        return this.key;
    }

    public String label() {
        return this.label;
    }

    public boolean byDefault() {
        return this.byDefault;
    }

    public Material icon() {
        return this.icon;
    }

    public String[] description() {
        return this.description;
    }

    public static PlayerSetting byKey(String key) {
        for (PlayerSetting setting : values()) {
            if (setting.key.equalsIgnoreCase(key)) return setting;
        }
        return null;
    }
}
