package dev.notskrib.frostffacore.rules;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.manager.IManager;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

public class RulesManager implements IManager {

    private final FrostFFACore plugin;

    private File file;
    private List<String> rules = new ArrayList<>();
    private int perPage = 10;
    private String header = "";
    private String footer = "";
    private String footerLast = "";

    public RulesManager(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.file = new File(this.plugin.getDataFolder(), "rules.yml");
        if (!this.file.exists()) this.plugin.saveResource("rules.yml", false);
        this.reload();
        this.plugin.getLogger().info("RulesManager enabled — " + this.rules.size() + " rule(s).");
    }

    @Override
    public void onDisable() {
        this.plugin.getLogger().info("RulesManager disabled.");
    }

    public void reload() {
        if (this.file == null || !this.file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.file);
        this.rules = new ArrayList<>(cfg.getStringList("list"));
        this.perPage = Math.max(1, cfg.getInt("per-page", 10));
        this.header = cfg.getString("header", "");
        this.footer = cfg.getString("footer", "");
        this.footerLast = cfg.getString("footer-last", "");
    }

    public int size() {
        return this.rules.size();
    }

    public int perPage() {
        return this.perPage;
    }

    public int pages() {
        return Math.max(1, (this.rules.size() + this.perPage - 1) / this.perPage);
    }

    public String header() {
        return this.header;
    }

    public String footer() {
        return this.footer;
    }

    public String footerLast() {
        return this.footerLast;
    }

    public List<String> page(int page) {
        List<String> out = new ArrayList<>();
        if (this.rules.isEmpty()) return out;

        int clamped = Math.max(1, Math.min(page, this.pages()));
        int from = (clamped - 1) * this.perPage;
        int to = Math.min(from + this.perPage, this.rules.size());

        for (int i = from; i < to; i++) {
            out.add("<color:" + Palette.DIM + ">" + (i + 1) + ".</color> "
                + "<color:" + Palette.INK + ">" + this.rules.get(i) + "</color>");
        }
        return out;
    }
}
