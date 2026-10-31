package com.hungergames.loot;

import com.hungergames.config.Reglages;
import com.hungergames.map.CalculBordure;
import com.hungergames.map.GameMap;
import org.bukkit.configuration.Configuration;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

public class ConfigMap {
    private double centreX;
    private double centreY;
    private double centreZ;
    private double centreClusterX;
    private double centreClusterY;
    private double centreClusterZ;
    private int rayonEparpillement;
    private int rayonCluster;
    private int nombreCoffresTotal;
    private int pourcentageCentre;
    private int delaiAvantRetrecissementSecondes;
    private int dureeMaxPartieSecondes;
    private final int intervalleLargageAutoSecondes;
    private final int delaiPremierLargageSecondes;
    private final int rayonLargageAutoAutourJoueur;

    private final String prefixe;
    private final Map<Rarete, Integer> proportions = new EnumMap<>(Rarete.class);

    public ConfigMap(GameMap map) {
        this.prefixe = "maps." + map.getCle() + ".";

        this.centreX = lireDecimal("chests.scatter-center.x", map.getCentreX());
        this.centreY = lireDecimal("chests.scatter-center.y", map.getCentreY());
        this.centreZ = lireDecimal("chests.scatter-center.z", map.getCentreZ());

        this.centreClusterX = lireDecimal("chests.cluster-center.x", map.getCentreX());
        this.centreClusterY = lireDecimal("chests.cluster-center.y", map.getCentreY());
        this.centreClusterZ = lireDecimal("chests.cluster-center.z", map.getCentreZ());

        this.rayonEparpillement = lireEntier("chests.scatter-radius");
        this.rayonCluster = lireEntier("chests.cluster-radius");
        this.nombreCoffresTotal = lireEntier("chests.total");
        this.pourcentageCentre = lireEntier("chests.center-percent");
        this.delaiAvantRetrecissementSecondes = lireEntier("border.shrink-delay-seconds");
        this.dureeMaxPartieSecondes = lireEntier("match.max-duration-seconds");
        this.intervalleLargageAutoSecondes = lireEntier("airdrops.interval-seconds");
        this.delaiPremierLargageSecondes = lireEntier("airdrops.first-delay-seconds");
        this.rayonLargageAutoAutourJoueur = lireEntier("airdrops.player-radius");

        for (Rarete rarete : Rarete.values()) {
            proportions.put(rarete, lireEntier("rarity-weights." + rarete.name()));
        }
    }

    private int lireEntier(String chemin) {
        Configuration config = Reglages.config();
        return config.contains(prefixe + chemin) ? config.getInt(prefixe + chemin) : config.getInt("map-defaults." + chemin);
    }

    private double lireDecimal(String chemin, double defaut) {
        return Reglages.config().getDouble(prefixe + chemin, defaut);
    }

    private void ecrire(String chemin, Object valeur) {
        Reglages.ecrire(prefixe + chemin, valeur);
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

    public void setCentre(double x, double y, double z) {
        this.centreX = x;
        this.centreY = y;
        this.centreZ = z;
        ecrire("chests.scatter-center.x", x);
        ecrire("chests.scatter-center.y", y);
        ecrire("chests.scatter-center.z", z);
    }

    public double getCentreClusterX() {
        return centreClusterX;
    }

    public double getCentreClusterY() {
        return centreClusterY;
    }

    public double getCentreClusterZ() {
        return centreClusterZ;
    }

    public void setCentreCluster(double x, double y, double z) {
        this.centreClusterX = x;
        this.centreClusterY = y;
        this.centreClusterZ = z;
        ecrire("chests.cluster-center.x", x);
        ecrire("chests.cluster-center.y", y);
        ecrire("chests.cluster-center.z", z);
    }

    public int getRayonEparpillement() {
        return rayonEparpillement;
    }

    public void setRayonEparpillement(int rayon) {
        this.rayonEparpillement = Math.max(1, rayon);
        ecrire("chests.scatter-radius", this.rayonEparpillement);
    }

    public int getRayonCluster() {
        return rayonCluster;
    }

    public void setRayonCluster(int rayon) {
        this.rayonCluster = Math.max(1, rayon);
        ecrire("chests.cluster-radius", this.rayonCluster);
    }

    public int getNombreCoffresTotal() {
        return nombreCoffresTotal;
    }

    public void setNombreCoffresTotal(int n) {
        this.nombreCoffresTotal = Math.max(0, n);
        ecrire("chests.total", this.nombreCoffresTotal);
    }

    public int getPourcentageCentre() {
        return pourcentageCentre;
    }

    public void setPourcentageCentre(int pourcentage) {
        this.pourcentageCentre = Math.max(0, Math.min(100, pourcentage));
        ecrire("chests.center-percent", this.pourcentageCentre);
    }

    public int getNombreCoffresCentre() {
        return Math.round(nombreCoffresTotal * pourcentageCentre / 100f);
    }

    public int getNombreCoffresEparpilles() {
        return nombreCoffresTotal - getNombreCoffresCentre();
    }

    public int getDelaiAvantRetrecissementSecondes() {
        return delaiAvantRetrecissementSecondes;
    }

    public void setDelaiAvantRetrecissementSecondes(int secondes) {
        this.delaiAvantRetrecissementSecondes = Math.max(0, secondes);
        ecrire("border.shrink-delay-seconds", this.delaiAvantRetrecissementSecondes);
    }

    public int getDureeMaxPartieSecondes() {
        return dureeMaxPartieSecondes;
    }

    public void setDureeMaxPartieSecondes(int secondes) {
        this.dureeMaxPartieSecondes = Math.max(60, secondes);
        ecrire("match.max-duration-seconds", this.dureeMaxPartieSecondes);
    }

    public int getDureeRetrecissementSecondes() {
        return CalculBordure.dureeRetrecissement(dureeMaxPartieSecondes, delaiAvantRetrecissementSecondes);
    }

    public int getIntervalleLargageAutoSecondes() {
        return intervalleLargageAutoSecondes;
    }

    public int getDelaiPremierLargageSecondes() {
        return delaiPremierLargageSecondes;
    }

    public int getRayonLargageAutoAutourJoueur() {
        return rayonLargageAutoAutourJoueur;
    }

    public int getProportion(Rarete rarete) {
        return proportions.getOrDefault(rarete, 0);
    }

    public void setProportion(Rarete rarete, int valeur) {
        proportions.put(rarete, Math.max(0, valeur));
        ecrire("rarity-weights." + rarete.name(), proportions.get(rarete));
    }

    public int getTotalProportions() {
        return proportions.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Rarete tirerRarete(Random random) {
        int total = getTotalProportions() - getProportion(Rarete.LARGAGE);
        if (total <= 0) {
            return Rarete.COMMUN;
        }

        int tirage = random.nextInt(total);
        int cumul = 0;

        for (Rarete rarete : Rarete.values()) {
            if (rarete == Rarete.LARGAGE) continue;

            cumul += getProportion(rarete);
            if (tirage < cumul) {
                return rarete;
            }
        }

        return Rarete.COMMUN;
    }
}
