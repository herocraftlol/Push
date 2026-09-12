package com.push.plugin.gui;

import com.push.plugin.Arena;
import com.push.plugin.ArenaManager;
import com.push.plugin.PushPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Ecoute les clics dans le GUI de gestion des spawns (SpawnGUI) :
 * - Clic gauche sur une equipe -> rappel de la commande a utiliser (voir lore)
 * - Clic droit sur une equipe -> supprime le dernier spawn configure pour cette equipe
 */
public class SpawnGUIListener implements Listener {

    private final PushPlugin plugin;
    private final SpawnGUI spawnGUI;

    public SpawnGUIListener(PushPlugin plugin, SpawnGUI spawnGUI) {
        this.plugin = plugin;
        this.spawnGUI = spawnGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!spawnGUI.isSpawnGuiTitle(title)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        String arenaName = spawnGUI.getOpenArenaName(player.getUniqueId());
        if (arenaName == null) return;
        ArenaManager manager = plugin.getArenaManager();
        Arena arena = manager.get(arenaName);
        if (arena == null) {
            player.sendMessage(ChatColor.RED + "Cette arene n'existe plus.");
            player.closeInventory();
            return;
        }

        int teamIndex = spawnGUI.getTeamIndexAt(arena, event.getRawSlot());
        if (teamIndex < 0) return; // Slot de remplissage

        if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
            int count = arena.getSpawnCount(teamIndex);
            if (count == 0) {
                player.sendMessage(ChatColor.RED + "Cette equipe n'a aucun spawn a supprimer.");
                return;
            }
            arena.removeSpawn(teamIndex, count);
            manager.saveArena(arena);
            player.sendMessage(ChatColor.GREEN + "Spawn #" + count + " de l'equipe " + (teamIndex + 1)
                    + " supprime.");
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                if (player.isOnline()) {
                    spawnGUI.open(player, arena);
                }
            }, 1L);
        } else {
            String colorName = Arena.spawnColorName(arena.getTeamCount(), teamIndex);
            player.sendMessage(ChatColor.GOLD + "Equipe " + (teamIndex + 1) + ChatColor.GRAY
                    + " (" + colorName + ")" + ChatColor.YELLOW
                    + " -> place-toi a l'endroit voulu puis tape :");
            player.sendMessage(ChatColor.WHITE + "/p setspawn " + arena.getName() + " " + (teamIndex + 1));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!spawnGUI.isSpawnGuiTitle(event.getView().getTitle())) return;
        spawnGUI.forgetSession(event.getPlayer().getUniqueId());
    }
}
