package dev.notskrib.frostffacore.screenshare.session;

import dev.notskrib.frostffacore.screenshare.box.BoxAllocator;
import dev.notskrib.frostffacore.screenshare.box.BoxBuilder;
import dev.notskrib.frostffacore.screenshare.config.ScreenShareConfig;
import dev.notskrib.frostffacore.screenshare.menu.ConfirmMenu;
import dev.notskrib.frostffacore.screenshare.menu.ScreenShareMenu;
import dev.notskrib.frostffacore.screenshare.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import java.time.Duration;
import java.util.BitSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns each detainment's lifecycle: allocate a cell, box and lock down the player, restore on
 * release. Entity changes run on the target's region thread (Folia); blocks go through
 * {@link BoxBuilder}.
 */
public final class SessionManager {

    private static final UUID CONSOLE_STAFF = new UUID(0L, 0L);

    private final Plugin plugin;
    private final ScreenShareConfig config;
    private final BoxAllocator allocator;
    private final BoxBuilder boxBuilder;
    private final SessionStore store;

    private final Map<UUID, ScreenShareSession> sessions = new ConcurrentHashMap<>();
    private final BitSet usedSlots = new BitSet();
    private final Collection<UUID> internalGamemodeChanges = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingRejoin = ConcurrentHashMap.newKeySet();

    public SessionManager(Plugin plugin, ScreenShareConfig config, BoxAllocator allocator,
            BoxBuilder boxBuilder, SessionStore store) {
        this.plugin = plugin;
        this.config = config;
        this.allocator = allocator;
        this.boxBuilder = boxBuilder;
        this.store = store;
    }

    public BoxAllocator allocator() {
        return allocator;
    }

    public boolean isDetained(UUID target) {
        return sessions.containsKey(target);
    }

    /** True while anyone is currently detained — used to keep the single custom room exclusive. */
    public boolean hasActiveSession() {
        return !sessions.isEmpty();
    }

    public ScreenShareSession getSession(UUID target) {
        return sessions.get(target);
    }

    public boolean isInternalGamemodeChange(UUID target) {
        return internalGamemodeChanges.contains(target);
    }

    /** Re-attach lockdown to anyone still recorded as detained after a restart. */
    public void loadPersisted() {
        pendingRejoin.addAll(store.readPendingRejoin());

        Map<UUID, ScreenShareSession> loaded = store.readAll();
        synchronized (usedSlots) {
            for (ScreenShareSession session : loaded.values()) {
                sessions.put(session.target(), session);
                usedSlots.set(session.slot());
            }
        }
        if (!loaded.isEmpty()) {
            plugin.getLogger().info("Restored " + loaded.size() + " active screen-share session(s).");
        }
        for (ScreenShareSession session : loaded.values()) {
            Player online = Bukkit.getPlayer(session.target());
            if (online != null) {
                reassert(online);
            }
        }
    }

    public boolean start(Player target, CommandSender staff) {
        UUID id = target.getUniqueId();
        if (sessions.containsKey(id)) {
            return false;
        }
        if (config.customRoomEnabled()) {
            return startInCustomRoom(target, staff);
        }
        if (!allocator.ready()) {
            plugin.getLogger().severe("Cannot start screen share: box world '" + config.boxWorld() + "' not loaded.");
            return false;
        }

        int slot = allocateSlot();
        Location origin = target.getLocation();
        ScreenShareSession session = new ScreenShareSession(
                id,
                staff instanceof Player p ? p.getUniqueId() : CONSOLE_STAFF,
                System.currentTimeMillis(),
                slot,
                LocationSnapshot.of(origin),
                target.getGameMode(),
                target.getAllowFlight(),
                target.isFlying());

        sessions.put(id, session);
        store.write(session);

        boxBuilder.build(slot, () -> {
            Location stand = allocator.standLocation(slot);
            if (stand == null) {
                return;
            }
            target.teleportAsync(stand).thenRun(() ->
                    target.getScheduler().run(plugin, task -> {
                        applyLockdown(target);
                        showTitle(target);
                        if (config.menuChest()) {
                            openMenu(target);
                        }
                    }, null));
        });
        return true;
    }

    /**
     * Custom-room variant of {@link #start}: one fixed admin-built cell at slot 0, no BoxBuilder
     * call, and the item-click GUI always opens since there are no wall buttons.
     */
    private boolean startInCustomRoom(Player target, CommandSender staff) {
        if (hasActiveSession()) {
            return false;
        }
        Location stand = config.customRoomLocation();
        if (stand == null) {
            plugin.getLogger().severe("Cannot start screen share: custom room world is not loaded"
                    + " — check screenshare.custom-room.world.");
            return false;
        }

        Location origin = target.getLocation();
        ScreenShareSession session = new ScreenShareSession(
                target.getUniqueId(),
                staff instanceof Player p ? p.getUniqueId() : CONSOLE_STAFF,
                System.currentTimeMillis(),
                0,
                LocationSnapshot.of(origin),
                target.getGameMode(),
                target.getAllowFlight(),
                target.isFlying());

        sessions.put(target.getUniqueId(), session);
        store.write(session);

        target.teleportAsync(stand).thenRun(() ->
                target.getScheduler().run(plugin, task -> {
                    applyLockdown(target);
                    showTitle(target);
                }, null));
        return true;
    }

    /** Toggle-off: restore the player and (optionally) remove the cell. */
    public boolean release(UUID target) {
        ScreenShareSession session = sessions.remove(target);
        if (session == null) {
            return false;
        }
        freeSlot(session.slot());
        store.delete(target);

        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            online.getScheduler().run(plugin, task -> {
                internalGamemodeChanges.add(target);
                online.setGameMode(session.returnGameMode());
                internalGamemodeChanges.remove(target);
                online.setAllowFlight(session.returnAllowFlight());
                online.setFlying(session.returnFlying());
                for (PotionEffect effect : config.lockdownEffects()) {
                    online.removePotionEffect(effect.getType());
                }
                online.clearTitle();
                online.closeInventory();
                Location back = session.returnLocation().toLocation();
                if (back != null) {
                    online.teleportAsync(back);
                }
            }, null);
        }

        if (!config.customRoomEnabled() && config.cleanupBox()) {
            boxBuilder.clear(session.slot());
        }
        return true;
    }

    /** End the session by running a punishment command; no state is restored. */
    public void punish(UUID target, String commandTemplate, String reason) {
        ScreenShareSession session = sessions.remove(target);
        if (session == null) {
            return;
        }
        freeSlot(session.slot());
        store.delete(target);

        pendingRejoin.add(target);
        store.writePendingRejoin(pendingRejoin);

        OfflinePlayer offline = Bukkit.getOfflinePlayer(target);
        String name = offline.getName() != null ? offline.getName() : target.toString();
        String command = commandTemplate
                .replace("%player%", name)
                .replace("%reason%", reason);

        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            online.getScheduler().run(plugin, task -> {
                online.clearTitle();
                online.closeInventory();
            }, null);
        }
        Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));

        if (!config.customRoomEnabled() && config.cleanupBox()) {
            boxBuilder.clear(session.slot());
        }
    }

    /** Put an already-detained player back in their cell (rejoin / restart). */
    public void reassert(Player target) {
        ScreenShareSession session = sessions.get(target.getUniqueId());
        if (session == null) {
            return;
        }
        if (config.customRoomEnabled()) {
            Location stand = config.customRoomLocation();
            if (stand == null) {
                return;
            }
            target.teleportAsync(stand).thenRun(() ->
                    target.getScheduler().run(plugin, task -> {
                        applyLockdown(target);
                        showTitle(target);
                    }, null));
            return;
        }
        Location stand = allocator.standLocation(session.slot());
        if (stand == null) {
            return;
        }
        boxBuilder.build(session.slot(), () ->
                target.teleportAsync(stand).thenRun(() ->
                        target.getScheduler().run(plugin, task -> {
                            applyLockdown(target);
                            showTitle(target);
                            if (config.menuChest()) {
                                openMenu(target);
                            }
                        }, null)));
    }

    /**
     * Called on join for a player who is NOT currently detained. If they were
     * banned by the plugin earlier, teleport them to the configured rejoin spot
     * and clear the pending flag. Returns true if it handled the player.
     */
    public boolean consumeRejoin(Player player) {
        UUID id = player.getUniqueId();
        if (!pendingRejoin.remove(id)) {
            return false;
        }
        store.writePendingRejoin(pendingRejoin);
        if (!config.rejoinEnabled()) {
            return true;
        }
        Location target = config.rejoinLocation();
        if (target == null) {
            plugin.getLogger().warning("rejoin.world is not loaded; cannot relocate " + player.getName() + ".");
            return true;
        }
        player.getScheduler().runDelayed(plugin, task -> {
            player.teleportAsync(target);
            String message = config.rejoinMessage();
            if (message != null && !message.isBlank()) {
                player.sendMessage(Text.legacy(config.prefix() + message));
            }
        }, null, 20L);
        return true;
    }

    /** Re-send the title and top up effects/gamemode for every online detainee. */
    public void tick() {
        for (ScreenShareSession session : sessions.values()) {
            Player online = Bukkit.getPlayer(session.target());
            if (online == null) {
                continue;
            }
            online.getScheduler().run(plugin, task -> {
                showTitle(online);
                for (PotionEffect effect : config.lockdownEffects()) {
                    if (config.allowMovement()
                            && effect.getType() == org.bukkit.potion.PotionEffectType.SLOWNESS) {
                        // Do not re-add the configured lockdown Slowness while movement
                        // is enabled; tick() runs repeatedly for active sessions.
                        online.removePotionEffect(effect.getType());
                        continue;
                    }
                    if (!online.hasPotionEffect(effect.getType())) {
                        online.addPotionEffect(effect);
                    }
                }
                if (online.getGameMode() != config.lockdownGamemode()) {
                    internalGamemodeChanges.add(session.target());
                    online.setGameMode(config.lockdownGamemode());
                    internalGamemodeChanges.remove(session.target());
                }
            }, null);
        }
    }

    /** Reserve the lowest free cell index. {@code usedSlots} is not thread-safe. */
    private int allocateSlot() {
        synchronized (usedSlots) {
            int slot = usedSlots.nextClearBit(0);
            usedSlots.set(slot);
            return slot;
        }
    }

    private void freeSlot(int slot) {
        synchronized (usedSlots) {
            usedSlots.clear(slot);
        }
    }

    /** A button/menu choice: show the confirm screen, or punish now if confirm is off. */
    public void choose(Player player, ConfirmMenu.Action action) {
        if (!isDetained(player.getUniqueId())) {
            return;
        }
        if (config.requireConfirm()) {
            player.getScheduler().run(plugin, task ->
                    player.openInventory(new ConfirmMenu(config, action).getInventory()), null);
        } else {
            applyPunishment(player.getUniqueId(), action);
        }
    }

    /** The detainee clicked "Confirm" on the confirm screen. */
    public void confirm(Player player, ConfirmMenu.Action action) {
        applyPunishment(player.getUniqueId(), action);
    }

    private void applyPunishment(UUID target, ConfirmMenu.Action action) {
        if (action == ConfirmMenu.Action.ADMIT) {
            punish(target, config.admitCommand(), config.admitReason());
        } else {
            punish(target, config.refuseCommand(), config.refuseReason());
        }
    }

    private void applyLockdown(Player target) {
        internalGamemodeChanges.add(target.getUniqueId());
        target.setGameMode(config.lockdownGamemode());
        internalGamemodeChanges.remove(target.getUniqueId());
        target.setAllowFlight(false);
        target.setFlying(false);
        for (PotionEffect effect : config.lockdownEffects()) {
            if (config.allowMovement() && effect.getType() == org.bukkit.potion.PotionEffectType.SLOWNESS) {
                target.removePotionEffect(effect.getType());
                continue;
            }
            target.addPotionEffect(effect);
        }
    }

    private void showTitle(Player target) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(config.titleFadeIn() * 50L),
                Duration.ofMillis(config.titleStay() * 50L),
                Duration.ofMillis(config.titleFadeOut() * 50L));
        target.showTitle(Title.title(
                Text.legacy(config.titleMain()),
                Text.legacy(config.titleSubtitle()),
                times));
    }

    private void openMenu(Player target) {
        target.getScheduler().run(plugin, task ->
                target.openInventory(new ScreenShareMenu(config).getInventory()), null);
    }
}
