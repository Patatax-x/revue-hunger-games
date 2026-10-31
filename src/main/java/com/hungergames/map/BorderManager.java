package com.hungergames.map;

import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.loot.ConfigMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public class BorderManager {
    private static final double RATIO_TAILLE_FINALE = Reglages.decimal("border.final-size-ratio");

    private static final double TAILLE_FINALE_MIN = Reglages.decimal("border.final-size-min");
    private static final double MARGE_AVANT_DEGATS = Reglages.decimal("border.damage-buffer");
    private static final double DEGATS_PAR_SECONDE = Reglages.decimal("border.damage-per-second");

    public static final double VITESSE_ELEVE_BLOCS_PAR_SECONDE = Reglages.decimal("border.speed.fast");
    public static final double VITESSE_TRES_ELEVE_BLOCS_PAR_SECONDE = Reglages.decimal("border.speed.very-fast");
    public static final double VITESSE_MEGA_ELEVE_BLOCS_PAR_SECONDE = Reglages.decimal("border.speed.extreme");

    public static final double VITESSE_MAX_BLOCS_PAR_SECONDE = Reglages.decimal("border.speed.max");

    private final JavaPlugin plugin;
    private final Map<GameMap, BukkitTask> tachesActives = new HashMap<>();

    private final Map<GameMap, Long> debutRetrecissementMillis = new HashMap<>();

    public BorderManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void demarrer(World monde, GameMap map, ConfigMap config) {
        annulerTache(map);

        WorldBorder border = monde.getWorldBorder();
        border.setCenter(map.getCentreX(), map.getCentreZ());
        border.setSize(map.getRayonBordure() * 2);
        border.setDamageBuffer(MARGE_AVANT_DEGATS);
        border.setDamageAmount(DEGATS_PAR_SECONDE);
        border.setWarningDistance(Reglages.entier("border.warning-distance"));
        border.setWarningTime(Reglages.entier("border.warning-time"));

        long delaiTicks = config.getDelaiAvantRetrecissementSecondes() * 20L;
        long dureeRetrecissementSecondes = config.getDureeRetrecissementSecondes();

        debutRetrecissementMillis.put(map, System.currentTimeMillis() + config.getDelaiAvantRetrecissementSecondes() * 1000L);

        BukkitTask tache = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tachesActives.remove(map);
            double tailleFinale = CalculBordure.tailleFinale(map.getRayonBordure(), RATIO_TAILLE_FINALE, TAILLE_FINALE_MIN);
            border.setSize(tailleFinale, dureeRetrecissementSecondes);
            Bukkit.broadcastMessage(Messages.get("bordermanager.la_zone_de_jeu_commence_se"));
        }, delaiTicks);

        tachesActives.put(map, tache);
    }

    public long getSecondesAvantRetrecissement(GameMap map) {
        Long debut = debutRetrecissementMillis.get(map);
        if (debut == null) {
            return -1;
        }
        return Math.max(0, (debut - System.currentTimeMillis()) / 1000L);
    }

    public double getDiametreActuel(GameMap map) {
        World monde = Bukkit.getWorld(map.getCle());
        if (monde == null) {
            return -1;
        }
        return monde.getWorldBorder().getSize();
    }

    public void arreter(World monde, GameMap map) {
        annulerTache(map);
        debutRetrecissementMillis.remove(map);

        if (monde != null) {
            monde.getWorldBorder().reset();
        }
    }

    private void annulerTache(GameMap map) {
        BukkitTask tache = tachesActives.remove(map);
        if (tache != null) {
            tache.cancel();
        }
    }

    public void definirVitesseRetrecissement(World monde, GameMap map, int secondes) {
        annulerTache(map);
        debutRetrecissementMillis.put(map, System.currentTimeMillis());

        double tailleFinale = CalculBordure.tailleFinale(map.getRayonBordure(), RATIO_TAILLE_FINALE, TAILLE_FINALE_MIN);
        monde.getWorldBorder().setSize(tailleFinale, Math.max(1, secondes));
    }

    public void definirVitesseBlocsParSeconde(World monde, GameMap map, double blocsParSeconde) {
        annulerTache(map);
        debutRetrecissementMillis.put(map, System.currentTimeMillis());

        WorldBorder border = monde.getWorldBorder();
        double tailleActuelle = border.getSize();
        double tailleFinale = CalculBordure.tailleFinale(map.getRayonBordure(), RATIO_TAILLE_FINALE, TAILLE_FINALE_MIN);

        if (tailleActuelle <= tailleFinale) {
            return;
        }

        int secondes = CalculBordure.secondesPourVitesse(tailleActuelle, tailleFinale, blocsParSeconde);
        border.setSize(tailleFinale, secondes);
    }

    public void activerVitesseMax(World monde, GameMap map) {
        definirVitesseBlocsParSeconde(monde, map, VITESSE_MAX_BLOCS_PAR_SECONDE);
    }
}
