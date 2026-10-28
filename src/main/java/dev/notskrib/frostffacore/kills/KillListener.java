package dev.notskrib.frostffacore.kills;

import dev.notskrib.frostffacore.FrostFFACore;
import dev.notskrib.frostffacore.core.Palette;
import dev.notskrib.frostffacore.core.util.MessageUtil;
import dev.notskrib.frostffacore.core.util.TimeUtil;
import dev.notskrib.frostffacore.economy.KillRewardManager;
import dev.notskrib.frostffacore.economy.Points;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class KillListener implements Listener {

    private final FrostFFACore plugin;

    public KillListener(FrostFFACore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        this.plugin.getKillsManager().addDeath(victim);
        boolean combatLogged = this.plugin.getCombatManager().consumeCombatLogged(victim.getUniqueId());

        if (killer == null || killer.equals(victim)) {
            this.plugin.getKillstreakManager().onDeath(victim);
            this.plugin.getKillMessageManager().applyEnvironment(event, victim, combatLogged);
            return;
        }

        // Single AFK gate (plan §6.7) — every "an AFK victim earns the killer nothing"
        // consequence is decided right here, once, rather than re-checked inside
        // KillsManager/KillstreakManager/KillRewardManager/BountyManager separately.
        // The kill message itself still fires normally either way (plan §6.7 only zeroes
        // reward/streak/kills/bounty — it never says to hide who killed whom).
        boolean victimAfk = this.plugin.getAfkManager().isAfk(victim);
        boolean creditKill = !victimAfk || this.plugin.getAfkManager().afkKillsCount();
        boolean payReward = !victimAfk || this.plugin.getAfkManager().afkKillsPay();

        // Read before the streak call below clears it — the death message wants the
        // streak the victim lost, regardless of whether the killer gets credit for it.
        int victimStreak = this.plugin.getKillstreakManager().getStreak(victim);

        int streak;
        if (creditKill) {
            this.plugin.getKillsManager().addKill(killer);
            this.announceMilestone(killer);
            // RankManager reads the just-updated kill total to detect a promotion crossing.
            this.plugin.getRankManager().onKill(killer);
            // Streak is bumped before the payout so the Streak Sword sees the kill that
            // just happened, which is what "every 10 kills on the current killstreak" means.
            this.plugin.getKillstreakManager().onKill(killer, victim);
            streak = this.plugin.getKillstreakManager().getStreak(killer);
        } else {
            // The victim still died — their own streak ends either way — but nothing here
            // credits the killer's kills/rank/streak (plan §6.7: afk.kills-count: false).
            this.plugin.getKillstreakManager().onDeath(victim);
            streak = this.plugin.getKillstreakManager().getStreak(killer);
        }

        KillRewardManager.Payout payout = payReward
            ? this.plugin.getKillRewardManager().reward(killer, victim, streak)
            : new KillRewardManager.Payout(0, false, 1.0);
        if (payout.amount() > 0) {
            this.plugin.getEconomyManager().deposit(killer.getUniqueId(), payout.amount(),
                "kill:" + victim.getName() + (payout.farmed() ? ":repeat" : ""));
        }

        // Bounty payout (plan §6.9) — the killer collects the victim's pool minus their
        // own contribution, which is refunded to them instead. An AFK victim's bounty is
        // NOT paid out; the pool stays intact and the killer is told why (plan §6.7).
        var bountyPayout = victimAfk ? null : this.plugin.getBountyManager().onKill(killer, victim);
        if (bountyPayout != null && bountyPayout.paidToKiller() > 0) {
            MessageUtil.sendRaw(killer, "<color:" + Palette.BRAND + "><bold>+"
                + Points.format(bountyPayout.paidToKiller()) + "</bold></color> <color:" + Palette.INK
                + ">bounty on " + victim.getName() + "</color>");
        } else if (victimAfk && this.plugin.getBountyManager().poolOf(victim.getUniqueId()) > 0) {
            MessageUtil.sendRaw(killer, "<color:" + Palette.INK + ">there's a bounty on "
                + victim.getName() + ", but they were AFK — it wasn't paid out.</color>");
        }

        StringBuilder line = new StringBuilder();
        line.append("<color:").append(Palette.BRAND).append("><bold>+")
            .append(Points.format(payout.amount())).append("</bold></color>");
        line.append(" <color:").append(Palette.INK).append(">for ")
            .append(victim.getName()).append("</color>");
        if (victimAfk) {
            line.append("  <dark_gray>|</dark_gray>  <color:").append(Palette.DIM).append(">ᴀꜰᴋ</color>");
        }

        if (payout.multiplier() > 1.0) {
            line.append("  <dark_gray>|</dark_gray>  <color:").append(Palette.WARN).append(">×")
                .append(String.format("%.2f", payout.multiplier())).append("</color>");
        }
        if (payout.farmed()) {
            long left = this.plugin.getKillRewardManager()
                .remainingCooldown(killer.getUniqueId(), victim.getUniqueId());
            line.append("  <dark_gray>|</dark_gray>  <color:").append(Palette.BAD)
                .append(">repeat kill</color> <color:").append(Palette.INK).append(">")
                .append(TimeUtil.shortDuration(left)).append(" left</color>");
        }

        MessageUtil.sendRaw(killer, line.toString());
        this.plugin.getKillMessageManager().applyKill(event, killer, victim, payout, streak, victimStreak);
        killer.playSound(killer.getLocation(),
            payout.farmed() ? Sound.BLOCK_NOTE_BLOCK_BASS : Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
            0.7f, payout.farmed() ? 0.8f : 1.5f);
    }

    private void announceMilestone(Player killer) {
        int interval = this.plugin.getConfig().getInt("announcements.kill-milestones.interval", 5);
        if (!this.plugin.getConfig().getBoolean("announcements.kill-milestones.enabled", true)
            || interval <= 0) return;
        int kills = this.plugin.getKillsManager().getKills(killer.getUniqueId());
        if (kills % interval != 0) return;
        String message = this.plugin.getConfig().getString("announcements.kill-milestones.message", "");
        if (message.isEmpty()) return;
        MessageUtil.broadcastRaw(message.replace("<player>", killer.getName())
            .replace("<kills>", Integer.toString(kills)));
    }
}
