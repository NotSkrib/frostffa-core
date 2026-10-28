package dev.notskrib.frostffacore.vanish;

import org.bukkit.GameMode;

public record VanishState(GameMode gameMode, boolean allowFlight, boolean flying) {
}
