package com.push.plugin.gui;

import com.push.plugin.Arena;
import com.push.plugin.ArenaManager;
import com.push.plugin.PushPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Ecoute les clics dans le GUI d'arenes et effectue l'action correspondante :
 * - Clic sur une arene disponible -> rejoint le lobby de cette arene (equivalent /p join <nom>)
 */
public class ArenaGUIListener implements Listener {

    private final PushPlugin plugin;
    private final ArenaGUI arenaGUI;

    public ArenaGUIListener(PushPlugin plugin, ArenaGUI arenaGUI) {
        this.plugin = plugin;
        this.arenaGUI = arenaGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Verifier que c'est bien notre GUI (par le titre)
        if (!ArenaGUI.GUI_TITLE.equals(event.getView().getTitle())) return;

        // Annuler toujours le clic pour eviter de prendre des items
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();

        String arenaName = arenaGUI.getArenaNameAt(slot);
        if (arenaName == null) return; // Slot vide / filler

        ArenaManager manager = plugin.getArenaManager();
        Arena arena = manager.get(arenaName);
        if (arena == null) {
            player.sendMessage(ChatColor.RED + "Cette arene n'existe plus.");
            player.closeInventory();
            return;
        }

        player.closeInventory();

        // Clic droit : observer la partie en spectateur plutot que d'y participer
        if (event.isRightClick()) {
            if (!player.hasPermission("push.spectate")) {
                player.sendMessage(ChatColor.RED + "Tu n'as pas la permission d'observer une partie.");
                return;
            }
            manager.addSpectator(player, arena);
            return;
        }

        // Toute la logique de validation (configuree, en attente, deja dans une arene, pleine...)
        // est deja geree par ArenaManager#joinLobby, on delegue simplement.
        manager.joinLobby(player, arena);
    }
}
