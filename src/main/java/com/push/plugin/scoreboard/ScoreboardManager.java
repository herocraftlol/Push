package com.push.plugin.scoreboard;

import com.push.plugin.Arena;
import com.push.plugin.ArenaManager;
import com.push.plugin.PushPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gere le sidebar affiche sur le cote de l'ecran.
 *
 * Contrairement a l'ancienne version (un seul scoreboard partage par arene qui ne montrait
 * que les points et les degats par equipe), chaque joueur possede ici SON propre scoreboard.
 * Cela permet d'afficher des informations personnelles (equipe, K/D, degats infliges,
 * etat de l'arc) en plus des informations communes (scores, effectifs, chrono).
 *
 * Trois variantes de sidebar sont produites :
 *  - lobby d'attente : effectifs, equipe assignee, compte a rebours avant le lancement
 *  - partie en cours : chrono, score et effectif de chaque equipe, stats personnelles
 *  - spectateur      : memes scores que les joueurs observes, sans stats personnelles
 *
 * Detail technique : une ligne de sidebar est une "entree" du scoreboard, et deux lignes
 * ne peuvent pas avoir le meme texte. On utilise donc comme entree une suite de codes
 * couleur (invisibles a l'ecran) unique par ligne, et on fait porter le texte reellement
 * affiche par le prefixe/suffixe d'une equipe scoreboard attachee a cette entree.
 */
public final class ScoreboardManager {

    /** Nom de l'objectif du sidebar (limite Bukkit : 16 caracteres). */
    private static final String OBJECTIVE_NAME = "push_sidebar";

    /** Prefixe des equipes scoreboard qui portent le texte des lignes. */
    private static final String LINE_TEAM_PREFIX = "ln_";

    /** Prefixe des equipes scoreboard qui colorent le pseudo des joueurs par equipe d'arene. */
    private static final String TEAM_COLOR_PREFIX = "tc_";

    /** Longueur maximale d'un prefixe/suffixe d'equipe scoreboard. */
    private static final int PART_LIMIT = 64;

    /** Nombre maximal de lignes affichables dans un sidebar. */
    private static final int MAX_LINES = 15;

    private final PushPlugin plugin;

    /** Scoreboard personnel de chaque joueur/spectateur suivi. */
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    private BukkitTask updateTask;

    private String title;
    private String footer;

    public ScoreboardManager(PushPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        startUpdateTask();
    }

    /** (Re)lit les libelles configurables du sidebar. */
    public void loadConfig() {
        title = plugin.getConfig().getString("scoreboard.title", "&8[&b&lHERO&d&lCRAFT&8] &d&lPUSH");
        footer = plugin.getConfig().getString("scoreboard.footer", "&dplay.herocraft.fr");
    }

    private void startUpdateTask() {
        long period = Math.max(1L, plugin.getConfig().getLong("scoreboard.update-ticks", 20L));
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    /** Arrete le rafraichissement et rend son scoreboard normal a chaque joueur suivi. */
    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        for (UUID uuid : new HashSet<>(boards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }

    // ================= API publique =================

    /**
     * Affiche (ou reaffiche) le sidebar a un joueur. Fonctionne aussi bien pour un joueur
     * en lobby/partie que pour un spectateur : le contenu est deduit de son etat reel.
     */
    public void show(Player player) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(),
                uuid -> Bukkit.getScoreboardManager().getNewScoreboard());
        player.setScoreboard(board);
        refresh(player, board);
    }

    /** Retire le sidebar d'un joueur et lui rend le scoreboard principal du serveur. */
    public void remove(Player player) {
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * Rafraichit immediatement le sidebar de tous les joueurs et spectateurs d'une arene.
     * Appele apres chaque evenement notable (point marque, degats, arrivee/depart...).
     */
    public void updateArena(Arena arena) {
        for (UUID uuid : new ArrayList<>(arena.getPlayerTeamMap().keySet())) {
            refreshIfTracked(uuid);
        }
        for (UUID uuid : new ArrayList<>(arena.getSpectators())) {
            refreshIfTracked(uuid);
        }
    }

    private void refreshIfTracked(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        Scoreboard board = boards.get(uuid);
        if (board == null) return;
        refresh(player, board);
    }

    // ================= Rafraichissement periodique =================

    private void tick() {
        for (UUID uuid : new ArrayList<>(boards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                boards.remove(uuid);
                continue;
            }
            ArenaManager manager = plugin.getArenaManager();
            if (manager.getArenaOf(uuid) == null && manager.getSpectatedArena(uuid) == null) {
                // Le joueur n'est plus concerne par une arene : on nettoie son sidebar
                boards.remove(uuid);
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                continue;
            }
            refresh(player, boards.get(uuid));
        }
    }

    // ================= Construction du sidebar =================

    private void refresh(Player player, Scoreboard board) {
        ArenaManager manager = plugin.getArenaManager();
        Arena arena = manager.getArenaOf(player.getUniqueId());
        boolean spectator = false;
        if (arena == null) {
            arena = manager.getSpectatedArena(player.getUniqueId());
            spectator = true;
        }
        if (arena == null) return;

        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, color(title));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            hideScoreNumbers(objective);
        } else {
            objective.setDisplayName(color(title));
        }

        List<String> lines = spectator
                ? buildSpectatorLines(player, arena, manager)
                : buildPlayerLines(player, arena, manager);

        applyLines(board, objective, lines);
        applyTeamColors(board, arena, manager);
    }

    /** Sidebar d'un joueur inscrit dans l'arene (lobby, partie ou fin de partie). */
    private List<String> buildPlayerLines(Player player, Arena arena, ArenaManager manager) {
        List<String> lines = new ArrayList<>();
        UUID uuid = player.getUniqueId();
        int team = arena.getTeamOf(uuid);

        lines.add(separator());
        lines.add("&7Arene: &f" + arena.getName());

        if (arena.getState() == Arena.State.WAITING) {
            lines.add("&7Etat: &eEn attente");
            lines.add("&7Joueurs: &a" + arena.countPlayers() + "&7/&f" + arena.getMaxPlayers());
            int countdown = manager.getStartCountdownRemaining(arena);
            if (countdown >= 0) {
                lines.add("&7Debut dans: &6" + countdown + "s");
            } else {
                lines.add("&7Debut: &8il manque "
                        + Math.max(0, arena.getMinPlayersToStart() - arena.countPlayers()) + " joueur(s)");
            }
            lines.add(separator2());
            lines.add("&7Ton equipe: " + teamLabel(manager, arena, team));
            lines.add(separator3());
            lines.add(footer);
            return lines;
        }

        lines.add("&7Temps: &f" + formatTime(arena.getElapsedSeconds()));
        lines.add(separator2());
        lines.addAll(teamScoreLines(arena, manager));
        lines.add(separator3());

        if (arena.getState() == Arena.State.ENDING) {
            lines.add("&6&lPartie terminee");
        } else if (manager.isArenaPaused(arena)) {
            int resume = manager.getResumeCountdownRemaining(arena);
            lines.add("&e&lReprise" + (resume > 0 ? " dans " + resume + "s" : "..."));
        } else {
            lines.add("&7Ton equipe: " + teamLabel(manager, arena, team));
        }

        int kills = arena.getKills(uuid);
        int deaths = arena.getDeaths(uuid);
        String kd = deaths > 0 ? String.format(java.util.Locale.ROOT, "%.1f", (double) kills / deaths)
                : String.valueOf(kills);
        lines.add("&7K/D: &a" + kills + "&7/&c" + deaths + " &8(" + kd + ")");

        // Lignes facultatives : retirees en premier si le sidebar deborde (arene a 4 equipes)
        List<String> optional = new ArrayList<>();

        if (arena.countSpectators() > 0) {
            String spectatorLine = "&7Spectateurs: &f" + arena.countSpectators();
            lines.add(spectatorLine);
            optional.add(spectatorLine);
        }

        String damageLine = "&7Degats: &c" + Math.round(arena.getPlayerDamage(uuid));
        lines.add(damageLine);
        optional.add(damageLine);

        int bow = manager.getBowCooldownRemaining(player);
        lines.add("&7Arc: " + (bow > 0 ? "&c" + bow + "s" : "&aPret"));

        lines.add(separator());
        lines.add(footer);

        trimToMaxLines(lines, optional);
        return lines;
    }

    /**
     * Le sidebar ne peut pas depasser 15 lignes. Sur une arene a 3 ou 4 equipes, les
     * lignes marquees comme facultatives sont retirees avant de tronquer betement la fin
     * (ce qui ferait disparaitre le pied de page).
     */
    private void trimToMaxLines(List<String> lines, List<String> optional) {
        int i = 0;
        while (lines.size() > MAX_LINES && i < optional.size()) {
            lines.remove(optional.get(i));
            i++;
        }
    }

    /**
     * Sidebar d'un spectateur : exactement les memes scores que les joueurs observes,
     * sans les statistiques personnelles puisqu'il ne joue pas.
     */
    private List<String> buildSpectatorLines(Player player, Arena arena, ArenaManager manager) {
        List<String> lines = new ArrayList<>();

        lines.add(separator());
        lines.add("&7Arene: &f" + arena.getName());

        if (arena.getState() == Arena.State.WAITING) {
            lines.add("&7Etat: &eEn attente");
            lines.add("&7Joueurs: &a" + arena.countPlayers() + "&7/&f" + arena.getMaxPlayers());
            int countdown = manager.getStartCountdownRemaining(arena);
            if (countdown >= 0) {
                lines.add("&7Debut dans: &6" + countdown + "s");
            }
        } else {
            lines.add("&7Temps: &f" + formatTime(arena.getElapsedSeconds()));
            lines.add(separator2());
            lines.addAll(teamScoreLines(arena, manager));
        }

        lines.add(separator3());
        lines.add("&e\uD83D\uDC41 Mode spectateur");
        lines.add("&7Spectateurs: &f" + arena.countSpectators());
        lines.add("&8/p unspectate pour partir");
        lines.add(separator());
        lines.add(footer);
        return lines;
    }

    /**
     * Une ligne par equipe, dans le style HikaBrain (icone coeur, "Nom: score/total
     * (Njoueurs)") : couleur de l'equipe, nom, points sur l'objectif, effectif et degats.
     */
    private List<String> teamScoreLines(Arena arena, ArenaManager manager) {
        List<String> lines = new ArrayList<>();
        int pointsToWin = plugin.getConfig().getInt("points-to-win", 5);

        for (int i = 0; i < arena.getTeamCount(); i++) {
            ChatColor color = manager.getTeamColor(arena, i);
            String name = manager.getTeamDisplayName(arena, i);
            int alive = arena.getPlayersInTeam(i).size();
            int damage = (int) Math.round(arena.getDamage(i));

            lines.add(ChatColor.COLOR_CHAR + colorCharOf(color) + "\u2764 " + name + ": "
                    + ChatColor.COLOR_CHAR + colorCharOf(color) + arena.getScore(i)
                    + "&7/&f" + pointsToWin
                    + " &7(&f" + alive + " joueur" + (alive == 1 ? "" : "s") + "&7, &c" + damage + " dgts&7)");
        }
        return lines;
    }

    private String teamLabel(ArenaManager manager, Arena arena, int team) {
        if (team < 0) return "&7-";
        ChatColor color = manager.getTeamColor(arena, team);
        return ChatColor.COLOR_CHAR + colorCharOf(color) + manager.getTeamDisplayName(arena, team);
    }

    // ================= Ecriture des lignes dans le scoreboard =================

    /**
     * Ecrit les lignes dans le sidebar. Les scores sont attribues en ordre decroissant
     * pour garantir que la premiere ligne de la liste s'affiche bien tout en haut.
     */
    private void applyLines(Scoreboard board, Objective objective, List<String> lines) {
        int max = Math.min(lines.size(), MAX_LINES);

        // Les lignes existantes sont mises a jour en place plutot que detruites puis
        // recreees : sans cela le sidebar clignoterait a chaque rafraichissement.
        for (int i = 0; i < max; i++) {
            String entry = invisibleEntry(i);
            String teamName = LINE_TEAM_PREFIX + i;

            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }

            String text = color(lines.get(i));
            team.setPrefix(safePart(text, 0));
            team.setSuffix(safePart(text, 1));
            objective.getScore(entry).setScore(max - i);
        }

        // Retirer les lignes en trop si le sidebar s'est raccourci
        for (int i = max; i < MAX_LINES; i++) {
            Team team = board.getTeam(LINE_TEAM_PREFIX + i);
            if (team == null) continue;
            for (String entry : new HashSet<>(team.getEntries())) {
                board.resetScores(entry);
            }
            team.unregister();
        }
    }

    /**
     * Colore le pseudo des joueurs de l'arene selon leur equipe, dans le scoreboard
     * personnel du joueur qui regarde (tab list et nom au-dessus de la tete).
     */
    private void applyTeamColors(Scoreboard board, Arena arena, ArenaManager manager) {
        for (int i = 0; i < arena.getTeamCount(); i++) {
            String teamName = TEAM_COLOR_PREFIX + i;
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            team.setColor(manager.getTeamColor(arena, i));
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);

            java.util.Set<String> expected = new HashSet<>();
            for (UUID uuid : arena.getPlayersInTeam(i)) {
                Player member = Bukkit.getPlayer(uuid);
                if (member == null) continue;
                expected.add(member.getName());
                if (!team.hasEntry(member.getName())) {
                    team.addEntry(member.getName());
                }
            }
            // Retirer ceux qui ont quitte l'equipe ou l'arene depuis le dernier passage
            for (String entry : new HashSet<>(team.getEntries())) {
                if (!expected.contains(entry)) {
                    team.removeEntry(entry);
                }
            }
        }
    }

    // ================= Utilitaires =================

    /**
     * Entree unique et invisible pour la ligne d'index donne : une combinaison de codes
     * couleur, qui ne produit aucun caractere visible a l'ecran.
     */
    private String invisibleEntry(int index) {
        char[] codes = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        return String.valueOf(ChatColor.COLOR_CHAR) + codes[index % codes.length]
                + ChatColor.COLOR_CHAR + codes[(index / codes.length) % codes.length]
                + ChatColor.RESET;
    }

    /**
     * Decoupe un texte en deux morceaux de 64 caracteres maximum (prefixe puis suffixe),
     * sans jamais couper au milieu d'un code couleur. part = 0 renvoie le prefixe,
     * part = 1 le suffixe.
     */
    private String safePart(String text, int part) {
        int cut = Math.min(PART_LIMIT, text.length());
        // Ne pas couper entre le caractere de section et sa lettre de couleur
        if (cut > 0 && cut < text.length() && text.charAt(cut - 1) == ChatColor.COLOR_CHAR) {
            cut--;
        }
        if (part == 0) {
            return text.substring(0, cut);
        }
        if (cut >= text.length()) {
            return "";
        }
        String rest = text.substring(cut);
        // Reporter la couleur courante sur le suffixe pour qu'il reste lisible
        String carried = ChatColor.getLastColors(text.substring(0, cut));
        String suffix = carried + rest;
        return suffix.length() > PART_LIMIT ? suffix.substring(0, PART_LIMIT) : suffix;
    }

    /**
     * Separateur en ligne horizontale (style HikaBrain : caracteres de trait plutot que
     * du texte barre). Les trois variantes ont chacune un caractere de moins pour former
     * des entrees de scoreboard distinctes (deux lignes ne peuvent pas etre identiques).
     */
    private String separator() {
        return "&7\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500";
    }

    private String separator2() {
        return "&7\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500";
    }

    private String separator3() {
        return "&7\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500";
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long rest = seconds % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, rest);
    }

    /** Lettre du code couleur ('c' pour rouge, '9' pour bleu...) d'une ChatColor. */
    private String colorCharOf(ChatColor chatColor) {
        return String.valueOf(chatColor.getChar());
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Masque les nombres rouges affiches a droite de chaque ligne. Cette possibilite
     * n'existe que sur les serveurs Paper recents ; on passe par la reflexion pour que
     * le plugin continue de fonctionner (avec les nombres visibles) ailleurs.
     */
    private void hideScoreNumbers(Objective objective) {
        try {
            Class<?> numberFormatClass = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Object blank = numberFormatClass.getMethod("blank").invoke(null);
            Objective.class.getMethod("numberFormat", numberFormatClass).invoke(objective, blank);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Serveur trop ancien ou API absente : les nombres resteront visibles, sans gravite
        }
    }

    public void reload() {
        loadConfig();
        if (updateTask != null) {
            updateTask.cancel();
        }
        startUpdateTask();
    }
}
