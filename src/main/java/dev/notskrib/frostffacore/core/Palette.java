package dev.notskrib.frostffacore.core;

public final class Palette {

    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String[] SMALL_CAPS = {
        "ᴀ", "ʙ", "ᴄ", "ᴅ", "ᴇ", "ꜰ", "ɢ", "ʜ", "ɪ", "ᴊ", "ᴋ", "ʟ", "ᴍ",
        "ɴ", "ᴏ", "ᴘ", "ǫ", "ʀ", "ꜱ", "ᴛ", "ᴜ", "ᴠ", "ᴡ", "x", "ʏ", "ᴢ"
    };

    // Brand palette (Frost FFA)
    public static final String BRAND   = "#4FC3F7";  // frost blue  — headers, prefix
    public static final String GLACIER = "#B3E5FC";  // pale ice    — was MINT
    public static final String SNOW    = "#EAF6FF";  // near-white  — emphasis, #1 rows
    public static final String SHARD   = "#7FE9FF";  // currency    — was CASH
    public static final String INK     = "#9FB3C0";  // cold grey body text
    public static final String DIM     = "#5A6B78";
    public static final String DEEP    = "#2E6E8E";  // was MOSS
    public static final String WARN    = "#FFD166";
    public static final String BAD     = "#FF6B7A";

    private Palette() {}

    public static String sc(String text) {
        StringBuilder out = new StringBuilder(text.length());
        char closing = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (closing == 0 && (ch == '<' || ch == '{')) {
                closing = ch == '<' ? '>' : '}';
            }
            if (closing != 0) {
                out.append(ch);
                if (ch == closing) closing = 0;
                continue;
            }
            int index = LOWER.indexOf(Character.toLowerCase(ch));
            out.append(index >= 0 ? SMALL_CAPS[index] : ch);
        }
        return out.toString();
    }

    public static String c(String hex, String text) {
        return "<color:" + hex + ">" + text + "</color>";
    }

    public static String l(String hex, String text) {
        return "<color:" + hex + ">" + sc(text) + "</color>";
    }

    public static String b(String hex, String text) {
        return "<color:" + hex + "><bold>" + sc(text) + "</bold></color>";
    }

    // Prebuilt fragments
    // Last, because these call the helpers above during class initialisation.

    public static final String PREFIX = b(BRAND, "frost") + " <dark_gray>›</dark_gray> ";
    public static final String ARROW = "<dark_gray>›</dark_gray>";
}
