package com.push.plugin;

import com.push.plugin.chat.QuickChatListener;
import com.push.plugin.gui.ArenaGUI;
import com.push.plugin.gui.ArenaGUIListener;
import com.push.plugin.gui.CosmeticGUI;
import com.push.plugin.gui.CosmeticGUIListener;
import com.push.plugin.gui.SpawnGUI;
import com.push.plugin.gui.SpawnGUIListener;
import com.push.plugin.hologram.LeaderboardHologramManager;
import com.push.plugin.levels.CosmeticManager;
import com.push.plugin.levels.LevelManager;
import com.push.plugin.lobby.LobbyZoneManager;
import com.push.plugin.scoreboard.ScoreboardManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PushPlugin extends JavaPlugin {

    private ArenaManager arenaManager;
    private StatsManager statsManager;
    private ScoreboardManager scoreboardManager;
    private LobbyZoneManager lobbyZoneManager;
    private ArenaGUI arenaGUI;
    private SpawnGUI spawnGUI;
    private CosmeticGUI cosmeticGUI;
    private LevelManager levelManager;
    private CosmeticManager cosmeticManager;
    private LeaderboardHologramManager hologramManager;
    private org.bukkit.scheduler.BukkitTask playtimeXpTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        statsManager = new StatsManager(this);
        statsManager.load();

        levelManager = new LevelManager(this);
        levelManager.load();
        cosmeticManager = new CosmeticManager(this);

        arenaManager = new ArenaManager(this);
        arenaManager.loadAll();

        // Salles d'attente construites en jeu : on recharge les moules puis on remet en
        // place celles des arenes ouvertes (un arret en pleine partie les laisse effacees).
        lobbyZoneManager = new LobbyZoneManager(this);
        lobbyZoneManager.loadAll();
        lobbyZoneManager.restoreAllOpenArenas();

        // Le sidebar est cree apres l'ArenaManager : il lit l'etat des arenes a chaque tick
        scoreboardManager = new ScoreboardManager(this);

        arenaGUI = new ArenaGUI(this);
        spawnGUI = new SpawnGUI(this);
        cosmeticGUI = new CosmeticGUI(this);

        hologramManager = new LeaderboardHologramManager(this);
        hologramManager.loadAll();
        hologramManager.startRefreshTask();

        GameListener gameListener = new GameListener(this);
        getServer().getPluginManager().registerEvents(gameListener, this);
        getServer().getPluginManager().registerEvents(new AdminItemListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaGUIListener(this, arenaGUI), this);
        getServer().getPluginManager().registerEvents(new SpawnGUIListener(this, spawnGUI), this);
        getServer().getPluginManager().registerEvents(new CosmeticGUIListener(this, cosmeticGUI), this);
        getServer().getPluginManager().registerEvents(new QuickChatListener(this), this);

        // Verification periodique de l'arc et des fleches des joueurs en partie
        gameListener.startKitWatchdog();

        // XP de temps de jeu : une fois par minute, pour tous les joueurs en partie
        playtimeXpTask = getServer().getScheduler().runTaskTimer(this,
                () -> arenaManager.awardPlaytimeXp(), 1200L, 1200L);

        ArenaCommand arenaCommand = new ArenaCommand(this);
        getCommand("p").setExecutor(arenaCommand);
        getCommand("p").setTabCompleter(arenaCommand);

        getLogger().info("Push active. " + arenaManager.getAll().size() + " arene(s) chargee(s).");
    }

    @Override
    public void onDisable() {
        if (playtimeXpTask != null) {
            playtimeXpTask.cancel();
        }
        if (scoreboardManager != null) {
            scoreboardManager.stop();
        }
        if (hologramManager != null) {
            hologramManager.despawnAll();
        }
        if (arenaManager != null) {
            arenaManager.saveAll();
        }
        if (statsManager != null) {
            statsManager.save();
        }
        if (levelManager != null) {
            levelManager.save();
        }
        getLogger().info("Push desactive.");
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public LobbyZoneManager getLobbyZoneManager() {
        return lobbyZoneManager;
    }

    public ArenaGUI getArenaGUI() {
        return arenaGUI;
    }

    public SpawnGUI getSpawnGUI() {
        return spawnGUI;
    }

    public CosmeticGUI getCosmeticGUI() {
        return cosmeticGUI;
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    public CosmeticManager getCosmeticManager() {
        return cosmeticManager;
    }

    public LeaderboardHologramManager getHologramManager() {
        return hologramManager;
    }
}
