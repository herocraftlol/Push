package com.push.plugin.chat;

import com.push.plugin.Arena;
import com.push.plugin.ArenaManager;
import com.push.plugin.PushPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Detecte un clic (gauche ou droit) en tenant un bloc de couleur "message rapide"
 * pendant la pause post-point (3s) ou l'ecran de victoire, et envoie alors le message
 * correspondant dans le chat de l'arene — voir {@link QuickMessage}.
 */
public class QuickChatListener implements Listener {

    /** Anti-spam : evite qu'un clic maintenu declenche une rafale de messages. */
    private static final long COOLDOWN_MILLIS = 1500;

    private final PushPlugin plugin;
    private final Map<UUID, Long> lastSentAt = new HashMap<>();

    public QuickChatListener(PushPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("quick-chat.enabled", true)) return;

        Action action = event.getAction();
        boolean isClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK
                || action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!isClick) return;

        Player player = event.getPlayer();
        ArenaManager manager = plugin.getArenaManager();
        Arena arena = manager.getArenaOf(player.getUniqueId());
        if (arena == null) return;

        // Les blocs de message rapide ne sont dans la hotbar que pendant la pause qui
        // suit un point (isArenaPaused) ou a l'ecran de victoire (ENDING).
        boolean isPause = arena.getState() == Arena.State.RUNNING && manager.isArenaPaused(arena);
        boolean isEnding = arena.getState() == Arena.State.ENDING;
        if (!isPause && !isEnding) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        QuickMessage message = QuickMessage.fromItem(item);
        if (message == null) return;

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Long last = lastSentAt.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MILLIS) return;
        lastSentAt.put(player.getUniqueId(), now);

        manager.sendQuickChatMessage(arena, player, message);
    }
}
