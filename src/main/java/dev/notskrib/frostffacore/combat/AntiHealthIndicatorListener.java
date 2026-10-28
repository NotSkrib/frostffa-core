package dev.notskrib.frostffacore.combat;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import dev.notskrib.frostffacore.FrostFFACore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Spoofs the Health metadata field (index 9, Float) to full health for every observer except
 * the player it belongs to, so health-bar mods and packs can't read an opponent's HP.
 * Requires PacketEvents; does nothing without it.
 */
public final class AntiHealthIndicatorListener extends PacketListenerAbstract implements Listener {

    private static final int HEALTH_INDEX = 9;

    private final FrostFFACore plugin;
    private final Map<Integer, UUID> entityIdToPlayer = new ConcurrentHashMap<>();

    public AntiHealthIndicatorListener(FrostFFACore plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        this.entityIdToPlayer.put(player.getEntityId(), player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.entityIdToPlayer.remove(event.getPlayer().getEntityId());
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!this.plugin.getConfig().getBoolean("combat.anti-health-indicator.enabled", true)) return;
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) return;

        WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
        UUID ownerId = this.entityIdToPlayer.get(wrapper.getEntityId());
        if (ownerId == null) return;
        if (ownerId.equals(event.getUser().getUUID())) return;

        List<EntityData<?>> original = wrapper.getEntityMetadata();
        boolean changed = false;
        List<EntityData<?>> rewritten = new ArrayList<>(original.size());
        float fakeHealth = (float) this.plugin.getConfig().getDouble("combat.anti-health-indicator.fake-health", 20.0);
        for (EntityData<?> data : original) {
            if (data.getIndex() == HEALTH_INDEX && data.getType() == EntityDataTypes.FLOAT) {
                rewritten.add(new EntityData<>(HEALTH_INDEX, EntityDataTypes.FLOAT, fakeHealth));
                changed = true;
            } else {
                rewritten.add(data);
            }
        }
        if (changed) {
            wrapper.setEntityMetadata(rewritten);
        }
    }
}
