package com.push.plugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Gere les statistiques persistantes des joueurs (victoires, eliminations, degats totaux),
 * stockees dans stats.yml.
 */
public class StatsManager {

    private final PushPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public StatsManager(PushPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Impossible de creer stats.yml", e);
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        if (config == null) return;
        try { config.save(file); } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Impossible de sauvegarder stats.yml", e);
        }
    }

    private String basePath(UUID uuid) { return "players." + uuid; }

    public void addKill(UUID uuid, String name) {
        int kills = getKills(uuid) + 1;
        config.set(basePath(uuid) + ".kills", kills);
        config.set(basePath(uuid) + ".name", name);
        save();
    }

    public void addWin(UUID uuid, String name) {
        int wins = getWins(uuid) + 1;
        config.set(basePath(uuid) + ".wins", wins);
        config.set(basePath(uuid) + ".name", name);
        save();
    }

    /** Une "poussee" = une elimination par le vide creditee a ce joueur. */
    public void addPush(UUID uuid, String name) {
        int pushes = getPushes(uuid) + 1;
        config.set(basePath(uuid) + ".pushes", pushes);
        config.set(basePath(uuid) + ".name", name);
        save();
    }

    /** Ajoute des degats au total cumule d'un joueur pour toutes ses parties. */
    public void addDamage(UUID uuid, String name, double amount) {
        double total = getDamage(uuid) + amount;
        config.set(basePath(uuid) + ".damage", total);
        config.set(basePath(uuid) + ".name", name);
        save();
    }

    public int getKills(UUID uuid) { return config.getInt(basePath(uuid) + ".kills", 0); }
    public int getWins(UUID uuid) { return config.getInt(basePath(uuid) + ".wins", 0); }
    public double getDamage(UUID uuid) { return config.getDouble(basePath(uuid) + ".damage", 0.0); }
    public int getPushes(UUID uuid) { return config.getInt(basePath(uuid) + ".pushes", 0); }

    public UUID findUuidByName(String name) {
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) return null;
        for (String key : players.getKeys(false)) {
            String stored = players.getString(key + ".name", "");
            if (stored.equalsIgnoreCase(name)) {
                try { return UUID.fromString(key); } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    public enum StatType { KILLS, WINS, DAMAGE, PUSHES }

    public record LeaderboardEntry(String name, double value) {}

    /** Retourne le top N des joueurs pour la stat demandee, tries par valeur decroissante. */
    public List<LeaderboardEntry> getTopPlayers(StatType type, int limit) {
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) return Collections.emptyList();

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (String key : players.getKeys(false)) {
            String name = players.getString(key + ".name", "Inconnu");
            double value = switch (type) {
                case KILLS  -> players.getInt(key + ".kills", 0);
                case WINS   -> players.getInt(key + ".wins", 0);
                case DAMAGE -> players.getDouble(key + ".damage", 0.0);
                case PUSHES -> players.getInt(key + ".pushes", 0);
            };
            entries.add(new LeaderboardEntry(name, value));
        }
        entries.sort(Comparator.comparingDouble(LeaderboardEntry::value).reversed());
        return entries.stream().limit(limit).collect(Collectors.toList());
    }
}
