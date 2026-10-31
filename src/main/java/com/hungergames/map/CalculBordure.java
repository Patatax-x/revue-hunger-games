package com.hungergames.map;

public final class CalculBordure {
    private CalculBordure() {
    }

    public static double tailleFinale(double rayonBordure, double ratio, double tailleMin) {
        return Math.max(tailleMin, rayonBordure * 2 * ratio);
    }

    /** La bordure atteint sa taille finale pile quand la partie se clôture automatiquement. */
    public static int dureeRetrecissement(int dureeMaxPartie, int delaiAvantRetrecissement) {
        return Math.max(60, dureeMaxPartie - delaiAvantRetrecissement);
    }

    public static int secondesPourVitesse(double tailleActuelle, double tailleFinale, double blocsParSeconde) {
        return Math.max(1, (int) Math.ceil((tailleActuelle - tailleFinale) / blocsParSeconde));
    }
}
