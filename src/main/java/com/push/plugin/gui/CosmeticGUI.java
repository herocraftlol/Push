package com.push.plugin.gui;

import com.push.plugin.PushPlugin;
import com.push.plugin.levels.LevelManager;
import com.push.plugin.levels.PushCosmetic;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI de selection des cosmetiques d'elimination exclusifs a Push (voir PushCosmetic).
 * Un cosmetique deverrouille peut etre equipe d'un clic ; un cosmetique pas encore
 * atteint est affiche grise avec le niveau requis.
 */
public class CosmeticGUI {

    public static final String GUI_TITLE = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "\u2726 Cosmetiques Push";
    private static final int GUI_SIZE = 27;

    private final PushPlugin plugin;

    public CosmeticGUI(PushPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        player.openInventory(build(player));
    }

    public boolean isCosmeticGuiTitle(String title) {
        return GUI_TITLE.equals(title);
    }

    private Inventory build(Player player) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);
        LevelManager levels = plugin.getLevelManager();
        int level = levels.getLevel(player.getUniqueId());
        PushCosmetic equipped = levels.getEquippedCosmetic(player.getUniqueId());

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < GUI_SIZE; i++) inv.setItem(i, filler);

        PushCosmetic[] values = PushCosmetic.values();
        for (int i = 0; i < values.length && i < GUI_SIZE; i++) {
            inv.setItem(i, buildItem(values[i], level, equipped == values[i]));
        }
        return inv;
    }

    private ItemStack buildItem(PushCosmetic cosmetic, int level, boolean equipped) {
        boolean unlocked = cosmetic.isUnlockedAt(level);
        Material material = unlocked ? iconFor(cosmetic) : Material.GRAY_DYE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        ChatColor nameColor = equipped ? ChatColor.GREEN : (unlocked ? ChatColor.LIGHT_PURPLE : ChatColor.GRAY);
        meta.setDisplayName(nameColor + "" + ChatColor.BOLD + cosmetic.getDisplayName()
                + (equipped ? ChatColor.GREEN + " (equipe)" : ""));

        List<String> lore = new ArrayList<>();
        lore.add("");
        if (unlocked) {
            lore.add(ChatColor.GRAY + "Joue a chaque elimination.");
            lore.add("");
            lore.add(equipped ? ChatColor.GREEN + "\u2714 Deja equipe" : ChatColor.YELLOW + "\u25B6 Clique pour equiper");
        } else {
            lore.add(ChatColor.RED + "Verrouille \u2014 niveau " + cosmetic.getRequiredLevel() + " requis");
            lore.add(ChatColor.GRAY + "(tu es niveau " + level + ")");
        }
        meta.setLore(lore);
        if (unlocked) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }
        if (equipped) meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
        return item;
    }

    private Material iconFor(PushCosmetic cosmetic) {
        return switch (cosmetic) {
            case NONE -> Material.BARRIER;
            case SPARKS -> Material.GLOWSTONE_DUST;
            case FLAMES -> Material.BLAZE_POWDER;
            case EXPLOSION -> Material.TNT;
            case LIGHTNING -> Material.TRIDENT;
            case FIREWORK -> Material.FIREWORK_ROCKET;
            case LEGENDARY -> Material.NETHER_STAR;
        };
    }

    public PushCosmetic getCosmeticAt(int slot) {
        PushCosmetic[] values = PushCosmetic.values();
        return slot >= 0 && slot < values.length ? values[slot] : null;
    }
}
