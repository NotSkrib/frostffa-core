package dev.notskrib.frostffacore.join;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class WelcomeManager implements IManager, Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long MOTD_DELAY_TICKS = 40L;

    private final FrostFFACore plugin;
    private final Map<UUID, Integer> members = new ConcurrentHashMap<>();

    private File file;
    private volatile int highest = 0;

    public WelcomeManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.file = new File(this.plugin.getDataFolder(), "members.yml");
        this.load();
        this.plugin.getLogger().info("WelcomeManager enabled — " + this.highest + " member(s) so far.");
    }

    @Override
    public void onDisable() {
        this.save();
        this.plugin.getLogger().info("WelcomeManager disabled.");
    }

    public int memberCount() {
        return this.highest;
    }

    public int memberNumber(UUID uuid) {
        return this.members.getOrDefault(uuid, 0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean firstJoin = !this.members.containsKey(player.getUniqueId());

        if (firstJoin) {
            int number = ++this.highest;
            this.members.put(player.getUniqueId(), number);
            this.save();

            this.welcome(player, number);
            this.announce(player, number);
        }

        this.scheduleMotd(player);
    }

    private void welcome(Player player, int number) {
        ConfigurationSection cfg = this.plugin.getConfig().getConfigurationSection("welcome");
        String title = cfg != null ? cfg.getString("title", "") : "";
        String subtitle = cfg != null ? cfg.getString("subtitle", "") : "";
        if (title.isEmpty() && subtitle.isEmpty()) return;

        player.showTitle(Title.title(
            MM.deserialize(title, Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("number", String.valueOf(number)),
                Placeholder.unparsed("ordinal", ordinal(number))),
            MM.deserialize(subtitle, Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("number", String.valueOf(number)),
                Placeholder.unparsed("ordinal", ordinal(number))),
            Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(5), Duration.ofSeconds(1))
        ));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
    }

    private void announce(Player player, int number) {
        ConfigurationSection cfg = this.plugin.getConfig().getConfigurationSection("welcome");
        String line = cfg != null ? cfg.getString("broadcast", "") : "";
        if (line.isEmpty()) return;

        MessageUtil.broadcastRaw(line,
            Placeholder.unparsed("player", player.getName()),
            Placeholder.unparsed("number", String.valueOf(number)),
            Placeholder.unparsed("ordinal", ordinal(number)));
    }

    /**
     * NEW addition (not in MoneyFFAV2): a short MOTD DM'd to EVERY player, first-timer or
     * not, 2 seconds after join — landing after the server's own join spam. Skipped
     * entirely when the list is empty.
     */
    private void scheduleMotd(Player player) {
        List<String> motd = this.plugin.getConfig().getStringList("welcome.motd");
        if (motd.isEmpty()) return;

        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (!player.isOnline()) return;
            for (String line : motd) {
                player.sendMessage(MM.deserialize(line));
            }
        }, MOTD_DELAY_TICKS);
    }

    static String ordinal(int number) {
        int lastTwo = Math.abs(number) % 100;
        if (lastTwo >= 11 && lastTwo <= 13) return number + "th";
        return switch (Math.abs(number) % 10) {
            case 1 -> number + "st";
            case 2 -> number + "nd";
            case 3 -> number + "rd";
            default -> number + "th";
        };
    }

    private void load() {
        this.members.clear();
        this.highest = 0;
        if (this.file == null || !this.file.exists()) return;

        YamlConfiguration in = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection players = in.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                try {
                    this.members.put(UUID.fromString(key), players.getInt(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        this.highest = Math.max(in.getInt("count", 0),
            this.members.values().stream().mapToInt(Integer::intValue).max().orElse(0));
    }

    private void save() {
        YamlConfiguration out = new YamlConfiguration();
        out.set("count", this.highest);
        this.members.forEach((uuid, number) -> out.set("players." + uuid, number));
        try {
            out.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Could not save members.yml: " + e.getMessage());
        }
    }

    public static String format(int number) {
        return String.format(Locale.ROOT, "%,d", number);
    }
}
