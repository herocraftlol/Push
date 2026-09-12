package com.push.plugin;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import com.push.plugin.chat.QuickMessage;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gere la collection d'arenes : creation, suppression, persistance dans config.yml,
 * cycle de vie des parties (lobby -> countdown -> demarrage -> point -> reset), et teleportations.
 */
public class ArenaManager {

    private final PushPlugin plugin;
    private final Map<String, Arena> arenas = new HashMap<>();

    // Taches planifiees par arene (pour pouvoir les annuler)
    private final Map<String, BukkitTaskHandle> endTasks = new HashMap<>();
    // Countdown de debut par arene (pour l'annuler si besoin)
    private final Map<String, Integer> startCountdownTasks = new HashMap<>();
    // Countdown de reprise apres point par arene
    private final Map<String, Integer> pointResumeTasks = new HashMap<>();
    // Flag : arene en pause apres un point (interdit les kills/moves pendant le countdown)
    private final Set<String> arenasPaused = new HashSet<>();
    // Secondes restantes des countdowns en cours, pour pouvoir les afficher sur le sidebar
    private final Map<String, Integer> startCountdownRemaining = new HashMap<>();
    private final Map<String, Integer> resumeCountdownRemaining = new HashMap<>();
    // Fin du temps de recharge de l'arc par joueur (horodatage en millisecondes)
    private final Map<UUID, Long> bowCooldownUntil = new HashMap<>();

    public ArenaManager(PushPlugin plugin) {
        this.plugin = plugin;
    }

    // ================= Persistance =================

    public void loadAll() {
        arenas.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("arenas");
        if (root == null) return;

        for (String name : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) continue;

            Arena arena = new Arena(name);

            if (sec.contains("lobby")) {
                arena.setLobbyLocation(deserializeLocation(sec.getConfigurationSection("lobby")));
            }
            if (sec.contains("voidY")) {
                arena.setVoidY(sec.getDouble("voidY"));
            }
            if (sec.contains("zonePos1")) {
                arena.setZonePos1(deserializeLocation(sec.getConfigurationSection("zonePos1")));
            }
            if (sec.contains("zonePos2")) {
                arena.setZonePos2(deserializeLocation(sec.getConfigurationSection("zonePos2")));
            }
            if (sec.contains("spectatorSpawn")) {
                arena.setSpectatorSpawn(deserializeLocation(sec.getConfigurationSection("spectatorSpawn")));
            }
            if (sec.contains("lobbyZonePos1")) {
                arena.setLobbyZonePos1(deserializeLocation(sec.getConfigurationSection("lobbyZonePos1")));
            }
            if (sec.contains("lobbyZonePos2")) {
                arena.setLobbyZonePos2(deserializeLocation(sec.getConfigurationSection("lobbyZonePos2")));
            }
            arena.setSpectatingEnabled(sec.getBoolean("spectatingEnabled", true));
            arena.setTeamCount(sec.getInt("teamCount", 2));
            arena.setTeamSize(sec.getInt("teamSize", 8));
            arena.setMinPlayersToStart(sec.getInt("minPlayersToStart", 2));

            ConfigurationSection spawns = sec.getConfigurationSection("teamSpawns");
            if (spawns != null) {
                for (String idxStr : spawns.getKeys(false)) {
                    int idx = Integer.parseInt(idxStr);
                    ConfigurationSection teamSec = spawns.getConfigurationSection(idxStr);
                    if (teamSec == null) continue;

                    // Nouveau format : arenas.<nom>.teamSpawns.<team>.<spawnNumero>.x/y/z/world
                    // Ancien format (retrocompatibilite) : arenas.<nom>.teamSpawns.<team>.x/y/z/world
                    if (teamSec.contains("world")) {
                        Location loc = deserializeLocation(teamSec);
                        arena.setSpawn(idx, 1, loc);
                    } else {
                        List<String> spawnKeys = new ArrayList<>(teamSec.getKeys(false));
                        spawnKeys.sort(Comparator.comparingInt(Integer::parseInt));
                        int spawnNumber = 1;
                        for (String spawnKey : spawnKeys) {
                            Location loc = deserializeLocation(teamSec.getConfigurationSection(spawnKey));
                            arena.setSpawn(idx, spawnNumber, loc);
                            spawnNumber++;
                        }
                    }
                }
            }

            arenas.put(name.toLowerCase(), arena);
        }
    }

    public void saveAll() {
        for (Arena arena : arenas.values()) {
            saveArena(arena);
        }
        plugin.saveConfig();
    }

    public void saveArena(Arena arena) {
        String path = "arenas." + arena.getName();
        plugin.getConfig().set(path + ".teamCount", arena.getTeamCount());
        plugin.getConfig().set(path + ".teamSize", arena.getTeamSize());
        plugin.getConfig().set(path + ".minPlayersToStart", arena.getMinPlayersToStart());
        plugin.getConfig().set(path + ".spectatingEnabled", arena.isSpectatingEnabled());
        if (arena.getLobbyLocation() != null) {
            serializeLocation(path + ".lobby", arena.getLobbyLocation());
        }
        if (arena.hasVoidY()) {
            plugin.getConfig().set(path + ".voidY", arena.getVoidY());
        }
        if (arena.getZonePos1() != null) {
            serializeLocation(path + ".zonePos1", arena.getZonePos1());
        }
        if (arena.getZonePos2() != null) {
            serializeLocation(path + ".zonePos2", arena.getZonePos2());
        }
        if (arena.getSpectatorSpawn() != null) {
            serializeLocation(path + ".spectatorSpawn", arena.getSpectatorSpawn());
        }
        if (arena.getLobbyZonePos1() != null) {
            serializeLocation(path + ".lobbyZonePos1", arena.getLobbyZonePos1());
        }
        if (arena.getLobbyZonePos2() != null) {
            serializeLocation(path + ".lobbyZonePos2", arena.getLobbyZonePos2());
        }
        plugin.getConfig().set(path + ".teamSpawns", null);
        for (Map.Entry<Integer, List<Location>> e : arena.getTeamSpawns().entrySet()) {
            List<Location> list = e.getValue();
            for (int i = 0; i < list.size(); i++) {
                serializeLocation(path + ".teamSpawns." + e.getKey() + "." + (i + 1), list.get(i));
            }
        }
        plugin.saveConfig();
    }

    public void deleteArenaFromConfig(String name) {
        plugin.getConfig().set("arenas." + name, null);
        plugin.saveConfig();
        plugin.getLobbyZoneManager().deleteSnapshot(name);
    }

    private void serializeLocation(String path, Location loc) {
        plugin.getConfig().set(path + ".world", loc.getWorld().getName());
        plugin.getConfig().set(path + ".x", loc.getX());
        plugin.getConfig().set(path + ".y", loc.getY());
        plugin.getConfig().set(path + ".z", loc.getZ());
        plugin.getConfig().set(path + ".yaw", loc.getYaw());
        plugin.getConfig().set(path + ".pitch", loc.getPitch());
    }

    private Location deserializeLocation(ConfigurationSection sec) {
        if (sec == null) return null;
        World world = Bukkit.getWorld(sec.getString("world", ""));
        if (world == null) return null;
        return new Location(world,
                sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                (float) sec.getDouble("yaw"), (float) sec.getDouble("pitch"));
    }

    // ================= CRUD arenes =================

    public Arena create(String name) {
        String key = name.toLowerCase();
        if (arenas.containsKey(key)) return null;
        Arena arena = new Arena(name);
        arenas.put(key, arena);
        saveArena(arena);
        return arena;
    }

    public boolean delete(String name) {
        String key = name.toLowerCase();
        Arena arena = arenas.remove(key);
        if (arena == null) return false;
        deleteArenaFromConfig(arena.getName());
        return true;
    }

    public Arena get(String name) {
        return arenas.get(name.toLowerCase());
    }

    public Collection<Arena> getAll() {
        return arenas.values();
    }

    /** Trouve l'arene dans laquelle se trouve un joueur (lobby ou partie), ou null. */
    public Arena getArenaOf(UUID uuid) {
        for (Arena arena : arenas.values()) {
            if (arena.isPlaying(uuid)) return arena;
        }
        return null;
    }

    /** Retourne true si l'arene est en pause apres un point (respawn countdown). */
    public boolean isArenaPaused(Arena arena) {
        return arenasPaused.contains(arena.getName().toLowerCase());
    }

    /** Retourne true si le compte a rebours pre-partie (30s) est en cours pour cette arene. */
    public boolean isPreGameCountdownRunning(Arena arena) {
        return startCountdownTasks.containsKey(arena.getName().toLowerCase());
    }

    // ================= Cycle de vie =================

    /**
     * Fait rejoindre un joueur dans le lobby d'une arene.
     * Assigne automatiquement l'equipe la moins remplie.
     */
    public boolean joinLobby(Player player, Arena arena) {
        if (!arena.isFullyConfigured()) {
            player.sendMessage(ChatColor.RED + "Cette arene n'est pas entierement configuree.");
            return false;
        }
        if (arena.getState() != Arena.State.WAITING) {
            player.sendMessage(ChatColor.RED + "Une partie est deja en cours sur cette arene.");
            return false;
        }
        if (getArenaOf(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "Tu es deja dans une arene.");
            return false;
        }
        int team = arena.findAvailableTeam();
        if (team == -1) {
            player.sendMessage(ChatColor.RED + "Cette arene est pleine.");
            return false;
        }

        arena.setReturnLocation(player.getUniqueId(), player.getLocation());
        arena.addPlayer(player.getUniqueId(), team);

        resetPlayerForLobby(player);
        player.teleport(arena.getLobbyLocation());

        plugin.getScoreboardManager().show(player);
        updateSidebar(arena);

        ChatColor color = getTeamColor(arena, team);
        String teamName = getTeamDisplayName(arena, team);
        player.sendMessage(ChatColor.GREEN + "Tu as rejoint l'arene " + ChatColor.YELLOW + arena.getName()
                + ChatColor.GREEN + " dans l'equipe " + color + teamName + ChatColor.GREEN + " !");

        broadcastToArena(arena, ChatColor.GRAY + player.getName() + " a rejoint le lobby ("
                + arena.countPlayers() + "/" + arena.getMaxPlayers() + ")");

        giveAdminStartItem(player, arena);

        if (arena.countPlayers() >= arena.getMinPlayersToStart()) {
            startPreGameCountdown(arena);
        }
        return true;
    }

    public void leave(Player player) {
        Arena arena = getArenaOf(player.getUniqueId());
        if (arena == null) {
            player.sendMessage(ChatColor.RED + "Tu n'es dans aucune arene.");
            return;
        }
        removePlayerFromArena(player, arena, true);
    }

    /**
     * Retire un joueur d'une arene : nettoie l'equipe scoreboard, l'inventaire,
     * et le teleporte a sa position d'origine si teleportBack est vrai.
     */
    public void removePlayerFromArena(Player player, Arena arena, boolean teleportBack) {
        UUID uuid = player.getUniqueId();
        Location ret = arena.getReturnLocation(uuid);
        arena.removePlayer(uuid);
        resetPlayerInventory(player);
        clearBowCooldown(player);
        resetTabList(player);

        plugin.getScoreboardManager().remove(player);

        if (teleportBack && ret != null) {
            player.teleport(ret);
        }

        if (arena.getState() == Arena.State.RUNNING) {
            checkForWinnerAfterDisconnect(arena);
        }

        if (arena.countPlayers() == 0 && arena.getState() != Arena.State.WAITING) {
            cancelStartCountdown(arena);
            arenasPaused.remove(arena.getName().toLowerCase());
            removeAllSpectators(arena);
            reopenArena(arena);
        } else if (arena.getState() == Arena.State.WAITING) {
            // Si le countdown etait lance mais qu'il n'y a plus assez de joueurs, on l'annule
            if (arena.countPlayers() < arena.getMinPlayersToStart()) {
                cancelStartCountdown(arena);
            }
            updateSidebar(arena);
        }
    }

    private void giveAdminStartItem(Player player, Arena arena) {
        if (player.hasPermission("push.admin")) {
            ItemStack diamond = new ItemStack(Material.DIAMOND, 1);
            ItemMeta meta = diamond.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + "Lancer la partie maintenant");
            meta.setLore(List.of(ChatColor.GRAY + "Clique pour demarrer la partie immediatement."));
            diamond.setItemMeta(meta);
            player.getInventory().setItem(0, diamond);
        }
    }

    private void resetPlayerForLobby(Player player) {
        player.getInventory().clear();
        clearArmor(player);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setGameMode(GameMode.ADVENTURE);
        for (PotionEffect eff : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(eff.getType());
        }
    }

    private void resetPlayerInventory(Player player) {
        player.getInventory().clear();
        clearArmor(player);
        player.setGameMode(GameMode.SURVIVAL);
        for (PotionEffect eff : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(eff.getType());
        }
    }

    /** Retire les 4 pieces d'armure du joueur. */
    private void clearArmor(Player player) {
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
    }

    /** Lance manuellement la partie (utilise par le diamant admin ou /p forcestart). */
    public void forceStart(Arena arena) {
        if (arena.getState() != Arena.State.WAITING) return;
        if (arena.countPlayers() < 1) return;
        // Annule le countdown en cours si present
        cancelStartCountdown(arena);
        startGame(arena);
    }

    // ================= Countdown pre-partie (30s) =================

    /**
     * Demarre le compte a rebours de 30s avant le debut de la partie.
     * Si un countdown est deja en cours pour cette arene, ne fait rien.
     */
    private void startPreGameCountdown(Arena arena) {
        String key = arena.getName().toLowerCase();
        if (startCountdownTasks.containsKey(key)) return; // deja en cours

        int[] remaining = {30};
        startCountdownRemaining.put(key, remaining[0]);
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            int r = remaining[0];
            startCountdownRemaining.put(key, Math.max(0, r));

            if (arena.getState() != Arena.State.WAITING || arena.countPlayers() == 0) {
                cancelStartCountdown(arena);
                return;
            }

            if (r <= 0) {
                cancelStartCountdown(arena);
                startGame(arena);
                return;
            }

            // Annonces aux moments cles
            if (r == 30 || r == 20 || r == 10 || r == 5 || r == 3 || r == 2 || r == 1) {
                broadcastToArena(arena, ChatColor.YELLOW + "La partie commence dans "
                        + ChatColor.GOLD + r + ChatColor.YELLOW + " seconde" + (r > 1 ? "s" : "") + " !");
                // Son de tick
                for (Player p : getOnlinePlayersInArena(arena)) {
                    if (r <= 5) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, r <= 3 ? 1.5f : 1.0f);
                    } else {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.8f);
                    }
                }
            }

            remaining[0]--;
        }, 0L, 20L);

        startCountdownTasks.put(key, taskId);
        broadcastToArena(arena, ChatColor.GREEN + "Assez de joueurs ! La partie commence dans "
                + ChatColor.GOLD + "30 secondes" + ChatColor.GREEN + ".");
    }

    private void cancelStartCountdown(Arena arena) {
        String key = arena.getName().toLowerCase();
        startCountdownRemaining.remove(key);
        Integer taskId = startCountdownTasks.remove(key);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    /** Secondes restantes avant le lancement automatique, ou -1 si aucun countdown en cours. */
    public int getStartCountdownRemaining(Arena arena) {
        return startCountdownRemaining.getOrDefault(arena.getName().toLowerCase(), -1);
    }

    /** Secondes restantes avant la reprise apres un point, ou -1 si aucune pause en cours. */
    public int getResumeCountdownRemaining(Arena arena) {
        return resumeCountdownRemaining.getOrDefault(arena.getName().toLowerCase(), -1);
    }

    // ================= Demarrage =================

    private void startGame(Arena arena) {
        if (arena.getState() != Arena.State.WAITING) return;
        arena.setState(Arena.State.RUNNING);
        arena.resetScores();
        arena.resetDamage();
        arena.resetMatchStats();
        arena.markStarted();

        for (Map.Entry<UUID, Integer> entry : new HashMap<>(arena.getPlayerTeamMap()).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            int team = entry.getValue();
            teleportToTeamSpawn(player, arena, team);
            giveKit(player, arena);
            plugin.getScoreboardManager().show(player);
        }

        // Les joueurs sont deja sur leurs spawns : on peut effacer la salle d'attente,
        // qui se trouve generalement juste au-dessus de la zone de jeu.
        plugin.getLobbyZoneManager().clear(arena);

        updateSidebar(arena);

        broadcastToArena(arena, ChatColor.GOLD + "La partie commence ! Premiere equipe a "
                + plugin.getConfig().getInt("points-to-win", 5) + " points gagne !");

        // Son de debut de partie
        for (Player p : getOnlinePlayersInArena(arena)) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        updateTabList(arena);
    }

    private void teleportToTeamSpawn(Player player, Arena arena, int team) {
        Location spawn = arena.getRandomSpawn(team);
        if (spawn != null) {
            player.teleport(spawn);
        }
    }

    public void giveKit(Player player, Arena arena) {
        player.getInventory().clear();
        clearArmor(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);

        // Un kit neuf remet toujours l'arc en etat de tirer immediatement
        clearBowCooldown(player);

        ItemStack sword = new ItemStack(Material.STONE_SWORD);
        ItemMeta swordMeta = sword.getItemMeta();
        swordMeta.setUnbreakable(true);
        swordMeta.setDisplayName(ChatColor.GRAY + "Epee d'arene");
        sword.setItemMeta(swordMeta);

        ItemStack bow = createArenaBow();

        // Munition : une seule fleche suffit avec INFINITY, mais on en met un paquet pour
        // rester tolerant si une fleche est malgre tout consommee par une version du serveur.
        ItemStack arrow = new ItemStack(Material.ARROW, 64);

        int team = arena.getTeamOf(player.getUniqueId());
        Color armorColor = chatColorToColor(getTeamColor(arena, team));
        ItemStack helmet = createColoredArmor(Material.LEATHER_HELMET, armorColor);
        ItemStack chestplate = createColoredArmor(Material.LEATHER_CHESTPLATE, armorColor);
        ItemStack leggings = createColoredArmor(Material.LEATHER_LEGGINGS, armorColor);
        ItemStack boots = createColoredArmor(Material.LEATHER_BOOTS, armorColor);

        player.getInventory().setItem(0, sword);
        player.getInventory().setItem(1, bow);
        player.getInventory().setItem(8, arrow);

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);
    }

    /**
     * Cree l'arc du kit. Il porte l'enchantement INFINITY : la fleche presente dans
     * l'inventaire n'est jamais consommee, ce qui garantit que le joueur puisse toujours
     * bander son arc (sans munition, l'arc est tout simplement inutilisable en jeu).
     */
    public static ItemStack createArenaBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta bowMeta = bow.getItemMeta();
        bowMeta.setUnbreakable(true);
        bowMeta.setDisplayName(ChatColor.GRAY + "Arc d'arene");
        bowMeta.addEnchant(Enchantment.INFINITY, 1, true);
        bowMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        bow.setItemMeta(bowMeta);
        return bow;
    }

    /**
     * Redonne uniquement l'arc et sa munition, sans toucher au reste de l'inventaire
     * ni a la vie du joueur (utilise par la verification periodique en cours de partie).
     */
    public void giveBowOnly(Player player) {
        player.getInventory().setItem(1, createArenaBow());
        ItemStack slot8 = player.getInventory().getItem(8);
        if (slot8 == null || slot8.getType() != Material.ARROW || slot8.getAmount() < 1) {
            player.getInventory().setItem(8, new ItemStack(Material.ARROW, 64));
        }
    }

    /**
     * Attribue l'XP de temps de jeu (voir LevelManager) a tous les joueurs actuellement
     * en partie (etat RUNNING), sur toutes les arenes. Appelee une fois par minute.
     */
    public void awardPlaytimeXp() {
        for (Arena arena : arenas.values()) {
            if (arena.getState() != Arena.State.RUNNING) continue;
            for (Player p : getOnlinePlayersInArena(arena)) {
                plugin.getLevelManager().addPlaytimeXp(p);
            }
        }
    }

    // ================= Messages rapides (quick-chat) =================

    /**
     * Remplace TEMPORAIREMENT l'inventaire d'un joueur par les 4 blocs de couleur
     * "message rapide" (voir QuickMessage), pendant la pause qui suit un point ou a
     * l'ecran de victoire. Le kit de combat est redonne au round suivant (giveKit).
     */
    public void giveQuickChatItems(Player player) {
        if (!plugin.getConfig().getBoolean("quick-chat.enabled", true)) return;
        player.getInventory().clear();
        for (QuickMessage message : QuickMessage.values()) {
            player.getInventory().setItem(message.getHotbarSlot(), message.createItem(player));
        }
    }

    /**
     * Envoie un message rapide dans le chat de l'arene. Chaque destinataire recoit le
     * texte traduit dans SA PROPRE langue (voir QuickMessage#getTextFor).
     */
    public void sendQuickChatMessage(Arena arena, Player sender, QuickMessage message) {
        for (UUID uuid : arena.getPlayerTeamMap().keySet()) {
            Player recipient = Bukkit.getPlayer(uuid);
            if (recipient == null) continue;
            String text = message.getTextFor(recipient);
            recipient.sendMessage(message.getColor() + sender.getName() + ChatColor.GRAY + " \u00BB "
                    + message.getColor() + text);
        }
        for (UUID uuid : arena.getSpectators()) {
            Player recipient = Bukkit.getPlayer(uuid);
            if (recipient == null) continue;
            String text = message.getTextFor(recipient);
            recipient.sendMessage(message.getColor() + sender.getName() + ChatColor.GRAY + " \u00BB "
                    + message.getColor() + text);
        }
    }

    /** Cree la fleche dediee d'arene. */
    public static ItemStack createArenaArrow() {
        ItemStack arrow = new ItemStack(Material.ARROW, 1);
        ItemMeta meta = arrow.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "Fleche d'arene");
        arrow.setItemMeta(meta);
        return arrow;
    }

    /** Cree une piece d'armure en cuir, teintee a la couleur de l'equipe, et incassable. */
    private ItemStack createColoredArmor(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        meta.setUnbreakable(true);
        meta.setDisplayName(ChatColor.GRAY + "Armure d'arene");
        item.setItemMeta(meta);
        return item;
    }

    /** Convertit une ChatColor en une couleur RGB exploitable pour teindre une armure en cuir. */
    private Color chatColorToColor(ChatColor chatColor) {
        return switch (chatColor) {
            case BLACK -> Color.fromRGB(0, 0, 0);
            case DARK_BLUE -> Color.fromRGB(0, 0, 170);
            case DARK_GREEN -> Color.fromRGB(0, 170, 0);
            case DARK_AQUA -> Color.fromRGB(0, 170, 170);
            case DARK_RED -> Color.fromRGB(170, 0, 0);
            case DARK_PURPLE -> Color.fromRGB(170, 0, 170);
            case GOLD -> Color.fromRGB(255, 170, 0);
            case GRAY -> Color.fromRGB(170, 170, 170);
            case DARK_GRAY -> Color.fromRGB(85, 85, 85);
            case BLUE -> Color.fromRGB(85, 85, 255);
            case GREEN -> Color.fromRGB(85, 255, 85);
            case AQUA -> Color.fromRGB(85, 255, 255);
            case RED -> Color.fromRGB(255, 85, 85);
            case LIGHT_PURPLE -> Color.fromRGB(255, 85, 255);
            case YELLOW -> Color.fromRGB(255, 255, 85);
            case WHITE -> Color.fromRGB(255, 255, 255);
            default -> Color.fromRGB(255, 255, 255);
        };
    }

    // ================= Point marque -> reteleportation + countdown 5s =================

    /**
     * Appelee quand un point est marque (kill ou chute dans le vide).
     * Reteleporte tout le monde a sa base, lance un countdown de 5s avant de reprendre.
     */
    public void addPointAndCheckWin(Arena arena, int scoringTeam, Player victim) {
        int pointsToWin = plugin.getConfig().getInt("points-to-win", 5);
        int newScore = arena.addPoint(scoringTeam);
        updateSidebar(arena);
        updateTabList(arena);

        ChatColor color = getTeamColor(arena, scoringTeam);
        String teamName = getTeamDisplayName(arena, scoringTeam);
        broadcastToArena(arena, color + teamName + ChatColor.WHITE + " marque un point ! ("
                + newScore + "/" + pointsToWin + ")");

        if (newScore >= pointsToWin) {
            endGame(arena, scoringTeam);
            return;
        }

        // La partie continue : reteleporter tout le monde et lancer le countdown 5s
        startPointResumeCountdown(arena);
    }

    /**
     * Reteleporte tous les joueurs a leur base et lance un compte a rebours de 5s
     * avant de les laisser jouer a nouveau.
     */
    private void startPointResumeCountdown(Arena arena) {
        String key = arena.getName().toLowerCase();

        // Annule un eventuel countdown precedent (ne devrait pas arriver mais securite)
        Integer oldTask = pointResumeTasks.remove(key);
        if (oldTask != null) Bukkit.getScheduler().cancelTask(oldTask);

        arenasPaused.add(key);

        // Reteleporter tous les joueurs et leur retirer le kit + immobiliser
        for (Map.Entry<UUID, Integer> entry : new HashMap<>(arena.getPlayerTeamMap()).entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;
            int team = entry.getValue();
            if (!p.isDead()) {
                teleportToTeamSpawn(p, arena, team);
            }
            // Retirer le kit de combat pendant la pause (ne peut pas combattre) et le
            // remplacer par les blocs de message rapide, comme dans HikaBrain.
            clearArmor(p);
            clearBowCooldown(p);
            if (!p.isDead()) {
                giveQuickChatItems(p);
            }
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setFireTicks(0);
            // Immobiliser le joueur pendant le countdown
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 4 * 20, 200, false, false, false));
            // Son de point marque
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        }

        int[] remaining = {Math.max(1, plugin.getConfig().getInt("round-resume-seconds", 3))};
        resumeCountdownRemaining.put(key, remaining[0]);
        // Utiliser un tableau pour capturer le taskId dans le lambda
        int[] taskIdHolder = {-1};

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            int r = remaining[0];
            resumeCountdownRemaining.put(key, Math.max(0, r));

            if (arena.getState() != Arena.State.RUNNING) {
                arenasPaused.remove(key);
                pointResumeTasks.remove(key);
                resumeCountdownRemaining.remove(key);
                Bukkit.getScheduler().cancelTask(taskIdHolder[0]);
                return;
            }

            if (r <= 0) {
                arenasPaused.remove(key);
                pointResumeTasks.remove(key);
                resumeCountdownRemaining.remove(key);
                // Retirer l'effet de ralentissement et redonner le kit a tout le monde
                for (UUID uuid : new ArrayList<>(arena.getPlayerTeamMap().keySet())) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.removePotionEffect(PotionEffectType.SLOWNESS);
                        // Un joueur encore sur l'ecran de mort recevra son kit au respawn :
                        // le lui donner maintenant le ferait reapparaitre les mains vides.
                        if (!p.isDead()) {
                            giveKit(p, arena);
                        }
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    }
                }
                broadcastToArena(arena, ChatColor.GREEN + "" + ChatColor.BOLD + "COMBAT !");
                updateTabList(arena);
                // Annuler la tache via son vrai ID capture
                Bukkit.getScheduler().cancelTask(taskIdHolder[0]);
                return;
            }

            // Annonce et son a chaque seconde
            broadcastToArena(arena, ChatColor.YELLOW + "Reprise dans " + ChatColor.GOLD + r
                    + ChatColor.YELLOW + " seconde" + (r > 1 ? "s" : "") + "...");
            for (Player p : getOnlinePlayersInArena(arena)) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, r == 1 ? 1.8f : 1.2f);
            }

            remaining[0]--;
        }, 20L, 20L);

        taskIdHolder[0] = taskId;
        pointResumeTasks.put(key, taskId);
    }

    /** Enregistre les degats infliges par une equipe et rafraichit le sidebar. Appele a chaque coup porte. */
    public void addDamage(Arena arena, int teamIndex, double amount) {
        if (teamIndex < 0 || amount <= 0) return;
        arena.addDamage(teamIndex, amount);
        updateSidebar(arena);
    }

    /** Enregistre les degats infliges par un joueur pour la partie en cours (sidebar). */
    public void addMatchDamage(Arena arena, Player damager, double amount) {
        if (amount <= 0) return;
        arena.addPlayerDamage(damager.getUniqueId(), amount);
    }

    /** Enregistre les degats infliges par un joueur dans ses stats persistantes. */
    public void addDamageToPlayerStats(org.bukkit.entity.Player damager, double amount) {
        plugin.getStatsManager().addDamage(damager.getUniqueId(), damager.getName(), amount);
    }

    /** Si une equipe entiere se deconnecte / quitte, on verifie s'il ne reste qu'une equipe vivante. */
    private void checkForWinnerAfterDisconnect(Arena arena) {
        Set<Integer> teamsWithPlayers = new HashSet<>();
        for (int team : arena.getPlayerTeamMap().values()) {
            teamsWithPlayers.add(team);
        }
        if (teamsWithPlayers.size() == 1) {
            int winner = teamsWithPlayers.iterator().next();
            endGame(arena, winner);
        } else if (teamsWithPlayers.isEmpty()) {
            arenasPaused.remove(arena.getName().toLowerCase());
            removeAllSpectators(arena);
            reopenArena(arena);
        }
    }

    private void endGame(Arena arena, int winningTeam) {
        if (arena.getState() != Arena.State.RUNNING) return;
        arena.setState(Arena.State.ENDING);

        // Annule le countdown de reprise si present
        String key = arena.getName().toLowerCase();
        Integer resumeTask = pointResumeTasks.remove(key);
        if (resumeTask != null) Bukkit.getScheduler().cancelTask(resumeTask);
        arenasPaused.remove(key);

        for (UUID uuid : arena.getPlayerTeamMap().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGameMode(GameMode.SPECTATOR);
                // Blocs de message rapide a l'ecran de victoire, comme pendant la pause
                giveQuickChatItems(p);
            }
        }

        ChatColor color = getTeamColor(arena, winningTeam);
        String teamName = getTeamDisplayName(arena, winningTeam);

        List<String> winnerNames = new ArrayList<>();
        for (UUID uuid : arena.getPlayersInTeam(winningTeam)) {
            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : "Inconnu";
            winnerNames.add(name);
            plugin.getStatsManager().addWin(uuid, name);
            if (p != null) plugin.getLevelManager().addWinXp(p);
        }

        broadcastToArena(arena, ChatColor.GOLD + "" + ChatColor.BOLD + "Victoire de l'equipe "
                + color + teamName + ChatColor.GOLD + " !");
        broadcastToArena(arena, color + "" + ChatColor.BOLD + teamName + ChatColor.GRAY
                + " : " + ChatColor.WHITE + String.join(ChatColor.GRAY + ", " + ChatColor.WHITE, winnerNames));

        // Son de victoire
        for (Player p : getOnlinePlayersInArena(arena)) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        updateSidebar(arena);

        updateTabList(arena);

        int delay = plugin.getConfig().getInt("end-delay-seconds", 5);
        BukkitTaskHandle handle = new BukkitTaskHandle();
        endTasks.put(arena.getName().toLowerCase(), handle);

        handle.taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            finishAndReset(arena);
            endTasks.remove(arena.getName().toLowerCase());
        }, delay * 20L);
    }

    private void finishAndReset(Arena arena) {
        // Les spectateurs sont renvoyes chez eux en meme temps que les joueurs
        removeAllSpectators(arena);

        for (UUID uuid : new ArrayList<>(arena.getPlayerTeamMap().keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                resetPlayerInventory(player);
                clearBowCooldown(player);
                plugin.getScoreboardManager().remove(player);
                Location ret = arena.getReturnLocation(uuid);
                if (ret != null) {
                    player.teleport(ret);
                }
                player.sendMessage(ChatColor.GREEN + "Tu as ete teleporte a ta position de depart.");
                resetTabList(player);
            }
        }
        reopenArena(arena);
    }

    /**
     * Remet l'arene a l'etat "ouverte" : scores et joueurs remis a zero, et surtout
     * remise en place de la salle d'attente construite en jeu, effacee au lancement.
     */
    private void reopenArena(Arena arena) {
        arena.resetAll();
        plugin.getLobbyZoneManager().paste(arena);
    }

    // ================= Sidebar =================

    /**
     * Rafraichit le sidebar de tous les joueurs et spectateurs de l'arene.
     * Le contenu et l'affichage sont geres par ScoreboardManager (un sidebar par joueur).
     */
    public void updateSidebar(Arena arena) {
        plugin.getScoreboardManager().updateArena(arena);
    }

    // ================= Temps de recharge de l'arc =================

    /** Retourne true si l'arc du joueur est encore en rechargement. */
    public boolean isBowOnCooldown(Player player) {
        Long until = bowCooldownUntil.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    /** Secondes entieres restantes avant que le joueur puisse retirer (0 si pret). */
    public int getBowCooldownRemaining(Player player) {
        Long until = bowCooldownUntil.get(player.getUniqueId());
        if (until == null) return 0;
        long remaining = until - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (int) Math.ceil(remaining / 1000.0);
    }

    /**
     * Demarre le rechargement de l'arc apres un tir. On s'appuie sur le cooldown d'item
     * natif de Minecraft (qui affiche lui-meme le balayage gris sur l'icone et empeche
     * de bander l'arc) : aucune tache repetee n'est necessaire, donc plus aucun risque
     * qu'un cooldown reste bloque et rende l'arc definitivement inutilisable.
     */
    public void startBowCooldown(Player player) {
        int seconds = Math.max(0, plugin.getConfig().getInt("bow-cooldown-seconds", 3));
        if (seconds == 0) {
            clearBowCooldown(player);
            return;
        }
        bowCooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        player.setCooldown(Material.BOW, seconds * 20);
    }

    /** Rend l'arc immediatement utilisable (kit neuf, respawn, fin de partie...). */
    public void clearBowCooldown(Player player) {
        bowCooldownUntil.remove(player.getUniqueId());
        if (player.isOnline()) {
            player.setCooldown(Material.BOW, 0);
        }
    }

    // ================= Mode spectateur =================

    /** Retourne l'arene que ce joueur observe en spectateur, ou null. */
    public Arena getSpectatedArena(UUID uuid) {
        for (Arena arena : arenas.values()) {
            if (arena.isSpectating(uuid)) return arena;
        }
        return null;
    }

    public boolean isSpectator(Player player) {
        return getSpectatedArena(player.getUniqueId()) != null;
    }

    /**
     * Fait passer un joueur en mode spectateur sur une arene : il voit la partie sans y
     * participer, avec le meme sidebar de scores que les joueurs qu'il observe.
     */
    public boolean addSpectator(Player player, Arena arena) {
        if (!arena.isSpectatingEnabled()) {
            player.sendMessage(ChatColor.RED + "Le mode spectateur est desactive sur cette arene.");
            return false;
        }
        if (!arena.isFullyConfigured()) {
            player.sendMessage(ChatColor.RED + "Cette arene n'est pas entierement configuree.");
            return false;
        }
        if (getArenaOf(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "Tu joues deja dans une arene. Utilise /p leave d'abord.");
            return false;
        }
        Arena current = getSpectatedArena(player.getUniqueId());
        if (current != null) {
            if (current == arena) {
                player.sendMessage(ChatColor.RED + "Tu observes deja cette arene.");
                return false;
            }
            removeSpectator(player);
        }

        Location target = arena.getSpectatorTeleportLocation();
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Impossible d'observer cette arene : aucun point "
                    + "de teleportation spectateur ni lobby configure.");
            return false;
        }

        arena.addSpectator(player.getUniqueId(), player.getLocation().clone(), player.getGameMode());
        player.teleport(target);
        player.setGameMode(GameMode.SPECTATOR);
        applySpectatorSettings(player);
        plugin.getScoreboardManager().show(player);

        player.sendMessage(ChatColor.GREEN + "Tu observes la partie sur l'arene "
                + ChatColor.YELLOW + arena.getName() + ChatColor.GREEN + ".");
        player.sendMessage(ChatColor.GRAY + "Utilise " + ChatColor.YELLOW + "/p unspectate"
                + ChatColor.GRAY + " pour revenir a ta position de depart.");
        broadcastToArena(arena, ChatColor.DARK_GRAY + player.getName() + " observe la partie.");
        updateSidebar(arena);
        return true;
    }

    /**
     * Applique les reglages configurables du mode spectateur (config.yml, section
     * "spectator") : vision nocturne, vitesse de vol... Le SPECTATOR de Minecraft vole
     * deja nativement, ces options ne font qu'ameliorer le confort de visionnage.
     */
    private void applySpectatorSettings(Player player) {
        if (plugin.getConfig().getBoolean("spectator.night-vision", true)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
        }
    }

    private void clearSpectatorSettings(Player player) {
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    /** Sort un joueur du mode spectateur et le renvoie d'ou il venait. */
    public void removeSpectator(Player player) {
        Arena arena = getSpectatedArena(player.getUniqueId());
        if (arena == null) return;
        removeSpectator(player, arena, true);
    }

    private void removeSpectator(Player player, Arena arena, boolean teleportBack) {
        GameMode previousMode = arena.getPreSpectateMode(player.getUniqueId());
        Location back = arena.removeSpectator(player.getUniqueId());

        player.setGameMode(previousMode == GameMode.SPECTATOR ? GameMode.SURVIVAL : previousMode);
        clearSpectatorSettings(player);
        plugin.getScoreboardManager().remove(player);
        resetTabList(player);

        if (teleportBack && back != null) {
            player.teleport(back);
        }
        player.sendMessage(ChatColor.GRAY + "Tu as quitte le mode spectateur.");
        updateSidebar(arena);
    }

    /** Renvoie tous les spectateurs d'une arene a leur position d'origine. */
    public void removeAllSpectators(Arena arena) {
        for (UUID uuid : new ArrayList<>(arena.getSpectators())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeSpectator(player, arena, true);
            } else {
                arena.removeSpectator(uuid);
            }
        }
    }

    // ================= Couleurs / noms d'equipe =================

    public ChatColor getTeamColor(Arena arena, int teamIndex) {
        List<String> colors = plugin.getConfig().getStringList("team-colors");
        if (teamIndex < colors.size()) {
            try {
                return ChatColor.valueOf(colors.get(teamIndex).toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        ChatColor[] fallback = Arena.defaultColorOrder();
        return fallback[teamIndex % fallback.length];
    }

    public String getTeamDisplayName(Arena arena, int teamIndex) {
        List<String> names = plugin.getConfig().getStringList("team-display-names");
        if (teamIndex < names.size()) {
            return names.get(teamIndex);
        }
        return "Equipe " + (teamIndex + 1);
    }

    // ================= Utilitaires =================

    // ================= Tab list =================

    /**
     * Met a jour le header/footer de la tab list pour tous les joueurs de l'arene.
     * Header : "HeroCraft" en jaune/bleu + "Push" en rose
     * Footer : scores des equipes
     */
    public void updateTabList(Arena arena) {
        String header = ChatColor.YELLOW + "" + ChatColor.BOLD + "Hero" + ChatColor.BLUE + "" + ChatColor.BOLD + "Craft"
                + "  " + ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Push";

        StringBuilder footer = new StringBuilder();
        for (int i = 0; i < arena.getTeamCount(); i++) {
            ChatColor color = getTeamColor(arena, i);
            String teamName = getTeamDisplayName(arena, i);
            int pts = arena.getScore(i);
            int dmg = (int) Math.round(arena.getDamage(i));
            if (i > 0) footer.append(ChatColor.DARK_GRAY).append("  |  ");
            footer.append(color).append(ChatColor.BOLD).append(teamName)
                  .append(ChatColor.RESET).append(" ")
                  .append(ChatColor.WHITE).append(pts).append(ChatColor.GRAY).append("pts")
                  .append(ChatColor.DARK_GRAY).append(" / ")
                  .append(ChatColor.RED).append(dmg).append(ChatColor.GRAY).append("dmg");
        }

        for (Player p : getOnlinePlayersInArena(arena)) {
            p.setPlayerListHeaderFooter(header, footer.toString());
        }
    }

    /** Remet la tab list par defaut quand le joueur quitte l'arene. */
    public void resetTabList(Player player) {
        player.setPlayerListHeaderFooter("", "");
    }

    public void broadcastToArena(Arena arena, String message) {
        for (UUID uuid : arena.getPlayerTeamMap().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    public List<Player> getOnlinePlayersInArena(Arena arena) {
        return arena.getPlayerTeamMap().keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** Petite enveloppe pour garder une reference d'ID de tache planifiee. */
    private static class BukkitTaskHandle {
        int taskId;
    }
}
