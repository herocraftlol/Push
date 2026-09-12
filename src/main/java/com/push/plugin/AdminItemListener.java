package com.push.plugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Gere le diamant special place en slot 0 pour les admins en attente dans un lobby :
 * un clic dessus lance immediatement la partie sans attendre le nombre minimum de joueurs.
 */
public class AdminItemListener implements Listener {

    private final PushPlugin plugin;

    public AdminItemListener(PushPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.DIAMOND) return;
        if (!isStartItem(item)) return;

        event.setCancelled(true);

        if (!player.hasPermission("push.admin")) {
            return;
        }

        ArenaManager manager = plugin.getArenaManager();
        Arena arena = manager.getArenaOf(player.getUniqueId());
        if (arena == null) {
            player.sendMessage(ChatColor.RED + "Tu n'es dans aucun lobby d'arene.");
            return;
        }
        if (arena.getState() != Arena.State.WAITING) {
            player.sendMessage(ChatColor.RED + "La partie a deja demarre sur cette arene.");
            return;
        }

        manager.forceStart(arena);
        player.sendMessage(ChatColor.GREEN + "Tu as force le demarrage de la partie.");
    }

    private boolean isStartItem(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && meta.getDisplayName().contains("Lancer la partie maintenant");
    }
}
