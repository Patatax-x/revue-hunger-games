package com.hungergames.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class Messages {
    private static final String FICHIER = "messages.yml";

    private static Configuration textes = new YamlConfiguration();

    private Messages() {
    }

    public static void charger(JavaPlugin plugin) {
        File fichier = new File(plugin.getDataFolder(), FICHIER);
        if (!fichier.exists()) {
            plugin.saveResource(FICHIER, false);
        }

        YamlConfiguration utilisateur = YamlConfiguration.loadConfiguration(fichier);
        try (InputStream defaut = plugin.getResource(FICHIER)) {
            if (defaut != null) {
                utilisateur.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaut, StandardCharsets.UTF_8)));
            }
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Lecture de " + FICHIER + " impossible : " + exception.getMessage());
        }
        textes = utilisateur;
    }

    public static void definir(Configuration configuration) {
        textes = configuration;
    }

    /** Les paires alternent nom et valeur : chaque {nom} du texte est remplacé par sa valeur. */
    public static String get(String cle, Object... paires) {
        String brut = textes.getString(cle);
        if (brut == null) {
            return cle;
        }

        // Les valeurs sont insérées après la conversion des couleurs : un pseudo ou un nom
        // d'équipe contenant "&" ne doit pas pouvoir injecter un code couleur.
        String texte = ChatColor.translateAlternateColorCodes('&', brut);
        for (int i = 0; i + 1 < paires.length; i += 2) {
            texte = texte.replace("{" + paires[i] + "}", String.valueOf(paires[i + 1]));
        }
        return texte;
    }
}
