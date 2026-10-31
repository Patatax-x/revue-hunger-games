package com.hungergames.hud;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Titre {
    private static final long ATTENTE_MAX_MS = 4000L;

    private static final Map<UUID, Long> libreA = new ConcurrentHashMap<>();
    private static JavaPlugin plugin;

    private Titre() {
    }

    public static void initialiser(JavaPlugin instance) {
        plugin = instance;
    }

    private static long dureeMs(int fadeIn, int stay, int fadeOut) {
        return (long) (fadeIn + stay + fadeOut) * 50L;
    }

    public static void immediat(Player joueur, String titre, String sousTitre, int fadeIn, int stay, int fadeOut) {
        libreA.put(joueur.getUniqueId(), System.currentTimeMillis() + dureeMs(fadeIn, stay, fadeOut));

        joueur.sendTitle(titre, sousTitre, fadeIn, stay, fadeOut);
    }

    public static void enFile(Player joueur, String titre, String sousTitre, int fadeIn, int stay, int fadeOut) {
        long maintenant = System.currentTimeMillis();
        Long fin = libreA.get(joueur.getUniqueId());
        long attenteMs = (fin == null) ? 0L : fin - maintenant;

        if (attenteMs <= 0L || plugin == null || attenteMs > ATTENTE_MAX_MS) {
            immediat(joueur, titre, sousTitre, fadeIn, stay, fadeOut);
            return;
        }

        libreA.put(joueur.getUniqueId(), fin + dureeMs(fadeIn, stay, fadeOut));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (joueur.isOnline()) {
                joueur.sendTitle(titre, sousTitre, fadeIn, stay, fadeOut);
            }
        }, (attenteMs / 50L) + 1L);
    }
}
