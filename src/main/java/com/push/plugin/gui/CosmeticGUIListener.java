package com.push.plugin.gui;

import com.push.plugin.PushPlugin;
import com.push.plugin.levels.PushCosmetic;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class CosmeticGUIListener implements Listener {

    private final PushPlugin plugin;
    private final CosmeticGUI cosmeticGUI;

    public CosmeticGUIListener(PushPlugin plugin, CosmeticGUI cosmeticGUI) {
        this.plugin = plugin;
        this.cosmeticGUI = cosmeticGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!cosmeticGUI.isCosmeticGuiTitle(event.getView().getTitle())) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        PushCosmetic cosmetic = cosmeticGUI.getCosmeticAt(event.getRawSlot());
        if (cosmetic == null) return;

        boolean equipped = plugin.getLevelManager().equipCosmetic(player, cosmetic);
        if (equipped) {
            player.sendMessage(ChatColor.GREEN + "Cosmetique equipe : " + ChatColor.WHITE + cosmetic.getDisplayName());
            cosmetic.preview(player);
            cosmeticGUI.open(player);
        } else {
            player.sendMessage(ChatColor.RED + "Ce cosmetique est verrouille (niveau "
                    + cosmetic.getRequiredLevel() + " requis, tu es niveau "
                    + plugin.getLevelManager().getLevel(player.getUniqueId()) + ").");
        }
    }
}
