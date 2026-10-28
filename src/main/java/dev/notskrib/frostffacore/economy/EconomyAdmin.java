package dev.notskrib.frostffacore.economy;

import dev.notskrib.frostffacore.FrostFFACore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Resolves an online or offline player by name; used by /bounty. */
public final class EconomyAdmin {

    private EconomyAdmin() {}

    public static OfflinePlayer resolve(FrostFFACore plugin, String name) {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer offline = plugin.getServer().getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    public static List<String> onlineNames(FrostFFACore plugin, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(lower)) names.add(online.getName());
        }
        return names;
    }
}
