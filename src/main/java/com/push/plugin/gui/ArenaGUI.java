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
import java.util.Collection;
import java.util.List;

/**
 * GUI d'inventaire permettant au joueur de voir toutes les arenes disponibles
 * et de rejoindre le lobby de l'une d'elles en un clic.
 *
 * Structure :
 *   - Lignes 1 a 5 : une icone par arene (max 45 arenes affichees)
 *   - Le reste des slots vides des lignes 1-5 est rempli de verre noir
 *
 * Taille du GUI = 54 slots (6 rangees x 9 colonnes).
 */
public class ArenaGUI {

    /** Titre affiche dans la barre du coffre. */
    public static final String GUI_TITLE = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "\u2694 Arenes disponibles";

    /** Nombre total de slots du GUI (6 rangees). */
    private static final int GUI_SIZE = 54;

    /** Nombre de slots reellement utilisables pour lister des arenes. */
    private static final int MAX_ARENA_SLOTS = 45;

    private final PushPlugin plugin;

    public ArenaGUI(PushPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le GUI pour le joueur donne.
     */
    public void open(Player player) {
        Inventory inv = buildInventory();
        player.openInventory(inv);
    }

    /**
     * Construit et retourne l'inventaire rempli.
     */
    public Inventory buildInventory() {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        Collection<Arena> allArenas = plugin.getArenaManager().getAll();

        int slot = 0;
        for (Arena arena : allArenas) {
            if (slot >= MAX_ARENA_SLOTS) break; // Max 45 arenes affichees

            ItemStack item = buildArenaItem(arena);
            inv.setItem(slot, item);
            slot++;
        }

        // Remplir les slots vides restants avec du verre noir
        ItemStack filler = buildFiller();
        for (int i = slot; i < GUI_SIZE; i++) {
            inv.setItem(i, filler);
        }

        return inv;
    }

    /**
     * Cree l'icone representant une arene.
     * - Arene jouable (WAITING, pas pleine) -> vitre teintee verte (lime)
     * - Arene en cours (RUNNING/ENDING) -> vitre teintee rouge
     * - Arene pleine -> vitre teintee orange
     * - Arene non configuree -> vitre teintee grise
     */
    private ItemStack buildArenaItem(Arena arena) {
        String name = arena.getName();
        Arena.State state = arena.getState();
        int current = arena.countPlayers();
        int max = arena.getMaxPlayers();

        Material mat;
        String displayName;
        String statusLine;
        ChatColor statusColor;

        if (!arena.isFullyConfigured()) {
            mat = Material.GRAY_STAINED_GLASS_PANE;
            displayName = ChatColor.GRAY + "" + ChatColor.BOLD + "\u2716 " + capitalize(name);
            statusLine = ChatColor.GRAY + "Non configuree";
            statusColor = ChatColor.GRAY;
        } else if (state == Arena.State.RUNNING || state == Arena.State.ENDING) {
            mat = Material.RED_STAINED_GLASS_PANE;
            displayName = ChatColor.RED + "" + ChatColor.BOLD + "\u2694 " + capitalize(name);
            statusLine = ChatColor.RED + "Partie en cours";
            statusColor = ChatColor.RED;
        } else if (current >= max) {
            mat = Material.ORANGE_STAINED_GLASS_PANE;
            displayName = ChatColor.GOLD + "" + ChatColor.BOLD + "\u26A0 " + capitalize(name);
            statusLine = ChatColor.GOLD + "Pleine";
            statusColor = ChatColor.GOLD;
        } else {
            mat = Material.LIME_STAINED_GLASS_PANE;
            displayName = ChatColor.GREEN + "" + ChatColor.BOLD + "\u2714 " + capitalize(name);
            statusLine = ChatColor.GREEN + "Disponible";
            statusColor = ChatColor.GREEN;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(displayName);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Joueurs : " + statusColor + current + ChatColor.DARK_GRAY + "/" + ChatColor.GRAY + max);
        lore.add(ChatColor.GRAY + "Equipes : " + ChatColor.WHITE + arena.getTeamCount());
        lore.add(ChatColor.GRAY + "Statut  : " + statusLine);
        lore.add("");

        boolean joinable = arena.isFullyConfigured()
                && state == Arena.State.WAITING
                && current < max;

        if (joinable) {
            lore.add(ChatColor.YELLOW + "\u25B6 Clic gauche : rejoindre");
        } else {
            lore.add(ChatColor.RED + "\u2716 Indisponible pour jouer");
        }
        if (arena.isFullyConfigured()) {
            lore.add(ChatColor.AQUA + "\u25C9 Clic droit : observer");
            if (arena.countSpectators() > 0) {
                lore.add(ChatColor.DARK_GRAY + "   " + arena.countSpectators() + " spectateur(s)");
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Case de remplissage pour les slots vides.
     */
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
     * Met la premiere lettre en majuscule.
     */
    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Retourne le nom de l'arene a partir de son slot dans le GUI.
     * Retourne null si le slot est en dehors de la zone des arenes ou vide.
     */
    public String getArenaNameAt(int slot) {
        if (slot < 0 || slot >= MAX_ARENA_SLOTS) return null;
        List<Arena> list = new ArrayList<>(plugin.getArenaManager().getAll());
        if (slot >= list.size()) return null;
        return list.get(slot).getName();
    }
}
