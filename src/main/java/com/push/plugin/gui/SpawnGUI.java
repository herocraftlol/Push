package com.push.plugin.gui;

import com.push.plugin.Arena;
import com.push.plugin.PushPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI d'administration permettant de visualiser et gerer les spawns d'une arene.
 *
 * Une case (vitre teintee) par equipe, dans les 4 premiers slots de la premiere ligne.
 * La couleur de chaque vitre depend du nombre d'equipes de l'arene (voir
 * {@link Arena#spawnGlassColors(int)}) :
 *   - 2 equipes  -> Magenta / Rose
 *   - 3/4 equipes -> Violet / Bleu clair / Bleu / Cyan
 *
 * Chaque case affiche dans sa lore le nombre de spawns deja configures pour cette equipe
 * (plusieurs spawns sont utiles quand teamSize > 1, pour eviter que tous les joueurs
 * d'une meme equipe apparaissent au meme endroit). Un clic gauche rappelle la commande a
 * utiliser pour ajouter un spawn (il faut etre physiquement a l'endroit voulu), un clic
 * droit (ou shift-clic) supprime le dernier spawn ajoute pour cette equipe.
 */
public class SpawnGUI {

    private static final String GUI_TITLE_PREFIX = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "\u2726 Spawns - ";
    private static final int GUI_SIZE = 27;

    private final PushPlugin plugin;

    // Retient, pour chaque joueur ayant ce GUI ouvert, le nom de l'arene concernee
    // (plus fiable que de reparser le titre de l'inventaire, notamment si le nom
    // de l'arene contient un tiret).
    private final Map<UUID, String> openArenaByPlayer = new HashMap<>();

    public SpawnGUI(PushPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Arena arena) {
        openArenaByPlayer.put(player.getUniqueId(), arena.getName());
        player.openInventory(buildInventory(arena));
    }

    public String titleFor(Arena arena) {
        return GUI_TITLE_PREFIX + arena.getName();
    }

    public boolean isSpawnGuiTitle(String title) {
        return title != null && title.startsWith(GUI_TITLE_PREFIX);
    }

    /**
     * Retourne le nom de l'arene actuellement ouverte dans ce GUI pour ce joueur,
     * ou null si aucune session n'est en cours pour lui.
     */
    public String getOpenArenaName(UUID playerUuid) {
        return openArenaByPlayer.get(playerUuid);
    }

    public void forgetSession(UUID playerUuid) {
        openArenaByPlayer.remove(playerUuid);
    }

    public Inventory buildInventory(Arena arena) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, titleFor(arena));

        int teamCount = arena.getTeamCount();
        Material[] colors = Arena.spawnGlassColors(teamCount);

        // Filler de fond en verre noir, comme HikaBrain
        ItemStack filler = buildFiller();
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, filler);
        }

        // Une vitre par equipe, centree sur la premiere ligne
        int startSlot = (9 - teamCount) / 2;
        for (int i = 0; i < teamCount; i++) {
            inv.setItem(startSlot + i, buildTeamItem(arena, i, colors[i]));
        }

        return inv;
    }

    private ItemStack buildTeamItem(Arena arena, int teamIndex, Material glass) {
        int count = arena.getSpawnCount(teamIndex);
        String colorName = Arena.spawnColorName(arena.getTeamCount(), teamIndex);

        ItemStack item = new ItemStack(glass);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        ChatColor nameColor = count > 0 ? ChatColor.GREEN : ChatColor.RED;
        meta.setDisplayName(nameColor + "" + ChatColor.BOLD + "Equipe " + (teamIndex + 1)
                + ChatColor.GRAY + " (" + colorName + ")");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Spawns configures : "
                + (count > 0 ? ChatColor.GREEN : ChatColor.RED) + count);
        lore.add("");
        lore.add(ChatColor.YELLOW + "\u25B6 Pour ajouter un spawn, place-toi");
        lore.add(ChatColor.YELLOW + "   a l'endroit voulu puis tape :");
        lore.add(ChatColor.WHITE + "   /p setspawn " + arena.getName() + " " + (teamIndex + 1));
        if (count > 0) {
            lore.add("");
            lore.add(ChatColor.RED + "\u25B6 Clic droit : supprimer le dernier spawn");
        }
        meta.setLore(lore);

        item.setItemMeta(meta);
        item.setAmount(Math.max(1, Math.min(count, 64)));
        return item;
    }

    private ItemStack buildFiller() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Retrouve l'index d'equipe correspondant au slot clique, ou -1 si le slot ne
     * correspond a aucune equipe (filler).
     */
    public int getTeamIndexAt(Arena arena, int slot) {
        int teamCount = arena.getTeamCount();
        int startSlot = (9 - teamCount) / 2;
        int relative = slot - startSlot;
        if (relative < 0 || relative >= teamCount) return -1;
        return relative;
    }
}
