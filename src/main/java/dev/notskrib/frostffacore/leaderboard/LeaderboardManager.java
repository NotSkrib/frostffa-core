package dev.notskrib.frostffacore.leaderboard;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.manager.IManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Vanilla {@code TextDisplay} leaderboards, forked from MoneyFFAV2 (plan §6.1). The slow
 * {@code refresh-seconds} timer sorts and renders the static boards; {@link #placeCycle} adds
 * cycling boards, and the cache feeds {@link LeaderboardAnimator}.
 */
public class LeaderboardManager implements IManager, Listener {

    public record Row(String name, String value) {}

    private static final MiniMessage MM = MiniMessage.miniMessage();
    static final int FLASH_FRAMES = 6;

    // Circled digits ①-⑮ — covers every row up to the 15-row cap enforced in readConfig().
    private static final String[] CIRCLED = {
        "①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧",
        "⑨", "⑩", "⑪", "⑫", "⑬", "⑭", "⑮"
    };

    /** Circled-digit row marker (①②③…), falling back to a plain "#n" past the covered range. */
    static String circled(int position) {
        return (position >= 1 && position <= CIRCLED.length) ? CIRCLED[position - 1] : "#" + position;
    }

    private final FrostFFACore plugin;
    private final NamespacedKey boardKey;
    private final Map<String, Leaderboard> boards = new LinkedHashMap<>();

    private File file;
    private BukkitTask refreshTask;
    private BukkitTask proximityTask;
    private LeaderboardAnimator animator;

    private int rows;
    private double yOffset;
    private boolean personalRank;
    private double viewDistance;
    private float yawOffset;
    private Display.Billboard billboard;
    private int backgroundAlpha;
    private Color backgroundColor;
    private String title;
    private String subtitle;
    private String emptyRow;
    private String rowFormat;
    private String footer;
    private String footerEmpty;
    private String animationHeaderColor;
    private String animationSubtitleColor;
    private String animationPositionColor;
    private String animationNameColor;
    private String animationValueColor;

    // Cache the animator reads from — populated only on the slow refresh timer below.
    private final Map<LeaderboardType, List<Row>> rowCache = new EnumMap<>(LeaderboardType.class);
    private final Map<LeaderboardType, Map<UUID, Integer>> rankCache = new EnumMap<>(LeaderboardType.class);
    private final Map<LeaderboardType, Map<UUID, Long>> valueCache = new EnumMap<>(LeaderboardType.class);
    private final Map<LeaderboardType, Map<Integer, Integer>> flashFrames = new EnumMap<>(LeaderboardType.class);

    public LeaderboardManager(FrostFFACore plugin) {
        this.plugin = plugin;
        this.boardKey = new NamespacedKey(plugin, "leaderboard");
    }

    @Override
    public void onEnable() {
        this.file = new File(this.plugin.getDataFolder(), "leaderboards.yml");
        this.readConfig();
        this.load();
        this.sweepStrays();

        if (!this.personalRank) {
            this.boards.values().forEach(this::spawnShared);
        }

        int seconds = Math.max(5, this.plugin.getConfig().getInt("leaderboards.refresh-seconds", 15));
        this.refreshTask = this.plugin.getServer().getScheduler()
            .runTaskTimer(this.plugin, this::refreshAll, 100L, seconds * 20L);
        this.proximityTask = this.plugin.getServer().getScheduler()
            .runTaskTimer(this.plugin, this::syncViewers, 60L, 20L);

        this.animator = new LeaderboardAnimator(this.plugin, this);
        this.animator.start();

        this.plugin.getLogger().info("LeaderboardManager enabled — " + this.boards.size()
            + " board(s), " + (this.personalRank ? "personal" : "shared") + " mode.");
    }

    @Override
    public void onDisable() {
        // Cancel the animation task BEFORE refresh/proximity, then despawn (plan §6.1's
        // disable-order note) — an animation frame firing after displays are removed would
        // call Bukkit.getEntity(...) on dead UUIDs.
        if (this.animator != null) {
            this.animator.stop();
            this.animator = null;
        }
        if (this.refreshTask != null) this.refreshTask.cancel();
        if (this.proximityTask != null) this.proximityTask.cancel();
        this.refreshTask = null;
        this.proximityTask = null;
        this.boards.values().forEach(this::despawnAll);
        this.save();
        this.plugin.getLogger().info("LeaderboardManager disabled.");
    }

    public void reload() {
        if (this.animator != null) this.animator.stop();
        this.boards.values().forEach(this::despawnAll);
        this.readConfig();
        this.load();
        this.sweepStrays();
        if (!this.personalRank) this.boards.values().forEach(this::spawnShared);
        this.rowCache.clear();
        this.rankCache.clear();
        this.valueCache.clear();
        this.flashFrames.clear();
        if (this.animator != null) this.animator.start();
    }

    private void readConfig() {
        this.rows = Math.max(1, Math.min(15, this.plugin.getConfig().getInt("leaderboards.rows", 10)));
        this.yOffset = this.plugin.getConfig().getDouble("leaderboards.y-offset", 2.2);
        this.personalRank = this.plugin.getConfig().getBoolean("leaderboards.personal-rank", true);
        this.viewDistance = Math.max(8.0, this.plugin.getConfig().getDouble("leaderboards.view-distance", 48.0));
        this.yawOffset = (float) this.plugin.getConfig().getDouble("leaderboards.yaw-offset", 180.0);
        String billboardName = this.plugin.getConfig().getString("leaderboards.billboard", "CENTER");
        try {
            this.billboard = Display.Billboard.valueOf(billboardName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            this.billboard = Display.Billboard.CENTER;
        }
        this.backgroundAlpha = Math.max(0, Math.min(255,
            this.plugin.getConfig().getInt("leaderboards.background-alpha", 0)));
        String colour = this.plugin.getConfig().getString("leaderboards.background-color", "#000000");
        try {
            this.backgroundColor = Color.fromRGB(Integer.parseInt(colour.replace("#", ""), 16));
        } catch (IllegalArgumentException ex) {
            this.backgroundColor = Color.BLACK;
        }
        this.title = this.plugin.getConfig().getString("leaderboards.text.title",
            "<gradient:#4FC3F7:#EAF6FF:#4FC3F7><bold>❆ TOP <type-upper> ❆</bold></gradient>");
        this.subtitle = this.plugin.getConfig().getString("leaderboards.text.subtitle",
            "<color:#9FB3C0><bold>LEADERBOARD</bold></color>");
        this.emptyRow = this.plugin.getConfig().getString("leaderboards.text.empty-row",
            "<color:#5A6B78><position> No data » ---</color>");
        this.rowFormat = this.plugin.getConfig().getString("leaderboards.text.row",
            "<color:#B3E5FC><position></color> <color:#EAF6FF><name></color> <color:#5A6B78>»</color> <color:#7FE9FF><value></color>");
        this.footer = this.plugin.getConfig().getString("leaderboards.text.footer",
            "<color:#5A6B78>#<rank> <color:#B3E5FC><bold>You</bold></color> » <color:#7FE9FF><value></color>");
        this.footerEmpty = this.plugin.getConfig().getString("leaderboards.text.footer-empty",
            "<color:#5A6B78>#--- <color:#B3E5FC><bold>You</bold></color> » <color:#9FB3C0>no data</color>");
        this.animationHeaderColor = colorTag(this.plugin.getConfig().getString(
            "leaderboards.animation-colors.header", "#4FC3F7"));
        this.animationSubtitleColor = colorTag(this.plugin.getConfig().getString(
            "leaderboards.animation-colors.subtitle", "#2E6E8E"));
        this.animationPositionColor = colorTag(this.plugin.getConfig().getString(
            "leaderboards.animation-colors.position", "#2E6E8E"));
        this.animationNameColor = colorTag(this.plugin.getConfig().getString(
            "leaderboards.animation-colors.name", "#B3E5FC"));
        this.animationValueColor = colorTag(this.plugin.getConfig().getString(
            "leaderboards.animation-colors.value", "#7FE9FF"));
    }

    private static String colorTag(String value) {
        if (value == null || value.isBlank()) return "<color:#FFFFFF>";
        String color = value.trim();
        if (color.startsWith("<")) return color;
        if (color.startsWith("&#")) color = color.substring(1);
        if (color.startsWith("&")) {
            String[] legacy = {"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f"};
            String[] hex = {"#000000","#0000AA","#00AA00","#00AAAA","#AA0000","#AA00AA","#FFAA00","#AAAAAA",
                "#555555","#5555FF","#55FF55","#55FFFF","#FF5555","#FF55FF","#FFFF55","#FFFFFF"};
            for (int i = 0; i < legacy.length; i++) {
                if (color.equalsIgnoreCase("&" + legacy[i])) return "<color:" + hex[i] + ">";
            }
        }
        if (color.matches("(?i)#[0-9a-f]{6}")) return "<color:" + color + ">";
        return "<" + color.toLowerCase(java.util.Locale.ROOT) + ">";
    }

    public String animationHeaderColor() { return animationHeaderColor; }
    public String animationSubtitleColor() { return animationSubtitleColor; }
    public String animationPositionColor() { return animationPositionColor; }
    public String animationNameColor() { return animationNameColor; }
    public String animationValueColor() { return animationValueColor; }

    public Collection<Leaderboard> boards() {
        return this.boards.values();
    }

    public double yOffset() {
        return this.yOffset;
    }

    public float yawOffset() {
        return this.yawOffset;
    }

    public int rows() {
        return this.rows;
    }

    public Leaderboard place(LeaderboardType type, Location where) {
        String id = type.id() + "-" + System.currentTimeMillis();
        Leaderboard board = new Leaderboard(id, type, where);
        this.boards.put(id, board);
        if (!this.personalRank) this.spawnShared(board);
        this.save();
        this.syncViewers();
        return board;
    }

    /** {@code /leaderboard cycle} (plan §6.1) — a board with no fixed type, stepping through {@code cycleOrder} over time. */
    public Leaderboard placeCycle(Location where) {
        List<LeaderboardType> order = this.cycleOrder();
        String id = "cycle-" + System.currentTimeMillis();
        Leaderboard board = new Leaderboard(id, order, where);
        this.boards.put(id, board);
        if (!this.personalRank) this.spawnShared(board);
        this.save();
        this.syncViewers();
        return board;
    }

    private List<LeaderboardType> cycleOrder() {
        List<String> raw = this.plugin.getConfig().getStringList("leaderboards.cycle-order");
        List<LeaderboardType> order = new ArrayList<>();
        for (String entry : raw) {
            LeaderboardType type = LeaderboardType.from(entry);
            if (type != null) order.add(type);
        }
        if (order.isEmpty()) {
            order = List.of(LeaderboardType.KILLS, LeaderboardType.DEATHS, LeaderboardType.BALANCE,
                LeaderboardType.KILLSTREAK, LeaderboardType.BOUNTY);
        }
        return order;
    }

    public Leaderboard nearest(Location origin, double radius) {
        return this.boards.values().stream()
            .filter(board -> board.location().getWorld() != null
                && board.location().getWorld().equals(origin.getWorld()))
            .filter(board -> board.location().distanceSquared(origin) <= radius * radius)
            .min(Comparator.comparingDouble(board -> board.location().distanceSquared(origin)))
            .orElse(null);
    }

    public void remove(Leaderboard board) {
        this.despawnAll(board);
        this.boards.remove(board.id());
        this.save();
    }

    private TextDisplay spawnDisplay(Leaderboard board, Component text) {
        Location loc = board.location();
        World world = loc.getWorld();
        if (world == null) return null;
        world.getChunkAt(loc).load();

        return world.spawn(loc, TextDisplay.class, spawned -> {
            // CENTER makes each personal display face the viewer, while shared displays
            // naturally turn toward the nearest/looking player through the client camera.
            spawned.setBillboard(this.billboard);
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setSeeThrough(false);
            spawned.setShadowed(true);
            spawned.setBackgroundColor(Color.fromARGB(this.backgroundAlpha,
                this.backgroundColor.getRed(), this.backgroundColor.getGreen(), this.backgroundColor.getBlue()));
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setPersistent(false);
            spawned.text(text);
            spawned.getPersistentDataContainer().set(this.boardKey, PersistentDataType.STRING, board.id());
        });
    }

    private void spawnShared(Leaderboard board) {
        TextDisplay display = this.spawnDisplay(board, this.render(board.effectiveType(), null));
        if (display == null) {
            this.plugin.getLogger().warning("Leaderboard " + board.id() + " has no world — skipped.");
            return;
        }
        board.setSharedDisplay(display.getUniqueId());
    }

    private void spawnFor(Leaderboard board, Player viewer) {
        TextDisplay display = this.spawnDisplay(board, this.render(board.effectiveType(), viewer.getUniqueId()));
        if (display == null) return;

        for (Player other : this.plugin.getServer().getOnlinePlayers()) {
            if (!other.getUniqueId().equals(viewer.getUniqueId())) {
                other.hideEntity(this.plugin, display);
            }
        }
        board.viewers().put(viewer.getUniqueId(), display.getUniqueId());
    }

    private void despawnAll(Leaderboard board) {
        if (board.sharedDisplay() != null) {
            Entity entity = Bukkit.getEntity(board.sharedDisplay());
            if (entity != null) entity.remove();
            board.setSharedDisplay(null);
        }
        for (UUID displayId : board.viewers().values()) {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null) entity.remove();
        }
        board.viewers().clear();
    }

    private void sweepStrays() {
        for (Leaderboard board : this.boards.values()) {
            Location loc = board.location();
            if (loc.getWorld() == null) continue;
            loc.getWorld().getChunkAt(loc).load();
            for (Entity entity : loc.getWorld().getNearbyEntities(loc, 4.0, 5.0, 4.0)) {
                if (entity instanceof TextDisplay
                    && entity.getPersistentDataContainer().has(this.boardKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }

    private void syncViewers() {
        if (!this.personalRank) return;

        for (Leaderboard board : this.boards.values()) {
            Location loc = board.location();
            if (loc.getWorld() == null) continue;

            for (Player player : this.plugin.getServer().getOnlinePlayers()) {
                boolean inRange = player.getWorld().equals(loc.getWorld())
                    && player.getLocation().distanceSquared(loc) <= this.viewDistance * this.viewDistance;
                boolean hasDisplay = board.viewers().containsKey(player.getUniqueId());

                if (inRange && !hasDisplay) {
                    this.spawnFor(board, player);
                } else if (!inRange && hasDisplay) {
                    this.removeViewer(board, player.getUniqueId());
                }
            }

            // Anyone who logged out still has an entry; clear those too.
            Iterator<Map.Entry<UUID, UUID>> iterator = board.viewers().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, UUID> entry = iterator.next();
                if (Bukkit.getPlayer(entry.getKey()) == null) {
                    Entity entity = Bukkit.getEntity(entry.getValue());
                    if (entity != null) entity.remove();
                    iterator.remove();
                }
            }
        }
    }

    private void removeViewer(Leaderboard board, UUID viewer) {
        UUID displayId = board.viewers().remove(viewer);
        if (displayId == null) return;
        Entity entity = Bukkit.getEntity(displayId);
        if (entity != null) entity.remove();
    }

    /**
     * The only place data is re-sorted. Fills {@link #rowCache}/{@link #rankCache}, seeds
     * {@link #flashFrames} from the cache diff, and renders the static frame. The animator
     * re-renders from the same cache between ticks.
     */
    private void refreshAll() {
        Set<LeaderboardType> activeTypes = EnumSet.noneOf(LeaderboardType.class);
        for (Leaderboard board : this.boards.values()) activeTypes.add(board.effectiveType());

        for (LeaderboardType type : activeTypes) {
            List<Row> data = this.rows(type, this.rows);
            this.updateFlashing(type, this.rowCache.get(type), data);
            this.rowCache.put(type, data);
            this.rankCache.put(type, this.rankIndex(type));
            this.valueCache.put(type, this.valueIndex(type));
        }

        for (Leaderboard board : this.boards.values()) {
            LeaderboardType type = board.effectiveType();
            List<Row> data = this.rowCache.getOrDefault(type, List.of());
            Map<UUID, Integer> ranks = this.rankCache.getOrDefault(type, Map.of());
            Map<UUID, Long> values = this.valueCache.getOrDefault(type, Map.of());

            if (board.sharedDisplay() != null) {
                this.setText(board.sharedDisplay(), renderConfigured(type, data, this.rows, null, null, this));
            }
            if (board.viewers().isEmpty()) continue;
            for (Map.Entry<UUID, UUID> entry : board.viewers().entrySet()) {
                Long ownValue = values.get(entry.getKey());
                this.setText(entry.getValue(), renderConfigured(type, data, this.rows,
                    ranks.get(entry.getKey()),
                    ownValue == null ? null : type.format(ownValue, this.plugin.getEconomyManager()), this));
            }
        }
    }

    private void updateFlashing(LeaderboardType type, List<Row> previous, List<Row> current) {
        if (previous == null) return;
        Map<Integer, Integer> frames = this.flashFrames.computeIfAbsent(type, t -> new HashMap<>());
        int limit = Math.min(previous.size(), current.size());
        for (int i = 0; i < limit; i++) {
            if (!previous.get(i).value().equals(current.get(i).value())) {
                frames.put(i, FLASH_FRAMES);
            }
        }
    }

    // Read-only accessors for LeaderboardAnimator (never re-sorts, only reads)

    public List<Row> cachedRows(LeaderboardType type) {
        return this.rowCache.getOrDefault(type, List.of());
    }

    private static final long PLACEHOLDER_TTL_MS = 15_000L;
    private final Map<LeaderboardType, List<Row>> placeholderRows = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<LeaderboardType, Long> placeholderStamp = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Top rows for {@code %frostffa_top_<type>_<n>_name/value%}. Uses the board cache when a
     * board of this type exists; otherwise sorts at most once per {@link #PLACEHOLDER_TTL_MS},
     * since a scoreboard or Skript may ask for these every tick.
     */
    public List<Row> topRows(LeaderboardType type) {
        List<Row> live = this.rowCache.get(type);
        if (live != null) return live;
        long now = System.currentTimeMillis();
        Long stamp = this.placeholderStamp.get(type);
        if (stamp == null || now - stamp > PLACEHOLDER_TTL_MS) {
            this.placeholderRows.put(type, this.rows(type, this.rows));
            this.placeholderStamp.put(type, now);
        }
        return this.placeholderRows.getOrDefault(type, List.of());
    }

    public Map<UUID, Integer> cachedRankIndex(LeaderboardType type) {
        return this.rankCache.getOrDefault(type, Map.of());
    }

    /** Raw (unformatted) score per player, for rendering a viewer's own value in the footer. */
    public Map<UUID, Long> cachedValueIndex(LeaderboardType type) {
        return this.valueCache.getOrDefault(type, Map.of());
    }

    public Set<Integer> flashingRows(LeaderboardType type) {
        Map<Integer, Integer> frames = this.flashFrames.get(type);
        return frames == null || frames.isEmpty() ? Set.of() : Set.copyOf(frames.keySet());
    }

    /** Decrements every flash-frame countdown by one animation frame; called once per frame by the animator. */
    public void decayFlashFrames() {
        for (Map<Integer, Integer> frames : this.flashFrames.values()) {
            Iterator<Map.Entry<Integer, Integer>> it = frames.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Integer> entry = it.next();
                int remaining = entry.getValue() - 1;
                if (remaining <= 0) it.remove();
                else entry.setValue(remaining);
            }
        }
    }

    public void setDisplayText(UUID displayId, Component text) {
        this.setText(displayId, text);
    }

    private void setText(UUID displayId, Component text) {
        Entity entity = Bukkit.getEntity(displayId);
        // Null means the chunk is unloaded — nobody can read it, so there's nothing to do.
        if (entity instanceof TextDisplay display) display.text(text);
    }

    private Component render(LeaderboardType type, UUID viewer) {
        Integer rank = viewer == null ? null : this.rankIndex(type).get(viewer);
        Long ownRaw = viewer == null ? null : this.valueIndex(type).get(viewer);
        String ownValue = ownRaw == null ? null : type.format(ownRaw, this.plugin.getEconomyManager());
        return renderConfigured(type, this.rows(type, this.rows), this.rows, rank, ownValue, this);
    }

    public static Component render(LeaderboardType type, List<Row> data, int slots, Integer viewerRank) {
        return renderConfigured(type, data, slots, viewerRank, null, null);
    }

    private static Component renderConfigured(LeaderboardType type, List<Row> data, int slots,
                                              Integer viewerRank, String viewerValue, LeaderboardManager manager) {
        if (manager != null) {
            StringBuilder configured = new StringBuilder(manager.title.replace("<type-upper>", type.displayName()))
                .append("\n").append(manager.subtitle.replace("<type>", type.basis()));
            for (int i = 0; i < slots; i++) {
                Row row = i < data.size() ? data.get(i) : null;
                String line = row == null ? manager.emptyRow.replace("<position>", circled(i + 1))
                    : manager.rowFormat
                        .replace("<position>", circled(i + 1))
                        .replace("<name>", row.name()).replace("<value>", row.value());
                configured.append("\n").append(line);
            }
            configured.append("\n\n").append(viewerRank == null || viewerValue == null
                ? manager.footerEmpty
                : manager.footer.replace("<rank>", String.valueOf(viewerRank)).replace("<value>", viewerValue));
            return MM.deserialize(configured.toString());
        }
        StringBuilder text = new StringBuilder();
        text.append(Palette.b(Palette.BRAND, "❆ TOP " + type.displayName() + " ❆"));
        text.append("\n").append(Palette.l(Palette.DEEP, "leaderboard"));
        text.append("\n");

        for (int i = 0; i < slots; i++) {
            Row row = i < data.size() ? data.get(i) : null;
            String position = circled(i + 1);
            text.append("\n");

            if (i == 0) {
                text.append(Palette.c(Palette.BRAND, position)).append(" ");
                text.append(row != null
                    ? "<color:" + Palette.GLACIER + "><bold>" + row.name() + "</bold></color>"
                    : Palette.c(Palette.DEEP, "---"));
            } else {
                text.append(" ").append(Palette.c(Palette.DEEP, position)).append(" ");
                text.append(row != null
                    ? Palette.c(Palette.SHARD, row.name())
                    : Palette.c(Palette.DEEP, "---"));
            }

            text.append(" ").append(Palette.c(Palette.DEEP, "»")).append(" ");
            text.append(row != null
                ? Palette.c(i == 0 ? Palette.BRAND : Palette.GLACIER, row.value())
                : Palette.c(Palette.DEEP, type.placeholder()));
        }

        if (viewerRank != null) {
            text.append("\n\n").append(Palette.l(Palette.DEEP, "your current rank "))
                .append(Palette.c(Palette.DEEP, "» "))
                .append(Palette.b(Palette.BRAND, String.valueOf(viewerRank)));
        }
        return MM.deserialize(text.toString());
    }

    public List<Row> rows(LeaderboardType type, int limit) {
        List<Row> out = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : this.sorted(type)) {
            if (out.size() >= limit) break;
            OfflinePlayer offline = this.plugin.getServer().getOfflinePlayer(entry.getKey());
            if (offline.getName() == null) continue;
            out.add(new Row(offline.getName(), type.format(entry.getValue(), this.plugin.getEconomyManager())));
        }
        return out;
    }

    private Map<UUID, Long> valueIndex(LeaderboardType type) {
        Map<UUID, Long> index = new HashMap<>();
        for (Map.Entry<UUID, Long> entry : this.sorted(type)) {
            index.put(entry.getKey(), entry.getValue());
        }
        return index;
    }

    private Map<UUID, Integer> rankIndex(LeaderboardType type) {
        Map<UUID, Integer> index = new HashMap<>();
        int position = 1;
        for (Map.Entry<UUID, Long> entry : this.sorted(type)) {
            index.put(entry.getKey(), position++);
        }
        return index;
    }

    private List<Map.Entry<UUID, Long>> sorted(LeaderboardType type) {
        Map<UUID, Long> scores = new HashMap<>();
        switch (type) {
            // Physical tokens exist in player inventories, so only online players can be ranked.
            case BALANCE -> this.plugin.getServer().getOnlinePlayers()
                .forEach(player -> scores.put(player.getUniqueId(),
                    (long) this.plugin.getEconomyManager().balance(player)));
            case KILLS -> this.plugin.getKillsManager().snapshot()
                .forEach((uuid, value) -> scores.put(uuid, value.longValue()));
            case DEATHS -> this.plugin.getKillsManager().deathsSnapshot()
                .forEach((uuid, value) -> scores.put(uuid, value.longValue()));
            case KILLSTREAK -> this.plugin.getKillstreakManager().highestSnapshot()
                .forEach((uuid, value) -> scores.put(uuid, value.longValue()));
            case BOUNTY -> this.plugin.getBountyManager().poolSnapshot().forEach(scores::put);
        }
        List<Map.Entry<UUID, Long>> ordered = new ArrayList<>(scores.entrySet());
        ordered.removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0L);
        ordered.sort(Map.Entry.<UUID, Long>comparingByValue().reversed());
        return ordered;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Every personal display already in the world belongs to somebody else.
        for (Leaderboard board : this.boards.values()) {
            for (Map.Entry<UUID, UUID> entry : board.viewers().entrySet()) {
                if (entry.getKey().equals(event.getPlayer().getUniqueId())) continue;
                Entity entity = Bukkit.getEntity(entry.getValue());
                if (entity != null) event.getPlayer().hideEntity(this.plugin, entity);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        for (Leaderboard board : this.boards.values()) {
            this.removeViewer(board, event.getPlayer().getUniqueId());
        }
    }

    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Leaderboard board : this.boards.values()) {
            String base = "boards." + board.id();
            Location loc = board.location();
            if (board.isCycle()) {
                out.set(base + ".type", "CYCLE");
                List<String> order = new ArrayList<>();
                for (LeaderboardType type : board.cycleOrder()) order.add(type.name());
                out.set(base + ".cycle-order", order);
            } else {
                out.set(base + ".type", board.type().name());
            }
            out.set(base + ".world", loc.getWorld() != null ? loc.getWorld().getName() : "");
            out.set(base + ".x", loc.getX());
            out.set(base + ".y", loc.getY());
            out.set(base + ".z", loc.getZ());
            out.set(base + ".yaw", loc.getYaw());
            out.set(base + ".pitch", loc.getPitch());
        }
        try {
            out.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Could not save leaderboards.yml: " + e.getMessage());
        }
    }

    private void load() {
        this.boards.clear();
        if (!this.file.exists()) return;

        YamlConfiguration in = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection root = in.getConfigurationSection("boards");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;

            World world = Bukkit.getWorld(section.getString("world", ""));
            if (world == null) {
                this.plugin.getLogger().warning("leaderboards.yml: skipping '" + id + "' (unknown world).");
                continue;
            }
            Location loc = new Location(world,
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));

            String typeRaw = section.getString("type", "");
            if ("CYCLE".equalsIgnoreCase(typeRaw)) {
                List<LeaderboardType> order = new ArrayList<>();
                for (String entry : section.getStringList("cycle-order")) {
                    LeaderboardType type = LeaderboardType.from(entry);
                    if (type != null) order.add(type);
                }
                if (order.isEmpty()) order = this.cycleOrder();
                this.boards.put(id, new Leaderboard(id, order, loc));
                continue;
            }

            LeaderboardType type = LeaderboardType.from(typeRaw);
            if (type == null) {
                this.plugin.getLogger().warning("leaderboards.yml: skipping '" + id + "' (unknown type).");
                continue;
            }
            this.boards.put(id, new Leaderboard(id, type, loc));
        }
    }
}
