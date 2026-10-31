package com.hungergames.hud;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionBar {
    private static final long DUREE_SILENCE_MS = 2500L;

    private static final Map<UUID, Long> silenceJusqua = new ConcurrentHashMap<>();

    private ActionBar() {
    }

    public static void prioritaire(Player joueur, String message) {
        prioritaire(joueur, message, DUREE_SILENCE_MS);
    }

    public static void prioritaire(Player joueur, String message, long dureeMs) {
        silenceJusqua.put(joueur.getUniqueId(), System.currentTimeMillis() + dureeMs);
        joueur.sendActionBar(message);
    }

    public static boolean hudAutorise(Player joueur) {
        Long fin = silenceJusqua.get(joueur.getUniqueId());
        if (fin == null) {
            return true;
        }
        if (System.currentTimeMillis() >= fin) {
            silenceJusqua.remove(joueur.getUniqueId());
            return true;
        }
        return false;
    }
}
