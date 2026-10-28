package dev.notskrib.frostffacore.core.util;

import org.bukkit.entity.Player;

/** Same-connection detection for {@code bounty.block-same-ip}, to stop alts funding or dodging a bounty. */
public final class NetworkUtil {

    private NetworkUtil() {}

    public static boolean sameConnection(Player a, Player b) {
        if (a == null || b == null) return false;
        if (a.getAddress() == null || b.getAddress() == null) return false;
        var addressA = a.getAddress().getAddress();
        var addressB = b.getAddress().getAddress();
        return addressA != null && addressA.equals(addressB);
    }
}
