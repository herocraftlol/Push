package com.push.plugin.levels;

import com.push.plugin.PushPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Systeme de niveau exclusif a Push : les joueurs gagnent de l'XP en tuant des
 * adversaires, en les poussant dans le vide, en infligeant des degats, en jouant
 * (temps de jeu) et en remportant des parties. Monter de niveau deverrouille des
 * cosmetiques d'elimination (voir PushCosmetic), choisis avec /p cosmetics.
 *
 * Persiste dans plugins/Push/levels.yml : XP cumulee et cosmetique equipe par joueur.
 */
public class LevelManager {

    private final PushPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    /** XP requise pour passer du niveau N au niveau N+1 : base + (N-1) * increment. */
    private static final double BASE_XP = 100.0;
    private static final double XP_INCREMENT = 25.0;

    public LevelManager(PushPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "levels.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Impossible de creer levels.yml", e);
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        if (config == null) return;
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Impossible de sauvegarder levels.yml", e);
        }
    }

    private String basePath(UUID uuid) {
        return "players." + uuid;
    }

    // ================= XP =================

    public double getXp(UUID uuid) {
        return config.getDouble(basePath(uuid) + ".xp", 0.0);
    }

    /** XP totale necessaire pour atteindre EXACTEMENT le debut du niveau donne. */
    public double xpForLevel(int level) {
        double total = 0.0;
        double req = BASE_XP;
        for (int i = 1; i < level; i++) {
            total += req;
            req += XP_INCREMENT;
        }
        return total;
    }

    public int getLevel(UUID uuid) {
        return levelForXp(getXp(uuid));
    }

    private int levelForXp(double xp) {
        int level = 1;
        double remaining = xp;
        double req = BASE_XP;
        // 500 niveaux de marge : largement suffisant, evite tout risque de boucle infinie
        for (int i = 0; i < 500 && remaining >= req; i++) {
            remaining -= req;
            level++;
            req += XP_INCREMENT;
        }
        return level;
    }

    /** XP actuelle / XP necessaire dans le niveau en cours (pour une barre de progression). */
    public double[] getProgressInLevel(UUID uuid) {
        double xp = getXp(uuid);
        int level = levelForXp(xp);
        double floor = xpForLevel(level);
        double req = BASE_XP + (level - 1) * XP_INCREMENT;
        return new double[]{xp - floor, req};
    }

    private void addXp(Player player, double amount) {
        if (amount <= 0 || player == null) return;
        UUID uuid = player.getUniqueId();
        int before = getLevel(uuid);
        double newXp = getXp(uuid) + amount;
        config.set(basePath(uuid) + ".xp", newXp);
        config.set(basePath(uuid) + ".name", player.getName());
        int after = levelForXp(newXp);
        save();

        if (after > before) {
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "\u2b06 Niveau superieur !"
                    + ChatColor.YELLOW + " Tu es maintenant niveau " + ChatColor.GOLD + after + ChatColor.YELLOW + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            for (PushCosmetic cosmetic : PushCosmetic.values()) {
                if (cosmetic.getRequiredLevel() == after) {
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "Nouveau cosmetique deverrouille : "
                            + ChatColor.WHITE + cosmetic.getDisplayName() + ChatColor.GRAY
                            + " (choisis-le avec /p cosmetics)");
                }
            }
        }
    }

    public void addKillXp(Player player) {
        addXp(player, plugin.getConfig().getDouble("levels.xp-per-kill", 15));
    }

    public void addPushXp(Player player) {
        addXp(player, plugin.getConfig().getDouble("levels.xp-per-push", 20));
    }

    public void addDamageXp(Player player, double damage) {
        addXp(player, damage * plugin.getConfig().getDouble("levels.xp-per-damage-point", 0.1));
    }

    public void addWinXp(Player player) {
        addXp(player, plugin.getConfig().getDouble("levels.xp-per-win", 100));
    }

    public void addPlaytimeXp(Player player) {
        addXp(player, plugin.getConfig().getDouble("levels.xp-per-minute-played", 2));
    }

    // ================= Cosmetiques =================

    public PushCosmetic getEquippedCosmetic(UUID uuid) {
        String id = config.getString(basePath(uuid) + ".cosmetic", PushCosmetic.NONE.getId());
        PushCosmetic cosmetic = PushCosmetic.fromId(id);
        // Un cosmetique deverrouille puis reverrouille (config modifiee) ne doit pas rester
        // equipe silencieusement : on retombe sur "Aucun" par securite.
        return cosmetic.isUnlockedAt(getLevel(uuid)) ? cosmetic : PushCosmetic.NONE;
    }

    /** Tente d'equiper un cosmetique. Renvoie false s'il n'est pas encore deverrouille. */
    public boolean equipCosmetic(Player player, PushCosmetic cosmetic) {
        if (!cosmetic.isUnlockedAt(getLevel(player.getUniqueId()))) {
            return false;
        }
        config.set(basePath(player.getUniqueId()) + ".cosmetic", cosmetic.getId());
        config.set(basePath(player.getUniqueId()) + ".name", player.getName());
        save();
        return true;
    }

    // ================= Classement =================

    public record LevelEntry(String name, int level, double xp) {}

    public List<LevelEntry> getTopPlayers(int limit) {
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) return Collections.emptyList();

        List<LevelEntry> entries = new ArrayList<>();
        for (String key : players.getKeys(false)) {
            String name = players.getString(key + ".name", "Inconnu");
            double xp = players.getDouble(key + ".xp", 0.0);
            entries.add(new LevelEntry(name, levelForXp(xp), xp));
        }
        entries.sort((a, b) -> {
            int cmp = Integer.compare(b.level(), a.level());
            return cmp != 0 ? cmp : Double.compare(b.xp(), a.xp());
        });
        return entries.stream().limit(limit).collect(Collectors.toList());
    }
}
