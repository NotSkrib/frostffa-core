package dev.notskrib.frostffacore.screenshare.session;

import org.bukkit.GameMode;

import java.util.UUID;

/** One active detainment: who is boxed, who boxed them, and how to put things back. */
public record ScreenShareSession(
        UUID target,
        UUID staff,
        long startedAt,
        int slot,
        LocationSnapshot returnLocation,
        GameMode returnGameMode,
        boolean returnAllowFlight,
        boolean returnFlying) {
}
