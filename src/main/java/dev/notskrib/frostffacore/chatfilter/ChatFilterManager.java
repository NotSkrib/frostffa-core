package dev.notskrib.frostffacore.chatfilter;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class ChatFilterManager implements IManager {

    public record FilterResult(boolean blocked, String matchedPattern, String rawMessage, String normalizedMessage) {}

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FrostFFACore plugin;
    private File logFile;

    private boolean enabled;
    private boolean blockMessage;
    private boolean staffAlerts;
    private boolean logToFile;
    private String notifyPlayerMessage;
    private String staffAlertFormat;

    private final Set<String> blacklistedWords = new HashSet<>();
    private final Set<String> whitelistedWords = new HashSet<>();
    private final List<Pattern> compiledRegexes = new ArrayList<>();

    public ChatFilterManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.logFile = new File(this.plugin.getDataFolder(), "chat_filter.log");
        this.reload();
        this.plugin.getLogger().info("ChatFilterManager enabled — "
            + this.blacklistedWords.size() + " word(s), "
            + this.compiledRegexes.size() + " regex(es), "
            + this.whitelistedWords.size() + " whitelisted word(s).");
    }

    @Override
    public void onDisable() {
        this.plugin.getLogger().info("ChatFilterManager disabled.");
    }

    public void reload() {
        FileConfiguration config = this.plugin.getConfig();
        this.enabled = config.getBoolean("chat-filter.enabled", true);
        this.blockMessage = config.getBoolean("chat-filter.block-message", true);
        this.staffAlerts = config.getBoolean("chat-filter.staff-alerts", true);
        this.logToFile = config.getBoolean("chat-filter.log-to-file", true);

        this.notifyPlayerMessage = config.getString("chat-filter.notify-player",
            "<color:#FF6B7A>Your message contained prohibited language and was blocked.</color>");
        this.staffAlertFormat = config.getString("chat-filter.staff-alert-format",
            "<color:#FFD166>[ChatFilter]</color> <color:#B3E5FC><player></color><color:#9FB3C0>: <message></color>");

        this.blacklistedWords.clear();
        List<String> words = config.getStringList("chat-filter.blacklisted-words");
        for (String word : words) {
            if (word != null && !word.isBlank()) {
                this.blacklistedWords.add(word.toLowerCase(Locale.ROOT).trim());
            }
        }

        this.whitelistedWords.clear();
        List<String> whitelist = config.getStringList("chat-filter.whitelisted-words");
        for (String safe : whitelist) {
            if (safe != null && !safe.isBlank()) {
                this.whitelistedWords.add(safe.toLowerCase(Locale.ROOT).trim());
            }
        }

        this.compiledRegexes.clear();
        List<String> regexes = config.getStringList("chat-filter.regex-patterns");
        for (String regex : regexes) {
            if (regex != null && !regex.isBlank()) {
                try {
                    this.compiledRegexes.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Invalid chat filter regex pattern: '" + regex + "' - " + e.getMessage());
                }
            }
        }
    }

    public boolean isEnabled() { return enabled; }
    public boolean isBlockMessage() { return blockMessage; }
    public boolean isStaffAlerts() { return staffAlerts; }
    public String getNotifyPlayerMessage() { return notifyPlayerMessage; }
    public String getStaffAlertFormat() { return staffAlertFormat; }

    /**
     * Checks a chat message against all normalization steps, blacklisted terms, regexes, and whitelists.
     */
    public FilterResult check(String rawMessage) {
        if (!this.enabled || rawMessage == null || rawMessage.isBlank()) {
            return new FilterResult(false, null, rawMessage, rawMessage);
        }

        String normalized = normalize(rawMessage);

        // Compiled regexes against the raw message.
        for (Pattern pattern : this.compiledRegexes) {
            Matcher m = pattern.matcher(rawMessage);
            if (m.find()) {
                String matched = m.group();
                if (!isWhitelisted(matched, rawMessage)) {
                    return new FilterResult(true, pattern.pattern(), rawMessage, normalized);
                }
            }
        }

        // Compiled regexes against the normalized message.
        for (Pattern pattern : this.compiledRegexes) {
            Matcher m = pattern.matcher(normalized);
            if (m.find()) {
                String matched = m.group();
                if (!isWhitelisted(matched, rawMessage)) {
                    return new FilterResult(true, pattern.pattern(), rawMessage, normalized);
                }
            }
        }

        // Token and substring matches on raw and normalized text.
        String strippedSeparators = stripSeparators(normalized);
        String collapsed = collapseDuplicates(strippedSeparators);

        for (String blacklisted : this.blacklistedWords) {
            if (containsWord(rawMessage, normalized, strippedSeparators, collapsed, blacklisted)) {
                return new FilterResult(true, blacklisted, rawMessage, normalized);
            }
        }

        return new FilterResult(false, null, rawMessage, normalized);
    }

    private boolean containsWord(String raw, String norm, String stripped, String collapsed, String target) {
        if (target.isEmpty()) return false;

        // Check standalone token match in raw & normalized text
        String[] rawTokens = raw.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+");
        for (String token : rawTokens) {
            if (token.equals(target) && !whitelistedWords.contains(token)) {
                return true;
            }
        }

        String[] normTokens = norm.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+");
        for (String token : normTokens) {
            if (token.equals(target) && !whitelistedWords.contains(token)) {
                return true;
            }
        }

        // Check if stripped or collapsed text contains target (for evasion like n.g.a, n_g_a, n g a)
        if (stripped.contains(target) || collapsed.contains(target)) {
            if (!isWhitelisted(target, raw) && !isWhitelisted(target, norm)) {
                return true;
            }
        }

        return false;
    }

    private boolean isWhitelisted(String term, String fullMessage) {
        String lowerFull = fullMessage.toLowerCase(Locale.ROOT);
        for (String safe : this.whitelistedWords) {
            if (lowerFull.contains(safe)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalizes text through:
     * 1) Zero-width & Invisible character removal
     * 2) Unicode homoglyph translation
     * 3) L33tsp34k translation
     */
    public String normalize(String input) {
        if (input == null) return "";

        // Strip zero-width and invisible characters.
        String cleaned = input.replaceAll("[\\u200B\\u200C\\u200D\\uFEFF\\u00A0]", "");

        // Map Cyrillic/Greek lookalikes.
        StringBuilder sb = new StringBuilder(cleaned.length());
        for (char c : cleaned.toCharArray()) {
            sb.append(mapHomoglyph(c));
        }
        String text = sb.toString().toLowerCase(Locale.ROOT);

        // Translate leetspeak.
        return translateL33t(text);
    }

    private char mapHomoglyph(char c) {
        return switch (c) {
            case 'а', 'ä', 'à', 'á', 'â', 'ã', 'å', 'α' -> 'a';
            case 'b', 'в', 'β' -> 'b';
            case 'c', 'с', 'ç', '¢' -> 'c';
            case 'd', 'ð' -> 'd';
            case 'e', 'е', 'ё', 'è', 'é', 'ê', 'ë', 'ε', '€' -> 'e';
            case 'g', 'г' -> 'g';
            case 'h', 'н' -> 'h';
            case 'i', 'і', 'ï', 'í', 'ì', 'î', '!' -> 'i';
            case 'k', 'к' -> 'k';
            case 'm', 'м' -> 'm';
            case 'n', 'ñ' -> 'n';
            case 'o', 'о', 'ö', 'ò', 'ó', 'ô', 'õ', 'ø' -> 'o';
            case 'p', 'р' -> 'p';
            case 'r' -> 'r';
            case 's', 'ş', '$' -> 's';
            case 't', 'т', '+' -> 't';
            case 'u', 'υ', 'ü', 'ù', 'ú', 'û' -> 'u';
            case 'w', 'ш', 'щ' -> 'w';
            case 'x', 'х' -> 'x';
            case 'y', 'у', 'ÿ', 'ý' -> 'y';
            case 'z', 'ž' -> 'z';
            default -> c;
        };
    }

    private String translateL33t(String input) {
        return input.replace("@", "a")
                    .replace("4", "a")
                    .replace("1", "i")
                    .replace("!", "i")
                    .replace("|", "i")
                    .replace("3", "e")
                    .replace("0", "o")
                    .replace("5", "s")
                    .replace("$", "s")
                    .replace("7", "t")
                    .replace("+", "t")
                    .replace("8", "b")
                    .replace("9", "g")
                    .replace("vv", "w");
    }

    private String stripSeparators(String input) {
        return input.replaceAll("[\\s\\._\\-\\~\\*\\,\\:]+", "");
    }

    private String collapseDuplicates(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        char prev = 0;
        for (char c : input.toCharArray()) {
            if (c != prev) {
                sb.append(c);
                prev = c;
            }
        }
        return sb.toString();
    }

    public void logBlocked(Player player, FilterResult result) {
        if (!this.logToFile) return;
        String line = LocalDateTime.now().format(STAMP)
            + " | BLOCKED | " + player.getName() + " (" + player.getUniqueId() + ")"
            + " | Pattern: " + result.matchedPattern()
            + " | Raw: " + result.rawMessage()
            + " | Norm: " + result.normalizedMessage();
        try {
            Files.write(this.logFile.toPath(), List.of(line), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            this.plugin.getLogger().warning("Could not write to chat_filter.log: " + e.getMessage());
        }
    }
}
