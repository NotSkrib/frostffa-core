package dev.notskrib.frostffacore.leaderboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;

/**
 * A placed board: either a fixed {@link #type()}, or a cycling board ({@code type == null})
 * that steps through {@link #cycleOrder()} via {@link #effectiveType()}. Cycle and animation
 * fields are runtime state; a reload restarts a cycling board at its first category.
 */
public class Leaderboard {

    private final String id;
    private final LeaderboardType type; // null for a cycling board — see effectiveType()
    private final List<LeaderboardType> cycleOrder; // null/empty for a fixed-type board
    private final Location location;

    private final Map<UUID, UUID> viewers = new HashMap<>();
    private UUID sharedDisplay;

    private int cycleIndex;
    private int cycleTick;
    private boolean viewerCapLogged;

    public Leaderboard(String id, LeaderboardType type, Location location) {
        this.id = id;
        this.type = type;
        this.cycleOrder = null;
        this.location = location;
    }

    public Leaderboard(String id, List<LeaderboardType> cycleOrder, Location location) {
        this.id = id;
        this.type = null;
        this.cycleOrder = List.copyOf(cycleOrder);
        this.location = location;
    }

    public String id() {
        return this.id;
    }

    /** The board's fixed type, or null for a cycling board — see {@link #effectiveType()}. */
    public LeaderboardType type() {
        return this.type;
    }

    public boolean isCycle() {
        return this.cycleOrder != null && !this.cycleOrder.isEmpty();
    }

    public List<LeaderboardType> cycleOrder() {
        return this.cycleOrder;
    }

    /** The type actually being rendered right now — the fixed type, or the current cycle category. */
    public LeaderboardType effectiveType() {
        if (!this.isCycle()) return this.type;
        return this.cycleOrder.get(this.cycleIndex % this.cycleOrder.size());
    }

    public int cycleTick() {
        return this.cycleTick;
    }

    public void incrementCycleTick() {
        this.cycleTick++;
    }

    /** Advances to the next category and resets the fade/tick counter. No-op on a fixed-type board. */
    public void advanceCycle() {
        if (!this.isCycle()) return;
        this.cycleIndex = (this.cycleIndex + 1) % this.cycleOrder.size();
        this.cycleTick = 0;
    }

    public boolean viewerCapLogged() {
        return this.viewerCapLogged;
    }

    public void setViewerCapLogged(boolean value) {
        this.viewerCapLogged = value;
    }

    public Location location() {
        return this.location.clone();
    }

    public Map<UUID, UUID> viewers() {
        return this.viewers;
    }

    public UUID sharedDisplay() {
        return this.sharedDisplay;
    }

    public void setSharedDisplay(UUID sharedDisplay) {
        this.sharedDisplay = sharedDisplay;
    }
}
