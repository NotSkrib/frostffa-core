package dev.notskrib.frostffacore.leaderboard;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cosmetic animation (plan §6.1) on a faster task than {@link LeaderboardManager}'s sort timer:
 * category cycling with a fade, a header shimmer, a glint on the #1 name, and a flash on
 * changed rows. It only re-renders {@link LeaderboardManager#cachedRows} and never re-sorts.
 * The manager starts it in {@code onEnable} and stops it before its own tasks in {@code onDisable}.
 */
final class LeaderboardAnimator {

    private enum Fade { NONE, IN, OUT }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int SHIMMER_WINDOW = 3;
    private static final int CYCLE_FADE_FRAMES = 6;

    private final FrostFFACore plugin;
    private final LeaderboardManager manager;

    private BukkitTask task;
    private int phase;

    private boolean animated;
    private int animationTicks;
    private int cycleSeconds;
    private int maxViewers;

    LeaderboardAnimator(FrostFFACore plugin, LeaderboardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    void start() {
        this.readConfig();
        // The kill switch: when off, this task never runs and LeaderboardManager's own
        // slow refreshAll() render stays the ONLY renderer — the exact Phase 1 behaviour,
        // with zero errors, since nothing here ever executes.
        if (!this.animated) return;
        this.task = this.plugin.getServer().getScheduler()
            .runTaskTimer(this.plugin, this::tick, this.animationTicks, this.animationTicks);
    }

    void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    private void readConfig() {
        this.animated = this.plugin.getConfig().getBoolean("leaderboards.animated", true);
        this.animationTicks = Math.max(1, this.plugin.getConfig().getInt("leaderboards.animation-ticks", 4));
        this.cycleSeconds = Math.max(1, this.plugin.getConfig().getInt("leaderboards.cycle-seconds", 10));
        this.maxViewers = Math.max(1, this.plugin.getConfig().getInt("leaderboards.animation-max-viewers", 20));
    }

    private void tick() {
        this.phase++;
        this.manager.decayFlashFrames();
        for (Leaderboard board : this.manager.boards()) {
            this.animateBoard(board);
        }
    }

    private void animateBoard(Leaderboard board) {
        if (board.isCycle()) this.advanceCycleIfDue(board);

        int viewerCount = board.viewers().size();
        if (viewerCount > this.maxViewers) {
            if (!board.viewerCapLogged()) {
                board.setViewerCapLogged(true);
                this.plugin.getLogger().info("Leaderboard '" + board.id() + "' has " + viewerCount
                    + " personal viewers (cap " + this.maxViewers + ") — freezing its animation until it thins out.");
            }
            return; // Static render from the last refreshAll() tick stays on screen.
        }
        board.setViewerCapLogged(false);

        LeaderboardType type = board.effectiveType();
        List<LeaderboardManager.Row> rows = this.manager.cachedRows(type);
        Set<Integer> flashing = this.manager.flashingRows(type);
        Fade fade = this.fadeStateOf(board);

        if (board.sharedDisplay() != null) {
            this.manager.setDisplayText(board.sharedDisplay(),
                this.renderFrame(type, rows, flashing, fade, null, null));
        }
        if (board.viewers().isEmpty()) return;

        Map<UUID, Integer> ranks = this.manager.cachedRankIndex(type);
        Map<UUID, Long> values = this.manager.cachedValueIndex(type);
        for (Map.Entry<UUID, UUID> entry : board.viewers().entrySet()) {
            Long ownRaw = values.get(entry.getKey());
            String ownValue = ownRaw == null ? null
                : type.format(ownRaw, this.plugin.getEconomyManager());
            this.manager.setDisplayText(entry.getValue(),
                this.renderFrame(type, rows, flashing, fade, ranks.get(entry.getKey()), ownValue));
        }
    }

    // Category cycling + fade

    private int framesPerCategory() {
        int framesPerSecond = Math.max(1, 20 / this.animationTicks);
        return Math.max(1, this.cycleSeconds * framesPerSecond);
    }

    private void advanceCycleIfDue(Leaderboard board) {
        board.incrementCycleTick();
        if (board.cycleTick() >= this.framesPerCategory()) {
            board.advanceCycle();
        }
    }

    private Fade fadeStateOf(Leaderboard board) {
        if (!board.isCycle()) return Fade.NONE;
        int framesPerCategory = this.framesPerCategory();
        int fadeWindow = Math.min(CYCLE_FADE_FRAMES, framesPerCategory / 2);
        if (fadeWindow <= 0) return Fade.NONE;

        int tick = board.cycleTick();
        if (tick < fadeWindow) return Fade.IN;
        if (tick >= framesPerCategory - fadeWindow) return Fade.OUT;
        return Fade.NONE;
    }

    // Rendering

    private Component renderFrame(LeaderboardType type, List<LeaderboardManager.Row> rows,
                                  Set<Integer> flashing, Fade fade, Integer viewerRank, String viewerValue) {
        String header = "❆ TOP " + type.displayName() + " ❆";
        StringBuilder text = new StringBuilder();

        if (fade != Fade.NONE) {
            text.append(this.manager.animationHeaderColor()).append("<bold>").append(header).append("</bold></color>");
            text.append("\n").append(this.manager.animationSubtitleColor())
                .append("leaderboard</color>\n");
            for (int i = 0; i < this.manager.rows(); i++) {
                LeaderboardManager.Row row = i < rows.size() ? rows.get(i) : null;
                text.append("\n").append(this.manager.animationPositionColor())
                    .append(LeaderboardManager.circled(i + 1)).append(" ")
                    .append(row != null ? row.name() + " » " + row.value() : "---").append("</color>");
            }
            return MM.deserialize(text.toString());
        }

        text.append(this.shimmer(header));
        text.append("\n").append(this.manager.animationSubtitleColor()).append("leaderboard</color>");
        text.append("\n");

        for (int i = 0; i < this.manager.rows(); i++) {
            LeaderboardManager.Row row = i < rows.size() ? rows.get(i) : null;
            String position = LeaderboardManager.circled(i + 1);
            boolean flashed = row != null && flashing.contains(i);
            text.append("\n");

            if (i == 0) {
                // Rank-1 pulse: the top player's name colour glints between frames.
                text.append(this.manager.animationPositionColor()).append(position).append("</color> ");
                text.append(row != null
                    ? this.manager.animationNameColor() + "<bold>" + row.name() + "</bold></color>"
                    : Palette.c(Palette.DEEP, "---"));
            } else {
                text.append(" ").append(this.manager.animationPositionColor()).append(position).append("</color> ");
                text.append(row != null ? this.manager.animationNameColor() + row.name() + "</color>"
                    : Palette.c(Palette.DEEP, "---"));
            }

            text.append(" ").append(Palette.c(Palette.DEEP, "»")).append(" ");
            String valueColor = flashed ? "<color:#FFD166>" : this.manager.animationValueColor();
            text.append(row != null
                ? valueColor + row.value() + "</color>"
                : Palette.c(Palette.DEEP, type.placeholder()));
        }

        text.append("\n\n").append(Palette.c(Palette.DIM, "#"))
            .append(Palette.b(Palette.DIM, viewerRank == null ? "---" : String.valueOf(viewerRank)))
            .append(" ")            .append(this.manager.animationNameColor()).append("<bold>You</bold></color>").append(" ")
            .append(Palette.c(Palette.DIM, "»")).append(" ")
            .append(viewerValue == null
                ? Palette.c(Palette.INK, "no data")
                : this.manager.animationValueColor() + viewerValue + "</color>");
        return MM.deserialize(text.toString());
    }

    /** A 3-character SNOW window sweeps left-to-right through the header, forever. */
    private String shimmer(String plain) {
        int len = plain.length();
        int start = this.phase % (len + 6);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < len; i++) {
            boolean hot = i >= start && i < start + SHIMMER_WINDOW;
            out.append(hot ? "<color:" + Palette.SNOW + ">" : this.manager.animationHeaderColor())
                .append(plain.charAt(i)).append("</color>");
        }
        return "<bold>" + out + "</bold>";
    }
}
