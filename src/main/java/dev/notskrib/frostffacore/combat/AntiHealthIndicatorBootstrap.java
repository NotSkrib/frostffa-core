package dev.notskrib.frostffacore.combat;

import dev.notskrib.frostffacore.FrostFFACore;

/**
 * Keeps PacketEvents types out of {@link FrostFFACore}'s bytecode: the JVM verifies a whole
 * method body when its class loads, so referencing the listener in onEnable() would throw
 * NoClassDefFoundError on servers without PacketEvents. This class loads only once
 * {@link #register} is called.
 */
public final class AntiHealthIndicatorBootstrap {

    private AntiHealthIndicatorBootstrap() {}

    /** Caller must already have confirmed the "packetevents" plugin is enabled. */
    public static void register(FrostFFACore plugin) {
        AntiHealthIndicatorListener listener = new AntiHealthIndicatorListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager()
            .registerListener(listener);
    }
}
