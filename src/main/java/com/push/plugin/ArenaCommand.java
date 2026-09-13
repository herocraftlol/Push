package com.push.plugin;

import com.push.plugin.gui.ArenaGUI;
import com.push.plugin.lobby.LobbyZoneManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ArenaCommand implements CommandExecutor, TabCompleter {

    /** Toutes les sous-commandes reconnues, pour distinguer /p <sous-commande> de /p <arene> ... */
    private static final Set<String> ALL_SUBCOMMANDS = Set.of(
            "create", "delete", "setlobby", "setspawn", "delspawn", "spawns", "setvoid", "setteams",
            "setteamsize", "setminplayers", "list", "info", "join", "leave", "spectate", "spec",
            "unspectate", "unspec", "setspectator", "setspec", "stats", "forcestart", "setpos1",
            "setpos2", "lobbyzone", "leaderboard", "reload", "gui", "level", "cosmetics",
            "spectatormode", "holo");

    private final PushPlugin plugin;
    private final ArenaManager manager;

    public ArenaCommand(PushPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getArenaManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        // Syntaxe alternative centree sur l'arene : /p <arene> lobbyzone <pos1|pos2|...>
        // Elle n'est acceptee que si args[0] n'est pas deja un nom de sous-commande, pour
        // ne pas casser les commandes existantes.
        if (args.length >= 2 && args[1].equalsIgnoreCase("lobbyzone") && !isSubCommand(args[0])) {
            String[] rebuilt = new String[Math.max(3, args.length)];
            rebuilt[0] = "lobbyzone";
            rebuilt[1] = args[0];
            rebuilt[2] = args.length >= 3 ? args[2] : "info";
            handleLobbyZone(sender, rebuilt);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "lobbyzone" -> handleLobbyZone(sender, args);
            case "create" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "setlobby" -> handleSetLobby(sender, args);
            case "setspawn" -> handleSetSpawn(sender, args);
            case "delspawn" -> handleDelSpawn(sender, args);
            case "spawns" -> handleSpawnsGui(sender, args);
            case "setvoid" -> handleSetVoid(sender, args);
            case "setteams" -> handleSetTeams(sender, args);
            case "setteamsize" -> handleSetTeamSize(sender, args);
            case "setminplayers" -> handleSetMinPlayers(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "join" -> handleJoin(sender, args);
            case "leave" -> handleLeave(sender);
            case "spectate", "spec" -> handleSpectate(sender, args);
            case "unspectate", "unspec" -> handleUnspectate(sender);
            case "setspectator", "setspec" -> handleSetSpectatorSpawn(sender, args);
            case "stats" -> handleStats(sender, args);
            case "forcestart" -> handleForceStart(sender, args);
            case "setpos1" -> handleSetPos(sender, args, 1);
            case "setpos2" -> handleSetPos(sender, args, 2);
            case "leaderboard" -> handleLeaderboard(sender, args);
            case "reload" -> handleReload(sender);
            case "gui" -> handleGui(sender);
            case "level" -> handleLevel(sender, args);
            case "cosmetics" -> handleCosmetics(sender);
            case "spectatormode" -> handleSpectatorMode(sender, args);
            case "holo" -> handleHolo(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    // ---------------- Sous-commandes admin ----------------

    private void handleCreate(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /p create <nom>");
            return;
        }
        String name = args[1];
        Arena arena = manager.create(name);
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "Une arene avec ce nom existe deja.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Arene " + ChatColor.YELLOW + name + ChatColor.GREEN
                + " creee. Configure-la avec /p setlobby, setspawn, setvoid, setteams...");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /p delete <nom>");
            return;
        }
        boolean ok = manager.delete(args[1]);
        sender.sendMessage(ok ? ChatColor.GREEN + "Arene supprimee."
                : ChatColor.RED + "Arene introuvable.");
    }

    private void handleSetLobby(CommandSender sender, String[] args) {
        if (!requirePlayerAdmin(sender)) return;
        Player player = (Player) sender;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /p setlobby <nom>");
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        arena.setLobbyLocation(player.getLocation());
        manager.saveArena(arena);
        sender.sendMessage(ChatColor.GREEN + "Point de lobby defini pour " + arena.getName() + ".");
    }

    /**
     * Resout un identifiant d'equipe saisi par un admin : accepte soit le nom de couleur
     * configure (Rose, Magenta, Violet, Bleu fonce...), insensible a la casse, soit
     * l'ancien format numerique (1-4) pour compatibilite. Envoie lui-meme un message
     * d'erreur explicite (listant les couleurs valides) et renvoie null en cas d'echec.
     */
    private Integer resolveTeamIndex(CommandSender sender, Arena arena, String input) {
        int teamCount = arena.getTeamCount();

        for (int i = 0; i < teamCount; i++) {
            if (manager.getTeamDisplayName(arena, i).equalsIgnoreCase(input)) {
                return i;
            }
        }

        try {
            int teamNumber = Integer.parseInt(input);
            if (teamNumber >= 1 && teamNumber <= teamCount) {
                return teamNumber - 1;
            }
        } catch (NumberFormatException ignored) {
        }

        StringBuilder valid = new StringBuilder();
        for (int i = 0; i < teamCount; i++) {
            if (i > 0) valid.append(ChatColor.GRAY).append(", ");
            valid.append(manager.getTeamColor(arena, i)).append(manager.getTeamDisplayName(arena, i));
        }
        sender.sendMessage(ChatColor.RED + "Equipe invalide. Utilise le nom de la couleur : "
                + valid + ChatColor.RED + ".");
        return null;
    }

    /** Variante silencieuse de resolveTeamIndex, pour la tab-completion (pas de message d'erreur). */
    private int findTeamIndexSilently(Arena arena, String input) {
        if (arena == null || input == null) return -1;
        for (int i = 0; i < arena.getTeamCount(); i++) {
            if (manager.getTeamDisplayName(arena, i).equalsIgnoreCase(input)) {
                return i;
            }
        }
        try {
            int teamNumber = Integer.parseInt(input);
            if (teamNumber >= 1 && teamNumber <= arena.getTeamCount()) {
                return teamNumber - 1;
            }
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }

    private void handleSetSpawn(CommandSender sender, String[] args) {
        if (!requirePlayerAdmin(sender)) return;
        Player player = (Player) sender;
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /p setspawn <nom> <couleur> [numeroSpawn]");
            sender.sendMessage(ChatColor.GRAY + "<couleur> : le nom de l'equipe (ex: Rose, Magenta, Violet, Bleu fonce).");
            sender.sendMessage(ChatColor.GRAY + "Si [numeroSpawn] est omis, un nouveau spawn est ajoute a la suite "
                    + "(utile pour plusieurs joueurs par equipe).");
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        Integer teamIndexBoxed = resolveTeamIndex(sender, arena, args[2]);
        if (teamIndexBoxed == null) return;
        int teamIndex = teamIndexBoxed;
        int teamNumber = teamIndex + 1;

        int nextAvailable = arena.getSpawnCount(teamIndex) + 1;
        int spawnNumber;
        if (args.length >= 4) {
            try {
                spawnNumber = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Le numero de spawn doit etre un nombre entier (ex: 1, 2, 3...).");
                return;
            }
        } else {
            spawnNumber = nextAvailable;
        }

        boolean wasReplaced = spawnNumber <= (nextAvailable - 1);
        boolean ok = arena.setSpawn(teamIndex, spawnNumber, player.getLocation());
        if (!ok) {
            sender.sendMessage(ChatColor.RED + "Numero de spawn invalide. Le prochain numero disponible pour cette "
                    + "equipe est " + nextAvailable + ".");
            return;
        }
        manager.saveArena(arena);

        String colorName = manager.getTeamDisplayName(arena, teamIndex);
        sender.sendMessage(ChatColor.GREEN + "Spawn #" + spawnNumber + " de l'equipe " + teamNumber
                + ChatColor.GRAY + " (" + colorName + ")" + ChatColor.GREEN + " "
                + (wasReplaced ? "remplace" : "defini") + " pour " + arena.getName() + "."
                + ChatColor.GRAY + " (" + arena.getSpawnCount(teamIndex) + " spawn(s) au total pour cette equipe)");
    }

    private void handleDelSpawn(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /p delspawn <nom> <couleur> <numeroSpawn>");
            sender.sendMessage(ChatColor.GRAY + "<couleur> : le nom de l'equipe (ex: Rose, Magenta, Violet, Bleu fonce).");
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        Integer teamIndexBoxed = resolveTeamIndex(sender, arena, args[2]);
        if (teamIndexBoxed == null) return;
        int teamIndex = teamIndexBoxed;
        int teamNumber = teamIndex + 1;

        int spawnNumber;
        try {
            spawnNumber = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Le numero de spawn doit etre un entier.");
            return;
        }

        boolean ok = arena.removeSpawn(teamIndex, spawnNumber);
        if (!ok) {
            sender.sendMessage(ChatColor.RED + "Numero de spawn invalide. Cette equipe a actuellement "
                    + arena.getSpawnCount(teamIndex) + " spawn(s).");
            return;
        }
        manager.saveArena(arena);
        sender.sendMessage(ChatColor.GREEN + "Spawn #" + spawnNumber + " de l'equipe " + teamNumber
                + " supprime pour " + arena.getName() + ".");
    }

    private void handleSpawnsGui(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande doit etre executee par un joueur.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /p spawns <nom>");
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        plugin.getSpawnGUI().open(player, arena);
    }

    private void handleSetVoid(CommandSender sender, String[] args) {
        if (!requirePlayerAdmin(sender)) return;
        Player player = (Player) sender;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /p setvoid <nom> [hauteurY optionnelle]");
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        double y;
        if (args.length >= 3) {
            try {
                y = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Hauteur Y invalide.");
                return;
            }
        } else {
            y = player.getLocation().getY();
        }

        arena.setVoidY(y);
        manager.saveArena(arena);
        sender.sendMessage(ChatColor.GREEN + "Limite de vide definie a Y=" + y + " pour " + arena.getName()
                + ". Tout joueur descendant sous cette hauteur sera elimine.");
    }

    private void handleSetTeams(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /p setteams <nom> <2-4>");
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        int count;
        try {
            count = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Le nombre d'equipes doit etre un entier entre 2 et 4.");
            return;
        }
        if (count < 2 || count > 4) {
            sender.sendMessage(ChatColor.RED + "Le nombre d'equipes doit etre entre 2 et 4.");
            return;
        }
        arena.setTeamCount(count);
        manager.saveArena(arena);
        sender.sendMessage(ChatColor.GREEN + "Nombre d'equipes defini a " + count + " pour " + arena.getName()
                + ". N'oublie pas de definir les spawns correspondants avec /p setspawn.");
    }

    private void handleSetTeamSize(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /p setteamsize <nom> <1-8>");
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        int size;
        try {
            size = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "La taille d'equipe doit etre un entier entre 1 et 8.");
            return;
        }
        if (size < 1 || size > 8) {
            sender.sendMessage(ChatColor.RED + "La taille d'equipe doit etre entre 1 et 8.");
            return;
        }
        arena.setTeamSize(size);
        manager.saveArena(arena);
        sender.sendMessage(ChatColor.GREEN + "Taille d'equipe definie a " + size + " joueur(s) max pour "
                + arena.getName() + ".");
    }

    private void handleSetMinPlayers(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /p setminplayers <nom> <minimum>");
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        int min;
        try {
            min = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Le minimum doit etre un entier.");
            return;
        }
        if (min < 2) {
            sender.sendMessage(ChatColor.RED + "Le minimum est de 2 joueurs.");
            return;
        }
        arena.setMinPlayersToStart(min);
        manager.saveArena(arena);
        sender.sendMessage(ChatColor.GREEN + "Minimum de joueurs pour demarrer automatiquement defini a "
                + min + " pour " + arena.getName() + ".");
    }

    private void handleSetPos(CommandSender sender, String[] args, int posNum) {
        if (!requirePlayerAdmin(sender)) return;
        Player player = (Player) sender;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /p setpos" + posNum + " <nom>");
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        if (posNum == 1) {
            arena.setZonePos1(player.getLocation().getBlock().getLocation());
        } else {
            arena.setZonePos2(player.getLocation().getBlock().getLocation());
        }
        manager.saveArena(arena);

        String status = arena.hasZone()
                ? ChatColor.GREEN + " Zone completement definie !"
                : ChatColor.YELLOW + " Definis aussi /p setpos" + (posNum == 1 ? 2 : 1) + " pour completer la zone.";
        sender.sendMessage(ChatColor.GREEN + "Position " + posNum + " de la zone definie pour "
                + ChatColor.YELLOW + arena.getName() + ChatColor.GREEN + "." + status);
    }

    private void handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) return;
        plugin.reloadConfig();
        manager.loadAll();
        plugin.getScoreboardManager().reload();
        sender.sendMessage(ChatColor.GREEN + "Configuration rechargee.");
    }

    // ---------------- Sous-commandes joueur ----------------

    private void handleList(CommandSender sender) {
        if (manager.getAll().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Aucune arene n'a ete creee.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Arenes disponibles :");
        for (Arena arena : manager.getAll()) {
            String status = switch (arena.getState()) {
                case WAITING -> ChatColor.GREEN + "en attente";
                case RUNNING -> ChatColor.RED + "en cours";
                case ENDING -> ChatColor.YELLOW + "termine";
            };
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + arena.getName()
                    + ChatColor.GRAY + " (" + status + ChatColor.GRAY + ", "
                    + arena.countPlayers() + "/" + arena.getMaxPlayers() + " joueurs, "
                    + arena.getTeamCount() + " equipes)"
                    + (arena.isFullyConfigured() ? "" : ChatColor.RED + " [non configuree]"));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /p info <nom>");
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        sender.sendMessage(ChatColor.GOLD + "=== Arene " + arena.getName() + " ===");
        sender.sendMessage(ChatColor.GRAY + "Etat: " + arena.getState());
        sender.sendMessage(ChatColor.GRAY + "Equipes: " + arena.getTeamCount()
                + " | Taille max/equipe: " + arena.getTeamSize()
                + " | Min pour demarrer: " + arena.getMinPlayersToStart());
        sender.sendMessage(ChatColor.GRAY + "Lobby defini: " + (arena.getLobbyLocation() != null ? "oui" : "NON"));
        sender.sendMessage(ChatColor.GRAY + "Limite de vide: " + (arena.hasVoidY() ? "Y=" + arena.getVoidY() : "NON"));
        for (int i = 0; i < arena.getTeamCount(); i++) {
            int count = arena.getSpawnCount(i);
            String colorName = Arena.spawnColorName(arena.getTeamCount(), i);
            sender.sendMessage(ChatColor.GRAY + "Spawns equipe " + (i + 1) + " (" + colorName + "): "
                    + (count > 0 ? ChatColor.GREEN + "" + count : ChatColor.RED + "0 - NON CONFIGUREE"));
        }
        LobbyZoneManager lobby = plugin.getLobbyZoneManager();
        sender.sendMessage(ChatColor.GRAY + "Salle d'attente en jeu: "
                + (lobby.hasSnapshot(arena)
                    ? ChatColor.GREEN + "" + lobby.getBlockCount(arena) + " blocs ("
                        + (lobby.isPlaced(arena) ? "posee" : "effacee") + ")"
                    : ChatColor.GRAY + "aucune"));
        sender.sendMessage(ChatColor.GRAY + "Entierement configuree: "
                + (arena.isFullyConfigured() ? ChatColor.GREEN + "oui" : ChatColor.RED + "non"));
    }

    private void handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seul un joueur peut rejoindre une arene.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /p join <nom>");
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        manager.joinLobby(player, arena);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seul un joueur peut quitter une arene.");
            return;
        }
        // /p leave sort aussi du mode spectateur, c'est le reflexe naturel du joueur
        if (manager.getSpectatedArena(player.getUniqueId()) != null) {
            manager.removeSpectator(player);
            return;
        }
        manager.leave(player);
    }

    // ---------------- Zone du lobby d'attente ----------------

    /**
     * Gere /p lobbyzone <arene> <pos1|pos2|save|clear|paste|info>, ainsi que la forme
     * equivalente /p <arene> lobbyzone <action>.
     */
    private void handleLobbyZone(CommandSender sender, String[] args) {
        if (!requirePlayerAdmin(sender)) return;
        Player player = (Player) sender;

        if (args.length < 3) {
            sendLobbyZoneHelp(sender);
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        LobbyZoneManager lobby = plugin.getLobbyZoneManager();
        String action = args[2].toLowerCase();

        switch (action) {
            case "pos1", "pos2" -> {
                Block target = player.getTargetBlockExact(10);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Vise un bloc (a moins de 10 blocs) pour definir ce coin.");
                    return;
                }
                Location corner = target.getLocation();
                if (action.equals("pos1")) {
                    arena.setLobbyZonePos1(corner);
                } else {
                    arena.setLobbyZonePos2(corner);
                }
                manager.saveArena(arena);

                sender.sendMessage(ChatColor.GREEN + "Coin " + action.substring(3) + " du lobby de "
                        + ChatColor.YELLOW + arena.getName() + ChatColor.GREEN + " place en "
                        + ChatColor.WHITE + corner.getBlockX() + " " + corner.getBlockY() + " " + corner.getBlockZ()
                        + ChatColor.GREEN + ".");

                if (!arena.hasLobbyZone()) {
                    sender.sendMessage(ChatColor.YELLOW + "Definis maintenant "
                            + (action.equals("pos1") ? "pos2" : "pos1") + " pour completer la zone.");
                    return;
                }
                // Les deux coins sont poses : on prend le moule automatiquement
                captureAndReport(sender, arena, lobby);
            }
            case "save", "capture" -> {
                if (!arena.hasLobbyZone()) {
                    sender.sendMessage(ChatColor.RED + "Definis d'abord les deux coins avec /p "
                            + arena.getName() + " lobbyzone pos1 et pos2.");
                    return;
                }
                captureAndReport(sender, arena, lobby);
            }
            case "clear", "hide" -> {
                if (!lobby.hasSnapshot(arena)) {
                    sender.sendMessage(ChatColor.RED + "Aucune structure de lobby enregistree pour cette arene.");
                    return;
                }
                lobby.clear(arena);
                sender.sendMessage(ChatColor.GREEN + "Structure du lobby effacee.");
            }
            case "paste", "show" -> {
                if (!lobby.hasSnapshot(arena)) {
                    sender.sendMessage(ChatColor.RED + "Aucune structure de lobby enregistree pour cette arene.");
                    return;
                }
                lobby.paste(arena);
                sender.sendMessage(ChatColor.GREEN + "Structure du lobby remise en place.");
            }
            case "info" -> {
                sender.sendMessage(ChatColor.GOLD + "=== Lobby de " + arena.getName() + " ===");
                sender.sendMessage(ChatColor.GRAY + "Coin 1: " + describeCorner(arena.getLobbyZonePos1()));
                sender.sendMessage(ChatColor.GRAY + "Coin 2: " + describeCorner(arena.getLobbyZonePos2()));
                if (arena.hasLobbyZone()) {
                    sender.sendMessage(ChatColor.GRAY + "Volume de la zone: " + ChatColor.WHITE
                            + arena.getLobbyZoneVolume() + " blocs");
                }
                sender.sendMessage(ChatColor.GRAY + "Structure enregistree: "
                        + (lobby.hasSnapshot(arena)
                            ? ChatColor.GREEN + "oui (" + lobby.getBlockCount(arena) + " blocs)"
                            : ChatColor.RED + "non"));
                sender.sendMessage(ChatColor.GRAY + "Actuellement posee: "
                        + (lobby.isPlaced(arena) ? ChatColor.GREEN + "oui" : ChatColor.YELLOW + "non"));
            }
            default -> sendLobbyZoneHelp(sender);
        }
    }

    private void captureAndReport(CommandSender sender, Arena arena, LobbyZoneManager lobby) {
        int count = lobby.capture(arena);
        if (count == -2) {
            sender.sendMessage(ChatColor.RED + "Capture impossible pendant une partie : la structure du "
                    + "lobby est effacee en ce moment. Attends la fin de la partie.");
            return;
        }
        if (count < 0) {
            sender.sendMessage(ChatColor.RED + "Capture impossible : zone invalide, coins dans deux mondes "
                    + "differents, ou zone trop grande (max " + LobbyZoneManager.MAX_VOLUME + " blocs).");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Structure du lobby enregistree : " + ChatColor.WHITE + count
                + ChatColor.GREEN + " bloc(s).");
        sender.sendMessage(ChatColor.GRAY + "Elle sera effacee a chaque lancement de partie et remise "
                + "en place a chaque reouverture de l'arene.");
    }

    private String describeCorner(Location loc) {
        if (loc == null) return ChatColor.RED + "non defini";
        return ChatColor.WHITE + "" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + ChatColor.DARK_GRAY + " (" + (loc.getWorld() != null ? loc.getWorld().getName() : "?") + ")";
    }

    private void sendLobbyZoneHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Lobby d'attente construit en jeu ===");
        sender.sendMessage(ChatColor.YELLOW + "/p <arene> lobbyzone pos1" + ChatColor.GRAY
                + " - premier coin (le bloc vise)");
        sender.sendMessage(ChatColor.YELLOW + "/p <arene> lobbyzone pos2" + ChatColor.GRAY
                + " - second coin, la structure est enregistree automatiquement");
        sender.sendMessage(ChatColor.YELLOW + "/p <arene> lobbyzone save" + ChatColor.GRAY
                + " - re-enregistrer apres avoir modifie la construction");
        sender.sendMessage(ChatColor.YELLOW + "/p <arene> lobbyzone clear|paste" + ChatColor.GRAY
                + " - effacer / reposer manuellement");
        sender.sendMessage(ChatColor.YELLOW + "/p <arene> lobbyzone info" + ChatColor.GRAY + " - etat actuel");
    }

    /** True si le mot correspond a une sous-commande connue (et non a un nom d'arene). */
    private boolean isSubCommand(String word) {
        return ALL_SUBCOMMANDS.contains(word.toLowerCase());
    }

    // ---------------- Mode spectateur ----------------

    private void handleSpectate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seul un joueur peut observer une arene.");
            return;
        }
        if (!player.hasPermission("push.spectate")) {
            sender.sendMessage(ChatColor.RED + "Tu n'as pas la permission d'observer une partie.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /p spectate <nom>");
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        manager.addSpectator(player, arena);
    }

    private void handleUnspectate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande doit etre executee par un joueur.");
            return;
        }
        if (manager.getSpectatedArena(player.getUniqueId()) == null) {
            sender.sendMessage(ChatColor.RED + "Tu n'observes aucune partie.");
            return;
        }
        manager.removeSpectator(player);
    }

    private void handleSetSpectatorSpawn(CommandSender sender, String[] args) {
        if (!requirePlayerAdmin(sender)) return;
        Player player = (Player) sender;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /p setspectator <nom>");
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        arena.setSpectatorSpawn(player.getLocation());
        manager.saveArena(arena);
        sender.sendMessage(ChatColor.GREEN + "Point d'observation defini pour " + arena.getName()
                + ". Les spectateurs apparaitront ici.");
    }

    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande doit etre executee par un joueur.");
            return;
        }
        plugin.getArenaGUI().open(player);
    }

    private void handleStats(CommandSender sender, String[] args) {
        // Accessible a tous les joueurs, pas besoin de permission admin
        StatsManager stats = plugin.getStatsManager();

        UUID targetUuid;
        String targetName;

        if (args.length >= 2) {
            targetName = args[1];
            Player online = Bukkit.getPlayer(targetName);
            if (online != null) {
                targetUuid = online.getUniqueId();
                targetName = online.getName();
            } else {
                UUID found = stats.findUuidByName(targetName);
                if (found == null) {
                    sender.sendMessage(ChatColor.RED + "Aucune statistique connue pour " + targetName + ".");
                    return;
                }
                targetUuid = found;
            }
        } else if (sender instanceof Player player) {
            targetUuid = player.getUniqueId();
            targetName = player.getName();
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /p stats <joueur>");
            return;
        }

        int wins = stats.getWins(targetUuid);
        int kills = stats.getKills(targetUuid);
        int damage = (int) Math.round(stats.getDamage(targetUuid));

        sender.sendMessage(ChatColor.GOLD + "=== Statistiques de " + ChatColor.YELLOW + targetName + ChatColor.GOLD + " ===");
        sender.sendMessage(ChatColor.GRAY + "Victoires : " + ChatColor.GREEN + wins);
        sender.sendMessage(ChatColor.GRAY + "Eliminations : " + ChatColor.RED + kills);
        sender.sendMessage(ChatColor.GRAY + "Degats totaux infliges : " + ChatColor.YELLOW + damage);
    }

    private void handleLeaderboard(CommandSender sender, String[] args) {
        if (!requirePlayerAdmin(sender)) return;
        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /p leaderboard <kills|wins|damage>");
            return;
        }

        StatsManager.StatType type;
        String typeName;
        switch (args[1].toLowerCase()) {
            case "kills" -> { type = StatsManager.StatType.KILLS; typeName = "Eliminations"; }
            case "wins"  -> { type = StatsManager.StatType.WINS;  typeName = "Victoires"; }
            case "damage"-> { type = StatsManager.StatType.DAMAGE; typeName = "Degats"; }
            case "pushes"-> { type = StatsManager.StatType.PUSHES; typeName = "Poussees"; }
            default -> {
                sender.sendMessage(ChatColor.RED + "Type invalide. Utilise kills, wins, damage ou pushes.");
                return;
            }
        }

        StatsManager stats = plugin.getStatsManager();
        List<StatsManager.LeaderboardEntry> top = stats.getTopPlayers(type, 4);

        // Placer un panneau au mur devant le joueur
        Block target = player.getTargetBlockExact(5);
        Block signBlock;
        BlockFace face;

        if (target != null && target.getType().isSolid()) {
            // Panneau mural sur la face regardee
            face = getPlayerFacing(player);
            Block adjacent = target.getRelative(face);
            if (adjacent.getType() == Material.AIR) {
                signBlock = adjacent;
            } else {
                sender.sendMessage(ChatColor.RED + "Pas de place pour placer le panneau ici.");
                return;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Regarde un mur pour placer le panneau leaderboard.");
            return;
        }

        Material signMat = Material.OAK_WALL_SIGN;
        signBlock.setType(signMat);

        org.bukkit.block.data.BlockData bd = signBlock.getBlockData();
        if (bd instanceof WallSign ws) {
            ws.setFacing(face);
            signBlock.setBlockData(ws);
        }

        Sign sign = (Sign) signBlock.getState();
        String medal = switch (type) {
            case KILLS  -> "\u2694 TOP KILLS";
            case WINS   -> "\u2605 TOP WINS";
            case DAMAGE -> "\u2763 TOP DAMAGE";
            case PUSHES -> "\u21C5 TOP PUSHES";
        };
        sign.setLine(0, ChatColor.GOLD + "" + ChatColor.BOLD + medal);
        for (int i = 0; i < Math.min(top.size(), 3); i++) {
            StatsManager.LeaderboardEntry e = top.get(i);
            String prefix = switch (i) { case 0 -> ChatColor.GOLD+"#1 "; case 1 -> ChatColor.GRAY+"#2 "; default -> ChatColor.RED+"#3 "; };
            String valStr = (type == StatsManager.StatType.DAMAGE)
                    ? String.valueOf((int)Math.round(e.value()))
                    : String.valueOf((int)e.value());
            sign.setLine(i + 1, prefix + e.name() + " " + valStr);
        }
        sign.update();

        // Afficher aussi le top 5 en chat + stats du demandeur
        sender.sendMessage(ChatColor.GOLD + "=== Leaderboard : " + typeName + " ===");
        String[] medals = {"\u2741 ", "\u2742 ", "\u2743 ", "\u2744 ", "\u2745 "};
        for (int i = 0; i < top.size(); i++) {
            StatsManager.LeaderboardEntry e = top.get(i);
            String valStr = (type == StatsManager.StatType.DAMAGE)
                    ? String.valueOf((int)Math.round(e.value()))
                    : String.valueOf((int)e.value());
            ChatColor col = switch (i) { case 0 -> ChatColor.GOLD; case 1 -> ChatColor.GRAY; case 2 -> ChatColor.RED; default -> ChatColor.WHITE; };
            sender.sendMessage(col + "#" + (i+1) + " " + e.name() + ChatColor.WHITE + " - " + valStr);
        }
        // Stats perso du joueur qui execute la commande
        double myVal = switch (type) {
            case KILLS  -> stats.getKills(player.getUniqueId());
            case WINS   -> stats.getWins(player.getUniqueId());
            case DAMAGE -> stats.getDamage(player.getUniqueId());
            case PUSHES -> stats.getPushes(player.getUniqueId());
        };
        String myValStr = (type == StatsManager.StatType.DAMAGE)
                ? String.valueOf((int)Math.round(myVal))
                : String.valueOf((int)myVal);
        sender.sendMessage(ChatColor.AQUA + "Tes " + typeName.toLowerCase() + " : " + ChatColor.WHITE + myValStr);
        sender.sendMessage(ChatColor.GREEN + "Panneau place devant toi.");
    }

    /** Retourne la direction a laquelle fait face le joueur (pour mur sign). */
    private BlockFace getPlayerFacing(Player player) {
        float yaw = player.getLocation().getYaw();
        if (yaw < 0) yaw += 360;
        if (yaw < 45 || yaw >= 315) return BlockFace.SOUTH;
        if (yaw < 135) return BlockFace.WEST;
        if (yaw < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    private void handleForceStart(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        Arena arena;
        if (args.length >= 2) {
            arena = getArenaOrError(sender, args[1]);
            if (arena == null) return;
        } else if (sender instanceof Player player) {
            arena = manager.getArenaOf(player.getUniqueId());
            if (arena == null) {
                sender.sendMessage(ChatColor.RED + "Precise le nom de l'arene: /p forcestart <nom>");
                return;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /p forcestart <nom>");
            return;
        }
        manager.forceStart(arena);
        sender.sendMessage(ChatColor.GREEN + "Demarrage force pour " + arena.getName() + ".");
    }

    // ---------------- Niveaux / cosmetiques / spectateur / hologrammes ----------------

    private void handleLevel(CommandSender sender, String[] args) {
        java.util.UUID target;
        String targetName;
        if (args.length >= 2) {
            target = plugin.getStatsManager().findUuidByName(args[1]);
            if (target == null) {
                Player online = Bukkit.getPlayer(args[1]);
                if (online != null) target = online.getUniqueId();
            }
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Aucune donnee de niveau pour " + args[1] + ".");
                return;
            }
            targetName = args[1];
        } else if (sender instanceof Player player) {
            target = player.getUniqueId();
            targetName = "Toi";
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /p level <joueur>");
            return;
        }

        var levels = plugin.getLevelManager();
        int level = levels.getLevel(target);
        double[] progress = levels.getProgressInLevel(target);
        var cosmetic = levels.getEquippedCosmetic(target);

        sender.sendMessage(ChatColor.GOLD + "=== Niveau de " + targetName + " ===");
        sender.sendMessage(ChatColor.GRAY + "Niveau : " + ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + level);
        sender.sendMessage(ChatColor.GRAY + "XP : " + ChatColor.WHITE + (int) progress[0]
                + ChatColor.GRAY + "/" + ChatColor.WHITE + (int) progress[1]);
        sender.sendMessage(ChatColor.GRAY + "Cosmetique equipe : " + ChatColor.WHITE + cosmetic.getDisplayName());
    }

    private void handleCosmetics(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande doit etre executee par un joueur en jeu.");
            return;
        }
        plugin.getCosmeticGUI().open(player);
    }

    private void handleSpectatorMode(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /p spectatormode <nom> <on|off>");
            return;
        }
        Arena arena = getArenaOrError(sender, args[1]);
        if (arena == null) return;

        boolean enable = args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true");
        boolean disable = args[2].equalsIgnoreCase("off") || args[2].equalsIgnoreCase("false");
        if (!enable && !disable) {
            sender.sendMessage(ChatColor.RED + "Usage: /p spectatormode <nom> <on|off>");
            return;
        }

        arena.setSpectatingEnabled(enable);
        manager.saveArena(arena);
        sender.sendMessage(ChatColor.GREEN + "Mode spectateur " + (enable ? "active" : "desactive")
                + " pour " + arena.getName() + ".");
    }

    private void handleHolo(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /p holo <summon|remove|list>");
            return;
        }
        var holograms = plugin.getHologramManager();

        switch (args[1].toLowerCase()) {
            case "summon" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Cette commande doit etre executee par un joueur en jeu.");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /p holo summon <kills|pushes|damage|wins|level>");
                    return;
                }
                var category = com.push.plugin.hologram.LeaderboardHologramManager.Category.fromString(args[2]);
                if (category == null) {
                    sender.sendMessage(ChatColor.RED + "Categorie invalide. Utilise kills, pushes, damage, wins ou level.");
                    return;
                }
                String id = holograms.summon(player.getLocation(), category);
                sender.sendMessage(ChatColor.GREEN + "Hologramme " + ChatColor.YELLOW + id
                        + ChatColor.GREEN + " place ici (" + category.name().toLowerCase() + ").");
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /p holo remove <id>");
                    return;
                }
                boolean ok = holograms.remove(args[2]);
                sender.sendMessage(ok ? ChatColor.GREEN + "Hologramme supprime."
                        : ChatColor.RED + "Hologramme introuvable.");
            }
            case "list" -> {
                sender.sendMessage(ChatColor.GOLD + "=== Hologrammes Push ===");
                if (holograms.listIds().isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Aucun hologramme place.");
                }
                for (String id : holograms.listIds()) {
                    sender.sendMessage(ChatColor.YELLOW + id + ChatColor.GRAY + " - " + holograms.describe(id));
                }
            }
            default -> sender.sendMessage(ChatColor.RED + "Usage: /p holo <summon|remove|list>");
        }
    }

    // ---------------- Helpers ----------------

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("push.admin")) {
            sender.sendMessage(ChatColor.RED + "Tu n'as pas la permission d'utiliser cette commande.");
            return false;
        }
        return true;
    }

    private boolean requirePlayerAdmin(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande doit etre executee par un joueur en jeu.");
            return false;
        }
        return requireAdmin(sender);
    }

    private Arena getArenaOrError(CommandSender sender, String name) {
        Arena arena = manager.get(name);
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "Arene inconnue: " + name);
        }
        return arena;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Push ===");
        sender.sendMessage(ChatColor.YELLOW + "/p list" + ChatColor.GRAY + " - liste des arenes");
        sender.sendMessage(ChatColor.YELLOW + "/p info <nom>" + ChatColor.GRAY + " - details d'une arene");
        sender.sendMessage(ChatColor.YELLOW + "/p join <nom>" + ChatColor.GRAY + " - rejoindre le lobby");
        sender.sendMessage(ChatColor.YELLOW + "/p leave" + ChatColor.GRAY + " - quitter l'arene actuelle");
        sender.sendMessage(ChatColor.YELLOW + "/p spectate <nom>" + ChatColor.GRAY + " - observer une partie");
        sender.sendMessage(ChatColor.YELLOW + "/p unspectate" + ChatColor.GRAY + " - quitter le mode spectateur");
        sender.sendMessage(ChatColor.YELLOW + "/p stats [joueur]" + ChatColor.GRAY + " - victoires et eliminations");
        sender.sendMessage(ChatColor.YELLOW + "/p level [joueur]" + ChatColor.GRAY + " - niveau et XP Push");
        sender.sendMessage(ChatColor.YELLOW + "/p cosmetics" + ChatColor.GRAY + " - choisir ton effet d'elimination");
        sender.sendMessage(ChatColor.YELLOW + "/p gui" + ChatColor.GRAY + " - ouvrir le menu des arenes");
        if (sender.hasPermission("push.admin")) {
            sender.sendMessage(ChatColor.AQUA + "--- Admin ---");
            sender.sendMessage(ChatColor.YELLOW + "/p create <nom>");
            sender.sendMessage(ChatColor.YELLOW + "/p delete <nom>");
            sender.sendMessage(ChatColor.YELLOW + "/p setlobby <nom>");
            sender.sendMessage(ChatColor.YELLOW + "/p setspectator <nom>" + ChatColor.GRAY
                    + " - point d'apparition des spectateurs (a defaut : le lobby)");
            sender.sendMessage(ChatColor.YELLOW + "/p setspawn <nom> <couleur> [numeroSpawn]" + ChatColor.GRAY
                    + " - ajoute/remplace un spawn (couleur : Rose, Magenta, Violet, Bleu fonce...)");
            sender.sendMessage(ChatColor.YELLOW + "/p delspawn <nom> <couleur> <numeroSpawn>" + ChatColor.GRAY
                    + " - supprime un spawn d'equipe");
            sender.sendMessage(ChatColor.YELLOW + "/p spawns <nom>" + ChatColor.GRAY
                    + " - ouvrir le menu visuel des spawns (vitres colorees par equipe)");
            sender.sendMessage(ChatColor.YELLOW + "/p setvoid <nom> [y]");
            sender.sendMessage(ChatColor.YELLOW + "/p setpos1 <nom>" + ChatColor.GRAY + " - coin 1 de la zone protegee");
            sender.sendMessage(ChatColor.YELLOW + "/p setpos2 <nom>" + ChatColor.GRAY + " - coin 2 de la zone protegee");
            sender.sendMessage(ChatColor.YELLOW + "/p <nom> lobbyzone pos1|pos2" + ChatColor.GRAY
                    + " - delimite la salle d'attente construite en jeu (bloc vise),");
            sender.sendMessage(ChatColor.GRAY + "    effacee pendant les parties et remise a la reouverture");
            sender.sendMessage(ChatColor.YELLOW + "/p setteams <nom> <2-4>");
            sender.sendMessage(ChatColor.YELLOW + "/p setteamsize <nom> <1-8>");
            sender.sendMessage(ChatColor.YELLOW + "/p setminplayers <nom> <min>");
            sender.sendMessage(ChatColor.YELLOW + "/p forcestart [nom]");
            sender.sendMessage(ChatColor.YELLOW + "/p leaderboard <kills|wins|damage|pushes>" + ChatColor.GRAY + " - panneau top joueurs");
            sender.sendMessage(ChatColor.YELLOW + "/p spectatormode <nom> <on|off>" + ChatColor.GRAY
                    + " - activer/desactiver le mode spectateur sur cette arene");
            sender.sendMessage(ChatColor.YELLOW + "/p holo summon <kills|pushes|damage|wins|level>" + ChatColor.GRAY
                    + " - hologramme de classement a ta position");
            sender.sendMessage(ChatColor.YELLOW + "/p holo remove <id>" + ChatColor.GRAY + " - supprimer un hologramme");
            sender.sendMessage(ChatColor.YELLOW + "/p holo list" + ChatColor.GRAY + " - lister les hologrammes");
            sender.sendMessage(ChatColor.YELLOW + "/p reload");
        }
    }

    // ---------------- Tab completion ----------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> subs = new ArrayList<>(List.of("list", "info", "join", "leave", "stats", "gui",
                "spectate", "unspectate", "level", "cosmetics"));
        if (sender.hasPermission("push.admin")) {
            subs.addAll(List.of("create", "delete", "setlobby", "setspawn", "delspawn", "spawns", "setvoid",
                    "setteams", "setteamsize", "setminplayers", "forcestart", "setpos1", "setpos2",
                    "setspectator", "lobbyzone", "leaderboard", "reload", "spectatormode", "holo"));
        }

        if (args.length == 1) {
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && List.of("delete", "setlobby", "setspawn", "delspawn", "spawns", "setvoid", "setteams",
                "setteamsize", "setminplayers", "forcestart", "setpos1", "setpos2", "info", "join",
                "spectate", "spec", "setspectator", "setspec", "lobbyzone", "spectatormode").contains(args[0].toLowerCase())) {
            return manager.getAll().stream()
                    .map(Arena::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("spectatormode")) {
            return List.of("on", "off").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("holo")) {
            return List.of("summon", "remove", "list").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("holo") && args[1].equalsIgnoreCase("summon")) {
            return List.of("kills", "pushes", "damage", "wins", "level").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("holo") && args[1].equalsIgnoreCase("remove")) {
            return new ArrayList<>(plugin.getHologramManager().listIds()).stream()
                    .filter(id -> id.startsWith(args[2]))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("setspawn") || args[0].equalsIgnoreCase("delspawn"))) {
            Arena arena = manager.get(args[1]);
            List<String> colorNames = new ArrayList<>();
            int teamCount = arena != null ? arena.getTeamCount() : 4;
            for (int i = 0; i < teamCount; i++) {
                colorNames.add(arena != null ? manager.getTeamDisplayName(arena, i) : "Equipe" + (i + 1));
            }
            return colorNames.stream()
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("setspawn") || args[0].equalsIgnoreCase("delspawn"))) {
            Arena arena = manager.get(args[1]);
            int nextAvailable = 1;
            if (arena != null) {
                int teamIndex = findTeamIndexSilently(arena, args[2]);
                if (teamIndex != -1) {
                    nextAvailable = arena.getSpawnCount(teamIndex) + 1;
                }
            }
            return List.of(String.valueOf(nextAvailable));
        }
        // /p <arene> lobbyzone <action>
        if (args.length == 2 && !isSubCommand(args[0]) && sender.hasPermission("push.admin")) {
            return List.of("lobbyzone").stream()
                    .filter(x -> x.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("lobbyzone") || args[1].equalsIgnoreCase("lobbyzone"))) {
            return List.of("pos1", "pos2", "save", "clear", "paste", "info").stream()
                    .filter(x -> x.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setteams")) {
            return List.of("2", "3", "4");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setteamsize")) {
            return List.of("1", "2", "3", "4", "5", "6", "7", "8");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("leaderboard")) {
            return List.of("kills", "wins", "damage", "pushes").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
