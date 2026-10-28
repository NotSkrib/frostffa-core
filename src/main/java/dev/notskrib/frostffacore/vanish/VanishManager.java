package dev.notskrib.frostffacore.vanish;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VanishManager implements IManager {

    public static final String SEE_PERMISSION = "frostffa.vanish.see";
    public static final String USE_PERMISSION = "frostffa.vanish.use";

    private final FrostFFACore plugin;
    private final Map<UUID, VanishState> vanished = new ConcurrentHashMap<>();

    public VanishManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        for (UUID uuid : vanished.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                unvanish(player);
            }
        }
        vanished.clear();
    }

    public boolean isVanished(UUID uuid) {
        return vanished.containsKey(uuid);
    }

    public Set<UUID> vanishedIds() {
        return Set.copyOf(vanished.keySet());
    }

    public boolean toggle(Player player) {
        return isVanished(player.getUniqueId()) ? unvanish(player) : vanish(player);
    }

    public boolean vanish(Player player) {
        if (isVanished(player.getUniqueId())) {
            return true;
        }
        vanished.put(player.getUniqueId(), new VanishState(player.getGameMode(), player.getAllowFlight(), player.isFlying()));
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId()) && !online.hasPermission(SEE_PERMISSION)) {
                online.hidePlayer(plugin, player);
            }
        }
        return true;
    }

    public boolean unvanish(Player player) {
        VanishState state = vanished.remove(player.getUniqueId());
        if (state != null) {
            player.setAllowFlight(state.allowFlight());
            player.setFlying(state.flying());
        }
        player.setCollidable(true);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, player);
        }
        return false;
    }

    public void forget(UUID uuid) {
        vanished.remove(uuid);
    }
}
