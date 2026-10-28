package dev.notskrib.frostffacore.discord;

import java.util.concurrent.CompletableFuture;
import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.manager.IManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

/**
 * Optional JDA client. No token is required for the plugin to load; it starts only when the
 * deployment explicitly enables it and replaces the local placeholder token.
 */
public final class DiscordIntegration implements IManager {
    private static final String LINK_BUTTON = "frostffa:link";
    private static final String LINK_MODAL = "frostffa:link-modal";
    private static final String CODE_INPUT = "minecraft-code";
    private static final String GENERATE_BUTTON = "frostffa:link-generate";
    private final FrostFFACore plugin;
    private volatile JDA jda;

    public DiscordIntegration(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) {
            plugin.getLogger().info("Discord bot disabled by configuration.");
            return;
        }
        String token = plugin.getConfig().getString("discord.token", "");
        if (token == null || token.isBlank() || token.contains("PUT_DISCORD_BOT_TOKEN_HERE")) {
            plugin.getLogger().warning("Discord bot enabled but discord.token is still a placeholder.");
            return;
        }
        try {
            jda = JDABuilder.createDefault(token).addEventListeners(new Events()).build();
            CompletableFuture.runAsync(() -> {
                try {
                    jda.awaitReady();
                    postLinkButton();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Discord bot could not finish startup: " + e.getMessage());
                }
            });
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Discord bot could not start: " + e.getMessage());
            jda = null;
        }
    }

    /** Posts to the channel configured at discord.channel-id — kept for the /linkpanelpost Minecraft command. */
    public boolean postLinkButton() {
        if (jda == null) return false;
        String channelId = plugin.getConfig().getString("discord.channel-id", "");
        if (channelId == null || channelId.isBlank()) {
            plugin.getLogger().warning("Discord bot is online but discord.channel-id is not configured.");
            return false;
        }
        var channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            plugin.getLogger().warning("Configured Discord channel does not exist or is inaccessible.");
            return false;
        }
        postPanelInto(channel);
        return true;
    }

    /** Posts wherever the caller already has a channel handle — the {@code !linkpanel} command's path. */
    private void postPanelInto(net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
        channel
            .sendMessage("Link your Minecraft account: generate a code here and enter it in-game with "
                + "`/link <code>`, or run `/link` in-game first and enter that code here.")
            .setActionRow(
                Button.primary(GENERATE_BUTTON, "Generate a code"),
                Button.secondary(LINK_BUTTON, "I have a code"))
            .queue();
    }

    @Override
    public void onDisable() {
        JDA client = jda;
        jda = null;
        if (client != null) client.shutdownNow();
    }

    private final class Events extends ListenerAdapter {
        @Override
        public void onButtonInteraction(ButtonInteractionEvent event) {
            if (GENERATE_BUTTON.equals(event.getComponentId())) {
                String code = plugin.getDiscordLinkManager().createCodeForDiscord(event.getUser().getId());
                long minutes = Math.max(1, plugin.getDiscordLinkManager().expirySeconds() / 60);
                event.reply("Your one-time link code: `" + code + "`\nIn Minecraft, run `/link " + code
                    + "` within " + minutes + " minute(s).").setEphemeral(true).queue();
                return;
            }
            if (!LINK_BUTTON.equals(event.getComponentId())) return;
            TextInput code = TextInput.create(CODE_INPUT, "Minecraft link code", TextInputStyle.SHORT)
                .setPlaceholder("The code shown by /link")
                .setRequired(true)
                .setMinLength(4)
                .setMaxLength(32)
                .build();
            event.replyModal(Modal.create(LINK_MODAL, "Link Minecraft account")
                .addActionRow(code)
                .build()).queue();
        }

        @Override
        public void onModalInteraction(ModalInteractionEvent event) {
            if (!LINK_MODAL.equals(event.getModalId())) return;
            String guildId = plugin.getConfig().getString("discord.guild-id", "");
            if (guildId != null && !guildId.isBlank()
                && (event.getGuild() == null || !guildId.equals(event.getGuild().getId()))) {
                event.reply("Please use the configured server's link button.")
                    .setEphemeral(true).queue();
                return;
            }
            String code = event.getValue(CODE_INPUT) == null ? "" : event.getValue(CODE_INPUT).getAsString();
            DiscordLinkManager.LinkResult result =
                plugin.getDiscordLinkManager().link(code, event.getUser().getId());
            if (result == DiscordLinkManager.LinkResult.SUCCESS) {
                assignVerifiedRole(event);
                event.reply("Your Minecraft account is now linked.").setEphemeral(true).queue();
            } else {
                event.reply(linkError(result)).setEphemeral(true).queue();
            }
        }

        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (event.getAuthor().isBot()) return;

            String prefix = plugin.getConfig().getString("discord.bot-command-prefix", "!");
            String content = event.getMessage().getContentRaw();
            boolean staff = hasStaffRole(event.getGuild(), event.getMember());

            // Posts the link panel right here, in whatever channel this was typed in — no
            // discord.channel-id needed. Staff-gated the same way !stats already is: this
            // starts the account-linking flow for anyone who clicks it, so it's not something
            // to leave open to every member.
            if (content != null && content.startsWith(prefix + "linkpanel")) {
                if (!staff) {
                    event.getChannel().sendMessage("You do not have permission to use that command.").queue();
                    return;
                }
                postPanelInto(event.getChannel());
                return;
            }

            String response = new DiscordCommandHandler(plugin).handle(content, event.getAuthor().getId(), staff);
            if (response != null) event.getChannel().sendMessage(response).queue();
        }
    }

    private void assignVerifiedRole(ModalInteractionEvent event) {
        String roleId = plugin.getConfig().getString("discord.verified-role-id", "");
        if (roleId == null || roleId.isBlank() || event.getGuild() == null) return;
        Role role = event.getGuild().getRoleById(roleId);
        if (role != null && event.getMember() != null) event.getGuild().addRoleToMember(event.getMember(), role).queue();
    }

    /**
     * Same verified-role grant as {@link #assignVerifiedRole}, for the Minecraft-initiated link
     * flow: the guild is looked up by discord.guild-id and the member fetched by ID. Best-effort
     * and cosmetic, so it only logs when the bot is offline or ids are missing or unresolved.
     */
    public void grantVerifiedRoleAsync(String discordId) {
        JDA client = jda;
        if (client == null || discordId == null) return;
        String guildId = plugin.getConfig().getString("discord.guild-id", "");
        String roleId = plugin.getConfig().getString("discord.verified-role-id", "");
        if (guildId == null || guildId.isBlank() || roleId == null || roleId.isBlank()) return;
        Guild guild = client.getGuildById(guildId);
        if (guild == null) return;
        Role role = guild.getRoleById(roleId);
        if (role == null) return;
        guild.retrieveMemberById(discordId).queue(
            member -> guild.addRoleToMember(member, role).queue(),
            failure -> plugin.getLogger().info(
                "Could not grant the verified Discord role (member not in guild or bot lacks permission): "
                    + failure.getMessage()));
    }

    private boolean hasStaffRole(Guild guild, net.dv8tion.jda.api.entities.Member member) {
        if (guild == null || member == null) return false;
        String roleId = plugin.getConfig().getString("discord.staff-role-id", "");
        return roleId != null && !roleId.isBlank() && member.getRoles().stream().anyMatch(r -> r.getId().equals(roleId));
    }

    private String linkError(DiscordLinkManager.LinkResult result) {
        return switch (result) {
            case EXPIRED -> "That code has expired. Run /link in Minecraft again.";
            case MINECRAFT_ALREADY_LINKED -> "That Minecraft account is already linked.";
            case DISCORD_ALREADY_LINKED -> "This Discord account is already linked.";
            default -> "That link code is invalid. Check it and try again.";
        };
    }
}
