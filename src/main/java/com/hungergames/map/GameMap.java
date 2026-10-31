package com.hungergames.map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class GameMap {
    private static final Pattern NOM_DOSSIER_VALIDE = Pattern.compile("[A-Za-z0-9_-]+");

    private static final List<GameMap> MAPS = new ArrayList<>();
    private static final GameMap AUCUNE = new GameMap("", "-", Material.BARRIER, 0, 0, 0, 0, 0, null, false);

    private final String cle;
    private final String affichage;
    private final Material icone;
    private final double centreX;
    private final double centreY;
    private final double centreZ;
    private final double rayon;
    private final double rayonBordure;
    private final String dossierSauvegarde;
    private final boolean chargerAuDemarrage;

    private GameMap(String cle, String affichage, Material icone, double centreX, double centreY, double centreZ,
                    double rayon, double rayonBordure, String dossierSauvegarde, boolean chargerAuDemarrage) {
        this.cle = cle;
        this.affichage = affichage;
        this.icone = icone;
        this.centreX = centreX;
        this.centreY = centreY;
        this.centreZ = centreZ;
        this.rayon = rayon;
        this.rayonBordure = rayonBordure;
        this.dossierSauvegarde = dossierSauvegarde;
        this.chargerAuDemarrage = chargerAuDemarrage;
    }

    public static GameMap aucune() {
        return AUCUNE;
    }

    public static List<GameMap> values() {
        return List.copyOf(MAPS);
    }

    public static GameMap parCle(String cle) {
        for (GameMap map : MAPS) {
            if (map.cle.equalsIgnoreCase(cle)) {
                return map;
            }
        }
        return null;
    }

    public static void charger(ConfigurationSection section, String mondePrincipal, Logger logger) {
        MAPS.clear();
        if (section == null) {
            logger.warning("Aucune map dans config.yml (section maps).");
            return;
        }

        for (String cle : section.getKeys(false)) {
            ConfigurationSection m = section.getConfigurationSection(cle);
            if (m == null) {
                continue;
            }

            // Le nom de monde et le dossier de sauvegarde finissent dans des chemins que /resetmap
            // efface : on refuse tout ce qui sortirait du dossier du serveur, et le monde du lobby.
            String dossier = m.getString("backup-folder", "");
            if (!NOM_DOSSIER_VALIDE.matcher(cle).matches() || cle.equalsIgnoreCase(mondePrincipal)
                    || (!dossier.isEmpty() && !NOM_DOSSIER_VALIDE.matcher(dossier).matches())) {
                logger.warning("Map ignorée, nom de monde ou de sauvegarde invalide : " + cle);
                continue;
            }

            Material icone = Material.matchMaterial(m.getString("icon", "GRASS_BLOCK"));
            MAPS.add(new GameMap(
                    cle,
                    ChatColor.translateAlternateColorCodes('&', m.getString("name", cle)),
                    icone != null ? icone : Material.GRASS_BLOCK,
                    m.getDouble("center.x"),
                    m.getDouble("center.y"),
                    m.getDouble("center.z"),
                    m.getDouble("spawn-radius"),
                    m.getDouble("border-radius"),
                    dossier.isEmpty() ? null : dossier,
                    m.getBoolean("load-on-start", true)
            ));
        }
    }

    public boolean estAucune() {
        return this == AUCUNE;
    }

    public String getCle() {
        return estAucune() ? Bukkit.getWorlds().get(0).getName() : cle;
    }

    public String getAffichage() {
        return affichage;
    }

    public Material getIcone() {
        return icone;
    }

    public double getCentreX() {
        return centreX;
    }

    public double getCentreY() {
        return centreY;
    }

    public double getCentreZ() {
        return centreZ;
    }

    public double getRayon() {
        return rayon;
    }

    public double getRayonBordure() {
        return rayonBordure;
    }

    public String getDossierSauvegarde() {
        return dossierSauvegarde;
    }

    public boolean doitEtreChargeeAuDemarrage() {
        return chargerAuDemarrage;
    }
}
