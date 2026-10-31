package com.hungergames.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculBordureTest {
    @Test
    void laTailleFinaleSuitLeRatioDuDiametre() {
        assertEquals(100.0, CalculBordure.tailleFinale(200, 0.25, 20));
    }

    @Test
    void laTailleFinaleNeDescendPasSousLeMinimum() {
        assertEquals(20.0, CalculBordure.tailleFinale(10, 0.25, 20));
    }

    @Test
    void leRetrecissementOccupeLeTempsRestantDeLaPartie() {
        assertEquals(2400, CalculBordure.dureeRetrecissement(2700, 300));
    }

    @Test
    void leRetrecissementDureAuMoinsUneMinute() {
        assertEquals(60, CalculBordure.dureeRetrecissement(300, 300));
        assertEquals(60, CalculBordure.dureeRetrecissement(100, 300));
    }

    @Test
    void laDureeSeDeduitDeLaVitesseEnArrondissantAuDessus() {
        assertEquals(100, CalculBordure.secondesPourVitesse(400, 100, 3));
        assertEquals(101, CalculBordure.secondesPourVitesse(401, 100, 3));
    }

    @Test
    void laDureeEstToujoursAuMoinsUneSeconde() {
        assertEquals(1, CalculBordure.secondesPourVitesse(100, 100, 3));
    }
}
