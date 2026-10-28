package dev.notskrib.frostffacore.core.util;

import java.text.Normalizer;
import java.util.Locale;

public final class Slug {

    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final String ASCII = "abcdefghijklmnopqrstuvwxyz";

    private Slug() {}

    public static String of(String raw, String fallback) {
        if (raw == null) return fallback;

        String text = raw.replaceAll("<[^>]*>", "");

        StringBuilder folded = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int smallCap = SMALL_CAPS.indexOf(ch);
            folded.append(smallCap >= 0 ? ASCII.charAt(smallCap) : ch);
        }

        // NFKD splits "é" into "e" + a combining accent, so stripping the marks keeps the
        // letter instead of throwing the whole character away.
        String normalised = Normalizer.normalize(folded.toString(), Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT);

        String slug = normalised.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? fallback : slug;
    }

    public static String unique(String desired, java.util.function.Predicate<String> taken) {
        if (!taken.test(desired)) return desired;
        for (int suffix = 2; suffix < 10_000; suffix++) {
            String candidate = desired + "-" + suffix;
            if (!taken.test(candidate)) return candidate;
        }
        return desired + "-" + System.currentTimeMillis();
    }
}
