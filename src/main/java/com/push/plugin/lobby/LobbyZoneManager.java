package com.push.plugin.lobby;

import com.push.plugin.Arena;
import com.push.plugin.PushPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Gere le lobby d'attente construit en jeu par un admin.
 *
 * Principe : l'admin construit sa salle d'attente ou il veut (typiquement juste
 * au-dessus de la zone de jeu), delimite cette construction avec deux coins, et le
 * plugin en prend un "moule" (la liste des blocs qui la composent).
 *
 * Ensuite, la structure est automatiquement :
 *  - effacee au lancement d'une partie, pour que les joueurs en jeu ne la voient pas
 *    au-dessus de l'arene ;
 *  - reposee a l'identique des que l'arene rouvre (fin de partie, arene videe,
 *    redemarrage du serveur).
 *
 * Seuls les blocs enregistres dans le moule sont touches : ce qui a ete construit
 * ailleurs dans la zone apres la capture n'est jamais efface par erreur, et un
 * bloc casse pendant l'attente est remis en place a la reouverture suivante.
 *
 * Les moules sont stockes dans plugins/Push/lobbies/<arene>.yml, avec une palette
 * de types de blocs pour garder des fichiers compacts.
 */
public class LobbyZoneManager {

    /** Nombre de blocs traites par tick, pour ne pas figer le serveur sur une grosse structure. */
    private static final int BLOCKS_PER_TICK = 4000;

    /** Garde-fou : au-dela, on refuse de capturer (erreur de selection la plupart du temps). */
    public static final long MAX_VOLUME = 500_000L;

    private final PushPlugin plugin;
    private final File folder;

    /** Moule de chaque arene (cle = nom d'arene en minuscules). */
    private final Map<String, Snapshot> snapshots = new HashMap<>();

    /** Arenes dont la structure est actuellement posee dans le monde. */
    private final Set<String> currentlyPlaced = new HashSet<>();

    /** Tache de pose/effacement en cours par arene, pour pouvoir l'interrompre. */
    private final Map<String, BukkitRunnable> runningTasks = new HashMap<>();

    public LobbyZoneManager(PushPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "lobbies");
    }

    // ================= Modele =================

    /** Un bloc du moule, en coordonnees absolues, avec l'index de son type dans la palette. */
    private record StoredBlock(int x, int y, int z, int paletteIndex) {}

    /** Le moule complet d'un lobby : le monde, la palette de blocs et leurs positions. */
    private static class Snapshot {
        String worldName;
        List<String> palette = new ArrayList<>();
        List<StoredBlock> blocks = new ArrayList<>();
    }

    // ================= Chargement / sauvegarde =================

    public void loadAll() {
        snapshots.clear();
        if (!folder.exists()) return;

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String key = file.getName().substring(0, file.getName().length() - 4).toLowerCase();
            try {
                Snapshot snapshot = readSnapshot(file);
                if (snapshot != null) {
                    snapshots.put(key, snapshot);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Moule de lobby illisible : " + file.getName(), e);
            }
        }
    }

    private Snapshot readSnapshot(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("lobby");
        if (root == null) return null;

        Snapshot snapshot = new Snapshot();
        snapshot.worldName = root.getString("world", "");
        snapshot.palette = new ArrayList<>(root.getStringList("palette"));

        for (String encoded : root.getStringList("blocks")) {
            String[] parts = encoded.split(",");
            if (parts.length != 4) continue;
            try {
                snapshot.blocks.add(new StoredBlock(
                        Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
            } catch (NumberFormatException ignored) {
                // Ligne corrompue : on ignore ce bloc plutot que de perdre tout le moule
            }
        }
        return snapshot;
    }

    private void writeSnapshot(String key, Snapshot snapshot) {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Impossible de creer le dossier lobbies/");
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("lobby.world", snapshot.worldName);
        config.set("lobby.palette", snapshot.palette);

        List<String> encoded = new ArrayList<>(snapshot.blocks.size());
        for (StoredBlock b : snapshot.blocks) {
            encoded.add(b.x() + "," + b.y() + "," + b.z() + "," + b.paletteIndex());
        }
        config.set("lobby.blocks", encoded);

        try {
            config.save(new File(folder, key + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Impossible de sauvegarder le moule de lobby " + key, e);
        }
    }

    /** Supprime le moule d'une arene (appele quand l'arene est supprimee). */
    public void deleteSnapshot(String arenaName) {
        String key = arenaName.toLowerCase();
        snapshots.remove(key);
        currentlyPlaced.remove(key);
        File file = new File(folder, key + ".yml");
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Impossible de supprimer le moule de lobby " + key);
        }
    }

    // ================= Etat =================

    public boolean hasSnapshot(Arena arena) {
        return snapshots.containsKey(arena.getName().toLowerCase());
    }

    /** Nombre de blocs enregistres dans le moule (0 si aucun moule). */
    public int getBlockCount(Arena arena) {
        Snapshot snapshot = snapshots.get(arena.getName().toLowerCase());
        return snapshot == null ? 0 : snapshot.blocks.size();
    }

    public boolean isPlaced(Arena arena) {
        return currentlyPlaced.contains(arena.getName().toLowerCase());
    }

    // ================= Capture =================

    /**
     * Enregistre la structure actuellement presente entre les deux coins de la zone de
     * lobby de l'arene. Les blocs d'air sont ignores : seule la construction est retenue.
     *
     * @return le nombre de blocs captures, ou -1 si la zone est invalide/trop grande
     */
    public int capture(Arena arena) {
        if (!arena.hasLobbyZone()) return -1;
        if (arena.getState() != Arena.State.WAITING) return -2;
        if (arena.getLobbyZoneVolume() > MAX_VOLUME) return -1;

        Location p1 = arena.getLobbyZonePos1();
        Location p2 = arena.getLobbyZonePos2();
        World world = p1.getWorld();
        if (world == null) return -1;

        Snapshot snapshot = new Snapshot();
        snapshot.worldName = world.getName();

        Map<String, Integer> paletteIndex = new HashMap<>();

        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.AIR) continue;

                    String data = block.getBlockData().getAsString();
                    Integer index = paletteIndex.get(data);
                    if (index == null) {
                        index = snapshot.palette.size();
                        snapshot.palette.add(data);
                        paletteIndex.put(data, index);
                    }
                    snapshot.blocks.add(new StoredBlock(x, y, z, index));
                }
            }
        }

        String key = arena.getName().toLowerCase();
        snapshots.put(key, snapshot);
        currentlyPlaced.add(key); // la structure est, par definition, presente au moment de la capture
        writeSnapshot(key, snapshot);
        return snapshot.blocks.size();
    }

    // ================= Pose / effacement =================

    /**
     * Efface la structure du lobby (lancement de partie). Seuls les blocs du moule sont
     * remplaces par de l'air, et uniquement s'ils correspondent encore a la structure.
     */
    public void clear(Arena arena) {
        String key = arena.getName().toLowerCase();
        Snapshot snapshot = snapshots.get(key);
        if (snapshot == null || !currentlyPlaced.contains(key)) return;

        World world = Bukkit.getWorld(snapshot.worldName);
        if (world == null) {
            plugin.getLogger().warning("Monde introuvable pour le lobby de " + arena.getName()
                    + " : " + snapshot.worldName);
            return;
        }

        currentlyPlaced.remove(key);

        // De haut en bas : evite de faire tomber le sable/gravier et de casser les
        // blocs qui ont besoin d'un support (torches, dalles, pancartes...).
        List<StoredBlock> ordered = new ArrayList<>(snapshot.blocks);
        ordered.sort(Comparator.comparingInt(StoredBlock::y).reversed());

        BlockData air = Bukkit.createBlockData(Material.AIR);
        process(key, ordered, block -> world.getBlockAt(block.x(), block.y(), block.z())
                .setBlockData(air, false));
    }

    /**
     * Repose la structure du lobby a l'identique (reouverture de l'arene).
     * Sans effet si elle est deja en place.
     */
    public void paste(Arena arena) {
        String key = arena.getName().toLowerCase();
        Snapshot snapshot = snapshots.get(key);
        if (snapshot == null || currentlyPlaced.contains(key)) return;

        World world = Bukkit.getWorld(snapshot.worldName);
        if (world == null) {
            plugin.getLogger().warning("Monde introuvable pour le lobby de " + arena.getName()
                    + " : " + snapshot.worldName);
            return;
        }

        currentlyPlaced.add(key);

        // De bas en haut : les blocs qui ont besoin d'un support sont poses apres lui.
        List<StoredBlock> ordered = new ArrayList<>(snapshot.blocks);
        ordered.sort(Comparator.comparingInt(StoredBlock::y));

        process(key, ordered, block -> {
            String data = snapshot.palette.get(block.paletteIndex());
            world.getBlockAt(block.x(), block.y(), block.z())
                    .setBlockData(Bukkit.createBlockData(data), false);
        });
    }

    /**
     * Applique une operation a tous les blocs, par paquets de BLOCKS_PER_TICK, pour
     * qu'une grosse structure ne provoque pas de pic de lag sur un seul tick.
     */
    private void process(String key, List<StoredBlock> blocks, java.util.function.Consumer<StoredBlock> action) {
        // Une operation encore en cours sur cette arene est abandonnee : la nouvelle
        // (par exemple un effacement au lancement d'une partie) fait autorite.
        BukkitRunnable previous = runningTasks.remove(key);
        if (previous != null) {
            previous.cancel();
        }

        if (blocks.isEmpty()) return;

        // Une structure modeste est traitee immediatement : pas d'attente d'un tick
        // entre la fin de partie et la reapparition du lobby.
        if (blocks.size() <= BLOCKS_PER_TICK) {
            for (StoredBlock block : blocks) {
                action.accept(block);
            }
            return;
        }

        BukkitRunnable task = new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                int end = Math.min(index + BLOCKS_PER_TICK, blocks.size());
                for (; index < end; index++) {
                    action.accept(blocks.get(index));
                }
                if (index >= blocks.size()) {
                    runningTasks.remove(key);
                    cancel();
                }
            }
        };
        runningTasks.put(key, task);
        task.runTaskTimer(plugin, 0L, 1L);
    }

    /** Repose les lobbies de toutes les arenes ouvertes (au demarrage du serveur). */
    public void restoreAllOpenArenas() {
        for (Arena arena : plugin.getArenaManager().getAll()) {
            if (arena.getState() == Arena.State.WAITING && hasSnapshot(arena)) {
                // Au demarrage, on ignore l'etat memorise : la structure peut avoir ete
                // laissee effacee par un arret brutal du serveur en pleine partie.
                currentlyPlaced.remove(arena.getName().toLowerCase());
                paste(arena);
            }
        }
    }
}
