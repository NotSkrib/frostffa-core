package dev.notskrib.frostffacore.screenshare.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Small helper so config strings can use familiar {@code &}-style color codes. */
public final class Text {

    private Text() {
    }

    /** Parses a legacy {@code &}-coded string into a Component. */
    public static Component legacy(String input) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(input == null ? "" : input);
    }

    /** Same as {@link #legacy(String)} but strips the default italics used by item names/lore. */
    public static Component item(String input) {
        return legacy(input).decoration(TextDecoration.ITALIC, false);
    }
}
