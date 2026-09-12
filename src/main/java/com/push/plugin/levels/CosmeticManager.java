package com.push.plugin.levels;

import com.push.plugin.PushPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Declenche l'effet cosmetique d'elimination equipe par un joueur (voir PushCosmetic)
 * a chaque kill ou poussee dans le vide qui lui est credite.
 */
public class CosmeticManager {

    private final PushPlugin plugin;

    public CosmeticManager(PushPlugin plugin) {
        this.plugin = plugin;
    }

    public void playKillEffect(Player killer, Location at) {
        if (killer == null || at == null || at.getWorld() == null) return;
        PushCosmetic cosmetic = plugin.getLevelManager().getEquippedCosmetic(killer.getUniqueId());
        if (cosmetic == PushCosmetic.NONE) return;
        cosmetic.play(at.getWorld(), at);
    }
}
