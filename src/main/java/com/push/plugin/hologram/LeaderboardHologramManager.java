package com.push.plugin.hologram;

import com.push.plugin.PushPlugin;
import com.push.plugin.StatsManager;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Hologrammes de classement summonables (voir /p holo), affichant le top des joueurs
 * de Push pour une categorie donnee (kills, poussees, degats, victoires ou niveau).
 *
 * Chaque hologramme est une pile d'ArmorStand invisibles (une entite par ligne, comme
 * les plugins d'hologrammes classiques) : pas de dependance externe necessaire.
 * La position et la categorie de chaque hologramme sont persistees dans holograms.yml ;
 * les entites elles-memes sont recreees a chaque demarrage du serveur.
 */
public class LeaderboardHologramManager {

    /** Categories de classement disponibles pour un hologramme. */
    public enum Category {
        KILLS("Eliminations", StatsManager.StatType.KILLS),
        PUSHES("Poussees", StatsManager.StatType.PUSHES),
        DAMAGE("Degats", StatsManager.StatType.DAMAGE),
        WINS("Victoires", StatsManager.StatType.WINS),
        LEVEL("Niveaux", null);

        final String label;
        final StatsManager.StatType statType;

        Category(String label, StatsManager.StatType statType) {
            this.label = label;
            this.statType = statType;
        }

        public static Category fromString(String s) {
            for (Category c : values()) {
                if (c.name().equalsIgnoreCase(s)) return c;
            }
            return null;
        }
    }

    private record Hologram(String id, Location location, Category category) {}

    private static final int TOP_SIZE = 5;
    private static final double LINE_SPACING = 0.26;

    private final PushPlugin plugin;
    private final Map<String, Hologram> holograms = new LinkedHashMap<>();
    private final Map<String, List<ArmorStand>> spawnedStands = new HashMap<>();
    private BukkitTask refreshTask;

    public LeaderboardHologramManager(PushPlugin plugin) {
        this.plugin = plugin;
    }

    // ================= Cycle de vie =================

    public void loadAll() {
        holograms.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("holograms");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            String worldName = sec.getString("world");
            World world = worldName != null ? Bukkit.getWorld(worldName) : null;
            if (world == null) continue;

            Location loc = new Location(world, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));
            Category category = Category.fromString(sec.getString("category", "KILLS"));
            if (category == null) category = Category.KILLS;

            holograms.put(id, new Hologram(id, loc, category));
        }

        spawnAll();
    }

    public void saveAll() {
        plugin.getConfig().set("holograms", null);
        for (Hologram holo : holograms.values()) {
            String path = "holograms." + holo.id();
            plugin.getConfig().set(path + ".world", holo.location().getWorld().getName());
            plugin.getConfig().set(path + ".x", holo.location().getX());
            plugin.getConfig().set(path + ".y", holo.location().getY());
            plugin.getConfig().set(path + ".z", holo.location().getZ());
            plugin.getConfig().set(path + ".category", holo.category().name());
        }
        plugin.saveConfig();
    }

    public void startRefreshTask() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 100L);
    }

    /** Supprime toutes les entites en jeu (les donnees restent en memoire/fichier). */
    public void despawnAll() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        for (List<ArmorStand> stands : spawnedStands.values()) {
            for (ArmorStand stand : stands) {
                if (stand != null && !stand.isDead()) stand.remove();
            }
        }
        spawnedStands.clear();
    }

    // ================= Gestion =================

    /** Cree un nouvel hologramme a l'emplacement donne. Renvoie son identifiant. */
    public String summon(Location location, Category category) {
        String id = "holo" + (holograms.size() + 1) + "_" + System.currentTimeMillis() % 100000;
        Hologram holo = new Hologram(id, location.clone(), category);
        holograms.put(id, holo);
        saveAll();
        spawn(holo);
        return id;
    }

    public boolean remove(String id) {
        Hologram holo = holograms.remove(id);
        if (holo == null) return false;
        List<ArmorStand> stands = spawnedStands.remove(id);
        if (stands != null) {
            for (ArmorStand stand : stands) {
                if (stand != null && !stand.isDead()) stand.remove();
            }
        }
        saveAll();
        return true;
    }

    public Collection<String> listIds() {
        return holograms.keySet();
    }

    public String describe(String id) {
        Hologram holo = holograms.get(id);
        if (holo == null) return null;
        Location l = holo.location();
        return holo.category().label + " @ " + l.getWorld().getName() + " "
                + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    // ================= Affichage =================

    private void spawnAll() {
        despawnAll();
        for (Hologram holo : holograms.values()) {
            spawn(holo);
        }
    }

    private void spawn(Hologram holo) {
        List<String> lines = buildLines(holo.category());
        List<ArmorStand> stands = new ArrayList<>();
        double y = holo.location().getY() + (lines.size() - 1) * LINE_SPACING;

        for (String line : lines) {
            Location lineLoc = holo.location().clone();
            lineLoc.setY(y);
            ArmorStand stand = (ArmorStand) holo.location().getWorld().spawnEntity(lineLoc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setInvulnerable(true);
            stand.setCustomNameVisible(true);
            stand.customName(net.kyori.adventure.text.Component.text(
                    ChatColor.translateAlternateColorCodes('&', line)));
            stands.add(stand);
            y -= LINE_SPACING;
        }
        spawnedStands.put(holo.id(), stands);
    }

    private void refreshAll() {
        for (Hologram holo : holograms.values()) {
            List<ArmorStand> stands = spawnedStands.get(holo.id());
            if (stands == null || stands.isEmpty() || stands.stream().anyMatch(s -> s == null || s.isDead())) {
                spawn(holo);
                continue;
            }
            List<String> lines = buildLines(holo.category());
            for (int i = 0; i < stands.size() && i < lines.size(); i++) {
                stands.get(i).customName(net.kyori.adventure.text.Component.text(
                        ChatColor.translateAlternateColorCodes('&', lines.get(i))));
            }
        }
    }

    private List<String> buildLines(Category category) {
        List<String> lines = new ArrayList<>();
        lines.add("&d&l\u2726 Push \u2014 Top " + category.label);
        lines.add("&8&m                    ");

        if (category == Category.LEVEL) {
            var top = plugin.getLevelManager().getTopPlayers(TOP_SIZE);
            if (top.isEmpty()) {
                lines.add("&7Aucune donnee pour l'instant");
            } else {
                String[] medals = {"&6\u2661", "&7\u2661", "&c\u2661", "&f\u2022", "&f\u2022"};
                for (int i = 0; i < top.size(); i++) {
                    var e = top.get(i);
                    lines.add(medals[Math.min(i, medals.length - 1)] + " &f" + e.name() + " &7\u2014 &dNv." + e.level());
                }
            }
        } else {
            var top = plugin.getStatsManager().getTopPlayers(category.statType, TOP_SIZE);
            if (top.isEmpty()) {
                lines.add("&7Aucune donnee pour l'instant");
            } else {
                String[] medals = {"&6\u2661", "&7\u2661", "&c\u2661", "&f\u2022", "&f\u2022"};
                for (int i = 0; i < top.size(); i++) {
                    var e = top.get(i);
                    String valueStr = category == Category.DAMAGE
                            ? String.valueOf(Math.round(e.value()))
                            : String.valueOf((int) e.value());
                    lines.add(medals[Math.min(i, medals.length - 1)] + " &f" + e.name() + " &7\u2014 &d" + valueStr);
                }
            }
        }
        return lines;
    }
}
