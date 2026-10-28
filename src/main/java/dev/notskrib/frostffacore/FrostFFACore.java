package dev.notskrib.frostffacore;

import dev.notskrib.frostffacore.afk.AfkCommand;
import dev.notskrib.frostffacore.afk.AfkListener;
import dev.notskrib.frostffacore.afk.AfkManager;
import dev.notskrib.frostffacore.arena.BlockLedger;
import dev.notskrib.frostffacore.arena.BlockPlaceListener;
import dev.notskrib.frostffacore.arena.BuildGate;
import dev.notskrib.frostffacore.arena.MaceBlockListener;
import dev.notskrib.frostffacore.arena.SpawnBowListener;
import dev.notskrib.frostffacore.bounty.BountyCommand;
import dev.notskrib.frostffacore.bounty.BountyManager;
import dev.notskrib.frostffacore.combat.CombatListener;
import dev.notskrib.frostffacore.combat.CombatManager;
import dev.notskrib.frostffacore.combat.AntiHealthIndicatorBootstrap;
import dev.notskrib.frostffacore.chatfilter.ChatFilterCommand;
import dev.notskrib.frostffacore.chatfilter.ChatFilterListener;
import dev.notskrib.frostffacore.chatfilter.ChatFilterManager;
import dev.notskrib.frostffacore.clearlag.ClearLagManager;
import dev.notskrib.frostffacore.commands.EnderChestCommand;
import dev.notskrib.frostffacore.commands.FrostFFACommand;
import dev.notskrib.frostffacore.commands.KitLayoutEditor;
import dev.notskrib.frostffacore.commands.StatsCommand;
import dev.notskrib.frostffacore.commands.AnnounceCommand;
import dev.notskrib.frostffacore.core.NpcSoundListener;
import dev.notskrib.frostffacore.core.manager.ConfigManager;
import dev.notskrib.frostffacore.core.manager.IManager;
import dev.notskrib.frostffacore.core.util.ChatInputManager;
import dev.notskrib.frostffacore.daily.DailyCommand;
import dev.notskrib.frostffacore.daily.DailyManager;
import dev.notskrib.frostffacore.discord.DiscordIntegration;
import dev.notskrib.frostffacore.discord.DiscordLinkManager;
import dev.notskrib.frostffacore.discord.LinkCommand;
import dev.notskrib.frostffacore.discord.LinkPanelPostCommand;
import dev.notskrib.frostffacore.economy.EconomyManager;
import dev.notskrib.frostffacore.economy.KillTokenCommand;
import dev.notskrib.frostffacore.economy.KillRewardManager;
import dev.notskrib.frostffacore.gui.GuiListener;
import dev.notskrib.frostffacore.join.WelcomeManager;
import dev.notskrib.frostffacore.juggernaut.JuggernautCommand;
import dev.notskrib.frostffacore.juggernaut.JuggernautManager;
import dev.notskrib.frostffacore.kills.KillListener;
import dev.notskrib.frostffacore.kills.KillMessageManager;
import dev.notskrib.frostffacore.kills.KillsManager;
import dev.notskrib.frostffacore.kills.KillstreakManager;
import dev.notskrib.frostffacore.kits.KitNpcListener;
import dev.notskrib.frostffacore.kits.KitManager;
import dev.notskrib.frostffacore.kits.KitSelectionGui;
import dev.notskrib.frostffacore.kits.KitAdminCommand;
import dev.notskrib.frostffacore.leaderboard.LeaderboardCommand;
import dev.notskrib.frostffacore.leaderboard.LeaderboardManager;
import dev.notskrib.frostffacore.maintenance.MaintenanceCommand;
import dev.notskrib.frostffacore.maintenance.MaintenanceListener;
import dev.notskrib.frostffacore.maintenance.MaintenanceManager;
import dev.notskrib.frostffacore.placeholders.FrostPlaceholders;
import dev.notskrib.frostffacore.rank.RankCommand;
import dev.notskrib.frostffacore.rank.RankManager;
import dev.notskrib.frostffacore.rules.RulesCommand;
import dev.notskrib.frostffacore.rules.RulesManager;
import dev.notskrib.frostffacore.screenshare.box.BoxAllocator;
import dev.notskrib.frostffacore.screenshare.box.BoxBuilder;
import dev.notskrib.frostffacore.screenshare.command.SsCommand;
import dev.notskrib.frostffacore.screenshare.config.ScreenShareConfig;
import dev.notskrib.frostffacore.screenshare.listener.LockdownListener;
import dev.notskrib.frostffacore.screenshare.menu.MenuListener;
import dev.notskrib.frostffacore.screenshare.session.SessionManager;
import dev.notskrib.frostffacore.screenshare.session.SessionStore;
import dev.notskrib.frostffacore.settings.SettingsCommand;
import dev.notskrib.frostffacore.settings.SettingsManager;
import dev.notskrib.frostffacore.shop.ShopCommand;
import dev.notskrib.frostffacore.shop.ShopManager;
import dev.notskrib.frostffacore.trash.TrashCommand;
import dev.notskrib.frostffacore.trash.TrashManager;
import dev.notskrib.frostffacore.vanish.VanishCommand;
import dev.notskrib.frostffacore.vanish.VanishListener;
import dev.notskrib.frostffacore.vanish.VanishManager;
import dev.notskrib.frostffacore.spectate.SpectateCommand;
import dev.notskrib.frostffacore.spectate.SpectateManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * FrostFFACore: arena core, economy, daily/bounty/rank, AFK and leaderboards. Manager enable and
 * reverse-disable order follows plan §7.2/§7.3. AxTrade stays the trade provider; ender-chest
 * commands are native.
 */
public final class FrostFFACore extends JavaPlugin {

    private static FrostFFACore instance;

    private final List<IManager> managers = new ArrayList<>();

    private ConfigManager configManager;
    private SettingsManager settingsManager;
    private EconomyManager economyManager;
    private ShopManager shopManager;
    private KillsManager killsManager;
    private KillstreakManager killstreakManager;
    private RankManager rankManager;
    private CombatManager combatManager;
    private AfkManager afkManager;
    private KillMessageManager killMessageManager;
    private RulesManager rulesManager;
    private WelcomeManager welcomeManager;
    private TrashManager trashManager;
    private BlockLedger blockLedger;
    private DailyManager dailyManager;
    private BountyManager bountyManager;
    private LeaderboardManager leaderboardManager;
    private KillRewardManager killRewardManager;
    private JuggernautManager juggernautManager;
    private DiscordLinkManager discordLinkManager;
    private DiscordIntegration discordIntegration;
    private MaintenanceManager maintenanceManager;
    private ClearLagManager clearLagManager;
    private ChatFilterManager chatFilterManager;
    private VanishManager vanishManager;
    private SpectateManager spectateManager;

    private BuildGate buildGate;
    private ChatInputManager chatInputManager;
    private BlockPlaceListener blockPlaceListener;
    private KitLayoutEditor kitLayoutEditor;
    private KitManager kitManager;
    private KitSelectionGui kitSelectionGui;
    private EnderChestCommand enderChestCommand;
    private FrostPlaceholders placeholders;

    private SessionManager screenShareSessionManager;
    private ScheduledTask screenShareTitleTask;

    private volatile boolean shuttingDown = false;

    @Override
    public void onEnable() {
        instance = this;
        this.getDataFolder().mkdirs();
        this.saveDefaultConfig();
        this.saveResource("rules.yml", false);
        this.saveResource("daily.yml", false);
        this.saveResource("ranks.yml", false);

        this.banner();

        // Stateless helper — no config/IO in its constructor, safe to build up front.
        this.buildGate = new BuildGate(this);

        // Order matters (plan §7.2): config first; economy before anything that pays out.
        // AfkManager follows CombatManager because it asks the
        // combat tag whether to suspend the idle timer; Leaderboard reads
        // Kills/Killstreak/Bounty snapshots and physical token inventories for BALANCE
        // board; the two economy consumers (kill reward, action bar) are registered last.
        //
        this.configManager = this.register(new ConfigManager(this));
        this.maintenanceManager = this.register(new MaintenanceManager(this));
        this.clearLagManager = this.register(new ClearLagManager(this));
        this.settingsManager = this.register(new SettingsManager(this));
        this.economyManager = this.register(new EconomyManager(this));
        this.shopManager = this.register(new ShopManager(this));
        this.killsManager = this.register(new KillsManager(this));
        this.killstreakManager = this.register(new KillstreakManager(this));
        this.rankManager = this.register(new RankManager(this));
        this.combatManager = this.register(new CombatManager(this));
        this.afkManager = this.register(new AfkManager(this));
        this.killMessageManager = this.register(new KillMessageManager(this));
        this.rulesManager = this.register(new RulesManager(this));
        this.welcomeManager = this.register(new WelcomeManager(this));
        this.trashManager = this.register(new TrashManager(this));
        this.blockLedger = this.register(new BlockLedger(this));
        this.dailyManager = this.register(new DailyManager(this));
        this.bountyManager = this.register(new BountyManager(this));
        this.leaderboardManager = this.register(new LeaderboardManager(this));
        this.killRewardManager = this.register(new KillRewardManager(this));
        this.juggernautManager = this.register(new JuggernautManager(this));
        this.discordLinkManager = this.register(new DiscordLinkManager(this));
        this.discordIntegration = this.register(new DiscordIntegration(this));
        this.chatFilterManager = this.register(new ChatFilterManager(this));
        this.vanishManager = this.register(new VanishManager(this));
        this.spectateManager = this.register(new SpectateManager(this, this.vanishManager));

        this.chatInputManager = new ChatInputManager(this);

        for (IManager manager : this.managers) {
            manager.onEnable();
        }

        ScreenShareConfig screenShareConfig = new ScreenShareConfig(this);
        BoxAllocator screenShareAllocator = new BoxAllocator(screenShareConfig);
        BoxBuilder screenShareBoxBuilder = new BoxBuilder(this, screenShareAllocator, screenShareConfig);
        SessionStore screenShareStore = new SessionStore(this);
        this.screenShareSessionManager = new SessionManager(
                this, screenShareConfig, screenShareAllocator, screenShareBoxBuilder, screenShareStore);
        this.screenShareSessionManager.loadPersisted();

        long screenShareTitlePeriod = Math.max(1, screenShareConfig.titleResendSeconds()) * 20L;
        this.screenShareTitleTask = this.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(this, task -> this.screenShareSessionManager.tick(), 20L, screenShareTitlePeriod);

        this.registerCommands(screenShareConfig, this.screenShareSessionManager);
        this.registerListeners(screenShareConfig, this.screenShareSessionManager);
        if (this.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.placeholders = new FrostPlaceholders(this);
            this.placeholders.register();
            this.getLogger().info("PlaceholderAPI detected — %frostffa_*% placeholders registered.");
        } else {
            this.getLogger().warning("PlaceholderAPI not found — placeholders disabled.");
        }
        if (!this.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            this.getLogger().warning("LuckPerms not found — daily reward tiers and rank permissions need it.");
        }
        if (this.getServer().getPluginManager().isPluginEnabled("packetevents")) {
            AntiHealthIndicatorBootstrap.register(this);
            this.getLogger().info("PacketEvents detected — Anti-HealthIndicator enabled.");
        } else {
            this.getLogger().warning("PacketEvents not found — Anti-HealthIndicator disabled.");
        }
        this.getLogger().info("FrostFFACore enabled.");
    }

    @Override
    public void onDisable() {
        this.shuttingDown = true;

        // Active screen-share sessions are already written through to sessions.yml. They
        // are intentionally NOT released here so a /reload or restart cannot free a
        // detainee; loadPersisted() re-attaches lockdown on the next enable.
        if (this.screenShareTitleTask != null) {
            this.screenShareTitleTask.cancel();
        }

        if (this.placeholders != null) {
            this.placeholders.unregister();
            this.placeholders = null;
        }

        for (int i = this.managers.size() - 1; i >= 0; i--) {
            try {
                this.managers.get(i).onDisable();
            } catch (Exception e) {
                this.getLogger().severe("Manager disable error: " + e.getMessage());
            }
        }
        this.managers.clear();
        this.getLogger().info("FrostFFACore disabled cleanly.");
    }

    public boolean isShuttingDown() {
        return this.shuttingDown;
    }

    private void banner() {
        this.getLogger().info("╔════════════════════════════════════╗");
        this.getLogger().info("║   F R O S T   F F A   v" + this.getPluginMeta().getVersion() + "         ║");
        this.getLogger().info("║   arena · points · progression      ║");
        this.getLogger().info("╚════════════════════════════════════╝");
    }

    private <T extends IManager> T register(T manager) {
        this.managers.add(manager);
        return manager;
    }

    private void registerCommands(ScreenShareConfig screenShareConfig, SessionManager screenShareSessionManager) {
        this.bind("ss", new SsCommand(this, screenShareSessionManager, screenShareConfig));
        if (this.isModuleEnabled("rules")) this.bind("rules", new RulesCommand(this));
        this.bind("settings", new SettingsCommand(this));
        this.bind("shop", new ShopCommand(this));
        if (this.isModuleEnabled("trash")) this.bind("trash", new TrashCommand(this));
        if (this.isModuleEnabled("leaderboard")) this.bind("leaderboard", new LeaderboardCommand(this));
        if (this.isModuleEnabled("daily")) this.bind("daily", new DailyCommand(this));
        if (this.isModuleEnabled("bounty")) this.bind("bounty", new BountyCommand(this));
        this.bind("rank", new RankCommand(this));

        this.bind("afk", new AfkCommand(this));
        this.bind("frostffa", new FrostFFACommand(this));
        if (this.isModuleEnabled("maintenance")) this.bind("maintenance", new MaintenanceCommand(this));
        this.enderChestCommand = new EnderChestCommand(this);
        this.bind("enderchest", this.enderChestCommand);
        this.bind("announce", new AnnounceCommand());
        this.bind("linkpanelpost", new LinkPanelPostCommand(this));
        this.bind("stats", new StatsCommand(this));
        this.bind("killtoken", new KillTokenCommand(this));
        this.bind("link", new LinkCommand(this));
        this.kitManager = new KitManager(this);
        this.kitLayoutEditor = new KitLayoutEditor(this, this.kitManager);
        this.kitManager.setEditor(this.kitLayoutEditor);
        this.kitSelectionGui = new KitSelectionGui(this, this.kitManager);
        this.kitManager.setSelectionGui(this.kitSelectionGui);
        if (this.isModuleEnabled("kit")) {
            this.bind("kit", this.kitManager);
            this.bind("kitadmin", new KitAdminCommand(this));
            this.bind("kitlayout", this.kitLayoutEditor);
        }
        if (this.isModuleEnabled("juggernaut")) this.bind("juggernaut", new JuggernautCommand(this));
        if (this.isModuleEnabled("chatfilter")) this.bind("chatfilter", new ChatFilterCommand(this));
        if (this.isModuleEnabled("vanish")) this.bind("vanish", new VanishCommand(this, this.vanishManager));
        if (this.isModuleEnabled("spectate")) this.bind("spectate", new SpectateCommand(this, this.spectateManager));
    }

    private boolean isModuleEnabled(String key) {
        return this.getConfig().getBoolean("modules." + key + ".enabled", true);
    }

    private void bind(String name, CommandExecutor executor) {
        PluginCommand command = this.getCommand(name);
        if (command == null) {
            this.getLogger().severe("Command '" + name + "' is missing from plugin.yml — not registered.");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    private void registerListeners(ScreenShareConfig screenShareConfig, SessionManager screenShareSessionManager) {
        PluginManager pm = this.getServer().getPluginManager();
        this.listen(pm, new LockdownListener(this, screenShareSessionManager, screenShareConfig));
        this.listen(pm, new MenuListener(this, screenShareSessionManager, screenShareConfig));
        this.listen(pm, new GuiListener(this));
        this.listen(pm, this.chatInputManager);
        if (this.isModuleEnabled("chatfilter")) this.listen(pm, new ChatFilterListener(this));
        if (this.isModuleEnabled("vanish")) this.listen(pm, new VanishListener(this, this.vanishManager));
        this.listen(pm, new CombatListener(this));
        if (this.isModuleEnabled("maintenance")) this.listen(pm, new MaintenanceListener(this));
        this.listen(pm, new KillListener(this));
        if (this.isModuleEnabled("juggernaut")) this.listen(pm, this.juggernautManager);
        this.listen(pm, new AfkListener(this));
        if (this.isModuleEnabled("welcome")) this.listen(pm, this.welcomeManager);
        this.listen(pm, this.economyManager);
        if (this.isModuleEnabled("trash")) this.listen(pm, this.trashManager);
        if (this.isModuleEnabled("leaderboard")) this.listen(pm, this.leaderboardManager);
        if (this.isModuleEnabled("kit")) {
            this.listen(pm, this.kitLayoutEditor);
            this.listen(pm, this.kitSelectionGui);
            this.listen(pm, new KitNpcListener(this));
        }
        this.listen(pm, this.enderChestCommand);
        this.listen(pm, this.discordLinkManager);
        this.listen(pm, new NpcSoundListener(this));

        this.blockPlaceListener = new BlockPlaceListener(this);
        this.listen(pm, this.blockPlaceListener);
        this.listen(pm, new MaceBlockListener(this));
        this.listen(pm, new SpawnBowListener(this));

    }

    private void listen(PluginManager pm, Listener listener) {
        pm.registerEvents(listener, this);
    }

    public static FrostFFACore getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    public SettingsManager getSettingsManager() {
        return this.settingsManager;
    }

    public EconomyManager getEconomyManager() {
        return this.economyManager;
    }

    public MaintenanceManager getMaintenanceManager() {
        return this.maintenanceManager;
    }

    public ClearLagManager getClearLagManager() {
        return this.clearLagManager;
    }

    public List<IManager> getManagers() {
        return List.copyOf(this.managers);
    }

    public ShopManager getShopManager() {
        return this.shopManager;
    }

    public KillsManager getKillsManager() {
        return this.killsManager;
    }

    public KillstreakManager getKillstreakManager() {
        return this.killstreakManager;
    }

    public CombatManager getCombatManager() {
        return this.combatManager;
    }

    public AfkManager getAfkManager() {
        return this.afkManager;
    }

    public KillMessageManager getKillMessageManager() {
        return this.killMessageManager;
    }

    public RulesManager getRulesManager() {
        return this.rulesManager;
    }

    public WelcomeManager getWelcomeManager() {
        return this.welcomeManager;
    }

    public TrashManager getTrashManager() {
        return this.trashManager;
    }

    public BlockLedger getBlockLedger() {
        return this.blockLedger;
    }

    public BuildGate getBuildGate() {
        return this.buildGate;
    }

    public LeaderboardManager getLeaderboardManager() {
        return this.leaderboardManager;
    }

    public KillRewardManager getKillRewardManager() {
        return this.killRewardManager;
    }

    public ChatInputManager getChatInputManager() {
        return this.chatInputManager;
    }

    public RankManager getRankManager() {
        return this.rankManager;
    }

    public DailyManager getDailyManager() {
        return this.dailyManager;
    }

    public BountyManager getBountyManager() {
        return this.bountyManager;
    }

    public JuggernautManager getJuggernautManager() {
        return this.juggernautManager;
    }

    public DiscordLinkManager getDiscordLinkManager() {
        return this.discordLinkManager;
    }

    public DiscordIntegration getDiscordIntegration() {
        return this.discordIntegration;
    }

    public ChatFilterManager getChatFilterManager() {
        return this.chatFilterManager;
    }

    public VanishManager getVanishManager() {
        return this.vanishManager;
    }

    public SpectateManager getSpectateManager() {
        return this.spectateManager;
    }

    public BlockPlaceListener getBlockPlaceListener() {
        return this.blockPlaceListener;
    }
}
