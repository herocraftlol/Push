package com.push.plugin.levels;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Effets cosmetiques exclusifs a Push, joues a l'endroit d'un adversaire elimine (par
 * kill ou par poussee dans le vide). Purement visuels/sonores : aucun impact sur le jeu.
 * Deverrouilles progressivement en montant de niveau (voir LevelManager) et choisis par
 * chaque joueur via /p cosmetics.
 */
public enum PushCosmetic {

    NONE("aucun", "Aucun effet", 1) {
        @Override
        public void play(World world, Location loc) {
            // Pas d'effet : c'est le choix par defaut
        }
    },

    SPARKS("etincelles", "Etincelles", 3) {
        @Override
        public void play(World world, Location loc) {
            world.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.0);
            world.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
        }
    },

    FLAMES("flammes", "Flammes", 6) {
        @Override
        public void play(World world, Location loc) {
            world.spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.02);
            world.playSound(loc, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
        }
    },

    EXPLOSION("explosion", "Explosion", 10) {
        @Override
        public void play(World world, Location loc) {
            world.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0.0);
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.3f);
        }
    },

    LIGHTNING("foudre", "Foudre", 15) {
        @Override
        public void play(World world, Location loc) {
            // Effet visuel uniquement (aucun degat, aucune mise a feu du bloc touche)
            world.strikeLightningEffect(loc);
        }
    },

    FIREWORK("feu-artifice", "Feu d'artifice", 20) {
        @Override
        public void play(World world, Location loc) {
            org.bukkit.entity.Firework firework = world.spawn(loc.clone().add(0, 1, 0), org.bukkit.entity.Firework.class);
            org.bukkit.inventory.meta.FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(org.bukkit.FireworkEffect.builder()
                    .withColor(org.bukkit.Color.FUCHSIA, org.bukkit.Color.WHITE)
                    .with(org.bukkit.FireworkEffect.Type.BURST)
                    .trail(true)
                    .build());
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            // Detonation immediate et sans degats : purement decoratif
            firework.detonate();
        }
    },

    LEGENDARY("etoile-legendaire", "Etoile legendaire", 25) {
        @Override
        public void play(World world, Location loc) {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.3);
            world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
            world.playSound(loc, Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
        }
    };

    private final String id;
    private final String displayName;
    private final int requiredLevel;

    PushCosmetic(String id, String displayName, int requiredLevel) {
        this.id = id;
        this.displayName = displayName;
        this.requiredLevel = requiredLevel;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public boolean isUnlockedAt(int level) {
        return level >= requiredLevel;
    }

    /** Joue l'effet a l'emplacement donne, visible par tous les joueurs proches. */
    public abstract void play(World world, Location loc);

    /** Joue l'effet pour un joueur (raccourci pratique dans les GUIs). */
    public void preview(Player player) {
        play(player.getWorld(), player.getLocation());
    }

    public static PushCosmetic fromId(String id) {
        if (id == null) return NONE;
        for (PushCosmetic cosmetic : values()) {
            if (cosmetic.id.equalsIgnoreCase(id)) return cosmetic;
        }
        return NONE;
    }
}
