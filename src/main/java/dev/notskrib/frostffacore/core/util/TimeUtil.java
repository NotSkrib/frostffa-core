package dev.notskrib.frostffacore.core.util;

public final class TimeUtil {

    private TimeUtil() {}

    public static String shortDuration(long millis) {
        if (millis <= 0) return "0s";
        long seconds = millis / 1000L;
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long secs = seconds % 60L;

        if (days > 0) return hours > 0 ? days + "d " + hours + "h" : days + "d";
        if (hours > 0) return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        if (minutes > 0) return secs > 0 ? minutes + "m " + secs + "s" : minutes + "m";
        return secs + "s";
    }

    public static String clock(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        return String.format("%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }
}
