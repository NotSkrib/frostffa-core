package dev.notskrib.frostffacore.economy;

import dev.notskrib.frostffacore.core.Palette;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Whole-integer amount parsing and formatting for the PlayerPoints economy. Accepts k/m/b
 * suffixes and thousands separators. Currency names come from PlayerPoints at render time,
 * never hardcoded.
 */
public final class Points {

    private static final DecimalFormat WHOLE;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        WHOLE = new DecimalFormat("#,##0", symbols);
    }

    private Points() {}

    /** Parses "500", "2.5k", "1m" etc into a whole amount. Returns -1 for invalid or non-positive input. */
    public static int parse(String raw) {
        long value = parseLong(raw);
        if (value <= 0L) return -1;
        return clamp(value);
    }

    /** Same as {@link #parse}, but accepts an explicit zero (used by "set the payout to 0" flows). */
    public static int parseAllowingZero(String raw) {
        if (raw == null) return -1;
        String cleaned = raw.trim().replace("$", "").replace(",", "").replace("_", "");
        if (cleaned.matches("0+(\\.0+)?")) return 0;
        return parse(raw);
    }

    private static long parseLong(String raw) {
        if (raw == null) return -1L;
        String s = raw.trim().toLowerCase(Locale.ROOT).replace("$", "").replace(",", "").replace("_", "");
        if (s.isEmpty()) return -1L;

        long multiplier = 1L;
        char suffix = s.charAt(s.length() - 1);
        if (suffix == 'k' || suffix == 'm' || suffix == 'b') {
            multiplier = switch (suffix) {
                case 'k' -> 1_000L;
                case 'm' -> 1_000_000L;
                default -> 1_000_000_000L;
            };
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) return -1L;

        try {
            // BigDecimal, not Double.parseDouble — "2.5k" must not become 2499.999...
            BigDecimal value = new BigDecimal(s)
                .multiply(BigDecimal.valueOf(multiplier))
                // Fractional points don't exist — anything finer than a whole point is
                // discarded rather than rounded up.
                .setScale(0, RoundingMode.DOWN);
            if (value.signum() < 0) return -1L;
            if (value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) return Integer.MAX_VALUE;
            return value.longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return -1L;
        }
    }

    private static int clamp(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    public static String format(int amount) {
        return WHOLE.format(amount);
    }

    /** e.g. "1,234 Points" / "1 Point" — the currency name is read fresh from PlayerPoints. */
    public static String format(int amount, EconomyManager economy) {
        String name = amount == 1 ? economy.currencyNameSingular() : economy.currencyNamePlural();
        return format(amount) + " " + name;
    }

    public static String colored(int amount, EconomyManager economy) {
        return "<color:" + Palette.SHARD + ">" + format(amount, economy) + "</color>";
    }

    /** Same as {@link #format(int, EconomyManager)}, but for a currency that isn't PlayerPoints. */
    public static String format(int amount, String currencyName) {
        return format(amount) + " " + currencyName;
    }

    public static String colored(int amount, String currencyName) {
        return "<color:" + Palette.SHARD + ">" + format(amount, currencyName) + "</color>";
    }
}
