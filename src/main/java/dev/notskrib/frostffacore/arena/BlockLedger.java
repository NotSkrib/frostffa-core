package dev.notskrib.frostffacore.arena;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.manager.IManager;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Local stand-in for MoneyFFAV2's MapClearManager. Tracks every player-placed,
 * whitelisted block so the Plier Axe has something to strip and the map can be swept
 * clean on a timer. There is deliberately no standalone /mapclear command here.
 */
public class BlockLedger implements IManager {

    private final FrostFFACore plugin;
    private BukkitTask countdownTask;
    private BukkitTask ledgerSaveTask;
    private volatile boolean dirty = false;
    private int autoIntervalSeconds = 0;
    private boolean announceCountdown = true;
    private final Set<Location> placedBlocks = Collections.synchronizedSet(new HashSet<>());
    private File ledgerFile;

    public BlockLedger(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        this.ledgerFile = new File(this.plugin.getDataFolder(), "placed_blocks.yml");
        this.loadLedger();
        this.readConfig();
        int ledgerSeconds = Math.max(15, this.plugin.getConfig().getInt("mapclear.ledger-save-seconds", 30));
        this.ledgerSaveTask = this.plugin.getServer().getScheduler().runTaskTimer(
            this.plugin, this::saveDirtyLedger, ledgerSeconds * 20L, ledgerSeconds * 20L);

        if (this.autoIntervalSeconds > 0) {
            this.startCountdown();
            this.plugin.getLogger().info("BlockLedger: Auto-clear every " + this.autoIntervalSeconds / 60 + " min.");
        } else {
            this.plugin.getLogger().info("BlockLedger enabled (auto-clear disabled).");
        }
    }

    public void reload() {
        if (this.countdownTask != null) {
            this.countdownTask.cancel();
            this.countdownTask = null;
        }
        this.readConfig();
        if (this.autoIntervalSeconds > 0) {
            this.startCountdown();
        }
    }

    @Override
    public void onDisable() {
        if (this.countdownTask != null) {
            this.countdownTask.cancel();
            this.countdownTask = null;
        }
        if (this.ledgerSaveTask != null) {
            this.ledgerSaveTask.cancel();
            this.ledgerSaveTask = null;
        }
        this.saveLedger();
        this.placedBlocks.clear();
        this.plugin.getLogger().info("BlockLedger disabled.");
    }

    public void addBlock(Location loc) {
        if (this.placedBlocks.add(loc.toBlockLocation())) this.dirty = true;
    }

    public void removeBlock(Location loc) {
        if (this.placedBlocks.remove(loc.toBlockLocation())) this.dirty = true;
    }

    public void saveDirtyLedger() {
        if (!this.dirty) return;
        this.saveLedger();
        this.dirty = false;
    }

    public boolean isPlaced(Location loc) {
        return this.placedBlocks.contains(loc.toBlockLocation());
    }

    public List<Location> placedWithin(Location center, int radius) {
        List<Location> found = new ArrayList<>();
        Location origin = center.toBlockLocation();
        synchronized (this.placedBlocks) {
            for (Location placed : this.placedBlocks) {
                if (placed.getWorld() == null || !placed.getWorld().equals(origin.getWorld())) continue;
                if (Math.abs(placed.getBlockX() - origin.getBlockX()) > radius) continue;
                if (Math.abs(placed.getBlockY() - origin.getBlockY()) > radius) continue;
                if (Math.abs(placed.getBlockZ() - origin.getBlockZ()) > radius) continue;
                found.add(placed);
            }
        }
        return found;
    }

    public int clearPlacedInside(Location corner1, Location corner2) {
        if (corner1 == null || corner2 == null || corner1.getWorld() == null) return 0;
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        List<Location> doomed = new ArrayList<>();
        synchronized (this.placedBlocks) {
            for (Location placed : this.placedBlocks) {
                if (placed.getWorld() == null || !placed.getWorld().equals(corner1.getWorld())) continue;
                if (placed.getBlockX() < minX || placed.getBlockX() > maxX) continue;
                if (placed.getBlockY() < minY || placed.getBlockY() > maxY) continue;
                if (placed.getBlockZ() < minZ || placed.getBlockZ() > maxZ) continue;
                doomed.add(placed);
            }
        }
        for (Location loc : doomed) {
            if (loc.getBlock().getType() != Material.AIR) loc.getBlock().setType(Material.AIR);
            this.placedBlocks.remove(loc);
        }
        return doomed.size();
    }

    public void clearMap() {
        final List<Item> items = new ArrayList<>();
        for (World world : this.plugin.getServer().getWorlds()) {
            items.addAll(world.getEntitiesByClass(Item.class));
        }
        final int totalItems = items.size();
        final List<Location> blocks = new ArrayList<>(this.placedBlocks);
        this.placedBlocks.clear();
        this.dirty = true;
        new BukkitRunnable() {
            int itemIdx = 0;
            int blockIdx = 0;

            public void run() {
                int ops;
                for (ops = 0; this.itemIdx < items.size() && ops < 100; ++ops) {
                    Item item = items.get(this.itemIdx++);
                    if (item.isDead()) continue;
                    item.remove();
                }
                while (this.blockIdx < blocks.size() && ops < 100) {
                    Location loc = blocks.get(this.blockIdx++);
                    if (loc.getWorld() != null && loc.getBlock().getType() != Material.AIR) {
                        loc.getBlock().setType(Material.AIR);
                    }
                    ++ops;
                }
                if (this.itemIdx >= items.size() && this.blockIdx >= blocks.size()) {
                    MessageUtil.broadcast("<color:" + Palette.INK + ">ᴛʜᴇ ᴍᴀᴘ ʜᴀꜱ ʙᴇᴇɴ ᴄʟᴇᴀʀᴇᴅ! <dark_gray>(<color:"
                        + Palette.GLACIER + ">" + totalItems + " item" + (totalItems == 1 ? "" : "s")
                        + "<dark_gray>, <color:" + Palette.GLACIER + ">" + blocks.size() + " block"
                        + (blocks.size() == 1 ? "" : "s") + " <dark_gray>ʀᴇᴍᴏᴠᴇᴅ)");
                    this.cancel();
                }
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    private void startCountdown() {
        if (this.autoIntervalSeconds <= 0) {
            return;
        }
        int[] countdown = new int[]{this.autoIntervalSeconds};
        this.countdownTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            if (countdown[0] <= 10 && countdown[0] > 0 && this.announceCountdown) {
                MessageUtil.broadcast("<color:" + Palette.GLACIER + ">" + countdown[0]
                    + (countdown[0] == 1 ? " <color:" + Palette.INK + ">ꜱᴇᴄᴏɴᴅ ᴜɴᴛɪʟ ᴛʜᴇ ᴍᴀᴘ ɪꜱ ᴄʟᴇᴀʀᴇᴅ!"
                                          : " <color:" + Palette.INK + ">ꜱᴇᴄᴏɴᴅꜱ ᴜɴᴛɪʟ ᴛʜᴇ ᴍᴀᴘ ɪꜱ ᴄʟᴇᴀʀᴇᴅ!"));
            }
            if (countdown[0] <= 0) {
                this.clearMap();
                countdown[0] = this.autoIntervalSeconds;
            } else {
                countdown[0] = countdown[0] - 1;
            }
        }, 20L, 20L);
    }

    private void saveLedger() {
        YamlConfiguration out = new YamlConfiguration();
        List<String> encoded = new ArrayList<>();
        synchronized (this.placedBlocks) {
            for (Location loc : this.placedBlocks) {
                if (loc.getWorld() == null) continue;
                encoded.add(loc.getWorld().getName() + ";" + loc.getBlockX()
                    + ";" + loc.getBlockY() + ";" + loc.getBlockZ());
            }
        }
        out.set("placed", encoded);
        try {
            out.save(this.ledgerFile);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Could not save placed_blocks.yml: " + e.getMessage());
        }
    }

    private void loadLedger() {
        if (!this.ledgerFile.exists()) return;
        YamlConfiguration in = YamlConfiguration.loadConfiguration(this.ledgerFile);
        int skipped = 0;
        for (String encoded : in.getStringList("placed")) {
            String[] parts = encoded.split(";");
            if (parts.length != 4) continue;
            World world = this.plugin.getServer().getWorld(parts[0]);
            if (world == null) {
                skipped++;
                continue;
            }
            try {
                this.placedBlocks.add(new Location(world,
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
            } catch (NumberFormatException e) {
                skipped++;
            }
        }
        this.plugin.getLogger().info("BlockLedger: " + this.placedBlocks.size()
            + " player-placed block(s) restored" + (skipped > 0 ? ", " + skipped + " skipped." : "."));
    }

    private void readConfig() {
        this.autoIntervalSeconds = this.plugin.getConfig().getInt("mapclear.interval-minutes", 0) * 60;
        this.announceCountdown = this.plugin.getConfig().getBoolean("mapclear.announce-countdown", true);
    }
}
