package com.push.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ecoute tous les evenements de jeu necessaires :
 * - mort par joueur ou par chute dans le vide -> attribution de point + reteleportation
 * - interdiction de drop / deplacement des armes du kit ET du diamant admin
 * - cooldown de tir a l'arc (3s) — l'arc a l'enchantement INFINITY, pas besoin de fleche
 * - mode spectateur : maintien dans les limites de l'arene
 * - deconnexion en cours de partie
 * - blocage des actions pendant la pause apres un point
 */
public class GameListener implements Listener {

    private final PushPlugin plugin;
    private final ArenaManager manager;

    // Suivi de la derniere entite qui a inflige des degats, pour les kills par chute
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();
    private static final long ASSIST_WINDOW_MS = 5000L;

    // Joueurs dont la mort par le vide est en cours de traitement
    private final Set<UUID> processingVoidDeath = new HashSet<>();

    public GameListener(PushPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getArenaManager();
    }

    // ---------------- Suivi des degats ----------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Arena arena = manager.getArenaOf(victim.getUniqueId());
        if (arena == null || arena.getState() != Arena.State.RUNNING) return;

        // Bloquer les degats pendant la pause post-point
        if (manager.isArenaPaused(arena)) {
            event.setCancelled(true);
            return;
        }

        Player damager = resolveDamagerPlayer(event.getDamager());
        if (damager == null) return;
        if (damager.getUniqueId().equals(victim.getUniqueId())) return;
        if (manager.getArenaOf(damager.getUniqueId()) != arena) return;

        lastDamager.put(victim.getUniqueId(), damager.getUniqueId());
        lastDamageTime.put(victim.getUniqueId(), System.currentTimeMillis());

        int damagerTeam = arena.getTeamOf(damager.getUniqueId());
        int victimTeam = arena.getTeamOf(victim.getUniqueId());
        if (damagerTeam != -1 && damagerTeam != victimTeam) {
            manager.addDamage(arena, damagerTeam, event.getFinalDamage());
            manager.addMatchDamage(arena, damager, event.getFinalDamage());
            manager.addDamageToPlayerStats(damager, event.getFinalDamage());
            plugin.getLevelManager().addDamageXp(damager, event.getFinalDamage());
        }
    }

    private Player resolveDamagerPlayer(org.bukkit.entity.Entity entity) {
        if (entity instanceof Player p) return p;
        if (entity instanceof Projectile proj) {
            ProjectileSource source = proj.getShooter();
            if (source instanceof Player p) return p;
        }
        return null;
    }

    // ---------------- Mort par un autre joueur ----------------

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Arena arena = manager.getArenaOf(victim.getUniqueId());
        if (arena == null || arena.getState() != Arena.State.RUNNING) return;

        event.getDrops().clear();
        event.setDroppedExp(0);

        if (processingVoidDeath.remove(victim.getUniqueId())) {
            event.setDeathMessage(null);
            return;
        }

        arena.addDeath(victim.getUniqueId());

        int victimTeam = arena.getTeamOf(victim.getUniqueId());
        Player killer = victim.getKiller();

        Integer scoringTeamObj = null;
        if (killer != null && manager.getArenaOf(killer.getUniqueId()) == arena) {
            int killerTeam = arena.getTeamOf(killer.getUniqueId());
            if (killerTeam != victimTeam) {
                scoringTeamObj = killerTeam;
            }
        }

        if (scoringTeamObj != null) {
            event.setDeathMessage(null);
            String killerName = killer.getName();
            manager.broadcastToArena(arena, ChatColor.YELLOW + victim.getName() + ChatColor.GRAY
                    + " a ete elimine par " + ChatColor.YELLOW + killerName + ChatColor.GRAY + ".");
            arena.addKill(killer.getUniqueId());
            plugin.getStatsManager().addKill(killer.getUniqueId(), killerName);
            plugin.getLevelManager().addKillXp(killer);
            plugin.getCosmeticManager().playKillEffect(killer, victim.getLocation());
            manager.addPointAndCheckWin(arena, scoringTeamObj, victim);
        } else {
            event.setDeathMessage(null);
            manager.broadcastToArena(arena, ChatColor.YELLOW + victim.getName() + ChatColor.GRAY + " est mort.");
        }

        lastDamager.remove(victim.getUniqueId());
        lastDamageTime.remove(victim.getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Arena arena = manager.getArenaOf(player.getUniqueId());
        if (arena == null) return;

        // Bug corrige : si la mort qui vient d'etre traitee (typiquement une chute dans le
        // vide) a fait gagner la partie, l'arene est deja passee en ENDING pendant que le
        // joueur etait encore sur l'ecran de mort. Sans ce cas, le code ci-dessous ne
        // faisait rien (l'arene n'etant plus RUNNING) et Bukkit renvoyait le joueur a son lit
        // ou au spawn du monde au lieu de l'arene : c'est ce qui causait le "je ne
        // reapparais pas a mon spawn" de temps en temps, sur la manche gagnante.
        if (arena.getState() == Arena.State.ENDING) {
            Location back = arena.getSpectatorTeleportLocation();
            if (back != null) {
                event.setRespawnLocation(back);
            }
            return;
        }

        if (arena.getState() == Arena.State.RUNNING) {
            int team = arena.getTeamOf(player.getUniqueId());
            var spawn = arena.getRandomSpawn(team);
            if (spawn != null) {
                event.setRespawnLocation(spawn.clone());
            } else {
                // Aucun spawn configure pour cette equipe (ne devrait pas arriver sur une
                // arene entierement configuree) : a defaut, le lobby plutot que de laisser
                // le joueur reapparaitre hors de l'arene.
                Location fallback = arena.getLobbyLocation();
                if (fallback != null) {
                    event.setRespawnLocation(fallback.clone());
                }
            }
            // Le kit est TOUJOURS redonne apres le respawn, y compris pendant la pause qui
            // suit un point : sinon un joueur qui meurt juste avant la pause reapparait les
            // mains vides (ni epee, ni arc, ni fleche) et ne peut plus rien faire du round.
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (manager.getArenaOf(player.getUniqueId()) != arena) return;
                if (arena.getState() != Arena.State.RUNNING) return;

                manager.giveKit(player, arena);

                // Si la partie est encore en pause, on le remet a sa base et on l'immobilise
                // comme les autres jusqu'a la reprise.
                if (manager.isArenaPaused(arena)) {
                    int t = arena.getTeamOf(player.getUniqueId());
                    org.bukkit.Location base = arena.getRandomSpawn(t);
                    if (base != null) {
                        player.teleport(base);
                    }
                    int rest = Math.max(1, manager.getResumeCountdownRemaining(arena));
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS, (rest + 1) * 20, 200, false, false, false));
                }
            }, 1L);
        }
    }

    // ---------------- Chute dans le vide + blocage mouvement ----------------

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Arena arena = manager.getArenaOf(player.getUniqueId());

        if (arena == null) {
            // Spectateur : on le ramene au point d'observation s'il descend sous le vide
            Arena spectated = manager.getSpectatedArena(player.getUniqueId());
            if (spectated != null && spectated.hasVoidY()
                    && player.getLocation().getY() < spectated.getVoidY() - 5) {
                org.bukkit.Location back = spectated.getSpectatorTeleportLocation();
                if (back != null) {
                    player.teleport(back);
                }
            }
            return;
        }

        if (arena.getState() != Arena.State.RUNNING) return;

        // Bloquer le mouvement pendant la pause post-point (5s countdown)
        if (manager.isArenaPaused(arena)) {
            org.bukkit.Location from = event.getFrom();
            org.bukkit.Location to = event.getTo();
            if (to != null && (Double.compare(from.getX(), to.getX()) != 0
                    || Double.compare(from.getY(), to.getY()) != 0
                    || Double.compare(from.getZ(), to.getZ()) != 0)) {
                event.setCancelled(true);
            }
            return;
        }

        if (!arena.hasVoidY()) return;
        if (manager.isArenaPaused(arena)) return;

        if (player.getLocation().getY() < arena.getVoidY()) {
            handleVoidDeath(player, arena);
        }
    }

    private void handleVoidDeath(Player player, Arena arena) {
        int victimTeam = arena.getTeamOf(player.getUniqueId());

        UUID damagerUuid = lastDamager.get(player.getUniqueId());
        Long time = lastDamageTime.get(player.getUniqueId());
        Integer scoringTeam = null;

        if (damagerUuid != null && time != null && (System.currentTimeMillis() - time) <= ASSIST_WINDOW_MS) {
            Player damager = plugin.getServer().getPlayer(damagerUuid);
            if (damager != null && manager.getArenaOf(damager.getUniqueId()) == arena) {
                int damagerTeam = arena.getTeamOf(damager.getUniqueId());
                if (damagerTeam != victimTeam) {
                    scoringTeam = damagerTeam;
                }
            }
        }

        if (scoringTeam == null && arena.getTeamCount() == 2) {
            scoringTeam = 1 - victimTeam;
        }

        arena.addDeath(player.getUniqueId());
        if (damagerUuid != null && scoringTeam != null && arena.getTeamOf(damagerUuid) == scoringTeam) {
            arena.addKill(damagerUuid);
            Player pusher = plugin.getServer().getPlayer(damagerUuid);
            if (pusher != null) {
                plugin.getStatsManager().addPush(damagerUuid, pusher.getName());
                plugin.getLevelManager().addPushXp(pusher);
                plugin.getCosmeticManager().playKillEffect(pusher, player.getLocation());
            }
        }

        manager.broadcastToArena(arena, ChatColor.YELLOW + player.getName() + ChatColor.GRAY
                + " est tombe dans le vide.");

        if (scoringTeam != null) {
            manager.addPointAndCheckWin(arena, scoringTeam, player);
        }

        lastDamager.remove(player.getUniqueId());
        lastDamageTime.remove(player.getUniqueId());

        processingVoidDeath.add(player.getUniqueId());
        player.setHealth(0.0001);
        player.damage(1000.0);
    }

    // ---------------- Anti-drop / anti-deplacement du kit ET du diamant admin ----------------

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Arena arena = manager.getArenaOf(player.getUniqueId());
        if (arena == null) return;

        ItemStack item = event.getItemDrop().getItemStack();
        // En lobby : bloquer le drop du diamant admin
        // En partie : bloquer le drop de tout item du kit
        if (arena.getState() == Arena.State.WAITING && isAdminDiamond(item)) {
            event.setCancelled(true);
        } else if (arena.getState() == Arena.State.RUNNING && isKitItem(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Arena arena = manager.getArenaOf(player.getUniqueId());
        if (arena == null) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (arena.getState() == Arena.State.WAITING) {
            // Bloquer tout mouvement du diamant admin en lobby
            if (isAdminDiamond(current) || isAdminDiamond(cursor)) {
                event.setCancelled(true);
                return;
            }
        }

        if (arena.getState() == Arena.State.RUNNING) {
            if (isKitItem(current) || isKitItem(cursor)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Arena arena = manager.getArenaOf(player.getUniqueId());
        if (arena == null) return;

        if (arena.getState() == Arena.State.WAITING) {
            if (isAdminDiamond(event.getMainHandItem()) || isAdminDiamond(event.getOffHandItem())) {
                event.setCancelled(true);
                return;
            }
        }

        if (arena.getState() == Arena.State.RUNNING) {
            if (isKitItem(event.getMainHandItem()) || isKitItem(event.getOffHandItem())) {
                event.setCancelled(true);
            }
        }
    }

    /** Verifie si l'item est le diamant de lancement admin (par son nom). */
    private boolean isAdminDiamond(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND) return false;
        if (!item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        return meta.hasDisplayName() && meta.getDisplayName().contains("Lancer la partie maintenant");
    }

    private boolean isKitItem(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.STONE_SWORD || type == Material.BOW || type == Material.ARROW
                || type == Material.LEATHER_HELMET || type == Material.LEATHER_CHESTPLATE
                || type == Material.LEATHER_LEGGINGS || type == Material.LEATHER_BOOTS;
    }

    // ---------------- Arc : cooldown + munition inepuisable ----------------

    /**
     * Empeche de bander l'arc quand il est en rechargement ou pendant la pause qui suit
     * un point, avec un retour visible pour le joueur. Le reste du temps, on ne touche a
     * rien : c'est le comportement vanilla qui s'applique, donc le tir fonctionne.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteractBow(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BOW) return;

        Player player = event.getPlayer();
        Arena arena = manager.getArenaOf(player.getUniqueId());
        if (arena == null || arena.getState() != Arena.State.RUNNING) return;

        if (manager.isArenaPaused(arena)) {
            event.setCancelled(true);
            return;
        }

        if (manager.isBowOnCooldown(player)) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Arc en rechargement... " + manager.getBowCooldownRemaining(player) + "s",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Arena arena = manager.getArenaOf(player.getUniqueId());
        if (arena == null || arena.getState() != Arena.State.RUNNING) return;

        // Bloquer le tir pendant la pause post-point
        if (manager.isArenaPaused(arena)) {
            event.setCancelled(true);
            removeGhostProjectile(event);
            return;
        }

        if (manager.isBowOnCooldown(player)) {
            event.setCancelled(true);
            removeGhostProjectile(event);
            return;
        }

        // Ne pas consommer la fleche (l'arc a deja INFINITY, c'est une securite en plus)
        event.setConsumeItem(false);
        if (event.getProjectile() instanceof Arrow arrow) {
            arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
        }

        // Garantir qu'il reste toujours de la munition au slot 8 : sans fleche dans
        // l'inventaire, l'arc devient purement et simplement impossible a bander.
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            if (player.isOnline()) {
                ensureArrows(player);
            }
        }, 1L);

        manager.startBowCooldown(player);
    }

    /**
     * Supprime la fleche deja apparue dans le monde quand on annule le tir (cooldown/pause) :
     * sans ca, l'evenement etait bien annule mais une fleche fantome restait parfois visible,
     * immobile, ce qui pouvait donner l'impression que "l'arc ne repond pas".
     */
    private void removeGhostProjectile(EntityShootBowEvent event) {
        if (event.getProjectile() != null) {
            event.getProjectile().remove();
        }
    }

    /** Remet un paquet de fleches au slot 8 si le joueur n'en a plus. */
    private void ensureArrows(Player player) {
        ItemStack slot8 = player.getInventory().getItem(8);
        if (slot8 == null || slot8.getType() != Material.ARROW || slot8.getAmount() < 1) {
            player.getInventory().setItem(8, new ItemStack(Material.ARROW, 64));
        }
    }

    /**
     * Filet de securite : toutes les secondes, on verifie que chaque joueur en partie a
     * bien son arc et sa munition. Un item perdu (mort mal geree, plugin tiers, clic
     * exotique) ne peut donc plus laisser un joueur incapable de tirer jusqu'au round suivant.
     */
    public void startKitWatchdog() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Arena arena : manager.getAll()) {
                    if (arena.getState() != Arena.State.RUNNING) continue;
                    if (manager.isArenaPaused(arena)) continue;

                    for (Player player : manager.getOnlinePlayersInArena(arena)) {
                        if (player.isDead()) continue;
                        ensureArrows(player);

                        ItemStack bow = player.getInventory().getItem(1);
                        if (bow == null || bow.getType() != Material.BOW) {
                            manager.giveBowOnly(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 20L);
    }

    // ---------------- Protection de la zone de l'arene ----------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        org.bukkit.Location loc = event.getBlock().getLocation();
        for (Arena arena : manager.getAll()) {
            if (arena.hasZone() && arena.isInZone(loc)) {
                // Les admins sans arene peuvent toujours casser des blocs (pour configurer)
                if (player.hasPermission("push.admin") && manager.getArenaOf(player.getUniqueId()) == null) {
                    return;
                }
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Tu ne peux pas casser de blocs dans la zone de l'arene.");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        org.bukkit.Location loc = event.getBlock().getLocation();
        for (Arena arena : manager.getAll()) {
            if (arena.hasZone() && arena.isInZone(loc)) {
                if (player.hasPermission("push.admin") && manager.getArenaOf(player.getUniqueId()) == null) {
                    return;
                }
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Tu ne peux pas placer de blocs dans la zone de l'arene.");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            org.bukkit.Location loc = block.getLocation();
            for (Arena arena : manager.getAll()) {
                if (arena.hasZone() && arena.isInZone(loc)) return true;
            }
            return false;
        });
    }

    // ---------------- Deconnexion en cours de partie ----------------

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Un spectateur qui se deconnecte est sorti proprement du mode observation,
        // sinon il se reconnecterait bloque en gamemode SPECTATOR au milieu de l'arene.
        Arena spectated = manager.getSpectatedArena(player.getUniqueId());
        if (spectated != null) {
            manager.removeSpectator(player);
            return;
        }

        Arena arena = manager.getArenaOf(player.getUniqueId());
        if (arena == null) return;

        manager.clearBowCooldown(player);
        lastDamager.remove(player.getUniqueId());
        lastDamageTime.remove(player.getUniqueId());
        processingVoidDeath.remove(player.getUniqueId());

        manager.removePlayerFromArena(player, arena, false);
        manager.resetTabList(player);
        manager.broadcastToArena(arena, ChatColor.GRAY + player.getName() + " a quitte la partie.");
    }

    // ---------------- Empecher la faim de baisser pendant les parties ----------------

    @EventHandler
    public void onFoodLevelChange(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Arena arena = manager.getArenaOf(player.getUniqueId());
        if (arena != null) {
            event.setCancelled(true);
        }
    }
}
