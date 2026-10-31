package com.hungergames.config;

import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class Reglages {
    private static JavaPlugin plugin;
    private static Configuration config;

    private Reglages() {
    }

    public static void charger(JavaPlugin instance) {
        plugin = instance;
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public static void definir(Configuration configuration) {
        plugin = null;
        config = configuration;
    }

    public static Configuration config() {
        return config;
    }

    public static int entier(String chemin) {
        return config.getInt(chemin);
    }

    public static double decimal(String chemin) {
        return config.getDouble(chemin);
    }

    public static boolean booleen(String chemin) {
        return config.getBoolean(chemin);
    }

    public static String texte(String chemin) {
        return config.getString(chemin);
    }

    public static List<String> liste(String chemin) {
        return config.getStringList(chemin);
    }

    public static void ecrire(String chemin, Object valeur) {
        config.set(chemin, valeur);
        if (plugin != null) {
            plugin.saveConfig();
        }
    }
}
