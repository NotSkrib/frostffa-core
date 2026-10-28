package dev.notskrib.frostffacore.discord;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Stores the linking state independently of a Discord client.  A Discord adapter can call
 * {@link #link(String, String)} after it has authenticated the Discord user.
 */
public final class DiscordLinkManager implements IManager, Listener {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final FrostFFACore plugin;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingCode> pending = new ConcurrentHashMap<>();
    private final Map<String, PendingDiscordCode> pendingFromDiscord = new ConcurrentHashMap<>();
    private final Map<UUID, String> minecraftToDiscord = new ConcurrentHashMap<>();
    private final Map<String, UUID> discordToMinecraft = new ConcurrentHashMap<>();
    private long expiryMillis;

    public DiscordLinkManager(FrostFFACore plugin) { this.plugin = plugin; }

    @Override public void onEnable() {
        this.expiryMillis = Math.max(1, plugin.getConfig().getLong("discord.code-expiry-seconds", 600)) * 1000L;
        ConfigurationSection links = plugin.getConfigManager().getDataConfig().getConfigurationSection("discord-links");
        if (links != null) for (String key : links.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String id = links.getString(key + ".discord-id");
                if (id != null && !id.isBlank() && !discordToMinecraft.containsKey(id)) {
                    minecraftToDiscord.put(uuid, id);
                    discordToMinecraft.put(id, uuid);
                }
            } catch (IllegalArgumentException ignored) { }
        }
        plugin.getLogger().info("Discord linking storage enabled (" + minecraftToDiscord.size() + " link(s)).");
    }

    @Override public void onDisable() {
        pending.clear();
        pendingFromDiscord.clear();
        plugin.getConfigManager().saveDataConfig();
    }

    private String generateCode() {
        StringBuilder out = new StringBuilder(8);
        for (int i = 0; i < 8; i++) out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return out.toString();
    }

    /** Minecraft-side origin: player runs /link, then redeems the code in Discord. */
    public synchronized String createCode(Player player) {
        pending.entrySet().removeIf(e -> e.getValue().expiresAt < System.currentTimeMillis()
            || e.getValue().uuid.equals(player.getUniqueId()));
        String code;
        do { code = generateCode(); } while (pending.containsKey(code) || pendingFromDiscord.containsKey(code));
        pending.put(code, new PendingCode(player.getUniqueId(), System.currentTimeMillis() + expiryMillis));
        return code;
    }

    /** Discord-side origin: user clicks "Generate code", then redeems it with /link <code>. */
    public synchronized String createCodeForDiscord(String discordId) {
        pendingFromDiscord.entrySet().removeIf(e -> e.getValue().expiresAt < System.currentTimeMillis()
            || e.getValue().discordId.equals(discordId));
        String code;
        do { code = generateCode(); } while (pending.containsKey(code) || pendingFromDiscord.containsKey(code));
        pendingFromDiscord.put(code, new PendingDiscordCode(discordId, System.currentTimeMillis() + expiryMillis));
        return code;
    }

    /** Redeems a code generated in-game (via /link) using a Discord user ID. Discord-side flow. */
    public synchronized LinkResult link(String suppliedCode, String discordId) {
        if (suppliedCode == null || discordId == null) return LinkResult.INVALID;
        PendingCode request = pending.remove(suppliedCode.trim().toUpperCase());
        if (request == null || request.expiresAt < System.currentTimeMillis()) return LinkResult.EXPIRED;
        return completeLink(request.uuid, discordId);
    }

    /** Redeems a code generated in Discord (via the "Generate code" button) using a player. Minecraft-side flow. */
    public synchronized LinkResult linkFromMinecraft(String suppliedCode, UUID minecraftUuid) {
        if (suppliedCode == null || minecraftUuid == null) return LinkResult.INVALID;
        PendingDiscordCode request = pendingFromDiscord.remove(suppliedCode.trim().toUpperCase());
        if (request == null || request.expiresAt < System.currentTimeMillis()) return LinkResult.EXPIRED;
        return completeLink(minecraftUuid, request.discordId);
    }

    private LinkResult completeLink(UUID uuid, String discordId) {
        if (minecraftToDiscord.containsKey(uuid)) return LinkResult.MINECRAFT_ALREADY_LINKED;
        if (discordToMinecraft.containsKey(discordId)) return LinkResult.DISCORD_ALREADY_LINKED;
        minecraftToDiscord.put(uuid, discordId);
        discordToMinecraft.put(discordId, uuid);
        plugin.getConfigManager().getDataConfig().set("discord-links." + uuid + ".discord-id", discordId);
        plugin.getConfigManager().saveDataConfig();
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> applyVerifiedDisplay(player));
        }
        if (plugin.getDiscordIntegration() != null) {
            plugin.getDiscordIntegration().grantVerifiedRoleAsync(discordId);
        }
        return LinkResult.SUCCESS;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isLinked(event.getPlayer().getUniqueId())) applyVerifiedDisplay(event.getPlayer());
    }

    private void applyVerifiedDisplay(Player player) {
        String prefix = plugin.getConfig().getString("discord.verified-display-prefix", "✓ ");
        if (prefix == null) prefix = "✓ ";
        // Display-only fallback; it never changes permissions or groups.
        player.setDisplayName(prefix + player.getName());
        player.setPlayerListName(prefix + player.getName());
    }

    public UUID resolveDiscord(String value) {
        if (value == null) return null;
        String id = value.replaceAll("[<@!>]", "").trim();
        return discordToMinecraft.get(id);
    }
    public boolean isLinked(UUID uuid) { return minecraftToDiscord.containsKey(uuid); }
    public String discordId(UUID uuid) { return minecraftToDiscord.get(uuid); }
    public long expirySeconds() { return expiryMillis / 1000L; }

    public enum LinkResult { SUCCESS, INVALID, EXPIRED, MINECRAFT_ALREADY_LINKED, DISCORD_ALREADY_LINKED }
    private record PendingCode(UUID uuid, long expiresAt) {}
    private record PendingDiscordCode(String discordId, long expiresAt) {}
}
