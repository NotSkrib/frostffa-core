package dev.notskrib.frostffacore.kills;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.manager.IManager;
import dev.notskrib.frostffacore.economy.KillRewardManager;
import dev.notskrib.frostffacore.economy.Points;
import dev.notskrib.frostffacore.settings.PlayerSetting;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public class KillMessageManager implements IManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    // "coins" is the documented placeholder; old names remain working aliases so
    // lines pasted from an old MoneyFFAV2-style config don't silently blank (plan §6.11).
    private static final String[] KEYS = { "killer", "victim", "weapon", "money", "coins", "shards", "streak", "health" };

    private static final int STREAK_ANNOUNCE_THRESHOLD = 5;

    private final FrostFFACore plugin;

    private boolean enabled = true;
    private List<String> pvp = List.of();
    private List<String> streakEnded = List.of();
    private List<String> combatLog = List.of();
    private final Map<String, List<String>> environment = new HashMap<>();

    public KillMessageManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.reload();
        this.plugin.getLogger().info("KillMessageManager enabled ("
            + (this.enabled ? this.pvp.size() + " kill line(s)" : "disabled") + ").");
    }

    @Override
    public void onDisable() {
        this.plugin.getLogger().info("KillMessageManager disabled.");
    }

    public void reload() {
        ConfigurationSection root = this.plugin.getConfig().getConfigurationSection("kill-messages");
        this.environment.clear();
        if (root == null) {
            this.enabled = false;
            this.pvp = List.of();
            this.streakEnded = List.of();
            this.combatLog = List.of();
            return;
        }
        this.enabled = root.getBoolean("enabled", true);
        this.pvp = List.copyOf(root.getStringList("pvp"));
        this.streakEnded = List.copyOf(root.getStringList("streak-ended"));
        this.combatLog = List.copyOf(root.getStringList("combat-log"));

        ConfigurationSection env = root.getConfigurationSection("environment");
        if (env != null) {
            for (String key : env.getKeys(false)) {
                this.environment.put(key.toLowerCase(), List.copyOf(env.getStringList(key)));
            }
        }
    }

    public boolean handlesStreakEnd() {
        return this.enabled && !this.streakEnded.isEmpty();
    }

    public void applyKill(PlayerDeathEvent event, Player killer, Player victim,
                          KillRewardManager.Payout payout, int killerStreak, int victimStreak) {
        if (!this.enabled) return;

        List<String> pool;
        int streak;
        if (victimStreak >= STREAK_ANNOUNCE_THRESHOLD && !this.streakEnded.isEmpty()) {
            pool = this.streakEnded;
            streak = victimStreak;
        } else {
            pool = this.pvp;
            streak = killerStreak;
        }
        if (pool.isEmpty()) return;

        Component weaponName = this.defaultWeaponName(killer);
        String payoutFormatted = Points.format(payout.amount(), this.plugin.getEconomyManager());
        this.deliver(event, this.render(pool,
            Placeholder.unparsed("killer", killer.getName()),
            Placeholder.unparsed("victim", victim.getName()),
            Placeholder.component("weapon", weaponName),
            Placeholder.unparsed("money", payoutFormatted),
            Placeholder.unparsed("coins", payoutFormatted),
            Placeholder.unparsed("shards", payoutFormatted),
            Placeholder.unparsed("streak", String.valueOf(streak)),
            Placeholder.unparsed("health", formatHearts(killer.getHealth()))
        ), victim, killer);
    }

    public void applyEnvironment(PlayerDeathEvent event, Player victim, boolean combatLogged) {
        if (!this.enabled) return;

        List<String> pool = combatLogged ? this.combatLog : this.environment.getOrDefault(
            causeKey(victim), this.environment.getOrDefault("other", List.of()));
        if (pool.isEmpty()) return;

        this.deliver(event, this.render(pool,
            Placeholder.unparsed("killer", ""),
            Placeholder.unparsed("victim", victim.getName()),
            Placeholder.unparsed("weapon", ""),
            Placeholder.unparsed("money", ""),
            Placeholder.unparsed("coins", ""),
            Placeholder.unparsed("shards", ""),
            Placeholder.unparsed("streak", "0"),
            Placeholder.unparsed("health", "0")
        ), victim, null);
    }

    private void deliver(PlayerDeathEvent event, Component message, Player victim, Player killer) {
        event.deathMessage(null);

        for (Player viewer : this.plugin.getServer().getOnlinePlayers()) {
            boolean involved = viewer.getUniqueId().equals(victim.getUniqueId())
                || (killer != null && viewer.getUniqueId().equals(killer.getUniqueId()));
            if (involved || this.plugin.getSettingsManager().get(viewer, PlayerSetting.KILL_MESSAGES)) {
                viewer.sendMessage(message);
            }
        }
        this.plugin.getServer().getConsoleSender().sendMessage(message);
    }

    private Component render(List<String> pool, TagResolver... resolvers) {
        String raw = pool.size() == 1 ? pool.get(0)
            : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        for (String key : KEYS) {
            raw = raw.replace("{" + key + "}", "<" + key + ">");
        }
        return MM.deserialize(raw, resolvers);
    }

    private Component defaultWeaponName(Player killer) {
        TextColor glacier = TextColor.fromHexString(Palette.GLACIER);
        ItemStack item = killer.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            return Component.text(Palette.sc("fists"), glacier);
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return Component.text(PLAIN.serialize(item.getItemMeta().displayName()), glacier);
        }
        return Component.text(Palette.sc(item.getType().name().toLowerCase().replace('_', ' ')), glacier);
    }

    private static String causeKey(Player victim) {
        EntityDamageEvent last = victim.getLastDamageCause();
        if (last == null) return "other";
        return switch (last.getCause()) {
            case VOID -> "void";
            case FALL -> "fall";
            case FIRE, FIRE_TICK, HOT_FLOOR -> "fire";
            case LAVA -> "lava";
            case DROWNING -> "drowning";
            default -> "other";
        };
    }

    private static String formatHearts(double health) {
        double hearts = health / 2.0;
        return hearts == Math.floor(hearts)
            ? String.valueOf((int) hearts)
            : String.format("%.1f", hearts);
    }
}
