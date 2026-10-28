package dev.notskrib.frostffacore.bounty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A live bounty on one target (plan §6.9). Contribution order is preserved (LinkedHashMap)
 * purely so bounties.yml round-trips in a stable, human-readable order.
 */
public class Bounty {

    private final UUID target;
    private final long placedAt;
    private final Map<UUID, Integer> contributors = new LinkedHashMap<>();

    public Bounty(UUID target, long placedAt) {
        this.target = target;
        this.placedAt = placedAt;
    }

    public UUID target() {
        return this.target;
    }

    public long placedAt() {
        return this.placedAt;
    }

    public Map<UUID, Integer> contributors() {
        return this.contributors;
    }

    public void addContribution(UUID contributor, int amount) {
        this.contributors.merge(contributor, amount, Integer::sum);
    }

    public int pool() {
        long total = 0L;
        for (int amount : this.contributors.values()) total += amount;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    public int contributorCount() {
        return this.contributors.size();
    }
}
